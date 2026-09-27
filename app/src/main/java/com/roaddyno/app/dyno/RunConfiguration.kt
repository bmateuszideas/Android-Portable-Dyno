package com.roaddyno.app.dyno

/** Calculation inputs are stored with each recording, separately from raw samples. */
data class RunConfiguration(
    val vehicleName: String = "",
    val massKg: Double,
    val gear: Int? = null,
    val calibrationRpm: Double? = null,
    val calibrationSpeedKmh: Double? = null,
) {
    fun validate(): RunConfiguration {
        require(massKg.isFinite() && massKg > 0) { "Podaj rzeczywistą masę pomiarową w kg." }
        require(gear == null || gear > 0) { "Podaj numer biegu." }
        require((calibrationRpm == null) == (calibrationSpeedKmh == null)) {
            "Uzupełnij oba pola kalibracji RPM albo pozostaw oba puste."
        }
        require(calibrationRpm == null || (calibrationRpm.isFinite() && calibrationRpm > 0 &&
            calibrationSpeedKmh!!.isFinite() && calibrationSpeedKmh > 0)) { "Nieprawidłowa kalibracja RPM." }
        return this
    }
}
