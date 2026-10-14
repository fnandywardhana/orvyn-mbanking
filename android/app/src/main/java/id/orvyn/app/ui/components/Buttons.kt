package id.orvyn.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring

val ButtonShape = RoundedCornerShape(14.dp)

enum class ButtonKind { Primary, Secondary, Destructive }

/**
 * Full-width 52dp action button. One [ButtonKind.Primary] per screen.
 * While [loading], a spinner replaces the label at the same size and taps are ignored.
 */
@Composable
fun OrvynButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    val colors: ButtonColors = when (kind) {
        ButtonKind.Primary -> ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary)
        ButtonKind.Secondary -> ButtonDefaults.buttonColors(containerColor = c.primaryContainer, contentColor = c.onPrimaryContainer)
        ButtonKind.Destructive -> ButtonDefaults.buttonColors(containerColor = c.error, contentColor = c.onError)
    }.copy(disabledContainerColor = c.onSurface.copy(alpha = 0.12f), disabledContentColor = c.onSurface.copy(alpha = 0.38f))
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 800f), label = "press")

    Button(
        onClick = { if (!loading) onClick() },
        enabled = enabled,
        shape = ButtonShape,
        colors = colors,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().height(52.dp).scale(scale),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.alpha(if (loading) 0f else 1f))
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = LocalContentColor.current, strokeWidth = 2.5.dp)
        }
    }
}

/** Low-emphasis action, e.g. "Forgot PIN?". */
@Composable
fun OrvynTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
}
