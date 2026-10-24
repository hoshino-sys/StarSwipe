package com.starswipe.app.feature.organize.component

import android.content.Intent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.starswipe.app.core.designsystem.component.bounceClick
import com.starswipe.app.core.designsystem.theme.AccentDeleteRed
import com.starswipe.app.core.designsystem.theme.DarkGreen
import com.starswipe.app.core.designsystem.theme.PureWhite
import com.starswipe.app.core.model.MediaItem

@OptIn(UnstableApi::class)
@Composable
fun VideoReelPlayer(
    items: List<MediaItem>,
    currentIndex: Int,
    autoPlay: Boolean,
    soundEnabled: Boolean,
    onKeep: (MediaItem) -> Unit,
    onDelete: (MediaItem) -> Unit,
    onShowExif: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = currentIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        pageCount = { items.size }
    )

    VerticalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize()
    ) { page ->
        val item = items.getOrNull(page)
        if (item != null) {
            val isCurrentPage = pagerState.currentPage == page

            val exoPlayer = remember(item.uri) {
                ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    setMediaItem(ExoMediaItem.fromUri(item.uri))
                    prepare()
                    volume = if (soundEnabled) 1.0f else 0.0f
                }
            }

            LaunchedEffect(isCurrentPage, autoPlay) {
                if (isCurrentPage && autoPlay) {
                    exoPlayer.play()
                } else {
                    exoPlayer.pause()
                }
            }

            DisposableEffect(exoPlayer) {
                onDispose {
                    exoPlayer.release()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Video Player Surface
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Bottom Video Information
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.75f)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC000000))
                            )
                        )
                        .padding(start = 20.dp, bottom = 24.dp, top = 20.dp)
                ) {
                    Column {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${item.formattedSize}  ·  时长 ${item.formattedDuration}  ·  ${item.width}×${item.height}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD1D5DB)
                        )
                    }
                }

                // Right Vertical Floating Action Buttons
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Keep (Check)
                    FloatingActionButton(
                        onClick = { onKeep(item) },
                        containerColor = PureWhite,
                        contentColor = DarkGreen,
                        shape = CircleShape,
                        modifier = Modifier.size(52.dp).bounceClick(scaleDown = 0.88f).shadow(4.dp, CircleShape)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "保留", modifier = Modifier.size(28.dp))
                    }

                    // Delete (Trash)
                    FloatingActionButton(
                        onClick = { onDelete(item) },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = AccentDeleteRed,
                        shape = CircleShape,
                        modifier = Modifier.size(52.dp).bounceClick(scaleDown = 0.88f).shadow(4.dp, CircleShape)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "待删除", modifier = Modifier.size(26.dp))
                    }

                    // EXIF Info
                    FloatingActionButton(
                        onClick = { onShowExif(item) },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp).bounceClick(scaleDown = 0.88f).shadow(4.dp, CircleShape)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "参数", modifier = Modifier.size(20.dp))
                    }

                    // Share
                    FloatingActionButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = item.mimeType
                                putExtra(Intent.EXTRA_STREAM, item.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "分享视频"))
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp).bounceClick(scaleDown = 0.88f).shadow(4.dp, CircleShape)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "分享", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
