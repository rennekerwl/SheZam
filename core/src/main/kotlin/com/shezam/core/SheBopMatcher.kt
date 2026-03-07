package com.shezam.core

class SheBopMatcher(
    private val decisionThreshold: Double = 0.55,
) {
    private val offsetPurityWeight = 0.6
    private val queryCoverageWeight = 0.4

    fun match(observed: List<FingerprintToken>, reference: List<FingerprintToken>): MatchResult {
        if (observed.isEmpty() || reference.isEmpty()) {
            return MatchResult(confidence = 0.0, strongestOffsetVotes = 0, isMatch = false)
        }

        val referenceIndex = reference.groupBy { Triple(it.binA, it.binB, it.deltaFrames) }
        val votes = mutableMapOf<Int, Int>()
        val matchedObservedIndicesByOffset = mutableMapOf<Int, MutableSet<Int>>()

        observed.forEachIndexed { observedIndex, token ->
            val key = Triple(token.binA, token.binB, token.deltaFrames)
            val hits = referenceIndex[key].orEmpty()
            hits.forEach { refToken ->
                val offset = refToken.frame - token.frame
                votes[offset] = votes.getOrDefault(offset, 0) + 1
                matchedObservedIndicesByOffset
                    .getOrPut(offset) { mutableSetOf() }
                    .add(observedIndex)
            }
        }

        val strongestOffsetEntry = votes.maxByOrNull { it.value }
        val strongestOffset = strongestOffsetEntry?.key
        val strongest = strongestOffsetEntry?.value ?: 0
        val totalVotes = votes.values.sum()
        val offsetPurity = strongest.toDouble() / maxOf(1, totalVotes).toDouble()
        val matchedQueryTokenCountAtTopOffset = strongestOffset
            ?.let { matchedObservedIndicesByOffset[it]?.size }
            ?: 0
        val queryCoverage = matchedQueryTokenCountAtTopOffset.toDouble() / observed.size.toDouble()
        val confidence = (offsetPurityWeight * offsetPurity + queryCoverageWeight * queryCoverage)
            .coerceIn(0.0, 1.0)

        return MatchResult(
            confidence = confidence,
            strongestOffsetVotes = strongest,
            isMatch = confidence >= decisionThreshold,
        )
    }
}
