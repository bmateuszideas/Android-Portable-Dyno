package com.roaddyno.app.dyno

import com.roaddyno.app.domain.model.SpeedSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class DynoEngineTest {
    private fun sample(second: Int, speed: Double) = SpeedSample(
        (second + 1L) * 1_000_000_000L, speed, 0.04,
        null, null, null, null, null,
    )

    @Test fun accelerationAndCoastProducePowerAndLossOnlyAtMeasuredSpeeds() {
        val samples = (0..20).map { sample(it, 10.0 + it * 0.5) } +
            (21..70).map { sample(it, 20.0 - (it - 20) * 0.2) }
        val result = DynoEngine().analyze(samples, 1_000.0, 3_000.0, 54.0)
        assertNotNull(result.coastStartSeconds)
        val middle = result.points.minByOrNull { kotlin.math.abs(it.speedKmh - 54.0) }!!
        assertEquals(7.5, middle.wheelPowerKw, 0.6)
        assertEquals(10.5, middle.correctedPowerKw!!, 1.1)
        assertEquals(3_000.0, middle.rpm!!, 150.0)
        assertTrue(result.peakTorqueNm!! > 0)
        assertTrue(result.points.first().correctedPowerKw == null)
    }

    @Test fun exportedRawCsvCanBeAnalyzedWithoutCoordinates() {
        val csv = "sample_index,timestamp_ns,speed_mps,speed_accuracy_mps,provider,has_speed\n" +
            "0,1000000000,10.0,0.04,\"gps\",true\n" +
            "1,2000000000,10.5,0.05,\"gps\",true\n"
        val samples = RawCsvReader.read(StringReader(csv))
        assertEquals(2, samples.size)
        assertEquals(10.5, samples[1].speedMps, 0.0)
        assertEquals(2_000_000_000L, samples[1].timestampNs)
    }
}
