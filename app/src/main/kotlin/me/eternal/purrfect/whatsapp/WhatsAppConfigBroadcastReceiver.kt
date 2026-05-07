package me.eternal.purrfect.whatsapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.eternal.purrfect.SharedContextHolder
import me.eternal.purrfect.common.Constants

class WhatsAppConfigBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Constants.WHATSAPP_CONFIG_REQUEST_ACTION) return

        runCatching {
            val remoteContext = SharedContextHolder.remote(context)
            remoteContext.mirrorWhatsAppFeaturePrefs()
        }
    }
}
