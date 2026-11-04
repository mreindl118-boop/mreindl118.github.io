package com.mattlabs.planlab.ui

import android.content.Context
import android.widget.Toast
import com.mattlabs.planlab.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Once a day: is there a newer planLAB release? If so, a toast and the Settings button get it. */
object Updater {
    const val APK = "https://github.com/mreindl118-boop/mreindl118.github.io/releases/latest/download/planlab.apk"
    private const val LATEST = "https://api.github.com/repos/mreindl118-boop/mreindl118.github.io/releases/latest"

    suspend fun check(ctx: Context) {
        val prefs = ctx.getSharedPreferences("planlab", Context.MODE_PRIVATE)
        val today = java.time.LocalDate.now().toString()
        if (prefs.getString("update_checked", "") == today) return
        val tag = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(LATEST).openConnection() as HttpURLConnection
                c.setRequestProperty("Accept", "application/vnd.github+json"); c.setRequestProperty("User-Agent", "planLAB-Android")
                c.connectTimeout = 10000; c.readTimeout = 10000
                JSONObject(c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }).getString("tag_name")
            }.getOrNull()
        } ?: return
        prefs.edit().putString("update_checked", today).apply()
        if (newer(tag.substringAfter("planlab-v"), BuildConfig.VERSION_NAME)) {
            Toast.makeText(ctx, "A new planLAB is ready. Settings → Get the newest version.", Toast.LENGTH_LONG).show()
        }
    }

    fun newer(a: String, b: String): Boolean {
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }; val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) { val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }; if (d != 0) return d > 0 }
        return false
    }
}
