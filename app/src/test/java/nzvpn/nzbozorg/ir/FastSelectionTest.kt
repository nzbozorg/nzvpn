package nzvpn.nzbozorg.ir

import kotlinx.coroutines.runBlocking
import nzvpn.nzbozorg.ir.core.XrayConfigBuilder
import nzvpn.nzbozorg.ir.data.GroupNaming
import nzvpn.nzbozorg.ir.data.GroupUpdates
import nzvpn.nzbozorg.ir.model.Profile
import nzvpn.nzbozorg.ir.model.Protocol
import nzvpn.nzbozorg.ir.net.PingTargets
import nzvpn.nzbozorg.ir.parser.ConfigImporter
import nzvpn.nzbozorg.ir.service.AutoSelector
import nzvpn.nzbozorg.ir.util.PersianText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FastSelectionTest {
    private fun profile(id: String, name: String = "سرور $id", group: String = "ساب") = Profile(
        id = id, name = name, protocol = Protocol.VLESS,
        address = "$id.example.com", port = 443, group = group,
    )

    @Test fun countryFlagsAreExtractedWithoutShowingBrokenEmojiLetters() {
        val german = profile("de", "🇩🇪 DE-01 | امن")
        assertEquals("DE", german.flagCountryCode)
        assertEquals("DE-01 | امن", german.displayName)
        val encoded = URLEncoder.encode("🇮🇷 تهران", "UTF-8")
        assertEquals("IR", PersianText.countryFlagCode(encoded))
        assertEquals("US", PersianText.countryFlagCode("🌐 🇺🇸 سریع"))
        assertNull(PersianText.countryFlagCode("نام بدون پرچم"))
        assertEquals("آلمان", PersianText.cleanName("🇩🇪  آلمان  "))
    }

    @Test fun raceQueueIncludesEveryServerAndInterleavesFolders() {
        val candidates = (1..8).map { profile("a$it", group = "الف") } +
            profile("b1", group = "ب") + profile("c1", group = "پ")
        val order = AutoSelector.orderForRace(candidates)
        assertEquals(candidates.map { it.id }.toSet(), order.map { it.id }.toSet())
        assertEquals(candidates.size, order.size)
        assertEquals(listOf("a1", "b1", "c1", "a2"), order.take(4).map { it.id })
    }

    @Test fun raceActuallyStopsWaitingForUninterruptibleProbeAtDeadline() = runBlocking {
        val targets = listOf(profile("one"), profile("two"), profile("three"))
        val began = System.nanoTime()
        val result = AutoSelector.raceFirstResponding(
            candidates = targets,
            options = XrayConfigBuilder.Options(),
            parallelism = 2,
            deadlineMs = 120L,
            probe = { _, _ ->
                // شبیه‌سازی JNI: قطع ترد را نادیده می‌گیرد.
                val until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(900)
                while (System.nanoTime() < until) {
                    try { Thread.sleep(20) } catch (_: InterruptedException) { /* بومی قابل‌لغو نیست */ }
                }
                240L to PingTargets.IRANIAN.first()
            },
        )
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began)
        assertNull(result)
        assertTrue("Selection waited $elapsed ms instead of 120 ms", elapsed < 700L)
        Thread.sleep(950) // ترد بومی شبیه‌سازی‌شده آزاد شود.
    }

    @Test fun raceDoesNotFallBackToCachedServerWithoutFreshSuccessfulProbe() = runBlocking {
        val cached = profile("cached").withTestResult(20)
        val others = (1..9).map { profile("server-$it") }
        val visited = Collections.synchronizedSet(mutableSetOf<String>())
        val result = AutoSelector.raceFirstResponding(
            candidates = listOf(cached) + others,
            options = XrayConfigBuilder.Options(),
            parallelism = 3,
            probe = { p, _ ->
                visited += p.id
                -1L to ""
            },
        )
        assertNull(result)
        assertEquals((listOf(cached) + others).map { it.id }.toSet(), visited)
    }

    @Test fun raceReachesLaterServersAndPicksFirstRealResponse() = runBlocking {
        val targets = (1..8).map { profile("server-$it") }
        val visited = Collections.synchronizedSet(mutableSetOf<String>())
        val result = AutoSelector.raceFirstResponding(
            candidates = targets,
            options = XrayConfigBuilder.Options(),
            parallelism = 2,
            probe = { p, _ ->
                visited += p.id
                if (p.id == "server-7") 90L to PingTargets.IRANIAN.first() else -1L to ""
            },
        )
        assertEquals("server-7", result?.profile?.id)
        assertTrue(visited.containsAll((1..7).map { "server-$it" }))
    }

    @Test fun raceReturnsEarliestPositiveCompletionNotPreviouslyCachedLowestLatency() = runBlocking {
        val slow = profile("slow").withTestResult(3)
        val quick = profile("quick")
        val result = AutoSelector.raceFirstResponding(
            candidates = listOf(slow, quick),
            options = XrayConfigBuilder.Options(),
            parallelism = 2,
            probe = { p, _ ->
                if (p.id == "slow") Thread.sleep(350) else Thread.sleep(30)
                (if (p.id == "slow") 3L else 140L) to PingTargets.IRANIAN.first()
            },
        )
        assertEquals("quick", result?.profile?.id)
        assertEquals(140L, result?.profile?.latencyMs)
    }

    @Test fun concurrencyIsBoundedEvenWithLargeQueue() = runBlocking {
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val targets = (1..21).map { profile("server-$it") }
        val result = AutoSelector.raceFirstResponding(
            candidates = targets,
            options = XrayConfigBuilder.Options(),
            parallelism = 3,
            probe = { _, _ ->
                val now = active.incrementAndGet()
                peak.updateAndGet { maxOf(it, now) }
                try { Thread.sleep(10) } finally { active.decrementAndGet() }
                -1L to ""
            },
        )
        assertNull(result)
        assertTrue(peak.get() in 1..3)
    }

    @Test fun manualSpeedTestRunsStrictlyOneByOne() = runBlocking {
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val progress = mutableListOf<Int>()
        val results = AutoSelector.testAll(
            profiles = (1..7).map { profile("server-$it") },
            parallelism = 1,
            options = XrayConfigBuilder.Options(),
            fast = false,
            probe = { _, _, _ ->
                val now = active.incrementAndGet()
                peak.updateAndGet { maxOf(it, now) }
                try { Thread.sleep(5) } finally { active.decrementAndGet() }
                100L to PingTargets.IRANIAN.first()
            },
            onProgress = { progress += it.done },
        )
        assertEquals(1, peak.get())
        assertEquals((1..7).toList(), progress)
        assertTrue(results.all { it.latencyMs == 100L })
    }

    @Test fun fileAndBatchNamesAndTargetedReplacementAreStable() {
        assertEquals("tehran-vpn.txt", GroupNaming.fromFile("/docs/tehran-vpn.txt"))
        assertEquals("کانفیگ‌ها.txt", GroupNaming.fromFile("کانفیگ‌ها.txt"))
        val examples = listOf(profile("a", "🇩🇪 آلمانِ سریع"), profile("b", "تهران - ویژه"))
        val group = GroupNaming.fromPaste(examples)
        assertTrue(group.contains("آلمانِ سریع"))
        assertTrue(group.contains("تهران"))
        assertEquals("tehran-vpn.txt (۲)", GroupNaming.unique("tehran-vpn.txt", listOf("tehran-vpn.txt")))
        assertEquals("آلمان", GroupNaming.key("🇩🇪 آلمان"))
        assertEquals("🇩🇪 آلمان (۲)", GroupNaming.unique("🇩🇪 آلمان", listOf("آلمان")))
        assertEquals("hidden", GroupNaming.key(".hidden"))
        val longName = "الف".repeat(80)
        val secondName = GroupNaming.unique(longName, listOf(GroupNaming.key(longName)))
        assertTrue(GroupNaming.key(secondName) != GroupNaming.key(longName))

        val original = listOf(profile("a", group = "الف").withTestResult(150),
            profile("b", group = "الف"), profile("other", group = "ب"))
        val updated = GroupUpdates.replace(original, "الف", listOf(profile("a"), profile("new")))
        assertEquals(listOf("other", "a", "new"), updated.map { it.id })
        assertEquals(150L, updated.first { it.id == "a" }.latencyMs)
        assertEquals("ب", updated.first().group)
        assertEquals("الف", updated.last().group)
        val flagged = GroupUpdates.replace(
            listOf(profile("de", group = "🇩🇪 آلمان")), "آلمان",
            listOf(profile("new-de", group = "🇩🇪 آلمان")),
        )
        assertEquals("🇩🇪 آلمان", flagged.single().group)
        assertTrue(runCatching { GroupUpdates.replace(original, "الف", emptyList()) }.isFailure)
        assertEquals(3, original.size) // ورودی پس از شکست دست‌نخورده می‌ماند.
    }

    @Test fun multiSubscriptionPasteKeepsEachSourceAndMixedConfigs() {
        val subs = "https://one.example/sub#تهران\nhttps://two.example/sub#آلمان"
        val result = ConfigImporter.classify(subs, "دستی")
        assertTrue(result is ConfigImporter.Result.Subscriptions)
        val links = (result as ConfigImporter.Result.Subscriptions).links
        assertEquals(listOf("تهران", "آلمان"), links.map { it.name })
        val mixed = ConfigImporter.classify(
            "vless://11111111-1111-1111-1111-111111111111@vpn.example:443?encryption=none#خوب\n$subs",
            "فایل.txt",
        )
        assertTrue(mixed is ConfigImporter.Result.Mixed)
        mixed as ConfigImporter.Result.Mixed
        assertEquals(1, mixed.profiles.size)
        assertEquals("فایل.txt", mixed.profiles.single().group)
        assertEquals(2, mixed.links.size)
    }

    @Test fun fullSubscriptionDeadlineDoesNotWaitForBlockedNativeCall() = runBlocking {
        val began = System.nanoTime()
        val results = AutoSelector.testAll(
            profiles = listOf(profile("a"), profile("b")),
            parallelism = 2,
            options = XrayConfigBuilder.Options(),
            deadlineMs = 120L,
            probe = { _, _, _ ->
                try { Thread.sleep(900) } catch (_: InterruptedException) { Thread.sleep(800) }
                200L to PingTargets.IRANIAN.first()
            },
        )
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began)
        assertTrue(results.all { it.latencyMs == -1L })
        assertTrue("Subscription test waited $elapsed ms instead of 120 ms", elapsed < 700L)
    }
}
