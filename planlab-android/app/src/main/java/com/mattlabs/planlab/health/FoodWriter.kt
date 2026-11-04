package com.mattlabs.planlab.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import com.mattlabs.planlab.core.FoodEntry
import com.mattlabs.planlab.core.LogDoc
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Copies the food log (today and yesterday) into Health Connect as nutrition records, so Fitbit, Pixel Watch and
 * anything else reading Health Connect sees what Matt ate. Each entry's id is the record's client id, so an entry
 * deleted in planLAB (on the phone or the web page) is deleted there too. Only runs once Matt has allowed it.
 */
class FoodWriter(private val ctx: Context) {
    companion object {
        val WRITE: String = HealthPermission.getWritePermission(NutritionRecord::class)
        private val lock = Mutex()

        suspend fun allowed(ctx: Context): Boolean {
            if (!HealthReader.available(ctx)) return false
            return runCatching { WRITE in HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions() }.getOrDefault(false)
        }
    }

    suspend fun sync(log: JSONObject) = lock.withLock {
        if (!allowed(ctx)) return@withLock
        val client = HealthConnectClient.getOrCreate(ctx)
        val prefs = ctx.getSharedPreferences("planlab", Context.MODE_PRIVATE)
        val written = runCatching { JSONArray(prefs.getString("hc_food", "[]")) }.getOrDefault(JSONArray())
        val have = (0 until written.length()).map { written.getString(it) }.toMutableSet()
        val today = LocalDate.now()
        val days = listOf(today.minusDays(1), today)
        val want = days.flatMap { d -> LogDoc.food(log, d.toString()).map { d to it } }
        val add = want.filter { (_, e) -> e.id !in have }
        if (add.isNotEmpty()) {
            client.insertRecords(add.map { (d, e) -> record(d, e) })
            add.forEach { have += it.second.id }
        }
        // Deleted in planLAB: the log has it as null under one of these days.
        val gone = days.flatMap { d ->
            val o = log.optJSONObject("food")?.optJSONObject(d.toString()) ?: return@flatMap emptyList<String>()
            o.keys().asSequence().filter { o.isNull(it) && it in have }.toList()
        }
        if (gone.isNotEmpty()) {
            client.deleteRecords(NutritionRecord::class, emptyList(), gone)
            have -= gone.toSet()
        }
        // Keep the list short: ids from the last week are enough to avoid writing anything twice.
        val keep = have.toList().takeLast(300)
        prefs.edit().putString("hc_food", JSONArray(keep).toString()).apply()
    }

    private fun record(d: LocalDate, e: FoodEntry): NutritionRecord {
        val t = runCatching { LocalTime.parse(e.at) }.getOrDefault(LocalTime.NOON)
        val start = LocalDateTime.of(d, t).atZone(ZoneId.systemDefault())
        val end = start.plusMinutes(15)
        return NutritionRecord(
            startTime = start.toInstant(), startZoneOffset = start.offset,
            endTime = end.toInstant(), endZoneOffset = end.offset,
            metadata = Metadata.manualEntry(clientRecordId = e.id, clientRecordVersion = 1),
            energy = Energy.kilocalories(e.kcal.toDouble()),
            protein = Mass.grams(e.protein.toDouble()),
            totalCarbohydrate = Mass.grams(e.carbs.toDouble()),
            totalFat = Mass.grams(e.fat.toDouble()),
            name = e.name.take(100),
            mealType = when (e.meal) {
                "breakfast" -> MealType.MEAL_TYPE_BREAKFAST
                "lunch" -> MealType.MEAL_TYPE_LUNCH
                "dinner" -> MealType.MEAL_TYPE_DINNER
                "snack" -> MealType.MEAL_TYPE_SNACK
                else -> MealType.MEAL_TYPE_UNKNOWN
            },
        )
    }
}
