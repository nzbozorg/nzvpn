package nzvpn.nzbozorg.ir

import kotlinx.coroutines.runBlocking
import nzvpn.nzbozorg.ir.net.GithubUpdateClient
import nzvpn.nzbozorg.ir.net.InstalledReleaseNotesPolicy
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledReleaseNotesTest {
    private val current = """{
        "tag_name":"v1.1.0", "draft":false, "prerelease":false,
        "name":"نسخهٔ ۱.۱.۰", "body":"- نمایش حجم ساب\n- لغو اتصال"
    }""".trimIndent()

    @Test fun onlyAnUpgradeNotAFreshInstallRequestsTheMatchingReleaseAndRetriesLater() {
        val should = { first: Long, updated: Long, shown: String, attemptVersion: String,
            attempted: Long, now: Long ->
            InstalledReleaseNotesPolicy.shouldRequest(
                "1.1.0", first, updated, shown, attemptVersion, attempted, now,
            )
        }
        assertFalse(should(100, 100, "", "", 0, 10_000)) // نصب تازه
        assertFalse(should(0, 300, "", "", 0, 10_000))
        assertTrue(should(100, 300, "1.0.9", "", 0, 10_000))
        assertFalse(should(100, 300, "1.1.0", "", 0, 10_000))
        assertFalse(should(100, 300, "", "1.1.0", 1000, 10_000))
        assertTrue(should(100, 300, "", "1.1.0", 1000,
            1000 + InstalledReleaseNotesPolicy.RETRY_AFTER_MS))
        assertTrue(should(100, 300, "", "1.0.9", 9000, 10_000)) // تلاش نسخهٔ قبل
    }

    @Test fun notesMustBelongToTheInstalledStableTagAndContainNoApkAsset() {
        val notes = InstalledReleaseNotesPolicy.parse(current, "1.1.0")
        assertEquals("1.1.0", notes.version)
        assertEquals("نسخهٔ ۱.۱.۰", notes.title)
        assertTrue(notes.body.contains("لغو اتصال"))
        assertTrue(runCatching { InstalledReleaseNotesPolicy.parse(
            current.replace("v1.1.0", "v1.0.9"), "1.1.0",
        ) }.isFailure)
        assertTrue(runCatching { InstalledReleaseNotesPolicy.parse(
            current.replace("\"prerelease\":false", "\"prerelease\":true"), "1.1.0",
        ) }.isFailure)
        assertTrue(runCatching { InstalledReleaseNotesPolicy.parse(
            current.replace("\"draft\":false", "\"draft\":true"), "1.1.0",
        ) }.isFailure)
    }

    @Test fun githubClientTriesOnlyInstalledVersionTagWithAndWithoutV() = runBlocking {
        val paths = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            paths += request.url.encodedPath
            val found = request.url.encodedPath.endsWith("/1.1.0")
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (found) 200 else 404).message(if (found) "OK" else "Not found")
                .body((if (found) current else "{}").toResponseBody())
                .build()
        }.build()
        val notes = GithubUpdateClient(http).installedReleaseNotes("1.1.0")
        assertEquals("1.1.0", notes?.version)
        assertEquals(listOf(
            "/repos/nzbozorg/nzvpn/releases/tags/v1.1.0",
            "/repos/nzbozorg/nzvpn/releases/tags/1.1.0",
        ), paths)
        assertTrue(paths.none { it.contains("latest") || it.contains("1.0.9") })
    }
}
