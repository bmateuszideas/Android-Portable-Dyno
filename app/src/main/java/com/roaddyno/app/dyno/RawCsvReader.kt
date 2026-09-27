package com.roaddyno.app.dyno

import com.roaddyno.app.domain.model.SpeedSample
import java.io.Reader

/** Reads the raw columns exported by the logger; calculated CSV columns are ignored. */
object RawCsvReader {
    fun read(reader: Reader): List<SpeedSample> = reader.buffered().useLines { lines ->
        val iterator = lines.iterator()
        require(iterator.hasNext()) { "CSV is empty." }
        val header = fields(iterator.next()).map { it.trim().removePrefix("\uFEFF") }
        val timestamp = header.indexOf("timestamp_ns")
        val speed = header.indexOf("speed_mps")
        val accuracy = header.indexOf("speed_accuracy_mps")
        val hasSpeed = header.indexOf("has_speed")
        require(timestamp >= 0 && speed >= 0) { "CSV needs timestamp_ns and speed_mps columns." }
        iterator.asSequence().filter { it.isNotBlank() }.mapIndexed { index, line ->
            val cells = fields(line)
            fun cell(at: Int) = if (at >= 0) cells.getOrNull(at)?.trim() else null
            val timeNs = cell(timestamp)?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid timestamp at CSV row ${index + 2}.")
            val speedMps = cell(speed)?.toDoubleOrNull()
                ?: throw IllegalArgumentException("Invalid speed at CSV row ${index + 2}.")
            SpeedSample(
                timestampNs = timeNs, speedMps = speedMps,
                speedAccuracyMps = cell(accuracy)?.toDoubleOrNull(),
                latitude = null, longitude = null, altitudeM = null,
                horizontalAccuracyM = null, bearingDeg = null,
                hasSpeed = cell(hasSpeed)?.lowercase() != "false",
            )
        }.toList()
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
