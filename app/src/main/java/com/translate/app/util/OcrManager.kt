package com.translate.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.translate.app.data.model.TranslatedBlock

/**
 * 基于 Google ML Kit 离线文字识别引擎 (Text Recognition V2)
 *
 * 特性：
 * - 纯设备端离线运行，零 API 调用配额消耗，极速响应 (50-200ms)
 * - 支持中文简体/繁体、英文、数字、多国混合排版高精度识别
 * - 支持提取 TextBlock 坐标包围盒 (boundingBox)，赋能实景原位贴字覆写翻译
 * - 内置 Exif 角度自动纠偏与大图采样防 OOM 机制
 */
object OcrManager {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /**
     * 识别图片中的文本块 (TextBlock)，提取每个块的精准像素坐标包围盒并采样背景底色与文字颜色
     *
     * @param context 上下文
     * @param imageUri 图片 Uri
     * @param onSuccess 成功回调（返回解析出的 TranslatedBlock 列表与正向位图 Bitmap）
     * @param onError 失败回调
     */
    fun recognizeBlocks(
        context: Context,
        imageUri: Uri,
        onSuccess: (List<TranslatedBlock>, Bitmap) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val bitmap = loadSafeBitmap(context, imageUri)
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val resultBlocks = mutableListOf<TranslatedBlock>()
                    var blockIndex = 0

                    for (textBlock in visionText.textBlocks) {
                        val rawText = textBlock.text.trim()
                        if (rawText.isBlank()) continue

                        // 获取包围盒，若为 null 则聚合 lines 计算包围盒
                        var box = textBlock.boundingBox
                        if (box == null) {
                            var minLeft = Int.MAX_VALUE
                            var minTop = Int.MAX_VALUE
                            var maxRight = Int.MIN_VALUE
                            var maxBottom = Int.MIN_VALUE
                            for (line in textBlock.lines) {
                                line.boundingBox?.let { lBox ->
                                    minLeft = minOf(minLeft, lBox.left)
                                    minTop = minOf(minTop, lBox.top)
                                    maxRight = maxOf(maxRight, lBox.right)
                                    maxBottom = maxOf(maxBottom, lBox.bottom)
                                }
                            }
                            if (minLeft < maxRight && minTop < maxBottom) {
                                box = Rect(minLeft, minTop, maxRight, maxBottom)
                            }
                        }

                        // 限制在 Bitmap 尺寸范围内
                        val safeBox = if (box != null) {
                            Rect(
                                box.left.coerceIn(0, bitmap.width),
                                box.top.coerceIn(0, bitmap.height),
                                box.right.coerceIn(0, bitmap.width),
                                box.bottom.coerceIn(0, bitmap.height)
                            )
                        } else {
                            Rect(0, 0, 10, 10)
                        }

                        if (safeBox.width() <= 0 || safeBox.height() <= 0) continue

                        // 智能采样背景色并计算对比前景色
                        val bgColor = ImageOverlayHelper.sampleBackgroundColor(bitmap, safeBox)
                        val textColor = ImageOverlayHelper.computeContrastingTextColor(bgColor)

                        resultBlocks.add(
                            TranslatedBlock(
                                index = ++blockIndex,
                                originalText = rawText,
                                translatedText = "",
                                boundingBox = safeBox,
                                backgroundColor = bgColor,
                                textColor = textColor,
                                isTranslating = false
                            )
                        )
                    }

                    if (resultBlocks.isNotEmpty()) {
                        onSuccess(resultBlocks, bitmap)
                    } else {
                        onError("未能从图片中识别到清晰文字，请调整拍摄角度、对焦或光线后重试")
                    }
                }
                .addOnFailureListener { e ->
                    onError("图片文字识别失败: ${e.localizedMessage ?: "未知异常"}")
                }
        } catch (t: Throwable) {
            onError("读取图片失败: ${t.localizedMessage ?: "图片尺寸过大或无权访问"}")
        }
    }

    /**
     * 从图片 Uri 识别提取纯文本（兼容旧接口）
     */
    fun recognizeText(
        context: Context,
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        recognizeBlocks(
            context = context,
            imageUri = imageUri,
            onSuccess = { blocks, _ ->
                val combinedText = blocks.joinToString("\n\n") { it.originalText }
                val cleanedText = cleanRecognizedText(combinedText)
                if (cleanedText.isNotBlank()) {
                    onSuccess(cleanedText)
                } else {
                    onError("未能从图片中识别到清晰文字，请调整拍摄角度、对焦或光线后重试")
                }
            },
            onError = onError
        )
    }

    /**
     * 安全加载位图，自动处理 Exif 旋转并下采样大图防止 OOM
     */
    fun loadSafeBitmap(context: Context, uri: Uri): Bitmap {
        // 1. 读取原始尺寸与 Exif 旋转信息
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        val rawWidth = options.outWidth
        val rawHeight = options.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) {
            throw IllegalArgumentException("无法解析图片尺寸")
        }

        // 读取 Exif 旋转角度
        var rotationDegrees = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                rotationDegrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        } catch (_: Exception) {
            rotationDegrees = 0
        }

        // 2. 计算安全采样率（最大边限制为 2048 像素，保证高清细节同时杜绝 OOM）
        val maxDim = maxOf(rawWidth, rawHeight)
        var inSampleSize = 1
        val reqDim = 2048
        while ((maxDim / inSampleSize) > reqDim) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decodedBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalArgumentException("无法打开指定图片数据流")

        // 3. 如有旋转角度，执行旋转纠偏
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
            }
            val rotatedBitmap = Bitmap.createBitmap(
                decodedBitmap,
                0,
                0,
                decodedBitmap.width,
                decodedBitmap.height,
                matrix,
                true
            )
            if (rotatedBitmap != decodedBitmap) {
                decodedBitmap.recycle()
            }
            return rotatedBitmap
        }

        return decodedBitmap
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
