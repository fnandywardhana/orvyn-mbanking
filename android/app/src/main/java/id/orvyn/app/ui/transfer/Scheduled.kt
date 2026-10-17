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
import id.orvyn.app.ui.components.*
import androidx.compose.foundation.layout.size
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
    val list = rememberLoad { Api.scheduled() }
    var deleting by remember { mutableStateOf<Scheduled?>(null) }
    var working by remember { mutableStateOf<String?>(null) } // the schedule being changed
    val scope = rememberCoroutineScope()
    val snacks = LocalSnacks.current
    // Q-B1: nothing on screen changes until the server says yes; a failure is said in a snackbar.
    fun change(id: String, block: suspend () -> Unit) = scope.launch {
        working = id
        try { block(); list.reload() } catch (e: ApiException) { snacks?.show(e.message, error = true) }
        working = null; deleting = null
    }

    FlowScreen("Scheduled transfers", "They go out at 07:00 WIB on the day. If one can't go through, you get a message.", onBack) {
        LoadContent(list, "Couldn't load your schedules", skeleton = { Skeleton(Modifier.fillMaxWidth().height(120.dp)) }) { l ->
            if (l.isEmpty()) MessageState(R.drawable.ph_calendar_blank, "Nothing scheduled", "On the transfer review screen, pick Schedule to send later or every month.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(l, key = { it.id }) { s ->
                    OrvynCard {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (s.nextRun != null) {
                                if (working == s.id && deleting == null) androidx.compose.material3.CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                                else OrvynTextButton(if (s.paused) "Resume" else "Pause", enabled = working == null, onClick = { change(s.id) { Api.updateSchedule(s.id, paused = !s.paused) } })
                            }
                            Spacer(Modifier.weight(1f))
                            OrvynTextButton("Delete", enabled = working == null, onClick = { deleting = s })
                        }
                    }
                }
            }
        }
    }
    deleting?.let { s ->
        ConfirmDialog(title = "Delete this schedule?", body = "No more transfers of ${Money.format(s.amount)} to ${s.name}. Money already sent stays sent.",
            confirmLabel = "Delete", destructive = true, busy = working == s.id, onDismiss = { deleting = null },
            onConfirm = { change(s.id) { Api.updateSchedule(s.id, delete = true) } })
    }
}
