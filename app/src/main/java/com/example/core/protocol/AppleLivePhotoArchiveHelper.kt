package com.example.core.protocol

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import com.example.core.media.ImageProcessor
import com.example.core.media.VideoProcessor
import com.example.data.LivePhotoRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object AppleLivePhotoArchiveHelper {
    private const val TAG = "AppleArchiveHelper"

    private val IMAGE_EXTENSIONS = setOf("heic", "heif", "jpg", "jpeg", "png", "webp", "gif")
    private val VIDEO_EXTENSIONS = setOf("mov", "mp4", "m4v", "webm", "mkv")

    enum class ArchiveItemKind {
        PAIRED_MEDIA,
        STANDALONE_VIDEO,
        EMBEDDED_MOTION_PHOTO,
        STANDALONE_PHOTO
    }

    data class ArchivePairItem(
        val id: String,
        val baseName: String,
        val imageEntryName: String,
        val videoEntryName: String,
        val imageSize: Long,
        val videoSize: Long,
        val itemKind: ArchiveItemKind = ArchiveItemKind.PAIRED_MEDIA,
        val formatBadge: String = "HEIC + MOV",
        val previewThumbPath: String? = null
    ) {
        val totalSizeBytes: Long get() = imageSize.coerceAtLeast(0L) + videoSize.coerceAtLeast(0L)
    }

    data class ArchiveScanProgress(
        val scannedCount: Int,
        val totalCount: Int,
        val currentEntryName: String,
        val fraction: Float
    )

    data class ArchivePreviewData(
        val item: ArchivePairItem,
        val coverPath: String,
        val videoPath: String
    )

    data class ArchiveScanResult(
        val archiveUri: Uri,
        val archiveName: String,
        val pairs: List<ArchivePairItem>,
        val totalArchiveEntries: Int = 0,
        val pairedCount: Int = 0,
        val standaloneVideoCount: Int = 0,
        val embeddedPhotoCount: Int = 0,
        val standalonePhotoCount: Int = 0,
        val formatBreakdownSummary: String = "",
        val errorMessage: String? = null
    )

    private fun normalizeBaseKey(entryName: String): String {
        val cleanName = entryName.replace('\\', '/').substringAfterLast('/')
        val withoutExt = cleanName.substringBeforeLast('.', cleanName)
        return withoutExt
            .replace(Regex("(?i)_(hevc|h264|cover|video|live|motion)$"), "")
            .replace(Regex("(?i)\\s*\\(edited\\)$"), "")
            .trim()
            .lowercase()
    }

    /**
     * Apple iOS/iCloud naming rule for edited photos: IMG_E1234.HEIC often pairs with IMG_1234.MOV
     * (or vice versa) when only one side of the pair has the 'E' prefix.
     */
    private fun alternateAppleEditedKey(key: String): String? {
        val m1 = Regex("^img_e(\\d+.*)$").matchEntire(key)
        if (m1 != null) return "img_${m1.groupValues[1]}"
        val m2 = Regex("^img_(\\d+.*)$").matchEntire(key)
        if (m2 != null) return "img_e${m2.groupValues[1]}"
        return null
    }

    private fun isAppleDoubleOrSystemEntry(entryName: String): Boolean {
        val normalized = entryName.replace('\\', '/')
        if (normalized.startsWith("__MACOSX/")) return true
        val fileName = normalized.substringAfterLast('/')
        return fileName.startsWith("._") || fileName.startsWith(".") || fileName.isEmpty()
    }

    private val sessionLock = Any()
    @Volatile
    private var activeSession: ActiveArchiveSession? = null

    private class ActiveArchiveSession(
        val uriString: String,
        val pfd: ParcelFileDescriptor?,
        val tempArchiveFile: File?,
        val zipFile: ZipFile
    ) {
        val readLock = Any()
        @Volatile
        var isClosed = false

        fun close() {
            isClosed = true
            try {
                zipFile.close()
            } catch (_: Exception) {}
            try {
                pfd?.close()
            } catch (_: Exception) {}
            try {
                tempArchiveFile?.delete()
            } catch (_: Exception) {}
        }
    }

    /**
     * Closes the cached ZIP session and deletes any fallback temp archive file when leaving the ZIP screen.
     */
    fun closeArchiveSession() {
        val old = synchronized(sessionLock) {
            val cur = activeSession
            activeSession = null
            cur
        }
        old?.close()
    }

    /**
     * Opens and caches a single ZipFile session for the active archive URI so scanning, scrolling thumbnails,
     * previewing videos, and batch importing reuse the exact same open ZipFile without ever re-copying the archive.
     */
    private fun getOrOpenSession(context: Context, uri: Uri): ActiveArchiveSession {
        synchronized(sessionLock) {
            val current = activeSession
            if (current != null && !current.isClosed && current.uriString == uri.toString()) {
                return current
            }
            current?.close()
            activeSession = null

            var pfd: ParcelFileDescriptor? = null
            try {
                pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val procFdFile = File("/proc/self/fd/${pfd.fd}")
                    if (procFdFile.exists() && procFdFile.canRead()) {
                        try {
                            val zip = ZipFile(procFdFile)
                            val session = ActiveArchiveSession(
                                uriString = uri.toString(),
                                pfd = pfd,
                                tempArchiveFile = null,
                                zipFile = zip
                            )
                            activeSession = session
                            return session
                        } catch (e: Exception) {
                            Log.w(TAG, "Direct /proc/self/fd ZipFile open failed, using single session cache copy: ${e.message}")
                        }
                    }
                }
            } catch (_: Exception) {}
            try {
                pfd?.close()
            } catch (_: Exception) {}

            val tempDir = File(context.cacheDir, "zip_fd_fallback").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }
            val fallbackTempFile = File(tempDir, "active_session_archive.zip")
            context.contentResolver.openInputStream(uri)?.use { input ->
                fallbackTempFile.outputStream().buffered(256 * 1024).use { out ->
                    input.copyTo(out, 256 * 1024)
                }
            }
            val zip = ZipFile(fallbackTempFile)
            val session = ActiveArchiveSession(
                uriString = uri.toString(),
                pfd = null,
                tempArchiveFile = fallbackTempFile,
                zipFile = zip
            )
            activeSession = session
            return session
        }
    }

    private inline fun <T> withOpenZipFile(
        context: Context,
        uri: Uri,
        block: (ZipFile) -> T
    ): T {
        val session = getOrOpenSession(context, uri)
        return block(session.zipFile)
    }

    private fun readZipEntryBytes(zipFile: ZipFile, entryName: String, maxBytes: Int = Int.MAX_VALUE): ByteArray? {
        if (entryName.isEmpty()) return null
        val lock = activeSession?.readLock ?: sessionLock
        synchronized(lock) {
            val entry = zipFile.getEntry(entryName) ?: return null
            if (entry.size > maxBytes) return null
            return zipFile.getInputStream(entry).use { input ->
                if (maxBytes == Int.MAX_VALUE) {
                    input.readBytes()
                } else {
                    val out = ByteArrayOutputStream(minOf(entry.size.toInt().coerceAtLeast(1024), maxBytes))
                    val buf = ByteArray(32 * 1024)
                    var remaining = maxBytes
                    while (remaining > 0) {
                        val n = input.read(buf, 0, minOf(buf.size, remaining))
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        remaining -= n
                    }
                    out.toByteArray()
                }
            }
        }
    }

    private fun copyZipEntryToFile(zipFile: ZipFile, entryName: String, outFile: File): Boolean {
        if (entryName.isEmpty()) return false
        val lock = activeSession?.readLock ?: sessionLock
        synchronized(lock) {
            val entry = zipFile.getEntry(entryName) ?: return false
            zipFile.getInputStream(entry).use { input ->
                outFile.outputStream().buffered(128 * 1024).use { out ->
                    input.copyTo(out, 128 * 1024)
                }
            }
            return outFile.exists() && outFile.length() > 0L
        }
    }

    private fun generateThumbnailForArchiveItem(
        context: Context,
        zipFile: ZipFile,
        item: ArchivePairItem,
        previewDir: File,
        index: Int
    ): String? {
        val safeHash = item.id.hashCode().toString().replace('-', 'n')
        val outThumb = File(previewDir, "thumb_${safeHash}.jpg")
        if (outThumb.exists() && outThumb.length() > 128L) {
            return outThumb.absolutePath
        }
        return try {
            // 1. Fast in-memory image thumbnail (EXIF embedded thumb -> scaled ByteBuffer ImageDecoder -> sampled BitmapFactory)
            if (item.imageEntryName.isNotEmpty()) {
                val imgBytes = readZipEntryBytes(zipFile, item.imageEntryName, maxBytes = 20 * 1024 * 1024)
                if (imgBytes != null && imgBytes.isNotEmpty()) {
                    if (ImageProcessor.extractFastThumbnailFromBytes(imgBytes, outThumb, 240)) {
                        return outThumb.absolutePath
                    }
                } else {
                    val ext = item.imageEntryName.substringAfterLast('.', "jpg").lowercase()
                    val rawTemp = File(previewDir, "raw_t_${index}_${safeHash}.$ext")
                    try {
                        if (copyZipEntryToFile(zipFile, item.imageEntryName, rawTemp)) {
                            if (ImageProcessor.extractFastThumbnail(rawTemp, outThumb, 240)) {
                                return outThumb.absolutePath
                            }
                            if (ImageProcessor.ensureJpegCover(context, null, rawTemp, outThumb)) {
                                return outThumb.absolutePath
                            }
                        }
                    } finally {
                        rawTemp.delete()
                    }
                }
            }

            // 2. Fallback (or primary for STANDALONE_VIDEO): extract a fast scaled frame from the video entry
            if (item.videoEntryName.isNotEmpty()) {
                val ext = item.videoEntryName.substringAfterLast('.', "mp4").lowercase()
                val rawVidTemp = File(previewDir, "raw_v_${index}_${safeHash}.$ext")
                try {
                    if (copyZipEntryToFile(zipFile, item.videoEntryName, rawVidTemp)) {
                        val frame = VideoProcessor.extractVideoFrame(context, Uri.fromFile(rawVidTemp), 0L, 240, 240)
                        if (frame != null && !frame.isRecycled) {
                            FileOutputStream(outThumb).use { out ->
                                frame.compress(Bitmap.CompressFormat.JPEG, 82, out)
                            }
                            if (outThumb.exists() && outThumb.length() > 128L) {
                                return outThumb.absolutePath
                            }
                        }
                    }
                } finally {
                    rawVidTemp.delete()
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail generation skipped for ${item.baseName}: ${e.message}")
            null
        }
    }

    /**
     * On-demand thumbnail generator called lazily as the user scrolls through large archives (e.g. 700+ items).
     * Uses the cached ActiveArchiveSession so zero archive copying happens during scrolling.
     */
    suspend fun loadArchiveItemThumbnail(
        context: Context,
        archiveUri: Uri,
        item: ArchivePairItem
    ): String? = withContext(Dispatchers.IO) {
        val previewDir = File(context.cacheDir, "zip_previews").apply { mkdirs() }
        val safeHash = item.id.hashCode().toString().replace('-', 'n')
        val outThumb = File(previewDir, "thumb_${safeHash}.jpg")
        if (outThumb.exists() && outThumb.length() > 128L) {
            return@withContext outThumb.absolutePath
        }
        try {
            currentCoroutineContext().ensureActive()
            withOpenZipFile(context, archiveUri) { zipFile ->
                generateThumbnailForArchiveItem(context, zipFile, item, previewDir, item.id.hashCode())
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun scanArchive(
        context: Context,
        uri: Uri,
        archiveDisplayName: String,
        onProgress: (suspend (ArchiveScanProgress) -> Unit)? = null
    ): ArchiveScanResult = withContext(Dispatchers.IO) {
        try {
            onProgress?.invoke(ArchiveScanProgress(0, 1, archiveDisplayName, 0.05f))
            val previewDir = File(context.cacheDir, "zip_previews").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }
            File(context.cacheDir, "zip_item_preview").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }

            withOpenZipFile(context, uri) { zipFile ->
                val allValidEntries = mutableListOf<ZipEntry>()
                val rawEnum = zipFile.entries()
                while (rawEnum.hasMoreElements()) {
                    val entry = rawEnum.nextElement()
                    if (!entry.isDirectory && !isAppleDoubleOrSystemEntry(entry.name)) {
                        allValidEntries.add(entry)
                    }
                }

                val totalValid = allValidEntries.size.coerceAtLeast(1)
                val imageEntries = LinkedHashMap<String, ZipEntry>()
                val videoEntries = LinkedHashMap<String, ZipEntry>()
                val extCounts = LinkedHashMap<String, Int>()

                for ((idx, entry) in allValidEntries.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val cleanName = entry.name.replace('\\', '/').substringAfterLast('/')
                    val ext = cleanName.substringAfterLast('.', "").lowercase()
                    val key = normalizeBaseKey(entry.name)
                    if (key.isEmpty()) continue

                    if (ext.isNotEmpty()) {
                        val upperExt = ext.uppercase()
                        extCounts[upperExt] = (extCounts[upperExt] ?: 0) + 1
                    }

                    if (IMAGE_EXTENSIONS.contains(ext)) {
                        val existing = imageEntries[key]
                        if (existing == null || entry.size > existing.size) {
                            imageEntries[key] = entry
                        }
                    } else if (VIDEO_EXTENSIONS.contains(ext)) {
                        val existing = videoEntries[key]
                        if (existing == null || entry.size > existing.size) {
                            videoEntries[key] = entry
                        }
                    }

                    if (idx % 10 == 0 || idx == allValidEntries.lastIndex) {
                        val frac = 0.05f + 0.35f * ((idx + 1).toFloat() / totalValid.toFloat())
                        onProgress?.invoke(
                            ArchiveScanProgress(
                                scannedCount = idx + 1,
                                totalCount = totalValid,
                                currentEntryName = cleanName,
                                fraction = frac
                            )
                        )
                    }
                }

                val initialItems = mutableListOf<ArchivePairItem>()
                val consumedVideoKeys = HashSet<String>()
                var pairedCount = 0
                var standaloneVideoCount = 0
                var embeddedPhotoCount = 0
                var standalonePhotoCount = 0

                // 1. If single .livp or 1 image + 1 video with different internal names
                if (imageEntries.size == 1 && videoEntries.size == 1 && imageEntries.keys.first() != videoEntries.keys.first()) {
                    val imgEntry = imageEntries.values.first()
                    val vidEntry = videoEntries.values.first()
                    val imgExt = imgEntry.name.substringAfterLast('.', "HEIC").uppercase()
                    val vidExt = vidEntry.name.substringAfterLast('.', "MOV").uppercase()
                    val base = archiveDisplayName.ifBlank {
                        imgEntry.name.replace('\\', '/').substringAfterLast('/').substringBeforeLast('.')
                    }
                    initialItems.add(
                        ArchivePairItem(
                            id = "single_livp",
                            baseName = base,
                            imageEntryName = imgEntry.name,
                            videoEntryName = vidEntry.name,
                            imageSize = imgEntry.size.coerceAtLeast(0L),
                            videoSize = vidEntry.size.coerceAtLeast(0L),
                            itemKind = ArchiveItemKind.PAIRED_MEDIA,
                            formatBadge = "$imgExt + $vidExt"
                        )
                    )
                    consumedVideoKeys.add(videoEntries.keys.first())
                    pairedCount++
                } else {
                    // 2. Match Image + Video pairs (exact normalized key first, then Apple IMG_E1234 <-> IMG_1234 fallback)
                    val unpairedImages = mutableListOf<Pair<String, ZipEntry>>()

                    for ((key, imgEntry) in imageEntries) {
                        var matchedVidKey: String? = null
                        var vidEntry = videoEntries[key]
                        if (vidEntry != null && !consumedVideoKeys.contains(key)) {
                            matchedVidKey = key
                        } else {
                            val altKey = alternateAppleEditedKey(key)
                            if (altKey != null && !imageEntries.containsKey(altKey) && !consumedVideoKeys.contains(altKey)) {
                                val altVid = videoEntries[altKey]
                                if (altVid != null) {
                                    vidEntry = altVid
                                    matchedVidKey = altKey
                                }
                            }
                        }

                        if (vidEntry != null && matchedVidKey != null) {
                            consumedVideoKeys.add(matchedVidKey)
                            val displayBase = imgEntry.name
                                .replace('\\', '/')
                                .substringAfterLast('/')
                                .substringBeforeLast('.')
                            val imgExt = imgEntry.name.substringAfterLast('.', "JPG").uppercase()
                            val vidExt = vidEntry.name.substringAfterLast('.', "MOV").uppercase()
                            initialItems.add(
                                ArchivePairItem(
                                    id = "pair_$key",
                                    baseName = displayBase,
                                    imageEntryName = imgEntry.name,
                                    videoEntryName = vidEntry.name,
                                    imageSize = imgEntry.size.coerceAtLeast(0L),
                                    videoSize = vidEntry.size.coerceAtLeast(0L),
                                    itemKind = ArchiveItemKind.PAIRED_MEDIA,
                                    formatBadge = "$imgExt + $vidExt"
                                )
                            )
                            pairedCount++
                        } else {
                            unpairedImages.add(key to imgEntry)
                        }
                    }

                    // 3. Classify unpaired images: check if embedded Motion Photo (reading only 64KB header first!)
                    for ((key, imgEntry) in unpairedImages) {
                        val displayBase = imgEntry.name
                            .replace('\\', '/')
                            .substringAfterLast('/')
                            .substringBeforeLast('.')
                        val imgExt = imgEntry.name.substringAfterLast('.', "JPG").uppercase()

                        var isEmbedded = false
                        if ((imgExt == "JPG" || imgExt == "JPEG") && imgEntry.size > 300 * 1024L) {
                            isEmbedded = try {
                                val headBytes = readZipEntryBytes(zipFile, imgEntry.name, maxBytes = 64 * 1024)
                                headBytes != null && ImageProcessor.findVideoOffset(headBytes) > 0
                            } catch (_: Exception) {
                                false
                            }
                        }

                        if (isEmbedded) {
                            initialItems.add(
                                ArchivePairItem(
                                    id = "embedded_$key",
                                    baseName = displayBase,
                                    imageEntryName = imgEntry.name,
                                    videoEntryName = "",
                                    imageSize = imgEntry.size.coerceAtLeast(0L),
                                    videoSize = 0L,
                                    itemKind = ArchiveItemKind.EMBEDDED_MOTION_PHOTO,
                                    formatBadge = "内嵌实况 $imgExt"
                                )
                            )
                            embeddedPhotoCount++
                        } else {
                            initialItems.add(
                                ArchivePairItem(
                                    id = "photo_$key",
                                    baseName = displayBase,
                                    imageEntryName = imgEntry.name,
                                    videoEntryName = "",
                                    imageSize = imgEntry.size.coerceAtLeast(0L),
                                    videoSize = 0L,
                                    itemKind = ArchiveItemKind.STANDALONE_PHOTO,
                                    formatBadge = "独立照片 $imgExt"
                                )
                            )
                            standalonePhotoCount++
                        }
                    }

                    // 4. Include standalone MP4 / MOV videos inside the ZIP that had no matching photo
                    for ((key, vidEntry) in videoEntries) {
                        if (consumedVideoKeys.contains(key)) continue
                        val displayBase = vidEntry.name
                            .replace('\\', '/')
                            .substringAfterLast('/')
                            .substringBeforeLast('.')
                        val vidExt = vidEntry.name.substringAfterLast('.', "MP4").uppercase()
                        initialItems.add(
                            ArchivePairItem(
                                id = "vid_$key",
                                baseName = displayBase,
                                imageEntryName = "",
                                videoEntryName = vidEntry.name,
                                imageSize = 0L,
                                videoSize = vidEntry.size.coerceAtLeast(0L),
                                itemKind = ArchiveItemKind.STANDALONE_VIDEO,
                                formatBadge = "独立视频 $vidExt"
                            )
                        )
                        standaloneVideoCount++
                    }
                }

                // Pre-generate thumbnails for the first 16 items during scan;
                // remaining items load smoothly on-demand from the cached ActiveArchiveSession as the user scrolls!
                val maxInitialThumbs = minOf(initialItems.size, 16)
                val finalItems = ArrayList<ArchivePairItem>(initialItems.size)
                for ((idx, item) in initialItems.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val thumbPath = if (idx < maxInitialThumbs) {
                        val frac = 0.40f + 0.58f * ((idx + 1).toFloat() / maxInitialThumbs.coerceAtLeast(1).toFloat())
                        onProgress?.invoke(
                            ArchiveScanProgress(
                                scannedCount = idx + 1,
                                totalCount = initialItems.size,
                                currentEntryName = item.baseName,
                                fraction = frac
                            )
                        )
                        generateThumbnailForArchiveItem(context, zipFile, item, previewDir, idx)
                    } else {
                        null
                    }
                    finalItems.add(item.copy(previewThumbPath = thumbPath))
                }

                val summaryParts = extCounts.entries.map { "${it.key} × ${it.value}" }
                val summaryStr = summaryParts.joinToString(" · ")

                onProgress?.invoke(
                    ArchiveScanProgress(
                        scannedCount = finalItems.size,
                        totalCount = finalItems.size.coerceAtLeast(1),
                        currentEntryName = archiveDisplayName,
                        fraction = 1.0f
                    )
                )

                ArchiveScanResult(
                    archiveUri = uri,
                    archiveName = archiveDisplayName,
                    pairs = finalItems,
                    totalArchiveEntries = allValidEntries.size,
                    pairedCount = pairedCount,
                    standaloneVideoCount = standaloneVideoCount,
                    embeddedPhotoCount = embeddedPhotoCount,
                    standalonePhotoCount = standalonePhotoCount,
                    formatBreakdownSummary = summaryStr
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning archive", e)
            ArchiveScanResult(
                archiveUri = uri,
                archiveName = archiveDisplayName,
                pairs = emptyList(),
                errorMessage = e.localizedMessage ?: "无法读取压缩包"
            )
        }
    }

    /**
     * Extracts a single item from the cached ArchiveSession for instant inline preview:
     * Normalizes Apple QuickTime .mov headers to standard MP4 in memory so ExoPlayer plays immediately.
     */
    suspend fun extractArchiveItemForPreview(
        context: Context,
        archiveUri: Uri,
        item: ArchivePairItem
    ): ArchivePreviewData? = withContext(Dispatchers.IO) {
        val previewDir = File(context.cacheDir, "zip_item_preview").apply { mkdirs() }
        val safeKey = item.id.hashCode().toString().replace('-', 'n')
        val rawVidExt = item.videoEntryName.substringAfterLast('.', "mp4").lowercase().ifBlank { "mp4" }
        val outCover = File(previewDir, "prev_cover_$safeKey.jpg")
        val outVideo = File(previewDir, "prev_video_$safeKey.mp4")

        if (item.itemKind == ArchiveItemKind.STANDALONE_PHOTO) {
            if (outCover.exists() && outCover.length() > 128L) {
                return@withContext ArchivePreviewData(item, outCover.absolutePath, "")
            }
        } else if (outCover.exists() && outCover.length() > 128L && outVideo.exists() && outVideo.length() > 256L) {
            return@withContext ArchivePreviewData(item, outCover.absolutePath, outVideo.absolutePath)
        }

        try {
            currentCoroutineContext().ensureActive()
            withOpenZipFile(context, archiveUri) { zipFile ->
                when (item.itemKind) {
                    ArchiveItemKind.PAIRED_MEDIA -> {
                        if (!outVideo.exists() || outVideo.length() <= 256L) {
                            val fastBytes = readZipEntryBytes(zipFile, item.videoEntryName, maxBytes = 60 * 1024 * 1024)
                            if (fastBytes != null && fastBytes.size > 256) {
                                val sanitized = com.example.core.zlc.ZlcMp4Util.sanitizeVideoTracksIfNeeded(fastBytes, previewDir)
                                outVideo.writeBytes(sanitized)
                            } else if (!copyZipEntryToFile(zipFile, item.videoEntryName, outVideo)) {
                                return@withOpenZipFile null
                            }
                        }

                        val existingThumb = item.previewThumbPath?.let { File(it) }
                        if (existingThumb != null && existingThumb.exists() && existingThumb.length() > 128L) {
                            existingThumb.copyTo(outCover, overwrite = true)
                        } else if (!outCover.exists() || outCover.length() <= 128L) {
                            val generated = generateThumbnailForArchiveItem(context, zipFile, item, previewDir, safeKey.hashCode())
                            if (generated != null && File(generated).exists()) {
                                File(generated).copyTo(outCover, overwrite = true)
                            }
                        }
                    }

                    ArchiveItemKind.STANDALONE_VIDEO -> {
                        if (!outVideo.exists() || outVideo.length() <= 256L) {
                            val fastBytes = readZipEntryBytes(zipFile, item.videoEntryName, maxBytes = 60 * 1024 * 1024)
                            if (fastBytes != null && fastBytes.size > 256) {
                                val sanitized = com.example.core.zlc.ZlcMp4Util.sanitizeVideoTracksIfNeeded(fastBytes, previewDir)
                                outVideo.writeBytes(sanitized)
                            } else if (!copyZipEntryToFile(zipFile, item.videoEntryName, outVideo)) {
                                return@withOpenZipFile null
                            }
                        }
                        val existingThumb = item.previewThumbPath?.let { File(it) }
                        if (existingThumb != null && existingThumb.exists() && existingThumb.length() > 128L) {
                            existingThumb.copyTo(outCover, overwrite = true)
                        } else if (!outCover.exists() || outCover.length() <= 128L) {
                            val frame = VideoProcessor.extractVideoFrame(context, Uri.fromFile(outVideo), 0L, 640, 640)
                            if (frame != null) {
                                FileOutputStream(outCover).use { out ->
                                    frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                }
                            }
                        }
                    }

                    ArchiveItemKind.EMBEDDED_MOTION_PHOTO -> {
                        if (!outCover.exists() || outCover.length() <= 128L) {
                            if (!copyZipEntryToFile(zipFile, item.imageEntryName, outCover)) {
                                return@withOpenZipFile null
                            }
                        }
                        if (!outVideo.exists() || outVideo.length() <= 256L) {
                            ImageProcessor.extractVideoFromMotionPhoto(outCover, outVideo)
                        }
                    }

                    ArchiveItemKind.STANDALONE_PHOTO -> {
                        val imgBytes = readZipEntryBytes(zipFile, item.imageEntryName, maxBytes = 25 * 1024 * 1024)
                        if (imgBytes != null && ImageProcessor.extractFastThumbnailFromBytes(imgBytes, outCover, 1280)) {
                            return@withOpenZipFile ArchivePreviewData(item, outCover.absolutePath, "")
                        }
                        val imgExt = item.imageEntryName.substringAfterLast('.', "jpg").lowercase()
                        val rawImg = File(previewDir, "raw_photo_$safeKey.$imgExt")
                        try {
                            if (copyZipEntryToFile(zipFile, item.imageEntryName, rawImg)) {
                                if (!ImageProcessor.extractFastThumbnail(rawImg, outCover, 1280)) {
                                    ImageProcessor.ensureJpegCover(context, null, rawImg, outCover)
                                }
                            }
                        } finally {
                            rawImg.delete()
                        }
                        return@withOpenZipFile if (outCover.exists() && outCover.length() > 128L) {
                            ArchivePreviewData(item, outCover.absolutePath, "")
                        } else null
                    }
                }

                if (outVideo.exists() && outVideo.length() > 256L) {
                    val coverFinal = if (outCover.exists() && outCover.length() > 128L) outCover.absolutePath else (item.previewThumbPath ?: "")
                    ArchivePreviewData(item, coverFinal, outVideo.absolutePath)
                } else {
                    null
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting preview for ${item.baseName}: ${e.message}", e)
            null
        }
    }

    /**
     * Directly saves an archive item's video (or standalone photo) to the system gallery without converting to Live Photo.
     */
    suspend fun saveArchiveItemDirectlyToGallery(
        context: Context,
        archiveUri: Uri,
        item: ArchivePairItem
    ): String? = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "zip_direct_save").apply { mkdirs() }
        val ts = System.currentTimeMillis()
        val safeName = item.baseName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "Media_$ts" }
        try {
            withOpenZipFile(context, archiveUri) { zipFile ->
                if (item.videoEntryName.isNotEmpty()) {
                    val rawExt = item.videoEntryName.substringAfterLast('.', "mp4").lowercase()
                    val rawVid = File(tempDir, "direct_v_$ts.$rawExt")
                    val outMp4 = File(tempDir, "direct_out_$ts.mp4")
                    try {
                        if (!copyZipEntryToFile(zipFile, item.videoEntryName, rawVid)) {
                            return@withOpenZipFile null
                        }
                        val targetVideoFile = if (rawExt == "mov") {
                            val ok = VideoProcessor.trimVideo(context, Uri.fromFile(rawVid), outMp4, 0L, 0L, includeAudio = true)
                            if (ok && outMp4.exists() && outMp4.length() > 256L) outMp4 else rawVid
                        } else {
                            rawVid
                        }
                        val resolver = context.contentResolver
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, "$safeName.mp4")
                            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LivePhotos")
                                put(MediaStore.Video.Media.IS_PENDING, 1)
                            }
                        }
                        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.buffered(64 * 1024)?.use { out ->
                                targetVideoFile.inputStream().buffered(64 * 1024).use { it.copyTo(out, 64 * 1024) }
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                values.clear()
                                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                                resolver.update(uri, values, null, null)
                            }
                            return@withOpenZipFile "视频已直接保存至系统相册 (Movies/LivePhotos/$safeName.mp4)"
                        }
                        null
                    } finally {
                        rawVid.delete()
                        outMp4.delete()
                    }
                } else if (item.imageEntryName.isNotEmpty()) {
                    val rawExt = item.imageEntryName.substringAfterLast('.', "jpg").lowercase()
                    val rawImg = File(tempDir, "direct_i_$ts.$rawExt")
                    val outJpg = File(tempDir, "direct_i_out_$ts.jpg")
                    try {
                        if (!copyZipEntryToFile(zipFile, item.imageEntryName, rawImg)) {
                            return@withOpenZipFile null
                        }
                        val ok = ImageProcessor.ensureJpegCover(context, null, rawImg, outJpg)
                        val fileToSave = if (ok && outJpg.exists()) outJpg else rawImg
                        saveMotionJpegToGallery(context, fileToSave, safeName)
                        "照片已直接保存至系统相册 (Pictures/LivePhotos/$safeName.jpg)"
                    } finally {
                        rawImg.delete()
                        outJpg.delete()
                    }
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveArchiveItemDirectlyToGallery failed: ${e.message}", e)
            null
        }
    }

    /**
     * Fallback invoked only if ExoPlayer encounters a hardware playback error on a raw .mov preview file.
     */
    suspend fun transcodePreviewVideoFallback(
        context: Context,
        rawVideoPath: String
    ): String? = withContext(Dispatchers.IO) {
        val src = File(rawVideoPath)
        if (!src.exists() || src.length() <= 256L) return@withContext null
        val fallbackOut = File(src.parentFile, "${src.nameWithoutExtension}_compat.mp4")
        if (fallbackOut.exists() && fallbackOut.length() > 512L) {
            return@withContext fallbackOut.absolutePath
        }
        val ok = VideoProcessor.transcodeToStandardMp4Sync(
            context = context,
            inputUri = Uri.fromFile(src),
            outputFile = fallbackOut,
            startMs = 0L,
            endMs = 0L,
            includeAudio = true
        )
        if (ok && fallbackOut.exists() && fallbackOut.length() > 512L) {
            fallbackOut.absolutePath
        } else {
            null
        }
    }

    data class SynthesisProgress(
        val currentIndex: Int,
        val totalCount: Int,
        val currentName: String,
        val stepIndex: Int, // 1=extract, 2=heic->jpg, 3=mov->mp4, 4=xmp+save
        val overallFraction: Float,
        val currentItemId: String = ""
    )

    /**
     * Synthesizes a single Apple pair (HEIC/JPG + MOV/MP4) with 4-step real-time progress reporting,
     * saving both to the app library and to the system gallery (Pictures/LivePhotos).
     */
    suspend fun synthesizeSingleApplePair(
        context: Context,
        imageUri: Uri,
        videoUri: Uri,
        customTitle: String,
        brandMode: LivePhotoBrandMode,
        onProgress: suspend (SynthesisProgress) -> Unit
    ): LivePhotoRecord? = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis()
        val rollingCacheDir = File(context.cacheDir, "zip_rolling").apply { mkdirs() }
        val libraryDir = File(context.filesDir, "live_photos").apply { mkdirs() }
        val displayTitle = customTitle.ifBlank { "AppleLive_$ts" }

        val rawImgTemp = File(rollingCacheDir, "single_img_${ts}.tmp")
        val rawVidTemp = File(rollingCacheDir, "single_vid_${ts}.tmp")
        val jpegCoverTemp = File(rollingCacheDir, "single_cover_${ts}.jpg")
        val remuxedMp4Temp = File(rollingCacheDir, "single_video_${ts}.mp4")
        val packagedOutputTemp = File(rollingCacheDir, "single_motion_${ts}.jpg")

        try {
            currentCoroutineContext().ensureActive()
            onProgress(SynthesisProgress(1, 1, displayTitle, 1, 0.15f))

            context.contentResolver.openInputStream(imageUri)?.use { input ->
                rawImgTemp.outputStream().buffered(64 * 1024).use { out ->
                    input.copyTo(out, 64 * 1024)
                }
            }
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                rawVidTemp.outputStream().buffered(64 * 1024).use { out ->
                    input.copyTo(out, 64 * 1024)
                }
            }

            currentCoroutineContext().ensureActive()
            onProgress(SynthesisProgress(1, 1, displayTitle, 2, 0.40f))

            val coverOk = ImageProcessor.ensureJpegCover(context, imageUri, rawImgTemp, jpegCoverTemp)
            if (!coverOk || !jpegCoverTemp.exists()) return@withContext null

            currentCoroutineContext().ensureActive()
            onProgress(SynthesisProgress(1, 1, displayTitle, 3, 0.70f))

            val presentationUs = extractApplePresentationTimeUs(rawVidTemp)
            val remuxOk = VideoProcessor.trimVideo(
                context = context,
                inputUri = Uri.fromFile(rawVidTemp),
                outputFile = remuxedMp4Temp,
                startMs = 0L,
                endMs = 0L,
                includeAudio = true
            )
            if (!remuxOk || !remuxedMp4Temp.exists() || remuxedMp4Temp.length() <= 256L) {
                return@withContext null
            }

            currentCoroutineContext().ensureActive()
            onProgress(SynthesisProgress(1, 1, displayTitle, 4, 0.92f))

            val packagedOk = LivePhotoProtocolPacker.packageLivePhoto(
                coverFile = jpegCoverTemp,
                videoFile = remuxedMp4Temp,
                outputFile = packagedOutputTemp,
                presentationTimestampUs = presentationUs,
                brandMode = brandMode
            )
            if (!packagedOk || !packagedOutputTemp.exists()) return@withContext null

            saveMotionJpegToGallery(context, packagedOutputTemp, displayTitle)

            val savedMotionFile = File(libraryDir, "${ts}_motion.jpg")
            val savedVideoFile = File(libraryDir, "${ts}_video.mp4")
            packagedOutputTemp.copyTo(savedMotionFile, overwrite = true)
            remuxedMp4Temp.copyTo(savedVideoFile, overwrite = true)

            com.example.util.DebugLogManager.inspectAndLogLivePhotoContainer("SingleApplePair", savedMotionFile)

            onProgress(SynthesisProgress(1, 1, displayTitle, 4, 1.0f))

            LivePhotoRecord(
                title = displayTitle,
                coverPath = savedMotionFile.absolutePath,
                videoPath = savedVideoFile.absolutePath,
                timestamp = ts,
                isEmbedded = true
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "synthesizeSingleApplePair failed: ${e.message}", e)
            null
        } finally {
            rawImgTemp.delete()
            rawVidTemp.delete()
            jpegCoverTemp.delete()
            remuxedMp4Temp.delete()
            packagedOutputTemp.delete()
        }
    }

    /**
     * Extracts a single item at a time from the ZipFile, supports paired HEIC/JPG+MOV/MP4,
     * standalone MP4/MOV videos (auto-extracting cover frame), and embedded Motion Photos,
     * packages with the target brandMode, writes to system gallery and local database,
     * and deletes temporary files in finally {} before moving to the next item.
     */
    suspend fun processArchivePairs(
        context: Context,
        archiveUri: Uri,
        selectedPairs: List<ArchivePairItem>,
        brandMode: LivePhotoBrandMode,
        onProgress: suspend (SynthesisProgress) -> Unit,
        onItemStatusChanged: suspend (String, Int) -> Unit = { _, _ -> },
        onRecordCreated: suspend (LivePhotoRecord) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        if (selectedPairs.isEmpty()) return@withContext 0
        var successCount = 0
        val rollingCacheDir = File(context.cacheDir, "zip_rolling").apply { mkdirs() }
        val libraryDir = File(context.filesDir, "live_photos").apply { mkdirs() }
        val total = selectedPairs.size

        withOpenZipFile(context, archiveUri) { zipFile ->
            for ((index, pair) in selectedPairs.withIndex()) {
                currentCoroutineContext().ensureActive()
                val curIdx = index + 1

                val ts = System.currentTimeMillis() + index
                val imgExt = pair.imageEntryName.substringAfterLast('.', "heic").lowercase()
                val vidExt = pair.videoEntryName.substringAfterLast('.', "mov").lowercase()

                val rawImgTemp = File(rollingCacheDir, "raw_img_${ts}.$imgExt")
                val rawVidTemp = File(rollingCacheDir, "raw_vid_${ts}.$vidExt")
                val jpegCoverTemp = File(rollingCacheDir, "cover_${ts}.jpg")
                val remuxedMp4Temp = File(rollingCacheDir, "video_${ts}.mp4")
                val packagedOutputTemp = File(rollingCacheDir, "motion_${ts}.jpg")

                try {
                    onItemStatusChanged(pair.id, 1)
                    onProgress(
                        SynthesisProgress(
                            currentIndex = curIdx,
                            totalCount = total,
                            currentName = pair.baseName,
                            stepIndex = 1,
                            overallFraction = ((index + 0.1f) / total.toFloat()).coerceIn(0.02f, 0.99f),
                            currentItemId = pair.id
                        )
                    )

                    when (pair.itemKind) {
                        ArchiveItemKind.PAIRED_MEDIA -> {
                            if (!copyZipEntryToFile(zipFile, pair.imageEntryName, rawImgTemp) ||
                                !copyZipEntryToFile(zipFile, pair.videoEntryName, rawVidTemp)
                            ) {
                                onItemStatusChanged(pair.id, -1)
                                continue
                            }

                            currentCoroutineContext().ensureActive()
                            onItemStatusChanged(pair.id, 2)
                            onProgress(
                                SynthesisProgress(
                                    currentIndex = curIdx,
                                    totalCount = total,
                                    currentName = pair.baseName,
                                    stepIndex = 2,
                                    overallFraction = ((index + 0.35f) / total.toFloat()).coerceIn(0.04f, 0.99f),
                                    currentItemId = pair.id
                                )
                            )

                            val coverOk = ImageProcessor.ensureJpegCover(context, null, rawImgTemp, jpegCoverTemp)
                            if (!coverOk || !jpegCoverTemp.exists()) {
                                onItemStatusChanged(pair.id, -1)
                                continue
                            }

                            currentCoroutineContext().ensureActive()
                            onItemStatusChanged(pair.id, 3)
                            onProgress(
                                SynthesisProgress(
                                    currentIndex = curIdx,
                                    totalCount = total,
                                    currentName = pair.baseName,
                                    stepIndex = 3,
                                    overallFraction = ((index + 0.65f) / total.toFloat()).coerceIn(0.06f, 0.99f),
                                    currentItemId = pair.id
                                )
                            )

                            val presentationUs = extractApplePresentationTimeUs(rawVidTemp)
                            val remuxOk = VideoProcessor.trimVideo(
                                context = context,
                                inputUri = Uri.fromFile(rawVidTemp),
                                outputFile = remuxedMp4Temp,
                                startMs = 0L,
                                endMs = 0L,
                                includeAudio = true
                            )
                            if (!remuxOk || !remuxedMp4Temp.exists() || remuxedMp4Temp.length() <= 256L) {
                                onItemStatusChanged(pair.id, -1)
                                continue
                            }

                            currentCoroutineContext().ensureActive()
                            onItemStatusChanged(pair.id, 4)
                            onProgress(
                                SynthesisProgress(
                                    currentIndex = curIdx,
                                    totalCount = total,
                                    currentName = pair.baseName,
                                    stepIndex = 4,
                                    overallFraction = ((index + 0.90f) / total.toFloat()).coerceIn(0.08f, 0.99f),
                                    currentItemId = pair.id
                                )
                            )

                            val packagedOk = LivePhotoProtocolPacker.packageLivePhoto(
                                coverFile = jpegCoverTemp,
                                videoFile = remuxedMp4Temp,
                                outputFile = packagedOutputTemp,
                                presentationTimestampUs = presentationUs,
                                brandMode = brandMode
                            )
                            if (!packagedOk || !packagedOutputTemp.exists()) {
                                onItemStatusChanged(pair.id, -1)
                                continue
                            }
                        }

                        ArchiveItemKind.STANDALONE_VIDEO -> {
                            if (!copyZipEntryToFile(zipFile, pair.videoEntryName, rawVidTemp)) {
                                onItemStatusChanged(pair.id, -1)
                                continue
                            }

                            currentCoroutineContext().ensureActive()
                            onItemStatusChanged(pair.id, 2)
                            onProgress(
                                SynthesisProgress(
                                    currentIndex = curIdx,
                                    totalCount = total,
                                    currentName = pair.baseName,
                                    stepIndex = 2,
                                    overallFraction = ((index + 0.35f) / total.toFloat()).coerceIn(0.04f, 0.99f),
                                    currentItemId = pair.id
                                )
                            )

                            val totalDur = VideoProcessor.getVideoDuration(context, Uri.fromFile(rawVidTemp))
                            val clipEndMs = if (totalDur > 3500L) 3000L else 0L

                            currentCoroutineContext().ensureActive()
                            onItemStatusChanged(pair.id, 3)
                            onProgress(
                                SynthesisProgress(
                                    currentIndex = curIdx,
                                    totalCount = total,
                                    currentName = pair.baseName,
                                    stepIndex = 3,
                                    overallFraction = ((index + 0.65f) / total.toFloat()).coerceIn(0.06f, 0.99f),
                                    currentItemId = pair.id
                                )
                            )

                            val remuxOk = VideoProcessor.trimVideo(
                                context = context,
                                inputUri = Uri.fromFile(rawVidTemp),
                                outputFile = remuxedMp4Temp,
                                startMs = 0L,
                                endMs = clipEndMs,
                                includeAudio = true
                            )
                            if (!remuxOk || !remuxedMp4Temp.exists() || remuxedMp4Temp.length() <= 256L) {
                                onItemStatusChanged(pair.id, -1)
                                continue
                            }

                            val frameBmp = VideoProcessor.extractVideoFrame(context, Uri.fromFile(remuxedMp4Temp), 0L)
                                ?: VideoProcessor.extractVideoFrame(context, Uri.fromFile(rawVidTemp), 0L)
                                ?: run {
                                    onItemStatusChanged(pair.id, -1)
                                    continue
                                }
                            FileOutputStream(jpegCoverTemp).use { out ->
                                frameBmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                            }

                            currentCoroutineContext().ensureActive()
                            onItemStatusChanged(pair.id, 4)
                            onProgress(
                                SynthesisProgress(
                                    currentIndex = curIdx,
                                    totalCount = total,
                                    currentName = pair.baseName,
                                    stepIndex = 4,
                                    overallFraction = ((index + 0.90f) / total.toFloat()).coerceIn(0.08f, 0.99f),
                                    currentItemId = pair.id
                                )
                            )

                            val packagedOk = LivePhotoProtocolPacker.packageLivePhoto(
                                coverFile = jpegCoverTemp,
                                videoFile = remuxedMp4Temp,
                                outputFile = packagedOutputTemp,
                                presentationTimestampUs = 0L,
                                brandMode = brandMode
                            )
                            if (!packagedOk || !packagedOutputTemp.exists()) {
                                onItemStatusChanged(pair.id, -1)
                                continue
                            }
                        }

                        ArchiveItemKind.EMBEDDED_MOTION_PHOTO -> {
                            if (!copyZipEntryToFile(zipFile, pair.imageEntryName, packagedOutputTemp)) {
                                onItemStatusChanged(pair.id, -1)
                                continue
                            }
                            onItemStatusChanged(pair.id, 3)
                            onProgress(
                                SynthesisProgress(
                                    currentIndex = curIdx,
                                    totalCount = total,
                                    currentName = pair.baseName,
                                    stepIndex = 3,
                                    overallFraction = ((index + 0.70f) / total.toFloat()).coerceIn(0.06f, 0.99f),
                                    currentItemId = pair.id
                                )
                            )
                            val extracted = ImageProcessor.extractVideoFromMotionPhoto(packagedOutputTemp, remuxedMp4Temp)
                            if (!extracted || !remuxedMp4Temp.exists() || remuxedMp4Temp.length() <= 256L) {
                                onItemStatusChanged(pair.id, -1)
                                continue
                            }
                        }

                        ArchiveItemKind.STANDALONE_PHOTO -> {
                            if (!copyZipEntryToFile(zipFile, pair.imageEntryName, rawImgTemp)) {
                                onItemStatusChanged(pair.id, -1)
                                continue
                            }
                            onItemStatusChanged(pair.id, 2)
                            onProgress(
                                SynthesisProgress(
                                    currentIndex = curIdx,
                                    totalCount = total,
                                    currentName = pair.baseName,
                                    stepIndex = 2,
                                    overallFraction = ((index + 0.65f) / total.toFloat()).coerceIn(0.06f, 0.99f),
                                    currentItemId = pair.id
                                )
                            )
                            val coverOk = ImageProcessor.ensureJpegCover(context, null, rawImgTemp, packagedOutputTemp)
                            if (!coverOk || !packagedOutputTemp.exists()) {
                                onItemStatusChanged(pair.id, -1)
                                continue
                            }
                            saveMotionJpegToGallery(context, packagedOutputTemp, pair.baseName)
                            onItemStatusChanged(pair.id, 5)
                            successCount++
                            continue
                        }
                    }

                    // Save to system MediaStore (Pictures/LivePhotos)
                    saveMotionJpegToGallery(context, packagedOutputTemp, pair.baseName)

                    // Always save both motion cover and standard MP4 video file so library playback is instant and reliable
                    val savedMotionFile = File(libraryDir, "${ts}_motion.jpg")
                    val savedVideoFile = File(libraryDir, "${ts}_video.mp4")
                    packagedOutputTemp.copyTo(savedMotionFile, overwrite = true)
                    remuxedMp4Temp.copyTo(savedVideoFile, overwrite = true)

                    com.example.util.DebugLogManager.inspectAndLogLivePhotoContainer("ArchiveItem-${pair.baseName}", savedMotionFile)

                    val record = LivePhotoRecord(
                        title = pair.baseName,
                        coverPath = savedMotionFile.absolutePath,
                        videoPath = savedVideoFile.absolutePath,
                        timestamp = ts,
                        isEmbedded = true
                    )
                    onRecordCreated(record)
                    onItemStatusChanged(pair.id, 5)
                    successCount++
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    onItemStatusChanged(pair.id, -1)
                    Log.e(TAG, "Failed processing archive pair ${pair.baseName}: ${e.message}", e)
                } finally {
                    rawImgTemp.delete()
                    rawVidTemp.delete()
                    jpegCoverTemp.delete()
                    remuxedMp4Temp.delete()
                    packagedOutputTemp.delete()
                }
            }
        }
        successCount
    }

    private fun saveMotionJpegToGallery(context: Context, motionJpegFile: File, displayTitle: String) {
        try {
            val resolver = context.contentResolver
            val safeName = displayTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "LivePhoto_${System.currentTimeMillis()}" }
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$safeName.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LivePhotos")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.buffered(64 * 1024)?.use { outStream ->
                    motionJpegFile.inputStream().buffered(64 * 1024).use { inStream ->
                        inStream.copyTo(outStream, 64 * 1024)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving to MediaStore: ${e.message}")
        }
    }

    /**
     * Reads Apple QuickTime timed metadata track (still-image-time) if available,
     * or falls back to half of the video duration.
     */
    fun extractApplePresentationTimeUs(videoFile: File): Long {
        if (!videoFile.exists()) return 0L
        if (videoFile.length() in 64L..(60L * 1024 * 1024)) {
            try {
                val zlcStillUs = com.example.core.zlc.ZlcMp4Util.appleStillImageTimeUs(videoFile.readBytes())
                if (zlcStillUs >= 0L) {
                    return zlcStillUs
                }
            } catch (_: Exception) {}
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(videoFile.absolutePath)
            var videoDurationUs = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_DURATION)) {
                    videoDurationUs = format.getLong(MediaFormat.KEY_DURATION)
                } else if (mime.contains("meta") || mime.contains("application/")) {
                    extractor.selectTrack(i)
                    val sampleTime = extractor.sampleTime
                    extractor.unselectTrack(i)
                    if (sampleTime > 0L) {
                        return sampleTime
                    }
                }
            }
            return if (videoDurationUs > 0L) videoDurationUs / 2L else 0L
        } catch (_: Exception) {
            return 0L
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Exports a MotionCraft LivePhotoRecord into an Apple-compatible single-file .livp package
     * (containing <title>.JPG and <title>.MOV bound together by a shared ContentIdentifier UUID).
     */
    suspend fun exportToAppleLivp(
        context: Context,
        record: LivePhotoRecord
    ): File? = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "apple_export").apply { mkdirs() }
        val cleanJpeg = File(tempDir, "clean_cover.jpg")
        val cleanMov = File(tempDir, "clean_video.mov")

        try {
            val assetUuid = UUID.randomUUID().toString().uppercase()
            val baseTitle = record.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "IMG_${record.id}" }

            // 1. Extract pure JPEG cover (strip appended video bytes after FF D9)
            val coverSource = File(record.coverPath)
            if (!coverSource.exists()) return@withContext null
            extractPureJpegWithAppleUuid(coverSource, cleanJpeg, assetUuid)

            // 2. Obtain video and inject QuickTime ContentIdentifier UUID box
            val directVideo = File(record.videoPath)
            val tempMp4 = File(tempDir, "extracted.mp4")
            val sourceVideo = if (directVideo.exists() && directVideo.length() > 0L) {
                directVideo
            } else {
                ImageProcessor.extractVideoFromMotionPhoto(coverSource, tempMp4)
                tempMp4
            }
            if (!sourceVideo.exists() || sourceVideo.length() <= 0L) return@withContext null

            buildAppleQuickTimeMovWithUuid(sourceVideo, cleanMov, assetUuid)
            tempMp4.delete()

            // 3. Package into a single .livp archive (standard ZIP containing <baseTitle>.JPG and <baseTitle>.MOV)
            val outLivpFile = File(tempDir, "$baseTitle.livp")
            ZipOutputStream(FileOutputStream(outLivpFile).buffered(64 * 1024)).use { zos ->
                zos.putNextEntry(ZipEntry("$baseTitle.JPG"))
                cleanJpeg.inputStream().buffered(64 * 1024).use { it.copyTo(zos, 64 * 1024) }
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("$baseTitle.MOV"))
                cleanMov.inputStream().buffered(64 * 1024).use { it.copyTo(zos, 64 * 1024) }
                zos.closeEntry()
            }

            // Also save to Downloads/MotionCraft on Android 10+ so the user can find it in Files
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, "$baseTitle.livp")
                        put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MotionCraft")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val dlUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (dlUri != null) {
                        context.contentResolver.openOutputStream(dlUri)?.buffered(64 * 1024)?.use { out ->
                            outLivpFile.inputStream().buffered(64 * 1024).use { it.copyTo(out, 64 * 1024) }
                        }
                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        context.contentResolver.update(dlUri, values, null, null)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed saving .livp to Downloads: ${e.message}")
                }
            }

            outLivpFile
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting Apple .livp", e)
            null
        } finally {
            cleanJpeg.delete()
            cleanMov.delete()
        }
    }

    /**
     * Strips any appended MP4 trailer from the cover JPEG and injects Apple's ContentIdentifier UUID
     * into both XMP (apple-fi:ContentIdentifier) and EXIF MakerNote/ImageUniqueID.
     */
    private fun extractPureJpegWithAppleUuid(sourceFile: File, outJpegFile: File, uuid: String) {
        val bytes = sourceFile.readBytes()
        var jpegEnd = bytes.size
        val ftypPattern = "ftyp".toByteArray(Charsets.US_ASCII)
        val ftypIdx = ImageProcessor.findSubarray(bytes, ftypPattern)
        val searchLimit = if (ftypIdx > 4) ftypIdx - 4 else bytes.size
        for (i in searchLimit - 2 downTo 2) {
            if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD9.toByte()) {
                jpegEnd = i + 2
                break
            }
        }

        val appleXmp = """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="XMP Core 6.0.0">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:apple-fi="http://ns.apple.com/faceinfo/1.0/"
        xmlns:photoshop="http://ns.adobe.com/photoshop/1.0/"
        apple-fi:ContentIdentifier="$uuid"/>
  </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""
        val xmpSeg = LivePhotoProtocolPacker.wrapXmpPayload(appleXmp)

        outJpegFile.outputStream().buffered(64 * 1024).use { out ->
            if (jpegEnd >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                out.write(0xFF)
                out.write(0xD8)
                out.write(xmpSeg)
                out.write(bytes, 2, jpegEnd - 2)
            } else {
                out.write(bytes, 0, jpegEnd)
            }
        }

        try {
            val exif = ExifInterface(outJpegFile.absolutePath)
            exif.setAttribute(ExifInterface.TAG_IMAGE_UNIQUE_ID, uuid)
            exif.setAttribute(ExifInterface.TAG_MAKE, "Apple")
            exif.saveAttributes()
        } catch (_: Exception) {}
    }

    private fun buildAppleQuickTimeMovWithUuid(sourceVideo: File, outMovFile: File, uuid: String) {
        val metaBox = buildQuickTimeContentIdentifierMetaBox(uuid)
        outMovFile.outputStream().buffered(64 * 1024).use { out ->
            sourceVideo.inputStream().buffered(64 * 1024).use { input ->
                input.copyTo(out, 64 * 1024)
            }
            out.write(metaBox)
        }
    }

    private fun buildQuickTimeContentIdentifierMetaBox(uuid: String): ByteArray {
        val keyBytes = "com.apple.quicktime.content.identifier".toByteArray(Charsets.UTF_8)
        val valBytes = uuid.toByteArray(Charsets.UTF_8)

        val hdlrPayload = ByteArrayOutputStream().apply {
            write(ByteArray(4))
            write(ByteArray(4))
            write("mdta".toByteArray(Charsets.US_ASCII))
            write(ByteArray(12))
            write(0)
        }.toByteArray()
        val hdlrBox = wrapAtom("hdlr", hdlrPayload)

        val keysPayload = ByteArrayOutputStream().apply {
            write(ByteArray(4))
            writeBe32(this, 1)
            writeBe32(this, keyBytes.size + 8)
            write("mdta".toByteArray(Charsets.US_ASCII))
            write(keyBytes)
        }.toByteArray()
        val keysBox = wrapAtom("keys", keysPayload)

        val dataPayload = ByteArrayOutputStream().apply {
            writeBe32(this, 1)
            writeBe32(this, 0)
            write(valBytes)
        }.toByteArray()
        val dataBox = wrapAtom("data", dataPayload)

        val item1Box = ByteArrayOutputStream().apply {
            writeBe32(this, dataBox.size + 8)
            writeBe32(this, 1)
            write(dataBox)
        }.toByteArray()
        val ilstBox = wrapAtom("ilst", item1Box)

        val metaPayload = ByteArrayOutputStream().apply {
            write(hdlrBox)
            write(keysBox)
            write(ilstBox)
        }.toByteArray()
        return wrapAtom("meta", metaPayload)
    }

    private fun wrapAtom(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(payload.size + 8)
        writeBe32(out, payload.size + 8)
        out.write(type.toByteArray(Charsets.US_ASCII))
        out.write(payload)
        return out.toByteArray()
    }

    private fun writeBe32(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}
