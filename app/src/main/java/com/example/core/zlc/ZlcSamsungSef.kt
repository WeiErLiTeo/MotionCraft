package com.example.core.zlc

import java.io.ByteArrayOutputStream

/**
 * 三星 SEF（Samsung Extended File）尾部段无损解析与封装（直接迁移自 ZLC SamsungSef）。
 */
object ZlcSamsungSef {

    private val SEFT_MAGIC = "SEFT".toByteArray(Charsets.ISO_8859_1)
    private val SEFH_MAGIC = "SEFH".toByteArray(Charsets.ISO_8859_1)

    const val TYPE_MOTION_PHOTO_DATA = 0x0A30
    const val KEY_MOTION_PHOTO_DATA = "MotionPhoto_Data"
    const val DEFAULT_VERSION = 0x6A

    data class Record(
        val type: Int,
        val sub: Int,
        val key: String,
        val payload: ByteArray,
    )

    data class Parsed(
        val segStart: Int,
        val sefhPos: Int,
        val version: Int,
        val records: List<Record>,
    )

    fun parse(data: ByteArray): Parsed? {
        if (data.size < 28) return null
        if (!ZlcBinaryUtils.arrayEquals(data, data.size - 4, SEFT_MAGIC)) return null

        val dirLen = ZlcBinaryUtils.readU32LE(data, data.size - 8).toInt()
        if (dirLen < 8 || dirLen > 65536) return null
        val sefhPos = data.size - 8 - dirLen
        if (sefhPos < 0 || !ZlcBinaryUtils.arrayEquals(data, sefhPos, SEFH_MAGIC)) return null

        val version = ZlcBinaryUtils.readU32LE(data, sefhPos + 4).toInt()
        val count = ZlcBinaryUtils.readU32LE(data, sefhPos + 8).toInt()
        if (count <= 0 || count > 256) return null
        if (sefhPos + 12 + count * 12 > data.size - 8) return null

        val records = ArrayList<Record>(count)
        var minBlockPos = sefhPos
        for (i in 0 until count) {
            val ePos = sefhPos + 12 + i * 12
            val sub = ZlcBinaryUtils.readU16LE(data, ePos)
            val type = ZlcBinaryUtils.readU16LE(data, ePos + 2)
            val relOff = ZlcBinaryUtils.readU32LE(data, ePos + 4).toInt()
            val blkLen = ZlcBinaryUtils.readU32LE(data, ePos + 8).toInt()

            val blkPos = sefhPos - relOff
            if (blkPos < 0 || blkLen < 8 || blkPos + blkLen > sefhPos) return null

            val bSub = ZlcBinaryUtils.readU16LE(data, blkPos)
            val bType = ZlcBinaryUtils.readU16LE(data, blkPos + 2)
            val keyLen = ZlcBinaryUtils.readU32LE(data, blkPos + 4).toInt()
            if (bSub != sub || bType != type) return null
            if (keyLen < 0 || 8 + keyLen > blkLen) return null

            val key = String(data, blkPos + 8, keyLen, Charsets.ISO_8859_1)
            val payload = data.copyOfRange(blkPos + 8 + keyLen, blkPos + blkLen)
            records.add(Record(type = type, sub = sub, key = key, payload = payload))
            if (blkPos < minBlockPos) minBlockPos = blkPos
        }

        return Parsed(
            segStart = minBlockPos,
            sefhPos = sefhPos,
            version = version,
            records = records,
        )
    }

    fun buildSegment(version: Int = DEFAULT_VERSION, records: List<Record>): ByteArray {
        val blocks = records.map { r ->
            val keyBytes = r.key.toByteArray(Charsets.ISO_8859_1)
            ZlcBinaryUtils.concat(
                ZlcBinaryUtils.writeU16LE(r.sub),
                ZlcBinaryUtils.writeU16LE(r.type),
                ZlcBinaryUtils.writeU32LE(keyBytes.size.toLong()),
                keyBytes,
                r.payload,
            )
        }

        val totalBlocksSize = blocks.sumOf { it.size }
        val out = ByteArrayOutputStream(totalBlocksSize + 12 + records.size * 12 + 8)
        for (b in blocks) out.write(b)

        out.write(SEFH_MAGIC)
        out.write(ZlcBinaryUtils.writeU32LE(version.toLong()))
        out.write(ZlcBinaryUtils.writeU32LE(records.size.toLong()))

        var distFromSefh = totalBlocksSize
        for ((r, b) in records.zip(blocks)) {
            out.write(ZlcBinaryUtils.writeU16LE(r.sub))
            out.write(ZlcBinaryUtils.writeU16LE(r.type))
            out.write(ZlcBinaryUtils.writeU32LE(distFromSefh.toLong()))
            out.write(ZlcBinaryUtils.writeU32LE(b.size.toLong()))
            distFromSefh -= b.size
        }

        val dirLen = 12 + records.size * 12
        out.write(ZlcBinaryUtils.writeU32LE(dirLen.toLong()))
        out.write(SEFT_MAGIC)
        return out.toByteArray()
    }
}
