package com.sundek.starfieldnextgen.settings

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToInt

data class StarfieldConfig(
    val starCount: Int,
    val targetFps: Int,
    val flightSpeed: Float,
    val systemChance: Float,
    val planetBrightness: Float,
    val planetMinBrightness: Float,
    val planetMaxBrightness: Float,
    val binaryChance: Float,
    val starDirectionDrift: Float,
    val galacticPlaneStrength: Float,
    val clusterAmount: Float,
    val dustAmount: Float,
    val nebulaCount: Int,
    val blackHoleChance: Float,
    val nebulaBrightness: Float,
    val nebulaMotion: Float,
    val starMinBrightness: Float,
    val starMaxBrightness: Float,
    val trailsEnabled: Boolean,
    val orbitLinesEnabled: Boolean,
    val glowEnabled: Boolean,
    val nebulasEnabled: Boolean,
    val blackHolesEnabled: Boolean
)

object StarfieldPrefs {
    const val PREFS_NAME = "starfield_nextgen_settings"

    const val KEY_STAR_COUNT = "star_count"
    const val KEY_TARGET_FPS = "target_fps"
    const val KEY_FLIGHT_SPEED = "flight_speed_percent"
    const val KEY_SYSTEM_CHANCE = "system_chance_percent"
    const val KEY_PLANET_BRIGHTNESS = "planet_brightness_percent" // Legacy single-slider key; kept so older installs do not break.
    const val KEY_PLANET_MIN_BRIGHTNESS = "planet_min_brightness_percent"
    const val KEY_PLANET_MAX_BRIGHTNESS = "planet_max_brightness_percent"
    const val KEY_BINARY_CHANCE = "binary_chance_percent"
    const val KEY_STAR_DIRECTION_DRIFT = "star_direction_drift_percent"
    const val KEY_GALACTIC_PLANE = "galactic_plane_percent"
    const val KEY_STAR_CLUSTERS = "star_cluster_percent"
    const val KEY_INTERSTELLAR_DUST = "interstellar_dust_percent"
    const val KEY_NEBULA_COUNT = "nebula_count"
    const val KEY_BLACK_HOLE_CHANCE = "black_hole_chance_percent"
    const val KEY_NEBULA_BRIGHTNESS = "nebula_brightness_percent"
    const val KEY_NEBULA_MOTION = "nebula_motion_percent"
    const val KEY_STAR_MIN_BRIGHTNESS = "star_min_brightness_percent"
    const val KEY_STAR_MAX_BRIGHTNESS = "star_max_brightness_percent"
    const val KEY_TRAILS = "trails_enabled"
    const val KEY_ORBIT_LINES = "orbit_lines_enabled"
    const val KEY_GLOW = "glow_enabled"
    const val KEY_NEBULAS = "nebulas_enabled"
    const val KEY_BLACK_HOLES = "black_holes_enabled"

    const val MIN_STAR_COUNT = 120
    const val MAX_STAR_COUNT = 12000

