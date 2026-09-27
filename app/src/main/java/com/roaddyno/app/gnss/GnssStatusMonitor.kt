package com.roaddyno.app.gnss

import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GnssStatusInfo(val visible: Int? = null, val usedInFix: Int? = null, val started: Boolean = false)

class GnssStatusMonitor(private val context: Context, private val manager: LocationManager) {
    private val mutableStatus = MutableStateFlow(GnssStatusInfo())
    val status = mutableStatus.asStateFlow()
    private var registered = false
    private val callback = object : GnssStatus.Callback() {
        override fun onStarted() { mutableStatus.value = mutableStatus.value.copy(started = true) }
        override fun onStopped() { mutableStatus.value = GnssStatusInfo() }
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (index in 0 until status.satelliteCount) if (status.usedInFix(index)) used++
            mutableStatus.value = GnssStatusInfo(status.satelliteCount, used, true)
        }
    }

    fun start() {
        if (!registered) {
            registered = manager.registerGnssStatusCallback(ContextCompat.getMainExecutor(context), callback)
        }
    }

    fun stop() {
        if (registered) manager.unregisterGnssStatusCallback(callback)
        registered = false
        mutableStatus.value = GnssStatusInfo()
    }
}
