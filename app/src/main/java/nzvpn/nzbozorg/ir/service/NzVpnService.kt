package nzvpn.nzbozorg.ir.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import nzvpn.nzbozorg.ir.MainActivity
import nzvpn.nzbozorg.ir.R
import nzvpn.nzbozorg.ir.core.XrayCore
import nzvpn.nzbozorg.ir.net.PingTargets
import nzvpn.nzbozorg.ir.util.AppLog
import nzvpn.nzbozorg.ir.util.PersianText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * قلب برنامه: سرویس VPN.
 *
 * کارهایی که انجام می‌دهد:
 *   ۱) اینترفیس TUN را می‌سازد (Builder) و ترافیک کل گوشی را به آن می‌فرستد
 *   ۲) فایل‌دیسکریپتور TUN را به هسته Xray می‌دهد (startLoop(config, fd))
 *   ۳) ترافیک آپ/دانلود را هر ۲ ثانیه به UI و نوتیفیکیشن می‌دهد
 *
 * دو نکته مهم که از v2rayNG یاد گرفته شده:
 *   - اپ خودمان از VPN مستثنا می‌شود: addDisallowedApplication(packageName)
 *     وگرنه سوکت‌های خروجی هسته داخل تونل خودش می‌افتند و حلقه می‌شود.
 *   - هنگام قطع: اول stopSelf() بعد بستن اینترفیس (وگرنه هسته پورت/سوکت را آزاد نمی‌کند).
 */
class NzVpnService : VpnService() {

