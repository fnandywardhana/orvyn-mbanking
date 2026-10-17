package id.orvyn.app.ui.wealth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.DataVersion
import id.orvyn.app.data.Deposit
import id.orvyn.app.data.DepositQuote
import id.orvyn.app.ui.DEPOSIT_QUICK_AMOUNTS
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.components.*
import id.orvyn.app.data.Paid
import androidx.compose.ui.text.style.TextAlign
import id.orvyn.app.ui.components.AmountKeypad
import id.orvyn.app.ui.components.ButtonKind
import id.orvyn.app.ui.components.Chip
import id.orvyn.app.ui.components.ConfirmDialog
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OrvynBottomSheet
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.ReceiptLine
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.components.SuccessScreen
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.launch
import java.util.UUID

// Stories S3.3–S3.4: time deposits.

/** Q-B2 (design.md 6.2): withdrawing early is review → PIN → done, never straight to the PIN. */
private sealed interface Withdraw {
    val d: Deposit; val index: Int
    data class Review(override val d: Deposit, val error: String? = null) : Withdraw { override val index = 0 }
    data class Pin(override val d: Deposit) : Withdraw { override val index = 1 }
    data class Done(override val d: Deposit, val amount: Long, val paid: Paid) : Withdraw { override val index = 2 }
}

