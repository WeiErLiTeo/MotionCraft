@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import com.example.R
import com.example.core.media.ImageProcessor
import com.example.core.protocol.DetectedOemBrand
import com.example.core.protocol.LivePhotoBrandMode
import com.example.data.LivePhotoRecord
import com.example.ui.components.ExoTextureVideoView
import com.example.ui.components.ExportProgressFloatingDialog
import com.example.ui.components.InAppMediaPickerDialog
import com.example.ui.components.MediaPickerType
import com.example.ui.components.PlayerDecoderConfig
import com.example.util.MediaPermissionHelper
import com.example.viewmodel.LivePhotoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun BrandConvertScreen(viewModel: LivePhotoViewModel) {
    val context = LocalContext.current

    val queue by viewModel.brandConvertQueue.collectAsStateWithLifecycle()
    val isConverting by viewModel.isBrandConverting.collectAsStateWithLifecycle()
    val convertResult by viewModel.brandConvertResult.collectAsStateWithLifecycle()
    val saveToLibrary by viewModel.brandConvertSaveToLibrary.collectAsStateWithLifecycle()
    val targetBrand by viewModel.brandMode.collectAsStateWithLifecycle()
    val useBuiltInPicker by viewModel.useBuiltInFilePicker.collectAsStateWithLifecycle()
    val allLivePhotos by viewModel.allLivePhotos.collectAsStateWithLifecycle()
    val exportProgressState by viewModel.exportProgressState.collectAsStateWithLifecycle()

    var showInAppPicker by remember { mutableStateOf(false) }
    var appendOnPick by remember { mutableStateOf(false) }
    var showLibraryDialog by remember { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<LivePhotoViewModel.BrandConvertItem?>(null) }

    ExportProgressFloatingDialog(state = exportProgressState)

    BackHandler(enabled = queue.isNotEmpty() && !isConverting) {
        viewModel.clearBrandConvertQueue()
    }

    val systemPhotosPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.prepareBrandConvertQueue(context, uris, append = appendOnPick || queue.isNotEmpty())
        }
        appendOnPick = false
    }

    val documentPhotosPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.prepareBrandConvertQueue(context, uris, append = appendOnPick || queue.isNotEmpty())
        }
        appendOnPick = false
    }

    if (showInAppPicker) {
        InAppMediaPickerDialog(
            pickerType = MediaPickerType.IMAGE_ONLY,
            allowMultiSelect = true,
            onDismiss = {
                showInAppPicker = false
                appendOnPick = false
            },
            onConfirm = { uris ->
                showInAppPicker = false
                if (uris.isNotEmpty()) {
                    viewModel.prepareBrandConvertQueue(context, uris, append = appendOnPick || queue.isNotEmpty())
                }
                appendOnPick = false
            },
            onFallbackToSystemFiles = {
                documentPhotosPicker.launch(arrayOf("image/*"))
            }
        )
    }

    if (showLibraryDialog) {
        LibraryLivePhotoSelectDialog(
            records = allLivePhotos,
            onDismiss = { showLibraryDialog = false },
            onConfirm = { selected ->
                showLibraryDialog = false
                viewModel.addLibraryRecordsToBrandQueue(selected, append = true)
            }
        )
    }

    previewItem?.let { item ->
        BrandConvertPreviewDialog(
            item = item,
            onDismiss = { previewItem = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (queue.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                modifier = Modifier.size(68.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.58f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.brand_convert_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.brand_convert_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MotionPhotosOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.brand_convert_supported_oems),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = {
                    appendOnPick = false
                    if (useBuiltInPicker) {
                        showInAppPicker = true
                    } else {
                        systemPhotosPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().testTag("btn_brand_pick_photos")
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(stringResource(R.string.brand_convert_pick_photos), fontWeight = FontWeight.Bold)
                    Text(
                        text = if (useBuiltInPicker) stringResource(R.string.brand_convert_pick_sub_builtin) else stringResource(R.string.brand_convert_pick_sub_system),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        appendOnPick = false
                        documentPhotosPicker.launch(arrayOf("image/*"))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).testTag("btn_brand_pick_files")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.brand_convert_pick_files), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = { showLibraryDialog = true },
                    enabled = allLivePhotos.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).testTag("btn_brand_pick_library")
                ) {
                    Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.brand_convert_pick_library), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(modifier = Modifier.weight(1.4f))
        } else {
            // Queue Mode
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { viewModel.clearBrandConvertQueue() },
                    enabled = !isConverting
                ) {
                    Text(stringResource(R.string.btn_cancel), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
                Text(
                    text = stringResource(R.string.brand_convert_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = {
                        appendOnPick = true
                        if (useBuiltInPicker) {
                            showInAppPicker = true
                        } else {
                            documentPhotosPicker.launch(arrayOf("image/*"))
                        }
                    },
                    enabled = !isConverting
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.batch_add_more_videos), fontWeight = FontWeight.Bold)
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.brand_convert_target_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LivePhotoBrandMode.entries.forEach { mode ->
                        val isSelected = targetBrand == mode
                        val labelText = when (mode) {
                            LivePhotoBrandMode.FUSION -> stringResource(R.string.protocol_fusion)
                            LivePhotoBrandMode.OPPO -> stringResource(R.string.protocol_oppo)
                            LivePhotoBrandMode.XIAOMI -> stringResource(R.string.protocol_xiaomi)
                            LivePhotoBrandMode.SAMSUNG -> stringResource(R.string.protocol_samsung)
                            LivePhotoBrandMode.VIVO -> stringResource(R.string.protocol_vivo)
                            LivePhotoBrandMode.GOOGLE -> stringResource(R.string.protocol_google)
                        }
                        Button(
                            onClick = { if (!isConverting) viewModel.setBrandMode(mode) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.height(40.dp)
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(labelText, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isConverting) {
                            viewModel.setBrandConvertSaveToLibrary(!saveToLibrary)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = stringResource(R.string.brand_convert_save_to_lib),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.brand_convert_save_to_lib_desc),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = saveToLibrary,
                        onCheckedChange = { viewModel.setBrandConvertSaveToLibrary(it) },
                        enabled = !isConverting
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                val motionCount = queue.count { it.probe.isMotionPhoto }
                Text(
                    text = stringResource(R.string.brand_convert_queue_summary, queue.size, motionCount),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            queue.forEach { item ->
                BrandConvertQueueCard(
                    item = item,
                    isConverting = isConverting,
                    onPreview = { previewItem = item },
                    onRemove = { viewModel.removeBrandConvertItem(item.key) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val validCount = queue.count { it.probe.isMotionPhoto }
            Button(
                onClick = { viewModel.startBatchBrandConversion(context, targetBrand, saveToLibrary) },
                enabled = !isConverting && validCount > 0,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("btn_start_brand_convert")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.brand_convert_start_btn, validCount), fontWeight = FontWeight.Bold)
            }

            convertResult?.let {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BrandConvertQueueCard(
    item: LivePhotoViewModel.BrandConvertItem,
    isConverting: Boolean,
    onPreview: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onPreview() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = item.outputCoverPath?.let { File(it) } ?: item.uri,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (item.probe.isMotionPhoto || item.outputVideoPath != null) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.56f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.archive_preview_btn),
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                when (item.status) {
                    2 -> {
                        Text(
                            text = stringResource(R.string.zip_status_done_tap_play),
                            fontSize = 11.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    3 -> {
                        Text(
                            text = stringResource(R.string.brand_convert_item_failed),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        val badgeColor = if (item.probe.isMotionPhoto) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                        val sourceLabel = if (item.probe.isMotionPhoto) {
                            stringResource(R.string.brand_convert_source_fmt, item.probe.brand.fullName)
                        } else {
                            stringResource(R.string.brand_convert_not_live)
                        }
                        Text(
                            text = sourceLabel,
                            fontSize = 11.sp,
                            color = badgeColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            when (item.status) {
                1 -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                2 -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                3 -> Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                else -> {
                    IconButton(
                        onClick = onRemove,
                        enabled = !isConverting
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryLivePhotoSelectDialog(
    records: List<LivePhotoRecord>,
    onDismiss: () -> Unit,
    onConfirm: (List<LivePhotoRecord>) -> Unit
) {
    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.brand_convert_pick_library), fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(records, key = { it.id }) { rec ->
                    val checked = selectedIds.contains(rec.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                selectedIds = if (checked) selectedIds - rec.id else selectedIds + rec.id
                            }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = File(rec.coverPath),
                            contentDescription = rec.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = rec.title,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                selectedIds = if (it) selectedIds + rec.id else selectedIds - rec.id
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(records.filter { selectedIds.contains(it.id) }) },
                enabled = selectedIds.isNotEmpty()
            ) {
                Text(stringResource(R.string.picker_confirm_count, selectedIds.size))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
private fun BrandConvertPreviewDialog(
    item: LivePhotoViewModel.BrandConvertItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var resolvedVideoUri by remember(item) {
        mutableStateOf<Uri?>(
            item.outputVideoPath?.let { File(it) }?.takeIf { it.exists() }?.let { Uri.fromFile(it) }
                ?: item.videoFallbackPath?.let { File(it) }?.takeIf { it.exists() }?.let { Uri.fromFile(it) }
        )
    }
    var isExtracting by remember(item) { mutableStateOf(resolvedVideoUri == null && item.probe.isMotionPhoto) }
    var isPlaying by remember(item) { mutableStateOf(true) }
    var isMuted by remember(item) { mutableStateOf(false) }

    LaunchedEffect(item) {
        if (resolvedVideoUri == null && item.probe.isMotionPhoto) {
            isExtracting = true
            val extracted = withContext(Dispatchers.IO) {
                try {
                    val cacheDir = File(context.cacheDir, "brand_item_preview").apply { mkdirs() }
                    val outFile = File(cacheDir, "bp_${item.key.hashCode()}.mp4")
                    val ok = if (item.uri.scheme == "file" && item.uri.path != null) {
                        ImageProcessor.extractVideoFromMotionPhoto(File(item.uri.path!!), outFile)
                    } else {
                        val originalUri = MediaPermissionHelper.resolveOriginalUri(context, item.uri)
                        context.contentResolver.openInputStream(originalUri)?.use { input ->
                            ImageProcessor.extractVideoFromMotionPhoto(input, outFile)
                        } ?: false
                    }
                    if (ok && outFile.exists() && outFile.length() > 256L) outFile else null
                } catch (_: Exception) {
                    null
                }
            }
            resolvedVideoUri = extracted?.let { Uri.fromFile(it) }
            isExtracting = false
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
        onDispose { exoPlayer?.release() }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        exoPlayer?.let { p -> if (isPlaying) p.play() else p.pause() }
    }

    LaunchedEffect(exoPlayer, isMuted) {
        exoPlayer?.volume = if (isMuted) 0f else 1f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f).clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = if (item.status == 2) {
                            stringResource(R.string.zip_status_done_tap_play)
                        } else if (item.probe.isMotionPhoto) {
                            item.probe.brand.fullName
                        } else {
                            DetectedOemBrand.NONE.fullName
                        }
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row {
                        if (exoPlayer != null) {
                            IconButton(onClick = { isMuted = !isMuted }) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .clickable(enabled = exoPlayer != null) { isPlaying = !isPlaying },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = item.outputCoverPath?.let { File(it) } ?: item.uri,
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (exoPlayer != null) {
                        ExoTextureVideoView(
                            exoPlayer = exoPlayer,
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (isExtracting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                    } else if (exoPlayer != null && !isPlaying) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }

                if (exoPlayer != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { isPlaying = !isPlaying },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPlaying) stringResource(R.string.picker_preview_pause) else stringResource(R.string.picker_preview_play))
                    }
                }
            }
        }
    }
}
