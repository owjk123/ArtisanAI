package com.artisanai.repository

import android.content.Context
import android.util.Log
import com.artisanai.util.ApiKeyManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.artisanai.data.model.AspectRatio
import com.artisanai.data.model.EditTurn
import com.artisanai.data.model.ImageSize
import com.artisanai.data.model.ThinkingLevel

class ImageGenRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        // 整条调用（连接+写+读+响应处理）硬上限，防止单线路无限挂起占着进度条
        .callTimeout(180, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(3, 90, TimeUnit.SECONDS)) // 90s idle，减少stale连接
        .build()

    private val gson = Gson()
    private val apiKey get() = ApiKeyManager.loadApiKey(context)

    /** 当前用户选中的首选 base url（非预设则作为唯一候选）。 */
    private fun primaryBaseUrl(): String = ApiKeyManager.loadBaseUrl(context).trimEnd('/')

    /** 构造 fallback 顺序：首选 → 其余预设。非预设（自定义地址）只用首选，不乱跳。 */
    private fun candidateBaseUrls(): List<String> {
        val primary = primaryBaseUrl()
        val presetUrls = ApiKeyManager.PRESETS.map { it.url.trimEnd('/') }
        return if (primary in presetUrls) {
            listOf(primary) + presetUrls.filter { it != primary }
        } else {
            listOf(primary)
        }
    }

    private fun toEndpointUrl(base: String): String =
        "$base/v1beta/models/gemini-3.1-flash-image:generateContent"

    suspend fun generateImage(
        prompt: String,
        referenceImages: List<String> = emptyList(),   // 风格参考图（多张）
        aspectRatio: AspectRatio = AspectRatio.PORTRAIT_9_16,
        imageSize: ImageSize = ImageSize.SIZE_2K,
        thinkingLevel: ThinkingLevel = ThinkingLevel.MINIMAL,
        useGrounding: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("请先在设置中填写 API Key"))
        try {
            executeRequest(buildBody(prompt, aspectRatio, imageSize, thinkingLevel, useGrounding, referenceImages))
        } catch (e: CancellationException) {
            throw e   // 取消/超时必须向上传递，不能吞掉
        } catch (e: Exception) {
            Log.e("ImageGenRepo", "generateImage failed", e)
            Result.failure(e)
        }
    }

    /**
     * 图片链式编辑：每轮带上最新结果图（或源图）+ 编辑指令。
     * 多轮时在提示词中附带历史上下文，让模型理解之前做了什么编辑。
     * 支持涂鸦遮罩：maskImageBase64 为红色涂鸦标记的遮罩图。
     */
    suspend fun multiTurnEditImage(
        completedTurns: List<EditTurn>,
        newInstruction: String,
        sourceImageBase64: String?,
        aspectRatio: AspectRatio = AspectRatio.SQUARE_1_1,
        imageSize: ImageSize = ImageSize.SIZE_2K,
        thinkingLevel: ThinkingLevel = ThinkingLevel.MINIMAL,
        maskImageBase64: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("请先在设置中填写 API Key"))
        try {
            // 最新结果图作为编辑基础；没有历史则用源图
            val baseImage = completedTurns.lastOrNull { it.resultImageBase64 != null }
                ?.resultImageBase64 ?: sourceImageBase64

            // 构建编辑提示词
            val editPrompt = buildString {
                append("Edit the following image. ")
                if (maskImageBase64 != null) {
                    append("The red-marked areas in the mask image indicate regions that need to be modified. ")
                    append("Focus your edits on these marked regions. ")
                }
                if (completedTurns.isNotEmpty()) {
                    append("Previous edits: ")
                    completedTurns.forEachIndexed { i, turn ->
                        append("${i + 1}) ${turn.userText}. ")
                    }
                    append("Continue editing based on the history above. ")
                }
                append("Instruction: $newInstruction")
            }

            val images = mutableListOf<String>()
            baseImage?.let { images.add(it) }
            maskImageBase64?.let { images.add(it) }

            executeRequest(buildBody(editPrompt, aspectRatio, imageSize, thinkingLevel, false, images))
        } catch (e: CancellationException) {
            throw e   // 取消/超时必须向上传递，不能吞掉
        } catch (e: Exception) {
            Log.e("ImageGenRepo", "multiTurnEditImage failed", e)
            Result.failure(e)
        }
    }

    private fun buildBody(
        prompt: String, aspectRatio: AspectRatio, imageSize: ImageSize,
        thinkingLevel: ThinkingLevel, useGrounding: Boolean,
        referenceImages: List<String> = emptyList()
    ): String {
        val body = JsonObject()
        val parts = com.google.gson.JsonArray().apply {
            add(JsonObject().apply { addProperty("text", prompt) })
            // 全部参考图（风格参考或编辑基础图）依次附入
            referenceImages.forEach { img ->
                add(JsonObject().apply {
                    add("inlineData", JsonObject().apply {
                        addProperty("mimeType", "image/jpeg")
                        addProperty("data", img)
                    })
                })
            }
        }
        body.add("contents", com.google.gson.JsonArray().apply {
            add(JsonObject().apply { add("parts", parts) })
        })
        body.add("generationConfig", JsonObject().apply {
            add("responseModalities", com.google.gson.JsonArray().apply { add("IMAGE") })
            add("imageConfig", JsonObject().apply {
                addProperty("aspectRatio", aspectRatio.value)
                addProperty("imageSize", imageSize.value)
            })
            if (thinkingLevel != ThinkingLevel.NONE) {
                add("thinkingConfig", JsonObject().apply {
                    addProperty("thinkingLevel", thinkingLevel.value)
                })
            }
        })
        if (useGrounding) {
            body.add("tools", com.google.gson.JsonArray().apply {
                add(JsonObject().apply { add("google_search", JsonObject()) })
            })
        }
        return gson.toJson(body)
    }

    /**
     * 顶层请求入口：依次尝试 候选线路。
     * 只在"连接/网络/网关类错误"时 fallback，4xx 业务错误（401/403/400等）立刻返回不再切换。
     */
    private suspend fun executeRequest(bodyJson: String): Result<String> {
        val candidates = candidateBaseUrls()
        var lastErr: Exception? = null
        for ((idx, base) in candidates.withIndex()) {
            try {
                val r = executeRequestOn(base, bodyJson)
                if (r.isSuccess) {
                    if (idx > 0) Log.i("ImageGenRepo", "Fallback succeeded on $base (idx=$idx)")
                    return r
                }
                val err = r.exceptionOrNull()
                // 业务错误（Key无效、安全审查、参数错误）不换线路
                if (err is BusinessException) return r
                lastErr = err as? Exception ?: Exception(err?.message ?: "未知错误")
                Log.w("ImageGenRepo", "Endpoint $base failed, try next. ${lastErr.message}")
            } catch (e: CancellationException) {
                throw e   // 取消/超时立即中止，不再尝试其它线路
            } catch (e: Exception) {
                lastErr = e
                Log.w("ImageGenRepo", "Endpoint $base threw, try next.", e)
            }
        }
        return Result.failure(lastErr ?: Exception("所有线路均不可用"))
    }

    /** 业务错误——不应触发 fallback */
    private class BusinessException(msg: String) : Exception(msg)

    private suspend fun executeRequestOn(base: String, bodyJson: String): Result<String> {
        val response = try {
            client.newCall(
                Request.Builder()
                    .url(toEndpointUrl(base))
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()
            ).await()
        } catch (e: IOException) {
            // 连接失败 / 超时 / socket abort / callTimeout → 允许 fallback
            return Result.failure(e)
        }

        val responseBody = response.body?.string()
        if (!response.isSuccessful || responseBody == null) {
            return when (response.code) {
                401, 403 -> Result.failure(BusinessException("API Key 无效或无权限，请在设置中重新填写"))
                400      -> Result.failure(BusinessException("请求参数错误 (400)"))
                429      -> Result.failure(Exception("请求过于频繁，请稍后再试"))       // 可 fallback
                in 500..599 -> Result.failure(Exception("服务器繁忙 (${response.code})")) // 可 fallback
                else -> Result.failure(Exception("请求失败 (${response.code})"))
            }
        }
        // 校验是否为合法 JSON（防止后台恢复后 stale 连接返回 HTML/网关错误）
        if (!responseBody.trimStart().startsWith("{")) {
            Log.e("ImageGenRepo", "Non-JSON response from $base (first 300): ${responseBody.take(300)}")
            return Result.failure(Exception("服务器返回异常响应"))
        }
        return try {
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val candidates = json.getAsJsonArray("candidates")
                ?: return Result.failure(BusinessException("API 返回空结果"))
            if (candidates.size() == 0) return Result.failure(BusinessException("可能触发了安全过滤，请修改提示词"))
            val content = candidates[0].asJsonObject.getAsJsonObject("content")
                ?: return Result.failure(BusinessException("API 返回结构异常（无 content）"))
            val parts = content.getAsJsonArray("parts")
                ?: return Result.failure(BusinessException("API 返回结构异常（无 parts）"))
            for (part in parts) {
                val p = part.asJsonObject
                if (p.has("inlineData")) {
                    return Result.success(p.getAsJsonObject("inlineData").get("data").asString)
                }
            }
            Result.failure(BusinessException("响应中未找到图像数据"))
        } catch (e: Exception) {
            Log.e("ImageGenRepo", "Parse error on $base, body (first 300): ${responseBody.take(300)}", e)
            Result.failure(Exception("解析响应失败: ${e.message}"))
        }
    }

    /**
     * 把 OkHttp 异步调用桥接为可取消的挂起函数。
     * 协程被取消（用户取消任务 / withTimeout 触发）时，会真正 cancel 掉底层 HTTP 调用，
     * 而不是让阻塞的 execute() 把线程一直占住——这是"任务卡死"能被及时中断的关键。
     */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isCancelled) return
                cont.resumeWithException(e)
            }
        })
        cont.invokeOnCancellation {
            try { cancel() } catch (_: Throwable) {}
        }
    }
}
