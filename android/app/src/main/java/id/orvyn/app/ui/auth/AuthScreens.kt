package id.orvyn.app.ui.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.DeviceStore
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.OrvynTextButton
import id.orvyn.app.ui.components.OrvynTextField
import id.orvyn.app.ui.components.PinPad
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.launch

/** Plain screen scaffold for the sign-in flow: optional back arrow, title, one-line explanation. */
@Composable
fun FlowScreen(title: String, body: String?, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Box(Modifier.height(56.dp), contentAlignment = Alignment.CenterStart) {
            if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp)) {
                Icon(painterResource(R.drawable.ph_arrow_left), contentDescription = "Back")
            }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary)
        }
        Spacer(Modifier.height(24.dp))
        content()
    }
}

/** Story S1.5: phone number + password, once per phone. */
@Composable
fun SignInScreen(store: DeviceStore, onBack: () -> Unit, onSignedIn: (needsPin: Boolean) -> Unit) {
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    FlowScreen("Sign in", "Use the phone number and password the ORVYN team sent you.", onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OrvynTextField("Phone number", phone, { phone = it; error = null }, keyboardType = KeyboardType.Phone, helper = "For example 0812 3456 7890")
            OrvynTextField("Password", password, { password = it; error = null }, password = true, error = error)
        }
        OrvynButton(
            "Sign in",
            enabled = phone.isNotBlank() && password.isNotBlank(),
            loading = busy,
            onClick = {
                busy = true
                scope.launch {
                    try {
                        val r = Api.signInWithPassword(phone, password)
                        store.forgetDevice()
                        store.deviceToken = r.deviceToken
                        onSignedIn(r.needsPin)
                    } catch (e: ApiException) {
                        error = e.message
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }
}

/** First sign-in: choose a 6-digit PIN, then type it again. */
@Composable
fun CreatePinScreen(onDone: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    FlowScreen(
        if (first == null) "Create your PIN" else "Enter it again",
        if (first == null) "You'll use these 6 digits to open ORVYN and approve payments. Avoid 123456 or your birthday." else "Just to be sure you remember it.",
        onBack = null,
    ) {
        Spacer(Modifier.weight(1f))
        PinPad(
            onComplete = { pin ->
                when {
                    first == null -> { first = pin; error = null }
                    pin != first -> { first = null; error = "Those PINs didn't match. Start again."; errors++ }
                    else -> scope.launch {
                        try { Api.setPin(pin); onDone() } catch (e: ApiException) { first = null; error = e.message; errors++ }
                    }
                }
            },
            errorCount = errors,
            errorText = error,
            onBiometric = null,
            modifier = Modifier.padding(bottom = 24.dp),
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
        Spacer(Modifier.weight(1f))
        MessageState(R.drawable.ph_fingerprint, "Your fingerprint stays on this phone", "Android checks it. ORVYN only learns whether it matched.")
        Spacer(Modifier.weight(1f))
        OrvynButton("Turn on", onClick = {
            promptBiometric(activity, "Confirm your fingerprint") { store.biometricEnabled = true; onDone() }
        })
        OrvynTextButton("Not now", onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
    }
}

/** Story S1.6: opening ORVYN on the Registered Device. */
@Composable
fun UnlockScreen(store: DeviceStore, firstName: String?, onUnlocked: () -> Unit, onUsePassword: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    var error by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val token = store.deviceToken
    val biometric = store.biometricEnabled && canUseBiometrics(activity)

    fun handle(e: ApiException) {
        if (e.code == "locked") locked = true
        // No attempts count means the server no longer knows this phone (signed in elsewhere, or PIN reset).
        if (e.code == "invalid" && (e.body == null || e.body.isNull("attemptsLeft"))) { store.forgetDevice(); onUsePassword(); return }
        error = e.message
        errors++
    }
    val fingerprint = {
        promptBiometric(activity, "Open ORVYN") {
            scope.launch { try { Api.signInWithDevice(token!!); onUnlocked() } catch (e: ApiException) { handle(e) } }
        }
    }
    LaunchedEffect(Unit) {
        if (token == null) onUsePassword() else if (biometric) fingerprint()
    }

    FlowScreen(if (firstName != null) "Welcome back, $firstName" else "Welcome back", "Enter your 6-digit PIN.", null) {
        Spacer(Modifier.weight(1f))
        if (locked) {
            MessageState(R.drawable.ph_warning_circle, "Your account is locked", error ?: "Ask the ORVYN team to unlock it.")
            Spacer(Modifier.weight(1f))
        } else {
            PinPad(
                onComplete = { pin -> scope.launch { try { Api.signInWithPin(token!!, pin); onUnlocked() } catch (e: ApiException) { handle(e) } } },
                errorCount = errors,
                errorText = error,
                onBiometric = if (biometric) fingerprint else null,
            )
        }
        OrvynTextButton("Not you? Sign in with a password", onClick = { store.forgetDevice(); onUsePassword() },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
        Spacer(Modifier.size(8.dp))
    }
}
