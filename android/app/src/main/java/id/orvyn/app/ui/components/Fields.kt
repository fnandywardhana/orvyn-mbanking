package id.orvyn.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.ui.AmountKey
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.QUICK_AMOUNTS
import id.orvyn.app.ui.pressAmountKey
import id.orvyn.app.ui.quickAmountLabel
import id.orvyn.app.ui.theme.OrvynTheme
import id.orvyn.app.ui.theme.OrvynType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Label above (never placeholder-only), helper or error below. Password fields get a show/hide toggle. [imeAction] and
 * [onImeAction] set the keyboard's action key ("Next", "Done"). Focusing the field scrolls it and its message into view.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    fieldModifier: Modifier = Modifier,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    FieldFrame(label, helper, error, modifier) { bring ->
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = error != null,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else visualTransformation,
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
            colors = fieldColors(),
            modifier = fieldModifier.fillMaxWidth().semantics { if (error != null) error(error) }
                .onFocusEvent { if (it.isFocused) bring() },
        )
    }
}

/** The same field on a [TextFieldValue], for input that must keep the cursor where it was when an edit is rejected (date of birth). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OrvynTextField(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    helper: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    fieldModifier: Modifier = Modifier,
) {
    FieldFrame(label, helper, error, modifier) { bring ->
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = error != null,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            colors = fieldColors(),
            modifier = fieldModifier.fillMaxWidth().semantics { if (error != null) error(error) }.onFocusEvent { if (it.isFocused) bring() },
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
)

/** Label, field, then the error (or helper) underneath; the whole block scrolls into view when focused or when the error appears. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FieldFrame(label: String, helper: String?, error: String?, modifier: Modifier, field: @Composable (bring: () -> Unit) -> Unit) {
    val bring = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(error) { if (error != null) runCatching { bring.bringIntoView() } }
    Column(modifier.fillMaxWidth().bringIntoViewRequester(bring), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        field { scope.launch { delay(250); runCatching { bring.bringIntoView() } } } // after the keyboard has opened
        when {
            error != null -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ph_warning_circle), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            helper != null -> Text(helper, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
        }
    }
}

/** "Pick one." under a group of choices that hasn't been answered (Q-D2 AC2). */
@Composable
fun GroupError(text: String) = Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(painterResource(R.drawable.ph_warning_circle), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

/**
 * Big formatted amount (shrinks to fit, design.md 4.5), the error or "Available Rp…", and Quick Amounts. Pair with
 * [AmountKeypad] in an [AmountScreen]. [shown] replaces the Rupiah text (e.g. "0.05 g"). While [loading] the "Available" line
 * is a skeleton; [loadError] replaces it with the message and a Try again (design.md 4.7).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun AmountDisplay(
    amount: Long,
    available: Long?,
    error: String?,
    onQuickPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    quickAmounts: List<Long> = QUICK_AMOUNTS,
    shown: String? = null,
    loading: Boolean = false,
    loadError: String? = null,
    retrying: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    // The amount (and an error under it) scrolls into view, even when a long recipient name sits above it at large font sizes.
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(error, loadError) { androidx.compose.runtime.withFrameNanos { }; runCatching { bring.bringIntoView() } }
    Column(modifier.fillMaxWidth().bringIntoViewRequester(bring), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AmountText(
            shown ?: Money.format(amount),
            style = OrvynType.displayBalance,
            color = if (amount == 0L) OrvynTheme.colors.textTertiary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        when {
            loadError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ph_warning_circle), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Text(loadError, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                if (onRetry != null) OrvynButton("Try again", onClick = onRetry, kind = ButtonKind.Secondary, loading = retrying, fill = false)
            }
            error != null -> Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            available != null -> Text(
                "Available ${Money.format(available)}",
                style = MaterialTheme.typography.bodyMedium,
                color = OrvynTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            loading -> Skeleton(Modifier.size(160.dp, 20.dp))
        }
        // Wraps to a second row at large font sizes instead of running off screen.
        if (quickAmounts.isNotEmpty()) FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (v in quickAmounts) Chip(quickAmountLabel(v), selected = amount == v, contentDescription = Money.format(v)) { onQuickPick(v) }
        }
    }
}

/**
 * Choice chip (design.md 4.9): unselected `surface` with a 1dp `outlineStrong` border, selected `primary` with a check, the
 * same in both themes. [role] RadioButton for pick-one, Checkbox for multi-pick. [contentDescription] replaces what TalkBack
 * reads, e.g. "Rp1,000,000" for a "1M" chip. [trailingIcon] is the caret of a filter chip that opens a sheet.
 */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    contentDescription: String? = null,
    role: Role = Role.RadioButton,
    trailingIcon: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val fg = if (selected) c.onPrimary else c.onSurface
    Row(
        Modifier
            .alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .background(if (selected) c.primary else c.surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, c.outline, CircleShape))
            .clickable(enabled = enabled, role = role, onClick = onClick)
            .semantics { this.selected = selected; if (contentDescription != null) this.contentDescription = contentDescription }
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .padding(start = if (selected) 12.dp else 16.dp, end = if (trailingIcon != null) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (selected) Icon(painterResource(R.drawable.ph_check), null, Modifier.size(16.dp), tint = fg)
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            textAlign = TextAlign.Center,
            modifier = if (contentDescription != null) Modifier.clearAndSetSemantics {} else Modifier,
        )
        if (trailingIcon != null) Icon(painterResource(trailingIcon), null, Modifier.size(16.dp), tint = fg)
    }
}

