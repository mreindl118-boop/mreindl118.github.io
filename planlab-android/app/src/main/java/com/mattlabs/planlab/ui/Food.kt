package com.mattlabs.planlab.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattlabs.planlab.core.Day
import com.mattlabs.planlab.core.FoodRow
import com.mattlabs.planlab.core.LogDoc
import com.mattlabs.planlab.core.Macros
import com.mattlabs.planlab.data.OpenFood
import com.mattlabs.planlab.data.Store
import com.mattlabs.planlab.health.FoodWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

// Eat is tracked, not ticked (Matt, 2026-11-04): planned meals are one tap, anything else is typed and looked up
// on the spot ("skyline 3-way, 2 coneys"), and the day's calories and protein show against his targets.

private fun n(i: Int) = "%,d".format(i)
private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

@Composable
fun MacroLine(label: String, have: Int, want: Int, unit: String, color: Color, modifier: Modifier = Modifier) {
    val tk = t()
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.weight(1f))
            Text("${n(have)} / ${n(want)} $unit", fontSize = 12.sp, color = tk.muted)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(tk.sunk)) {
            val f = if (want > 0) (have.toFloat() / want).coerceIn(0f, 1f) else 0f
            if (f > 0f) Box(Modifier.fillMaxWidth(f).height(7.dp).clip(CircleShape).background(color))
        }
    }
}

/** The day's numbers: a calorie ring and protein, carbs and fat against target. */
@Composable
fun EatTotals(date: LocalDate) {
    val tk = t()
    val st by Store.state.collectAsState()
    val have = remember(st.version, st.log, date) { Store.eaten(date) }
    val want = remember(st.version, st.plan, st.health, date) { Store.foodTargets(date) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Ring(if (want.kcal > 0) have.kcal / want.kcal.toFloat() else 0f, tk.eat, 84.dp, 9.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(n(have.kcal), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = tk.fg)
                Text("of ${n(want.kcal)}", fontSize = 10.sp, color = tk.muted, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            MacroLine("Protein", have.protein, want.protein, "g", tk.eat)
            MacroLine("Carbs", have.carbs, want.carbs, "g", tk.accent)
            MacroLine("Fat", have.fat, want.fat, "g", tk.piano)
        }
    }
    val (nib, nibK) = remember(st.version, st.log, date) { Store.nibbles(date) }
    if (nib > 0) Text("🤏 $nib ${if (nib == 1) "nibble" else "nibbles"} · ${n(nibK)} kcal of it", Modifier.padding(top = 8.dp), fontSize = 13.sp, color = tk.muted, fontWeight = FontWeight.SemiBold)
}

/** One tap per nibble: the bites, handfuls and tastes that feel like they don't count. */
@Composable
fun NibbleRow(date: LocalDate, onLogged: (Macros) -> Unit) {
    val tk = t()
    val st by Store.state.collectAsState()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (nb in st.foods.nibbles) {
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(tk.sunk).clickable { Store.addNibble(date, nb.id); onLogged(nb.m) }
                .padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(nb.icon, fontSize = 20.sp)
                Text(nb.name, fontSize = 11.sp, color = tk.fg, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("${nb.m.kcal}", fontSize = 10.sp, color = tk.muted)
            }
        }
    }
}

