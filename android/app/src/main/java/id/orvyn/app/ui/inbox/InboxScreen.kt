package id.orvyn.app.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.DataVersion
import id.orvyn.app.data.InboxItem
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.dayLabel
import id.orvyn.app.ui.formatTime
import id.orvyn.app.ui.theme.OrvynTheme

/** Story S1.27: every notification, kept in the app. Opening it marks everything read. */
@Composable
fun InboxScreen(onBack: () -> Unit, onOpenTransaction: (String) -> Unit) {
    var items by remember { mutableStateOf<List<InboxItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try { items = Api.inbox(); Api.markInboxRead(); DataVersion.count++ } catch (e: ApiException) { error = e.message }
    }
    FlowScreen("Inbox", null, onBack) {
        val list = items
        when {
            error != null -> MessageState(R.drawable.ph_warning_circle, "Couldn't load your inbox", error!!)
            list == null -> repeat(4) { Skeleton(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(56.dp)) }
            list.isEmpty() -> MessageState(R.drawable.ph_bell, "Nothing here yet", "Money in, money out and news from ORVYN show up here.")
            else -> LazyColumn {
                items(list, key = { it.id }) { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = m.link?.startsWith("tx:") == true) { onOpenTransaction(m.link!!.removePrefix("tx:")) }
                            .padding(vertical = 14.dp).semantics { if (!m.read) stateDescription = "Unread" },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape)
                            .background(if (m.read) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.primary))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text("${dayLabel(m.at)}, ${formatTime(m.at)}", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textTertiary)
                            }
                            Text(m.body, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
