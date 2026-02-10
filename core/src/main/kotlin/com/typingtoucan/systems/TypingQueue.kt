package com.typingtoucan.systems

import com.badlogic.gdx.Gdx

/**
 * Manages the queue of upcoming characters for the player to type.
 *
 * It buffers characters from a [TypingSource] using a memory-efficient circular buffer
 * (Optimization #6). Provides methods to validate input and synchronize with the game state.
 *
 * @param source The source strategy for character generation (e.g. Classic, Custom, Text).
 */
class TypingQueue(private val source: TypingSource) {

    /** Capacity of the internal circular buffer. */
    private val capacity = 16
    private val buffer = CharArray(capacity)

    /** Index of the first element in the queue. */
    private var head = 0
    /** Index where the next element will be inserted. */
    private var tail = 0
    /** Current number of elements in the queue. */
    private var count = 0

    /** The target number of characters to keep in the queue. */
    val queueSize = 3

    init {
        repeat(queueSize) { addLetter() }
    }

    /** Adds a letter from the source to the queue if available. */
    private fun addLetter() {
        if (count >= capacity) return // Buffer full (shouldn't happen with queueSize=3)

        if (!source.isComplete()) {
            try {
                buffer[tail] = source.getNextChar()
                tail = (tail + 1) % capacity
                count++
            } catch (e: Exception) {
                Gdx.app.error("TypingQueue", "Failed to add letter", e)
            }
        }
    }

    /**
     * Processes a typed character input.
     *
     * If the input matches the head of the queue, it removes it, updates source weights, and adds a
     * new letter to the queue.
     *
     * @param char The character typed by the user.
     * @param updateWeights Whether to report this type event to the source (for weighting).
     * @return An integer weight/score for value if match was successful, null otherwise.
     */
    fun handleInput(char: Char, updateWeights: Boolean = true): Int? {
        if (count == 0) {
            repeat(queueSize) { addLetter() }
        }

        if (count > 0 && buffer[head] == char) {
            val matchedChar = buffer[head]
            head = (head + 1) % capacity
            count--

            if (updateWeights) {
                source.onCharTyped(matchedChar)
            }

            addLetter()
            return 5
        }
        return null
    }

    /** Delegates crash reporting to the source. */
    fun onCrash(crashedChar: Char) {
        source.onCrash(crashedChar)
    }

    /** Returns the first character in the queue. */
    fun first(): Char = if (count > 0) buffer[head] else ' '

    /** Returns true if the queue is not empty. */
    fun isNotEmpty(): Boolean = count > 0

    /** Returns true if the queue is empty. */
    fun isEmpty(): Boolean = count == 0

    /** Appends a string representation of the queue to the provided StringBuilder. */
    fun appendTo(sb: StringBuilder, separator: String) {
        for (i in 0 until count) {
            sb.append(buffer[(head + i) % capacity])
            if (i < count - 1) sb.append(separator)
        }
    }

    // Proxy methods for GameScreen compatibility

    /** Proxies [TypingSource.expandPool]. */
    fun expandPool(): List<Char> = source.expandPool()
    /** Proxies [TypingSource.isComplete]. */
    fun isFullyUnlocked(): Boolean = source.isComplete()

    /** Proxies [TypingSource.setCapitalsEnabled]. */
    fun setCapitalsEnabled(enabled: Boolean) {
        source.setCapitalsEnabled(enabled)
    }

    /** Gets the underlying source. */
    fun getSource() = source
}
