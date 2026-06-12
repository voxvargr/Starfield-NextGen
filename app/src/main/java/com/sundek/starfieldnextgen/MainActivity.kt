package com.sundek.starfieldnextgen

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.sundek.starfieldnextgen.settings.WallpaperSettingsActivity
import com.sundek.starfieldnextgen.wallpaper.StarfieldWallpaperService

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.rgb(5, 7, 13))
        }

        val title = TextView(this).apply {
            text = "Starfield NextGen"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Flying starfield with close-pass solar systems and brighter nebula flybys."
            textSize = 16f
            setTextColor(Color.rgb(190, 205, 235))
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 38)
        }

        val previewButton = Button(this).apply {
            text = "Open live wallpaper preview"
            setOnClickListener { openWallpaperPreview() }
        }

        val settingsButton = Button(this).apply {
            text = "Settings"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, WallpaperSettingsActivity::class.java))
            }
        }

        root.addView(title, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(subtitle, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(previewButton, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(settingsButton, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        setContentView(root)
    }

    private fun openWallpaperPreview() {
        val component = ComponentName(this, StarfieldWallpaperService::class.java)
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Opening live wallpaper chooser instead.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }
}
