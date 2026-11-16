package com.example.core.protocol

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import com.example.core.media.ImageProcessor
import com.example.core.zlc.ZlcBinaryUtils
import com.example.core.zlc.ZlcJpegUtil
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

enum class DetectedOemBrand(val shortBadge: String, val fullName: String) {
    OPPO("OPPO", "OPPO / 一加 / realme"),
    XIAOMI("小米", "小米 / 红米 (HyperOS / MIUI)"),
    VIVO("vivo", "vivo / iQOO (OriginOS)"),
    SAMSUNG("三星", "三星 Galaxy (SEF)"),
    HONOR_HUAWEI("荣耀/华为", "华为 / 荣耀 (MagicOS / HarmonyOS)"),
    MEIZU("魅族", "魅族 (Flyme)"),
    GOOGLE("Pixel", "Google MotionPhoto"),
    GENERIC("实况", "通用实况动图"),
    NONE("", "静态图片")
}

data class MotionProbeResult(
    val isMotionPhoto: Boolean,
    val brand: DetectedOemBrand,
    val presentationTimestampUs: Long = 0L
)

object LivePhotoDetector {

    private val probeCache = object : LruCache<String, MotionProbeResult>(512) {}

    private val VIVO_CAMERALBUM_MARKER = "cameralbum!".toByteArray(Charsets.US_ASCII)
    private val VIVO_EXT_MARKER = "vivoMediaExtInfo".toByteArray(Charsets.US_ASCII)
    private val VIVO_MAGIC_BYTES = byteArrayOf(
        0x1B, 0x2A, 0x39, 0x48, 0x57, 0x66, 0x75,
        0x84.toByte(), 0x93.toByte(), 0xA2.toByte(), 0xB3.toByte()
    )
    private val HONOR_LIVE_MARKER = "LIVE_".toByteArray(Charsets.US_ASCII)
    private val SEFT_MAGIC = "SEFT".toByteArray(Charsets.US_ASCII)
    private val MOOV_BYTES = "moov".toByteArray(Charsets.US_ASCII)
    private val MVHD_BYTES = "mvhd".toByteArray(Charsets.US_ASCII)
    private val TRAK_BYTES = "trak".toByteArray(Charsets.US_ASCII)
    private val OPLUS_TAG_BYTES = "oplustag".toByteArray(Charsets.US_ASCII)

    fun probeFast(context: Context, uri: Uri, cacheKey: String? = null): MotionProbeResult {
        if (cacheKey != null) {
            probeCache.get(cacheKey)?.let { return it }
        }
        val result = try {
            val afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
            if (afd != null) {
                afd.use { descriptor ->
                    FileInputStream(descriptor.fileDescriptor).use { fis ->
                        val channel = fis.channel
                        val fileLen = descriptor.length.takeIf { it > 0L } ?: runCatching { channel.size() }.getOrDefault(-1L)
                        val startOffset = descriptor.startOffset.coerceAtLeast(0L)

                        val headLen = if (fileLen > 0L) minOf(fileLen, 65536L).toInt() else 65536
                        val headBuf = ByteBuffer.allocate(headLen)
                        channel.position(startOffset)
                        var readHead = 0
                        while (readHead < headLen) {
                            val r = channel.read(headBuf)
                            if (r <= 0) break
                            readHead += r
                        }
                        val head = if (readHead == headLen) headBuf.array() else headBuf.array().copyOf(readHead)

                        var tail = ByteArray(0)
                        if (fileLen > headLen.toLong()) {
                            val tailLen = minOf(fileLen - headLen, 65536L).toInt()
                            val tailPos = startOffset + fileLen - tailLen
                            val tailBuf = ByteBuffer.allocate(tailLen)
                            channel.position(tailPos)
                            var readTail = 0
                            while (readTail < tailLen) {
                                val r = channel.read(tailBuf)
                                if (r <= 0) break
                                readTail += r
                            }
                            tail = if (readTail == tailLen) tailBuf.array() else tailBuf.array().copyOf(readTail)
                        }

                        var detected = analyzeHeadAndTail(head, tail)
                        // If XMP declares VideoLength / MicroVideoOffset without an explicit MotionPhoto="1",
                        // verify 'ftyp' exists at the declared offset when channel is seekable.
                        if (!detected.isMotionPhoto && fileLen > 512L && head.isNotEmpty()) {
                            val xmp = ImageProcessor.extractXmpXml(head)
                            val declaredLen = parseDeclaredVideoLen(xmp)
                            if (declaredLen in 256L until fileLen) {
                                val candidateOffset = startOffset + fileLen - declaredLen
                                if (checkFtypAtChannelOffset(channel, candidateOffset, startOffset + fileLen)) {
                                    detected = classifyFromXmpAndExif(xmp, head, tail)
                                }
                            }
                        }
                        detected
                    }
                }
            } else {
                probeFromStreamFallback(context, uri)
            }
        } catch (_: Exception) {
            probeFromStreamFallback(context, uri)
        }

        if (cacheKey != null) {
            probeCache.put(cacheKey, result)
        }
        return result
    }

