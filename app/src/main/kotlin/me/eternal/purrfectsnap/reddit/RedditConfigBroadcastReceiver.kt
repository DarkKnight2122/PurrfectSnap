package me.eternal.purrfectsnap.reddit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.eternal.purrfectsnap.SharedContextHolder
import me.eternal.purrfectsnap.common.Constants

class RedditConfigBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Constants.REDDIT_CONFIG_REQUEST_ACTION) return

        runCatching {
            val remoteContext = SharedContextHolder.remote(context)
            remoteContext.mirrorRedditFeaturePrefs()
        }
    }
}
