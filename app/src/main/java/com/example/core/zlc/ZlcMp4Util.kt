package com.example.core.zlc

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan2
import kotlin.math.round

/**
 * MP4/MOV ISOBMFF Box 级无损解析与规范化工具（1:1 对应 ZLC Mp4Util + VideoTrackSanitizer，
 * 并增强 QuickTime 音轨 v1/v2 `wave`/`lpcm`/`sowt` 无损转封装为标准 ISO MP4 + AAC）。
 */
object ZlcMp4Util {

    // 1:1 对应 ZLC Mp4Util.VIVO_UUID ("vivoMediaExtInfo" 16 字节)
    val VIVO_UUID: ByteArray = "vivoMediaExtInfo".toByteArray(Charsets.US_ASCII)

    private val AV_HANDLERS = setOf("vide", "soun")
    private val COMMON_VIDEO_FOURCCS = setOf("avc1", "avc3", "hev1", "hvc1")
    private const val AAC_FOURCC = "mp4a"
    private const val VISUAL_ENTRY_CHILD_OFFSET = 86

    data class Box(
        val type: String,
        val start: Int,
        val size: Int,
        val headerSize: Int,
    ) {
        val payloadStart: Int get() = start + headerSize
        val end: Int get() = start + size
    }

    data class VideoCompat(
        val videoCodec: String,
        val audioCodec: String,
        val reasons: List<String>
    ) {
        val needsReencode: Boolean get() = reasons.isNotEmpty()
    }

    data class PcmAudioData(
        val sampleRate: Int,
        val channelCount: Int,
        val pcm16Le: ByteArray
    )

    fun hasFtyp(data: ByteArray): Boolean {
        if (data.size < 12) return false
        return data[4] == 0x66.toByte() &&
            data[5] == 0x74.toByte() &&
            data[6] == 0x79.toByte() &&
            data[7] == 0x70.toByte()
    }

    /**
     * 检查 ftyp 是否包含 QuickTime ("qt  ") 品牌。
     */
    fun isQuickTimeBrand(data: ByteArray): Boolean {
        if (!hasFtyp(data) || data.size < 16) return false
        val ftypSize = ZlcBinaryUtils.readU32BE(data, 0).toInt()
        if (ftypSize < 16 || ftypSize > data.size) return false
        val qt = byteArrayOf(0x71, 0x74, 0x20, 0x20) // "qt  "
        var pos = 8
        while (pos + 4 <= ftypSize) {
            if (pos != 12 && ZlcBinaryUtils.arrayEquals(data, pos, qt)) {
                return true
            }
            pos += 4
        }
        return false
    }

    /**
     * 检测 MP4/MOV 是否包含 QuickTime 特有结构（包括被早期版本把 ftyp 改成 isom 但内部仍是 QuickTime stsd/trak 的文件）：
     * 1) ftyp 含 "qt  "
     * 2) 含非音视频轨（mebx / mett / tmcd 等）
     * 3) trak 含 QuickTime 特有 tapt / tref 盒
     * 4) 音频 stsd 条目为 QuickTime SoundSampleDescription v1/v2（version > 0 或含 wave 子盒）或非 mp4a（如 lpcm/sowt/twos）
     */
    fun hasQuickTimeSampleEntriesOrTracks(data: ByteArray): Boolean {
        if (!hasFtyp(data)) return false
        if (isQuickTimeBrand(data)) return true
        if (nonAvHandlers(data).isNotEmpty()) return true

        val moov = findTopLevelBox(data, "moov") ?: return false
        for (b in iterBoxes(data, moov.payloadStart, moov.end)) {
            if (b.type != "trak") continue
            val trakChildren = iterBoxes(data, b.payloadStart, b.end)
            if (trakChildren.any { it.type == "tapt" || it.type == "tref" }) return true

            val trakBoxes = walkInto(data, b.start, b.size, b.headerSize, setOf("mdia", "minf", "stbl")).toList()
            val hdlr = trakBoxes.firstOrNull { it.type == "hdlr" } ?: continue
            if (hdlr.payloadStart + 12 > data.size) continue
            val handler = String(data, hdlr.payloadStart + 8, 4, Charsets.ISO_8859_1)
            val stsd = trakBoxes.firstOrNull { it.type == "stsd" } ?: continue
            val entryOffset = stsd.payloadStart + 8
            if (entryOffset + 18 > stsd.end || entryOffset + 18 > data.size) continue
            val entrySize = ZlcBinaryUtils.readU32BE(data, entryOffset).toInt()
            val fourcc = String(data, entryOffset + 4, 4, Charsets.ISO_8859_1)

            if (handler == "soun") {
                if (fourcc != AAC_FOURCC) return true
                // 标准 ISO AudioSampleEntry 在 entryOffset + 16 处为 reserved(0)，
                // 而 QuickTime SoundSampleDescription 在此处为 version (1 或 2)
                val qtSoundVersion = ZlcBinaryUtils.readU16BE(data, entryOffset + 16)
                if (qtSoundVersion != 0) return true
                val entryBox = Box(fourcc, entryOffset, minOf(entrySize, stsd.end - entryOffset), 8)
                if (childBox(data, entryBox, "wave", 36) != null) return true
            } else if (handler == "vide") {
                val entryBox = Box(fourcc, entryOffset, minOf(entrySize, stsd.end - entryOffset), 8)
                if (childBox(data, entryBox, "gama", VISUAL_ENTRY_CHILD_OFFSET) != null) return true
            }
        }
        return false
    }

    /**
     * 如果文件内部含有 QuickTime SoundSampleDescription v1/v2 等结构，但 ftyp 曾被改写成了 isom，
     * 将 ftyp 的 major_brand 恢复为 "qt  "，以便 MediaExtractor / Media3 AtomParsers 正确按 QuickTime 模式解析 stsd。
     */
    fun restoreQuickTimeFtypIfNeeded(data: ByteArray): ByteArray {
        if (!hasFtyp(data) || data.size < 16) return data
        if (isQuickTimeBrand(data)) return data
        if (!hasQuickTimeSampleEntriesOrTracks(data)) return data
        val ftypSize = ZlcBinaryUtils.readU32BE(data, 0).toInt()
        if (ftypSize < 16 || ftypSize > data.size) return data
        val buf = data.copyOf()
        // 将 major_brand 恢复为 "qt  "
        buf[8] = 0x71
        buf[9] = 0x74
        buf[10] = 0x20
        buf[11] = 0x20
        if (ftypSize >= 20) {
            buf[16] = 0x71
            buf[17] = 0x74
            buf[18] = 0x20
            buf[19] = 0x20
        }
        return buf
    }

