package id.orvyn.app.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.orvyn.app.R
import id.orvyn.app.ui.theme.OrvynTheme

/** The 56dp top bar with an optional back arrow, shared by flow screens. */
@Composable
fun BackBar(onBack: (() -> Unit)?, enabled: Boolean = true) {
    Box(Modifier.height(56.dp), contentAlignment = Alignment.CenterStart) {
        if (onBack != null) IconButton(onClick = rememberOnce(onBack), enabled = enabled) {
            Icon(painterResource(R.drawable.ph_arrow_left), contentDescription = "Back")
        }
    }
}

/**
 * A scrolling area that fills the space it gets and lines its content up at the bottom ([bottom]) or centre when it fits,
 * so the part above a fixed keypad never pushes the keypad off screen (design.md 4.6, 4.7).
 */
@Composable
fun ColumnScope.FitArea(bottom: Boolean, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val h = maxHeight
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().heightIn(min = h), verticalArrangement = if (bottom) Arrangement.Bottom else Arrangement.SpaceBetween, content = content)
        }
    }
}

/**
 * Every place a PIN is typed or chosen (design.md 4.6): the title, explanation, dots and status scroll and line up at the
 * bottom; the keypad stays fixed at the bottom. At font size 1.5 and up the title is one line (shrinking to 18sp, wrapping
 * only if a long name still doesn't fit) and the explanation is read by TalkBack only. [busyText] is the "Checking your
 * PIN…" state: keypad, back arrow and system Back are off until the server answers. The window can't be captured.
 * [footer] (Unlock: Forgot PIN, Not you, the demo label) sits under the keypad, or in the scrolling part at large fonts.
 */
@Composable
fun PinScreen(
    title: String,
    body: String?,
    onBack: (() -> Unit)?,
    onComplete: (String) -> Unit,
    errorCount: Int,
    errorText: String?,
    busyText: String? = null,
    infoText: String? = null,
    onBiometric: (() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    SecureWhileShown()
    BackHandler(enabled = busyText != null) {} // nothing goes back while money or a PIN is being processed
    val large = isLargeFont()
    val entry = remember { PinEntry() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 20.dp)) {
        BackBar(onBack, enabled = busyText == null)
        FitArea(bottom = true) {
            if (large) Box(Modifier.clearAndSetSemantics { heading(); contentDescription = listOfNotNull(title, body).joinToString(". ") }) {
                AmountText(title, MaterialTheme.typography.titleLarge, minSize = 18.sp, breakAfterCommas = false, color = MaterialTheme.colorScheme.onBackground)
            } else {
                Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                if (body != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(body, style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary)
                }
            }
            Spacer(Modifier.height(24.dp))
            PinDots(entry, errorCount, errorText, busyText, infoText)
            if (large && footer != null) Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) { footer() }
            Spacer(Modifier.height(16.dp))
        }
        PinKeypad(entry, onComplete, busy = busyText != null, onBiometric = onBiometric)
        if (!large && footer != null) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { footer() }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Every screen with an amount keypad (design.md 4.7): title, context, amount and Quick Amounts scroll; the keypad and the
 * primary button stay at the bottom. No text field on this screen (PO-19): notes go on the next step.
 */
@Composable
fun AmountScreen(
    title: String,
    body: String?,
    onBack: (() -> Unit)?,
    keypad: @Composable () -> Unit,
    button: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 20.dp)) {
        BackBar(onBack)
        FitArea(bottom = false) {
            Column {
                Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                if (body != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(body, style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary)
                }
            }
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            Spacer(Modifier.height(0.dp))
        }
        keypad()
        Spacer(Modifier.height(12.dp))
        button()
        Spacer(Modifier.height(16.dp))
    }
}
