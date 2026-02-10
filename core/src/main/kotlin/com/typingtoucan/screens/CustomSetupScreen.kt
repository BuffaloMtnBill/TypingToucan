package com.typingtoucan.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector3
import com.typingtoucan.TypingToucanGame
import com.typingtoucan.systems.CustomPoolSource
import com.typingtoucan.systems.DifficultyManager

/**
 * Screen used to configure a custom practice session where the player selects specific letters to
 * practice.
 *
 * Provides a virtual keyboard UI allowing users to toggle characters and Shift state.
 *
 * @param game The main game instance.
 */
class CustomSetupScreen(val game: TypingToucanGame) : Screen, InputProcessor {
    private val camera = OrthographicCamera().apply { setToOrtho(false, 800f, 600f) }
    private val viewport = com.badlogic.gdx.utils.viewport.ExtendViewport(800f, 600f, camera)

    private lateinit var uiFont: BitmapFont
    private val shapeRenderer = ShapeRenderer()
    private val layout = GlyphLayout()

    /** String containing all available standard keys for the grid layout. */
    private val standardKeys = "abcdefghijklmnopqrstuvwxyz1234567890"

    /** Maps characters to their corresponding visual rectangles on the screen. */
    private val keyRects = mutableMapOf<Char, Rectangle>()

    /** Set of actively selected characters (case sensitive). */
    private val selectedChars = mutableSetOf<Char>()

    private val startRect = Rectangle()
    private val backRect = Rectangle()
    private val instructionText =
            "Press a key to toggle letters.\nSHIFT: Toggle Case Mix   ENTER: Start Game"

    /** Rectangle for the Shift/Caps toggle button. */
    private val capsRect = Rectangle()

    /** Tracks the state of the Shift toggle. */
    private var isShiftActive = false
    private lateinit var instructionFont: BitmapFont

    private val SELECTED_COLOR = Color(1f, 0.906f, 0f, 1f) // #ffe700

    init {
        // Initialize fonts.
        val generator =
                FreeTypeFontGenerator(Gdx.files.internal("assets/OriginalSurfer-Regular.ttf"))
        val parameter = FreeTypeFontGenerator.FreeTypeFontParameter()
        parameter.size = 30 // Increased for mobile visibility.
        parameter.color = Color.WHITE
        parameter.borderColor = Color.BLACK
        parameter.borderWidth = 1f
        uiFont = generator.generateFont(parameter)

        val smallParam = FreeTypeFontGenerator.FreeTypeFontParameter()
        smallParam.size = 22 // Increased for mobile visibility.
        smallParam.color = Color.WHITE
        smallParam.borderColor = Color.BLACK
        smallParam.borderWidth = 1f
        instructionFont = generator.generateFont(smallParam)

        generator.dispose()

        layoutGrid()
    }

    /**
     * Calculates and sets the layout positions for all keys on the virtual keyboard grid.
     *
     * Arranges keys in standard QWERTY rows, centered on the screen. Also positions the Shift,
     * Start, and Back buttons.
     */
    private fun layoutGrid() {
        val row1 = "1234567890"
        val row2 = "qwertyuiop"
        val row3 = "asdfghjkl"
        val row4 = "zxcvbnm"

        val rows = listOf(row1, row2, row3, row4)

        val cellSize = 50f
        val gap = 10f

        // Calculate block dimensions.
        val totalHeight = rows.size * cellSize + (rows.size - 1) * gap
        var currentY = viewport.worldHeight / 2f + totalHeight / 2f

        rows.forEachIndexed { i, rowStr ->
            val rowWidth = rowStr.length * cellSize + (rowStr.length - 1) * gap
            var startX = (viewport.worldWidth - rowWidth) / 2f

            // Special case for row 4 (index 3) to position the shift button.
            if (i == 3) {
                capsRect.set(
                        startX - cellSize * 2f - gap,
                        currentY - cellSize,
                        cellSize * 2f,
                        cellSize
                )
            }

            rowStr.forEach { char ->
                keyRects[char] = Rectangle(startX, currentY - cellSize, cellSize, cellSize)
                startX += cellSize + gap
            }
            currentY -= (cellSize + gap)
        }

        // Dynamically position screen buttons.
        val btnY = 60f
        val btnW = 150f
        val btnH = 50f

        backRect.set(50f, btnY, btnW, btnH)
        startRect.set(viewport.worldWidth - btnW - 50f, btnY, btnW, btnH)
    }

