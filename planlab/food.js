// planLAB food lookup: "skyline 3-way, 2 coneys" -> foods with calories and macros, instantly, with no AI.
// Same rules as planlab-android core/Food.kt; keep the two in step (planlab/test/food.test.mjs and FoodTest.kt
// check the same cases). Sources, best first: Matt's own foods (meals/foods.md in his private repo), this
// site's table (data/foods.json), then Open Food Facts for anything else.
(function (root) {
"use strict";
const STOP = new Set(["a", "an", "the", "of", "with", "from", "at", "some", "my", "i", "had", "ate", "got", "for", "on", "in", "and", "plus", "order", "today", "lunch", "dinner", "breakfast", "snack"]);
const WORDN = { a: 1, an: 1, one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, eight: 8, nine: 9, ten: 10, twelve: 12, half: 0.5, couple: 2, few: 3 };
const UNITS = { oz: "oz", ounce: "oz", ounces: "oz", g: "g", gram: "g", grams: "g", cup: "cup", cups: "cup", slice: "slice", slices: "slice", piece: "piece", pieces: "piece", pc: "piece", scoop: "scoop", scoops: "scoop", tbsp: "tbsp", tsp: "tsp", bowl: "bowl", bowls: "bowl" };

function norm(s) {
  return String(s).toLowerCase().replace(/[’']/g, "").replace(/&/g, " and ")
    .replace(/\b(one|two|three|four|five)[\s-]+ways?\b/g, (m, w) => WORDN[w] + "way")
    .replace(/\b(\d)[\s-]*ways?\b/g, "$1way").replace(/[^a-z0-9.\/]+/g, " ").trim();
}
function sing(t) {
  if (t.length <= 3 || /\d/.test(t)) return t;
  if (t.endsWith("ies")) return t.slice(0, -3) + "y";
  if (/(ss|us|is)$/.test(t)) return t;
  if (/(ch|sh|x)es$/.test(t)) return t.slice(0, -2);
  if (t.endsWith("s")) return t.slice(0, -1);
  return t;
}
const toks = s => norm(s).split(" ").filter(t => t && !STOP.has(t)).map(sing);

function table(json) {
  const brands = [];
  for (const [b, al] of Object.entries(json.brands || {})) for (const a of [b, ...al]) brands.push({ brand: b, t: toks(a) });
  brands.sort((x, y) => y.t.length - x.t.length);
  const foods = (json.foods || []).map(r => food(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[0] ? "chain" : "table"));
  const nibbles = (json.nibbles || []).map(r => ({ id: r[0], icon: r[1], name: r[2], kcal: +r[3] || 0, protein_g: +r[4] || 0, carbs_g: +r[5] || 0, fat_g: +r[6] || 0 }));
  return { brands, foods, nibbles: nibbles.length ? nibbles : NIBBLES };
}

// Nibbles: the bites that feel like they don't count. Used when foods.json has no "nibbles" list; keep in step with Foods.NIBBLES.
const NIBBLES = [["bite", "🤏", "A bite", 80, 2, 9, 4], ["handful", "✊", "Handful", 150, 3, 16, 8], ["sweet", "🍪", "Sweet", 120, 1, 17, 5],
  ["cheese", "🧀", "Cheese", 100, 6, 1, 8], ["spoon", "🥄", "Spoon of PB", 95, 4, 3, 8], ["sip", "🥤", "Sweet drink", 140, 0, 36, 0]]
  .map(r => ({ id: r[0], icon: r[1], name: r[2], kcal: r[3], protein_g: r[4], carbs_g: r[5], fat_g: r[6] }));
function nibbleEntry(n, at) { return { at, text: "nibble: " + n.id, name: n.name, qty: 1, kcal: n.kcal, protein_g: n.protein_g, carbs_g: n.carbs_g, fat_g: n.fat_g, src: "nibble", meal: "snack", plan: "" }; }
function nibbles(list) { const n = list.filter(e => e && e.src === "nibble"); return { n: n.length, kcal: n.reduce((a, e) => a + (e.kcal || 0), 0) }; }
function food(brand, name, serving, kcal, p, c, f, aliases, src) {
  const names = [name, ...String(aliases || "").split("|").filter(Boolean)];
  return { brand: brand || "", name, serving: serving || "1 serving", kcal: +kcal || 0, p: +p || 0, c: +c || 0, f: +f || 0, src, keys: names.map(toks).filter(t => t.length) };
}

// Matt's own foods: the table in meals/foods.md, | Food | Serving | kcal | Protein | Carbs | Fat | Also called |
function mine(md) {
  const out = [];
  for (const line of String(md || "").split("\n")) {
    const t = line.trim(); if (!t.startsWith("|") || /^\|[\s\-|:]+\|$/.test(t)) continue;
    const c = t.replace(/^\||\|$/g, "").split("|").map(x => x.trim());
    if (c.length < 6 || !/\d/.test(c[2])) continue;
    const n = x => parseFloat(String(x).replace(/[^\d.]/g, "")) || 0;
    const brand = (c[0].match(/^(.+?):\s/) || [])[1] || "";
    out.push(food(brand, brand ? c[0].slice(brand.length + 1).trim() : c[0], c[1], n(c[2]), n(c[3]), n(c[4]), n(c[5]), (c[6] || "").replace(/,\s*/g, "|"), "mine"));
  }
  return out;
}

function brandsIn(tokens, db) {
  const found = [];
  for (const b of db.brands) {
    for (let i = 0; i + b.t.length <= tokens.length; i++) if (b.t.every((x, j) => tokens[i + j] === x)) { found.push({ brand: b.brand, at: i, len: b.t.length }); break; }
  }
  return found;
}

// "2 coneys" -> { qty: 2, unit: "", rest: "coneys" }
function qtyOf(chunk) {
  let s = norm(chunk), qty = 1, unit = "";
  let m = s.match(/^(\d+(?:\.\d+)?|\d+\/\d+)\s*x?\s+(?!way\b)(.*)$/);
  if (m) { qty = m[1].includes("/") ? m[1].split("/").reduce((a, b) => a / b) : +m[1]; s = m[2]; }
  else if ((m = s.match(/^(a couple|a few|half a|a half|one|two|three|four|five|six|seven|eight|nine|ten|twelve|half|couple|a|an)\s+(?:of\s+)?(.*)$/))) { const w = m[1].replace(/^a (couple|few)$/, "$1").replace(/^(half a|a half)$/, "half"); qty = WORDN[w] ?? 1; s = m[2]; }
  m = s.match(/^(.*?)\s+x\s?(\d+)$/) || s.match(/^(.*?)\s+(\d+)x$/); if (m) { qty *= +m[2]; s = m[1]; }
  m = s.match(/^([a-z]+)\s+(?:of\s+)?(.*)$/); if (m && UNITS[m[1]] && m[2]) { unit = UNITS[m[1]]; s = m[2]; }
  return { qty, unit, rest: s };
}

function score(q, f, brand) {
  let best = 0;
  for (const k of f.keys) {
    let hit = 0;
    for (const t of q) { if (k.includes(t)) hit++; else if (t.length >= 4 && k.some(x => x.length >= 4 && (x.startsWith(t) || t.startsWith(x)))) hit += 0.7; }
    if (!hit) continue;
    const s = (hit / q.length) * 0.65 + (Math.min(hit, k.length) / k.length) * 0.35;
    if (s > best) best = s;
  }
  if (!best) return 0;
  if (brand) best += f.brand === brand ? 0.25 : f.brand ? -0.35 : -0.05;
  else if (f.brand) best -= 0.04;
  if (f.src === "mine") best += 0.06;
  return best;
}

function servingScale(unit, qty, serving) {
  if (!unit) return qty;
  const m = norm(serving).match(/^(\d+(?:\.\d+)?|\d+\/\d+)?\s*([a-z]+)/);
  if (!m || UNITS[m[2]] !== unit) return qty;
  const amt = m[1] ? (m[1].includes("/") ? m[1].split("/").reduce((a, b) => a / b) : +m[1]) : 1;
  return qty / (amt || 1);
}

function splitText(text) {
  return String(text).split(/[,;+\n]|\s+&\s+|\band\b(?!\s+cheese)/i).map(s => s.trim()).filter(s => s && toks(s).length + (/\d/.test(s) ? 1 : 0) > 0);
}

// The whole lookup: one row per thing eaten, best match first plus a few alternatives.
// Matt's own row replaces a table row with the same brand and name (and keeps its other names).
function merged(db, extra) {
  if (!extra || !extra.length) return db.foods;
  const id = f => (f.brand + "|" + f.name).toLowerCase(), mine = new Map(extra.map(f => [id(f), f]));
  const out = db.foods.map(f => { const m = mine.get(id(f)); if (!m) return f; mine.delete(id(f)); return Object.assign({}, m, { keys: m.keys.concat(f.keys) }); });
  return [...mine.values(), ...out];
}

function lookup(text, db, extra) {
  const all = merged(db, extra);
  const parts = splitText(text);
  const allTok = toks(text);
  const mentioned = brandsIn(allTok, db);
  let ctx = mentioned.length === 1 ? mentioned[0].brand : "";
  const rows = [];
  for (const part of parts) {
    const { qty, unit, rest } = qtyOf(part);
    let q = toks(rest);
    const here = brandsIn(q, db);
    if (here.length) { ctx = here[0].brand; q = q.filter((_, i) => !here.some(h => i >= h.at && i < h.at + h.len)); }
    if (!q.length) continue;
    const ranked = all.map(f => [score(q, f, ctx), f]).filter(x => x[0] >= 0.45).sort((a, b) => b[0] - a[0]);
    const seen = new Set(), alts = [];
    for (const [, f] of ranked) { const k = f.brand + "|" + f.name; if (!seen.has(k)) { seen.add(k); alts.push(f); } if (alts.length >= 5) break; }
    const f = alts[0] || null;
    // "half slab ribs" is one "Ribs, half slab", not half of one.
    const scale = f ? (qty === 0.5 && !unit && /\bhalf\b|1\/2/i.test(f.name + " " + f.serving) ? 1 : servingScale(unit, qty, f.serving)) : qty;
    rows.push({ text: part, query: q.join(" "), brand: ctx, qty, unit, food: f, alts, scale });
  }
  return rows;
}

const round = n => Math.round(n);
function entryOf(row, extra) {
  const f = row.food, k = row.scale;
  return Object.assign({ text: row.text, name: f ? (f.brand ? f.brand + " " : "") + f.name : row.text, qty: +k.toFixed(2),
    kcal: f ? round(f.kcal * k) : 0, protein_g: f ? round(f.p * k) : 0, carbs_g: f ? round(f.c * k) : 0, fat_g: f ? round(f.f * k) : 0,
    src: f ? (f.src === "chain" ? norm(f.brand).replace(/ /g, "-") : f.src) : "guess" }, extra || {});
}

// A planned meal step, "Lunch: chicken wrap: 5 oz rotisserie chicken, tortilla (45 g · 420 kcal)"
function planned(label, db, extra) {
  const m = String(label).match(/^(breakfast|lunch|dinner|snack)\s*:\s*(.+)$/i) || String(label).match(/^(breakfast|lunch|dinner)\s+with\b(.+)$/i);
  const meal = m ? m[1].toLowerCase() : /shake|yogurt|snack/i.test(label) ? "snack" : "";
  // A trailing " · Recipe" or " · Food" (a link in the week file) isn't part of the meal.
  const what = (m ? m[2] : label).trim().replace(/\s+·\s+[^()]*$/, "");
  const p = (what.match(/\((?:~)?(\d+)\s*g\b/) || label.match(/(\d+)\s*g protein/) || [])[1];
  const k = (what.match(/(\d[\d,]*)\s*kcal/) || [])[1];
  // "Breakfast with 40 g protein (eggs, Greek yogurt or a shake)": the hint in brackets is the name.
  const withForm = m && /^\s*\d+\s*g protein/i.test(m[2]);
  const name = withForm ? ((what.match(/\(([^)]*)\)\s*$/) || [])[1] || "your pick") : what.replace(/\s*\([^)]*\)\s*$/, "").replace(/:.*$/, "").trim();
  let kcal = k ? +k.replace(/,/g, "") : 0, protein = p ? +p : 0, carbs = 0, fat = 0, src = "plan";
  if (!kcal) {
    const all = db ? merged(db, extra) : (extra || []);
    const q = toks(name), best = q.length ? all.map(f => [score(q, f, ""), f]).filter(x => x[0] >= 0.75).sort((a, b) => b[0] - a[0])[0] : null;
    if (best) { const f = best[1], s = protein && f.p ? protein / f.p : 1; kcal = round(f.kcal * s); carbs = round(f.c * s); fat = round(f.f * s); if (!protein) protein = round(f.p); }
    else { kcal = { breakfast: 450, lunch: 550, dinner: 650, snack: 220 }[meal] || 450; src = "plan-est"; }
  }
  if (kcal && !carbs && !fat) { const rest = Math.max(0, kcal - protein * 4); carbs = round(rest * 0.55 / 4); fat = round(rest * 0.45 / 9); }
  return { meal, name: name.charAt(0).toUpperCase() + name.slice(1), kcal, protein_g: protein, carbs_g: carbs, fat_g: fat, src };
}

function mealNow(d) { const h = d.getHours() + d.getMinutes() / 60; return h < 10.5 ? "breakfast" : h < 14.5 ? "lunch" : h < 17 ? "snack" : h < 21 ? "dinner" : "snack"; }
function totals(list) { const t = { kcal: 0, protein_g: 0, carbs_g: 0, fat_g: 0, n: 0 }; for (const e of list) if (e) { t.kcal += e.kcal || 0; t.protein_g += e.protein_g || 0; t.carbs_g += e.carbs_g || 0; t.fat_g += e.fat_g || 0; t.n++; } return t; }
function targets(profileMd, proteinFallback) {
  const k = String(profileMd || "").match(/Calorie target:\s*about\s*([\d,]+)\s*kcal/i), p = String(profileMd || "").match(/Protein target:\s*about\s*(\d+)\s*g/i);
  const kcal = k ? +k[1].replace(/,/g, "") : 2400, protein = p ? +p[1] : proteinFallback || 180;
  const fat = round(kcal * 0.3 / 9), carbs = Math.max(0, round((kcal - protein * 4 - fat * 9) / 4));
  return { kcal, protein_g: protein, carbs_g: carbs, fat_g: fat };
}

// Open Food Facts, for anything not in the tables. Free, no key.
async function openFoodFacts(q) {
  const u = "https://world.openfoodfacts.org/cgi/search.pl?search_simple=1&json=1&page_size=8&fields=product_name,brands,serving_size,nutriments&search_terms=" + encodeURIComponent(q);
  const r = await fetch(u); if (!r.ok) return [];
  const j = await r.json();
  return (j.products || []).map(p => {
    const n = p.nutriments || {}, per = n["energy-kcal_serving"] != null ? "_serving" : "_100g";
    const kcal = n["energy-kcal" + per]; if (kcal == null || !p.product_name) return null;
    return food((p.brands || "").split(",")[0].trim(), p.product_name, per === "_serving" ? (p.serving_size || "1 serving") : "100 g", kcal, n["proteins" + per], n["carbohydrates" + per], n["fat" + per], "", "off");
  }).filter(Boolean).slice(0, 5);
}

const api = { norm, toks, table, NIBBLES, nibbleEntry, nibbles, mine, lookup, entryOf, planned, mealNow, totals, targets, openFoodFacts, qtyOf, splitText };
if (typeof module !== "undefined" && module.exports) module.exports = api; else root.PlanFood = api;
})(typeof window !== "undefined" ? window : globalThis);
