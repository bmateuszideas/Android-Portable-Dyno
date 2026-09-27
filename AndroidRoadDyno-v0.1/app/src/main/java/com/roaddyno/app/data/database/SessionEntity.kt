package com.roaddyno.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

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
)
