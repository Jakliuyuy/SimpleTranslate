package com.translate.app.data.model

enum class TranslationEngineType(
    val id: String,
    val title: String,
    val description: String,
    val websiteUrl: String
) {
    GOOGLE(
        id = "google",
        title = "Google 翻译 (内置/免Key)",
        description = "内置免Key引擎，支持海外官方直连与美国专用中转服务器代理双模",
        websiteUrl = "https://translate.google.com/"
    ),
    BAIDU(
        id = "baidu",
        title = "百度翻译开放平台",
        description = "国内主流通用文本翻译接口，每月提供免费翻译额度，注册即用",
        websiteUrl = "https://fanyi-api.baidu.com/"
    ),
    YOUDAO(
        id = "youdao",
        title = "网易有道智云",
        description = "支持精准翻译与专业词典释义、英汉双向词性音标展示",
        websiteUrl = "https://ai.youdao.com/"
    ),
    TENCENT(
        id = "tencent",
        title = "腾讯云机器翻译 (TMT)",
        description = "腾讯云自然语言处理，高准确度神经机器翻译引擎",
        websiteUrl = "https://cloud.tencent.com/product/tmt"
    ),
    MOCK(
        id = "mock",
        title = "内置演示引擎",
        description = "开箱即用，无需申请任何密钥，包含常用词汇、日常对话与规则翻译",
        websiteUrl = ""
    );

    companion object {
        fun fromId(id: String): TranslationEngineType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MOCK
        }
    }
}
