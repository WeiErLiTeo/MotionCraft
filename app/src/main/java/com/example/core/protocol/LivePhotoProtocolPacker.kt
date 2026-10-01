package com.example.core.protocol

import android.media.ExifInterface
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

object LivePhotoProtocolPacker {
    private const val TAG = "ProtocolPacker"

    fun buildMpfSegment(totalJpegSize: Int): ByteArray {
        val tiff = ByteArrayOutputStream(66)
        tiff.write(byteArrayOf(0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08))
        tiff.write(byteArrayOf(0x00, 0x03))
        tiff.write(byteArrayOf(0xB0.toByte(), 0x00, 0x00, 0x07, 0x00, 0x00, 0x00, 0x04))
        tiff.write("0100".toByteArray(Charsets.US_ASCII))
        tiff.write(byteArrayOf(0xB0.toByte(), 0x01, 0x00, 0x04, 0x00, 0x00, 0x00, 0x01))
        tiff.write(byteArrayOf(0x00, 0x00, 0x00, 0x01))
        tiff.write(byteArrayOf(0xB0.toByte(), 0x02, 0x00, 0x07, 0x00, 0x00, 0x00, 0x10))
        tiff.write(byteArrayOf(0x00, 0x00, 0x00, 0x32))
        tiff.write(byteArrayOf(0x00, 0x00, 0x00, 0x00))

        tiff.write(byteArrayOf(0x00, 0x03, 0x00, 0x00))
        tiff.write((totalJpegSize shr 24) and 0xFF)
        tiff.write((totalJpegSize shr 16) and 0xFF)
        tiff.write((totalJpegSize shr 8) and 0xFF)
        tiff.write(totalJpegSize and 0xFF)
        tiff.write(byteArrayOf(0x00, 0x00, 0x00, 0x00))
        tiff.write(byteArrayOf(0x00, 0x00, 0x00, 0x00))

        val body = ByteArrayOutputStream(74)
        body.write("MPF\u0000".toByteArray(Charsets.US_ASCII))
        body.write(tiff.toByteArray())

        val segLen = body.size() + 2
        val out = ByteArrayOutputStream(segLen + 2)
        out.write(0xFF)
        out.write(0xE2)
        out.write((segLen shr 8) and 0xFF)
        out.write(segLen and 0xFF)
        out.write(body.toByteArray())
        return out.toByteArray()
    }

    fun wrapXmpPayload(xmpContent: String): ByteArray {
        val namespace = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.UTF_8)
        val xmpBytes = xmpContent.toByteArray(Charsets.UTF_8)

        val payloadSize = namespace.size + xmpBytes.size
        val markerSize = payloadSize + 2

