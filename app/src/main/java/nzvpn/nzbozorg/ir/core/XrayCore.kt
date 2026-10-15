package nzvpn.nzbozorg.ir.core

import android.content.Context
import go.Seq
import nzvpn.nzbozorg.ir.data.AppPrefs
import nzvpn.nzbozorg.ir.util.AppLog
import java.io.File
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * پوشش نازک روی هسته Xray (libv2ray.aar که با gomobile از AndroidLibXrayLite ساخته شده).
 *
 * API استفاده‌شده (نسخه libv2ray v26.10.10):
 *   Seq.setContext(appContext)
 *   Libv2ray.initCoreEnv(assetPath, xudpKey)
 *   Libv2ray.newCoreController(CoreCallbackHandler)
 *   controller.startLoop(configJson, tunFd) / controller.stopLoop() / controller.isRunning
 *   Libv2ray.checkVersionX()
 *   Libv2ray.measureOutboundDelay(configJson, testUrl)   // تست تاخیر بدون بالا آوردن VPN
 *   controller.queryAllOutboundTrafficStats()
 *
 * همه این‌ها را v2rayNG هم به همین شکل صدا می‌زند (CoreNativeManager.kt).
 */
object XrayCore {

    private var controller: CoreController? = null

    @Volatile
    private var envReady = false

    /** یک‌بار در طول عمر پروسه اجرا می‌شود */
    @Synchronized
    fun init(context: Context) {
        if (envReady) return
        try {
            Seq.setContext(context.applicationContext)
            Libv2ray.initCoreEnv(assetPath(context), AppPrefs.get(context).xudpKey)
            envReady = true
            AppLog.d("Xray init: ${version()}")
        } catch (t: Throwable) {
            AppLog.e("Xray init failed", t)
            throw t
        }
    }

    private fun assetPath(context: Context): String {
        // مسیر قابل‌دسترس برای کد Go؛ کاربر می‌تواند geoip.dat/geosite.dat خودش را اینجا بگذارد.
        // اگر فایل نباشد، هسته از asset های داخل APK استفاده می‌کند.
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "assets")
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    private fun controllerOf(context: Context): CoreController {
        init(context)
        controller?.let { return it }
        val created = Libv2ray.newCoreController(Callback())
        controller = created
        return created
    }

    /** شروع هسته با کانفیگ کامل و فایل‌دیسکریپتور تونل (0 = بدون تونل) */
    fun start(context: Context, configJson: String, tunFd: Int) {
        val c = controllerOf(context)
        c.startLoop(configJson, tunFd)
        check(c.isRunning) { "هسته شروع نشد" }
        AppLog.d("Xray core started (tunFd=$tunFd)")
    }

    fun stop() {
        try {
            controller?.stopLoop()
            AppLog.d("Xray core stopped")
        } catch (t: Throwable) {
            AppLog.e("Xray core stop failed", t)
        }
    }

    val isRunning: Boolean
        get() = controller?.isRunning == true

    fun version(): String = try {
        Libv2ray.checkVersionX()
    } catch (t: Throwable) {
        "unknown"
    }

    /**
     * تست تاخیر یک کانفیگ بدون بالا آوردن VPN (یک نمونه موقت می‌سازد).
     * @return میلی‌ثانیه یا -1 در صورت خطا
     */
    fun measureOutboundDelay(configJson: String, testUrl: String): Long = try {
        Libv2ray.measureOutboundDelay(configJson, testUrl)
    } catch (t: Throwable) {
        AppLog.e("latency test failed", t)
        -1L
    }

    /**
     * اندازه‌گیری تاخیر **از داخل هستهٔ در حال اجرا** (یعنی ترافیک واقعاً از تونل می‌رود).
     * برای بررسی «وصل شد یا نه» بعد از بالا آمدن سرویس استفاده می‌شود.
     * @return میلی‌ثانیه، یا -1 اگر جواب نداد
     */
    fun measureDelayInCore(url: String): Long = try {
        controller?.measureDelay(url) ?: -1L
    } catch (t: Throwable) {
        AppLog.e("اندازه‌گیری از داخل هسته ناموفق بود", t)
        -1L
    }

    /**
     * ترافیک از آخرین فراخوانی (هسته شمارنده‌ها را صفر می‌کند).
     * خروجی: (آپلود بایت، دانلود بایت)
     */
    fun trafficDelta(): Pair<Long, Long> {
        val raw = try {
            controller?.queryAllOutboundTrafficStats().orEmpty()
        } catch (t: Throwable) {
            ""
        }
        var up = 0L
        var down = 0L
        for (entry in raw.split(';')) {
            val parts = entry.split(',')
            if (parts.size != 3) continue
            val value = parts[2].toLongOrNull() ?: continue
            when (parts[1]) {
                "uplink" -> up += value
                "downlink" -> down += value
            }
        }
        return up to down
    }

    private class Callback : CoreCallbackHandler {
        override fun startup(): Long {
            AppLog.d("core callback: startup")
            return 0
        }

        override fun shutdown(): Long {
            AppLog.d("core callback: shutdown")
            return 0
        }

        override fun onEmitStatus(p0: Long, p1: String?): Long {
            if (!p1.isNullOrBlank()) AppLog.d("core: $p1")
            return 0
        }
    }
}
