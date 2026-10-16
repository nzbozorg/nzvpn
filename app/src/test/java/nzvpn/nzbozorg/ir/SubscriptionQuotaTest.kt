package nzvpn.nzbozorg.ir

import com.sun.net.httpserver.HttpServer
import nzvpn.nzbozorg.ir.data.GroupSource
import nzvpn.nzbozorg.ir.data.SubscriptionQuotaParser
import nzvpn.nzbozorg.ir.data.SubscriptionQuotaVisibility
import nzvpn.nzbozorg.ir.net.RepoClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

class SubscriptionQuotaTest {
    @Test fun validProviderHeaderShowsUsedAndRemainingWithoutCountingVpnSessionTraffic() {
        val quota = SubscriptionQuotaParser.parse(
            "upload=1073741824; download=2147483648; total=8589934592; expire=1735689600",
            checkedAtMillis = 1234L,
        )!!
        assertEquals(3_221_225_472L, quota.usedBytes)
        assertEquals(5_368_709_120L, quota.remainingBytes)
        assertEquals(0.375f, quota.usedFraction, 0.0001f)
        assertEquals(1_735_689_600L, quota.expireSeconds)
        assertEquals(1234L, quota.checkedAtMillis)
    }

    @Test fun noSupportInvalidValuesAndOverflowAreHandledWithoutFakeNumbers() {
        for (header in listOf<String?>(null, "", "upload=2; download=3", "total=100",
                "upload=2; download=3; total=0", "upload=-1; download=3; total=100",
                "upload=x; download=3; total=100", "upload=2; download=3; total=bad")) {
            assertNull("نباید حجم ساختگی برای $header داشته باشیم", SubscriptionQuotaParser.parse(header))
        }
        val overUsed = SubscriptionQuotaParser.parse(
            "DOWNLOAD=${Long.MAX_VALUE}; upload=15; total=200; foo=bar",
        )!!
        assertEquals(Long.MAX_VALUE, overUsed.usedBytes)
        assertEquals(0L, overUsed.remainingBytes)
        assertEquals(1f, overUsed.usedFraction, 0f)
        assertEquals(0L, SubscriptionQuotaParser.parse("upload=0; download=200; total=200")!!.remainingBytes)
        assertEquals(95L, SubscriptionQuotaParser.parse(
            "upload=2; download=3; total=100; expire=invalid",
        )!!.remainingBytes)
    }

    @Test fun defaultRepoNeverGetsVolumeButtonEvenIfItSendsTheSameHeader() {
        val quota = SubscriptionQuotaParser.parse("upload=10; download=5; total=100")
        assertNotNull(quota)
        assertTrue(SubscriptionQuotaVisibility.canShow(
            GroupSource("شخصی", GroupSource.Kind.SUBSCRIPTION, "https://sub.example"), quota,
        ))
        assertFalse(SubscriptionQuotaVisibility.canShow(
            GroupSource("پیش‌فرض", GroupSource.Kind.REPO, "https://sub.example"), quota,
        ))
        assertFalse(SubscriptionQuotaVisibility.canShow(
            GroupSource("فایل", GroupSource.Kind.FILE, "content://file"), quota,
        ))
        assertFalse(SubscriptionQuotaVisibility.canShow(
            GroupSource("بدون آمار", GroupSource.Kind.SUBSCRIPTION, "https://sub.example"), null,
        ))
    }

    @Test fun fetchedUserSubscriptionReadsQuotaFromTheSameHttpResponse() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val link = "vless://11111111-1111-1111-1111-111111111111@one.example:443?encryption=none#نمونه"
        server.createContext("/with-info") { exchange ->
            exchange.responseHeaders.add("Subscription-Userinfo", "upload=100; download=300; total=1000")
            val bytes = link.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/without-info") { exchange ->
            val bytes = link.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = RepoClient()
            val base = "http://127.0.0.1:${server.address.port}"
            val fetched = client.fetchSubscriptionWithQuota("$base/with-info", "شخصی")
            assertEquals(1, fetched.parsed.profiles.size)
            assertEquals(400L, fetched.quota!!.usedBytes)
            assertEquals(600L, fetched.quota.remainingBytes)
            assertNull(client.fetchSubscriptionWithQuota("$base/without-info", "شخصی").quota)
            assertEquals(1, client.fetchSubscription("$base/with-info", "پیش‌فرض").profiles.size)
        } finally {
            server.stop(0)
        }
    }
}
