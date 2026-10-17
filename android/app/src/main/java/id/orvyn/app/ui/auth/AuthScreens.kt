package id.orvyn.app.ui.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.DeviceStore
import id.orvyn.app.ui.components.BackBar
import id.orvyn.app.ui.components.ButtonKind
import id.orvyn.app.ui.components.FitArea
import id.orvyn.app.ui.components.FlowSteps
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OFFLINE
import id.orvyn.app.ui.components.OrvynBottomSheet
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.OrvynTextButton
import id.orvyn.app.ui.components.OrvynTextField
import id.orvyn.app.ui.components.PinScreen
import id.orvyn.app.ui.components.StateIcon
import id.orvyn.app.ui.isAcceptablePin
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.launch

/** Plain screen scaffold for flows: optional back arrow, title, one-line explanation. The content below decides what scrolls. */
@Composable
fun FlowScreen(title: String, body: String?, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()
            .padding(horizontal = 20.dp),
    ) {
        BackBar(onBack)
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary)
        }
        Spacer(Modifier.height(24.dp))
        content()
    }
}

const val WEAK_PIN = "Choose 6 digits that aren't all the same or in a row."
const val PIN_MISMATCH = "Those PINs didn't match. Start again."

/**
 * Story S1.5 and Q-D1: phone number and password, once per phone. Everything scrolls above the keyboard, so the password
 * field and its message are always in sight; "Next" moves to the password and "Done" signs in. [forgotPin] (Q-D6): signing
 * in to choose a new PIN on this same phone, which stays the Registered Device.
 */
