package com.example.starfieldwallpaper

import kotlin.math.cos
import kotlin.math.sin

class Star {
    var x: Float = 0f
    var y: Float = 0f
    var z: Float = 0f
    var pz: Float = 0f
    var speed: Float = 0f
    var color: Int = 0xFFFFFFFF.toInt()

    init {
        reset()
    }

    fun reset() {
        x = (Math.random() * 2000 - 1000) / 1000f
        y = (Math.random() * 2000 - 1000) / 1000f
        z = (Math.random() * 1000) / 1000f
        pz = z
        speed = (Math.random() * 0.002 + 0.001).toFloat()
        
        // Random color with a slight blue tint for space
        val r = (Math.random() * 128 + 127).toInt()
        val g = (Math.random() * 128 + 127).toInt()
        val b = 255  // Blue tint
        color = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    fun update() {
        z -= speed
        if (z <= 0) {
            reset()
            z = 1f
            pz = 1f
        }
    }

    fun getScreenX(width: Int, height: Int): Float {
        val scale = width.toFloat() / height
        return (x / z * 1000 * scale + width / 2f).coerceIn(0f, width.toFloat())
    }

    fun getScreenY(width: Int, height: Int): Float {
        return (y / z * 1000 + height / 2f).coerceIn(0f, height.toFloat())
    }

    fun getPreviousScreenX(width: Int, height: Int): Float {
        val scale = width.toFloat() / height
        return (x / pz * 1000 * scale + width / 2f).coerceIn(0f, width.toFloat())
    }

    fun getPreviousScreenY(width: Int, height: Int): Float {
        return (y / pz * 1000 + height / 2f).coerceIn(0f, height.toFloat())
    }
}
