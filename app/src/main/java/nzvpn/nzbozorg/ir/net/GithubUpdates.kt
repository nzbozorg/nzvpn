package nzvpn.nzbozorg.ir.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import nzvpn.nzbozorg.ir.brand.BrandConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** فقط انتشار رسمی همین مخزن، با فایل APK مشترک و نام دقیق پذیرفته می‌شود. */
data class GithubRelease(
    val version: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
) {
    val fileName: String get() = "NZVPN-$version-universal-release.apk"
}

object ReleaseCatalog {
    const val MAX_APK_BYTES = 120L * 1024L * 1024L
    private val versionPattern = Regex("^[vV]?(\\d{1,6})\\.(\\d{1,6})\\.(\\d{1,6})$")
    private val digestPattern = Regex("^sha256:([a-fA-F0-9]{64})$")

    fun normalizeVersion(tag: String): String {
        val match = versionPattern.matchEntire(tag.trim())
            ?: throw IOException("شمارهٔ نسخهٔ انتشار گیت‌هاب معتبر نیست")
        return match.groupValues.drop(1).joinToString(".") { it.toInt().toString() }
    }

    fun isNewer(remote: String, local: String): Boolean {
        val current = normalizeVersion(local.substringBefore('-')).split('.').map(String::toInt)
        val latest = normalizeVersion(remote).split('.').map(String::toInt)
        for (i in 0..2) {
            if (latest[i] != current[i]) return latest[i] > current[i]
        }
        return false
    }

    fun parse(json: String): GithubRelease {
        val release = JSONObject(json)
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
            throw IOException("این نسخه هنوز انتشار پایدار نیست")
        }
        val version = normalizeVersion(release.optString("tag_name"))
        val filename = "NZVPN-$version-universal-release.apk"
        val assets = release.optJSONArray("assets") ?: throw IOException("فایل APK در انتشار پیدا نشد")
        val asset = (0 until assets.length()).asSequence()
            .mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { it.optString("name") == filename }
            ?: throw IOException("فایل «$filename» در انتشار گیت‌هاب نیست")
        val url = asset.optString("browser_download_url")
        requireGithubDownloadUrl(url)
        if (URI(url).path.substringAfterLast('/') != filename) {
            throw IOException("نشانی فایل با نام بستهٔ انتشار یکی نیست")
        }
        val bytes = asset.optLong("size", -1L)
        if (bytes !in 1..MAX_APK_BYTES) throw IOException("حجم فایل به‌روزرسانی معتبر نیست")
        val rawDigest = asset.optString("digest", "").takeUnless { it == "null" }.orEmpty()
        val sha256 = if (rawDigest.isBlank()) null else {
            digestPattern.matchEntire(rawDigest)?.groupValues?.get(1)?.lowercase()
                ?: throw IOException("اثر انگشت فایل انتشار نامعتبر است")
        }
        return GithubRelease(version, url, bytes, sha256)
    }

    fun requireGithubDownloadUrl(url: String) {
        val uri = runCatching { URI(url) }.getOrNull()
        if (uri == null || !uri.scheme.equals("https", ignoreCase = true) ||
            !uri.host.equals("github.com", ignoreCase = true) ||
            uri.port != -1 || uri.userInfo != null || uri.query != null || uri.fragment != null ||
            !uri.path.startsWith("/nzbozorg/nzvpn/releases/download/") ||
            uri.normalize().path != uri.path
        ) throw IOException("نشانی فایل انتشار به مخزن رسمی تعلق ندارد")
    }
}

