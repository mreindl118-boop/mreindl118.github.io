package com.mattlabs.planlab.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/** The few GitHub calls planLAB needs, on Matt's private GeneralStorage repo, with the key he pasted. */
class GitHub(private val token: String) {
    class HttpError(val code: Int, msg: String) : IOException("GitHub $code: $msg")

    private fun call(path: String, method: String = "GET", body: String? = null, raw: Boolean = false): String {
        val c = URL("https://api.github.com/repos/$REPO/$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.useCaches = false
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Accept", if (raw) "application/vnd.github.raw+json" else "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        c.setRequestProperty("User-Agent", "planLAB-Android")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        c.disconnect()
        if (code !in 200..299) throw HttpError(code, text.take(200))
        return text
    }

    /** A file's text, or null if it doesn't exist. */
    fun file(path: String): String? = try { call("contents/$DIR/$path", raw = true) } catch (e: HttpError) { if (e.code == 404) null else throw e }

    /** Names (no .md) of the Markdown files in a folder. */
    fun list(dir: String): List<String> = try {
        val a = JSONArray(call("contents/$DIR/$dir"))
        (0 until a.length()).map { a.getJSONObject(it).getString("name") }.filter { it.endsWith(".md") }.map { it.removeSuffix(".md") }
    } catch (e: HttpError) { if (e.code == 404) emptyList() else throw e }

    /** A JSON file with its sha (for a later write), or null. */
    fun json(path: String): Pair<JSONObject, String>? = try {
        val o = JSONObject(call("contents/$DIR/$path"))
        val text = String(Base64.getMimeDecoder().decode(o.getString("content")), Charsets.UTF_8)
        JSONObject(text) to o.getString("sha")
    } catch (e: HttpError) { if (e.code == 404) null else throw e }

    /** Writes a file on main; returns the new sha. */
    fun put(path: String, text: String, sha: String?, message: String): String {
        val body = JSONObject().put("message", message).put("content", Base64.getEncoder().encodeToString(text.toByteArray()))
        if (sha != null) body.put("sha", sha)
        return JSONObject(call("contents/$DIR/$path", "PUT", body.toString())).getJSONObject("content").getString("sha")
    }

    fun wish(text: String, today: String) {
        val body = JSONObject().put("title", "Wish: $text").put("body", "Added from the planLAB app on $today. Claude: give it a date in inbox.md, then close this.")
        call("issues", "POST", body.toString())
    }

    companion object {
        const val REPO = "mreindl118-boop/GeneralStorage"
        const val DIR = "projects/planlab"
        const val KEY_URL = "https://github.com/settings/personal-access-tokens/new?name=planLAB%20Android&description=planLAB%20on%20my%20phone&target_name=mreindl118-boop&expires_in=none&contents=write&issues=write"
        const val WEB = "https://www.understorydesignstudio.com/planlab/"
    }
}
