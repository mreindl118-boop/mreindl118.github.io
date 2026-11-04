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
            Spacer(Modifier.height(14.dp))
            Hero(b, day, onOpen)
            st.progress?.let { Spacer(Modifier.height(10.dp)); LevelCard(it) { onOpen("progress") } }
        }
        if (day == null) {
            item {
                Spacer(Modifier.height(12.dp))
                PCard { Text(if (st.plan.weeks.isEmpty()) (if (loading) "Loading your plan…" else "No plan loaded yet. Pull to refresh in a moment.") else if (st.plan.lastDay()?.isBefore(date) == true) "Not planned yet. Next week's plan arrives Sunday." else "Nothing planned for this day.", color = tk.muted) }
            }
            return@LazyColumn
        }
        item { Label("Your call") ; CallCard(b) }
        item { Label("Check off"); Tiles(day, b) }
        val stepped = day.boxes.filter { it.steps.isNotEmpty() }.sortedBy { Store.MAIN.indexOf(it.key).let { i -> if (i < 0) 99 else i } }
        if (stepped.isNotEmpty()) {
            item { Label("Step by step") }
            for (box in stepped) item { StepCard(day, box); Spacer(Modifier.height(10.dp)) }
        }
        val others = day.boxes.filter { it.key !in Store.MAIN && it.steps.isEmpty() }
        if (others.isNotEmpty()) item {
            Spacer(Modifier.height(10.dp))
            PCard(pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                for (box in others) {
                    val on = Store.isDone(day, box)
                    RowItem(lead = { CheckDot(on, tk.accent, 30.dp) { Store.toggle(day, box) } }, title = box.label.replaceFirstChar { it.uppercase() }, titleColor = if (on) tk.muted else tk.fg)
                }
            }
        }
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
        if (b.nudges.isNotEmpty()) item {
            Label("Heads up")
            PCard(pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                for (n in b.nudges) RowItem(lead = { Dot(if (n.warn) tk.bad else tk.accent) }, title = n.title, sub = n.detail)
            }
        }
        if (day.eat.isNotEmpty()) item {
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
                    for (sub in i.steps) {
                        val so = LogDoc.ticked(st.log, "inbox", sub.key, sub.done)
                        RowItem(Modifier.padding(start = 28.dp), lead = { CheckDot(so, tk.accent, 24.dp) { Store.tick("inbox", sub.key, !so) } }, title = sub.text, titleColor = if (so) tk.muted else tk.fg)
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


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(b: Coach.Brief, day: Day?, onOpen: (String) -> Unit) {
    val tk = t()
    val r = b.readiness
    val c = r?.let { tk.score(it.score) } ?: tk.accent
    val done = day?.boxes?.count { Store.isDone(day, it) } ?: 0
    val n = day?.boxes?.size ?: 0
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
        .background(Brush.linearGradient(listOf(c.copy(alpha = if (tk.dark) 0.32f else 0.16f), tk.surface)))
        .clickable { onOpen("health") }
        .padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(r?.let { it.score / 100f } ?: 0f, c, 96.dp, 10.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(r?.score?.toString() ?: "–", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = tk.fg)
                    Text("ready", fontSize = 11.sp, color = tk.muted, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(r?.label ?: "Connect your health data", style = MaterialTheme.typography.headlineSmall, color = tk.fg)
                Spacer(Modifier.height(4.dp))
                Text(r?.reasons?.joinToString(" · ") ?: "Sleep, heart and steps tune today's plan.", color = tk.muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (n > 0) Pill("$done of $n done", if (done == n) tk.ok else tk.accent, if (done == n) tk.okSoft else tk.accentSoft)
                    val d = b.daysToBig
                    if (d != null && b.bigName.isNotEmpty()) Pill(when { d > 1 -> "$d days to ${b.bigName}"; d == 1L -> "${b.bigName.replaceFirstChar { it.uppercase() }} tomorrow"; d == 0L -> "${b.bigName.replaceFirstChar { it.uppercase() }} today"; else -> "Day ${-d} after" }, tk.warn, tk.warnSoft)
                    Pill(b.phase.short, tk.muted, tk.sunk)
                }
            }
        }
    }
}

@Composable
private fun CallCard(b: Coach.Brief) {
    val tk = t()
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(tk.body))
            Spacer(Modifier.size(10.dp))
            Text(b.body.title, style = MaterialTheme.typography.titleMedium, color = tk.fg, modifier = Modifier.weight(1f))
            if (b.body.easier) Pill("Eased", tk.warn, tk.warnSoft)
        }
        if (b.body.detail.isNotBlank()) Text(b.body.detail, Modifier.padding(start = 20.dp, top = 2.dp), color = tk.muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Protein", "${b.protein} g", b.proteinEaten?.let { "$it eaten" }, tk.eat, Modifier.weight(1f))
            Stat("Water", "${b.waterL} L", null, tk.accent, Modifier.weight(1f))
            b.steps?.let { Stat("Steps", it.title, null, tk.body, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String?, color: Color, modifier: Modifier) {
    val tk = t()
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(tk.sunk).padding(10.dp)) {
        Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = tk.fg, maxLines = 2)
        if (sub != null) Text(sub, fontSize = 12.sp, color = tk.muted)
    }
}

@Composable
private fun Tiles(day: Day, b: Coach.Brief) {
    val tk = t()
    val st by Store.state.collectAsState()
    val main = day.boxes.filter { it.key in Store.MAIN }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        main.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (box in pair) {
                    val on = Store.isDone(day, box)
                    val skipped = !on && LogDoc.skipped(st.log, day.date.toString(), box.key)
                    val color = tk.kind(box.key)
                    val next = if (on) null else Store.nextStep(day, box)
                    val sub = if (next != null) "${Store.stepsDone(day, box)}/${box.steps.size} · ${next.label}" else when (box.key) {
                        "meals" -> "${day.protein().takeIf { it > 0 } ?: b.protein} g protein"
                        "body" -> b.body.title
                        "piano" -> LogDoc.minutes(st.log, day.date.toString(), "piano").let { if (it > 0) "$it of ${day.plannedMinutes("piano")} min" else b.piano?.title?.removePrefix("Piano, ") ?: "" }
                        "voice" -> LogDoc.minutes(st.log, day.date.toString(), "voice").let { if (it > 0) "$it of ${day.plannedMinutes("voice")} min" else b.voice?.title?.removePrefix("Voice, ") ?: "" }
                        else -> ""
                    }
                    PCard(Modifier.weight(1f), color = if (on) color.copy(alpha = if (tk.dark) 0.28f else 0.14f) else tk.surface, onClick = { if (next != null) Store.setStep(day, box, next, true) else Store.toggle(day, box) }, pad = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(Store.LABEL[box.key] ?: box.label, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.height(2.dp))
                                Text(if (skipped) "Skipped" else sub, color = tk.muted, fontSize = 13.sp, maxLines = 2, lineHeight = 16.sp)
                            }
                            CheckDot(on, color, 30.dp) { Store.toggle(day, box) }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
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
