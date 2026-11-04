// planLAB: Matt's planner. Reads the Markdown in his private GeneralStorage repo
// (projects/planlab/) with a read-only GitHub key kept on this device only.
// No planner content lives in this public repo.
(() => {
const REPO = "mreindl118-boop/GeneralStorage";
const DIR = "projects/planlab";
const KEY_URL = "https://github.com/settings/personal-access-tokens/new?name=planLAB&description=Read%20my%20planner%20on%20my%20phone&target_name=mreindl118-boop&expires_in=none&contents=read&issues=write";

const ICON = {
  check: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>',
  left: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M15 5l-7 7 7 7"/></svg>',
  right: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M9 5l7 7-7 7"/></svg>',
  today: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="8.5"/><path d="M8 12.5l2.8 2.8L16.5 9.5"/></svg>',
  week: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="3.5" y="5" width="17" height="15" rx="3"/><path d="M3.5 10h17M8 3v4M16 3v4"/></svg>',
  soon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 3.5l2.5 5.2 5.7.8-4.1 4 1 5.6-5.1-2.7-5.1 2.7 1-5.6-4.1-4 5.7-.8z"/></svg>',
  food: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 11h16a8 8 0 0 1-16 0zM8 4c0 2 2 2 2 4M13 4c0 2 2 2 2 4"/></svg>',
};
const TABS = [["today","Today"],["week","Week"],["soon","Coming up"],["food","Food"]];
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
  for (const row of parseTable(text.split("## Work and friends")[0])) {
    const m = (row[0] || "").match(/(\d{4}-)?(\d\d)-(\d\d)/); if (!m || row.length < 2) continue;
    events.push({ date: guessDate(m[1] ? +m[1].slice(0, 4) : y, +m[2], +m[3], !!m[1]), approx: row[0].startsWith("~"), what: row[1], who: row[2] || "", notes: row[3] || "" });
  }
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
const S = { tab: "today", day: todayIso(), plan: store.json("planlab-cache", null), ticks: store.json("planlab-ticks", {}), loading: false, error: "", token: store.get("planlab-key"), msg: "" };
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
const surgery = () => (S.plan?.calendar || []).find(e => /surgery/i.test(e.what));
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
const TITLES = { meals: "Eat", body: "Body", piano: "Piano", voice: "Voice", groceries: "Groceries ordered" };

// ---------- views
const checkRow = (attrs, checked, label, detail) => `<button class="check" role="checkbox" aria-checked="${checked}" ${attrs}><span class="box">${ICON.check}</span><span class="txt"><span class="label">${label}</span>${detail ? `<span class="detail">${detail}</span>` : ""}</span></button>`;
const head2 = (title, right) => `<div class="h2row"><h2>${title}</h2>${right ? `<span class="note">${right}</span>` : ""}</div>`;
function surgeryChip(date) {
  const s = surgery(); if (!s) return "";
  const n = daysBetween(date, s.date);
  if (n > 1) return `<span class="chip">${n} days to surgery</span>`;
  if (n === 1) return `<span class="chip">Surgery tomorrow</span>`;
  if (n === 0) return `<span class="chip">Surgery day</span>`;
  return `<span class="chip blue">Recovery day ${-n}</span>`;
}

function viewSetup() {
  return `<header class="top"><div class="t"><h1>planLAB</h1><div class="sub">Your plan, on this phone</div></div></header>
  <section class="section"><p>Your planner lives in your private GitHub repo. This page reads it with a key that stays on this phone. You set it up once.</p></section>
  <section class="section">${head2("1. Make a key")}
    <p><a class="btn" href="${KEY_URL}" target="_blank" rel="noopener">Make my key on GitHub</a></p>
    <p class="note">Under Repository access pick <b>Only select repositories</b> and choose <b>GeneralStorage</b>. Everything else is filled in. Tap Generate, then Copy.</p></section>
  <section class="section">${head2("2. Paste it here")}
    <form class="capture" id="keyform"><input id="key-input" type="password" placeholder="github_pat_…" autocomplete="off" aria-label="GitHub key"><button class="btn">Open my plan</button></form></section>
  ${S.error ? `<p class="status" role="status">${esc(S.error)}</p>` : ""}`;
}

function viewToday() {
  const date = S.day, day = findDay(date), t = todayIso();
  const title = date === t ? "Today" : date === addDays(t, 1) ? "Tomorrow" : date === addDays(t, -1) ? "Yesterday" : nice(date, { weekday: "long" });
  let h = `<header class="top"><button class="navbtn" data-go="-1" aria-label="Previous day">${ICON.left}</button>
    <div class="t"><h1>${title}</h1><div class="sub">${nice(date, { weekday: "long", month: "long", day: "numeric" })} ${surgeryChip(date)}</div></div>
    <button class="navbtn" data-go="1" aria-label="Next day">${ICON.right}</button></header>`;
  if (date !== t) h += `<p><button class="btn small" data-jump="${t}">Back to today</button></p>`;
  if (!day) {
    const last = allDays().at(-1);
    h += `<div class="empty">${!S.plan ? "Loading your plan…" : last && date > last.date ? "This day isn't planned yet. Claude writes next week's plan on Sunday." : "Nothing planned for this day."}</div>`;
  } else {
    const { n, done } = dayCount(day);
    h += `<section class="section">${head2("Do today", `${done} of ${n} done`)}<div class="progress" aria-hidden="true"><i style="width:${n ? done / n * 100 : 0}%"></i></div>
      <div class="list">${day.boxes.map(b => checkRow(`data-list="${date}" data-item="${b.key}"`, tickGet(date, b), esc(TITLES[b.key] || cap(b.label)), boxDetail(day, b.key))).join("")}</div></section>`;
    const extra = day.practice.filter(p => !day.boxes.some(b => p.toLowerCase().startsWith(b.key)));
    if (extra.length) h += `<p class="note">Also, if you feel like it: ${extra.map(esc).join(" · ")}</p>`;
    if (day.coming.length) h += `<section class="section">${head2("On your radar")}<div class="list">${day.coming.map(c => `<div class="row"><span class="what">${recipeLinks(c)}</span></div>`).join("")}</div></section>`;
  }
  const due = inboxItems().filter(i => i.date === date && !tickGet("inbox", i));
  if (due.length) h += `<section class="section">${head2(date === t ? "Due today" : "Due this day")}<div class="list">${due.map(i => checkRow(`data-list="inbox" data-item="${i.key}"`, false, esc(i.what), "")).join("")}</div></section>`;
  const ev = (S.plan?.calendar || []).filter(e => e.date === date);
  if (ev.length) h += `<section class="section">${head2("On the calendar")}<div class="list">${ev.map(e => `<div class="row"><span class="what">${esc(e.what)}${e.notes ? `<small>${esc(e.notes)}</small>` : ""}</span></div>`).join("")}</div></section>`;
  return h;
}

function viewWeek() {
  const w = weekOf(S.day) || (S.plan?.weeks || []).at(-1);
  let h = `<header class="top"><div class="t"><h1>This week</h1>${w ? `<div class="sub">${nice(w.start, { month: "short", day: "numeric" })} to ${nice(addDays(w.start, 6), { month: "short", day: "numeric" })} · ${esc(w.phase)}</div>` : ""}</div></header>`;
  if (!w) return h + `<div class="empty">${!S.plan ? "Loading your plan…" : "No week planned yet."}</div>`;
  if (w.focus) h += `<p>${esc(cap(w.focus))}</p>`;
  const t = todayIso();
  h += `<section class="section">${head2("Days")}<div class="list">${w.days.map(d => { const c = dayCount(d);
    return `<button class="row${d.date === t ? " today" : ""}" data-jump="${d.date}"><span class="when">${esc(d.label.slice(0, 3))} ${parse(d.date).getDate()}</span><span class="what">${esc(d.body)}<small>${esc(d.eat.at(-1) || "")}</small></span><span class="chip ${c.done === c.n && c.n ? "green" : "blue"}">${c.done}/${c.n}</span></button>`; }).join("")}</div></section>`;
  if (w.glance.length) h += `<section class="section">${head2("The week at a glance")}<div class="list">${w.glance.map(g => `<div class="row"><span class="what"><b>${esc(g.label)}</b><small>${recipeLinks(g.text)}</small></span></div>`).join("")}</div></section>`;
  const wi = S.plan.weeks.indexOf(w);
  h += `<p class="btnrow">${wi > 0 ? `<button class="btn small" data-jump="${S.plan.weeks[wi-1].start}" data-stay="1">Last week</button>` : ""}${wi < S.plan.weeks.length - 1 ? `<button class="btn small" data-jump="${S.plan.weeks[wi+1].start}" data-stay="1">Next week</button>` : ""}</p>`;
  return h;
}

function viewSoon() {
  const t = todayIso(), s = surgery(), n = s ? daysBetween(t, s.date) : -1;
  let h = `<header class="top"><div class="t"><h1>Coming up</h1></div></header>`;
  if (s && n >= 0) h += `<div class="hero"><div class="big">${n}</div><p><strong>${n === 0 ? "Surgery is today" : n === 1 ? "day until surgery" : "days until surgery"}</strong>${esc(nice(s.date, { weekday: "short", month: "short", day: "numeric" }))}${s.notes ? ` · ${esc(s.notes)}` : ""}</p></div>`;
  h += `<section class="section">${head2("Want to do something?")}<form class="capture" id="capture"><input id="capture-text" placeholder="See Goose in the spring" autocomplete="off" aria-label="Something you want to do"><button class="btn" id="capture-btn">Add</button></form>
    <p class="note">${S.msg ? esc(S.msg) : "Claude gives it a date the next morning and puts it in your plan."}</p></section>`;
  const before = inboxItems().filter(i => /^before/i.test(i.group));
  const setup = S.plan?.setup || [];
  if (n >= 0 && (before.length || setup.length)) {
    const all = [...before.map(i => ["inbox", i, i.what, i.when]), ...setup.map(i => ["setup", i, i.text, ""])];
    h += `<section class="section">${head2(s ? "Before surgery" : "Before then", `${all.filter(([l, i]) => tickGet(l, i)).length} of ${all.length} done`)}
      <div class="list">${all.map(([l, i, text, when]) => checkRow(`data-list="${l}" data-item="${i.key}"`, tickGet(l, i), esc(text), esc(when))).join("")}</div></section>`;
  }
  const ev = (S.plan?.calendar || []).filter(e => e.date >= t);
  if (ev.length) h += `<section class="section">${head2("Calendar")}<div class="list">${ev.map(e => `<div class="row${daysBetween(t, e.date) <= 3 ? " soon" : ""}"><span class="when">${e.approx ? "~" : ""}${nice(e.date, { month: "short", day: "numeric" })}</span><span class="what">${esc(e.what)}<small>${esc([e.who, e.notes].filter(Boolean).join(" · "))}</small></span></div>`).join("")}</div></section>`;
  const later = inboxItems().filter(i => !/^(before|open)/i.test(i.group));
  if (later.length) h += `<section class="section">${head2("Later")}<div class="list">${later.map(i => checkRow(`data-list="inbox" data-item="${i.key}"`, tickGet("inbox", i), esc(i.what), esc(i.when))).join("")}</div></section>`;
  if (S.plan?.watching?.length) h += `<section class="section">${head2("Claude is watching for")}<div class="list">${S.plan.watching.map(w => `<div class="row"><span class="what">${esc(w)}</span></div>`).join("")}</div></section>`;
  return h;
}

function viewFood() {
  let h = `<header class="top"><div class="t"><h1>Food</h1></div></header>`;
  const g = (S.plan?.groceries || []).at(-1);
  if (g) {
    const id = `groceries-${g.week}`, items = g.sections.flatMap(s => s.items), done = items.filter(i => tickGet(id, i)).length;
    h += `<section class="section">${head2(`Kroger list · ${esc(g.pickup || g.week)}`, `${done} of ${items.length}`)}<div class="progress" aria-hidden="true"><i style="width:${items.length ? done / items.length * 100 : 0}%"></i></div></section>`;
    for (const s of g.sections) h += `<section class="section">${head2(esc(s.name))}<div class="list plain">${s.items.map(i => checkRow(`data-list="${id}" data-item="${i.key}"`, tickGet(id, i), esc(i.text), "")).join("")}</div></section>`;
  }
  const rs = S.plan?.recipes || [];
  h += `<section class="section">${head2("Recipes")}${rs.length ? `<div class="list">${rs.map(r => `<button class="recipe" data-recipe="${r.slug}"><span class="name">${esc(r.title)}</span><span class="macros"><span><b>${r.protein ?? "?"} g</b> protein</span><span>${r.kcal ?? "?"} kcal</span>${r.standing ? `<span>Standing: ${esc(r.standing.split(/[;(]/)[0].trim())}</span>` : ""}</span></button>`).join("")}</div>` : `<div class="empty">${!S.plan ? "Loading recipes…" : "No recipes yet."}</div>`}</section>`;
  return h;
}

function md(src) {
  const inline = s => esc(s).replace(/\*\*(.+?)\*\*/g, "<b>$1</b>").replace(/`([^`]+)`/g, "$1").replace(/\[([^\]]+)\]\([^)]+\)/g, "$1");
  const out = [], lines = src.split("\n"); let i = 0;
  while (i < lines.length) {
    const l = lines[i];
    if (/^#\s/.test(l)) { out.push(`<h1>${inline(l.slice(2))}</h1>`); i++; }
    else if (/^##\s/.test(l)) { out.push(`<h2>${inline(l.slice(3))}</h2>`); i++; }
    else if (/^\|/.test(l)) { const rows = []; while (i < lines.length && /^\|/.test(lines[i])) { if (!/^\|[\s\-|]+\|$/.test(lines[i].trim())) rows.push(lines[i].trim().replace(/^\||\|$/g, "").split("|")); i++; }
      out.push(`<div class="tbl"><table>${rows.map((r, k) => `<tr>${r.map(c => k ? `<td>${inline(c.trim())}</td>` : `<th>${inline(c.trim())}</th>`).join("")}</tr>`).join("")}</table></div>`); }
    else if (/^\s*-\s/.test(l)) { const it = []; while (i < lines.length && /^\s*-\s/.test(lines[i])) it.push(lines[i++].replace(/^\s*-\s/, "")); out.push(`<ul>${it.map(x => `<li>${inline(x)}</li>`).join("")}</ul>`); }
    else if (/^\d+\.\s/.test(l)) { const it = []; while (i < lines.length && /^\d+\.\s/.test(lines[i])) it.push(lines[i++].replace(/^\d+\.\s/, "")); out.push(`<ol>${it.map(x => `<li>${inline(x)}</li>`).join("")}</ol>`); }
    else { if (l.trim()) out.push(`<p>${inline(l)}</p>`); i++; }
  }
  return out.join("");
}
function openRecipe(slug) {
  const r = (S.plan?.recipes || []).find(x => x.slug === slug); if (!r) return;
  const sh = document.getElementById("sheet");
  sh.innerHTML = `<div class="wrap"><header class="top"><button class="navbtn" id="close-sheet" aria-label="Close recipe">${ICON.left}</button><div class="t"><div class="sub">Recipe</div></div></header><article class="md">${md(r.md)}</article></div>`;
  sh.hidden = false; sh.scrollTop = 0; document.getElementById("close-sheet").focus();
}

// ---------- render + events
function render() {
  const app = document.getElementById("app");
  const tabs = document.querySelector(".tabs");
  if (!S.token || (S.badKey && !S.plan)) { app.innerHTML = `<div class="wrap">${viewSetup()}</div>`; tabs.hidden = true; return; }
  tabs.hidden = false;
  const view = { today: viewToday, week: viewWeek, soon: viewSoon, food: viewFood }[S.tab];
  const draft = document.getElementById("capture-text")?.value;
  const when = S.plan?.built ? new Date(S.plan.built).toLocaleString("en-US", { weekday: "short", hour: "numeric", minute: "2-digit" }) : "";
  app.innerHTML = `<div class="wrap">${view()}
    ${S.error ? `<p class="status" role="status">${esc(S.error)}${S.badKey ? ` <button class="link" id="reset-key">Set up a new key</button>` : ""}</p>` : ""}
    <p class="status">${S.loading ? "Updating…" : when ? `Loaded ${when} · <button class="link" id="refresh">Refresh</button>` : ""}</p></div>`;
  const ci = document.getElementById("capture-text"); if (ci && draft) ci.value = draft;
  document.getElementById("tabs").innerHTML = TABS.map(([k, l]) => `<button data-tab="${k}" ${S.tab === k ? 'aria-current="page"' : ""}>${ICON[k]}<span>${l}</span></button>`).join("");
}
const VALID = TABS.map(t => t[0]);
const fromHash = () => { const h = location.hash.slice(1); if (VALID.includes(h)) S.tab = h; };
fromHash();
addEventListener("hashchange", () => { fromHash(); render(); });

document.addEventListener("click", e => {
  const r = e.target.closest("[data-recipe]"); if (r) { e.preventDefault(); openRecipe(r.dataset.recipe); return; }
  if (e.target.closest("#close-sheet")) { document.getElementById("sheet").hidden = true; return; }
  if (e.target.closest("#refresh")) { load(); return; }
  if (e.target.closest("#reset-key")) { store.del("planlab-key"); S.token = null; S.badKey = false; S.error = ""; render(); return; }
  const tab = e.target.closest("[data-tab]"); if (tab) { S.tab = tab.dataset.tab; if (S.tab === "today") S.day = todayIso(); history.replaceState(null, "", "#" + S.tab); render(); scrollTo(0, 0); return; }
  const go = e.target.closest("[data-go]"); if (go) { S.day = addDays(S.day, +go.dataset.go); render(); return; }
  const j = e.target.closest("[data-jump]"); if (j) { S.day = j.dataset.jump; if (!j.dataset.stay) { S.tab = "today"; history.replaceState(null, "", "#today"); } render(); scrollTo(0, 0); return; }
  const l = e.target.closest("[data-list]"); if (l) {
    const list = l.dataset.list, key = l.dataset.item;
    const pool = list === "inbox" ? inboxItems() : list === "setup" ? S.plan.setup : list.startsWith("groceries-") ? (S.plan.groceries.find(g => `groceries-${g.week}` === list)?.sections.flatMap(s => s.items) || []) : (findDay(list)?.boxes || []);
    const item = pool.find(x => x.key === key); if (item) tickSet(list, item);
  }
});
document.addEventListener("submit", async e => {
  e.preventDefault();
  if (e.target.id === "keyform") {
    const v = document.getElementById("key-input").value.trim(); if (!v) return;
    S.token = v; S.badKey = false; S.error = ""; store.set("planlab-key", v); render(); load(); return;
  }
  if (e.target.id !== "capture") return;
  const inp = document.getElementById("capture-text"), text = inp.value.trim(); if (!text) return;
  const btn = document.getElementById("capture-btn"); btn.disabled = true;
  try {
    const r = await fetch(`https://api.github.com/repos/${REPO}/issues`, { method: "POST", headers: { Authorization: `Bearer ${S.token}`, Accept: "application/vnd.github+json", "Content-Type": "application/json" },
      body: JSON.stringify({ title: `Wish: ${text}`, body: `Added from planLAB on ${nice(todayIso())}. Claude: give it a date in inbox.md, then close this.` }) });
    if (!r.ok) throw new Error(String(r.status));
    inp.value = ""; S.msg = `Added "${text}". Claude dates it tomorrow morning.`;
  } catch {
    S.msg = "Couldn't add it. Your key may not allow issues; tell Claude in chat instead.";
  }
  btn.disabled = false; render();
});
document.addEventListener("keydown", e => { if (e.key === "Escape") document.getElementById("sheet").hidden = true; });

let lastToday = todayIso();
setInterval(() => { const t = todayIso(); if (t !== lastToday) { if (S.day === lastToday) S.day = t; lastToday = t; load(); } }, 60000);
document.addEventListener("visibilitychange", () => { if (document.visibilityState === "visible" && S.token && (!S.plan || Date.now() - new Date(S.plan.built) > 15 * 60000)) load(); });

render();
load();
})();
