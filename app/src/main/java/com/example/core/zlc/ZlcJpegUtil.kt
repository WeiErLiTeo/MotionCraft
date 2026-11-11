package com.example.core.zlc

import java.io.ByteArrayOutputStream

/**
 * JPEG 段级无损读写工具（直接迁移自 ZLC JpegUtil）。
 */
object ZlcJpegUtil {

    val XMP_HEADER: ByteArray = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.ISO_8859_1)
    val EXIF_HEADER: ByteArray = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1)
    val MPF_HEADER: ByteArray = "MPF\u0000".toByteArray(Charsets.ISO_8859_1)

    private val STANDALONE_MARKERS = setOf(
        0xD8, 0xD9, 0x01,
        0xD0, 0xD1, 0xD2, 0xD3, 0xD4, 0xD5, 0xD6, 0xD7,
    )

    data class Segment(
        val marker: Int,
        val payload: ByteArray,
        val offset: Int,
        val rawLength: Int,
    )

    data class ParsedJpeg(
        val segments: List<Segment>,
        val scanAndEoi: ByteArray,
        val eoiEnd: Int,
    )

    fun parseJpeg(data: ByteArray, start: Int = 0): ParsedJpeg {
        require(start + 2 <= data.size && data[start] == 0xFF.toByte() && data[start + 1] == 0xD8.toByte()) {
            "Data does not start with JPEG SOI (FFD8)"
        }
        val segments = mutableListOf<Segment>()
        var pos = start + 2
        val n = data.size

        while (pos < n) {
            if (data[pos] != 0xFF.toByte()) {
                throw IllegalArgumentException("Invalid JPEG marker at $pos: 0x${(data[pos].toInt() and 0xFF).toString(16)}")
            }
            while (pos < n && data[pos] == 0xFF.toByte()) pos++
            if (pos >= n) break
            val marker = data[pos].toInt() and 0xFF
            pos++

            if (marker == 0xD9) {
                return ParsedJpeg(segments, byteArrayOf(0xFF.toByte(), 0xD9.toByte()), pos)
            }
            if (marker in STANDALONE_MARKERS) continue

            if (pos + 2 > n) throw IllegalArgumentException("Truncated segment length at $pos")
            val segLen = ZlcBinaryUtils.readU16BE(data, pos)
            if (segLen < 2 || pos + segLen > n) {
                throw IllegalArgumentException("Invalid segment length $segLen at $pos")
            }
            val payload = data.copyOfRange(pos + 2, pos + segLen)
            val segStart = pos - 2
            segments.add(Segment(marker, payload, segStart, segLen + 2))
            pos += segLen

            if (marker == 0xDA) {
                val scanStart = segStart
                var scanPos = pos
                while (scanPos < n - 1) {
                    if (data[scanPos] == 0xFF.toByte()) {
                        val nxt = data[scanPos + 1].toInt() and 0xFF
                        if (nxt == 0xD9) {
                            val eoiEnd = scanPos + 2
                            return ParsedJpeg(segments, data.copyOfRange(scanStart, eoiEnd), eoiEnd)
                        }
                        if (nxt == 0x00 || nxt in 0xD0..0xD7 || nxt == 0xFF) {
                            scanPos += if (nxt == 0xFF) 1 else 2
                            continue
                        }
                        if (scanPos + 4 <= n) {
                            val innerLen = ZlcBinaryUtils.readU16BE(data, scanPos + 2)
                            scanPos += 2 + innerLen
                            continue
                        }
                    }
                    scanPos++
                }
                throw IllegalArgumentException("JPEG EOI (FFD9) not found after SOS")
            }
        }
        throw IllegalArgumentException("Unexpected end of JPEG before SOS/EOI")
    }

    fun extractXmpXml(segments: List<Segment>): String? {
        for (s in segments) {
            if (s.marker == 0xE1 && ZlcBinaryUtils.arrayEquals(s.payload, 0, XMP_HEADER)) {
                var raw = s.payload.copyOfRange(XMP_HEADER.size, s.payload.size)
                var end = raw.size
                while (end > 0 && raw[end - 1] == 0.toByte()) end--
                if (end < raw.size) raw = raw.copyOfRange(0, end)
                return String(raw, Charsets.UTF_8)
            }
        }
        return null
    }

    fun buildXmpApp1Payload(xmpXml: String): ByteArray =
        ZlcBinaryUtils.concat(XMP_HEADER, xmpXml.toByteArray(Charsets.UTF_8))

    fun rebuildJpeg(segments: List<Segment>, scanAndEoi: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(scanAndEoi.size + 8192)
        out.write(0xFF)
        out.write(0xD8)
        for (s in segments) {
            if (s.marker == 0xDA) continue
            out.write(0xFF)
            out.write(s.marker)
            val len = s.payload.size + 2
            out.write((len ushr 8) and 0xFF)
            out.write(len and 0xFF)
            out.write(s.payload)
        }
        out.write(scanAndEoi)
        return out.toByteArray()
    }

    fun replaceOrInsertXmp(jpegBytes: ByteArray, newXmpXml: String): ByteArray {
        val parsed = parseJpeg(jpegBytes, 0)
        val newPayload = buildXmpApp1Payload(newXmpXml)
        val newSegs = mutableListOf<Segment>()
        var replaced = false
        for (s in parsed.segments) {
            if (s.marker == 0xDA) continue
            if (s.marker == 0xE1 && ZlcBinaryUtils.arrayEquals(s.payload, 0, XMP_HEADER)) {
                if (!replaced) {
                    newSegs.add(Segment(0xE1, newPayload, 0, newPayload.size + 4))
                    replaced = true
                }
            } else {
                newSegs.add(s)
            }
        }
        if (!replaced) {
            var insertIdx = 0
            for ((i, s) in newSegs.withIndex()) {
                if (s.marker == 0xE0 || (s.marker == 0xE1 && ZlcBinaryUtils.arrayEquals(s.payload, 0, EXIF_HEADER))) {
                    insertIdx = i + 1
                }
            }
            newSegs.add(insertIdx, Segment(0xE1, newPayload, 0, newPayload.size + 4))
        }
        return rebuildJpeg(newSegs, parsed.scanAndEoi)
    }

    fun stripMpfApp2(jpegBytes: ByteArray): ByteArray {
        val parsed = parseJpeg(jpegBytes, 0)
        val newSegs = parsed.segments.filter { s ->
            s.marker != 0xDA && !(s.marker == 0xE2 && ZlcBinaryUtils.arrayEquals(s.payload, 0, MPF_HEADER))
        }
        return rebuildJpeg(newSegs, parsed.scanAndEoi)
    }

    fun stripXmpApp1(jpegBytes: ByteArray): ByteArray {
        val parsed = parseJpeg(jpegBytes, 0)
        val newSegs = parsed.segments.filter { s ->
            s.marker != 0xDA && !(s.marker == 0xE1 && ZlcBinaryUtils.arrayEquals(s.payload, 0, XMP_HEADER))
        }
        return rebuildJpeg(newSegs, parsed.scanAndEoi)
    }

    /**
     * 读取 JPEG 的 (width, height)，通过 SOF0..SOF15 段解析（1:1 对应 ZLC JpegUtil.getDimensions）。
     */
    fun getDimensions(jpeg: ByteArray): Pair<Int, Int> {
        val parsed = parseJpeg(jpeg, 0)
        val sofMarkers = setOf(
            0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
            0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF
        )
        for (seg in parsed.segments) {
            if (seg.marker in sofMarkers && seg.payload.size >= 5) {
                val h = ZlcBinaryUtils.readU16BE(seg.payload, 1)
                val w = ZlcBinaryUtils.readU16BE(seg.payload, 3)
                return w to h
            }
        }
        return 0 to 0
    }

    private fun buildMpfPayload(
        primarySize: Int,
        gainmapSize: Int,
        gainmapOffsetFromMpfEndian: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream(96)
        out.write(MPF_HEADER)
        out.write("II".toByteArray(Charsets.ISO_8859_1))
        out.write(ZlcBinaryUtils.writeU16LE(0x002A))
        out.write(ZlcBinaryUtils.writeU32LE(8))
        out.write(ZlcBinaryUtils.writeU16LE(3))
        out.write(ZlcBinaryUtils.writeU16LE(0xB000))
        out.write(ZlcBinaryUtils.writeU16LE(7))
        out.write(ZlcBinaryUtils.writeU32LE(4))
        out.write("0100".toByteArray(Charsets.ISO_8859_1))
        out.write(ZlcBinaryUtils.writeU16LE(0xB001))
        out.write(ZlcBinaryUtils.writeU16LE(4))
        out.write(ZlcBinaryUtils.writeU32LE(2))
        out.write(ZlcBinaryUtils.writeU32LE(2))
        out.write(ZlcBinaryUtils.writeU16LE(0xB002))
        out.write(ZlcBinaryUtils.writeU16LE(7))
        out.write(ZlcBinaryUtils.writeU32LE(32))
        out.write(ZlcBinaryUtils.writeU32LE(50))
        out.write(ZlcBinaryUtils.writeU32LE(0))
        out.write(ZlcBinaryUtils.writeU32LE(0x00030000L))
        out.write(ZlcBinaryUtils.writeU32LE(primarySize.toLong() and 0xFFFFFFFFL))
        out.write(ZlcBinaryUtils.writeU32LE(0))
        out.write(ZlcBinaryUtils.writeU16LE(0))
        out.write(ZlcBinaryUtils.writeU16LE(0))
        out.write(ZlcBinaryUtils.writeU32LE(0x00000000L))
        out.write(ZlcBinaryUtils.writeU32LE(gainmapSize.toLong() and 0xFFFFFFFFL))
        out.write(ZlcBinaryUtils.writeU32LE(gainmapOffsetFromMpfEndian.toLong() and 0xFFFFFFFFL))
        out.write(ZlcBinaryUtils.writeU16LE(0))
        out.write(ZlcBinaryUtils.writeU16LE(0))
        return out.toByteArray()
    }

    fun updateMpfWithGainmap(primaryJpeg: ByteArray, gainmapJpeg: ByteArray?): ByteArray {
        if (gainmapJpeg == null) return stripMpfApp2(primaryJpeg)

        val parsed = parseJpeg(primaryJpeg, 0)
        val segsNoMpf = parsed.segments.filter { s ->
            s.marker != 0xDA && !(s.marker == 0xE2 && ZlcBinaryUtils.arrayEquals(s.payload, 0, MPF_HEADER))
        }.toMutableList()

        val dummyPayload = buildMpfPayload(0, gainmapJpeg.size, 0)
        var insertIdx = 0
        for ((i, s) in segsNoMpf.withIndex()) {
            if (s.marker in listOf(0xE0, 0xE1, 0xE2)) insertIdx = i + 1
        }
        segsNoMpf.add(insertIdx, Segment(0xE2, dummyPayload, 0, dummyPayload.size + 4))

        val tempPrimary = rebuildJpeg(segsNoMpf, parsed.scanAndEoi)
        val primarySize = tempPrimary.size

        var mpfSegOffset: Int? = null
        var pos = 2
        for (s in segsNoMpf) {
            if (s.marker == 0xE2 && ZlcBinaryUtils.arrayEquals(s.payload, 0, MPF_HEADER)) {
                mpfSegOffset = pos
                break
            }
            pos += 4 + s.payload.size
        }
        checkNotNull(mpfSegOffset)
        val mpfEndianPos = mpfSegOffset + 4 + 4
        val gainmapOffset = primarySize - mpfEndianPos

        val realPayload = buildMpfPayload(primarySize, gainmapJpeg.size, gainmapOffset)
        segsNoMpf[insertIdx] = Segment(0xE2, realPayload, 0, realPayload.size + 4)
        val finalPrimary = rebuildJpeg(segsNoMpf, parsed.scanAndEoi)
        return ZlcBinaryUtils.concat(finalPrimary, gainmapJpeg)
    }
}
