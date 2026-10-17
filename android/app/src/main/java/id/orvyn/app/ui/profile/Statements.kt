package id.orvyn.app.ui.profile

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.ApiException
import id.orvyn.app.data.Statement
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.auth.FlowScreen
import id.orvyn.app.ui.components.*
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import id.orvyn.app.ui.components.MessageState
import id.orvyn.app.ui.components.Skeleton
import id.orvyn.app.ui.groupAccountNumber
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Story S2.17: monthly e-statements, drawn as a PDF on the phone and handed to Android's share sheet.

private fun monthTitle(key: String) = YearMonth.parse(key).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))

/** Story S2.17 and Q-B10: one PDF failing leaves the list alone and offers Try again in a snackbar. */
@Composable
fun StatementsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val months = rememberLoad { Api.statementMonths() }
    var making by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snacks = LocalSnacks.current
    fun make(key: String) {
        if (making != null) return
        making = key
        scope.launch {
            try { sharePdf(context, Api.statement(key)) }
            catch (e: ApiException) { snacks?.show("Couldn't make your ${monthTitle(key)} statement.", error = true, action = "Try again") { make(key) } }
            making = null
        }
    }
    FlowScreen("E-statements", "Your ORVYN Everyday account, month by month, as a PDF.", onBack) {
        LoadContent(months, "Couldn't load your statements", skeleton = { Skeleton(Modifier.fillMaxWidth().height(160.dp)) }) { m ->
            if (m.isEmpty()) MessageState(R.drawable.ph_file_pdf, "No statements yet", "Your first statement is ready after your first full month with ORVYN.")
            else LazyColumn {
                items(m) { key ->
                    ListRow(monthTitle(key), if (making == key) "Making your PDF…" else "PDF", R.drawable.ph_file_pdf, onClick = { make(key) }) {
                        if (making == key) CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = "Making your ${monthTitle(key)} statement" }, strokeWidth = 2.dp)
                        else Icon(painterResource(R.drawable.ph_share_network), contentDescription = "Share ${monthTitle(key)} statement", tint = OrvynTheme.colors.textSecondary)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** A4 pages: brand header, account details, summary, then every transaction with its running balance. */
private suspend fun sharePdf(context: Context, s: Statement) {
    val file = withContext(Dispatchers.Default) {
        val W = 595f; val H = 842f; val M = 40f
        val base = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) ?: Typeface.DEFAULT
        fun face(weight: Int) = if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false) else if (weight >= 600) Typeface.create(base, Typeface.BOLD) else base
        fun paint(size: Float, color: Long = 0xFF0B1020, weight: Int = 400, right: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color.toInt(); typeface = face(weight); if (right) textAlign = Paint.Align.RIGHT
        }
        val muted = 0xFF4D5872; val line = Paint().apply { color = 0xFFDDE2EE.toInt(); strokeWidth = 0.6f }
        val dateFmt = DateTimeFormatter.ofPattern("d MMM", Locale.US).withZone(ZoneId.of("Asia/Jakarta"))
        val cols = floatArrayOf(M, M + 52, W - M - 190, W - M - 105, W - M) // date, description, in, out, balance (right edges for numbers)
        val rowH = 26f
        val doc = PdfDocument()
        val firstPageRows = ((H - 330 - 60) / rowH).toInt(); val otherRows = ((H - 120 - 60) / rowH).toInt()
        val pages = if (s.lines.size <= firstPageRows) 1 else 1 + (s.lines.size - firstPageRows + otherRows - 1) / otherRows
        var index = 0
        for (pageNo in 1..pages) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create())
            val c: Canvas = page.canvas
            // Header band with the jersey stripes, like the app's home screen.
            c.drawRect(0f, 0f, W, 70f, Paint().apply { shader = LinearGradient(0f, 0f, W, 70f, intArrayOf(0xFF0032A0.toInt(), 0xFF0A1650.toInt(), 0xFF0A0B10.toInt()), null, Shader.TileMode.CLAMP) })
            var x = 0f; while (x < W) { c.drawRect(x, 0f, x + 18f, 70f, Paint().apply { color = 0x0DFFFFFF }); x += 36f }
            c.drawText("ORVYN", M, 44f, paint(20f, 0xFFFFFFFF, 800).apply { letterSpacing = 0.14f })
            c.drawText("Account statement · ${monthTitle(s.month)}", W - M, 44f, paint(11f, 0xFFC9D4F5, 600, right = true))
            var y: Float
            if (pageNo == 1) {
                c.drawText(s.name, M, 110f, paint(16f, weight = 700))
                c.drawText("${s.accountName} · ${groupAccountNumber(s.accountNumber)}", M, 130f, paint(11f, muted))
                c.drawText("Period: 1 to ${YearMonth.parse(s.month).lengthOfMonth()} ${monthTitle(s.month)} (WIB)", M, 147f, paint(11f, muted))
                c.drawRoundRect(RectF(M, 170f, W - M, 270f), 10f, 10f, Paint().apply { color = 0xFFF4F6FB.toInt() })
                val boxW = (W - 2 * M) / 4
                listOf("Opening balance" to s.opening, "Money in" to s.moneyIn, "Money out" to s.moneyOut, "Closing balance" to s.closing).forEachIndexed { i, (label, v) ->
                    c.drawText(label, M + 14 + i * boxW, 200f, paint(9.5f, muted))
                    c.drawText(Money.format(v), M + 14 + i * boxW, 228f, paint(13f, weight = if (i == 3) 800 else 600))
                }
                c.drawText("${s.lines.size} transactions", M + 14, 255f, paint(9.5f, muted))
                y = 310f
            } else y = 100f
            // Table header
            c.drawText("Date", cols[0], y, paint(9f, muted, 600)); c.drawText("Description", cols[1], y, paint(9f, muted, 600))
            c.drawText("Money in", cols[2], y, paint(9f, muted, 600, true)); c.drawText("Money out", cols[3], y, paint(9f, muted, 600, true))
            c.drawText("Balance", cols[4], y, paint(9f, muted, 600, true))
            c.drawLine(M, y + 8, W - M, y + 8, line)
            y += 8 + rowH
            val rows = if (pageNo == 1) firstPageRows else otherRows
            repeat(rows) {
                val l = s.lines.getOrNull(index++) ?: return@repeat
                c.drawText(dateFmt.format(l.at), cols[0], y - 8, paint(9.5f))
                val desc = l.description.let { if (it.length > 34) it.take(33) + "…" else it }
                c.drawText(desc, cols[1], y - 12, paint(9.5f, weight = 500))
                l.channel?.let { c.drawText(it.take(40), cols[1], y - 1, paint(7.5f, muted)) }
                if (l.amount > 0) c.drawText(Money.format(l.amount), cols[2], y - 8, paint(9.5f, 0xFF1B7F4B, 500, true))
                else c.drawText(Money.format(l.amount), cols[3], y - 8, paint(9.5f, right = true))
                c.drawText(Money.format(l.balance), cols[4], y - 8, paint(9.5f, weight = 600, right = true))
                c.drawLine(M, y + 4, W - M, y + 4, line)
                y += rowH
            }
            // Footer on every page (S2.17 AC2).
            c.drawText("Demo, not a real bank statement. ORVYN Bank is a fictional bank and all money in it is pretend.", M, H - 30f, paint(8f, muted))
            c.drawText("Page $pageNo of $pages", W - M, H - 30f, paint(8f, muted, right = true))
            doc.finishPage(page)
        }
        val dir = File(context.cacheDir, "statements").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        File(dir, "ORVYN-statement-${s.month}.pdf").also { f -> f.outputStream().use { doc.writeTo(it) }; doc.close() }
    }
    val uri = FileProvider.getUriForFile(context, "id.orvyn.app.receipts", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share statement"))
}
