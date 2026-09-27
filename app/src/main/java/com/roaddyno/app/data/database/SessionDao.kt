package com.roaddyno.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Insert
    suspend fun insertSamples(samples: List<SpeedSampleEntity>)

    @Query("""UPDATE measurement_sessions SET
        sampleCount = sampleCount + :count,
        startedElapsedRealtimeNs = COALESCE(startedElapsedRealtimeNs, :firstTimestampNs)
        WHERE id = :sessionId""")
    suspend fun addSampleCount(sessionId: Long, count: Int, firstTimestampNs: Long)

    @Query("UPDATE measurement_sessions SET status = :status, endedElapsedRealtimeNs = :endedNs WHERE id = :sessionId")
    suspend fun finish(sessionId: Long, status: String, endedNs: Long)

    @Query("UPDATE measurement_sessions SET status = 'INTERRUPTED' WHERE status = 'RECORDING'")
    suspend fun markInterruptedSessions()

    @Query("SELECT * FROM measurement_sessions ORDER BY id DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM measurement_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): SessionEntity?

    @Query("""UPDATE measurement_sessions SET vehicleName = :vehicle, measurementMassKg = :mass,
        measurementGear = :gear, calibrationRpm = :rpm, calibrationSpeedKmh = :speed
        WHERE id = :sessionId AND status != 'RECORDING'""")
    suspend fun updateConfiguration(sessionId: Long, vehicle: String, mass: Double, gear: Int?, rpm: Double?, speed: Double?)

    @Query("SELECT * FROM speed_samples WHERE sessionId = :sessionId ORDER BY sampleIndex ASC")
    suspend fun getSamples(sessionId: Long): List<SpeedSampleEntity>

    @Query("DELETE FROM measurement_sessions WHERE id = :sessionId AND status != 'RECORDING'")
    suspend fun deleteSession(sessionId: Long)
}
