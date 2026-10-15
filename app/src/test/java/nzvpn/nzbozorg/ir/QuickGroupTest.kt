package nzvpn.nzbozorg.ir

import kotlinx.coroutines.runBlocking
import nzvpn.nzbozorg.ir.core.XrayConfigBuilder
import nzvpn.nzbozorg.ir.model.Profile
import nzvpn.nzbozorg.ir.model.Protocol
import nzvpn.nzbozorg.ir.net.PingTargets
import nzvpn.nzbozorg.ir.service.AutoSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class QuickGroupTest {
    private fun profile(id: String) = Profile(id = id, name = "🇮🇷 $id", group = "پوشه",
        protocol = Protocol.VLESS, address = "$id.example.com", port = 443)

    @Test fun eachServerHasItsOwnShortBudgetAndLateNativeAnswersCannotRewriteResults() = runBlocking {
        val events = Collections.synchronizedList(mutableListOf<AutoSelector.Progress>())
        val started = System.nanoTime()
        val result = AutoSelector.testGroupWithinOneSecond(
            profiles = listOf(profile("slow"), profile("fast"), profile("last")),
            options = XrayConfigBuilder.Options(), budgetMs = 80L,
            probe = { p, _ ->
                if (p.id == "slow") {
                    // شبیه JNI: قطع‌کردن ترد هم پاسخِ دیررس را متوقف نمی‌کند.
                    val until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(350)
                    while (System.nanoTime() < until) {
                        try { Thread.sleep(12) } catch (_: InterruptedException) { }
                    }
                } else Thread.sleep(8)
                150L to PingTargets.GLOBAL.first()
            },
            onProgress = { events += it },
        )
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        assertTrue("انتظار برای JNI دیررس: $elapsed ms", elapsed < 280)
        assertEquals(listOf(1, 2, 3), events.map { it.done })
        assertEquals(listOf(-1L, 150L, 150L), result.map { it.latencyMs })
        assertEquals("هسته هنوز ممکن است کار کند، ولی نتیجه نباید تغییر کند", -1L,
            events.first().latest.latencyMs)
        Thread.sleep(360)
        assertEquals(3, events.size)
        assertEquals(-1L, result.first().latencyMs)
    }

    @Test fun budgetCannotBeRaisedOverOneSecondAndNativeCapacityRemainsLimited() = runBlocking {
        val invalid = runCatching {
            AutoSelector.testGroupWithinOneSecond(emptyList(), XrayConfigBuilder.Options(), 1_001L)
        }
        assertTrue(invalid.isFailure)
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val launches = AtomicInteger()
        val results = AutoSelector.testGroupWithinOneSecond(
            profiles = (1..12).map { profile("server-$it") },
            options = XrayConfigBuilder.Options(), budgetMs = 35L,
            probe = { _, _ ->
                launches.incrementAndGet()
                val number = active.incrementAndGet()
                peak.updateAndGet { maxOf(it, number) }
                try {
                    val until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(440)
                    while (System.nanoTime() < until) {
                        try { Thread.sleep(12) } catch (_: InterruptedException) { }
                    }
                    210L to PingTargets.GLOBAL.first()
                } finally { active.decrementAndGet() }
            },
        )
        assertEquals(12, results.size)
        assertTrue("بیش از ظرفیت هسته اجرا شد: ${peak.get()}", peak.get() in 1..AutoSelector.MAX_CONCURRENT_PROBES)
        assertTrue(results.all { it.latencyMs < 0 })
        Thread.sleep(470) // تماس‌های بومی شبیه‌سازی‌شده آزاد شوند.
        assertTrue("آزمون لغوشده بعداً شروع شد: ${launches.get()}",
            launches.get() <= AutoSelector.MAX_CONCURRENT_PROBES)
    }

    @Test fun raceDoesNotPutOldCacheAheadOfServersInOtherFolders() {
        val cached = profile("cached").copy(group = "الف").withTestResult(9L)
        val candidates = listOf(profile("first").copy(group = "الف"),
            profile("second").copy(group = "ب"), cached,
            profile("fourth").copy(group = "ب"))
        assertEquals(listOf("first", "second", "cached", "fourth"),
            AutoSelector.orderForRace(candidates).map { it.id })
        assertEquals(listOf(PingTargets.GLOBAL.first(), PingTargets.IRANIAN.first()),
            PingTargets.quickGroupAttempts())
    }
}
