package id.orvyn.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.DataVersion
import id.orvyn.app.data.DeviceStore
import id.orvyn.app.data.Home
import id.orvyn.app.data.Transaction
import id.orvyn.app.data.parseHome
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.components.IconTile
import id.orvyn.app.ui.components.ListRow
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.MoneyText
import id.orvyn.app.ui.components.OrvynCard
import id.orvyn.app.ui.components.OrvynTextButton
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.components.Tier
import id.orvyn.app.ui.components.TierBadge
import id.orvyn.app.ui.components.rememberReducedMotion
import id.orvyn.app.ui.formatTime
import id.orvyn.app.ui.dayLabel
import id.orvyn.app.ui.greeting
import id.orvyn.app.ui.groupAccountNumber
import id.orvyn.app.ui.theme.Brand
import id.orvyn.app.ui.theme.OrvynTheme
import id.orvyn.app.ui.theme.OrvynType
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Icon for a transaction row, by kind. */
fun iconFor(t: Transaction) = when (t.kind) {
    "qris_payment" -> R.drawable.ph_qr_code
    "bill_payment" -> R.drawable.ph_lightning
    "internal_transfer", "external_transfer", "reversal" -> R.drawable.ph_arrows_left_right
    else -> R.drawable.ph_arrows_left_right
}

/** "QRIS · Yesterday, 16:16"; lists already grouped by day pass [withDay] = false for "QRIS · 16:16". */
fun subtitleFor(t: Transaction, withDay: Boolean = true) =
    listOfNotNull(t.subtitle, if (withDay) "${dayLabel(t.at)}, ${formatTime(t.at)}" else formatTime(t.at)).joinToString(" · ")

@Composable
fun TransactionRow(t: Transaction, withDay: Boolean = true, onClick: () -> Unit) =
    ListRow(t.title, subtitleFor(t, withDay), iconFor(t), onClick = onClick) { MoneyText(t.amount, struck = t.reversed) }

