package com.example.core.zlc

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 字节数组与大端/小端整数读写工具（直接迁移自 ZLC BinaryUtils）。
 */
object ZlcBinaryUtils {

    fun readU16BE(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    fun readU16LE(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    fun readU32BE(data: ByteArray, offset: Int): Long =
        ((data[offset].toLong() and 0xFF) shl 24) or
            ((data[offset + 1].toLong() and 0xFF) shl 16) or
            ((data[offset + 2].toLong() and 0xFF) shl 8) or
            (data[offset + 3].toLong() and 0xFF)

    fun readU32LE(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFF) or
            ((data[offset + 1].toLong() and 0xFF) shl 8) or
            ((data[offset + 2].toLong() and 0xFF) shl 16) or
            ((data[offset + 3].toLong() and 0xFF) shl 24)

    fun readU64BE(data: ByteArray, offset: Int): Long =
        (readU32BE(data, offset) shl 32) or (readU32BE(data, offset + 4) and 0xFFFFFFFFL)

    fun readI32BE(data: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(data, offset, 4).order(ByteOrder.BIG_ENDIAN).int

    fun writeU16BE(value: Int): ByteArray = byteArrayOf(
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    fun writeU16LE(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
    )

    fun writeU32BE(value: Long): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    fun writeU32LE(value: Long): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte(),
    )

    fun writeU64BE(value: Long): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(value).array()

    fun putU32BE(dst: ByteArray, offset: Int, value: Long) {
        dst[offset] = ((value ushr 24) and 0xFF).toByte()
        dst[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        dst[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        dst[offset + 3] = (value and 0xFF).toByte()
    }

    fun putU64BE(dst: ByteArray, offset: Int, value: Long) {
        putU32BE(dst, offset, value ushr 32)
        putU32BE(dst, offset + 4, value and 0xFFFFFFFFL)
    }

    /** KMP/朴素字节子序列查找，未找到返回 -1 */
    fun indexOf(haystack: ByteArray, needle: ByteArray, fromIndex: Int = 0, endIndex: Int = haystack.size): Int {
        if (needle.isEmpty()) return fromIndex
        val limit = endIndex - needle.size
        if (fromIndex > limit) return -1
        val first = needle[0]
        var i = fromIndex
        while (i <= limit) {
            if (haystack[i] == first) {
                var j = 1
                while (j < needle.size && haystack[i + j] == needle[j]) j++
                if (j == needle.size) return i
            }
            i++
        }
        return -1
    }

    fun lastIndexOf(haystack: ByteArray, needle: ByteArray, fromIndex: Int = 0, endIndex: Int = haystack.size): Int {
        if (needle.isEmpty()) return endIndex
        var i = endIndex - needle.size
        val first = needle[0]
        while (i >= fromIndex) {
            if (haystack[i] == first) {
                var j = 1
                while (j < needle.size && haystack[i + j] == needle[j]) j++
                if (j == needle.size) return i
            }
            i--
        }
        return -1
    }

    fun concat(vararg parts: ByteArray): ByteArray {
        var total = 0
        for (p in parts) total += p.size
        val out = ByteArray(total)
        var pos = 0
        for (p in parts) {
            System.arraycopy(p, 0, out, pos, p.size)
            pos += p.size
        }
        return out
    }

    fun arrayEquals(a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int = 0, length: Int = b.size): Boolean {
        if (aOffset + length > a.size || bOffset + length > b.size) return false
        for (i in 0 until length) {
            if (a[aOffset + i] != b[bOffset + i]) return false
        }
        return true
    }

    fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.ISO_8859_1)

    fun asciiBytes(s: String): ByteArray = s.toByteArray(Charsets.ISO_8859_1)
}
