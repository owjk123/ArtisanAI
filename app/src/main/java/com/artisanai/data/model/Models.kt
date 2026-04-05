package com.artisanai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ── 生成任务状态 ───────────────────────────────────────────
enum class TaskStatus {
    QUEUED,       // 等待中
    PROCESSING,   // 生成中
    SUCCESS,      // 完成
    FAILED        // 失败
}

// ── 宽高比选项 ─────────────────────────────────────────────
enum class AspectRatio(val label: String, val value: String, val widthRatio: Float, val heightRatio: Float) {
    PORTRAIT_9_16("9:16", "9:16", 9f, 16f),
    PORTRAIT_3_4("3:4", "3:4", 3f, 4f),
    PORTRAIT_2_3("2:3", "2:3", 2f, 3f),
    SQUARE_1_1("1:1", "1:1", 1f, 1f),
    LANDSCAPE_4_3("4:3", "4:3", 4f, 3f),
    LANDSCAPE_3_2("3:2", "3:2", 3f, 2f),
    LANDSCAPE_16_9("16:9", "16:9", 16f, 9f),
    LANDSCAPE_21_9("21:9", "21:9", 21f, 9f),
    ULTRA_TALL_1_4("1:4", "1:4", 1f, 4f),
    ULTRA_WIDE_4_1("4:1", "4:1", 4f, 1f),
}

// ── 分辨率选项 ─────────────────────────────────────────────
enum class ImageSize(val label: String, val value: String) {
    SIZE_512("512px", "512"),
    SIZE_1K("1K", "1K"),
    SIZE_2K("2K", "2K"),
    SIZE_4K("4K", "4K"),
}

// ── 思维模式 ───────────────────────────────────────────────
enum class ThinkingLevel(val label: String, val value: String) {
    NONE("关闭", "none"),
    MINIMAL("快速", "minimal"),
    HIGH("精准", "high"),
}

// ── 生成任务（内存中追踪） ──────────────────────────────────
data class GenerateTask(
    val id: String,
    val prompt: String,
    val referencePrompt: String = "",   // 反推的参考图提示词
    val aspectRatio: AspectRatio = AspectRatio.PORTRAIT_9_16,
    val imageSize: ImageSize = ImageSize.SIZE_2K,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.MINIMAL,
    val useGrounding: Boolean = false,
    val referenceImageBase64: String? = null,  // 用于图生图
    val status: TaskStatus = TaskStatus.QUEUED,
    val progress: Float = 0f,
    val resultImageBase64: String? = null,
    val resultImagePath: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// ── Room 实体（本地图库持久化） ────────────────────────────
@Entity(tableName = "gallery_images")
data class GalleryImage(
    @PrimaryKey
    val id: String,
    val prompt: String,
    val imagePath: String,          // 内部存储路径
    val aspectRatio: String,
    val imageSize: String,
    val createdAt: Long,
    val savedToAlbum: Boolean = false
)
