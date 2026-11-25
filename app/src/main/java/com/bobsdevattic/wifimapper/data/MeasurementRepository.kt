package com.bobsdevattic.wifimapper.data

import android.content.Context
import com.bobsdevattic.wifimapper.model.FusedSample
import com.bobsdevattic.wifimapper.model.Vector3
import com.bobsdevattic.wifimapper.model.WallObservation
import kotlinx.coroutines.flow.Flow

/** Thin repository over the Room DAO; the rest of the app talks to this. */
class MeasurementRepository(private val dao: WifiMapperDao) {

    suspend fun startSession(name: String, baselinePressureHpa: Float?): Long =
        dao.insertSession(
            SessionEntity(
                name = name,
                createdAt = System.currentTimeMillis(),
                anchorLat = null, anchorLon = null, anchorAltitude = null,
                anchorAccuracy = null, anchorHeading = null,
                baselinePressureHpa = baselinePressureHpa,
            )
        )

    suspend fun setAnchor(sessionId: Long, s: FusedSample.GeoFix) =
        dao.updateAnchor(sessionId, s.lat, s.lon, s.altitude, s.accuracy, s.heading)

    suspend fun setBaseline(sessionId: Long, pressureHpa: Float) =
        dao.updateBaseline(sessionId, pressureHpa)

    /** Persist one fused measurement and its per-AP readings. */
    suspend fun saveSample(sessionId: Long, sample: FusedSample): Long {
        val best = sample.readings.maxByOrNull { it.rssi }?.rssi ?: -100
        val point = SamplePointEntity(
            sessionId = sessionId,
            timestamp = sample.timestamp,
            x = sample.position.x, y = sample.position.y, z = sample.position.z,
            arTracked = sample.arTracked,
            baroAltitudeMeters = sample.baroAltitudeMeters,
            floorIndex = sample.floorIndex,
            bestRssi = best,
            apCount = sample.readings.size,
        )
        return dao.insertSample(point) { pointId ->
            sample.readings.map {
                ApReadingEntity(
                    pointId = pointId,
                    bssid = it.bssid, ssid = it.ssid,
                    rssi = it.rssi, frequencyMhz = it.frequencyMhz,
                )
            }
        }
    }

    fun observeSessions(): Flow<List<SessionEntity>> = dao.observeSessions()
    suspend fun getSession(id: Long) = dao.getSession(id)
    fun observePoints(sessionId: Long): Flow<List<SamplePointWithReadings>> = dao.observePoints(sessionId)
    suspend fun getPoints(sessionId: Long) = dao.getPoints(sessionId)
    fun observePointCount(sessionId: Long): Flow<Int> = dao.observePointCount(sessionId)
    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    /** Store the latest set of detected walls for a session (replaces previous). */
    suspend fun replaceWalls(sessionId: Long, walls: List<WallObservation>) =
        dao.replaceWalls(sessionId, walls.map { w ->
            WallEntity(
                sessionId = sessionId,
                cx = w.center.x, cy = w.center.y, cz = w.center.z,
                nx = w.nx, nz = w.nz, width = w.width, height = w.height,
                // Same convention as sample points without a barometer: ~3 m storeys
                // relative to the AR origin (the phone's start height).
                floorIndex = Math.round(w.center.y / 3f),
            )
        })

    /** A session's walls, paired with the floor index each was assigned. */
    suspend fun getWalls(sessionId: Long): List<Pair<WallObservation, Int>> =
        dao.getWalls(sessionId).map { e ->
            WallObservation(Vector3(e.cx, e.cy, e.cz), e.nx, e.nz, e.width, e.height) to e.floorIndex
        }

    companion object {
        fun from(context: Context) = MeasurementRepository(WifiMapperDatabase.get(context).dao())
    }
}
