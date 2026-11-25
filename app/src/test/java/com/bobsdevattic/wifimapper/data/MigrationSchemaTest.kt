package com.bobsdevattic.wifimapper.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the hand-written migrations: Room rejects a migrated database at open
 * time if its tables differ from what the entities declare. Room exports the
 * expected schema for the CURRENT version (v4) (with `${TABLE_NAME}` placeholders)
 * to app/schemas at build time; what our migrations produce must match it.
 */
class MigrationSchemaTest {

    private val schema: String by lazy {
        val f = File("schemas/com.bobsdevattic.wifimapper.data.WifiMapperDatabase/4.json")
        assertTrue("Room schema not exported at ${f.absolutePath}", f.exists())
        f.readText()
    }

    private val table = "`\${TABLE_NAME}`"

    private fun assertInSchema(what: String, sql: String) =
        assertTrue("$what differs from Room's exported schema:\n$sql", schema.contains(sql))

    @Test fun floorsTableMatchesRoom() {
        assertInSchema("floors table", Migrations.CREATE_FLOORS.replace("EXISTS `floors`", "EXISTS $table"))
        assertInSchema("floors index", Migrations.CREATE_FLOORS_INDEX.replace("ON `floors`", "ON $table"))
    }

    @Test fun wallsTableAfterBothMigrationsMatchesRoom() {
        // 1→2 creates walls; 2→3 appends a nullable floorId column (ALTER TABLE
        // ADD COLUMN puts it last, which is where the entity declares it).
        val v3 = Migrations.CREATE_WALLS
            .replace("EXISTS `walls`", "EXISTS $table")
            .replace("`floorIndex` INTEGER NOT NULL, FOREIGN", "`floorIndex` INTEGER NOT NULL, `floorId` INTEGER, FOREIGN")
        assertInSchema("walls table", v3)
        assertInSchema("walls index", Migrations.CREATE_WALLS_INDEX.replace("ON `walls`", "ON $table"))
    }

    @Test fun bleReadingsTableMatchesRoom() {
        assertInSchema("ble_readings table", Migrations.CREATE_BLE_READINGS.replace("EXISTS `ble_readings`", "EXISTS $table"))
        assertInSchema("ble_readings index", Migrations.CREATE_BLE_READINGS_INDEX.replace("ON `ble_readings`", "ON $table"))
    }

    @Test fun samplePointsGainNullableFloorId() {
        assertInSchema("sample_points.floorId", "`apCount` INTEGER NOT NULL, `floorId` INTEGER, FOREIGN KEY")
    }

    @Test fun migrationAddsExactlyTheNewColumns() {
        val alters = Migrations.MIGRATION_2_3_SQL.filter { it.startsWith("ALTER TABLE") }
        assertTrue(alters.toString(), alters == listOf(
            "ALTER TABLE `sample_points` ADD COLUMN `floorId` INTEGER",
            "ALTER TABLE `walls` ADD COLUMN `floorId` INTEGER",
        ))
    }
}
