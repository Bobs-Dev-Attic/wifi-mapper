package com.bobsdevattic.wifimapper.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hand-written schema migrations. The SQL must match exactly what Room generates
 * for the entities, or Room rejects the database at open time. That's enforced
 * by `MigrationSchemaTest`, which compares these strings against the schema JSON
 * Room exports at build time.
 */
object Migrations {

    /** v1 → v2 (app 0.5.0): add the `walls` table. */
    const val CREATE_WALLS =
        "CREATE TABLE IF NOT EXISTS `walls` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`sessionId` INTEGER NOT NULL, `cx` REAL NOT NULL, `cy` REAL NOT NULL, `cz` REAL NOT NULL, " +
            "`nx` REAL NOT NULL, `nz` REAL NOT NULL, `width` REAL NOT NULL, `height` REAL NOT NULL, " +
            "`floorIndex` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )"

    const val CREATE_WALLS_INDEX =
        "CREATE INDEX IF NOT EXISTS `index_walls_sessionId` ON `walls` (`sessionId`)"

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_WALLS)
            db.execSQL(CREATE_WALLS_INDEX)
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
