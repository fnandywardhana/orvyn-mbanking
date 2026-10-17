package id.orvyn.app.ui.history

import kotlinx.coroutines.launch
import id.orvyn.app.data.DataVersion
import id.orvyn.app.ui.components.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.contentDescription
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import id.orvyn.app.ui.components.OrvynBottomSheet
import id.orvyn.app.ui.insights.CATEGORIES
import id.orvyn.app.ui.insights.categoryLabel
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
import id.orvyn.app.data.AppConfig
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

/** Q-F3 (PO-11): the six Type choices and the Journal Entry kinds behind each. */
val TYPES = linkedMapOf(
    "Transfers" to listOf("internal_transfer", "external_transfer"),
    "QRIS" to listOf("qris_payment"),
    "Bills" to listOf("bill_payment"),
    "Cards" to listOf("card_spend", "card_payment"),
    "Pockets" to listOf("pocket_move"),
    "Wealth" to listOf("fx_exchange", "deposit_open", "deposit_close", "deposit_interest", "investment", "bond_coupon"),
)
private val WIB = ZoneId.of("Asia/Jakarta")
private val SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.US)
private val LONG = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

/** [from] inclusive, [to] exclusive, both start-of-day WIB. Dates are ISO strings so the choice survives leaving and coming back. */
data class DateFilter(val key: String, val from: String? = null, val to: String? = null) : java.io.Serializable

fun dateRange(f: DateFilter, today: LocalDate = LocalDate.now(WIB)): Pair<LocalDate?, LocalDate?> = when (f.key) {
    "this" -> today.withDayOfMonth(1) to null
    "last" -> today.withDayOfMonth(1).minusMonths(1) to today.withDayOfMonth(1)
    "three" -> today.minusMonths(3) to null
    "pick" -> LocalDate.parse(f.from) to LocalDate.parse(f.to).plusDays(1)
    else -> null to null
}

/** "This month", or "3 Oct to 16 Oct" (with the year when it isn't this year). */
fun dateChipLabel(f: DateFilter, today: LocalDate = LocalDate.now(WIB)) = when (f.key) {
    "this" -> "This month"; "last" -> "Last month"; "three" -> "Last 3 months"
    "pick" -> {
        val a = LocalDate.parse(f.from); val b = LocalDate.parse(f.to)
        val fmt = if (a.year != today.year || b.year != today.year) LONG else SHORT
        "${fmt.format(a)} to ${fmt.format(b)}"
    }
    else -> "Date"
}

