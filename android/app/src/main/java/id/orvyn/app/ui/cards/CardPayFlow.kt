package id.orvyn.app.ui.cards

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.Card
import id.orvyn.app.data.DataVersion
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.components.*
import id.orvyn.app.data.Paid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import id.orvyn.app.ui.components.AmountKeypad
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.PinPad
import id.orvyn.app.ui.components.ReceiptLine
import id.orvyn.app.ui.components.SuccessScreen
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Story S1.25: pay the credit card bill in full, the minimum, or another amount, from savings. This choice screen is the
 * review: a failed payment comes back here with the message above Pay, with the choice kept (Q-B1 AC2).
 */
@Composable
fun CardPayFlow(card: Card, onExit: () -> Unit) {
    val credit = card.credit ?: return onExit()
    var step by remember { mutableIntStateOf(0) } // 0 choose, 1 PIN, 2 done
    var choice by remember { mutableStateOf("full") }
    var other by remember { mutableLongStateOf(0L) }
    var paid by remember { mutableStateOf<Paid?>(null) }
    var refused by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val key = remember { UUID.randomUUID().toString() }
    val scope = rememberCoroutineScope()
    val amount = when (choice) { "full" -> credit.owed; "minimum" -> credit.minimum; else -> other }
    BackHandler(step != 2) { if (step == 0) onExit() else step = 0 }

    FlowSteps(step, index = { it }, success = { it == 2 }) { s ->
        when (s) {
            0 -> {
                val choices: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for ((id, label, value) in listOf(Triple("full", "Full bill", credit.owed), Triple("minimum", "Minimum", credit.minimum), Triple("other", "Another amount", null))) {
                            val sel = id == choice
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                                    .border(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                                    .clickable(role = Role.RadioButton) { choice = id; refused = null }.semantics { selected = sel }.heightIn(min = 56.dp).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                value?.let { Text(Money.format(it), style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary) }
                            }
                        }
                    }
                }
                val button: @Composable () -> Unit = {
                    Column {
                        refused?.let { ErrorBox(it, Modifier.padding(bottom = 12.dp)) }
                        OrvynButton(if (amount >= 10_000) "Pay ${Money.format(amount)}" else "Minimum Rp10,000", enabled = amount in 10_000..credit.owed,
                            onClick = { refused = null; step = 1 })
                    }
                }
                if (choice == "other") AmountScreen("Pay ${card.name} bill", "From your ORVYN Everyday savings.", onExit,
                    keypad = { AmountKeypad(other, { other = it; refused = null }) }, button = button) {
                    choices()
                    AmountDisplay(other, null, if (other > credit.owed) "That's more than you owe (${Money.format(credit.owed)})." else null, onQuickPick = { other = it })
                } else FlowScreen("Pay ${card.name} bill", "From your ORVYN Everyday savings.", onExit) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { choices() }
                    Column(Modifier.padding(vertical = 16.dp)) { button() }
                }
            }
            1 -> PinScreen("Enter your PIN", "To pay ${Money.format(amount)} toward your ${card.name} bill.", { step = 0 }, errorCount = errors, errorText = error,
                busyText = if (busy) "Paying ${Money.format(amount)}…" else null,
                onComplete = { pin ->
                    busy = true; error = null
                    scope.launch {
                        try { paid = Api.payCard(card.id, amount, pin, key); DataVersion.count++; step = 2 }
                        catch (e: ApiException) {
                            when (e.code) { "WRONG_PIN" -> { error = e.message; errors++ }; "LOCKED", "signed_out" -> {}; else -> { refused = e.message; step = 0 } }
                        } finally { busy = false }
                    }
                })
            else -> SuccessScreen("Paid", Money.format(amount), "toward your ${card.name} bill", date = paid?.at,
                details = { ReceiptLine("Paid from", "ORVYN Everyday"); ReceiptLine("Still owed", Money.format(credit.owed - amount)); ReceiptLine("Reference", paid?.reference ?: "") },
                onPrimary = onExit)
        }
    }
}
