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
        Section("Security") {
            SettingRow("Change PIN") { onRoute("profile/pin") }
            Divider()
            SettingRow("Change password") { onRoute("profile/password") }
            if (canUseBiometrics(activity)) {
                Divider()
                SettingRow("Sign in with fingerprint", role = Role.Switch, trailing = { Switch(biometric, onCheckedChange = null) }) {
                    if (biometric) { biometric = false; store.biometricEnabled = false }
                    else promptBiometric(activity, "Confirm your fingerprint") { biometric = true; store.biometricEnabled = true }
                }
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
            SettingRow("Sign out") { confirm = true }
        }
        Text("ORVYN ${BuildConfig.VERSION_NAME} · Demo. ORVYN Bank is a fictional bank, and all money in it is pretend.",
            style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textTertiary, modifier = Modifier.padding(20.dp))
    }
    if (confirm) ConfirmDialog(
        title = "Sign out of ORVYN?",
        body = "This phone forgets your PIN and fingerprint sign-in. Next time you sign in with your phone number and password.",
        confirmLabel = "Sign out", destructive = true, onDismiss = { confirm = false },
        onConfirm = { scope.launch { Api.signOut(forget = true); store.forgetDevice(); onSignedOut() } },
    )
}

/** Story S1.30 AC1: current PIN, new PIN, new PIN again. */
@Composable
fun ChangePinFlow(onDone: () -> Unit) {
    var old by remember { mutableStateOf<String?>(null) }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (done) {
        FlowScreen("PIN changed", "Use your new PIN from now on.", null) {
            Spacer(Modifier.weight(1f)); OrvynButton("Done", onClick = onDone, modifier = Modifier.padding(vertical = 16.dp))
        }
        return
    }
    FlowScreen(
        when { old == null -> "Enter your current PIN"; first == null -> "Choose a new PIN"; else -> "Enter the new PIN again" },
        if (old != null && first == null) "Avoid 123456 or your birthday." else null, onDone,
    ) {
        Spacer(Modifier.weight(1f))
        PinPad(onComplete = { pin ->
            when {
                old == null -> { old = pin; error = null }
                first == null -> { first = pin; error = null }
                pin != first -> { first = null; error = "Those PINs didn't match. Choose your new PIN again."; errors++ }
                else -> scope.launch {
                    try { Api.changePin(old!!, pin); done = true }
                    catch (e: ApiException) { if (e.code == "WRONG_PIN") old = null; first = null; error = e.message; errors++ }
                }
            }
        }, errorCount = errors, errorText = error, onBiometric = null, modifier = Modifier.padding(bottom = 24.dp))
    }
}

@Composable
fun ChangePasswordScreen(onDone: () -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    FlowScreen(if (done) "Password changed" else "Change password", if (done) "Use your new password the next time you sign in on a phone." else null, onDone) {
        if (done) { Spacer(Modifier.weight(1f)); OrvynButton("Done", onClick = onDone, modifier = Modifier.padding(vertical = 16.dp)); return@FlowScreen }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OrvynTextField("Current password", old, { old = it; error = null }, password = true)
            OrvynTextField("New password", new, { new = it; error = null }, password = true, helper = "At least 8 characters, with letters and numbers.", error = error)
        }
        OrvynButton("Save password", enabled = old.isNotEmpty() && new.isNotEmpty(), loading = busy, modifier = Modifier.padding(vertical = 16.dp), onClick = {
            busy = true
            scope.launch { try { Api.changePassword(old, new); done = true } catch (e: ApiException) { error = e.message } finally { busy = false } }
        })
    }
}

/** Story S1.31 AC1: FAQs by category, searchable. */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    var faqs by remember { mutableStateOf<List<Faq>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { try { faqs = Api.content().faqs } catch (e: ApiException) { error = e.message } }
    FlowScreen("Questions and answers", null, onBack) {
        OrvynTextField("Search", query, { query = it })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            val list = faqs?.filter { query.isBlank() || (it.question + it.answer).contains(query.trim(), ignoreCase = true) }
            when {
                error != null -> MessageState(R.drawable.ph_warning_circle, "Couldn't load help", error!!)
                list == null -> repeat(4) { Skeleton(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(48.dp)) }
                list.isEmpty() -> MessageState(R.drawable.ph_magnifying_glass, "No answers match", "Try another word, like transfer or card.")
                else -> list.groupBy { it.category }.forEach { (cat, items) ->
                    Text(cat.uppercase(), style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    items.forEach { f ->
                        Column(Modifier.fillMaxWidth().clickable { open = if (open == f.id) null else f.id }.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(f.question, style = MaterialTheme.typography.titleMedium)
                            if (open == f.id) Text(f.answer, style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Story S1.31 AC2: PRD 7.4 in plain words. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    FlowScreen("Privacy", "What ORVYN can and can't see on your phone.", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for ((title, body) in listOf(
                "What ORVYN sends" to "Only what you type into the app: your sign-in details, the people and amounts you pay, and the notes you add.",
                "Camera" to "Used only while the QRIS scanner is open. The QR code is read on your phone. No pictures are taken, saved or sent.",
                "Fingerprint" to "Android checks your fingerprint. ORVYN only learns whether it matched, never the fingerprint itself.",
                "Gallery" to "When you pick a QR picture, Android hands ORVYN that one picture. ORVYN can't see your other photos.",
                "Never touched" to "Your contacts, location, messages, calls, files and other apps. ORVYN doesn't ask for them, so it can't read them.",
                "No tracking" to "No analytics, ads or crash reporters. Your phone is recognised by a random code ORVYN makes when it's installed, not by its serial number.",
                "Notifications" to "Google's Firebase delivers push notifications to a random token made for this phone. Each one carries the same short text as your Inbox, like an amount and a name. Your lock screen only shows \"New activity\".",
            )) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary)
                }
            }
            Text("ORVYN Bank is a demo. All money in it is pretend.", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textTertiary,
                modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}
