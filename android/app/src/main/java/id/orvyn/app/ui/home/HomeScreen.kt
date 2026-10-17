package id.orvyn.app.ui.home

import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.AppConfig
import id.orvyn.app.data.Banner
import id.orvyn.app.data.DataVersion
import id.orvyn.app.data.DeviceStore
import id.orvyn.app.data.Home
import id.orvyn.app.data.Transaction
import id.orvyn.app.data.Wealth
import id.orvyn.app.data.parseHome
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.ThemeIsDark
import id.orvyn.app.ui.components.AmountText
import id.orvyn.app.ui.components.IconTile
import id.orvyn.app.ui.components.ListRow
import id.orvyn.app.ui.components.LoadError
import id.orvyn.app.ui.components.LocalSnacks
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.MoneyText
import id.orvyn.app.ui.components.OrvynCard
import id.orvyn.app.ui.components.OrvynTextButton
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.components.StatusBanner
import id.orvyn.app.ui.components.Tier
import id.orvyn.app.ui.components.TierBadge
import id.orvyn.app.ui.components.isLargeFont
import id.orvyn.app.ui.components.rememberOnce
import id.orvyn.app.ui.components.rememberReducedMotion
import id.orvyn.app.ui.formatTime
import id.orvyn.app.ui.dayLabel
import id.orvyn.app.ui.greeting
import id.orvyn.app.ui.groupAccountNumber
import id.orvyn.app.ui.theme.Brand
import id.orvyn.app.ui.theme.OrvynTheme
import id.orvyn.app.ui.theme.OrvynType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Icon for a transaction row, by kind. */
fun iconFor(t: Transaction) = when (t.kind) {
    "qris_payment" -> R.drawable.ph_qr_code
    "bill_payment" -> R.drawable.ph_lightning
    "pocket_move" -> R.drawable.ph_piggy_bank
    "card_spend", "card_payment" -> R.drawable.ph_credit_card
    else -> R.drawable.ph_arrows_left_right
}

/** "QRIS · Yesterday, 16:16"; lists already grouped by day pass [withDay] = false for "QRIS · 16:16". */
fun subtitleFor(t: Transaction, withDay: Boolean = true) =
    listOfNotNull(t.subtitle, if (withDay) "${dayLabel(t.at)}, ${formatTime(t.at)}" else formatTime(t.at)).joinToString(" · ")

/** At large font sizes the amount moves under the subtitle (Q-A6). */
@Composable
fun TransactionRow(t: Transaction, withDay: Boolean = true, onClick: (() -> Unit)?) =
    ListRow(t.title, subtitleFor(t, withDay), iconFor(t), onClick = onClick, stack = true) { MoneyText(t.amount, struck = t.reversed) }

/** What the home screen last showed, kept while ORVYN runs so a failed refresh still has something to show (PO-24). */
private object HomeCache { var wealth: Wealth? = null; var banners: List<Banner>? = null; var insight: String? = null }

private const val HIDDEN = "Rp••••••••"

