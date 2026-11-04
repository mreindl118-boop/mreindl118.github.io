// planLAB: Matt's planner. Reads the Markdown in his private GeneralStorage repo
// (projects/planlab/) with a read-only GitHub key kept on this device only.
// No planner content lives in this public repo.
(() => {
const REPO = "mreindl118-boop/GeneralStorage";
const DIR = "projects/planlab";
const KEY_URL = "https://github.com/settings/personal-access-tokens/new?name=planLAB&description=Read%20my%20planner%20on%20my%20phone&target_name=mreindl118-boop&expires_in=none&contents=read&issues=write";

const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const pad = n => String(n).padStart(2, "0");
const iso = d => `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`;
const parse = s => { const [y,m,d] = s.split("-").map(Number); return new Date(y, m-1, d); };
const addDays = (s, n) => { const d = parse(s); d.setDate(d.getDate()+n); return iso(d); };
const daysBetween = (a, b) => Math.round((parse(b) - parse(a)) / 864e5);
const todayIso = () => iso(new Date());
const nice = (s, opts) => parse(s).toLocaleDateString("en-US", opts || { weekday: "short", month: "short", day: "numeric" });
const store = {
  get(k) { try { return localStorage.getItem(k); } catch { return null; } },
  set(k, v) { try { localStorage.setItem(k, v); } catch {} },
  del(k) { try { localStorage.removeItem(k); } catch {} },
  json(k, d) { try { return JSON.parse(localStorage.getItem(k) || "null") ?? d; } catch { return d; } },
};

// ---------- Markdown -> plan (mirrors projects/planlab/tools/app_data.py)
function stripMd(s) {
  return String(s).replace(/\*\*(.+?)\*\*/g, "$1").replace(/`([^`]+)`/g, "$1")
    .replace(/\[([^\]]+)\]\([^)]+\)/g, "$1").replace(/\s*\(?\b[\w./-]+\.md\)?/g, "").trim();
}
const keyOf = s => stripMd(s).toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 60);
function isoMonday(id) { const [y, w] = id.split("-W").map(Number); const j4 = new Date(y, 0, 4); const d = new Date(j4); d.setDate(j4.getDate() - ((j4.getDay() + 6) % 7) + (w - 1) * 7); return d; }
function guessDate(y, m, d, explicit) { let s = `${y}-${pad(m)}-${pad(d)}`; if (!explicit && daysBetween(s, todayIso()) > 120) s = `${y+1}-${pad(m)}-${pad(d)}`; return s; }
const sections = text => text.split(/^## /m).slice(1).map(sec => { const i = sec.indexOf("\n"); return [sec.slice(0, i < 0 ? undefined : i).trim(), i < 0 ? "" : sec.slice(i + 1)]; });

function parseWeek(id, text) {
  const mon = isoMonday(id);
  const w = { id, start: iso(mon), phase: "", focus: "", glance: [], days: [] };
  let m = text.match(/\*\*Phase:\*\*\s*(.+)/); if (m) w.phase = stripMd(m[1]);
  m = text.match(/\*\*Focus this week:\*\*\s*(.+)/); if (m) w.focus = stripMd(m[1]);
  for (const [head, body] of sections(text)) {
    if (/^the week at a glance/i.test(head)) {
      for (const line of body.split("\n")) { const g = line.trim().match(/^- \*\*(.+?):\*\*\s*(.+)/); if (g) w.glance.push({ label: stripMd(g[1]), text: stripMd(g[2]) }); }
      continue;
    }
    const dm = head.match(/^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)\s+(\d\d)-(\d\d)/); if (!dm) continue;
    const month = +dm[2], year = mon.getMonth() === 11 && month === 1 ? mon.getFullYear() + 1 : mon.getFullYear();
    const d = { date: `${year}-${dm[2]}-${dm[3]}`, label: head, eat: [], body: "", practice: [], coming: [], boxes: [] };
    // Sub-steps (same rules as the Android app): "### piano" then "- [ ] step" lines (key "piano/<step>"),
    // or an indented "  - [ ] piano: step" under the box row (key = the whole label).
    const steps = {}, heads = {}; let area = null;
    for (const raw of body.split("\n")) {
      const t = raw.trim();
      if (t.startsWith("### ")) { area = keyOf(t.slice(4)); heads[area] = stripMd(t.slice(4)); continue; }
      const one = raw.match(/^\s*- \[( |x|X)\]\s*(.+)/);
      if (one && area) { const label = stripMd(one[2]); (steps[area] = steps[area] || []).push({ key: `${area}/${keyOf(label)}`, label, done: one[1].toLowerCase() === "x" }); continue; }
      if (one && /^\s/.test(raw)) { const full = stripMd(one[2]), c = full.indexOf(":"); const parent = c > 0 ? keyOf(full.slice(0, c)) : d.boxes.at(-1)?.key; if (!parent) continue;
        (steps[parent] = steps[parent] || []).push({ key: keyOf(full), label: c > 0 ? full.slice(c + 1).trim() : full, done: one[1].toLowerCase() === "x" }); continue; }
      let line = t; if (!line.startsWith("- ")) continue; line = line.slice(2);
      if (line.startsWith("[")) { for (const b of line.matchAll(/\[( |x|X)\]\s*([^\[]+)/g)) { const label = b[2].trim(); d.boxes.push({ key: keyOf(label), label, done: b[1].toLowerCase() === "x" }); } continue; }
      const c = line.indexOf(":"); if (c < 0) continue;
      const k = line.slice(0, c).trim().toLowerCase(), v = line.slice(c + 1);
      const parts = v.split(" · ").filter(p => p.trim()).map(stripMd);
      if (k === "eat") d.eat = parts; else if (k === "body") d.body = stripMd(v); else if (k === "practice") d.practice = parts; else if (k === "coming up") d.coming = parts;
    }
    for (const [k, list] of Object.entries(steps)) { const b = d.boxes.find(x => x.key === k); if (b) b.steps = list; else d.boxes.push({ key: k, label: heads[k] || k.replace(/-/g, " "), done: false, steps: list }); }
    // Eat is tracked, not ticked (Matt, 2026-11-04): the meals box leaves the task list; its steps are planned meals.
    const mi = d.boxes.findIndex(b => b.key === "meals"); d.meals = mi >= 0 ? (d.boxes.splice(mi, 1)[0].steps || []) : [];
    w.days.push(d);
  }
  return w;
}
function parseTable(text) {
  const rows = [];
  for (let line of text.split("\n")) { line = line.trim(); if (!line.startsWith("|") || /^\|[\s\-|]+\|$/.test(line)) continue; rows.push(line.replace(/^\||\|$/g, "").split("|").map(stripMd)); }
  return rows.slice(1);
}
function parseCalendar(text) {
  const y = new Date().getFullYear(), events = [];
  const raw = text.split("## Work and friends")[0].split("\n").filter(l => /^\|/.test(l.trim()) && !/^\|[\s\-|]+\|$/.test(l.trim())).slice(1);
  parseTable(text.split("## Work and friends")[0]).forEach((row, i) => {
    const m = (row[0] || "").match(/(\d{4}-)?(\d\d)-(\d\d)/); if (!m || row.length < 2) return;
    // A bold row in calendar.md is the big date the app counts down to.
    events.push({ date: guessDate(m[1] ? +m[1].slice(0, 4) : y, +m[2], +m[3], !!m[1]), approx: row[0].startsWith("~"), what: row[1], who: row[2] || "", notes: row[3] || "", big: /\*\*/.test((raw[i] || "").split("|")[2] || "") });
  });
  const watching = [];
  if (text.includes("## Watching for")) for (const l of text.split("## Watching for")[1].split("\n")) if (l.trim().startsWith("- ")) watching.push(stripMd(l.trim().slice(2)));
  return { events, watching };
}
function parseChecklists(text) {
  const out = [];
  for (const [head, body] of sections(text)) {
    const items = [];
    for (const line of body.split("\n")) { const m = line.match(/^\s*- \[( |x|X)\]\s*(.+)/); if (!m) continue; const t = stripMd(m[2]), it = { key: keyOf(t), text: t, done: m[1].toLowerCase() === "x" };
      // An indented box is a step of the box above it.
      if (/^(  |\t)/.test(line) && items.length) (items.at(-1).steps = items.at(-1).steps || []).push(it); else items.push(it); }
    if (items.length) out.push({ name: stripMd(head), items });
  }
  return out;
}
function parseInbox(text) {
  const y = new Date().getFullYear();
  return parseChecklists(text).map(g => ({ ...g, items: g.items.map(it => {
    const m = it.text.match(/·\s*((?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\s+)?(\d{4}-)?(\d\d)-(\d\d)/);
    const i = it.text.indexOf(" · ");
    return { ...it, date: m ? guessDate(m[2] ? +m[2].slice(0, 4) : y, +m[3], +m[4], !!m[2]) : null, what: i < 0 ? it.text : it.text.slice(0, i), when: i < 0 ? "" : it.text.slice(i + 3) };
  }) }));
}
function parseRecipe(slug, text) {
  const t = text.match(/^# (.+)/m); const r = { slug, title: t ? stripMd(t[1]) : slug, md: text };
  const row = parseTable(text)[0];
  if (row && row.length >= 5) { const n = s => +(s.replace(/[^\d]/g, "") || 0); [r.kcal, r.protein, r.carbs, r.fat] = row.slice(1, 5).map(n); }
  const st = text.match(/\*\*Standing time:\*\*\s*([^·\n]+)/); if (st) r.standing = stripMd(st[1]);
  return r;
}

// ---------- GitHub

// ---------- more parsers: body program, music blocks, freezer, recovery, ideas, trips, profile
const secs = s => { const m = String(s).match(/(\d+)\s*s\b/); return m ? +m[1] : 0; };
const mins = s => { const m = String(s).match(/(\d+)\s*min/); return m ? +m[1] : 0; };
function parseMove(name, rx) {
  rx = stripMd(rx || ""); name = stripMd(name);
  const sets = +((rx.match(/(\d+)\s*×/) || [])[1] || 0) || 1;
  const hold = /×\s*\d+\s*s\b/.test(rx) ? secs(rx.replace(/.*×/, "")) : 0;
  const perSide = /per side/.test(rx);
  const time = !hold && /\bmin\b/.test(name + " " + rx) ? mins(name + " " + rx) * 60 : 0;
  return { key: keyOf(name), name, rx, sets, hold, perSide, time };
}
function parseProgram(text) {
  const out = { daily: null, workouts: {}, phases: [], goals: [] };
  for (const [head, body] of sections(text)) {
    if (/pelvic floor/i.test(head) && /every day/i.test(head)) {
      const reps = [];
      for (const l of body.split("\n")) {
        const m = l.match(/^- (\d+) ([^:]+):\s*(.+)/); if (!m) continue;
        const nums = [...m[3].matchAll(/(\d+)\s*s\b/g)].map(x => +x[1]);
        reps.push({ n: +m[1], name: stripMd(m[2]), on: nums[0] || 1, off: nums[1] || nums[0] || 1, how: stripMd(m[3]) });
      }
      const notes = body.split("\n").filter(l => /^- /.test(l) && !/^- \d+ /.test(l)).map(l => stripMd(l.slice(2)));
      out.daily = { title: stripMd(head.replace(/^every day, every phase:\s*/i, "")), reps, notes };
      continue;
    }
    if (/^phase/i.test(head)) {
      const ph = { title: stripMd(head), bullets: [] };
      let cur = null;
      for (const raw of body.split("\n")) {
        const l = raw.trim();
        const w = l.match(/^\*\*(.+?):\s*(.+?)\*\*\s*(.*)$/);
        if (w) { cur = { name: stripMd(w[1]), title: stripMd(w[2]), minutes: mins(w[2]), moves: [], note: stripMd(w[3]) }; out.workouts[keyOf(cur.name)] = cur; continue; }
        if (/^##/.test(l)) cur = null;
        if (cur && /^\|/.test(l)) { const c = l.replace(/^\||\|$/g, "").split("|").map(x => x.trim()); if (/^-+$/.test(c[0].replace(/\s/g, "")) || /^move$/i.test(c[0])) continue; cur.moves.push(parseMove(c[0], c[1])); continue; }
        if (cur && /^- /.test(l)) {
          const t = l.slice(2);
          const ci = t.indexOf(":");
          if (/^\d+ min /.test(t) && ci > 0) cur.moves.push({ ...parseMove(t.slice(0, ci), ""), rx: stripMd(t.slice(ci + 1)), time: mins(t) * 60 });
          else for (const part of t.split(" · ")) { const m = part.match(/^(.+?)\s+(\d+\s*×.*|for \d+ min.*)$/); cur.moves.push(m ? parseMove(m[1], m[2]) : { ...parseMove(part, ""), rx: "" }); }
          continue;
        }
        if (!cur && /^- /.test(l)) ph.bullets.push(stripMd(l.slice(2)));
        if (!cur && /^\d+\.\s/.test(l)) { const g = l.replace(/^\d+\.\s*/, ""); const mm = g.match(/^\*\*(.+?)\*\*:?\s*(.*)$/); out.goals.push(mm ? { title: stripMd(mm[1]).replace(/:$/, ""), text: stripMd(mm[2]) } : { title: stripMd(g), text: "" }); }
      }
      out.phases.push(ph);
    }
  }
  return out;
}
function parseMusic(text) {
  const out = { title: "", teacher: "", method: "", block: [], blockMin: 0, short: "", now: [], log: [] };
  const t = text.match(/^# (.+)/m); if (t) out.title = stripMd(t[1]);
  const tm = text.match(/Teacher:\s*([^.\n]+)/); if (tm) out.teacher = stripMd(tm[1]);
  const mm = text.match(/Method:\s*([^\n]+)/); if (mm) out.method = stripMd(mm[1]);
  const sm = text.match(/^Short day:\s*(.+)$/m); if (sm) out.short = stripMd(sm[1]);
  for (const [head, body] of sections(text)) {
    if (/^daily block/i.test(head)) { out.blockMin = mins(head); out.block = parseTable(body).filter(r => r.length >= 2 && /\d/.test(r[0])).map(r => ({ min: +r[0].replace(/[^\d.]/g, ""), what: r[1] })); }
    if (/^now working on/i.test(head)) out.now = body.split("\n").filter(l => /^- /.test(l.trim())).map(l => { const s = stripMd(l.trim().slice(2)); const i = s.indexOf(":"); return { label: i > 0 ? s.slice(0, i) : s, value: i > 0 ? s.slice(i + 1).trim() : "" }; });
    if (/^log/i.test(head)) out.log = parseTable(body).filter(r => r[0]);
  }
  return out;
}
function parseFreezer(text) {
  const out = { items: [], planned: [], target: [] };
  for (const [head, body] of sections(text)) {
    const rows = parseTable(body);
    if (/^what.s in it/i.test(head)) out.items = rows.filter(r => r[0] && /\d/.test(r[1] || "")).map(r => ({ key: keyOf(r[0]), name: r[0], n: +(r[1].match(/\d+/) || [0])[0], in: r[2] || "", use: r[3] || "" }));
    if (/^planned batch/i.test(head)) out.planned = rows.filter(r => r[0]).map(r => ({ day: r[0], cook: r[1], n: r[2] }));
    if (/^target/i.test(head)) out.target = rows.filter(r => r[0]).map(r => ({ meal: r[0], need: r[1], planned: r[2], from: r[3] }));
  }
  return out;
}
function parseRecovery(text) { return parseTable((text.split(/^## /m)[0]) || "").filter(r => r.length >= 4).map(r => ({ phase: r[0], dates: r[1], away: r[2], opens: r[3] })); }
function parseIdeas(text) { return sections(text).map(([h, b]) => ({ name: stripMd(h), items: b.split("\n").filter(l => /^- /.test(l.trim())).map(l => stripMd(l.trim().slice(2))) })).filter(s => s.items.length); }
function parseTrips(text) { return parseTable(text).filter(r => r.length >= 3).map(r => ({ when: r[0], idea: r[1], why: r[2], status: r[3] || "" })); }
function parseProfile(text) { const m = text.match(/Protein target:\s*about\s*(\d+)\s*g/i); return { protein: m ? +m[1] : 0 }; }
function parseRecipeFull(slug, text) {
  const r = parseRecipe(slug, text);
  const meta = k => { const m = text.match(new RegExp("\\*\\*" + k + ":\\*\\*\\s*([^·\\n]+)")); return m ? stripMd(m[1]) : ""; };
  r.serves = meta("Serves"); r.active = meta("Active time"); r.total = meta("Total time"); r.freezes = meta("Freezes"); r.with = meta("Serve with"); r.best = meta("Best for");
  r.ingredients = []; r.steps = []; r.notes = [];
  for (const [head, body] of sections(text)) {
    const bl = body.split("\n").map(l => l.trim());
    if (/^ingredients/i.test(head)) r.ingredients = bl.filter(l => /^- /.test(l)).map(l => stripMd(l.slice(2)));
    else if (/^steps/i.test(head)) r.steps = bl.filter(l => /^\d+\.\s/.test(l)).map(l => stripMd(l.replace(/^\d+\.\s*/, "")));
    else if (/^notes/i.test(head)) r.notes = bl.filter(l => /^- /.test(l)).map(l => stripMd(l.slice(2)));
  }
  return r;
}

// ---------- state
const KEY_RW = KEY_URL.replace("contents=read", "contents=write");
const LOG_PATH = `${DIR}/log/app.json`;
const S = { tab: "today", food: "list", setupStep: 1, day: todayIso(), plan: store.json("planlab-cache", null), loading: false, error: "", token: store.get("planlab-key"), msg: "", sync: store.get("planlab-sync") || "unknown" };
S.log = store.json("planlab-log", null) || { v: 1, updated: 0, ticks: store.json("planlab-ticks", {}), mins: {}, freezer: {} };
for (const k of ["ticks", "mins", "freezer", "food"]) S.log[k] = S.log[k] || {};
// Food entries merge by id from both sides (the Android app logs too); a deleted entry is null and stays null.
function mergeFood(a, b) { const out = JSON.parse(JSON.stringify(a || {})); for (const [d, ids] of Object.entries(b || {})) { out[d] = out[d] || {}; for (const [id, e] of Object.entries(ids || {})) if (!(id in out[d]) || e === null) out[d][id] = e; } return out; }
const saveLocal = () => store.set("planlab-log", JSON.stringify(S.log));

// ---------- GitHub
async function gh(path, opts = {}) {
  const r = await fetch(`https://api.github.com/repos/${REPO}/${path}`, { method: opts.method || "GET", body: opts.body, cache: "no-store",
    headers: { Authorization: `Bearer ${S.token}`, Accept: opts.raw ? "application/vnd.github.raw+json" : "application/vnd.github+json", ...(opts.body ? { "Content-Type": "application/json" } : {}) } });
  if (!r.ok) { const e = new Error(r.status === 401 ? "key" : "http"); e.status = r.status; throw e; }
  return opts.raw ? r.text() : r.json();
}
const file = p => gh(`contents/${DIR}/${p}`, { raw: true }).catch(e => { if (e.status === 404) return ""; throw e; });
const listDir = p => gh(`contents/${DIR}/${p}`).then(a => a.filter(f => f.name.endsWith(".md")).map(f => f.name.slice(0, -3))).catch(e => { if (e.status === 404) return []; throw e; });
const b64e = s => btoa(unescape(encodeURIComponent(s)));
const b64d = s => decodeURIComponent(escape(atob(s.replace(/\n/g, ""))));

