package com.sapos.caller

import android.content.Context
import android.content.Intent
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object SaPosClient {
    private const val PREF_NAME = "sapos_caller_prefs"
    const val ACTION_LOG_UPDATE = "com.sapos.caller.LOG_UPDATE"

    private val executor = Executors.newSingleThreadExecutor()
    private val logs = mutableListOf<String>()

    fun getHost(ctx: Context): String =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString("host", "192.168.1.50") ?: "192.168.1.50"

    fun getPort(ctx: Context): String =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString("port", "3000") ?: "3000"

    fun isCallerEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean("caller_enabled", true)

    fun isWaEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean("wa_enabled", true)

    fun saveSettings(ctx: Context, host: String, port: String, callerOn: Boolean, waOn: Boolean) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString("host", host.trim())
            .putString("port", port.trim().ifEmpty { "3000" })
            .putBoolean("caller_enabled", callerOn)
            .putBoolean("wa_enabled", waOn)
            .apply()
    }

    fun getBaseUrl(ctx: Context): String {
        val h = getHost(ctx).removePrefix("http://").removePrefix("https://").trimEnd('/')
        val p = getPort(ctx).trim().ifEmpty { "3000" }
        return "http://$h:$p"
    }

    fun addLog(ctx: Context, msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale("tr", "TR")).format(Date())
        synchronized(logs) {
            logs.add(0, "[$time] $msg")
            if (logs.size > 25) logs.removeAt(logs.lastIndex)
        }
        ctx.sendBroadcast(Intent(ACTION_LOG_UPDATE).setPackage(ctx.packageName))
    }

    fun getLogsText(): String = synchronized(logs) {
        if (logs.isEmpty()) "Henüz işlem kaydı yok." else logs.joinToString("
")
    }

    fun sendCallerId(ctx: Context, phone: String, onDone: ((Boolean, String) -> Unit)? = null) {
        val clean = phone.trim()
        if (clean.isEmpty()) return
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val urlStr = "${getBaseUrl(ctx)}/api/caller-id?phone=$encoded"
        fireGet(ctx, urlStr, "ARAMA ($clean)", onDone)
    }

    fun sendWhatsApp(ctx: Context, sender: String, text: String, onDone: ((Boolean, String) -> Unit)? = null) {
        val encSender = URLEncoder.encode(sender.trim(), "UTF-8")
        val encText = URLEncoder.encode(text.trim(), "UTF-8")
        val urlStr = "${getBaseUrl(ctx)}/api/whatsapp-incoming?sender=$encSender&text=$encText"
        fireGet(ctx, urlStr, "WHATSAPP ($sender)", onDone)
    }

    fun testStatus(ctx: Context, onDone: (Boolean, String) -> Unit) {
        val urlStr = "${getBaseUrl(ctx)}/api/status"
        fireGet(ctx, urlStr, "KASA BAĞLANTI TESTİ", onDone)
    }

    private fun fireGet(ctx: Context, urlStr: String, label: String, onDone: ((Boolean, String) -> Unit)?) {
        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                val code = conn.responseCode
                val ok = code in 200..299
                val msg = if (ok) "OK ($code) -> $label" else "HATA ($code) -> $label"
                addLog(ctx, msg)
                onDone?.invoke(ok, msg)
            } catch (e: Exception) {
                val msg = "BAĞLANTI HATASI -> $label (${e.message ?: "Erişilemedi"})"
                addLog(ctx, msg)
                onDone?.invoke(false, msg)
            } finally {
                conn?.disconnect()
            }
        }
    }
}
