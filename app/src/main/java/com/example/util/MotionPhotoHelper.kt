package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.core.media.ImageProcessor
import com.example.core.media.VideoProcessor
import com.example.core.protocol.LivePhotoBrandMode
import com.example.core.protocol.LivePhotoProtocolPacker
import java.io.File
import java.io.InputStream

typealias LivePhotoBrandMode = com.example.core.protocol.LivePhotoBrandMode

object MotionPhotoHelper {
    fun extractVideoFromMotionPhoto(file: File, cacheFile: File): Boolean =
        ImageProcessor.extractVideoFromMotionPhoto(file, cacheFile)

    fun extractVideoFromMotionPhoto(inputStream: InputStream, cacheFile: File): Boolean =
        ImageProcessor.extractVideoFromMotionPhoto(inputStream, cacheFile)

    fun isMotionPhoto(context: Context, uri: Uri): Boolean =
        ImageProcessor.isMotionPhoto(context, uri)

    fun extractXmpXml(bytes: ByteArray): String =
        ImageProcessor.extractXmpXml(bytes)

    fun extractExifMetadata(inputStream: InputStream): String =
        ImageProcessor.extractExifMetadata(inputStream)

    fun extractVideoFrame(context: Context, videoUri: Uri, timeMs: Long): Bitmap? =
        VideoProcessor.extractVideoFrame(context, videoUri, timeMs)

    fun getVideoDuration(context: Context, videoUri: Uri): Long =
        VideoProcessor.getVideoDuration(context, videoUri)

    fun buildMpfSegment(totalJpegSize: Int): ByteArray =
        LivePhotoProtocolPacker.buildMpfSegment(totalJpegSize)

    fun wrapXmpPayload(xmpContent: String): ByteArray =
        LivePhotoProtocolPacker.wrapXmpPayload(xmpContent)

    fun buildXiaomiMotionPhotoXmp(videoLength: Int, presentationTimestampUs: Long = 0L): ByteArray =
        LivePhotoProtocolPacker.buildXiaomiMotionPhotoXmp(videoLength, presentationTimestampUs)

    fun buildOppoXmp(videoLength: Int, presentationTimestampUs: Long = 0L): ByteArray =
        LivePhotoProtocolPacker.buildOppoXmp(videoLength, presentationTimestampUs)

    fun buildFusionXmp(videoLength: Int, presentationTimestampUs: Long = 0L): ByteArray =
        LivePhotoProtocolPacker.buildFusionXmp(videoLength, presentationTimestampUs)

    fun buildXmpSegment(videoLength: Int, presentationTimestampUs: Long = 0L): ByteArray =
        LivePhotoProtocolPacker.buildFusionXmp(videoLength, presentationTimestampUs)

    fun buildOplusFallbackExifSegment(userComment: String = "{\"oplustag\":8388608}"): ByteArray =
        LivePhotoProtocolPacker.buildOplusFallbackExifSegment(userComment)

    fun injectLivePhotoMetadata(
        jpegBytes: ByteArray,
        videoLength: Int,
        presentationTimestampUs: Long = 0L,
        brandMode: LivePhotoBrandMode = LivePhotoBrandMode.FUSION
    ): ByteArray =
        LivePhotoProtocolPacker.injectLivePhotoMetadata(jpegBytes, videoLength, presentationTimestampUs, brandMode)

    fun packageLivePhoto(
        coverFile: File,
        videoFile: File,
        outputFile: File,
        presentationTimestampUs: Long = 0L,
        brandMode: LivePhotoBrandMode = LivePhotoBrandMode.FUSION,
        coverMs: Int = 0,
        durationMs: Int = 1500
    ): Boolean =
        LivePhotoProtocolPacker.packageLivePhoto(coverFile, videoFile, outputFile, presentationTimestampUs, brandMode)

    fun packageMotionPhoto(
        coverFile: File,
        videoFile: File,
        outputFile: File,
        presentationTimestampUs: Long = 0L
    ): Boolean =
        LivePhotoProtocolPacker.packageLivePhoto(coverFile, videoFile, outputFile, presentationTimestampUs, LivePhotoBrandMode.FUSION)

    fun trimVideo(context: Context, inputUri: Uri, outputFile: File, startMs: Long, endMs: Long): Boolean =
        VideoProcessor.trimVideo(context, inputUri, outputFile, startMs, endMs)

    suspend fun encodeImagesToVideo(context: Context, imageUris: List<Uri>, outputFile: File, durationMsPerFrame: Long = 300): Boolean =
        VideoProcessor.encodeImagesToVideo(context, imageUris, outputFile, durationMsPerFrame)
}
