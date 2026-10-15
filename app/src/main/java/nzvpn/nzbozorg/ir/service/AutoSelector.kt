package nzvpn.nzbozorg.ir.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import nzvpn.nzbozorg.ir.core.XrayConfigBuilder
import nzvpn.nzbozorg.ir.core.XrayCore
import nzvpn.nzbozorg.ir.model.Profile
import nzvpn.nzbozorg.ir.net.PingTargets
import nzvpn.nzbozorg.ir.util.AppLog
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** تست واقعیِ پروکسی با هسته؛ عدد تأخیر معادل سرعت دانلود نیست. */
object AutoSelector {

    data class Progress(val done: Int, val total: Int, val latest: Profile)
    data class QuickResult(val profile: Profile, val url: String)

    /** حتی هنگام لغو و شروع دوباره، حداکثر شش نمونهٔ سنگین هسته هم‌زمان اجرا می‌شوند. */
    const val MAX_CONCURRENT_PROBES = 6
    private val coreSlots = Semaphore(MAX_CONCURRENT_PROBES, true)

    fun measure(
        profile: Profile,
        options: XrayConfigBuilder.Options,
        fast: Boolean = false,
    ): Pair<Long, String> {
        val config = XrayConfigBuilder.buildJson(profile, options)
        for (url in if (fast) PingTargets.fastAttempts() else PingTargets.attempts()) {
            val latency = XrayCore.measureOutboundDelay(config, url)
            if (latency > 0) return latency to url
        }
        return -1L to ""
    }

    /**
     * تست سنتیِ یک‌به‌یک: فراخواننده parallelism=1 می‌دهد. تماس JNI لغوپذیر
     * نیست؛ با timeout اختیاری، نتیجهٔ دیررس دیگر وارد UI یا فایل نمی‌شود.
     */
    suspend fun testAll(
        profiles: List<Profile>,
        parallelism: Int,
        options: XrayConfigBuilder.Options,
        fast: Boolean = false,
        deadlineMs: Long = 0L,
        probe: (Profile, XrayConfigBuilder.Options, Boolean) -> Pair<Long, String> = { p, o, f ->
            measure(p, o, f)
        },
        onProgress: (Progress) -> Unit = {},
    ): List<Profile> {
        if (profiles.isEmpty()) return emptyList()

        val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val nextIndex = AtomicInteger(0)
        val done = AtomicInteger(0)
        val accepting = AtomicBoolean(true)
        val finished = Collections.synchronizedMap(mutableMapOf<String, Profile>())

        val workers = List(parallelism.coerceIn(1, 12).coerceAtMost(profiles.size)) {
            workerScope.launch {
                while (accepting.get()) {
                    val index = nextIndex.getAndIncrement()
                    if (index >= profiles.size) break
                    val profile = profiles[index]
                    val (latency, url) = runCatching {
                        coreSlots.acquire()
                        try { probe(profile, options, fast) } finally { coreSlots.release() }
                    }.onFailure { AppLog.e("تست ${profile.displayName} ناموفق", it) }
                        .getOrDefault(-1L to "")
                    if (!accepting.get()) break
                    val result = if (latency > 0) profile.withTestResult(latency)
                    else profile.withTestResult(-1, "بدون پاسخ")
                    if (url.isNotBlank()) {
                        AppLog.d("تست ${profile.displayName}: ${latency}ms (${PingTargets.label(url)})")
                    }
                    finished[result.id] = result
                    onProgress(Progress(done.incrementAndGet(), profiles.size, result))
                }
            }
        }

        try {
            if (deadlineMs > 0) {
                withTimeoutOrNull(deadlineMs) { workers.joinAll() }
            } else {
                workers.joinAll()
            }
        } finally {
            accepting.set(false)
            workerScope.cancel()
        }
        val snapshot = synchronized(finished) { finished.toMap() }
        return profiles.map { snapshot[it.id] ?: it }
    }

