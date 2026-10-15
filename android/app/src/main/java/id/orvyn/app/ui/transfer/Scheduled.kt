package id.orvyn.app.ui.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.Scheduled
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.components.ConfirmDialog
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OrvynTextButton
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Story S2.5: every schedule with its next date and last result; pause, resume or delete. */
@Composable
fun ScheduledScreen(onBack: () -> Unit) {
    var list by remember { mutableStateOf<List<Scheduled>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Scheduled?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) { try { list = Api.scheduled(); error = null } catch (e: ApiException) { error = e.message } }
    fun change(block: suspend () -> Unit) = scope.launch { try { block() } catch (e: ApiException) { error = e.message }; reload++ }

    FlowScreen("Scheduled transfers", "They go out at 07:00 WIB on the day. If one can't go through, you get a message.", onBack) {
        val l = list
        when {
            l == null && error != null -> MessageState(R.drawable.ph_warning_circle, "Couldn't load your schedules", error!!,
                action = { OrvynTextButton("Try again", onClick = { reload++ }) })
            l == null -> Skeleton(Modifier.fillMaxWidth().height(120.dp))
            l.isEmpty() -> MessageState(R.drawable.ph_calendar_blank, "Nothing scheduled", "On the transfer review screen, pick Schedule to send later or every month.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(l, key = { it.id }) { s ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(Money.format(s.amount), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(listOfNotNull(repeatLabel(s.repeat, s.nextRun),
                            s.nextRun?.let { "next ${LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMM", Locale.US))}" },
                            if (s.paused) "paused" else null).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, color = if (s.paused) OrvynTheme.colors.warning else OrvynTheme.colors.textSecondary)
                        s.lastOk?.let { ok ->
                            Text(if (ok) "Last one sent" else "Last one didn't go through: ${s.lastMessage}",
                                style = MaterialTheme.typography.bodyMedium, color = if (ok) OrvynTheme.colors.success else MaterialTheme.colorScheme.error)
                        }
                        Row {
                            if (s.nextRun != null) OrvynTextButton(if (s.paused) "Resume" else "Pause", onClick = { change { Api.updateSchedule(s.id, paused = !s.paused) } })
                            Spacer(Modifier.weight(1f))
                            OrvynTextButton("Delete", onClick = { deleting = s })
                        }
                    }
                }
            }
        }
    }
    deleting?.let { s ->
        ConfirmDialog(title = "Delete this schedule?", body = "No more transfers of ${Money.format(s.amount)} to ${s.name}. Money already sent stays sent.",
            confirmLabel = "Delete", destructive = true, onDismiss = { deleting = null },
            onConfirm = { deleting = null; change { Api.updateSchedule(s.id, delete = true) } })
    }
}
