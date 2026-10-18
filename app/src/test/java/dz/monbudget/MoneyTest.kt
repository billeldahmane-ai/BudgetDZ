package dz.monbudget

import dz.monbudget.util.Money
import org.junit.Assert.*
import org.junit.Test

class MoneyTest {
    @Test fun parse_accepts_comma_and_dot() {
        assertEquals(140000L, Money.parseAmount("1400"))
        assertEquals(125050L, Money.parseAmount("1250,5"))
        assertEquals(125050L, Money.parseAmount("1 250.50"))
    }

    @Test fun parse_rejects_invalid_input_without_crashing() {
        assertNull(Money.parseAmount(""))
        assertNull(Money.parseAmount("abc"))
        assertNull(Money.parseAmount("-5"))
        assertNull(Money.parseAmount("1e5"))
        assertNull(Money.parseAmount("0"))
        assertNull(Money.parseAmount("99999999999"))      // au-dessus du plafond
        assertEquals(0L, Money.parseAmount("0", allowZero = true))
    }

    @Test fun parse_quantity() {
        assertEquals(2500L, Money.parseQuantity("2,5"))
        assertNull(Money.parseQuantity("0"))
        assertNull(Money.parseQuantity("-1"))
    }

    @Test fun format_uses_grouping_and_hides_zero_centimes() {
        assertEquals("1 200 DZD", Money.format(120000))
        assertEquals("1 234,56 DZD", Money.format(123456))
        assertEquals("-50 DZD", Money.format(-5000))
        assertEquals("0 DZD", Money.format(0))
    }
}
