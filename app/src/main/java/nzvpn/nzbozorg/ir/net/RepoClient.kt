package nzvpn.nzbozorg.ir.net

import nzvpn.nzbozorg.ir.brand.BrandConfig
import nzvpn.nzbozorg.ir.data.SubscriptionQuota
import nzvpn.nzbozorg.ir.data.SubscriptionQuotaParser
import nzvpn.nzbozorg.ir.model.Profile
import nzvpn.nzbozorg.ir.parser.SubscriptionParser
import nzvpn.nzbozorg.ir.util.AppLog
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * خواندن فایل کانفیگ از مخزن گیت‌هاب + دانلود ساب‌های داخل آن.
 *
 * آدرس‌های پذیرفته‌شده:
 *   https://raw.githubusercontent.com/USER/REPO/BRANCH/configs.txt      (بهترین حالت)
 *   https://github.com/USER/REPO/blob/BRANCH/configs.txt                (خودش تبدیل می‌کند)
 *   https://github.com/USER/REPO                                        (نام فایل‌های پیش‌فرض را امتحان می‌کند)
 *   هر آدرس دیگری (سرور خودتان / jsdelivr / pastbin)                     (بدون تغییر)
 */
class RepoClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** null یعنی دانلود/پارس ناموفق؛ در این حالت پوشهٔ موجود نباید حذف شود. */
    data class LoadedSubscription(
        val link: SubscriptionParser.SubLink,
        val profiles: List<Profile>?,
    )

    data class RefreshResult(
        val directProfiles: List<Profile>,
        val subscriptions: List<LoadedSubscription>,
        val warnings: List<String>,
    ) {
        val profiles: List<Profile> get() = SubscriptionParser.dedupe(
            directProfiles + subscriptions.flatMap { it.profiles.orEmpty() },
        )
    }

    /**
     * فایل اصلی را می‌خواند، ساب‌های داخلش را دانلود می‌کند و لیست نهایی سرورها را می‌دهد.
     * هر ساب یک **پوشه** می‌شود؛ سرورهای مستقیم فایل در پوشهٔ «فایل مخزن».
     */
    fun refresh(repoUrl: String): RefreshResult {
        val warnings = mutableListOf<String>()
        val subscriptions = mutableListOf<LoadedSubscription>()

        val mainText = fetchRepoFile(repoUrl)
        val parsed = SubscriptionParser.parseText(mainText, BrandConfig.GROUP_REPO_FILE)
        warnings += parsed.warnings
        AppLog.d("فایل مخزن: ${parsed.profiles.size} سرور، ${parsed.subscriptions.size} ساب")

        for (sub in parsed.subscriptions) {
            try {
                val body = fetchText(sub.url)
                val subParsed = SubscriptionParser.parseSubscriptionBody(body, sub.name)
                warnings += subParsed.warnings.map { "${sub.name}: $it" }
                if (subParsed.profiles.isEmpty()) {
                    warnings += "ساب «${sub.name}» خالی یا ناخوانا بود؛ سرورهای قبلی حفظ شدند"
                    subscriptions += LoadedSubscription(sub, null)
                } else {
                    subscriptions += LoadedSubscription(sub, subParsed.profiles)
                    AppLog.d("ساب «${sub.name}»: ${subParsed.profiles.size} سرور")
                }
            } catch (t: Throwable) {
                warnings += "ساب «${sub.name}» خوانده نشد؛ سرورهای قبلی حفظ شدند"
                subscriptions += LoadedSubscription(sub, null)
                AppLog.e("ساب «${sub.name}» ناموفق", t)
            }
        }
        return RefreshResult(parsed.profiles, subscriptions, warnings)
    }

    data class FetchedSubscription(
        val parsed: SubscriptionParser.Result,
        val quota: SubscriptionQuota?,
    )

    /** حجم فقط از هدرِ همان پاسخِ ساب خوانده می‌شود، نه از تخمین مصرف VPN. */
    fun fetchSubscriptionWithQuota(url: String, groupName: String): FetchedSubscription {
        val (body, quota) = fetchTextAndQuota(SubscriptionParser.subLink(url).url)
        return FetchedSubscription(SubscriptionParser.parseSubscriptionBody(body, groupName), quota)
    }

    /** سایر خواندن‌ها، از جمله ساب‌های پیش‌فرض مخزن، حجم را دور می‌اندازند. */
    fun fetchSubscription(url: String, groupName: String): SubscriptionParser.Result =
        fetchSubscriptionWithQuota(url, groupName).parsed

    /** فایل اصلی (با همهٔ تبدیل‌های آدرس و fallback روی API گیت‌هاب) */
    fun fetchRepoFile(repoUrl: String): String {
        var lastError: Throwable? = null
        for (candidate in candidates(repoUrl)) {
            try {
                return fetchText(candidate)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw (lastError as? IOException)
            ?: IOException("فایل گیت‌هاب خوانده نشد${lastError?.message?.let { " ($it)" } ?: ""}")
    }

    fun fetchText(url: String, accept: String? = null): String = fetchTextAndQuota(url, accept).first

    private fun fetchTextAndQuota(url: String, accept: String? = null): Pair<String, SubscriptionQuota?> {
        val builder = Request.Builder().url(url).header("User-Agent", "NZVPN/1.1.0")
        if (accept != null) builder.header("Accept", accept)

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException(httpMessage(response.code, url))
            // تنها هدرِ پاسخِ موفق معنا دارد. نبودنش یعنی سرویس آمار مصرف ارائه نمی‌کند.
            val quota = SubscriptionQuotaParser.parse(
                response.header("Subscription-Userinfo") ?: response.header("X-Subscription-Userinfo"),
            )
            val body = response.body.string()
            if (body.length > 4_000_000) throw IOException("فایل بزرگ‌تر از ۴ مگابایت است — این فایل ساب نیست")
            return body to quota
        }
    }

    private fun httpMessage(code: Int, url: String): String {
        // URL ساب ممکن است توکن داشته باشد؛ آن را در پیام کاربر/لاگ کپی نکن.
        val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
        return when (code) {
            401, 403 -> "دسترسی رد شد (مخزن خصوصی است؟) — $host"
            404 -> "فایل پیدا نشد؛ آدرس یا نام شاخه را چک کنید — $host"
            429 -> "محدودیت نرخ گیت‌هاب؛ چند دقیقه بعد دوباره تلاش کنید"
            else -> "خطای شبکه ($code) — $host"
        }
    }

    private fun candidates(input: String): List<String> {
        val url = input.trim()
        if (url.isEmpty()) return emptyList()

        if (url.contains("raw.githubusercontent.com") ||
            url.contains("gist.githubusercontent.com") ||
            url.contains("cdn.jsdelivr.net")
        ) {
            return listOf(url)
        }

        if (url.contains("github.com")) {
            val base = url.replace("http://", "https://")
            val rawForm = base.replace("://github.com/", "://raw.githubusercontent.com/")
                .replace("/blob/", "/")
                .replace("/raw/", "/")
            val out = mutableListOf(rawForm)

            // فقط owner/repo داده شده → نام‌های پیش‌فرض را در main و master امتحان کن
            if (Regex("^https?://(www\\.)?github\\.com/[^/]+/[^/]+/?$").matches(url)) {
                val repoBase = url.trimEnd('/').replace("://github.com/", "://raw.githubusercontent.com/")
                for (branch in listOf("main", "master")) {
                    for (name in BrandConfig.REPO_FILE_NAMES) {
                        out += "$repoBase/$branch/$name"
                    }
                }
            }
            return out.distinct()
        }

        return listOf(url)
    }
}
