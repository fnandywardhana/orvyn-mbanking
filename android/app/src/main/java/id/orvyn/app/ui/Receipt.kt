package id.orvyn.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import id.orvyn.app.R
import id.orvyn.app.data.Transaction
import java.io.File

/**
 * Story S1.11 AC3–AC4: draws an ORVYN receipt and opens Android's share sheet. The picture lives in the
 * app's cache only; it reaches the gallery or a chat only if the customer picks that in the share sheet.
 */
fun shareReceipt(context: Context, t: Transaction) {
    val w = 1080
    val lines = buildList {
        add("Date" to formatDateTime(t.at))
        t.subtitle?.let { add("Type" to it) }
        t.note?.let { add("Note" to it) }
        if (t.fee > 0) add("Fee" to Money.format(t.fee))
        t.accountNumber?.let { add("Account" to groupAccountNumber(it)) }
        t.reference?.let { add("Reference" to it) }
    }
    val h = 620 + lines.size * 150 + 180
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val base = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) ?: Typeface.DEFAULT
    fun face(weight: Int) = if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false) else if (weight >= 600) Typeface.create(base, Typeface.BOLD) else base
    fun paint(size: Float, color: Int, weight: Int = 400) = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face(weight) }

    c.drawColor(0xFFFFFFFF.toInt())
    // Brand band: the hero gradient with jersey stripes.
    val band = Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), 420f, intArrayOf(0xFF0032A0.toInt(), 0xFF0A1650.toInt(), 0xFF0A0B10.toInt()), null, Shader.TileMode.CLAMP) }
    c.drawRect(0f, 0f, w.toFloat(), 420f, band)
    val stripe = Paint().apply { color = 0x0DFFFFFF }
    var x = 0f; while (x < w) { c.drawRect(x, 0f, x + 58f, 420f, stripe); x += 116f }
    c.drawText("ORVYN", 72f, 120f, paint(52f, 0xFFFFFFFF.toInt(), 800).apply { letterSpacing = 0.14f })
    c.drawText(if (t.reversed) "Reversed" else if (t.amount > 0) "Money in" else "Payment receipt", 72f, 230f, paint(40f, 0xFFC9D4F5.toInt(), 600))
    c.drawText(Money.signed(t.amount), 72f, 330f, paint(88f, 0xFFFFFFFF.toInt(), 800))
    c.drawText(t.title, 72f, 520f, paint(52f, 0xFF0B1020.toInt(), 700))
    var y = 640f
    for ((label, value) in lines) {
        c.drawText(label, 72f, y, paint(36f, 0xFF4D5872.toInt()))
        c.drawText(value, 72f, y + 56f, paint(42f, 0xFF0B1020.toInt(), 500))
        y += 150f
    }
    c.drawRoundRect(RectF(48f, h - 150f, w - 48f, h - 50f), 24f, 24f, Paint().apply { color = 0xFFF4F6FB.toInt() })
    c.drawText("Demo · ORVYN Bank is a fictional bank", 88f, h - 86f, paint(34f, 0xFF4D5872.toInt()))

    val dir = File(context.cacheDir, "receipts").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    val file = File(dir, "orvyn-receipt-${t.reference ?: "transaction"}.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "id.orvyn.app.receipts", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share receipt"))
}
