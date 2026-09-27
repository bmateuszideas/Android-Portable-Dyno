package com.roaddyno.app.dyno

import com.roaddyno.app.domain.model.SpeedSample
import kotlin.math.PI
import kotlin.math.max

data class DynoPoint(
    val elapsedSeconds: Double,
    val speedKmh: Double,
    val rpm: Double?,
    val wheelPowerKw: Double,
    val lossPowerKw: Double?,
    val correctedPowerKw: Double?,
    val torqueNm: Double?,
)

data class DynoResult(
    val points: List<DynoPoint>,
    val accelerationStartSeconds: Double,
    val peakSeconds: Double,
    val coastStartSeconds: Double?,
    val coastEndSeconds: Double?,
    val coastMinKmh: Double?,
    val coastMaxKmh: Double?,
    val peakWheelPowerKw: Double,
    val peakCorrectedPowerKw: Double?,
    val peakTorqueNm: Double?,
    val sampleRateHz: Double,
)

/** Offline inertial road dyno. Raw samples are never changed.
 * Power is the local derivative of kinetic energy. Coast losses are interpolated
 * at matching speeds only; neither a drag model nor missing coast data is invented.
 */
class DynoEngine {
    companion object { const val VERSION = "energy-coast-2" }

    fun analyze(
        raw: List<SpeedSample>,
        massKg: Double,
        calibrationRpm: Double? = null,
        calibrationSpeedKmh: Double? = null,
    ): DynoResult {
        RunConfiguration(massKg = massKg, calibrationRpm = calibrationRpm,
            calibrationSpeedKmh = calibrationSpeedKmh).validate()
        val rpmPerKmh = calibrationRpm?.div(calibrationSpeedKmh!!)
        val samples = raw.filter { it.hasSpeed && it.timestampNs > 0 &&
            it.speedMps.isFinite() && it.speedMps >= 0 }
            .fold(mutableListOf<SpeedSample>()) { list, sample ->
                if (list.isEmpty() || sample.timestampNs > list.last().timestampNs) list.add(sample)
                list
            }
        require(samples.size >= 9) { "Za mało próbek: zapisz rozpędzenie i wybieg, następnie naciśnij STOP." }
        val time = DoubleArray(samples.size) { (samples[it].timestampNs - samples[0].timestampNs) / 1e9 }
        val speed = DoubleArray(samples.size) { samples[it].speedMps }
        val intervals = (1..time.lastIndex).map { time[it] - time[it - 1] }.sorted()
        val medianInterval = intervals[intervals.size / 2]
        val segment = findRun(time, speed)
        require((segment.start + 1..segment.end).none {
            time[it] - time[it - 1] > max(3.0 * medianInterval, 3.0)
        }) { "Przerwa w zapisie przecina przejazd. Surowe dane pozostają w historii." }
        // A time window, not a fixed sample count: the same smoothing duration at 1 Hz and 20 Hz.
        val radius = max(2.0, medianInterval * 2.0)
        val coastStart = (segment.peak + 1..segment.end).firstOrNull {
            time[it] - time[segment.peak] >= 2.0
        }
        val coast = if (coastStart != null && segment.end - coastStart >= 2 &&
            time[segment.end] - time[coastStart] >= 3.0) {
            (coastStart..segment.end).mapNotNull { i ->
                val power = energySlope(time, speed, i, coastStart, segment.end, radius)?.let { -massKg * it / 2000.0 }
                if (power != null && power > 0) speed[i] to power else null
            }.sortedBy { it.first }.distinctBy { it.first }
        } else emptyList()
        val points = (segment.start..segment.peak).mapNotNull { i ->
            val derivative = energySlope(time, speed, i, segment.start, segment.peak, radius)
                ?: return@mapNotNull null
            val wheel = massKg * derivative / 2000.0
            if (wheel <= 0) return@mapNotNull null
            val loss = interpolate(coast, speed[i])
            val corrected = loss?.let { wheel + it }
            val rpm = rpmPerKmh?.let { speed[i] * 3.6 * it }
            val torque = if (rpm != null && rpm > 0 && corrected != null)
                corrected * 1000.0 * 60.0 / (2 * PI * rpm) else null
            DynoPoint(time[i], speed[i] * 3.6, rpm, wheel, loss, corrected, torque)
        }
        require(points.size >= 3) { "Rozpędzanie jest zbyt krótkie do obliczenia krzywej mocy." }
        return DynoResult(
            points, time[segment.start], time[segment.peak],
            coastStart?.takeIf { coast.isNotEmpty() }?.let { time[it] },
            time[segment.end].takeIf { coast.isNotEmpty() },
            coast.firstOrNull()?.first?.times(3.6), coast.lastOrNull()?.first?.times(3.6),
            points.maxOf { it.wheelPowerKw }, points.mapNotNull { it.correctedPowerKw }.maxOrNull(),
            points.mapNotNull { it.torqueNm }.maxOrNull(), (samples.size - 1) / time.last(),
        )
    }