/** بررسی نسخهٔ پایدار و دریافت APK از GitHub Releases با محدودیت حجم و تطبیق هش. */
class GithubUpdateClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.MINUTES)
        // مسیر تغییر نشانیِ دانلود را خودمان بررسی می‌کنیم (فقط HTTPS/میزبان گیت‌هاب).
        .followRedirects(false)
        .build(),
) {
    private fun releaseFile(url: String): Response {
        var current = URI(url)
        repeat(5) {
            val host = current.host.orEmpty().lowercase()
            if (current.scheme != "https" || current.port != -1 ||
                (host != "github.com" && !host.endsWith(".githubusercontent.com"))
            ) throw IOException("لینک دانلود از گیت‌هاب خارج شد")
            val request = Request.Builder().url(current.toString())
                .header("User-Agent", "NZVPN/${BrandConfig.VERSION_NAME}").build()
            val response = client.newCall(request).execute()
            if (response.code !in 300..399) return response
            val location = response.header("Location")
            response.close()
            if (location.isNullOrBlank()) throw IOException("نشانی دانلود نسخهٔ تازه ناقص است")
            current = current.resolve(location)
        }
        throw IOException("تغییر نشانی دانلود بیش از حد مجاز بود")
    }

    suspend fun latest(): GithubRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(BrandConfig.RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "NZVPN/${BrandConfig.VERSION_NAME}")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                404 -> throw IOException("هنوز نسخه‌ای در انتشارهای گیت‌هاب نیست؛ اول یه انتشار رسمی بسازید")
                403, 429 -> throw IOException("گیت‌هاب فعلاً درخواست را محدود کرده؛ کمی بعد دوباره امتحان کنید")
            }
            if (!response.isSuccessful) throw IOException("بررسی نسخه از گیت‌هاب ناموفق بود (${response.code})")
            val body = response.body
            if (body.contentLength() > 1_000_000) throw IOException("پاسخ گیت‌هاب بیش از حد بزرگ است")
            val text = body.string()
            if (text.length > 1_000_000) throw IOException("پاسخ گیت‌هاب بیش از حد بزرگ است")
            ReleaseCatalog.parse(text)
        }
    }

    suspend fun download(
        context: Context, release: GithubRelease,
        onProgress: (Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        ReleaseCatalog.requireGithubDownloadUrl(release.downloadUrl)
        if (URI(release.downloadUrl).path.substringAfterLast('/') != release.fileName ||
            release.sizeBytes !in 1..ReleaseCatalog.MAX_APK_BYTES
        ) throw IOException("اطلاعات فایل به‌روزرسانی معتبر نیست")
        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(folder, release.fileName)
        val part = File(folder, "${release.fileName}.part")
        part.delete()
        target.delete()
        try {
            releaseFile(release.downloadUrl).use { response ->
                if (!response.isSuccessful) throw IOException("دریافت فایل ناموفق بود (${response.code})")
                val finalUrl = response.request.url
                val allowedHost = finalUrl.host == "github.com" ||
                    finalUrl.host.endsWith(".githubusercontent.com")
                if (finalUrl.scheme != "https" || !allowedHost) {
                    throw IOException("دانلود به نشانی ناشناخته هدایت شد")
                }
                val body = response.body
                if (body.contentLength() > ReleaseCatalog.MAX_APK_BYTES) {
                    throw IOException("حجم بستهٔ دانلودی بیش از حد مجاز است")
                }
                val hash = MessageDigest.getInstance("SHA-256")
                var copied = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    part.outputStream().buffered().use { output ->
                        val chunk = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(chunk)
                            if (count == -1) break
                            copied += count
                            if (copied > ReleaseCatalog.MAX_APK_BYTES) {
                                throw IOException("حجم بستهٔ دانلودی بیش از حد مجاز است")
                            }
                            output.write(chunk, 0, count)
                            hash.update(chunk, 0, count)
                            val percent = (copied * 100 / release.sizeBytes).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
                if (copied != release.sizeBytes) throw IOException("حجم فایل دانلودشده با گیت‌هاب یکی نیست")
                val actualHash = hash.digest().joinToString("") { "%02x".format(it) }
                if (release.sha256 != null && actualHash != release.sha256) {
                    throw IOException("اثر انگشت فایل دانلودشده با گیت‌هاب یکی نیست")
                }
            }
            if (!part.renameTo(target)) throw IOException("فایل به‌روزرسانی ذخیره نشد")
            target
        } catch (t: Throwable) {
            part.delete()
            target.delete()
            throw t
        }
    }
}
