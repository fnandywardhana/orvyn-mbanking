package id.orvyn.app

import id.orvyn.app.ui.AmountKey
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.groupAccountNumber
import id.orvyn.app.ui.pressAmountKey
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun `rupiah is grouped with commas and has no decimals`() {
        assertEquals("Rp0", Money.format(0))
        assertEquals("Rp1,250,000", Money.format(1_250_000))
        assertEquals("+Rp500", Money.signed(500))
        assertEquals("−Rp500", Money.signed(-500))
    }

    @Test fun `account numbers are shown in groups of four`() {
        assertEquals("1234 5678 90", groupAccountNumber("1234567890"))
    }

    @Test fun `the amount keypad builds numbers and never overflows`() {
        var a = 0L
        for (k in listOf(AmountKey.Digit(0), AmountKey.Digit(5), AmountKey.TripleZero, AmountKey.Digit(1))) a = pressAmountKey(a, k)
        assertEquals(50_001L, a)
        assertEquals(5_000L, pressAmountKey(50_001, AmountKey.Delete))
        assertEquals(0L, pressAmountKey(5, AmountKey.Delete))
        assertEquals(0L, pressAmountKey(0, AmountKey.TripleZero))
        val max = 9_999_999_999_999L
        assertEquals(max, pressAmountKey(max, AmountKey.Digit(9)))
        assertEquals(max, pressAmountKey(max, AmountKey.TripleZero))
    }
}
