package com.example.core.zlc

import kotlin.random.Random

/**
 * vivo / OPPO 共用的 cameralbum! footer 编解码（1:1 完整照搬自 ZLC FooterUtil + JsonMin）。
 */
object ZlcFooterUtil {

    val magic: ByteArray = byteArrayOf(
        0x1B, 0x2A, 0x39, 0x48, 0x57, 0x66, 0x75,
        0x84.toByte(), 0x93.toByte(), 0xA2.toByte(), 0xB3.toByte()
    )
    val marker: ByteArray = "cameralbum!".toByteArray(Charsets.US_ASCII)
    val vivoPrefix: ByteArray = "vivo".toByteArray(Charsets.US_ASCII)
    val extPrefix: ByteArray = "vivoMediaExtInfovivo".toByteArray(Charsets.US_ASCII) // vivoMediaExtInfo + vivo
    const val idLen: Int = 28

    /** OPPO / vivo 单文件使用的固定 ID（'motionphoto' + '0'*17） */
    val oppoFixedId: String = "motionphoto" + "0".repeat(17)

    class FooterInfo {
        var json: Map<String, Any?>? = null
        var id: String? = null
        var prefix: ByteArray = ByteArray(0)
        var footerStart: Int = 0
        var version: Int? = null
        var imageTime: Long? = null
        var livephotoId: String? = null
    }

    /**
     * 生成 vivo 风格 livephoto ID：'-<数字><8位随机字符>'，'0' 填充至 28 字符。
     */
    fun generateLivephotoId(): String {
        val num = Random.nextInt(1, 2147483647)
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val rand = (0 until 8).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        val s = "-$num$rand"
        return s.padEnd(idLen, '0').substring(0, idLen)
    }

    /**
     * 构造完整 footer（含前缀），与 ZLC FooterUtil.buildFooter 逐字节一致。
     */
    fun buildFooter(jsonBytes: ByteArray, idStr: String, prefix: ByteArray = extPrefix): ByteArray {
        var idBytes = idStr.toByteArray(Charsets.US_ASCII)
        if (idBytes.size != idLen) {
            val padded = ByteArray(idLen)
            System.arraycopy(idBytes, 0, padded, 0, minOf(idBytes.size, idLen))
            for (i in idBytes.size until idLen) padded[i] = '0'.code.toByte()
            idBytes = padded
        }

        val tail = ByteArray(idLen + 4 + magic.size)
        System.arraycopy(idBytes, 0, tail, 0, idLen)
        tail[idLen] = 0xFF.toByte()
        tail[idLen + 1] = 0xFF.toByte()
        tail[idLen + 2] = 0xFF.toByte()
        tail[idLen + 3] = 0xFF.toByte()
        System.arraycopy(magic, 0, tail, idLen + 4, magic.size)

        val result = ByteArray(prefix.size + jsonBytes.size + 4 + marker.size + 4 + tail.size)
        var pos = 0
        System.arraycopy(prefix, 0, result, pos, prefix.size); pos += prefix.size
        System.arraycopy(jsonBytes, 0, result, pos, jsonBytes.size); pos += jsonBytes.size
        ZlcBinaryUtils.putU32BE(result, pos, jsonBytes.size.toLong()); pos += 4
        System.arraycopy(marker, 0, result, pos, marker.size); pos += marker.size
        ZlcBinaryUtils.putU32BE(result, pos, (tail.size + 4).toLong()); pos += 4
        System.arraycopy(tail, 0, result, pos, tail.size)
        return result
    }

    fun buildFooter(
        fields: Map<String, Any?>,
        idStr: String,
        prefix: ByteArray = extPrefix
    ): ByteArray = buildFooter(buildFooterJson(fields), idStr, prefix)

    /**
     * 按样本键序生成 footer JSON（紧凑分隔符，UTF-8 直出，与 ZLC FooterUtil.buildFooterJson 一致）。
     */
    fun buildFooterJson(fields: Map<String, Any?>): ByteArray {
        val sb = StringBuilder()
        sb.append('{')
        var first = true
        for ((key, value) in fields) {
            if (!first) sb.append(',')
            first = false
            sb.append('"')
            ZlcJsonMin.escapeString(key, sb)
            sb.append('"')
            sb.append(':')
            ZlcJsonMin.writeValue(value, sb)
        }
        sb.append('}')
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
}

object ZlcJsonMin {
    fun writeValue(v: Any?, sb: StringBuilder) {
        when (v) {
            null -> sb.append("null")
            is String -> {
                sb.append('"')
                escapeString(v, sb)
                sb.append('"')
            }
            is Int -> sb.append(v.toString())
            is Long -> sb.append(v.toString())
            is Double -> sb.append(v.toString())
            is Float -> sb.append(v.toDouble().toString())
            is Boolean -> sb.append(if (v) "true" else "false")
            is IntArray -> {
                sb.append('[')
                for (i in v.indices) {
                    if (i > 0) sb.append(',')
                    sb.append(v[i].toString())
                }
                sb.append(']')
            }
            is LongArray -> {
                sb.append('[')
                for (i in v.indices) {
                    if (i > 0) sb.append(',')
                    sb.append(v[i].toString())
                }
                sb.append(']')
            }
            is DoubleArray -> {
                sb.append('[')
                for (i in v.indices) {
                    if (i > 0) sb.append(',')
                    sb.append(v[i].toString())
                }
                sb.append(']')
            }
            is Array<*> -> {
                sb.append('[')
                for (i in v.indices) {
                    if (i > 0) sb.append(',')
                    writeValue(v[i], sb)
                }
                sb.append(']')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (item in v) {
                    if (!first) sb.append(',')
                    first = false
                    writeValue(item, sb)
                }
                sb.append(']')
            }
            else -> {
                sb.append('"')
                escapeString(v.toString(), sb)
                sb.append('"')
            }
        }
    }

    fun escapeString(s: String, sb: StringBuilder) {
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> {
                    if (c.code < 0x20) {
                        sb.append("\\u")
                        sb.append(c.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
    }
}
