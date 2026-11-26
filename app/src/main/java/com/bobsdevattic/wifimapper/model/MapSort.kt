package com.bobsdevattic.wifimapper.model

/** One saved map as shown on the home list. */
data class MapSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val geoAnchored: Boolean,
    val floors: Int,
    val points: Int,
)

/** Ways to order the saved-maps list. The first is the default. */
enum class MapSort(val label: String) {
    NEWEST("Newest"),
    NAME("Name"),
    POINTS("Most points"),
    FLOORS("Most floors"),
    GEO("Geo-anchored first"),
}

object MapListSort {
    /**
     * Orders [maps] by [sort] in its natural direction (newest, A→Z, most);
     * [reversed] flips that key. Ties fall back to newest first.
     */
    fun sort(maps: List<MapSummary>, sort: MapSort, reversed: Boolean = false): List<MapSummary> {
        val key: Comparator<MapSummary> = when (sort) {
            MapSort.NEWEST -> compareByDescending { it.createdAt }
            MapSort.NAME -> compareBy { it.name.trim().lowercase() }
            MapSort.POINTS -> compareByDescending { it.points }
            MapSort.FLOORS -> compareByDescending { it.floors }
            MapSort.GEO -> compareByDescending { it.geoAnchored }
        }
        return maps.sortedWith((if (reversed) key.reversed() else key).thenByDescending { it.createdAt })
    }
}
