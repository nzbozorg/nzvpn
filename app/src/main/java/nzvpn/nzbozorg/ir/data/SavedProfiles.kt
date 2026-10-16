package nzvpn.nzbozorg.ir.data

import nzvpn.nzbozorg.ir.brand.BrandConfig
import nzvpn.nzbozorg.ir.model.Profile

/** کپی مستقل از ساب: حذف منبع/رفرش نباید علاقه‌مندی کاربر را پاک کند. */
object SavedProfiles {
    private const val PREFIX = "saved-"

    fun isSaved(profile: Profile): Boolean =
        profile.groupName == BrandConfig.GROUP_SAVED && profile.id.startsWith(PREFIX)

    fun originalId(profile: Profile): String =
        if (isSaved(profile)) profile.id.removePrefix(PREFIX) else profile.id

    fun savedId(profile: Profile): String = PREFIX + originalId(profile)

    fun isStarred(profiles: List<Profile>, profile: Profile): Boolean =
        profiles.any { isSaved(it) && it.id == savedId(profile) }

    fun save(profile: Profile): Profile = profile.copy(
        id = savedId(profile),
        group = BrandConfig.GROUP_SAVED,
        originGroup = profile.originGroup.ifBlank { profile.groupName },
        displayAlias = "", // نام عمومی ساب در پوشهٔ اصلی دوباره از روی منبع تولید می‌شود.
    )

    fun connectedGroup(profile: Profile): String =
        profile.originGroup.ifBlank { profile.groupName }
}

/** ترتیب پوشه‌ها مستقل از سرورها ذخیره می‌شود؛ پوشهٔ ذخیره‌شده در اولین ساخت اول می‌آید. */
object GroupOrder {
    fun merge(saved: List<String>, present: Collection<String>): List<String> {
        val names = present.toSet()
        val kept = saved.filter { it in names }.distinct()
        val added = (names - kept.toSet()).sortedWith(
            compareBy({ it == BrandConfig.GROUP_REPO_FILE }, { it }),
        )
        return if (BrandConfig.GROUP_SAVED in added) {
            listOf(BrandConfig.GROUP_SAVED) + kept + added.filterNot { it == BrandConfig.GROUP_SAVED }
        } else kept + added
    }

    fun move(order: List<String>, group: String, steps: Int): List<String> {
        val from = order.indexOf(group)
        if (from < 0 || steps == 0) return order
        val to = (from + steps).coerceIn(0, order.lastIndex)
        if (to == from) return order
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }
}
