package com.bobsdevattic.wifimapper.data

import android.content.Context
import com.bobsdevattic.wifimapper.model.FloorInfo
import com.bobsdevattic.wifimapper.model.FloorLayout
import com.bobsdevattic.wifimapper.model.FusedSample
import com.bobsdevattic.wifimapper.model.Vector3
import com.bobsdevattic.wifimapper.model.WallObservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    /** Persist one fused measurement, its Wi-Fi readings and any Bluetooth devices heard. */
    suspend fun saveSample(
        sessionId: Long,
        floorId: Long,
        sample: FusedSample,
        ble: List<com.bobsdevattic.wifimapper.capture.BleDeviceInfo> = emptyList(),
    ): Long {
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
            floorId = floorId,
        )
        return dao.insertSample(point, readings = { pointId ->
            sample.readings.map {
                ApReadingEntity(
                    pointId = pointId,
                    bssid = it.bssid, ssid = it.ssid,
                    rssi = it.rssi, frequencyMhz = it.frequencyMhz,
                )
            }
        }, bleReadings = { pointId ->
            ble.map { BleReadingEntity(pointId = pointId, address = it.address, name = it.name, rssi = it.rssi) }
        })
    }

    fun observeSessions(): Flow<List<SessionEntity>> = dao.observeSessions()
    suspend fun getSession(id: Long) = dao.getSession(id)
    fun observePoints(sessionId: Long): Flow<List<SamplePointWithReadings>> = dao.observePoints(sessionId)
    suspend fun getPoints(sessionId: Long) = dao.getPoints(sessionId)
    fun observePointCount(sessionId: Long): Flow<Int> = dao.observePointCount(sessionId)
    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    /** Store the latest set of detected walls for one floor (replaces previous). */
    suspend fun replaceWalls(sessionId: Long, floorId: Long, walls: List<WallObservation>) =
        dao.replaceWalls(floorId, walls.map { w ->
            WallEntity(
                sessionId = sessionId,
                cx = w.center.x, cy = w.center.y, cz = w.center.z,
                nx = w.nx, nz = w.nz, width = w.width, height = w.height,
                // Same convention as sample points without a barometer: ~3 m storeys
                // relative to the AR origin (the phone's start height).
                floorIndex = Math.round(w.center.y / 3f),
                floorId = floorId,
            )
        })

    /** A map's walls, each with the floor it was captured on. */
    suspend fun getWalls(sessionId: Long): List<Pair<WallObservation, Long?>> =
        dao.getWalls(sessionId).map { e ->
            WallObservation(Vector3(e.cx, e.cy, e.cz), e.nx, e.nz, e.width, e.height) to e.floorId
        }

    // --- Floors ----------------------------------------------------------------

    fun observeFloors(sessionId: Long): Flow<List<FloorInfo>> =
        dao.observeFloors(sessionId).map { fs -> fs.map { FloorInfo(it.id, it.label, it.orderIndex) } }

    suspend fun getFloors(sessionId: Long): List<FloorInfo> =
        dao.getFloors(sessionId).map { FloorInfo(it.id, it.label, it.orderIndex) }

    fun observeFloorPointCounts(sessionId: Long): Flow<Map<Long, Int>> =
        dao.observeFloorPointCounts(sessionId).map { rows ->
            rows.filter { it.floorId != null }.associate { it.floorId!! to it.points }
        }

    /** Add a floor above (or below) the existing ones. Returns its id. */
    suspend fun addFloor(sessionId: Long, label: String, above: Boolean): Long {
        val order = FloorLayout.newOrderIndex(getFloors(sessionId), above)
        return dao.insertFloor(
            FloorEntity(sessionId = sessionId, label = label, orderIndex = order, createdAt = System.currentTimeMillis())
        )
    }

    suspend fun renameFloor(floorId: Long, label: String) = dao.renameFloor(floorId, label)

    /** Move a floor one step up (+1) or down (−1); orders are renumbered 0..n−1. */
    suspend fun moveFloor(sessionId: Long, floorId: Long, step: Int) =
        dao.reorderFloors(FloorLayout.move(getFloors(sessionId), floorId, step))

    suspend fun deleteFloor(floorId: Long) = dao.deleteFloor(floorId)

    companion object {
        fun from(context: Context) = MeasurementRepository(WifiMapperDatabase.get(context).dao())
    }
}
