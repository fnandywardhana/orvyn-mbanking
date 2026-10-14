package id.orvyn.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.orvyn.app.ui.theme.Brand
import id.orvyn.app.ui.theme.OrvynType

/** Initials in a tinted circle, for people without a photo. */
@Composable
fun InitialsAvatar(name: String, modifier: Modifier = Modifier) {
    val initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Text(initials, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/**
 * The moment money has moved (design system §5): hero gradient, a check that draws itself, the amount,
 * a confirm buzz. [details] sits on a white card; [actions] go at the bottom.
 */
@Composable
fun SuccessScreen(
    headline: String,
    amount: String,
    subline: String,
    details: @Composable ColumnScope.() -> Unit,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondary: (@Composable () -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        progress.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }
    Column(
        Modifier.fillMaxSize().background(Brand.Hero).drawBehind {
            val w = 22.dp.toPx(); var x = 0f
            while (x < size.width) { drawRect(Color.White.copy(alpha = 0.05f), Offset(x, 0f), Size(w, size.height)); x += w * 2 }
        }.safeDrawingPadding().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Canvas(Modifier.size(88.dp)) {
            val stroke = 6.dp.toPx()
            drawCircle(Color.White.copy(alpha = 0.15f), radius = size.minDimension / 2)
            val check = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.67f)
                lineTo(size.width * 0.73f, size.height * 0.36f)
            }
            val partial = Path()
            PathMeasure().apply { setPath(check, false); getSegment(0f, length * progress.value, partial, true) }
            drawPath(partial, Color.White, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Spacer(Modifier.height(20.dp))
        Text(headline, style = MaterialTheme.typography.titleMedium, color = Brand.HeroTextMuted,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text(amount, style = OrvynType.displayBalance, color = Color.White, textAlign = TextAlign.Center)
        Text(subline, style = MaterialTheme.typography.bodyLarge, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = details,
        )
        Spacer(Modifier.weight(1f))
        secondary?.invoke()
        Button(
            onClick = onPrimary, shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.Nerazzurro),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(bottom = 8.dp),
        ) { Text(primaryLabel, style = MaterialTheme.typography.labelLarge) }
    }
}

/** Label + value line for receipts and review cards. Dark text: these sit on white even in dark mode. */
@Composable
fun ReceiptLine(label: String, value: String, onSurfaceWhite: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (onSurfaceWhite) Color(0xFF4D5872) else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = if (onSurfaceWhite) Color(0xFF0B1020) else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun WhiteTextButton(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}
