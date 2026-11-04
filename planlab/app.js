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
    for (let line of body.split("\n")) {
      line = line.trim(); if (!line.startsWith("- ")) continue; line = line.slice(2);
      if (line.startsWith("[")) { for (const b of line.matchAll(/\[( |x|X)\]\s*([^\[]+)/g)) { const label = b[2].trim(); d.boxes.push({ key: keyOf(label), label, done: b[1].toLowerCase() === "x" }); } continue; }
      const c = line.indexOf(":"); if (c < 0) continue;
      const k = line.slice(0, c).trim().toLowerCase(), v = line.slice(c + 1);
      const parts = v.split(" · ").filter(p => p.trim()).map(stripMd);
      if (k === "eat") d.eat = parts; else if (k === "body") d.body = stripMd(v); else if (k === "practice") d.practice = parts; else if (k === "coming up") d.coming = parts;
    }
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
    for (const line of body.split("\n")) { const m = line.match(/^\s*- \[( |x|X)\]\s*(.+)/); if (m) { const t = stripMd(m[2]); items.push({ key: keyOf(t), text: t, done: m[1].toLowerCase() === "x" }); } }
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
const S = { tab: "today", food: "list", setupStep: 1, day: todayIso(), plan: store.json("planlab-cache", null), ticks: store.json("planlab-ticks", {}), loading: false, error: "", token: store.get("planlab-key"), msg: "" };
async function gh(path, raw) {
  const r = await fetch(`https://api.github.com/repos/${REPO}/${path}`, { headers: { Authorization: `Bearer ${S.token}`, Accept: raw ? "application/vnd.github.raw+json" : "application/vnd.github+json" }, cache: "no-store" });
  if (r.status === 401 || r.status === 403 || r.status === 404) { const e = new Error("key"); e.status = r.status; throw e; }
  if (!r.ok) throw new Error("net");
  return raw ? r.text() : r.json();
}
const file = p => gh(`contents/${DIR}/${p}`, true).catch(e => { if (e.status === 404) return ""; throw e; });
const listDir = p => gh(`contents/${DIR}/${p}`).then(a => a.filter(f => f.name.endsWith(".md")).map(f => f.name.slice(0, -3))).catch(e => { if (e.status === 404) return []; throw e; });

async function load() {
  if (!S.token) return;
  S.loading = true; render();
  try {
    const [weekIds, groceryIds, recipeIds, cal, inbox, recovery] = await Promise.all([listDir("weeks"), listDir("groceries"), listDir("meals/recipes"), file("social/calendar.md"), file("inbox.md"), file("me/recovery.md")]);
    const weeks = (await Promise.all(weekIds.sort().map(async id => parseWeek(id, await file(`weeks/${id}.md`)))));
    const groceries = await Promise.all(groceryIds.sort().map(async id => { const t = await file(`groceries/${id}.md`); const p = t.match(/\*\*Pickup:\*\*\s*([^·\n]+)/); return { week: id, pickup: p ? stripMd(p[1]) : "", sections: parseChecklists(t) }; }));
    const recipes = await Promise.all(recipeIds.sort().map(async id => parseRecipe(id, await file(`meals/recipes/${id}.md`))));
    const c = parseCalendar(cal);
    const setup = parseChecklists(recovery)[0];
    S.plan = { built: new Date().toISOString(), weeks, calendar: c.events, watching: c.watching, inbox: parseInbox(inbox), setup: setup ? setup.items : [], groceries, recipes };
    store.set("planlab-cache", JSON.stringify(S.plan));
    S.error = "";
  } catch (e) {
    S.error = e.message === "key" ? "Your GitHub key didn't work. It may have expired or not include GeneralStorage. Make a new one below." : "Couldn't reach GitHub. Showing the last copy you loaded.";
    if (e.message === "key") { S.badKey = true; }
  }
  S.loading = false; render();
}

// ---------- plan helpers
function allDays() { return (S.plan?.weeks || []).flatMap(w => w.days); }
const findDay = date => allDays().find(d => d.date === date);
const weekOf = date => (S.plan?.weeks || []).find(w => date >= w.start && date <= addDays(w.start, 6));
const bigDay = () => (S.plan?.calendar || []).find(e => e.big && daysBetween(todayIso(), e.date) > -60);
const bigName = e => e.what.replace(/\s*\(.*\)$/, "").toLowerCase();
function tickGet(list, item) { const t = S.ticks[list]; return t && typeof t[item.key] === "boolean" ? t[item.key] : !!item.done; }
function tickSet(list, item) { S.ticks[list] = { ...(S.ticks[list] || {}), [item.key]: !tickGet(list, item) }; store.set("planlab-ticks", JSON.stringify(S.ticks)); render(); }
const dayCount = d => ({ n: d.boxes.length, done: d.boxes.filter(b => tickGet(d.date, b)).length });
const inboxItems = () => (S.plan?.inbox || []).flatMap(g => g.items.map(i => ({ ...i, group: g.name })));

const SKIP = new Set(["chicken","breakfast","protein","turkey","black","bowls","thighs","overnight","masala","bean"]);
function recipeLinks(text) {
  let out = esc(text);
  for (const r of S.plan?.recipes || []) for (const w of r.slug.split("-").filter(w => w.length > 3 && !SKIP.has(w))) {
    const re = new RegExp(`\\b(${w}s?)\\b`, "i");
    if (re.test(out) && !out.includes(`data-recipe="${r.slug}"`)) { out = out.replace(re, `<a href="#food" data-recipe="${r.slug}">$1</a>`); break; }
  }
  return out;
}
function boxDetail(day, key) {
  if (key === "meals") return day.eat.map(recipeLinks).join(" · ");
  if (key === "body") return esc(day.body);
  const p = day.practice.find(x => x.toLowerCase().startsWith(key));
  return p ? esc(p.replace(new RegExp("^" + key + "\\s*", "i"), "")) : "";
}
const cap = s => s.charAt(0).toUpperCase() + s.slice(1);

// ---------- icons (one stroke family, 24px grid)
const I = (d, w = 2) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${d}</svg>`;
const IC = {
  check: I('<path d="M5 12.5l4.5 4.5L19 7.5"/>', 3),
  left: I('<path d="M15 5l-7 7 7 7"/>', 2.4),
  right: I('<path d="M9 5l7 7-7 7"/>', 2.4),
  close: I('<path d="M6 6l12 12M18 6L6 18"/>', 2.4),
  meals: I('<path d="M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10M17 3c-2 1.5-3 4-3 7h3v11"/>'),
  body: I('<circle cx="12" cy="4.5" r="2"/><path d="M5 9l7 1.5L19 9M12 10.5V15l-3.5 6M12 15l3.5 6"/>'),
  piano: I('<rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="M8 5v8M12 5v8M16 5v8M6.5 13h3M10.5 13h3M14.5 13h3M8 13v6M12 13v6M16 13v6"/>', 1.7),
  voice: I('<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21"/>'),
  task: I('<rect x="4" y="4" width="16" height="16" rx="4"/><path d="M8.5 12l2.5 2.5 4.5-5"/>'),
  today: I('<circle cx="12" cy="12" r="8.5"/><path d="M8 12.5l2.8 2.8L16.5 9.5"/>'),
  week: I('<rect x="3.5" y="5" width="17" height="15" rx="3.5"/><path d="M3.5 10h17M8 3v4M16 3v4"/>'),
  soon: I('<path d="M12 3.5l2.5 5.2 5.7.8-4.1 4 1 5.6-5.1-2.7-5.1 2.7 1-5.6-4.1-4 5.7-.8z"/>'),
  food: I('<path d="M4 11h16a8 8 0 0 1-16 0zM8 4c0 2 2 2 2 4M13 4c0 2 2 2 2 4"/>'),
  plus: I('<path d="M12 5v14M5 12h14"/>', 2.4),
  heart: I('<path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"/>'),
  clock: I('<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>'),
  refresh: I('<path d="M20 11a8 8 0 1 0-2.3 5.7M20 5v6h-6"/>'),
  key: I('<circle cx="8" cy="15" r="4"/><path d="M11 12l8-8M16 7l2 2M14 9l2 2"/>'),
  paste: I('<rect x="6" y="4" width="12" height="17" rx="2.5"/><path d="M9 4V3h6v1M9 11h6M9 15h4"/>'),
};
const TABS = [["today", "Today"], ["week", "Week"], ["soon", "Coming up"], ["food", "Food"]];
const KIND = { meals: ["Eat", "meals"], body: ["Body", "body"], piano: ["Piano", "piano"], voice: ["Voice", "voice"] };

// ---------- small pieces
const ring = (done, n, size = 64) => {
  const r = size / 2 - 5, c = 2 * Math.PI * r, f = n ? done / n : 0;
  return `<svg class="ring" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" aria-hidden="true"><circle cx="${size/2}" cy="${size/2}" r="${r}" class="ring-bg"/><circle cx="${size/2}" cy="${size/2}" r="${r}" class="ring-fg${f === 1 ? " full" : ""}" stroke-dasharray="${c}" stroke-dashoffset="${c * (1 - f)}" transform="rotate(-90 ${size/2} ${size/2})"/></svg>`;
};
const pill = (label, tone = "") => `<span class="pill ${tone}">${label}</span>`;
const lab = t => `<h2 class="lab">${t}</h2>`;
function bigPill(date) {
  const s = bigDay(); if (!s) return "";
  const n = daysBetween(date, s.date), name = bigName(s);
  if (n > 1) return pill(`${n} days to ${esc(name)}`, "warm");
  if (n === 1) return pill(`${esc(cap(name))} tomorrow`, "warm");
  if (n === 0) return pill(`${esc(cap(name))} today`, "warm");
  return pill(`Day ${-n} after ${esc(name)}`, "soft");
}
const checkRow = (attrs, checked, label, sub) => `<button class="row check" role="checkbox" aria-checked="${checked}" ${attrs}><span class="box">${IC.check}</span><span class="rt"><span class="rl">${label}</span>${sub ? `<span class="rs">${sub}</span>` : ""}</span></button>`;
function tileDetail(day, key) {
  if (key === "meals") return `${day.eat.length} meals${(day.eat.join(" ").match(/~?\d+ g protein/) || [""])[0] ? " · " + day.eat.join(" ").match(/~?\d+ g protein/)[0] : ""}`;
  if (key === "body") return esc(day.body.replace(/\s*\(.*\)/, ""));
  const p = day.practice.find(x => x.toLowerCase().startsWith(key));
  return p ? esc(p.replace(new RegExp("^" + key + "\\s*", "i"), "").replace(/\s*\(.*\)/, "")) : "";
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
        <a class="btn" href="${KEY_URL}" target="_blank" rel="noopener" data-step="2">${IC.key} Make my key</a></div></li>
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
  if (all) h += `<p class="cheer">All done${date === t ? " today" : ""}. Nice work.</p>`;
  else if (!store.get("planlab-tip")) h += `<p class="tip">Tap a card when it's done.</p>`;
  const main = day.boxes.filter(b => KIND[b.key]), other = day.boxes.filter(b => !KIND[b.key]);
  h += `<div class="tiles">${main.map(b => { const on = tickGet(date, b);
    return `<button class="tile k-${b.key}" role="checkbox" aria-checked="${on}" data-list="${date}" data-item="${b.key}">
      <span class="tile-top"><span class="tile-ic">${IC[b.key]}</span><span class="box">${IC.check}</span></span>
      <span class="tile-name">${KIND[b.key][0]}</span><span class="tile-sub">${tileDetail(day, b.key)}</span></button>`; }).join("")}</div>`;
  if (other.length) h += `<div class="card list">${other.map(b => checkRow(`data-list="${date}" data-item="${b.key}"`, tickGet(date, b), esc(cap(b.label)), "")).join("")}</div>`;
  if (day.eat.length) h += `<section class="sec">${lab("Meals")}<div class="card list">${day.eat.map((m, i) => `<div class="row"><span class="tag">${MEAL[Math.min(i, 3)]}</span><span class="rt"><span class="rl n">${recipeLinks(m.replace(/\s*\(~?\d+ g protein\)/, ""))}</span></span></div>`).join("")}</div></section>`;
  const extra = day.practice.filter(p => !day.boxes.some(b => p.toLowerCase().startsWith(b.key)));
  const radar = [...day.coming, ...extra.map(x => cap(x) + " (if you feel like it)")];
  const due = inboxItems().filter(i => i.date === date);
  const ev = (S.plan?.calendar || []).filter(e => e.date === date);
  if (due.length || radar.length || ev.length) {
    h += `<section class="sec">${lab("On your radar")}<div class="card list">
      ${ev.map(e => `<div class="row"><span class="dot warm"></span><span class="rt"><span class="rl">${esc(e.what)}</span>${e.notes ? `<span class="rs">${esc(e.notes)}</span>` : ""}</span></div>`).join("")}
      ${due.map(i => checkRow(`data-list="inbox" data-item="${i.key}"`, tickGet("inbox", i), esc(i.what), "Due today")).join("")}
      ${radar.map(x => `<div class="row"><span class="dot"></span><span class="rt"><span class="rl n">${recipeLinks(cap(x))}</span></span></div>`).join("")}</div></section>`;
  }
  return h;
}

