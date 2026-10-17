package id.orvyn.app.ui.components

import android.app.Activity
import android.os.SystemClock
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.orvyn.app.R
import id.orvyn.app.data.ApiException
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// Shared patterns from docs/features/2026-10-16-quality-pass/design.md section 4.

// ---- Sizes (4.1)

/** "Font besar" in design.md: fontScale 1.5 or more. Layouts switch to their stacked, wrapping form. */
@Composable fun isLargeFont() = LocalDensity.current.fontScale >= 1.5f

/** "Layar pendek": a window under 700dp tall. Keypad keys shrink to 56dp there at large font sizes. */
@Composable fun isShortScreen() = LocalConfiguration.current.screenHeightDp < 700

/** Only keypad digits and bottom-nav labels stop growing at 1.5×; they sit in fixed-width cells (design.md 4.1). */
@Composable
fun CapFontScale(max: Float = 1.5f, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    if (d.fontScale <= max) content() else CompositionLocalProvider(LocalDensity provides Density(d.density, max), content = content)
}

// ---- Motion and the input guard (4.3)

val EnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
val ExitEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
const val ENTER_MS = 250
const val EXIT_MS = 175

fun forwardEnter() = slideInHorizontally(tween(ENTER_MS, easing = EnterEasing)) { it / 4 } + fadeIn(tween(ENTER_MS, easing = EnterEasing))
fun forwardExit() = slideOutHorizontally(tween(EXIT_MS, easing = ExitEasing)) { -it / 4 } + fadeOut(tween(EXIT_MS, easing = ExitEasing))
fun backEnter() = slideInHorizontally(tween(ENTER_MS, easing = EnterEasing)) { -it / 4 } + fadeIn(tween(ENTER_MS, easing = EnterEasing))
fun backExit() = slideOutHorizontally(tween(EXIT_MS, easing = ExitEasing)) { it / 4 } + fadeOut(tween(EXIT_MS, easing = ExitEasing))

/**
 * A screen or flow step that ignores taps until its enter transition ends (250ms, none with animations off). This is what
 * stops a double tap on "Send" from typing a digit on the PIN pad behind it (F3, Q-E4). Keypad keys themselves have no guard.
 */
@Composable
fun InputGuard(content: @Composable () -> Unit) {
    val reduced = rememberReducedMotion()
    var open by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { if (!open) { delay(ENTER_MS.toLong()); open = true } }
    Box(if (open) Modifier else Modifier.pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
    }) { content() }
}

/**
 * Steps of one flow (recipient, amount, review, PIN). Forward slides in from the right, back from the left, the success
 * step only fades in. [index] orders the steps; two states with the same index swap without animating.
 */
@Composable
fun <S> FlowSteps(step: S, index: (S) -> Int, success: (S) -> Boolean = { false }, content: @Composable (S) -> Unit) {
    val reduced = rememberReducedMotion()
    AnimatedContent(step, label = "step", contentKey = index, transitionSpec = {
        when {
            reduced -> EnterTransition.None togetherWith ExitTransition.None
            success(targetState) -> fadeIn(tween(ENTER_MS, easing = EnterEasing)) togetherWith fadeOut(tween(EXIT_MS))
            index(targetState) >= index(initialState) -> forwardEnter() togetherWith forwardExit()
            else -> backEnter() togetherWith backExit()
        }
    }) { s -> InputGuard { content(s) } }
}

/** Buttons, rows that open screens and chips that move a flow on fire once, then ignore taps for 600ms (design.md 4.3). */
@Composable
fun rememberOnce(onClick: () -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onClick)
    var last by remember { mutableLongStateOf(Long.MIN_VALUE / 2) }
    return remember {
        {
            val now = SystemClock.uptimeMillis()
            if (now - last >= ONCE_MS) { last = now; latest() }
        }
    }
}
const val ONCE_MS = 600L

// ---- Amounts that shrink to fit (4.5)

