package com.artisanai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.artisanai.data.model.EditSession
import com.artisanai.data.model.EditTurn
import com.artisanai.ui.components.*
import com.artisanai.ui.theme.ArtisanColors
import com.artisanai.ui.theme.ArtisanType
import com.artisanai.viewmodel.MainUiState
import com.artisanai.viewmodel.MainViewModel
import java.io.File

@Composable
fun ImageEditPanel(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val session = uiState.editSession
    val listState = rememberLazyListState()

    // 初次加载时清除焦点，防止键盘自动弹出
    LaunchedEffect(Unit) { focusManager.clearFocus() }

    // 自动滚动到最新
    LaunchedEffect(session.turns.size) {
        if (session.turns.isNotEmpty()) listState.animateScrollToItem(session.turns.size - 1)
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val b64 = compressImageForEdit(context, it)
            if (b64.isNotEmpty()) viewModel.setEditSourceImage(b64)
        }
    }

    Column(modifier = modifier.background(ArtisanColors.Obsidian).imePadding()) {

        // ── 顶栏 ───────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ArtisanColors.Onyx)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Edit, null, tint = ArtisanColors.Champagne, modifier = Modifier.size(16.dp))
                Text("图片编辑", style = ArtisanType.TitleGold.copy(fontSize = 14.sp))
                if (session.turns.isNotEmpty()) {
                    Text("· ${session.turns.size} 轮",
                        style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 11.sp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 更换源图
                IconButton(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)).background(ArtisanColors.Graphite)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, null, tint = ArtisanColors.TextSecondary, modifier = Modifier.size(16.dp))
                }
                // 清除会话
                if (session.turns.isNotEmpty() || session.sourceImageBase64 != null) {
                    IconButton(
                        onClick = viewModel::clearEditSession,
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)).background(ArtisanColors.Graphite)
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = ArtisanColors.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        GoldDivider()

        // ── 无源图时的初始上传区 ───────────────────────────
        if (session.sourceImageBase64 == null && session.turns.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.ImageSearch, null, tint = ArtisanColors.TextMuted, modifier = Modifier.size(52.dp))
                    Text("上传图片开始编辑", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted))
                    Text("支持多轮对话式图片修改", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 11.sp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ArtisanColors.Charcoal)
                            .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(8.dp))
                            .clickable { imagePicker.launch("image/*") }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Upload, null, tint = ArtisanColors.Champagne, modifier = Modifier.size(16.dp))
                            Text("选择图片", style = ArtisanType.Label.copy(color = ArtisanColors.Champagne))
                        }
                    }
                }
            }
            return@Column
        }

        // ── 对话历史列表 ───────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            // 首张源图
            session.sourceImageBase64?.let { src ->
                if (session.turns.isEmpty()) {
                    item {
                        SourceImageCard(src)
                    }
                }
            }

            // 每一轮
            items(session.turns, key = { it.id }) { turn ->
                EditTurnCard(turn = turn, onSave = { viewModel.saveEditResultToAlbum(turn) })
            }
        }

        GoldDivider()

        // ── 输入区 ─────────────────────────────────────────
        Column(
            modifier = Modifier
                .background(ArtisanColors.Onyx)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 文本输入
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ArtisanColors.Graphite)
                        .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = session.instruction,
                        onValueChange = viewModel::updateEditInstruction,
                        textStyle = ArtisanType.Body.copy(color = ArtisanColors.TextPrimary, fontSize = 13.sp),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (session.instruction.isEmpty()) {
                                Text(
                                    "输入编辑指令，如：把背景换成星空，风格改成水彩...",
                                    style = ArtisanType.Body.copy(color = ArtisanColors.TextMuted, fontSize = 13.sp)
                                )
                            }
                            inner()
                        }
                    )
                }

                // 发送按钮
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (!session.isGenerating && session.instruction.isNotBlank())
                                ArtisanColors.Charcoal else ArtisanColors.Graphite
                        )
                        .border(
                            1.dp,
                            if (!session.isGenerating && session.instruction.isNotBlank())
                                ArtisanColors.Champagne else ArtisanColors.Steel,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = !session.isGenerating && session.instruction.isNotBlank()) {
                            viewModel.sendEditInstruction()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (session.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = ArtisanColors.Champagne
                        )
                    } else {
                        Icon(
                            Icons.Default.Send, null,
                            tint = if (session.instruction.isNotBlank()) ArtisanColors.Champagne else ArtisanColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 当前使用的参数提示
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("使用参数:", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 10.sp))
                InfoTag(uiState.selectedAspectRatio.label)
                InfoTag(uiState.selectedImageSize.label)
                InfoTag(uiState.selectedThinkingLevel.label)
            }
        }
    }
}

@Composable
private fun SourceImageCard(base64: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("原始图片", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 10.sp))
        val bitmap = remember(base64) {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun EditTurnCard(turn: EditTurn, onSave: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 用户指令气泡
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 4.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
                    .background(ArtisanColors.Charcoal)
                    .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(topStart = 10.dp, topEnd = 4.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(turn.userText, style = ArtisanType.Caption.copy(color = ArtisanColors.TextPrimary, fontSize = 12.sp))
            }
        }

        // 结果图或生成中状态
        when {
            turn.isGenerating -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .clip(RoundedCornerShape(4.dp, 10.dp, 10.dp, 10.dp))
                        .background(ArtisanColors.Graphite)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = ArtisanColors.Champagne)
                    Text("生成中...", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 11.sp))
                }
            }
            turn.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .clip(RoundedCornerShape(4.dp, 10.dp, 10.dp, 10.dp))
                        .background(ArtisanColors.Error.copy(alpha = 0.1f))
                        .border(1.dp, ArtisanColors.Error.copy(alpha = 0.3f), RoundedCornerShape(4.dp, 10.dp, 10.dp, 10.dp))
                        .padding(12.dp)
                ) {
                    Text("失败: ${turn.error}", style = ArtisanType.Caption.copy(color = ArtisanColors.Error, fontSize = 11.sp))
                }
            }
            turn.resultImageBase64 != null -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val bitmap = remember(turn.resultImageBase64) {
                        val bytes = android.util.Base64.decode(turn.resultImageBase64, android.util.Base64.NO_WRAP)
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    bitmap?.let {
                        Box {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .clip(RoundedCornerShape(4.dp, 10.dp, 10.dp, 10.dp)),
                                contentScale = ContentScale.Fit
                            )
                            // 保存按钮
                            IconButton(
                                onClick = onSave,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ArtisanColors.Obsidian.copy(alpha = 0.75f))
                            ) {
                                Icon(Icons.Default.Download, null, tint = ArtisanColors.Champagne, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(ArtisanColors.Graphite)
            .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 10.sp))
    }
}

private fun compressImageForEdit(context: android.content.Context, uri: Uri): String {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val rawBytes = inputStream.readBytes()
        val original = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return ""
        val maxDim = 1280
        val scale = if (original.width > maxDim || original.height > maxDim)
            maxDim.toFloat() / maxOf(original.width, original.height) else 1f
        val bitmap = if (scale < 1f) {
            android.graphics.Bitmap.createScaledBitmap(
                original, (original.width * scale).toInt(), (original.height * scale).toInt(), true
            ).also { original.recycle() }
        } else original
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
    } catch (e: Exception) { "" }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
