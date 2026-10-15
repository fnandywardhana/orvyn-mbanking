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
import id.orvyn.app.ui.components.AmountKeypad
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

@Composable
fun FxScreen(onBack: () -> Unit, onExchange: (currency: String, buy: Boolean) -> Unit) {
    var data by remember { mutableStateOf<Pair<List<FxRate>, List<FxAccount>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(reload, DataVersion.count) { try { data = Api.fx(); error = null } catch (e: ApiException) { error = e.message } }
    FlowScreen("Foreign currency", "Hold dollars, yen or euros for trips and school fees. No fees; you pay the rate shown.", onBack) {
        val d = data
        when {
            d == null && error != null -> MessageState(R.drawable.ph_warning_circle, "Couldn't load rates", error!!)
            d == null -> Skeleton(Modifier.fillMaxWidth().height(200.dp))
            else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                if (d.second.isNotEmpty()) {
                    SectionLabel("Your accounts")
                    for (a in d.second) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(formatForeign(a.balance, a.currency, a.decimals), style = MaterialTheme.typography.titleMedium)
                                Text("${a.name} · about ${Money.format(a.idrValue)}", style = MaterialTheme.typography.bodySmall, color = OrvynTheme.colors.textSecondary)
                            }
                            if (a.balance > 0) OrvynTextButton("Sell", onClick = { onExchange(a.currency, false) })
                            else OrvynTextButton("Close", onClick = { scope.launch { runCatching { Api.fxClose(a.currency) }; reload++ } })
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Today's rates", Modifier.weight(1f))
                    if (d.first.firstOrNull()?.special == true) Text("Prestige rates", style = MaterialTheme.typography.labelMedium, color = Brand.GialloInk,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Brand.Giallo.copy(alpha = 0.25f)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Row(Modifier.padding(bottom = 4.dp)) {
                    Text("Currency", style = MaterialTheme.typography.labelMedium, color = OrvynTheme.colors.textSecondary, modifier = Modifier.weight(1.2f))
                    Text("We buy", style = MaterialTheme.typography.labelMedium, color = OrvynTheme.colors.textSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text("We sell", style = MaterialTheme.typography.labelMedium, color = OrvynTheme.colors.textSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
                for (r in d.first) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1.2f)) {
                            Text(r.currency, style = MaterialTheme.typography.titleMedium)
                            Text(r.name, style = MaterialTheme.typography.bodySmall, color = OrvynTheme.colors.textSecondary)
                        }
                        Text(formatRate(r.buy), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        Text(formatRate(r.sell), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Text("Rupiah per one unit (JPY: per one yen).", style = MaterialTheme.typography.bodySmall, color = OrvynTheme.colors.textTertiary, modifier = Modifier.padding(top = 8.dp))
                OrvynButton("Buy foreign currency", onClick = { onExchange(d.first.first().currency, true) }, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp))
            }
        }
    }
}

/** Amount in whole units of the currency → quote → PIN → done. */
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
    var step by remember { mutableStateOf(0) } // 0 amount, 1 PIN, 2 done
    var reference by remember { mutableStateOf("") }
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
    when (step) {
        0 -> FlowScreen(if (buy) "Buy $currency" else "Sell $currency", if (buy) "From ORVYN Everyday into your $currency account." else "From your $currency account into ORVYN Everyday.", onExit) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Buy", buy) { buy = true }
                    Chip("Sell", !buy) { buy = false }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in rates) Chip(r.currency, r.currency == currency) { currency = r.currency; units = 0 }
                }
                Text(formatForeign(minor, currency, decimals), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                val q = quote
                Text(when {
                    error != null -> error!!
                    q != null -> "${if (buy) "You pay" else "You get"} ${Money.format(q.first)} at ${formatRate(q.second)}"
                    !buy -> "You have ${formatForeign(heldMinor, currency, decimals)}"
                    else -> rate?.let { "1 $currency = ${formatRate(it.sell)}" } ?: ""
                }, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    color = if (error != null) MaterialTheme.colorScheme.error else OrvynTheme.colors.textSecondary)
                Spacer(Modifier.weight(1f))
                AmountKeypad(units, { units = it.coerceAtMost(9_999_999) })
            }
            OrvynButton("Review", enabled = quote != null && (buy || minor <= heldMinor), onClick = { step = 1 },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
        }
        1 -> PinConfirm("To ${if (buy) "buy" else "sell"} ${formatForeign(minor, currency, decimals)} for ${Money.format(quote!!.first)}.", onBack = { step = 0 },
            onRefused = { error = it; step = 0 }) { pin -> reference = Api.fxExchange(currency, buy, minor, pin, key); DataVersion.count++; step = 2 }
        else -> SuccessScreen(if (buy) "Bought" else "Sold", formatForeign(minor, currency, decimals), "for ${Money.format(quote!!.first)}",
            details = { ReceiptLine("Rate", formatRate(quote!!.second)); ReceiptLine("Reference", reference) }, primaryLabel = "Done", onPrimary = onExit)
    }
}
