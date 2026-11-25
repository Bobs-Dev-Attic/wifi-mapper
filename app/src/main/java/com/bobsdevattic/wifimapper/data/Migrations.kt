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

    /** v2 → v3 (app 0.6.0): floors. Existing maps get a single "Floor 1". */
    const val CREATE_FLOORS =
        "CREATE TABLE IF NOT EXISTS `floors` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`sessionId` INTEGER NOT NULL, `label` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )"

    const val CREATE_FLOORS_INDEX =
        "CREATE INDEX IF NOT EXISTS `index_floors_sessionId` ON `floors` (`sessionId`)"

    /** Ordered statements for 2→3; also executed against SQLite in unit tests. */
    val MIGRATION_2_3_SQL = listOf(
        CREATE_FLOORS,
        CREATE_FLOORS_INDEX,
        "ALTER TABLE `sample_points` ADD COLUMN `floorId` INTEGER",
        "ALTER TABLE `walls` ADD COLUMN `floorId` INTEGER",
        // One floor per existing map, then attach that map's points and walls to it.
        "INSERT INTO `floors` (`sessionId`, `label`, `orderIndex`, `createdAt`) " +
            "SELECT `id`, 'Floor 1', 0, `createdAt` FROM `sessions`",
        "UPDATE `sample_points` SET `floorId` = " +
            "(SELECT f.`id` FROM `floors` f WHERE f.`sessionId` = `sample_points`.`sessionId`)",
        "UPDATE `walls` SET `floorId` = " +
            "(SELECT f.`id` FROM `floors` f WHERE f.`sessionId` = `walls`.`sessionId`)",
    )

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_2_3_SQL.forEach(db::execSQL)
        }
    }

    /** v3 → v4 (app 0.8.0): Bluetooth readings per point. */
    const val CREATE_BLE_READINGS =
        "CREATE TABLE IF NOT EXISTS `ble_readings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`pointId` INTEGER NOT NULL, `address` TEXT NOT NULL, `name` TEXT, `rssi` INTEGER NOT NULL, " +
            "FOREIGN KEY(`pointId`) REFERENCES `sample_points`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

    const val CREATE_BLE_READINGS_INDEX =
        "CREATE INDEX IF NOT EXISTS `index_ble_readings_pointId` ON `ble_readings` (`pointId`)"

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_BLE_READINGS)
            db.execSQL(CREATE_BLE_READINGS_INDEX)
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
