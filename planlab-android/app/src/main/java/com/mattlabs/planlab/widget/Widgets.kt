package com.mattlabs.planlab.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mattlabs.planlab.core.LogDoc
import com.mattlabs.planlab.data.Store
import com.mattlabs.planlab.ui.MainActivity
import com.mattlabs.planlab.ui.BugActivity
import com.mattlabs.planlab.ui.FoodActivity
import java.time.LocalDate

object Widgets {
    suspend fun updateAll(ctx: Context) {
        TodayWidget().updateAll(ctx)
        ReadyWidget().updateAll(ctx)
    }
}

private object WC {
    val bg = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF121A24))
    val sunk = ColorProvider(day = Color(0xFFEEF2F7), night = Color(0xFF1C2632))
    val fg = ColorProvider(day = Color(0xFF0E1620), night = Color(0xFFE8EDF3))
    val muted = ColorProvider(day = Color(0xFF5B6979), night = Color(0xFF93A2B2))
    val accent = ColorProvider(day = Color(0xFF2F7DE1), night = Color(0xFF69B4F4))
    val ok = ColorProvider(day = Color(0xFF17875A), night = Color(0xFF4CC38A))
    val warm = ColorProvider(day = Color(0xFFB4690E), night = Color(0xFFE0B25A))
    val white = ColorProvider(day = Color.White, night = Color(0xFF06121D))
    fun kind(k: String) = when (k) {
        "meals" -> ColorProvider(day = Color(0xFFE08A1E), night = Color(0xFFF2A94A))
        "body", "move", "mind" -> ColorProvider(day = Color(0xFF12A383), night = Color(0xFF3FD1AE))
        "piano" -> ColorProvider(day = Color(0xFF6E56CF), night = Color(0xFF9E8CFC))
        "voice" -> ColorProvider(day = Color(0xFFE5484D), night = Color(0xFFFF7A7F))
        "clean", "home", "bugs" -> ColorProvider(day = Color(0xFF0E8FB0), night = Color(0xFF4FC7E3))
        else -> accent
    }
    fun score(s: Int) = when { s >= 80 -> ok; s >= 65 -> accent; s >= 50 -> warm; else -> ColorProvider(day = Color(0xFFC2410C), night = Color(0xFFFF8A4C)) }
}

private val FROM = ActionParameters.Key<String>("from")
private val CAMERA = ActionParameters.Key<Boolean>("camera")

class ToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store.init(context)
        val key = parameters[KEY] ?: return
        Store.count("widget:tick")
        val today = LocalDate.now()
        if (key == "pf") Store.pelvicSet(today) else {
            val day = Store.state.value.plan.day(today) ?: return
            val box = day.boxes.firstOrNull { it.key == key } ?: return
            // An area with steps ticks its next step; one without ticks (or unticks) the area.
            if (box.steps.isNotEmpty() && !Store.isDone(day, box)) Store.doNextStep(today, key, parameters[STEP]?.takeIf { it.isNotEmpty() }) else Store.toggle(day, box)
        }
        Widgets.updateAll(context)
    }
    companion object { val KEY = ActionParameters.Key<String>("key"); val STEP = ActionParameters.Key<String>("step") }
}

/** One tap: the next planned meal is eaten (logged with the plan's numbers). */
class AtePlanAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store.init(context)
        Store.count("widget:food")
        val today = LocalDate.now()
        val day = Store.state.value.plan.day(today) ?: return
        val step = Store.nextPlanned(today) ?: return
        Store.logPlanned(day, step)
        Widgets.updateAll(context)
    }
}

/** One tap: a nibble ("a bite", about 80 kcal). The ones that don't feel like they count. */
class NibbleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store.init(context)
        Store.count("widget:nibble")
        Store.addNibble(LocalDate.now())
        Widgets.updateAll(context)
    }
}

class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        provideContent { TodayContent() }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TodayWidget() }

/** An area icon, tinted. */
@Composable
private fun Ico(key: String, color: androidx.glance.unit.ColorProvider, size: androidx.compose.ui.unit.Dp) =
    Image(ImageProvider(com.mattlabs.planlab.ui.areaIcon(key)), null, GlanceModifier.size(size), colorFilter = ColorFilter.tint(color))

@Composable
private fun Ico(res: Int, color: androidx.glance.unit.ColorProvider, size: androidx.compose.ui.unit.Dp) =
    Image(ImageProvider(res), null, GlanceModifier.size(size), colorFilter = ColorFilter.tint(color))

