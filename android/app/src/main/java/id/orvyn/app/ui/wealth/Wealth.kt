package id.orvyn.app.ui.wealth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.AppConfig
import id.orvyn.app.data.DataVersion
import id.orvyn.app.data.Wealth
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.ThemeIsDark
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.components.ListRow
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OrvynTextButton
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.theme.OrvynTheme
import id.orvyn.app.ui.theme.OrvynType

// Story S3.9: everything a customer owns, in Rupiah, in one place.

/** Categorical slots from the dataviz reference palette, in validated order (light / dark steps). */
private val SLOTS_LIGHT = listOf(0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4, 0xFF008300).map { Color(it) }
private val SLOTS_DARK = listOf(0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500, 0xFFD55181, 0xFF008300).map { Color(it) }
private val ORDER = listOf("cash", "deposits", "fx", "funds", "bonds", "gold") // colour follows the asset type, never its rank

@Composable
fun slotColor(key: String): Color = (if (ThemeIsDark.value) SLOTS_DARK else SLOTS_LIGHT)[ORDER.indexOf(key).coerceAtLeast(0)]

/** One 100% bar, 2dp surface gaps between parts, 4dp rounded ends. Labels live in the list below it (identity is never colour alone). */
@Composable
fun CompositionBar(w: Wealth, modifier: Modifier = Modifier) {
    val parts = w.parts.filter { it.value > 0 }
    val colors = parts.map { slotColor(it.key) }
    val gap = MaterialTheme.colorScheme.surface
    Canvas(modifier.fillMaxWidth().height(14.dp).semantics {
        contentDescription = parts.joinToString(", ") { "${it.label} ${it.value * 100 / w.total.coerceAtLeast(1)}%" }
    }) {
        var x = 0f
        val r = 4.dp.toPx()
        parts.forEachIndexed { i, p ->
            val width = size.width * p.value / w.total.coerceAtLeast(1)
            drawRoundRect(colors[i], Offset(x, 0f), Size(width, size.height), CornerRadius(r, r))
            if (i > 0) drawRect(gap, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
            x += width
        }
    }
}

@Composable
fun WealthScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    var w by remember { mutableStateOf<Wealth?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(DataVersion.count) { try { w = Api.wealth(); error = null } catch (e: ApiException) { error = e.message } }
    FlowScreen("Wealth", "Everything you have with ORVYN, valued in Rupiah today.", onBack) {
        val x = w
        when {
            x == null && error != null -> MessageState(R.drawable.ph_warning_circle, "Couldn't load your wealth", error!!)
            x == null -> Skeleton(Modifier.fillMaxWidth().height(200.dp))
            else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Total assets", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                Text(Money.format(x.total), style = OrvynType.displayBalance)
                Spacer(Modifier.height(16.dp))
                CompositionBar(x)
                Spacer(Modifier.height(8.dp))
                for (p in x.parts) {
                    val route = mapOf("cash" to null, "deposits" to "deposits", "fx" to "fx", "funds" to "invest", "bonds" to "invest", "gold" to "invest")[p.key]
                    val enabled = when (p.key) { "deposits" -> AppConfig.on("deposits"); "fx" -> AppConfig.on("fx"); "cash" -> true; else -> AppConfig.on("invest") }
                    if (!enabled && p.value == 0L) continue
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(slotColor(p.key)))
                        Column(Modifier.weight(1f)) {
                            Text(p.label, style = MaterialTheme.typography.titleMedium)
                            Text(share(p.value, x.total), style = MaterialTheme.typography.bodySmall, color = OrvynTheme.colors.textSecondary)
                        }
                        Text(Money.format(p.value), style = MaterialTheme.typography.titleMedium)
                        if (route != null && enabled) OrvynTextButton("Open", onClick = { onOpen(route) })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (x.creditOwed > 0) {
                    SectionLabel("Owed")
                    ListRow("Credit cards", "Not taken off your assets", R.drawable.ph_credit_card) { Text(Money.format(x.creditOwed), style = MaterialTheme.typography.titleMedium) }
                }
                SectionLabel("Grow your money")
                if (AppConfig.on("deposits")) ListRow("Time deposits", "Fixed rate for 1 to 12 months", R.drawable.ph_calendar_blank, onClick = { onOpen("deposits") }) { Chevron() }
                if (AppConfig.on("fx")) ListRow("Foreign currency", "USD, SGD, JPY, AUD and EUR", R.drawable.ph_coins, onClick = { onOpen("fx") }) { Chevron() }
                if (AppConfig.on("invest")) ListRow("Investments", "Mutual funds, bonds and gold", R.drawable.ph_chart_bar, onClick = { onOpen("invest") }) { Chevron() }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** "12% of assets"; a small but real share says so instead of rounding to 0%. */
private fun share(value: Long, total: Long) = (value * 100 / total.coerceAtLeast(1)).let { if (value > 0 && it == 0L) "Less than 1% of assets" else "$it% of assets" }

@Composable
fun Chevron() = Icon(painterResource(R.drawable.ph_caret_right), null, tint = OrvynTheme.colors.textSecondary)

/** Home screen card: the total and the bar, tap for the full view. */
@Composable
fun WealthCard(onOpen: () -> Unit) {
    var w by remember { mutableStateOf<Wealth?>(null) }
    LaunchedEffect(DataVersion.count) { w = runCatching { Api.wealth() }.getOrNull() }
    val x = w ?: return
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable(onClickLabel = "Open wealth", onClick = onOpen).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Total assets", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                Text(Money.format(x.total), style = MaterialTheme.typography.titleLarge)
            }
            Chevron()
        }
        CompositionBar(x)
    }
}

