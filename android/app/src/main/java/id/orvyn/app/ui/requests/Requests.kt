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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import id.orvyn.app.ui.QUICK_AMOUNTS
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.AppConfig
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

/** design.md 2.2 and 2.4 (H91-1): why the amount button is off, or null when the amount can be asked for. */
internal fun belowMinimum(amount: Long, min: Long) = when { amount == 0L -> "Enter an amount"; amount < min -> "Minimum ${Money.format(min)}"; else -> null }

/** MASTER Amount input: no Quick Amount under the minimum. */
internal fun quickAmountsFrom(min: Long) = QUICK_AMOUNTS.filter { it >= min }

/** design.md 2.3: the send button on "Check the shares", and whether it's on. */
internal fun sendSharesButton(shares: List<Long>, total: Long, min: Long): Pair<String, Boolean> = when {
    shares.any { it < min } -> "Minimum ${Money.format(min)} a share" to false
    else -> "Send ${shares.size} ${if (shares.size == 1) "request" else "requests"}" to (shares.sum() <= total)
}

/** design.md 2.3: shown when no change of shares can reach the minimum for every friend. */
internal fun tooSmallForFriends(total: Long, friends: Int, min: Long) =
    if (total < friends * min) "This bill is too small for $friends friends at ${Money.format(min)} each. Go back and pick fewer friends." else null

/**
 * One friend's share on "Check the shares" (design.md 2.3). At large font sizes it stacks like a large ListRow: the name,
 * then "Needs at least …" wrapping under it, then the amount on its own line (H91-F3, Q-A4).
 */
@Composable
internal fun ShareRow(name: String, avatarName: String, amount: Long, min: Long, onClick: () -> Unit) {
    val low = amount < min
    val label = "$name, ${Money.format(amount)}" + if (low) ", Needs at least ${Money.format(min)}" else ""
    val large = isLargeFont()
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
        .clickable(role = Role.Button, onClickLabel = "Change share", onClick = onClick).semantics { contentDescription = label }.padding(12.dp),
        verticalAlignment = if (large) Alignment.Top else Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InitialsAvatar(avatarName)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            if (low) GroupError("Needs at least ${Money.format(min)}")
            if (large) Text(Money.format(amount), style = MaterialTheme.typography.titleMedium)
        }
        if (!large) Text(Money.format(amount), style = MaterialTheme.typography.titleMedium)
    }
}

