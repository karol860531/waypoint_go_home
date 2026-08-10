package com.waypoint.gohome.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_WAYPOINTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                sequence INTEGER NOT NULL,
                isStart INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_TRACK_POINTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WAYPOINTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TRACK_POINTS")
        onCreate(db)
    }

    fun insertWaypoint(latitude: Double, longitude: Double, sequence: Int, isStart: Boolean, timestamp: Long): Long {
        val values = ContentValues().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("sequence", sequence)
            put("isStart", if (isStart) 1 else 0)
            put("timestamp", timestamp)
        }
        return writableDatabase.insert(TABLE_WAYPOINTS, null, values)
    }

    fun insertTrackPoint(latitude: Double, longitude: Double, timestamp: Long): Long {
        val values = ContentValues().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("timestamp", timestamp)
        }
        return writableDatabase.insert(TABLE_TRACK_POINTS, null, values)
    }

    fun queryWaypoints(): List<Waypoint> {
        val result = mutableListOf<Waypoint>()
        readableDatabase.rawQuery("SELECT id, latitude, longitude, sequence, isStart, timestamp FROM $TABLE_WAYPOINTS ORDER BY sequence ASC", null)
            .use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(
                        Waypoint(
                            id = cursor.getLong(0),
                            latitude = cursor.getDouble(1),
                            longitude = cursor.getDouble(2),
                            sequence = cursor.getInt(3),
                            isStart = cursor.getInt(4) != 0,
                            timestamp = cursor.getLong(5)
                        )
                    )
                }
            }
        return result
    }

    fun queryTrackPoints(): List<TrackPoint> {
        val result = mutableListOf<TrackPoint>()
        readableDatabase.rawQuery("SELECT id, latitude, longitude, timestamp FROM $TABLE_TRACK_POINTS ORDER BY timestamp ASC", null)
            .use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(
                        TrackPoint(
                            id = cursor.getLong(0),
                            latitude = cursor.getDouble(1),
                            longitude = cursor.getDouble(2),
                            timestamp = cursor.getLong(3)
                        )
                    )
                }
            }
        return result
    }

    fun clearAll() {
        writableDatabase.delete(TABLE_WAYPOINTS, null, null)
        writableDatabase.delete(TABLE_TRACK_POINTS, null, null)
    }

    companion object {
        private const val DB_NAME = "waypoint_go_home.db"
        private const val DB_VERSION = 1
        private const val TABLE_WAYPOINTS = "waypoints"
        private const val TABLE_TRACK_POINTS = "track_points"
    }
}
