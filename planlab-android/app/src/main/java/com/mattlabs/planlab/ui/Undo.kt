package com.mattlabs.planlab.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** After any tap that changes the day: a bar saying what happened, with Undo, for 6 seconds. */
object Undo {
    var text by mutableStateOf<String?>(null)
    var at by mutableLongStateOf(0L)
    var undo by mutableStateOf<(() -> Unit)?>(null)

    /** [revert] null shows the message alone. */
    fun offer(what: String, revert: (() -> Unit)?) { text = what; undo = revert; at = System.currentTimeMillis() }
    fun run() { undo?.invoke(); com.mattlabs.planlab.data.Store.count("undo"); clear() }
    fun clear() { text = null; undo = null }
}

@Composable
fun BoxScope.UndoBar() {
    val tk = t()
    val msg = Undo.text
    LaunchedEffect(Undo.at) { if (Undo.at > 0) { delay(6000); Undo.clear() } }
    AnimatedVisibility(msg != null, Modifier.align(Alignment.BottomCenter).padding(16.dp), enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tk.fg).padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(msg ?: "", Modifier.weight(1f), color = tk.bg, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            if (Undo.undo != null) Text("Undo", Modifier.clip(CircleShape).background(tk.accent).clickable { Undo.run() }.padding(horizontal = 16.dp, vertical = 10.dp),
                color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}
