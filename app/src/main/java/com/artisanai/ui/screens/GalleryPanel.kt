package com.artisanai.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.artisanai.data.model.GalleryImage
import com.artisanai.ui.components.*
import com.artisanai.ui.theme.ArtisanColors
import com.artisanai.ui.theme.ArtisanType
import com.artisanai.ui.theme.sp
import com.artisanai.viewmodel.MainUiState
import com.artisanai.viewmodel.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class SortOrder { NEWEST, OLDEST, LARGEST }

@Composable
fun GalleryPanel(
    uiState: MainUiState,
    viewModel: MainViewModel,
    columns: Int = 2,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sortOrder by remember { mutableStateOf(SortOrder.NEWEST) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    val sortedImages = remember(uiState.galleryImages, sortOrder) {
        when (sortOrder) {
            SortOrder.NEWEST -> uiState.galleryImages.sortedByDescending { it.createdAt }
            SortOrder.OLDEST -> uiState.galleryImages.sortedBy { it.createdAt }
            SortOrder.LARGEST -> uiState.galleryImages.sortedByDescending { it.imageSize }
        }
    }

    Column(modifier = modifier) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("图库", style = ArtisanType.TitleGold)
                Text(
                    if (multiSelectMode) "已选择 ${selectedIds.size} 张" else "${uiState.galleryImages.size} 张作品",
                    style = ArtisanType.Caption
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // 排序
                Box {
                    var showSort by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showSort = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ArtisanColors.Graphite)
                    ) {
                        Icon(Icons.Default.Sort, null, tint = ArtisanColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = showSort,
                        onDismissRequest = { showSort = false },
                        modifier = Modifier.background(ArtisanColors.Onyx)
                    ) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (order) {
                                            SortOrder.NEWEST -> "最新优先"
                                            SortOrder.OLDEST -> "最早优先"
                                            SortOrder.LARGEST -> "分辨率优先"
                                        },
                                        style = ArtisanType.Caption.copy(color = ArtisanColors.TextPrimary)
                                    )
                                },
                                onClick = {
                                    sortOrder = order
                                    showSort = false
                                },
                                leadingIcon = {
                                    if (sortOrder == order) {
                                        Icon(Icons.Default.Check, null, tint = ArtisanColors.Champagne, modifier = Modifier.size(16.dp))
                                    }
                                }
                            )
                        }
                    }
                }

                // 多选/取消
                if (uiState.galleryImages.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            multiSelectMode = !multiSelectMode
                            if (!multiSelectMode) selectedIds = emptySet()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (multiSelectMode) ArtisanColors.Champagne else ArtisanColors.Graphite)
                    ) {
                        Icon(
                            if (multiSelectMode) Icons.Default.Close else Icons.Default.Checklist,
                            null,
                            tint = if (multiSelectMode) ArtisanColors.Obsidian else ArtisanColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 多选操作栏
        AnimatedVisibility(visible = multiSelectMode && selectedIds.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlineGoldButton(
                    text = "保存到相册 (${selectedIds.size})",
                    onClick = {
                        selectedIds.forEach { id ->
                            uiState.galleryImages.find { it.id == id }?.let { viewModel.saveToAlbum(it) }
                        }
                        Toast.makeText(context, "已开始保存到相册", Toast.LENGTH_SHORT).show()
                        multiSelectMode = false
                        selectedIds = emptySet()
                    },
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp), tint = ArtisanColors.Champagne) }
                )
                OutlineGoldButton(
                    text = "删除 (${selectedIds.size})",
                    onClick = {
                        selectedIds.mapNotNull { id -> uiState.galleryImages.find { it.id == id } }
                            .forEach { viewModel.deleteGalleryImage(it) }
                        multiSelectMode = false
                        selectedIds = emptySet()
                    },
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(14.dp), tint = ArtisanColors.Error) }
                )
            }
        }

        GoldDivider()

        if (sortedImages.isEmpty()) {
            EmptyGalleryState()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sortedImages, key = { it.id }) { image ->
                    GalleryGridItem(
                        image = image,
                        isSelected = selectedIds.contains(image.id),
                        multiSelectMode = multiSelectMode,
                        onClick = {
                            if (multiSelectMode) {
                                selectedIds = if (selectedIds.contains(image.id)) {
                                    selectedIds - image.id
                                } else {
                                    selectedIds + image.id
                                }
                            } else {
                                viewModel.selectGalleryImage(image)
                            }
                        },
                        onLongClick = {
                            if (!multiSelectMode) {
                                multiSelectMode = true
                                selectedIds = setOf(image.id)
                            }
                        }
                    )
                }
            }
        }
    }

    // 图片详情弹窗
    uiState.selectedGalleryImage?.let { image ->
        ImageDetailDialog(
            image = image,
            onDismiss = { viewModel.selectGalleryImage(null) },
            onSaveToAlbum = { viewModel.saveToAlbum(image) },
            onDelete = {
                viewModel.deleteGalleryImage(image)
                viewModel.selectGalleryImage(null)
            },
            onCopyPrompt = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("prompt", image.prompt))
                Toast.makeText(context, "提示词已复制", Toast.LENGTH_SHORT).show()
            },
            onShare = {
                try {
                    val f = File(image.imagePath)
                    if (f.exists()) {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "分享图片"))
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryGridItem(
    image: GalleryImage,
    isSelected: Boolean,
    multiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(ArtisanColors.Graphite)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    ArtisanColors.Champagne,
                    RoundedCornerShape(6.dp)
                ) else Modifier
            )
    ) {
        AsyncImage(
            model = File(image.imagePath),
            contentDescription = image.prompt,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 多选勾选
        if (multiSelectMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isSelected) ArtisanColors.Champagne else ArtisanColors.Obsidian.copy(alpha = 0.6f)
                    )
                    .border(1.dp, ArtisanColors.Champagne, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check, null,
                        tint = ArtisanColors.Obsidian,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 底部信息
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, ArtisanColors.Obsidian.copy(alpha = 0.85f))
                    )
                )
        )
        Text(
            text = image.aspectRatio + "  " + image.imageSize,
            style = ArtisanType.Caption.copy(
                color = ArtisanColors.Champagne,
                fontSize = 9.sp
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
        )

        // 已保存到相册标记
        if (image.savedToAlbum) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ArtisanColors.Success.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Download, null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyGalleryState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.PhotoLibrary, null, tint = ArtisanColors.TextMuted, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("图库为空", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted))
            Text("生成的作品将在此显示", style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted))
        }
    }
}

