package id.orvyn.app.ui

import id.orvyn.app.ui.insights.CategoryScreen
import id.orvyn.app.ui.insights.InsightsScreen
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.Push
import id.orvyn.app.data.Recipient
import id.orvyn.app.ui.pockets.PocketEditor
import id.orvyn.app.ui.transfer.ScheduledScreen
import id.orvyn.app.ui.vyna.VynaScreen
import id.orvyn.app.ui.profile.StatementsScreen
import id.orvyn.app.ui.profile.PasskeysScreen
import id.orvyn.app.ui.wealth.*
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
import id.orvyn.app.ui.components.CapFontScale
import id.orvyn.app.ui.components.FitArea
import id.orvyn.app.ui.components.InputGuard
import id.orvyn.app.ui.components.LocalSnacks
import id.orvyn.app.ui.components.OFFLINE
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.OrvynSnackbarHost
import id.orvyn.app.ui.components.snackAnchor
import id.orvyn.app.ui.components.StateIcon
import id.orvyn.app.ui.components.backEnter
import id.orvyn.app.ui.components.backExit
import id.orvyn.app.ui.components.forwardEnter
import id.orvyn.app.ui.components.forwardExit
import id.orvyn.app.ui.components.rememberReducedMotion
import id.orvyn.app.ui.components.rememberSnacks
import id.orvyn.app.ui.components.say
import id.orvyn.app.ui.history.HistoryScreen
import id.orvyn.app.ui.history.TransactionDetailScreen
import id.orvyn.app.ui.home.AccountScreen
import id.orvyn.app.ui.home.HomeScreen
import id.orvyn.app.ui.theme.OrvynTheme
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
import kotlinx.coroutines.delay
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

/** Every destination ignores taps during its enter transition (Q-E4), with the 250ms slide of design.md 4.3. */
private fun NavGraphBuilder.screen(route: String, arguments: List<NamedNavArgument> = emptyList(), content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit) =
    composable(route, arguments) { e -> InputGuard { content(e) } }

