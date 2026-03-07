package com.shezam.core

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Lightweight, deterministic fingerprinting for MVP/prototyping.
 *
 * This is intentionally simple:
 * - fixed FFT-size DFT per frame
 * - local spectral peaks in a robust frequency band
 * - anchor-target fanout into compact hash tokens
 */
class SimpleFingerprinter(
    private val sampleRate: Int = 16_000,
    private val frameSize: Int = 1024,
    private val hopSize: Int = 512,
    private val topBinsPerFrame: Int = 8,
    private val anchorPeaksPerFrame: Int = 3,
    private val targetPeaksPerFrame: Int = 3,
    private val targetZoneFrames: Int = 8,
    private val quantizedBinSize: Int = 2,
    private val quantizedDeltaSize: Int = 2,
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
            val windowed = applyHannWindow(pad(frame, frameSize))
            val magnitudes = dftMagnitudes(windowed)
            val logMagnitudes = magnitudes.map { ln(1.0 + it) }.toDoubleArray()
            peaksByFrame += localPeaks(logMagnitudes, topBinsPerFrame)
        }

        val tokens = mutableListOf<FingerprintToken>()
        for (frame in peaksByFrame.indices) {
            val anchors = peaksByFrame[frame].take(anchorPeaksPerFrame)
            if (anchors.isEmpty()) continue

            for (delta in 1..targetZoneFrames) {
                val targetFrame = frame + delta
                if (targetFrame >= peaksByFrame.size) break

                val targets = peaksByFrame[targetFrame].take(targetPeaksPerFrame)
                if (targets.isEmpty()) continue

                anchors.forEach { anchorBin ->
                    targets.forEach { targetBin ->
                        tokens += FingerprintToken(
                            binA = quantizeBin(anchorBin),
                            binB = quantizeBin(targetBin),
                            deltaFrames = quantizeDelta(delta),
                            frame = frame,
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

    private fun applyHannWindow(frame: DoubleArray): DoubleArray {
        val n = frame.size
        if (n <= 1) return frame
        return DoubleArray(n) { idx ->
            val multiplier = 0.5 * (1.0 - cos(2.0 * Math.PI * idx / (n - 1).toDouble()))
            frame[idx] * multiplier
        }
    }

    private fun localPeaks(magnitudes: DoubleArray, count: Int): List<Int> {
        val minBin = binForFrequency(300.0)
        val maxBin = min(magnitudes.lastIndex - 1, binForFrequency(5_000.0))
        if (maxBin <= minBin) return topBins(magnitudes, count)

        val peaks =
            (minBin..maxBin)
                .filter { bin ->
                    bin > 0 &&
                        bin < magnitudes.lastIndex &&
                        magnitudes[bin] > magnitudes[bin - 1] &&
                        magnitudes[bin] >= magnitudes[bin + 1]
                }.map { it to magnitudes[it] }
                .sortedByDescending { it.second }
                .take(count)
                .map { it.first }
                .sorted()

        return if (peaks.isNotEmpty()) peaks else topBins(magnitudes, count)
    }

    private fun topBins(magnitudes: DoubleArray, count: Int): List<Int> =
        magnitudes
            .mapIndexed { index, value -> index to value }
            .sortedByDescending { it.second }
            .take(count)
            .map { it.first }
            .sorted()

    private fun quantizeBin(bin: Int): Int = bin / quantizedBinSize

    private fun quantizeDelta(deltaFrames: Int): Int = ((deltaFrames - 1) / quantizedDeltaSize) + 1

    private fun binForFrequency(frequencyHz: Double): Int =
        (frequencyHz * frameSize / sampleRate.toDouble()).toInt().coerceAtLeast(1)

    fun frequencyForBin(bin: Int): Double = bin.toDouble() * sampleRate / frameSize
}
