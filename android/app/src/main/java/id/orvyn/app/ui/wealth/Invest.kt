package id.orvyn.app.ui.wealth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.DataVersion
import id.orvyn.app.data.Holding
import id.orvyn.app.data.Invest
import id.orvyn.app.data.InvestProduct
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.investQuickAmounts
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.components.AmountDisplay
import id.orvyn.app.ui.components.AmountKeypad
import id.orvyn.app.ui.components.ButtonKind
import id.orvyn.app.ui.components.Chip
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.ReceiptLine
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.components.SuccessScreen
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

// Stories S3.5–S3.8: risk profile, funds, bonds and gold.

private val RISK = listOf("", "Low risk", "Low to medium risk", "Medium risk", "High risk")
private val SUBTYPE = mapOf("money_market" to "Money market", "fixed_income" to "Fixed income", "balanced" to "Balanced", "equity" to "Shares")
private fun profileText(p: String) = mapOf(
    "conservative" to "Conservative: you'd rather keep what you have than chase growth. Money market and fixed income funds, bonds and gold suit you.",
    "moderate" to "Moderate: you're fine with some ups and downs for better growth. Balanced funds suit you too.",
    "aggressive" to "Aggressive: you're investing for years and can ride out big swings. Every product suits you.",
)[p] ?: ""

@Composable
private fun Gain(h: Holding) {
    val pct = if (h.cost > 0) h.gain * 10_000 / h.cost / 100.0 else 0.0
    Text("${if (h.gain >= 0) "+" else "−"}${Money.format(h.gain)} (${String.format(Locale.US, "%.2f", kotlin.math.abs(pct))}%)", style = MaterialTheme.typography.bodySmall,
        color = if (h.gain >= 0) OrvynTheme.colors.success else MaterialTheme.colorScheme.error)
}

