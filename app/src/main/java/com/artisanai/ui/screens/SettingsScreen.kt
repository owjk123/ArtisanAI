package com.artisanai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.artisanai.util.ApiKeyManager
import com.artisanai.ui.components.*
import com.artisanai.ui.theme.ArtisanColors
import com.artisanai.ui.theme.ArtisanType

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(ApiKeyManager.loadApiKey(context)) }
    var baseUrl by remember { mutableStateOf(ApiKeyManager.loadBaseUrl(context)) }
    var keyVisible by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    val hasKey = apiKey.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ArtisanColors.Obsidian)
            .verticalScroll(rememberScrollState())
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = ArtisanColors.TextSecondary)
            }
            Spacer(Modifier.width(8.dp))
            Text("设置", style = ArtisanType.TitleGold)
        }

        GoldDivider()

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── API Key 卡片 ─────────────────────────────
            ArtisanCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ArtisanColors.GoldMist),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Key, null, tint = ArtisanColors.Champagne, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("API Key", style = ArtisanType.TitleGold.copy(fontSize = 15.sp))
                        Text(
                            if (hasKey) "已配置 · 点击修改" else "未配置 · 请填写您的 API Key",
                            style = ArtisanType.Caption.copy(
                                color = if (hasKey) ArtisanColors.Success else ArtisanColors.TextMuted
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Key输入框
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; saved = false },
                    placeholder = { Text("sk-...", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted)) },
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null,
                                tint = ArtisanColors.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = ArtisanType.Body,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArtisanColors.Champagne,
                        unfocusedBorderColor = ArtisanColors.Steel,
                        focusedTextColor = ArtisanColors.TextPrimary,
                        unfocusedTextColor = ArtisanColors.TextPrimary,
                        cursorColor = ArtisanColors.Champagne,
                        focusedContainerColor = ArtisanColors.Graphite,
                        unfocusedContainerColor = ArtisanColors.Graphite,
                    ),
                    shape = RoundedCornerShape(6.dp)
                )

                Spacer(Modifier.height(6.dp))

                // 提示文字
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = ArtisanColors.TextMuted, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "前往 api.apiyi.com 注册获取 Key，充值后即可使用",
                        style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 11.sp)
                    )
                }
            }

            // ── Base URL 卡片 ────────────────────────────
            ArtisanCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ArtisanColors.GoldMist),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Link, null, tint = ArtisanColors.Champagne, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("API 地址", style = ArtisanType.TitleGold.copy(fontSize = 15.sp))
                        Text("默认使用 api.apiyi.com", style = ArtisanType.Caption)
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; saved = false },
                    placeholder = { Text("https://api.apiyi.com", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = ArtisanType.Body,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArtisanColors.Champagne,
                        unfocusedBorderColor = ArtisanColors.Steel,
                        focusedTextColor = ArtisanColors.TextPrimary,
                        unfocusedTextColor = ArtisanColors.TextPrimary,
                        cursorColor = ArtisanColors.Champagne,
                        focusedContainerColor = ArtisanColors.Graphite,
                        unfocusedContainerColor = ArtisanColors.Graphite,
                    ),
                    shape = RoundedCornerShape(6.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "兼容任意 OpenAI 格式接口，可替换为自定义中转地址",
                    style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 11.sp)
                )
            }

            // ── 保存按钮 ──────────────────────────────────
            GoldButton(
                text = if (saved) "✓  已保存" else "保存配置",
                onClick = {
                    ApiKeyManager.saveApiKey(context, apiKey)
                    ApiKeyManager.saveBaseUrl(context, baseUrl.ifBlank { "https://api.apiyi.com" })
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = apiKey.isNotBlank()
            )

            // ── 关于 ──────────────────────────────────────
            ArtisanCard {
                SectionLabel("关于 · ABOUT")
                Spacer(Modifier.height(12.dp))
                AboutRow("应用版本", "1.0.0")
                AboutRow("图像模型", "Nano Banana 2  (gemini-3.1-flash-image-preview)")
                AboutRow("Agent 模型", "gemini-3.1-flash-lite-preview")
                AboutRow("最大并发", "10 个任务")
                AboutRow("默认分辨率", "2K · 9:16")
            }
        }

        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary))
        Text(value, style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted))
    }
    Divider(color = ArtisanColors.Steel.copy(alpha = 0.4f), thickness = 0.5.dp)
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
