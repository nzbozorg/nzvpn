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

    @Test fun everyServerGetsEnoughTimeAndLateNativeAnswersCannotRewriteResults() = runBlocking {
        val events = Collections.synchronizedList(mutableListOf<AutoSelector.Progress>())
        val started = System.nanoTime()
        val result = AutoSelector.testGroupResponsive(
            profiles = listOf(profile("slow"), profile("fast"), profile("last")),
            options = XrayConfigBuilder.Options(), budgetMs = 220L,
            probe = { p, _ ->
                if (p.id == "slow") {
                    // شبیه JNI: قطع‌کردن ترد، پاسخِ دیررس را متوقف نمی‌کند.
                    val until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(650)
                    while (System.nanoTime() < until) {
                        try { Thread.sleep(12) } catch (_: InterruptedException) { }
                    }
                } else Thread.sleep(if (p.id == "fast") 30 else 80)
                150L to PingTargets.GLOBAL.first()
            },
            onProgress = { events += it },
        )
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        assertTrue("انتظار بی‌دلیل برای JNI دیررس: $elapsed ms", elapsed < 530)
        assertEquals(listOf(1, 2, 3), events.map { it.done })
        assertEquals(listOf(-1L, 150L, 150L), result.map { it.latencyMs })
        assertEquals("پاسخِ سریع باید قبل از انقضای سرور کند نمایش داده شود",
            "fast", events.first().latest.id)
        Thread.sleep(680)
        assertEquals(3, events.size)
        assertEquals(-1L, result.first().latencyMs)
    }

    @Test fun budgetHasSensibleUpperBoundAndNativeCapacityRemainsLimited() = runBlocking {
        val invalid = runCatching {
            AutoSelector.testGroupResponsive(emptyList(), XrayConfigBuilder.Options(), 15_001L)
        }
        assertTrue(invalid.isFailure)
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val launches = AtomicInteger()
        val results = AutoSelector.testGroupResponsive(
            profiles = (1..12).map { profile("server-$it") },
            options = XrayConfigBuilder.Options(), budgetMs = 60L, parallelism = 12,
            probe = { _, _ ->
                launches.incrementAndGet()
                val number = active.incrementAndGet()
                peak.updateAndGet { maxOf(it, number) }
                try {
                    val until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(310)
                    while (System.nanoTime() < until) {
                        try { Thread.sleep(12) } catch (_: InterruptedException) { }
                    }
                    210L to PingTargets.GLOBAL.first()
                } finally { active.decrementAndGet() }
            },
        )
        assertEquals(12, results.size)
        assertTrue("بیش از ظرفیت هسته اجرا شد: ${peak.get()}",
            peak.get() in 1..AutoSelector.MAX_CONCURRENT_PROBES)
        assertTrue(results.all { it.latencyMs < 0 })
        Thread.sleep(350)
        assertEquals("هر ۱۲ سرور باید فرصت تست بگیرند", 12, launches.get())
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
