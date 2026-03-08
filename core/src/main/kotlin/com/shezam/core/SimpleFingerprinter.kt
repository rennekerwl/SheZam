package com.shezam.core

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Lightweight, deterministic fingerprinting for MVP/prototyping.
 *
 * This is intentionally simple:
 * - fixed FFT-size DFT per frame
 * - top-N spectral bins as local peaks
 * - pair adjacent peaks into compact hash tokens
 */
class SimpleFingerprinter(
    private val sampleRate: Int = 16_000,
    private val frameSize: Int = 1024,
    private val hopSize: Int = 512,
    private val topBinsPerFrame: Int = 5,
    private val targetZoneFrames: Int = 4,
) {
    fun fingerprint(samples: ShortArray): List<FingerprintToken> {
        if (samples.isEmpty()) return emptyList()

        val mono = normalize(samples)
        val frameCount = ((mono.size - frameSize).coerceAtLeast(0) / hopSize) + 1
        if (frameCount <= 0) return emptyList()

        val peaksByFrame = mutableListOf<List<Int>>()
        for (frameIndex in 0 until frameCount) {
            val start = frameIndex * hopSize
            val frame = mono.copyOfRange(start, min(start + frameSize, mono.size))
            val magnitudes = dftMagnitudes(pad(frame, frameSize))
            peaksByFrame += topBins(magnitudes, topBinsPerFrame)
        }

        val tokens = mutableListOf<FingerprintToken>()
        for (anchorFrame in peaksByFrame.indices) {
            val anchorPeaks = peaksByFrame[anchorFrame]
            if (anchorPeaks.isEmpty()) continue

            val maxTargetFrame = min(anchorFrame + targetZoneFrames, peaksByFrame.lastIndex)
            for (targetFrame in (anchorFrame + 1)..maxTargetFrame) {
                val targetPeaks = peaksByFrame[targetFrame]
                if (targetPeaks.isEmpty()) continue

                for (a in anchorPeaks) {
                    for (b in targetPeaks) {
                        tokens += FingerprintToken(
                            binA = a,
                            binB = b,
                            deltaFrames = targetFrame - anchorFrame,
                            frame = anchorFrame,
                        )
                    }
                }
            }
        }
        return tokens
    }

    fun detectQuality(samples: ShortArray): Boolean {
        if (samples.isEmpty()) return false
        val maxAbs = samples.maxOf { kotlin.math.abs(it.toInt()) }
        return maxAbs > 1_000
    }

    private fun normalize(input: ShortArray): DoubleArray {
        val maxAbs = max(1, input.maxOf { kotlin.math.abs(it.toInt()) })
        return DoubleArray(input.size) { idx -> input[idx].toDouble() / maxAbs.toDouble() }
    }

    private fun pad(input: DoubleArray, target: Int): DoubleArray {
        if (input.size >= target) return input
        return DoubleArray(target) { idx -> if (idx < input.size) input[idx] else 0.0 }
    }

    private fun dftMagnitudes(frame: DoubleArray): DoubleArray {
        val n = frame.size
        val usefulBins = n / 2
        val output = DoubleArray(usefulBins)
        for (k in 0 until usefulBins) {
            var real = 0.0
            var imag = 0.0
            for (t in 0 until n) {
                val angle = -2.0 * Math.PI * k * t / n
                real += frame[t] * cos(angle)
                imag += frame[t] * sin(angle)
            }
            output[k] = hypot(real, imag)
        }
        return output
    }

    private fun topBins(magnitudes: DoubleArray, count: Int): List<Int> =
        magnitudes
            .mapIndexed { index, value -> index to value }
            .sortedByDescending { it.second }
            .take(count)
            .map { it.first }
            .sorted()

    fun frequencyForBin(bin: Int): Double = bin.toDouble() * sampleRate / frameSize
}
