package com.typingtoucan.screens

// Removed ShapeRenderer.
import com.badlogic.gdx.*
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Animation
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.math.Intersector
import com.typingtoucan.TypingToucanGame
import com.typingtoucan.entities.Bird
import com.typingtoucan.entities.Neck
import com.typingtoucan.systems.DifficultyManager
import com.typingtoucan.utils.DebugOverlay
import com.typingtoucan.utils.SaveManager
import com.typingtoucan.utils.ScreenshotFactory

/**
 * The core gameplay screen.
 *
 * Orchestrates the game loop, including entity updates ([Bird], [Neck]), typing queue processing,
 * rendering, and collision detection.
 *
 * @param game The main game instance.
 * @param difficulty The chosen [DifficultyManager.Difficulty] level.
 * @param isPracticeMode Whether the game is running in practice mode (no death).
 * @param customSource Optional custom text source for typing content.
 * @param isAutoplay Whether the game is running in autoplay mode (AI types for you).
 * @param startLevel The level from which gameplay starts.
 * @param isArcadeMode Whether the game is in arcade mode.
 */
class GameScreen(
        private val game: TypingToucanGame,
        var difficulty: DifficultyManager.Difficulty = DifficultyManager.Difficulty.NORMAL,
        private val isPracticeMode: Boolean = false,
        private val customSource: com.typingtoucan.systems.TypingSource? = null,
        private val isAutoplay: Boolean = false,
        private val startLevel: Int = 1,
        private val isArcadeMode: Boolean = false
) : Screen, InputProcessor {

    companion object {
        const val ENABLE_SCREENSHOTS = true
    }

    private val camera = OrthographicCamera().apply { setToOrtho(false, 800f, 600f) }
    private val viewport = com.badlogic.gdx.utils.viewport.ExtendViewport(800f, 600f, camera)

    // Font assets.
    private lateinit var uiFont: BitmapFont
    private lateinit var queueFont: BitmapFont
    private val layout = GlyphLayout()
    private lateinit var smallFont: BitmapFont
    private lateinit var shadowFont: BitmapFont
    private lateinit var debugOverlay: DebugOverlay

    // Graphics assets.
    private val backgroundTiles = mutableListOf<TextureRegion>()
    private lateinit var groundTexture: TextureRegion
    private lateinit var victoryTexture: TextureRegion
    private lateinit var monkeyTextures: List<TextureRegion>
    private lateinit var currentMonkeyTexture: TextureRegion

    // Ground animation state.
    private lateinit var groundAnimTextures: List<TextureRegion>
    private lateinit var groundAnimation: Animation<TextureRegion>
    private var activeGroundIndex = 0
    private var groundTimer = 0f
    private var lastTextLineIndex = -1
    private var textScrollTimer = 0f
    private val SCROLL_DURATION = 0.5f

    private enum class GroundState {
        IDLE,
        ANIMATING
    }

    // Static obstacles (giraffe: 50x300, anaconda: wide).
    private lateinit var giraffeObstacles: Array<TextureRegion>
    private lateinit var anacondaObstacles: Array<TextureRegion>

    // Toucan assets.
    private lateinit var toucanPainTexture: TextureRegion

    // Entity animation.
    private val birdAnimation: Animation<TextureRegion>
    /** The accumulated state time used for animation frame selection. */
    private var stateTime = 0f

    // Game state.
    private lateinit var whitePixel: TextureRegion
    private val bird = Bird(90f, 400f)
    private val neckPool =
            object : com.badlogic.gdx.utils.Pool<Neck>() {
                override fun newObject(): Neck = Neck()
            }
    private val necks = com.badlogic.gdx.utils.Array<Neck>()
    private val typingQueue =
            com.typingtoucan.systems.TypingQueue(
                    customSource ?: com.typingtoucan.systems.ClassicSource()
            )
    private var diffManager = DifficultyManager(difficulty)

    private enum class PauseState {
        NONE,
        MAIN,
        AUDIO,
        VICTORY,
        EXIT_CONFIRM
    }
    private var pauseState: PauseState = PauseState.NONE

    private val mainMenuItems =
            if (customSource is com.typingtoucan.systems.CustomPoolSource && !isArcadeMode) {
                listOf("Resume", "Difficulty", "Audio", "Letter Selection Menu", "Main Menu")
            } else if (customSource is com.typingtoucan.systems.TextSnippetSource || isArcadeMode) {
                listOf("Resume", "Difficulty", "Audio", "Main Menu")
            } else {
                listOf("Resume", "Difficulty", "Capitals", "Audio", "Main Menu")
            }
    private val audioMenuItems = listOf("Sound", "Music", "Music Track", "Back")
    private val exitConfirmItems = listOf("Yes", "No")
    private var menuSelectedIndex = 0
    private val soundManager = game.soundManager

    private var neckTimer = 0f
    private var nextNeckInterval = diffManager.neckInterval // Initialize with default.

    private val SELECTED_COLOR = Color(1f, 0.906f, 0f, 1f) // #ffe700
    private val HOT_PINK = Color(1f, 0.41f, 0.71f, 1f) // #ff69b4
    private var score = 0
    private var progressionPoints = 0
    private var displayProgression = 0f // Fluid animation value.
    private var gameStarted = false
    private var hurtTimer = 0f // Timer for red flash effect.
    private var flashTimer = 0f // Timer for green "level up" flash.
    private var justUnlockedChar = ""
    private var milestoneTimer = 0f // Timer for milestone "pulse" effect.
    private var textAnimTimer = 1f
    private var lastLineIndex = -1
    private var level = startLevel
    private var totalNecksSpawned = 0

    /** Timer for controlling the AI's typing speed in autoplay/credits mode. */
    private var autoplayTimer = 0f

    /** Cooldown timer for the AI bird's flap action. */
    private var aiFlapCooldown = 0f

    // Kinetic queue animation (Optimization #11).
    private var queueSlideTimer = 0f
    private val QUEUE_SLIDE_DURATION = 0.2f
    private var queueSlideT = 0f

    // Weight flash.
    private var weightFlashValue = 0
    private var weightFlashTimer = 0f

    // Pulse animations.
    private var cachedHighScoreStr = ""
    private var cachedStreakStr = ""

    // Input limiting
    private var inputProcessedThisFrame = false
    private val inputDebugHistory = com.badlogic.gdx.utils.Array<String>()
    private var pulse5 = 0f
    private var pulse10 = 0f

    // Random cycle pool (Optimization #8).
    private val rng = com.badlogic.gdx.math.RandomXS128()
    private val randomPool = FloatArray(256) { rng.nextFloat() }
    private var randomPoolIndex = 0

    /** Returns the next pre-calculated random float [0, 1). */
    private fun nextRand(): Float {
        val r = randomPool[randomPoolIndex]
        randomPoolIndex = (randomPoolIndex + 1) and 255 // Fast bitwise mask for size 256.
        return r
    }

    /** Returns a random integer in [0, range). */
    private fun nextInt(range: Int): Int = (nextRand() * range).toInt()

    // Interpolation scalars (Optimization #9).
    private var hurtAlpha = 0f
    private var progressRatio = 0f
    private var statusBarPulse = 1f
    private var isStatusBarFlash = false
    private var textScrollT = 0f
    private var milestonePulse = 0f
    private var milestoneColorT = 0.5f
    private var unlockFlashPulse = 0f
    private var underscoreAlpha = 0f

    // Scrolling background state.
    private var backgroundX = 0f
    private var groundX = 0f
    private var bgTotalWidth = 0f
    private var bgBaseTileW = 800f
    private var currentBgIndex = 0
    private var nextBgIndex = 0

    // Practice mode stats.
    private var streak = 0
    private var maxStreak = 0

    // High score display.
    private var topHighScore = 0

    // Monkey decoration.
    private var monkeyX = 500f
    private var monkeyPassed = false

    // Physics synchronization.
    private var physicsAccumulator = 0f
    private val PHYSICS_STEP = 1f / 60f

    // Input handling.
    /** The last character typed by the user, used for debouncing. */
    private var lastTypedChar: Char = ' '
    /** The last character correctly typed, used for the green flash effect. */
    private var lastCorrectChar: Char = ' '

    /** Timestamp of the last key press to prevent double-firing on some Android devices. */
    private var lastTypedTime: Long = 0

    // Cached draw strings.
    private val queueSb = StringBuilder(100)
    private var cachedLevelStr = startLevel.toString()
    private var cachedWeightStr = ""
    private val descenders = listOf("g", "j", "p", "q", "y") // Static allocation.
    private val singleCharSb = StringBuilder(1) // Optimization #11.

    // Performance caches.
    private val tempVec = com.badlogic.gdx.math.Vector3() // Reusable Vector3.
    private val tempColor = Color() // Reusable color object.
    private val queueLayout = GlyphLayout()
    private val firstCharLayout = GlyphLayout()
    private val highScoreLayout = GlyphLayout()
    private val streakLayout = GlyphLayout()
    private val levelLayout = GlyphLayout()
    private var cachedMarkupLine = "" // For Text Mode.
    private var lastLocalProgress = -1 // For Text Mode.

    // Cached UI Alignment (Optimization #6).
    private var practiceModeX = 0f
    private var escLabelX = 0f
    private var highLabelX = 0f
    private var scoreLabelX = 0f
    private var highScoreValueX = 0f
    private var levelStreakLabelX = 0f
    private var bottomValueX = 0f
    private var pauseTitleX = 0f
    private var justUnlockedCharX = 0f

    // Tiling & scaling caches.
    private var bgTileScale = 0f
    private var numGroundTiles = 0
    private var groundW = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var hudX = 0f
    private var yOffsetQueue = 0f
    private var yOffsetStart = 0f
    private val monkeyWidths = mutableMapOf<TextureRegion, Float>()

    private val practiceModeLayout = GlyphLayout()
    private val startTextLayout = GlyphLayout()
    private val escTextLayout = GlyphLayout()
    private val victoryTextLayout = GlyphLayout()
    private val victorySubTextLayout = GlyphLayout()

    // HUD label cache.
    private val highLabelLayout = GlyphLayout()
    private val scoreLabelLayout = GlyphLayout()
    private val levelStreakLabelLayout = GlyphLayout()
    private val escLabelLayout = GlyphLayout()
    private var cachedLevelLabel = ""
    private var hudLabelX = 0f
    private var topLabelY = 0f
    private var topLabel2Y = 0f
    private var topValueY = 0f
    private var bottomLabelY = 0f
    private var bottomValueY = 0f

    init {
        Gdx.input.inputProcessor = this

        // Initialize font.
        val generator =
                FreeTypeFontGenerator(Gdx.files.internal("assets/OriginalSurfer-Regular.ttf"))
        val parameter = FreeTypeFontGenerator.FreeTypeFontParameter()
        parameter.size = 26 // Increased for mobile.
        parameter.color = Color.WHITE
        parameter.borderColor = Color.BLACK
        parameter.borderWidth = 2f
        uiFont = generator.generateFont(parameter)

        // Create large font for queue display.
        val queueParam = FreeTypeFontGenerator.FreeTypeFontParameter()
        queueParam.size = 70 // Increased for mobile.
        queueParam.color = Color.WHITE // Will be updated dynamically.
        queueParam.borderColor = Color.BLACK
        queueParam.borderWidth = 4f // Thicker border.
        queueFont = generator.generateFont(queueParam)

        // Create smaller font for "LEVEL" label.
        val labelParam = FreeTypeFontGenerator.FreeTypeFontParameter()
        labelParam.size = 18 // Increased for mobile.
        labelParam.color = Color.WHITE
        labelParam.borderColor = Color.BLACK
        labelParam.borderWidth = 1f
        smallFont = generator.generateFont(labelParam)

        // Create shadow font (size 50).
        val shadowParam = FreeTypeFontGenerator.FreeTypeFontParameter()
        shadowParam.size = 50
        shadowParam.color = Color.WHITE
        shadowParam.borderColor = Color.BLACK
        shadowParam.borderWidth = 2f
        shadowFont = generator.generateFont(shadowParam)

        generator.dispose()

        // Initialize white pixel (Optimization #2).
        val pixmap =
                com.badlogic.gdx.graphics.Pixmap(
                        1,
                        1,
                        com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888
                )
        pixmap.setColor(Color.WHITE)
        pixmap.fill()
        whitePixel = TextureRegion(Texture(pixmap))
        pixmap.dispose()

        // Retrieve assets from atlas.
        val atlas =
                game.assetManager.get(
                        "assets/atlas/game.atlas",
                        com.badlogic.gdx.graphics.g2d.TextureAtlas::class.java
                )

        // Load background tiles from standalone files.
        backgroundTiles.clear()
        for (i in 0..9) {
            val tex =
                    game.assetManager.get(
                            "assets/background_tiles/tile_$i.png",
                            Texture::class.java
                    )
            backgroundTiles.add(TextureRegion(tex))
        }
        groundTexture = atlas.findRegion("ground")

        // Load and convert large textures to regions.
        val victoryTex =
                game.assetManager.get(
                        "assets/victory_background.png",
                        com.badlogic.gdx.graphics.Texture::class.java
                )
        victoryTexture = TextureRegion(victoryTex)

        val groundFrames = com.badlogic.gdx.utils.Array<TextureRegion>()
        val anim1 = atlas.findRegion("ground_anim", 1)
        val anim2 = atlas.findRegion("ground_anim", 2)
        groundAnimTextures = listOf(anim1, anim2)
        groundFrames.add(anim1)
        groundFrames.add(anim2)
        groundAnimation = Animation(0.8f, groundFrames, Animation.PlayMode.LOOP)

        monkeyTextures =
                listOf(
                        atlas.findRegion("banana_monkey"),
                        atlas.findRegion("banana_monkey", 1),
                        atlas.findRegion("banana_monkey", 2),
                        atlas.findRegion("banana_monkey", 3),
                        atlas.findRegion("young_monkey"),
                        atlas.findRegion("young_monkey", 1),
                        atlas.findRegion("young_monkey", 2),
                        atlas.findRegion("young_monkey", 3),
                        atlas.findRegion("old_monkey"),
                        atlas.findRegion("old_monkey", 1),
                        atlas.findRegion("old_monkey", 2),
                        atlas.findRegion("old_monkey", 3)
                )
        currentMonkeyTexture = monkeyTextures.random()

        giraffeObstacles = Array(5) { i -> atlas.findRegion("giraffe${i + 1}") }
        anacondaObstacles = Array(2) { i -> atlas.findRegion("anaconda_long", i) }
        toucanPainTexture = atlas.findRegion("toucan_pain")

        // Setup bird animation.
        val birdRegions = com.badlogic.gdx.utils.Array<TextureRegion>(4)
        for (i in 0 until 4) {
            birdRegions.add(atlas.findRegion("toucan", i))
        }
        birdAnimation = Animation(0.1f, birdRegions, Animation.PlayMode.LOOP)

        // Pre-cache monkey widths (scaled to 150f height).
        monkeyTextures.forEach { region ->
            val scale = 150f / region.regionHeight.toFloat()
            monkeyWidths[region] = region.regionWidth * scale
        }

        updateQueueString()

        // Apply initial difficulty gravity and flap strength
        bird.gravity = diffManager.gravity
        bird.flapStrength = diffManager.flapStrength

        // Initial monkey position for first run.
        val scale = 150f / currentMonkeyTexture.regionHeight.toFloat()
        val mWidth = currentMonkeyTexture.regionWidth * scale
        val distanceToSpawn = diffManager.scrollSpeed * nextNeckInterval
        monkeyX = viewport.worldWidth + distanceToSpawn - 90f - mWidth
        monkeyPassed = false

        // Initialize performance caches.
        practiceModeLayout.setText(uiFont, "Practice Mode - Obstacles Disabled")
        startTextLayout.setText(queueFont, "TYPE TO START")
        escTextLayout.setText(uiFont, "Press ESC for Menu")
        victoryTextLayout.setText(queueFont, "VICTORY!")
        victorySubTextLayout.setText(uiFont, "Press ENTER to Return to Menu")

        // Sync capitals preference.
        typingQueue.setCapitalsEnabled(com.typingtoucan.utils.SaveManager.loadCapitalsEnabled())

        // Fast forward level in learning mode.
        if (startLevel > 1) {
            repeat(startLevel - 1) { typingQueue.expandPool() }
        }

        // Initialize high scores.
        if (isArcadeMode) {
            topHighScore = com.typingtoucan.utils.SaveManager.getArcadeStreak()
            maxStreak = topHighScore
        } else if (isPracticeMode) {
            if (customSource is com.typingtoucan.systems.CustomPoolSource) {
                topHighScore = com.typingtoucan.utils.SaveManager.getCustomStreak()
            } else {
                topHighScore = com.typingtoucan.utils.SaveManager.getTextStreak()
            }
            maxStreak = topHighScore // Initialize session max to global max.
        } else {
            topHighScore = com.typingtoucan.utils.SaveManager.getNormalLevel()
        }
        cachedHighScoreStr = topHighScore.toString()
        cachedStreakStr = streak.toString()
        highScoreLayout.setText(queueFont, cachedHighScoreStr) // Scales managed separately in draw.
        streakLayout.setText(queueFont, cachedStreakStr)

        // Initialize debug overlay.
        debugOverlay = DebugOverlay(smallFont)
    }

    private fun updateQueueString() {
        queueSb.setLength(0)
        typingQueue.appendTo(queueSb, "   ")
    }

    override fun show() {
        Gdx.input.inputProcessor = this
        updateQueueString()
        refreshHudCaching()

        // Set projection matrix once on show.
        game.batch.projectionMatrix = camera.combined

        // Switch to game music but don't play yet (wait for gameStarted).
        // Apply fixed 'What' track for gameplay.
        game.soundManager.updateTrack(com.typingtoucan.systems.SoundManager.MusicTrack.WHAT)
        game.soundManager.stopMusic()
    }

    /**
     * Handles the end-of-game logic.
     *
     * Plays sound effects, calculates penalties/scores, and initiates a soft reset.
     */
    private fun endGame() {
        try {
            soundManager.playCrash()
            // Penalize queue on crash.
            if (typingQueue.isNotEmpty()) {
                val activeChar = typingQueue.first()
                typingQueue.onCrash(activeChar)
            }

            if (!isPracticeMode) {
                // Calculate effective level (penalize skipped levels).
                val effectiveLevel = level - (startLevel - 1)
                if (effectiveLevel > 0) {
                    com.typingtoucan.utils.SaveManager.saveNormalLevel(effectiveLevel)
                }
            }
        } catch (e: Exception) {
            Gdx.app.error("GameScreen", "Error during endGame", e)
        } finally {
            softReset()
        }
    }

    override fun render(delta: Float) {
        inputProcessedThisFrame = false // Reset input limit for this frame

        // Input handling (Pause/Back polling for reliability).
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) ||
                        Gdx.input.isKeyJustPressed(Input.Keys.BACK)
        ) {
            if (pauseState == PauseState.NONE) {
                pause()
            } else if (pauseState == PauseState.VICTORY) {
                game.soundManager.stopMusic()
                game.screen = MenuScreen(game)
            }
            // Other states (MAIN, AUDIO, EXIT_CONFIRM) are handled via event-based
            // handlePauseMenuInput.
        }

        // Screenshot Trigger (Desktop Only)
        if (ENABLE_SCREENSHOTS && Gdx.app.type == Application.ApplicationType.Desktop) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.P) &&
                            (Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) ||
                                    Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT))
            ) {
                ScreenshotFactory.saveScreenshot()
            }
        }

        // Handle enter for victory screen.
        if (pauseState == PauseState.VICTORY &&
                        (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) ||
                                Gdx.input.isKeyJustPressed(Input.Keys.SPACE))
        ) {
            game.screen = MenuScreen(game)
        }

        if (gameStarted && pauseState == PauseState.NONE) {
            update(delta)
        } else {
            // Auto-start for credits mode.
            if (!gameStarted && isAutoplay && pauseState == PauseState.NONE) {
                // Determine start character (first in queue).
                if (typingQueue.isNotEmpty()) {
                    val startChar = typingQueue.first()
                    // Simulate key press.
                    keyTyped(startChar)
                }
            }

            // If paused or ready state, animate bird in place.
            if (pauseState == PauseState.NONE) {
                // Ready state (type to start).
                stateTime += delta
                pulse5 = kotlin.math.sin(stateTime * 5f)
                pulse10 = kotlin.math.sin(stateTime * 10f)
                underscoreAlpha = 0.5f + 0.5f * pulse10
                bird.y = 300f + pulse5 * 10f
                bird.velocity = 0f
            }
        }

        draw()
    }

    /**
     * Updates the game simulation.
     *
     * Handles timer updates, physics stepping (fixed timestep), and input processing.
     *
     * @param delta The time elapsed since the last frame in seconds.
     */
    private fun update(delta: Float) {
        // Handle autoplay AI.
        if (isAutoplay) {
            autoplayTimer -= delta
            if (autoplayTimer <= 0) {
                autoplayTimer = 0.11f // Approx 9 chars/sec.
                if (typingQueue.isNotEmpty()) {
                    keyTyped(typingQueue.first())
                }
            }
        }

        stateTime += delta

        // Update hurt timer.
        if (hurtTimer > 0) {
            hurtTimer -= delta
            if (hurtTimer < 0) hurtTimer = 0f
        }

        // Update flash timer.
        if (weightFlashTimer > 0) {
            weightFlashTimer -= delta * 3f // Flash speed.
            if (weightFlashTimer < 0) weightFlashTimer = 0f
        }

        // Stepping physics with accumulator.
        physicsAccumulator += delta.coerceAtMost(0.25f)
        while (physicsAccumulator >= PHYSICS_STEP - 0.0001f) {
            updatePhysics(PHYSICS_STEP)
            physicsAccumulator -= PHYSICS_STEP
        }
        if (physicsAccumulator < 0) physicsAccumulator = 0f

        // Consolidate pulse math (Optimization #10).
        pulse5 = kotlin.math.sin(stateTime * 5f)
        pulse10 = kotlin.math.sin(stateTime * 10f)

        // Kinetic queue animation (Optimization #11).
        if (queueSlideTimer > 0) {
            queueSlideTimer -= delta
            if (queueSlideTimer < 0) queueSlideTimer = 0f
        }
        queueSlideT = (queueSlideTimer / QUEUE_SLIDE_DURATION).coerceIn(0f, 1f)

        // Transition math consolidation (Optimization #9).
        hurtAlpha = (hurtTimer / 0.3f).coerceIn(0f, 1f)
        progressRatio = (displayProgression / 5f).coerceIn(0f, 1f)

        val flashSinStep = kotlin.math.sin(flashTimer * 10f)
        statusBarPulse = 1.0f + 0.6f * kotlin.math.abs(flashSinStep)
        isStatusBarFlash = (flashTimer * 10).toInt() % 2 == 0

        textScrollT = (textScrollTimer / SCROLL_DURATION).coerceIn(0f, 1f)

        milestonePulse = kotlin.math.abs(pulse5)
        milestoneColorT = (pulse5 + 1f) / 2f
        unlockFlashPulse = kotlin.math.abs(flashSinStep) // Reuses sin from flashTimer.
        underscoreAlpha = 0.5f + 0.5f * pulse10
    }

    /**
     * Performs a single physics step.
     *
     * Updates entity positions, handles collision detection, and manages level progression.
     *
     * @param dt The fixed timestep for physics calculations (usually 1/60s).
     */
    private fun updatePhysics(dt: Float) {
        if (gameStarted && isAutoplay) {
            // Simple Flap AI.
            // Find target pipe gap.
            var targetY = viewport.worldHeight / 2f
            // Look for closest pipe ahead.
            for (i in 0 until necks.size) {
                val neck = necks[i]
                if (neck.x + neck.width > bird.x) {
                    // Target the lower part of the gap to allow for the flap arc.
                    targetY = neck.gapCenterY - 140f
                    break
                }
            }

            // Flap logic for AI.
            if (bird.y < targetY) {
                if (aiFlapCooldown <= 0 &&
                                (bird.velocity < 0 || (bird.y < targetY - 60 && bird.velocity < 3f))
                ) {
                    bird.flap()
                }
            }
            if (aiFlapCooldown > 0) aiFlapCooldown -= dt
        }

        bird.update(dt)

        // Ground animation logic (random frame every 1-3s).
        groundTimer -= dt
        if (groundTimer <= 0) {
            groundTimer = 1f + nextRand() * 2f

            val oldIndex = activeGroundIndex
            var newIndex = oldIndex
            while (newIndex == oldIndex) {
                newIndex = nextInt(3)
            }
            activeGroundIndex = newIndex
        }
        if (flashTimer > 0) {
            flashTimer -= dt
            if (flashTimer < 0) flashTimer = 0f
        }
        if (milestoneTimer > 0) {
            milestoneTimer -= dt
            if (milestoneTimer < 0) milestoneTimer = 0f
        }

        // Fluid progress bar update.
        val targetProgression = if (flashTimer > 0) 5f else progressionPoints.toFloat()
        displayProgression += (targetProgression - displayProgression) * 5f * dt

        // Scroll background and movement.
        backgroundX -= 10f * dt
        groundX -= diffManager.scrollSpeed * dt
        monkeyX -= diffManager.scrollSpeed * dt

        // Monkey sound logic.
        if (!monkeyPassed && monkeyX < bird.x) {
            monkeyPassed = true
            soundManager.playMonkey()
        }

        if (backgroundX <= -bgTotalWidth) {
            backgroundX += bgTotalWidth
        }
        if (groundX <= -groundW) groundX = 0f

        // Neck spawning logic.
        neckTimer += dt
        if (neckTimer >= nextNeckInterval) {
            neckTimer = 0f
            // Calculate next interval with variance.
            val base = diffManager.neckInterval
            val variance = 0.7f + nextRand() * 0.6f
            nextNeckInterval = base * variance

            // Spawn neck offscreen.
            val minGapY = 150f
            val maxGapY = viewport.worldHeight - 150f
            val gapY = minGapY + nextRand() * (maxGapY - minGapY)

            val isSnake = nextRand() < 0.125f // 1/8 chance.
            val neck = neckPool.obtain()
            neck.init(viewport.worldWidth + 50f, gapY, isSnake)
            neck.currentTexture =
                    if (isSnake) {
                        anacondaObstacles[neck.headIndex % 2]
                    } else {
                        giraffeObstacles[neck.headIndex]
                    }
            necks.add(neck)
            totalNecksSpawned++
        }

        for (i in necks.size - 1 downTo 0) {
            val neck = necks[i]
            neck.update(dt, diffManager.scrollSpeed)

            if (neck.x + neck.width < 0) {
                neckPool.free(neck)
                necks.removeIndex(i)
                continue
            }

            if (!neck.scored && neck.x < bird.x) {
                neck.scored = true
                score++
                soundManager.playScore()
                progressionPoints++
                if (progressionPoints >= 5) {
                    var didLevelUp = false

                    if (typingQueue.isFullyUnlocked()) {
                        // Max level reached: continue leveling endlessly.
                        didLevelUp = true
                        justUnlockedChar = "MAX"
                    } else {
                        val unlockedChars = typingQueue.expandPool()
                        if (unlockedChars.isNotEmpty()) {
                            didLevelUp = true
                            // Handle display text.
                            if (unlockedChars.size > 1) {
                                // Bulk unlock (e.g. capitals).
                                val first = unlockedChars.first()
                                val last = unlockedChars.last()
                                justUnlockedChar = "$first-$last"
                            } else {
                                // Single unlock.
                                justUnlockedChar = unlockedChars.first().toString()
                            }

                            // Check for milestone (only during progression).
                            if (level % 5 == 0) {
                                milestoneTimer = 2.0f // Pulse for 2 seconds.
                            }
                        }
                    }

                    if (didLevelUp) {
                        level++
                        if (!isPracticeMode) {
                            val effectiveLevel = level - (startLevel - 1)
                            if (effectiveLevel > topHighScore) {
                                topHighScore = effectiveLevel
                                cachedHighScoreStr = topHighScore.toString()
                                highScoreLayout.setText(queueFont, cachedHighScoreStr)
                                highScoreValueX = hudLabelX - highScoreLayout.width / 2f
                                com.typingtoucan.utils.SaveManager.saveNormalLevel(effectiveLevel)
                            }
                        }
                        cachedLevelStr = level.toString()
                        levelLayout.setText(queueFont, cachedLevelStr)
                        bottomValueX = hudLabelX - levelLayout.width / 2f
                        flashTimer = 1.0f // Flash green for 1.0 second.
                        soundManager.playLevelUp()
                    }
                    progressionPoints = 0
                }
            }

            if (!neck.collided &&
                            (Intersector.overlaps(bird.bounds, neck.bottomBounds) ||
                                    Intersector.overlaps(bird.bounds, neck.topBounds))
            ) {
                if (!isPracticeMode || customSource is com.typingtoucan.systems.TextSnippetSource) {
                    neck.collided = true
                    soundManager.playCrash()
                    hurtTimer = 0.5f // Flash red and show pain sprite

                    if (isArcadeMode || isPracticeMode || !isPracticeMode) {
                        // All modes now bounce/recoil on pipe hits in some way.
                        // Arcade/Practice reset streak, Normal resets progression.
                        if (com.badlogic.gdx.math.Intersector.overlaps(bird.bounds, neck.topBounds)
                        ) {
                            bird.velocity = -5f // Push Down (hit ceiling/top pipe)
                        } else {
                            bird.velocity = 7f // Push Up (hit floor/bottom pipe)
                        }

                        if (isArcadeMode || isPracticeMode) {
                            // Reset streak on hit
                            streak = 0
                            cachedStreakStr = "0"
                            if (isArcadeMode) {
                                com.typingtoucan.utils.SaveManager.saveArcadeStreak(0)
                            }
                            refreshHudCaching()
                        } else {
                            // Normal mode: Reset progression bar.
                            progressionPoints = 0
                        }
                    }
                }
            }
        }

        if (bird.bounds.y <= 60) { // Screen height for ground is 60.
            soundManager.playCrash()
            if (isArcadeMode) {
                // Arcade Mode: Transition to pause state on ground crash.
                softReset()
            } else if (isPracticeMode) {
                if (customSource is com.typingtoucan.systems.TextSnippetSource) {
                    // Text Mode: Soft Reset (Type to Start) behavior.
                    softReset()
                } else {
                    // Standard Practice Mode: Bounce.
                    streak = 0
                    cachedStreakStr = "0"
                    refreshHudCaching()
                    bird.y = 80f // Reset slightly above ground.
                    bird.velocity = 5f // Small bounce.
                }
            } else {
                endGame() // Normal Mode.
            }
        }
    }

    /** Updates HUD alignment and layouts based on cached values. */
    private fun refreshHudCaching() {
        val centerX = viewport.worldWidth / 2f

        // Static HUD alignments.
        practiceModeX = centerX - practiceModeLayout.width / 2f
        escLabelX = centerX - escLabelLayout.width / 2f
        highLabelX = hudLabelX - highLabelLayout.width / 2f
        scoreLabelX = hudLabelX - scoreLabelLayout.width / 2f
        levelStreakLabelX = hudLabelX - levelStreakLabelLayout.width / 2f

        // Dynamic HUD alignments. Update layouts from cache to ensure width/content is correct.
        highScoreLayout.setText(queueFont, cachedHighScoreStr)
        if (isPracticeMode || isArcadeMode) {
            streakLayout.setText(queueFont, cachedStreakStr)
        } else {
            levelLayout.setText(queueFont, cachedLevelStr)
        }

        // We'll calculate highScoreValueX based on squeezed width in draw(),
        // but let's keep a standard fallback here.
        highScoreValueX = hudLabelX - highScoreLayout.width / 2f
        val bottomLayout = if (isPracticeMode || isArcadeMode) streakLayout else levelLayout
        bottomValueX = hudLabelX - bottomLayout.width / 2f
    }

    /** Resets the game state for a new run or practice session. */
    private fun softReset() {
        gameStarted = false
        progressionPoints = 0
        score = 0

        // Reset streak logic.
        if (isPracticeMode || isArcadeMode) {
            streak = 0
            cachedStreakStr = "0"
            if (isArcadeMode) {
                com.typingtoucan.utils.SaveManager.saveArcadeStreak(0)
            }
            streakLayout.setText(queueFont, cachedStreakStr)
            refreshHudCaching()
        }

        bird.x = 100f
        bird.y = 300f
        bird.velocity = 0f

        neckPool.freeAll(necks)
        necks.clear()
        hurtTimer = 0f
        flashTimer = 0f

        // Reset neck logic for deterministic spawn.
        neckTimer = 0f
        nextNeckInterval = diffManager.neckInterval

        // Position monkey: Screen end + distance to spawn - 90 buffer - monkey width.
        currentMonkeyTexture = monkeyTextures[nextInt(monkeyTextures.size)]
        val scale = 150f / currentMonkeyTexture.regionHeight.toFloat()
        val mWidth = currentMonkeyTexture.regionWidth * scale
        val distanceToSpawn = diffManager.scrollSpeed * nextNeckInterval
        monkeyX = viewport.worldWidth + distanceToSpawn - 90f - mWidth
        monkeyPassed = false

        updateQueueString() // Ensure queue is visually synced.
    }

    /**
     * Renders the game frame using a consolidated graphics pipeline (Optimization #4).
     *
     * 1. Clear Screen
     * 2. Begin Batch
     * 3. Draw World (Background, Necks, Bird)
     * 4. Draw UI (Status bars, text)
     * 5. End Batch
     */
    private fun draw() {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.3f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()

        // Single batch pass (Optimization #2).
        game.batch.begin()

        // 1. Draw tiled background.
        val worldH = viewport.worldHeight
        val tileScale = worldH / 1200f // Base height of tiles is 1200.
        val baseTileW = 1024f * tileScale
        val totalBgWidth = baseTileW * backgroundTiles.size

        for (loop in 0..1) {
            val loopOffset = loop * totalBgWidth
            for (index in 0 until backgroundTiles.size) {
                val tile = backgroundTiles[index]
                val tileX = backgroundX + loopOffset + (index * baseTileW)
                if (tileX + baseTileW > 0 && tileX < viewport.worldWidth) {
                    game.batch.draw(tile, tileX, 0f, baseTileW, worldH)
                }
            }
        }

        // 2. Draw neck entities.
        for (i in 0 until necks.size) {
            val neck = necks[i]
            neck.render(game.batch)
        }

        // 3. Draw tiled ground.
        val groundW = groundTexture.regionWidth.toFloat()
        val numGroundTiles = kotlin.math.ceil(viewport.worldWidth / groundW).toInt() + 1
        val textureToDraw =
                when (activeGroundIndex) {
                    0 -> groundTexture
                    1 -> groundAnimTextures[0]
                    else -> groundAnimTextures[1]
                }
        for (i in 0 until numGroundTiles) {
            game.batch.draw(textureToDraw, groundX + i * groundW, 0f, groundW, 70f)
        }

        // 4. Draw monkey decoration.
        if (monkeyX > -200) {
            val scale = 150f / currentMonkeyTexture.regionHeight.toFloat()
            val width = currentMonkeyTexture.regionWidth * scale
            game.batch.draw(currentMonkeyTexture, monkeyX, 40f, width, 150f)
        }

        // 5. Draw bird entity.
        val currentFrame =
                if (hurtTimer > 0) {
                    toucanPainTexture
                } else {
                    birdAnimation.getKeyFrame(stateTime, true)
                }
        bird.render(game.batch, currentFrame)

        // Tinted shapes block (Optimization #2).
        // Using whitePixel with batch.setColor to simulate shapes to avoid batch flushes.

        // 6. Draw hurt flash overlay.
        if (hurtAlpha > 0) {
            tempColor.set(1f, 0f, 0f, 0.5f * hurtAlpha)
            game.batch.color = tempColor
            game.batch.draw(whitePixel, 0f, 0f, viewport.worldWidth, viewport.worldHeight)
        }

        if (!isPracticeMode) {
            // 7. Draw status bar.
            var barWidth = 200f
            var barHeight = 24f
            var barX = viewport.worldWidth / 2f - barWidth / 2f
            var barY = viewport.worldHeight - 70f
            var fillColor = Color.CYAN

            if (flashTimer > 0) {
                barWidth *= statusBarPulse
                barHeight *= statusBarPulse
                barX = centerX - barWidth / 2f
                barY = viewport.worldHeight - 50f - barHeight / 2f

                fillColor = if (isStatusBarFlash) Color.GREEN else Color.GOLD
            }

            tempColor.set(Color.DARK_GRAY).lerp(Color.GOLD, progressRatio)

            // Draw border.
            val borderThickness = 4f
            game.batch.color = tempColor
            game.batch.draw(
                    whitePixel,
                    barX - borderThickness,
                    barY - borderThickness,
                    barWidth + borderThickness * 2,
                    barHeight + borderThickness * 2
            )

            if (flashTimer > 0) {
                game.batch.color = fillColor
                game.batch.draw(whitePixel, barX, barY, barWidth, barHeight)
            } else {
                game.batch.color = Color.DARK_GRAY
                game.batch.draw(whitePixel, barX, barY, barWidth, barHeight)

                game.batch.color = fillColor
                val fillWidth = progressRatio * barWidth
                game.batch.draw(whitePixel, barX, barY, fillWidth, barHeight)
            }
        }

        // 8. Draw pause menu overlay background.
        if (pauseState != PauseState.NONE) {
            game.batch.color = tempColor.set(0f, 0f, 0f, 0.7f)
            game.batch.draw(whitePixel, 0f, 0f, viewport.worldWidth, viewport.worldHeight)
        }

        // Reset batch color to white for sprites and text.
        game.batch.color = Color.WHITE

        // Handle Text Snippet Mode glow.
        val src = customSource
        if (pauseState == PauseState.NONE && src is com.typingtoucan.systems.TextSnippetSource) {
            val state_tm = src.getDisplayState()
            layout.setText(queueFont, state_tm.currentLine)
            val fullWidth = layout.width
            val startX = viewport.worldWidth / 2f - fullWidth / 2f
            // Calculate typed width using range to avoid .take() allocation.
            layout.setText(
                    queueFont,
                    state_tm.currentLine,
                    0,
                    state_tm.localProgress,
                    queueFont.color,
                    0f,
                    com.badlogic.gdx.utils.Align.left,
                    false,
                    null
            )
            val typedWidth = layout.width
            val cursorX = startX + typedWidth

            var charWidth = 20f
            if (state_tm.localProgress < state_tm.currentLine.length) {
                // Calculate current char width using range to avoid .toString() allocation.
                layout.setText(
                        queueFont,
                        state_tm.currentLine,
                        state_tm.localProgress,
                        state_tm.localProgress + 1,
                        queueFont.color,
                        0f,
                        com.badlogic.gdx.utils.Align.left,
                        false,
                        null
                )
                charWidth = layout.width
            }
            if (charWidth < 10f) charWidth = 15f

            // Draw glow effect.
            val alpha = 0.6f + 0.4f * pulse10
            val underlineY = (viewport.worldHeight / 2f + 50f) - 70f

            tempColor.set(HOT_PINK)
            tempColor.a = alpha
            game.batch.color = tempColor
            game.batch.draw(whitePixel, cursorX, underlineY, charWidth, 4f)
            tempColor.a = alpha * 0.5f
            game.batch.color = tempColor
            game.batch.draw(whitePixel, cursorX - 2f, underlineY - 2f, charWidth + 4f, 8f)
            game.batch.color = Color.WHITE
        }

        // Text and UI rendering.

        // 10. Draw Text Snippet Mode text (Optimization #5).
        if (pauseState == PauseState.NONE &&
                        customSource is com.typingtoucan.systems.TextSnippetSource
        ) {
            val src_tm = customSource
            val state_tm = src_tm.getDisplayState()

            // Update text scrolling timer.
            if (lastTextLineIndex != -1 && state_tm.lineIndex != lastTextLineIndex) {
                textScrollTimer = SCROLL_DURATION
            }
            lastTextLineIndex = state_tm.lineIndex

            if (textScrollTimer > 0f) {
                textScrollTimer -= Gdx.graphics.deltaTime
                if (textScrollTimer < 0f) textScrollTimer = 0f
            }

            // Interpolation factor (0 to 1) for scrolling animation.
            val textScrollT = textScrollTimer / SCROLL_DURATION
            val centerYTM = viewport.worldHeight / 2f + 50f
            val gap = 110f
            val aboveY = centerYTM + gap
            val belowY = centerYTM - gap
            val animOffset = -gap * textScrollT

            // 1. Draw previous line (fading out and moving up).
            if (textScrollT > 0f && state_tm.prevLine.isNotEmpty()) {
                layout.setText(queueFont, state_tm.prevLine)
                val w = layout.width
                val x = viewport.worldWidth / 2f - w / 2f
                val y = aboveY + animOffset

                queueFont.color = tempColor.set(1f, 1f, 1f, textScrollT)
                queueFont.draw(game.batch, state_tm.prevLine, x, y)
            }

            // 2. Draw current line (moving up from below).
            layout.setText(queueFont, state_tm.currentLine)
            val fullWidth = layout.width
            val startX = viewport.worldWidth / 2f - fullWidth / 2f
            val currentY = centerYTM + animOffset

            // Draw typed portion in green.
            if (state_tm.localProgress > 0) {
                queueFont.color = Color.GREEN
                queueFont.draw(
                        game.batch,
                        state_tm.currentLine,
                        startX,
                        currentY,
                        0,
                        state_tm.localProgress,
                        0f,
                        com.badlogic.gdx.utils.Align.left,
                        false
                )
            }

            // Measure width for offset.
            layout.setText(
                    queueFont,
                    state_tm.currentLine,
                    0,
                    state_tm.localProgress,
                    queueFont.color,
                    0f,
                    com.badlogic.gdx.utils.Align.left,
                    false,
                    null
            )
            val typedW = layout.width

            queueFont.color = Color.WHITE
            if (state_tm.localProgress < state_tm.currentLine.length) {
                queueFont.draw(
                        game.batch,
                        state_tm.currentLine,
                        startX + typedW,
                        currentY,
                        state_tm.localProgress,
                        state_tm.currentLine.length,
                        0f,
                        com.badlogic.gdx.utils.Align.left,
                        false
                )
            }

            // 3. Next Line (Shadow, Moving Up)
            val nextY = belowY + animOffset

            if (state_tm.nextLine.isNotEmpty()) {
                layout.setText(shadowFont, state_tm.nextLine)
                val w = layout.width
                val x = viewport.worldWidth / 2f - w / 2f

                shadowFont.color = tempColor.set(0.5f, 0.5f, 0.5f, 0.5f) // Shadow gray
                shadowFont.draw(game.batch, state_tm.nextLine, x, nextY)
                shadowFont.color = Color.WHITE
            }
            queueFont.color = Color.WHITE
        }

        // 11. Practice Mode UI
        if (isPracticeMode && customSource !is com.typingtoucan.systems.TextSnippetSource) {
            uiFont.draw(game.batch, practiceModeLayout, practiceModeX, viewport.worldHeight - 20f)
            smallFont.draw(game.batch, escLabelLayout, escLabelX, viewport.worldHeight - 50f)
        }

        // 12. Level / Streak
        if (!isAutoplay) {
            // High Score Label: Split into two lines.
            smallFont.draw(game.batch, highLabelLayout, highLabelX, topLabelY)
            smallFont.draw(game.batch, scoreLabelLayout, scoreLabelX, topLabel2Y)

            // High Score Value: Squeezed digits (Optimization #12).
            queueFont.data.setScale(0.5f)
            drawSqueezedText(game.batch, queueFont, cachedHighScoreStr, hudLabelX, topValueY, -2f)
            queueFont.data.setScale(1.0f)

            // Level/Streak Label
            smallFont.draw(game.batch, levelStreakLabelLayout, levelStreakLabelX, bottomLabelY)

            // Value
            if (milestoneTimer > 0) {
                queueFont.data.setScale(1.0f + 0.4f * milestonePulse)
                tempColor.set(Color.GOLD).lerp(Color.CYAN, milestoneColorT)
                queueFont.color = tempColor
            }

            // Using cached layouts with squeezed logic.
            val bottomVal = if (isPracticeMode || isArcadeMode) cachedStreakStr else cachedLevelStr
            drawSqueezedText(game.batch, queueFont, bottomVal, hudLabelX, 70f, -5f)

            queueFont.color = Color.WHITE
            queueFont.data.setScale(1.0f)
        }

        // 13. Draw unlocked character flash.
        if (flashTimer > 0) {
            layout.setText(queueFont, justUnlockedChar)
            justUnlockedCharX = viewport.worldWidth / 2f - layout.width / 2f
            queueFont.data.setScale(1.0f + 0.5f * unlockFlashPulse)
            queueFont.draw(
                    game.batch,
                    justUnlockedChar,
                    justUnlockedCharX,
                    viewport.worldHeight - 80f
            )
            queueFont.data.setScale(1.0f)
        }

        // 14. Draw pause menu.
        if (pauseState != PauseState.NONE) {
            val menuTitle =
                    if (pauseState == PauseState.EXIT_CONFIRM) "Exit to main menu. Are you sure?"
                    else "PAUSED"
            layout.setText(uiFont, menuTitle)
            pauseTitleX = viewport.worldWidth / 2f - layout.width / 2f
            uiFont.draw(game.batch, menuTitle, pauseTitleX, viewport.worldHeight - 100f)

            if (pauseState == PauseState.MAIN) drawPauseMainMenu()
            else if (pauseState == PauseState.AUDIO) drawAudioMenu()
            else if (pauseState == PauseState.EXIT_CONFIRM) {
                drawExitConfirmMenu()
            }
        } else if (!gameStarted) {
            queueFont.draw(
                    game.batch,
                    startTextLayout,
                    centerX - startTextLayout.width / 2,
                    yOffsetStart
            )
            uiFont.draw(game.batch, escTextLayout, centerX - escTextLayout.width / 2, 30f)
        }

        // 15. Draw typing queue text.
        if (pauseState == PauseState.NONE &&
                        customSource !is com.typingtoucan.systems.TextSnippetSource
        ) {
            val totalTextY = viewport.worldHeight / 2f + viewport.worldHeight * 0.1f
            val sepWidth = 50f // "   " separator width.

            if (weightFlashTimer > 0 && lastCorrectChar != ' ') {
                // Seamless kinetic animation (Optimization #11).

                // 1. Calculate the final stationary position (Standby State).
                layout.setText(queueFont, queueSb)
                val targetX = viewport.worldWidth / 2f - layout.width / 2f

                // 2. Measure the "jump distance" (width of character + gap).
                layout.setText(queueFont, lastCorrectChar.toString())
                val ghostW = layout.width
                val jumpDistance = ghostW + sepWidth
                val slideOffset = jumpDistance * queueSlideT

                // 3. Draw stationary ghost character with fading green underscore.
                val expandFactor = 1.0f + (1.0f - weightFlashTimer) * 0.5f
                val baseUnderW = if (ghostW < 10f) 15f else ghostW
                val baseUnderH = 4f

                val flashUnderW = baseUnderW * expandFactor
                val flashUnderH = baseUnderH * expandFactor
                val flashUnderX = targetX + (ghostW - flashUnderW) / 2f
                val flashUnderY = totalTextY - 70f - (flashUnderH - baseUnderH) / 2f

                tempColor.set(Color.GREEN)
                tempColor.a = weightFlashTimer
                game.batch.color = tempColor
                game.batch.draw(whitePixel, flashUnderX, flashUnderY, flashUnderW, flashUnderH)

                // Draw secondary glow layer.
                tempColor.a = weightFlashTimer * 0.5f
                game.batch.color = tempColor
                game.batch.draw(
                        whitePixel,
                        flashUnderX - 2f,
                        flashUnderY - 2f,
                        flashUnderW + 4f,
                        flashUnderH + 4f
                )

                game.batch.color = Color.WHITE

                // Draw the main ghost character.
                queueFont.data.setScale(1.0f)
                tempColor.set(Color.WHITE).lerp(Color.GREEN, weightFlashTimer)
                tempColor.a = weightFlashTimer
                queueFont.color = tempColor

                // Use singleCharSb to avoid toString() allocation.
                singleCharSb.setLength(0)
                singleCharSb.append(lastCorrectChar)
                queueFont.draw(game.batch, singleCharSb, targetX, totalTextY)

                // 4. Draw the sliding queue block.
                queueFont.data.setScale(1.0f)
                val shiftedLength = minOf(8, queueSb.length)

                if (shiftedLength > 0) {
                    queueFont.color = Color.WHITE
                    queueFont.draw(
                            game.batch,
                            queueSb,
                            targetX + slideOffset,
                            totalTextY,
                            0,
                            shiftedLength,
                            0f,
                            com.badlogic.gdx.utils.Align.left,
                            false
                    )
                }
            } else {
                // Standard centered queue logic.
                layout.setText(queueFont, queueSb)
                val startX = viewport.worldWidth / 2f - layout.width / 2f
                queueFont.color = Color.WHITE
                queueFont.draw(game.batch, queueSb, startX, totalTextY)

                // Highlight underscore on the first letter.
                if (queueSb.isNotEmpty()) {
                    // Use range-based layout to avoid toString() allocation.
                    firstCharLayout.setText(
                            queueFont,
                            queueSb,
                            0,
                            1,
                            queueFont.color,
                            0f,
                            com.badlogic.gdx.utils.Align.left,
                            false,
                            null
                    )
                    val charW = if (firstCharLayout.width < 10f) 15f else firstCharLayout.width
                    val alpha = underscoreAlpha
                    tempColor.set(HOT_PINK).set(HOT_PINK.r, HOT_PINK.g, HOT_PINK.b, alpha)
                    game.batch.color = tempColor
                    game.batch.draw(whitePixel, startX, totalTextY - 70f, charW, 4f)
                    tempColor.a = alpha * 0.5f
                    game.batch.color = tempColor
                    game.batch.draw(whitePixel, startX - 2f, totalTextY - 72f, charW + 4f, 8f)
                    game.batch.color = Color.WHITE
                }
            }
        }

        // 16. Draw victory screen.
        if (pauseState == PauseState.VICTORY) {
            game.batch.draw(victoryTexture, 0f, 0f, viewport.worldWidth, viewport.worldHeight)

            queueFont.draw(
                    game.batch,
                    victoryTextLayout,
                    centerX - victoryTextLayout.width / 2,
                    viewport.worldHeight - 100f
            )
            uiFont.draw(
                    game.batch,
                    victorySubTextLayout,
                    centerX - victorySubTextLayout.width / 2,
                    50f
            )
        }

        // 17. Draw debug overlay.
        debugOverlay.render(game.batch, 10f, viewport.worldHeight - 10f)

        // 18. Draw Input Latency Debug (Temporary) - DISABLED
        // Debugging code for checking Android input intervals.
        // var debugY = viewport.worldHeight - 110f
        // for (i in 0 until inputDebugHistory.size) {
        //     smallFont.draw(game.batch, inputDebugHistory[i], 10f, debugY)
        //     debugY -= 20f
        // }

        game.batch.end()
    }

    /**
     * Draws text with tightened character spacing.
     *
     * @param batch The batch to draw with.
     * @param font The font to use.
     * @param text The string to render.
     * @param centerX The horizontal center position for the text block.
     * @param y The vertical position.
     * @param charSpacing The pixel offset to apply between characters (can be negative).
     */
    private fun drawSqueezedText(
            batch: com.badlogic.gdx.graphics.g2d.SpriteBatch,
            font: BitmapFont,
            text: String,
            centerX: Float,
            y: Float,
            charSpacing: Float
    ) {
        var totalWidth = 0f
        for (i in 0 until text.length) {
            val glyph = font.data.getGlyph(text[i]) ?: continue
            totalWidth += glyph.width * font.data.scaleX + charSpacing
        }
        if (text.isNotEmpty()) totalWidth -= charSpacing

        var currentX = centerX - totalWidth / 2f
        for (i in 0 until text.length) {
            val glyph = font.data.getGlyph(text[i]) ?: continue
            // Range-based draw avoids String allocation.
            font.draw(
                    batch,
                    text,
                    currentX,
                    y,
                    i,
                    i + 1,
                    0f,
                    com.badlogic.gdx.utils.Align.left,
                    false
            )
            currentX += glyph.width * font.data.scaleX + charSpacing
        }
    }

    override fun keyDown(keycode: Int): Boolean {
        if (pauseState == PauseState.VICTORY) {
            if (keycode == Input.Keys.ENTER ||
                            keycode == Input.Keys.SPACE ||
                            keycode == Input.Keys.ESCAPE ||
                            keycode == Input.Keys.BACK
            ) {
                game.soundManager.stopMusic()
                game.screen = MenuScreen(game)
                return true
            }
        }

        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
            if (pauseState == PauseState.NONE) {
                pause()
            } else if (pauseState == PauseState.AUDIO) {
                pauseState = PauseState.MAIN
            } else if (pauseState == PauseState.EXIT_CONFIRM) {
                pauseState = PauseState.MAIN
            } else {
                pauseState = PauseState.NONE
                game.soundManager.resumeMusic()
            }
            return true
        }

        if (keycode == Input.Keys.F3) {
            debugOverlay.isEnabled = !debugOverlay.isEnabled
            return true
        }

        if (pauseState != PauseState.NONE) {
            handlePauseMenuInput(keycode)
            return true
        }

        return false
    }

    override fun keyUp(keycode: Int): Boolean = false

    override fun keyTyped(character: Char): Boolean {
        // Android/LibGDX fix: Filter out null and carriage return characters.
        if (character.code == 0 || character == '\r') return false

        // Debounce: Ignore duplicate keyTyped events from some Android devices.
        val currentTime = com.badlogic.gdx.utils.TimeUtils.millis()
        val delta = currentTime - lastTypedTime

        // Record debug info regardless of acceptance - DISABLED
        // Debugging code for checking Android input intervals.
        // if (inputDebugHistory.size > 5) inputDebugHistory.removeIndex(0)
        // inputDebugHistory.add("Char: '$character' (${character.code}) | dt: ${delta}ms")

        val debounceThreshold =
                if (com.badlogic.gdx.Gdx.app.type ==
                                com.badlogic.gdx.Application.ApplicationType.Android
                )
                        200
                else 0
        if (character == lastTypedChar && delta < debounceThreshold) {
            return false
        }

        lastTypedChar = character
        lastTypedTime = currentTime

        if (pauseState != PauseState.NONE) return false
        val result = typingQueue.handleInput(character, updateWeights = !isPracticeMode)
        if (result != null) {
            if (!gameStarted) {
                gameStarted = true
                soundManager.playMusic()
            }

            // Text mode fix: Ensure cursor advances in practice mode.
            if (isPracticeMode && customSource is com.typingtoucan.systems.TextSnippetSource) {
                customSource.onCharTyped(character)
            }

            // Sync visual state.
            updateQueueString()
            lastCorrectChar = character
            weightFlashValue = result
            cachedWeightStr = weightFlashValue.toString()

            weightFlashTimer = 1.0f
            queueSlideTimer = QUEUE_SLIDE_DURATION

            // Only flap if not in autoplay mode.
            if (!isAutoplay) {
                bird.flap()
                soundManager.playFlap()
            }

            if (isPracticeMode || isArcadeMode) {
                streak++
                cachedStreakStr = streak.toString()
                streakLayout.setText(queueFont, cachedStreakStr)
                bottomValueX = hudLabelX - streakLayout.width / 2f

                if (!isAutoplay && streak > topHighScore) {
                    topHighScore = streak
                    cachedHighScoreStr = topHighScore.toString()
                    highScoreLayout.setText(queueFont, cachedHighScoreStr)

                    if (isArcadeMode) {
                        com.typingtoucan.utils.SaveManager.saveArcadeStreak(streak)
                    } else if (customSource is com.typingtoucan.systems.CustomPoolSource) {
                        com.typingtoucan.utils.SaveManager.saveCustomStreak(streak)
                    } else {
                        com.typingtoucan.utils.SaveManager.saveTextStreak(streak)
                    }
                }
                if (streak > maxStreak) maxStreak = streak

                // Milestone flash every 10 streaks.
                if (streak % 10 == 0 && !isAutoplay) {
                    soundManager.playLevelUpPractice()
                    milestoneTimer = 2.0f
                }
            }

            inputProcessedThisFrame = true
            return true
        } else {
            // Handle incorrect key input.
            if (gameStarted && typingQueue.isNotEmpty()) {
                hurtTimer = 0.3f
                soundManager.playError()
                progressionPoints = (progressionPoints - 1).coerceAtLeast(0)

                if (isPracticeMode || isArcadeMode) {
                    streak = 0
                    cachedStreakStr = "0"
                    refreshHudCaching()

                    if (isArcadeMode) {
                        com.typingtoucan.utils.SaveManager.saveArcadeStreak(0)
                    }
                }
            }
        }
        return false
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pauseState == PauseState.VICTORY) {
            game.soundManager.stopMusic()
            game.screen = MenuScreen(game)
            return true
        }

        if (pauseState != PauseState.NONE) {
            handlePauseMenuTouch()
            return true
        }

        if (!gameStarted) {
            // Begin game on touch.
            gameStarted = true
            soundManager.playMusic()
            updateQueueString()
            if (!isAutoplay) {
                bird.flap()
                soundManager.playFlap()
            }
            return true
        }

        return false
    }
    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = false
    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean = false
    override fun mouseMoved(screenX: Int, screenY: Int): Boolean = false
    override fun scrolled(amountX: Float, amountY: Float): Boolean = false
    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean =
            false

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)

        // Sync visual scaling properties.
        bgTileScale = viewport.worldHeight / 1200f
        bgBaseTileW = 1024f * bgTileScale
        bgTotalWidth = bgBaseTileW * backgroundTiles.size

        groundW = groundTexture.regionWidth.toFloat()
        numGroundTiles = kotlin.math.ceil(viewport.worldWidth / groundW).toInt() + 1
        centerX = viewport.worldWidth / 2f
        centerY = viewport.worldHeight / 2f
        hudX = viewport.worldWidth - 70f
        yOffsetQueue = viewport.worldHeight * 0.1f
        yOffsetStart = viewport.worldHeight * 0.76f

        // Recalculate HUD layouts.
        hudLabelX = viewport.worldWidth - 70f
        topLabelY = viewport.worldHeight - 25f
        topLabel2Y = viewport.worldHeight - 45f
        topValueY = viewport.worldHeight - 68f
        bottomLabelY = 100f
        bottomValueY = 70f

        highLabelLayout.setText(smallFont, "HIGH")
        scoreLabelLayout.setText(smallFont, "SCORE")
        val label = if (isPracticeMode || isArcadeMode) "STREAK" else "LEVEL"
        levelStreakLabelLayout.setText(smallFont, label)
        escLabelLayout.setText(smallFont, "ESC for menu")

        highScoreLayout.setText(queueFont, cachedHighScoreStr)
        streakLayout.setText(queueFont, cachedStreakStr)
        levelLayout.setText(queueFont, cachedLevelStr)

        camera.position.set(centerX, centerY, 0f)
        game.batch.projectionMatrix = camera.combined

        refreshHudCaching()
    }

    override fun pause() {
        if (pauseState == PauseState.NONE) {
            pauseState = PauseState.MAIN
            menuSelectedIndex = 0
        }
        game.soundManager.pauseMusic()
    }

    override fun resume() {
        game.soundManager.resumeMusic()
    }
    override fun hide() {}
    override fun dispose() {
        uiFont.dispose()
        smallFont.dispose()
        queueFont.dispose()
        // generator.dispose() // Generator is disposed in init now
        // shapeRenderer.dispose() // Removed
        neckPool.clear()

        // Textures needed to be disposed manually before AssetManager.
        // Now AssetManager owns them. We do NOT dispose them here.
        // TappyBirdGame disposes AssetManager on exit.

        // soundManager is owned by Game, do not dispose here
        // Textures are owned by AssetManager/Atlas, do not dispose here
    }

    private fun drawPauseMainMenu() {
        var startY = viewport.worldHeight / 2f + 100f
        val gap = 50f
        val centerX = viewport.worldWidth / 2f

        for (index in 0 until mainMenuItems.size) {
            val item = mainMenuItems[index]
            val label =
                    when (item) {
                        "Difficulty" -> "Difficulty: ${difficulty.name}"
                        "Capitals" ->
                                "Capitals: ${if (com.typingtoucan.utils.SaveManager.loadCapitalsEnabled()) "ON" else "OFF"}"
                        else -> item
                    }
            val isSelected = index == menuSelectedIndex
            uiFont.color = if (isSelected) SELECTED_COLOR else Color.WHITE

            layout.setText(uiFont, label)
            val x = centerX - layout.width / 2
            val y = startY - (index * gap)
            uiFont.draw(game.batch, label, x, y)
        }
        uiFont.color = Color.WHITE
    }

    private fun drawAudioMenu() {
        val startY = viewport.worldHeight / 2f + 100f
        val gap = 50f
        val centerX = viewport.worldWidth / 2f

        for (index in 0 until audioMenuItems.size) {
            val item = audioMenuItems[index]
            val label =
                    when (item) {
                        "Sound" -> "Sound: ${if (game.soundManager.soundEnabled) "ON" else "OFF"}"
                        "Music" -> "Music: ${if (game.soundManager.musicEnabled) "ON" else "OFF"}"
                        "Music Track" -> {
                            val trackName =
                                    if (game.soundManager.currentTrack ==
                                                    com.typingtoucan.systems.SoundManager.MusicTrack
                                                            .WHAT
                                    )
                                            "What"
                                    else "Dark Forest"
                            "Music Track: $trackName"
                        }
                        else -> item
                    }

            val isSelected = index == menuSelectedIndex
            uiFont.color = if (isSelected) SELECTED_COLOR else Color.WHITE

            layout.setText(uiFont, label)
            uiFont.draw(game.batch, label, centerX - layout.width / 2, startY - (index * gap))
        }
        uiFont.color = Color.WHITE
    }

    private fun handlePauseMenuInput(keycode: Int) {
        if (pauseState == PauseState.NONE) return

        val currentListSize =
                when (pauseState) {
                    PauseState.MAIN -> mainMenuItems.size
                    PauseState.AUDIO -> audioMenuItems.size
                    PauseState.EXIT_CONFIRM -> exitConfirmItems.size
                    else -> 0
                }

        if (keycode == Input.Keys.UP || keycode == Input.Keys.DPAD_UP || keycode == Input.Keys.W) {
            menuSelectedIndex--
            if (menuSelectedIndex < 0) menuSelectedIndex = currentListSize - 1
        }
        if (keycode == Input.Keys.DOWN || keycode == Input.Keys.DPAD_DOWN || keycode == Input.Keys.S
        ) {
            menuSelectedIndex++
            if (menuSelectedIndex >= currentListSize) menuSelectedIndex = 0
        }

        if (keycode == Input.Keys.ENTER ||
                        keycode == Input.Keys.SPACE ||
                        keycode == Input.Keys.DPAD_CENTER
        ) {
            executePauseMenuAction()
        }
    }

    private fun handlePauseMenuTouch() {
        val touchX = Gdx.input.x.toFloat()
        val touchY = Gdx.input.y.toFloat()
        tempVec.set(touchX, touchY, 0f)
        val worldPos = camera.unproject(tempVec)

        val centerX = viewport.worldWidth / 2f
        val startY =
                if (pauseState == PauseState.EXIT_CONFIRM) viewport.worldHeight / 2f + 50f
                else viewport.worldHeight / 2f + 100f
        val gap = 50f

        val currentList =
                when (pauseState) {
                    PauseState.MAIN -> mainMenuItems
                    PauseState.AUDIO -> audioMenuItems
                    PauseState.EXIT_CONFIRM -> exitConfirmItems
                    else -> emptyList()
                }

        for (index in 0 until currentList.size) {
            val item = currentList[index]
            val label =
                    when (item) {
                        "Difficulty" -> "Difficulty: ${difficulty.name}"
                        "Capitals" ->
                                "Capitals: ${if (com.typingtoucan.utils.SaveManager.loadCapitalsEnabled()) "ON" else "OFF"}"
                        "Sound" -> "Sound: ${if (game.soundManager.soundEnabled) "ON" else "OFF"}"
                        "Music" -> "Music: ${if (game.soundManager.musicEnabled) "ON" else "OFF"}"
                        "Music Track" -> {
                            "Music Track: Dark Forest"
                        }
                        else -> item
                    }

            // Note: Dynamic labels might vary in width, but this is sufficient for touch detection.
            layout.setText(uiFont, label)
            val w = layout.width + 40f
            val h = layout.height + 40f
            val x = centerX - w / 2
            val y = startY - (index * gap) - 20f

            if (worldPos.x >= x && worldPos.x <= x + w && worldPos.y >= y && worldPos.y <= y + h) {
                menuSelectedIndex = index
                executePauseMenuAction()
            }
        }
    }

    private fun executePauseMenuAction() {
        game.soundManager.playMenuSelect()

        if (pauseState == PauseState.MAIN) {
            val selectedItem = mainMenuItems[menuSelectedIndex]
            if (selectedItem == "Resume") {
                pauseState = PauseState.NONE
                game.soundManager.resumeMusic()
            } else if (selectedItem == "Difficulty") {
                difficulty =
                        when (difficulty) {
                            DifficultyManager.Difficulty.EASY -> DifficultyManager.Difficulty.NORMAL
                            DifficultyManager.Difficulty.NORMAL -> DifficultyManager.Difficulty.HARD
                            DifficultyManager.Difficulty.HARD -> DifficultyManager.Difficulty.INSANE
                            DifficultyManager.Difficulty.INSANE -> DifficultyManager.Difficulty.EASY
                        }
                diffManager = DifficultyManager(difficulty)
                bird.gravity = diffManager.gravity
                bird.flapStrength = diffManager.flapStrength
                nextNeckInterval = diffManager.neckInterval
            } else if (selectedItem == "Capitals") {
                val current = com.typingtoucan.utils.SaveManager.loadCapitalsEnabled()
                val newState = !current
                com.typingtoucan.utils.SaveManager.saveCapitalsEnabled(newState)
                typingQueue.setCapitalsEnabled(newState)
            } else if (selectedItem == "Letter Selection Menu") {
                game.screen = CustomSetupScreen(game)
            } else if (selectedItem == "Audio") {
                pauseState = PauseState.AUDIO
                menuSelectedIndex = 0
            } else if (selectedItem == "Main Menu") {
                pauseState = PauseState.EXIT_CONFIRM
                menuSelectedIndex = 1 // Default to 'No'.
            }
        } else if (pauseState == PauseState.AUDIO) {
            val sm = game.soundManager
            when (menuSelectedIndex) {
                0 -> sm.soundEnabled = !sm.soundEnabled // Toggle sound.
                1 -> sm.musicEnabled = !sm.musicEnabled // Toggle music.
                2 -> { // Toggle track.
                    sm.currentTrack =
                            if (sm.currentTrack ==
                                            com.typingtoucan.systems.SoundManager.MusicTrack.WHAT
                            )
                                    com.typingtoucan.systems.SoundManager.MusicTrack.DARK_FOREST
                            else com.typingtoucan.systems.SoundManager.MusicTrack.WHAT
                }
                3 -> { // Back.
                    pauseState = PauseState.MAIN
                    menuSelectedIndex = 3
                }
            }
        } else if (pauseState == PauseState.EXIT_CONFIRM) {
            if (menuSelectedIndex == 0) { // Yes.
                game.soundManager.stopMusic()
                game.screen = MenuScreen(game)
            } else { // No.
                pauseState = PauseState.MAIN
                menuSelectedIndex = mainMenuItems.indexOf("Main Menu").coerceAtLeast(0)
            }
        }
    }

    private fun drawExitConfirmMenu() {
        val startY = viewport.worldHeight / 2f + 50f
        val gap = 50f
        val centerX = viewport.worldWidth / 2f

        for (index in 0 until exitConfirmItems.size) {
            val item = exitConfirmItems[index]
            val isSelected = index == menuSelectedIndex
            uiFont.color = if (isSelected) SELECTED_COLOR else Color.WHITE

            layout.setText(uiFont, item)
            uiFont.draw(game.batch, item, centerX - layout.width / 2, startY - (index * gap))
        }
        uiFont.color = Color.WHITE
    }

    private fun drawSnippetLine(
            text: String,
            y: Float,
            alpha: Float = 1f,
            isCurrent: Boolean = false,
            localProg: Int = 0
    ) {
        if (text.isEmpty()) return

        // Measure full width for centering.
        layout.setText(queueFont, text)
        val fullWidth = layout.width
        val startX = viewport.worldWidth / 2f - fullWidth / 2f

        if (isCurrent && localProg > 0) {
            // Draw typed portion in green.
            queueFont.color = tempColor.set(0.2f, 1f, 0.2f, alpha)
            queueFont.draw(
                    game.batch,
                    text,
                    startX,
                    y,
                    0,
                    localProg,
                    0f,
                    com.badlogic.gdx.utils.Align.left,
                    false
            )

            // Measure typed width to offset the remaining part.
            layout.setText(
                    queueFont,
                    text,
                    0,
                    localProg,
                    queueFont.color,
                    0f,
                    com.badlogic.gdx.utils.Align.left,
                    false,
                    null
            )
            val typedWidth = layout.width

            // Draw remaining portion in white.
            queueFont.color = tempColor.set(1f, 1f, 1f, alpha)
            if (localProg < text.length) {
                queueFont.draw(
                        game.batch,
                        text,
                        startX + typedWidth,
                        y,
                        localProg,
                        text.length,
                        0f,
                        com.badlogic.gdx.utils.Align.left,
                        false
                )
            }
        } else {
            // Draw entire line with adjusted alpha.
            val baseAlpha = if (isCurrent) 1f else 0.4f
            queueFont.color = tempColor.set(1f, 1f, 1f, baseAlpha * alpha)
            queueFont.draw(
                    game.batch,
                    text,
                    startX,
                    y,
                    0,
                    text.length,
                    0f,
                    com.badlogic.gdx.utils.Align.left,
                    false
            )
        }
    }
}
