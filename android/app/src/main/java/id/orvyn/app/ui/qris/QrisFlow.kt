package id.orvyn.app.ui.qris

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.DataVersion
import id.orvyn.app.data.Merchant
import id.orvyn.app.data.MyQr
import id.orvyn.app.data.Recipient
import id.orvyn.app.data.Scan
import id.orvyn.app.data.Paid
import id.orvyn.app.ui.components.*
import id.orvyn.app.ui.shareTransaction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.components.AmountDisplay
import id.orvyn.app.ui.components.AmountKeypad
import id.orvyn.app.ui.components.ButtonKind
import id.orvyn.app.ui.components.IconTile
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.PinPad
import id.orvyn.app.ui.components.ReceiptLine
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.components.SuccessScreen
import id.orvyn.app.ui.groupAccountNumber
import id.orvyn.app.ui.theme.Brand
import id.orvyn.app.ui.theme.OrvynTheme
import id.orvyn.app.ui.theme.OrvynType
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.Executors

private sealed interface QStep {
    val index: Int
    data object Scan : QStep { override val index = 0 }
    data class Pay(val merchant: Merchant, val fixed: Long?, val error: String? = null) : QStep { override val index = 1 }
    data class Pin(val merchant: Merchant, val fixed: Long?, val amount: Long) : QStep { override val index = 2 }
    data class Done(val merchant: Merchant, val amount: Long, val paid: Paid) : QStep { override val index = 3 }
    data object Mine : QStep { override val index = 1 }
    data object MineAmount : QStep { override val index = 2 }
}

/** Stories S1.15 and S1.16. A friend's My QR hands over to the transfer flow via [onSendTo]. */
@Composable
fun QrisFlow(onExit: () -> Unit, onSendTo: (Recipient, Long?) -> Unit) {
    var step by remember { mutableStateOf<QStep>(QStep.Scan) }
    var myAmount by remember { mutableStateOf<Long?>(null) }
    val key = remember { UUID.randomUUID().toString() }
    val context = LocalContext.current
    BackHandler(step !is QStep.Done) {
        step = when (val s = step) {
            QStep.Scan -> { onExit(); QStep.Scan }
            is QStep.Pin -> QStep.Pay(s.merchant, s.fixed)
            QStep.MineAmount -> QStep.Mine
            else -> QStep.Scan
        }
    }
    FlowSteps(step, index = { it.index }, success = { it is QStep.Done }) { s ->
        when (s) {
            QStep.Scan -> Scanner(onExit, onMine = { step = QStep.Mine }) { scan ->
                when (scan) {
                    is Scan.Pay -> step = QStep.Pay(scan.merchant, scan.amount)
                    is Scan.Send -> onSendTo(scan.recipient, scan.amount)
                }
            }
            is QStep.Pay -> PayMerchant(s.merchant, s.fixed, s.error, onBack = { step = QStep.Scan }) { step = QStep.Pin(s.merchant, s.fixed, it) }
            is QStep.Pin -> PayPin(s.merchant, s.amount, key, onBack = { step = QStep.Pay(s.merchant, s.fixed) },
                onRefused = { step = QStep.Pay(s.merchant, s.fixed ?: s.amount, it) }) { paid ->
                DataVersion.count++
                step = QStep.Done(s.merchant, s.amount, paid)
            }
            is QStep.Done -> SuccessScreen(
                headline = "Paid", amount = Money.format(s.amount), subline = "to ${s.merchant.name}", date = s.paid.at,
                details = { ReceiptLine("Merchant", "${s.merchant.name} · ${s.merchant.city}"); ReceiptLine("Method", "QRIS"); ReceiptLine("Reference", s.paid.reference) },
                onPrimary = onExit, onShare = s.paid.transactionId?.let { id -> { shareTransaction(context, id) } },
            )
            QStep.Mine -> MyQrScreen(myAmount, onBack = { step = QStep.Scan }, onAddAmount = { step = QStep.MineAmount }, onRemove = { myAmount = null })
            QStep.MineAmount -> MyQrAmount(myAmount ?: 0L, onBack = { step = QStep.Mine }) { myAmount = it; step = QStep.Mine }
        }
    }
}

