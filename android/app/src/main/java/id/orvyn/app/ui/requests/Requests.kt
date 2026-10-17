package id.orvyn.app.ui.requests

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.DataVersion
import id.orvyn.app.data.MoneyRequest
import id.orvyn.app.data.Recipient
import id.orvyn.app.data.Split
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.components.*
import id.orvyn.app.data.Paid
import id.orvyn.app.ui.formatDateTime
import id.orvyn.app.ui.shareTransaction
import id.orvyn.app.ui.transfer.RecipientHeader
import id.orvyn.app.ui.transfer.ReviewRow
import androidx.compose.ui.platform.LocalContext
import id.orvyn.app.ui.components.AmountKeypad
import id.orvyn.app.ui.components.ButtonKind
import id.orvyn.app.ui.components.ConfirmDialog
import id.orvyn.app.ui.components.InitialsAvatar
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OrvynBottomSheet
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.OrvynTextButton
import id.orvyn.app.ui.components.OrvynTextField
import id.orvyn.app.ui.components.PinPad
import id.orvyn.app.ui.components.ReceiptLine
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.components.SuccessScreen
import id.orvyn.app.ui.dayLabel
import id.orvyn.app.ui.groupAccountNumber
import id.orvyn.app.ui.theme.OrvynTheme
import id.orvyn.app.ui.theme.OrvynType
import kotlinx.coroutines.launch

// Stories S2.6–S2.7: ask an ORVYN customer for money, or split a bill between friends.

/** Q-E9 (design.md 4.17): a status chip with an icon and a word, never colour alone. */
private fun statusOf(status: String, mine: Boolean) = when (status) {
    "open" -> if (mine) Status.Waiting else Status.ToPay
    "paid" -> Status.Paid; "declined" -> Status.Declined; "cancelled" -> Status.Cancelled; else -> Status.Expired
}

@Composable
private fun Section(text: String) = Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary,
    modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))

