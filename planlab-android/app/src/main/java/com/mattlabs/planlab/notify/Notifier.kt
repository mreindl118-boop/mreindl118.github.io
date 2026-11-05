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
import com.mattlabs.planlab.core.Discreet
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
    const val CH_BREAK = "breaks"
    const val CH_MIND = "mind"
    /** Check-ins buzz in their own pattern (short, short, long) so they feel different from everything else. */
    val MIND_BUZZ = longArrayOf(0, 90, 140, 90, 140, 500)
    /** Used until the plan has a "### mind" box. */
    private val MIND_FALLBACK = listOf("3 slow breaths", "Drop your shoulders", "Feel your feet", "Unclench your jaw", "Look far away")
    const val ID_TODAY = 1

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_BRIEF, "Morning plan", NotificationManager.IMPORTANCE_HIGH).apply { description = "Your day and how ready your body is, each morning" })
        nm.createNotificationChannel(NotificationChannel(CH_NUDGE, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply { description = "Body, practice, meals and recovery, with buttons to answer" })
        nm.createNotificationChannel(NotificationChannel(CH_BREAK, "Move breaks", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "A gentle nudge to get up and move for a minute" })
        nm.createNotificationChannel(NotificationChannel(CH_MIND, "Check-ins", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "A one-minute pause, with its own buzz"; enableVibration(true); vibrationPattern = MIND_BUZZ
        })
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
        // On the lock screen: just "planLAB", details after unlocking.
        b.setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(
            NotificationCompat.Builder(ctx, b.build().channelId ?: CH_NUDGE).setSmallIcon(R.drawable.ic_stat).setContentTitle("planLAB").setContentText("1 thing to do").build())
        try { NotificationManagerCompat.from(ctx).notify(id, b.build()) } catch (_: SecurityException) {}
    }

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)

    private fun base(ctx: Context, ch: String, title: String, text: String) = NotificationCompat.Builder(ctx, ch)
        .setSmallIcon(R.drawable.ic_stat).setColor(0xFF2F7DE1.toInt())
        .setContentTitle(Discreet.text(title)).setContentText(Discreet.text(text)).setStyle(NotificationCompat.BigTextStyle().bigText(Discreet.text(text)))
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
                val title = if (r != null) "${r.score} ready" else "Today"
                val first = day?.let { com.mattlabs.planlab.ui.TodayData.next(it) }
                val line = first?.let { n -> n.step?.let { com.mattlabs.planlab.ui.TodayData.short(it) } ?: com.mattlabs.planlab.ui.TodayData.label(n.box) } ?: "Nothing planned"
                post(ctx, id, base(ctx, CH_BRIEF, title, "First: $line")
                    .addAction(NotificationCompat.Action.Builder(0, "Open", openApp(ctx)).build())
                    .addAction(action(ctx, "Snooze 1h", "snooze", slot, minutes = 60, notif = id)))
            }
            Slot.PELVIC_AM, Slot.PELVIC_PM -> {
                val n = LogDoc.pelvic(log, today.toString())
                if (n >= 2 || (slot == Slot.PELVIC_AM && n >= 1)) return false
                post(ctx, id, base(ctx, CH_NUDGE, "Quick set · 1 min", "Set ${n + 1} of 2")
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
                // Eat is tracked, not ticked: one nudge in the evening if dinner isn't logged yet.
                val food = Store.foodOn(today)
                if (food.any { it.meal == "dinner" }) return false
                val have = Store.eaten(today); val want = Store.foodTargets(today)
                val next = Store.nextPlanned(today)?.let { Store.planned(it) }?.takeIf { it.meal == "dinner" }
                val nb = base(ctx, CH_NUDGE, if (next != null) "Dinner: ${next.name}?" else "Dinner?",
                    "%d / %d g protein".format(have.protein, want.protein))
                    .setContentIntent(foodIntent(ctx))
                if (next != null) nb.addAction(action(ctx, "✓ Ate it", "ateplan", slot, notif = id))
                nb.addAction(NotificationCompat.Action.Builder(0, if (next != null) "Other" else "Track", foodIntent(ctx)).build())
                nb.addAction(action(ctx, "In 1 hour", "snooze", slot, minutes = 60, notif = id))
                post(ctx, id, nb)
            }
            Slot.NIBBLES -> {
                // Grace calories: ask once, late, with one tap per bite. Each tap brings the banner back with the new count.
                val (count, kcal) = Store.nibbles(today)
                val nb = base(ctx, CH_NUDGE, "Any nibbles today?", if (count > 0) "🤏 $count logged · ${kcal} kcal. Tap once per bite." else "A bite, a handful, a taste? Tap once each.")
                    .setContentIntent(foodIntent(ctx))
                listOf("bite", "handful", "sweet").forEach { k ->
                    val n = st.foods.nibbles.firstOrNull { it.id == k } ?: return@forEach
                    nb.addAction(action(ctx, "+ ${n.icon} ${n.name}", "nibble", slot, k, notif = id))
                }
                post(ctx, id, nb)
            }
            Slot.ELEVATE_1, Slot.ELEVATE_2, Slot.ELEVATE_3 -> {
                if (b.phase != Phase.NWB) return false
                post(ctx, id, base(ctx, CH_NUDGE, "Elevate 20 min", "Foot above your heart. Good time for a show or seated practice.")
                    .addAction(action(ctx, "Done", "elevate", slot, notif = id))
                    .addAction(action(ctx, "In 30 min", "snooze", slot, minutes = 30, notif = id)))
            }
            Slot.MOVE_1, Slot.MOVE_2, Slot.MOVE_3 -> {
                val box = day?.boxes?.firstOrNull { it.key == "move" } ?: return false
                val step = box.steps.getOrNull(slot.breakIndex) ?: return false
                if (Store.stepDone(day, step) || LogDoc.skipped(log, today.toString(), "move")) return false
                val kind = com.mattlabs.planlab.core.Moves.kindOf(box, step, day)
                val nb = NotificationCompat.Builder(ctx, CH_BREAK).setSmallIcon(R.drawable.ic_stat).setColor(0xFF0E8FB0.toInt())
                    .setContentTitle(com.mattlabs.planlab.ui.TodayData.short(step)).setContentText("Break · ${kind?.minutes ?: 1} min")
                    .setContentIntent(moveIntent(ctx, box.key, step)).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .addAction(action(ctx, "✓ Done", "step", slot, box.key, notif = id, step = step.key))
                    .also { nb ->
                        val pick = step.links.firstOrNull { it.url == "app:stretch" }
                        nb.addAction(if (pick != null) NotificationCompat.Action.Builder(0, "Where am I?", sheetIntent(ctx, "stretch", box.key, step)).build()
                            else NotificationCompat.Action.Builder(0, "How to", moveIntent(ctx, box.key, step)).build())
                    }
                    .addAction(action(ctx, "In 15 min", "snooze", slot, minutes = 15, notif = id))
                post(ctx, id, nb)
            }
            Slot.MIND_1, Slot.MIND_2, Slot.MIND_3 -> {
                val box = day?.boxes?.firstOrNull { it.key == "mind" }
                val step = box?.steps?.getOrNull(slot.breakIndex)
                val nb = NotificationCompat.Builder(ctx, CH_MIND).setSmallIcon(R.drawable.ic_stat).setColor(0xFF12A383.toInt())
                    .setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_HIGH).setVibrate(MIND_BUZZ)
                if (day == null || box == null || step == null) {
                    // No "### mind" box in the plan yet: two built-in check-ins a day.
                    if (slot == Slot.MIND_3 || (day != null && day.boxes.any { it.key == "mind" })) return false
                    val p = MIND_FALLBACK[(today.dayOfYear * 2 + slot.breakIndex) % MIND_FALLBACK.size]
                    nb.setContentTitle(p).setContentText("1 min").setContentIntent(openApp(ctx))
                        .addAction(action(ctx, "✓ Done", "mind", slot, notif = id))
                        .addAction(action(ctx, "In 15 min", "snooze", slot, minutes = 15, notif = id))
                    post(ctx, id, nb)
                    return true
                }
                if (Store.stepDone(day, step) || LogDoc.skipped(log, today.toString(), "mind")) return false
                val journal = step.links.any { it.url == "app:journal" }
                val q = com.mattlabs.planlab.ui.MindSheet.question(step.label)
                val title = q ?: com.mattlabs.planlab.core.TodayPick.words(com.mattlabs.planlab.ui.MoveSheet.shortLabel(step.label), 4)
                val open = if (journal) sheetIntent(ctx, "journal", box.key, step) else moveIntent(ctx, box.key, step)
                nb.setContentTitle(title).setContentText(if (journal) "Journal" else "1 min").setContentIntent(open)
                    .addAction(if (journal) NotificationCompat.Action.Builder(0, "Write", open).build() else action(ctx, "✓ Done", "step", slot, box.key, notif = id, step = step.key))
                    .addAction(action(ctx, "In 15 min", "snooze", slot, minutes = 15, notif = id))
                post(ctx, id, nb)
            }
            Slot.EVENING -> {
                if (day == null) return false
                val open = day.boxes.filter { !Store.isDone(day, it) && !LogDoc.skipped(log, today.toString(), it.key) }
                if (open.isEmpty()) return false
                val done = day.boxes.size - open.size
                val nb = base(ctx, CH_NUDGE, "$done/${day.boxes.size} done", open.joinToString("\n") { nextLabel(day, it) })
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
            base(ctx, CH_NUDGE, com.mattlabs.planlab.ui.TodayData.short(next), "${Store.LABEL[key] ?: box.label} ${n + 1}/${box.steps.size}")
                .addAction(action(ctx, "✓ Done", "step", slot, key, notif = id, step = next.key))
                .also { nb -> next.links.firstOrNull()?.let { l -> nb.addAction(NotificationCompat.Action.Builder(0, com.mattlabs.planlab.ui.Links.name(l, next.label).take(16), linkIntent(ctx, l.url)).build()) } }
        } else {
            base(ctx, CH_NUDGE, title, text).addAction(action(ctx, whole?.first ?: "Done", "done", slot, key, whole?.second ?: 0, id))
        }
        // Android shows three buttons: Done, the step's link (if any) or snooze, and Skip.
        if (next?.links.isNullOrEmpty()) snoozes.forEach { (label, m) -> nb.addAction(action(ctx, label, "snooze", slot, minutes = m, notif = id)) }
        nb.addAction(action(ctx, "Skip today", "skip", slot, key, notif = id))
        post(ctx, id, nb)
        return true
    }

    private fun nextLabel(day: com.mattlabs.planlab.core.Day, box: com.mattlabs.planlab.core.Box): String {
        val name = Store.LABEL[box.key] ?: box.label.replaceFirstChar { it.uppercase() }
        val next = Store.nextStep(day, box) ?: return name
        return "$name: ${com.mattlabs.planlab.ui.TodayData.short(next)}"
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
            append(if (open.isEmpty()) "All done" else "$done/${day.boxes.size}")
            b.readiness?.let { append(" · ${it.score}") }
            Store.state.value.progress?.let { p -> if (p.streak > 1) append(" · 🔥${p.streak}") }
        }
        val text = com.mattlabs.planlab.ui.TodayData.next(day)?.let { n -> "Next: " + (n.step?.let { com.mattlabs.planlab.ui.TodayData.short(it) } ?: com.mattlabs.planlab.ui.TodayData.label(n.box)) } ?: "✓"
        val nb = NotificationCompat.Builder(ctx, CH_TODAY).setSmallIcon(R.drawable.ic_stat).setColor(0xFF2F7DE1.toInt())
            .setContentTitle(Discreet.text(title)).setContentText(Discreet.text(text)).setStyle(NotificationCompat.BigTextStyle().bigText(Discreet.text(text))).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(openApp(ctx)).setProgress(day.boxes.size, done, false)
            .setPriority(NotificationCompat.PRIORITY_LOW).setCategory(NotificationCompat.CATEGORY_STATUS)
        open.take(1).forEach { nb.addAction(action(ctx, "✓ " + (Store.LABEL[it.key] ?: it.label.take(14)), "step", null, it.key, notif = 0)) }
        nb.addAction(NotificationCompat.Action.Builder(0, "Food", foodIntent(ctx)).build())
        nb.addAction(NotificationCompat.Action.Builder(0, "Bugs me", bugIntent(ctx)).build())
        post(ctx, ID_TODAY, nb)
    }

    fun foodIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(ctx, 78, Intent(ctx, com.mattlabs.planlab.ui.FoodActivity::class.java).putExtra("from", "notif").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Opens the app on today's instruction card for this step. */
    fun moveIntent(ctx: Context, boxKey: String, step: com.mattlabs.planlab.core.Box): PendingIntent {
        val key = step.links.firstOrNull { it.url.startsWith("app:move/") }?.url?.removePrefix("app:") ?: "move/"
        val i = Intent(ctx, MainActivity::class.java).putExtra("go", key).putExtra("box", boxKey).putExtra("step", step.key).putExtra("from", "notif")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, step.key.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Opens the stretch picker or the journal box for this step. */
    fun sheetIntent(ctx: Context, kind: String, boxKey: String, step: com.mattlabs.planlab.core.Box): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).putExtra("go", kind).putExtra("box", boxKey).putExtra("step", step.key).putExtra("from", "notif")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, (kind + step.key).hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun linkIntent(ctx: Context, url: String): PendingIntent =
        PendingIntent.getActivity(ctx, url.hashCode(), com.mattlabs.planlab.ui.Links.intent(ctx, url, "notif"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun bugIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(ctx, 77, Intent(ctx, com.mattlabs.planlab.ui.BugActivity::class.java).putExtra("from", "notif").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
