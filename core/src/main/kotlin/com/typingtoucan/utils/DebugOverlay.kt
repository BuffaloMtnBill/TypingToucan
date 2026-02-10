package com.typingtoucan.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Disposable

/**
 * A lightweight overlay for displaying performance metrics such as FPS and memory usage. Useful for
 * profiling the game on various devices, especially older Android hardware.
 */
class DebugOverlay(private val font: BitmapFont) : Disposable {
    var isEnabled = false

    // Cache strings to avoid allocations during rendering.
    private var fpsStr = ""
    private var javaHeapStr = ""
    private var nativeHeapStr = ""
    private var updateTimer = 0f
    private val UPDATE_INTERVAL = 0.5f // Update stats every 0.5 seconds.

    /**
     * Renders the debug information to the screen.
     * @param batch The [SpriteBatch] to use for drawing.
     * @param x The X position (top-left).
     * @param y The Y position (starting line).
     */
    fun render(batch: SpriteBatch, x: Float, y: Float) {
        if (!isEnabled) return

        updateStats()

        val lineHeight = font.lineHeight
        font.color = Color.YELLOW
        font.draw(batch, fpsStr, x, y)
        font.draw(batch, javaHeapStr, x, y - lineHeight)
        font.draw(batch, nativeHeapStr, x, y - lineHeight * 2)
        font.color = Color.WHITE // Reset color
    }

    private fun updateStats() {
        updateTimer += Gdx.graphics.deltaTime
        if (updateTimer >= UPDATE_INTERVAL) {
            updateTimer = 0f

            // FPS.
            fpsStr = "FPS: ${Gdx.graphics.framesPerSecond}"

            // Memory (convert from bytes to megabytes).
            val javaHeap = Gdx.app.javaHeap / (1024 * 1024)
            val nativeHeap = Gdx.app.nativeHeap / (1024 * 1024)

            javaHeapStr = "Java Heap: ${javaHeap} MB"
            nativeHeapStr = "Native Heap: ${nativeHeap} MB"
        }
    }

    override fun dispose() {
        // Font is managed by GameScreen, so we don't dispose it here
    }
}
