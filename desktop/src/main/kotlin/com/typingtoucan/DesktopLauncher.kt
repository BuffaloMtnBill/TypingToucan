package com.typingtoucan

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration

/**
 * Launches the desktop application with an LWJGL3 window at 1600×1200, VSync enabled.
 *
 * Passes [TypingToucanGame.isDebugBuild] as true so dev-only features (F3 overlay, screenshots)
 * are active on desktop.
 */
fun main() {
    val config =
            Lwjgl3ApplicationConfiguration().apply {
                setTitle("Typing Toucan")
                setWindowedMode(1600, 1200)
                useVsync(true)
                setForegroundFPS(60)
            }
    try {
        Lwjgl3Application(TypingToucanGame(isDebugBuild = true), config)
    } catch (e: Throwable) {
        println("CRITICAL ERROR: Game Crashed")
        e.printStackTrace()
    }
}
