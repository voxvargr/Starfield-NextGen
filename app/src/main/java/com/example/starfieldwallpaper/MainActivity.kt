package com.example.starfieldwallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.starfieldwallpaper.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.setWallpaperButton.setOnClickListener {
            val intent = Intent(
                WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER
            ).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, StarfieldWallpaperService::class.java)
                )
            }
            startActivity(intent)
        }
    }
}
