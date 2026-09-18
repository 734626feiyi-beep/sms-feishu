package com.jiayan.smsfeishu

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * 唯一的一个界面：填配置、选群、测一条、看日志。
 * 全部用代码画界面，不再单独写 xml 布局，减少出错点。
 */
class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var chatText: TextView
    private lateinit var appIdEdit: EditText
    private lateinit var secretEdit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pad = dp(16)
        root.setPadding(pad, pad, pad, pad)
        setContentView(ScrollView(this).apply { addView(root) })

        val title = TextView(this).apply {
            text = "短信自动转发到飞书"
            textSize = 22f
            setTextColor(Color.parseColor("#1F2329"))
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(title)
        root.addView(hint("收到短信后自动生成图片并发送到飞书群，供同事查看 / 转发。"))

        // ---------- 状态 ----------
        statusText = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(14), 0, dp(6))
        }
        root.addView(statusText)

        // ---------- 第 1 步 ----------
        root.addView(section("第 1 步 · 申请短信权限"))
        root.addView(button("申请短信权限（必须）") { askSmsPermission() })

        // ---------- 第 2 步 ----------
        root.addView(section("第 2 步 · 填写飞书应用信息"))
        appIdEdit = EditText(this).apply {
            hint = "App ID（cli_ 开头）"
            setSingleLine()
            setText(Prefs.appId(this@MainActivity))
        }
        secretEdit = EditText(this).apply {
            hint = "App Secret"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(Prefs.appSecret(this@MainActivity))
        }
        root.addView(appIdEdit)
        root.addView(secretEdit)
        root.addView(button("保存") {
            Prefs.setCredentials(
                this,
                appIdEdit.text.toString().trim(),
                secretEdit.text.toString().trim()
            )
            toast("已保存")
            refreshStatus()
        })

        // ---------- 第 3 步 ----------
        root.addView(section("第 3 步 · 选择接收短信的飞书群"))
        chatText = TextView(this).apply { textSize = 14f }
        root.addView(chatText)
        root.addView(button("一键获取群列表并选择") { fetchChats() })
        root.addView(button("手动填写群 ID") { askChatIdManually() })

        // ---------- 第 4 步 ----------
        root.addView(section("第 4 步 · 测试"))
        root.addView(button("发送一条测试短信到飞书") {
            SmsPipeline.enqueue(
                this,
                "10086（测试）",
                "【测试】这是一条来自测试机的短信，收到说明配置成功。",
                System.currentTimeMillis()
            )
            toast("已加入发送队列，马上看飞书群")
        })

        val textToggle = CheckBox(this).apply {
            text = "同时发送一条文字版（方便同事复制内容）"
            isChecked = Prefs.sendTextToo(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setSendTextToo(this@MainActivity, v) }
        }
        root.addView(textToggle)

        val scanToggle = CheckBox(this).apply {
            text = "开启兜底扫描（每 15 分钟核对收件箱，防漏收）"
            isChecked = Prefs.scanEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setScanEnabled(this@MainActivity, v) }
        }
        root.addView(scanToggle)

        // ---------- 日志 ----------
        root.addView(section("运行日志（最近 60 条）"))
        logText = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, dp(6), 0, dp(6))
        }
        root.addView(logText)
        root.addView(button("刷新日志") { refreshLog() })
        root.addView(button("清空日志") {
            Prefs.clearLog(this)
            refreshLog()
        })
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshLog()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
        if (requestCode == 1001) {
            val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            toast(if (ok) "短信权限已授权" else "短信权限被拒绝，无法收到短信")
        }
    }

    // ================= 权限 =================

    private fun askSmsPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
            1001
        )
    }

    private fun hasPermission(p: String): Boolean =
        checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    // ================= 刷新界面 =================

    private fun refreshStatus() {
        val recv = hasPermission(Manifest.permission.RECEIVE_SMS)
        val read = hasPermission(Manifest.permission.READ_SMS)
        val cfg = Prefs.appId(this).isNotBlank() && Prefs.appSecret(this).isNotBlank()
        val chat = Prefs.chatId(this)

        statusText.text = buildString {
            append("短信接收权限：").append(if (recv) "已授权 ✓" else "未授权 ✗")
            append("\n收件箱读取权限：").append(if (read) "已授权 ✓" else "未授权 ✗")
            append("\n飞书应用信息：").append(if (cfg) "已填写 ✓" else "未填写 ✗")
            append("\n接收群：")
            append(
                if (chat.isBlank()) "未选择 ✗"
                else Prefs.chatName(this@MainActivity) + "（…" + chat.takeLast(8) + "）"
            )
            append("\n累计成功发送：").append(Prefs.sentCount(this@MainActivity)).append(" 条")
        }

        chatText.text = if (chat.isBlank()) "尚未选择群" else "当前群：" + Prefs.chatName(this@MainActivity)
    }

    private fun refreshLog() {
        val l = Prefs.log(this)
        logText.text = if (l.isBlank()) "（暂无日志）" else l
    }

    // ================= 拉群列表 =================

    private fun fetchChats() {
        val appId = Prefs.appId(this)
        val secret = Prefs.appSecret(this)
        if (appId.isBlank() || secret.isBlank()) {
            toast("请先在第 2 步填写并保存 App ID / App Secret")
            return
        }
        toast("正在获取群列表…")
        Thread {
            try {
                val token = FeishuClient.getTenantToken(appId, secret)
                val chats = FeishuClient.listChats(token)
                runOnUiThread {
                    if (chats.isEmpty()) {
                        AlertDialog.Builder(this)
                            .setTitle("没找到可用的群")
                            .setMessage(
                                "请依次检查：\n" +
                                    "1. 应用是否已「创建版本并发布」；\n" +
                                    "2. 机器人是否已被拉进目标群；\n" +
                                    "3. 是否已开通「获取群组信息」权限。"
                            )
                            .setPositiveButton("知道了", null)
                            .show()
                    } else {
                        val names = chats.map { it.second }.toTypedArray()
                        AlertDialog.Builder(this)
                            .setTitle("选择接收短信的群")
                            .setItems(names) { _, which ->
                                val id = chats[which].first
                                val name = chats[which].second
                                Prefs.setChat(this, id, name)
                                Prefs.addLog(this, "已选择群：$name")
                                toast("已选择：$name")
                                refreshStatus()
                                refreshLog()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                Prefs.addLog(this, "获取群列表失败：${e.message}")
                runOnUiThread {
                    toast("获取失败：${e.message}")
                    refreshLog()
                }
            }
        }.start()
    }

    private fun askChatIdManually() {
        val input = EditText(this).apply {
            hint = "oc_ 开头的 chat_id"
            setSingleLine()
            setText(Prefs.chatId(this@MainActivity))
        }
        AlertDialog.Builder(this)
            .setTitle("手动填写群 ID")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val id = input.text.toString().trim()
                if (id.isNotBlank()) {
                    Prefs.setChat(this, id, "手动填写")
                    Prefs.addLog(this, "手动设置群 ID：${id.takeLast(8)}")
                    refreshStatus()
                    refreshLog()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ================= 小工具 =================

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun hint(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(Color.parseColor("#646A73"))
        setPadding(0, dp(8), 0, 0)
    }

    private fun section(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#3370FF"))
        setPadding(0, dp(22), 0, dp(6))
    }

    private fun button(t: String, onClick: () -> Unit): Button = Button(this).apply {
        text = t
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) }
    }
}
