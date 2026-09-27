package com.roaddyno.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roaddyno.app.RoadDynoApp
import com.roaddyno.app.export.CsvExporter
import com.roaddyno.app.service.MeasurementService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as RoadDynoApp).repository
    val sessions = repository.sessions
    val measurement = MeasurementService.state
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()

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
}
