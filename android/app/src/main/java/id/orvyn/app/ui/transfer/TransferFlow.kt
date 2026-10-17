package id.orvyn.app.ui.transfer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.AppConfig
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.Bank
import id.orvyn.app.data.DataVersion
import id.orvyn.app.data.Method
import id.orvyn.app.data.Paid
import id.orvyn.app.data.Recipient
import id.orvyn.app.data.TransferOptions
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.components.AmountDisplay
import id.orvyn.app.ui.components.AmountKeypad
import id.orvyn.app.ui.components.AmountScreen
import id.orvyn.app.ui.components.ButtonKind
import id.orvyn.app.ui.components.Chip
import id.orvyn.app.ui.components.ConfirmDialog
import id.orvyn.app.ui.components.ErrorBox
import id.orvyn.app.ui.components.FlowSteps
import id.orvyn.app.ui.components.InitialsAvatar
import id.orvyn.app.ui.components.InlineLoadError
import id.orvyn.app.ui.components.LoadContent
import id.orvyn.app.ui.components.LocalSnacks
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OFFLINE
import id.orvyn.app.ui.components.OrvynBottomSheet
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.OrvynTextButton
import id.orvyn.app.ui.components.OrvynTextField
import id.orvyn.app.ui.components.PinScreen
import id.orvyn.app.ui.components.ReceiptLine
import id.orvyn.app.ui.components.Segmented
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.components.SuccessScreen
import id.orvyn.app.ui.components.WhiteTextButton
import id.orvyn.app.ui.components.rememberLoad
import id.orvyn.app.ui.components.rememberOnce
import id.orvyn.app.ui.components.say
import id.orvyn.app.ui.groupAccountNumber
import id.orvyn.app.ui.shareTransaction
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

private enum class Step { Pick, New, Amount, Review, Pin, Done }

/** S2.4: send later instead of now. [repeat] is once, weekly or monthly. */
data class Later(val firstDate: String, val repeat: String)

fun repeatLabel(repeat: String, firstDate: String?): String {
    val d = firstDate?.let { java.time.LocalDate.parse(it) }
    return when (repeat) {
        "weekly" -> "Every ${d?.dayOfWeek?.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US) ?: "week"}"
        "monthly" -> "Every month on the ${d?.dayOfMonth?.let(::ordinal) ?: "same day"}"
        else -> "Once"
    }
}

private fun ordinal(n: Int) = "$n" + if (n in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }

/**
 * Stories S1.12–S1.14: recipient → amount → review → PIN → done, with back going one step at a time. Started with a
 * recipient and an amount (a scanned My QR, or Vyna), it opens on review, and Back from there leaves the flow (Q-G7).
 */
@Composable
fun TransferFlow(onExit: () -> Unit, startWith: Recipient? = null, startAmount: Long? = null, onScheduled: () -> Unit = {}) {
    val entry = remember { if (startWith == null) Step.Pick else if (startAmount != null) Step.Review else Step.Amount }
    var step by remember { mutableStateOf(entry) }
    var to by remember { mutableStateOf(startWith) }
    var amount by remember { mutableLongStateOf(startAmount ?: 0L) }
    var note by remember { mutableStateOf("") }
    var method by remember { mutableStateOf<Method?>(null) }
    var later by remember { mutableStateOf<Later?>(null) }
    var paid by remember { mutableStateOf<Paid?>(null) }
    var scheduledAt by remember { mutableStateOf<Instant?>(null) }
    var reviewError by remember { mutableStateOf<String?>(null) }
    // One key per review: a double tap or a retry after a timeout can't send twice (kept until the transfer goes through).
    var idempotencyKey by remember { mutableStateOf(UUID.randomUUID().toString()) }

    BackHandler(step != Step.Done) {
        step = when {
            step == entry -> { onExit(); step }
            step == Step.Pick -> { onExit(); step }
            step == Step.New -> Step.Pick
            step == Step.Amount -> if (to?.id != null || entry != Step.Pick) Step.Pick else Step.New
            step == Step.Review -> Step.Amount
            else -> Step.Review
        }
    }

    FlowSteps(step, index = { it.ordinal }, success = { it == Step.Done }) { s ->
        when (s) {
            Step.Pick -> PickRecipient(onBack = onExit, onNew = { step = Step.New }, onScheduled = onScheduled, onPick = { to = it; amount = 0; step = Step.Amount })
            Step.New -> NewRecipient(onBack = { step = Step.Pick }, onFound = { to = it; amount = 0; step = Step.Amount })
            Step.Amount -> EnterAmount(to!!, amount, onBack = { if (entry == Step.Amount) onExit() else step = Step.Pick }) { a ->
                amount = a; reviewError = null; idempotencyKey = UUID.randomUUID().toString(); step = Step.Review
            }
            Step.Review -> Review(to!!, amount, note, { note = it }, reviewError, { reviewError = null }, later, { later = it },
                onBack = { if (entry == Step.Review) onExit() else step = Step.Amount }) { m -> method = m; step = Step.Pin }
            Step.Pin -> EnterPin(to!!, amount, method!!, note, idempotencyKey, later, onBack = { step = Step.Review },
                onSent = { p, at -> paid = p; scheduledAt = at; DataVersion.count++; step = Step.Done },
                onRefused = { msg -> reviewError = msg; step = Step.Review })
            Step.Done -> Done(to!!, amount, method!!, note, paid, scheduledAt, later, onDone = onExit)
        }
    }
}

