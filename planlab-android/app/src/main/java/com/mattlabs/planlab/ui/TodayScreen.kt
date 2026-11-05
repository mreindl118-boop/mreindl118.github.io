package com.mattlabs.planlab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattlabs.planlab.core.Coach
import com.mattlabs.planlab.core.Day
import com.mattlabs.planlab.core.LogDoc
import com.mattlabs.planlab.core.NICE_DAY
import com.mattlabs.planlab.core.TodayPick
import com.mattlabs.planlab.data.Store
import java.time.LocalDate

@Composable
fun TodayScreen(loading: Boolean, onSettings: () -> Unit, onOpen: (String) -> Unit) {
    val st by Store.state.collectAsState()
    var date by remember { mutableStateOf(LocalDate.now()) }
    val today = LocalDate.now()
    val day = st.plan.day(date)
    val b = remember(st.version, st.plan, st.health, date) { Store.brief(date) }
    val tk = t()
    var openKey by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    fun flip(k: String) { openKey = if (openKey == k) null else k }

    LazyColumn(Modifier.fillMaxWidth().statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(date.format(NICE_DAY), color = tk.muted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(when (date) { today -> "Today"; today.plusDays(1) -> "Tomorrow"; today.minusDays(1) -> "Yesterday"; else -> date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() } },
                        style = MaterialTheme.typography.displaySmall, color = tk.fg)
                }
                val r = b.readiness
                Box(Modifier.clip(CircleShape).clickable { onOpen("health") }) {
                    Ring(r?.let { it.score / 100f } ?: 0f, r?.let { tk.score(it.score) } ?: tk.muted, 46.dp, 5.dp) {
                        Text(r?.score?.toString() ?: "–", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = tk.fg)
                    }
                }
                IconButton(onClick = { date = date.minusDays(1) }) { Icon(Icons.Filled.KeyboardArrowLeft, "Previous day", tint = tk.fg) }
                IconButton(onClick = { date = date.plusDays(1) }) { Icon(Icons.Filled.KeyboardArrowRight, "Next day", tint = tk.fg) }
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings", tint = tk.muted) }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (date != today) Text("Today", Modifier.clip(CircleShape).background(tk.accent).clickable { date = today }.padding(horizontal = 12.dp, vertical = 5.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val d = b.daysToBig
                if (d != null && d >= 0 && b.bigName.isNotEmpty()) Pill(if (d == 0L) b.bigName.replaceFirstChar { it.uppercase() } else "$d days", tk.warn, tk.warnSoft)
            }
            if (st.error.isNotEmpty()) Text(st.error, Modifier.padding(top = 6.dp), color = tk.warn, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.height(12.dp))
        }
        if (day == null) {
            item { PCard { Text(if (st.plan.weeks.isEmpty()) (if (loading) "Loading…" else "No plan yet") else "Nothing planned", color = tk.muted, fontSize = 18.sp, fontWeight = FontWeight.Bold) } }
            return@LazyColumn
        }
        item { NextUpCard(day) { flip(it) } }
        item {
            Spacer(Modifier.height(12.dp))
            // Each thing once: the area already shown big in Next up gets no ring, and the plan's
            // pelvic floor box *is* the pelvic counter (no second tile).
            val nextKey = TodayData.next(day)?.box?.key
            val pelvicBox = day.boxes.firstOrNull { isPelvic(it.key, it.label) }
            val tiles = TodayPick.order(day.boxes).filter { it.key != nextKey && it != pelvicBox }.map { box ->
                val n = box.steps.size
                val done = Store.isDone(day, box)
                val name = TodayData.label(box)
                RingTile(box.key, if (done) 1f else if (n > 0) Store.stepsDone(day, box) / n.toFloat() else 0f, TodayPick.words(name, 2), tk.kind(box.key),
                    onLong = if (n == 0 && done) ({ Store.toggle(day, box); Undo.offer("$name undone") { Store.toggle(day, box) } }) else null) {
                    if (n > 0) flip(box.key) else { val was = done; Store.toggle(day, box); Undo.offer(if (was) "$name undone" else "✓ $name") { Store.toggle(day, box) } }
                }
            }.toMutableList()
            val have = Store.eaten(date); val want = Store.foodTargets(date)
            tiles += RingTile("eat", if (want.protein > 0) have.protein / want.protein.toFloat() else 0f, "Eat", tk.eat) { flip("eat") }
            val pf = LogDoc.pelvic(st.log, date.toString())
            if (pelvicBox?.key != nextKey || pelvicBox == null) {
                val boxDone = pelvicBox != null && Store.isDone(day, pelvicBox)
                tiles += RingTile("pelvic", if (boxDone) 1f else pf / 2f, "Pelvic $pf/2", tk.ok,
                    onLong = if (pf > 0) ({ Store.pelvicUndo(date); Undo.offer("Pelvic set removed") { Store.pelvicSet(date) } }) else null) {
                    if (pf >= 2) Undo.offer("Pelvic 2/2 · hold to undo", null) else {
                        Store.pelvicSet(date)
                        // The second set also ticks the plan's pelvic box.
                        val tick = pf + 1 >= 2 && pelvicBox != null && !Store.isDone(day, pelvicBox)
                        if (tick) Store.toggle(day, pelvicBox!!)
                        Undo.offer("✓ Pelvic floor set ${pf + 1}/2") { Store.pelvicUndo(date); if (tick) Store.toggle(day, pelvicBox!!) }
                    }
                }
            }
            RingGrid(tiles, openKey)
        }
        val opened = day.boxes.firstOrNull { it.key == openKey && it.steps.isNotEmpty() }
        if (opened != null) item { Spacer(Modifier.height(10.dp)); StepCard(day, opened) }
        if (openKey == "eat") item { Spacer(Modifier.height(10.dp)); EatCard(day, date) }
        item { Spacer(Modifier.height(12.dp)); WeekChart(date) }
        item {
            val due = st.plan.inbox.count { it.date == date && !LogDoc.ticked(st.log, "inbox", it.key, it.done) }
            Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Box {
                    RoundButton(com.mattlabs.planlab.R.drawable.ic_a_more, tk.fg, tk.surface, if (more) "Less" else "More", 52.dp) { more = !more }
                    if (due > 0 && !more) Text("$due", Modifier.align(Alignment.TopEnd).clip(CircleShape).background(tk.bad).padding(horizontal = 6.dp, vertical = 1.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (!more) return@LazyColumn
        if (date == today) item { Spacer(Modifier.height(12.dp)); FeelRow(date); Spacer(Modifier.height(10.dp)); InsightHead { onOpen("insights") } }
        st.progress?.let { p -> item { Spacer(Modifier.height(10.dp)); LevelCard(p) { onOpen("progress") } } }
        if (b.nudges.isNotEmpty()) item {
            Label("Heads up")
            PCard(pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                for (n in b.nudges) RowItem(lead = { Dot(if (n.warn) tk.bad else tk.accent) }, title = n.title)
            }
        }
        val events = st.plan.calendar.filter { it.date == date }
        val due = st.plan.inbox.filter { it.date == date }
        if (events.isNotEmpty() || due.isNotEmpty() || day.coming.isNotEmpty()) item {
            Label("Radar")
            PCard(pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                for (e in events) RowItem(lead = { Dot(tk.warn) }, title = e.what)
                for (i in due) {
                    val on = LogDoc.ticked(st.log, "inbox", i.key, i.done)
                    RowItem(lead = { CheckDot(on, tk.accent, 28.dp) { Store.tick("inbox", i.key, !on) } }, title = i.what, sub = if (i.steps.isEmpty()) null else "${i.steps.count { LogDoc.ticked(st.log, "inbox", it.key, it.done) }}/${i.steps.size}")
                    if (i.links.isNotEmpty() && !on) LinkChips(i.links, i.what, tk.accent, Modifier.padding(start = 40.dp, bottom = 6.dp))
                    for (sub in i.steps) {
                        val so = LogDoc.ticked(st.log, "inbox", sub.key, sub.done)
                        RowItem(Modifier.padding(start = 28.dp), lead = { CheckDot(so, tk.accent, 24.dp) { Store.tick("inbox", sub.key, !so) } }, title = sub.text, titleColor = if (so) tk.muted else tk.fg)
                        if (sub.links.isNotEmpty() && !so) LinkChips(sub.links, sub.text, tk.accent, Modifier.padding(start = 64.dp, bottom = 6.dp))
                    }
                }
                for (c in day.coming) RowItem(lead = { Dot(tk.line) }, title = c.replaceFirstChar { it.uppercase() })
            }
        }
        item { Label("Bugs me"); BugsCard() }
        item { Label("Wish"); Wish() }
    }
}

@Composable
private fun Wish() {
    val tk = t()
    var text by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    PCard {
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), placeholder = { Text("A wish…") }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = tk.accent, unfocusedBorderColor = tk.line), shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(msg, Modifier.weight(1f), color = tk.muted, fontSize = 13.sp)
            PButton("Add") {
                val w = text.trim(); if (w.isEmpty()) return@PButton
                Store.count("wish")
                Store.sendWish(w) { ok -> msg = if (ok) "✓" else "Offline"; if (ok) text = "" }
            }
        }
    }
}
