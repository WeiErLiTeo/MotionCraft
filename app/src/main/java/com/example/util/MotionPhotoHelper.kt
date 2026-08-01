package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer

object MotionPhotoHelper {
    private const val TAG = "MotionPhotoHelper"

    /**
     * Scans a JPEG file to find embedded MP4 segment and streams it to cacheFile without memory allocation limits.
     */
    fun extractVideoFromMotionPhoto(file: File, cacheFile: File): Boolean {
        return try {
            if (!file.exists() || file.length() < 100) return false
            val raf = java.io.RandomAccessFile(file, "r")
            val fileLength = raf.length()
            
            val buffer = ByteArray(64 * 1024)
            var ftypOffset = -1L
            
            var pos = 0L
            while (pos < fileLength) {
                raf.seek(pos)
                val bytesRead = raf.read(buffer)
                if (bytesRead < 4) break
                
                for (i in 0 until bytesRead - 3) {
                    if (buffer[i] == 0x66.toByte() &&
                        buffer[i + 1] == 0x74.toByte() &&
                        buffer[i + 2] == 0x79.toByte() &&
                        buffer[i + 3] == 0x70.toByte()) {
                        ftypOffset = pos + i
                        break
                    }
                }
                if (ftypOffset != -1L) break
                pos += bytesRead - 3
            }
            
            if (ftypOffset == -1L) {
                raf.close()
                return false
            }
            
            val startOffset = (ftypOffset - 4).coerceAtLeast(0L)
            raf.seek(startOffset)
            
            cacheFile.outputStream().buffered().use { out ->
                val copyBuffer = ByteArray(128 * 1024)
                var bytesToRead = fileLength - startOffset
                while (bytesToRead > 0) {
                    val readSize = raf.read(copyBuffer, 0, copyBuffer.size.coerceAtMost(bytesToRead.toInt()))
                    if (readSize <= 0) break
                    out.write(copyBuffer, 0, readSize)
                    bytesToRead -= readSize
                }
            }
            raf.close()
            Log.d(TAG, "Successfully extracted embedded MP4 to ${cacheFile.absolutePath}, size: ${cacheFile.length()} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting video from motion photo file", e)
            false
        }
    }

