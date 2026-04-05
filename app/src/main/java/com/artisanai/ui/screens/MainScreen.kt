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
import androidx.compose.ui.unit.dp
import com.artisanai.data.model.TaskStatus
import com.artisanai.ui.components.GoldDivider
import com.artisanai.ui.components.GoldGradient
import com.artisanai.ui.theme.ArtisanColors
import com.artisanai.ui.theme.ArtisanType
import com.artisanai.viewmodel.MainUiState
import com.artisanai.viewmodel.MainViewModel

@Composable
fun MainScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    windowSizeClass: WindowSizeClass
) {
    val isWideScreen = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    var showSettings by remember { mutableStateOf(false) }

    // Toast
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

        // Toast
        AnimatedVisibility(
            visible = uiState.toastMessage != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ArtisanColors.Charcoal)
                    .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(6.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(uiState.toastMessage ?: "", style = ArtisanType.Caption)
            }
        }
    }
}

// ── 平板横屏：左右三栏 ────────────────────────────────────
@Composable
private fun TabletLayout(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onSettings: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // 左栏：生成控制 360dp
        Column(
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight()
                .background(ArtisanColors.Onyx)
        ) {
            // 设置图标
            TabletTopBar(onSettings)
            GoldDivider()
            GeneratePanel(uiState, viewModel, Modifier.fillMaxSize())
        }

        // 竖分割线
        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(ArtisanColors.Steel))

        // 中栏：任务队列 300dp
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .background(ArtisanColors.Obsidian)
        ) {
            TaskQueuePanel(uiState, viewModel, Modifier.fillMaxSize())
        }

        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(ArtisanColors.Steel))

        // 右栏：图库（自适应剩余空间）
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(ArtisanColors.Obsidian)
        ) {
            GalleryPanel(uiState, viewModel, columns = 3, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TabletTopBar(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("ARTISAN AI", style = ArtisanType.TitleGold)
            Text("IMAGE STUDIO", style = ArtisanType.Label.copy(
                color = ArtisanColors.TextMuted,
                letterSpacing = 3.sp
            ))
        }
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ArtisanColors.Graphite)
        ) {
            Icon(Icons.Default.Settings, null, tint = ArtisanColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

// ── 手机竖屏：底部Tab ─────────────────────────────────────
@Composable
private fun PhoneLayout(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onSettings: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部栏（仅手机）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ArtisanColors.Onyx)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("ARTISAN AI", style = ArtisanType.TitleGold)
                Text("IMAGE STUDIO", style = ArtisanType.Label.copy(
                    color = ArtisanColors.TextMuted, letterSpacing = 3.sp
                ))
            }
            IconButton(
                onClick = onSettings,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ArtisanColors.Graphite)
            ) {
                Icon(Icons.Default.Settings, null, tint = ArtisanColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
        GoldDivider()

        // 内容区
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> GeneratePanel(uiState, viewModel, Modifier.fillMaxSize())
                1 -> TaskQueuePanel(uiState, viewModel, Modifier.fillMaxSize())
                2 -> GalleryPanel(uiState, viewModel, columns = 2, Modifier.fillMaxSize())
            }
        }

        // 底部导航
        GoldDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ArtisanColors.Onyx)
                .navigationBarsPadding()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(Icons.Default.AutoAwesome, "生成", selectedTab == 0) { selectedTab = 0 }

            // 任务tab带徽章
            val activeTasks = uiState.tasks.count {
                it.status == TaskStatus.QUEUED || it.status == TaskStatus.PROCESSING
            }
            BadgedBox(
                badge = {
                    if (activeTasks > 0) {
                        Badge(
                            containerColor = ArtisanColors.Champagne,
                            contentColor = ArtisanColors.Obsidian
                        ) { Text("$activeTasks", style = ArtisanType.Caption.copy(fontSize = 9.sp)) }
                    }
                }
            ) {
                BottomNavItem(Icons.Default.PlaylistPlay, "任务", selectedTab == 1) { selectedTab = 1 }
            }

            BottomNavItem(Icons.Default.PhotoLibrary, "图库", selectedTab == 2) { selectedTab = 2 }
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
            .padding(horizontal = 20.dp, vertical = 8.dp),
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
