package com.example.stromplanfinder

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StromEintragEntity::class],
    version = 2,
    exportSchema = false
)
abstract class StromplanDatenbank : RoomDatabase() {

    abstract fun stromEintragDao(): StromEintragDao

    companion object {
        @Volatile
        private var INSTANCE: StromplanDatenbank? = null

        fun getInstance(context: Context): StromplanDatenbank {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    StromplanDatenbank::class.java,
                    "stromplan.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
