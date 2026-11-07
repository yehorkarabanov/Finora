package com.yehor.finora.data.dataio

import android.content.Context
import android.content.Intent
import android.os.Process

object ProcessRestarter {

    fun restart(context: Context) {
        val intent = Intent(context, RestartActivity::class.java).apply {
            putExtra(RestartActivity.EXTRA_MAIN_PID, Process.myPid())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
