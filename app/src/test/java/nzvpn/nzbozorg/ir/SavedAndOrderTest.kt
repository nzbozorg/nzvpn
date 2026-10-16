package nzvpn.nzbozorg.ir

import nzvpn.nzbozorg.ir.brand.BrandConfig
import nzvpn.nzbozorg.ir.data.GroupOrder
import nzvpn.nzbozorg.ir.data.GroupUpdates
import nzvpn.nzbozorg.ir.data.RepoVisibility
import nzvpn.nzbozorg.ir.data.SavedProfiles
import nzvpn.nzbozorg.ir.data.ServerStore
import nzvpn.nzbozorg.ir.model.Profile
import nzvpn.nzbozorg.ir.model.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedAndOrderTest {
    private fun profile(id: String = "first") = Profile(
        id = id, name = "🇮🇷 سرورِ اصل", group = "ساب اصلی", protocol = Protocol.VLESS,
        address = "one.example", port = 443, link = "vless://example#اصل",
    )

    @Test fun starredCopyRemainsAfterItsSubscriptionIsRemovedOrRefreshed() {
        val original = profile()
        val saved = SavedProfiles.save(original)
        assertEquals(BrandConfig.GROUP_SAVED, saved.groupName)
        assertEquals("ساب اصلی", saved.originGroup)
        assertEquals("ساب اصلی", SavedProfiles.connectedGroup(saved))
        assertTrue(SavedProfiles.isStarred(listOf(original, saved), original))
        assertEquals("saved-first", saved.id)

        val merged = ServerStore.merge(listOf(original), listOf(saved))
        assertEquals(2, merged.size) // کپی باید از dedupe سرور اصلی عبور کند.
        val remaining = merged.filterNot { it.groupName == "ساب اصلی" }
        assertEquals(listOf(saved), remaining)
        assertEquals("🇮🇷 سرورِ اصل", remaining.single().name)

        val refreshed = GroupUpdates.replace(merged, "ساب اصلی", listOf(profile("new")))
        assertTrue(refreshed.any { it.id == saved.id && it.originGroup == "ساب اصلی" })
    }

    @Test fun automaticRefreshKeepsSelectedAndConnectedProfilesWithoutDuplicates() {
        val old = listOf(profile("selected"), profile("connected"))
        val latest = listOf(profile("new"))
        assertEquals(listOf("new", "selected", "connected"),
            GroupUpdates.retainForRefresh(old, latest, "selected", "connected").map { it.id })
        assertEquals(listOf("selected", "connected"),
            GroupUpdates.retainForRefresh(old, old, "selected", "connected").map { it.id })
        assertEquals(listOf("new"),
            GroupUpdates.retainForRefresh(old, latest, "", "").map { it.id })
    }

    @Test fun savedFolderStartsFirstButUserOrderingIsPreserved() {
        val initial = GroupOrder.merge(emptyList(), listOf("ب", BrandConfig.GROUP_SAVED, "الف"))
        assertEquals(BrandConfig.GROUP_SAVED, initial.first())
        val shifted = GroupOrder.move(initial, BrandConfig.GROUP_SAVED, 2)
        assertEquals(BrandConfig.GROUP_SAVED, shifted.last())
        assertEquals(shifted, GroupOrder.merge(shifted, initial.reversed()))
        assertEquals(shifted, GroupOrder.move(shifted, BrandConfig.GROUP_SAVED, 20))
    }

    @Test fun defaultSubscriptionDoesNotComeBackDuringAutomaticRefresh() {
        val location = "https://example.org/sub"
        val suppressed = setOf(location)
        assertFalse(RepoVisibility.visible(location, suppressed))
        assertTrue(RepoVisibility.visible(location, emptySet())) // فقط بازیابیِ صریح
        assertTrue(RepoVisibility.visible("https://example.org/another", suppressed))
    }
}
