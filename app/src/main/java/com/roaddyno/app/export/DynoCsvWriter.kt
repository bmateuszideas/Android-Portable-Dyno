package com.roaddyno.app.export

import com.roaddyno.app.dyno.DynoEngine
import com.roaddyno.app.dyno.DynoResult
import com.roaddyno.app.dyno.RunConfiguration
import java.io.Writer

/** Separate derived export: never replaces the original raw CSV. */
object DynoCsvWriter {
    fun write(writer: Writer, result: DynoResult, config: RunConfiguration) {
        writer.write("algorithm,vehicle,mass_kg,gear,calibration_rpm,calibration_speed_kmh,elapsed_s,speed_kmh,rpm,acceleration_power_kw,loss_power_kw,corrected_power_kw,torque_nm\n")
        for (point in result.points) {
            val fields = listOf(DynoEngine.VERSION, config.vehicleName, config.massKg, config.gear,
                config.calibrationRpm, config.calibrationSpeedKmh, point.elapsedSeconds, point.speedKmh,
                point.rpm, point.wheelPowerKw, point.lossPowerKw, point.correctedPowerKw, point.torqueNm)
            writer.write(fields.joinToString(",") { value ->
                val text = value?.toString() ?: ""
                "\"${text.replace("\"", "\"\"")}\""
            } + "\n")
        }
    }
}
