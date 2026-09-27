package com.roaddyno.app.dyno

import com.roaddyno.app.domain.model.SpeedSample
import java.io.Reader

data class RawCsvRecording(val samples: List<SpeedSample>, val configuration: RunConfiguration?)

/** Reads the raw columns exported by the logger; calculated CSV columns are ignored. */
object RawCsvReader {
    fun read(reader: Reader): List<SpeedSample> = readRecording(reader).samples

    fun readRecording(reader: Reader): RawCsvRecording = reader.buffered().useLines { lines ->
        val iterator = lines.iterator()
        require(iterator.hasNext()) { "CSV is empty." }
        val header = fields(iterator.next()).map { it.trim().removePrefix("\uFEFF") }
        val timestamp = header.indexOf("timestamp_ns")
        val speed = header.indexOf("speed_mps")
        val accuracy = header.indexOf("speed_accuracy_mps")
        val hasSpeed = header.indexOf("has_speed")
        require(timestamp >= 0 && speed >= 0) { "CSV needs timestamp_ns and speed_mps columns." }
        var configuration: RunConfiguration? = null
        val samples = iterator.asSequence().filter { it.isNotBlank() }.mapIndexed { index, line ->
            val cells = fields(line)
            fun cell(at: Int) = if (at >= 0) cells.getOrNull(at)?.trim() else null
            fun named(name: String) = cell(header.indexOf(name))?.takeIf { it.isNotBlank() }
            if (index == 0) {
                val mass = named("mass_kg")?.toDoubleOrNull()
                if (mass != null) configuration = RunConfiguration(
                    named("vehicle") ?: "", mass, named("gear")?.toIntOrNull(),
                    named("calibration_rpm")?.toDoubleOrNull(), named("calibration_speed_kmh")?.toDoubleOrNull(),
                    named("speed_at_2000_rpm_kmh")?.toDoubleOrNull(), named("speed_at_3000_rpm_kmh")?.toDoubleOrNull(),
                    named("tyre_size"),
                ).validate()
            }
            val timeNs = cell(timestamp)?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid timestamp at CSV row ${index + 2}.")
            val speedMps = cell(speed)?.toDoubleOrNull()
                ?: throw IllegalArgumentException("Invalid speed at CSV row ${index + 2}.")
            SpeedSample(
                timestampNs = timeNs, speedMps = speedMps,
                speedAccuracyMps = cell(accuracy)?.toDoubleOrNull(),
                latitude = named("latitude")?.toDoubleOrNull(), longitude = named("longitude")?.toDoubleOrNull(),
                altitudeM = named("altitude_m")?.toDoubleOrNull(), horizontalAccuracyM = named("horizontal_accuracy_m")?.toDoubleOrNull(),
                bearingDeg = named("bearing_deg")?.toDoubleOrNull(),
                hasSpeed = cell(hasSpeed)?.lowercase() != "false",
                receivedElapsedRealtimeNs = named("received_elapsed_realtime_ns")?.toLongOrNull() ?: timeNs,
                provider = named("provider"),
            )
        }.toList()
        RawCsvRecording(samples, configuration)
    }

    private fun fields(line: String): List<String> {
        val result = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            when {
                line[i] == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    cell.append('"'); i++
                }
                line[i] == '"' -> quoted = !quoted
                line[i] == ',' && !quoted -> { result += cell.toString(); cell.clear() }
                else -> cell.append(line[i])
            }
            i++
        }
        result += cell.toString()
        return result
    }
}
