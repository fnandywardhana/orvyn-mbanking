package id.orvyn.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import id.orvyn.app.BuildConfig
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.DeviceStore
import id.orvyn.app.data.parseHome
import id.orvyn.app.ui.auth.BiometricOfferScreen
import id.orvyn.app.ui.auth.CreatePinScreen
import id.orvyn.app.ui.auth.SignInScreen
import id.orvyn.app.ui.auth.UnlockScreen
import id.orvyn.app.ui.components.ConfirmDialog
import id.orvyn.app.ui.components.ListRow
import id.orvyn.app.ui.components.Tier
import id.orvyn.app.ui.components.TierBadge
import id.orvyn.app.ui.history.HistoryScreen
import id.orvyn.app.ui.history.TransactionDetailScreen
import id.orvyn.app.ui.home.HomeScreen
import id.orvyn.app.ui.transfer.TransferFlow
import id.orvyn.app.ui.qris.QrisFlow
import id.orvyn.app.ui.bills.BillsFlow
import id.orvyn.app.ui.cards.CardPayFlow
import id.orvyn.app.ui.cards.CardsScreen
import id.orvyn.app.ui.inbox.InboxScreen
import id.orvyn.app.ui.welcome.WelcomeScreen
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Bumped by the activity when the session ends (5 idle minutes, or the server said so). */
object SignOutSignal { var count by mutableIntStateOf(0) }

