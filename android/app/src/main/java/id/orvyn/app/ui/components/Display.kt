package id.orvyn.app.ui.components

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.theme.Brand
import id.orvyn.app.ui.theme.OrvynTheme

/** True when the user turned animations off in Android settings. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Surface card: 14dp radius with a hairline border (no shadow in dark mode, per design system). */
@Composable
fun OrvynCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() } }
}

/** 40dp tinted square holding a 24dp icon; the leading element of list rows and quick actions. Radius 10 (Q-E13). */
@Composable
fun IconTile(@DrawableRes icon: Int, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
}

/** Money with sign: incoming is green with "+", outgoing is plain text with "−" (spending is not an error). */
@Composable
fun MoneyText(rupiah: Long, modifier: Modifier = Modifier, signed: Boolean = true, struck: Boolean = false) {
    Text(
        if (signed) Money.signed(rupiah) else Money.format(rupiah),
        style = MaterialTheme.typography.titleMedium.copy(textDecoration = if (struck) TextDecoration.LineThrough else null),
        color = when {
            struck -> OrvynTheme.colors.textSecondary
            signed && rupiah > 0 -> OrvynTheme.colors.success
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier.semantics { if (signed) contentDescription = (if (rupiah < 0) "minus " else "plus ") + Money.format(rupiah) },
    )
}

/**
 * 64dp or taller: icon tile, title and subtitle, trailing content. With [stack] (amounts), the trailing part moves to its own
 * line under the subtitle at large font sizes, so names keep whole words per line (Q-A6); below that it stays on the right,
 * at most 45% of the row. One TalkBack node per row.
 */
@Composable
fun ListRow(
    title: String,
    subtitle: String?,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    stack: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    val large = isLargeFont()
    val frame = modifier
        .fillMaxWidth()
        .heightIn(min = 64.dp)
        .then(if (onClick != null) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = rememberOnce(onClick)) else Modifier.semantics(mergeDescendants = true) {})
        .padding(horizontal = 20.dp, vertical = 12.dp)
    val texts: @Composable () -> Unit = {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
    }
    if (stack && large) Row(frame, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Column { texts() }; trailing() }
    } else androidx.compose.foundation.layout.BoxWithConstraints(frame) {
        val max = maxWidth * 0.45f
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(icon)
            Column(Modifier.weight(1f)) { texts() }
            Box(Modifier.widthIn(max = max)) { trailing() }
        }
    }
}

enum class Status(val label: String, @DrawableRes val icon: Int) {
    Success("Successful", R.drawable.ph_check_circle),
    Pending("Processing", R.drawable.ph_clock_counter_clockwise),
    Failed("Failed", R.drawable.ph_x_circle),
    Reversed("Reversed", R.drawable.ph_arrows_left_right),
    // Money requests (design.md 4.17)
    Waiting("Waiting", R.drawable.ph_clock),
    ToPay("To pay", R.drawable.ph_hand_coins),
    Paid("Paid", R.drawable.ph_check_circle),
    Declined("Declined", R.drawable.ph_x_circle),
    Cancelled("Cancelled", R.drawable.ph_prohibit),
    Expired("Expired", R.drawable.ph_clock_counter_clockwise),
}

/** Status pill with icon and text, never color alone. */
@Composable
fun StatusChip(status: Status, modifier: Modifier = Modifier) {
    val x = OrvynTheme.colors
    val (fg, bg) = when (status) {
        Status.Success, Status.Paid -> x.success to x.successContainer
        Status.Pending, Status.ToPay -> x.warning to x.warningContainer
        Status.Failed -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
        else -> x.textSecondary to MaterialTheme.colorScheme.surfaceVariant
    }
    Pill(status.label, fg, bg, modifier, status.icon)
}

enum class Tier { Everyday, Prestige, Private }

@Composable
fun TierBadge(tier: Tier, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val (fg, bg) = when (tier) {
        Tier.Everyday -> c.onPrimaryContainer to c.primaryContainer
        Tier.Prestige -> Brand.Nero to Brand.Giallo
        Tier.Private -> Brand.Giallo to Brand.Nero
    }
    // Private is Nero on Nero in dark mode, so it keeps its pill shape with a Giallo hairline.
    Pill(tier.name.uppercase(), fg, bg, modifier, outline = if (tier == Tier.Private) Brand.Giallo else null)
}

@Composable
private fun Pill(text: String, fg: Color, bg: Color, modifier: Modifier, @DrawableRes icon: Int? = null, outline: Color? = null) {
    Row(
        modifier.clip(CircleShape).background(bg)
            .then(if (outline != null) Modifier.border(1.dp, outline, CircleShape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(painterResource(icon), null, Modifier.size(14.dp), tint = fg)
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

/**
 * Placeholder block in the final layout's shape (Q-E12): `surfaceVariant`, a 1.2s pulse (still with animations off), shown
 * only after 300ms of waiting. Blocks have no semantics; the screen says "Loading" once (see [LoadContent]).
 */
@Composable
fun Skeleton(modifier: Modifier, color: Color = MaterialTheme.colorScheme.surfaceVariant, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)) {
    val reduced = rememberReducedMotion()
    var shown by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(300); shown = true }
    val alpha = if (reduced) 1f else {
        val t = rememberInfiniteTransition(label = "skeleton")
        val a by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), label = "a")
        a
    }
    Box(
        modifier
            .graphicsLayer { this.alpha = if (shown) alpha else 0f }
            .clip(shape)
            .background(color)
            .clearAndSetSemantics {},
    )
}

/**
 * Empty or error state: icon in a tinted circle, one-line title, one sentence, one action. [error] uses the error colours
 * and is announced once (design.md 4.10).
 */
@Composable
fun MessageState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val c = MaterialTheme.colorScheme
        StateIcon(icon, if (error) c.errorContainer else c.primaryContainer, if (error) c.error else c.onPrimaryContainer)
        Column(
            Modifier.semantics(mergeDescendants = true) { if (error) liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = c.onSurface)
            Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = OrvynTheme.colors.textSecondary)
        }
        if (action != null) Box(Modifier.padding(top = 8.dp)) { action() }
    }
}
