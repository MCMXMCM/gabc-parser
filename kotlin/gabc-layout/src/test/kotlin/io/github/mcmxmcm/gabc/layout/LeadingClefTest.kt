package io.github.mcmxmcm.gabc.layout

import io.github.mcmxmcm.gabc.parser.GregorianScoreParser
import org.junit.Assert.assertEquals
import org.junit.Test

// Kotlin's own, for a slip the Swift tests don't cover: a clef that opens a staff is set at its head, and takes no
// room in it, as Swift's `continue` past the staff's advance leaves it.
class LeadingClefTest {
    @Test fun aClefThatOpensALaterStaffTakesNoRoomInIt() {
        val score = GregorianScoreParser.parse("%% (c3) Al(h) (z) (c3) Al(h) (::)")
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f)
        val first = layout.events.filter { it.lineIndex == 0 }.minBy { it.frame.left }
        val second = layout.events.filter { it.lineIndex == 1 }.minBy { it.frame.left }
        assertEquals(first.frame.left, second.frame.left, 0.001f)
    }

    @Test fun anOpeningLabelMayEndInANumeralOfAnyScript() {
        assertEquals("8G²", GregorianEngravingLayoutEngine.openingLabel("8g²"))
    }
}