    fun iterBoxes(data: ByteArray, start: Int = 0, end: Int = data.size): List<Box> {
        val out = mutableListOf<Box>()
        var pos = start
        while (pos + 8 <= end) {
            val size32 = ZlcBinaryUtils.readU32BE(data, pos)
            var validType = true
            for (i in pos + 4 until pos + 8) {
                val c = data[i].toInt() and 0xFF
                if (c < 0x20 || c > 0x7E) {
                    validType = false
                    break
                }
            }
            if (!validType) break

            val btype = ZlcBinaryUtils.ascii(data, pos + 4, 4)
            var hdr = 8
            val size: Long = when (size32) {
                1L -> {
                    if (pos + 16 > end) break
                    hdr = 16
                    ZlcBinaryUtils.readU64BE(data, pos + 8)
                }
                0L -> (end - pos).toLong()
                else -> size32
            }
            if (size < hdr || pos + size > end) break
            out.add(Box(btype, pos, size.toInt(), hdr))
            pos += size.toInt()
        }
        return out
    }

    private fun walkInto(
        data: ByteArray,
        offset: Int,
        size: Int,
        headerLen: Int,
        pathTypes: Set<String>
    ): Sequence<Box> = sequence {
        for (b in iterBoxes(data, offset + headerLen, offset + size)) {
            yield(b)
            if (b.type in pathTypes) {
                yieldAll(walkInto(data, b.start, b.size, b.headerSize, pathTypes))
            }
        }
    }

    fun findTopLevelBox(data: ByteArray, boxType: String): Box? =
        iterBoxes(data).firstOrNull { it.type == boxType }

    fun findPureMp4End(data: ByteArray, start: Int = 0): Int {
        var pos = start
        val n = data.size
        var seenFtyp = false
        var seenMoovOrMdat = false
        val validBoxes = setOf("ftyp", "moov", "mdat", "free", "wide", "skip", "beam", "edts", "meta", "pdin")
        while (pos + 8 <= n) {
            val size32 = ZlcBinaryUtils.readU32BE(data, pos)
            val btype = ZlcBinaryUtils.ascii(data, pos + 4, 4)
            if (btype == "uuid") break
            if (btype !in validBoxes) break
            val size = when (size32) {
                1L -> {
                    if (pos + 16 > n) break
                    ZlcBinaryUtils.readU64BE(data, pos + 8)
                }
                0L -> break
                else -> size32
            }
            if (size < 8 || pos + size > n) break
            if (btype == "ftyp") seenFtyp = true
            if (btype == "moov" || btype == "mdat") seenMoovOrMdat = true
            pos += size.toInt()
        }
        return if (seenFtyp && seenMoovOrMdat) pos else start
    }

    fun trackHandlers(data: ByteArray): List<String> {
        val boxes = iterBoxes(data, 0, data.size)
        val moov = boxes.firstOrNull { it.type == "moov" } ?: return emptyList()
        val out = ArrayList<String>()
        for (b in iterBoxes(data, moov.payloadStart, moov.end)) {
            if (b.type != "trak") continue
            val trak = walkInto(data, b.start, b.size, b.headerSize, setOf("mdia")).toList()
            val hdlr = trak.firstOrNull { it.type == "hdlr" } ?: continue
            val body = hdlr.payloadStart
            if (body + 12 > data.size) continue
            out.add(String(data, body + 8, 4, Charsets.ISO_8859_1))
        }
        return out
    }

    fun nonAvHandlers(data: ByteArray): List<String> =
        trackHandlers(data).filter { it !in AV_HANDLERS }

    fun inspectVideo(mp4Bytes: ByteArray): MutableMap<String, Any?> {
        val info = mutableMapOf<String, Any?>(
            "codec" to null,
            "width" to null,
            "height" to null,
            "duration_us" to null,
            "fps" to null,
            "frame_count" to null,
            "rotation" to 0,
        )
        val moov = findTopLevelBox(mp4Bytes, "moov") ?: return info

        for (child in iterBoxes(mp4Bytes, moov.payloadStart, moov.end)) {
            if (child.type == "mvhd") {
                val p = child.payloadStart
                val ver = mp4Bytes[p].toInt() and 0xFF
                val ts: Long
                val dur: Long
                if (ver == 0) {
                    ts = ZlcBinaryUtils.readU32BE(mp4Bytes, p + 12)
                    dur = ZlcBinaryUtils.readU32BE(mp4Bytes, p + 16)
                } else {
                    ts = ZlcBinaryUtils.readU32BE(mp4Bytes, p + 20)
                    dur = ZlcBinaryUtils.readU64BE(mp4Bytes, p + 24)
                }
                if (ts > 0) info["duration_us"] = (dur * 1_000_000L) / ts
            } else if (child.type == "trak") {
                parseTrak(mp4Bytes, child.payloadStart, child.end, info)
            }
        }
        return info
    }