/** Mostly pictures: the one next thing with a big check, then an icon per area, then food and bugs buttons. */
@Composable
private fun TodayContent() {
    val today = LocalDate.now()
    val st = Store.state.value
    val day = st.plan.day(today)
    val b = Store.brief(today)
    Column(GlanceModifier.fillMaxSize().background(WC.bg).cornerRadius(24.dp).padding(14.dp)) {
        Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>(actionParametersOf(FROM to "widget"))), verticalAlignment = Alignment.CenterVertically) {
            val (d, n) = day?.let { com.mattlabs.planlab.ui.TodayData.count(it) } ?: (0 to 0)
            Text(if (day == null) "planLAB" else "$d/$n", GlanceModifier.defaultWeight(), style = TextStyle(color = WC.fg, fontSize = 20.sp, fontWeight = FontWeight.Bold))
            st.progress?.streak?.takeIf { it > 0 }?.let { Text("🔥$it  ", style = TextStyle(color = WC.warm, fontSize = 15.sp, fontWeight = FontWeight.Bold)) }
            b.readiness?.let { r ->
                Box(GlanceModifier.size(40.dp).background(WC.score(r.score)).cornerRadius(20.dp), contentAlignment = Alignment.Center) {
                    Text("${r.score}", style = TextStyle(color = WC.white, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        if (day == null) {
            Text(if (st.hasKey) "Nothing planned" else "Open to set up", style = TextStyle(color = WC.muted, fontSize = 15.sp))
        } else {
            val next = com.mattlabs.planlab.ui.TodayData.next(day)
            if (next == null) {
                Text("✓ All done", style = TextStyle(color = WC.ok, fontSize = 20.sp, fontWeight = FontWeight.Bold))
            } else {
                val c = WC.kind(next.box.key)
                Row(GlanceModifier.fillMaxWidth().background(WC.sunk).cornerRadius(20.dp).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ico(next.box.key, c, 34.dp)
                    Spacer(GlanceModifier.width(10.dp))
                    Text(next.step?.let { com.mattlabs.planlab.ui.TodayData.short(it) } ?: com.mattlabs.planlab.ui.TodayData.label(next.box),
                        GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>(actionParametersOf(FROM to "widget"))),
                        style = TextStyle(color = WC.fg, fontSize = 17.sp, fontWeight = FontWeight.Bold), maxLines = 2)
                    Box(GlanceModifier.size(48.dp).background(c).cornerRadius(24.dp)
                        .clickable(actionRunCallback<ToggleAction>(actionParametersOf(ToggleAction.KEY to next.box.key, ToggleAction.STEP to (next.step?.key ?: "")))),
                        contentAlignment = Alignment.Center) {
                        Text("✓", style = TextStyle(color = WC.white, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
            Spacer(GlanceModifier.height(10.dp))
            // One icon per area: filled when done. Tap ticks its next step.
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val boxes = com.mattlabs.planlab.core.TodayPick.order(day.boxes).take(6)
                boxes.forEachIndexed { i, box ->
                    val on = Store.isDone(day, box)
                    val c = WC.kind(box.key)
                    Box(GlanceModifier.size(40.dp).background(if (on) c else WC.sunk).cornerRadius(20.dp)
                        .clickable(actionRunCallback<ToggleAction>(actionParametersOf(ToggleAction.KEY to box.key))), contentAlignment = Alignment.Center) {
                        Ico(box.key, if (on) WC.white else c, 22.dp)
                    }
                    if (i < boxes.size - 1) Spacer(GlanceModifier.width(8.dp))
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        val have = Store.eaten(today); val want = Store.foodTargets(today)
        LinearProgressIndicator(progress = if (want.protein > 0) (have.protein.toFloat() / want.protein).coerceIn(0f, 1f) else 0f,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp), color = WC.kind("meals"), backgroundColor = WC.sunk)
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            Box(GlanceModifier.defaultWeight().height(40.dp).background(WC.kind("meals")).cornerRadius(20.dp)
                .clickable(actionStartActivity<FoodActivity>(actionParametersOf(FROM to "widget"))), contentAlignment = Alignment.Center) {
                Ico("eat", WC.white, 22.dp)
            }
            Spacer(GlanceModifier.width(8.dp))
            // Nibbles: one tap each, the count shows so the bites can't hide.
            val nib = Store.nibbles(today).first
            Box(GlanceModifier.width(64.dp).height(40.dp).background(WC.sunk).cornerRadius(20.dp)
                .clickable(actionRunCallback<NibbleAction>()), contentAlignment = Alignment.Center) {
                Text(if (nib > 0) "🤏 $nib" else "🤏", style = TextStyle(color = WC.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(GlanceModifier.width(8.dp))
            Box(GlanceModifier.defaultWeight().height(40.dp).background(WC.kind("clean")).cornerRadius(20.dp)
                .clickable(actionStartActivity<BugActivity>(actionParametersOf(FROM to "widget"))), contentAlignment = Alignment.Center) {
                Ico("bugs", WC.white, 24.dp)
            }
            Spacer(GlanceModifier.width(8.dp))
            Box(GlanceModifier.width(48.dp).height(40.dp).background(WC.sunk).cornerRadius(20.dp)
                .clickable(actionStartActivity<BugActivity>(actionParametersOf(FROM to "widget", CAMERA to true))), contentAlignment = Alignment.Center) {
                Text("📷", style = TextStyle(color = WC.fg, fontSize = 16.sp))
            }
        }
    }
}

class ReadyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        provideContent { ReadyContent() }
    }
}

class ReadyWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ReadyWidget() }

@Composable
private fun ReadyContent() {
    val today = LocalDate.now()
    val b = Store.brief(today)
    val r = b.readiness
    Column(GlanceModifier.fillMaxSize().background(WC.bg).cornerRadius(24.dp).padding(14.dp).clickable(actionStartActivity<MainActivity>(actionParametersOf(FROM to "widget")))) {
        Text(r?.let { "${it.score}" } ?: "–", style = TextStyle(color = r?.let { WC.score(it.score) } ?: WC.muted, fontSize = 40.sp, fontWeight = FontWeight.Bold))
        Text(if (r == null) "Connect health" else "ready", style = TextStyle(color = WC.fg, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        if (b.daysToBig != null && b.daysToBig > 0) Text("${b.daysToBig} days", style = TextStyle(color = WC.warm, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}
