package com.roaddyno.app.ui

import android.Manifest
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.roaddyno.app.dyno.RpmCalibration
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun RpmCalibrationDialog(vehicle: String, gear: Int, vm: RpmCalibrationViewModel,
                         onClose: () -> Unit, onComplete: (RpmCalibration) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sample by vm.sample.collectAsState()
    val sourceError by vm.error.collectAsState()
    var firstSpeed by rememberSaveable { mutableStateOf<Double?>(null) }
    var firstTimestamp by rememberSaveable { mutableLongStateOf(0L) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.startPreview()
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.startPreview()
            try { awaitCancellation() } finally { vm.stopPreview() }
        }
    }
    DisposableEffect(vm) { onDispose { vm.stopPreview() } }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtimeNanos(); delay(200) } }
    val fresh = RpmCalibration.canCapture(sample, now) && (sample?.timestampNs ?: 0) > firstTimestamp
    val target = if (firstSpeed == null) 2000 else 3000
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val wasOn = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = wasOn }
        }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("KALIBRACJA RPM · ${if (firstSpeed == null) "1 / 2" else "2 / 2"}", style = MaterialTheme.typography.titleLarge)
                Text("${vehicle.ifBlank { "Samochód" }} · bieg $gear")
                Text("Utrzymuj", style = MaterialTheme.typography.headlineSmall)
                Text("$target RPM", style = MaterialTheme.typography.displayMedium)
                Text("Na tym samym biegu, bez zmiany przełożenia.")
                Text(if (fresh) String.format(Locale.US, "%.1f km/h", sample!!.speedMps * 3.6)
                    else "Czekam na prędkość GNSS…", style = MaterialTheme.typography.headlineMedium)
                firstSpeed?.let { Text(String.format(Locale.US, "Zapisano 2000 RPM = %.2f km/h", it)) }
                (captureError ?: sourceError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    try {
                        val captured = vm.capture()
                        require(captured.timestampNs > firstTimestamp) { "Poczekaj na nową próbkę GNSS." }
                        if (firstSpeed == null) {
                            firstSpeed = captured.speedMps * 3.6
                            firstTimestamp = captured.timestampNs
                            captureError = null
                        } else {
                            val calibration = RpmCalibration(firstSpeed!!, captured.speedMps * 3.6)
                            vm.save(vehicle, gear, calibration)
                            vm.stopPreview()
                            onComplete(calibration)
                        }
                    } catch (e: Exception) { captureError = e.message }
                }, enabled = fresh, modifier = Modifier.fillMaxWidth().height(80.dp)) {
                    Text(if (firstSpeed == null) "ZAPISZ 2000 RPM → DALEJ" else "ZAPISZ 3000 RPM → GOTOWE")
                }
                if (sourceError != null) {
                    OutlinedButton(onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION)) }) { Text("Uprawnienie lokalizacji") }
                    OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                        Text("Ustawienia GPS")
                    }
                }
                if (firstSpeed != null) OutlinedButton(onClick = { firstSpeed = null; firstTimestamp = 0; captureError = null }) {
                    Text("Powtórz od 2000 RPM")
                }
                OutlinedButton(onClick = onClose) { Text("Anuluj") }
            }
        }
    }
}
