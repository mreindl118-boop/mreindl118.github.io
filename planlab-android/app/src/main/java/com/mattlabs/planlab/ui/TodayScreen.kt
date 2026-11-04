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
    var openArea by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxWidth().statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(date.format(NICE_DAY), color = tk.muted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(when (date) { today -> "Today"; today.plusDays(1) -> "Tomorrow"; today.minusDays(1) -> "Yesterday"; else -> date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() } },
                        style = MaterialTheme.typography.displaySmall, color = tk.fg)
                }
                IconButton(onClick = { date = date.minusDays(1) }) { Icon(Icons.Filled.KeyboardArrowLeft, "Previous day", tint = tk.fg) }
                IconButton(onClick = { date = date.plusDays(1) }) { Icon(Icons.Filled.KeyboardArrowRight, "Next day", tint = tk.fg) }
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings", tint = tk.muted) }
            }
            if (date != today) Text("Back to today", Modifier.padding(top = 4.dp).clip(CircleShape).background(tk.accent).clickable { date = today }.padding(horizontal = 12.dp, vertical = 5.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (st.error.isNotEmpty()) Text(st.error, Modifier.padding(top = 8.dp), color = tk.warn, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            HeaderPills(b) { onOpen("health") }
            if (day != null) { Spacer(Modifier.height(14.dp)); DayBar(day) }
        }
        if (day == null) {
            item {
                Spacer(Modifier.height(12.dp))
                PCard { Text(if (st.plan.weeks.isEmpty()) (if (loading) "Loading your plan…" else "No plan loaded yet. Pull to refresh in a moment.") else if (st.plan.lastDay()?.isBefore(date) == true) "Not planned yet. Next week's plan arrives Sunday." else "Nothing planned for this day.", color = tk.muted) }
            }
            return@LazyColumn
        }
        item { Label("Next up"); NextUpCard(day) }
        item { LaterToday(day) { openArea = if (openArea == it) null else it } }
        item { Label("Areas"); AreaRings(day) { openArea = if (openArea == it) null else it } }
        val opened = day.boxes.firstOrNull { it.key == openArea && it.steps.isNotEmpty() }
        if (opened != null && !showAll) item { Spacer(Modifier.height(10.dp)); StepCard(day, opened) }
        item { Spacer(Modifier.height(10.dp)); WeekChart(date) }
        item {
            Text(if (showAll) "Hide all steps" else "Show all steps", Modifier.padding(top = 12.dp).clip(CircleShape).background(tk.sunk).clickable { showAll = !showAll }.padding(horizontal = 14.dp, vertical = 8.dp),
                color = tk.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        if (showAll) for (box in TodayPick.order(day.boxes.filter { it.steps.isNotEmpty() })) item { Spacer(Modifier.height(10.dp)); StepCard(day, box) }
        if (date == today) item { Spacer(Modifier.height(16.dp)); FeelRow(date); Spacer(Modifier.height(10.dp)); InsightHead { onOpen("insights") } }
        item { Label("Eat"); EatCard(day, date) }
        item {
            Spacer(Modifier.height(10.dp))
            val pf = LogDoc.pelvic(st.log, date.toString())
            PCard(onClick = { Store.pelvicSet(date) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring(pf / 2f, tk.ok, 44.dp, 5.dp) { Text("$pf", fontWeight = FontWeight.ExtraBold, color = tk.fg) }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Pelvic floor", style = MaterialTheme.typography.titleMedium, color = tk.fg)
                        Text("10 slow, 10 quick · tap after each set", color = tk.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Pill("$pf/2", if (pf >= 2) tk.ok else tk.muted, if (pf >= 2) tk.okSoft else tk.sunk)
                }
            }
        }
        item {
            val due = st.plan.inbox.count { it.date == date && !LogDoc.ticked(st.log, "inbox", it.key, it.done) }
            Text(if (more) "Less" else "More for today" + if (due > 0) " · $due due" else "",
                Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tk.surface).clickable { more = !more }.padding(16.dp),
                color = if (due > 0 && !more) tk.warn else tk.accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        if (!more) return@LazyColumn
        st.progress?.let { p -> item { Spacer(Modifier.height(10.dp)); LevelCard(p) { onOpen("progress") } } }
        if (b.nudges.isNotEmpty()) item {
            Label("Heads up")
            PCard(pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                for (n in b.nudges) RowItem(lead = { Dot(if (n.warn) tk.bad else tk.accent) }, title = n.title, sub = n.detail)
            }
        }
        if (day.meals.isEmpty() && day.eat.isNotEmpty()) item {
            Label("Meals")
            PCard(pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                val names = listOf("Breakfast", "Lunch", "Dinner", "Also")
                day.eat.forEachIndexed { i, m ->
                    RowItem(lead = { Text(names[minOf(i, 3)], Modifier.width(72.dp), color = tk.eat, fontWeight = FontWeight.Bold, fontSize = 13.sp) }, title = m.replace(Regex("""\s*\(~?\d+ g protein\)"""), "").replaceFirstChar { it.uppercase() })
                }
            }
        }
        val events = st.plan.calendar.filter { it.date == date }
        val due = st.plan.inbox.filter { it.date == date }
        val extra = day.practice.filter { p -> day.boxes.none { p.lowercase().startsWith(it.key) } }
        if (events.isNotEmpty() || due.isNotEmpty() || day.coming.isNotEmpty() || extra.isNotEmpty()) item {
            Label("On your radar")
            PCard(pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                for (e in events) RowItem(lead = { Dot(tk.warn) }, title = e.what, sub = e.notes)
                for (i in due) {
                    val on = LogDoc.ticked(st.log, "inbox", i.key, i.done)
                    RowItem(lead = { CheckDot(on, tk.accent, 28.dp) { Store.tick("inbox", i.key, !on) } }, title = i.what, sub = if (i.steps.isEmpty()) "Due today" else "Due today · ${i.steps.count { LogDoc.ticked(st.log, "inbox", it.key, it.done) }} of ${i.steps.size} steps")
                    if (i.links.isNotEmpty() && !on) LinkChips(i.links, i.what, tk.accent, Modifier.padding(start = 40.dp, bottom = 6.dp))
                    for (sub in i.steps) {
                        val so = LogDoc.ticked(st.log, "inbox", sub.key, sub.done)
                        RowItem(Modifier.padding(start = 28.dp), lead = { CheckDot(so, tk.accent, 24.dp) { Store.tick("inbox", sub.key, !so) } }, title = sub.text, titleColor = if (so) tk.muted else tk.fg)
                        if (sub.links.isNotEmpty() && !so) LinkChips(sub.links, sub.text, tk.accent, Modifier.padding(start = 64.dp, bottom = 6.dp))
                    }
                }
                for (c in day.coming) RowItem(lead = { Dot(tk.line) }, title = c.replaceFirstChar { it.uppercase() })
                for (x in extra) RowItem(lead = { Dot(tk.line) }, title = x.replaceFirstChar { it.uppercase() }, sub = "If you feel like it")
            }
        }
        item { Label("Bugs me"); BugsCard() }
        item { Label("Want to do something?"); Wish() }
    }
}


/** Readiness and the countdown, as two small pills. Tap readiness for the health screen. */
@Composable
private fun HeaderPills(b: Coach.Brief, onHealth: () -> Unit) {
    val tk = t()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val r = b.readiness
        val c = r?.let { tk.score(it.score) } ?: tk.muted
        Text(if (r != null) "${r.score} ready" else "Connect health", Modifier.clip(CircleShape).background(c.copy(alpha = if (tk.dark) 0.22f else 0.12f)).clickable { onHealth() }.padding(horizontal = 12.dp, vertical = 6.dp),
            color = c, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        val d = b.daysToBig
        if (d != null && b.bigName.isNotEmpty()) Pill(when { d > 1 -> "$d days to ${b.bigName}"; d == 1L -> "${b.bigName.replaceFirstChar { it.uppercase() }} tomorrow"; d == 0L -> "${b.bigName.replaceFirstChar { it.uppercase() }} today"; else -> "Day ${-d} after" }, tk.warn, tk.warnSoft)
    }
}

@Composable
private fun Wish() {
    val tk = t()
    var text by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    PCard {
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), placeholder = { Text("A date idea, a trip, a thing to fix…") }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = tk.accent, unfocusedBorderColor = tk.line), shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(msg.ifEmpty { "Claude gives it a date tomorrow morning." }, Modifier.weight(1f), color = tk.muted, fontSize = 13.sp)
            PButton("Add") {
                val w = text.trim(); if (w.isEmpty()) return@PButton
                Store.count("wish")
                Store.sendWish(w) { ok -> msg = if (ok) "Added \"$w\"." else "Couldn't add it. Try again online."; if (ok) text = "" }
            }
        }
    }
}
