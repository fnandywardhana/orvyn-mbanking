package id.orvyn.app.ui.register

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.DeviceStore
import id.orvyn.app.ui.DobTransformation
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.dobDigits
import id.orvyn.app.ui.dobInput
import id.orvyn.app.ui.parseDobDigits
import id.orvyn.app.ui.components.ButtonKind
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.OrvynTextButton
import id.orvyn.app.ui.components.OrvynTextField
import id.orvyn.app.ui.components.PinPad
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate

private enum class Step { Phone, Code, About, IdPhoto, Selfie, Password, Pin, PinAgain, Waiting }

private val WORK = listOf("Employee", "Business owner", "Civil servant", "Professional", "Student", "Other")
private val INCOME = listOf("Under Rp5 million", "Rp5–10 million", "Rp10–25 million", "Over Rp25 million")
private val PURPOSE = listOf("Savings", "Daily spending", "Receiving salary", "Business", "Investing")

/** Stories S1.1–S1.3. The draft survives closing the app; the password and PIN never touch the phone's storage. */
@Composable
fun RegisterFlow(store: DeviceStore, onExit: () -> Unit, onSignIn: () -> Unit) {
    val saved = remember { store.registration?.let { runCatching { JSONObject(it) }.getOrNull() } }
    var token by remember { mutableStateOf(saved?.optString("token")?.takeIf { it.isNotEmpty() }) }
    val d = remember { saved?.optJSONObject("details") ?: JSONObject() }
    var step by remember { mutableStateOf(when { saved?.optBoolean("sent") == true -> Step.Waiting; token != null && d.length() >= 7 -> Step.IdPhoto; token != null -> Step.About; else -> Step.Phone }) }
    var phone by remember { mutableStateOf(saved?.optString("phone") ?: "") }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    fun save(sent: Boolean = false) { store.registration = JSONObject().put("token", token).put("phone", phone).put("details", d).put("sent", sent).toString() }

    BackHandler(step != Step.Waiting) {
        step = when (step) { Step.Phone -> { onExit(); Step.Phone }; Step.Code -> Step.Phone; Step.About -> Step.Phone; Step.IdPhoto -> Step.About; Step.Selfie -> Step.IdPhoto
            Step.Password -> Step.Selfie; Step.Pin -> Step.Password; Step.PinAgain -> Step.Pin; Step.Waiting -> Step.Waiting }
    }
    val progress = "Step ${step.ordinal + 1} of 8"

    when (step) {
        Step.Phone -> PhoneStep(phone, progress, onExit) { p -> phone = p; step = Step.Code }
        Step.Code -> CodeStep(phone, progress, { step = Step.Phone }) { t -> token = t; save(); step = Step.About }
        Step.About -> AboutStep(d, progress, { step = Step.Phone }) { save(); step = Step.IdPhoto }
        Step.IdPhoto -> PhotoStep("Photo of your KTP", "Fit your KTP inside the frame. $progress.", back = true, card = true, onBack = { step = Step.About }) { step = Step.Selfie }
        Step.Selfie -> PhotoStep("Take a selfie", "Look straight at the camera, face inside the circle. $progress.", back = false, card = false, onBack = { step = Step.IdPhoto }) { step = Step.Password }
        Step.Password -> PasswordStep(progress, { step = Step.Selfie }) { password = it; step = Step.Pin }
        Step.Pin -> PinStep("Create your PIN", "6 digits to open ORVYN and approve payments. Avoid 123456 or your birthday.", null) { pin = it; step = Step.PinAgain }
        Step.PinAgain -> {
            var error by remember { mutableStateOf<String?>(null) }
            var errors by remember { mutableIntStateOf(0) }
            val scope = rememberCoroutineScope()
            PinStep("Enter it again", "Just to be sure you remember it.", error, errors) { again ->
                if (again != pin) { error = "Those PINs didn't match. Start again."; step = Step.Pin; return@PinStep }
                scope.launch {
                    try { Api.registerSubmit(token!!, d, password, pin); password = ""; pin = ""; save(sent = true); step = Step.Waiting }
                    catch (e: ApiException) { error = e.message; errors++ }
                }
            }
        }
        Step.Waiting -> WaitingStep(token!!, onDone = { store.registration = null; onSignIn() }, onRestart = { store.registration = null; token = null; d.keys().asSequence().toList().forEach(d::remove); step = Step.Phone })
    }
}

private fun showOtp(context: Context, code: String): Boolean {
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel("otp", "Sign-up codes", NotificationManager.IMPORTANCE_HIGH))
    NotificationManagerCompat.from(context).notify(1, NotificationCompat.Builder(context, "otp")
        .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("ORVYN code: $code")
        .setContentText("Use it to confirm your phone number. It expires in 5 minutes.").setAutoCancel(true).build())
    return true
}

