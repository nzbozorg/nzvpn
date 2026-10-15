package nzvpn.nzbozorg.ir.data

import android.content.Context

/**
 * حافظهٔ تنظیمات (SharedPreferences: "nzvpn").
 *
 * از این نسخه به بعد **بخش تنظیمات در برنامه حذف شده**؛ همه‌چیز خودکار و درست
 * تنظیم شده است. فقط چیزهایی که واقعاً باید بمانند اینجا نگه داشته می‌شوند:
 *   • آخرین سرور انتخاب‌شده
 *   • نتیجهٔ تست‌ها
 *   • کلید رمزنگاری XUDP (تصادفی، یک‌بار)
 *   • پرچم «یک‌بار از مخزن خوانده شد»
 */
class AppPrefs private constructor(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("nzvpn", Context.MODE_PRIVATE)

    /** آخرین سرور انتخاب‌شده */
    var lastSelectedId: String
        get() = sp.getString(KEY_SELECTED, "").orEmpty()
        set(value) = sp.edit().putString(KEY_SELECTED, value).apply()

    /** آخرین نتیجهٔ تست: id → میلی‌ثانیه */
    var latencyMap: String
        get() = sp.getString(KEY_LATENCY, "").orEmpty()
        set(value) = sp.edit().putString(KEY_LATENCY, value).apply()

    /**
     * کلید XUDP هسته (متغیر محیطی xray.xudp.basekey).
     *
     * ⚠️ بسیار مهم: هستهٔ Xray این کلید را با base64url بدون padding دیکود می‌کند و
     * **باید دقیقاً ۳۲ بایت** باشد؛ وگرنه هنگام ساخت هسته خطای
     * «xray.xudp.basekey: invalid value (BaseKey must be 32 bytes)» می‌دهد و
     * اتصال با «core init failed» شکست می‌خورد. (باگ نسخهٔ ۱.۰.۱ همین بود.)
     */
    val xudpKey: String
        get() {
            val saved = sp.getString(KEY_XUDP, null)
            if (!saved.isNullOrBlank() && isValidXudpKey(saved)) return saved
            val generated = newXudpKey()
            sp.edit().putString(KEY_XUDP, generated).apply()
            return generated
        }

    /** ۳۲ بایت تصادفی → base64url بدون padding (همان چیزی که هسته انتظار دارد) */
    private fun newXudpKey(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
    }

    /** فقط اگر دیکود شود و دقیقاً ۳۲ بایت بدهد معتبر است */
    private fun isValidXudpKey(key: String): Boolean = try {
        android.util.Base64.decode(
            key,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        ).size == 32
    } catch (t: Throwable) {
        false
    }

    /** آیا تا حالا از مخزن گیت‌هاب سرور خوانده شده؟ (برای به‌روزرسانی خودکار بار اول) */
    var repoLoadedOnce: Boolean
        get() = sp.getBoolean(KEY_REPO_LOADED, false)
        set(value) = sp.edit().putBoolean(KEY_REPO_LOADED, value).apply()

    /** مهاجرت پوشه‌های ساب نسخه‌های قدیمی به منبع پایدار انجام شده است. */
    var sourcesMigrated: Boolean
        get() = sp.getBoolean(KEY_SOURCES_MIGRATED, false)
        set(value) = sp.edit().putBoolean(KEY_SOURCES_MIGRATED, value).apply()

    /** دوست‌داشتن‌ها: پوشه‌های بسته‌شده (با کاما جدا می‌شوند) */
    var collapsedGroups: String
        get() = sp.getString(KEY_COLLAPSED, "").orEmpty()
        set(value) = sp.edit().putString(KEY_COLLAPSED, value).apply()

    /** جداکنندهٔ پرکاربرد: پوشه‌هایی که کاربر بسته است */
    fun collapsedSet(): MutableSet<String> =
        collapsedGroups.split('\u0001').filter { it.isNotBlank() }.toMutableSet()

    fun saveCollapsed(groups: Set<String>) {
        collapsedGroups = groups.joinToString("\u0001")
    }

    companion object {
        private const val KEY_SELECTED = "selected"
        private const val KEY_LATENCY = "latency"
        private const val KEY_XUDP = "xudp"
        private const val KEY_REPO_LOADED = "repo_loaded_once"
        private const val KEY_SOURCES_MIGRATED = "sources_migrated"
        private const val KEY_COLLAPSED = "collapsed_groups"

        @Volatile
        private var instance: AppPrefs? = null

        fun get(context: Context): AppPrefs =
            instance ?: synchronized(this) {
                instance ?: AppPrefs(context).also { instance = it }
            }
    }
}
