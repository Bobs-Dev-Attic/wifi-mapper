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
}
