package id.orvyn.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/** Rupiah has no decimals in this app; amounts are whole rupiah (design-system/MASTER.md §6). */
object Money {
    private val grouping = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))
    const val MAX_DIGITS = 13 // Rp9,999,999,999,999 is far above any limit

    fun format(rupiah: Long): String = "Rp" + grouping.format(abs(rupiah))

    /** "+Rp500" for money in, "−Rp500" (true minus sign) for money out. */
    fun signed(rupiah: Long): String = (if (rupiah < 0) "−" else "+") + format(rupiah)
}

/** "1234567890" → "1234 5678 90" */
fun groupAccountNumber(number: String) = number.chunked(4).joinToString(" ")

sealed interface AmountKey {
    data class Digit(val d: Int) : AmountKey
    data object TripleZero : AmountKey
    data object Delete : AmountKey
}

/** What the amount keypad does to the current amount. Ignores keys that would overflow. */
fun pressAmountKey(current: Long, key: AmountKey): Long {
    val next = when (key) {
        is AmountKey.Digit -> current.toString().let { if (current == 0L) "${key.d}" else it + key.d }
        AmountKey.TripleZero -> if (current == 0L) "0" else "${current}000"
        AmountKey.Delete -> current.toString().dropLast(1).ifEmpty { "0" }
    }
    return if (next.length > Money.MAX_DIGITS) current else next.toLong()
}

private val WIB = java.time.ZoneId.of("Asia/Jakarta")
private val DATE_TIME = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'WIB'", Locale.US) // US, not UK: CLDR spells September "Sept" in UK English
private val TIME = java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.US) // US, not UK: CLDR spells September "Sept" in UK English
private val DAY = java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US) // US, not UK: CLDR spells September "Sept" in UK English

/** "14 Oct 2026, 19:27 WIB" */
fun formatDateTime(at: java.time.Instant): String = DATE_TIME.format(at.atZone(WIB))

/** "19:27" */
fun formatTime(at: java.time.Instant): String = TIME.format(at.atZone(WIB))

/** Group header for a day in Western Indonesia Time: "Today", "Yesterday" or "Mon, 12 Oct". */
fun dayLabel(at: java.time.Instant, now: java.time.Instant = java.time.Instant.now()): String {
    val d = at.atZone(WIB).toLocalDate()
    val today = now.atZone(WIB).toLocalDate()
    return when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DAY.format(d)
    }
}

/** Greeting by the hour in WIB. */
fun greeting(now: java.time.Instant = java.time.Instant.now()) = when (now.atZone(WIB).hour) {
    in 4..10 -> "Good morning"
    in 11..16 -> "Good afternoon"
    else -> "Good evening"
}

/** Quick Amounts (design.md 2.1): every screen offers only amounts its product rule accepts. */
val QUICK_AMOUNTS = listOf(50_000L, 100_000L, 250_000L, 500_000L)
val DEPOSIT_QUICK_AMOUNTS = listOf(1_000_000L, 5_000_000L, 10_000_000L, 50_000_000L)
fun investQuickAmounts(kind: String) = when (kind) {
    "fund" -> listOf(100_000L, 250_000L, 500_000L, 1_000_000L)
    "bond" -> listOf(1_000_000L, 2_000_000L, 5_000_000L, 10_000_000L)
    else -> QUICK_AMOUNTS
}

/** 50000 → "50k", 1000000 → "1M". Quick Amounts are whole thousands or whole millions. */
fun quickAmountLabel(rupiah: Long) = if (rupiah >= 1_000_000) "${rupiah / 1_000_000}M" else "${rupiah / 1000}k"

/** Date of birth field value: digits only, at most 8. A 9th digit is ignored; a long paste keeps the first 8. */
fun dobInput(old: String, new: String): String {
    val digits = new.filter(Char::isDigit)
    return if (digits.length <= 8) digits else if (old.length == 8) old else digits.take(8)
}

fun dobDigits(date: java.time.LocalDate) = "%02d%02d%04d".format(date.dayOfMonth, date.monthValue, date.year)

/** "17081990" → 17 Aug 1990; anything but 8 digits of a real day → null. */
fun parseDobDigits(d: String) = if (d.length != 8) null
    else runCatching { java.time.LocalDate.of(d.substring(4).toInt(), d.substring(2, 4).toInt(), d.take(2).toInt()) }.getOrNull()

/** Shows "17081990" as "17/08/1990"; the slashes are display only, so delete and the cursor work per digit. */
val DobTransformation = VisualTransformation { text ->
    val d = text.text
    val n = d.length
    val shown = buildString {
        append(d.take(2)); if (n >= 2) append('/')
        append(d.drop(2).take(2)); if (n >= 4) append('/')
        append(d.drop(4))
    }
    TransformedText(AnnotatedString(shown), object : OffsetMapping {
        override fun originalToTransformed(offset: Int) =
            offset + (if (offset >= 2 && n >= 2) 1 else 0) + (if (offset >= 4 && n >= 4) 1 else 0)
        override fun transformedToOriginal(offset: Int) =
            (offset - (if (offset > 2 && n >= 2) 1 else 0) - (if (offset > 5 && n >= 4) 1 else 0)).coerceIn(0, n)
    })
}