    fun probeFile(file: File): MotionProbeResult {
        if (!file.exists() || file.length() < 128L) {
            return MotionProbeResult(false, DetectedOemBrand.NONE)
        }
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                val headLen = minOf(len, 65536L).toInt()
                val head = ByteArray(headLen)
                raf.seek(0L)
                raf.readFully(head)

                val tail = if (len > headLen) {
                    val tailLen = minOf(len - headLen, 65536L).toInt()
                    val buf = ByteArray(tailLen)
                    raf.seek(len - tailLen)
                    raf.readFully(buf)
                    buf
                } else {
                    ByteArray(0)
                }

                var detected = analyzeHeadAndTail(head, tail)
                if (!detected.isMotionPhoto) {
                    val xmp = ImageProcessor.extractXmpXml(head)
                    val declaredLen = parseDeclaredVideoLen(xmp)
                    if (declaredLen in 256L until len) {
                        val hdr = ByteArray(8)
                        raf.seek(len - declaredLen)
                        raf.readFully(hdr)
                        if (hdr[4] == 0x66.toByte() && hdr[5] == 0x74.toByte() &&
                            hdr[6] == 0x79.toByte() && hdr[7] == 0x70.toByte()
                        ) {
                            detected = classifyFromXmpAndExif(xmp, head, tail)
                        }
                    }
                }
                detected
            }
        } catch (_: Exception) {
            MotionProbeResult(false, DetectedOemBrand.NONE)
        }
    }

    private fun probeFromStreamFallback(context: Context, uri: Uri): MotionProbeResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val head = ByteArray(65536)
                var read = 0
                while (read < head.size) {
                    val r = input.read(head, read, head.size - read)
                    if (r <= 0) break
                    read += r
                }
                val actualHead = if (read == head.size) head else head.copyOf(read)
                analyzeHeadAndTail(actualHead, ByteArray(0))
            } ?: MotionProbeResult(false, DetectedOemBrand.NONE)
        } catch (_: Exception) {
            MotionProbeResult(false, DetectedOemBrand.NONE)
        }
    }

    private fun checkFtypAtChannelOffset(
        channel: java.nio.channels.FileChannel,
        offset: Long,
        limit: Long
    ): Boolean {
        if (offset < 0L || offset + 12L > limit) return false
        return try {
            val buf = ByteBuffer.allocate(8)
            channel.position(offset)
            while (buf.hasRemaining()) {
                if (channel.read(buf) <= 0) break
            }
            val arr = buf.array()
            arr[4] == 0x66.toByte() && arr[5] == 0x74.toByte() &&
                arr[6] == 0x79.toByte() && arr[7] == 0x70.toByte()
        } catch (_: Exception) {
            false
        }
    }

    private fun analyzeHeadAndTail(head: ByteArray, tail: ByteArray): MotionProbeResult {
        if (head.size < 16) return MotionProbeResult(false, DetectedOemBrand.NONE)

        val xmp = ImageProcessor.extractXmpXml(head)
        val ptsUs = extractPresentationTimestampUs(xmp)

        // 1. Samsung SEF tail check ("SEFT" at EOF + MotionPhoto_Data)
        if (tail.size >= 28 && ZlcBinaryUtils.arrayEquals(tail, tail.size - 4, SEFT_MAGIC)) {
            val sefDataMarker = "MotionPhoto_Data".toByteArray(Charsets.US_ASCII)
            if (ImageProcessor.findSubarray(tail, sefDataMarker) != -1 ||
                xmp.contains("MotionPhoto") ||
                tail.size >= 32
            ) {
                return MotionProbeResult(true, DetectedOemBrand.SAMSUNG, ptsUs)
            }
        }

        // 2. vivo / iQOO cameralbum! / vivoMediaExtInfo trailer or VCamera XMP
        if (tail.isNotEmpty() && (
                ImageProcessor.findSubarray(tail, VIVO_CAMERALBUM_MARKER) != -1 ||
                    ImageProcessor.findSubarray(tail, VIVO_EXT_MARKER) != -1 ||
                    ImageProcessor.findSubarray(tail, VIVO_MAGIC_BYTES) != -1
                )
        ) {
            return MotionProbeResult(true, DetectedOemBrand.VIVO, ptsUs)
        }
        if (xmp.contains("VCamera:") || xmp.contains("ns.vivo.com/photos")) {
            return MotionProbeResult(true, DetectedOemBrand.VIVO, ptsUs)
        }

        // 3. Huawei / Honor LIVE_ trailer or HwCamera XMP
        if (tail.size >= 16) {
            val tailWindowStart = (tail.size - 160).coerceAtLeast(0)
            val tailSlice = tail.copyOfRange(tailWindowStart, tail.size)
            if (ImageProcessor.findSubarray(tailSlice, HONOR_LIVE_MARKER) != -1) {
                return MotionProbeResult(true, DetectedOemBrand.HONOR_HUAWEI, ptsUs)
            }
        }
        if (xmp.contains("HwCamera:") || xmp.contains("MagicPhoto") || xmp.contains("honor", ignoreCase = true)) {
            if (xmp.contains("MotionPhoto=\"1\"") || xmp.contains("MicroVideo=\"1\"") || xmp.contains("HwCamera:")) {
                return MotionProbeResult(true, DetectedOemBrand.HONOR_HUAWEI, ptsUs)
            }
        }

        // 4. OPPO / OnePlus / realme OpCamera XMP
        if (xmp.contains("OpCamera:") || xmp.contains("ns.oplus.com/photos") || xmp.contains("OLivePhoto")) {
            return MotionProbeResult(true, DetectedOemBrand.OPPO, ptsUs)
        }

        // 5. Standard GCamera / MicroVideo / Camera XMP or Container:Directory MotionPhoto item
        val hasXmpMotion = xmp.contains("GCamera:MotionPhoto=\"1\"") ||
            xmp.contains("GCamera:MicroVideo=\"1\"") ||
            xmp.contains("Camera:MotionPhoto=\"1\"") ||
            xmp.contains("Item:Semantic=\"MotionPhoto\"") ||
            parseDeclaredVideoLen(xmp) > 256L

        if (hasXmpMotion) {
            return classifyFromXmpAndExif(xmp, head, tail)
        }

        // 6. OPPO oplustag fallback + trailing MP4 box verification (in case XMP was customized/stripped)
        val hasOplusTag = ImageProcessor.findSubarray(head, OPLUS_TAG_BYTES) != -1
        if (tail.size >= 128 && hasMp4MoovBoxInTail(tail)) {
            val exifBrand = detectExifMakeBrand(head)
            val brand = when {
                hasOplusTag -> DetectedOemBrand.OPPO
                exifBrand != DetectedOemBrand.NONE -> exifBrand
                else -> DetectedOemBrand.GENERIC
            }
            return MotionProbeResult(true, brand, ptsUs)
        }

        return MotionProbeResult(false, DetectedOemBrand.NONE)
    }

    private fun classifyFromXmpAndExif(xmp: String, head: ByteArray, tail: ByteArray): MotionProbeResult {
        val ptsUs = extractPresentationTimestampUs(xmp)
        val brand = when {
            xmp.contains("OpCamera:") || xmp.contains("oplus") || xmp.contains("OLivePhoto") -> DetectedOemBrand.OPPO
            xmp.contains("VCamera:") || xmp.contains("vivo") -> DetectedOemBrand.VIVO
            xmp.contains("MiCamera:") || xmp.contains("xiaomi", ignoreCase = true) -> DetectedOemBrand.XIAOMI
            xmp.contains("HwCamera:") -> DetectedOemBrand.HONOR_HUAWEI
            tail.size >= 28 && ZlcBinaryUtils.arrayEquals(tail, tail.size - 4, SEFT_MAGIC) -> DetectedOemBrand.SAMSUNG
            else -> {
                val exifBrand = detectExifMakeBrand(head)
                if (exifBrand != DetectedOemBrand.NONE) exifBrand else DetectedOemBrand.GOOGLE
            }
        }
        return MotionProbeResult(true, brand, ptsUs)
    }

    private fun detectExifMakeBrand(head: ByteArray): DetectedOemBrand {
        val makeStr = try {
            val exif = ExifInterface(ByteArrayInputStream(head))
            val make = exif.getAttribute(ExifInterface.TAG_MAKE).orEmpty()
            val model = exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty()
            val soft = exif.getAttribute(ExifInterface.TAG_SOFTWARE).orEmpty()
            "$make $model $soft".lowercase()
        } catch (_: Exception) {
            ""
        }
        if (makeStr.isBlank()) return DetectedOemBrand.NONE
        return when {
            makeStr.contains("oppo") || makeStr.contains("oneplus") || makeStr.contains("realme") -> DetectedOemBrand.OPPO
            makeStr.contains("xiaomi") || makeStr.contains("redmi") || makeStr.contains("poco") -> DetectedOemBrand.XIAOMI
            makeStr.contains("vivo") || makeStr.contains("iqoo") -> DetectedOemBrand.VIVO
            makeStr.contains("samsung") -> DetectedOemBrand.SAMSUNG
            makeStr.contains("huawei") || makeStr.contains("honor") -> DetectedOemBrand.HONOR_HUAWEI
            makeStr.contains("meizu") -> DetectedOemBrand.MEIZU
            makeStr.contains("google") || makeStr.contains("pixel") -> DetectedOemBrand.GOOGLE
            else -> DetectedOemBrand.NONE
        }
    }

    private fun hasMp4MoovBoxInTail(tail: ByteArray): Boolean {
        var idx = 4
        while (idx + 8 < tail.size) {
            if (tail[idx] == MOOV_BYTES[0] &&
                tail[idx + 1] == MOOV_BYTES[1] &&
                tail[idx + 2] == MOOV_BYTES[2] &&
                tail[idx + 3] == MOOV_BYTES[3]
            ) {
                val boxSize = ZlcBinaryUtils.readU32BE(tail, idx - 4)
                if (boxSize in 64L..(1024L * 1024L)) {
                    val searchEnd = (idx + 256).coerceAtMost(tail.size)
                    val window = tail.copyOfRange(idx + 4, searchEnd)
                    if (ImageProcessor.findSubarray(window, MVHD_BYTES) != -1 ||
                        ImageProcessor.findSubarray(window, TRAK_BYTES) != -1
                    ) {
                        return true
                    }
                }
            }
            idx++
        }
        return false
    }

    fun extractPresentationTimestampUs(xmp: String): Long {
        if (xmp.isBlank()) return 0L
        val match = Regex("""GCamera:MotionPhotoPresentationTimestampUs\s*=\s*"(-?\d+)"""")
            .find(xmp)
            ?: Regex("""OpCamera:MotionPhotoPrimaryPresentationTimestampUs\s*=\s*"(-?\d+)"""")
                .find(xmp)
            ?: Regex("""GCamera:MicroVideoPresentationTimestampUs\s*=\s*"(-?\d+)"""")
                .find(xmp)
        val v = match?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        return if (v > 0L) v else 0L
    }

    private fun parseDeclaredVideoLen(xmp: String): Long {
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

    /**
     * Losslessly extracts the clean primary cover JPEG (retaining EXIF/GPS/ICC while removing old OEM XMP/MPF/video trailers).
     * Falls back to ImageProcessor.ensureJpegCover if the source is HEIC/PNG/WEBP or non-standard JPEG.
     */
    fun extractCleanCoverJpeg(context: Context, sourceFile: File, targetCoverFile: File): Boolean {
        return try {
            val rawBytes = sourceFile.readBytes()
            if (rawBytes.size >= 4 && rawBytes[0] == 0xFF.toByte() && rawBytes[1] == 0xD8.toByte()) {
                val cleanBytes = try {
                    val parsed = ZlcJpegUtil.parseJpeg(rawBytes, 0)
                    val filteredSegs = parsed.segments.filter { s ->
                        s.marker != 0xDA &&
                            !(s.marker == 0xE1 && ZlcBinaryUtils.arrayEquals(s.payload, 0, ZlcJpegUtil.XMP_HEADER)) &&
                            !(s.marker == 0xE2 && ZlcBinaryUtils.arrayEquals(s.payload, 0, ZlcJpegUtil.MPF_HEADER))
                    }
                    ZlcJpegUtil.rebuildJpeg(filteredSegs, parsed.scanAndEoi)
                } catch (_: Exception) {
                    var cutEnd = rawBytes.size
                    for (i in 4 until rawBytes.size - 8) {
                        if (rawBytes[i] == 0x66.toByte() &&
                            rawBytes[i + 1] == 0x74.toByte() &&
                            rawBytes[i + 2] == 0x79.toByte() &&
                            rawBytes[i + 3] == 0x70.toByte()
                        ) {
                            val boxStart = i - 4
                            if (boxStart > 256) {
                                cutEnd = boxStart
                                break
                            }
                        }
                    }
                    rawBytes.copyOfRange(0, cutEnd)
                }
                if (cleanBytes.size > 64) {
                    targetCoverFile.writeBytes(cleanBytes)
                    return true
                }
            }
            ImageProcessor.ensureJpegCover(context, null, sourceFile, targetCoverFile)
        } catch (_: Exception) {
            ImageProcessor.ensureJpegCover(context, null, sourceFile, targetCoverFile)
        }
    }
}
