package com.bobsdevattic.wifimapper.model

/** A floor of a map, as far as layout is concerned. */
data class FloorInfo(val id: Long, val label: String, val orderIndex: Int)

/**
 * Stacks a map's floors vertically. Each floor is captured in its own AR run
 * whose origin is the floor's marked start point, so its points have local Y≈0
 * at phone height. We place floors by their user-chosen order: rank 0 is the
 * lowest, each next floor [spacingM] higher.
 */
object FloorLayout {

    /** floorId → rank (0 = lowest), by orderIndex then creation (id). */
    fun ranks(floors: List<FloorInfo>): Map<Long, Int> =
        floors.sortedWith(compareBy({ it.orderIndex }, { it.id }))
            .mapIndexed { rank, f -> f.id to rank }.toMap()

    fun yOffset(rank: Int, spacingM: Float): Float = rank * spacingM

    /** Floors ordered top-to-bottom, as people read a building. */
    fun topDown(floors: List<FloorInfo>): List<FloorInfo> =
        floors.sortedWith(compareBy({ -it.orderIndex }, { -it.id }))

    /**
     * New orderIndex values after moving [floorId] one step up (+1) or down (−1),
     * renumbered 0..n−1 (lowest = 0). Returns the unchanged order at the ends.
     */
    fun move(floors: List<FloorInfo>, floorId: Long, step: Int): Map<Long, Int> {
        val bottomUp = floors.sortedWith(compareBy({ it.orderIndex }, { it.id })).toMutableList()
        val i = bottomUp.indexOfFirst { it.id == floorId }
        val j = i + step
        if (i >= 0 && j in bottomUp.indices) {
            val t = bottomUp[i]; bottomUp[i] = bottomUp[j]; bottomUp[j] = t
        }
        return bottomUp.mapIndexed { idx, f -> f.id to idx }.toMap()
    }

    /** orderIndex for a new floor placed above everything (or below everything). */
    fun newOrderIndex(floors: List<FloorInfo>, above: Boolean): Int =
        if (floors.isEmpty()) 0
        else if (above) floors.maxOf { it.orderIndex } + 1
        else floors.minOf { it.orderIndex } - 1
}
