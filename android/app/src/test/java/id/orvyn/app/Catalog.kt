package id.orvyn.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.ui.components.AmountDisplay
import id.orvyn.app.ui.components.AmountKeypad
import id.orvyn.app.ui.components.ButtonKind
import id.orvyn.app.ui.components.ListRow
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.MoneyText
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.OrvynCard
import id.orvyn.app.ui.components.OrvynTextButton
import id.orvyn.app.ui.components.OrvynTextField
import id.orvyn.app.ui.components.PinPad
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.components.Status
import id.orvyn.app.ui.components.StatusChip
import id.orvyn.app.ui.components.Tier
import id.orvyn.app.ui.components.TierBadge
import androidx.compose.ui.text.input.KeyboardType
import id.orvyn.app.ui.theme.OrvynTheme

// Component sheets for screenshot review (story S0.7). Example data only.

@Composable
private fun Page(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
) { content() }

@Composable
private fun Eyebrow(text: String) = Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = OrvynTheme.colors.textSecondary)

@Composable
fun CatalogBasics() = Page {
    Eyebrow("Buttons")
    OrvynButton("Send Rp500,000", onClick = {})
    OrvynButton("Sending", onClick = {}, loading = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OrvynButton("Share receipt", onClick = {}, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
        OrvynButton("Reverse", onClick = {}, kind = ButtonKind.Destructive, modifier = Modifier.weight(1f))
    }
    OrvynButton("Continue", onClick = {}, enabled = false)
    OrvynTextButton("Forgot PIN?", onClick = {})
    Eyebrow("Fields")
    OrvynTextField("Phone number", "0812 3456 7890", {}, helper = "We'll text you a 6-digit code.", keyboardType = KeyboardType.Phone)
    OrvynTextField("NIK", "32750123", {}, error = "NIK has 16 digits. You entered 8.")
    OrvynTextField("Password", "rahasia123", {}, password = true)
}

@Composable
fun CatalogDisplay() = Page {
    Eyebrow("Badges and status")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TierBadge(Tier.Everyday); TierBadge(Tier.Prestige); TierBadge(Tier.Private)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip(Status.Success); StatusChip(Status.Pending)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip(Status.Failed); StatusChip(Status.Reversed)
    }
    Eyebrow("Card and list rows")
    OrvynCard {
        Text("ORVYN Everyday", style = MaterialTheme.typography.titleMedium)
        Text("4021 7788 3310", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
        MoneyText(12_920_500, signed = false)
    }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        ListRow("Salary PT Maju Jaya", "Today, 09:12", R.drawable.ph_arrows_left_right) { MoneyText(12_000_000) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ListRow("Kopi Tuku Cipete", "QRIS · Yesterday, 08:40", R.drawable.ph_qr_code) { MoneyText(-38_000) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ListRow("Dimas Pratama", "Reversed · 12 Oct", R.drawable.ph_arrows_left_right) { MoneyText(-500_000, struck = true) }
    }
    Eyebrow("Loading")
    Row { Skeleton(Modifier.width(40.dp).height(40.dp)); Spacer(Modifier.width(12.dp)); Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Skeleton(Modifier.width(180.dp).height(14.dp)); Skeleton(Modifier.width(120.dp).height(12.dp)) } }
    Snackbar { Text("Account number copied") }
}

@Composable
fun CatalogAmount() = Page {
    Spacer(Modifier.height(24.dp))
    AmountDisplay(amount = 250_000, available = 12_920_500, error = null, onQuickPick = {})
    Spacer(Modifier.weight(1f))
    AmountKeypad(amount = 250_000, onAmountChange = {})
    OrvynButton("Continue", onClick = {})
}

@Composable
fun CatalogPin() = Page {
    Spacer(Modifier.height(24.dp))
    Text("Enter your PIN", style = MaterialTheme.typography.headlineMedium)
    Text("To send Rp250,000 to Dimas Pratama", style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary)
    Spacer(Modifier.weight(1f))
    PinPad(onComplete = {}, errorCount = 0, errorText = "Wrong PIN. 4 tries left.", onBiometric = {})
}

@Composable
fun CatalogEmpty() = Page {
    Spacer(Modifier.weight(1f))
    MessageState(R.drawable.ph_tray, "No transactions yet", "Money you send and receive shows up here.", action = { OrvynTextButton("Make a transfer", onClick = {}) })
    MessageState(R.drawable.ph_warning_circle, "Couldn't load your accounts", "Check your connection, then try again.", action = { OrvynTextButton("Try again", onClick = {}) })
    Spacer(Modifier.weight(1f))
}
