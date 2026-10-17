package id.orvyn.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import id.orvyn.app.BuildConfig
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.DeviceStore
import id.orvyn.app.data.Faq
import id.orvyn.app.data.parseHome
import id.orvyn.app.ui.ThemeMode
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.auth.canUseBiometrics
import id.orvyn.app.ui.auth.promptBiometric
import id.orvyn.app.ui.components.ConfirmDialog
import id.orvyn.app.ui.components.*
import id.orvyn.app.ui.auth.PIN_MISMATCH
import id.orvyn.app.ui.auth.WEAK_PIN
import id.orvyn.app.ui.isAcceptablePin
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.stateDescription
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.OrvynTextField
import id.orvyn.app.ui.components.PinPad
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.components.Tier
import id.orvyn.app.ui.components.TierBadge
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp))
    Column(Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface), content = content)
}

@Composable
private fun SettingRow(title: String, value: String? = null, role: Role = Role.Button, selected: Boolean? = null, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = role, onClick = onClick).then(if (selected != null) Modifier.semantics { this.selected = selected } else Modifier)
            .heightIn(min = 56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        value?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary) }
        trailing?.invoke()
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

/** Story S1.29–S1.31. */
@Composable
fun ProfileScreen(store: DeviceStore, onSignedOut: () -> Unit, onRoute: (String) -> Unit) {
    val home = remember { store.lastHome?.second?.let { runCatching { parseHome(JSONObject(it)) }.getOrNull() } }
    val activity = LocalContext.current as FragmentActivity
    var biometric by remember { mutableStateOf(store.biometricEnabled) }
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, top = 16.dp).semantics { heading() })
        if (home != null) Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(home.fullName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TierBadge(Tier.valueOf(home.tier.replaceFirstChar(Char::uppercase)))
        }
        home?.relationshipManager?.let { id.orvyn.app.ui.wealth.RelationshipManagerCard(it, Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
        Section("Security") {
            SettingRow("Change PIN") { onRoute("profile/pin") }
            if (id.orvyn.app.data.AppConfig.on("passkeys")) {
                Divider()
                SettingRow("Passkeys") { onRoute("passkeys") }
            }
            Divider()
            SettingRow("Change password") { onRoute("profile/password") }
            if (canUseBiometrics(activity)) {
                Divider()
                id.orvyn.app.ui.components.SwitchRow("Sign in with fingerprint", null, biometric, onChange = {
                    if (biometric) { biometric = false; store.biometricEnabled = false }
                    else promptBiometric(activity, "Confirm your fingerprint") { biometric = true; store.biometricEnabled = true }
                })
            }
        }
        Section("Appearance") {
            listOf("system" to "Match my phone", "light" to "Light", "dark" to "Dark").forEachIndexed { i, (mode, label) ->
                if (i > 0) Divider()
                SettingRow(label, role = Role.RadioButton, selected = ThemeMode.value == mode,
                    trailing = { if (ThemeMode.value == mode) androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ph_check_circle), null, tint = MaterialTheme.colorScheme.primary) }) {
                    ThemeMode.value = mode; store.themeMode = mode
                }
            }
        }
        Section("Help") {
            SettingRow("Questions and answers") { onRoute("help") }
            Divider()
            SettingRow("Privacy") { onRoute("privacy") }
        }
        Section("Account") {
            if (id.orvyn.app.data.AppConfig.on("statements")) {
                SettingRow("E-statements") { onRoute("statements") }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            SettingRow("Sign out") { confirm = true }
        }
        Text("ORVYN ${BuildConfig.VERSION_NAME} · Demo. ORVYN Bank is a fictional bank, and all money in it is pretend.",
            style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textTertiary, modifier = Modifier.padding(20.dp))
    }
    val widgetContext = androidx.compose.ui.platform.LocalContext.current
    if (confirm) ConfirmDialog(
        title = "Sign out of ORVYN?",
        body = "This phone forgets your PIN and fingerprint sign-in. Next time you sign in with your phone number and password.",
        confirmLabel = "Sign out", destructive = true, onDismiss = { confirm = false },
        onConfirm = { scope.launch { Api.signOut(forget = true); store.forgetDevice(); id.orvyn.app.widget.BalanceWidget.refresh(widgetContext); onSignedOut() } },
    )
}