@Composable
fun OrvynApp(store: DeviceStore) {
    val nav = rememberNavController()
    // Decided once: recomputing it when the device token appears (first sign-in) would rebuild the graph and jump to unlock.
    val start = remember { if (store.deviceToken != null) "unlock" else "welcome" }
    val snacks = rememberSnacks()
    val reduced = rememberReducedMotion()

    // PO-8: maintenance shows up within a minute while ORVYN is open, and when it comes back to the front.
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(DataVersion.count) { runCatching { Api.loadConfig() } }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { while (true) { runCatching { Api.loadConfig() }; delay(60_000) } }
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
        if (route.substringBefore('?') in setOf("welcome", "unlock", "signin", "register", "pin/create", "pin/new", "biometric")) return@LaunchedEffect
        Push.pendingLink = null
        nav.navigate(when {
            link.startsWith("tx:") -> "tx/${link.removePrefix("tx:")}"
            link == "qris" -> "qris" // the widget's Scan QRIS button
            link.startsWith("request:") -> "request/${link.removePrefix("request:")}"
            else -> "inbox"
        })
    }
    var pendingCard by remember { mutableStateOf<id.orvyn.app.data.Card?>(null) }
    var pendingQrTransfer by remember { mutableStateOf<Pair<id.orvyn.app.data.Recipient, Long?>?>(null) }
    var pinTimedOut by remember { mutableStateOf(false) } // Forgot PIN took more than 10 minutes (Q-D6)
    val maintenance = AppConfig.maintenance

    CompositionLocalProvider(LocalSnacks provides snacks) {
    Box(Modifier.fillMaxSize()) {
    Box(if (maintenance != null) Modifier.fillMaxSize().clearAndSetSemantics {} else Modifier.fillMaxSize()) {
    NavHost(
        nav, startDestination = start,
        enterTransition = { if (reduced) EnterTransition.None else forwardEnter() },
        exitTransition = { if (reduced) ExitTransition.None else forwardExit() },
        popEnterTransition = { if (reduced) EnterTransition.None else backEnter() },
        popExitTransition = { if (reduced) ExitTransition.None else backExit() },
    ) {
        screen("welcome") { WelcomeScreen(onSignIn = { nav.navigate("signin") }, onRegister = if (AppConfig.on("register")) ({ nav.navigate("register") }) else null) }
        screen("register") { RegisterFlow(store, onExit = { nav.popBackStack() }, onSignIn = { nav.navigate("signin") { popUpTo("welcome") } }) }
        screen("signin?forgot={forgot}", listOf(androidx.navigation.navArgument("forgot") { defaultValue = false })) { e ->
            val forgot = e.arguments?.getBoolean("forgot") == true
            SignInScreen(store, onBack = { nav.popBackStack() }, forgotPin = forgot, timedOut = forgot && pinTimedOut) { needsPin ->
                pinTimedOut = false
                when {
                    forgot -> nav.navigate("pin/new") { popUpTo("unlock") }
                    needsPin -> nav.navigate("pin/create") { popUpTo(0) }
                    else -> toMain()
                }
            }
        }
        screen("pin/create") { CreatePinScreen(onDone = { nav.navigate("biometric") { popUpTo(0) } }) }
        // Q-D6: after Forgot PIN. Back returns to Unlock, where the old PIN still works until the new one is saved.
        screen("pin/new") {
            CreatePinScreen(forgot = true, onBack = { nav.popBackStack() }, onTimedOut = {
                pinTimedOut = true
                nav.navigate("signin?forgot=true") { popUpTo("unlock") }
            }, onDone = { toMain(); snacks.show("Your PIN is changed") })
        }
        screen("biometric") { BiometricOfferScreen(store) { toMain() } }
        screen("unlock") {
            UnlockScreen(store, onUnlocked = toMain, onUsePassword = { nav.navigate("signin") { popUpTo(0) } },
                onForgotPin = { nav.navigate("signin?forgot=true") })
        }
        screen("main") {
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
        screen("account") { AccountScreen(store, onBack = { nav.popBackStack() }, onOpenTransaction = { nav.navigate("tx/$it") }) }
        screen("transfer") { TransferFlow(onExit = { nav.popBackStack() }, onScheduled = { nav.navigate("scheduled") }) }
        screen("scheduled") { ScheduledScreen(onBack = { nav.popBackStack() }) }
        screen("bills") { BillsFlow(onExit = { nav.popBackStack() }, phone = myPhone(store)) }
        screen("profile/pin") { ChangePinFlow(onDone = { nav.popBackStack() }) }
        screen("profile/password") { ChangePasswordScreen(onDone = { nav.popBackStack() }) }
        screen("help") { HelpScreen(onBack = { nav.popBackStack() }, onAskVyna = if (AppConfig.on("vyna")) ({ nav.navigate("vyna") }) else null) }
        screen("privacy") { PrivacyScreen(onBack = { nav.popBackStack() }) }
        screen("inbox") { InboxScreen(onBack = { nav.popBackStack() }, onOpenTransaction = { nav.navigate("tx/$it") }, onOpenRequest = { nav.navigate("request/$it") }) }
        screen("insights") { InsightsScreen(onBack = { nav.popBackStack() }, onCategory = { nav.navigate("insights/$it") }) }
        screen("insights/{key}") { entry ->
            CategoryScreen(entry.arguments!!.getString("key")!!, onBack = { nav.popBackStack() }, onOpen = { id, account -> nav.navigate(if (account == null) "tx/$id" else "tx/$id?account=$account") })
        }
        screen("requests") { RequestsScreen(onBack = { nav.popBackStack() }, onAsk = { nav.navigate("request/new") }, onSplit = { nav.navigate("split/new") }, onOpen = { nav.navigate("request/$it") }) }
        screen("request/new") { AskFlow(onExit = { nav.popBackStack() }) }
        screen("request/{id}") { entry -> RequestScreen(entry.arguments!!.getString("id")!!, onBack = { nav.popBackStack() }) }
        screen("split/new?total={total}", listOf(androidx.navigation.navArgument("total") { nullable = true; defaultValue = null })) { entry ->
            SplitFlow(entry.arguments?.getString("total")?.toLongOrNull(), onExit = { nav.popBackStack() })
        }
        screen("cardpay") { pendingCard?.let { CardPayFlow(it, onExit = { nav.popBackStack() }) } }
        screen("topup") { BillsFlow(onExit = { nav.popBackStack() }, ewalletsOnly = true) }
        screen("qris") {
            QrisFlow(onExit = { nav.popBackStack() }, onSendTo = { r, a -> pendingQrTransfer = r to a; nav.navigate("transfer/qr") { popUpTo("main") } })
        }
        screen("transfer/qr") {
            val (r, a) = pendingQrTransfer ?: return@screen
            TransferFlow(onExit = { nav.popBackStack() }, startWith = r, startAmount = a)
        }
        screen("pocket/new") { PocketEditor(null, onBack = { nav.popBackStack() }, onSaved = { nav.navigate("pocket/$it") { popUpTo("main") } }, onClosed = {}) }
        screen("pocket/{id}") { entry ->
            val id = entry.arguments!!.getString("id")!!
            PocketScreen(id, onBack = { nav.popBackStack() }, onMove = { nav.navigate("pocket/$id/move/${if (it) "in" else "out"}") },
                onEdit = { nav.navigate("pocket/$id/edit") }, onOpenTransaction = { nav.navigate("tx/$it?account=$id") })
        }
        screen("pocket/{id}/edit") { entry ->
            PocketEditor(entry.arguments!!.getString("id"), onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() }, onClosed = { nav.popBackStack("main", false) })
        }
        screen("pocket/{id}/move/{dir}?amount={amount}", listOf(androidx.navigation.navArgument("amount") { nullable = true; defaultValue = null })) { entry ->
            PocketMoveFlow(entry.arguments!!.getString("id")!!, entry.arguments!!.getString("dir") == "in", onExit = { nav.popBackStack() },
                startAmount = entry.arguments?.getString("amount")?.toLongOrNull() ?: 0L)
        }
        screen("passkeys") { PasskeysScreen(onBack = { nav.popBackStack() }) }
        screen("wealth") { WealthScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(it) }) }
        screen("fx") { FxScreen(onBack = { nav.popBackStack() }, onExchange = { c, b -> nav.navigate("fx/$c/${if (b) "buy" else "sell"}") }) }
        screen("fx/{currency}/{side}") { e -> FxExchangeFlow(e.arguments!!.getString("currency")!!, e.arguments!!.getString("side") == "buy", onExit = { nav.popBackStack() }) }
        screen("deposits") { DepositsScreen(onBack = { nav.popBackStack() }, onOpenNew = { nav.navigate("deposits/new") }) }
        screen("deposits/new") { OpenDepositFlow(onExit = { nav.popBackStack() }) }
        screen("invest") { InvestScreen(onBack = { nav.popBackStack() }, onQuiz = { nav.navigate("invest/quiz") }, onProduct = { nav.navigate("invest/p/$it") }) }
        screen("invest/quiz") { RiskQuizFlow(onExit = { nav.popBackStack() }) }
        screen("invest/p/{pid}") { e ->
            val pid = e.arguments!!.getString("pid")!!
            ProductScreen(pid, onBack = { nav.popBackStack() }, onTrade = { nav.navigate("invest/p/$pid/${if (it) "buy" else "sell"}") })
        }
        screen("invest/p/{pid}/{side}") { e ->
            TradeFlow(e.arguments!!.getString("pid")!!, e.arguments!!.getString("side") == "buy", onExit = { nav.popBackStack() }, onQuiz = { nav.navigate("invest/quiz") })
        }
        screen("statements") { StatementsScreen(onBack = { nav.popBackStack() }) }
        screen("vyna") {
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
        screen("tx/{id}?account={account}", listOf(androidx.navigation.navArgument("account") { nullable = true; defaultValue = null })) { entry ->
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                TransactionDetailScreen(entry.arguments!!.getString("id")!!, entry.arguments?.getString("account"), onBack = { nav.popBackStack() },
                    onSplit = { nav.navigate("split/new?total=$it") }) { msg -> snacks.show(msg) }
            }
        }
    }
    }
    if (maintenance != null) MaintenanceScreen(maintenance) {
        // Q-B8 AC4: back to Unlock, or home when the session is still good.
        nav.navigate(if (Api.session != null) "main" else if (store.deviceToken != null) "unlock" else "welcome") { popUpTo(0) }
    }
    // One snackbar for the whole app, above the sticky buttons and the bottom nav (design.md 4.11).
    OrvynSnackbarHost(snacks)
    }
    }
}

