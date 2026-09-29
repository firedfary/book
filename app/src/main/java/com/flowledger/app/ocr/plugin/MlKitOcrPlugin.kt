package com.flowledger.app.ocr.plugin

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 基于 Google ML Kit Text Recognition (Chinese) 的离线端侧高精度识别插件
 */
class MlKitOcrPlugin : IOcrEnginePlugin {
    override val id: String = "mlkit_chinese"
    override val displayName: String = "离线高精度 OCR 引擎 (ML Kit)"
    override val description: String = "100% 本地运行，极速毫秒级响应，零网络开销，极致保护银行流水隐私"
    override val isInstalled: Boolean = true
    override val isCloudBased: Boolean = false

    private val recognizer by lazy {
        val options = ChineseTextRecognizerOptions.Builder().build()
        TextRecognition.getClient(options)
    }

    override suspend fun install(onProgress: (Int) -> Unit): Result<Unit> {
        onProgress(100)
        return Result.success(Unit)
    }

    override suspend fun uninstall(): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun processSlice(bitmap: Bitmap, globalOffsetY: Int): OcrResult {
        return suspendCancellableCoroutine { continuation ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val lines = mutableListOf<OcrTextLine>()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val localBox = line.boundingBox ?: Rect(0, 0, 0, 0)
                            val globalBox = Rect(
                                localBox.left,
                                localBox.top + globalOffsetY,
                                localBox.right,
                                localBox.bottom + globalOffsetY
                            )
                            lines.add(
                                OcrTextLine(
                                    text = line.text.trim(),
                                    localBounds = localBox,
                                    globalBounds = globalBox
                                )
                            )
                        }
                    }
                    continuation.resume(OcrResult(lines))
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }
}
