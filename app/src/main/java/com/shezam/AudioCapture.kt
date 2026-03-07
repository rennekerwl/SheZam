package com.shezam

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioCapture(
    private val sampleRate: Int = 16_000,
    private val captureSeconds: Int = 5,
) {
    suspend fun capture(): Result<ShortArray> = withContext(Dispatchers.IO) {
        runCatching {
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            require(minBuffer > 0) { "Unable to initialize recorder buffer." }

            val samplesToRead = sampleRate * captureSeconds
            val readBuffer = ShortArray(samplesToRead)
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer.coerceAtLeast(samplesToRead * 2),
            )

            recorder.startRecording()
            var totalRead = 0
            while (totalRead < samplesToRead) {
                val read = recorder.read(readBuffer, totalRead, samplesToRead - totalRead)
                if (read <= 0) break
                totalRead += read
            }
            recorder.stop()
            recorder.release()

            readBuffer.copyOf(totalRead)
        }
    }
}
