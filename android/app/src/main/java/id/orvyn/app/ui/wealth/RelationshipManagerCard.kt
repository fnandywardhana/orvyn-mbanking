package id.orvyn.app.ui.wealth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.orvyn.app.data.RelationshipManager
import id.orvyn.app.ui.components.ButtonKind
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.theme.Brand
import id.orvyn.app.ui.theme.OrvynTheme

/** Story S3.11: a monogram (never a photo of a real person), and one-tap call or message through the phone's own apps. */
@Composable
fun RelationshipManagerCard(rm: RelationshipManager, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tel = rm.phone.filter { it.isDigit() || it == '+' }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, Brand.Giallo.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Brand.Hero).border(2.dp, Brand.Giallo, CircleShape), contentAlignment = Alignment.Center) {
                Text(rm.name.split(' ').take(2).joinToString("") { it.take(1) }, style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(rm.name, style = MaterialTheme.typography.titleMedium)
                Text(rm.title, style = MaterialTheme.typography.bodySmall, color = OrvynTheme.colors.textSecondary)
                Text("${rm.phone} · ${rm.email}", style = MaterialTheme.typography.bodySmall, color = OrvynTheme.colors.textSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // ACTION_DIAL and SENDTO open the phone's own apps with the number filled in; ORVYN needs no permission and calls nothing itself.
            OrvynButton("Call", kind = ButtonKind.Secondary, modifier = Modifier.weight(1f), onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel"))) })
            OrvynButton("Message", kind = ButtonKind.Secondary, modifier = Modifier.weight(1f), onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$tel"))) })
        }
    }
}