@Composable
private fun PickRecipient(onBack: () -> Unit, onNew: () -> Unit, onScheduled: () -> Unit, onPick: (Recipient) -> Unit) {
    val list = rememberLoad { Api.recipients() }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Recipient?>(null) }
    val scope = rememberCoroutineScope()

    // Story S1.14 AC3: nickname or remove a saved recipient. Failures stay in the sheet (design.md 6.1).
    editing?.let { r ->
        var nick by remember(r.id) { mutableStateOf(r.nickname ?: "") }
        var saving by remember(r.id) { mutableStateOf(false) }
        var removing by remember(r.id) { mutableStateOf(false) }
        var confirm by remember(r.id) { mutableStateOf(false) }
        var error by remember(r.id) { mutableStateOf<String?>(null) }
        OrvynBottomSheet(onDismiss = { editing = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(r.name, style = MaterialTheme.typography.titleLarge)
                Text("${r.bankName} · ${groupAccountNumber(r.accountNumber)}", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                OrvynTextField("Nickname", nick, { nick = it.take(30); error = null }, helper = "Only you see this, for example \"Mom\" or \"Rent\".")
                error?.let { ErrorBox(it) }
                OrvynButton("Save", loading = saving, onClick = {
                    saving = true
                    scope.launch {
                        try { Api.renameRecipient(r.id!!, nick); editing = null; list.reload() } catch (e: ApiException) { error = e.message }
                        saving = false
                    }
                })
                Spacer(Modifier.height(12.dp)) // 24dp between the primary action and the destructive one (Q-C5 AC4)
                OrvynButton("Remove from saved", kind = ButtonKind.DestructiveText, onClick = { confirm = true }, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
        if (confirm) ConfirmDialog(
            "Remove ${r.name} from saved?", "You can save them again after your next transfer.", "Remove", destructive = true, busy = removing,
            onDismiss = { confirm = false },
            onConfirm = {
                removing = true
                scope.launch {
                    try { Api.deleteRecipient(r.id!!); editing = null; list.reload() } catch (e: ApiException) { error = e.message }
                    removing = false; confirm = false
                }
            },
        )
    }

    FlowScreen("Send money", null, onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrvynButton("New recipient", onClick = onNew, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
            if (id.orvyn.app.data.AppConfig.on("scheduled")) OrvynButton("Scheduled", onClick = onScheduled, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search name or account number") },
            leadingIcon = { Icon(painterResource(R.drawable.ph_magnifying_glass), null) },
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline, unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface),
        )
        val match = { r: Recipient -> query.isBlank() || listOfNotNull(r.name, r.nickname, r.accountNumber).any { it.contains(query.trim(), ignoreCase = true) } }
        LoadContent(list, "Couldn't load your recipients", skeleton = {
            Column(Modifier.padding(top = 8.dp)) { repeat(4) { Skeleton(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(44.dp)) } }
        }) { l ->
            val saved = l.saved.filter(match)
            val recent = l.recent.filter(match)
            LazyColumn(Modifier.padding(top = 8.dp)) {
                when {
                    l.saved.isEmpty() && l.recent.isEmpty() -> item {
                        MessageState(R.drawable.ph_arrows_left_right, "No recipients yet", "Tap New recipient to send money to someone for the first time.")
                    }
                    saved.isEmpty() && recent.isEmpty() -> item {
                        // Q-G6: say what was searched for, and what to do next.
                        MessageState(R.drawable.ph_magnifying_glass, "No matches for \"${query.trim()}\"", "Check the spelling, or send to a new recipient.",
                            action = { OrvynButton("New recipient", onClick = onNew, kind = ButtonKind.Secondary, fill = false) })
                    }
                    else -> for ((title, group) in listOf("Saved" to saved, "Recent" to recent)) {
                        if (group.isEmpty()) continue
                        item { Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
                        items(group, key = { title + it.bank + it.accountNumber }) { r ->
                            RecipientRowFlush(r, onEdit = if (r.id != null) ({ editing = r }) else null) { onPick(r) }
                        }
                    }
                }
            }
        }
    }
}

// Rows inside FlowScreen already have the 20dp gutter, so drop the row's own horizontal padding.
@Composable
private fun RecipientRowFlush(r: Recipient, onEdit: (() -> Unit)? = null, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = rememberOnce(onClick)).heightIn(min = 64.dp).padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    InitialsAvatar(r.nickname ?: r.name)
    Column(Modifier.weight(1f)) {
        Text(r.nickname ?: r.name, style = MaterialTheme.typography.titleMedium)
        Text(listOfNotNull(r.nickname?.let { r.name }, "${r.bankName} · ${groupAccountNumber(r.accountNumber)}").joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
    }
    if (onEdit != null) androidx.compose.material3.IconButton(onClick = onEdit) {
        Icon(painterResource(R.drawable.ph_pencil_simple), contentDescription = "Edit ${r.nickname ?: r.name}", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun NewRecipient(onBack: () -> Unit, onFound: (Recipient) -> Unit) {
    var banks by remember { mutableStateOf(listOf(Bank("ORVYN", "ORVYN Bank"))) }
    var bank by remember { mutableStateOf(banks.first()) }
    var number by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { banks = Api.banks() } }

    FlowScreen("New recipient", "We'll show you the account holder's name before you send anything.", onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Bank", style = MaterialTheme.typography.titleMedium)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)).clickable(role = Role.Button) { picking = true }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(bank.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(painterResource(R.drawable.ph_caret_right), contentDescription = "Change bank")
                }
            }
            OrvynTextField("Account number", number, { number = it.filter(Char::isDigit).take(16); error = null },
                keyboardType = KeyboardType.Number, error = error,
                helper = if (bank.code == "ORVYN") "ORVYN account numbers have 10 digits." else "Usually 10 to 16 digits.")
        }
        OrvynButton("Check account", enabled = number.length >= 10, loading = busy, modifier = Modifier.padding(vertical = 16.dp), onClick = {
            busy = true
            scope.launch {
                try { onFound(Api.lookup(bank.code, number)) } catch (e: ApiException) { error = e.message } finally { busy = false }
            }
        })
    }
    if (picking) OrvynBottomSheet(onDismiss = { picking = false }) {
        var q by remember { mutableStateOf("") }
        OrvynTextField("Find a bank", q, { q = it }, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(Modifier.padding(top = 8.dp, bottom = 24.dp)) {
            items(banks.filter { it.name.contains(q.trim(), ignoreCase = true) }, key = { it.code }) { b ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { bank = b; error = null; picking = false }
                        .semantics { selected = b == bank }.heightIn(min = 56.dp).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(b.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                        color = if (b == bank) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if (b == bank) Icon(painterResource(R.drawable.ph_check_circle), null, tint = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun RecipientHeader(to: Recipient) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InitialsAvatar(to.name)
        Column {
            Text(to.name, style = MaterialTheme.typography.titleMedium)
            Text("${to.bankName} · ${groupAccountNumber(to.accountNumber)}", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
        }
    }
}

/** design.md 4.7: the balance and today's limit load under the amount; if they can't, the screen says so and offers Try again. */
@Composable
private fun EnterAmount(to: Recipient, initial: Long, onBack: () -> Unit, onContinue: (Long) -> Unit) {
    var amount by remember { mutableLongStateOf(initial) }
    val opts = rememberLoad { Api.transferOptions(to.bank, 0) }
    val o = opts.value
    val error = when {
        o == null || amount == 0L -> null
        amount > o.balance -> "You're ${Money.format(amount - o.balance)} short."
        amount > o.remainingToday -> "That's over your daily limit. You can still send ${Money.format(o.remainingToday)} today."
        else -> null
    }
    val failed = opts.error?.let { if (it == OFFLINE) it else "Couldn't load your balance and daily limit." }
    AmountScreen("How much?", null, onBack, keypad = { AmountKeypad(amount, { amount = it }) }, button = {
        OrvynButton(
            when { amount == 0L -> "Enter an amount"; amount < AppConfig.minTransfer -> "Minimum ${Money.format(AppConfig.minTransfer)}"; o == null && failed != null -> "Balance didn't load"; else -> "Continue" },
            enabled = amount >= AppConfig.minTransfer && error == null && o != null, // H-2: the server's one minimum
            onClick = { onContinue(amount) },
        )
    }) {
        RecipientHeader(to)
        AmountDisplay(amount, o?.balance, error, onQuickPick = { amount = it }, loading = o == null && opts.busy,
            loadError = if (o == null) failed else null, retrying = opts.busy, onRetry = opts::reload)
    }
}

/**
 * Q-B4 (design.md 6.4): the fee and arrival load here. If they don't, the card says so with Try again and the button says
 * why it's off. Coming back from the PIN with any error other than a wrong PIN reloads them and shows the message.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun Review(
    to: Recipient, amount: Long, note: String, onNote: (String) -> Unit, error: String?, onClearError: () -> Unit,
    later: Later?, onLater: (Later?) -> Unit, onBack: () -> Unit, onConfirm: (Method) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    var opts by remember { mutableStateOf<TransferOptions?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var chosen by remember { mutableStateOf<Method?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun load(): Boolean {
        loading = true
        return try {
            val o = Api.transferOptions(to.bank, amount)
            opts = o; loadError = null
            if (chosen == null || o.methods.none { it.method == chosen?.method }) chosen = o.methods.firstOrNull()
            true
        } catch (e: ApiException) { loadError = e.message; false } finally { loading = false }
    }
    LaunchedEffect(Unit) { load() }
    val fee = chosen?.fee ?: 0
    FlowScreen("Check the details", null, onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RecipientHeader(to)
            val o = opts
            when {
                o != null -> {
                    if (o.methods.size > 1) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("How to send", style = MaterialTheme.typography.titleMedium)
                        for (m in o.methods) MethodOption(m, m == chosen) { chosen = m }
                    }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ReviewRow("Amount", Money.format(amount))
                        ReviewRow("Fee", if (fee == 0L) "Free" else Money.format(fee))
                        chosen?.let { ReviewRow("Arrives", it.note) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ReviewRow("Total", Money.format(amount + fee), strong = true)
                        if (later == null && o.balance >= amount + fee) ReviewRow("Balance after", Money.format(o.balance - amount - fee))
                    }
                }
                loadError != null && !loading -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InlineLoadError("Couldn't load the fee and arrival time.", retrying = loading, onRetry = { scope.launch { if (load()) onClearError() } }) // fresh details: the old error box goes (4.12, Q-01-F4)
                    Text(loadError!!, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                }
                else -> Skeleton(Modifier.fillMaxWidth().height(180.dp))
            }
            if (id.orvyn.app.data.AppConfig.on("scheduled")) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("When", style = MaterialTheme.typography.titleMedium)
                Segmented(listOf("Send now", "Schedule"), if (later == null) 0 else 1, { if (it == 0) onLater(null) else if (later == null) picking = true })
                later?.let { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("First on ${java.time.LocalDate.parse(l.firstDate).format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy", java.util.Locale.US))}",
                            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        OrvynTextButton("Change", onClick = { picking = true })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (r in listOf("once", "weekly", "monthly")) Chip(repeatLabel(r, l.firstDate), l.repeat == r) { onLater(l.copy(repeat = r)) }
                    }
                    Text("Sent at 07:00 WIB. Your PIN now covers every send, and fees and limits apply on the day.",
                        style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                }
            }
            OrvynTextField("Note (optional)", note, { onNote(it.take(50)) }, helper = "${note.length}/50 · The recipient sees this.")
            error?.let { ErrorBox(it) }
        }
        val label = when {
            opts != null -> "${if (later == null) "Send" else "Schedule"} ${Money.format(amount)}"
            loadError != null && !loading -> "Details didn't load"
            else -> "Loading the details…"
        }
        var sending by remember { mutableStateOf(false) }
        OrvynButton(label, enabled = opts != null && chosen != null, loading = sending, modifier = Modifier.padding(vertical = 16.dp), onClick = {
            if (error == null) chosen?.let(onConfirm)
            else {
                // After a failed send (offline, server): fetch the details again first, then ask for the PIN.
                sending = true
                scope.launch { if (load()) { onClearError(); chosen?.let(onConfirm) }; sending = false }
            }
        })
    }
    if (picking) {
        val tomorrow = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jakarta")).plusDays(1)
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = (later?.firstDate?.let(java.time.LocalDate::parse) ?: tomorrow).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= tomorrow.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            },
        )
        androidx.compose.material3.DatePickerDialog(onDismissRequest = { picking = false },
            confirmButton = { OrvynTextButton("OK", onClick = {
                state.selectedDateMillis?.let { onLater(Later(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString(), later?.repeat ?: "once")) }
                picking = false
            }) },
            dismissButton = { OrvynTextButton("Cancel", onClick = { picking = false }) }) { androidx.compose.material3.DatePicker(state) }
    }
}

/** Label left, value right: easy to scan before approving money. */
@Composable
fun ReviewRow(label: String, value: String, strong: Boolean = false) = Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
    Text(label, style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary, modifier = Modifier.weight(1f))
    Text(value, style = if (strong) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
        textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1.4f))
}

@Composable
private fun MethodOption(m: Method, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.outline, RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onClick).semantics { this.selected = selected }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.label, style = MaterialTheme.typography.titleMedium)
            Text(m.note, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
        }
        Text(if (m.fee == 0L) "Free" else Money.format(m.fee), style = MaterialTheme.typography.titleMedium)
    }
}

/** Q-B5: "Sending Rp500,000…" while the server works. A wrong PIN stays here; anything else goes back to review. */
@Composable
private fun EnterPin(to: Recipient, amount: Long, method: Method, note: String, key: String, later: Later?, onBack: () -> Unit,
                     onSent: (Paid?, Instant?) -> Unit, onRefused: (String) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    PinScreen(
        "Enter your PIN", if (later == null) "To send ${Money.format(amount)} to ${to.name}." else "To schedule ${Money.format(amount)} to ${to.name}.", onBack,
        errorCount = errors, errorText = error,
        busyText = if (busy) "${if (later == null) "Sending" else "Scheduling"} ${Money.format(amount)}…" else null,
        onComplete = { pin ->
            busy = true; error = null
            scope.launch {
                try {
                    if (later == null) onSent(Api.sendTransfer(to, amount, method.method, note, pin, key), null)
                    else onSent(null, Api.createSchedule(to, amount, method.method, note, later.repeat, later.firstDate, pin) ?: Instant.now())
                } catch (e: ApiException) {
                    when (e.code) {
                        "WRONG_PIN" -> { error = e.message; errors++ }
                        "LOCKED", "signed_out" -> {} // the app moves to the locked or unlock screen
                        else -> onRefused(e.message)
                    }
                } finally { busy = false }
            }
        },
    )
}

@Composable
private fun Done(to: Recipient, amount: Long, method: Method, note: String, paid: Paid?, scheduledAt: Instant?, later: Later?, onDone: () -> Unit) {
    var saved by remember { mutableStateOf(to.id != null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snacks = LocalSnacks.current
    SuccessScreen(
        headline = if (later == null) "Sent" else "Scheduled",
        amount = Money.format(amount),
        subline = "to ${to.name}",
        date = if (later == null) paid?.at else scheduledAt,
        details = {
            ReceiptLine("To", "${to.bankName} · ${groupAccountNumber(to.accountNumber)}")
            ReceiptLine("Method", if (method.fee == 0L) method.label else "${method.label} · fee ${Money.format(method.fee)}")
            if (note.isNotBlank()) ReceiptLine("Note", note)
            if (later == null) ReceiptLine("Reference", paid?.reference ?: "")
            else {
                ReceiptLine("First send", java.time.LocalDate.parse(later.firstDate).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.US)) + ", 07:00 WIB")
                ReceiptLine("Repeats", repeatLabel(later.repeat, later.firstDate))
            }
        },
        onPrimary = onDone,
        onShare = paid?.transactionId?.takeIf { later == null }?.let { id -> { shareTransaction(context, id) } },
        textAction = when {
            to.id != null -> null
            // No name in either line (design.md 17.1): the subline already shows it in full, and a first word can be "PT".
            saved -> { {
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp).semantics(mergeDescendants = true) { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ph_check), null, Modifier.size(16.dp), tint = androidx.compose.ui.graphics.Color.White)
                    Text("Saved to your recipients", style = MaterialTheme.typography.labelLarge, color = androidx.compose.ui.graphics.Color.White)
                }
            } }
            else -> { { WhiteTextButton("Save as a recipient", loading = saving) {
                saving = true
                scope.launch {
                    try { Api.saveRecipient(to, null); saved = true } catch (e: Exception) { snacks?.show(e.say(), error = true) }
                    saving = false
                }
            } } }
        },
    )
}