function viewWeek() {
  const w = weekOf(S.day) || (S.plan?.weeks || []).at(-1);
  let h = `<header class="hero"><div class="hero-t"><p class="eyebrow">${w ? `${nice(w.start, { month: "short", day: "numeric" })} – ${nice(addDays(w.start, 6), { month: "short", day: "numeric" })}` : ""}</p><h1>This week</h1>${w?.phase ? `<div class="pills">${pill(esc(cap(w.phase.replace(/\s*\(.*\)/, ""))), "soft")}</div>` : ""}</div></header>`;
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
  const G = { "With Elizabeth": IC.heart, Cooking: IC.meals, Groceries: IC.food };
  if (w.glance.length) h += `<section class="sec">${lab("At a glance")}<div class="card list">${w.glance.map(g => `<div class="row"><span class="tag-ic">${G[g.label] || IC.clock}</span><span class="rt"><span class="rl">${esc(g.label.replace(/\s*\(.*\)/, ""))}</span><span class="rs">${recipeLinks(g.text)}</span></span></div>`).join("")}</div></section>`;
  const wi = S.plan.weeks.indexOf(w);
  h += `<div class="seg two">${wi > 0 ? `<button data-pick="${S.plan.weeks[wi-1].start}">${IC.left} Last week</button>` : "<span></span>"}${wi < S.plan.weeks.length - 1 ? `<button data-pick="${S.plan.weeks[wi+1].start}">Next week ${IC.right}</button>` : "<span></span>"}</div>`;
  return h;
}

