package com.roaddyno.app.ui

import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roaddyno.app.domain.model.SpeedSample
import com.roaddyno.app.dyno.DynoEngine
import com.roaddyno.app.dyno.DynoResult
import com.roaddyno.app.dyno.RunConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun RunSetupForm(initial: RunConfiguration?, action: String, enabled: Boolean = true,
                 onSubmit: (RunConfiguration) -> Unit) {
    var vehicle by rememberSaveable { mutableStateOf(initial?.vehicleName ?: "") }
    var mass by rememberSaveable { mutableStateOf(initial?.massKg?.toString() ?: "") }
    var gear by rememberSaveable { mutableStateOf(initial?.gear?.toString() ?: "") }
    var rpm by rememberSaveable { mutableStateOf(initial?.calibrationRpm?.toString() ?: "") }
    var speed by rememberSaveable { mutableStateOf(initial?.calibrationSpeedKmh?.toString() ?: "") }
    var formError by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(vehicle, { vehicle = it }, label = { Text("Samochód / opis") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        NumericField(mass, { mass = it }, "Masa pomiarowa [kg]")
        NumericField(gear, { gear = it }, "Bieg pomiarowy (opcjonalnie)")
        Text("Kalibracja na tym biegu: znane RPM przy znanej prędkości. Bez kalibracji otrzymasz moc względem km/h.",
            style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumericField(rpm, { rpm = it }, "RPM", Modifier.weight(1f))
            NumericField(speed, { speed = it }, "przy km/h", Modifier.weight(1f))
        }
        formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            try {
                fun parse(text: String): Double? = if (text.isBlank()) null
                    else text.trim().replace(',', '.').toDoubleOrNull()
                        ?: throw IllegalArgumentException("Wpisz poprawną liczbę: $text")
                val config = RunConfiguration(vehicle.trim(), parse(mass) ?: error("Podaj masę pomiarową."),
                    if (gear.isBlank()) null else gear.trim().toIntOrNull() ?: error("Podaj numer biegu."),
                    parse(rpm), parse(speed)).validate()
                formError = null
                onSubmit(config)
            } catch (e: IllegalArgumentException) { formError = e.message }
              catch (e: IllegalStateException) { formError = e.message }
        }, enabled = enabled, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text(action) }
    }
}

