package com.typingtoucan.systems

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound

/**
 * Manages the loading, playing, and lifecycle of all audio assets in the game.
 *
 * Handles both short-duration sound effects and long-duration background music. Supports
 * enabling/disabling of sound and music separateley.
 */
class SoundManager {
    private val flapSounds = com.badlogic.gdx.utils.Array<Sound>()
    private val scoreSounds = com.badlogic.gdx.utils.Array<Sound>()
    private val monkeySounds = com.badlogic.gdx.utils.Array<Sound>()
    private var crashSound: Sound? = null
    private var levelUpSound: Sound? = null
    private var levelUpPracticeSound: Sound? = null
    private var errorSound: Sound? = null
    private var splashSound: Music? = null
    private var bgMusic: Music? = null

    /** Controls whether sound effects are played. */
    var soundEnabled: Boolean = true
        set(value) {
            field = value
            val prefs = Gdx.app.getPreferences("TypingToucanPrefs")
            prefs.putBoolean("soundEnabled", value)
            prefs.flush()
        }

    /** Controls whether background music is played. Updates playback immediately. */
    var musicEnabled: Boolean = true
        set(value) {
            field = value
            val prefs = Gdx.app.getPreferences("TypingToucanPrefs")
            prefs.putBoolean("musicEnabled", value)
            prefs.flush()
            if (value) {
                playMusic()
            } else {
                stopMusic()
            }
        }

    /**
     * Available music tracks.
     *
     * - [WHAT]: The default upbeat track.
     * - [DARK_FOREST]: The alternative track.
     */
    enum class MusicTrack {
        WHAT,
        DARK_FOREST
    }

    private var _currentTrack: MusicTrack = MusicTrack.WHAT
    /** The currently selected background music track. Switches track immediately upon change. */
    var currentTrack: MusicTrack
        get() = _currentTrack
        set(value) {
            updateTrack(value)
        }

    /**
     * Updates the active music track.
     * @param track The track to switch to.
     */
    fun updateTrack(track: MusicTrack) {
        if (_currentTrack != track) {
            _currentTrack = track
            switchMusic()
        }
    }

    private var assetManager: com.badlogic.gdx.assets.AssetManager? = null

    /** Links the SoundManager to an AssetManager to retrieve preloaded audio assets. */
    fun init(am: com.badlogic.gdx.assets.AssetManager) {
        this.assetManager = am
        refreshAssets()

        // Load persisted settings (Tracks are now fixed per screen).
        val prefs = Gdx.app.getPreferences("TypingToucanPrefs")
        soundEnabled = prefs.getBoolean("soundEnabled", true)
        musicEnabled = prefs.getBoolean("musicEnabled", true)
        _currentTrack = MusicTrack.WHAT
    }

    /** Refreshes references to audio assets from the AssetManager. Does not trigger playback. */
    fun refreshAssets() {
        if (assetManager == null) return

        // Populate collections from AssetManager.
        // Flap.
        flapSounds.clear()
        for (i in 1..4) {
            val s = getSound("assets/sfx_flap$i.mp3")
            if (s != null) flapSounds.add(s)
        }
        if (flapSounds.size == 0) {
            val s = getSound("assets/sfx_flap.mp3")
            if (s != null) flapSounds.add(s)
        }

        // Score.
        scoreSounds.clear()
        for (i in 1..4) {
            var s = getSound("assets/sfx_score$i.mp3")
            if (s == null) s = getSound("assets/sfx_score$i.wav")
            if (s != null) scoreSounds.add(s)
        }
        if (scoreSounds.size == 0) {
            var s = getSound("assets/sfx_score.mp3")
            if (s == null) s = getSound("assets/sfx_score.wav")
            if (s != null) scoreSounds.add(s)
        }

        // Monkey.
        monkeySounds.clear()
        for (i in 1..3) {
            val s = getSound("assets/sfx_monkey$i.mp3")
            if (s != null) monkeySounds.add(s)
        }

        crashSound = getSound("assets/sfx_crash.mp3")
        levelUpSound = getSound("assets/sfx_levelup.mp3")
        levelUpPracticeSound = getSound("assets/sfx_levelup_practice.mp3")
        errorSound = getSound("assets/sfx_error.mp3")
        splashSound = getMusic("assets/minnowbyte.mp3")

        // Initial music (managed individually via getMusic).
        val path =
                when (_currentTrack) {
                    MusicTrack.WHAT -> "assets/music_bg.mp3"
                    MusicTrack.DARK_FOREST -> "assets/music_dark_forest.mp3"
                }
        bgMusic = getMusic(path)
    }

