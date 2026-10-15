package nzvpn.nzbozorg.ir

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import nzvpn.nzbozorg.ir.service.NzVpnService
import nzvpn.nzbozorg.ir.util.AppLog

/**
 * کلاس Application برنامه.
 * کارش: ساختن کانال اعلان و ثبت لاگ راه‌اندازی.
 */
class NzApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        AppLog.d("NZVPN راه‌اندازی شد (${Build.MANUFACTURER} ${Build.MODEL} · API ${Build.VERSION.SDK_INT})")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            NzVpnService.CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
