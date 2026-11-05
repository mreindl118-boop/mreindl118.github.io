package com.mattlabs.planlab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattlabs.planlab.core.Md
import com.mattlabs.planlab.core.Moves
import com.mattlabs.planlab.data.Store
import java.time.LocalDate

/** The instruction card for an exercise or break: what to do, cues, dose, safety, a demo, a timer and Done. */
object MoveSheet {
    data class Req(val date: LocalDate, val boxKey: String?, val stepKey: String?, val moveKey: String?)
    var req by mutableStateOf<Req?>(null)
    fun show(r: Req) { Store.count("move_card"); req = r }

    /** "Break 1 at 10:30, 1 min: stand up, 10 squats" -> "Stand up, 10 squats". */
    fun shortLabel(label: String): String =
        if ((label.lowercase().startsWith("break") || label.lowercase().startsWith("check-in")) && label.contains(": ")) label.substringAfter(": ").trim().replaceFirstChar { it.uppercase() } else label
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveSheetHost() {
    val r = MoveSheet.req ?: return
    val st by Store.state.collectAsState()
    val tk = t()
    val ctx = LocalContext.current
    val day = st.plan.day(r.date)
    val box = r.boxKey?.let { k -> day?.boxes?.firstOrNull { it.key == k } }
    val step = r.stepKey?.let { k -> box?.steps?.firstOrNull { it.key == k } }
    val label = step?.label ?: ""
    val move = r.moveKey?.let { st.plan.moves[it] } ?: Moves.find(st.plan.moves, label, step?.links ?: emptyList())
    val kind = if (box != null) Moves.kindOf(box, step, day) else null
    val color = tk.kind(r.boxKey ?: "body")
    val done = day != null && step != null && Store.stepDone(day, step)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { MoveSheet.req = null }, sheetState = sheet, containerColor = tk.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kind?.let { Pill(it.badge, color, color.copy(alpha = if (tk.dark) 0.22f else 0.12f)) }
                if (done) Pill("Done", tk.ok, tk.okSoft)
            }
            Text(move?.name ?: MoveSheet.shortLabel(label), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = tk.fg)
            val dose = move?.field("Dose")
            if (dose != null) Text(dose, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            move?.field("Do")?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = tk.fg, lineHeight = 24.sp) }
            val cues = move?.cues ?: emptyList()
            if (cues.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cues.forEachIndexed { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${i + 1}", Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)).padding(top = 4.dp), color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text(c.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge, color = tk.fg)
                    }
                }
            }
            move?.field("Safe")?.let {
                Text("⚠ $it", Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(tk.warnSoft).padding(12.dp), color = tk.warn, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            if (move == null && label.isNotBlank()) Text(MoveSheet.shortLabel(label), style = MaterialTheme.typography.bodyLarge, color = tk.fg)
            val links = (move?.links ?: emptyList()) + (step?.links ?: emptyList()).filter { !it.url.startsWith("app:move/") }
            if (links.isNotEmpty()) LinkChips(links.distinctBy { it.url }, "", color)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val mins = Md.minutes(label)
                if (mins > 0 && !done && day != null && box != null && step != null) StepTimer(mins, color) { Store.setStep(day, box, step, true) }
                if (day != null && box != null && step != null) {
                    PButton(if (done) "Undo" else "Done", Modifier.weight(1f), if (done) tk.sunk else color, if (done) tk.fg else Color.White) {
                        Store.setStep(day, box, step, !done); if (!done) MoveSheet.req = null
                    }
                } else PButton("Close", Modifier.weight(1f), tk.sunk, tk.fg) { MoveSheet.req = null }
            }
        }
    }
}
