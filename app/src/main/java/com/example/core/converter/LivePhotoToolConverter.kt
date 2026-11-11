package com.example.core.converter

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.core.media.ImageProcessor
import com.example.core.media.VideoProcessor
import com.example.data.LivePhotoRecord
import com.example.util.DebugLogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object LivePhotoToolConverter {
    private const val TAG = "LivePhotoToolConverter"

    data class ExtractionInfo(
        val hasMotionPhoto: Boolean = false,
        val videoOffset: Long = -1L,
        val videoLength: Long = -1L,
        val coverSize: Long = 0L,
        val brandDetected: String = "未知",
        val videoFile: File? = null,
        val videoDurationMs: Long = 0L,
        val videoWidth: Int = 0,
        val videoHeight: Int = 0,
        val hasAudio: Boolean = false,
        val fileSize: Long = 0L,
        val exifSummary: String = ""
    )

    suspend fun inspectLivePhoto(context: Context, record: LivePhotoRecord): ExtractionInfo = withContext(Dispatchers.IO) {
        try {
            val coverFile = File(record.coverPath)
            val directVideoFile = File(record.videoPath)
            val cacheDir = File(context.cacheDir, "extracted_inspect").apply { mkdirs() }
            val rawResolvedFile = if (directVideoFile.exists() && directVideoFile.length() > 0) {
                directVideoFile
            } else if (coverFile.exists()) {
                val tempVideo = File(cacheDir, "${record.id}_temp.mp4")
                ImageProcessor.extractVideoFromMotionPhoto(coverFile, tempVideo)
                if (tempVideo.exists() && tempVideo.length() > 0) tempVideo else null
            } else null

            val resolvedVideoFile = if (rawResolvedFile != null && rawResolvedFile.exists()) {
                if (VideoProcessor.isPlayableMp4(rawResolvedFile)) {
                    rawResolvedFile
                } else {
                    val healedFile = File(cacheDir, "${record.id}_${record.timestamp}_healed.mp4")
                    if (VideoProcessor.healToStandardMp4(context, rawResolvedFile, healedFile) && healedFile.exists()) {
                        if (directVideoFile.exists() && record.id >= 0) {
                            runCatching { healedFile.copyTo(directVideoFile, overwrite = true) }
                            directVideoFile
                        } else {
                            healedFile
                        }
                    } else {
                        rawResolvedFile
                    }
                }
            } else null

            var duration = 0L
            var width = 0
            var height = 0
            var hasAudio = false
            var brand = "标准格式"

            if (resolvedVideoFile != null && resolvedVideoFile.exists()) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(resolvedVideoFile.absolutePath)
                    duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null
                } catch (_: Exception) {} finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }

            if (coverFile.exists()) {
                val headerBytes = coverFile.inputStream().use { input ->
                    val buf = ByteArray(8192)
                    val read = input.read(buf)
                    if (read > 0) String(buf, 0, read, Charsets.ISO_8859_1) else ""
                }
                val hasSamsungTail = try {
                    if (coverFile.length() >= 32) {
                        val raf = java.io.RandomAccessFile(coverFile, "r")
                        val tail = ByteArray(8)
                        raf.seek(coverFile.length() - 8)
                        raf.readFully(tail)
                        raf.close()
                        String(tail, 4, 4, Charsets.US_ASCII) == "SEFT"
                    } else false
                } catch (_: Exception) {
                    false
                }
                brand = when {
                    hasSamsungTail || headerBytes.contains("MotionPhoto_Data") -> "三星 (Samsung One UI SEF)"
                    headerBytes.contains("OpCamera") || headerBytes.contains("oplustag") -> "OPPO / OnePlus"
                    headerBytes.contains("GCamera:MicroVideo") -> "小米 (Xiaomi)"
                    headerBytes.contains("VCamera") -> "vivo"
                    headerBytes.contains("GCamera") -> "Google / 通用标准"
                    else -> "嵌入式实况"
                }
            }

            val totalSize = (if (coverFile.exists()) coverFile.length() else 0L) + (resolvedVideoFile?.length() ?: 0L)

            ExtractionInfo(
                hasMotionPhoto = resolvedVideoFile != null && resolvedVideoFile.exists(),
                videoOffset = if (coverFile.exists()) coverFile.length() else 0L,
                videoLength = resolvedVideoFile?.length() ?: 0L,
                coverSize = if (coverFile.exists()) coverFile.length() else 0L,
                brandDetected = brand,
                videoFile = resolvedVideoFile,
                videoDurationMs = duration,
                videoWidth = width,
                videoHeight = height,
                hasAudio = hasAudio,
                fileSize = totalSize,
                exifSummary = "品牌协议: $brand | 视频轨道: ${if (hasAudio) "音视频复合" else "仅视频"}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Inspect error", e)
            ExtractionInfo()
        }
    }

    suspend fun inspectLivePhoto(jpegFile: File): ExtractionInfo = withContext(Dispatchers.IO) {
        try {
            val totalBytes = jpegFile.readBytes()
            val totalLen = totalBytes.size.toLong()
            var mp4Offset = -1L
            for (i in 0 until totalBytes.size - 8) {
                if (totalBytes[i + 4] == 'f'.code.toByte() &&
                    totalBytes[i + 5] == 't'.code.toByte() &&
                    totalBytes[i + 6] == 'y'.code.toByte() &&
                    totalBytes[i + 7] == 'p'.code.toByte()
                ) {
                    mp4Offset = i.toLong()
                    break
                }
            }
            if (mp4Offset != -1L && mp4Offset < totalLen) {
                val vidLen = totalLen - mp4Offset
                ExtractionInfo(
                    hasMotionPhoto = true,
                    videoOffset = mp4Offset,
                    videoLength = vidLen,
                    coverSize = mp4Offset,
                    fileSize = totalLen
                )
            } else {
                ExtractionInfo(hasMotionPhoto = false, coverSize = totalLen, fileSize = totalLen)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Inspect file error", e)
            ExtractionInfo()
        }
    }

    suspend fun extractAndSaveImage(
        context: Context,
        record: LivePhotoRecord,
        timeMs: Long = 0L,
        format: String = "JPEG"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val directVideoFile = File(record.videoPath)
            val coverFile = File(record.coverPath)
            val bitmapToSave = if (timeMs > 0 && directVideoFile.exists() && directVideoFile.length() > 0) {
                VideoProcessor.extractVideoFrame(context, Uri.fromFile(directVideoFile), timeMs)
            } else if (timeMs > 0 && coverFile.exists()) {
                val tempVid = File(context.cacheDir, "temp_frame_vid_${record.id}.mp4")
                val extracted = ImageProcessor.extractVideoFromMotionPhoto(coverFile, tempVid)
                val bmp = if (extracted && tempVid.exists()) {
                    VideoProcessor.extractVideoFrame(context, Uri.fromFile(tempVid), timeMs)
                } else null
                tempVid.delete()
                bmp ?: BitmapFactory.decodeFile(coverFile.absolutePath)
            } else if (coverFile.exists()) {
                BitmapFactory.decodeFile(coverFile.absolutePath)
            } else null

            if (bitmapToSave == null) return@withContext false

            val upperFmt = format.uppercase()
            val ext = when {
                upperFmt.contains("PNG") -> "png"
                upperFmt.contains("WEBP") -> "webp"
                else -> "jpg"
            }
            val mime = when (ext) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            val fileName = "Extracted_${System.currentTimeMillis()}.$ext"
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MotionCraft_Extract")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return@withContext false
            resolver.openOutputStream(uri)?.use { out ->
                when (ext) {
                    "png" -> bitmapToSave.compress(Bitmap.CompressFormat.PNG, 100, out)
                    "webp" -> {
                        @Suppress("DEPRECATION")
                        val webpFmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSLESS
                        } else {
                            Bitmap.CompressFormat.WEBP
                        }
                        bitmapToSave.compress(webpFmt, 95, out)
                    }
                    else -> bitmapToSave.compress(Bitmap.CompressFormat.JPEG, 96, out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Extract image error", e)
            false
        }
    }

    suspend fun extractAndSaveVideo(
        context: Context,
        record: LivePhotoRecord,
        keepAudio: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val videoFile = File(record.videoPath)
            val tempExtracted = File(context.cacheDir, "temp_extract_${System.currentTimeMillis()}.mp4")
            val tempProcessed = File(context.cacheDir, "temp_proc_${System.currentTimeMillis()}.mp4")

            try {
                val baseVideo = if (videoFile.exists() && videoFile.length() > 0) {
                    videoFile
                } else {
                    val coverFile = File(record.coverPath)
                    val ok = ImageProcessor.extractVideoFromMotionPhoto(coverFile, tempExtracted)
                    if (ok && tempExtracted.exists() && tempExtracted.length() > 0) tempExtracted else null
                } ?: return@withContext false

                val finalSource = if (!keepAudio) {
                    val stripped = VideoProcessor.trimVideo(
                        context = context,
                        inputUri = Uri.fromFile(baseVideo),
                        outputFile = tempProcessed,
                        startMs = 0L,
                        endMs = 0L,
                        includeAudio = false
                    )
                    if (stripped && tempProcessed.exists() && tempProcessed.length() > 0) tempProcessed else baseVideo
                } else {
                    baseVideo
                }

                val resolver = context.contentResolver
                val fileName = "Extracted_${System.currentTimeMillis()}.mp4"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/MotionCraft_Extract")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return@withContext false
                resolver.openOutputStream(uri)?.buffered(64 * 1024)?.use { out ->
                    finalSource.inputStream().buffered(64 * 1024).use { input ->
                        input.copyTo(out, 64 * 1024)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                true
            } finally {
                tempExtracted.delete()
                tempProcessed.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extract video error", e)
            false
        }
    }
}
