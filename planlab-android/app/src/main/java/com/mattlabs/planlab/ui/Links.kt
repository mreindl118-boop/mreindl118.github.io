package com.mattlabs.planlab.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mattlabs.planlab.core.Link
import com.mattlabs.planlab.data.Store

/**
 * Where a step's link goes. "app:bugs" opens the capture sheet; other "app:" targets (body, pelvic, piano, voice,
 * recipe/<slug>, cook/<slug>, kroger, kitchen, freezer, calendar, inbox, wish) open that view of the planner inside the app;
 * https links open in their own app (YouTube, Spotify, Maps…) or the browser.
 */
object Links {
    private val TABS = setOf("today", "food", "health", "week", "progress", "planner")

    fun intent(ctx: Context, url: String, from: String): Intent = when {
        url == "app:bugs" -> Intent(ctx, BugActivity::class.java).putExtra("from", from)
        url.startsWith("app:") -> Intent(ctx, MainActivity::class.java).putExtra("go", url.removePrefix("app:")).putExtra("from", from)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        else -> Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun open(ctx: Context, link: Link, from: String, label: String = "") {
        Store.count(if (link.url.startsWith("app:")) "link:${link.url.removePrefix("app:").substringBefore('/')}" else "link:web")
        if (link.url.startsWith("app:move/") && from == "step") { MoveSheet.show(MoveSheet.Req(java.time.LocalDate.now(), null, null, link.url.removePrefix("app:move/"))); return }
        if (link.url == "app:stretch" || link.url == "app:journal") { MindSheet.forLabel(link.url.removePrefix("app:"), label); return }
        runCatching { ctx.startActivity(intent(ctx, link.url, from)) }
    }

    /** Which tab an in-app target opens; anything else opens the planner at that view. */
    fun tabFor(target: String): String = when {
        target == "wish" || target == "stretch" || target == "journal" || target.startsWith("move/") -> "today"
        target in TABS -> target
        else -> "planner"
    }

    /** What to name a link's button: its own label, or "Open" when it's just the step's text again. */
    fun name(link: Link, stepLabel: String): String = if (link.label.equals(stepLabel, true)) "Open" else link.label
}
