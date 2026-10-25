package com.translate.app.util

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    /**
     * 计算 MD5 摘要（小写 32 位十六进制）
     * 百度翻译签名标准：md5(appid + q + salt + securityKey)
     */
    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 计算 SHA-256 摘要（小写 64 位十六进制）
     * 有道智云签名标准：sha256(appKey + truncate(q) + salt + curtime + appSecret)
     */
    fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 计算 HMAC-SHA256 签名（Base64 或十六进制，用于腾讯云等 API）
     */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(key, "HmacSHA256")
        mac.init(secretKeySpec)
        return mac.doFinal(data)
    }

    fun hmacSha256Hex(key: ByteArray, data: String): String {
        val bytes = hmacSha256(key, data.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