    fun extractVideoFromMotionPhoto(inputStream: InputStream, cacheFile: File): Boolean {
        return try {
            val bytes = inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return false

            val pattern = byteArrayOf(0x66, 0x74, 0x79, 0x70)
            val ftypIndex = findSubarray(bytes, pattern)
            if (ftypIndex == -1) {
                Log.d(TAG, "No 'ftyp' MP4 signature found in JPEG")
                return false
            }

            val startOffset = (ftypIndex - 4).coerceAtLeast(0)
            cacheFile.outputStream().use { out ->
                out.write(bytes, startOffset, bytes.size - startOffset)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting motion video", e)
            false
        }
    }

    private fun findSubarray(array: ByteArray, pattern: ByteArray): Int {
        if (pattern.size > array.size) return -1
        for (i in 0..array.size - pattern.size) {
            var found = true
            for (j in pattern.indices) {
                if (array[i + j] != pattern[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    /**
     * Determines if a Uri represents a valid Motion Photo (contains embedded video)
     */
    fun isMotionPhoto(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(64 * 1024)
                val pattern = byteArrayOf(0x66, 0x74, 0x79, 0x70)
                var bytesRead: Int
                var totalRead = 0L
                val maxSearch = 8L * 1024 * 1024
                while (inputStream.read(buffer).also { bytesRead = it } != -1 && totalRead < maxSearch) {
                    if (findSubarray(buffer, pattern) != -1) return true
                    totalRead += bytesRead
                }
                false
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Safely locates and extracts XMP XML metadata from image bytes without creating huge strings
     */
    fun extractXmpXml(bytes: ByteArray): String {
        return try {
            val ftypPattern = byteArrayOf(0x66, 0x74, 0x79, 0x70)
            val ftypIdx = findSubarray(bytes, ftypPattern)
            val searchLimit = if (ftypIdx != -1) ftypIdx else bytes.size.coerceAtMost(2 * 1024 * 1024)
            
            val xmpHeader = "<?xpacket begin"
            val headerBytes = xmpHeader.toByteArray(Charsets.UTF_8)
            val headerIdx = findSubarray(bytes.sliceArray(0 until searchLimit), headerBytes)
            if (headerIdx != -1) {
                val xmpFooter = "<?xpacket end"
                val footerBytes = xmpFooter.toByteArray(Charsets.UTF_8)
                val footerIdx = findSubarray(bytes.sliceArray(headerIdx until searchLimit), footerBytes)
                if (footerIdx != -1) {
                    val endIdx = headerIdx + footerIdx + 19
                    String(bytes.sliceArray(headerIdx until endIdx.coerceAtMost(bytes.size)), Charsets.UTF_8)
                } else {
                    String(bytes.sliceArray(headerIdx until (headerIdx + 4096).coerceAtMost(bytes.size)), Charsets.UTF_8)
                }
            } else {
                "未在图片前段找到 XMP 元数据包 (<?xpacket begin...)"
            }
        } catch (e: Exception) {
            "解析 XMP 出错: ${e.message}"
        }
    }

    /**
     * Extracts a frame from video at the specified timestamp in milliseconds
     */
    fun extractVideoFrame(context: Context, videoUri: Uri, timeMs: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val timeUs = timeMs * 1000
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting frame at $timeMs ms", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Gets the duration of a video in milliseconds
     */
    fun getVideoDuration(context: Context, videoUri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video duration", e)
            0L
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Pack/merge cover photo and video into a single Android-compatible Motion Photo.
     * We inject Google MicroVideo XMP metadata into the JPEG, and stream the MP4 video bytes safely without memory allocation errors.
     */
    fun packageMotionPhoto(coverFile: File, videoFile: File, outputFile: File): Boolean {
        return try {
            val videoLength = videoFile.length().toInt()
            val coverBytes = coverFile.readBytes()
            val jpegWithXmp = injectXMPIntoJPEG(coverBytes, videoLength)
            
            outputFile.outputStream().buffered().use { out ->
                out.write(jpegWithXmp)
                videoFile.inputStream().buffered().use { videoIn ->
                    videoIn.copyTo(out)
                }
            }
            Log.d(TAG, "Packaged motion photo into ${outputFile.absolutePath}, total size: ${outputFile.length()} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error packaging motion photo from files", e)
            false
        }
    }

    fun packageMotionPhoto(coverBytes: ByteArray, videoBytes: ByteArray, outputFile: File): Boolean {
        return try {
            val jpegWithXmp = injectXMPIntoJPEG(coverBytes, videoBytes.size)
            outputFile.outputStream().buffered().use { out ->
                out.write(jpegWithXmp)
                out.write(videoBytes)
            }
            Log.d(TAG, "Packaged motion photo into ${outputFile.absolutePath}, total size: ${outputFile.length()} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error packaging motion photo from bytes", e)
            false
        }
    }

    private fun injectXMPIntoJPEG(jpegBytes: ByteArray, videoLength: Int): ByteArray {
        val xmpContent = """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
        GCamera:MotionPhoto="1"
        GCamera:MotionPhotoVersion="1"
        GCamera:MotionPhotoPresentationTimestampUs="0">
      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0" Item:Padding="0"/>
          </rdf:li>
          <rdf:li rdf:parseType="Resource">
            <Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="$videoLength" Item:Padding="0"/>
          </rdf:li>
        </rdf:Seq>
      </Container:Directory>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""

        val namespace = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.UTF_8)
        val xmpBytes = xmpContent.toByteArray(Charsets.UTF_8)
        
        val payloadSize = namespace.size + xmpBytes.size
        val markerSize = payloadSize + 2
        
        val out = java.io.ByteArrayOutputStream(jpegBytes.size + markerSize + 2)
        out.write(0xFF)
        out.write(0xD8)
        out.write(0xFF)
        out.write(0xE1)
        out.write((markerSize shr 8) and 0xFF)
        out.write(markerSize and 0xFF)
        out.write(namespace)
        out.write(xmpBytes)
        
        if (jpegBytes.size > 2 && jpegBytes[0] == 0xFF.toByte() && jpegBytes[1] == 0xD8.toByte()) {
            out.write(jpegBytes, 2, jpegBytes.size - 2)
        } else {
            out.write(jpegBytes)
        }
        return out.toByteArray()
    }

    fun trimVideo(context: Context, inputUri: Uri, outputFile: File, startMs: Long, endMs: Long): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            val pfd = context.contentResolver.openFileDescriptor(inputUri, "r") ?: return false
            extractor.setDataSource(pfd.fileDescriptor)

            val trackCount = extractor.trackCount
            val trackIndices = HashMap<Int, Int>()
            var videoTrackIdx = -1

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val dstIndex = muxer.addTrack(format)
                    trackIndices[i] = dstIndex
                    if (mime.startsWith("video/")) {
                        videoTrackIdx = i
                    }
                }
            }

            muxer.start()

            val startUs = startMs * 1000
            val endUs = endMs * 1000
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val bufferSize = 2 * 1024 * 1024 // 2 MB
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            var firstVideoKeyFrameSeen = false
            var baseUs = -1L

            while (true) {
                val sampleTrackIndex = extractor.sampleTrackIndex
                if (sampleTrackIndex == -1) break

                val dstTrackIndex = trackIndices[sampleTrackIndex]
                if (dstTrackIndex == null) {
                    extractor.advance()
                    continue
                }

                val sampleTime = extractor.sampleTime
                if (sampleTime > endUs) break

                val flags = extractor.sampleFlags

                if (sampleTrackIndex == videoTrackIdx && !firstVideoKeyFrameSeen) {
                    if ((flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 || (flags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        firstVideoKeyFrameSeen = true
                        baseUs = sampleTime
                    } else {
                        extractor.advance()
                        continue
                    }
                }

                if (baseUs == -1L) {
                    baseUs = sampleTime
                }

                val presentationTimeUs = (sampleTime - baseUs).coerceAtLeast(0L)

                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                bufferInfo.presentationTimeUs = presentationTimeUs
                bufferInfo.flags = flags

                muxer.writeSampleData(dstTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            pfd.close()
            Log.d(TAG, "Trimming video succeeded! Start: $startMs ms, End: $endMs ms")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error trimming video", e)
            return try {
                context.contentResolver.openInputStream(inputUri)?.use { input ->
                    outputFile.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Trim failed but fallback copied full file")
                true
            } catch (e2: Exception) {
                false
            }
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {}
        }
    }

    fun diagnoseVideoFormat(context: Context, videoUri: Uri): String {
        val extractor = MediaExtractor()
        return try {
            if (videoUri.scheme == "file") {
                extractor.setDataSource(videoUri.path!!)
            } else {
                val pfd = context.contentResolver.openFileDescriptor(videoUri, "r")
                if (pfd != null) {
                    extractor.setDataSource(pfd.fileDescriptor)
                    pfd.close()
                } else {
                    return "无法打开视频文件"
                }
            }
            
            var formatDesc = "未找到视频轨道"
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    val width = format.getInteger(MediaFormat.KEY_WIDTH)
                    val height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    var frameRateStr = "未知"
                    try {
                        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            frameRateStr = format.getInteger(MediaFormat.KEY_FRAME_RATE).toString()
                        }
                    } catch (e: Exception) {
                        try {
                            frameRateStr = format.getFloat(MediaFormat.KEY_FRAME_RATE).toString()
                        } catch (e2: Exception) {}
                    }
                    formatDesc = "编码格式: $mime\n分辨率: ${width}x${height}\n帧率: $frameRateStr fps"
                    if (mime != MediaFormat.MIMETYPE_VIDEO_AVC && mime != MediaFormat.MIMETYPE_VIDEO_HEVC) {
                        formatDesc += "\n⚠️ 警告: 抖音实况通常要求 H.264 (AVC) 或 H.265 (HEVC) 编码"
                    } else {
                        formatDesc += "\n✅ 编码格式兼容抖音"
                    }
                    break
                }
            }
            formatDesc
        } catch (e: Exception) {
            "分析失败: ${e.message}"
        } finally {
            extractor.release()
        }
    }


    suspend fun transcodeVideoToAVC(context: Context, inputUri: Uri, outputFile: File): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .build()

            val mediaItem = MediaItem.fromUri(inputUri)
            val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()

            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    Log.e(TAG, "Transcode failed: ${exportException.message}")
                    if (continuation.isActive) continuation.resume(false)
                }
            })

            transformer.start(editedMediaItem, outputFile.absolutePath)

            continuation.invokeOnCancellation {
                transformer.cancel()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcode error", e)
            if (continuation.isActive) continuation.resume(false)
        }
    }

    suspend fun encodeImagesToVideo(context: Context, imageUris: List<Uri>, outputFile: File, durationMsPerFrame: Long = 300): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        try {
            val transformer = androidx.media3.transformer.Transformer.Builder(context)
                .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                .build()
                
            val videoEffects = listOf(
                androidx.media3.effect.Presentation.createForWidthAndHeight(
                    1080, 1920, androidx.media3.effect.Presentation.LAYOUT_SCALE_TO_FIT
                )
            )
            
            val editedMediaItems = imageUris.map { uri ->
                val mediaItem = androidx.media3.common.MediaItem.fromUri(uri)
                androidx.media3.transformer.EditedMediaItem.Builder(mediaItem)
                    .setDurationUs(durationMsPerFrame * 1000)
                    .setFrameRate(30)
                    .setEffects(androidx.media3.transformer.Effects(emptyList(), videoEffects))
                    .build()
            }
            
            val sequence = androidx.media3.transformer.EditedMediaItemSequence(editedMediaItems)
            val composition = androidx.media3.transformer.Composition.Builder(listOf(sequence)).build()

            transformer.addListener(object : androidx.media3.transformer.Transformer.Listener {
                override fun onCompleted(comp: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) {
                    if (continuation.isActive) continuation.resume(true)
                }
                override fun onError(
                    comp: androidx.media3.transformer.Composition,
                    exportResult: androidx.media3.transformer.ExportResult,
                    exportException: androidx.media3.transformer.ExportException
                ) {
                    Log.e(TAG, "Image encode failed: ${exportException.message}")
                    if (continuation.isActive) continuation.resume(false)
                }
            })
            transformer.start(composition, outputFile.absolutePath)
            
            continuation.invokeOnCancellation {
                transformer.cancel()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Encode images error", e)
            if (continuation.isActive) continuation.resume(false)
        }
    }
}
