package com.roaddyno.app.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.roaddyno.app.domain.model.SpeedSample

@Entity(
    tableName = "speed_samples",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId"), Index(value = ["sessionId", "sampleIndex"], unique = true)],
)
data class SpeedSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val sampleIndex: Long,
    val timestampNs: Long,
    val receivedElapsedRealtimeNs: Long,
    val speedMps: Double,
    val speedRawBits: Long,
    val hasSpeed: Boolean,
    val speedAccuracyMps: Double?,
    val speedAccuracyRawBits: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeM: Double?,
    val horizontalAccuracyM: Double?,
    val bearingDeg: Double?,
    val provider: String?,
    val anomalyFlags: Int,
) {
    fun toDomain() = SpeedSample(
        timestampNs, Double.fromBits(speedRawBits), speedAccuracyRawBits?.let { Double.fromBits(it) }, latitude, longitude, altitudeM,
        horizontalAccuracyM, bearingDeg, hasSpeed, receivedElapsedRealtimeNs, provider,
    )
}

object SampleAnomaly {
    const val NO_SPEED = 1
    const val INVALID_TIMESTAMP = 2
    const val NON_MONOTONIC_TIMESTAMP = 4
    const val INVALID_SPEED = 8

    fun flags(sample: SpeedSample, previousTimestampNs: Long?): Int {
        var flags = 0
        if (!sample.hasSpeed) flags = flags or NO_SPEED
        if (sample.timestampNs <= 0) flags = flags or INVALID_TIMESTAMP
        if (previousTimestampNs != null && sample.timestampNs <= previousTimestampNs) {
            flags = flags or NON_MONOTONIC_TIMESTAMP
        }
        if (!sample.speedMps.isFinite() || sample.speedMps < 0) flags = flags or INVALID_SPEED
        return flags
    }
}

fun SpeedSample.toEntity(sessionId: Long, index: Long, flags: Int) = SpeedSampleEntity(
    sessionId = sessionId,
    sampleIndex = index,
    timestampNs = timestampNs,
    receivedElapsedRealtimeNs = receivedElapsedRealtimeNs,
    speedMps = speedMps.takeIf { it.isFinite() } ?: 0.0,
    speedRawBits = speedMps.toBits(),
    hasSpeed = hasSpeed,
    speedAccuracyMps = speedAccuracyMps?.takeIf { it.isFinite() },
    speedAccuracyRawBits = speedAccuracyMps?.toBits(),
    latitude = latitude,
    longitude = longitude,
    altitudeM = altitudeM,
    horizontalAccuracyM = horizontalAccuracyM,
    bearingDeg = bearingDeg,
    provider = provider,
    anomalyFlags = flags,
)
