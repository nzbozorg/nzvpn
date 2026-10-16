package nzvpn.nzbozorg.ir.net

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import nzvpn.nzbozorg.ir.BuildConfig
import nzvpn.nzbozorg.ir.MainActivity
import nzvpn.nzbozorg.ir.R
import nzvpn.nzbozorg.ir.data.AppPrefs
import nzvpn.nzbozorg.ir.util.PersianText
import java.util.concurrent.TimeUnit

/** بررسی دوره‌ای حتی زمانی که برنامه بسته است؛ WorkManager در حالت Doze ممکن است تأخیر داشته باشد. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val context = applicationContext
        val client = GithubUpdateClient()
        val release = client.latest()
        if (ReleaseCatalog.isNewer(release.version, BuildConfig.VERSION_NAME)) {
            val ready = client.downloadedFile(context, release)
            val valid = ready.isFile && ready.length() == release.sizeBytes &&
                runCatching { TrustedApk.verify(context, ready); true }.getOrDefault(false)
            if (!valid) {
                ready.delete()
                UpdateAlerts.notifyIfNeeded(context, release)
            }
        }
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}

object UpdateAlerts {
    const val ACTION_OPEN_UPDATE = "nzvpn.nzbozorg.ir.OPEN_UPDATE"
    private const val CHANNEL = "nzvpn_updates"
    private const val NOTIFICATION_ID = 109
    private const val UNIQUE_WORK = "nzvpn_update_check_6h"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }

    fun notifyIfNeeded(context: Context, release: GithubRelease) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (AppPrefs.get(context).lastNotifiedVersion == release.version) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "به‌روزرسانی‌های NZVPN", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_UPDATE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val open = PendingIntent.getActivity(
            context, 109, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_nzvpn)
            .setContentTitle("نسخهٔ تازهٔ NZVPN رسید")
            .setContentText("نسخهٔ ${PersianText.digits(release.version)} آمادهٔ دریافته.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_stat_nzvpn),
                "بازکردن برنامه و دانلود", open,
            ).build())
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        AppPrefs.get(context).lastNotifiedVersion = release.version
    }

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
