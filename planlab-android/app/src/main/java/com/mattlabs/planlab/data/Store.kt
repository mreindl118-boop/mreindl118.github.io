package com.mattlabs.planlab.data

import android.content.Context
import android.content.SharedPreferences
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mattlabs.planlab.core.Box
import com.mattlabs.planlab.core.Coach
import com.mattlabs.planlab.core.Day
import com.mattlabs.planlab.core.HealthData
import com.mattlabs.planlab.core.LogDoc
import com.mattlabs.planlab.core.Md
import com.mattlabs.planlab.core.Plan
import com.mattlabs.planlab.health.HealthReader
import com.mattlabs.planlab.notify.Notifier
import com.mattlabs.planlab.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

data class UiState(
    val hasKey: Boolean = false,
    val plan: Plan = Plan.EMPTY,
    val log: JSONObject = LogDoc.empty(),
    val health: HealthData = HealthData.EMPTY,
    val loading: Boolean = false,
    val error: String = "",
    val synced: Boolean = false,
    val pending: Int = 0,
    val version: Long = 0,
)

/** One place for the plan, Matt's taps and his health numbers. Screens, widgets and notifications all read it. */
object Store {
    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncLock = Mutex()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> get() = _state
    private var files: Map<String, String> = emptyMap()

    val MAIN = listOf("meals", "body", "piano", "voice")
    val LABEL = mapOf("meals" to "Eat", "body" to "Body", "piano" to "Piano", "voice" to "Voice")

    @Synchronized
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        prefs = app.getSharedPreferences("planlab", Context.MODE_PRIVATE)
        files = runCatching {
            val o = JSONObject(File(app.filesDir, "plan_files.json").readText())
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
        val health = HealthData.fromJson(runCatching { File(app.filesDir, "health.json").readText() }.getOrNull())
        val plan = Md.plan(files, LocalDate.now(), prefs.getLong("built", 0))
        _state.value = UiState(hasKey = token() != null, plan = plan, log = view(), health = health, synced = prefs.getBoolean("synced", false), pending = ops().length())
    }

    fun token(): String? = prefs.getString("key", null)?.takeIf { it.isNotBlank() }
    fun prefs(): SharedPreferences = prefs

    fun clearKey() {
        prefs.edit().remove("key").apply()
        _state.value = _state.value.copy(hasKey = false)
    }

    fun setKey(k: String) {
        prefs.edit().putString("key", k.trim()).apply()
        _state.value = _state.value.copy(hasKey = true, error = "")
        refreshAsync()
    }

    // ---------- the log: base (last pulled) + ops (not pushed yet)
    private fun base(): JSONObject = LogDoc.parse(prefs.getString("log_base", null))
    private fun ops(): JSONArray = runCatching { JSONArray(prefs.getString("log_ops", "[]")) }.getOrDefault(JSONArray())
    private fun view(): JSONObject = LogDoc.apply(base(), ops(), prefs.getLong("log_updated", 0))

    @Synchronized
    private fun addOps(vararg op: JSONObject) {
        val a = ops(); op.forEach { a.put(it) }
        prefs.edit().putString("log_ops", a.toString()).putLong("log_updated", System.currentTimeMillis()).apply()
        publish()
        schedulePush()
    }

    fun tick(list: String, key: String, value: Boolean) = addOps(LogDoc.tickOp(list, key, value))
    fun addMinutes(date: LocalDate, key: String, n: Int) = addOps(LogDoc.addOp("mins", date.toString(), key, n))
    fun pelvicSet(date: LocalDate) = addOps(LogDoc.addOp("ticks", "pf-$date", "n", 1))
    fun skip(date: LocalDate, key: String) = addOps(LogDoc.tickOp("skip-$date", key, true))
    fun addCount(date: LocalDate, name: String) = addOps(LogDoc.addOp("ticks", "$name-$date", "n", 1))

