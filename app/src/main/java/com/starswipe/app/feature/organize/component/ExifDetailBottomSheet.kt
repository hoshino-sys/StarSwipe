package com.starswipe.app.feature.organize.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.starswipe.app.core.designsystem.component.bounceClick
import com.starswipe.app.core.model.ExifInfo
import com.starswipe.app.core.model.MediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ExifDetailBottomSheet(
    visible: Boolean,
    mediaItem: MediaItem,
    exifInfo: ExifInfo?,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val coroutineScope = rememberCoroutineScope()
    var sheetVisible by remember { mutableStateOf(false) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        sheetVisible = true
    }

    val closeSheet: () -> Unit = {
        sheetVisible = false
        coroutineScope.launch {
            delay(220)
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = closeSheet,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (sheetVisible) 0.55f else 0f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { closeSheet() },
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = sheetVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = 0.70f, // Subtle inertia bounce upward and settle
                        stiffness = 320f
                    )
                ) + fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 450f
                    )
                ) + fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 400.dp, max = 500.dp)
                        .offset { IntOffset(0, offsetY.coerceAtLeast(0f).roundToInt()) }
                        .draggable(
                            state = rememberDraggableState { delta ->
                                offsetY = (offsetY + delta).coerceAtLeast(0f)
                            },
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                if (offsetY > 120f || velocity > 400f) {
                                    closeSheet()
                                }
                                offsetY = 0f
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* prevent dismiss when clicking sheet body */ }
                        .shadow(24.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                        )
                        .padding(horizontal = 24.dp)
                        .padding(top = 12.dp, bottom = 36.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Drag Handle Pill
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(width = 40.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "详细拍摄参数 (EXIF)",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = mediaItem.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = closeSheet,
                                modifier = Modifier
                                    .size(32.dp)
                                    .bounceClick(scaleDown = 0.88f) { closeSheet() }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Top Metric Badges (Aperture, Shutter, ISO, Focal)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExifPill(
                                title = "光圈",
                                value = exifInfo?.aperture ?: "未知",
                                modifier = Modifier.weight(1f)
                            )
                            ExifPill(
                                title = "快门",
                                value = exifInfo?.shutterSpeed ?: "未知",
                                modifier = Modifier.weight(1f)
                            )
                            ExifPill(
                                title = "ISO",
                                value = exifInfo?.iso ?: "未知",
                                modifier = Modifier.weight(1f)
                            )
                            ExifPill(
                                title = "焦距",
                                value = exifInfo?.focalLength ?: "未知",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Detailed Property List
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ExifDetailRow(
                                icon = Icons.Default.CameraAlt,
                                label = "拍摄设备",
                                value = exifInfo?.cameraModel ?: "未知设备"
                            )
                            ExifDetailRow(
                                icon = Icons.Default.Schedule,
                                label = "拍摄时间",
                                value = exifInfo?.dateTakenFormatted ?: mediaItem.formattedDate
                            )
                            ExifDetailRow(
                                icon = Icons.Default.Place,
                                label = "拍摄地点",
                                value = exifInfo?.locationAddress ?: "无 GPS 定位信息"
                            )
                            ExifDetailRow(
                                icon = Icons.Default.Info,
                                label = "尺寸 / 格式",
                                value = "${exifInfo?.resolution ?: "未知"} · ${exifInfo?.fileFormat ?: ""}"
                            )
                            ExifDetailRow(
                                icon = Icons.Default.Folder,
                                label = "文件大小",
                                value = exifInfo?.fileSizeFormatted ?: mediaItem.formattedSize
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExifPill(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ExifDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2
        )
    }
}
