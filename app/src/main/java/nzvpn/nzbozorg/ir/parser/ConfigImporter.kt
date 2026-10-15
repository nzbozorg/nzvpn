package nzvpn.nzbozorg.ir.parser

import nzvpn.nzbozorg.ir.data.ServerStore
import nzvpn.nzbozorg.ir.model.Profile
import nzvpn.nzbozorg.ir.model.Protocol
import nzvpn.nzbozorg.ir.util.Base64Util

/**
 * تشخیص خودکار «چیزی که کاربر چسبانده یا از فایل انتخاب کرده» و تبدیلش به سرور/ساب.
 *
 * حالت‌های تشخیص‌داده‌شده:
 *   ۱) چند لینک سرور (هر خط یکی)                → چند سرور
 *   ۲) یک لینک سرور (vless:// …)                → یک سرور
 *   ۳) لینک ساب (http/https)                     → ساب (دانلود و پوشه‌بندی می‌شود)
 *   ۴) متن Base64 (محتوای ساب)                   → مثل ساب، ولی بدون دانلود
 *   ۵) کانفیگ کامل Xray (JSON با outbounds)      → یک سرور «کانفیگ کامل»
 */
object ConfigImporter {

    sealed class Result {
        /** سرورهای آمادهٔ افزودن (لینک/کانفیگ کامل) */
        data class Servers(val profiles: List<Profile>, val note: String) : Result()

        /** یک ساب که باید دانلود شود */
        data class Subscription(val url: String, val name: String) : Result()

        /** محتوای ساب که همین‌جا باز شده */
        data class SubscriptionText(val profiles: List<Profile>, val name: String) : Result()

        /** چند نشانی ساب در یک فایل/متن؛ هیچ‌کدام نباید بی‌صدا نادیده گرفته شود. */
        data class Subscriptions(val links: List<SubscriptionParser.SubLink>) : Result()

        /** کانفیگ‌های مستقیم در کنار چند ساب؛ هرکدام در پوشهٔ مربوط به خود. */
        data class Mixed(
            val profiles: List<Profile>, val links: List<SubscriptionParser.SubLink>,
        ) : Result()

        data class Failure(val message: String) : Result()
    }

    private val LINKS = listOf("vmess://", "vless://", "trojan://", "ss://", "socks://", "socks5://", "http://", "https://")

    /** آیا این متن اصلاً چیزی برای افزودن دارد؟ */
    fun looksImportable(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (t.startsWith("{")) return true
        if (LINKS.any { t.lowercase().startsWith(it) }) return true
        if (t.lines().size > 1 && t.lines().any { l -> LINKS.any { l.trim().lowercase().startsWith(it) } }) return true
        return Base64Util.looksLikeBase64(t)
    }

    /**
     * @param groupName نام پوشه‌ای که سرورهای دستی در آن می‌نشینند
     */
    fun classify(rawText: String, groupName: String): Result {
        val text = rawText.trim().removeSuffix("\n").trim()
        if (text.isEmpty()) return Result.Failure("چیزی برای افزودن پیدا نشد")

        // ۵) کانفیگ کامل Xray
        if (text.startsWith("{")) {
            return try {
                org.json.JSONObject(text)
                val profile = ServerStore.buildCustomProfile(text, groupName)
                Result.Servers(listOf(profile), "کانفیگ کامل اضافه شد")
            } catch (t: Throwable) {
                Result.Failure("این متن یک کانفیگ JSON معتبر نیست")
            }
        }

        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

        // ۱ و ۲) لینک‌های سرور
        val shareLinks = lines.filter { l ->
            val lower = l.lowercase()
            lower.startsWith("vmess://") || lower.startsWith("vless://") || lower.startsWith("trojan://") ||
                lower.startsWith("ss://") || lower.startsWith("socks://") || lower.startsWith("socks5://") ||
                lower.startsWith("ssr://")
        }
        if (shareLinks.isNotEmpty()) {
            val profiles = mutableListOf<Profile>()
            var failed = 0
            for (link in shareLinks) {
                try {
                    profiles += ShareLinkParser.parseOrThrow(link).copy(group = groupName)
                } catch (t: Throwable) {
                    failed++
                }
            }
            if (profiles.isEmpty()) return Result.Failure("لینک پیدا شد ولی هیچ‌کدام خوانده نشد")
            val links = SubscriptionParser.parseText(text, groupName).subscriptions
            if (links.isNotEmpty()) return Result.Mixed(profiles, links)
            val note = if (profiles.size == 1) "۱ سرور اضافه شد" else "${profiles.size} سرور اضافه شد" +
                if (failed > 0) " ($failed لینک ناخوانا)" else ""
            return Result.Servers(profiles, note)
        }

        // ۳) لینک ساب
        if (lines.size == 1) {
            val url = lines.first()
            if (url.lowercase().startsWith("http://") || url.lowercase().startsWith("https://")) {
                val sub = SubscriptionParser.subLink(url)
                return Result.Subscription(sub.url, sub.name)
            }
        }

        // ۴) Base64 (محتوای ساب یا فایل ساب)
        if (Base64Util.looksLikeBase64(text)) {
            val decoded = Base64Util.decodeIfBase64(text)
            val parsed = SubscriptionParser.parseText(decoded, groupName)
            if (parsed.profiles.isNotEmpty()) {
                return Result.SubscriptionText(parsed.profiles, "$groupName (فایل)")
            }
        }

        // متنی که شبیه فایل کانفیگ مخزن است (sub: + لینک‌ها)
        val parsed = SubscriptionParser.parseText(text, groupName)
        if (parsed.profiles.isNotEmpty() && parsed.subscriptions.isNotEmpty()) {
            return Result.Mixed(parsed.profiles, parsed.subscriptions)
        }
        if (parsed.profiles.isNotEmpty()) {
            return Result.Servers(parsed.profiles, "${parsed.profiles.size} سرور اضافه شد")
        }
        if (parsed.subscriptions.size > 1) return Result.Subscriptions(parsed.subscriptions)
        if (parsed.subscriptions.size == 1) {
            val sub = parsed.subscriptions.first()
            return Result.Subscription(sub.url, sub.name)
        }

        return Result.Failure("این متن شناسایی نشد؛ باید لینک سرور یا لینک ساب باشد")
    }

    /** نسخهٔ سادهٔ پرکاربرد: فقط لینک ساب */
    fun isSubscriptionUrl(value: String): Boolean {
        val v = value.trim().lowercase()
        if (!(v.startsWith("http://") || v.startsWith("https://"))) return false
        return !LINKS.any { v.startsWith(it) && it != "http://" && it != "https://" }
    }

    fun isXrayJson(text: String): Boolean = text.trim().startsWith("{") && text.contains("outbounds")

    fun protocolOrNull(text: String): Protocol? =
        ShareLinkParser.parse(text.trim())?.protocol
}
