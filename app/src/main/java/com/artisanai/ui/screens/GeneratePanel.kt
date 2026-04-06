package com.artisanai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.artisanai.data.model.*
import com.artisanai.ui.components.*
import com.artisanai.ui.theme.ArtisanColors
import com.artisanai.ui.theme.ArtisanType
import com.artisanai.viewmodel.MainUiState
import com.artisanai.viewmodel.MainViewModel

private enum class GenerateMode { DIRECT, REVERSE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratePanel(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    isTablet: Boolean = true
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // 初次加载时清除焦点，防止键盘自动弹出
    LaunchedEffect(Unit) { focusManager.clearFocus() }

    // 同步 UI 模式状态到 ViewModel
    val mode = if (uiState.isReverseMode) GenerateMode.REVERSE else GenerateMode.DIRECT

    // 参考图选择器（风格参考，多选触发单次）
    val refImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val b64 = compressImage(context, it)
            if (b64.isNotEmpty()) viewModel.addReferenceImage(b64)
        }
    }
    // 反推图选择器（单张）
    val reverseImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val b64 = compressImage(context, it)
            if (b64.isNotEmpty()) viewModel.setReverseImage(b64)
        }
    }

    Column(modifier = modifier) {
        // ── 模式 Tab ─────────────────────────────────────
        ModeTabs(mode) { newMode ->
            viewModel.setReverseMode(newMode == GenerateMode.REVERSE)
        }

        GoldDivider()

        // ── 控件区（可滚动，防高级选项展开时挤压） ────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (mode == GenerateMode.REVERSE) {
                // 反推图上传（单张，用于自动分析）
                ReverseImageSection(
                    imageBase64 = uiState.reverseImageBase64,
                    isAnalyzing = uiState.isReversingPrompt,
                    onPick = { reverseImagePicker.launch("image/*") },
                    onClear = viewModel::clearReverseImage
                )
            }

            // 参考图多选（最多5张）
            MultiRefImageSection(
                images = uiState.referenceImages,
                onAdd = { if (uiState.referenceImages.size < 5) refImagePicker.launch("image/*") },
                onRemove = viewModel::removeReferenceImage
            )

            // ── 提示词 + AI优化 ──────────────────────────
            PromptSection(uiState, viewModel)

            // ── 分辨率 + 宽高比（并排） ──────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactDropdown(
                    label = "分辨率",
                    options = ImageSize.entries,
                    selected = uiState.selectedImageSize,
                    onSelect = viewModel::selectImageSize,
                    displayText = { it.label },
                    modifier = Modifier.weight(1f)
                )
                CompactDropdown(
                    label = "宽高比",
                    options = AspectRatio.entries,
                    selected = uiState.selectedAspectRatio,
                    onSelect = viewModel::selectAspectRatio,
                    displayText = { it.label },
                    modifier = Modifier.weight(1f)
                )
            }

            // ── 高级选项（折叠） ─────────────────────────
            AdvancedOptions(uiState, viewModel)
        }

        GoldDivider()

        // ── 数量选择（固定底部） ─────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("数量", style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary))
            listOf(1, 3, 5, 10).forEach { count ->
                val selected = uiState.selectedCount == count
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) ArtisanColors.Charcoal else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) ArtisanColors.Champagne else ArtisanColors.Steel,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { viewModel.selectCount(count) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$count",
                        style = ArtisanType.Label.copy(
                            color = if (selected) ArtisanColors.Champagne else ArtisanColors.TextSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }
            }
        }
    }
}

// ── 模式 Tab 栏 ──────────────────────────────────────────
@Composable
private fun ModeTabs(mode: GenerateMode, onSelect: (GenerateMode) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        GenerateMode.entries.forEach { m ->
            val selected = m == mode
            val label = if (m == GenerateMode.DIRECT) "直接生图" else "反推生图"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(m) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        label,
                        style = ArtisanType.Label.copy(
                            color = if (selected) ArtisanColors.Champagne else ArtisanColors.TextMuted,
                            fontSize = 13.sp
                        )
                    )
                    Spacer(Modifier.height(3.dp))
                    AnimatedVisibility(visible = selected) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(GoldGradient)
                        )
                    }
                }
            }
        }
    }
}

