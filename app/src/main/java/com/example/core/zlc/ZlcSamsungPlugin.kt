package com.example.core.zlc

/**
 * 三星动态照片格式插件（直接迁移自 ZLC SamsungPlugin）。
 *
 * 物理结构：
 * [主图 JPEG (FFD8 ... FFD9，清除 XMP MotionPhoto 段)]
 * [SefData 块: 0x0A30 "MotionPhoto_Data" + MP4]
 * [SEFH 目录表 ... SEFT]
 */
object ZlcSamsungPlugin {

    fun pack(primaryJpeg: ByteArray, videoMp4: ByteArray, gainmapJpeg: ByteArray? = null): ByteArray {
        val cleanPrimary = ZlcJpegUtil.stripXmpApp1(primaryJpeg)
        val jpegBlock = if (gainmapJpeg != null) {
            ZlcJpegUtil.updateMpfWithGainmap(cleanPrimary, gainmapJpeg)
        } else {
            cleanPrimary
        }

        val records = listOf(
            ZlcSamsungSef.Record(
                type = ZlcSamsungSef.TYPE_MOTION_PHOTO_DATA,
                sub = 0,
                key = ZlcSamsungSef.KEY_MOTION_PHOTO_DATA,
                payload = videoMp4,
            )
        )
        val sefBytes = ZlcSamsungSef.buildSegment(ZlcSamsungSef.DEFAULT_VERSION, records)
        return ZlcBinaryUtils.concat(jpegBlock, sefBytes)
    }
}
