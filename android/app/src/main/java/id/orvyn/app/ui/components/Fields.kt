package id.orvyn.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.ui.AmountKey
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.pressAmountKey
import id.orvyn.app.ui.theme.OrvynTheme
import id.orvyn.app.ui.theme.OrvynType

/** Label above (never placeholder-only), helper or error below. Password fields get a show/hide toggle. */
@Composable
fun OrvynTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    helper: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = error != null,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (password) {
                {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            painterResource(if (visible) R.drawable.ph_eye_slash else R.drawable.ph_eye),
                            contentDescription = if (visible) "Hide password" else "Show password",
                        )
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.fillMaxWidth().semantics { if (error != null) error(error) },
        )
        when {
            error != null -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ph_warning_circle), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            helper != null -> Text(helper, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
        }
    }
}

/** Big formatted amount, quick chips, available balance and an inline error. Pair with [AmountKeypad]. */
@Composable
fun AmountDisplay(amount: Long, available: Long?, error: String?, onQuickPick: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            Money.format(amount),
            style = OrvynType.displayBalance,
            color = if (amount == 0L) OrvynTheme.colors.textTertiary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (error != null) Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) else if (available != null) Text(
            "Available ${Money.format(available)}",
            style = MaterialTheme.typography.bodyMedium,
            color = OrvynTheme.colors.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (v in listOf(50_000L, 100_000L, 250_000L, 500_000L)) Chip("${v / 1000}k", selected = amount == v) { onQuickPick(v) }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) c.primary else c.primaryContainer)
            .clickable(onClick = onClick)
            .height(48.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) c.onPrimary else c.onPrimaryContainer) }
}

/** 3×4 keypad with 64dp keys. [leftKey] is "000" for amounts and biometrics for PINs. */
@Composable
fun Keypad(onDigit: (Int) -> Unit, onDelete: () -> Unit, leftKey: (@Composable () -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in row) Key(Modifier.weight(1f), "$d", onClick = { onDigit(d) }) { Text("$d", style = MaterialTheme.typography.headlineMedium) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).height(64.dp)) { leftKey?.invoke() }
            Key(Modifier.weight(1f), "0", onClick = { onDigit(0) }) { Text("0", style = MaterialTheme.typography.headlineMedium) }
            Key(Modifier.weight(1f), "Delete", onClick = onDelete) { Icon(painterResource(R.drawable.ph_backspace), null) }
        }
    }
}

@Composable
fun Key(modifier: Modifier, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() } }
}

/** Amount keypad wired to [pressAmountKey], with a "000" key. */
@Composable
fun AmountKeypad(amount: Long, onAmountChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    Keypad(
        onDigit = { onAmountChange(pressAmountKey(amount, AmountKey.Digit(it))) },
        onDelete = { onAmountChange(pressAmountKey(amount, AmountKey.Delete)) },
        leftKey = {
            Key(Modifier.fillMaxWidth(), "Three zeros", onClick = { onAmountChange(pressAmountKey(amount, AmountKey.TripleZero)) }) {
                Text("000", style = MaterialTheme.typography.titleLarge)
            }
        },
        modifier = modifier,
    )
}

/**
 * Six-dot PIN entry. Calls [onComplete] at 6 digits. Bump [errorCount] after a wrong PIN:
 * the dots shake, the phone gives a reject buzz, the entry clears and [errorText] is shown.
 */
@Composable
fun PinPad(
    onComplete: (String) -> Unit,
    errorCount: Int,
    errorText: String?,
    onBiometric: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var pin by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(errorCount) {
        if (errorCount == 0) return@LaunchedEffect
        pin = ""
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
        shake.animateTo(0f, keyframes { durationMillis = 300; -16f at 50; 16f at 100; -10f at 150; 10f at 200; -4f at 250 })
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(
            Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) }.semantics { contentDescription = "${pin.length} of 6 digits entered" },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            repeat(6) { i ->
                val filled = i < pin.length
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
        }
        Box(Modifier.height(20.dp)) {
            if (errorText != null) Text(
                errorText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        Keypad(
            onDigit = {
                if (pin.length < 6) {
                    pin += it
                    // Let the sixth dot show briefly, then hand the PIN over and start fresh for the next entry.
                    if (pin.length == 6) scope.launch { delay(150); val entered = pin; pin = ""; onComplete(entered) }
                }
            },
            onDelete = { pin = pin.dropLast(1) },
            leftKey = onBiometric?.let { bio ->
                {
                    Key(Modifier.fillMaxWidth(), "Use fingerprint", onClick = bio) {
                        Icon(painterResource(R.drawable.ph_fingerprint), null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            },
        )
    }
}
