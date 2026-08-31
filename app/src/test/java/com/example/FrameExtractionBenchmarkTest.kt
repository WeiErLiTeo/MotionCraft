package com.example

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.util.MotionPhotoHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FrameExtractionBenchmarkTest {

    @Test
    fun benchmarkMediaMetadataRetrieverReuse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dummyUri = Uri.parse("content://dummy/video.mp4")
        val steps = 5
        val timestampsMs = List(steps) { i -> (i * 500).toLong() }

        // 1. Baseline: Repeatedly initializing retriever inside loop
        val baselineTimeNs = measureNanoTime {
            for (timeMs in timestampsMs) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, dummyUri)
                    val timeUs = timeMs * 1000
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (e: Exception) {
                    // Ignore for benchmark
                } finally {
                    try {
                        retriever.release()
                    } catch (e: Exception) {}
                }
            }
        }

        // 2. Optimized: MotionPhotoHelper batch extraction with single retriever instance
        val optimizedTimeNs = measureNanoTime {
            MotionPhotoHelper.extractVideoFrames(context, dummyUri, timestampsMs)
        }

        val baselineMs = baselineTimeNs / 1_000_000.0
        val optimizedMs = optimizedTimeNs / 1_000_000.0

        println("=== Performance Benchmark Result ===")
        println("Baseline time (separate instances per frame): %.3f ms".format(baselineMs))
        println("Optimized time (batch helper with single instance): %.3f ms".format(optimizedMs))
        println("=====================================")

        // Batch helper returns empty list or frames safely without throwing exception
        val frames = MotionPhotoHelper.extractVideoFrames(context, dummyUri, timestampsMs)
        assertNotNull(frames)
    }
}
