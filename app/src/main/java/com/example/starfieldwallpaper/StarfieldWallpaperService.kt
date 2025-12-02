package com.example.starfieldwallpaper

import android.opengl.GLSurfaceView
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.backends.android.AndroidLiveWallpaperService
import com.badlogic.gdx.backends.android.AndroidWallpaperListener
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3

class StarfieldWallpaperService : AndroidLiveWallpaperService() {
    override fun createListener(isPreview: Boolean): AndroidLiveWallpaperService.AndroidWallpaperListener {
        return object : AndroidLiveWallpaperService.AndroidWallpaperListener {
            private var starfieldApp: StarfieldApp? = null
            private var initialized = false

            override fun create() {
                val config = AndroidApplicationConfiguration().apply {
                    useAccelerometer = false
                    useCompass = false
                    useWakelock = false
                    useImmersiveMode = true
                    hideStatusBar = true
                }
                
                starfieldApp = StarfieldApp()
                initialize(starfieldApp, config)
                initialized = true
            }

            override fun resize(width: Int, height: Int) {
                if (initialized) {
                    starfieldApp?.resize(width, height)
                }
            }

            override fun render() {
                if (initialized) {
                    starfieldApp?.render()
                }
            }

            override fun pause() {
                if (initialized) {
                    starfieldApp?.pause()
                }
            }

            override fun resume() {
                if (initialized) {
                    starfieldApp?.resume()
                }
            }

            override fun dispose() {
                if (initialized) {
                    starfieldApp?.dispose()
                    initialized = false
                }
            }
        }
    }
}

class StarfieldApp : ApplicationListener {
    private lateinit var batch: SpriteBatch
    private lateinit var shader: ShaderProgram
    private var time = 0f
    private val resolution = Vector2()
    private val mouse = Vector3()
    private var lastFrameTime = 0L
    private var frameCount = 0
    private var currentFPS = 60f
    
    // Frame timing for consistent animation speed
    private val frameTimes = FloatArray(60) { 1f/60f }
    private var frameTimeIndex = 0
    
    override fun create() {
        // Load shaders
        val vertexShader = Gdx.files.internal("shaders/default.vert").readString()
        val fragmentShader = Gdx.files.internal("shaders/galaxy.frag").readString()
        
        // Create shader program
        shader = ShaderProgram(vertexShader, fragmentShader)
        if (!shader.isCompiled) {
            Gdx.app.error("Shader Error", "Shader compilation failed: ${shader.log}")
        }
        
        // Create batch with our shader
        batch = SpriteBatch(1000, shader)
        
        // Set up OpenGL
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }
    
    override fun resize(width: Int, height: Int) {
        resolution.set(width.toFloat(), height.toFloat())
        // Use integer scaling for pixel-perfect rendering
        val scale = (Gdx.graphics.density * 2).toInt()
        val scaledWidth = (width / scale) * scale
        val scaledHeight = (height / scale) * scale
        batch.projectionMatrix.setToOrtho2D(0f, 0f, scaledWidth.toFloat(), scaledHeight.toFloat())
    }
    
    private fun updateFrameTiming() {
        val now = System.nanoTime()
        val delta = (now - lastFrameTime) / 1_000_000_000f // Convert to seconds
        lastFrameTime = now
        
        // Update frame times for FPS calculation
        frameTimes[frameTimeIndex] = delta
        frameTimeIndex = (frameTimeIndex + 1) % frameTimes.size
        
        // Calculate average frame time and FPS
        val avgFrameTime = frameTimes.average().toFloat()
        currentFPS = 1f / avgFrameTime
    }
    
    override fun render() {
        updateFrameTiming()
        
        // Update time with frame-rate independent delta
        val targetFPS = when {
            currentFPS > 90 -> 120f
            currentFPS > 45 -> 60f
            else -> 30f
        }
        
        time += 1f / targetFPS
        
        // Clear screen
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        
        // Calculate mouse position (center of screen if no touch)
        val mx = if (Gdx.input.isTouched) Gdx.input.x.toFloat() else resolution.x / 2f
        val my = if (Gdx.input.isTouched) Gdx.input.y.toFloat() else resolution.y / 2f
        mouse.set(mx, resolution.y - my, 0f)
        
        // Set shader uniforms
        shader.begin()
        shader.setUniformf("time", time)
        shader.setUniformf("resolution", resolution)
        shader.setUniformf("mouse", mouse.x, mouse.y)
        shader.setUniformf("pixelDensity", Gdx.graphics.density)
        shader.end()
        
        // Draw fullscreen quad
        batch.begin()
        batch.draw(Texture(createWhitePixel()), 0f, 0f, resolution.x, resolution.y)
        batch.end()
    }
    
    private fun createWhitePixel(): Pixmap {
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.WHITE)
        pixmap.fill()
        return pixmap
    }
    
    override fun pause() {}
    override fun resume() {}
    
    override fun dispose() {
        batch.dispose()
        shader.dispose()
    }
        }
        
        private fun createRandomStar(): Star {
            val angle = random.nextFloat() * Math.PI * 2
            val distance = random.nextFloat() * 0.5f + 0.5f
            val speed = random.nextFloat() * 0.01f + 0.01f
            val size = random.nextFloat() * 3f + 1f
            val alpha = random.nextInt(155) + 100 // 100-255 alpha
            val isGalaxy = random.nextFloat() < 0.1f // 10% chance to be part of a galaxy
            
            return Star(
                angle = angle.toFloat(),
                distance = distance,
                speed = speed,
                size = size,
                alpha = alpha,
                isGalaxy = isGalaxy
            )
        }
        
        fun drawFrame(canvas: Canvas, frameTime: Long) {
            if (width != canvas.width || height != canvas.height) {
                width = canvas.width
                height = canvas.height
                centerX = width / 2f
                centerY = height / 2f
            }
            
            time += frameTime / 1000f
            
            // Clear screen
            canvas.drawColor(Color.BLACK)
            
            // Draw stars
            for (i in stars.indices) {
                val star = stars[i]
                
                // Update star position
                star.angle += star.speed * frameTime / 1000f
                if (star.angle > Math.PI * 2) {
                    star.angle -= Math.PI.toFloat() * 2
                }
                
                // Calculate star position
                val distance = star.distance * (width / 2f)
                val x = centerX + cos(star.angle.toDouble()).toFloat() * distance
                val y = centerY + sin(star.angle.toDouble()).toFloat() * distance * (width.toFloat() / height)
                
                // Draw star
                paint.alpha = star.alpha
                
                if (star.isGalaxy) {
                    // Draw galaxy particles in a spiral
                    val particles = 10
                    for (j in 0 until particles) {
                        val offset = j * 0.1f
                        val particleX = x + cos(star.angle * 5 + offset * 2) * 30f * offset
                        val particleY = y + sin(star.angle * 5 + offset * 2) * 30f * offset
                        val particleSize = star.size * (1 - offset * 0.5f)
                        canvas.drawCircle(particleX, particleY, particleSize, paint)
                    }
                } else {
                    // Draw regular star
                    canvas.drawCircle(x, y, star.size, paint)
                    
                    // Add a subtle glow effect
                    if (star.size > 2f) {
                        paint.alpha = (star.alpha * 0.5f).toInt()
                        canvas.drawCircle(x, y, star.size * 1.5f, paint)
                        paint.alpha = star.alpha
                    }
                }
            }
        }
    }
    
    private data class Star(
        var angle: Float,
        val distance: Float,
        val speed: Float,
        val size: Float,
        val alpha: Int,
        val isGalaxy: Boolean = false
    )
}
