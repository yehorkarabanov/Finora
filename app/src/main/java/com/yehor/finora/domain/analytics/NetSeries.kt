package com.yehor.finora.domain.analytics

object NetSeries {

    /**
     * Per bucket: income - expense. Null when either side is null (unconvertible/overflow).
     * Buckets after [lastIndex] are null (future). Overflow in the subtraction also gives null.
     */
    fun perBucket(income: List<Long?>, expense: List<Long?>, lastIndex: Int = income.lastIndex): List<Long?> {
        require(income.size == expense.size) { "size mismatch: ${income.size} income vs ${expense.size} expense" }
        return income.indices.map { i ->
            val inc = income[i]
            val exp = expense[i]
            if (i > lastIndex || inc == null || exp == null) null
            else try {
                Math.subtractExact(inc, exp)
            } catch (e: ArithmeticException) {
                null
            }
        }
    }

    /**
     * Running sum of [perBucket] starting at 0. A null bucket makes that point null but the running total
     * continues from the last known value. A running-sum overflow makes that point null and keeps the last total.
     */
    fun running(income: List<Long?>, expense: List<Long?>, lastIndex: Int = income.lastIndex): List<Long?> {
        var total = 0L
        return perBucket(income, expense, lastIndex).map { net ->
            if (net == null) null
            else try {
                total = Math.addExact(total, net)
                total
            } catch (e: ArithmeticException) {
                null
            }
        }
    }
}
