package com.sundek.starfieldnextgen.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.sundek.starfieldnextgen.settings.StarfieldConfig
import com.sundek.starfieldnextgen.settings.StarfieldPrefs
import java.util.Random
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class StarfieldRenderer(private val context: Context) {
    private val rng = Random()
    private val stars = ArrayList<Star>()
    private val nebulas = ArrayList<Nebula>()

    private var width = 1
    private var height = 1
    private var minDim = 1f
    private var config: StarfieldConfig = StarfieldPrefs.read(context)

    private var backgroundGradient: LinearGradient? = null
    private var ambientGlowGradient: RadialGradient? = null

    private val backgroundPaint = Paint().apply { isDither = true }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        isDither = true
    }
    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isDither = true
    }
    private val planetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val nebulaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
        isFilterBitmap = true
    }
    private val orbitRect = RectF()
    private val nebulaMatrix = Matrix()

    private val planetColors = intArrayOf(
        Color.rgb(94, 145, 255),
        Color.rgb(220, 142, 72),
        Color.rgb(94, 196, 150),
        Color.rgb(168, 118, 228),
        Color.rgb(216, 202, 135),
        Color.rgb(88, 196, 218),
        Color.rgb(230, 115, 124)
    )

    // Natural star colors, weighted toward the warmer/common side.
    // Bright blue-white stars exist, but they should feel rare instead of covering the field.
    private val starTints = arrayOf(
        WeightedColor(Color.rgb(255, 244, 220), 22),
        WeightedColor(Color.rgb(255, 236, 205), 20),
        WeightedColor(Color.rgb(255, 213, 166), 18),
        WeightedColor(Color.rgb(255, 188, 140), 13),
        WeightedColor(Color.rgb(255, 164, 128), 9),
        WeightedColor(Color.rgb(245, 250, 255), 16),
        WeightedColor(Color.rgb(218, 232, 255), 7),
        WeightedColor(Color.rgb(168, 198, 255), 2)
    )
    private val starTintWeightTotal = starTints.sumOf { it.weight }

    private val nebulaPalettes = arrayOf(
        intArrayOf(Color.rgb(100, 76, 255), Color.rgb(250, 82, 205)),
        intArrayOf(Color.rgb(50, 170, 240), Color.rgb(110, 255, 220)),
        intArrayOf(Color.rgb(214, 78, 158), Color.rgb(255, 150, 82)),
        intArrayOf(Color.rgb(82, 120, 255), Color.rgb(190, 214, 255)),
        intArrayOf(Color.rgb(85, 230, 180), Color.rgb(210, 135, 255))
    )

    fun resize(newWidth: Int, newHeight: Int) {
        width = max(1, newWidth)
        height = max(1, newHeight)
        minDim = min(width, height).toFloat()
        rebuildBackgroundShaders()
        ensureCounts()
    }

    fun reloadSettings() {
        config = StarfieldPrefs.read(context)
        rebuildBackgroundShaders()
        ensureCounts()
    }

    fun draw(canvas: Canvas, dtSeconds: Float, timeSeconds: Float) {
        val safeDt = dtSeconds.coerceIn(0f, 0.045f)
        drawBackground(canvas)
        ensureCounts()

        drawNebulas(canvas, safeDt, timeSeconds)

        val centerX = width * 0.5f
        val centerY = height * 0.5f
        val focal = minDim * 0.58f
        val baseMargin = minDim * 0.18f

        for (star in stars) {
            star.z -= config.flightSpeed * star.speedFactor * safeDt
            if (star.z <= PAST_CAMERA_Z) {
                resetStar(star, farReset = true)
                continue
            }

            val x = centerX + (star.x / star.z) * focal
            val y = centerY + (star.y / star.z) * focal
            if (x.isNaN() || y.isNaN()) {
                resetStar(star, farReset = true)
                continue
            }

            val radius = (star.radiusBase * (0.58f / star.z)).coerceIn(0.20f, 7.0f)
            val systemMargin = if (star.isSystem && star.z < PLANET_REVEAL_Z) minDim * 0.15f / star.z else 0f
            val margin = max(baseMargin, radius * 10f) + systemMargin
            if (x < -margin || x > width + margin || y < -margin || y > height + margin) {
                resetStar(star, farReset = true)
                continue
            }

            drawStar(canvas, star, x, y, radius, centerX, centerY, timeSeconds)
        }
    }

    private fun rebuildBackgroundShaders() {
        backgroundGradient = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(
                Color.rgb(0, 1, 5),
                Color.rgb(3, 4, 12),
                Color.rgb(6, 7, 17)
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )

        val cx = width * 0.50f
        val cy = height * 0.48f
        ambientGlowGradient = RadialGradient(
            cx,
            cy,
            minDim * 0.95f,
            intArrayOf(
                Color.argb(13, 26, 36, 92),
                Color.argb(6, 18, 26, 64),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun drawBackground(canvas: Canvas) {
        backgroundPaint.shader = backgroundGradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        backgroundPaint.shader = null

        if (config.glowEnabled) {
            glowPaint.shader = ambientGlowGradient
            canvas.drawCircle(width * 0.50f, height * 0.48f, minDim * 0.95f, glowPaint)
            glowPaint.shader = null
        }
    }

    private fun drawNebulas(canvas: Canvas, dtSeconds: Float, timeSeconds: Float) {
        if (config.nebulaCount <= 0) return

        val focal = minDim * 0.32f
        val centerX = width * 0.5f
        val centerY = height * 0.5f

        for (nebula in nebulas) {
            nebula.z -= config.flightSpeed * nebula.speedFactor * 0.18f * dtSeconds
            if (nebula.z <= NEBULA_NEAR_Z) {
                resetNebula(nebula, farReset = true)
                continue
            }

            val x = centerX + (nebula.x / nebula.z) * focal
            val y = centerY + (nebula.y / nebula.z) * focal
            if (x.isNaN() || y.isNaN()) {
                resetNebula(nebula, farReset = true)
                continue
            }

            val radius = ((nebula.sizeBase * minDim) / nebula.z).coerceIn(24f, minDim * 1.25f)
            val margin = radius * 1.55f
            if (x < -margin || x > width + margin || y < -margin || y > height + margin) {
                resetNebula(nebula, farReset = true)
                continue
            }

            val depthReveal = smootherStep(((NEBULA_FAR_Z - nebula.z) / (NEBULA_FAR_Z - NEBULA_NEAR_Z)).coerceIn(0f, 1f))
            val nearSoftFade = (1f - smootherStep(((NEBULA_NEAR_FADE_Z - nebula.z) / (NEBULA_NEAR_FADE_Z - NEBULA_NEAR_Z)).coerceIn(0f, 1f)) * 0.35f).coerceIn(0.65f, 1f)
            val visibility = depthReveal * nearSoftFade * nebula.alphaBase * config.nebulaBrightness
            if (visibility <= 0.02f) continue

            val sprite = nebula.sprite ?: buildNebulaSprite(nebula).also { nebula.sprite = it }
            val diameter = radius * 2f
            val scale = diameter / NEBULA_SPRITE_SIZE
            val alpha = (visibility * 168f).toInt().coerceIn(0, 215)
            if (alpha <= 3) continue

            nebulaPaint.alpha = alpha
            nebulaMatrix.reset()
            nebulaMatrix.postTranslate(-NEBULA_SPRITE_SIZE * 0.5f, -NEBULA_SPRITE_SIZE * 0.5f)
            nebulaMatrix.postScale(scale * (1f + sin(timeSeconds * nebula.wobbleSpeed + nebula.phase) * 0.035f), scale)
            nebulaMatrix.postRotate(nebula.rotation + timeSeconds * nebula.rotationSpeed)
            nebulaMatrix.postTranslate(x, y)
            canvas.drawBitmap(sprite, nebulaMatrix, nebulaPaint)
            nebulaPaint.alpha = 255
        }
    }

    private fun buildNebulaSprite(nebula: Nebula): Bitmap {
        val bitmap = Bitmap.createBitmap(NEBULA_SPRITE_SIZE, NEBULA_SPRITE_SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
        val center = NEBULA_SPRITE_SIZE * 0.5f
        val base = NEBULA_SPRITE_SIZE * 0.46f

        for (lobe in nebula.lobes) {
            val lx = center + lobe.offsetX * base
            val ly = center + lobe.offsetY * base
            val lobeRadius = base * lobe.sizeFactor
            val alpha = (lobe.alpha * 210f).toInt().coerceIn(0, 230)
            val inner = blendColor(nebula.colorA, Color.WHITE, 0.13f)
            p.shader = RadialGradient(
                lx,
                ly,
                lobeRadius,
                intArrayOf(
                    withAlpha(inner, (alpha * 0.74f).toInt()),
                    withAlpha(nebula.colorA, (alpha * 0.55f).toInt()),
                    withAlpha(nebula.colorB, (alpha * 0.28f).toInt()),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.28f, 0.66f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawCircle(lx, ly, lobeRadius, p)
            p.shader = null
        }

        p.shader = RadialGradient(
            center,
            center,
            base * 0.82f,
            intArrayOf(
                withAlpha(blendColor(nebula.colorB, Color.WHITE, 0.08f), 82),
                withAlpha(nebula.colorA, 28),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(center, center, base * 0.82f, p)
        p.shader = null

        for (spark in nebula.sparks) {
            val sx = center + spark.offsetX * base
            val sy = center + spark.offsetY * base
            p.color = Color.argb(96, 230, 236, 255)
            c.drawCircle(sx, sy, (base * spark.radiusFactor).coerceIn(0.65f, 2.9f), p)
        }

        return bitmap
    }

    private fun drawStar(
        canvas: Canvas,
        star: Star,
        x: Float,
        y: Float,
        radius: Float,
        centerX: Float,
        centerY: Float,
        timeSeconds: Float
    ) {
        val travelX = x - centerX
        val travelY = y - centerY
        val depthAmount = smootherStep(((FAR_Z - star.z) / (FAR_Z - PAST_CAMERA_Z)).coerceIn(0f, 1f))

        if (config.trailsEnabled) {
            val distance = sqrt(travelX * travelX + travelY * travelY).coerceAtLeast(1f)
            val tailLength = (radius * (2.0f + depthAmount * 6.0f) * config.flightSpeed * 2.2f).coerceIn(0.8f, minDim * 0.20f)
            val tailX = x - travelX / distance * tailLength
            val tailY = y - travelY / distance * tailLength
            val trailAlpha = ((18 + 132 * depthAmount) * star.brightness).toInt().coerceIn(14, 190)
            trailPaint.color = withAlpha(star.trailTint, trailAlpha)
            trailPaint.strokeWidth = radius.coerceIn(0.40f, 2.9f)
            canvas.drawLine(tailX, tailY, x, y, trailPaint)
        }

        if (config.glowEnabled && radius > 1.75f) {
            val glowAlpha = ((7 + 20 * depthAmount) * star.brightness).toInt().coerceIn(3, 34)
            glowPaint.color = withAlpha(star.tint, (glowAlpha * 0.32f).toInt())
            canvas.drawCircle(x, y, radius * (3.0f + depthAmount * 0.9f), glowPaint)
            glowPaint.color = withAlpha(star.tint, glowAlpha)
            canvas.drawCircle(x, y, radius * (1.55f + depthAmount * 0.45f), glowPaint)
        }

        val outerAlpha = (102 + 126 * depthAmount + 22 * star.brightness).toInt().coerceIn(78, 255)
        starPaint.color = withAlpha(star.tint, outerAlpha)
        canvas.drawCircle(x, y, radius, starPaint)

        if (radius > 0.62f) {
            val coreAlpha = (148 + 90 * depthAmount).toInt().coerceIn(130, 255)
            starPaint.color = withAlpha(star.coreTint, coreAlpha)
            canvas.drawCircle(x, y, (radius * 0.36f).coerceAtLeast(0.24f), starPaint)
        }

        if (star.isSystem && star.z < PLANET_REVEAL_Z) {
            val closeAmount = smootherStep(((PLANET_REVEAL_Z - star.z) / (PLANET_REVEAL_Z - PLANET_FULL_Z)).coerceIn(0f, 1f))
            if (closeAmount > 0.01f) {
                drawSolarSystem(canvas, star, x, y, radius, timeSeconds, closeAmount)
            }
        }
    }

    private fun drawSolarSystem(canvas: Canvas, star: Star, x: Float, y: Float, radius: Float, timeSeconds: Float, visibility: Float) {
        val systemAlpha = (visibility * visibility * config.planetBrightness * 225f).toInt().coerceIn(0, 225)
        if (systemAlpha <= 4) return

        for (planet in star.planets) {
            val orbit = ((planet.orbitBase * minDim) / star.z).coerceIn(radius * 1.75f, minDim * 0.18f)
            val rx = orbit
            val ry = orbit * planet.incline
            val angle = planet.phase + timeSeconds * planet.orbitSpeed
            val planetX = x + cos(angle) * rx
            val planetY = y + sin(angle) * ry
            val planetRadius = (radius * planet.radiusRatio * (0.48f + visibility * 0.24f)).coerceIn(0.24f, radius * 0.28f)
            val color = planet.color

            if (config.orbitLinesEnabled && visibility > 0.40f) {
                orbitPaint.color = Color.argb((15 * visibility).toInt().coerceIn(0, 15), 150, 175, 220)
                orbitPaint.strokeWidth = (0.40f + visibility * 0.50f).coerceIn(0.40f, 1.0f)
                orbitRect.set(x - rx, y - ry, x + rx, y + ry)
                canvas.drawOval(orbitRect, orbitPaint)
            }

            if (config.trailsEnabled && visibility > 0.22f) {
                for (i in 4 downTo 1) {
                    val oldAngle = angle - i * 0.13f * planet.orbitDirection
                    val oldX = x + cos(oldAngle) * rx
                    val oldY = y + sin(oldAngle) * ry
                    val a = (systemAlpha * (0.024f * (5 - i))).toInt().coerceIn(0, 28)
                    planetPaint.color = withAlpha(color, a)
                    canvas.drawCircle(oldX, oldY, planetRadius * (0.30f + i * 0.035f), planetPaint)
                }
            }

            if (planet.hasRing && planetRadius > 1.35f && visibility > 0.56f) {
                canvas.save()
                canvas.rotate(planet.ringAngle, planetX, planetY)
                orbitPaint.color = withAlpha(planet.ringTint, (systemAlpha * 0.28f).toInt())
                orbitPaint.strokeWidth = (planetRadius * 0.18f).coerceIn(0.50f, 1.25f)
                orbitRect.set(
                    planetX - planetRadius * 2.0f,
                    planetY - planetRadius * 0.58f,
                    planetX + planetRadius * 2.0f,
                    planetY + planetRadius * 0.58f
                )
                canvas.drawOval(orbitRect, orbitPaint)
                canvas.restore()
            }

            if (config.glowEnabled && visibility > 0.32f && planetRadius > 0.75f) {
                planetPaint.color = withAlpha(color, (systemAlpha * 0.09f).toInt())
                canvas.drawCircle(planetX, planetY, planetRadius * 1.85f, planetPaint)
            }

            planetPaint.color = withAlpha(color, systemAlpha)
            canvas.drawCircle(planetX, planetY, planetRadius, planetPaint)

            if (planetRadius > 0.55f) {
                planetPaint.color = withAlpha(planet.highlightTint, (systemAlpha * 0.28f).toInt())
                canvas.drawCircle(
                    planetX - planetRadius * 0.30f,
                    planetY - planetRadius * 0.34f,
                    (planetRadius * 0.22f).coerceAtLeast(0.30f),
                    planetPaint
                )
            }
        }
    }

    private fun ensureCounts() {
        ensureStarCount()
        ensureNebulaCount()
    }

    private fun ensureStarCount() {
        val target = config.starCount
        while (stars.size < target) {
            stars.add(newStar(randomizeDepth = true))
        }
        while (stars.size > target) {
            stars.removeAt(stars.lastIndex)
        }
    }

    private fun ensureNebulaCount() {
        val target = config.nebulaCount
        while (nebulas.size < target) {
            nebulas.add(newNebula(randomizeDepth = true))
        }
        while (nebulas.size > target) {
            val nebula = nebulas.removeAt(nebulas.lastIndex)
            nebula.sprite?.recycle()
            nebula.sprite = null
        }
    }

    private fun newStar(randomizeDepth: Boolean): Star {
        val star = Star()
        resetStar(star, farReset = !randomizeDepth)
        if (randomizeDepth) star.z = rand(PAST_CAMERA_Z + 0.07f, FAR_Z)
        return star
    }

    private fun resetStar(star: Star, farReset: Boolean) {
        val willBeSystem = rng.nextFloat() < config.systemChance

        if (willBeSystem) {
            val angle = rand(0f, TWO_PI)
            val radius = rand(0.018f, 0.125f)
            star.x = cos(angle) * radius
            star.y = sin(angle) * radius
            star.isSystem = true
            star.radiusBase = randomStarRadiusBase(systemStar = true)
            rebuildPlanets(star)
        } else {
            val angle = rand(0f, TWO_PI)
            val radius = sqrt(rng.nextFloat()) * rand(0.06f, 1.05f)
            star.x = cos(angle) * radius
            star.y = sin(angle) * radius
            star.isSystem = false
            star.radiusBase = randomStarRadiusBase(systemStar = false)
            star.planets.clear()
        }

        star.z = if (farReset) FAR_Z + rand(0f, 0.34f) else rand(PAST_CAMERA_Z + 0.07f, FAR_Z)
        star.speedFactor = rand(0.78f, 1.20f)
        star.tint = weightedStarTint()
        star.coreTint = blendColor(star.tint, Color.WHITE, 0.74f)
        star.trailTint = blendColor(star.tint, Color.WHITE, 0.60f)
        star.brightness = rand(0.58f, 1.18f)
    }

    private fun randomStarRadiusBase(systemStar: Boolean): Float {
        val roll = rng.nextFloat()
        val base = when {
            roll < 0.58f -> rand(0.12f, 0.31f)
            roll < 0.86f -> rand(0.31f, 0.52f)
            roll < 0.97f -> rand(0.52f, 0.78f)
            else -> rand(0.78f, 1.08f)
        }
        return if (systemStar) (base * rand(1.05f, 1.24f)).coerceIn(0.30f, 1.06f) else base
    }

    private fun weightedStarTint(): Int {
        var pick = rng.nextInt(starTintWeightTotal)
        for (entry in starTints) {
            pick -= entry.weight
            if (pick < 0) return entry.color
        }
        return Color.WHITE
    }

    private fun rebuildPlanets(star: Star) {
        star.planets.clear()
        val count = rng.nextInt(4) + 2
        val usedColorStart = rng.nextInt(planetColors.size)
        for (i in 0 until count) {
            val direction = if (rng.nextBoolean()) 1f else -1f
            val color = planetColors[(usedColorStart + i) % planetColors.size]
            val biggerOuterPlanet = if (i == count - 1 && rng.nextFloat() < 0.25f) 1.18f else 1f
            star.planets.add(
                Planet(
                    orbitBase = 0.0038f + i * rand(0.0032f, 0.0057f),
                    radiusRatio = rand(0.08f, 0.20f) * biggerOuterPlanet,
                    phase = rand(0f, TWO_PI),
                    orbitSpeed = direction * rand(0.42f, 1.30f) / (1f + i * 0.45f),
                    orbitDirection = direction,
                    incline = rand(0.44f, 0.84f),
                    color = color,
                    highlightTint = blendColor(color, Color.WHITE, 0.62f),
                    ringTint = blendColor(color, Color.WHITE, 0.20f),
                    hasRing = rng.nextFloat() < 0.16f,
                    ringAngle = rand(-23f, 23f)
                )
            )
        }
    }

    private fun newNebula(randomizeDepth: Boolean): Nebula {
        val nebula = Nebula()
        resetNebula(nebula, farReset = !randomizeDepth)
        if (randomizeDepth) nebula.z = rand(0.26f, NEBULA_FAR_Z)
        return nebula
    }

    private fun resetNebula(nebula: Nebula, farReset: Boolean) {
        nebula.sprite?.recycle()
        nebula.sprite = null

        val angle = rand(0f, TWO_PI)
        val radius = sqrt(rng.nextFloat()) * rand(0.10f, 0.88f)
        nebula.x = cos(angle) * radius
        nebula.y = sin(angle) * radius * 0.82f
        nebula.z = if (farReset) NEBULA_FAR_Z + rand(0f, 0.40f) else rand(0.26f, NEBULA_FAR_Z)
        nebula.sizeBase = rand(0.050f, 0.135f)
        nebula.speedFactor = rand(0.58f, 1.05f)
        nebula.rotation = rand(0f, 360f)
        nebula.rotationSpeed = rand(-2.6f, 2.6f)
        nebula.wobbleSpeed = rand(0.10f, 0.28f)
        nebula.phase = rand(0f, TWO_PI)
        nebula.alphaBase = rand(0.70f, 1.08f)

        val palette = nebulaPalettes[rng.nextInt(nebulaPalettes.size)]
        nebula.colorA = palette[0]
        nebula.colorB = palette[1]

        nebula.lobes.clear()
        val lobeCount = rng.nextInt(5) + 7
        for (i in 0 until lobeCount) {
            nebula.lobes.add(
                NebulaLobe(
                    offsetX = rand(-0.68f, 0.68f),
                    offsetY = rand(-0.48f, 0.48f),
                    sizeFactor = rand(0.30f, 0.84f),
                    alpha = rand(0.48f, 1.0f)
                )
            )
        }

        nebula.sparks.clear()
        val sparkCount = rng.nextInt(9) + 8
        for (i in 0 until sparkCount) {
            nebula.sparks.add(
                NebulaSpark(
                    offsetX = rand(-0.56f, 0.56f),
                    offsetY = rand(-0.42f, 0.42f),
                    radiusFactor = rand(0.0035f, 0.009f),
                    phase = rand(0f, TWO_PI),
                    twinkleSpeed = rand(0.55f, 1.75f)
                )
            )
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)
    }

    private fun blendColor(a: Int, b: Int, t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)
        val inv = 1f - clamped
        return Color.rgb(
            (Color.red(a) * inv + Color.red(b) * clamped).toInt().coerceIn(0, 255),
            (Color.green(a) * inv + Color.green(b) * clamped).toInt().coerceIn(0, 255),
            (Color.blue(a) * inv + Color.blue(b) * clamped).toInt().coerceIn(0, 255)
        )
    }

    private fun smootherStep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * x * (x * (x * 6f - 15f) + 10f)
    }

    private fun rand(min: Float, max: Float): Float = min + rng.nextFloat() * (max - min)

    private data class WeightedColor(
        val color: Int,
        val weight: Int
    )

    private class Star {
        var x: Float = 0f
        var y: Float = 0f
        var z: Float = FAR_Z
        var radiusBase: Float = 1f
        var speedFactor: Float = 1f
        var isSystem: Boolean = false
        var tint: Int = Color.WHITE
        var coreTint: Int = Color.WHITE
        var trailTint: Int = Color.WHITE
        var brightness: Float = 1f
        val planets: MutableList<Planet> = ArrayList(5)
    }

    private data class Planet(
        val orbitBase: Float,
        val radiusRatio: Float,
        val phase: Float,
        val orbitSpeed: Float,
        val orbitDirection: Float,
        val incline: Float,
        val color: Int,
        val highlightTint: Int,
        val ringTint: Int,
        val hasRing: Boolean,
        val ringAngle: Float
    )

    private class Nebula {
        var x: Float = 0f
        var y: Float = 0f
        var z: Float = NEBULA_FAR_Z
        var sizeBase: Float = 0.08f
        var speedFactor: Float = 1f
        var rotation: Float = 0f
        var rotationSpeed: Float = 0f
        var wobbleSpeed: Float = 0.2f
        var phase: Float = 0f
        var alphaBase: Float = 0.9f
        var colorA: Int = Color.rgb(80, 100, 220)
        var colorB: Int = Color.rgb(220, 90, 190)
        var sprite: Bitmap? = null
        val lobes: MutableList<NebulaLobe> = ArrayList(11)
        val sparks: MutableList<NebulaSpark> = ArrayList(16)
    }

    private data class NebulaLobe(
        val offsetX: Float,
        val offsetY: Float,
        val sizeFactor: Float,
        val alpha: Float
    )

    private data class NebulaSpark(
        val offsetX: Float,
        val offsetY: Float,
        val radiusFactor: Float,
        val phase: Float,
        val twinkleSpeed: Float
    )

    private companion object {
        const val PAST_CAMERA_Z = 0.018f
        const val FAR_Z = 1.45f
        const val PLANET_REVEAL_Z = 0.155f
        const val PLANET_FULL_Z = 0.055f
        const val NEBULA_NEAR_Z = 0.035f
        const val NEBULA_NEAR_FADE_Z = 0.18f
        const val NEBULA_FAR_Z = 1.80f
        const val NEBULA_SPRITE_SIZE = 192
        const val TWO_PI = (Math.PI * 2.0).toFloat()
    }
}