/** 64dp keys; 56dp on short screens at large font sizes. Digits stop growing at 1.5× (design.md 4.1). */
@Composable
private fun keyHeight(threshold: Float) =
    if (isShortScreen() && androidx.compose.ui.platform.LocalDensity.current.fontScale >= threshold) 56.dp else 64.dp

/** 3×4 keypad. [leftKey] is "000" or "." for amounts and biometrics for PINs. [enabled] = false dims it to 38% (PIN busy). */
@Composable
fun Keypad(onDigit: (Int) -> Unit, onDelete: () -> Unit, leftKey: (@Composable () -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true, shrinkFrom: Float = 1.5f) {
    val h = keyHeight(shrinkFrom)
    CapFontScale {
        Column(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in row) Key(Modifier.weight(1f), "$d", onClick = { onDigit(d) }, enabled = enabled, height = h) { Text("$d", style = MaterialTheme.typography.headlineMedium) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(h)) { leftKey?.invoke() }
                Key(Modifier.weight(1f), "0", onClick = { onDigit(0) }, enabled = enabled, height = h) { Text("0", style = MaterialTheme.typography.headlineMedium) }
                Key(Modifier.weight(1f), "Delete", onClick = onDelete, enabled = enabled, height = h) { Icon(painterResource(R.drawable.ph_backspace), null) }
            }
        }
    }
}

/** One keypad key. Never uses the 600ms tap guard, so fast PIN typing keeps every digit (Q-E4 AC5). */
@Composable
fun Key(modifier: Modifier, label: String, onClick: () -> Unit, enabled: Boolean = true, height: androidx.compose.ui.unit.Dp = 64.dp, content: @Composable () -> Unit) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() } }
}

/** Amount keypad wired to [pressAmountKey], with a "000" key. Keys shrink on short screens from font size 1.3 (design.md 4.7). */
@Composable
fun AmountKeypad(amount: Long, onAmountChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    Keypad(
        onDigit = { onAmountChange(pressAmountKey(amount, AmountKey.Digit(it))) },
        onDelete = { onAmountChange(pressAmountKey(amount, AmountKey.Delete)) },
        leftKey = {
            Key(Modifier.fillMaxWidth(), "Three zeros", onClick = { onAmountChange(pressAmountKey(amount, AmountKey.TripleZero)) }, height = keyHeight(1.3f)) {
                Text("000", style = MaterialTheme.typography.titleLarge)
            }
        },
        modifier = modifier,
        shrinkFrom = 1.3f,
    )
}

/** Gram keypad (Q-F6): "." instead of "000", at most two decimals; [text] is what's typed so far, like "0.05". */
@Composable
fun DecimalKeypad(text: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Keypad(
        onDigit = { onChange(gramsKey(text, ('0' + it))) },
        onDelete = { onChange(text.dropLast(1)) },
        leftKey = { Key(Modifier.fillMaxWidth(), "Decimal point", onClick = { onChange(gramsKey(text, '.')) }, height = keyHeight(1.3f)) { Text(".", style = MaterialTheme.typography.headlineMedium) } },
        modifier = modifier,
        shrinkFrom = 1.3f,
    )
}

/** What a gram key does: one point, at most two digits after it (a third is ignored), no leading zeros, at most 6 digits before. */
fun gramsKey(text: String, key: Char): String {
    if (key == '.') return if ('.' in text) text else (text.ifEmpty { "0" } + ".")
    val dot = text.indexOf('.')
    if (dot >= 0 && text.length - dot > 2) return text
    if (dot < 0 && text.length >= 6) return text
    return if (text == "0") "$key" else text + key
}

