package nzvpn.nzbozorg.ir

import nzvpn.nzbozorg.ir.net.ReleaseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GithubUpdateTest {
    private fun response(
        tag: String = "v1.0.9", file: String = "NZVPN-1.0.9-universal-release.apk",
        url: String = "https://github.com/nzbozorg/nzvpn/releases/download/v1.0.9/NZVPN-1.0.9-universal-release.apk",
        size: Long = 42_700_000L,
        digest: String? = "sha256:${"a".repeat(64)}",
        prerelease: Boolean = false,
    ): String = """
        {
          "tag_name":"$tag", "draft":false, "prerelease":$prerelease,
          "assets":[
            {"name":"readme.txt", "browser_download_url":"https://github.com/nzbozorg/nzvpn/readme.txt", "size":99},
            {"name":"$file", "browser_download_url":"$url", "size":$size${if (digest != null) ", \"digest\":\"$digest\"" else ""}}
          ]
        }
    """.trimIndent()

    @Test fun stableReleaseFromExactRepositoryAndUniversalAssetIsAccepted() {
        val release = ReleaseCatalog.parse(response())
        assertEquals("1.0.9", release.version)
        assertEquals("NZVPN-1.0.9-universal-release.apk", release.fileName)
        assertEquals("a".repeat(64), release.sha256)
        assertTrue(ReleaseCatalog.isNewer(release.version, "1.0.8"))
        assertFalse(ReleaseCatalog.isNewer("v1.0.8", "1.0.8"))
        assertFalse(ReleaseCatalog.isNewer("1.0.7", "1.0.8"))
        assertTrue(ReleaseCatalog.isNewer("v1.1.0", "1.0.99"))
        assertNull(ReleaseCatalog.parse(response(digest = null)).sha256)
    }

    @Test fun mismatchedAssetOrPrereleaseIsRejected() {
        assertTrue(runCatching { ReleaseCatalog.parse(response(prerelease = true)) }.isFailure)
        assertTrue(runCatching { ReleaseCatalog.parse(response(file = "app-debug.apk")) }.isFailure)
        assertTrue(runCatching { ReleaseCatalog.parse(response(tag = "main")) }.isFailure)
        assertTrue(runCatching { ReleaseCatalog.parse(response(size = ReleaseCatalog.MAX_APK_BYTES + 1)) }.isFailure)
        assertTrue(runCatching { ReleaseCatalog.parse(response(digest = "sha256:BAD")) }.isFailure)
    }

    @Test fun lookalikeHttpRedirectAndWrongRepoCannotSupplyAnUpdate() {
        val valid = "https://github.com/nzbozorg/nzvpn/releases/download/v1.0.9/NZVPN-1.0.9-universal-release.apk"
        val attempts = listOf(
            valid.replace("https:", "http:"),
            valid.replace("github.com/", "github.com.evil.example/"),
            valid.replace("/nzbozorg/nzvpn/", "/another/repo/"),
            valid.replace("/v1.0.9/", "/v1.0.9/../"),
            valid.replace("github.com/", "username@github.com/"),
            valid.replace(".apk", ".apk?file=bad"),
        )
        attempts.forEach { url ->
            val result = runCatching { ReleaseCatalog.parse(response(url = url)) }
            assertTrue("نشانی ناامن پذیرفته شد: $url", result.exceptionOrNull() is IOException)
        }
        assertTrue(runCatching { ReleaseCatalog.parse(response(
            url = valid.replace("NZVPN-1.0.9-universal-release.apk", "other.apk"),
        )) }.isFailure)
    }
}
