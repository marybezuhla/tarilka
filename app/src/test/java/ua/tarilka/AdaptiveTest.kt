package ua.tarilka

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.tarilka.ui.twoPanesFit

/** When the cards go two by two: never on a phone, on a tablet unless the font is large for its width. */
class AdaptiveTest {
    @Test
    fun phonesKeepOneColumn() {
        assertFalse(twoPanesFit(360f, 1f))
        assertFalse(twoPanesFit(411f, 1f))
        // An unfolded phone (about 673 dp) less the 80 dp rail
        assertFalse(twoPanesFit(593f, 1f))
    }

    @Test
    fun tabletsGetTwoColumns() {
        // A 10-inch tablet (800 dp) less the rail
        assertTrue(twoPanesFit(720f, 1f))
        // The same tablet with a larger font: one column, so no card is squeezed
        assertFalse(twoPanesFit(720f, 1.3f))
        // Held sideways (1280 dp): two columns even with the largest font
        assertTrue(twoPanesFit(1200f, 2f))
    }
}
