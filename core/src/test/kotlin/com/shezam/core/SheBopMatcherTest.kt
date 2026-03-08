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
    fun `confidence uses observed token count as denominator`() {
        val reference = (0 until 120).map {
            FingerprintToken(binA = 10 + it, binB = 20 + it, deltaFrames = 1, frame = it)
        }
        val observed = (0 until 20).map {
            FingerprintToken(binA = 10 + it, binB = 20 + it, deltaFrames = 1, frame = it)
        }

        val result = matcher.match(observed, reference)

        assertEquals(20, result.strongestOffsetVotes)
        assertEquals(1.0, result.confidence)
        assertTrue(result.isMatch)
    }
}
