package com.roaddyno.app.data

import com.roaddyno.app.data.database.SampleAnomaly
import com.roaddyno.app.data.database.toEntity
import com.roaddyno.app.domain.model.SpeedSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSampleMappingTest {
    @Test fun nonFiniteSpeedKeepsOriginalBitsAndDiagnosticFlag() {
        val raw = SpeedSample(123L, Double.NaN, Double.POSITIVE_INFINITY, null, null, null, null, null)
        val flags = SampleAnomaly.flags(raw, null)
        val stored = raw.toEntity(sessionId = 7, index = 2, flags = flags)
        assertEquals(0.0, stored.speedMps, 0.0)
        assertTrue(flags and SampleAnomaly.INVALID_SPEED != 0)
        assertEquals(raw.speedMps.toBits(), stored.speedRawBits)
        assertTrue(stored.toDomain().speedMps.isNaN())
        assertTrue(stored.toDomain().speedAccuracyMps == Double.POSITIVE_INFINITY)
    }
}