async function load() {
  if (!S.token) return;
  S.loading = true; render();
  try {
    const names = ["social/calendar.md", "inbox.md", "me/recovery.md", "body/program.md", "music/piano.md", "music/voice.md", "music/guitar.md", "meals/freezer.md", "social/ideas.md", "social/trips.md", "me/profile.md", "meals/foods.md"];
    const [weekIds, groceryIds, recipeIds, ...txt] = await Promise.all([listDir("weeks"), listDir("groceries"), listDir("meals/recipes"), ...names.map(file)]);
    const T = Object.fromEntries(names.map((n, i) => [n, txt[i]]));
    const weeks = await Promise.all(weekIds.sort().map(async id => parseWeek(id, await file(`weeks/${id}.md`))));
    const groceries = await Promise.all(groceryIds.sort().map(async id => { const t = await file(`groceries/${id}.md`); const p = t.match(/\*\*Pickup:\*\*\s*([^·\n]+)/); return { week: id, pickup: p ? stripMd(p[1]) : "", sections: parseChecklists(t) }; }));
    const recipes = await Promise.all(recipeIds.sort().map(async id => parseRecipeFull(id, await file(`meals/recipes/${id}.md`))));
    const c = parseCalendar(T["social/calendar.md"]);
    const setup = parseChecklists(T["me/recovery.md"])[0];
    S.plan = { v: 2, built: new Date().toISOString(), weeks, calendar: c.events, watching: c.watching, inbox: parseInbox(T["inbox.md"]), setup: setup ? setup.items : [], groceries, recipes,
      program: parseProgram(T["body/program.md"]), piano: parseMusic(T["music/piano.md"]), voice: parseMusic(T["music/voice.md"]), guitar: parseMusic(T["music/guitar.md"]),
      freezer: parseFreezer(T["meals/freezer.md"]), recovery: parseRecovery(T["me/recovery.md"]), ideas: parseIdeas(T["social/ideas.md"]), trips: parseTrips(T["social/trips.md"]), profile: parseProfile(T["me/profile.md"]), profileMd: T["me/profile.md"], foodsMd: T["meals/foods.md"] };
    store.set("planlab-cache", JSON.stringify(S.plan));
    S.error = "";
    pullLog();
  } catch (e) {
    S.error = e.message === "key" || e.status === 403 ? "Your GitHub key didn't work. It may have expired or not include GeneralStorage." : "Couldn't reach GitHub. Showing the last copy you loaded.";
    if (e.message === "key" || e.status === 403) S.badKey = true;
  }
  S.loading = false; render();
}

// ---------- sync: ticks and logs go to projects/planlab/log/app.json, so Claude sees them
let logSha = null, pushTimer = null, pushing = false;
function setSync(v) { S.sync = v; store.set("planlab-sync", v); }
async function pullLog() {
  try {
    const f = await gh(`contents/${LOG_PATH}`);
    logSha = f.sha;
    const remote = JSON.parse(b64d(f.content));
    if ((remote.updated || 0) > (S.log.updated || 0)) { const food = mergeFood(remote.food, S.log.food); S.log = { ...S.log, ...remote, food }; saveLocal(); render(); }
    else if (remote.food) { S.log.food = mergeFood(S.log.food, remote.food); saveLocal(); }
    else if ((S.log.updated || 0) > (remote.updated || 0)) schedulePush(0);
    if (S.sync === "unknown") setSync("on");
  } catch (e) { if (e.status === 404 || e.status === 403) setSync("off"); }
}
function schedulePush(delay = 20000) { clearTimeout(pushTimer); pushTimer = setTimeout(pushLog, delay); }
async function pushLog(retry = true) {
  if (!S.token || S.sync === "off" || pushing) return;
  pushing = true;
  try {
    try { const f = await gh(`contents/${LOG_PATH}`); logSha = f.sha; const remote = JSON.parse(b64d(f.content)); S.log.food = mergeFood(S.log.food, remote.food); saveLocal(); } catch {}
    const body = JSON.stringify({ message: `planLAB: Matt's ticks ${todayIso()}`, content: b64e(JSON.stringify(S.log, null, 1)), ...(logSha ? { sha: logSha } : {}) });
    const r = await gh(`contents/${LOG_PATH}`, { method: "PUT", body });
    logSha = r.content.sha; setSync("on");
  } catch (e) {
    if ((e.status === 409 || e.status === 422) && retry) { try { const f = await gh(`contents/${LOG_PATH}`); logSha = f.sha; } catch {} pushing = false; return pushLog(false); }
    if (e.status === 403 || e.status === 404) { setSync("off"); render(); }
  }
  pushing = false;
}
// ---------- usage counts (log/engagement.json, shared with the Android app): what gets used, so Claude can make it easier
const ENG_PATH = `${DIR}/log/engagement.json`;
function eng(name) { const d = todayIso(), e = store.json("planlab-eng", {}); e[d] = e[d] || {}; e[d][name] = (e[d][name] || 0) + 1; store.set("planlab-eng", JSON.stringify(e)); }
async function pushEng() {
  const e = store.json("planlab-eng", {}); if (!S.token || S.sync === "off" || !Object.keys(e).length) return;
  store.set("planlab-eng", "{}");
  try {
    let doc = { v: 1, days: {} }, sha = null;
    try { const f = await gh(`contents/${ENG_PATH}`); sha = f.sha; doc = JSON.parse(b64d(f.content)); doc.days = doc.days || {}; } catch (x) { if (x.status !== 404) throw x; }
    for (const [d, ev] of Object.entries(e)) { doc.days[d] = doc.days[d] || {}; for (const [k, n] of Object.entries(ev)) doc.days[d][k] = (doc.days[d][k] || 0) + n; }
    const cut = addDays(todayIso(), -60); for (const d of Object.keys(doc.days)) if (d < cut) delete doc.days[d];
    doc.updated = Date.now();
    await gh(`contents/${ENG_PATH}`, { method: "PUT", body: JSON.stringify({ message: `planLAB: usage ${todayIso()}`, content: b64e(JSON.stringify(doc, null, 1)), ...(sha ? { sha } : {}) }) });
  } catch { const now = store.json("planlab-eng", {}); for (const [d, ev] of Object.entries(e)) { now[d] = now[d] || {}; for (const [k, n] of Object.entries(ev)) now[d][k] = (now[d][k] || 0) + n; } store.set("planlab-eng", JSON.stringify(now)); }
}
function touch() { S.log.updated = Date.now(); saveLocal(); schedulePush(); }

