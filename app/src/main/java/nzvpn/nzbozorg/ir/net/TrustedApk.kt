package nzvpn.nzbozorg.ir.net

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** نصب بی‌صدا نداریم: پیش از بازکردن نصب‌کنندهٔ اندروید، بسته و امضا را می‌سنجیم. */
object TrustedApk {
    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else info.signatures
        return signatures?.map { signature ->
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }?.toSet().orEmpty()
    }

    @Suppress("DEPRECATION")
    fun verify(context: Context, apk: File) {
        if (!apk.isFile || apk.length() !in 1..ReleaseCatalog.MAX_APK_BYTES) {
            throw IOException("فایل به‌روزرسانی معتبر نیست")
        }
        ZipFile(apk).use { zip ->
            val abis = zip.entries().asSequence().map { it.name }
                .filter { it.startsWith("lib/") && it.endsWith("/libgojni.so") }
                .map { it.split('/')[1] }.toSet()
            if (abis != setOf("arm64-v8a", "armeabi-v7a")) {
                throw IOException("این APK مشترک با هر دو معماری گوشی سازگار نیست")
            }
        }
        val pm = context.packageManager
        val flag = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flag)
            ?: throw IOException("بستهٔ دانلودی APK معتبر نیست")
        val installed = pm.getPackageInfo(context.packageName, flag)
        if (archive.packageName != context.packageName) {
            throw IOException("نام بستهٔ دانلودی با برنامه یکی نیست")
        }
        @Suppress("DEPRECATION")
        fun code(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode
        } else info.versionCode.toLong()
        if (code(archive) <= code(installed)) {
            throw IOException("نسخهٔ دانلودی از برنامهٔ فعلی جدیدتر نیست")
        }
        val original = signers(installed)
        if (original.isEmpty() || original != signers(archive)) {
            throw IOException("امضای نسخهٔ دانلودی با امضای برنامه یکی نیست؛ نصب انجام نمی‌شود")
        }
    }
}
