package com.artisanai.ui.screens

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.artisanai.data.model.GalleryImage
import com.artisanai.ui.components.*
import com.artisanai.ui.theme.ArtisanColors
import com.artisanai.ui.theme.ArtisanType
import com.artisanai.viewmodel.MainUiState
import com.artisanai.viewmodel.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GalleryPanel(
    uiState: MainUiState,
    viewModel: MainViewModel,
    columns: Int = 2,
    modifier: Modifier = Modifier
) {
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
                Text("${uiState.galleryImages.size} 张作品", style = ArtisanType.Caption)
            }
        }

        GoldDivider()

        if (uiState.galleryImages.isEmpty()) {
            EmptyGalleryState()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.galleryImages, key = { it.id }) { image ->
                    GalleryGridItem(
                        image = image,
                        onClick = { viewModel.selectGalleryImage(image) }
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
            }
        )
    }
}

@Composable
private fun GalleryGridItem(image: GalleryImage, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(ArtisanColors.Graphite)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = File(image.imagePath),
            contentDescription = image.prompt,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 底部信息渐变覆盖
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
    onDelete: () -> Unit
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

            // 图片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(ArtisanColors.Obsidian)
            ) {
                AsyncImage(
                    model = File(image.imagePath),
                    contentDescription = image.prompt,
                    modifier = Modifier.fillMaxSize(),
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
                        InfoChip("状态", "已保存到相册")
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
                GoldButton("删除", onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }, modifier = Modifier.width(80.dp))
            },
            dismissButton = {
                OutlineGoldButton("取消", onClick = { showDeleteConfirm = false }, modifier = Modifier.width(80.dp))
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

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
