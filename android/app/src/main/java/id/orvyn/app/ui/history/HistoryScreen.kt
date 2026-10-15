package id.orvyn.app.ui.history

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.Transaction
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.shareReceipt
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.ButtonKind
import id.orvyn.app.ui.components.IconTile
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OrvynTextButton
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.components.Status
import id.orvyn.app.ui.components.StatusChip
import id.orvyn.app.ui.dayLabel
import id.orvyn.app.ui.formatDateTime
import id.orvyn.app.ui.groupAccountNumber
import id.orvyn.app.ui.home.TransactionRow
import id.orvyn.app.ui.home.iconFor
import id.orvyn.app.ui.theme.OrvynTheme
import id.orvyn.app.ui.theme.OrvynType
import kotlinx.coroutines.delay

private enum class Filter(val label: String, val direction: String?) { All("All", null), In("Money in", "in"), Out("Money out", "out") }

/** Story S1.10: newest first, grouped by day, loads more while scrolling, search and in/out filter. */
@Composable
fun HistoryScreen(accountId: String?, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    val items = remember { mutableStateListOf<Transaction>() }
    var next by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()

    suspend fun page(reset: Boolean) {
        if (accountId == null) return
        loading = true
        try {
            val p = Api.transactions(accountId, before = if (reset) null else next, direction = filter.direction, query = query)
            if (reset) items.clear()
            items.addAll(p.items); next = p.next; error = null
        } catch (e: ApiException) { error = e.message } finally { loading = false }
    }
    LaunchedEffect(accountId, query, filter) {
        delay(if (query.isEmpty()) 0 else 350) // wait for typing to pause
        page(reset = true)
    }
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 5 } == true } }
    LaunchedEffect(nearEnd, next) { if (nearEnd && next != null && !loading) page(reset = false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Text("History", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp).semantics { heading() })
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search name, note or amount") },
            leadingIcon = { Icon(painterResource(R.drawable.ph_magnifying_glass), contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) { { IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ph_x), contentDescription = "Clear search") } } } else null,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline, unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (f in Filter.entries) FilterChip(f.label, f == filter) { filter = f }
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            var lastDay: String? = null
            for (t in items) {
                val day = dayLabel(t.at)
                if (day != lastDay) {
                    lastDay = day
                    item(key = "day-$day-${t.id}") {
                        Text(day.uppercase(), style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp).semantics { heading() })
                    }
                }
                item(key = t.id) {
                    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                        TransactionRow(t, withDay = false) { onOpen(t.id) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            when {
                loading && items.isEmpty() -> items(6) { Skeleton(Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().height(44.dp)) }
                error != null && items.isEmpty() -> item {
                    MessageState(R.drawable.ph_warning_circle, "Couldn't load your history", error!!)
                }
                !loading && items.isEmpty() -> item {
                    MessageState(R.drawable.ph_tray, if (query.isNotEmpty() || filter != Filter.All) "Nothing matches" else "No transactions yet",
                        if (query.isNotEmpty() || filter != Filter.All) "Try another name or amount, or clear the filter." else "Money you send and receive shows up here.")
                }
                loading -> item { Skeleton(Modifier.padding(20.dp).fillMaxWidth().height(44.dp)) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.clip(CircleShape).background(if (selected) c.primary else c.surface)
            .clickable(role = Role.Tab, onClick = onClick).semantics { this.selected = selected }
            .height(40.dp).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) c.onPrimary else c.onSurface) }
}

/** Story S1.11. */
@Composable
fun TransactionDetailScreen(id: String, account: String? = null, onBack: () -> Unit, onCopied: (String) -> Unit) {
    var t by remember { mutableStateOf<Transaction?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) { try { t = Api.transaction(id, account) } catch (e: ApiException) { error = e.message } }
    val clipboard = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
            Icon(painterResource(R.drawable.ph_arrow_left), contentDescription = "Back")
        }
        val tx = t
        when {
            error != null -> MessageState(R.drawable.ph_warning_circle, "Couldn't load this transaction", error!!)
            tx == null -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Skeleton(Modifier.size(56.dp)); Skeleton(Modifier.fillMaxWidth().height(40.dp)); Skeleton(Modifier.fillMaxWidth().height(200.dp))
            }
            else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconTile(iconFor(tx), size = 56.dp)
                    Text(tx.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text(
                        Money.signed(tx.amount),
                        style = OrvynType.displayBalance,
                        color = if (tx.amount > 0 && !tx.reversed) OrvynTheme.colors.success else MaterialTheme.colorScheme.onSurface,
                    )
                    StatusChip(if (tx.reversed) Status.Reversed else Status.Success)
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Field("Date", formatDateTime(tx.at))
                    tx.subtitle?.let { Field("Type", it) }
                    tx.note?.let { Field("Note", it) }
                    if (tx.fee > 0) Field("Fee", Money.format(tx.fee))
                    tx.accountNumber?.let { Field("Account", groupAccountNumber(it)) }
                    tx.reference?.let { ref ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Field("Reference", ref) }
                            OrvynTextButton("Copy", onClick = { clipboard.setText(AnnotatedString(ref)); onCopied("Reference copied") })
                        }
                    }
                }
                OrvynButton("Share receipt", kind = ButtonKind.Secondary,
                    onClick = { shareReceipt(context, tx) })
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String) = Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
    Text(value, style = MaterialTheme.typography.bodyLarge)
}