// ---------- plan helpers
const allDays = () => (S.plan?.weeks || []).flatMap(w => w.days);
const findDay = date => allDays().find(d => d.date === date);
const weekOf = date => (S.plan?.weeks || []).find(w => date >= w.start && date <= addDays(w.start, 6));
const bigDay = () => (S.plan?.calendar || []).find(e => e.big && daysBetween(todayIso(), e.date) > -60) || (S.plan?.calendar || []).find(e => e.big);
const bigName = e => e.what.replace(/\s*\(.*\)$/, "").toLowerCase();
function tickGet(list, item) { const t = S.log.ticks[list]; if (t && typeof t[item.key] === "boolean") return t[item.key]; if (item.steps?.length && item.steps.every(x => tickGet(list, x))) return true; return !!item.done; }
const withSteps = items => items.flatMap(i => [i, ...(i.steps || [])]);
const stepsDone = (list, item) => (item.steps || []).filter(x => tickGet(list, x)).length;
function tickPut(list, key, v) { S.log.ticks[list] = { ...(S.log.ticks[list] || {}), [key]: v }; touch(); }
function tickSet(list, item) { if (!tickGet(list, item)) eng("web_tick"); tickPut(list, item.key, !tickGet(list, item)); render(); }
const dayCount = d => ({ n: d.boxes.length, done: d.boxes.filter(b => tickGet(d.date, b)).length });
const inboxItems = () => (S.plan?.inbox || []).flatMap(g => g.items.map(i => ({ ...i, group: g.name })));
const cap = s => s ? s.charAt(0).toUpperCase() + s.slice(1) : "";
function phaseIndex(date = todayIso()) { const s = bigDay(); if (!s) return 0; const n = daysBetween(s.date, date); return n < 0 ? 0 : n <= 14 ? 1 : n <= 90 ? 2 : 3; }
function logMinutes(date, key, m) { S.log.mins[date] = { ...(S.log.mins[date] || {}), [key]: Math.round(((S.log.mins[date] || {})[key] || 0) + m) }; touch(); }
const minsOn = (date, key) => (S.log.mins[date] || {})[key] || 0;
function plannedMins(day, key) { const p = day?.practice.find(x => x.toLowerCase().startsWith(key)); return p ? mins(p) : 0; }
function workoutFor(day) {
  const W = S.plan?.program?.workouts || {};
  if (!day) return null;
  for (const k in W) if (day.body.toLowerCase().includes(W[k].name.toLowerCase())) return W[k];
  return null;
}
function dayProtein(day) { const m = day.eat.join(" ").match(/~?(\d+)\s*g protein/); return m ? +m[1] : 0; }
function streak(key) {
  let n = 0, d = todayIso();
  const box = date => findDay(date)?.boxes.find(b => b.key === key);
  if (!(box(d) && tickGet(d, box(d)))) d = addDays(d, -1);
  for (let i = 0; i < 400; i++) { const b = box(d); if (!b) { if (allDays().some(x => x.date < d)) { d = addDays(d, -1); continue; } break; } if (!tickGet(d, b)) break; n++; d = addDays(d, -1); }
  return n;
}
const SKIP = new Set(["chicken","breakfast","protein","turkey","black","bowls","thighs","overnight","masala","bean","sheet","lighter","smoky","freezer"]);
function recipeLinks(text) {
  let out = esc(text);
  for (const r of S.plan?.recipes || []) for (const w of r.slug.split("-").filter(w => w.length > 3 && !SKIP.has(w))) {
    const re = new RegExp(`\\b(${w}s?)\\b`, "i");
    if (re.test(out) && !out.includes(`data-recipe="${r.slug}"`)) { out = out.replace(re, `<a href="#food" data-recipe="${r.slug}">$1</a>`); break; }
  }
  return out;
}
const krogerQuery = t => t.replace(/\([^)]*\)/g, "").replace(/^\s*(\d+[\d./]*\s*(lb|lbs|oz|dozen|cups?|bunch|head|heads|piece|pack|packs|bag|tub|tubs|can|cans|box|roll|jar|bottle|carton|container|loaf|canister)?\s*)+/i, "").replace(/\s+/g, " ").trim();
function icsFor(e) {
  const d = e.date.replace(/-/g, ""), n = addDays(e.date, 1).replace(/-/g, "");
  const t = s => String(s).replace(/[,;\\]/g, m => "\\" + m);
  const ics = ["BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//planLAB//EN", "BEGIN:VEVENT", `UID:${d}-${keyOf(e.what)}@planlab`, `DTSTAMP:${new Date().toISOString().replace(/[-:]/g, "").slice(0, 15)}Z`, `DTSTART;VALUE=DATE:${d}`, `DTEND;VALUE=DATE:${n}`, `SUMMARY:${t(e.what)}`, e.notes ? `DESCRIPTION:${t(e.notes)}` : "", "END:VEVENT", "END:VCALENDAR"].filter(Boolean).join("\r\n");
  return URL.createObjectURL(new Blob([ics], { type: "text/calendar" }));
}

// ---------- timers (survive tab switches), beeps, wake lock
const T = { list: [], tick: null };
let actx = null;
function beep(times = 2) {
  try { actx = actx || new (window.AudioContext || window.webkitAudioContext)(); for (let i = 0; i < times; i++) { const o = actx.createOscillator(), g = actx.createGain(); o.frequency.value = 880; o.connect(g); g.connect(actx.destination); const t0 = actx.currentTime + i * 0.28; g.gain.setValueAtTime(0.0001, t0); g.gain.exponentialRampToValueAtTime(0.25, t0 + 0.02); g.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.22); o.start(t0); o.stop(t0 + 0.24); } } catch {}
  try { navigator.vibrate && navigator.vibrate([120, 80, 120]); } catch {}
}
function unlockAudio() { try { actx = actx || new (window.AudioContext || window.webkitAudioContext)(); actx.resume(); } catch {} }
let wake = null;
async function keepAwake(on) { try { if (on && !wake && navigator.wakeLock) wake = await navigator.wakeLock.request("screen"); if (!on && wake) { wake.release(); wake = null; } } catch {} }
function startTimer(label, seconds) { unlockAudio(); T.list.push({ id: Math.random().toString(36).slice(2), label, end: Date.now() + seconds * 1000, total: seconds }); runTimers(); }
function runTimers() {
  clearInterval(T.tick);
  if (!T.list.length) { drawTimers(); return; }
  T.tick = setInterval(() => { const now = Date.now(); for (const t of T.list) if (!t.rang && t.end <= now) { t.rang = true; beep(3); } drawTimers(); }, 500);
  drawTimers();
}
const fmt = s => { s = Math.max(0, Math.ceil(s)); return `${Math.floor(s / 60)}:${pad(s % 60)}`; };
function drawTimers() {
  const el = document.getElementById("timers"); if (!el) return;
  el.hidden = !T.list.length;
  el.innerHTML = T.list.map(t => { const left = (t.end - Date.now()) / 1000; return `<button class="tchip${left <= 0 ? " ring-done" : ""}" data-timer="${t.id}" aria-label="${esc(t.label)} timer, tap to clear">${IC.clock}<b>${left <= 0 ? "Done" : fmt(left)}</b><span>${esc(t.label)}</span>${IC.close}</button>`; }).join("");
}

// ---------- icons (one stroke family, 24px grid)
const I = (d, w = 2) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${d}</svg>`;
const IC = {
  check: I('<path d="M5 12.5l4.5 4.5L19 7.5"/>', 3),
  left: I('<path d="M15 5l-7 7 7 7"/>', 2.4), right: I('<path d="M9 5l7 7-7 7"/>', 2.4), close: I('<path d="M6 6l12 12M18 6L6 18"/>', 2.4),
  meals: I('<path d="M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10M17 3c-2 1.5-3 4-3 7h3v11"/>'),
  body: I('<circle cx="12" cy="4.5" r="2"/><path d="M5 9l7 1.5L19 9M12 10.5V15l-3.5 6M12 15l3.5 6"/>'),
  piano: I('<rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="M8 5v8M12 5v8M16 5v8M6.5 13h3M10.5 13h3M14.5 13h3M8 13v6M12 13v6M16 13v6"/>', 1.7),
  voice: I('<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21"/>'),
  today: I('<circle cx="12" cy="12" r="8.5"/><path d="M8 12.5l2.8 2.8L16.5 9.5"/>'),
  week: I('<rect x="3.5" y="5" width="17" height="15" rx="3.5"/><path d="M3.5 10h17M8 3v4M16 3v4"/>'),
  soon: I('<path d="M12 3.5l2.5 5.2 5.7.8-4.1 4 1 5.6-5.1-2.7-5.1 2.7 1-5.6-4.1-4 5.7-.8z"/>'),
  food: I('<path d="M4 11h16a8 8 0 0 1-16 0zM8 4c0 2 2 2 2 4M13 4c0 2 2 2 2 4"/>'),
  you: I('<circle cx="12" cy="8" r="4"/><path d="M4.5 20.5a7.5 7.5 0 0 1 15 0"/>'),
  plus: I('<path d="M12 5v14M5 12h14"/>', 2.4), minus: I('<path d="M5 12h14"/>', 2.4),
  heart: I('<path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"/>'),
  clock: I('<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>'),
  refresh: I('<path d="M20 11a8 8 0 1 0-2.3 5.7M20 5v6h-6"/>'),
  key: I('<circle cx="8" cy="15" r="4"/><path d="M11 12l8-8M16 7l2 2M14 9l2 2"/>'),
  paste: I('<rect x="6" y="4" width="12" height="17" rx="2.5"/><path d="M9 4V3h6v1M9 11h6M9 15h4"/>'),
  play: I('<path d="M7 4.5v15l12-7.5z"/>'), pause: I('<path d="M8 5v14M16 5v14"/>', 2.6), skip: I('<path d="M6 5l9 7-9 7zM18 5v14"/>'),
  search: I('<circle cx="11" cy="11" r="6.5"/><path d="M16 16l4.5 4.5"/>'),
  calplus: I('<rect x="3.5" y="5" width="17" height="15" rx="3.5"/><path d="M3.5 10h17M8 3v4M16 3v4M12 13v5M9.5 15.5h5"/>'),
  copy: I('<rect x="8" y="8" width="12" height="12" rx="2.5"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"/>'),
  snow: I('<path d="M12 3v18M4.2 7.5l15.6 9M4.2 16.5l15.6-9M9.5 4.5L12 7l2.5-2.5M9.5 19.5L12 17l2.5 2.5"/>', 1.8),
  flame: I('<path d="M12 21a6 6 0 0 0 6-6c0-4-3-6-4-10-2 2-3 4-3 6-1-1-1.5-2-1.5-3C7 10 6 12.5 6 15a6 6 0 0 0 6 6z"/>'),
  flag: I('<path d="M5 21V4M5 4h11l-2 4 2 4H5"/>'),
  sync: I('<path d="M4 12a8 8 0 0 1 13.7-5.7M20 4v5h-5M20 12a8 8 0 0 1-13.7 5.7M4 20v-5h5"/>'),
  map: I('<path d="M9 4L3.5 6v14L9 18l6 2 5.5-2V4L15 6zM9 4v14M15 6v14"/>'),
};
const TABS = [["today", "Today"], ["week", "Week"], ["soon", "Coming up"], ["food", "Food"], ["you", "You"]];
const KIND = { body: "Body", piano: "Piano", voice: "Voice" };

// ---------- small pieces
const ring = (done, n, size = 64) => {
  const r = size / 2 - 5, c = 2 * Math.PI * r, f = n ? Math.min(1, done / n) : 0;
  return `<svg class="ring" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" aria-hidden="true"><circle cx="${size/2}" cy="${size/2}" r="${r}" class="ring-bg"/><circle cx="${size/2}" cy="${size/2}" r="${r}" class="ring-fg${f === 1 ? " full" : ""}" stroke-dasharray="${c}" stroke-dashoffset="${c * (1 - f)}" transform="rotate(-90 ${size/2} ${size/2})"/></svg>`;
};
const pill = (label, tone = "") => `<span class="pill ${tone}">${label}</span>`;
const lab = t => `<h2 class="lab">${t}</h2>`;
const hero = (eyebrow, title, extra = "") => `<header class="hero"><div class="hero-t"><p class="eyebrow">${eyebrow}</p><h1>${title}</h1>${extra}</div></header>`;
function bigPill(date) {
  const s = bigDay(); if (!s) return "";
  const n = daysBetween(date, s.date), name = bigName(s);
  if (n > 1) return pill(`${n} days to ${esc(name)}`, "warm");
  if (n === 1) return pill(`${esc(cap(name))} tomorrow`, "warm");
  if (n === 0) return pill(`${esc(cap(name))} today`, "warm");
  return pill(`Day ${-n} after ${esc(name)}`, "soft");
}
const checkRow = (attrs, checked, label, sub, tail = "") => `<div class="row has-tail"><button class="check rowbtn" role="checkbox" aria-checked="${checked}" ${attrs}><span class="box">${IC.check}</span><span class="rt"><span class="rl">${label}</span>${sub ? `<span class="rs">${sub}</span>` : ""}</span></button>${tail}</div>`;
const unset = v => !v || /^\(.*\)$/.test(v) || /\?\)?$/.test(v);
function tileDetail(day, key) {
  const sb = day.boxes.find(b => b.key === key);
  if (sb?.steps?.length && !tickGet(day.date, sb)) { const nx = sb.steps.find(x => !tickGet(day.date, x)); return `${stepsDone(day.date, sb)}/${sb.steps.length} · ${esc(nx.label)}`; }
  if (key === "meals") { const p = dayProtein(day), t = S.plan?.profile?.protein; return p ? `${p} g protein${t ? ` of ${t}` : ""}` : `${day.eat.length} meals`; }
  if (key === "body") { const w = workoutFor(day); return esc(w ? `${w.moves.length} moves · ${w.minutes || mins(day.body)} min` : day.body.replace(/\s*\(.*\)/, "")); }
  const done = minsOn(day.date, key), plan = plannedMins(day, key);
  return done ? `${done} of ${plan || "?"} min` : plan ? `${plan} min · tap to start` : "";
}
const MEAL = ["Breakfast", "Lunch", "Dinner", "Also"];

