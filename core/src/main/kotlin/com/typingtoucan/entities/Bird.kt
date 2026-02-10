package com.typingtoucan.entities

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Rectangle

/**
 * Represents the player controllable bird entity (Toucan).
 *
 * Handles physics validation, position updates, and rendering of the bird.
 *
 * @property x The horizontal position of the bird.
 * @property y The vertical position of the bird.
 */
class Bird(var x: Float, var y: Float) {
    /** The visual width of the bird sprite in pixels. */
    val width = 140f
    /** The visual height of the bird sprite in pixels. */
    val height = 206f

    // Visual dimensions.
    private val originX = width / 2f
    private val originY = height / 2f

    /** The width of the collision hitbox in pixels. */
    val collisionWidth = 40f
    /** The height of the collision hitbox in pixels. */
    val collisionHeight = 30f

    // Cached collision offsets.
    private val hitboxOffsetX = (width - collisionWidth) / 2f
    private val hitboxOffsetY = (height - collisionHeight) / 2f

    /** Current vertical velocity of the bird (pixels/frame). */
    var velocity = 0f
    /** Gravity applied to the velocity per frame (pixels/frame^2). */
    var gravity = -0.5f
    /** Vertical impulse applied when flapping (pixels/frame). */
    var flapStrength = 10f

    /** Rectangle representing the collision area of the bird. */
    val bounds = Rectangle(x, y, collisionWidth, collisionHeight)

    /**
     * Updates the bird's physics, ensuring consistency across different frame rates.
     *
     * @param delta The time elapsed since the last frame.
     */
    fun update(delta: Float) {
        val dt = delta * 60f
        val oldVelocity = velocity
        velocity += gravity * dt
        y += (oldVelocity + velocity) * 0.5f * dt

        // Handle screen boundaries.
        if (y > 600 - height) {
            y = 600 - height
            velocity = 0f
        }

        // Update collision box position.
        bounds.setPosition(x + hitboxOffsetX, y + hitboxOffsetY)
    }

    /** Applies an upward impulse to the bird, simulating a flap. */
    fun flap() {
        velocity = flapStrength
    }

    /**
     * Renders the bird sprite.
     *
     * @param batch The [SpriteBatch] used for drawing.
     * @param region The [TextureRegion] of the current animation frame to draw.
     */
    fun render(
            batch: com.badlogic.gdx.graphics.g2d.SpriteBatch,
            region: com.badlogic.gdx.graphics.g2d.TextureRegion
    ) {
        val rotation = (velocity * 2).coerceIn(-45f, 45f)
        batch.draw(region, x, y, originX, originY, width, height, 1f, 1f, rotation)
    }
}
