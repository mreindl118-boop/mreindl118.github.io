package com.mattlabs.planlab.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattlabs.planlab.core.LogDoc
import com.mattlabs.planlab.core.Md
import com.mattlabs.planlab.data.Store
import java.time.LocalDate

/**
 * Two small sheets for mindful steps:
 * "app:stretch" asks where you are (from mind/anywhere.md) and shows that place's 1-minute list;
 * "app:journal" is a one-line text or voice answer, saved to the private log (journal[date]).
 */
object MindSheet {
    data class Req(val kind: String, val date: LocalDate, val boxKey: String?, val stepKey: String?, val label: String)
    var req by mutableStateOf<Req?>(null)

    fun show(r: Req) { Store.count("sheet_${r.kind}"); req = r }

    /** Opens [kind] for the step with this label today (so Done ticks it), or on its own. */
    fun forLabel(kind: String, label: String, boxKey: String? = null, stepKey: String? = null) {
        val today = LocalDate.now()
        var box = boxKey; var step = stepKey; var text = label
        val day = Store.state.value.plan.day(today)
        if (day != null) {
            val hit = day.boxes.firstNotNullOfOrNull { b ->
                b.steps.firstOrNull { s -> (stepKey != null && s.key == stepKey) || (label.isNotEmpty() && s.label == label) }?.let { b to it }
            }
            if (hit != null) { box = hit.first.key; step = hit.second.key; text = hit.second.label }
        }
        show(Req(kind, today, box, step, text))
    }

    /** "## Desk" sections with their "- " bullets; the "Journal prompts" section is left out. */
    fun places(md: String?): List<Pair<String, List<String>>> =
        Md.sections(md ?: "").filter { !it.first.lowercase().startsWith("journal") }
            .map { (h, body) -> Md.strip(h) to bullets(body) }.filter { it.second.isNotEmpty() }

    fun prompts(md: String?): List<String> =
        Md.sections(md ?: "").firstOrNull { it.first.lowercase().startsWith("journal") }?.let { bullets(it.second) } ?: emptyList()

    private fun bullets(body: String): List<String> =
        body.lines().map { it.trim() }.filter { it.startsWith("- ") }.map { Md.strip(it.removePrefix("- ").trim()) }.filter { it.isNotBlank() }

    /** "Hold 30 s" -> 30. */
    fun seconds(text: String): Int = Regex("""(\d+)\s*s\b""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /** "Check-in 3 at 20:30, 1 min: Journal: What went well?" -> "What went well?". */
    fun question(label: String): String? =
        MoveSheet.shortLabel(label).let { s -> if (s.lowercase().startsWith("journal")) s.substringAfter(":", "").trim().ifEmpty { null } else null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindSheetHost() {
    val r = MindSheet.req ?: return
    val tk = t()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { MindSheet.req = null }, sheetState = sheet, containerColor = tk.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (r.kind == "journal") Journal(r) else Stretch(r)
        }
    }
}

private fun finish(r: MindSheet.Req) {
    val day = Store.state.value.plan.day(r.date)
    val box = day?.boxes?.firstOrNull { it.key == r.boxKey }
    val step = box?.steps?.firstOrNull { it.key == r.stepKey }
    if (day != null && box != null && step != null && !Store.stepDone(day, step)) Store.setStep(day, box, step, true)
    MindSheet.req = null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Stretch(r: MindSheet.Req) {
    val tk = t()
    val st by Store.state.collectAsState()
    val places = remember(st.plan) { MindSheet.places(Store.file("mind/anywhere.md")) }
    var place by remember { mutableStateOf<String?>(null) }
    val color = tk.body
    if (place == null) {
        Text("Where are you?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = tk.fg)
        if (places.isEmpty()) Text("Not loaded yet", color = tk.muted, fontSize = 16.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((name, _) in places) {
                Text(name, Modifier.clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = if (tk.dark) 0.22f else 0.12f)).clickable { Store.count("stretch:${Md.keyOf(name)}"); place = name }
                    .padding(horizontal = 20.dp, vertical = 16.dp), color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        return
    }
    val items = places.firstOrNull { it.first == place }?.second ?: emptyList()
    val ticked = remember(place) { mutableStateListOf<Int>() }
    Text(place ?: "", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = tk.fg)
    items.forEachIndexed { i, text ->
        val on = i in ticked
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CheckDot(on, color, 30.dp) { if (on) ticked.remove(i) else ticked.add(i) }
            Text(text, Modifier.weight(1f), fontSize = 17.sp, color = if (on) tk.muted else tk.fg, textDecoration = if (on) TextDecoration.LineThrough else null)
            val s = MindSheet.seconds(text)
            if (s > 0 && !on) StepTimer(0, color, seconds = s) { if (i !in ticked) ticked.add(i) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PButton("Back", Modifier, tk.sunk, tk.fg) { place = null }
        PButton("Done", Modifier.weight(1f), color) { finish(r) }
    }
}

@Composable
private fun Journal(r: MindSheet.Req) {
    val tk = t()
    val st by Store.state.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val q = remember(r) { MindSheet.question(r.label) ?: MindSheet.prompts(Store.file("mind/anywhere.md")).randomOrNull() ?: "One good thing today?" }
    var text by remember(r) { mutableStateOf(LogDoc.journal(st.log, r.date.toString()) ?: "") }
    val speak = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { text = (text + " " + it).trim() }
    }
    val color = tk.piano
    Text(q, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = tk.fg)
    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = false, maxLines = 4,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color, unfocusedBorderColor = tk.line), shape = RoundedCornerShape(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(com.mattlabs.planlab.R.drawable.ic_a_voice, color, color.copy(alpha = if (tk.dark) 0.22f else 0.12f), "Speak") {
            Store.count("journal_voice")
            runCatching {
                speak.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT, q))
            }.onFailure { android.widget.Toast.makeText(ctx, "No voice typing on this phone", android.widget.Toast.LENGTH_SHORT).show() }
        }
        PButton("Save", Modifier.weight(1f), if (text.isBlank()) tk.sunk else color, if (text.isBlank()) tk.muted else Color.White) {
            if (text.isNotBlank()) { Store.journal(r.date, q, text.trim()); finish(r) }
        }
    }
}