    /** Marks a day's box done, logging the planned minutes for piano and voice if none were logged. */
    fun done(date: LocalDate, key: String, minutes: Int? = null) {
        val ops = mutableListOf(LogDoc.tickOp(date.toString(), key, true))
        val d = _state.value.plan.day(date)
        if (key == "piano" || key == "voice") {
            val m = minutes ?: d?.plannedMinutes(key) ?: 0
            if (m > 0 && LogDoc.minutes(_state.value.log, date.toString(), key) == 0) ops += LogDoc.addOp("mins", date.toString(), key, m)
        }
        addOps(*ops.toTypedArray())
    }

    /** Ticked directly, or (when it has steps and was never ticked directly) all its steps ticked. */
    fun isDone(day: Day, box: Box): Boolean {
        LogDoc.tick(_state.value.log, day.date.toString(), box.key)?.let { return it }
        if (box.steps.isNotEmpty() && box.steps.all { stepDone(day, it) }) return true
        return box.done
    }
    fun stepDone(day: Day, step: Box): Boolean = LogDoc.ticked(_state.value.log, day.date.toString(), step.key, step.done)
    fun nextStep(day: Day, box: Box): Box? = box.steps.firstOrNull { !stepDone(day, it) }
    fun stepsDone(day: Day, box: Box): Int = box.steps.count { stepDone(day, it) }
    fun toggle(day: Day, box: Box) = if (isDone(day, box)) tick(day.date.toString(), box.key, false) else done(day.date, box.key)

    /** Ticks one step; ticking the last open step ticks its box too, and unticking a step unticks the box. */
    fun setStep(day: Day, box: Box, step: Box, value: Boolean) {
        val d = day.date.toString()
        val ops = mutableListOf(LogDoc.tickOp(d, step.key, value))
        if (value && box.steps.all { it.key == step.key || stepDone(day, it) }) {
            ops += LogDoc.tickOp(d, box.key, true)
            if (box.key == "piano" || box.key == "voice") {
                val m = day.plannedMinutes(box.key)
                if (m > 0 && LogDoc.minutes(_state.value.log, d, box.key) == 0) ops += LogDoc.addOp("mins", d, box.key, m)
            }
        }
        if (!value && LogDoc.tick(_state.value.log, d, box.key) == true) ops += LogDoc.tickOp(d, box.key, false)
        addOps(*ops.toTypedArray())
    }

    /** Ticks the next open step of [key] on [date] (from a widget or a notification button). */
    fun doNextStep(date: LocalDate, key: String, stepKey: String? = null) {
        val day = _state.value.plan.day(date) ?: return
        val box = day.boxes.firstOrNull { it.key == key } ?: return
        if (box.steps.isEmpty()) { done(date, key); return }
        val step = stepKey?.let { k -> box.steps.firstOrNull { it.key == k } } ?: nextStep(day, box) ?: return
        setStep(day, box, step, true)
    }

    fun brief(date: LocalDate = LocalDate.now()): Coach.Brief = Coach.brief(_state.value.plan, _state.value.health, date, LocalTime.now())

    /** Re-emits state and redraws everything that shows it. */
    private fun publish() {
        _state.value = _state.value.copy(log = view(), pending = ops().length(), version = _state.value.version + 1)
        scope.launch {
            runCatching { Widgets.updateAll(app) }
            runCatching { Notifier.refreshPinned(app) }
        }
    }

