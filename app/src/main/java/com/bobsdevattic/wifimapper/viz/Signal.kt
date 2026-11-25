package com.bobsdevattic.wifimapper.viz

import androidx.compose.ui.graphics.Color

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

object SignalColor {
    // Usable RSSI range for mapping to colour. -30 dBm ~ excellent, -90 ~ dead.
    const val RSSI_BEST = -30
    const val RSSI_WORST = -90

    /** 0f (worst) .. 1f (best) normalised signal quality. */
    fun quality(rssi: Int): Float =
        ((rssi - RSSI_WORST).toFloat() / (RSSI_BEST - RSSI_WORST)).coerceIn(0f, 1f)

    /**
     * Red (weak) -> yellow -> green (strong) heat colour for an RSSI value.
     * Uses a simple HSV sweep from hue 0 (red) to 120 (green).
     */
    fun forRssi(rssi: Int): Color {
        val q = quality(rssi)
        val hue = 120f * q // 0=red .. 120=green
        return Color.hsv(hue, 0.85f, 0.95f)
    }
}
