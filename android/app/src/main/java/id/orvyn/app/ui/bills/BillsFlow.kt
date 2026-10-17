package id.orvyn.app.ui.bills

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.BillPaid
import id.orvyn.app.data.Biller
import id.orvyn.app.data.DataVersion
import id.orvyn.app.data.Inquiry
import id.orvyn.app.data.Product
import id.orvyn.app.ui.components.*
import id.orvyn.app.ui.shareTransaction
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.components.AmountDisplay
import id.orvyn.app.ui.components.AmountKeypad
import id.orvyn.app.ui.components.ListRow
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.OrvynTextField
import id.orvyn.app.ui.components.PinPad
import id.orvyn.app.ui.components.ReceiptLine
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.components.SuccessScreen
import id.orvyn.app.ui.components.WhiteTextButton
import id.orvyn.app.ui.theme.Brand
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.launch
import java.util.UUID

private sealed interface BStep {
    val index: Int
    data object Hub : BStep { override val index = 0 }
    data class Number(val biller: Biller) : BStep { override val index = 1 }
    data class Details(val q: Inquiry, val error: String? = null) : BStep { override val index = 2 }
    data class Pin(val q: Inquiry, val product: Product?, val amount: Long) : BStep { override val index = 3 }
    data class Done(val q: Inquiry, val paid: BillPaid) : BStep { override val index = 4 }
}

/** Q-E10: a different icon for each kind of bill. */
private fun iconFor(kind: String) = when (kind) {
    "pln_token", "pln_postpaid" -> R.drawable.ph_lightning
    "bpjs" -> R.drawable.ph_first_aid
    "mobile" -> R.drawable.ph_device_mobile
    else -> R.drawable.ph_wallet
}

private fun numberLabel(kind: String) = when (kind) {
    "pln_token" -> "Meter number"
    "pln_postpaid" -> "PLN customer ID"
    "bpjs" -> "BPJS number"
    else -> "Mobile number"
}

/** Q-G4: "2026-10" → "Oct 2026". */
fun billPeriod(period: String) = runCatching { java.time.YearMonth.parse(period).format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.US)) }.getOrDefault(period)

/** The same 4-digit prefixes as the server's operatorFor (Q-F5, PO-23). */
private val OPERATORS = listOf(
    "Telkomsel" to listOf("0811", "0812", "0813", "0821", "0822", "0823", "0851", "0852", "0853"),
    "Indosat" to listOf("0814", "0815", "0816", "0855", "0856", "0857", "0858"),
    "XL" to listOf("0817", "0818", "0819", "0859", "0877", "0878"),
    "Axis" to listOf("0831", "0832", "0833", "0838"),
    "Tri" to listOf("0895", "0896", "0897", "0898", "0899"),
    "Smartfren" to listOf("0881", "0882", "0883", "0884", "0885", "0886", "0887", "0888", "0889"),
)
fun operatorFor(phone: String): String? { val p = phone.filter(Char::isDigit).replace(Regex("^62"), "0"); return OPERATORS.firstOrNull { p.take(4) in it.second }?.first }

