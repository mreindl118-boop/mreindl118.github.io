package com.mattlabs.planlab.core

import org.json.JSONObject
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.roundToInt

// planLAB food lookup: "skyline 3-way, 2 coneys" -> foods with calories and macros, instantly, with no AI.
// Same rules as planlab/food.js on the site; keep the two in step (FoodTest.kt and planlab/test/food.test.cjs
// check the same cases). Sources, best first: Matt's own foods (meals/foods.md), the built-in table
// (planlab/data/foods.json, bundled and refreshed from the site), then Open Food Facts.

data class Food(
    val brand: String, val name: String, val serving: String,
    val kcal: Double, val p: Double, val c: Double, val f: Double,
    val src: String, val keys: List<List<String>>,
) {
    val title: String get() = if (brand.isEmpty()) name else "$brand $name"
}

data class FoodRow(val text: String, val brand: String, val qty: Double, val unit: String, val food: Food?, val alts: List<Food>, val scale: Double) {
    fun with(f: Food, k: Double = scale): FoodRow = copy(food = f, scale = k)
}

/** One thing eaten, as stored in log/app.json under food[date][id]. */
data class FoodEntry(
    val id: String, val at: String, val text: String, val name: String, val qty: Double,
    val kcal: Int, val protein: Int, val carbs: Int, val fat: Int,
    val src: String, val meal: String, val plan: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("at", at).put("text", text).put("name", name).put("qty", qty)
        .put("kcal", kcal).put("protein_g", protein).put("carbs_g", carbs).put("fat_g", fat).put("src", src).put("meal", meal).put("plan", plan)

    companion object {
        fun of(id: String, o: JSONObject): FoodEntry = FoodEntry(id, o.optString("at"), o.optString("text"), o.optString("name"), o.optDouble("qty", 1.0),
            o.optInt("kcal"), o.optInt("protein_g"), o.optInt("carbs_g"), o.optInt("fat_g"), o.optString("src"), o.optString("meal"), o.optString("plan"))
    }
}

data class Macros(val kcal: Int = 0, val protein: Int = 0, val carbs: Int = 0, val fat: Int = 0) {
    operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat)
}

data class Planned(val meal: String, val name: String, val m: Macros, val src: String)

class FoodDb(val brands: List<Pair<String, List<String>>>, val foods: List<Food>) {
    companion object {
        val EMPTY = FoodDb(emptyList(), emptyList())
        fun parse(json: String?): FoodDb = runCatching {
            val o = JSONObject(json ?: "")
            val brands = mutableListOf<Pair<String, List<String>>>()
            o.optJSONObject("brands")?.let { b -> for (k in b.keys()) { val a = b.getJSONArray(k); (listOf(k) + (0 until a.length()).map { a.getString(it) }).forEach { brands += k to Foods.toks(it) } } }
            val fa = o.getJSONArray("foods")
            val foods = (0 until fa.length()).map { i ->
                val r = fa.getJSONArray(i)
                Foods.food(r.getString(0), r.getString(1), r.getString(2), r.getDouble(3), r.getDouble(4), r.getDouble(5), r.getDouble(6), r.optString(7), if (r.getString(0).isEmpty()) "table" else "chain")
            }
            FoodDb(brands.sortedByDescending { it.second.size }, foods)
        }.getOrDefault(EMPTY)
    }
}

object Foods {
    private val STOP = setOf("a", "an", "the", "of", "with", "from", "at", "some", "my", "i", "had", "ate", "got", "for", "on", "in", "and", "plus", "order", "today", "lunch", "dinner", "breakfast", "snack")
    private val WORDN = mapOf("a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0,
        "nine" to 9.0, "ten" to 10.0, "twelve" to 12.0, "half" to 0.5, "couple" to 2.0, "few" to 3.0)
    private val UNITS = mapOf("oz" to "oz", "ounce" to "oz", "ounces" to "oz", "g" to "g", "gram" to "g", "grams" to "g", "cup" to "cup", "cups" to "cup",
        "slice" to "slice", "slices" to "slice", "piece" to "piece", "pieces" to "piece", "pc" to "piece", "scoop" to "scoop", "scoops" to "scoop",
        "tbsp" to "tbsp", "tsp" to "tsp", "bowl" to "bowl", "bowls" to "bowl")