    private fun parseTrak(data: ByteArray, start: Int, end: Int, info: MutableMap<String, Any?>) {
        var isVideo = false
        var mdiaRange: Pair<Int, Int>? = null
        var tkhdRange: Pair<Int, Int>? = null

        for (b in iterBoxes(data, start, end)) {
            when (b.type) {
                "tkhd" -> tkhdRange = b.payloadStart to b.end
                "mdia" -> mdiaRange = b.payloadStart to b.end
            }
        }
        val (mStart, mEnd) = mdiaRange ?: return

        var mdhdTimescale = 0L
        var mdhdDur = 0L
        var minfRange: Pair<Int, Int>? = null
        for (b in iterBoxes(data, mStart, mEnd)) {
            when (b.type) {
                "hdlr" -> {
                    if (b.payloadStart + 12 <= b.end) {
                        val handler = ZlcBinaryUtils.ascii(data, b.payloadStart + 8, 4)
                        if (handler == "vide") isVideo = true
                    }
                }
                "mdhd" -> {
                    val p = b.payloadStart
                    val ver = data[p].toInt() and 0xFF
                    if (ver == 0) {
                        mdhdTimescale = ZlcBinaryUtils.readU32BE(data, p + 12)
                        mdhdDur = ZlcBinaryUtils.readU32BE(data, p + 16)
                    } else {
                        mdhdTimescale = ZlcBinaryUtils.readU32BE(data, p + 20)
                        mdhdDur = ZlcBinaryUtils.readU64BE(data, p + 24)
                    }
                }
                "minf" -> minfRange = b.payloadStart to b.end
            }
        }
        if (!isVideo) return

        // 1:1 对齐 ZLC Mp4Util.getTrackInfo：从 tkhd 末尾读取宽高与旋转矩阵
        tkhdRange?.let { (tp, tEnd) ->
            if (tEnd - tp >= 8) {
                val wFixed = ZlcBinaryUtils.readU32BE(data, tEnd - 8)
                val hFixed = ZlcBinaryUtils.readU32BE(data, tEnd - 4)
                info["width"] = (wFixed ushr 16).toInt()
                info["height"] = (hFixed ushr 16).toInt()
            }
            val matOff = tEnd - 8 - 36
            if (matOff >= tp) {
                val a = ZlcBinaryUtils.readI32BE(data, matOff) / 65536.0
                val b = ZlcBinaryUtils.readI32BE(data, matOff + 4) / 65536.0
                val deg = ((round(atan2(b, a) * (180.0 / Math.PI)).toInt() % 360) + 360) % 360
                info["rotation"] = deg
            }
        }

        val (minfStart, minfEnd) = minfRange ?: return
        var sttsSamples = 0L
        var stszSamples = 0L
        for (b in iterBoxes(data, minfStart, minfEnd)) {
            if (b.type == "stbl") {
                for (sb in iterBoxes(data, b.payloadStart, b.end)) {
                    when (sb.type) {
                        "stsd" -> {
                            val sp = sb.payloadStart
                            if (sp + 16 <= sb.end) {
                                val codec = ZlcBinaryUtils.ascii(data, sp + 12, 4).trim()
                                info["codec"] = codec
                            }
                        }
                        "stts" -> {
                            val sp = sb.payloadStart
                            if (sp + 8 <= sb.end) {
                                val entryCount = ZlcBinaryUtils.readU32BE(data, sp + 4).toInt()
                                var p = sp + 8
                                for (i in 0 until entryCount) {
                                    if (p + 8 > sb.end) break
                                    sttsSamples += ZlcBinaryUtils.readU32BE(data, p)
                                    p += 8
                                }
                            }
                        }
                        "stsz" -> {
                            val sp = sb.payloadStart
                            if (sp + 12 <= sb.end) {
                                stszSamples = ZlcBinaryUtils.readU32BE(data, sp + 8)
                            }
                        }
                    }
                }
            }
        }
        val totalFrames = if (sttsSamples > 0L) sttsSamples else stszSamples
        if (totalFrames > 0L) {
            info["frame_count"] = totalFrames
        }
        if (mdhdTimescale > 0 && mdhdDur > 0) {
            val durS = mdhdDur.toDouble() / mdhdTimescale.toDouble()
            if (durS > 0 && totalFrames > 0L) {
                info["fps"] = totalFrames.toDouble() / durS
            }
            info["duration_us"] = (mdhdDur * 1_000_000L) / mdhdTimescale
        }
    }

    /**
     * 修复 moov 内所有 stco/co64 条目：位于 insertAt 之后的偏移整体前移/后移 delta（与 ZLC Mp4Util.fixChunkOffsets 一致）。
     */
    private fun fixChunkOffsets(
        data: ByteArray,
        buf: ByteArray,
        moovOff: Int,
        moovSize: Int,
        insertAt: Int,
        delta: Int,
        moovHeaderLen: Int = 8
    ) {
        val containers = setOf("trak", "mdia", "minf", "stbl")
        for (b in walkInto(data, moovOff, moovSize, moovHeaderLen, containers)) {
            if (b.type != "stco" && b.type != "co64") continue
            val entrySize = if (b.type == "co64") 8 else 4
            val body = b.payloadStart
            val count = ZlcBinaryUtils.readU32BE(data, body + 4).toInt()
            val entriesStart = body + 8

            for (i in 0 until count) {
                val epos = entriesStart + i * entrySize
                if (b.type == "co64") {
                    val v = ZlcBinaryUtils.readU64BE(data, epos)
                    if (v.toULong() >= insertAt.toULong()) {
                        ZlcBinaryUtils.putU64BE(buf, epos, v + delta.toLong())
                    }
                } else {
                    val v = ZlcBinaryUtils.readU32BE(data, epos)
                    if (v >= insertAt) {
                        ZlcBinaryUtils.putU32BE(buf, epos, v + delta)
                    }
                }
            }
        }
    }

    fun insertBoxIntoMoov(mp4Bytes: ByteArray, boxType: String, payload: ByteArray): ByteArray {
        require(boxType.length == 4)
        val boxes = iterBoxes(mp4Bytes)
        val moov = boxes.firstOrNull { it.type == "moov" } ?: return mp4Bytes

        val moovOff = moov.start
        val moovSize = moov.size
        val insertAt = moovOff + moovSize

        val newBox = ZlcBinaryUtils.concat(
            ZlcBinaryUtils.writeU32BE((8 + payload.size).toLong()),
            ZlcBinaryUtils.asciiBytes(boxType),
            payload,
        )
        val delta = newBox.size

        val buf = mp4Bytes.copyOf()
        fixChunkOffsets(mp4Bytes, buf, moovOff, moovSize, insertAt, delta, moov.headerSize)
        ZlcBinaryUtils.putU32BE(buf, moovOff, (moovSize + delta).toLong())

        val result = ByteArray(buf.size + delta)
        System.arraycopy(buf, 0, result, 0, insertAt)
        System.arraycopy(newBox, 0, result, insertAt, delta)
        System.arraycopy(buf, insertAt, result, insertAt + delta, buf.size - insertAt)
        return result
    }

    fun removeBoxesFromMoov(mp4Bytes: ByteArray, boxTypesToRemove: Set<String>): ByteArray {
        val boxes = iterBoxes(mp4Bytes)
        val moov = boxes.firstOrNull { it.type == "moov" } ?: return mp4Bytes
        val mdat = boxes.firstOrNull { it.type == "mdat" }

        val kept = ByteArrayOutputStream()
        var removedBytes = 0
        for (child in iterBoxes(mp4Bytes, moov.payloadStart, moov.end)) {
            if (child.type in boxTypesToRemove) {
                removedBytes += child.size
            } else {
                kept.write(mp4Bytes, child.start, child.size)
            }
        }
        if (removedBytes == 0) return mp4Bytes

        val keptBytes = kept.toByteArray()
        if (mdat != null && moov.start < mdat.start) {
            shiftChunkOffsets(keptBytes, 0, keptBytes.size, -removedBytes)
        }

        val newMoovSize = moov.headerSize + keptBytes.size
        val out = ByteArrayOutputStream(mp4Bytes.size - removedBytes)
        out.write(mp4Bytes, 0, moov.start)
        if (moov.headerSize == 8) {
            out.write(ZlcBinaryUtils.writeU32BE(newMoovSize.toLong()))
            out.write(ZlcBinaryUtils.asciiBytes("moov"))
        } else {
            out.write(ZlcBinaryUtils.writeU32BE(1))
            out.write(ZlcBinaryUtils.asciiBytes("moov"))
            out.write(ZlcBinaryUtils.writeU64BE(newMoovSize.toLong()))
        }
        out.write(keptBytes)
        out.write(mp4Bytes, moov.end, mp4Bytes.size - moov.end)
        return out.toByteArray()
    }

