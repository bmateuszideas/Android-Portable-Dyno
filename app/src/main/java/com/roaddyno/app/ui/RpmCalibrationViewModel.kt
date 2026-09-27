package com.roaddyno.app.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roaddyno.app.domain.model.SpeedSample
import com.roaddyno.app.dyno.RpmCalibration
import com.roaddyno.app.gnss.PhoneGnssSpeedSource
import com.roaddyno.app.service.MeasurementService
import com.roaddyno.app.service.MeasurementState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class RpmCalibrationViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val preferences = application.getSharedPreferences("rpm_two_point_calibration", Context.MODE_PRIVATE)
    private var previewJob: Job? = null
    private val mutableSample = MutableStateFlow<SpeedSample?>(null)
    val sample = mutableSample.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    fun startPreview() {
        if (previewJob?.isActive == true) return
        mutableSample.value = null
        mutableError.value = null
        previewJob = viewModelScope.launch {
            val source = PhoneGnssSpeedSource(getApplication(), manager)
            try {
                check(MeasurementService.state.value.let {
                    it !is MeasurementState.Recording && it !is MeasurementState.Preparing && it !is MeasurementState.Stopping
                }) { "Zakończ bieżący pomiar przed kalibracją RPM." }
                check(hasPrecisePermission()) { "Zezwól na dokładną lokalizację." }
                check(manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) { "Włącz lokalizację w ustawieniach telefonu." }
                source.start()
                source.samples.collect { mutableSample.value = it }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (e: Exception) { mutableError.value = e.message ?: "Nie można uruchomić GNSS." }
            finally {
                withContext(NonCancellable) { source.stop() }
            }
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        mutableSample.value = null
    }

    fun capture(): SpeedSample {
        check(hasPrecisePermission() && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            "Wymagana dokładna lokalizacja i włączony GPS."
        }
        val current = mutableSample.value
        require(RpmCalibration.canCapture(current, SystemClock.elapsedRealtimeNanos())) {
            "Poczekaj na aktualny odczyt prędkości GNSS."
        }
        return current!!
    }

    private fun hasPrecisePermission() = ContextCompat.checkSelfPermission(getApplication(),
        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun key(vehicle: String, gear: Int): String = MessageDigest.getInstance("SHA-256")
        .digest("${vehicle.trim()}\u0000$gear".toByteArray()).joinToString("") { "%02x".format(it) }

    fun save(vehicle: String, gear: Int, calibration: RpmCalibration) {
        val key = key(vehicle, gear)
        preferences.edit().putString("${key}_2000", calibration.speed2000Kmh.toString())
            .putString("${key}_3000", calibration.speed3000Kmh.toString()).apply()
    }

    fun load(vehicle: String, gear: Int): RpmCalibration? = runCatching {
        val key = key(vehicle, gear)
        RpmCalibration(preferences.getString("${key}_2000", null)?.toDouble() ?: return null,
            preferences.getString("${key}_3000", null)?.toDouble() ?: return null)
    }.getOrNull()
}
