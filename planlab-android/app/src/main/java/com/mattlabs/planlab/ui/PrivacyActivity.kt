package com.mattlabs.planlab.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** What Health Connect shows when Matt asks why planLAB wants his data. Required for the permission screen. */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlanTheme {
                val tk = t()
                Column(Modifier.fillMaxSize().background(tk.bg).statusBarsPadding().padding(24.dp)) {
                    Text("How planLAB uses your health data", style = MaterialTheme.typography.headlineSmall, color = tk.fg)
                    Spacer(Modifier.height(12.dp))
                    for (line in listOf(
                        "planLAB reads sleep, heart, activity, body and nutrition data to adjust your daily plan: how hard to train, how long to practice, protein and water targets, and recovery reminders.",
                        "It only reads. It never writes to Health Connect.",
                        "The numbers stay on this phone, except a daily summary (one number per metric per day) that goes to your own private GeneralStorage repository so Claude can plan your weeks with it. You can turn that off in Settings.",
                        "No ads, no analytics, no other servers.",
                    )) { Text(line, color = tk.fg, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 12.dp)) }
                }
            }
        }
    }
}
