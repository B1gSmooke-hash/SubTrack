package ru.subtrack
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class DomainTest {
    @Test fun paymentDatesRejectPastAndInvalidValues() {
        val today=LocalDate.of(2026,10,29)
        assertFalse(PaymentDates.allowed("2026-10-28",today))
        assertFalse(PaymentDates.allowed("2026-02-30",today))
        assertFalse(PaymentDates.allowed("",today))
        assertTrue(PaymentDates.allowed("2026-10-29",today))
        assertTrue(PaymentDates.allowed("2027-01-01",today))
    }
    @Test fun calendarUtcDaysRoundTripAcrossTimeZones() {
        val day=LocalDate.of(2026,10,29)
        for(zone in listOf("Pacific/Kiritimati","America/Los_Angeles","Europe/Moscow")) {
            val now=day.atTime(23,59).atZone(java.time.ZoneId.of(zone))
            assertEquals(now.toLocalDate(),PaymentDates.fromUtc(PaymentDates.utcMillis(now.toLocalDate())))
        }
    }
    @Test fun parsesCommaAndRejectsFractionsOfKopeck() { assertEquals(29990L,Money.minor("299,90"));assertNull(Money.minor("1.001")) }
    @Test fun rejectsInvalidAmounts() { listOf("0","-1","NaN","1e999","abc").forEach{assertNull(Money.minor(it))} }
    @Test fun yearlyNormalizesToMonth() { assertEquals(0,Money.monthly(120000,"YEAR").compareTo(BigDecimal("100"))) }
    @Test fun quarterlyNormalizesToMonth() { assertEquals(0,Money.monthly(90000,"QUARTER").compareTo(BigDecimal("300"))) }
    @Test fun weeklyUsesAnnualizedEstimate() { assertEquals(0,Money.monthly(1200,"WEEK").compareTo(BigDecimal("52"))) }
    @Test fun rubleTotalsKeepKopecksAndPeriods() {
        assertEquals(0, Money.monthlyRub(29990, "MONTH", "RUB")!!.compareTo(BigDecimal("299.90")))
        assertEquals(0, Money.monthlyRub(302400, "YEAR", "RUB")!!.compareTo(BigDecimal("252")))
    }
    @Test fun legacyAmountsAreNotRelabeledAsRubles() {
        listOf("USD", "EUR", "").forEach { assertNull(Money.monthlyRub(1000, "MONTH", it)) }
    }
    @Test fun monthEndIsClamped() { assertEquals(LocalDate.of(2026,2,28),Money.next(LocalDate.of(2026,1,31),"MONTH")) }
    @Test fun passwordHashIsSaltedAndChecked() { val a=Passwords.salt();val b=Passwords.salt();val h=Passwords.hash("test-password-27",a);assertNotEquals(h,Passwords.hash("test-password-27",b));assertTrue(Passwords.verify("test-password-27",a,h));assertFalse(Passwords.verify("wrong-password",a,h)) }
    @Test fun passwordLengthBoundaries() { assertFalse(Passwords.valid("12345"));assertTrue(Passwords.valid("123456"));assertTrue(Passwords.valid("x".repeat(128)));assertFalse(Passwords.valid("x".repeat(129))) }
}
