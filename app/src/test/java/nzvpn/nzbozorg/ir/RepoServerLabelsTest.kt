package nzvpn.nzbozorg.ir

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nzvpn.nzbozorg.ir.core.XrayConfigBuilder
import nzvpn.nzbozorg.ir.data.GroupSource
import nzvpn.nzbozorg.ir.data.GroupUpdates
import nzvpn.nzbozorg.ir.data.RepoServerLabels
import nzvpn.nzbozorg.ir.model.Profile
import nzvpn.nzbozorg.ir.model.Protocol
import nzvpn.nzbozorg.ir.net.PingTargets
import nzvpn.nzbozorg.ir.service.AutoSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RepoServerLabelsTest {
    private val repo = "ساب مخزن"
    private val user = "ساب من"
    private val sources = mapOf(
        repo to GroupSource(repo, GroupSource.Kind.REPO, "https://repo.example/sub"),
        user to GroupSource(user, GroupSource.Kind.SUBSCRIPTION, "https://mine.example/sub"),
    )

    private fun profile(id: String, group: String = repo, name: String = "🇩🇪 نام اصلی $id") = Profile(
        id = id, group = group, name = name, link = "vless://$id@example.com:443#$name",
        protocol = Protocol.VLESS, address = "example.com", port = 443,
    )

    @Test fun onlyRepoSubscriptionsAreAliasedWhileRawNamesLinksAndFlagsRemainIntact() {
        val a = profile("a")
        val b = profile("b", group = user, name = "🇺🇸 اسم دلخواه")
        val c = profile("c", group = "فایل مخزن")
        val labeled = RepoServerLabels.apply(listOf(a, b, c), sources)
        assertEquals("سرور ۱", labeled[0].displayName)
        assertEquals("DE", labeled[0].flagCountryCode)
        assertEquals(a.name, labeled[0].name)
        assertEquals(a.link, labeled[0].link)
        assertEquals("اسم دلخواه", labeled[1].displayName)
        assertEquals("US", labeled[1].flagCountryCode)
        assertEquals("نام اصلی c", labeled[2].displayName)
        // در صورتی که کاربر منبع ساب را جایگزین کند، نامِ نمایشی دوباره خام می‌شود.
        assertEquals("نام اصلی a", RepoServerLabels.apply(
            labeled, mapOf(repo to GroupSource(repo, GroupSource.Kind.SUBSCRIPTION, "https://mine.example")),
        ).first().displayName)
    }

    @Test fun refreshOfRepoSubscriptionReappliesAliasToUpdatedRawFlagAndLink() {
        val previous = RepoServerLabels.apply(listOf(profile("a")), sources)
        val incoming = profile("a", name = "🇯🇵 نام تازه")
            .copy(link = "vless://a@example.com:443#%F0%9F%87%AF%F0%9F%87%B5")
        val refreshed = GroupUpdates.replace(previous, repo, listOf(incoming))
        val visible = RepoServerLabels.apply(refreshed, sources).single()
        assertEquals("سرور ۱", visible.displayName)
        assertEquals("JP", visible.flagCountryCode)
        assertEquals(incoming.name, visible.name)
        assertEquals(incoming.link, visible.link)
    }

    @Test fun firstFreshResponderRisesAboveOldCacheAndRanksWithNewPings() {
        val started = 10_000L
        val old = profile("old").withTestResult(20, at = started - 1)
        val slow = profile("slow")
        val fast = profile("fast")
        var current = listOf(old, slow, fast)
        current = current.map { if (it.id == "slow") it.withTestResult(430, at = started + 1) else it }
        val first = AutoSelector.sortByLiveQuality(current, started)
        assertEquals(listOf("slow", "old", "fast"), first.map { it.id })
        assertEquals("سرور ۱", RepoServerLabels.apply(current, sources, repo, started)
            .first { it.id == "slow" }.displayName)
        current = current.map { if (it.id == "fast") it.withTestResult(85, at = started + 2) else it }
        assertEquals(listOf("fast", "slow", "old"),
            AutoSelector.sortByLiveQuality(current, started).map { it.id })
        val labels = RepoServerLabels.apply(current, sources, repo, started)
        assertEquals("سرور ۱", labels.first { it.id == "fast" }.displayName)
        assertEquals("سرور ۲", labels.first { it.id == "slow" }.displayName)
        assertEquals("سرور ۳", labels.first { it.id == "old" }.displayName)
        // پس از پایان تست، رتبه فقط به پینگ وابسته است (بدون آستانهٔ رنگی/کیفی).
        assertEquals(listOf("old", "fast", "slow"),
            AutoSelector.sortByQuality(current).map { it.id })
    }

    @Test fun failedNewProbeMovesBelowPendingCachedResult() {
        val started = 100L
        val stale = profile("stale").withTestResult(50, at = started - 1)
        val failed = profile("failed").withTestResult(-1, "بدون پاسخ", at = started + 1)
        val live = profile("live").withTestResult(450, at = started + 2)
        assertEquals(listOf("live", "stale", "failed"),
            AutoSelector.sortByLiveQuality(listOf(failed, stale, live), started).map { it.id })
    }

    @Test fun groupProbeOrdersFastFallbackBeforeSecondIranianWithoutLosingAccuracy() {
        assertEquals(listOf(PingTargets.IRANIAN[0], PingTargets.GLOBAL[0], PingTargets.IRANIAN[1]),
            PingTargets.attempts())
        assertEquals(2, PingTargets.fastAttempts().size)
    }

    @Test fun sequentialGroupTestPublishesSuccessBeforeWaitingForRemainingServers() = runBlocking {
        val profiles = listOf(profile("first"), profile("second"), profile("third"))
        val arrived = CompletableDeferred<List<String>>()
        val secondGate = CountDownLatch(1)
        val testedIds = mutableListOf<String>()
        val job = async(Dispatchers.Default) {
            try {
                AutoSelector.testAll(
                    profiles = profiles, parallelism = 1, options = XrayConfigBuilder.Options(),
                    fast = false,
                    probe = { p, _, fast ->
                        assertFalse(fast)
                        synchronized(testedIds) { testedIds += p.id }
                        if (p.id == "second") secondGate.await(3, TimeUnit.SECONDS)
                        (if (p.id == "first") 160L else 90L) to PingTargets.IRANIAN.first()
                    },
                    onProgress = { p ->
                        if (p.done == 1) {
                            val live = listOf(p.latest) + profiles.filterNot { it.id == p.latest.id }
                            arrived.complete(AutoSelector.sortByLiveQuality(live, p.latest.lastTestedAt)
                                .map { it.id })
                        }
                    },
                )
            } finally { secondGate.countDown() }
        }
        try {
            assertEquals(listOf("first", "second", "third"), withTimeout(2_000) { arrived.await() })
            assertFalse("نتیجهٔ اول نباید منتظر کل پوشه بماند", job.isCompleted)
            assertEquals(listOf("first", "second"), synchronized(testedIds) { testedIds.toList() })
        } finally { secondGate.countDown() }
        assertEquals(3, withTimeout(5_000) { job.await() }.count { it.latencyMs > 0 })
    }
}
