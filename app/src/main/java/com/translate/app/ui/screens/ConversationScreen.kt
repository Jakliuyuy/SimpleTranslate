package com.translate.app.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.translate.app.SimpleTranslateApp
import com.translate.app.data.model.Language
import com.translate.app.ui.components.LanguagePickerSheet
import com.translate.app.ui.viewmodel.TranslateViewModel
import kotlinx.coroutines.launch

data class ConversationMessage(
    val id: Long = System.currentTimeMillis(),
    val isUserA: Boolean, // true 为甲方语言，false 为乙方语言
    val originalText: String,
    val translatedText: String,
    val fromLang: Language,
    val toLang: Language
)

@Composable
fun ConversationScreen(
    viewModel: TranslateViewModel,
    onVoiceInputForLanguage: (Language) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repo = SimpleTranslateApp.instance.translationRepository

    var langA by remember { mutableStateOf(Language.ZH) }
    var langB by remember { mutableStateOf(Language.EN) }

    var showPickerA by remember { mutableStateOf(false) }
    var showPickerB by remember { mutableStateOf(false) }

    val messages = remember {
        mutableStateListOf(
            ConversationMessage(
                isUserA = true,
                originalText = "你好！很高兴认识你。",
                translatedText = "Hello! Nice to meet you.",
                fromLang = Language.ZH,
                toLang = Language.EN
            )
        )
    }

    var manualInputText by remember { mutableStateOf("") }
    var activeSpeakerIsA by remember { mutableStateOf(true) }

    if (showPickerA) {
        LanguagePickerSheet(
            title = "选择己方语言",
            selectedLanguage = langA,
            supportedLanguages = Language.SUPPORTED_TARGET_LANGUAGES,
            onSelectLanguage = { langA = it },
            onDismiss = { showPickerA = false }
        )
    }

    if (showPickerB) {
        LanguagePickerSheet(
            title = "选择对方语言",
            selectedLanguage = langB,
            supportedLanguages = Language.SUPPORTED_TARGET_LANGUAGES,
            onSelectLanguage = { langB = it },
            onDismiss = { showPickerB = false }
        )
    }

    fun sendMessage(text: String, isA: Boolean) {
        if (text.isBlank()) return
        val from = if (isA) langA else langB
        val to = if (isA) langB else langA

        coroutineScope.launch {
            val res = repo.translate(text, from, to)
            if (res.isSuccess) {
                messages.add(
                    ConversationMessage(
                        isUserA = isA,
                        originalText = text,
                        translatedText = res.translatedText,
                        fromLang = from,
                        toLang = to
                    )
                )
                // 自动朗读译文
                viewModel.speak(res.translatedText, to)
            } else {
                Toast.makeText(context, res.errorMessage ?: "翻译失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // 顶部双方语言指示栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 甲方语言
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showPickerA = true }
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = langA.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }

            Text(
                text = "双人互译",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 乙方语言
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showPickerB = true }
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = langB.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 对话气泡流
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val alignment = if (msg.isUserA) Alignment.Start else Alignment.End
                val bubbleColor = if (msg.isUserA) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = alignment
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (msg.isUserA) 4.dp else 16.dp,
                                    bottomEnd = if (msg.isUserA) 16.dp else 4.dp
                                )
                            )
                            .background(bubbleColor)
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = msg.originalText,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = msg.translatedText,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(
                                    onClick = { viewModel.speak(msg.translatedText, msg.toLang) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = "朗读",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 快捷手动文字输入条
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = manualInputText,
                onValueChange = { manualInputText = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(if (activeSpeakerIsA) "以 ${langA.displayName} 输入..." else "以 ${langB.displayName} 输入...")
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )

            IconButton(
                onClick = {
                    if (manualInputText.isNotBlank()) {
                        sendMessage(manualInputText, activeSpeakerIsA)
                        manualInputText = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "发送",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 底部双麦克风按钮（对话核心）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 甲方语音按钮
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    activeSpeakerIsA = true
                    onVoiceInputForLanguage(langA)
                }
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "说 ${langA.displayName}",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = langA.displayName,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 乙方语音按钮
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    activeSpeakerIsA = false
                    onVoiceInputForLanguage(langB)
                }
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "说 ${langB.displayName}",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = langB.displayName,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