/**
 * Story S1.30 AC1 and Q-C1 (design.md 7.1): the current PIN is checked by the server first and counts toward the same 5
 * tries; then the new PIN twice, weak ones refused at once and a mismatch back to step 2.
 */
@Composable
fun ChangePinFlow(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var old by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var errors0 by remember { mutableIntStateOf(0) }
    var errors1 by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BackHandler(step in 1..2 && !busy) { step = 0; old = ""; first = ""; error = null }
    FlowSteps(step, index = { it }, success = { it == 3 }) { s ->
        when (s) {
            3 -> DoneScreen("Your PIN is changed", "Use your new PIN from now on.", onDone)
            0 -> PinScreen("Enter your current PIN", "We check it before you choose a new one.", onDone, errorCount = errors0, errorText = error,
                busyText = if (busy) "Checking your PIN…" else null,
                onComplete = { pin ->
                    busy = true; error = null
                    scope.launch {
                        try { Api.checkPin(pin); old = pin; step = 1 }
                        catch (e: ApiException) {
                            when (e.code) { "WRONG_PIN" -> { error = e.message; errors0++ }; "LOCKED", "signed_out" -> {}; else -> error = e.message }
                        } finally { busy = false }
                    }
                })
            1 -> PinScreen("Choose a new PIN", "Avoid 123456 or your birthday.", { step = 0; old = ""; error = null }, errorCount = errors1, errorText = error,
                onComplete = { pin -> if (!isAcceptablePin(pin)) { error = WEAK_PIN; errors1++ } else { error = null; first = pin; step = 2 } })
            else -> PinScreen("Enter the new PIN again", "Just to be sure you remember it.", { step = 0; old = ""; first = ""; error = null }, errorCount = 0,
                errorText = error, busyText = if (busy) "Saving your new PIN…" else null,
                onComplete = { pin ->
                    if (pin != first) { first = ""; error = PIN_MISMATCH; errors1++; step = 1; return@PinScreen }
                    busy = true; error = null
                    scope.launch {
                        try { Api.changePin(old, pin); step = 3 }
                        catch (e: ApiException) {
                            when (e.code) { "WRONG_PIN" -> { old = ""; error = e.message; errors0++; step = 0 }; "LOCKED", "signed_out" -> {}; else -> error = e.message }
                        } finally { busy = false }
                    }
                })
        }
    }
}

/** Q-C2 (design.md 7.2): a wrong current password shows under that field with the tries left; a weak new one under its own. */
@Composable
fun ChangePasswordScreen(onDone: () -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var oldError by remember { mutableStateOf<String?>(null) }
    var newError by remember { mutableStateOf<String?>(null) }
    var boxError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val oldFocus = remember { FocusRequester() }
    val newFocus = remember { FocusRequester() }
    if (done) return DoneScreen("Your password is changed", "Use your new password the next time you sign in on a phone.", onDone)
    fun save() {
        if (old.isEmpty() || new.isEmpty() || busy) return
        busy = true
        scope.launch {
            try { Api.changePassword(old, new); done = true }
            catch (e: ApiException) {
                when (e.code) {
                    "WRONG_PASSWORD" -> { oldError = e.message; runCatching { oldFocus.requestFocus() } }
                    "WEAK_PASSWORD" -> { newError = e.message; runCatching { newFocus.requestFocus() } }
                    "LOCKED", "signed_out" -> {}
                    else -> boxError = e.message
                }
            } finally { busy = false }
        }
    }
    FlowScreen("Change password", null, onDone) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OrvynTextField("Current password", old, { old = it; oldError = null; boxError = null }, password = true, error = oldError,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next, onImeAction = { newFocus.requestFocus() }, fieldModifier = Modifier.focusRequester(oldFocus))
            OrvynTextField("New password", new, { new = it; newError = null; boxError = null }, password = true, helper = "At least 8 characters, with letters and numbers.",
                error = newError, imeAction = androidx.compose.ui.text.input.ImeAction.Done, onImeAction = doneAction(old.isNotEmpty() && new.isNotEmpty() && !busy) { save() }, fieldModifier = Modifier.focusRequester(newFocus))
        }
        boxError?.let { ErrorBox(it, Modifier.padding(top = 12.dp)) }
        OrvynButton("Save password", enabled = old.isNotEmpty() && new.isNotEmpty(), loading = busy, modifier = Modifier.padding(vertical = 16.dp), onClick = { save() })
    }
}

