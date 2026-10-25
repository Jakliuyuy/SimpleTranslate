package com.translate.app.data.model

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import java.util.UUID

/**
 * 图像矩形包围盒（纯 Kotlin 实现，完全跨平台且在 JVM 单元测试中免 mock）
 */
data class OverlayRect(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    val width: Int get() = kotlin.math.max(0, right - left)
    val height: Int get() = kotlin.math.max(0, bottom - top)

    fun contains(x: Float, y: Float): Boolean {
        return x >= left && x <= right && y >= top && y <= bottom
    }

    fun toAndroidRect(): Rect = Rect(left, top, right, bottom)

    companion object {
        fun fromAndroidRect(rect: Rect): OverlayRect {
            return OverlayRect(rect.left, rect.top, rect.right, rect.bottom)
        }
    }
}

/**
 * 图像文本块（对应 ML Kit 识别出的一个 TextBlock 区域及其中英文翻译）
 */
data class TranslatedBlock(
    val id: String = UUID.randomUUID().toString(),
    val index: Int = 0,
    val originalText: String,
    var translatedText: String = "",
    val box: OverlayRect,
    var backgroundColor: Int = -1, // 0xFFFFFFFF
    var textColor: Int = -16777216, // 0xFF000000
    var isTranslating: Boolean = false,
    var error: String? = null
) {
    // 兼容使用 Android Rect 的场景
    val boundingBox: Rect
        get() = box.toAndroidRect()

    constructor(
        id: String = UUID.randomUUID().toString(),
        index: Int = 0,
        originalText: String,
        translatedText: String = "",
        boundingBox: Rect,
        backgroundColor: Int = -1,
        textColor: Int = -16777216,
        isTranslating: Boolean = false,
        error: String? = null
    ) : this(
        id = id,
        index = index,
        originalText = originalText,
        translatedText = translatedText,
        box = OverlayRect.fromAndroidRect(boundingBox),
        backgroundColor = backgroundColor,
        textColor = textColor,
        isTranslating = isTranslating,
        error = error
    )

    /**
     * 判断原图坐标点 (imageX, imageY) 是否落在该文本块内
     */
    fun contains(imageX: Float, imageY: Float): Boolean {
        return box.contains(imageX, imageY)
    }

    /**
     * 获取显示文本（若译文非空则优先返回译文，否则返回原文）
     */
    fun getDisplayText(showTranslation: Boolean): String {
        return if (showTranslation && translatedText.isNotBlank()) translatedText else originalText
    }
}

/**
 * 图片翻译界面 UI 状态
 */
data class ImageTranslateUiState(
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val originalBitmap: Bitmap? = null,
    val imageUri: Uri? = null,
    val blocks: List<TranslatedBlock> = emptyList(),
    val showTranslationOverlay: Boolean = true, // true: 显示译文贴字覆盖; false: 显示原始高清图
    val selectedBlockId: String? = null, // 当前选中的 Block ID
    val sourceLanguage: Language = Language.AUTO,
    val targetLanguage: Language = Language.ZH,
    val errorMessage: String? = null,
    val isRetranslating: Boolean = false,
    val totalBlockCount: Int = 0,
    val translatedBlockCount: Int = 0
) {
    val selectedBlock: TranslatedBlock?
        get() = blocks.firstOrNull { it.id == selectedBlockId }
}
