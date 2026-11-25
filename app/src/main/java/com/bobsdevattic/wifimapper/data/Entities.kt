package com.bobsdevattic.wifimapper.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.Embedded

/**
 * One mapping run (e.g. "Ground + first floor, Nov 25"). Holds the GPS geo-anchor
 * captured at the AR session origin so the local point cloud can be georeferenced.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    // Geo-anchor (nullable: a session may start before a GPS fix arrives).
    val anchorLat: Double?,
    val anchorLon: Double?,
    val anchorAltitude: Double?,
    val anchorAccuracy: Float?,
    val anchorHeading: Float?,
    // Barometric pressure (hPa) at the floor where mapping began, for relative
    // altitude. Null when the device has no barometer.
    val baselinePressureHpa: Float?,
)

/**
 * A single location in space where we took a WiFi snapshot. Position is in the
 * AR-local frame; [floorIndex] is derived from barometric altitude (0 = baseline
 * floor, +1 up a storey, etc.).
 */
@Entity(
    tableName = "sample_points",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class SamplePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    // AR-local metres.
    val x: Float,
    val y: Float,
    val z: Float,
    // True if ARCore was tracking at capture time; false => position is a
    // dead-reckoned / manual estimate and should be shown as low-confidence.
    val arTracked: Boolean,
    // Barometer-derived.
    val baroAltitudeMeters: Float?,
    val floorIndex: Int,
    // Convenience summary so the viewer can colour points without joining:
    // RSSI (dBm) of the strongest AP seen here, and how many APs were visible.
    val bestRssi: Int,
    val apCount: Int,
)

/**
 * One access point observed at a [SamplePointEntity]. A point typically has many.
 */
@Entity(
    tableName = "ap_readings",
    foreignKeys = [ForeignKey(
        entity = SamplePointEntity::class,
        parentColumns = ["id"],
        childColumns = ["pointId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("pointId"), Index("bssid")],
)
data class ApReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pointId: Long,
    val bssid: String,
    val ssid: String,
    val rssi: Int,          // dBm, typically -30 (excellent) .. -90 (unusable)
    val frequencyMhz: Int,  // 2412.. (2.4 GHz) or 5xxx / 6xxx
)

/** A sample point with its AP readings eagerly loaded. */
data class SamplePointWithReadings(
    @Embedded val point: SamplePointEntity,
    @Relation(parentColumn = "id", entityColumn = "pointId")
    val readings: List<ApReadingEntity>,
)

/**
 * A wall-like vertical surface detected by ARCore during a session (AR-local
 * frame). Signal loss is NOT stored: it's derived in the viewer from the
 * session's measurements, so it improves if the analysis improves.
 */
@Entity(
    tableName = "walls",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class WallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val cx: Float,
    val cy: Float,
    val cz: Float,
    val nx: Float,
    val nz: Float,
    val width: Float,
    val height: Float,
    val floorIndex: Int,
)
