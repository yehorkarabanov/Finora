package com.yehor.finora.data.dataio

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process

class RestartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mainPid = intent.getIntExtra(EXTRA_MAIN_PID, -1)
        if (mainPid > 0) {
            Process.killProcess(mainPid)
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (launchIntent != null) {
            startActivity(launchIntent)
        }
        finish()
        Process.killProcess(Process.myPid())
    }

    companion object {
        const val EXTRA_MAIN_PID = "extra_main_pid"
    }
}
