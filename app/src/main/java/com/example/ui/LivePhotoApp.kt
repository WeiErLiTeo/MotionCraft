package com.example.ui

import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.util.MotionPhotoHelper
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextOverflow
import java.io.File
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.lifecycle.Lifecycle
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.asImageBitmap
import android.widget.VideoView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.material.icons.filled.SaveAlt
import com.example.data.LivePhotoRecord
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.*
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.media3.ui.PlayerView
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.media3.common.MediaItem
import android.net.Uri
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import com.example.viewmodel.LivePhotoViewModel
import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.rememberScrollState
import androidx.media3.common.Player
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Image


@Composable
fun LivePhotoApp(viewModel: LivePhotoViewModel) {
    var currentTab by remember { mutableIntStateOf(0) }
    var activeRecordForPlayback by remember { mutableStateOf<com.example.data.LivePhotoRecord?>(null) }
    
    val autoPlay by viewModel.autoPlayLivePhoto.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.testTag("bottom_nav_bar"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.Collections, contentDescription = stringResource(R.string.tab_library)) },
                        label = { Text(stringResource(R.string.tab_library), fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.Movie, contentDescription = stringResource(R.string.tab_convert)) },
                        label = { Text(stringResource(R.string.tab_convert), fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.tab_manual_pair)) },
                        label = { Text(stringResource(R.string.tab_manual_pair), fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.tab_settings)) },
                        label = { Text(stringResource(R.string.tab_settings), fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                            )
                        )
                    )
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220))) togetherWith
                        (fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.96f, animationSpec = tween(180)))
                    },
                    label = "tab_animated_content",
                    modifier = Modifier.fillMaxSize()
                ) { tab ->
                    when (tab) {
                        0 -> LibraryScreen(
                            viewModel = viewModel,
                            innerPadding = innerPadding,
                            onPlayRecord = { record -> activeRecordForPlayback = record }
                        )
                        1 -> Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) { ConvertScreen(viewModel) }
                        2 -> Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) { ManualPairScreen(viewModel) }
                        3 -> Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) { SettingsScreen(viewModel) }
                    }
                }
            }
        }

        // Fullscreen Overlay ON TOP OF SCAFFOLD AND BOTTOM BAR
        androidx.compose.animation.AnimatedVisibility(
            visible = activeRecordForPlayback != null,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            activeRecordForPlayback?.let { record ->
                LivePhotoPlaybackOverlay(
                    record = record,
                    autoPlay = autoPlay,
                    onDismiss = { activeRecordForPlayback = null },
                    onSave = { viewModel.saveToGallery(context, record) }
                )
            }
        }
    }
}

