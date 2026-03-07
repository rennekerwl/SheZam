package com.shezam.core

class SheBopMatcher(
    private val decisionThreshold: Double = 0.55,
) {
    private companion object {
        const val MAX_OFFSETS_PER_TOKEN = 200
    }

    fun match(observed: List<FingerprintToken>, reference: List<FingerprintToken>): MatchResult {
        if (observed.isEmpty() || reference.isEmpty()) {
            return MatchResult(confidence = 0.0, strongestOffsetVotes = 0, isMatch = false)
        }

        val referenceIndex = reference.groupBy { Triple(it.binA, it.binB, it.deltaFrames) }
        val votes = mutableMapOf<Int, Int>()
        val matchedObservedTokenIdsAtOffset = mutableMapOf<Int, MutableSet<Pair<Int, Int>>>()

        observed.forEach { token ->
            val key = Triple(token.binA, token.binB, token.deltaFrames)
            val hits = referenceIndex[key].orEmpty()

            val distinctOffsets = hits
                .asSequence()
                .map { refToken -> refToken.frame - token.frame }
                .distinct()
                .take(MAX_OFFSETS_PER_TOKEN)
                .toList()

            val observedTokenId = token.frame to key.hashCode()
            distinctOffsets.forEach { offset ->
                votes[offset] = votes.getOrDefault(offset, 0) + 1
                matchedObservedTokenIdsAtOffset
                    .getOrPut(offset) { mutableSetOf() }
                    .add(observedTokenId)
            }
        }

        val strongestOffsetVotes = votes.maxOfOrNull { it.value } ?: 0
        val strongestMatchedObservedTokens = matchedObservedTokenIdsAtOffset
            .maxOfOrNull { (_, observedTokenIds) -> observedTokenIds.size }
            ?: 0
        val confidence = strongestMatchedObservedTokens.toDouble() / observed.size.toDouble()
        return MatchResult(
            confidence = confidence,
            strongestOffsetVotes = strongestOffsetVotes,
            isMatch = confidence >= decisionThreshold,
        )
    }
}