/** Stories S1.18–S1.21. [ewalletsOnly] is the "Top up" shortcut. [phone] is the customer's own number, from the server (S1.20). */
@Composable
fun BillsFlow(onExit: () -> Unit, ewalletsOnly: Boolean = false, phone: String? = null) {
    var step by remember { mutableStateOf<BStep>(BStep.Hub) }
    val billers = rememberLoad { Api.billers() }
    val key = remember(step is BStep.Pin) { UUID.randomUUID().toString() }
    val context = LocalContext.current

    BackHandler(step !is BStep.Done) {
        step = when (val s = step) {
            BStep.Hub -> { onExit(); BStep.Hub }
            is BStep.Number -> BStep.Hub
            is BStep.Details -> billers.value?.find { it.code == s.q.biller }?.let { BStep.Number(it) } ?: BStep.Hub
            is BStep.Pin -> BStep.Details(s.q)
            is BStep.Done -> s
        }
    }

    FlowSteps(step, index = { it.index }, success = { it is BStep.Done }) { s ->
        when (s) {
            BStep.Hub -> FlowScreen(if (ewalletsOnly) "Top up an e-wallet" else "Pay and buy", null, onExit) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LoadContent(billers, "Couldn't load this list", skeleton = { Column { repeat(5) { Skeleton(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(48.dp)) } } }) { all ->
                        val list = all.filter { !ewalletsOnly || it.kind == "ewallet" }
                        Column {
                            for ((title, kinds) in listOf("Electricity" to setOf("pln_token", "pln_postpaid"), "Health" to setOf("bpjs"), "Phone" to setOf("mobile"), "E-wallets" to setOf("ewallet"))) {
                                val group = list.filter { it.kind in kinds }
                                if (group.isEmpty()) continue
                                Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                                Column(Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)) {
                                    group.forEachIndexed { i, b ->
                                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        ListRow(b.name, if (b.fee > 0) "Fee ${Money.format(b.fee)}" else "No fee", iconFor(b.kind), onClick = { step = BStep.Number(b) })
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            is BStep.Number -> EnterNumber(s.biller, phone, onBack = { step = BStep.Hub }) { step = BStep.Details(it) }
            is BStep.Details -> Details(s.q, s.error, onBack = { billers.value?.find { it.code == s.q.biller }?.let { b -> step = BStep.Number(b) } ?: run { step = BStep.Hub } }) { p, a -> step = BStep.Pin(s.q, p, a) }
            is BStep.Pin -> BillPin(s.q, s.product, s.amount, key, onBack = { step = BStep.Details(s.q) }, onRefused = { step = BStep.Details(s.q, it) }) { DataVersion.count++; step = BStep.Done(s.q, it) }
            is BStep.Done -> Receipt(s.q, s.paid, onExit, onShare = s.paid.transactionId?.let { id -> { shareTransaction(context, id) } })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnterNumber(b: Biller, myPhone: String?, onBack: () -> Unit, onFound: (Inquiry) -> Unit) {
    var number by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun check(n: String) {
        busy = true
        scope.launch { try { onFound(Api.inquire(b.code, n)) } catch (e: ApiException) { error = e.message } finally { busy = false } }
    }
    val mobile = b.kind == "mobile"
    // Q-F5 (PO-23): the operator shows from the 4th digit, before Continue.
    val op = if (mobile && number.length >= 4) operatorFor(number) else null
    val unknown = mobile && number.length >= 4 && op == null
    FlowScreen(b.name, null, onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (mobile && myPhone != null) Column(Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)) {
                ListRow("My number", listOfNotNull(myPhone.chunked(4).joinToString(" "), operatorFor(myPhone)).joinToString(" · "), R.drawable.ph_device_mobile,
                    onClick = { number = myPhone; error = null; check(myPhone) })
            }
            OrvynTextField(numberLabel(b.kind), number, { number = it.filter(Char::isDigit).take(16); error = null }, keyboardType = KeyboardType.Number,
                error = error ?: if (unknown) "We don't know this operator. Check the first 4 digits." else null,
                helper = when { !mobile -> null; op != null -> "Operator: $op"; else -> "For example 0812 3456 7890" },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                imeAction = androidx.compose.ui.text.input.ImeAction.Done, onImeAction = doneAction(number.length >= 10 && !unknown && !busy) { check(number) })
            if (b.saved.isNotEmpty()) {
                Text("Paid before", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (sn in b.saved) Box(
                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).clickable { number = sn.number; check(sn.number) }
                            .heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center,
                    ) { Text("${sn.name} · ${sn.number}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
            }
        }
        OrvynButton("Continue", enabled = number.length >= 10 && !unknown, loading = busy, onClick = { check(number) }, modifier = Modifier.padding(vertical = 16.dp))
    }
}

/** What will be paid, and where a failed payment comes back to with its message ([refused]). */
@Composable
private fun Details(q: Inquiry, refused: String?, onBack: () -> Unit, onPay: (Product?, Long) -> Unit) {
    var product by remember { mutableStateOf<Product?>(null) }
    var amount by remember { mutableLongStateOf(0L) }
    val price = q.billAmount ?: product?.price ?: amount
    val summary: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            q.name?.let { ReceiptLine("Name", it, onSurfaceWhite = false) }
            ReceiptLine(numberLabel(q.kind), q.number, onSurfaceWhite = false)
            q.details?.let { ReceiptLine(if (q.kind == "mobile") "Operator" else "Details", it, onSurfaceWhite = false) }
            if (q.billAmount != null) ReceiptLine("Bill for ${billPeriod(q.billPeriod ?: "")}", Money.format(q.billAmount), onSurfaceWhite = false)
        }
    }
    val total: @Composable () -> Unit = {
        if (price > 0) Text(
            if (q.fee > 0) "Total ${Money.format(price + q.fee)}, including a ${Money.format(q.fee)} fee." else "Total ${Money.format(price)}. No fee.",
            style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary,
        )
    }
    val ok = price > 0 && (q.minAmount == null || amount >= q.minAmount)
    val button: @Composable () -> Unit = {
        Column {
            refused?.let { ErrorBox(it, Modifier.padding(bottom = 12.dp)) }
            OrvynButton(when {
                ok -> "Pay ${Money.format(price + q.fee)}"
                q.minAmount != null && amount == 0L -> "Enter an amount"
                q.minAmount != null -> "Minimum ${Money.format(q.minAmount)}" // Q-G6
                else -> "Choose what to pay"
            }, enabled = ok, onClick = { onPay(product, price) })
        }
    }
    // An e-wallet top-up is typed on the keypad, so it gets the fixed keypad layout (design.md 4.7).
    if (q.minAmount != null) AmountScreen(q.billerName, null, onBack, keypad = { AmountKeypad(amount, { amount = it }) }, button = button) {
        summary()
        AmountDisplay(amount, null, if (amount in 1 until q.minAmount) "The smallest top-up is ${Money.format(q.minAmount)}." else null, onQuickPick = { amount = it })
        total()
    } else FlowScreen(q.billerName, null, onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            summary()
            if (q.products.isNotEmpty()) {
                Text("Choose", style = MaterialTheme.typography.titleMedium)
                for (p in q.products) {
                    val sel = p == product
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                            .border(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                            .clickable(role = Role.RadioButton) { product = p }.semantics { selected = sel }.heightIn(min = 56.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(p.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (p.label != Money.format(p.price)) Text(Money.format(p.price), style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary)
                    }
                }
            }
            total()
        }
        Column(Modifier.padding(vertical = 16.dp)) { button() }
    }
}

@Composable
private fun BillPin(q: Inquiry, product: Product?, amount: Long, key: String, onBack: () -> Unit, onRefused: (String) -> Unit, onPaid: (BillPaid) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    PinScreen("Enter your PIN", "To pay ${Money.format(amount + q.fee)} for ${q.billerName}.", onBack, errorCount = errors, errorText = error,
        busyText = if (busy) "Paying ${Money.format(amount + q.fee)}…" else null,
        onComplete = { pin ->
            busy = true; error = null
            scope.launch {
                try { onPaid(Api.payBill(q.biller, q.number, product?.id, if (q.minAmount != null) amount else null, pin, key)) }
                catch (e: ApiException) {
                    when (e.code) { "WRONG_PIN" -> { error = e.message; errors++ }; "LOCKED", "signed_out" -> {}; else -> onRefused(e.message) }
                } finally { busy = false }
            }
        })
}

@Composable
private fun Receipt(q: Inquiry, paid: BillPaid, onDone: () -> Unit, onShare: (suspend () -> Unit)?) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    SuccessScreen(
        headline = "Paid", amount = Money.format(paid.amount + paid.fee), subline = paid.label.replace(Regex("\\d{4}-\\d{2}")) { billPeriod(it.value) },
        date = paid.at,
        details = {
            if (paid.token != null) {
                Text("Your token", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4D5872))
                Text(paid.token.chunked(4).joinToString("-"), style = MaterialTheme.typography.headlineMedium, color = Brand.Nero)
            }
            q.name?.let { ReceiptLine("Name", it) }
            ReceiptLine(numberLabel(q.kind), q.number)
            if (paid.fee > 0) ReceiptLine("Fee", Money.format(paid.fee))
            ReceiptLine("Reference", paid.reference)
        },
        onPrimary = onDone, onShare = onShare,
        textAction = paid.token?.let { token -> { WhiteTextButton(if (copied) "Token copied" else "Copy token") { clipboard.setText(AnnotatedString(token)); copied = true } } },
    )
}