    /**
     * برای دکمهٔ تست پوشه: به هر سرور حداکثر یک ثانیه فرصتِ نتیجه در رابط می‌دهیم
     * و بعد سراغ بعدی می‌رویم؛ هر نتیجه بلافاصله منتشر می‌شود.
     * فراخوانی بومی Xray در حال اجرا را نمی‌توان به زور متوقف کرد. پس از مهلت،
     * پاسخ دیررس نادیده گرفته می‌شود و semaphore سراسری جلوی انباشت هسته را می‌گیرد.
     * اگر همهٔ جایگاه‌ها مشغول باشند، همان بودجهٔ یک‌ثانیه‌ای شامل انتظار نوبت است.
     */
    suspend fun testGroupWithinOneSecond(
        profiles: List<Profile>,
        options: XrayConfigBuilder.Options,
        budgetMs: Long = 1_000L,
        probe: (Profile, XrayConfigBuilder.Options) -> Pair<Long, String> = { p, o ->
            // اول مقصد جهانی با پاسخِ سبک ۲۰۴؛ اگر زود خطا داد مقصد ایرانی هم فرصت دارد.
            val json = XrayConfigBuilder.buildJson(p, o)
            var result: Pair<Long, String> = -1L to ""
            for (url in PingTargets.quickGroupAttempts()) {
                val latency = XrayCore.measureOutboundDelay(json, url)
                if (latency > 0) {
                    result = latency to url
                    break
                }
            }
            result
        },
        onProgress: (Progress) -> Unit = {},
    ): List<Profile> {
        require(budgetMs in 1L..1_000L) { "سقف تست هر سرور یک ثانیه است" }
        if (profiles.isEmpty()) return emptyList()
        val workers = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val results = ArrayList<Profile>(profiles.size)
        try {
            for (profile in profiles) {
                currentCoroutineContext().ensureActive()
                val started = AtomicBoolean(false)
                val task = workers.async {
                    coreSlots.acquire()
                    try {
                        // اگر نوبت بعد از پایانِ بودجه رسید، دیگر نمونهٔ جدید نساز.
                        currentCoroutineContext().ensureActive()
                        started.set(true)
                        probe(profile, options)
                    } finally { coreSlots.release() }
                }
                // async در محدودهٔ مستقل است: توقف انتظار، منتظر آزادشدن JNI نمی‌ماند.
                val measured = withTimeoutOrNull(budgetMs) {
                    try {
                        task.await()
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        AppLog.e("تست سریع ${profile.displayName} ناموفق", t)
                        -1L to ""
                    }
                }
                if (measured == null) task.cancel()
                val result = if (measured != null && measured.first > 0) {
                    profile.withTestResult(measured.first)
                } else {
                    val reason = when {
                        measured != null -> "بدون پاسخ"
                        !started.get() -> "هسته درگیر بود؛ تست اجرا نشد"
                        else -> "مهلت یک‌ثانیه‌ای تمام شد"
                    }
                    profile.withTestResult(-1L, reason)
                }
                results += result
                onProgress(Progress(results.size, profiles.size, result))
            }
        } finally {
            // تماس‌های بومیِ دیررس ممکن است هنوز مشغول باشند؛ نتیجه‌شان منتشر نمی‌شود.
            workers.cancel()
        }
        return results
    }

    /**
     * ترتیب آزمون: سرورها به‌نوبت از همهٔ پوشه‌ها، بدون پیش‌داوری از کشِ پینگ.
     * هیچ سروری از مسابقه کنار گذاشته نمی‌شود.
     */
    fun orderForRace(profiles: List<Profile>): List<Profile> {
        val groups = profiles.groupBy { it.groupName }.values
        val interleaved = ArrayList<Profile>(profiles.size)
        val turns = java.util.ArrayDeque(groups.map { it.iterator() })
        while (turns.isNotEmpty()) {
            val iterator = turns.removeFirst()
            interleaved += iterator.next()
            if (iterator.hasNext()) turns.addLast(iterator)
        }
        return interleaved.distinctBy { it.id }
    }

    private sealed class RaceEvent {
        data class Measured(val profile: Profile, val latency: Long, val url: String) : RaceEvent()
        data object WorkerDone : RaceEvent()
    }

