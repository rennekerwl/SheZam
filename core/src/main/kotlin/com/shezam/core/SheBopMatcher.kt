package com.shezam.core

class SheBopMatcher(
    private val decisionThreshold: Double = 0.1,
) {
    fun match(observed: List<FingerprintToken>, reference: List<FingerprintToken>): MatchResult {
        if (observed.isEmpty() || reference.isEmpty()) {
            return MatchResult(confidence = 0.0, strongestOffsetVotes = 0, isMatch = false)
        }

        val referenceIndex = reference.groupBy { Triple(it.binA, it.binB, it.deltaFrames) }
        val votes = mutableMapOf<Int, Int>()

        observed.forEach { token ->
            val key = Triple(token.binA, token.binB, token.deltaFrames)
            val hits = referenceIndex[key].orEmpty()
            hits.forEach { refToken ->
                val offset = refToken.frame - token.frame
                votes[offset] = votes.getOrDefault(offset, 0) + 1
            }
        }

        val strongest = votes.maxOfOrNull { it.value } ?: 0
        val confidence = strongest.toDouble() / observed.size.toDouble()
        return MatchResult(
            confidence = confidence,
            strongestOffsetVotes = strongest,
            isMatch = confidence >= decisionThreshold,
        )
    }
}
