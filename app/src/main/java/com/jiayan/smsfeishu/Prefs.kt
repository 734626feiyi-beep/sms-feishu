package com.jiayan.smsfeishu

import android.content.Context

/**
 * 本地配置存储（用 SharedPreferences，不依赖数据库，简单可靠）
 */
object Prefs {
    private const val NAME = "sms_feishu"

    private fun sp(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ---------- 飞书配置 ----------
    fun appId(c: Context): String = sp(c).getString("app_id", "") ?: ""

    fun appSecret(c: Context): String = sp(c).getString("app_secret", "") ?: ""

    fun chatId(c: Context): String = sp(c).getString("chat_id", "") ?: ""

    fun chatName(c: Context): String = sp(c).getString("chat_name", "") ?: ""

    fun setCredentials(c: Context, id: String, secret: String) {
        sp(c).edit().putString("app_id", id).putString("app_secret", secret).apply()
    }

    fun setChat(c: Context, id: String, name: String) {
        sp(c).edit().putString("chat_id", id).putString("chat_name", name).apply()
    }

    // ---------- 开关 ----------
    fun sendTextToo(c: Context): Boolean = sp(c).getBoolean("send_text_too", true)

    fun setSendTextToo(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("send_text_too", v).apply()
    }

    fun scanEnabled(c: Context): Boolean = sp(c).getBoolean("scan_enabled", true)

    fun setScanEnabled(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("scan_enabled", v).apply()
    }

    // ---------- 统计 ----------
    fun sentCount(c: Context): Int = sp(c).getInt("sent_count", 0)

    fun incSent(c: Context) {
        sp(c).edit().putInt("sent_count", sentCount(c) + 1).apply()
    }

    // ---------- 去重（避免同一条短信重复发送）----------
    fun seen(c: Context, key: String): Boolean {
        val raw = sp(c).getString("sent_keys", "") ?: ""
        return raw.split("\n").contains(key)
    }

    fun markSeen(c: Context, key: String) {
        val p = sp(c)
        val list = (p.getString("sent_keys", "") ?: "")
            .split("\n").filter { it.isNotBlank() }.toMutableList()
        if (!list.contains(key)) list.add(key)
        while (list.size > 400) list.removeAt(0)
        p.edit().putString("sent_keys", list.joinToString("\n")).apply()
    }

    // ---------- 运行日志（方便手机上看排错信息）----------
    fun log(c: Context): String = sp(c).getString("log", "") ?: ""

    fun addLog(c: Context, line: String) {
        val p = sp(c)
        val ts = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.CHINA)
            .format(java.util.Date())
        val lines = (p.getString("log", "") ?: "")
            .split("\n").filter { it.isNotBlank() }.toMutableList()
        lines.add("[$ts] $line")
        while (lines.size > 60) lines.removeAt(0)
        p.edit().putString("log", lines.joinToString("\n")).apply()
    }

    fun clearLog(c: Context) {
        sp(c).edit().putString("log", "").apply()
    }
}
