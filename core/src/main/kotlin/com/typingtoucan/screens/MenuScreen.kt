package com.typingtoucan.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.typingtoucan.TypingToucanGame
import com.typingtoucan.systems.DifficultyManager
import com.typingtoucan.utils.SaveManager

/**
 * The primary menu screen of the game.
 *
 * Handles navigation between different game modes (Start Game, Custom Mode, Text Mode), options
 * configuration, and viewing credits.
 *
 * @param game The main game instance.
 */
class MenuScreen(val game: TypingToucanGame) : Screen, com.badlogic.gdx.InputProcessor {
    private val stage = Stage(ScreenViewport())
    private val camera = OrthographicCamera().apply { setToOrtho(false, 800f, 600f) }
    private val viewport = com.badlogic.gdx.utils.viewport.ExtendViewport(800f, 600f, camera)
    private val tempVec = com.badlogic.gdx.math.Vector3()

    /** List of main menu options. */
    private val options =
            listOf("Learn to Type", "Practice", "Arcade", "Text Typing", "Options", "Credits")
    /** List of descriptions corresponding to main menu options. */
    private val descriptions =
            listOf(
                    "Standard progression mode. Unlock keys as you go.",
                    "Practice specific keys with no penalties.",
                    "All letters unlocked, get the longest streak!",
                    "Type full paragraphs and stories.",
                    "Adjust sound and music settings.",
                    "View the game credits."
            )

    // Game assets.
    private lateinit var titleFont: BitmapFont
    private lateinit var menuFont: BitmapFont
    private lateinit var captionFont: BitmapFont
    private val shapeRenderer = ShapeRenderer()
    private val layout = GlyphLayout()

    /** List of difficulty levels available for selection. */
    private val difficultyOptions =
            listOf("Easy", "Normal", "Hard", "Insane", "", "Change Start Level")

    /** List of settings available in the options submenu. */
    private val optionsMenuItems =
            listOf("Sound", "Music", "Music Track", "Reset High Score", "Back")

    /** Reusable color object to avoid per-frame allocations. */
    private val tempColor = Color()

    /** Index of the currently selected menu item. */
    private var selectedIndex = 0
    private var isDifficultySelect = false
    private var isTextModeSelect = false
    private var isArcadeModeSelect = false
    private var isOptionsSelect = false
    private var startLevel = 1
    private val progressionString = "asdfjkl;ghqweruioptyzxcvm,./bn1234567890-!@#$%()"
    private val SELECTED_COLOR = Color(1f, 0.906f, 0f, 1f) // #ffe700.

    // Cached menu strings (Optimization #3).
    private var cachedSoundLabel = ""
    private var cachedMusicLabel = ""
    private var cachedTrackLabel = ""
    private var cachedStartLevelLabel = ""

    // Cached UI alignment (Optimization #6).
    private var centerX = 0f
    private var titleX = 0f
    private var scoreTextX = 0f
    private var promptX = 0f // For Difficulty/Options headers.
    private val mainMenuX = FloatArray(6) // Solo, Practice, Arcade, Text, Options, Credits.
    private val descriptionX = FloatArray(6)
    private val difficultyX = FloatArray(6) // Easy to Change Level.
    private val optionsX = FloatArray(5) // Sound to Back.

    // Cached GlyphLayouts (Optimization #7).
    private val titleLayout = GlyphLayout()
    private val mainMenuLayouts = Array(6) { GlyphLayout() }
    private val mainMenuSelectedLayouts = Array(6) { GlyphLayout() }
    private val descriptionLayouts = Array(6) { GlyphLayout() }
    private val difficultyLayouts = Array(6) { GlyphLayout() }
    private val difficultySelectedLayouts = Array(6) { GlyphLayout() }
    private val optionsMenuLayouts = Array(5) { GlyphLayout() }
    private val optionsMenuSelectedLayouts = Array(5) { GlyphLayout() }
    private val difficultyHeaderLayout = GlyphLayout()
    private val optionsHeaderLayout = GlyphLayout()
    private val scoreTextLayout = GlyphLayout()

