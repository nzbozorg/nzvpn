package nzvpn.nzbozorg.ir.parser

import nzvpn.nzbozorg.ir.model.Profile
import nzvpn.nzbozorg.ir.util.Base64Util

/**
 * خواندن «فایل کانفیگ» مخزن گیت‌هاب.
 *
 * قواعد فایل (docs/github-file-format.md):
 *   - خط خالی، یا شروع‌شده با # یا //  → نادیده
 *   - sub: <url>  (یا subscription:/url: یا خود http(s))
 *        → یک **ساب**؛ هر ساب یک **پوشه** در برنامه می‌شود.
 *        نام پوشه از انتهای آدرس بعد از # خوانده می‌شود: sub: https://a.com/x#آلمان
 *        اگر نامی نباشد، از دامنه/آی‌پی ساخته می‌شود.
 *   - بقیهٔ خطوط → لینک سرور (vmess/vless/trojan/ss/socks/http)
 *
 * سرورهای مستقیمِ داخل فایل، در پوشهٔ «فایل مخزن» می‌نشینند.
 */
object SubscriptionParser {

    private val SUB_PREFIXES = listOf("sub:", "subscription:", "url:", "sublink:", "sub-url:")

    /** یک ساب: آدرس + نام پوشه */
    data class SubLink(val url: String, val name: String)

    data class Result(
        val profiles: List<Profile>,
        val subscriptions: List<SubLink>,
        val warnings: List<String>,
    )

    /**
     * @param groupName نام پوشه‌ای که سرورهای این متن در آن می‌نشینند
     */
    fun parseText(text: String, groupName: String): Result {
        val profiles = mutableListOf<Profile>()
        val subscriptions = mutableListOf<SubLink>()
        val warnings = mutableListOf<String>()

        for (rawLine in text.split('\n')) {
            val line = rawLine.trim().removeSuffix("\r").trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue

            val lower = line.lowercase()
            val prefix = SUB_PREFIXES.firstOrNull { lower.startsWith(it) }
            if (prefix != null) {
                val rest = line.substring(prefix.length).trim()
                if (rest.isNotEmpty()) subscriptions += subLink(rest)
                continue
            }
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                subscriptions += subLink(line)
                continue
            }

            try {
                val profile = ShareLinkParser.parse(line)?.copy(group = groupName)
                if (profile == null) {
                    warnings += "این خط خوانده نشد: ${line.take(50)}"
                } else {
                    profiles += profile
                }
            } catch (e: Exception) {
                warnings += "لینک نامعتبر (${e.message}): ${line.take(50)}"
            }
        }
        return Result(dedupe(profiles), subscriptions.distinctBy { it.url }, warnings)
    }

    /**
     * آدرس ساب → آدرس + نام پوشه.
     * نام پوشه: از #انتهای آدرس، وگرنه از دامنه.
     */
    fun subLink(raw: String): SubLink {
        val trimmed = raw.trim()
        val hashIndex = trimmed.lastIndexOf('#')
        val url = if (hashIndex > 0) trimmed.substring(0, hashIndex).trim() else trimmed
        val fragment = if (hashIndex > 0) trimmed.substring(hashIndex + 1).trim() else ""
        val name = fragment.ifBlank { hostOf(url) }
        return SubLink(url, name.ifBlank { "اشتراک" })
    }

    /** نام پوشه از دامنه: srv.example.com → example.com */
    fun hostOf(url: String): String {
        val host = url.substringAfter("://", url)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfter('@')      // حذف user:pass اگر بود
            .substringBefore(':')
        val parts = host.split('.').filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> parts.takeLast(2).joinToString(".")
            parts.size == 1 -> parts[0]
            else -> ""
        }
    }

    /**
     * محتوای یک ساب دانلودشده: یا کل فایل base64 است یا لیست خام لینک‌ها.
     * همهٔ سرورها در پوشهٔ همان ساب می‌نشینند.
     */
    fun parseSubscriptionBody(body: String, groupName: String): Result {
        val text = Base64Util.decodeIfBase64(body)
        val parsed = parseText(text, groupName)
        // داخل یک ساب، سابِ تودرتو معنی ندارد
        return Result(parsed.profiles, emptyList(), parsed.warnings)
    }

    /** حذف تکراری‌ها */
    fun dedupe(list: List<Profile>): List<Profile> = list.distinctBy { it.dedupeKey }
}
