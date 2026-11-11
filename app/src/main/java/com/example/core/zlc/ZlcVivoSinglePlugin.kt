package com.example.core.zlc

import kotlin.math.round

/**
 * vivo 单文件动态照片插件（1:1 完整照搬自 ZLC VivoSinglePlugin + OppoPlugin.buildLpexPayload）。
 */
object ZlcVivoSinglePlugin {

    fun buildLpexPayload(
        primaryJpeg: ByteArray,
        videoInfo: Map<String, Any?>,
        ptsUs: Long
    ): ByteArray {
        val vw = (videoInfo["width"] as? Int) ?: 0
        val vh = (videoInfo["height"] as? Int) ?: 0
        val (iw, ih) = ZlcJpegUtil.getDimensions(primaryJpeg)

        val payload = linkedMapOf<String, Any?>(
            "coverFramePts" to ptsUs,
            "cropRect" to intArrayOf(0, 0, vw, vh),
            "desc" to "OppoMotionVideoExt",
            "matrixCount" to 0,
            "originPhotoSize" to intArrayOf(iw, ih),
            "photoCropFactor" to 1.0,
            "photoCropMatrix" to intArrayOf(1, 0, 0, 0, 1, 0, 0, 0, 1),
            "photoCropRect" to intArrayOf(0, 0, iw, ih),
            "photoEisCropFactor" to doubleArrayOf(1.0, 1.0),
            "photoEisMatrix" to intArrayOf(1, 0, 0, 0, 1, 0, 0, 0, 1),
            "subVideoScaleFactor" to 0.5,
            "version" to 1,
            "videoOrientation" to ((videoInfo["rotation"] as? Int) ?: 0),
            "videoSize" to intArrayOf(vw, vh)
        )
        val jsonBytes = ZlcFooterUtil.buildFooterJson(payload)
        val prefix = "LivePhotoExtension".toByteArray(Charsets.US_ASCII)
        return prefix + jsonBytes
    }

    fun pack(
        primaryJpeg: ByteArray,
        videoMp4: ByteArray,
        presentationTimestampUs: Long = 0L,
        gainmapJpeg: ByteArray? = null
    ): ByteArray {
        var video = ZlcMp4Util.stripVivoUuid(videoMp4)
        val videoInfo = ZlcMp4Util.inspectVideo(video)

        val ptsUs = if (presentationTimestampUs > 0L) {
            presentationTimestampUs
        } else {
            val dur = videoInfo["duration_us"] as? Long
            if (dur != null && dur > 0L) dur / 2 else -1L
        }

        val fps = videoInfo["fps"] as? Double
        val frameCount = videoInfo["frame_count"] as? Long
        val imageTime = when {
            ptsUs >= 0 && fps != null && fps > 0.0 -> round(ptsUs / 1_000_000.0 * fps).toLong()
            frameCount != null && frameCount > 0L -> frameCount / 2
            else -> 0L
        }

        if (video.size >= 8 && !ZlcMp4Util.hasMoovChild(video, "lpex")) {
            try {
                video = ZlcMp4Util.insertBoxIntoMoov(video, "lpex", buildLpexPayload(primaryJpeg, videoInfo, ptsUs))
            } catch (_: Exception) {}
        }

        val footerJson = ZlcFooterUtil.buildFooterJson(
            linkedMapOf(
                "com.vivo.gallery.livePhoto.otherPhone.MotionRotationOffset" to 0,
                "com.android.camera.imageTime" to imageTime,
                "com.vivo.gallery.file.convert" to 10004,
                "com.vivo.gallery.livePhoto.otherPhone.MotionRotationCheck" to 1,
                "com.android.camera.livephoto" to ZlcFooterUtil.oppoFixedId,
                "version" to 2200
            )
        )
        val footer = ZlcFooterUtil.buildFooter(footerJson, ZlcFooterUtil.oppoFixedId, ZlcFooterUtil.extPrefix)
        val trailer = ZlcMp4Util.wrapVivoUuidBox(footer)

        val xmp = ZlcXmpTemplate.buildVivoSingleXmp(ptsUs, gainmapJpeg?.size, video.size + trailer.size)
        val primary = ZlcJpegUtil.replaceOrInsertXmp(primaryJpeg, xmp)
        return if (gainmapJpeg != null) {
            ZlcBinaryUtils.concat(primary, gainmapJpeg, video, trailer)
        } else {
            ZlcBinaryUtils.concat(primary, video, trailer)
        }
    }
}
