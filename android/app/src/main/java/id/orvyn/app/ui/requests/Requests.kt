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
import id.orvyn.app.ui.components.AmountDisplay
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

private fun statusText(r: MoneyRequest) = when (r.status) {
    "open" -> if (r.mine) "Waiting" else "To pay"
    "paid" -> "Paid"; "declined" -> "Declined"; "cancelled" -> "Cancelled"; else -> "Expired"
}

@Composable
private fun StatusLabel(status: String, text: String) = Text(text, style = MaterialTheme.typography.labelMedium, color = when (status) {
    "paid" -> OrvynTheme.colors.success
    "declined", "expired", "cancelled" -> OrvynTheme.colors.textSecondary
    else -> OrvynTheme.colors.warning
})

@Composable
private fun Section(text: String) = Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary,
    modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))

/** Everything asked of me, everything I asked, and my splits. */
@Composable
fun RequestsScreen(onBack: () -> Unit, onAsk: () -> Unit, onSplit: () -> Unit, onOpen: (String) -> Unit) {
    var data by remember { mutableStateOf<Pair<List<MoneyRequest>, List<Split>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(DataVersion.count) { try { data = Api.requests(); error = null } catch (e: ApiException) { error = e.message } }
    FlowScreen("Requests", null, onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrvynButton("Ask for money", onClick = onAsk, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
            OrvynButton("Split a bill", onClick = onSplit, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
        }
        val d = data
        when {
            d == null && error != null -> MessageState(R.drawable.ph_warning_circle, "Couldn't load your requests", error!!)
            d == null -> Skeleton(Modifier.padding(top = 16.dp).fillMaxWidth().height(160.dp))
            d.first.isEmpty() && d.second.isEmpty() -> MessageState(R.drawable.ph_hand_coins, "No requests yet",
                "Ask a friend on ORVYN for money, or split a bill and everyone gets their share to pay.")
            else -> LazyColumn {
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
        Row(Modifier.fillMaxWidth().clickable { onOpen(r.id) }.heightIn(min = 64.dp).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InitialsAvatar(other)
            Column(Modifier.weight(1f)) {
                Text(if (r.mine) "You asked $other" else "$other asks you", style = MaterialTheme.typography.titleMedium)
                Text(listOfNotNull(r.note, dayLabel(r.at)).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(r.amount), style = MaterialTheme.typography.titleMedium)
                StatusLabel(r.status, statusText(r))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SplitCard(s: Split) {
    val paid = s.people.filter { it.status == "paid" }.sumOf { it.amount }
    Column(Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(s.note ?: "Split bill", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(Money.format(s.total), style = MaterialTheme.typography.titleMedium)
        }
        Text("${Money.format(paid)} of ${Money.format(s.people.sumOf { it.amount })} back from friends", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
        for (p in s.people) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(Money.format(p.amount), style = MaterialTheme.typography.bodyLarge)
            StatusLabel(p.status, when (p.status) { "open" -> "Waiting"; "paid" -> "Paid"; "declined" -> "Declined"; else -> p.status.replaceFirstChar(Char::uppercase) })
        }
    }
}

/** One request: pay or decline it (asked of me), or cancel it (mine). Paying needs the PIN, like any transfer. */
@Composable
fun RequestScreen(id: String, onBack: () -> Unit) {
    var r by remember { mutableStateOf<MoneyRequest?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var paying by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinErrors by remember { mutableIntStateOf(0) }
    var reference by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) } // "decline" or "cancel"
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) { try { r = Api.request(id) } catch (e: ApiException) { error = e.message } }
    val q = r
    BackHandler(paying && reference == null) { paying = false }
    if (q != null && reference != null) return SuccessScreen("Paid", Money.format(q.amount), "to ${q.from}",
        details = { q.note?.let { ReceiptLine("Note", it) }; ReceiptLine("Reference", reference!!) }, primaryLabel = "Done", onPrimary = onBack)
    if (q != null && paying) return FlowScreen("Enter your PIN", "To pay ${Money.format(q.amount)} to ${q.from}.", { paying = false }) {
        Spacer(Modifier.weight(1f))
        PinPad(onComplete = { pin ->
            scope.launch {
                try { reference = Api.payRequest(q.id, pin); DataVersion.count++ }
                catch (e: ApiException) { if (e.code == "WRONG_PIN") { pinError = e.message; pinErrors++ } else { error = e.message; paying = false } }
            }
        }, errorCount = pinErrors, errorText = pinError, onBiometric = null, modifier = Modifier.padding(bottom = 24.dp))
    }
    FlowScreen("Money request", null, onBack) {
        if (q == null) {
            error?.let { MessageState(R.drawable.ph_warning_circle, "Couldn't load this request", it) } ?: Skeleton(Modifier.fillMaxWidth().height(160.dp))
            return@FlowScreen
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InitialsAvatar(if (q.mine) q.to else q.from)
                Text(if (q.mine) "You asked ${q.to}" else "${q.from} asks you", style = MaterialTheme.typography.titleLarge)
            }
            Text(Money.format(q.amount), style = OrvynType.displayBalance)
            q.note?.let { Text("“$it”", style = MaterialTheme.typography.bodyLarge) }
            StatusLabel(q.status, when (q.status) {
                "open" -> if (q.mine) "Waiting for ${q.to.substringBefore(' ')}" else "Sent ${dayLabel(q.at)}. Open for 7 days."
                else -> statusText(q)
            })
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        }
        if (q.status == "open") {
            if (q.mine) OrvynButton("Cancel request", onClick = { confirm = "cancel" }, kind = ButtonKind.Secondary, modifier = Modifier.padding(vertical = 16.dp))
            else {
                OrvynButton("Pay ${Money.format(q.amount)}", onClick = { error = null; paying = true }, modifier = Modifier.padding(top = 16.dp))
                OrvynButton("Decline", onClick = { confirm = "decline" }, kind = ButtonKind.Secondary, modifier = Modifier.padding(vertical = 10.dp))
            }
        }
    }
    confirm?.let { what ->
        ConfirmDialog(
            title = if (what == "decline") "Decline this request?" else "Cancel this request?",
            body = if (what == "decline") "${q?.from?.substringBefore(' ')} gets a message that you declined." else "${q?.to?.substringBefore(' ')} won't be able to pay it anymore.",
            confirmLabel = if (what == "decline") "Decline" else "Cancel request", destructive = true, onDismiss = { confirm = null },
            onConfirm = {
                confirm = null
                scope.launch {
                    try { if (what == "decline") Api.declineRequest(id) else Api.cancelRequest(id); DataVersion.count++; r = Api.request(id) }
                    catch (e: ApiException) { error = e.message }
                }
            },
        )
    }
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

/** S2.6: person → amount and note → sent. */
@Composable
fun AskFlow(onExit: () -> Unit) {
    var who by remember { mutableStateOf<Recipient?>(null) }
    var amount by remember { mutableLongStateOf(0L) }
    var note by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val w = who
    BackHandler(w != null && !sent) { who = null }
    when {
        w == null -> PeoplePicker("Ask for money", "From someone with an ORVYN account.", multi = false, initial = emptyList(), onBack = onExit) { who = it.first() }
        sent -> SuccessScreen("Request sent", Money.format(amount), "asked of ${w.name}",
            details = { if (note.isNotBlank()) ReceiptLine("Note", note); ReceiptLine("Open for", "7 days") }, primaryLabel = "Done", onPrimary = onExit)
        else -> FlowScreen("Ask ${w.name.substringBefore(' ')}", "They get a notification and pay with one tap and their PIN.", { who = null }) {
            Column(Modifier.weight(1f)) {
                AmountDisplay(amount, null, error, onQuickPick = { amount = it })
                Spacer(Modifier.height(16.dp))
                OrvynTextField("Note (optional)", note, { note = it.take(50) }, helper = "For example \"Lunch on Friday\"")
                Spacer(Modifier.weight(1f))
                AmountKeypad(amount, { amount = it; error = null })
            }
            OrvynButton(if (amount >= 1_000) "Ask for ${Money.format(amount)}" else "Ask for at least Rp1,000", enabled = amount >= 1_000, loading = sending,
                modifier = Modifier.padding(vertical = 16.dp), onClick = {
                    scope.launch {
                        sending = true
                        try { Api.askForMoney(w.accountNumber, amount, note.trim()); DataVersion.count++; sent = true } catch (e: ApiException) { error = e.message }
                        sending = false
                    }
                })
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
        0 -> FlowScreen("Split a bill", "How much was the whole bill?", onExit) {
            Column(Modifier.weight(1f)) {
                AmountDisplay(total, null, null, onQuickPick = { total = it })
                Spacer(Modifier.weight(1f))
                AmountKeypad(total, { total = it })
            }
            OrvynButton("Next", enabled = total >= 2_000, onClick = { step = 1 }, modifier = Modifier.padding(vertical = 16.dp))
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
                    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
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
        else -> SuccessScreen("Requests sent", Money.format(shares.sum()), "asked of ${friends.size} ${if (friends.size == 1) "friend" else "friends"}",
            details = { friends.forEachIndexed { i, f -> ReceiptLine(f.name, Money.format(shares[i])) }; ReceiptLine("Your share", Money.format(total - shares.sum())) },
            primaryLabel = "Done", onPrimary = onExit)
    }
}