    // ---------- sync
    private fun schedulePush() {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(8, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork("push", ExistingWorkPolicy.REPLACE, req)
    }

    fun refreshAsync() { scope.launch { refresh(full = true) } }

    /** Pulls the plan and the log, pushes taps, reads Health Connect. Safe to call from a worker. */
    suspend fun refresh(full: Boolean) = withContext(Dispatchers.IO) {
        val k = token()
        _state.value = _state.value.copy(loading = true)
        var err = ""
        if (k != null) {
            val gh = GitHub(k)
            if (full) err = runCatching { pullPlan(gh) }.exceptionOrNull()?.let(::describe) ?: ""
            runCatching { pushLog(gh) }.exceptionOrNull()?.let { if (err.isEmpty()) err = describe(it) }
        }
        runCatching { readHealth() }
        if (k != null) runCatching { pushHealth(GitHub(k)) }
        _state.value = _state.value.copy(loading = false, error = err)
        publish()
    }

    suspend fun readHealth() {
        val h = HealthReader(app).read(LocalDate.now())
        if (h.connected || _state.value.health.takenAt == 0L) {
            File(app.filesDir, "health.json").writeText(h.toJson().toString())
            _state.value = _state.value.copy(health = h)
        }
    }

    private fun describe(e: Throwable): String = when {
        e is GitHub.HttpError && (e.code == 401 || e.code == 403) -> "Your GitHub key didn't work. Make a new one in Settings."
        e is GitHub.HttpError && e.code == 404 -> "Your key can't see GeneralStorage. Make a new one in Settings."
        else -> "Offline. Showing the last plan you loaded."
    }

    private fun pullPlan(gh: GitHub) {
        val out = mutableMapOf<String, String>()
        for (n in listOf("social/calendar.md", "inbox.md", "me/recovery.md", "me/profile.md")) gh.file(n)?.let { out[n] = it }
        val today = LocalDate.now()
        // Only the weeks that matter now: last week, this week and anything ahead.
        val thisWeek = today.minusWeeks(1).let { "%d-W%02d".format(it.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR), it.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)) }
        for (id in gh.list("weeks").filter { it >= thisWeek }) gh.file("weeks/$id.md")?.let { out["weeks/$id.md"] = it }
        files = out
        val now = System.currentTimeMillis()
        File(app.filesDir, "plan_files.json").writeText(JSONObject(out as Map<*, *>).toString())
        prefs.edit().putLong("built", now).apply()
        _state.value = _state.value.copy(plan = Md.plan(out, today, now))
    }

    private suspend fun pushLog(gh: GitHub) = syncLock.withLock {
        val remote = gh.json("log/app.json")
        val pending = ops()
        val n = pending.length()
        if (n == 0) {
            if (remote != null) prefs.edit().putString("log_base", remote.first.toString()).putBoolean("synced", true).apply()
            _state.value = _state.value.copy(synced = remote != null)
            return@withLock
        }
        val now = System.currentTimeMillis()
        val merged = LogDoc.apply(remote?.first ?: LogDoc.empty(), pending, now)
        gh.put("log/app.json", merged.toString(1), remote?.second, "planLAB app: Matt's taps ${LocalDate.now()}")
        // Drop only the ops that were pushed; taps made during the push stay queued.
        synchronized(this) {
            val all = ops(); val rest = JSONArray()
            for (i in n until all.length()) rest.put(all.get(i))
            prefs.edit().putString("log_base", merged.toString()).putString("log_ops", rest.toString()).putBoolean("synced", true).apply()
        }
        _state.value = _state.value.copy(synced = true)
    }

    /** Once a day, a 30-day health summary goes to log/health.json in the private repo, so Claude can plan with it. */
    private fun pushHealth(gh: GitHub) {
        if (!prefs.getBoolean("share_health", true)) return
        val h = _state.value.health
        if (!h.connected || h.days.isEmpty()) return
        val today = LocalDate.now().toString()
        val hour = LocalTime.now().hour
        val last = prefs.getString("health_pushed", "")
        // Morning push (after sleep lands) and an evening one with the full day.
        val slot = if (hour >= 18) "$today-pm" else "$today-am"
        if (last == slot) return
        val b = brief()
        val doc = h.toJson().put("updated", System.currentTimeMillis()).put("today", JSONObject()
            .put("date", today).put("phase", b.phase.label).put("readiness", b.readiness?.score ?: JSONObject.NULL)
            .put("readinessLabel", b.readiness?.label ?: "").put("bodyCall", b.body.title).put("proteinTarget", b.protein))
        val sha = runCatching { gh.json("log/health.json")?.second }.getOrNull()
        gh.put("log/health.json", doc.toString(1), sha, "planLAB app: health summary $today")
        prefs.edit().putString("health_pushed", slot).apply()
    }

    fun sendWish(text: String, done: (Boolean) -> Unit) {
        val k = token() ?: return done(false)
        scope.launch {
            val ok = runCatching { GitHub(k).wish(text, LocalDate.now().toString()) }.isSuccess
            withContext(Dispatchers.Main) { done(ok) }
        }
    }
}
