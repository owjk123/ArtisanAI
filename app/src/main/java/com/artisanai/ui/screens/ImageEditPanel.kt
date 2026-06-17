package com.artisanai.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.artisanai.data.model.EditSession
import com.artisanai.data.model.EditTurn
import com.artisanai.ui.components.*
import com.artisanai.ui.theme.ArtisanColors
import com.artisanai.ui.theme.ArtisanType
import com.artisanai.viewmodel.MainUiState
import com.artisanai.viewmodel.MainViewModel
import java.io.ByteArrayOutputStream

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

    LaunchedEffect(Unit) { focusManager.clearFocus() }

    LaunchedEffect(session.turns.size) {
        if (session.turns.isNotEmpty()) listState.animateScrollToItem(session.turns.size - 1)
    }

    // 涂鸦状态
    var showDrawingCanvas by remember { mutableStateOf(false) }
    var drawingPaths by remember { mutableStateOf(listOf<List<Offset>>()) }
    var currentPath by remember { mutableStateOf(listOf<Offset>()) }
    // 画布尺寸（用于生成遮罩）
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            val b64 = compressImageForEdit(context, uri)
            if (b64.isNotEmpty()) viewModel.setEditSourceImage(b64)
        }
    }

    // 直接打开相册的 Intent
    val galleryIntent = remember {
        android.content.Intent(android.content.Intent.ACTION_PICK).apply {
            type = "image/*"
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
                // 涂鸦按钮
                if (session.sourceImageBase64 != null || session.turns.isNotEmpty()) {
                    IconButton(
                        onClick = { showDrawingCanvas = !showDrawingCanvas },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (showDrawingCanvas) ArtisanColors.Charcoal else ArtisanColors.Graphite)
                    ) {
                        Icon(
                            Icons.Default.Brush, null,
                            tint = if (showDrawingCanvas) ArtisanColors.Champagne else ArtisanColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                // 更换源图
                IconButton(
                    onClick = { imagePicker.launch(galleryIntent) },
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)).background(ArtisanColors.Graphite)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, null, tint = ArtisanColors.TextSecondary, modifier = Modifier.size(16.dp))
                }
                // 清除会话
                if (session.turns.isNotEmpty() || session.sourceImageBase64 != null) {
                    IconButton(
                        onClick = {
                            viewModel.clearEditSession()
                            drawingPaths = emptyList()
                            currentPath = emptyList()
                            showDrawingCanvas = false
                        },
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
                            .clickable { imagePicker.launch(galleryIntent) }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.PhotoLibrary, null, tint = ArtisanColors.Champagne, modifier = Modifier.size(16.dp))
                            Text("从相册选择", style = ArtisanType.Label.copy(color = ArtisanColors.Champagne))
                        }
                    }
                }
            }
            return@Column
        }

        // ── 涂鸦画板 ─────────────────────────────────────
        if (showDrawingCanvas) {
            val currentImageBase64 = if (session.turns.isNotEmpty()) {
                session.turns.last().resultImageBase64 ?: session.sourceImageBase64
            } else {
                session.sourceImageBase64
            }

            currentImageBase64?.let { imgBase64 ->
                DrawingCanvasSection(
                    imageBase64 = imgBase64,
                    paths = drawingPaths,
                    currentPath = currentPath,
                    onPathUpdate = { currentPath = it },
                    onPathComplete = {
                        if (currentPath.isNotEmpty()) {
                            drawingPaths = drawingPaths + listOf(currentPath)
                            currentPath = emptyList()
                        }
                    },
                    onClear = {
                        drawingPaths = emptyList()
                        currentPath = emptyList()
                    },
                    onCanvasSizeChanged = { canvasSize = it },
                    onApplyMask = {
                        // 生成遮罩并发送
                        if (drawingPaths.isNotEmpty() && canvasSize != androidx.compose.ui.geometry.Size.Zero) {
                            val maskBase64 = generateMaskBitmap(drawingPaths, canvasSize, imgBase64)
                            if (maskBase64 != null) {
                                val instruction = session.instruction.ifBlank { "根据涂鸦标记修改图片" }
                                viewModel.sendEditWithMask(instruction, maskBase64)
                                showDrawingCanvas = false
                                drawingPaths = emptyList()
                                currentPath = emptyList()
                            }
                        }
                    }
                )
            }
        }

        // ── 对话历史列表 ───────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            // 源图始终显示在顶部
            session.sourceImageBase64?.let { src ->
                item(key = "source") {
                    SourceImageCard(src)
                }
            }

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
                                    if (showDrawingCanvas) "输入涂鸦区域的修改指令..." else "输入编辑指令，如：把背景换成星空，风格改成水彩...",
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("使用参数:", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 10.sp))
                InfoTag(uiState.selectedAspectRatio.label)
                InfoTag(uiState.selectedImageSize.label)
                InfoTag(uiState.selectedThinkingLevel.label)
                if (showDrawingCanvas) {
                    InfoTag("涂鸦模式")
                }
            }
        }
    }
}

