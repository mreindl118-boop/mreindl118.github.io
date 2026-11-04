package com.mattlabs.planlab

import com.mattlabs.planlab.core.Coach
import com.mattlabs.planlab.core.HealthData
import com.mattlabs.planlab.core.Md
import com.mattlabs.planlab.core.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CoachTest {
    private val mon = LocalDate.of(2030, 3, 4)
    private val plan = Md.plan(Fixtures.files(), mon, 0)

    @Test fun goodNightKeepsThePlan() {
        val b = Coach.brief(plan, Fixtures.health(mon, sleep = 8.0, hrv = 56.0, rhr = 56.0), mon)
        assertEquals(Phase.PRE_OP, b.phase)
        assertTrue(b.readiness!!.score >= 80)
        assertEquals("Pre-op A, 20 min", b.body.title)
        assertFalse(b.body.easier)
        assertEquals("Piano, 25 min", b.piano!!.title)
        assertEquals(8L, b.daysToBig)
        assertEquals(145, b.protein) // 80 kg x 1.8, to the nearest 5
    }

    @Test fun badNightEasesTheWorkoutAndPractice() {
        val b = Coach.brief(plan, Fixtures.health(mon, sleep = 4.8, hrv = 38.0, rhr = 66.0), mon)
        assertTrue(b.readiness!!.score < 50)
        assertTrue(b.body.easier)
        assertTrue(b.body.title.startsWith("Mobility"))
        assertTrue(b.piano!!.easier)
        assertTrue(b.voice!!.easier) // resting HR up 8: rest the voice
        assertTrue(b.nudges.any { it.warn })
        assertTrue(b.bedtime!!.startsWith("Lights out by 10:30"))
    }

    @Test fun noHealthDataStillPlans() {
        val b = Coach.brief(plan, HealthData.EMPTY, mon)
        assertNull(b.readiness)
        assertEquals("Pre-op A, 20 min", b.body.title)
        assertEquals(170, b.protein)
    }

    @Test fun afterSurgeryRestThenSeated() {
        val day1 = LocalDate.of(2030, 3, 13)
        val b1 = Coach.brief(plan, Fixtures.health(day1, 8.0, 55.0, 57.0), day1)
        assertEquals(Phase.NWB, b1.phase)
        assertEquals("Rest and elevate", b1.body.title)
        assertEquals("Healing", b1.readiness!!.label)
        assertEquals(160, b1.protein) // healing: 2 g per kg
        assertTrue(b1.nudges.any { it.title.startsWith("Elevate") })
        val day6 = LocalDate.of(2030, 3, 18)
        val b6 = Coach.brief(plan, Fixtures.health(day6, 8.0, 55.0, 57.0), day6)
        assertTrue(b6.body.title.startsWith("Seated"))
        assertEquals("No step goal", b6.steps!!.title)
    }

    @Test fun lowStepsInTheAfternoonAddsAWalk() {
        val b = Coach.brief(plan, Fixtures.health(mon, 7.5, 50.0, 58.0), mon, LocalTime.of(16, 0))
        assertEquals("Walk 15 min", b.steps!!.title)
    }

    @Test fun phases() {
        val big = LocalDate.of(2030, 3, 12)
        assertEquals(Phase.PRE_OP, Phase.of(big, big.minusDays(1)))
        assertEquals(Phase.NWB, Phase.of(big, big))
        assertEquals(Phase.NWB, Phase.of(big, big.plusDays(14)))
        assertEquals(Phase.PROTECTED, Phase.of(big, big.plusDays(15)))
        assertEquals(Phase.CLEARED, Phase.of(big, big.plusDays(91)))
    }
}
