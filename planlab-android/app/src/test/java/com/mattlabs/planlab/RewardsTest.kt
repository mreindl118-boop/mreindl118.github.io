package com.mattlabs.planlab

import com.mattlabs.planlab.core.Earned
import com.mattlabs.planlab.core.LogDoc
import com.mattlabs.planlab.core.Md
import com.mattlabs.planlab.core.ProgressCalc
import com.mattlabs.planlab.core.Rewards
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RewardsTest {
    private val today = LocalDate.of(2030, 3, 5)
    private val plan = Md.plan(Fixtures.files(), LocalDate.of(2030, 3, 4), 0)

    private val md = """
# XP, levels and streaks

## XP

| Action | XP |
|---|---|
| Tick a step | 2 |
| Finish an area (every step under one box) | 6 |
| Finish the whole day | 20 |
| Log a "bugs me" | 3 |
| Fix a "bugs me" (all its steps) | 11 |
| A "bugs me" habit kept 7 days | 30 |
| Wish dated and done | 9 |

## Levels

| Level | Total XP | Name |
|---|---|---|
| 1 | 0 | Test start |
| 2 | 50 | Test two |
| 3 | 150 | Test three |

## Streak

- A day counts when at least one area is finished.
- Test days (Tue 03-17 to Thu 03-19) count automatically.

## Milestones

| Milestone | Date | XP |
|---|---|---|
| Test chair built | 2030-03-14 | 50 |
| Test walk | ~2030-03-30 | 100 |
| Test summer | ~July | 200 |
""".trimIndent()

    @Test fun readsTheRewardsFile() {
        val r = Rewards.parse(md, today)
        assertEquals(listOf(2, 6, 20, 3, 11, 30, 9), listOf(r.step, r.area, r.day, r.bugLogged, r.bugFixed, r.habit, r.wish))
        assertEquals(listOf("Test start", "Test two", "Test three"), r.levels.map { it.name })
        assertEquals(setOf(LocalDate.of(2030, 3, 17), LocalDate.of(2030, 3, 18), LocalDate.of(2030, 3, 19)), r.autoDays)
        assertEquals(3, r.milestones.size)
        assertEquals(LocalDate.of(2030, 3, 14), r.milestones[0].date)
        assertEquals(LocalDate.of(2030, 3, 30), r.milestones[1].date)
        assertEquals(LocalDate.of(2030, 7, 1), r.milestones[2].date)
        assertEquals("test-chair-built", r.milestones[0].key)
        // No file: the built-in numbers.
        assertEquals(1, Rewards.parse(null, today).step)
        assertEquals(10, Rewards.parse(null, today).levels.size)
    }

    @Test fun earnsFromStepsAreasAndBugs() {
        val r = Rewards()
        val log = LogDoc.apply(LogDoc.empty(), JSONArray()
            .put(LogDoc.tickOp("2030-03-05", "body-walk-to-the-test-corner", true))
            .put(LogDoc.tickOp("2030-03-05", "body-stretch-5-min", true))
            .put(LogDoc.addOp("ticks", "bug-2030-03-05", "n", 2))
            .put(LogDoc.tickOp("bugs", "12", true)), 1)
        val e = ProgressCalc.earned(plan, log, r).mapValues { it.value.first }
        // Mon: one piano step ticked in the Markdown, body ticked [x] with no steps.
        assertEquals(1 + (5 + 1), e.getValue("2030-03-04").xp)
        assertEquals(setOf("body"), e.getValue("2030-03-04").finished)
        // Tue: both body steps, so body is finished; two bugs logged.
        assertEquals(2 + 5 + 2 * 2, e.getValue("2030-03-05").xp)
        assertEquals(10, e.getValue("bugfix:12").xp)
        val p = ProgressCalc.progress(e, r, log, today)
        assertEquals(2, p.streak)
        assertEquals(e.values.sumOf { it.xp }, p.xp)
        assertEquals(1, p.level.n)
    }

    @Test fun oneMissedDayAWeekIsShielded() {
        val r = Rewards()
        fun day(vararg fin: String) = Earned(5, emptyMap(), fin.toSet())
        // Fri 03-01 to Tue 03-05 (2030); Sun 03-03 missed.
        val e = mapOf("2030-03-01" to day("body"), "2030-03-02" to day("body"), "2030-03-03" to day(), "2030-03-04" to day("body"), "2030-03-05" to day())
        val (n, ready, _) = ProgressCalc.streak(e, r, today)
        assertEquals(3, n) // Mon, Sat, Fri; Sun shielded; today not done yet doesn't break it
        assertTrue(ready) // this week's shield is unused
        // Two misses in one week end it.
        val e2 = mapOf("2030-03-04" to day("body"), "2030-03-05" to day("body"), "2030-03-02" to day("body"), "2030-03-01" to day(), "2030-02-28" to day(), "2030-02-27" to day("body"))
        assertEquals(3, ProgressCalc.streak(e2, r, today).first)
    }

    @Test fun habitsKeptSevenDays() {
        val e = (0 until 8).associate { LocalDate.of(2030, 3, 1).plusDays(it.toLong()).toString() to Earned(1, emptyMap(), emptySet(), setOf("habit-shoes-to-the-rack")) }
        assertEquals(1, ProgressCalc.habits(e))
        assertEquals("habit-shoes-to-the-rack", ProgressCalc.habitId("Habit: shoes to the rack"))
        assertEquals(null, ProgressCalc.habitId("shoes to the rack"))
    }

    @Test fun levelsCarryOnPastTheTable() {
        val r = Rewards()
        assertEquals(3, ProgressCalc.levelOf(350, r).first.n)
        assertEquals("Groove", ProgressCalc.levelOf(350, r).second?.name)
        val (lv, next) = ProgressCalc.levelOf(5000, r)
        assertEquals(10, lv.n)
        assertEquals(5500, next?.xp)
        assertEquals(11, ProgressCalc.levelOf(5600, r).first.n)
        assertFalse(Earned.fromJson(Earned(3, mapOf("Body" to 3), setOf("body"), setOf("habit-x")).toJson()).habits.isEmpty())
    }
}
