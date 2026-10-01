package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.protocol.LivePhotoBrandMode
import com.example.data.AppDatabase
import com.example.data.LivePhotoRecord
import com.example.util.MotionPhotoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class LivePhotoViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "LivePhotoViewModel"
    private val db = AppDatabase.getDatabase(application)
    private val livePhotoDao = db.livePhotoDao()
    private val sharedPrefs = application.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(sharedPrefs.getInt("theme_mode", 0))
    val themeMode = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(sharedPrefs.getBoolean("dynamic_color", true))
    val dynamicColor = _dynamicColor.asStateFlow()

    private val _themeColor = MutableStateFlow(sharedPrefs.getString("theme_color", "default") ?: "default")
    val themeColor = _themeColor.asStateFlow()

    private val _autoPlayLivePhoto = MutableStateFlow(sharedPrefs.getBoolean("auto_play", true))
    val autoPlayLivePhoto = _autoPlayLivePhoto.asStateFlow()

    fun setAutoPlayLivePhoto(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("auto_play", enabled).apply()
        _autoPlayLivePhoto.value = enabled
    }

    private val _language = MutableStateFlow(sharedPrefs.getString("language", "system") ?: "system")
    val language = _language.asStateFlow()

    private fun detectDefaultBrandMode(): LivePhotoBrandMode {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val brand = android.os.Build.BRAND.lowercase()
        return when {
            manufacturer.contains("oppo") || manufacturer.contains("oneplus") ||
            brand.contains("oppo") || brand.contains("oneplus") || brand.contains("realme") -> LivePhotoBrandMode.OPPO
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") || brand.contains("redmi") -> LivePhotoBrandMode.XIAOMI
            else -> LivePhotoBrandMode.FUSION
        }
    }

    private val _brandMode = MutableStateFlow(
        if (sharedPrefs.contains("brand_mode")) {
            LivePhotoBrandMode.fromId(sharedPrefs.getInt("brand_mode", 0))
        } else {
            detectDefaultBrandMode()
        }
    )
    val brandMode = _brandMode.asStateFlow()

    fun setBrandMode(mode: LivePhotoBrandMode) {
        sharedPrefs.edit().putInt("brand_mode", mode.id).apply()
        _brandMode.value = mode
    }

    fun setThemeMode(mode: Int) {
        sharedPrefs.edit().putInt("theme_mode", mode).apply()
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("dynamic_color", enabled).apply()
        _dynamicColor.value = enabled
    }

    fun setThemeColor(color: String) {
        sharedPrefs.edit().putString("theme_color", color).apply()
        _themeColor.value = color
    }

    fun setLanguage(lang: String) {
        sharedPrefs.edit().putString("language", lang).apply()
        _language.value = lang
    }

    val allLivePhotos: StateFlow<List<LivePhotoRecord>> = livePhotoDao.getAllLivePhotos()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri = _selectedVideoUri.asStateFlow()

    private val _videoDuration = MutableStateFlow(0L)
    val videoDuration = _videoDuration.asStateFlow()

    private val _videoFrames = MutableStateFlow<List<Bitmap>>(emptyList())
    val videoFrames = _videoFrames.asStateFlow()

    private val _trimStartMs = MutableStateFlow(0L)
    val trimStartMs = _trimStartMs.asStateFlow()

    private val _trimEndMs = MutableStateFlow(3000L)
    val trimEndMs = _trimEndMs.asStateFlow()

    private val _extractedCoverFrame = MutableStateFlow<Bitmap?>(null)
    val extractedCoverFrame = _extractedCoverFrame.asStateFlow()

    private val _customCoverUri = MutableStateFlow<Uri?>(null)
    val customCoverUri = _customCoverUri.asStateFlow()

    private val _coverTimeMs = MutableStateFlow<Long>(0L)
    val coverTimeMs = _coverTimeMs.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _generationResult = MutableStateFlow<String?>(null)
    val generationResult = _generationResult.asStateFlow()

    private val _defaultTitle = MutableStateFlow("")
    val defaultTitle = _defaultTitle.asStateFlow()

    fun queryFileName(context: Context, uri: Uri?): String {
        if (uri == null) return ""
        var name = ""
        try {
            if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) name = it.getString(idx) ?: ""
                    }
                }
            }
        } catch (_: Exception) {}
        if (name.isEmpty()) {
            name = uri.lastPathSegment ?: ""
        }
        val dotIdx = name.lastIndexOf('.')
        return if (dotIdx > 0) name.substring(0, dotIdx) else name
    }

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val photos = livePhotoDao.getAllLivePhotosList()
                    val context = getApplication<Application>().applicationContext
                    for (photo in photos) {
                        val videoFile = File(photo.videoPath)
                        if (videoFile.exists() && videoFile.length() > 0) {
                            val videoUri = Uri.fromFile(videoFile)
                            val refreshedFrame = MotionPhotoHelper.extractVideoFrame(context, videoUri, 0L)
                            if (refreshedFrame != null) {
                                val coverFile = File(photo.coverPath)
                                FileOutputStream(coverFile).use { out ->
                                    refreshedFrame.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error healing covers: ${e.message}")
                }
            }
        }
    }

    private fun clearVideoFrames() {
        val old = _videoFrames.value
        _videoFrames.value = emptyList()
        old.forEach { if (!it.isRecycled) it.recycle() }
    }

    private fun clearExtractedFrame() {
        val old = _extractedCoverFrame.value
        _extractedCoverFrame.value = null
        if (old != null && !old.isRecycled) old.recycle()
    }

    fun selectVideoForTrim(context: Context, uri: Uri?) {
        if (uri == null) {
            _selectedVideoUri.value = null
            _defaultTitle.value = ""
            clearVideoFrames()
            clearExtractedFrame()
            return
        }
        clearVideoFrames()
        clearExtractedFrame()
        _selectedVideoUri.value = uri
        _defaultTitle.value = queryFileName(context, uri)
        _customCoverUri.value = null
        _generationResult.value = null
        com.example.util.DebugLogManager.interaction("ConvertScreen", "Selected video: $uri, defaultTitle=${_defaultTitle.value}")

        viewModelScope.launch {
            val duration = withContext(Dispatchers.IO) {
                MotionPhotoHelper.getVideoDuration(context, uri)
            }
            _videoDuration.value = duration
            _trimStartMs.value = 0L
            val defaultEnd = if (duration > 0L) minOf(duration, 3000L) else 3000L
            _trimEndMs.value = defaultEnd
            _coverTimeMs.value = 0L

            extractFrameAt(context, 0L)
            extractPreviewFrames(context, uri)
        }
    }

    fun setTrimStart(startMs: Long) {
        val newStart = startMs.coerceIn(0L, _trimEndMs.value)
        _trimStartMs.value = newStart
        if (_coverTimeMs.value < newStart || _coverTimeMs.value > _trimEndMs.value) {
            _coverTimeMs.value = newStart
            extractFrameAt(getApplication(), newStart)
        }
    }

    fun setTrimEnd(endMs: Long) {
        val maxDuration = _videoDuration.value
        val newEnd = endMs.coerceIn(_trimStartMs.value, maxDuration)
        _trimEndMs.value = newEnd
        if (_coverTimeMs.value < _trimStartMs.value || _coverTimeMs.value > newEnd) {
            _coverTimeMs.value = _trimStartMs.value
            extractFrameAt(getApplication(), _trimStartMs.value)
        }
    }

    fun updateTrimRange(startMs: Long, endMs: Long) {
        val maxDuration = _videoDuration.value
        val validDuration = if (maxDuration > 0L) maxDuration else Long.MAX_VALUE
        val s = startMs.coerceIn(0L, validDuration)
        val e = endMs.coerceIn(s, validDuration)
        _trimStartMs.value = s
        _trimEndMs.value = e
        if (_coverTimeMs.value < s || _coverTimeMs.value > e) {
            _coverTimeMs.value = s
        }
    }

    fun updateTrimStart(startMs: Long) {
        val newStart = startMs.coerceIn(0L, _trimEndMs.value)
        _trimStartMs.value = newStart
        if (_coverTimeMs.value < newStart || _coverTimeMs.value > _trimEndMs.value) {
            _coverTimeMs.value = newStart
        }
    }

    fun updateTrimEnd(endMs: Long) {
        val maxDuration = _videoDuration.value
        val validDuration = if (maxDuration > 0L) maxDuration else Long.MAX_VALUE
        val newEnd = endMs.coerceIn(_trimStartMs.value, validDuration)
        _trimEndMs.value = newEnd
        if (_coverTimeMs.value < _trimStartMs.value || _coverTimeMs.value > newEnd) {
            _coverTimeMs.value = _trimStartMs.value
        }
    }

    private fun extractPreviewFrames(context: Context, uri: Uri) {
        viewModelScope.launch {
            val duration = _videoDuration.value
            if (duration <= 0) return@launch
            val frames = withContext(Dispatchers.IO) {
                com.example.core.media.VideoThumbnailCache.prefetchThumbnails(
                    context = context,
                    uri = uri,
                    durationMs = duration,
                    count = 8,
                    thumbWidth = 140,
                    thumbHeight = 140
                )
            }
            _videoFrames.value = frames
        }
    }

    fun extractFrameAt(context: Context, timeMs: Long) {
        val uri = _selectedVideoUri.value ?: return
        _coverTimeMs.value = timeMs
        val cached = com.example.core.media.VideoThumbnailCache.getFromMemory(uri, timeMs)
        if (cached != null && !cached.isRecycled) {
            _extractedCoverFrame.value = cached
            return
        }
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                com.example.core.media.VideoThumbnailCache.getOrExtractFrame(context, uri, timeMs)
            }
            if (bitmap != null) {
                _extractedCoverFrame.value = bitmap
            }
        }
    }

    fun setCustomCover(uri: Uri) {
        _customCoverUri.value = uri
    }

    fun generateLivePhoto(context: Context, title: String) {
        val videoUri = _selectedVideoUri.value ?: return
        val start = _trimStartMs.value
        val end = _trimEndMs.value
        val customCover = _customCoverUri.value
        val extractedFrame = _extractedCoverFrame.value

        _isGenerating.value = true
        _generationResult.value = null

        viewModelScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val dir = File(context.filesDir, "live_photos").apply { mkdirs() }

                val coverFile = File(dir, "${timestamp}_cover.jpg")
                if (customCover != null) {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(customCover)?.use { input ->
                            coverFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                } else {
                    val targetCoverTime = _coverTimeMs.value.coerceIn(start, end)
                    val coverBmp = withContext(Dispatchers.IO) {
                        MotionPhotoHelper.extractVideoFrame(context, videoUri, targetCoverTime)
                            ?: extractedFrame
                    }
                    if (coverBmp != null) {
                        withContext(Dispatchers.IO) {
                            FileOutputStream(coverFile).use { out ->
                                coverBmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                            }
                        }
                    } else {
                        throw IllegalStateException("没有可用的封面图片")
                    }
                }

                val videoFile = File(dir, "${timestamp}_video.mp4")
                val trimmedSuccess = withContext(Dispatchers.IO) {
                    MotionPhotoHelper.trimVideo(context, videoUri, videoFile, start, end)
                }

                if (!trimmedSuccess) {
                    throw IllegalStateException("视频片段截取失败")
                }

                val combinedFile = File(dir, "${timestamp}_motion.jpg")
                val coverTime = _coverTimeMs.value
                val relativeCoverMs = (coverTime - start).coerceAtLeast(0L)
                val presentationTimestampUs = relativeCoverMs * 1000L
                val currentBrandMode = _brandMode.value
                val clipDurationMs = (end - start).toInt()
                val packaged = withContext(Dispatchers.IO) {
                    MotionPhotoHelper.packageLivePhoto(
                        coverFile = coverFile,
                        videoFile = videoFile,
                        outputFile = combinedFile,
                        presentationTimestampUs = presentationTimestampUs,
                        brandMode = currentBrandMode,
                        coverMs = relativeCoverMs.toInt(),
                        durationMs = clipDurationMs
                    )
                }

                val resolvedTitle = title.ifBlank { _defaultTitle.value.ifBlank { "LivePhoto_$timestamp" } }

                val record = LivePhotoRecord(
                    title = resolvedTitle,
                    coverPath = if (packaged) combinedFile.absolutePath else coverFile.absolutePath,
                    videoPath = videoFile.absolutePath,
                    timestamp = timestamp,
                    isEmbedded = packaged
                )

                withContext(Dispatchers.IO) {
                    livePhotoDao.insertLivePhoto(record)
                }

                withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$resolvedTitle.jpg")
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/LivePhotos")
                    }
                    val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { outStream ->
                            if (packaged) {
                                combinedFile.inputStream().use { input -> input.copyTo(outStream) }
                            } else {
                                coverFile.inputStream().use { input -> input.copyTo(outStream) }
                            }
                        }
                    }
                }

                com.example.util.DebugLogManager.interaction("ConvertScreen", "Generated LivePhoto: $resolvedTitle, duration=${end - start}ms, packaged=$packaged")

                _isGenerating.value = false
                _generationResult.value = "生成实况照片成功！"
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "已保存至库与系统相册", Toast.LENGTH_SHORT).show()
                }
                _selectedVideoUri.value = null
                _customCoverUri.value = null
                _extractedCoverFrame.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error generating live photo", e)
                _isGenerating.value = false
                _generationResult.value = "生成失败: ${e.localizedMessage ?: "未知错误"}"
            }
        }
    }

    fun importMotionPhoto(context: Context, imageUri: Uri, title: String) {
        _isGenerating.value = true
        _generationResult.value = null

        viewModelScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val dir = File(context.filesDir, "live_photos").apply { mkdirs() }

                val isMotion = withContext(Dispatchers.IO) {
                    MotionPhotoHelper.isMotionPhoto(context, imageUri)
                }

                val coverFile = File(dir, "${timestamp}_cover.jpg")
                val videoFile = File(dir, "${timestamp}_video.mp4")

                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(imageUri)?.use { input ->
                        coverFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                var isSuccess = false
                if (isMotion) {
                    isSuccess = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(imageUri)?.use { input ->
                            MotionPhotoHelper.extractVideoFromMotionPhoto(input, videoFile)
                        } ?: false
                    }
                }

                if (isSuccess) {
                    val combinedFile = File(dir, "${timestamp}_motion.jpg")
                    withContext(Dispatchers.IO) {
                        coverFile.copyTo(combinedFile, overwrite = true)
                    }

                    val resolvedTitle = title.ifBlank { queryFileName(context, imageUri).ifBlank { "MotionPhoto_$timestamp" } }
                    val record = LivePhotoRecord(
                        title = resolvedTitle,
                        coverPath = combinedFile.absolutePath,
                        videoPath = videoFile.absolutePath,
                        timestamp = timestamp,
                        isEmbedded = true
                    )

                    withContext(Dispatchers.IO) {
                        livePhotoDao.insertLivePhoto(record)
                    }
                    com.example.util.DebugLogManager.interaction("LibraryScreen", "Imported MotionPhoto: $resolvedTitle")
                    _generationResult.value = "成功解析并导入实况照片！"
                } else {
                    _generationResult.value = "解析失败: 该图片未包含各大厂商的嵌入式实况视频，将以普通图片保存，或使用双选模式播放。"
                    val resolvedTitle = title.ifBlank { queryFileName(context, imageUri).ifBlank { "Photo_$timestamp" } }
                    val record = LivePhotoRecord(
                        title = resolvedTitle,
                        coverPath = coverFile.absolutePath,
                        videoPath = "",
                        timestamp = timestamp,
                        isEmbedded = false
                    )
                    withContext(Dispatchers.IO) {
                        livePhotoDao.insertLivePhoto(record)
                    }
                    com.example.util.DebugLogManager.interaction("LibraryScreen", "Imported StaticPhoto: $resolvedTitle")
                }

                _isGenerating.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Error importing motion photo", e)
                _isGenerating.value = false
                _generationResult.value = "导入失败: ${e.localizedMessage ?: "未知错误"}"
            }
        }
    }

    fun pairManualLivePhoto(context: Context, title: String, coverUri: Uri, videoUri: Uri) {
        _isGenerating.value = true
        _generationResult.value = null

        viewModelScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val dir = File(context.filesDir, "live_photos").apply { mkdirs() }

                val coverFile = File(dir, "${timestamp}_cover.jpg")
                val videoFile = File(dir, "${timestamp}_video.mp4")

                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(coverUri)?.use { input ->
                        coverFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(videoUri)?.use { input ->
                        videoFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                val combinedFile = File(dir, "${timestamp}_motion.jpg")
                val currentBrandMode = _brandMode.value
                val packaged = withContext(Dispatchers.IO) {
                    MotionPhotoHelper.packageLivePhoto(
                        coverFile = coverFile,
                        videoFile = videoFile,
                        outputFile = combinedFile,
                        presentationTimestampUs = 0L,
                        brandMode = currentBrandMode
                    )
                }

                val resolvedTitle = title.ifBlank { queryFileName(context, coverUri).ifBlank { queryFileName(context, videoUri).ifBlank { "LivePhoto_$timestamp" } } }

                val record = LivePhotoRecord(
                    title = resolvedTitle,
                    coverPath = if (packaged) combinedFile.absolutePath else coverFile.absolutePath,
                    videoPath = videoFile.absolutePath,
                    timestamp = timestamp,
                    isEmbedded = packaged
                )

                withContext(Dispatchers.IO) {
                    livePhotoDao.insertLivePhoto(record)
                }

                com.example.util.DebugLogManager.interaction("ManualPairScreen", "Paired LivePhoto: $resolvedTitle, packaged=$packaged")

                _isGenerating.value = false
                _generationResult.value = "实况配对并保存成功！"
            } catch (e: Exception) {
                Log.e(TAG, "Error pairing files", e)
                _isGenerating.value = false
                _generationResult.value = "配对失败: ${e.localizedMessage ?: "未知错误"}"
            }
        }
    }

    fun deleteLivePhoto(record: LivePhotoRecord) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    File(record.coverPath).delete()
                    File(record.videoPath).delete()
                } catch (_: Exception) {}
                livePhotoDao.deleteLivePhotoById(record.id)
            }
        }
    }

    fun saveToGallery(context: Context, record: LivePhotoRecord) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "${record.title}.jpg")
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/LivePhotos")
                    }

                    val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            File(record.coverPath).inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }

                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "保存到相册失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving to gallery", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun injectCustomXmp(context: Context, imageUri: Uri, xmpString: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputBytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                if (inputBytes == null) {
                    withContext(Dispatchers.Main) { onResult(false, "读取图片失败") }
                    return@launch
                }

                var endOfJpeg = inputBytes.size
                for (i in inputBytes.size - 2 downTo 0) {
                    if (inputBytes[i] == 0xFF.toByte() && inputBytes[i + 1] == 0xD9.toByte()) {
                        endOfJpeg = i + 2
                        break
                    }
                }

                val namespace = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.UTF_8)
                var xmpBytes = xmpString.toByteArray(Charsets.UTF_8)
                if (namespace.size + xmpBytes.size + 2 > 65530) {
                    val maxLen = 65530 - namespace.size - 2
                    xmpBytes = xmpBytes.sliceArray(0 until maxLen)
                }
                val payloadSize = namespace.size + xmpBytes.size
                val markerSize = payloadSize + 2

                val out = ByteArrayOutputStream(endOfJpeg + markerSize + 2)
                out.write(0xFF)
                out.write(0xD8)
                out.write(0xFF)
                out.write(0xE1)
                out.write((markerSize shr 8) and 0xFF)
                out.write(markerSize and 0xFF)
                out.write(namespace)
                out.write(xmpBytes)
                if (inputBytes.size > 2 && inputBytes[0] == 0xFF.toByte() && inputBytes[1] == 0xD8.toByte()) {
                    out.write(inputBytes, 2, endOfJpeg - 2)
                } else {
                    out.write(inputBytes, 0, endOfJpeg)
                }
                if (endOfJpeg < inputBytes.size) {
                    out.write(inputBytes, endOfJpeg, inputBytes.size - endOfJpeg)
                }

                val title = "xmp_modified_${System.currentTimeMillis()}"
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$title.jpg")
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/LivePhotos")
                        put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(out.toByteArray())
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                        context.contentResolver.update(uri, contentValues, null, null)
                    }
                    withContext(Dispatchers.Main) {
                        onResult(true, "保存成功: 已存入系统相册 (Pictures/LivePhotos)")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(false, "保存失败: 无法创建 MediaStore 记录")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "错误: ${e.message}")
                }
            }
        }
    }

    fun generateFromMultipleImages(context: Context, imageUris: List<Uri>, title: String) {
        if (imageUris.isEmpty()) return

        _isGenerating.value = true
        _generationResult.value = null

        viewModelScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val dir = File(context.filesDir, "live_photos").apply { mkdirs() }

                val coverFile = File(dir, "${timestamp}_cover.jpg")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(imageUris.first())?.use { input ->
                        coverFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                val videoFile = File(dir, "${timestamp}_video.mp4")

                val localImageUris = withContext(Dispatchers.IO) {
                    imageUris.mapIndexed { index, uri ->
                        val localFile = File(dir, "temp_img_${timestamp}_${index}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            localFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Uri.fromFile(localFile)
                    }
                }

                val encodeSuccess = MotionPhotoHelper.encodeImagesToVideo(context, localImageUris, videoFile)

                withContext(Dispatchers.IO) {
                    localImageUris.forEach { uri ->
                        uri.path?.let { File(it).delete() }
                    }
                }

                if (!encodeSuccess) {
                    throw IllegalStateException("多图合成视频失败")
                }

                val combinedFile = File(dir, "${timestamp}_motion.jpg")
                val packaged = withContext(Dispatchers.IO) {
                    MotionPhotoHelper.packageMotionPhoto(coverFile, videoFile, combinedFile)
                }

                val resolvedTitle = title.ifBlank { queryFileName(context, imageUris.firstOrNull()).ifBlank { "LivePhoto_$timestamp" } }
                val record = LivePhotoRecord(
                    title = resolvedTitle,
                    coverPath = if (packaged) combinedFile.absolutePath else coverFile.absolutePath,
                    videoPath = videoFile.absolutePath,
                    timestamp = timestamp,
                    isEmbedded = packaged
                )
                withContext(Dispatchers.IO) {
                    livePhotoDao.insertLivePhoto(record)
                }

                withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$resolvedTitle.jpg")
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/LivePhotos")
                    }
                    val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { outStream ->
                            if (packaged) {
                                combinedFile.inputStream().use { input -> input.copyTo(outStream) }
                            } else {
                                coverFile.inputStream().use { input -> input.copyTo(outStream) }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "已成功保存至系统相册", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                _isGenerating.value = false
                _generationResult.value = "多图合成实况成功！"
            } catch (e: Exception) {
                Log.e("LivePhotoVM", "Error generating from multiple images", e)
                _isGenerating.value = false
                _generationResult.value = "生成失败: ${e.localizedMessage ?: "未知错误"}"
            }
        }
    }

    fun clearTempCache(context: Context, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var deletedBytes = 0L
                context.cacheDir.listFiles()?.forEach { file ->
                    deletedBytes += file.length()
                    file.deleteRecursively()
                }
                val mb = "%.2f".format(deletedBytes / (1024f * 1024f))
                withContext(Dispatchers.Main) {
                    onResult("已成功清理 ${mb} MB 缓存空间")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult("清理缓存失败: ${e.message}")
                }
            }
        }
    }
}
