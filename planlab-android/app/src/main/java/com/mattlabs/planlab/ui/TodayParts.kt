package com.mattlabs.planlab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattlabs.planlab.core.Box as PBox
import com.mattlabs.planlab.core.Day
import com.mattlabs.planlab.core.LogDoc
import com.mattlabs.planlab.core.Md
import com.mattlabs.planlab.core.Moves
import com.mattlabs.planlab.core.TodayPick
import com.mattlabs.planlab.data.Store
import java.time.LocalDate
import java.time.LocalTime

/** Today's helpers: what is open, in one place so every card agrees. */
object TodayData {
    fun open(day: Day, box: PBox): Boolean =
        !Store.isDone(day, box) && !LogDoc.skipped(Store.state.value.log, day.date.toString(), box.key)

    fun next(day: Day): TodayPick.Next? =
        TodayPick.next(day, if (day.date == LocalDate.now()) LocalTime.now() else null, { Store.stepDone(day, it) }, { open(day, it) })

    fun rest(day: Day): List<TodayPick.Next> = TodayPick.rest(day, { Store.stepDone(day, it) }, { open(day, it) })

    fun count(day: Day): Pair<Int, Int> = TodayPick.count(day, { Store.stepDone(day, it) }, { Store.isDone(day, it) })

    fun label(box: PBox): String = Store.LABEL[box.key] ?: box.label.replaceFirstChar { it.uppercase() }

    /** A short name for a step: the move's name, else the text before any "(" or " — ". */
    fun short(step: PBox): String {
        val moves = Store.state.value.plan.moves
        Moves.find(moves, step.label, step.links)?.let { return it.name }
        return MoveSheet.shortLabel(step.label).split(" — ", " - ", " (").first().trim().trimEnd(',', '.', ':')
    }

    /** The dose line under the title: the move's dose, else what came after the short name. */
    fun dose(step: PBox): String? {
        val moves = Store.state.value.plan.moves
        Moves.find(moves, step.label, step.links)?.field("Dose")?.let { return it }
        val full = MoveSheet.shortLabel(step.label)
        val rest = full.removePrefix(short(step)).trim().trimStart('—', '-', ',', ':', '(').trimEnd(')').trim()
        return rest.ifBlank { null }
    }
}

private fun soft(c: Color, dark: Boolean) = c.copy(alpha = if (dark) 0.22f else 0.12f)

