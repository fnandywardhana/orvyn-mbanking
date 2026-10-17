package id.orvyn.app.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.orvyn.app.ui.theme.OrvynTheme

/** Bottom sheet with 20dp top corners and a 50% (light) / 60% (dark) scrim. Swipe down or tap outside to close. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrvynBottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true), // tall sheets open fully, so buttons at the bottom are never hidden
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = if (isSystemInDarkTheme()) 0.6f else 0.5f),
        content = content,
    )
}

/**
 * Only for destructive or irreversible confirmations (design.md 4.16): a title, one sentence, two actions. A destructive
 * confirm is a filled `error` button; [dismissLabel] names what stays ("Cancel", "Keep request"). While [busy] the confirm
 * button spins and the dialog stays open.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissLabel: String = "Cancel",
    busy: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary) },
        confirmButton = {
            if (destructive) OrvynButton(confirmLabel, onClick = onConfirm, kind = ButtonKind.Destructive, loading = busy, fill = false)
            else OrvynTextButton(confirmLabel, onClick = onConfirm, enabled = !busy)
        },
        dismissButton = { OrvynTextButton(dismissLabel, onClick = onDismiss, enabled = !busy) },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
