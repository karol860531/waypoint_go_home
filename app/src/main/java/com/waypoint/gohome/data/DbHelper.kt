package com.waypoint.gohome.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_TRIPS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_WAYPOINTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tripId INTEGER NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                altitude REAL,
                sequence INTEGER NOT NULL,
                isStart INTEGER NOT NULL,
                isEnd INTEGER NOT NULL DEFAULT 0,
                label TEXT,
                photoUri TEXT,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_TRACK_POINTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tripId INTEGER NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                altitude REAL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_waypoints_trip ON $TABLE_WAYPOINTS(tripId)")
        db.execSQL("CREATE INDEX idx_track_points_trip ON $TABLE_TRACK_POINTS(tripId)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WAYPOINTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TRACK_POINTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TRIPS")
        onCreate(db)
    }

    fun insertTrip(name: String, createdAt: Long): Long {
        val values = ContentValues().apply {
            put("name", name)
            put("createdAt", createdAt)
        }
        return writableDatabase.insert(TABLE_TRIPS, null, values)
    }

    fun deleteTrip(tripId: Long) {
        writableDatabase.delete(TABLE_WAYPOINTS, "tripId = ?", arrayOf(tripId.toString()))
        writableDatabase.delete(TABLE_TRACK_POINTS, "tripId = ?", arrayOf(tripId.toString()))
        writableDatabase.delete(TABLE_TRIPS, "id = ?", arrayOf(tripId.toString()))
    }

    fun queryTrips(): List<Trip> {
        val result = mutableListOf<Trip>()
        readableDatabase.rawQuery("SELECT id, name, createdAt FROM $TABLE_TRIPS ORDER BY createdAt DESC", null)
            .use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(Trip(cursor.getLong(0), cursor.getString(1), cursor.getLong(2)))
                }
            }
        return result
    }

    fun getTrip(tripId: Long): Trip? {
        readableDatabase.rawQuery("SELECT id, name, createdAt FROM $TABLE_TRIPS WHERE id = ?", arrayOf(tripId.toString())).use {
            return if (it.moveToFirst()) Trip(it.getLong(0), it.getString(1), it.getLong(2)) else null
        }
    }

    fun tripExists(tripId: Long): Boolean {
        readableDatabase.rawQuery("SELECT id FROM $TABLE_TRIPS WHERE id = ?", arrayOf(tripId.toString())).use {
            return it.moveToFirst()
        }
    }

    fun insertWaypoint(
        tripId: Long,
        latitude: Double,
        longitude: Double,
        altitude: Double?,
        sequence: Int,
        isStart: Boolean,
        isEnd: Boolean,
        label: String?,
        timestamp: Long
    ): Long {
        val values = ContentValues().apply {
            put("tripId", tripId)
            put("latitude", latitude)
            put("longitude", longitude)
            if (altitude != null) put("altitude", altitude) else putNull("altitude")
            put("sequence", sequence)
            put("isStart", if (isStart) 1 else 0)
            put("isEnd", if (isEnd) 1 else 0)
            if (label != null) put("label", label) else putNull("label")
            put("timestamp", timestamp)
        }
        return writableDatabase.insert(TABLE_WAYPOINTS, null, values)
    }

    fun deleteWaypoint(waypointId: Long) {
        writableDatabase.delete(TABLE_WAYPOINTS, "id = ?", arrayOf(waypointId.toString()))
    }

    fun updateWaypointPhoto(waypointId: Long, photoUri: String?) {
        val values = ContentValues().apply {
            if (photoUri != null) put("photoUri", photoUri) else putNull("photoUri")
        }
        writableDatabase.update(TABLE_WAYPOINTS, values, "id = ?", arrayOf(waypointId.toString()))
    }

    fun insertTrackPoint(tripId: Long, latitude: Double, longitude: Double, altitude: Double?, timestamp: Long): Long {
        val values = ContentValues().apply {
            put("tripId", tripId)
            put("latitude", latitude)
            put("longitude", longitude)
            if (altitude != null) put("altitude", altitude) else putNull("altitude")
            put("timestamp", timestamp)
        }
        return writableDatabase.insert(TABLE_TRACK_POINTS, null, values)
    }

    fun queryWaypoints(tripId: Long): List<Waypoint> {
        val result = mutableListOf<Waypoint>()
        readableDatabase.rawQuery(
            "SELECT id, tripId, latitude, longitude, altitude, sequence, isStart, isEnd, label, photoUri, timestamp FROM $TABLE_WAYPOINTS WHERE tripId = ? ORDER BY sequence ASC",
            arrayOf(tripId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    Waypoint(
                        id = cursor.getLong(0),
                        tripId = cursor.getLong(1),
                        latitude = cursor.getDouble(2),
                        longitude = cursor.getDouble(3),
                        altitude = cursor.getNullableDouble(4),
                        sequence = cursor.getInt(5),
                        isStart = cursor.getInt(6) != 0,
                        isEnd = cursor.getInt(7) != 0,
                        label = if (cursor.isNull(8)) null else cursor.getString(8),
                        photoUri = if (cursor.isNull(9)) null else cursor.getString(9),
                        timestamp = cursor.getLong(10)
                    )
                )
            }
        }
        return result
    }

    fun queryTrackPoints(tripId: Long): List<TrackPoint> {
        val result = mutableListOf<TrackPoint>()
        readableDatabase.rawQuery(
            "SELECT id, tripId, latitude, longitude, altitude, timestamp FROM $TABLE_TRACK_POINTS WHERE tripId = ? ORDER BY timestamp ASC",
            arrayOf(tripId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    TrackPoint(
                        id = cursor.getLong(0),
                        tripId = cursor.getLong(1),
                        latitude = cursor.getDouble(2),
                        longitude = cursor.getDouble(3),
                        altitude = cursor.getNullableDouble(4),
                        timestamp = cursor.getLong(5)
                    )
                )
            }
        }
        return result
    }

    fun countTrackPoints(tripId: Long): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_TRACK_POINTS WHERE tripId = ?", arrayOf(tripId.toString())).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun Cursor.getNullableDouble(columnIndex: Int): Double? =
        if (isNull(columnIndex)) null else getDouble(columnIndex)

    companion object {
        private const val DB_NAME = "waypoint_go_home.db"
        private const val DB_VERSION = 5
        private const val TABLE_TRIPS = "trips"
        private const val TABLE_WAYPOINTS = "waypoints"
        private const val TABLE_TRACK_POINTS = "track_points"
    }
}
