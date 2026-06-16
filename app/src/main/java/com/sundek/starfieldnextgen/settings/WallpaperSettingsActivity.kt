package com.sundek.starfieldnextgen.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.sundek.starfieldnextgen.MainActivity

class WallpaperSettingsActivity : Activity() {
    private val prefs by lazy { StarfieldPrefs.prefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Starfield Settings"
        StarfieldPrefs.read(this) // Applies small settings migrations before the controls are drawn.

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(5, 7, 13))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 38, 42, 42)
        }

        root.addView(header("Starfield Settings"))
        root.addView(body("This OpenGL build uses the GPU for high-density rendering, more varied star types, rare close solar-system and binary-star flybys, star clusters, soft noise-textured nebula flybys, subtle non-perfect star motion, a galactic-plane density band, interstellar dust, and optional black-hole events with light warping."))

        root.addView(sectionLabel("Performance"))
        root.addView(fpsSelector())

        root.addView(sectionLabel("Space density"))
        root.addView(intSlider(
            label = "Star density",
            key = StarfieldPrefs.KEY_STAR_COUNT,
            min = StarfieldPrefs.MIN_STAR_COUNT,
            max = StarfieldPrefs.MAX_STAR_COUNT,
            defaultValue = StarfieldPrefs.DEFAULT_STAR_COUNT,
            valueSuffix = " stars"
        ))
        root.addView(intSlider(
            label = "Galactic plane density band",
            key = StarfieldPrefs.KEY_GALACTIC_PLANE,
            min = 0,
            max = 100,
            defaultValue = StarfieldPrefs.DEFAULT_GALACTIC_PLANE_PERCENT,
            valueSuffix = "%"
        ))
        root.addView(intSlider(
            label = "Interstellar dust amount",
            key = StarfieldPrefs.KEY_INTERSTELLAR_DUST,
            min = 0,
            max = 100,
            defaultValue = StarfieldPrefs.DEFAULT_INTERSTELLAR_DUST_PERCENT,
            valueSuffix = "%"
        ))
        root.addView(intSlider(
            label = "Star cluster amount",
            key = StarfieldPrefs.KEY_STAR_CLUSTERS,
            min = 0,
            max = 100,
            defaultValue = StarfieldPrefs.DEFAULT_STAR_CLUSTER_PERCENT,
            valueSuffix = "%"
        ))

        root.addView(sectionLabel("Star brightness"))
        root.addView(intSlider(
            label = "Minimum star brightness",
            key = StarfieldPrefs.KEY_STAR_MIN_BRIGHTNESS,
            min = 0,
            max = 100,
            defaultValue = StarfieldPrefs.DEFAULT_STAR_MIN_BRIGHTNESS_PERCENT,
            valueSuffix = "%"
        ))
        root.addView(intSlider(
            label = "Maximum star brightness",
            key = StarfieldPrefs.KEY_STAR_MAX_BRIGHTNESS,
            min = 20,
            max = 220,
            defaultValue = StarfieldPrefs.DEFAULT_STAR_MAX_BRIGHTNESS_PERCENT,
            valueSuffix = "%"
        ))

        root.addView(sectionLabel("Flight feel"))
        root.addView(intSlider(
            label = "Flight speed",
            key = StarfieldPrefs.KEY_FLIGHT_SPEED,
            min = 1,
            max = 100,
            defaultValue = StarfieldPrefs.DEFAULT_FLIGHT_SPEED_PERCENT,
            valueSuffix = "%"
        ))
        root.addView(intSlider(
            label = "Natural star direction drift",
            key = StarfieldPrefs.KEY_STAR_DIRECTION_DRIFT,
            min = 0,
            max = 100,
            defaultValue = StarfieldPrefs.DEFAULT_STAR_DIRECTION_DRIFT_PERCENT,
            valueSuffix = "%"
        ))

        root.addView(sectionLabel("Solar-system flybys"))
        root.addView(intSlider(
            label = "Close solar-system frequency",
            key = StarfieldPrefs.KEY_SYSTEM_CHANCE,
            min = 0,
            max = 35,
            defaultValue = StarfieldPrefs.DEFAULT_SYSTEM_CHANCE_PERCENT,
            valueSuffix = "%"
        ))
        root.addView(intSlider(
            label = "Binary-star flyby chance",
            key = StarfieldPrefs.KEY_BINARY_CHANCE,
            min = 0,
            max = 50,
            defaultValue = StarfieldPrefs.DEFAULT_BINARY_CHANCE_PERCENT,
            valueSuffix = "%"
        ))
        root.addView(intSlider(
            label = "Minimum planet brightness",
            key = StarfieldPrefs.KEY_PLANET_MIN_BRIGHTNESS,
            min = 0,
            max = 100,
            defaultValue = StarfieldPrefs.DEFAULT_PLANET_MIN_BRIGHTNESS_PERCENT,
            valueSuffix = "%"
        ))
        root.addView(intSlider(
            label = "Maximum planet brightness",
            key = StarfieldPrefs.KEY_PLANET_MAX_BRIGHTNESS,
            min = 10,
            max = 200,
            defaultValue = StarfieldPrefs.DEFAULT_PLANET_MAX_BRIGHTNESS_PERCENT,
            valueSuffix = "%"
        ))

        root.addView(sectionLabel("Rare events"))
        root.addView(checkBox("Occasional black holes", StarfieldPrefs.KEY_BLACK_HOLES, StarfieldPrefs.DEFAULT_BLACK_HOLES))
        root.addView(intSlider(
            label = "Black-hole event chance",
            key = StarfieldPrefs.KEY_BLACK_HOLE_CHANCE,
            min = 0,
            max = 100,
            defaultValue = StarfieldPrefs.DEFAULT_BLACK_HOLE_CHANCE_PERCENT,
            valueSuffix = "%"
        ))

        root.addView(sectionLabel("Nebula flybys"))
        root.addView(checkBox("Random nebula clouds", StarfieldPrefs.KEY_NEBULAS, StarfieldPrefs.DEFAULT_NEBULAS))
        root.addView(intSlider(
            label = "Nebula amount",
            key = StarfieldPrefs.KEY_NEBULA_COUNT,
            min = 0,
            max = 20,
            defaultValue = StarfieldPrefs.DEFAULT_NEBULA_COUNT,
            valueSuffix = " clouds"
        ))
        root.addView(intSlider(
            label = "Nebula brightness",
            key = StarfieldPrefs.KEY_NEBULA_BRIGHTNESS,
            min = 1,
            max = 100,
            defaultValue = StarfieldPrefs.DEFAULT_NEBULA_BRIGHTNESS_PERCENT,
            valueSuffix = "%"
        ))
        root.addView(intSlider(
            label = "Nebula flyby motion",
            key = StarfieldPrefs.KEY_NEBULA_MOTION,
            min = 0,
            max = 100,
            defaultValue = StarfieldPrefs.DEFAULT_NEBULA_MOTION_PERCENT,
            valueSuffix = "%"
        ))

        root.addView(sectionLabel("Visual details"))
        root.addView(checkBox("Motion trails", StarfieldPrefs.KEY_TRAILS, StarfieldPrefs.DEFAULT_TRAILS))
        root.addView(checkBox("Faint orbit guide lines", StarfieldPrefs.KEY_ORBIT_LINES, StarfieldPrefs.DEFAULT_ORBIT_LINES))
        root.addView(checkBox("Soft star glow", StarfieldPrefs.KEY_GLOW, StarfieldPrefs.DEFAULT_GLOW))

        val previewButton = Button(this).apply {
            text = "Back to preview launcher"
            setOnClickListener {
                startActivity(Intent(this@WallpaperSettingsActivity, MainActivity::class.java))
            }
        }
        root.addView(previewButton, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun header(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 28f
        setTextColor(Color.WHITE)
        gravity = Gravity.START
        setPadding(0, 0, 0, 18)
    }

    private fun body(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.rgb(190, 205, 235))
        setPadding(0, 0, 0, 28)
    }

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(Color.rgb(220, 230, 255))
        setPadding(0, 26, 0, 6)
    }

    private fun fpsSelector(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 18)
        }

        val title = TextView(this).apply {
            text = "FPS limit"
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 6)
        }
        box.addView(title)

        val currentFps = StarfieldPrefs.validFps(prefs.getInt(StarfieldPrefs.KEY_TARGET_FPS, StarfieldPrefs.DEFAULT_TARGET_FPS))
        val group = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }

        addFpsOption(group, 30, "30 FPS - battery saving", currentFps)
        addFpsOption(group, 60, "60 FPS - normal", currentFps)
        addFpsOption(group, 90, "90 FPS - smooth", currentFps)
        addFpsOption(group, 120, "120 FPS - extreme", currentFps)

        group.setOnCheckedChangeListener { _, checkedId ->
            val fps = when (checkedId) {
                30 -> 30
                90 -> 90
                120 -> 120
                else -> 60
            }
            prefs.edit().putInt(StarfieldPrefs.KEY_TARGET_FPS, fps).commit()
        }

        box.addView(group)
        return box
    }

    private fun addFpsOption(group: RadioGroup, fps: Int, label: String, currentFps: Int) {
        val button = RadioButton(this).apply {
            id = fps
            text = label
            textSize = 15f
            setTextColor(Color.WHITE)
            isChecked = fps == currentFps
            setPadding(0, 4, 0, 4)
        }
        group.addView(button)
    }

    private fun intSlider(
        label: String,
        key: String,
        min: Int,
        max: Int,
        defaultValue: Int,
        valueSuffix: String
    ): LinearLayout {
        val current = prefs.getInt(key, defaultValue).coerceIn(min, max)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 18)
        }

        val labelView = TextView(this).apply {
            text = "$label: $current$valueSuffix"
            textSize = 15f
            setTextColor(Color.WHITE)
        }

        val seekBar = SeekBar(this).apply {
            this.max = max - min
            progress = current - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = min + progress
                    labelView.text = "$label: $value$valueSuffix"
                    if (fromUser) prefs.edit().putInt(key, value).commit()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }

        box.addView(labelView)
        box.addView(seekBar)
        return box
    }

    private fun checkBox(label: String, key: String, defaultValue: Boolean): CheckBox {
        return CheckBox(this).apply {
            text = label
            textSize = 15f
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean(key, defaultValue)
            setPadding(0, 8, 0, 8)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(key, isChecked).commit()
            }
        }
    }
}