    fun norm(s: String): String = s.lowercase().replace(Regex("[’']"), "").replace("&", " and ")
        .replace(Regex("""\b(one|two|three|four|five)[\s-]+ways?\b""")) { "${WORDN[it.groupValues[1]]!!.toInt()}way" }
        .replace(Regex("""\b(\d)[\s-]*ways?\b"""), "$1way").replace(Regex("""[^a-z0-9./]+"""), " ").trim()

    private fun sing(t: String): String = when {
        t.length <= 3 || t.any { it.isDigit() } -> t
        t.endsWith("ies") -> t.dropLast(3) + "y"
        Regex("(ss|us|is)$").containsMatchIn(t) -> t
        Regex("(ch|sh|x)es$").containsMatchIn(t) -> t.dropLast(2)
        t.endsWith("s") -> t.dropLast(1)
        else -> t
    }

    fun toks(s: String): List<String> = norm(s).split(" ").filter { it.isNotEmpty() && it !in STOP }.map(::sing)

    fun food(brand: String, name: String, serving: String, kcal: Double, p: Double, c: Double, f: Double, aliases: String, src: String): Food {
        val names = listOf(name) + aliases.split("|").filter { it.isNotBlank() }
        return Food(brand, name, serving.ifBlank { "1 serving" }, kcal, p, c, f, src, names.map(::toks).filter { it.isNotEmpty() })
    }

    /** Matt's own foods: the table in meals/foods.md, | Food | Serving | kcal | Protein | Carbs | Fat | Also called |. */
    fun mine(md: String?): List<Food> = (md ?: "").lines().mapNotNull { line ->
        val t = line.trim()
        if (!t.startsWith("|") || Regex("""^\|[\s\-|:]+\|$""").matches(t)) return@mapNotNull null
        val c = t.removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
        if (c.size < 6 || c[2].none { it.isDigit() }) return@mapNotNull null
        fun n(x: String) = x.replace(Regex("[^\\d.]"), "").toDoubleOrNull() ?: 0.0
        val brand = Regex("""^(.+?):\s""").find(c[0])?.groupValues?.get(1) ?: ""
        food(brand, if (brand.isNotEmpty()) c[0].substring(brand.length + 1).trim() else c[0], c[1], n(c[2]), n(c[3]), n(c[4]), n(c[5]), c.getOrElse(6) { "" }.replace(Regex(""",\s*"""), "|"), "mine")
    }

    private data class Hit(val brand: String, val at: Int, val len: Int)

    private fun brandsIn(t: List<String>, db: FoodDb): List<Hit> = db.brands.mapNotNull { (b, bt) ->
        if (bt.isEmpty()) return@mapNotNull null
        (0..t.size - bt.size).firstOrNull { i -> bt.indices.all { t[i + it] == bt[it] } }?.let { Hit(b, it, bt.size) }
    }.distinctBy { it.brand }

    private fun frac(s: String): Double = if ('/' in s) s.split('/').let { (it[0].toDoubleOrNull() ?: 1.0) / (it[1].toDoubleOrNull() ?: 1.0) } else s.toDoubleOrNull() ?: 1.0

    data class Qty(val qty: Double, val unit: String, val rest: String)

    fun qtyOf(chunk: String): Qty {
        var s = norm(chunk); var qty = 1.0; var unit = ""
        var m = Regex("""^(\d+(?:\.\d+)?|\d+/\d+)\s*x?\s+(?!way\b)(.*)$""").find(s)
        if (m != null) { qty = frac(m.groupValues[1]); s = m.groupValues[2] }
        else {
            m = Regex("""^(a couple|a few|half a|a half|one|two|three|four|five|six|seven|eight|nine|ten|twelve|half|couple|a|an)\s+(?:of\s+)?(.*)$""").find(s)
            if (m != null) {
                val w = m.groupValues[1].replace(Regex("^a (couple|few)$"), "$1").replace(Regex("^(half a|a half)$"), "half")
                qty = WORDN[w] ?: 1.0; s = m.groupValues[2]
            }
        }
        m = Regex("""^(.*?)\s+x\s?(\d+)$""").find(s) ?: Regex("""^(.*?)\s+(\d+)x$""").find(s)
        if (m != null) { qty *= m.groupValues[2].toDouble(); s = m.groupValues[1] }
        m = Regex("""^([a-z]+)\s+(?:of\s+)?(.*)$""").find(s)
        if (m != null && UNITS[m.groupValues[1]] != null && m.groupValues[2].isNotEmpty()) { unit = UNITS[m.groupValues[1]]!!; s = m.groupValues[2] }
        return Qty(qty, unit, s)
    }