// ---------- views
function viewSetup() {
  const step = S.setupStep || 1;
  return `<section class="setup">
    <div class="setup-mark">${IC.today}</div>
    <h1>planLAB</h1>
    <p class="lead">Your plan for today, this week and what's coming up.</p>
    <ol class="steps">
      <li class="${step === 1 ? "on" : "done"}"><span class="num">${step > 1 ? IC.check : "1"}</span><div><b>Make your key</b><span>GitHub opens with everything filled in. Pick <b>GeneralStorage</b> under repository access, tap Generate, then Copy.</span>
        <a class="btn" href="${KEY_RW}" target="_blank" rel="noopener" data-step="2">${IC.key} Make my key</a></div></li>
      <li class="${step === 2 ? "on" : ""}"><span class="num">2</span><div><b>Paste it here</b><span>It stays on this phone. You only do this once.</span>
        <form id="keyform" class="field"><input id="key-input" type="password" placeholder="github_pat_…" autocomplete="off" aria-label="Your GitHub key"><button type="button" class="btn ghost icon" id="paste" aria-label="Paste">${IC.paste}</button></form>
        <button class="btn" form="keyform">Open my plan</button></div></li>
    </ol>
    ${S.error ? `<p class="toast-inline" role="status">${esc(S.error)}</p>` : ""}
  </section>`;
}

function viewToday() {
  const date = S.day, day = findDay(date), t = todayIso();
  const title = date === t ? "Today" : date === addDays(t, 1) ? "Tomorrow" : date === addDays(t, -1) ? "Yesterday" : nice(date, { weekday: "long" });
  const c = day ? dayCount(day) : { n: 0, done: 0 }, all = c.n && c.done === c.n;
  let h = `<header class="hero">
    <div class="hero-t"><p class="eyebrow">${nice(date, { weekday: "long", month: "long", day: "numeric" })}</p><h1>${title}</h1><div class="pills">${bigPill(date)}${date !== t ? `<button class="pill btnpill" data-jump="${t}">Back to today</button>` : ""}</div></div>
    ${day ? `<div class="hero-ring${all ? " pop" : ""}">${ring(c.done, c.n, 76)}<span class="ring-n">${all ? IC.check : `${c.done}<small>/${c.n}</small>`}</span></div>` : ""}
    <div class="daynav"><button class="iconbtn" data-go="-1" aria-label="Previous day">${IC.left}</button><button class="iconbtn" data-go="1" aria-label="Next day">${IC.right}</button></div>
  </header>`;
  if (!day) {
    const last = allDays().at(-1);
    return h + `<div class="empty">${IC.week}<p>${!S.plan ? "Loading your plan…" : last && date > last.date ? "Not planned yet. Next week's plan arrives Sunday." : "Nothing planned for this day."}</p></div>`;
  }
  if (S.sync === "off" && !store.get("planlab-syncnag")) h += `<div class="card nag"><span class="tag-ic">${IC.sync}</span><span class="rt"><span class="rl">Let Claude see your ticks</span><span class="rs">Make a new key with saving on, then paste it.</span></span><a class="btn small" href="${KEY_RW}" target="_blank" rel="noopener" id="nag-go">New key</a><button class="tailbtn" id="nag-x" aria-label="Not now">${IC.close}</button></div>`;
  if (all) h += `<p class="cheer">All done${date === t ? " today" : ""}. Nice work.</p>`;
  else if (!store.get("planlab-tip2")) h += `<p class="tip">Tap a card to start it. Tap its circle when it's done.</p>`;
  const main = day.boxes.filter(b => KIND[b.key]), other = day.boxes.filter(b => !KIND[b.key]);
  h += `<div class="tiles">${main.map(b => { const on = tickGet(date, b);
    return `<div class="tile k-${b.key}${on ? " on" : ""}"><button class="tile-main" data-open="${b.key}" aria-label="Open ${KIND[b.key]}">
      <span class="tile-ic">${IC[b.key]}</span><span class="tile-name">${KIND[b.key]}</span><span class="tile-sub">${tileDetail(day, b.key)}</span></button>
      <button class="tile-check" role="checkbox" aria-checked="${on}" aria-label="${KIND[b.key]} done" data-list="${date}" data-item="${b.key}"><span class="box">${IC.check}</span></button></div>`; }).join("")}</div>`;
  const stepped = day.boxes.filter(b => b.steps?.length);
  for (const b of stepped) { const n = stepsDone(date, b);
    h += `<section class="sec">${lab(`${esc(KIND[b.key] || cap(b.label))} <span class="cnt">${n === b.steps.length ? "done" : `${n} of ${b.steps.length}`}</span>`)}<div class="card list compact">${b.steps.map(x => checkRow(`data-list="${date}" data-item="${x.key}"`, tickGet(date, x), recipeLinks(cap(x.label)), "")).join("")}</div></section>`; }
  const plain = other.filter(b => !b.steps?.length);
  if (plain.length) h += `<div class="card list">${plain.map(b => checkRow(`data-list="${date}" data-item="${b.key}"`, tickGet(date, b), recipeLinks(cap(b.label)), "")).join("")}</div>`;
  const pf = S.plan?.program?.daily;
  if (pf) h += `<button class="card strip-card" data-open="pelvic"><span class="tag-ic">${IC.heart}</span><span class="rt"><span class="rl">${esc(cap(pf.title.replace(/\s*\(.*\)/, "")))}</span><span class="rs">Twice a day · ${pf.reps.map(r => `${r.n} ${r.name}`).join(", ")}</span></span>${pill(`${(S.log.ticks["pf-" + date] || {}).n || 0}/2`, ((S.log.ticks["pf-" + date] || {}).n || 0) >= 2 ? "ok" : "soft")}</button>`;
  h += viewEat(day, date);
  if (!day.meals?.length && day.eat.length) h += `<section class="sec">${lab("Meals")}<div class="card list">${day.eat.map((m, i) => `<div class="row"><span class="tag">${MEAL[Math.min(i, 3)]}</span><span class="rt"><span class="rl n">${recipeLinks(m.replace(/\s*\(~?\d+ g protein\)/, ""))}</span></span></div>`).join("")}</div></section>`;
  const extra = day.practice.filter(p => !day.boxes.some(b => p.toLowerCase().startsWith(b.key)));
  const radar = [...day.coming, ...extra.map(x => cap(x) + " (if you feel like it)")];
  const due = inboxItems().filter(i => i.date === date);
  const ev = (S.plan?.calendar || []).filter(e => e.date === date);
  if (due.length || radar.length || ev.length) {
    h += `<section class="sec">${lab("On your radar")}<div class="card list">
      ${ev.map(e => `<div class="row"><span class="dot warm"></span><span class="rt"><span class="rl">${esc(e.what)}</span>${e.notes ? `<span class="rs">${esc(e.notes)}</span>` : ""}</span></div>`).join("")}
      ${due.map(i => checkRow(`data-list="inbox" data-item="${i.key}"`, tickGet("inbox", i), esc(i.what), i.steps?.length ? `Due today · ${stepsDone("inbox", i)} of ${i.steps.length} steps` : "Due today") + (i.steps || []).map(x => checkRow(`data-list="inbox" data-item="${x.key}"`, tickGet("inbox", x), "↳ " + esc(x.text), "")).join("")).join("")}
      ${radar.map(x => `<div class="row"><span class="dot"></span><span class="rt"><span class="rl n">${recipeLinks(cap(x))}</span></span></div>`).join("")}</div></section>`;
  }
  return h;
}

