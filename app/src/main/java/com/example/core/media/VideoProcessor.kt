package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
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
import com.example.util.DebugLogManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume

object VideoProcessor {
    private const val TAG = "VideoProcessor"

    fun getVideoDuration(context: Context, videoUri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            DebugLogManager.d(TAG, "getVideoDuration: uri=$videoUri, durationMs=$duration, hasAudio=$hasAudio, mime=$mime")
            duration
        } catch (e: Exception) {
            DebugLogManager.e(TAG, "Error getting video duration: ${e.message}", e)
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    fun extractVideoFrame(context: Context, videoUri: Uri, timeMs: Long): Bitmap? {
        val cached = VideoThumbnailCache.getFromMemory(videoUri, timeMs)
        if (cached != null && !cached.isRecycled) {
            return cached
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val timeUs = timeMs * 1000L
            val bmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            if (bmp != null) {
                VideoThumbnailCache.putToMemory(videoUri, timeMs, bmp)
            }
            bmp
        } catch (e: Exception) {
            DebugLogManager.e(TAG, "Error extracting frame at $timeMs ms", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    fun trimVideo(context: Context, inputUri: Uri, outputFile: File, startMs: Long, endMs: Long): Boolean {
        DebugLogManager.i(TAG, "Starting trimVideo: startMs=$startMs, endMs=$endMs, out=${outputFile.name}")

        val tempLocalFile = File(context.cacheDir, "temp_src_${System.currentTimeMillis()}.mp4")
        try {
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                tempLocalFile.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            DebugLogManager.w(TAG, "Failed copying inputUri to tempLocalFile: ${e.message}")
        }

        val sourceFile = if (tempLocalFile.exists() && tempLocalFile.length() > 0) {
            tempLocalFile
        } else if (inputUri.scheme == "file" && inputUri.path != null) {
            File(inputUri.path!!)
        } else {
            null
        }

        val totalDuration = if (sourceFile != null && sourceFile.exists()) {
            getVideoDuration(context, Uri.fromFile(sourceFile))
        } else {
            getVideoDuration(context, inputUri)
        }

        if (sourceFile != null && sourceFile.exists() && startMs <= 0L && (totalDuration <= 0L || endMs >= totalDuration - 80L)) {
            try {
                sourceFile.copyTo(outputFile, overwrite = true)
                if (outputFile.exists() && outputFile.length() > 0L) {
                    DebugLogManager.i(TAG, "Full clip selected: Direct stream copy succeeded, size=${outputFile.length()} bytes")
                    tempLocalFile.delete()
                    return true
                }
            } catch (e: Exception) {
                DebugLogManager.w(TAG, "Direct copy failed, falling back to MediaExtractor: ${e.message}")
            }
        }

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            if (sourceFile != null && sourceFile.exists()) {
                extractor.setDataSource(sourceFile.absolutePath)
            } else {
                extractor.setDataSource(context, inputUri, null)
            }

            val trackCount = extractor.trackCount
            DebugLogManager.i(TAG, "MediaExtractor found $trackCount tracks")

            var videoTrackIdx = -1
            var audioTrackIdx = -1

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                DebugLogManager.d(TAG, "Source track $i: mime=$mime")
                if (mime.startsWith("video/") && videoTrackIdx == -1) {
                    videoTrackIdx = i
                } else if (mime.startsWith("audio/") && audioTrackIdx == -1) {
                    audioTrackIdx = i
                }
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackIndices = HashMap<Int, Int>()
            var audioTrackAdded = false

            var rotationDegrees = 0
            val rotRetriever = MediaMetadataRetriever()
            try {
                if (sourceFile != null && sourceFile.exists()) {
                    rotRetriever.setDataSource(sourceFile.absolutePath)
                } else {
                    rotRetriever.setDataSource(context, inputUri)
                }
                val rotStr = rotRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                rotationDegrees = rotStr?.toIntOrNull() ?: 0
            } catch (e: Exception) {
                DebugLogManager.w(TAG, "Failed reading video rotation: ${e.message}")
            } finally {
                try {
                    rotRetriever.release()
                } catch (_: Exception) {}
            }

            if (videoTrackIdx != -1) {
                extractor.selectTrack(videoTrackIdx)
                val vFormat = extractor.getTrackFormat(videoTrackIdx)
                if (rotationDegrees == 0 && vFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                    rotationDegrees = vFormat.getInteger(MediaFormat.KEY_ROTATION)
                }
                val dstVideoIdx = muxer.addTrack(vFormat)
                trackIndices[videoTrackIdx] = dstVideoIdx
                DebugLogManager.i(TAG, "Selected video track: idx=$videoTrackIdx, mime=${vFormat.getString(MediaFormat.KEY_MIME)}")
            }

            if (rotationDegrees != 0) {
                muxer.setOrientationHint(rotationDegrees)
                DebugLogManager.i(TAG, "Applied video orientation hint: $rotationDegrees degrees")
            }

            if (audioTrackIdx != -1) {
                extractor.selectTrack(audioTrackIdx)
                val aFormat = extractor.getTrackFormat(audioTrackIdx)
                try {
                    val dstAudioIdx = muxer.addTrack(aFormat)
                    trackIndices[audioTrackIdx] = dstAudioIdx
                    audioTrackAdded = true
                    DebugLogManager.i(TAG, "Selected audio track: idx=$audioTrackIdx, mime=${aFormat.getString(MediaFormat.KEY_MIME)}")
                } catch (e: Exception) {
                    DebugLogManager.w(TAG, "MediaMuxer failed to add audio track: ${e.message}")
                }
            }

            if (audioTrackIdx != -1 && !audioTrackAdded && sourceFile != null && sourceFile.exists()) {
                DebugLogManager.w(TAG, "Audio track exists but incompatible with MediaMuxer. Falling back to direct copy to preserve audio.")
                try {
                    muxer.release()
                } catch (_: Exception) {}
                muxer = null
                sourceFile.copyTo(outputFile, overwrite = true)
                tempLocalFile.delete()
                return true
            }

            muxer.start()

            val startUs = (startMs * 1000L).coerceAtLeast(0L)
            val endUs = if (endMs > 0L) endMs * 1000L else Long.MAX_VALUE
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val maxBufferSize = 2 * 1024 * 1024
            val buffer = ByteBuffer.allocateDirect(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            var globalBaseUs = -1L
            var videoBaseUs = -1L
            var audioBaseUs = -1L
            var videoFinished = (videoTrackIdx == -1)
            var audioFinished = (audioTrackIdx == -1 || !audioTrackAdded)

            var writtenVideoSamples = 0
            var writtenAudioSamples = 0

            while (true) {
                val sampleTrackIndex = extractor.sampleTrackIndex
                if (sampleTrackIndex == -1) break

                val dstTrackIndex = trackIndices[sampleTrackIndex]
                if (dstTrackIndex == null) {
                    extractor.advance()
                    continue
                }

                val sampleTime = extractor.sampleTime

                if (sampleTrackIndex == videoTrackIdx) {
                    if (sampleTime > endUs) {
                        videoFinished = true
                        if (audioFinished) break
                        extractor.advance()
                        continue
                    }
                    if (videoBaseUs == -1L) {
                        videoBaseUs = sampleTime
                    }
                    if (globalBaseUs == -1L) {
                        globalBaseUs = sampleTime
                    }
                    val pts = (sampleTime - globalBaseUs).coerceAtLeast(0L)

                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) {
                        videoFinished = true
                        if (audioFinished) break
                        extractor.advance()
                        continue
                    }
                    bufferInfo.presentationTimeUs = pts
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(dstTrackIndex, buffer, bufferInfo)
                    writtenVideoSamples++
                } else if (sampleTrackIndex == audioTrackIdx) {
                    if (sampleTime > endUs + 100_000L) {
                        audioFinished = true
                        if (videoFinished) break
                        extractor.advance()
                        continue
                    }
                    if (globalBaseUs != -1L && sampleTime < globalBaseUs - 40_000L) {
                        extractor.advance()
                        continue
                    }
                    if (globalBaseUs == -1L) {
                        globalBaseUs = sampleTime
                    }
                    val pts = (sampleTime - globalBaseUs).coerceAtLeast(0L)

                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) {
                        audioFinished = true
                        if (videoFinished) break
                        extractor.advance()
                        continue
                    }
                    bufferInfo.presentationTimeUs = pts
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(dstTrackIndex, buffer, bufferInfo)
                    writtenAudioSamples++
                }

                if (videoFinished && audioFinished) break
                extractor.advance()
            }

            DebugLogManager.i(TAG, "trimVideo success: videoSamples=$writtenVideoSamples, audioSamples=$writtenAudioSamples")

            try {
                muxer.stop()
                muxer.release()
            } catch (_: Exception) {}
            muxer = null

            if (audioTrackIdx != -1 && writtenAudioSamples == 0 && sourceFile != null && sourceFile.exists()) {
                DebugLogManager.w(TAG, "Audio samples count is 0 despite audio track existing in source. Restoring source file to guarantee audio!")
                sourceFile.copyTo(outputFile, overwrite = true)
            }

            tempLocalFile.delete()
            return true
        } catch (e: Exception) {
            DebugLogManager.e(TAG, "Error trimming video, trying direct copy fallback", e)
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
            muxer = null

            return try {
                if (sourceFile != null && sourceFile.exists()) {
                    sourceFile.copyTo(outputFile, overwrite = true)
                } else {
                    context.contentResolver.openInputStream(inputUri)?.use { input ->
                        outputFile.outputStream().buffered().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                DebugLogManager.i(TAG, "Fallback direct copy succeeded")
                tempLocalFile.delete()
                true
            } catch (fallbackEx: Exception) {
                DebugLogManager.e(TAG, "Fallback copy failed", fallbackEx)
                tempLocalFile.delete()
                false
            }
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {}
            try {
                muxer?.release()
            } catch (_: Exception) {}
            if (tempLocalFile.exists()) {
                tempLocalFile.delete()
            }
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
