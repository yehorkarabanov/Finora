package com.yehor.finora

import android.app.Application
import android.content.Context
import com.yehor.finora.data.dataio.PendingImportApplier
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.repo.RateRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class FinoraApp : Application() {

    @Inject
    lateinit var rates: RateRepository

    @Inject
    lateinit var recurring: com.yehor.finora.data.repo.RecurringRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        if (getProcessName() == base.packageName) {
            val dbFile = base.getDatabasePath(FinoraDatabase.FILE_NAME)
            val dbDir = dbFile.parentFile ?: File(base.applicationInfo.dataDir, "databases")
            PendingImportApplier.run(dbDir, base.cacheDir)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (getProcessName() == packageName) {
            rates.start(appScope)
            recurring.start(appScope)
        }
    }
}
