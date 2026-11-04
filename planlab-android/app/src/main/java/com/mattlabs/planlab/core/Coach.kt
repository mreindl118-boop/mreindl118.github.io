package com.mattlabs.planlab.core

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns today's plan + Health Connect into today's calls. Pure, so it is tested on fictional data.
 *
 * Medical line (projects/planlab/CLAUDE.md): body calls never overrule the surgeon or PT. Anything after surgery
 * says "if cleared". The coach only ever makes the plan easier than written, never harder, until the cleared phase.
 */
object Coach {

    data class Readiness(val score: Int, val label: String, val reasons: List<String>)
    data class Call(val title: String, val detail: String, val easier: Boolean = false)
    data class Nudge(val title: String, val detail: String, val warn: Boolean = false)

    data class Brief(
        val date: LocalDate,
        val phase: Phase,
        val daysToBig: Long?,
        val bigName: String,
        val readiness: Readiness?,
        val headline: String,
        val body: Call,
        val piano: Call?,
        val voice: Call?,
        val protein: Int,
        val proteinEaten: Int?,
        val waterL: Double,
        val bedtime: String?,
        val steps: Call?,
        val nudges: List<Nudge>,
    )

    fun readiness(h: HealthData, date: LocalDate, phase: Phase, daysSinceBig: Long?): Readiness? {
        val sleep = h.on(date, Metric.SLEEP)
        val hrv = h.on(date, Metric.HRV) ?: h.latest(date, Metric.HRV, 1)
        val rhr = h.on(date, Metric.RHR) ?: h.latest(date, Metric.RHR, 1)
        if (sleep == null && hrv == null && rhr == null) return null
        var s = 70.0
        val why = mutableListOf<String>()
        if (sleep != null) {
            when {
                sleep >= 7.5 -> { s += 10; why += "Slept ${fmtH(sleep)}" }
                sleep >= 6.5 -> why += "Slept ${fmtH(sleep)}"
                sleep >= 5.5 -> { s -= 10; why += "Short sleep, ${fmtH(sleep)}" }
                else -> { s -= 20; why += "Very short sleep, ${fmtH(sleep)}" }
            }
            val base = h.baseline(date, Metric.SLEEP)
            if (base != null && sleep < base - 1) s -= 5
        }
        val hrvBase = h.baseline(date, Metric.HRV)
        if (hrv != null && hrvBase != null && hrvBase > 0) {
            val r = hrv / hrvBase
            when {
                r >= 1.1 -> { s += 10; why += "HRV above normal" }
                r >= 0.95 -> s += 3
                r >= 0.85 -> { s -= 7; why += "HRV a bit low" }
                else -> { s -= 15; why += "HRV well below normal" }
            }
        }
        val rhrBase = h.baseline(date, Metric.RHR)
        if (rhr != null && rhrBase != null) {
            val d = rhr - rhrBase
            when {
                d >= 7 -> { s -= 20; why += "Resting HR up ${d.roundToInt()}" }
                d >= 4 -> { s -= 10; why += "Resting HR up ${d.roundToInt()}" }
                d <= -3 -> { s += 5; why += "Resting HR low" }
            }
        }
        val yLoad = h.on(date.minusDays(1), Metric.ACTIVE_KCAL)
        val loadBase = h.baseline(date.minusDays(1), Metric.ACTIVE_KCAL)
        if (yLoad != null && loadBase != null && loadBase > 0 && yLoad > loadBase * 1.5) { s -= 5; why += "Big day yesterday" }
        if (phase == Phase.NWB && daysSinceBig != null && daysSinceBig <= 3) { s = min(s, 40.0); why.add(0, "Day $daysSinceBig after surgery") }
        val score = s.roundToInt().coerceIn(5, 100)
        val label = when {
            phase == Phase.NWB && daysSinceBig != null && daysSinceBig <= 3 -> "Healing"
            score >= 80 -> "Ready to push"
            score >= 65 -> "Good to go"
            score >= 50 -> "Take it steady"
            else -> "Recovery day"
        }
        return Readiness(score, label, why.take(3))
    }

