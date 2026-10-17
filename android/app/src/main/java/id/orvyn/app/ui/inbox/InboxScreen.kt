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
import id.orvyn.app.ui.components.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.dayLabel
import id.orvyn.app.ui.formatTime
import id.orvyn.app.ui.theme.OrvynTheme

/** "Sinta Maharani asks for Rp50,000: lunch" → "Sinta Maharani asks you for Rp50,000" (design.md 10.7). */
private fun requestLabel(body: String) = Regex("^(.+?) asks for (Rp[\\d,]+)").find(body)?.let { "${it.groupValues[1]} asks you for ${it.groupValues[2]}" } ?: body

/**
 * Story S1.27 and Q-E7: every notification, kept in the app. Opening it marks everything read. Rows with somewhere to go
 * are buttons; the others are plain text (never announced "disabled"). Unread titles are bolder and darker.
 */
@Composable
fun InboxScreen(onBack: () -> Unit, onOpenTransaction: (String) -> Unit, onOpenRequest: (String) -> Unit = {}) {
    val load = rememberLoad { Api.inbox().also { runCatching { Api.markInboxRead(); DataVersion.count++ } } }
    FlowScreen("Inbox", null, onBack) {
        LoadContent(load, "Couldn't load your inbox", skeleton = { Column { repeat(4) { Skeleton(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(56.dp)) } } }) { list ->
            if (list.isEmpty()) MessageState(R.drawable.ph_bell, "Nothing here yet", "Money in, money out and news from ORVYN show up here.")
            else LazyColumn {
                items(list, key = { it.id }) { m ->
                    val link = m.link?.takeIf { it.startsWith("tx:") || it.startsWith("request:") }
                    val time = "${dayLabel(m.at)}, ${formatTime(m.at)}"
                    val label = (if (!m.read) "Unread, " else "") + (if (link?.startsWith("request:") == true) requestLabel(m.body) else spokenCard("${m.title}, ${m.body}")) + ", $time"
                    Row(
                        Modifier.fillMaxWidth()
                            .then(if (link != null) Modifier.clickable(role = Role.Button, onClick = rememberOnce {
                                if (link.startsWith("tx:")) onOpenTransaction(link.removePrefix("tx:")) else onOpenRequest(link.removePrefix("request:"))
                            }) else Modifier)
                            .semantics(mergeDescendants = true) { contentDescription = label }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape)
                            .background(if (m.read) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.primary))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (m.read) FontWeight.SemiBold else FontWeight.Bold),
                                    color = if (m.read) OrvynTheme.colors.textSecondary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                Text(time, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textTertiary)
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
