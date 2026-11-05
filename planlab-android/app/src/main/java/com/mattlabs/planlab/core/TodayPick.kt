package com.mattlabs.planlab.core

import java.time.LocalTime

/** What Today leads with: the one next thing, then what's left, then how much is done. */
object TodayPick {
    data class Next(val box: Box, val step: Box?)

    private val ORDER = listOf("body", "piano", "voice", "clean")
    /** Boxes whose steps have set times (move breaks, mindful check-ins): they come up when due. */
    val TIMED = setOf("move", "mind")

    fun order(boxes: List<Box>): List<Box> = boxes.sortedBy { ORDER.indexOf(it.key).let { i -> if (i < 0) 99 else i } }

    /** A break that is due (from 10 min early to 60 min late) wins; otherwise the first open step in area order. */
    fun next(day: Day, now: LocalTime?, stepDone: (Box) -> Boolean, boxOpen: (Box) -> Boolean): Next? {
        if (now != null) {
            for (box in day.boxes.filter { it.key in TIMED && boxOpen(it) }) {
                val due = box.steps.filter { !stepDone(it) }
                    .mapNotNull { s -> Moves.timeOf(s.label)?.let { s to it } }
                    .filter { (_, at) -> !at.isAfter(now.plusMinutes(10)) && !at.isBefore(now.minusMinutes(60)) }
                    .maxByOrNull { it.second }?.first
                if (due != null) return Next(box, due)
            }
        }
        return rest(day, stepDone, boxOpen).firstOrNull()
    }

    /** Every open area in order, each with its next step (the move box last, since its breaks have set times). */
    fun rest(day: Day, stepDone: (Box) -> Boolean, boxOpen: (Box) -> Boolean): List<Next> {
        val open = day.boxes.filter { boxOpen(it) }
        return (order(open.filter { it.key !in TIMED }) + open.filter { it.key in TIMED }).mapNotNull { box ->
            if (box.steps.isEmpty()) Next(box, null) else box.steps.firstOrNull { !stepDone(it) }?.let { Next(box, it) }
        }
    }

    /** Done and total units of work: each step, or the box itself when it has no steps. */
    fun count(day: Day, stepDone: (Box) -> Boolean, boxDone: (Box) -> Boolean): Pair<Int, Int> {
        var d = 0; var n = 0
        for (b in day.boxes) {
            if (b.steps.isEmpty()) { n++; if (boxDone(b)) d++ }
            else { n += b.steps.size; d += if (boxDone(b)) b.steps.size else b.steps.count(stepDone) }
        }
        return d to n
    }

    /** The first [n] words, without a trailing comma or clause ("Play C major scale, slow" -> "Play C major scale"). */
    fun words(text: String, n: Int): String {
        val head = text.split(" — ", " - ", " (", ", ", ": ", "; ").first().trim()
        // Stop before a dose ("Dead bug 3 × 10" -> "Dead bug"), unless the step starts with a number.
        val ws = head.split(Regex("""\s+"""))
        val cut = ws.withIndex().firstOrNull { (i, w) -> i > 0 && w.isNotEmpty() && (w.first().isDigit() || w.first() == '×' || w.first() == '~') }?.index ?: ws.size
        val kept = ws.take(cut)
        return kept.take(n).joinToString(" ").trimEnd(',', '.', ':', ';')
    }
}
