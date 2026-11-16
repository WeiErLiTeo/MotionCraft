@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.example.core.media.VideoThumbnailCache
import com.example.core.protocol.LivePhotoBrandMode
import com.example.ui.components.ExoTextureVideoView
import com.example.ui.components.ExportProgressFloatingDialog
import com.example.ui.components.InAppMediaPickerDialog
import com.example.ui.components.InlineVideoPlayer
import com.example.ui.components.MediaPickerType
import com.example.ui.components.PlayerDecoderConfig
import com.example.ui.components.VideoTrimSlider
import com.example.util.DebugLogManager
import com.example.util.MediaPermissionHelper
import com.example.viewmodel.LivePhotoViewModel
import kotlinx.coroutines.delay

@Composable
fun ConvertScreen(viewModel: LivePhotoViewModel) {
    val context = LocalContext.current

    val selectedVideoUri by viewModel.selectedVideoUri.collectAsStateWithLifecycle()
    val batchVideoQueue by viewModel.batchVideoQueue.collectAsStateWithLifecycle()
    val isBatchConverting by viewModel.isBatchConvertingVideos.collectAsStateWithLifecycle()
    val useBuiltInPicker by viewModel.useBuiltInFilePicker.collectAsStateWithLifecycle()
    val videoDuration by viewModel.videoDuration.collectAsStateWithLifecycle()
    val frames by viewModel.videoFrames.collectAsStateWithLifecycle()
    val trimStartMs by viewModel.trimStartMs.collectAsStateWithLifecycle()
    val trimEndMs by viewModel.trimEndMs.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val generationResult by viewModel.generationResult.collectAsStateWithLifecycle()
    val brandMode by viewModel.brandMode.collectAsStateWithLifecycle()
    val coverTimeMs by viewModel.coverTimeMs.collectAsStateWithLifecycle()
    val extractedCoverFrame by viewModel.extractedCoverFrame.collectAsStateWithLifecycle()
    val customCoverUri by viewModel.customCoverUri.collectAsStateWithLifecycle()
    val defaultTitle by viewModel.defaultTitle.collectAsStateWithLifecycle()
    val exportProgressState by viewModel.exportProgressState.collectAsStateWithLifecycle()

    // 0 = Video to Live (single/batch), 1 = Dual-Select Pair & Multi-Image Synthesis
    var convertSubMode by remember { mutableIntStateOf(0) }

    var title by remember { mutableStateOf("") }
    var currentPlaybackPositionMs by remember { mutableLongStateOf(trimStartMs) }
    var previewSeekTimeMs by remember { mutableStateOf<Long?>(null) }
    var isBoundaryPreview by remember { mutableStateOf(false) }

    var showInAppVideoPicker by remember { mutableStateOf(false) }
    var appendVideosToBatch by remember { mutableStateOf(false) }
    var showInAppCoverPicker by remember { mutableStateOf(false) }

    var batchClipLimitMs by remember { mutableLongStateOf(3000L) }
    var batchCoverMode by remember { mutableStateOf(LivePhotoViewModel.BatchCoverMode.FIRST_FRAME) }
    var previewBatchItem by remember { mutableStateOf<LivePhotoViewModel.BatchVideoItem?>(null) }

    // Dual-select pair & multi-image states
    var pairImageUri by remember { mutableStateOf<Uri?>(null) }
    var pairVideoUri by remember { mutableStateOf<Uri?>(null) }
    var multiImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var activePairPickerMode by remember { mutableStateOf<String?>(null) }

    val handlePickedVideos: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            pairImageUri = null
            pairVideoUri = null
            multiImageUris = emptyList()
            convertSubMode = 0
            if (appendVideosToBatch || batchVideoQueue.isNotEmpty() || uris.size > 1) {
                viewModel.prepareBatchVideoQueue(
                    context,
                    uris,
                    append = appendVideosToBatch || batchVideoQueue.isNotEmpty()
                )
            } else {
                viewModel.selectVideoForTrim(context, uris.first())
            }
        }
        appendVideosToBatch = false
    }

    LaunchedEffect(trimStartMs, trimEndMs) {
        if (currentPlaybackPositionMs < trimStartMs) {
            currentPlaybackPositionMs = trimStartMs
        } else if (currentPlaybackPositionMs > trimEndMs) {
            currentPlaybackPositionMs = trimEndMs
        }
    }

    // Reset pair state cleanly after successful generation
    LaunchedEffect(selectedVideoUri, isGenerating) {
        if (!isGenerating && selectedVideoUri == null && pairVideoUri != null) {
            pairVideoUri = null
            pairImageUri = null
        }
    }

    LaunchedEffect(generationResult, isGenerating) {
        if (!isGenerating && generationResult?.contains("成功") == true && multiImageUris.isNotEmpty()) {
            multiImageUris = emptyList()
            pairImageUri = null
        }
    }

    ExportProgressFloatingDialog(state = exportProgressState)

    LaunchedEffect(defaultTitle) {
        if (defaultTitle.isNotBlank()) {
            title = defaultTitle
        }
    }

    val isInPairEditor = pairVideoUri != null || multiImageUris.isNotEmpty()

    BackHandler(enabled = selectedVideoUri != null && !isGenerating && !isInPairEditor) {
        DebugLogManager.interaction("ConvertScreen", "Back gesture -> cancel video selection")
        viewModel.selectVideoForTrim(context, null)
        title = ""
    }

    BackHandler(enabled = isInPairEditor && !isGenerating) {
        DebugLogManager.interaction("ConvertScreen", "Back gesture -> cancel pair/multi synthesis")
        pairVideoUri = null
        multiImageUris = emptyList()
        viewModel.selectVideoForTrim(context, null)
    }

    BackHandler(enabled = batchVideoQueue.isNotEmpty() && !isBatchConverting) {
        DebugLogManager.interaction("ConvertScreen", "Back gesture -> clear batch queue")
        viewModel.clearBatchVideoQueue()
    }

    val multiVideoSystemPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        handlePickedVideos(uris)
    }

    val documentVideosPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        handlePickedVideos(uris)
    }

    val customCoverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            DebugLogManager.interaction("ConvertScreen", "Custom cover picked: $uri")
            viewModel.setCustomCover(uri)
        }
    }

    val customCoverFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            DebugLogManager.interaction("ConvertScreen", "Custom cover file picked: $uri")
            viewModel.setCustomCover(uri)
        }
    }

    // Launchers for Dual-Select Pair & Multi-Image
    val onPairSingleImageSelected: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            val safeUri = MediaPermissionHelper.resolveOriginalUri(context, uri)
            pairImageUri = safeUri
            multiImageUris = emptyList()
            if (title.isBlank()) {
                title = viewModel.queryFileName(context, safeUri)
            }
        }
    }

    val onPairVideoSelected: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            val safeUri = MediaPermissionHelper.resolveOriginalUri(context, uri)
            pairVideoUri = safeUri
            multiImageUris = emptyList()
            viewModel.selectVideoForTrim(context, safeUri)
            if (title.isBlank()) {
                title = viewModel.queryFileName(context, safeUri)
            }
        }
    }

    val onPairMultiImagesSelected: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            val safeUris = uris.map { MediaPermissionHelper.resolveOriginalUri(context, it) }
            multiImageUris = safeUris
            pairImageUri = safeUris.first()
            pairVideoUri = null
            if (title.isBlank()) {
                title = viewModel.queryFileName(context, safeUris.first())
            }
        }
    }

    val pairSingleImgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { onPairSingleImageSelected(it) }
    val pairSingleImgDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { onPairSingleImageSelected(it) }
    val pairVidPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { onPairVideoSelected(it) }
    val pairVidDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { onPairVideoSelected(it) }
    val pairMultiImgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { onPairMultiImagesSelected(it) }
    val pairMultiImgDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { onPairMultiImagesSelected(it) }

    if (showInAppVideoPicker) {
        InAppMediaPickerDialog(
            pickerType = MediaPickerType.VIDEO_ONLY,
            allowMultiSelect = true,
            onDismiss = {
                showInAppVideoPicker = false
                appendVideosToBatch = false
            },
            onConfirm = { uris ->
                showInAppVideoPicker = false
                handlePickedVideos(uris)
            },
            onFallbackToSystemFiles = {
                documentVideosPicker.launch(arrayOf("video/*"))
            }
        )
    }

    if (showInAppCoverPicker) {
        InAppMediaPickerDialog(
            pickerType = MediaPickerType.IMAGE_ONLY,
            allowMultiSelect = false,
            onDismiss = { showInAppCoverPicker = false },
            onConfirm = { uris ->
                showInAppCoverPicker = false
                uris.firstOrNull()?.let { viewModel.setCustomCover(it) }
            },
            onFallbackToSystemFiles = {
                customCoverFilePicker.launch(arrayOf("image/*"))
            }
        )
    }

    when (activePairPickerMode) {
        "single_image" -> {
            InAppMediaPickerDialog(
                pickerType = MediaPickerType.IMAGE_ONLY,
                allowMultiSelect = false,
                onDismiss = { activePairPickerMode = null },
                onConfirm = { uris ->
                    activePairPickerMode = null
                    onPairSingleImageSelected(uris.firstOrNull())
                },
                onFallbackToSystemFiles = { pairSingleImgDocPicker.launch(arrayOf("image/*")) }
            )
        }
        "single_video" -> {
            InAppMediaPickerDialog(
                pickerType = MediaPickerType.VIDEO_ONLY,
                allowMultiSelect = false,
                onDismiss = { activePairPickerMode = null },
                onConfirm = { uris ->
                    activePairPickerMode = null
                    onPairVideoSelected(uris.firstOrNull())
                },
                onFallbackToSystemFiles = { pairVidDocPicker.launch(arrayOf("video/*")) }
            )
        }
        "multi_image" -> {
            InAppMediaPickerDialog(
                pickerType = MediaPickerType.IMAGE_ONLY,
                allowMultiSelect = true,
                onDismiss = { activePairPickerMode = null },
                onConfirm = { uris ->
                    activePairPickerMode = null
                    onPairMultiImagesSelected(uris)
                },
                onFallbackToSystemFiles = { pairMultiImgDocPicker.launch(arrayOf("image/*")) }
            )
        }
    }

    previewBatchItem?.let { item ->
        BatchVideoPreviewDialog(
            item = item,
            clipLimitMs = batchClipLimitMs,
            onDismiss = { previewBatchItem = null },
            onOpenInTrimmer = {
                previewBatchItem = null
                viewModel.clearBatchVideoQueue()
                viewModel.selectVideoForTrim(context, item.uri)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (batchVideoQueue.isNotEmpty()) {
            // Batch video queue screen
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { viewModel.clearBatchVideoQueue() },
                    enabled = !isBatchConverting
                ) {
                    Text(stringResource(R.string.btn_cancel), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
                Text(
                    text = stringResource(R.string.batch_video_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = {
                        appendVideosToBatch = true
                        if (useBuiltInPicker) {
                            showInAppVideoPicker = true
                        } else {
                            documentVideosPicker.launch(arrayOf("video/*"))
                        }
                    },
                    enabled = !isBatchConverting
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.batch_add_more_videos), fontWeight = FontWeight.Bold)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.batch_clip_duration_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            3000L to stringResource(R.string.batch_clip_3s),
                            5000L to stringResource(R.string.batch_clip_5s),
                            15000L to stringResource(R.string.batch_clip_full)
                        ).forEach { (dur, label) ->
                            FilterChip(
                                selected = batchClipLimitMs == dur,
                                onClick = { if (!isBatchConverting) batchClipLimitMs = dur },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.batch_cover_mode_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            LivePhotoViewModel.BatchCoverMode.FIRST_FRAME to stringResource(R.string.batch_cover_first),
                            LivePhotoViewModel.BatchCoverMode.MIDDLE_FRAME to stringResource(R.string.batch_cover_middle)
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = batchCoverMode == mode,
                                onClick = { if (!isBatchConverting) batchCoverMode = mode },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.brand_protocol_setting),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                BrandProtocolRow(brandMode = brandMode, onSelect = { viewModel.setBrandMode(it) })
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.batch_video_count, batchVideoQueue.size),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            batchVideoQueue.forEach { item ->
                BatchVideoItemCard(
                    item = item,
                    isBatchConverting = isBatchConverting,
                    onPreview = { previewBatchItem = item },
                    onRemove = { viewModel.removeBatchVideoItem(item.uri) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.startBatchVideoToLivePhoto(context, batchClipLimitMs, batchCoverMode) },
                enabled = !isBatchConverting && batchVideoQueue.isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(stringResource(R.string.btn_start_batch_convert, batchVideoQueue.size), fontWeight = FontWeight.Bold)
            }

            generationResult?.let {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        } else if (isInPairEditor) {
            // Dual-Select Pair or Multi-Image Synthesis Editor
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            DebugLogManager.interaction("ConvertScreen", "Click Cancel Pair/Multi Synthesis")
                            pairVideoUri = null
                            multiImageUris = emptyList()
                            viewModel.selectVideoForTrim(context, null)
                        },
                        enabled = !isGenerating
                    ) {
                        Text(stringResource(R.string.btn_cancel), color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        text = if (multiImageUris.isNotEmpty()) stringResource(R.string.multi_image_synthesis) else stringResource(R.string.manual_pair_synthesis),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            if (multiImageUris.isNotEmpty()) {
                                DebugLogManager.interaction("ConvertScreen", "Click Synthesize Multiple Images: count=${multiImageUris.size}")
                                viewModel.generateFromMultipleImages(context, multiImageUris, title)
                            } else if (pairImageUri != null && pairVideoUri != null) {
                                DebugLogManager.interaction("ConvertScreen", "Click Synthesize Live Photo: title=$title")
                                viewModel.setCustomCover(pairImageUri!!)
                                viewModel.generateLivePhoto(context, title)
                            }
                        },
                        enabled = !isGenerating && (pairImageUri != null) && (pairVideoUri != null || multiImageUris.isNotEmpty())
                    ) {
                        Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (pairVideoUri != null) {
                    pairImageUri?.let { cover ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = cover,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.pair_cover_attached),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    if (useBuiltInPicker) activePairPickerMode = "single_image"
                                    else pairSingleImgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }) {
                                    Text(stringResource(R.string.click_to_reselect), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        InlineVideoPlayer(
                            videoUri = pairVideoUri!!,
                            trimStartMs = trimStartMs,
                            trimEndMs = trimEndMs,
                            videoDurationMs = videoDuration,
                            previewSeekTimeMs = previewSeekTimeMs,
                            isBoundaryPreview = isBoundaryPreview,
                            isPausedExternal = isGenerating,
                            onClearPreviewSeek = {
                                previewSeekTimeMs = null
                                isBoundaryPreview = false
                            },
                            onPositionUpdate = { pos -> currentPlaybackPositionMs = pos },
                            onDurationDetected = { realDur ->
                                viewModel.onPlayerDurationResolved(context, realDur)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    VideoTrimSlider(
                        frames = frames,
                        videoDuration = videoDuration,
                        trimStartMs = trimStartMs,
                        trimEndMs = trimEndMs,
                        currentPlaybackMs = currentPlaybackPositionMs,
                        onTrimChanged = { start, end -> viewModel.updateTrimRange(start, end) },
                        onScrubbing = { scrubMs ->
                            isBoundaryPreview = false
                            previewSeekTimeMs = scrubMs
                            if (scrubMs != null) currentPlaybackPositionMs = scrubMs
                        },
                        onBoundaryPreview = { boundaryMs ->
                            isBoundaryPreview = boundaryMs != null
                            previewSeekTimeMs = boundaryMs
                        },
                        onWindowChanged = { winStart, winEnd ->
                            viewModel.refreshTimelineWindowThumbnails(context, winStart, winEnd)
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (pairImageUri != null) {
                            AsyncImage(
                                model = pairImageUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.brand_protocol_setting),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BrandProtocolRow(brandMode = brandMode, onSelect = { viewModel.setBrandMode(it) })
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.name_photo_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    enabled = !isGenerating
                )

                generationResult?.let {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        it,
                        color = if (it.contains("成功") || it.contains("完成")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        } else if (selectedVideoUri != null) {
            // Single Video Trimmer
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        DebugLogManager.interaction("ConvertScreen", "Click Cancel Video Selection")
                        viewModel.selectVideoForTrim(context, null)
                        title = ""
                    }
                ) {
                    Text(
                        stringResource(R.string.btn_cancel),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = stringResource(R.string.convert_screen_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = {
                        DebugLogManager.interaction("ConvertScreen", "Click Save LivePhoto: title=$title")
                        viewModel.generateLivePhoto(context, title)
                    },
                    enabled = !isGenerating
                ) {
                    Text(
                        stringResource(R.string.btn_save),
                        color = if (isGenerating) Color.Gray else MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
            ) {
                InlineVideoPlayer(
                    videoUri = selectedVideoUri!!,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    videoDurationMs = videoDuration,
                    previewSeekTimeMs = previewSeekTimeMs,
                    isBoundaryPreview = isBoundaryPreview,
                    isPausedExternal = isGenerating,
                    onClearPreviewSeek = {
                        previewSeekTimeMs = null
                        isBoundaryPreview = false
                    },
                    onPositionUpdate = { pos -> currentPlaybackPositionMs = pos },
                    onDurationDetected = { realDur ->
                        viewModel.onPlayerDurationResolved(context, realDur)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            VideoTrimSlider(
                frames = frames,
                videoDuration = videoDuration,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                currentPlaybackMs = currentPlaybackPositionMs,
                onTrimChanged = { start, end ->
                    viewModel.updateTrimRange(start, end)
                },
                onScrubbing = { scrubMs ->
                    isBoundaryPreview = false
                    previewSeekTimeMs = scrubMs
                    if (scrubMs != null) {
                        currentPlaybackPositionMs = scrubMs
                    }
                },
                onBoundaryPreview = { boundaryMs ->
                    isBoundaryPreview = boundaryMs != null
                    previewSeekTimeMs = boundaryMs
                },
                onWindowChanged = { winStart, winEnd ->
                    viewModel.refreshTimelineWindowThumbnails(context, winStart, winEnd)
                }
            )

            val clipDurationMs = trimEndMs - trimStartMs
            if (clipDurationMs > 4000L) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.duration_recommendation),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.cover_frame_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val relativeCoverSec = ((coverTimeMs - trimStartMs).coerceAtLeast(0L) / 100L) / 10f
                        Text(
                            text = stringResource(R.string.cover_time_point, "%.1f".format(relativeCoverSec)),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            if (customCoverUri != null) {
                                AsyncImage(
                                    model = customCoverUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (extractedCoverFrame != null) {
                                Image(
                                    bitmap = extractedCoverFrame!!.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = {
                                    DebugLogManager.interaction("ConvertScreen", "Set current frame as cover at $currentPlaybackPositionMs ms")
                                    viewModel.extractFrameAt(context, currentPlaybackPositionMs)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.btn_set_current_frame_cover), fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedButton(
                                onClick = {
                                    DebugLogManager.interaction("ConvertScreen", "Launch custom cover picker (useBuiltIn=$useBuiltInPicker)")
                                    if (useBuiltInPicker) {
                                        showInAppCoverPicker = true
                                    } else {
                                        customCoverPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (customCoverUri != null) stringResource(R.string.custom_image_selected) else stringResource(R.string.btn_pick_cover_from_gallery),
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedButton(
                                onClick = { customCoverFilePicker.launch(arrayOf("image/*")) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.btn_pick_cover_file), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.brand_protocol_setting),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                BrandProtocolRow(brandMode = brandMode, onSelect = { viewModel.setBrandMode(it) })
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.name_photo_optional)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            if (isGenerating) {
                Spacer(modifier = Modifier.height(20.dp))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.generating_live_photo), style = MaterialTheme.typography.bodySmall)
            }

            generationResult?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    it,
                    color = if (it.contains("成功") || it.contains("完成")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        } else {
            // Landing Page with top mode switcher: Video to Live OR Dual-Select Pair / Multi-Image
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                SegmentedButton(
                    selected = convertSubMode == 0,
                    onClick = { convertSubMode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.testTag("mode_video_to_live")
                ) {
                    Text(stringResource(R.string.convert_mode_video), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SegmentedButton(
                    selected = convertSubMode == 1,
                    onClick = { convertSubMode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("mode_pair_and_multi")
                ) {
                    Text(stringResource(R.string.convert_mode_pair_multi), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (convertSubMode == 0) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    modifier = Modifier.size(68.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.convert_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.convert_subtitle_v2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(26.dp))

                Button(
                    onClick = {
                        DebugLogManager.interaction("ConvertScreen", "Click Default Video Entry (useBuiltIn=$useBuiltInPicker)")
                        appendVideosToBatch = false
                        if (useBuiltInPicker) {
                            showInAppVideoPicker = true
                        } else {
                            multiVideoSystemPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().testTag("btn_select_video_default")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(stringResource(R.string.btn_select_video_default), fontWeight = FontWeight.Bold)
                        Text(
                            text = if (useBuiltInPicker) stringResource(R.string.btn_select_video_default_sub_builtin) else stringResource(R.string.btn_select_video_default_sub_system),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        DebugLogManager.interaction("ConvertScreen", "Click Document File Entry")
                        appendVideosToBatch = false
                        documentVideosPicker.launch(arrayOf("video/*"))
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("btn_pick_video_files")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.padding(vertical = 4.dp).weight(1f)) {
                        Text(stringResource(R.string.btn_pick_video_files), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            stringResource(R.string.convert_file_entry_desc),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1.4f))
            } else {
                // Dual-select pair + Multi-image landing inside Tab 2
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.manual_pair_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.manual_pair_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    modifier = Modifier
                        .animateContentSize()
                        .fillMaxWidth()
                        .height(94.dp)
                        .clickable {
                            if (useBuiltInPicker) activePairPickerMode = "single_image"
                            else pairSingleImgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (pairImageUri != null) {
                            AsyncImage(
                                model = pairImageUri,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ImageSearch, contentDescription = null)
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (pairImageUri != null) stringResource(R.string.selected_single_image) else stringResource(R.string.select_static_cover),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (pairImageUri != null) stringResource(R.string.click_to_reselect) else stringResource(R.string.as_live_cover),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { pairSingleImgDocPicker.launch(arrayOf("image/*")) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.btn_pick_from_file_preserve), modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier
                        .animateContentSize()
                        .fillMaxWidth()
                        .height(94.dp)
                        .clickable {
                            if (useBuiltInPicker) activePairPickerMode = "single_video"
                            else pairVidPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.select_matching_video), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.compose_with_cover), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { pairVidDocPicker.launch(arrayOf("video/*")) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.btn_pick_from_file_preserve), modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(stringResource(R.string.or_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    modifier = Modifier
                        .animateContentSize()
                        .fillMaxWidth()
                        .height(94.dp)
                        .clickable {
                            if (useBuiltInPicker) activePairPickerMode = "multi_image"
                            else pairMultiImgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Collections, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.select_multiple_images), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(stringResource(R.string.auto_generate_animation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                        }
                        IconButton(onClick = { pairMultiImgDocPicker.launch(arrayOf("image/*")) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.btn_pick_from_file_preserve), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun BatchVideoItemCard(
    item: LivePhotoViewModel.BatchVideoItem,
    isBatchConverting: Boolean,
    onPreview: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(initialValue = VideoThumbnailCache.getFromMemory(item.uri, 0L, 180, 180), key1 = item.uri) {
        value = VideoThumbnailCache.getOrExtractFrame(
            context = context,
            uri = item.uri,
            timeMs = 0L,
            targetWidth = 180,
            targetHeight = 180
        )
    }

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
                if (thumb != null && !thumb!!.isRecycled) {
                    Image(
                        bitmap = thumb!!.asImageBitmap(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.archive_preview_btn),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.durationMs > 0L) {
                    Text(
                        text = "%.1fs · %s".format(item.durationMs / 1000f, stringResource(R.string.archive_preview_btn)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.archive_preview_btn),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            when (item.status) {
                1 -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                2 -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                3 -> Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                else -> {
                    IconButton(
                        onClick = onRemove,
                        enabled = !isBatchConverting
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchVideoPreviewDialog(
    item: LivePhotoViewModel.BatchVideoItem,
    clipLimitMs: Long,
    onDismiss: () -> Unit,
    onOpenInTrimmer: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }

    val effectiveClipEnd = if (item.durationMs > 0L) minOf(item.durationMs, clipLimitMs) else clipLimitMs

    val exoPlayer = remember(item.uri) {
        PlayerDecoderConfig.createExoPlayer(context).apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
    }

    LaunchedEffect(exoPlayer, isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(exoPlayer, effectiveClipEnd, isPlaying) {
        while (isPlaying) {
            if (effectiveClipEnd > 300L && exoPlayer.currentPosition >= effectiveClipEnd) {
                exoPlayer.seekTo(0L)
            }
            delay(80L)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp)),
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
                            text = stringResource(R.string.batch_preview_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.title,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row {
                        IconButton(onClick = { isMuted = !isMuted }) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null
                            )
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
                        .clickable { isPlaying = !isPlaying },
                    contentAlignment = Alignment.Center
                ) {
                    ExoTextureVideoView(
                        exoPlayer = exoPlayer,
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (!isPlaying) {
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

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { isPlaying = !isPlaying },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPlaying) stringResource(R.string.picker_preview_pause) else stringResource(R.string.picker_preview_play))
                    }
                    Button(
                        onClick = onOpenInTrimmer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.batch_preview_open_trimmer))
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandProtocolRow(
    brandMode: LivePhotoBrandMode,
    onSelect: (LivePhotoBrandMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LivePhotoBrandMode.entries.forEach { mode ->
            val isSelected = brandMode == mode
            val labelText = when (mode) {
                LivePhotoBrandMode.FUSION -> stringResource(R.string.protocol_fusion)
                LivePhotoBrandMode.OPPO -> stringResource(R.string.protocol_oppo)
                LivePhotoBrandMode.XIAOMI -> stringResource(R.string.protocol_xiaomi)
                LivePhotoBrandMode.SAMSUNG -> stringResource(R.string.protocol_samsung)
                LivePhotoBrandMode.VIVO -> stringResource(R.string.protocol_vivo)
                LivePhotoBrandMode.GOOGLE -> stringResource(R.string.protocol_google)
            }
            Button(
                onClick = {
                    DebugLogManager.interaction("ConvertScreen", "Selected brand protocol: ${mode.name}")
                    onSelect(mode)
                },
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
