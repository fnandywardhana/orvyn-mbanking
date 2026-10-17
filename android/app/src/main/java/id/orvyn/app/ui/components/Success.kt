package id.orvyn.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import id.orvyn.app.ui.formatDateTime
import id.orvyn.app.ui.theme.Brand
import id.orvyn.app.ui.theme.OrvynType
import kotlinx.coroutines.launch
import java.time.Instant

/** Initials in a tinted circle, for people without a photo. */
@Composable
fun InitialsAvatar(name: String, modifier: Modifier = Modifier) {
    val initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Text(initials, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/**
 * The moment money has moved (design.md 4.8): hero gradient, a check that draws itself, the amount (shrinks to fit), and a
 * white details card whose first line is [date] from the server. The details scroll; [textAction] ("Save Sinta as a
 * recipient"), "Share receipt" ([onShare], only when money went to someone else) and Done stay at the bottom above the
 * system navigation. Back is the same as Done, never back to the PIN. [buzz]: a CONFIRM pulse, only when money moved.
 */
@Composable
fun SuccessScreen(
    headline: String,
    amount: String,
    subline: String,
    details: @Composable ColumnScope.() -> Unit,
    primaryLabel: String = "Done",
    onPrimary: () -> Unit,
    date: Instant? = null,
    textAction: (@Composable () -> Unit)? = null,
    onShare: (suspend () -> Unit)? = null,
    buzz: Boolean = true,
) {
    id.orvyn.app.ui.DarkTopBar()
    BackHandler { onPrimary() }
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        if (buzz) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        progress.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }
    val large = isLargeFont()
    val scroll = rememberScrollState()
    Column(
        Modifier.fillMaxSize().background(Brand.Hero).drawBehind {
            val w = 22.dp.toPx(); var x = 0f
            while (x < size.width) { drawRect(Color.White.copy(alpha = 0.05f), Offset(x, 0f), Size(w, size.height)); x += w * 2 }
        }.safeDrawingPadding().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val h = maxHeight
            Column(Modifier.fillMaxWidth().verticalScroll(scroll)) {
                Column(Modifier.fillMaxWidth().heightIn(min = h).padding(vertical = 16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.size(if (large) 64.dp else 88.dp)) {
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
                    // One announcement: "Sent, Rp500,000, to Sinta Maharani".
                    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(headline, style = MaterialTheme.typography.titleMedium, color = Brand.HeroTextMuted, textAlign = TextAlign.Center)
                        AmountText(amount, OrvynType.displayBalance, color = Color.White, textAlign = TextAlign.Center)
                        Text(subline, style = MaterialTheme.typography.bodyLarge, color = Color.White, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(24.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        date?.let { ReceiptLine("Date", formatDateTime(it)) }
                        details()
                    }
                }
            }
        }
        if (scroll.canScrollForward) HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        Column(Modifier.fillMaxWidth().snackAnchor().padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            textAction?.invoke()
            if (onShare != null) ShareReceiptButton(onShare)
            OrvynButtonFrame(primaryLabel, rememberOnce(onPrimary), Modifier, ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.Nerazzurro),
                enabled = true, loading = false, fill = true, minHeight = 52)
        }
    }
}

/** Outlined white on the hero (design.md 4.4). Draws the receipt and opens the share sheet; never saved to the gallery. */
@Composable
private fun ShareReceiptButton(onShare: suspend () -> Unit) {
    var making by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snacks = id.orvyn.app.ui.components.LocalSnacks.current
    OrvynButtonFrame("Share receipt", rememberOnce {
        if (making) return@rememberOnce
        making = true
        scope.launch {
            try { onShare() } catch (e: Exception) { snacks?.show("Couldn't make the receipt. Try again.", error = true) }
            making = false
        }
    }, Modifier, ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White), enabled = true, loading = making, fill = true, minHeight = 52,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.56f)))
}

/** Label + value line for receipts and review cards, read as one ("Date, 16 Oct 2026, 19:27 WIB"). Dark text: these sit on white even in dark mode. */
@Composable
fun ReceiptLine(label: String, value: String, onSurfaceWhite: Boolean = true) {
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (onSurfaceWhite) Color(0xFF4D5872) else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = if (onSurfaceWhite) Color(0xFF0B1020) else MaterialTheme.colorScheme.onSurface)
    }
}

/** A text action in white on the hero, e.g. "Save as a recipient". [loading] shows a spinner in its place. */
@Composable
fun WhiteTextButton(text: String, loading: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !loading, role = androidx.compose.ui.semantics.Role.Button, onClick = rememberOnce(onClick)),
        contentAlignment = Alignment.Center) {
        if (loading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White, textAlign = TextAlign.Center)
    }
}

/** A plain done screen with no money moved: Change PIN, Change password (design.md 7.1). */
@Composable
fun DoneScreen(title: String, body: String, onDone: () -> Unit) {
    BackHandler { onDone() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 20.dp)) {
        BackBar(null)
        FitArea(bottom = false) {
            Spacer(Modifier.height(0.dp))
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StateIcon(id.orvyn.app.R.drawable.ph_check_circle, id.orvyn.app.ui.theme.OrvynTheme.colors.successContainer, id.orvyn.app.ui.theme.OrvynTheme.colors.success)
                Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(body, style = MaterialTheme.typography.bodyLarge, color = id.orvyn.app.ui.theme.OrvynTheme.colors.textSecondary, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(0.dp))
        }
        OrvynButton("Done", onClick = onDone, modifier = Modifier.padding(vertical = 16.dp))
    }
}
