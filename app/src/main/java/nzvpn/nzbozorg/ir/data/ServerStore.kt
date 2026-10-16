package nzvpn.nzbozorg.ir.data

import android.content.Context
import android.util.AtomicFile
import nzvpn.nzbozorg.ir.brand.BrandConfig
import nzvpn.nzbozorg.ir.core.XrayConfigBuilder
import nzvpn.nzbozorg.ir.model.Profile
import nzvpn.nzbozorg.ir.model.Protocol
import nzvpn.nzbozorg.ir.parser.ShareLinkParser
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * ذخیره‌سازی سرورها روی دیسک.
 *
 * - لینک‌ها به‌صورت متن (به‌همراه نام پوشه) ذخیره می‌شوند و در هر اجرا دوباره پارس
 *   می‌شوند؛ یعنی اگر پارسر بهتر شود، همهٔ سرورهای قدیمی هم به‌روز می‌شوند.
 * - کانفیگ‌های کامل (JSON) در files/custom_servers/<id>.json نگه داشته می‌شوند.
 * - نتیجهٔ تست تاخیر در SharedPreferences می‌ماند تا لیست بعد از بستن برنامه هم مرتب بماند.
 *
 * قالب فایل servers.json (نسخهٔ ۴): لینک‌ها، کانفیگ‌های کامل، منبع رفرش و
 * شناسه/پوشهٔ اصلی کپی‌های ذخیره‌شده. نسخه‌های ۱ تا ۳ نیز قابل خواندن‌اند.
 */
object ServerStore {

    private const val SNAPSHOT = "servers.json"
    private const val CUSTOM_DIR = "custom_servers"

    data class StoredData(val profiles: List<Profile>, val sources: List<GroupSource>)

    fun load(context: Context): List<Profile> = loadData(context).profiles

    fun loadData(context: Context): StoredData {
        val snapshot = readSnapshot(context)
        val latencies = parseLatency(AppPrefs.get(context).latencyMap)
        val profiles = mutableListOf<Profile>()

        for (item in snapshot.items) {
            try {
                val parsed = ShareLinkParser.parseOrThrow(item.link)
                val group = item.group.ifBlank { parsed.group }
                profiles += parsed.copy(
                    group = group,
                    id = if (group == BrandConfig.GROUP_SAVED && item.id.startsWith("saved-"))
                        item.id else parsed.id,
                    originGroup = item.originGroup,
                )
            } catch (t: Throwable) {
                // لینک ناخوانا را نادیده بگیر
            }
        }
        for (item in snapshot.custom) {
            readCustom(context, item.id)?.let { profiles += it.copy(
                group = item.group, originGroup = item.originGroup,
            ) }
        }
        return StoredData(
            profiles = profiles.map { profile ->
                latencies[profile.id]?.let { profile.withTestResult(it.latency, at = it.testedAt) } ?: profile
            },
            sources = snapshot.sources,
        )
    }

    fun save(context: Context, profiles: List<Profile>, sources: Collection<GroupSource>) {
        val items = JSONArray()
        val custom = JSONArray()
        val latency = StringBuilder()

        for (profile in profiles) {
            when {
                profile.protocol == Protocol.CUSTOM -> {
                    writeCustom(context, profile)
                    custom.put(JSONObject().put("id", profile.id).put("group", profile.group)
                        .put("originGroup", profile.originGroup))
                }
                profile.link.isNotBlank() ->
                    items.put(JSONObject().put("id", profile.id).put("link", profile.link)
                        .put("group", profile.group).put("originGroup", profile.originGroup))
            }
            if (profile.latencyMs > 0) {
                latency.append(profile.id).append('=').append(profile.latencyMs)
                    .append('@').append(profile.lastTestedAt).append(';')
            }
        }
        val groups = profiles.map { it.groupName }.toSet()
        val sourceArray = JSONArray()
        for (source in sources.distinctBy { it.group }) {
            if (source.group !in groups || source.location.isBlank()) continue
            sourceArray.put(JSONObject().put("group", source.group)
                .put("kind", source.kind.name).put("location", source.location))
        }
        writeSnapshot(context, items, custom, sourceArray)
        AppPrefs.get(context).latencyMap = latency.toString()
    }

    /**
     * ادغام دو لیست سرور: اولی (های کاربر) باقی می‌ماند، از دومی فقط تکراری‌ها حذف می‌شوند.
     * نتیجه بر اساس پوشه مرتب می‌شود.
     */
    fun merge(existing: List<Profile>, fresh: List<Profile>): List<Profile> {
        // کپیِ ستاره‌دار باید کنار سرور اصلی باقی بماند؛ در عین حال هر یک فقط
        // یک‌بار در جای خودش دیده شود.
        fun key(profile: Profile): String =
            (if (profile.groupName == BrandConfig.GROUP_SAVED) "saved|" else "") + profile.dedupeKey
        val seen = existing.map(::key).toMutableSet()
        val merged = existing.toMutableList()
        for (profile in fresh) {
            if (seen.add(key(profile))) merged += profile
        }
        return merged
    }

    /** ساخت پروفایل از یک کانفیگ JSON کامل */
    fun buildCustomProfile(json: String, group: String = BrandConfig.GROUP_MANUAL): Profile {
        val (address, port) = XrayConfigBuilder.describeCustom(json)
        val id = "custom-" + ShareLinkParser.sha1(json)
        return Profile(
            id = id,
            name = if (address.isBlank()) "کانفیگ کامل" else address,
            protocol = Protocol.CUSTOM,
            address = address,
            port = port,
            group = group,
            rawJson = json,
        )
    }

