package com.translate.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.translate.app.SimpleTranslateApp
import com.translate.app.data.model.TranslationEngineType
import com.translate.app.ui.theme.ErrorRed
import com.translate.app.ui.theme.SuccessGreen
import com.translate.app.ui.viewmodel.TranslateViewModel

@Composable
fun SettingsScreen(
    viewModel: TranslateViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefRepo = SimpleTranslateApp.instance.preferenceRepository

    val selectedEngine by viewModel.selectedEngine.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Google 翻译字段
    var googleMode by remember { mutableStateOf(prefRepo.googleMode) }
    var googleProxyUrl by remember { mutableStateOf(prefRepo.googleProxyUrl) }

    // 百度字段
    var baiduAppId by remember { mutableStateOf(prefRepo.baiduAppId) }
    var baiduSecretKey by remember { mutableStateOf(prefRepo.baiduSecretKey) }
    var showBaiduSecret by remember { mutableStateOf(false) }

    // 有道字段
    var youdaoAppKey by remember { mutableStateOf(prefRepo.youdaoAppKey) }
    var youdaoAppSecret by remember { mutableStateOf(prefRepo.youdaoAppSecret) }
    var showYoudaoSecret by remember { mutableStateOf(false) }

    // 腾讯云字段
    var tencentSecretId by remember { mutableStateOf(prefRepo.tencentSecretId) }
    var tencentSecretKey by remember { mutableStateOf(prefRepo.tencentSecretKey) }
    var showTencentSecret by remember { mutableStateOf(false) }

    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    val scrollState = rememberScrollState()

    fun openUrl(url: String) {
        if (url.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题卡片
        Text(
            text = "翻译引擎与 API 配置",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "支持国内主流翻译 API 平台，您可以直接在下方选择引擎并填入申请到的密钥，也可以由开发者统一在代码常量中配置。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 引擎单选卡片列表
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "选择当前使用的翻译服务",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            TranslationEngineType.entries.forEach { engine ->
                val isSelected = engine == selectedEngine
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else Color.Transparent
                        )
                        .clickable { viewModel.setEngineType(engine) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { viewModel.setEngineType(engine) },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = engine.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = engine.description,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 密钥配置详情卡片 (根据选中动态展开)
        AnimatedVisibility(visible = selectedEngine == TranslationEngineType.GOOGLE) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Google 翻译网络连接模式",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "免Key服务",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Text(
                    text = "Google 翻译无需申请任何 API Key 即可使用！针对国内用户，本应用已为您搭建专用的海外服务器中转代理通道；海外用户亦可选择直连官方接口。",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 模式选择单选区
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (googleMode == "proxy") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else Color.Transparent
                        )
                        .clickable { googleMode = "proxy" }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = googleMode == "proxy",
                        onClick = { googleMode = "proxy" },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "服务器中转代理 (推荐国内网络)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "经由美国专用高速服务器代理向 Google 请求，国内无需翻墙开箱即用",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (googleMode == "direct") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else Color.Transparent
                        )
                        .clickable { googleMode = "direct" }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = googleMode == "direct",
                        onClick = { googleMode = "direct" },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "官方接口直连 (需国际网络/海外)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "手机直接向 translate.googleapis.com 发送请求，适合海外用户或开启代理时",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 若为代理模式，显示代理服务器地址输入框
                if (googleMode == "proxy") {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = googleProxyUrl,
                        onValueChange = { googleProxyUrl = it.trim() },
                        label = { Text("中转服务器地址") },
                        placeholder = { Text("http://129.151.9.55:8787") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    val clip = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                    if (!clip.isNullOrBlank()) {
                                        googleProxyUrl = clip
                                        Toast.makeText(context, "已粘贴代理地址", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "粘贴代理地址")
                                }
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "恢复默认地址 (129.151.9.55:8787)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.clickable {
                                googleProxyUrl = com.translate.app.data.api.ApiConfig.DEFAULT_GOOGLE_PROXY_URL
                                Toast.makeText(context, "已重置为默认美国服务器代理", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = selectedEngine == TranslationEngineType.BAIDU) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "百度翻译开放平台密钥",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "申请地址 ↗",
                        style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.clickable { openUrl("https://fanyi-api.baidu.com/") }
                    )
                }

                Text(
                    text = "登录百度翻译开放平台 ->【开发者信息】即可查看您的 APP ID 与 密钥 (Secret Key / API Key)。当前系统已为您预置百度 API Key，只需填入您的数字 APP ID 即可激活！",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = baiduAppId,
                    onValueChange = { baiduAppId = it.trim() },
                    label = { Text("百度 APP ID (必填，17位纯数字)") },
                    placeholder = { Text("例如: 20241025000000000") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = {
                            val clip = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                            if (!clip.isNullOrBlank()) {
                                baiduAppId = clip
                                Toast.makeText(context, "已粘贴 APP ID", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "粘贴 APP ID")
                        }
                    }
                )

                OutlinedTextField(
                    value = baiduSecretKey,
                    onValueChange = { baiduSecretKey = it.trim() },
                    label = { Text("百度 密钥 (API Key / Secret Key)") },
                    placeholder = { Text("wMHD_dber75qj3nn4okmvvqb0") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showBaiduSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val clip = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                if (!clip.isNullOrBlank()) {
                                    baiduSecretKey = clip
                                    Toast.makeText(context, "已粘贴密钥", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "粘贴密钥")
                            }
                            IconButton(onClick = { showBaiduSecret = !showBaiduSecret }) {
                                Icon(
                                    imageVector = if (showBaiduSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    }
                )
            }
        }

        AnimatedVisibility(visible = selectedEngine == TranslationEngineType.YOUDAO) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "网易有道智云密钥",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "申请地址 ↗",
                        style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.clickable { openUrl("https://ai.youdao.com/") }
                    )
                }

                Text(
                    text = "登录有道智云控制台 ->【我的应用】创建应用后即可获取 appKey 与 appSecret。",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = youdaoAppKey,
                    onValueChange = { youdaoAppKey = it },
                    label = { Text("有道 应用ID (appKey)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = youdaoAppSecret,
                    onValueChange = { youdaoAppSecret = it },
                    label = { Text("有道 应用密钥 (appSecret)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showYoudaoSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showYoudaoSecret = !showYoudaoSecret }) {
                            Icon(
                                imageVector = if (showYoudaoSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        }

        AnimatedVisibility(visible = selectedEngine == TranslationEngineType.TENCENT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "腾讯云机器翻译密钥",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "申请地址 ↗",
                        style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.clickable { openUrl("https://cloud.tencent.com/product/tmt") }
                    )
                }

                Text(
                    text = "登录腾讯云控制台 ->【访问管理】->【API密钥管理】即可获取 SecretId 和 SecretKey。",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = tencentSecretId,
                    onValueChange = { tencentSecretId = it },
                    label = { Text("腾讯云 SecretId") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = tencentSecretKey,
                    onValueChange = { tencentSecretKey = it },
                    label = { Text("腾讯云 SecretKey") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showTencentSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showTencentSecret = !showTencentSecret }) {
                            Icon(
                                imageVector = if (showTencentSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        }

        // 保存并测试连接按钮
        Button(
            onClick = {
                // 保存各引擎配置
                viewModel.saveGoogleConfig(googleMode, googleProxyUrl)
                viewModel.saveBaiduConfig(baiduAppId, baiduSecretKey)
                viewModel.saveYoudaoConfig(youdaoAppKey, youdaoAppSecret)
                viewModel.saveTencentConfig(tencentSecretId, tencentSecretKey)
                Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
                viewModel.testConnection()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("正在测试 API 连通性...")
            } else {
                Icon(Icons.Default.Speed, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存设置并测试连接")
            }
        }

        // 测试结果卡片
        if (testResult != null) {
            val (isSuccess, message) = testResult!!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSuccess) SuccessGreen.copy(alpha = 0.12f) else ErrorRed.copy(alpha = 0.12f))
                    .border(
                        width = 1.dp,
                        color = if (isSuccess) SuccessGreen else ErrorRed,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isSuccess) SuccessGreen else ErrorRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isSuccess) SuccessGreen else ErrorRed,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }

        // 开发者说明卡片
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "开发者替换指引",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "您申请到 API 密钥发给开发者后，开发者可以直接在以下文件集中修改全局默认常量：\napp/src/main/java/com/translate/app/data/api/ApiConfig.kt\n重新打包后默认即为您的正式商业密钥！",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
