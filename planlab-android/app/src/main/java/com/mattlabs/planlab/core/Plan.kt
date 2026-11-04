package com.mattlabs.planlab.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// The plan, read from the Markdown in GeneralStorage projects/planlab/. Mirrors planlab/app.js on the site and
// projects/planlab/tools/app_data.py, so all three read the same files the same way.

/** A link on a step: "app:piano" opens a planLAB view, anything else (https) opens outside. */
data class Link(val label: String, val url: String)

/** A checkbox. [steps] are its sub-steps (each checkable); the box counts done when all of them are. [links] open where it gets done. */
data class Box(val key: String, val label: String, val done: Boolean, val steps: List<Box> = emptyList(), val links: List<Link> = emptyList())

data class Day(
    val date: LocalDate,
    val label: String,
    val eat: List<String>,
    val body: String,
    val practice: List<String>,
    val coming: List<String>,
    val boxes: List<Box>,
    /** The day's planned meals (the "### meals" steps). Eating isn't a task: they're one-tap food log entries, not boxes. */
    val meals: List<Box> = emptyList(),
) {
    /** "piano 25 min" -> 25 for key "piano". */
    fun plannedMinutes(key: String): Int = practice.firstOrNull { it.lowercase().startsWith(key) }?.let { Md.minutes(it) } ?: 0
    fun protein(): Int = Regex("""~?(\d+)\s*g protein""").find(eat.joinToString(" "))?.groupValues?.get(1)?.toInt() ?: 0
}

data class Week(val id: String, val start: LocalDate, val phase: String, val focus: String, val glance: List<Pair<String, String>>, val days: List<Day>)

data class CalEvent(val date: LocalDate, val what: String, val who: String, val notes: String, val big: Boolean)

data class Item(val key: String, val text: String, val done: Boolean, val group: String, val date: LocalDate? = null, val what: String = text, val steps: List<Item> = emptyList(), val links: List<Link> = emptyList())

data class Plan(
    val weeks: List<Week>,
    val calendar: List<CalEvent>,
    val inbox: List<Item>,
    val setup: List<Item>,
    val proteinTarget: Int,
    val builtAt: Long,
) {
    fun day(date: LocalDate): Day? = weeks.asSequence().flatMap { it.days.asSequence() }.firstOrNull { it.date == date }
    fun weekOf(date: LocalDate): Week? = weeks.firstOrNull { !date.isBefore(it.start) && !date.isAfter(it.start.plusDays(6)) }
    fun lastDay(): LocalDate? = weeks.flatMap { it.days }.maxOfOrNull { it.date }

    /** The bold row in calendar.md: the date the app counts down to (surgery, for now). */
    fun bigDay(today: LocalDate): CalEvent? =
        calendar.firstOrNull { it.big && ChronoUnit.DAYS.between(today, it.date) > -60 } ?: calendar.firstOrNull { it.big }

    companion object { val EMPTY = Plan(emptyList(), emptyList(), emptyList(), emptyList(), 0, 0) }
}

/** The recovery phase from the big day: 0 before, 1 non-weight-bearing (14 days), 2 protected walking and PT, 3 cleared. */
enum class Phase(val label: String, val short: String) {
    PRE_OP("Pre-op", "Pre-op"),
    NWB("Non-weight-bearing", "Off the foot"),
    PROTECTED("Protected walking and PT", "PT"),
    CLEARED("Cleared and rebuilding", "Rebuild");

    companion object {
        fun of(big: LocalDate?, today: LocalDate): Phase {
            if (big == null) return PRE_OP
            val n = ChronoUnit.DAYS.between(big, today)
            return when {
                n < 0 -> PRE_OP
                n <= 14 -> NWB
                n <= 90 -> PROTECTED
                else -> CLEARED
            }
        }
    }
}

val NICE_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM d")
fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)
