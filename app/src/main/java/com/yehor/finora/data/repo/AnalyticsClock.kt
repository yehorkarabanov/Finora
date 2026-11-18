package com.yehor.finora.data.repo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.yehor.finora.domain.analytics.DayTicker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Time facts for the analytics screens: today (moving at midnight and after a time zone change),
 * the device zone and the locale's first weekday. Period boundaries are computed from these at
 * query time (design section 2).
 */
@Singleton
class AnalyticsClock(
    private val nowMillis: () -> Long,
    private val zoneProvider: () -> ZoneId,
    private val firstDay: () -> DayOfWeek,
    zoneChanges: Flow<Unit>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        System::currentTimeMillis,
        ZoneId::systemDefault,
        { WeekFields.of(Locale.getDefault()).firstDayOfWeek },
        timeZoneChanges(context),
    )

    private val ticker = DayTicker(nowMillis, zoneProvider, zoneChanges)

    fun days(): Flow<LocalDate> = ticker.days()

    fun now(): Long = nowMillis()

    fun zone(): ZoneId = zoneProvider()

    fun firstDayOfWeek(): DayOfWeek = firstDay()

    private companion object {
        fun timeZoneChanges(context: Context): Flow<Unit> = callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    trySend(Unit)
                }
            }
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED))
            awaitClose { context.unregisterReceiver(receiver) }
        }
    }
}
