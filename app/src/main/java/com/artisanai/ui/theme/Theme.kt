package com.artisanai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── 高奢色系 ──────────────────────────────────────────────
object ArtisanColors {
    // 背景层次
    val Obsidian      = Color(0xFF0A0A0A)   // 最深底色
    val Onyx          = Color(0xFF111111)   // 卡片底色
    val Charcoal      = Color(0xFF1A1A1A)   // 次级卡片
    val Graphite      = Color(0xFF242424)   // 输入框背景
    val Steel         = Color(0xFF2E2E2E)   // 分割线/边框

    // 香槟金系
    val Champagne     = Color(0xFFD4AF6E)   // 主金色
    val ChampagneLight= Color(0xFFEDD898)   // 浅金高光
    val ChampagneDark = Color(0xFF8B6914)   // 深金阴影
    val GoldMist      = Color(0x26D4AF6E)   // 金色半透明

    // 文字层次
    val TextPrimary   = Color(0xFFF5F0E8)   // 主文字（暖白）
    val TextSecondary = Color(0xFFB8B0A0)   // 次级文字
    val TextMuted     = Color(0xFF6B6560)   // 占位符

    // 功能色
    val Success       = Color(0xFF4CAF6E)
    val Error         = Color(0xFFCF6679)
    val Processing    = Color(0xFFD4AF6E)
}

// ── Material3 深色配色方案 ─────────────────────────────────
private val ArtisanColorScheme = darkColorScheme(
    primary           = ArtisanColors.Champagne,
    onPrimary         = ArtisanColors.Obsidian,
    primaryContainer  = ArtisanColors.ChampagneDark,
    onPrimaryContainer= ArtisanColors.ChampagneLight,
    secondary         = ArtisanColors.Steel,
    onSecondary       = ArtisanColors.TextPrimary,
    background        = ArtisanColors.Obsidian,
    onBackground      = ArtisanColors.TextPrimary,
    surface           = ArtisanColors.Onyx,
    onSurface         = ArtisanColors.TextPrimary,
    surfaceVariant    = ArtisanColors.Charcoal,
    onSurfaceVariant  = ArtisanColors.TextSecondary,
    outline           = ArtisanColors.Steel,
    error             = ArtisanColors.Error,
)

// ── 字体 (Google Fonts via downloadable fonts) ───────────
// 使用系统serif作为后备，实际项目可添加字体文件
val CormorantFamily = FontFamily.Serif   // 替换为真实Cormorant字体
val DMSansFamily    = FontFamily.Default // 替换为真实DM Sans字体

object ArtisanType {
    val DisplayLarge = TextStyle(
        fontFamily = CormorantFamily,
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        letterSpacing = 3.sp,
        color = ArtisanColors.TextPrimary
    )
    val DisplayMedium = TextStyle(
        fontFamily = CormorantFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        letterSpacing = 2.sp,
        color = ArtisanColors.TextPrimary
    )
    val TitleGold = TextStyle(
        fontFamily = CormorantFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 1.5.sp,
        color = ArtisanColors.Champagne
    )
    val Label = TextStyle(
        fontFamily = DMSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.8.sp,
        color = ArtisanColors.TextSecondary
    )
    val Body = TextStyle(
        fontFamily = DMSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.3.sp,
        color = ArtisanColors.TextPrimary
    )
    val Caption = TextStyle(
        fontFamily = DMSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.2.sp,
        color = ArtisanColors.TextSecondary
    )
}

// ── 主题入口 ───────────────────────────────────────────────
@Composable
fun ArtisanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArtisanColorScheme,
        content = content
    )
}
