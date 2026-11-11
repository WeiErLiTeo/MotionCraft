package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile

object ImageProcessor {
    private const val TAG = "ImageProcessor"

    private val IMAGE_FTYP_BRANDS = setOf(
        "heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs",
        "mif1", "msf1", "avif", "avis", "crx "
    )

    private val KNOWN_VIDEO_FTYP_BRANDS = setOf(
        "isom", "mp41", "mp42", "qt  ", "avc1", "hvc1", "hev1",
        "iso2", "iso4", "iso5", "iso6", "mmp4", "3gp4", "3gp5",
        "msnv", "f4v ", "dash", "nvr1", "mp71"
    )

    private val VALID_NEXT_MP4_BOXES = setOf(
        "moov", "mdat", "free", "wide", "skip", "uuid", "pdin", "meta", "styp", "edts", "beam"
    )

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

    /**
     * Finds the byte offset of an embedded MP4/MOV 'ftyp' box inside a JPEG or HEIC byte buffer,
     * skipping the primary image's own 'ftyp' header if the container is HEIC/AVIF and validating
     * the MP4 box header so random 'ftyp' bytes inside compressed JPEG scan data are never mistaken for video.
     */
    private fun findVideoFtypOffsetInBuffer(buffer: ByteArray, limit: Int, isFirstChunk: Boolean): Int {
        for (i in 4 until limit - 8) {
            if (buffer[i] == 0x66.toByte() &&
                buffer[i + 1] == 0x74.toByte() &&
                buffer[i + 2] == 0x79.toByte() &&
                buffer[i + 3] == 0x70.toByte()
            ) {
                // Verify 4 bytes of brand are printable ASCII
                val b0 = buffer[i + 4].toInt() and 0xFF
                val b1 = buffer[i + 5].toInt() and 0xFF
                val b2 = buffer[i + 6].toInt() and 0xFF
                val b3 = buffer[i + 7].toInt() and 0xFF
                if (b0 !in 0x20..0x7E || b1 !in 0x20..0x7E || b2 !in 0x20..0x7E || b3 !in 0x20..0x7E) {
                    continue
                }
                val brand = String(buffer, i + 4, 4, Charsets.US_ASCII).lowercase()
                if (isFirstChunk && i <= 16 && IMAGE_FTYP_BRANDS.contains(brand)) {
                    continue
                }
                if (IMAGE_FTYP_BRANDS.contains(brand)) {
                    continue
                }
                // Check big-endian box size at [i-4 .. i-1]
                val boxSize = ((buffer[i - 4].toInt() and 0xFF) shl 24) or
                    ((buffer[i - 3].toInt() and 0xFF) shl 16) or
                    ((buffer[i - 2].toInt() and 0xFF) shl 8) or
                    (buffer[i - 1].toInt() and 0xFF)
                if (boxSize !in 16..256) {
                    continue
                }
                val nextBoxTypeOffset = i - 4 + boxSize + 4
                if (nextBoxTypeOffset + 4 <= limit) {
                    val nextBox = String(buffer, nextBoxTypeOffset, 4, Charsets.US_ASCII).lowercase()
                    if (VALID_NEXT_MP4_BOXES.contains(nextBox) || KNOWN_VIDEO_FTYP_BRANDS.contains(brand)) {
                        return i
                    }
                } else if (KNOWN_VIDEO_FTYP_BRANDS.contains(brand)) {
                    return i
                }
            }
        }
        return -1
    }

    fun findVideoOffset(bytes: ByteArray): Int {
        if (bytes.size < 16) return -1
        val xmp = extractXmpXml(bytes.sliceArray(0 until bytes.size.coerceAtMost(64 * 1024)))
        if (parseXmpDeclaredVideoLength(xmp) > 256L ||
            xmp.contains("GCamera:MotionPhoto=\"1\"") ||
            xmp.contains("GCamera:MicroVideo=\"1\"")
        ) {
            return 1
        }
        return findVideoFtypOffsetInBuffer(bytes, bytes.size, isFirstChunk = true)
    }