// ---------- Eat: tracked, not ticked. Planned meals are one tap; anything else is typed and looked up on the spot.
let FOODDB = null;
fetch("data/foods.json").then(r => r.json()).then(j => { FOODDB = PlanFood.table(j); if (S.tab === "today") render(); }).catch(() => {});
const mineFoods = () => { const t = S.plan?.foodsMd || ""; if (mineFoods.t !== t) { mineFoods.t = t; mineFoods.v = PlanFood.mine(t); } return mineFoods.v; };
const foodOn = date => Object.entries(S.log.food[date] || {}).filter(([, e]) => e).map(([id, e]) => ({ id, ...e })).sort((a, b) => (a.at + a.id).localeCompare(b.at + b.id));
const foodTargets = () => PlanFood.targets(S.plan?.profileMd, S.plan?.profile?.protein);
const fmtN = n => Math.round(n).toLocaleString("en-US");
const nowAt = () => { const d = new Date(); return `${pad(d.getHours())}:${pad(d.getMinutes())}`; };
const newId = () => Date.now().toString(36) + Math.floor(1000 + Math.random() * 9000);
function putFood(date, id, e) { S.log.food[date] = { ...(S.log.food[date] || {}), [id]: e }; touch(); }
const plannedOf = step => PlanFood.planned(step.label, FOODDB, mineFoods());
function macroBar(label, have, want, unit, tone) { const f = want ? Math.min(1, have / want) : 0; return `<div class="mac"><span class="mac-l ${tone}">${label}</span><span class="mac-n">${fmtN(have)} / ${fmtN(want)} ${unit}</span><div class="bar"><i class="${tone}" style="width:${(f * 100).toFixed(1)}%"></i></div></div>`; }
function eatTotals(date) {
  const have = PlanFood.totals(foodOn(date)), want = foodTargets();
  return `<div class="eat-top"><div class="hero-ring eat-ring">${ring(have.kcal, want.kcal, 88)}<span class="ring-n">${fmtN(have.kcal)}<small>of ${fmtN(want.kcal)}</small></span></div>
    <div class="macs">${macroBar("Protein", have.protein_g, want.protein_g, "g", "t-eat")}${macroBar("Carbs", have.carbs_g, want.carbs_g, "g", "t-acc")}${macroBar("Fat", have.fat_g, want.fat_g, "g", "t-fat")}</div></div>`;
}
function viewEat(day, date) {
  const list = foodOn(date);
  const planned = (day.meals || []).map(x => {
    const p = plannedOf(x);
    if (!p.meal) return checkRow(`data-list="${date}" data-item="${x.key}"`, tickGet(date, x), esc(cap(x.label)), "");
    const on = list.some(e => e.plan === x.key);
    return checkRow(`data-ate="${esc(x.key)}"`, on, `${cap(p.meal)} · ${esc(p.name)}`, `${p.protein_g} g protein · ${p.src === "plan-est" ? "~" : ""}${fmtN(p.kcal)} kcal${on ? " · logged" : " · tap when eaten"}`);
  }).join("");
  const extra = list.filter(e => !e.plan).map(e => `<div class="row has-tail"><span class="dot eat"></span><span class="rt"><span class="rl">${esc(e.name)}${e.qty !== 1 ? ` ×${+(+e.qty).toFixed(2)}` : ""}</span><span class="rs">${esc(e.at)} · ${fmtN(e.kcal)} kcal · ${e.protein_g} g protein${e.src === "guess" ? " · not found, Claude checks it" : ""}</span></span><button class="tailbtn" data-delfood="${esc(e.id)}" aria-label="Remove">${IC.close}</button></div>`).join("");
  return `<section class="sec">${lab("Eat")}<div class="card eat">${eatTotals(date)}
    ${planned || extra ? `<div class="list compact">${planned}${extra}</div>` : ""}
    <form id="foodform" class="foodform"><input id="food-text" autocomplete="off" placeholder="What did you eat? e.g. skyline 3-way, 2 coneys" aria-label="What did you eat?"><div id="food-preview"></div></form></div></section>`;
}
let FOODROWS = [], foodTimer = null;
const OFF = {};
function foodPreview() {
  const box = document.getElementById("food-preview"), inp = document.getElementById("food-text"); if (!box || !inp) return;
  const text = inp.value.trim();
  if (!text || !FOODDB) { FOODROWS = []; box.innerHTML = text && !FOODDB ? `<p class="rs">Loading the food table…</p>` : ""; return; }
  FOODROWS = PlanFood.lookup(text, FOODDB, mineFoods()).map(r => !r.food && OFF[r.text]?.length ? { ...r, food: OFF[r.text][0], alts: OFF[r.text], scale: r.qty } : r);
  redrawRows();
  // Anything the tables don't know: ask Open Food Facts once typing pauses.
  clearTimeout(foodTimer);
  const missing = FOODROWS.filter(r => !r.food && OFF[r.text] === undefined);
  if (missing.length) foodTimer = setTimeout(async () => { for (const r of missing) OFF[r.text] = await PlanFood.openFoodFacts(r.text).catch(() => []); foodPreview(); }, 900);
}
function redrawRows() {
  const box = document.getElementById("food-preview"); if (!box) return;
  const tot = FOODROWS.reduce((t, r) => (r.food ? { k: t.k + r.food.kcal * r.scale, p: t.p + r.food.p * r.scale } : t), { k: 0, p: 0 });
  box.innerHTML = FOODROWS.map((r, i) => { const f = r.food;
    return `<div class="row frow"><button class="rt fpick" data-falt="${i}" ${r.alts.length > 1 ? "" : "disabled"}><span class="rl${f ? "" : " warnc"}">${esc(f ? (f.brand ? f.brand + " " : "") + f.name : r.text)}</span>
      <span class="rs">${f ? `${fmtN(f.kcal * r.scale)} kcal · ${Math.round(f.p * r.scale)} g protein · ${+r.scale.toFixed(2)} × ${esc(f.serving)}${r.alts.length > 1 ? " · tap for others" : ""}` : OFF[r.text] === undefined ? "Looking it up…" : "Not found. Logged as a guess; Claude fills it in tomorrow."}</span></button>
      ${f ? `<button class="stp" data-fq="${i}" data-d="-1" aria-label="Less">−</button><span class="fq">${+r.scale.toFixed(2)}</span><button class="stp" data-fq="${i}" data-d="1" aria-label="More">+</button>` : ""}</div>`; }).join("")
    + (FOODROWS.length ? `<button class="btn wide eatbtn" form="foodform">Log it · ${fmtN(tot.k)} kcal · ${Math.round(tot.p)} g protein</button>` : "");
}
document.addEventListener("input", e => { if (e.target.id === "food-text") foodPreview(); });
function logFood() {
  const date = S.day, meal = PlanFood.mealNow(new Date()), at = nowAt();
  if (!FOODROWS.length) return;
  for (const r of FOODROWS) { const id = newId() + Math.floor(Math.random() * 10); putFood(date, id, PlanFood.entryOf(r, { at, meal, plan: "" })); }
  eng("web_food"); FOODROWS = []; const inp = document.getElementById("food-text"); if (inp) inp.value = ""; render();
}
function atePlanned(key) {
  const date = S.day, day = findDay(date), step = day?.meals?.find(x => x.key === key); if (!step) return;
  const had = foodOn(date).find(e => e.plan === key);
  if (had) { putFood(date, had.id, null); tickPut(date, key, false); render(); return; }
  const p = plannedOf(step);
  putFood(date, newId(), { at: nowAt(), text: step.label, name: p.name, qty: 1, kcal: p.kcal, protein_g: p.protein_g, carbs_g: p.carbs_g, fat_g: p.fat_g, src: p.src, meal: p.meal || PlanFood.mealNow(new Date()), plan: key });
  eng("web_food:plan"); tickPut(date, key, true); render();
}

function viewWeek() {
  const w = weekOf(S.day) || (S.plan?.weeks || []).at(-1);
  let h = hero(w ? `${nice(w.start, { month: "short", day: "numeric" })} – ${nice(addDays(w.start, 6), { month: "short", day: "numeric" })}` : "", "This week", w?.phase ? `<div class="pills">${pill(esc(cap(w.phase.replace(/\s*\(.*\)/, ""))), "soft")}</div>` : "");
  if (!w) return h + `<div class="empty">${IC.week}<p>${!S.plan ? "Loading your plan…" : "No week planned yet."}</p></div>`;
  const t = todayIso(), sel = w.days.find(d => d.date === S.day) || w.days.find(d => d.date === t) || w.days[0];
  h += `<div class="strip">${w.days.map(d => { const c = dayCount(d);
    return `<button class="dayp${d.date === sel.date ? " sel" : ""}${d.date === t ? " now" : ""}" data-pick="${d.date}" aria-pressed="${d.date === sel.date}"><span>${esc(d.label.slice(0, 1))}</span>${ring(c.done, c.n, 38)}<b>${parse(d.date).getDate()}</b></button>`; }).join("")}</div>`;
  if (w.focus) h += `<p class="focus">${esc(cap(w.focus))}</p>`;
  if (sel) {
    const c = dayCount(sel);
    h += `<button class="card daycard" data-jump="${sel.date}"><span class="dc-h"><b>${esc(nice(sel.date, { weekday: "long" }))}</b>${pill(`${c.done} of ${c.n}`, c.done === c.n && c.n ? "ok" : "soft")}</span>
      <span class="dc-r">${IC.body}<span>${esc(sel.body)}</span></span><span class="dc-r">${IC.meals}<span>${esc(sel.eat.at(-1) || "")}</span></span>
      ${sel.coming.length ? `<span class="dc-r">${IC.soon}<span>${esc(cap(sel.coming[0]))}</span></span>` : ""}<span class="dc-go">Open day ${IC.right}</span></button>`;
  }
  // week totals
  const tot = k => w.days.reduce((a, d) => a + minsOn(d.date, k), 0), plan = k => w.days.reduce((a, d) => a + plannedMins(d, k), 0);
  const bodyDone = w.days.filter(d => d.boxes.some(b => b.key === "body" && tickGet(d.date, b))).length, bodyN = w.days.filter(d => d.boxes.some(b => b.key === "body")).length;
  h += `<div class="stats3"><div class="card s3">${IC.body}<b>${bodyDone}<small>/${bodyN}</small></b><span>workouts</span></div><div class="card s3">${IC.piano}<b>${tot("piano")}<small>/${plan("piano")}</small></b><span>piano min</span></div><div class="card s3">${IC.voice}<b>${tot("voice")}<small>/${plan("voice")}</small></b><span>voice min</span></div></div>`;
  const G = { "With Elizabeth": IC.heart, Cooking: IC.meals, Groceries: IC.food };
  if (w.glance.length) h += `<section class="sec">${lab("At a glance")}<div class="card list">${w.glance.map(g => `<div class="row"><span class="tag-ic">${G[g.label] || IC.clock}</span><span class="rt"><span class="rl">${esc(g.label.replace(/\s*\(.*\)/, ""))}</span><span class="rs">${recipeLinks(g.text)}</span></span></div>`).join("")}</div></section>`;
  const wi = S.plan.weeks.indexOf(w);
  h += `<div class="seg two">${wi > 0 ? `<button data-pick="${S.plan.weeks[wi-1].start}">${IC.left} Last week</button>` : "<span></span>"}${wi < S.plan.weeks.length - 1 ? `<button data-pick="${S.plan.weeks[wi+1].start}">Next week ${IC.right}</button>` : "<span></span>"}</div>`;
  return h;
}

function viewSoon() {
  const t = todayIso(), s = bigDay(), n = s ? daysBetween(t, s.date) : -1;
  let h = hero("What's ahead", "Coming up");
  if (s && n >= 0) h += `<div class="card count"><span class="count-n">${n}</span><span class="count-t"><b>${n === 0 ? esc(cap(bigName(s))) + " is today" : (n === 1 ? "day to " : "days to ") + esc(bigName(s))}</b><span>${esc(nice(s.date, { weekday: "long", month: "short", day: "numeric" }))}</span></span><a class="iconbtn" href="#" data-ics="${s.date}|${esc(s.what)}" aria-label="Add to calendar">${IC.calplus}</a></div>`;
  h += `<form class="card wish" id="capture"><label for="capture-text" class="lab">Want to do something?</label><div class="field"><input id="capture-text" placeholder="See Goose in the spring" autocomplete="off"><button class="btn icon" id="capture-btn" aria-label="Add">${IC.plus}</button></div><p class="rs">${S.msg ? esc(S.msg) : "Claude gives it a date the next morning."}</p></form>`;
  const before = inboxItems().filter(i => /^before/i.test(i.group)), setup = S.plan?.setup || [];
  if (n >= 0 && (before.length || setup.length)) {
    const all = [...before.map(i => ["inbox", i, i.what, i.when]), ...setup.map(i => ["setup", i, i.text, ""])];
    const open = all.filter(([l, i]) => !tickGet(l, i)), done = all.length - open.length;
    h += `<section class="sec"><div class="labrow">${lab(s ? "Before " + esc(bigName(s)) : "Before then")}<span class="rs">${done} of ${all.length}</span></div><div class="bar"><i style="width:${all.length ? done / all.length * 100 : 0}%"></i></div>
      <div class="card list">${[...open, ...all.filter(([l, i]) => tickGet(l, i))].map(([l, i, text, when]) => checkRow(`data-list="${l}" data-item="${i.key}"`, tickGet(l, i), esc(text), esc(when))).join("")}</div></section>`;
  }
  const ev = (S.plan?.calendar || []).filter(e => e.date >= t);
  if (ev.length) h += `<section class="sec">${lab("Calendar")}<div class="card list">${ev.map(e => { const d = parse(e.date);
    return `<div class="row has-tail"><span class="date${daysBetween(t, e.date) <= 3 ? " soon" : ""}"><small>${d.toLocaleDateString("en-US", { month: "short" })}</small>${e.approx ? "~" : ""}${d.getDate()}</span><span class="rt"><span class="rl">${esc(e.what)}</span><span class="rs">${esc([e.who, e.notes].filter(Boolean).join(" · ") || d.toLocaleDateString("en-US", { weekday: "long" }))}</span></span><a class="tailbtn" href="#" data-ics="${e.date}|${esc(e.what)}|${esc(e.notes)}" aria-label="Add ${esc(e.what)} to calendar">${IC.calplus}</a></div>`; }).join("")}</div></section>`;
  const later = inboxItems().filter(i => !/^(before|open)/i.test(i.group));
  if (later.length) h += `<section class="sec">${lab("Later")}<div class="card list">${later.map(i => checkRow(`data-list="inbox" data-item="${i.key}"`, tickGet("inbox", i), esc(i.what), esc(i.when.replace(/\s*\(.*\)/, "")))).join("")}</div></section>`;
  const q = inboxItems().filter(i => /^open/i.test(i.group));
  const qs = (S.plan?.inbox || []).find(g => /^open/i.test(g.name));
  if (S.plan?.watching?.length) h += `<section class="sec">${lab("Claude is watching for")}<div class="card list">${S.plan.watching.map(w => `<div class="row"><span class="dot"></span><span class="rt"><span class="rl n">${esc(w)}</span></span></div>`).join("")}</div></section>`;
  return h;
}

