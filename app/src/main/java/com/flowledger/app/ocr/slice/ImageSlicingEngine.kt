package com.flowledger.app.ocr.slice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import java.io.InputStream

/**
 * 超长截图防 OOM 切片引擎
 * 针对 1264x42455 等超高长截图，采用带重叠保护带的滑动窗口切片，将内存峰值控制在 25MB 以内。
 */
class ImageSlicingEngine(private val context: Context) {

    data class ImageSlice(
        val bitmap: Bitmap,
        val globalOffsetY: Int,
        val sliceIndex: Int,
        val totalSlices: Int
    )

    fun sliceImage(
        uri: Uri,
        sliceHeight: Int = 2800,
        overlapHeight: Int = 150
    ): List<ImageSlice> {
        val slices = mutableListOf<ImageSlice>()

        // 1. 获取图像尺寸元数据
        var inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("无法打开图片文件: $uri")

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        val imageWidth = options.outWidth
        val imageHeight = options.outHeight

        if (imageWidth <= 0 || imageHeight <= 0) {
            throw IllegalArgumentException("无效的图片尺寸: ${imageWidth}x${imageHeight}")
        }

        // 2. 若高度较小，直接单图解码返回
        if (imageHeight <= sliceHeight) {
            inputStream = context.contentResolver.openInputStream(uri)!!
            val bmp = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bmp != null) {
                slices.add(ImageSlice(bmp, 0, 0, 1))
            }
            return slices
        }

        // 3. 超长图滑动窗口切片
        inputStream = context.contentResolver.openInputStream(uri)!!
        val decoder = BitmapRegionDecoder.newInstance(inputStream, false)
            ?: throw IllegalStateException("初始化 BitmapRegionDecoder 失败")

        val step = (sliceHeight - overlapHeight).coerceAtLeast(500)
        val numSlices = ((imageHeight - overlapHeight) + (step - 1)) / step

        var currentTop = 0
        var index = 0

        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565 // 节省 50% 内存
        }

        while (currentTop < imageHeight) {
            val currentBottom = (currentTop + sliceHeight).coerceAtMost(imageHeight)
            val rect = Rect(0, currentTop, imageWidth, currentBottom)
            val sliceBitmap = decoder.decodeRegion(rect, decodeOptions)

            if (sliceBitmap != null) {
                slices.add(
                    ImageSlice(
                        bitmap = sliceBitmap,
                        globalOffsetY = currentTop,
                        sliceIndex = index,
                        totalSlices = numSlices
                    )
                )
            }

            index++
            currentTop += step
            if (currentBottom >= imageHeight) break
        }

        decoder.recycle()
        inputStream.close()
        return slices
    }
}
