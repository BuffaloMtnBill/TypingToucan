package com.typingtoucan.systems

data class PassageItem(val text: String, val metadata: String)

/**
 * Typing source that serves random passages sequentially.
 *
 * @param allPassages List of all available passages to cycle through.
 */
class TextSnippetSource(
        private val allPassages: List<PassageItem>,
        private val sequential: Boolean = false
) : TypingSource {
    override fun setCapitalsEnabled(enabled: Boolean) {
        // No-op for text mode
    }

    // State pointers.
    private var currentIndex = 0 // Global read pointer across passages.
    private var typedIndex = 0 // Tracks progress in CURRENT passage text.
    // For sequential mode.
    private var listIndex = 0

    // Formatting.
    val displayLines = mutableListOf<String>()
    private var processedText: String = ""

    // Pooled UI state (Optimization #156).
    private val cachedDisplayState = DisplayState("", "", "", 0, 0)

    // Metadata.
    // We track current passage and next passage info for seamless transition.
    var sourceMetadata = ""
    private var nextSourceMetadata = ""
    private var nextDisplayLines = listOf<String>()
    private var nextProcessedText: String = ""

    // Initialize first passage
    init {
        if (allPassages.isEmpty()) throw IllegalArgumentException("Passages cannot be empty")
        // Load initial passage.
        if (sequential) {
            listIndex = 0
            setupCurrent(allPassages[0])
        } else {
            val first = allPassages.random()
            setupCurrent(first)
        }
        preloadNext()
    }

    // Helpers to process text
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

    private fun preloadNext() {
        val p =
                if (sequential) {
                    // Check if we just loaded final credit? Loop?
                    // User says "autoplays... display credits". Usually loops or ends?
                    // Infinite loop is safest for Text Mode architecture.
                    val nextIdx = (listIndex + 1) % allPassages.size
                    // Note: listIndex is tracking CURRENTLY PRELOADED next.
                    // Wait. init calls setupCurrent (idx 0), then preloadNext.
                    // preloadNext should load idx 1.
                    // So if listIndex was used for CURRENT, we increment first.
                    // If listIndex=0 (Current). Next is 1.
                    // But I updated listIndex in init? No.
                    // If sequential: listIndex=0. setupCurrent(0).
                    // preloadNext logic needs to increment first.
                    // BUT listIndex should track what is IN next? Or what was LAST used?
                    // Let's assume listIndex tracks the one currently in 'processedText' (or
                    // 'nextProcessedText'?)
                    // If init: listIndex=0. Used by Current.
                    // preloadNext: listIndex becomes 1. Used by Next.
                    // swap: Current becomes Next (idx 1).
                    // preloadNext: listIndex becomes 2.
                    // Matches.
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
        // Fetch from Current or Next
        // We do NOT swap passages here. Queue pre-fetches.

        if (currentIndex < processedText.length) {
            return processedText[currentIndex++]
        }

        // Reading from Next
        val nextIndex = currentIndex - processedText.length
        if (nextIndex < nextProcessedText.length) {
            currentIndex++
            return nextProcessedText[nextIndex]
        }

        // Next exhausted? Cycle (should be rare given update rate)
        // For robustness, could force a preload here, but simplest logic assumes swap happens
        // eventually.
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
        // Current becomes Next
        sourceMetadata = nextSourceMetadata
        displayLines.clear()
        displayLines.addAll(nextDisplayLines)
        processedText = nextProcessedText

        val oldLen = processedText.length

        // Rebase
        // Hard reset to 3 matches the TypingQueue buffer size.
        // This ensures that we are reading exactly from the 4th character of the NEW passage,
        // which corresponds to the fact that the queue already holds [Char0, Char1, Char2].
        currentIndex = 3
        typedIndex = 0 // Reset user progress for new passage

        // Preload new Next
        preloadNext()
    }

    override fun onCharTyped(char: Char) {
        typedIndex++

        // Check for completion.
        // If we finished typing current text (including trailing spaces/joins).
        if (typedIndex >= processedText.length) {
            performSwap()
        }
    }

    override fun onCrash(char: Char) {
        // No logic needed
    }

    // UI helpers.

    class DisplayState(
            var currentLine: String,
            var nextLine: String,
            var prevLine: String,
            var localProgress: Int,
            var lineIndex: Int
    )

    fun getDisplayState(): DisplayState {
        // Calculate which line matches 'typedIndex'
        var charCount = 0
        var lineIdx = 0

        for (i in displayLines.indices) {
            val len = displayLines[i].length + 1 // space join
            if (typedIndex < charCount + len) {
                lineIdx = i
                break
            }
            charCount += len
            lineIdx = i // Safety clamp
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

        // Local progress
        // Re-calculate start CharCount for this line strictly
        // We need exact start index of current line
        var startCharIdx = 0
        for (i in 0 until lineIdx) {
            startCharIdx += displayLines[i].length + 1
        }

        val localProg = (typedIndex - startCharIdx).coerceIn(0, currentStr.length)

        // Update cached state (Optimization #156).
        cachedDisplayState.currentLine = currentStr
        cachedDisplayState.nextLine = nextStr
        cachedDisplayState.prevLine = prevStr
        cachedDisplayState.localProgress = localProg
        cachedDisplayState.lineIndex = lineIdx

        return cachedDisplayState
    }

    override fun getProgressDisplay(): String {
        return "Inf" // Infinite mode
    }

    override fun isComplete(): Boolean {
        // Never complete in this mode
        return false
    }

    override fun expandPool(): List<Char> {
        return emptyList()
    }

    fun getTypedIndex(): Int = typedIndex
}
