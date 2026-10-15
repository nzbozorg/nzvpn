package nzvpn.nzbozorg.ir.util

/**
 * کمک‌کار متن‌های فارسی:
 *  - تبدیل ارقام لاتین به فارسی (همه‌جای رابط)
 *  - تبدیل بایت به متن خوانا با ارقام فارسی
 *  - «۳ سرور» و شکل‌های جمع درست
 */
object PersianText {

    private val DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    /** هر رقم لاتین در متن را فارسی می‌کند */
    fun digits(text: String): String =
        text.map { ch -> if (ch in '0'..'9') DIGITS[ch - '0'] else ch }.joinToString("")

    fun digits(value: Number): String = digits(value.toString())

    /** «۱٫۲ مگابایت» */
    fun bytes(bytes: Long): String = when {
        bytes < 1024 -> "${digits(bytes)} بایت"
        bytes < 1024 * 1024 -> digits("%.1f".format(bytes / 1024.0)) + " کیلوبایت"
        bytes < 1024L * 1024 * 1024 -> digits("%.1f".format(bytes / 1024.0 / 1024.0)) + " مگابایت"
        else -> digits("%.2f".format(bytes / 1024.0 / 1024.0 / 1024.0)) + " گیگابایت"
    }

    /** «۳ سرور» / «۱ سرور» */
    fun count(value: Int, singular: String, plural: String): String =
        if (value == 1) "۱ $singular" else "${digits(value)} $plural"

    /** «۱۴۸ میلی‌ثانیه» */
    fun latency(ms: Long): String = when {
        ms <= 0 -> "—"
        ms < 1000 -> "${digits(ms)} میلی‌ثانیه"
        else -> digits("%.1f".format(ms / 1000.0)) + " ثانیه"
    }

    /** عدد خالی: برای نمایش کوتاه پینگ «۱۴۸ms» → «۱۴۸» */
    fun latencyShort(ms: Long): String = if (ms <= 0) "—" else digits(ms)

    /** زمان به‌صورت ساعت:دقیقه:ثانیه فارسی */
    fun clock(millis: Long): String {
        val total = (millis / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) {
            "${digits(h)}:${digits("%02d".format(m))}:${digits("%02d".format(s))}"
        } else {
            "${digits(m)}:${digits("%02d".format(s))}"
        }
    }

    /** کد پرچم ایموجی (دو regional indicator) از نام خام سرور/پوشه. */
    fun countryFlagCode(raw: String): String? {
        if (raw.isBlank()) return null
        val text = if ('%' in raw) {
            runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        } else raw
        var i = 0
        while (i < text.length) {
            val first = text.codePointAt(i)
            val nextIndex = i + Character.charCount(first)
            if (first in 0x1F1E6..0x1F1FF && nextIndex < text.length) {
                val second = text.codePointAt(nextIndex)
                if (second in 0x1F1E6..0x1F1FF) {
                    return "${('A'.code + first - 0x1F1E6).toChar()}" +
                        "${('A'.code + second - 0x1F1E6).toChar()}"
                }
            }
            i = nextIndex
        }
        return null
    }

    /**
     * پاکسازی نام برای **متن**؛ پرچم از نام خام جدا و به‌صورت تصویر ایموجی
     * در کنار آن نمایش داده می‌شود. این‌طوری حتی روی گوشی بدون فونت ایموجی هم
     * پرچم به‌جای دو حرف لاتین دیده می‌شود. ایموجی‌های تزئینی و نویسه‌های
     * نامرئی حذف، درصدکدها باز و فاصله‌های اضافه جمع می‌شوند.
     */
    fun cleanName(raw: String, maxLen: Int = 64): String {
        if (raw.isBlank()) return ""
        var text = raw.trim()
        if (text.contains('%')) {
            text = runCatching { java.net.URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)
        }
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val width = Character.charCount(cp)
            val drop = when {
                cp in 0x1F1E6..0x1F1FF -> true                 // حروف نشانگر منطقه‌ای (پرچم)
                cp in 0x1F000..0x1FAFF -> true                 // ایموجی‌های تصویری
                cp in 0x2190..0x21FF -> true                   // فلش‌ها
                cp in 0x2600..0x27BF -> true                   // نمادهای تصویری/دینگبت
                cp in 0x2B00..0x2BFF || cp in 0xFE00..0xFE0F -> true
                cp == 0x200B || cp == 0x200D -> true            // فاصلهٔ صفر و چسبانندهٔ صفر
                cp in 0x200E..0x200F -> true                    // نشانگرهای جهت
                cp in 0x202A..0x202E || cp in 0x2066..0x2069 -> true
                cp == 0xFEFF -> true                            // BOM
                else -> false
            }
            if (!drop) out.appendCodePoint(cp)
            i += width
        }
        // فاصله‌های اضافی + جداکننده‌های چسبیده به دو سر نام
        val cleaned = out.toString().replace(Regex("\\s+"), " ").trim()
            .trim('|', '-', '–', '•', '·', '_', '.', ',', ':', '،', '*', '#', '=', '+')
            .trim()
        return when {
            cleaned.isEmpty() -> ""
            cleaned.length <= maxLen -> cleaned
            else -> cleaned.take(maxLen).trimEnd() + "…"
        }
    }
}
