package id.orvyn.app.ui.transfer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.Bank
import id.orvyn.app.data.DataVersion
import id.orvyn.app.data.Method
import id.orvyn.app.data.Recipient
import id.orvyn.app.data.Recipients
import id.orvyn.app.data.TransferOptions
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.components.AmountDisplay
import id.orvyn.app.ui.components.AmountKeypad
import id.orvyn.app.ui.components.ButtonKind
import id.orvyn.app.ui.components.InitialsAvatar
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OrvynBottomSheet
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.OrvynTextField
import id.orvyn.app.ui.components.PinPad
import id.orvyn.app.ui.components.ReceiptLine
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.components.SuccessScreen
import id.orvyn.app.ui.components.WhiteTextButton
import id.orvyn.app.ui.groupAccountNumber
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.launch
import java.util.UUID

private enum class Step { Pick, New, Amount, Review, Pin, Done }

/** Stories S1.12–S1.14: recipient → amount → review → PIN → done, with back going one step at a time. */
@Composable
fun TransferFlow(onExit: () -> Unit, startWith: Recipient? = null, startAmount: Long? = null) {
    // Coming from a scanned My QR: skip straight to the amount (or review, if the QR carried one).
    var step by remember { mutableStateOf(if (startWith == null) Step.Pick else if (startAmount != null) Step.Review else Step.Amount) }
    var to by remember { mutableStateOf(startWith) }
    var amount by remember { mutableLongStateOf(startAmount ?: 0L) }
    var note by remember { mutableStateOf("") }
    var method by remember { mutableStateOf<Method?>(null) }
    var reference by remember { mutableStateOf("") }
    var reviewError by remember { mutableStateOf<String?>(null) }
    // One key per review screen: a double tap or a retry after a timeout can't send twice.
    var idempotencyKey by remember { mutableStateOf(UUID.randomUUID().toString()) }

    BackHandler(step != Step.Done) {
        step = when (step) {
            Step.Pick -> { onExit(); Step.Pick }
            Step.New, Step.Amount -> if (to?.id != null || step == Step.New) Step.Pick else Step.New
            Step.Review -> Step.Amount
            Step.Pin -> Step.Review
            Step.Done -> Step.Done
        }
    }

    when (step) {
        Step.Pick -> PickRecipient(onBack = onExit, onNew = { step = Step.New }, onPick = { to = it; amount = 0; step = Step.Amount })
        Step.New -> NewRecipient(onBack = { step = Step.Pick }, onFound = { to = it; amount = 0; step = Step.Amount })
        Step.Amount -> EnterAmount(to!!, amount, onBack = { step = Step.Pick }) { a -> amount = a; reviewError = null; idempotencyKey = UUID.randomUUID().toString(); step = Step.Review }
        Step.Review -> Review(to!!, amount, note, { note = it }, reviewError, onBack = { step = Step.Amount }) { m -> method = m; step = Step.Pin }
        Step.Pin -> EnterPin(to!!, amount, method!!, note, idempotencyKey, onBack = { step = Step.Review },
            onSent = { ref -> reference = ref; DataVersion.count++; step = Step.Done },
            onRefused = { msg -> reviewError = msg; step = Step.Review })
        Step.Done -> Done(to!!, amount, method!!, note, reference, onDone = onExit)
    }
}

@Composable
private fun PickRecipient(onBack: () -> Unit, onNew: () -> Unit, onPick: (Recipient) -> Unit) {
    var list by remember { mutableStateOf<Recipients?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { try { list = Api.recipients() } catch (e: ApiException) { error = e.message } }

    FlowScreen("Send money", null, onBack) {
        OrvynButton("New recipient", onClick = onNew, kind = ButtonKind.Secondary)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search name or account number") },
            leadingIcon = { Icon(painterResource(R.drawable.ph_magnifying_glass), null) },
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline, unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface),
        )
        val l = list
        val match = { r: Recipient -> query.isBlank() || listOfNotNull(r.name, r.nickname, r.accountNumber).any { it.contains(query.trim(), ignoreCase = true) } }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            when {
                error != null -> item { MessageState(R.drawable.ph_warning_circle, "Couldn't load your recipients", error!!) }
                l == null -> items(4) { Skeleton(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(44.dp)) }
                l.saved.isEmpty() && l.recent.isEmpty() -> item {
                    MessageState(R.drawable.ph_arrows_left_right, "No recipients yet", "Tap New recipient to send money to someone for the first time.")
                }
                else -> {
                    for ((title, group) in listOf("Saved" to l.saved.filter(match), "Recent" to l.recent.filter(match))) {
                        if (group.isEmpty()) continue
                        item { Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
                        items(group, key = { title + it.bank + it.accountNumber }) { r ->
                            RecipientRowFlush(r) { onPick(r) }
                        }
                    }
                }
            }
        }
    }
}