@Composable
fun InvestScreen(onBack: () -> Unit, onQuiz: () -> Unit, onProduct: (String) -> Unit) {
    var data by remember { mutableStateOf<Invest?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(DataVersion.count) { try { data = Api.invest(); error = null } catch (e: ApiException) { error = e.message } }
    FlowScreen("Investments", "Made-up funds, bonds and gold with simulated prices. Values can go down as well as up.", onBack) {
        val d = data
        when {
            d == null && error != null -> MessageState(R.drawable.ph_warning_circle, "Couldn't load investments", error!!)
            d == null -> Skeleton(Modifier.fillMaxWidth().height(200.dp))
            else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick = onQuiz).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (d.riskProfile == null) "Find your risk profile first" else "Your profile: ${d.riskProfile.replaceFirstChar(Char::uppercase)}",
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(if (d.riskProfile == null) "Five quick questions before your first investment." else "Tap to take the quiz again.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (d.holdings.isNotEmpty()) {
                    SectionLabel("Your investments · ${Money.format(d.holdings.sumOf { it.value })}")
                    for (h in d.holdings) {
                        Row(Modifier.fillMaxWidth().clickable { onProduct(h.productId) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(h.name, style = MaterialTheme.typography.titleMedium)
                                Text(when (h.kind) { "gold" -> String.format(Locale.US, "%.4f g", h.quantity); "bond" -> "Face value"; else -> String.format(Locale.US, "%,.4f units", h.quantity) },
                                    style = MaterialTheme.typography.bodySmall, color = OrvynTheme.colors.textSecondary)
                            }
                            Column(horizontalAlignment = Alignment.End) { Text(Money.format(h.value), style = MaterialTheme.typography.titleMedium); if (h.kind != "bond") Gain(h) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                for ((kind, title) in listOf("fund" to "Mutual funds", "bond" to "Retail government bonds", "gold" to "Gold")) {
                    SectionLabel(title)
                    for (p in d.products.filter { it.kind == kind }) {
                        Row(Modifier.fillMaxWidth().clickable { onProduct(p.id) }.heightIn(min = 64.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text(listOfNotNull(p.subtype?.let { SUBTYPE[it] }, RISK[p.riskLevel], if (p.suitable == false) "Above your profile" else null).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = if (p.suitable == false) OrvynTheme.colors.warning else OrvynTheme.colors.textSecondary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                when (p.kind) {
                                    "fund" -> {
                                        Text(formatRate(p.nav ?: 0.0), style = MaterialTheme.typography.bodyLarge)
                                        p.oneYear?.let { Text("${if (it >= 0) "+" else ""}${String.format(Locale.US, "%.2f", it)}% in 1 year", style = MaterialTheme.typography.bodySmall,
                                            color = if (it >= 0) OrvynTheme.colors.success else MaterialTheme.colorScheme.error) }
                                    }
                                    "bond" -> { Text("${percent(p.couponBp ?: 0)} a year", style = MaterialTheme.typography.bodyLarge); Text("Until ${formatDay(p.maturity!!)}", style = MaterialTheme.typography.bodySmall, color = OrvynTheme.colors.textSecondary) }
                                    else -> { Text("${Money.format(p.goldSell ?: 0)}/g", style = MaterialTheme.typography.bodyLarge); Text("Buy back ${Money.format(p.goldBuy ?: 0)}/g", style = MaterialTheme.typography.bodySmall, color = OrvynTheme.colors.textSecondary) }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun RiskQuizFlow(onExit: () -> Unit) {
    var quiz by remember { mutableStateOf<List<Pair<String, List<String>>>>(emptyList()) }
    val answers = remember { mutableStateListOf<Int>() }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { Api.invest() }.onSuccess { quiz = it.quiz }.onFailure { error = it.message } }
    BackHandler(answers.isNotEmpty() && result == null) { answers.removeAt(answers.lastIndex) }
    val r = result
    if (r != null) return FlowScreen("You're ${r.replaceFirstChar(Char::uppercase)}", profileText(r), onExit) {
        Spacer(Modifier.weight(1f)); OrvynButton("See investments", onClick = onExit, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
    }
    val i = answers.size
    if (quiz.isEmpty()) return FlowScreen("Risk profile", null, onExit) { error?.let { MessageState(R.drawable.ph_warning_circle, "Couldn't load the quiz", it) } ?: Skeleton(Modifier.fillMaxWidth().height(200.dp)) }
    val (q, options) = quiz[i.coerceAtMost(quiz.lastIndex)]
    FlowScreen("Question ${i + 1} of ${quiz.size}", q, { if (answers.isEmpty()) onExit() else answers.removeAt(answers.lastIndex) }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEachIndexed { k, a ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)).clickable(role = Role.Button) {
                        answers.add(k)
                        if (answers.size == quiz.size) scope.launch { try { result = Api.setRiskProfile(answers.toList()); DataVersion.count++ } catch (e: ApiException) { error = e.message; answers.removeAt(answers.lastIndex) } }
                    }.heightIn(min = 56.dp).padding(16.dp), contentAlignment = Alignment.CenterStart) { Text(a, style = MaterialTheme.typography.bodyLarge) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** One series, one line: 2dp, a soft fill under it, drag to read any day. */
@Composable
private fun PriceChart(prices: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    var at by remember(prices) { mutableIntStateOf(prices.lastIndex) }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    if (prices.size < 2) return
    val lo = prices.minOf { it.second }
    val hi = prices.maxOf { it.second }
    Column(modifier.semantics { contentDescription = "Price from ${formatRate(prices.first().second)} on ${formatDay(prices.first().first)} to ${formatRate(prices.last().second)} on ${formatDay(prices.last().first)}" }) {
        Text("${formatDay(prices[at].first)} · ${formatRate(prices[at].second)}", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
        Canvas(Modifier.fillMaxWidth().height(160.dp).padding(top = 8.dp)
            .pointerInput(prices) { detectTapGestures { o -> at = (o.x / size.width * prices.lastIndex).toInt().coerceIn(0, prices.lastIndex) } }
            .pointerInput(prices) { detectDragGestures { c, _ -> at = (c.position.x / size.width * prices.lastIndex).toInt().coerceIn(0, prices.lastIndex) } }) {
            fun x(i: Int) = size.width * i / prices.lastIndex
            fun y(v: Double) = (size.height * (1 - (v - lo) / (hi - lo).coerceAtLeast(1e-9))).toFloat()
            val path = Path().apply { prices.forEachIndexed { i, (_, v) -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
            val fill = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.18f), line.copy(alpha = 0f))))
            drawPath(path, line, style = Stroke(2.dp.toPx()))
            drawLine(grid, Offset(x(at), 0f), Offset(x(at), size.height), 1.dp.toPx())
            drawCircle(line, 4.dp.toPx(), Offset(x(at), y(prices[at].second)))
        }
    }
}

@Composable
fun ProductScreen(id: String, onBack: () -> Unit, onTrade: (buy: Boolean) -> Unit) {
    var data by remember { mutableStateOf<Invest?>(null) }
    var days by remember { mutableIntStateOf(30) }
    var prices by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
    LaunchedEffect(DataVersion.count) { data = runCatching { Api.invest() }.getOrNull() }
    LaunchedEffect(days) { prices = runCatching { Api.prices(id, days) }.getOrDefault(emptyList()) }
    val p = data?.products?.firstOrNull { it.id == id } ?: return FlowScreen("Investment", null, onBack) { Skeleton(Modifier.fillMaxWidth().height(200.dp)) }
    val h = data?.holdings?.firstOrNull { it.productId == id }
    FlowScreen(p.name, listOfNotNull(p.subtype?.let { SUBTYPE[it] }, RISK[p.riskLevel]).joinToString(" · "), onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (p.kind == "fund") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for ((d, l) in listOf(30 to "1M", 180 to "6M", 365 to "1Y")) Chip(l, days == d) { days = d } }
                PriceChart(prices)
            }
            Text(p.description, style = MaterialTheme.typography.bodyLarge)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (p.kind) {
                    "fund" -> { ReviewLine("NAV per unit", formatRate(p.nav ?: 0.0)); p.oneYear?.let { ReviewLine("1 year", String.format(Locale.US, "%+.2f%%", it)) }; p.manager?.let { ReviewLine("Manager", it) } }
                    "bond" -> { ReviewLine("Coupon", "${percent(p.couponBp ?: 0)} a year, monthly"); ReviewLine("Matures", formatDay(p.maturity!!)); ReviewLine("Tax on coupons", "10%") }
                    else -> { ReviewLine("You pay", "${Money.format(p.goldSell ?: 0)} a gram"); ReviewLine("We buy back at", "${Money.format(p.goldBuy ?: 0)} a gram") }
                }
                ReviewLine("Smallest purchase", Money.format(p.minBuy))
            }
            if (h != null) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("You hold", style = MaterialTheme.typography.titleMedium)
                ReviewLine("Value today", Money.format(h.value), strong = true)
                ReviewLine("You paid", Money.format(h.cost))
                if (h.kind != "bond") Row { Text("Gain or loss", style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary, modifier = Modifier.weight(1f)); Gain(h) }
            }
            if (p.suitable == false) Text("This is riskier than your profile. You can still buy it after confirming you understand the risk.",
                style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.warning)
        }
        Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrvynButton("Buy", onClick = { onTrade(true) }, modifier = Modifier.weight(1f))
            if (h != null && p.kind != "bond") OrvynButton("Sell", kind = ButtonKind.Secondary, onClick = { onTrade(false) }, modifier = Modifier.weight(1f))
        }
    }
}

/** Buy or sell in Rupiah; sells are turned into units or grams at today's price. */
@Composable
fun TradeFlow(id: String, buy: Boolean, onExit: () -> Unit, onQuiz: () -> Unit) {
    var data by remember { mutableStateOf<Invest?>(null) }
    var amount by remember { mutableLongStateOf(0L) }
    var acknowledged by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var reference by remember { mutableStateOf("") }
    val key = remember { UUID.randomUUID().toString() }
    LaunchedEffect(Unit) { data = runCatching { Api.invest() }.getOrNull() }
    val d = data ?: return FlowScreen(if (buy) "Buy" else "Sell", null, onExit) { Skeleton(Modifier.fillMaxWidth().height(200.dp)) }
    val p = d.products.first { it.id == id }
    if (buy && d.riskProfile == null) return FlowScreen("One step first", "Take the five-question risk profile quiz before your first investment.", onExit) {
        Spacer(Modifier.weight(1f)); OrvynButton("Take the quiz", onClick = onQuiz, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
    }
    val h = d.holdings.firstOrNull { it.productId == id }
    val unit = when (p.kind) { "gold" -> (if (buy) p.goldSell else p.goldBuy)!!.toDouble(); "fund" -> p.nav!!; else -> 1.0 }
    val quantity = amount / unit
    val sellAll = !buy && h != null && amount >= h.value
    BackHandler(step == 1) { step = 0 }
    when (step) {
        0 -> FlowScreen("${if (buy) "Buy" else "Sell"} ${p.name}", if (buy) "From ORVYN Everyday." else "Into ORVYN Everyday. You have ${Money.format(h?.value ?: 0)} here.", onExit) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountDisplay(amount, null, error, onQuickPick = { amount = if (!buy && h != null) minOf(it, h.value) else it }, quickAmounts = investQuickAmounts(p.kind))
                Text(when (p.kind) {
                    "gold" -> String.format(Locale.US, "About %.4f g at %s a gram", quantity, Money.format(unit.toLong()))
                    "fund" -> String.format(Locale.US, "About %,.4f units at %s", quantity, formatRate(unit))
                    else -> "Bonds are bought in steps of Rp1,000,000."
                }, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                if (!buy && h != null) Chip("Sell everything", sellAll) { amount = h.value }
                if (buy && p.suitable == false) Row(Modifier.clickable(role = Role.Checkbox) { acknowledged = !acknowledged }.semantics { selected = acknowledged }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(acknowledged, { acknowledged = it })
                    Text("I understand this is riskier than my ${d.riskProfile} profile and I could lose money.", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.weight(1f))
                AmountKeypad(amount, { amount = if (!buy && h != null) minOf(it, h.value) else it; error = null })
            }
            val ok = amount > 0 && (if (buy) amount >= p.minBuy && (p.kind != "bond" || amount % 1_000_000 == 0L) && (p.suitable != false || acknowledged) else h != null)
            OrvynButton(if (buy) "Buy ${Money.format(amount)}" else "Sell ${Money.format(amount)}", enabled = ok, onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
        }
        1 -> PinConfirm("To ${if (buy) "buy" else "sell"} ${Money.format(amount)} of ${p.name}.", onBack = { step = 0 }, onRefused = { error = it; step = 0 }) { pin ->
            reference = (if (buy) Api.buyProduct(id, amount, null, acknowledged, pin, key)
                else Api.sellProduct(id, if (sellAll) null else quantity, pin, key)).reference
            DataVersion.count++; step = 2
        }
        else -> SuccessScreen(if (buy) "Bought" else "Sold", Money.format(amount), "of ${p.name}",
            details = {
                when (p.kind) {
                    "gold" -> ReceiptLine("Price", "${Money.format(unit.toLong())} a gram")
                    "bond" -> ReceiptLine("Coupon", "${percent(p.couponBp ?: 0)} a year, paid monthly")
                    else -> ReceiptLine("NAV per unit", formatRate(unit))
                }
                ReceiptLine("Reference", reference)
            },
            primaryLabel = "Done", onPrimary = onExit)
    }
}
