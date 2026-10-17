package id.orvyn.app.ui.profile

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.components.*
import id.orvyn.app.ui.components.ListRow
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.OrvynTextButton
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.formatDateTime
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.launch
import java.time.Instant

// Story S3.12: passkeys. The phone's password manager makes and keeps the key; ORVYN's server only ever sees the public half.

class PasskeyCancelled : Exception()

/** Asks the phone to make a passkey for the signed-in customer, then hands it to the server. */
suspend fun createPasskey(activity: Activity) {
    val response = try {
        CredentialManager.create(activity).createCredential(activity, CreatePublicKeyCredentialRequest(Api.passkeyOptions())) as CreatePublicKeyCredentialResponse
    } catch (e: CreateCredentialCancellationException) { throw PasskeyCancelled() }
    catch (e: CreateCredentialException) { throw ApiException("PASSKEY", "This phone couldn't make a passkey. Check that a screen lock is set up.", 0, null) }
    Api.registerPasskey(response.registrationResponseJson)
}

/** S3.12 AC2: the phone offers its ORVYN passkey; the server checks it and signs this phone in. */
suspend fun signInWithPasskey(activity: Activity): Api.SignIn {
    val credential = try {
        CredentialManager.create(activity).getCredential(activity, GetCredentialRequest(listOf(GetPublicKeyCredentialOption(Api.passkeySignInOptions())))).credential
    } catch (e: GetCredentialCancellationException) { throw PasskeyCancelled() }
    catch (e: NoCredentialException) { throw ApiException("PASSKEY", "There's no ORVYN passkey on this phone yet. Sign in with your password, then add one in Profile.", 0, null) }
    catch (e: GetCredentialException) { throw ApiException("PASSKEY", "The passkey couldn't be used. Try again or sign in with your password.", 0, null) }
    return Api.signInWithPasskey((credential as PublicKeyCredential).authenticationResponseJson)
}

@Composable
fun PasskeysScreen(onBack: () -> Unit) {
    val activity = LocalContext.current as Activity
    val list = rememberLoad { Api.passkeys() }
    var message by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snacks = LocalSnacks.current
    FlowScreen("Passkeys", "Sign in on a new phone with your fingerprint or screen lock instead of your password. The passkey stays in your phone's password manager.", onBack) {
        Column(Modifier.weight(1f)) {
            // Q-B3 AC4: a list that didn't load is an error, never "No passkeys yet."
            LoadContent(list, "Couldn't load your passkeys", skeleton = { Skeleton(Modifier.fillMaxWidth().height(80.dp)) }) { l ->
                Column {
                    if (l.isEmpty()) Text("No passkeys yet.", style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary)
                    else l.forEachIndexed { i, (id, at) ->
                        ListRow("Passkey ${i + 1}", "Added ${formatDateTime(at)}", R.drawable.ph_fingerprint) { OrvynTextButton("Remove", onClick = { removing = id }) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp)) }
        }
        OrvynButton("Add a passkey", loading = busy, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), onClick = {
            busy = true
            scope.launch {
                try { createPasskey(activity); message = "Passkey added. Next time, tap Sign in with a passkey."; list.reload() }
                catch (_: PasskeyCancelled) {}
                catch (e: ApiException) { message = e.message }
                busy = false
            }
        })
    }
    removing?.let { id ->
        ConfirmDialog("Remove this passkey?", "You'll need your password to sign in on a new phone. Also delete it from your password manager if you like.", "Remove",
            busy = deleting, destructive = true, onDismiss = { removing = null }, onConfirm = {
                deleting = true
                scope.launch {
                    try { Api.deletePasskey(id); list.reload() } catch (e: ApiException) { snacks?.show(e.message, error = true) }
                    deleting = false; removing = null
                }
            })
    }
}
