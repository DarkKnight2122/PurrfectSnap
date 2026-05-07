package me.eternal.purrfect.core.whatsapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.os.SystemClock
import de.robv.android.xposed.XposedBridge
import me.eternal.purrfect.bridge.BridgeInterface
import me.eternal.purrfect.common.BuildConfig
import me.eternal.purrfect.common.Constants
import me.eternal.purrfect.core.logger.CoreLogger
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class WhatsAppFeatureState(
    val hideChannelRecommendations: Boolean = false,
    val hideTypingIndicators: Boolean = false,
    val hideRecordingAudio: Boolean = false,
    val hideViewOnceSeen: Boolean = false,
    val hideDelivered: Boolean = false,
    val hideAudioSeen: Boolean = false,
    val unlimitedViewOnce: Boolean = false,
    val hideBlueTicksGroups: Boolean = false,
    val hideBlueTicks: Boolean = false,
    val showDeletedMessages: Boolean = false,
    val source: String = "unavailable"
) {
    companion object {
        private const val TAG = WhatsAppChannelHooks.TAG
        private const val PREFS_NAME = "whatsapp_features"
        private const val KEY_HIDE_CHANNEL_RECOMMENDATIONS = "hide_channel_recommendations"
        private const val KEY_HIDE_TYPING_INDICATORS = "hide_typing_indicators"
        private const val KEY_HIDE_RECORDING_AUDIO = "hide_recording_audio"
        private const val KEY_HIDE_VIEW_ONCE_SEEN = "hide_view_once_seen"
        private const val KEY_HIDE_DELIVERED = "hide_delivered"
        private const val KEY_HIDE_AUDIO_SEEN = "hide_audio_seen"
        private const val KEY_UNLIMITED_VIEW_ONCE = "unlimited_view_once"
        private const val KEY_HIDE_BLUE_TICKS_GROUPS = "hide_blue_ticks_groups"
        private const val KEY_HIDE_BLUE_TICKS = "hide_blue_ticks"
        private const val KEY_SHOW_DELETED_MESSAGES = "show_deleted_messages"
        private const val PROVIDER_AUTHORITY = "me.eternal.purrfect.whatsapp.config"
        private const val PROVIDER_METHOD_GET_FEATURES = "getWhatsAppFeatures"
        private const val WHATSAPP_FEATURES_FILE = "files/whatsapp_features.json"
        private const val WHATSAPP_EXTERNAL_FEATURES_FILE = "whatsapp_features.json"
        private val unavailableLogged = AtomicBoolean(false)

        fun load(androidContext: Context): WhatsAppFeatureState {
            WhatsAppFeatureStateStore.current.takeIf { it.source != "unavailable" }?.let { return it }
            return loadFromExternalJson()
                ?: loadFromProvider(androidContext)
                ?: loadFromBridge(androidContext)
                ?: loadFromXposedPrefs()
                ?: loadFromWorldReadablePrefs()
                ?: WhatsAppFeatureState().also {
                    if (unavailableLogged.compareAndSet(false, true)) {
                        log("WhatsApp feature state unavailable; waiting for broadcast config")
                    }
                }
        }

        fun loadForEarlyHooks(): WhatsAppFeatureState? {
            WhatsAppFeatureStateStore.current.takeIf { it.source != "unavailable" }?.let { return it }
            return loadFromExternalJson()
                ?: loadFromXposedPrefs()
                ?: loadFromWorldReadablePrefs()
        }

        fun loadAsync(androidContext: Context) {
            Thread {
                repeat(8) { attempt ->
                    if (WhatsAppFeatureStateStore.current.source != "unavailable") return@Thread
                    val state = load(androidContext)
                    if (state.source != "unavailable") {
                        WhatsAppFeatureStateStore.update(state)
                        return@Thread
                    }
                    if (WhatsAppFeatureStateStore.current.source == "unavailable") {
                        WhatsAppFeatureStateStore.update(state)
                    }
                    SystemClock.sleep(1_500L + attempt * 500L)
                }
            }.apply {
                name = "PurrfectWhatsAppConfig"
                isDaemon = true
                start()
            }
        }

        private fun loadFromProvider(androidContext: Context): WhatsAppFeatureState? {
            return runCatching {
                val resolverContext = moduleContext(androidContext) ?: androidContext
                val result = resolverContext.contentResolver.call(
                    Uri.parse("content://$PROVIDER_AUTHORITY"),
                    PROVIDER_METHOD_GET_FEATURES,
                    null,
                    null
                ) ?: return null

                val source = "provider:${result.getString("source", "unknown")}"
                val json = result.getString("json")
                if (!json.isNullOrBlank()) {
                    fromJson(json, source).also { logLoadedState(it) }
                } else {
                    fromBooleanLookup(source) { key, defaultValue ->
                        result.getBoolean(key, defaultValue)
                    }.also { logLoadedState(it) }
                }
            }.onFailure { throwable ->
                log(
                    "WhatsApp config provider unavailable: " +
                        "${throwable.javaClass.simpleName}: ${throwable.message}"
                )
            }.getOrNull()
        }

        private fun loadFromBridge(androidContext: Context): WhatsAppFeatureState? {
            val bridgeContext = moduleContext(androidContext) ?: androidContext
            var service: BridgeInterface? = null
            val latch = CountDownLatch(1)
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    service = BridgeInterface.Stub.asInterface(binder)
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    service = null
                }
            }

            return runCatching {
                runCatching {
                    bridgeContext.startActivity(
                        Intent()
                            .setClassName(Constants.MODULE_PACKAGE_NAME, "me.eternal.purrfect.bridge.ForceStartActivity")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    )
                }.onFailure { throwable ->
                    log("Failed to nudge Purrfect bridge process for WhatsApp config: ${throwable.message}")
                }

                val intent = Intent()
                    .setClassName(Constants.MODULE_PACKAGE_NAME, "me.eternal.purrfect.bridge.BridgeService")
                    .setPackage(Constants.MODULE_PACKAGE_NAME)
                val bound = bridgeContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                if (!bound) return null

                try {
                    if (!latch.await(900L, TimeUnit.MILLISECONDS)) return null
                    val json = service?.whatsAppFeaturesJson ?: return null
                    fromJson(json, "bridge").also { logLoadedState(it) }
                } finally {
                    runCatching { bridgeContext.unbindService(connection) }
                }
            }.onFailure { throwable ->
                log("Failed to load WhatsApp feature state from bridge: ${throwable.message}")
                runCatching { bridgeContext.unbindService(connection) }
            }.getOrNull()
        }

        private fun loadFromExternalJson(): WhatsAppFeatureState? {
            val candidates = listOf(
                File("/storage/emulated/0/Android/media/${BuildConfig.APPLICATION_ID}/$WHATSAPP_EXTERNAL_FEATURES_FILE"),
                File("/sdcard/Android/media/${BuildConfig.APPLICATION_ID}/$WHATSAPP_EXTERNAL_FEATURES_FILE")
            ).distinctBy { it.absolutePath }

            candidates.forEach { file ->
                val state = runCatching {
                    if (!file.exists()) return@runCatching null
                    val json = file.readText(Charsets.UTF_8).trim()
                    if (!json.startsWith("{")) return@runCatching null
                    fromJson(json, "external-json:${file.absolutePath}").also { logLoadedState(it) }
                }.getOrNull()

                if (state != null) return state
            }

            return null
        }

        private fun moduleContext(androidContext: Context): Context? {
            return runCatching {
                androidContext.createPackageContext(
                    Constants.MODULE_PACKAGE_NAME,
                    Context.CONTEXT_IGNORE_SECURITY
                )
            }.getOrNull()
        }

        private fun loadFromXposedPrefs(): WhatsAppFeatureState? {
            return runCatching {
                val prefsClass = Class.forName("de.robv.android.xposed.XSharedPreferences")
                val prefs = prefsClass
                    .getConstructor(String::class.java, String::class.java)
                    .newInstance(BuildConfig.APPLICATION_ID, PREFS_NAME)

                runCatching { prefsClass.getMethod("makeWorldReadable").invoke(prefs) }
                runCatching { prefsClass.getMethod("reload").invoke(prefs) }

                val fileMethod = prefsClass.methods.firstOrNull { it.name == "getFile" && it.parameterTypes.isEmpty() }
                val file = fileMethod?.invoke(prefs)
                val exists = file?.javaClass?.getMethod("exists")?.invoke(file) as? Boolean
                if (exists == false) return null

                val getBoolean = prefsClass.getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                fromBooleanLookup("XSharedPreferences") { key, defaultValue ->
                    getBoolean.invoke(prefs, key, defaultValue) as Boolean
                }.also { logLoadedState(it) }
            }.getOrNull()
        }

        private fun loadFromWorldReadablePrefs(): WhatsAppFeatureState? {
            val candidates = listOf(
                File("/data/user/0/${BuildConfig.APPLICATION_ID}/shared_prefs/$PREFS_NAME.xml"),
                File("/data/data/${BuildConfig.APPLICATION_ID}/shared_prefs/$PREFS_NAME.xml")
            ).distinctBy { it.absolutePath }

            candidates.forEach { file ->
                val state = runCatching {
                    if (!file.exists()) return@runCatching null
                    val values = parseBooleanPrefs(file)
                    fromBooleanLookup("world-readable:${file.absolutePath}") { key, defaultValue ->
                        values[key] ?: defaultValue
                    }.also { logLoadedState(it) }
                }.getOrNull()

                if (state != null) return state
            }

            return null
        }

        private fun parseBooleanPrefs(file: File): Map<String, Boolean> {
            val result = mutableMapOf<String, Boolean>()
            file.inputStream().use { input ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(input, "utf-8")

                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "boolean") {
                        val name = parser.getAttributeValue(null, "name")
                        val value = parser.getAttributeValue(null, "value")
                        if (name != null && value != null) {
                            result[name] = value.toBooleanStrictOrNull() ?: false
                        }
                    }
                    event = parser.next()
                }
            }
            return result
        }

        fun fromJson(json: String, source: String): WhatsAppFeatureState {
            val obj = JSONObject(json)
            return fromBooleanLookup(source) { key, defaultValue ->
                obj.optBoolean(key, defaultValue)
            }
        }

        private fun fromBooleanLookup(
            source: String,
            getBoolean: (key: String, defaultValue: Boolean) -> Boolean
        ): WhatsAppFeatureState {
            return WhatsAppFeatureState(
                hideChannelRecommendations = getBoolean(KEY_HIDE_CHANNEL_RECOMMENDATIONS, false),
                hideTypingIndicators = getBoolean(KEY_HIDE_TYPING_INDICATORS, false),
                hideRecordingAudio = getBoolean(KEY_HIDE_RECORDING_AUDIO, false),
                hideViewOnceSeen = getBoolean(KEY_HIDE_VIEW_ONCE_SEEN, false),
                hideDelivered = getBoolean(KEY_HIDE_DELIVERED, false),
                hideAudioSeen = getBoolean(KEY_HIDE_AUDIO_SEEN, false),
                unlimitedViewOnce = getBoolean(KEY_UNLIMITED_VIEW_ONCE, false),
                hideBlueTicksGroups = getBoolean(KEY_HIDE_BLUE_TICKS_GROUPS, false),
                hideBlueTicks = getBoolean(KEY_HIDE_BLUE_TICKS, false),
                showDeletedMessages = getBoolean(KEY_SHOW_DELETED_MESSAGES, false),
                source = source
            )
        }

        private fun logLoadedState(state: WhatsAppFeatureState) {
            log(
                "WhatsApp feature state loaded from ${state.source}: " +
                    "hideChannelRecommendations=${state.hideChannelRecommendations}, " +
                    "hideTypingIndicators=${state.hideTypingIndicators}, " +
                    "hideRecordingAudio=${state.hideRecordingAudio}, " +
                    "hideViewOnceSeen=${state.hideViewOnceSeen}, " +
                    "hideDelivered=${state.hideDelivered}, " +
                    "hideAudioSeen=${state.hideAudioSeen}, " +
                    "unlimitedViewOnce=${state.unlimitedViewOnce}, " +
                    "hideBlueTicksGroups=${state.hideBlueTicksGroups}, " +
                    "hideBlueTicks=${state.hideBlueTicks}, " +
                    "showDeletedMessages=${state.showDeletedMessages}"
            )
        }

        private fun log(message: String) {
            XposedBridge.log("[$TAG] $message")
            CoreLogger.xposedLog(message, TAG)
            WhatsAppAppLogWriter.info(null, TAG, message)
        }
    }
}

object WhatsAppFeatureStateStore {
    @Volatile
    var current: WhatsAppFeatureState = WhatsAppFeatureState()
        private set

    fun update(state: WhatsAppFeatureState) {
        current = state
    }
}
