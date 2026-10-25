package com.translate.app.util

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import com.translate.app.data.model.OverlayRect
import com.translate.app.data.model.TranslatedBlock
import kotlin.math.max
import kotlin.math.min

/**
 * 原位图像贴字翻译的数学计算、坐标变换与辅助算法工具
 */
object ImageOverlayHelper {

    const val MASK_ALPHA = 255 // 100% 完全不透明度，彻底杜绝原图字迹透光重影
    const val DEFAULT_OUTSET_PX = 3 // 矩形安全外扩默认 3 像素，彻底覆盖笔画边缘与抗锯齿
    const val MIN_FONT_SIZE = 10f

    data class OverlayPoint(val x: Float, val y: Float) {
        fun toPointF(): PointF = PointF(x, y)
    }

    data class FitTransform(
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
        val displayWidth: Float,
        val displayHeight: Float
    )

    /**
     * 计算图片按 Fit 模式（保持宽高比且完整居中适应）放置在容器内的缩放与偏移
     */
    fun computeFitTransform(
        imageWidth: Int,
        imageHeight: Int,
        containerWidth: Float,
        containerHeight: Float
    ): FitTransform {
        if (imageWidth <= 0 || imageHeight <= 0 || containerWidth <= 0f || containerHeight <= 0f) {
            return FitTransform(1.0f, 0f, 0f, 0f, 0f)
        }
        val scale = min(containerWidth / imageWidth.toFloat(), containerHeight / imageHeight.toFloat())
        val displayWidth = imageWidth * scale
        val displayHeight = imageHeight * scale
        val offsetX = (containerWidth - displayWidth) / 2f
        val offsetY = (containerHeight - displayHeight) / 2f
        return FitTransform(
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            displayWidth = displayWidth,
            displayHeight = displayHeight
        )
    }

    /**
     * 将原图坐标系下的点映射到当前屏幕视口坐标（考虑 Fit 缩放与用户手势缩放、平移）
     */
    fun mapImagePointToScreen(
        imageX: Float,
        imageY: Float,
        fit: FitTransform,
        userScale: Float,
        userOffsetX: Float,
        userOffsetY: Float,
        containerWidth: Float,
        containerHeight: Float
    ): OverlayPoint {
        val xInFit = fit.offsetX + imageX * fit.scale
        val yInFit = fit.offsetY + imageY * fit.scale
        val screenX = (xInFit - containerWidth / 2f) * userScale + containerWidth / 2f + userOffsetX
        val screenY = (yInFit - containerHeight / 2f) * userScale + containerHeight / 2f + userOffsetY
        return OverlayPoint(screenX, screenY)
    }

    /**
     * 将用户在屏幕上的触摸点逆向映射回原图坐标系
     */
    fun mapScreenPointToImage(
        screenX: Float,
        screenY: Float,
        fit: FitTransform,
        userScale: Float,
        userOffsetX: Float,
        userOffsetY: Float,
        containerWidth: Float,
        containerHeight: Float
    ): OverlayPoint {
        if (fit.scale <= 0f || userScale <= 0f) {
            return OverlayPoint(0f, 0f)
        }
        val xInFit = (screenX - containerWidth / 2f - userOffsetX) / userScale + containerWidth / 2f
        val yInFit = (screenY - containerHeight / 2f - userOffsetY) / userScale + containerHeight / 2f
        val imageX = (xInFit - fit.offsetX) / fit.scale
        val imageY = (yInFit - fit.offsetY) / fit.scale
        return OverlayPoint(imageX, imageY)
    }

    /**
     * 根据原图坐标点在所有文本块中查找命中的 Block（优先命中较小面积的精确 Block）
     */
    fun findBlockAtPoint(
        blocks: List<TranslatedBlock>,
        imagePointX: Float,
        imagePointY: Float
    ): TranslatedBlock? {
        val matched = blocks.filter { it.contains(imagePointX, imagePointY) }
        // 若同时命中重叠块，优先返回面积更小的块（更精准的文本行/词组块）
        return matched.minByOrNull {
            val w = max(1, it.box.width)
            val h = max(1, it.box.height)
            w * h
        }
    }

