package com.roaddyno.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roaddyno.app.data.database.SessionEntity
import com.roaddyno.app.data.database.SpeedSampleEntity
import com.roaddyno.app.measurement.sessionReport
import com.roaddyno.app.replay.ReplayMode
import com.roaddyno.app.replay.ReplaySpeedSource
import com.roaddyno.app.service.MeasurementState
import com.roaddyno.app.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { RoadDynoScreen() }
            }
        }
    }
}

@Composable
private fun RoadDynoScreen(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val sessions by vm.sessions.collectAsState(initial = emptyList())
    val measurement by vm.measurement.collectAsState()
    val message by vm.message.collectAsState()
    var page by rememberSaveable { mutableStateOf("measurement") }
    var selectedId by rememberSaveable { mutableLongStateOf(-1L) }
    var precise by remember { mutableStateOf(hasPreciseLocation(context)) }
    var gpsEnabled by remember { mutableStateOf(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                precise = hasPreciseLocation(context)
                gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        precise = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || hasPreciseLocation(context)
        if (precise && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null && selectedId >= 0) vm.export(selectedId, uri)
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("ANDROID ROAD DYNO", style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { page = "measurement" }) { Text("LOGGER") }
            OutlinedButton(onClick = { page = "sessions" }) { Text("SESSIONS") }
        }
        HorizontalDivider()
        if (message != null) {
            Text(message ?: "", color = MaterialTheme.colorScheme.error)
            LaunchedEffect(message) { kotlinx.coroutines.delay(4_000); vm.clearMessage() }
        }
        when (page) {
            "measurement" -> MeasurementPage(
                measurement, precise, gpsEnabled,
                onPermission = { locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) },
                onSettings = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                onStart = vm::start,
                onStop = vm::stop,
                onSession = { id -> selectedId = id; page = "details" },
            )
            "sessions" -> SessionsPage(sessions) { id -> selectedId = id; page = "details" }
            "details", "replay" -> {
                val session = sessions.firstOrNull { it.id == selectedId }
                val samples by produceState<List<SpeedSampleEntity>?>(null, selectedId) {
                    value = if (selectedId >= 0) vm.getSamples(selectedId) else emptyList()
                }
                if (session == null || samples == null) Text("Loading session…")
                else if (page == "details") SessionDetailsPage(
                    session, samples!!,
                    onReplay = { page = "replay" },
                    onExport = {
                        val date = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date(session.createdAtMillis))
                        csvLauncher.launch("AndroidRoadDyno_$date.csv")
                    },
                    onDelete = { vm.delete(selectedId); page = "sessions" },
                ) else ReplayPage(samples!!)
            }
        }
    }
}

private fun hasPreciseLocation(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
private fun MeasurementPage(
    state: MeasurementState,
    precise: Boolean,
    gpsEnabled: Boolean,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSession: (Long) -> Unit,
) {
    val recording = state as? MeasurementState.Recording
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("GNSS LOGGER", style = MaterialTheme.typography.titleMedium)
        Text(recording?.sample?.speedMps?.times(3.6).number(1), style = MaterialTheme.typography.displayLarge)
        Text("km/h", style = MaterialTheme.typography.titleLarge)
        Metric("GNSS", when { !precise -> "PRECISE LOCATION REQUIRED"; !gpsEnabled -> "GPS OFF"; else -> "READY" })
        Metric("Speed accuracy", recording?.sample?.speedAccuracyMps?.let { "±${it.number(2)} m/s" } ?: "—")
        Metric("Current rate", recording?.statistics?.currentRateHz.hz())
        Metric("Average rate", recording?.statistics?.averageRateHz.hz())
        Metric("Samples", recording?.statistics?.sampleCount?.toString() ?: "—")
        Metric("Average Δt", recording?.statistics?.averageDeltaMs.ms())
        Metric("Min / max Δt", "${recording?.statistics?.minDeltaMs.ms()} / ${recording?.statistics?.maxDeltaMs.ms()}")
        Metric("Satellites", recording?.satellites?.let { "${it.visible ?: "—"} / ${it.usedInFix ?: "—"}" } ?: "—")
        when {
            !precise -> Button(onClick = onPermission, modifier = Modifier.fillMaxWidth()) { Text("GRANT PRECISE LOCATION") }
            !gpsEnabled -> Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("OPEN LOCATION SETTINGS") }
            recording != null || state is MeasurementState.Preparing -> Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("STOP") }
            state is MeasurementState.Stopping -> Text("Finishing session…")
            else -> Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("START") }
        }
        when (state) {
            is MeasurementState.Completed -> Button(onClick = { onSession(state.sessionId) }) { Text("VIEW LAST SESSION") }
            is MeasurementState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            else -> Unit
        }
    }
}

