package com.mattlabs.planlab

import com.mattlabs.planlab.core.Box
import com.mattlabs.planlab.core.Link
import com.mattlabs.planlab.core.Md
import com.mattlabs.planlab.core.Moves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class MovesTest {
    private val md = """
# Moves

## Test bug

- **Do:** Lie on your back and move slowly.
- **Cues:** Back flat · breathe out as you lower · slow, 3 s down
- **Dose:** 3 × 10 per side
- **Safe:** Test safety note.
- **Demo:** [Video](https://example.com/test-bug)

## Test bug, arms only

- **Do:** Arms only.
- **Dose:** 2 × 8
""".trimIndent()

    @Test fun readsTheMovesFile() {
        val m = Moves.parse(md)
        assertEquals(listOf("test-bug", "test-bug-arms-only"), m.keys.toList())
        val bug = m.getValue("test-bug")
        assertEquals("3 × 10 per side", bug.field("Dose"))
        assertEquals(listOf("Back flat", "breathe out as you lower", "slow, 3 s down"), bug.cues)
        assertEquals(listOf(Link("Watch demo", "https://example.com/test-bug")), bug.links)
        assertNull(bug.field("Demo"))
    }

    @Test fun findsTheMoveForAStep() {
        val m = Moves.parse(md)
        assertEquals("test-bug-arms-only", Moves.find(m, "Test bug, arms only, 2 × 8")?.key)
        assertEquals("test-bug", Moves.find(m, "Test bug, 3 × 10")?.key)
        assertEquals("test-bug-arms-only", Moves.find(m, "something", listOf(Link("How to", "app:move/test-bug-arms-only")))?.key)
        assertEquals("test-bug", Moves.find(m, "Break 1 at 10:30, 1 min: test bug, 5 reps")?.key)
    }

    @Test fun breaksAndSessionsGetDifferentBadges() {
        assertEquals(LocalTime.of(10, 30), Moves.timeOf("Break 1 at 10:30, 1 min: stand up"))
        assertEquals(LocalTime.of(15, 30), Moves.timeOf("Break 3 at 15:30, 1 min: walk"))
        assertEquals(LocalTime.of(15, 0), Moves.timeOf("at 3 pm"))
        val brk = Box("move", "move", false, listOf(Box("move/b1", "Break 1 at 10:30, 1 min: stand up", false)))
        assertEquals("Break · 1 min · 10:30 am", Moves.kindOf(brk, brk.steps[0], null)?.badge)
        val plan = Md.plan(Fixtures.files(), java.time.LocalDate.of(2030, 3, 4), 0)
        val mon = plan.weeks.single().days[0]
        assertEquals("Session · 20 min", Moves.kindOf(mon.boxes.first { it.key == "body" }, null, mon)?.badge)
    }
}
