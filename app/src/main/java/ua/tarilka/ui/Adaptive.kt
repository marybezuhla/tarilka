package ua.tarilka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** From this window width the tabs move to a rail at the side (tablets, unfolded phones, landscape). */
val RAIL_FROM: Dp = 600.dp

/** One column never grows wider than this: long lines are hard to read. */
private val COLUMN_MAX: Dp = 640.dp

/**
 * Whether two columns of cards fit side by side in [widthDp]: each needs 300 dp, more with a larger font,
 * plus 16 dp between them. The font counts only up to 1.5, so a large tablet keeps two columns even with
 * the largest font; the cards themselves already wrap their text.
 */
fun twoPanesFit(widthDp: Float, fontScale: Float): Boolean = widthDp >= 300f * fontScale.coerceIn(1f, 1.5f) * 2 + 16f

@Composable
fun twoPanes(width: Dp): Boolean = twoPanesFit(width.value, LocalDensity.current.fontScale)

/**
 * A screen of cards. On a phone one list, in this order: [header], [main], [side], [tail].
 * On a wide screen [header] stays on top, [main] and [tail] go on the left and [side] on the right,
 * each column scrolling on its own.
 */
@Composable
fun CardColumns(
    padding: PaddingValues,
    header: (@Composable () -> Unit)? = null,
    main: LazyListScope.() -> Unit,
    side: LazyListScope.() -> Unit,
    tail: LazyListScope.() -> Unit = {},
) {
    val top = padding.calculateTopPadding() + 8.dp
    val bottom = padding.calculateBottomPadding() + 24.dp
    val gap = Arrangement.spacedBy(12.dp)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (!twoPanes(maxWidth)) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = COLUMN_MAX),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = bottom),
                verticalArrangement = gap,
            ) {
                if (header != null) item(key = "header") { header() }
                main()
                side()
                tail()
            }
        } else {
            Column(Modifier.fillMaxSize().padding(top = top)) {
                // Not scrolled with either column: the day and its arrows stay in reach
                if (header != null) Box(Modifier.padding(horizontal = 16.dp)) { header() }
                Row(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    val pad = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = bottom)
                    LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = pad, verticalArrangement = gap) {
                        main()
                        tail()
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = pad, verticalArrangement = gap) {
                        side()
                    }
                }
            }
        }
    }
}

/**
 * A tab's name that always fits on one line: with a very large font it gets a little smaller
 * instead of being cut off ("Option" for "Options"). The icon next to it does not change.
 */
@Composable
fun TabLabel(text: String) {
    val style = LocalTextStyle.current.merge(color = LocalContentColor.current, textAlign = TextAlign.Center)
    val size = if (style.fontSize.isSp) style.fontSize else 12.sp
    Box(contentAlignment = Alignment.Center) {
        BasicText(
            text,
            style = style,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = size, stepSize = 0.5.sp),
        )
    }
}
