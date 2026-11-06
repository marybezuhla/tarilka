package ua.tarilka

import org.junit.Assert.assertTrue
import org.junit.Test
import ua.tarilka.ui.theme.Theme
import ua.tarilka.ui.theme.attention
import ua.tarilka.ui.theme.contrast
import ua.tarilka.ui.theme.mix
import ua.tarilka.ui.theme.readableArgb

/** Every theme's text reads on its cards and on the page behind them (WCAG AA, at least 4.5:1). */
class PaletteTest {
    private val themes = Theme.entries.filter { it.palette != null }

    private fun check(theme: Theme, what: String, color: Long, vararg on: Pair<String, Long>) {
        for ((where, bg) in on) {
            val c = contrast(color, bg)
            assertTrue("${theme.key}: $what on $where is %.2f, needs 4.5".format(c), c >= 4.5)
        }
    }

    @Test
    fun textReadsInEveryTheme() {
        for (t in themes) {
            val p = t.palette!!
            val bgs = arrayOf("cards" to p.surface, "page" to p.background)
            check(t, "text", p.onSurface, *bgs, "fields" to p.surfaceVariant)
            check(t, "small print", p.onSurfaceVariant, *bgs, "fields" to p.surfaceVariant)
            check(t, "accent", p.primary, *bgs)
            check(t, "error", p.error, *bgs)
            check(t, "warning", p.attention(t.dark), *bgs, "fields" to p.surfaceVariant)
            check(t, "button text", p.onPrimary, "button" to p.primary)
        }
    }

    /**
     * The colours as the app shows them (Theme.kt): small print is strengthened towards the text colour, and
     * chosen chips, the "New food" button and the selected tab sit on tinted containers.
     */
    @Test
    fun textReadsAsShownAndOnContainers() {
        for (t in themes) {
            val p = t.palette!!
            val shownSmall = mix(p.onSurfaceVariant, p.onSurface, 0.45)
            val warn = p.attention(t.dark)
            val containers = arrayOf(
                "accent container" to mix(p.surface, p.primary, 0.2),
                "second container" to mix(p.surface, p.secondary, 0.25),
                "warning container" to mix(p.surface, warn, 0.2),
            )
            check(t, "text", p.onSurface, *containers)
            check(t, "small print as shown", shownSmall, "cards" to p.surface, "page" to p.background, "fields" to p.surfaceVariant, *containers)
        }
    }

    @Test
    fun aDayThemeIsLightAndANightThemeDark() {
        for (t in themes) {
            val p = t.palette!!
            assertTrue("${t.key}: text and page don't match light/dark", (contrast(p.background, 0xFF000000) > contrast(p.background, 0xFFFFFFFF)) == !t.dark)
        }
    }

    @Test
    fun readableArgbAlwaysEndsReadable() {
        // Colours the app makes readable at run time (nutrients, badges): any colour on any theme
        val samples = listOf(0xFF43A047, 0xFFFFB300, 0xFFF4511E, 0xFF9CC0EA, 0xFFB8483D, 0xFF8F6200)
        for (t in themes) {
            val p = t.palette!!
            for (s in samples) {
                val r = readableArgb(s, p.onSurface, p.surface, p.background, p.surfaceVariant)
                check(t, "colour %08X".format(s), r, "cards" to p.surface, "page" to p.background, "fields" to p.surfaceVariant)
            }
        }
    }
}
