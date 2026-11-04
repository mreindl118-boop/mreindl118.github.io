package com.mattlabs.planlab.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Markdown -> plan. Same rules as planlab/app.js; keep them in step. */
object Md {
    private val BOLD = Regex("""\*\*(.+?)\*\*""")
    private val CODE = Regex("""`([^`]+)`""")
    private val LINK = Regex("""\[([^\]]+)\]\([^)]+\)""")
    private val MDREF = Regex("""\s*\(?\b[\w./-]+\.md\)?""")
    private val SECTION = Regex("^## ", RegexOption.MULTILINE)
    private val DAYHEAD = Regex("""^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)\s+(\d\d)-(\d\d)""")
    private val BOXES = Regex("""\[( |x|X)\]\s*([^\[]+)""")
    private val CHECK = Regex("""^\s*- \[( |x|X)\]\s*(.+)""")
    private val MDATE = Regex("""(\d{4}-)?(\d\d)-(\d\d)""")
    private val INBOX_DATE = Regex("""·\s*((?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\s+)?(\d{4}-)?(\d\d)-(\d\d)""")

    fun strip(s: String): String = s.replace(BOLD, "$1").replace(CODE, "$1").replace(LINK, "$1").replace(MDREF, "").trim()

    fun keyOf(s: String): String =
        strip(s).lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60)

    fun minutes(s: String): Int = Regex("""(\d+)\s*min""").find(s)?.groupValues?.get(1)?.toInt() ?: 0

    fun sections(text: String): List<Pair<String, String>> = text.split(SECTION).drop(1).map { sec ->
        val i = sec.indexOf('\n')
        if (i < 0) sec.trim() to "" else sec.substring(0, i).trim() to sec.substring(i + 1)
    }

    fun isoMonday(id: String): LocalDate = LocalDate.parse("$id-1", DateTimeFormatter.ISO_WEEK_DATE)

    fun guessDate(y: Int, m: Int, d: Int, explicit: Boolean, today: LocalDate): LocalDate? {
        val date = runCatching { LocalDate.of(y, m, d) }.getOrNull() ?: return null
        return if (!explicit && daysBetween(date, today) > 120) date.plusYears(1) else date
    }

    fun week(id: String, text: String): Week {
        val mon = isoMonday(id)
        val phase = Regex("""\*\*Phase:\*\*\s*(.+)""").find(text)?.groupValues?.get(1)?.let(::strip) ?: ""
        val focus = Regex("""\*\*Focus this week:\*\*\s*(.+)""").find(text)?.groupValues?.get(1)?.let(::strip) ?: ""
        val glance = mutableListOf<Pair<String, String>>()
        val days = mutableListOf<Day>()
        for ((head, body) in sections(text)) {
            if (head.lowercase().startsWith("the week at a glance")) {
                for (line in body.lines()) {
                    val g = Regex("""^- \*\*(.+?):\*\*\s*(.+)""").find(line.trim()) ?: continue
                    glance += strip(g.groupValues[1]) to strip(g.groupValues[2])
                }
                continue
            }
            val dm = DAYHEAD.find(head) ?: continue
            val month = dm.groupValues[2].toInt()
            val year = if (mon.monthValue == 12 && month == 1) mon.year + 1 else mon.year
            val date = runCatching { LocalDate.of(year, month, dm.groupValues[3].toInt()) }.getOrNull() ?: continue
            var eat = emptyList<String>(); var bodyLine = ""; var practice = emptyList<String>(); var coming = emptyList<String>()
            val boxes = mutableListOf<Box>()
            // Sub-steps, two accepted forms: a "### piano" heading followed by "- [ ] step" lines (key "piano/<step>"),
            // or an indented "  - [ ] piano: step" line under the box row (key = the whole label).
            val steps = linkedMapOf<String, MutableList<Box>>()
            val heads = mutableMapOf<String, String>()
            var area: String? = null
            for (raw in body.lines()) {
                val t = raw.trim()
                if (t.startsWith("### ")) { area = keyOf(t.removePrefix("### ")); heads[area] = strip(t.removePrefix("### ")); continue }
                if (!t.startsWith("- ")) continue
                val indented = raw.startsWith(" ") || raw.startsWith("\t")
                val single = CHECK.find(raw)
                if (area != null && single != null) {
                    val label = strip(single.groupValues[2])
                    steps.getOrPut(area) { mutableListOf() } += Box("$area/${keyOf(label)}", label, single.groupValues[1].equals("x", true))
                    continue
                }
                if (indented && single != null) {
                    val full = strip(single.groupValues[2])
                    val c = full.indexOf(':')
                    val parent = if (c > 0) keyOf(full.substring(0, c)) else boxes.lastOrNull()?.key ?: continue
                    val label = if (c > 0) full.substring(c + 1).trim() else full
                    steps.getOrPut(parent) { mutableListOf() } += Box(keyOf(full), label, single.groupValues[1].equals("x", true))
                    continue
                }
                val line = t.substring(2)
                if (line.startsWith("[")) {
                    for (b in BOXES.findAll(line)) {
                        val label = b.groupValues[2].trim()
                        boxes += Box(keyOf(label), label, b.groupValues[1].equals("x", true))
                    }
                    continue
                }
                val c = line.indexOf(':'); if (c < 0) continue
                val k = line.substring(0, c).trim().lowercase(); val v = line.substring(c + 1)
                val parts = v.split(" · ").filter { it.isNotBlank() }.map(::strip)
                when (k) {
                    "eat" -> eat = parts
                    "body" -> bodyLine = strip(v)
                    "practice" -> practice = parts
                    "coming up" -> coming = parts
                }
            }
            for ((parent, list) in steps) {
                val i = boxes.indexOfFirst { it.key == parent }
                if (i >= 0) boxes[i] = boxes[i].copy(steps = list)
                else boxes += Box(parent, heads[parent] ?: parent.replace('-', ' '), false, list)
            }
            // Eat is tracked, not ticked (Matt, 2026-11-04): the meals box leaves the task list and its steps become the food log's planned meals.
            val mealsBox = boxes.firstOrNull { it.key == "meals" }
            if (mealsBox != null) boxes.remove(mealsBox)
            days += Day(date, head, eat, bodyLine, practice, coming, boxes, mealsBox?.steps ?: emptyList())
        }
        return Week(id, mon, phase, focus, glance, days)
    }

    private fun tableRows(text: String): List<List<String>> = text.lines().map { it.trim() }
        .filter { it.startsWith("|") && !Regex("""^\|[\s\-|]+\|$""").matches(it) }
        .map { row -> row.removePrefix("|").removeSuffix("|").split("|").map { it.trim() } }

    fun calendar(text: String, today: LocalDate): List<CalEvent> {
        val rows = tableRows(text.split("## Work and friends")[0]).drop(1)
        return rows.mapNotNull { row ->
            if (row.size < 2) return@mapNotNull null
            val m = MDATE.find(row[0]) ?: return@mapNotNull null
            val explicit = m.groupValues[1].isNotEmpty()
            val y = if (explicit) m.groupValues[1].take(4).toInt() else today.year
            val date = guessDate(y, m.groupValues[2].toInt(), m.groupValues[3].toInt(), explicit, today) ?: return@mapNotNull null
            CalEvent(date, strip(row[1]), strip(row.getOrElse(2) { "" }), strip(row.getOrElse(3) { "" }), row[1].contains("**"))
        }.sortedBy { it.date }
    }

    /** Checklists by section. An indented box is a step of the box above it. */
    fun checklists(text: String): List<Pair<String, List<Item>>> = sections(text).mapNotNull { (head, body) ->
        val name = strip(head)
        val items = mutableListOf<Item>()
        for (l in body.lines()) {
            val m = CHECK.find(l) ?: continue
            val t = strip(m.groupValues[2])
            val item = Item(keyOf(t), t, m.groupValues[1].equals("x", true), name)
            val indented = l.startsWith("  ") || l.startsWith("\t")
            if (indented && items.isNotEmpty()) items[items.size - 1] = items.last().let { it.copy(steps = it.steps + item) }
            else items += item
        }
        if (items.isEmpty()) null else name to items
    }

    fun inbox(text: String, today: LocalDate): List<Item> = checklists(text).flatMap { (_, items) ->
        items.map { it ->
            val m = INBOX_DATE.find(it.text)
            val explicit = m != null && m.groupValues[2].isNotEmpty()
            val date = m?.let { mm ->
                guessDate(if (explicit) mm.groupValues[2].take(4).toInt() else today.year, mm.groupValues[3].toInt(), mm.groupValues[4].toInt(), explicit, today)
            }
            val i = it.text.indexOf(" · ")
            it.copy(date = date, what = if (i < 0) it.text else it.text.substring(0, i))
        }
    }

    fun proteinTarget(profile: String): Int =
        Regex("""Protein target:\s*about\s*(\d+)\s*g""", RegexOption.IGNORE_CASE).find(profile)?.groupValues?.get(1)?.toInt() ?: 0

    /** Build the whole plan from the raw files, keyed by their path under projects/planlab/. */
    fun plan(files: Map<String, String>, today: LocalDate, builtAt: Long): Plan {
        val weeks = files.keys.filter { it.startsWith("weeks/") && it.endsWith(".md") }.sorted()
            .mapNotNull { p -> runCatching { week(p.removePrefix("weeks/").removeSuffix(".md"), files[p]!!) }.getOrNull() }
        val setup = checklists(files["me/recovery.md"] ?: "").firstOrNull()?.second?.map { it.copy(group = "setup") } ?: emptyList()
        return Plan(
            weeks = weeks,
            calendar = calendar(files["social/calendar.md"] ?: "", today),
            inbox = inbox(files["inbox.md"] ?: "", today),
            setup = setup,
            proteinTarget = proteinTarget(files["me/profile.md"] ?: ""),
            builtAt = builtAt,
        )
    }
}
