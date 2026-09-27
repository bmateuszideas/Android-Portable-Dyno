package com.roaddyno.app.gnss

import android.location.Location
import android.os.SystemClock
import com.roaddyno.app.domain.model.SpeedSample

internal fun Location.toSpeedSample(): SpeedSample = SpeedSample(
    timestampNs = elapsedRealtimeNanos,
    speedMps = speed.toDouble(),
    speedAccuracyMps = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond.toDouble() else null,
    latitude = latitude,
    longitude = longitude,
    altitudeM = if (hasAltitude()) altitude else null,
    horizontalAccuracyM = if (hasAccuracy()) accuracy.toDouble() else null,
    bearingDeg = if (hasBearing()) bearing.toDouble() else null,
    hasSpeed = hasSpeed(),
    receivedElapsedRealtimeNs = SystemClock.elapsedRealtimeNanos(),
    provider = provider,
)
