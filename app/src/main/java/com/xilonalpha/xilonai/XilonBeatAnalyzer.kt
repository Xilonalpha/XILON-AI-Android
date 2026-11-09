package com.xilonalpha.xilonai

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Local PCM onset-envelope BPM estimator. Runs on a worker thread only.
 * Estimates tempo from decoded audio; not a beat-perfect music transcription.
 */
object XilonBeatAnalyzer {
    fun estimateBpm(context: Context, uri: Uri): Int? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            if (rate <= 0) return null
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
            val amplitudes = ArrayList<Double>()
            val frameSamples = (rate / 50).coerceAtLeast(1) // 20ms envelope
            var sum = 0.0
            var count = 0
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()
            val started = android.os.SystemClock.elapsedRealtime()
            while (!outputDone && amplitudes.size < 3000 &&
                android.os.SystemClock.elapsedRealtime() - started < 20_000L) {
                if (!inputDone) {
                    val idx = decoder.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val buffer = decoder.getInputBuffer(idx) ?: break
                        val bytes = extractor.readSampleData(buffer, 0)
                        if (bytes < 0) {
                            decoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(idx, 0, bytes, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val idx = decoder.dequeueOutputBuffer(info, 10_000)
                if (idx >= 0) {
                    val pcmFormat = decoder.outputFormat
                    val encoding = if (pcmFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                        pcmFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else 2
                    val pcm = decoder.getOutputBuffer(idx)
                    if (pcm != null && info.size > 0 && encoding == 2) {
                        pcm.position(info.offset)
                        pcm.limit(info.offset + info.size)
                        val shorts = pcm.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        while (shorts.hasRemaining()) {
                            val v = shorts.get().toDouble() / 32768.0
                            sum += v * v
                            count++
                            if (count >= frameSamples * channels) {
                                amplitudes.add(sqrt(sum / count))
                                sum = 0.0
                                count = 0
                            }
                        }
                    }
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(idx, false)
                }
            }
            return estimateFromEnvelope(amplitudes)
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            extractor.release()
        }
    }

    fun estimateFromEnvelope(envelope: List<Double>): Int? {
        if (envelope.size < 250) return null
        val onset = DoubleArray(envelope.size) { i ->
            if (i == 0) 0.0 else (envelope[i] - envelope[i - 1]).coerceAtLeast(0.0)
        }
        val mean = onset.average()
        if (mean < 0.000001) return null
        var bestBpm = 0
        var bestScore = 0.0
        for (bpm in 60..180) {
            val lag = (3000.0 / bpm).toInt()
            var score = 0.0
            for (i in lag until onset.size) score += onset[i] * onset[i - lag]
            score /= (onset.size - lag)
            if (score > bestScore) {
                bestScore = score
                bestBpm = bpm
            }
        }
        return bestBpm.takeIf { bestScore > mean * mean * 1.2 }
    }
}
