package com.roaddyno.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SessionEntity::class, SpeedSampleEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class RoadDynoDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN speed2000Kmh REAL")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN speed3000Kmh REAL")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN tyreSize TEXT")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN vehicleName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN measurementMassKg REAL")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN measurementGear INTEGER")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN calibrationRpm REAL")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN calibrationSpeedKmh REAL")
            }
        }
    }
}
