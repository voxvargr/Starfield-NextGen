package com.sundek.starfieldnextgen.wallpaper

import android.content.SharedPreferences
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import com.sundek.starfieldnextgen.render.GlStarfieldRenderer
import com.sundek.starfieldnextgen.settings.StarfieldPrefs

class StarfieldWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = StarfieldEngine()

    private inner class StarfieldEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val prefs = StarfieldPrefs.prefs(this@StarfieldWallpaperService)
        private var renderThread: GlWallpaperThread? = null
        private var visible = false
        private var lastWidth = 1
        private var lastHeight = 1

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            startRenderThread(holder.surface)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            lastWidth = width.coerceAtLeast(1)
            lastHeight = height.coerceAtLeast(1)
            renderThread?.setSize(lastWidth, lastHeight)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            renderThread?.setPaused(!isVisible)
            if (isVisible) {
                // The preview engine and the applied home/lock wallpaper engine are separate instances.
                // Some launchers keep the applied engine alive while settings are changed in preview,
                // so force a fresh preference read whenever the real wallpaper becomes visible again.
                renderThread?.requestSettingsReload()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            stopRenderThread()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            stopRenderThread()
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            renderThread?.requestSettingsReload()
        }

        private fun startRenderThread(surface: Surface) {
            stopRenderThread()
            renderThread = GlWallpaperThread(
                appContext = this@StarfieldWallpaperService.applicationContext,
                surface = surface,
                prefs = prefs
            ).also {
                it.setSize(lastWidth, lastHeight)
                it.setPaused(!visible)
                it.start()
            }
        }

        private fun stopRenderThread() {
            val thread = renderThread ?: return
            renderThread = null
            thread.requestStop()
            try {
                thread.join(800)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private class GlWallpaperThread(
        private val appContext: android.content.Context,
        private val surface: Surface,
        private val prefs: SharedPreferences
    ) : Thread("StarfieldNextGen-OpenGL") {
        private companion object {
            const val TAG = "GlWallpaperThread"
        }

        @Volatile private var running = true
        @Volatile private var paused = true
        @Volatile private var width = 1
        @Volatile private var height = 1
        @Volatile private var settingsReloadRequested = true
        @Volatile private var resizeRequested = true

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var renderer: GlStarfieldRenderer? = null

        fun requestStop() {
            running = false
            interrupt()
        }

        fun setPaused(value: Boolean) {
            paused = value
            if (!value) settingsReloadRequested = true
            interrupt()
        }

        fun setSize(newWidth: Int, newHeight: Int) {
            width = newWidth.coerceAtLeast(1)
            height = newHeight.coerceAtLeast(1)
            resizeRequested = true
            interrupt()
        }

        fun requestSettingsReload() {
            settingsReloadRequested = true
            interrupt()
        }

        override fun run() {
            var lastFrameNs = 0L
            var lastSettingsReloadNs = 0L
            val startNs = System.nanoTime()
            try {
                initEgl()
                val currentRenderer = GlStarfieldRenderer(appContext)
                renderer = currentRenderer
                currentRenderer.init()
                currentRenderer.resize(width, height)

                while (running) {
                    if (paused || !surface.isValid) {
                        sleepQuietly(40L)
                        lastFrameNs = 0L
                        continue
                    }

                    if (resizeRequested) {
                        resizeRequested = false
                        currentRenderer.resize(width, height)
                    }

                    val beforeFrameNs = System.nanoTime()
                    if (settingsReloadRequested || beforeFrameNs - lastSettingsReloadNs > 500_000_000L) {
                        settingsReloadRequested = false
                        lastSettingsReloadNs = beforeFrameNs
                        currentRenderer.reloadSettings()
                    }

                    val frameStartNs = beforeFrameNs
                    val dt = if (lastFrameNs == 0L) 1f / 60f else (frameStartNs - lastFrameNs) / 1_000_000_000f
                    lastFrameNs = frameStartNs
                    val timeSeconds = (frameStartNs - startNs) / 1_000_000_000f

                    currentRenderer.draw(dt, timeSeconds)
                    if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
                        Log.w(TAG, "eglSwapBuffers failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
                        sleepQuietly(20L)
                    }

                    val frameElapsedMs = (System.nanoTime() - frameStartNs) / 1_000_000L
                    val delayMs = (targetFrameMs() - frameElapsedMs).coerceAtLeast(0L)
                    if (delayMs > 0L) sleepQuietly(delayMs)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "OpenGL wallpaper thread crashed", t)
            } finally {
                renderer?.release()
                renderer = null
                releaseEgl()
            }
        }

        private fun targetFrameMs(): Long {
            val fps = StarfieldPrefs.validFps(prefs.getInt(StarfieldPrefs.KEY_TARGET_FPS, StarfieldPrefs.DEFAULT_TARGET_FPS))
            return (1000L / fps).coerceAtLeast(1L)
        }

        private fun sleepQuietly(ms: Long) {
            try {
                sleep(ms)
            } catch (_: InterruptedException) {
                // Used to wake the render thread after lifecycle/settings changes.
            }
        }

        private fun initEgl() {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw RuntimeException("Unable to get EGL display")

            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                throw RuntimeException("Unable to initialize EGL: 0x${Integer.toHexString(EGL14.eglGetError())}")
            }

            val attribList = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 0,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0) || numConfigs[0] <= 0) {
                throw RuntimeException("Unable to choose EGL config: 0x${Integer.toHexString(EGL14.eglGetError())}")
            }
            val eglConfig = configs[0] ?: throw RuntimeException("EGL config was null")

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                throw RuntimeException("Unable to create EGL context: 0x${Integer.toHexString(EGL14.eglGetError())}")
            }

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                throw RuntimeException("Unable to create EGL window surface: 0x${Integer.toHexString(EGL14.eglGetError())}")
            }

            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw RuntimeException("Unable to make EGL context current: 0x${Integer.toHexString(EGL14.eglGetError())}")
            }
            EGL14.eglSwapInterval(eglDisplay, 1)
        }

        private fun releaseEgl() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            eglSurface = EGL14.EGL_NO_SURFACE
            eglContext = EGL14.EGL_NO_CONTEXT
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }
}
