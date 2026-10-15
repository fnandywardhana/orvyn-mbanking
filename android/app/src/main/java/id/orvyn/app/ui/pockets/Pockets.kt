package id.orvyn.app.ui.pockets

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.DataVersion
import id.orvyn.app.data.Pocket
import id.orvyn.app.data.Transaction
import id.orvyn.app.data.parseHome
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.components.AmountDisplay
import id.orvyn.app.ui.components.AmountKeypad
import id.orvyn.app.ui.components.ButtonKind
import id.orvyn.app.ui.components.Chip
import id.orvyn.app.ui.components.ConfirmDialog
import id.orvyn.app.ui.components.ListRow
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OrvynBottomSheet
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.OrvynTextButton
import id.orvyn.app.ui.components.OrvynTextField
import id.orvyn.app.ui.components.ReceiptLine
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.components.SuccessScreen
import id.orvyn.app.ui.home.TransactionRow
import id.orvyn.app.ui.theme.OrvynTheme
import id.orvyn.app.ui.theme.OrvynType
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// Stories S2.1–S2.3: Pockets.

private val IMAGES = listOf(
    Triple("travel", "Travel", R.drawable.ph_airplane_tilt), Triple("home", "Home", R.drawable.ph_house),
    Triple("car", "Car", R.drawable.ph_car), Triple("study", "Study", R.drawable.ph_graduation_cap),
    Triple("wedding", "Wedding", R.drawable.ph_heart), Triple("gadget", "Gadget", R.drawable.ph_device_mobile),
    Triple("safety", "Rainy day", R.drawable.ph_umbrella), Triple("gift", "Gift", R.drawable.ph_gift),
)

@DrawableRes fun pocketIcon(image: String) = IMAGES.firstOrNull { it.first == image }?.third ?: R.drawable.ph_piggy_bank

private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val dateText = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

fun ruleSummary(rule: JSONObject?): String = when (rule?.optString("type")) {
    "payday" -> if (rule.has("percent")) "${rule.getInt("percent")}% of every pay day" else "${Money.format(rule.getLong("amount"))} every pay day"
    "weekly" -> "${Money.format(rule.getLong("amount"))} every ${DAYS[rule.getInt("day") - 1]}"
    "roundup" -> "Rounds up each payment to the next Rp10,000"
    else -> "Off"
}

@Composable
private fun PocketTile(@DrawableRes icon: Int, size: Int = 40) = Box(
    Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
    contentAlignment = Alignment.Center,
) { Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size((size * 0.55f).dp)) }

@Composable
fun GoalBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(50))
            .background(if (fraction >= 1f) OrvynTheme.colors.success else MaterialTheme.colorScheme.primary))
    }
}

private fun goalLine(p: Pocket) = p.target?.let { t ->
    val pct = (p.balance * 100 / t).coerceAtMost(100)
    "$pct% of ${Money.format(t)}" + (p.targetDate?.let { " · by ${LocalDate.parse(it).format(dateText)}" } ?: "")
}