    /**
     * 将 Android Rect 向外做安全膨胀（外扩 outset），彻底覆盖边缘抗锯齿墨迹，并限制在图像边界内
     */
    fun expandRect(
        rect: Rect,
        outsetX: Int = DEFAULT_OUTSET_PX,
        outsetY: Int = DEFAULT_OUTSET_PX,
        maxWidth: Int = Int.MAX_VALUE,
        maxHeight: Int = Int.MAX_VALUE
    ): Rect {
        val left = (rect.left - outsetX).coerceAtLeast(0)
        val top = (rect.top - outsetY).coerceAtLeast(0)
        val right = (rect.right + outsetX).coerceAtMost(maxWidth)
        val bottom = (rect.bottom + outsetY).coerceAtMost(maxHeight)
        return Rect(left, top, max(left, right), max(top, bottom))
    }

    /**
     * 将 OverlayRect 向外做安全膨胀（跨平台纯 Kotlin 实现）
     */
    fun expandOverlayRect(
        rect: OverlayRect,
        outsetX: Int = DEFAULT_OUTSET_PX,
        outsetY: Int = DEFAULT_OUTSET_PX,
        maxWidth: Int = Int.MAX_VALUE,
        maxHeight: Int = Int.MAX_VALUE
    ): OverlayRect {
        return rect.expand(outsetX, outsetY, maxWidth, maxHeight)
    }

    /**
     * 根据多行 Lines 的包围盒精确计算紧凑外接包围盒，杜绝 ML Kit textBlock.boundingBox 溢出空白区
     */
    fun computeCompactBoundingBox(
        lineBoxes: List<OverlayRect>,
        fallback: OverlayRect? = null
    ): OverlayRect {
        if (lineBoxes.isEmpty()) {
            return fallback ?: OverlayRect(0, 0, 0, 0)
        }
        var minLeft = Int.MAX_VALUE
        var minTop = Int.MAX_VALUE
        var maxRight = Int.MIN_VALUE
        var maxBottom = Int.MIN_VALUE
        for (box in lineBoxes) {
            minLeft = minOf(minLeft, box.left)
            minTop = minOf(minTop, box.top)
            maxRight = maxOf(maxRight, box.right)
            maxBottom = maxOf(maxBottom, box.bottom)
        }
        return if (minLeft <= maxRight && minTop <= maxBottom) {
            OverlayRect(minLeft, minTop, maxRight, maxBottom)
        } else {
            fallback ?: OverlayRect(0, 0, 0, 0)
        }
    }

    /**
     * 计算文字排版的垂直相对偏移：
     * - 多行段落从框顶部对齐（保持微量内边距），自然向右向下排布，杜绝垂直强制居中导致的上下大片悬空；
     * - 单行短文本垂直居中更端正美观。
     */
    fun computeVerticalOffset(
        availHeight: Float,
        layoutHeight: Float,
        paddingV: Float,
        isSingleLine: Boolean
    ): Float {
        return if (isSingleLine) {
            paddingV + max(0f, (availHeight - layoutHeight) / 2f)
        } else {
            paddingV
        }
    }

    /**
     * 规范化段落文本：去除段落内部孤立的硬断行，使多行文本能够根据可用宽度自然折行，释放垂直排版空间
     */
    fun normalizeParagraphText(text: String): String {
        if (!text.contains("\n")) return text.trim()
        val paragraphs = text.split(Regex("\n{2,}"))
        return paragraphs.joinToString("\n\n") { p ->
            p.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        }.trim()
    }

    /**
     * 针对指定矩形框自适应计算合适字号（支持基于原行数与行高动态适配）
     */
    fun calculateAutoFitFontSize(
        text: String,
        boxWidth: Float,
        boxHeight: Float,
        lineCount: Int = 1,
        maxFontSize: Float = Float.MAX_VALUE,
        minFontSize: Float = 10f
    ): Float {
        if (text.isBlank() || boxWidth <= 0f || boxHeight <= 0f) return minFontSize
        val charCount = max(1, text.length)
        // 估算：长宽面积与字符数量的关系
        val areaPerChar = (boxWidth * boxHeight) / charCount.toFloat()
        val estimatedSize = kotlin.math.sqrt(areaPerChar.toDouble()).toFloat() * 0.85f

        // 若多行段落，结合平均行高动态适配，让译文自然舒展填满原文字区域
        val effectiveEstimated = if (lineCount > 1) {
            val avgLineHeight = boxHeight / lineCount.toFloat()
            val targetSizeByLine = avgLineHeight * 0.72f
            max(estimatedSize, targetSizeByLine)
        } else {
            estimatedSize
        }

        val upperLimit = min(maxFontSize, boxHeight * 0.85f)
        val safeMin = min(minFontSize, upperLimit)
        return effectiveEstimated.coerceIn(safeMin, max(safeMin, upperLimit))
    }

