package com.artisanai.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.artisanai.data.model.TaskStatus
import com.artisanai.ui.components.GoldDivider
import com.artisanai.ui.components.GoldGradient
import com.artisanai.ui.theme.ArtisanColors
import com.artisanai.ui.theme.ArtisanType
import com.artisanai.viewmodel.MainUiState
import com.artisanai.viewmodel.MainViewModel
import java.io.File

@Composable
fun MainScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    windowSizeClass: WindowSizeClass
) {
    val isWideScreen = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    var showSettings by remember { mutableStateOf(false) }

    uiState.toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            viewModel.dismissToast()
        }
    }

    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArtisanColors.Obsidian)
    ) {
        if (isWideScreen) {
            TabletLayout(uiState, viewModel, onSettings = { showSettings = true })
        } else {
            PhoneLayout(uiState, viewModel, onSettings = { showSettings = true })
        }

        // Toast 消息（居中显示，不被按钮遮挡）
        AnimatedVisibility(
            visible = uiState.toastMessage != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(ArtisanColors.Charcoal)
                    .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(uiState.toastMessage ?: "", style = ArtisanType.Caption)
            }
        }
    }
}

// ── 平板横屏：左右两栏 ────────────────────────────────────
@Composable
private fun TabletLayout(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onSettings: () -> Unit
) {
    var rightTab by remember { mutableIntStateOf(0) } // 0=预览 1=图库

    Row(modifier = Modifier.fillMaxSize()) {
        // 左栏 40%：控制面板
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .background(ArtisanColors.Onyx)
        ) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ARTISAN AI", style = ArtisanType.TitleGold)
                    Text(
                        "IMAGE STUDIO",
                        style = ArtisanType.Label.copy(color = ArtisanColors.TextMuted, letterSpacing = 3.sp)
                    )
                }
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ArtisanColors.Graphite)
                ) {
                    Icon(Icons.Default.Settings, null, tint = ArtisanColors.TextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            GoldDivider()

            GeneratePanel(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                isTablet = true
            )
        }

        // 分割线
        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(ArtisanColors.Steel))

        // 右栏 60%：预览 + 图库
        Box(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .background(ArtisanColors.Obsidian)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 右栏顶部 Tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(ArtisanColors.Onyx)
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("当前预览", "图片编辑", "图库").forEachIndexed { idx, label ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { rightTab = idx },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    label,
                                    style = ArtisanType.Label.copy(
                                        color = if (rightTab == idx) ArtisanColors.Champagne else ArtisanColors.TextMuted,
                                        fontSize = 12.sp
                                    )
                                )
                                if (rightTab == idx) {
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(28.dp)
                                            .height(2.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(GoldGradient)
                                    )
                                }
                            }
                        }
                    }
                }

                GoldDivider()

                // 内容区
                when (rightTab) {
                    0 -> PreviewPanel(uiState, modifier = Modifier.fillMaxSize())
                    1 -> ImageEditPanel(uiState, viewModel, modifier = Modifier.fillMaxSize())
                    2 -> GalleryPanel(uiState, viewModel, columns = 3, modifier = Modifier.fillMaxSize())
                }
            }

            // 右下角"加入队列"按钮（预览Tab显示）
            if (rightTab == 0) {
                val count = uiState.selectedCount
                val activeTaskCount = uiState.tasks.count {
                    it.status == TaskStatus.QUEUED || it.status == TaskStatus.PROCESSING
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(28.dp))
                            .background(ArtisanColors.Charcoal)
                            .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(28.dp))
                            .clickable { viewModel.addGenerateTask() }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Add, null, tint = ArtisanColors.TextPrimary, modifier = Modifier.size(18.dp))
                        Text(
                            "加入队列 ($count 个)",
                            style = ArtisanType.Label.copy(color = ArtisanColors.TextPrimary, fontSize = 14.sp)
                        )
                        if (activeTaskCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ArtisanColors.Champagne),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("$activeTaskCount", style = ArtisanType.Caption.copy(color = ArtisanColors.Obsidian, fontSize = 10.sp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 预览面板：显示最新生成图 ─────────────────────────────
@Composable
private fun PreviewPanel(uiState: MainUiState, modifier: Modifier = Modifier) {
    val latestSuccess = uiState.tasks.lastOrNull { it.status == TaskStatus.SUCCESS }
    val processing = uiState.tasks.filter { it.status == TaskStatus.PROCESSING }

    Box(modifier = modifier.background(ArtisanColors.Obsidian)) {
        if (latestSuccess?.resultImagePath != null) {
            AsyncImage(
                model = File(latestSuccess.resultImagePath),
                contentDescription = latestSuccess.prompt,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            // 占位符
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Image,
                    null,
                    tint = ArtisanColors.TextMuted,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text("生成的图片将显示在这里", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted))
            }
        }

        // 进行中的任务进度条
        if (processing.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(ArtisanColors.Obsidian.copy(alpha = 0.85f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                processing.take(3).forEach { task ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = ArtisanColors.Champagne
                        )
                        Text(
                            "生成中... ${(task.progress * 100).toInt()}%",
                            style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary, fontSize = 11.sp)
                        )
                        LinearProgressIndicator(
                            progress = { task.progress },
                            modifier = Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)),
                            color = ArtisanColors.Champagne,
                            trackColor = ArtisanColors.Graphite
                        )
                    }
                }
                if (processing.size > 3) {
                    Text(
                        "还有 ${processing.size - 3} 个任务排队中",
                        style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 10.sp)
                    )
                }
            }
        }
    }
}

