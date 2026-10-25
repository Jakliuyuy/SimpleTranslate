package com.translate.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.translate.app.data.model.Language
import com.translate.app.ui.screens.ConversationScreen
import com.translate.app.ui.screens.HistoryFavoritesScreen
import com.translate.app.ui.screens.MainTranslateScreen
import com.translate.app.ui.screens.SettingsScreen
import com.translate.app.ui.theme.SimpleTranslateTheme
import com.translate.app.ui.viewmodel.TranslateViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: TranslateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SimpleTranslateTheme {
                MainAppContainer(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContainer(viewModel: TranslateViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var onSpeechResultCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    // 语音识别回调
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                onSpeechResultCallback?.invoke(spokenText)
            }
        }
    }

    // 录音权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSpeechRecognition(
                launcher = speechRecognizerLauncher,
                language = viewModel.sourceLanguage.value
            )
        }
    }

    fun launchVoiceInput(targetLang: Language, onResult: (String) -> Unit) {
        onSpeechResultCallback = onResult
        startSpeechRecognition(
            launcher = speechRecognizerLauncher,
            language = targetLang
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Translate, contentDescription = "翻译") },
                    label = { Text("翻译") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "对话") },
                    label = { Text("对话") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.History, contentDescription = "历史与收藏") },
                    label = { Text("历史/收藏") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                    label = { Text("设置/API") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (selectedTab) {
                0 -> MainTranslateScreen(
                    viewModel = viewModel,
                    onVoiceInputClick = {
                        launchVoiceInput(viewModel.sourceLanguage.value) { spoken ->
                            viewModel.onInputTextChanged(spoken)
                        }
                    }
                )
                1 -> ConversationScreen(
                    viewModel = viewModel,
                    onVoiceInputForLanguage = { lang, onResult ->
                        launchVoiceInput(lang, onResult)
                    }
                )
                2 -> HistoryFavoritesScreen(
                    viewModel = viewModel,
                    onSelectItem = { selectedTab = 0 }
                )
                3 -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

private fun startSpeechRecognition(
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
    language: Language
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        val langTag = when (language.code) {
            "zh" -> "zh-CN"
            "en" -> "en-US"
            "ja" -> "ja-JP"
            "ko" -> "ko-KR"
            else -> Locale.getDefault().toLanguageTag()
        }
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话…")
    }
    try {
        launcher.launch(intent)
    } catch (_: Exception) {
        // 部分模拟器或定制系统可能无语音识别服务组件
    }
}
