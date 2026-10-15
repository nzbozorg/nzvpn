package nzvpn.nzbozorg.ir.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * لاگ ساده در حافظه + logcat. خط آخر در UI نمایش داده می‌شود
 * (برای وقتی که کاربر می‌گوید «وصل نمی‌شود و هیچ پیامی نمی‌بینم»).
 */
object AppLog {

    private const val TAG = "NZVPN"
    private const val MAX_LINES = 400
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    val lines = MutableStateFlow<List<String>>(emptyList())

    fun d(message: String) {
        val line = "${timeFormat.format(Date())}  $message"
        // روی JVM تست واحد، android.util.Log شبیه‌سازی نشده است.
        runCatching { Log.d(TAG, message) }
        lines.value = (lines.value + line).takeLast(MAX_LINES)
    }

    fun e(message: String, t: Throwable? = null) {
        val line = "${timeFormat.format(Date())}  ✗ $message${t?.message?.let { " ($it)" } ?: ""}"
        runCatching { Log.e(TAG, message, t) }
        lines.value = (lines.value + line).takeLast(MAX_LINES)
    }

    fun lastLine(): String = lines.value.lastOrNull().orEmpty()
}
