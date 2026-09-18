package com.jiayan.smsfeishu

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把短信内容画成一张「短信通知」图片。
 *
 * 为什么不直接截手机屏幕？
 *  - Android 14 / HyperOS 后台截屏必须走 MediaProjection，每次重启手机都要重新授权一次，
 *    息屏、锁屏、部分应用界面还会截出黑屏，非常不稳定；
 *  - 自己画图 100% 稳定，文字清晰、内容完整，不受手机状态影响。
 */
object SmsImageRenderer {

    private const val W = 1080
    private const val BLUE = "#3370FF"

    fun render(sender: String, body: String, timeMillis: Long): Bitmap {
        val pad = 40f
        val bubblePad = 34f
        val lineGap = 16f

        val bodyPaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.parseColor("#1F2329")
            textSize = 40f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headTimePaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.parseColor("#D6E4FF")
            textSize = 30f
        }
        val senderPaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.parseColor(BLUE)
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val metaPaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.parseColor("#646A73")
            textSize = 30f
        }

        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        val timeText = fmt.format(Date(timeMillis))
        val bodyText = if (body.length > 1500) body.substring(0, 1500) + "……（内容过长已截断）" else body

        val maxTextWidth = W - pad * 2 - bubblePad * 2
        val lines = wrap(bodyText, bodyPaint, maxTextWidth)
        val fm = bodyPaint.fontMetrics
        val lineHeight = fm.descent - fm.ascent + lineGap

        val headerH = 170f
        val infoH = 210f
        val bubbleH = bubblePad * 2 + lineHeight * lines.size
        val cardTop = headerH + 40f
        val cardBottom = cardTop + infoH + bubbleH + 40f
        val totalH = (cardBottom + pad).toInt()

        val bmp = Bitmap.createBitmap(W, totalH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // 背景
        c.drawColor(Color.parseColor("#F2F3F5"))

        // 顶部蓝色标题栏
        c.drawRect(0f, 0f, W.toFloat(), headerH,
            Paint().apply { isAntiAlias = true; color = Color.parseColor(BLUE) })
        c.drawText("短信通知", pad, 92f, titlePaint)
        c.drawText(timeText, pad, 142f, headTimePaint)

        // 白色卡片
        c.drawRoundRect(
            RectF(pad * 0.7f, cardTop, W - pad * 0.7f, cardBottom), 28f, 28f,
            Paint().apply { isAntiAlias = true; color = Color.WHITE }
        )

        // 发件人 / 时间
        var y = cardTop + 80f
        c.drawText("发件号码：" + (if (sender.isBlank()) "未知号码" else sender), pad, y, senderPaint)
        y += 58f
        c.drawText("接收时间：$timeText", pad, y, metaPaint)

        // 短信气泡
        val bubbleTop = cardTop + infoH
        c.drawRoundRect(
            RectF(pad, bubbleTop, W - pad, bubbleTop + bubbleH), 22f, 22f,
            Paint().apply { isAntiAlias = true; color = Color.parseColor("#E8F3FF") }
        )
        var baseline = bubbleTop + bubblePad - fm.ascent
        for (line in lines) {
            c.drawText(line, pad + bubblePad, baseline, bodyPaint)
            baseline += lineHeight
        }

        return bmp
    }

    fun toJpeg(bmp: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
        return out.toByteArray()
    }

    /** 按像素宽度逐字折行（中英文混排都能处理） */
    private fun wrap(text: String, paint: TextPaint, maxWidth: Float): List<String> {
        val result = ArrayList<String>()
        for (raw in text.split("\n")) {
            val para = if (raw.isEmpty()) " " else raw
            var cur = StringBuilder()
            for (ch in para) {
                val test = cur.toString() + ch
                if (cur.isNotEmpty() && paint.measureText(test) > maxWidth) {
                    result.add(cur.toString())
                    cur = StringBuilder().append(ch)
                } else {
                    cur.append(ch)
                }
            }
            result.add(cur.toString())
        }
        if (result.isEmpty()) result.add(" ")
        return result
    }
}
