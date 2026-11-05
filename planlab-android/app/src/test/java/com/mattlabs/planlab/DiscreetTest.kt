package com.mattlabs.planlab

import com.mattlabs.planlab.core.Discreet
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscreetTest {
    @Test fun neutralWordsOutsideTheApp() {
        assertEquals("Core set 1/2", Discreet.text("Pelvic floor 1/2"))
        assertEquals("Next: Core set", Discreet.text("Next: pelvic floor exercises"))
        assertEquals("Core 0/2", Discreet.text("Pelvic 0/2"))
        assertEquals("Test bug", Discreet.text("Test bug"))
    }
}