    private fun shiftChunkOffsets(buf: ByteArray, start: Int, end: Int, delta: Int) {
        val containerTypes = setOf("trak", "mdia", "minf", "stbl", "edts", "udta")
        for (b in iterBoxes(buf, start, end)) {
            when {
                b.type in containerTypes -> shiftChunkOffsets(buf, b.payloadStart, b.end, delta)
                b.type == "stco" -> {
                    val p = b.payloadStart
                    val count = ZlcBinaryUtils.readU32BE(buf, p + 4).toInt()
                    var off = p + 8
                    repeat(count) {
                        val v = ZlcBinaryUtils.readU32BE(buf, off)
                        ZlcBinaryUtils.putU32BE(buf, off, v + delta)
                        off += 4
                    }
                }
                b.type == "co64" -> {
                    val p = b.payloadStart
                    val count = ZlcBinaryUtils.readU32BE(buf, p + 4).toInt()
                    var off = p + 8
                    repeat(count) {
                        val v = ZlcBinaryUtils.readU64BE(buf, off)
                        ZlcBinaryUtils.putU64BE(buf, off, v + delta)
                        off += 8
                    }
                }
            }
        }
    }

    fun hasMoovChild(mp4Bytes: ByteArray, boxType: String): Boolean {
        val moov = findTopLevelBox(mp4Bytes, "moov") ?: return false
        return iterBoxes(mp4Bytes, moov.payloadStart, moov.end).any { it.type == boxType }
    }

    fun stripVivoUuid(mp4Bytes: ByteArray): ByteArray {
        val boxes = iterBoxes(mp4Bytes)
        val out = ByteArrayOutputStream(mp4Bytes.size)
        var stripped = false
        for (b in boxes) {
            if (b.type == "uuid" && b.size >= b.headerSize + 16 &&
                ZlcBinaryUtils.arrayEquals(mp4Bytes, b.payloadStart, VIVO_UUID)
            ) {
                stripped = true
                continue
            }
            out.write(mp4Bytes, b.start, b.size)
        }
        return if (stripped) out.toByteArray() else mp4Bytes
    }

    /**
     * 1:1 对应 ZLC Mp4Util.wrapVivoUuidBox：
     * buildVivoFooterPayload 自身以 "vivoMediaExtInfovivo" 开头（前 16B 即 VIVO_UUID），
     * 因此直接封装为 [u32BE(payload.size + 8)]["uuid"][payload]。
     */
    fun wrapVivoUuidBox(payload: ByteArray): ByteArray {
        return if (ZlcBinaryUtils.arrayEquals(payload, 0, VIVO_UUID)) {
            ZlcBinaryUtils.concat(
                ZlcBinaryUtils.writeU32BE((payload.size + 8).toLong()),
                ZlcBinaryUtils.asciiBytes("uuid"),
                payload,
            )
        } else {
            val total = 8 + VIVO_UUID.size + payload.size
            ZlcBinaryUtils.concat(
                ZlcBinaryUtils.writeU32BE(total.toLong()),
                ZlcBinaryUtils.asciiBytes("uuid"),
                VIVO_UUID,
                payload,
            )
        }
    }

    /**
     * 1:1 照搬自 ZLC Mp4Util.normalizeFtyp：
     * 归一化 ftyp 品牌：major_brand 与 compatible_brands 中出现的 `qt  `（QuickTime）
     * 全部原地改写为 `isom`。
     */
    fun normalizeFtyp(data: ByteArray): ByteArray {
        if (!hasFtyp(data) || data.size < 16) return data
        val ftypSize = ZlcBinaryUtils.readU32BE(data, 0).toInt()
        if (ftypSize < 16 || ftypSize > data.size) return data
        val qt = byteArrayOf(0x71, 0x74, 0x20, 0x20) // "qt  "
        val buf = data.copyOf()
        var patched = false
        var pos = 8
        while (pos + 4 <= ftypSize) {
            if (pos != 12 && ZlcBinaryUtils.arrayEquals(buf, pos, qt)) {
                buf[pos] = 0x69 // 'i'
                buf[pos + 1] = 0x73 // 's'
                buf[pos + 2] = 0x6F // 'o'
                buf[pos + 3] = 0x6D // 'm'
                patched = true
            }
            pos += 4
        }
        return if (patched) buf else data
    }

    /**
     * 1:1 照搬自 ZLC Mp4Util.pruneNonAvTracks：
     * 纯字节级零拷贝剔除 moov 下 hdlr 不是 vide/soun 的 trak（如 mebx/mett/tmcd）。
     */
    fun pruneNonAvTracks(data: ByteArray): ByteArray {
        if (!hasFtyp(data)) return data
        val boxes = iterBoxes(data, 0, data.size)
        val moov = boxes.firstOrNull { it.type == "moov" } ?: return data
        val moovEnd = moov.end
        val children = iterBoxes(data, moov.payloadStart, moovEnd)
        if (children.isEmpty()) return data

        val drop = ArrayList<Box>()
        for (b in children) {
            if (b.type != "trak") continue
            val hdlr = walkInto(data, b.start, b.size, b.headerSize, setOf("mdia"))
                .firstOrNull { it.type == "hdlr" } ?: return data
            val body = hdlr.payloadStart
            if (body + 12 > data.size) return data
            val handler = String(data, body + 8, 4, Charsets.ISO_8859_1)
            if (handler != "vide" && handler != "soun") drop.add(b)
        }
        if (drop.isEmpty()) return data

        val first = drop.first()
        val last = drop.last()
        val spanEnd = last.end
        for (i in 1 until drop.size) {
            if (drop[i - 1].end != drop[i].start) return data
        }
        for (b in children) {
            if (drop.any { it.start == b.start }) continue
            if (b.start >= first.start && b.end <= spanEnd) return data
        }

        val removed = spanEnd - first.start
        val newMoovSize = moov.size - removed
        val fixed = data.copyOf()
        fixChunkOffsets(data, fixed, moov.start, moov.size, spanEnd, -removed, moov.headerSize)
        val out = ByteArray(data.size - removed)
        System.arraycopy(fixed, 0, out, 0, first.start)
        val tailLen = data.size - spanEnd
        if (tailLen > 0) System.arraycopy(fixed, spanEnd, out, first.start, tailLen)
        ZlcBinaryUtils.putU32BE(out, moov.start, newMoovSize.toLong())
        return out
    }