// ── 手机竖屏：底部 Tab ────────────────────────────────────
@Composable
private fun PhoneLayout(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onSettings: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ArtisanColors.Onyx)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("ARTISAN AI", style = ArtisanType.TitleGold)
                Text("IMAGE STUDIO", style = ArtisanType.Label.copy(color = ArtisanColors.TextMuted, letterSpacing = 3.sp))
            }
            IconButton(
                onClick = onSettings,
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(ArtisanColors.Graphite)
            ) {
                Icon(Icons.Default.Settings, null, tint = ArtisanColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
        GoldDivider()

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> PhoneGenerateWithButton(uiState, viewModel)
                1 -> ImageEditPanel(uiState, viewModel, modifier = Modifier.fillMaxSize())
                2 -> GalleryPanel(uiState, viewModel, columns = 2, modifier = Modifier.fillMaxSize())
            }
        }

        GoldDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ArtisanColors.Onyx)
                .navigationBarsPadding()
                .height(60.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(Icons.Default.AutoAwesome, "生成", selectedTab == 0) { selectedTab = 0 }
            BottomNavItem(Icons.Default.Edit, "编辑", selectedTab == 1) { selectedTab = 1 }
            BottomNavItem(Icons.Default.PhotoLibrary, "图库", selectedTab == 2) { selectedTab = 2 }
        }
    }
}

// 手机生成页：面板 + 底部固定按钮
@Composable
private fun PhoneGenerateWithButton(uiState: MainUiState, viewModel: MainViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            GeneratePanel(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
                isTablet = false
            )
            // 占位，防止内容被按钮遮挡
            Spacer(Modifier.height(72.dp))
        }

        // 加入队列按钮（固定底部）
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(ArtisanColors.Onyx)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val count = uiState.selectedCount
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ArtisanColors.Charcoal)
                    .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(8.dp))
                    .clickable { viewModel.addGenerateTask() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = ArtisanColors.TextPrimary, modifier = Modifier.size(16.dp))
                    Text(
                        "加入队列 ($count 个)",
                        style = ArtisanType.Label.copy(color = ArtisanColors.TextPrimary, fontSize = 14.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            icon, null,
            tint = if (selected) ArtisanColors.Champagne else ArtisanColors.TextMuted,
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            style = ArtisanType.Caption.copy(
                color = if (selected) ArtisanColors.Champagne else ArtisanColors.TextMuted,
                fontSize = 10.sp
            )
        )
        AnimatedVisibility(visible = selected) {
            Box(
                modifier = Modifier
                    .width(20.dp).height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(GoldGradient)
            )
        }
    }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