// ── 反推图上传（单张）────────────────────────────────────
@Composable
private fun ReverseImageSection(
    imageBase64: String?,
    isAnalyzing: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("反推参考图", style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary, fontSize = 11.sp))
            if (isAnalyzing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp, color = ArtisanColors.Champagne)
                    Text("AI 分析中...", style = ArtisanType.Caption.copy(color = ArtisanColors.Champagne, fontSize = 10.sp))
                }
            } else {
                Text(if (imageBase64 != null) "1/1" else "0/1",
                    style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 11.sp))
            }
        }

        if (imageBase64 != null) {
            ImageThumbnailRow(
                images = listOf(imageBase64),
                onRemove = { onClear() },
                onAdd = null  // 单张，不允许再加
            )
        } else {
            DashedUploadButton(text = "上传反推图片", onClick = onPick)
        }
    }
}

// ── 参考图多选（最多5张）────────────────────────────────
@Composable
private fun MultiRefImageSection(
    images: List<String>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("参考图 (点击插入 <图N>)", style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary, fontSize = 11.sp))
            Text("${images.size}/5", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 11.sp))
        }

        if (images.isEmpty()) {
            DashedUploadButton(text = "上传参考图（生成风格参考）", onClick = onAdd)
        } else {
            ImageThumbnailRow(
                images = images,
                onRemove = onRemove,
                onAdd = if (images.size < 5) onAdd else null
            )
        }
    }
}

// ── 缩略图横排 ───────────────────────────────────────────
@Composable
private fun ImageThumbnailRow(
    images: List<String>,
    onRemove: (Int) -> Unit,
    onAdd: (() -> Unit)?
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        images.forEachIndexed { index, base64 ->
            val bitmap = remember(base64) {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ArtisanColors.Graphite)
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit  // 保持比例，不拉伸
                    )
                }
                // 删除按钮
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ArtisanColors.Obsidian.copy(alpha = 0.8f))
                        .clickable { onRemove(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, null, tint = ArtisanColors.TextPrimary, modifier = Modifier.size(10.dp))
                }
            }
        }

        // 加号按钮（未满5张时显示）
        if (onAdd != null) {
            val dashColor = ArtisanColors.Steel
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ArtisanColors.Graphite.copy(alpha = 0.3f))
                    .drawBehind {
                        drawRoundRect(
                            color = dashColor,
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f), 0f)
                            ),
                            cornerRadius = CornerRadius(6.dp.toPx())
                        )
                    }
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, null, tint = ArtisanColors.TextMuted, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ── 虚线上传按钮 ─────────────────────────────────────────
@Composable
private fun DashedUploadButton(text: String, onClick: () -> Unit) {
    val dashColor = ArtisanColors.Steel
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ArtisanColors.Graphite.copy(alpha = 0.3f))
            .drawBehind {
                drawRoundRect(
                    color = dashColor,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    ),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.AddPhotoAlternate, null, tint = ArtisanColors.TextMuted, modifier = Modifier.size(18.dp))
            Text(text, style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 12.sp))
        }
    }
}

