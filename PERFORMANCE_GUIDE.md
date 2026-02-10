# Android Performance Analysis Guide

This guide describes how to analyze memory and processor usage for the Typing Toucan game, specifically for older Android devices.

## 1. Using the In-Game Debug Overlay

The game now includes a lightweight on-screen overlay to monitor live performance stats.

- **How to enable**: Press **F3** on a physical keyboard (Desktop build).
- **Stats displayed**:
    - **FPS**: Current frames per second. Aim for constant 60 FPS.
    - **Java Heap**: Memory used by the JVM (strings, objects, game logic).
    - **Native Heap**: Memory used by native assets (textures, sounds, mesh data).

> [!TIP]
> If you see the Java Heap steadily climbing without dropping, you might have a memory leak (objects being created every frame and not collected).

## 2. Using Android Studio Profiler

For deeper analysis, use the built-in Android Studio Profiler. This is the most accurate way to measure performance on actual hardware.

### Steps to Profile:
1.  Connect your Android device via USB.
2.  In Android Studio, click **View > Tool Windows > Profiler**.
3.  Run the game on your device.
4.  Click the **+** (plus) icon in the Profiler window and select your device/process.
5.  **Memory Profiler**:
    - Look for "Garbage Collection" events (small trash icons). Frequent GCs cause stuttering.
    - Use "Capture Heap Dump" to see what objects are taking up space.
6.  **CPU Profiler**:
    - Use "Record" to capture a trace of the code.
    - Identify "hot" methods that take a long time to execute.

## 3. General Optimization Tips for Older Devices

- **Texture Atlas**: Ensure all sprites are in a single texture atlas (already implemented). This significantly reduces draw calls.
- **Batching**: The game uses a single `SpriteBatch` pass where possible. Avoid switching between `SpriteBatch` and `ShapeRenderer` frequently.
- **Font Caching**: Use `GlyphLayout` to pre-calculate text width/height instead of measuring every frame (already implemented for most UI).
- **Object Pooling**: The game uses a `Pool` for obstacles (Necks) to avoid frequent object allocation and GC pressure.
- **Fixed Timestep**: Use a fixed timestep for physics (already implemented) to ensure consistent behavior regardless of frame rate.

> [!IMPORTANT]
> Older devices often have limited Fill Rate. Minimize large transparent overlays or unnecessary full-screen redraws if performance is still poor.