/** Story S1.7. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    store: DeviceStore,
    onSeeAll: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onQuickAction: (String) -> Unit,
    onCopied: (String) -> Unit,
    onInbox: () -> Unit = {},
) {
    var home by remember { mutableStateOf(store.lastHome?.second?.let { runCatching { parseHome(JSONObject(it)) }.getOrNull() }) }
    var updatedAt by remember { mutableStateOf(store.lastHome?.first) }
    var offline by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(store.balanceHidden) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            val json = Api.homeJson()
            home = parseHome(json)
            offline = false; error = null
            updatedAt = System.currentTimeMillis()
            store.lastHome = updatedAt!! to json.toString()
        } catch (e: ApiException) {
            if (e.code == "offline") offline = true else error = e.message
        }
    }
    LaunchedEffect(DataVersion.count) { load() } // reloads after money moves
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

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val h = home
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Hero(h, hidden, onToggle = { hidden = !hidden; store.balanceHidden = hidden }, onInbox = onInbox)
                QuickActions(enabled = !offline, onAction = onQuickAction)
                Banners(onAction = onQuickAction)
            }
            if (offline) item {
                Text(
                    "You're offline. Showing balances from ${updatedAt?.let { formatTime(java.time.Instant.ofEpochMilli(it)) } ?: "earlier"}.",
                    style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.warning,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(10.dp)).background(OrvynTheme.colors.warningContainer).padding(12.dp),
                )
            }
            if (h == null && error != null) item {
                MessageState(R.drawable.ph_warning_circle, "Couldn't load your accounts", error!!,
                    action = { OrvynTextButton("Try again", onClick = { scope.launch { load() } }) })
            }
            item { Eyebrow("Accounts") }
            if (h == null) item { Skeleton(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(96.dp)) }
            else items(h.accounts, key = { it.id }) { a ->
                val clipboard = LocalClipboardManager.current
                OrvynCard(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(a.name, style = MaterialTheme.typography.titleMedium)
                    if (a.number != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(groupAccountNumber(a.number), style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { clipboard.setText(AnnotatedString(a.number)); onCopied("Account number copied") }) {
                            Icon(painterResource(R.drawable.ph_copy), contentDescription = "Copy account number", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(if (hidden) "Rp••••••" else Money.format(a.balance), style = MaterialTheme.typography.titleLarge)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("RECENT", style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                    OrvynTextButton("See all", onClick = onSeeAll)
                }
            }
            if (h == null) items(3) { Skeleton(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(48.dp)) }
            else if (h.recent.isEmpty()) item {
                MessageState(R.drawable.ph_tray, "No transactions yet", "Money you send and receive shows up here.")
            } else items(h.recent, key = { it.id }) { t ->
                Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                    TransactionRow(t) { onOpenTransaction(t.id) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Eyebrow(text: String) = Text(
    text.uppercase(), style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary,
    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
)

@Composable
private fun Hero(h: Home?, hidden: Boolean, onToggle: () -> Unit, onInbox: () -> Unit) {
    id.orvyn.app.ui.DarkTopBar()
    // Count the balance up only when it actually changes (design system: balance motion).
    val reduced = rememberReducedMotion()
    val shown = remember { Animatable(h?.totalBalance?.toFloat() ?: 0f) }
    LaunchedEffect(h?.totalBalance) {
        val target = h?.totalBalance?.toFloat() ?: return@LaunchedEffect
        if (reduced || shown.value == 0f) shown.snapTo(target) else shown.animateTo(target, tween(600))
    }
    Column(
        Modifier.fillMaxWidth().background(Brand.Hero).drawBehind {
            val w = 22.dp.toPx(); var x = 0f
            while (x < size.width) { drawRect(Color.White.copy(alpha = 0.05f), Offset(x, 0f), Size(w, size.height)); x += w * 2 }
        }.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(h?.let { "${greeting()}, ${it.firstName}" } ?: greeting(), style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
            if (h != null) TierBadge(Tier.valueOf(h.tier.replaceFirstChar(Char::uppercase)))
            IconButton(onClick = onInbox) {
                Box {
                    Icon(painterResource(R.drawable.ph_bell), contentDescription = if ((h?.unread ?: 0) > 0) "Inbox, ${h!!.unread} unread" else "Inbox", tint = Color.White)
                    if ((h?.unread ?: 0) > 0) Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Brand.Giallo))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Total balance", style = MaterialTheme.typography.bodyMedium, color = Brand.HeroTextMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (h == null) "Rp—" else if (hidden) "Rp••••••••" else Money.format(shown.value.toLong()),
                style = OrvynType.displayBalance, color = Color.White, modifier = Modifier.weight(1f, fill = false),
            )
            IconButton(onClick = onToggle) {
                Icon(painterResource(if (hidden) R.drawable.ph_eye else R.drawable.ph_eye_slash),
                    contentDescription = if (hidden) "Show balances" else "Hide balances", tint = Brand.HeroTextMuted)
            }
        }
    }
}

/** Story S1.8: banners from the admin, swipeable, tap for details. Nothing at all when there are none. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Banners(onAction: (String) -> Unit) {
    var banners by remember { mutableStateOf<List<id.orvyn.app.data.Banner>>(emptyList()) }
    var open by remember { mutableStateOf<id.orvyn.app.data.Banner?>(null) }
    LaunchedEffect(Unit) { runCatching { banners = Api.content().banners } }
    if (banners.isEmpty()) return
    val pager = androidx.compose.foundation.pager.rememberPagerState { banners.size }
    val reduced = rememberReducedMotion()
    // Moves on every 6 seconds, like most banking apps; waits while the customer swipes or reads a banner.
    // Keyed on drag, not isScrollInProgress: the auto-scroll itself sets that and would cancel itself.
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(pager.settledPage, dragged, open, reduced) {
        if (reduced || open != null || dragged || banners.size < 2) return@LaunchedEffect
        kotlinx.coroutines.delay(6_000)
        pager.animateScrollToPage((pager.settledPage + 1) % banners.size)
    }
    androidx.compose.foundation.pager.HorizontalPager(pager, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp,
        modifier = Modifier.padding(top = 8.dp)) { i ->
        val b = banners[i]
        BannerArt(b, Modifier.height(156.dp).clickable(role = Role.Button, onClickLabel = "Read more") { open = b }) {
            Column(Modifier.fillMaxWidth(0.66f).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(b.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2)
                    Text(b.body, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.88f), maxLines = 2)
                }
                b.action?.let { a ->
                    Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(BANNER_ACTIONS[a]?.first ?: "Open", style = MaterialTheme.typography.labelMedium, color = Brand.Nerazzurro)
                        Icon(painterResource(R.drawable.ph_caret_right), contentDescription = null, tint = Brand.Nerazzurro, modifier = Modifier.size(14.dp))
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

/** The photo with a dark fade on the left so white text stays readable; brand colours while it loads or when there is no photo. */
@Composable
private fun BannerArt(b: id.orvyn.app.data.Banner, modifier: Modifier, fade: Boolean = true, content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val photo by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, b.image) {
        value = b.image?.let { id.orvyn.app.data.BannerImages.load(context, it) }
    }
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (photo != null) 1f else 0f, tween(250), label = "photo")
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (b.style == "black") androidx.compose.ui.graphics.Brush.linearGradient(listOf(Brand.Nero, Color(0xFF1A1B22))) else Brand.Hero)
        .drawBehind { val w = 16.dp.toPx(); var x = size.width * 0.7f; while (x < size.width) { drawRect(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Size(w, size.height)); x += w * 2 } }) {
        photo?.let {
            androidx.compose.foundation.Image(it, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha })
            if (fade) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.horizontalGradient(
                0f to Brand.Nero.copy(alpha = 0.86f), 0.5f to Brand.Nero.copy(alpha = 0.5f), 0.8f to Color.Transparent)))
        }
        content()
    }
}

@Composable
private fun QuickActions(enabled: Boolean, onAction: (String) -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp).pullUp(36.dp)
            .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = Color(0xFF002080), spotColor = Color(0xFF002080))
            .clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(vertical = 12.dp),
    ) {
        for ((label, icon) in listOf("Transfer" to R.drawable.ph_arrows_left_right, "QRIS" to R.drawable.ph_qr_code, "Pay" to R.drawable.ph_lightning, "Top up" to R.drawable.ph_plus)) {
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, role = Role.Button) { onAction(label) }
                    .padding(vertical = 6.dp).semantics { contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconTile(icon)
                Text(label, style = MaterialTheme.typography.labelMedium)
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
