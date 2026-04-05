package com.artisanai.ui.screens

import androidx.compose.animation.*
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
import com.artisanai.BuildConfig
import com.artisanai.util.ApiKeyManager
import com.artisanai.ui.components.*
import com.artisanai.ui.theme.ArtisanColors
import com.artisanai.ui.theme.ArtisanType
import com.artisanai.ui.theme.sp
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(ApiKeyManager.loadApiKey(context)) }
    var baseUrl by remember { mutableStateOf(ApiKeyManager.loadBaseUrl(context)) }
    var keyVisible by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

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
                    Spacer(Modifier.weight(1f))
                    // API 测试按钮
                    if (hasKey) {
                        IconButton(
                            onClick = {
                                isTesting = true
                                testResult = null
                                scope.launch {
                                    testResult = testApiHealth(baseUrl.ifBlank { "https://api.apiyi.com" }, apiKey)
                                    isTesting = false
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = ArtisanColors.Champagne,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    when (testResult) {
                                        "ok" -> Icons.Default.CheckCircle
                                        "fail" -> Icons.Default.ErrorOutline
                                        else -> Icons.Default.NetworkPing
                                    },
                                    null,
                                    tint = when (testResult) {
                                        "ok" -> ArtisanColors.Success
                                        "fail" -> ArtisanColors.Error
                                        else -> ArtisanColors.TextMuted
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Key输入框
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; saved = false; testResult = null },
                    placeholder = { Text("sk-...", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted)) },
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null,
                                    tint = ArtisanColors.TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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

                // 测试结果
                AnimatedVisibility(visible = testResult != null && !isTesting) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (testResult == "ok") Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            null,
                            tint = if (testResult == "ok") ArtisanColors.Success else ArtisanColors.Error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (testResult == "ok") "API 连接正常" else "API 连接失败，请检查 Key 和地址",
                            style = ArtisanType.Caption.copy(
                                color = if (testResult == "ok") ArtisanColors.Success else ArtisanColors.Error,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

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
                    onValueChange = { baseUrl = it; saved = false; testResult = null },
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
                    testResult = null
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = apiKey.isNotBlank()
            )

            // ── 关于 ──────────────────────────────────────
            ArtisanCard {
                SectionLabel("关于 · ABOUT")
                Spacer(Modifier.height(12.dp))
                AboutRow("应用版本", BuildConfig.VERSION_NAME)
                AboutRow("版本号", BuildConfig.VERSION_CODE.toString())
                AboutRow("构建类型", BuildConfig.BUILD_TYPE)
                AboutRow("图像模型", "Nano Banana 2  (gemini-3.1-flash-image-preview)")
                AboutRow("Agent 模型", "gemini-3.1-flash-lite-preview")
                AboutRow("最大并发", "10 个任务")
                AboutRow("默认分辨率", "2K · 9:16")
                AboutRow("数据库", "Room SQLite")
                AboutRow("API Key 存储", "SharedPreferences（本地）")
                AboutRow("网络库", "OkHttp 4")
            }

            // ── 使用提示 ──────────────────────────────────
            ArtisanCard {
                SectionLabel("使用提示 · TIPS")
                Spacer(Modifier.height(12.dp))
                TipRow(Icons.Default.AutoAwesome, "使用「提示词模板」快速开始")
                TipRow(Icons.Default.AutoFixHigh, "「AI 润色」可将中文描述转为专业英文提示词")
                TipRow(Icons.Default.ImageSearch, "上传参考图可实现风格迁移（图生图）")
                TipRow(Icons.Default.PlaylistPlay, "任务队列支持最多10个并发生成")
                TipRow(Icons.Default.Download, "生成的图片默认保存在 App 图库，手动保存到相册")
            }
        }

        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary))
            Text(value, style = ArtisanType.Caption.copy(color = ArtisanColors.Champagne))
        }
        HorizontalDivider(color = ArtisanColors.Steel.copy(alpha = 0.4f), thickness = 0.5.dp)
    }
}

@Composable
private fun TipRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = ArtisanColors.Champagne, modifier = Modifier.size(14.dp))
        Text(text, style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary))
    }
}

private fun testApiHealth(baseUrl: String, apiKey: String): String {
    return try {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("$baseUrl/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) "ok" else "fail"
    } catch (e: Exception) {
        "fail"
    }
}
