package com.mattlabs.planlab.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mattlabs.planlab.data.Store
import com.mattlabs.planlab.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** The day's banners. Each only shows if its thing is still open. */
enum class Slot(val label: String, val time: LocalTime, val notifId: Int) {
    BRIEF("Morning plan", LocalTime.of(7, 30), 10),
    PELVIC_AM("Pelvic floor, morning", LocalTime.of(9, 30), 11),
    ELEVATE_1("Elevate (recovery)", LocalTime.of(11, 0), 12),
    ELEVATE_2("Elevate (recovery)", LocalTime.of(15, 0), 13),
    BODY("Body", LocalTime.of(16, 30), 14),
    VOICE("Voice", LocalTime.of(18, 30), 15),
    ELEVATE_3("Elevate (recovery)", LocalTime.of(19, 0), 16),
    PIANO("Piano", LocalTime.of(19, 30), 17),
    MEALS("Meals check", LocalTime.of(20, 0), 18),
    PELVIC_PM("Pelvic floor, evening", LocalTime.of(20, 30), 19),
    EVENING("Evening check-in", LocalTime.of(21, 30), 20);

    fun enabled(): Boolean = Store.prefs().getBoolean("slot_$name", true)
}

object Alarms {
    private fun pi(ctx: Context, slot: Slot, snooze: Boolean): PendingIntent =
        PendingIntent.getBroadcast(ctx, slot.ordinal + if (snooze) 100 else 0,
            Intent(ctx, AlarmReceiver::class.java).setAction("planlab.alarm.${slot.name}.$snooze").putExtra("slot", slot.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun at(ctx: Context, millis: Long, p: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, p)
    }

    fun next(slot: Slot, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val t = LocalDate.now().atTime(slot.time)
        return if (t.isAfter(now.plusSeconds(30))) t else t.plusDays(1)
    }

    fun scheduleAll(ctx: Context) {
        Store.init(ctx)
        for (s in Slot.entries) schedule(ctx, s)
    }

    fun schedule(ctx: Context, slot: Slot) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (!slot.enabled()) { am.cancel(pi(ctx, slot, false)); return }
        at(ctx, next(slot).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi(ctx, slot, false))
    }

    fun snooze(ctx: Context, slot: Slot, minutes: Int) =
        at(ctx, System.currentTimeMillis() + minutes * 60_000L, pi(ctx, slot, true))
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val slot = intent.getStringExtra("slot")?.let { n -> Slot.entries.firstOrNull { it.name == n } } ?: return
        Store.init(ctx)
        val p = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // A fresh look at Health Connect makes the morning banner's numbers current.
                if (slot == Slot.BRIEF) runCatching { Store.readHealth() }
                if (slot.enabled()) Notifier.fire(ctx, slot)
                Alarms.schedule(ctx, slot)
                Notifier.refreshPinned(ctx)
                runCatching { Widgets.updateAll(ctx) }
            } finally { p.finish() }
        }
    }
}

/** Puts the alarms back after a reboot, an update or a clock change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        Alarms.scheduleAll(ctx)
        Notifier.refreshPinned(ctx)
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        val act = intent.getStringExtra("act") ?: return
        val key = intent.getStringExtra("key") ?: ""
        val min = intent.getIntExtra("min", 0)
        val notif = intent.getIntExtra("notif", 0)
        val slot = intent.getStringExtra("slot")?.let { n -> Slot.entries.firstOrNull { it.name == n } }
        val date = runCatching { LocalDate.parse(intent.getStringExtra("date")) }.getOrDefault(LocalDate.now())
        when (act) {
            "done" -> Store.done(date, key, min.takeIf { it > 0 })
            "step" -> Store.doNextStep(date, key, intent.getStringExtra("step")?.takeIf { it.isNotEmpty() })
            "skip" -> Store.skip(date, key)
            "pelvic" -> Store.pelvicSet(date)
            "elevate" -> Store.addCount(date, "elevate")
            "snooze" -> slot?.let { Alarms.snooze(ctx, it, if (min > 0) min else 60) }
        }
        if (notif != 0) Notifier.cancel(ctx, notif)
        // A step done from a banner brings the banner back with the next step.
        if (act == "step" && slot != null && date == LocalDate.now()) Notifier.fire(ctx, slot)
        val p = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { Notifier.refreshPinned(ctx); runCatching { Widgets.updateAll(ctx) } } finally { p.finish() }
        }
    }
}