function viewFood() {
  const mode = S.food || "list";
  let h = hero("Eat well, cook less", "Food") + `<div class="seg three" role="tablist">${[["list", "Kroger list"], ["recipes", "Recipes"], ["freezer", "Freezer"]].map(([k, l]) => `<button role="tab" aria-selected="${mode === k}" data-food="${k}">${l}</button>`).join("")}</div>`;
  if (mode === "list") {
    const g = (S.plan?.groceries || []).at(-1);
    if (!g) return h + `<div class="empty">${IC.food}<p>${!S.plan ? "Loading…" : "No list this week."}</p></div>`;
    const id = `groceries-${g.week}`, items = g.sections.flatMap(s => s.items), done = items.filter(i => tickGet(id, i)).length;
    h += `<div class="labrow"><span class="rs">${g.pickup ? "Pickup " + esc(g.pickup) : esc(g.week)}</span><span class="rs">${done} of ${items.length} in the cart</span></div><div class="bar"><i style="width:${items.length ? done / items.length * 100 : 0}%"></i></div>
      <div class="seg two"><button data-copylist="${id}">${IC.copy} Copy what's left</button><a class="segbtn" href="https://www.kroger.com/" target="_blank" rel="noopener">Open Kroger ${IC.right}</a></div>`;
    if (S.copied) h += `<p class="rs center">${esc(S.copied)}</p>`;
    for (const s of g.sections) {
      const left = s.items.filter(i => !tickGet(id, i)).length;
      const kr = i => `<a class="tailbtn" href="https://www.kroger.com/search?query=${encodeURIComponent(krogerQuery(i.text))}" target="_blank" rel="noopener" aria-label="Find ${esc(krogerQuery(i.text))} at Kroger">${IC.search}</a>`;
      h += `<section class="sec">${lab(`${esc(s.name)} <span class="cnt">${left ? left + " left" : "done"}</span>`)}<div class="card list compact">${[...s.items.filter(i => !tickGet(id, i)), ...s.items.filter(i => tickGet(id, i))].map(i => checkRow(`data-list="${id}" data-item="${i.key}"`, tickGet(id, i), esc(i.text), "", /^already/i.test(s.name) ? "" : kr(i))).join("")}</div></section>`;
    }
  } else if (mode === "recipes") {
    const rs = S.plan?.recipes || [];
    h += rs.length ? `<div class="recipes">${rs.map(r => `<button class="card rcard" data-recipe="${r.slug}"><span class="rc-p"><b>${r.protein ?? "?"}</b>g protein</span><span class="rc-t">${esc(r.title)}</span><span class="rc-m">${r.kcal ?? "?"} kcal${r.standing ? ` · ${esc(/none/i.test(r.standing) ? "no standing" : r.standing.split(/[;(,]/)[0].trim() + " standing")}` : ""}${/^yes/i.test(r.freezes || "") ? " · freezes" : ""}</span></button>`).join("")}</div>` : `<div class="empty">${IC.food}<p>${!S.plan ? "Loading…" : "No recipes yet."}</p></div>`;
  } else {
    const f = S.plan?.freezer || { items: [], planned: [], target: [] };
    const items = f.items.map(i => ({ ...i, left: Math.max(0, i.n + (S.log.freezer[i.key] || 0)) }));
    const total = items.reduce((a, i) => a + i.left, 0);
    h += `<div class="card count"><span class="count-n cool">${total}</span><span class="count-t"><b>${total === 1 ? "serving" : "servings"} in the freezer</b><span>${items.length ? `${items.length} kinds` : "Batch cooking fills it"}</span></span></div>`;
    if (items.length) h += `<section class="sec">${lab("What's in it")}<div class="card list">${items.map(i => `<div class="row has-tail"><span class="tag-ic">${IC.snow}</span><span class="rt"><span class="rl">${recipeLinks(i.name)}</span><span class="rs">${i.left} left${i.use ? ` · use by ${esc(i.use)}` : ""}</span></span><button class="tailbtn" data-freeze="${i.key}" data-d="-1" aria-label="Ate one">${IC.minus}</button><button class="tailbtn" data-freeze="${i.key}" data-d="1" aria-label="Add one">${IC.plus}</button></div>`).join("")}</div></section>`;
    if (f.planned.length) h += `<section class="sec">${lab("Batch cook plan")}<div class="card list">${f.planned.map(p => `<div class="row"><span class="tag">${esc(p.day)}</span><span class="rt"><span class="rl">${recipeLinks(p.cook)}</span><span class="rs">${esc(p.n)} to freeze</span></span></div>`).join("")}</div></section>`;
    if (f.target.length) h += `<section class="sec">${lab("Goal for the weeks off your feet")}<div class="card list">${f.target.map(x => `<div class="row"><span class="tag">${esc(x.meal)}</span><span class="rt"><span class="rl">${esc(x.planned)} of ${esc(x.need)}</span><span class="rs">${esc(x.from)}</span></span></div>`).join("")}</div></section>`;
  }
  return h;
}

function viewYou() {
  const P = S.plan; if (!P) return hero("", "You") + `<div class="empty">${IC.you}<p>Loading…</p></div>`;
  let h = hero("Progress and the long game", "You");
  h += `<section class="sec">${lab("Streaks")}<div class="tiles four">${Object.entries(KIND).map(([k, l]) => { const n = streak(k); return `<div class="card streak${n ? " hot" : ""}"><span class="st-ic">${n ? IC.flame : IC[k]}</span><b>${n}</b><span>${l}${n === 1 ? " · 1 day" : n ? ` · ${n} days` : ""}</span></div>`; }).join("")}</div></section>`;
  // practice minutes, last 7 days
  const days = [...Array(7)].map((_, i) => addDays(todayIso(), i - 6));
  const vals = days.map(d => minsOn(d, "piano") + minsOn(d, "voice")), mx = Math.max(30, ...vals), none = !vals.some(Boolean);
  h += `<section class="sec"><div class="labrow">${lab("Practice, last 7 days")}<span class="rs">${vals.reduce((a, b) => a + b, 0)} min</span></div><div class="card chart">${days.map((d, i) => `<div class="col"><span class="v">${vals[i] || ""}</span><span class="b" style="height:${vals[i] / mx * 100}%"><i style="height:${(minsOn(d, "voice") / (vals[i] || 1)) * 100}%"></i></span><span class="d">${parse(d).toLocaleDateString("en-US", { weekday: "narrow" })}</span></div>`).join("")}</div><p class="rs legend">${none ? "Start a piano or voice block from Today and your minutes show up here." : '<span class="sw p"></span>Piano <span class="sw v"></span>Voice'}</p></section>`;
  // now working on
  const nw = [["Piano", P.piano], ["Voice", P.voice]].flatMap(([n, m]) => (m?.now || []).map(x => [n, x]));
  if (nw.length) h += `<section class="sec">${lab("Now working on")}<div class="card list">${nw.map(([n, x]) => `<div class="row"><span class="tag">${n}</span><span class="rt"><span class="rl n">${esc(x.label)}</span><span class="rs">${unset(x.value) ? "Not set yet. Tell Claude in chat." : esc(x.value)}</span></span></div>`).join("")}</div></section>`;
  // recovery phase
  const pi = phaseIndex(), rec = P.recovery || [];
  if (rec.length) h += `<section class="sec">${lab("Recovery")}<div class="phases">${rec.map((r, i) => `<div class="card phase${i === pi ? " now" : ""}${i < pi ? " past" : ""}"><span class="ph-h"><b>${esc(r.phase)}</b>${i === pi ? pill("Now", "ok") : `<span class="rs">${esc(r.dates)}</span>`}</span>${i === pi ? `<span class="rs">${esc(r.dates)}</span><span class="ph-r"><span class="minus">Takes away</span>${esc(r.away)}</span><span class="ph-r"><span class="plus">Opens up</span>${esc(r.opens)}</span>` : ""}</div>`).join("")}</div></section>`;
  // goals
  const goals = P.program?.goals || [];
  if (goals.length) h += `<section class="sec">${lab("Goals to train for, once cleared")}<div class="card list">${goals.map(g => `<div class="row"><span class="tag-ic">${IC.flag}</span><span class="rt"><span class="rl">${esc(g.title)}</span>${g.text ? `<span class="rs">${esc(g.text)}</span>` : ""}</span></div>`).join("")}</div></section>`;
  // with Elizabeth
  const ideas = (P.ideas || [])[Math.min(pi, (P.ideas || []).length - 1)];
  if (ideas) h += `<section class="sec">${lab(`With Elizabeth · ${esc(ideas.name.replace(/\s*\(.*\)/, "").toLowerCase())}`)}<div class="chips">${ideas.items.map(x => `<span class="chip">${esc(x)}</span>`).join("")}</div></section>`;
  if (P.trips?.length) h += `<section class="sec">${lab("Trips")}<div class="card list">${P.trips.map(x => `<div class="row"><span class="tag-ic">${IC.map}</span><span class="rt"><span class="rl">${esc(x.idea)}</span><span class="rs">${esc(x.when)} · ${esc(x.why)}</span></span></div>`).join("")}</div></section>`;
  // sync
  h += `<section class="sec">${lab("Sync")}<div class="card list"><div class="row"><span class="tag-ic">${IC.sync}</span><span class="rt"><span class="rl">${S.sync === "on" ? "Ticks and minutes sync to your repo" : "Ticks stay on this phone"}</span><span class="rs">${S.sync === "on" ? "Claude sees what you did each morning." : "Make a new key with saving on, so Claude sees what you did."}</span></span></div>
    ${S.sync === "on" ? "" : `<div class="row"><a class="btn" href="${KEY_RW}" target="_blank" rel="noopener">${IC.key} Make a new key</a><button class="btn ghost" id="reset-key">Paste it</button></div>`}</div></section>`;
  return h;
}

// ---------- sheets: meals, workout, practice, pelvic floor, recipe/cook mode
const sheetWrap = (title, sub, body, cls = "") => `<div class="sheet-card ${cls}" role="dialog" aria-modal="true" aria-label="${esc(title)}"><div class="grab"></div>
  <div class="sheet-h"><div><p class="eyebrow">${sub}</p><h1>${esc(title)}</h1></div><button class="iconbtn" id="close-sheet" aria-label="Close">${IC.close}</button></div>${body}</div>`;
function openSheet(html) {
  const sh = document.getElementById("sheet"); sh.innerHTML = html; sh.hidden = false;
  requestAnimationFrame(() => sh.classList.add("open"));
  sh.querySelector(".sheet-card").scrollTop = 0; document.getElementById("close-sheet")?.focus();
}
function refreshSheet() { if (!SH.kind) return; const sh = document.getElementById("sheet"), card = sh.querySelector(".sheet-card"), top = card ? card.scrollTop : 0; sh.innerHTML = SH.draw(); const c2 = sh.querySelector(".sheet-card"); if (c2) c2.scrollTop = top; }
const SH = { kind: null, draw: null };
function show(kind, draw) { SH.kind = kind; SH.draw = draw; openSheet(draw()); }
function closeSheet() { const sh = document.getElementById("sheet"); sh.classList.remove("open"); SH.kind = null; stopSession(); keepAwake(false); setTimeout(() => { if (!SH.kind) sh.hidden = true; }, 220); render(); }
const doneBtn = (list, key, label) => { const day = findDay(list), b = day?.boxes.find(x => x.key === key); if (!b) return ""; const on = tickGet(list, b); return `<button class="btn wide${on ? " ok" : ""}" data-list="${list}" data-item="${key}">${on ? IC.check + " Done" : label}</button>`; };

