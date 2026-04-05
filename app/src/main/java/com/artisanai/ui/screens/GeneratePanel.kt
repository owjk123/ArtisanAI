package com.artisanai.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
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
import com.artisanai.data.model.*
import com.artisanai.ui.components.*
import com.artisanai.ui.theme.ArtisanColors
import com.artisanai.ui.theme.ArtisanType
import com.artisanai.ui.theme.sp
import com.artisanai.viewmodel.MainUiState
import com.artisanai.viewmodel.MainViewModel

// ── 提示词模板 ─────────────────────────────────────────────
private val PROMPT_TEMPLATES = listOf(
    "人像摄影" to "masterpiece, best quality, ultra detailed, professional portrait photography, soft natural lighting, shallow depth of field",
    "风景摄影" to "masterpiece, best quality, breathtaking landscape, golden hour, volumetric fog, cinematic composition, 8k resolution",
    "二次元动漫" to "masterpiece, best quality, anime style, vibrant colors, detailed background, studio ghibli inspired, soft lighting",
    "赛博朋克" to "cyberpunk, neon lights, futuristic city, rain-soaked streets, hologram, high tech low life, cinematic, 8k",
    "油画质感" to "oil painting style, classical art, museum quality, rich textures, dramatic chiaroscuro lighting, renaissance style",
    "商业广告" to "commercial photography, product shot, clean background, studio lighting, professional, magazine cover quality",
    "电影感" to "cinematic, film grain, anamorphic lens flare, movie still, dramatic lighting, color grading, 35mm film",
    "东方美学" to "chinese ink painting, shan shui, traditional chinese art, ethereal mist, mountain landscape, zen atmosphere",
)

@Composable
fun GeneratePanel(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showTemplates by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    // 图片选择器
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes() ?: return@let
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            viewModel.setReferenceImage(base64)
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("描述 · PROMPT")
                // 复制提示词
                if (uiState.userPrompt.isNotBlank()) {
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("prompt", uiState.userPrompt))
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy, null,
                            tint = ArtisanColors.TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ArtisanTextField(
                value = uiState.userPrompt,
                onValueChange = viewModel::updateUserPrompt,
                placeholder = "描述您想要的画面...",
                minLines = 3,
                maxLines = 5
            )

            // 提示词模板
            if (showTemplates) {
                Spacer(Modifier.height(8.dp))
                Text("快速模板", style = ArtisanType.Caption.copy(color = ArtisanColors.Champagne))
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PROMPT_TEMPLATES.forEach { (label, template) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(ArtisanColors.Graphite)
                                .clickable {
                                    val newPrompt = if (uiState.userPrompt.isBlank()) template
                                    else "${uiState.userPrompt}, $template"
                                    viewModel.updateUserPrompt(newPrompt)
                                    showTemplates = false
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary))
                            Icon(
                                Icons.Default.Add, null,
                                tint = ArtisanColors.TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlineGoldButton(
                    text = if (showTemplates) "收起模板" else "提示词模板",
                    onClick = { showTemplates = !showTemplates },
                    modifier = Modifier.weight(1f),
                    icon = {
                        Icon(
                            if (showTemplates) Icons.Default.ExpandLess else Icons.Default.AutoAwesome,
                            null, modifier = Modifier.size(14.dp),
                            tint = ArtisanColors.Champagne
                        )
                    }
                )
                OutlineGoldButton(
                    text = "AI 润色",
                    onClick = viewModel::polishPrompt,
                    isLoading = uiState.isPolishing,
                    modifier = Modifier.weight(1f),
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
        }

        // ── 参考图 + 反推提示词 ──────────────────────────
        ArtisanCard {
            SectionLabel("参考图 · REFERENCE")
            Spacer(Modifier.height(12.dp))

            if (uiState.referenceImageBase64 != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
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
                        Text("点击选择参考图（图生图）", style = ArtisanType.Caption)
                    }
                }
            }
        }

        // ── 参考提示词（润色/反推结果）──────────────────
        AnimatedVisibility(visible = uiState.referencePrompt.isNotBlank()) {
            ArtisanCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("参考提示词 · REFERENCE PROMPT")
                    if (uiState.referencePrompt.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("ref_prompt", uiState.referencePrompt))
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, tint = ArtisanColors.TextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("生成参数 · PARAMETERS")
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(
                        if (showAdvanced) "收起" else "更多选项",
                        style = ArtisanType.Caption.copy(
                            color = ArtisanColors.Champagne,
                            fontSize = 10.sp
                        )
                    )
                }
            }
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

            // 高级选项
            AnimatedVisibility(visible = showAdvanced) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    GoldDivider()
                    Spacer(Modifier.height(14.dp))

                    // Grounding 开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("图像搜索增强", style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary))
                            Text("参考真实世界图像生成（Grounding）", style = ArtisanType.Caption.copy(
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

                    Spacer(Modifier.height(12.dp))

                    // 当前配置摘要
                    Text(
                        "当前配置：${uiState.selectedAspectRatio.label} · ${uiState.selectedImageSize.label} · ${uiState.selectedThinkingLevel.label}" +
                                if (uiState.useGrounding) " · 🌐 Grounding" else "",
                        style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 10.sp)
                    )
                }
            }
        }

        // ── 生成按钮 ──────────────────────────────────────
        GoldButton(
            text = "开始生成",
            onClick = viewModel::addGenerateTask,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.userPrompt.isNotBlank()
        )

        // 并发提示
        val activeCount = uiState.tasks.count {
            it.status == TaskStatus.QUEUED || it.status == TaskStatus.PROCESSING
        }
        if (activeCount > 0) {
            Text(
                "$activeCount 个任务进行中（最多10个并发）",
                style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}
