package com.mattlabs.planlab.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Matt's taps, in the same shape as projects/planlab/log/app.json (planLAB app README):
 * { v, updated, ticks: { list: { key: bool|number } }, mins: { date: { key: n } }, freezer: { key: delta },
 *   food: { date: { id: { at, text, name, qty, kcal, protein_g, carbs_g, fat_g, src, meal, plan } | null } } }.
 * A food entry set to null was deleted (kept as null so the web page's merge doesn't bring it back).
 *
 * The phone keeps what it last pulled (the base) plus the taps it hasn't pushed yet (ops). What it shows is
 * base + ops, and a push replays the same ops onto whatever is on GitHub at that moment, so taps from the web
 * page and from this app never overwrite each other.
 */
object LogDoc {
    fun empty(): JSONObject = JSONObject().put("v", 1).put("updated", 0).put("ticks", JSONObject()).put("mins", JSONObject()).put("freezer", JSONObject())

    fun tickOp(list: String, key: String, value: Boolean): JSONObject = JSONObject().put("t", "tick").put("l", list).put("k", key).put("v", value)
    fun addOp(root: String, list: String, key: String, add: Int): JSONObject = JSONObject().put("t", "add").put("r", root).put("l", list).put("k", key).put("n", add)
    fun foodOp(date: String, id: String, entry: JSONObject?): JSONObject = JSONObject().put("t", "food").put("d", date).put("k", id).put("e", entry ?: JSONObject.NULL)

    fun parse(s: String?): JSONObject = runCatching { JSONObject(s ?: "") }.getOrNull()?.also { d ->
        for (k in listOf("ticks", "mins", "freezer")) if (d.optJSONObject(k) == null) d.put(k, JSONObject())
    } ?: empty()

    fun apply(base: JSONObject, ops: JSONArray, now: Long): JSONObject {
        val d = parse(base.toString())
        for (i in 0 until ops.length()) {
            val op = ops.optJSONObject(i) ?: continue
            when (op.optString("t")) {
                "tick" -> obj(d.getJSONObject("ticks"), op.getString("l")).put(op.getString("k"), op.getBoolean("v"))
                "add" -> {
                    val root = d.optJSONObject(op.getString("r")) ?: JSONObject().also { d.put(op.getString("r"), it) }
                    val list = obj(root, op.getString("l"))
                    val k = op.getString("k")
                    list.put(k, list.optInt(k, 0) + op.getInt("n"))
                }
                "food" -> obj(obj(d, "food"), op.getString("d")).put(op.getString("k"), op.opt("e") ?: JSONObject.NULL)
            }
        }
        if (ops.length() > 0) d.put("updated", now)
        return d
    }

    private fun obj(parent: JSONObject, key: String): JSONObject =
        parent.optJSONObject(key) ?: JSONObject().also { parent.put(key, it) }

    /** A tick from the log if it was ever set there, else the [x] in the Markdown. */
    fun ticked(doc: JSONObject, list: String, key: String, fallback: Boolean): Boolean {
        val l = doc.optJSONObject("ticks")?.optJSONObject(list) ?: return fallback
        return if (l.has(key) && l.opt(key) is Boolean) l.getBoolean(key) else fallback
    }

    /** The tick if the log has one, else null. */
    fun tick(doc: JSONObject, list: String, key: String): Boolean? {
        val l = doc.optJSONObject("ticks")?.optJSONObject(list) ?: return null
        return if (l.has(key) && l.opt(key) is Boolean) l.getBoolean(key) else null
    }

    fun skipped(doc: JSONObject, date: String, key: String): Boolean = ticked(doc, "skip-$date", key, false)
    fun minutes(doc: JSONObject, date: String, key: String): Int = doc.optJSONObject("mins")?.optJSONObject(date)?.optInt(key, 0) ?: 0
    /** The day's food entries, oldest first (deleted ones left out). */
    fun food(doc: JSONObject, date: String): List<FoodEntry> {
        val d = doc.optJSONObject("food")?.optJSONObject(date) ?: return emptyList()
        return d.keys().asSequence().mapNotNull { k -> d.optJSONObject(k)?.let { FoodEntry.of(k, it) } }.sortedBy { it.at + it.id }.toList()
    }

    fun pelvic(doc: JSONObject, date: String): Int = doc.optJSONObject("ticks")?.optJSONObject("pf-$date")?.optInt("n", 0) ?: 0
}
