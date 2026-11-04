package com.mattlabs.planlab.core

import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * XP, levels, streaks and milestones, from projects/planlab/rewards.md (Claude keeps the numbers there) and Matt's
 * taps in log/app.json. Everything is worked out from the log, so nothing is stored that could get lost, and XP is
 * never taken away: a missed day pauses things, it doesn't cost anything.
 */
data class Level(val n: Int, val xp: Int, val name: String)
data class Milestone(val name: String, val date: LocalDate?, val when_: String, val xp: Int) {
    val key: String get() = Md.keyOf(name)
}

data class Rewards(
    val step: Int = 1, val area: Int = 5, val day: Int = 15, val bugLogged: Int = 2, val bugFixed: Int = 10, val habit: Int = 25, val wish: Int = 10,
    val levels: List<Level> = DEFAULT_LEVELS,
    val milestones: List<Milestone> = emptyList(),
    val autoDays: Set<LocalDate> = emptySet(),
) {
    companion object {
        val DEFAULT_LEVELS = listOf(
            Level(1, 0, "Starting line"), Level(2, 100, "Showing up"), Level(3, 300, "Rhythm"), Level(4, 600, "Groove"), Level(5, 1000, "Locked in"),
            Level(6, 1500, "Steady hands"), Level(7, 2100, "Rebuilder"), Level(8, 2800, "Back on two feet"), Level(9, 3600, "Trailblazer"), Level(10, 4500, "Ren Faire ready"),
        )

        private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

        private fun rows(body: String): List<List<String>> = body.lines().map { it.trim() }
            .filter { it.startsWith("|") && !Regex("""^\|[\s:|-]+\|?$""").matches(it) }
            .map { l -> l.trim('|').split("|").map { Md.strip(it.trim()) } }
            .drop(1) // header

        /** "Sat 11-14", "~Mon 11-30", "Sat 2027-09-04", "~mid-February", "~July". */
        fun date(s: String, today: LocalDate): LocalDate? {
            Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").find(s)?.let { m -> return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull() }
            Regex("""(\d{1,2})-(\d{1,2})""").find(s)?.let { m -> return Md.guessDate(today.year, m.groupValues[1].toInt(), m.groupValues[2].toInt(), false, today) }
            val low = s.lowercase()
            val mi = MONTHS.indexOfFirst { low.contains(Regex("""\b$it""")) }
            if (mi < 0) return null
            val d = when { "early" in low -> 5; "mid" in low -> 15; "late" in low || "end" in low -> 25; else -> 1 }
            return Md.guessDate(today.year, mi + 1, d, false, today)
        }

        fun parse(md: String?, today: LocalDate): Rewards {
            if (md.isNullOrBlank()) return Rewards()
            var r = Rewards()
            for ((head, body) in Md.sections(md)) {
                val h = head.lowercase()
                when {
                    h.startsWith("xp") -> for (row in rows(body)) {
                        val what = row.getOrNull(0)?.lowercase() ?: continue
                        val n = row.getOrNull(1)?.let { Regex("""\d+""").find(it)?.value?.toInt() } ?: continue
                        r = when {
                            "habit" in what -> r.copy(habit = n)
                            "fix" in what -> r.copy(bugFixed = n)
                            "bugs me" in what || "log" in what -> r.copy(bugLogged = n)
                            "wish" in what -> r.copy(wish = n)
                            "whole day" in what || "full day" in what -> r.copy(day = n)
                            "area" in what -> r.copy(area = n)
                            "step" in what -> r.copy(step = n)
                            else -> r
                        }
                    }
                    h.startsWith("level") -> {
                        val lv = rows(body).mapNotNull { row ->
                            val n = row.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                            val xp = row.getOrNull(1)?.replace(",", "")?.toIntOrNull() ?: return@mapNotNull null
                            Level(n, xp, row.getOrNull(2) ?: "Level $n")
                        }.sortedBy { it.xp }
                        if (lv.isNotEmpty()) r = r.copy(levels = lv)
                    }
                    h.startsWith("streak") -> {
                        // "Surgery days (Tue 11-17 to Thu 11-19) count automatically."
                        val days = mutableSetOf<LocalDate>()
                        for (line in body.lines().filter { "automatic" in it.lowercase() }) {
                            val ds = Regex("""(?:\d{4}-)?\d{1,2}-\d{1,2}""").findAll(line).mapNotNull { date(it.value, today) }.toList()
                            if (ds.size >= 2) { var d = ds.first(); while (!d.isAfter(ds.last()) && days.size < 60) { days += d; d = d.plusDays(1) } } else days += ds
                        }
                        r = r.copy(autoDays = days)
                    }
                    h.startsWith("milestone") -> r = r.copy(milestones = rows(body).mapNotNull { row ->
                        val name = row.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val xp = row.getOrNull(2)?.let { Regex("""\d+""").find(it)?.value?.toInt() } ?: 0
                        Milestone(name, row.getOrNull(1)?.let { date(it, today) }, row.getOrNull(1) ?: "", xp)
                    })
                }
            }
            return r
        }
    }
}