    fun normalizeMovToMp4(movBytes: ByteArray): ByteArray = normalizeFtyp(pruneNonAvTracks(movBytes))

    /**
     * 1:1 照搬自 ZLC Mp4Util.appleStillImageTimeUs：
     * 解析 Apple MOV 的静帧时刻（still-image-time），单位微秒；无法判定返回 -1。
     */
    fun appleStillImageTimeUs(data: ByteArray): Long {
        if (!hasFtyp(data)) return -1L
        val moov = iterBoxes(data, 0, data.size).firstOrNull { it.type == "moov" } ?: return -1L
        val children = iterBoxes(data, moov.payloadStart, moov.end)
        val movieTimescale = children.firstOrNull { it.type == "mvhd" }?.let { fullBoxTimescale(data, it) } ?: return -1L
        if (movieTimescale <= 0L) return -1L

        val metaEntryTypes = setOf("mebx", "mett", "meta")
        for (trak in children) {
            if (trak.type != "trak") continue
            val entryType = sampleEntryType(data, trak) ?: continue
            if (entryType !in metaEntryTypes) continue
            val elst = walkInto(data, trak.start, trak.size, trak.headerSize, setOf("edts", "mdia"))
                .firstOrNull { it.type == "elst" } ?: continue
            val us = emptyEditDurationUs(data, elst, movieTimescale)
            if (us >= 0L) return us
        }
        return -1L
    }

    private fun fullBoxTimescale(data: ByteArray, box: Box): Long {
        val body = box.payloadStart
        if (body + 24 > data.size) return -1L
        val version = data[body].toInt() and 0xFF
        return ZlcBinaryUtils.readU32BE(data, body + if (version == 1) 20 else 12)
    }

    private fun sampleEntryType(data: ByteArray, trak: Box): String? {
        val stsd = walkInto(data, trak.start, trak.size, trak.headerSize, setOf("mdia", "minf", "stbl"))
            .firstOrNull { it.type == "stsd" } ?: return null
        val body = stsd.payloadStart
        if (body + 16 > data.size) return null
        return String(data, body + 12, 4, Charsets.ISO_8859_1)
    }

    private fun emptyEditDurationUs(data: ByteArray, elst: Box, movieTimescale: Long): Long {
        val body = elst.payloadStart
        if (body + 8 > data.size) return -1L
        val version = data[body].toInt() and 0xFF
        val count = ZlcBinaryUtils.readU32BE(data, body + 4).toInt()
        var p = body + 8
        for (i in 0 until count) {
            if (version == 1) {
                if (p + 20 > data.size) return -1L
                val duration = ZlcBinaryUtils.readU64BE(data, p)
                val mediaTime = ZlcBinaryUtils.readU64BE(data, p + 8)
                p += 20
                if (mediaTime == -1L && duration > 0L) return duration * 1_000_000L / movieTimescale
            } else {
                if (p + 12 > data.size) return -1L
                val duration = ZlcBinaryUtils.readU32BE(data, p)
                val mediaTime = ZlcBinaryUtils.readU32BE(data, p + 4).toInt().toLong()
                p += 12
                if (mediaTime == -1L && duration > 0L) return duration * 1_000_000L / movieTimescale
            }
        }
        return -1L
    }

    private fun childBox(data: ByteArray, entry: Box, type: String, canonicalOffset: Int): Box? {
        val end = minOf(entry.end, data.size)
        iterBoxes(data, entry.start + canonicalOffset, end)
            .firstOrNull { it.type == type }
            ?.let { return it }
        var pos = entry.start + 8
        while (pos + 8 <= end) {
            val size = ZlcBinaryUtils.readU32BE(data, pos).toInt()
            if (size >= 8 && pos + size <= end &&
                String(data, pos + 4, 4, Charsets.ISO_8859_1) == type
            ) {
                return Box(type, pos, size, 8)
            }
            pos += 4
        }
        return null
    }

    private fun hasMirrorMatrix(data: ByteArray, tkhd: Box): Boolean {
        val m = tkhd.end - 8 - 36
        if (m < tkhd.payloadStart) return false
        val a = ZlcBinaryUtils.readI32BE(data, m).toLong()
        val b = ZlcBinaryUtils.readI32BE(data, m + 4).toLong()
        val c = ZlcBinaryUtils.readI32BE(data, m + 12).toLong()
        val d = ZlcBinaryUtils.readI32BE(data, m + 16).toLong()
        return a * d - b * c < 0L
    }

    /**
     * 1:1 照搬自 ZLC Mp4Util.videoCompat：
     * 检测容器重封装无法解决的流级兼容性问题。
     */
    fun videoCompat(data: ByteArray): VideoCompat {
        var videoCodec = ""
        var audioCodec = ""
        val reasons = ArrayList<String>()
        if (!hasFtyp(data)) return VideoCompat(videoCodec, audioCodec, reasons)
        val boxes = iterBoxes(data, 0, data.size)
        val moov = boxes.firstOrNull { it.type == "moov" }
            ?: return VideoCompat(videoCodec, audioCodec, reasons)

        for (b in iterBoxes(data, moov.payloadStart, moov.end)) {
            if (b.type != "trak") continue
            val trak = walkInto(data, b.start, b.size, b.headerSize, setOf("mdia", "minf", "stbl")).toList()
            val hdlr = trak.firstOrNull { it.type == "hdlr" } ?: continue
            val body = hdlr.payloadStart
            if (body + 12 > data.size) continue
            val handler = String(data, body + 8, 4, Charsets.ISO_8859_1)

            val stsd = trak.firstOrNull { it.type == "stsd" } ?: continue
            val entryOffset = stsd.payloadStart + 8
            if (entryOffset + 8 > stsd.end) continue
            val entrySize = ZlcBinaryUtils.readU32BE(data, entryOffset).toInt()
            val fourcc = String(data, entryOffset + 4, 4, Charsets.ISO_8859_1)
            val entry = Box(fourcc, entryOffset, entrySize, 8)

            when (handler) {
                "vide" -> {
                    videoCodec = fourcc
                    if (fourcc !in COMMON_VIDEO_FOURCCS) {
                        reasons.add("视频编码 $fourcc 不是通用的 H.264/H.265")
                    }
                    if (fourcc == "hev1") {
                        reasons.add("H.265 标记为 hev1（部分机型仅识别 hvc1）")
                    }
                    val colr = childBox(data, entry, "colr", VISUAL_ENTRY_CHILD_OFFSET)
                    if (colr != null && colr.start + 16 <= data.size) {
                        val colourType = String(data, colr.start + 8, 4, Charsets.ISO_8859_1)
                        if (colourType == "nclx") {
                            val transfer = (ZlcBinaryUtils.readU32BE(data, colr.start + 8 + 6) ushr 16).toInt() and 0xFFFF
                            if (transfer == 16 || transfer == 18) {
                                val name = if (transfer == 16) "PQ" else "HLG"
                                reasons.add("视频为 HDR ($name 传输特性)")
                            }
                        }
                    }
                    if (childBox(data, entry, "dvcC", VISUAL_ENTRY_CHILD_OFFSET) != null ||
                        childBox(data, entry, "dvvC", VISUAL_ENTRY_CHILD_OFFSET) != null
                    ) {
                        reasons.add("视频包含杜比视界 (Dolby Vision)")
                    }
                    val hvcC = childBox(data, entry, "hvcC", VISUAL_ENTRY_CHILD_OFFSET)
                    if (hvcC != null) {
                        val payload = hvcC.payloadStart
                        if (payload + 19 <= hvcC.end && payload + 18 <= data.size) {
                            val bitDepth = data[payload + 17].toInt() and 0x07
                            if (bitDepth > 0) {
                                reasons.add("视频为 ${bitDepth + 8}bit H.265 (HDR 实况)")
                            }
                        }
                    }
                    val tkhd = trak.firstOrNull { it.type == "tkhd" }
                    if (tkhd != null && hasMirrorMatrix(data, tkhd)) {
                        reasons.add("视频带有镜像变换矩阵")
                    }
                }
                "soun" -> {
                    audioCodec = fourcc
                    if (fourcc != AAC_FOURCC) {
                        reasons.add("音频编码 $fourcc 不是 AAC")
                    }
                }
            }
        }
        return VideoCompat(videoCodec, audioCodec, reasons)
    }

