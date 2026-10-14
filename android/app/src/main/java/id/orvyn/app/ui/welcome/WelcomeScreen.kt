package id.orvyn.app.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.orvyn.app.R
import id.orvyn.app.ui.MarkOn
import id.orvyn.app.ui.OrvynMark
import id.orvyn.app.ui.theme.Brand

/** First screen after the splash. Same look in light and dark: it is a brand moment on the hero gradient. */
@Composable
fun WelcomeScreen(onSignIn: () -> Unit, onRegister: () -> Unit) {
    id.orvyn.app.ui.DarkTopBar()
    Box(
        Modifier
            .fillMaxSize()
            .background(Brand.Hero)
            .drawBehind {
                // Jersey stripes, 22dp wide, white at 5%
                val w = 22.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawRect(Color.White.copy(alpha = 0.05f), Offset(x, 0f), Size(w, size.height))
                    x += w * 2
                }
            }
    ) {
        // The brand moment: an oversized mark bleeding off the right edge, quiet enough not to compete with the copy.
        OrvynMark(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 96.dp, y = 140.dp)
                .size(320.dp),
            on = MarkOn.Watermark,
        )
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OrvynMark(Modifier.size(32.dp), on = MarkOn.Blue)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge.copy(letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.75f),
                    color = Color.White,
                )
            }

            Spacer(Modifier.weight(1f).heightIn(min = 48.dp))

            Text(
                stringResource(R.string.tagline).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Brand.Giallo,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = MaterialTheme.typography.headlineLarge.fontSize * 1.25f, lineHeight = MaterialTheme.typography.headlineLarge.lineHeight * 1.2f),
                color = Color.White,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                color = Brand.HeroTextMuted,
            )
            Spacer(Modifier.height(32.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.Nerazzurro),
                ) { Text(stringResource(R.string.welcome_sign_in), style = MaterialTheme.typography.labelLarge) }
                TextButton(
                    onClick = onRegister,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) { Text(stringResource(R.string.welcome_register), style = MaterialTheme.typography.labelLarge) }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.demo_label),
                style = MaterialTheme.typography.labelMedium,
                color = Brand.HeroTextMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