/** The groups that get their own level. Box keys are matched by their first word. */
object Areas {
    val ORDER = listOf("Body", "Music", "Home", "Elizabeth", "Kitchen", "Life")
    fun of(key: String): String {
        val k = key.lowercase().split('/', '-', ' ', ':').firstOrNull() ?: ""
        return when {
            k in setOf("body", "stretch", "pt", "rehab", "elevate", "walk", "strength", "mobility", "pf", "pelvic", "workout", "move") -> "Body"
            k in setOf("piano", "voice", "guitar", "music", "sing", "singing", "practice") -> "Music"
            k in setOf("clean", "home", "bug", "bugs", "tidy", "laundry", "chores", "house") -> "Home"
            k in setOf("elizabeth", "date", "partner", "social", "liz", "us") -> "Elizabeth"
            k in setOf("meals", "eat", "cook", "cooking", "grocery", "groceries", "prep", "food", "kitchen") -> "Kitchen"
            else -> "Life"
        }
    }
}

/** What one day (or one other source) earned. [finished] holds the box keys finished that day. */
data class Earned(val xp: Int, val byArea: Map<String, Int>, val finished: Set<String>, val habits: Set<String> = emptySet()) {
    fun toJson(): JSONObject = JSONObject().put("xp", xp).put("a", JSONObject(byArea as Map<*, *>)).put("f", org.json.JSONArray(finished.toList())).put("h", org.json.JSONArray(habits.toList()))
    companion object {
        private fun set(o: JSONObject, k: String) = o.optJSONArray(k)?.let { f -> (0 until f.length()).map { f.getString(it) }.toSet() } ?: emptySet()
        fun fromJson(o: JSONObject): Earned = Earned(o.optInt("xp"), o.optJSONObject("a")?.let { a -> a.keys().asSequence().associateWith { a.getInt(it) } } ?: emptyMap(), set(o, "f"), set(o, "h"))
    }
}

data class Progress(
    val xp: Int,
    val today: Int,
    val level: Level,
    val next: Level?,
    val areas: List<Pair<String, Int>>,   // area to XP
    val streak: Int,
    val shieldReady: Boolean,
    val shieldedToday: Boolean,
    val milestones: List<Pair<Milestone, Boolean>>, // milestone to done
    val habits: Int,
) {
    val intoLevel: Float get() = next?.let { (xp - level.xp).toFloat() / (it.xp - level.xp).coerceAtLeast(1) } ?: 1f
}

object ProgressCalc {
    private val DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val AREA_KEYS = setOf("meals", "body", "piano", "voice", "clean")

    /** A step that starts "Habit:" (a habit from a bugs-me), keyed the same way whether or not the plan is loaded. */
    fun habitId(label: String): String? = if (Md.strip(label).trim().lowercase().startsWith("habit:")) Md.keyOf(label) else null

