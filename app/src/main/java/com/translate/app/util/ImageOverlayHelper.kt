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
     * 判断当前文本块是否应该采用顶部对齐：
     * - 原图包含多行 (origLineCount > 1) 的段落，或者可用高度显著高于单行排版高度的大文本框，必须从顶部对齐自然向下排布；
     * - 杜绝因译文仅 1 行而被错误居中抛到框底中间导致位置完全脱节的 Bug。
     */
    fun shouldAlignTop(origLineCount: Int, boxHeight: Float, layoutHeight: Float): Boolean {
        if (origLineCount > 1) return true
        if (boxHeight > 45f && boxHeight >= layoutHeight * 1.35f) return true
        return false
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
     * 规范化段落文本：去除段落内部孤立的硬断行，使多行文本能够根据可用宽度自然折行，释放垂直排版空间。
     * 智能识别中日韩 (CJK) 表意汉字与西方单词：
     * - 汉字与汉字、汉字与标点之间相连自然平滑拼接，绝不硬塞破坏连贯性的空格；
     * - 西文单词之间保留必要的半角空格。
     */
    fun normalizeParagraphText(text: String): String {
        if (!text.contains("\n")) return text.trim()
        val paragraphs = text.split(Regex("\n{2,}"))
        return paragraphs.joinToString("\n\n") { p ->
            val lines = p.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size <= 1) return@joinToString lines.firstOrNull() ?: ""
            val sb = StringBuilder()
            for (i in lines.indices) {
                if (i > 0) {
                    val prevLastChar = lines[i - 1].last()
                    val currFirstChar = lines[i].first()
                    if (isCjk(prevLastChar) || isCjk(currFirstChar)) {
                        // CJK 汉字或全角标点，不插入空格
                    } else {
                        // 西文字符或单词，插入一个空格
                        sb.append(' ')
                    }
                }
                sb.append(lines[i])
            }
            sb.toString()
        }.trim()
    }

    /**
     * 判断字符是否属于 CJK（中日韩统一表意文字）及全角标点符号集
     */
    fun isCjk(c: Char): Boolean {
        val ub = Character.UnicodeBlock.of(c)
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
                ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS ||
                ub == Character.UnicodeBlock.GENERAL_PUNCTUATION
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
        val origLines = max(1, lineCount)
        val avgLineHeight = boxHeight / origLines.toFloat()

        // 1. 基准行高字号：根据原图实际行高锚定视觉比例
        val lineBasedSize = if (origLines > 1) {
            (avgLineHeight * 0.72f).coerceIn(12f, boxHeight * 0.85f)
        } else {
            min(boxHeight * 0.75f, boxWidth * 0.85f).coerceAtLeast(minFontSize)
        }

        // 2. 面积容量预估
        val charCount = max(1, text.length)
        val areaPerChar = (boxWidth * boxHeight) / charCount.toFloat()
        val estimatedSize = kotlin.math.sqrt(areaPerChar.toDouble()).toFloat() * 0.85f

        // 3. 动态平滑融合：多行段落严格限制在合理字号区间，杜绝短文本暴增与长文本缩微
        val effectiveEstimated = if (origLines > 1) {
            min(max(lineBasedSize, estimatedSize), lineBasedSize * 1.25f)
        } else {
            min(estimatedSize, lineBasedSize)
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
     * 智能采样原图指定矩形边缘的平均背景颜色（采用众数桶过滤算法 Dominant Color Extraction）
     * 彻底剔除文字前景色墨迹与抗锯齿污染，杜绝背景变脏灰和灰色遮罩色块溢出
     */
    fun sampleBackgroundColor(bitmap: Bitmap, rect: Rect): Int {
        val bWidth = bitmap.width
        val bHeight = bitmap.height
        if (bWidth <= 0 || bHeight <= 0) return -1

        // 沿包围盒外围 1 像素采样，避免直接踩在字符字形的墨迹边缘上
        val left = (rect.left - 1).coerceIn(0, bWidth - 1)
        val right = (rect.right + 1).coerceIn(0, bWidth - 1)
        val top = (rect.top - 1).coerceIn(0, bHeight - 1)
        val bottom = (rect.bottom + 1).coerceIn(0, bHeight - 1)
        if (left >= right || top >= bottom) return -1

        val sampledColors = mutableListOf<Int>()
        val stepX = max(1, (right - left) / 10)
        val stepY = max(1, (bottom - top) / 10)

        for (x in left..right step stepX) {
            sampledColors.add(bitmap.getPixel(x, top))
            sampledColors.add(bitmap.getPixel(x, bottom))
        }
        for (y in top..bottom step stepY) {
            sampledColors.add(bitmap.getPixel(left, y))
            sampledColors.add(bitmap.getPixel(right, y))
        }

        if (sampledColors.isEmpty()) return -1

        // 众数桶聚类：按高 4 位（16 级灰度）量化颜色
        val buckets = HashMap<Int, MutableList<Int>>()
        for (color in sampledColors) {
            val rBucket = extractRed(color) shr 4
            val gBucket = extractGreen(color) shr 4
            val bBucket = extractBlue(color) shr 4
            val key = (rBucket shl 8) or (gBucket shl 4) or bBucket
            buckets.getOrPut(key) { mutableListOf() }.add(color)
        }

        // 选取频次最高的颜色簇（背景色占绝大多数采样点）
        val dominantColors = buckets.values.maxByOrNull { it.size } ?: sampledColors
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        for (c in dominantColors) {
            rSum += extractRed(c)
            gSum += extractGreen(c)
            bSum += extractBlue(c)
        }
        val count = dominantColors.size
        return makeRgb((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
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
