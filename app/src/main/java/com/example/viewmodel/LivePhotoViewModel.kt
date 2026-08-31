package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    // Theme States
    private val _themeMode = MutableStateFlow(sharedPrefs.getInt("theme_mode", 0)) // 0: System, 1: Light, 2: Dark
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

    // 1. Live Photos Flow

    val allLivePhotos: StateFlow<List<LivePhotoRecord>> = livePhotoDao.getAllLivePhotos()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Video to Live UI States
    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri = _selectedVideoUri.asStateFlow()

    private val _videoDuration = MutableStateFlow(0L)
    val videoDuration = _videoDuration.asStateFlow()

    private val _videoFrames = MutableStateFlow<List<Bitmap>>(emptyList())
    val videoFrames = _videoFrames.asStateFlow()

    private val _trimStartMs = MutableStateFlow(0L)
    val trimStartMs = _trimStartMs.asStateFlow()

    private val _trimEndMs = MutableStateFlow(3000L) // Default 3s
    val trimEndMs = _trimEndMs.asStateFlow()

    private val _extractedCoverFrame = MutableStateFlow<Bitmap?>(null)
    val extractedCoverFrame = _extractedCoverFrame.asStateFlow()

    private val _customCoverUri = MutableStateFlow<Uri?>(null)
    val customCoverUri = _customCoverUri.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _generationResult = MutableStateFlow<String?>(null)
    val generationResult = _generationResult.asStateFlow()

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

    // Set video for conversion
    fun selectVideoForTrim(context: Context, uri: Uri?) {
        if (uri == null) {
            _selectedVideoUri.value = null
            clearVideoFrames()
            clearExtractedFrame()
            return
        }
        clearVideoFrames()
        clearExtractedFrame()
        _selectedVideoUri.value = uri
        _customCoverUri.value = null
        _generationResult.value = null

        viewModelScope.launch {
            val duration = withContext(Dispatchers.IO) {
                MotionPhotoHelper.getVideoDuration(context, uri)
            }
            _videoDuration.value = duration
            _trimStartMs.value = 0L
            _trimEndMs.value = duration

            // Extract initial frame at 0ms as cover
            extractFrameAt(context, 0L)
            extractPreviewFrames(context, uri)
        }
    }

    fun setTrimStart(startMs: Long) {
        val maxDuration = _videoDuration.value
        _trimStartMs.value = startMs.coerceIn(0L, _trimEndMs.value)
    }

    fun setTrimEnd(endMs: Long) {
        val maxDuration = _videoDuration.value
        _trimEndMs.value = endMs.coerceIn(_trimStartMs.value, maxDuration)
    }

    fun updateTrimStart(startMs: Long) {
        val maxDuration = _videoDuration.value
        _trimStartMs.value = startMs.coerceIn(0L, _trimEndMs.value)
    }

    fun updateTrimEnd(endMs: Long) {
        val maxDuration = _videoDuration.value
        _trimEndMs.value = endMs.coerceIn(_trimStartMs.value, maxDuration)
    }

    private fun extractPreviewFrames(context: Context, uri: Uri) {
        viewModelScope.launch {
            val duration = _videoDuration.value
            if (duration <= 0) return@launch
            val frames = mutableListOf<Bitmap>()
            val steps = 5
            val stepSize = duration / steps
            withContext(Dispatchers.IO) {
                for (i in 0 until steps) {
                    val timeMs = i * stepSize
                    val bitmap = MotionPhotoHelper.extractVideoFrame(context, uri, timeMs)
                    if (bitmap != null) {
                        frames.add(bitmap)
                    }
                }
            }
            _videoFrames.value = frames
        }
    }

    fun extractFrameAt(context: Context, timeMs: Long) {
        val uri = _selectedVideoUri.value ?: return
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                MotionPhotoHelper.extractVideoFrame(context, uri, timeMs)
            }
            if (bitmap != null) {
                _extractedCoverFrame.value = bitmap
            }
        }
    }

    // Batch Delete Records
    fun deleteLivePhotos(records: List<LivePhotoRecord>) {
        if (records.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                records.forEach { record ->
                    try {
                        File(record.coverPath).delete()
                        File(record.videoPath).delete()
                    } catch (e: Exception) {
                        // Ignore file deletion errors to ensure DB delete continues
                    }
                }
                livePhotoDao.deleteLivePhotos(records)
            }
        }
    }

    fun setCustomCover(uri: Uri) {
        _customCoverUri.value = uri
    }

    /**
     * Converts the selected video segment and cover photo into a fully working,
     * persistent Live Photo (can be single-file embedded motion photo or dual-file paired).
     */
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

                // 1. Save Cover Image
                val coverFile = File(dir, "${timestamp}_cover.jpg")
                if (customCover != null) {
                    // Copy from custom chosen URI
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(customCover)?.use { input ->
                            coverFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                } else if (extractedFrame != null) {
                    // Compress extracted bitmap frame
                    withContext(Dispatchers.IO) {
                        FileOutputStream(coverFile).use { out ->
                            extractedFrame.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                    }
                } else {
                    throw IllegalStateException("没有可用的封面图片")
                }

                // 2. Trim and Save Video segment
                val videoFile = File(dir, "${timestamp}_video.mp4")
                val trimmedSuccess = withContext(Dispatchers.IO) {
                    MotionPhotoHelper.trimVideo(context, videoUri, videoFile, start, end)
                }

                if (!trimmedSuccess) {
                    throw IllegalStateException("视频片段截取失败")
                }

                // 3. Package as combined Single Motion Photo (JPEG with appended MP4)
                val combinedFile = File(dir, "${timestamp}_motion.jpg")
                val packaged = withContext(Dispatchers.IO) {
                    MotionPhotoHelper.packageMotionPhoto(coverFile, videoFile, combinedFile)
                }

                // 4. Insert into Local Database so it appears in Live Library
                val record = LivePhotoRecord(
                    title = title.ifEmpty { "实况照片_$timestamp" },
                    coverPath = if (packaged) combinedFile.absolutePath else coverFile.absolutePath,
                    videoPath = videoFile.absolutePath,
                    timestamp = timestamp,
                    isEmbedded = packaged
                )

                withContext(Dispatchers.IO) {
                    livePhotoDao.insertLivePhoto(record)
                }

                // 5. Save to Gallery (MediaStore)
                withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "${title.ifEmpty { "实况照片_$timestamp" }}.jpg")
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

                _isGenerating.value = false
                _generationResult.value = "生成实况照片成功！"
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "已保存至库与系统相册", android.widget.Toast.LENGTH_SHORT).show()
                }
                // Reset state
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

    /**
     * Imports a user-selected JPEG, parses if it contains an embedded manufacturer motion video,
     * extracts it, saves both to permanent cache, and catalogs it.
     */
    fun importMotionPhoto(context: Context, imageUri: Uri, title: String) {
        _isGenerating.value = true
        _generationResult.value = null

        viewModelScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val dir = File(context.filesDir, "live_photos").apply { mkdirs() }

                // Check if it's actually a motion photo with embedded video
                val isMotion = withContext(Dispatchers.IO) {
                    MotionPhotoHelper.isMotionPhoto(context, imageUri)
                }

                val coverFile = File(dir, "${timestamp}_cover.jpg")
                val videoFile = File(dir, "${timestamp}_video.mp4")

                // Copy cover image bytes
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(imageUri)?.use { input ->
                        coverFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                var isSuccess = false
                if (isMotion) {
                    // Extract embedded video stream
                    isSuccess = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(imageUri)?.use { input ->
                            MotionPhotoHelper.extractVideoFromMotionPhoto(input, videoFile)
                        } ?: false
                    }
                }

                if (isSuccess) {
                    // Save combined version too
                    val combinedFile = File(dir, "${timestamp}_motion.jpg")
                    withContext(Dispatchers.IO) {
                        coverFile.copyTo(combinedFile, overwrite = true)
                    }

                    val record = LivePhotoRecord(
                        title = title.ifEmpty { "实况导入_$timestamp" },
                        coverPath = combinedFile.absolutePath,
                        videoPath = videoFile.absolutePath,
                        timestamp = timestamp,
                        isEmbedded = true
                    )

                    withContext(Dispatchers.IO) {
                        livePhotoDao.insertLivePhoto(record)
                    }
                    _generationResult.value = "成功解析并导入实况照片！"
                } else {
                    // If no embedded video, save as a plain image (or alert user)
                    _generationResult.value = "解析失败: 该图片未包含各大厂商的嵌入式实况视频，将以普通图片保存，或使用双选模式播放。"
                    // Still insert so they can use it or pair it
                    val record = LivePhotoRecord(
                        title = title.ifEmpty { "普通照片_$timestamp" },
                        coverPath = coverFile.absolutePath,
                        videoPath = "", // No video
                        timestamp = timestamp,
                        isEmbedded = false
                    )
                    withContext(Dispatchers.IO) {
                        livePhotoDao.insertLivePhoto(record)
                    }
                }

                _isGenerating.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Error importing motion photo", e)
                _isGenerating.value = false
                _generationResult.value = "导入失败: ${e.localizedMessage ?: "未知错误"}"
            }
        }
    }

    /**
     * Manually pairs separate Image and Video files (such as Apple Live Photo standard HEIC/JPG + MOV)
     * and saves them permanently in the app gallery directory.
     */
    fun pairManualLivePhoto(context: Context, title: String, coverUri: Uri, videoUri: Uri) {
        _isGenerating.value = true
        _generationResult.value = null

        viewModelScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val dir = File(context.filesDir, "live_photos").apply { mkdirs() }

                val coverFile = File(dir, "${timestamp}_cover.jpg")
                val videoFile = File(dir, "${timestamp}_video.mp4")

                // Copy cover image
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(coverUri)?.use { input ->
                        coverFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // Copy video
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(videoUri)?.use { input ->
                        videoFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // Try to create a packaged single file as well, for Android compatibility
                val combinedFile = File(dir, "${timestamp}_motion.jpg")
                val packaged = withContext(Dispatchers.IO) {
                    val coverBytes = coverFile.readBytes()
                    val videoBytes = videoFile.readBytes()
                    MotionPhotoHelper.packageMotionPhoto(coverBytes, videoBytes, combinedFile)
                }

                val record = LivePhotoRecord(
                    title = title.ifEmpty { "手动配对_$timestamp" },
                    coverPath = if (packaged) combinedFile.absolutePath else coverFile.absolutePath,
                    videoPath = videoFile.absolutePath,
                    timestamp = timestamp,
                    isEmbedded = packaged
                )

                withContext(Dispatchers.IO) {
                    livePhotoDao.insertLivePhoto(record)
                }

                _isGenerating.value = false
                _generationResult.value = "实况配对并保存成功！"
            } catch (e: Exception) {
                Log.e(TAG, "Error pairing files", e)
                _isGenerating.value = false
                _generationResult.value = "配对失败: ${e.localizedMessage ?: "未知错误"}"
            }
        }
    }

    // Delete Record
    fun deleteLivePhoto(record: LivePhotoRecord) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Delete actual files from internal directory
                try {
                    File(record.coverPath).delete()
                    File(record.videoPath).delete()
                } catch (e: Exception) {
                    // Ignore
                }
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
                            android.widget.Toast.makeText(context, "已保存到相册", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "保存到相册失败", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving to gallery", e)
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "保存失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
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
                
                // Truncate at FFD9
                var endOfJpeg = inputBytes.size
                for (i in inputBytes.size - 2 downTo 0) {
                    if (inputBytes[i] == 0xFF.toByte() && inputBytes[i + 1] == 0xD9.toByte()) {
                        endOfJpeg = i + 2
                        break
                    }
                }
                
                // If it already has XMP, we could strip it, but for simplicity we just append a new APP1 before the rest.
                // A more robust way is just re-running our injectXMPIntoJPEG logic.
                val namespace = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.UTF_8)
                val xmpBytes = xmpString.toByteArray(Charsets.UTF_8)
                val payloadSize = namespace.size + xmpBytes.size
                val markerSize = payloadSize + 2
                
                val out = java.io.ByteArrayOutputStream(endOfJpeg + markerSize + 2)
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
                // Write any appended video if present (after FFD9)
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

    fun diagnoseVideo(context: Context, imageUri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Read the bytes
                val inputBytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                if (inputBytes == null) {
                    withContext(Dispatchers.Main) { onResult("读取图片失败") }
                    return@launch
                }
                // 2. Find FFD9
                var endOfJpeg = -1
                for (i in inputBytes.size - 2 downTo 0) {
                    if (inputBytes[i] == 0xFF.toByte() && inputBytes[i + 1] == 0xD9.toByte()) {
                        endOfJpeg = i + 2
                        break
                    }
                }
                if (endOfJpeg == -1 || endOfJpeg == inputBytes.size) {
                    withContext(Dispatchers.Main) { onResult("未找到追加的视频数据 (无 FF D9 或其后没有数据)") }
                    return@launch
                }
                
                // 3. Write video to temp file
                val videoBytes = inputBytes.sliceArray(endOfJpeg until inputBytes.size)
                val tempVideo = java.io.File(context.cacheDir, "temp_diag_video.mp4")
                tempVideo.writeBytes(videoBytes)
                
                // 4. Diagnose
                val desc = MotionPhotoHelper.diagnoseVideoFormat(context, android.net.Uri.fromFile(tempVideo))
                
                withContext(Dispatchers.Main) {
                    onResult("视频大小: ${videoBytes.size} 字节\\n$desc\\n如果编码不兼容，请使用外部工具转码后再配对。")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult("诊断错误: ${e.message}") }
            }
        }
    }

    fun transcodeAndRepairLivePhoto(context: Context, imageUri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { onResult("正在提取和转码，请稍候...") }
                
                val inputBytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                if (inputBytes == null) {
                    withContext(Dispatchers.Main) { onResult("读取图片失败") }
                    return@launch
                }
                
                var endOfJpeg = -1
                for (i in inputBytes.size - 2 downTo 0) {
                    if (inputBytes[i] == 0xFF.toByte() && inputBytes[i + 1] == 0xD9.toByte()) {
                        endOfJpeg = i + 2
                        break
                    }
                }
                if (endOfJpeg == -1 || endOfJpeg == inputBytes.size) {
                    withContext(Dispatchers.Main) { onResult("未发现内嵌视频，无法转码") }
                    return@launch
                }
                
                val coverBytes = inputBytes.sliceArray(0 until endOfJpeg)
                val videoBytes = inputBytes.sliceArray(endOfJpeg until inputBytes.size)
                
                val tempVideo = java.io.File(context.cacheDir, "temp_diag_video.mp4")
                tempVideo.writeBytes(videoBytes)
                
                val transcodedVideo = java.io.File(context.cacheDir, "transcoded_video.mp4")
                if (transcodedVideo.exists()) transcodedVideo.delete()
                
                val success = MotionPhotoHelper.transcodeVideoToAVC(context, android.net.Uri.fromFile(tempVideo), transcodedVideo)
                
                if (success) {
                    val newVideoBytes = transcodedVideo.readBytes()
                    val outDir = java.io.File(context.filesDir, "live_photos")
                    val outFile = java.io.File(outDir, "repaired_motion_${System.currentTimeMillis()}.jpg")
                    
                    val packaged = MotionPhotoHelper.packageMotionPhoto(coverBytes, newVideoBytes, outFile)
                    if (packaged) {
                        withContext(Dispatchers.Main) { onResult("转码修复成功！\n文件已保存: ${outFile.name}") }
                        
                        val record = com.example.data.LivePhotoRecord(
                            title = "转码修复_${System.currentTimeMillis()}",
                            coverPath = outFile.absolutePath,
                            videoPath = transcodedVideo.absolutePath,
                            timestamp = System.currentTimeMillis(),
                            isEmbedded = true
                        )
                        livePhotoDao.insertLivePhoto(record)

                    } else {
                        withContext(Dispatchers.Main) { onResult("转码成功，但重新打包失败") }
                    }
                } else {
                    withContext(Dispatchers.Main) { onResult("视频转码失败") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult("转码过程发生错误: ${e.message}") }
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
                
                // Copy images to local cache to avoid URI permission issues in Media3
                val localImageUris = withContext(Dispatchers.IO) {
                    imageUris.mapIndexed { index, uri ->
                        val localFile = File(dir, "temp_img_${timestamp}_${index}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            localFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        android.net.Uri.fromFile(localFile)
                    }
                }
                
                val encodeSuccess = MotionPhotoHelper.encodeImagesToVideo(context, localImageUris, videoFile)
                
                // Clean up temp images
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
                
                // Insert into Local Database so it appears in Live Photo Library
                val record = LivePhotoRecord(
                    title = title.ifEmpty { "实况图集_$timestamp" },
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
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "${title.ifEmpty { "实况图集_$timestamp" }}.jpg")
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
                            android.widget.Toast.makeText(context, "已成功保存至系统相册", android.widget.Toast.LENGTH_SHORT).show()
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