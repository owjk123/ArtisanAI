package com.artisanai.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.artisanai.data.model.GenerateTask
import com.artisanai.data.model.TaskStatus
import com.artisanai.ui.components.*
import com.artisanai.ui.theme.ArtisanColors
import com.artisanai.ui.theme.ArtisanType
import com.artisanai.viewmodel.MainUiState
import com.artisanai.viewmodel.MainViewModel

@Composable
fun TaskQueuePanel(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val activeTasks = uiState.tasks.filter {
        it.status == TaskStatus.QUEUED || it.status == TaskStatus.PROCESSING
    }
    val doneTasks = uiState.tasks.filter {
        it.status == TaskStatus.SUCCESS || it.status == TaskStatus.FAILED
    }

    Column(modifier = modifier) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("任务队列", style = ArtisanType.TitleGold)
                Text(
                    "${activeTasks.size} 进行中  ·  ${doneTasks.size} 已完成",
                    style = ArtisanType.Caption
                )
            }
            if (doneTasks.isNotEmpty()) {
                OutlineGoldButton(
                    text = "清除已完成",
                    onClick = viewModel::clearCompletedTasks,
                    modifier = Modifier.width(110.dp)
                )
            }
        }

        GoldDivider()

        if (uiState.tasks.isEmpty()) {
            EmptyTaskState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.tasks.sortedByDescending { it.createdAt }, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onRetry = { viewModel.retryTask(task.id) },
                        onRemove = { viewModel.removeTask(task.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTaskState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.HourglassEmpty,
                contentDescription = null,
                tint = ArtisanColors.TextMuted,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("暂无任务", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted))
            Text("填写提示词后点击「开始生成」", style = ArtisanType.Caption.copy(
                color = ArtisanColors.TextMuted,
                fontSize = 11.sp
            ))
        }
    }
}

@Composable
private fun TaskCard(
    task: GenerateTask,
    onRetry: () -> Unit,
    onRemove: () -> Unit
) {
    ArtisanCard(hasBorder = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 缩略图区域
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ArtisanColors.Graphite),
                contentAlignment = Alignment.Center
            ) {
                when (task.status) {
                    TaskStatus.SUCCESS -> {
                        val bitmap = remember(task.resultImageBase64) {
                            task.resultImageBase64?.let {
                                val bytes = android.util.Base64.decode(it, android.util.Base64.NO_WRAP)
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                        }
                        bitmap?.let {
                            androidx.compose.foundation.Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } ?: Icon(Icons.Default.CheckCircle, null, tint = ArtisanColors.Success, modifier = Modifier.size(28.dp))
                    }
                    TaskStatus.PROCESSING -> {
                        // 金色旋转动画
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = ArtisanColors.Champagne,
                            strokeWidth = 2.dp,
                            progress = { task.progress }
                        )
                    }
                    TaskStatus.QUEUED -> {
                        Icon(Icons.Default.Schedule, null, tint = ArtisanColors.TextMuted, modifier = Modifier.size(24.dp))
                    }
                    TaskStatus.FAILED -> {
                        Icon(Icons.Default.ErrorOutline, null, tint = ArtisanColors.Error, modifier = Modifier.size(28.dp))
                    }
                }
            }

            // 信息区
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(task.status)
                    Text(
                        text = "${task.aspectRatio.label}  ${task.imageSize.label}",
                        style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted)
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = task.prompt,
                    style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.status == TaskStatus.FAILED && task.errorMessage != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        task.errorMessage,
                        style = ArtisanType.Caption.copy(color = ArtisanColors.Error),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 操作按钮
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (task.status == TaskStatus.FAILED) {
                        OutlineGoldButton(
                            text = "重试",
                            onClick = onRetry,
                            modifier = Modifier.width(70.dp)
                        )
                    }
                    if (task.status != TaskStatus.PROCESSING) {
                        OutlineGoldButton(
                            text = "移除",
                            onClick = onRemove,
                            modifier = Modifier.width(70.dp)
                        )
                    }
                }
            }
        }

        // 进度条
        if (task.status == TaskStatus.PROCESSING) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                color = ArtisanColors.Champagne,
                trackColor = ArtisanColors.Steel
            )
        }
    }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