    /**
     * 直接从 QuickTime MOV / MP4 字节流解析 PCM 音轨（支持 iPhone Live Photo 常见的 lpcm v2、sowt、twos、in24、fl32），
     * 并统一转换为 16-bit Little-Endian PCM，供 MediaCodec AAC 编码器合成标准音轨。
     */
    fun extractQuickTimePcmAudio(data: ByteArray): PcmAudioData? {
        if (!hasFtyp(data)) return null
        val moov = findTopLevelBox(data, "moov") ?: return null
        for (b in iterBoxes(data, moov.payloadStart, moov.end)) {
            if (b.type != "trak") continue
            val trakBoxes = walkInto(data, b.start, b.size, b.headerSize, setOf("mdia", "minf", "stbl")).toList()
            val hdlr = trakBoxes.firstOrNull { it.type == "hdlr" } ?: continue
            if (hdlr.payloadStart + 12 > data.size) continue
            val handler = String(data, hdlr.payloadStart + 8, 4, Charsets.ISO_8859_1)
            if (handler != "soun") continue

            val stsd = trakBoxes.firstOrNull { it.type == "stsd" } ?: continue
            val entryOffset = stsd.payloadStart + 8
            if (entryOffset + 36 > stsd.end || entryOffset + 36 > data.size) continue
            val fourcc = String(data, entryOffset + 4, 4, Charsets.ISO_8859_1)
            if (fourcc !in setOf("lpcm", "sowt", "twos", "in24", "in32", "fl32", "raw ")) continue

            val version = ZlcBinaryUtils.readU16BE(data, entryOffset + 16)
            var sampleRate = 44100
            var channels = 1
            var bitsPerChannel = 16
            var isFloat = fourcc == "fl32"
            var isBigEndian = fourcc == "twos" || fourcc == "in24" || fourcc == "in32"
            var bytesPerFrame = 0

            if (version == 2 && entryOffset + 72 <= data.size) {
                val srBits = ZlcBinaryUtils.readU64BE(data, entryOffset + 40)
                val srDouble = Double.fromBits(srBits)
                if (srDouble in 8000.0..192000.0) sampleRate = srDouble.toInt()
                channels = ZlcBinaryUtils.readU32BE(data, entryOffset + 48).toInt().coerceIn(1, 8)
                bitsPerChannel = ZlcBinaryUtils.readU32BE(data, entryOffset + 56).toInt()
                val flags = ZlcBinaryUtils.readU32BE(data, entryOffset + 60).toInt()
                isFloat = (flags and 0x01) != 0
                isBigEndian = (flags and 0x02) != 0
                bytesPerFrame = ZlcBinaryUtils.readU32BE(data, entryOffset + 64).toInt()
            } else {
                channels = ZlcBinaryUtils.readU16BE(data, entryOffset + 24).coerceIn(1, 8)
                bitsPerChannel = ZlcBinaryUtils.readU16BE(data, entryOffset + 26)
                val srFixed = ZlcBinaryUtils.readU32BE(data, entryOffset + 32)
                val sr = (srFixed ushr 16).toInt()
                if (sr in 8000..192000) sampleRate = sr
                if (fourcc == "in24") bitsPerChannel = 24
                if (fourcc == "in32" || fourcc == "fl32") bitsPerChannel = 32
                if (version == 1 && entryOffset + 52 <= data.size) {
                    val bpf = ZlcBinaryUtils.readU32BE(data, entryOffset + 44).toInt()
                    if (bpf > 0) bytesPerFrame = bpf
                }
            }
            if (bitsPerChannel !in listOf(8, 16, 24, 32)) bitsPerChannel = 16
            if (bytesPerFrame <= 0) {
                bytesPerFrame = channels * (bitsPerChannel / 8)
            }
            if (bytesPerFrame <= 0) continue

            val stsc = trakBoxes.firstOrNull { it.type == "stsc" } ?: continue
            val stsz = trakBoxes.firstOrNull { it.type == "stsz" } ?: continue
            val stco = trakBoxes.firstOrNull { it.type == "stco" }
            val co64 = trakBoxes.firstOrNull { it.type == "co64" }

            val chunkOffsets = LongArray(
                when {
                    stco != null && stco.payloadStart + 8 <= stco.end -> {
                        val cnt = ZlcBinaryUtils.readU32BE(data, stco.payloadStart + 4).toInt()
                        cnt.coerceAtLeast(0)
                    }
                    co64 != null && co64.payloadStart + 8 <= co64.end -> {
                        val cnt = ZlcBinaryUtils.readU32BE(data, co64.payloadStart + 4).toInt()
                        cnt.coerceAtLeast(0)
                    }
                    else -> 0
                }
            )
            if (chunkOffsets.isEmpty()) continue
            if (stco != null) {
                var p = stco.payloadStart + 8
                for (i in chunkOffsets.indices) {
                    if (p + 4 > stco.end) break
                    chunkOffsets[i] = ZlcBinaryUtils.readU32BE(data, p)
                    p += 4
                }
            } else if (co64 != null) {
                var p = co64.payloadStart + 8
                for (i in chunkOffsets.indices) {
                    if (p + 8 > co64.end) break
                    chunkOffsets[i] = ZlcBinaryUtils.readU64BE(data, p)
                    p += 8
                }
            }

            if (stsc.payloadStart + 8 > stsc.end) continue
            val stscCount = ZlcBinaryUtils.readU32BE(data, stsc.payloadStart + 4).toInt()
            val firstChunks = IntArray(stscCount)
            val samplesPerChunks = IntArray(stscCount)
            var sp = stsc.payloadStart + 8
            for (i in 0 until stscCount) {
                if (sp + 12 > stsc.end) break
                firstChunks[i] = ZlcBinaryUtils.readU32BE(data, sp).toInt()
                samplesPerChunks[i] = ZlcBinaryUtils.readU32BE(data, sp + 4).toInt()
                sp += 12
            }

            if (stsz.payloadStart + 12 > stsz.end) continue
            val defaultSampleSize = ZlcBinaryUtils.readU32BE(data, stsz.payloadStart + 4).toInt()
            val sampleCount = ZlcBinaryUtils.readU32BE(data, stsz.payloadStart + 8).toInt()

            val rawPcmOut = ByteArrayOutputStream()
            var sampleCursor = 0
            for (chunkIdx0 in chunkOffsets.indices) {
                val chunkNum1 = chunkIdx0 + 1
                var spc = if (samplesPerChunks.isNotEmpty()) samplesPerChunks[0] else 0
                for (j in 0 until stscCount) {
                    if (firstChunks[j] <= chunkNum1) {
                        spc = samplesPerChunks[j]
                    } else {
                        break
                    }
                }
                if (spc <= 0) continue
                val chunkByteLen: Int = when {
                    defaultSampleSize > 1 -> spc * defaultSampleSize
                    defaultSampleSize == 1 -> spc * bytesPerFrame
                    else -> {
                        var sum = 0
                        for (k in 0 until spc) {
                            val idx = sampleCursor + k
                            val pos = stsz.payloadStart + 12 + idx * 4
                            if (pos + 4 <= stsz.end && idx < sampleCount) {
                                sum += ZlcBinaryUtils.readU32BE(data, pos).toInt()
                            }
                        }
                        sum
                    }
                }
                sampleCursor += spc
                val startOff = chunkOffsets[chunkIdx0].toInt()
                if (startOff < 0 || chunkByteLen <= 0 || startOff + chunkByteLen > data.size) continue
                rawPcmOut.write(data, startOff, chunkByteLen)
            }

            val rawBytes = rawPcmOut.toByteArray()
            if (rawBytes.isEmpty()) continue
            val pcm16Le = convertPcmTo16BitLe(rawBytes, bitsPerChannel, isFloat, isBigEndian)
            if (pcm16Le.isNotEmpty()) {
                return PcmAudioData(sampleRate, channels, pcm16Le)
            }
        }
        return null
    }