/** An icon and a sentence in `error`; [live] announces it once when it appears. */
@Composable
private fun Warning(text: String, live: Boolean = false) =
    Box(Modifier.semantics(mergeDescendants = true) { if (live) liveRegion = LiveRegionMode.Polite }) { GroupError(text) }

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
    LaunchedEffect(Unit) {
        val loaded = runCatching { Api.recipients().let { (it.saved + it.recent).filter { r -> r.internal }.distinctBy { r -> r.accountNumber } } }.getOrDefault(emptyList())
        // H91-F2: friends picked earlier with Find aren't saved or recent; keep them on the list so they can be seen and unticked.
        known = initial.filter { i -> loaded.none { it.accountNumber == i.accountNumber } } + loaded
    }
    fun toggle(r: Recipient) {
        if (!multi) return onDone(listOf(r))
        if (chosen.any { it.accountNumber == r.accountNumber }) chosen.removeAll { it.accountNumber == r.accountNumber }
        else if (chosen.size < 9) chosen.add(r)
    }
    fun find() {
        scope.launch {
            looking = true
            try { val r = Api.lookup("ORVYN", number); toggle(r); if (multi) { known = listOf(r) + (known ?: emptyList()).filter { it.accountNumber != r.accountNumber }; number = "" } }
            catch (e: ApiException) { lookupError = e.message }
            looking = false
        }
    }
    FlowScreen(title, body, onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrvynTextField("ORVYN account number", number, { number = it.filter(Char::isDigit).take(10); lookupError = null },
                    keyboardType = KeyboardType.Number, error = lookupError, modifier = Modifier.weight(1f),
                    imeAction = ImeAction.Done, onImeAction = doneAction(number.length == 10 && !looking) { find() })
            }
            OrvynButton("Find", loading = looking, enabled = number.length == 10, kind = ButtonKind.Secondary, onClick = { find() })
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
                button = { OrvynButton(belowMinimum(amount, AppConfig.minTransfer) ?: "Continue", enabled = amount >= AppConfig.minTransfer, onClick = { error = null; step = 2 }) }) {
                AmountDisplay(amount, null, null, onQuickPick = { amount = it }, quickAmounts = quickAmountsFrom(AppConfig.minTransfer))
            }
            s == 2 -> FlowScreen("Check the request", null, { step = 1 }) {
                fun send() {
                    sending = true
                    scope.launch {
                        try { sentAt = Api.askForMoney(w.accountNumber, amount, note.trim()) ?: java.time.Instant.now(); DataVersion.count++; step = 3 }
                        catch (e: ApiException) { error = e.message; if (e.code == "BAD_AMOUNT") runCatching { Api.loadConfig() } } // the minimum may have changed (design.md 2.5)
                        sending = false
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OrvynCard {
                        ReviewRow("Ask", w.name)
                        ReviewRow("Amount", Money.format(amount))
                        ReviewRow("Open for", "7 days")
                    }
                    Column {
                        OrvynTextField("Note (optional)", note, { note = it.take(50); error = null }, helper = "For example \"Lunch on Friday\". ${w.name.substringBefore(' ')} sees this.",
                            imeAction = ImeAction.Done, onImeAction = doneAction(!sending) { send() })
                        Text("${note.length}/50", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary, modifier = Modifier.align(Alignment.End))
                    }
                }
                error?.let { ErrorBox(it, Modifier.padding(top = 12.dp)) }
                OrvynButton("Ask for ${Money.format(amount)}", loading = sending, modifier = Modifier.padding(vertical = 16.dp), onClick = { send() })
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
            button = { OrvynButton(belowMinimum(total, AppConfig.minTransfer) ?: "Next", enabled = total >= AppConfig.minTransfer, onClick = { step = 1 }) }) {
            AmountDisplay(total, null, null, onQuickPick = { total = it }, quickAmounts = quickAmountsFrom(AppConfig.minTransfer))
        }
        1 -> PeoplePicker("Who's in?", "Pick up to 9 friends on ORVYN. You pay your own share.", multi = true, initial = friends, onBack = { if (startTotal != null) onExit() else step = 0 }) {
            picked ->
            friends = picked
            shares.clear(); repeat(picked.size) { shares.add(total / (picked.size + 1)) } // equal, rounded down; the creator covers the rest
            step = 2
        }
        2 -> {
            val min = AppConfig.minTransfer
            val friendsTotal = shares.sum()
            val (sendLabel, sendOn) = sendSharesButton(shares, total, min)
            fun send() {
                scope.launch {
                    sending = true
                    try { Api.splitBill(total, note.trim(), friends.map { it.accountNumber }.zip(shares)); DataVersion.count++; step = 3 }
                    catch (e: ApiException) { error = e.message; if (e.code == "BAD_AMOUNT") runCatching { Api.loadConfig() } } // the minimum may have changed (design.md 2.5)
                    sending = false
                }
            }
            FlowScreen("Check the shares", "Total ${Money.format(total)}. Tap a share to change it.", { step = 1 }) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    friends.forEachIndexed { i, f -> ShareRow(f.nickname ?: f.name, f.name, shares[i], min) { editing = i } }
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Your share", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text((if (friendsTotal > total) "−" else "") + Money.format(total - friendsTotal), style = MaterialTheme.typography.titleMedium, color = if (friendsTotal > total) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground)
                    }
                    OrvynTextField("What for (optional)", note, { note = it.take(50); error = null }, helper = "Your friends see this, like \"Dinner at Bakmi 88\"",
                        imeAction = ImeAction.Done, onImeAction = doneAction(sendOn && !sending) { send() })
                    tooSmallForFriends(total, friends.size, min)?.let { Warning(it, live = true) }
                    if (friendsTotal > total) Warning("Your friends' shares add up to more than the bill.")
                    error?.let { ErrorBox(it) }
                }
                OrvynButton(sendLabel, loading = sending, enabled = sendOn, modifier = Modifier.padding(vertical = 16.dp), onClick = { send() })
            }
            editing?.let { i ->
                var v by remember(i) { mutableLongStateOf(shares[i]) }
                OrvynBottomSheet(onDismiss = { editing = null }) {
                    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${friends[i].name.substringBefore(' ')}'s share", style = MaterialTheme.typography.titleLarge)
                            Text("The smallest share is ${Money.format(min)}.", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                        }
                        AmountDisplay(v, null, null, onQuickPick = { v = it }, quickAmounts = quickAmountsFrom(min))
                        AmountKeypad(v, { v = it })
                        OrvynButton(belowMinimum(v, min) ?: "Save", enabled = v >= min, onClick = { shares[i] = v; error = null; editing = null }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        else -> SuccessScreen("Requests sent", Money.format(shares.sum()), "asked of ${friends.size} ${if (friends.size == 1) "friend" else "friends"}", buzz = false, date = remember { java.time.Instant.now() },
            details = { friends.forEachIndexed { i, f -> ReceiptLine(f.name, Money.format(shares[i])) }; ReceiptLine("Your share", Money.format(total - shares.sum())) },
            primaryLabel = "Done", onPrimary = onExit)
    }
}