function viewSoon() {
  const t = todayIso(), s = bigDay(), n = s ? daysBetween(t, s.date) : -1;
  let h = `<header class="hero"><div class="hero-t"><p class="eyebrow">What's ahead</p><h1>Coming up</h1></div></header>`;
  if (s && n >= 0) h += `<div class="card count"><span class="count-n">${n}</span><span class="count-t"><b>${n === 0 ? esc(cap(bigName(s))) + " is today" : (n === 1 ? "day to " : "days to ") + esc(bigName(s))}</b><span>${esc(nice(s.date, { weekday: "long", month: "short", day: "numeric" }))}</span></span></div>`;
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
    return `<div class="row"><span class="date${daysBetween(t, e.date) <= 3 ? " soon" : ""}"><small>${d.toLocaleDateString("en-US", { month: "short" })}</small>${e.approx ? "~" : ""}${d.getDate()}</span><span class="rt"><span class="rl">${esc(e.what)}</span><span class="rs">${esc([e.who, e.notes].filter(Boolean).join(" · ") || d.toLocaleDateString("en-US", { weekday: "long" }))}</span></span></div>`; }).join("")}</div></section>`;
  const later = inboxItems().filter(i => !/^(before|open)/i.test(i.group));
  if (later.length) h += `<section class="sec">${lab("Later")}<div class="card list">${later.map(i => checkRow(`data-list="inbox" data-item="${i.key}"`, tickGet("inbox", i), esc(i.what), esc(i.when.replace(/\s*\(.*\)/, "")))).join("")}</div></section>`;
  if (S.plan?.watching?.length) h += `<section class="sec">${lab("Claude is watching for")}<div class="card list">${S.plan.watching.map(w => `<div class="row"><span class="dot"></span><span class="rt"><span class="rl n">${esc(w)}</span></span></div>`).join("")}</div></section>`;
  return h;
}

function viewFood() {
  const mode = S.food || "list";
  let h = `<header class="hero"><div class="hero-t"><p class="eyebrow">Eat well, cook less</p><h1>Food</h1></div></header>
    <div class="seg" role="tablist"><button role="tab" aria-selected="${mode === "list"}" data-food="list">Kroger list</button><button role="tab" aria-selected="${mode === "recipes"}" data-food="recipes">Recipes</button></div>`;
  if (mode === "list") {
    const g = (S.plan?.groceries || []).at(-1);
    if (!g) return h + `<div class="empty">${IC.food}<p>${!S.plan ? "Loading…" : "No list this week."}</p></div>`;
    const id = `groceries-${g.week}`, items = g.sections.flatMap(s => s.items), done = items.filter(i => tickGet(id, i)).length;
    h += `<div class="labrow"><span class="rs">${g.pickup ? "Pickup " + esc(g.pickup) : esc(g.week)}</span><span class="rs">${done} of ${items.length} in the cart</span></div><div class="bar"><i style="width:${items.length ? done / items.length * 100 : 0}%"></i></div>`;
    for (const s of g.sections) {
      const left = s.items.filter(i => !tickGet(id, i)).length;
      h += `<section class="sec">${lab(`${esc(s.name)} <span class="cnt">${left ? left + " left" : "done"}</span>`)}<div class="card list compact">${[...s.items.filter(i => !tickGet(id, i)), ...s.items.filter(i => tickGet(id, i))].map(i => checkRow(`data-list="${id}" data-item="${i.key}"`, tickGet(id, i), esc(i.text), "")).join("")}</div></section>`;
    }
  } else {
    const rs = S.plan?.recipes || [];
    h += rs.length ? `<div class="recipes">${rs.map(r => `<button class="card rcard" data-recipe="${r.slug}"><span class="rc-p"><b>${r.protein ?? "?"}</b>g protein</span><span class="rc-t">${esc(r.title)}</span><span class="rc-m">${r.kcal ?? "?"} kcal${r.standing ? ` · ${esc(/none/i.test(r.standing) ? "no standing" : r.standing.split(/[;(,]/)[0].trim() + " standing")}` : ""}</span></button>`).join("")}</div>` : `<div class="empty">${IC.food}<p>${!S.plan ? "Loading…" : "No recipes yet."}</p></div>`;
  }
  return h;
}

function md(src) {
  const inline = s => esc(s).replace(/\*\*(.+?)\*\*/g, "<b>$1</b>").replace(/`([^`]+)`/g, "$1").replace(/\[([^\]]+)\]\([^)]+\)/g, "$1");
  const out = [], lines = src.split("\n"); let i = 0;
  while (i < lines.length) {
    const l = lines[i];
    if (/^#\s/.test(l) || /^\|/.test(l)) { while (i < lines.length && /^\|/.test(lines[i])) i++; if (/^#\s/.test(l)) i++; }
    else if (/^##\s/.test(l)) { out.push(`<h2 class="lab">${inline(l.slice(3))}</h2>`); i++; }
    else if (/^\s*-\s/.test(l)) { const it = []; while (i < lines.length && /^\s*-\s/.test(lines[i])) it.push(lines[i++].replace(/^\s*-\s/, "")); out.push(`<ul>${it.map(x => `<li>${inline(x)}</li>`).join("")}</ul>`); }
    else if (/^\d+\.\s/.test(l)) { const it = []; while (i < lines.length && /^\d+\.\s/.test(lines[i])) it.push(lines[i++].replace(/^\d+\.\s/, "")); out.push(`<ol>${it.map(x => `<li>${inline(x)}</li>`).join("")}</ol>`); }
    else { if (l.trim()) out.push(`<p>${inline(l)}</p>`); i++; }
  }
  return out.join("");
}
function openRecipe(slug) {
  const r = (S.plan?.recipes || []).find(x => x.slug === slug); if (!r) return;
  const sh = document.getElementById("sheet");
  const stat = (v, u, l) => `<span class="stat"><b>${v ?? "?"}<small>${u}</small></b><span>${l}</span></span>`;
  sh.innerHTML = `<div class="sheet-card" role="dialog" aria-modal="true" aria-label="${esc(r.title)}"><div class="grab"></div>
    <div class="sheet-h"><h1>${esc(r.title)}</h1><button class="iconbtn" id="close-sheet" aria-label="Close">${IC.close}</button></div>
    <div class="stats">${stat(r.protein, "g", "protein")}${stat(r.kcal, "", "kcal")}${stat(r.carbs, "g", "carbs")}${stat(r.fat, "g", "fat")}</div>
    <article class="md">${md(r.md)}</article></div>`;
  sh.hidden = false; requestAnimationFrame(() => sh.classList.add("open"));
  sh.querySelector(".sheet-card").scrollTop = 0; document.getElementById("close-sheet").focus();
}
function closeSheet() { const sh = document.getElementById("sheet"); sh.classList.remove("open"); setTimeout(() => { sh.hidden = true; }, 220); }

// ---------- render + events
function render() {
  const app = document.getElementById("app"), tabs = document.querySelector(".tabs");
  if (!S.token || (S.badKey && !S.plan)) { app.innerHTML = `<div class="wrap">${viewSetup()}</div>`; tabs.hidden = true; return; }
  tabs.hidden = false;
  const view = { today: viewToday, week: viewWeek, soon: viewSoon, food: viewFood }[S.tab];
  const draft = document.getElementById("capture-text")?.value;
  const when = S.plan?.built ? new Date(S.plan.built).toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" }) : "";
  app.innerHTML = `<div class="wrap v-${S.tab}">${view()}
    ${S.error ? `<p class="toast-inline" role="status">${esc(S.error)}${S.badKey ? ` <button class="link" id="reset-key">Set up a new key</button>` : ""}</p>` : ""}
    <p class="foot">${S.loading ? "Updating…" : when ? `Updated ${when}` : ""} <button class="link" id="refresh" aria-label="Refresh">${IC.refresh}</button></p></div>`;
  const ci = document.getElementById("capture-text"); if (ci && draft) ci.value = draft;
  document.getElementById("tabs").innerHTML = TABS.map(([k, l]) => `<button data-tab="${k}" ${S.tab === k ? 'aria-current="page"' : ""}>${IC[k]}<span>${l}</span></button>`).join("");
}
const VALID = TABS.map(t => t[0]);
const fromHash = () => { const h = location.hash.slice(1); if (VALID.includes(h)) S.tab = h; };
fromHash();
addEventListener("hashchange", () => { fromHash(); render(); });
const goTab = k => { S.tab = k; history.replaceState(null, "", "#" + k); render(); scrollTo({ top: 0 }); };

document.addEventListener("click", async e => {
  const r = e.target.closest("[data-recipe]"); if (r) { e.preventDefault(); openRecipe(r.dataset.recipe); return; }
  if (e.target.closest("#close-sheet") || e.target.id === "sheet") { closeSheet(); return; }
  if (e.target.closest("#refresh")) { load(); return; }
  if (e.target.closest("#reset-key")) { store.del("planlab-key"); S.token = null; S.badKey = false; S.error = ""; S.setupStep = 1; render(); return; }
  if (e.target.closest("[data-step]")) { S.setupStep = 2; setTimeout(render, 300); return; }
  if (e.target.closest("#paste")) { try { const v = await navigator.clipboard.readText(); document.getElementById("key-input").value = v.trim(); } catch { document.getElementById("key-input").focus(); } return; }
  const tab = e.target.closest("[data-tab]"); if (tab) { if (tab.dataset.tab === "today") S.day = todayIso(); goTab(tab.dataset.tab); return; }
  const f = e.target.closest("[data-food]"); if (f) { S.food = f.dataset.food; render(); return; }
  const go = e.target.closest("[data-go]"); if (go) { S.day = addDays(S.day, +go.dataset.go); render(); return; }
  const pk = e.target.closest("[data-pick]"); if (pk) { S.day = pk.dataset.pick; render(); return; }
  const j = e.target.closest("[data-jump]"); if (j) { S.day = j.dataset.jump; goTab("today"); return; }
  const l = e.target.closest("[data-list]"); if (l) {
    const list = l.dataset.list, key = l.dataset.item;
    const pool = list === "inbox" ? inboxItems() : list === "setup" ? S.plan.setup : list.startsWith("groceries-") ? (S.plan.groceries.find(g => `groceries-${g.week}` === list)?.sections.flatMap(s => s.items) || []) : (findDay(list)?.boxes || []);
    const item = pool.find(x => x.key === key); if (!item) return;
    store.set("planlab-tip", "1");
    if (navigator.vibrate) try { navigator.vibrate(8); } catch {}
    tickSet(list, item);
  }
});
document.addEventListener("submit", async e => {
  e.preventDefault();
  if (e.target.id === "keyform") {
    const v = document.getElementById("key-input").value.trim(); if (!v) { document.getElementById("key-input").focus(); return; }
    S.token = v; S.badKey = false; S.error = ""; store.set("planlab-key", v); render(); load(); return;
  }
  if (e.target.id !== "capture") return;
  const inp = document.getElementById("capture-text"), text = inp.value.trim(); if (!text) { inp.focus(); return; }
  const btn = document.getElementById("capture-btn"); btn.disabled = true;
  try {
    const r = await fetch(`https://api.github.com/repos/${REPO}/issues`, { method: "POST", headers: { Authorization: `Bearer ${S.token}`, Accept: "application/vnd.github+json", "Content-Type": "application/json" },
      body: JSON.stringify({ title: `Wish: ${text}`, body: `Added from planLAB on ${nice(todayIso())}. Claude: give it a date in inbox.md, then close this.` }) });
    if (!r.ok) throw new Error(String(r.status));
    inp.value = ""; S.msg = `Added "${text}". Claude dates it tomorrow morning.`;
  } catch { S.msg = "Couldn't add it. Tell Claude in chat instead."; }
  btn.disabled = false; render();
});
document.addEventListener("keydown", e => { if (e.key === "Escape") closeSheet(); });

let lastToday = todayIso();
setInterval(() => { const t = todayIso(); if (t !== lastToday) { if (S.day === lastToday) S.day = t; lastToday = t; load(); } }, 60000);
document.addEventListener("visibilitychange", () => { if (document.visibilityState === "visible" && S.token && (!S.plan || Date.now() - new Date(S.plan.built) > 15 * 60000)) load(); });

render();
load();
})();
