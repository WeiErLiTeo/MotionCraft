package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.protocol.LivePhotoBrandMode
import com.example.util.MediaPermissionHelper
import com.example.viewmodel.LivePhotoViewModel

@Composable
fun OnboardingScreen(
    viewModel: LivePhotoViewModel,
    allowBackDismiss: Boolean = false,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var step by remember { mutableIntStateOf(0) }
    var permissionTick by remember { mutableIntStateOf(0) }

    val useBuiltInPicker by viewModel.useBuiltInFilePicker.collectAsStateWithLifecycle()
    val brandMode by viewModel.brandMode.collectAsStateWithLifecycle()

    val hasVideo = remember(permissionTick) { MediaPermissionHelper.hasVideoAccess(context) }
    val hasImage = remember(permissionTick) { MediaPermissionHelper.hasImageAccess(context) }
    val hasLocation = remember(permissionTick) { MediaPermissionHelper.hasMediaLocationAccess(context) }
    val hasFullMedia = remember(permissionTick) { MediaPermissionHelper.hasFullMediaAccess(context) }
    val isPartial = remember(permissionTick) { MediaPermissionHelper.isPartialPhotoAccessOnly(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionTick++
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = step > 0 || allowBackDismiss) {
        if (step > 0) {
            step = 0
        } else if (allowBackDismiss) {
            onFinished()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_step_indicator, step + 1),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                TextButton(
                    onClick = {
                        viewModel.completeOnboarding()
                        onFinished()
                    }
                ) {
                    Text(stringResource(R.string.btn_skip_for_now))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f),
                label = "onboarding_step_anim"
            ) { currentStep ->
                if (currentStep == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.onboarding_welcome_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.onboarding_perm_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.onboarding_perm_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        PermissionStatusCard(
                            icon = Icons.Default.Movie,
                            title = stringResource(R.string.perm_video_title),
                            description = stringResource(R.string.perm_video_desc),
                            isGranted = hasVideo,
                            isPartial = isPartial
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PermissionStatusCard(
                            icon = Icons.Default.PhotoLibrary,
                            title = stringResource(R.string.perm_image_title),
                            description = stringResource(R.string.perm_image_desc),
                            isGranted = hasImage,
                            isPartial = isPartial
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PermissionStatusCard(
                            icon = Icons.Default.LocationOn,
                            title = stringResource(R.string.perm_location_title),
                            description = stringResource(R.string.perm_location_desc),
                            isGranted = hasLocation,
                            isPartial = false
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.onboarding_setup_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.onboarding_setup_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = stringResource(R.string.use_built_in_picker_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.use_built_in_picker_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = useBuiltInPicker,
                                    onCheckedChange = { viewModel.setUseBuiltInFilePicker(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.onboarding_brand_confirm_label),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LivePhotoBrandMode.entries.forEach { mode ->
                                        val labelText = when (mode) {
                                            LivePhotoBrandMode.FUSION -> stringResource(R.string.protocol_fusion)
                                            LivePhotoBrandMode.OPPO -> stringResource(R.string.protocol_oppo)
                                            LivePhotoBrandMode.XIAOMI -> stringResource(R.string.protocol_xiaomi)
                                            LivePhotoBrandMode.SAMSUNG -> stringResource(R.string.protocol_samsung)
                                            LivePhotoBrandMode.VIVO -> stringResource(R.string.protocol_vivo)
                                            LivePhotoBrandMode.GOOGLE -> stringResource(R.string.protocol_google)
                                        }
                                        FilterChip(
                                            selected = brandMode == mode,
                                            onClick = { viewModel.setBrandMode(mode) },
                                            label = { Text(labelText) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (step == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!hasFullMedia || !hasLocation) {
                        Button(
                            onClick = {
                                permissionLauncher.launch(MediaPermissionHelper.getMediaPermissionsToRequest())
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(stringResource(R.string.btn_grant_media_permissions), fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { MediaPermissionHelper.openAppPermissionSettings(context) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(stringResource(R.string.btn_open_app_settings), fontSize = 13.sp)
                        }
                        if (hasFullMedia && hasLocation) {
                            Button(
                                onClick = { step = 1 },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(stringResource(R.string.btn_continue), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { step = 1 },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(stringResource(R.string.btn_continue), fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        viewModel.completeOnboarding()
                        onFinished()
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.btn_finish_onboarding), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isPartial: Boolean
) {
    val badgeBg = when {
        isGranted && !isPartial -> MaterialTheme.colorScheme.primaryContainer
        isPartial -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val badgeFg = when {
        isGranted && !isPartial -> MaterialTheme.colorScheme.onPrimaryContainer
        isPartial -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val badgeText = when {
        isPartial -> stringResource(R.string.perm_state_partial)
        isGranted -> stringResource(R.string.perm_state_granted)
        else -> stringResource(R.string.perm_state_pending)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = badgeBg) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isGranted && !isPartial) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = badgeFg, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(badgeText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = badgeFg)
                }
            }
        }
    }
}
