package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.protocol.LivePhotoBrandMode
import com.example.ui.components.InlineVideoPlayer
import com.example.ui.components.VideoTrimSlider
import com.example.util.DebugLogManager
import com.example.viewmodel.LivePhotoViewModel

@Composable
fun ConvertScreen(viewModel: LivePhotoViewModel) {
    val context = LocalContext.current
    val selectedVideoUri by viewModel.selectedVideoUri.collectAsStateWithLifecycle()
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

    var title by remember { mutableStateOf("") }
    var currentPlaybackPositionMs by remember { mutableLongStateOf(trimStartMs) }
    var previewSeekTimeMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(defaultTitle) {
        if (defaultTitle.isNotBlank()) {
            title = defaultTitle
        }
    }

    val singleVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.selectVideoForTrim(context, uri)
    }

    val customCoverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            DebugLogManager.interaction("ConvertScreen", "Custom cover picked: $uri")
            viewModel.setCustomCover(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
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
            Text(
                stringResource(R.string.convert_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.convert_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    DebugLogManager.interaction("ConvertScreen", "Click Select Video Button")
                    singleVideoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.btn_select_video), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.weight(1.5f))
        } else {
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
                    previewSeekTimeMs = previewSeekTimeMs,
                    onPositionUpdate = { pos -> currentPlaybackPositionMs = pos },
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
                    previewSeekTimeMs = scrubMs
                    if (scrubMs != null) {
                        currentPlaybackPositionMs = scrubMs
                    }
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
                                    DebugLogManager.interaction("ConvertScreen", "Launch custom cover picker")
                                    customCoverPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LivePhotoBrandMode.entries.forEach { mode ->
                        val isSelected = brandMode == mode
                        Button(
                            onClick = {
                                DebugLogManager.interaction("ConvertScreen", "Selected brand protocol: ${mode.name}")
                                viewModel.setBrandMode(mode)
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
                            Text(mode.displayName, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
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
        }
    }
}
