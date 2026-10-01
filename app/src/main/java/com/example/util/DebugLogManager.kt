package com.example.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _isDebugMode = MutableStateFlow(false)
    val isDebugMode: StateFlow<Boolean> = _isDebugMode.asStateFlow()

    private val _isFloatingWindowVisible = MutableStateFlow(false)
    val isFloatingWindowVisible: StateFlow<Boolean> = _isFloatingWindowVisible.asStateFlow()

    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null

    init {
        logInitialSystemInfo()
    }

    fun initCrashHandler() {
        if (defaultExceptionHandler == null) {
            defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                e("CrashHandler", "Uncaught Exception in thread ${thread.name}: ${throwable.localizedMessage}", throwable)
                defaultExceptionHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun setDebugMode(enabled: Boolean) {
        _isDebugMode.value = enabled
        if (!enabled) {
            _isFloatingWindowVisible.value = false
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

    fun log(tag: String, message: String, level: Level = Level.INFO) {
        if (!_isDebugMode.value && (level == Level.VERBOSE || level == Level.DEBUG)) return
        val entry = LogEntry(tag = tag, message = message, level = level)
        val current = _logs.value.toMutableList()
        if (current.size > 2000) {
            current.removeAt(0)
        }
        current.add(entry)
        _logs.value = current
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
        _logs.value = emptyList()
        logInitialSystemInfo()
    }

    fun getAllLogsText(): String {
        return _logs.value.joinToString("\n") { it.format() }
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

    fun logContextDetails(context: Context) {
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

            val memoryDetails = "RAM: ${availRamMb}MB free / ${totalRamMb}MB total | JVM Heap: ${usedMb}MB used / ${maxMb}MB max | Internal Storage: ${freeStorageMb}MB free / ${totalStorageMb}MB total"
            i("SystemTelemetry", memoryDetails)
        } catch (_: Exception) {}
    }

    private fun logInitialSystemInfo() {
        val versionInfo = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        val systemInfo = "App Version: $versionInfo | Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.BRAND}) | Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) | ABI: ${Build.SUPPORTED_ABIS.joinToString()}"
        val entry = LogEntry(tag = "SystemInfo", message = systemInfo, level = Level.INFO)
        _logs.value = listOf(entry)
    }
}
