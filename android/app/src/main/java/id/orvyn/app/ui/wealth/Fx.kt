package id.orvyn.app.ui.wealth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.DataVersion
import id.orvyn.app.data.FxAccount
import id.orvyn.app.data.FxRate
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.components.*
import id.orvyn.app.data.Paid
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import id.orvyn.app.ui.components.ButtonKind
import id.orvyn.app.ui.components.Chip
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.OrvynTextButton
import id.orvyn.app.ui.components.ReceiptLine
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.components.SuccessScreen
import id.orvyn.app.ui.theme.Brand
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

// Stories S3.1–S3.2: foreign currency accounts and exchanges.

/** PO-13: a rate under Rp1,000 a unit reads per 100 units with 2 decimals, so Rp108.025 is never read as a hundred thousand. */
fun perHundred(rate: Double) = rate < 1000
fun rateValue(rate: Double): String = if (perHundred(rate)) "Rp" + java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols(java.util.Locale.US)).format(rate * 100) else formatRate(rate)
fun rateLine(currency: String, rate: Double) = "${if (perHundred(rate)) "100" else "1"} $currency = ${rateValue(rate)}"

@Composable
fun FxScreen(onBack: () -> Unit, onExchange: (currency: String, buy: Boolean) -> Unit) {
    val data = rememberLoad(DataVersion.count) { Api.fx() }
    var closing by remember { mutableStateOf<FxAccount?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val snacks = LocalSnacks.current
    FlowScreen("Foreign currency", "Hold dollars, yen or euros for trips and school fees. No fees; you pay the rate shown.", onBack) {
        LoadContent(data, "Couldn't load rates", skeleton = { Skeleton(Modifier.fillMaxWidth().height(200.dp)) }) { d ->
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (d.second.isNotEmpty()) {
                    SectionLabel("Your accounts")
                    for (a in d.second) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                AmountText(formatForeign(a.balance, a.currency, a.decimals), MaterialTheme.typography.titleMedium, minSize = MaterialTheme.typography.titleMedium.fontSize)
                                Text("${a.name} · about ${Money.format(a.idrValue)}", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                            }
                            if (a.balance > 0) OrvynTextButton("Sell", onClick = { onExchange(a.currency, false) })
                            else OrvynTextButton("Close", onClick = { closing = a })
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Today's rates", Modifier.weight(1f))
                    if (d.first.firstOrNull()?.special == true) Text("Prestige rates", style = MaterialTheme.typography.labelMedium, color = Brand.GialloInk,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Brand.Giallo.copy(alpha = 0.25f)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                // Q-G8: named from the customer's side.
                Row(Modifier.padding(bottom = 4.dp)) {
                    Text("Currency", style = MaterialTheme.typography.labelMedium, color = OrvynTheme.colors.textSecondary, modifier = Modifier.weight(1.2f))
                    Text("You sell at", style = MaterialTheme.typography.labelMedium, color = OrvynTheme.colors.textSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text("You buy at", style = MaterialTheme.typography.labelMedium, color = OrvynTheme.colors.textSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
                for (r in d.first) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1.2f)) {
                            Text(if (perHundred(r.sell)) "100 ${r.currency}" else r.currency, style = MaterialTheme.typography.titleMedium)
                            Text(r.name, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                        }
                        Text(rateValue(r.buy), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        Text(rateValue(r.sell), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Text("Rupiah for one unit. JPY is per 100 yen.", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textTertiary, modifier = Modifier.padding(top = 8.dp))
                OrvynButton("Buy foreign currency", onClick = { onExchange(d.first.first().currency, true) }, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp))
            }
        }
    }
    closing?.let { a ->
        ConfirmDialog("Close your ${a.currency} account?", "It's empty, so nothing moves. You can open it again when you buy ${a.currency}.", "Close account",
            destructive = true, busy = busy, onDismiss = { closing = null }, onConfirm = {
                busy = true
                scope.launch {
                    try { Api.fxClose(a.currency); data.reload() } catch (e: ApiException) { snacks?.show(e.message, error = true) }
                    busy = false; closing = null
                }
            })
    }
}

/** Amount in whole units of the currency → quote → PIN → done. The button names the action and amount (PO-14). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FxExchangeFlow(startCurrency: String, startBuy: Boolean, onExit: () -> Unit) {
    var rates by remember { mutableStateOf<List<FxRate>>(emptyList()) }
    var held by remember { mutableStateOf<List<FxAccount>>(emptyList()) }
    var currency by remember { mutableStateOf(startCurrency) }
    var buy by remember { mutableStateOf(startBuy) }
    var units by remember { mutableLongStateOf(0L) }
    var quote by remember { mutableStateOf<Pair<Long, Double>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refused by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableIntStateOf(0) } // 0 amount, 1 PIN, 2 done
    var paid by remember { mutableStateOf<Paid?>(null) }
    val key = remember { UUID.randomUUID().toString() }
    LaunchedEffect(Unit) { runCatching { Api.fx() }.onSuccess { rates = it.first; held = it.second } }
    val rate = rates.firstOrNull { it.currency == currency }
    val decimals = rate?.decimals ?: 2
    val minor = units * Math.round(Math.pow(10.0, decimals.toDouble()))
    val heldMinor = held.firstOrNull { it.currency == currency }?.balance ?: 0L
    LaunchedEffect(currency, buy, units) {
        quote = null; error = null
        if (units <= 0) return@LaunchedEffect
        delay(300)
        try { quote = Api.fxQuote(currency, buy, minor) } catch (e: ApiException) { error = e.message }
    }
    BackHandler(step == 1) { step = 0 }
    val shown = formatForeign(minor, currency, decimals)
    FlowSteps(step, index = { it }, success = { it == 2 }) { s ->
        when (s) {
            0 -> AmountScreen(if (buy) "Buy $currency" else "Sell $currency", if (buy) "From ORVYN Everyday into your $currency account." else "From your $currency account into ORVYN Everyday.", onExit,
                keypad = { AmountKeypad(units, { units = it.coerceAtMost(9_999_999); refused = null }) },
                button = {
                    Column {
                        refused?.let { ErrorBox(it, Modifier.padding(bottom = 12.dp)) }
                        val short = !buy && minor > heldMinor
                        OrvynButton(when { units == 0L -> "Enter an amount"; short -> "Not enough $currency"; else -> "${if (buy) "Buy" else "Sell"} $shown" },
                            enabled = quote != null && !short, onClick = { refused = null; step = 1 })
                    }
                }) {
                Segmented(listOf("Buy", "Sell"), if (buy) 0 else 1, { buy = it == 0 })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in rates) Chip(r.currency, r.currency == currency) { currency = r.currency; units = 0 }
                }
                AmountText(shown, MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    color = if (units == 0L) OrvynTheme.colors.textTertiary else MaterialTheme.colorScheme.onSurface)
                val q = quote
                Text(when {
                    error != null -> error!!
                    q != null -> "${if (buy) "You pay" else "You get"} ${Money.format(q.first)} at ${rateLine(currency, q.second)}"
                    !buy -> "You have ${formatForeign(heldMinor, currency, decimals)}"
                    else -> rate?.let { rateLine(currency, it.sell) } ?: ""
                }, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    color = if (error != null) MaterialTheme.colorScheme.error else OrvynTheme.colors.textSecondary)
            }
            1 -> PinConfirm("To ${if (buy) "buy" else "sell"} $shown for ${Money.format(quote?.first ?: 0)}.", "${if (buy) "Buying" else "Selling"} $shown…", onBack = { step = 0 },
                onRefused = { refused = it; step = 0 }) { pin -> paid = Api.fxExchange(currency, buy, minor, pin, key); DataVersion.count++; step = 2 }
            else -> SuccessScreen(if (buy) "Bought" else "Sold", shown, "for ${Money.format(quote?.first ?: 0)}", date = paid?.at,
                details = { ReceiptLine("Rate", rateLine(currency, quote?.second ?: 0.0)); ReceiptLine("Reference", paid?.reference ?: "") }, onPrimary = onExit)
        }
    }
}
