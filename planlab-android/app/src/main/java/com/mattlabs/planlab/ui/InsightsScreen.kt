package com.mattlabs.planlab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattlabs.planlab.data.Store
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/*
 * Insights: log/insights.json, worked out twice a day in the private repo by
 * projects/planlab/tools/insights.py. The app only shows it, word for word.
 */

private val FEEL = listOf("Rough", "Low", "OK", "Good", "Great")

/** "How do you feel?" One tap, 1 to 5, stored as ticks["feel-<date>"]. */
@Composable
fun FeelRow(date: LocalDate) {
    val st by Store.state.collectAsState()
    val tk = t()
    val haptic = LocalHapticFeedback.current
    val picked = Store.feel(st.log, date)
    PCard(pad = PaddingValues(14.dp)) {
        Text("How do you feel?", style = MaterialTheme.typography.titleMedium, color = tk.fg)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FEEL.forEachIndexed { i, label ->
                val on = picked == i + 1
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) tk.accent else tk.sunk)
                        .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); Store.setFeel(date, i + 1) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("${i + 1}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = if (on) Color.White else tk.fg)
                    Text(label, fontSize = 11.sp, color = if (on) Color.White else tk.muted, maxLines = 1)
                }
            }
        }
    }
}

/** This week's one line, on Today. Tap for the whole picture. */
@Composable
fun InsightHead(onOpen: (() -> Unit)?) {
    val st by Store.state.collectAsState()
    val tk = t()
    val h = Store.insights(st.plan)?.optJSONObject("headline") ?: return
    val kind = h.optString("kind")
    PCard(color = tk.accentSoft, onClick = onOpen) {
        Text(when (kind) { "likely" -> "This week's insight"; "hint" -> "An early hint"; "trend" -> "This week"; else -> "Learning you" }.uppercase(),
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tk.accent)
        Spacer(Modifier.height(4.dp))
        Text(h.optString("text"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = tk.fg)
        h.optString("try").takeIf { it.isNotBlank() }?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = tk.muted) }
    }
}

@Composable
fun InsightsScreen(onBack: () -> Unit) {
    val st by Store.state.collectAsState()
    val tk = t()
    val ins = Store.insights(st.plan)
    LaunchedEffect(Unit) { Store.count("screen:insights") }

    LazyColumn(Modifier.fillMaxWidth().statusBarsPadding(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.KeyboardArrowLeft, "Back", tint = tk.fg) }
                Column(Modifier.weight(1f)) {
                    Text("Patterns in your own days", color = tk.muted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Insights", style = MaterialTheme.typography.displaySmall, color = tk.fg)
                }
            }
            Spacer(Modifier.height(12.dp))
            FeelRow(LocalDate.now())
            Spacer(Modifier.height(10.dp))
        }
        if (ins == null) {
            item { PCard { Text("Insights arrive after the first nightly run. They come from your ticks, check-ins and health data.", color = tk.muted) } }
            return@LazyColumn
        }
        item { InsightHead(null) }

        val findings = ins.optJSONArray("findings").objs()
        if (findings.isNotEmpty()) {
            item { Label("Patterns") }
            for (f in findings.take(6)) item {
                PCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(f.optString("text"), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = tk.fg)
                        Spacer(Modifier.width(8.dp))
                        val likely = f.optString("kind_of") == "likely"
                        Pill(if (likely) "Likely" else "Hint", if (likely) tk.ok else tk.muted, if (likely) tk.okSoft else tk.sunk)
                    }
                    val g = f.optJSONArray("groups").objs()
                    if (g.size == 2 && f.optString("id").contains("~did:")) {
                        Spacer(Modifier.height(8.dp))
                        Bar(g[0].optDouble("value"), tk.warn)
                        Spacer(Modifier.height(4.dp))
                        Bar(g[1].optDouble("value"), tk.ok)
                    }
                    f.optString("try").takeIf { it.isNotBlank() }?.let { Text(it, Modifier.padding(top = 8.dp), color = tk.muted, style = MaterialTheme.typography.bodyMedium) }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        val series = ins.optJSONObject("series")
        val trends = ins.optJSONArray("trends").objs().filter { it.optString("text").isNotBlank() }
        if (trends.isNotEmpty()) {
            item { Label("This week vs your usual") }
            item {
                PCard(pad = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    for (tr in trends) Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr.optString("text"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = tk.fg)
                        Spacer(Modifier.width(10.dp))
                        val color = when { tr.isNull("good") || !tr.has("good") -> tk.muted; tr.optBoolean("good") -> tk.ok; else -> tk.warn }
                        Spark(series.values(tr.optString("key")).takeLast(28), color, Modifier.width(84.dp).height(28.dp))
                    }
                }
            }
        }

        val waiting = ins.optJSONArray("waiting").objs()
        if (waiting.isNotEmpty()) {
            val c = ins.optJSONObject("counts")
            item { Label("Coming soon") }
            item {
                PCard(pad = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    for (w in waiting.take(4)) Column(Modifier.padding(vertical = 8.dp)) {
                        val have = w.optInt("have"); val need = w.optInt("need")
                        Text(w.optString("label"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = tk.fg)
                        Spacer(Modifier.height(4.dp))
                        Bar(have.toDouble() / (have + need).coerceAtLeast(1), tk.accent)
                        Text("$need more day${if (need == 1) "" else "s"}", fontSize = 12.sp, color = tk.muted)
                    }
                    if (c != null) Text("${c.optInt("planner_days")} planner days · ${c.optInt("sleep_days")} nights of sleep · ${c.optInt("feel_days")} check-ins",
                        Modifier.padding(vertical = 8.dp), fontSize = 12.sp, color = tk.muted)
                }
            }
        }
        item { Text(ins.optString("note"), Modifier.padding(top = 14.dp), fontSize = 12.sp, color = tk.muted) }
    }
}

@Composable
private fun Bar(f: Double, color: Color) {
    val tk = t()
    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(tk.sunk)) {
        Box(Modifier.fillMaxWidth(f.coerceIn(0.0, 1.0).toFloat()).fillMaxHeight().clip(CircleShape).background(color))
    }
}

private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONObject?.values(key: String): List<Double?> {
    val a = this?.optJSONArray(key) ?: return emptyList()
    return (0 until a.length()).map { if (a.isNull(it)) null else a.optDouble(it) }
}
