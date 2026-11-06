package com.yehor.finora

import android.app.Application
import com.yehor.finora.data.repo.RateRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class FinoraApp : Application() {

    @Inject
    lateinit var rates: RateRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        rates.start(appScope)
    }
}
