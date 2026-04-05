package com.artisanai.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artisanai.data.model.*
import com.artisanai.repository.AgentRepository
import com.artisanai.repository.GalleryRepository
import com.artisanai.repository.ImageGenRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.util.UUID

// ── UI 状态 ────────────────────────────────────────────────
data class MainUiState(
    // 输入区
    val userPrompt: String = "",
    val referencePrompt: String = "",          // 反推/润色后的独立提示词
    val selectedAspectRatio: AspectRatio = AspectRatio.PORTRAIT_9_16,
    val selectedImageSize: ImageSize = ImageSize.SIZE_2K,
    val selectedThinkingLevel: ThinkingLevel = ThinkingLevel.MINIMAL,
    val useGrounding: Boolean = false,
    val referenceImages: List<String> = emptyList(), // 最多5张风格参考图
    val reverseImageBase64: String? = null,          // 反推分析图（提取提示词用）

    // 模式
    val isReverseMode: Boolean = false,

    // Agent状态
    val isPolishing: Boolean = false,
    val isReversingPrompt: Boolean = false,
    val isSavingImage: Boolean = false,

    // 生成数量
    val selectedCount: Int = 1,

    // 任务队列
    val tasks: List<GenerateTask> = emptyList(),

    // 图库
    val galleryImages: List<com.artisanai.data.model.GalleryImage> = emptyList(),

    // 弹窗
    val selectedGalleryImage: com.artisanai.data.model.GalleryImage? = null,
    val toastMessage: String? = null,

    // 导航
    val currentTab: AppTab = AppTab.GENERATE
)

enum class AppTab { GENERATE, GALLERY }

