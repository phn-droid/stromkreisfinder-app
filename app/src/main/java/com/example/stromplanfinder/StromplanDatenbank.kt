package com.example.stromplanfinder

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StromEintragEntity::class],
    version = 3,
    exportSchema = false
)
abstract class StromplanDatenbank : RoomDatabase() {

    abstract fun stromEintragDao(): StromEintragDao

    companion object {
        @Volatile
        private var INSTANCE: StromplanDatenbank? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE stromEintraege " +
                            "ADD COLUMN leitungsbezeichnung TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "ALTER TABLE stromEintraege " +
                            "ADD COLUMN kabelart TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun getInstance(context: Context): StromplanDatenbank {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    StromplanDatenbank::class.java,
                    "stromplan.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
