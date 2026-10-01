package com.example.core.media

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

object ImageProcessor {
    private const val TAG = "ImageProcessor"

    fun findSubarray(array: ByteArray, pattern: ByteArray): Int {
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
        } catch (_: Exception) {
            false
        }
    }

    fun extractVideoFromMotionPhoto(file: File, cacheFile: File): Boolean {
        return try {
            if (!file.exists() || file.length() < 100) return false
            val raf = RandomAccessFile(file, "r")
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

    fun extractXmpXml(bytes: ByteArray): String {
        return try {
            val ftypPattern = byteArrayOf(0x66, 0x74, 0x79, 0x70)
            val ftypIdx = findSubarray(bytes, ftypPattern)
            val searchLimit = if (ftypIdx != -1) ftypIdx else bytes.size

            val xmpHeader = "<?xpacket begin".toByteArray(Charsets.UTF_8)
            val headerIdx = findSubarray(bytes.sliceArray(0 until searchLimit), xmpHeader)
            if (headerIdx != -1) {
                val xmpFooter = "<?xpacket end".toByteArray(Charsets.UTF_8)
                val footerIdx = findSubarray(bytes.sliceArray(headerIdx until searchLimit), xmpFooter)
                return if (footerIdx != -1) {
                    val endIdx = (headerIdx + footerIdx + 19).coerceAtMost(bytes.size)
                    String(bytes.sliceArray(headerIdx until endIdx), Charsets.UTF_8)
                } else {
                    String(bytes.sliceArray(headerIdx until (headerIdx + 8192).coerceAtMost(bytes.size)), Charsets.UTF_8)
                }
            }

            val xmpMetaHeader = "<x:xmpmeta".toByteArray(Charsets.UTF_8)
            val metaIdx = findSubarray(bytes.sliceArray(0 until searchLimit), xmpMetaHeader)
            if (metaIdx != -1) {
                val xmpMetaFooter = "</x:xmpmeta>".toByteArray(Charsets.UTF_8)
                val footerIdx = findSubarray(bytes.sliceArray(metaIdx until searchLimit), xmpMetaFooter)
                return if (footerIdx != -1) {
                    val endIdx = (metaIdx + footerIdx + 12).coerceAtMost(bytes.size)
                    String(bytes.sliceArray(metaIdx until endIdx), Charsets.UTF_8)
                } else {
                    String(bytes.sliceArray(metaIdx until (metaIdx + 8192).coerceAtMost(bytes.size)), Charsets.UTF_8)
                }
            }

            val nsBytes = "http://ns.adobe.com/xap/1.0/".toByteArray(Charsets.UTF_8)
            val nsIdx = findSubarray(bytes.sliceArray(0 until searchLimit), nsBytes)
            if (nsIdx != -1) {
                val startXml = (nsIdx + nsBytes.size).coerceAtMost(bytes.size)
                val sample = bytes.sliceArray(startXml until (startXml + 8192).coerceAtMost(bytes.size))
                return String(sample, Charsets.UTF_8).trimStart { it.code == 0 || it.isWhitespace() }
            }

            ""
        } catch (e: Exception) {
            "解析 XMP 出错: ${e.message}"
        }
    }

    fun extractExifMetadata(inputStream: InputStream): String {
        return try {
            val exifInterface = ExifInterface(inputStream)
            val sb = StringBuilder()
            val tags = listOf(
                "相机品牌 (Make)" to ExifInterface.TAG_MAKE,
                "相机型号 (Model)" to ExifInterface.TAG_MODEL,
                "拍摄时间 (DateTime)" to ExifInterface.TAG_DATETIME,
                "数字化时间 (DateTimeDigitized)" to ExifInterface.TAG_DATETIME_DIGITIZED,
                "图像宽度 (ImageWidth)" to ExifInterface.TAG_IMAGE_WIDTH,
                "图像高度 (ImageLength)" to ExifInterface.TAG_IMAGE_LENGTH,
                "画面朝向 (Orientation)" to ExifInterface.TAG_ORIENTATION,
                "曝光时间 (ExposureTime)" to ExifInterface.TAG_EXPOSURE_TIME,
                "光圈 F值 (FNumber)" to ExifInterface.TAG_F_NUMBER,
                "ISO 感光度 (ISO)" to ExifInterface.TAG_ISO_SPEED_RATINGS,
                "焦距 (FocalLength)" to ExifInterface.TAG_FOCAL_LENGTH,
                "GPS 纬度 (GPSLatitude)" to ExifInterface.TAG_GPS_LATITUDE,
                "GPS 经度 (GPSLongitude)" to ExifInterface.TAG_GPS_LONGITUDE
            )
            for (pair in tags) {
                val label = pair.first
                val tag = pair.second
                val value = exifInterface.getAttribute(tag)
                if (!value.isNullOrBlank()) {
                    sb.append(label).append(": ").append(value).append("\n")
                }
            }
            if (sb.isEmpty()) {
                "此图片未包含 EXIF 拍摄参数（可能经社交软件/截图压缩已抹除元数据）"
            } else {
                sb.toString().trimEnd()
            }
        } catch (e: Exception) {
            "读取 EXIF 失败: ${e.message}"
        }
    }
}
