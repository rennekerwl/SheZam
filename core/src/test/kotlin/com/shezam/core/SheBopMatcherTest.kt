package com.shezam.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SheBopMatcherTest {
    private val matcher = SheBopMatcher(decisionThreshold = 0.6)

    @Test
    fun `returns true for strong overlap`() {
        val reference = (0 until 20).map {
            FingerprintToken(binA = 10 + it, binB = 20 + it, deltaFrames = 1, frame = it)
        }
        val observed = (0 until 20).map {
            FingerprintToken(binA = 10 + it, binB = 20 + it, deltaFrames = 1, frame = it)
        }

        val result = matcher.match(observed, reference)
        assertTrue(result.isMatch)
    }

    @Test
    fun `returns false for weak overlap`() {
        val reference = (0 until 20).map {
            FingerprintToken(binA = 10 + it, binB = 20 + it, deltaFrames = 1, frame = it)
        }
        val observed = (0 until 20).map {
            FingerprintToken(binA = 500 + it, binB = 700 + it, deltaFrames = 1, frame = it)
        }

        val result = matcher.match(observed, reference)
        assertFalse(result.isMatch)
    }

    @Test
    fun `duplicate reference hashes do not inflate confidence`() {
        val repeatedHashFrame = 42
        val repeatedHash = FingerprintToken(binA = 1, binB = 2, deltaFrames = 3, frame = repeatedHashFrame)
        val reference = List(100) { repeatedHash }

        val observed = listOf(
            FingerprintToken(binA = 1, binB = 2, deltaFrames = 3, frame = repeatedHashFrame),
            FingerprintToken(binA = 100, binB = 200, deltaFrames = 1, frame = 10),
            FingerprintToken(binA = 101, binB = 201, deltaFrames = 1, frame = 20),
            FingerprintToken(binA = 102, binB = 202, deltaFrames = 1, frame = 30),
            FingerprintToken(binA = 103, binB = 203, deltaFrames = 1, frame = 40),
        )

        val result = matcher.match(observed, reference)

        assertEquals(1, result.strongestOffsetVotes)
        assertEquals(0.2, result.confidence)
        assertFalse(result.isMatch)
    }
}
