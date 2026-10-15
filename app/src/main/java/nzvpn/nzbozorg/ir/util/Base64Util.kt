package nzvpn.nzbozorg.ir.util

import java.util.Base64

/**
 * Base64 با تحمل خطا: پشتیبانی از base64 استاندارد، urlsafe و رشته بدون padding.
 * (همان کاری که نسخه مرجع JS در tools/xray-config.mjs انجام می‌دهد)
 */
object Base64Util {

    fun decode(input: String): String {
        val cleaned = input.trim()
            .replace('-', '+')
            .replace('_', '/')
            .filterNot { it.isWhitespace() }
        val padded = cleaned + "=".repeat((4 - cleaned.length % 4) % 4)
        val bytes = try {
            Base64.getDecoder().decode(padded)
        } catch (e: IllegalArgumentException) {
            Base64.getMimeDecoder().decode(padded)
        }
        return String(bytes, Charsets.UTF_8)
    }

    fun encode(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    /** خروجی ساب‌های استاندارد یک blob بی‌فاصله base64 است */
    fun looksLikeBase64(text: String): Boolean {
        val t = text.filterNot { it.isWhitespace() }
        if (t.isEmpty() || t.length % 4 != 0) return false
        if (t.startsWith("http", ignoreCase = true)) return false
        return t.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' || it == '-' || it == '_' }
    }

    /**
     * اگر متن base64 بود، محتوای decode شده را برمی‌گرداند، وگرنه خود متن را.
     * (بعضی پنل‌ها کل فایل ساب را base64 می‌کنند و بعضی ساب خام می‌دهند)
     */
    fun decodeIfBase64(text: String): String {
        if (!looksLikeBase64(text)) return text
        return try {
            val decoded = decode(text)
            if (decoded.contains("://")) decoded else text
        } catch (e: Exception) {
            text
        }
    }
}
