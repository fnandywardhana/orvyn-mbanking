package id.orvyn.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.DeviceStore
import id.orvyn.app.data.Home
import id.orvyn.app.data.Transaction
import id.orvyn.app.data.parseHome
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.components.AmountText
import id.orvyn.app.ui.components.BackBar
import id.orvyn.app.ui.components.ButtonKind
import id.orvyn.app.ui.components.InlineLoadError
import id.orvyn.app.ui.components.LoadError
import id.orvyn.app.ui.components.LocalSnacks
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.OrvynCard
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.components.StatusBanner
import id.orvyn.app.ui.components.isLargeFont
import id.orvyn.app.ui.dayLabel
import id.orvyn.app.ui.formatTime
import id.orvyn.app.ui.groupAccountNumber
import id.orvyn.app.ui.theme.OrvynTheme
import id.orvyn.app.ui.theme.OrvynType
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Q-F1 (S1.9, design.md 10.1): ORVYN Everyday with its number in groups of four, balance, opening date, Share account
 * details, Copy number and the account's own history. Offline it shows the last data with the offline banner, and Share
 * and Copy still work. A hidden balance stays hidden here (PO-9).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(store: DeviceStore, onBack: () -> Unit, onOpenTransaction: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snacks = LocalSnacks.current
    val scope = rememberCoroutineScope()
    var home by remember { mutableStateOf<Home?>(store.lastHome?.second?.let { runCatching { parseHome(JSONObject(it)) }.getOrNull() }) }
    var updatedAt by remember { mutableStateOf(store.lastHome?.first) }
    var homeError by remember { mutableStateOf<ApiException?>(null) }
    var hidden by remember { mutableStateOf(store.balanceHidden) }
    val items = remember { mutableStateListOf<Transaction>() }
    var next by remember { mutableStateOf<String?>(null) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var loadingPage by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    suspend fun page(reset: Boolean) {
        val id = home?.accounts?.firstOrNull()?.id ?: return
        loadingPage = true
        try {
            val p = Api.transactions(id, before = if (reset) null else next)
            if (reset) items.clear()
            items.addAll(p.items); next = p.next; pageError = null
        } catch (e: ApiException) { pageError = e.message } finally { loadingPage = false }
    }
    suspend fun load() {
        busy = true
        try {
            val json = Api.homeJson()
            home = parseHome(json); homeError = null
            updatedAt = System.currentTimeMillis()
            store.lastHome = updatedAt!! to json.toString()
        } catch (e: ApiException) { homeError = e }
        page(reset = true)
        busy = false
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(Modifier.padding(horizontal = 20.dp)) { BackBar(onBack) }
        val h = home
        val a = h?.accounts?.firstOrNull()
        if (a == null) {
            when (val e = homeError) {
                null -> Column(Modifier.padding(20.dp).semantics(mergeDescendants = true) { contentDescription = "Loading" }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Skeleton(Modifier.fillMaxWidth().height(180.dp)); Skeleton(Modifier.fillMaxWidth().height(52.dp)); Skeleton(Modifier.fillMaxWidth().height(52.dp))
                    repeat(3) { Skeleton(Modifier.fillMaxWidth().height(48.dp)) }
                }
                else -> LoadError("Couldn't load your account", e.message, retrying = busy, onRetry = { scope.launch { load() } })
            }
            return@Column
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(a.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                        val stamp = updatedAt?.let { Instant.ofEpochMilli(it) }?.let { "${dayLabel(it)}, ${formatTime(it)}" } ?: "earlier"
                        homeError?.let { e ->
                            if (e.code == "offline") StatusBanner(true, "You're offline. These details are from $stamp.", null, null)
                            else StatusBanner(false, "Couldn't refresh. Showing balances from $stamp.", null, onRetry = { scope.launch { load() } })
                        }
                        OrvynCard {
                            DetailLine("Account holder", h.fullName)
                            DetailLine("Account number", groupAccountNumber(a.number ?: ""), said = (a.number ?: "").toList().joinToString(" "))
                            val large = isLargeFont()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { if (hidden) contentDescription = "Balance, Balance hidden" }) {
                                    Text("Balance", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                                    AmountText(if (hidden) "Rp••••••••" else Money.format(a.balance), if (large) MaterialTheme.typography.titleLarge else OrvynType.displayBalance.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize),
                                        color = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { hidden = !hidden; store.balanceHidden = hidden }) {
                                    Icon(painterResource(if (hidden) R.drawable.ph_eye else R.drawable.ph_eye_slash), contentDescription = if (hidden) "Show balance" else "Hide balance",
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            a.openedOn?.let {
                                Text("Opened on ${DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US).format(it.atZone(java.time.ZoneId.of("Asia/Jakarta")))}",
                                    style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                            }
                        }
                        OrvynButton("Share account details", onClick = {
                            context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "${h.fullName} · ORVYN Bank · ${groupAccountNumber(a.number ?: "")}")
                            }, "Share account details"))
                        })
                        OrvynButton("Copy number", kind = ButtonKind.Secondary, onClick = {
                            clipboard.setText(AnnotatedString(a.number ?: "")); snacks?.show("Account number copied")
                        })
                        Text("HISTORY", style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    }
                }
                var lastDay: String? = null
                for (t in items) {
                    val day = dayLabel(t.at)
                    if (day != lastDay) {
                        lastDay = day
                        item(key = "day-$day-${t.id}") {
                            Text(day.uppercase(), style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary,
                                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp).semantics { heading() })
                        }
                    }
                    item(key = t.id) {
                        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                            TransactionRow(t, withDay = false) { onOpenTransaction(t.id) }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                when {
                    pageError != null -> item {
                        if (items.isEmpty() && homeError?.code == "offline") Spacer(Modifier.height(0.dp))
                        else InlineLoadError("Couldn't load more. Try again", retrying = loadingPage, onRetry = { scope.launch { page(reset = items.isEmpty()) } },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                    items.isEmpty() && !loadingPage -> item { MessageState(R.drawable.ph_tray, "No transactions yet", "Money you send and receive shows up here.") }
                    next != null -> item {
                        LaunchedEffect(next) { if (!loadingPage) page(reset = false) }
                        Skeleton(Modifier.padding(20.dp).fillMaxWidth().height(44.dp))
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** At large font sizes the label sits above the value; TalkBack reads them as one ("Account number, 4 0 6 8 …"). */
@Composable
private fun DetailLine(label: String, value: String, said: String? = null) {
    val content: @Composable () -> Unit = {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary, modifier = if (isLargeFont()) Modifier else Modifier.weight1())
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
    val m = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { if (said != null) contentDescription = "$label, $said" }
    if (isLargeFont()) Column(m) { content() } else Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

private fun Modifier.weight1() = this.then(Modifier.fillMaxWidth(0.45f))