@Composable
fun OrvynApp(store: DeviceStore) {
    val nav = rememberNavController()
    val cached = remember { store.lastHome?.second?.let { runCatching { parseHome(JSONObject(it)) }.getOrNull() } }
    val start = if (store.deviceToken != null) "unlock" else "welcome"

    LaunchedEffect(SignOutSignal.count) {
        if (SignOutSignal.count > 0) nav.navigate(if (store.deviceToken != null) "unlock" else "welcome") { popUpTo(0) }
    }
    val toMain = { nav.navigate("main") { popUpTo(0) } }
    var pendingCard by remember { mutableStateOf<id.orvyn.app.data.Card?>(null) }
    var pendingQrTransfer by remember { mutableStateOf<Pair<id.orvyn.app.data.Recipient, Long?>?>(null) }

    NavHost(nav, startDestination = start) {
        composable("welcome") { WelcomeScreen(onSignIn = { nav.navigate("signin") }, onRegister = { nav.navigate("signin") }) }
        composable("signin") {
            SignInScreen(store, onBack = { nav.popBackStack() }) { needsPin -> if (needsPin) nav.navigate("pin/create") { popUpTo(0) } else toMain() }
        }
        composable("pin/create") { CreatePinScreen { nav.navigate("biometric") { popUpTo(0) } } }
        composable("biometric") { BiometricOfferScreen(store) { toMain() } }
        composable("unlock") {
            UnlockScreen(store, cached?.firstName, onUnlocked = toMain, onUsePassword = { nav.navigate("signin") { popUpTo(0) } })
        }
        composable("main") {
            MainShell(store, onOpenTransaction = { nav.navigate("tx/$it") }, onSignedOut = { nav.navigate("welcome") { popUpTo(0) } },
                onTransfer = { nav.navigate("transfer") }, onQris = { nav.navigate("qris") }, onRoute = { nav.navigate(it) },
                onPayCard = { pendingCard = it; nav.navigate("cardpay") })
        }
        composable("transfer") { TransferFlow(onExit = { nav.popBackStack() }) }
        composable("bills") { BillsFlow(onExit = { nav.popBackStack() }) }
        composable("inbox") { InboxScreen(onBack = { nav.popBackStack() }, onOpenTransaction = { nav.navigate("tx/$it") }) }
        composable("cardpay") { pendingCard?.let { CardPayFlow(it, onExit = { nav.popBackStack() }) } }
        composable("topup") { BillsFlow(onExit = { nav.popBackStack() }, ewalletsOnly = true) }
        composable("qris") {
            QrisFlow(onExit = { nav.popBackStack() }, onSendTo = { r, a -> pendingQrTransfer = r to a; nav.navigate("transfer/qr") { popUpTo("main") } })
        }
        composable("transfer/qr") {
            val (r, a) = pendingQrTransfer ?: return@composable
            TransferFlow(onExit = { nav.popBackStack() }, startWith = r, startAmount = a)
        }
        composable("tx/{id}") { entry ->
            val snack = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            Scaffold(snackbarHost = { SnackbarHost(snack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
                Box(Modifier.padding(pad)) {
                    TransactionDetailScreen(entry.arguments!!.getString("id")!!, onBack = { nav.popBackStack() }) { msg -> scope.launch { snack.showSnackbar(msg) } }
                }
            }
        }
    }
}

private enum class Tab(val label: String, val icon: Int, val iconActive: Int) {
    Home("Home", R.drawable.ph_house, R.drawable.ph_house_fill),
    History("History", R.drawable.ph_clock_counter_clockwise, R.drawable.ph_clock_counter_clockwise_fill),
    Qris("QRIS", R.drawable.ph_qr_code, R.drawable.ph_qr_code),
    Cards("Cards", R.drawable.ph_credit_card, R.drawable.ph_credit_card_fill),
    Profile("Profile", R.drawable.ph_user, R.drawable.ph_user_fill),
}

@Composable
private fun MainShell(store: DeviceStore, onOpenTransaction: (String) -> Unit, onSignedOut: () -> Unit, onTransfer: () -> Unit, onQris: () -> Unit, onRoute: (String) -> Unit, onPayCard: (id.orvyn.app.data.Card) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val say: (String) -> Unit = { msg -> scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(msg) } }
    val soon: (String) -> Unit = { what -> say("$what arrives in the next update.") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = { BottomBar(tab) { t -> if (t == Tab.Qris) onQris() else tab = t } },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                Tab.Home -> HomeScreen(store, onSeeAll = { tab = Tab.History }, onOpenTransaction = onOpenTransaction, onQuickAction = { when (it) { "Transfer" -> onTransfer(); "QRIS" -> onQris(); "Pay" -> onRoute("bills"); else -> onRoute("topup") } }, onCopied = say,
                    onInbox = { onRoute("inbox") })
                Tab.History -> HistoryScreen(store.lastHome?.second?.let { runCatching { parseHome(JSONObject(it)).accounts.firstOrNull()?.id }.getOrNull() }, onOpenTransaction)
                Tab.Profile -> ProfileScreen(store, onSignedOut)
                Tab.Cards -> CardsScreen(onPayBill = onPayCard, say = say)
                else -> {}
            }
        }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            for (t in Tab.entries) {
                val active = t == current
                val item = Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Tab) { onSelect(t) }.semantics { selected = active }
                if (t == Tab.Qris) Box(item) {
                    // The raised centre button (design system §5) pokes 20dp above the bar.
                    Box(
                        Modifier.align(Alignment.TopCenter).offset(y = (-20).dp).size(52.dp)
                            .shadow(6.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) { Icon(painterResource(t.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary) }
                    Text(t.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
                } else Column(item, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                    Icon(painterResource(if (active) t.iconActive else t.icon), contentDescription = null,
                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(t.label, style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(store: DeviceStore, onSignedOut: () -> Unit) {
    val home = remember { store.lastHome?.second?.let { runCatching { parseHome(JSONObject(it)) }.getOrNull() } }
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 16.dp).semantics { heading() })
        if (home != null) Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(home.fullName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TierBadge(Tier.valueOf(home.tier.replaceFirstChar(Char::uppercase)))
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            ListRow("Sign out", "You'll need your password to sign in again", R.drawable.ph_x, onClick = { confirm = true })
        }
        Spacer(Modifier.weight(1f))
        Text("ORVYN ${BuildConfig.VERSION_NAME} · Demo. ORVYN Bank is a fictional bank.", style = MaterialTheme.typography.bodyMedium,
            color = id.orvyn.app.ui.theme.OrvynTheme.colors.textTertiary, modifier = Modifier.padding(20.dp))
    }
    if (confirm) ConfirmDialog(
        title = "Sign out of ORVYN?",
        body = "This phone forgets your PIN and fingerprint sign-in. Next time you sign in with your phone number and password.",
        confirmLabel = "Sign out",
        destructive = true,
        onDismiss = { confirm = false },
        onConfirm = { scope.launch { Api.signOut(); store.forgetDevice(); onSignedOut() } },
    )
}
