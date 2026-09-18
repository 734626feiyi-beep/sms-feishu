package com.jiayan.smsfeishu

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 短信处理流水线：去重 → 记日志 → 交给 WorkManager 上传。
 * 用 WorkManager 而不是直接发网络请求，是为了断网/失败时自动重试，且系统不会中途杀掉。
 */
object SmsPipeline {

    fun handle(context: Context, sender: String, body: String, timeMillis: Long) {
        if (body.isBlank() && sender.isBlank()) return

        val key = md5("$sender|$body|$timeMillis")
        if (Prefs.seen(context, key)) return // 广播和兜底扫描重复上报同一条短信
        Prefs.markSeen(context, key)

        Prefs.addLog(context, "收到短信：$sender（${body.length} 字）")
        enqueue(context, sender, body, timeMillis)
    }

    fun enqueue(context: Context, sender: String, body: String, timeMillis: Long) {
        val data = Data.Builder()
            .putString("sender", sender)
            .putString("body", body.take(3000))
            .putLong("time", timeMillis)
            .build()

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    private fun md5(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (b in d) sb.append(String.format("%02x", b))
        return sb.toString()
    }
}
