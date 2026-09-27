package com.roaddyno.app

import android.app.Application
import androidx.room.Room
import com.roaddyno.app.data.database.RoadDynoDatabase
import com.roaddyno.app.data.repository.MeasurementRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class RoadDynoApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database by lazy {
        Room.databaseBuilder(this, RoadDynoDatabase::class.java, "road_dyno.db").build()
    }
    val repository by lazy { MeasurementRepository(database) }
    val recovery by lazy { appScope.async { repository.recoverInterruptedSessions() } }

    override fun onCreate() {
        super.onCreate()
        recovery
    }
}
