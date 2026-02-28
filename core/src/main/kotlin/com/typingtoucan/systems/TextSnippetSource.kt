package com.typingtoucan.systems

/**
 * A single passage of text used in Text Mode.
 *
 * @param text The raw passage text.
 * @param metadata Optional metadata associated with the passage (e.g. title or author).
 */
data class PassageItem(val text: String, val metadata: String)

/**
 * Typing source that serves passages one at a time, preloading the next for seamless transitions.
 *
 * @param allPassages The pool of passages to draw from.
 * @param sequential If true, passages are served in order; otherwise they are chosen randomly.
 */
class TextSnippetSource(
        private val allPassages: List<PassageItem>,
        private val sequential: Boolean = false
) : TypingSource {
    override fun setCapitalsEnabled(enabled: Boolean) {
        // No-op for text mode.
    }

    // Character read pointer into the concatenated passage stream.
    private var currentIndex = 0
    // Number of characters the player has confirmed correct in the current passage.
    private var typedIndex = 0
    // Index into allPassages used by sequential mode to track the next passage to preload.
    private var listIndex = 0

    // Word-wrapped lines for the current passage, used by the UI.
    val displayLines = mutableListOf<String>()
    private var processedText: String = ""

    // Cached UI state — recomputed only when typedIndex changes.
    private val cachedDisplayState = DisplayState("", "", "", 0, 0)
    private var cachedTypedIndex = -1

    // Current and preloaded-next passage metadata.
    var sourceMetadata = ""
    private var nextSourceMetadata = ""
    private var nextDisplayLines = listOf<String>()
    private var nextProcessedText: String = ""

    init {
        if (allPassages.isEmpty()) throw IllegalArgumentException("Passages cannot be empty")
        if (sequential) {
            listIndex = 0
            setupCurrent(allPassages[0])
        } else {
            val first = allPassages.random()
            setupCurrent(first)
        }
        preloadNext()
    }

    private fun processPassage(p: PassageItem): Pair<List<String>, String> {
        val raw = p.text.replace('\n', ' ').filter { !it.isISOControl() }
        val lines = wordWrap(raw, 15)
        var text = lines.joinToString(" ")
        if (text.isNotEmpty()) text += " "
        return Pair(lines, text)
    }

    private fun setupCurrent(p: PassageItem) {
        sourceMetadata = p.metadata
        val (lines, text) = processPassage(p)
        displayLines.clear()
        displayLines.addAll(lines)
        processedText = text

        currentIndex = 0
        typedIndex = 0
    }

    /** Loads the next passage into the preload buffer so swaps are instantaneous. */
    private fun preloadNext() {
        val p =
                if (sequential) {
                    // Advance listIndex and wrap around to loop through passages indefinitely.
                    listIndex = (listIndex + 1) % allPassages.size
                    allPassages[listIndex]
                } else {
                    allPassages.random()
                }
        nextSourceMetadata = p.metadata
        val (lines, text) = processPassage(p)
        nextDisplayLines = lines
        nextProcessedText = text
    }

    /**
     * Splits text into lines of a maximum length, ensuring words are not broken.
     *
     * Optimized to reduce memory allocations (Optimization #8) by using [StringBuilder] directly
     * instead of `String.split()` or creating intermediate substring objects.
     *
     * @param text The input text to wrap.
     * @param limit The maximum number of characters per line.
     * @return A list of wrapped lines.
     */
    private fun wordWrap(text: String, limit: Int): List<String> {
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        var start = 0

        while (start < text.length) {
            var end = text.indexOf(' ', start)
            if (end == -1) end = text.length

            val wordLen = end - start
            if (wordLen == 0) {
                start = end + 1
                continue
            }

            val spaceLen = if (sb.isNotEmpty()) 1 else 0

            if (sb.length + wordLen + spaceLen <= limit) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(text, start, end)
            } else {
                if (sb.isNotEmpty()) lines.add(sb.toString())
                sb.setLength(0)
                sb.append(text, start, end)
            }
            start = end + 1
        }

        if (sb.isNotEmpty()) lines.add(sb.toString())
        return lines
    }

    override fun getNextChar(): Char {
        // Passage swaps are triggered by onCharTyped, not here; the queue reads ahead freely.
        if (currentIndex < processedText.length) {
            return processedText[currentIndex++]
        }

        // Spill into the preloaded next passage.
        val nextIndex = currentIndex - processedText.length
        if (nextIndex < nextProcessedText.length) {
            currentIndex++
            return nextProcessedText[nextIndex]
        }

        // Both passages exhausted — swap has not yet occurred. Return a space as a safe fallback.
        return ' '
    }

    /**
     * Swaps the current passage with the pre-loaded next passage.
     *
     * Resets indices to maintain synchronization with the [TypingQueue]. Specifically, sets
     * [currentIndex] to 3 to account for the buffer size of the queue (Optimization #6), ensuring
     * the character stream remains continuous without overlaps.
     */
    private fun performSwap() {
        sourceMetadata = nextSourceMetadata
        displayLines.clear()
        displayLines.addAll(nextDisplayLines)
        processedText = nextProcessedText

        // Reset currentIndex to 3 to skip the characters already held in the TypingQueue buffer,
        // keeping the character stream continuous with no gaps or overlaps.
        currentIndex = 3
        typedIndex = 0

        preloadNext()
    }

    override fun onCharTyped(char: Char) {
        typedIndex++
        if (typedIndex >= processedText.length) {
            performSwap()
        }
    }

    override fun onCrash(char: Char) {
        // No-op for text mode.
    }

    /**
     * Snapshot of the UI-visible passage state for a single frame.
     *
     * @param currentLine The line the player is currently typing.
     * @param nextLine The line that follows, shown as a preview.
     * @param prevLine The line that precedes the current one.
     * @param localProgress Number of characters typed on the current line.
     * @param lineIndex Index of the current line within [displayLines].
     */
    class DisplayState(
            var currentLine: String,
            var nextLine: String,
            var prevLine: String,
            var localProgress: Int,
            var lineIndex: Int
    )

    /**
     * Returns the current [DisplayState] for rendering.
     *
     * The result is cached and recomputed only when [typedIndex] has changed since the last call.
     */
    fun getDisplayState(): DisplayState {
        if (typedIndex == cachedTypedIndex) return cachedDisplayState

        // Find the line that contains typedIndex.
        var charCount = 0
        var lineIdx = 0

        for (i in displayLines.indices) {
            val len = displayLines[i].length + 1 // +1 for the space joining lines
            if (typedIndex < charCount + len) {
                lineIdx = i
                break
            }
            charCount += len
            lineIdx = i // clamp to last line if typedIndex overshoots
        }

        if (lineIdx >= displayLines.size && displayLines.isNotEmpty())
                lineIdx = displayLines.size - 1

        val currentStr = displayLines.getOrElse(lineIdx) { "" }

        val nextStr =
                if (lineIdx + 1 < displayLines.size) {
                    displayLines[lineIdx + 1]
                } else {
                    if (nextDisplayLines.isNotEmpty()) nextDisplayLines[0] else ""
                }

        val prevStr = if (lineIdx > 0) displayLines[lineIdx - 1] else ""

        // Compute the character offset of the start of the current line.
        var startCharIdx = 0
        for (i in 0 until lineIdx) {
            startCharIdx += displayLines[i].length + 1
        }

        val localProg = (typedIndex - startCharIdx).coerceIn(0, currentStr.length)

        cachedDisplayState.currentLine = currentStr
        cachedDisplayState.nextLine = nextStr
        cachedDisplayState.prevLine = prevStr
        cachedDisplayState.localProgress = localProg
        cachedDisplayState.lineIndex = lineIdx
        cachedTypedIndex = typedIndex

        return cachedDisplayState
    }

    override fun getProgressDisplay(): String = "Inf"

    override fun isComplete(): Boolean = false // Text mode runs indefinitely.

    override fun expandPool(): List<Char> {
        return emptyList()
    }

    fun getTypedIndex(): Int = typedIndex
}
