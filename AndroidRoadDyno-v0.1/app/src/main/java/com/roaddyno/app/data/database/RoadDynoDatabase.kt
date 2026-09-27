package com.roaddyno.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, SpeedSampleEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RoadDynoDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}