    private fun getSound(path: String): Sound? {
        val am = assetManager ?: return null
        return if (am.isLoaded(path, Sound::class.java)) {
            am.get(path, Sound::class.java)
        } else {
            null
        }
    }

    private fun getMusic(path: String): Music? {
        val am = assetManager ?: return null
        return if (am.isLoaded(path, Music::class.java)) {
            am.get(path, Music::class.java)
        } else {
            null
        }
    }

    /** Plays a random flap sound effect. */
    fun playFlap() {
        if (soundEnabled && flapSounds.size > 0) {
            flapSounds.random().play(0.5f)
        }
    }

    /** Plays a random score sound effect. */
    fun playScore() {
        if (soundEnabled && scoreSounds.size > 0) {
            scoreSounds.random().play(0.5f)
        }
    }

    /** Plays a random monkey sound effect. */
    fun playMonkey() {
        if (soundEnabled && monkeySounds.size > 0) {
            monkeySounds.random().play(0.6f)
        }
    }

    /** Plays the crash sound effect. */
    fun playCrash() {
        if (soundEnabled) crashSound?.play(0.7f)
    }

    /** Plays the primary level up sound. */
    fun playLevelUp() {
        if (soundEnabled) levelUpSound?.play(1.0f) // Loud and proud
    }

    /** Plays the practice mode level up / variation sound. */
    fun playLevelUpPractice() {
        if (soundEnabled) levelUpPracticeSound?.play(1.0f)
    }

    /** Plays the error sound effect. */
    fun playError() {
        if (soundEnabled) errorSound?.play(0.6f)
    }

    /** Plays the menu selection sound (score1). */
    fun playMenuSelect() {
        if (soundEnabled && scoreSounds.size > 0) {
            scoreSounds[0].play(0.5f)
        }
    }

    /** Plays the MinnowByte splash sound. */
    fun playSplash() {
        if (soundEnabled) {
            splashSound?.apply {
                stop() // Force restart if already playing or in limbo state
                volume = 1.0f
                play()
            }
        }
    }

    /** Stops the MinnowByte splash sound. */
    fun stopSplash() {
        splashSound?.stop()
    }

    /** Starts playing the background music if enabled and not already playing. */
    fun playMusic() {
        if (!musicEnabled) return
        if (bgMusic == null) {
            // Load current track if null
            loadCurrentTrack()
        }
        bgMusic?.apply {
            if (!isPlaying) {
                isLooping = true
                volume = 0.3f
                play()
            }
        }
    }

    private fun loadCurrentTrack() {
        // bgMusic?.dispose() // Removed: AssetManager handles disposal
        val path =
                when (_currentTrack) {
                    MusicTrack.WHAT -> "assets/music_bg.mp3"
                    MusicTrack.DARK_FOREST -> "assets/music_dark_forest.mp3"
                }
        bgMusic = getMusic(path)
    }

    /**
     * Helper to switch the active music track.
     *
     * Stops the current music, loads the new assets (via AssetManager), and plays if music is
     * enabled.
     *
     * @param track The new [MusicTrack] to play.
     */
    private fun switchMusic() {
        stopMusic()
        loadCurrentTrack()
        if (musicEnabled) {
            playMusic()
        }
    }

    /** Stops the currently playing background music. */
    fun stopMusic() {
        bgMusic?.stop()
    }

    /** Pauses the background music. */
    fun pauseMusic() {
        bgMusic?.pause()
    }

    /** Resumes the background music. */
    fun resumeMusic() {
        if (musicEnabled) {
            bgMusic?.play()
        }
    }

    /** Disposes of all audio assets. Managed by AssetManager, but we null out references. */
    fun dispose() {
        bgMusic = null
        flapSounds.clear()
        scoreSounds.clear()
        monkeySounds.clear()
        assetManager = null
    }
}
