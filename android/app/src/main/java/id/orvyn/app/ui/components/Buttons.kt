package id.orvyn.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.orvyn.app.R

val ButtonShape = RoundedCornerShape(14.dp)

/**
 * Primary (filled), Secondary (tonal), Destructive (filled `error`, only for the confirm button of a dialog) and
 * DestructiveText (`error` label with a trash icon, the entry point that opens that dialog). design.md 4.4.
 */
enum class ButtonKind { Primary, Secondary, Destructive, DestructiveText }

/**
 * At least 52dp tall and grows with its label, which wraps instead of being cut at large font sizes (Q-A6). Fires once,
 * then ignores taps for 600ms (Q-E4). While [loading], a spinner replaces the label at the same size, taps are ignored,
 * and TalkBack hears the label plus "Busy". [fill] = false sizes it to its content (at least 120dp), e.g. "Try again".
 */
@Composable
fun OrvynButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    fill: Boolean = true,
) {
    val once = rememberOnce { if (!loading) onClick() }
    if (kind == ButtonKind.DestructiveText) {
        TextButton(onClick = once, enabled = enabled && !loading, modifier = modifier.heightIn(min = 48.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.error)
                else Icon(painterResource(R.drawable.ph_trash), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            }
        }
        return
    }
    val c = MaterialTheme.colorScheme
    val colors: ButtonColors = when (kind) {
        ButtonKind.Secondary -> ButtonDefaults.buttonColors(containerColor = c.primaryContainer, contentColor = c.onPrimaryContainer)
        ButtonKind.Destructive -> ButtonDefaults.buttonColors(containerColor = c.error, contentColor = c.onError)
        else -> ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary)
    }.copy(disabledContainerColor = c.onSurface.copy(alpha = 0.12f), disabledContentColor = c.onSurface.copy(alpha = 0.38f))
    OrvynButtonFrame(text, once, modifier, colors, enabled, loading, fill, if (fill) 52 else 48)
}

/** Shared by the themed buttons and the white ones on the success hero. */
@Composable
internal fun OrvynButtonFrame(
    text: String, onClick: () -> Unit, modifier: Modifier, colors: ButtonColors, enabled: Boolean, loading: Boolean, fill: Boolean, minHeight: Int,
    border: androidx.compose.foundation.BorderStroke? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 800f), label = "press")
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = colors,
        border = border,
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = modifier.then(if (fill) Modifier.fillMaxWidth() else Modifier.widthIn(min = 120.dp)).heightIn(min = minHeight.dp).scale(scale)
            .semantics { if (loading) stateDescription = "Busy" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.alpha(if (loading) 0f else 1f))
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = LocalContentColor.current, strokeWidth = 2.5.dp)
        }
    }
}

/**
 * Buttons side by side, each given [content]'s modifier. At large font sizes (1.5× and up) they stack full width instead,
 * so a label wraps between words and never breaks inside one (MASTER Button, 0.9.1 QA H91-F4).
 */
@Composable
fun ButtonRow(modifier: Modifier = Modifier, spacing: androidx.compose.ui.unit.Dp = 10.dp, content: @Composable (Modifier) -> Unit) {
    if (isLargeFont()) androidx.compose.foundation.layout.Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) { content(Modifier.fillMaxWidth()) }
    else androidx.compose.foundation.layout.Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) { content(Modifier.weight(1f)) }
}

/** Low-emphasis action, e.g. "Forgot PIN?". Fires once per 600ms like every button. */
@Composable
fun OrvynTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(
        onClick = rememberOnce(onClick),
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), textAlign = TextAlign.Center) }
}
