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

// ── 宽高比选项（14种）─────────────────────────────────────
enum class AspectRatio(val label: String, val value: String, val widthRatio: Float, val heightRatio: Float) {
    PORTRAIT_9_16("9:16",  "9:16",  9f, 16f),
    PORTRAIT_4_5("4:5",   "4:5",   4f, 5f),
    PORTRAIT_3_4("3:4",   "3:4",   3f, 4f),
    PORTRAIT_2_3("2:3",   "2:3",   2f, 3f),
    PORTRAIT_1_2("1:2",   "1:2",   1f, 2f),
    SQUARE_1_1("1:1",     "1:1",   1f, 1f),
    LANDSCAPE_2_1("2:1",  "2:1",   2f, 1f),
    LANDSCAPE_4_3("4:3",  "4:3",   4f, 3f),
    LANDSCAPE_5_4("5:4",  "5:4",   5f, 4f),
    LANDSCAPE_3_2("3:2",  "3:2",   3f, 2f),
    LANDSCAPE_16_9("16:9","16:9",  16f, 9f),
    LANDSCAPE_21_9("21:9","21:9",  21f, 9f),
    ULTRA_TALL_1_4("1:4", "1:4",   1f, 4f),
    ULTRA_WIDE_4_1("4:1", "4:1",   4f, 1f),
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
    val referenceImages: List<String> = emptyList(),  // 风格参考图（多张）
    val status: TaskStatus = TaskStatus.QUEUED,
    val progress: Float = 0f,
    val resultImageBase64: String? = null,
    val resultImagePath: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null            // 进入"生成中"的时刻，用于展示已用时长
)

// ── 图片编辑多轮对话 ───────────────────────────────────────
data class EditTurn(
    val id: String = java.util.UUID.randomUUID().toString(),
    val userText: String,
    val inputImageBase64: String? = null,   // 仅第一轮有（用户上传的源图）
    val resultImageBase64: String? = null,  // 模型返回的结果图
    val isGenerating: Boolean = false,
    val error: String? = null
)

data class EditSession(
    val turns: List<EditTurn> = emptyList(),
    val sourceImageBase64: String? = null,  // 当前编辑的原始图（用户上传）
    val instruction: String = "",           // 输入框文字
    val isGenerating: Boolean = false
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
