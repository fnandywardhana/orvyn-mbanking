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
import id.orvyn.app.ui.components.AmountDisplay
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

@Composable
fun DepositsScreen(onBack: () -> Unit, onOpenNew: () -> Unit) {
    var data by remember { mutableStateOf<Pair<List<Pair<Int, Int>>, List<Deposit>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var managing by remember { mutableStateOf<Deposit?>(null) }
    var withdrawing by remember { mutableStateOf<Deposit?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload, DataVersion.count) { try { data = Api.deposits(); error = null } catch (e: ApiException) { error = e.message } }
    withdrawing?.let { d ->
        return PinConfirm("To withdraw ${Money.format(d.principal)} early. The interest is lost.", onBack = { withdrawing = null }, onRefused = { error = it; withdrawing = null }) { pin ->
            Api.withdrawDeposit(d.id, pin); DataVersion.count++; withdrawing = null; reload++
        }
    }
    FlowScreen("Time deposits", "A fixed rate for a fixed time. Interest is paid at maturity, after 20% tax.", onBack) {
        val x = data
        when {
            x == null && error != null -> MessageState(R.drawable.ph_warning_circle, "Couldn't load your deposits", error!!)
            x == null -> Skeleton(Modifier.fillMaxWidth().height(160.dp))
            else -> LazyColumn(Modifier.weight(1f)) {
                item {
                    SectionLabel("Rates today")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((t, bp) in x.first) Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(percent(bp), style = MaterialTheme.typography.titleMedium)
                            Text("$t ${if (t == 1) "month" else "months"}", style = MaterialTheme.typography.bodySmall, color = OrvynTheme.colors.textSecondary)
                        }
                    }
                    SectionLabel("Your deposits")
                    if (x.second.isEmpty()) Text("No deposits yet.", style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary)
                }
                items(x.second, key = { it.id }) { d ->
                    Row(Modifier.fillMaxWidth().clickable { managing = d }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Money.format(d.principal), style = MaterialTheme.typography.titleMedium)
                            Text("${d.tenor} ${if (d.tenor == 1) "month" else "months"} · ${percent(d.rateBp)} · matures ${formatDay(d.maturityDate)}",
                                style = MaterialTheme.typography.bodySmall, color = OrvynTheme.colors.textSecondary)
                            Text(if (d.instruction == "rollover") "Renews automatically" else "Pays out to ORVYN Everyday", style = MaterialTheme.typography.bodySmall, color = OrvynTheme.colors.textSecondary)
                        }
                        Text("+${Money.format(d.expectedInterest)}", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.success)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        OrvynButton("Open a deposit", onClick = onOpenNew, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
    }
    managing?.let { d ->
        OrvynBottomSheet(onDismiss = { managing = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${d.tenor}-month deposit", style = MaterialTheme.typography.titleLarge)
                ReviewLine("Principal", Money.format(d.principal))
                ReviewLine("Rate", "${percent(d.rateBp)} a year")
                ReviewLine("Matures", formatDay(d.maturityDate))
                ReviewLine("Interest after tax", Money.format(d.expectedInterest))
                Text("At maturity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Renew", d.instruction == "rollover") { scope.launch { runCatching { Api.setDepositInstruction(d.id, true) }; managing = null; reload++ } }
                    Chip("Pay out", d.instruction == "payout") { scope.launch { runCatching { Api.setDepositInstruction(d.id, false) }; managing = null; reload++ } }
                }
                OrvynButton("Withdraw early", kind = ButtonKind.Destructive, onClick = { withdrawing = d; managing = null }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Text("Withdrawing early returns the principal only; the interest is lost.", style = MaterialTheme.typography.bodySmall, color = OrvynTheme.colors.textSecondary)
            }
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
    var step by remember { mutableIntStateOf(0) } // 0 amount, 1 term, 2 review, 3 PIN, 4 done
    var reference by remember { mutableStateOf("") }
    val key = remember { UUID.randomUUID().toString() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { Api.deposits() }.onSuccess { rates = it.first } }
    BackHandler(step in 1..3) { step -= 1 }
    when (step) {
        0 -> FlowScreen("Open a deposit", "How much? From ORVYN Everyday, at least Rp1,000,000.", onExit) {
            Column(Modifier.weight(1f)) {
                AmountDisplay(amount, null, error, onQuickPick = { amount = it }, quickAmounts = DEPOSIT_QUICK_AMOUNTS)
                Spacer(Modifier.weight(1f))
                AmountKeypad(amount, { amount = it; error = null })
            }
            OrvynButton("Next", enabled = amount >= 1_000_000, onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
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
                        Text(note, style = MaterialTheme.typography.bodySmall, color = OrvynTheme.colors.textSecondary)
                    }
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
            OrvynButton("Review", modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), onClick = {
                scope.launch { try { quote = Api.depositQuote(amount, tenor); step = 2 } catch (e: ApiException) { error = e.message } }
            })
        }
        2 -> FlowScreen("Check the details", null, { step = 1 }) {
            val q = quote!!
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReviewLine("Deposit", Money.format(amount), strong = true)
                ReviewLine("Term", "$tenor ${if (tenor == 1) "month" else "months"}")
                ReviewLine("Rate", "${percent(q.rateBp)} a year")
                ReviewLine("Matures", formatDay(q.maturityDate))
                ReviewLine("Interest", Money.format(q.gross))
                ReviewLine("Tax (20%)", "−" + Money.format(q.tax))
                ReviewLine("You get", Money.format(q.net), strong = true)
                ReviewLine("At maturity", if (rollover) "Renews automatically" else "Pays out to ORVYN Everyday")
            }
            OrvynButton("Open deposit", onClick = { step = 3 }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
        }
        3 -> PinConfirm("To open a ${Money.format(amount)} deposit.", onBack = { step = 2 }, onRefused = { error = it; step = 1 }) { pin ->
            reference = Api.openDeposit(amount, tenor, rollover, pin, key).reference; DataVersion.count++; step = 4
        }
        else -> SuccessScreen("Deposit opened", Money.format(amount), "for $tenor ${if (tenor == 1) "month" else "months"} at ${percent(quote!!.rateBp)}",
            details = { ReceiptLine("Matures", formatDay(quote!!.maturityDate)); ReceiptLine("Interest after tax", Money.format(quote!!.net)); ReceiptLine("Reference", reference) },
            primaryLabel = "Done", onPrimary = onExit)
    }
}