    private fun convertPcmTo16BitLe(
        raw: ByteArray,
        bitsPerChannel: Int,
        isFloat: Boolean,
        isBigEndian: Boolean
    ): ByteArray {
        if (bitsPerChannel == 16 && !isFloat && !isBigEndian) {
            return raw
        }
        val bytesPerSample = (bitsPerChannel / 8).coerceAtLeast(1)
        val totalSamples = raw.size / bytesPerSample
        val out = ByteArray(totalSamples * 2)
        var inPos = 0
        var outPos = 0
        for (i in 0 until totalSamples) {
            val s16: Int = when {
                isFloat && bitsPerChannel == 32 -> {
                    val bits = if (isBigEndian) {
                        ((raw[inPos].toInt() and 0xFF) shl 24) or
                            ((raw[inPos + 1].toInt() and 0xFF) shl 16) or
                            ((raw[inPos + 2].toInt() and 0xFF) shl 8) or
                            (raw[inPos + 3].toInt() and 0xFF)
                    } else {
                        (raw[inPos].toInt() and 0xFF) or
                            ((raw[inPos + 1].toInt() and 0xFF) shl 8) or
                            ((raw[inPos + 2].toInt() and 0xFF) shl 16) or
                            ((raw[inPos + 3].toInt() and 0xFF) shl 24)
                    }
                    val f = Float.fromBits(bits).coerceIn(-1.0f, 1.0f)
                    (f * 32767.0f).toInt()
                }
                bitsPerChannel == 16 -> {
                    if (isBigEndian) {
                        (raw[inPos].toInt() shl 8) or (raw[inPos + 1].toInt() and 0xFF)
                    } else {
                        (raw[inPos + 1].toInt() shl 8) or (raw[inPos].toInt() and 0xFF)
                    }
                }
                bitsPerChannel == 24 -> {
                    if (isBigEndian) {
                        (raw[inPos].toInt() shl 8) or (raw[inPos + 1].toInt() and 0xFF)
                    } else {
                        (raw[inPos + 2].toInt() shl 8) or (raw[inPos + 1].toInt() and 0xFF)
                    }
                }
                bitsPerChannel == 32 -> {
                    if (isBigEndian) {
                        (raw[inPos].toInt() shl 8) or (raw[inPos + 1].toInt() and 0xFF)
                    } else {
                        (raw[inPos + 3].toInt() shl 8) or (raw[inPos + 2].toInt() and 0xFF)
                    }
                }
                bitsPerChannel == 8 -> {
                    ((raw[inPos].toInt() and 0xFF) - 128) shl 8
                }
                else -> 0
            }
            out[outPos] = (s16 and 0xFF).toByte()
            out[outPos + 1] = ((s16 ushr 8) and 0xFF).toByte()
            inPos += bytesPerSample
            outPos += 2
        }
        return out
    }

