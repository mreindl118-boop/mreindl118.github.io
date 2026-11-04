package com.mattlabs.planlab

import com.mattlabs.planlab.core.Link
import com.mattlabs.planlab.core.Md
import org.junit.Assert.assertEquals
import org.junit.Test

class LinksTest {
    @Test fun splitsLinksOffTheStep() {
        val (t, links) = Md.splitLinks("Test groove, hands together, ×3 at 80 bpm · [Practice](app:piano) · [Tutorial](https://example.com/search?q=test)")
        assertEquals("Test groove, hands together, ×3 at 80 bpm", t)
        assertEquals(listOf(Link("Practice", "app:piano"), Link("Tutorial", "https://example.com/search?q=test")), links)
        val (t2, l2) = Md.splitLinks("[Test scale](https://example.com/a)")
        assertEquals("Test scale", Md.strip(t2))
        assertEquals("https://example.com/a", l2.single().url)
        val (t3, l3) = Md.splitLinks("Test stretch 5 min https://example.com/b")
        assertEquals("Test stretch 5 min", t3)
        assertEquals(Link("Open", "https://example.com/b"), l3.single())
    }

    @Test fun linksNeverChangeTheKey() {
        fun week(step: String) = Md.week("2030-W10", "# Week 2030-W10 (Mon 03-04 to Sun 03-10)\n\n## Mon 03-04\n- [ ] piano\n### piano\n- [ ] $step\n  - [ ] piano: indented $step\n")
        val plain = week("Test scale").days.single().boxes.single().steps
        val linked = week("Test scale · [Practice](app:piano)").days.single().boxes.single().steps
        assertEquals(plain.map { it.key }, linked.map { it.key })
        assertEquals("Test scale", linked[0].label)
        assertEquals(listOf(Link("Practice", "app:piano")), linked[0].links)
        assertEquals(listOf(Link("Practice", "app:piano")), linked[1].links)
        val inbox = Md.inbox("# Inbox\n\n## Soon\n\n- [ ] Test chair · Thu 03-07\n  - [ ] measure the test desk · [Guide](https://example.com/c)\n", java.time.LocalDate.of(2030, 3, 4))
        assertEquals("measure-the-test-desk", inbox.single().steps.single().key)
        assertEquals("https://example.com/c", inbox.single().steps.single().links.single().url)
    }
}