// ---- PIN entry (design.md 4.6)

/** What the six dots and the keypad share. One per PIN screen. */
@Stable
class PinEntry {
    var pin by mutableStateOf(""); internal set
    internal var rejecting by mutableStateOf(false)
    internal val shake = Animatable(0f)
}

/**
 * Six dots and the status slot under them. [busyText] (after the 6th digit, while the server checks): the dots stay full and
 * pulse, and the text is announced once. A bumped [errorCount] is a rejected PIN: the dots shake with a REJECT buzz, then
 * empty, and [errorText] shows. [infoText] is a neutral line (e.g. "Your account is unlocked. Enter your PIN.").
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinDots(entry: PinEntry, errorCount: Int, errorText: String?, busyText: String?, infoText: String? = null, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val reduced = rememberReducedMotion()
    LaunchedEffect(errorCount) {
        if (errorCount == 0) return@LaunchedEffect
        entry.rejecting = true
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
        if (!reduced) entry.shake.animateTo(0f, keyframes { durationMillis = 300; -16f at 50; 16f at 100; -10f at 150; 10f at 200; -4f at 250 })
        entry.rejecting = false
    }
    val pulse = if (busyText != null && !reduced) {
        val t = rememberInfiniteTransition(label = "busy")
        t.animateFloat(1f, 0.4f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), label = "pulse").value
    } else 1f
    val filled = if (busyText != null || entry.rejecting) 6 else entry.pin.length
    val bring = remember { BringIntoViewRequester() }
    val status = errorText ?: busyText ?: infoText
    LaunchedEffect(status) { if (status != null) runCatching { bring.bringIntoView() } }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier.offset { IntOffset(entry.shake.value.dp.roundToPx(), 0) }.alpha(pulse).semantics { contentDescription = "$filled of 6 digits entered" },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            repeat(6) { i ->
                val on = i < filled
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
        }
        // Reserved slot: a status never moves the layout, and long messages wrap instead of being cut (Q-A1 AC3).
        Box(Modifier.fillMaxWidth().heightIn(min = 20.dp).bringIntoViewRequester(bring), contentAlignment = Alignment.TopCenter) {
            when {
                errorText != null -> Row(
                    Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top,
                ) {
                    Icon(painterResource(R.drawable.ph_warning_circle), null, Modifier.padding(top = 2.dp).size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Text(errorText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                busyText != null || infoText != null -> Text(
                    (busyText ?: infoText)!!, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** The PIN keypad. Calls [onComplete] at the 6th digit and starts fresh; ignores keys while [busy]. */
@Composable
fun PinKeypad(entry: PinEntry, onComplete: (String) -> Unit, busy: Boolean, onBiometric: (() -> Unit)?, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var handing by remember { mutableStateOf(false) }
    Keypad(
        onDigit = {
            if (!busy && !handing && entry.pin.length < 6) {
                entry.pin += it
                // Let the sixth dot show for a moment, then hand the PIN over.
                if (entry.pin.length == 6) { handing = true; scope.launch { delay(100); val e = entry.pin; entry.pin = ""; handing = false; onComplete(e) } }
            }
        },
        onDelete = { if (!busy) entry.pin = entry.pin.dropLast(1) },
        leftKey = onBiometric?.let { bio ->
            { Key(Modifier.fillMaxWidth(), "Use fingerprint", onClick = bio, enabled = !busy, height = keyHeight(1.5f)) { Icon(painterResource(R.drawable.ph_fingerprint), null, tint = MaterialTheme.colorScheme.primary) } }
        },
        modifier = modifier,
        enabled = !busy,
    )
}

/**
 * Dots, status and keypad in one column, for a PIN inside a sheet (card details). Full screens use [PinScreen], which keeps
 * the keypad fixed at the bottom. Either way the window can't be captured while it's shown (Q-C4).
 */
@Composable
fun PinPad(
    onComplete: (String) -> Unit,
    errorCount: Int,
    errorText: String?,
    onBiometric: (() -> Unit)?,
    modifier: Modifier = Modifier,
    busyText: String? = null,
) {
    SecureWhileShown()
    val entry = remember { PinEntry() }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        PinDots(entry, errorCount, errorText, busyText)
        PinKeypad(entry, onComplete, busy = busyText != null, onBiometric = onBiometric)
    }
}
