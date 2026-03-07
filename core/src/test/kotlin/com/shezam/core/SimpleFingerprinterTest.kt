package com.shezam.core

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

class SimpleFingerprinterTest {
    @Test
    fun `fingerprint generates fanout tokens with quantized deltas`() {
        val fingerprinter = SimpleFingerprinter()
        val sampleRate = 16_000
        val seconds = 2
        val frequencyHz = 440.0

        val samples = ShortArray(sampleRate * seconds) { idx ->
            val value = sin(2.0 * PI * frequencyHz * idx / sampleRate)
            (value * Short.MAX_VALUE).toInt().toShort()
        }

        val tokens = fingerprinter.fingerprint(samples)

        assertTrue(tokens.isNotEmpty(), "Expected non-empty token list for a clean sine tone")
        assertTrue(tokens.any { it.deltaFrames > 1 }, "Expected fanout deltas beyond adjacent frames")
        assertTrue(tokens.all { it.binA >= 0 && it.binB >= 0 }, "Expected non-negative quantized bin IDs")
    }
}
