package com.translate.app.util

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

/**
 * 基于 Google ML Kit 离线文字识别引擎 (Text Recognition V2)
 *
 * 特性：
 * - 纯设备端离线运行，零 API 调用配额消耗，极速响应 (50-200ms)
 * - 支持中文简体/繁体、英文、数字、多国混合排版高精度识别
 * - 自动检测文本行、块级结构并智能拼接
 */
object OcrManager {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /**
     * 从图片 Uri 识别提取文本
     *
     * @param context 上下文
     * @param imageUri 图片 Uri
     * @param onSuccess 识别成功回调（传入提取清洗后的文本）
     * @param onError 识别失败回调（传入友好提示文案）
     */
    fun recognizeText(
        context: Context,
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val image = InputImage.fromFilePath(context, imageUri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val rawText = visionText.text
                    val cleanedText = cleanRecognizedText(rawText)
                    if (cleanedText.isNotBlank()) {
                        onSuccess(cleanedText)
                    } else {
                        onError("未能从图片中识别到清晰文字，请调整拍摄角度、对焦或光线后重试")
                    }
                }
                .addOnFailureListener { e ->
                    onError("图片文字识别失败: ${e.localizedMessage ?: "未知异常"}")
                }
        } catch (e: Exception) {
            onError("读取图片失败: ${e.localizedMessage ?: "无法访问所选图片"}")
        }
    }

    /**
     * 智能清洗 OCR 识别出的文本：去除冗余空行，保持段落自然流畅
     */
    fun cleanRecognizedText(text: String): String {
        return text
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }
}
