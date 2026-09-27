package com.roaddyno.app.dyno

/** Calculation inputs are stored with each recording, separately from raw samples. */
data class RunConfiguration(
    val vehicleName: String = "",
    val massKg: Double,
    val gear: Int? = null,
    val calibrationRpm: Double? = null,
    val calibrationSpeedKmh: Double? = null,
    val speed2000Kmh: Double? = null,
    val speed3000Kmh: Double? = null,
    val tyreSize: String? = null,
) {
    fun twoPointCalibration(): RpmCalibration? = speed2000Kmh?.let { RpmCalibration(it, speed3000Kmh!!) }
    val effectiveCalibrationRpm: Double? get() = if (speed2000Kmh != null) 2000.0 else calibrationRpm
    val effectiveCalibrationSpeedKmh: Double? get() = twoPointCalibration()?.let { 2000 / it.rpmPerKmh } ?: calibrationSpeedKmh
    fun tyreGeometry(): TyreGeometry? = tyreSize?.takeIf { it.isNotBlank() }?.let { TyreGeometry.parse(it) }

    fun validate(): RunConfiguration {
        require(massKg.isFinite() && massKg > 0) { "Podaj rzeczywistą masę pomiarową w kg." }
        require(gear == null || gear > 0) { "Podaj numer biegu." }
        require((speed2000Kmh == null) == (speed3000Kmh == null)) { "Kalibracja wymaga obu punktów: 2000 i 3000 RPM." }
        if (speed2000Kmh != null) {
            require(gear != null) { "Wybierz bieg dla kalibracji 2000 / 3000 RPM." }
            twoPointCalibration()
        }
        tyreGeometry()
        require((calibrationRpm == null) == (calibrationSpeedKmh == null)) {
            "Uzupełnij oba pola kalibracji RPM albo pozostaw oba puste."
        }
        require(calibrationRpm == null || (calibrationRpm.isFinite() && calibrationRpm > 0 &&
            calibrationSpeedKmh!!.isFinite() && calibrationSpeedKmh > 0)) { "Nieprawidłowa kalibracja RPM." }
        return this
    }
}
