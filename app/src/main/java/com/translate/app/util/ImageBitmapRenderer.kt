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
            val displayText = if (block.translatedText.isNotBlank()) block.translatedText else block.originalText
            if (displayText.isBlank()) return@forEach

            val rect = block.boundingBox
            val width = max(10, rect.width())
            val height = max(10, rect.height())

            // 1. 绘制背景圆角遮罩
            bgPaint.color = block.backgroundColor
            bgPaint.alpha = 240 // 约 94% 不透明度，完美覆盖原英文同时融入背景
            val cornerRadius = min(width, height) * 0.08f
            canvas.drawRoundRect(
                RectF(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat()),
                cornerRadius,
                cornerRadius,
                bgPaint
            )

            // 2. 自适应计算文本排版（摆脱 48f 硬编码限制，支持大图与大招牌高保真渲染）
            val paddingH = max(2f, width * 0.04f)
            val paddingV = max(2f, height * 0.04f)
            val availWidth = max(8, (width - paddingH * 2).toInt())
            val availHeight = max(8f, height - paddingV * 2)

            var bestLayout: StaticLayout? = null
            var textSize = min(height * 0.75f, width * 0.85f).coerceAtLeast(10f)
            val minTextSize = 10f

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = block.textColor
                isFakeBoldText = true
            }

            while (textSize >= minTextSize) {
                textPaint.textSize = textSize
                val layout = StaticLayout.Builder.obtain(displayText, 0, displayText.length, textPaint, availWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
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
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .build()
            }

            // 3. 严格边界裁剪与居中绘制（彻底杜绝文字溢出框外涂抹原图）
            val offsetY = rect.top + paddingV + max(0f, (availHeight - bestLayout.height) / 2f)
            canvas.save()
            canvas.clipRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())
            canvas.translate(rect.left + paddingH, offsetY)
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
