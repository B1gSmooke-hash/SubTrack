package ru.subtrack

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Money {
    fun minor(text: String): Long? = runCatching {
        val value = text.trim().replace(',', '.').toBigDecimal()
        require(value > BigDecimal.ZERO && value <= BigDecimal("100000000"))
        value.movePointRight(2).longValueExact()
    }.getOrNull()
    fun monthly(amountMinor: Long, period: String): BigDecimal {
        val amount = BigDecimal.valueOf(amountMinor, 2)
        return when (period) {
            "YEAR" -> amount.divide(BigDecimal(12), 8, RoundingMode.HALF_UP)
            "QUARTER" -> amount.divide(BigDecimal(3), 8, RoundingMode.HALF_UP)
            "WEEK" -> amount.multiply(BigDecimal(52)).divide(BigDecimal(12), 8, RoundingMode.HALF_UP)
            else -> amount
        }
    }
    // Never reinterpret legacy foreign-currency amounts as rubles.
    fun monthlyRub(amountMinor: Long, period: String, currency: String): BigDecimal? =
        if (currency == "RUB") monthly(amountMinor, period) else null
    fun next(date: LocalDate, period: String): LocalDate = when (period) {
        "YEAR" -> date.plusYears(1); "QUARTER" -> date.plusMonths(3)
        "WEEK" -> date.plusWeeks(1); else -> date.plusMonths(1)
    }
    fun display(amount: BigDecimal): String = amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
}
object Passwords {
    private const val ITERATIONS = 600000
    fun salt(): String = Base64.getEncoder().encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) })
    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, 256)
        return try { Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded) }
        finally { spec.clearPassword() }
    }
    fun verify(password: String, salt: String, expected: String): Boolean =
        MessageDigest.isEqual(Base64.getDecoder().decode(hash(password, salt)), Base64.getDecoder().decode(expected))
    fun valid(password: String): Boolean = password.length in 10..128
}