    private fun parseXmpDeclaredVideoLength(xmp: String): Long {
        if (xmp.isBlank()) return -1L
        val microMatch = Regex("""GCamera:MicroVideoOffset\s*=\s*"(\d+)"""").find(xmp)
            ?: Regex("""OpCamera:VideoLength\s*=\s*"(\d+)"""").find(xmp)
        if (microMatch != null) {
            val v = microMatch.groupValues[1].toLongOrNull() ?: -1L
            if (v > 256L) return v
        }
        val itemRegex = Regex("""<Container:Item[^>]*Item:Semantic\s*=\s*"MotionPhoto"[^>]*Item:Length\s*=\s*"(\d+)"|<Container:Item[^>]*Item:Length\s*=\s*"(\d+)"[^>]*Item:Semantic\s*=\s*"MotionPhoto"""")
        val itemMatch = itemRegex.find(xmp)
        if (itemMatch != null) {
            val vStr = itemMatch.groupValues[1].ifEmpty { itemMatch.groupValues[2] }
            val v = vStr.toLongOrNull() ?: -1L
            if (v > 256L) return v
        }
        return -1L
    }

    fun isMotionPhoto(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val chunkSize = 64 * 1024
                val buffer = ByteArray(chunkSize + 16)
                var overlap = 0
                var totalRead = 0L
                var isFirstChunk = true
                val maxSearch = 35L * 1024 * 1024
                while (totalRead < maxSearch) {
                    val bytesRead = inputStream.read(buffer, overlap, chunkSize)
                    if (bytesRead <= 0) break
                    val limit = overlap + bytesRead
                    if (isFirstChunk) {
                        val headerSample = buffer.sliceArray(0 until limit)
                        val xmp = extractXmpXml(headerSample)
                        if (parseXmpDeclaredVideoLength(xmp) > 256L ||
                            xmp.contains("GCamera:MotionPhoto=\"1\"") ||
                            xmp.contains("GCamera:MicroVideo=\"1\"")
                        ) {
                            return true
                        }
                    }
                    if (limit >= 12 && findVideoFtypOffsetInBuffer(buffer, limit, isFirstChunk) != -1) {
                        return true
                    }
                    isFirstChunk = false
                    totalRead += bytesRead
                    if (limit >= 16) {
                        System.arraycopy(buffer, limit - 16, buffer, 0, 16)
                        overlap = 16
                    } else {
                        overlap = 0
                    }
                }
                false
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if the file ends with Samsung's SEFH...SEFT footer and returns the footer length to trim.
     */
    private fun detectSamsungSefTrailerLength(raf: RandomAccessFile, fileLength: Long): Long {
        if (fileLength < 28) return 0L
        return try {
            val tail = ByteArray(8)
            raf.seek(fileLength - 8)
            raf.readFully(tail)
            val magic = String(tail, 4, 4, Charsets.US_ASCII)
            if (magic == "SEFT") {
                val sefTableLen = (tail[0].toInt() and 0xFF) or
                    ((tail[1].toInt() and 0xFF) shl 8) or
                    ((tail[2].toInt() and 0xFF) shl 16) or
                    ((tail[3].toInt() and 0xFF) shl 24)
                val totalFooter = sefTableLen.toLong() + 8L
                if (totalFooter in 20L..65536L) totalFooter else 28L
            } else {
                0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun isFtypAtOffset(raf: RandomAccessFile, boxStartOffset: Long, fileLength: Long): Boolean {
        if (boxStartOffset < 0L || boxStartOffset + 12L > fileLength) return false
        return try {
            val hdr = ByteArray(8)
            raf.seek(boxStartOffset)
            raf.readFully(hdr)
            hdr[4] == 0x66.toByte() &&
                hdr[5] == 0x74.toByte() &&
                hdr[6] == 0x79.toByte() &&
                hdr[7] == 0x70.toByte()
        } catch (_: Exception) {
            false
        }
    }

    fun extractVideoFromMotionPhoto(file: File, cacheFile: File): Boolean {
        return try {
            if (!file.exists() || file.length() < 100) return false
            val fileLength = file.length()

            // For files <= 45MB, check ZLC Samsung SEF exact structure first
            if (fileLength <= 45L * 1024 * 1024) {
                val allBytes = file.readBytes()
                val sefParsed = com.example.core.zlc.ZlcSamsungSef.parse(allBytes)
                if (sefParsed != null) {
                    val motionRec = sefParsed.records.firstOrNull {
                        it.type == com.example.core.zlc.ZlcSamsungSef.TYPE_MOTION_PHOTO_DATA ||
                            it.key == com.example.core.zlc.ZlcSamsungSef.KEY_MOTION_PHOTO_DATA
                    }
                    if (motionRec != null && motionRec.payload.size > 256) {
                        val pureEnd = com.example.core.zlc.ZlcMp4Util.findPureMp4End(motionRec.payload, 0)
                        val cleanMp4 = if (pureEnd > 256) motionRec.payload.copyOfRange(0, pureEnd) else motionRec.payload
                        cacheFile.writeBytes(cleanMp4)
                        return cacheFile.exists() && cacheFile.length() > 256L
                    }
                }
            }

            val raf = RandomAccessFile(file, "r")
            val sefFooterLen = detectSamsungSefTrailerLength(raf, fileLength)

            var startOffset = -1L

            // 1. First try exact offset from XMP metadata (same strategy as Douyin & Google Photos)
            val headBytes = ByteArray(fileLength.coerceAtMost(64 * 1024L).toInt())
            raf.seek(0L)
            raf.readFully(headBytes)
            val xmpStr = extractXmpXml(headBytes)
            val declaredLen = parseXmpDeclaredVideoLength(xmpStr)
            if (declaredLen in 256L until fileLength) {
                val candidate1 = fileLength - declaredLen
                val candidate2 = fileLength - declaredLen - sefFooterLen
                if (isFtypAtOffset(raf, candidate1, fileLength)) {
                    startOffset = candidate1
                } else if (isFtypAtOffset(raf, candidate2, fileLength)) {
                    startOffset = candidate2
                }
            }

            // 2. Fallback: scan with strict MP4 box validation
            if (startOffset == -1L) {
                val buffer = ByteArray(64 * 1024)
                var ftypOffset = -1L
                var pos = 0L
                var isFirstChunk = true
                while (pos < fileLength) {
                    raf.seek(pos)
                    val bytesRead = raf.read(buffer)
                    if (bytesRead < 12) break

                    val foundIdx = findVideoFtypOffsetInBuffer(buffer, bytesRead, isFirstChunk)
                    if (foundIdx != -1) {
                        ftypOffset = pos + foundIdx
                        break
                    }
                    isFirstChunk = false
                    pos += (bytesRead - 16).coerceAtLeast(1)
                }
                if (ftypOffset != -1L) {
                    startOffset = (ftypOffset - 4).coerceAtLeast(0L)
                }
            }

            if (startOffset == -1L) {
                raf.close()
                return false
            }

            val endOffset = (fileLength - sefFooterLen).coerceAtLeast(startOffset)
            raf.seek(startOffset)

            cacheFile.outputStream().buffered(64 * 1024).use { out ->
                val copyBuffer = ByteArray(64 * 1024)
                var bytesToRead = endOffset - startOffset
                while (bytesToRead > 0) {
                    val readSize = raf.read(copyBuffer, 0, copyBuffer.size.coerceAtMost(bytesToRead.toInt()))
                    if (readSize <= 0) break
                    out.write(copyBuffer, 0, readSize)
                    bytesToRead -= readSize
                }
            }
            raf.close()

            // Trim any trailing vendor uuid/footer boxes (e.g. vivo uuid or Honor uuid + LIVE_ 60B tail)
            if (cacheFile.exists() && cacheFile.length() in 257L..(45L * 1024 * 1024)) {
                val extractedBytes = cacheFile.readBytes()
                val pureEnd = com.example.core.zlc.ZlcMp4Util.findPureMp4End(extractedBytes, 0)
                if (pureEnd in 256 until extractedBytes.size) {
                    cacheFile.writeBytes(extractedBytes.copyOfRange(0, pureEnd))
                }
            }

            cacheFile.exists() && cacheFile.length() > 256L
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting video from motion photo file", e)
            false
        }
    }

    fun extractVideoFromMotionPhoto(inputStream: InputStream, cacheFile: File): Boolean {
        return try {
            val tempSource = File(cacheFile.parentFile, "temp_motion_src_${System.currentTimeMillis()}.tmp")
            try {
                tempSource.outputStream().buffered(64 * 1024).use { out ->
                    inputStream.use { input ->
                        input.copyTo(out, 64 * 1024)
                    }
                }
                return extractVideoFromMotionPhoto(tempSource, cacheFile)
            } finally {
                tempSource.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting motion video", e)
            false
        }
    }

    /**
     * Ensures that a cover image (which might be HEIC/HEIF/PNG/WEBP from Apple Live Photos or gallery)
     * is converted to a valid JPEG file with EXIF orientation and shooting metadata preserved.
     */
    fun ensureJpegCover(context: Context, sourceUri: Uri?, sourceFile: File, targetJpegFile: File): Boolean {
        return try {
            if (!sourceFile.exists() && sourceUri != null) {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    sourceFile.outputStream().buffered(64 * 1024).use { output ->
                        input.copyTo(output, 64 * 1024)
                    }
                }
            }
            if (!sourceFile.exists() || sourceFile.length() < 4) return false

            val header = ByteArray(2)
            sourceFile.inputStream().use { it.read(header) }
            val isAlreadyJpeg = (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte())

            if (isAlreadyJpeg) {
                if (sourceFile.absolutePath != targetJpegFile.absolutePath) {
                    sourceFile.copyTo(targetJpegFile, overwrite = true)
                }
                return true
            }

            // Decode non-JPEG (such as Apple .HEIC) to Bitmap
            var bitmap: Bitmap? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val source = ImageDecoder.createSource(sourceFile)
                    bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = false
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "ImageDecoder failed on ${sourceFile.name}, trying fallback: ${e.message}")
                }
            }
            if (bitmap == null) {
                val raw = BitmapFactory.decodeFile(sourceFile.absolutePath)
                if (raw != null) {
                    bitmap = applyExifOrientation(sourceFile, raw)
                }
            }
            if (bitmap == null) {
                try {
                    val exif = ExifInterface(sourceFile.absolutePath)
                    val exifThumb = exif.thumbnailBitmap
                    if (exifThumb != null) {
                        bitmap = applyExifOrientation(sourceFile, exifThumb)
                    }
                } catch (_: Exception) {}
            }

            if (bitmap == null) return false

            val tempJpeg = File(targetJpegFile.parentFile, "conv_${System.currentTimeMillis()}_${sourceFile.name.hashCode()}.jpg")
            FileOutputStream(tempJpeg).buffered(64 * 1024).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            copyExifAttributes(sourceFile, tempJpeg)
            tempJpeg.copyTo(targetJpegFile, overwrite = true)
            tempJpeg.delete()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error converting cover to JPEG: ${e.message}", e)
            false
        }
    }

    /**
     * Ultra-fast thumbnail extractor for ZIP archive items (.HEIC, .JPG, .PNG, .WEBP, .GIF):
     * 1. Reads embedded EXIF thumbnail in ~1-2ms if present.
     * 2. Uses hardware/software scaled ImageDecoder (setTargetSize) on API 28+ so 48MP HEICs
     *    decode directly at thumbnail resolution (~256px) instead of full resolution.
     * 3. Falls back to sampled BitmapFactory.
     */
    fun extractFastThumbnail(sourceFile: File, targetThumbFile: File, maxDim: Int = 256): Boolean {
        if (!sourceFile.exists() || sourceFile.length() < 16L) return false
        return try {
            var thumbBmp: Bitmap? = null

            // 1. Fast embedded EXIF thumbnail (standard in Apple iPhone .HEIC and .JPG)
            try {
                val exif = ExifInterface(sourceFile.absolutePath)
                if (exif.hasThumbnail()) {
                    val rawExifThumb = exif.thumbnailBitmap
                    if (rawExifThumb != null && rawExifThumb.width > 16 && rawExifThumb.height > 16) {
                        thumbBmp = applyExifOrientation(sourceFile, rawExifThumb)
                    }
                }
            } catch (_: Exception) {}

            // 2. Scaled ImageDecoder on Android 9+ (decodes HEIC/WEBP/PNG directly at ~256px)
            if (thumbBmp == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val source = ImageDecoder.createSource(sourceFile)
                    thumbBmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = false
                        val w = info.size.width.coerceAtLeast(1)
                        val h = info.size.height.coerceAtLeast(1)
                        val longest = maxOf(w, h)
                        if (longest > maxDim) {
                            val scale = maxDim.toFloat() / longest.toFloat()
                            decoder.setTargetSize(
                                (w * scale).toInt().coerceAtLeast(1),
                                (h * scale).toInt().coerceAtLeast(1)
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            // 3. Sampled BitmapFactory fallback
            if (thumbBmp == null) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    var sampleSize = 1
                    val longest = maxOf(bounds.outWidth, bounds.outHeight)
                    while (longest / sampleSize > maxDim) {
                        sampleSize *= 2
                    }
                    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    val decoded = BitmapFactory.decodeFile(sourceFile.absolutePath, opts)
                    if (decoded != null) {
                        thumbBmp = applyExifOrientation(sourceFile, decoded)
                    }
                }
            }

            if (thumbBmp != null) {
                FileOutputStream(targetThumbFile).buffered(16 * 1024).use { out ->
                    thumbBmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
                }
                targetThumbFile.exists() && targetThumbFile.length() > 128L
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractFastThumbnail failed for ${sourceFile.name}: ${e.message}")
            false
        }
    }

    /**
     * In-memory thumbnail extractor for ZIP archive image entries (.HEIC, .JPG, .PNG, .WEBP):
     * Decodes directly from ByteArray/ByteBuffer in RAM without writing or deleting temporary image files on disk.
     */
    fun extractFastThumbnailFromBytes(bytes: ByteArray, targetThumbFile: File, maxDim: Int = 256): Boolean {
        if (bytes.size < 16) return false
        return try {
            var thumbBmp: Bitmap? = null

            // 1. Fast embedded EXIF thumbnail in memory
            try {
                val exif = ExifInterface(java.io.ByteArrayInputStream(bytes))
                if (exif.hasThumbnail()) {
                    val rawExifThumb = exif.thumbnailBitmap
                    if (rawExifThumb != null && rawExifThumb.width > 16 && rawExifThumb.height > 16) {
                        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                        val matrix = Matrix()
                        thumbBmp = when (orientation) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> {
                                matrix.postRotate(90f)
                                Bitmap.createBitmap(rawExifThumb, 0, 0, rawExifThumb.width, rawExifThumb.height, matrix, true)
                            }
                            ExifInterface.ORIENTATION_ROTATE_180 -> {
                                matrix.postRotate(180f)
                                Bitmap.createBitmap(rawExifThumb, 0, 0, rawExifThumb.width, rawExifThumb.height, matrix, true)
                            }
                            ExifInterface.ORIENTATION_ROTATE_270 -> {
                                matrix.postRotate(270f)
                                Bitmap.createBitmap(rawExifThumb, 0, 0, rawExifThumb.width, rawExifThumb.height, matrix, true)
                            }
                            else -> rawExifThumb
                        }
                    }
                }
            } catch (_: Exception) {}

            // 2. Direct ByteBuffer scaled ImageDecoder on Android 9+ (decodes HEIC/JPG/PNG/WEBP in RAM at ~256px)
            if (thumbBmp == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val source = ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
                    thumbBmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = false
                        val w = info.size.width.coerceAtLeast(1)
                        val h = info.size.height.coerceAtLeast(1)
                        val longest = maxOf(w, h)
                        if (longest > maxDim) {
                            val scale = maxDim.toFloat() / longest.toFloat()
                            decoder.setTargetSize(
                                (w * scale).toInt().coerceAtLeast(1),
                                (h * scale).toInt().coerceAtLeast(1)
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            // 3. Sampled BitmapFactory from ByteArray fallback
            if (thumbBmp == null) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    var sampleSize = 1
                    val longest = maxOf(bounds.outWidth, bounds.outHeight)
                    while (longest / sampleSize > maxDim) {
                        sampleSize *= 2
                    }
                    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    thumbBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                }
            }

            if (thumbBmp != null) {
                FileOutputStream(targetThumbFile).buffered(16 * 1024).use { out ->
                    thumbBmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
                }
                targetThumbFile.exists() && targetThumbFile.length() > 128L
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun copyExifAttributes(srcFile: File, dstJpegFile: File) {
        try {
            val srcExif = ExifInterface(srcFile.absolutePath)
            val dstExif = ExifInterface(dstJpegFile.absolutePath)
            val tagsToCopy = listOf(
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_SUBSEC_TIME,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_ISO_SPEED_RATINGS,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_GPS_ALTITUDE_REF
            )
            var modified = false
            for (tag in tagsToCopy) {
                val v = srcExif.getAttribute(tag)
                if (!v.isNullOrBlank()) {
                    dstExif.setAttribute(tag, v)
                    modified = true
                }
            }
            dstExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            if (modified) {
                dstExif.saveAttributes()
            }
        } catch (_: Exception) {}
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
