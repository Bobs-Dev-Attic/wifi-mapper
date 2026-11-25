package com.bobsdevattic.wifimapper.model

import java.util.Locale
import kotlin.math.roundToInt

/** How distances are shown. All values are stored and computed in metres. */
enum class DistanceUnit(val label: String) {
    METRIC("Metres (m)"),
    IMPERIAL("Feet (ft)");

    companion object {
        /** Feet where that's the everyday unit; metres everywhere else. */
        fun forCountry(country: String): DistanceUnit =
            if (country.uppercase() in setOf("US", "LR", "MM")) IMPERIAL else METRIC

        fun default(): DistanceUnit = forCountry(Locale.getDefault().country)
    }
}

/** Distance formatting in the user's chosen unit. Pure Kotlin. */
object UnitFormat {
    const val FT_PER_M = 3.28084

    fun toFeet(m: Double) = m * FT_PER_M

    /** A rough distance, e.g. "4.2 m" / "14 ft". */
    fun distance(m: Double, unit: DistanceUnit): String = when (unit) {
        DistanceUnit.METRIC -> when {
            m < 1 -> "<1 m"
            m < 10 -> "%.1f m".format(m)
            m < 100 -> "${m.toInt()} m"
            else -> ">100 m"
        }
        DistanceUnit.IMPERIAL -> {
            val ft = toFeet(m)
            when {
                ft < 3 -> "<3 ft"
                ft < 330 -> "${ft.roundToInt()} ft"
                else -> ">330 ft"
            }
        }
    }

    /** A distance range for tracker-style readouts, e.g. "5.9-22 m" / "19-72 ft". */
    fun range(range: Pair<Double, Double>, unit: DistanceUnit): String = when (unit) {
        DistanceUnit.METRIC -> {
            fun f(m: Double) = if (m < 10) "%.1f".format(m).removeSuffix(".0") else m.toInt().toString()
            "${f(range.first)}-${f(range.second)} m"
        }
        DistanceUnit.IMPERIAL ->
            "${toFeet(range.first).roundToInt()}-${toFeet(range.second).roundToInt()} ft"
    }

    /** A precise length like floor spacing, e.g. "3.0 m" / "9.8 ft". */
    fun length(m: Double, unit: DistanceUnit): String = when (unit) {
        DistanceUnit.METRIC -> "%.1f m".format(m)
        DistanceUnit.IMPERIAL -> "%.1f ft".format(toFeet(m))
    }

    /**
     * Tracker range rings as (metres, label): round numbers in the chosen unit
     * (1/3/10/30 m, or 3/10/30/100 ft) rather than awkward conversions.
     */
    fun trackerRings(unit: DistanceUnit): List<Pair<Double, String>> = when (unit) {
        DistanceUnit.METRIC -> listOf(1.0, 3.0, 10.0, 30.0).map { it to "${it.toInt()}m" }
        DistanceUnit.IMPERIAL -> listOf(3.0, 10.0, 30.0, 100.0).map { it / FT_PER_M to "${it.toInt()}ft" }
    }

    fun emptyRange(unit: DistanceUnit) = if (unit == DistanceUnit.METRIC) "--- m" else "--- ft"
}