    init {
        // Initialize fonts.
        val generator =
                FreeTypeFontGenerator(Gdx.files.internal("assets/OriginalSurfer-Regular.ttf"))

        val titleParam = FreeTypeFontGenerator.FreeTypeFontParameter()
        titleParam.size = 70 // Use larger size for mobile visibility.
        titleParam.color = Color.GOLD
        titleParam.borderColor = Color.BLACK
        titleParam.borderWidth = 3f
        titleFont = generator.generateFont(titleParam)

        val menuParam = FreeTypeFontGenerator.FreeTypeFontParameter()
        menuParam.size = 35 // Use larger size for mobile visibility.
        menuParam.color = Color.WHITE
        menuParam.borderColor = Color.BLACK
        menuParam.borderWidth = 2f
        menuFont = generator.generateFont(menuParam)

        val captionParam = FreeTypeFontGenerator.FreeTypeFontParameter()
        captionParam.size = 24 // Use larger size for mobile visibility.
        captionParam.color = Color.WHITE
        captionParam.borderColor = Color.BLACK
        captionParam.borderWidth = 1f
        captionFont = generator.generateFont(captionParam)

        generator.dispose()
    }

    override fun show() {
        Gdx.input.inputProcessor = this
        // Reset selection on screen show.
        selectedIndex = 0
        isDifficultySelect = false
        isOptionsSelect = false
        isArcadeModeSelect = false
        isTextModeSelect = false

        refreshDynamicLabels()
        refreshLayoutCaching()

        // Set projection matrix once on show.
        game.batch.projectionMatrix = camera.combined
        shapeRenderer.projectionMatrix = camera.combined

        // Start menu music.
        if (game.soundManager.musicEnabled) {
            game.soundManager.currentTrack =
                    com.typingtoucan.systems.SoundManager.MusicTrack.DARK_FOREST
            game.soundManager.playMusic()
        }
    }

    private fun refreshDynamicLabels() {
        val sm = game.soundManager
        cachedSoundLabel = "Sound: ${if (sm.soundEnabled) "ON" else "OFF"}"
        cachedMusicLabel = "Music: ${if (sm.musicEnabled) "ON" else "OFF"}"

        val trackName =
                if (sm.pendingTrack == com.typingtoucan.systems.SoundManager.MusicTrack.WHAT) "What"
                else "Dark Forest"
        cachedTrackLabel = "Music Track: $trackName"

        val char =
                if (startLevel <= progressionString.length) progressionString[startLevel - 1]
                else '?'
        cachedStartLevelLabel = "< Change Start Level: $startLevel - $char >"

        // Update centering and layouts for dynamic labels.
        centerX = viewport.worldWidth / 2f

        // Difficulty level selector.
        menuFont.color = Color.WHITE
        difficultyLayouts[5].setText(menuFont, cachedStartLevelLabel)
        menuFont.color = SELECTED_COLOR
        difficultySelectedLayouts[5].setText(menuFont, cachedStartLevelLabel)
        difficultyX[5] = centerX - difficultyLayouts[5].width / 2f

        // Sound label.
        menuFont.color = Color.WHITE
        optionsMenuLayouts[0].setText(menuFont, cachedSoundLabel)
        menuFont.color = SELECTED_COLOR
        optionsMenuSelectedLayouts[0].setText(menuFont, cachedSoundLabel)
        optionsX[0] = centerX - optionsMenuLayouts[0].width / 2f

        // Music label.
        menuFont.color = Color.WHITE
        optionsMenuLayouts[1].setText(menuFont, cachedSoundLabel)
        menuFont.color = SELECTED_COLOR
        optionsMenuSelectedLayouts[1].setText(menuFont, cachedMusicLabel)
        optionsX[1] = centerX - optionsMenuLayouts[1].width / 2f

        // Track label.
        menuFont.color = Color.WHITE
        optionsMenuLayouts[2].setText(menuFont, cachedTrackLabel)
        menuFont.color = SELECTED_COLOR
        optionsMenuSelectedLayouts[2].setText(menuFont, cachedTrackLabel)
        optionsX[2] = centerX - optionsMenuLayouts[2].width / 2f

        menuFont.color = Color.WHITE // Final reset.
    }

