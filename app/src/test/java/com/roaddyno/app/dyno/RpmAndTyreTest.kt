package com.roaddyno.app.dyno

import com.roaddyno.app.domain.model.SpeedSample
import com.roaddyno.app.export.DynoCsvWriter
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter
import kotlin.math.PI

class RpmAndTyreTest {
    private fun sample(t: Double, speed: Double) = SpeedSample(((t + 1) * 1e9).toLong(), speed, .05,
        null, null, null, null, null)

    @Test fun twoCapturedPointsCalibrateBothTargetsAndZero() {
        val calibration = RpmCalibration(40.0, 60.0)
        assertEquals(2000.0, calibration.rpmPerKmh * 40, 1e-9)
        assertEquals(3000.0, calibration.rpmPerKmh * 60, 1e-9)
        assertEquals(4000.0, calibration.rpmPerKmh * 80, 1e-9)
        assertEquals(0.0, calibration.differencePercent, 0.0)
    }

    @Test fun noisyPointsUseBothObservationsAndReportDisagreement() {
        val cal = RpmCalibration(40.0, 62.0)
        assertEquals((2000.0 * 40 + 3000.0 * 62) / (40 * 40 + 62 * 62), cal.rpmPerKmh, 1e-10)
        assertTrue(cal.differencePercent > 3)
        assertTrue(cal.rpmPerKmh > 3000.0 / 62 && cal.rpmPerKmh < 2000.0 / 40)
    }

    @Test(expected = IllegalArgumentException::class)
    fun reversedSpeedCaptureCannotBecomeCalibration() { RpmCalibration(60.0, 40.0) }

    @Test(expected = IllegalArgumentException::class)
    fun incompleteCalibrationCannotBeSaved() {
        RunConfiguration(massKg = 1000.0, gear = 3, speed2000Kmh = 40.0).validate()
    }

    @Test fun freshSpeedRequiredAtEachButtonPress() {
        val s = sample(0.0, 15.0)
        assertTrue(RpmCalibration.canCapture(s, 2_000_000_000L))
        assertFalse(RpmCalibration.canCapture(s, 4_000_000_000L))
        assertFalse(RpmCalibration.canCapture(s, 500_000_000L))
        assertFalse(RpmCalibration.canCapture(s.copy(hasSpeed = false), 2_000_000_000L))
        assertFalse(RpmCalibration.canCapture(s.copy(speedMps = 0.0), 2_000_000_000L))
        assertFalse(RpmCalibration.canCapture(s.copy(speedMps = Double.NaN), 2_000_000_000L))
        assertFalse(RpmCalibration.canCapture(null, 2_000_000_000L))
    }

    @Test fun tyreSizeProducesNominalDiameterAndWheelRpm() {
        val tyre = TyreGeometry.parse("225/45 R17")
        assertEquals(.6343, tyre.diameterM, 1e-10)
        assertEquals(.6343 * PI, tyre.circumferenceM, 1e-10)
        assertEquals(60.0, tyre.wheelRpm(tyre.circumferenceM), 1e-9)
        assertEquals(tyre, TyreGeometry.parse("225/45zr17"))
        assertEquals(22.5, TyreGeometry.parse("315/70 R22,5").rimInches, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedTyreCannotSilentlyUseDefault() { TyreGeometry.parse("225/45") }

    @Test fun distanceIsTimeIntegralAndWheelDistanceIsSameSource() {
        val tyre = TyreGeometry.parse("225/45 R17")
        val calculator = WheelTravelCalculator(tyre)
        calculator.accept(sample(0.0, 10.0))
        calculator.accept(sample(1.0, 12.0))
        val result = calculator.accept(sample(3.0, 14.0))
        assertEquals(37.0, result.distanceM, 1e-9)
        assertEquals(result.distanceM, result.wheelTurns!! * tyre.circumferenceM, 1e-9)
        assertEquals(14 * 60 / tyre.circumferenceM, result.wheelRpm!!, 1e-9)
    }

    @Test fun changingTyreChangesTurnsButDoesNotInventDifferentDistance() {
        val a = WheelTravelCalculator(TyreGeometry.parse("225/45 R17"))
        val b = WheelTravelCalculator(TyreGeometry.parse("205/55 R16"))
        (0..10).forEach { a.accept(sample(it.toDouble(), 20.0)); b.accept(sample(it.toDouble(), 20.0)) }
        assertEquals(200.0, a.snapshot().distanceM, 1e-8)
        assertEquals(a.snapshot().distanceM, b.snapshot().distanceM, 0.0)
        assertNotEquals(a.snapshot().wheelTurns, b.snapshot().wheelTurns)
    }

    @Test fun duplicatesAndLongGapsDoNotAddInventedDistance() {
        val calculator = WheelTravelCalculator(null)
        calculator.accept(sample(0.0, 10.0))
        calculator.accept(sample(1.0, 10.0))
        calculator.accept(sample(1.0, 99.0))
        val result = calculator.accept(sample(10.0, 10.0))
        assertEquals(10.0, result.distanceM, 0.0)
        assertEquals(1, result.omittedIntervals)
        assertNull(result.wheelTurns)
    }

    @Test fun bothCalibrationPointsDriveTheActualDynoEngineAndExport() {
        val config = RunConfiguration("Car", 1000.0, 3, speed2000Kmh = 40.0, speed3000Kmh = 60.0,
            tyreSize = "225/45 R17").validate()
        val samples = (0..70).map { sample(it.toDouble(), if (it <= 20) 10 + it * .5 else 20 - (it - 20) * .2) }
        val result = DynoEngine().analyze(samples, config.massKg, config.effectiveCalibrationRpm, config.effectiveCalibrationSpeedKmh)
        val p = result.points.minBy { kotlin.math.abs(it.speedKmh - 54) }
        assertEquals(2700.0, p.rpm!!, 1e-8)
        val writer = StringWriter()
        DynoCsvWriter.write(writer, result, config)
        val header = writer.toString().lineSequence().first()
        assertTrue(header.contains("speed_at_2000_rpm_kmh"))
        assertTrue(header.contains("wheel_rpm_from_gnss"))
        assertTrue(writer.toString().contains("225/45 R17"))
    }

    @Test fun rawCsvImportRestoresCalibrationAndTyreParameters() {
        val csv = "timestamp_ns,speed_mps,mass_kg,gear,speed_at_2000_rpm_kmh,speed_at_3000_rpm_kmh,tyre_size,latitude,received_elapsed_realtime_ns\n" +
            "1000000000,10,1000,3,40,60,225/45 R17,52.0,1000010000\n"
        val recording = RawCsvReader.readRecording(StringReader(csv))
        assertEquals(50.0, recording.configuration!!.twoPointCalibration()!!.rpmPerKmh, 1e-9)
        assertEquals("225/45 R17", recording.configuration.tyreSize)
        assertEquals(52.0, recording.samples.single().latitude!!, 0.0)
        assertEquals(1_000_010_000L, recording.samples.single().receivedElapsedRealtimeNs)
    }
}
