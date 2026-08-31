package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
class MotionPhotoHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun extractVideoFromMotionPhoto_inputStreamThrowsIOException_returnsFalse() {
        val failingInputStream = object : InputStream() {
            override fun read(): Int {
                throw IOException("Simulated read failure")
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                throw IOException("Simulated read failure")
            }
        }
        val cacheFile = tempFolder.newFile("test_cache.mp4")

        val result = MotionPhotoHelper.extractVideoFromMotionPhoto(failingInputStream, cacheFile)

        assertFalse("Expected extractVideoFromMotionPhoto to return false when IOException occurs", result)
    }

    @Test
    fun extractVideoFromMotionPhoto_emptyInputStream_returnsFalse() {
        val emptyInputStream = ByteArrayInputStream(ByteArray(0))
        val cacheFile = tempFolder.newFile("test_cache_empty.mp4")

        val result = MotionPhotoHelper.extractVideoFromMotionPhoto(emptyInputStream, cacheFile)

        assertFalse("Expected extractVideoFromMotionPhoto to return false for empty stream", result)
    }

    @Test
    fun extractVideoFromMotionPhoto_noFtypPattern_returnsFalse() {
        // Create dummy data that is long enough (>= 4 bytes) but doesn't contain 'ftyp'
        val dummyData = ByteArray(100) { 0xAA.toByte() }
        val inputStream = ByteArrayInputStream(dummyData)
        val cacheFile = tempFolder.newFile("test_cache_no_ftyp.mp4")

        val result = MotionPhotoHelper.extractVideoFromMotionPhoto(inputStream, cacheFile)

        assertFalse("Expected extractVideoFromMotionPhoto to return false when ftyp signature is missing", result)
    }

    @Test
    fun extractVideoFromMotionPhoto_validMotionPhotoStream_returnsTrueAndWritesFile() {
        // Construct simulated motion photo bytes: dummy header + offset bytes + 'ftyp' + mp4 payload
        val dummyHeader = ByteArray(10) { 0x01.toByte() }
        val ftypPattern = byteArrayOf(0x66, 0x74, 0x79, 0x70) // "ftyp"
        val mp4Payload = "MP4_VIDEO_DATA".toByteArray()

        val fullBytes = dummyHeader + ftypPattern + mp4Payload
        val inputStream = ByteArrayInputStream(fullBytes)
        val cacheFile = tempFolder.newFile("test_cache_valid.mp4")

        val result = MotionPhotoHelper.extractVideoFromMotionPhoto(inputStream, cacheFile)

        assertTrue("Expected extractVideoFromMotionPhoto to return true for valid stream", result)
        assertTrue("Cache file should exist and have non-zero size", cacheFile.exists() && cacheFile.length() > 0)
    }
}