@Composable
private fun NumericField(value: String, change: (String) -> Unit, label: String,
                         modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(value, change, label = { Text(label) }, modifier = modifier, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
fun RunResultPage(samples: List<SpeedSample>, initial: RunConfiguration?,
                  onExport: (Uri, DynoResult, RunConfiguration) -> Unit,
                  onConfiguration: (RunConfiguration) -> Unit,
                  onDetails: (() -> Unit)?) {
    // Inputs survive rotation; calculations are reproducible from immutable raw data.
    var vehicle by rememberSaveable { mutableStateOf(initial?.vehicleName ?: "") }
    var mass by rememberSaveable { mutableStateOf(initial?.massKg) }
    var gear by rememberSaveable { mutableStateOf(initial?.gear) }
    var rpm by rememberSaveable { mutableStateOf(initial?.calibrationRpm) }
    var kmh by rememberSaveable { mutableStateOf(initial?.calibrationSpeedKmh) }
    val config = mass?.let { RunConfiguration(vehicle, it, gear, rpm, kmh) }
    var editing by rememberSaveable { mutableStateOf(initial == null) }
    var result by remember { mutableStateOf<DynoResult?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    // Capture exactly the result selected for export, even if form fields change while SAF is open.
    var exportSnapshot by remember { mutableStateOf<Pair<DynoResult, RunConfiguration>?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val snapshot = exportSnapshot
        if (uri != null && snapshot != null) onExport(uri, snapshot.first, snapshot.second)
    }
    LaunchedEffect(samples, config) {
        result = null
        failure = null
        if (config == null) return@LaunchedEffect
        working = true
        try {
            result = withContext(Dispatchers.Default) {
                DynoEngine().analyze(samples, config.massKg, config.calibrationRpm, config.calibrationSpeedKmh)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (e: Exception) { failure = e.message ?: "Nie udało się obliczyć wyniku." }
        finally { working = false }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("WYNIK POMIARU", style = MaterialTheme.typography.headlineSmall)
        config?.let {
            Text("${it.vehicleName.ifBlank { "Samochód" }} · ${f(it.massKg, 0)} kg · bieg ${it.gear ?: "—"}")
        }
        if (editing) RunSetupForm(config, "OBLICZ I ZAPISZ USTAWIENIA", onSubmit = {
            vehicle = it.vehicleName; mass = it.massKg; gear = it.gear
            rpm = it.calibrationRpm; kmh = it.calibrationSpeedKmh
            editing = false
            onConfiguration(it)
        }) else OutlinedButton(onClick = { editing = true }) { Text("Zmień masę / kalibrację") }
        if (working) Text("Obliczam moc, straty i moment…")
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        result?.let { report ->
            val peak = report.points.filter { it.correctedPowerKw != null }.maxByOrNull { it.correctedPowerKw!! }
            val torque = report.points.filter { it.torqueNm != null }.maxByOrNull { it.torqueNm!! }
            Text(peak?.let { "${f(it.correctedPowerKw!! * 1.3596216)} KM" } ?: "Brak wyniku ze stratami",
                style = MaterialTheme.typography.displaySmall)
            Text("Moc ze stratami · maksimum w zakresie pokrytym wybiegiem")
            peak?.let {
                ResultMetric("Przy", it.rpm?.let { value -> "${f(value, 0)} rpm" } ?: "${f(it.speedKmh)} km/h")
                ResultMetric("Moc rozpędzania w tym punkcie", "${f(it.wheelPowerKw * 1.3596216)} KM")
                ResultMetric("Straty w tym punkcie", "${f(it.lossPowerKw!! * 1.3596216)} KM")
            }
            ResultMetric("Maks. moment", torque?.let { "${f(it.torqueNm!!, 0)} Nm przy ${f(it.rpm!!, 0)} rpm" }
                ?: "—")
            ResultMetric("Maks. moc rozpędzania", "${f(report.peakWheelPowerKw * 1.3596216)} KM")
            Text(if (report.coastMinKmh != null) {
                "Zmierzony wybieg: ${f(report.coastMaxKmh!!)} → ${f(report.coastMinKmh)} km/h. " +
                    "Krzywa ze stratami obejmuje wspólny zakres prędkości."
            } else "Brak wystarczającego wybiegu. Dostępna jest moc rozpędzania.")
            val x = report.points.map { it.rpm ?: it.speedKmh }
            val xLabel = if (rpm != null) "RPM" else "km/h"
            Text("MOC I STRATY", style = MaterialTheme.typography.titleMedium)
            CurveChart(x, listOf(
                Curve("Rozpędzanie", Color(0xFF1976D2), report.points.map { it.wheelPowerKw * 1.3596216 }),
                Curve("Ze stratami", Color(0xFFE65100), report.points.map { it.correctedPowerKw?.times(1.3596216) }),
                Curve("Straty", Color(0xFF2E7D32), report.points.map { it.lossPowerKw?.times(1.3596216) }),
            ), xLabel, "KM")
            if (torque != null) {
                Text("MOMENT", style = MaterialTheme.typography.titleMedium)
                CurveChart(x, listOf(Curve("Moment ze stratami", Color(0xFF7B1FA2), report.points.map { it.torqueNm })), xLabel, "Nm")
            }
            Text("PRZEBIEG PRĘDKOŚCI", style = MaterialTheme.typography.titleMedium)
            val valid = samples.filter { it.hasSpeed && it.speedMps.isFinite() && it.timestampNs > 0 }
            if (valid.isNotEmpty()) CurveChart(valid.map { (it.timestampNs - valid.first().timestampNs) / 1e9 },
                listOf(Curve("Surowa prędkość", Color(0xFF1976D2), valid.map { it.speedMps * 3.6 })), "s", "km/h")
            Text("Rozpędzanie: ${f(report.accelerationStartSeconds)}–${f(report.peakSeconds)} s. " +
                "Wybieg: ${report.coastStartSeconds?.let { f(it) } ?: "—"}–${report.coastEndSeconds?.let { f(it) } ?: "—"} s.")
            Text("Obliczenia zakładają wybieg z rozłączonym napędem, bez hamowania. Sam zapis prędkości nie rozróżnia hamulca od oporów ruchu.",
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                config?.let { exportSnapshot = report to it; export.launch("AndroidRoadDyno_wynik.csv") }
            }, modifier = Modifier.fillMaxWidth()) { Text("EKSPORT WYNIKU CSV") }
        }
        if (onDetails != null) OutlinedButton(onClick = onDetails, modifier = Modifier.fillMaxWidth()) {
            Text("SUROWE DANE · CSV · REPLAY")
        }
    }
}

private data class Curve(val label: String, val color: Color, val y: List<Double?>)

@Composable
private fun CurveChart(x: List<Double>, curves: List<Curve>, xLabel: String, yLabel: String) {
    if (x.isEmpty()) return
    val minX = x.min()
    val maxX = x.max()
    val maxY = curves.flatMap { it.y }.filterNotNull().maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val textColor = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(250.dp)) {
        val left = 46.dp.toPx(); val right = size.width - 22.dp.toPx()
        val top = 24.dp.toPx(); val bottom = size.height - 36.dp.toPx()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor.toArgb(); textSize = 11.dp.toPx() }
        fun point(index: Int, y: Double) = Offset(
            left + ((x[index] - minX) / (maxX - minX).coerceAtLeast(0.01) * (right - left)).toFloat(),
            bottom - (y / maxY * (bottom - top)).toFloat())
        for (i in 0..4) {
            val fraction = i / 4f
            val y = bottom - fraction * (bottom - top)
            drawLine(Color.Gray.copy(alpha = .25f), Offset(left, y), Offset(right, y))
            drawContext.canvas.nativeCanvas.drawText(f(maxY * fraction, 0), 0f, y, paint)
            val px = left + fraction * (right - left)
            paint.textAlign = Paint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText(f(minX + (maxX - minX) * fraction, 0), px, bottom + 18.dp.toPx(), paint)
            paint.textAlign = Paint.Align.LEFT
        }
        drawContext.canvas.nativeCanvas.drawText(yLabel, 0f, 13.dp.toPx(), paint)
        drawContext.canvas.nativeCanvas.drawText(xLabel, right - 22.dp.toPx(), size.height - 1.dp.toPx(), paint)
        for (curve in curves) for (i in 1 until x.size) {
            val a = curve.y[i - 1]; val b = curve.y[i]
            if (a != null && b != null) drawLine(curve.color, point(i - 1, a), point(i, b), 2.dp.toPx())
        }
    }
    curves.forEach { Text(it.label, color = it.color, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun ResultMetric(label: String, value: String) {
    Column { Text(label, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.titleMedium) }
}

private fun f(value: Double, digits: Int = 1) = String.format(Locale.US, "%1$.${digits}f", value)
