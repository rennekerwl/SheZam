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
    fun `confidence reflects offset consistency and coverage`() {
        val observed = (0 until 10).map {
            FingerprintToken(binA = 10 + it, binB = 20 + it, deltaFrames = 1, frame = it)
        }
        val reference = observed + observed.map {
            it.copy(frame = it.frame + 100)
        }

        val result = matcher.match(observed, reference)

        assertTrue(result.confidence < 1.0)
        assertTrue(result.confidence > 0.6)
    }
}
