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

/**
 * Offline calculation from raw GNSS speed. Nothing here changes persisted samples.
 * Road load is estimated from a following neutral coast and is only applied
 * within the measured coast speed range. It includes road grade and wind.
 */
class DynoEngine {
    fun analyze(
        raw: List<SpeedSample>,
        massKg: Double,
        calibrationRpm: Double? = null,
        calibrationSpeedKmh: Double? = null,
    ): DynoResult {
        require(massKg.isFinite() && massKg > 0) { "Enter a positive measurement mass in kg." }
        val rpmPerKmh = if (calibrationRpm != null && calibrationSpeedKmh != null) {
            require(calibrationRpm.isFinite() && calibrationRpm > 0 &&
                calibrationSpeedKmh.isFinite() && calibrationSpeedKmh > 0) { "Invalid RPM calibration." }
            calibrationRpm / calibrationSpeedKmh
        } else null
        val samples = raw.filter { it.hasSpeed && it.timestampNs > 0 &&
            it.speedMps.isFinite() && it.speedMps >= 0 }
            .fold(mutableListOf<SpeedSample>()) { list, sample ->
                if (list.isEmpty() || sample.timestampNs > list.last().timestampNs) list.add(sample)
                list
            }
        require(samples.size >= 9) { "At least nine ordered speed samples are required." }
        val time = DoubleArray(samples.size) { (samples[it].timestampNs - samples[0].timestampNs) / 1e9 }
        val speed = DoubleArray(samples.size) { samples[it].speedMps }
        val smooth = DoubleArray(samples.size) { i ->
            val from = max(0, i - 2)
            val to = minOf(speed.lastIndex, i + 2)
            (from..to).sumOf { j -> speed[j] * (3 - kotlin.math.abs(i - j)) } /
                (from..to).sumOf { j -> (3 - kotlin.math.abs(i - j)).toDouble() }
        }
        data class Segment(val start: Int, val peak: Int, val end: Int?, val score: Double)
        val candidates = mutableListOf<Segment>()
        for (peak in 2 until smooth.lastIndex) {
            if (smooth[peak] < smooth[peak - 1] || smooth[peak] <= smooth[peak + 1]) continue
            val first = (0 until peak).firstOrNull { time[peak] - time[it] <= 20.0 } ?: peak - 1
            val start = (first until peak).minByOrNull { smooth[it] } ?: continue
            val gain = (smooth[peak] - smooth[start]) * 3.6
            if (time[peak] - time[start] < 4.0 || gain < 10.0) continue
            var lowest = peak + 1
            for (i in peak + 2..smooth.lastIndex) {
                if (time[i] - time[peak] > 90.0) break
                if (smooth[i] < smooth[lowest]) lowest = i
                if (smooth[i] - smooth[lowest] > 1.5 / 3.6 &&
                    i + 1 <= smooth.lastIndex && smooth[i + 1] > smooth[i]) break
            }
            val coastDrop = (smooth[peak] - smooth[lowest]) * 3.6
            val coast = lowest.takeIf { time[it] - time[peak] >= 5.0 && coastDrop >= 5.0 }
            // Prefer one continuous acceleration followed by a useful coast.
            candidates += Segment(start, peak, coast, gain * (coast?.let { coastDrop } ?: 0.1))
        }
        val chosen = candidates.maxByOrNull { it.score }
            ?: throw IllegalArgumentException("No sustained acceleration found in this session.")
        val coastEnd = chosen.end
        val coastStart = coastEnd?.let { minOf(chosen.peak + 2, it) }
        val coastModel = if (coastStart != null && coastEnd != null && coastEnd - coastStart >= 6)
            fitRoadLoad(time, speed, coastStart, coastEnd) else null
        val minCoast = coastEnd?.let { speed[it] * 3.6 }
        val maxCoast = coastStart?.let { speed[it] * 3.6 }
        val points = (chosen.start..chosen.peak).mapNotNull { i ->
            val slope = slope(time, speed, max(chosen.start, i - 2), minOf(chosen.peak, i + 2))
                ?: return@mapNotNull null
            if (slope <= 0) return@mapNotNull null
            val v = speed[i]
            val wheel = massKg * v * slope / 1_000.0
            val withinCoast = coastModel != null && minCoast != null && maxCoast != null &&
                v * 3.6 in minCoast..maxCoast
            val loss = if (withinCoast) massKg * v *
                max(0.0, coastModel!!.first + coastModel.second * v * v) / 1_000.0 else null
            val corrected = loss?.let { wheel + it }
            val rpm = rpmPerKmh?.let { v * 3.6 * it }
            val torque = if (rpm != null && rpm > 0 && corrected != null)
                corrected * 1_000.0 * 60.0 / (2 * PI * rpm) else null
            DynoPoint(time[i], v * 3.6, rpm, wheel, loss, corrected, torque)
        }
        require(points.size >= 3) { "Acceleration segment is too short for a power curve." }
        return DynoResult(
            points, time[chosen.start], time[chosen.peak],
            coastStart?.let { time[it] }, coastEnd?.let { time[it] },
            minCoast, maxCoast, points.maxOf { it.wheelPowerKw },
            points.mapNotNull { it.correctedPowerKw }.maxOrNull(),
            points.mapNotNull { it.torqueNm }.maxOrNull(),
            (samples.size - 1) / time.last(),
        )
    }

    private fun fitRoadLoad(t: DoubleArray, v: DoubleArray, start: Int, end: Int): Pair<Double, Double>? {
        val xs = mutableListOf<Double>()
        val ys = mutableListOf<Double>()
        for (i in start + 2..end - 2) {
            val deceleration = -(slope(t, v, i - 2, i + 2) ?: continue)
            if (deceleration >= 0) { xs += v[i] * v[i]; ys += deceleration }
        }
        if (xs.size < 3) return null
        val mx = xs.average()
        val my = ys.average()
        val denominator = xs.sumOf { (it - mx) * (it - mx) }
        if (denominator <= 0) return null
        val quadratic = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / denominator
        return Pair(my - quadratic * mx, quadratic)
    }

    private fun slope(t: DoubleArray, v: DoubleArray, from: Int, to: Int): Double? {
        if (to - from < 2) return null
        val center = (from..to).sumOf { t[it] } / (to - from + 1)
        val denominator = (from..to).sumOf { (t[it] - center) * (t[it] - center) }
        return if (denominator > 0) (from..to).sumOf { (t[it] - center) * v[it] } / denominator else null
    }
}
