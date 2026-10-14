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
import id.orvyn.app.ui.components.MessageState
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
fun CardsScreen(onPayBill: (Card) -> Unit, say: (String) -> Unit) {
    var cards by remember { mutableStateOf<List<Card>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(DataVersion.count) { try { cards = Api.cards() } catch (e: ApiException) { error = e.message } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Text("Cards", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 16.dp).semantics { heading() })
        val list = cards
        when {
            error != null && list == null -> MessageState(R.drawable.ph_warning_circle, "Couldn't load your cards", error!!)
            list == null -> Skeleton(Modifier.padding(horizontal = 20.dp).fillMaxWidth().aspectRatio(1.586f))
            else -> {
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
                val card = list[pager.currentPage]
                CardControls(card, onChange = { patch ->
                    scope.launch {
                        try { cards = Api.updateCard(card.id, patch) } catch (e: ApiException) { say(e.message) }
                    }
                }, onPayBill = { onPayBill(card) }, say = say)
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

@Composable
private fun CardControls(card: Card, onChange: (JSONObject) -> Unit, onPayBill: () -> Unit, say: (String) -> Unit) {
    var showDetails by remember(card.id) { mutableStateOf(false) }
    var limitSheet by remember { mutableStateOf(false) }
    var txs by remember(card.id) { mutableStateOf<List<Transaction>?>(null) }
    LaunchedEffect(card.id, DataVersion.count) { runCatching { txs = Api.cardTransactions(card.id) } }

    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        card.credit?.let { c ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This month's bill", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                Text(Money.format(c.owed), style = id.orvyn.app.ui.theme.OrvynType.displayBalance.copy(fontSize = MaterialTheme.typography.headlineLarge.fontSize))
                LinearProgressIndicator(progress = { (c.owed.toFloat() / c.limit).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant)
                Text("${Money.format(c.available)} available of ${Money.format(c.limit)}", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                if (c.owed > 0) Text("Minimum ${Money.format(c.minimum)} · due ${c.dueDate.substring(8)} ${monthName(c.dueDate)}", style = MaterialTheme.typography.bodyMedium)
                if (c.owed > 0) OrvynButton("Pay bill", onClick = onPayBill)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)) {
            SwitchRow("Freeze card", "Stops every payment until you unfreeze it", card.frozen) { onChange(JSONObject().put("frozen", it)); say(if (it) "Card frozen" else "Card unfrozen") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow("Online payments", null, card.online) { onChange(JSONObject().put("online", it)) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow("Overseas payments", null, card.overseas) { onChange(JSONObject().put("overseas", it)) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow("Contactless", null, card.contactless) { onChange(JSONObject().put("contactless", it)) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ActionRow("Daily limit", Money.format(card.dailyLimit)) { limitSheet = true }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ActionRow("Show card details", "Needs your PIN") { showDetails = true }
        }
        Text("RECENT CARD PAYMENTS", style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary)
        val t = txs
        when {
            t == null -> Skeleton(Modifier.fillMaxWidth().height(48.dp))
            t.isEmpty() -> Text(if (card.kind == "debit") "Debit card payments show in your History." else "No card payments yet.",
                style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
            else -> Column(Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)) {
                t.take(10).forEach { TransactionRow(it, onClick = {}) }
            }
        }
    }
    if (showDetails) DetailsSheet(card) { showDetails = false }
    if (limitSheet) OrvynBottomSheet(onDismiss = { limitSheet = false }) {
        Text("Daily limit", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        val options = if (card.kind == "credit") listOf(1_000_000L, 5_000_000L, 10_000_000L, 20_000_000L, 50_000_000L) else listOf(1_000_000L, 2_500_000L, 5_000_000L, 10_000_000L, 20_000_000L)
        for (o in options) ActionRow(Money.format(o), if (o == card.dailyLimit) "Current" else null) { onChange(JSONObject().put("dailyLimit", o)); limitSheet = false }
        Spacer(Modifier.height(24.dp))
    }
}

private fun monthName(iso: String) = java.time.LocalDate.parse(iso).month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary) }
        }
        Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary))
    }
}

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
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as ComponentActivity
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(Unit) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val hideOnLeave = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) onClose() }
        lifecycle.lifecycle.addObserver(hideOnLeave)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE); lifecycle.lifecycle.removeObserver(hideOnLeave) }
    }
    LaunchedEffect(secret) { if (secret != null) { delay(30_000); onClose() } }
    OrvynBottomSheet(onDismiss = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (secret == null) {
                Text("Enter your PIN to see the card number", style = MaterialTheme.typography.titleLarge)
                PinPad(onComplete = { pin -> scope.launch { try { secret = Api.cardDetails(card.id, pin) } catch (e: ApiException) { error = e.message; errors++ } } },
                    errorCount = errors, errorText = error, onBiometric = null)
            } else {
                CardVisual(card, secret)
                Text("Hidden again in 30 seconds, or when you leave ORVYN.", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                OrvynButton("Hide now", onClick = onClose)
            }
        }
    }
}
