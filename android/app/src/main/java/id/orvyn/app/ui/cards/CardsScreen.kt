package id.orvyn.app.ui.cards

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.Card
import id.orvyn.app.data.CardSecret
import id.orvyn.app.data.DataVersion
import id.orvyn.app.data.Transaction
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.components.*
import id.orvyn.app.ui.components.OrvynBottomSheet
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.PinPad
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.home.TransactionRow
import id.orvyn.app.ui.theme.Brand
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Stories S1.23–S1.25. */
@Composable
fun CardsScreen(onPayBill: (Card) -> Unit, onOpenTransaction: (String) -> Unit) {
    val cards = rememberLoad(DataVersion.count) { Api.cards() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Text("Cards", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 16.dp).semantics { heading() })
        LoadContent(cards, "Couldn't load your cards", skeleton = { Skeleton(Modifier.padding(horizontal = 20.dp).fillMaxWidth().aspectRatio(1.586f), shape = RoundedCornerShape(18.dp)) }) { list ->
            Column {
                val pager = rememberPagerState { list.size }
                HorizontalPager(pager, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp) { i ->
                    CardVisual(list[i])
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(list.size) { i ->
                        Box(Modifier.padding(4.dp).size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                            .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
                    }
                }
                val card = list[pager.currentPage.coerceAtMost(list.lastIndex)]
                CardControls(card, onUpdated = { cards.value = it }, onPayBill = { onPayBill(card) }, onOpenTransaction = onOpenTransaction)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** The card itself. Numbers stay masked here; the full number is only shown after the PIN. */
@Composable
fun CardVisual(card: Card, secret: CardSecret? = null) {
    val bg = when (card.product) {
        "voyage" -> Brush.linearGradient(listOf(Color(0xFF1A1B22), Brand.Nero))
        "cashback" -> Brush.linearGradient(listOf(Brand.Nero, Color(0xFF0A1650)))
        else -> Brand.Hero
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(18.dp)).background(bg)
            .drawBehind {
                val w = 18.dp.toPx(); var x = size.width * 0.55f
                val stripe = if (card.product == "voyage") Brand.Giallo.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f)
                while (x < size.width) { drawRect(stripe, Offset(x, 0f), Size(w, size.height)); x += w * 2 }
            }
            .semantics { contentDescription = "${card.name} ending ${card.last4}${if (card.frozen) ", frozen" else ""}" }
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            id.orvyn.app.ui.OrvynMark(Modifier.size(28.dp), on = if (card.product == "debit") id.orvyn.app.ui.MarkOn.Blue else id.orvyn.app.ui.MarkOn.Dark)
            Text(card.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
        Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(secret?.number?.chunked(4)?.joinToString(" ") ?: "•••• •••• •••• ${card.last4}",
                style = MaterialTheme.typography.titleLarge, color = Color.White)
            if (secret != null) Text("Expires ${secret.expiry}   CVV ${secret.cvv}", style = MaterialTheme.typography.bodyLarge, color = Brand.HeroTextMuted)
        }
        Text(if (card.kind == "credit") "CREDIT" else "DEBIT", style = MaterialTheme.typography.labelSmall,
            color = if (card.product == "voyage") Brand.Giallo else Brand.HeroTextMuted, modifier = Modifier.align(Alignment.BottomEnd))
        if (card.frozen) Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
            Text("Frozen", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
    }
}

/** Q-C6 (design.md 7.6): what each switch says once the server agreed. */
private fun switchedMessage(key: String, on: Boolean) = when (key) {
    "frozen" -> if (on) "Card frozen" else "Card unfrozen"
    "online" -> if (on) "Online payments are on" else "Online payments are off"
    "overseas" -> if (on) "Overseas payments are on" else "Overseas payments are off"
    else -> if (on) "Contactless is on" else "Contactless is off"
}

@Composable
private fun CardControls(card: Card, onUpdated: (List<Card>) -> Unit, onPayBill: () -> Unit, onOpenTransaction: (String) -> Unit) {
    var showDetails by remember(card.id) { mutableStateOf(false) }
    var limitSheet by remember { mutableStateOf(false) }
    val txs = rememberLoad(card.id, DataVersion.count) { Api.cardTransactions(card.id) }
    val snacks = LocalSnacks.current
    val scope = rememberCoroutineScope()
    // The switch moves at once; a spinner shows until the server answers. No to: it moves back and says why.
    var pending by remember(card.id) { mutableStateOf<Pair<String, Boolean>?>(null) }
    fun change(key: String, value: Boolean) {
        if (pending != null) return
        pending = key to value
        scope.launch {
            try { onUpdated(Api.updateCard(card.id, JSONObject().put(key, value))); snacks?.show(switchedMessage(key, value)) }
            catch (e: ApiException) { snacks?.show(e.message, error = true) }
            pending = null
        }
    }
    fun shown(key: String, actual: Boolean) = pending?.takeIf { it.first == key }?.second ?: actual

    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        card.credit?.let { c ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This month's bill", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                AmountText(Money.format(c.owed), id.orvyn.app.ui.theme.OrvynType.displayBalance.copy(fontSize = MaterialTheme.typography.headlineLarge.fontSize))
                LinearProgressIndicator(progress = { (c.owed.toFloat() / c.limit).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant)
                Text("${Money.format(c.available)} available of ${Money.format(c.limit)}", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                if (c.owed > 0) Text("Minimum ${Money.format(c.minimum)} · due ${c.dueDate.substring(8)} ${monthName(c.dueDate)}", style = MaterialTheme.typography.bodyMedium)
                if (c.owed > 0) OrvynButton("Pay bill", onClick = onPayBill)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)) {
            for ((key, title, sub, value) in listOf(
                Quad("frozen", "Freeze card", "Stops every payment until you unfreeze it", card.frozen),
                Quad("online", "Online payments", null, card.online),
                Quad("overseas", "Overseas payments", null, card.overseas),
                Quad("contactless", "Contactless", null, card.contactless),
            )) {
                SwitchRow(title, sub, shown(key, value), { change(key, it) }, saving = pending?.first == key, enabled = pending == null || pending?.first == key)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            ActionRow("Daily limit", Money.format(card.dailyLimit)) { limitSheet = true }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ActionRow("Show card details", "Needs your PIN") { showDetails = true }
        }
        Text("RECENT CARD PAYMENTS", style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary)
        LoadContent(txs, "Couldn't load these", skeleton = { Skeleton(Modifier.fillMaxWidth().height(48.dp)) }) { t ->
            if (t.isEmpty()) Text(if (card.kind == "debit") "Debit card payments show in your History." else "No card payments yet.",
                style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
            else Column(Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)) {
                t.take(10).forEach { tx -> TransactionRow(tx, onClick = { onOpenTransaction(tx.id) }) } // Q-E8 AC1
            }
        }
    }
    if (showDetails) DetailsSheet(card) { showDetails = false }
    if (limitSheet) OrvynBottomSheet(onDismiss = { limitSheet = false }) {
        Text("Daily limit", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        val options = if (card.kind == "credit") listOf(1_000_000L, 5_000_000L, 10_000_000L, 20_000_000L, 50_000_000L) else listOf(1_000_000L, 2_500_000L, 5_000_000L, 10_000_000L, 20_000_000L)
        for (o in options) ActionRow(Money.format(o), if (o == card.dailyLimit) "Current" else null) {
            limitSheet = false
            scope.launch { try { onUpdated(Api.updateCard(card.id, JSONObject().put("dailyLimit", o))) } catch (e: ApiException) { snacks?.show(e.message, error = true) } }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private data class Quad(val a: String, val b: String, val c: String?, val d: Boolean)

private fun monthName(iso: String) = java.time.LocalDate.parse(iso).month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)

@Composable
private fun ActionRow(title: String, value: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        value?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary) }
    }
}

/** PIN, then the full card for 30 seconds. The window blocks screenshots while it's shown (S1.23 AC4). */
@Composable
private fun DetailsSheet(card: Card, onClose: () -> Unit) {
    var secret by remember { mutableStateOf<CardSecret?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    SecureWhileShown() // counted with the PIN pad's own, so the number stays uncapturable after the pad goes (Q-C4 AC3)
    DisposableEffect(Unit) {
        val hideOnLeave = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) onClose() }
        lifecycle.lifecycle.addObserver(hideOnLeave)
        onDispose { lifecycle.lifecycle.removeObserver(hideOnLeave) }
    }
    LaunchedEffect(secret) { if (secret != null) { delay(30_000); onClose() } }
    OrvynBottomSheet(onDismiss = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (secret == null) {
                Text("Enter your PIN to see the card number", style = MaterialTheme.typography.titleLarge)
                PinPad(onComplete = { pin ->
                    busy = true; error = null
                    scope.launch { try { secret = Api.cardDetails(card.id, pin) } catch (e: ApiException) { error = e.message; if (e.code == "WRONG_PIN") errors++ } finally { busy = false } }
                }, errorCount = errors, errorText = error, onBiometric = null, busyText = if (busy) "Checking your PIN…" else null)
            } else {
                CardVisual(card, secret)
                Text("Hidden again in 30 seconds, or when you leave ORVYN.", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                OrvynButton("Hide now", onClick = onClose)
            }
        }
    }
}