/**
 * One line at [style] size if it fits; otherwise smaller, down to [minSize] (scaled by the font size); and only if it still
 * doesn't fit, it wraps, after a thousands comma when [breakAfterCommas]. The size comes from [fitTo] (the final value),
 * so a count-up never resizes. TalkBack reads [text] (or [contentDescription]) without the invisible break characters.
 */
@Composable
fun AmountText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    fitTo: String = text,
    minSize: TextUnit = 24.sp,
    breakAfterCommas: Boolean = true,
    contentDescription: String? = null,
) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val maxWidth = constraints.maxWidth
        val fit = remember(fitTo, style, maxWidth, LocalDensity.current) {
            if (maxWidth == Constraints.Infinity) style.fontSize to false
            else {
                fun width(size: Float) = measurer.measure(AnnotatedString(fitTo), style.copy(fontSize = size.sp), maxLines = 1, softWrap = false).size.width
                var size = style.fontSize.value
                while (size > minSize.value && width(size) > maxWidth) size -= 1f
                size.coerceAtLeast(minSize.value).sp to (width(size.coerceAtLeast(minSize.value)) > maxWidth)
            }
        }
        val (size, wraps) = fit
        val ratio = if (style.fontSize.value > 0 && style.lineHeight.isSp) style.lineHeight.value / style.fontSize.value else 1.2f
        val shown = if (wraps && breakAfterCommas) text.replace(",", ",​") else text
        val said = contentDescription ?: if (shown != text) text else null
        Text(
            shown, style = style.copy(fontSize = size, lineHeight = (size.value * ratio).sp), color = color, textAlign = textAlign,
            // A set alignment needs the full width to act on, or a full-width amount sits left (Q-01-F10).
            modifier = (if (textAlign != null) Modifier.fillMaxWidth() else Modifier).then(if (said != null) Modifier.semantics { this.contentDescription = said } else Modifier),
        )
    }
}

// ---- Messages (4.12, 4.13)

/** For a failure that isn't one field's: right above the screen's or sheet's primary button, announced once. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ErrorBox(message: String, modifier: Modifier = Modifier) {
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(message) { runCatching { bring.bringIntoView() } }
    Row(
        modifier.fillMaxWidth().bringIntoViewRequester(bring).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.errorContainer)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(R.drawable.ph_warning_circle), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Offline (wifi icon, no button) or "couldn't refresh" (warning icon, Try again) over cached data: home and account detail. */
@Composable
fun StatusBanner(offline: Boolean, line1: String, line2: String?, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val x = OrvynTheme.colors
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(x.warningContainer).padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(painterResource(if (offline) R.drawable.ph_wifi_slash else R.drawable.ph_warning_circle), null, Modifier.size(20.dp), tint = x.warning)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(line1, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                line2?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = x.textSecondary) }
            }
        }
        if (onRetry != null) OrvynTextButton("Try again", onClick = onRetry, modifier = Modifier.align(Alignment.End))
    }
}

// ---- Choices (4.9, 4.15)

/** Exactly two (or a few) mutually exclusive options: "Send now"/"Schedule", "Buy"/"Sell", "Renew"/"Pay out", "Rupiah"/"Grams". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, busy: Int? = null, enabled: Boolean = true) {
    val c = MaterialTheme.colorScheme
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected, onClick = { onSelect(i) }, enabled = enabled && busy == null,
                shape = SegmentedButtonDefaults.itemShape(i, options.size, CircleShape),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = c.primary, activeContentColor = c.onPrimary, activeBorderColor = c.primary,
                    inactiveContainerColor = c.surface, inactiveContentColor = c.onSurface, inactiveBorderColor = c.outline,
                    disabledActiveContainerColor = c.primary, disabledActiveContentColor = c.onPrimary, disabledActiveBorderColor = c.primary,
                    disabledInactiveContainerColor = c.surface, disabledInactiveContentColor = c.onSurface, disabledInactiveBorderColor = c.outline,
                ),
                icon = {
                    when {
                        busy == i -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = if (i == selected) c.onPrimary else c.primary)
                        i == selected -> Icon(painterResource(R.drawable.ph_check), null, Modifier.size(16.dp))
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
        }
    }
}

/**
 * The whole row is one switch (Q-E5): TalkBack reads "Freeze card, ..., switch, on", one tap anywhere toggles once. While
 * [saving], the switch already shows the new position, a spinner sits next to it and the row is disabled (design.md 4.15).
 */
