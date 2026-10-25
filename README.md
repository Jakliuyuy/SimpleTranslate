# SimpleTranslate - 极简谷歌翻译 Android APP

> 一款基于现代 Android 技术栈（Jetpack Compose + Material Design 3）打造的极简、无广告翻译客户端，界面与交互体验对标 Google Translate，全面支持**内置免Key谷歌翻译（服务器代理与海外直连双模）**、**相机拍照与相册图片文字提取翻译（ML Kit离线OCR）**、**高保真语音引擎（TTS朗读与STT录音输入）**，并深度集成了**百度翻译开放平台**商用 API。

---

## ✨ 核心特性

- 🌐 **内置 Google 翻译引擎（免 Key / 独家双模支持）**：
  - **服务器中转代理模式（默认推荐）**：专为国内网络环境设计，手机端请求发送至美国专用服务器中转代理（预置 `http://129.151.9.55:8787`），由服务端直连 Google 官方翻译端点并标准化返回，**免翻墙、免注册、免 API Key，开箱即用**！
  - **官方接口直连模式**：手机直接请求 Google 翻译公开端点，适合身处海外或开启全局梯子的用户。
  - **中转服务端模块**：位于 `/server` 目录下，纯 Python 3 标准库零外部依赖，毫秒级响应，已配置 systemd 守护进程常驻运行。
- 🖼️ **原位实景图片翻译 (深度对标 Google 翻译与微信图片翻译)**：
  - **实景原位贴字覆写 (In-place AR Image Translation Overlay)**：彻底告别只把识别文本填入主输入框的旧模式！利用 Google ML Kit 离线文本识别精准定位每个段落文本块在原图上的坐标区域 (`boundingBox: Rect`)；
  - **智能采样与自适应排版**：采样文本块四周背景颜色动态绘制圆角遮罩层，依据背景明暗度自动计算高对比度文本颜色，并运用 `StaticLayout` 自适应计算字号 (Auto-sizing)，让图片上的英文“直接变中文”，视觉体验浑然一体；
  - **专属全屏沉浸式图片翻译查看器 (ImageTranslateScreen)**：拍照或从相册选图后直接进入沉浸式全屏预览页；
  - **底部浮动控制栏**：支持一键秒切【显示译文】/【显示原文】（快速对比）；
  - **手势缩放与拖拽**：支持双指 1.0x ~ 5.0x 无级平滑缩放与平移查看超高清细节，双击快速在 1.0x 与 2.5x 之间切换；
  - **交互点击高亮与卡片查看**：点击图片上任意翻译文本块，醒目蓝色外框高亮聚焦，底部弹出精致卡片，展示原文、译文，支持一键单独复制与 TTS 语音发音朗读！
  - **辅助操作**：右上角菜单支持一键“复制全部提取文本”、“保存翻译后的图片”至系统相册、以及“导入主输入框编辑”。
- 🔊 **高保真双模语音朗读 (本地 TTS + 在线网络发音兜底)**：
  - **彻底告别真机无声**：针对国内定制 ROM（小米澎湃OS、华为、OPPO、vivo等）缺少 Google TTS 或英文离线数据包的问题，先尝试本地 TTS，不可用或缺少离线数据时立即平滑无缝降级为免 Key 在线音频流发音（有道发音 + 美国中转代理 TTS + Google TTS 免 Key 接口），确保 100% 响亮发音！
  - **规范音频流与静音提示**：标准使用 `STREAM_MUSIC` 媒体流，并在媒体音量为 0 或设备静音时给出友好提醒。
  - **Android 11+ 包可见性适配**：清单中声明 `TTS_SERVICE`，支持系统安装的各种第三方与系统 TTS 引擎发现。
  - **超长文本分句切片朗读**：突破系统 4000 字符限制，连续自然播放。
- 🔄 **智能自动检测下的中英双向对调**：
  - 左侧源语言为【自动检测 (AUTO)】时，随用户输入/粘贴实时判断语言特征：
    - 输入中文 -> 自动将目标语言动态对齐切换为英文 (`Language.EN`)，执行中译英；
    - 输入英文及其他语种 -> 自动将目标语言动态对齐切换为中文 (`Language.ZH`)，执行英译中；
  - 界面动态联动显示目标语言，支持用户随时手动切换，彻底避免“中译中”尴尬情况。
- 🎙️ **智能语音输入 (STT)**：
  - 精准权限动态申请与友好交互提示。
  - 根据当前所选源语言动态切换识别语调偏好（`zh-CN`, `en-US`, `ja-JP`, `ko-KR`, `fr-FR` 等）。
  - 优雅拦截系统缺失语音组件的异常并指导用户。
- 🔑 **多翻译引擎自由切换**：
  - **Google 翻译**（内置首选，免 Key）
  - **百度翻译开放平台**（已预置商用 API Key `wMHD_dber75qj3nn4okmvvqb0`，支持 AI 大模型翻译与通用 VIP 翻译智能降级）
  - **网易有道智云**（支持单词音标、词性及词典详细释义）
  - **腾讯云机器翻译 (TMT)**（高并发、企业级高质量机器翻译）
  - **本地 Mock 演示引擎**（离线快速体验与调试）
- 🎨 **Google 极简现代美学**：
  - 纯正 Material 3 规范与 Google 标志性四色设计。
  - 一键双向语言互换。
  - 文本快捷一键清空、剪贴板一键粘贴。
