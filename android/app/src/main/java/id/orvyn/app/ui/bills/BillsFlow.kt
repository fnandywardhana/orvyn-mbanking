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
    data object Hub : BStep
    data class Number(val biller: Biller) : BStep
    data class Details(val q: Inquiry) : BStep
    data class Pin(val q: Inquiry, val product: Product?, val amount: Long) : BStep
    data class Done(val q: Inquiry, val paid: BillPaid) : BStep
}

private fun iconFor(kind: String) = when (kind) {
    "pln_token", "pln_postpaid" -> R.drawable.ph_lightning
    "bpjs" -> R.drawable.ph_receipt
    "mobile" -> R.drawable.ph_receipt
    else -> R.drawable.ph_plus
}

private fun numberLabel(kind: String) = when (kind) {
    "pln_token" -> "Meter number"
    "pln_postpaid" -> "PLN customer ID"
    "bpjs" -> "BPJS number"
    else -> "Mobile number"
}

/** Stories S1.18–S1.21. [ewalletsOnly] is the "Top up" shortcut. */
@Composable
fun BillsFlow(onExit: () -> Unit, ewalletsOnly: Boolean = false) {
    var step by remember { mutableStateOf<BStep>(BStep.Hub) }
    var billers by remember { mutableStateOf<List<Biller>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val key = remember(step is BStep.Pin) { UUID.randomUUID().toString() }
    LaunchedEffect(Unit) { try { billers = Api.billers() } catch (e: ApiException) { error = e.message } }

    BackHandler(step !is BStep.Done) {
        step = when (val s = step) {
            BStep.Hub -> { onExit(); BStep.Hub }
            is BStep.Number -> BStep.Hub
            is BStep.Details -> billers?.find { it.code == s.q.biller }?.let { BStep.Number(it) } ?: BStep.Hub
            is BStep.Pin -> BStep.Details(s.q)
            is BStep.Done -> s
        }
    }

    when (val s = step) {
        BStep.Hub -> FlowScreen(if (ewalletsOnly) "Top up an e-wallet" else "Pay and buy", null, onExit) {
            val list = billers?.filter { !ewalletsOnly || it.kind == "ewallet" }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    error != null -> MessageState(R.drawable.ph_warning_circle, "Couldn't load this list", error!!)
                    list == null -> repeat(5) { Skeleton(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(48.dp)) }
                    else -> for ((title, kinds) in listOf("Electricity" to setOf("pln_token", "pln_postpaid"), "Health" to setOf("bpjs"), "Phone" to setOf("mobile"), "E-wallets" to setOf("ewallet"))) {
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
                Spacer(Modifier.height(24.dp))
            }
        }
        is BStep.Number -> EnterNumber(s.biller, onBack = { step = BStep.Hub }) { step = BStep.Details(it) }
        is BStep.Details -> Details(s.q, onBack = { billers?.find { it.code == s.q.biller }?.let { b -> step = BStep.Number(b) } ?: run { step = BStep.Hub } }) { p, a -> step = BStep.Pin(s.q, p, a) }
        is BStep.Pin -> BillPin(s.q, s.product, s.amount, key, onBack = { step = BStep.Details(s.q) }) { DataVersion.count++; step = BStep.Done(s.q, it) }
        is BStep.Done -> Receipt(s.q, s.paid, onExit)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnterNumber(b: Biller, onBack: () -> Unit, onFound: (Inquiry) -> Unit) {
    var number by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun check(n: String) {
        busy = true
        scope.launch { try { onFound(Api.inquire(b.code, n)) } catch (e: ApiException) { error = e.message } finally { busy = false } }
    }
    FlowScreen(b.name, null, onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OrvynTextField(numberLabel(b.kind), number, { number = it.filter(Char::isDigit).take(16); error = null }, keyboardType = KeyboardType.Number, error = error)
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
        OrvynButton("Continue", enabled = number.length >= 10, loading = busy, onClick = { check(number) }, modifier = Modifier.padding(vertical = 16.dp))
    }
}

@Composable
private fun Details(q: Inquiry, onBack: () -> Unit, onPay: (Product?, Long) -> Unit) {
    var product by remember { mutableStateOf<Product?>(null) }
    var amount by remember { mutableLongStateOf(0L) }
    val price = q.billAmount ?: product?.price ?: amount
    FlowScreen(q.billerName, null, onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                q.name?.let { ReceiptLine("Name", it, onSurfaceWhite = false) }
                ReceiptLine(numberLabel(q.kind), q.number, onSurfaceWhite = false)
                q.details?.let { ReceiptLine(if (q.kind == "mobile") "Operator" else "Details", it, onSurfaceWhite = false) }
                if (q.billAmount != null) ReceiptLine("Bill for ${q.billPeriod}", Money.format(q.billAmount), onSurfaceWhite = false)
            }
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
            if (q.minAmount != null) {
                AmountDisplay(amount, null, if (amount in 1 until q.minAmount) "The smallest top-up is ${Money.format(q.minAmount)}." else null, onQuickPick = { amount = it })
                AmountKeypad(amount, { amount = it })
            }
            if (price > 0) Text(
                if (q.fee > 0) "Total ${Money.format(price + q.fee)}, including a ${Money.format(q.fee)} fee." else "Total ${Money.format(price)}. No fee.",
                style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary,
            )
        }
        val ok = price > 0 && (q.minAmount == null || amount >= q.minAmount)
        OrvynButton(if (ok) "Pay ${Money.format(price + q.fee)}" else "Choose what to pay", enabled = ok,
            onClick = { onPay(product, price) }, modifier = Modifier.padding(vertical = 16.dp))
    }
}

@Composable
private fun BillPin(q: Inquiry, product: Product?, amount: Long, key: String, onBack: () -> Unit, onPaid: (BillPaid) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    FlowScreen("Enter your PIN", "To pay ${Money.format(amount + q.fee)} for ${q.billerName}.", onBack) {
        Spacer(Modifier.weight(1f))
        PinPad(
            onComplete = { pin ->
                scope.launch {
                    try { onPaid(Api.payBill(q.biller, q.number, product?.id, if (q.minAmount != null) amount else null, pin, key)) }
                    catch (e: ApiException) { error = e.message; errors++ }
                }
            },
            errorCount = errors, errorText = error, onBiometric = null, modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun Receipt(q: Inquiry, paid: BillPaid, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    SuccessScreen(
        headline = "Paid", amount = Money.format(paid.amount + paid.fee), subline = paid.label,
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
        primaryLabel = "Done", onPrimary = onDone,
        secondary = paid.token?.let { token -> { WhiteTextButton(if (copied) "Token copied" else "Copy token") { clipboard.setText(AnnotatedString(token)); copied = true } } },
    )
}
