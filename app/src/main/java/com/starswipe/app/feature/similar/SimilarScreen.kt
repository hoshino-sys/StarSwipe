package com.starswipe.app.feature.similar

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.starswipe.app.core.designsystem.component.bounceClick
import com.starswipe.app.core.designsystem.theme.AccentDeleteRed
import com.starswipe.app.core.designsystem.theme.DarkGreen
import com.starswipe.app.core.designsystem.theme.GoldStar
import com.starswipe.app.core.designsystem.theme.NeonGreen
import com.starswipe.app.core.designsystem.theme.PureWhite
import com.starswipe.app.core.mediastore.SimilarGroup
import com.starswipe.app.core.model.CleanupStats
import com.starswipe.app.core.model.MediaItem

@Composable
fun SimilarScreen(
    viewModel: SimilarViewModel = viewModel(factory = SimilarViewModel.provideFactory(LocalContext.current)),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDeletionResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(uiState.deleteIntentSender) {
        uiState.deleteIntentSender?.let { sender ->
            val request = IntentSenderRequest.Builder(sender).build()
            deleteLauncher.launch(request)
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 10.dp)
            ) {
                Text(
                    text = "相似照片",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Year Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.availableYears.forEach { year ->
                        val selected = year == uiState.selectedYear
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectYear(year) },
                            label = { Text(text = year, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DarkGreen,
                                selectedLabelColor = PureWhite,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(20.dp),
                            border = null
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
            // Summary Card (Only when releasable space > 0)
            if (uiState.totalReleasableBytes > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "最多可释放",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = uiState.formattedTotalReleasable,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonGreen
                            )
                        }

                        Button(
                            onClick = { viewModel.smartCleanAll() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkGreen,
                                contentColor = PureWhite
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "智能清理", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Animated Content Container for Seamless Year Transitions
            AnimatedContent(
                targetState = uiState.selectedYear,
                transitionSpec = {
                    (slideInHorizontally { width -> width / 4 } + fadeIn())
                        .togetherWith(slideOutHorizontally { width -> -width / 4 } + fadeOut())
                },
                label = "yearTransition"
            ) { _ ->
                Column(modifier = Modifier.fillMaxSize()) {
                    // Scanning progress
                    if (uiState.isScanning) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "正在智能比对相似照片 ${(uiState.scanProgress * 100).toInt()}%...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { uiState.scanProgress },
                                color = NeonGreen,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                            )
                        }
                    } else if (uiState.groups.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(NeonGreen.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = NeonGreen,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "未发现相似照片",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "所选范围内的照片都独一无二，无需清理",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                            items(
                                items = uiState.groups,
                                key = { it.id },
                                contentType = { "similar_group" }
                            ) { group ->
                                SimilarGroupCard(
                                    group = group,
                                    onToggleItem = { item -> viewModel.toggleItemSelection(group.id, item.id) },
                                    onDeleteGroup = { viewModel.deleteGroupSelection(group) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom progressive frosted gradient blur
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(115.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )
    }
}
}

@Composable
private fun SimilarGroupCard(
    group: SimilarGroup,
    onToggleItem: (MediaItem) -> Unit,
    onDeleteGroup: () -> Unit
) {
    val selectedCount = remember(group.selectedForDelete) {
        group.similarItems.count { group.selectedForDelete.contains(it.id) }
    }
    val formattedGroupSize = remember(group.selectedForDelete) {
        CleanupStats.formatBytes(group.totalReleasableBytes)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Group Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "相似连拍 · 共 ${group.similarItems.size + 1} 张",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "可释放 $formattedGroupSize",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Horizontal media items row
            val groupScrollState = remember(group.id) { androidx.compose.foundation.ScrollState(0) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(groupScrollState),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Best item (first)
                SimilarPhotoItem(
                    item = group.bestItem,
                    isBest = true,
                    isSelected = false,
                    onClick = { /* best is kept by default */ }
                )

                // Other similar items
                group.similarItems.forEach { item ->
                    val isSelected = group.selectedForDelete.contains(item.id)
                    SimilarPhotoItem(
                        item = item,
                        isBest = false,
                        isSelected = isSelected,
                        onClick = { onToggleItem(item) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Delete Selected Button for this group
            Button(
                onClick = onDeleteGroup,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkGreen,
                    contentColor = PureWhite,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "删除选中 $selectedCount 张 · 释放 $formattedGroupSize",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SimilarPhotoItem(
    item: MediaItem,
    isBest: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(240, 300)
            .crossfade(false)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .allowRgb565(true)
            .allowHardware(true)
            .build()
    }

    Box(
        modifier = Modifier
            .size(120.dp, 150.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick) // Direct clickable without expensive composed animation coroutines
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Best Badge
        if (isBest) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = GoldStar,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "最优",
                        color = GoldStar,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // Delete Selection Checkbox
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AccentDeleteRed else Color(0x66000000))
                    .border(1.5.dp, PureWhite, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "待删除",
                        tint = PureWhite,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Bottom File Size
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0x88000000))
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.formattedSize,
                color = PureWhite,
                fontSize = 10.sp
            )
        }
    }
}
