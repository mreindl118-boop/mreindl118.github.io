package com.mattlabs.planlab.core

import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

/** Every metric the app can read from Health Connect, one value per day. */
enum class Metric(val id: String, val label: String, val unit: String, val group: String, val decimals: Int = 0, val higherIsBetter: Boolean? = null) {
    SLEEP("sleep_h", "Sleep", "h", "Sleep", 1, true),
    SLEEP_DEEP("sleep_deep_h", "Deep sleep", "h", "Sleep", 1, true),
    SLEEP_REM("sleep_rem_h", "REM sleep", "h", "Sleep", 1, true),
    SLEEP_AWAKE("sleep_awake_min", "Awake in bed", "min", "Sleep", 0, false),
    BEDTIME("bedtime_h", "Bedtime", "", "Sleep", 1),
    RHR("rhr", "Resting heart rate", "bpm", "Heart", 0, false),
    HRV("hrv", "HRV", "ms", "Heart", 0, true),
    HR_AVG("hr_avg", "Average heart rate", "bpm", "Heart"),
    HR_MIN("hr_min", "Lowest heart rate", "bpm", "Heart"),
    HR_MAX("hr_max", "Highest heart rate", "bpm", "Heart"),
    SPO2("spo2", "Blood oxygen", "%", "Heart", 0, true),
    RESP("resp_rate", "Breathing rate", "/min", "Heart", 1),
    BP_SYS("bp_sys", "Blood pressure (top)", "mmHg", "Heart"),
    BP_DIA("bp_dia", "Blood pressure (bottom)", "mmHg", "Heart"),
    VO2("vo2max", "VO2 max", "", "Heart", 1, true),
    STEPS("steps", "Steps", "", "Move", 0, true),
    ACTIVE_KCAL("active_kcal", "Active calories", "kcal", "Move", 0, true),
    TOTAL_KCAL("total_kcal", "Calories burned", "kcal", "Move"),
    DISTANCE("distance_km", "Distance", "km", "Move", 1, true),
    FLOORS("floors", "Floors", "", "Move"),
    ELEVATION("elevation_m", "Elevation", "m", "Move"),
    EXERCISE("exercise_min", "Workout time", "min", "Move", 0, true),
    WHEELCHAIR("wheelchair_pushes", "Wheelchair pushes", "", "Move"),
    WEIGHT("weight_kg", "Weight", "kg", "Body", 1),
    BODY_FAT("body_fat_pct", "Body fat", "%", "Body", 1),
    LEAN("lean_kg", "Lean mass", "kg", "Body", 1),
    BMR("bmr_kcal", "Resting burn", "kcal", "Body"),
    TEMP("temp_c", "Body temperature", "°C", "Body", 1),
    GLUCOSE("glucose", "Blood sugar", "mg/dL", "Body"),
    HYDRATION("hydration_l", "Water", "L", "Food", 1, true),
    PROTEIN("protein_g", "Protein", "g", "Food", 0, true),
    KCAL_IN("kcal_in", "Calories eaten", "kcal", "Food"),
    CARBS("carbs_g", "Carbs", "g", "Food"),
    FAT("fat_g", "Fat", "g", "Food");

    fun format(v: Double?): String {
        if (v == null) return "–"
        if (this == BEDTIME) { val h = ((v % 24) + 24) % 24; val hh = h.toInt(); val mm = ((h - hh) * 60).roundToInt(); val ap = if (hh >= 12) "pm" else "am"; return "${if (hh % 12 == 0) 12 else hh % 12}:${mm.toString().padStart(2, '0')} $ap" }
        if (this == STEPS || this == ACTIVE_KCAL || this == TOTAL_KCAL || this == KCAL_IN || this == BMR) return "%,d".format(v.roundToInt())
        return if (decimals == 0) v.roundToInt().toString() else "%.${decimals}f".format(v)
    }

    companion object { fun byId(id: String) = entries.firstOrNull { it.id == id } }
}

/** Days oldest first. A missing value means no data that day. */
data class HealthData(val days: Map<LocalDate, Map<Metric, Double>>, val takenAt: Long, val connected: Boolean, val granted: Int) {
    fun on(date: LocalDate, m: Metric): Double? = days[date]?.get(m)

    /** The latest value at or before [date], within [lookback] days. */
    fun latest(date: LocalDate, m: Metric, lookback: Long = 14): Double? {
        for (i in 0..lookback) days[date.minusDays(i)]?.get(m)?.let { return it }
        return null
    }

    /** Mean over the [n] days before [date] (not counting it). */
    fun baseline(date: LocalDate, m: Metric, n: Long = 14): Double? {
        val v = (1..n).mapNotNull { days[date.minusDays(it)]?.get(m) }
        return if (v.size >= 3) v.average() else null
    }

    fun series(date: LocalDate, m: Metric, n: Int = 14): List<Double?> = (n - 1 downTo 0).map { days[date.minusDays(it.toLong())]?.get(m) }

    fun metricsWithData(): List<Metric> = Metric.entries.filter { m -> days.values.any { it.containsKey(m) } }

    fun toJson(): JSONObject {
        val d = JSONObject()
        for ((date, vals) in days.toSortedMap()) {
            if (vals.isEmpty()) continue
            val o = JSONObject()
            for ((m, v) in vals) o.put(m.id, (v * 100).roundToInt() / 100.0)
            d.put(date.toString(), o)
        }
        return JSONObject().put("v", 1).put("takenAt", takenAt).put("connected", connected).put("granted", granted).put("days", d)
    }

    companion object {
        val EMPTY = HealthData(emptyMap(), 0, false, 0)
        fun fromJson(s: String?): HealthData = runCatching {
            val o = JSONObject(s ?: "")
            val d = o.getJSONObject("days")
            val days = mutableMapOf<LocalDate, Map<Metric, Double>>()
            for (k in d.keys()) {
                val vals = d.getJSONObject(k)
                days[LocalDate.parse(k)] = vals.keys().asSequence().mapNotNull { id -> Metric.byId(id)?.let { it to vals.getDouble(id) } }.toMap()
            }
            HealthData(days, o.optLong("takenAt"), o.optBoolean("connected"), o.optInt("granted"))
        }.getOrDefault(EMPTY)
    }
}
