package com.mattlabs.planlab.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattlabs.planlab.core.Progress
import com.mattlabs.planlab.core.ProgressCalc
import com.mattlabs.planlab.core.SHORT_DAY
import com.mattlabs.planlab.data.Store
import kotlinx.coroutines.delay
import java.time.LocalDate

fun openBug(ctx: Context, from: String, camera: Boolean = false) {
    ctx.startActivity(Intent(ctx, BugActivity::class.java).putExtra("from", from).putExtra("camera", camera).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun areaColor(tk: Tokens, a: String): Color = when (a) {
    "Body" -> tk.body; "Music" -> tk.piano; "Home" -> tk.home; "Kitchen" -> tk.eat; "Elizabeth" -> tk.voice; else -> tk.accent
}

/** Level, the bar to the next one, and the streak. Used on Today and at the top of Progress. */
@Composable
fun LevelCard(p: Progress, onClick: (() -> Unit)? = null) {
    val tk = t()
    PCard(onClick = onClick, color = tk.surface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(tk.accent, tk.piano))), contentAlignment = Alignment.Center) {
                Text("${p.level.n}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.level.name, style = MaterialTheme.typography.titleMedium, color = tk.fg, fontWeight = FontWeight.ExtraBold)
                Text(p.next?.let { "${it.xp - p.xp} XP to ${it.name}" } ?: "${p.xp} XP", color = tk.muted, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (p.streak > 0) "🔥 ${p.streak}" else "🔥 0", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = if (p.streak > 0) tk.warn else tk.muted)
                Text(if (p.streak == 1) "day" else "days", color = tk.muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(progress = { p.intoLevel }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = tk.accent, trackColor = tk.sunk, drawStopIndicator = {})
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("+${p.today} XP today", if (p.today > 0) tk.ok else tk.muted, if (p.today > 0) tk.okSoft else tk.sunk)
            if (p.shieldedToday) Pill("🛡 Yesterday shielded", tk.accent, tk.accentSoft)
            else if (p.shieldReady) Pill("🛡 Shield ready", tk.muted, tk.sunk)
        }
    }
}

/** Open "bugs me" captures, each with a Fixed button, plus the capture button. */
@Composable
fun BugsCard() {
    val tk = t()
    val ctx = LocalContext.current
    val st by Store.state.collectAsState()
    PCard(pad = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        for ((n, text) in st.bugs.take(6)) {
            RowItem(lead = { Dot(tk.home) }, title = text.replaceFirstChar { it.uppercase() }, sub = if (n == 0) "Sending…" else "Claude turns it into steps") {
                if (n > 0) Text("Fixed +${st.rewards.bugFixed}", Modifier.clip(CircleShape).background(tk.okSoft).clickable { Store.fixBug(n) }.padding(horizontal = 10.dp, vertical = 6.dp), color = tk.ok, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        if (st.bugs.isNotEmpty()) Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PButton("This bugs me", Modifier.weight(1.4f), tk.home) { openBug(ctx, "today") }
            PButton("📷 Snap it", Modifier.weight(1f), tk.sunk, tk.fg) { openBug(ctx, "today", camera = true) }
        }
    }
}

@Composable
fun ProgressScreen() {
    val st by Store.state.collectAsState()
    val tk = t()
    val p = st.progress ?: return
    val r = st.rewards
    val today = LocalDate.now()
    LazyColumn(Modifier.fillMaxWidth().statusBarsPadding(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
        item {
            Text("${p.xp} XP all time", color = tk.muted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Progress", style = MaterialTheme.typography.displaySmall, color = tk.fg)
            Spacer(Modifier.height(14.dp))
            LevelCard(p)
        }
        item {
            Label("Areas")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                p.areas.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for ((a, xp) in pair) {
                            val (lv, f) = ProgressCalc.areaLevel(xp, r)
                            val c = areaColor(tk, a)
                            PCard(Modifier.weight(1f), pad = PaddingValues(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(a, Modifier.weight(1f), color = c, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                                    Text("Lv $lv", color = tk.fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = c, trackColor = tk.sunk, drawStopIndicator = {})
                                Text("$xp XP", Modifier.padding(top = 6.dp), color = tk.muted, fontSize = 12.sp)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item { Label("Bugs me"); BugsCard() }
        if (p.milestones.isNotEmpty()) item {
            Label("Milestones")
            PCard(pad = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                for ((m, done) in p.milestones) {
                    val d = m.date
                    val sub = when {
                        done -> "Done"
                        d == null -> m.when_
                        else -> {
                            val n = java.time.temporal.ChronoUnit.DAYS.between(today, d)
                            (if (m.when_.trim().startsWith("~")) "About " else "") + d.format(SHORT_DAY) + when { n > 1 -> " · in $n days"; n == 1L -> " · tomorrow"; n == 0L -> " · today"; else -> "" }
                        }
                    }
                    RowItem(lead = { CheckDot(done, tk.ok, 28.dp) { Store.milestone(m.key, !done) } }, title = m.name, sub = sub, titleColor = if (done) tk.muted else tk.fg) {
                        Pill("+${m.xp}", if (done) tk.ok else tk.muted, if (done) tk.okSoft else tk.sunk)
                    }
                }
            }
        }
        item {
            Label("How XP works")
            PCard(pad = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                for ((what, n) in listOf("Tick a step" to r.step, "Finish an area" to r.area, "Finish the whole day" to r.day, "Log a bugs me" to r.bugLogged,
                    "Fix a bugs me" to r.bugFixed, "A habit kept 7 days" to r.habit, "A wish done" to r.wish)) {
                    RowItem(title = what) { Text("+$n", color = tk.accent, fontWeight = FontWeight.Bold) }
                }
                Text("A day counts for your streak when one area is finished. One missed day a week is shielded. XP never goes down.", Modifier.padding(vertical = 8.dp), color = tk.muted, fontSize = 13.sp)
            }
        }
    }
}

/** "+5 XP" that floats in when a tick earns something, and a card when a level is reached. */
@Composable
fun BoxScope.GainOverlay() {
    val st by Store.state.collectAsState()
    val tk = t()
    val haptic = LocalHapticFeedback.current
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(st.gainAt) {
        if (st.gainAt > 0 && System.currentTimeMillis() - st.gainAt < 3000) { show = true; delay(1500); show = false }
    }
    AnimatedVisibility(show, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp), enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
        Text("+${st.gain} XP", Modifier.clip(CircleShape).background(tk.ok).padding(horizontal = 16.dp, vertical = 8.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
    val lv = st.levelUp
    LaunchedEffect(lv) { if (lv != null) repeat(2) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); delay(180) } }
    AnimatedVisibility(lv != null, Modifier.align(Alignment.Center), enter = scaleIn() + fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { Store.clearLevelUp() }, contentAlignment = Alignment.Center) {
            Column(Modifier.padding(32.dp).clip(RoundedCornerShape(28.dp)).background(tk.surface).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎉", fontSize = 48.sp)
                Text("Level ${lv?.n ?: ""}", style = MaterialTheme.typography.displaySmall, color = tk.fg, fontWeight = FontWeight.ExtraBold)
                Text(lv?.name ?: "", style = MaterialTheme.typography.titleLarge, color = tk.accent, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                PButton("Nice", Modifier.fillMaxWidth()) { Store.clearLevelUp() }
            }
        }
    }
}
