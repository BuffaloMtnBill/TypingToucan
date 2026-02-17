package com.typingtoucan.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.ScreenUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Utility for taking and saving screenshots. */
object ScreenshotFactory {

    /**
     * Captures the current frame buffer and saves it as a PNG file. The file is saved in the
     * working directory (project root on Desktop).
     */
    fun saveScreenshot() {
        try {
            // Get frame buffer as Pixmap
            val pixmap =
                    ScreenUtils.getFrameBufferPixmap(
                            0,
                            0,
                            Gdx.graphics.backBufferWidth,
                            Gdx.graphics.backBufferHeight
                    )

            // Flip it because frame buffer is upside down relative to PNG
            val flipped = flipPixmap(pixmap)

            // Generate filename with timestamp
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val filename = "screenshot_${dateFormat.format(Date())}.png"
            val file = Gdx.files.local(filename)

            // Save
            PixmapIO.writePNG(file, flipped)

            // Cleanup
            pixmap.dispose()
            flipped.dispose()

            Gdx.app.log("ScreenshotFactory", "Saved screenshot to ${file.file().absolutePath}")
        } catch (e: Exception) {
            Gdx.app.error("ScreenshotFactory", "Failed to take screenshot", e)
        }
    }

    private fun flipPixmap(src: Pixmap): Pixmap {
        val width = src.width
        val height = src.height
        val flipped = Pixmap(width, height, src.format)

        for (x in 0 until width) {
            for (y in 0 until height) {
                flipped.drawPixel(x, y, src.getPixel(x, height - y - 1))
            }
        }
        return flipped
    }
}
