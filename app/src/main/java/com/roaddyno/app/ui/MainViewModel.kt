package com.roaddyno.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roaddyno.app.RoadDynoApp
import com.roaddyno.app.export.CsvExporter
import com.roaddyno.app.dyno.RawCsvReader
import com.roaddyno.app.dyno.RunConfiguration
import com.roaddyno.app.dyno.DynoResult
import com.roaddyno.app.export.DynoCsvWriter
import com.roaddyno.app.domain.model.SpeedSample
import com.roaddyno.app.service.MeasurementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportedRun(val name: String, val samples: List<SpeedSample>, val configuration: RunConfiguration? = null)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as RoadDynoApp).repository
    val sessions = repository.sessions
    val measurement = MeasurementService.state
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private val mutableImported = MutableStateFlow<ImportedRun?>(null)
    val imported = mutableImported.asStateFlow()

    private val preferences = application.getSharedPreferences("run_setup", 0)
    fun lastConfiguration(): RunConfiguration? = runCatching {
        RunConfiguration(
            preferences.getString("vehicle", "") ?: "",
            preferences.getString("mass", null)?.toDoubleOrNull() ?: return null,
            preferences.getString("gear", null)?.toIntOrNull(),
            preferences.getString("rpm", null)?.toDoubleOrNull(),
            preferences.getString("kmh", null)?.toDoubleOrNull(),
            preferences.getString("speed2000", null)?.toDoubleOrNull(),
            preferences.getString("speed3000", null)?.toDoubleOrNull(),
            preferences.getString("tyre", null),
        ).validate()
    }.getOrNull()

    fun start(configuration: RunConfiguration) {
        try {
            configuration.validate()
            preferences.edit().putString("vehicle", configuration.vehicleName)
                .putString("mass", configuration.massKg.toString())
                .putString("gear", configuration.gear?.toString())
                .putString("rpm", configuration.calibrationRpm?.toString())
                .putString("kmh", configuration.calibrationSpeedKmh?.toString())
                .putString("speed2000", configuration.speed2000Kmh?.toString())
                .putString("speed3000", configuration.speed3000Kmh?.toString())
                .putString("tyre", configuration.tyreSize).apply()
            MeasurementService.start(getApplication(), configuration)
        }
        catch (error: Exception) { mutableMessage.value = error.message ?: "Unable to start recording." }
    }

    fun stop() = MeasurementService.stop(getApplication())
    suspend fun getSamples(sessionId: Long) = repository.getSamples(sessionId)
    suspend fun getSession(sessionId: Long) = repository.getSession(sessionId)
    fun saveConfiguration(sessionId: Long, configuration: RunConfiguration) = viewModelScope.launch {
        try { repository.saveConfiguration(sessionId, configuration) }
        catch (error: Exception) { mutableMessage.value = error.message ?: "Nie zapisano ustawień." }
    }
    fun delete(sessionId: Long) = viewModelScope.launch { repository.delete(sessionId) }

    fun export(sessionId: Long, uri: Uri) = viewModelScope.launch {
        try {
            CsvExporter(getApplication()).export(uri, repository.getSamples(sessionId), repository.getSession(sessionId)?.configuration())
            mutableMessage.value = "CSV saved."
        } catch (error: Exception) {
            mutableMessage.value = error.message ?: "CSV export failed."
        }
    }

    fun clearMessage() { mutableMessage.value = null }

    fun exportResult(uri: Uri, result: DynoResult, configuration: RunConfiguration) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.writer()?.use {
                    DynoCsvWriter.write(it, result, configuration)
                } ?: error("Nie można zapisać pliku.")
            }
            mutableMessage.value = "Zapisano wynik CSV."
        } catch (error: Exception) { mutableMessage.value = error.message ?: "Błąd zapisu wyniku." }
    }

    fun importCsv(uri: Uri) = viewModelScope.launch {
        mutableImported.value = null
        try {
            val recording = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.reader()?.use { RawCsvReader.readRecording(it) }
                    ?: throw IllegalArgumentException("Cannot open CSV file.")
            }
            mutableImported.value = ImportedRun(uri.lastPathSegment ?: "Imported CSV", recording.samples, recording.configuration)
            mutableMessage.value = "Wczytano ${recording.samples.size} próbek."
        } catch (error: Exception) {
            mutableMessage.value = error.message ?: "CSV import failed."
        }
    }
}
