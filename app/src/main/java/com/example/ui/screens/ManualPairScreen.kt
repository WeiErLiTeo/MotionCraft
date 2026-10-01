package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.components.InlineVideoPlayer
import com.example.ui.components.VideoTrimSlider
import com.example.util.DebugLogManager
import com.example.viewmodel.LivePhotoViewModel

@Composable
fun ManualPairScreen(viewModel: LivePhotoViewModel) {
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
            DebugLogManager.interaction("ManualPairScreen", "Single image selected: $uri")
            imageUri = uri
            multiImageUris = emptyList()
            if (title.isBlank()) {
                title = viewModel.queryFileName(context, uri)
            }
        }
    }

    val vidPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            DebugLogManager.interaction("ManualPairScreen", "Matching video selected: $uri")
            videoUri = uri
            multiImageUris = emptyList()
            viewModel.selectVideoForTrim(context, uri)
            if (title.isBlank()) {
                title = viewModel.queryFileName(context, uri)
            }
        }
    }

    val multiImgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            DebugLogManager.interaction("ManualPairScreen", "Multiple images selected: count=${uris.size}")
            multiImageUris = uris
            imageUri = uris.first()
            videoUri = null
            if (title.isBlank()) {
                title = viewModel.queryFileName(context, uris.first())
            }
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
                        DebugLogManager.interaction("ManualPairScreen", "Click Cancel Synthesis")
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
                                DebugLogManager.interaction("ManualPairScreen", "Click Synthesize Multiple Images: count=${multiImageUris.size}")
                                viewModel.generateFromMultipleImages(context, multiImageUris, title)
                            } else if (imageUri != null && videoUri != null) {
                                DebugLogManager.interaction("ManualPairScreen", "Click Synthesize Live Photo: title=$title")
                                viewModel.setCustomCover(imageUri!!)
                                viewModel.generateLivePhoto(context, title)
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