@Composable
private fun PhoneStep(initial: String, progress: String, onBack: () -> Unit, onSent: (String) -> Unit) {
    val context = LocalContext.current
    var phone by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var fallback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) }
    FlowScreen("Open an account", "First, your phone number. We'll send a 6-digit code to confirm it. $progress.", onBack) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OrvynTextField("Phone number", phone, { phone = it; error = null }, keyboardType = KeyboardType.Phone, error = error, helper = "For example 0812 3456 7890")
            fallback?.let { Text("Notifications are off, so here's your demo code: $it", style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.warning) }
        }
        OrvynButton("Send code", enabled = phone.length >= 10, loading = busy, modifier = Modifier.padding(vertical = 16.dp), onClick = {
            busy = true
            scope.launch {
                try {
                    val (normalized, code) = Api.registerOtp(phone)
                    if (!showOtp(context, code)) { fallback = code; delay(2500) }
                    onSent(normalized)
                } catch (e: ApiException) { error = e.message } finally { busy = false }
            }
        })
    }
}

@Composable
private fun CodeStep(phone: String, progress: String, onBack: () -> Unit, onVerified: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    FlowScreen("Enter the code", "We sent it to $phone as a notification. $progress.", onBack) {
        Column(Modifier.weight(1f)) {
            OrvynTextField("6-digit code", code, { code = it.filter(Char::isDigit).take(6); error = null }, keyboardType = KeyboardType.NumberPassword, error = error)
        }
        OrvynButton("Confirm", enabled = code.length == 6, loading = busy, modifier = Modifier.padding(vertical = 16.dp), onClick = {
            busy = true
            scope.launch { try { onVerified(Api.registerVerify(phone, code)) } catch (e: ApiException) { error = e.message } finally { busy = false } }
        })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(label: String, options: List<String>, value: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (o in options) {
                val sel = o == value
                Box(
                    Modifier.clip(CircleShape).background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable(role = Role.RadioButton) { onPick(o) }.semantics { selected = sel }.heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(o, style = MaterialTheme.typography.labelLarge, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
}

@Composable
private fun AboutStep(d: JSONObject, progress: String, onBack: () -> Unit, onNext: () -> Unit) {
    var name by remember { mutableStateOf(d.optString("fullName")) }
    var nik by remember { mutableStateOf(d.optString("nik")) }
    // Digits only ("17081990"); the slashes are drawn by DobTransformation (hotfix 0.8.1, H1).
    var dob by remember { mutableStateOf(d.optString("birthDate").takeIf { it.isNotEmpty() }?.let { dobDigits(LocalDate.parse(it)) } ?: "") }
    var address by remember { mutableStateOf(d.optString("address")) }
    var work by remember { mutableStateOf(d.optString("occupation")) }
    var income by remember { mutableStateOf(d.optString("incomeRange")) }
    var purpose by remember { mutableStateOf(d.optString("purpose")) }
    var tried by remember { mutableStateOf(false) }
    val date = parseDobDigits(dob)
    val age = date?.let { java.time.Period.between(it, LocalDate.now()).years }
    val errNik = if (tried && nik.length != 16) "NIK has 16 digits. You entered ${nik.length}." else null
    val errDob = if (tried && (date == null || age!! < 17)) (if (date == null) "Use the format DD/MM/YYYY." else "You need to be at least 17 to open an account.") else null
    FlowScreen("About you", "As written on your KTP. $progress.", onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OrvynTextField("Full name", name, { name = it }, error = if (tried && name.trim().length < 3) "Enter your full name." else null)
            OrvynTextField("NIK", nik, { nik = it.filter(Char::isDigit).take(16) }, keyboardType = KeyboardType.Number, error = errNik)
            OrvynTextField("Date of birth", dob, { dob = dobInput(dob, it) }, keyboardType = KeyboardType.Number, helper = "DD/MM/YYYY", error = errDob, visualTransformation = DobTransformation)
            OrvynTextField("Address", address, { address = it }, error = if (tried && address.isBlank()) "Enter your address." else null)
            Choices("Work", WORK, work) { work = it }
            Choices("Monthly income", INCOME, income) { income = it }
            Choices("Main reason for the account", PURPOSE, purpose) { purpose = it }
            if (tried && (work.isEmpty() || income.isEmpty() || purpose.isEmpty())) Text("Pick one answer for each question.", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.heightIn(min = 8.dp))
        }
        OrvynButton("Continue", modifier = Modifier.padding(vertical = 16.dp), onClick = {
            tried = true
            if (name.trim().length >= 3 && nik.length == 16 && date != null && age!! >= 17 && address.isNotBlank() && work.isNotEmpty() && income.isNotEmpty() && purpose.isNotEmpty()) {
                d.put("fullName", name.trim()).put("nik", nik).put("birthDate", date.toString()).put("address", address.trim())
                    .put("occupation", work).put("incomeRange", income).put("purpose", purpose)
                onNext()
            }
        })
    }
}

/** Story S1.2: the photo stays in memory on this screen only, is never uploaded, and is dropped when you move on. */
@Composable
private fun PhotoStep(title: String, body: String, back: Boolean, card: Boolean, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val lifecycle = LocalLifecycleOwner.current

    FlowScreen(title, body, if (back) onBack else null) {
        if (!granted) {
            Spacer(Modifier.weight(1f))
            MessageState(R.drawable.ph_user, "ORVYN needs the camera for this step",
                if (asked) "The camera is off for ORVYN. You can skip this step in the demo." else "The picture stays on your phone. It's never sent anywhere, and it's deleted as soon as you move on.")
            Spacer(Modifier.weight(1f))
            if (!asked) OrvynButton("Allow camera", onClick = { permission.launch(Manifest.permission.CAMERA) })
            OrvynTextButton("Skip this step", onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
            return@FlowScreen
        }
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
            val shot = photo
            if (shot != null) Image(shot.asImageBitmap(), contentDescription = "Your photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { view ->
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycle, if (card) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA, preview, capture)
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            // Guide: a KTP-shaped frame or a face oval.
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                drawRect(Color.Black.copy(alpha = if (shot != null) 0.2f else 0.45f))
                if (card) {
                    val w = size.width * 0.86f; val h = w / 1.586f
                    val tl = Offset((size.width - w) / 2, (size.height - h) / 2)
                    drawRoundRect(Color.Transparent, tl, Size(w, h), CornerRadius(16.dp.toPx()), blendMode = BlendMode.Clear)
                    drawRoundRect(Color.White, tl, Size(w, h), CornerRadius(16.dp.toPx()), style = Stroke(3.dp.toPx()))
                } else {
                    val w = size.width * 0.62f; val h = w * 1.3f
                    val tl = Offset((size.width - w) / 2, (size.height - h) / 2)
                    drawOval(Color.Transparent, tl, Size(w, h), blendMode = BlendMode.Clear)
                    drawOval(Color.White, tl, Size(w, h), style = Stroke(3.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.heightIn(min = 16.dp))
        if (photo == null) OrvynButton("Take photo", modifier = Modifier.padding(bottom = 16.dp), onClick = {
            capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotation = image.imageInfo.rotationDegrees
                    val b = image.toBitmap()
                    image.close()
                    photo = if (rotation == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                }
                override fun onError(e: ImageCaptureException) {}
            })
        }) else {
            OrvynButton("Use this photo", onClick = { photo = null; onDone() }) // dropped here: never stored or sent
            OrvynButton("Retake", kind = ButtonKind.Secondary, onClick = { photo = null }, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun PasswordStep(progress: String, onBack: () -> Unit, onNext: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    val strength = listOf(pw.length >= 8, pw.any(Char::isLetter), pw.any(Char::isDigit)).count { it }
    FlowScreen("Create a password", "You'll use it once per phone, with your phone number. $progress.", onBack) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OrvynTextField("Password", pw, { pw = it }, password = true, helper = when (strength) { 3 -> "Strong enough."; else -> "At least 8 characters, with letters and numbers." })
        }
        OrvynButton("Continue", enabled = strength == 3, onClick = { onNext(pw) }, modifier = Modifier.padding(vertical = 16.dp))
    }
}

@Composable
private fun PinStep(title: String, body: String, error: String?, errorCount: Int = 0, onPin: (String) -> Unit) {
    FlowScreen(title, body, null) {
        Spacer(Modifier.weight(1f))
        PinPad(onComplete = onPin, errorCount = errorCount, errorText = error, onBiometric = null, modifier = Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun WaitingStep(token: String, onDone: () -> Unit, onRestart: () -> Unit) {
    var status by remember { mutableStateOf("pending") }
    var reason by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (status == "pending") {
            runCatching { Api.registerStatus(token) }.onSuccess { status = it.first; reason = it.second }
            delay(10_000)
        }
    }
    FlowScreen(
        when (status) { "approved" -> "Your account is ready"; "rejected" -> "We couldn't open your account"; else -> "We're checking your details" },
        when (status) {
            "approved" -> "Sign in with your phone number and the password you just made."
            "rejected" -> reason ?: "Please start again."
            else -> "This usually takes a few minutes. You can close ORVYN; your application is saved."
        }, null,
    ) {
        Spacer(Modifier.weight(1f))
        MessageState(if (status == "rejected") R.drawable.ph_warning_circle else if (status == "approved") R.drawable.ph_check_circle else R.drawable.ph_clock_counter_clockwise,
            when (status) { "approved" -> "Welcome to ORVYN"; "rejected" -> "Application not approved"; else -> "Waiting for the ORVYN team" },
            when (status) { "approved" -> "Your account and debit card are open."; "rejected" -> "You can apply again with the right details."; else -> "We'll update this screen as soon as there's news." })
        Spacer(Modifier.weight(1f))
        when (status) {
            "approved" -> OrvynButton("Sign in", onClick = onDone, modifier = Modifier.padding(vertical = 16.dp))
            "rejected" -> OrvynButton("Start again", onClick = onRestart, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}
