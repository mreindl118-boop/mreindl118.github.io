// node --test planlab/test/  (same cases as planlab-android FoodTest.kt)
const test = require("node:test"), assert = require("node:assert");
const F = require("../food.js");
const db = F.table(require("../data/foods.json"));
const names = (t, extra) => F.lookup(t, db, extra).map(r => [r.food ? `${r.food.brand}|${r.food.name}` : null, +r.scale.toFixed(2)]);

test("Skyline order: brand carries to every part", () => {
  assert.deepStrictEqual(names("skyline 3-way, 2 coneys"), [["Skyline|Regular 3-way", 1], ["Skyline|Cheese coney", 2]]);
  assert.deepStrictEqual(names("3 way and two cheese coneys from Skyline"), [["Skyline|Regular 3-way", 1], ["Skyline|Cheese coney", 2]]);
  assert.deepStrictEqual(names("three-way at gold star"), [["Gold Star|Regular 3-way", 1]]);
});
test("sizes, units and plain foods", () => {
  assert.deepStrictEqual(names("large 3-way skyline"), [["Skyline|Large 3-way", 1]]);
  assert.deepStrictEqual(names("8 oz steak"), [["|Steak, sirloin, cooked", 1.33]]);
  assert.deepStrictEqual(names("3 eggs, 2 slices toast"), [["|Egg", 3], ["|Bread", 2]]);
  assert.deepStrictEqual(names("2 slices larosas pepperoni"), [["LaRosa's|Pepperoni pizza, large", 2]]);
  assert.deepStrictEqual(names("a beer"), [["|Beer", 1]]);
  assert.deepStrictEqual(names("half slab ribs montgomery inn"), [["Montgomery Inn|Ribs, half slab", 1]]);
  assert.deepStrictEqual(names("5 chicken fingers canes"), [["Raising Cane's|Chicken finger", 5]]);
  assert.deepStrictEqual(names("mac and cheese"), [["|Mac and cheese", 1]]);
  assert.deepStrictEqual(names("chick fil a nuggets and waffle fries"), [["Chick-fil-A|Nuggets, 8 count", 1], ["Chick-fil-A|Waffle fries, medium", 1]]);
});
test("Matt's own foods win", () => {
  const mine = F.mine("| Food | Serving | kcal | Protein | Carbs | Fat | Also called |\n|---|---|---|---|---|---|---|\n| Turkey chili | 1 bowl | 430 | 52 g | 30 g | 11 g | chili, my chili |\n| Skyline: Regular 3-way | 1 regular | 760 | 33 | 70 | 38 | |");
  assert.equal(mine.length, 2);
  assert.deepStrictEqual(names("bowl of chili", mine)[0], ["|Turkey chili", 1]);
  assert.deepStrictEqual(names("skyline 3 way", mine)[0], ["Skyline|Regular 3-way", 1]);
  assert.equal(F.lookup("skyline 3 way", db, mine)[0].food.kcal, 760);
});
test("entries and planned meals", () => {
  const e = F.entryOf(F.lookup("2 coneys skyline", db)[0]);
  assert.deepStrictEqual([e.kcal, e.protein_g, e.src], [560, 24, "skyline"]);
  const p = F.planned("Lunch: chicken wrap: 5 oz rotisserie chicken, tortilla, greens, salsa (45 g · 420 kcal)", db);
  assert.deepStrictEqual([p.meal, p.name, p.kcal, p.protein_g], ["lunch", "Chicken wrap", 420, 45]);
  const l = F.planned("Snack: protein shake, 1 scoop whey in milk (30 g · 240 kcal) · Food", db);
  assert.deepStrictEqual([l.meal, l.name, l.kcal, l.protein_g], ["snack", "Protein shake, 1 scoop whey in milk", 240, 30]);
  const g = F.planned("Breakfast with 40 g protein (eggs, Greek yogurt or a shake)", db);
  assert.deepStrictEqual([g.meal, g.protein_g, g.src], ["breakfast", 40, "plan-est"]);
  const t = F.targets("Protein target: about 180 g a day.\nCalorie target: about 2,400 kcal a day.");
  assert.deepStrictEqual([t.kcal, t.protein_g], [2400, 180]);
  assert.equal(F.totals([e, null, e]).kcal, 1120);
});
test("nothing found stays a guess", () => {
  const r = F.lookup("zzqx", db)[0];
  assert.equal(r.food, null);
  assert.equal(F.entryOf(r).src, "guess");
});
