package com.mattlabs.planlab.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattlabs.planlab.core.Box
import com.mattlabs.planlab.core.Day
import com.mattlabs.planlab.core.Md
import com.mattlabs.planlab.data.Store
import kotlinx.coroutines.delay

/** One area's steps as a checklist: tick each one. The next open step is highlighted; "N min" steps get a timer. */
@Composable
fun StepCard(day: Day, box: Box) {
    val tk = t()
    val color = tk.kind(box.key)
    val n = Store.stepsDone(day, box)
    val all = Store.isDone(day, box)
    var open by remember(all) { mutableStateOf(!all) }
    val next = Store.nextStep(day, box)
    PCard(pad = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text(Store.LABEL[box.key] ?: box.label.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Pill(if (all) "Done" else "$n of ${box.steps.size}", if (all) tk.ok else color, if (all) tk.okSoft else tk.sunk)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { if (box.steps.isEmpty()) 0f else n / box.steps.size.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = color, trackColor = tk.sunk, drawStopIndicator = {})
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 6.dp)) {
                for (step in box.steps) {
                    val on = Store.stepDone(day, step)
                    val isNext = step == next
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (isNext) color.copy(alpha = if (tk.dark) 0.18f else 0.09f) else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(horizontal = 6.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CheckDot(on, color, 28.dp) { Store.setStep(day, box, step, !on) }
                        Column(Modifier.weight(1f)) {
                            Text(step.label, color = if (on) tk.muted else tk.fg, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal, textDecoration = if (on) TextDecoration.LineThrough else null)
                            if (step.links.isNotEmpty() && !on) LinkChips(step.links, step.label, color, Modifier.padding(top = 6.dp))
                        }
                        val mins = Md.minutes(step.label)
                        if (mins > 0 && !on) StepTimer(mins, color) { Store.setStep(day, box, step, true) }
                    }
                }
            }
        }
    }
}

/** A step's links as buttons: the first one filled, the rest outlined. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LinkChips(links: List<com.mattlabs.planlab.core.Link>, stepLabel: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val tk = t()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.foundation.layout.FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        links.take(3).forEachIndexed { i, l ->
            val ext = !l.url.startsWith("app:")
            Text(Links.name(l, stepLabel) + if (ext) " ↗" else " ›",
                Modifier.clip(CircleShape).background(if (i == 0) color else tk.sunk).clickable { Links.open(ctx, l, "step") }.padding(horizontal = 12.dp, vertical = 7.dp),
                color = if (i == 0) androidx.compose.ui.graphics.Color.White else color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

/** Tap to start a countdown; at zero it buzzes and ticks the step. Tap again to stop. */
@Composable
fun StepTimer(minutes: Int, color: androidx.compose.ui.graphics.Color, onDone: () -> Unit) {
    val tk = t()
    val haptic = LocalHapticFeedback.current
    var endAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAt) {
        while (endAt > 0) {
            now = System.currentTimeMillis()
            if (now >= endAt) {
                repeat(3) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); delay(250) }
                endAt = 0; onDone(); break
            }
            delay(250)
        }
    }
    val running = endAt > 0
    val left = ((endAt - now) / 1000).coerceAtLeast(0)
    Text(if (running) "%d:%02d".format(left / 60, left % 60) else "▶ $minutes:00",
        Modifier.clip(CircleShape).background(if (running) color else tk.sunk).clickable { if (!running) Store.count("timer"); endAt = if (running) 0 else System.currentTimeMillis() + minutes * 60_000L }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = if (running) androidx.compose.ui.graphics.Color.White else color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
}