    /**
     * 将 16-bit LE PCM 内存数据通过 MediaCodec 编码为标准 AAC 采样包列表与输出 MediaFormat（含 csd-0）。
     */
    fun encodePcm16LeToAac(
        pcmData: PcmAudioData
    ): Pair<MediaFormat, List<Pair<ByteArray, MediaCodec.BufferInfo>>>? {
        var encoder: MediaCodec? = null
        return try {
            val mime = MediaFormat.MIMETYPE_AUDIO_AAC
            val format = MediaFormat.createAudioFormat(mime, pcmData.sampleRate, pcmData.channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536)
            }
            encoder = MediaCodec.createEncoderByType(mime)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            var outputFormat: MediaFormat? = null
            val packets = ArrayList<Pair<ByteArray, MediaCodec.BufferInfo>>()
            val bytesPerSec = (pcmData.sampleRate * pcmData.channelCount * 2).coerceAtLeast(1)
            var inputOffset = 0
            var inputDone = false
            var outputDone = false
            val bufInfo = MediaCodec.BufferInfo()

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = encoder.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = encoder.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        val remaining = pcmData.pcm16Le.size - inputOffset
                        if (remaining <= 0) {
                            encoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val chunk = minOf(remaining, inBuf.remaining(), 8192)
                            inBuf.put(pcmData.pcm16Le, inputOffset, chunk)
                            val ptsUs = (inputOffset.toLong() * 1_000_000L) / bytesPerSec.toLong()
                            encoder.queueInputBuffer(inIdx, 0, chunk, ptsUs, 0)
                            inputOffset += chunk
                        }
                    }
                }

                val outIdx = encoder.dequeueOutputBuffer(bufInfo, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = encoder.outputFormat
                    }
                    outIdx >= 0 -> {
                        val outBuf = encoder.getOutputBuffer(outIdx)
                        val isCodecConfig = (bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (!isCodecConfig && bufInfo.size > 0 && outBuf != null) {
                            outBuf.position(bufInfo.offset)
                            outBuf.limit(bufInfo.offset + bufInfo.size)
                            val bytes = ByteArray(bufInfo.size)
                            outBuf.get(bytes)
                            val copyInfo = MediaCodec.BufferInfo().apply {
                                set(0, bytes.size, bufInfo.presentationTimeUs, bufInfo.flags)
                            }
                            packets.add(bytes to copyInfo)
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if ((bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    else -> {
                        if (inputDone) break
                    }
                }
            }
            val finalFmt = outputFormat
            if (finalFmt != null && packets.isNotEmpty()) {
                finalFmt to packets
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Apple MOV / 异常 MP4 -> 标准 ISO MP4 无损清洗与重封装：
     * 1) 若文件本身已是干净的标准 ISO MP4（不含 "qt  "、不含 mebx/mett/tmcd、不含 QuickTime stsd v1/v2 或非 AAC 音轨），直接原样返回。
     * 2) 若包含 QuickTime 结构（或曾被旧版把 ftyp 改成 isom 但内部仍是 QuickTime stsd v1/v2 / PCM 音轨）：
     *    - 先恢复 "qt  " 头以便 MediaExtractor 按 QuickTime 规范解析；
     *    - 视频轨 1:1 零重编码直通拷贝；
     *    - 音频轨：若已是 AAC 则 1:1 写入标准 ISO mp4a；若为 lpcm/sowt/twos PCM 则提取 PCM 并通过 MediaCodec 编码为标准 AAC 音轨写入，彻底解决无声与 Muxer error。
     */
    fun sanitizeVideoTracksIfNeeded(mp4Bytes: ByteArray, cacheDir: File): ByteArray {
        if (!hasFtyp(mp4Bytes)) return mp4Bytes
        if (!hasQuickTimeSampleEntriesOrTracks(mp4Bytes)) {
            return mp4Bytes
        }

        val qtSourceBytes = restoreQuickTimeFtypIfNeeded(mp4Bytes)
        val inFile = File.createTempFile("zlc_san_in_", ".mov", cacheDir)
        val outFile = File.createTempFile("zlc_san_out_", ".mp4", cacheDir)
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        return try {
            inFile.writeBytes(qtSourceBytes)
            extractor.setDataSource(inFile.absolutePath)

            var videoSrcTrack = -1
            var audioSrcTrack = -1
            var audioMime = ""
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoSrcTrack < 0) {
                    videoSrcTrack = i
                } else if (mime.startsWith("audio/") && audioSrcTrack < 0) {
                    audioSrcTrack = i
                    audioMime = mime
                }
            }
            if (videoSrcTrack < 0) {
                return normalizeMovToMp4(mp4Bytes)
            }

            val rotation = inspectVideo(qtSourceBytes)["rotation"] as? Int ?: 0
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (rotation != 0) {
                runCatching { muxer.setOrientationHint(rotation) }
            }

            val videoDstTrack = muxer.addTrack(extractor.getTrackFormat(videoSrcTrack))
            extractor.selectTrack(videoSrcTrack)

            var audioDstTrack = -1
            var directCopyAudio = false
            var encodedAacPackets: List<Pair<ByteArray, MediaCodec.BufferInfo>>? = null

            if (audioSrcTrack >= 0 && audioMime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                try {
                    audioDstTrack = muxer.addTrack(extractor.getTrackFormat(audioSrcTrack))
                    extractor.selectTrack(audioSrcTrack)
                    directCopyAudio = true
                } catch (_: Exception) {
                    audioDstTrack = -1
                }
            }

            // 若原视频为 QuickTime lpcm v2 / sowt / twos 等 PCM 音轨，MediaExtractor 无法直通给 MediaMuxer，
            // 直接提取 PCM 并编码为标准 AAC 音轨，保证导出/转换后的实况视频有声音
            if (!directCopyAudio) {
                val pcm = extractQuickTimePcmAudio(qtSourceBytes)
                if (pcm != null) {
                    val encoded = encodePcm16LeToAac(pcm)
                    if (encoded != null) {
                        val (aacFmt, packets) = encoded
                        runCatching {
                            audioDstTrack = muxer.addTrack(aacFmt)
                            encodedAacPackets = packets
                        }
                    }
                }
            }

            muxer.start()
            started = true

            val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val trackIdx = extractor.sampleTrackIndex
                if (trackIdx < 0) break
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val dst = when (trackIdx) {
                    videoSrcTrack -> videoDstTrack
                    audioSrcTrack -> if (directCopyAudio) audioDstTrack else -1
                    else -> -1
                }
                if (dst >= 0) {
                    val sampleTime = extractor.sampleTime
                    if (sampleTime >= 0L) {
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = sampleTime
                        info.flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0)
                            MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        muxer.writeSampleData(dst, buffer, info)
                    }
                }
                extractor.advance()
            }

            if (audioDstTrack >= 0 && encodedAacPackets != null) {
                for ((pktBytes, pktInfo) in encodedAacPackets!!) {
                    val pktBuf = ByteBuffer.wrap(pktBytes)
                    muxer.writeSampleData(audioDstTrack, pktBuf, pktInfo)
                }
            }

            muxer.stop()
            muxer.release()
            muxer = null

            val out = outFile.readBytes()
            if (out.size > 64 && hasFtyp(out)) out else normalizeMovToMp4(mp4Bytes)
        } catch (_: Exception) {
            normalizeMovToMp4(mp4Bytes)
        } finally {
            if (muxer != null) {
                try { if (started) muxer.stop() } catch (_: Exception) {}
                try { muxer.release() } catch (_: Exception) {}
            }
            try { extractor.release() } catch (_: Exception) {}
            inFile.delete()
            outFile.delete()
        }
    }
}
