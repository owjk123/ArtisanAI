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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.artisanai.data.model.*
import com.artisanai.ui.components.*
import com.artisanai.ui.theme.ArtisanColors
import com.artisanai.ui.theme.ArtisanType
import com.artisanai.viewmodel.MainUiState
import com.artisanai.viewmodel.MainViewModel

@Composable
fun GeneratePanel(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 图片选择器
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it) ?: return@let
            val rawBytes = inputStream.readBytes()
            // 解码原始图片，压缩到最大边1280px，减少内存占用和API传输量
            val original = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: return@let
            val maxDim = 1280
            val scale = if (original.width > maxDim || original.height > maxDim) {
                maxDim.toFloat() / maxOf(original.width, original.height)
            } else 1f
            val bitmap = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt(),
                    (original.height * scale).toInt(),
                    true
                ).also { original.recycle() }
            } else original
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            bitmap.recycle()
            val base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
            viewModel.setReferenceImage(base64)
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── App 标题 ─────────────────────────────────────
        Column {
            Text("ARTISAN", style = ArtisanType.DisplayLarge.copy(
                color = ArtisanColors.Champagne,
                letterSpacing = 8.sp
            ))
            Text("AI IMAGE STUDIO", style = ArtisanType.Label.copy(
                color = ArtisanColors.TextMuted,
                letterSpacing = 4.sp
            ))
        }

        GoldDivider()

        // ── 用户主提示词 ──────────────────────────────────
        ArtisanCard {
            SectionLabel("描述 · PROMPT")
            Spacer(Modifier.height(12.dp))
            ArtisanTextField(
                value = uiState.userPrompt,
                onValueChange = viewModel::updateUserPrompt,
                placeholder = "描述您想要的画面...",
                minLines = 3,
                maxLines = 5
            )
            Spacer(Modifier.height(12.dp))
            OutlineGoldButton(
                text = "AI 润色提示词",
                onClick = viewModel::polishPrompt,
                isLoading = uiState.isPolishing,
                modifier = Modifier.fillMaxWidth(),
                icon = {
                    Icon(
                        Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = ArtisanColors.Champagne
                    )
                }
            )
        }

        // ── 参考图 + 反推提示词 ──────────────────────────
        ArtisanCard {
            SectionLabel("参考图 · REFERENCE")
            Spacer(Modifier.height(12.dp))

            // 参考图预览
            if (uiState.referenceImageBase64 != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ArtisanColors.Graphite)
                ) {
                    val bitmap = remember(uiState.referenceImageBase64) {
                        val bytes = android.util.Base64.decode(
                            uiState.referenceImageBase64, android.util.Base64.NO_WRAP
                        )
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "参考图",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    // 清除按钮
                    IconButton(
                        onClick = viewModel::clearReferenceImage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ArtisanColors.Obsidian.copy(alpha = 0.7f))
                    ) {
                        Icon(Icons.Default.Close, null, tint = ArtisanColors.TextPrimary, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlineGoldButton(
                    text = "AI 反推提示词",
                    onClick = viewModel::reversePromptFromImage,
                    isLoading = uiState.isReversingPrompt,
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(Icons.Default.ImageSearch, null, modifier = Modifier.size(14.dp), tint = ArtisanColors.Champagne)
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ArtisanColors.Graphite)
                        .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(6.dp))
                        .clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AddPhotoAlternate, null, tint = ArtisanColors.TextMuted, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("点击选择参考图（可选）", style = ArtisanType.Caption)
                    }
                }
            }
        }

        // ── 参考提示词（润色/反推结果）──────────────────
        AnimatedVisibility(visible = uiState.referencePrompt.isNotBlank()) {
            ArtisanCard {
                SectionLabel("参考提示词 · REFERENCE PROMPT")
                Spacer(Modifier.height(12.dp))
                ArtisanTextField(
                    value = uiState.referencePrompt,
                    onValueChange = viewModel::updateReferencePrompt,
                    placeholder = "AI生成的参考提示词（可编辑）",
                    minLines = 2,
                    maxLines = 4
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "此提示词将与您的描述合并后用于生图",
                    style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted)
                )
            }
        }

        // ── 参数设置 ──────────────────────────────────────
        ArtisanCard {
            SectionLabel("生成参数 · PARAMETERS")
            Spacer(Modifier.height(16.dp))

            // 宽高比
            Text("宽高比", style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary))
            Spacer(Modifier.height(8.dp))
            ChipSelector(
                options = AspectRatio.entries,
                selected = uiState.selectedAspectRatio,
                onSelect = viewModel::selectAspectRatio,
                label = { it.label }
            )

            Spacer(Modifier.height(14.dp))

            // 分辨率
            Text("分辨率", style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary))
            Spacer(Modifier.height(8.dp))
            ChipSelector(
                options = ImageSize.entries,
                selected = uiState.selectedImageSize,
                onSelect = viewModel::selectImageSize,
                label = { it.label }
            )

            Spacer(Modifier.height(14.dp))

            // 思维模式
            Text("思维模式", style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary))
            Spacer(Modifier.height(8.dp))
            ChipSelector(
                options = ThinkingLevel.entries,
                selected = uiState.selectedThinkingLevel,
                onSelect = viewModel::selectThinkingLevel,
                label = { it.label }
            )

            Spacer(Modifier.height(14.dp))

            // Grounding 开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("图像搜索增强", style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary))
                    Text("参考真实图像生成", style = ArtisanType.Caption.copy(
                        color = ArtisanColors.TextMuted,
                        fontSize = 10.sp
                    ))
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

        // ── 生成按钮 ──────────────────────────────────────
        GoldButton(
            text = "开始生成",
            onClick = viewModel::addGenerateTask,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.userPrompt.isNotBlank()
        )

        Spacer(Modifier.height(80.dp)) // 底部padding
    }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
