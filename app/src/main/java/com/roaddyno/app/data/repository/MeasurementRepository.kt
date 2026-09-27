package com.roaddyno.app.data.repository

import android.os.Build
import androidx.room.withTransaction
import com.roaddyno.app.data.database.RoadDynoDatabase
import com.roaddyno.app.data.database.SessionEntity
import com.roaddyno.app.data.database.SpeedSampleEntity
import com.roaddyno.app.dyno.RunConfiguration

class MeasurementRepository(private val database: RoadDynoDatabase) {
    private val dao = database.sessionDao()
    val sessions = dao.observeSessions()

    suspend fun recoverInterruptedSessions() = dao.markInterruptedSessions()

    suspend fun createSession(configuration: RunConfiguration): Long = dao.insertSession(SessionEntity(
        createdAtMillis = System.currentTimeMillis(),
        deviceManufacturer = Build.MANUFACTURER,
        deviceModel = Build.MODEL,
        androidVersion = Build.VERSION.RELEASE,
        vehicleName = configuration.vehicleName,
        measurementMassKg = configuration.massKg,
        measurementGear = configuration.gear,
        calibrationRpm = configuration.calibrationRpm,
        calibrationSpeedKmh = configuration.calibrationSpeedKmh,
    ))

    suspend fun append(sessionId: Long, samples: List<SpeedSampleEntity>) {
        if (samples.isEmpty()) return
        database.withTransaction {
            dao.insertSamples(samples)
            dao.addSampleCount(sessionId, samples.size, samples.first().timestampNs)
        }
    }

    suspend fun finish(sessionId: Long, status: String, endedNs: Long) = dao.finish(sessionId, status, endedNs)
    suspend fun getSession(id: Long) = dao.getSession(id)
    suspend fun saveConfiguration(id: Long, config: RunConfiguration) {
        config.validate()
        dao.updateConfiguration(id, config.vehicleName, config.massKg, config.gear,
            config.calibrationRpm, config.calibrationSpeedKmh)
    }
    suspend fun getSamples(id: Long) = dao.getSamples(id)
    suspend fun delete(id: Long) = dao.deleteSession(id)
}
