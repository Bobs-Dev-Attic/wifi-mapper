package com.bobsdevattic.wifimapper.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the hand-written v1→v2 migration: Room rejects a migrated database at
 * open time if its tables differ from what the entities declare. Room exports
 * the expected schema (with `${TABLE_NAME}` placeholders) to app/schemas at
 * build time; the migration SQL must match it exactly.
 */
class MigrationSchemaTest {

    private val schema: String by lazy {
        val f = File("schemas/com.bobsdevattic.wifimapper.data.WifiMapperDatabase/2.json")
        assertTrue("Room schema not exported at ${f.absolutePath}", f.exists())
        f.readText()
    }

    private val tablePlaceholder = "`\${TABLE_NAME}`"

    @Test fun wallsTableSqlMatchesRoom() {
        val expected = Migrations.CREATE_WALLS.replace("EXISTS `walls`", "EXISTS $tablePlaceholder")
        assertTrue("walls CREATE TABLE differs from Room's schema:\n$expected", schema.contains(expected))
    }

    @Test fun wallsIndexSqlMatchesRoom() {
        val expected = Migrations.CREATE_WALLS_INDEX.replace("ON `walls`", "ON $tablePlaceholder")
        assertTrue("walls index differs from Room's schema:\n$expected", schema.contains(expected))
    }
}
