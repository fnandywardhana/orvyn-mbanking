package id.orvyn.app.ui

import id.orvyn.app.ui.insights.CategoryScreen
import id.orvyn.app.ui.insights.InsightsScreen
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
import androidx.navigation.compose.currentBackStackEntryAsState
import id.orvyn.app.BuildConfig
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.Push
import id.orvyn.app.data.Recipient
import id.orvyn.app.ui.pockets.PocketEditor
import id.orvyn.app.ui.transfer.ScheduledScreen
import id.orvyn.app.ui.vyna.VynaScreen
import id.orvyn.app.ui.profile.StatementsScreen
import id.orvyn.app.ui.requests.AskFlow
import id.orvyn.app.ui.requests.RequestScreen
import id.orvyn.app.ui.requests.RequestsScreen
import id.orvyn.app.ui.requests.SplitFlow
import id.orvyn.app.ui.pockets.PocketMoveFlow
import id.orvyn.app.ui.pockets.PocketScreen
import id.orvyn.app.data.AppConfig
import id.orvyn.app.data.DataVersion
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
import id.orvyn.app.ui.profile.ChangePasswordScreen
import id.orvyn.app.ui.profile.ChangePinFlow
import id.orvyn.app.ui.profile.HelpScreen
import id.orvyn.app.ui.profile.PrivacyScreen
import id.orvyn.app.ui.profile.ProfileScreen
import id.orvyn.app.ui.register.RegisterFlow
import id.orvyn.app.ui.welcome.WelcomeScreen
import kotlinx.coroutines.launch
import org.json.JSONObject

/** The theme the customer chose; changes apply immediately (story S1.29). */
object ThemeMode { var value by mutableStateOf("system") }
object ThemeIsDark { var value by mutableStateOf(false) }

/** Call from screens whose top sits on the dark brand gradient: status bar icons turn white while it's shown. */
@Composable
fun DarkTopBar() {
    val activity = androidx.compose.ui.platform.LocalContext.current as? id.orvyn.app.MainActivity ?: return
    androidx.compose.runtime.DisposableEffect(Unit) {
        activity.setStatusBarIcons(light = true)
        onDispose { activity.setStatusBarIcons(light = ThemeIsDark.value) }
    }
}

/** Bumped by the activity when the session ends (5 idle minutes, or the server said so). */
object SignOutSignal { var count by mutableIntStateOf(0) }

