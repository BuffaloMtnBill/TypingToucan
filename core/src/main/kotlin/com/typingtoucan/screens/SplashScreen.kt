package com.typingtoucan.screens

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.typingtoucan.TypingToucanGame

/**
 * Initial screen displayed when the application starts.
 *
 * Shows the MinnowByte logo and an optional keyboard instruction screen on mobile devices. Each
 * splash screen is displayed for a fixed duration before transitioning to the main menu.
 *
 * @param game The main game instance.
 */
class SplashScreen(val game: TypingToucanGame) : Screen {
    private val camera = OrthographicCamera().apply { setToOrtho(false, 800f, 600f) }
    private val viewport = ExtendViewport(800f, 600f, camera)

    private var timer = 0f
    private var stage = 0 // 0: MinnowByte, 1: Keyboard (if applicable)

    private val logoTextures = mutableListOf<com.badlogic.gdx.graphics.g2d.TextureRegion>()
    private var keyboardTexture: com.badlogic.gdx.graphics.g2d.TextureRegion? = null
    private var splashSoundPlayed = false

    private val TOTAL_LOGO_DURATION = 2.75f

    private val isMobile =
            Gdx.app.type == Application.ApplicationType.Android ||
                    Gdx.app.type == Application.ApplicationType.iOS

    override fun show() {
        // Assets are queued in TypingToucanGame, including the atlas.
        // We just wait for them.
        game.batch.projectionMatrix = camera.combined
    }

    override fun render(delta: Float) {
        if (splashSoundPlayed) {
            timer += delta
        }
        game.assetManager.update() // Continue loading game assets in background

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // Play splash sound as soon as it's loaded
        if (!splashSoundPlayed &&
                        game.assetManager.isLoaded(
                                "assets/minnowbyte.mp3",
                                com.badlogic.gdx.audio.Music::class.java
                        )
        ) {
            splashSoundPlayed = true
            game.soundManager.refreshAssets() // Refresh references without re-running init logic
            game.soundManager.playSplash()
        }

        // Check if atlas and required textures are loaded
        if (!game.assetManager.isLoaded("assets/atlas/game.atlas")) return
        if (!game.assetManager.isLoaded("assets/logo1.png")) return
        if (!game.assetManager.isLoaded("assets/logo2.png")) return
        if (!game.assetManager.isLoaded("assets/logo3.png")) return

        // Assign once
        if (logoTextures.isEmpty()) {
            val textures = listOf("assets/logo1.png", "assets/logo2.png", "assets/logo3.png")
            textures.forEach { path ->
                val tex = game.assetManager.get(path, com.badlogic.gdx.graphics.Texture::class.java)
                logoTextures.add(com.badlogic.gdx.graphics.g2d.TextureRegion(tex))
            }

            if (isMobile) {
                val atlas =
                        game.assetManager.get(
                                "assets/atlas/game.atlas",
                                com.badlogic.gdx.graphics.g2d.TextureAtlas::class.java
                        )
                keyboardTexture = atlas.findRegion("keyboard")
            }
        }

        viewport.apply()
        game.batch.begin()

        if (stage == 0) {
            val logoIndex =
                    when {
                        timer < 0.75f -> 0
                        timer < 1.50f -> 1
                        else -> 2
                    }
            val currentLogo = logoTextures[logoIndex]

            // Scale and center logo based on viewport height to avoid squishing
            val textureWidth = currentLogo.regionWidth.toFloat()
            val textureHeight = currentLogo.regionHeight.toFloat()
            val scale = viewport.worldHeight / textureHeight
            val drawWidth = textureWidth * scale
            val drawX = (viewport.worldWidth - drawWidth) / 2f

            game.batch.draw(currentLogo, drawX, 0f, drawWidth, viewport.worldHeight)

            if (timer >= TOTAL_LOGO_DURATION) {
                timer = 0f
                if (isMobile) {
                    stage = 1
                } else if (game.assetManager.isFinished) {
                    finish()
                }
            }
        } else if (stage == 1) {
            // Scale and center Keyboard screen based on viewport height
            keyboardTexture?.let {
                val textureWidth = it.regionWidth.toFloat()
                val textureHeight = it.regionHeight.toFloat()
                val scale = viewport.worldHeight / textureHeight
                val drawWidth = textureWidth * scale
                val drawX = (viewport.worldWidth - drawWidth) / 2f

                game.batch.draw(it, drawX, 0f, drawWidth, viewport.worldHeight)
            }

            if (timer >= 5.0f && game.assetManager.isFinished) {
                finish()
            }
        }

        game.batch.end()
    }

    /**
     * Transitions to the menu screen. In a production scenario, we might wait for
     * assetManager.progress == 1f here, but the MenuScreen already handles partial asset loading
     * gracefully.
     */
    private fun finish() {
        game.soundManager.stopSplash()
        game.soundManager.init(game.assetManager)
        game.screen = MenuScreen(game)
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        game.batch.projectionMatrix = camera.combined
    }

    override fun pause() {}

    override fun resume() {}

    override fun hide() {
        dispose()
    }

    override fun dispose() {
        // Managed by AssetManager
    }
}
