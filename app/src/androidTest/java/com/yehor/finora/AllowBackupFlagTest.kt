package com.yehor.finora

import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Test

class AllowBackupFlagTest {

    @Test
    fun allowBackupFlagIsNotSet() {
        val args = InstrumentationRegistry.getArguments()
        assumeFalse(
            "backupVerification build allows backup on purpose",
            args.getString("backupVerification") == "true",
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val flags = context.applicationInfo.flags
        assertEquals(0, flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
