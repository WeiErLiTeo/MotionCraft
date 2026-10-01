package com.example

import com.example.util.MotionPhotoHelper
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying live photo binary packaging and OPPO/ColorOS compatibility.
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testOppoMpfSegmentStructure() {
    val imageSize = 98765
    val mpf = MotionPhotoHelper.buildMpfSegment(imageSize)
    
    // 1. Strict 74 bytes check
    assertEquals(74, mpf.size)
    // 2. Starts with FF E2
    assertEquals(0xFF.toByte(), mpf[0])
    assertEquals(0xE2.toByte(), mpf[1])
    // 3. Length field is 72 (74 - 2)
    val segLen = ((mpf[2].toInt() and 0xFF) shl 8) or (mpf[3].toInt() and 0xFF)
    assertEquals(72, segLen)
    // 4. Contains MPF\0 signature
    val sig = String(mpf.sliceArray(4..7), Charsets.US_ASCII)
    assertEquals("MPF\u0000", sig)
    // 5. Encoded size matches imageSize
    val encodedSize = ((mpf[62].toInt() and 0xFF) shl 24) or
            ((mpf[63].toInt() and 0xFF) shl 16) or
            ((mpf[64].toInt() and 0xFF) shl 8) or
            (mpf[65].toInt() and 0xFF)
    assertEquals(imageSize, encodedSize)
  }
}
