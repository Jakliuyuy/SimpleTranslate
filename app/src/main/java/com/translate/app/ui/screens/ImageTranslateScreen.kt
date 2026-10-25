package com.translate.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.translate.app.data.model.Language
import com.translate.app.data.model.TranslatedBlock
import com.translate.app.ui.components.LanguagePickerSheet
import com.translate.app.ui.viewmodel.ImageTranslateViewModel
import com.translate.app.util.ImageOverlayHelper
import kotlin.math.max
import kotlin.math.min

/**
 * 原位实景图片翻译专属全屏界面 (Google 翻译同款 In-place AR Overlay 体验)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageTranslateScreen(
    imageUri: Uri,
    onClose: () -> Unit,
    onImportTextToMain: ((String) -> Unit)? = null,
    viewModel: ImageTranslateViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()

    // 语种选择器弹窗状态
    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // 视口手势控制状态：缩放比例 (1.0x - 5.0x) 与平移偏移量
    var userScale by remember { mutableFloatStateOf(1.0f) }
    var userOffset by remember { mutableStateOf(Offset.Zero) }

    // 页面初始化时加载并识别图片
    LaunchedEffect(imageUri) {
        userScale = 1.0f
        userOffset = Offset.Zero
        viewModel.loadImageAndTranslate(context, imageUri)
    }

    // 物理返回键处理
    BackHandler {
        if (uiState.selectedBlockId != null) {
            viewModel.selectBlock(null)
        } else {
            onClose()
        }
    }

    // 语言选择 BottomSheet
    if (showSourcePicker) {
        LanguagePickerSheet(
            title = "选择图片原文字语言",
            selectedLanguage = uiState.sourceLanguage,
            supportedLanguages = Language.SUPPORTED_SOURCE_LANGUAGES,
            onSelectLanguage = { lang ->
                viewModel.setSourceLanguage(lang)
                showSourcePicker = false
            },
            onDismiss = { showSourcePicker = false }
        )
    }

    if (showTargetPicker) {
        LanguagePickerSheet(
            title = "选择目标翻译语言",
            selectedLanguage = uiState.targetLanguage,
            supportedLanguages = Language.SUPPORTED_TARGET_LANGUAGES,
            onSelectLanguage = { lang ->
                viewModel.setTargetLanguage(lang)
                showTargetPicker = false
            },
            onDismiss = { showTargetPicker = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)) // 高沉浸感暗黑视口底色
    ) {
        // ==========================================
        // 1. 中间手势交互视口与原位贴字渲染层
        // ==========================================
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()
            val bitmap = uiState.originalBitmap

            val currentScale by rememberUpdatedState(userScale)
            val currentOffset by rememberUpdatedState(userOffset)
            val layoutCache = remember { HashMap<String, Pair<android.text.StaticLayout, Float>>() }

            if (bitmap != null) {
                val fit = remember(bitmap.width, bitmap.height, containerWidth, containerHeight) {
                    ImageOverlayHelper.computeFitTransform(
                        bitmap.width,
                        bitmap.height,
                        containerWidth,
                        containerHeight
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(fit, containerWidth, containerHeight) {
                            // 双击缩放与单击命中检测
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    if (currentScale > 1.2f) {
                                        userScale = 1.0f
                                        userOffset = Offset.Zero
                                    } else {
                                        userScale = 2.5f
                                        val focusX = (containerWidth / 2f - tapOffset.x) * 1.5f
                                        val focusY = (containerHeight / 2f - tapOffset.y) * 1.5f
                                        userOffset = Offset(focusX, focusY)
                                    }
                                },
                                onTap = { tapOffset ->
                                    // 逆向计算触摸点在原图坐标系下的像素位置
                                    val imagePoint = ImageOverlayHelper.mapScreenPointToImage(
                                        screenX = tapOffset.x,
                                        screenY = tapOffset.y,
                                        fit = fit,
                                        userScale = currentScale,
                                        userOffsetX = currentOffset.x,
                                        userOffsetY = currentOffset.y,
                                        containerWidth = containerWidth,
                                        containerHeight = containerHeight
                                    )
                                    val tappedBlock = ImageOverlayHelper.findBlockAtPoint(
                                        blocks = uiState.blocks,
                                        imagePointX = imagePoint.x,
                                        imagePointY = imagePoint.y
                                    )
                                    viewModel.selectBlock(tappedBlock)
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            // 双指缩放和平移手势
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (userScale * zoom).coerceIn(1.0f, 5.0f)
                                userScale = newScale
                                if (newScale <= 1.0f) {
                                    userOffset = Offset.Zero
                                } else {
                                    val maxOffsetX = (containerWidth * (newScale - 1f)) / 2f + 100f
                                    val maxOffsetY = (containerHeight * (newScale - 1f)) / 2f + 100f
                                    val nextX = (userOffset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                    val nextY = (userOffset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    userOffset = Offset(nextX, nextY)
                                }
                            }
                        }
                ) {
                    drawIntoCanvas { canvas ->
                        val nativeCanvas = canvas.nativeCanvas

                        // 保存初始画布状态并应用缩放、平移矩阵
                        nativeCanvas.save()
                        nativeCanvas.translate(containerWidth / 2f + userOffset.x, containerHeight / 2f + userOffset.y)
                        nativeCanvas.scale(userScale, userScale)
                        nativeCanvas.translate(-containerWidth / 2f, -containerHeight / 2f)
                        nativeCanvas.translate(fit.offsetX, fit.offsetY)
                        nativeCanvas.scale(fit.scale, fit.scale)

                        // 1. 绘制原图 Bitmap
                        nativeCanvas.drawBitmap(bitmap, 0f, 0f, null)

                        // 2. 原位贴字覆盖层渲染
                        val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            style = android.graphics.Paint.Style.FILL
                        }

                        val highlightPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 3f / fit.scale
                            color = android.graphics.Color.parseColor("#3B82F6") // Primary 品牌蓝高亮
                        }

                        val subtleBoxPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 1.5f / fit.scale
                            color = android.graphics.Color.parseColor("#4B5563")
                            alpha = 100
                        }

                        uiState.blocks.forEach { block ->
                            val isSelected = block.id == uiState.selectedBlockId
                            val rect = block.boundingBox
                            val width = max(10, rect.width())
                            val height = max(10, rect.height())

                            if (uiState.showTranslationOverlay) {
                                val rawDisplayText = if (block.translatedText.isNotBlank()) block.translatedText else block.originalText
                                if (rawDisplayText.isNotBlank()) {
                                    val displayText = ImageOverlayHelper.normalizeParagraphText(rawDisplayText)
                                    // 1. 矩形向外安全膨胀 3px，彻底物理擦除原图的英文字体墨迹和边缘抗锯齿
                                    val expRect = ImageOverlayHelper.expandRect(
                                        rect = rect,
                                        outsetX = ImageOverlayHelper.DEFAULT_OUTSET_PX,
                                        outsetY = ImageOverlayHelper.DEFAULT_OUTSET_PX,
                                        maxWidth = bitmap.width,
                                        maxHeight = bitmap.height
                                    )
                                    val expWidth = max(10, expRect.width())
                                    val expHeight = max(10, expRect.height())

                                    // 绘制 100% 完全不透明背景遮罩覆盖层 (alpha = 255，彻底杜绝文字透过重叠乱码)
                                    bgPaint.color = block.backgroundColor
                                    bgPaint.alpha = ImageOverlayHelper.MASK_ALPHA

                                    // 原文彻底擦除：优先按行 (lines) 精确覆盖每一个文本行，绝不溢出污染气泡外空白区
                                    if (block.lineBoxes.isNotEmpty()) {
                                        for (lBox in block.lineBoxes) {
                                            val expLine = ImageOverlayHelper.expandOverlayRect(
                                                rect = lBox,
                                                outsetX = ImageOverlayHelper.DEFAULT_OUTSET_PX,
                                                outsetY = ImageOverlayHelper.DEFAULT_OUTSET_PX,
                                                maxWidth = bitmap.width,
                                                maxHeight = bitmap.height
                                            )
                                            val lineCornerRadius = min(expLine.width, expLine.height) * 0.12f
                                            nativeCanvas.drawRoundRect(
                                                expLine.left.toFloat(),
                                                expLine.top.toFloat(),
                                                expLine.right.toFloat(),
                                                expLine.bottom.toFloat(),
                                                lineCornerRadius,
                                                lineCornerRadius,
                                                bgPaint
                                            )
                                        }
                                    } else {
                                        val cornerRadius = min(expWidth, expHeight) * 0.06f
                                        nativeCanvas.drawRoundRect(
                                            expRect.left.toFloat(),
                                            expRect.top.toFloat(),
                                            expRect.right.toFloat(),
                                            expRect.bottom.toFloat(),
                                            cornerRadius,
                                            cornerRadius,
                                            bgPaint
                                        )
                                    }

                                    // 计算自适应排版文本（利用排版缓存，杜绝手势重绘频繁 GC 掉帧）
                                    // 严格对齐：内边距微量恒定 (2px)，确保译文左边界与原文字左边界精确一一对应
                                    val paddingH = 2f
                                    val paddingV = 2f
                                    val availWidth = max(8, (expWidth - paddingH * 2).toInt())
                                    val availHeight = max(8f, expHeight - paddingV * 2)

                                    val origLineCount = max(1, block.lineBoxes.size)
                                    val cacheKey = "${block.id}_${displayText}_${expWidth}_${expHeight}_${block.textColor}_${origLineCount}"
                                    val cached = layoutCache[cacheKey]

                                    val (bestLayout, offsetYRel) = if (cached != null) {
                                        cached
                                    } else {
                                        val textPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                            color = block.textColor
                                            isFakeBoldText = true
                                        }

                                        // 调用 ImageOverlayHelper.calculateAutoFitFontSize 动态估算基准字号，避免多行被压缩或短文本膨胀
                                        val targetBaseSize = ImageOverlayHelper.calculateAutoFitFontSize(
                                            text = displayText,
                                            boxWidth = availWidth.toFloat(),
                                            boxHeight = availHeight,
                                            lineCount = origLineCount,
                                            maxFontSize = 100f,
                                            minFontSize = 10f
                                        )

                                        var textSize = min(targetBaseSize * 1.15f, 100f)
                                        val minTextSize = 10f

                                        var selectedLayout: android.text.StaticLayout? = null
                                        while (textSize >= minTextSize) {
                                            textPaint.textSize = textSize
                                            val layout = android.text.StaticLayout.Builder.obtain(
                                                displayText,
                                                0,
                                                displayText.length,
                                                textPaint,
                                                availWidth
                                            )
                                                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL) // 自然左对齐，彻底告别诗歌居中
                                                .setIncludePad(false)
                                                .setLineSpacing(0f, 1.15f)
                                                .build()

                                            if (layout.height <= availHeight) {
                                                selectedLayout = layout
                                                break
                                            }
                                            val step = if (textSize > 48f) 4f else if (textSize > 24f) 2f else 1f
                                            textSize -= step
                                        }

                                        val finalLayout = selectedLayout ?: run {
                                            textPaint.textSize = minTextSize
                                            android.text.StaticLayout.Builder.obtain(
                                                displayText,
                                                0,
                                                displayText.length,
                                                textPaint,
                                                availWidth
                                            )
                                                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                                                .setIncludePad(false)
                                                .setLineSpacing(0f, 1.15f)
                                                .build()
                                        }

                                        // 垂直方向：根据原图是否是多行段落及框高富余程度判断。多行段落紧贴顶部自然向下排布，单行居中
                                        val shouldAlignTop = ImageOverlayHelper.shouldAlignTop(origLineCount, expHeight.toFloat(), finalLayout.height.toFloat())
                                        val relY = ImageOverlayHelper.computeVerticalOffset(availHeight, finalLayout.height.toFloat(), paddingV, !shouldAlignTop)
                                        val pair = Pair(finalLayout, relY)
                                        layoutCache[cacheKey] = pair
                                        pair
                                    }

                                    // 译文底色覆写：确保译文实际占用区域有完整背景色
                                    val layoutW = bestLayout.width.toFloat()
                                    val layoutH = bestLayout.height.toFloat()
                                    val textBgLeft = expRect.left.toFloat()
                                    val textBgTop = expRect.top.toFloat() + offsetYRel - paddingV
                                    val textBgRight = min(expRect.right.toFloat(), textBgLeft + layoutW + paddingH * 2)
                                    val textBgBottom = min(expRect.bottom.toFloat(), textBgTop + layoutH + paddingV * 2)
                                    if (textBgRight > textBgLeft && textBgBottom > textBgTop) {
                                        val textCornerRadius = min(textBgRight - textBgLeft, textBgBottom - textBgTop) * 0.08f
                                        nativeCanvas.drawRoundRect(
                                            textBgLeft,
                                            textBgTop,
                                            textBgRight,
                                            textBgBottom,
                                            textCornerRadius,
                                            textCornerRadius,
                                            bgPaint
                                        )
                                    }

                                    // 严格边界裁剪与自然左对齐绘制
                                    nativeCanvas.save()
                                    nativeCanvas.clipRect(
                                        expRect.left.toFloat(),
                                        expRect.top.toFloat(),
                                        expRect.right.toFloat(),
                                        expRect.bottom.toFloat()
                                    )
                                    nativeCanvas.translate(expRect.left + paddingH, expRect.top + offsetYRel)
                                    bestLayout.draw(nativeCanvas)
                                    nativeCanvas.restore()
                                }
                            } else {
                                // 原文模式下绘制微透明轮廓线
                                nativeCanvas.drawRect(
                                    rect.left.toFloat(),
                                    rect.top.toFloat(),
                                    rect.right.toFloat(),
                                    rect.bottom.toFloat(),
                                    subtleBoxPaint
                                )
                            }

                            // 若当前 Block 被点击选中，绘制突出蓝色高亮光框
                            if (isSelected) {
                                val cornerRadius = min(width, height) * 0.08f
                                nativeCanvas.drawRoundRect(
                                    rect.left.toFloat() - 2f,
                                    rect.top.toFloat() - 2f,
                                    rect.right.toFloat() + 2f,
                                    rect.bottom.toFloat() + 2f,
                                    cornerRadius,
                                    cornerRadius,
                                    highlightPaint
                                )
                            }
                        }

                        nativeCanvas.restore()
                    }
                }
            }
        }

        // ==========================================
        // 2. 顶部悬浮控制栏 (透明毛玻璃美学)
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回按钮
            Surface(
                shape = CircleShape,
                color = Color(0x99000000),
                modifier = Modifier.size(44.dp)
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
            }

            // 语种切换胶囊按钮 (英语 -> 中文)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xCC1F2937),
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.sourceLanguage.displayName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showSourcePicker = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )

                    IconButton(
                        onClick = { viewModel.swapLanguages() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "互换语言",
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = uiState.targetLanguage.displayName,
                        color = Color(0xFF60A5FA),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showTargetPicker = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }

            // 右侧更多操作
            Surface(
                shape = CircleShape,
                color = Color(0x99000000),
                modifier = Modifier.size(44.dp)
            ) {
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("复制全部提取文本") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                viewModel.copyAllText(context)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("保存翻译后的图片") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                Toast.makeText(context, "正在合成并保存图片...", Toast.LENGTH_SHORT).show()
                                viewModel.saveTranslatedImageToGallery(context) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("重新翻译图片") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                viewModel.retranslateCurrentBlocks()
                            }
                        )

                        if (onImportTextToMain != null) {
                            DropdownMenuItem(
                                text = { Text("导入主输入框编辑") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    val allText = ImageOverlayHelper.formatAllBlocksText(uiState.blocks, includeTranslation = false)
                                    onImportTextToMain(allText)
                                    onClose()
                                }
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. 底部浮动控制栏 (显示译文 / 显示原文 对比切换)
        // ==========================================
        AnimatedVisibility(
            visible = uiState.selectedBlock == null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 翻译进度指示器
                if (uiState.isRetranslating) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xDD111827),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = Color(0xFF60A5FA),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "正在原位翻译 (${uiState.translatedBlockCount}/${uiState.totalBlockCount})...",
                                color = Color(0xFFE5E7EB),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 切换药丸组件：【显示译文】/【显示原文】
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xEE1E293B),
                    modifier = Modifier
                        .height(52.dp)
                        .border(1.dp, Color(0x339CA3AF), RoundedCornerShape(26.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isTranslationMode = uiState.showTranslationOverlay

                        // 显示译文
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    if (isTranslationMode) Color(0xFF2563EB)
                                    else Color.Transparent
                                )
                                .clickable { viewModel.setOverlayMode(true) }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "显示译文",
                                color = if (isTranslationMode) Color.White else Color(0xFF9CA3AF),
                                fontWeight = if (isTranslationMode) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }

                        // 显示原文
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    if (!isTranslationMode) Color(0xFF2563EB)
                                    else Color.Transparent
                                )
                                .clickable { viewModel.setOverlayMode(false) }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "显示原文",
                                color = if (!isTranslationMode) Color.White else Color(0xFF9CA3AF),
                                fontWeight = if (!isTranslationMode) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 4. 选中文本块详情浮层卡片 (底部弹出)
        // ==========================================
        AnimatedVisibility(
            visible = uiState.selectedBlock != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            uiState.selectedBlock?.let { block ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xF21F2937) // 半透明质感深灰背景
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        // 头部：文本块序号与关闭
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "文本块 #${block.index}",
                                color = Color(0xFF9CA3AF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            IconButton(
                                onClick = { viewModel.selectBlock(null) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭",
                                    tint = Color(0xFF9CA3AF)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 原文内容与操作
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "原文 (${uiState.sourceLanguage.displayName})",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = block.originalText,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    lineHeight = 20.sp
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(block.originalText))
                                        Toast.makeText(context, "已复制原文", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "复制原文",
                                        tint = Color(0xFF9CA3AF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.speakText(block.originalText, uiState.sourceLanguage)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "朗读原文",
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color(0x334B5563)
                        )

                        // 译文内容与操作
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "译文 (${uiState.targetLanguage.displayName})",
                                    color = Color(0xFF60A5FA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val translated = block.translatedText
                                Text(
                                    text = if (translated.isNotBlank()) translated else "正在翻译中...",
                                    color = if (translated.isNotBlank()) Color(0xFFF9FAFB) else Color(0xFF9CA3AF),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 22.sp
                                )
                            }

                            if (block.translatedText.isNotBlank()) {
                                Row {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(block.translatedText))
                                            Toast.makeText(context, "已复制译文", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "复制译文",
                                            tint = Color(0xFF9CA3AF),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            viewModel.speakText(block.translatedText, uiState.targetLanguage)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "朗读译文",
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 5. 加载遮罩与识别失败提示
        // ==========================================
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFF3B82F6),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = uiState.loadingMessage,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (uiState.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEE000000))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "图片识别提示",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = Color(0xFFD1D5DB),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        FilledTonalButton(onClick = onClose) {
                            Text("返回主页")
                        }
                    }
                }
            }
        }
    }
}
