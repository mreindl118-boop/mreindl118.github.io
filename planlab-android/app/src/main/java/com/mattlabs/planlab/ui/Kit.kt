package com.mattlabs.planlab.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Card = RoundedCornerShape(22.dp)

@Composable
fun t(): Tokens = LocalTokens.current

/** A card that dips slightly when pressed. */
@Composable
fun PCard(modifier: Modifier = Modifier, color: Color = t().surface, onClick: (() -> Unit)? = null, pad: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.98f else 1f, spring(stiffness = 600f), label = "press")
    Column(
        modifier.fillMaxWidth().scale(s).clip(Card).background(color)
            .then(if (onClick != null) Modifier.clickable(src, null) { onClick() } else Modifier)
            .padding(pad),
        content = content,
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall, color = t().muted)

@Composable
fun Pill(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) =
    Text(text, modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp), color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)

/** A progress ring. [f] is 0..1. */
@Composable
fun Ring(f: Float, color: Color, size: Dp, stroke: Dp = 8.dp, track: Color = t().sunk, content: @Composable () -> Unit = {}) {
    val a by animateFloatAsState(f.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx(); val d = this.size.minDimension - w
            val tl = Offset(w / 2, w / 2)
            drawArc(track, -90f, 360f, false, tl, Size(d, d), style = Stroke(w))
            if (a > 0f) drawArc(color, -90f, 360f * a, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** The round check button. Done turns it the task's color, with a buzz. */
@Composable
fun CheckDot(on: Boolean, color: Color, size: Dp = 34.dp, onToggle: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (on) color else Color.Transparent, tween(220), label = "dot")
    val s by animateFloatAsState(if (on) 1f else 0.92f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "dotScale")
    Box(
        Modifier.size(size).scale(s).clip(CircleShape).background(bg)
            .then(if (!on) Modifier.background(t().sunk) else Modifier)
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onToggle() },
        contentAlignment = Alignment.Center,
    ) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.6f)) }
}

/** A 14-day line. Nulls are gaps. */
@Composable
fun Spark(values: List<Double?>, color: Color, modifier: Modifier = Modifier) {
    val pts = values.mapIndexedNotNull { i, v -> v?.let { i to it } }
    Canvas(modifier) {
        if (pts.size < 2) return@Canvas
        val lo = pts.minOf { it.second }; val hi = pts.maxOf { it.second }
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        fun y(v: Double) = (size.height - 4f) - ((v - lo) / span * (size.height - 8f)).toFloat()
        val p = Path()
        pts.forEachIndexed { i, (x, v) -> if (i == 0) p.moveTo(x * stepX, y(v)) else p.lineTo(x * stepX, y(v)) }
        drawPath(p, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        val (lx, lv) = pts.last()
        drawCircle(color, 3.5.dp.toPx(), Offset(lx * stepX, y(lv)))
    }
}

@Composable
fun RowItem(modifier: Modifier = Modifier, lead: @Composable () -> Unit = {}, title: String, sub: String? = null, titleColor: Color = t().fg, tail: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        lead()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = titleColor)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = t().muted)
        }
        tail()
    }
}

@Composable
fun Dot(color: Color) = Box(Modifier.size(10.dp).clip(CircleShape).background(color))

@Composable
fun PButton(text: String, modifier: Modifier = Modifier, color: Color = t().accent, fg: Color = Color.White, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(color).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }.padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}
