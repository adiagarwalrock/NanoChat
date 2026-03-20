package com.fcm.nanochat.inference

object RepetitionDetector {
    /**
     * Determines if the given visible output is likely caught in a runaway repetition loop. This
     * checks for both single-character repeats (e.g. "########") and multi-word phrase limits (e.g.
     * "be prepared for potential threats").
     */
    fun isLikelyDegenerate(output: String): Boolean {
        if (output.isBlank() || output.length < 64) return false

        if (hasSingleCharacterDegeneracy(output)) return true
        if (hasPhraseRepetitionLoop(output)) return true

        return false
    }

    private fun hasSingleCharacterDegeneracy(output: String): Boolean {
        val compact = output.filterNot(Char::isWhitespace)
        if (compact.length < 64) return false

        val first = compact.first()
        if (first.isLetterOrDigit()) return false

        val punctuationCandidates = setOf('#', '\'', '$', '-', '_', '=', '|', '`', '.', '*', '+')
        if (first !in punctuationCandidates) return false

        val sameRatio = compact.count { it == first }.toDouble() / compact.length
        return sameRatio >= 0.95
    }

    /**
     * Looks for phrase repetition at the end of the generated output. Specifically, looks for a
     * block of length N (between 10 and 120 chars) that repeats itself consecutively 3 or more
     * times.
     */
    internal fun hasPhraseRepetitionLoop(output: String, repeatThreshold: Int = 3): Boolean {
        val minBlockLength = 10
        val maxBlockLength = 200

        val text = output.trimEnd()
        val textLength = text.length

        if (textLength < minBlockLength * repeatThreshold) return false

        // Check various block sizes starting from the end of the string
        for (blockSize in minBlockLength..maxBlockLength) {
            if (textLength < blockSize * repeatThreshold) {
                break // String isn't long enough to hold this many repeats of this block size
            }

            val candidateBlock = text.substring(textLength - blockSize)
            var repeatCount = 1

            for (i in 1 until repeatThreshold) {
                val startIdx = textLength - (blockSize * (i + 1))
                val endIdx = textLength - (blockSize * i)
                val precedingBlock = text.substring(startIdx, endIdx)

                if (precedingBlock == candidateBlock) {
                    repeatCount++
                } else {
                    break
                }
            }

            if (repeatCount >= repeatThreshold) {
                return true
            }
        }

        return false
    }
}