    private fun refreshLayoutCaching() {
        centerX = viewport.worldWidth / 2f

        // Title (uses its own font and color).
        titleLayout.setText(titleFont, "TYPING TOUCAN")
        titleX = centerX - titleLayout.width / 2f

        // Main menu options.
        options.forEachIndexed { index, option ->
            if (index < mainMenuLayouts.size) {
                menuFont.color = Color.WHITE
                mainMenuLayouts[index].setText(menuFont, option)
                menuFont.color = SELECTED_COLOR
                mainMenuSelectedLayouts[index].setText(menuFont, option)
                mainMenuX[index] = centerX - mainMenuLayouts[index].width / 2f
            }
        }

        // Descriptions (static white).
        descriptions.forEachIndexed { index, desc ->
            if (index < descriptionLayouts.size) {
                descriptionLayouts[index].setText(captionFont, desc)
                descriptionX[index] = centerX - descriptionLayouts[index].width / 2f
            }
        }

        // Difficulty options (normal and selected states).
        difficultyOptions.forEachIndexed { index, opt ->
            if (index < 5 && opt.isNotEmpty()) {
                menuFont.color = Color.WHITE
                difficultyLayouts[index].setText(menuFont, opt)
                menuFont.color = SELECTED_COLOR
                difficultySelectedLayouts[index].setText(menuFont, opt)
                difficultyX[index] = centerX - difficultyLayouts[index].width / 2f
            }
        }

        // Options menu items (normal and selected states).
        optionsMenuItems.forEachIndexed { index, item ->
            if (index >= 3) { // Reset High Score, Back.
                val label = if (item == "Reset High Score") "Reset High Scores" else item
                menuFont.color = Color.WHITE
                optionsMenuLayouts[index].setText(menuFont, label)
                menuFont.color = SELECTED_COLOR
                optionsMenuSelectedLayouts[index].setText(menuFont, label)
                optionsX[index] = centerX - optionsMenuLayouts[index].width / 2f
            }
        }

        // Prompt headers.
        difficultyHeaderLayout.setText(menuFont, "SELECT DIFFICULTY")
        optionsHeaderLayout.setText(menuFont, "OPTIONS")
        promptX = centerX - difficultyHeaderLayout.width / 2f

        menuFont.color = Color.WHITE // Final reset.
    }

    override fun render(delta: Float) {
        // Input is handled via InputProcessor (keyDown, touchDown).

        // Clear the screen.
        Gdx.gl.glClearColor(0.2f, 0.2f, 0.2f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()

        game.assetManager.update()
        game.batch.begin()

        if (game.assetManager.isLoaded("assets/title_background.png")) {
            val bg =
                    game.assetManager.get(
                            "assets/title_background.png",
                            com.badlogic.gdx.graphics.Texture::class.java
                    )
            game.batch.draw(bg, 0f, 0f, viewport.worldWidth, viewport.worldHeight)
        }

        // Title.
        val topY = viewport.worldHeight - 70f // Increased padding from top.
        titleFont.draw(game.batch, titleLayout, titleX, topY)

        // High score display based on the selected option.
        var scoreText = ""
        if (!isDifficultySelect && !isOptionsSelect) {
            scoreText =
                    when (selectedIndex) {
                        0 -> "Most Levels Unlocked: ${SaveManager.getNormalLevel()}"
                        1 -> "Best Streak: ${SaveManager.getCustomStreak()}"
                        2 -> "Best Streak: ${SaveManager.getArcadeStreak()}"
                        3 -> "Best Streak: ${SaveManager.getTextStreak()}"
                        else -> ""
                    }
        }

        if (scoreText.isNotEmpty()) {
            scoreTextLayout.setText(captionFont, scoreText)
            scoreTextX = centerX - scoreTextLayout.width / 2f
            captionFont.draw(game.batch, scoreTextLayout, scoreTextX, topY - 70f)
        }

        if (isDifficultySelect) {
            drawDifficultySelect()
        } else if (isOptionsSelect) {
            drawOptionsMenu()
        } else {
            drawMainMenu()
        }

        game.batch.end()
    }

    private fun drawMainMenu() {
        val centerY = viewport.worldHeight / 2f + 95f // Shifted down 0.5 lines.
        val startY = centerY
        val gap = 50f

        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            val x = if (index < mainMenuX.size) mainMenuX[index] else centerX
            val y = startY - (index * gap)

            if (index < mainMenuLayouts.size) {
                val layout =
                        if (isSelected) mainMenuSelectedLayouts[index] else mainMenuLayouts[index]
                menuFont.draw(game.batch, layout, x, y)
            } else {
                // Fallback (should not happen with size 6).
                menuFont.color = if (isSelected) SELECTED_COLOR else Color.WHITE
                menuFont.draw(game.batch, option, x, y)
            }
        }
        menuFont.color = Color.WHITE // Final color reset.

        // Draw caption description.
        if (selectedIndex in descriptions.indices) {
            val x = if (selectedIndex < descriptionX.size) descriptionX[selectedIndex] else centerX
            if (selectedIndex < descriptionLayouts.size) {
                captionFont.draw(game.batch, descriptionLayouts[selectedIndex], x, 80f)
            } else {
                captionFont.draw(game.batch, descriptions[selectedIndex], x, 80f)
            }
        }
    }

