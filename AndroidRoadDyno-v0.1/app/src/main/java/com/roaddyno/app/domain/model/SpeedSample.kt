package com.roaddyno.app.domain.model

/** A raw speed fix from the platform GNSS provider. Time is monotonic nanoseconds. */
data class SpeedSample(
    val timestampNs: Long,
    val speedMps: Double,
    val speedAccuracyMps: Double?,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeM: Double?,
    val horizontalAccuracyM: Double?,
    val bearingDeg: Double?,
    val hasSpeed: Boolean = true,
    val receivedElapsedRealtimeNs: Long = timestampNs,
    val provider: String? = null,
)
