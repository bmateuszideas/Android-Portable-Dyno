package com.roaddyno.app.measurement

import com.roaddyno.app.domain.model.SpeedSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SamplingStatisticsCalculatorTest {
    private fun sample(timestampNs: Long) = SpeedSample(timestampNs, 10.0, 0.2, null, null, null, null, null)

    @Test fun tenHertzAndAnomalyAreCountedWithoutChangingTiming() {
        val calculator = SamplingStatisticsCalculator()
        listOf(1_000_000_000L, 1_100_000_000L, 1_200_000_000L, 1_300_000_000L, 1_400_000_000L)
            .forEach { calculator.accept(sample(it)) }
        calculator.accept(sample(1_300_000_000L), hasAnomaly = true)
        val statistics = calculator.snapshot(fullDistribution = true)
        assertEquals(6L, statistics.sampleCount)
        assertEquals(1, statistics.anomalyCount)
        assertEquals(10.0, statistics.currentRateHz!!, 1e-8)
        assertEquals(10.0, statistics.averageRateHz!!, 1e-8)
        assertEquals(100.0, statistics.medianDeltaMs!!, 1e-8)
        assertEquals(0, statistics.gapCount)
    }

    @Test fun oneSampleHasNoInventedRate() {
        val calculator = SamplingStatisticsCalculator()
        calculator.accept(sample(1_000_000_000L))
        assertNull(calculator.snapshot().currentRateHz)
        assertNull(calculator.snapshot().averageRateHz)
    }
}
