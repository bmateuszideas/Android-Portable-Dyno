package com.roaddyno.app.dyno

import com.roaddyno.app.domain.model.SpeedSample
import kotlin.math.abs

data class RpmCalibration(val speed2000Kmh: Double, val speed3000Kmh: Double) {
    init {
        require(speed2000Kmh.isFinite() && speed3000Kmh.isFinite() && speed2000Kmh > 0 &&
            speed3000Kmh > speed2000Kmh) {
            "Prędkość przy 3000 RPM musi być większa niż przy 2000 RPM. Utrzymaj obroty na tym samym biegu."
        }
    }

    // Least squares RPM = k * speed, constrained through the physical origin.
    // An arbitrary nonzero RPM at standstill would misrepresent a fixed gear ratio.
    val rpmPerKmh: Double get() = (2000 * speed2000Kmh + 3000 * speed3000Kmh) /
        (speed2000Kmh * speed2000Kmh + speed3000Kmh * speed3000Kmh)
    val differencePercent: Double get() {
        val a = 2000 / speed2000Kmh
        val b = 3000 / speed3000Kmh
        return 100 * abs(a - b) / ((a + b) / 2)
    }

    companion object {
        fun canCapture(sample: SpeedSample?, nowNs: Long): Boolean = sample != null &&
            sample.hasSpeed && sample.speedMps.isFinite() && sample.speedMps > 0 &&
            sample.timestampNs > 0 && nowNs >= sample.timestampNs &&
            nowNs - sample.timestampNs <= 2_500_000_000L
    }
}