    /** What each source earned: a date ("2030-03-04"), an inbox item ("inbox:<key>") or a bugs-me count. [exact] tells whether the plan backs it. */
    fun earned(plan: Plan, log: JSONObject, r: Rewards): Map<String, Pair<Earned, Boolean>> {
        val out = mutableMapOf<String, Pair<Earned, Boolean>>()
        val ticks = log.optJSONObject("ticks") ?: JSONObject()
        val foodDates = log.optJSONObject("food")?.keys()?.asSequence()?.filter { DATE.matches(it) }?.toList() ?: emptyList()
        val dates = (ticks.keys().asSequence().filter { DATE.matches(it) } + plan.weeks.flatMap { w -> w.days.map { it.date.toString() } } + foodDates).toSet()
        for (date in dates) {
            val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: continue
            val day = plan.day(d)
            val area = mutableMapOf<String, Int>()
            val fin = mutableSetOf<String>()
            val hab = mutableSetOf<String>()
            var xp = 0
            fun add(key: String, n: Int) { if (n == 0) return; xp += n; val a = Areas.of(key); area[a] = (area[a] ?: 0) + n }
            if (day != null) {
                for (box in day.boxes) {
                    val ticked = box.steps.filter { LogDoc.ticked(log, date, it.key, it.done) }
                    val steps = ticked.size
                    ticked.forEach { habitId(it.label)?.let(hab::add) }
                    add(box.key, steps * r.step)
                    val explicit = LogDoc.tick(log, date, box.key)
                    val done = explicit ?: ((box.steps.isNotEmpty() && steps == box.steps.size) || box.done)
                    if (done) { add(box.key, r.area + if (box.steps.isEmpty()) r.step else 0); fin += box.key }
                }
                if (day.boxes.isNotEmpty() && fin.size == day.boxes.size) { xp += r.day; area["Life"] = (area["Life"] ?: 0) + r.day }
            } else {
                // A day the phone no longer has the plan for: count what the log says.
                val l = ticks.optJSONObject(date) ?: JSONObject()
                for (k in l.keys()) if (l.opt(k) == true) {
                    if (k in AREA_KEYS) { add(k, r.area); fin += k } else { add(k, r.step); k.indexOf("habit-").takeIf { it >= 0 }?.let { hab += k.substring(it) } }
                }
            }
            // Eating is tracked, not ticked: each food logged earns a step, up to 5 a day.
            add("meals", LogDoc.food(log, date).size.coerceAtMost(5) * r.step)
            val pf = LogDoc.pelvic(log, date).coerceAtMost(2)
            add("pelvic", pf * r.step)
            val el = ticks.optJSONObject("elevate-$date")?.optInt("n", 0)?.coerceAtMost(3) ?: 0
            add("elevate", el * r.step)
            val bugs = ticks.optJSONObject("bug-$date")?.optInt("n", 0) ?: 0
            add("bug", bugs * r.bugLogged)
            out[date] = Earned(xp, area, fin, hab) to (day != null)
        }
        for (i in plan.inbox) {
            val done = LogDoc.ticked(log, "inbox", i.key, i.done)
            val steps = i.steps.count { LogDoc.ticked(log, "inbox", it.key, it.done) }
            val xp = steps * r.step + if (done) (if (i.date != null) r.wish else r.area) else 0
            if (xp > 0) out["inbox:${i.key}"] = Earned(xp, mapOf(Areas.of(i.text) to xp), emptySet()) to true
        }
        ticks.optJSONObject("bugs")?.let { b -> for (k in b.keys()) if (b.opt(k) == true) out["bugfix:$k"] = Earned(r.bugFixed, mapOf("Home" to r.bugFixed), emptySet()) to true }
        ticks.optJSONObject("milestones")?.let { m ->
            for (ms in r.milestones) if (m.opt(ms.key) == true) out["milestone:${ms.key}"] = Earned(ms.xp, mapOf("Life" to ms.xp), emptySet()) to true
        }
        return out
    }

