package com.mattlabs.planlab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattlabs.planlab.core.LogDoc
import com.mattlabs.planlab.data.Store
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WeekScreen() {
    val st by Store.state.collectAsState()
    val tk = t()
    val today = LocalDate.now()
    var weekIdx by remember { mutableStateOf(st.plan.weeks.indexOfFirst { it == st.plan.weekOf(today) }.takeIf { it >= 0 } ?: (st.plan.weeks.size - 1)) }
    val w = st.plan.weeks.getOrNull(weekIdx)
    var sel by remember(weekIdx) { mutableStateOf(w?.days?.firstOrNull { it.date == today }?.date ?: w?.days?.firstOrNull()?.date) }
    val md = DateTimeFormatter.ofPattern("MMM d")

    LazyColumn(Modifier.fillMaxWidth().statusBarsPadding(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
        item {
            Text(w?.let { "${it.start.format(md)} – ${it.start.plusDays(6).format(md)}" } ?: "", color = tk.muted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(if (w != null && w == st.plan.weekOf(today)) "This week" else "Week", style = MaterialTheme.typography.displaySmall, color = tk.fg)
            if (w?.phase?.isNotBlank() == true) Pill(w.phase.replace(Regex("""\s*\(.*\)"""), "").replaceFirstChar { it.uppercase() }, tk.muted, tk.sunk, Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))
        }
        if (w == null) { item { PCard { Text("No week planned yet.", color = tk.muted) } }; return@LazyColumn }
        item {
            PCard(pad = PaddingValues(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (d in w.days) {
                        val done = d.boxes.count { Store.isDone(d, it) }
                        val isSel = d.date == sel
                        Column(Modifier.clip(RoundedCornerShape(14.dp)).background(if (isSel) tk.accentSoft else Color.Transparent).clickable { sel = d.date }.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(d.label.take(1), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (d.date == today) tk.accent else tk.muted)
                            Spacer(Modifier.height(4.dp))
                            Ring(if (d.boxes.isEmpty()) 0f else done / d.boxes.size.toFloat(), if (done == d.boxes.size && done > 0) tk.ok else tk.accent, 34.dp, 4.dp) {
                                Text("${d.date.dayOfMonth}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tk.fg)
                            }
                        }
                    }
                }
            }
            if (w.focus.isNotBlank()) Text(w.focus.replaceFirstChar { it.uppercase() }, Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp), color = tk.fg, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        w.days.firstOrNull { it.date == sel }?.let { d ->
            item {
                Label(d.date.format(DateTimeFormatter.ofPattern("EEEE")))
                PCard(pad = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    for (box in d.boxes) {
                        val on = Store.isDone(d, box)
                        RowItem(lead = { CheckDot(on, tk.kind(box.key), 28.dp) { Store.toggle(d, box) } }, title = Store.LABEL[box.key] ?: box.label.replaceFirstChar { it.uppercase() },
                            sub = if (box.steps.isNotEmpty()) "${Store.stepsDone(d, box)} of ${box.steps.size} steps" else when (box.key) { "body" -> d.body; "meals" -> d.eat.lastOrNull(); "piano", "voice" -> d.practice.firstOrNull { it.lowercase().startsWith(box.key) }; else -> null })
                    }
                    for (c in d.coming) RowItem(lead = { Dot(tk.line) }, title = c.replaceFirstChar { it.uppercase() })
                }
            }
        }
        item {
            Label("Totals")
            val piano = w.days.sumOf { LogDoc.minutes(st.log, it.date.toString(), "piano") }
            val pianoPlan = w.days.sumOf { it.plannedMinutes("piano") }
            val voice = w.days.sumOf { LogDoc.minutes(st.log, it.date.toString(), "voice") }
            val voicePlan = w.days.sumOf { it.plannedMinutes("voice") }
            val bodyDays = w.days.filter { d -> d.boxes.any { it.key == "body" } }
            val bodyDone = bodyDays.count { d -> d.boxes.first { it.key == "body" }.let { Store.isDone(d, it) } }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Total("Workouts", "$bodyDone/${bodyDays.size}", tk.body, Modifier.weight(1f))
                Total("Piano min", "$piano/$pianoPlan", tk.piano, Modifier.weight(1f))
                Total("Voice min", "$voice/$voicePlan", tk.voice, Modifier.weight(1f))
            }
        }
        if (w.glance.isNotEmpty()) item {
            Label("At a glance")
            PCard(pad = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                for ((label, text) in w.glance) RowItem(title = label.replace(Regex("""\s*\(.*\)"""), ""), sub = text)
            }
        }
        item {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (weekIdx > 0) PButton("‹ Last week", Modifier.weight(1f), tk.sunk, tk.fg) { weekIdx-- } else Spacer(Modifier.weight(1f))
                if (weekIdx < st.plan.weeks.size - 1) PButton("Next week ›", Modifier.weight(1f), tk.sunk, tk.fg) { weekIdx++ } else Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Total(label: String, value: String, color: Color, modifier: Modifier) {
    val tk = t()
    PCard(modifier, pad = PaddingValues(12.dp)) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = tk.fg)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
