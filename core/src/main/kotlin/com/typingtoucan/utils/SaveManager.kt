package com.typingtoucan.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/**
 * Manages persistent storage of game progress and user preferences via [Preferences].
 *
 * All writes are dispatched asynchronously to avoid blocking the GL thread. Reads are synchronous
 * and safe to call from any thread after the first [Gdx] context is available.
 */
object SaveManager {
    private const val PREFS_NAME = "TypingToucanPrefs"
    private val prefs: Preferences by lazy { Gdx.app.getPreferences(PREFS_NAME) }

    private const val KEY_NORMAL_LEVEL = "normal_level"
    private const val KEY_CUSTOM_STREAK = "custom_streak_v2"
    private const val KEY_TEXT_STREAK = "text_streak_v2"
    private const val KEY_ARCADE_STREAK = "arcade_streak"
    private const val CAPITALS_KEY = "capitalsEnabled"
    private const val KEY_SOUND_ENABLED = "soundEnabled"
    private const val KEY_MUSIC_ENABLED = "musicEnabled"

    /** Flushes pending preference writes on a background thread. */
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
     * Saves [level] as the highest Normal Mode level if it exceeds the stored value.
     *
     * @param level The level reached at the end of the session.
     */
    fun saveNormalLevel(level: Int) {
        val current = getNormalLevel()
        if (level > current) {
            prefs.putInteger(KEY_NORMAL_LEVEL, level)
            asyncFlush()
        }
    }

    /** Returns the highest Normal Mode level reached, defaulting to 1. */
    fun getNormalLevel(): Int = prefs.getInteger(KEY_NORMAL_LEVEL, 1)

    // Custom mode (practice) persistence.

    /**
     * Saves [streak] as the Custom Mode high score if it exceeds the stored value.
     *
     * @param streak The streak count at the end of the session.
     */
    fun saveCustomStreak(streak: Int) {
        val current = getCustomStreak()
        if (streak > current) {
            prefs.putInteger(KEY_CUSTOM_STREAK, streak)
            asyncFlush()
        }
    }

    /** Returns the Custom Mode high streak, defaulting to 0. */
    fun getCustomStreak(): Int = prefs.getInteger(KEY_CUSTOM_STREAK, 0)

    // Text mode persistence.

    /**
     * Saves [streak] as the Text Mode high score if it exceeds the stored value.
     *
     * @param streak The streak count at the end of the session.
     */
    fun saveTextStreak(streak: Int) {
        val current = getTextStreak()
        if (streak > current) {
            prefs.putInteger(KEY_TEXT_STREAK, streak)
            asyncFlush()
        }
    }

    /** Returns the Text Mode high streak, defaulting to 0. */
    fun getTextStreak(): Int = prefs.getInteger(KEY_TEXT_STREAK, 0)

    // Arcade mode persistence.

    /**
     * Saves [streak] as the Arcade Mode high score if it exceeds the stored value.
     *
     * @param streak The streak count at the end of the session.
     */
    fun saveArcadeStreak(streak: Int) {
        val current = getArcadeStreak()
        if (streak > current) {
            prefs.putInteger(KEY_ARCADE_STREAK, streak)
            asyncFlush()
        }
    }

    /** Returns the Arcade Mode high streak, defaulting to 0. */
    fun getArcadeStreak(): Int = prefs.getInteger(KEY_ARCADE_STREAK, 0)

    /** Resets all mode high scores and streaks to their initial values. */
    fun resetHighScore() {
        prefs.putInteger(KEY_NORMAL_LEVEL, 1)
        prefs.putInteger(KEY_CUSTOM_STREAK, 0)
        prefs.putInteger(KEY_TEXT_STREAK, 0)
        prefs.putInteger(KEY_ARCADE_STREAK, 0)
        asyncFlush()
    }

    /** Saves the user's sound-enabled preference asynchronously. */
    fun saveSoundEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_SOUND_ENABLED, enabled)
        asyncFlush()
    }

    /** Loads the user's sound-enabled preference. */
    fun loadSoundEnabled(): Boolean = prefs.getBoolean(KEY_SOUND_ENABLED, true)

    /** Saves the user's music-enabled preference asynchronously. */
    fun saveMusicEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_MUSIC_ENABLED, enabled)
        asyncFlush()
    }

    /** Loads the user's music-enabled preference. */
    fun loadMusicEnabled(): Boolean = prefs.getBoolean(KEY_MUSIC_ENABLED, true)

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
