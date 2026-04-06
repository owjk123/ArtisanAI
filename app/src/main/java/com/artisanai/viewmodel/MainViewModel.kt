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
    val polishedPrompt: String = "",           // 直接生图：AI润色结果（独立于反推）
    val reversedPrompt: String = "",           // 反推生图：图像分析结果（独立于润色）
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

    // 导航信号：加入队列后通知UI切换到任务Tab
    val pendingTaskNavigation: Boolean = false,

    // 导航
    val currentTab: AppTab = AppTab.GENERATE,

    // 图片编辑会话
    val editSession: EditSession = EditSession()
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
    fun clearPolishedPrompt() = _uiState.update { it.copy(polishedPrompt = "") }
    fun clearReversedPrompt() = _uiState.update { it.copy(reversedPrompt = "") }
    fun dismissTaskNavigation() = _uiState.update { it.copy(pendingTaskNavigation = false) }
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

    // ── Agent：润色提示词（直接生图模式专用）─────────────────
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
                _uiState.update { it.copy(polishedPrompt = polished, isPolishing = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isPolishing = false) }
                showToast("润色失败: ${e.message}")
            }
        }
    }

    // ── Agent：反推参考图提示词（反推生图模式专用）──────────
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
                _uiState.update { it.copy(reversedPrompt = reversed, isReversingPrompt = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isReversingPrompt = false) }
                showToast("反推失败: ${e.message}")
            }
        }
    }

    /** 内部使用：加入队列时自动执行反推，结果写入 reversedPrompt */
    private suspend fun runReversePrompt(imageBase64: String): Result<String> {
        _uiState.update { it.copy(isReversingPrompt = true) }
        val result = agentRepo.reversePromptFromImage(imageBase64)
        result.onSuccess { reversed ->
            _uiState.update { it.copy(reversedPrompt = reversed, isReversingPrompt = false) }
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

        // ── 反推模式：先执行图像分析，再加队列 ─────────────
        if (state.isReverseMode && state.reverseImageBase64 != null) {
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

        // ── 直接生图模式：使用 userPrompt + polishedPrompt ──
        if (userPrompt.isBlank()) {
            showToast("请输入提示词")
            return
        }
        val finalPrompt = buildFinalPrompt(userPrompt, state.polishedPrompt)
        doEnqueueTasks(state, finalPrompt, state.polishedPrompt)
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
        // 通知 UI 切换到任务Tab
        _uiState.update { it.copy(pendingTaskNavigation = true) }
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

    // ── 图片编辑（多轮对话）────────────────────────────────
    fun setEditSourceImage(base64: String?) {
        _uiState.update { it.copy(editSession = it.editSession.copy(sourceImageBase64 = base64, turns = emptyList())) }
    }

    fun updateEditInstruction(text: String) {
        _uiState.update { it.copy(editSession = it.editSession.copy(instruction = text)) }
    }

    fun clearEditSession() {
        _uiState.update { it.copy(editSession = EditSession()) }
    }

    fun sendEditInstruction() {
        val session = _uiState.value.editSession
        val instruction = session.instruction.trim()
        if (instruction.isBlank()) { showToast("请输入编辑指令"); return }
        if (session.turns.isEmpty() && session.sourceImageBase64 == null) {
            showToast("请先上传要编辑的图片"); return
        }
        if (session.isGenerating) return

        val newTurn = EditTurn(
            userText = instruction,
            inputImageBase64 = if (session.turns.isEmpty()) session.sourceImageBase64 else null
        )
        _uiState.update { it.copy(
            editSession = it.editSession.copy(
                turns = it.editSession.turns + newTurn.copy(isGenerating = true),
                instruction = "",
                isGenerating = true
            )
        )}

        val turnId = newTurn.id
        val sourceImage = session.sourceImageBase64

        viewModelScope.launch {
            try {
                val completedTurns = _uiState.value.editSession.turns.dropLast(1)
                    .filter { it.resultImageBase64 != null }
                val state = _uiState.value

                val result = imageGenRepo.multiTurnEditImage(
                    completedTurns = completedTurns,
                    newInstruction = instruction,
                    sourceImageBase64 = sourceImage,
                    aspectRatio = state.selectedAspectRatio,
                    imageSize = state.selectedImageSize,
                    thinkingLevel = state.selectedThinkingLevel
                )

                val base64 = result.getOrNull()
                if (base64 != null) {
                    _uiState.update { st ->
                        st.copy(editSession = st.editSession.copy(
                            turns = st.editSession.turns.map { t ->
                                if (t.id == turnId) t.copy(resultImageBase64 = base64, isGenerating = false) else t
                            },
                            isGenerating = false
                        ))
                    }
                    // 保存到图库（独立协程，不影响编辑流程）
                    runCatching {
                        galleryRepo.saveGeneratedImage(
                            id = turnId,
                            base64Data = base64,
                            prompt = instruction,
                            aspectRatio = state.selectedAspectRatio.value,
                            imageSize = state.selectedImageSize.value
                        )
                    }
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "未知错误"
                    _uiState.update { st ->
                        st.copy(editSession = st.editSession.copy(
                            turns = st.editSession.turns.map { t ->
                                if (t.id == turnId) t.copy(error = errMsg, isGenerating = false) else t
                            },
                            isGenerating = false
                        ))
                    }
                    showToast("编辑失败: $errMsg")
                }
            } catch (e: Exception) {
                _uiState.update { st ->
                    st.copy(editSession = st.editSession.copy(
                        turns = st.editSession.turns.map { t ->
                            if (t.id == turnId) t.copy(error = e.message ?: "出错", isGenerating = false) else t
                        },
                        isGenerating = false
                    ))
                }
                showToast("编辑出错: ${e.message}")
            }
        }
    }

    fun saveEditResultToAlbum(turn: EditTurn) {
        val base64 = turn.resultImageBase64 ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingImage = true) }
            // 通过内部存储路径保存（saveGeneratedImage已写入文件）
            val internalPath = "${galleryRepo.getInternalDir()?.absolutePath}/${turn.id}.png"
            val result = galleryRepo.saveToAlbum(turn.id, internalPath)
            _uiState.update { it.copy(isSavingImage = false) }
            result.onSuccess { showToast("图片已保存到相册") }
                .onFailure { showToast("保存失败: ${it.message}") }
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
