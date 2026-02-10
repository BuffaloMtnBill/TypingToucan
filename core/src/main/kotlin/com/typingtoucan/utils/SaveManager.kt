package com.typingtoucan.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/**
 * Handles persistent data storage using [Preferences].
 *
 * Saves and loads high scores and user preferences.
 */
object SaveManager {
    private const val PREFS_NAME = "TypingToucanPrefs"
    private val prefs: Preferences by lazy { Gdx.app.getPreferences(PREFS_NAME) }

    private const val KEY_NORMAL_LEVEL = "normal_level"
    private const val KEY_CUSTOM_STREAK = "custom_streak_v2"
    private const val KEY_TEXT_STREAK = "text_streak_v2"
    private const val KEY_ARCADE_STREAK = "arcade_streak"
    private const val CAPITALS_KEY = "capitalsEnabled"

    /**
     * Offloads the blocking disk write to a background thread. We use a single thread to ensure
     * order of operations and avoid race conditions on the file.
     */
    private fun asyncFlush() {
        val prefsToFlush = prefs
        Thread {
                    try {
                        prefsToFlush.flush()
                    } catch (e: Exception) {
                        Gdx.app.error(
                                "SaveManager",
                                "Failed to flush preferences asynchronously.",
                                e
                        )
                    }
                }
                .start()
    }

    // Normal mode persistence.

    /**
     * Saves the highest level reached in Normal Mode. Only updates if the new level is higher than
     * the stored value.
     */
    fun saveNormalLevel(level: Int) {
        val current = getNormalLevel()
        if (level > current) {
            prefs.putInteger(KEY_NORMAL_LEVEL, level)
            asyncFlush()
        }
    }
    fun getNormalLevel(): Int = prefs.getInteger(KEY_NORMAL_LEVEL, 1)

    // Custom mode persistence.

    /** Saves the highest streak achieved in Custom Mode (Practice). */
    fun saveCustomStreak(streak: Int) {
        val current = getCustomStreak()
        if (streak > current) {
            prefs.putInteger(KEY_CUSTOM_STREAK, streak)
            asyncFlush()
        }
    }
    fun getCustomStreak(): Int = prefs.getInteger(KEY_CUSTOM_STREAK, 0)

    // Text mode streak persistence.
    fun saveTextStreak(streak: Int) {
        val current = getTextStreak()
        if (streak > current) {
            prefs.putInteger(KEY_TEXT_STREAK, streak)
            asyncFlush()
        }
    }
    fun getTextStreak(): Int = prefs.getInteger(KEY_TEXT_STREAK, 0)

    // Arcade mode streak persistence.
    fun saveArcadeStreak(streak: Int) {
        val current = getArcadeStreak()
        if (streak > current) {
            prefs.putInteger(KEY_ARCADE_STREAK, streak)
            asyncFlush()
        }
    }
    fun getArcadeStreak(): Int = prefs.getInteger(KEY_ARCADE_STREAK, 0)

    fun resetHighScore() {
        prefs.putInteger(KEY_NORMAL_LEVEL, 1)
        prefs.putInteger(KEY_CUSTOM_STREAK, 0)
        prefs.putInteger(KEY_TEXT_STREAK, 0)
        prefs.putInteger(KEY_ARCADE_STREAK, 0)
        asyncFlush()
    }

    /** Saves the user's preference for capital letters. */
    fun saveCapitalsEnabled(enabled: Boolean) {
        prefs.putBoolean(CAPITALS_KEY, enabled)
        asyncFlush()
    }

    /** Loads the user's preference for capital letters. */
    fun loadCapitalsEnabled(): Boolean {
        return prefs.getBoolean(CAPITALS_KEY, false)
    }
}
