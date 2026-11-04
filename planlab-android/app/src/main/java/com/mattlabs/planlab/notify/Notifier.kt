package com.mattlabs.planlab.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mattlabs.planlab.R
import com.mattlabs.planlab.core.LogDoc
import com.mattlabs.planlab.core.Phase
import com.mattlabs.planlab.data.Store
import com.mattlabs.planlab.ui.MainActivity
import java.time.LocalDate

/** Banners with buttons that log without opening the app, plus the pinned "Today" card. */
object Notifier {
    const val CH_NUDGE = "nudges"
    const val CH_BRIEF = "brief"
    const val CH_TODAY = "today"
    const val ID_TODAY = 1

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_BRIEF, "Morning plan", NotificationManager.IMPORTANCE_HIGH).apply { description = "Your day and how ready your body is, each morning" })
        nm.createNotificationChannel(NotificationChannel(CH_NUDGE, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply { description = "Body, practice, meals and recovery, with buttons to answer" })
        nm.createNotificationChannel(NotificationChannel(CH_TODAY, "Today card", NotificationManager.IMPORTANCE_LOW).apply { description = "A pinned card with what's left today"; setShowBadge(false) })
    }

    fun allowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openApp(ctx: Context, tab: String = "today"): PendingIntent =
        PendingIntent.getActivity(ctx, tab.hashCode(), Intent(ctx, MainActivity::class.java).putExtra("tab", tab).putExtra("from", "notif").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** A button that runs [ActionReceiver] with these extras. */
    fun action(ctx: Context, label: String, act: String, slot: Slot?, key: String = "", minutes: Int = 0, notif: Int = 0, step: String = ""): NotificationCompat.Action {
        val i = Intent(ctx, ActionReceiver::class.java).setAction("planlab.$act.$key.${slot?.name}.$minutes.$step")
            .putExtra("act", act).putExtra("key", key).putExtra("slot", slot?.name).putExtra("min", minutes).putExtra("notif", notif).putExtra("step", step)
            .putExtra("date", LocalDate.now().toString())
        val pi = PendingIntent.getBroadcast(ctx, (act + key + slot?.name + minutes + step).hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action.Builder(0, label, pi).build()
    }

    private fun post(ctx: Context, id: Int, b: NotificationCompat.Builder) {
        if (!allowed(ctx)) return
        Slot.entries.firstOrNull { it.notifId == id }?.let { slot ->
            Store.count("notif_shown:${slot.name}")
            val i = Intent(ctx, ActionReceiver::class.java).setAction("planlab.dismiss.${slot.name}").putExtra("act", "dismiss").putExtra("slot", slot.name)
            b.setDeleteIntent(PendingIntent.getBroadcast(ctx, 500 + slot.ordinal, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        try { NotificationManagerCompat.from(ctx).notify(id, b.build()) } catch (_: SecurityException) {}
    }

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)

    private fun base(ctx: Context, ch: String, title: String, text: String) = NotificationCompat.Builder(ctx, ch)
        .setSmallIcon(R.drawable.ic_stat).setColor(0xFF2F7DE1.toInt())
        .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(openApp(ctx)).setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER)

    /** Fires a slot's banner if there's still something to answer. Returns false if nothing was needed. */
    fun fire(ctx: Context, slot: Slot): Boolean {
        Store.init(ctx)
        val today = LocalDate.now()
        val st = Store.state.value
        val day = st.plan.day(today)
        val b = Store.brief(today)
        val log = st.log
        fun boxOpen(k: String): Boolean {
            val box = day?.boxes?.firstOrNull { it.key == k } ?: return false
            return !Store.isDone(day, box) && !LogDoc.skipped(log, today.toString(), k)
        }
        val id = slot.notifId
        when (slot) {
            Slot.BRIEF -> {
                val r = b.readiness
                val title = if (r != null) "${r.score} · ${r.label}" else "Your plan today"
                val lines = buildList {
                    add(b.body.title + if (b.body.easier) " (eased)" else "")
                    b.piano?.let { add(it.title) }
                    if (b.daysToBig != null && b.daysToBig > 0) add("${b.daysToBig} days to ${b.bigName}")
                    b.nudges.firstOrNull()?.let { add(it.title) }
                }
                post(ctx, id, base(ctx, CH_BRIEF, title, lines.joinToString("\n"))
                    .addAction(NotificationCompat.Action.Builder(0, "Open plan", openApp(ctx)).build())
                    .addAction(action(ctx, "Snooze 1h", "snooze", slot, minutes = 60, notif = id)))
            }
            Slot.PELVIC_AM, Slot.PELVIC_PM -> {
                val n = LogDoc.pelvic(log, today.toString())
                if (n >= 2 || (slot == Slot.PELVIC_AM && n >= 1)) return false
                post(ctx, id, base(ctx, CH_NUDGE, "Pelvic floor, set ${n + 1} of 2", "10 slow holds, 10 quick ones. Seated is fine.")
                    .addAction(action(ctx, "Done", "pelvic", slot, notif = id))
                    .addAction(action(ctx, "In 30 min", "snooze", slot, minutes = 30, notif = id)))
            }
            Slot.BODY -> {
                if (!boxOpen("body")) return false
                return area(ctx, slot, day!!, "body", b.body.title, b.body.detail, listOf("In 1 hour" to 60))
            }
            Slot.VOICE -> {
                if (!boxOpen("voice")) return false
                val m = day?.plannedMinutes("voice") ?: 10
                return area(ctx, slot, day!!, "voice", b.voice?.title ?: "Voice, $m min", b.voice?.detail ?: "", listOf("In 1 hour" to 60), "Did $m min" to m)
            }
            Slot.PIANO -> {
                if (!boxOpen("piano")) return false
                val m = day?.plannedMinutes("piano") ?: 25
                return area(ctx, slot, day!!, "piano", b.piano?.title ?: "Piano, $m min", b.piano?.detail ?: "", listOf("In 1 hour" to 60), "Did $m min" to m)
            }
            Slot.MEALS -> {
                if (!boxOpen("meals")) return false
                val target = b.protein
                return area(ctx, slot, day!!, "meals", "Ate the plan today?", day.eat.lastOrNull()?.let { "Dinner: $it · aim for $target g protein" } ?: "Aim for $target g protein", emptyList(), "Yes" to 0)
            }
            Slot.ELEVATE_1, Slot.ELEVATE_2, Slot.ELEVATE_3 -> {
                if (b.phase != Phase.NWB) return false
                post(ctx, id, base(ctx, CH_NUDGE, "Elevate 20 min", "Foot above your heart. Good time for a show or seated practice.")
                    .addAction(action(ctx, "Done", "elevate", slot, notif = id))
                    .addAction(action(ctx, "In 30 min", "snooze", slot, minutes = 30, notif = id)))
            }
            Slot.EVENING -> {
                if (day == null) return false
                val open = day.boxes.filter { !Store.isDone(day, it) && !LogDoc.skipped(log, today.toString(), it.key) }
                if (open.isEmpty()) return false
                val done = day.boxes.size - open.size
                val nb = base(ctx, CH_NUDGE, "$done of ${day.boxes.size} done today", "Still open:\n" + open.joinToString("\n") { nextLabel(day, it) })
                open.take(3).forEach { nb.addAction(action(ctx, "✓ " + (Store.LABEL[it.key] ?: it.label.take(14)), "step", slot, it.key, notif = id)) }
                post(ctx, id, nb)
            }
        }
        return true
    }

    /**
     * One area's banner. With steps, it shows the next step and "Done" ticks just that step, then the banner comes
     * back with the step after it. Without steps, "Done" ticks the whole area.
     */
    private fun area(ctx: Context, slot: Slot, day: com.mattlabs.planlab.core.Day, key: String, title: String, text: String,
                     snoozes: List<Pair<String, Int>>, whole: Pair<String, Int>? = null): Boolean {
        val id = slot.notifId
        val box = day.boxes.firstOrNull { it.key == key } ?: return false
        val next = Store.nextStep(day, box)
        val nb = if (next != null) {
            val n = Store.stepsDone(day, box)
            base(ctx, CH_NUDGE, "${Store.LABEL[key] ?: box.label} · step ${n + 1} of ${box.steps.size}", next.label + if (n == 0 && title.isNotBlank()) "\n$title" else "")
                .addAction(action(ctx, "✓ Done", "step", slot, key, notif = id, step = next.key))
        } else {
            base(ctx, CH_NUDGE, title, text).addAction(action(ctx, whole?.first ?: "Done", "done", slot, key, whole?.second ?: 0, id))
        }
        snoozes.forEach { (label, m) -> nb.addAction(action(ctx, label, "snooze", slot, minutes = m, notif = id)) }
        nb.addAction(action(ctx, "Skip today", "skip", slot, key, notif = id))
        post(ctx, id, nb)
        return true
    }

    private fun nextLabel(day: com.mattlabs.planlab.core.Day, box: com.mattlabs.planlab.core.Box): String {
        val name = Store.LABEL[box.key] ?: box.label.replaceFirstChar { it.uppercase() }
        val next = Store.nextStep(day, box) ?: return name
        return "$name: ${next.label}"
    }

    /** The pinned card: progress and one-tap buttons for what's left. */
    fun refreshPinned(ctx: Context) {
        Store.init(ctx)
        if (!Store.prefs().getBoolean("pinned", true) || !allowed(ctx)) { cancel(ctx, ID_TODAY); return }
        val today = LocalDate.now()
        val day = Store.state.value.plan.day(today)
        val b = Store.brief(today)
        if (day == null) { cancel(ctx, ID_TODAY); return }
        val log = Store.state.value.log
        val open = day.boxes.filter { !Store.isDone(day, it) && !LogDoc.skipped(log, today.toString(), it.key) }
        val done = day.boxes.size - open.size
        val title = buildString {
            append(if (open.isEmpty()) "All done today" else "Today $done/${day.boxes.size}")
            b.readiness?.let { append(" · ${it.score} ${it.label.lowercase()}") }
            if (b.daysToBig != null && b.daysToBig > 0) append(" · ${b.daysToBig}d to ${b.bigName}")
            Store.state.value.progress?.let { p -> append(" · Lv ${p.level.n}"); if (p.streak > 1) append(" 🔥${p.streak}") }
        }
        val text = if (open.isEmpty()) "Nice work." else open.joinToString("\n") { nextLabel(day, it) }
        val nb = NotificationCompat.Builder(ctx, CH_TODAY).setSmallIcon(R.drawable.ic_stat).setColor(0xFF2F7DE1.toInt())
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(openApp(ctx)).setProgress(day.boxes.size, done, false)
            .setPriority(NotificationCompat.PRIORITY_LOW).setCategory(NotificationCompat.CATEGORY_STATUS)
        open.take(2).forEach { nb.addAction(action(ctx, "✓ " + (Store.LABEL[it.key] ?: it.label.take(14)), "step", null, it.key, notif = 0)) }
        nb.addAction(NotificationCompat.Action.Builder(0, "Bugs me", bugIntent(ctx)).build())
        post(ctx, ID_TODAY, nb)
    }

    fun bugIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(ctx, 77, Intent(ctx, com.mattlabs.planlab.ui.BugActivity::class.java).putExtra("from", "notif").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
