package com.artisanai.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * apiyi 各线路健康检查：通过 HTTP 协议端口 16888 探测存活与延迟。
 * 并发 ping 所有 preset，返回延迟/状态，便于 UI 展示及用户选择最优线路。
 */
object EndpointHealth {

    data class Result(
        val preset: ApiKeyManager.EndpointPreset,
        val reachable: Boolean,
        val latencyMs: Long,       // 成功时的往返耗时
        val message: String        // 失败原因或 "OK"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /** 并发 ping 所有 preset，3 秒超时 */
    suspend fun checkAll(): List<Result> = withContext(Dispatchers.IO) {
        coroutineScope {
            ApiKeyManager.PRESETS.map { p -> async { checkOne(p) } }.awaitAll()
        }
    }

    private fun checkOne(p: ApiKeyManager.EndpointPreset): Result {
        val host = try { URI(p.url).host } catch (e: Exception) {
            return Result(p, false, -1, "地址解析失败")
        }
        val probeUrl = "http://$host:${ApiKeyManager.STATUS_PORT}/"
        val start = System.currentTimeMillis()
        return try {
            val resp = client.newCall(
                Request.Builder().url(probeUrl).get().build()
            ).execute()
            val elapsed = System.currentTimeMillis() - start
            resp.close()
            // 只要返回任意 HTTP 响应（包括 404）就视为可达，因为端口开放说明节点在线
            Result(p, true, elapsed, "OK (${resp.code})")
        } catch (e: java.net.SocketTimeoutException) {
            Result(p, false, -1, "超时")
        } catch (e: java.io.IOException) {
            Log.w("EndpointHealth", "probe $probeUrl failed: ${e.message}")
            Result(p, false, -1, "不可达")
        } catch (e: Exception) {
            Result(p, false, -1, e.message ?: "未知错误")
        }
    }
}
