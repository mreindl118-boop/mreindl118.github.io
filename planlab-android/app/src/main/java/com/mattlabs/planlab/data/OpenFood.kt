package com.mattlabs.planlab.data

import com.mattlabs.planlab.core.Food
import com.mattlabs.planlab.core.Foods
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Open Food Facts: free, no key. Used only for what the built-in table and Matt's own foods don't know. */
object OpenFood {
    suspend fun search(q: String): List<Food> = withContext(Dispatchers.IO) {
        runCatching {
            val u = "https://world.openfoodfacts.org/cgi/search.pl?search_simple=1&json=1&page_size=8&fields=product_name,brands,serving_size,nutriments&search_terms=" + URLEncoder.encode(q, "UTF-8")
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 12000
            c.setRequestProperty("User-Agent", "planLAB-Android/1.0 (personal planner)")
            val text = try { if (c.responseCode == 200) c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) } else "" } finally { c.disconnect() }
            val a = JSONObject(text).optJSONArray("products") ?: return@runCatching emptyList<Food>()
            (0 until a.length()).mapNotNull { i ->
                val p = a.getJSONObject(i); val n = p.optJSONObject("nutriments") ?: return@mapNotNull null
                val per = if (n.has("energy-kcal_serving")) "_serving" else "_100g"
                if (!n.has("energy-kcal$per") || p.optString("product_name").isBlank()) return@mapNotNull null
                Foods.food(p.optString("brands").split(",").first().trim(), p.optString("product_name"), if (per == "_serving") p.optString("serving_size").ifBlank { "1 serving" } else "100 g",
                    n.optDouble("energy-kcal$per", 0.0), n.optDouble("proteins$per", 0.0), n.optDouble("carbohydrates$per", 0.0), n.optDouble("fat$per", 0.0), "", "off")
            }.take(5)
        }.getOrDefault(emptyList())
    }
}