@Composable
fun OrvynApp(store: DeviceStore) {
    val nav = rememberNavController()
    val cached = remember { store.lastHome?.second?.let { runCatching { parseHome(JSONObject(it)) }.getOrNull() } }
    val start = if (store.deviceToken != null) "unlock" else "welcome"

    LaunchedEffect(DataVersion.count) { runCatching { Api.loadConfig() } }
    AppConfig.maintenance?.let { message ->
        // Story S1.37 AC4: during maintenance the app explains and waits.
        val scope = rememberCoroutineScope()
        id.orvyn.app.ui.auth.FlowScreen("Back soon", message, null) {
            Spacer(Modifier.weight(1f))
            id.orvyn.app.ui.components.MessageState(R.drawable.ph_clock_counter_clockwise, "ORVYN is being updated", "Your money is safe. Try again in a few minutes.")
            Spacer(Modifier.weight(1f))
            id.orvyn.app.ui.components.OrvynButton("Try again", onClick = { scope.launch { runCatching { Api.loadConfig() } } }, modifier = Modifier.padding(vertical = 16.dp))
        }
        return
    }
    LaunchedEffect(SignOutSignal.count) {
        if (SignOutSignal.count > 0) nav.navigate(if (store.deviceToken != null) "unlock" else "welcome") { popUpTo(0) }
    }
    val toMain = { nav.navigate("main") { popUpTo(0) } }
    // Story S2.0 AC4: a tapped notification opens its screen once the customer is signed in.
    val entry by nav.currentBackStackEntryAsState()
    LaunchedEffect(Push.pendingLink, entry) {
        val link = Push.pendingLink ?: return@LaunchedEffect
        val route = entry?.destination?.route ?: return@LaunchedEffect
        if (route in setOf("welcome", "unlock", "signin", "register", "pin/create", "biometric")) return@LaunchedEffect
        Push.pendingLink = null
        nav.navigate(when {
            link.startsWith("tx:") -> "tx/${link.removePrefix("tx:")}"
            link.startsWith("request:") -> "request/${link.removePrefix("request:")}"
            else -> "inbox"
        })
    }
    var pendingCard by remember { mutableStateOf<id.orvyn.app.data.Card?>(null) }
    var pendingQrTransfer by remember { mutableStateOf<Pair<id.orvyn.app.data.Recipient, Long?>?>(null) }

    NavHost(nav, startDestination = start) {
        composable("welcome") { WelcomeScreen(onSignIn = { nav.navigate("signin") }, onRegister = if (AppConfig.on("register")) ({ nav.navigate("register") }) else null) }
        composable("register") { RegisterFlow(store, onExit = { nav.popBackStack() }, onSignIn = { nav.navigate("signin") { popUpTo("welcome") } }) }
        composable("signin") {
            SignInScreen(store, onBack = { nav.popBackStack() }) { needsPin -> if (needsPin) nav.navigate("pin/create") { popUpTo(0) } else toMain() }
        }
        composable("pin/create") { CreatePinScreen { nav.navigate("biometric") { popUpTo(0) } } }
        composable("biometric") { BiometricOfferScreen(store) { toMain() } }
        composable("unlock") {
            UnlockScreen(store, cached?.firstName, onUnlocked = toMain, onUsePassword = { nav.navigate("signin") { popUpTo(0) } })
        }
        composable("main") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val askNotify = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(Unit) {
                Push.register(context)
                if (android.os.Build.VERSION.SDK_INT >= 33 && !store.askedNotifications) {
                    store.askedNotifications = true
                    askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            MainShell(store, onOpenTransaction = { nav.navigate("tx/$it") }, onSignedOut = { nav.navigate("welcome") { popUpTo(0) } },
                onTransfer = { nav.navigate("transfer") }, onQris = { nav.navigate("qris") }, onRoute = { nav.navigate(it) },
                onPayCard = { pendingCard = it; nav.navigate("cardpay") })
        }
        composable("transfer") { TransferFlow(onExit = { nav.popBackStack() }, onScheduled = { nav.navigate("scheduled") }) }
        composable("scheduled") { ScheduledScreen(onBack = { nav.popBackStack() }) }
        composable("bills") { BillsFlow(onExit = { nav.popBackStack() }) }
        composable("profile/pin") { ChangePinFlow(onDone = { nav.popBackStack() }) }
        composable("profile/password") { ChangePasswordScreen(onDone = { nav.popBackStack() }) }
        composable("help") { HelpScreen(onBack = { nav.popBackStack() }) }
        composable("privacy") { PrivacyScreen(onBack = { nav.popBackStack() }) }
        composable("inbox") { InboxScreen(onBack = { nav.popBackStack() }, onOpenTransaction = { nav.navigate("tx/$it") }, onOpenRequest = { nav.navigate("request/$it") }) }
        composable("insights") { InsightsScreen(onBack = { nav.popBackStack() }, onCategory = { nav.navigate("insights/$it") }) }
        composable("insights/{key}") { entry ->
            CategoryScreen(entry.arguments!!.getString("key")!!, store.lastHome?.second?.let { runCatching { parseHome(JSONObject(it)).accounts.firstOrNull()?.id }.getOrNull() },
                onBack = { nav.popBackStack() }, onOpen = { nav.navigate("tx/$it") })
        }
        composable("requests") { RequestsScreen(onBack = { nav.popBackStack() }, onAsk = { nav.navigate("request/new") }, onSplit = { nav.navigate("split/new") }, onOpen = { nav.navigate("request/$it") }) }
        composable("request/new") { AskFlow(onExit = { nav.popBackStack() }) }
        composable("request/{id}") { entry -> RequestScreen(entry.arguments!!.getString("id")!!, onBack = { nav.popBackStack() }) }
        composable("split/new?total={total}", arguments = listOf(androidx.navigation.navArgument("total") { nullable = true; defaultValue = null })) { entry ->
            SplitFlow(entry.arguments?.getString("total")?.toLongOrNull(), onExit = { nav.popBackStack() })
        }
        composable("cardpay") { pendingCard?.let { CardPayFlow(it, onExit = { nav.popBackStack() }) } }
        composable("topup") { BillsFlow(onExit = { nav.popBackStack() }, ewalletsOnly = true) }
        composable("qris") {
            QrisFlow(onExit = { nav.popBackStack() }, onSendTo = { r, a -> pendingQrTransfer = r to a; nav.navigate("transfer/qr") { popUpTo("main") } })
        }
        composable("transfer/qr") {
            val (r, a) = pendingQrTransfer ?: return@composable
            TransferFlow(onExit = { nav.popBackStack() }, startWith = r, startAmount = a)
        }
        composable("pocket/new") { PocketEditor(null, onBack = { nav.popBackStack() }, onSaved = { nav.navigate("pocket/$it") { popUpTo("main") } }, onClosed = {}) }
        composable("pocket/{id}") { entry ->
            val id = entry.arguments!!.getString("id")!!
            PocketScreen(id, onBack = { nav.popBackStack() }, onMove = { nav.navigate("pocket/$id/move/${if (it) "in" else "out"}") },
                onEdit = { nav.navigate("pocket/$id/edit") }, onOpenTransaction = { nav.navigate("tx/$it?account=$id") })
        }
        composable("pocket/{id}/edit") { entry ->
            PocketEditor(entry.arguments!!.getString("id"), onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() }, onClosed = { nav.popBackStack("main", false) })
        }
        composable("pocket/{id}/move/{dir}?amount={amount}", arguments = listOf(androidx.navigation.navArgument("amount") { nullable = true; defaultValue = null })) { entry ->
            PocketMoveFlow(entry.arguments!!.getString("id")!!, entry.arguments!!.getString("dir") == "in", onExit = { nav.popBackStack() },
                startAmount = entry.arguments?.getString("amount")?.toLongOrNull() ?: 0L)
        }
        composable("statements") { StatementsScreen(onBack = { nav.popBackStack() }) }
        composable("vyna") {
            VynaScreen(store, onBack = { nav.popBackStack() }, onAction = { a ->
                when (a.optString("type")) {
                    "transfer" -> {
                        pendingQrTransfer = Recipient(a.getString("bank"), a.getString("bankName"), a.getString("accountNumber"), a.getString("name")) to a.getLong("amount")
                        nav.navigate("transfer/qr")
                    }
                    "pocket" -> nav.navigate("pocket/${a.getString("pocketId")}/move/${a.getString("direction")}?amount=${a.getLong("amount")}")
                    else -> nav.navigate("bills")
                }
            })
        }
        composable("tx/{id}?account={account}", arguments = listOf(androidx.navigation.navArgument("account") { nullable = true; defaultValue = null })) { entry ->
            val snack = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            Scaffold(snackbarHost = { SnackbarHost(snack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
                Box(Modifier.padding(pad)) {
                    TransactionDetailScreen(entry.arguments!!.getString("id")!!, entry.arguments?.getString("account"), onBack = { nav.popBackStack() }, onSplit = { nav.navigate("split/new?total=$it") }) { msg -> scope.launch { snack.showSnackbar(msg) } }
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
        bottomBar = { BottomBar(tab) { t ->
            when {
                t == Tab.Qris && !AppConfig.on("qris") -> say("QRIS is switched off for now. Try again later.")
                t == Tab.Cards && !AppConfig.on("cards") -> say("Cards are switched off for now. Try again later.")
                t == Tab.Qris -> onQris()
                else -> tab = t
            }
        } },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                Tab.Home -> HomeScreen(store, onSeeAll = { tab = Tab.History }, onOpenTransaction = onOpenTransaction, onQuickAction = { action ->
                    if (action == "Cards") { tab = Tab.Cards; return@HomeScreen }
                    if (action == "Request") { onRoute("requests"); return@HomeScreen }
                    val feature = mapOf("Transfer" to "transfer", "QRIS" to "qris", "Pay" to "bills", "Top up" to "bills")[action]!!
                    if (!AppConfig.on(feature)) say("$action is switched off for now. Try again later.")
                    else when (action) { "Transfer" -> onTransfer(); "QRIS" -> onQris(); "Pay" -> onRoute("bills"); else -> onRoute("topup") }
                }, onCopied = say,
                    onInbox = { onRoute("inbox") }, onRoute = onRoute)
                Tab.History -> HistoryScreen(store.lastHome?.second?.let { runCatching { parseHome(JSONObject(it)).accounts.firstOrNull()?.id }.getOrNull() }, onOpenTransaction, onInsights = { onRoute("insights") })
                Tab.Profile -> ProfileScreen(store, onSignedOut, onRoute)
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

