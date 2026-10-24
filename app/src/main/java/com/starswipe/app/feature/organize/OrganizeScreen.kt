package com.starswipe.app.feature.organize

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.starswipe.app.core.designsystem.theme.DarkGreen
import com.starswipe.app.feature.organize.component.ExifDetailBottomSheet
import com.starswipe.app.feature.organize.component.OrganizeTopBar
import com.starswipe.app.feature.organize.component.SettlementDialog
import com.starswipe.app.feature.organize.component.SwipeCardStack
import com.starswipe.app.feature.organize.component.VideoReelPlayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeScreen(
    viewModel: OrganizeViewModel = viewModel(factory = OrganizeViewModel.provideFactory(LocalContext.current)),
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settlementSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Proactive memory-cache preloading for items 3 & 4 (items 0, 1, 2 are rendered by the stack)
    LaunchedEffect(uiState.currentIndex, uiState.currentBatch) {
        val batch = uiState.currentBatch
        val currentIdx = uiState.currentIndex
        val loader = context.imageLoader
        for (i in 3..4) {
            val nextItem = batch.getOrNull(currentIdx + i) ?: break
            val preloadRequest = ImageRequest.Builder(context)
                .data(nextItem.uri)
                .size(720, 1280)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build()
            loader.enqueue(preloadRequest)
        }
    }

    // Activity Result Launcher for MediaStore Scoped Storage Batch Deletion (API 30+)
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

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 1. Dynamic Ambient Blurred Background (100% Full Display Edge-to-Edge)
        // Flows behind transparent status bar, top bar, card stack, and floating bottom navigation bar
        if (uiState.currentItem != null) {
            val bgRequest = remember(uiState.currentItem!!.uri) {
                ImageRequest.Builder(context)
                    .data(uiState.currentItem!!.uri)
                    .size(100, 180) // Tiny thumbnail for blur: eliminates decoding bottleneck
                    .crossfade(false)
                    .allowHardware(true)
                    .build()
            }
            AsyncImage(
                model = bgRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.25f
                        scaleY = 1.25f
                    }
                    .blur(32.dp)
            )
            // Ambient translucent scrim for crisp contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.58f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }

        // 2. Foreground Layout (Compact Top Bar + Responsive Card Stack)
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            OrganizeTopBar(
                selectedCategory = uiState.selectedCategory,
                categoryCounts = uiState.categoryCounts,
                onSelectCategory = { viewModel.selectCategory(it) },
                canUndo = uiState.canUndo,
                onUndo = { viewModel.undo() },
                pendingDeleteCount = uiState.pendingDeleteItems.size,
                onOpenTrash = { viewModel.openSettlement() },
                currentIndex = (uiState.currentIndex).coerceAtMost(uiState.currentRoundTotal),
                totalInRound = uiState.currentRoundTotal,
                progress = uiState.currentRoundProgress,
                progressEmoji = uiState.progressEmoji
            )

            // Responsive Card Stack Container: fills remaining height between TopBar and Floating Nav Bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 78.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = DarkGreen)
                        }
                    }

                    uiState.currentBatch.isEmpty() || uiState.currentIndex >= uiState.currentBatch.size -> {
                        // Empty State: All items in this batch/category organized!
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = DarkGreen,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "太棒了！已整理完毕",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "当前分类暂无更多待清理媒体，您可以切换到其他板块继续整理。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    uiState.selectedCategory.isVideo -> {
                        // Video Mode: Vertical Reel player with auto-play
                        VideoReelPlayer(
                            items = uiState.currentBatch,
                            currentIndex = uiState.currentIndex,
                            autoPlay = uiState.userPreferences.autoPlayVideo && isActive,
                            soundEnabled = uiState.userPreferences.videoSoundEnabled,
                            onKeep = { viewModel.onSwipeRight(it) },
                            onDelete = { viewModel.onSwipeLeft(it) },
                            onShowExif = { viewModel.showExifDetail(it) }
                        )
                    }

                    else -> {
                        // Photo Mode: Tinder Stack Swiping
                        SwipeCardStack(
                            items = uiState.currentBatch,
                            currentIndex = uiState.currentIndex,
                            onSwipeLeft = { viewModel.onSwipeLeft(it) },
                            onSwipeRight = { viewModel.onSwipeRight(it) },
                            onSwipeUp = { viewModel.showExifDetail(it) }
                        )
                    }
                }
            }
        }

        // 3. EXIF Bottom Sheet & Settlement Dialog on top
        if (uiState.currentItem != null) {
            ExifDetailBottomSheet(
                visible = uiState.showExifBottomSheet,
                mediaItem = uiState.currentItem!!,
                exifInfo = uiState.selectedExifInfo,
                onDismiss = { viewModel.dismissExifDetail() }
            )
        }

        if (uiState.showSettlement) {
            SettlementDialog(
                pendingDeleteItems = uiState.pendingDeleteItems,
                isDeleting = uiState.isSettlementDeleting,
                sheetState = settlementSheetState,
                onToggleItem = { viewModel.toggleSettlementItem(it) },
                onConfirmDelete = { viewModel.startSettlementDeletion() },
                onDismiss = { viewModel.dismissSettlement() }
            )
        }
    }
}