    fun addCustomJson(context: Context, json: String, group: String = BrandConfig.GROUP_MANUAL): Profile {
        val profile = buildCustomProfile(json, group)
        File(customDir(context), "${profile.id}.json").writeText(json)
        return profile
    }

    // ------------------------------------------------------------------ داخلی

    private class Item(val id: String, val link: String, val group: String, val originGroup: String = "")

    private class Snapshot(
        val items: List<Item>, val custom: List<Item>, val sources: List<GroupSource>,
    )

    private fun snapshotFile(context: Context) = File(context.filesDir, SNAPSHOT)

    private fun customDir(context: Context) = File(context.filesDir, CUSTOM_DIR).apply { mkdirs() }

    private fun readSnapshot(context: Context): Snapshot {
        val file = snapshotFile(context)
        if (!file.exists() && !File("${file.path}.bak").exists()) {
            return Snapshot(emptyList(), emptyList(), emptyList())
        }
        return try {
            val raw = AtomicFile(file).readFully().toString(Charsets.UTF_8).trim()
            // نسخهٔ ۱: آرایهٔ سادهٔ لینک‌ها
            if (raw.startsWith("[")) {
                val array = JSONArray(raw)
                val items = (0 until array.length()).mapNotNull { i ->
                    val link = array.optString(i)
                    if (link.isBlank()) null else Item(ShareLinkParser.sha1(link), link, "")
                }
                return Snapshot(items, emptyList(), emptyList())
            }

            val json = JSONObject(raw)
            val itemsArray = json.optJSONArray("items") ?: JSONArray()
            val customArray = json.optJSONArray("custom") ?: JSONArray()

            val items = (0 until itemsArray.length()).mapNotNull { i ->
                when (val element = itemsArray.opt(i)) {
                    is JSONObject -> Item(
                        id = element.optString("id").ifBlank {
                            ShareLinkParser.sha1(element.optString("link"))
                        },
                        link = element.optString("link"),
                        group = element.optString("group"),
                        originGroup = element.optString("originGroup"),
                    )
                    is String -> Item(ShareLinkParser.sha1(element), element, "")
                    else -> null
                }
            }
            val customs = (0 until customArray.length()).mapNotNull { i ->
                when (val element = customArray.opt(i)) {
                    is JSONObject -> Item(element.optString("id"), "", element.optString("group"),
                        element.optString("originGroup"))
                    is String -> Item(element, "", "")
                    else -> null
                }
            }
            val sourceArray = json.optJSONArray("sources") ?: JSONArray()
            val sources = (0 until sourceArray.length()).mapNotNull { i ->
                val source = sourceArray.optJSONObject(i) ?: return@mapNotNull null
                val group = source.optString("group")
                val location = source.optString("location")
                val kind = runCatching {
                    GroupSource.Kind.valueOf(source.optString("kind"))
                }.getOrNull()
                if (group.isBlank() || location.isBlank() || kind == null) null
                else GroupSource(group, kind, location)
            }
            Snapshot(items, customs, sources)
        } catch (t: Throwable) {
            Snapshot(emptyList(), emptyList(), emptyList())
        }
    }

    private fun writeSnapshot(context: Context, items: JSONArray, custom: JSONArray, sources: JSONArray) {
        val json = JSONObject().put("v", 4).put("items", items)
            .put("custom", custom).put("sources", sources)
        val atomic = AtomicFile(snapshotFile(context))
        val stream = atomic.startWrite()
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (t: Throwable) {
            atomic.failWrite(stream)
            throw t
        }
    }

    private fun writeCustom(context: Context, profile: Profile) {
        val json = profile.rawJson ?: return
        File(customDir(context), "${profile.id}.json").writeText(json)
    }

    private fun readCustom(context: Context, id: String): Profile? {
        if (id.isBlank()) return null
        val file = File(customDir(context), "$id.json")
        if (!file.exists()) return null
        val raw = file.readText()
        val (address, port) = XrayConfigBuilder.describeCustom(raw)
        return Profile(
            id = id,
            name = if (address.isBlank()) "کانفیگ کامل" else address,
            protocol = Protocol.CUSTOM,
            address = address,
            port = port,
            rawJson = raw,
            rawJsonPath = file.absolutePath,
        )
    }

    private data class RecordedLatency(val latency: Long, val testedAt: Long)

    /** قالب تازه: id=میلی‌ثانیه@زمان؛ قالب قدیمی id=میلی‌ثانیه هم سازگار است. */
    private fun parseLatency(raw: String): Map<String, RecordedLatency> {
        if (raw.isBlank()) return emptyMap()
        val map = HashMap<String, RecordedLatency>()
        for (entry in raw.split(';')) {
            val idx = entry.indexOf('=')
            if (idx <= 0) continue
            val payload = entry.substring(idx + 1)
            val latency = payload.substringBefore('@').toLongOrNull() ?: continue
            if (latency <= 0) continue
            // زمان نسخه‌های قبلی مشخص نبود؛ نتیجهٔ قدیمی برای «اتصال فوری» تازه نیست.
            val testedAt = payload.substringAfter('@', "0").toLongOrNull() ?: 0L
            map[entry.substring(0, idx)] = RecordedLatency(latency, testedAt)
        }
        return map
    }
}
