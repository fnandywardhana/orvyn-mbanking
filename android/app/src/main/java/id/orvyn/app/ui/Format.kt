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
