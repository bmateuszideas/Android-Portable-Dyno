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
    companion object { const val VERSION = "energy-coast-3" }

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
        require(samples.size >= 2) { "Potrzebne są co najmniej dwie próbki prędkości. Surowy zapis jest dostępny poniżej." }
        val time = DoubleArray(samples.size) { (samples[it].timestampNs - samples[0].timestampNs) / 1e9 }
        val speed = DoubleArray(samples.size) { samples[it].speedMps }
        val intervals = (1..time.lastIndex).map { time[it] - time[it - 1] }.sorted()
        val medianInterval = intervals[intervals.size / 2]
        // One recorded measurement: the lowest speed before its maximum is the
        // beginning of acceleration; the lowest speed after it closes the coast.
        // No minimum speed gain, fixed duration or "best run" score is applied.
        val peak = speed.indices.maxBy { speed[it] }
        val start = (0..peak).minBy { speed[it] }
        require(start < peak) { "W tym zapisie nie ma rozpędzania. Surowy przebieg jest dostępny poniżej." }
        val end = (peak..speed.lastIndex).minBy { speed[it] }
        // A time window, not a fixed sample count: the same smoothing duration at 1 Hz and 20 Hz.
        val radius = max(2.0, medianInterval * 2.0)
        val coast = if (end > peak) {
            (peak..end).mapNotNull { i ->
                val power = energySlope(time, speed, i, peak, end, radius)?.let { -massKg * it / 2000.0 }
                if (power != null && power > 0) speed[i] to power else null
            }.sortedBy { it.first }.distinctBy { it.first }
        } else emptyList()
        val points = (start..peak).mapNotNull { i ->
            val derivative = energySlope(time, speed, i, start, peak, radius)
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
        require(points.isNotEmpty()) { "Zapis nie zawiera dodatniej mocy rozpędzania. Surowy przebieg jest dostępny poniżej." }
        return DynoResult(
            points, time[start], time[peak],
            time[peak].takeIf { coast.size >= 2 },
            time[end].takeIf { coast.size >= 2 },
            coast.firstOrNull()?.first?.times(3.6)?.takeIf { coast.size >= 2 },
            coast.lastOrNull()?.first?.times(3.6)?.takeIf { coast.size >= 2 },
            points.maxOf { it.wheelPowerKw }, points.mapNotNull { it.correctedPowerKw }.maxOrNull(),
            points.mapNotNull { it.torqueNm }.maxOrNull(), (samples.size - 1) / time.last(),
        )
    }

    /** Least squares derivative of v²(t), clipped to one phase, in seconds and SI units. */
    private fun energySlope(t: DoubleArray, v: DoubleArray, i: Int, start: Int, end: Int, radius: Double): Double? {
        var from = i
        var to = i
        while (from > start && t[i] - t[from - 1] <= radius * 1.05) from--
        while (to < end && t[to + 1] - t[i] <= radius * 1.05) to++
        if (to - from < 1) return null
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
