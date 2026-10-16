package nzvpn.nzbozorg.ir.brand

/**
 * تنها نقطهٔ ویرایش برند و متن‌های کاربر: NZBOZORG.
 * تغییر نام، شعار، لینک‌ها و … فقط از همین فایل انجام می‌شود.
 */
object BrandConfig {

    // ------------------------------------------------------------------ برند
    const val BRAND_FIRST = "NZ"
    const val BRAND_SECOND = "VPN"
    const val FULL_NAME = "NZBOZORG"
    const val CREDIT_NAME = "Amir NoZohour"
    const val TAGLINE = "شبکهٔ امن، ساده و سریع"

    // ---------------------------------------------------------------- لینک‌ها
    const val WEBSITE = "https://nzbozorg.ir"
    const val TELEGRAM = "https://t.me/nzbozorg"
    const val SUPPORT_EMAIL = "support@nzbozorg.ir"

    /** لینک حمایت اختیاری با فنجان قهوه، بدون اصرار یا متن دستوری. */
    const val DONATION_URL = "https://coffeebede.com/Amir_NoZohour"

    const val DONATION_TITLE = "یه قهوه مهمونم می‌کنی؟ :)"
    const val DONATION_BODY =
        "NZVPN همیشه رایگان و بدون تبلیغ می‌مونه. اگه دوست داشتی، " +
            "می‌تونی یه قهوه مهمونم کنی؛ اگرم نه، همین که اینجایی کلی خوشحالم ☕"
    const val DONATION_BUTTON = "یه قهوه مهمونم می‌کنی؟ :)"
    const val DONATION_LATER = "بعداً"

    // ------------------------------------------------------- اشتراک‌گذاری
    /** نسخهٔ برنامه (همین‌جا عوض کنید) */
    const val VERSION_NAME = "1.0.9"

    /** متنی که با دکمهٔ اشتراک‌گذاری برای دیگران فرستاده می‌شود */
    const val SHARE_TEXT = "NZVPN — اینترنت آزاد، ساده و سریع 🔒\n" +
        "بدون تبلیغ، بدون ثبت‌نام. فایل نصبی همین‌جا پیوست است؛ نصب کن و استفاده کن.\n\n" +
        "سورس و انتشارها: https://github.com/nzbozorg/nzvpn\n" +
        "وب‌سایت: https://nzbozorg.ir\n" +
        "کانال تلگرام: https://t.me/nzbozorg"

    // ------------------------------------------------------------------ مخزن
    /** مخزن عمومی کانفیگ‌ها و GitHub Releases برای به‌روزرسانی امضاشدهٔ اپ */
    const val DEFAULT_REPO = "https://github.com/nzbozorg/nzvpn"
    const val RELEASES_API = "https://api.github.com/repos/nzbozorg/nzvpn/releases/latest"

    /** نام‌های فایلی که در مخزن جست‌وجو می‌شوند */
    val REPO_FILE_NAMES = listOf("configs.txt", "nzconfigs.txt", "nzvpn.txt", "v2ray.txt", "sub.txt")

    /** پوشهٔ پیش‌فرض سرورهای داخل فایل مخزن */
    const val GROUP_REPO_FILE = "فایل مخزن"

    /** کپی‌های مستقل کانفیگ‌های ستاره‌دار؛ فقط با اولین ستاره پوشه ساخته می‌شود. */
    const val GROUP_SAVED = "ذخیره‌شده‌ها"

    /** پوشهٔ کانفیگ‌هایی که کاربر دستی می‌چسباند */
    const val GROUP_MANUAL = "افزوده‌شده با دست"
}