    fun brief(plan: Plan, h: HealthData, date: LocalDate, now: LocalTime = LocalTime.NOON): Brief {
        val big = plan.bigDay(date)
        val toBig = big?.let { daysBetween(date, it.date) }
        val since = toBig?.let { -it }
        val phase = Phase.of(big?.date, date)
        val day = plan.day(date)
        val r = readiness(h, date, phase, since)
        val score = r?.score ?: 65
        val bodyPlan = day?.body?.takeIf { it.isNotBlank() } ?: ""

        val body: Call = when (phase) {
            Phase.PRE_OP -> when {
                bodyPlan.isEmpty() -> Call("Mobility, 15 min", "Hips, core and breathing. Nothing planned, so keep it easy.")
                score >= 65 -> Call(bodyPlan, "As planned.")
                score >= 50 -> Call(bodyPlan, "One set fewer per move today.", easier = true)
                else -> Call("Mobility, 15 min + easy walk", "Swapped from \"$bodyPlan\". Your body needs the rest more today.", easier = true)
            }
            Phase.NWB -> when {
                since != null && since <= 2 -> Call("Rest and elevate", "Foot above your heart. Breathing and pelvic floor holds only.", easier = true)
                score >= 50 -> Call("Seated upper body and core, 20 min", "If cleared. Foot up between sets.")
                else -> Call("Breathing and stretches, 10 min", "Seated. Pelvic floor holds count.", easier = true)
            }
            Phase.PROTECTED -> if (score >= 55) Call("PT exercises, then ${bodyPlan.ifEmpty { "upper body and core" }}", "Only what your PT has cleared.")
            else Call("PT exercises only", "Low recovery today. PT work is the priority.", easier = true)
            Phase.CLEARED -> when {
                score >= 80 -> Call(bodyPlan.ifEmpty { "Training day" }, "Good day to add a set or a little weight.")
                score >= 60 -> Call(bodyPlan.ifEmpty { "Training day" }, "As planned.")
                else -> Call("Easy 20 min + mobility", "Swapped from \"${bodyPlan.ifEmpty { "training" }}\".", easier = true)
            }
        }

        val sleep = h.on(date, Metric.SLEEP)
        val tired = sleep != null && sleep < 6.0
        val pianoMin = day?.plannedMinutes("piano") ?: 0
        val piano = if (pianoMin > 0) when {
            tired -> Call("Piano, ${max(10, pianoMin / 2 + 5)} min", "Review only today: slow four-bar sections you know.", easier = true)
            phase == Phase.NWB -> Call("Piano, $pianoMin min", "Plus 10 if you feel like it. Lots of seated time now.")
            else -> Call("Piano, $pianoMin min", "Slow four-bar sections, hands separately, then together.")
        } else null

        val voiceMin = day?.plannedMinutes("voice") ?: 0
        val resp = h.on(date, Metric.RESP); val respBase = h.baseline(date, Metric.RESP)
        val temp = h.latest(date, Metric.TEMP, 1)
        val rhr = h.latest(date, Metric.RHR, 1); val rhrBase = h.baseline(date, Metric.RHR)
        val runDown = (resp != null && respBase != null && resp > respBase + 2) || (temp != null && temp >= 37.6) ||
            (rhr != null && rhrBase != null && rhr - rhrBase >= 7)
        val voice = if (voiceMin > 0) {
            if (runDown) Call("Voice: rest it", "Signs you're run down. Listen and mark the lyrics instead.", easier = true)
            else Call("Voice, $voiceMin min", "Warm up gently first.")
        } else null

        val weight = h.latest(date, Metric.WEIGHT, 60)
        val healing = phase == Phase.NWB || phase == Phase.PROTECTED
        val protein = if (weight != null) roundTo5(min(240.0, weight * (if (healing) 2.0 else 1.8))) else plan.proteinTarget.takeIf { it > 0 } ?: 180
        val eaten = h.on(date, Metric.PROTEIN)?.roundToInt()
        val water = ((weight?.let { it * 0.035 } ?: 2.8).coerceIn(2.5, 4.0) * 10).roundToInt() / 10.0

        val bedBase = h.baseline(date, Metric.BEDTIME, 7)
        val bedtime = when {
            sleep != null && sleep < 6.5 && bedBase != null -> "Lights out by ${Metric.BEDTIME.format(bedBase - 0.5)}"
            sleep != null && sleep < 6.5 -> "Early night tonight"
            else -> null
        }

        val stepsToday = h.on(date, Metric.STEPS)
        val stepsBase = h.baseline(date, Metric.STEPS, 7)
        val steps: Call? = when (phase) {
            Phase.PRE_OP -> stepsToday?.let { st ->
                val goal = max(6000.0, stepsBase ?: 7000.0).roundToInt()
                if (now.hour >= 15 && st < goal * 0.5) Call("Walk 15 min", "${Metric.STEPS.format(st)} steps so far, usually ${Metric.STEPS.format(goal.toDouble())}.")
                else null
            }
            Phase.NWB -> Call("No step goal", "Only the trips you need, on the scooter or crutches.")
            Phase.PROTECTED -> Call("Walk only as PT allows", stepsToday?.let { "${Metric.STEPS.format(it)} steps today." } ?: "")
            Phase.CLEARED -> null
        }

        val nudges = mutableListOf<Nudge>()
        if (phase == Phase.NWB) {
            nudges += Nudge("Elevate 20 min", "Foot above your heart, three times today.")
            nudges += Nudge("Water and fiber", "Pain meds slow digestion. Aim for $water L.")
            nudges += Nudge("Call the surgeon if", "calf pain or swelling, fever, or a wound that's red or leaking.", warn = true)
        }
        if (phase == Phase.PRE_OP && toBig != null && toBig in 0..7) {
            val left = plan.setup.filter { !it.done }.size
            if (left > 0) nudges += Nudge("$left setup items left", "Before ${(big?.what ?: "the big day").replace(Regex("""\s*\(.*\)$"""), "").lowercase()}.")
        }
        if (runDown) nudges += Nudge("Run down?", "Breathing rate, temperature or resting HR is up. Go easy and sleep early.", warn = true)
        bedtime?.let { nudges += Nudge(it, "Last night was ${fmtH(sleep!!)}.") }

        val bigName = big?.what?.replace(Regex("""\s*\(.*\)$"""), "")?.lowercase() ?: ""
        val headline = when {
            phase == Phase.NWB && since != null -> "Day $since of recovery · ${body.title.lowercase()}"
            r != null -> "${r.label} · ${body.title}"
            else -> body.title
        }
        return Brief(date, phase, toBig, bigName, r, headline, body, piano, voice, protein, eaten, water, bedtime, steps, nudges)
    }

    fun fmtH(h: Double): String { val m = (h * 60).roundToInt(); return "${m / 60}h ${(m % 60).toString().padStart(2, '0')}m" }
    private fun roundTo5(v: Double): Int = ((v / 5).roundToInt() * 5)
}
