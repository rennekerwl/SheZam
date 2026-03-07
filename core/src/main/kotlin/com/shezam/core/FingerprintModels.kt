package com.shezam.core

data class FingerprintToken(
    val binA: Int,
    val binB: Int,
    val deltaFrames: Int,
    val frame: Int,
)

data class MatchResult(
    val confidence: Double,
    val strongestOffsetVotes: Int,
    val isMatch: Boolean,
)
