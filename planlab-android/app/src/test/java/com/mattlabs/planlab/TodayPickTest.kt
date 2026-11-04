package com.mattlabs.planlab

import com.mattlabs.planlab.core.Box
import com.mattlabs.planlab.core.Day
import com.mattlabs.planlab.core.TodayPick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TodayPickTest {
    private fun s(k: String, t: String) = Box(k, t, false)
    private val day = Day(LocalDate.of(2030, 1, 2), "Wed", emptyList(), "Test session 20 min", emptyList(), emptyList(), listOf(
        Box("piano", "Piano", false, listOf(s("p1", "Test scales, 5 min"), s("p2", "Test piece"))),
        Box("move", "Move breaks", false, listOf(s("m1", "Break 1 at 10:30, 1 min: test stretch"), s("m2", "Break 2 at 15:30, 1 min: test walk"))),
        Box("body", "Body", false, listOf(s("b1", "Test bug 3 × 10"), s("b2", "Test bridge"))),
        Box("water", "Water", false),
    ))

    @Test fun bodyComesFirstWhenNoBreakIsDue() {
        val n = TodayPick.next(day, LocalTime.of(9, 0), { false }, { true })!!
        assertEquals("body", n.box.key); assertEquals("b1", n.step!!.key)
    }

    @Test fun aDueBreakWins() {
        val n = TodayPick.next(day, LocalTime.of(15, 25), { false }, { true })!!
        assertEquals("m2", n.step!!.key)
        // An hour and a half late, it no longer jumps the queue.
        assertEquals("body", TodayPick.next(day, LocalTime.of(17, 0), { false }, { true })!!.box.key)
    }

    @Test fun skipsDoneSteps() {
        val done = setOf("b1", "b2")
        val n = TodayPick.next(day, null, { it.key in done }, { it.key != "body" })!!
        assertEquals("piano", n.box.key); assertEquals("p1", n.step!!.key)
    }

    @Test fun nothingLeft() = assertNull(TodayPick.next(day, LocalTime.NOON, { true }, { false }))

    @Test fun restPutsBreaksLast() =
        assertEquals(listOf("body", "piano", "water", "move"), TodayPick.rest(day, { false }, { true }).map { it.box.key })

    @Test fun countsStepsAndPlainBoxes() {
        assertEquals(0 to 7, TodayPick.count(day, { false }, { false }))
        assertEquals(3 to 7, TodayPick.count(day, { it.key == "p1" }, { it.key == "body" }))
    }
}
