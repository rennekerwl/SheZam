package com.shezam.core

import kotlin.test.Test
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
    fun `confidence denominator is capped for dense token streams`() {
        val matcher = SheBopMatcher(decisionThreshold = 0.15, confidenceDenominatorCap = 100)
        val reference = (0 until 300).map {
            FingerprintToken(binA = it % 50, binB = (it + 7) % 50, deltaFrames = 1, frame = it)
        }
        val observed = (0 until 600).map {
            FingerprintToken(binA = it % 50, binB = (it + 7) % 50, deltaFrames = 1, frame = it)
        }

        val result = matcher.match(observed, reference)
        assertTrue(result.confidence > 0.15)
        assertTrue(result.isMatch)
    }

}