- 🌟 **全屏大字展示 & 历史收藏**：
  - 支持将翻译结果一键全屏超大字体展示，专为出境问路、交流沟通量身打造。
  - 本地 SQLite 离线存储全部翻译历史记录，支持一键星标收藏与快速搜索。

---

## 🛠️ 项目目录结构

```
Translate
├── app                                 # 安卓客户端工程
│   ├── src/main/java/com/translate/app
│   │   ├── data
│   │   │   ├── api
│   │   │   │   ├── ApiConfig.kt        # 全局配置常量 (Google代理地址、百度Key)
│   │   │   │   ├── EngineFactory.kt    # 翻译引擎工厂
│   │   │   │   ├── ITranslationEngine.kt# 统一翻译接口抽象
│   │   │   │   └── engine              # 各引擎实现
│   │   │   │       ├── GoogleTranslationEngine.kt # Google双模翻译引擎
│   │   │   │       ├── BaiduTranslationEngine.kt  # 百度翻译实现 (AI+VIP)
│   │   │   │       ├── YoudaoTranslationEngine.kt # 网易有道实现
│   │   │   │       ├── TencentTranslationEngine.kt# 腾讯云实现
│   │   │   │       └── MockTranslationEngine.kt   # 本地离线引擎
│   │   │   ├── db                      # SQLite 数据库 (历史与收藏)
│   │   │   ├── model                   # 语言模型、引擎类型、翻译结果
│   │   │   └── repository              # 偏好持久化与数据仓库
│   │   ├── ui
│   │   │   ├── components              # Material 3 自定义组件 (输入卡片、图片弹窗等)
│   │   │   ├── screens                 # 翻译主界面、对话模式、历史收藏、设置页
│   │   │   ├── theme                   # 动态配色与 Typography
│   │   │   └── viewmodel               # MVVM 状态流管理 (StateFlow)
│   │   └── util                        # 加密工具、TTS 管理器、ML Kit OCR 管理器
│   └── src/test                        # 完整单元测试用例
├── server                              # Google 翻译中转代理服务端
│   ├── server.py                       # 高性能多线程 HTTP 代理服务
│   ├── start.sh / stop.sh              # 启停管理脚本
│   ├── simpletranslate-proxy.service   # systemd 守护进程单元
│   ├── test_service.py                 # 服务端完整单元测试
│   └── README.md                       # 服务端专属部署说明
├── release-apk                         # 本地构建产物归档
│   ├── SimpleTranslate-release.apk     # 正式版 APK
│   └── SimpleTranslate-debug.apk       # 调试版 APK
└── .github/workflows/build-apk.yml     # GitHub Actions 自动化构建与发布
```

---

## 🚀 Google 翻译与中转代理使用指引

### 1. 客户端使用
- 安装本 APP 后，默认引擎即为 **Google 翻译**。
- 默认连接模式为【**服务器中转代理**】，预置中转地址为 `http://129.151.9.55:8787`（部署于美国甲骨文云高速服务器）。国内用户无需任何额外配置或梯子，即可直接享受 Google 翻译。
- 在【设置】界面中，您可以自由切换为【**官方接口直连**】或自定义配置私有中转服务器地址，并随时点击“保存设置并测试连接”。

### 2. 服务端独立部署（可选自建中转）
若您希望在自己的海外 VPS 上部署该中转服务，只需运行：
```bash
cd server
./start.sh
```
或使用 systemd 守护进程：
```bash
cp server/simpletranslate-proxy.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now simpletranslate-proxy
```
服务将在 `8787` 端口启动，并提供 `/health` 健康检查与 `/api/translate` 翻译接口。

---

## 📷 图片与拍照翻译使用指引

1. 在翻译主界面中，点击输入框右下方的 **相机 (📷)** 图标。
2. 弹出选择面板：
   - **拍照识别翻译**：启动系统相机拍摄现实中的路牌、菜单、包装等，拍摄确认后自动识别。
   - **从相册导入图片**：选择相册中的截图或照片。
3. 系统将基于本地 Google ML Kit 离线文字识别模型自动提取文字，清洗段落后自动填入输入框，并触发当前所选引擎即时翻译！

---

## 🔊 语音引擎特性说明

1. **语音输入 (STT)**：点击输入框麦克风图标，系统根据当前源语言设定自动匹配对应发音模型，开始说话并自动完成识别与翻译。
2. **语音朗读 (TTS)**：点击原文或译文卡片上的小喇叭按钮，系统自动匹配高保真本地发音包。支持超长文本分句流式朗读，未完成初始化时自动加入缓冲队列。

---

## 📦 自动打包与 GitHub Actions CI/CD

本项目已配置完善的 GitHub Actions 自动构建与发布流水线（[`.github/workflows/build-apk.yml`](file:///.github/workflows/build-apk.yml)）：
- 每次推送（Push）或打 Tag 会自动运行全部单测并打包；
- 自动生成可直接在手机上安装的 `Release APK` 与 `Debug APK`；
- 产物自动归档发布至 GitHub Releases。

### 本地编译构建
```bash
# 运行单元测试
./gradlew testDebugUnitTest

# 打包 Debug APK
./gradlew assembleDebug

# 打包 Release 正式版 APK
./gradlew assembleRelease
```
产物生成于：
- `release-apk/SimpleTranslate-release.apk`
- `release-apk/SimpleTranslate-debug.apk`
