package id.orvyn.app.ui.qris

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.concurrent.atomic.AtomicBoolean

// Reading and drawing QR codes with ZXing, entirely on the phone.

private val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)

/** Camera frames → first QR text found. Reports once, then ignores further frames. */
class QrAnalyzer(private val onFound: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply { setHints(hints) }
    private val done = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        try {
            if (done.get()) return
            val plane = image.planes[0] // luminance is all a QR reader needs
            val bytes = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
            val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
            val text = runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()
            if (text != null && done.compareAndSet(false, true)) onFound(text)
        } finally {
            reader.reset()
            image.close()
        }
    }
}

/** QR text in a picture the customer picked, or null. */
fun decodeQr(bitmap: Bitmap): String? {
    val px = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val source = RGBLuminanceSource(bitmap.width, bitmap.height, px)
    return runCatching { MultiFormatReader().apply { setHints(hints) }.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()
}

/** Black-on-white QR bitmap for "My QR". */
fun drawQr(text: String, size: Int = 720): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
    val px = IntArray(size * size) { if (m.get(it % size, it / size)) 0xFF0A0B10.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}
