package com.jiayan.smsfeishu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * 短信到达的第一入口：系统收到短信后立刻把这条短信交给我们。
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        try {
            val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            if (msgs.isEmpty()) return

            val bodies = msgs.mapNotNull { it.displayMessageBody ?: it.messageBody }
            // 长短信会被拆成多段：有的系统已合并（各段内容相同，去重），有的需要拼接
            val body = when {
                bodies.isEmpty() -> ""
                bodies.size > 1 && bodies.all { it == bodies[0] } -> bodies[0]
                else -> bodies.joinToString("")
            }

            val sender = (msgs[0].displayOriginatingAddress
                ?: msgs[0].originatingAddress
                ?: "未知号码").trim()
            val timeMillis = msgs[0].timestampMillis

            SmsPipeline.handle(context, sender, body, timeMillis)
        } catch (e: Exception) {
            Prefs.addLog(context, "接收短信异常：${e.message}")
        }
    }
}