@Composable
fun DepositsScreen(onBack: () -> Unit, onOpenNew: () -> Unit) {
    val data = rememberLoad(DataVersion.count) { Api.deposits() }
    var managing by remember { mutableStateOf<Deposit?>(null) }
    var withdraw by remember { mutableStateOf<Withdraw?>(null) }
    withdraw?.let { w ->
        BackHandler { withdraw = when (w) { is Withdraw.Pin -> Withdraw.Review(w.d); is Withdraw.Done -> { data.reload(); null }; else -> null } }
        FlowSteps(w, index = { it.index }, success = { it is Withdraw.Done }) { s ->
            when (s) {
                is Withdraw.Review -> FlowScreen("Withdraw early?", "You get the principal back. The interest is lost.", { withdraw = null }) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ReviewLine("Deposit", "${s.d.tenor}-month deposit")
                            ReviewLine("Matures", formatDay(s.d.maturityDate))
                            ReviewLine("Back to ORVYN Everyday", Money.format(s.d.principal), strong = true)
                            ReviewLine("Interest you lose", Money.format(s.d.expectedInterest))
                        }
                    }
                    s.error?.let { ErrorBox(it, Modifier.padding(top = 12.dp)) }
                    OrvynButton("Withdraw ${Money.format(s.d.principal)}", onClick = { withdraw = Withdraw.Pin(s.d) }, modifier = Modifier.padding(vertical = 16.dp))
                }
                is Withdraw.Pin -> PinConfirm("To withdraw ${Money.format(s.d.principal)} early. The interest is lost.", "Withdrawing ${Money.format(s.d.principal)}…",
                    onBack = { withdraw = Withdraw.Review(s.d) }, onRefused = { withdraw = Withdraw.Review(s.d, it) }) { pin ->
                    val (amount, paid) = Api.withdrawDeposit(s.d.id, pin)
                    DataVersion.count++
                    withdraw = Withdraw.Done(s.d, amount, paid)
                }
                is Withdraw.Done -> SuccessScreen("Withdrawn", Money.format(s.amount), "back in ORVYN Everyday", date = s.paid.at, details = {
                    ReceiptLine("From", "${s.d.tenor}-month deposit")
                    ReceiptLine("Interest", "Not paid, because you withdrew before ${formatDay(s.d.maturityDate)}")
                    ReceiptLine("Reference", s.paid.reference)
                }, onPrimary = { withdraw = null; data.reload() })
            }
        }
        return
    }
    FlowScreen("Time deposits", "A fixed rate for a fixed time. Interest is paid at maturity, after 20% tax.", onBack) {
        Column(Modifier.weight(1f)) {
            LoadContent(data, "Couldn't load your deposits", skeleton = { Skeleton(Modifier.fillMaxWidth().height(160.dp)) }) { x ->
                LazyColumn {
                    item {
                        SectionLabel("Rates today")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((t, bp) in x.first) Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(percent(bp), style = MaterialTheme.typography.titleMedium)
                                Text("$t ${if (t == 1) "month" else "months"}", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary, textAlign = TextAlign.Center)
                            }
                        }
                        SectionLabel("Your deposits")
                        if (x.second.isEmpty()) Text("No deposits yet.", style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary)
                    }
                    items(x.second, key = { it.id }) { d ->
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { managing = d }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Money.format(d.principal), style = MaterialTheme.typography.titleMedium)
                                Text("${d.tenor} ${if (d.tenor == 1) "month" else "months"} · ${percent(d.rateBp)} · matures ${formatDay(d.maturityDate)}",
                                    style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                                Text(if (d.instruction == "rollover") "Renews automatically" else "Pays out to ORVYN Everyday", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                            }
                            Text("+${Money.format(d.expectedInterest)}", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.success)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        OrvynButton("Open a deposit", onClick = onOpenNew, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
    }
    managing?.let { d -> ManageSheet(d, onDismiss = { managing = null }, onChanged = { data.reload() }, onWithdraw = { managing = null; withdraw = Withdraw.Review(d) }) }
}

/** The deposit's details and its maturity choice. A choice the server refuses snaps back with the message in the sheet (Q-B1). */
@Composable
private fun ManageSheet(d: Deposit, onDismiss: () -> Unit, onChanged: () -> Unit, onWithdraw: () -> Unit) {
    var rollover by remember { mutableStateOf(d.instruction == "rollover") }
    var busy by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    OrvynBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${d.tenor}-month deposit", style = MaterialTheme.typography.titleLarge)
            ReviewLine("Principal", Money.format(d.principal))
            ReviewLine("Rate", "${percent(d.rateBp)} a year")
            ReviewLine("Matures", formatDay(d.maturityDate))
            ReviewLine("Interest after tax", Money.format(d.expectedInterest))
            Text("At maturity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Segmented(listOf("Renew", "Pay out"), if (rollover) 0 else 1, busy = busy, onSelect = { i ->
                if ((i == 0) == rollover) return@Segmented
                busy = i; error = null
                scope.launch {
                    try { Api.setDepositInstruction(d.id, i == 0); rollover = i == 0; onChanged() } catch (e: ApiException) { error = e.message }
                    busy = null
                }
            })
            error?.let { ErrorBox(it) }
            Spacer(Modifier.height(14.dp))
            OrvynButton("Withdraw early", kind = ButtonKind.DestructiveText, onClick = onWithdraw, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Withdrawing early returns the principal only; the interest is lost.", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpenDepositFlow(onExit: () -> Unit) {
    var rates by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var amount by remember { mutableLongStateOf(0L) }
    var tenor by remember { mutableIntStateOf(3) }
    var rollover by remember { mutableStateOf(true) }
    var quote by remember { mutableStateOf<DepositQuote?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refused by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) } // 0 amount, 1 term, 2 review, 3 PIN, 4 done
    var paid by remember { mutableStateOf<Paid?>(null) }
    val key = remember { UUID.randomUUID().toString() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { Api.deposits() }.onSuccess { rates = it.first } }
    BackHandler(step in 1..3) { step -= 1 }
    FlowSteps(step, index = { it }, success = { it == 4 }) { s ->
        when (s) {
            0 -> AmountScreen("Open a deposit", "How much? From ORVYN Everyday, at least Rp1,000,000.", onExit,
                keypad = { AmountKeypad(amount, { amount = it; error = null }) },
                button = { OrvynButton("Next", enabled = amount >= 1_000_000, onClick = { step = 1 }) }) {
                AmountDisplay(amount, null, error, onQuickPick = { amount = it }, quickAmounts = DEPOSIT_QUICK_AMOUNTS)
            }
            1 -> FlowScreen("For how long?", "${Money.format(amount)} at a fixed rate.", { step = 0 }) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((t, bp) in rates) Chip("$t mo · ${percent(bp)}", tenor == t) { tenor = t }
                    }
                    Text("At maturity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    for ((value, label, note) in listOf(Triple(true, "Renew automatically", "Principal and interest start a new term"), Triple(false, "Pay out", "Everything goes to ORVYN Everyday"))) {
                        val sel = rollover == value
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                            .border(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                            .clickable(role = Role.RadioButton) { rollover = value }.semantics { selected = sel }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(label, style = MaterialTheme.typography.titleMedium)
                            Text(note, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                        }
                    }
                    error?.let { ErrorBox(it) }
                }
                OrvynButton("Review", loading = busy, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), onClick = {
                    busy = true
                    scope.launch { try { quote = Api.depositQuote(amount, tenor); error = null; refused = null; step = 2 } catch (e: ApiException) { error = e.message }; busy = false }
                })
            }
            2 -> FlowScreen("Check the details", null, { step = 1 }) {
                val q = quote ?: return@FlowScreen
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ReviewLine("Deposit", Money.format(amount), strong = true)
                        ReviewLine("Term", "$tenor ${if (tenor == 1) "month" else "months"}")
                        ReviewLine("Rate", "${percent(q.rateBp)} a year")
                        ReviewLine("Matures", formatDay(q.maturityDate))
                        ReviewLine("Interest", Money.format(q.gross))
                        ReviewLine("Tax (20%)", "−" + Money.format(q.tax))
                        ReviewLine("You get", Money.format(q.net), strong = true)
                        ReviewLine("At maturity", if (rollover) "Renews automatically" else "Pays out to ORVYN Everyday")
                    }
                }
                refused?.let { ErrorBox(it, Modifier.padding(top = 12.dp)) }
                OrvynButton("Open deposit", onClick = { step = 3 }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
            }
            3 -> PinConfirm("To open a ${Money.format(amount)} deposit.", "Opening your deposit…", onBack = { step = 2 }, onRefused = { refused = it; step = 2 }) { pin ->
                paid = Api.openDeposit(amount, tenor, rollover, pin, key); DataVersion.count++; step = 4
            }
            else -> SuccessScreen("Deposit opened", Money.format(amount), "for $tenor ${if (tenor == 1) "month" else "months"} at ${percent(quote?.rateBp ?: 0)}", date = paid?.at,
                details = { ReceiptLine("Matures", formatDay(quote?.maturityDate ?: "")); ReceiptLine("Interest after tax", Money.format(quote?.net ?: 0)); ReceiptLine("Reference", paid?.reference ?: "") },
                onPrimary = onExit)
        }
    }
}