// Rows inside FlowScreen already have the 20dp gutter, so drop the row's own horizontal padding.
@Composable
private fun RecipientRowFlush(r: Recipient, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).heightIn(min = 64.dp).padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    InitialsAvatar(r.nickname ?: r.name)
    Column(Modifier.weight(1f)) {
        Text(r.nickname ?: r.name, style = MaterialTheme.typography.titleMedium)
        Text(listOfNotNull(r.nickname?.let { r.name }, "${r.bankName} · ${groupAccountNumber(r.accountNumber)}").joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
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
private fun RecipientHeader(to: Recipient) {
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

private const val MIN_TRANSFER = 10_000L

@Composable
private fun EnterAmount(to: Recipient, initial: Long, onBack: () -> Unit, onContinue: (Long) -> Unit) {
    var amount by remember { mutableLongStateOf(initial) }
    var opts by remember { mutableStateOf<TransferOptions?>(null) }
    LaunchedEffect(Unit) { runCatching { opts = Api.transferOptions(to.bank, 0) } }
    val o = opts
    val error = when {
        o == null || amount == 0L -> null
        amount > o.balance -> "You're ${Money.format(amount - o.balance)} short."
        amount > o.remainingToday -> "That's over your daily limit. You can still send ${Money.format(o.remainingToday)} today."
        else -> null
    }
    FlowScreen("How much?", null, onBack) {
        RecipientHeader(to)
        Spacer(Modifier.weight(1f))
        AmountDisplay(amount, o?.balance, error, onQuickPick = { amount = it })
        Spacer(Modifier.weight(1f))
        AmountKeypad(amount, { amount = it })
        OrvynButton(
            when { amount == 0L -> "Enter an amount"; amount < MIN_TRANSFER -> "Minimum ${Money.format(MIN_TRANSFER)}"; else -> "Continue" },
            enabled = amount >= MIN_TRANSFER && error == null && o != null,
            onClick = { onContinue(amount) }, modifier = Modifier.padding(vertical = 16.dp),
        )
    }
}

@Composable
private fun Review(to: Recipient, amount: Long, note: String, onNote: (String) -> Unit, error: String?, onBack: () -> Unit, onConfirm: (Method) -> Unit) {
    var opts by remember { mutableStateOf<TransferOptions?>(null) }
    var chosen by remember { mutableStateOf<Method?>(null) }
    LaunchedEffect(Unit) {
        runCatching { Api.transferOptions(to.bank, amount) }.onSuccess { opts = it; chosen = it.methods.firstOrNull() }
    }
    val fee = chosen?.fee ?: 0
    FlowScreen("Check the details", null, onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RecipientHeader(to)
            val o = opts
            if (o == null) Skeleton(Modifier.fillMaxWidth().height(180.dp)) else {
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
                    ReviewRow("Balance after", Money.format(o.balance - amount - fee))
                }
            }
            OrvynTextField("Note (optional)", note, { onNote(it.take(50)) }, helper = "${note.length}/50 · The recipient sees this.")
            if (error != null) Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        OrvynButton("Send ${Money.format(amount)}", enabled = chosen != null, onClick = { chosen?.let(onConfirm) }, modifier = Modifier.padding(vertical = 16.dp))
    }
}

/** Label left, value right: easy to scan before approving money. */
@Composable
private fun ReviewRow(label: String, value: String, strong: Boolean = false) = Row(verticalAlignment = Alignment.Top) {
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

@Composable
private fun EnterPin(to: Recipient, amount: Long, method: Method, note: String, key: String, onBack: () -> Unit, onSent: (String) -> Unit, onRefused: (String) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    FlowScreen("Enter your PIN", "To send ${Money.format(amount)} to ${to.name}.", onBack) {
        Spacer(Modifier.weight(1f))
        if (busy) Text("Sending…", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
        PinPad(
            onComplete = { pin ->
                busy = true
                scope.launch {
                    try { onSent(Api.sendTransfer(to, amount, method.method, note, pin, key)) } catch (e: ApiException) {
                        when (e.code) {
                            "WRONG_PIN" -> { error = e.message; errors++ }
                            "LOCKED", "signed_out" -> { error = e.message; errors++ } // the app signs out and returns to the PIN screen
                            else -> onRefused(e.message)
                        }
                    } finally { busy = false }
                }
            },
            errorCount = errors, errorText = error, onBiometric = null, modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun Done(to: Recipient, amount: Long, method: Method, note: String, reference: String, onDone: () -> Unit) {
    var saved by remember { mutableStateOf(to.id != null) }
    val scope = rememberCoroutineScope()
    SuccessScreen(
        headline = "Sent",
        amount = Money.format(amount),
        subline = "to ${to.name}",
        details = {
            ReceiptLine("To", "${to.bankName} · ${groupAccountNumber(to.accountNumber)}")
            ReceiptLine("Method", if (method.fee == 0L) method.label else "${method.label} · fee ${Money.format(method.fee)}")
            if (note.isNotBlank()) ReceiptLine("Note", note)
            ReceiptLine("Reference", reference)
        },
        primaryLabel = "Done",
        onPrimary = onDone,
        secondary = when {
            to.id != null -> null
            saved -> { { Text("${to.name.substringBefore(' ')} is in your saved recipients", style = MaterialTheme.typography.labelLarge,
                color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(vertical = 14.dp)) } }
            else -> { { WhiteTextButton("Save ${to.name.substringBefore(' ')} as a recipient") { scope.launch { runCatching { Api.saveRecipient(to, null) }.onSuccess { saved = true } } } } }
        },
    )
}