/** "x of N" bar under the header. */
@Composable
fun DayBar(day: Day) {
    val tk = t()
    Store.state.collectAsState().value
    val (d, n) = TodayData.count(day)
    if (n == 0) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(progress = { d / n.toFloat() }, modifier = Modifier.weight(1f).height(10.dp).clip(CircleShape),
            color = if (d == n) tk.ok else tk.accent, trackColor = tk.sunk, drawStopIndicator = {})
        Text("$d of $n", color = tk.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** The one thing to do now, big, with Done and How to. */
@Composable
fun NextUpCard(day: Day) {
    val tk = t()
    val st by Store.state.collectAsState()
    val next = TodayData.next(day)
    if (next == null) {
        PCard(color = soft(tk.ok, tk.dark), pad = PaddingValues(22.dp)) {
            Text("All done", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = tk.ok)
            Text("Nothing left on today's plan.", color = tk.fg, fontSize = 16.sp)
        }
        return
    }
    val box = next.box
    val step = next.step
    val color = tk.kind(box.key)
    val kind = Moves.kindOf(box, step, day)
    val idx = step?.let { box.steps.indexOf(it) + 1 } ?: 0
    val move = step?.let { Moves.find(st.plan.moves, it.label, it.links) }
    PCard(pad = PaddingValues(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(soft(color, tk.dark)), contentAlignment = Alignment.Center) {
                Text(TodayData.label(box).take(1), color = color, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            }
            Spacer(Modifier.size(12.dp))
            Text(TodayData.label(box).uppercase() + if (step != null) " · STEP $idx OF ${box.steps.size}" else "",
                Modifier.weight(1f), color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (kind != null) Pill(kind.badge, color, soft(color, tk.dark))
        }
        Spacer(Modifier.height(16.dp))
        Text(step?.let { TodayData.short(it) } ?: TodayData.label(box), fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, color = tk.fg)
        val dose = step?.let { TodayData.dose(it) }
        if (dose != null) Text(dose, Modifier.padding(top = 6.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = tk.muted)
        val links = step?.links?.filter { !it.url.startsWith("app:move/") } ?: box.links
        if (links.isNotEmpty()) LinkChips(links, step?.label ?: box.label, color, Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PButton("Done", Modifier.weight(1f).height(56.dp), color) {
                if (step != null) Store.setStep(day, box, step, true) else Store.toggle(day, box)
            }
            if (step != null && (move != null || box.key == "body" || box.key == "move"))
                PButton("How to", Modifier.height(56.dp), tk.sunk, tk.fg) { MoveSheet.show(MoveSheet.Req(day.date, box.key, step.key, null)) }
            val mins = step?.let { Md.minutes(it.label) } ?: 0
            if (mins > 0 && step != null) StepTimer(mins, color) { Store.setStep(day, box, step, true) }
        }
    }
}

/** Up to three small rows for what comes after the next thing. Tap one to open that area. */
@Composable
fun LaterToday(day: Day, onArea: (String) -> Unit) {
    val tk = t()
    Store.state.collectAsState().value
    val first = TodayData.next(day)
    val later = TodayData.rest(day).filter { it.box.key != first?.box?.key }.take(3)
    if (later.isEmpty()) return
    Label("Later today")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (n in later) {
            val color = tk.kind(n.box.key)
            val kind = if (n.box.key == "move") Moves.kindOf(n.box, n.step, day) else Moves.kindOf(n.box, null, day) ?: Moves.kindOf(n.box, n.step, day)
            val right = kind?.badge ?: if (n.box.steps.isNotEmpty()) "${Store.stepsDone(day, n.box)} of ${n.box.steps.size}" else ""
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tk.surface).clickable { onArea(n.box.key) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Dot(color)
                Text(TodayData.label(n.box), Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = tk.fg, maxLines = 1)
                Text(right, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

/** One ring per area, two per row. Tap to open that area's steps. */
@Composable
fun AreaRings(day: Day, onArea: (String) -> Unit) {
    val tk = t()
    Store.state.collectAsState().value
    val boxes = TodayPick.order(day.boxes)
    if (boxes.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        boxes.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (box in pair) {
                    val color = tk.kind(box.key)
                    val done = Store.isDone(day, box)
                    val n = box.steps.size
                    val d = if (done) n else Store.stepsDone(day, box)
                    val skipped = !done && LogDoc.skipped(Store.state.value.log, day.date.toString(), box.key)
                    val nx = if (done) null else Store.nextStep(day, box)
                    PCard(Modifier.weight(1f), onClick = { if (n > 0) onArea(box.key) else Store.toggle(day, box) }, pad = PaddingValues(16.dp)) {
                        Ring(if (n > 0) d / n.toFloat() else if (done) 1f else 0f, color, 60.dp, 7.dp) {
                            Text(if (n > 0) "$d/$n" else if (done) "✓" else "0/1", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = tk.fg)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(TodayData.label(box), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color)
                        Text(when { done -> "Done"; skipped -> "Skipped"; nx != null -> "Next: " + TodayData.short(nx); else -> "Tap to tick" },
                            color = tk.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** This week as bars: how much of each day got done. Today is outlined; future days are stubs. */
@Composable
fun WeekChart(date: LocalDate) {
    val tk = t()
    val st by Store.state.collectAsState()
    val week = st.plan.weekOf(date) ?: return
    val today = LocalDate.now()
    PCard(pad = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This week", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = tk.fg)
            st.progress?.streak?.takeIf { it > 0 }?.let { Text("$it-day streak", color = tk.warn, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            for (i in 0 until 7) {
                val d = week.start.plusDays(i.toLong())
                val day = week.days.firstOrNull { it.date == d }
                val (done, n) = day?.let { TodayData.count(it) } ?: (0 to 0)
                val f = if (n == 0 || d.isAfter(today)) 0f else done / n.toFloat()
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height((6 + 64 * f).dp).clip(RoundedCornerShape(8.dp))
                        .background(when { f >= 1f -> tk.ok; f > 0f -> tk.accent; else -> tk.sunk }))
                    Text(d.dayOfWeek.name.take(1), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (d == today) tk.fg else tk.muted)
                }
            }
        }
    }
}
