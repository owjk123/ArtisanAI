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
import com.artisanai.util.EndpointHealth
import com.artisanai.ui.components.*
import com.artisanai.ui.theme.ArtisanColors
import com.artisanai.ui.theme.ArtisanType
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // 生图 API
    var apiKey by remember { mutableStateOf(ApiKeyManager.loadApiKey(context)) }
    var baseUrl by remember { mutableStateOf(ApiKeyManager.loadBaseUrl(context)) }
    var keyVisible by remember { mutableStateOf(false) }
    // Agent API（AI优化/反推）
    var agentKey by remember { mutableStateOf(
        context.getSharedPreferences("artisan_prefs", android.content.Context.MODE_PRIVATE)
            .getString("agent_api_key", "") ?: ""
    ) }
    var agentUrl by remember { mutableStateOf(
        context.getSharedPreferences("artisan_prefs", android.content.Context.MODE_PRIVATE)
            .getString("agent_base_url", "") ?: ""
    ) }
    var agentKeyVisible by remember { mutableStateOf(false) }
    var agentSystemPrompt by remember { mutableStateOf(ApiKeyManager.loadAgentSystemPrompt(context)) }

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

            // ── API 线路 卡片 ────────────────────────────
            val scope = rememberCoroutineScope()
            var customMode by remember {
                mutableStateOf(baseUrl.trimEnd('/') !in ApiKeyManager.PRESETS.map { it.url.trimEnd('/') })
            }
            var checking by remember { mutableStateOf(false) }
            var healthResults by remember { mutableStateOf<List<EndpointHealth.Result>>(emptyList()) }

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
                    Column(Modifier.weight(1f)) {
                        Text("API 线路", style = ArtisanType.TitleGold.copy(fontSize = 15.sp))
                        Text("选择最快线路，失败会自动切换其他线路", style = ArtisanType.Caption)
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                checking = true
                                healthResults = EndpointHealth.checkAll()
                                checking = false
                            }
                        },
                        enabled = !checking
                    ) {
                        Text(
                            if (checking) "测试中..." else "测速",
                            style = ArtisanType.Caption.copy(color = ArtisanColors.Champagne)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 4 条预设线路
                ApiKeyManager.PRESETS.forEach { preset ->
                    val selected = !customMode && baseUrl.trimEnd('/') == preset.url.trimEnd('/')
                    val health = healthResults.firstOrNull { it.preset.url == preset.url }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) ArtisanColors.GoldMist else ArtisanColors.Graphite
                            )
                            .clickable {
                                customMode = false
                                baseUrl = preset.url
                                saved = false
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = {
                                customMode = false
                                baseUrl = preset.url
                                saved = false
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = ArtisanColors.Champagne,
                                unselectedColor = ArtisanColors.TextMuted
                            )
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${preset.label} · ${preset.desc}",
                                style = ArtisanType.Body.copy(
                                    fontSize = 13.sp,
                                    color = if (selected) ArtisanColors.Champagne else ArtisanColors.TextPrimary
                                )
                            )
                            Text(
                                preset.url,
                                style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 10.sp)
                            )
                        }
                        // 健康状态徽标
                        if (health != null) {
                            val (badgeColor, badgeText) = when {
                                !health.reachable -> ArtisanColors.TextMuted to health.message
                                health.latencyMs < 300 -> ArtisanColors.Success to "${health.latencyMs}ms"
                                health.latencyMs < 1000 -> ArtisanColors.Champagne to "${health.latencyMs}ms"
                                else -> ArtisanColors.TextSecondary to "${health.latencyMs}ms"
                            }
                            Text(
                                badgeText,
                                style = ArtisanType.Caption.copy(color = badgeColor, fontSize = 10.sp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                // 自定义地址
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (customMode) ArtisanColors.GoldMist else ArtisanColors.Graphite)
                        .clickable {
                            customMode = true
                            saved = false
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = customMode,
                        onClick = { customMode = true; saved = false },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = ArtisanColors.Champagne,
                            unselectedColor = ArtisanColors.TextMuted
                        )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "自定义中转地址",
                        style = ArtisanType.Body.copy(
                            fontSize = 13.sp,
                            color = if (customMode) ArtisanColors.Champagne else ArtisanColors.TextPrimary
                        )
                    )
                }

                if (customMode) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it; saved = false },
                        placeholder = { Text("https://your-relay.example.com", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted)) },
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
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = ArtisanColors.TextMuted, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "测速探测 HTTP:${ApiKeyManager.STATUS_PORT}；生图失败时自动按顺序切换其他预设线路",
                        style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 11.sp)
                    )
                }
            }

            // ── Agent API（AI优化/反推）卡片 ─────────────
            ArtisanCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ArtisanColors.GoldMist),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoFixHigh, null, tint = ArtisanColors.Champagne, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Agent API (AI优化/反推)", style = ArtisanType.TitleGold.copy(fontSize = 15.sp))
                        Text(
                            if (agentKey.isNotBlank()) "已配置独立 Key" else "未设置时自动使用生图 API",
                            style = ArtisanType.Caption.copy(
                                color = if (agentKey.isNotBlank()) ArtisanColors.Success else ArtisanColors.TextMuted
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = agentKey,
                    onValueChange = { agentKey = it; saved = false },
                    placeholder = { Text("独立 Agent Key（留空则复用生图 Key）", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted)) },
                    visualTransformation = if (agentKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { agentKeyVisible = !agentKeyVisible }) {
                            Icon(
                                if (agentKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null, tint = ArtisanColors.TextMuted, modifier = Modifier.size(18.dp)
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

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = agentUrl,
                    onValueChange = { agentUrl = it; saved = false },
                    placeholder = { Text("Agent API 地址（留空则复用生图地址）", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted)) },
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
                Spacer(Modifier.height(12.dp))
                Text("自定义系统提示词（AI优化 + 反推 共用）",
                    style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary, fontSize = 11.sp))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = agentSystemPrompt,
                    onValueChange = { agentSystemPrompt = it; saved = false },
                    placeholder = { Text("附加指令，如：风格倾向写实/日系/电影感...", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = ArtisanType.Body.copy(fontSize = 13.sp),
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
                    "会追加到两个 Agent 的系统提示词末尾，留空则使用默认提示词。不同模型/服务时可单独配置地址",
                    style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 11.sp)
                )
            }

            // ── 保存按钮 ──────────────────────────────────
            GoldButton(
                text = if (saved) "✓  已保存" else "保存配置",
                onClick = {
                    ApiKeyManager.saveApiKey(context, apiKey)
                    ApiKeyManager.saveBaseUrl(context, baseUrl.ifBlank { "https://api.apiyi.com" })
                    ApiKeyManager.saveAgentApiKey(context, agentKey)
                    ApiKeyManager.saveAgentBaseUrl(context, agentUrl)
                    ApiKeyManager.saveAgentSystemPrompt(context, agentSystemPrompt)
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
    HorizontalDivider(color = ArtisanColors.Steel.copy(alpha = 0.4f), thickness = 0.5.dp)
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
