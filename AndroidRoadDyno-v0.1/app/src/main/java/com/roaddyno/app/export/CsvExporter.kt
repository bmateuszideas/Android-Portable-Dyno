package com.roaddyno.app.export

import android.content.Context
import android.net.Uri
import com.roaddyno.app.data.database.SpeedSampleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class CsvExporter(private val context: Context) {
    suspend fun export(uri: Uri, samples: List<SpeedSampleEntity>) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "w")
            ?: throw IOException("The selected document could not be opened.")
        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.appendLine("sample_index,timestamp_ns,received_elapsed_realtime_ns,elapsed_s,speed_mps,speed_kmh,has_speed,speed_accuracy_mps,speed_accuracy_kmh,latitude,longitude,altitude_m,horizontal_accuracy_m,bearing_deg,provider,anomaly_flags")
            val first = samples.firstOrNull()?.timestampNs ?: 0L
            for (sample in samples) {
                val raw = sample.toDomain()
                val fields = listOf(
                    sample.sampleIndex.toString(),
                    sample.timestampNs.toString(),
                    sample.receivedElapsedRealtimeNs.toString(),
                    String.format(Locale.US, "%.9f", (sample.timestampNs - first) / 1e9),
                    raw.speedMps.toString(),
                    (raw.speedMps * 3.6).toString(),
                    sample.hasSpeed.toString(),
                    raw.speedAccuracyMps.csv(),
                    raw.speedAccuracyMps?.times(3.6).csv(),
                    sample.latitude.csv(),
                    sample.longitude.csv(),
                    sample.altitudeM.csv(),
                    sample.horizontalAccuracyM.csv(),
                    sample.bearingDeg.csv(),
                    sample.provider.csvText(),
                    sample.anomalyFlags.toString(),
                )
                writer.appendLine(fields.joinToString(","))
            }
        }
    }

    private fun Double?.csv() = this?.toString() ?: ""
    private fun String?.csvText() = this?.let { "\"${it.replace("\"", "\"\"")}\"" } ?: ""
}
