package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.collection.LruCache
import com.example.util.DebugLogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoThumbnailCache {
    private const val TAG = "VideoThumbnailCache"

    // 48 MB memory cache (in KB units) so 4K/high-bitrate scaled cover frames and timeline thumbnails never thrash
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(48 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    private fun getCacheKey(uri: Uri, timeMs: Long, targetWidth: Int, targetHeight: Int): String {
        val roundedMs = ((timeMs + 25L) / 50L) * 50L
        val normW = if (targetWidth > 0) targetWidth else 960
        val normH = if (targetHeight > 0) targetHeight else 960
        return "${uri}_${roundedMs}_${normW}x${normH}"
    }

    fun getFromMemory(uri: Uri, timeMs: Long, targetWidth: Int = 960, targetHeight: Int = 960): Bitmap? {
        val key = getCacheKey(uri, timeMs, targetWidth, targetHeight)
        return memoryCache.get(key)
    }

    fun putToMemory(uri: Uri, timeMs: Long, bitmap: Bitmap, targetWidth: Int = 960, targetHeight: Int = 960) {
        if (bitmap.isRecycled) return
        val key = getCacheKey(uri, timeMs, targetWidth, targetHeight)
        memoryCache.put(key, bitmap)
    }

    /**
     * Checks whether an extracted frame is a solid black / blank fade-in frame (common at 0ms in
     * 4K or Apple QuickTime videos with EditList offsets). Samples a 6x6 grid across the bitmap.
     */
    private fun isMostlyBlackFrame(bitmap: Bitmap): Boolean {
        if (bitmap.isRecycled || bitmap.width < 8 || bitmap.height < 8) return true
        return try {
            var totalLuma = 0L
            var sampleCount = 0
            val stepX = (bitmap.width / 7).coerceAtLeast(1)
            val stepY = (bitmap.height / 7).coerceAtLeast(1)
            for (ix in 1..6) {
                val x = (ix * stepX).coerceIn(0, bitmap.width - 1)
                for (iy in 1..6) {
                    val y = (iy * stepY).coerceIn(0, bitmap.height - 1)
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    totalLuma += (r * 299 + g * 587 + b * 114) / 1000
                    sampleCount++
                }
            }
            val avgLuma = if (sampleCount > 0) totalLuma / sampleCount else 0L
            avgLuma < 5L
        } catch (_: Exception) {
            false
        }
    }

    private fun decodeFrameFromRetriever(
        retriever: MediaMetadataRetriever,
        timeUs: Long,
        option: Int,
        reqW: Int,
        reqH: Int
    ): Bitmap? {
        return try {
            if (reqW > 0 && reqH > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(timeUs, option, reqW, reqH)
            } else {
                retriever.getFrameAtTime(timeUs, option)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun extractFrameBlocking(
        context: Context,
        uri: Uri,
        timeMs: Long,
        targetWidth: Int = 960,
        targetHeight: Int = 960
    ): Bitmap? {
        val effW = if (targetWidth > 0) targetWidth else 960
        val effH = if (targetHeight > 0) targetHeight else 960

        val cached = getFromMemory(uri, timeMs, effW, effH)
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file" && uri.path != null) {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }

            val timeUs = timeMs.coerceAtLeast(0L) * 1000L
            var frame: Bitmap? = if (timeMs <= 50L) {
                // At 0ms on large 4K videos, OPTION_CLOSEST_SYNC decodes the first hardware I-frame in ~15ms
                // instead of stalling on B-frame reordering or EditList seek.
                decodeFrameFromRetriever(retriever, timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, effW, effH)
                    ?: decodeFrameFromRetriever(retriever, timeUs, MediaMetadataRetriever.OPTION_CLOSEST, effW, effH)
            } else {
                decodeFrameFromRetriever(retriever, timeUs, MediaMetadataRetriever.OPTION_CLOSEST, effW, effH)
                    ?: decodeFrameFromRetriever(retriever, timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, effW, effH)
            }

            // If frame at 0ms is null or solid black (e.g. black fade-in frame or EditList blank frame),
            // probe slightly ahead at 150ms and 350ms so the cover preview is never black or missing.
            if (timeMs <= 80L && (frame == null || isMostlyBlackFrame(frame))) {
                val probeTimesUs = longArrayOf(150_000L, 350_000L, 600_000L)
                for (probeUs in probeTimesUs) {
                    val candidate = decodeFrameFromRetriever(
                        retriever,
                        probeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        effW,
                        effH
                    ) ?: decodeFrameFromRetriever(
                        retriever,
                        probeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        effW,
                        effH
                    )
                    if (candidate != null && !isMostlyBlackFrame(candidate)) {
                        frame = candidate
                        DebugLogManager.d(TAG, "0ms frame was black/null for $uri; recovered non-black cover frame at ${probeUs / 1000}ms")
                        break
                    } else if (frame == null && candidate != null) {
                        frame = candidate
                    }
                }
            }

            if (frame != null && !frame.isRecycled) {
                putToMemory(uri, timeMs, frame, effW, effH)
            }
            frame
        } catch (e: Exception) {
            DebugLogManager.e(TAG, "Failed extracting frame for $uri at $timeMs ms", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    suspend fun getOrExtractFrame(
        context: Context,
        uri: Uri,
        timeMs: Long,
        targetWidth: Int = 960,
        targetHeight: Int = 960
    ): Bitmap? = withContext(Dispatchers.IO) {
        extractFrameBlocking(context, uri, timeMs, targetWidth, targetHeight)
    }

    suspend fun prefetchThumbnails(
        context: Context,
        uri: Uri,
        durationMs: Long,
        count: Int = 8,
        thumbWidth: Int = 144,
        thumbHeight: Int = 144,
        windowStartMs: Long = 0L,
        windowEndMs: Long = durationMs,
        onProgressiveUpdate: ((List<Bitmap>) -> Unit)? = null
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        if (durationMs <= 0L) return@withContext emptyList()
        val safeStart = windowStartMs.coerceIn(0L, durationMs)
        val safeEnd = windowEndMs.coerceIn(safeStart + 100L, durationMs)
        val spanMs = (safeEnd - safeStart).coerceAtLeast(100L)
        val result = mutableListOf<Bitmap>()
        val step = (spanMs / count).coerceAtLeast(1L)
        val retriever = MediaMetadataRetriever()
        try {
            if (uri.scheme == "file" && uri.path != null) {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }
            for (i in 0 until count) {
                val tMs = safeStart + i * step
                val cached = getFromMemory(uri, tMs, thumbWidth, thumbHeight)
                if (cached != null && !cached.isRecycled) {
                    result.add(cached)
                    onProgressiveUpdate?.invoke(result.toList())
                    continue
                }
                val timeUs = tMs * 1000L
                val frame = decodeFrameFromRetriever(
                    retriever,
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    thumbWidth,
                    thumbHeight
                ) ?: decodeFrameFromRetriever(
                    retriever,
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    thumbWidth,
                    thumbHeight
                )

                if (frame != null && !frame.isRecycled) {
                    putToMemory(uri, tMs, frame, thumbWidth, thumbHeight)
                    result.add(frame)
                    onProgressiveUpdate?.invoke(result.toList())
                }
            }
        } catch (e: Exception) {
            DebugLogManager.e(TAG, "Error during prefetching thumbnails", e)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
        result
    }

    fun getMemorySizeBytes(): Long {
        return memoryCache.size().toLong() * 1024L
    }

    fun clear() {
        memoryCache.evictAll()
    }
}
