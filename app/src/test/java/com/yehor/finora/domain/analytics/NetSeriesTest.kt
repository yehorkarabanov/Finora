package com.yehor.finora.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NetSeriesTest {

    @Test fun perBucketSubtracts() = assertEquals(listOf(70L, -20L), NetSeries.perBucket(listOf(100L, 0L), listOf(30L, 20L)))

    @Test fun runningAccumulatesFromZero() = assertEquals(listOf(-10L, 90L, 60L), NetSeries.running(listOf(0L, 100L, 0L), listOf(10L, 0L, 30L)))

    @Test fun futureBucketsAreNull() = assertEquals(listOf(5L, null, null), NetSeries.running(listOf(5L, 5L, 5L), listOf(0L, 0L, 0L), lastIndex = 0))

    @Test fun unknownBucketIsNullButRunningContinues() = assertEquals(listOf(10L, null, 15L), NetSeries.running(listOf(10L, null, 5L), listOf(0L, 0L, 0L)))

    @Test fun emptyInputGivesEmpty() = assertEquals(emptyList<Long?>(), NetSeries.running(emptyList(), emptyList()))

    @Test fun mismatchedSizesThrow() {
        assertThrows(IllegalArgumentException::class.java) { NetSeries.perBucket(listOf(1L), emptyList()) }
    }
}