    fun levelOf(xp: Int, r: Rewards): Pair<Level, Level?> {
        val lv = r.levels.ifEmpty { Rewards.DEFAULT_LEVELS }
        val cur = lv.lastOrNull { it.xp <= xp } ?: lv.first()
        lv.firstOrNull { it.xp > xp }?.let { return cur to it }
        // Past the table: one more level every (last gap + 100) XP.
        val last = lv.last(); val gap = (if (lv.size > 1) last.xp - lv[lv.size - 2].xp else 1000) + 100
        val extra = (xp - last.xp) / gap
        val now = if (extra == 0) last else Level(last.n + extra, last.xp + extra * gap, "${last.name} +$extra")
        return now to Level(now.n + 1, now.xp + gap, "${last.name} +${extra + 1}")
    }

    /** Days in a row with at least one area finished. One missed day per week is shielded (it pauses), and the auto days count. Today doesn't break it. */
    fun streak(earned: Map<String, Earned>, r: Rewards, today: LocalDate): Triple<Int, Boolean, Boolean> {
        fun counts(d: LocalDate) = (earned[d.toString()]?.finished?.isNotEmpty() == true) || d in r.autoDays
        fun week(d: LocalDate) = d.get(IsoFields.WEEK_BASED_YEAR) * 100 + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val first = earned.keys.filter { DATE.matches(it) }.minOrNull()?.let { LocalDate.parse(it) } ?: today
        val used = mutableSetOf<Int>()
        var n = if (counts(today)) 1 else 0
        var d = today.minusDays(1)
        var shieldedToday = false
        while (!d.isBefore(first)) {
            if (counts(d)) n++
            else if (week(d) !in used) { used += week(d); if (d == today.minusDays(1)) shieldedToday = true }
            else break
            d = d.minusDays(1)
        }
        return Triple(n, week(today) !in used, shieldedToday)
    }

    /** The same "Habit:" step ticked 7 days running earns the habit bonus, once per 7-day run. */
    fun habits(earned: Map<String, Earned>): Int {
        val byKey = mutableMapOf<String, MutableList<LocalDate>>()
        for ((k, e) in earned) if (DATE.matches(k)) for (f in e.habits) byKey.getOrPut(f) { mutableListOf() } += LocalDate.parse(k)
        var n = 0
        for (ds in byKey.values) {
            var run = 0; var prev: LocalDate? = null
            for (d in ds.sorted()) { run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1; if (run % 7 == 0) n++; prev = d }
        }
        return n
    }

    fun progress(all: Map<String, Earned>, r: Rewards, log: JSONObject, today: LocalDate): Progress {
        val habits = habits(all)
        val area = mutableMapOf<String, Int>()
        for (e in all.values) for ((a, n) in e.byArea) area[a] = (area[a] ?: 0) + n
        area["Life"] = (area["Life"] ?: 0) + habits * r.habit
        val xp = all.values.sumOf { it.xp } + habits * r.habit
        val (lv, next) = levelOf(xp, r)
        val (streak, shield, shieldedToday) = streak(all, r, today)
        val ms = log.optJSONObject("ticks")?.optJSONObject("milestones")
        return Progress(
            xp = xp, today = all[today.toString()]?.xp ?: 0, level = lv, next = next,
            areas = Areas.ORDER.map { it to (area[it] ?: 0) },
            streak = streak, shieldReady = shield, shieldedToday = shieldedToday,
            milestones = r.milestones.map { it to (ms?.opt(it.key) == true) }, habits = habits,
        )
    }

    /** Area levels use the same table at a quarter of the XP. */
    fun areaLevel(xp: Int, r: Rewards): Pair<Int, Float> {
        val (lv, next) = levelOf(xp * 4, r)
        return lv.n to (next?.let { (xp * 4 - lv.xp).toFloat() / (it.xp - lv.xp).coerceAtLeast(1) } ?: 1f)
    }
}
