package com.flowledger.app.ocr.plugin

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * OCR 识别出的单行文本项（附带局部与全局空间坐标）
 */
data class OcrTextLine(
    val text: String,
    val localBounds: Rect,
    val globalBounds: Rect
)

/**
 * OCR 切片识别结果
 */
data class OcrResult(
    val lines: List<OcrTextLine>
) {
    val fullText: String
        get() = lines.joinToString("\n") { it.text }
}

/**
 * 插件化识图引擎接口契约
 */
interface IOcrEnginePlugin {
    val id: String
    val displayName: String
    val description: String
    val isInstalled: Boolean
    val isCloudBased: Boolean

    suspend fun install(onProgress: (Int) -> Unit): Result<Unit>
    suspend fun uninstall(): Result<Unit>
    suspend fun processSlice(bitmap: Bitmap, globalOffsetY: Int): OcrResult
}
