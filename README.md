# SimpleTranslate - 极简谷歌翻译 Android APP

> 一款基于现代 Android 技术栈（Jetpack Compose + Material Design 3）打造的极简、无广告翻译客户端，界面与交互体验对标 Google Translate，并深度集成了**百度翻译开放平台**商用 API。

---

## ✨ 核心特性

- 🌐 **多引擎无缝支持**：
  - **百度翻译开放平台**（默认推荐，已预置商用 API Key `wMHD_dber75qj3nn4okmvvqb0`，支持 AI 大模型翻译与经典通用翻译双模智能降级）
  - **网易有道智云**（支持单词音标、词性及词典详细释义）
  - **腾讯云机器翻译 (TMT)**（高并发、企业级高质量机器翻译）
  - **本地 Mock 引擎**（离线快速体验与调试）
- 🎨 **Google 极简现代美学**：
  - 纯正 Material 3 规范与 Google 标志性四色点缀。
  - 智能源语言自动检测（Auto Detect）与一键双向语言互换。
  - 文本快捷一键清空、剪贴板一键粘贴。
- 🔊 **自然语音朗读 (TTS)**：
  - 支持源文本与译文的高保真本地语音合成朗读。
- 🌟 **全屏大字展示 & 历史收藏**：
  - 支持将翻译结果一键全屏横向或纵向超大字体展示，专为出境问路、交流沟通量身打造。
  - 本地 SQLite 离线存储全部翻译历史记录，支持一键星标收藏与快速搜索。
- ⚙️ **动态与静态双重配置**：
  - 开发者可在代码全局常量集中维护。
  - 普通用户亦可在 APP 内【设置】面板随时动态填入/切换密钥，即改即生效。

---

## 🛠️ 技术架构

```
app
├── data
│   ├── api
│   │   ├── ApiConfig.kt                # 全局 API 预置常量 (已预置百度 Key)
│   │   ├── EngineFactory.kt            # 翻译引擎工厂
│   │   ├── ITranslationEngine.kt       # 统一翻译接口抽象
│   │   └── engine                      # 各大引擎实现
│   │       ├── BaiduTranslationEngine.kt   # 百度翻译实现 (AI大模型+VIP双引擎)
│   │       ├── YoudaoTranslationEngine.kt  # 网易有道实现
│   │       ├── TencentTranslationEngine.kt # 腾讯云实现
│   │       └── MockTranslationEngine.kt    # 本地离线引擎
│   ├── db                              # SQLite 数据库 (历史与收藏)
│   ├── model                           # 语言模型、引擎类型、翻译结果
│   └── repository                      # 偏好持久化与数据仓库
├── ui
│   ├── components                      # Material 3 自定义组件
│   ├── screens                         # 翻译主界面、会话模式、历史收藏、设置页
│   ├── theme                           # 动态配色与 Typography
│   └── viewmodel                       # MVVM 状态流管理 (StateFlow)
└── util                                # MD5/SHA256 加密工具、TTS 管理器
```

---

## 🚀 百度 API Key 集成与调用指南

### 1. 密钥预置说明
本项目在 [`ApiConfig.kt`](file:///root/Translate/app/src/main/java/com/translate/app/data/api/ApiConfig.kt) 中已默认将用户的商业密钥进行了固化：
```kotlin
const val DEFAULT_BAIDU_SECRET_KEY: String = "wMHD_dber75qj3nn4okmvvqb0"
```
且在偏好仓库中已将百度翻译设置为默认首选翻译服务。

### 2. 百度翻译 API 调用规范与机制
百度翻译开放平台采用双参数安全验证：
1. **APP ID**：17位纯数字，代表您的账户身份。
2. **Secret Key / API Key**：即您申请到的 `wMHD_dber75qj3nn4okmvvqb0`。

本客户端已实现双模式自动适配：
- **模式 1 (大模型翻译)**：通过 HTTP Header `Authorization: Bearer <API Key>` 向 `https://fanyi-api.baidu.com/ait/api/aiTextTranslate` 发送请求。
- **模式 2 (通用文本翻译)**：通过计算 $sign = \text{MD5}(appid + q + salt + key)$ 向 `https://fanyi-api.baidu.com/api/trans/vip/translate` 发送请求。

### 3. 如何激活与使用百度翻译
由于百度接口必须配合开发者的账户 **APP ID**（数字），只需：
1. 登录 [百度翻译开放平台-开发者信息](https://fanyi-api.baidu.com/) 复制您的 **APP ID**。
2. 在本 APP 的【设置】界面中点击“粘贴 APP ID”并点击“保存设置并测试连接”。
3. 提示“百度翻译连接成功！”即可开始畅享极速翻译！

更多细节请查阅 [BAIDU_TRANSLATE_GUIDE.md](file:///root/Translate/BAIDU_TRANSLATE_GUIDE.md)。

---

## 📦 自动打包与 GitHub Actions CI/CD

本项目已配置完善的 GitHub Actions 自动构建与发布流水线（[`.github/workflows/build-apk.yml`](file:///.github/workflows/build-apk.yml)）：
- 每次推送（Push）或打 Tag 会自动运行单测与构建；
- 自动生成可直接在手机上安装的 `Release APK` 与 `Debug APK`；
- 产物自动归档至 GitHub Releases 供用户一键下载。

### 本地命令行构建
```bash
# 运行单元测试
./gradlew testDebugUnitTest

# 打包 Debug APK
./gradlew assembleDebug

# 打包 Release 正式版 APK
./gradlew assembleRelease
```
构建成功后生成的安装包位于：`app/build/outputs/apk/release/app-release.apk`
