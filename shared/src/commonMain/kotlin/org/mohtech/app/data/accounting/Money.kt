package org.mohtech.app.data.accounting

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlinx.serialization.Serializable

/**
 * Money:
 * Currency-aware representation enforcing precision, minor units, half-up rounding,
 * and currency isolation across all financial operations.
 */
@Serializable
data class Money(
    val amountMinor: Long,
    val currency: String
) : Comparable<Money> {

    val decimals: Int get() = getCurrencyDecimals(currency)
    val amountDouble: Double get() = minorToDouble(amountMinor, currency)

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amountMinor + other.amountMinor, currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amountMinor - other.amountMinor, currency)
    }

    operator fun times(factor: Long): Money {
        return Money(amountMinor * factor, currency)
    }

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amountMinor.compareTo(other.amountMinor)
    }

    private fun requireSameCurrency(other: Money) {
        require(this.currency == other.currency) {
            "Currency mismatch: cannot combine ${this.currency} with ${other.currency}. Currency isolation enforced."
        }
    }

    companion object {
        fun of(amount: Double, currency: String): Money {
            return Money(doubleToMinor(amount, currency), currency)
        }

        fun zero(currency: String): Money {
            return Money(0L, currency)
        }

        /**
         * Resolves the decimal places for standard supported currencies:
         * JOD (د.أ) = 3 decimal places (fils).
         * ILS (₪), SAR (ر.س), EGP (ج.م), USD ($) = 2 decimal places.
         */
        fun getCurrencyDecimals(currency: String): Int {
            return when (currency.trim()) {
                "د.أ", "JOD", "JD", "دينار", "دينار أردني" -> 3
                "₪", "ILS", "ر.س", "SAR", "ج.م", "EGP", "$", "USD" -> 2
                else -> 2
            }
        }

        /**
         * Converts a Double amount to currency minor units using Half-Up rounding.
         */
        fun doubleToMinor(amount: Double, currency: String): Long {
            val decimals = getCurrencyDecimals(currency)
            val factor = 10.0.pow(decimals)
            val scaled = amount * factor
            val sign = if (scaled < 0) -1.0 else 1.0
            val epsilon = 1e-9
            val rounded = floor(abs(scaled) + 0.5 + epsilon) * sign
            return rounded.toLong()
        }

        /**
         * Converts currency minor units back to standard Double representation.
         */
        fun minorToDouble(minor: Long, currency: String): Double {
            val decimals = getCurrencyDecimals(currency)
            val factor = 10.0.pow(decimals)
            return minor / factor
        }

        /**
         * Rounds a Double amount to the appropriate currency decimal places using Half-Up rounding.
         */
        fun roundAmount(amount: Double, currency: String): Double {
            return minorToDouble(doubleToMinor(amount, currency), currency)
        }

        /**
         * Distributes [totalAmount] evenly among [totalParticipants] (where 1 of them is the user).
         * Allocates any remainder minor units to [myShare] to guarantee:
         * otherShares.sum() + myShare == totalAmount (both in minor units and Double).
         */
        fun distributeSplitEvenly(
            totalAmount: Double,
            currency: String,
            totalParticipants: Int
        ): EvenSplitResult {
            require(totalParticipants > 0) { "Total participants must be at least 1" }
            require(totalAmount >= 0.0) { "Total amount cannot be negative" }
            val totalMinor = doubleToMinor(totalAmount, currency)
            if (totalParticipants == 1) {
                val roundedTotal = minorToDouble(totalMinor, currency)
                return EvenSplitResult(
                    totalAmount = roundedTotal,
                    myShare = roundedTotal,
                    sharePerOther = 0.0,
                    otherShares = emptyList(),
                    currency = currency
                )
            }
            val baseMinorPerPerson = totalMinor / totalParticipants
            val remainderMinor = totalMinor % totalParticipants
            val othersCount = totalParticipants - 1
            val othersMinor = baseMinorPerPerson
            // Remainder minor units are distributed to the user's share
            val myMinor = baseMinorPerPerson + remainderMinor

            val myShareDouble = minorToDouble(myMinor, currency)
            val otherShareDouble = minorToDouble(othersMinor, currency)
            val otherShares = List(othersCount) { otherShareDouble }

            return EvenSplitResult(
                totalAmount = minorToDouble(totalMinor, currency),
                myShare = myShareDouble,
                sharePerOther = otherShareDouble,
                otherShares = otherShares,
                currency = currency
            )
        }

        /**
         * Calculates user's [myShare] given a total amount and custom allocation amounts for others.
         * Guarantees: allocations.sum() + myShare == totalAmount exactly.
         */
        fun calculateRemainderShare(
            totalAmount: Double,
            currency: String,
            allocationAmounts: List<Double>
        ): Double {
            val totalMinor = doubleToMinor(totalAmount, currency)
            val allocationsMinorSum = allocationAmounts.sumOf { doubleToMinor(it, currency) }
            val myMinor = totalMinor - allocationsMinorSum
            return minorToDouble(myMinor, currency)
        }
    }
}

/**
 * Result of an even expense split calculation.
 */
data class EvenSplitResult(
    val totalAmount: Double,
    val myShare: Double,
    val sharePerOther: Double,
    val otherShares: List<Double>,
    val currency: String
)

