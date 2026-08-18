package com.waypoint.gohome.about

import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.waypoint.gohome.R
import com.waypoint.gohome.databinding.ActivityChangelogBinding

class ChangelogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChangelogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangelogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.currentVersionText.text = getString(R.string.label_installed_version, installedVersionName())
        buildChangelog()
    }

    private fun installedVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (_: PackageManager.NameNotFoundException) {
        "?"
    }

    private fun buildChangelog() {
        val density = resources.displayMetrics.density
        val cardMargin = (12 * density).toInt()
        val cardPadding = (16 * density).toInt()

        Changelog.entries.forEach { entry ->
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(cardMargin, cardMargin / 2, cardMargin, cardMargin / 2)
                }
                radius = 16 * density
                cardElevation = density
                strokeWidth = 0
                setCardBackgroundColor(color(R.color.surface))
            }

            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(cardPadding, cardPadding, cardPadding, cardPadding)
            }

            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            headerRow.addView(
                TextView(this).apply {
                    text = "v${entry.version}"
                    textSize = 17f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(color(R.color.on_surface))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            headerRow.addView(
                TextView(this).apply {
                    text = entry.date
                    textSize = 13f
                    setTextColor(color(R.color.on_surface_variant))
                }
            )
            content.addView(headerRow)

            entry.changes.forEach { change ->
                content.addView(
                    TextView(this).apply {
                        text = "•  $change"
                        textSize = 14f
                        setTextColor(color(R.color.on_surface_variant))
                        setPadding(0, (6 * density).toInt(), 0, 0)
                    }
                )
            }

            card.addView(content)
            binding.changelogContainer.addView(card)
        }
    }

    private fun color(colorRes: Int) = ContextCompat.getColor(this, colorRes)

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
