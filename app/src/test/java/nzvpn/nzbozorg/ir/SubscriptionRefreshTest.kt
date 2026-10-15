package nzvpn.nzbozorg.ir

import com.sun.net.httpserver.HttpServer
import nzvpn.nzbozorg.ir.data.GroupUpdates
import nzvpn.nzbozorg.ir.net.RepoClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

class SubscriptionRefreshTest {
    @Test fun oneFailedSubscriptionDoesNotDiscardTheOthersAndIndividualRefreshIsIsolated() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        fun respond(path: String, code: Int, body: String) {
            server.createContext(path) { exchange ->
                val bytes = body.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(code, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        val original = "vless://11111111-1111-1111-1111-111111111111@one.example:443?encryption=none#قدیمی"
        val latest = "vless://22222222-2222-2222-2222-222222222222@two.example:443?encryption=none#تازه"
        respond("/configs.txt", 200,
            "sub: http://127.0.0.1:$port/good#اول\n" +
                "sub: http://127.0.0.1:$port/bad#دوم\n" +
                "sub: http://127.0.0.1:$port/empty#سوم\n")
        respond("/good", 200, latest)
        respond("/bad", 500, "down")
        respond("/empty", 200, "")
        server.start()
        try {
            val client = RepoClient()
            val result = client.refresh("http://127.0.0.1:$port/configs.txt")
            assertEquals(3, result.subscriptions.size)
            assertNotNull(result.subscriptions[0].profiles)
            assertNull(result.subscriptions[1].profiles)
            assertNull(result.subscriptions[2].profiles)
            assertTrue(result.warnings.any { it.contains("قبلی") })

            val previousFirst = nzvpn.nzbozorg.ir.parser.ShareLinkParser.parseOrThrow(original)
                .copy(group = "اول")
            val previousSecond = previousFirst.copy(id = "second", group = "دوم", name = "نباید پاک شود")
            val existing = listOf(previousFirst, previousSecond)
            val fresh = client.fetchSubscription("http://127.0.0.1:$port/good", "اول").profiles
            val updated = GroupUpdates.replace(existing, "اول", fresh)
            assertEquals(2, updated.size)
            assertEquals("دوم", updated.first().group)
            assertEquals("نباید پاک شود", updated.first().name)
            assertEquals("تازه", updated.last().name)
            assertTrue(runCatching { client.fetchSubscription("http://127.0.0.1:$port/bad", "دوم") }.isFailure)
            assertEquals(existing[1], updated.first())
        } finally {
            server.stop(0)
        }
    }
}
