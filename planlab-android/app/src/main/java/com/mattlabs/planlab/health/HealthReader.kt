package com.mattlabs.planlab.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.WheelchairPushesRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.mattlabs.planlab.core.HealthData
import com.mattlabs.planlab.core.Metric
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/** Reads every type planLAB knows from Health Connect, 30 days back, into one value per day per metric. */
class HealthReader(private val ctx: Context) {

    companion object {
        const val BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
        const val PROVIDER = "com.google.android.apps.healthdata"

        val TYPES: List<KClass<out Record>> = listOf(
            StepsRecord::class, ActiveCaloriesBurnedRecord::class, TotalCaloriesBurnedRecord::class, DistanceRecord::class,
            FloorsClimbedRecord::class, ElevationGainedRecord::class, ExerciseSessionRecord::class, WheelchairPushesRecord::class,
            SleepSessionRecord::class, HeartRateRecord::class, RestingHeartRateRecord::class, HeartRateVariabilityRmssdRecord::class,
            OxygenSaturationRecord::class, RespiratoryRateRecord::class, BloodPressureRecord::class, Vo2MaxRecord::class,
            WeightRecord::class, BodyFatRecord::class, LeanBodyMassRecord::class, BasalMetabolicRateRecord::class,
            BodyTemperatureRecord::class, BloodGlucoseRecord::class, HydrationRecord::class, NutritionRecord::class,
        )
        val READ: Set<String> = TYPES.map { HealthPermission.getReadPermission(it) }.toSet()
        /** Writing nutrition lets the food log show up in Fitbit and anything else reading Health Connect. */
        val ALL: Set<String> = READ + BACKGROUND + FoodWriter.WRITE

        fun status(ctx: Context): Int = HealthConnectClient.getSdkStatus(ctx)
        fun available(ctx: Context): Boolean = status(ctx) == HealthConnectClient.SDK_AVAILABLE
    }

    private val zone = ZoneId.systemDefault()
    private val days = mutableMapOf<LocalDate, MutableMap<Metric, Double>>()
    private val many = mutableMapOf<Pair<LocalDate, Metric>, MutableList<Double>>()

    private fun put(d: LocalDate, m: Metric, v: Double) { days.getOrPut(d) { mutableMapOf() }[m] = v }
    private fun add(d: LocalDate, m: Metric, v: Double) { many.getOrPut(d to m) { mutableListOf() } += v }
    private fun date(i: Instant): LocalDate = LocalDateTime.ofInstant(i, zone).toLocalDate()

