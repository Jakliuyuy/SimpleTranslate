package com.translate.app

import android.graphics.Rect
import com.translate.app.data.model.ImageTranslateUiState
import com.translate.app.data.model.Language
import com.translate.app.data.model.TranslatedBlock
import com.translate.app.util.ImageOverlayHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ImageTranslationTest {

    @Test
    fun testComputeFitTransform_Landscape() {
        // 宽 1920 高 1080 的横屏图片，放在宽 1080 高 1920 的手机竖屏视口中
        val imgWidth = 1920
        val imgHeight = 1080
        val containerWidth = 1080f
        val containerHeight = 1920f

        val fit = ImageOverlayHelper.computeFitTransform(imgWidth, imgHeight, containerWidth, containerHeight)

        // 缩放应受宽度限制：1080 / 1920 = 0.5625
        val expectedScale = 1080f / 1920f
        assertEquals(expectedScale, fit.scale, 0.0001f)
        assertEquals(1080f, fit.displayWidth, 0.01f)
        assertEquals(1080 * expectedScale, fit.displayHeight, 0.01f)
        // 水平贴边，垂直居中
        assertEquals(0f, fit.offsetX, 0.001f)
        val expectedOffsetY = (containerHeight - fit.displayHeight) / 2f
        assertEquals(expectedOffsetY, fit.offsetY, 0.01f)
    }

    @Test
    fun testComputeFitTransform_Portrait() {
        // 宽 1080 高 2400 的竖屏长图，放在宽 1080 高 1920 的手机视口中
        val imgWidth = 1080
        val imgHeight = 2400
        val containerWidth = 1080f
        val containerHeight = 1920f

        val fit = ImageOverlayHelper.computeFitTransform(imgWidth, imgHeight, containerWidth, containerHeight)

        // 缩放应受高度限制：1920 / 2400 = 0.8
        val expectedScale = 1920f / 2400f
        assertEquals(expectedScale, fit.scale, 0.0001f)
        assertEquals(imgWidth * expectedScale, fit.displayWidth, 0.01f)
        assertEquals(1920f, fit.displayHeight, 0.01f)
        // 垂直贴边，水平居中
        assertEquals(0f, fit.offsetY, 0.001f)
        val expectedOffsetX = (containerWidth - fit.displayWidth) / 2f
        assertEquals(expectedOffsetX, fit.offsetX, 0.01f)
    }

    @Test
    fun testCoordinateMappingInvertible() {
        // 验证原图坐标到屏幕坐标、再逆向反算回原图坐标的双向严格可逆性
        val imgWidth = 1200
        val imgHeight = 1600
        val containerWidth = 1080f
        val containerHeight = 1920f

        val fit = ImageOverlayHelper.computeFitTransform(imgWidth, imgHeight, containerWidth, containerHeight)

        // 测试多种缩放与平移组合场景
        val scenarios = listOf(
            Triple(1.0f, 0f, 0f), // 无缩放、无位移
            Triple(2.5f, 150f, -80f), // 放大 2.5 倍并平移
            Triple(4.0f, -300f, 400f), // 放大 4.0 倍
            Triple(1.5f, -50f, 120f)
        )

        // 测试原图四个角及内部采样点
        val testPoints = listOf(
            Pair(0f, 0f),
            Pair(imgWidth.toFloat(), imgHeight.toFloat()),
            Pair(imgWidth / 2f, imgHeight / 2f),
            Pair(153.4f, 892.1f),
            Pair(789.0f, 345.6f)
        )

        for ((userScale, userOffsetX, userOffsetY) in scenarios) {
            for ((origX, origY) in testPoints) {
                // 正向映射
                val screenPoint = ImageOverlayHelper.mapImagePointToScreen(
                    imageX = origX,
                    imageY = origY,
                    fit = fit,
                    userScale = userScale,
                    userOffsetX = userOffsetX,
                    userOffsetY = userOffsetY,
                    containerWidth = containerWidth,
                    containerHeight = containerHeight
                )

                // 逆向映射
                val invertedPoint = ImageOverlayHelper.mapScreenPointToImage(
                    screenX = screenPoint.x,
                    screenY = screenPoint.y,
                    fit = fit,
                    userScale = userScale,
                    userOffsetX = userOffsetX,
                    userOffsetY = userOffsetY,
                    containerWidth = containerWidth,
                    containerHeight = containerHeight
                )

                assertTrue(
                    "X mapping failed for scale $userScale: orig=$origX, inverted=${invertedPoint.x}",
                    abs(origX - invertedPoint.x) < 0.01f
                )
                assertTrue(
                    "Y mapping failed for scale $userScale: orig=$origY, inverted=${invertedPoint.y}",
                    abs(origY - invertedPoint.y) < 0.01f
                )
            }
        }
    }

    @Test
    fun testFindBlockAtPoint() {
        val block1 = TranslatedBlock(
            id = "1",
            index = 1,
            originalText = "Hello World",
            box = com.translate.app.data.model.OverlayRect(100, 100, 300, 200)
        )
        val block2 = TranslatedBlock(
            id = "2",
            index = 2,
            originalText = "Sub Title",
            box = com.translate.app.data.model.OverlayRect(120, 120, 200, 160) // 嵌套在 block1 内的小面积精准块
        )
        val block3 = TranslatedBlock(
            id = "3",
            index = 3,
            originalText = "Footer",
            box = com.translate.app.data.model.OverlayRect(500, 800, 800, 900)
        )

        val blocks = listOf(block1, block2, block3)

        // 1. 点击空白处
        val hitNone = ImageOverlayHelper.findBlockAtPoint(blocks, 50f, 50f)
        assertNull(hitNone)

        // 2. 点击 block3 内部
        val hit3 = ImageOverlayHelper.findBlockAtPoint(blocks, 600f, 850f)
        assertNotNull(hit3)
        assertEquals("3", hit3?.id)

        // 3. 点击 block1 和 block2 重叠区域 (150, 140)，应优先命中更精准的小面积 block2
        val hitInner = ImageOverlayHelper.findBlockAtPoint(blocks, 150f, 140f)
        assertNotNull(hitInner)
        assertEquals("2", hitInner?.id)

        // 4. 点击 block1 独占区域 (250, 180)
        val hitOuter = ImageOverlayHelper.findBlockAtPoint(blocks, 250f, 180f)
        assertNotNull(hitOuter)
        assertEquals("1", hitOuter?.id)
    }

    @Test
    fun testAutoFitFontSize() {
        // 短标题
        val sizeShort = ImageOverlayHelper.calculateAutoFitFontSize(
            text = "SALE",
            boxWidth = 200f,
            boxHeight = 80f,
            maxFontSize = 48f,
            minFontSize = 10f
        )
        assertTrue(sizeShort >= 20f)
        assertTrue(sizeShort <= 48f)

        // 长段落
        val sizeLong = ImageOverlayHelper.calculateAutoFitFontSize(
            text = "This is a very long descriptive paragraph that contains multiple sentences to explain the special promotion rules in detail.",
            boxWidth = 200f,
            boxHeight = 80f,
            maxFontSize = 48f,
            minFontSize = 10f
        )
        assertTrue(sizeLong < sizeShort)
        assertTrue(sizeLong >= 10f)

        // 空白文本返回保底最小字号
        val sizeEmpty = ImageOverlayHelper.calculateAutoFitFontSize("", 100f, 50f)
        assertEquals(10f, sizeEmpty, 0.01f)
    }

    @Test
    fun testContrastingTextColor() {
        // 浅色背景（纯白、米白、亮黄）：应产生深色文字
        val white = ImageOverlayHelper.makeRgb(255, 255, 255)
        val cream = ImageOverlayHelper.makeRgb(240, 240, 230)
        val yellow = ImageOverlayHelper.makeRgb(255, 235, 59)

        val textColorOnWhite = ImageOverlayHelper.computeContrastingTextColor(white)
        val textColorOnCream = ImageOverlayHelper.computeContrastingTextColor(cream)
        val textColorOnYellow = ImageOverlayHelper.computeContrastingTextColor(yellow)

        // 深色文字 RGB 平均值很低
        assertTrue(ImageOverlayHelper.extractRed(textColorOnWhite) < 50)
        assertTrue(ImageOverlayHelper.extractRed(textColorOnCream) < 50)
        assertTrue(ImageOverlayHelper.extractRed(textColorOnYellow) < 50)

        // 深色背景（纯黑、深蓝、深灰）：应产生高对比冷白文字
        val black = ImageOverlayHelper.makeRgb(0, 0, 0)
        val darkBlue = ImageOverlayHelper.makeRgb(15, 23, 42)
        val darkGray = ImageOverlayHelper.makeRgb(30, 30, 30)

        val textColorOnBlack = ImageOverlayHelper.computeContrastingTextColor(black)
        val textColorOnDarkBlue = ImageOverlayHelper.computeContrastingTextColor(darkBlue)
        val textColorOnDarkGray = ImageOverlayHelper.computeContrastingTextColor(darkGray)

        assertTrue(ImageOverlayHelper.extractRed(textColorOnBlack) > 200)
        assertTrue(ImageOverlayHelper.extractRed(textColorOnDarkBlue) > 200)
        assertTrue(ImageOverlayHelper.extractRed(textColorOnDarkGray) > 200)
    }

    @Test
    fun testFormatAllBlocksText() {
        val blocks = listOf(
            TranslatedBlock(
                id = "1",
                index = 1,
                originalText = "Welcome to our store",
                translatedText = "欢迎光临我们的商店",
                box = com.translate.app.data.model.OverlayRect(0, 0, 100, 50)
            ),
            TranslatedBlock(
                id = "2",
                index = 2,
                originalText = "Open Daily 9am - 10pm",
                translatedText = "每天上午 9 点至晚上 10 点营业",
                box = com.translate.app.data.model.OverlayRect(0, 60, 100, 100)
            )
        )

        val withTranslation = ImageOverlayHelper.formatAllBlocksText(blocks, includeTranslation = true)
        assertTrue(withTranslation.contains("Welcome to our store"))
        assertTrue(withTranslation.contains("欢迎光临我们的商店"))
        assertTrue(withTranslation.contains("Open Daily 9am - 10pm"))
        assertTrue(withTranslation.contains("每天上午 9 点至晚上 10 点营业"))

        val withoutTranslation = ImageOverlayHelper.formatAllBlocksText(blocks, includeTranslation = false)
        assertTrue(withoutTranslation.contains("Welcome to our store"))
        assertFalse(withoutTranslation.contains("欢迎光临我们的商店"))
    }

    @Test
    fun testTranslatedBlockDisplayText() {
        val block = TranslatedBlock(
            id = "1",
            originalText = "English Text",
            translatedText = "中文译文",
            box = com.translate.app.data.model.OverlayRect(0, 0, 50, 50)
        )

        // 译文模式：展示中文
        assertEquals("中文译文", block.getDisplayText(showTranslation = true))
        // 原文模式：展示英文
        assertEquals("English Text", block.getDisplayText(showTranslation = false))

        // 若译文为空，译文模式下回退到展示原文
        val emptyTranslationBlock = block.copy(translatedText = "")
        assertEquals("English Text", emptyTranslationBlock.getDisplayText(showTranslation = true))
    }

    @Test
    fun testUiStateSelectedBlockHelper() {
        val b1 = TranslatedBlock(id = "a", originalText = "A", box = com.translate.app.data.model.OverlayRect(0, 0, 10, 10))
        val b2 = TranslatedBlock(id = "b", originalText = "B", box = com.translate.app.data.model.OverlayRect(20, 20, 30, 30))
        val state = ImageTranslateUiState(
            blocks = listOf(b1, b2),
            selectedBlockId = "b"
        )
        assertNotNull(state.selectedBlock)
        assertEquals("b", state.selectedBlock?.id)
        assertEquals("B", state.selectedBlock?.originalText)
    }

    @Test
    fun testAutoFitFontSize_LargeBannerDynamicScale() {
        // 大招牌/大标题（宽 800 高 200，单词 "STORE"），摆脱 48f 硬编码上限，应自适应放大为大字号
        val bannerSize = ImageOverlayHelper.calculateAutoFitFontSize(
            text = "STORE",
            boxWidth = 800f,
            boxHeight = 200f
        )
        assertTrue("Large banner font size should exceed 48f, got $bannerSize", bannerSize > 48f)
        assertTrue("Large banner font size should respect boxHeight limit, got $bannerSize", bannerSize <= 200f * 0.85f)

        // 若调用方显式传入 maxFontSize 上限，应严格受限
        val constrainedSize = ImageOverlayHelper.calculateAutoFitFontSize(
            text = "STORE",
            boxWidth = 800f,
            boxHeight = 200f,
            maxFontSize = 40f
        )
        assertEquals(40f, constrainedSize, 0.01f)
    }

    @Test
    fun testLanguageSwapLogic() {
        // 场景 1: 普通语言互换 (EN -> ZH 互换为 ZH -> EN)
        var sourceLang = Language.EN
        var targetLang = Language.ZH

        var newSource = targetLang
        var newTarget = if (sourceLang == Language.AUTO) Language.ZH else sourceLang
        assertEquals(Language.ZH, newSource)
        assertEquals(Language.EN, newTarget)

        // 场景 2: AUTO 语言互换 (AUTO -> JA 互换为 JA -> ZH)
        sourceLang = Language.AUTO
        targetLang = Language.JA

        newSource = targetLang
        newTarget = if (sourceLang == Language.AUTO) Language.ZH else sourceLang
        assertEquals(Language.JA, newSource)
        assertEquals(Language.ZH, newTarget)
    }

    @Test
    fun testZeroOrNegativeFitTransformSafeguards() {
        // 容器或图像为 0 或负数时的除零防崩溃保护
        val fitZeroImg = ImageOverlayHelper.computeFitTransform(0, 0, 1080f, 1920f)
        assertEquals(1.0f, fitZeroImg.scale, 0.001f)

        val fitZeroContainer = ImageOverlayHelper.computeFitTransform(100, 100, 0f, 0f)
        assertEquals(1.0f, fitZeroContainer.scale, 0.001f)

        val mapped = ImageOverlayHelper.mapScreenPointToImage(
            screenX = 100f,
            screenY = 100f,
            fit = fitZeroContainer,
            userScale = 0f,
            userOffsetX = 0f,
            userOffsetY = 0f,
            containerWidth = 0f,
            containerHeight = 0f
        )
        assertEquals(0f, mapped.x, 0.001f)
        assertEquals(0f, mapped.y, 0.001f)
    }

    @Test
    fun testOverlayRectContainsBoundary() {
        val rect = com.translate.app.data.model.OverlayRect(10, 20, 100, 200)
        // 顶点与边界测试
        assertTrue(rect.contains(10f, 20f))
        assertTrue(rect.contains(100f, 200f))
        assertTrue(rect.contains(10f, 200f))
        assertTrue(rect.contains(100f, 20f))
        assertTrue(rect.contains(50f, 100f))

        // 越界测试
        assertFalse(rect.contains(9.9f, 20f))
        assertFalse(rect.contains(10f, 19.9f))
        assertFalse(rect.contains(100.1f, 100f))
        assertFalse(rect.contains(50f, 200.1f))
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun testCancellationExceptionNotSwallowedPattern() {
        // 验证协程取消异常的向上重新抛出模式
        try {
            throw kotlinx.coroutines.CancellationException("Job was cancelled")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // 吞噬分支不应被执行
            throw IllegalStateException("Should not swallow")
        }
    }

    @Test
    fun testMaskOpacityIsFull100Percent() {
        // 验证遮罩画笔不透明度必须为 255 (100% 不透明)，彻底杜绝原图文字透过 94% 半透明产生鬼影重影
        assertEquals(255, ImageOverlayHelper.MASK_ALPHA)
        assertTrue("Mask alpha must be exactly 255 (0xFF)", ImageOverlayHelper.MASK_ALPHA == 0xFF)
    }

    @Test
    fun testRectSafetyOutsetExpansion() {
        // 1. 标准外扩测试：向外安全膨胀 3 像素 (DEFAULT_OUTSET_PX)
        val origRect = com.translate.app.data.model.OverlayRect(left = 100, top = 200, right = 500, bottom = 400)
        val expanded = origRect.expand(outsetX = 3, outsetY = 3)
        assertEquals(97, expanded.left)
        assertEquals(197, expanded.top)
        assertEquals(503, expanded.right)
        assertEquals(403, expanded.bottom)

        // 2. 图像左上边界保护：外扩不能小于 0
        val boundaryTopLeft = com.translate.app.data.model.OverlayRect(left = 1, top = 2, right = 50, bottom = 60)
        val clampTopLeft = boundaryTopLeft.expand(outsetX = 3, outsetY = 3, maxWidth = 1000, maxHeight = 1000)
        assertEquals(0, clampTopLeft.left)
        assertEquals(0, clampTopLeft.top)
        assertEquals(53, clampTopLeft.right)
        assertEquals(63, clampTopLeft.bottom)

        // 3. 图像右下边界保护：外扩不能超出图片最大尺寸
        val boundaryBottomRight = com.translate.app.data.model.OverlayRect(left = 950, top = 980, right = 999, bottom = 1000)
        val clampBottomRight = boundaryBottomRight.expand(outsetX = 3, outsetY = 3, maxWidth = 1000, maxHeight = 1000)
        assertEquals(947, clampBottomRight.left)
        assertEquals(977, clampBottomRight.top)
        assertEquals(1000, clampBottomRight.right)
        assertEquals(1000, clampBottomRight.bottom)

        // 4. OverlayRect expandOverlayRect 辅助方法一致性测试
        val overlayRect = com.translate.app.data.model.OverlayRect(50, 60, 150, 160)
        val overlayExpanded = ImageOverlayHelper.expandOverlayRect(overlayRect, outsetX = 4, outsetY = 4, maxWidth = 200, maxHeight = 200)
        assertEquals(46, overlayExpanded.left)
        assertEquals(56, overlayExpanded.top)
        assertEquals(154, overlayExpanded.right)
        assertEquals(164, overlayExpanded.bottom)
    }

    @Test
    fun testComputeCompactBoundingBoxFromLines() {
        // 模拟消息气泡：ML Kit textBlock.boundingBox 原本过宽 (100, 100, 800, 300) 溢出到气泡外空白区
        // 但 actual lines 只有 (100, 100, 350, 180) 和 (100, 200, 320, 280)
        val line1 = com.translate.app.data.model.OverlayRect(100, 100, 350, 180)
        val line2 = com.translate.app.data.model.OverlayRect(100, 200, 320, 280)
        val lines = listOf(line1, line2)

        val compactBox = ImageOverlayHelper.computeCompactBoundingBox(lines)
        assertEquals(100, compactBox.left)
        assertEquals(100, compactBox.top)
        assertEquals(350, compactBox.right) // 紧凑宽度为 350，而不是粗糙大框的 800！
        assertEquals(280, compactBox.bottom)

        // 模拟长段落：所有行紧密包含，覆盖到最后一行的底部
        val paraLine1 = com.translate.app.data.model.OverlayRect(50, 500, 600, 540)
        val paraLine2 = com.translate.app.data.model.OverlayRect(50, 545, 590, 585)
        val paraLine3 = com.translate.app.data.model.OverlayRect(50, 590, 420, 630)
        val paraLines = listOf(paraLine1, paraLine2, paraLine3)

        val paraBox = ImageOverlayHelper.computeCompactBoundingBox(paraLines)
        assertEquals(50, paraBox.left)
        assertEquals(500, paraBox.top)
        assertEquals(600, paraBox.right)
        assertEquals(630, paraBox.bottom) // 完整覆盖到第三行底，不漏字

        // 空 lines fallback 测试
        val fallback = com.translate.app.data.model.OverlayRect(10, 10, 50, 50)
        val emptyCompact = ImageOverlayHelper.computeCompactBoundingBox(emptyList(), fallback)
        assertEquals(fallback, emptyCompact)
    }

    @Test
    fun testLeftAlignmentAndVerticalOffset() {
        // 1. 多行段落排版：必须从顶部内边距自然向下排布，严禁垂直居中悬空
        val availHeight = 300f
        val layoutHeightMultiLine = 120f
        val paddingV = 10f
        val multiLineOffset = ImageOverlayHelper.computeVerticalOffset(
            availHeight = availHeight,
            layoutHeight = layoutHeightMultiLine,
            paddingV = paddingV,
            isSingleLine = false
        )
        // 多行必须紧贴顶部 (paddingV = 10f)，绝不能是居中 (10 + (300 - 120) / 2 = 100f)！
        assertEquals(10f, multiLineOffset, 0.001f)

        // 2. 单行短文本排版：垂直居中更加整洁美观
        val layoutHeightSingleLine = 30f
        val singleLineOffset = ImageOverlayHelper.computeVerticalOffset(
            availHeight = availHeight,
            layoutHeight = layoutHeightSingleLine,
            paddingV = paddingV,
            isSingleLine = true
        )
        val expectedCentered = paddingV + (availHeight - layoutHeightSingleLine) / 2f // 10 + 135 = 145f
        assertEquals(expectedCentered, singleLineOffset, 0.001f)
    }

    @Test
    fun testDynamicFontSizeAdaptationByLineCount() {
        // 多行长文本排版测试：
        // 区域高 200，宽 400。原段落有 4 行，平均行高 50px。
        val text = "这是一个经过高精度实景原位翻译的大段中文描述文字，内容丰富且通顺自然。"
        val singleLineSize = ImageOverlayHelper.calculateAutoFitFontSize(
            text = text,
            boxWidth = 400f,
            boxHeight = 200f,
            lineCount = 1
        )

        val multiLineSize = ImageOverlayHelper.calculateAutoFitFontSize(
            text = text,
            boxWidth = 400f,
            boxHeight = 200f,
            lineCount = 4
        )

        // 多行段落应基于平均行高 (50 * 0.72 = 36px) 进行适配，字号显著大于单纯面积字符比的字号
        assertTrue("Multi-line size ($multiLineSize) should be adapted to line height and >= 30f", multiLineSize >= 30f)
        assertTrue("Multi-line size should not exceed box boundary limit", multiLineSize <= 200f * 0.85f)
    }

    @Test
    fun testNormalizeParagraphText() {
        // 1. 孤立英文换行连贯排版（单词间补充空格）
        val rawOcrText = "This is a sentence\nthat was broken across\nmultiple lines."
        val normalized = ImageOverlayHelper.normalizeParagraphText(rawOcrText)
        assertEquals("This is a sentence that was broken across multiple lines.", normalized)

        // 2. 中文 CJK 换行连贯排版（汉字间自然相接，严禁插入空格）
        val chineseOcrText = "这是一段非常长的中文描述文字\n被断成了两行显示"
        val normalizedChinese = ImageOverlayHelper.normalizeParagraphText(chineseOcrText)
        assertEquals("这是一段非常长的中文描述文字被断成了两行显示", normalizedChinese)

        // 3. 多段落双换行保留
        val multiParagraph = "Paragraph one line 1\nline 2\n\nParagraph two line 1\nline 2"
        val normalizedMulti = ImageOverlayHelper.normalizeParagraphText(multiParagraph)
        assertTrue(normalizedMulti.contains("Paragraph one line 1 line 2"))
        assertTrue(normalizedMulti.contains("Paragraph two line 1 line 2"))
    }

    @Test
    fun testShouldAlignTopParagraphDetection() {
        // 1. 原图为多行段落 (origLineCount = 3)，即使译文仅 1 行 (layoutHeight = 30)，也必须顶部对齐
        assertTrue(ImageOverlayHelper.shouldAlignTop(origLineCount = 3, boxHeight = 150f, layoutHeight = 30f))

        // 2. 原图为单行 (origLineCount = 1) 且框高很小 (boxHeight = 35f, layoutHeight = 30f)，居中端正排布
        assertFalse(ImageOverlayHelper.shouldAlignTop(origLineCount = 1, boxHeight = 35f, layoutHeight = 30f))

        // 3. 原图为单行 (origLineCount = 1) 但框高度极大 (boxHeight = 120f, layoutHeight = 30f)，富余高度下采用顶部排布
        assertTrue(ImageOverlayHelper.shouldAlignTop(origLineCount = 1, boxHeight = 120f, layoutHeight = 30f))
    }

    @Test
    fun testAutoFitFontSizeBoundedGrowth() {
        // 短文本在大框中字号不能无限暴涨成大怪兽
        val shortText = "确定"
        val autoFitSize = ImageOverlayHelper.calculateAutoFitFontSize(
            text = shortText,
            boxWidth = 300f,
            boxHeight = 100f,
            lineCount = 2,
            maxFontSize = 100f,
            minFontSize = 10f
        )
        // 平均行高 50f，基准 36f，最大不超过 36 * 1.25 = 45f
        assertTrue("Short text size in 2-line box should be capped around line height", autoFitSize <= 45f)
    }
}
