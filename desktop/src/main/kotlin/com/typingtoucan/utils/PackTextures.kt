package com.typingtoucan.utils

import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.tools.texturepacker.TexturePacker
import java.io.File

fun main(args: Array<String>) {
    try {
        val projectRoot = System.getProperty("user.dir")
        val inputRoot = java.io.File(projectRoot, "android/assets/assets")
        val bgTilesDir = java.io.File(inputRoot, "background_tiles")
        val rawDir = java.io.File(projectRoot, "android/temp_pack_raw")
        val outputDir = java.io.File(inputRoot, "atlas")

        // Prepare raw directory
        if (rawDir.exists()) rawDir.deleteRecursively()
        rawDir.mkdirs()

        val excludes =
                listOf(
                        "background_panoramic.png",
                        "title_background.png",
                        "victory_background.png",
                        "oldground.png",
                        "credits.txt",
                        "passages.txt",
                        "literaturepassages.txt",
                        "font.ttf",
                        "OriginalSurfer-Regular.ttf",
                        "minnowbyte.mp3",
                        "music_bg.mp3",
                        "music_dark_forest.mp3",
                        "sfx_crash.mp3",
                        "sfx_error.mp3",
                        "sfx_flap.mp3",
                        "sfx_flap1.mp3",
                        "sfx_flap2.mp3",
                        "sfx_flap3.mp3",
                        "sfx_flap4.mp3",
                        "sfx_levelup.mp3",
                        "sfx_levelup_practice.mp3",
                        "sfx_monkey1.mp3",
                        "sfx_monkey2.mp3",
                        "sfx_monkey3.mp3",
                        "sfx_score.mp3",
                        "sfx_score1.mp3",
                        "sfx_score2.mp3",
                        "sfx_score3.mp3",
                        "sfx_score4.mp3",
                        "head1.xcf",
                        "head2.xcf",
                        "head3.xcf",
                        "smallmonkey.xcf"
                )

        println("Copying assets from $inputRoot to raw dir...")
        // Copy root assets (Bird, Neck, etc.)
        inputRoot.listFiles()?.forEach { file ->
            if (file.isFile && file.extension.lowercase() == "png" && !excludes.contains(file.name)
            ) {
                file.copyTo(java.io.File(rawDir, file.name))
            }
        }

        // Copy background tiles (Flattened)
        if (bgTilesDir.exists()) {
            bgTilesDir.listFiles()?.forEach { file ->
                if (file.isFile && file.extension.lowercase() == "png") {
                    file.copyTo(java.io.File(rawDir, file.name))
                }
            }
        }

        val settings = TexturePacker.Settings()
        settings.maxWidth = 2048
        settings.maxHeight = 2048
        settings.edgePadding = true
        settings.duplicatePadding = true
        settings.filterMin = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
        settings.filterMag = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
        settings.stripWhitespaceX = true
        settings.stripWhitespaceY = true

        // Ensure output dir exists
        outputDir.mkdirs()

        println("Packing textures to $outputDir...")
        TexturePacker.process(settings, rawDir.absolutePath, outputDir.absolutePath, "game")

        // Clean up
        if (rawDir.exists()) rawDir.deleteRecursively()
        println("Texture packing complete!")
    } catch (e: Exception) {
        e.printStackTrace()
        System.exit(1)
    }
}
