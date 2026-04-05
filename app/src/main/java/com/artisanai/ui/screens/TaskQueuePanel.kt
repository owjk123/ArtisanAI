package com.artisanai.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.artisanai.data.model.GenerateTask
import com.artisanai.data.model.TaskStatus
import com.artisanai.ui.components.*
import com.artisanai.ui.theme.ArtisanColors
import com.artisanai.ui.theme.ArtisanType
import com.artisanai.ui.theme.sp
import com.artisanai.viewmodel.MainUiState
import com.artisanai.viewmodel.MainViewModel
import java.io.File

@Composable
fun TaskQueuePanel(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
                        onRemove = { viewModel.removeTask(task.id) },
                        onCopyPrompt = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("prompt", task.prompt))
                            Toast.makeText(context, "提示词已复制", Toast.LENGTH_SHORT).show()
                        },
                        onPreview = { /* handled in dialog below */ }
                    )
                }
            }
        }
    }

    // 成功任务的图片预览弹窗
    val successTask = uiState.tasks.find { it.status == TaskStatus.SUCCESS && it.resultImageBase64 != null }
    successTask?.let { task ->
        ImagePreviewDialog(
            task = task,
            onDismiss = { viewModel.removeTask(task.id) },
            onCopyPrompt = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("prompt", task.prompt))
                Toast.makeText(context, "提示词已复制", Toast.LENGTH_SHORT).show()
            },
            onShare = {
                try {
                    val bytes = android.util.Base64.decode(task.resultImageBase64, android.util.Base64.NO_WRAP)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val f = File(context.cacheDir, "share_${task.id}.png")
                    f.outputStream().use { it.write(bytes) }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(share, "分享图片"))
                } catch (e: Exception) {
                    Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
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
    onRemove: () -> Unit,
    onCopyPrompt: () -> Unit,
    onPreview: () -> Unit
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
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(onClick = onPreview),
                                contentScale = ContentScale.Crop
                            )
                        } ?: Icon(Icons.Default.CheckCircle, null, tint = ArtisanColors.Success, modifier = Modifier.size(28.dp))
                    }
                    TaskStatus.PROCESSING -> {
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 操作按钮行
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 复制提示词（所有状态都可点）
                    IconButton(
                        onClick = onCopyPrompt,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ArtisanColors.Graphite)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy, null,
                            tint = ArtisanColors.TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = ArtisanColors.Champagne,
                trackColor = ArtisanColors.Steel
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "生成中... ${(task.progress * 100).toInt()}%",
                style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 10.sp)
            )
        }
    }
}

@Composable
private fun ImagePreviewDialog(
    task: GenerateTask,
    onDismiss: () -> Unit,
    onCopyPrompt: () -> Unit,
    onShare: () -> Unit
) {
    val bitmap = remember(task.resultImageBase64) {
        task.resultImageBase64?.let {
            val bytes = android.util.Base64.decode(it, android.util.Base64.NO_WRAP)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(12.dp))
                .background(ArtisanColors.Onyx)
                .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(12.dp))
        ) {
            // 顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = ArtisanColors.TextSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onCopyPrompt,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ArtisanColors.Graphite)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, tint = ArtisanColors.Champagne, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ArtisanColors.GoldMist)
                    ) {
                        Icon(Icons.Default.Share, null, tint = ArtisanColors.Champagne, modifier = Modifier.size(18.dp))
                    }
                }
            }

            GoldDivider()

            // 图片预览（可滚动缩放）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(ArtisanColors.Obsidian),
                contentAlignment = Alignment.Center
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "生成结果",
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(rememberScrollState()),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            GoldDivider()

            // 提示词信息
            Column(modifier = Modifier.padding(16.dp)) {
                Text("PROMPT", style = ArtisanType.Label)
                Spacer(Modifier.height(6.dp))
                Text(
                    task.prompt,
                    style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoChip("比例", task.aspectRatio.label)
                    InfoChip("分辨率", task.imageSize.label)
                    InfoChip("思维", task.thinkingLevel.label)
                    if (task.useGrounding) InfoChip("Grounding", "开启")
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(ArtisanColors.Graphite)
            .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(3.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 10.sp))
        Text(value, style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary, fontSize = 10.sp))
    }
}
