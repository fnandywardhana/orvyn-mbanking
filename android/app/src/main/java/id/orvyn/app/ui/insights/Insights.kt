package id.orvyn.app.ui.insights

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.Budget
import id.orvyn.app.data.DataVersion
import id.orvyn.app.data.DeviceStore
import id.orvyn.app.data.Insights
import id.orvyn.app.data.Transaction
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.components.AmountDisplay
import id.orvyn.app.ui.components.LoadContent
import id.orvyn.app.ui.components.rememberLoad
import id.orvyn.app.ui.components.AmountKeypad
import id.orvyn.app.ui.components.ButtonKind
import id.orvyn.app.ui.components.Chip
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OrvynBottomSheet
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.OrvynTextButton
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.home.TransactionRow
import id.orvyn.app.ui.pockets.GoalBar
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale

// Stories S2.8–S2.11: where the money went, budgets, and the home screen card.

val CATEGORIES = listOf(
    Triple("food", "Food and drinks", R.drawable.ph_fork_knife), Triple("transport", "Transport", R.drawable.ph_bus),
    Triple("shopping", "Shopping", R.drawable.ph_shopping_bag), Triple("bills", "Bills", R.drawable.ph_lightning),
    Triple("entertainment", "Fun", R.drawable.ph_film_strip), Triple("transfer", "Transfers", R.drawable.ph_arrows_left_right),
    Triple("other", "Other", R.drawable.ph_dots_three_circle),
)
@DrawableRes fun categoryIcon(key: String) = CATEGORIES.firstOrNull { it.first == key }?.third ?: R.drawable.ph_dots_three_circle
fun categoryLabel(key: String) = CATEGORIES.firstOrNull { it.first == key }?.second ?: "Other"

private fun monthShort(key: String) = YearMonth.parse(key).month.getDisplayName(TextStyle.SHORT, Locale.US)
private fun monthLong(key: String) = YearMonth.parse(key).month.getDisplayName(TextStyle.FULL, Locale.US)

@Composable
private fun CategoryTile(key: String) = Box(
    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center,
) { Icon(painterResource(categoryIcon(key)), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp)) }

