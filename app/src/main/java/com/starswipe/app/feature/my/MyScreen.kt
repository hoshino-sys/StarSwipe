package com.starswipe.app.feature.my

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starswipe.app.core.designsystem.component.bounceClick
import com.starswipe.app.core.designsystem.theme.DarkBorder
import com.starswipe.app.core.designsystem.theme.DarkCardSurface
import com.starswipe.app.core.designsystem.theme.NeonGreen
import com.starswipe.app.core.designsystem.theme.PureWhite
import com.starswipe.app.core.model.SortOrder
import com.starswipe.app.core.model.SwipeDirection
import kotlin.math.roundToInt

@Composable
fun MyScreen(
    viewModel: MyViewModel = viewModel(factory = MyViewModel.provideFactory(LocalContext.current)),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    var showSortDialog by remember { mutableStateOf(false) }
    var showSwipeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDisclaimerDialog by remember { mutableStateOf(false) }

    val fixedRoundSizes = remember { listOf(10, 20, 30, 50, 100, 150, 200, 300) }
    val currentRoundSize = uiState.preferences.roundSize
    val initialIndex = remember(currentRoundSize) {
        val idx = fixedRoundSizes.indexOf(currentRoundSize)
        if (idx >= 0) idx.toFloat()
        else {
            val closest = fixedRoundSizes.minByOrNull { kotlin.math.abs(it - currentRoundSize) } ?: 20
            fixedRoundSizes.indexOf(closest).toFloat().coerceAtLeast(0f)
        }
    }
    var sliderStep by remember(initialIndex) { mutableFloatStateOf(initialIndex) }
    val selectedIndex = sliderStep.roundToInt().coerceIn(0, fixedRoundSizes.size - 1)
    val selectedRoundSize = fixedRoundSizes[selectedIndex]

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 96.dp)
        ) {
        Text(
            text = "我的",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Top Dark Card: Stats Dashboard
        StatsDashboardCard(
            cleanedCount = uiState.stats.totalCleanedCount,
            freedSpaceFormatted = uiState.stats.formattedFreedBytes,
            usedSpaceFormatted = uiState.stats.formattedUsedStorage,
            totalSpaceFormatted = uiState.stats.formattedTotalStorage,
            usageProgress = uiState.stats.usagePercentage
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Section: Organize Preferences
        SectionHeader(title = "整理体验")
        SettingsCard {
            // Round Size Slider with Discrete Fixed Values
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "每轮整理数量",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$selectedRoundSize 项",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = sliderStep,
                    onValueChange = { sliderStep = it },
                    onValueChangeFinished = {
                        val finalIndex = sliderStep.roundToInt().coerceIn(0, fixedRoundSizes.size - 1)
                        viewModel.updateRoundSize(fixedRoundSizes[finalIndex])
                    },
                    valueRange = 0f..(fixedRoundSizes.size - 1).toFloat(),
                    steps = fixedRoundSizes.size - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                // Discrete step values row below slider
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    fixedRoundSizes.forEachIndexed { index, size ->
                        val isCurrent = index == selectedIndex
                        Text(
                            text = "$size",
                            fontSize = 11.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)

            // Swipe Direction
            SettingsItemRow(
                icon = Icons.Default.Swipe,
                title = "滑动方向",
                value = uiState.preferences.swipeDirection.label,
                onClick = { showSwipeDialog = true }
            )

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)

            // Sort Order
            SettingsItemRow(
                icon = Icons.Default.Sort,
                title = "展示顺序",
                value = uiState.preferences.sortOrder.label,
                onClick = { showSortDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section: Video Preferences
        SectionHeader(title = "视频体验")
        SettingsCard {
            SettingsSwitchRow(
                icon = Icons.Default.Videocam,
                title = "自动播放视频",
                subtitle = "上下滑切换视频流时自动循环播放",
                checked = uiState.preferences.autoPlayVideo,
                onCheckedChange = { viewModel.updateAutoPlayVideo(it) }
            )

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)

            SettingsSwitchRow(
                icon = Icons.Default.VolumeUp,
                title = "视频声音",
                subtitle = "播放视频时默认开启扬声器声音",
                checked = uiState.preferences.videoSoundEnabled,
                onCheckedChange = { viewModel.updateVideoSoundEnabled(it) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section: Data Maintenance & Agreements
        SectionHeader(title = "关于与协议")
        SettingsCard {
            SettingsItemRow(
                icon = Icons.Default.RestartAlt,
                title = "重置已跳过记录",
                value = "重新整理",
                onClick = { viewModel.showResetDialog(true) }
            )

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)

            SettingsItemRow(
                icon = Icons.Default.Info,
                title = "关于星滑相册",
                value = "v1.0.0",
                onClick = { showAboutDialog = true }
            )

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)

            SettingsItemRow(
                icon = Icons.Default.Gavel,
                title = "免责声明与服务协议",
                value = "查看详情",
                onClick = { showDisclaimerDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(36.dp))
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

    // Compact Modern Sort Order Dialog (Zero Wasted Space)
    if (showSortDialog) {
        CompactSelectDialog(
            title = "选择媒体展示顺序",
            items = SortOrder.entries,
            selectedItem = uiState.preferences.sortOrder,
            itemLabel = { it.label },
            onSelect = { viewModel.updateSortOrder(it) },
            onDismiss = { showSortDialog = false }
        )
    }

    // Compact Modern Swipe Direction Dialog (Zero Wasted Space)
    if (showSwipeDialog) {
        CompactSelectDialog(
            title = "选择滑动方向逻辑",
            items = SwipeDirection.entries,
            selectedItem = uiState.preferences.swipeDirection,
            itemLabel = { it.label },
            onSelect = { viewModel.updateSwipeDirection(it) },
            onDismiss = { showSwipeDialog = false }
        )
    }

    // Reset History Confirmation Dialog
    if (uiState.showResetDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showResetDialog(false) },
            title = {
                Text(
                    text = "重置已跳过记录？",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "此操作将清除之前标记为“保留/跳过”的历史状态，所有照片将重新出现在整理池中供您再次审视。已物理删除的照片不会受影响。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.resetSkippedHistory() },
                    modifier = Modifier.bounceClick()
                ) {
                    Text(
                        text = "确认重置",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.showResetDialog(false) },
                    modifier = Modifier.bounceClick()
                ) {
                    Text(text = "取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // About StarSwipe Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text(
                    text = "关于星滑相册",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        text = "星滑相册 StarSwipe",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "版本：v1.0.0",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "一款优雅高效的本地相册滑动整理与清理工具。通过简单跟手的上滑、下滑手势，快速告别杂乱，为手机腾出更多宝贵空间。",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    TextButton(
                        onClick = {
                            showAboutDialog = false
                            showDisclaimerDialog = true
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(text = "查看免责声明与服务协议 >", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(text = "关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Detailed Disclaimer Bottom Sheet
    if (showDisclaimerDialog) {
        DisclaimerBottomSheet(
            onDismiss = { showDisclaimerDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisclaimerBottomSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Gavel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "免责声明与服务协议",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "版本生效日期：2026年10月",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                DisclaimerSection(
                    title = "一、总则与离线安全承诺",
                    content = "星滑相册（StarSwipe）是一款运行在您本地 Android 设备上的相册整理辅助工具。本软件的所有核心算法（包括但不限于图片相似度感知哈希聚类、EXIF 拍摄地理位置与设备参数解析、相簿分类统计与空相簿探测等）均在用户设备本地离线执行，不收集、不上报、不上传用户的任何个人相册照片、视频及隐私数据。"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Mandatory Empty Album Cleanup File Loss Disclaimer Alert Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE65100).copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFE65100).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "二、空相簿清理特别免责条款（重要须知）",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1. 本软件提供“空相簿清理”功能，旨在识别并辅助清理手机相册路径下不包含有效照片或视频的空目录。本软件已内置防误删过滤机制（如相机核心目录保护、隐藏文件夹与 .nomedia 私有文件夹排除及二次弹窗确认）。\n\n" +
                                    "2. 鉴于 Android 系统厂商定制 ROM 的高度碎片化、特殊存储挂载策略，以及部分第三方应用程序可能违规将私有配置、文档或其他非媒体数据存放在公开相册同级路径，用户在点击“一键清理”或确认删除前，应自行确认所识别空相簿列表的名称与目录性质，并自行承担相关操作风险。\n\n" +
                                    "3. 因使用本软件清理空相簿、删除文件夹或整理文件而直接或间接导致的任何非媒体文件丢失、三方应用数据损坏、系统运行异常或其他形式的数据与财产损失，星滑相册及其开发者不承担任何直接、间接、连带、附带或惩罚性法律责任与赔偿责任。\n\n" +
                                    "4. 强烈建议用户在进行任何大范围相册清理操作前，做好重要数据的本地或云端备份。",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                DisclaimerSection(
                    title = "三、照片与视频删除操作免责",
                    content = "在卡片滑动整理过程中，所有最终删除操作均依赖 Android 系统标准底层接口与系统级安全确认机制（如调用系统相册回收站或弹出系统删除授权框）。最终删除动作由用户完全自主决定并触发，请在最终确认前审慎甄别。因用户个人主观误判、手滑误删所致的照片丢失，开发者不承担数据恢复或赔偿责任。"
                )

                Spacer(modifier = Modifier.height(12.dp))

                DisclaimerSection(
                    title = "四、软件服务与担保限制",
                    content = "本软件按“现状”（AS-IS）和“现有”基础提供，不提供任何形式的明示或暗示保证，包括但不限于适销性、特定用途适用性或无侵权保证。开发者不保证软件功能能完全满足所有用户的特定硬件或系统需求，亦不保证软件在所有极端环境下均无错误。"
                )

                Spacer(modifier = Modifier.height(12.dp))

                DisclaimerSection(
                    title = "五、协议接受与终止",
                    content = "当您安装、启动或使用本软件的任何功能时，即表示您已完整阅读、充分理解并完全同意本免责声明的全部条款约束。若您不同意本免责声明中的任何条款，请立即退出并卸载本软件。"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick()
            ) {
                Text(text = "我已阅读并知晓", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun DisclaimerSection(
    title: String,
    content: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun <T> CompactSelectDialog(
    title: String,
    items: List<T>,
    selectedItem: T,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 18.dp, horizontal = 16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                items.forEach { item ->
                    val isSelected = item == selectedItem
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .bounceClick(scaleDown = 0.97f) {
                                onSelect(item)
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = itemLabel(item),
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun StatsDashboardCard(
    cleanedCount: Int,
    freedSpaceFormatted: String,
    usedSpaceFormatted: String,
    totalSpaceFormatted: String,
    usageProgress: Float
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = "清理成果看板",
                color = Color(0xFFA0A3BD),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "$cleanedCount",
                        color = PureWhite,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "已清理张数",
                        color = Color(0xFFA0A3BD),
                        fontSize = 12.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = freedSpaceFormatted,
                        color = NeonGreen,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "累计释放空间",
                        color = Color(0xFFA0A3BD),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Storage space progress bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "手机存储使用率 ${(usageProgress * 100).toInt()}%",
                        color = PureWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$usedSpaceFormatted / $totalSpaceFormatted",
                        color = Color(0xFFA0A3BD),
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { usageProgress },
                    color = NeonGreen,
                    trackColor = Color(0xFF2C323D),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingsItemRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(scaleDown = 0.97f, onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PureWhite,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
