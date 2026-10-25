package com.translate.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.translate.app.data.model.TranslatedBlock
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * 图像离线覆写贴字渲染与相册持久化保存引擎
 */
object ImageBitmapRenderer {

    /**
     * 将翻译后的文本框原位绘制覆写在原图上，生成高清成品图片
     */
    fun renderOverlayOnBitmap(
        originalBitmap: Bitmap,
        blocks: List<TranslatedBlock>
    ): Bitmap {
        val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        blocks.forEach { block ->
            val rawDisplayText = if (block.translatedText.isNotBlank()) block.translatedText else block.originalText
            if (rawDisplayText.isBlank()) return@forEach
            val displayText = ImageOverlayHelper.normalizeParagraphText(rawDisplayText)

            val rect = block.boundingBox
            // 1. 矩形向外安全膨胀 2~4px，彻底物理擦除原图的英文字体墨迹和边缘抗锯齿
            val expRect = ImageOverlayHelper.expandRect(
                rect = rect,
                outsetX = ImageOverlayHelper.DEFAULT_OUTSET_PX,
                outsetY = ImageOverlayHelper.DEFAULT_OUTSET_PX,
                maxWidth = originalBitmap.width,
                maxHeight = originalBitmap.height
            )
            val expWidth = max(10, expRect.width())
            val expHeight = max(10, expRect.height())

            // 绘制 100% 完全不透明背景圆角遮罩 (alpha = 255，彻底杜绝文字透过重叠乱码)
            bgPaint.color = block.backgroundColor
            bgPaint.alpha = ImageOverlayHelper.MASK_ALPHA
            val cornerRadius = min(expWidth, expHeight) * 0.06f
            canvas.drawRoundRect(
                RectF(expRect.left.toFloat(), expRect.top.toFloat(), expRect.right.toFloat(), expRect.bottom.toFloat()),
                cornerRadius,
                cornerRadius,
                bgPaint
            )

            // 2. 自适应计算文本排版（基于原行数与行高动态自适应，左对齐自然向下排布）
            val paddingH = max(2f, expWidth * 0.03f)
            val paddingV = max(2f, expHeight * 0.03f)
            val availWidth = max(8, (expWidth - paddingH * 2).toInt())
            val availHeight = max(8f, expHeight - paddingV * 2)

            val origLineCount = max(1, block.lineBoxes.size)
            val avgLineHeight = expHeight.toFloat() / origLineCount
            val lineBasedSize = if (origLineCount > 1) {
                (avgLineHeight * 0.72f).coerceIn(12f, expHeight * 0.85f)
            } else {
                min(expHeight * 0.75f, expWidth * 0.85f).coerceAtLeast(10f)
            }

            var textSize = max(lineBasedSize, min(expHeight * 0.75f, expWidth * 0.85f)).coerceIn(10f, 120f)
            val minTextSize = 10f

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = block.textColor
                isFakeBoldText = true
            }

            var bestLayout: StaticLayout? = null
            while (textSize >= minTextSize) {
                textPaint.textSize = textSize
                val layout = StaticLayout.Builder.obtain(displayText, 0, displayText.length, textPaint, availWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL) // 自然左对齐
                    .setIncludePad(false)
                    .setLineSpacing(0f, 1.08f)
                    .build()

                if (layout.height <= availHeight) {
                    bestLayout = layout
                    break
                }
                val step = if (textSize > 64f) 6f else if (textSize > 32f) 3f else 1.5f
                textSize -= step
            }

            if (bestLayout == null) {
                textPaint.textSize = minTextSize
                bestLayout = StaticLayout.Builder.obtain(displayText, 0, displayText.length, textPaint, availWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .setLineSpacing(0f, 1.08f)
                    .build()
            }

            // 3. 严格边界裁剪与自然左对齐排布
            val isSingleLine = bestLayout.lineCount == 1
            val relY = ImageOverlayHelper.computeVerticalOffset(availHeight, bestLayout.height.toFloat(), paddingV, isSingleLine)
            val offsetY = expRect.top + relY

            canvas.save()
            canvas.clipRect(expRect.left.toFloat(), expRect.top.toFloat(), expRect.right.toFloat(), expRect.bottom.toFloat())
            canvas.translate(expRect.left + paddingH, offsetY)
            bestLayout.draw(canvas)
            canvas.restore()
        }

        return resultBitmap
    }

    /**
     * 保存合成后的翻译图片到系统相册
     */
    fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        title: String = "SimpleTranslate_${System.currentTimeMillis()}"
    ): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$title.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SimpleTranslate")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        var imageUri: Uri? = null
        try {
            imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri != null) {
                resolver.openOutputStream(imageUri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                return imageUri
            }
        } catch (_: Exception) {
            // 回退到应用专属私有目录
        }

        // 回退逻辑
        try {
            val picturesDir = File(context.getExternalFilesDir(null), "Pictures")
            picturesDir.mkdirs()
            val file = File(picturesDir, "$title.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            return Uri.fromFile(file)
        } catch (_: Exception) {
            return null
        }
    }
}
