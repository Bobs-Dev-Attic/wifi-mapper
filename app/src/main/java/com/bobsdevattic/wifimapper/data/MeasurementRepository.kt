package com.bobsdevattic.wifimapper.data

import android.content.Context
import com.bobsdevattic.wifimapper.model.FusedSample
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

    companion object {
        fun from(context: Context) = MeasurementRepository(WifiMapperDatabase.get(context).dao())
    }
}