@Composable
private fun Scanner(onExit: () -> Unit, onMine: () -> Unit, onScanned: (Scan) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) } // restarts the analyzer after a bad code
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }

    fun handle(text: String) {
        if (busy) return
        busy = true
        scope.launch {
            try { onScanned(Api.qrisScan(text)) } catch (e: ApiException) { error = e.message; attempt++ } finally { busy = false }
        }
    }
    // Photo Picker: the customer chooses one image; ORVYN needs no storage permission and sees nothing else.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        val text = bitmap?.let(::decodeQr)
        if (text == null) error = "We couldn't find a QR code in that picture." else handle(text)
    }
    val fromGallery = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    if (!granted) {
        FlowScreen("Scan to pay", "ORVYN uses your camera only to read QR codes. Nothing is recorded or sent anywhere.", onExit) {
            FitArea(bottom = false) {
                Spacer(Modifier.height(0.dp))
                MessageState(R.drawable.ph_qr_code, if (asked) "Camera is off for ORVYN" else "Point your camera at a QRIS code",
                    if (asked) "Turn it on in Settings, or pick a QR picture from your gallery." else "We'll ask Android for camera access first.")
                Spacer(Modifier.height(0.dp))
            }
            if (asked) OrvynButton("Open Settings", onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }) else OrvynButton("Allow camera", onClick = { permission.launch(Manifest.permission.CAMERA) })
            OrvynButton("Pick from gallery", onClick = fromGallery, kind = ButtonKind.Secondary, modifier = Modifier.padding(vertical = 8.dp))
            OrvynButton("Show my QR", onClick = onMine, kind = ButtonKind.Secondary, modifier = Modifier.padding(bottom = 16.dp))
        }
        return
    }
    // Q-G2: the flashlight goes off when the scanner closes.
    DisposableEffect(Unit) { onDispose { runCatching { camera?.cameraControl?.enableTorch(false) } } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(attempt, { camera = it }, ::handle)
        // Dim everything except the square viewfinder.
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val side = size.width * 0.68f
            val tl = Offset((size.width - side) / 2, size.height * 0.42f - side / 2)
            drawRect(Color.Black.copy(alpha = 0.55f))
            drawRoundRect(Color.Transparent, tl, Size(side, side), CornerRadius(28.dp.toPx()), blendMode = BlendMode.Clear)
            drawRoundRect(Color.White, tl, Size(side, side), CornerRadius(28.dp.toPx()), style = Stroke(3.dp.toPx()))
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) { CircleIcon(R.drawable.ph_x, "Close") }
                Text("Scan QRIS", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                if (camera?.cameraInfo?.hasFlashUnit() == true) IconButton(onClick = {
                    torch = !torch; camera?.cameraControl?.enableTorch(torch)
                }) { CircleIcon(if (torch) R.drawable.ph_flashlight_fill else R.drawable.ph_flashlight, if (torch) "Turn off the flashlight" else "Turn on the flashlight") }
            }
            Spacer(Modifier.weight(1f))
            val message = when { busy -> "Reading the code…"; error != null -> error; else -> null }
            if (error != null && !busy) Row(
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp).clip(RoundedCornerShape(50)).background(Color.White)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painterResource(R.drawable.ph_warning_circle), null, Modifier.size(20.dp), tint = Color(0xFFC62828))
                Text(error!!, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF0B1020))
            } else Text(
                message ?: "Point your camera at the QRIS code",
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = Color.White,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                OrvynButton("Gallery", onClick = fromGallery, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
                OrvynButton("My QR", onClick = onMine, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** White icon on a 40% Nero circle, readable over any camera picture (design.md 11.2). */
@Composable
private fun CircleIcon(icon: Int, label: String) = Box(Modifier.size(40.dp).clip(CircleShape).background(Brand.Nero.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
    Icon(painterResource(icon), contentDescription = label, tint = Color.White)
}

@Composable
private fun CameraPreview(attempt: Int, onCamera: (androidx.camera.core.Camera) -> Unit, onText: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    AndroidView(
        factory = { PreviewView(it).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } },
        update = { view ->
            attempt.let { }
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    .also { it.setAnalyzer(executor, QrAnalyzer { text -> view.post { onText(text) } }) }
                provider.unbindAll()
                onCamera(provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
            }, ContextCompat.getMainExecutor(context))
        },
        modifier = Modifier.fillMaxSize().semantics { contentDescription = "Camera. Point it at a QRIS code." },
    )
}

@Composable
private fun MerchantHeader(m: Merchant) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(R.drawable.ph_qr_code)
        Column {
            Text(m.name, style = MaterialTheme.typography.titleMedium)
            Text("${m.city} · QRIS", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
        }
    }
}

/** The QRIS amount, and the screen a failed payment comes back to with its message ([refused]). */
@Composable
private fun PayMerchant(m: Merchant, fixed: Long?, refused: String?, onBack: () -> Unit, onPay: (Long) -> Unit) {
    var amount by remember { mutableLongStateOf(fixed ?: 0L) }
    val balance = rememberLoad { Api.transferOptions("ORVYN", 0).balance }
    val b = balance.value
    val error = b?.let { if (amount > it) "You're ${Money.format(amount - it)} short." else null }
    val failed = balance.error?.let { if (it == OFFLINE) it else "Couldn't load your balance and daily limit." }
    val button: @Composable () -> Unit = {
        if (refused != null) ErrorBox(refused, Modifier.padding(bottom = 12.dp))
        OrvynButton(when { amount == 0L -> "Enter an amount"; amount < 1_000 -> "Minimum Rp1,000"; else -> "Pay ${Money.format(amount)}" },
            enabled = amount >= 1_000 && error == null, onClick = { onPay(amount) })
    }
    if (fixed != null) FlowScreen("Pay ${m.name}", "The merchant set this amount.", onBack) {
        FitArea(bottom = false) {
            MerchantHeader(m)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountText(Money.format(fixed), OrvynType.displayBalance, textAlign = TextAlign.Center)
                when {
                    error != null -> Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    b != null -> Text("Available ${Money.format(b)}", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
                    failed != null -> InlineLoadError(failed, balance.busy, balance::reload)
                }
            }
            Spacer(Modifier.height(0.dp))
        }
        Column(Modifier.padding(vertical = 16.dp)) { button() }
    } else AmountScreen("How much?", null, onBack, keypad = { AmountKeypad(amount, { amount = it }) }, button = { Column { button() } }) {
        MerchantHeader(m)
        AmountDisplay(amount, b, error, onQuickPick = { amount = it }, loading = b == null && balance.busy,
            loadError = if (b == null) failed else null, retrying = balance.busy, onRetry = balance::reload)
    }
}

@Composable
private fun PayPin(m: Merchant, amount: Long, key: String, onBack: () -> Unit, onRefused: (String) -> Unit, onPaid: (Paid) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    PinScreen("Enter your PIN", "To pay ${Money.format(amount)} to ${m.name}.", onBack, errorCount = errors, errorText = error,
        busyText = if (busy) "Paying ${Money.format(amount)}…" else null,
        onComplete = { pin ->
            busy = true; error = null
            scope.launch {
                try { onPaid(Api.qrisPay(m.id, amount, pin, key)) }
                catch (e: ApiException) {
                    when (e.code) { "WRONG_PIN" -> { error = e.message; errors++ }; "LOCKED", "signed_out" -> {}; else -> onRefused(e.message) }
                } finally { busy = false }
            }
        })
}

/** Story S1.16: a QR other ORVYN customers scan to send money here, with an optional amount (Q-F4). Full brightness while shown. */
@Composable
private fun MyQrScreen(amount: Long?, onBack: () -> Unit, onAddAmount: () -> Unit, onRemove: () -> Unit) {
    val activity = LocalContext.current as ComponentActivity
    val qr = rememberLoad(amount) { Api.myQr(amount) }
    DisposableEffect(Unit) {
        val w = activity.window
        val before = w.attributes.screenBrightness
        w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { w.attributes = w.attributes.apply { screenBrightness = before } }
    }
    FlowScreen("My QR", "Other ORVYN customers scan this to send you money.", onBack) {
        FitArea(bottom = false) {
            Spacer(Modifier.height(0.dp))
            val q = qr.value?.takeIf { !qr.busy }
            when {
                q != null -> Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val bitmap = remember(q.payload) { drawQr(q.payload).asImageBitmap() }
                    Image(bitmap, contentDescription = "QR code for ${q.name}" + (amount?.let { ", ${Money.format(it)}" } ?: ""), modifier = Modifier.size(260.dp))
                    Text(q.name, style = MaterialTheme.typography.titleLarge, color = Brand.Nero, textAlign = TextAlign.Center)
                    Text("ORVYN Bank · ${groupAccountNumber(q.accountNumber)}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4D5872), textAlign = TextAlign.Center)
                }
                qr.error != null && !qr.busy -> LoadError("Couldn't make your QR", qr.error!!, qr.busy, qr::reload)
                else -> Skeleton(Modifier.size(280.dp).align(Alignment.CenterHorizontally), shape = RoundedCornerShape(20.dp))
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (amount == null) OrvynButton("Add an amount", onClick = onAddAmount, kind = ButtonKind.Secondary)
                else {
                    Text(Money.format(amount), style = MaterialTheme.typography.titleLarge)
                    val buttons: @Composable () -> Unit = { OrvynTextButton("Change amount", onClick = onAddAmount); OrvynTextButton("Remove amount", onClick = onRemove) }
                    if (isLargeFont()) Column(horizontalAlignment = Alignment.CenterHorizontally) { buttons() } else Row { buttons() }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Q-F4 (PO-25): at least Rp10,000, because whoever scans it makes an ORVYN transfer. */
@Composable
private fun MyQrAmount(initial: Long, onBack: () -> Unit, onDone: (Long) -> Unit) {
    var amount by remember { mutableLongStateOf(initial) }
    AmountScreen("Add an amount", "Whoever scans this QR pays this amount.", onBack, keypad = { AmountKeypad(amount, { amount = it }) }, button = {
        OrvynButton(when { amount == 0L -> "Enter an amount"; amount < 10_000 -> "Minimum Rp10,000"; else -> "Show QR for ${Money.format(amount)}" },
            enabled = amount >= 10_000, onClick = { onDone(amount) })
    }) { AmountDisplay(amount, null, null, onQuickPick = { amount = it }) }
}