/** Everything asked of me, everything I asked, and my splits. */
@Composable
fun RequestsScreen(onBack: () -> Unit, onAsk: () -> Unit, onSplit: () -> Unit, onOpen: (String) -> Unit) {
    val data = rememberLoad(DataVersion.count) { Api.requests() }
    FlowScreen("Requests", null, onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrvynButton("Ask for money", onClick = onAsk, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
            OrvynButton("Split a bill", onClick = onSplit, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
        }
        LoadContent(data, "Couldn't load your requests", skeleton = { Skeleton(Modifier.padding(top = 16.dp).fillMaxWidth().height(160.dp)) }) { d ->
            if (d.first.isEmpty() && d.second.isEmpty()) MessageState(R.drawable.ph_hand_coins, "No requests yet",
                "Ask a friend on ORVYN for money, or split a bill and everyone gets their share to pay.")
            else LazyColumn {
                val toPay = d.first.filter { !it.mine && it.status == "open" }
                if (toPay.isNotEmpty()) item { Section("To pay") }
                items(toPay, key = { "p" + it.id }) { r -> RequestRow(r, onOpen) }
                val asked = d.first.filter { it.mine && it.splitId == null }
                if (asked.isNotEmpty()) item { Section("You asked") }
                items(asked, key = { "a" + it.id }) { r -> RequestRow(r, onOpen) }
                if (d.second.isNotEmpty()) item { Section("Splits") }
                items(d.second, key = { "s" + it.id }) { s -> SplitCard(s) }
                val done = d.first.filter { !it.mine && it.status != "open" }
                if (done.isNotEmpty()) item { Section("Earlier") }
                items(done, key = { "d" + it.id }) { r -> RequestRow(r, onOpen) }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun RequestRow(r: MoneyRequest, onOpen: (String) -> Unit) {
    val other = if (r.mine) r.to else r.from
    Column {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = rememberOnce { onOpen(r.id) }).heightIn(min = 64.dp).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InitialsAvatar(other)
            Column(Modifier.weight(1f)) {
                Text(if (r.mine) "You asked $other" else "$other asks you", style = MaterialTheme.typography.titleMedium)
                Text(listOfNotNull(r.note, dayLabel(r.at)).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Money.format(r.amount), style = MaterialTheme.typography.titleMedium)
                StatusChip(statusOf(r.status, r.mine))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SplitCard(s: Split) {
    val paid = s.people.filter { it.status == "paid" }.sumOf { it.amount }
    OrvynCard(Modifier.padding(vertical = 6.dp)) {
        Row {
            Text(s.note ?: "Split bill", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(Money.format(s.total), style = MaterialTheme.typography.titleMedium)
        }
        Text("${Money.format(paid)} of ${Money.format(s.people.sumOf { it.amount })} back from friends", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
        for (p in s.people) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(Money.format(p.amount), style = MaterialTheme.typography.bodyLarge)
            StatusChip(statusOf(p.status, mine = true))
        }
    }
}

/**
 * One request (design.md 10.7). Asked of me and open: Pay goes through a review screen, then the PIN; Decline asks first
 * (PO-26). Mine and open: Cancel request asks first, with Keep request to back out (PO-15).
 */
@Composable
fun RequestScreen(id: String, onBack: () -> Unit) {
    val load = rememberLoad { Api.request(id) }
    var step by remember { mutableIntStateOf(0) } // 0 detail, 1 review, 2 PIN, 3 done
    var refused by remember { mutableStateOf<String?>(null) }
    var paid by remember { mutableStateOf<Paid?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) } // "decline" or "cancel"
    var working by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    BackHandler(step in 1..2) { step -= 1 }
    val q = load.value ?: return FlowScreen("Money request", null, onBack) {
        LoadContent(load, "Couldn't load this request", skeleton = { Skeleton(Modifier.fillMaxWidth().height(160.dp)) }) {}
    }
    FlowSteps(step, index = { it }, success = { it == 3 }) { s ->
        when (s) {
            0 -> FlowScreen("Money request", null, onBack) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InitialsAvatar(if (q.mine) q.to else q.from)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (q.mine) "You asked ${q.to}" else "${q.from} asks you", style = MaterialTheme.typography.titleLarge)
                            StatusChip(statusOf(q.status, q.mine))
                        }
                    }
                    AmountText(Money.format(q.amount), OrvynType.displayBalance)
                    OrvynCard {
                        ReviewRow(if (q.mine) "To" else "From", if (q.mine) q.to else q.from)
                        q.note?.let { ReviewRow("Note", it) }
                        ReviewRow("Sent", formatDateTime(q.at))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Status", style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                            StatusChip(statusOf(q.status, q.mine))
                        }
                    }
                    actionError?.let { ErrorBox(it) }
                }
                if (q.status == "open") {
                    if (q.mine) OrvynButton("Cancel request", onClick = { confirm = "cancel" }, kind = ButtonKind.Secondary, modifier = Modifier.padding(vertical = 16.dp))
                    else {
                        OrvynButton("Pay ${Money.format(q.amount)}", onClick = { actionError = null; refused = null; step = 1 }, modifier = Modifier.padding(top = 16.dp))
                        Spacer(Modifier.height(12.dp))
                        OrvynButton("Decline", onClick = { confirm = "decline" }, kind = ButtonKind.DestructiveText, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
                    }
                }
            }
            1 -> PayReview(q, refused, onBack = { step = 0 }) { step = 2 }
            2 -> PinConfirm(q, onBack = { step = 1 }, onRefused = { refused = it; step = 1 }) { p -> paid = p; DataVersion.count++; step = 3 }
            else -> SuccessScreen("Paid", Money.format(q.amount), "to ${q.from}", date = paid?.at, details = {
                ReceiptLine("To", q.from)
                q.note?.let { ReceiptLine("Note", it) }
                ReceiptLine("Reference", paid?.reference ?: "")
            }, onPrimary = onBack, onShare = paid?.transactionId?.let { tid -> { shareTransaction(context, tid) } })
        }
    }
    confirm?.let { what ->
        val first = (if (what == "decline") q.from else q.to).substringBefore(' ')
        ConfirmDialog(
            title = if (what == "decline") "Decline this request?" else "Cancel this request?",
            body = if (what == "decline") "$first gets a message that you declined." else "$first won't be able to pay it anymore.",
            confirmLabel = if (what == "decline") "Decline" else "Cancel request", dismissLabel = "Keep request", destructive = true, busy = working,
            onDismiss = { confirm = null },
            onConfirm = {
                working = true
                scope.launch {
                    try { if (what == "decline") Api.declineRequest(id) else Api.cancelRequest(id); DataVersion.count++; load.reload() }
                    catch (e: ApiException) { actionError = e.message }
                    working = false; confirm = null
                }
            },
        )
    }
}

/** Q-F7: who gets it, the amount, the fee (free) and the balance after, before the PIN. */
@Composable
private fun PayReview(q: MoneyRequest, refused: String?, onBack: () -> Unit, onPay: () -> Unit) {
    val opts = rememberLoad { Api.transferOptions("ORVYN", q.amount) }
    FlowScreen("Check the details", null, onBack) {
        val o = opts.value
        val short = o?.let { q.amount - it.balance }?.takeIf { it > 0 }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RecipientHeader(Recipient("ORVYN", "ORVYN Bank", q.fromAccount ?: "", q.from))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReviewRow("Amount", Money.format(q.amount))
                ReviewRow("Fee", "Free")
                q.note?.let { ReviewRow("Note", it) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReviewRow("Total", Money.format(q.amount), strong = true)
                when {
                    o != null -> if (o.balance >= q.amount) ReviewRow("Balance after", Money.format(o.balance - q.amount)) // short: the error box says by how much (Q-01-F8)
                    opts.error != null -> InlineLoadError(opts.error!!, opts.busy, opts::reload)
                    else -> Skeleton(Modifier.fillMaxWidth().height(24.dp))
                }
            }
        }
        (short?.let { "You're ${Money.format(it)} short." } ?: refused)?.let { ErrorBox(it, Modifier.padding(top = 12.dp)) }
        OrvynButton("Pay ${Money.format(q.amount)}", enabled = o != null && short == null, onClick = onPay, modifier = Modifier.padding(vertical = 16.dp))
    }
}

@Composable
private fun PinConfirm(q: MoneyRequest, onBack: () -> Unit, onRefused: (String) -> Unit, onPaid: (Paid) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    PinScreen("Enter your PIN", "To pay ${Money.format(q.amount)} to ${q.from}.", onBack, errorCount = errors, errorText = error,
        busyText = if (busy) "Paying ${Money.format(q.amount)}…" else null,
        onComplete = { pin ->
            busy = true; error = null
            scope.launch {
                try { onPaid(Api.payRequest(q.id, pin)) }
                catch (e: ApiException) { when (e.code) { "WRONG_PIN" -> { error = e.message; errors++ }; "LOCKED", "signed_out" -> {}; else -> onRefused(e.message) } }
                finally { busy = false }
            }
        })
}

/** Pick ORVYN people: saved and recent ones, or anyone by account number. */
@Composable
private fun PeoplePicker(title: String, body: String, multi: Boolean, initial: List<Recipient>, onBack: () -> Unit, onDone: (List<Recipient>) -> Unit) {
    var known by remember { mutableStateOf<List<Recipient>?>(null) }
    val chosen = remember { mutableStateListOf<Recipient>().apply { addAll(initial) } }
    var number by remember { mutableStateOf("") }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var looking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { known = runCatching { Api.recipients().let { (it.saved + it.recent).filter { r -> r.internal }.distinctBy { r -> r.accountNumber } } }.getOrDefault(emptyList()) }
    fun toggle(r: Recipient) {
        if (!multi) return onDone(listOf(r))
        if (chosen.any { it.accountNumber == r.accountNumber }) chosen.removeAll { it.accountNumber == r.accountNumber }
        else if (chosen.size < 9) chosen.add(r)
    }
    FlowScreen(title, body, onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrvynTextField("ORVYN account number", number, { number = it.filter(Char::isDigit).take(10); lookupError = null },
                    keyboardType = KeyboardType.Number, error = lookupError, modifier = Modifier.weight(1f))
            }
            OrvynButton("Find", loading = looking, enabled = number.length == 10, kind = ButtonKind.Secondary, onClick = {
                scope.launch {
                    looking = true
                    try { val r = Api.lookup("ORVYN", number); toggle(r); if (multi) { known = listOf(r) + (known ?: emptyList()).filter { it.accountNumber != r.accountNumber }; number = "" } }
                    catch (e: ApiException) { lookupError = e.message }
                    looking = false
                }
            })
            val k = known
            if (k == null) Skeleton(Modifier.fillMaxWidth().height(120.dp))
            else if (k.isNotEmpty()) Text("PEOPLE ON ORVYN", style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary, modifier = Modifier.padding(top = 12.dp))
            k?.forEach { r ->
                val sel = chosen.any { it.accountNumber == r.accountNumber }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable(role = if (multi) Role.Checkbox else Role.Button) { toggle(r) }.semantics { selected = sel }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    InitialsAvatar(r.name)
                    Column(Modifier.weight(1f)) {
                        Text(r.nickname ?: r.name, style = MaterialTheme.typography.titleMedium)
                        Text(groupAccountNumber(r.accountNumber), style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                    }
                    if (sel) Icon(painterResource(R.drawable.ph_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (multi) OrvynButton(if (chosen.isEmpty()) "Pick at least one friend" else "Next: ${chosen.size} ${if (chosen.size == 1) "friend" else "friends"}",
            enabled = chosen.isNotEmpty(), onClick = { onDone(chosen.toList()) }, modifier = Modifier.padding(vertical = 16.dp))
    }
}

/** S2.6 and Q-G9: person → amount → check the request (the note goes here, never next to the keypad) → sent. */
@Composable
fun AskFlow(onExit: () -> Unit) {
    var who by remember { mutableStateOf<Recipient?>(null) }
    var amount by remember { mutableLongStateOf(0L) }
    var note by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(0) } // 0 who, 1 amount, 2 check, 3 sent
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sentAt by remember { mutableStateOf<java.time.Instant?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler(step in 1..2) { step -= 1 }
    FlowSteps(step, index = { it }, success = { it == 3 }) { s ->
        val w = who
        when {
            s == 0 || w == null -> PeoplePicker("Ask for money", "From someone with an ORVYN account.", multi = false, initial = emptyList(), onBack = onExit) { who = it.first(); step = 1 }
            s == 1 -> AmountScreen("Ask ${w.name.substringBefore(' ')}", "They get a notification and pay with one tap and their PIN.", { step = 0 },
                keypad = { AmountKeypad(amount, { amount = it }) },
                button = { OrvynButton(if (amount >= 1_000) "Continue" else "Ask for at least Rp1,000", enabled = amount >= 1_000, onClick = { error = null; step = 2 }) }) {
                AmountDisplay(amount, null, null, onQuickPick = { amount = it })
            }
            s == 2 -> FlowScreen("Check the request", null, { step = 1 }) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OrvynCard {
                        ReviewRow("Ask", w.name)
                        ReviewRow("Amount", Money.format(amount))
                        ReviewRow("Open for", "7 days")
                    }
                    Column {
                        OrvynTextField("Note (optional)", note, { note = it.take(50) }, helper = "For example \"Lunch on Friday\". ${w.name.substringBefore(' ')} sees this.")
                        Text("${note.length}/50", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary, modifier = Modifier.align(Alignment.End))
                    }
                }
                error?.let { ErrorBox(it, Modifier.padding(top = 12.dp)) }
                OrvynButton("Ask for ${Money.format(amount)}", loading = sending, modifier = Modifier.padding(vertical = 16.dp), onClick = {
                    sending = true
                    scope.launch {
                        try { sentAt = Api.askForMoney(w.accountNumber, amount, note.trim()) ?: java.time.Instant.now(); DataVersion.count++; step = 3 }
                        catch (e: ApiException) { error = e.message }
                        sending = false
                    }
                })
            }
            else -> SuccessScreen("Request sent", Money.format(amount), "asked of ${w.name}", date = sentAt, buzz = false,
                details = { if (note.isNotBlank()) ReceiptLine("Note", note.trim()); ReceiptLine("Open for", "7 days") }, onPrimary = onExit)
        }
    }
}

/** S2.7: total → friends → shares (equal by default, each one editable) → sent. */
@Composable
fun SplitFlow(startTotal: Long?, onExit: () -> Unit) {
    var step by remember { mutableIntStateOf(if (startTotal != null) 1 else 0) }
    var total by remember { mutableLongStateOf(startTotal ?: 0L) }
    var friends by remember { mutableStateOf<List<Recipient>>(emptyList()) }
    val shares = remember { mutableStateListOf<Long>() }
    var note by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Int?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler(step in 1..2) { step -= 1 }
    when (step) {
        0 -> AmountScreen("Split a bill", "How much was the whole bill?", onExit, keypad = { AmountKeypad(total, { total = it }) },
            button = { OrvynButton("Next", enabled = total >= 2_000, onClick = { step = 1 }) }) {
            AmountDisplay(total, null, null, onQuickPick = { total = it })
        }
        1 -> PeoplePicker("Who's in?", "Pick up to 9 friends on ORVYN. You pay your own share.", multi = true, initial = friends, onBack = { if (startTotal != null) onExit() else step = 0 }) {
            picked ->
            friends = picked
            shares.clear(); repeat(picked.size) { shares.add(total / (picked.size + 1)) } // equal, rounded down; the creator covers the rest
            step = 2
        }
        2 -> {
            val friendsTotal = shares.sum()
            FlowScreen("Check the shares", "Total ${Money.format(total)}. Tap a share to change it.", { step = 1 }) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    friends.forEachIndexed { i, f ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).clickable { editing = i }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            InitialsAvatar(f.name)
                            Text(f.nickname ?: f.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(Money.format(shares[i]), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Your share", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(Money.format(total - friendsTotal), style = MaterialTheme.typography.titleMedium, color = if (friendsTotal > total) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground)
                    }
                    OrvynTextField("What for (optional)", note, { note = it.take(50) }, helper = "Your friends see this, like \"Dinner at Bakmi 88\"")
                    if (friendsTotal > total) Text("Your friends' shares add up to more than the bill.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    error?.let { ErrorBox(it) }
                }
                OrvynButton("Send ${friends.size} ${if (friends.size == 1) "request" else "requests"}", loading = sending,
                    enabled = friendsTotal <= total && shares.all { it >= 1_000 }, modifier = Modifier.padding(vertical = 16.dp), onClick = {
                        scope.launch {
                            sending = true
                            try { Api.splitBill(total, note.trim(), friends.map { it.accountNumber }.zip(shares)); DataVersion.count++; step = 3 }
                            catch (e: ApiException) { error = e.message }
                            sending = false
                        }
                    })
            }
            editing?.let { i ->
                var v by remember(i) { mutableLongStateOf(shares[i]) }
                OrvynBottomSheet(onDismiss = { editing = null }) {
                    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${friends[i].name.substringBefore(' ')}'s share", style = MaterialTheme.typography.titleLarge)
                        AmountDisplay(v, null, null, onQuickPick = { v = it })
                        AmountKeypad(v, { v = it })
                        OrvynButton("Save", enabled = v >= 1_000, onClick = { shares[i] = v; editing = null }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        else -> SuccessScreen("Requests sent", Money.format(shares.sum()), "asked of ${friends.size} ${if (friends.size == 1) "friend" else "friends"}", buzz = false, date = remember { java.time.Instant.now() },
            details = { friends.forEachIndexed { i, f -> ReceiptLine(f.name, Money.format(shares[i])) }; ReceiptLine("Your share", Money.format(total - shares.sum())) },
            primaryLabel = "Done", onPrimary = onExit)
    }
}
