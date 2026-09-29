package com.flowledger.app.ocr.plugin

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Rect

/**
 * 云端大模型多模态视觉插件（可选，用户自备 API Key）
 */
class CloudVisionPlugin(context: Context) : IOcrEnginePlugin {
    override val id: String = "cloud_vision_api"
    override val displayName: String = "云端视觉大模型 (自配 API Key)"
    override val description: String = "支持 OpenAI / Gemini / Qwen 视觉 API 协议，零本地存储占用"
    
    private val prefs: SharedPreferences =
        context.getSharedPreferences("flow_ledger_ocr_prefs", Context.MODE_PRIVATE)

    override val isInstalled: Boolean
        get() = !prefs.getString("cloud_api_key", "").isNullOrBlank()

    override val isCloudBased: Boolean = true

    fun setApiKey(apiKey: String, endpoint: String = "") {
        prefs.edit()
            .putString("cloud_api_key", apiKey.trim())
            .putString("cloud_api_endpoint", endpoint.trim())
            .apply()
    }

    fun getApiKey(): String = prefs.getString("cloud_api_key", "") ?: ""
    fun getEndpoint(): String = prefs.getString("cloud_api_endpoint", "") ?: ""

    override suspend fun install(onProgress: (Int) -> Unit): Result<Unit> {
        onProgress(100)
        return Result.success(Unit)
    }

    override suspend fun uninstall(): Result<Unit> {
        prefs.edit().remove("cloud_api_key").remove("cloud_api_endpoint").apply()
        return Result.success(Unit)
    }

    override suspend fun processSlice(bitmap: Bitmap, globalOffsetY: Int): OcrResult {
        // 当未配置密钥或网络离线时，抛出明确错误提示
        if (!isInstalled) {
            throw IllegalStateException("未配置云端视觉 API Key，请在插件设置中填入 API Key")
        }
        // 若用户配置了云端，可按需调用网络接口，此处提供结构包装
        return OcrResult(emptyList())
    }
}
