package com.bobsdevattic.wifimapper.viz

import com.bobsdevattic.wifimapper.model.LossConfidence
import com.bobsdevattic.wifimapper.model.WallLoss

/** A wall ready to draw: its geometry, floor, and signal-loss estimate. */
data class WallRender(val loss: WallLoss, val floor: Int) {
    val corners get() = loss.wall.corners()
    val center get() = loss.wall.center

    /** e.g. "−9 dB · high", or "wall · no data" if never measured across. */
    val label: String
        get() = loss.lossDb?.let { "−${it.toInt()} dB · ${loss.confidence.label}" }
            ?: "wall · no data"

    val hasEstimate: Boolean get() = loss.confidence != LossConfidence.NONE
}

/** Summary for the stats panel. */
data class WallSummary(val count: Int, val measured: Int, val worstLossDb: Float?) {
    companion object {
        fun of(walls: List<WallRender>) = WallSummary(
            count = walls.size,
            measured = walls.count { it.hasEstimate },
            worstLossDb = walls.mapNotNull { it.loss.lossDb }.maxOrNull(),
        )
    }
}