    const val DEFAULT_STAR_COUNT = 4200
    const val DEFAULT_TARGET_FPS = 60
    const val DEFAULT_FLIGHT_SPEED_PERCENT = 50
    const val DEFAULT_SYSTEM_CHANCE_PERCENT = 8
    const val DEFAULT_PLANET_BRIGHTNESS_PERCENT = 64
    const val DEFAULT_PLANET_MIN_BRIGHTNESS_PERCENT = 8
    const val DEFAULT_PLANET_MAX_BRIGHTNESS_PERCENT = 135
    const val DEFAULT_BINARY_CHANCE_PERCENT = 6
    const val DEFAULT_STAR_DIRECTION_DRIFT_PERCENT = 16
    const val DEFAULT_GALACTIC_PLANE_PERCENT = 28
    const val DEFAULT_STAR_CLUSTER_PERCENT = 18
    const val DEFAULT_INTERSTELLAR_DUST_PERCENT = 22
    const val DEFAULT_NEBULA_COUNT = 6
    const val DEFAULT_BLACK_HOLE_CHANCE_PERCENT = 12
    const val DEFAULT_NEBULA_BRIGHTNESS_PERCENT = 84
    const val DEFAULT_NEBULA_MOTION_PERCENT = 72
    const val DEFAULT_STAR_MIN_BRIGHTNESS_PERCENT = 22
    const val DEFAULT_STAR_MAX_BRIGHTNESS_PERCENT = 135
    const val DEFAULT_TRAILS = true
    const val DEFAULT_ORBIT_LINES = false
    const val DEFAULT_GLOW = true
    const val DEFAULT_NEBULAS = true
    const val DEFAULT_BLACK_HOLES = true

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(context: Context): StarfieldConfig {
        val sp = prefs(context)
        val speedPercent = sp.getInt(KEY_FLIGHT_SPEED, DEFAULT_FLIGHT_SPEED_PERCENT).coerceIn(1, 100)
        val legacyPlanetBrightnessPercent = sp.getInt(KEY_PLANET_BRIGHTNESS, DEFAULT_PLANET_BRIGHTNESS_PERCENT).coerceIn(1, 100)
        if ((sp.contains(KEY_PLANET_BRIGHTNESS)) && !sp.contains(KEY_PLANET_MIN_BRIGHTNESS) && !sp.contains(KEY_PLANET_MAX_BRIGHTNESS)) {
            sp.edit()
                .putInt(KEY_PLANET_MIN_BRIGHTNESS, (legacyPlanetBrightnessPercent * 0.16f).roundToInt().coerceIn(0, 100))
                .putInt(KEY_PLANET_MAX_BRIGHTNESS, (legacyPlanetBrightnessPercent * 1.55f).roundToInt().coerceIn(10, 200))
                .apply()
        }
        val planetMinBrightnessPercent = sp.getInt(KEY_PLANET_MIN_BRIGHTNESS, DEFAULT_PLANET_MIN_BRIGHTNESS_PERCENT).coerceIn(0, 100)
        val planetMaxBrightnessPercent = sp.getInt(KEY_PLANET_MAX_BRIGHTNESS, DEFAULT_PLANET_MAX_BRIGHTNESS_PERCENT).coerceIn(10, 200)
        val chancePercent = sp.getInt(KEY_SYSTEM_CHANCE, DEFAULT_SYSTEM_CHANCE_PERCENT).coerceIn(0, 35)
        val binaryChancePercent = sp.getInt(KEY_BINARY_CHANCE, DEFAULT_BINARY_CHANCE_PERCENT).coerceIn(0, 50)
        val starDirectionDriftPercent = sp.getInt(KEY_STAR_DIRECTION_DRIFT, DEFAULT_STAR_DIRECTION_DRIFT_PERCENT).coerceIn(0, 100)
        val galacticPlanePercent = sp.getInt(KEY_GALACTIC_PLANE, DEFAULT_GALACTIC_PLANE_PERCENT).coerceIn(0, 100)
        val clusterPercent = sp.getInt(KEY_STAR_CLUSTERS, DEFAULT_STAR_CLUSTER_PERCENT).coerceIn(0, 100)
        val interstellarDustPercent = sp.getInt(KEY_INTERSTELLAR_DUST, DEFAULT_INTERSTELLAR_DUST_PERCENT).coerceIn(0, 100)
        val nebulaBrightnessPercent = sp.getInt(KEY_NEBULA_BRIGHTNESS, DEFAULT_NEBULA_BRIGHTNESS_PERCENT).coerceIn(1, 100)
        val blackHoleChancePercent = sp.getInt(KEY_BLACK_HOLE_CHANCE, DEFAULT_BLACK_HOLE_CHANCE_PERCENT).coerceIn(0, 100)
        val nebulaMotionPercent = sp.getInt(KEY_NEBULA_MOTION, DEFAULT_NEBULA_MOTION_PERCENT).coerceIn(0, 100)
        val starMinBrightnessPercent = sp.getInt(KEY_STAR_MIN_BRIGHTNESS, DEFAULT_STAR_MIN_BRIGHTNESS_PERCENT).coerceIn(0, 100)
        val starMaxBrightnessPercent = sp.getInt(KEY_STAR_MAX_BRIGHTNESS, DEFAULT_STAR_MAX_BRIGHTNESS_PERCENT).coerceIn(20, 220)
        val nebulasEnabled = sp.getBoolean(KEY_NEBULAS, DEFAULT_NEBULAS)
        val blackHolesEnabled = sp.getBoolean(KEY_BLACK_HOLES, DEFAULT_BLACK_HOLES)
        var starCountPref = sp.getInt(KEY_STAR_COUNT, DEFAULT_STAR_COUNT)
        if (starCountPref in 8200 until MAX_STAR_COUNT) {
            starCountPref = MAX_STAR_COUNT
            sp.edit().putInt(KEY_STAR_COUNT, starCountPref).apply()
        }

        return StarfieldConfig(
            starCount = starCountPref.coerceIn(MIN_STAR_COUNT, MAX_STAR_COUNT),
            targetFps = validFps(sp.getInt(KEY_TARGET_FPS, DEFAULT_TARGET_FPS)),
            flightSpeed = lerp(0.06f, 0.58f, speedPercent / 100f),
            systemChance = chancePercent / 100f,
            planetBrightness = planetMaxBrightnessPercent / 100f,
            planetMinBrightness = planetMinBrightnessPercent / 100f,
            planetMaxBrightness = planetMaxBrightnessPercent / 100f,
            binaryChance = binaryChancePercent / 100f,
            starDirectionDrift = starDirectionDriftPercent / 100f,
            galacticPlaneStrength = galacticPlanePercent / 100f,
            clusterAmount = clusterPercent / 100f,
            dustAmount = interstellarDustPercent / 100f,
            nebulaCount = if (nebulasEnabled) sp.getInt(KEY_NEBULA_COUNT, DEFAULT_NEBULA_COUNT).coerceIn(0, 20) else 0,
            blackHoleChance = blackHoleChancePercent / 100f,
            nebulaBrightness = lerp(0.12f, 3.25f, nebulaBrightnessPercent / 100f),
            nebulaMotion = nebulaMotionPercent / 100f,
            starMinBrightness = starMinBrightnessPercent / 100f,
            starMaxBrightness = starMaxBrightnessPercent / 100f,
            trailsEnabled = sp.getBoolean(KEY_TRAILS, DEFAULT_TRAILS),
            orbitLinesEnabled = sp.getBoolean(KEY_ORBIT_LINES, DEFAULT_ORBIT_LINES),
            glowEnabled = sp.getBoolean(KEY_GLOW, DEFAULT_GLOW),
            nebulasEnabled = nebulasEnabled,
            blackHolesEnabled = blackHolesEnabled
        )
    }

    fun validFps(value: Int): Int = when (value) {
        30, 60, 90, 120 -> value
        else -> DEFAULT_TARGET_FPS
    }

    fun speedPercentFromConfig(config: StarfieldConfig): Int {
        return (((config.flightSpeed - 0.06f) / (0.58f - 0.06f)) * 100f).roundToInt().coerceIn(1, 100)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
}