@Composable
private fun SessionsPage(sessions: List<SessionEntity>, onSelect: (Long) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SESSIONS", style = MaterialTheme.typography.titleLarge)
        if (sessions.isEmpty()) Text("No saved sessions yet.")
        for (session in sessions) {
            HorizontalDivider()
            val date = remember(session.createdAtMillis) {
                SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault()).format(Date(session.createdAtMillis))
            }
            Text(date)
            Text("${session.sampleCount} samples · ${session.status}")
            Button(onClick = { onSelect(session.id) }) { Text("DETAILS") }
        }
    }
}

@Composable
private fun SessionDetailsPage(
    session: SessionEntity,
    samples: List<SpeedSampleEntity>,
    onReplay: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val report = remember(samples) { sessionReport(samples) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("SESSION #${session.id} · ${session.status}", style = MaterialTheme.typography.titleLarge)
        Metric("Duration", report.durationSeconds?.let { "${it.number(2)} s" } ?: "—")
        Metric("Samples", report.statistics.sampleCount.toString())
        Metric("Average rate", report.statistics.averageRateHz.hz())
        Metric("Average Δt", report.statistics.averageDeltaMs.ms())
        Metric("Median Δt", report.statistics.medianDeltaMs.ms())
        Metric("Min / max Δt", "${report.statistics.minDeltaMs.ms()} / ${report.statistics.maxDeltaMs.ms()}")
        Metric("Detected gaps", report.statistics.gapCount.toString())
        Metric("Anomalies", report.statistics.anomalyCount.toString())
        Metric("Mean speed accuracy", report.meanSpeedAccuracyMps?.let { "${it.number(2)} m/s" } ?: "—")
        Metric("Best / worst accuracy", "${report.bestSpeedAccuracyMps.msUnit()} / ${report.worstSpeedAccuracyMps.msUnit()}")
        Metric("Min / max speed", "${report.minSpeedKmh.kmh()} / ${report.maxSpeedKmh.kmh()}")
        Button(onClick = onReplay, modifier = Modifier.fillMaxWidth()) { Text("REPLAY") }
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("EXPORT CSV") }
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth(), enabled = session.status != "RECORDING") { Text("DELETE") }
    }
}

@Composable
private fun ReplayPage(samples: List<SpeedSampleEntity>) {
    var mode by remember { mutableStateOf(ReplayMode.REALTIME) }
    var playKey by remember { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(0) }
    var currentSpeed by remember { mutableStateOf<Double?>(null) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(samples, mode, playKey) {
        if (playKey == 0) return@LaunchedEffect
        count = 0
        currentSpeed = null
        playing = true
        val source = ReplaySpeedSource(samples.map { it.toDomain() }, mode)
        try {
            source.start()
            source.samples.collect { sample ->
                count++
                currentSpeed = sample.speedMps * 3.6
            }
        } finally {
            source.stop()
            playing = false
        }
    }
    Column(Modifier.fillMaxSize().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("REPLAY", style = MaterialTheme.typography.titleLarge)
        Text("${currentSpeed.kmh()} · $count / ${samples.size} samples", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { mode = ReplayMode.REALTIME }, enabled = !playing) { Text("1× REALTIME") }
            OutlinedButton(onClick = { mode = ReplayMode.MAX_SPEED }, enabled = !playing) { Text("MAX SPEED") }
        }
        Button(onClick = { playKey++ }, enabled = !playing) { Text("PLAY $mode") }
        if (!playing && count == samples.size && playKey > 0) Text("Replay complete. Order and sample values were preserved.")
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value)
    }
}

private fun Double?.number(digits: Int): String = this?.let { String.format(Locale.US, "%1$.${digits}f", it) } ?: "—"
private fun Double?.hz() = this?.let { "${it.number(2)} Hz" } ?: "—"
private fun Double?.ms() = this?.let { "${it.number(1)} ms" } ?: "—"
private fun Double?.msUnit() = this?.let { "${it.number(2)} m/s" } ?: "—"
private fun Double?.kmh() = this?.let { "${it.number(1)} km/h" } ?: "—"