/** Story S1.10 and Q-F3: newest first by day, more while scrolling, search, Date, Type and in/out filters together. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(accountId: String?, onOpen: (String) -> Unit, onInsights: (() -> Unit)? = null) {
    var query by rememberSaveable { mutableStateOf("") }
    var direction by rememberSaveable { mutableStateOf<String?>(null) }
    var date by rememberSaveable { mutableStateOf(DateFilter("any")) }
    var types by rememberSaveable { mutableStateOf(listOf<String>()) }
    val items = remember { mutableStateListOf<Transaction>() }
    var next by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var moreError by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) } // "date", "type", "pick"
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val filtered = direction != null || date.key != "any" || types.isNotEmpty()

    suspend fun page(reset: Boolean) {
        if (accountId == null) return
        loading = true
        try {
            val (from, to) = dateRange(date)
            val p = Api.transactions(accountId, before = if (reset) null else next, direction = direction, query = query,
                from = from?.atStartOfDay(WIB)?.toInstant(), to = to?.atStartOfDay(WIB)?.toInstant(), kinds = types.flatMap { TYPES[it] ?: emptyList() })
            if (reset) items.clear()
            items.addAll(p.items); next = p.next; error = null; moreError = false
        } catch (e: ApiException) { if (reset && items.isEmpty()) error = e.message else moreError = true } finally { loading = false }
    }
    LaunchedEffect(accountId, query, direction, date, types) {
        delay(if (query.isEmpty()) 0 else 350) // wait for typing to pause
        page(reset = true)
    }
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 5 } == true } }
    LaunchedEffect(nearEnd, next) { if (nearEnd && next != null && !loading && !moreError) page(reset = false) }
    fun clear() { direction = null; date = DateFilter("any"); types = emptyList() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("History", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).padding(start = 20.dp, top = 16.dp, bottom = 12.dp).semantics { heading() })
            if (onInsights != null && AppConfig.on("insights")) OrvynTextButton("Insights", onClick = onInsights, modifier = Modifier.padding(end = 8.dp, top = 4.dp))
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search name, note or amount") },
            leadingIcon = { Icon(painterResource(R.drawable.ph_magnifying_glass), contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) { { IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ph_x), contentDescription = "Clear search") } } } else null,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline, unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically) {
            val dateLabel = dateChipLabel(date)
            Chip(dateLabel, date.key != "any", contentDescription = "Date filter, $dateLabel", role = Role.Button, trailingIcon = R.drawable.ph_caret_down) { sheet = "date" }
            val typeLabel = when (types.size) { 0 -> "Type"; 1 -> types.first(); else -> "${types.size} types" }
            Chip(typeLabel, types.isNotEmpty(), contentDescription = "Type filter, $typeLabel", role = Role.Button, trailingIcon = R.drawable.ph_caret_down) { sheet = "type" }
            Chip("Money in", direction == "in", role = Role.Checkbox) { direction = if (direction == "in") null else "in" }
            Chip("Money out", direction == "out", role = Role.Checkbox) { direction = if (direction == "out") null else "out" }
            if (filtered) OrvynTextButton("Clear filters", onClick = { clear() })
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; page(reset = true); refreshing = false } }, modifier = Modifier.fillMaxSize()) {
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
                    loading && items.isEmpty() -> item { Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "Loading" }) { repeat(6) { Skeleton(Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().height(44.dp)) } } }
                    error != null && items.isEmpty() -> item { LoadError("Couldn't load your history", error!!, retrying = loading, onRetry = { scope.launch { page(reset = true) } }) }
                    !loading && items.isEmpty() -> item {
                        if (query.isNotEmpty() || filtered) MessageState(R.drawable.ph_tray, "Nothing matches", "Try other dates or types, or clear the filters.",
                            action = if (filtered) ({ OrvynButton("Clear filters", onClick = { clear() }, kind = ButtonKind.Secondary, fill = false) }) else null)
                        else MessageState(R.drawable.ph_tray, "No transactions yet", "Money you send and receive shows up here.")
                    }
                    moreError -> item {
                        // Q-F3 AC5: the whole last row retries the page that didn't load.
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, enabled = !loading) { scope.launch { page(reset = false) } }.padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(painterResource(R.drawable.ph_warning_circle), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                            Text("Couldn't load more. Try again", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    loading -> item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    when (sheet) {
        "date" -> OrvynBottomSheet(onDismiss = { sheet = null }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text("Date", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() })
                for ((key, label) in listOf("any" to "Any time", "this" to "This month", "last" to "Last month", "three" to "Last 3 months", "pick" to "Pick dates")) {
                    val sel = date.key == key
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.RadioButton) {
                        if (key == "pick") sheet = "pick" else { date = DateFilter(key); sheet = null }
                    }.semantics { selected = sel }.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(sel, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
        "type" -> {
            var picked by remember { mutableStateOf(types) }
            OrvynBottomSheet(onDismiss = { sheet = null }) {
                Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                    Text("Type", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp).semantics { heading() })
                    for (t in TYPES.keys) {
                        val on = t in picked
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(on, role = Role.Checkbox) { picked = if (it) picked + t else picked - t },
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, onCheckedChange = null)
                            Text(t, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OrvynButton("Show results", onClick = { types = TYPES.keys.filter { it in picked }; sheet = null })
                    OrvynTextButton("Clear", onClick = { picked = emptyList() }, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
        "pick" -> {
            val today = LocalDate.now(WIB)
            val utc = { d: LocalDate -> d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }
            val state = rememberDateRangePickerState(
                initialSelectedStartDateMillis = date.from?.let { utc(LocalDate.parse(it)) }, initialSelectedEndDateMillis = date.to?.let { utc(LocalDate.parse(it)) },
                selectableDates = object : SelectableDates { override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= utc(today) },
            )
            DatePickerDialog(onDismissRequest = { sheet = null },
                confirmButton = { OrvynTextButton("Save", enabled = state.selectedStartDateMillis != null, onClick = {
                    val toDay = { m: Long -> java.time.Instant.ofEpochMilli(m).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() }
                    val start = state.selectedStartDateMillis!!
                    date = DateFilter("pick", toDay(start), toDay(state.selectedEndDateMillis ?: start)); sheet = null
                }) },
                dismissButton = { OrvynTextButton("Cancel", onClick = { sheet = null }) }) {
                DateRangePicker(state, title = { Text("Pick dates", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                    modifier = Modifier.heightIn(max = 520.dp))
            }
        }
    }
}

/** Story S1.11. */
@Composable
fun TransactionDetailScreen(id: String, account: String? = null, onBack: () -> Unit, onSplit: ((Long) -> Unit)? = null, onCopied: (String) -> Unit) {
    val load = rememberLoad(id) { Api.transaction(id, account) }
    val t = load.value
    val clipboard = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // S2.8 AC3: move this transaction to another spending category.
    // Q-B1: the sheet stays open and the old category stays ticked until the server agrees; a failure is said in the sheet.
    var saving by remember { mutableStateOf<String?>(null) }
    var sheetError by remember { mutableStateOf<String?>(null) }
    if (picking) OrvynBottomSheet(onDismiss = { picking = false; sheetError = null }) {
        Column(Modifier.padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Category", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp))
            for ((key, label, icon) in CATEGORIES) {
                ListRow(label, null, icon, onClick = {
                    if (saving != null) return@ListRow
                    saving = key; sheetError = null
                    scope.launch {
                        try { Api.setCategory(id, key); load.value = Api.transaction(id, account); DataVersion.count++; picking = false }
                        catch (e: ApiException) { sheetError = e.message }
                        saving = null
                    }
                }) {
                    if (saving == key) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else if (t?.spendCategory == key) Icon(painterResource(R.drawable.ph_check), contentDescription = "Current", tint = MaterialTheme.colorScheme.primary)
                }
            }
            sheetError?.let { ErrorBox(it, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
            Icon(painterResource(R.drawable.ph_arrow_left), contentDescription = "Back")
        }
        val tx = t
        when {
            tx == null && load.error != null -> LoadError("Couldn't load this transaction", load.error!!, load.busy, load::reload)
            tx == null -> Column(Modifier.padding(20.dp).semantics(mergeDescendants = true) { contentDescription = "Loading" }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    tx.spendCategory?.let { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Field("Category", categoryLabel(c)) }
                            OrvynTextButton("Change", onClick = { picking = true })
                        }
                    }
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
                // S2.7 AC5: a purchase can be split straight from its receipt; H-11: only when every share could meet the minimum.
                if (onSplit != null && -tx.amount >= AppConfig.minTransfer && tx.kind in setOf("qris_payment", "bill_payment", "card_spend") && AppConfig.on("requests"))
                    OrvynButton("Split this bill", kind = ButtonKind.Secondary, onClick = { onSplit(-tx.amount) }, modifier = Modifier.padding(top = 10.dp))
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
