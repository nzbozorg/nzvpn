package nzvpn.nzbozorg.ir.net

/**
 * آدرس‌های تست تاخیر — **ایرانی**.
 *
 * چرا ایرانی؟ چون کاربر ایرانی می‌خواهد بداند سرور برای سایت‌های ایرانی چقدر خوب
 * جواب می‌دهد و مسیر ایران هم واقعی‌تر سنجیده می‌شود.
 *
 * قاعده‌های مهم این آدرس‌ها:
 *   • پاسخ ۲۰۰ یا ۲۰۴ بدهند (قلب اندازه‌گیری هسته فقط همین دو کد را قبول می‌کند)
 *   • کوچک باشند (favicon) تا تست سریع باشد و پهنای‌باند نخورد
 *
 * در تست پوشه بعد از مقصد اول ایرانی، پشتیبان جهانی برای پاسخ سریع‌تر می‌آید؛
 * اگر آن هم جواب نداد، مقصد ایرانی دوم بررسی می‌شود تا پاسخ‌گو اشتباه رد نشود.
 */
object PingTargets {

    /** اول این‌ها (به ترتیب) */
    val IRANIAN = listOf(
        "https://www.zoomit.ir/favicon.ico",
        "https://www.varzesh3.com/favicon.ico",
        "https://www.irancell.ir/favicon.ico",
        "https://www.isna.ir/favicon.ico",
    )

    /** پشتیبان جهانی (generate_204 → پاسخ ۲۰۴) */
    val GLOBAL = listOf(
        "https://cp.cloudflare.com/generate_204",
        "https://www.gstatic.com/generate_204",
    )

    /** تست دقیق تک‌به‌تک: ایران → جهان برای پاسخ سریع‌تر → ایرانِ دوم برای اطمینان. */
    fun attempts(): List<String> = listOf(IRANIAN[0], GLOBAL[0], IRANIAN[1])

    /**
     * حالت **سریع** برای تست دسته‌جمعی (دکمهٔ «اتصال به سریع‌ترین»):
     * یک آدرس ایرانی کافی است؛ اگر جواب نداد یک پشتیبان جهانی.
     * کم‌کردن تعداد تلاش‌ها = تست چند ثانیه‌ای.
     */
    fun fastAttempts(): List<String> = listOf(IRANIAN.first(), GLOBAL.first())

    /** مهلت یک‌ثانیه‌ای پوشه: مقصد سبک جهانی اول؛ ایرانی فقط اگر سریع شکست خورد. */
    fun quickGroupAttempts(): List<String> = listOf(GLOBAL.first(), IRANIAN.first())

    /** ترتیب تلاش برای بررسی «وصل شد یا نه» */
    fun verifyAttempts(): List<String> = IRANIAN + GLOBAL

    /** برچسب فارسی هر آدرس، برای نمایش در لاگ/پیام‌ها */
    fun label(url: String): String = when {
        url.contains("zoomit") -> "زومیت"
        url.contains("varzesh3") -> "ورزش سه"
        url.contains("irancell") -> "ایرانسل"
        url.contains("isna") -> "ایسنا"
        url.contains("cloudflare") -> "کلادفلر"
        url.contains("gstatic") -> "گوگل"
        else -> url.substringAfter("//").substringBefore("/")
    }
}
