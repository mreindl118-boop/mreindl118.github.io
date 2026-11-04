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
        assertEquals(listOf("body", "piano", "voice", "stew-frozen", "stretch"), mon.boxes.map { it.key })
        assertTrue(mon.boxes[0].done)
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

    @Test fun readsStepsInBothForms() {
        val mon = plan.weeks.single().days[0]
        val piano = mon.boxes.first { it.key == "piano" }
        assertEquals(listOf("piano/warm-up-five-finger-pattern-3-min", "piano/g-major-scale-hands-separate", "piano/test-piece-bars-1-4-slow"), piano.steps.map { it.key })
        assertEquals("Warm-up: five-finger pattern, 3 min", piano.steps[0].label)
        assertTrue(piano.steps[1].done)
        assertEquals("stretch", mon.boxes.last().label)
        assertEquals(1, mon.boxes.last().steps.size)
        val tue = plan.weeks.single().days[1]
        assertEquals(2, tue.boxes.size)
        val body = tue.boxes.first { it.key == "body" }
        assertEquals(listOf("body-walk-to-the-test-corner", "body-stretch-5-min"), body.steps.map { it.key })
        assertEquals("stretch 5 min", body.steps[1].label)
        assertEquals(listOf("measure-the-test-desk"), plan.inbox[0].steps.map { it.key })
        assertEquals(2, plan.inbox.size)
    }

    @Test fun keysMatchTheWebPage() {
        assertEquals("burritos-and-bites-frozen", Md.keyOf("burritos and bites frozen"))
        assertEquals("stew-frozen", Md.keyOf("**stew** frozen"))
    }
}