@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, saving: Boolean = false, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = enabled && !saving, role = Role.Switch, onValueChange = onChange)
            .semantics { if (saving) stateDescription = "${if (checked) "On" else "Off"}, Saving" }
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary) }
        }
        if (saving) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        Switch(checked, onCheckedChange = null, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.clearAndSetSemantics {})
    }
}

// ---- Loading (4.10)

const val LOAD_FAILED = "Couldn't load this. Check your connection and try again."

/** One screen's data: the value, or why it isn't there. [slow] turns true after 300ms so a fast load never flashes a skeleton. */
@Stable
class Load<T> internal constructor(private val scope: CoroutineScope) {
    var value by mutableStateOf<T?>(null); internal set
    var error by mutableStateOf<String?>(null); internal set
    var busy by mutableStateOf(false); private set
    var slow by mutableStateOf(false); private set
    internal var fetch: suspend () -> T = { error("no fetch") }

    fun reload() { scope.launch { run() } }

    internal suspend fun run() {
        busy = true
        val timer = scope.launch { delay(300); slow = true }
        try {
            // ponytail: the blocking HTTP call keeps running in the background until its own timeout; the screen moves on at 15s.
            val call = CoroutineScope(Dispatchers.IO + SupervisorJob()).async { fetch() }
            value = withTimeout(15_000) { call.await() }
            error = null
        } catch (e: ApiException) {
            error = e.message
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            error = LOAD_FAILED
        } finally {
            timer.cancel(); slow = false; busy = false
        }
    }
}

/** Loads on first show and whenever a key changes; keeps the last value while reloading. */
@Composable
fun <T> rememberLoad(vararg keys: Any?, fetch: suspend () -> T): Load<T> {
    val scope = rememberCoroutineScope()
    val load = remember { Load<T>(scope) }
    val latest by rememberUpdatedState(fetch)
    load.fetch = { latest() }
    LaunchedEffect(*keys) { load.run() }
    return load
}

/** Skeleton (after 300ms), content, or the error state with Try again. The skeleton is announced once as "Loading". */
@Composable
fun <T> LoadContent(load: Load<T>, errorTitle: String, skeleton: @Composable () -> Unit, content: @Composable (T) -> Unit) {
    val v = load.value
    when {
        v != null -> content(v)
        load.error != null -> LoadError(errorTitle, load.error!!, retrying = load.busy, onRetry = load::reload)
        load.slow -> Box(Modifier.semantics(mergeDescendants = true) { contentDescription = "Loading" }) { skeleton() }
    }
}

/** The error state every loading screen has: what failed, why, and a Try again that reloads in place. */
@Composable
fun LoadError(title: String, message: String, retrying: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) =
    MessageState(R.drawable.ph_warning_circle, title, message, modifier, error = true) {
        OrvynButton("Try again", onClick = onRetry, kind = ButtonKind.Secondary, loading = retrying, fill = false)
    }

/** The same error as one row, for a part of a screen (a card, the end of a list). */
@Composable
fun InlineLoadError(message: String, retrying: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.ph_warning_circle), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        if (retrying) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else OrvynTextButton("Try again", onClick = onRetry)
    }
}

/** What a failed action says: the server's own words, or the offline line. */
fun Throwable.say() = (this as? ApiException)?.message ?: OFFLINE
const val OFFLINE = "You're offline. Check your connection and try again."

// ---- Snackbars (4.11)

class OrvynSnack(
    override val message: String,
    val error: Boolean,
    override val actionLabel: String?,
    override val duration: SnackbarDuration,
    override val withDismissAction: Boolean,
) : SnackbarVisuals

