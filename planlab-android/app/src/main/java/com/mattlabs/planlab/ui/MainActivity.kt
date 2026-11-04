package com.mattlabs.planlab.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.lifecycleScope
import com.mattlabs.planlab.data.Store
import com.mattlabs.planlab.notify.Alarms
import com.mattlabs.planlab.update.AutoUpdate
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var tab by mutableStateOf("today")
    private var settings by mutableStateOf(false)
    /** An "app:" target the planner should open (from a step's link), and a counter so the same one opens twice. */
    private var go by mutableStateOf<String?>(null)
    private var goN by mutableStateOf(0)

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { Alarms.scheduleAll(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        handle(intent)
        if (Build.VERSION.SDK_INT >= 33 && !Store.prefs().getBoolean("asked_notif", false)) {
            Store.prefs().edit().putBoolean("asked_notif", true).apply()
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            PlanTheme {
                val st by Store.state.collectAsState()
                LaunchedEffect(Unit) { AutoUpdate.checkSoon(this@MainActivity) }
                if (!st.hasKey) SetupScreen() else if (settings) SettingsScreen(onBack = { settings = false }) else Shell(st.loading)
                LaunchedEffect(settings) { if (settings) Store.count("screen:settings") }
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    override fun onResume() {
        super.onResume()
        Store.count("app_open")
        Store.count("screen:${if (settings) "settings" else tab}")
        val stale = System.currentTimeMillis() - Store.prefs().getLong("built", 0) > 5 * 60_000
        lifecycleScope.launch { Store.refresh(full = stale) }
    }

    /** planlab://key#<key> from the web page's "Send my key to the app", and tab extras from notifications. */
    private fun handle(i: Intent?) {
        i ?: return
        i.getStringExtra("tab")?.let { tab = it; settings = false }
        i.getStringExtra("go")?.let { t ->
            tab = Links.tabFor(t); settings = false
            if (tab == "planner") { go = t; goN++ }
            if (t == "stretch" || t == "journal") MindSheet.forLabel(t, "", i.getStringExtra("box"), i.getStringExtra("step"))
            if (t.startsWith("move/")) MoveSheet.show(MoveSheet.Req(java.time.LocalDate.now(), i.getStringExtra("box"), i.getStringExtra("step"), t.removePrefix("move/")))
            i.removeExtra("go")
        }
        i.getStringExtra("from")?.let { Store.count("open_from:$it"); i.removeExtra("from") }
        i.getStringExtra("bug")?.let { openBug(this, "shortcut", camera = it == "photo"); i.removeExtra("bug") }
        i.getStringExtra("food")?.let { openFood(this, "shortcut"); i.removeExtra("food") }
        val data = i.data ?: return
        if (data.scheme == "planlab" && data.host == "key") {
            val k = data.fragment ?: data.getQueryParameter("k")
            if (!k.isNullOrBlank()) Store.setKey(k)
        }
    }

    @Composable
    private fun Shell(loading: Boolean) {
        val tk = t()
        val back = { tab = "today" }
        androidx.activity.compose.BackHandler(enabled = tab != "today" && tab != "planner") { back() }
        Scaffold(
            containerColor = tk.bg,
            bottomBar = {
                NavigationBar(containerColor = tk.surface) {
                    for ((k, label, icon) in TABS) NavigationBarItem(
                        selected = tab == k, onClick = { if (tab != k) Store.count("screen:$k"); tab = k },
                        icon = { Icon(icon, null) }, label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = tk.accent, selectedTextColor = tk.accent, indicatorColor = tk.accentSoft, unselectedIconColor = tk.muted, unselectedTextColor = tk.muted),
                    )
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().background(tk.bg).padding(pad)) {
                AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { k ->
                    Column(Modifier.fillMaxSize()) {
                        when (k) {
                            "today" -> TodayScreen(loading, onSettings = { settings = true }, onOpen = { tab = it })
                            "health" -> HealthScreen(onSettings = { settings = true }, onInsights = { tab = "insights" })
                            "week" -> WeekScreen()
                            "progress" -> ProgressScreen()
                            "insights" -> InsightsScreen(onBack = back)
                            else -> PlannerScreen(onBack = back, go = go, goN = goN)
                        }
                    }
                }
                GainOverlay()
                MoveSheetHost()
                MindSheetHost()
            }
        }
    }

    companion object {
        val TABS: List<Triple<String, String, ImageVector>> = listOf(
            Triple("today", "Today", Icons.Filled.Home),
            Triple("health", "Body", Icons.Filled.Favorite),
            Triple("week", "Week", Icons.Filled.DateRange),
            Triple("progress", "Level", Icons.Filled.Star),
            Triple("planner", "Planner", Icons.Filled.List),
        )
    }
}
