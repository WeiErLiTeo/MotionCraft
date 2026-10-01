package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.collection.LruCache
import com.example.util.DebugLogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoThumbnailCache {
    private const val TAG = "VideoThumbnailCache"

    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(128) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    private fun getCacheKey(uri: Uri, timeMs: Long, targetWidth: Int, targetHeight: Int): String {
        val roundedMs = ((timeMs + 25L) / 50L) * 50L
        return "${uri}_${roundedMs}_${targetWidth}x${targetHeight}"
    }

    fun getFromMemory(uri: Uri, timeMs: Long, targetWidth: Int = 0, targetHeight: Int = 0): Bitmap? {
        val key = getCacheKey(uri, timeMs, targetWidth, targetHeight)
        return memoryCache.get(key)
    }

    fun putToMemory(uri: Uri, timeMs: Long, bitmap: Bitmap, targetWidth: Int = 0, targetHeight: Int = 0) {
        val key = getCacheKey(uri, timeMs, targetWidth, targetHeight)
        memoryCache.put(key, bitmap)
    }

    suspend fun getOrExtractFrame(
        context: Context,
        uri: Uri,
        timeMs: Long,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cached = getFromMemory(uri, timeMs, targetWidth, targetHeight)
        if (cached != null && !cached.isRecycled) {
            return@withContext cached
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val timeUs = timeMs * 1000L
            val frame = if (targetWidth > 0 && targetHeight > 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetWidth,
                    targetHeight
                ) ?: retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    targetWidth,
                    targetHeight
                )
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            }

            if (frame != null) {
                putToMemory(uri, timeMs, frame, targetWidth, targetHeight)
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

    suspend fun prefetchThumbnails(
        context: Context,
        uri: Uri,
        durationMs: Long,
        count: Int = 8,
        thumbWidth: Int = 160,
        thumbHeight: Int = 160
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        if (durationMs <= 0L) return@withContext emptyList()
        val result = mutableListOf<Bitmap>()
        val step = (durationMs / count).coerceAtLeast(1L)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            for (i in 0 until count) {
                val tMs = i * step
                val cached = getFromMemory(uri, tMs, thumbWidth, thumbHeight)
                if (cached != null && !cached.isRecycled) {
                    result.add(cached)
                    continue
                }
                val timeUs = tMs * 1000L
                val frame = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        thumbWidth,
                        thumbHeight
                    ) ?: retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        thumbWidth,
                        thumbHeight
                    )
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                }

                if (frame != null) {
                    putToMemory(uri, tMs, frame, thumbWidth, thumbHeight)
                    result.add(frame)
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

    fun clear() {
        memoryCache.evictAll()
    }
}