function drawMeals() {
  const day = findDay(S.day); if (!day) return sheetWrap("Meals", "", "");
  const p = dayProtein(day), t = S.plan?.profile?.protein || 0;
  const recs = (S.plan?.recipes || []).filter(r => day.eat.some(m => recipeLinks(m).includes(`data-recipe="${r.slug}"`)));
  return sheetWrap("Eat", nice(day.date, { weekday: "long", month: "short", day: "numeric" }), `
    ${p ? `<div class="card pbar"><div class="labrow"><b>${p} g protein</b><span class="rs">${t ? `target ${t} g` : ""}</span></div><div class="bar big"><i style="width:${t ? Math.min(100, p / t * 100) : 100}%"></i></div>${t && p < t ? `<p class="rs">${t - p} g short: add a shake or a cottage cheese cup.</p>` : ""}</div>` : ""}
    <div class="card list">${day.eat.map((m, i) => `<div class="row"><span class="tag">${MEAL[Math.min(i, 3)]}</span><span class="rt"><span class="rl n">${recipeLinks(m.replace(/\s*\(~?\d+ g protein\)/, ""))}</span></span></div>`).join("")}</div>
    ${recs.length ? `${lab("Cooking today")}<div class="recipes">${recs.map(r => `<button class="card rcard" data-recipe="${r.slug}"><span class="rc-p"><b>${r.protein}</b>g protein</span><span class="rc-t">${esc(r.title)}</span><span class="rc-m">${esc(r.active || "")} active</span></button>`).join("")}</div>` : ""}
    ${doneBtn(day.date, "meals", "Ate to plan today")}`);
}

// a running session: workout timer or practice block
const SES = { on: false, kind: "", steps: [], i: 0, end: 0, paused: 0, started: 0, elapsed: 0, timer: null, sets: {} };
function stopSession() { clearInterval(SES.timer); SES.on = false; }
function sessionTick() {
  if (!SES.on || SES.paused) return;
  const left = (SES.end - Date.now()) / 1000;
  const el = document.getElementById("ses-left"); if (el) el.textContent = fmt(left);
  const rg = document.getElementById("ses-ring"); if (rg) { const st = SES.steps[SES.i], c = +rg.dataset.c; rg.style.strokeDashoffset = c * (Math.max(0, left) / (st.sec || 1)); }
  const br = document.getElementById("breath"); if (br) { const st = SES.steps[SES.i]; const cyc = st.inn + st.out, ph = ((st.sec - left) % cyc + cyc) % cyc; const inh = ph < st.inn; br.dataset.phase = inh ? "in" : "out"; br.querySelector("b").textContent = inh ? "In" : "Out"; br.style.transform = `scale(${inh ? 0.6 + 0.4 * (ph / st.inn) : 1 - 0.4 * ((ph - st.inn) / st.out)})`; }
  if (left <= 0) { beep(2); nextStep(); }
}
function startSession(kind, steps) { eng("web_session:" + kind); unlockAudio(); keepAwake(true); Object.assign(SES, { on: true, kind, steps, i: 0, paused: 0, started: Date.now(), elapsed: 0 }); SES.end = Date.now() + steps[0].sec * 1000; clearInterval(SES.timer); SES.timer = setInterval(sessionTick, 200); refreshSheet(); }
function nextStep() {
  SES.elapsed += SES.steps[SES.i].sec - Math.max(0, (SES.end - Date.now()) / 1000);
  SES.i++;
  if (SES.i >= SES.steps.length) { finishSession(); return; }
  SES.end = Date.now() + SES.steps[SES.i].sec * 1000; refreshSheet();
}
function togglePause() { if (SES.paused) { SES.end += Date.now() - SES.paused; SES.paused = 0; } else SES.paused = Date.now(); refreshSheet(); }
function finishSession() {
  stopSession(); keepAwake(false); beep(3);
  const m = Math.round(SES.elapsed / 60);
  if (SES.kind === "piano" || SES.kind === "voice") { if (m > 0) logMinutes(S.day, SES.kind, m); const b = findDay(S.day)?.boxes.find(x => x.key === SES.kind); if (b && m >= Math.max(1, plannedMins(findDay(S.day), SES.kind) * 0.6)) tickPut(S.day, SES.kind, true); SES.result = m > 0 ? `${m} min logged.` : "Session ended."; }
  if (SES.kind === "pelvic") { const k = "pf-" + S.day; S.log.ticks[k] = { n: ((S.log.ticks[k] || {}).n || 0) + 1 }; touch(); SES.result = "Round done."; }
  refreshSheet();
}
function sessionPanel() {
  if (!SES.on) return SES.result ? `<p class="cheer">${IC.check} ${esc(SES.result)}</p>` : "";
  const st = SES.steps[SES.i], left = SES.paused ? (SES.end - SES.paused) / 1000 : (SES.end - Date.now()) / 1000, size = 190, r = 84, c = 2 * Math.PI * r;
  return `<div class="card session">
    <p class="eyebrow">Step ${SES.i + 1} of ${SES.steps.length}</p><h2 class="ses-t">${esc(st.title)}</h2>${st.sub ? `<p class="rs">${esc(st.sub)}</p>` : ""}
    <div class="ses-ring">${st.inn ? `<div class="breath" id="breath"><b>In</b></div>` : ""}<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}"><circle cx="95" cy="95" r="${r}" class="ring-bg"/><circle id="ses-ring" data-c="${c}" cx="95" cy="95" r="${r}" class="ring-fg" stroke-dasharray="${c}" stroke-dashoffset="${c * Math.max(0, left) / (st.sec || 1)}" transform="rotate(-90 95 95)"/></svg><span id="ses-left">${fmt(left)}</span></div>
    <div class="ses-ctl"><button class="iconbtn big" id="ses-pause" aria-label="${SES.paused ? "Resume" : "Pause"}">${SES.paused ? IC.play : IC.pause}</button><button class="iconbtn big" id="ses-next" aria-label="Next step">${IC.skip}</button></div>
    <button class="link center" id="ses-stop">End session</button></div>`;
}

function practiceSteps(m, short) {
  if (short) { const st = m.block.find(b => /piece|section|song|phrase/i.test(b.what)) || m.block[0]; return st ? [{ title: "Short day", sub: m.short || st.what, sec: (mins(m.short) || st.min) * 60 }] : []; }
  return m.block.map(b => { const parts = b.what.split(":"), title = parts.length > 1 ? parts[0] : b.what.split(/[,.]/)[0], br = b.what.match(/(\d+)\s*counts? in,\s*(\d+)\s*counts? out/i);
    return { title: stripMd(title), sub: parts.length > 1 ? parts.slice(1).join(":").trim() : "", sec: b.min * 60, ...(br ? { inn: +br[1], out: +br[2] } : {}) }; });
}
function drawPractice(key) {
  const m = S.plan?.[key], day = findDay(S.day); if (!m) return sheetWrap(cap(key), "", "");
  const done = minsOn(S.day, key), plan = plannedMins(day, key) || m.blockMin;
  const now = (m.now || []).filter(x => !/next lesson/i.test(x.label));
  return sheetWrap(m.title || cap(key), `${plan} min block${m.teacher ? ` · with ${esc(m.teacher)}` : ""}`, `
    ${sessionPanel()}
    ${!SES.on ? `<div class="seg two"><button class="primary" data-start="${key}">${IC.play} Start ${plan} min</button>${m.short ? `<button data-start="${key}" data-short="1">Short day</button>` : "<span></span>"}</div>` : ""}
    ${done ? `<p class="rs center">${done} min logged today${plan ? ` of ${plan}` : ""}</p>` : ""}
    ${now.length ? `<div class="card list">${now.map(x => `<div class="row"><span class="tag">${esc(x.label)}</span><span class="rt"><span class="rl n">${unset(x.value) ? `<span class="rs">Not set yet. Tell Claude in chat.</span>` : esc(x.value)}</span></span></div>`).join("")}</div>` : ""}
    ${lab("The block")}<div class="card list">${m.block.map((b, i) => `<div class="row${SES.on && SES.kind === key && SES.i === i ? " cur" : ""}"><span class="tag">${b.min} min</span><span class="rt"><span class="rl n">${esc(b.what)}</span></span></div>`).join("")}</div>
    ${m.method ? `<p class="rs">${esc(m.method)}</p>` : ""}
    <div class="row-btns"><button class="btn ghost" data-addmin="${key}" data-m="5">${IC.plus} 5 min</button><button class="btn ghost" data-addmin="${key}" data-m="15">${IC.plus} 15 min</button></div>
    ${doneBtn(S.day, key, `Mark ${key} done`)}`);
}
function drawPelvic() {
  const pf = S.plan?.program?.daily; if (!pf) return sheetWrap("Pelvic floor", "", "");
  const n = (S.log.ticks["pf-" + S.day] || {}).n || 0;
  return sheetWrap(cap(pf.title.replace(/\s*\(.*\)/, "")), `Round ${Math.min(n + 1, 2)} of 2 today`, `
    ${sessionPanel()}
    ${!SES.on ? `<button class="btn wide" data-start="pelvic">${IC.play} Start guided round</button>` : ""}
    <div class="card list">${pf.reps.map(r => `<div class="row"><span class="tag">${r.n}×</span><span class="rt"><span class="rl">${esc(cap(r.name))}</span><span class="rs">${esc(r.how)}</span></span></div>`).join("")}</div>
    ${pf.notes.map(x => `<p class="rs">${esc(x)}</p>`).join("")}`);
}
function pelvicSteps() {
  const out = [];
  for (const r of S.plan.program.daily.reps) for (let i = 0; i < r.n; i++) { out.push({ title: `${cap(r.name).replace(/s$/, "")} ${i + 1} of ${r.n}`, sub: "Squeeze and lift", sec: r.on }); out.push({ title: "Relax", sub: "Let go fully", sec: r.off }); }
  return out;
}
function drawWorkout() {
  const day = findDay(S.day), w = workoutFor(day);
  if (!w) return sheetWrap("Body", nice(S.day, { weekday: "long" }), `<div class="card pad"><p>${esc(day?.body || "")}</p></div>${(S.plan?.program?.phases?.[phaseIndex()]?.bullets || []).length ? lab("This phase") + `<div class="card list">${S.plan.program.phases[phaseIndex()].bullets.map(b => `<div class="row"><span class="dot"></span><span class="rt"><span class="rl n">${esc(b)}</span></span></div>`).join("")}</div>` : ""}${doneBtn(S.day, "body", "Mark body done")}`);
  const sk = "sets-" + S.day, sets = S.log.ticks[sk] || {};
  const total = w.moves.reduce((a, m) => a + m.sets, 0), done = w.moves.reduce((a, m) => a + Math.min(m.sets, sets[m.key] || 0), 0);
  return sheetWrap(w.name, `${esc(w.title)}`, `
    <div class="card pbar"><div class="labrow"><b>${done} of ${total} sets</b><span class="rs">${w.minutes || mins(day.body)} min</span></div><div class="bar big"><i style="width:${total ? done / total * 100 : 0}%"></i></div></div>
    <div class="moves">${w.moves.map(m => { const d = Math.min(m.sets, sets[m.key] || 0), t = m.hold || m.time;
      return `<div class="card move${d >= m.sets ? " ok" : ""}"><div class="mv-h"><span class="rt"><span class="rl">${esc(m.name)}</span><span class="rs">${esc(m.rx)}</span></span>${t ? `<button class="tbtn" data-hold="${t}" data-label="${esc(m.name)}${m.perSide ? " (each side)" : ""}">${IC.clock}${fmt(t)}</button>` : ""}</div>
        <div class="setdots">${[...Array(m.sets)].map((_, i) => `<button class="sd${i < d ? " on" : ""}" data-set="${m.key}" data-n="${i < d ? i : i + 1}" aria-label="Set ${i + 1}${i < d ? " done" : ""}">${i < d ? IC.check : i + 1}</button>`).join("")}</div></div>`; }).join("")}</div>
    ${w.note ? `<p class="rs">${esc(w.note)}</p>` : ""}
    ${doneBtn(S.day, "body", "Workout done")}`);
}
function drawRecipe(slug) {
  const r = (S.plan?.recipes || []).find(x => x.slug === slug); if (!r) return sheetWrap("Recipe", "", "");
  const ck = S.cook[slug] || (S.cook[slug] = { ing: {}, step: {} });
  const stat = (v, u, l) => `<span class="stat"><b>${v ?? "?"}<small>${u}</small></b><span>${l}</span></span>`;
  const timerOf = s => { const m = s.match(/(\d+)(?:\s*to\s*(\d+))?\s*(min|h)\b/); if (!m) return 0; return (+(m[2] || m[1])) * (m[3] === "h" ? 3600 : 60); };
  const meta = [["Serves", r.serves], ["Active", r.active], ["Total", r.total], ["Standing", r.standing], ["Freezes", r.freezes], ["Serve with", r.with], ["Best for", r.best]].filter(x => x[1]);
  return sheetWrap(r.title, r.serving ? `Per serving: ${esc(r.serving)}` : "Recipe", `
    <div class="stats">${stat(r.protein, "g", "protein")}${stat(r.kcal, "", "kcal")}${stat(r.carbs, "g", "carbs")}${stat(r.fat, "g", "fat")}</div>
    <div class="card list compact">${meta.map(([k, v]) => `<div class="row"><span class="tag">${k}</span><span class="rt"><span class="rl n">${esc(v)}</span></span></div>`).join("")}</div>
    ${r.ingredients.length ? `<div class="labrow">${lab("Ingredients")}<span class="rs">${Object.values(ck.ing).filter(Boolean).length} of ${r.ingredients.length} out</span></div><div class="card list compact">${r.ingredients.map((x, i) => checkRow(`data-ing="${slug}" data-i="${i}"`, !!ck.ing[i], esc(x), "")).join("")}</div>` : ""}
    ${r.steps.length ? `${lab("Steps")}<div class="steps-list">${r.steps.map((x, i) => { const t = timerOf(x); return `<div class="card cstep${ck.step[i] ? " ok" : ""}"><button class="cs-n" data-cstep="${slug}" data-i="${i}" aria-label="Step ${i + 1} ${ck.step[i] ? "done" : ""}">${ck.step[i] ? IC.check : i + 1}</button><span class="rt"><span class="rl n">${esc(x)}</span>${t ? `<button class="tbtn" data-hold="${t}" data-label="${esc(r.title.split(" ").slice(-2).join(" "))}, step ${i + 1}">${IC.clock}Start ${fmt(t)}</button>` : ""}</span></div>`; }).join("")}</div>` : ""}
    ${r.notes.map(x => `<p class="rs">${esc(x)}</p>`).join("")}`);
}
S.cook = {};
function openKind(kind) {
  SES.result = "";
  if (kind === "meals") show("meals", drawMeals);
  else if (kind === "body") show("body", drawWorkout);
  else if (kind === "piano" || kind === "voice") show(kind, () => drawPractice(kind));
  else if (kind === "pelvic") show("pelvic", drawPelvic);
}

