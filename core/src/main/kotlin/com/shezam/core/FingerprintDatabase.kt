package com.shezam.core

object FingerprintDatabase {
    fun fromCsv(csv: String): List<FingerprintToken> =
        csv
            .lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val (binA, binB, deltaFrames, frame) = line.split(',')
                FingerprintToken(
                    binA = binA.trim().toInt(),
                    binB = binB.trim().toInt(),
                    deltaFrames = deltaFrames.trim().toInt(),
                    frame = frame.trim().toInt(),
                )
            }.toList()
}