    private data class Segment(val start: Int, val peak: Int, val end: Int)

    /** One START/STOP represents one pull. Do not silently select a stronger pull from traffic. */
    private fun findRun(t: DoubleArray, v: DoubleArray): Segment {
        val runs = mutableListOf<Segment>()
        var start = 0
        var peak = 0
        var low = 0
        var accelerating = false
        var coasting = false
        for (i in 1..v.lastIndex) {
            if (!accelerating && !coasting) {
                if (v[i] <= v[start]) start = i
                if (v[i] - v[start] >= 10.0 / 3.6 && t[i] - t[start] >= 2.0) {
                    accelerating = true
                    peak = (start..i).maxBy { v[it] }
                }
            } else if (accelerating) {
                if (v[i] >= v[peak]) peak = i
                if (v[peak] - v[i] >= 1.5 / 3.6 && t[i] - t[peak] >= 2.0) {
                    accelerating = false
                    coasting = true
                    low = (peak..i).minBy { v[it] }
                }
            } else {
                if (v[i] <= v[low]) low = i
                if (v[i] - v[low] >= 1.5 / 3.6 && t[i] - t[low] >= 1.0) {
                    runs += Segment(start, peak, low)
                    start = low
                    coasting = false
                    if (v[i] - v[start] >= 10.0 / 3.6 && t[i] - t[start] >= 2.0) {
                        accelerating = true
                        peak = (start..i).maxBy { v[it] }
                    }
                }
            }
        }
        if (accelerating) runs += Segment(start, peak, peak)
        if (coasting) runs += Segment(start, peak, low)
        require(runs.isNotEmpty()) { "Nie znaleziono rozpędzenia o co najmniej 10 km/h. Dane są zapisane." }
        require(runs.size == 1) { "Zapis zawiera kilka rozpędzeń. Wykonaj jeden przejazd: START → rozpędzenie → wybieg → STOP." }
        return runs.single()
    }

    /** Least squares derivative of v²(t), clipped to one phase, in seconds and SI units. */
    private fun energySlope(t: DoubleArray, v: DoubleArray, i: Int, start: Int, end: Int, radius: Double): Double? {
        var from = i
        var to = i
        while (from > start && t[i] - t[from - 1] <= radius * 1.05) from--
        while (to < end && t[to + 1] - t[i] <= radius * 1.05) to++
        if (to - from < 2) return null
        val center = (from..to).sumOf { t[it] } / (to - from + 1)
        val denominator = (from..to).sumOf { (t[it] - center) * (t[it] - center) }
        return if (denominator > 0)
            (from..to).sumOf { (t[it] - center) * v[it] * v[it] } / denominator else null
    }

    private fun interpolate(values: List<Pair<Double, Double>>, speed: Double): Double? {
        if (values.size < 2 || speed < values.first().first || speed > values.last().first) return null
        val index = values.binarySearchBy(speed) { it.first }
        if (index >= 0) return values[index].second
        val upper = -index - 1
        if (upper == 0 || upper >= values.size) return null
        val a = values[upper - 1]
        val b = values[upper]
        return a.second + (b.second - a.second) * (speed - a.first) / (b.first - a.first)
    }
}
