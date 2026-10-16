package nzvpn.nzbozorg.ir.model

import nzvpn.nzbozorg.ir.util.PersianText

/** نوع سرور. CUSTOM = کانفیگ کامل Xray که کاربر دستی می‌چسباند. */
enum class Protocol {
    VMESS, VLESS, TROJAN, SS, SOCKS, HTTP, CUSTOM;

    /** نام فارسی برای نمایش در رابط */
    val fa: String
        get() = when (this) {
            VMESS -> "وی‌مِس"
            VLESS -> "وی‌لِس"
            TROJAN -> "تروجان"
            SS -> "شادوساکس"
            SOCKS -> "سوکس"
            HTTP -> "پروکسی"
            CUSTOM -> "کانفیگ کامل"
        }
}

/**
 * مدل یک سرور. از لینک اشتراک (vless/vmess/trojan/ss/…) ساخته می‌شود
 * یا برای پروفایل دستی از JSON کامل Xray.
 *
 * بدون وابستگی به اندروید است تا در تست‌های JVM قابل استفاده باشد.
 */
data class Profile(
    val id: String,
    val name: String,
    val protocol: Protocol,
    val address: String,
    val port: Int,
    /** لینک اصلی (برای ذخیره‌سازی و اشتراک‌گذاری مجدد) */
    val link: String = "",
    /** پوشه/گروه سرور: نام ساب یا «فایل مخزن» یا «افزوده‌شده با دست» */
    val group: String = "",
    /** پوشهٔ اصلیِ کپی ذخیره‌شده؛ حتی پس از حذف منبع برای اعلان اتصال می‌ماند. */
    val originGroup: String = "",
    /** برای CUSTOM: کل کانفیگ Xray به‌صورت JSON */
    val rawJson: String? = null,
    /** برای CUSTOM: فایل کانفیگ روی دیسک (مسیر) */
    val rawJsonPath: String? = null,

    // vmess
    val uuid: String = "",
    val alterId: Int = 0,
    val vmessSecurity: String = "auto",
    // vless
    val flow: String = "",
    val encryption: String = "none",
    // trojan / ss / socks / http
    val password: String = "",
    val method: String = "",
    val username: String = "",

    // transport
    val network: String = "tcp",
    val security: String = "none",   // none | tls | xtls | reality
    val sni: String = "",
    val alpn: List<String> = emptyList(),
    val fingerprint: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val path: String = "",
    val host: String = "",
    val serviceName: String = "",
    val mode: String = "",
    val headerType: String = "none",
    val allowInsecure: Boolean = false,
    val plugin: String = "",

    // نتیجه تست (ذخیره نمی‌شود؛ بعد از هر تست پر می‌شود)
    val latencyMs: Long = -1,
    val lastTestedAt: Long = 0,
    val lastError: String = "",
    /** فقط برای نمایش؛ لینک اصلی/نام خام و پرچم بدون تغییر می‌مانند. */
    val displayAlias: String = "",
) {
    val endpoint: String get() = "$address:$port"

    val displayName: String
        get() = displayAlias.ifBlank { PersianText.cleanName(name).ifBlank { endpoint } }

    /** کد دوحرفی پرچم ایموجی نام اصلی؛ رندر پرچم جدا از متن انجام می‌شود. */
    val flagCountryCode: String?
        get() = PersianText.countryFlagCode(name)

    /** نام پوشه؛ اگر خالی بود «بدون پوشه» */
    val groupName: String
        get() = PersianText.cleanName(group).ifBlank { "بدون پوشه" }

    /** نام فارسی امنیت لینک */
    val securityFa: String
        get() = when (security.lowercase()) {
            "tls" -> "تی‌ال‌اس"
            "xtls" -> "ایکس‌تی‌ال‌اس"
            "reality" -> "ریلیتی"
            else -> "بدون رمز"
        }

    /** نام فارسی نوع شبکه */
    val networkFa: String
        get() = when (network.lowercase()) {
            "ws", "websocket" -> "وب‌سوکت"
            "grpc", "gun" -> "جی‌آرپی‌سی"
            "h2", "http" -> "اچ‌تی‌تی‌پی۲"
            "quic" -> "کوییک"
            "xhttp", "splithttp" -> "ایکس‌اچ‌تی‌تی‌پی"
            "kcp", "mkcp" -> "کِی‌سی‌پی"
            else -> "تی‌سی‌پی"
        }

    fun withTestResult(latency: Long, error: String = "", at: Long = System.currentTimeMillis()) =
        copy(latencyMs = latency, lastError = error, lastTestedAt = at)

    /** شناسهٔ یکتا برای حذف تکراری‌ها */
    val dedupeKey: String
        get() = "$protocol|$address|$port|$uuid|$password|$network|$path|$sni"
}
