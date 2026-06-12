# Starfield NextGen

OpenGL ES Android live wallpaper prototype.

## v0.15 nebula pass-through + binary refinement
- Smoothed near-camera nebula fade-out so close nebulas no longer pop/disappear abruptly.
- Nebulas now keep traveling slightly past the observer before they recycle.
- Close nebulas become a bit larger/softer during the pass-through to feel more volumetric.
- Refined binary star flybys with shared-center orbits, unequal mass/brightness, orbit orientation, and more related companion colors.
- Fixed preview/applied wallpaper setting sync by forcing the running home/lock engine to reload preferences when it becomes visible and by polling settings while running.
- Kept the v0.14 realism features including 12,000 max stars, 120 FPS mode, galactic plane bias, and interstellar dust.

Open in Android Studio, let Gradle sync, skip the Gradle plugin update for now, then build a debug APK.
