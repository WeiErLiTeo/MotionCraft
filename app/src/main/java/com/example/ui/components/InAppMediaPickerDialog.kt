@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.media3.common.util.UnstableApi::class
)
package com.example.ui.components

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import com.example.R
import com.example.core.media.ImageProcessor
import com.example.core.protocol.DetectedOemBrand
import com.example.core.protocol.LivePhotoDetector
import com.example.core.protocol.MotionProbeResult
import com.example.util.MediaPermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

enum class MediaPickerType {
    VIDEO_ONLY,
    IMAGE_ONLY,
    IMAGE_AND_VIDEO
}

data class DeviceMediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val bucketId: String,
    val bucketName: String,
    val dateModified: Long,
    val durationMs: Long,
    val isVideo: Boolean
)

data class MediaFolderBucket(
    val bucketId: String,
    val name: String,
    val count: Int
)

private object PickerThumbCache {
    private val cache = object : LruCache<String, Bitmap>(84) {}

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bmp: Bitmap) {
        if (!bmp.isRecycled) cache.put(key, bmp)
    }
}

private tailrec fun Context.findActivityWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findActivityWindow()
    else -> null
}

@Composable
fun InAppMediaPickerDialog(
    pickerType: MediaPickerType,
    allowMultiSelect: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<Uri>) -> Unit,
    onFallbackToSystemFiles: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionVersion by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedBucketId by remember { mutableStateOf("__ALL__") }
    var onlyMotionPhotos by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var allItems by remember { mutableStateOf<List<DeviceMediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var previewItem by remember { mutableStateOf<DeviceMediaItem?>(null) }

    val motionProbeMap = remember { mutableStateMapOf<Long, MotionProbeResult>() }

    val hasRequiredPermission = remember(permissionVersion, pickerType) {
        when (pickerType) {
            MediaPickerType.VIDEO_ONLY -> MediaPermissionHelper.hasVideoAccess(context)
            MediaPickerType.IMAGE_ONLY -> MediaPermissionHelper.hasImageAccess(context)
            MediaPickerType.IMAGE_AND_VIDEO -> MediaPermissionHelper.hasAnyMediaAccess(context)
        }
    }
    val isPartialAccess = remember(permissionVersion) {
        MediaPermissionHelper.isPartialPhotoAccessOnly(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionVersion++
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionVersion++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(permissionVersion, hasRequiredPermission, pickerType) {
        if (!hasRequiredPermission) {
            isLoading = false
            allItems = emptyList()
            return@LaunchedEffect
        }
        isLoading = true
        allItems = withContext(Dispatchers.IO) {
            queryDeviceMedia(context, pickerType)
        }
        isLoading = false
    }

    // Background OEM Motion Photo detection for images
    LaunchedEffect(allItems) {
        val imageItems = allItems.filter { !it.isVideo }
        if (imageItems.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val semaphore = Semaphore(4)
            imageItems.chunked(24).forEach { batch ->
                coroutineScope {
                    val results = batch.map { item ->
                        async {
                            semaphore.withPermit {
                                val probe = LivePhotoDetector.probeFast(
                                    context = context,
                                    uri = item.uri,
                                    cacheKey = "${item.id}_${item.dateModified}"
                                )
                                item.id to probe
                            }
                        }
                    }.awaitAll()
                    withContext(Dispatchers.Main) {
                        results.forEach { (id, probe) ->
                            motionProbeMap[id] = probe
                        }
                    }
                }
            }
        }
    }

    val folders = remember(allItems, context) {
        val grouped = allItems.groupBy { it.bucketId }
        val result = mutableListOf(
            MediaFolderBucket("__ALL__", context.getString(R.string.picker_folder_all), allItems.size)
        )
        grouped.entries
            .sortedByDescending { it.value.size }
            .forEach { (bucketId, list) ->
                val name = list.firstOrNull()?.bucketName?.ifBlank { "Storage" } ?: "Storage"
                result.add(MediaFolderBucket(bucketId, name, list.size))
            }
        result
    }

    val motionCountInFolder = remember(allItems, selectedBucketId, motionProbeMap.size) {
        allItems.count { item ->
            !item.isVideo &&
                (selectedBucketId == "__ALL__" || item.bucketId == selectedBucketId) &&
                motionProbeMap[item.id]?.isMotionPhoto == true
        }
    }

    val filteredItems = remember(allItems, selectedBucketId, searchQuery, onlyMotionPhotos, motionProbeMap.size) {
        val q = searchQuery.trim().lowercase()
        allItems.filter { item ->
            val matchFolder = selectedBucketId == "__ALL__" || item.bucketId == selectedBucketId
            val matchQuery = q.isEmpty() || item.displayName.lowercase().contains(q)
            val matchMotion = !onlyMotionPhotos || (!item.isVideo && motionProbeMap[item.id]?.isMotionPhoto == true)
            matchFolder && matchQuery && matchMotion
        }
    }

    val dialogTitle = when (pickerType) {
        MediaPickerType.VIDEO_ONLY -> stringResource(R.string.picker_title_video)
        MediaPickerType.IMAGE_ONLY -> stringResource(R.string.picker_title_image)
        MediaPickerType.IMAGE_AND_VIDEO -> stringResource(R.string.picker_title_media)
    }

    val isLightBg = MaterialTheme.colorScheme.background.luminance() > 0.5f

    Dialog(
        onDismissRequest = {
            if (previewItem != null) {
                previewItem = null
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        SideEffect {
            val dialogWindow = (view.parent as? DialogWindowProvider)?.window
            dialogWindow?.let { w ->
                WindowCompat.setDecorFitsSystemWindows(w, false)
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                w.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                @Suppress("DEPRECATION")
                w.clearFlags(
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION or
                        WindowManager.LayoutParams.FLAG_DIM_BEHIND
                )
                @Suppress("DEPRECATION")
                w.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                w.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val attrs = w.attributes
                    attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    w.attributes = attrs
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    w.isStatusBarContrastEnforced = false
                    w.isNavigationBarContrastEnforced = false
                }
                WindowCompat.getInsetsController(w, view).apply {
                    isAppearanceLightStatusBars = isLightBg
                    isAppearanceLightNavigationBars = isLightBg
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    // Compact immersive top bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.btn_cancel)
                                )
                            }
                            Text(
                                text = dialogTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hasRequiredPermission) {
                                IconButton(
                                    onClick = {
                                        isSearchExpanded = !isSearchExpanded
                                        if (!isSearchExpanded) searchQuery = ""
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                        contentDescription = stringResource(R.string.picker_search_hint),
                                        tint = if (isSearchExpanded || searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    onDismiss()
                                    onFallbackToSystemFiles()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.picker_fallback_file_manager), fontSize = 12.sp)
                            }
                        }
                    }

                    // Slim metadata-protection strip
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.picker_metadata_safe_badge),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isPartialAccess && hasRequiredPermission) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .clickable {
                                    permissionLauncher.launch(MediaPermissionHelper.getMediaPermissionsToRequest())
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = stringResource(R.string.picker_partial_banner),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (!hasRequiredPermission) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = stringResource(R.string.picker_need_permission_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.picker_need_permission_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Button(
                                        onClick = {
                                            permissionLauncher.launch(MediaPermissionHelper.getMediaPermissionsToRequest())
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(stringResource(R.string.picker_grant_permission_btn), fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { MediaPermissionHelper.openAppPermissionSettings(context) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(stringResource(R.string.btn_open_app_settings))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TextButton(
                                        onClick = {
                                            onDismiss()
                                            onFallbackToSystemFiles()
                                        }
                                    ) {
                                        Text(stringResource(R.string.picker_fallback_file_manager))
                                    }
                                }
                            }
                        }
                    } else {
                        AnimatedVisibility(visible = isSearchExpanded || searchQuery.isNotEmpty()) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.picker_search_hint), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Folder + Motion filter strip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (pickerType != MediaPickerType.VIDEO_ONLY) {
                                FilterChip(
                                    selected = onlyMotionPhotos,
                                    onClick = { onlyMotionPhotos = !onlyMotionPhotos },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.MotionPhotosOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(R.string.picker_filter_live_only, motionCountInFolder),
                                            fontSize = 11.sp,
                                            fontWeight = if (onlyMotionPhotos) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    ),
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                            folders.forEach { folder ->
                                FilterChip(
                                    selected = selectedBucketId == folder.bucketId,
                                    onClick = { selectedBucketId = folder.bucketId },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    label = { Text("${folder.name} (${folder.count})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }

                        // Compact selection summary row
                        if (allowMultiSelect && filteredItems.isNotEmpty()) {
                            val filteredUris = remember(filteredItems) { filteredItems.map { it.uri }.toSet() }
                            val allInFolderSelected = filteredUris.isNotEmpty() && filteredUris.all { selectedUris.contains(it) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.zip_category_selection_summary, filteredItems.size, selectedUris.size),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = {
                                        selectedUris = if (allInFolderSelected) {
                                            selectedUris.filterNot { filteredUris.contains(it) }
                                        } else {
                                            (selectedUris + filteredItems.map { it.uri }).distinct()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (allInFolderSelected)
                                            stringResource(R.string.picker_deselect_folder)
                                        else
                                            stringResource(R.string.picker_select_all_folder),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        if (isLoading) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (filteredItems.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.picker_empty_folder),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(
                                    start = 10.dp,
                                    end = 10.dp,
                                    top = 4.dp,
                                    bottom = if (allowMultiSelect) 8.dp else 20.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (!allowMultiSelect) Modifier.navigationBarsPadding() else Modifier)
                            ) {
                                items(filteredItems, key = { "${it.isVideo}_${it.id}" }) { item ->
                                    val selectedIndex = selectedUris.indexOf(item.uri)
                                    val isSelected = selectedIndex >= 0
                                    val probe = if (!item.isVideo) motionProbeMap[item.id] else null
                                    MediaPickerGridCell(
                                        item = item,
                                        motionProbe = probe,
                                        isSelected = isSelected,
                                        selectionOrder = if (isSelected) selectedIndex + 1 else 0,
                                        allowMultiSelect = allowMultiSelect,
                                        onClick = {
                                            if (allowMultiSelect) {
                                                selectedUris = if (isSelected) {
                                                    selectedUris - item.uri
                                                } else {
                                                    selectedUris + item.uri
                                                }
                                            } else {
                                                val resolved = MediaPermissionHelper.resolveOriginalUri(context, item.uri)
                                                onConfirm(listOf(resolved))
                                            }
                                        },
                                        onPreview = { previewItem = item }
                                    )
                                }
                            }
                        }

                        if (allowMultiSelect) {
                            Surface(
                                tonalElevation = 4.dp,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { selectedUris = emptyList() },
                                        enabled = selectedUris.isNotEmpty()
                                    ) {
                                        Text(stringResource(R.string.archive_deselect_all))
                                    }
                                    Button(
                                        onClick = {
                                            val resolved = selectedUris.map { MediaPermissionHelper.resolveOriginalUri(context, it) }
                                            onConfirm(resolved)
                                        },
                                        enabled = selectedUris.isNotEmpty(),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.picker_confirm_count, selectedUris.size),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Full-screen preview overlay inside the picker dialog
            previewItem?.let { currentPreview ->
                val isPreviewSelected = selectedUris.contains(currentPreview.uri)
                val probe = if (!currentPreview.isVideo) motionProbeMap[currentPreview.id] else null
                PickerMediaPreviewOverlay(
                    item = currentPreview,
                    motionProbe = probe,
                    allowMultiSelect = allowMultiSelect,
                    isSelected = isPreviewSelected,
                    onClose = { previewItem = null },
                    onToggleSelect = {
                        if (allowMultiSelect) {
                            selectedUris = if (isPreviewSelected) {
                                selectedUris - currentPreview.uri
                            } else {
                                selectedUris + currentPreview.uri
                            }
                        } else {
                            previewItem = null
                            val resolved = MediaPermissionHelper.resolveOriginalUri(context, currentPreview.uri)
                            onConfirm(listOf(resolved))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MediaPickerGridCell(
    item: DeviceMediaItem,
    motionProbe: MotionProbeResult?,
    isSelected: Boolean,
    selectionOrder: Int,
    allowMultiSelect: Boolean,
    onClick: () -> Unit,
    onPreview: () -> Unit
) {
    val context = LocalContext.current
    val cacheKey = "${item.isVideo}_${item.id}"

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isSelected) 2.5.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onPreview
            )
    ) {
        if (item.isVideo) {
            val videoThumb by produceState<Bitmap?>(initialValue = PickerThumbCache.get(cacheKey), key1 = cacheKey) {
                if (value == null) {
                    value = withContext(Dispatchers.IO) {
                        loadVideoThumbnail(context, item.uri, cacheKey)
                    }
                }
            }
            if (videoThumb != null && !videoThumb!!.isRecycled) {
                Image(
                    bitmap = videoThumb!!.asImageBitmap(),
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = item.displayName,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        } else {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top-left preview button (36dp touch wrapper around visible pill)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .clickable(onClick = onPreview)
                .padding(5.dp),
            contentAlignment = Alignment.TopStart
        ) {
            if (!item.isVideo && motionProbe?.isMotionPhoto == true) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.68f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MotionPhotosOn,
                            contentDescription = stringResource(R.string.archive_preview_btn),
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        val tag = if (motionProbe.brand.shortBadge.isNotBlank() && motionProbe.brand != DetectedOemBrand.GENERIC) {
                            motionProbe.brand.shortBadge
                        } else {
                            stringResource(R.string.picker_live_badge_short)
                        }
                        Text(
                            text = tag,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.50f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.isVideo) Icons.Default.PlayArrow else Icons.Default.Visibility,
                        contentDescription = stringResource(R.string.archive_preview_btn),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Bottom filename / duration bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                    )
                )
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.displayName,
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.isVideo && item.durationMs > 0L) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatDurationCompact(item.durationMs),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (allowMultiSelect) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.48f)
                    )
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Text(
                        text = selectionOrder.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerMediaPreviewOverlay(
    item: DeviceMediaItem,
    motionProbe: MotionProbeResult?,
    allowMultiSelect: Boolean,
    isSelected: Boolean,
    onClose: () -> Unit,
    onToggleSelect: () -> Unit
) {
    val context = LocalContext.current
    BackHandler { onClose() }

    var resolvedVideoUri by remember(item) { mutableStateOf<Uri?>(if (item.isVideo) item.uri else null) }
    var isExtractingMotion by remember(item) { mutableStateOf(!item.isVideo && motionProbe?.isMotionPhoto == true) }
    var isPlaying by remember(item) { mutableStateOf(true) }
    var isMuted by remember(item) { mutableStateOf(false) }

    LaunchedEffect(item, motionProbe) {
        if (!item.isVideo && motionProbe?.isMotionPhoto == true) {
            isExtractingMotion = true
            val extractedFile = withContext(Dispatchers.IO) {
                try {
                    val originalUri = MediaPermissionHelper.resolveOriginalUri(context, item.uri)
                    val cacheDir = File(context.cacheDir, "picker_preview").apply { mkdirs() }
                    val outFile = File(cacheDir, "preview_${item.id}_${item.dateModified}.mp4")
                    if (outFile.exists() && outFile.length() > 256L) {
                        outFile
                    } else {
                        val ok = context.contentResolver.openInputStream(originalUri)?.use { input ->
                            ImageProcessor.extractVideoFromMotionPhoto(input, outFile)
                        } ?: false
                        if (ok && outFile.exists() && outFile.length() > 256L) outFile else null
                    }
                } catch (_: Exception) {
                    null
                }
            }
            resolvedVideoUri = extractedFile?.let { Uri.fromFile(it) }
            isExtractingMotion = false
        }
    }

    val exoPlayer = remember(resolvedVideoUri) {
        if (resolvedVideoUri != null) {
            PlayerDecoderConfig.createExoPlayer(context).apply {
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(MediaItem.fromUri(resolvedVideoUri!!))
                prepare()
                playWhenReady = true
            }
        } else {
            null
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        exoPlayer?.let { p ->
            if (isPlaying) p.play() else p.pause()
        }
    }

    LaunchedEffect(exoPlayer, isMuted) {
        exoPlayer?.volume = if (isMuted) 0f else 1f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        text = item.displayName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (motionProbe?.isMotionPhoto == true) {
                        Text(
                            text = motionProbe.brand.fullName,
                            color = Color(0xFFFFD54F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (item.isVideo && item.durationMs > 0L) {
                        Text(
                            text = formatDurationCompact(item.durationMs),
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 11.sp
                        )
                    }
                }
                if (exoPlayer != null) {
                    IconButton(onClick = { isMuted = !isMuted }) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            }

            // Center media stage
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clickable(enabled = exoPlayer != null) { isPlaying = !isPlaying },
                contentAlignment = Alignment.Center
            ) {
                if (!item.isVideo) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (exoPlayer != null) {
                    ExoTextureVideoView(
                        exoPlayer = exoPlayer,
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isExtractingMotion) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.zip_loading_preview), color = Color.White, fontSize = 12.sp)
                        }
                    }
                } else if (exoPlayer != null && !isPlaying) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                    }
                }
            }

            // Bottom action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (exoPlayer != null) {
                    OutlinedButton(
                        onClick = { isPlaying = !isPlaying },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPlaying) stringResource(R.string.picker_preview_pause) else stringResource(R.string.picker_preview_play),
                            color = Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = onToggleSelect,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Close else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            !allowMultiSelect -> stringResource(R.string.picker_preview_use_item)
                            isSelected -> stringResource(R.string.picker_preview_deselect)
                            else -> stringResource(R.string.picker_preview_select)
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatDurationCompact(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}

private fun loadVideoThumbnail(context: Context, uri: Uri, cacheKey: String): Bitmap? {
    PickerThumbCache.get(cacheKey)?.let { return it }
    val bmp: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            context.contentResolver.loadThumbnail(uri, Size(320, 320), null)
        } catch (_: Exception) {
            null
        }
    } else {
        null
    } ?: try {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val frame = mmr.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        mmr.release()
        frame
    } catch (_: Exception) {
        null
    }
    if (bmp != null) {
        PickerThumbCache.put(cacheKey, bmp)
    }
    return bmp
}

private fun queryDeviceMedia(context: Context, pickerType: MediaPickerType): List<DeviceMediaItem> {
    val items = mutableListOf<DeviceMediaItem>()
    if (pickerType == MediaPickerType.VIDEO_ONLY || pickerType == MediaPickerType.IMAGE_AND_VIDEO) {
        if (MediaPermissionHelper.hasVideoAccess(context)) {
            items += queryMediaCollection(
                context = context,
                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                isVideo = true
            )
        }
    }
    if (pickerType == MediaPickerType.IMAGE_ONLY || pickerType == MediaPickerType.IMAGE_AND_VIDEO) {
        if (MediaPermissionHelper.hasImageAccess(context)) {
            items += queryMediaCollection(
                context = context,
                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                isVideo = false
            )
        }
    }
    return items.sortedByDescending { it.dateModified }
}

private fun queryMediaCollection(
    context: Context,
    contentUri: Uri,
    isVideo: Boolean
): List<DeviceMediaItem> {
    val result = mutableListOf<DeviceMediaItem>()
    val projection = mutableListOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.BUCKET_ID,
        MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
    ).apply {
        if (isVideo) add(MediaStore.Video.VideoColumns.DURATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.MediaColumns.RELATIVE_PATH)
        }
    }.toTypedArray()

    try {
        context.contentResolver.query(
            contentUri,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val bucketIdCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val durCol = if (isVideo) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1
            val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(contentUri, id)
                val displayName = if (nameCol >= 0) cursor.getString(nameCol) ?: "Media_$id" else "Media_$id"
                val dateMod = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                val bucketId = if (bucketIdCol >= 0) cursor.getString(bucketIdCol) ?: "unknown" else "unknown"
                val relPath = if (relCol >= 0) cursor.getString(relCol).orEmpty().trim('/').substringAfterLast('/') else ""
                val bucketName = (if (bucketNameCol >= 0) cursor.getString(bucketNameCol) else null)
                    ?.takeIf { it.isNotBlank() }
                    ?: relPath.ifBlank { "Storage" }
                val dur = if (durCol >= 0) cursor.getLong(durCol) else 0L

                result.add(
                    DeviceMediaItem(
                        id = id,
                        uri = uri,
                        displayName = displayName,
                        bucketId = bucketId,
                        bucketName = bucketName,
                        dateModified = dateMod,
                        durationMs = dur,
                        isVideo = isVideo
                    )
                )
            }
        }
    } catch (_: Exception) {
    }
    return result
}
