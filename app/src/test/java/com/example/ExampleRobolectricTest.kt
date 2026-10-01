package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.MotionPhotoHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertTrue(appName == "实况照片" || appName == "Live Photo")
  }

  @Test
  fun `test oppo live photo packaging with robolectric`() {
    val mockJpeg = byteArrayOf(
      0xFF.toByte(), 0xD8.toByte(), // SOI
      0xFF.toByte(), 0xDB.toByte(), 0x00, 0x04, 0x00, 0x01, // DQT
      0xFF.toByte(), 0xD9.toByte()  // EOI
    )
    val mockVideo = "fake_mp4_video_content_with_ftyp".toByteArray(Charsets.UTF_8)
    val tempOut = File.createTempFile("test_oppo_motion", ".jpg")
    try {
      val success = MotionPhotoHelper.packageMotionPhoto(mockJpeg, mockVideo, tempOut, 500_000L)
      assertTrue(success)
      assertTrue(tempOut.exists())

      val outputBytes = tempOut.readBytes()
      val outputStr = String(outputBytes, Charsets.ISO_8859_1)

      // 1. Contains OPPO OpCamera namespace and attributes
      assertTrue(outputStr.contains("http://ns.oplus.com/photos/1.0/camera/"))
      assertTrue(outputStr.contains("OpCamera:MotionPhotoOwner=\"oplus\""))
      assertTrue(outputStr.contains("OpCamera:OLivePhotoVersion=\"2\""))
      assertTrue(outputStr.contains("OpCamera:VideoLength=\"${mockVideo.size}\""))
      assertTrue(outputStr.contains("OpCamera:MotionPhotoPrimaryPresentationTimestampUs=\"500000\""))

      // 2. Contains Google Camera standards for cross-brand compatibility
      assertTrue(outputStr.contains("http://ns.google.com/photos/1.0/camera/"))
      assertTrue(outputStr.contains("GCamera:MotionPhoto=\"1\""))

      // 3. Contains oplustag in Exif
      assertTrue(outputStr.contains("oplustag\":8388608"))

      // 4. Verify MPF segment exists and records exact JPEG size
      val mpfIdx = outputBytes.indexOfMpf()
      assertTrue("MPF segment must be present", mpfIdx >= 0)
      val recordedSize = ((outputBytes[mpfIdx + 62].toInt() and 0xFF) shl 24) or
              ((outputBytes[mpfIdx + 63].toInt() and 0xFF) shl 16) or
              ((outputBytes[mpfIdx + 64].toInt() and 0xFF) shl 8) or
              (outputBytes[mpfIdx + 65].toInt() and 0xFF)

      // The MP4 video starts exactly at recordedSize
      assertEquals(outputBytes.size - mockVideo.size, recordedSize)
    } finally {
      tempOut.delete()
    }
  }

  private fun ByteArray.indexOfMpf(): Int {
    for (i in 0 until this.size - 4) {
      if (this[i] == 0xFF.toByte() && this[i + 1] == 0xE2.toByte() &&
        this[i + 4] == 'M'.code.toByte() && this[i + 5] == 'P'.code.toByte() &&
        this[i + 6] == 'F'.code.toByte() && this[i + 7] == 0.toByte()) {
        return i
      }
    }
    return -1
  }
}
