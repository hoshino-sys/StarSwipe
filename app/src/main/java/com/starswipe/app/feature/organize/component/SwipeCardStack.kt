package com.starswipe.app.feature.organize.component

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.starswipe.app.core.designsystem.theme.AccentDeleteRed
import com.starswipe.app.core.designsystem.theme.AccentKeepGreen
import com.starswipe.app.core.designsystem.theme.NeonGreen
import com.starswipe.app.core.designsystem.theme.PureWhite
import com.starswipe.app.core.model.MediaItem
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class DepartingCard(
    val id: Long,
    val item: MediaItem,
    val initialX: Float,
    val targetX: Float
)

@Composable
fun SwipeCardStack(
    items: List<MediaItem>,
    currentIndex: Int,
    onSwipeLeft: (MediaItem) -> Unit,
    onSwipeRight: (MediaItem) -> Unit,
    onSwipeUp: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentItem = items.getOrNull(currentIndex)
    val nextItem1 = items.getOrNull(currentIndex + 1)
    val nextItem2 = items.getOrNull(currentIndex + 2)

    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    val animOffsetX = remember(currentIndex) { Animatable(0f) }
    val animOffsetY = remember(currentIndex) { Animatable(0f) }
    var isDragging by remember(currentIndex) { mutableStateOf(false) }
    var dragOffsetX by remember(currentIndex) { mutableFloatStateOf(0f) }

    // Independent departing cards stack to ensure rapid swiping NEVER drops a gesture
    var departingCards by remember { mutableStateOf<List<DepartingCard>>(emptyList()) }

    // Lambda reading current X inside draw/layout phases only - PREVENTS RECOMPOSITION ON TOUCH MOVES
    val getCurrentX = { if (isDragging) dragOffsetX else animOffsetX.value }

    val snapBackSpring = spring<Float>(
        dampingRatio = 0.75f,
        stiffness = 450f
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (currentItem != null) {
            // Card 2 (Bottom / 3rd layer):
            // Stays completely static while Card 0 is being swiped
            if (nextItem2 != null) {
                CardView(
                    item = nextItem2,
                    targetSize = Pair(720, 1280),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(top = 36.dp, bottom = 0.dp)
                        .graphicsLayer {
                            scaleX = 0.92f
                            scaleY = 0.92f
                        }
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(28.dp),
                            spotColor = Color.Black.copy(alpha = 0.15f)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                    )
                }
            }

            // Card 1 (Middle / 2nd layer):
            // Smoothly expands and sharpens purely via GPU graphicsLayer scaling and translation
            if (nextItem1 != null) {
                CardView(
                    item = nextItem1,
                    targetSize = Pair(720, 1280),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                        .padding(top = 18.dp, bottom = 18.dp)
                        .graphicsLayer {
                            val cx = getCurrentX()
                            val progress = (abs(cx) / 280f).coerceIn(0f, 1f)
                            val scale = 0.96f + (0.04f * progress)
                            scaleX = scale
                            scaleY = scale
                            translationY = -((18.dp.toPx()) * progress)
                        }
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(28.dp),
                            spotColor = Color.Black.copy(alpha = 0.18f)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val cx = getCurrentX()
                                val progress = (abs(cx) / 280f).coerceIn(0f, 1f)
                                alpha = (0.12f * (1f - progress)).coerceIn(0f, 0.12f)
                            }
                            .background(Color.Black)
                    )
                }
            }

            // Card 0 (Active Front Interactive Card)
            CardView(
                item = currentItem,
                targetSize = Pair(720, 1280),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 36.dp)
                    .graphicsLayer {
                        val cx = getCurrentX()
                        translationX = cx
                        translationY = animOffsetY.value
                        rotationZ = if (screenWidthPx > 0f) {
                            (cx / screenWidthPx * 14f).coerceIn(-35f, 35f)
                        } else 0f
                    }
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(28.dp),
                        spotColor = Color.Black.copy(alpha = 0.25f)
                    )
                    .pointerInput(currentIndex) {
                        var cumulativeDragY = 0f
                        var isUpwardDrag = false
                        var swipeUpTriggered = false

                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                isUpwardDrag = false
                                dragOffsetX = animOffsetX.value
                                cumulativeDragY = 0f
                                swipeUpTriggered = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                cumulativeDragY += dragAmount.y
                                dragOffsetX += dragAmount.x

                                // Trigger upward EXIF bottom sheet & card micro-bounce
                                if (!swipeUpTriggered && cumulativeDragY < -70f && abs(dragOffsetX) < 60f) {
                                    swipeUpTriggered = true
                                    coroutineScope.launch {
                                        animOffsetY.animateTo(-20f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
                                        animOffsetY.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 400f))
                                    }
                                    onSwipeUp(currentItem)
                                }
                            },
                            onDragEnd = {
                                val finalX = dragOffsetX
                                isDragging = false

                                val threshold = 200f
                                when {
                                    swipeUpTriggered -> {
                                        coroutineScope.launch {
                                            animOffsetX.animateTo(0f, snapBackSpring)
                                        }
                                    }
                                    finalX > threshold -> {
                                        // Swipe Right -> Keep
                                        val departing = DepartingCard(
                                            id = currentItem.id,
                                            item = currentItem,
                                            initialX = finalX,
                                            targetX = screenWidthPx * 1.5f
                                        )
                                        departingCards = departingCards + departing
                                        onSwipeRight(currentItem)
                                    }
                                    finalX < -threshold -> {
                                        // Swipe Left -> Delete
                                        val departing = DepartingCard(
                                            id = currentItem.id,
                                            item = currentItem,
                                            initialX = finalX,
                                            targetX = -screenWidthPx * 1.5f
                                        )
                                        departingCards = departingCards + departing
                                        onSwipeLeft(currentItem)
                                    }
                                    cumulativeDragY < -50f && abs(finalX) < 80f -> {
                                        coroutineScope.launch {
                                            animOffsetY.animateTo(-20f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
                                            animOffsetY.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 400f))
                                        }
                                        onSwipeUp(currentItem)
                                        coroutineScope.launch {
                                            animOffsetX.animateTo(0f, snapBackSpring)
                                        }
                                    }
                                    else -> {
                                        coroutineScope.launch {
                                            animOffsetX.animateTo(0f, snapBackSpring)
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                val finalX = dragOffsetX
                                isDragging = false
                                coroutineScope.launch {
                                    animOffsetX.snapTo(finalX)
                                    animOffsetX.animateTo(0f, snapBackSpring)
                                }
                            }
                        )
                    }
            ) {
                // Overlay Badges: Left = Delete, Right = Keep (Calculated in draw phase)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .graphicsLayer {
                            val cx = getCurrentX()
                            alpha = if (cx < -25f) (abs(cx) / 200f).coerceIn(0f, 0.95f) else 0f
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentDeleteRed.copy(alpha = 0.92f))
                        .border(2.dp, PureWhite, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "待删除",
                        color = PureWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(24.dp)
                        .graphicsLayer {
                            val cx = getCurrentX()
                            alpha = if (cx > 25f) (cx / 200f).coerceIn(0f, 0.95f) else 0f
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentKeepGreen.copy(alpha = 0.92f))
                        .border(2.dp, PureWhite, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "保留",
                        color = PureWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Departing overlay cards flying off smoothly in parallel without blocking user gestures on new cards
        departingCards.forEach { departing ->
            key(departing.id) {
                DepartingCardView(
                    departing = departing,
                    screenWidthPx = screenWidthPx,
                    onAnimationFinished = {
                        departingCards = departingCards.filterNot { it.id == departing.id }
                    }
                )
            }
        }
    }
}

