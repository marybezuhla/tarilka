package ua.tarilka.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * "Worth attention" (over the target, short sleep, a warning): amber by day, honey by night, the same in
 * every theme, so it never takes a theme's green or red. Made readable on each theme's cards by [readable].
 */
private const val ATTENTION_LIGHT = 0xFF9A5F1CL
private const val ATTENTION_DARK = 0xFFF0BD7AL

/** Material colours from one of the themes; the phone's own (Material You) for the system ones. Same mapping as Komora. */
fun colorScheme(theme: Theme, context: Context): ColorScheme {
    val p = theme.palette
        ?: return if (theme.dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    fun c(v: Long) = Color(v)
    val base = if (theme.dark) darkColorScheme() else lightColorScheme()
    val attention = readableArgb(if (theme.dark) ATTENTION_DARK else ATTENTION_LIGHT, p.onSurface, p.surface, p.background)
    return base.copy(
        primary = c(p.primary),
        onPrimary = c(p.onPrimary),
        primaryContainer = c(mix(p.surface, p.primary, 0.2)),
        onPrimaryContainer = c(p.onSurface),
        secondary = c(p.secondary),
        onSecondary = c(p.onPrimary),
        secondaryContainer = c(mix(p.surface, p.secondary, 0.25)),
        onSecondaryContainer = c(p.onSurface),
        tertiary = c(attention),
        onTertiary = c(p.surface),
        tertiaryContainer = c(mix(p.surface, attention, 0.2)),
        onTertiaryContainer = c(p.onSurface),
        background = c(p.background),
        onBackground = c(p.onSurface),
        // Cards are the lighter (or, at night, raised) surface; the top bar sits on the background
        surface = c(p.surface),
        onSurface = c(p.onSurface),
        surfaceVariant = c(p.surfaceVariant),
        onSurfaceVariant = c(p.onSurfaceVariant),
        surfaceContainerLowest = c(p.surface),
        surfaceContainerLow = c(p.surface),
        surfaceContainer = c(p.surface),
        surfaceContainerHigh = c(p.surface),
        surfaceContainerHighest = c(p.surface),
        surfaceBright = c(p.surface),
        surfaceDim = c(p.background),
        outline = c(p.onSurfaceVariant),
        outlineVariant = c(mix(p.surfaceVariant, p.onSurfaceVariant, 0.3)),
        error = c(p.error),
    )
}

/**
 * [color], moved step by step towards [toward] (the text colour) until it reads on every one of [on]
 * (WCAG AA, at least 4.5:1). Colours that already read are kept as they are.
 */
fun readableArgb(color: Long, toward: Long, vararg on: Long): Long {
    var t = 0.0
    var c = color
    while (t < 1.0 && on.any { contrast(c, it) < 4.5 }) {
        t += 0.05
        c = mix(color, toward, t)
    }
    return c
}

private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

/** [color] readable as text on the cards and on the background of this colour scheme. */
fun ColorScheme.readable(color: Color): Color =
    Color(readableArgb(color.argb(), onSurface.argb(), surfaceContainerLow.argb(), surface.argb(), background.argb()))

/** Fixed colors for the four nutrients so they read the same everywhere. */
@Immutable
data class MacroColors(val protein: Color, val fat: Color, val carbs: Color, val fiber: Color, val kcal: Color)

private val LightMacros = MacroColors(
    protein = Color(0xFFB8483D), fat = Color(0xFF8F6200), carbs = Color(0xFF3F6FA3),
    fiber = Color(0xFF4E7A30), kcal = Color(0xFF4B7A5C),
)
private val DarkMacros = MacroColors(
    protein = Color(0xFFF0A196), fat = Color(0xFFF2C46B), carbs = Color(0xFF9CC0EA),
    fiber = Color(0xFFADD48C), kcal = Color(0xFFA8CFB3),
)

val LocalMacroColors = staticCompositionLocalOf { LightMacros }

private val Type = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium),
        // A bit larger than Material defaults: hints and small print stay readable.
        bodyMedium = bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
    )
}

/** Numbers line up in columns. */
val Numbers = TextStyle(fontFamily = FontFamily.Default, fontFeatureSettings = "tnum")

/** The full colour scheme of [theme], as the app shows it (small print a little stronger than Android's grey). */
fun appScheme(theme: Theme, context: Context): ColorScheme = colorScheme(theme, context).let {
    // Descriptions and small print darker (or lighter at night) than Android's grey: easier to read, as in Komora
    it.copy(onSurfaceVariant = androidx.compose.ui.graphics.lerp(it.onSurfaceVariant, it.onSurface, 0.45f))
}

@Composable
fun TarilkaTheme(day: Theme = Theme.VISK, night: Theme = Theme.HUTSULSHCHYNA, content: @Composable () -> Unit) {
    // AppCompat night mode (Auto / Light / Dark) is already applied to the configuration.
    val theme = if (isSystemInDarkTheme()) night else day
    val context = androidx.compose.ui.platform.LocalContext.current
    val scheme = remember(theme, context) { appScheme(theme, context) }
    val macros = remember(scheme, theme) {
        val m = if (theme.dark) DarkMacros else LightMacros
        MacroColors(scheme.readable(m.protein), scheme.readable(m.fat), scheme.readable(m.carbs), scheme.readable(m.fiber), scheme.readable(m.kcal))
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalMacroColors provides macros) {
        MaterialTheme(colorScheme = scheme, typography = Type, content = content)
    }
}
