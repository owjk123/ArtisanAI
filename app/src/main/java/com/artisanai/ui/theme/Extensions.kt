package com.artisanai.ui.theme

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType

// 统一 sp 扩展，避免每个文件重复定义
val Int.sp: TextUnit
    get() = TextUnit(this.toFloat(), TextUnitType.Sp)

val Float.sp: TextUnit
    get() = TextUnit(this, TextUnitType.Sp)

val Double.sp: TextUnit
    get() = TextUnit(this.toFloat(), TextUnitType.Sp)
