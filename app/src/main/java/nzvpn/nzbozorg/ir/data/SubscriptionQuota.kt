package nzvpn.nzbozorg.ir.data

import android.content.Context
import java.security.MessageDigest

/** مصرفی که خودِ ارائه‌دهندهٔ ساب در HTTP Subscription-Userinfo اعلام کرده است. */
data class SubscriptionQuota(
    val uploadBytes: Long,
    val downloadBytes: Long,
    val totalBytes: Long,
    val expireSeconds: Long? = null,
    val checkedAtMillis: Long = 0L,
) {
    val usedBytes: Long get() = if (uploadBytes > Long.MAX_VALUE - downloadBytes)
        Long.MAX_VALUE else uploadBytes + downloadBytes
    val remainingBytes: Long get() = (totalBytes - usedBytes.coerceAtMost(totalBytes)).coerceAtLeast(0L)
    val usedFraction: Float get() = (usedBytes.toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat()
}

/** بدون «total» معتبر و هر دو مقدار upload/download، حجم ساب را حدس نمی‌زنیم. */
object SubscriptionQuotaVisibility {
    fun canShow(source: GroupSource?, quota: SubscriptionQuota?): Boolean =
        source?.kind == GroupSource.Kind.SUBSCRIPTION && quota != null
}

object SubscriptionQuotaParser {
    fun parse(header: String?, checkedAtMillis: Long = System.currentTimeMillis()): SubscriptionQuota? {
        val value = header?.takeIf { it.isNotBlank() && it.length <= 2_048 } ?: return null
        val numbers = mutableMapOf<String, Long>()
        for (piece in value.split(';')) {
            val key = piece.substringBefore('=', "").trim().lowercase()
            if (key !in setOf("upload", "download", "total", "expire")) continue
            val raw = piece.substringAfter('=', "").trim()
            val number = raw.toLongOrNull()?.takeIf { it >= 0 }
            if (number == null && key == "expire") continue // اختیاری است؛ خرابی‌اش حجم را مخفی نکند.
            numbers[key] = number ?: return null
        }
        val upload = numbers["upload"] ?: return null
        val download = numbers["download"] ?: return null
        val total = numbers["total"]?.takeIf { it > 0L } ?: return null
        return SubscriptionQuota(upload, download, total, numbers["expire"], checkedAtMillis)
    }
}

/** دادهٔ حجم فقط در فضای خصوصی برنامه؛ کلید URL با هش نگه‌داری می‌شود. */
object SubscriptionQuotaStore {
    private const val FILE = "nzvpn_subscription_quotas"

    private fun key(location: String): String = MessageDigest.getInstance("SHA-256")
        .digest(location.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun load(context: Context, location: String): SubscriptionQuota? {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(key(location), null) ?: return null
        val values = raw.split(':')
        if (values.size != 5) return null
        val up = values[0].toLongOrNull()?.takeIf { it >= 0L } ?: return null
        val down = values[1].toLongOrNull()?.takeIf { it >= 0L } ?: return null
        val total = values[2].toLongOrNull()?.takeIf { it > 0L } ?: return null
        val expire = values[3].toLongOrNull()?.takeIf { it >= 0L }
        val checkedAt = values[4].toLongOrNull()?.takeIf { it >= 0L } ?: return null
        return SubscriptionQuota(up, down, total, expire, checkedAt)
    }

    fun save(context: Context, location: String, quota: SubscriptionQuota?) {
        val editor = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        if (quota == null) editor.remove(key(location)) else editor.putString(
            key(location),
            listOf(quota.uploadBytes, quota.downloadBytes, quota.totalBytes,
                quota.expireSeconds ?: 0L, quota.checkedAtMillis).joinToString(":"),
        )
        editor.apply()
    }
}
