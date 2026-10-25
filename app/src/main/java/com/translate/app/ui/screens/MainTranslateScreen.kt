package com.translate.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.translate.app.data.model.Language
import com.translate.app.ui.components.FullscreenDisplayDialog
import com.translate.app.ui.components.InputCard
import com.translate.app.ui.components.LanguagePickerSheet
import com.translate.app.ui.components.LanguageSelectorBar
import com.translate.app.ui.components.ResultCard
import com.translate.app.ui.theme.StarYellow
import com.translate.app.ui.viewmodel.TranslateViewModel

@Composable
fun MainTranslateScreen(
    viewModel: TranslateViewModel,
    onVoiceInputClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val inputText by viewModel.inputText.collectAsState()
    val translationResult by viewModel.translationResult.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val sourceLanguage by viewModel.sourceLanguage.collectAsState()
    val targetLanguage by viewModel.targetLanguage.collectAsState()
    val historyList by viewModel.historyList.collectAsState()
    val fullscreenText by viewModel.fullscreenText.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val isCurrentFavorite by viewModel.isCurrentResultFavorite.collectAsState()

    var showSourceSheet by remember { mutableStateOf(false) }
    var showTargetSheet by remember { mutableStateOf(false) }

    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // 全屏展示弹窗
    if (fullscreenText != null) {
        FullscreenDisplayDialog(
            text = fullscreenText!!,
            onDismiss = { viewModel.dismissFullscreen() }
        )
    }

    // 源语言选择 Sheet
    if (showSourceSheet) {
        LanguagePickerSheet(
            title = "选择源语言",
            selectedLanguage = sourceLanguage,
            supportedLanguages = Language.SUPPORTED_SOURCE_LANGUAGES,
            onSelectLanguage = { viewModel.setSourceLanguage(it) },
            onDismiss = { showSourceSheet = false }
        )
    }

    // 目标语言选择 Sheet
    if (showTargetSheet) {
        LanguagePickerSheet(
            title = "选择目标语言",
            selectedLanguage = targetLanguage,
            supportedLanguages = Language.SUPPORTED_TARGET_LANGUAGES,
            onSelectLanguage = { viewModel.setTargetLanguage(it) },
            onDismiss = { showTargetSheet = false }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            // 语言选择与对调条
            LanguageSelectorBar(
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                onSourceClick = { showSourceSheet = true },
                onTargetClick = { showTargetSheet = true },
                onSwapClick = { viewModel.swapLanguages() }
            )
        }

        item {
            // 原文输入卡片
            InputCard(
                text = inputText,
                onTextChange = { viewModel.onInputTextChanged(it) },
                onClearClick = { viewModel.clearInput() },
                onPasteClick = {
                    val clip = clipboardManager.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                        if (pasteText.isNotBlank()) {
                            viewModel.onInputTextChanged(pasteText)
                            Toast.makeText(context, "已粘贴剪贴板内容", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onVoiceClick = onVoiceInputClick,
                onTtsClick = {
                    viewModel.speak(inputText, sourceLanguage) { err ->
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                },
                isSpeaking = isSpeaking
            )
        }

        item {
            // 译文结果卡片
            ResultCard(
                result = translationResult,
                isLoading = isLoading,
                isFavorite = isCurrentFavorite,
                isSpeaking = isSpeaking,
                onTtsClick = {
                    translationResult?.let { res ->
                        viewModel.speak(res.translatedText, targetLanguage) { err ->
                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onCopyClick = {
                    translationResult?.let { res ->
                        val clip = ClipData.newPlainText("TranslateResult", res.translatedText)
                        clipboardManager.setPrimaryClip(clip)
                        Toast.makeText(context, "已复制译文", Toast.LENGTH_SHORT).show()
                    }
                },
                onFavoriteClick = {
                    viewModel.toggleCurrentResultFavorite()
                    Toast.makeText(context, "已更新收藏状态", Toast.LENGTH_SHORT).show()
                },
                onFullscreenClick = {
                    translationResult?.let { res ->
                        viewModel.showFullscreen(res.translatedText)
                    }
                },
                onShareClick = {
                    translationResult?.let { res ->
                        val shareText = "${res.originalText}\n\n译文:\n${res.translatedText}"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享翻译内容"))
                    }
                }
            )
        }

        // 历史记录标题与最近列表
        if (historyList.isNotEmpty() && inputText.isBlank()) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "最近翻译",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(historyList.take(6), key = { it.id }) { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.selectHistoryItem(item) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.sourceText,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.translatedText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { viewModel.toggleFavorite(item) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "收藏",
                                tint = if (item.isFavorite) StarYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
