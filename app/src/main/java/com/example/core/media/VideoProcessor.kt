package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.core.zlc.ZlcMp4Util
import com.example.util.DebugLogManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume

object VideoProcessor {
    private const val TAG = "VideoProcessor"

    /**
     * 综合读取视频真实总时长（毫秒）：
     * 同时比对 MediaMetadataRetriever、MediaExtractor 视频轨/音频轨 KEY_DURATION 以及 MediaStore 数据库列，
     * 防止部分 MP4/MOV 容器头 mvhd 写入截断时长（例如 60s 视频被读成 23s）。
     */
    fun getVideoDuration(context: Context, videoUri: Uri): Long {
        var retrieverDurationMs = 0L
        var extractorDurationMs = 0L
        var mediaStoreDurationMs = 0L

        // 1. MediaMetadataRetriever
        val retriever = MediaMetadataRetriever()
        try {
            if (videoUri.scheme == "file" && videoUri.path != null) {
                retriever.setDataSource(videoUri.path)
            } else {
                var setOk = false
                try {
                    context.contentResolver.openFileDescriptor(videoUri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                        setOk = true
                    }
                } catch (_: Exception) {}
                if (!setOk) {
                    retriever.setDataSource(context, videoUri)
                }
            }
            retrieverDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            DebugLogManager.w(TAG, "Retriever duration read warning: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        // 2. MediaExtractor track KEY_DURATION (often more accurate than container mvhd on edited/remuxed videos)
        val extractor = MediaExtractor()
        try {
            var dataSourceSet = false
            if (videoUri.scheme == "file" && videoUri.path != null) {
                extractor.setDataSource(videoUri.path!!)
                dataSourceSet = true
            } else {
                try {
                    context.contentResolver.openFileDescriptor(videoUri, "r")?.use { pfd ->
                        extractor.setDataSource(pfd.fileDescriptor)
                        dataSourceSet = true
                    }
                } catch (_: Exception) {}
                if (!dataSourceSet) {
                    extractor.setDataSource(context, videoUri, null)
                    dataSourceSet = true
                }
            }
            if (dataSourceSet) {
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if ((mime.startsWith("video/") || mime.startsWith("audio/")) &&
                        format.containsKey(MediaFormat.KEY_DURATION)
                    ) {
                        val trackDurMs = format.getLong(MediaFormat.KEY_DURATION) / 1000L
                        if (trackDurMs > extractorDurationMs) {
                            extractorDurationMs = trackDurMs
                        }
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {}
        }

        // 3. MediaStore column fallback for content:// URIs
        if (videoUri.scheme == "content") {
            try {
                context.contentResolver.query(
                    videoUri,
                    arrayOf(MediaStore.Video.Media.DURATION),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                        if (idx != -1) {
                            mediaStoreDurationMs = cursor.getLong(idx)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val resolved = maxOf(retrieverDurationMs, extractorDurationMs, mediaStoreDurationMs)
        DebugLogManager.d(
            TAG,
            "getVideoDuration: uri=$videoUri -> resolved=${resolved}ms (retriever=$retrieverDurationMs, extractor=$extractorDurationMs, mediaStore=$mediaStoreDurationMs)"
        )
        return resolved
    }

    fun extractVideoFrame(
        context: Context,
        videoUri: Uri,
        timeMs: Long,
        targetWidth: Int = 1280,
        targetHeight: Int = 1280
    ): Bitmap? {
        return VideoThumbnailCache.extractFrameBlocking(
            context = context,
            uri = videoUri,
            timeMs = timeMs,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )
    }

    fun isPlayableMp4(file: File, requireAudio: Boolean = false): Boolean {
        if (!file.exists() || file.length() <= 512L) return false
        if (file.length() <= 60L * 1024 * 1024) {
            try {
                val raw = file.readBytes()
                if (ZlcMp4Util.hasQuickTimeSampleEntriesOrTracks(raw)) {
                    return false
                }
            } catch (_: Exception) {}
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val videoStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val audioStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val hasVideo = videoStr.equals("yes", ignoreCase = true) || videoStr == "true" || videoStr == "1"
            val hasAudio = audioStr.equals("yes", ignoreCase = true) || audioStr == "true" || audioStr == "1"
            hasVideo && durationMs >= 50L && (!requireAudio || hasAudio)
        } catch (_: Exception) {
            false
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * 快速无损修复 QuickTime MOV 或被改写 ftyp 的异常 MP4 为标准 ISO MP4（视频 1:1 零重编码 + AAC 标准音轨）。
     * 若无损重封装仍无法播放，再回退到 Media3 Transformer 转码。
     */
    fun healToStandardMp4(context: Context, inputFile: File, outputFile: File): Boolean {
        if (!inputFile.exists() || inputFile.length() <= 64L) return false
        try {
            if (inputFile.length() <= 80L * 1024 * 1024) {
                val rawBytes = inputFile.readBytes()
                val sanitized = ZlcMp4Util.sanitizeVideoTracksIfNeeded(rawBytes, context.cacheDir)
                if (sanitized.size > 64 && !ZlcMp4Util.hasQuickTimeSampleEntriesOrTracks(sanitized)) {
                    outputFile.parentFile?.mkdirs()
                    outputFile.writeBytes(sanitized)
                    if (isPlayableMp4(outputFile)) {
                        DebugLogManager.i(TAG, "healToStandardMp4 succeeded via lossless remux: ${outputFile.name}")
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogManager.w(TAG, "Lossless healToStandardMp4 warning: ${e.message}")
        }
        return transcodeToStandardMp4Sync(
            context = context,
            inputUri = Uri.fromFile(inputFile),
            outputFile = outputFile,
            includeAudio = true
        )
    }

    fun isQuickTimeMov(file: File): Boolean {
        if (!file.exists() || file.length() < 12) return false
        return try {
            if (file.length() <= 60L * 1024 * 1024) {
                val bytes = file.readBytes()
                if (ZlcMp4Util.hasQuickTimeSampleEntriesOrTracks(bytes)) return true
            }
            val header = ByteArray(12)
            file.inputStream().use { it.read(header) }
            val ftyp = String(header, 4, 4, Charsets.US_ASCII)
            val brand = String(header, 8, 4, Charsets.US_ASCII)
            ftyp == "ftyp" && brand == "qt  "
        } catch (_: Exception) {
            false
        }
    }

    fun trimVideo(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long,
        includeAudio: Boolean = true
    ): Boolean {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val transcodeMode = prefs.getString("video_transcode_mode", "remux") ?: "remux"
        DebugLogManager.i(
            TAG,
            "Starting trimVideo: startMs=$startMs, endMs=$endMs, includeAudio=$includeAudio, transcodeMode=$transcodeMode, out=${outputFile.name}"
        )
        val tempLocalFile = File(context.cacheDir, "temp_src_${System.currentTimeMillis()}.mp4")
        try {
            if (inputUri.scheme == "file" && inputUri.path != null) {
                File(inputUri.path!!).copyTo(tempLocalFile, overwrite = true)
            } else {
                context.contentResolver.openInputStream(inputUri)?.use { input ->
                    tempLocalFile.outputStream().buffered(64 * 1024).use { output ->
                        input.copyTo(output, 64 * 1024)
                    }
                }
            }
            if (!tempLocalFile.exists() || tempLocalFile.length() == 0L) {
                DebugLogManager.e(TAG, "Failed to copy input video to local cache")
                return false
            }

            if (tempLocalFile.length() <= 80L * 1024 * 1024) {
                try {
                    val rawBytes = tempLocalFile.readBytes()
                    when (transcodeMode) {
                        "off" -> {
                            val sanitized = ZlcMp4Util.sanitizeVideoTracksIfNeeded(rawBytes, context.cacheDir)
                            if (sanitized !== rawBytes && sanitized.size > 64) {
                                tempLocalFile.writeBytes(sanitized)
                            }
                        }
                        "encode" -> {
                            val sanitized = ZlcMp4Util.sanitizeVideoTracksIfNeeded(rawBytes, context.cacheDir)
                            if (sanitized !== rawBytes && sanitized.size > 64) {
                                tempLocalFile.writeBytes(sanitized)
                            }
                            val compat = ZlcMp4Util.videoCompat(sanitized)
                            if (compat.needsReencode) {
                                DebugLogManager.i(TAG, "ZLC videoCompat triggered re-encode: ${compat.reasons.joinToString("; ")}")
                                val encodedOk = transcodeToStandardMp4Sync(
                                    context = context,
                                    inputUri = Uri.fromFile(tempLocalFile),
                                    outputFile = outputFile,
                                    startMs = startMs,
                                    endMs = endMs,
                                    includeAudio = includeAudio
                                )
                                if (encodedOk && outputFile.exists() && outputFile.length() > 512L) {
                                    tempLocalFile.delete()
                                    return true
                                }
                            }
                        }
                        else -> {
                            // Default "remux": ZLC zero-copy normalizeMovToMp4 + fallback VideoTrackSanitizer
                            val sanitized = ZlcMp4Util.sanitizeVideoTracksIfNeeded(rawBytes, context.cacheDir)
                            if (sanitized !== rawBytes && sanitized.size > 64) {
                                tempLocalFile.writeBytes(sanitized)
                            }
                        }
                    }
                } catch (e: Exception) {
                    DebugLogManager.w(TAG, "ZlcMp4Util normalize skipped: ${e.message}")
                }
            }

            val totalDuration = getVideoDuration(context, Uri.fromFile(tempLocalFile))
            if (includeAudio && startMs <= 0L && (endMs <= 0L || totalDuration <= 0L || endMs >= totalDuration - 80L)) {
                DebugLogManager.i(TAG, "Video requires no trimming, performing direct 1:1 stream copy")
                tempLocalFile.copyTo(outputFile, overwrite = true)
                return outputFile.exists() && outputFile.length() > 0L
            }
        } catch (e: Exception) {
            DebugLogManager.e(TAG, "Error preparing local video file: ${e.message}", e)
            tempLocalFile.delete()
            return false
        }

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            if (outputFile.exists()) outputFile.delete()
            extractor.setDataSource(tempLocalFile.absolutePath)

            val trackCount = extractor.trackCount
            var videoTrackIdx = -1
            var audioTrackIdx = -1

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && videoTrackIdx == -1) {
                    videoTrackIdx = i
                } else if (mime.startsWith("audio/") && audioTrackIdx == -1) {
                    audioTrackIdx = i
                }
            }

            if (videoTrackIdx == -1) {
                DebugLogManager.w(TAG, "No video track found in MediaExtractor, falling back to direct copy")
                tempLocalFile.copyTo(outputFile, overwrite = true)
                return outputFile.exists() && outputFile.length() > 0L
            }

            val startUs = (startMs * 1000L).coerceAtLeast(0L)
            val endUs = if (endMs > 0L) endMs * 1000L else Long.MAX_VALUE

            // Select ONLY the video track first to locate the exact sync keyframe timestamp (`videoBaseUs`).
            // If we selected audio before seeking, `extractor.sampleTime` after seek could belong to an audio sample
            // at a different timestamp, misaligning video PTS and audio PTS.
            extractor.selectTrack(videoTrackIdx)
            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }
            val syncVideoBaseUs = extractor.sampleTime.coerceAtLeast(0L)

            // If the user trimmed from a non-zero start time and the nearest previous keyframe is > 1.5s earlier,
            // use Media3 Transformer's exact clipping so the output video starts right at startMs without extra pre-roll frames.
            if (startUs > 0L && (startUs - syncVideoBaseUs) > 1_500_000L) {
                DebugLogManager.i(
                    TAG,
                    "Keyframe gap is ${(startUs - syncVideoBaseUs) / 1000}ms (>1500ms); using Transformer exact clip"
                )
                try {
                    extractor.release()
                } catch (_: Exception) {}
                val exactOk = transcodeToStandardMp4Sync(
                    context = context,
                    inputUri = Uri.fromFile(tempLocalFile),
                    outputFile = outputFile,
                    startMs = startMs,
                    endMs = endMs,
                    includeAudio = includeAudio
                )
                if (exactOk && outputFile.exists() && outputFile.length() > 512L) {
                    return true
                }
                // Re-open extractor if Transformer exact clip failed
                extractor.setDataSource(tempLocalFile.absolutePath)
                extractor.selectTrack(videoTrackIdx)
                if (startUs > 0L) {
                    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                }
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackIndices = HashMap<Int, Int>()
            var audioTrackAdded = false

            var rotationDegrees = 0
            val rotRetriever = MediaMetadataRetriever()
            try {
                rotRetriever.setDataSource(tempLocalFile.absolutePath)
                val rotStr = rotRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                rotationDegrees = rotStr?.toIntOrNull() ?: 0
            } catch (_: Exception) {
            } finally {
                try {
                    rotRetriever.release()
                } catch (_: Exception) {}
            }

            val vFormat = extractor.getTrackFormat(videoTrackIdx)
            if (rotationDegrees == 0 && vFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                rotationDegrees = vFormat.getInteger(MediaFormat.KEY_ROTATION)
            }
            val dstVideoIdx = muxer.addTrack(vFormat)
            trackIndices[videoTrackIdx] = dstVideoIdx

            if (rotationDegrees != 0) {
                muxer.setOrientationHint(rotationDegrees)
            }

            if (includeAudio && audioTrackIdx != -1) {
                val aFormat = extractor.getTrackFormat(audioTrackIdx)
                try {
                    val dstAudioIdx = muxer.addTrack(aFormat)
                    trackIndices[audioTrackIdx] = dstAudioIdx
                    extractor.selectTrack(audioTrackIdx)
                    audioTrackAdded = true
                } catch (e: Exception) {
                    DebugLogManager.w(TAG, "Muxer rejected audio track (${e.message}); transcoding audio to AAC")
                    try { muxer.release() } catch (_: Exception) {}
                    muxer = null
                    val transcoded = transcodeToStandardMp4Sync(
                        context = context,
                        inputUri = Uri.fromFile(tempLocalFile),
                        outputFile = outputFile,
                        startMs = startMs,
                        endMs = endMs,
                        includeAudio = includeAudio
                    )
                    if (transcoded && outputFile.exists() && outputFile.length() > 512L) {
                        return true
                    }
                    tempLocalFile.copyTo(outputFile, overwrite = true)
                    return outputFile.exists() && outputFile.length() > 0L
                }
            }

            // Re-seek with both video and audio selected to the exact video keyframe timestamp (`syncVideoBaseUs`)
            if (syncVideoBaseUs > 0L) {
                extractor.seekTo(syncVideoBaseUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            } else {
                extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            muxer.start()

            val maxBufferSize = 4 * 1024 * 1024
            val buffer = ByteBuffer.allocateDirect(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            var videoFinished = false
            var audioFinished = !audioTrackAdded
            var writtenVideoSamples = 0
            var writtenAudioSamples = 0

            // Base timestamp is strictly the first sync video frame's presentation time (`syncVideoBaseUs`).
            // Do NOT force monotonic PTS on video frames! H.264/H.265 B-frames naturally have out-of-order PTS in
            // decode order, and MediaMuxer uses their original relative offsets to build the `ctts` box.
            val baseUs = syncVideoBaseUs
            val clipDurationUs = if (endUs < Long.MAX_VALUE) (endUs - baseUs).coerceAtLeast(100_000L) else Long.MAX_VALUE

            while (true) {
                val sampleTrackIndex = extractor.sampleTrackIndex
                if (sampleTrackIndex == -1) break

                val dstTrackIndex = trackIndices[sampleTrackIndex]
                if (dstTrackIndex == null) {
                    extractor.advance()
                    continue
                }

                val sampleTime = extractor.sampleTime
                if (sampleTime < 0L) {
                    extractor.advance()
                    continue
                }

                if (sampleTrackIndex == videoTrackIdx) {
                    if (videoFinished) {
                        if (audioFinished) break
                        extractor.advance()
                        continue
                    }
                    if (sampleTime < baseUs) {
                        extractor.advance()
                        continue
                    }
                    val relPtsUs = sampleTime - baseUs
                    if (relPtsUs > clipDurationUs) {
                        videoFinished = true
                        if (audioFinished) break
                        extractor.advance()
                        continue
                    }

                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        videoFinished = true
                        if (audioFinished) break
                        extractor.advance()
                        continue
                    }

                    val sampleFlags = extractor.sampleFlags
                    var codecFlags = 0
                    if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                    }
                    if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                        codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                    }
                    bufferInfo.set(0, size, relPtsUs, codecFlags)
                    muxer.writeSampleData(dstTrackIndex, buffer, bufferInfo)
                    writtenVideoSamples++
                } else if (sampleTrackIndex == audioTrackIdx && audioTrackAdded) {
                    if (audioFinished) {
                        if (videoFinished) break
                        extractor.advance()
                        continue
                    }
                    if (sampleTime < baseUs) {
                        extractor.advance()
                        continue
                    }
                    val relPtsUs = sampleTime - baseUs
                    if (relPtsUs > clipDurationUs + 50_000L) {
                        audioFinished = true
                        if (videoFinished) break
                        extractor.advance()
                        continue
                    }

                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        audioFinished = true
                        if (videoFinished) break
                        extractor.advance()
                        continue
                    }

                    bufferInfo.set(0, size, relPtsUs, 0)
                    muxer.writeSampleData(dstTrackIndex, buffer, bufferInfo)
                    writtenAudioSamples++
                }

                if (videoFinished && audioFinished) break
                extractor.advance()
            }

            DebugLogManager.i(TAG, "Trim completed: writtenVideoSamples=$writtenVideoSamples, writtenAudioSamples=$writtenAudioSamples")

            if (writtenVideoSamples == 0) {
                throw IllegalStateException("Zero video samples written")
            }

            muxer.stop()
            muxer.release()
            muxer = null

            if (includeAudio && audioTrackAdded && writtenAudioSamples == 0) {
                DebugLogManager.w(TAG, "Audio track had 0 samples in trim window, copying full stream to preserve audio")
                tempLocalFile.copyTo(outputFile, overwrite = true)
            }

            true
        } catch (e: Exception) {
            DebugLogManager.e(TAG, "Trim error, falling back to full stream copy: ${e.message}", e)
            try { muxer?.release() } catch (_: Exception) {}
            muxer = null
            return try {
                if (tempLocalFile.exists() && tempLocalFile.length() > 0L) {
                    tempLocalFile.copyTo(outputFile, overwrite = true)
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {}
            try {
                muxer?.release()
            } catch (_: Exception) {}
            tempLocalFile.delete()
        }
    }

    fun transcodeToStandardMp4Sync(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        startMs: Long = 0L,
        endMs: Long = 0L,
        includeAudio: Boolean = true
    ): Boolean {
        var tempQtFile: File? = null
        val effectiveUri: Uri = try {
            if (inputUri.scheme == "file" && inputUri.path != null) {
                val srcFile = File(inputUri.path!!)
                if (srcFile.exists() && srcFile.length() in 64L..(60L * 1024 * 1024)) {
                    val bytes = srcFile.readBytes()
                    val restored = ZlcMp4Util.restoreQuickTimeFtypIfNeeded(bytes)
                    if (restored !== bytes) {
                        tempQtFile = File.createTempFile("transcode_qt_", ".mov", context.cacheDir)
                        tempQtFile!!.writeBytes(restored)
                        Uri.fromFile(tempQtFile!!)
                    } else {
                        inputUri
                    }
                } else {
                    inputUri
                }
            } else {
                inputUri
            }
        } catch (_: Exception) {
            inputUri
        }

        val ok = runSingleTransformerPass(
            context = context,
            inputUri = effectiveUri,
            outputFile = outputFile,
            startMs = startMs,
            endMs = endMs,
            includeAudio = includeAudio
        )
        if (ok) {
            tempQtFile?.delete()
            return true
        }
        // 若因特殊音轨导致 Muxer error，自动回退为仅转码视频轨，避免彻底失败
        if (includeAudio) {
            DebugLogManager.w(TAG, "Retrying Transformer without audio after failure")
            val retryNoAudio = runSingleTransformerPass(
                context = context,
                inputUri = effectiveUri,
                outputFile = outputFile,
                startMs = startMs,
                endMs = endMs,
                includeAudio = false
            )
            tempQtFile?.delete()
            return retryNoAudio
        }
        tempQtFile?.delete()
        return false
    }

    private fun runSingleTransformerPass(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long,
        includeAudio: Boolean
    ): Boolean {
        return try {
            if (outputFile.exists()) outputFile.delete()
            val latch = java.util.concurrent.CountDownLatch(1)
            val successRef = java.util.concurrent.atomic.AtomicBoolean(false)
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

            mainHandler.post {
                try {
                    val transformerBuilder = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                    if (includeAudio) {
                        transformerBuilder.setAudioMimeType(MimeTypes.AUDIO_AAC)
                    }
                    val transformer = transformerBuilder.build()

                    val mediaItemBuilder = MediaItem.Builder().setUri(inputUri)
                    if (startMs > 0L || endMs > 0L) {
                        val clipBuilder = MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(startMs.coerceAtLeast(0L))
                        if (endMs > startMs) {
                            clipBuilder.setEndPositionMs(endMs)
                        }
                        mediaItemBuilder.setClippingConfiguration(clipBuilder.build())
                    }

                    val editedItem = EditedMediaItem.Builder(mediaItemBuilder.build())
                        .setRemoveAudio(!includeAudio)
                        .build()

                    transformer.addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            successRef.set(outputFile.exists() && outputFile.length() > 512L)
                            latch.countDown()
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            DebugLogManager.w(TAG, "Transformer fallback error: ${exportException.message}")
                            successRef.set(false)
                            latch.countDown()
                        }
                    })

                    transformer.start(editedItem, outputFile.absolutePath)
                } catch (e: Exception) {
                    DebugLogManager.w(TAG, "Failed starting Transformer fallback: ${e.message}")
                    latch.countDown()
                }
            }

            latch.await(45, java.util.concurrent.TimeUnit.SECONDS)
            successRef.get() && outputFile.exists() && outputFile.length() > 512L
        } catch (e: Exception) {
            DebugLogManager.w(TAG, "runSingleTransformerPass interrupted: ${e.message}")
            false
        }
    }

    suspend fun encodeImagesToVideo(context: Context, imageUris: List<Uri>, outputFile: File, durationMsPerFrame: Long = 300): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .build()

            val videoEffects = listOf(
                Presentation.createForWidthAndHeight(
                    1080, 1920, Presentation.LAYOUT_SCALE_TO_FIT
                )
            )

            val editedMediaItems = imageUris.map { uri ->
                val mediaItem = MediaItem.fromUri(uri)
                EditedMediaItem.Builder(mediaItem)
                    .setDurationUs(durationMsPerFrame * 1000)
                    .setFrameRate(30)
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()
            }

            val sequence = EditedMediaItemSequence(editedMediaItems)
            val composition = Composition.Builder(listOf(sequence)).build()

            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(comp: Composition, exportResult: ExportResult) {
                    DebugLogManager.i(TAG, "Image encode to video completed")
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onError(
                    comp: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    DebugLogManager.e(TAG, "Image encode failed: ${exportException.message}")
                    if (continuation.isActive) continuation.resume(false)
                }
            })

            transformer.start(composition, outputFile.absolutePath)

            continuation.invokeOnCancellation {
                transformer.cancel()
            }
        } catch (e: Exception) {
            DebugLogManager.e(TAG, "Encode images error", e)
            if (continuation.isActive) continuation.resume(false)
        }
    }
}
