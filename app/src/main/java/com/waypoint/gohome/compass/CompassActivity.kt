package com.waypoint.gohome.compass

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.waypoint.gohome.R
import com.waypoint.gohome.databinding.ActivityCompassBinding
import com.waypoint.gohome.location.CompassSensor
import com.waypoint.gohome.sun.SunCalculator

class CompassActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompassBinding
    private lateinit var compass: CompassSensor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompassBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        compass = CompassSensor(this) { heading ->
            binding.compassView.heading = heading
            val bearing = ((heading % 360) + 360) % 360
            binding.headingText.text = getString(R.string.label_heading_degrees, bearing.toInt())
            binding.cardinalText.text = SunCalculator.bearingToCardinal(bearing.toDouble())
        }
    }

    override fun onResume() {
        super.onResume()
        compass.start()
    }

    override fun onPause() {
        super.onPause()
        compass.stop()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
