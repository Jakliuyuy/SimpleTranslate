# SimpleTranslate Google 翻译中转代理服务端

## 📖 项目简介
本模块是专为 **SimpleTranslate** 安卓客户端打造的轻量级高性能 Google 翻译代理中转服务。
部署于具有良好国际网络的服务器（如美国服务器 IP `129.151.9.55`），调用 Google 官方免 Key 翻译端点并将结果标准化输出给客户端，解决国内网络环境下无法直连 Google 翻译的问题。

---

## 🌟 核心特性
- **纯标准库零依赖**：基于 Python 3 `http.server.ThreadingHTTPServer` 与 `urllib`，无需 `pip install` 任何额外库。
- **高并发多线程**：内置线程池并发处理请求，毫秒级响应。
- **CORS 全局跨域**：支持任何 Web 前端、小程序、Flutter/React Native、Android/iOS 客户端直连。
- **健康监控**：提供 `/health` 端点即时监控服务运行状态。
- **一键服务化守护进程**：提供 `systemd` 服务单元，系统自启、奔溃自动拉起。

---

## 🚀 快速启动与管理

### 1. 手动前台启动
```bash
python3 server.py --port 8787
```

### 2. 后台脚本启停
```bash
# 启动
./start.sh

# 停止
./stop.sh
```

### 3. 作为 systemd 守护进程运行 (推荐)
```bash
# 复制服务配置文件
cp simpletranslate-proxy.service /etc/systemd/system/

# 重载配置并开机自启
systemctl daemon-reload
systemctl enable simpletranslate-proxy
systemctl start simpletranslate-proxy

# 查看运行状态
systemctl status simpletranslate-proxy
```

---

## 📡 API 接口规范

### 1. 服务健康检查
- **路径**：`GET /health` 或 `GET /`
- **响应示例**：
```json
{
  "status": "ok",
  "service": "SimpleTranslate-GoogleProxy",
  "version": "1.0.0",
  "timestamp": 1729840000,
  "message": "Google Translation Proxy is running smoothly."
}
```

### 2. 文本翻译接口
- **路径**：`POST /api/translate` 或 `GET /api/translate`
- **支持格式**：`application/json` 或 `application/x-www-form-urlencoded`
- **参数说明**：
  - `text` (或 `q`)：待翻译文本（必填）
  - `from` (或 `sl`)：源语言代码（可选，默认 `auto` 自动检测）
  - `to` (或 `tl`)：目标语言代码（可选，默认 `zh-CN`）
- **响应示例**：
```json
{
  "code": 200,
  "success": true,
  "data": {
    "originalText": "Hello world! This is SimpleTranslate.",
    "translatedText": "你好世界！这是简单翻译。",
    "detectedSource": "en",
    "sourceLanguage": "auto",
    "targetLanguage": "zh-CN"
  }
}
```

---

## 🧪 自动化测试
运行本服务的完整单测：
```bash
python3 test_service.py
```
