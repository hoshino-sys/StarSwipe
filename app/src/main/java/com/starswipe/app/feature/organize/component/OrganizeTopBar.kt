package com.starswipe.app.feature.organize.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.starswipe.app.core.designsystem.theme.AccentDeleteRed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starswipe.app.core.designsystem.component.bounceClick
import com.starswipe.app.core.designsystem.theme.DarkGreen
import com.starswipe.app.core.designsystem.theme.NeonGreen
import com.starswipe.app.core.designsystem.theme.PureWhite
import com.starswipe.app.core.model.MediaType

@Composable
fun OrganizeTopBar(
    selectedCategory: MediaType,
    categoryCounts: Map<MediaType, Int>,
    onSelectCategory: (MediaType) -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    pendingDeleteCount: Int = 0,
    onOpenTrash: () -> Unit = {},
    currentIndex: Int,
    totalInRound: Int,
    progress: Float,
    progressEmoji: String,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val buttonScale by animateFloatAsState(
        targetValue = if (menuExpanded) 1.05f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "categoryButtonSpring"
    )

    val arrowRotation by animateFloatAsState(
        targetValue = if (menuExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "arrowRotationSpring"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .statusBarsPadding()
            .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 4.dp)
    ) {
        // Top Navigation Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Dropdown with Bounce Touch & Spring Animation
            Box {
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = buttonScale
                            scaleY = buttonScale
                        }
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .bounceClick(scaleDown = 0.93f) { menuExpanded = !menuExpanded }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val count = categoryCounts[selectedCategory] ?: 0
                    Text(
                        text = selectedCategory.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(DarkGreen)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$count",
                            color = PureWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "选择分类",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = arrowRotation }
                    )
                }

                CategoryDropdownPopup(
                    menuExpanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    selectedCategory = selectedCategory,
                    categoryCounts = categoryCounts,
                    onSelectCategory = onSelectCategory
                )
            }

            // Right Actions: Undo & Round progress badge
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier
                        .size(38.dp)
                        .bounceClick(scaleDown = 0.88f) { if (canUndo) onUndo() }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "撤回",
                        tint = if (canUndo) NeonGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Trash Can Button with Pending Delete Red Badge
                Box {
                    IconButton(
                        onClick = onOpenTrash,
                        modifier = Modifier
                            .size(38.dp)
                            .bounceClick(scaleDown = 0.88f, onClick = onOpenTrash)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "查看待删除",
                            tint = if (pendingDeleteCount > 0) AccentDeleteRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (pendingDeleteCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-2).dp)
                                .clip(CircleShape)
                                .background(AccentDeleteRed)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "$pendingDeleteCount",
                                color = PureWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "$currentIndex / $totalInRound",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Fun Dynamic Progress Bar with animated Emoji Runner (Spring Physics)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        ) {
            val totalTrackWidth = maxWidth.coerceAtLeast(0.dp)
            val safeTargetProgress = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
            val animatedProgress by animateFloatAsState(
                targetValue = safeTargetProgress,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "progressSpring"
            )
            val currentProgress = if (animatedProgress.isNaN()) 0f else animatedProgress.coerceIn(0f, 1f)

            // Background track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            // Neon green progress bar
            val currentFillWidth = (totalTrackWidth * currentProgress).coerceAtLeast(0.dp)
            Box(
                modifier = Modifier
                    .width(currentFillWidth)
                    .height(8.dp)
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(NeonGreen)
            )

            // Floating progress emoji (Spring Bounce runner)
            val travelDistance = (totalTrackWidth - 26.dp).coerceAtLeast(0.dp)
            val emojiOffsetX = travelDistance * currentProgress
            Box(
                modifier = Modifier
                    .offset(x = emojiOffsetX)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = progressEmoji,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun CategoryDropdownPopup(
    menuExpanded: Boolean,
    onDismissRequest: () -> Unit,
    selectedCategory: MediaType,
    categoryCounts: Map<MediaType, Int>,
    onSelectCategory: (MediaType) -> Unit
) {
    if (!menuExpanded) return

    Popup(
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        var isVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { isVisible = true }

        androidx.compose.animation.AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                    scaleIn(
                        initialScale = 0.82f,
                        transformOrigin = TransformOrigin(0f, 0f),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) +
                    expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                    scaleOut(
                        targetScale = 0.85f,
                        transformOrigin = TransformOrigin(0f, 0f),
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) +
                    shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 18.dp,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .width(230.dp)
                    .padding(top = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    MediaType.entries.forEach { category ->
                        val count = categoryCounts[category] ?: 0
                        val isSelected = category == selectedCategory
                        val categoryIcon = when (category) {
                            MediaType.ALL_IMAGES -> Icons.Default.PhotoLibrary
                            MediaType.ALL_VIDEOS -> Icons.Default.VideoLibrary
                            MediaType.PHOTO -> Icons.Default.CameraAlt
                            MediaType.VIDEO -> Icons.Default.Videocam
                            MediaType.LIVE_PHOTO -> Icons.Default.MotionPhotosOn
                            MediaType.SCREENSHOT -> Icons.Default.Smartphone
                            MediaType.SCREEN_RECORDING -> Icons.Default.ScreenShare
                            MediaType.SELFIE -> Icons.Default.Face
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick(scaleDown = 0.96f) {
                                    onSelectCategory(category)
                                    onDismissRequest()
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = categoryIcon,
                                contentDescription = null,
                                tint = if (isSelected) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) NeonGreen else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (category.isVideo) "$count 条" else "$count 张",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) NeonGreen.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
