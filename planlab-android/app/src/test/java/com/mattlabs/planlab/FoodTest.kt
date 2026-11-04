package com.mattlabs.planlab

import com.mattlabs.planlab.core.FoodDb
import com.mattlabs.planlab.core.Foods
import com.mattlabs.planlab.core.LogDoc
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** The same cases as planlab/test/food.test.cjs on the site, so the phone and the web page find the same foods. */
class FoodTest {
    private val db = FoodDb.parse(listOf("../../planlab/data/foods.json", "../planlab/data/foods.json", "planlab/data/foods.json")
        .map(::File).first { it.exists() }.readText())

    private fun names(t: String, mine: String? = null) =
        Foods.lookup(t, db, Foods.mine(mine)).map { (it.food?.let { f -> "${f.brand}|${f.name}" }) to Math.round(it.scale * 100) / 100.0 }

    @Test fun skylineOrderCarriesTheBrand() {
        assertEquals(listOf("Skyline|Regular 3-way" to 1.0, "Skyline|Cheese coney" to 2.0), names("skyline 3-way, 2 coneys"))
        assertEquals(listOf("Skyline|Regular 3-way" to 1.0, "Skyline|Cheese coney" to 2.0), names("3 way and two cheese coneys from Skyline"))
        assertEquals(listOf("Gold Star|Regular 3-way" to 1.0), names("three-way at gold star"))
    }

    @Test fun sizesUnitsAndPlainFoods() {
        assertEquals(listOf("Skyline|Large 3-way" to 1.0), names("large 3-way skyline"))
        assertEquals(listOf("|Steak, sirloin, cooked" to 1.33), names("8 oz steak"))
        assertEquals(listOf("|Egg" to 3.0, "|Bread" to 2.0), names("3 eggs, 2 slices toast"))
        assertEquals(listOf("LaRosa's|Pepperoni pizza, large" to 2.0), names("2 slices larosas pepperoni"))
        assertEquals(listOf("|Beer" to 1.0), names("a beer"))
        assertEquals(listOf("Montgomery Inn|Ribs, half slab" to 1.0), names("half slab ribs montgomery inn"))
        assertEquals(listOf("Raising Cane's|Chicken finger" to 5.0), names("5 chicken fingers canes"))
        assertEquals(listOf("|Mac and cheese" to 1.0), names("mac and cheese"))
        assertEquals(listOf("Chick-fil-A|Nuggets, 8 count" to 1.0, "Chick-fil-A|Waffle fries, medium" to 1.0), names("chick fil a nuggets and waffle fries"))
    }

    @Test fun mattsOwnFoodsWin() {
        val md = "| Food | Serving | kcal | Protein | Carbs | Fat | Also called |\n|---|---|---|---|---|---|---|\n| Turkey chili | 1 bowl | 430 | 52 g | 30 g | 11 g | chili, my chili |\n| Skyline: Regular 3-way | 1 regular | 760 | 33 | 70 | 38 | |"
        assertEquals(2, Foods.mine(md).size)
        assertEquals("|Turkey chili" to 1.0, names("bowl of chili", md)[0])
        assertEquals("Skyline|Regular 3-way" to 1.0, names("skyline 3 way", md)[0])
        assertEquals(760.0, Foods.lookup("skyline 3 way", db, Foods.mine(md))[0].food!!.kcal, 0.0)
    }

    @Test fun entriesAndPlannedMeals() {
        val e = Foods.entryOf(Foods.lookup("2 coneys skyline", db)[0], "x", "12:00", "lunch")
        assertEquals(listOf(560, 24), listOf(e.kcal, e.protein)); assertEquals("skyline", e.src)
        val p = Foods.planned("Lunch: chicken wrap: 5 oz rotisserie chicken, tortilla, greens, salsa (45 g · 420 kcal)", db)
        assertEquals(listOf("lunch", "Chicken wrap"), listOf(p.meal, p.name)); assertEquals(listOf(420, 45), listOf(p.m.kcal, p.m.protein))
        val g = Foods.planned("Breakfast with 40 g protein (eggs, Greek yogurt or a shake)", db)
        assertEquals(listOf("breakfast", "40", "plan-est"), listOf(g.meal, g.m.protein.toString(), g.src))
        val t = Foods.targets("Protein target: about 180 g a day.\nCalorie target: about 2,400 kcal a day.", 180)
        assertEquals(listOf(2400, 180), listOf(t.kcal, t.protein))
        assertEquals(1120, Foods.totals(listOf(e, e)).kcal)
    }

    @Test fun nothingFoundStaysAGuess() {
        val r = Foods.lookup("zzqx", db)[0]
        assertNull(r.food)
        assertEquals("guess", Foods.entryOf(r, "x", "12:00", "lunch").src)
    }

    @Test fun foodOpsMergeAndDeleteAsNull() {
        val e = Foods.entryOf(Foods.lookup("2 coneys skyline", db)[0], "a1", "12:00", "lunch")
        val remote = JSONObject("""{"v":1,"updated":5,"ticks":{},"mins":{},"freezer":{},"food":{"2030-03-04":{"w1":{"name":"Egg","kcal":70,"protein_g":6}}}}""")
        val ops = JSONArray().put(LogDoc.foodOp("2030-03-04", "a1", e.toJson())).put(LogDoc.foodOp("2030-03-04", "w1", null))
        val d = LogDoc.apply(remote, ops, 9)
        assertEquals(listOf("a1"), LogDoc.food(d, "2030-03-04").map { it.id })
        assertEquals(560, LogDoc.food(d, "2030-03-04")[0].kcal)
        assertEquals(JSONObject.NULL, d.getJSONObject("food").getJSONObject("2030-03-04").get("w1"))
    }
}