@Composable
fun SignInScreen(store: DeviceStore, onBack: () -> Unit, forgotPin: Boolean = false, timedOut: Boolean = false, onSignedIn: (needsPin: Boolean) -> Unit) {
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf(if (timedOut) "That took too long. Sign in with your password again." else null) }
    var passkeyError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val passwordFocus = remember { FocusRequester() }
    val canSubmit = phone.isNotBlank() && password.isNotBlank() && !busy
    fun signIn() {
        if (!canSubmit) return
        busy = true
        scope.launch {
            try {
                val r = Api.signInWithPassword(phone, password, if (forgotPin) store.deviceToken else null)
                if (!forgotPin || r.deviceToken != store.deviceToken) store.forgetDevice()
                store.deviceToken = r.deviceToken
                onSignedIn(r.needsPin)
            } catch (e: ApiException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding().padding(horizontal = 20.dp)) {
        BackBar(onBack)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Sign in", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(if (forgotPin) "Sign in with your password, then choose a new PIN." else "Use your phone number and ORVYN password.",
                style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            OrvynTextField("Phone number", phone, { phone = it; error = null }, keyboardType = KeyboardType.Phone, helper = "For example 0812 3456 7890",
                imeAction = ImeAction.Next, onImeAction = { passwordFocus.requestFocus() })
            OrvynTextField("Password", password, { password = it; error = null }, password = true, error = error,
                imeAction = ImeAction.Done, onImeAction = { signIn() }, fieldModifier = Modifier.focusRequester(passwordFocus))
            OrvynButton("Sign in", enabled = phone.isNotBlank() && password.isNotBlank(), loading = busy, onClick = { signIn() })
            // Story S3.12: a passkey made earlier replaces the phone number and password.
            val activity = LocalContext.current as android.app.Activity
            if (!forgotPin && id.orvyn.app.data.AppConfig.on("passkeys")) {
                OrvynButton("Sign in with a passkey", kind = ButtonKind.Secondary, enabled = !busy, onClick = {
                    busy = true; passkeyError = null
                    scope.launch {
                        try {
                            val r = id.orvyn.app.ui.profile.signInWithPasskey(activity)
                            store.forgetDevice()
                            store.deviceToken = r.deviceToken
                            onSignedIn(r.needsPin)
                        } catch (_: id.orvyn.app.ui.profile.PasskeyCancelled) {
                        } catch (e: ApiException) { passkeyError = e.message } finally { busy = false }
                    }
                })
                passkeyError?.let { id.orvyn.app.ui.components.GroupError(it) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Choosing a PIN after the first sign-in, or after Forgot PIN ([forgot], Q-D6). A weak PIN is refused right after the 6th
 * digit of step 1; a second PIN that doesn't match goes back to step 1 with the message (Q-D3).
 */
@Composable
fun CreatePinScreen(forgot: Boolean = false, onBack: (() -> Unit)? = null, onTimedOut: () -> Unit = {}, onDone: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    FlowSteps(first != null, index = { if (it) 1 else 0 }) { again ->
        if (!again) PinScreen(
            if (forgot) "Choose a new PIN" else "Create your PIN",
            if (forgot) "You'll use it to open ORVYN and approve payments. Avoid 123456 or your birthday."
            else "You'll use these 6 digits to open ORVYN and approve payments. Avoid 123456 or your birthday.",
            onBack, onComplete = { pin ->
                if (!isAcceptablePin(pin)) { error = WEAK_PIN; errors++ } else { error = null; first = pin }
            }, errorCount = errors, errorText = error,
        ) else PinScreen(
            "Enter it again", "Just to be sure you remember it.", onBack, errorCount = 0,
            errorText = if (busy) null else error.takeIf { first != null }, busyText = if (busy) "Saving your PIN…" else null,
            onComplete = { pin ->
                if (pin != first) { first = null; error = PIN_MISMATCH; errors++; return@PinScreen }
                busy = true; error = null
                scope.launch {
                    try { Api.setPin(pin); onDone() }
                    catch (e: ApiException) {
                        if (e.code == "PIN_TIMEOUT") onTimedOut() else error = e.message
                    } finally { busy = false }
                }
            },
        )
    }
}

fun canUseBiometrics(activity: FragmentActivity) =
    BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

fun promptBiometric(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
    BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
    }).authenticate(
        BiometricPrompt.PromptInfo.Builder().setTitle(title).setNegativeButtonText("Use PIN").setAllowedAuthenticators(BIOMETRIC_STRONG).build(),
    )
}

/** After the PIN: offer fingerprint sign-in once (story S1.5). Skipped on phones without a strong biometric. */
@Composable
fun BiometricOfferScreen(store: DeviceStore, onDone: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    LaunchedEffect(Unit) { if (!canUseBiometrics(activity)) onDone() }
    FlowScreen("Sign in with your fingerprint?", "Open ORVYN with a touch instead of typing your PIN. You can change this later in Profile.", null) {
        FitArea(bottom = false) {
            Spacer(Modifier.height(0.dp))
            MessageState(R.drawable.ph_fingerprint, "Your fingerprint stays on this phone", "Android checks it. ORVYN only learns whether it matched.")
            Spacer(Modifier.height(0.dp))
        }
        OrvynButton("Turn on", onClick = {
            promptBiometric(activity, "Confirm your fingerprint") { store.biometricEnabled = true; onDone() }
        })
        OrvynTextButton("Not now", onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
    }
}

/**
 * Story S1.6: opening ORVYN on the Registered Device (design.md 4.6, 6.9, 8.6). The locked state has its own Try again,
 * which asks the server without a PIN (Q-B9); coming back to ORVYN checks too. "Forgot PIN?" signs in with the password
 * and keeps this phone.
 */
@Composable
fun UnlockScreen(store: DeviceStore, onUnlocked: () -> Unit, onUsePassword: () -> Unit, onForgotPin: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    var error by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var locked by remember { mutableStateOf(Api.lockedNow) }
    var lockMessage by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var forgotSheet by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(store.firstName) }
    val scope = rememberCoroutineScope()
    val token = store.deviceToken
    val biometric = store.biometricEnabled && canUseBiometrics(activity)

    fun unlocked() { Api.lockedNow = false; onUnlocked() }
    fun handle(e: ApiException) {
        when {
            e.code == "locked" -> { locked = true; lockMessage = null }
            // No attempts count means the server no longer knows this phone (signed in elsewhere, or PIN reset).
            e.code == "invalid" && (e.body == null || e.body.isNull("attemptsLeft")) -> { store.forgetDevice(); onUsePassword() }
            e.code == "offline" -> error = e.message // no shake: the PIN wasn't wrong
            else -> { error = e.message; errors++ }
        }
    }
    /** Asks whether this phone's customer is still locked. [quiet]: on open or resume, no message if nothing changed. */
    fun checkLock(quiet: Boolean) {
        if (token == null) return
        if (!quiet) checking = true
        scope.launch {
            try {
                val (isLocked, first) = Api.deviceStatus(token)
                name = first; store.firstName = first
                if (isLocked) { locked = true; if (!quiet) lockMessage = "It's still locked. Ask the ORVYN team to unlock it." }
                else if (locked) { locked = false; Api.lockedNow = false; error = null; info = "Your account is unlocked. Enter your PIN." }
            } catch (e: ApiException) {
                if (e.code == "invalid") { store.forgetDevice(); onUsePassword() } else if (!quiet) lockMessage = e.message
            } finally { checking = false }
        }
    }
    val fingerprint = {
        promptBiometric(activity, "Open ORVYN") {
            scope.launch { busy = true; try { Api.signInWithDevice(token!!); unlocked() } catch (e: ApiException) { handle(e) } finally { busy = false } }
        }
    }
    LaunchedEffect(Unit) {
        if (token == null) onUsePassword() else { checkLock(quiet = true); if (biometric && !locked) fingerprint() }
    }
    // Q-B9 AC5: coming back after an officer unlocked the customer shows the PIN pad straight away.
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME && locked) checkLock(quiet = true) }
        lifecycle.lifecycle.addObserver(o)
        onDispose { lifecycle.lifecycle.removeObserver(o) }
    }
    val title = name?.let { "Welcome back, $it" } ?: "Welcome back"
    val notYou: @Composable () -> Unit = {
        OrvynTextButton("Not you? Sign in with a password", onClick = { store.forgetDevice(); onUsePassword() })
    }
    val demo: @Composable () -> Unit = {
        Text("Demo · ORVYN Bank is a fictional bank", style = MaterialTheme.typography.labelMedium, color = OrvynTheme.colors.textTertiary,
            textAlign = TextAlign.Center) // story S1.6 AC5
    }

    if (locked) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 20.dp)) {
            BackBar(null)
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            FitArea(bottom = false) {
                Spacer(Modifier.height(0.dp))
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StateIcon(R.drawable.ph_lock_simple, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
                    Text("Your account is locked", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text("Ask the ORVYN team to unlock it. Once they have, tap Try again.", style = MaterialTheme.typography.bodyLarge,
                        color = OrvynTheme.colors.textSecondary, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(0.dp))
            }
            lockMessage?.let { id.orvyn.app.ui.components.GroupError(it) }
            OrvynButton("Try again", loading = checking, onClick = { checkLock(quiet = false) }, modifier = Modifier.padding(vertical = 16.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { notYou(); demo(); Spacer(Modifier.height(8.dp)) }
        }
        return
    }

    PinScreen(
        title, "Enter your 6-digit PIN.", null,
        onComplete = { pin ->
            info = null; error = null; busy = true
            scope.launch { try { Api.signInWithPin(token!!, pin); unlocked() } catch (e: ApiException) { handle(e) } finally { busy = false } }
        },
        errorCount = errors, errorText = error, busyText = if (busy) "Checking your PIN…" else null, infoText = info,
        onBiometric = if (biometric) fingerprint else null,
        footer = {
            OrvynTextButton("Forgot PIN?", onClick = { forgotSheet = true })
            notYou()
            demo()
        },
    )
    if (forgotSheet) OrvynBottomSheet(onDismiss = { forgotSheet = false }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Forgot your PIN?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text("Sign in with your phone number and password, then choose a new PIN.", style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary)
            OrvynButton("Sign in with password", onClick = { forgotSheet = false; onForgotPin() })
            OrvynTextButton("Not now", onClick = { forgotSheet = false }, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