// ── 提示词区 ─────────────────────────────────────────────
@Composable
private fun PromptSection(uiState: MainUiState, viewModel: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("提示词", style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary))
            TextButton(
                onClick = viewModel::polishPrompt,
                enabled = !uiState.isPolishing && uiState.userPrompt.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                if (uiState.isPolishing) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = ArtisanColors.Champagne)
                } else {
                    Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(12.dp), tint = ArtisanColors.Champagne)
                    Spacer(Modifier.width(3.dp))
                    Text("AI 优化", style = ArtisanType.Caption.copy(color = ArtisanColors.Champagne, fontSize = 11.sp))
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ArtisanColors.Graphite.copy(alpha = 0.4f))
                .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(6.dp))
        ) {
            val minLines = if (uiState.isReverseMode) 2 else 3
            val maxLines = if (uiState.isReverseMode) 3 else 5
            val placeholder = if (uiState.isReverseMode) "可选：补充描述（反推结果会自动填入）" else "描述你想生成的图片..."
            androidx.compose.foundation.text.BasicTextField(
                value = uiState.userPrompt,
                onValueChange = viewModel::updateUserPrompt,
                textStyle = ArtisanType.Body.copy(color = ArtisanColors.TextPrimary, fontSize = 13.sp),
                minLines = minLines,
                maxLines = maxLines,
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                decorationBox = { inner ->
                    if (uiState.userPrompt.isEmpty()) Text(placeholder, style = ArtisanType.Body.copy(color = ArtisanColors.TextMuted, fontSize = 13.sp))
                    inner()
                }
            )
        }
    }
}

// ── 紧凑下拉框 ───────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> CompactDropdown(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    displayText: (T) -> String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary, fontSize = 11.sp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ArtisanColors.Graphite)
                    .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(displayText(selected), style = ArtisanType.Caption.copy(color = ArtisanColors.TextPrimary, fontSize = 12.sp))
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null, tint = ArtisanColors.TextMuted, modifier = Modifier.size(16.dp)
                )
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(ArtisanColors.Charcoal)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                displayText(option),
                                style = ArtisanType.Caption.copy(
                                    color = if (option == selected) ArtisanColors.Champagne else ArtisanColors.TextPrimary,
                                    fontSize = 12.sp
                                )
                            )
                        },
                        onClick = { onSelect(option); expanded = false },
                        modifier = Modifier.background(
                            if (option == selected) ArtisanColors.GoldMist else Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

// ── 高级选项（折叠） ─────────────────────────────────────
@Composable
private fun AdvancedOptions(uiState: MainUiState, viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("高级选项", style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                null, tint = ArtisanColors.TextMuted, modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ArtisanColors.Graphite.copy(alpha = 0.4f))
                    .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(6.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("思考模式", style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary, fontSize = 12.sp))
                        Text("minimal=快速生成，high=精准推理",
                            style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 10.sp))
                    }
                    ThinkingDropdown(selected = uiState.selectedThinkingLevel, onSelect = viewModel::selectThinkingLevel)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("图像搜索", style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary, fontSize = 12.sp))
                        Text("接入 Google 图片搜索，场景更准确 (Grounding)",
                            style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 10.sp))
                    }
                    Switch(
                        checked = uiState.useGrounding,
                        onCheckedChange = { viewModel.toggleGrounding() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ArtisanColors.Obsidian,
                            checkedTrackColor = ArtisanColors.Champagne,
                            uncheckedThumbColor = ArtisanColors.TextMuted,
                            uncheckedTrackColor = ArtisanColors.Graphite
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThinkingDropdown(selected: ThinkingLevel, onSelect: (ThinkingLevel) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        Row(
            modifier = Modifier
                .menuAnchor()
                .width(90.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ArtisanColors.Charcoal)
                .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(selected.label, style = ArtisanType.Caption.copy(color = ArtisanColors.TextPrimary, fontSize = 11.sp))
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                null, tint = ArtisanColors.TextMuted, modifier = Modifier.size(14.dp))
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(ArtisanColors.Charcoal)
        ) {
            ThinkingLevel.entries.forEach { level ->
                DropdownMenuItem(
                    text = { Text(level.label, style = ArtisanType.Caption.copy(color = ArtisanColors.TextPrimary, fontSize = 12.sp)) },
                    onClick = { onSelect(level); expanded = false }
                )
            }
        }
    }
}

// ── 工具：压缩图片 ───────────────────────────────────────
private fun compressImage(context: android.content.Context, uri: Uri): String {
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
