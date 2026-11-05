package com.mattlabs.planlab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattlabs.planlab.core.NICE_DAY
import com.mattlabs.planlab.data.Store
import java.time.LocalDate

/** The Food tab: the day's numbers, planned meals, the food box always open, nibbles, and "again" for what he eats most. */
@Composable
fun FoodScreen() {
    val tk = t()
    val st by Store.state.collectAsState()
    var date by remember { mutableStateOf(LocalDate.now()) }
    val today = LocalDate.now()
    val day = st.plan.day(date)
    val recent = remember(st.version, st.log) { Store.recentFoods() }

    LazyColumn(Modifier.fillMaxWidth().statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(date.format(NICE_DAY), color = tk.muted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Food", style = MaterialTheme.typography.displaySmall, color = tk.fg)
                }
                IconButton(onClick = { date = date.minusDays(1) }) { Icon(Icons.Filled.KeyboardArrowLeft, "Previous day", tint = tk.fg) }
                IconButton(onClick = { if (date < today) date = date.plusDays(1) }, enabled = date < today) { Icon(Icons.Filled.KeyboardArrowRight, "Next day", tint = if (date < today) tk.fg else tk.line) }
            }
            Spacer(Modifier.height(8.dp))
        }
        item { EatCard(day, date, alwaysOpen = true) }
        if (recent.isNotEmpty()) item {
            Spacer(Modifier.height(16.dp))
            Text("Again", Modifier.padding(start = 4.dp, bottom = 8.dp), color = tk.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (e in recent) Column(Modifier.widthIn(max = 160.dp).clip(RoundedCornerShape(16.dp)).background(tk.surface)
                    .clickable { Store.relog(date, e) }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text("+ ${e.name}", color = tk.fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2)
                    Text("${e.kcal} kcal · ${e.protein} g", color = tk.muted, fontSize = 11.sp)
                }
            }
        }
    }
}
