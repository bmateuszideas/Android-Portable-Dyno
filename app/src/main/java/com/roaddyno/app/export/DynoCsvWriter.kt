package com.roaddyno.app.export

import com.roaddyno.app.dyno.DynoEngine
import com.roaddyno.app.dyno.DynoResult
import com.roaddyno.app.dyno.RunConfiguration
import java.io.Writer

/** Separate derived export: never replaces the original raw CSV. */
object DynoCsvWriter {
    fun write(writer: Writer, result: DynoResult, config: RunConfiguration) {
        val tyre = config.tyreGeometry()
        writer.write("algorithm,vehicle,mass_kg,gear,calibration_rpm,calibration_speed_kmh,speed_at_2000_rpm_kmh,speed_at_3000_rpm_kmh,calibration_difference_percent,tyre_size,nominal_tyre_diameter_m,nominal_tyre_circumference_m,wheel_rpm_from_gnss,elapsed_s,speed_kmh,rpm,acceleration_power_kw,loss_power_kw,corrected_power_kw,torque_nm\n")
        for (point in result.points) {
            val fields = listOf(DynoEngine.VERSION, config.vehicleName, config.massKg, config.gear,
                config.effectiveCalibrationRpm, config.effectiveCalibrationSpeedKmh,
                config.speed2000Kmh, config.speed3000Kmh, config.twoPointCalibration()?.differencePercent,
                config.tyreSize, tyre?.diameterM, tyre?.circumferenceM, tyre?.wheelRpm(point.speedKmh / 3.6),
                point.elapsedSeconds, point.speedKmh,
                point.rpm, point.wheelPowerKw, point.lossPowerKw, point.correctedPowerKw, point.torqueNm)
            writer.write(fields.joinToString(",") { value ->
                val text = value?.toString() ?: ""
                "\"${text.replace("\"", "\"\"")}\""
            } + "\n")
        }
    }
}