    private fun score(q: List<String>, f: Food, brand: String): Double {
        var best = 0.0
        for (k in f.keys) {
            var hit = 0.0
            for (t in q) {
                if (t in k) hit += 1.0
                else if (t.length >= 4 && k.any { x -> x.length >= 4 && (x.startsWith(t) || t.startsWith(x)) }) hit += 0.7
            }
            if (hit == 0.0) continue
            val s = (hit / q.size) * 0.65 + (minOf(hit, k.size.toDouble()) / k.size) * 0.35
            if (s > best) best = s
        }
        if (best == 0.0) return 0.0
        if (brand.isNotEmpty()) best += if (f.brand == brand) 0.25 else if (f.brand.isNotEmpty()) -0.35 else -0.05
        else if (f.brand.isNotEmpty()) best -= 0.04
        if (f.src == "mine") best += 0.06
        return best
    }

    private fun servingScale(unit: String, qty: Double, serving: String): Double {
        if (unit.isEmpty()) return qty
        val m = Regex("""^(\d+(?:\.\d+)?|\d+/\d+)?\s*([a-z]+)""").find(norm(serving)) ?: return qty
        if (UNITS[m.groupValues[2]] != unit) return qty
        val amt = if (m.groupValues[1].isNotEmpty()) frac(m.groupValues[1]) else 1.0
        return qty / (if (amt == 0.0) 1.0 else amt)
    }

    fun split(text: String): List<String> = text.split(Regex("""[,;+\n]|\s+&\s+|\band\b(?!\s+cheese)""", RegexOption.IGNORE_CASE))
        .map { it.trim() }.filter { it.isNotEmpty() && toks(it).size + (if (it.any { c -> c.isDigit() }) 1 else 0) > 0 }

    /** Matt's own row replaces a table row with the same brand and name (and keeps its other names). */
    fun merged(db: FoodDb, mine: List<Food>): List<Food> {
        if (mine.isEmpty()) return db.foods
        fun id(f: Food) = "${f.brand}|${f.name}".lowercase()
        val left = LinkedHashMap(mine.associateBy(::id))
        val out = db.foods.map { f -> left.remove(id(f))?.let { m -> m.copy(keys = m.keys + f.keys) } ?: f }
        return left.values.toList() + out
    }

    /** One row per thing eaten, best match first plus a few alternatives. */
    fun lookup(text: String, db: FoodDb, mine: List<Food> = emptyList()): List<FoodRow> {
        val all = merged(db, mine)
        val mentioned = brandsIn(toks(text), db)
        var ctx = if (mentioned.size == 1) mentioned[0].brand else ""
        val rows = mutableListOf<FoodRow>()
        for (part in split(text)) {
            val (qty, unit, rest) = qtyOf(part)
            var q = toks(rest)
            val here = brandsIn(q, db)
            if (here.isNotEmpty()) { ctx = here[0].brand; q = q.filterIndexed { i, _ -> here.none { h -> i >= h.at && i < h.at + h.len } } }
            if (q.isEmpty()) continue
            val ranked = all.map { score(q, it, ctx) to it }.filter { it.first >= 0.45 }.sortedByDescending { it.first }
            val alts = ranked.map { it.second }.distinctBy { "${it.brand}|${it.name}" }.take(5)
            val f = alts.firstOrNull()
            // "half slab ribs" is one "Ribs, half slab", not half of one.
            val scale = if (f == null) qty else if (qty == 0.5 && unit.isEmpty() && Regex("""\bhalf\b|1/2""", RegexOption.IGNORE_CASE).containsMatchIn(f.name + " " + f.serving)) 1.0 else servingScale(unit, qty, f.serving)
            rows += FoodRow(part, ctx, qty, unit, f, alts, scale)
        }
        return rows
    }

    fun slug(s: String) = norm(s).replace(' ', '-')