// ---------------------- SCREEN 1: LIBRARY ----------------------
@Composable
fun LibraryScreen(viewModel: LivePhotoViewModel, innerPadding: PaddingValues = PaddingValues(), onPlayRecord: (com.example.data.LivePhotoRecord) -> Unit = {}) {
    val context = LocalContext.current
    val livePhotos by viewModel.allLivePhotos.collectAsStateWithLifecycle()
    val autoPlay by viewModel.autoPlayLivePhoto.collectAsStateWithLifecycle()
    
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedRecords by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    
    var showImportDialog by remember { mutableStateOf(false) }
    var importTitle by remember { mutableStateOf("") }
    var selectedImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    val importPicker = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImportUri = uri
            showImportDialog = true
        }
    }
    
    val documentPicker = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedImportUri = uri
            showImportDialog = true
        }
    }
    
    var importMenuExpanded by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // App Header
            androidx.compose.animation.AnimatedContent(
                targetState = isSelectionMode,
                transitionSpec = {
                    androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut()
                }, label = "header"
            ) { selectionMode ->
                if (selectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { 
                            isSelectionMode = false
                            selectedRecords = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                        Text(
                            text = "已选择 ${selectedRecords.size} 项",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { 
                                if (selectedRecords.isNotEmpty()) {
                                    showBatchDeleteConfirm = true
                                }
                            },
                            enabled = selectedRecords.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = if (selectedRecords.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Live Photos",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.library_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Box {
                            Button(
                                onClick = { importMenuExpanded = true },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.testTag("import_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.btn_import), fontWeight = FontWeight.Bold)
                            }
                            DropdownMenu(
                                expanded = importMenuExpanded,
                                onDismissRequest = { importMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.import_from_gallery)) },
                                    onClick = {
                                        importMenuExpanded = false
                                        importPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.import_from_files)) },
                                    onClick = {
                                        importMenuExpanded = false
                                        documentPicker.launch(arrayOf("image/*"))
                                    },
                                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
                                )
                            }
                        }
                    }
                }
            }
            
            if (livePhotos.isEmpty()) {
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(96.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.empty_library_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                    }
                }
            } else {
                // Photos Grid
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(livePhotos, key = { it.id }) { record ->
                        Box(
                            modifier = Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = androidx.compose.animation.core.tween(300)
                            )
                        ) {
                            LivePhotoCard(
                                record = record,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedRecords.contains(record.id),
                                onClick = {
                                    if (isSelectionMode) {
                                        if (selectedRecords.contains(record.id)) {
                                            selectedRecords = selectedRecords - record.id
                                            if (selectedRecords.isEmpty()) isSelectionMode = false
                                        } else {
                                            selectedRecords = selectedRecords + record.id
                                        }
                                    } else {
                                        onPlayRecord(record)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedRecords = setOf(record.id)
                                    }
                                },
                                onDelete = { viewModel.deleteLivePhoto(record) }
                            )
                        }
                    }
                }
            }
        }
        

    }
    
    // Import Dialog
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("批量删除", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除选中的 ${selectedRecords.size} 条记录吗？(不会删除原文件)") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val recordsToDelete = livePhotos.filter { selectedRecords.contains(it.id) }
                        recordsToDelete.forEach { viewModel.deleteLivePhoto(it) }
                        selectedRecords = emptySet()
                        isSelectionMode = false
                        showBatchDeleteConfirm = false
                    }
                ) {
                    Text("删除", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showImportDialog && selectedImportUri != null) {
        AlertDialog(
            onDismissRequest = {
                showImportDialog = false
                selectedImportUri = null
                importTitle = ""
            },
            title = { Text("导入实况照片", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("系统将扫描此图片是否包含各大厂商(华为、小米、三星、Google)嵌入的实况视频，解析成功后即可直接在库中按压播放！", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = importTitle,
                        onValueChange = { importTitle = it },
                        label = { Text(stringResource(R.string.input_title_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importMotionPhoto(context, selectedImportUri!!, importTitle)
                        showImportDialog = false
                        selectedImportUri = null
                        importTitle = ""
                    }
                ) {
                    Text(stringResource(R.string.btn_parse_import), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        selectedImportUri = null
                        importTitle = ""
                    }
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

// ---------------------- SCREEN 2: CONVERT VIDEO TO LIVE ----------------------
@Composable
fun ConvertScreen(viewModel: com.example.viewmodel.LivePhotoViewModel) {
    val context = LocalContext.current
    val selectedVideoUri by viewModel.selectedVideoUri.collectAsStateWithLifecycle()
    val videoDuration by viewModel.videoDuration.collectAsStateWithLifecycle()
    val trimStartMs by viewModel.trimStartMs.collectAsStateWithLifecycle()
    val trimEndMs by viewModel.trimEndMs.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val generationResult by viewModel.generationResult.collectAsStateWithLifecycle()
    val frames by viewModel.videoFrames.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.selectVideoForTrim(context, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (selectedVideoUri == null) {
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.convert_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.convert_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                modifier = Modifier.height(56.dp).fillMaxWidth(0.8f),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_select_video), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.weight(1.5f))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { viewModel.selectVideoForTrim(context, null) }) {
                        Text(stringResource(R.string.btn_cancel), color = MaterialTheme.colorScheme.error)
                    }
                    Text(stringResource(R.string.convert_edit_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = { viewModel.generateLivePhoto(context, title) },
                        enabled = !isGenerating
                    ) {
                        Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Video Preview 
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f/4f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    InlineVideoPlayer(
                        videoUri = selectedVideoUri!!,
                        trimStartMs = trimStartMs,
                        trimEndMs = trimEndMs,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Trim Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.trim_range), fontWeight = FontWeight.Medium)
                    Text("${"%.1f".format((trimEndMs - trimStartMs) / 1000f)}s", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Trim Slider
                VideoTrimSlider(
                    frames = frames,
                    videoDuration = videoDuration,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    onTrimChanged = { start, end ->
                        viewModel.updateTrimStart(start)
                        viewModel.updateTrimEnd(end)
                    }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.input_title_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                
                if (isGenerating) {
                    Spacer(modifier = Modifier.height(24.dp))
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.generating_live_photo), style = MaterialTheme.typography.bodySmall)
                }
                
                generationResult?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(it, color = if (it.contains("成功") || it.contains("完成")) Color.Green else Color.Red)
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ---------------------- SCREEN 3: MANUAL PAIR ----------------------

@Composable
fun ManualPairScreen(viewModel: com.example.viewmodel.LivePhotoViewModel) {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var multiImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var title by remember { mutableStateOf("") }
    
    val frames by viewModel.videoFrames.collectAsStateWithLifecycle()
    val videoDuration by viewModel.videoDuration.collectAsStateWithLifecycle()
    val trimStartMs by viewModel.trimStartMs.collectAsStateWithLifecycle()
    val trimEndMs by viewModel.trimEndMs.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val generationResult by viewModel.generationResult.collectAsStateWithLifecycle()
    
    val singleImgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            imageUri = uri
            multiImageUris = emptyList() // clear multi-image mode
        }
    }
    
    val vidPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            videoUri = uri
            multiImageUris = emptyList() // clear multi-image mode
            viewModel.selectVideoForTrim(context, uri)
        }
    }

    val multiImgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            multiImageUris = uris
            imageUri = uris.first()
            videoUri = null // clear video mode
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (videoUri == null && multiImageUris.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.manual_pair_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.manual_pair_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(32.dp))
            
            // Image Box
            Card(
                modifier = Modifier.animateContentSize().fillMaxWidth().height(100.dp).clickable { singleImgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = null,
                            modifier = Modifier.size(68.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(68.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ImageSearch, contentDescription = null)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(if (imageUri != null) stringResource(R.string.selected_single_image) else stringResource(R.string.select_static_cover), fontWeight = FontWeight.Bold)
                        Text(if (imageUri != null) stringResource(R.string.click_to_reselect) else stringResource(R.string.as_live_cover), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Video Box
            Card(
                modifier = Modifier.animateContentSize().fillMaxWidth().height(100.dp).clickable { vidPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(68.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.select_matching_video), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.compose_with_cover), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.or_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Multi Image Box
            Card(
                modifier = Modifier.animateContentSize().fillMaxWidth().height(100.dp).clickable { multiImgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(68.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Collections, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.select_multiple_images), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(stringResource(R.string.auto_generate_animation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha=0.8f))
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1.5f))
        } else {
            // Generating Screen for Pair or Multi-Image
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { 
                        videoUri = null 
                        multiImageUris = emptyList()
                        viewModel.selectVideoForTrim(context, null)
                    }) {
                        Text(stringResource(R.string.btn_cancel), color = MaterialTheme.colorScheme.error)
                    }
                    Text(if (multiImageUris.isNotEmpty()) stringResource(R.string.multi_image_synthesis) else stringResource(R.string.manual_pair_synthesis), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = { 
                            if (multiImageUris.isNotEmpty()) {
                                viewModel.generateFromMultipleImages(context, multiImageUris, title)
                            } else if (imageUri != null && videoUri != null) {
                                viewModel.setCustomCover(imageUri!!); viewModel.generateLivePhoto(context, title)
                            }
                        },
                        enabled = !isGenerating && (imageUri != null) && (videoUri != null || multiImageUris.isNotEmpty())
                    ) {
                        Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (videoUri != null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().aspectRatio(3f/4f).clip(RoundedCornerShape(24.dp)).background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            InlineVideoPlayer(videoUri = videoUri!!, trimStartMs = trimStartMs, trimEndMs = trimEndMs)
                            if (isGenerating) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = Color.White)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(stringResource(R.string.generating_live), color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.trim_video_segment), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        VideoTrimSlider(
                            frames = frames,
                            videoDuration = videoDuration,
                            trimStartMs = trimStartMs,
                            trimEndMs = trimEndMs,
                            onTrimChanged = { start, end ->
                                viewModel.updateTrimStart(start)
                                viewModel.updateTrimEnd(end)
                            }
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(3f/4f).clip(RoundedCornerShape(24.dp)).background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageUri != null) {
                            AsyncImage(
                                model = imageUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (isGenerating) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color.White)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(stringResource(R.string.generating_live), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.name_photo_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    enabled = !isGenerating
                )
                
                if (generationResult != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.animateContentSize().fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = if (generationResult!!.contains("成功")) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = generationResult!!,
                            modifier = Modifier.padding(16.dp),
                            color = if (generationResult!!.contains("成功")) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun SettingsScreen(viewModel: com.example.viewmodel.LivePhotoViewModel) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val autoPlay by viewModel.autoPlayLivePhoto.collectAsStateWithLifecycle()



    var cacheResultText by remember { mutableStateOf("") }
    var showXmpTool by remember { mutableStateOf(false) }

    if (showXmpTool) {
        Dialog(onDismissRequest = { showXmpTool = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box {
                    XmpToolScreen(viewModel)
                    IconButton(onClick = { showXmpTool = false }, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(16.dp))

        // 1. Theme Settings Card
        Card(
            modifier = Modifier.animateContentSize().fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.theme_settings), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(stringResource(R.string.dark_mode_setting), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = themeMode == 0, onClick = { viewModel.setThemeMode(0) }, label = { Text(stringResource(R.string.theme_system)) })
                    FilterChip(selected = themeMode == 1, onClick = { viewModel.setThemeMode(1) }, label = { Text(stringResource(R.string.theme_light)) })
                    FilterChip(selected = themeMode == 2, onClick = { viewModel.setThemeMode(2) }, label = { Text(stringResource(R.string.theme_dark)) })
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.dynamic_color), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.supports_android_12_plus), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = dynamicColor, onCheckedChange = { viewModel.setDynamicColor(it) })
                }
                
                if (!dynamicColor || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.theme_palette), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    val paletteItems = listOf(
                        Triple("default", "紫罗兰", Color(0xFF6750A4)),
                        Triple("blue", "蔚蓝", Color(0xFF1976D2)),
                        Triple("green", "翡翠", Color(0xFF388E3C)),
                        Triple("orange", "日落", Color(0xFFF57C00)),
                        Triple("pink", "玫瑰", Color(0xFFC2185B)),
                        Triple("cyan", "海碧", Color(0xFF0097A7)),
                        Triple("purple", "极光", Color(0xFF7B1FA2)),
                        Triple("red", "烈焰", Color(0xFFD32F2F))
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        paletteItems.forEach { (colorKey, label, colorHex) ->
                            val isSelected = themeColor == colorKey
                            val scale by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (isSelected) 1.15f else 1.0f,
                                animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium)
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .scale(scale)
                                    .clip(CircleShape)
                                    .clickable { viewModel.setThemeColor(colorKey) }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(colorHex)
                                        .border(
                                            width = if (isSelected) 3.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

                // Auto Play Settings Card
        Card(
            modifier = Modifier.animateContentSize().fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.auto_play_setting), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.auto_play_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(
                    checked = autoPlay,
                    onCheckedChange = { viewModel.setAutoPlayLivePhoto(it) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Language Settings Card
        Card(
            modifier = Modifier.animateContentSize().fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.language_settings), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = language == "system", onClick = { viewModel.setLanguage("system"); restartApp(context) }, label = { Text("Auto") })
                    FilterChip(selected = language == "en", onClick = { viewModel.setLanguage("en"); restartApp(context) }, label = { Text("English") })
                    FilterChip(selected = language == "ja", onClick = { viewModel.setLanguage("ja"); restartApp(context) }, label = { Text("日本語") })
                    FilterChip(selected = language == "zh-CN", onClick = { viewModel.setLanguage("zh-CN"); restartApp(context) }, label = { Text("简体中文") })
                    FilterChip(selected = language == "zh-TW", onClick = { viewModel.setLanguage("zh-TW"); restartApp(context) }, label = { Text("繁體中文") })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // 2. Storage & Cache Cleaning Card
        Card(
            modifier = Modifier.animateContentSize().fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.storage_cache_management), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.clean_temp_cache_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.clearTempCache(context) { msg ->
                            cacheResultText = msg
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_clean_cache))
                }
                if (cacheResultText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(cacheResultText, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. XMP Diagnostic Card
        Card(
            modifier = Modifier.animateContentSize().fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.xmp_diagnostic_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.xmp_diagnostic_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showXmpTool = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_open_xmp_tool))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. About App & Version Info Card
        Card(
            modifier = Modifier.animateContentSize().fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.about_platform_support), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("当前版本: v1.2.0 (Live Photo Engine)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("支持相册类型: iPhone / 华为 / 小米 / 三星 Motion Photo 标准，自动兼容系统相册及社交平台发布。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


// ---------------------- SCREEN 5: XMP TOOL ----------------------
@Composable
fun XmpToolScreen(viewModel: LivePhotoViewModel) {
    val context = LocalContext.current
    var xmpContent by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var resultMessage by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    xmpContent = com.example.util.MotionPhotoHelper.extractXmpXml(bytes)
                }
            } catch(e: Exception) {
                xmpContent = "读取错误: ${e.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "XMP 元数据分析与编辑",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.ImageSearch, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("选择图片文件")
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (selectedImageUri != null) {
            Text("当前选择的文件: ${selectedImageUri?.lastPathSegment}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = xmpContent,
                onValueChange = { xmpContent = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp),
                textStyle = LocalTextStyle.current.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 10.sp),
                label = { Text("XMP Content") }
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // Inject the new XMP content into the selected image and save it as a new file.
                    // This is for debugging purposes.
                    viewModel.injectCustomXmp(context, selectedImageUri!!, xmpContent) { success, msg ->
                        resultMessage = msg
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("注入自定义 XMP 并另存为")
            }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "视频编码诊断",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (selectedImageUri != null) {
                    // Try to extract video to a temp file and diagnose it
                    viewModel.diagnoseVideo(context, selectedImageUri!!) { msg ->
                        resultMessage = msg
                    }
                } else {
                    resultMessage = "请先选择实况照片"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("检查嵌入的视频编码 (MP4)")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                if (selectedImageUri != null) {
                    viewModel.transcodeAndRepairLivePhoto(context, selectedImageUri!!) { msg ->
                        resultMessage = msg
                    }
                } else {
                    resultMessage = "请先选择实况照片"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Text("一键转码并修复 (MP4 -> AVC)")
        }



            if (resultMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(resultMessage, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }
    }
}


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LivePhotoCard(
    record: com.example.data.LivePhotoRecord,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.animateContentSize()
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .shadow(4.dp, RoundedCornerShape(24.dp))
            .scale(if (isSelectionMode && isSelected) 0.9f else 1f)
            .then(
                if (isSelectionMode && isSelected) {
                    Modifier.border(4.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = record.coverPath,
                contentDescription = record.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Motion Photo Badge
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            // Selection Checkbox or Delete Button
            androidx.compose.animation.AnimatedContent(
                targetState = isSelectionMode,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                label = "delete_or_select"
            ) { selectionMode ->
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f), CircleShape)
                            .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                } else {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            
            // Title Overlay (Only show if it's not a default timestamp title)
            val isDefaultTitle = record.title.matches(Regex(".*_\\d{10,}"))
            if (record.title.isNotEmpty() && !isDefaultTitle) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = record.title,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除确认") },
            text = { Text("确定要删除这条实况记录吗？(不会删除原文件)") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}



@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun InlineVideoPlayer(
    videoUri: android.net.Uri,
    trimStartMs: Long,
    trimEndMs: Long,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUri)
            setMediaItem(mediaItem)
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                exoPlayer.play()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(videoUri) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(trimStartMs, trimEndMs) {
        exoPlayer.seekTo(trimStartMs)
        while(true) {
            if (exoPlayer.currentPosition >= trimEndMs) {
                exoPlayer.seekTo(trimStartMs)
            }
            kotlinx.coroutines.delay(50)
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        modifier = modifier
    )
}



@Composable
fun LivePhotoPlaybackOverlay(
    record: com.example.data.LivePhotoRecord,
    autoPlay: Boolean = false,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(autoPlay) }
    var currentPlaybackPositionMs by remember { mutableLongStateOf(0L) }
    var totalVideoDurationMs by remember { mutableLongStateOf(0L) }
    var playableVideoPath by remember { mutableStateOf(record.videoPath) }

    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val insetsController = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val prevLightStatus = insetsController?.isAppearanceLightStatusBars ?: false
        val prevLightNav = insetsController?.isAppearanceLightNavigationBars ?: false
        
        insetsController?.isAppearanceLightStatusBars = false
        insetsController?.isAppearanceLightNavigationBars = false
        insetsController?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        
        onDispose {
            insetsController?.isAppearanceLightStatusBars = prevLightStatus
            insetsController?.isAppearanceLightNavigationBars = prevLightNav
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(record) {
        val vFile = File(record.videoPath)
        if (record.videoPath.isEmpty() || !vFile.exists() || vFile.length() == 0L) {
            val coverFile = File(record.coverPath)
            if (coverFile.exists()) {
                val cacheDir = File(context.cacheDir, "extracted_mp4").apply { mkdirs() }
                val cacheVideo = File(cacheDir, "${record.id}_video.mp4")
                withContext(Dispatchers.IO) {
                    MotionPhotoHelper.extractVideoFromMotionPhoto(coverFile, cacheVideo)
                }
                if (cacheVideo.exists() && cacheVideo.length() > 0) {
                    playableVideoPath = cacheVideo.absolutePath
                }
            }
        }
        if (playableVideoPath.isNotEmpty()) {
            val mediaItem = MediaItem.fromUri(playableVideoPath)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
                isPlaying = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            } catch (e: Exception) {}
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying, playableVideoPath) {
        if (isPlaying && playableVideoPath.isNotEmpty()) {
            if (exoPlayer.playbackState == Player.STATE_ENDED || currentPlaybackPositionMs == 0L) {
                exoPlayer.seekTo(0)
            }
            exoPlayer.play()
            while (isPlaying) {
                currentPlaybackPositionMs = exoPlayer.currentPosition
                totalVideoDurationMs = exoPlayer.duration.coerceAtLeast(1L)
                if (exoPlayer.playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    break
                }
                kotlinx.coroutines.delay(30)
            }
        } else {
            exoPlayer.pause()
        }
    }

    androidx.activity.compose.BackHandler {
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Full screen ambient blurred background
        AsyncImage(
            model = record.coverPath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(50.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
                .background(Color.Black.copy(alpha = 0.5f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            val scale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isPlaying) 1.02f else 1.0f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                ),
                label = "card_scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(3f / 4f)
                    .scale(scale)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
                    .clickable {
                        isPlaying = !isPlaying
                    },
                contentAlignment = Alignment.Center
            ) {
                // Cover Image
                AsyncImage(
                    model = record.coverPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Embedded Video Player
                androidx.compose.animation.AnimatedVisibility(
                    visible = isPlaying,
                    enter = fadeIn(animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(200))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                controllerAutoShow = false
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Timeline Progress Bar when playing
                androidx.compose.animation.AnimatedVisibility(
                    visible = isPlaying,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        val progress = if (totalVideoDurationMs > 0) (currentPlaybackPositionMs.toFloat() / totalVideoDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val formatTime = { ms: Long ->
                                val s = (ms / 1000) % 60
                                val m = (ms / 1000) / 60
                                "%02d:%02d".format(m, s)
                            }
                            Text(formatTime(currentPlaybackPositionMs), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(formatTime(totalVideoDurationMs), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Play / Replay Badge Indicator when Paused
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isPlaying,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "点击播放实况",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(28.dp))
                }

                IconButton(
                    onClick = onSave,
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(Icons.Default.SaveAlt, contentDescription = "Save", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
fun VideoTrimSlider(
    frames: List<android.graphics.Bitmap>,
    videoDuration: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    onTrimChanged: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val currentTrimStartMs by rememberUpdatedState(trimStartMs)
    val currentTrimEndMs by rememberUpdatedState(trimEndMs)

    BoxWithConstraints(
        modifier = modifier
            .height(72.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.DarkGray)
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (frames.isNotEmpty()) {
                frames.forEach { frame ->
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
        
        if (videoDuration > 0) {
            val startRatio = (trimStartMs.toFloat() / videoDuration.toFloat()).coerceIn(0f, 1f)
            val endRatio = (trimEndMs.toFloat() / videoDuration.toFloat()).coerceIn(0f, 1f)
            
            val startPx = startRatio * widthPx
            val endPx = endRatio * widthPx
            val handleWidthDp = 48.dp
            val handleWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { handleWidthDp.toPx() }
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(0f, 0f),
                    size = Size(startPx, size.height)
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(endPx, 0f),
                    size = Size(size.width - endPx, size.height)
                )
                
                val rectColor = Color(0xFFFFC107)
                drawRect(
                    color = rectColor,
                    topLeft = Offset(startPx, 0f),
                    size = Size(endPx - startPx, size.height),
                    style = Stroke(width = 8f)
                )
            }

            // The drag region between handles
            Box(
                modifier = Modifier
                    .offset { IntOffset(startPx.toInt(), 0) }
                    .width(with(LocalDensity.current) { (endPx - startPx).toDp() })
                    .fillMaxHeight()
                    .pointerInput(videoDuration) {
                        var initialStartMs = 0L
                        var initialEndMs = 0L
                        var accumulatedDx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { 
                                initialStartMs = currentTrimStartMs
                                initialEndMs = currentTrimEndMs
                                accumulatedDx = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDx += dragAmount
                                val dxMs = (accumulatedDx / widthPx * videoDuration).toLong()
                                var newStart = initialStartMs + dxMs
                                var newEnd = initialEndMs + dxMs

                                // Constrain to boundaries
                                if (newStart < 0) {
                                    newEnd -= newStart
                                    newStart = 0
                                }
                                if (newEnd > videoDuration) {
                                    val over = newEnd - videoDuration
                                    newStart -= over
                                    newEnd = videoDuration
                                }
                                
                                if (newStart != currentTrimStartMs || newEnd != currentTrimEndMs) {
                                    onTrimChanged(newStart, newEnd)
                                }
                            }
                        )
                    }
            )
            
            // Left handle badge
            val formatMs = { ms: Long ->
                val totalSec = ms / 1000f
                "%.1fs".format(totalSec)
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset((startPx - handleWidthPx/2).toInt(), 0) }
                    .width(handleWidthDp)
                    .fillMaxHeight()
                    .pointerInput(videoDuration) {
                        var initialStartMs = 0L
                        var accumulatedDx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { 
                                initialStartMs = currentTrimStartMs
                                accumulatedDx = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDx += dragAmount
                                val dxMs = (accumulatedDx / widthPx * videoDuration).toLong()
                                val newStart = (initialStartMs + dxMs).coerceIn(0L, currentTrimEndMs - 300L)
                                if (newStart != currentTrimStartMs) {
                                    onTrimChanged(newStart, currentTrimEndMs)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .background(Color.Yellow, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(formatMs(trimStartMs), color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                            .background(Color.Yellow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                }
            }
            
            // Right handle badge
            Box(
                modifier = Modifier
                    .offset { IntOffset((endPx - handleWidthPx/2).toInt(), 0) }
                    .width(handleWidthDp)
                    .fillMaxHeight()
                    .pointerInput(videoDuration) {
                        var initialEndMs = 0L
                        var accumulatedDx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { 
                                initialEndMs = currentTrimEndMs
                                accumulatedDx = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDx += dragAmount
                                val dxMs = (accumulatedDx / widthPx * videoDuration).toLong()
                                val newEnd = (initialEndMs + dxMs).coerceIn(currentTrimStartMs + 300L, videoDuration)
                                if (newEnd != currentTrimEndMs) {
                                    onTrimChanged(currentTrimStartMs, newEnd)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .background(Color.Yellow, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(formatMs(trimEndMs), color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                            .background(Color.Yellow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

fun restartApp(context: Context) {
    if (context is android.app.Activity) {
        val intent = android.content.Intent(context, com.example.MainActivity::class.java)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        context.finish()
    }
}
