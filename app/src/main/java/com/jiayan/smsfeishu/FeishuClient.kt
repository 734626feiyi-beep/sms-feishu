package com.jiayan.smsfeishu

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 飞书开放平台接口调用（只用系统自带的 HttpURLConnection，不引入第三方库）
 *
 * 用到的三个接口：
 *  1. 获取 tenant_access_token
 *  2. 上传图片  → 拿到 image_key
 *  3. 用 image_key 往群里发图片消息
 */
object FeishuClient {

    private const val BASE = "https://open.feishu.cn/open-apis"

    class FeishuException(message: String) : Exception(message)

    // ---------------- 1. 获取企业自建应用 token ----------------
    fun getTenantToken(appId: String, appSecret: String): String {
        val body = JSONObject()
            .put("app_id", appId)
            .put("app_secret", appSecret)
            .toString()
        val resp = postJson("$BASE/auth/v3/tenant_access_token/internal", null, body)
        val json = JSONObject(resp)
        val code = json.optInt("code", -1)
        if (code != 0) {
            throw FeishuException("获取 token 失败：${json.optString("msg")} (code=$code)")
        }
        return json.getString("tenant_access_token")
    }

    // ---------------- 2. 上传图片 ----------------
    fun uploadImage(token: String, jpeg: ByteArray, fileName: String): String {
        val boundary = "----smsfeishu" + System.currentTimeMillis()
        val conn = (URL("$BASE/im/v1/images").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20000
            readTimeout = 60000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        conn.outputStream.use { os ->
            fun write(s: String) = os.write(s.toByteArray(Charsets.UTF_8))
            write("--$boundary\r\n")
            write("Content-Disposition: form-data; name=\"image_type\"\r\n\r\n")
            write("message\r\n")
            write("--$boundary\r\n")
            write("Content-Disposition: form-data; name=\"image\"; filename=\"$fileName\"\r\n")
            write("Content-Type: image/jpeg\r\n\r\n")
            os.write(jpeg)
            write("\r\n--$boundary--\r\n")
        }
        val json = JSONObject(readResponse(conn))
        val code = json.optInt("code", -1)
        if (code != 0) {
            throw FeishuException("上传图片失败：${json.optString("msg")} (code=$code)")
        }
        return json.getJSONObject("data").getString("image_key")
    }

    // ---------------- 3. 往群里发图片 ----------------
    fun sendImage(token: String, chatId: String, imageKey: String) {
        val content = JSONObject().put("image_key", imageKey).toString()
        val body = JSONObject()
            .put("receive_id", chatId)
            .put("msg_type", "image")
            .put("content", content)
            .toString()
        checkOk(postJson("$BASE/im/v1/messages?receive_id_type=chat_id", token, body))
    }

    // ---------------- 3b. 往群里发文字（便于同事复制/搜索）----------------
    fun sendText(token: String, chatId: String, text: String) {
        val content = JSONObject().put("text", text).toString()
        val body = JSONObject()
            .put("receive_id", chatId)
            .put("msg_type", "text")
            .put("content", content)
            .toString()
        checkOk(postJson("$BASE/im/v1/messages?receive_id_type=chat_id", token, body))
    }

    // ---------------- 4. 列出机器人所在的群（用于 App 里一键选群）----------------
    fun listChats(token: String): List<Pair<String, String>> {
        val conn = (URL("$BASE/im/v1/chats?page_size=100").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000
            readTimeout = 30000
            setRequestProperty("Authorization", "Bearer $token")
        }
        val json = JSONObject(readResponse(conn))
        val code = json.optInt("code", -1)
        if (code != 0) {
            throw FeishuException("获取群列表失败：${json.optString("msg")} (code=$code)")
        }
        val items = json.optJSONObject("data")?.optJSONArray("items") ?: return emptyList()
        val result = ArrayList<Pair<String, String>>()
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            val id = o.optString("chat_id")
            var name = o.optString("name")
            if (name.isBlank()) name = "未命名群"
            if (id.isNotBlank()) result.add(id to name)
        }
        return result
    }

    // ---------------- 内部工具 ----------------
    private fun checkOk(resp: String) {
        val json = JSONObject(resp)
        val code = json.optInt("code", -1)
        if (code != 0) {
            throw FeishuException("发送消息失败：${json.optString("msg")} (code=$code)")
        }
    }

    private fun postJson(urlStr: String, token: String?, body: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20000
            readTimeout = 30000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return readResponse(conn)
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        val text = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw FeishuException("HTTP $code：$text")
        return text
    }
}
