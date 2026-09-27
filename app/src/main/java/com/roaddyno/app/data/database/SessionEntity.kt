package com.roaddyno.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.roaddyno.app.dyno.RunConfiguration

@Entity(tableName = "measurement_sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMillis: Long,
    val startedElapsedRealtimeNs: Long? = null,
    val endedElapsedRealtimeNs: Long? = null,
    val deviceManufacturer: String,
    val deviceModel: String,
    val androidVersion: String,
    val sampleCount: Int = 0,
    val status: String = "RECORDING",
    @ColumnInfo(defaultValue = "''") val vehicleName: String = "",
    val measurementMassKg: Double? = null,
    val measurementGear: Int? = null,
    val calibrationRpm: Double? = null,
    val calibrationSpeedKmh: Double? = null,
    val speed2000Kmh: Double? = null,
    val speed3000Kmh: Double? = null,
    val tyreSize: String? = null,
) {
    fun configuration(): RunConfiguration? = measurementMassKg?.let {
        RunConfiguration(vehicleName, it, measurementGear, calibrationRpm, calibrationSpeedKmh, speed2000Kmh, speed3000Kmh, tyreSize)
    }
}
