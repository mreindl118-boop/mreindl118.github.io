package com.mattlabs.planlab.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.mattlabs.planlab.core.Coach
import com.mattlabs.planlab.core.Metric
import com.mattlabs.planlab.data.Store
import com.mattlabs.planlab.health.HealthReader
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Asks Health Connect for every read permission planLAB uses, then reads. */
@Composable
fun rememberHealthConnect(): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        scope.launch { Store.readHealth() }
    }
    return {
        when (HealthReader.status(ctx)) {
            HealthConnectClient.SDK_AVAILABLE -> launcher.launch(HealthReader.ALL)
            else -> openHealthConnectStore(ctx)
        }
    }
}

fun openHealthConnectStore(ctx: Context) {
    val i = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${HealthReader.PROVIDER}&url=healthconnect%3A%2F%2Fonboarding")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(i) }.onFailure {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${HealthReader.PROVIDER}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun HealthScreen(onSettings: () -> Unit, onInsights: () -> Unit = {}) {
    val st by Store.state.collectAsState()
    val tk = t()
    val connect = rememberHealthConnect()
    val today = LocalDate.now()
    val h = st.health
    val b: Coach.Brief = Store.brief(today)
    val shown = h.metricsWithData()

    LazyColumn(Modifier.fillMaxWidth().statusBarsPadding(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.phase.label, color = tk.muted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Body", style = MaterialTheme.typography.displaySmall, color = tk.fg)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings", tint = tk.muted) }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (!h.connected || h.granted < HealthReader.TYPES.size) item {
            PCard(color = tk.accentSoft) {
                Text(if (!h.connected) "Connect your health data" else "Allow the rest of your health data", style = MaterialTheme.typography.titleMedium, color = tk.fg)
                Text(if (!h.connected) "Sleep, heart, steps, weight and more make today's plan fit how you actually are. It stays in your private planner." else "${h.granted} of ${HealthReader.TYPES.size} kinds allowed. More data, better calls.",
                    color = tk.muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                PButton(if (!h.connected) "Connect Health Connect" else "Allow all", onClick = connect)
            }
            Spacer(Modifier.height(12.dp))
        }
        b.readiness?.let { r ->
            item {
                PCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ring(r.score / 100f, tk.score(r.score), 72.dp, 8.dp) { Text("${r.score}", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = tk.fg) }
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.label, style = MaterialTheme.typography.headlineSmall, color = tk.fg)
                            for (why in r.reasons) Text("· $why", color = tk.muted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)); InsightHead(onInsights) }
        for (group in listOf("Sleep", "Heart", "Move", "Body", "Food")) {
            val ms = shown.filter { it.group == group }
            if (ms.isEmpty()) continue
            item { Label(group) }
            items(ms.chunked(2).size) { i ->
                val pair = ms.chunked(2)[i]
                Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (m in pair) MetricCard(m, Modifier.weight(1f))
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (h.takenAt > 0) item {
            val at = Instant.ofEpochMilli(h.takenAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
            Text("Health Connect · $at", Modifier.padding(top = 12.dp), color = tk.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MetricCard(m: Metric, modifier: Modifier) {
    val tk = t()
    val st by Store.state.collectAsState()
    val h = st.health
    val today = LocalDate.now()
    val v = h.latest(today, m, 30)
    val base = h.baseline(today, m)
    val color = when (m.group) { "Sleep" -> tk.piano; "Heart" -> tk.voice; "Move" -> tk.body; "Food" -> tk.eat; else -> tk.accent }
    PCard(modifier, pad = PaddingValues(14.dp)) {
        Text(m.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tk.muted, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(m.format(v), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = tk.fg)
            if (m.unit.isNotEmpty()) Text(" ${m.unit}", fontSize = 13.sp, color = tk.muted, modifier = Modifier.padding(bottom = 3.dp))
        }
        if (v != null && base != null && m != Metric.BEDTIME) {
            val d = v - base
            val good = m.higherIsBetter?.let { if (it) d >= 0 else d <= 0 }
            val pct = if (base != 0.0) d / base * 100 else 0.0
            if (kotlin.math.abs(pct) >= 3) Text("${if (d > 0) "▲" else "▼"} ${kotlin.math.abs(pct).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = when (good) { true -> tk.ok; false -> tk.warn; null -> tk.muted })
            else Text("Usual", fontSize = 12.sp, color = tk.muted)
        } else Spacer(Modifier.height(16.dp))
        Spacer(Modifier.height(6.dp))
        Spark(h.series(today, m), color, Modifier.fillMaxWidth().height(30.dp))
    }
}