    private fun drawDifficultySelect() {
        val centerY = viewport.worldHeight / 2f + 50f
        var startY = centerY
        val gap = 50f

        menuFont.draw(game.batch, difficultyHeaderLayout, promptX, centerY + 100f)

        difficultyOptions.forEachIndexed { index, option ->
            if (option.isEmpty()) return@forEachIndexed // Skip blank lines.

            val isSelected = index == selectedIndex
            val x = if (index < difficultyX.size) difficultyX[index] else centerX
            val y = startY - (index * gap)

            if (index < difficultyLayouts.size) {
                val layout =
                        if (isSelected) difficultySelectedLayouts[index]
                        else difficultyLayouts[index]
                menuFont.draw(game.batch, layout, x, y)
            } else {
                menuFont.color = if (isSelected) SELECTED_COLOR else Color.WHITE
                menuFont.draw(game.batch, if (index == 5) cachedStartLevelLabel else option, x, y)
            }
        }
        menuFont.color = Color.WHITE
    }

    private fun drawOptionsMenu() {
        val centerY = viewport.worldHeight / 2f + 50f
        val startY = centerY
        val gap = 50f

        menuFont.draw(
                game.batch,
                optionsHeaderLayout,
                centerX - optionsHeaderLayout.width / 2f,
                centerY + 100f
        )

        optionsMenuItems.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex
            val x = if (index < optionsX.size) optionsX[index] else centerX
            val y = startY - (index * gap)

            if (index < optionsMenuLayouts.size) {
                val layout =
                        if (isSelected) optionsMenuSelectedLayouts[index]
                        else optionsMenuLayouts[index]
                menuFont.draw(game.batch, layout, x, y)
            } else {
                val label =
                        when (item) {
                            "Sound" -> cachedSoundLabel
                            "Music" -> cachedMusicLabel
                            "Music Track" -> cachedTrackLabel
                            "Reset High Score" -> "Reset High Scores"
                            else -> item
                        }
                menuFont.color = if (isSelected) SELECTED_COLOR else Color.WHITE
                menuFont.draw(game.batch, label, x, y)
            }
        }
        menuFont.color = Color.WHITE
    }

    override fun keyDown(keycode: Int): Boolean {
        val currentList =
                when {
                    isDifficultySelect -> difficultyOptions
                    isOptionsSelect -> optionsMenuItems
                    else -> options
                }

        // Keyboard navigation.
        if (keycode == Input.Keys.UP || keycode == Input.Keys.DPAD_UP || keycode == Input.Keys.W) {
            selectedIndex--
            if (selectedIndex < 0) selectedIndex = currentList.size - 1
            // Skip empty items (separators).
            if (currentList[selectedIndex].isEmpty()) selectedIndex--
            if (selectedIndex < 0) selectedIndex = currentList.size - 1

            // Skip "Change Start Level" (index 5) in text mode or arcade mode.
            if ((isTextModeSelect || isArcadeModeSelect) && isDifficultySelect && selectedIndex == 5
            ) {
                selectedIndex = 4 // Skip to the item above.
                if (selectedIndex < 0) selectedIndex = currentList.size - 1
            }
            return true
        }
        if (keycode == Input.Keys.DOWN || keycode == Input.Keys.DPAD_DOWN || keycode == Input.Keys.S
        ) {
            selectedIndex++
            if (selectedIndex >= currentList.size) selectedIndex = 0

            // Skip "Change Start Level" (index 5) in text mode or arcade mode.
            if ((isTextModeSelect || isArcadeModeSelect) && isDifficultySelect && selectedIndex == 5
            ) {
                selectedIndex = 0 // Wrap to top.
            }

            // Skip empty items (separators).
            if (currentList[selectedIndex].isEmpty()) selectedIndex++
            if (selectedIndex >= currentList.size) selectedIndex = 0
            return true
        }

        // Handle menu selection.
        if (keycode == Input.Keys.SPACE ||
                        keycode == Input.Keys.ENTER ||
                        keycode == Input.Keys.DPAD_CENTER
        ) {
            selectOption(selectedIndex)
            return true
        }

        // Handle back and escape input.
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
            if (isDifficultySelect) {
                isDifficultySelect = false
                isTextModeSelect = false
                isArcadeModeSelect = false
                selectedIndex = 0
            } else if (isOptionsSelect) {
                isOptionsSelect = false
                selectedIndex = 0
            } else {
                Gdx.app.exit()
            }
            return true
        }

        // Handle left and right input for slider options.
        if (isDifficultySelect && selectedIndex == 5) {
            if (keycode == Input.Keys.RIGHT ||
                            keycode == Input.Keys.DPAD_RIGHT ||
                            keycode == Input.Keys.D
            ) {
                startLevel = (startLevel + 1).coerceAtMost(progressionString.length)
                refreshDynamicLabels()
                return true
            }
            if (keycode == Input.Keys.LEFT ||
                            keycode == Input.Keys.DPAD_LEFT ||
                            keycode == Input.Keys.A
            ) {
                startLevel = (startLevel - 1).coerceAtLeast(1)
                refreshDynamicLabels()
                return true
            }
        }

        return false
    }

    override fun keyUp(keycode: Int): Boolean = false
    override fun keyTyped(character: Char): Boolean = false

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val currentList =
                when {
                    isDifficultySelect -> difficultyOptions
                    isOptionsSelect -> optionsMenuItems
                    else -> options
                }

        // Unproject to world coordinates using reusable vector.
        tempVec.set(screenX.toFloat(), screenY.toFloat(), 0f)
        val worldPos = camera.unproject(tempVec)

        val centerX = viewport.worldWidth / 2f
        val centerY = viewport.worldHeight / 2f + 50f
        val startY = centerY
        val gap = 50f

        // Iterate through visible options to check click bounds.
        currentList.forEachIndexed { index, option ->
            // Skip "Change Start Level" for Text Mode or Arcade Mode if it's not drawn
            if ((isTextModeSelect || isArcadeModeSelect) && isDifficultySelect && index == 5) {
                return@forEachIndexed
            }

            layout.setText(menuFont, option)
            val w = layout.width
            val h = layout.height
            val x = centerX - w / 2
            val y = startY - (index * gap)

            if (worldPos.x >= x - 20 &&
                            worldPos.x <= x + w + 20 &&
                            worldPos.y >= y - h - 10 &&
                            worldPos.y <= y + 20
            ) {
                if (isDifficultySelect && index == 5) {
                    val rx = worldPos.x - x
                    if (rx < w * 0.25f) { // Left 25%
                        startLevel = (startLevel - 1).coerceAtLeast(1)
                    } else if (rx > w * 0.75f) { // Right 25%
                        startLevel = (startLevel + 1).coerceAtMost(progressionString.length)
                    }
                    selectedIndex = index
                    refreshDynamicLabels()
                    return true
                }

                selectedIndex = index
                selectOption(index)
                return true
            }
        }
        return false
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = false
    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean = false
    override fun mouseMoved(screenX: Int, screenY: Int): Boolean = false
    override fun scrolled(amountX: Float, amountY: Float): Boolean = false
    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean =
            false

    private fun selectOption(index: Int) {
        game.soundManager.playMenuSelect()

        if (!isDifficultySelect && !isOptionsSelect) {
            // Main menu selection.
            val selectedOption = options[index]
            when (selectedOption) {
                "Learn to Type" -> {
                    isDifficultySelect = true
                    selectedIndex = 1 // Default to Normal.
                }
                "Practice" -> {
                    game.screen = CustomSetupScreen(game)
                }
                "Arcade" -> {
                    isDifficultySelect = true
                    isArcadeModeSelect = true
                    selectedIndex = 1 // Default to Normal.
                }
                "Text Typing" -> {
                    isDifficultySelect = true
                    isTextModeSelect = true
                    selectedIndex = 1 // Default to Normal.
                }
                "Options" -> {
                    isOptionsSelect = true
                    selectedIndex = 0
                }
                "Credits" -> {
                    // Try to load and display credits.
                    try {
                        val file = Gdx.files.internal("assets/credits.txt")
                        val text = file.readString()
                        // Credits file is newline separated; keep empty lines for scrolling
                        // spacing.
                        val lines = text.split("\n")
                        val items =
                                lines.map { com.typingtoucan.systems.PassageItem(it.trim(), "") }
                        val src =
                                com.typingtoucan.systems.TextSnippetSource(items, sequential = true)

                        game.screen =
                                GameScreen(
                                        game,
                                        DifficultyManager.Difficulty.INSANE,
                                        isPracticeMode = true,
                                        customSource = src,
                                        isAutoplay = true
                                )
                    } catch (e: Exception) {
                        Gdx.app.error("MenuScreen", "Failed to load credits", e)
                    }
                }
            }
        } else if (isDifficultySelect) {
            // Difficulty selection.
            if (isArcadeModeSelect) {
                val selectedDifficulty =
                        when (index) {
                            0 -> DifficultyManager.Difficulty.EASY
                            1 -> DifficultyManager.Difficulty.NORMAL
                            2 -> DifficultyManager.Difficulty.HARD
                            3 -> DifficultyManager.Difficulty.INSANE
                            else -> DifficultyManager.Difficulty.NORMAL
                        }

                // Launch arcade mode.
                val allChars = progressionString.toList()
                val source = com.typingtoucan.systems.CustomPoolSource(allChars)

                game.screen =
                        GameScreen(
                                game,
                                difficulty = selectedDifficulty,
                                isPracticeMode = false,
                                customSource = source,
                                isArcadeMode = true
                        )
            } else if (isTextModeSelect) {
                when (index) {
                    0 -> game.screen = TextSetupScreen(game, DifficultyManager.Difficulty.EASY)
                    1 -> game.screen = TextSetupScreen(game, DifficultyManager.Difficulty.NORMAL)
                    2 -> game.screen = TextSetupScreen(game, DifficultyManager.Difficulty.HARD)
                    3 -> game.screen = TextSetupScreen(game, DifficultyManager.Difficulty.INSANE)
                }
            } else {
                when (index) {
                    0 -> startGame(DifficultyManager.Difficulty.EASY)
                    1 -> startGame(DifficultyManager.Difficulty.NORMAL)
                    2 -> startGame(DifficultyManager.Difficulty.HARD)
                    3 -> startGame(DifficultyManager.Difficulty.INSANE)
                    4 -> {
                        // Separator - no action.
                    }
                    5 -> {
                        // Change Start Level (handled via Left/Right keys).
                    }
                }
            }
        } else if (isOptionsSelect) {
            val sm = game.soundManager
            when (index) {
                0 -> {
                    sm.soundEnabled = !sm.soundEnabled
                    refreshDynamicLabels()
                }
                1 -> {
                    sm.musicEnabled = !sm.musicEnabled
                    refreshDynamicLabels()
                }
                2 -> { // Toggle track.
                    sm.pendingTrack =
                            if (sm.pendingTrack ==
                                            com.typingtoucan.systems.SoundManager.MusicTrack.WHAT
                            )
                                    com.typingtoucan.systems.SoundManager.MusicTrack.DARK_FOREST
                            else com.typingtoucan.systems.SoundManager.MusicTrack.WHAT
                    refreshDynamicLabels()
                }
                3 -> {
                    // Reset high scores and play feedback sound.
                    SaveManager.resetHighScore()
                    game.soundManager.playLevelUpPractice()
                }
                4 -> { // Back.
                    isOptionsSelect = false
                    selectedIndex = 3 // Return to "Options" menu item.
                }
            }
        }
    }

    private fun startGame(difficulty: DifficultyManager.Difficulty) {
        game.screen = GameScreen(game, difficulty, startLevel = startLevel)
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        camera.position.set(viewport.worldWidth / 2f, viewport.worldHeight / 2f, 0f)
        refreshLayoutCaching()
        // Re-evaluate alignment for dynamic labels after resize.
        refreshDynamicLabels()

        game.batch.projectionMatrix = camera.combined
        shapeRenderer.projectionMatrix = camera.combined
    }
    override fun pause() {
        game.soundManager.pauseMusic()
    }
    override fun resume() {
        game.soundManager.resumeMusic()
    }
    override fun hide() {}
    override fun dispose() {
        titleFont.dispose()
        menuFont.dispose()
        captionFont.dispose()
        shapeRenderer.dispose()
    }
}
