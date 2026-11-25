package com.bobsdevattic.wifimapper.model

/** A measurement point for analysis: where it is, and RSSI per access point. */
data class MeasuredPoint(
    val position: Vector3,
    /** BSSID -> RSSI (dBm). */
    val readings: Map<String, Int>,
)

enum class LossConfidence(val label: String) { NONE("no data"), LOW("low"), MEDIUM("medium"), HIGH("high") }

/** Estimated attenuation through one wall. [lossDb] is null when there's no data. */
data class WallLoss(
    val wall: WallObservation,
    val lossDb: Float?,
    val pairCount: Int,
    val confidence: LossConfidence,
)

/**
 * Estimates how much signal each wall costs, from measurements taken on both
 * sides of it.
 *
 * Method, per wall:
 *  1. Find pairs of nearby points (≤ [maxPairDistance] m apart) whose connecting
 *     segment passes through the wall.
 *  2. For each pair, take the strongest access point seen at BOTH points.
 *  3. Per access point, the side with the higher average RSSI is taken to be the
 *     side facing that AP; the pair's raw loss = RSSI(AP side) − RSSI(far side).
 *  4. Subtract the normal fall-off over that distance, estimated as the median
 *     |ΔRSSI| per metre between nearby pairs that cross NO wall.
 *  5. The wall's loss is the median over its pairs, clamped to 0..[maxLossDb].
 *
 * The median keeps it robust to multipath noise; confidence reflects how many
 * crossing pairs support the estimate. Pure Kotlin — no Android types.
 */
class WallLossAnalyzer(
    private val maxPairDistance: Float = 3.0f,
    private val minPairDistance: Float = 0.3f,
    private val defaultFalloffDbPerM: Float = 2.0f,
    private val maxLossDb: Float = 40f,
) {

    fun analyze(walls: List<WallObservation>, points: List<MeasuredPoint>): List<WallLoss> {
        if (walls.isEmpty()) return emptyList()

        data class PointPair(val a: MeasuredPoint, val b: MeasuredPoint, val dist: Float, val bssid: String)

        // Candidate pairs: nearby, sharing at least one AP.
        val pairs = ArrayList<PointPair>()
        for (i in points.indices) for (j in i + 1 until points.size) {
            val a = points[i]; val b = points[j]
            val d = a.position.distanceTo(b.position)
            if (d < minPairDistance || d > maxPairDistance) continue
            val bssid = strongestCommonAp(a, b) ?: continue
            pairs += PointPair(a, b, d, bssid)
        }

        // Baseline fall-off from pairs that cross no wall at all.
        val baseline = pairs
            .filter { p -> walls.none { it.intersects(p.a.position, p.b.position) } }
            .map { p -> kotlin.math.abs(p.a.readings.getValue(p.bssid) - p.b.readings.getValue(p.bssid)) / p.dist }
        val falloff = median(baseline) ?: defaultFalloffDbPerM

        return walls.map { wall ->
            val crossing = pairs.filter { wall.intersects(it.a.position, it.b.position) }
            if (crossing.isEmpty()) return@map WallLoss(wall, null, 0, LossConfidence.NONE)

            // Which side faces each AP? (+1 = normal side, −1 = back side)
            val apSide: Map<String, Int> = crossing.groupBy { it.bssid }.mapValues { (bssid, ps) ->
                var pos = 0.0; var neg = 0.0
                for (p in ps) {
                    val (front, back) = orient(wall, p.a, p.b)
                    pos += front.readings.getValue(bssid); neg += back.readings.getValue(bssid)
                }
                if (pos >= neg) 1 else -1
            }

            val losses = crossing.map { p ->
                val (front, back) = orient(wall, p.a, p.b)
                val near = if (apSide.getValue(p.bssid) > 0) front else back
                val far = if (near === front) back else front
                (near.readings.getValue(p.bssid) - far.readings.getValue(p.bssid)) - falloff * p.dist
            }
            val loss = median(losses)!!.coerceIn(0f, maxLossDb)
            WallLoss(wall, loss, crossing.size, confidenceFor(crossing.size))
        }
    }

    /** Returns (point on the normal side, point on the back side). */
    private fun orient(wall: WallObservation, a: MeasuredPoint, b: MeasuredPoint) =
        if (wall.signedDistance(a.position) > 0f) a to b else b to a

    private fun strongestCommonAp(a: MeasuredPoint, b: MeasuredPoint): String? =
        a.readings.keys.filter { it in b.readings }
            .maxByOrNull { minOf(a.readings.getValue(it), b.readings.getValue(it)) }

    companion object {
        fun confidenceFor(pairs: Int) = when {
            pairs >= 10 -> LossConfidence.HIGH
            pairs >= 4 -> LossConfidence.MEDIUM
            pairs >= 1 -> LossConfidence.LOW
            else -> LossConfidence.NONE
        }

        fun median(xs: List<Float>): Float? {
            if (xs.isEmpty()) return null
            val s = xs.sorted()
            val m = s.size / 2
            return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2f
        }
    }
}
