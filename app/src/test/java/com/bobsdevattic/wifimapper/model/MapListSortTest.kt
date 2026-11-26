package com.bobsdevattic.wifimapper.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MapListSortTest {
    private val maps = listOf(
        MapSummary(1, "garage", createdAt = 100, geoAnchored = false, floors = 1, points = 40),
        MapSummary(2, "House", createdAt = 300, geoAnchored = true, floors = 3, points = 120),
        MapSummary(3, "attic", createdAt = 200, geoAnchored = true, floors = 1, points = 120),
    )

    private fun ids(s: MapSort, rev: Boolean = false) = MapListSort.sort(maps, s, rev).map { it.id }

    @Test fun sortsEachWayWithNewestTieBreak() {
        assertEquals(listOf(2L, 3L, 1L), ids(MapSort.NEWEST))
        assertEquals(listOf(1L, 3L, 2L), ids(MapSort.NEWEST, rev = true))
        assertEquals(listOf(3L, 1L, 2L), ids(MapSort.NAME))                 // case-insensitive
        assertEquals(listOf(2L, 1L, 3L), ids(MapSort.NAME, rev = true))
        assertEquals(listOf(2L, 3L, 1L), ids(MapSort.POINTS))               // tie 120 → newer first
        assertEquals(listOf(1L, 2L, 3L), ids(MapSort.POINTS, rev = true))   // fewest first, tie → newer
        assertEquals(listOf(2L, 3L, 1L), ids(MapSort.FLOORS))
        assertEquals(listOf(2L, 3L, 1L), ids(MapSort.GEO))
        assertEquals(listOf(1L, 2L, 3L), ids(MapSort.GEO, rev = true))
    }
}
