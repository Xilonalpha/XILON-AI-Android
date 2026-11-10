package com.xilonalpha.xilonai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.sin

/**
 * Offline MP4 export, H.264 video + existing AAC soundtrack.
 * First version deliberately supports AAC tracks only; no network access.
 * Renders a lightweight procedural 2D representation of the music-video dancers.
 * It does NOT capture the Three.js WebGL preview.
 */
object XilonMusicVideoExport {
    private const val WIDTH = 320
    private const val HEIGHT = 240
    private const val FPS = 15

    fun export(context: Context, audioUri: Uri, spec: XilonMusicVideo.Scene, output: File) {
        val extractor = MediaExtractor()
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, audioUri, null)
            val audioTrack = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm"
            } ?: throw IllegalArgumentException("Exportul MP4 acceptă momentan melodii AAC/M4A. Pentru MP3 este necesară transcodarea audio.")
            extractor.selectTrack(audioTrack)
            val audioFormat = extractor.getTrackFormat(audioTrack)
            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION))
                audioFormat.getLong(MediaFormat.KEY_DURATION) else spec.duration.toLong() * 1_000_000L
            val durationSeconds = minOf(
                (durationUs / 1_000_000L).coerceAtLeast(1L),
                spec.duration.toLong().coerceIn(5L, 60L)
            ).toInt()
            val format = MediaFormat.createVideoFormat("video/avc", WIDTH, HEIGHT).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 450_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            }
            encoder = MediaCodec.createEncoderByType("video/avc")
            val codec = encoder
            val supported = codec.codecInfo.getCapabilitiesForType("video/avc").colorFormats
            require(supported.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)) {
                "Encoderul acestui telefon nu acceptă NV12; exportul necesită alt format YUV."
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val writer = muxer
            val muxAudioTrack = writer.addTrack(audioFormat)
            var muxVideoTrack = -1
            var started = false
            val info = MediaCodec.BufferInfo()
            val totalFrames = durationSeconds * FPS
            var queued = 0
            var done = false
            val deadlineMs = android.os.SystemClock.elapsedRealtime() + 180_000L
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            try {
                while (!done) {
                    check(android.os.SystemClock.elapsedRealtime() < deadlineMs) {
                        "Encoderul H.264 nu a finalizat exportul în 180 de secunde."
                    }
                    if (queued <= totalFrames) {
                        val idx = codec.dequeueInputBuffer(10_000)
                        if (idx >= 0) {
                            if (queued == totalFrames) {
                                codec.queueInputBuffer(idx, 0, 0, totalFrames * 1_000_000L / FPS,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            } else {
                                drawFrame(bitmap, spec, queued.toDouble() / FPS)
                                val bytes = toNv12(bitmap)
                                val buffer = codec.getInputBuffer(idx) ?: error("Encoder input unavailable")
                                require(buffer.capacity() >= bytes.size)
                                buffer.clear()
                                buffer.put(bytes)
                                codec.queueInputBuffer(idx, 0, bytes.size,
                                    queued * 1_000_000L / FPS, 0)
                            }
                            queued++
                        }
                    }
                    when (val out = codec.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            muxVideoTrack = writer.addTrack(codec.outputFormat)
                            writer.start()
                            started = true
                        }
                        else -> if (out >= 0) {
                            if (info.size > 0 && started && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                val bytes = codec.getOutputBuffer(out) ?: error("Encoder output unavailable")
                                bytes.position(info.offset)
                                bytes.limit(info.offset + info.size)
                                writer.writeSampleData(muxVideoTrack, bytes, info)
                            }
                            done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(out, false)
                        }
                    }
                }
                require(started) { "Encoderul nu a furnizat format video." }
                val buffer = ByteBuffer.allocateDirect(1024 * 1024)
                val audioInfo = MediaCodec.BufferInfo()
                while (true) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val timestamp = extractor.sampleTime
                    if (timestamp < 0 || timestamp >= durationSeconds * 1_000_000L) break
                    require(size <= buffer.capacity()) { "Cadru audio prea mare." }
                    audioInfo.set(0, size, timestamp, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    buffer.position(0)
                    buffer.limit(size)
                    writer.writeSampleData(muxAudioTrack, buffer, audioInfo)
                    extractor.advance()
                }
                writer.stop()
                started = false
            } finally {
                bitmap.recycle()
                if (started) runCatching { writer.stop() }
            }
        } catch (e: Exception) {
            output.delete()
            throw e
        } finally {
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { muxer?.release() }
            extractor.release()
        }
    }

    /** Rejects truncated or silent-track MP4 outputs before copying them to user storage. */
    fun verifyExport(file: File) {
        require(file.isFile && file.length() > 1024L) { "Fișierul MP4 este gol sau incomplet." }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var hasVideo = false
            var hasAudio = false
            for (track in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime == "video/avc") hasVideo = true
                if (mime == "audio/mp4a-latm") hasAudio = true
            }
            require(hasVideo && hasAudio) {
                "Exportul MP4 nu conține ambele piste H.264 și AAC."
            }
        } finally {
            extractor.release()
        }
    }

    private fun drawFrame(bitmap: Bitmap, spec: XilonMusicVideo.Scene, t: Double) {
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(when (spec.theme) {
            "space" -> Color.rgb(8, 11, 36)
            "sunset" -> Color.rgb(73, 36, 58)
            else -> Color.rgb(16, 12, 37)
        })
        paint.color = Color.rgb(65, 62, 94)
        canvas.drawRect(0f, 190f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        for (i in 0 until spec.characters) {
            val x = WIDTH * (i + 1f) / (spec.characters + 1)
            val beat = t * spec.bpm * Math.PI / 30 + i
            val bounce = (sin(beat) * 8).toFloat()
            paint.color = intArrayOf(Color.CYAN, Color.MAGENTA, Color.YELLOW)[i]
            canvas.drawRoundRect(x - 17, 110f + bounce, x + 17, 169f + bounce, 12f, 12f, paint)
            paint.color = Color.rgb(229, 180, 142)
            canvas.drawCircle(x, 91f + bounce, 17f, paint)
            paint.strokeWidth = 8f
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = Color.WHITE
            val swing = (sin(beat) * 18).toFloat()
            canvas.drawLine(x - 16, 125f + bounce, x - 34, 145f + bounce + swing, paint)
            canvas.drawLine(x + 16, 125f + bounce, x + 34, 145f + bounce - swing, paint)
            canvas.drawLine(x - 9, 168f + bounce, x - 12 + swing, 190f, paint)
            canvas.drawLine(x + 9, 168f + bounce, x + 12 - swing, 190f, paint)
        }
    }

    private fun toNv12(bitmap: Bitmap): ByteArray {
        val pixels = IntArray(WIDTH * HEIGHT)
        bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        val out = ByteArray(WIDTH * HEIGHT * 3 / 2)
        val uvOffset = WIDTH * HEIGHT
        for (y in 0 until HEIGHT) for (x in 0 until WIDTH) {
            val p = pixels[y * WIDTH + x]
            val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
            val yy = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
            out[y * WIDTH + x] = yy.coerceIn(0, 255).toByte()
            if (y % 2 == 0 && x % 2 == 0) {
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                val idx = uvOffset + y / 2 * WIDTH + x
                out[idx] = u.coerceIn(0, 255).toByte()
                out[idx + 1] = v.coerceIn(0, 255).toByte()
            }
        }
        return out
    }
}
