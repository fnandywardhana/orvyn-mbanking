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
    data object Scan : QStep
    data class Pay(val merchant: Merchant, val fixed: Long?) : QStep
    data class Pin(val merchant: Merchant, val amount: Long) : QStep
    data class Done(val merchant: Merchant, val amount: Long, val reference: String) : QStep
    data object Mine : QStep
}

/** Stories S1.15 and S1.16. A friend's My QR hands over to the transfer flow via [onSendTo]. */
@Composable
fun QrisFlow(onExit: () -> Unit, onSendTo: (Recipient, Long?) -> Unit) {
    var step by remember { mutableStateOf<QStep>(QStep.Scan) }
    val key = remember { UUID.randomUUID().toString() }
    BackHandler(step !is QStep.Done) {
        step = when (val s = step) {
            QStep.Scan -> { onExit(); QStep.Scan }
            is QStep.Pin -> QStep.Pay(s.merchant, null)
            else -> QStep.Scan
        }
    }
    when (val s = step) {
        QStep.Scan -> Scanner(onExit, onMine = { step = QStep.Mine }) { scan ->
            when (scan) {
                is Scan.Pay -> step = QStep.Pay(scan.merchant, scan.amount)
                is Scan.Send -> onSendTo(scan.recipient, scan.amount)
            }
        }
        is QStep.Pay -> PayMerchant(s.merchant, s.fixed, onBack = { step = QStep.Scan }) { step = QStep.Pin(s.merchant, it) }
        is QStep.Pin -> PayPin(s.merchant, s.amount, key, onBack = { step = QStep.Pay(s.merchant, null) }) { ref ->
            DataVersion.count++
            step = QStep.Done(s.merchant, s.amount, ref)
        }
        is QStep.Done -> SuccessScreen(
            headline = "Paid", amount = Money.format(s.amount), subline = "to ${s.merchant.name}",
            details = { ReceiptLine("Merchant", "${s.merchant.name} · ${s.merchant.city}"); ReceiptLine("Method", "QRIS"); ReceiptLine("Reference", s.reference) },
            primaryLabel = "Done", onPrimary = onExit,
        )
        QStep.Mine -> MyQrScreen(onBack = { step = QStep.Scan })
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
            Spacer(Modifier.weight(1f))
            MessageState(R.drawable.ph_qr_code, if (asked) "Camera is off for ORVYN" else "Point your camera at a QRIS code",
                if (asked) "Turn it on in Settings, or pick a QR picture from your gallery." else "We'll ask Android for camera access first.")
            Spacer(Modifier.weight(1f))
            if (asked) OrvynButton("Open Settings", onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }) else OrvynButton("Allow camera", onClick = { permission.launch(Manifest.permission.CAMERA) })
            OrvynButton("Pick from gallery", onClick = fromGallery, kind = ButtonKind.Secondary, modifier = Modifier.padding(vertical = 8.dp))
            OrvynButton("Show my QR", onClick = onMine, kind = ButtonKind.Secondary, modifier = Modifier.padding(bottom = 16.dp))
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(attempt, ::handle)
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
                IconButton(onClick = onExit) { Icon(painterResource(R.drawable.ph_x), contentDescription = "Close", tint = Color.White) }
                Text("Scan QRIS", style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text(
                when { busy -> "Reading the code…"; error != null -> error!!; else -> "Point your camera at the QRIS code" },
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                color = if (error != null && !busy) Brand.Giallo else Color.White,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                OrvynButton("Gallery", onClick = fromGallery, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
                OrvynButton("My QR", onClick = onMine, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CameraPreview(attempt: Int, onText: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    AndroidView(
        factory = { PreviewView(it).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } },
        update = { view ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    .also { it.setAnalyzer(executor, QrAnalyzer { text -> view.post { onText(text) } }) }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(context))
        },
        modifier = Modifier.fillMaxSize().semantics { contentDescription = "Camera viewfinder (attempt ${attempt + 1})" },
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

@Composable
private fun PayMerchant(m: Merchant, fixed: Long?, onBack: () -> Unit, onPay: (Long) -> Unit) {
    var amount by remember { mutableLongStateOf(fixed ?: 0L) }
    var balance by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { runCatching { balance = Api.transferOptions("ORVYN", 0).balance } }
    val error = balance?.let { if (amount > it) "You're ${Money.format(amount - it)} short." else null }
    FlowScreen(if (fixed != null) "Pay ${m.name}" else "How much?", if (fixed != null) "The merchant set this amount." else null, onBack) {
        MerchantHeader(m)
        Spacer(Modifier.weight(1f))
        if (fixed != null) {
            Text(Money.format(fixed), style = OrvynType.displayBalance, modifier = Modifier.align(Alignment.CenterHorizontally))
            balance?.let { Text("Available ${Money.format(it)}", style = MaterialTheme.typography.bodyMedium, color = error?.let { MaterialTheme.colorScheme.error } ?: OrvynTheme.colors.textSecondary, modifier = Modifier.align(Alignment.CenterHorizontally)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.CenterHorizontally)) }
            Spacer(Modifier.weight(1f))
        } else {
            AmountDisplay(amount, balance, error, onQuickPick = { amount = it })
            Spacer(Modifier.weight(1f))
            AmountKeypad(amount, { amount = it })
        }
        OrvynButton(when { amount == 0L -> "Enter an amount"; amount < 1_000 -> "Minimum Rp1,000"; else -> "Pay ${Money.format(amount)}" },
            enabled = amount >= 1_000 && error == null, onClick = { onPay(amount) }, modifier = Modifier.padding(vertical = 16.dp))
    }
}

@Composable
private fun PayPin(m: Merchant, amount: Long, key: String, onBack: () -> Unit, onPaid: (String) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    FlowScreen("Enter your PIN", "To pay ${Money.format(amount)} to ${m.name}.", onBack) {
        Spacer(Modifier.weight(1f))
        PinPad(
            onComplete = { pin -> scope.launch { try { onPaid(Api.qrisPay(m.id, amount, pin, key).reference) } catch (e: ApiException) { error = e.message; errors++ } } },
            errorCount = errors, errorText = error, onBiometric = null, modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

/** Story S1.16: a QR other ORVYN customers scan to send money here. Screen goes full brightness while shown. */
@Composable
private fun MyQrScreen(onBack: () -> Unit) {
    val activity = LocalContext.current as ComponentActivity
    var qr by remember { mutableStateOf<MyQr?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { try { qr = Api.myQr(null) } catch (e: ApiException) { error = e.message } }
    DisposableEffect(Unit) {
        val w = activity.window
        val before = w.attributes.screenBrightness
        w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { w.attributes = w.attributes.apply { screenBrightness = before } }
    }
    FlowScreen("My QR", "Other ORVYN customers scan this to send you money.", onBack) {
        Spacer(Modifier.weight(1f))
        val q = qr
        when {
            error != null -> MessageState(R.drawable.ph_warning_circle, "Couldn't make your QR", error!!)
            q == null -> Skeleton(Modifier.size(280.dp).align(Alignment.CenterHorizontally))
            else -> Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val bitmap = remember(q.payload) { drawQr(q.payload).asImageBitmap() }
                Image(bitmap, contentDescription = "QR code for ${q.name}", modifier = Modifier.size(260.dp))
                Text(q.name, style = MaterialTheme.typography.titleLarge, color = Brand.Nero)
                Text("ORVYN Bank · ${groupAccountNumber(q.accountNumber)}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4D5872))
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(16.dp))
    }
}
