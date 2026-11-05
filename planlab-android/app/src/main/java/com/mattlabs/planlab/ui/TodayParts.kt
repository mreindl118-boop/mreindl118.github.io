package com.mattlabs.planlab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattlabs.planlab.R
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

    fun count(day: Day): Pair<Int, Int> = TodayPick.count(day, { Store.stepDone(day, it) }, { Store.isDone(day, it) })

    fun label(box: PBox): String = Store.LABEL[box.key] ?: box.label.replaceFirstChar { it.uppercase() }

    /** A step in four words at most: the move's name, else the step's first words. */
    fun short(step: PBox): String {
        Moves.find(Store.state.value.plan.moves, step.label, step.links)?.let { return it.name }
        return TodayPick.words(MoveSheet.shortLabel(step.label), 4)
    }

    /** The dose, only when it is short and mostly numbers ("3 × 10", "5 min"). */
    fun dose(step: PBox): String? {
        Moves.find(Store.state.value.plan.moves, step.label, step.links)?.field("Dose")?.let { return it.takeIf { d -> d.length <= 16 } }
        // No how-to card: the numbers after the name ("Dead bug 3 × 10 each side" -> "3 × 10 each side").
        val full = MoveSheet.shortLabel(step.label).split(" — ", " (").first().trim()
        val rest = full.removePrefix(short(step)).trim().trimStart(',', '-', ':').trim()
        return rest.takeIf { it.isNotEmpty() && (it.first().isDigit() || it.first() == '×') && it.length <= 18 }
    }
}

/** Each area's icon. */
fun isPelvic(key: String, label: String = ""): Boolean =
    key == "pf" || key.contains("pelvic") || key.contains("kegel") || label.lowercase().contains("pelvic")

fun areaIcon(key: String): Int = if (isPelvic(key)) R.drawable.ic_a_pelvic else when (key) {
    "body" -> R.drawable.ic_a_body
    "move" -> R.drawable.ic_a_move
    "piano" -> R.drawable.ic_a_piano
    "voice" -> R.drawable.ic_a_voice
    "clean", "home" -> R.drawable.ic_a_clean
    "meals", "eat" -> R.drawable.ic_a_eat
    "water" -> R.drawable.ic_a_water
    "pelvic" -> R.drawable.ic_a_pelvic
    "mind" -> R.drawable.ic_a_mind
    "bugs" -> R.drawable.ic_a_bug
    else -> R.drawable.ic_a_dot
}

private fun soft(c: Color, dark: Boolean) = c.copy(alpha = if (dark) 0.22f else 0.12f)

@Composable
fun AreaIcon(key: String, color: Color, size: Dp) =
    Icon(painterResource(areaIcon(key)), null, Modifier.size(size), tint = color)

/** A round icon button: no words. */
@Composable
fun RoundButton(icon: Int, color: Color, bg: Color, desc: String, size: Dp = 56.dp, onClick: () -> Unit) =
    Box(Modifier.size(size).clip(CircleShape).background(bg).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), desc, Modifier.size(size * 0.45f), tint = color)
    }