/** Story S1.7, with the 0.9.0 order (Q-G1), offline banner (Q-B6) and a pull-to-refresh that reloads every part (Q-B7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    store: DeviceStore,
    onSeeAll: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onQuickAction: (String) -> Unit,
    onCopied: (String) -> Unit,
    onInbox: () -> Unit = {},
    onRoute: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var home by remember { mutableStateOf(store.lastHome?.second?.let { runCatching { parseHome(JSONObject(it)) }.getOrNull() }) }
    var updatedAt by remember { mutableStateOf(store.lastHome?.first) }
    var offline by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(store.balanceHidden) }
    var wealth by remember { mutableStateOf(HomeCache.wealth) }
    var banners by remember { mutableStateOf(HomeCache.banners) }
    var insight by remember { mutableStateOf(HomeCache.insight) }
    val scope = rememberCoroutineScope()
    val snacks = LocalSnacks.current

    /** Balances, Total assets, banners and the insight card together, so two numbers never disagree (Q-B7). */
    suspend fun load() {
        loading = true
        coroutineScope {
            val w = async { if (listOf("fx", "deposits", "invest").any { AppConfig.on(it) }) runCatching { Api.wealth() }.getOrNull() else null }
            val c = async { runCatching { Api.content().banners }.getOrNull() }
            val i = async { if (AppConfig.on("insights")) runCatching { Api.insightCard() }.getOrNull() else null }
            launch { runCatching { Api.loadConfig() } }
            try {
                val json = Api.homeJson()
                home = parseHome(json)
                offline = false; error = null
                updatedAt = System.currentTimeMillis()
                store.lastHome = updatedAt!! to json.toString()
                store.firstName = home!!.firstName
                id.orvyn.app.widget.BalanceWidget.refresh(context)
            } catch (e: ApiException) {
                if (e.code == "offline") offline = true else error = e.message
            }
            w.await()?.let { wealth = it; HomeCache.wealth = it }
            c.await()?.let { banners = it; HomeCache.banners = it }
            if (AppConfig.on("insights")) i.await().let { if (it != null || !offline) { insight = it; HomeCache.insight = it } }
        }
        loading = false
    }
    LaunchedEffect(DataVersion.count) { load() } // reloads after money moves
    // B7 AC2: coming back from the background refreshes Total assets with the rest.
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        var first = true
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { if (first) first = false else scope.launch { load() } } }
        lifecycle.lifecycle.addObserver(o)
        onDispose { lifecycle.lifecycle.removeObserver(o) }
    }
    var announcement by remember { mutableStateOf<id.orvyn.app.data.Announcement?>(null) }
    LaunchedEffect(Unit) { runCatching { announcement = Api.announcement() } }
    announcement?.let { a ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { Text(a.title, style = MaterialTheme.typography.titleLarge) },
            text = { Text(a.body, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { OrvynTextButton("Got it", onClick = { scope.launch { runCatching { Api.announcementSeen(a.id) } }; announcement = null }) },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    // Q-E12: once the dark hero has scrolled away in the light theme, the status bar gets dark icons on the page colour.
    val pastHero by remember { androidx.compose.runtime.derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 200 } }
    val activity = context as? id.orvyn.app.MainActivity
    LaunchedEffect(pastHero, ThemeIsDark.value) { activity?.setStatusBarIcons(light = !pastHero || ThemeIsDark.value) }
    DisposableEffect(Unit) { onDispose { activity?.setStatusBarIcons(light = ThemeIsDark.value) } }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val h = home
        val stamp = updatedAt?.let { java.time.Instant.ofEpochMilli(it) }?.let { "${dayLabel(it)}, ${formatTime(it)}" } ?: "earlier"
        LazyColumn(Modifier.fillMaxSize(), state = list) {
            item {
                Hero(h, hidden, onToggle = { hidden = !hidden; store.balanceHidden = hidden }, onInbox = onInbox,
                    onVyna = if (AppConfig.on("vyna")) ({ onRoute("vyna") }) else null)
                QuickActions(offline = offline, onAction = onQuickAction, onOffline = { snacks?.show("You're offline. Try again when you're connected.") })
                when {
                    offline && h != null -> StatusBanner(true, "You're offline. These balances are from $stamp.", "Sending money works again once you're back online.", null,
                        Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    error != null && h != null -> StatusBanner(false, "Couldn't refresh. Showing balances from $stamp.", null, onRetry = { scope.launch { load() } },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
                if (AppConfig.on("insights")) id.orvyn.app.ui.insights.InsightCard(store, insight, onOpen = { onRoute("insights") })
            }
            if (h == null && (offline || error != null)) item {
                LoadError("Couldn't load your accounts", error ?: "You're offline. Check your connection and try again.", retrying = loading, onRetry = { scope.launch { load() } })
            }
            if (h != null || !(offline || error != null)) item { Eyebrow("Accounts") }
            if (h == null) { if (!(offline || error != null)) item { Skeleton(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(96.dp), shape = RoundedCornerShape(14.dp)) } }
            else items(h.accounts, key = { it.id }) { a ->
                val clipboard = LocalClipboardManager.current
                // The whole card opens the account (Q-F1); the copy icon stays its own target.
                OrvynCard(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), onClick = rememberOnce { onRoute("account") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(painterResource(R.drawable.ph_caret_right), null, Modifier.size(20.dp), tint = OrvynTheme.colors.textSecondary)
                    }
                    if (a.number != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(groupAccountNumber(a.number), style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { clipboard.setText(AnnotatedString(a.number)); onCopied("Account number copied") }) {
                            Icon(painterResource(R.drawable.ph_copy), contentDescription = "Copy account number", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    AmountText(if (hidden) HIDDEN else Money.format(a.balance), MaterialTheme.typography.titleLarge, minSize = MaterialTheme.typography.titleLarge.fontSize,
                        color = MaterialTheme.colorScheme.onSurface, contentDescription = if (hidden) "Balance hidden" else null)
                }
            }
            // A part with nothing to show and nothing cached isn't shown at all, eyebrow included (PO-24).
            wealth?.let { w -> if (h != null && listOf("fx", "deposits", "invest").any { AppConfig.on(it) }) item {
                Eyebrow("Wealth")
                id.orvyn.app.ui.wealth.WealthCard(w, onOpen = { onRoute("wealth") })
            } }
            h?.relationshipManager?.let { rm -> item {
                Eyebrow("Your Relationship Manager")
                id.orvyn.app.ui.wealth.RelationshipManagerCard(rm, Modifier.padding(horizontal = 20.dp))
            } }
            if (h != null && AppConfig.on("pockets")) item {
                Eyebrow("Pockets")
                id.orvyn.app.ui.pockets.PocketsStrip(h.pockets, hidden, onOpen = { onRoute("pocket/$it") }, onCreate = { onRoute("pocket/new") })
            }
            if (h != null || !(offline || error != null)) item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("RECENT", style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                    OrvynTextButton("See all", onClick = onSeeAll)
                }
            }
            if (h == null) { if (!(offline || error != null)) items(3) { Skeleton(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(48.dp)) } }
            else if (h.recent.isEmpty()) item {
                MessageState(R.drawable.ph_tray, "No transactions yet", "Money you send and receive shows up here.")
            } else items(h.recent, key = { it.id }) { t ->
                Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                    TransactionRow(t) { onOpenTransaction(t.id) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            // K1 (Q-G1): promotions come after the customer's own money.
            banners?.takeIf { it.isNotEmpty() }?.let { b -> item { Banners(b, onAction = onQuickAction) } }
            item { Spacer(Modifier.height(24.dp)) }
        }
        if (pastHero) Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.background))
    }
}

@Composable
private fun Eyebrow(text: String) = Text(
    text.uppercase(), style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary,
    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
)

@Composable
private fun Hero(h: Home?, hidden: Boolean, onToggle: () -> Unit, onInbox: () -> Unit, onVyna: (() -> Unit)? = null) {
    // Count the balance up only when it actually changes (design system: balance motion).
    val reduced = rememberReducedMotion()
    // Animate a 0..1 fraction and compute the Rupiah exactly: a Float can't hold Rp835,108,000 (it showed ...107,968).
    var from by remember { mutableLongStateOf(h?.totalBalance ?: 0L) }
    var to by remember { mutableLongStateOf(h?.totalBalance ?: 0L) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(h?.totalBalance) {
        val target = h?.totalBalance ?: return@LaunchedEffect
        if (target == to) return@LaunchedEffect
        from = if (to == 0L) target else to; to = target
        progress.snapTo(0f)
        if (reduced || from == to) progress.snapTo(1f) else progress.animateTo(1f, tween(600))
    }
    val shownBalance = from + ((to - from) * progress.value.toDouble()).toLong()
    val large = isLargeFont()
    Column(
        Modifier.fillMaxWidth().background(Brand.Hero).drawBehind {
            // S3.10: Prestige and Private get gold (Giallo) stripes and a gold edge
            val prestige = h != null && h.tier != "everyday"
            val w = 22.dp.toPx(); var x = 0f
            while (x < size.width) { drawRect(if (prestige) Brand.Giallo.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.05f), Offset(x, 0f), Size(w, size.height)); x += w * 2 }
            if (prestige) drawRect(Brand.Giallo, Offset(0f, size.height - 3.dp.toPx()), Size(size.width, 3.dp.toPx()))
        }.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val greet = h?.let { "${greeting()}, ${it.firstName}" } ?: greeting()
        val icons: @Composable () -> Unit = {
            if (onVyna != null) IconButton(onClick = onVyna) { Icon(painterResource(R.drawable.ph_sparkle), contentDescription = "Ask Vyna", tint = Color.White) }
            IconButton(onClick = onInbox) {
                Box {
                    Icon(painterResource(R.drawable.ph_bell), contentDescription = if ((h?.unread ?: 0) > 0) "Inbox, ${h!!.unread} unread" else "Inbox", tint = Color.White)
                    if ((h?.unread ?: 0) > 0) Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Brand.Giallo))
                }
            }
        }
        // Q-A5 AC2: at large font sizes the greeting gets its own full-width line and wraps by word.
        if (large) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (h != null) TierBadge(Tier.valueOf(h.tier.replaceFirstChar(Char::uppercase)))
                Spacer(Modifier.weight(1f)); icons()
            }
            Text(greet, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(end = 12.dp))
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            Text(greet, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
            if (h != null) TierBadge(Tier.valueOf(h.tier.replaceFirstChar(Char::uppercase)))
            icons()
        }
        Spacer(Modifier.height(12.dp))
        Text("Total balance", style = MaterialTheme.typography.bodyMedium, color = Brand.HeroTextMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (h == null) Skeleton(Modifier.size(200.dp, 40.dp), color = Color.White.copy(alpha = 0.16f))
            else AmountText(
                if (hidden) HIDDEN else Money.format(shownBalance), OrvynType.displayBalance, color = Color.White,
                fitTo = if (hidden) HIDDEN else Money.format(h.totalBalance), modifier = Modifier.weight(1f, fill = false),
                contentDescription = if (hidden) "Balance hidden" else Money.format(h.totalBalance),
            )
            IconButton(onClick = onToggle) {
                Icon(painterResource(if (hidden) R.drawable.ph_eye else R.drawable.ph_eye_slash),
                    contentDescription = if (hidden) "Show balances" else "Hide balances", tint = Brand.HeroTextMuted)
            }
        }
    }
}

/**
 * Story S1.8 with K1 (Q-G1, design.md 11.1): below Recent, auto-advancing every 6 seconds only when TalkBack is off,
 * animations are on, there are 2 or more, and nobody touched it in the last 6 seconds. The action pill runs its action; the
 * rest of the banner opens the one that was showing when the finger came down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Banners(banners: List<Banner>, onAction: (String) -> Unit) {
    var open by remember { mutableStateOf<Banner?>(null) }
    val context = LocalContext.current
    val pager = androidx.compose.foundation.pager.rememberPagerState { banners.size }
    val reduced = rememberReducedMotion()
    val talkBack = remember { context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true }
    var touchedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(pager.settledPage, touchedAt, open, reduced, talkBack, banners.size) {
        if (reduced || talkBack || open != null || banners.size < 2) return@LaunchedEffect
        delay(6_000)
        pager.animateScrollToPage((pager.settledPage + 1) % banners.size)
    }
    val large = isLargeFont()
    Column(Modifier.padding(top = 24.dp)) {
        androidx.compose.foundation.pager.HorizontalPager(pager, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp,
            modifier = Modifier.pointerInput(Unit) {
                // A finger on the carousel stops the auto-advance at once; a tap opens the page it landed on.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touchedAt = System.currentTimeMillis()
                }
            }) { i ->
            val b = banners[i]
            val action = b.action?.let { BANNER_ACTIONS[it] }
            BannerArt(b, Modifier.heightIn(min = 156.dp).semantics { stateDescription = "Banner ${i + 1} of ${banners.size}" }
                .clickable(role = Role.Button, onClickLabel = "Read more") { open = b }, fade = true, full = large) {
                Column(Modifier.fillMaxWidth(if (large) 1f else 0.66f).heightIn(min = 156.dp).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = bannerLabel(b.title, b.body) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(b.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = if (large) Int.MAX_VALUE else 2)
                        Text(b.body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f), maxLines = if (large) Int.MAX_VALUE else 2)
                    }
                    action?.let { (label, run) ->
                        // Its own target, 48dp tall to touch, 32dp to see: runs the action without opening the details.
                        Box(Modifier.padding(top = 8.dp).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = rememberOnce { onAction(run) }), contentAlignment = Alignment.CenterStart) {
                            Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(label, style = MaterialTheme.typography.labelMedium, color = Brand.Nerazzurro)
                                Icon(painterResource(R.drawable.ph_caret_right), contentDescription = null, tint = Brand.Nerazzurro, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
        if (banners.size > 1) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            banners.indices.forEach { i ->
                val w by androidx.compose.animation.core.animateDpAsState(if (i == pager.currentPage) 18.dp else 6.dp, label = "dot")
                Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(w).clip(RoundedCornerShape(50))
                    .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
    open?.let { b ->
        id.orvyn.app.ui.components.OrvynBottomSheet(onDismiss = { open = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (b.image != null) BannerArt(b, Modifier.aspectRatio(16f / 7f), fade = false) {}
                Text(b.title, style = MaterialTheme.typography.titleLarge)
                Text(b.detail, style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary)
                b.action?.let { a ->
                    Spacer(Modifier.height(4.dp))
                    id.orvyn.app.ui.components.OrvynButton(BANNER_ACTIONS[a]?.first ?: "Open", onClick = { open = null; onAction(BANNER_ACTIONS[a]?.second ?: return@OrvynButton) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Button text, and the home action it runs. */
private val BANNER_ACTIONS = mapOf(
    "transfer" to ("Send money" to "Transfer"), "qris" to ("Scan to pay" to "QRIS"), "pay" to ("Pay a bill" to "Pay"),
    "topup" to ("Top up" to "Top up"), "cards" to ("Open Cards" to "Cards"),
)

/**
 * The photo with a dark fade on the left so white text stays readable; brand colours while it loads or when there is no
 * photo. [full]: at large font sizes the text runs the full width over an even 86% Nero layer (design.md 5.5).
 */
@Composable
private fun BannerArt(b: Banner, modifier: Modifier, fade: Boolean = true, full: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val photo by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, b.image) {
        value = b.image?.let { id.orvyn.app.data.BannerImages.load(context, it) }
    }
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (photo != null) 1f else 0f, tween(250), label = "photo")
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (b.style == "black") androidx.compose.ui.graphics.Brush.linearGradient(listOf(Brand.Nero, Color(0xFF1A1B22))) else Brand.Hero)
        .drawBehind { val w = 16.dp.toPx(); var x = size.width * 0.7f; while (x < size.width) { drawRect(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Size(w, size.height)); x += w * 2 } }) {
        photo?.let {
            androidx.compose.foundation.Image(it, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.matchParentSize().graphicsLayer { this.alpha = alpha })
            if (fade) Box(Modifier.matchParentSize().background(if (full) androidx.compose.ui.graphics.SolidColor(Brand.Nero.copy(alpha = 0.86f)) else androidx.compose.ui.graphics.Brush.horizontalGradient(
                0f to Brand.Nero.copy(alpha = 0.86f), 0.5f to Brand.Nero.copy(alpha = 0.5f), 0.8f to Color.Transparent)))
        }
        content()
    }
}

