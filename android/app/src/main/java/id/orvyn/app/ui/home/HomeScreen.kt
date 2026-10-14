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

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val h = home
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Hero(h, hidden, onToggle = { hidden = !hidden; store.balanceHidden = hidden })
                QuickActions(enabled = !offline, onAction = onQuickAction)
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
private fun Hero(h: Home?, hidden: Boolean, onToggle: () -> Unit) {
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
            Spacer(Modifier.width(4.dp))
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
