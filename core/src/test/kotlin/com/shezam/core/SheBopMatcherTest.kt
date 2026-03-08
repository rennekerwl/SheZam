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
    fun `returns false when top offsets are too close`() {
        val observed = (0 until 10).map {
            FingerprintToken(binA = 100 + it, binB = 200 + it, deltaFrames = 1, frame = it)
        }
        val reference = buildList {
            addAll((0 until 7).map {
                FingerprintToken(binA = 100 + it, binB = 200 + it, deltaFrames = 1, frame = it + 10)
            })
            addAll((0 until 6).map {
                FingerprintToken(binA = 100 + it, binB = 200 + it, deltaFrames = 1, frame = it + 20)
            })
        }

        val result = matcher.match(observed, reference)
        assertFalse(result.isMatch)
    }

    @Test
    fun `returns true when strongest offset clearly dominates`() {
        val observed = (0 until 10).map {
            FingerprintToken(binA = 300 + it, binB = 400 + it, deltaFrames = 1, frame = it)
        }
        val reference = buildList {
            addAll((0 until 8).map {
                FingerprintToken(binA = 300 + it, binB = 400 + it, deltaFrames = 1, frame = it + 10)
            })
            addAll((0 until 4).map {
                FingerprintToken(binA = 300 + it, binB = 400 + it, deltaFrames = 1, frame = it + 20)
            })
        }

        val result = matcher.match(observed, reference)
        assertTrue(result.isMatch)
    }
}