/**
 * Four or five actions in a row; at large font sizes a grid with as many 112dp columns as fit (Q-A5 AC1). Offline they
 * stay tappable at 38% and explain themselves (Q-B6 AC2).
 */
@Composable
private fun QuickActions(offline: Boolean, onAction: (String) -> Unit, onOffline: () -> Unit) {
    val actions = listOf("Transfer" to R.drawable.ph_arrows_left_right, "QRIS" to R.drawable.ph_qr_code, "Pay" to R.drawable.ph_lightning, "Top up" to R.drawable.ph_plus) +
        (if (AppConfig.on("requests")) listOf("Request" to R.drawable.ph_hand_coins) else emptyList())
    val large = isLargeFont()
    val cell: @Composable (Modifier, String, Int) -> Unit = { m, label, icon ->
        Column(
            m.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = rememberOnce { if (offline) onOffline() else onAction(label) })
                .graphicsLayer { alpha = if (offline) 0.38f else 1f }
                .heightIn(min = 72.dp).padding(vertical = 6.dp, horizontal = 2.dp)
                .semantics(mergeDescendants = true) { contentDescription = if (offline) "$label, unavailable while offline" else label },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconTile(icon)
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
    val frame = Modifier.padding(horizontal = 20.dp).pullUp(36.dp)
        .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = Color(0xFF002080), spotColor = Color(0xFF002080))
        .clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(vertical = 12.dp)
    if (!large) Row(frame) { for ((label, icon) in actions) cell(Modifier.weight(1f), label, icon) }
    else BoxWithConstraints(frame.fillMaxWidth()) {
        val columns = (maxWidth / 112.dp).toInt().coerceIn(1, actions.size)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in actions.chunked(columns)) Row {
                for ((label, icon) in row) cell(Modifier.weight(1f), label, icon)
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Moves content up over the hero and gives the space back to the layout (offset alone would leave a gap). */
private fun Modifier.pullUp(by: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val dy = by.roundToPx()
    layout(p.width, p.height - dy) { p.place(0, -dy) }
}

/** "{title}. {body}" (design.md 11.1), without a second stop when the title already ends in one ("Scan. Pay. Done."). */
fun bannerLabel(title: String, body: String) = if (title.trimEnd().lastOrNull() in listOf('.', '!', '?')) "${title.trimEnd()} $body" else "$title. $body"
