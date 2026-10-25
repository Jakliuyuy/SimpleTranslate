package com.translate.app.data.api

/**
 * 集中管理各大翻译平台的默认 API 密钥与配置。
 * 
 * 💡 提示：
 * 当您在百度、有道或腾讯开放平台申请到 API 密钥后，
 * 可以直接将下面的常量替换为您申请到的真实密钥！
 * 同时，APP 也支持在【设置】界面中动态输入保存密钥，无需重新打包即可生效。
 */
object ApiConfig {

    // ==========================================
    // 0. Google 翻译内置引擎 (免Key / 服务器中转代理与海外直连双模)
    // 默认服务器位于美国 (129.151.9.55:8787)，解决国内网络无法直连 Google 的痛点
    // ==========================================
    const val DEFAULT_GOOGLE_PROXY_URL: String = "http://129.151.9.55:8787"
    const val DEFAULT_GOOGLE_MODE: String = "proxy" // "proxy" (中转代理模式) 或 "direct" (官方直连模式)


    // ==========================================
    // 1. 百度翻译开放平台 (通用文本翻译 / 大模型文本翻译 API)
    // 申请地址: https://fanyi-api.baidu.com/
    // 免费额度: 标准版个人认证每月 5 万字符免费，高级版每月 100-200 万字符免费
    // ==========================================
    const val DEFAULT_BAIDU_APP_ID: String = ""       // 填写申请到的 APP ID (通常为 17 位纯数字)
    const val DEFAULT_BAIDU_SECRET_KEY: String = "wMHD_dber75qj3nn4okmvvqb0"   // 用户申请的百度 API Key / 密钥

    // ==========================================
    // 2. 网易有道智云翻译平台 (文本翻译 + 词典释义)
    // 申请地址: https://ai.youdao.com/
    // 特点: 支持单词音标、词性、词典详细释义
    // ==========================================
    const val DEFAULT_YOUDAO_APP_KEY: String = ""     // 填写应用ID (appKey)
    const val DEFAULT_YOUDAO_APP_SECRET: String = ""  // 填写应用密钥 (appSecret)

    // ==========================================
    // 3. 腾讯云机器翻译 (TMT)
    // 申请地址: https://cloud.tencent.com/product/tmt
    // 免费额度: 每月前 500 万字符免费
    // ==========================================
    const val DEFAULT_TENCENT_SECRET_ID: String = ""  // 填写腾讯云 SecretId
    const val DEFAULT_TENCENT_SECRET_KEY: String = "" // 填写腾讯云 SecretKey
}
