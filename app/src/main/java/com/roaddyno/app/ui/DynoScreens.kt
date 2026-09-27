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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.roaddyno.app.dyno.RpmCalibration
import com.roaddyno.app.dyno.TyreGeometry
import com.roaddyno.app.dyno.WheelTravelCalculator
import androidx.lifecycle.viewmodel.compose.viewModel
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
    var speed2000 by rememberSaveable { mutableStateOf(initial?.speed2000Kmh) }
    var speed3000 by rememberSaveable { mutableStateOf(initial?.speed3000Kmh) }
    var tyreSize by rememberSaveable { mutableStateOf(initial?.tyreSize ?: "") }
    var calibrating by rememberSaveable { mutableStateOf(false) }
    val calibrationVm: RpmCalibrationViewModel = viewModel()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun applyCalibration(calibration: RpmCalibration) {
        speed2000 = calibration.speed2000Kmh
        speed3000 = calibration.speed3000Kmh
        rpm = "2000.0"
        speed = (2000 / calibration.rpmPerKmh).toString()
    }
    fun clearCalibration() { speed2000 = null; speed3000 = null; rpm = ""; speed = "" }
    var formError by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(vehicle, { vehicle = it; clearCalibration() }, label = { Text("Samochód / opis") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        NumericField(mass, { mass = it }, "Masa pomiarowa [kg]")
        NumericField(gear, { gear = it; clearCalibration() }, "Bieg pomiarowy")
        val selectedGear = gear.trim().toIntOrNull()?.takeIf { it > 0 }
        OutlinedButton(onClick = { focusManager.clearFocus(); keyboard?.hide(); calibrating = true }, enabled = selectedGear != null,
            modifier = Modifier.fillMaxWidth()) { Text("KALIBRUJ: 2000 → 3000 RPM") }
        val saved = remember(vehicle, gear, speed2000, speed3000) { selectedGear?.let { calibrationVm.load(vehicle, it) } }
        if (saved != null) OutlinedButton(onClick = { applyCalibration(saved) }) { Text("Wczytaj kalibrację tego auta i biegu") }
        if (calibrating && selectedGear != null) RpmCalibrationDialog(vehicle, selectedGear, calibrationVm,
            onClose = { calibrating = false }, onComplete = { applyCalibration(it); calibrating = false })
        if (speed2000 != null && speed3000 != null) {
            val calibration = RpmCalibration(speed2000!!, speed3000!!)
            Text("Zapisano: 2000 RPM = ${f(speed2000!!, 2)} km/h; 3000 RPM = ${f(speed3000!!, 2)} km/h")
            Text("Różnica przeliczników obu punktów: ${f(calibration.differencePercent, 1)}%")
        }
        Text("Kalibracja na tym biegu: znane RPM przy znanej prędkości. Bez kalibracji otrzymasz moc względem km/h.",
            style = MaterialTheme.typography.bodySmall)
        if (speed2000 == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumericField(rpm, { rpm = it }, "RPM", Modifier.weight(1f))
            NumericField(speed, { speed = it }, "przy km/h", Modifier.weight(1f))
        }
        if (speed2000 != null) OutlinedButton(onClick = { clearCalibration() }) { Text("Usuń kalibrację") }
        OutlinedTextField(tyreSize, { tyreSize = it }, label = { Text("Opona, np. 225/45 R17 (opcjonalnie)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        val tyre = remember(tyreSize) { runCatching { TyreGeometry.parse(tyreSize) }.getOrNull() }
        tyre?.let {
            Text("Nominalnie: średnica ${f(it.diameterM * 1000, 1)} mm · obwód ${f(it.circumferenceM, 3)} m")
        }
        Text("Obroty koła i droga są wyliczane z prędkości GNSS oraz nominalnego obwodu opony.",
            style = MaterialTheme.typography.bodySmall)
        formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            try {
                fun parse(text: String): Double? = if (text.isBlank()) null
                    else text.trim().replace(',', '.').toDoubleOrNull()
                        ?: throw IllegalArgumentException("Wpisz poprawną liczbę: $text")
                val config = RunConfiguration(vehicle.trim(), parse(mass) ?: error("Podaj masę pomiarową."),
                    if (gear.isBlank()) null else gear.trim().toIntOrNull() ?: error("Podaj numer biegu."),
                    parse(rpm), parse(speed), speed2000, speed3000, tyreSize.trim().takeIf { it.isNotBlank() }).validate()
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
    var speed2000 by rememberSaveable { mutableStateOf(initial?.speed2000Kmh) }
    var speed3000 by rememberSaveable { mutableStateOf(initial?.speed3000Kmh) }
    var tyreSize by rememberSaveable { mutableStateOf(initial?.tyreSize) }
    val config = mass?.let { RunConfiguration(vehicle, it, gear, rpm, kmh, speed2000, speed3000, tyreSize) }
    val validTrace = remember(samples) {
        samples.filter { it.hasSpeed && it.speedMps.isFinite() && it.timestampNs > 0 }
    }
    val travel = remember(samples, tyreSize) {
        WheelTravelCalculator(config?.tyreGeometry()).apply { samples.forEach { accept(it) } }.snapshot()
    }
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
                DynoEngine().analyze(samples, config.massKg, config.effectiveCalibrationRpm, config.effectiveCalibrationSpeedKmh)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (e: Exception) { failure = e.message ?: "Nie udało się obliczyć wyniku." }
        finally { working = false }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("WYNIK HAMOWNI", style = MaterialTheme.typography.headlineSmall)
        config?.let {
            Text("${it.vehicleName.ifBlank { "Samochód" }} · ${f(it.massKg, 0)} kg · bieg ${it.gear ?: "—"}")
        }
        if (editing) RunSetupForm(config, "OBLICZ I ZAPISZ USTAWIENIA", onSubmit = {
            vehicle = it.vehicleName; mass = it.massKg; gear = it.gear
            rpm = it.calibrationRpm; kmh = it.calibrationSpeedKmh
            speed2000 = it.speed2000Kmh; speed3000 = it.speed3000Kmh; tyreSize = it.tyreSize
            editing = false
            onConfiguration(it)
        }) else OutlinedButton(onClick = { editing = true }) { Text("Zmień masę / kalibrację") }
        if (working) Text("Obliczam moc, straty i moment…")
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        result?.let { report ->
            DynoSummary(report, config, validTrace) {
                config?.let { exportSnapshot = report to it; export.launch("AndroidRoadDyno_wynik.csv") }
            }
        }
        Text("PRĘDKOŚĆ PODCZAS CAŁEGO ZAPISU", style = MaterialTheme.typography.titleMedium)
        ResultMetric("Zapisane próbki", samples.size.toString())
        if (validTrace.isNotEmpty()) {
            ResultMetric("Czas zapisu", "${f((validTrace.last().timestampNs - validTrace.first().timestampNs) / 1e9)} s")
            CurveChart(validTrace.map { (it.timestampNs - validTrace.first().timestampNs) / 1e9 },
                listOf(Curve("Prędkość GNSS", Color(0xFF1976D2), validTrace.map { it.speedMps * 3.6 })), "s", "km/h")
        } else Text("Brak poprawnej prędkości w zapisie.")
        config?.twoPointCalibration()?.let {
            Text("Kalibracja: 2000 RPM / ${f(it.speed2000Kmh, 2)} km/h · 3000 RPM / ${f(it.speed3000Kmh, 2)} km/h")
            Text("Różnica przeliczników: ${f(it.differencePercent)}%")
        }
        ResultMetric("Droga z prędkości GNSS", "${f(travel.distanceM)} m")
        config?.tyreGeometry()?.let { tyre ->
            ResultMetric("Opona / nominalny obwód", "${config.tyreSize} / ${f(tyre.circumferenceM, 3)} m")
            ResultMetric("Wyliczone obroty koła na całej trasie", f(travel.wheelTurns ?: 0.0, 1))
            val maxWheelRpm = samples.filter { it.hasSpeed && it.speedMps.isFinite() && it.speedMps >= 0 }
                .maxOfOrNull { tyre.wheelRpm(it.speedMps) }
            maxWheelRpm?.let { ResultMetric("Maks. wyliczone obroty koła", "${f(it, 0)} obr/min") }
            val engineRpm = config.effectiveCalibrationRpm
            val calibrationSpeed = config.effectiveCalibrationSpeedKmh
            if (engineRpm != null && calibrationSpeed != null)
                ResultMetric("Wyliczone przełożenie całkowite", f(tyre.totalRatio(engineRpm / calibrationSpeed), 3))
            Text("To obliczenia z GPS i nominalnego rozmiaru opony, bez niezależnego czujnika koła.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (travel.omittedIntervals > 0) Text("Droga częściowa — pominięte odcinki bez poprawnej prędkości lub z przerwą ponad 5 s: ${travel.omittedIntervals}.")
        if (onDetails != null) OutlinedButton(onClick = onDetails, modifier = Modifier.fillMaxWidth()) {
            Text("SUROWE DANE · CSV · REPLAY")
        }
    }
}

@Composable
private fun DynoSummary(report: DynoResult, config: RunConfiguration?,
                        validTrace: List<SpeedSample>, onExport: () -> Unit) {
    val correctedPeak = report.points.filter { it.correctedPowerKw != null }
        .maxByOrNull { it.correctedPowerKw!! }
    val wheelPeak = report.points.maxBy { it.wheelPowerKw }
    val shownPeak = correctedPeak ?: wheelPeak
    val correctedTorquePeak = report.points.filter { it.torqueNm != null }.maxByOrNull { it.torqueNm!! }
    val wheelTorquePeak = report.points.filter { it.wheelTorqueNm != null }.maxByOrNull { it.wheelTorqueNm!! }
    val shownTorque = correctedTorquePeak ?: wheelTorquePeak
    val shownTorqueNm = correctedTorquePeak?.torqueNm ?: wheelTorquePeak?.wheelTorqueNm

    Text("${f((shownPeak.correctedPowerKw ?: shownPeak.wheelPowerKw) * 1.3596216)} KM",
        style = MaterialTheme.typography.displaySmall)
    Text(if (correctedPeak != null) "Moc z oporami · maksimum w zakresie zmierzonego wybiegu"
        else "Moc rozpędzania · bez korekty o straty")
    ResultMetric("Przy", shownPeak.rpm?.let { "${f(it, 0)} rpm" } ?: "${f(shownPeak.speedKmh)} km/h")
    ResultMetric("Maks. moc rozpędzania", "${f(report.peakWheelPowerKw * 1.3596216)} KM")
    correctedPeak?.let {
        ResultMetric("Rozpędzanie / straty przy maksimum", "${f(it.wheelPowerKw * 1.3596216)} / ${f(it.lossPowerKw!! * 1.3596216)} KM")
    }
    if (shownTorqueNm != null && shownTorque != null) {
        ResultMetric(if (correctedTorquePeak != null) "Maks. moment z oporami" else "Maks. moment rozpędzania",
            "${f(shownTorqueNm, 0)} Nm przy ${f(shownTorque.rpm!!, 0)} rpm")
    } else Text("Moment: wykonaj kalibrację RPM na wybranym biegu.")

    val x = report.points.map { it.rpm ?: it.speedKmh }
    val xLabel = if (config?.effectiveCalibrationRpm != null) "RPM" else "km/h"
    Text("MOC I STRATY", style = MaterialTheme.typography.titleMedium)
    CurveChart(x, listOf(
        Curve("Moc rozpędzania", Color(0xFF1976D2), report.points.map { it.wheelPowerKw * 1.3596216 }),
        Curve("Moc z oporami", Color(0xFFE65100), report.points.map { it.correctedPowerKw?.times(1.3596216) }),
        Curve("Straty", Color(0xFF2E7D32), report.points.map { it.lossPowerKw?.times(1.3596216) }),
    ), xLabel, "KM")
    if (wheelTorquePeak != null) {
        Text("MOMENT", style = MaterialTheme.typography.titleMedium)
        CurveChart(x, listOf(
            Curve("Moment rozpędzania", Color(0xFF1976D2), report.points.map { it.wheelTorqueNm }),
            Curve("Moment z oporami", Color(0xFF7B1FA2), report.points.map { it.torqueNm }),
        ), xLabel, "Nm")
    }
    Text(if (report.coastMinKmh != null) {
        "Zmierzony wybieg: ${f(report.coastMaxKmh!!)} → ${f(report.coastMinKmh)} km/h. " +
            "Korekta strat obejmuje wyłącznie wspólny zakres prędkości."
    } else "Brak wystarczającego wybiegu. Wynik obejmuje moc rozpędzania i jej moment.")
    Text("Rozpędzanie: ${f(report.accelerationStartSeconds)}–${f(report.peakSeconds)} s. " +
        "Wybieg: ${report.coastStartSeconds?.let { f(it) } ?: "—"}–${report.coastEndSeconds?.let { f(it) } ?: "—"} s.")
    val traceEndSeconds = validTrace.lastOrNull()?.let {
        (it.timestampNs - validTrace.first().timestampNs) / 1e9
    }
    if (traceEndSeconds != null && report.coastEndSeconds != null && traceEndSeconds > report.coastEndSeconds) {
        Text("Po wybiegu zapis trwa do ${f(traceEndSeconds)} s. Cały przebieg jest poniżej; wynik dotyczy podanych zakresów.")
    }
    Text("Wybieg musi odbyć się z rozłączonym napędem, bez hamowania. Sama prędkość nie odróżnia hamulca od oporów ruchu.",
        style = MaterialTheme.typography.bodySmall)
    Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("EKSPORT WYNIKU CSV") }
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
