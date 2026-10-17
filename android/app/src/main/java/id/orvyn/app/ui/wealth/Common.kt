package id.orvyn.app.ui.wealth

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.orvyn.app.data.ApiException
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.components.PinScreen
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Shared bits for the Phase 3 money screens (FX, deposits, investments).

/** "USD 1,250.00", "JPY 10,000": an amount held in the currency's smallest unit. */
fun formatForeign(minor: Long, currency: String, decimals: Int): String {
    val f = DecimalFormat(if (decimals == 0) "#,##0" else "#,##0." + "0".repeat(decimals), DecimalFormatSymbols(Locale.US))
    return "$currency ${f.format(minor / Math.pow(10.0, decimals.toDouble()))}"
}

/** Rates and NAVs keep up to four decimals, without trailing zeros. */
fun formatRate(r: Double): String = "Rp" + DecimalFormat("#,##0.####", DecimalFormatSymbols(Locale.US)).format(r)

fun formatDay(iso: String): String = LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US))

fun percent(bp: Int) = DecimalFormat("0.00", DecimalFormatSymbols(Locale.US)).format(bp / 100.0) + "%"

@Composable
fun ReviewLine(label: String, value: String, strong: Boolean = false) = Row(verticalAlignment = Alignment.Top) {
    Text(label, style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary, modifier = Modifier.weight(1f))
    Text(value, style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End, modifier = Modifier.weight(1.3f))
}

/**
 * The PIN step every money action ends with. [action] gets the PIN; a wrong PIN stays here with the count, anything else
 * goes back with its message. [busy] is the status while the server works ("Buying Rp100,000…", Q-B5).
 */
@Composable
fun PinConfirm(body: String, busy: String, onBack: () -> Unit, onRefused: (String) -> Unit, action: suspend (String) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    PinScreen("Enter your PIN", body, onBack, errorCount = errors, errorText = error, busyText = if (working) busy else null, onComplete = { pin ->
        if (working) return@PinScreen
        working = true; error = null
        scope.launch {
            try { action(pin) } catch (e: ApiException) {
                when (e.code) { "WRONG_PIN" -> { error = e.message; errors++ }; "LOCKED", "signed_out" -> {}; else -> onRefused(e.message) }
            } finally { working = false }
        }
    })
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) = Text(text.uppercase(), style = MaterialTheme.typography.labelSmall,
    color = OrvynTheme.colors.textSecondary, modifier = modifier.padding(top = 20.dp, bottom = 8.dp))
