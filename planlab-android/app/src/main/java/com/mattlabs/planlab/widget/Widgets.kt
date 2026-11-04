package com.mattlabs.planlab.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
        "body" -> ColorProvider(day = Color(0xFF12A383), night = Color(0xFF3FD1AE))
        "piano" -> ColorProvider(day = Color(0xFF6E56CF), night = Color(0xFF9E8CFC))
        "voice" -> ColorProvider(day = Color(0xFFE5484D), night = Color(0xFFFF7A7F))
        else -> accent
    }
    fun score(s: Int) = when { s >= 80 -> ok; s >= 65 -> accent; s >= 50 -> warm; else -> ColorProvider(day = Color(0xFFC2410C), night = Color(0xFFFF8A4C)) }
}

class ToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store.init(context)
        val key = parameters[KEY] ?: return
        val today = LocalDate.now()
        if (key == "pf") Store.pelvicSet(today) else {
            val day = Store.state.value.plan.day(today) ?: return
            val box = day.boxes.firstOrNull { it.key == key } ?: return
            Store.toggle(day, box)
        }
        Widgets.updateAll(context)
    }
    companion object { val KEY = ActionParameters.Key<String>("key") }
}

class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        provideContent { TodayContent() }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TodayWidget() }

@Composable
private fun TodayContent() {
    val today = LocalDate.now()
    val st = Store.state.value
    val day = st.plan.day(today)
    val b = Store.brief(today)
    Column(GlanceModifier.fillMaxSize().background(WC.bg).cornerRadius(24.dp).padding(14.dp)) {
        Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                val done = day?.boxes?.count { Store.isDone(day, it) } ?: 0
                Text(if (day == null) "planLAB" else if (done == day.boxes.size) "All done" else "Today  $done/${day.boxes.size}",
                    style = TextStyle(color = WC.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                val sub = buildList {
                    if (b.daysToBig != null && b.daysToBig > 0) add("${b.daysToBig}d to ${b.bigName}")
                    if (b.daysToBig != null && b.daysToBig <= 0 && b.daysToBig > -120) add("Day ${-b.daysToBig} of recovery")
                    add(b.body.title)
                }.joinToString(" · ")
                Text(sub, style = TextStyle(color = WC.muted, fontSize = 12.sp), maxLines = 1)
            }
            b.readiness?.let { r ->
                Box(GlanceModifier.size(44.dp).background(WC.score(r.score)).cornerRadius(22.dp), contentAlignment = Alignment.Center) {
                    Text("${r.score}", style = TextStyle(color = WC.white, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        if (day == null) {
            Text(if (st.hasKey) "Nothing planned today." else "Open planLAB to set it up.", style = TextStyle(color = WC.muted, fontSize = 14.sp))
        } else {
            val log = st.log
            for (box in day.boxes.take(6)) {
                val on = Store.isDone(day, box)
                val skipped = LogDoc.skipped(log, today.toString(), box.key)
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(actionRunCallback<ToggleAction>(actionParametersOf(ToggleAction.KEY to box.key))),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.size(26.dp).background(if (on) WC.kind(box.key) else WC.sunk).cornerRadius(13.dp), contentAlignment = Alignment.Center) {
                        Text(if (on) "✓" else "", style = TextStyle(color = WC.white, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    }
                    Spacer(GlanceModifier.width(10.dp))
                    Text((Store.LABEL[box.key] ?: box.label.replaceFirstChar { it.uppercase() }) + if (skipped && !on) "  (skipped)" else "",
                        style = TextStyle(color = if (on) WC.muted else WC.fg, fontSize = 15.sp, fontWeight = if (on) FontWeight.Normal else FontWeight.Medium), maxLines = 1)
                }
            }
            val pf = LogDoc.pelvic(log, today.toString())
            Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(actionRunCallback<ToggleAction>(actionParametersOf(ToggleAction.KEY to "pf"))),
                verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(26.dp).background(if (pf >= 2) WC.ok else WC.sunk).cornerRadius(13.dp), contentAlignment = Alignment.Center) {
                    Text("$pf", style = TextStyle(color = if (pf >= 2) WC.white else WC.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }
                Spacer(GlanceModifier.width(10.dp))
                Text("Pelvic floor  $pf/2  · tap after a set", style = TextStyle(color = WC.fg, fontSize = 15.sp), maxLines = 1)
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
    Column(GlanceModifier.fillMaxSize().background(WC.bg).cornerRadius(24.dp).padding(14.dp).clickable(actionStartActivity<MainActivity>())) {
        Text(r?.let { "${it.score}" } ?: "–", style = TextStyle(color = r?.let { WC.score(it.score) } ?: WC.muted, fontSize = 40.sp, fontWeight = FontWeight.Bold))
        Text(r?.label ?: "Connect health", style = TextStyle(color = WC.fg, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Spacer(GlanceModifier.height(6.dp))
        Text(b.body.title, style = TextStyle(color = WC.muted, fontSize = 12.sp), maxLines = 2)
        if (b.daysToBig != null && b.daysToBig > 0) Text("${b.daysToBig} days to ${b.bigName}", style = TextStyle(color = WC.warm, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}
