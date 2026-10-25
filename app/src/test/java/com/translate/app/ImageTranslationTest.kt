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
}