    override fun show() {
        Gdx.input.inputProcessor = this
        selectedChars.clear()
        // Set projection matrix once on screen show.
        game.batch.projectionMatrix = camera.combined
        shapeRenderer.projectionMatrix = camera.combined
    }

    override fun render(delta: Float) {
        // Update shift state from the physical keyboard.
        val physicalShift =
                Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) ||
                        Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
        val enterPressed = Gdx.input.isKeyPressed(Input.Keys.ENTER)

        // Shift is active if the physical key is held or toggled via touch.
        val effectiveShift = physicalShift || isShiftActive

        Gdx.gl.glClearColor(0.2f, 0.2f, 0.3f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()
        // projectionMatrix update moved to resize() and show()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        // Draw the visual key grid.
        standardKeys.forEach { char ->
            val rect = keyRects[char]!!
            val isSelected = selectedChars.contains(char)

            if (isSelected) {
                // If shift is active, show the selected state in gold (mixed case).
                // If inactive, show as green (lowercase only).
                shapeRenderer.color = if (effectiveShift) SELECTED_COLOR else Color.GREEN
            } else {
                shapeRenderer.color = Color.DARK_GRAY
            }

            shapeRenderer.rect(rect.x, rect.y, rect.width, rect.height)
        }

        // Draw the shift/caps button.
        shapeRenderer.color = if (effectiveShift) SELECTED_COLOR else Color.DARK_GRAY
        shapeRenderer.rect(capsRect.x, capsRect.y, capsRect.width, capsRect.height)

        // Draw the start button.
        val validSelection = selectedChars.isNotEmpty()
        if (enterPressed && validSelection) {
            shapeRenderer.color = Color.WHITE
        } else {
            shapeRenderer.color = if (validSelection) SELECTED_COLOR else Color.GRAY
        }
        shapeRenderer.rect(startRect.x, startRect.y, startRect.width, startRect.height)

        // Draw the back button.
        shapeRenderer.color = Color.RED
        shapeRenderer.rect(backRect.x, backRect.y, backRect.width, backRect.height)

        // Draw the return symbol arrow on the start button if a selection is active.
        if (validSelection) {
            shapeRenderer.color = if (enterPressed) Color.BLACK else Color.WHITE
            val cx = startRect.x + startRect.width - 25f
            val cy = startRect.y + startRect.height / 2f
            val size = 10f

            shapeRenderer.rectLine(cx, cy + size, cx, cy, 2f)
            shapeRenderer.rectLine(cx, cy, cx - size, cy, 2f)
            shapeRenderer.rectLine(cx - size, cy, cx - size + 4f, cy + 4f, 2f)
            shapeRenderer.rectLine(cx - size, cy, cx - size + 4f, cy - 4f, 2f)
        }

        // Draw the CAPS arrow symbol.
        shapeRenderer.color = if (effectiveShift) Color.BLACK else Color.WHITE
        val sx = capsRect.x + capsRect.width / 2f
        val sy = capsRect.y + capsRect.height / 2f
        shapeRenderer.triangle(sx - 8f, sy - 5f, sx + 8f, sy - 5f, sx, sy + 8f)
        shapeRenderer.rectLine(sx, sy - 5f, sx, sy - 12f, 4f)

        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        game.batch.begin()

        // Instructions
        val padding = 20f
        layout.setText(
                instructionFont,
                instructionText,
                Color.WHITE,
                viewport.worldWidth - 60f,
                com.badlogic.gdx.utils.Align.center,
                true
        )
        val textY = viewport.worldHeight - padding
        instructionFont.draw(game.batch, layout, 30f, textY)

        // Draw Labels on Keys
        uiFont.color = Color.WHITE
        standardKeys.forEach { char ->
            val rect = keyRects[char]!!
            // Show Case based on toggle? Or just always uppercase?
            // "Type desired letter".
            // Let's show the case that WOULD be added?
            // If shift is active, show 'A'. If not, 'a'?
            // Request said "The keyboard..."
            // usually onscreen keyboards shift labels.
            val label = if (effectiveShift) char.uppercase() else char.toString()

            layout.setText(uiFont, label)
            uiFont.draw(
                    game.batch,
                    label,
                    rect.x + rect.width / 2 - layout.width / 2,
                    rect.y + rect.height / 2 + layout.height / 2
            )
        }

        // Caps Label (if needed, or symbol is enough)
        // Symbol drawn above.

        // Button Text
        layout.setText(uiFont, "START")
        // Shift text slightly left to make room for symbol
        uiFont.draw(
                game.batch,
                "START",
                startRect.x + startRect.width / 2 - layout.width / 2 - 10f,
                startRect.y + startRect.height / 2 + layout.height / 2
        )

        layout.setText(uiFont, "BACK")
        uiFont.draw(
                game.batch,
                "BACK",
                backRect.x + backRect.width / 2 - layout.width / 2,
                backRect.y + backRect.height / 2 + layout.height / 2
        )

        game.batch.end()
    }

