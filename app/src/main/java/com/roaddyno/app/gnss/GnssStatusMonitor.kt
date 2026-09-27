package com.roaddyno.app.gnss

import android.content.Context
import android.location.GnssMeasurementRequest
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GnssStatusInfo(
    val visible: Int? = null,
    val usedInFix: Int? = null,
    val started: Boolean = false,
    val rawRateHz: Double? = null,
    val rawEventCount: Long = 0,
    val fullTrackingActive: Boolean? = null,
    val rawMeasurementsAvailable: Boolean = false,
)

class GnssStatusMonitor(private val context: Context, private val manager: LocationManager) {
    private val mutableStatus = MutableStateFlow(GnssStatusInfo())
    val status = mutableStatus.asStateFlow()
    private var registered = false
    private var measurementsRegistered = false
    private var firstRawNs: Long? = null
    private var rawEventCount = 0L
    private val callback = object : GnssStatus.Callback() {
        override fun onStarted() { mutableStatus.value = mutableStatus.value.copy(started = true) }
        override fun onStopped() { mutableStatus.value = mutableStatus.value.copy(visible = null, usedInFix = null, started = false) }
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (index in 0 until status.satelliteCount) if (status.usedInFix(index)) used++
            mutableStatus.value = mutableStatus.value.copy(visible = status.satelliteCount, usedInFix = used, started = true)
        }
    }
    private val measurementsCallback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
            val now = SystemClock.elapsedRealtimeNanos()
            if (firstRawNs == null) firstRawNs = now
            rawEventCount++
            val elapsedNs = now - firstRawNs!!
            mutableStatus.value = mutableStatus.value.copy(
                rawRateHz = if (rawEventCount > 1 && elapsedNs > 0) (rawEventCount - 1) * 1e9 / elapsedNs else null,
                rawEventCount = rawEventCount,
                fullTrackingActive = event.isFullTracking,
            )
        }
    }

    fun start() {
        if (!registered) {
            registered = manager.registerGnssStatusCallback(ContextCompat.getMainExecutor(context), callback)
        }
        if (!measurementsRegistered) {
            // Full tracking disables GNSS duty cycling. The raw event cadence is diagnostic;
            // vehicle speed still comes exclusively from GPS_PROVIDER Location.speed.
            val builder = GnssMeasurementRequest.Builder().setFullTracking(true)
            if (Build.VERSION.SDK_INT >= 33) builder.setIntervalMillis(0)
            measurementsRegistered = runCatching {
                manager.registerGnssMeasurementsCallback(
                    builder.build(), ContextCompat.getMainExecutor(context), measurementsCallback,
                )
            }.getOrDefault(false)
            mutableStatus.value = mutableStatus.value.copy(rawMeasurementsAvailable = measurementsRegistered)
        }
    }

    fun stop() {
        if (registered) manager.unregisterGnssStatusCallback(callback)
        if (measurementsRegistered) manager.unregisterGnssMeasurementsCallback(measurementsCallback)
        registered = false
        measurementsRegistered = false
        firstRawNs = null
        rawEventCount = 0
        mutableStatus.value = GnssStatusInfo()
    }
}
