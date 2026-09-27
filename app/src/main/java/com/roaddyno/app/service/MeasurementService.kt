package com.roaddyno.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.roaddyno.app.MainActivity
import com.roaddyno.app.RoadDynoApp
import com.roaddyno.app.data.database.SampleAnomaly
import com.roaddyno.app.data.database.SpeedSampleEntity
import com.roaddyno.app.data.database.toEntity
import com.roaddyno.app.data.repository.MeasurementRepository
import com.roaddyno.app.domain.model.SpeedSample
import com.roaddyno.app.dyno.RunConfiguration
import com.roaddyno.app.gnss.GnssStatusInfo
import com.roaddyno.app.gnss.GnssStatusMonitor
import com.roaddyno.app.gnss.PhoneGnssSpeedSource
import com.roaddyno.app.measurement.SamplingStatistics
import com.roaddyno.app.measurement.SamplingStatisticsCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

sealed interface MeasurementState {
    data object Idle : MeasurementState
    data object Preparing : MeasurementState
    data class Recording(
        val sessionId: Long,
        val sample: SpeedSample? = null,
        val statistics: SamplingStatistics = SamplingStatistics(),
        val satellites: GnssStatusInfo = GnssStatusInfo(),
    ) : MeasurementState
    data class Stopping(val sessionId: Long) : MeasurementState
    data class Completed(val sessionId: Long) : MeasurementState
    data class Error(val message: String) : MeasurementState
}

