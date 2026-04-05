import androidx.compose.foundation.text.BasicTextField
package com.artisanai.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.artisanai.ui.theme.ArtisanColors
import com.artisanai.ui.theme.ArtisanType

// ── 金色渐变背景 ───────────────────────────────────────────
val GoldGradient = Brush.linearGradient(
    colors = listOf(ArtisanColors.ChampagneDark, ArtisanColors.Champagne, ArtisanColors.ChampagneLight),
    start = Offset(0f, 0f),
    end = Offset(300f, 0f)
)

val SubtleGoldBorder = Brush.linearGradient(
    colors = listOf(
        ArtisanColors.ChampagneDark.copy(alpha = 0.3f),
        ArtisanColors.Champagne.copy(alpha = 0.6f),
        ArtisanColors.ChampagneDark.copy(alpha = 0.3f)
    )
)

// ── 主按钮：金色渐变 ───────────────────────────────────────
@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "alpha")

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) GoldGradient else Brush.linearGradient(
                listOf(ArtisanColors.Steel, ArtisanColors.Steel)
            ))
            .clickable(enabled = enabled && !isLoading) { onClick() }
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = ArtisanColors.Obsidian,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                style = ArtisanType.Label.copy(
                    color = ArtisanColors.Obsidian,
                    letterSpacing = 2.5.sp
                )
            )
        }
    }
}

// ── 次级按钮：描边金色 ─────────────────────────────────────
@Composable
fun OutlineGoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(ArtisanColors.Graphite)
            .border(
                width = 1.dp,
                brush = if (enabled) SubtleGoldBorder else Brush.linearGradient(
                    listOf(ArtisanColors.Steel, ArtisanColors.Steel)
                ),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(enabled = enabled && !isLoading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = ArtisanColors.Champagne,
                strokeWidth = 1.5.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                icon?.invoke()
                Text(
                    text = text,
                    style = ArtisanType.Label.copy(
                        color = if (enabled) ArtisanColors.Champagne else ArtisanColors.TextMuted
                    )
                )
            }
        }
    }
}

// ── 磨砂玻璃卡片 ──────────────────────────────────────────
@Composable
fun ArtisanCard(
    modifier: Modifier = Modifier,
    hasBorder: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ArtisanColors.Onyx)
            .then(
                if (hasBorder) Modifier.border(
                    1.dp,
                    ArtisanColors.Steel,
                    RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .padding(20.dp),
        content = content
    )
}

// ── 细金线分割 ─────────────────────────────────────────────
@Composable
fun GoldDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SubtleGoldBorder)
    )
}

// ── 标签栏选项 ─────────────────────────────────────────────
@Composable
fun <T> ChipSelector(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isSelected) GoldGradient
                        else Brush.linearGradient(listOf(ArtisanColors.Graphite, ArtisanColors.Graphite))
                    )
                    .border(
                        1.dp,
                        if (isSelected) Color.Transparent else ArtisanColors.Steel,
                        RoundedCornerShape(3.dp)
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(option),
                    style = ArtisanType.Label.copy(
                        color = if (isSelected) ArtisanColors.Obsidian else ArtisanColors.TextSecondary,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

// ── 节标题 ────────────────────────────────────────────────
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = ArtisanType.Label,
        modifier = modifier
    )
}

// ── 输入框 ────────────────────────────────────────────────
@Composable
fun ArtisanTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 3,
    maxLines: Int = 6,
    trailingActions: @Composable (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ArtisanColors.Graphite)
                .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(6.dp))
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = ArtisanType.Body.copy(color = ArtisanColors.TextPrimary),
                minLines = minLines,
                maxLines = maxLines,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, style = ArtisanType.Body.copy(color = ArtisanColors.TextMuted))
                    }
                    inner()
                }
            )
        }
        if (trailingActions != null) {
            Spacer(Modifier.height(8.dp))
            trailingActions()
        }
    }
}

// ── 任务状态徽章 ──────────────────────────────────────────
@Composable
fun StatusBadge(status: com.artisanai.data.model.TaskStatus) {
    val (color, label) = when (status) {
        com.artisanai.data.model.TaskStatus.QUEUED -> ArtisanColors.TextMuted to "等待中"
        com.artisanai.data.model.TaskStatus.PROCESSING -> ArtisanColors.Champagne to "生成中"
        com.artisanai.data.model.TaskStatus.SUCCESS -> ArtisanColors.Success to "完成"
        com.artisanai.data.model.TaskStatus.FAILED -> ArtisanColors.Error to "失败"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, style = ArtisanType.Caption.copy(color = color))
    }
}

// 扩展属性，避免导入冲突
private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
private val Double.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
