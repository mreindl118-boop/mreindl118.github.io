package com.mattlabs.planlab

import com.mattlabs.planlab.core.Md
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MdTest {
    private val today = LocalDate.of(2030, 3, 4)
    private val plan = Md.plan(Fixtures.files(), today, 0)

    @Test fun readsTheWeek() {
        val w = plan.weeks.single()
        assertEquals(LocalDate.of(2030, 3, 4), w.start)
        assertEquals("pre-op, a test week", w.phase)
        assertEquals(2, w.days.size)
        assertEquals(listOf("With Partner", "Cooking"), w.glance.map { it.first })
        val mon = w.days[0]
        assertEquals(LocalDate.of(2030, 3, 4), mon.date)
        assertEquals("Pre-op A, 20 min", mon.body)
        assertEquals(listOf("meals", "body", "piano", "voice", "stew-frozen"), mon.boxes.map { it.key })
        assertTrue(mon.boxes[1].done)
        assertEquals(25, mon.plannedMinutes("piano"))
        assertEquals(10, mon.plannedMinutes("voice"))
        assertEquals(160, mon.protein())
        assertEquals("cook lentil stew, eat 1, freeze 4 (~160 g protein)", mon.eat[2])
    }

    @Test fun readsTheCalendarAndTheBigDay() {
        assertEquals(2, plan.calendar.size)
        val big = plan.bigDay(today)!!
        assertEquals(LocalDate.of(2030, 3, 12), big.date)
        assertEquals("Knee check (test)", big.what)
        assertFalse(plan.calendar[0].big)
    }

    @Test fun readsInboxSetupAndProtein() {
        assertEquals(LocalDate.of(2030, 3, 7), plan.inbox[0].date)
        assertEquals("Buy a test chair", plan.inbox[0].what)
        assertTrue(plan.inbox[1].done)
        assertEquals(listOf("shower-chair", "ride-arranged"), plan.setup.map { it.key })
        assertEquals(170, plan.proteinTarget)
    }

    @Test fun keysMatchTheWebPage() {
        assertEquals("burritos-and-bites-frozen", Md.keyOf("burritos and bites frozen"))
        assertEquals("stew-frozen", Md.keyOf("**stew** frozen"))
    }
}
