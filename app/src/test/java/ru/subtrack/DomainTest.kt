package ru.subtrack
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class DomainTest {
    @Test fun parsesCommaAndRejectsFractionsOfKopeck() { assertEquals(29990L,Money.minor("299,90"));assertNull(Money.minor("1.001")) }
    @Test fun rejectsInvalidAmounts() { listOf("0","-1","NaN","1e999","abc").forEach{assertNull(Money.minor(it))} }
    @Test fun yearlyNormalizesToMonth() { assertEquals(0,Money.monthly(120000,"YEAR").compareTo(BigDecimal("100"))) }
    @Test fun quarterlyNormalizesToMonth() { assertEquals(0,Money.monthly(90000,"QUARTER").compareTo(BigDecimal("300"))) }
    @Test fun weeklyUsesAnnualizedEstimate() { assertEquals(0,Money.monthly(1200,"WEEK").compareTo(BigDecimal("52"))) }
    @Test fun missingRateIsNotOne() { assertNull(Money.rub(BigDecimal.TEN,"USD",emptyMap()));assertEquals(BigDecimal.TEN,Money.rub(BigDecimal.TEN,"RUB",emptyMap())) }
    @Test fun conversionUsesRubPerUnit() { assertEquals(0,Money.rub(BigDecimal("10"),"USD",mapOf("USD" to "90.5"))!!.compareTo(BigDecimal("905"))) }
    @Test fun monthEndIsClamped() { assertEquals(LocalDate.of(2026,2,28),Money.next(LocalDate.of(2026,1,31),"MONTH")) }
    @Test fun passwordHashIsSaltedAndChecked() { val a=Passwords.salt();val b=Passwords.salt();val h=Passwords.hash("test-password-27",a);assertNotEquals(h,Passwords.hash("test-password-27",b));assertTrue(Passwords.verify("test-password-27",a,h));assertFalse(Passwords.verify("wrong-password",a,h)) }
    @Test fun passwordLengthBoundaries() { assertFalse(Passwords.valid("123456789"));assertTrue(Passwords.valid("1234567890"));assertFalse(Passwords.valid("x".repeat(129))) }
}