// ── 生成遮罩 Bitmap ─────────────────────────────────────
private fun generateMaskBitmap(
    paths: List<List<Offset>>,
    canvasSize: androidx.compose.ui.geometry.Size,
    originalImageBase64: String
): String? {
    return try {
        // 解析原图获取尺寸
        val origBytes = android.util.Base64.decode(originalImageBase64, android.util.Base64.NO_WRAP)
        val origBitmap = android.graphics.BitmapFactory.decodeByteArray(origBytes, 0, origBytes.size)
        val imgWidth = origBitmap?.width ?: 1024
        val imgHeight = origBitmap?.height ?: 1024

        // 创建遮罩 bitmap（黑色背景，白色涂鸦区域）
        val maskBitmap = Bitmap.createBitmap(imgWidth, imgHeight, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(maskBitmap)
        // 黑色背景 = 不修改的区域
        canvas.drawColor(android.graphics.Color.BLACK)

        // 计算缩放比例（Canvas 坐标 -> 图片坐标）
        val scaleX = imgWidth.toFloat() / canvasSize.width
        val scaleY = imgHeight.toFloat() / canvasSize.height

        val paint = Paint().apply {
            color = android.graphics.Color.WHITE  // 白色 = 需要修改的区域
            strokeWidth = 24f * scaleX  // 粗线
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        // 绘制涂鸦路径到遮罩
        paths.forEach { offsetPath ->
            if (offsetPath.size >= 2) {
                val androidPath = AndroidPath()
                androidPath.moveTo(offsetPath[0].x * scaleX, offsetPath[0].y * scaleY)
                for (i in 1 until offsetPath.size) {
                    androidPath.lineTo(offsetPath[i].x * scaleX, offsetPath[i].y * scaleY)
                }
                canvas.drawPath(androidPath, paint)
            }
        }

        // 转为 base64
        val out = ByteArrayOutputStream()
        maskBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        maskBitmap.recycle()
        origBitmap?.recycle()

        android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}

// ── 涂鸦画板组件 ─────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawingCanvasSection(
    imageBase64: String,
    paths: List<List<Offset>>,
    currentPath: List<Offset>,
    onPathUpdate: (List<Offset>) -> Unit,
    onPathComplete: () -> Unit,
    onClear: () -> Unit,
    onCanvasSizeChanged: (androidx.compose.ui.geometry.Size) -> Unit,
    onApplyMask: () -> Unit
) {
    val bitmap = remember(imageBase64) {
        val bytes = android.util.Base64.decode(imageBase64, android.util.Base64.NO_WRAP)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // 工具栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("涂鸦标记（白色区域将被修改）", style = ArtisanType.Caption.copy(color = ArtisanColors.Champagne, fontSize = 11.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 清除按钮
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ArtisanColors.Graphite)
                        .clickable { onClear() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("清除", style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary, fontSize = 11.sp))
                }
                // 应用涂鸦按钮
                if (paths.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ArtisanColors.Charcoal)
                            .border(1.dp, ArtisanColors.Champagne, RoundedCornerShape(4.dp))
                            .clickable { onApplyMask() }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("应用涂鸦", style = ArtisanType.Caption.copy(color = ArtisanColors.Champagne, fontSize = 11.sp))
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // 画布
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: 1f)
                .clip(RoundedCornerShape(8.dp))
                .background(ArtisanColors.Graphite)
        ) {
            // 底图
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            // 涂鸦层 - 使用 Path 对象绘制平滑线条
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                // 开始新路径
                                val newPath = Path().apply { moveTo(offset.x, offset.y) }
                                onPathUpdate(listOf(offset))
                                onCanvasSizeChanged(androidx.compose.ui.geometry.Size(
                                    size.width.toFloat(), size.height.toFloat()
                                ))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                // 添加历史点和当前点，使线条更平滑
                                val historicalPoints = change.historical.map { it.position }
                                val allNewPoints = historicalPoints + change.position
                                onPathUpdate(currentPath + allNewPoints)
                            },
                            onDragEnd = {
                                onPathComplete()
                            }
                        )
                    }
            ) {
                // 绘制已完成的路径
                paths.forEach { pathPoints ->
                    if (pathPoints.size >= 2) {
                        val path = Path().apply {
                            moveTo(pathPoints[0].x, pathPoints[0].y)
                            for (i in 1 until pathPoints.size) {
                                lineTo(pathPoints[i].x, pathPoints[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = Color.Red.copy(alpha = 0.6f),
                            style = Stroke(
                                width = 12.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                // 绘制当前路径
                if (currentPath.size >= 2) {
                    val path = Path().apply {
                        moveTo(currentPath[0].x, currentPath[0].y)
                        for (i in 1 until currentPath.size) {
                            lineTo(currentPath[i].x, currentPath[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = Color.Red.copy(alpha = 0.8f),
                        style = Stroke(
                            width = 12.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }

        // 提示文字
        if (paths.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "已标记 ${paths.size} 个区域，点击「应用涂鸦」发送修改请求",
                style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 10.sp)
            )
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
        val out = ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
    } catch (e: Exception) { "" }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