    // Input processor methods.
    override fun keyTyped(character: Char): Boolean {
        // Toggle character in the selection set, normalized to lowercase.
        if (Character.isLetterOrDigit(character)) {
            toggleChar(character.lowercaseChar())
            return true
        }
        return false
    }

    override fun keyDown(keycode: Int): Boolean {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
            game.screen = MenuScreen(game)
            return true
        }

        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            tryStartGame()
            return true
        }

        if (keycode == Input.Keys.SHIFT_LEFT || keycode == Input.Keys.SHIFT_RIGHT) {
            isShiftActive = !isShiftActive
            return true
        }
        return false
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val touch = camera.unproject(Vector3(screenX.toFloat(), screenY.toFloat(), 0f))

        // Handle the shift/caps toggle.
        if (capsRect.contains(touch.x, touch.y)) {
            isShiftActive = !isShiftActive
            return true
        }

        // Start
        if (startRect.contains(touch.x, touch.y)) {
            tryStartGame()
            return true
        }

        // Handle back navigation.
        if (backRect.contains(touch.x, touch.y)) {
            game.screen = MenuScreen(game)
            return true
        }

        // Handle grid key selection.
        standardKeys.forEach { char ->
            val rect = keyRects[char]!!
            if (rect.contains(touch.x, touch.y)) {
                toggleChar(char)
            }
        }

        return false
    }

    /**
     * Toggles the selection state of a character.
     *
     * Adds the character to [selectedChars] if not present, otherwise removes it.
     *
     * @param char The character to toggle.
     */
    private fun toggleChar(char: Char) {
        if (selectedChars.contains(char)) {
            selectedChars.remove(char)
        } else {
            selectedChars.add(char)
        }
    }

    /**
     * Attempts to start the game with the current selection.
     *
     * If valid characters are selected, creates a custom typing pool based on the current selection
     * and shift state, then transitions to [GameScreen].
     */
    private fun tryStartGame() {
        if (selectedChars.isNotEmpty()) {
            // Include uppercase versions in the pool if shift is active.
            val effectiveShift =
                    isShiftActive ||
                            Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) ||
                            Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)

            val finalPool = mutableListOf<Char>()
            selectedChars.forEach { base ->
                finalPool.add(base)
                if (effectiveShift && base.isLetter()) {
                    finalPool.add(base.uppercaseChar())
                }
            }

            game.screen =
                    GameScreen(
                            game,
                            DifficultyManager.Difficulty.NORMAL,
                            isPracticeMode = true,
                            customSource = CustomPoolSource(finalPool)
                    )
        }
    }

    override fun keyUp(keycode: Int): Boolean = false
    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = false
    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean = false
    override fun mouseMoved(screenX: Int, screenY: Int): Boolean = false
    override fun scrolled(amountX: Float, amountY: Float): Boolean = false
    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean =
            false

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        camera.position.set(viewport.worldWidth / 2f, viewport.worldHeight / 2f, 0f)
        layoutGrid()

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
        uiFont.dispose()
        instructionFont.dispose()
        shapeRenderer.dispose()
    }
}