    fun entryOf(row: FoodRow, id: String, at: String, meal: String): FoodEntry {
        val f = row.food; val k = row.scale
        fun r(x: Double) = (x * k).roundToInt()
        return FoodEntry(id, at, row.text, f?.title ?: row.text, Math.round(k * 100) / 100.0,
            f?.let { r(it.kcal) } ?: 0, f?.let { r(it.p) } ?: 0, f?.let { r(it.c) } ?: 0, f?.let { r(it.f) } ?: 0,
            if (f == null) "guess" else if (f.src == "chain") slug(f.brand) else f.src, meal)
    }

    /** A planned meal step, "Lunch: chicken wrap: 5 oz rotisserie chicken, tortilla (45 g · 420 kcal)". */
    fun planned(label: String, db: FoodDb?, mine: List<Food> = emptyList()): Planned {
        val m = Regex("""^(breakfast|lunch|dinner|snack)\s*:\s*(.+)$""", RegexOption.IGNORE_CASE).find(label)
            ?: Regex("""^(breakfast|lunch|dinner)\s+with\b(.+)$""", RegexOption.IGNORE_CASE).find(label)
        val meal = m?.groupValues?.get(1)?.lowercase() ?: if (Regex("shake|yogurt|snack", RegexOption.IGNORE_CASE).containsMatchIn(label)) "snack" else ""
        val what = (m?.groupValues?.get(2) ?: label).trim()
        val p = (Regex("""\(~?(\d+)\s*g\b""").find(what) ?: Regex("""(\d+)\s*g protein""").find(label))?.groupValues?.get(1)
        val k = Regex("""(\d[\d,]*)\s*kcal""").find(what)?.groupValues?.get(1)
        val name = what.replace(Regex("""\s*\([^)]*\)\s*$"""), "").replace(Regex(":.*$"), "").trim()
        var kcal = k?.replace(",", "")?.toInt() ?: 0; var protein = p?.toInt() ?: 0; var carbs = 0; var fat = 0; var src = "plan"
        if (kcal == 0) {
            val all = if (db != null) merged(db, mine) else mine
            val q = toks(name)
            val best = if (q.isEmpty()) null else all.map { score(q, it, "") to it }.filter { it.first >= 0.75 }.maxByOrNull { it.first }
            if (best != null) {
                val f = best.second; val s = if (protein > 0 && f.p > 0) protein / f.p else 1.0
                kcal = (f.kcal * s).roundToInt(); carbs = (f.c * s).roundToInt(); fat = (f.f * s).roundToInt(); if (protein == 0) protein = f.p.roundToInt()
            } else { kcal = mapOf("breakfast" to 450, "lunch" to 550, "dinner" to 650, "snack" to 220)[meal] ?: 450; src = "plan-est" }
        }
        if (kcal > 0 && carbs == 0 && fat == 0) { val rest = max(0, kcal - protein * 4); carbs = (rest * 0.55 / 4).roundToInt(); fat = (rest * 0.45 / 9).roundToInt() }
        return Planned(meal, name.replaceFirstChar { it.uppercase() }, Macros(kcal, protein, carbs, fat), src)
    }

    fun mealNow(t: LocalTime = LocalTime.now()): String {
        val h = t.hour + t.minute / 60.0
        return when { h < 10.5 -> "breakfast"; h < 14.5 -> "lunch"; h < 17 -> "snack"; h < 21 -> "dinner"; else -> "snack" }
    }

    fun totals(list: List<FoodEntry>): Macros = list.fold(Macros()) { a, e -> a + Macros(e.kcal, e.protein, e.carbs, e.fat) }

    /** Daily targets: "Calorie target: about N kcal" and "Protein target: about N g" in me/profile.md. */
    fun targets(profile: String?, proteinTarget: Int): Macros {
        val kcal = Regex("""Calorie target:\s*about\s*([\d,]+)\s*kcal""", RegexOption.IGNORE_CASE).find(profile ?: "")?.groupValues?.get(1)?.replace(",", "")?.toInt() ?: 2400
        val protein = if (proteinTarget > 0) proteinTarget else 180
        val fat = (kcal * 0.3 / 9).roundToInt()
        return Macros(kcal, protein, max(0, ((kcal - protein * 4 - fat * 9) / 4.0).roundToInt()), fat)
    }
}
