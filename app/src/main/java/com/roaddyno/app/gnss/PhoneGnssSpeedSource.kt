package com.roaddyno.app.gnss

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import androidx.core.content.ContextCompat
import android.content.Context
import com.roaddyno.app.domain.model.SpeedSample
import com.roaddyno.app.domain.source.SpeedSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Reads only GPS_PROVIDER fixes and forwards them without filtering or persistence. */
class PhoneGnssSpeedSource(
    private val context: Context,
    private val locationManager: LocationManager,
) : SpeedSource {

    private val sampleChannel = Channel<SpeedSample>(capacity = Channel.UNLIMITED)
    private var callbackExecutor: ExecutorService? = null
    private var activeListener: LocationListener? = null
    private var stopped = false

    override val samples: Flow<SpeedSample> = sampleChannel.receiveAsFlow()

    override suspend fun start() {
        check(!stopped) { "This source has already been stopped." }
        if (activeListener != null) return
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            "Precise location permission is required."
        }
        check(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            "GPS provider is disabled. Enable Location Services before starting."
        }

        val request = LocationRequest.Builder(0L)
            .setMinUpdateIntervalMillis(0L)
            .setMinUpdateDistanceMeters(0f)
            .setMaxUpdateDelayMillis(0L)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .build()

        val listener = LocationListener { location ->
            // Keep the platform callback small; samples are consumed and handled off this thread.
            sampleChannel.trySend(location.toSpeedSample())
        }
        activeListener = listener
        val executor = Executors.newSingleThreadExecutor { command -> Thread(command, "RoadDyno-GNSS") }
        callbackExecutor = executor
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                request,
                executor,
                listener,
            )
        } catch (error: Exception) {
            activeListener = null
            callbackExecutor = null
            executor.shutdownNow()
            throw error
        }
    }

    override suspend fun stop() {
        if (stopped) return
        stopped = true
        activeListener?.let { locationManager.removeUpdates(it) }
        activeListener = null
        callbackExecutor?.shutdown()
        withContext(Dispatchers.IO) {
            if (callbackExecutor?.awaitTermination(2, TimeUnit.SECONDS) == false) {
                callbackExecutor?.shutdownNow()
            }
        }
        callbackExecutor = null
        sampleChannel.close()
    }
}
