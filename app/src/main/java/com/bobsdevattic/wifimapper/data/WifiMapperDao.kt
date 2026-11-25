package com.bobsdevattic.wifimapper.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WifiMapperDao {

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Query("UPDATE sessions SET anchorLat=:lat, anchorLon=:lon, anchorAltitude=:alt, anchorAccuracy=:acc, anchorHeading=:heading WHERE id=:sessionId")
    suspend fun updateAnchor(sessionId: Long, lat: Double, lon: Double, alt: Double, acc: Float, heading: Float?)

    @Query("UPDATE sessions SET baselinePressureHpa=:pressure WHERE id=:sessionId")
    suspend fun updateBaseline(sessionId: Long, pressure: Float)

    @Insert
    suspend fun insertPoint(point: SamplePointEntity): Long

    @Insert
    suspend fun insertReadings(readings: List<ApReadingEntity>)

    /** Insert a fused sample (point + its readings) atomically. */
    @Transaction
    suspend fun insertSample(point: SamplePointEntity, readings: (Long) -> List<ApReadingEntity>): Long {
        val pointId = insertPoint(point)
        insertReadings(readings(pointId))
        return pointId
    }

    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id=:sessionId")
    suspend fun getSession(sessionId: Long): SessionEntity?

    @Transaction
    @Query("SELECT * FROM sample_points WHERE sessionId=:sessionId ORDER BY timestamp")
    fun observePoints(sessionId: Long): Flow<List<SamplePointWithReadings>>

    @Transaction
    @Query("SELECT * FROM sample_points WHERE sessionId=:sessionId ORDER BY timestamp")
    suspend fun getPoints(sessionId: Long): List<SamplePointWithReadings>

    @Query("SELECT COUNT(*) FROM sample_points WHERE sessionId=:sessionId")
    fun observePointCount(sessionId: Long): Flow<Int>

    @Query("DELETE FROM sessions WHERE id=:sessionId")
    suspend fun deleteSession(sessionId: Long)

    // --- Walls ---------------------------------------------------------------

    @Insert
    suspend fun insertWalls(walls: List<WallEntity>)

    @Query("DELETE FROM walls WHERE floorId=:floorId")
    suspend fun deleteWallsForFloor(floorId: Long)

    /** Replace one floor's walls with the latest ARCore snapshot, atomically. */
    @Transaction
    suspend fun replaceWalls(floorId: Long, walls: List<WallEntity>) {
        deleteWallsForFloor(floorId)
        insertWalls(walls)
    }

    @Query("SELECT * FROM walls WHERE sessionId=:sessionId")
    suspend fun getWalls(sessionId: Long): List<WallEntity>

    // --- Floors --------------------------------------------------------------

    @Insert
    suspend fun insertFloor(floor: FloorEntity): Long

    @Query("SELECT * FROM floors WHERE sessionId=:sessionId ORDER BY orderIndex, id")
    suspend fun getFloors(sessionId: Long): List<FloorEntity>

    @Query("SELECT * FROM floors WHERE sessionId=:sessionId ORDER BY orderIndex, id")
    fun observeFloors(sessionId: Long): Flow<List<FloorEntity>>

    @Query("UPDATE floors SET label=:label WHERE id=:floorId")
    suspend fun renameFloor(floorId: Long, label: String)

    @Query("UPDATE floors SET orderIndex=:orderIndex WHERE id=:floorId")
    suspend fun setFloorOrder(floorId: Long, orderIndex: Int)

    /** Apply a whole new ordering at once (floorId → orderIndex). */
    @Transaction
    suspend fun reorderFloors(order: Map<Long, Int>) {
        order.forEach { (id, idx) -> setFloorOrder(id, idx) }
    }

    @Query("SELECT floorId, COUNT(*) AS points FROM sample_points WHERE sessionId=:sessionId GROUP BY floorId")
    fun observeFloorPointCounts(sessionId: Long): Flow<List<FloorPointCount>>

    @Query("DELETE FROM sample_points WHERE floorId=:floorId")
    suspend fun deletePointsForFloor(floorId: Long)

    @Query("DELETE FROM floors WHERE id=:floorId")
    suspend fun deleteFloorRow(floorId: Long)

    /** Delete a floor and everything captured on it (AP readings cascade from points). */
    @Transaction
    suspend fun deleteFloor(floorId: Long) {
        deletePointsForFloor(floorId)
        deleteWallsForFloor(floorId)
        deleteFloorRow(floorId)
    }
}

/** Row type for [WifiMapperDao.observeFloorPointCounts]. */
data class FloorPointCount(val floorId: Long?, val points: Int)
