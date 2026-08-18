package com.waypoint.gohome.about

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.waypoint.gohome.R
import com.waypoint.gohome.databinding.ActivityChangelogBinding
import com.waypoint.gohome.ui.BlueprintCard

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

        Changelog.entries.forEachIndexed { index, entry ->
            val isLatest = index == 0

            val card = BlueprintCard(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(cardMargin, cardMargin / 2, cardMargin, cardMargin / 2)
                }
                elevation = density
                setBackgroundColor(color(R.color.surface))
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
                    textSize = 13f
                    setBackgroundResource(if (isLatest) R.drawable.bg_tag_accent else R.drawable.bg_tag_outline)
                    setTextColor(color(if (isLatest) R.color.primary else R.color.on_surface_variant))
                }
            )
            headerRow.addView(
                TextView(this).apply {
                    text = entry.date
                    textSize = 13f
                    setTextColor(color(R.color.on_surface_variant))
                    val marginParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    marginParams.marginStart = (8 * density).toInt()
                    layoutParams = marginParams
                }
            )
            content.addView(headerRow)

            entry.changes.forEach { change ->
                content.addView(
                    TextView(this).apply {
                        text = "—  $change"
                        textSize = 14f
                        setTextColor(color(R.color.on_surface_variant))
                        setPadding(0, (8 * density).toInt(), 0, 0)
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
