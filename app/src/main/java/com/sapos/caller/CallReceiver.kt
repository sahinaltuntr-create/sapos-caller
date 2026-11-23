package com.sapos.caller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {

    companion object {
        private var lastPhone: String = ""
        private var lastTime: Long = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (!SaPosClient.isCallerEnabled(context)) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state != TelephonyManager.EXTRA_STATE_RINGING) return

        @Suppress("DEPRECATION")
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return
        val clean = incomingNumber.trim()
        if (clean.isEmpty()) return

        val now = System.currentTimeMillis()
        if (clean == lastPhone && (now - lastTime) < 6000L) return
        lastPhone = clean
        lastTime = now

        SaPosClient.sendCallerId(context, clean)
    }
}