    private var tunInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var statsJob: Job? = null
    private var upTotal = 0L
    private var downTotal = 0L
    private val verifyPool = Executors.newFixedThreadPool(6) { task ->
        Thread(task, "nzvpn-verify").apply { isDaemon = true }
    }
    // نمایش رنگیِ همان لوگو در متن اعلان؛ آیکون کوچک نوار وضعیت طبق قانون
    // اندروید فقط به‌صورت تک‌رنگ نمایش داده می‌شود.
    private val notificationLogo by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.nz_logo_notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AppLog.d("stop requested by user")
                stopVpn()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_CONFIG).orEmpty()
                val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty()
                val group = intent.getStringExtra(EXTRA_GROUP).orEmpty()
                val latency = intent.getLongExtra(EXTRA_LATENCY, -1L)
                if (config.isBlank()) {
                    AppLog.e("start requested without config")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundCompat(group, name)
                scope.launch { connect(config, name, profileId, group, latency) }
            }

            else -> {
                // سرویس را سیستم (Always-on VPN) بالا آورده و کانفیگی همراه نیست
                AppLog.d("service started without action — nothing to do")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        AppLog.d("VPN permission revoked")
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        statsJob?.cancel()
        verifyPool.shutdownNow()
        XrayCore.stop()
        closeTun()
        scope.cancel()
        VpnBus.reset()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- connect

    private suspend fun connect(
        configJson: String, name: String, profileId: String, group: String, latency: Long,
    ) {
        VpnBus.update {
            it.copy(state = VpnState.CONNECTING, profileName = name, profileId = profileId,
                groupName = group, latencyMs = latency, message = "")
        }
        try {
            closeTun()
            // مقادیر ثابت و آزمایش‌شده (هم‌راستا با v2rayNG):
            //   MTU 1500 · آدرس کلاینت /30 · مسیر همهٔ ترافیک · دی‌ان‌اس عمومی
            val builder = Builder()
                .setSession("NZVPN")
                .setMtu(1500)
                .addAddress(TUN_ADDRESS, 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }
            // ترافیک خود برنامه از تونل بیرون بماند (جلوگیری از حلقه)
            runCatching { builder.addDisallowedApplication(packageName) }
                .onFailure { AppLog.e("could not exclude own package", it) }

            val pfd = builder.establish()
                ?: throw IllegalStateException("ساخت اینترفیس VPN ممکن نشد (اجازه داده نشده؟)")

            tunInterface = pfd
            VpnBus.update { it.copy(message = "روشن کردن هستهٔ اتصال…") }
            XrayCore.start(this, configJson, pfd.fd)

            // بررسی واقعی: ترافیک از داخل تونل بیرون می‌رود یا نه؟
            VpnBus.update { it.copy(message = "بررسی اتصال…") }
            val (verified, usedUrl) = verifyConnection()
            currentCoroutineContext().ensureActive()
            if (!verified) {
                throw IllegalStateException(
                    "تونل بالا آمد ولی اینترنت از آن رد نشد. " +
                        "کانفیگ سرور یا دسترسی شبکه را بررسی کنید.",
                )
            }

            VpnBus.update { it.copy(state = VpnState.CONNECTED, message = "") }
            AppLog.d("اتصال برقرار شد به $name (تست: ${PingTargets.label(usedUrl)})")
            startStatsLoop()
        } catch (t: Throwable) {
            val detail = t.message ?: t.javaClass.simpleName
            AppLog.e("اتصال ناموفق: $detail", t)
            VpnBus.update { it.copy(state = VpnState.DISCONNECTED, message = detail) }
            XrayCore.stop()
            closeTun()
            stopSelf()
        }
    }

    /**
     * تأیید اتصال از داخل هسته، بدون انتظارِ زنجیره‌ایِ شش تستِ ۱۲ثانیه‌ای.
     * یک مقصد ایرانی و یک پشتیبان جهانی هم‌زمان شروع می‌شوند. اگر هیچ‌کدام
     * تا ۹۰۰ میلی‌ثانیه جواب ندهند، چهار مقصد دیگر هم شروع می‌شوند. اولین
     * پاسخ سالم کافی است؛ کل مرحله حداکثر ۱۳ ثانیه (در حالت شبکهٔ خراب) است.
     * در حالت عادی مقصدی که زودتر جواب دهد ظرف چند ثانیه اتصال را تأیید می‌کند.
     */
    private fun verifyConnection(): Pair<Boolean, String> {
        val initial = listOf(PingTargets.IRANIAN.first(), PingTargets.GLOBAL.first())
        val remaining = PingTargets.verifyAttempts().filterNot { it in initial }
        val completion = ExecutorCompletionService<Pair<Long, String>>(verifyPool)
        val futures = mutableListOf<java.util.concurrent.Future<Pair<Long, String>>>()
        fun submit(urls: List<String>) {
            urls.forEach { url ->
                futures += completion.submit(Callable { XrayCore.measureDelayInCore(url) to url })
            }
        }

        val start = System.nanoTime()
        val expandAt = start + TimeUnit.MILLISECONDS.toNanos(900)
        val deadline = start + TimeUnit.SECONDS.toNanos(13)
        submit(initial)
        var expanded = false
        var received = 0
        try {
            while (System.nanoTime() < deadline) {
                if (!expanded && (System.nanoTime() >= expandAt || received == futures.size)) {
                    submit(remaining)
                    expanded = true
                }
                if (expanded && received == futures.size) break
                val waitUntil = if (expanded) deadline else minOf(expandAt, deadline)
                val pending = completion.poll(
                    (waitUntil - System.nanoTime()).coerceAtLeast(0L),
                    TimeUnit.NANOSECONDS,
                ) ?: continue
                received++
                val (latency, url) = runCatching { pending.get() }.getOrNull() ?: continue
                if (latency > 0) {
                    VpnBus.update { it.copy(latencyMs = latency) }
                    return true to url
                }
            }
            return false to ""
        } finally {
            futures.forEach { it.cancel(true) }
        }
    }

    private fun startStatsLoop() {
        statsJob?.cancel()
        upTotal = 0L
        downTotal = 0L
        statsJob = scope.launch {
            while (isActive) {
                val (up, down) = XrayCore.trafficDelta()
                upTotal += up
                downTotal += down
                VpnBus.update { it.copy(upBytes = upTotal, downBytes = downTotal) }
                updateNotification()
                delay(2_000)
            }
        }
    }

    private fun stopVpn() {
        VpnBus.update { it.copy(state = VpnState.STOPPING) }
        statsJob?.cancel()
        XrayCore.stop()
        // نکته مهم (از v2rayNG): stopSelf باید قبل از بستن اینترفیس باشد
        stopSelf()
        scope.launch {
            delay(150)
            closeTun()
            VpnBus.reset()
        }
        stopForeground(android.app.Service.STOP_FOREGROUND_REMOVE)
    }

    private fun closeTun() {
        try {
            tunInterface?.close()
        } catch (t: Throwable) {
            AppLog.e("closing tun failed", t)
        }
        tunInterface = null
    }

    // ----------------------------------------------------------- notification

    private fun startForegroundCompat(group: String, serverName: String) {
        val label = listOf(group, serverName).filter { it.isNotBlank() }.joinToString(" / ")
        val notification = buildNotification(getString(R.string.notif_title_connecting), label)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (t: Throwable) {
            // اگر سیستم نوع سرویس را قبول نکرد، بدون نوع هم امتحان می‌کنیم
            AppLog.e("startForeground with type failed", t)
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification() {
        val status = VpnBus.status.value
        if (status.state != VpnState.CONNECTED) return
        val label = listOf(status.groupName, status.profileName).filter { it.isNotBlank() }.joinToString(" / ")
        val text = "$label  ↑${PersianText.bytes(status.upBytes)}  ↓${PersianText.bytes(status.downBytes)}"
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification(getString(R.string.notif_title_connected), text))
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, NzVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setLargeIcon(notificationLogo)
            .setColor(ContextCompat.getColor(this, R.color.nz_copper))
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.notif_stop), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    }

    companion object {
        const val CHANNEL_ID = "nzvpn_status"
        const val ACTION_START = "nzvpn.nzbozorg.ir.action.START"
        const val ACTION_STOP = "nzvpn.nzbozorg.ir.action.STOP"

        private const val EXTRA_CONFIG = "extra_config"
        private const val EXTRA_NAME = "extra_name"
        private const val EXTRA_PROFILE_ID = "extra_profile_id"
        private const val EXTRA_GROUP = "extra_group"
        private const val EXTRA_LATENCY = "extra_latency"
        private const val NOTIF_ID = 1001

        /** آدرس کلاینت داخل تونل (انتخاب v2rayNG: 10.10.14.1/30 هم رایج است) */
        const val TUN_ADDRESS = "10.10.10.2"

        fun start(
            context: Context, configJson: String, name: String,
            profileId: String, group: String, latencyMs: Long = -1L,
        ) {
            val intent = Intent(context, NzVpnService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CONFIG, configJson)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_PROFILE_ID, profileId)
                .putExtra(EXTRA_GROUP, group)
                .putExtra(EXTRA_LATENCY, latencyMs)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            // ترتیب درست: پیام STOP به سرویس، تا هسته تمیز خاموش شود و بعد اینترفیس بسته شود
            val intent = Intent(context, NzVpnService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
                .onFailure { context.stopService(Intent(context, NzVpnService::class.java)) }
        }
    }
}
