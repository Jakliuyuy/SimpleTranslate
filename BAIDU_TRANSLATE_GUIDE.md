# 百度翻译开放平台 API 对接与集成开发文档

本项目已深度对接并集成了用户申请的百度翻译官方 API 凭据。本文档详细阐述百度翻译开放平台的鉴权原理、接口调用机制、本项目双引擎智能适配架构以及使用指南。

---

## 一、 百度 API 凭证分析与鉴权规范

### 1. 凭证解析
用户提供的凭证为：
```text
Key: wMHD_dber75qj3nn4okmvvqb0
```
该 Key 为百度翻译开放平台生成的**开发者应用密钥 (Secret Key / API Key)**。

### 2. 百度翻译开放平台的鉴权机制
百度翻译开放平台 (`fanyi-api.baidu.com`) 的服务调用遵循严格的安全计费规则：
* **APP ID (必须)**：百度分配给每位开发者的账户唯一编号（通常为 17 位纯数字），用于标识计费主体、权限级别与免费额度。
* **密钥 / API Key (必须)**：用于请求身份鉴权。支持以下两种调用机制：

#### 机制 A：大模型文本翻译 API (AI Text Translate) —— 推荐
* **接口地址**：`https://fanyi-api.baidu.com/ait/api/aiTextTranslate`
* **请求方式**：`POST` (JSON 格式)
* **鉴权 Header**：
  ```http
  Authorization: Bearer wMHD_dber75qj3nn4okmvvqb0
  Content-Type: application/json; charset=utf-8
  ```
* **请求参数 (JSON Body)**：
  ```json
  {
    "appid": "您的百度APP_ID",
    "from": "zh",
    "to": "en",
    "q": "待翻译文本"
  }
  ```
* **成功响应**：
  ```json
  {
    "from": "zh",
    "to": "en",
    "trans_result": [
      {
        "src": "待翻译文本",
        "dst": "Translated text"
      }
    ]
  }
  ```

#### 机制 B：通用文本翻译 API (VIP 经典接口)
* **接口地址**：`https://fanyi-api.baidu.com/api/trans/vip/translate`
* **请求方式**：`POST` (application/x-www-form-urlencoded)
* **签名算法**：
  $$sign = \text{MD5}(appid + q + salt + secretKey)$$
* **请求参数**：
  - `q`：待翻译文本
  - `from`：源语言代码（支持 `auto` 自动检测）
  - `to`：目标语言代码
  - `appid`：开发者 APP ID
  - `salt`：随机数（本项目使用当前时间戳 `System.currentTimeMillis()`）
  - `sign`：计算出的 32 位小写 MD5 签名值

---

## 二、 本客户端双模容错架构设计

为了保证高可用性与无缝体验，客户端的 [`BaiduTranslationEngine`](file:///root/Translate/app/src/main/java/com/translate/app/data/api/engine/BaiduTranslationEngine.kt) 采用了**双引擎智能降级与重试机制**：

```
[用户触发翻译]
       │
       ▼
[检查 AppID & Key] ── (若未填 AppID) ──► [弹出明确引导，提示输入数字 AppID]
       │ (均已配置)
       ▼
[尝试 1: 大模型文本翻译 API] ── (Bearer Token 鉴权)
       │
   成功? ──── 是 ────► [展示大模型高质翻译结果]
       │ 否 (未开通/超时/错误)
       ▼
[自动降级 2: 通用文本翻译 API] ── (MD5 动态签名鉴权)
       │
   成功? ──── 是 ────► [展示通用翻译结果]
       │ 否
       ▼
[解析百度错误码，反馈清晰中文原因 (如 54004 余额不足 / 54001 签名错误等)]
```

### 1. 智能数据清洗
针对用户复制密钥时可能携带的前缀（如 `keywMHD_...`）、外层引号、`Bearer ` 前缀或多余空格，底层引擎在发起请求前会自动进行清洗与去空格处理：
```kotlin
fun sanitizeKey(key: String): String {
    var result = key.trim()
    // 剥离外层单双引号
    if ((result.startsWith("\"") && result.endsWith("\"")) ||
        (result.startsWith("'") && result.endsWith("'")) ||
        (result.startsWith("“") && result.endsWith("”"))
    ) {
        result = result.substring(1, result.length - 1).trim()
    }
    // 剥离 Bearer 前缀
    if (result.startsWith("Bearer ", ignoreCase = true)) {
        result = result.substring(7).trim()
    }
    // 剥离 key: 或 key= 前缀
    if (result.startsWith("key:", ignoreCase = true) || result.startsWith("key=", ignoreCase = true)) {
        result = result.substring(4).trim()
    }
    // 剥离粘连的 key 前缀 (如 keywMHD...)
    if (result.startsWith("key", ignoreCase = true) && result.length > 3 && !result.startsWith("key-", ignoreCase = true)) {
        val remainder = result.substring(3).trim()
        if (remainder.isNotEmpty()) {
            result = remainder
        }
    }
    return result
}
```

### 2. 代码全局预置
在 [`ApiConfig.kt`](file:///root/Translate/app/src/main/java/com/translate/app/data/api/ApiConfig.kt) 中已将该 Key 固化为默认常量：
```kotlin
const val DEFAULT_BAIDU_SECRET_KEY: String = "wMHD_dber75qj3nn4okmvvqb0"
```
且在 [`PreferenceRepository.kt`](file:///root/Translate/app/src/main/java/com/translate/app/data/repository/PreferenceRepository.kt) 中已将默认引擎切换为 `BAIDU`，用户安装启动即默认使用百度翻译服务。

---

## 三、 用户快速上手指南 (10秒激活)

由于百度安全校验机制必须关联 APP ID，您只需完成以下简单一步：

1. 打开浏览器登录 [百度翻译开放平台管理控制台](https://fanyi-api.baidu.com/)。
2. 点击顶部导航栏 **【开发者信息】**。
3. 复制页面上显示的 **APP ID**（例如 `20241025000000000`）。
4. 在 APP 底部点击 **【设置】** 图标 -> 在“百度 APP ID”输入框中点击右侧的 **【粘贴】** 图标即可保存生效！
5. 点击下方 **【保存设置并测试连接】**，提示“百度翻译连接成功！”即完成全部配置。

---

## 四、 常见百度 API 错误码排查表

| 错误码 | 错误含义 | 解决方案 |
| :--- | :--- | :--- |
| **52003** | 未授权用户 | 检查 APP ID 是否输错，或前往平台【我的服务】确认是否开通了“通用文本翻译”或“大模型翻译” |
| **54000** | 必填参数为空 | 缺少 appid 或 q，检查设置页中 APP ID 是否填写完整 |
| **54001** | 签名或 Token 错误 | 密钥不匹配，请核对密钥是否与当前 APP ID 来自同一百度账号 |
| **54004** | 账户余额不足 | 前往百度翻译控制台查看免费额度，每月免费字符用尽时可充值或申请高级版额度 |
| **58000** | 客户端 IP 非法 | 百度控制台若设置了 IP 白名单，需将客户端 IP 加入或关闭白名单限制 |
