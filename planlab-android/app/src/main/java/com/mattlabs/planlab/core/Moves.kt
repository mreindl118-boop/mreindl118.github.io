package com.mattlabs.planlab.core

import java.time.LocalTime

/** One exercise from body/moves.md: "## Dead bug" then "- **Do:** …", "- **Cues:** …", "- **Dose:** …", "- **Safe:** …", "- **Demo:** [Video](…)". */
data class Move(val key: String, val name: String, val fields: List<Pair<String, String>>, val links: List<Link>) {
    fun field(label: String): String? = fields.firstOrNull { it.first.equals(label, true) }?.second
    /** The cues, one per line ("a · b · c" or "a; b; c"). */
    val cues: List<String> get() = field("Cues")?.split(Regex("""\s+·\s+|;\s+"""))?.map { it.trim().trimEnd('.') }?.filter { it.isNotBlank() } ?: emptyList()
}

/** What kind of step this is, so a 1-minute break never looks like a 20-minute workout. */
data class StepKind(val kind: String, val minutes: Int, val at: LocalTime?) {
    val badge: String get() = buildString {
        append(kind)
        if (minutes > 0) append(" · $minutes min")
        if (at != null) append(" · ").append(at.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")).lowercase())
    }
}

object Moves {
    private val FIELD = Regex("""^-\s+\*\*(.+?):\*\*\s*(.+)$""")
    private val PLAIN = Regex("""^-\s+([A-Z][A-Za-z /-]{1,20}):\s+(.+)$""")
    private val AT = Regex("""\bat\s+(\d{1,2})(?::(\d\d))?\s*(am|pm|a\.m\.|p\.m\.)?""", RegexOption.IGNORE_CASE)
    private val SECS = Regex("""(\d+)\s*s\b""")

    fun parse(md: String?): Map<String, Move> {
        if (md.isNullOrBlank()) return emptyMap()
        val out = linkedMapOf<String, Move>()
        for ((head, body) in Md.sections(md)) {
            val name = Md.strip(head)
            val fields = mutableListOf<Pair<String, String>>()
            val links = mutableListOf<Link>()
            for (l in body.lines()) {
                val t = l.trim()
                val m = FIELD.find(t) ?: PLAIN.find(t) ?: continue
                val (label, raw) = m.groupValues[1].trim() to m.groupValues[2].trim()
                val (text, ls) = Md.splitLinks(raw)
                links += ls.map { if (it.label.equals("video", true) || it.label.equals("demo", true)) it.copy(label = "Watch demo") else it }
                val shown = Md.strip(text).trim()
                if (shown.isNotEmpty() && !(label.equals("Demo", true) && ls.isNotEmpty() && shown.equals(ls.first().label, true))) fields += label to shown
            }
            if (fields.isNotEmpty() || links.isNotEmpty()) out[Md.keyOf(name)] = Move(Md.keyOf(name), name, fields, links)
        }
        return out
    }

    /** The move for a step: its app:move/<key> link, else the longest move name the step's text starts with. */
    fun find(moves: Map<String, Move>, label: String, links: List<Link> = emptyList()): Move? {
        links.firstOrNull { it.url.startsWith("app:move/") }?.let { l -> moves[l.url.removePrefix("app:move/")]?.let { return it } }
        val low = label.lowercase().replace(Regex("""^(break|check-in) \d+.*?:\s+"""), "")
        return moves.values.filter { low.startsWith(it.name.lowercase()) }.maxByOrNull { it.name.length }
    }

    /** "Break 1 at 10:30, 1 min: …" -> 10:30. Bare hours under 7 are read as pm. */
    fun timeOf(label: String): LocalTime? {
        val m = AT.find(label) ?: return null
        var h = m.groupValues[1].toInt(); val min = m.groupValues[2].toIntOrNull() ?: 0
        val ap = m.groupValues[3].lowercase().replace(".", "")
        if (ap == "pm" && h < 12) h += 12
        if (ap == "am" && h == 12) h = 0
        if (ap.isEmpty() && h in 1..6) h += 12
        return runCatching { LocalTime.of(h, min) }.getOrNull()
    }

    /** Session for a body box (minutes from the day's Body line), Break for a move box, else from the step's minutes. */
    fun kindOf(box: Box, step: Box?, day: Day?): StepKind? {
        val label = step?.label ?: box.label
        val mins = Md.minutes(label).takeIf { it > 0 } ?: (SECS.find(label)?.groupValues?.get(1)?.toInt()?.let { if (it >= 60) it / 60 else 0 } ?: 0)
        return when {
            box.key == "move" || label.lowercase().startsWith("break") -> StepKind("Break", mins.takeIf { it > 0 } ?: 1, timeOf(label))
            box.key == "mind" || label.lowercase().startsWith("check-in") -> StepKind("Check-in", mins.takeIf { it > 0 } ?: 1, timeOf(label))
            step == null && box.key == "body" -> StepKind("Session", day?.let { Md.minutes(it.body) } ?: 0, null)
            step == null -> null
            box.key == "body" -> StepKind("Exercise", mins, null)
            mins in 1..3 -> StepKind("Quick", mins, null)
            else -> null
        }
    }
}
