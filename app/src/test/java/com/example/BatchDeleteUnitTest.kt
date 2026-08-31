package com.example

import com.example.data.LivePhotoRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.system.measureNanoTime

class BatchDeleteUnitTest {

    @Test
    fun testBatchDeleteLogic() {
        val tempDir = File.createTempFile("test", "dir").apply {
            delete()
            mkdir()
        }

        try {
            val count = 100
            val records = (1..count).map { id ->
                val cover = File(tempDir, "cover_$id.jpg").apply { createNewFile() }
                val video = File(tempDir, "video_$id.mp4").apply { createNewFile() }
                LivePhotoRecord(
                    id = id,
                    title = "Photo $id",
                    coverPath = cover.absolutePath,
                    videoPath = video.absolutePath
                )
            }

            assertEquals(count * 2, tempDir.listFiles()?.size ?: 0)

            // Simulate baseline: individual file deletion + individual DB query calls (N operations)
            var baselineNs = 0L
            val simulatedDbCalls = mutableListOf<Int>()

            val timeBaseline = measureNanoTime {
                records.forEach { record ->
                    File(record.coverPath).delete()
                    File(record.videoPath).delete()
                    simulatedDbCalls.add(record.id)
                }
            }

            assertEquals(0, tempDir.listFiles()?.size ?: 0)
            assertEquals(100, simulatedDbCalls.size)

            // Re-create files for batch test
            val batchRecords = (1..count).map { id ->
                val cover = File(tempDir, "cover_batch_$id.jpg").apply { createNewFile() }
                val video = File(tempDir, "video_batch_$id.mp4").apply { createNewFile() }
                LivePhotoRecord(
                    id = id,
                    title = "Photo Batch $id",
                    coverPath = cover.absolutePath,
                    videoPath = video.absolutePath
                )
            }

            assertEquals(count * 2, tempDir.listFiles()?.size ?: 0)

            val batchDbCalls = mutableListOf<List<LivePhotoRecord>>()

            val timeBatch = measureNanoTime {
                batchRecords.forEach { record ->
                    File(record.coverPath).delete()
                    File(record.videoPath).delete()
                }
                batchDbCalls.add(batchRecords)
            }

            assertEquals(0, tempDir.listFiles()?.size ?: 0)
            assertEquals(1, batchDbCalls.size)
            assertEquals(100, batchDbCalls[0].size)

            println("Individual operations (N=100) time: ${timeBaseline / 1_000_000.0} ms")
            println("Batch operation (N=100) time: ${timeBatch / 1_000_000.0} ms")

        } finally {
            tempDir.deleteRecursively()
        }
    }
}
