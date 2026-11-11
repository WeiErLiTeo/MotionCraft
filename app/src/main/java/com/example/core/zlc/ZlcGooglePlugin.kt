package com.example.core.zlc

/**
 * Google 原生 MotionPhoto 动态照片插件（1:1 完整照搬自 ZLC GooglePlugin）。
 *
 * 物理结构：
 * [主图 JPEG (含 GCamera MotionPhoto + Container:Directory)]
 * [可选 GainMap JPEG]
 * [纯 MP4]
 */
object ZlcGooglePlugin {

    fun pack(
        primaryJpeg: ByteArray,
        videoMp4: ByteArray,
        presentationTimestampUs: Long = 0L,
        gainmapJpeg: ByteArray? = null
    ): ByteArray {
        val video = ZlcMp4Util.removeBoxesFromMoov(ZlcMp4Util.stripVivoUuid(videoMp4), setOf("lpex"))
        val ptsUs = if (presentationTimestampUs > 0L) {
            presentationTimestampUs
        } else {
            val dur = ZlcMp4Util.inspectVideo(video)["duration_us"] as? Long
            if (dur != null && dur > 0L) dur / 2 else -1L
        }
        val xmp = ZlcXmpTemplate.buildGoogleXmp(ptsUs, gainmapJpeg?.size, video.size)
        val primary = ZlcJpegUtil.replaceOrInsertXmp(primaryJpeg, xmp)
        return if (gainmapJpeg != null) {
            ZlcBinaryUtils.concat(primary, gainmapJpeg, video)
        } else {
            ZlcBinaryUtils.concat(primary, video)
        }
    }
}
