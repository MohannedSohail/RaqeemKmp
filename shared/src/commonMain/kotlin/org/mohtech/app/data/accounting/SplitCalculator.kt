package org.mohtech.app.data.accounting

data class ClampResult(
    val clampedAmount: Double,
    val wasClamped: Boolean,
    val maxAllowed: Double
)

data class AutoSplitSummary(
    val totalAmount: Double,
    val personalShare: Double,
    val partyShares: List<Double>,
    val personalRatio: Float,
    val partyRatios: List<Float>
)

/**
 * Minor-unit auto-balancing split calculator and input clamper.
 * Guarantees external party shares never exceed the bill total across 2-decimal and 3-decimal (JOD) currencies.
 */
object SplitCalculator {

    private fun safeNonNegativeMinor(amount: Double, currency: String): Long {
        if (!amount.isFinite() || amount <= 0.0) return 0L
        return Money.doubleToMinor(amount, currency).coerceAtLeast(0L)
    }

    fun clampPartyShare(
        totalAmount: Double,
        currency: String,
        otherPartyShares: List<Double>,
        requestedAmount: Double
    ): ClampResult {
        val totalMinor = safeNonNegativeMinor(totalAmount, currency)
        var remainingMinor = totalMinor
        for (share in otherPartyShares) {
            remainingMinor = (remainingMinor - safeNonNegativeMinor(share, currency)).coerceAtLeast(0L)
        }
        val maxAllowedMinor = remainingMinor

        val clampedMinor: Long
        val wasClamped: Boolean

        if (!requestedAmount.isFinite() || requestedAmount < 0.0) {
            clampedMinor = 0L
            wasClamped = true
        } else if (requestedAmount == 0.0) {
            clampedMinor = 0L
            wasClamped = false
        } else {
            val reqMinor = Money.doubleToMinor(requestedAmount, currency).coerceAtLeast(0L)
            clampedMinor = reqMinor.coerceAtMost(maxAllowedMinor)
            wasClamped = reqMinor > maxAllowedMinor
        }

        return ClampResult(
            clampedAmount = Money.minorToDouble(clampedMinor, currency),
            wasClamped = wasClamped,
            maxAllowed = Money.minorToDouble(maxAllowedMinor, currency)
        )
    }

    fun clampSharesToTotal(
        totalAmount: Double,
        currency: String,
        partyShares: List<Double>
    ): List<Double> {
        var remainingMinor = safeNonNegativeMinor(totalAmount, currency)
        return partyShares.map { share ->
            val shareMinor = safeNonNegativeMinor(share, currency)
            val allowedMinor = shareMinor.coerceAtMost(remainingMinor)
            remainingMinor -= allowedMinor
            Money.minorToDouble(allowedMinor, currency)
        }
    }

    fun computeAutoBalancedSplit(
        totalAmount: Double,
        currency: String,
        partyShares: List<Double>
    ): AutoSplitSummary {
        val totalMinor = safeNonNegativeMinor(totalAmount, currency)
        var remainingMinor = totalMinor
        val clampedMinors = partyShares.map { share ->
            val shareMinor = safeNonNegativeMinor(share, currency)
            val allowedMinor = shareMinor.coerceAtMost(remainingMinor)
            remainingMinor -= allowedMinor
            allowedMinor
        }

        val partiesMinorSum = clampedMinors.sum()
        val personalMinor = (totalMinor - partiesMinorSum).coerceAtLeast(0L)

        val personalRatio: Float
        val partyRatios: List<Float>
        if (totalMinor <= 0L) {
            personalRatio = 0f
            partyRatios = List(partyShares.size) { 0f }
        } else {
            val totalFloat = totalMinor.toDouble()
            personalRatio = (personalMinor.toDouble() / totalFloat).toFloat().coerceIn(0f, 1f)
            partyRatios = clampedMinors.map { minor ->
                (minor.toDouble() / totalFloat).toFloat().coerceIn(0f, 1f)
            }
        }

        return AutoSplitSummary(
            totalAmount = Money.minorToDouble(totalMinor, currency),
            personalShare = Money.minorToDouble(personalMinor, currency),
            partyShares = clampedMinors.map { Money.minorToDouble(it, currency) },
            personalRatio = personalRatio,
            partyRatios = partyRatios
        )
    }
}

