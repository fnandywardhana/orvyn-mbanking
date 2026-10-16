package id.orvyn.app

import androidx.compose.ui.text.AnnotatedString
import id.orvyn.app.ui.DEPOSIT_QUICK_AMOUNTS
import id.orvyn.app.ui.DobTransformation
import id.orvyn.app.ui.QUICK_AMOUNTS
import id.orvyn.app.ui.dobDigits
import id.orvyn.app.ui.dobInput
import id.orvyn.app.ui.investQuickAmounts
import id.orvyn.app.ui.parseDobDigits
import id.orvyn.app.ui.quickAmountLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Hotfix 0.8.1: date of birth auto-slash (H1, H2) and Quick Amounts per screen (H3, H4, H5). */
class HotfixTest {
    private fun shown(digits: String) = DobTransformation.filter(AnnotatedString(digits)).text.text

    @Test fun `slashes appear right after the 2nd and 4th digit`() {
        assertEquals("", shown(""))
        assertEquals("1", shown("1"))
        assertEquals("17/", shown("17"))
        assertEquals("17/0", shown("170"))
        assertEquals("17/08/", shown("1708"))
        assertEquals("17/08/1990", shown("17081990"))
    }

    @Test fun `input keeps digits only, at most 8, and ignores a 9th digit`() {
        assertEquals("17081990", dobInput("", "17081990"))
        assertEquals("17081990", dobInput("17081990", "170819901"))  // 9th digit at the end
        assertEquals("17081990", dobInput("17081990", "170819190"))  // 9th digit in the middle
        assertEquals("12345678", dobInput("", "1234567890"))         // long paste into an empty field
        assertEquals("1", dobInput("17", "1"))                         // one backspace, one digit
    }

    @Test fun `pasting with slashes, dashes or dots gives the same digits`() {
        for (p in listOf("17/08/1990", "17081990", "17-08-1990", "17.08.1990")) {
            assertEquals("17081990", dobInput("", p))
            assertEquals("17/08/1990", shown(dobInput("", p)))
        }
    }

    @Test fun `cursor mapping round-trips and never leaves the text`() {
        for (n in 0..8) {
            val digits = "17081990".take(n)
            val t = DobTransformation.filter(AnnotatedString(digits))
            for (o in 0..n) {
                val tt = t.offsetMapping.originalToTransformed(o)
                assertTrue(tt in 0..t.text.length)
                assertEquals(o, t.offsetMapping.transformedToOriginal(tt))
            }
            for (tt in 0..t.text.length) assertTrue(t.offsetMapping.transformedToOriginal(tt) in 0..n)
        }
        val m = DobTransformation.filter(AnnotatedString("17081990")).offsetMapping
        assertEquals(3, m.originalToTransformed(2))   // after "17/"
        assertEquals(2, m.transformedToOriginal(2))   // before the slash
        assertEquals(2, m.transformedToOriginal(3))   // right after the slash = before the 3rd digit
        assertEquals(10, m.originalToTransformed(8))
    }

    @Test fun `editing in the middle replaces the month`() {
        // H1 AC4: delete "08" then type "09"
        val afterDelete = dobInput("17081990", "171990")
        assertEquals("17/19/90", shown(afterDelete))
        assertEquals("17/09/1990", shown(dobInput(afterDelete, "17091990")))
    }

    @Test fun `a date is read only from 8 digits that make a real day`() {
        assertEquals(LocalDate.of(1990, 8, 17), parseDobDigits("17081990"))
        assertNull(parseDobDigits("31021990"))
        assertNull(parseDobDigits("1708199"))
        assertNull(parseDobDigits(""))
        assertEquals("17081990", dobDigits(LocalDate.parse("1990-08-17")))
        assertEquals("01010999", dobDigits(LocalDate.of(999, 1, 1)))
    }

    @Test fun `quick amount labels use k below a million and M from a million`() {
        assertEquals("50k", quickAmountLabel(50_000))
        assertEquals("250k", quickAmountLabel(250_000))
        assertEquals("1M", quickAmountLabel(1_000_000))
        assertEquals("10M", quickAmountLabel(10_000_000))
        assertEquals("50M", quickAmountLabel(50_000_000))
    }

    @Test fun `each screen offers only quick amounts its product rule accepts`() {
        assertEquals(listOf(50_000L, 100_000L, 250_000L, 500_000L), QUICK_AMOUNTS)
        assertEquals(listOf(1_000_000L, 5_000_000L, 10_000_000L, 50_000_000L), DEPOSIT_QUICK_AMOUNTS)
        assertEquals(listOf("1M", "5M", "10M", "50M"), DEPOSIT_QUICK_AMOUNTS.map(::quickAmountLabel))
        val fund = investQuickAmounts("fund")
        assertEquals(listOf("100k", "250k", "500k", "1M"), fund.map(::quickAmountLabel))
        assertTrue(fund.all { it >= 100_000 })
        val bond = investQuickAmounts("bond")
        assertEquals(listOf("1M", "2M", "5M", "10M"), bond.map(::quickAmountLabel))
        assertTrue(bond.all { it >= 1_000_000 && it % 1_000_000 == 0L })
        assertEquals(QUICK_AMOUNTS, investQuickAmounts("gold"))
    }
}
