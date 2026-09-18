package com.jiayan.smsfeishu

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 兜底扫描：有些 MIUI/HyperOS 机型在省电策略下会拦掉短信广播，
 * 所以每 15 分钟扫一次系统短信收件箱，把漏掉的补发出去（靠去重避免重复发送）。
 */
class SmsInboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!Prefs.scanEnabled(ctx)) return Result.success()

        try {
            val uri = Uri.parse("content://sms/inbox")
            val cursor = ctx.contentResolver.query(
                uri,
                arrayOf("_id", "address", "body", "date"),
                null, null, "date DESC"
            )
            cursor?.use {
                var n = 0
                while (it.moveToNext() && n < 20) {
                    n++
                    val addr = it.getString(1) ?: ""
                    val body = it.getString(2) ?: ""
                    val date = it.getLong(3)
                    if (body.isNotBlank()) SmsPipeline.handle(ctx, addr, body, date)
                }
            }
        } catch (e: SecurityException) {
            Prefs.addLog(ctx, "兜底扫描失败：没有短信读取权限")
        } catch (e: Exception) {
            Prefs.addLog(ctx, "兜底扫描异常：${e.message}")
        }
        return Result.success()
    }
}