    suspend fun read(today: LocalDate): HealthData {
        if (!available(ctx)) return HealthData(emptyMap(), System.currentTimeMillis(), false, 0)
        val client = HealthConnectClient.getOrCreate(ctx)
        val granted = runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        val ok = { t: KClass<out Record> -> HealthPermission.getReadPermission(t) in granted }
        val n = TYPES.count(ok)
        if (n == 0) return HealthData(emptyMap(), System.currentTimeMillis(), false, 0)

        val start = today.minusDays(29).atStartOfDay()
        val end = LocalDateTime.now()
        val from = start.atZone(zone).toInstant(); val to = end.atZone(zone).toInstant()

        suspend fun <T : Any> daily(type: KClass<out Record>, metric: AggregateMetric<T>, m: Metric, conv: (T) -> Double) {
            if (!ok(type)) return
            runCatching {
                val res = client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(metric), TimeRangeFilter.between(start, end), Period.ofDays(1)))
                for (g in res) { val v = g.result[metric] ?: continue; put(g.startTime.toLocalDate(), m, conv(v)) }
            }
        }

        suspend fun <T : Record> records(type: KClass<T>): List<T> {
            if (!ok(type)) return emptyList()
            return runCatching {
                val out = mutableListOf<T>(); var page: String? = null
                do {
                    val r = client.readRecords(ReadRecordsRequest(recordType = type, timeRangeFilter = TimeRangeFilter.between(from, to), pageToken = page))
                    out += r.records; page = r.pageToken
                } while (page != null && out.size < 20000)
                out.toList()
            }.getOrDefault(emptyList())
        }

        daily(StepsRecord::class, StepsRecord.COUNT_TOTAL, Metric.STEPS) { it.toDouble() }
        daily(ActiveCaloriesBurnedRecord::class, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, Metric.ACTIVE_KCAL) { it.inKilocalories }
        daily(TotalCaloriesBurnedRecord::class, TotalCaloriesBurnedRecord.ENERGY_TOTAL, Metric.TOTAL_KCAL) { it.inKilocalories }
        daily(DistanceRecord::class, DistanceRecord.DISTANCE_TOTAL, Metric.DISTANCE) { it.inKilometers }
        daily(FloorsClimbedRecord::class, FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL, Metric.FLOORS) { it }
        daily(ElevationGainedRecord::class, ElevationGainedRecord.ELEVATION_GAINED_TOTAL, Metric.ELEVATION) { it.inMeters }
        daily(ExerciseSessionRecord::class, ExerciseSessionRecord.EXERCISE_DURATION_TOTAL, Metric.EXERCISE) { it.toMinutes().toDouble() }
        daily(WheelchairPushesRecord::class, WheelchairPushesRecord.COUNT_TOTAL, Metric.WHEELCHAIR) { it.toDouble() }
        daily(HeartRateRecord::class, HeartRateRecord.BPM_AVG, Metric.HR_AVG) { it.toDouble() }
        daily(HeartRateRecord::class, HeartRateRecord.BPM_MIN, Metric.HR_MIN) { it.toDouble() }
        daily(HeartRateRecord::class, HeartRateRecord.BPM_MAX, Metric.HR_MAX) { it.toDouble() }
        daily(RestingHeartRateRecord::class, RestingHeartRateRecord.BPM_AVG, Metric.RHR) { it.toDouble() }
        daily(WeightRecord::class, WeightRecord.WEIGHT_AVG, Metric.WEIGHT) { it.inKilograms }
        daily(BasalMetabolicRateRecord::class, BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL, Metric.BMR) { it.inKilocalories }
        daily(HydrationRecord::class, HydrationRecord.VOLUME_TOTAL, Metric.HYDRATION) { it.inLiters }
        daily(NutritionRecord::class, NutritionRecord.PROTEIN_TOTAL, Metric.PROTEIN) { it.inGrams }
        daily(NutritionRecord::class, NutritionRecord.ENERGY_TOTAL, Metric.KCAL_IN) { it.inKilocalories }
        daily(NutritionRecord::class, NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL, Metric.CARBS) { it.inGrams }
        daily(NutritionRecord::class, NutritionRecord.TOTAL_FAT_TOTAL, Metric.FAT) { it.inGrams }

        for (r in records(HeartRateVariabilityRmssdRecord::class)) add(date(r.time), Metric.HRV, r.heartRateVariabilityMillis)
        for (r in records(OxygenSaturationRecord::class)) add(date(r.time), Metric.SPO2, r.percentage.value)
        for (r in records(RespiratoryRateRecord::class)) add(date(r.time), Metric.RESP, r.rate)
        for (r in records(BodyTemperatureRecord::class)) add(date(r.time), Metric.TEMP, r.temperature.inCelsius)
        for (r in records(Vo2MaxRecord::class)) add(date(r.time), Metric.VO2, r.vo2MillilitersPerMinuteKilogram)
        for (r in records(BodyFatRecord::class)) add(date(r.time), Metric.BODY_FAT, r.percentage.value)
        for (r in records(LeanBodyMassRecord::class)) add(date(r.time), Metric.LEAN, r.mass.inKilograms)
        for (r in records(BloodGlucoseRecord::class)) add(date(r.time), Metric.GLUCOSE, r.level.inMilligramsPerDeciliter)
        for (r in records(BloodPressureRecord::class)) {
            add(date(r.time), Metric.BP_SYS, r.systolic.inMillimetersOfMercury)
            add(date(r.time), Metric.BP_DIA, r.diastolic.inMillimetersOfMercury)
        }
        for ((k, v) in many) if (v.isNotEmpty()) put(k.first, k.second, v.average())

        // Sleep counts toward the day you wake up. Awake and out-of-bed stages don't count as sleep.
        val sleep = mutableMapOf<LocalDate, DoubleArray>() // total min, deep, rem, awake, bedtime (h), longest min
        for (s in records(SleepSessionRecord::class)) {
            val d = date(s.endTime)
            val total = java.time.Duration.between(s.startTime, s.endTime).toMinutes().toDouble()
            var awake = 0.0; var deep = 0.0; var rem = 0.0
            for (st in s.stages) {
                val m = java.time.Duration.between(st.startTime, st.endTime).toMinutes().toDouble()
                when (st.stage) {
                    SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED, 7 -> awake += m
                    SleepSessionRecord.STAGE_TYPE_DEEP -> deep += m
                    SleepSessionRecord.STAGE_TYPE_REM -> rem += m
                }
            }
            val a = sleep.getOrPut(d) { DoubleArray(6) }
            a[0] += total - awake; a[1] += deep; a[2] += rem; a[3] += awake
            if (total > a[5]) {
                a[5] = total
                val st = LocalDateTime.ofInstant(s.startTime, zone)
                var h = st.hour + st.minute / 60.0
                if (h < 12) h += 24 // after midnight sorts after 11 pm
                a[4] = h
            }
        }
        for ((d, a) in sleep) {
            if (a[0] <= 0) continue
            put(d, Metric.SLEEP, a[0] / 60.0)
            if (a[1] > 0) put(d, Metric.SLEEP_DEEP, a[1] / 60.0)
            if (a[2] > 0) put(d, Metric.SLEEP_REM, a[2] / 60.0)
            if (a[3] > 0) put(d, Metric.SLEEP_AWAKE, a[3])
            if (a[5] >= 180) put(d, Metric.BEDTIME, a[4])
        }

        return HealthData(days.mapValues { it.value.toMap() }, System.currentTimeMillis(), true, n)
    }
}
