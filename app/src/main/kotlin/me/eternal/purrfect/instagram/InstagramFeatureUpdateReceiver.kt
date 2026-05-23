package me.eternal.purrfect.instagram

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.eternal.purrfect.SharedContextHolder
import me.eternal.purrfect.common.Constants
import me.eternal.purrfect.common.config.ConfigContainer

class InstagramFeatureUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Constants.INSTAGRAM_FEATURE_PREF_UPDATE_ACTION) return
        val key = intent.getStringExtra(Constants.INSTAGRAM_FEATURE_PREF_KEY_EXTRA)?.takeIf { it.isNotBlank() } ?: return
        val isString = intent.getBooleanExtra(Constants.INSTAGRAM_FEATURE_PREF_IS_STRING_EXTRA, false)

        runCatching {
            val remoteContext = SharedContextHolder.remote(context)
            val value: Any = if (isString) {
                intent.getStringExtra(Constants.INSTAGRAM_FEATURE_PREF_STRING_EXTRA).orEmpty()
            } else {
                intent.getBooleanExtra(Constants.INSTAGRAM_FEATURE_PREF_BOOLEAN_EXTRA, false)
            }

            if (setProperty(remoteContext.config.root.instagram, key, value)) {
                remoteContext.config.writeConfig()
                remoteContext.mirrorInstagramFeaturePrefs()
            }
        }
    }

    private fun setProperty(container: ConfigContainer, key: String, value: Any): Boolean {
        container.properties.entries.firstOrNull { it.key.name == key }?.let { (_, propertyValue) ->
            propertyValue.setAny(value)
            return true
        }

        container.properties.values.forEach { propertyValue ->
            val child = propertyValue.getNullable() as? ConfigContainer ?: return@forEach
            if (setProperty(child, key, value)) return true
        }
        return false
    }
}
