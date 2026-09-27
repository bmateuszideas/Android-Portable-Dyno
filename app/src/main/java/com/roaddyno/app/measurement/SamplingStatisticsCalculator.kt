package com.roaddyno.app.measurement

import com.roaddyno.app.data.database.SpeedSampleEntity
import com.roaddyno.app.domain.model.SpeedSample
import java.util.ArrayDeque

data class SamplingStatistics(
    val sampleCount: Long = 0,
    val currentRateHz: Double? = null,
    val averageRateHz: Double? = null,
    val averageDeltaMs: Double? = null,
    val medianDeltaMs: Double? = null,
    val minDeltaMs: Double? = null,
    val maxDeltaMs: Double? = null,
    val gapCount: Int = 0,
    val anomalyCount: Int = 0,
)

class SamplingStatisticsCalculator {
    private val window = ArrayDeque<Long>()
    private val deltas = mutableListOf<Double>()
    private var firstNs: Long? = null
    private var lastNs: Long? = null
    private var validCount = 0
    private var samples = 0L
    private var anomalies = 0

    fun accept(sample: SpeedSample, hasAnomaly: Boolean = false): SamplingStatistics {
        samples++
        if (hasAnomaly) anomalies++
        val timestamp = sample.timestampNs
        val previous = lastNs
        if (timestamp > 0 && (previous == null || timestamp > previous)) {
            if (firstNs == null) firstNs = timestamp
            if (previous != null) deltas += (timestamp - previous) / 1_000_000.0
            lastNs = timestamp
            validCount++
            window.addLast(timestamp)
            while (window.size > 1 && timestamp - window.first() > 2_000_000_000L) window.removeFirst()
        }
        return snapshot()
    }

    fun snapshot(fullDistribution: Boolean = false): SamplingStatistics {
        val first = firstNs
        val last = lastNs
        val elapsedNs = if (first != null && last != null) last - first else 0L
        val rate = if (validCount > 1 && elapsedNs > 0) (validCount - 1) * 1e9 / elapsedNs else null
        val windowNs = if (window.size > 1) window.last() - window.first() else 0L
        val distribution = if (fullDistribution) deltas else deltas.takeLast(100)
        val distributionMedian = median(distribution)
        return SamplingStatistics(
            sampleCount = samples,
            currentRateHz = if (windowNs > 0) (window.size - 1) * 1e9 / windowNs else null,
            averageRateHz = rate,
            averageDeltaMs = if (deltas.isNotEmpty()) deltas.average() else null,
            medianDeltaMs = distributionMedian,
            minDeltaMs = deltas.minOrNull(),
            maxDeltaMs = deltas.maxOrNull(),
            gapCount = distributionMedian?.let { med -> if (med > 0) deltas.count { it > 3 * med } else 0 } ?: 0,
            anomalyCount = anomalies,
        )
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
    }
}

data class SessionReport(
    val durationSeconds: Double?,
    val statistics: SamplingStatistics,
    val meanSpeedAccuracyMps: Double?,
    val bestSpeedAccuracyMps: Double?,
    val worstSpeedAccuracyMps: Double?,
    val minSpeedKmh: Double?,
    val maxSpeedKmh: Double?,
)

fun sessionReport(samples: List<SpeedSampleEntity>): SessionReport {
    val calculator = SamplingStatisticsCalculator()
    samples.forEach { calculator.accept(it.toDomain(), it.anomalyFlags != 0) }
    val validSpeeds = samples.map { it.toDomain() }.filter { it.hasSpeed && it.speedMps.isFinite() && it.speedMps >= 0 }.map { it.speedMps * 3.6 }
    val accuracies = samples.mapNotNull { it.toDomain().speedAccuracyMps }.filter { it.isFinite() && it >= 0 }
    val first = samples.firstOrNull()?.timestampNs
    val last = samples.lastOrNull()?.timestampNs
    return SessionReport(
        durationSeconds = if (first != null && last != null && last >= first) (last - first) / 1e9 else null,
        statistics = calculator.snapshot(fullDistribution = true),
        meanSpeedAccuracyMps = accuracies.takeIf { it.isNotEmpty() }?.average(),
        bestSpeedAccuracyMps = accuracies.minOrNull(),
        worstSpeedAccuracyMps = accuracies.maxOrNull(),
        minSpeedKmh = validSpeeds.minOrNull(),
        maxSpeedKmh = validSpeeds.maxOrNull(),
    )
}
