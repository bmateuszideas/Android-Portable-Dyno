package com.roaddyno.app.dyno

import com.roaddyno.app.domain.model.SpeedSample
import kotlin.math.PI

/** Nominal unloaded geometry from the sidewall marking; not a wheel sensor. */
data class TyreGeometry(val widthMm: Int, val aspectPercent: Int, val rimInches: Double) {
    init {
        require(widthMm > 0 && aspectPercent > 0 && rimInches.isFinite() && rimInches > 0)
    }
    val diameterM: Double get() = (rimInches * 25.4 + 2 * widthMm * aspectPercent / 100.0) / 1000
    val circumferenceM: Double get() = PI * diameterM
    fun wheelRpm(speedMps: Double) = speedMps * 60 / circumferenceM
    fun totalRatio(rpmPerKmh: Double) = rpmPerKmh * 3.6 * circumferenceM / 60

    companion object {
        fun parse(text: String): TyreGeometry {
            val match = Regex("""^\s*(\d{3})\s*/\s*(\d{2,3})\s*(?:Z?R)\s*(\d{2}(?:[.,]\d+)?)\s*$""",
                RegexOption.IGNORE_CASE).matchEntire(text)
                ?: throw IllegalArgumentException("Podaj rozmiar opony, np. 225/45 R17.")
            return TyreGeometry(match.groupValues[1].toInt(), match.groupValues[2].toInt(),
                match.groupValues[3].replace(',', '.').toDouble())
        }
    }
}

data class WheelTravel(
    val distanceM: Double = 0.0,
    val wheelRpm: Double? = null,
    val wheelTurns: Double? = null,
    val omittedIntervals: Int = 0,
)

/** Integrates GNSS speed in time. Tyre turns are derived from that same distance. */
class WheelTravelCalculator(private val tyre: TyreGeometry?) {
    private var previous: SpeedSample? = null
    private var lastTimestampNs = 0L
    private var distanceM = 0.0
    private var omitted = 0
    private var currentRpm: Double? = null

    fun accept(sample: SpeedSample): WheelTravel {
        if (sample.timestampNs <= lastTimestampNs) return snapshot()
        lastTimestampNs = sample.timestampNs
        if (!sample.hasSpeed || !sample.speedMps.isFinite() || sample.speedMps < 0) {
            previous = null
            currentRpm = null
            omitted++
            return snapshot()
        }
        previous?.let {
            val dt = (sample.timestampNs - it.timestampNs) / 1e9
            if (dt <= 5.0) distanceM += (it.speedMps + sample.speedMps) * .5 * dt
            else omitted++
        }
        previous = sample
        currentRpm = tyre?.wheelRpm(sample.speedMps)
        return snapshot()
    }

    fun snapshot() = WheelTravel(distanceM, currentRpm, tyre?.let { distanceM / it.circumferenceM }, omitted)
}
