package com.example.util

import android.content.Context
import android.util.Log
import okhttp3.Credentials
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object WebDavClient {
    private const val TAG = "WebDavClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Checks if we can successfully connect and authenticate to the WebDAV server.
     * Uses the OPTIONS method which lists capabilities and is very lightweight.
     */
    fun testConnection(url: String, user: String, pass: String): Result<Boolean> {
        val cleanUrl = url.trim().trimEnd('/')
        val credential = Credentials.basic(user, pass)
        
        val request = Request.Builder()
            .url(cleanUrl)
            .method("OPTIONS", null)
            .header("Authorization", credential)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 200 || response.code == 207) {
                    Result.success(true)
                } else {
                    Result.failure(IOException("HTTP error code: ${response.code}, message: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV Connection Test failed", e)
            Result.failure(e)
        }
    }

    /**
     * Extracts this app's own APK file and uploads it to WebDAV server with custom progress callback.
     */
    fun uploadApk(
        context: Context,
        serverUrl: String,
        user: String,
        pass: String,
        onProgress: (Float) -> Unit
    ): Result<String> {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val credential = Credentials.basic(user, pass)

        // Get own APK path
        val apkPath = context.applicationInfo.sourceDir
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            return Result.failure(java.io.FileNotFoundException("Could not find own APK source path!"))
        }

        // Target URL
        val targetUrl = "$cleanUrl/实况照片_安装包.apk"
        Log.d(TAG, "Uploading APK from $apkPath to $targetUrl, file size: ${apkFile.length()} bytes")

        val mediaType = "application/vnd.android.package-archive".toMediaTypeOrNull()

        // Create a custom request body to track upload progress
        val requestBody = object : RequestBody() {
            override fun contentType(): MediaType? = mediaType
            override fun contentLength(): Long = apkFile.length()

            override fun writeTo(sink: BufferedSink) {
                apkFile.source().use { source ->
                    var totalWritten: Long = 0
                    val bufferSize = 2048L
                    var readCount: Long
                    val contentLength = contentLength()

                    while (source.read(sink.buffer, bufferSize).also { readCount = it } != -1L) {
                        sink.flush()
                        totalWritten += readCount
                        val progress = if (contentLength > 0) totalWritten.toFloat() / contentLength else 0f
                        onProgress(progress)
                    }
                }
            }
        }

        val request = Request.Builder()
            .url(targetUrl)
            .put(requestBody)
            .header("Authorization", credential)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 200 || response.code == 201 || response.code == 204) {
                    Log.d(TAG, "APK upload completed successfully")
                    Result.success(targetUrl)
                } else {
                    Result.failure(IOException("Upload failed. HTTP code: ${response.code}, message: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV Upload failed", e)
            Result.failure(e)
        }
    }
}
