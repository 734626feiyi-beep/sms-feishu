package com.jiayan.smsfeishu

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            val request = PeriodicWorkRequestBuilder<SmsInboxWorker>(15, TimeUnit.MINUTES)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "sms_inbox_scan",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (e: Exception) {
            Prefs.addLog(this, "初始化兜底扫描失败：${e.message}")
        }
    }
}
