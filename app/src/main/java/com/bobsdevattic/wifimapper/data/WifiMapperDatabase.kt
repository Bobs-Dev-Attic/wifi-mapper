package com.bobsdevattic.wifimapper.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, SamplePointEntity::class, ApReadingEntity::class, WallEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class WifiMapperDatabase : RoomDatabase() {
    abstract fun dao(): WifiMapperDao

    companion object {
        @Volatile private var instance: WifiMapperDatabase? = null

        fun get(context: Context): WifiMapperDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WifiMapperDatabase::class.java,
                    "wifi_mapper.db",
                )
                    // Keep users' existing maps across schema changes.
                    .addMigrations(*Migrations.ALL)
                    .build().also { instance = it }
            }
    }
}
