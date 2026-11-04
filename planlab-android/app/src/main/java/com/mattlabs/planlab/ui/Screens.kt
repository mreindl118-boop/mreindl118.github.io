package com.mattlabs.planlab.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mattlabs.planlab.BuildConfig
import com.mattlabs.planlab.data.GitHub
import com.mattlabs.planlab.data.Store
import com.mattlabs.planlab.notify.Alarms
import com.mattlabs.planlab.notify.Notifier
import com.mattlabs.planlab.notify.Slot
import org.json.JSONObject

fun open(ctx: Context, url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

/** The full web planner (workout player, cook mode, groceries, recipes), signed in with the app's key. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlannerScreen(onBack: () -> Unit, go: String? = null, goN: Int = 0) {
    val key = Store.token() ?: ""
    var web by remember { mutableStateOf<WebView?>(null) }
    BackHandler { val w = web; if (w != null && w.canGoBack()) w.goBack() else onBack() }
    AndroidView(modifier = Modifier.fillMaxSize().statusBarsPadding(), factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val u = request.url.toString()
                    if (u.startsWith(GitHub.WEB)) return false
                    open(ctx, u); return true
                }
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript("(function(){try{if(localStorage.getItem('planlab-key')!==${JSONObject.quote(key)}){localStorage.setItem('planlab-key',${JSONObject.quote(key)});location.reload();}}catch(e){}})()", null)
                }
            }
            loadUrl(GitHub.WEB + (go?.let { "#go/$it" } ?: ""))
            web = this
        }
    })
    // A step's link opened the planner at a view: jump there without reloading.
    val firstN = remember { goN }
    androidx.compose.runtime.LaunchedEffect(goN) {
        val w = web
        if (goN != firstN && go != null && w != null) w.evaluateJavascript("location.hash=${JSONObject.quote("#go/$go")}", null)
    }
}

@Composable
fun SetupScreen() {
    val tk = t()
    val ctx = LocalContext.current
    var key by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(tk.bg).statusBarsPadding().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(tk.accent), contentAlignment = Alignment.Center) {
            Text("p", color = androidx.compose.ui.graphics.Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(16.dp))
        Text("planLAB", style = MaterialTheme.typography.displaySmall, color = tk.fg)
        Text("Your plan, your body, your day. One tap at a time.", color = tk.muted, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(28.dp))
        PButton("Bring my key from planLAB web", Modifier.fillMaxWidth()) { open(ctx, GitHub.WEB + "#app") }
        Text("Opens your planner in the browser. Tap “Send my key to the app” there.", Modifier.padding(top = 6.dp, bottom = 22.dp), color = tk.muted, fontSize = 13.sp)
        Text("Or paste a key", fontWeight = FontWeight.Bold, color = tk.fg)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), placeholder = { Text("github_pat_…") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = tk.accent, unfocusedBorderColor = tk.line), shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PButton("Paste", Modifier.weight(1f), tk.sunk, tk.fg) {
                val cm = ctx.getSystemService(ClipboardManager::class.java)
                cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim()?.let { key = it }
            }
            PButton("Open my plan", Modifier.weight(1f)) { if (key.isNotBlank()) Store.setKey(key) }
        }
        PButton("Make a new key on GitHub", Modifier.fillMaxWidth().padding(top = 14.dp), tk.surface, tk.accent) { open(ctx, GitHub.KEY_URL) }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val tk = t()
    val ctx = LocalContext.current
    val st by Store.state.collectAsState()
    val prefs = Store.prefs()
    val connect = rememberHealthConnect()
    var tick by remember { mutableStateOf(0) }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Alarms.scheduleAll(ctx); tick++ }
    BackHandler { onBack() }
    LazyColumn(Modifier.fillMaxSize().background(tk.bg).statusBarsPadding(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = tk.fg) }
                Text("Settings", style = MaterialTheme.typography.headlineSmall, color = tk.fg)
            }
        }
        item {
            Label("Health")
            PCard {
                Text(if (st.health.connected) "Connected · ${st.health.granted} kinds of data" else "Not connected", style = MaterialTheme.typography.titleMedium, color = tk.fg)
                Spacer(Modifier.height(10.dp))
                PButton(if (st.health.connected) "Allow all health data" else "Connect Health Connect", onClick = connect)
                Toggle("Send a daily summary to Claude", "To your private planner, so weekly plans use your sleep and recovery.", prefs.getBoolean("share_health", true)) {
                    prefs.edit().putBoolean("share_health", it).apply(); tick++
                }
            }
        }
        item {
            Label("Notifications")
            PCard {
                if (!Notifier.allowed(ctx) && Build.VERSION.SDK_INT >= 33) {
                    PButton("Turn on notifications", Modifier.fillMaxWidth()) { askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    Spacer(Modifier.height(8.dp))
                }
                key(tick) {
                    Toggle("Pinned Today card", "Always in your notification shade, with buttons for what's left.", prefs.getBoolean("pinned", true)) {
                        prefs.edit().putBoolean("pinned", it).apply(); Notifier.refreshPinned(ctx); tick++
                    }
                    for (s in Slot.entries.distinctBy { it.label }) {
                        val on = s.enabled()
                        Toggle(s.label, s.time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")), on) { v ->
                            Slot.entries.filter { it.label == s.label }.forEach { prefs.edit().putBoolean("slot_${it.name}", v).apply(); Alarms.schedule(ctx, it) }
                            tick++
                        }
                    }
                }
            }
        }
        item {
            Label("Planner")
            PCard {
                Text(if (st.synced) "Synced with your planner" else "Not synced yet", style = MaterialTheme.typography.titleMedium, color = tk.fg)
                if (st.pending > 0) Text("${st.pending} taps waiting to send", color = tk.muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PButton("Refresh now", Modifier.weight(1f)) { Store.refreshAsync() }
                    PButton("New key", Modifier.weight(1f), tk.sunk, tk.fg) { Store.clearKey(); onBack() }
                }
            }
        }
        item {
            Label("App")
            PCard {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                var upd by remember { mutableStateOf("Updates install on their own.") }
                Text("planLAB ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = tk.fg)
                Text(upd, color = tk.muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PButton("Check now", Modifier.weight(1f), tk.sunk, tk.fg) {
                        upd = "Checking…"
                        scope.launch { upd = com.mattlabs.planlab.update.AutoUpdate.check(ctx, 0)?.let { "Installing $it…" } ?: "You have the newest version." }
                    }
                    PButton("Download", Modifier.weight(1f), tk.sunk, tk.fg) { open(ctx, com.mattlabs.planlab.update.AutoUpdate.APK) }
                }
            }
        }
    }
}

@Composable
private fun key(k: Int, content: @Composable () -> Unit) = androidx.compose.runtime.key(k) { content() }

@Composable
private fun Toggle(title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
    val tk = t()
    var v by remember(on) { mutableStateOf(on) }
    RowItem(title = title, sub = sub) {
        Switch(v, { v = it; set(it) }, colors = SwitchDefaults.colors(checkedTrackColor = tk.accent))
    }
}
