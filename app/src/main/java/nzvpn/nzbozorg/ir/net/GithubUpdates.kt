package nzvpn.nzbozorg.ir.net

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import nzvpn.nzbozorg.ir.brand.BrandConfig
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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

/** ادامهٔ دانلود فقط با پاسخ Range معتبر؛ پاسخ ۲۰۰ یعنی دانلود کامل از ابتدا. */
object UpdateRange {
    private val pattern = Regex("^bytes (\\d+)-(\\d+)/(\\d+)$")

    fun valid(contentRange: String?, start: Long, total: Long): Boolean {
        val parts = pattern.matchEntire(contentRange.orEmpty())?.groupValues ?: return false
        val first = parts[1].toLongOrNull() ?: return false
        val last = parts[2].toLongOrNull() ?: return false
        val length = parts[3].toLongOrNull() ?: return false
        return first == start && length == total && last in first until total
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
    private val activeDownloadCall = AtomicReference<Call?>()

    /** دکمهٔ توقف باید درخواست شبکهٔ بلاک‌شده را هم فوراً آزاد کند. */
    fun interruptDownload() { activeDownloadCall.get()?.cancel() }

    private fun releaseFile(url: String, offset: Long, onCall: (Call) -> Unit): Response {
        var current = URI(url)
        repeat(5) {
            val host = current.host.orEmpty().lowercase()
            if (current.scheme != "https" || current.port != -1 ||
                (host != "github.com" && !host.endsWith(".githubusercontent.com"))
            ) throw IOException("لینک دانلود از گیت‌هاب خارج شد")
            val builder = Request.Builder().url(current.toString())
                .header("User-Agent", "NZVPN/${BrandConfig.VERSION_NAME}")
            if (offset > 0L) builder.header("Range", "bytes=$offset-")
            val call = client.newCall(builder.build())
            onCall(call)
            activeDownloadCall.set(call)
            val response = call.execute()
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

    /** تنها tag نسخهٔ نصب‌شده؛ آخرین انتشارِ نسخه‌ای دیگر نباید جای آن نشان داده شود. */
    suspend fun installedReleaseNotes(installed: String): InstalledReleaseNotes? = withContext(Dispatchers.IO) {
        val version = ReleaseCatalog.normalizeVersion(installed)
        for (tag in listOf("v$version", version)) {
            val request = Request.Builder()
                .url("https://api.github.com/repos/nzbozorg/nzvpn/releases/tags/$tag")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NZVPN/${BrandConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    404 -> return@use // هنوز منتشر نشده؛ املای بدون v را هم امتحان کن.
                    403, 429 -> throw IOException("گیت‌هاب فعلاً محدودیت درخواست دارد")
                }
                if (!response.isSuccessful) throw IOException("دریافت یادداشت انتشار ناموفق بود (${response.code})")
                val body = response.body
                if (body.contentLength() > 1_000_000) throw IOException("یادداشت انتشار بیش از حد بزرگ است")
                val text = body.string()
                if (text.length > 1_000_000) throw IOException("یادداشت انتشار بیش از حد بزرگ است")
                return@withContext InstalledReleaseNotesPolicy.parse(text, version)
            }
        }
        null
    }

    fun partialFile(context: Context, release: GithubRelease): File =
        File(File(context.cacheDir, "updates"), "${release.fileName}.part")

    fun downloadedFile(context: Context, release: GithubRelease): File =
        File(File(context.cacheDir, "updates"), release.fileName)

    fun partialPercent(context: Context, release: GithubRelease): Int =
        ((partialFile(context, release).length().coerceIn(0L, release.sizeBytes) * 100) /
            release.sizeBytes).toInt()

    fun discard(context: Context, release: GithubRelease) {
        interruptDownload()
        partialFile(context, release).delete()
        downloadedFile(context, release).delete()
    }

    /** توقف، قطعهٔ دریافت‌شده را حفظ می‌کند؛ ادامه با Range و هش‌سنجی کل فایل. */
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
        target.delete()
        if (part.length() >= release.sizeBytes) part.delete()
        var offset = part.length()
        var active: Call? = null
        var discardOnFailure = false
        try {
            releaseFile(release.downloadUrl, offset) { active = it }.use { response ->
                currentCoroutineContext().ensureActive()
                val finalUrl = response.request.url
                val allowedHost = finalUrl.host == "github.com" ||
                    finalUrl.host.endsWith(".githubusercontent.com")
                if (finalUrl.scheme != "https" || !allowedHost) {
                    discardOnFailure = true
                    throw IOException("دانلود به نشانی ناشناخته هدایت شد")
                }
                val append = when {
                    offset > 0L && response.code == 206 &&
                        UpdateRange.valid(response.header("Content-Range"), offset, release.sizeBytes) -> true
                    response.code == 200 -> false // سرور Range را نپذیرفت؛ از صفر شروع کن.
                    else -> {
                        discardOnFailure = true
                        throw IOException("پاسخ ادامهٔ دانلود معتبر نیست (${response.code})")
                    }
                }
                if (!append) { part.delete(); offset = 0L }
                val body = response.body
                if (body.contentLength() > release.sizeBytes - offset) {
                    discardOnFailure = true
                    throw IOException("حجم بستهٔ دانلودی بیش از حد مجاز است")
                }
                val hash = MessageDigest.getInstance("SHA-256")
                if (append) part.inputStream().buffered().use { input ->
                    val bytes = ByteArray(32 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(bytes)
                        if (n < 0) break
                        hash.update(bytes, 0, n)
                    }
                }
                var copied = offset
                var lastPercent = -1
                body.byteStream().use { input ->
                    java.io.FileOutputStream(part, append).buffered().use { output ->
                        val chunk = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(chunk)
                            if (count == -1) break
                            copied += count
                            if (copied > release.sizeBytes) {
                                discardOnFailure = true
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
                currentCoroutineContext().ensureActive()
                if (copied != release.sizeBytes) {
                    // قطعهٔ نیمه‌کاره برای ادامهٔ بعدی باقی می‌ماند.
                    throw IOException("دانلود کامل نشد؛ می‌تونی ادامه‌اش بدی")
                }
                val actualHash = hash.digest().joinToString("") { "%02x".format(it) }
                if (release.sha256 != null && actualHash != release.sha256) {
                    discardOnFailure = true
                    throw IOException("اثر انگشت فایل دانلودشده با گیت‌هاب یکی نیست")
                }
            }
            currentCoroutineContext().ensureActive()
            if (!part.renameTo(target)) throw IOException("فایل به‌روزرسانی ذخیره نشد")
            target
        } catch (t: Throwable) {
            target.delete()
            if (discardOnFailure) part.delete()
            if (!currentCoroutineContext().isActive && t !is CancellationException) {
                throw CancellationException("دریافت متوقف شد").apply { initCause(t) }
            }
            throw t
        } finally {
            active?.let { activeDownloadCall.compareAndSet(it, null) }
        }
    }
}
