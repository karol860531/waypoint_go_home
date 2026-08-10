package com.waypoint.gohome

import android.app.Application
import org.osmdroid.config.Configuration

class WaypointApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().osmdroidTileCache = cacheDir.resolve("osmdroid")
    }
}
