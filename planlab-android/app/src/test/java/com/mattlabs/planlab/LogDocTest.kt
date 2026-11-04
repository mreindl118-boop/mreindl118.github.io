package com.mattlabs.planlab

import com.mattlabs.planlab.core.HealthData
import com.mattlabs.planlab.core.LogDoc
import com.mattlabs.planlab.core.Metric
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LogDocTest {
    @Test fun opsReplayOntoWhateverIsOnGitHub() {
        // The web page ticked meals and froze 2; the phone ticks body and logs 25 piano minutes.
        val remote = JSONObject("""{"v":1,"updated":5,"ticks":{"2030-03-04":{"meals":true}},"mins":{},"freezer":{"chili":-2},"other":{"keep":1}}""")
        val ops = JSONArray().put(LogDoc.tickOp("2030-03-04", "body", true)).put(LogDoc.addOp("mins", "2030-03-04", "piano", 25)).put(LogDoc.addOp("ticks", "pf-2030-03-04", "n", 1))
        val d = LogDoc.apply(remote, ops, 99)
        assertTrue(LogDoc.ticked(d, "2030-03-04", "meals", false))
        assertTrue(LogDoc.ticked(d, "2030-03-04", "body", false))
        assertEquals(25, LogDoc.minutes(d, "2030-03-04", "piano"))
        assertEquals(1, LogDoc.pelvic(d, "2030-03-04"))
        assertEquals(-2, d.getJSONObject("freezer").getInt("chili"))
        assertEquals(1, d.getJSONObject("other").getInt("keep"))
        assertEquals(99L, d.getLong("updated"))
        assertFalse(LogDoc.ticked(d, "2030-03-04", "voice", false))
        assertTrue(LogDoc.ticked(d, "2030-03-04", "voice", true))
    }

    @Test fun healthRoundTrips() {
        val h = HealthData(mapOf(LocalDate.of(2030, 3, 4) to mapOf(Metric.SLEEP to 7.25, Metric.STEPS to 1234.0)), 7, true, 3)
        val back = HealthData.fromJson(h.toJson().toString())
        assertEquals(7.25, back.on(LocalDate.of(2030, 3, 4), Metric.SLEEP)!!, 0.001)
        assertEquals(3, back.granted)
        assertEquals("1,234", Metric.STEPS.format(1234.0))
        assertEquals("11:30 pm", Metric.BEDTIME.format(23.5))
        assertEquals("12:15 am", Metric.BEDTIME.format(24.25))
    }
}