/** One series, one hue: this month in the brand colour, earlier months quieter. Tap a bar to read its total. */
@Composable
private fun MonthBars(months: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    var selected by remember(months) { mutableIntStateOf(months.lastIndex) }
    val strong = MaterialTheme.colorScheme.primary
    // Opaque, so the square base drawn over the rounded bar doesn't show a darker seam.
    val quiet = OrvynTheme.colors.chartBarMuted // Q-E11: 3.50:1 (light), 3.36:1 (dark) against the card
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val max = (months.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    val (key, total) = months[selected]
    Column(modifier.semantics {
        contentDescription = "Spending by month: " + months.joinToString(", ") { "${monthLong(it.first)} ${Money.format(it.second)}" }
    }) {
        Text(if (selected == months.lastIndex) "${monthLong(key)} so far" else monthLong(key), style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
        Text(Money.format(total), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp).pointerInput(months) {
            detectTapGestures { o -> selected = (o.x / (size.width / months.size)).toInt().coerceIn(0, months.lastIndex) }
        }) {
            val slot = size.width / months.size
            val barW = slot * 0.5f
            val r = 4.dp.toPx()
            months.forEachIndexed { i, (_, v) ->
                val h = (size.height - 2.dp.toPx()) * v / max
                if (h <= 0f) return@forEachIndexed
                val x = slot * i + (slot - barW) / 2
                val c = if (i == selected) strong else quiet
                // Rounded top, square bottom on the baseline.
                drawRoundRect(c, Offset(x, size.height - h), Size(barW, h), CornerRadius(r, r))
                drawRect(c, Offset(x, size.height - minOf(h, r)), Size(barW, minOf(h, r)))
            }
            drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            months.forEachIndexed { i, (k, _) ->
                Text(monthShort(k), style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal),
                    color = if (i == selected) MaterialTheme.colorScheme.onSurface else OrvynTheme.colors.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) = id.orvyn.app.ui.components.OrvynCard { content() }

@Composable
private fun Eyebrow(text: String) = Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary,
    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))

/** Against the same days of last month (the server compares like with like). */
private fun changeText(change: Int?, lastMonth: String) = when {
    change == null -> "None by now in ${monthShort(lastMonth)}"
    change > 0 -> "$change% more than by now in ${monthShort(lastMonth)}"
    change < 0 -> "${-change}% less than by now in ${monthShort(lastMonth)}"
    else -> "Same as by now in ${monthShort(lastMonth)}"
}

@Composable
fun InsightsScreen(onBack: () -> Unit, onCategory: (String) -> Unit) {
    val load = rememberLoad(DataVersion.count) { Api.insights() }
    var editing by remember { mutableStateOf<String?>(null) } // category whose budget is being set; "" = pick one
    val data = load.value
    FlowScreen("Insights", "What you spent from ORVYN Everyday and your cards.", onBack) {
        LoadContent(load, "Couldn't load your insights", skeleton = { Skeleton(Modifier.fillMaxWidth().height(220.dp)) }) { d ->
            LazyColumn {
                item { Card { MonthBars(d.months) } }
                val monthTotal = d.categories.sumOf { it.total }.coerceAtLeast(1)
                val lastMonth = YearMonth.parse(d.month).minusMonths(1).toString()
                item { Eyebrow("${monthLong(d.month)} by category") }
                if (d.categories.none { it.total > 0 }) item {
                    MessageState(R.drawable.ph_chart_bar, "No spending yet this month", "Payments, transfers and card purchases show up here by category.")
                }
                items(d.categories.filter { it.total > 0 }, key = { it.key }) { c ->
                    Row(Modifier.fillMaxWidth().clickable { onCategory(c.key) }.heightIn(min = 64.dp).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategoryTile(c.key)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row {
                                Text(c.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text(Money.format(c.total), style = MaterialTheme.typography.titleMedium)
                            }
                            GoalBar(c.total.toFloat() / monthTotal)
                            Text("${c.total * 100 / monthTotal}% of spending · ${changeText(c.change, lastMonth)}",
                                style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { Eyebrow("Budgets") }
                        OrvynTextButton("Add a budget", onClick = { editing = "" })
                    }
                }
                if (d.budgets.isEmpty()) item {
                    Text("Set a monthly limit for a category. ORVYN reminds you at 80% and 100%. Payments are never blocked.",
                        style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                }
                items(d.budgets, key = { "b" + it.category }) { b -> BudgetRow(b) { editing = b.category } }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    editing?.let { cat -> BudgetSheet(cat.ifEmpty { null }, data?.budgets?.firstOrNull { it.category == cat }?.limit, onDismiss = { editing = null }) { load.reload() } }
}

@Composable
private fun BudgetRow(b: Budget, onEdit: () -> Unit) {
    val used = (b.spent * 100 / b.limit).toInt()
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Change budget") { onEdit() }.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryTile(b.category)
            Column(Modifier.weight(1f)) {
                Text(b.label, style = MaterialTheme.typography.titleMedium)
                Text("${Money.format(b.spent)} of ${Money.format(b.limit)}", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
            }
            // Status in words and an icon, never colour alone.
            if (used >= 80) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(R.drawable.ph_warning_circle), null, Modifier.size(16.dp),
                    tint = if (used >= 100) MaterialTheme.colorScheme.error else OrvynTheme.colors.warning)
                Text(if (used >= 100) "Over" else "$used%", style = MaterialTheme.typography.labelMedium,
                    color = if (used >= 100) MaterialTheme.colorScheme.error else OrvynTheme.colors.warning)
            } else Text("$used%", style = MaterialTheme.typography.labelMedium, color = OrvynTheme.colors.textSecondary)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outlineVariant)) {
            Box(Modifier.fillMaxWidth((used / 100f).coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(50)).background(
                when { used >= 100 -> MaterialTheme.colorScheme.error; used >= 80 -> OrvynTheme.colors.warning; else -> MaterialTheme.colorScheme.primary }))
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetSheet(initialCategory: String?, current: Long?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var category by remember { mutableStateOf(initialCategory) }
    var limit by remember { mutableLongStateOf(current ?: 0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun save(value: Long?) = scope.launch {
        busy = true
        try { Api.setBudget(category!!, value); onSaved(); onDismiss() } catch (e: ApiException) { error = e.message }
        busy = false; confirm = false
    }
    OrvynBottomSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (initialCategory == null) "Add a budget" else "${categoryLabel(initialCategory)} budget", style = MaterialTheme.typography.titleLarge)
            if (initialCategory == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((key, label) in CATEGORIES.map { it.first to it.second }) Chip(label, category == key) { category = key }
            }
            Text("Each month", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
            AmountDisplay(limit, null, null, onQuickPick = { limit = it })
            AmountKeypad(limit, { limit = it; error = null })
            error?.let { id.orvyn.app.ui.components.ErrorBox(it) }
            OrvynButton("Save budget", enabled = category != null && limit >= 10_000, loading = busy && !confirm, onClick = { save(limit) }, modifier = Modifier.fillMaxWidth())
            if (current != null) {
                Spacer(Modifier.height(12.dp)) // 24dp from the primary action (Q-C5 AC4)
                OrvynButton("Remove budget", kind = ButtonKind.DestructiveText, onClick = { confirm = true }, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
    if (confirm && initialCategory != null) id.orvyn.app.ui.components.ConfirmDialog("Remove your ${categoryLabel(initialCategory)} budget?", "Reminders for this category stop.", "Remove",
        destructive = true, busy = busy, onDismiss = { confirm = false }, onConfirm = { save(null) })
}

/** S2.9 AC3: this month's transactions in one category. */
@Composable
fun CategoryScreen(key: String, accountId: String?, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val load = rememberLoad(key) {
        val start = LocalDate.now(ZoneId.of("Asia/Jakarta")).withDayOfMonth(1).atStartOfDay(ZoneId.of("Asia/Jakarta")).toInstant()
        Api.transactions(accountId ?: "", category = key, from = start).items
    }
    FlowScreen(categoryLabel(key), "This month, from ORVYN Everyday.", onBack) {
        LoadContent(load, "Couldn't load these", skeleton = { Skeleton(Modifier.fillMaxWidth().height(160.dp)) }) { l ->
            // Q-E11 AC3: says which category, and nothing about credit cards.
            if (l.isEmpty()) MessageState(R.drawable.ph_tray, "No ${categoryLabel(key)} spending this month.", "")
            else LazyColumn { items(l, key = { it.id }) { t -> TransactionRow(t) { onOpen(t.id) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) } }
        }
    }
}

/** S2.11: one sentence on the home screen. Closing it hides it until next week. */
@Composable
fun InsightCard(store: DeviceStore, text: String?, onOpen: () -> Unit) {
    val week = remember { LocalDate.now(ZoneId.of("Asia/Jakarta")).let { "${it.get(IsoFields.WEEK_BASED_YEAR)}-W${it.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}" } }
    var dismissed by remember { mutableStateOf(store.insightDismissedWeek == week) }
    val t = text ?: return
    if (dismissed) return
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer).clickable(onClickLabel = "Open insights") { onOpen() }.padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ph_chart_bar), null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Column(Modifier.weight(1f)) {
            Text("This week", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        IconButton(onClick = { store.insightDismissedWeek = week; dismissed = true }) {
            Icon(painterResource(R.drawable.ph_x), contentDescription = "Hide until next week", tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

