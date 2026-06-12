package com.sundek.starfieldnextgen.render

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import com.sundek.starfieldnextgen.settings.StarfieldConfig
import com.sundek.starfieldnextgen.settings.StarfieldPrefs
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class GlStarfieldRenderer(private val context: Context) {
    private companion object {
        const val TAG = "GlStarfieldRenderer"
        const val FLOAT_SIZE_BYTES = 4
        const val POINT_FLOATS = 7
        const val LINE_FLOATS = 6
        const val QUAD_FLOATS = 8
        const val MAX_PLANETS_PER_SYSTEM = 4
        const val MAX_NEBULAS = 20
        const val MAX_DUST = 3200
        const val NEBULA_LOBES = 6
        const val NOISE_TEXTURE_SIZE = 256
        const val FIELD_OF_VIEW = 0.78f

        const val STAR_VERTEX_SHADER = """
            attribute vec2 a_Position;
            attribute float a_PointSize;
            attribute vec4 a_Color;
            varying vec4 v_Color;
            void main() {
                gl_Position = vec4(a_Position, 0.0, 1.0);
                gl_PointSize = a_PointSize;
                v_Color = a_Color;
            }
        """

        const val STAR_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec4 v_Color;
            void main() {
                vec2 uv = gl_PointCoord - vec2(0.5);
                float dist = length(uv) * 2.0;
                float core = 1.0 - smoothstep(0.0, 0.72, dist);
                float glow = pow(max(0.0, 1.0 - dist), 2.6);
                float alpha = (core * 0.86 + glow * 0.46) * v_Color.a;
                if (alpha < 0.008) discard;
                vec3 color = v_Color.rgb * (0.72 + glow * 0.68);
                gl_FragColor = vec4(color, alpha);
            }
        """

        const val LINE_VERTEX_SHADER = """
            attribute vec2 a_Position;
            attribute vec4 a_Color;
            varying vec4 v_Color;
            void main() {
                gl_Position = vec4(a_Position, 0.0, 1.0);
                v_Color = a_Color;
            }
        """

        const val LINE_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec4 v_Color;
            void main() {
                gl_FragColor = v_Color;
            }
        """

        const val NEBULA_VERTEX_SHADER = """
            attribute vec2 a_Position;
            attribute vec2 a_Uv;
            attribute vec4 a_Color;
            varying vec2 v_Uv;
            varying vec4 v_Color;
            void main() {
                gl_Position = vec4(a_Position, 0.0, 1.0);
                v_Uv = a_Uv;
                v_Color = a_Color;
            }
        """

        const val NEBULA_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D u_NoiseTexture;
            uniform float u_Time;
            varying vec2 v_Uv;
            varying vec4 v_Color;

            float ovalMask(vec2 uv) {
                vec2 centered = uv - vec2(0.5);
                centered.x *= 0.92;
                centered.y *= 1.10;
                float d = length(centered) * 2.0;
                return 1.0 - smoothstep(0.30, 1.0, d);
            }

            void main() {
                // v_Uv stays in normal 0..1 quad space so the soft oval edge never disappears.
                float mask = ovalMask(v_Uv);
                if (mask <= 0.001) discard;

                // Animate only the noise samples. This gives cloud motion without breaking the oval mask.
                vec2 uv1 = fract(v_Uv * 0.92 + vec2(0.07 + u_Time * 0.010, 0.13 - u_Time * 0.006));
                vec2 uv2 = fract(v_Uv * 1.85 + vec2(0.31 - u_Time * 0.014, 0.47 + u_Time * 0.009));
                vec2 uv3 = fract(v_Uv * 3.70 + vec2(0.61 + u_Time * 0.021, 0.23 + u_Time * 0.013));
                vec4 t1 = texture2D(u_NoiseTexture, uv1);
                vec4 t2 = texture2D(u_NoiseTexture, uv2);
                vec4 t3 = texture2D(u_NoiseTexture, uv3);

                float broad = t1.r;
                float detail = t2.g;
                float wisp = t3.b;
                float cloud = clamp(broad * 0.58 + detail * 0.30 + wisp * 0.12, 0.0, 1.0);
                float filaments = smoothstep(0.46, 0.92, detail * 0.65 + wisp * 0.35);

                // Keep a visible floor so a bad/noisy texture sample cannot make the whole nebula disappear.
                float alphaShape = mask * (0.40 + cloud * 0.50 + filaments * 0.15);
                float alpha = alphaShape * v_Color.a;
                if (alpha < 0.001) discard;

                vec3 color = v_Color.rgb * (0.54 + cloud * 0.38 + filaments * 0.16);
                gl_FragColor = vec4(color, alpha);
            }
        """
    }

    private val random = Random(293847L)
    private var config: StarfieldConfig = StarfieldPrefs.read(context)
    private var width = 1
    private var height = 1
    private var aspect = 1f
    private var initialized = false

    private val maxStars = StarfieldPrefs.MAX_STAR_COUNT
    private val starX = FloatArray(maxStars)
    private val starY = FloatArray(maxStars)
    private val starZ = FloatArray(maxStars)
    private val starSpeed = FloatArray(maxStars)
    private val starDriftX = FloatArray(maxStars)
    private val starDriftY = FloatArray(maxStars)
    private val starBaseSize = FloatArray(maxStars)
    private val starR = FloatArray(maxStars)
    private val starG = FloatArray(maxStars)
    private val starB = FloatArray(maxStars)
    private val starLuminosity = FloatArray(maxStars)
    private val starSpawnFade = FloatArray(maxStars)
    private val starIsSystem = BooleanArray(maxStars)
    private val starIsBinary = BooleanArray(maxStars)
    private val planetCount = IntArray(maxStars)

    private val binarySeparation = FloatArray(maxStars)
    private val binaryPhase = FloatArray(maxStars)
    private val binarySpeed = FloatArray(maxStars)
    private val binaryCompanionSize = FloatArray(maxStars)
    private val binaryCompanionLuminosity = FloatArray(maxStars)
    private val binaryTilt = FloatArray(maxStars)
    private val binaryMassRatio = FloatArray(maxStars)
    private val binaryOrbitAngle = FloatArray(maxStars)
    private val binaryEccentricity = FloatArray(maxStars)
    private val binaryR = FloatArray(maxStars)
    private val binaryG = FloatArray(maxStars)
    private val binaryB = FloatArray(maxStars)

    private val planetOrbit = FloatArray(maxStars * MAX_PLANETS_PER_SYSTEM)
    private val planetPhase = FloatArray(maxStars * MAX_PLANETS_PER_SYSTEM)
    private val planetSpeed = FloatArray(maxStars * MAX_PLANETS_PER_SYSTEM)
    private val planetSize = FloatArray(maxStars * MAX_PLANETS_PER_SYSTEM)
    private val planetTilt = FloatArray(maxStars * MAX_PLANETS_PER_SYSTEM)
    private val planetR = FloatArray(maxStars * MAX_PLANETS_PER_SYSTEM)
    private val planetG = FloatArray(maxStars * MAX_PLANETS_PER_SYSTEM)
    private val planetB = FloatArray(maxStars * MAX_PLANETS_PER_SYSTEM)

    private val nebulaX = FloatArray(MAX_NEBULAS)
    private val nebulaY = FloatArray(MAX_NEBULAS)
    private val nebulaZ = FloatArray(MAX_NEBULAS)
    private val nebulaSize = FloatArray(MAX_NEBULAS)
    private val nebulaSpeed = FloatArray(MAX_NEBULAS)
    private val nebulaR = FloatArray(MAX_NEBULAS)
    private val nebulaG = FloatArray(MAX_NEBULAS)
    private val nebulaB = FloatArray(MAX_NEBULAS)
    private val nebulaRot = FloatArray(MAX_NEBULAS)
    private val nebulaSpin = FloatArray(MAX_NEBULAS)
    private val nebulaDriftX = FloatArray(MAX_NEBULAS)
    private val nebulaDriftY = FloatArray(MAX_NEBULAS)
    private val nebulaSpawnFade = FloatArray(MAX_NEBULAS)
    private val nebulaUvX = FloatArray(MAX_NEBULAS)
    private val nebulaUvY = FloatArray(MAX_NEBULAS)
    private val nebulaUvSpeedX = FloatArray(MAX_NEBULAS)
    private val nebulaUvSpeedY = FloatArray(MAX_NEBULAS)

    private val dustX = FloatArray(MAX_DUST)
    private val dustY = FloatArray(MAX_DUST)
    private val dustZ = FloatArray(MAX_DUST)
    private val dustSpeed = FloatArray(MAX_DUST)
    private val dustBaseSize = FloatArray(MAX_DUST)
    private val dustAlpha = FloatArray(MAX_DUST)
    private val dustSpawnFade = FloatArray(MAX_DUST)

    private val starBuffer = newFloatBuffer(maxStars * 3 * POINT_FLOATS)
    private val planetBuffer = newFloatBuffer(maxStars * MAX_PLANETS_PER_SYSTEM * POINT_FLOATS)
    private val dustBuffer = newFloatBuffer(MAX_DUST * POINT_FLOATS)
    private val lineBuffer = newFloatBuffer(maxStars * 2 * LINE_FLOATS)
    private val nebulaBuffer = newFloatBuffer(MAX_NEBULAS * NEBULA_LOBES * 6 * QUAD_FLOATS)

    private var starProgram = 0
    private var lineProgram = 0
    private var nebulaProgram = 0
    private var nebulaTexture = 0

    private var starPositionHandle = 0
    private var starPointSizeHandle = 0
    private var starColorHandle = 0

    private var linePositionHandle = 0
    private var lineColorHandle = 0

    private var nebulaPositionHandle = 0
    private var nebulaUvHandle = 0
    private var nebulaColorHandle = 0
    private var nebulaTextureHandle = 0
    private var nebulaTimeHandle = 0

    fun init() {
        if (initialized) return
        starProgram = createProgram(STAR_VERTEX_SHADER, STAR_FRAGMENT_SHADER)
        lineProgram = createProgram(LINE_VERTEX_SHADER, LINE_FRAGMENT_SHADER)
        nebulaProgram = createProgram(NEBULA_VERTEX_SHADER, NEBULA_FRAGMENT_SHADER)

        starPositionHandle = GLES20.glGetAttribLocation(starProgram, "a_Position")
        starPointSizeHandle = GLES20.glGetAttribLocation(starProgram, "a_PointSize")
        starColorHandle = GLES20.glGetAttribLocation(starProgram, "a_Color")

        linePositionHandle = GLES20.glGetAttribLocation(lineProgram, "a_Position")
        lineColorHandle = GLES20.glGetAttribLocation(lineProgram, "a_Color")

        nebulaPositionHandle = GLES20.glGetAttribLocation(nebulaProgram, "a_Position")
        nebulaUvHandle = GLES20.glGetAttribLocation(nebulaProgram, "a_Uv")
        nebulaColorHandle = GLES20.glGetAttribLocation(nebulaProgram, "a_Color")
        nebulaTextureHandle = GLES20.glGetUniformLocation(nebulaProgram, "u_NoiseTexture")
        nebulaTimeHandle = GLES20.glGetUniformLocation(nebulaProgram, "u_Time")
        nebulaTexture = createNoiseTexture()

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)

        for (i in 0 until maxStars) resetStar(i, randomDepth = true)
        for (i in 0 until MAX_DUST) resetDust(i, randomDepth = true)
        for (i in 0 until MAX_NEBULAS) resetNebula(i, randomDepth = true)
        initialized = true
    }

    fun resize(newWidth: Int, newHeight: Int) {
        width = max(1, newWidth)
        height = max(1, newHeight)
        aspect = width.toFloat() / height.toFloat()
        GLES20.glViewport(0, 0, width, height)
    }

    fun reloadSettings() {
        config = StarfieldPrefs.read(context)
    }

    fun draw(dtSeconds: Float, timeSeconds: Float) {
        if (!initialized) init()
        val dt = dtSeconds.coerceIn(0.001f, 0.05f)

        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0.002f, 0.004f, 0.010f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_BLEND)

        drawNebulas(dt, timeSeconds)
        drawDust(dt)
        drawTrails(dt)
        drawStarsAndPlanets(dt, timeSeconds)
    }

    fun release() {
        if (starProgram != 0) GLES20.glDeleteProgram(starProgram)
        if (lineProgram != 0) GLES20.glDeleteProgram(lineProgram)
        if (nebulaProgram != 0) GLES20.glDeleteProgram(nebulaProgram)
        if (nebulaTexture != 0) GLES20.glDeleteTextures(1, intArrayOf(nebulaTexture), 0)
        starProgram = 0
        lineProgram = 0
        nebulaProgram = 0
        nebulaTexture = 0
        initialized = false
    }

    private fun drawStarsAndPlanets(dt: Float, timeSeconds: Float) {
        val count = config.starCount.coerceIn(0, maxStars)
        starBuffer.clear()
        planetBuffer.clear()
        var starVertexCount = 0
        var planetVertexCount = 0
        val pxToNdcX = 2f / width.toFloat()
        val pxToNdcY = 2f / height.toFloat()

        for (i in 0 until count) {
            val zVelocity = config.flightSpeed * starSpeed[i]
            starZ[i] -= zVelocity * dt
            val lateralScale = config.starDirectionDrift * config.flightSpeed * dt
            starX[i] += starDriftX[i] * lateralScale
            starY[i] += starDriftY[i] * lateralScale

            val projectedX = projectX(starX[i], starZ[i])
            val projectedY = projectY(starY[i], starZ[i])
            if (starZ[i] <= 0.035f || (starZ[i] < 0.15f && (abs(projectedX) > 1.45f || abs(projectedY) > 1.45f))) {
                resetStar(i, randomDepth = false)
                continue
            }

            if (projectedX < -1.18f || projectedX > 1.18f || projectedY < -1.18f || projectedY > 1.18f) {
                continue
            }

            starSpawnFade[i] = (starSpawnFade[i] + dt * 0.42f).coerceAtMost(1f)
            val spawnFade = smooth01(starSpawnFade[i])
            val farFade = ((1.88f - starZ[i]) / 0.54f).coerceIn(0f, 1f).pow(1.35f)
            val fadeIn = min(spawnFade, farFade)
            if (fadeIn <= 0.003f) continue

            val distanceCurve = ((1.28f - starZ[i]) / 1.23f).coerceIn(0f, 1f).pow(1.18f)
            val scale = 1.0f / sqrt(starZ[i] + 0.18f)
            val baseSize = starBaseSize[i]
            val glowMultiplier = if (config.glowEnabled) 1.18f else 0.88f
            val pointSize = (baseSize * (1.55f + scale * 3.65f) * glowMultiplier).coerceIn(0.75f, 30.0f)

            val minBrightness = min(config.starMinBrightness, config.starMaxBrightness).coerceIn(0f, 2.2f)
            val maxBrightness = max(config.starMinBrightness, config.starMaxBrightness).coerceIn(0.02f, 2.2f)
            val distanceBrightness = minBrightness + (maxBrightness - minBrightness) * distanceCurve
            val alpha = distanceBrightness * starLuminosity[i] * fadeIn

            val binaryFade = if (starIsBinary[i]) ((0.225f - starZ[i]) / 0.120f).coerceIn(0f, 1f).let { smooth01(it) } else 0f
            if (alpha > 0.004f) {
                if (binaryFade > 0.015f) {
                    val orbitPx = pointSize * binarySeparation[i] * (0.70f + binaryFade * 0.68f)
                    val angle = binaryPhase[i] + timeSeconds * binarySpeed[i]
                    val localX = cos(angle) * orbitPx
                    val localY = sin(angle) * orbitPx * binaryTilt[i] * binaryEccentricity[i]
                    val orientation = binaryOrbitAngle[i]
                    val rotatedX = localX * cos(orientation) - localY * sin(orientation)
                    val rotatedY = localX * sin(orientation) + localY * cos(orientation)
                    val companionMass = binaryMassRatio[i].coerceIn(0.28f, 1.25f)
                    val totalMass = 1.0f + companionMass
                    val primaryOffset = companionMass / totalMass
                    val companionOffset = 1.0f / totalMass
                    val primaryX = projectedX - rotatedX * primaryOffset * pxToNdcX
                    val primaryY = projectedY - rotatedY * primaryOffset * pxToNdcY
                    val companionX = projectedX + rotatedX * companionOffset * pxToNdcX
                    val companionY = projectedY + rotatedY * companionOffset * pxToNdcY
                    val centerAlpha = alpha * (1f - binaryFade * 0.90f)
                    if (centerAlpha > 0.006f) {
                        putPoint(starBuffer, projectedX, projectedY, pointSize, starR[i], starG[i], starB[i], centerAlpha.coerceIn(0f, 1.05f))
                        starVertexCount++
                    }
                    val primarySize = (pointSize * (0.78f + (1.0f - companionMass / 1.25f) * 0.22f + binaryFade * 0.08f)).coerceAtLeast(0.75f)
                    val companionSize = (pointSize * binaryCompanionSize[i]).coerceAtLeast(0.65f)
                    putPoint(starBuffer, primaryX, primaryY, primarySize, starR[i], starG[i], starB[i], (alpha * binaryFade * 0.98f).coerceIn(0f, 1.18f))
                    starVertexCount++
                    putPoint(starBuffer, companionX, companionY, companionSize, binaryR[i], binaryG[i], binaryB[i], (alpha * binaryFade * binaryCompanionLuminosity[i] * 0.90f).coerceIn(0f, 1.10f))
                    starVertexCount++
                } else {
                    putPoint(starBuffer, projectedX, projectedY, pointSize, starR[i], starG[i], starB[i], alpha.coerceIn(0f, 1.28f))
                    starVertexCount++
                }
            }

            val closeFade = ((0.135f - starZ[i]) / 0.075f).coerceIn(0f, 1f)
            if (starIsSystem[i] && !starIsBinary[i] && closeFade > 0f) {
                val pCount = planetCount[i]
                for (p in 0 until pCount) {
                    val index = i * MAX_PLANETS_PER_SYSTEM + p
                    val orbitPx = pointSize * planetOrbit[index] * (0.78f + closeFade * 0.5f)
                    val angle = planetPhase[index] + timeSeconds * planetSpeed[index]
                    val px = projectedX + cos(angle) * orbitPx * pxToNdcX
                    val py = projectedY + sin(angle) * orbitPx * planetTilt[index] * pxToNdcY
                    val pSize = (pointSize * planetSize[index] * (0.56f + closeFade * 0.38f)).coerceIn(0.65f, max(0.9f, pointSize * 0.28f))
                    val minPlanetBrightness = min(config.planetMinBrightness, config.planetMaxBrightness).coerceIn(0f, 2.0f)
                    val maxPlanetBrightness = max(config.planetMinBrightness, config.planetMaxBrightness).coerceIn(0.05f, 2.0f)
                    val planetDistanceBrightness = minPlanetBrightness + (maxPlanetBrightness - minPlanetBrightness) * closeFade.pow(1.55f)
                    val parentSizeInfluence = (pointSize / 24.0f).coerceIn(0.0f, 1.0f)
                    val parentColorInfluence = (0.2126f * starR[i] + 0.7152f * starG[i] + 0.0722f * starB[i]).coerceIn(0.45f, 1.12f)
                    val parentLight = (0.60f + starLuminosity[i] * 0.28f + parentSizeInfluence * 0.22f) * parentColorInfluence
                    val pAlpha = (closeFade * closeFade * planetDistanceBrightness * parentLight * 0.82f).coerceIn(0f, 1.15f)
                    val litR = (planetR[index] * 0.82f + starR[i] * 0.18f).coerceIn(0f, 1f)
                    val litG = (planetG[index] * 0.82f + starG[i] * 0.18f).coerceIn(0f, 1f)
                    val litB = (planetB[index] * 0.82f + starB[i] * 0.18f).coerceIn(0f, 1f)
                    if (px > -1.08f && px < 1.08f && py > -1.08f && py < 1.08f && pSize < pointSize) {
                        putPoint(planetBuffer, px, py, pSize, litR, litG, litB, pAlpha)
                        planetVertexCount++
                    }
                }
            }
        }

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        if (starVertexCount > 0) {
            starBuffer.flip()
            drawPointBuffer(starBuffer, starVertexCount)
        }
        if (planetVertexCount > 0) {
            planetBuffer.flip()
            drawPointBuffer(planetBuffer, planetVertexCount)
        }
    }

    private fun drawDust(dt: Float) {
        val amount = config.dustAmount.coerceIn(0f, 1f)
        if (amount <= 0.002f) return
        val count = (MAX_DUST * amount).toInt().coerceIn(0, MAX_DUST)
        if (count <= 0) return

        dustBuffer.clear()
        var vertexCount = 0
        for (i in 0 until count) {
            dustZ[i] -= config.flightSpeed * dustSpeed[i] * 1.22f * dt
            val projectedX = projectX(dustX[i], dustZ[i])
            val projectedY = projectY(dustY[i], dustZ[i])
            if (dustZ[i] <= 0.040f || (dustZ[i] < 0.14f && (abs(projectedX) > 1.55f || abs(projectedY) > 1.55f))) {
                resetDust(i, randomDepth = false)
                continue
            }
            if (projectedX < -1.12f || projectedX > 1.12f || projectedY < -1.12f || projectedY > 1.12f) continue

            dustSpawnFade[i] = (dustSpawnFade[i] + dt * 0.55f).coerceAtMost(1f)
            val spawnFade = smooth01(dustSpawnFade[i])
            val farFade = smooth01(((1.55f - dustZ[i]) / 0.70f).coerceIn(0f, 1f))
            val close = ((0.72f - dustZ[i]) / 0.68f).coerceIn(0f, 1f)
            val speedVisibility = (0.45f + config.flightSpeed * 1.25f).coerceIn(0.45f, 1.25f)
            val alpha = dustAlpha[i] * (0.55f + close * 1.20f) * spawnFade * farFade * speedVisibility
            if (alpha <= 0.002f) continue

            val pointSize = (dustBaseSize[i] * (0.82f + close * 2.20f)).coerceIn(0.45f, 3.25f)
            putPoint(dustBuffer, projectedX, projectedY, pointSize, 0.58f, 0.66f, 0.82f, alpha.coerceIn(0f, 0.22f))
            vertexCount++
        }

        if (vertexCount == 0) return
        dustBuffer.flip()
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        drawPointBuffer(dustBuffer, vertexCount)
    }

    private fun drawTrails(dt: Float) {
        if (!config.trailsEnabled) return
        val count = config.starCount.coerceIn(0, maxStars)
        lineBuffer.clear()
        var vertexCount = 0

        for (i in 0 until count) {
            val z = starZ[i]
            if (z > 0.60f || z < 0.045f) continue
            val currentX = projectX(starX[i], z)
            val currentY = projectY(starY[i], z)
            if (currentX < -1.15f || currentX > 1.15f || currentY < -1.15f || currentY > 1.15f) continue

            val trailBackTime = dt * 4.2f
            val previousZ = z + config.flightSpeed * starSpeed[i] * trailBackTime
            val previousX = projectX(starX[i] - starDriftX[i] * config.starDirectionDrift * config.flightSpeed * trailBackTime, previousZ)
            val previousY = projectY(starY[i] - starDriftY[i] * config.starDirectionDrift * config.flightSpeed * trailBackTime, previousZ)
            val closeness = ((0.62f - z) / 0.58f).coerceIn(0f, 1f)
            val spawnFade = smooth01(starSpawnFade[i])
            val alpha = (0.06f + closeness * 0.22f) * spawnFade
            putLineVertex(lineBuffer, previousX, previousY, starR[i], starG[i], starB[i], alpha)
            putLineVertex(lineBuffer, currentX, currentY, starR[i], starG[i], starB[i], alpha * 0.55f)
            vertexCount += 2
        }

        if (vertexCount == 0) return
        lineBuffer.flip()
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glUseProgram(lineProgram)
        val stride = LINE_FLOATS * FLOAT_SIZE_BYTES
        lineBuffer.position(0)
        GLES20.glVertexAttribPointer(linePositionHandle, 2, GLES20.GL_FLOAT, false, stride, lineBuffer)
        GLES20.glEnableVertexAttribArray(linePositionHandle)
        lineBuffer.position(2)
        GLES20.glVertexAttribPointer(lineColorHandle, 4, GLES20.GL_FLOAT, false, stride, lineBuffer)
        GLES20.glEnableVertexAttribArray(lineColorHandle)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(linePositionHandle)
        GLES20.glDisableVertexAttribArray(lineColorHandle)
        lineBuffer.position(0)
    }

    private fun drawNebulas(dt: Float, timeSeconds: Float) {
        if (!config.nebulasEnabled || config.nebulaCount <= 0) return
        val count = config.nebulaCount.coerceIn(0, MAX_NEBULAS)
        nebulaBuffer.clear()
        var vertexCount = 0
        val motion = config.nebulaMotion.coerceIn(0f, 1f)
        val nebulaMotionSpeed = config.flightSpeed * (0.36f + motion * 1.18f)

        for (i in 0 until count) {
            val zStep = nebulaMotionSpeed * nebulaSpeed[i] * dt
            nebulaZ[i] -= zStep
            nebulaX[i] += nebulaDriftX[i] * nebulaMotionSpeed * dt
            nebulaY[i] += nebulaDriftY[i] * nebulaMotionSpeed * dt
            nebulaRot[i] += nebulaSpin[i] * (0.11f + motion * 0.19f) * dt
            nebulaUvX[i] += nebulaUvSpeedX[i] * (0.018f + motion * 0.036f) * dt
            nebulaUvY[i] += nebulaUvSpeedY[i] * (0.018f + motion * 0.036f) * dt
            nebulaSpawnFade[i] = (nebulaSpawnFade[i] + dt * (0.28f + motion * 0.20f)).coerceAtMost(1f)

            if (nebulaZ[i] <= -0.22f) {
                resetNebula(i, randomDepth = false)
                continue
            }

            val displayZ = max(0.06f, nebulaZ[i])
            val projectedX = projectX(nebulaX[i], displayZ)
            val projectedY = projectY(nebulaY[i], displayZ)

            val distanceProgress = ((3.10f - displayZ) / 2.92f).coerceIn(0f, 1f)
            val farFade = smooth01(((3.18f - displayZ) / 0.96f).coerceIn(0f, 1f))
            val spawnFade = (0.22f + smooth01(nebulaSpawnFade[i]) * 0.78f).coerceIn(0f, 1f)
            val nearFade = smooth01(((displayZ - 0.10f) / 0.46f).coerceIn(0f, 1f))
            val edgeMeasure = max(abs(projectedX), abs(projectedY))
            val edgeFade = (1f - smooth01(((edgeMeasure - 1.16f) / 0.72f).coerceIn(0f, 1f))).coerceIn(0.10f, 1f)
            val fullFade = spawnFade * farFade * nearFade * edgeFade
            if (fullFade <= 0.0015f) continue

            val closeBoost = distanceProgress.pow(0.74f)
            val approachDiffuse = (1f - nearFade)
            val softSize = (nebulaSize[i] / (displayZ * 0.72f)).coerceIn(0.10f, 2.70f) * (1.0f + approachDiffuse * 0.42f)
            val alphaBase = (0.064f * config.nebulaBrightness * (0.64f + closeBoost * 1.54f) / displayZ.pow(0.08f) * fullFade).coerceIn(0.003f, 0.60f)
            if (alphaBase <= 0.0015f) continue

            val w = softSize
            val h = softSize * (0.70f + 0.16f * sin(nebulaRot[i]))
            if (displayZ < 0.18f) {
                val overdrawMargin = 2.6f
                if (projectedX + w * overdrawMargin < -1.60f || projectedX - w * overdrawMargin > 1.60f || projectedY + h * overdrawMargin < -1.60f || projectedY - h * overdrawMargin > 1.60f) {
                    continue
                }
            } else if (projectedX + w * 1.55f < -1.32f || projectedX - w * 1.55f > 1.32f || projectedY + h * 1.55f < -1.32f || projectedY - h * 1.55f > 1.32f) {
                continue
            }

            val uvScale = 0.66f + closeBoost * 1.18f
            val uBase = nebulaUvX[i] + timeSeconds * nebulaUvSpeedX[i] * 0.010f
            val vBase = nebulaUvY[i] + timeSeconds * nebulaUvSpeedY[i] * 0.010f

            putNebulaQuad(nebulaBuffer, projectedX, projectedY, w, h, nebulaR[i], nebulaG[i], nebulaB[i], alphaBase * (0.72f + nearFade * 0.10f), uBase, vBase, uvScale)
            vertexCount += 6

            for (lobe in 1 until NEBULA_LOBES) {
                val angle = nebulaRot[i] + lobe * 1.43f
                val wobble = sin(nebulaRot[i] * 0.71f + lobe * 2.11f)
                val offset = softSize * (0.12f + 0.058f * lobe) * (0.75f + closeBoost * 0.40f)
                val lx = projectedX + cos(angle) * offset * 0.72f
                val ly = projectedY + sin(angle * 1.17f) * offset * 0.48f
                val lw = w * (0.30f + 0.055f * lobe) * (0.92f + closeBoost * 0.22f + approachDiffuse * 0.08f)
                val lh = h * (0.26f + 0.045f * ((lobe + 2) % 4)) * (0.92f + closeBoost * 0.22f + approachDiffuse * 0.08f)
                val tintWarm = if (lobe % 2 == 0) 1.06f else 0.92f
                val tintCool = if (lobe % 3 == 0) 1.10f else 0.96f
                val lr = (nebulaR[i] * tintWarm + wobble * 0.035f).coerceIn(0f, 1f)
                val lg = (nebulaG[i] * (0.96f + wobble * 0.030f)).coerceIn(0f, 1f)
                val lb = (nebulaB[i] * tintCool - wobble * 0.025f).coerceIn(0f, 1f)
                val la = alphaBase * ((0.54f - lobe * 0.040f).coerceAtLeast(0.24f))
                val lobeUvScale = uvScale * (1.08f + lobe * 0.10f)
                putNebulaQuad(
                    nebulaBuffer,
                    lx,
                    ly,
                    lw,
                    lh,
                    lr,
                    lg,
                    lb,
                    la,
                    uBase + lobe * 0.137f + wobble * 0.035f,
                    vBase + lobe * 0.091f - wobble * 0.025f,
                    lobeUvScale
                )
                vertexCount += 6
            }
        }

        if (vertexCount == 0) return
        nebulaBuffer.flip()
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(nebulaProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, nebulaTexture)
        GLES20.glUniform1i(nebulaTextureHandle, 0)
        GLES20.glUniform1f(nebulaTimeHandle, timeSeconds)
        val stride = QUAD_FLOATS * FLOAT_SIZE_BYTES
        nebulaBuffer.position(0)
        GLES20.glVertexAttribPointer(nebulaPositionHandle, 2, GLES20.GL_FLOAT, false, stride, nebulaBuffer)
        GLES20.glEnableVertexAttribArray(nebulaPositionHandle)
        nebulaBuffer.position(2)
        GLES20.glVertexAttribPointer(nebulaUvHandle, 2, GLES20.GL_FLOAT, false, stride, nebulaBuffer)
        GLES20.glEnableVertexAttribArray(nebulaUvHandle)
        nebulaBuffer.position(4)
        GLES20.glVertexAttribPointer(nebulaColorHandle, 4, GLES20.GL_FLOAT, false, stride, nebulaBuffer)
        GLES20.glEnableVertexAttribArray(nebulaColorHandle)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(nebulaPositionHandle)
        GLES20.glDisableVertexAttribArray(nebulaUvHandle)
        GLES20.glDisableVertexAttribArray(nebulaColorHandle)
        nebulaBuffer.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun drawPointBuffer(buffer: FloatBuffer, vertexCount: Int) {
        GLES20.glUseProgram(starProgram)
        val stride = POINT_FLOATS * FLOAT_SIZE_BYTES
        buffer.position(0)
        GLES20.glVertexAttribPointer(starPositionHandle, 2, GLES20.GL_FLOAT, false, stride, buffer)
        GLES20.glEnableVertexAttribArray(starPositionHandle)
        buffer.position(2)
        GLES20.glVertexAttribPointer(starPointSizeHandle, 1, GLES20.GL_FLOAT, false, stride, buffer)
        GLES20.glEnableVertexAttribArray(starPointSizeHandle)
        buffer.position(3)
        GLES20.glVertexAttribPointer(starColorHandle, 4, GLES20.GL_FLOAT, false, stride, buffer)
        GLES20.glEnableVertexAttribArray(starColorHandle)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(starPositionHandle)
        GLES20.glDisableVertexAttribArray(starPointSizeHandle)
        GLES20.glDisableVertexAttribArray(starColorHandle)
        buffer.position(0)
    }

    private fun resetStar(i: Int, randomDepth: Boolean) {
        val z = if (randomDepth) randomRange(0.25f, 1.75f) else randomRange(1.05f, 1.92f)
        val spawn = randomStarSpawnScreenPosition()
        val screenX = spawn.first
        val screenY = spawn.second
        starZ[i] = z
        starX[i] = screenX * z * aspect * FIELD_OF_VIEW
        starY[i] = screenY * z * FIELD_OF_VIEW
        starSpeed[i] = randomRange(0.58f, 1.48f)
        val driftStrength = if (random.nextFloat() < 0.86f) randomRange(0.010f, 0.044f) else randomRange(0.044f, 0.088f)
        val driftAngle = randomRange(0f, (Math.PI * 2.0).toFloat())
        starDriftX[i] = cos(driftAngle) * driftStrength
        starDriftY[i] = sin(driftAngle) * driftStrength
        starSpawnFade[i] = if (randomDepth) randomRange(0.72f, 1.0f) else 0f
        starBaseSize[i] = weightedStarSize()
        setNaturalStarColor(i)
        starLuminosity[i] = (starLuminosity[i] * apparentLuminosityMultiplier()).coerceIn(0.20f, 2.35f)

        val closeRoute = abs(screenX) < 0.30f && abs(screenY) < 0.30f
        starIsBinary[i] = closeRoute && random.nextFloat() < (config.binaryChance * 1.75f).coerceIn(0f, 0.72f)
        starIsSystem[i] = !starIsBinary[i] && closeRoute && random.nextFloat() < (config.systemChance * 1.75f).coerceIn(0f, 0.65f)
        planetCount[i] = if (starIsSystem[i]) 2 + random.nextInt(3) else 0
        if (starIsSystem[i]) resetPlanetsForStar(i)
        if (starIsBinary[i]) resetBinaryForStar(i)
    }

    private fun resetBinaryForStar(starIndex: Int) {
        binarySeparation[starIndex] = randomRange(0.86f, 1.86f)
        binaryPhase[starIndex] = randomRange(0f, (Math.PI * 2.0).toFloat())
        binarySpeed[starIndex] = randomRange(-1.15f, 1.15f).let { if (abs(it) < 0.28f) it + 0.42f else it }
        binaryMassRatio[starIndex] = randomRange(0.34f, 1.10f)
        binaryCompanionSize[starIndex] = (0.48f + binaryMassRatio[starIndex] * 0.34f + randomRange(-0.08f, 0.08f)).coerceIn(0.42f, 0.92f)
        binaryCompanionLuminosity[starIndex] = (0.56f + binaryMassRatio[starIndex] * 0.32f + randomRange(-0.08f, 0.10f)).coerceIn(0.52f, 1.08f)
        binaryTilt[starIndex] = if (random.nextFloat() < 0.22f) randomRange(0.12f, 0.38f) else randomRange(0.42f, 1.00f)
        binaryOrbitAngle[starIndex] = randomRange(0f, (Math.PI * 2.0).toFloat())
        binaryEccentricity[starIndex] = if (random.nextFloat() < 0.22f) randomRange(0.55f, 0.78f) else randomRange(0.78f, 1.00f)
        setBinaryCompanionColor(starIndex)
    }

    private fun resetPlanetsForStar(starIndex: Int) {
        val count = planetCount[starIndex]
        for (p in 0 until MAX_PLANETS_PER_SYSTEM) {
            val index = starIndex * MAX_PLANETS_PER_SYSTEM + p
            if (p < count) {
                planetOrbit[index] = randomRange(0.95f + p * 0.35f, 1.42f + p * 0.44f)
                planetPhase[index] = randomRange(0f, (Math.PI * 2.0).toFloat())
                planetSpeed[index] = randomRange(-1.30f, 1.30f).let { if (abs(it) < 0.25f) it + 0.35f else it }
                planetSize[index] = randomRange(0.10f, 0.24f)
                planetTilt[index] = if (random.nextFloat() < 0.24f) randomRange(0.12f, 0.35f) else randomRange(0.38f, 1.00f)
                setPlanetColor(index)
            } else {
                planetOrbit[index] = 0f
                planetPhase[index] = 0f
                planetSpeed[index] = 0f
                planetSize[index] = 0f
                planetTilt[index] = 0f
                planetR[index] = 0f
                planetG[index] = 0f
                planetB[index] = 0f
            }
        }
    }

    private fun resetDust(i: Int, randomDepth: Boolean) {
        val z = if (randomDepth) randomRange(0.16f, 1.60f) else randomRange(1.05f, 1.70f)
        val screenX = randomRange(-1.10f, 1.10f)
        val screenY = randomRange(-1.10f, 1.10f)
        dustZ[i] = z
        dustX[i] = screenX * z * aspect * FIELD_OF_VIEW
        dustY[i] = screenY * z * FIELD_OF_VIEW
        dustSpeed[i] = randomRange(0.86f, 1.72f)
        dustBaseSize[i] = randomRange(0.40f, 0.95f)
        dustAlpha[i] = randomRange(0.018f, 0.075f)
        dustSpawnFade[i] = if (randomDepth) randomRange(0.50f, 1.0f) else 0f
    }

    private fun resetNebula(i: Int, randomDepth: Boolean) {
        val z = if (randomDepth) randomRange(0.58f, 3.05f) else randomRange(2.25f, 3.20f)
        val screenX = randomRange(-1.18f, 1.18f)
        val screenY = randomRange(-1.12f, 1.12f)
        nebulaZ[i] = z
        nebulaX[i] = screenX * z * aspect * FIELD_OF_VIEW
        nebulaY[i] = screenY * z * FIELD_OF_VIEW
        nebulaSize[i] = randomRange(0.32f, 0.82f)
        nebulaSpeed[i] = randomRange(0.72f, 1.34f)
        nebulaRot[i] = randomRange(0f, (Math.PI * 2.0).toFloat())
        nebulaSpin[i] = randomRange(-1.0f, 1.0f).let { if (abs(it) < 0.24f) it + 0.31f else it }
        val driftAngle = randomRange(0f, (Math.PI * 2.0).toFloat())
        val driftAmount = randomRange(0.030f, 0.105f)
        nebulaDriftX[i] = cos(driftAngle) * driftAmount
        nebulaDriftY[i] = sin(driftAngle) * driftAmount
        nebulaSpawnFade[i] = if (randomDepth) randomRange(0.62f, 1.0f) else 0f
        nebulaUvX[i] = randomRange(0f, 16f)
        nebulaUvY[i] = randomRange(0f, 16f)
        nebulaUvSpeedX[i] = randomRange(-1.0f, 1.0f).let { if (abs(it) < 0.22f) it + 0.33f else it }
        nebulaUvSpeedY[i] = randomRange(-1.0f, 1.0f).let { if (abs(it) < 0.22f) it - 0.33f else it }
        when (random.nextInt(5)) {
            0 -> setNebulaColor(i, 0.40f, 0.48f, 1.00f)
            1 -> setNebulaColor(i, 0.80f, 0.38f, 1.00f)
            2 -> setNebulaColor(i, 1.00f, 0.44f, 0.30f)
            3 -> setNebulaColor(i, 0.32f, 0.95f, 0.88f)
            else -> setNebulaColor(i, 0.82f, 0.66f, 1.00f)
        }
    }

    private fun setNaturalStarColor(i: Int) {
        val roll = random.nextFloat()
        val jitter = randomRange(-0.035f, 0.035f)
        when {
            roll < 0.52f -> {
                setStarColor(i, 1.00f, 0.94f + jitter, 0.82f + jitter) // warm white / G type feel
                starLuminosity[i] = randomRange(0.72f, 1.00f)
            }
            roll < 0.72f -> {
                setStarColor(i, 1.00f, 0.86f + jitter, 0.58f + jitter) // yellow orange
                starLuminosity[i] = randomRange(0.62f, 0.92f)
            }
            roll < 0.84f -> {
                setStarColor(i, 1.00f, 0.64f + jitter, 0.38f + jitter) // orange/red-orange
                starLuminosity[i] = randomRange(0.48f, 0.78f)
            }
            roll < 0.94f -> {
                setStarColor(i, 0.82f + jitter, 0.90f + jitter, 1.00f) // blue white
                starLuminosity[i] = randomRange(0.90f, 1.22f)
            }
            roll < 0.985f -> {
                setStarColor(i, 0.62f + jitter, 0.75f + jitter, 1.00f) // hotter blue
                starLuminosity[i] = randomRange(1.02f, 1.38f)
            }
            else -> {
                setStarColor(i, 1.00f, 0.38f + jitter, 0.28f + jitter) // rare red tint
                starLuminosity[i] = randomRange(0.38f, 0.70f)
            }
        }
    }

    private fun setBinaryCompanionColor(i: Int) {
        val jitter = randomRange(-0.030f, 0.030f)
        val related = random.nextFloat() < 0.62f
        if (related) {
            setBinaryColor(
                i,
                (starR[i] + randomRange(-0.08f, 0.08f)).coerceIn(0f, 1f),
                (starG[i] + randomRange(-0.08f, 0.08f)).coerceIn(0f, 1f),
                (starB[i] + randomRange(-0.08f, 0.08f)).coerceIn(0f, 1f)
            )
            return
        }
        val roll = random.nextFloat()
        when {
            roll < 0.42f -> setBinaryColor(i, 1.00f, 0.92f + jitter, 0.78f + jitter)
            roll < 0.62f -> setBinaryColor(i, 1.00f, 0.76f + jitter, 0.50f + jitter)
            roll < 0.78f -> setBinaryColor(i, 0.84f + jitter, 0.91f + jitter, 1.00f)
            roll < 0.90f -> setBinaryColor(i, 1.00f, 0.55f + jitter, 0.36f + jitter)
            else -> setBinaryColor(i, 0.62f + jitter, 0.76f + jitter, 1.00f)
        }
    }

    private fun setPlanetColor(i: Int) {
        when (random.nextInt(7)) {
            0 -> setPlanetColor(i, 0.46f, 0.68f, 1.0f)
            1 -> setPlanetColor(i, 0.95f, 0.62f, 0.32f)
            2 -> setPlanetColor(i, 0.55f, 0.95f, 0.72f)
            3 -> setPlanetColor(i, 0.90f, 0.86f, 0.62f)
            4 -> setPlanetColor(i, 0.68f, 0.52f, 0.95f)
            5 -> setPlanetColor(i, 0.96f, 0.44f, 0.38f)
            else -> setPlanetColor(i, 0.62f, 0.74f, 0.82f)
        }
    }

    private fun setStarColor(i: Int, r: Float, g: Float, b: Float) {
        starR[i] = r.coerceIn(0f, 1f)
        starG[i] = g.coerceIn(0f, 1f)
        starB[i] = b.coerceIn(0f, 1f)
    }

    private fun setBinaryColor(i: Int, r: Float, g: Float, b: Float) {
        binaryR[i] = r.coerceIn(0f, 1f)
        binaryG[i] = g.coerceIn(0f, 1f)
        binaryB[i] = b.coerceIn(0f, 1f)
    }

    private fun setPlanetColor(i: Int, r: Float, g: Float, b: Float) {
        planetR[i] = r.coerceIn(0f, 1f)
        planetG[i] = g.coerceIn(0f, 1f)
        planetB[i] = b.coerceIn(0f, 1f)
    }

    private fun setNebulaColor(i: Int, r: Float, g: Float, b: Float) {
        nebulaR[i] = r
        nebulaG[i] = g
        nebulaB[i] = b
    }

    private fun randomStarSpawnScreenPosition(): Pair<Float, Float> {
        val band = config.galacticPlaneStrength.coerceIn(0f, 1f)
        val useBand = band > 0.01f && random.nextFloat() < (0.12f + band * 0.58f)
        if (useBand) {
            // A subtle diagonal Milky-Way-like density plane. It is a spawn bias, not a flat image layer,
            // so it still feels like real depth when the stars move toward the camera.
            val angle = -0.30f
            val along = randomRange(-1.42f, 1.42f)
            val bandWidth = 0.10f + (1f - band) * 0.34f
            val offset = centeredNoise() * bandWidth + randomRange(-0.045f, 0.045f)
            val x = along * cos(angle) - offset * sin(angle)
            val y = along * sin(angle) + offset * cos(angle)
            if (x > -1.10f && x < 1.10f && y > -1.10f && y < 1.10f) {
                return Pair(x, y)
            }
        }
        return Pair(randomRange(-1.08f, 1.08f), randomRange(-1.08f, 1.08f))
    }

    private fun apparentLuminosityMultiplier(): Float {
        val roll = random.nextFloat()
        return when {
            roll < 0.76f -> randomRange(0.36f, 0.78f)   // most stars are tiny/dim
            roll < 0.94f -> randomRange(0.78f, 1.12f)   // normal visible stars
            roll < 0.992f -> randomRange(1.12f, 1.62f)  // uncommon bright stars
            else -> randomRange(1.72f, 2.55f)           // rare standout stars/giants
        }
    }

    private fun weightedStarSize(): Float {
        val roll = random.nextFloat()
        return when {
            roll < 0.74f -> randomRange(0.34f, 0.68f)
            roll < 0.93f -> randomRange(0.68f, 1.04f)
            roll < 0.988f -> randomRange(1.04f, 1.55f)
            else -> randomRange(1.55f, 2.28f)
        }
    }

    private fun centeredNoise(): Float {
        return (random.nextFloat() + random.nextFloat() + random.nextFloat() + random.nextFloat()) * 0.5f - 1f
    }

    private fun projectX(x: Float, z: Float): Float {
        val safeZ = max(0.025f, z)
        return x / (safeZ * aspect * FIELD_OF_VIEW)
    }

    private fun projectY(y: Float, z: Float): Float {
        val safeZ = max(0.025f, z)
        return y / (safeZ * FIELD_OF_VIEW)
    }

    private fun putPoint(buffer: FloatBuffer, x: Float, y: Float, size: Float, r: Float, g: Float, b: Float, a: Float) {
        buffer.put(x)
        buffer.put(y)
        buffer.put(size)
        buffer.put(r)
        buffer.put(g)
        buffer.put(b)
        buffer.put(a)
    }

    private fun putLineVertex(buffer: FloatBuffer, x: Float, y: Float, r: Float, g: Float, b: Float, a: Float) {
        buffer.put(x)
        buffer.put(y)
        buffer.put(r)
        buffer.put(g)
        buffer.put(b)
        buffer.put(a)
    }

    private fun putNebulaQuad(
        buffer: FloatBuffer,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        uOffset: Float = 0f,
        vOffset: Float = 0f,
        uvScale: Float = 1f
    ) {
        val x0 = cx - w
        val x1 = cx + w
        val y0 = cy - h
        val y1 = cy + h
        // Keep UVs local to the quad. v0.12 offset UVs made the oval shader mask sample outside 0..1,
        // which could make nebulas disappear completely on-device. Noise movement now comes from u_Time.
        putNebulaVertex(buffer, x0, y0, 0f, 0f, r, g, b, a)
        putNebulaVertex(buffer, x1, y0, 1f, 0f, r, g, b, a)
        putNebulaVertex(buffer, x1, y1, 1f, 1f, r, g, b, a)
        putNebulaVertex(buffer, x0, y0, 0f, 0f, r, g, b, a)
        putNebulaVertex(buffer, x1, y1, 1f, 1f, r, g, b, a)
        putNebulaVertex(buffer, x0, y1, 0f, 1f, r, g, b, a)
    }

    private fun putNebulaVertex(buffer: FloatBuffer, x: Float, y: Float, u: Float, v: Float, r: Float, g: Float, b: Float, a: Float) {
        buffer.put(x)
        buffer.put(y)
        buffer.put(u)
        buffer.put(v)
        buffer.put(r)
        buffer.put(g)
        buffer.put(b)
        buffer.put(a)
    }

    private fun smooth01(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun createNoiseTexture(): Int {
        val size = NOISE_TEXTURE_SIZE
        val buffer = ByteBuffer.allocateDirect(size * size * 4)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val fx = x.toFloat() / size.toFloat()
                val fy = y.toFloat() / size.toFloat()
                val octaveA = valueNoise(fx, fy, 6, 13)
                val octaveB = valueNoise(fx, fy, 13, 47)
                val octaveC = valueNoise(fx, fy, 29, 91)
                val octaveD = valueNoise(fx, fy, 57, 141)
                val cloud = (octaveA * 0.46f + octaveB * 0.30f + octaveC * 0.17f + octaveD * 0.07f).coerceIn(0f, 1f)
                val detail = (octaveB * 0.42f + octaveC * 0.38f + octaveD * 0.20f).coerceIn(0f, 1f)
                val wisp = (octaveC * 0.55f + octaveD * 0.45f).coerceIn(0f, 1f)
                buffer.put((cloud * 255f).toInt().coerceIn(0, 255).toByte())
                buffer.put((detail * 255f).toInt().coerceIn(0, 255).toByte())
                buffer.put((wisp * 255f).toInt().coerceIn(0, 255).toByte())
                buffer.put(255.toByte())
            }
        }
        buffer.position(0)

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            size,
            size,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            buffer
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return textureId
    }

    private fun valueNoise(x: Float, y: Float, cells: Int, seed: Int): Float {
        val gx = floor(x * cells).toInt()
        val gy = floor(y * cells).toInt()
        val tx = smooth01(x * cells - gx)
        val ty = smooth01(y * cells - gy)
        val x1 = (gx + 1) % cells
        val y1 = (gy + 1) % cells
        val a = hash01(gx % cells, gy % cells, seed)
        val b = hash01(x1, gy % cells, seed)
        val c = hash01(gx % cells, y1, seed)
        val d = hash01(x1, y1, seed)
        val ab = a + (b - a) * tx
        val cd = c + (d - c) * tx
        return ab + (cd - ab) * ty
    }

    private fun hash01(x: Int, y: Int, seed: Int): Float {
        var n = x * 374761393 + y * 668265263 + seed * 1442695041
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0x00FFFFFF).toFloat() / 16777215f
    }

    private fun randomRange(minValue: Float, maxValue: Float): Float {
        return minValue + random.nextFloat() * (maxValue - minValue)
    }

    private fun newFloatBuffer(floatCount: Int): FloatBuffer {
        return ByteBuffer.allocateDirect(floatCount * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("OpenGL program link failed: $log")
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            Log.e(TAG, "Shader compile failed: $log")
            throw RuntimeException("OpenGL shader compile failed: $log")
        }
        return shader
    }
}
