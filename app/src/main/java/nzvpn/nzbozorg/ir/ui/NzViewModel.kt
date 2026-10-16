package nzvpn.nzbozorg.ir.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import nzvpn.nzbozorg.ir.BuildConfig
import nzvpn.nzbozorg.ir.brand.BrandConfig
import nzvpn.nzbozorg.ir.core.XrayConfigBuilder
import nzvpn.nzbozorg.ir.core.XrayCore
import nzvpn.nzbozorg.ir.data.AppPrefs
import nzvpn.nzbozorg.ir.data.GroupNaming
import nzvpn.nzbozorg.ir.data.GroupSource
import nzvpn.nzbozorg.ir.data.GroupUpdates
import nzvpn.nzbozorg.ir.data.GroupOrder
import nzvpn.nzbozorg.ir.data.SavedProfiles
import nzvpn.nzbozorg.ir.data.RepoVisibility
import nzvpn.nzbozorg.ir.data.RepoServerLabels
import nzvpn.nzbozorg.ir.data.ServerStore
import nzvpn.nzbozorg.ir.model.Profile
import nzvpn.nzbozorg.ir.model.Protocol
import nzvpn.nzbozorg.ir.net.GithubRelease
import nzvpn.nzbozorg.ir.net.GithubUpdateClient
import nzvpn.nzbozorg.ir.net.UpdateAlerts
import nzvpn.nzbozorg.ir.net.ReleaseCatalog
import nzvpn.nzbozorg.ir.net.RepoClient
import nzvpn.nzbozorg.ir.net.TrustedApk
import nzvpn.nzbozorg.ir.parser.ConfigImporter
import nzvpn.nzbozorg.ir.parser.ShareLinkParser
import nzvpn.nzbozorg.ir.parser.SubscriptionParser
import nzvpn.nzbozorg.ir.service.AutoSelector
import nzvpn.nzbozorg.ir.service.VpnBus
import nzvpn.nzbozorg.ir.service.VpnState
import nzvpn.nzbozorg.ir.util.AppLog
import nzvpn.nzbozorg.ir.util.PersianText
import java.io.File
import java.io.IOException

/** مرحلهٔ راه‌اندازی که در صفحهٔ لودینگ نمایش داده می‌شود */
enum class BootStage { IDLE, CORE, STORAGE, REPO, READY }

enum class UpdatePhase { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, PAUSED, READY, ERROR }

data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val release: GithubRelease? = null,
    val percent: Int = 0,
    val apkPath: String = "",
    val message: String = "",
) {
    val hasPendingUpdate: Boolean get() = release != null && phase in setOf(
        UpdatePhase.CHECKING, UpdatePhase.AVAILABLE, UpdatePhase.DOWNLOADING,
        UpdatePhase.PAUSED, UpdatePhase.ERROR,
    )
}

/** یک پوشه (ساب/گروه) در لیست سرورها */
data class ServerGroup(
    val name: String,
    val profiles: List<Profile>,
    val collapsed: Boolean,
    val source: GroupSource? = null,
    val flagCountryCode: String? = null,
) {
    val best: Long get() = profiles.map { it.latencyMs }.filter { it > 0 }.minOrNull() ?: -1
}

data class NzUiState(
    val bootStage: BootStage = BootStage.IDLE,
    val bootMessage: String = "",
    val coreVersion: String = "",
    val servers: List<Profile> = emptyList(),
    val groups: List<ServerGroup> = emptyList(),
    /** ساب‌ها برای حذف از تنظیمات؛ گروه‌های مخزن هم اینجا دیده می‌شوند. */
    val subscriptions: List<GroupSource> = emptyList(),
    val selectedId: String = "",
    val busy: Boolean = false,
    val busyText: String = "",
    val progress: Float? = null,
    val message: String = "",
    /** پاپ‌آپ «در حال تست» هنگام زدن دکمهٔ اتصال به سریع‌ترین سرور */
    val testing: Boolean = false,
    val testingDone: Int = 0,
    val testingTotal: Int = 0,
    /** پوشه‌ای که الان دارد تست می‌شود (تست جدا برای هر ساب) */
    val testingGroup: String = "",
    val testingGroupStartedAt: Long = 0L,
    val testingGroupDone: Int = 0,
    val testingGroupTotal: Int = 0,
    /** پوشه‌ای که فقط منبع همان در حال دانلود/خواندن است */
    val refreshingGroup: String = "",
) {
    val selected: Profile? get() = servers.firstOrNull { it.id == selectedId }
    val connectedServerName: String get() = selected?.displayName.orEmpty()
}

/**
 * ViewModel اصلی برنامه؛ شیت تنظیمات اطلاعات، مدیریت ساب‌ها، حمایت،
 * اشتراک‌گذاری و آپدیت را نشان می‌دهد؛ تنظیمات هستهٔ VPN در کد ثابت‌اند.
 *
 *  • راه‌اندازی هستهٔ Xray و بارگذاری سرورها
 *  • خواندن خودکار مخزن گیت‌هاب در اولین اجرا
 *  • تست تاخیر با آدرس‌های ایرانی + اتصال به بهترین سرور
 *  • افزودن از کلیپ‌بورد یا فایل، با تشخیص خودکار ساب/سرور
 *  • پوشه‌بندی خودکار ساب‌ها
 */
class NzViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPrefs.get(application)
    private val repo = RepoClient()
    private val githubUpdates = GithubUpdateClient()

    private val _update = MutableStateFlow(UpdateUiState())
    val update: StateFlow<UpdateUiState> = _update.asStateFlow()
    private var updateJob: Job? = null

    private val _ui = MutableStateFlow(NzUiState())
    val ui: StateFlow<NzUiState> = _ui.asStateFlow()

    val vpnStatus = VpnBus.status
    val logs = AppLog.lines

    /** هر ورود همهٔ پوشه‌ها بسته است؛ باز/بستن فقط در همین نشست نگه‌داری می‌شود. */
    private val expanded = mutableSetOf<String>()
    private var groupOrder = prefs.groupOrder()
    private var suppressedRepo = prefs.suppressedRepoLocations().toMutableSet()

    /** منابع و سرورها با هم در یک snapshot ذخیره می‌شوند؛ ذخیره‌ها آخرین وضعیت را می‌خوانند. */
    @Volatile private var sourcesByGroup: Map<String, GroupSource> = emptyMap()
    private val saveMutex = Mutex()

    /** تست اتصال و تست دستی هر کدام جداگانه قابل لغو هستند. */
    private var testJob: Job? = null
    private var groupTestJob: Job? = null
    private var repoJob: Job? = null
    private var groupRefreshJob: Job? = null
    private var foregroundRefreshJob: Job? = null
    private var bootStarted = false

    // ------------------------------------------------------------ راه‌اندازی

    suspend fun boot() {
        if (bootStarted) return
        bootStarted = true
        _ui.value = _ui.value.copy(bootStage = BootStage.CORE, bootMessage = "آماده‌سازی هستهٔ اتصال…")
        val version = withContext(Dispatchers.IO) {
            runCatching {
                XrayCore.init(getApplication())
                XrayCore.version()
            }.getOrElse { t ->
                AppLog.e("راه‌اندازی هسته ناموفق بود", t)
                ""
            }
        }

        _ui.value = _ui.value.copy(
            bootStage = BootStage.STORAGE,
            bootMessage = "خواندن سرورها…",
            coreVersion = version,
        )
        val stored = withContext(Dispatchers.IO) { ServerStore.loadData(getApplication()) }
        val servers = stored.profiles
        sourcesByGroup = stored.sources.associateBy { it.group }
        val selected = prefs.lastSelectedId.takeIf { id -> servers.any { it.id == id } }
            ?: servers.firstOrNull()?.id.orEmpty()
        _ui.value = _ui.value.copy(selectedId = selected)
        refreshGroups(servers)

        // همهٔ ساب‌ها در هر ورود تازه می‌شوند. اگر هنوز هیچ سروری در حافظه نیست،
        // پیش از ورود به خانه تا پایان بارگذاری صبر کن؛ شبکهٔ خراب نباید کاربر را
        // برای همیشه در لودینگ زندانی کند (پس از ۱۵ ثانیه ورود/افزودن دستی آزاد است).
        _ui.value = _ui.value.copy(bootStage = BootStage.REPO, bootMessage = "خواندن ساب‌ها از مخزن…")
        val refresh = refreshAllOnLaunch()
        val completed = if (servers.isEmpty()) {
            withTimeoutOrNull(15_000L) { refresh.join(); true } == true
        } else false
        val count = _ui.value.servers.size
        _ui.value = _ui.value.copy(
            bootStage = BootStage.READY,
            bootMessage = if (count == 0) "می‌تونی سرور رو با + اضافه کنی" else "${fa(count)} سرور آماده است",
            message = if (count == 0 && !completed)
                "بارگذاری ساب‌ها طول کشید؛ می‌تونی از + اضافه کنی یا کمی صبر کنی" else _ui.value.message,
        )
        checkForUpdates()
        AppLog.d("راه‌اندازی کامل شد — $count سرور، رفرش پس‌زمینه: ${!refresh.isCompleted}")
    }

    private fun refreshAllOnLaunch(): Job {
        foregroundRefreshJob = viewModelScope.launch {
            refreshFromRepo(silent = true)
            repoJob?.join()
            // ساب‌ها و فایل‌های کاربر را هم بدون آسیب به پوشهٔ دیگر تازه کن.
            val userSources = sourcesByGroup.values.filter { it.kind != GroupSource.Kind.REPO }.toList()
            for (source in userSources) {
                if (_ui.value.groups.none { it.name == source.group }) continue
                refreshGroup(source.group, silent = true)
                groupRefreshJob?.join()
            }
        }
        return foregroundRefreshJob!!
    }

    /** هر بار ورود به پیش‌زمینه، بدون پرش انتخاب یا جایگزینیِ سرور متصل. */
    fun refreshOnForeground() {
        if (_ui.value.bootStage != BootStage.READY) return
        if (foregroundRefreshJob?.isActive != true) refreshAllOnLaunch()
        checkForUpdates()
    }

    private fun buildGroups(
        servers: List<Profile>,
        testingGroup: String = _ui.value.testingGroup,
        testingStartedAt: Long = _ui.value.testingGroupStartedAt,
    ): List<ServerGroup> {
        val grouped = servers.groupBy { it.groupName }
        return GroupOrder.merge(groupOrder, grouped.keys).map { name ->
            val list = grouped.getValue(name)
            ServerGroup(
                name = name,
                profiles = if (name == testingGroup) {
                    AutoSelector.sortByLiveQuality(list, testingStartedAt)
                } else AutoSelector.sortByQuality(list),
                collapsed = name !in expanded,
                source = sourcesByGroup[name],
                flagCountryCode = list.firstNotNullOfOrNull { PersianText.countryFlagCode(it.group) },
            )
        }
    }

    private fun refreshGroups(servers: List<Profile>) {
        val state = _ui.value
        val labeled = RepoServerLabels.apply(
            servers, sourcesByGroup, state.testingGroup, state.testingGroupStartedAt,
        )
        val ordered = GroupOrder.merge(groupOrder, labeled.map { it.groupName })
        if (ordered != groupOrder) {
            groupOrder = ordered
            prefs.saveGroupOrder(ordered)
        }
        _ui.value = state.copy(
            servers = labeled,
            groups = buildGroups(labeled, state.testingGroup, state.testingGroupStartedAt),
            subscriptions = sourcesByGroup.values.filter { source ->
                source.kind != GroupSource.Kind.FILE && ordered.contains(source.group)
            }.sortedBy { ordered.indexOf(it.group) },
        )
    }

    private fun persist() {
        viewModelScope.launch(Dispatchers.IO) {
            saveMutex.withLock {
                runCatching {
                    ServerStore.save(getApplication(), _ui.value.servers, sourcesByGroup.values)
                }.onFailure { AppLog.e("ذخیرهٔ پوشه‌ها ناموفق", it) }
            }
        }
    }

    private fun occupiedGroups(): Set<String> =
        _ui.value.groups.map { it.name }.toSet() + sourcesByGroup.keys + BrandConfig.GROUP_SAVED

    private fun rawGroup(key: String): String =
        _ui.value.servers.firstOrNull { it.groupName == key }?.group ?: key

    private fun chooseGroup(preferred: String, existingSource: GroupSource? = null): String =
        existingSource?.let { rawGroup(it.group) }
            ?: GroupNaming.unique(preferred, occupiedGroups())

    private fun legacyManualSub(name: String): String? {
        val key = GroupNaming.key(name)
        return key.takeIf {
            it != BrandConfig.GROUP_REPO_FILE && it != BrandConfig.GROUP_MANUAL &&
                !it.startsWith("چسبانده‌شده:") && sourcesByGroup[it] == null &&
                _ui.value.groups.any { group -> group.name == it }
        }?.let { rawGroup(it) }
    }

    private fun keepSelection(servers: List<Profile>, preferred: String = "") {
        val id = _ui.value.selectedId
        val selected = if (servers.any { it.id == id }) id
        else servers.firstOrNull { it.groupName == preferred }?.id ?: servers.firstOrNull()?.id.orEmpty()
        prefs.lastSelectedId = selected
        _ui.value = _ui.value.copy(selectedId = selected)
    }

    fun toggleGroup(name: String) {
        if (!expanded.remove(name)) expanded.add(name)
        refreshGroups(_ui.value.servers)
    }

    /** نگه‌داشتن پوشه و کشیدن آن، حتی پوشهٔ ذخیره‌شده‌ها را جابه‌جا می‌کند. */
    fun moveGroup(name: String, steps: Int) {
        val next = GroupOrder.move(groupOrder, name, steps)
        if (next == groupOrder) return
        groupOrder = next
        prefs.saveGroupOrder(next)
        refreshGroups(_ui.value.servers)
    }

    /** اولین ستاره، پوشهٔ مستقل ذخیره‌شده‌ها را می‌سازد. */
    fun toggleSaved(profile: Profile) {
        val old = _ui.value.servers
        val savedId = SavedProfiles.savedId(profile)
        val saved = old.any { SavedProfiles.isSaved(it) && it.id == savedId }
        val updated = if (saved) old.filterNot { SavedProfiles.isSaved(it) && it.id == savedId }
            else old + SavedProfiles.save(profile)
        if (updated.none { it.groupName == BrandConfig.GROUP_SAVED }) {
            expanded.remove(BrandConfig.GROUP_SAVED)
        }
        refreshGroups(updated)
        keepSelection(updated)
        _ui.value = _ui.value.copy(message = if (saved) "از ذخیره‌شده‌ها برداشته شد" else "در ذخیره‌شده‌ها موند ⭐")
        persist()
    }

    fun selectedProfile(): Profile? = _ui.value.selected

    fun select(profile: Profile) {
        prefs.lastSelectedId = profile.id
        _ui.value = _ui.value.copy(selectedId = profile.id)
    }

    // ------------------------------------------- انتشار امضاشدهٔ گیت‌هاب
    fun checkForUpdates() {
        if (updateJob?.isActive == true) return
        val previous = _update.value
        _update.value = previous.copy(phase = UpdatePhase.CHECKING, message = "")
        updateJob = viewModelScope.launch {
            try {
                val release = githubUpdates.latest()
                val app = getApplication<Application>()
                if (ReleaseCatalog.isNewer(release.version, BuildConfig.VERSION_NAME)) {
                    val ready = withContext(Dispatchers.IO) {
                        val apk = githubUpdates.downloadedFile(app, release)
                        if (apk.isFile && apk.length() == release.sizeBytes &&
                            runCatching { TrustedApk.verify(app, apk); true }.getOrDefault(false)
                        ) apk else { apk.delete(); null }
                    }
                    if (ready != null) {
                        _update.value = UpdateUiState(
                            phase = UpdatePhase.READY, release = release,
                            apkPath = ready.absolutePath, percent = 100,
                        )
                        UpdateAlerts.dismiss(app)
                    } else {
                        val percent = githubUpdates.partialPercent(app, release)
                        _update.value = UpdateUiState(
                            phase = if (percent > 0) UpdatePhase.PAUSED else UpdatePhase.AVAILABLE,
                            release = release, percent = percent,
                        )
                        UpdateAlerts.notifyIfNeeded(app, release)
                    }
                } else {
                    _update.value = UpdateUiState(phase = UpdatePhase.CURRENT)
                    UpdateAlerts.dismiss(app)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLog.e("بررسی انتشار گیت‌هاب ناموفق", e)
                _update.value = if (previous.hasPendingUpdate) previous.copy(
                    message = "بررسی تازه ناموفق بود؛ اطلاعات قبلی حفظ شد",
                ) else UpdateUiState(
                    phase = UpdatePhase.ERROR,
                    message = (e as? IOException)?.message ?: "بررسی نسخه ناموفق بود؛ اینترنت را بررسی کنید",
                )
            }
        }
    }

    fun downloadUpdate() {
        if (updateJob?.isActive == true) {
            // توقف فوری ممکن است هنوز در حال بستن جریان شبکه باشد؛ پس از آن ادامه بده.
            if (_update.value.phase == UpdatePhase.PAUSED) {
                val previous = updateJob
                viewModelScope.launch {
                    previous?.join()
                    if (_update.value.phase == UpdatePhase.PAUSED) downloadUpdate()
                }
            }
            return
        }
        val release = _update.value.release ?: return
        if (_update.value.phase !in setOf(UpdatePhase.AVAILABLE, UpdatePhase.PAUSED, UpdatePhase.ERROR)) return
        val app = getApplication<Application>()
        _update.value = UpdateUiState(
            phase = UpdatePhase.DOWNLOADING, release = release,
            percent = githubUpdates.partialPercent(app, release),
        )
        updateJob = viewModelScope.launch {
            try {
                val apk = githubUpdates.download(app, release) { percent ->
                    _update.update { state ->
                        if (state.phase == UpdatePhase.DOWNLOADING && percent != state.percent)
                            state.copy(percent = percent) else state
                    }
                }
                withContext(Dispatchers.IO) { TrustedApk.verify(app, apk) }
                _update.value = UpdateUiState(
                    phase = UpdatePhase.READY, release = release,
                    apkPath = apk.absolutePath, percent = 100,
                )
                UpdateAlerts.dismiss(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                githubUpdates.downloadedFile(app, release).delete()
                AppLog.e("دریافت یا اعتبارسنجی به‌روزرسانی ناموفق", e)
                _update.value = UpdateUiState(
                    phase = UpdatePhase.ERROR, release = release,
                    percent = githubUpdates.partialPercent(app, release),
                    message = (e as? IOException)?.message ?: "فایل به‌روزرسانی دریافت نشد؛ دوباره امتحان کن",
                )
            }
        }
    }

    fun pauseUpdateDownload() {
        if (_update.value.phase != UpdatePhase.DOWNLOADING) return
        _update.value = _update.value.copy(phase = UpdatePhase.PAUSED, message = "دریافت متوقف شد")
        updateJob?.cancel()
        githubUpdates.interruptDownload()
    }

    fun cancelUpdateDownload() {
        val release = _update.value.release ?: return
        if (_update.value.phase !in setOf(
                UpdatePhase.DOWNLOADING, UpdatePhase.PAUSED, UpdatePhase.ERROR, UpdatePhase.AVAILABLE,
            )) return
        _update.value = UpdateUiState(phase = UpdatePhase.AVAILABLE, release = release)
        updateJob?.cancel()
        githubUpdates.discard(getApplication(), release)
    }

    /** پیش از دادن فایل به نصب‌کننده، دوباره اصالت آن بررسی می‌شود. */
    fun readyUpdateFile(): File? = _update.value.takeIf { it.phase == UpdatePhase.READY }
        ?.apkPath?.takeIf { it.isNotBlank() }?.let(::File)

    // ------------------------------------------------------- مخزن گیت‌هاب

    /** فقط این عملِ صریح، ساب‌های پیش‌فرض حذف‌شده در تنظیمات را برمی‌گرداند. */
    fun restoreFromRepo() {
        viewModelScope.launch {
            // اگر رفرشِ خودکار/یک‌پوشه‌ای در جریان است، این درخواست دستی گم نشود.
            repoJob?.join()
            groupRefreshJob?.join()
            suppressedRepo.clear()
            prefs.saveSuppressedRepoLocations(suppressedRepo)
            refreshFromRepo()
        }
    }

    fun refreshFromRepo(silent: Boolean = false) {
        if (repoJob?.isActive == true || _ui.value.refreshingGroup.isNotBlank()) {
            if (!silent) _ui.value = _ui.value.copy(message = "یک به‌روزرسانی دیگر در جریان است")
            return
        }
        repoJob = viewModelScope.launch {
            if (!silent) {
                _ui.value = _ui.value.copy(busy = true, busyText = "خواندن مخزن…", progress = null)
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { repo.refresh(BrandConfig.DEFAULT_REPO) }
            }
            result.onSuccess { refreshed ->
                applyRepoRefresh(refreshed, silent)
                prefs.repoLoadedOnce = true
                prefs.sourcesMigrated = true
                refreshed.warnings.take(3).forEach { AppLog.d("هشدار: $it") }
            }.onFailure { t ->
                AppLog.e("خواندن مخزن ناموفق", t)
                if (!silent || _ui.value.servers.isEmpty()) {
                    _ui.value = _ui.value.copy(
                        busy = false, busyText = "",
                        message = if (silent) "مخزن خوانده نشد؛ از + سرور اضافه کن یا دوباره تلاش کن"
                            else t.message ?: "خواندن مخزن ناموفق بود؛ سرورهای قبلی حفظ شدند",
                    )
                }
            }
        }
    }

    /** رفرش سراسری: حذف انتخابی مخزن، علاقه‌مندی و اتصال فعال را حفظ می‌کند. */
    private fun applyRepoRefresh(refreshed: RepoClient.RefreshResult, silent: Boolean) {
        val current = _ui.value.servers
        val sources = sourcesByGroup.toMutableMap()
        val oldRepo = sources.values.filter { it.kind == GroupSource.Kind.REPO }
        val oldByUrl = oldRepo.associateBy { it.location }
        val visibleSubs = refreshed.subscriptions.filter {
            RepoVisibility.visible(it.link.url, suppressedRepo)
        }
        val direct = if (RepoVisibility.visible(RepoVisibility.DIRECT_FILE, suppressedRepo))
            refreshed.directProfiles else emptyList()
        val presentUrls = visibleSubs.map { it.link.url }.toMutableSet()
        if (direct.isNotEmpty()) presentUrls += RepoVisibility.DIRECT_FILE
        val removed = oldRepo.filter { it.location !in presentUrls }.map { it.group }.toMutableSet()
        removed.forEach { sources.remove(it) }

        val reserved = (occupiedGroups() - oldRepo.map { it.group }.toSet() - BrandConfig.GROUP_REPO_FILE)
            .toMutableSet()
        val assigned = mutableSetOf<String>()
        val replacement = direct.toMutableList()
        val replacedGroups = removed.apply { add(BrandConfig.GROUP_REPO_FILE) }
        if (direct.isNotEmpty()) {
            sources[BrandConfig.GROUP_REPO_FILE] = GroupSource(
                BrandConfig.GROUP_REPO_FILE, GroupSource.Kind.REPO, RepoVisibility.DIRECT_FILE,
            )
        }
        for (loaded in visibleSubs) {
            val old = oldByUrl[loaded.link.url]
            // پوشه‌های نسخه‌های قدیمی آدرس ساب نداشتند؛ نامشان را نگه دار.
            val legacyKey = GroupNaming.key(loaded.link.name)
            val legacy = legacyKey.takeIf { candidate ->
                !prefs.sourcesMigrated && candidate !in assigned && sources[candidate] == null &&
                    current.any { it.groupName == candidate } &&
                    candidate != BrandConfig.GROUP_MANUAL && !candidate.startsWith("چسبانده‌شده:")
            }?.let { rawGroup(it) }
            val name = old?.let { rawGroup(it.group) } ?: legacy ?: GroupNaming.unique(
                loaded.link.name, reserved + assigned,
            )
            val key = GroupNaming.key(name)
            assigned += key
            reserved += key
            if (loaded.profiles != null) {
                replacedGroups += key
                replacement += loaded.profiles.map { it.copy(group = name) }
            }
            // شکست دانلود بدون سرور قبلی پوشهٔ بی‌مصرف نمی‌سازد.
            if (loaded.profiles != null || current.any { it.groupName == key }) {
                sources[key] = GroupSource(key, GroupSource.Kind.REPO, loaded.link.url)
            }
        }
        val kept = current.filterNot { it.groupName in replacedGroups }
        val merged = keepActiveDuringRefresh(current, ServerStore.merge(kept, replacement))
        sourcesByGroup = sources.filterKeys { key -> merged.any { it.groupName == key } }
        refreshGroups(merged)
        keepSelection(merged)
        val failed = visibleSubs.count { it.profiles == null }
        val note = if (replacement.isEmpty()) "سرور تازه‌ای در مخزن پیدا نشد"
            else "${fa(replacement.size)} سرور از مخزن خوانده شد"
        _ui.value = _ui.value.copy(
            busy = false, busyText = "",
            message = if (silent && merged.isNotEmpty()) ""
                else if (failed > 0) "$note؛ ${fa(failed)} ساب خوانده نشد، قبلی‌ها حفظ شدند" else note,
        )
        persist()
    }

    private fun keepActiveDuringRefresh(old: List<Profile>, fresh: List<Profile>): List<Profile> {
        // اگر ساب حتی لینک انتخاب‌شده را عوض کند، رفرش نباید انتخاب یا اتصال
        // زنده را ناگهان به سرور دیگری ببرد. حذف دستی ساب از این مسیر نمی‌گذرد.
        val status = VpnBus.status.value
        val activeId = if (status.state in listOf(VpnState.CONNECTED, VpnState.CONNECTING))
            status.profileId else ""
        return GroupUpdates.retainForRefresh(old, fresh, _ui.value.selectedId, activeId)
    }

    /** فقط همین ساب/فایل را از منبع ذخیره‌شده می‌خواند؛ شکست یا فایل خالی پوشه را دست نمی‌زند. */
    fun refreshGroup(name: String, silent: Boolean = false) {
        val source = sourcesByGroup[name] ?: return
        val originalGroup = rawGroup(name)
        if (repoJob?.isActive == true) {
            _ui.value = _ui.value.copy(message = "اول خواندن مخزن تموم بشه، بعد این پوشه رو رفرش کن")
            return
        }
        if (_ui.value.refreshingGroup.isNotBlank() || _ui.value.busy) return
        groupRefreshJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(refreshingGroup = name)
            try {
                val fetched = withContext(Dispatchers.IO) {
                    runCatching {
                        when (source.kind) {
                            GroupSource.Kind.REPO, GroupSource.Kind.SUBSCRIPTION ->
                                repo.fetchSubscription(source.location, originalGroup).profiles
                            GroupSource.Kind.FILE -> {
                                val uri = Uri.parse(source.location)
                                val content = readFileText(uri)
                                when (val parsed = ConfigImporter.classify(content, originalGroup)) {
                                    is ConfigImporter.Result.Servers -> parsed.profiles
                                    is ConfigImporter.Result.SubscriptionText -> parsed.profiles
                                    is ConfigImporter.Result.Subscription ->
                                        repo.fetchSubscription(parsed.url, originalGroup).profiles
                                    is ConfigImporter.Result.Mixed -> parsed.profiles
                                    is ConfigImporter.Result.Subscriptions -> emptyList()
                                    is ConfigImporter.Result.Failure ->
                                        throw IllegalArgumentException(parsed.message)
                                }
                            }
                        }
                    }
                }
                fetched.onSuccess { profiles ->
                    if (profiles.isEmpty()) {
                        if (!silent) _ui.value = _ui.value.copy(message = "«$name» خالی بود؛ سرورهای قبلی حفظ شدند")
                    } else {
                        val old = _ui.value.servers
                        val merged = keepActiveDuringRefresh(old, GroupUpdates.replace(old, name, profiles))
                        refreshGroups(merged)
                        keepSelection(merged, name)
                        if (!silent) _ui.value = _ui.value.copy(
                            message = "پوشهٔ «$name» با ${fa(profiles.size)} سرور به‌روز شد",
                        )
                        persist()
                    }
                }.onFailure { t ->
                    AppLog.e("به‌روزرسانی «$name» ناموفق", t)
                    if (!silent) _ui.value = _ui.value.copy(
                        message = "«$name» خوانده نشد؛ سرورهای قبلی حفظ شدند",
                    )
                }
            } finally {
                _ui.value = _ui.value.copy(refreshingGroup = "")
            }
        }
    }

    // ------------------------------------------------------------- تست و اتصال

    /** تست دستی کل لیست: سرورها دقیقاً یکی‌یکی، با آدرس‌های ایرانی و پشتیبان. */
    fun testAll(onBest: (Profile?) -> Unit = {}) {
        val servers = _ui.value.servers
        if (servers.isEmpty() || testJob?.isActive == true) return
        testJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(
                busy = true, busyText = "آمادهٔ تست…", progress = 0f,
            )
            try {
                val tested = AutoSelector.testAll(
                    profiles = servers,
                    parallelism = 1,
                    options = options(includeTun = false),
                    fast = false,
                ) { p ->
                    _ui.update {
                        it.copy(
                            busyText = "تست ${fa(p.done)} از ${fa(p.total)}",
                            progress = p.done.toFloat() / p.total.toFloat(),
                        )
                    }
                }
                val sorted = AutoSelector.sortByQuality(tested)
                val best = AutoSelector.pickBest(sorted)
                refreshGroups(sorted)
                if (best != null) select(best)
                _ui.value = _ui.value.copy(
                    message = best?.let { "سریع‌ترین: ${it.displayName}" } ?: "هیچ سروری پاسخ نداد",
                )
                persist()
                onBest(best)
            } finally {
                _ui.value = _ui.value.copy(busy = false, busyText = "", progress = null)
            }
        }
    }

    /**
     * اتصال هوشمند: تمام پوشه‌ها در نوبت مسابقه‌اند، فقط پاسخ موفقِ تازه انتخاب
     * می‌شود. سابقهٔ تست فقط ترتیب آزمون را تغییر می‌دهد، نه اجازهٔ اتصال را.
     */
    fun smartConnect(groupName: String? = null, onBest: (Profile?) -> Unit) {
        val servers = if (groupName == null) _ui.value.servers
            else _ui.value.groups.firstOrNull { it.name == groupName }?.profiles.orEmpty()
        if (servers.isEmpty()) {
            onBest(null)
            return
        }
        if (_ui.value.testing || testJob?.isActive == true || groupTestJob?.isActive == true) return

        testJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(
                testing = true, testingDone = 0, testingTotal = servers.size,
            )
            try {
                val raced = AutoSelector.raceFirstResponding(
                    candidates = servers,
                    options = options(includeTun = false),
                    onProgress = { done ->
                        _ui.update { if (it.testing) it.copy(testingDone = done) else it }
                    },
                )
                val best = raced?.profile
                if (best != null) {
                    val updated = _ui.value.servers.map { if (it.id == best.id) best else it }
                    prefs.lastSelectedId = best.id
                    _ui.value = _ui.value.copy(selectedId = best.id, testing = false)
                    // نام، نتیجهٔ تازه و سرور انتخابی را پیش از فراخوانی اتصال منتشر کن.
                    refreshGroups(updated)
                    _ui.value = _ui.value.copy(message = "به ${_ui.value.selected?.displayName ?: best.displayName} وصل می‌شویم")
                    persist()
                    onBest(_ui.value.selected ?: best)
                } else {
                    _ui.value = _ui.value.copy(testing = false, message = "")
                    onBest(null)
                }
            } finally {
                _ui.update { if (it.testing) it.copy(testing = false) else it }
            }
        }
    }

    /** لغو مسابقه از پاپ‌آپ؛ تماس‌های بومی دیررس برای اتصال استفاده نمی‌شوند. */
    fun cancelTest() {
        testJob?.cancel()
        testJob = null
        _ui.value = _ui.value.copy(
            testing = false, busy = false, busyText = "", progress = null,
            message = "تست لغو شد",
        )
    }

    /** دکمهٔ تست هر پوشه: چهار آزمون محدود موازی؛ لمس دوباره = لغو. */
    fun testGroup(name: String) {
        if (_ui.value.testingGroup == name) {
            groupTestJob?.cancel()
            groupTestJob = null
            _ui.value = _ui.value.copy(
                testingGroup = "", testingGroupStartedAt = 0L,
                testingGroupDone = 0, testingGroupTotal = 0, message = "تست لغو شد",
            )
            refreshGroups(_ui.value.servers)
            persist() // پاسخ‌های تازهٔ همین تست پس از لغو هم باقی می‌مانند.
            return
        }
        if (groupTestJob?.isActive == true || testJob?.isActive == true) return
        val group = _ui.value.groups.firstOrNull { it.name == name } ?: return
        if (group.profiles.isEmpty()) return
        groupTestJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(
                testingGroup = name, testingGroupStartedAt = System.currentTimeMillis(),
                testingGroupDone = 0, testingGroupTotal = group.profiles.size,
            )
            refreshGroups(_ui.value.servers)
            try {
                val tested = AutoSelector.testGroupResponsive(
                    profiles = group.profiles,
                    options = options(includeTun = false),
                ) { progress ->
                    // نتیجهٔ هر سرور به‌محض پایان در فهرست ظاهر و بر اساس پینگ مرتب می‌شود؛
                    // کاربر لازم نیست تا پایان همهٔ سرورها منتظر بماند.
                    _ui.update { state ->
                        if (state.testingGroup != name) state else {
                            val updated = state.servers.map { current ->
                                if (current.id == progress.latest.id && current.groupName == name)
                                    progress.latest else current
                            }
                            val labeled = RepoServerLabels.apply(
                                updated, sourcesByGroup, name, state.testingGroupStartedAt,
                            )
                            state.copy(
                                servers = labeled,
                                groups = buildGroups(labeled, name, state.testingGroupStartedAt),
                                testingGroupDone = progress.done,
                            )
                        }
                    }
                }
                val byId = tested.associateBy { it.id }
                val servers = _ui.value.servers.map { current ->
                    if (current.groupName == name) byId[current.id] ?: current else current
                }
                val best = AutoSelector.pickBest(tested)
                refreshGroups(servers)
                val visibleBest = best?.let { chosen ->
                    _ui.value.servers.firstOrNull { it.id == chosen.id } ?: chosen
                }
                _ui.value = _ui.value.copy(
                    message = if (best != null && visibleBest != null) {
                        "بهترین پینگ «$name»: ${visibleBest.displayName} — ${fa(best.latencyMs)}"
                    } else "سرورهای «$name» توی مهلت تست پاسخی ندادند", 
                )
                persist()
            } finally {
                _ui.update {
                    if (it.testingGroup == name) it.copy(
                        testingGroup = "", testingGroupStartedAt = 0L,
                        testingGroupDone = 0, testingGroupTotal = 0,
                    ) else it
                }
                refreshGroups(_ui.value.servers)
            }
        }
    }

    fun disconnect() = VpnBus.reset()

    // ------------------------------------------------- افزودن از کلیپ‌بورد/فایل

    private data class IncomingFile(val uri: Uri, val group: String, val canRefresh: Boolean, val existing: Boolean)

    /** لینک یا متن چسبانده‌شده؛ چند کانفیگ در پوشه‌ای با نمونه‌هایی از نامشان قرار می‌گیرند. */
    fun importText(text: String) {
        viewModelScope.launch { importContent(text, null) }
    }

    /** فایل متنی: نام خود فایل پوشه می‌شود و URI با مجوز ماندگار، منبع رفرش آن است. */
    fun importUri(uri: Uri, canRefresh: Boolean) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, busyText = "خواندن فایل…", progress = null)
            val read = withContext(Dispatchers.IO) {
                runCatching { readFileText(uri) to fileDisplayName(uri) }
            }
            read.onFailure { t ->
                _ui.value = _ui.value.copy(
                    busy = false, busyText = "", message = t.message ?: "فایل خوانده نشد",
                )
            }.onSuccess { (content, name) ->
                val old = sourcesByGroup.values.firstOrNull {
                    it.kind == GroupSource.Kind.FILE && it.location == uri.toString()
                }
                val group = chooseGroup(GroupNaming.fromFile(name), old)
                importContent(content, IncomingFile(uri, group, canRefresh, old != null))
            }
        }
    }

    private fun readFileText(uri: Uri): String {
        val stream = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("فایل خوانده نشد")
        return stream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (out.size() + count > 4_000_000) {
                    throw IllegalArgumentException("فایل بزرگ‌تر از ۴ مگابایت است")
                }
                out.write(buffer, 0, count)
            }
            out.toString(Charsets.UTF_8.name())
        }
    }

    private fun fileDisplayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty()
    }

    private suspend fun importContent(content: String, file: IncomingFile?) {
        _ui.value = _ui.value.copy(busy = true, busyText = "تشخیص نوع متن…", progress = null)
        val result = withContext(Dispatchers.IO) {
            runCatching { ConfigImporter.classify(content, file?.group ?: BrandConfig.GROUP_MANUAL) }
        }
        result.onFailure { t ->
            _ui.value = _ui.value.copy(
                busy = false, busyText = "", message = t.message ?: "افزودن ناموفق بود",
            )
        }.onSuccess { imported ->
            when (imported) {
                is ConfigImporter.Result.Servers ->
                    addImportedProfiles(imported.profiles, imported.note, file)
                is ConfigImporter.Result.SubscriptionText ->
                    addImportedProfiles(imported.profiles, "سرورها اضافه شدند", file)
                is ConfigImporter.Result.Subscription -> {
                    val old = if (file == null) sourcesByGroup.values.firstOrNull {
                        it.kind == GroupSource.Kind.SUBSCRIPTION && it.location == imported.url
                    } else null
                    // نسخه‌های قدیمی URL ساب دستی را نگه نمی‌داشتند؛ واردکردن دوبارهٔ
                    // همان نام، پوشهٔ قدیمی را به منبع تازه وصل می‌کند نه یک پوشهٔ تکراری.
                    val legacy = if (file == null && old == null) legacyManualSub(imported.name)
                        else null
                    val name = file?.group ?: old?.let { rawGroup(it.group) } ?: legacy
                        ?: GroupNaming.unique(imported.name, occupiedGroups())
                    downloadSubscription(imported.url, name, old != null || legacy != null)
                }
                is ConfigImporter.Result.Subscriptions -> downloadSubscriptions(imported.links)
                is ConfigImporter.Result.Mixed -> {
                    addImportedProfiles(imported.profiles, "سرورها اضافه شدند", file)
                    downloadSubscriptions(imported.links)
                }
                is ConfigImporter.Result.Failure ->
                    _ui.value = _ui.value.copy(busy = false, busyText = "", message = imported.message)
            }
        }
    }

    private fun addImportedProfiles(profiles: List<Profile>, note: String, file: IncomingFile?) {
        if (profiles.isEmpty()) return
        val name = file?.group ?: if (profiles.size > 1) {
            GroupNaming.unique(GroupNaming.fromPaste(profiles), occupiedGroups())
        } else BrandConfig.GROUP_MANUAL
        val source = if (file?.canRefresh == true) {
            GroupSource(GroupNaming.key(name), GroupSource.Kind.FILE, file.uri.toString())
        } else null
        val message = if (file != null || profiles.size > 1) {
            "پوشهٔ «$name» با ${fa(profiles.size)} سرور اضافه شد"
        } else note
        addProfiles(profiles.map { it.copy(group = name) }, message, source, file?.existing == true)
    }

    private suspend fun downloadSubscription(url: String, name: String, replace: Boolean) {
        _ui.value = _ui.value.copy(busyText = "دانلود ساب «$name»…")
        val fetched = withContext(Dispatchers.IO) {
            runCatching { repo.fetchSubscription(url, name) }
        }
        fetched.onFailure { t ->
            _ui.value = _ui.value.copy(
                busy = false, busyText = "", message = t.message ?: "ساب خوانده نشد",
            )
        }.onSuccess { parsed ->
            if (parsed.profiles.isEmpty()) {
                _ui.value = _ui.value.copy(
                    busy = false, busyText = "", message = "ساب خالی بود؛ سرورهای قبلی حفظ شدند",
                )
            } else {
                val source = GroupSource(GroupNaming.key(name), GroupSource.Kind.SUBSCRIPTION, url)
                addProfiles(
                    parsed.profiles.map { it.copy(group = name) },
                    "پوشهٔ «$name» با ${fa(parsed.profiles.size)} سرور اضافه شد",
                    source, replace,
                )
            }
        }
    }

    private suspend fun downloadSubscriptions(links: List<SubscriptionParser.SubLink>) {
        var succeeded = 0
        var failed = 0
        for ((index, link) in links.withIndex()) {
            val old = sourcesByGroup.values.firstOrNull {
                it.kind == GroupSource.Kind.SUBSCRIPTION && it.location == link.url
            }
            val legacy = if (old == null) legacyManualSub(link.name) else null
            val name = old?.let { rawGroup(it.group) } ?: legacy
                ?: GroupNaming.unique(link.name, occupiedGroups())
            _ui.value = _ui.value.copy(
                busy = true, busyText = "دانلود ساب ${fa(index + 1)} از ${fa(links.size)}…",
                progress = index.toFloat() / links.size,
            )
            val fetched = withContext(Dispatchers.IO) {
                runCatching { repo.fetchSubscription(link.url, name) }
            }
            val profiles = fetched.getOrNull()?.profiles.orEmpty()
            if (profiles.isNotEmpty()) {
                addProfiles(
                    profiles.map { it.copy(group = name) }, "",
                    GroupSource(GroupNaming.key(name), GroupSource.Kind.SUBSCRIPTION, link.url),
                    replace = old != null || legacy != null, showMessage = false,
                )
                succeeded++
            } else {
                failed++
                fetched.exceptionOrNull()?.let { AppLog.e("ساب «$name» خوانده نشد", it) }
            }
        }
        _ui.value = _ui.value.copy(
            busy = false, busyText = "", progress = null,
            message = "${fa(succeeded)} ساب اضافه شد" +
                if (failed > 0) "؛ ${fa(failed)} ساب خوانده نشد، قبلی‌ها حفظ شدند" else "",
        )
    }

    private fun addProfiles(
        newOnes: List<Profile>, note: String,
        source: GroupSource? = null, replace: Boolean = false,
        showMessage: Boolean = true,
    ) {
        if (newOnes.isEmpty()) return
        val servers = if (replace) {
            GroupUpdates.replace(_ui.value.servers, newOnes.first().groupName, newOnes)
        } else ServerStore.merge(_ui.value.servers, newOnes)
        val added = servers.size - _ui.value.servers.size
        if (source != null && servers.any { it.groupName == source.group }) {
            sourcesByGroup = sourcesByGroup + (source.group to source)
        }
        refreshGroups(servers)
        keepSelection(servers, source?.group.orEmpty())
        _ui.value = _ui.value.copy(
            busy = false, busyText = "", progress = null,
            message = if (!showMessage) ""
                else if (!replace && added == 0) "این سرورها قبلاً اضافه شده‌اند" else note,
        )
        persist()
    }

    /** افزودن دستی با لینک (از کادر متن) */
    fun addLink(link: String) {
        viewModelScope.launch {
            val profile = withContext(Dispatchers.IO) {
                runCatching { ShareLinkParser.parseOrThrow(link.trim()).copy(group = BrandConfig.GROUP_MANUAL) }.getOrNull()
            }
            if (profile == null) {
                _ui.value = _ui.value.copy(message = "این لینک خوانده نشد")
            } else {
                addProfiles(listOf(profile), "سرور اضافه شد")
            }
        }
    }

    fun remove(profile: Profile) {
        if (SavedProfiles.isSaved(profile)) {
            toggleSaved(profile)
            return
        }
        val servers = _ui.value.servers.filterNot { it.id == profile.id }
        if (servers.none { it.groupName == profile.groupName }) {
            val source = sourcesByGroup[profile.groupName]
            if (source?.kind == GroupSource.Kind.REPO) {
                suppressedRepo.add(source.location)
                prefs.saveSuppressedRepoLocations(suppressedRepo)
            }
            sourcesByGroup = sourcesByGroup - profile.groupName
        }
        refreshGroups(servers)
        keepSelection(servers)
        _ui.value = _ui.value.copy(message = "سرور حذف شد")
        persist()
    }

    /** دکمهٔ حذف داخل پوشه‌های پیش‌فرض/ذخیره‌شده‌ها عمداً وجود ندارد. */
    fun removeGroup(name: String) {
        if (name == BrandConfig.GROUP_SAVED || sourcesByGroup[name]?.kind == GroupSource.Kind.REPO) return
        deleteGroup(name)
    }

    /** حذف از تنظیمات شامل ساب‌های پیش‌فرض هم می‌شود؛ برگشت فقط با بازخوانی صریح است. */
    fun deleteSubscription(name: String) {
        val source = sourcesByGroup[name] ?: return
        if (source.kind == GroupSource.Kind.REPO) {
            suppressedRepo.add(source.location)
            prefs.saveSuppressedRepoLocations(suppressedRepo)
        }
        deleteGroup(name)
    }

    private fun deleteGroup(name: String) {
        val servers = _ui.value.servers.filterNot { it.groupName == name }
        sourcesByGroup = sourcesByGroup - name
        expanded.remove(name)
        groupOrder = groupOrder.filterNot { it == name }
        prefs.saveGroupOrder(groupOrder)
        refreshGroups(servers)
        keepSelection(servers)
        _ui.value = _ui.value.copy(message = "پوشهٔ «$name» حذف شد")
        persist()
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = "")
    }

    // ------------------------------------------------------------- کمکی‌ها

    /** کانفیگ کامل Xray برای یک سرور (برای دادن به سرویس VPN) */
    fun configFor(profile: Profile): String =
        XrayConfigBuilder.buildJson(profile, options(includeTun = true))

    fun options(includeTun: Boolean) = XrayConfigBuilder.Options(
        mtu = 1500,
        tunName = "xray0",
        socksPort = 10808,
        dns = listOf("1.1.1.1", "8.8.8.8"),
        logLevel = "warning",
        includeTunInbound = includeTun,
        includeSocksInbound = true,
    )

    fun donationUrl(): String = BrandConfig.DONATION_URL

    fun openUrl(url: String) {
        if (url.isBlank()) return
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
        }.onFailure { AppLog.e("باز کردن لینک ناموفق", it) }
    }

    /** آیا الان VPN وصل است؟ (برای دکمهٔ اصلی) */
    fun isConnected(): Boolean = VpnBus.status.value.state == VpnState.CONNECTED

    private fun fa(value: Number): String = nzvpn.nzbozorg.ir.util.PersianText.digits(value)

}