/** One snackbar at a time for the whole app. Errors start with a warning icon; with an action it stays 10 seconds, or until dismissed under TalkBack. */
class Snacks(val host: SnackbarHostState, private val scope: CoroutineScope, private val talkBack: () -> Boolean) {
    /** Window top of each sticky action area on screen ([snackAnchor]); the snackbar sits just above the highest one. */
    val anchors = androidx.compose.runtime.mutableStateMapOf<Any, Float>()
    fun show(message: String, error: Boolean = false, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val pinned = action != null && talkBack()
            val r = host.showSnackbar(OrvynSnack(message, error, action,
                if (action == null) SnackbarDuration.Short else if (pinned) SnackbarDuration.Indefinite else SnackbarDuration.Long, pinned))
            if (r == SnackbarResult.ActionPerformed) onAction()
        }
    }
}

val LocalSnacks = staticCompositionLocalOf<Snacks?> { null }

@Composable
fun rememberSnacks(): Snacks {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    return remember { Snacks(SnackbarHostState(), scope) { context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true } }
}

/** Shows a message app-wide: `snack("Card frozen")`, `snack(e.say(), error = true)`. */
@Composable
fun rememberSay(): (String, Boolean) -> Unit {
    val s = LocalSnacks.current
    return remember(s) { { m, err -> s?.show(m, err) } }
}

/**
 * Marks a sticky action area (keypad, bottom buttons, bottom nav) so the snackbar never covers it (design.md 4.11).
 * Screens without a marked area keep the default spot, 76dp above the bottom, clear of one sticky button.
 */
@Composable
fun Modifier.snackAnchor(): Modifier {
    val s = LocalSnacks.current ?: return this
    val key = remember { Any() }
    DisposableEffect(s) { onDispose { s.anchors.remove(key) } }
    return onGloballyPositioned { s.anchors[key] = it.boundsInWindow().top }
}

/** Fills the window; the snackbar sits at the bottom, just above the highest [snackAnchor]. */
@Composable
fun OrvynSnackbarHost(snacks: Snacks) {
    var bottom by remember { mutableStateOf(0f) }
    val top = snacks.anchors.values.minOrNull()
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().onGloballyPositioned { bottom = it.boundsInWindow().bottom }) {
        val place = if (top == null) Modifier.navigationBarsPadding().padding(bottom = 76.dp) else Modifier.padding(bottom = with(density) { (bottom - top).coerceAtLeast(0f).toDp() })
        SnackbarBox(snacks, Modifier.align(Alignment.BottomCenter).then(place))
    }
}

@Composable
private fun SnackbarBox(snacks: Snacks, modifier: Modifier) {
    SnackbarHost(snacks.host, modifier) { data ->
        val v = data.visuals as? OrvynSnack
        Snackbar(
            modifier = Modifier.padding(12.dp), shape = RoundedCornerShape(14.dp),
            containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            action = data.visuals.actionLabel?.let { label -> { TextButton(onClick = { data.performAction() }) { Text(label, color = MaterialTheme.colorScheme.inversePrimary) } } },
            dismissAction = if (data.visuals.withDismissAction) ({
                androidx.compose.material3.IconButton(onClick = { data.dismiss() }) { Icon(painterResource(R.drawable.ph_x), contentDescription = "Dismiss") }
            }) else null,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (v?.error == true) Icon(painterResource(R.drawable.ph_warning_circle), null, Modifier.size(20.dp))
                Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ---- Secure window (4.6, Q-C4)

/** FLAG_SECURE while any holder is on screen (a PIN pad, card details); counted, so one leaving doesn't unlock the other. */
object SecureWindow {
    private var holders = 0
    fun acquire(w: Window) { if (holders++ == 0) w.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    fun release(w: Window) { if (--holders <= 0) { holders = 0; w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) } }
}

@Composable
fun SecureWhileShown() {
    val window = (LocalContext.current as? Activity)?.window ?: return
    DisposableEffect(window) { SecureWindow.acquire(window); onDispose { SecureWindow.release(window) } }
}

/** An icon in a 72dp circle: the head of empty, error, locked and done states. */
@Composable
fun StateIcon(@DrawableRes icon: Int, container: Color, tint: Color) = Box(
    Modifier.size(72.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center,
) { Icon(painterResource(icon), null, Modifier.size(36.dp), tint = tint) }
