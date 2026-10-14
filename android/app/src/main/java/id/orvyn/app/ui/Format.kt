package id.orvyn.app.ui

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
