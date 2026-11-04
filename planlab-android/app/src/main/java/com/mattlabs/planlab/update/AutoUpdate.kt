package com.mattlabs.planlab.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.mattlabs.planlab.BuildConfig
import com.mattlabs.planlab.R
import com.mattlabs.planlab.data.Store
import com.mattlabs.planlab.notify.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Keeps planLAB current on its own. A few times a day it checks the newest release; if it's newer, it downloads the
 * APK and installs it through Android's package installer. The first time, Android asks Matt to allow installs from
 * planLAB and confirm (one tap on the "Update" notification). After that, planLAB installed itself, so Android lets
 * it update without asking (Android 12 and later).
 */
object AutoUpdate {
    const val APK = "https://github.com/mreindl118-boop/mreindl118.github.io/releases/latest/download/planlab.apk"
    private const val LATEST = "https://api.github.com/repos/mreindl118-boop/mreindl118.github.io/releases/latest"
    const val NOTIF = 900
    private const val EVERY = 3 * 60 * 60_000L

    fun newer(a: String, b: String): Boolean {
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }; val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) { val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }; if (d != 0) return d > 0 }
        return false
    }

    /** From the app on launch: at most once an hour. */
    suspend fun checkSoon(ctx: Context) = check(ctx, 60 * 60_000L)

    /** Checks if [minGap] has passed since the last check. Returns the newer version it started installing, or null. */
    suspend fun check(ctx: Context, minGap: Long = EVERY): String? = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences("planlab", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("update_at", 0) < minGap) return@withContext null
        prefs.edit().putLong("update_at", now).apply()
        val tag = runCatching {
            val c = URL(LATEST).openConnection() as HttpURLConnection
            c.setRequestProperty("Accept", "application/vnd.github+json"); c.setRequestProperty("User-Agent", "planLAB-Android")
            c.connectTimeout = 10000; c.readTimeout = 10000
            JSONObject(c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }).getString("tag_name")
        }.getOrNull() ?: return@withContext null
        val v = tag.substringAfter("planlab-v")
        if (!newer(v, BuildConfig.VERSION_NAME)) return@withContext null
        runCatching { install(app, download(app, v), v) }.onFailure { offerManual(app, v) }.getOrNull() ?: return@withContext null
        Store.count("update_start")
        v
    }

    private fun download(ctx: Context, v: String): File {
        val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.filter { it.name != "planlab-$v.apk" }?.forEach { it.delete() }
        val f = File(dir, "planlab-$v.apk")
        if (f.exists() && f.length() > 1_000_000) return f
        val tmp = File(dir, "part")
        var url = URL(APK)
        var c: HttpURLConnection
        var hops = 0
        while (true) {
            c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "planLAB-Android")
            c.connectTimeout = 15000; c.readTimeout = 60000
            val code = c.responseCode
            if (code in 300..399 && hops++ < 5) { url = URL(url, c.getHeaderField("Location")); c.disconnect(); continue }
            if (code !in 200..299) throw IllegalStateException("download $code")
            break
        }
        c.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
        c.disconnect()
        if (tmp.length() < 1_000_000) throw IllegalStateException("short download")
        tmp.renameTo(f)
        return f
    }

    private fun install(ctx: Context, apk: File, v: String) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("planlab.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val i = Intent(ctx, InstallReceiver::class.java).setAction("planlab.install.$v").putExtra("v", v)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            s.commit(PendingIntent.getBroadcast(ctx, id, i, flags).intentSender)
        }
    }

    /** If the installer can't run, a notification that opens the download in the browser. */
    fun offerManual(ctx: Context, v: String) {
        val open = PendingIntent.getActivity(ctx, 901, Intent(Intent.ACTION_VIEW, Uri.parse(APK)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        notify(ctx, "planLAB $v is ready", "Tap to download and install it.", open, "Download")
    }

    fun notify(ctx: Context, title: String, text: String, pi: PendingIntent, button: String) {
        if (!Notifier.allowed(ctx)) return
        val n = NotificationCompat.Builder(ctx, Notifier.CH_BRIEF).setSmallIcon(R.drawable.ic_stat).setColor(0xFF2F7DE1.toInt())
            .setContentTitle(title).setContentText(text).setContentIntent(pi).setAutoCancel(true)
            .addAction(NotificationCompat.Action.Builder(0, button, pi).build())
            .setPriority(NotificationCompat.PRIORITY_HIGH).build()
        try { NotificationManagerCompat.from(ctx).notify(NOTIF, n) } catch (_: SecurityException) {}
    }
}

/** Android's answer to an install: needs Matt's OK (first time), done, or failed. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val v = intent.getStringExtra("v") ?: ""
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return AutoUpdate.offerManual(ctx, v)
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val inFront = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                if (inFront && runCatching { ctx.startActivity(confirm) }.isSuccess) return
                val pi = PendingIntent.getActivity(ctx, 902, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                AutoUpdate.notify(ctx, "planLAB $v is ready", "Tap Update. After this one, updates install on their own.", pi, "Update")
            }
            PackageInstaller.STATUS_SUCCESS -> NotificationManagerCompat.from(ctx).cancel(AutoUpdate.NOTIF)
            PackageInstaller.STATUS_FAILURE_ABORTED -> {} // Matt said not now; the next check asks again.
            else -> AutoUpdate.offerManual(ctx, v)
        }
    }
}