@Composable
private fun ImageDetailDialog(
    image: GalleryImage,
    onDismiss: () -> Unit,
    onSaveToAlbum: () -> Unit,
    onDelete: () -> Unit,
    onCopyPrompt: () -> Unit,
    onShare: () -> Unit
) {
    val dateStr = remember(image.createdAt) {
        SimpleDateFormat("yyyy·MM·dd  HH:mm", Locale.getDefault()).format(Date(image.createdAt))
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(12.dp))
                .background(ArtisanColors.Onyx)
                .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(12.dp))
        ) {
            // 顶部操作栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = ArtisanColors.TextSecondary)
                }
                Text(dateStr, style = ArtisanType.Caption)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onCopyPrompt,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ArtisanColors.Graphite)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, tint = ArtisanColors.Champagne, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ArtisanColors.GoldMist)
                    ) {
                        Icon(Icons.Default.Share, null, tint = ArtisanColors.Champagne, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onSaveToAlbum,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ArtisanColors.GoldMist)
                    ) {
                        Icon(Icons.Default.Download, null, tint = ArtisanColors.Champagne, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ArtisanColors.Error.copy(alpha = 0.15f))
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, tint = ArtisanColors.Error, modifier = Modifier.size(18.dp))
                    }
                }
            }

            GoldDivider()

            // 图片（可滚动）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(ArtisanColors.Obsidian),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = File(image.imagePath),
                    contentDescription = image.prompt,
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState()),
                    contentScale = ContentScale.Fit
                )
            }

            GoldDivider()

            // 提示词信息
            Column(modifier = Modifier.padding(16.dp)) {
                Text("PROMPT", style = ArtisanType.Label)
                Spacer(Modifier.height(6.dp))
                Text(
                    image.prompt,
                    style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoChip("比例", image.aspectRatio)
                    InfoChip("分辨率", image.imageSize)
                    if (image.savedToAlbum) {
                        InfoChip("状态", "已保存")
                    }
                }
            }
        }
    }

    // 删除确认
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = ArtisanColors.Onyx,
            titleContentColor = ArtisanColors.TextPrimary,
            textContentColor = ArtisanColors.TextSecondary,
            title = { Text("删除确认", style = ArtisanType.TitleGold) },
            text = { Text("确定要删除这张图片吗？此操作不可撤销。") },
            confirmButton = {
                GoldButton(
                    "删除",
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    modifier = Modifier.width(80.dp)
                )
            },
            dismissButton = {
                OutlineGoldButton(
                    "取消",
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.width(80.dp)
                )
            }
        )
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(ArtisanColors.Graphite)
            .border(1.dp, ArtisanColors.Steel, RoundedCornerShape(3.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = ArtisanType.Caption.copy(color = ArtisanColors.TextMuted, fontSize = 10.sp))
        Text(value, style = ArtisanType.Caption.copy(color = ArtisanColors.TextSecondary, fontSize = 10.sp))
    }
}
