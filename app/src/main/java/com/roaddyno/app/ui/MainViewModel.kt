package com.roaddyno.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roaddyno.app.RoadDynoApp
import com.roaddyno.app.export.CsvExporter
import com.roaddyno.app.dyno.RawCsvReader
import com.roaddyno.app.domain.model.SpeedSample
import com.roaddyno.app.service.MeasurementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportedRun(val name: String, val samples: List<SpeedSample>)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as RoadDynoApp).repository
    val sessions = repository.sessions
    val measurement = MeasurementService.state
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private val mutableImported = MutableStateFlow<ImportedRun?>(null)
    val imported = mutableImported.asStateFlow()

    fun start() {
        try { MeasurementService.start(getApplication()) }
        catch (error: Exception) { mutableMessage.value = error.message ?: "Unable to start recording." }
    }

    fun stop() = MeasurementService.stop(getApplication())
    suspend fun getSamples(sessionId: Long) = repository.getSamples(sessionId)
    fun delete(sessionId: Long) = viewModelScope.launch { repository.delete(sessionId) }

    fun export(sessionId: Long, uri: Uri) = viewModelScope.launch {
        try {
            CsvExporter(getApplication()).export(uri, repository.getSamples(sessionId))
            mutableMessage.value = "CSV saved."
        } catch (error: Exception) {
            mutableMessage.value = error.message ?: "CSV export failed."
        }
    }

    fun clearMessage() { mutableMessage.value = null }

    fun importCsv(uri: Uri) = viewModelScope.launch {
        try {
            val samples = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.reader()?.use { RawCsvReader.read(it) }
                    ?: throw IllegalArgumentException("Cannot open CSV file.")
            }
            mutableImported.value = ImportedRun(uri.lastPathSegment ?: "Imported CSV", samples)
            mutableMessage.value = "Loaded ${samples.size} raw speed samples."
        } catch (error: Exception) {
            mutableMessage.value = error.message ?: "CSV import failed."
        }
    }
}