private fun savingsId(store: DeviceStore) = store.lastHome?.second?.let { runCatching { parseHome(JSONObject(it)).accounts.firstOrNull()?.id }.getOrNull() }
private fun myPhone(store: DeviceStore) = store.lastHome?.second?.let { runCatching { parseHome(JSONObject(it)).phone }.getOrNull() }

/**
 * Q-B8 (design.md 6.8): one title, the server's sentence, Try again. It covers the whole app the moment the server says
 * maintenance, and goes when it's over.
 */
@Composable
private fun MaintenanceScreen(message: String, onOver: () -> Unit) {
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    BackHandler { activity?.moveTaskToBack(true) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .safeDrawingPadding().padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        FitArea(bottom = false) {
            Spacer(Modifier.height(0.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StateIcon(R.drawable.ph_clock_counter_clockwise, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                Text("ORVYN is being updated", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite })
                Text(message.ifBlank { "Your money is safe. Try again in a few minutes." }, style = MaterialTheme.typography.bodyLarge,
                    color = OrvynTheme.colors.textSecondary, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(0.dp))
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
        OrvynButton("Try again", loading = busy, modifier = Modifier.padding(bottom = 16.dp), onClick = {
            busy = true
            scope.launch {
                try {
                    Api.loadConfig()
                    if (AppConfig.maintenance == null) onOver() else status = "Still updating. Try again in a few minutes."
                } catch (e: Exception) { status = OFFLINE }
                busy = false
            }
        })
    }
}

internal enum class Tab(val label: String, val icon: Int, val iconActive: Int) {
    Home("Home", R.drawable.ph_house, R.drawable.ph_house_fill),
    History("History", R.drawable.ph_clock_counter_clockwise, R.drawable.ph_clock_counter_clockwise_fill),
    Qris("QRIS", R.drawable.ph_qr_code, R.drawable.ph_qr_code),
    Cards("Cards", R.drawable.ph_credit_card, R.drawable.ph_credit_card_fill),
    Profile("Profile", R.drawable.ph_user, R.drawable.ph_user_fill),
}

@Composable
private fun MainShell(store: DeviceStore, onOpenTransaction: (String) -> Unit, onSignedOut: () -> Unit, onTransfer: () -> Unit, onQris: () -> Unit, onRoute: (String) -> Unit, onPayCard: (id.orvyn.app.data.Card) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val snacks = LocalSnacks.current
    val say: (String) -> Unit = { msg -> snacks?.show(msg) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
        // Tabs are peers, not steps: a 150ms crossfade without sliding (design.md 4.3).
        androidx.compose.animation.Crossfade(tab, Modifier.padding(pad), animationSpec = tween(150), label = "tab") { t ->
            when (t) {
                Tab.Home -> HomeScreen(store, onSeeAll = { tab = Tab.History }, onOpenTransaction = onOpenTransaction, onQuickAction = { action ->
                    if (action == "Cards") { tab = Tab.Cards; return@HomeScreen }
                    if (action == "Request") { onRoute("requests"); return@HomeScreen }
                    val feature = mapOf("Transfer" to "transfer", "QRIS" to "qris", "Pay" to "bills", "Top up" to "bills")[action]!!
                    if (!AppConfig.on(feature)) say("$action is switched off for now. Try again later.")
                    else when (action) { "Transfer" -> onTransfer(); "QRIS" -> onQris(); "Pay" -> onRoute("bills"); else -> onRoute("topup") }
                }, onCopied = say, onInbox = { onRoute("inbox") }, onRoute = onRoute)
                Tab.History -> HistoryScreen(savingsId(store), onOpenTransaction, onInsights = { onRoute("insights") })
                Tab.Profile -> ProfileScreen(store, onSignedOut, onRoute)
                Tab.Cards -> CardsScreen(onPayBill = onPayCard, onOpenTransaction = { id, account -> onRoute(if (account == null) "tx/$id" else "tx/$id?account=$account") })
                else -> {}
            }
        }
    }
}

/** Raises the QRIS button 20dp above the bar and gives the space back, so its label sits right under it (Q-A5 AC3). */
private fun Modifier.raise(by: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val dy = by.roundToPx()
    layout(p.width, p.height - dy) { p.place(0, -dy) }
}

@Composable
internal fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.snackAnchor().background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CapFontScale { // labels stop growing at 1.5×, so "Profile" never breaks in its fifth of the width (design.md 4.1)
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.Bottom) {
                for (t in Tab.entries) {
                    val active = t == current
                    Column(
                        Modifier.weight(1f).heightIn(min = 64.dp).clickable(role = Role.Tab) { onSelect(t) }.semantics { selected = active }.padding(bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
                    ) {
                        if (t == Tab.Qris) Box(
                            Modifier.raise(20.dp).snackAnchor(unclipped = true).testTag("qris").size(52.dp).shadow(6.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(t.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary) }
                        else Icon(painterResource(if (active) t.iconActive else t.icon), contentDescription = null,
                            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(t.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