    /**
     * تمام سرورها در صفِ بررسی‌اند؛ حداکثر شش تماس بومی هم‌زمان. به محض نخستین
     * پاسخ موفقِ تازه، انتخاب برمی‌گردد (بدون تکیه بر نتیجهٔ کش‌شده/سرور تست‌نشده).
     * اگر هیچ‌کدام جواب ندهد، تا بررسی کل فهرست یا لغو کاربر ادامه می‌دهیم.
     * deadlineMs فقط برای آزمون/محدودیت اختیاری است؛ پیش‌فرض هیچ سقف کلی ندارد.
     * لغو JNI را متوقف نمی‌کند، ولی semaphore جلوی هجوم نمونه‌های جدید را می‌گیرد.
     */
    suspend fun raceFirstResponding(
        candidates: List<Profile>,
        options: XrayConfigBuilder.Options,
        parallelism: Int = MAX_CONCURRENT_PROBES,
        deadlineMs: Long = 0L,
        probe: (Profile, XrayConfigBuilder.Options) -> Pair<Long, String> = { p, o ->
            measure(p, o, fast = true)
        },
        onProgress: (Int) -> Unit = {},
    ): QuickResult? = withContext(Dispatchers.IO) {
        val ordered = orderForRace(candidates)
        if (ordered.isEmpty()) return@withContext null
        val count = parallelism.coerceIn(1, MAX_CONCURRENT_PROBES).coerceAtMost(ordered.size)
        val queue = LinkedBlockingQueue<RaceEvent>()
        val next = AtomicInteger(0)
        val stopped = AtomicBoolean(false)
        val found = AtomicBoolean(false)
        val pool = Executors.newFixedThreadPool(count) { task ->
            Thread(task, "nzvpn-live-probe").apply { isDaemon = true }
        }
        // تمام پوشه‌ها در یک مسابقه‌اند؛ موج اول هم‌زمان راه می‌افتد.
        // فراخوانی بومی عمداً به اندازهٔ همهٔ سرورها نامحدود نمی‌شود (خطر OOM).
        val startGate = CountDownLatch(1)
        val workers = List(count) {
            pool.submit {
                try {
                    startGate.await()
                    while (!stopped.get() && !found.get()) {
                        val index = next.getAndIncrement()
                        if (index >= ordered.size) break
                        val profile = ordered[index]
                        val measured = try {
                            coreSlots.acquire()
                            try {
                                if (stopped.get() || found.get()) break
                                runCatching { probe(profile, options) }
                                    .onFailure { AppLog.e("تست ${profile.displayName} ناموفق", it) }
                                    .getOrDefault(-1L to "")
                            } finally {
                                coreSlots.release()
                            }
                        } catch (_: InterruptedException) {
                            break
                        }
                        if (stopped.get()) break
                        val (latency, url) = measured
                        if (latency > 0) {
                            if (found.compareAndSet(false, true)) {
                                queue.offer(RaceEvent.Measured(profile, latency, url))
                            }
                            break
                        }
                        if (!found.get()) queue.offer(RaceEvent.Measured(profile, -1L, ""))
                    }
                } catch (_: InterruptedException) {
                    // لغو مسابقه پیش از شروع موج اول.
                } finally {
                    queue.offer(RaceEvent.WorkerDone)
                }
            }
        }
        startGate.countDown()
        val deadline = if (deadlineMs > 0) {
            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(deadlineMs)
        } else Long.MAX_VALUE
        var finishedWorkers = 0
        var done = 0
        try {
            while (finishedWorkers < count) {
                currentCoroutineContext().ensureActive()
                val left = if (deadlineMs > 0) {
                    TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                } else 150L
                if (deadlineMs > 0 && left <= 0) break
                val event = queue.poll(minOf(150L, left), TimeUnit.MILLISECONDS) ?: continue
                when (event) {
                    RaceEvent.WorkerDone -> finishedWorkers++
                    is RaceEvent.Measured -> {
                        done++
                        onProgress(done)
                        if (event.latency > 0) {
                            AppLog.d("پاسخ تازه از ${event.profile.displayName}: ${event.latency}ms (${PingTargets.label(event.url)})")
                            return@withContext QuickResult(
                                event.profile.withTestResult(event.latency), event.url,
                            )
                        }
                    }
                }
            }
            null
        } finally {
            stopped.set(true)
            workers.forEach { it.cancel(true) }
            pool.shutdownNow()
        }
    }

    fun pickBest(profiles: List<Profile>): Profile? =
        profiles.filter { it.latencyMs > 0 }.minByOrNull { it.latencyMs }

    /** هنگام تست، پاسخ‌های تازه بالاتر از نتیجه‌های ذخیره‌شدهٔ هنوز بررسی‌نشده‌اند. */
    fun sortByLiveQuality(profiles: List<Profile>, startedAt: Long): List<Profile> {
        if (startedAt <= 0) return sortByQuality(profiles)
        val live = profiles.filter { it.lastTestedAt >= startedAt && it.latencyMs > 0 }
            .sortedBy { it.latencyMs }
        val pending = sortByQuality(profiles.filter { it.lastTestedAt < startedAt })
        val failed = profiles.filter { it.lastTestedAt >= startedAt && it.latencyMs <= 0 }
        return live + pending + failed
    }

    fun sortByQuality(profiles: List<Profile>): List<Profile> =
        profiles.sortedWith(
            compareBy(
                { if (it.latencyMs > 0) 0 else 1 },
                { if (it.latencyMs > 0) it.latencyMs else Long.MAX_VALUE },
            ),
        )
}
