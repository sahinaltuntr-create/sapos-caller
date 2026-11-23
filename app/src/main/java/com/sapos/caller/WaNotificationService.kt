package com.sapos.caller

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class WaNotificationService : NotificationListenerService() {

    companion object {
        private val ALLOWED_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
        )
        private var lastHash = ""
        private var lastTs = 0L
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!SaPosClient.isWaEnabled(applicationContext)) return
        if (sbn.packageName !in ALLOWED_PACKAGES) return

        val notif = sbn.notification ?: return
        // Grup özet bildirimlerini atla (çift kayıt önleme)
        if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = notif.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""

        if (title.isEmpty() || text.isEmpty()) return
        if (title.equals("WhatsApp", ignoreCase = true) || title.equals("WhatsApp Business", ignoreCase = true)) return
        if (text.contains("yeni mesaj", ignoreCase = true) || text.contains("new messages", ignoreCase = true)) return
        if (text.contains("mesajlar kontrol ediliyor", ignoreCase = true)) return

        val sig = "$title|$text"
        val now = System.currentTimeMillis()
        if (sig == lastHash && (now - lastTs) < 4000L) return
        lastHash = sig
        lastTs = now

        SaPosClient.sendWhatsApp(applicationContext, title, text)
    }
}
