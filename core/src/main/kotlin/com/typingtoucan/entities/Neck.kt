package com.typingtoucan.entities

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Pool
import kotlin.random.Random

/**
 * Represents an obstacle pair (necks/snakes) that the bird must fly through.
 *
 * Manages the position, gap size, and rendering of the top and bottom obstacles.
 *
 * @property x The horizontal position of the obstacle.
 * @property gapCenterY The vertical center of the gap between the obstacles.
 * @property isSnake Whether this obstacle uses snake graphics instead of giraffe necks.
 */
class Neck() : Pool.Poolable {
    var x: Float = 0f
    var gapCenterY: Float = 0f
    var isSnake: Boolean = false

    /** Index for selecting random head variations (if applicable). */
    var headIndex = 0

    /** Cached texture for rendering (assigned during init). */
    var currentTexture: com.badlogic.gdx.graphics.g2d.TextureRegion? = null

    /** Cached draw X position (centered within the 120px draw width). */
    var cachedDrawX: Float = 0f

    /** Cached draw Y for the bottom neck (anchored). */
    var cachedBottomY: Float = 0f

    /** Width of the collision hitbox. */
    val width = 80f

    // Visual dimensions (Asset is 512x2048, aspect ratio 1:4)
    private val DRAW_WIDTH = 120f
    private val DRAW_HEIGHT = 480f
    private val DRAW_X_OFFSET = (width - DRAW_WIDTH) / 2f

    /** The vertical space between the top and bottom obstacles (pixels). */
    val gap = 220f
    private val halfGap = gap / 2f

    /** Minimum height for the neck to be visible. */
    val minNeckHeight = 50f

    /** Cached Top Y-coordinate of the bottom obstacle. */
    private var cachedBottomHeight: Float = 0f

    /** Cached Bottom Y-coordinate of the top obstacle. */
    private var cachedTopY: Float = 0f

    /** Height of the top obstacle bounds (pixels). Uses a large value to extend off-screen. */
    val topHeight = 2000f

    /** Collision bounds for the bottom obstacle. */
    val bottomBounds = Rectangle(0f, 0f, width, 0f)

    /** Collision bounds for the top obstacle. */
    val topBounds = Rectangle(0f, 0f, width, topHeight)

    /** Tracks if this obstacle has been successfully passed by the player. */
    var scored = false

    /** Tracks if a collision has occurred with this obstacle. */
    var collided = false

    fun init(x: Float, gapCenterY: Float, isSnake: Boolean) {
        this.x = x
        this.gapCenterY = gapCenterY
        this.isSnake = isSnake
        this.headIndex = Random.nextInt(if (isSnake) 2 else 5)
        this.scored = false
        this.collided = false

        // Cache gap-dependent positions
        cachedBottomHeight = gapCenterY - halfGap
        cachedTopY = gapCenterY + halfGap
        cachedBottomY = cachedBottomHeight - DRAW_HEIGHT
        cachedDrawX = x + DRAW_X_OFFSET

        bottomBounds.set(x, 0f, width, cachedBottomHeight)
        topBounds.set(x, cachedTopY, width, topHeight)
    }

    override fun reset() {
        x = 0f
        gapCenterY = 0f
        isSnake = false
        headIndex = 0
        currentTexture = null
        scored = false
        collided = false
    }

    /**
     * Updates the horizontal position of the obstacle.
     *
     * @param delta Time elapsed since the last frame.
     * @param speed The speed at which the obstacle moves to the left.
     */
    fun update(delta: Float, speed: Float) {
        x -= speed * delta
        bottomBounds.setX(x)
        topBounds.setX(x)

        // Update cached position with pre-calculated offset
        cachedDrawX = x + DRAW_X_OFFSET
    }

    /**
     * Renders the top and bottom obstacles.
     *
     * @param batch The [SpriteBatch] used for drawing.
     */
    fun render(batch: SpriteBatch) {
        val region = currentTexture ?: return

        // --- BOTTOM NECK ---
        batch.draw(region, cachedDrawX, cachedBottomY, DRAW_WIDTH, DRAW_HEIGHT)

        // --- TOP NECK ---
        batch.draw(
                region.texture,
                cachedDrawX,
                cachedTopY,
                DRAW_WIDTH,
                DRAW_HEIGHT,
                region.regionX,
                region.regionY,
                region.regionWidth,
                region.regionHeight,
                false,
                true
        )
    }
}
