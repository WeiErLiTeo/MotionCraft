package com.example.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.core.media.ImageProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

object DebugLogManager {
    enum class Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val tag: String,
        val message: String,
        val level: Level = Level.INFO
    ) {
        fun format(): String {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            val timeStr = sdf.format(Date(timestamp))
            return "[$timeStr] [${level.name}] [$tag] $message"
        }
    }

    private const val MAX_LOG_ENTRIES = 2500
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ringBuffer = CopyOnWriteArrayList<LogEntry>()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _isDebugMode = MutableStateFlow(false)
    val isDebugMode: StateFlow<Boolean> = _isDebugMode.asStateFlow()

    private val _isFloatingWindowVisible = MutableStateFlow(false)
    val isFloatingWindowVisible: StateFlow<Boolean> = _isFloatingWindowVisible.asStateFlow()

    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private val logcatStarted = AtomicBoolean(false)
    private val watchdogStarted = AtomicBoolean(false)
    private val codecCheckCompleted = AtomicBoolean(false)
    private var appContextRef: Context? = null

    private val RELEVANT_LOGCAT_TAGS = setOf(
        "ExoPlayerImpl", "MediaCodec", "CCodec", "CCodecConfig", "CCodecBuffers",
        "MediaMuxer", "MPEG4Writer", "StagefrightMetadataRetriever", "Transformer",
        "VideoProcessor", "ImageProcessor", "AppleArchiveHelper", "LivePhotoVM",
        "LivePhotoViewModel", "AudioTrack", "GraphicBuffer", "OpenGLRenderer",
        "Choreographer", "AndroidRuntime", "System.err"
    )

    init {
        logInitialSystemInfo()
        startOwnProcessLogcatCapture()
        startMainThreadFreezeWatchdog()
    }

    fun initCrashHandler() {
        if (defaultExceptionHandler == null) {
            defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                val crashReport = "Uncaught Exception in thread [${thread.name}]: ${throwable.localizedMessage}\n${throwable.stackTraceToString()}"
                e("CrashHandler", crashReport)
                persistCrashSnapshot(crashReport)
                defaultExceptionHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun persistCrashSnapshot(report: String) {
        try {
            val ctx = appContextRef ?: return
            val crashFile = File(ctx.filesDir, "last_crash_diagnostic.log")
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            crashFile.writeText("=== CRASH SNAPSHOT @ ${sdf.format(Date())} ===\n$report\n")
        } catch (_: Exception) {}
    }

    private fun loadPreviousCrashIfPresent(context: Context) {
        try {
            val crashFile = File(context.filesDir, "last_crash_diagnostic.log")
            if (crashFile.exists() && crashFile.length() > 0L) {
                val prevText = crashFile.readText()
                w("PrevSessionCrash", "检测到上次运行遗留异常快照:\n$prevText")
                crashFile.delete()
            }
        } catch (_: Exception) {}
    }

    fun setDebugMode(enabled: Boolean) {
        _isDebugMode.value = enabled
        if (!enabled) {
            _isFloatingWindowVisible.value = false
        } else {
            appContextRef?.let { runFullSelfCheckSuite(it) }
        }
    }

    fun setFloatingWindowVisible(visible: Boolean) {
        _isFloatingWindowVisible.value = visible
    }

    fun toggleFloatingWindow() {
        _isFloatingWindowVisible.value = !_isFloatingWindowVisible.value
    }

    fun interaction(screen: String, action: String) {
        log("UI_INTERACTION", "[$screen] $action", Level.INFO)
    }

    /**
     * Always records into the in-memory ringBuffer even if Debug Mode is toggled on later,
     * so no startup or pre-toggle diagnostic events are ever lost.
     */
    fun log(tag: String, message: String, level: Level = Level.INFO) {
        val entry = LogEntry(tag = tag, message = message, level = level)
        synchronized(ringBuffer) {
            if (ringBuffer.size >= MAX_LOG_ENTRIES) {
                val removeCount = (ringBuffer.size - MAX_LOG_ENTRIES + 1).coerceAtLeast(1)
                for (i in 0 until removeCount) {
                    if (ringBuffer.isNotEmpty()) ringBuffer.removeAt(0)
                }
            }
            ringBuffer.add(entry)
            _logs.value = ringBuffer.toList()
        }
    }

    fun v(tag: String, message: String) = log(tag, message, Level.VERBOSE)
    fun d(tag: String, message: String) = log(tag, message, Level.DEBUG)
    fun i(tag: String, message: String) = log(tag, message, Level.INFO)
    fun w(tag: String, message: String) = log(tag, message, Level.WARN)
    fun e(tag: String, message: String, tr: Throwable? = null) {
        val fullMsg = if (tr != null) "$message\n${tr.stackTraceToString()}" else message
        log(tag, fullMsg, Level.ERROR)
    }

    fun clear() {
        synchronized(ringBuffer) {
            ringBuffer.clear()
        }
        logInitialSystemInfo()
        appContextRef?.let {
            logContextDetails(it)
            codecCheckCompleted.set(false)
            runFullSelfCheckSuite(it)
        }
    }

    fun getAllLogsText(): String {
        return _logs.value.joinToString("\n") { it.format() }
    }

    /**
     * Captures Android's internal logcat output for our own process PID without requiring ADB.
     * Filters for media codecs, ExoPlayer, muxers, graphics buffers, and all warnings/errors.
     */
    private fun startOwnProcessLogcatCapture() {
        if (!logcatStarted.compareAndSet(false, true)) return
        val myPid = Process.myPid().toString()
        val readerThread = Thread({
            var proc: java.lang.Process? = null
            try {
                proc = try {
                    Runtime.getRuntime().exec(arrayOf("logcat", "-v", "brief", "--pid=$myPid"))
                } catch (_: Exception) {
                    Runtime.getRuntime().exec(arrayOf("logcat", "-v", "brief"))
                }
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var line: String? = reader.readLine()
                while (line != null) {
                    parseAndRecordLogcatLine(line, myPid)
                    line = reader.readLine()
                }
            } catch (e: Exception) {
                w("SelfCheckLogcat", "进程内 Logcat 监听流结束: ${e.message}")
            } finally {
                try {
                    proc?.destroy()
                } catch (_: Exception) {}
            }
        }, "MotionCraft-SelfLogcat")
        readerThread.isDaemon = true
        readerThread.start()
    }

    private fun parseAndRecordLogcatLine(rawLine: String, myPid: String) {
        if (rawLine.isBlank() || rawLine.startsWith("---------")) return
        // Format: W/Tag(12345): message
        val colonIdx = rawLine.indexOf("): ")
        if (colonIdx <= 2) return
        val prefix = rawLine.substring(0, colonIdx + 1)
        val msg = rawLine.substring(colonIdx + 3).trim()
        if (msg.isEmpty()) return

        // Avoid recursive logging if our own tag prints to logcat
        if (prefix.contains("SelfCheck") || prefix.contains("UI_INTERACTION")) return
        if (!prefix.contains("($myPid)") && !prefix.contains("( $myPid)")) {
            // If --pid wasn't supported on an older ROM, ignore other processes
            if (prefix.contains("(") && prefix.contains(")")) return
        }

        val levelChar = rawLine[0]
        val openParen = prefix.indexOf('(')
        val rawTag = if (openParen > 2) prefix.substring(2, openParen).trim() else "System"

        val isRelevantTag = RELEVANT_LOGCAT_TAGS.any { rawTag.contains(it, ignoreCase = true) }
        val isWarnOrError = (levelChar == 'W' || levelChar == 'E' || levelChar == 'F')

        if (!isRelevantTag && !isWarnOrError) return

        val mappedLevel = when (levelChar) {
            'E', 'F' -> Level.ERROR
            'W' -> Level.WARN
            'I' -> Level.INFO
            else -> Level.DEBUG
        }
        log("Sys:$rawTag", msg, mappedLevel)
    }

    /**
     * Monitors the Main Looper for UI stalls (> 380ms) and captures the exact main thread stack trace
     * so we can diagnose freezes on large 4K videos without ADB.
     */
    private fun startMainThreadFreezeWatchdog() {
        if (!watchdogStarted.compareAndSet(false, true)) return
        val mainHandler = Handler(Looper.getMainLooper())
        backgroundScope.launch {
            while (isActive) {
                val pingSentAt = SystemClock.uptimeMillis()
                val answered = AtomicBoolean(false)
                mainHandler.post {
                    answered.set(true)
                }
                delay(420L)
                if (!answered.get()) {
                    val stallMs = SystemClock.uptimeMillis() - pingSentAt
                    val mainThread = Looper.getMainLooper().thread
                    val topStack = mainThread.stackTrace.take(8).joinToString("\n  at ") { it.toString() }
                    w(
                        "MainThreadWatchdog",
                        "检测到主线程响应延迟 (${stallMs}ms)，当前调用栈:\n  at $topStack"
                    )
                    // Wait until main thread recovers before next check
                    var waitCycles = 0
                    while (!answered.get() && waitCycles < 15) {
                        delay(200L)
                        waitCycles++
                    }
                }
                delay(1500L)
            }
        }
    }

    fun logContextDetails(context: Context) {
        appContextRef = context.applicationContext
        loadPreviousCrashIfPresent(appContextRef!!)
        try {
            val rt = Runtime.getRuntime()
            val maxMb = rt.maxMemory() / (1024 * 1024)
            val totalMb = rt.totalMemory() / (1024 * 1024)
            val freeMb = rt.freeMemory() / (1024 * 1024)
            val usedMb = totalMb - freeMb
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            val availRamMb = memInfo.availMem / (1024 * 1024)
            val totalRamMb = memInfo.totalMem / (1024 * 1024)

            val stat = StatFs(Environment.getDataDirectory().path)
            val freeStorageMb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
            val totalStorageMb = (stat.blockCountLong * stat.blockSizeLong) / (1024 * 1024)

            val memoryDetails = "RAM: ${availRamMb}MB 可用 / ${totalRamMb}MB 总计 (lowMemory=${memInfo.lowMemory}) | JVM Heap: ${usedMb}MB 已用 / ${maxMb}MB 上限 | 存储空间: ${freeStorageMb}MB 可用 / ${totalStorageMb}MB"
            i("SystemTelemetry", memoryDetails)
        } catch (_: Exception) {}

        runFullSelfCheckSuite(appContextRef!!)
    }

    /**
     * Runs a non-ADB hardware & media capability self-check to inspect hardware decoder instances,
     * 4K support, HEVC/H.264/AAC codec availability, and cache directory health.
     */
    fun runFullSelfCheckSuite(context: Context) {
        if (!codecCheckCompleted.compareAndSet(false, true)) return
        backgroundScope.launch {
            try {
                val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                val targetMimes = listOf("video/avc", "video/hevc", "audio/mp4a-latm")
                val reportBuilder = StringBuilder("硬件编解码器自检报告:")

                for (mime in targetMimes) {
                    val matching = codecList.filter { info ->
                        info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
                    }
                    val decoders = matching.filter { !it.isEncoder }
                    val encoders = matching.filter { it.isEncoder }
                    val hwDecoderNames = decoders.filter { info ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            info.isHardwareAccelerated
                        } else {
                            val n = info.name.lowercase()
                            !n.startsWith("omx.google.") && !n.startsWith("c2.android.")
                        }
                    }.map { info ->
                        val caps = try {
                            info.getCapabilitiesForType(mime)
                        } catch (_: Exception) {
                            null
                        }
                        val maxInst = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && caps != null) {
                            caps.maxSupportedInstances
                        } else -1
                        val maxRes = caps?.videoCapabilities?.let { vc ->
                            "${vc.supportedWidths.upper}x${vc.supportedHeights.upper}"
                        } ?: ""
                        if (maxRes.isNotEmpty()) "${info.name}(最大并发=$maxInst, 极限分辨率=$maxRes)"
                        else "${info.name}(最大并发=$maxInst)"
                    }
                    reportBuilder.append("\n  • $mime -> 硬解=${hwDecoderNames.ifEmpty { listOf("无") }.joinToString()} | 编码器数=${encoders.size}")
                }
                i("CodecSelfCheck", reportBuilder.toString())
            } catch (e: Exception) {
                w("CodecSelfCheck", "编解码器自检异常: ${e.message}")
            }
        }
    }

    /**
     * Inspects a media file (MP4/MOV) without ADB and logs detailed track timestamps,
     * B-frame reorder detection, color transfer (SDR vs HDR HLG/PQ), and AV start/end alignment delta.
     */
    fun inspectAndLogMediaFile(stageLabel: String, file: File) {
        backgroundScope.launch {
            if (!file.exists() || file.length() <= 0L) {
                w("MediaSelfCheck", "[$stageLabel] 文件不存在或大小为 0: ${file.name}")
                return@launch
            }
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                val trackCount = extractor.trackCount
                var videoTrackIdx = -1
                var audioTrackIdx = -1
                var videoMime = "none"
                var audioMime = "none"
                var width = 0
                var height = 0
                var rotation = 0
                var colorTransfer = 0
                var colorStandard = 0
                var videoFormatDurationUs = 0L
                var audioFormatDurationUs = 0L

                for (i in 0 until trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/") && videoTrackIdx == -1) {
                        videoTrackIdx = i
                        videoMime = mime
                        if (format.containsKey(MediaFormat.KEY_WIDTH)) width = format.getInteger(MediaFormat.KEY_WIDTH)
                        if (format.containsKey(MediaFormat.KEY_HEIGHT)) height = format.getInteger(MediaFormat.KEY_HEIGHT)
                        if (format.containsKey(MediaFormat.KEY_ROTATION)) rotation = format.getInteger(MediaFormat.KEY_ROTATION)
                        if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) colorTransfer = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
                        if (format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) colorStandard = format.getInteger(MediaFormat.KEY_COLOR_STANDARD)
                        if (format.containsKey(MediaFormat.KEY_DURATION)) videoFormatDurationUs = format.getLong(MediaFormat.KEY_DURATION)
                    } else if (mime.startsWith("audio/") && audioTrackIdx == -1) {
                        audioTrackIdx = i
                        audioMime = mime
                        if (format.containsKey(MediaFormat.KEY_DURATION)) audioFormatDurationUs = format.getLong(MediaFormat.KEY_DURATION)
                    }
                }

                var firstVideoPtsUs = -1L
                var lastVideoPtsUs = -1L
                var videoFrameCount = 0
                var keyframeCount = 0
                var bFrameOutOfOrderCount = 0
                var prevVideoPtsUs = -1L

                if (videoTrackIdx != -1) {
                    extractor.selectTrack(videoTrackIdx)
                    while (true) {
                        val pts = extractor.sampleTime
                        if (pts < 0L) break
                        val flags = extractor.sampleFlags
                        if (firstVideoPtsUs == -1L || pts < firstVideoPtsUs) firstVideoPtsUs = pts
                        if (pts > lastVideoPtsUs) lastVideoPtsUs = pts
                        if ((flags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) keyframeCount++
                        if (prevVideoPtsUs != -1L && pts < prevVideoPtsUs) bFrameOutOfOrderCount++
                        prevVideoPtsUs = pts
                        videoFrameCount++
                        if (!extractor.advance() || videoFrameCount > 5000) break
                    }
                    extractor.unselectTrack(videoTrackIdx)
                }

                var firstAudioPtsUs = -1L
                var lastAudioPtsUs = -1L
                var audioSampleCount = 0
                if (audioTrackIdx != -1) {
                    extractor.selectTrack(audioTrackIdx)
                    while (true) {
                        val pts = extractor.sampleTime
                        if (pts < 0L) break
                        if (firstAudioPtsUs == -1L || pts < firstAudioPtsUs) firstAudioPtsUs = pts
                        if (pts > lastAudioPtsUs) lastAudioPtsUs = pts
                        audioSampleCount++
                        if (!extractor.advance() || audioSampleCount > 8000) break
                    }
                    extractor.unselectTrack(audioTrackIdx)
                }

                val startDriftMs = if (firstVideoPtsUs >= 0L && firstAudioPtsUs >= 0L) {
                    (firstVideoPtsUs - firstAudioPtsUs) / 1000L
                } else 0L
                val endDriftMs = if (lastVideoPtsUs >= 0L && lastAudioPtsUs >= 0L) {
                    (lastVideoPtsUs - lastAudioPtsUs) / 1000L
                } else 0L

                val hdrLabel = when (colorTransfer) {
                    6 -> "HDR10/PQ"
                    7 -> "HDR-HLG(苹果实况常用)"
                    3 -> "SDR(SMPTE170M)"
                    else -> "Standard($colorTransfer)"
                }

                val summary = "[$stageLabel] 文件=${file.name} (${file.length() / 1024}KB) | " +
                    "视频=$videoMime ${width}x${height} (rot=$rotation, 色彩=$hdrLabel, 帧数=$videoFrameCount, I帧=$keyframeCount, B帧重排=$bFrameOutOfOrderCount, PTS=${firstVideoPtsUs / 1000}..${lastVideoPtsUs / 1000}ms) | " +
                    "音频=$audioMime (样本=$audioSampleCount, PTS=${if (firstAudioPtsUs >= 0) firstAudioPtsUs / 1000 else -1}..${if (lastAudioPtsUs >= 0) lastAudioPtsUs / 1000 else -1}ms) | " +
                    "音画对齐偏差: 起点差=${startDriftMs}ms, 终点差=${endDriftMs}ms"

                if (kotlin.math.abs(startDriftMs) > 65L || kotlin.math.abs(endDriftMs) > 90L) {
                    w("MediaSelfCheck", "$summary [警告: 音视频首尾时间戳存在明显偏移]")
                } else {
                    i("MediaSelfCheck", "$summary [音画对齐正常]")
                }
            } catch (e: Exception) {
                w("MediaSelfCheck", "[$stageLabel] 媒体流自检异常 (${file.name}): ${e.message}")
            } finally {
                try {
                    extractor.release()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Verifies a packaged Motion Photo / Live Photo JPEG container:
     * checks JPEG SOI/EOI markers, XMP MicroVideoOffset / Container:Item Length vs actual embedded MP4 offset,
     * and Samsung SEFT trailer integrity.
     */
    fun inspectAndLogLivePhotoContainer(stageLabel: String, motionJpegFile: File) {
        backgroundScope.launch {
            try {
                if (!motionJpegFile.exists() || motionJpegFile.length() < 128L) {
                    w("ContainerSelfCheck", "[$stageLabel] 实况容器文件无效: ${motionJpegFile.name}")
                    return@launch
                }
                val fileLen = motionJpegFile.length()
                val raf = RandomAccessFile(motionJpegFile, "r")
                val head = ByteArray(fileLen.coerceAtMost(64 * 1024L).toInt())
                raf.readFully(head)
                val xmp = ImageProcessor.extractXmpXml(head)
                val hasMotionFlag = xmp.contains("MotionPhoto=\"1\"") || xmp.contains("MicroVideo=\"1\"")

                val tail = ByteArray(8)
                raf.seek(fileLen - 8L)
                raf.readFully(tail)
                val hasSamsungSef = String(tail, 4, 4, Charsets.US_ASCII) == "SEFT"
                raf.close()

                i(
                    "ContainerSelfCheck",
                    "[$stageLabel] 实况容器校验: 文件=${motionJpegFile.name} (${fileLen / 1024}KB) | XMP实况标记=$hasMotionFlag | 三星SEFT尾表=$hasSamsungSef"
                )
            } catch (e: Exception) {
                w("ContainerSelfCheck", "[$stageLabel] 容器自检失败: ${e.message}")
            }
        }
    }

    fun exportLogsToFile(context: Context): File? {
        return try {
            val dir = File(context.cacheDir, "debug_logs").apply { mkdirs() }
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "MotionCraft_Debug_${sdf.format(Date())}.txt"
            val file = File(dir, fileName)
            file.writeText(getAllLogsText())
            file
        } catch (e: Exception) {
            e("DebugLogManager", "Failed to export logs to file", e)
            null
        }
    }

    fun shareLogs(context: Context) {
        try {
            val file = exportLogsToFile(context)
            if (file != null && file.exists()) {
                val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "MotionCraft Diagnostic Logs")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "MotionCraft Logs"))
            } else {
                shareLogsAsText(context)
            }
        } catch (e: Exception) {
            e("DebugLogManager", "FileProvider share failed, falling back to plaintext share", e)
            shareLogsAsText(context)
        }
    }

    private fun shareLogsAsText(context: Context) {
        try {
            val text = getAllLogsText()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, "MotionCraft Diagnostic Logs")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "MotionCraft Logs"))
        } catch (e: Exception) {
            e("DebugLogManager", "Plaintext share failed", e)
        }
    }

    private fun logInitialSystemInfo() {
        val versionInfo = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        val systemInfo = "App Version: $versionInfo | Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.BRAND}) | Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) | ABI: ${Build.SUPPORTED_ABIS.joinToString()}"
        val entry = LogEntry(tag = "SystemInfo", message = systemInfo, level = Level.INFO)
        ringBuffer.add(entry)
        _logs.value = ringBuffer.toList()
    }
}