// ---------- render + events
function render() {
  const app = document.getElementById("app"), tabs = document.querySelector(".tabs");
  if (!S.token || (S.badKey && !S.plan)) { app.innerHTML = `<div class="wrap">${viewSetup()}</div>`; tabs.hidden = true; return; }
  // The Android app's setup opens #app: one tap hands this phone's key to the app (planlab://key), never to a server.
  if (location.hash === "#app") { app.innerHTML = `<div class="wrap"><section class="setup"><div class="setup-mark">${IC.today}</div><h1>planLAB app</h1>
    <p class="lead">Tap below and pick planLAB. Your key goes straight to the app on this phone.</p>
    <a class="btn" href="planlab://key#${encodeURIComponent(S.token)}">${IC.key} Send my key to the app</a>
    <p class="tip"><a href="#today">Back to the planner</a></p></section></div>`; tabs.hidden = true; return; }
  tabs.hidden = false;
  const view = { today: viewToday, week: viewWeek, soon: viewSoon, food: viewFood, you: viewYou }[S.tab];
  const draft = document.getElementById("capture-text")?.value, foodDraft = document.getElementById("food-text")?.value, foodFocus = document.activeElement?.id === "food-text";
  const when = S.plan?.built ? new Date(S.plan.built).toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" }) : "";
  const anim = S.lastTab !== S.tab ? " anim" : ""; S.lastTab = S.tab;
  app.innerHTML = `<div class="wrap v-${S.tab}${anim}">${view()}
    ${S.error ? `<p class="toast-inline" role="status">${esc(S.error)}${S.badKey ? ` <button class="link" id="reset-key">Set up a new key</button>` : ""}</p>` : ""}
    <p class="foot">${S.loading ? "Updating…" : when ? `Updated ${when}` : ""}${S.sync === "on" ? " · synced" : ""} <button class="link" id="refresh" aria-label="Refresh">${IC.refresh}</button></p></div>`;
  const ci = document.getElementById("capture-text"); if (ci && draft) ci.value = draft;
  const fi = document.getElementById("food-text"); if (fi && foodDraft) { fi.value = foodDraft; const rows = FOODROWS; foodPreview(); if (rows.length === FOODROWS.length) { FOODROWS = rows; redrawRows(); } } if (fi && foodFocus) fi.focus();
  document.getElementById("tabs").innerHTML = TABS.map(([k, l]) => `<button data-tab="${k}" ${S.tab === k ? 'aria-current="page"' : ""}>${IC[k]}<span>${l}</span></button>`).join("");
  if (SH.kind) refreshSheet();
  drawTimers();
}
const VALID = TABS.map(t => t[0]);
const fromHash = () => { const h = location.hash.slice(1); if (VALID.includes(h)) S.tab = h; };
fromHash();
addEventListener("hashchange", () => { fromHash(); render(); });
const goTab = k => { if (S.tab !== k) eng("web_tab:" + k); S.tab = k; history.replaceState(null, "", "#" + k); render(); scrollTo({ top: 0 }); };

document.addEventListener("click", async e => {
  const q = s => e.target.closest(s);
  let el;
  if ((el = q("[data-recipe]"))) { e.preventDefault(); const slug = el.dataset.recipe; show("recipe", () => drawRecipe(slug)); return; }
  if (q("#close-sheet") || e.target.id === "sheet") { closeSheet(); return; }
  if ((el = q("[data-timer]"))) { T.list = T.list.filter(t => t.id !== el.dataset.timer); runTimers(); return; }
  if ((el = q("[data-hold]"))) { startTimer(el.dataset.label, +el.dataset.hold); return; }
  if ((el = q("[data-open]"))) { store.set("planlab-tip2", "1"); openKind(el.dataset.open); return; }
  if ((el = q("[data-start]"))) { const k = el.dataset.start; const steps = k === "pelvic" ? pelvicSteps() : practiceSteps(S.plan[k], !!el.dataset.short); if (steps.length) startSession(k, steps); return; }
  if (q("#ses-pause")) { togglePause(); return; }
  if (q("#ses-next")) { nextStep(); return; }
  if (q("#ses-stop")) { finishSession(); return; }
  if ((el = q("[data-addmin]"))) { logMinutes(S.day, el.dataset.addmin, +el.dataset.m); render(); return; }
  if ((el = q("[data-set]"))) { const sk = "sets-" + S.day; S.log.ticks[sk] = { ...(S.log.ticks[sk] || {}), [el.dataset.set]: +el.dataset.n }; touch();
    const w = workoutFor(findDay(S.day)); if (w && w.moves.every(m => (S.log.ticks[sk][m.key] || 0) >= m.sets)) { tickPut(S.day, "body", true); beep(1); } render(); return; }
  if ((el = q("[data-ing]"))) { const c = S.cook[el.dataset.ing]; c.ing[el.dataset.i] = !c.ing[el.dataset.i]; refreshSheet(); return; }
  if ((el = q("[data-cstep]"))) { const c = S.cook[el.dataset.cstep]; c.step[el.dataset.i] = !c.step[el.dataset.i]; refreshSheet(); return; }
  if ((el = q("[data-freeze]"))) { const k = el.dataset.freeze; S.log.freezer[k] = (S.log.freezer[k] || 0) + +el.dataset.d; touch(); render(); return; }
  if ((el = q("[data-ics]"))) { e.preventDefault(); const [date, what, notes] = el.dataset.ics.split("|"); const a = document.createElement("a"); a.href = icsFor({ date, what, notes }); a.download = `${keyOf(what)}.ics`; document.body.appendChild(a); a.click(); a.remove(); return; }
  if ((el = q("[data-copylist]"))) { const g = S.plan.groceries.find(x => `groceries-${x.week}` === el.dataset.copylist); const txt = g.sections.filter(s => !/^already/i.test(s.name)).map(s => { const left = s.items.filter(i => !tickGet(el.dataset.copylist, i)); return left.length ? `${s.name}\n${left.map(i => "- " + i.text).join("\n")}` : ""; }).filter(Boolean).join("\n\n");
    try { await navigator.clipboard.writeText(txt); S.copied = "Copied. Paste it into Kroger's list or a note."; } catch { S.copied = "Couldn't copy on this phone."; } render(); setTimeout(() => { S.copied = ""; render(); }, 3500); return; }
  if (q("#nag-x")) { store.set("planlab-syncnag", "1"); render(); return; }
  if (q("#nag-go")) { setTimeout(() => { store.del("planlab-key"); S.token = null; S.setupStep = 2; render(); }, 400); return; }
  if (q("#refresh")) { load(); return; }
  if (q("#reset-key")) { store.del("planlab-key"); S.token = null; S.badKey = false; S.error = ""; S.setupStep = 2; render(); return; }
  if (q("[data-step]")) { S.setupStep = 2; setTimeout(render, 300); return; }
  if (q("#paste")) { try { const v = await navigator.clipboard.readText(); document.getElementById("key-input").value = v.trim(); } catch { document.getElementById("key-input").focus(); } return; }
  if ((el = q("[data-tab]"))) { if (el.dataset.tab === "today") S.day = todayIso(); goTab(el.dataset.tab); return; }
  if ((el = q("[data-food]"))) { S.food = el.dataset.food; render(); return; }
  if ((el = q("[data-go]"))) { S.day = addDays(S.day, +el.dataset.go); render(); return; }
  if ((el = q("[data-ate]"))) { try { navigator.vibrate && navigator.vibrate(8); } catch {} atePlanned(el.dataset.ate); return; }
  if ((el = q("[data-delfood]"))) { putFood(S.day, el.dataset.delfood, null); render(); return; }
  if ((el = q("[data-falt]"))) { const r = FOODROWS[+el.dataset.falt]; if (r?.alts.length > 1) { const j = (r.alts.indexOf(r.food) + 1) % r.alts.length; r.food = r.alts[j]; r.scale = r.qty; redrawRows(); } return; }
  if ((el = q("[data-fq]"))) { const r = FOODROWS[+el.dataset.fq]; if (r) { const s = r.scale; r.scale = +el.dataset.d > 0 ? (s < 1 ? s * 2 : s + 1) : (s > 1 ? s - 1 : Math.max(0.25, s / 2)); redrawRows(); } return; }
  if ((el = q("[data-pick]"))) { S.day = el.dataset.pick; render(); return; }
  if ((el = q("[data-jump]"))) { S.day = el.dataset.jump; goTab("today"); return; }
  if ((el = q("[data-list]"))) {
    const list = el.dataset.list, key = el.dataset.item;
    const pool = withSteps(list === "inbox" ? inboxItems() : list === "setup" ? S.plan.setup : list.startsWith("groceries-") ? (S.plan.groceries.find(g => `groceries-${g.week}` === list)?.sections.flatMap(s => s.items) || []) : (findDay(list)?.boxes || []));
    const item = pool.find(x => x.key === key); if (!item) return;
    store.set("planlab-tip2", "1");
    try { navigator.vibrate && navigator.vibrate(8); } catch {}
    tickSet(list, item);
  }
});
document.addEventListener("submit", async e => {
  e.preventDefault();
  if (e.target.id === "keyform") {
    const v = document.getElementById("key-input").value.trim(); if (!v) { document.getElementById("key-input").focus(); return; }
    S.token = v; S.badKey = false; S.error = ""; setSync("unknown"); store.set("planlab-key", v); render(); load(); return;
  }
  if (e.target.id === "foodform") { logFood(); return; }
  if (e.target.id !== "capture") return;
  const inp = document.getElementById("capture-text"), text = inp.value.trim(); if (!text) { inp.focus(); return; }
  const btn = document.getElementById("capture-btn"); btn.disabled = true;
  try {
    await gh("issues", { method: "POST", body: JSON.stringify({ title: `Wish: ${text}`, body: `Added from planLAB on ${nice(todayIso())}. Claude: give it a date in inbox.md, then close this.` }) });
    inp.value = ""; S.msg = `Added "${text}". Claude dates it tomorrow morning.`;
  } catch { S.msg = "Couldn't add it. Tell Claude in chat instead."; }
  btn.disabled = false; render();
});
document.addEventListener("keydown", e => { if (e.key === "Escape") closeSheet(); });

let lastToday = todayIso();
setInterval(() => { const t = todayIso(); if (t !== lastToday) { if (S.day === lastToday) S.day = t; lastToday = t; load(); } }, 60000);
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "hidden") { clearTimeout(pushTimer); pushLog(); pushEng(); return; }
  eng("web_open");
  if (S.token && (!S.plan || Date.now() - new Date(S.plan.built) > 15 * 60000)) load();
});

if (S.plan && (S.plan.v !== 2 || !S.plan.weeks?.[0]?.days?.[0]?.meals)) S.plan = null;
eng("web_open");
render();
load();
})();
