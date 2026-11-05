package com.mattlabs.planlab

import com.mattlabs.planlab.core.HealthData
import com.mattlabs.planlab.core.Metric
import java.time.LocalDate

/** Fictional plan files in the real formats. Never real data. */
object Fixtures {
    val WEEK = """
# Week 2030-W10 (Mon 03-04 to Sun 03-10)

**Phase:** pre-op, a test week
**Focus this week:** fill the freezer and practice.

## The week at a glance

- **With Partner:** Sat 03-09, a test dinner.
- **Cooking:** about 2 hours, see `meals/freezer.md`

## Mon 03-04
- Eat: oat bowl · tuna wrap · **cook lentil stew**, eat 1, freeze 4 (~160 g protein)
- Body: Pre-op A, 20 min
- Practice: piano 25 min · voice 10 min · guitar 5 min
- Coming up: test thing Tue
- [ ] meals  [x] body  [ ] piano  [ ] voice  [ ] stew frozen
### piano
- [ ] Warm-up: five-finger pattern, 3 min
- [x] G major scale, hands separate
- [ ] Test piece, bars 1-4, slow
### stretch
- [ ] Hamstring, 30 s each side

## Tue 03-05
- Eat: eggs · salad
- Body: long walk, 45 min
- Practice: piano 20 min
- Coming up: nothing
- [ ] meals  [ ] body  [ ] piano
  - [ ] body: walk to the test corner
  - [ ] body: stretch 5 min
""".trimIndent()

    val CAL = """
# Calendar

| Date | What | Who | Notes |
|---|---|---|---|
| Wed 03-06 | Test show | Partner | pick seats |
| Tue 03-12 | **Knee check (test)** | ride needed | |

## Work and friends

| Fri 03-08 | Not an event | | |
""".trimIndent()

    val INBOX = """
# Inbox

## Before the big day

- [ ] Buy a test chair · Thu 03-07
  - [ ] measure the test desk
- [x] Call someone · 2030-03-05
""".trimIndent()

    val PROFILE = "- **Protein target: about 170 g a day.** Assumption."

    val RECOVERY = """
# Recovery

| Phase | Dates | Away | Opens |
|---|---|---|---|
| Pre-op | now | nothing | all |

## Set up before

- [ ] Shower chair
- [x] Ride arranged
""".trimIndent()

    fun files() = mapOf(
        "weeks/2030-W10.md" to WEEK, "social/calendar.md" to CAL, "inbox.md" to INBOX,
        "me/profile.md" to PROFILE, "me/recovery.md" to RECOVERY,
    )

    /** 14 steady days, then [today] with the given values. */
    fun health(today: LocalDate, sleep: Double, hrv: Double, rhr: Double, weight: Double? = 80.0): HealthData {
        val days = mutableMapOf<LocalDate, Map<Metric, Double>>()
        for (i in 1..14) days[today.minusDays(i.toLong())] = mapOf(Metric.SLEEP to 7.4, Metric.HRV to 50.0, Metric.RHR to 58.0, Metric.STEPS to 8000.0, Metric.ACTIVE_KCAL to 400.0, Metric.BEDTIME to 23.0) +
            (weight?.let { mapOf(Metric.WEIGHT to it) } ?: emptyMap())
        days[today] = mapOf(Metric.SLEEP to sleep, Metric.HRV to hrv, Metric.RHR to rhr, Metric.STEPS to 1500.0)
        return HealthData(days, 1, true, 24)
    }
}
