package nzvpn.nzbozorg.ir.net

import org.json.JSONObject
import java.io.IOException

/** یادداشت انتشارِ *همان نسخهٔ نصب‌شده*؛ متن از API رسمی مخزن گرفته و ساده نمایش داده می‌شود. */
data class InstalledReleaseNotes(val version: String, val title: String, val body: String)

object InstalledReleaseNotesPolicy {
    const val RETRY_AFTER_MS = 15 * 60 * 1000L
    private const val MAX_NOTES_CHARS = 64_000

    fun shouldRequest(
        installed: String,
        firstInstalledAt: Long,
        lastUpdatedAt: Long,
        shownVersion: String,
        lastAttemptVersion: String,
        lastAttemptAt: Long,
        now: Long,
    ): Boolean {
        val version = runCatching { ReleaseCatalog.normalizeVersion(installed) }.getOrNull()
            ?: return false
        // نصب تازه، آپدیت نیست؛ پاپ‌آپِ یادداشت برای ارتقای برنامه است.
        if (firstInstalledAt <= 0 || lastUpdatedAt <= firstInstalledAt) return false
        if (shownVersion == version) return false
        if (lastAttemptVersion == version && lastAttemptAt > 0 && now >= lastAttemptAt &&
            now - lastAttemptAt < RETRY_AFTER_MS) return false
        return true
    }

    fun parse(json: String, installed: String): InstalledReleaseNotes {
        val version = ReleaseCatalog.normalizeVersion(installed)
        val data = JSONObject(json)
        if (data.optBoolean("draft") || data.optBoolean("prerelease") ||
            ReleaseCatalog.normalizeVersion(data.optString("tag_name")) != version
        ) throw IOException("یادداشت انتشار با نسخهٔ نصب‌شده یکی نیست")
        val title = data.optString("name", "").takeUnless { it == "null" }
            ?.trim()?.take(160).orEmpty().ifBlank { "نسخهٔ $version" }
        val body = data.optString("body", "").takeUnless { it == "null" }
            ?.trim()?.take(MAX_NOTES_CHARS).orEmpty()
        return InstalledReleaseNotes(version, title, body)
    }
}
