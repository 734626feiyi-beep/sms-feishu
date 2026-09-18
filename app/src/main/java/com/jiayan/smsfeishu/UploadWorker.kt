package com.jiayan.smsfeishu

import android.content.Context
import android.graphics.Bitmap
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 真正干活的工人：把短信画成图片 → 上传飞书 → 发到群里。
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val sender = inputData.getString("sender") ?: "未知号码"
        val body = inputData.getString("body") ?: ""
        val time = inputData.getLong("time", System.currentTimeMillis())

        val appId = Prefs.appId(ctx)
        val secret = Prefs.appSecret(ctx)
        val chatId = Prefs.chatId(ctx)

        if (appId.isBlank() || secret.isBlank() || chatId.isBlank()) {
            Prefs.addLog(ctx, "跳过：飞书配置不完整（App ID / Secret / 群 ID 有缺）")
            return Result.success()
        }

        return try {
            val token = FeishuClient.getTenantToken(appId, secret)

            val bmp: Bitmap = SmsImageRenderer.render(sender, body, time)
            val jpeg = SmsImageRenderer.toJpeg(bmp)
            bmp.recycle()

            val imageKey = FeishuClient.uploadImage(token, jpeg, "sms_$time.jpg")
            FeishuClient.sendImage(token, chatId, imageKey)

            if (Prefs.sendTextToo(ctx)) {
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                    .format(java.util.Date(time))
                FeishuClient.sendText(
                    token, chatId,
                    "【短信】\n号码：$sender\n时间：$fmt\n内容：$body"
                )
            }

            Prefs.incSent(ctx)
            Prefs.addLog(ctx, "已发送到飞书：$sender")
            Result.success()
        } catch (e: Exception) {
            Prefs.addLog(ctx, "发送失败（第 ${runAttemptCount + 1} 次）：${e.message}")
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
