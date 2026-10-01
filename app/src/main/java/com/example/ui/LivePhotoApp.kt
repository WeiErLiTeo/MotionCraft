@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.LivePhotoRecord
import com.example.ui.components.DebugLogModalSheet
import com.example.ui.components.FloatingDebugPill
import com.example.ui.components.LivePhotoPlaybackOverlay
import com.example.ui.screens.ConvertScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.LivePhotoDetailScreen
import com.example.ui.screens.ManualPairScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.XmpToolScreen
import com.example.util.DebugLogManager
import com.example.viewmodel.LivePhotoViewModel

@Composable
fun LivePhotoApp(viewModel: LivePhotoViewModel) {
    var currentTab by remember { mutableIntStateOf(0) }
    var activeRecordForPlayback by remember { mutableStateOf<LivePhotoRecord?>(null) }
    var activeRecordForDetail by remember { mutableStateOf<LivePhotoRecord?>(null) }
    var isXmpToolOpen by remember { mutableStateOf(false) }
    var isDebugSheetOpen by remember { mutableStateOf(false) }

    val autoPlay by viewModel.autoPlayLivePhoto.collectAsStateWithLifecycle()
    val isFloatingDebugVisible by DebugLogManager.isFloatingWindowVisible.collectAsStateWithLifecycle()
    val selectedVideoUri by viewModel.selectedVideoUri.collectAsStateWithLifecycle()
    val isTrimming = currentTab == 1 && selectedVideoUri != null
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!isTrimming) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 8.dp)
                            .testTag("bottom_nav_bar"),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ImmersiveNavItem(
                            icon = Icons.Default.Collections,
                            label = stringResource(R.string.tab_library),
                            isSelected = currentTab == 0,
                            onClick = {
                                DebugLogManager.interaction("Navigation", "Switch to Library Tab")
                                currentTab = 0
                            }
                        )
                        ImmersiveNavItem(
                            icon = Icons.Default.Movie,
                            label = stringResource(R.string.tab_convert),
                            isSelected = currentTab == 1,
                            onClick = {
                                DebugLogManager.interaction("Navigation", "Switch to Convert Tab")
                                currentTab = 1
                            }
                        )
                        ImmersiveNavItem(
                            icon = Icons.Default.SwapHoriz,
                            label = stringResource(R.string.tab_manual_pair),
                            isSelected = currentTab == 2,
                            onClick = {
                                DebugLogManager.interaction("Navigation", "Switch to Manual Pair Tab")
                                currentTab = 2
                            }
                        )
                        ImmersiveNavItem(
                            icon = Icons.Default.Settings,
                            label = stringResource(R.string.tab_settings),
                            isSelected = currentTab == 3,
                            onClick = {
                                DebugLogManager.interaction("Navigation", "Switch to Settings Tab")
                                currentTab = 3
                            }
                        )
                    }
                }
            },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                AnimatedContent(
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
                            onPlayRecord = { record -> activeRecordForPlayback = record },
                            onOpenDetail = { record -> activeRecordForDetail = record }
                        )
                        1 -> Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) { ConvertScreen(viewModel) }
                        2 -> Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) { ManualPairScreen(viewModel) }
                        3 -> Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) { SettingsScreen(viewModel, onOpenXmpTool = { isXmpToolOpen = true }) }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isXmpToolOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                XmpToolScreen(
                    viewModel = viewModel,
                    onBack = { isXmpToolOpen = false }
                )
            }
        }

        AnimatedVisibility(
            visible = activeRecordForDetail != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            activeRecordForDetail?.let { record ->
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LivePhotoDetailScreen(
                        record = record,
                        viewModel = viewModel,
                        onBack = { activeRecordForDetail = null }
                    )
                }
            }
        }

        AnimatedVisibility(
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
                    onOpenTool = {
                        val r = activeRecordForPlayback
                        activeRecordForPlayback = null
                        activeRecordForDetail = r
                    }
                )
            }
        }

        if (isFloatingDebugVisible) {
            FloatingDebugPill(onClick = { isDebugSheetOpen = true })
        }

        if (isDebugSheetOpen) {
            DebugLogModalSheet(onDismissRequest = { isDebugSheetOpen = false })
        }
    }
}

@Composable
private fun ImmersiveNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Column(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 28.dp),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
    }
}
