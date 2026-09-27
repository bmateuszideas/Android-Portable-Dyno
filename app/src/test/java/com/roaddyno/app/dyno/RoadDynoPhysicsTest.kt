package com.roaddyno.app.dyno

import com.roaddyno.app.domain.model.SpeedSample
import com.roaddyno.app.export.DynoCsvWriter
import org.junit.Assert.*
import org.junit.Test
import java.io.StringWriter
import kotlin.math.abs

class RoadDynoPhysicsTest {
    private fun sample(t: Double, v: Double) = SpeedSample(((t + 1) * 1e9).toLong(), v, .04,
        null, null, null, null, null)
    private fun run(hz: Int = 1, coastSeconds: Int = 50): List<SpeedSample> =
        (0..(20 + coastSeconds) * hz).map {
            val t = it.toDouble() / hz
            sample(t, if (t <= 20) 10 + .5 * t else 20 - .2 * (t - 20))
        }
    private fun at54(result: DynoResult) = result.points.minBy { abs(it.speedKmh - 54) }

    @Test fun knownPhysicsAndTorqueAtMatchingSpeed() {
        val result = DynoEngine().analyze(run(), 1000.0, 3000.0, 54.0)
        val p = at54(result)
        assertEquals(7.5, p.wheelPowerKw, .001)
        assertEquals(3.0, p.lossPowerKw!!, .001)
        assertEquals(10.5, p.correctedPowerKw!!, .001)
        assertEquals(33.422538, p.torqueNm!!, .001)
    }

    @Test fun sameWindowAndPhysicsAtOneAndTwentyHertz() {
        val slow = at54(DynoEngine().analyze(run(1), 1000.0))
        val fast = at54(DynoEngine().analyze(run(20), 1000.0))
        assertEquals(slow.wheelPowerKw, fast.wheelPowerKw, .01)
        assertEquals(slow.lossPowerKw!!, fast.lossPowerKw!!, .01)
    }

    @Test fun doublingMassDoublesPower() {
        val a = at54(DynoEngine().analyze(run(), 1000.0))
        val b = at54(DynoEngine().analyze(run(), 2000.0))
        assertEquals(a.correctedPowerKw!! * 2, b.correctedPowerKw!!, 1e-8)
    }

    @Test fun longAccelerationIsNotTruncatedToTwentySeconds() {
        val samples = (0..100).map { sample(it.toDouble(), if (it <= 40) 10 + it * .5 else 30 - (it - 40) * .2) }
        val result = DynoEngine().analyze(samples, 1000.0)
        assertEquals(0.0, result.accelerationStartSeconds, 0.0)
        assertEquals(40.0, result.peakSeconds, 0.0)
    }

    @Test fun accelerationWithoutCoastStillHasRawPowerAndNoInventedLoss() {
        val result = DynoEngine().analyze(run().take(21), 1000.0)
        assertNull(result.peakCorrectedPowerKw)
        assertTrue(result.points.all { it.lossPowerKw == null && it.torqueNm == null })
    }

    @Test fun incompleteCoastNeverExtrapolatesLosses() {
        val result = DynoEngine().analyze(run(coastSeconds = 20), 1000.0)
        assertTrue(result.points.any { it.correctedPowerKw != null })
        assertTrue(result.points.filter { it.speedKmh < 57.6 }.all { it.lossPowerKw == null })
    }

    @Test(expected = IllegalArgumentException::class)
    fun multipleRunsAreNotSilentlyScoredAndSelected() {
        val first = run()
        val second = run().map { it.copy(timestampNs = it.timestampNs + 71_000_000_000L) }
        DynoEngine().analyze(first + second, 1000.0)
    }

    @Test fun analysisPreservesRawOrderingAndValuesWithDuplicateTimestamp() {
        val original = run().toMutableList()
        original.add(10, original[9].copy(speedMps = 99.0))
        val before = original.toList()
        DynoEngine().analyze(original, 1000.0)
        assertEquals(before, original)
    }

    @Test(expected = IllegalArgumentException::class)
    fun gapsInsideRunAreNotBridged() {
        DynoEngine().analyze(run().filterIndexed { i, _ -> i !in 6..12 }, 1000.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun partialCalibrationIsRejected() {
        DynoEngine().analyze(run(), 1000.0, 3000.0, null)
    }

    @Test fun derivedCsvCarriesInputsAndDoesNotInventMissingLoss() {
        val result = DynoEngine().analyze(run(coastSeconds = 20), 1000.0)
        val writer = StringWriter()
        DynoCsvWriter.write(writer, result, RunConfiguration("Car, \"A\"", 1000.0))
        val text = writer.toString()
        assertTrue(text.contains("\"Car, \"\"A\"\"\""))
        assertEquals(result.points.size + 1, text.trimEnd().lines().size)
        assertTrue(text.lines()[1].endsWith("\"\",\"\",\"\""))
    }
}