/** Today's Eat card: numbers, the plan's meals as one-tap logs, what's been logged, and the food box. */
@Composable
fun EatCard(day: Day?, date: LocalDate, alwaysOpen: Boolean = false) {
    val tk = t()
    val st by Store.state.collectAsState()
    var open by remember { mutableStateOf(alwaysOpen) }
    val entries = remember(st.version, st.log, date) { Store.foodOn(date) }
    PCard {
        EatTotals(date)
        if (day != null && day.meals.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            for (step in day.meals) {
                val p = remember(step.key, st.foods, st.mine) { Store.planned(step) }
                if (p.meal.isEmpty()) {
                    // Not a meal (water, a rule): a plain tick.
                    val on = LogDoc.ticked(st.log, date.toString(), step.key, step.done)
                    RowItem(lead = { CheckDot(on, tk.eat, 28.dp) { Store.tick(date.toString(), step.key, !on) } }, title = step.label, titleColor = if (on) tk.muted else tk.fg)
                } else {
                    val logged = entries.any { it.plan == step.key }
                    RowItem(lead = { CheckDot(logged, tk.eat, 28.dp) { Store.logPlanned(day, step) } }, title = "${cap(p.meal)} · ${p.name}",
                        sub = "${p.m.protein} g protein · ${if (p.src == "plan-est") "~" else ""}${n(p.m.kcal)} kcal" + if (logged) " · logged" else " · tap when eaten",
                        titleColor = if (logged) tk.muted else tk.fg)
                }
            }
        }
        val extra = entries.filter { it.plan.isEmpty() && it.src != "nibble" }
        val nibs = entries.filter { it.src == "nibble" }
        if (nibs.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            RowItem(lead = { Text("🤏", fontSize = 20.sp) }, title = "${nibs.size} ${if (nibs.size == 1) "nibble" else "nibbles"}",
                sub = "${n(nibs.sumOf { it.kcal })} kcal · " + nibs.groupBy { it.name }.entries.joinToString(", ") { "${it.value.size} ${it.key.lowercase()}" }) {
                Text("−", Modifier.clip(CircleShape).clickable { Store.deleteFood(date, nibs.last().id) }.padding(horizontal = 12.dp, vertical = 6.dp), color = tk.muted, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
        if (extra.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            for (e in extra) RowItem(lead = { Dot(tk.eat) }, title = e.name + if (e.qty != 1.0) " ×${fmtQty(e.qty)}" else "",
                sub = "${e.at} · ${n(e.kcal)} kcal · ${e.protein} g protein" + if (e.src == "guess") " · not found, Claude checks it" else "") {
                Text("✕", Modifier.clip(CircleShape).clickable { Store.deleteFood(date, e.id) }.padding(8.dp), color = tk.muted, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (open || alwaysOpen) FoodLogger(date, autoFocus = !alwaysOpen) { open = false }
        else PButton("+  Track food", Modifier.fillMaxWidth(), tk.eat) { Store.count("screen:food"); open = true }
    }
}

fun fmtQty(q: Double): String = if (q == q.roundToInt().toDouble()) q.roundToInt().toString() else "%.2f".format(q).trimEnd('0').trimEnd('.')

/** The food box: type what you ate, see the numbers as you type, adjust, log. */
@Composable
fun FoodLogger(date: LocalDate, autoFocus: Boolean, onLogged: (Macros) -> Unit) {
    val tk = t()
    val st by Store.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    val picks = remember { mutableStateMapOf<Int, FoodRow>() }
    val online = remember { mutableStateMapOf<String, List<com.mattlabs.planlab.core.Food>>() }
    var searching by remember { mutableStateOf(false) }
    var canWrite by remember { mutableStateOf(true) }
    val connect = rememberHealthConnect()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus) runCatching { focus.requestFocus() }; canWrite = FoodWriter.allowed(ctx) }

    val base = remember(text, st.foods, st.mine) { Store.lookup(text) }
    val rows = base.mapIndexed { i, r ->
        picks[i] ?: online[r.text]?.firstOrNull()?.takeIf { r.food == null }?.let { f -> r.copy(alts = online[r.text]!!).with(f, r.qty) } ?: r
    }
    // Anything the tables don't know: ask Open Food Facts once typing pauses.
    LaunchedEffect(text) {
        picks.clear()
        delay(900)
        val missing = base.filter { it.food == null && it.text !in online }
        if (missing.isEmpty()) return@LaunchedEffect
        searching = true
        for (r in missing) online[r.text] = OpenFood.search(r.text)
        searching = false
    }

    fun log() {
        val ok = rows.filter { it.food != null || it.text.isNotBlank() }
        if (ok.isEmpty()) return
        val m = ok.fold(Macros()) { a, r -> a + Macros(((r.food?.kcal ?: 0.0) * r.scale).roundToInt(), ((r.food?.p ?: 0.0) * r.scale).roundToInt(), ((r.food?.c ?: 0.0) * r.scale).roundToInt(), ((r.food?.f ?: 0.0) * r.scale).roundToInt()) }
        Store.addFood(date, ok)
        text = ""; picks.clear()
        onLogged(m)
    }

    NibbleRow(date) { onLogged(it) }
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().focusRequester(focus),
        placeholder = { Text("skyline 3-way, 2 coneys") }, label = { Text("What did you eat?") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { log() }), maxLines = 3,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = tk.eat, unfocusedBorderColor = tk.line, focusedLabelColor = tk.eat), shape = RoundedCornerShape(14.dp))
    if (rows.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        rows.forEachIndexed { i, r ->
            val f = r.food
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(enabled = r.alts.size > 1) {
                    // Tap the name to try the next match.
                    val j = (r.alts.indexOf(f) + 1) % r.alts.size
                    picks[i] = r.with(r.alts[j], r.qty)
                }) {
                    Text(f?.title ?: r.text, fontWeight = FontWeight.SemiBold, color = if (f == null) tk.warn else tk.fg, maxLines = 2)
                    Text(if (f == null) (if (searching) "Looking it up…" else "Not found. Logged as a guess; Claude fills it in tomorrow.")
                        else "${n((f.kcal * r.scale).roundToInt())} kcal · ${(f.p * r.scale).roundToInt()} g protein · ${fmtQty(r.scale)} × ${f.serving}" + if (r.alts.size > 1) " · tap for others" else "",
                        fontSize = 12.sp, color = tk.muted, lineHeight = 15.sp)
                }
                if (f != null) {
                    Stepper("−") { val s = r.scale; val k = if (s > 1) s - 1 else s / 2; if (k >= 0.25) picks[i] = r.with(f, k) }
                    Text(fmtQty(r.scale), Modifier.width(34.dp), color = tk.fg, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Stepper("+") { val s = r.scale; picks[i] = r.with(f, if (s < 1) s * 2 else s + 1) }
                }
            }
        }
        val m = rows.fold(Macros()) { a, r -> a + Macros(((r.food?.kcal ?: 0.0) * r.scale).roundToInt(), ((r.food?.p ?: 0.0) * r.scale).roundToInt()) }
        Spacer(Modifier.height(6.dp))
        PButton("Log it · ${n(m.kcal)} kcal · ${m.protein} g protein", Modifier.fillMaxWidth(), tk.eat) { log() }
    }
    if (!canWrite) {
        Text("Also send food to Health Connect", Modifier.padding(top = 10.dp).clickable { connect(); scope.launch { delay(4000); canWrite = FoodWriter.allowed(ctx) } },
            color = tk.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Stepper(label: String, onClick: () -> Unit) {
    val tk = t()
    Box(Modifier.size(32.dp).clip(CircleShape).background(tk.sunk).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(label, color = tk.fg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

fun openFood(ctx: Context, from: String) =
    ctx.startActivity(Intent(ctx, FoodActivity::class.java).putExtra("from", from).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

/** "Track food" from the widget, a notification or the launcher: a sheet over whatever is on screen. */
class FoodActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        Store.count("screen:food")
        intent.getStringExtra("from")?.let { Store.count("open_from:$it") }
        setContent { PlanTheme { Sheet() } }
    }

    private fun done(m: Macros) {
        val today = LocalDate.now()
        val have = Store.eaten(today); val want = Store.foodTargets(today)
        Toast.makeText(this, "Logged ${n(m.kcal)} kcal. Today: ${n(have.kcal)} of ${n(want.kcal)} kcal, ${have.protein} of ${want.protein} g protein.", Toast.LENGTH_LONG).show()
        finish()
    }

    @Composable
    private fun Sheet() {
        val tk = t()
        val st by Store.state.collectAsState()
        val today = LocalDate.now()
        val day = st.plan.day(today)
        val next = remember(st.version) { Store.nextPlanned(today) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(remember { MutableInteractionSource() }, null) { finish() }, contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(tk.surface)
                .clickable(remember { MutableInteractionSource() }, null) {}.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text("Track food", style = MaterialTheme.typography.headlineSmall, color = tk.fg)
                Spacer(Modifier.height(12.dp))
                EatTotals(today)
                if (day != null && next != null) {
                    val p = remember(next.key) { Store.planned(next) }
                    Spacer(Modifier.height(14.dp))
                    PButton("Ate the plan: ${cap(p.meal)}, ${p.name}", Modifier.fillMaxWidth(), tk.eatSoft(), tk.fg) {
                        Store.logPlanned(day, next); done(p.m)
                    }
                }
                Spacer(Modifier.height(14.dp))
                FoodLogger(today, autoFocus = next == null) { done(it) }
                Spacer(Modifier.height(14.dp))
                Text("🥫  Ran out of something?", Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                    Store.count("food:kitchen"); runCatching { startActivity(Links.intent(this@FoodActivity, "app:kitchen", "food")) }; finish()
                }.padding(vertical = 8.dp), color = tk.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun Tokens.eatSoft(): Color = eat.copy(alpha = if (dark) 0.28f else 0.16f)
