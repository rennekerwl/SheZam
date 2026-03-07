package com.shezam.core

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Lightweight, deterministic fingerprinting for MVP/prototyping.
 *
 * Tuned to reduce collisions:
 * - fewer salient peaks per frame (lower fanout density)
 * - salience floor to reject weak/noisy peaks
 * - deterministic anchor/target pairing in a narrow target zone
 * - no coarse quantization (raw bin + frame deltas)
 */
class SimpleFingerprinter(
    private val sampleRate: Int = 16_000,
    private val frameSize: Int = 1024,
    private val hopSize: Int = 512,
    private val topBinsPerFrame: Int = 3,
    private val peakSalienceThresholdRatio: Double = 0.45,
    private val targetZoneMaxDeltaFrames: Int = 2,
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
            peaksByFrame += topSalientBins(magnitudes, topBinsPerFrame, peakSalienceThresholdRatio)
        }

        val tokens = mutableListOf<FingerprintToken>()
        for (anchorFrame in peaksByFrame.indices) {
            val anchors = peaksByFrame[anchorFrame]
            if (anchors.isEmpty()) continue

            val maxTargetFrame = min(peaksByFrame.lastIndex, anchorFrame + targetZoneMaxDeltaFrames)
            for (targetFrame in (anchorFrame + 1)..maxTargetFrame) {
                val targets = peaksByFrame[targetFrame]
                if (targets.isEmpty()) continue
                for (anchorBin in anchors) {
                    for (targetBin in targets) {
                        tokens += FingerprintToken(
                            binA = anchorBin,
                            binB = targetBin,
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

    private fun topSalientBins(
        magnitudes: DoubleArray,
        count: Int,
        salienceThresholdRatio: Double,
    ): List<Int> {
        if (magnitudes.isEmpty()) return emptyList()
        val maxMagnitude = magnitudes.maxOrNull() ?: return emptyList()
        if (maxMagnitude <= 0.0) return emptyList()

        val threshold = maxMagnitude * salienceThresholdRatio
        return magnitudes
            .mapIndexed { index, value -> index to value }
            .filter { it.second >= threshold }
            .sortedByDescending { it.second }
            .take(count)
            .map { it.first }
            .sorted()
    }

    fun frequencyForBin(bin: Int): Double = bin.toDouble() * sampleRate / frameSize
}
