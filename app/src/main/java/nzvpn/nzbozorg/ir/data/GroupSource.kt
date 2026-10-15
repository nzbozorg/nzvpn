package nzvpn.nzbozorg.ir.data

import nzvpn.nzbozorg.ir.model.Profile
import nzvpn.nzbozorg.ir.util.PersianText

/** منبع پایدار هر پوشه؛ پیوندها و URI فایل فقط در فضای خصوصی خود برنامه ذخیره می‌شوند. */
data class GroupSource(val group: String, val kind: Kind, val location: String) {
    enum class Kind { REPO, SUBSCRIPTION, FILE }
}

/** انتخاب نام پوشه بدون برخورد با پوشه‌های موجود، مستقل از اندروید برای آزمون واحد. */
object GroupNaming {
    /** همان کلیدی که Profile.groupName و UI برای پوشه به کار می‌برند. */
    fun key(raw: String): String = PersianText.cleanName(raw).ifBlank { "بدون پوشه" }

    fun fromFile(fileName: String): String =
        fileName.substringAfterLast('/').substringAfterLast('\\')
            // نیم‌فاصلهٔ فارسی جزء اسم فایل است؛ فقط کنترل‌های خطرناکِ جهت/خط حذف شوند.
            .replace(Regex("[\\p{Cc}\\u202A-\\u202E\\u2066-\\u2069]"), "").trim().take(64)
            .ifBlank { "فایل واردشده" }

    fun fromPaste(profiles: List<Profile>): String {
        val examples = profiles.asSequence()
            .map { PersianText.cleanName(it.name, 22).ifBlank { it.address.take(22) } }
            .filter { it.isNotBlank() }.distinct().take(2).toList()
        return "چسبانده‌شده: " + examples.ifEmpty { listOf("سرورهای من") }.joinToString("، ")
    }

    fun unique(preferred: String, occupied: Collection<String>): String {
        val base = preferred.trim().ifBlank { "سرورها" }
        val existing = occupied.map { key(it).lowercase() }.toSet()
        if (key(base).lowercase() !in existing) return base
        // پسوندِ عددی باید قبل از سقفِ ۶۴ نویسهٔ نام نمایشی قرار بگیرد.
        val stem = base.take(52).trimEnd()
        var number = 2
        while (true) {
            val candidate = "$stem (${PersianText.digits(number)})"
            if (key(candidate).lowercase() !in existing) return candidate
            number++
        }
    }
}

/** به‌روزرسانی موفق فقط همان پوشه را جایگزین می‌کند؛ دانلود ناموفق هرگز به این تابع نمی‌رسد. */
object GroupUpdates {
    fun replace(existing: List<Profile>, group: String, fresh: List<Profile>): List<Profile> {
        require(fresh.isNotEmpty()) { "پاسخ خالی است؛ پوشهٔ قبلی باید حفظ شود" }
        val previous = existing.filter { it.groupName == group }.associateBy { it.id }
        val retained = existing.filterNot { it.groupName == group }
        val replacement = fresh.map { profile ->
            val old = previous[profile.id]
            // گروه خام ممکن است پرچم داشته باشد؛ کلیدِ پاک‌شده فقط برای تطبیق است.
            val updated = if (profile.groupName == group) profile else profile.copy(group = group)
            if (old != null && old.latencyMs > 0) {
                updated.copy(latencyMs = old.latencyMs, lastTestedAt = old.lastTestedAt)
            } else updated
        }
        val updated = ServerStore.merge(retained, replacement)
        // اگر همهٔ لینک‌های تازه در پوشه‌های دیگر تکراری بودند، پوشهٔ قبلی را پاک نکن.
        return if (updated.any { it.groupName == group }) updated else existing
    }
}