class MeasurementService : Service() {
    companion object {
        private const val CHANNEL_ID = "gnss_recording"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.roaddyno.app.START"
        const val ACTION_STOP = "com.roaddyno.app.STOP"

        private val mutableState = MutableStateFlow<MeasurementState>(MeasurementState.Idle)
        val state = mutableState.asStateFlow()

        fun start(context: Context, configuration: RunConfiguration) = ContextCompat.startForegroundService(
            context, Intent(context, MeasurementService::class.java).setAction(ACTION_START).apply {
                configuration.validate()
                putExtra("vehicle", configuration.vehicleName)
                putExtra("mass", configuration.massKg)
                configuration.gear?.let { putExtra("gear", it) }
                configuration.calibrationRpm?.let { putExtra("rpm", it) }
                configuration.calibrationSpeedKmh?.let { putExtra("kmh", it) }
            },
        )

        fun stop(context: Context) {
            context.startService(Intent(context, MeasurementService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val notifications by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private var source: PhoneGnssSpeedSource? = null
    private var monitor: GnssStatusMonitor? = null
    private var recordingJob: Job? = null
    private var preparingJob: Job? = null
    private var startRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "GNSS recording", NotificationManager.IMPORTANCE_LOW,
        ))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (!startRequested && source == null) {
                startRequested = true
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        notification("Preparing GNSS measurement"),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                    )
                    val configuration = RunConfiguration(
                        intent.getStringExtra("vehicle") ?: "",
                        intent.getDoubleExtra("mass", Double.NaN),
                        if (intent.hasExtra("gear")) intent.getIntExtra("gear", 0) else null,
                        if (intent.hasExtra("rpm")) intent.getDoubleExtra("rpm", 0.0) else null,
                        if (intent.hasExtra("kmh")) intent.getDoubleExtra("kmh", 0.0) else null,
                    ).validate()
                    preparingJob = scope.launch { prepare(configuration) }
                } catch (error: Exception) {
                    mutableState.value = MeasurementState.Error(error.message ?: "Unable to start the location service.")
                    stopSelf()
                }
            }
            ACTION_STOP -> scope.launch {
                preparingJob?.join()
                val current = mutableState.value
                if (current is MeasurementState.Recording) mutableState.value = MeasurementState.Stopping(current.sessionId)
                source?.stop()
                recordingJob?.join()
                if (recordingJob == null) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun prepare(configuration: RunConfiguration) {
        mutableState.value = MeasurementState.Preparing
        val repository = (application as RoadDynoApp).repository
        var sessionId: Long? = null
        try {
            check(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                "Precise location permission is required."
            }
            check(manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) { "Enable GPS before recording." }
            (application as RoadDynoApp).recovery.await()
            sessionId = repository.createSession(configuration)
            val activeSource = PhoneGnssSpeedSource(applicationContext, manager)
            source = activeSource
            activeSource.start()
            val statusMonitor = GnssStatusMonitor(applicationContext, manager)
            monitor = statusMonitor
            statusMonitor.start()
            mutableState.value = MeasurementState.Recording(sessionId)
            recordingJob = scope.launch(Dispatchers.Default) {
                record(repository, activeSource, statusMonitor, sessionId)
            }
        } catch (error: Exception) {
            source?.stop()
            monitor?.stop()
            if (sessionId != null) repository.finish(sessionId, "INTERRUPTED", SystemClock.elapsedRealtimeNanos())
            mutableState.value = MeasurementState.Error(error.message ?: "GNSS recording failed.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun record(
        repository: MeasurementRepository,
        activeSource: PhoneGnssSpeedSource,
        statusMonitor: GnssStatusMonitor,
        sessionId: Long,
    ) {
        val statistics = SamplingStatisticsCalculator()
        val pending = ArrayList<SpeedSampleEntity>(50)
        var previousTimestampNs: Long? = null
        var index = 0L
        var status = "COMPLETED"
        var lastNotificationNs = 0L
        var lastFlushNs = SystemClock.elapsedRealtimeNanos()
        val statusJob = scope.launch {
            statusMonitor.status.collect { satellites ->
                val current = mutableState.value
                if (current is MeasurementState.Recording && current.sessionId == sessionId) {
                    mutableState.value = current.copy(satellites = satellites)
                }
            }
        }

        suspend fun flush() {
            if (pending.isNotEmpty()) {
                repository.append(sessionId, pending.toList())
                pending.clear()
            }
            lastFlushNs = SystemClock.elapsedRealtimeNanos()
        }

        try {
            val incoming = activeSource.samples.produceIn(scope)
            while (true) {
                val remainingNs = (250_000_000L - (SystemClock.elapsedRealtimeNanos() - lastFlushNs)).coerceAtLeast(0L)
                val waitMs = ((remainingNs + 999_999L) / 1_000_000L).coerceAtLeast(1L)
                val result = withTimeoutOrNull(waitMs) { incoming.receiveCatching() }
                if (result == null) {
                    flush()
                    if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        ContextCompat.checkSelfPermission(this@MeasurementService, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    ) {
                        status = "INTERRUPTED"
                        activeSource.stop()
                    }
                    continue
                }
                if (result.isClosed) break
                val sample = result.getOrThrow()
                val flags = SampleAnomaly.flags(sample, previousTimestampNs)
                pending += sample.toEntity(sessionId, index++, flags)
                previousTimestampNs = sample.timestampNs
                val snapshot = statistics.accept(sample, flags != 0)
                if (pending.size >= 50) flush()
                val current = mutableState.value
                if (current is MeasurementState.Recording && current.sessionId == sessionId) {
                    mutableState.value = current.copy(sample = sample, statistics = snapshot)
                }
                if (sample.receivedElapsedRealtimeNs - lastNotificationNs >= 1_000_000_000L) {
                    lastNotificationNs = sample.receivedElapsedRealtimeNs
                    val speed = if (sample.hasSpeed && sample.speedMps.isFinite())
                        "${(sample.speedMps * 3.6).roundToInt()} km/h" else "—"
                    runCatching {
                        notifications.notify(NOTIFICATION_ID, notification("Samples: $index · Speed: $speed"))
                    }
                }
            }
            flush()
            repository.finish(sessionId, status, SystemClock.elapsedRealtimeNanos())
            mutableState.value = if (status == "COMPLETED") MeasurementState.Completed(sessionId)
                else MeasurementState.Error("Location access stopped. The saved session is marked INTERRUPTED.")
        } catch (error: Exception) {
            runCatching { flush() }
            runCatching { repository.finish(sessionId, "INTERRUPTED", SystemClock.elapsedRealtimeNanos()) }
            mutableState.value = MeasurementState.Error(error.message ?: "The session was interrupted.")
        } finally {
            statusJob.cancel()
            runCatching { activeSource.stop() }
            runCatching { statusMonitor.stop() }
            source = null
            monitor = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MeasurementService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Android Road Dyno")
            .setContentText(message)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "STOP", stop)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