/** The one thing to do now: a big icon, four words, Done. */
@Composable
fun NextUpCard(day: Day, onOpen: (String) -> Unit = {}) {
    val tk = t()
    val st by Store.state.collectAsState()
    val next = TodayData.next(day)
    if (next == null) {
        PCard(color = soft(tk.ok, tk.dark), pad = PaddingValues(28.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(tk.ok), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, Modifier.size(52.dp), tint = Color.White)
                }
                Spacer(Modifier.height(12.dp))
                Text("All done", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = tk.ok)
            }
        }
        return
    }
    val box = next.box
    val step = next.step
    val color = tk.kind(box.key)
    val kind = Moves.kindOf(box, step, day)
    val move = step?.let { Moves.find(st.plan.moves, it.label, it.links) }
    val ctx = LocalContext.current
    PCard(pad = PaddingValues(24.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Tap the icon to see every step of this area.
            Box(Modifier.size(96.dp).clip(CircleShape).background(soft(color, tk.dark)).clickable(enabled = box.steps.isNotEmpty()) { onOpen(box.key) }, contentAlignment = Alignment.Center) {
                AreaIcon(if (isPelvic(box.key, box.label)) "pelvic" else box.key, color, 52.dp)
            }
            Spacer(Modifier.height(14.dp))
            Text(step?.let { TodayData.short(it) } ?: TodayData.label(box), fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold,
                color = tk.fg, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(step?.let { TodayData.dose(it) }, kind?.let { k -> if (k.minutes > 0) "${k.minutes} min" else null }).distinct().joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, Modifier.padding(top = 4.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            if (step != null && box.steps.size > 1) {
                Spacer(Modifier.height(12.dp))
                StepDots(box.steps.size, box.steps.indexOf(step), color)
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step != null && (move != null || box.key == "body" || box.key == "move"))
                    RoundButton(R.drawable.ic_a_info, tk.fg, tk.sunk, "How to") { MoveSheet.show(MoveSheet.Req(day.date, box.key, step.key, null)) }
                Row(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(22.dp)).background(color)
                    .clickable {
                        val name = step?.let { TodayData.short(it) } ?: TodayData.label(box)
                        if (step != null) { Store.setStep(day, box, step, true); Undo.offer("✓ $name") { Store.setStep(day, box, step, false) } }
                        else { Store.toggle(day, box); Undo.offer("✓ $name") { Store.toggle(day, box) } }
                    },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.Check, null, Modifier.size(28.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Done", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                val link = (step?.links ?: box.links).firstOrNull { !it.url.startsWith("app:move/") }
                if (link != null) RoundButton(R.drawable.ic_a_link, tk.fg, tk.sunk, Links.name(link, step?.label ?: box.label)) { Links.open(ctx, link, "step", step?.label ?: box.label) }
            }
            val mins = step?.let { Md.minutes(it.label) } ?: 0
            if (mins > 0 && step != null) { Spacer(Modifier.height(12.dp)); StepTimer(mins, color) { Store.setStep(day, box, step, true) } }
        }
    }
}

/** n dots, the current one wide, done ones filled. */
@Composable
fun StepDots(n: Int, at: Int, color: Color) {
    val tk = t()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until n.coerceAtMost(12)) {
            Box(Modifier.height(8.dp).width(if (i == at) 22.dp else 8.dp).clip(CircleShape).background(if (i <= at) color.copy(alpha = if (i == at) 1f else 0.45f) else tk.sunk))
        }
    }
}

/** One tile in the ring grid. */
data class RingTile(val key: String, val f: Float, val label: String, val color: Color, val onLong: (() -> Unit)? = null, val onTap: () -> Unit)

/** Rings with icons and a one-word name, three per row. Tap does it; press and hold undoes it. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RingGrid(tiles: List<RingTile>, openKey: String?) {
    val tk = t()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (tile in row) {
                    val on = tile.key == openKey
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(if (on) soft(tile.color, tk.dark) else tk.surface)
                        .combinedClickable(onLongClick = tile.onLong, onClick = { tile.onTap() }).padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Ring(tile.f, if (tile.f >= 1f) tk.ok else tile.color, 64.dp, 7.dp) {
                            if (tile.f >= 1f) Icon(Icons.Filled.Check, null, Modifier.size(30.dp), tint = tk.ok)
                            else AreaIcon(tile.key, tile.color, 30.dp)
                        }
                        Text(tile.label, Modifier.padding(top = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tk.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** This week as bars. Only a flame and a number for the streak. */
@Composable
fun WeekChart(date: LocalDate) {
    val tk = t()
    val st by Store.state.collectAsState()
    val week = st.plan.weekOf(date) ?: return
    val today = LocalDate.now()
    PCard(pad = PaddingValues(18.dp)) {
        Row(Modifier.fillMaxWidth().height(92.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            for (i in 0 until 7) {
                val d = week.start.plusDays(i.toLong())
                val day = week.days.firstOrNull { it.date == d }
                val (done, n) = day?.let { TodayData.count(it) } ?: (0 to 0)
                val f = if (n == 0 || d.isAfter(today)) 0f else done / n.toFloat()
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height((6 + 62 * f).dp).clip(RoundedCornerShape(8.dp))
                        .background(when { f >= 1f -> tk.ok; f > 0f -> tk.accent; else -> tk.sunk }))
                    Text(d.dayOfWeek.name.take(1), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (d == today) tk.fg else tk.muted)
                }
            }
            st.progress?.streak?.takeIf { it > 0 }?.let {
                Column(Modifier.padding(start = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔥", fontSize = 22.sp)
                    Text("$it", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = tk.warn)
                }
            }
        }
    }
}
