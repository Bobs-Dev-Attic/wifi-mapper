package com.bobsdevattic.wifimapper.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FloorLayoutTest {
    private val ground = FloorInfo(1, "Ground", 0)
    private val upstairs = FloorInfo(2, "Upstairs", 1)
    private val basement = FloorInfo(3, "Basement", -1)
    private val all = listOf(ground, upstairs, basement)

    @Test fun ranksLowestFirst() {
        assertEquals(mapOf(3L to 0, 1L to 1, 2L to 2), FloorLayout.ranks(all))
        assertEquals(6f, FloorLayout.yOffset(2, 3f))
    }

    @Test fun topDownForDisplay() {
        assertEquals(listOf("Upstairs", "Ground", "Basement"), FloorLayout.topDown(all).map { it.label })
    }

    @Test fun moveSwapsAndRenumbers() {
        // Move the basement up one: it swaps with ground. Renumbered 0..2.
        assertEquals(mapOf(1L to 0, 3L to 1, 2L to 2), FloorLayout.move(all, 3, +1))
        // Moving the top floor further up is a no-op (still renumbered).
        assertEquals(mapOf(3L to 0, 1L to 1, 2L to 2), FloorLayout.move(all, 2, +1))
    }

    @Test fun newFloorGoesAboveOrBelow() {
        assertEquals(2, FloorLayout.newOrderIndex(all, above = true))
        assertEquals(-2, FloorLayout.newOrderIndex(all, above = false))
        assertEquals(0, FloorLayout.newOrderIndex(emptyList(), above = true))
    }
}
