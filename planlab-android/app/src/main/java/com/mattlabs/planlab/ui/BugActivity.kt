package com.mattlabs.planlab.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mattlabs.planlab.data.Store
import java.io.File

/**
 * "This bugs me": a mess or annoyance Matt keeps walking past. A few words and/or a photo, one tap to save.
 * Claude turns each into small steps (and a habit if it keeps coming back) in the next morning run.
 */
class BugActivity : ComponentActivity() {
    private var photo by mutableStateOf<File?>(null)
    private var pending: File? = null
    private val snap = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) photo = pending else pending?.delete()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        Store.count("screen:bug")
        intent.getStringExtra("from")?.let { Store.count("open_from:$it") }
        val camera = intent.getBooleanExtra("camera", false)
        setContent { PlanTheme { Sheet() } }
        if (camera && savedInstanceState == null) takePhoto()
    }

    private fun takePhoto() {
        val dir = File(cacheDir, "bugs").apply { mkdirs() }
        val f = File(dir, "bug-${System.currentTimeMillis()}.jpg")
        pending = f
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.files", f)
        runCatching { snap.launch(uri) }.onFailure { Toast.makeText(this, "No camera app found.", Toast.LENGTH_SHORT).show() }
    }

    private fun save(text: String) {
        if (text.isBlank() && photo == null) return
        Store.addBug(text, photo)
        Toast.makeText(this, "Got it. Claude turns it into steps tomorrow morning.", Toast.LENGTH_LONG).show()
        finish()
    }

    @Composable
    private fun Sheet() {
        val tk = t()
        var text by remember { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(remember { MutableInteractionSource() }, null) { finish() }, contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(tk.surface)
                .clickable(remember { MutableInteractionSource() }, null) {}.navigationBarsPadding().imePadding().padding(20.dp)) {
                Text("This bugs me", style = MaterialTheme.typography.headlineSmall, color = tk.fg)
                Text("A mess, a chore, anything you keep walking past.", color = tk.muted, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().focusRequester(focus), placeholder = { Text("Shoes piled by the door") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save(text) }), maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = tk.home, unfocusedBorderColor = tk.line), shape = RoundedCornerShape(14.dp))
                photo?.let { f ->
                    val bmp = remember(f) { runCatching { BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 8 }) }.getOrNull() }
                    if (bmp != null) {
                        Spacer(Modifier.height(12.dp))
                        Image(bmp.asImageBitmap(), "Your photo", Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PButton(if (photo == null) "📷 Photo" else "Retake", Modifier.weight(1f), tk.sunk, tk.fg) { takePhoto() }
                    PButton("Save  +${Store.state.value.rewards.bugLogged} XP", Modifier.weight(1.4f), tk.home) { save(text) }
                }
            }
        }
    }
}
