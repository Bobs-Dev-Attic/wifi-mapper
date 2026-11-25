package com.bobsdevattic.wifimapper.viz

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** WiFi band derived from a channel centre frequency (MHz). */
enum class WifiBand(val label: String) {
    BAND_2_4("2.4 GHz"),
    BAND_5("5 GHz"),
    BAND_6("6 GHz"),   // Wi-Fi 6E / 7 — the S25 Edge supports this.
    UNKNOWN("?");

    companion object {
        fun of(frequencyMhz: Int): WifiBand = when (frequencyMhz) {
            in 2401..2495 -> BAND_2_4
            in 5150..5895 -> BAND_5
            in 5925..7125 -> BAND_6
            else -> UNKNOWN
        }
    }
}

/**
 * Colour scales for signal strength. Every palette runs weak → strong. All but
 * [RED_GREEN] are colour-vision-deficiency (CVD) friendly: they vary in
 * *lightness* monotonically, so they stay readable for red-green colour
 * blindness and even in greyscale.
 */
enum class SignalPalette(
    val label: String,
    val description: String,
    val colorblindSafe: Boolean,
    private val stops: List<Color>,
) {
    CIVIDIS(
        "Blue → Yellow (Cividis)",
        "Designed for colour-vision deficiency",
        true,
        listOf(0xFF00204D, 0xFF31446B, 0xFF666970, 0xFF958F78, 0xFFCBBA69, 0xFFFFEA46).map { Color(it) },
    ),
    BLUE_YELLOW(
        "Blue → Yellow (vivid)",
        "Higher-contrast blue to yellow",
        true,
        listOf(0xFF0A2F6B, 0xFF2E6FBF, 0xFF8FB3D9, 0xFFF2E08A, 0xFFFFD500).map { Color(it) },
    ),
    VIRIDIS(
        "Purple → Yellow (Viridis)",
        "Perceptually uniform, CVD-safe",
        true,
        listOf(0xFF440154, 0xFF3B528B, 0xFF21918C, 0xFF5EC962, 0xFFFDE725).map { Color(it) },
    ),
    MAGMA(
        "Black → Cream (Magma)",
        "Works well in greyscale too",
        true,
        listOf(0xFF000004, 0xFF51127C, 0xFFB73779, 0xFFFC8961, 0xFFFCFDBF).map { Color(it) },
    ),
    BLUE_ORANGE(
        "Blue → Orange",
        "Blue/orange pair, CVD-safe",
        true,
        listOf(0xFF2166AC, 0xFF67A9CF, 0xFFD1E5F0, 0xFFFDB863, 0xFFE66101).map { Color(it) },
    ),
    RED_GREEN(
        "Red → Green (classic)",
        "Not colour-blind safe",
        false,
        listOf(0xFFD32F2F, 0xFFFBC02D, 0xFF388E3C).map { Color(it) },
    );

    /** Colour at [t] in 0..1 along the palette (0 = weak, 1 = strong). */
    fun at(t: Float): Color {
        val x = t.coerceIn(0f, 1f) * (stops.size - 1)
        val i = x.toInt().coerceAtMost(stops.size - 2)
        return lerp(stops[i], stops[i + 1], x - i)
    }

    /** Evenly spaced colours for drawing a legend gradient. */
    fun gradient(steps: Int = 8): List<Color> = (0 until steps).map { at(it / (steps - 1f)) }

    companion object {
        val DEFAULT = CIVIDIS
        fun fromName(name: String?): SignalPalette =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

object SignalColor {
    // Usable RSSI range for mapping to colour. -30 dBm ~ excellent, -90 ~ dead.
    const val RSSI_BEST = -30
    const val RSSI_WORST = -90

    /** 0f (worst) .. 1f (best) normalised signal quality. */
    fun quality(rssi: Int): Float =
        ((rssi - RSSI_WORST).toFloat() / (RSSI_BEST - RSSI_WORST)).coerceIn(0f, 1f)

    /** Heat colour for an RSSI value in the given palette. */
    fun forRssi(rssi: Int, palette: SignalPalette = SignalPalette.DEFAULT): Color =
        palette.at(quality(rssi))
}