/** Home screen strip: one card per pocket and a "New pocket" card at the end. */
@Composable
fun PocketsStrip(pockets: List<Pocket>, hidden: Boolean, onOpen: (String) -> Unit, onCreate: () -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(pockets, key = { it.id }) { p ->
            Column(
                Modifier.width(164.dp).height(144.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { onOpen(p.id) }.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PocketTile(pocketIcon(p.image), 36)
                Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(if (hidden) "Rp••••••" else Money.format(p.balance), style = MaterialTheme.typography.titleMedium)
                p.progress?.let { GoalBar(it, Modifier.semantics { stateDescription = "${(it * 100).toInt()}% of goal" }) }
                    ?: Text("No target", style = MaterialTheme.typography.bodySmall, color = OrvynTheme.colors.textSecondary)
            }
        }
        item {
            Column(
                Modifier.width(if (pockets.isEmpty()) 280.dp else 140.dp).height(144.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer).clickable(role = Role.Button, onClickLabel = "Create a pocket") { onCreate() }.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painterResource(R.drawable.ph_plus), null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("New pocket", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (pockets.isEmpty()) Text("Put money aside for a trip, a laptop or a rainy day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/** S2.1/S2.2: balance, goal, money in and out, auto-save and history. */
@Composable
fun PocketScreen(id: String, onBack: () -> Unit, onMove: (intoPocket: Boolean) -> Unit, onEdit: () -> Unit, onOpenTransaction: (String) -> Unit) {
    var pocket by remember { mutableStateOf<Pocket?>(null) }
    var history by remember { mutableStateOf<List<Transaction>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var ruleSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun load() {
        try {
            pocket = Api.pockets().firstOrNull { it.id == id } ?: return onBack()
            history = Api.transactions(id).items
            error = null
        } catch (e: ApiException) { error = e.message }
    }
    LaunchedEffect(DataVersion.count) { load() }
    val p = pocket
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(Modifier.height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ph_arrow_left), contentDescription = "Back") }
            Spacer(Modifier.weight(1f))
            if (p != null) IconButton(onClick = onEdit) { Icon(painterResource(R.drawable.ph_pencil_simple), contentDescription = "Edit pocket") }
        }
        if (p == null) {
            if (error != null) MessageState(R.drawable.ph_warning_circle, "Couldn't load this pocket", error!!,
                action = { OrvynTextButton("Try again", onClick = { scope.launch { load() } }) })
            else Skeleton(Modifier.padding(20.dp).fillMaxWidth().height(144.dp))
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PocketTile(pocketIcon(p.image), 48)
                    Text(p.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Text(Money.format(p.balance), style = OrvynType.displayBalance)
                    p.progress?.let { GoalBar(it) }
                    goalLine(p)?.let { Text(if (p.reached) "Goal reached · $it" else it, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary) }
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OrvynButton("Add money", onClick = { onMove(true) }, modifier = Modifier.weight(1f))
                        OrvynButton("Take out", onClick = { onMove(false) }, kind = ButtonKind.Secondary, enabled = p.balance > 0, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(16.dp))
                ListRow("Auto-save", ruleSummary(p.rule), R.drawable.ph_repeat, onClick = { ruleSheet = true }) {
                    Icon(painterResource(R.drawable.ph_caret_right), null, tint = OrvynTheme.colors.textSecondary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("HISTORY", style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary,
                    modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp))
            }
            val h = history
            if (h == null) items(3) { Skeleton(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(48.dp)) }
            else if (h.isEmpty()) item { MessageState(R.drawable.ph_piggy_bank, "Nothing here yet", "Money you add or take out shows up here.") }
            else items(h, key = { it.id }) { t ->
                Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                    TransactionRow(t) { onOpenTransaction(t.id) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (ruleSheet && p != null) AutoSaveSheet(p, onDismiss = { ruleSheet = false }) { scope.launch { load() } }
}

/** S2.3: off, a share of pay day, a weekly amount, or round-ups. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoSaveSheet(p: Pocket, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val r = p.rule
    var type by remember { mutableStateOf(r?.optString("type") ?: "off") }
    var percent by remember { mutableLongStateOf(r?.optLong("percent")?.takeIf { it > 0 } ?: 10) }
    var day by remember { mutableLongStateOf(r?.optLong("day")?.takeIf { it > 0 } ?: 1) }
    var amount by remember { mutableLongStateOf(r?.optLong("amount")?.takeIf { it > 0 } ?: 100_000) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    OrvynBottomSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Auto-save", style = MaterialTheme.typography.titleLarge)
            Text("Money moves from ORVYN Everyday into ${p.name} on its own. If your balance is too low, that save is skipped and you get a message.",
                style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
            for ((key, label) in listOf("off" to "Off", "payday" to "Every pay day", "weekly" to "Every week", "roundup" to "Round up payments")) {
                val sel = type == key
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                        .border(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                        .clickable(role = Role.RadioButton) { type = key }.semantics { selected = sel }.heightIn(min = 52.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text(label, style = MaterialTheme.typography.titleMedium) }
            }
            when (type) {
                "payday" -> {
                    Text("Share of each salary that arrives", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (v in listOf(5L, 10L, 20L, 30L)) Chip("$v%", percent == v) { percent = v }
                    }
                }
                "weekly" -> {
                    Text("Day", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DAYS.forEachIndexed { i, d -> Chip(d.take(3), day == i + 1L) { day = i + 1L } }
                    }
                    Text("Amount", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (v in listOf(50_000L, 100_000L, 250_000L, 500_000L)) Chip("${v / 1000}k", amount == v) { amount = v }
                    }
                }
                "roundup" -> Text("Pay Rp23,000 and Rp7,000 goes into ${p.name}. Works for QRIS, bills and debit card payments.",
                    style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            OrvynButton("Save", loading = saving, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), onClick = {
                val rule = when (type) {
                    "payday" -> JSONObject().put("type", "payday").put("percent", percent)
                    "weekly" -> JSONObject().put("type", "weekly").put("day", day).put("amount", amount)
                    "roundup" -> JSONObject().put("type", "roundup")
                    else -> null
                }
                scope.launch {
                    saving = true
                    try { Api.updatePocket(p, rule = rule); onSaved(); onDismiss() } catch (e: ApiException) { error = e.message }
                    saving = false
                }
            })
        }
    }
}

/** S2.1: create, or edit an existing pocket (then it can also be closed). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PocketEditor(id: String?, onBack: () -> Unit, onSaved: (String) -> Unit, onClosed: () -> Unit) {
    var existing by remember { mutableStateOf<Pocket?>(null) }
    var name by remember { mutableStateOf("") }
    var image by remember { mutableStateOf("travel") }
    var target by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) {
        if (id != null) runCatching { Api.pockets().first { it.id == id } }.getOrNull()?.let { p ->
            existing = p; name = p.name; image = p.image; target = p.target?.toString() ?: ""; date = p.targetDate
        }
    }
    val targetValue = target.filter(Char::isDigit).toLongOrNull()
    val targetError = if (targetValue != null && targetValue < 10_000) "A target needs to be at least Rp10,000." else null
    FlowScreen(if (id == null) "New pocket" else "Edit pocket", if (id == null) "Money in a pocket stays yours. Move it back any time." else null, onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            OrvynTextField("Name", name, { name = it.take(24) }, helper = "${name.length}/24")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Picture", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 4) {
                    for ((key, label, icon) in IMAGES) {
                        val sel = image == key
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                .border(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                                .clickable(role = Role.RadioButton) { image = key }.semantics { selected = sel; contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(icon), null, tint = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else OrvynTheme.colors.textSecondary) }
                    }
                }
            }
            // Plain digits while typing (reformatting would jump the cursor); the formatted amount shows underneath.
            OrvynTextField("Target (optional)", target, { target = it.filter(Char::isDigit).take(13) },
                keyboardType = KeyboardType.Number, error = targetError, helper = targetValue?.let { Money.format(it) } ?: "How much you want to save")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Target date (optional)", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OrvynTextButton(date?.let { LocalDate.parse(it).format(dateText) } ?: "Pick a date", onClick = { picking = true })
                    if (date != null) OrvynTextButton("Clear", onClick = { date = null })
                }
            }
            if (existing != null) OrvynButton("Close pocket", onClick = { confirmClose = true }, kind = ButtonKind.Destructive, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        }
        OrvynButton(if (id == null) "Create pocket" else "Save", loading = saving, enabled = name.isNotBlank() && targetError == null,
            modifier = Modifier.padding(vertical = 16.dp), onClick = {
                scope.launch {
                    saving = true
                    try {
                        val p = existing
                        if (p == null) onSaved(Api.createPocket(name.trim(), image, targetValue, date))
                        else { Api.updatePocket(p, name.trim(), image, targetValue, date); onSaved(p.id) }
                        DataVersion.count++
                    } catch (e: ApiException) { error = e.message }
                    saving = false
                }
            })
    }
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date?.let { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() },
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis > System.currentTimeMillis()
            },
        )
        DatePickerDialog(onDismissRequest = { picking = false },
            confirmButton = { OrvynTextButton("OK", onClick = { state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; picking = false }) },
            dismissButton = { OrvynTextButton("Cancel", onClick = { picking = false }) }) { DatePicker(state) }
    }
    existing?.let { p ->
        if (confirmClose) ConfirmDialog(
            title = "Close ${p.name}?",
            body = if (p.balance > 0) "${Money.format(p.balance)} goes back to ORVYN Everyday." else "This pocket is empty.",
            confirmLabel = "Close pocket", destructive = true, onDismiss = { confirmClose = false },
            onConfirm = { scope.launch { try { Api.closePocket(p.id, UUID.randomUUID().toString()); DataVersion.count++; onClosed() } catch (e: ApiException) { error = e.message; confirmClose = false } } },
        )
    }
}

/** S2.2: no PIN and no fee, because the money stays the customer's own. */
@Composable
fun PocketMoveFlow(id: String, intoPocket: Boolean, onExit: () -> Unit, startAmount: Long = 0L) {
    var pocket by remember { mutableStateOf<Pocket?>(null) }
    var savings by remember { mutableLongStateOf(0L) }
    var amount by remember { mutableLongStateOf(startAmount) }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<Boolean?>(null) } // true when the goal was just reached
    val key = remember { UUID.randomUUID().toString() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        runCatching {
            pocket = Api.pockets().first { it.id == id }
            savings = parseHome(Api.homeJson()).accounts.first().balance
        }.onFailure { error = it.message }
    }
    val p = pocket ?: return FlowScreen(if (intoPocket) "Add money" else "Take out", null, onExit) {
        error?.let { MessageState(R.drawable.ph_warning_circle, "Couldn't load this pocket", it) } ?: Skeleton(Modifier.fillMaxWidth().height(120.dp))
    }
    val available = if (intoPocket) savings else p.balance
    BackHandler(done != null) { onExit() }
    done?.let { reached ->
        return SuccessScreen(if (reached) "Goal reached" else if (intoPocket) "Added" else "Moved",
            Money.format(amount), if (intoPocket) "into ${p.name}" else "back to ORVYN Everyday",
            details = {
                ReceiptLine("From", if (intoPocket) "ORVYN Everyday" else p.name)
                ReceiptLine("${p.name} now", Money.format(p.balance + if (intoPocket) amount else -amount))
            }, primaryLabel = "Done", onPrimary = onExit)
    }
    FlowScreen(if (intoPocket) "Add to ${p.name}" else "Take out of ${p.name}",
        if (intoPocket) "From ORVYN Everyday. No fee." else "Back to ORVYN Everyday. No fee.", onExit) {
        Column(Modifier.weight(1f)) {
            AmountDisplay(amount, available, error ?: if (amount > available) "You have ${Money.format(available)} to move." else null, onQuickPick = { amount = it; error = null })
            Spacer(Modifier.weight(1f))
            AmountKeypad(amount, { amount = it; error = null })
        }
        OrvynButton(if (amount > 0) "Move ${Money.format(amount)}" else "Enter an amount", enabled = amount in 1..available, loading = sending,
            modifier = Modifier.padding(vertical = 16.dp), onClick = {
                scope.launch {
                    sending = true
                    try { done = Api.movePocket(p.id, intoPocket, amount, key); DataVersion.count++ } catch (e: ApiException) { error = e.message }
                    sending = false
                }
            })
    }
}
