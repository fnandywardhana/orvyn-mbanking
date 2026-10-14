package id.orvyn.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import id.orvyn.app.ui.theme.Brand

/** Stripe pair for the mark; pick the one whose colors both stand out from the surface behind it. */
enum class MarkOn(val first: Color, val second: Color) {
    Light(Brand.Nerazzurro, Brand.Nero),
    Dark(Brand.Nerazzurro, Color.White),
    Blue(Color.White, Brand.Nero),
    /** Tonal watermark for hero backgrounds; never competes with white text or the primary button. */
    Watermark(Color.White.copy(alpha = 0.10f), Brand.Nero.copy(alpha = 0.55f)),
}

/** The ORVYN ring of five vertical stripes. */
@Composable
fun OrvynMark(modifier: Modifier = Modifier, on: MarkOn = MarkOn.Dark) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        val ring = Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(c, r))
            addOval(Rect(c, r * 20 / 46))
        }
        val stripe = r * 2 / 5
        clipPath(ring) {
            for (i in 0 until 5) drawRect(
                color = if (i % 2 == 0) on.first else on.second,
                topLeft = Offset(c.x - r + i * stripe, c.y - r),
                size = Size(stripe + 0.5f, r * 2),
            )
        }
    }
}