/**
 * Story S1.31 AC1 and Q-G3: FAQs by category, searchable; each one says it can open (chevron, expanded or collapsed for
 * TalkBack). "Still need help?" offers Vyna, and is gone when Vyna is switched off (PO-17). No phone number (K3).
 */
@Composable
fun HelpScreen(onBack: () -> Unit, onAskVyna: (() -> Unit)? = null) {
    val faqs = rememberLoad { Api.content().faqs }
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    val reduced = id.orvyn.app.ui.components.rememberReducedMotion()
    FlowScreen("Questions and answers", null, onBack) {
        OrvynTextField("Search", query, { query = it }, imeAction = androidx.compose.ui.text.input.ImeAction.Search, onImeAction = closeKeyboard())
        Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            LoadContent(faqs, "Couldn't load help", skeleton = { Column { repeat(4) { Skeleton(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(48.dp)) } } }) { all ->
                val list = all.filter { query.isBlank() || (it.question + it.answer).contains(query.trim(), ignoreCase = true) }
                Column {
                    if (list.isEmpty()) MessageState(R.drawable.ph_magnifying_glass, "No answers match", "Try another word, like transfer or card.")
                    else list.groupBy { it.category }.forEach { (cat, items) ->
                        Text(cat.uppercase(), style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                        items.forEach { f ->
                            val isOpen = open == f.id
                            val turn by androidx.compose.animation.core.animateFloatAsState(if (isOpen) 180f else 0f, androidx.compose.animation.core.tween(if (reduced) 0 else 150), label = "caret")
                            Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { open = if (isOpen) null else f.id }
                                .semantics { stateDescription = if (isOpen) "Expanded" else "Collapsed"; if (isOpen) collapse { open = null; true } else expand { open = f.id; true } }
                                .heightIn(min = 56.dp).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(f.question, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    androidx.compose.material3.Icon(painterResource(R.drawable.ph_caret_down), null, Modifier.size(20.dp).rotate(turn), tint = OrvynTheme.colors.textSecondary)
                                }
                                if (isOpen) Text(f.answer, style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    if (onAskVyna != null) id.orvyn.app.ui.components.OrvynCard(Modifier.padding(top = 24.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            id.orvyn.app.ui.components.IconTile(R.drawable.ph_sparkle)
                            Text("Still need help?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        }
                        Text("Vyna can answer questions about your money and how ORVYN works.", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                        OrvynButton("Ask Vyna", onClick = onAskVyna, kind = id.orvyn.app.ui.components.ButtonKind.Secondary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Story S1.31 AC2 and Q-G3: every line of PRD 7.4 in plain words, including the sign-up camera and Vyna's microphone. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    FlowScreen("Privacy", "What ORVYN can and can't see on your phone.", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for ((title, body) in listOf(
                "What ORVYN sends" to "Only what you type into the app: your sign-in details, the people and amounts you pay, and the notes you add.",
                "Camera" to "Used only when you open it: to scan a QRIS code, and to photograph your KTP and take a selfie when you open an account. QR codes are read on your phone. The KTP photo and selfie stay on your phone, are never sent, and are deleted as soon as you move on.",
                "Microphone" to "Used only after you tap the microphone in Vyna. Your phone turns what you say into text, and only that text goes to Vyna. The recording never leaves your phone.",
                "Fingerprint" to "Android checks your fingerprint. ORVYN only learns whether it matched, never the fingerprint itself.",
                "Gallery" to "When you pick a QR picture, Android hands ORVYN that one picture. ORVYN can't see your other photos.",
                "Never touched" to "Your contacts, location, messages, calls, files and other apps. ORVYN doesn't ask for them, so it can't read them.",
                "No tracking" to "No analytics, ads or crash reporters. Your phone is recognised by a random code ORVYN makes when it's installed, not by its serial number.",
                "Notifications" to "Google's Firebase delivers push notifications to a random token made for this phone. Each one carries the same short text as your Inbox, like an amount and a name. Your lock screen only shows \"New activity\".",
            )) {
                Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text(body, style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary)
                }
            }
            Text("ORVYN Bank is a demo. All money in it is pretend.", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textTertiary,
                modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}
