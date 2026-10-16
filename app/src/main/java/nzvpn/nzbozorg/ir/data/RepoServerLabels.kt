package nzvpn.nzbozorg.ir.data

import nzvpn.nzbozorg.ir.model.Profile
import nzvpn.nzbozorg.ir.service.AutoSelector
import nzvpn.nzbozorg.ir.util.PersianText

/**
 * فقط سرورهای سابِ معرفی‌شده در configs.txt نام کوتاه می‌گیرند. نام واقعی لینک
 * و پرچمش دست‌نخورده می‌ماند؛ برچسب صرفاً نمایشی است و در دیسک نوشته نمی‌شود.
 * شماره‌ها بر اساس ترتیب کنونی فهرست (پینگ کمتر در بالا) دوباره محاسبه می‌شوند.
 */
object RepoServerLabels {
    fun apply(
        profiles: List<Profile>, sources: Map<String, GroupSource>,
        testingGroup: String = "", testingStartedAt: Long = 0L,
    ): List<Profile> {
        val repoGroups = sources.values.asSequence()
            .filter { it.kind == GroupSource.Kind.REPO && it.location != RepoVisibility.DIRECT_FILE }
            .map { it.group }.toSet()
        val aliases = profiles.asSequence()
            .filter { it.groupName in repoGroups }
            .groupBy { it.groupName }
            .flatMap { (groupName, members) ->
                val ranked = if (groupName == testingGroup && testingStartedAt > 0) {
                    AutoSelector.sortByLiveQuality(members, testingStartedAt)
                } else AutoSelector.sortByQuality(members)
                ranked.mapIndexed { index, profile ->
                    profile.id to "سرور ${PersianText.digits(index + 1)}"
                }
            }.toMap()
        return profiles.map { profile ->
            val label = aliases[profile.id].orEmpty()
            if (profile.displayAlias == label) profile else profile.copy(displayAlias = label)
        }
    }
}