class MainViewModel(
    private val imageGenRepo: ImageGenRepository,
    private val agentRepo: AgentRepository,
    private val galleryRepo: GalleryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // 并发信号量：最多同时10个生图任务
    private val semaphore = kotlinx.coroutines.sync.Semaphore(10)

    init {
        // 监听图库变化
        viewModelScope.launch {
            galleryRepo.getAllImages().collect { images ->
                _uiState.update { it.copy(galleryImages = images) }
            }
        }
    }

    // ── 输入更新 ───────────────────────────────────────────
    fun updateUserPrompt(text: String) = _uiState.update { it.copy(userPrompt = text) }
    fun updateReferencePrompt(text: String) = _uiState.update { it.copy(referencePrompt = text) }
    fun selectAspectRatio(ratio: AspectRatio) = _uiState.update { it.copy(selectedAspectRatio = ratio) }
    fun selectImageSize(size: ImageSize) = _uiState.update { it.copy(selectedImageSize = size) }
    fun selectThinkingLevel(level: ThinkingLevel) = _uiState.update { it.copy(selectedThinkingLevel = level) }
    fun toggleGrounding() = _uiState.update { it.copy(useGrounding = !it.useGrounding) }
    fun setCurrentTab(tab: AppTab) = _uiState.update { it.copy(currentTab = tab) }
    fun dismissToast() = _uiState.update { it.copy(toastMessage = null) }

    // 参考图（多张，最多5张）
    fun addReferenceImage(base64: String) {
        _uiState.update { state ->
            if (state.referenceImages.size >= 5) state
            else state.copy(referenceImages = state.referenceImages + base64)
        }
    }

    fun removeReferenceImage(index: Int) {
        _uiState.update { state ->
            state.copy(referenceImages = state.referenceImages.toMutableList().also { it.removeAt(index) })
        }
    }

    fun clearReferenceImages() {
        _uiState.update { it.copy(referenceImages = emptyList()) }
    }

    fun setReverseImage(base64: String?) {
        _uiState.update { it.copy(reverseImageBase64 = base64) }
    }

    fun clearReverseImage() {
        _uiState.update { it.copy(reverseImageBase64 = null) }
    }

    fun selectCount(count: Int) {
        _uiState.update { it.copy(selectedCount = count) }
    }

    fun setReverseMode(enabled: Boolean) {
        _uiState.update { it.copy(isReverseMode = enabled) }
    }

    fun selectGalleryImage(image: com.artisanai.data.model.GalleryImage?) {
        _uiState.update { it.copy(selectedGalleryImage = image) }
    }

    // ── Agent：润色提示词 ──────────────────────────────────
    fun polishPrompt() {
        val userPrompt = _uiState.value.userPrompt
        if (userPrompt.isBlank()) {
            showToast("请先输入描述内容")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isPolishing = true) }
            val result = agentRepo.polishPrompt(userPrompt)
            result.onSuccess { polished ->
                _uiState.update { it.copy(
                    referencePrompt = polished,
                    isPolishing = false
                )}
            }.onFailure { e ->
                _uiState.update { it.copy(isPolishing = false) }
                showToast("润色失败: ${e.message}")
            }
        }
    }

    // ── Agent：反推参考图提示词（手动触发，返回 Result 供内部复用）─
    fun reversePromptFromImage() {
        val imageBase64 = _uiState.value.reverseImageBase64
        if (imageBase64 == null) {
            showToast("请先上传反推参考图")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isReversingPrompt = true) }
            val result = agentRepo.reversePromptFromImage(imageBase64)
            result.onSuccess { reversed ->
                _uiState.update { it.copy(referencePrompt = reversed, isReversingPrompt = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isReversingPrompt = false) }
                showToast("反推失败: ${e.message}")
            }
        }
    }

    /** 内部使用：等待反推完成并返回提示词 */
    private suspend fun runReversePrompt(imageBase64: String): Result<String> {
        _uiState.update { it.copy(isReversingPrompt = true) }
        val result = agentRepo.reversePromptFromImage(imageBase64)
        result.onSuccess { reversed ->
            _uiState.update { it.copy(referencePrompt = reversed, isReversingPrompt = false) }
        }.onFailure {
            _uiState.update { it.copy(isReversingPrompt = false) }
        }
        return result
    }

    // ── 添加生成任务 ───────────────────────────────────────
    fun addGenerateTask() {
        val state = _uiState.value
        val userPrompt = state.userPrompt.trim()
        val activeCount = state.tasks.count { it.status == TaskStatus.QUEUED || it.status == TaskStatus.PROCESSING }
        if (activeCount + state.selectedCount > 10) {
            showToast("任务队列已满（最多10个并发）")
            return
        }

        // 反推模式且有反推图时，先自动执行反推再加队列
        if (state.isReverseMode && state.reverseImageBase64 != null) {
            if (userPrompt.isBlank() && state.referencePrompt.isBlank()) {
                // prompt 为空也允许：反推结果会作为 prompt
            }
            viewModelScope.launch {
                val reverseResult = runReversePrompt(state.reverseImageBase64)
                reverseResult.onSuccess { reversed ->
                    val updatedState = _uiState.value
                    val finalPrompt = buildFinalPrompt(updatedState.userPrompt.trim(), reversed)
                    if (finalPrompt.isBlank()) {
                        showToast("反推结果为空，请重试")
                        return@onSuccess
                    }
                    doEnqueueTasks(updatedState, finalPrompt, reversed)
                }.onFailure { e ->
                    showToast("反推失败: ${e.message}")
                }
            }
            return
        }

        // 直接生图模式
        if (userPrompt.isBlank()) {
            showToast("请输入提示词")
            return
        }
        val finalPrompt = buildFinalPrompt(userPrompt, state.referencePrompt)
        doEnqueueTasks(state, finalPrompt, state.referencePrompt)
    }

    private fun doEnqueueTasks(state: MainUiState, finalPrompt: String, refPrompt: String) {
        val refImage = state.referenceImages.firstOrNull()
        repeat(state.selectedCount) {
            val task = GenerateTask(
                id = UUID.randomUUID().toString(),
                prompt = finalPrompt,
                referencePrompt = refPrompt,
                aspectRatio = state.selectedAspectRatio,
                imageSize = state.selectedImageSize,
                thinkingLevel = state.selectedThinkingLevel,
                useGrounding = state.useGrounding,
                referenceImageBase64 = refImage,
                status = TaskStatus.QUEUED
            )
            _uiState.update { it.copy(tasks = it.tasks + task) }
            launchTaskExecution(task)
        }
        if (state.selectedCount > 1) showToast("已加入 ${state.selectedCount} 个任务")
    }

    private fun buildFinalPrompt(userPrompt: String, referencePrompt: String): String {
        return if (referencePrompt.isBlank()) {
            userPrompt
        } else {
            // referencePrompt是英文提示词，userPrompt追加在后
            "$referencePrompt, $userPrompt"
        }
    }

    private fun launchTaskExecution(task: GenerateTask) {
        viewModelScope.launch {
            semaphore.acquire()
            try {
                // 更新状态为处理中
                updateTaskStatus(task.id, TaskStatus.PROCESSING, progress = 0.1f)

                val result = if (task.referenceImageBase64 != null) {
                    imageGenRepo.editImage(
                        prompt = task.prompt,
                        imageBase64 = task.referenceImageBase64,
                        aspectRatio = task.aspectRatio,
                        imageSize = task.imageSize,
                        thinkingLevel = task.thinkingLevel
                    )
                } else {
                    imageGenRepo.generateImage(
                        prompt = task.prompt,
                        aspectRatio = task.aspectRatio,
                        imageSize = task.imageSize,
                        thinkingLevel = task.thinkingLevel,
                        useGrounding = task.useGrounding
                    )
                }

                updateTaskStatus(task.id, TaskStatus.PROCESSING, progress = 0.8f)

                result.onSuccess { base64 ->
                    // 保存到内部图库
                    val saveResult = galleryRepo.saveGeneratedImage(
                        id = task.id,
                        base64Data = base64,
                        prompt = task.prompt,
                        aspectRatio = task.aspectRatio.value,
                        imageSize = task.imageSize.value
                    )
                    saveResult.onSuccess { path ->
                        updateTaskFull(task.id, TaskStatus.SUCCESS, 1f, base64, path)
                    }.onFailure {
                        updateTaskFull(task.id, TaskStatus.SUCCESS, 1f, base64, null)
                    }
                }.onFailure { e ->
                    updateTaskStatus(task.id, TaskStatus.FAILED, errorMessage = e.message)
                }
            } finally {
                semaphore.release()
            }
        }
    }

    fun retryTask(taskId: String) {
        val task = _uiState.value.tasks.find { it.id == taskId } ?: return
        val newTask = task.copy(
            id = UUID.randomUUID().toString(),
            status = TaskStatus.QUEUED,
            progress = 0f,
            resultImageBase64 = null,
            resultImagePath = null,
            errorMessage = null
        )
        _uiState.update { state ->
            state.copy(tasks = state.tasks.map {
                if (it.id == taskId) newTask else it
            })
        }
        launchTaskExecution(newTask)
    }

    fun removeTask(taskId: String) {
        _uiState.update { it.copy(tasks = it.tasks.filter { t -> t.id != taskId }) }
    }

    fun clearCompletedTasks() {
        _uiState.update { it.copy(
            tasks = it.tasks.filter { t ->
                t.status == TaskStatus.QUEUED || t.status == TaskStatus.PROCESSING
            }
        )}
    }

    // ── 图库操作 ───────────────────────────────────────────
    fun saveToAlbum(image: com.artisanai.data.model.GalleryImage) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingImage = true) }
            val result = galleryRepo.saveToAlbum(image.id, image.imagePath)
            _uiState.update { it.copy(isSavingImage = false) }
            result.onSuccess { showToast("图片已保存到相册") }
                .onFailure { showToast("保存失败: ${it.message}") }
        }
    }

    fun deleteGalleryImage(image: com.artisanai.data.model.GalleryImage) {
        viewModelScope.launch {
            galleryRepo.deleteImage(image)
            if (_uiState.value.selectedGalleryImage?.id == image.id) {
                _uiState.update { it.copy(selectedGalleryImage = null) }
            }
            showToast("已删除")
        }
    }

    // ── 内部工具 ───────────────────────────────────────────
    private fun updateTaskStatus(
        id: String,
        status: TaskStatus,
        progress: Float = 0f,
        errorMessage: String? = null
    ) {
        _uiState.update { state ->
            state.copy(tasks = state.tasks.map {
                if (it.id == id) it.copy(status = status, progress = progress, errorMessage = errorMessage)
                else it
            })
        }
    }

    private fun updateTaskFull(
        id: String, status: TaskStatus, progress: Float,
        base64: String?, path: String?
    ) {
        _uiState.update { state ->
            state.copy(tasks = state.tasks.map {
                if (it.id == id) it.copy(
                    status = status, progress = progress,
                    resultImageBase64 = base64, resultImagePath = path
                ) else it
            })
        }
    }

    private fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    // ── ViewModelFactory ───────────────────────────────────
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            // 使用ArtisanApp的单例数据库，避免多实例冲突
            val db = com.artisanai.ArtisanApp.getDatabase()

            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                imageGenRepo = ImageGenRepository(appContext),
                agentRepo = AgentRepository(appContext),
                galleryRepo = GalleryRepository(appContext, db.galleryDao())
            ) as T
        }
    }
}
