package com.fcm.nanochat.inference

class StopSequenceDetector(private val stopSequences: List<String>) {
    var foundSequence: String? = null
        private set

    fun hasStopSequence(output: String): Boolean {
        if (stopSequences.isEmpty()) return false

        for (sequence in stopSequences) {
            if (output.contains(sequence)) {
                foundSequence = sequence
                return true
            }
        }
        return false
    }

    fun trimStopSequence(output: String): String {
        val sequence = foundSequence ?: return output
        val index = output.indexOf(sequence)
        if (index != -1) {
            return output.substring(0, index)
        }
        return output
    }
}