        val out = ByteArrayOutputStream(markerSize + 2)
        out.write(0xFF)
        out.write(0xE1)
        out.write((markerSize shr 8) and 0xFF)
        out.write(markerSize and 0xFF)
        out.write(namespace)
        out.write(xmpBytes)
        return out.toByteArray()
    }

    fun buildXiaomiMotionPhotoXmp(videoLength: Int, presentationTimestampUs: Long = 0L): ByteArray {
        val xmpContent = """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
        GCamera:MotionPhoto="1"
        GCamera:MotionPhotoVersion="1"
        GCamera:MotionPhotoPresentationTimestampUs="$presentationTimestampUs">
      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0" Item:Padding="0"/>
          </rdf:li>
          <rdf:li rdf:parseType="Resource">
            <Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="$videoLength" Item:Padding="0"/>
          </rdf:li>
        </rdf:Seq>
      </Container:Directory>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""
        return wrapXmpPayload(xmpContent)
    }

    fun buildOppoXmp(videoLength: Int, presentationTimestampUs: Long = 0L): ByteArray {
        val xmpContent = """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:OpCamera="http://ns.oplus.com/photos/1.0/camera/"
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
        GCamera:MotionPhoto="1"
        GCamera:MotionPhotoVersion="1"
        GCamera:MotionPhotoPresentationTimestampUs="$presentationTimestampUs"
        OpCamera:MotionPhotoPrimaryPresentationTimestampUs="$presentationTimestampUs"
        OpCamera:MotionPhotoOwner="oplus"
        OpCamera:OLivePhotoVersion="2"
        OpCamera:VideoLength="$videoLength"
        OpCamera:MotionPhotoFeatureFlag="1">
      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0" Item:Padding="0"/>
          </rdf:li>
          <rdf:li rdf:parseType="Resource">
            <Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="$videoLength" Item:Padding="0"/>
          </rdf:li>
        </rdf:Seq>
      </Container:Directory>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""
        return wrapXmpPayload(xmpContent)
    }

    fun buildFusionXmp(videoLength: Int, presentationTimestampUs: Long = 0L): ByteArray {
        val xmpContent = """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:OpCamera="http://ns.oplus.com/photos/1.0/camera/"
        xmlns:VCamera="http://ns.vivo.com/photos/1.0/camera/"
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
        GCamera:MotionPhoto="1"
        GCamera:MotionPhotoVersion="1"
        GCamera:MotionPhotoPresentationTimestampUs="$presentationTimestampUs"
        OpCamera:MotionPhotoPrimaryPresentationTimestampUs="$presentationTimestampUs"
        OpCamera:MotionPhotoOwner="oplus"
        OpCamera:OLivePhotoVersion="2"
        OpCamera:VideoLength="$videoLength"
        OpCamera:MotionPhotoFeatureFlag="1"
        VCamera:VMotionPhotoVersion="1"
        VCamera:VMotionPhotoSource="1"
        VCamera:VMediaKitVersion="1.0.0.9">
      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0" Item:Padding="0"/>
          </rdf:li>
          <rdf:li rdf:parseType="Resource">
            <Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="$videoLength" Item:Padding="0"/>
          </rdf:li>
        </rdf:Seq>
      </Container:Directory>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""
        return wrapXmpPayload(xmpContent)
    }

    fun buildOplusFallbackExifSegment(userComment: String = "{\"oplustag\":8388608}"): ByteArray {
        val commentBytes = userComment.toByteArray(Charsets.UTF_8)
        val charsetPrefix = byteArrayOf(0x55, 0x4E, 0x49, 0x43, 0x4F, 0x44, 0x45, 0x00)
        val valueBytes = charsetPrefix + commentBytes

        val tiff = ByteArrayOutputStream()
        tiff.write(byteArrayOf(0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00))
        tiff.write(byteArrayOf(0x01, 0x00))
        tiff.write(byteArrayOf(0x69.toByte(), 0x87.toByte(), 0x04, 0x00, 0x01, 0x00, 0x00, 0x00, 0x1A, 0x00, 0x00, 0x00))
        tiff.write(byteArrayOf(0x00, 0x00, 0x00, 0x00))
        tiff.write(byteArrayOf(0x01, 0x00))
        tiff.write(byteArrayOf(0x86.toByte(), 0x92.toByte(), 0x07, 0x00))
        val len = valueBytes.size
        tiff.write(len and 0xFF)
        tiff.write((len shr 8) and 0xFF)
        tiff.write((len shr 16) and 0xFF)
        tiff.write((len shr 24) and 0xFF)
        tiff.write(byteArrayOf(0x28, 0x00, 0x00, 0x00))
        tiff.write(byteArrayOf(0x00, 0x00, 0x00, 0x00))
        tiff.write(valueBytes)

        val body = ByteArrayOutputStream()
        body.write("Exif\u0000\u0000".toByteArray(Charsets.US_ASCII))
        body.write(tiff.toByteArray())

        val segLen = body.size() + 2
        val out = ByteArrayOutputStream(segLen + 2)
        out.write(0xFF)
        out.write(0xE1)
        out.write((segLen shr 8) and 0xFF)
        out.write(segLen and 0xFF)
        out.write(body.toByteArray())
        return out.toByteArray()
    }

    fun injectLivePhotoMetadata(
        jpegBytes: ByteArray,
        videoLength: Int,
        presentationTimestampUs: Long = 0L,
        brandMode: LivePhotoBrandMode = LivePhotoBrandMode.FUSION
    ): ByteArray {
        val out = ByteArrayOutputStream(jpegBytes.size + 4096)

        if (jpegBytes.size < 2 || jpegBytes[0] != 0xFF.toByte() || jpegBytes[1] != 0xD8.toByte()) {
            return jpegBytes
        }

        out.write(0xFF)
        out.write(0xD8)

        val xmpSegment = when (brandMode) {
            LivePhotoBrandMode.FUSION -> buildFusionXmp(videoLength, presentationTimestampUs)
            LivePhotoBrandMode.OPPO -> buildOppoXmp(videoLength, presentationTimestampUs)
            LivePhotoBrandMode.XIAOMI -> buildXiaomiMotionPhotoXmp(videoLength, presentationTimestampUs)
        }

        out.write(xmpSegment)

        val totalJpegSize = jpegBytes.size + xmpSegment.size + 78
        val mpfSegment = buildMpfSegment(totalJpegSize)
        out.write(mpfSegment)

        var pos = 2
        var hasExif = false

        while (pos < jpegBytes.size - 1) {
            if (jpegBytes[pos] != 0xFF.toByte()) {
                out.write(jpegBytes, pos, jpegBytes.size - pos)
                break
            }

            val marker = jpegBytes[pos + 1].toInt() and 0xFF
            if (marker == 0xD9 || marker == 0xDA) {
                out.write(jpegBytes, pos, jpegBytes.size - pos)
                break
            }

            if (pos + 3 >= jpegBytes.size) {
                out.write(jpegBytes, pos, jpegBytes.size - pos)
                break
            }

            val length = ((jpegBytes[pos + 2].toInt() and 0xFF) shl 8) or (jpegBytes[pos + 3].toInt() and 0xFF)
            val segmentEnd = pos + 2 + length

            if (segmentEnd > jpegBytes.size) {
                out.write(jpegBytes, pos, jpegBytes.size - pos)
                break
            }

            if (marker == 0xE1) {
                if (pos + 10 < jpegBytes.size &&
                    String(jpegBytes.sliceArray(pos + 4 until pos + 10), Charsets.US_ASCII).startsWith("Exif")) {
                    hasExif = true
                }
            }

            out.write(jpegBytes, pos, segmentEnd - pos)
            pos = segmentEnd
        }

        if (!hasExif && (brandMode == LivePhotoBrandMode.OPPO || brandMode == LivePhotoBrandMode.FUSION)) {
            val fallbackExif = buildOplusFallbackExifSegment()
            val finalBytes = out.toByteArray()
            val withExif = ByteArrayOutputStream(finalBytes.size + fallbackExif.size)
            withExif.write(finalBytes, 0, 2)
            withExif.write(fallbackExif)
            withExif.write(finalBytes, 2, finalBytes.size - 2)
            return withExif.toByteArray()
        }

        return out.toByteArray()
    }

    fun packageLivePhoto(
        coverFile: File,
        videoFile: File,
        outputFile: File,
        presentationTimestampUs: Long = 0L,
        brandMode: LivePhotoBrandMode = LivePhotoBrandMode.FUSION
    ): Boolean {
        return try {
            if (brandMode == LivePhotoBrandMode.OPPO || brandMode == LivePhotoBrandMode.FUSION) {
                try {
                    val exif = ExifInterface(coverFile.absolutePath)
                    exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "{\"oplustag\":8388608}")
                    exif.saveAttributes()
                } catch (e: Exception) {
                    Log.w(TAG, "ExifInterface save error: ${e.message}")
                }
            }

            val coverBytes = coverFile.readBytes()
            val videoBytes = videoFile.readBytes()
            val videoLength = videoBytes.size

            val modifiedJpeg = injectLivePhotoMetadata(
                jpegBytes = coverBytes,
                videoLength = videoLength,
                presentationTimestampUs = presentationTimestampUs,
                brandMode = brandMode
            )

            outputFile.outputStream().buffered().use { out ->
                out.write(modifiedJpeg)
                out.write(videoBytes)
                out.flush()
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Package live photo error", e)
            false
        }
    }
}