@Composable
private fun DepartingCardView(
    departing: DepartingCard,
    screenWidthPx: Float,
    onAnimationFinished: () -> Unit
) {
    val offsetX = remember { Animatable(departing.initialX) }

    LaunchedEffect(departing.id) {
        offsetX.animateTo(
            targetValue = departing.targetX,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 520f
            )
        )
        onAnimationFinished()
    }

    CardView(
        item = departing.item,
        targetSize = Pair(720, 1280),
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 36.dp)
            .graphicsLayer {
                val ox = offsetX.value
                translationX = ox
                rotationZ = if (screenWidthPx > 0f) {
                    (ox / screenWidthPx) * 14f
                } else 0f
            }
    )
}

@Composable
private fun CardView(
    item: MediaItem,
    targetSize: Pair<Int, Int> = Pair(720, 1280),
    modifier: Modifier = Modifier,
    overlayContent: @Composable (BoxScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    val imageRequest = remember(item.uri, targetSize) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(targetSize.first, targetSize.second)
            .crossfade(false)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .allowHardware(true)
            .build()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Bottom Info Bar with Gradient scrim
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xCC000000))
                    )
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${item.formattedSize}  ·  ${item.width}×${item.height}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE0E0E0)
                    )
                    Text(
                        text = "上滑查看 EXIF ↑",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        overlayContent?.invoke(this)
    }
}
