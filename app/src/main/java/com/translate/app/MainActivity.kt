package com.translate.app

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.translate.app.data.model.Language
import com.translate.app.ui.components.ImagePickerBottomSheet
import com.translate.app.ui.screens.ConversationScreen
import com.translate.app.ui.screens.HistoryFavoritesScreen
import com.translate.app.ui.screens.MainTranslateScreen
import com.translate.app.ui.screens.SettingsScreen
import com.translate.app.ui.theme.SimpleTranslateTheme
import com.translate.app.ui.viewmodel.TranslateViewModel
import com.translate.app.util.OcrManager
import java.io.File
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
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var onSpeechResultCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var pendingSpeechLang by remember { mutableStateOf<Language?>(null) }

    // 图片翻译选择 Sheet
    var showImagePickerSheet by remember { mutableStateOf(false) }
    var currentCameraPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // ==========================================
    // 1. 语音输入 (STT) 回调与权限处理
    // ==========================================
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

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val lang = pendingSpeechLang ?: viewModel.sourceLanguage.value
            executeSpeechRecognition(context, speechRecognizerLauncher, lang)
        } else {
            Toast.makeText(context, "需要录音权限以进行语音输入，请在系统设置中授予", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchVoiceInput(targetLang: Language, onResult: (String) -> Unit) {
        onSpeechResultCallback = onResult
        pendingSpeechLang = targetLang

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            executeSpeechRecognition(context, speechRecognizerLauncher, targetLang)
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // ==========================================
    // 2. 图片/拍照翻译 (OCR) 回调与权限处理
    // ==========================================
    // 拍照完成回调
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentCameraPhotoUri != null) {
            Toast.makeText(context, "正在提取图片中的文字...", Toast.LENGTH_SHORT).show()
            OcrManager.recognizeText(
                context = context,
                imageUri = currentCameraPhotoUri!!,
                onSuccess = { extractedText ->
                    viewModel.onInputTextChanged(extractedText)
                    Toast.makeText(context, "已成功识别图片文字并为您翻译！", Toast.LENGTH_SHORT).show()
                },
                onError = { err ->
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // 相机权限申请
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCameraCapture(context) { uri ->
                currentCameraPhotoUri = uri
                takePhotoLauncher.launch(uri)
            }
        } else {
            Toast.makeText(context, "需要相机权限以进行拍照翻译，请在系统设置中授予", Toast.LENGTH_SHORT).show()
        }
    }

    // 相册选择图片回调
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            Toast.makeText(context, "正在识别相册图片文字...", Toast.LENGTH_SHORT).show()
            OcrManager.recognizeText(
                context = context,
                imageUri = uri,
                onSuccess = { extractedText ->
                    viewModel.onInputTextChanged(extractedText)
                    Toast.makeText(context, "已成功识别相册文字并为您翻译！", Toast.LENGTH_SHORT).show()
                },
                onError = { err ->
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    fun handleTakePhoto() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            startCameraCapture(context) { uri ->
                currentCameraPhotoUri = uri
                takePhotoLauncher.launch(uri)
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun handlePickGallery() {
        pickImageLauncher.launch("image/*")
    }

    // 弹窗组件：图片/拍照翻译选择
    if (showImagePickerSheet) {
        ImagePickerBottomSheet(
            onTakePhotoClick = { handleTakePhoto() },
            onPickImageClick = { handlePickGallery() },
            onDismiss = { showImagePickerSheet = false }
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
                    },
                    onImageTranslateClick = {
                        showImagePickerSheet = true
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

/**
 * 启动相机拍照临时文件准备
 */
private fun startCameraCapture(context: Context, onUriReady: (Uri) -> Unit) {
    try {
        val photoFile = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
        val photoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        onUriReady(photoUri)
    } catch (e: Exception) {
        Toast.makeText(context, "创建相机临时文件失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 启动系统语音识别，具备全语种映射与无语音服务异常容错
 */
private fun executeSpeechRecognition(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
    language: Language
) {
    val langTag = when (language.code.lowercase()) {
        "zh", "zh-cn", "zh-hans" -> "zh-CN"
        "cht", "zh-tw", "zh-hk", "zh-hant" -> "zh-TW"
        "en", "en-us" -> "en-US"
        "en-gb" -> "en-GB"
        "ja" -> "ja-JP"
        "ko" -> "ko-KR"
        "fr" -> "fr-FR"
        "de" -> "de-DE"
        "ru" -> "ru-RU"
        "es" -> "es-ES"
        "it" -> "it-IT"
        "pt" -> "pt-PT"
        "vi" -> "vi-VN"
        "th" -> "th-TH"
        "ar" -> "ar-SA"
        else -> Locale.getDefault().toLanguageTag()
    }

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话 (${language.displayName})…")
    }

    try {
        launcher.launch(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "未检测到系统语音识别组件，请在系统中安装支持语音识别的输入法（如 Google 语音输入或讯飞输入法）",
            Toast.LENGTH_LONG
        ).show()
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "启动语音识别失败: ${e.localizedMessage ?: "未知错误"}",
            Toast.LENGTH_SHORT
        ).show()
    }
}