    /**
     * 重载版本，保持向后兼容
     */
    fun calculateAutoFitFontSize(
        text: String,
        boxWidth: Float,
        boxHeight: Float,
        maxFontSize: Float,
        minFontSize: Float
    ): Float {
        return calculateAutoFitFontSize(text, boxWidth, boxHeight, 1, maxFontSize, minFontSize)
    }

    /**
     * 智能采样原图指定矩形边缘的平均背景颜色
     */
    fun sampleBackgroundColor(bitmap: Bitmap, rect: Rect): Int {
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val right = rect.right.coerceIn(0, bitmap.width - 1)
        val top = rect.top.coerceIn(0, bitmap.height - 1)
        val bottom = rect.bottom.coerceIn(0, bitmap.height - 1)
        if (left >= right || top >= bottom) return -1 // White 0xFFFFFFFF

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0

        // 沿矩形四周边缘采样
        val stepX = max(1, (right - left) / 8)
        val stepY = max(1, (bottom - top) / 8)

        for (x in left..right step stepX) {
            val pTop = bitmap.getPixel(x, top)
            val pBottom = bitmap.getPixel(x, bottom)
            rSum += extractRed(pTop) + extractRed(pBottom)
            gSum += extractGreen(pTop) + extractGreen(pBottom)
            bSum += extractBlue(pTop) + extractBlue(pBottom)
            count += 2
        }
        for (y in top..bottom step stepY) {
            val pLeft = bitmap.getPixel(left, y)
            val pRight = bitmap.getPixel(right, y)
            rSum += extractRed(pLeft) + extractRed(pRight)
            gSum += extractGreen(pLeft) + extractGreen(pRight)
            bSum += extractBlue(pLeft) + extractBlue(pRight)
            count += 2
        }

        if (count == 0) return -1
        val avgR = (rSum / count).toInt()
        val avgG = (gSum / count).toInt()
        val avgB = (bSum / count).toInt()
        return makeRgb(avgR, avgG, avgB)
    }

    /**
     * 根据背景色的明暗度计算高对比度文本颜色（亮背景用纯深黑，暗背景用高亮白）
     */
    fun computeContrastingTextColor(backgroundColor: Int): Int {
        val r = extractRed(backgroundColor) / 255.0
        val g = extractGreen(backgroundColor) / 255.0
        val b = extractBlue(backgroundColor) / 255.0
        // 国际标准亮度加权公式 (Luminance)
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        return if (luminance >= 0.55) {
            makeRgb(18, 24, 38) // 高质感深黑
        } else {
            makeRgb(248, 250, 252) // 高质感冷白
        }
    }

    /**
     * 将识别出的全部文本块格式化为整洁的纯文本，方便一键复制
     */
    fun formatAllBlocksText(blocks: List<TranslatedBlock>, includeTranslation: Boolean = true): String {
        if (blocks.isEmpty()) return ""
        val sb = StringBuilder()
        blocks.forEach { block ->
            if (block.originalText.isNotBlank()) {
                if (includeTranslation && block.translatedText.isNotBlank()) {
                    sb.append("${block.originalText}\n${block.translatedText}\n\n")
                } else {
                    sb.append("${block.originalText}\n\n")
                }
            }
        }
        return sb.toString().trim()
    }

    fun extractRed(color: Int): Int = (color shr 16) and 0xFF
    fun extractGreen(color: Int): Int = (color shr 8) and 0xFF
    fun extractBlue(color: Int): Int = color and 0xFF
    fun makeRgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
