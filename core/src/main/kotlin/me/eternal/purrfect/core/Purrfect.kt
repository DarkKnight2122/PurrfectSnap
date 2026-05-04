package me.eternal.purrfect.core

import me.eternal.purrfect.common.scripting.JSModule
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.runtime.Composable
import java.lang.reflect.Method
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.eternal.purrfect.bridge.ConfigStateListener
import me.eternal.purrfect.bridge.SyncCallback
import me.eternal.purrfect.common.Constants
import me.eternal.purrfect.common.ReceiversConfig
import me.eternal.purrfect.common.action.EnumAction
import me.eternal.purrfect.common.data.FriendLinkType
import me.eternal.purrfect.common.bridge.FileHandleScope
import me.eternal.purrfect.common.bridge.InternalFileHandleType
import me.eternal.purrfect.common.bridge.toWrapper
import me.eternal.purrfect.common.database.impl.FriendFeedEntry
import me.eternal.purrfect.common.database.impl.FriendInfo
import me.eternal.purrfect.common.data.FriendStreaks
import me.eternal.purrfect.common.data.MessagingFriendInfo
import me.eternal.purrfect.common.data.MessagingGroupInfo
import me.eternal.purrfect.common.util.toSerialized
import me.eternal.purrfect.core.bridge.BridgeClient
import me.eternal.purrfect.core.data.SnapClassCache
import me.eternal.purrfect.core.event.events.impl.NativeUnaryCallEvent
import me.eternal.purrfect.core.event.events.impl.SnapWidgetBroadcastReceiveEvent
import me.eternal.purrfect.core.ui.InAppOverlay
import me.eternal.purrfect.core.ui.CustomComposable
import me.eternal.purrfect.core.util.LSPatchUpdater
import me.eternal.purrfect.core.util.hook.HookAdapter
import me.eternal.purrfect.core.util.hook.HookStage
import me.eternal.purrfect.core.util.hook.findRestrictedMethod
import me.eternal.purrfect.core.util.hook.hook
import me.eternal.purrfect.mapper.impl.PlatformClientAttestationMapper
import me.eternal.purrfect.common.ui.components.AphelionFriendMutationToast
import kotlin.reflect.KClass
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis


class Purrfect {
    companion object {
        lateinit var classLoader: ClassLoader
            private set
        val classCache by lazy {
            SnapClassCache(classLoader)
        }
    }
    private lateinit var appContext: ModContext
    private var isBridgeInitialized = false
    private var android9ValdiBindDisabled = false
    private var android9ValdiBindDisableLogged = false
    private val nativeLateInitTriggered = java.util.concurrent.atomic.AtomicBoolean(false)
    private var syncCallback: SyncCallback? = null

    private fun FriendInfo.isCurrentSocialFriend(): Boolean {
        return !userId.isNullOrBlank() &&
            FriendLinkType.fromValue(friendLinkType) == FriendLinkType.MUTUAL &&
            addedTimestamp > 0L
    }

    private fun FriendFeedEntry.toMessagingGroupInfo(): MessagingGroupInfo? {
        if (conversationType != 1 || participantsSize <= 0) return null
        val conversationId = key?.takeIf { it.isNotBlank() } ?: return null
        return MessagingGroupInfo(
            conversationId = conversationId,
            name = feedDisplayName ?: "",
            participantsCount = participantsSize
        )
    }

    private fun hookMainActivity(methodName: String, stage: HookStage = HookStage.AFTER, block: Activity.(param: HookAdapter) -> Unit) {
        Activity::class.java.hook(methodName, stage, { isBridgeInitialized }) { param ->
            val activity = param.thisObject() as Activity
            if (!activity.packageName.equals(Constants.SNAPCHAT_PACKAGE_NAME)) return@hook
            block(activity, param)
        }
    }

    fun init(context: Context) {
        appContext = ModContext(
            androidContext = context.also { classLoader = it.classLoader },
            purrfect = this
        )
        appContext.apply {
            bridgeClient = BridgeClient(this)
            initConfigListener()
            bridgeClient.addOnConnectedCallback {
                bridgeClient.registerMessagingBridge(messagingBridge)
                coroutineScope.launch {
                    runCatching {
                        syncRemote()
                    }.onFailure {
                        log.error("Failed to sync remote", it)
                    }
                }
            }
        }

        runBlocking {
            var throwable: Throwable? = null
            val canLoad = appContext.bridgeClient.connect { throwable = it }
            if (canLoad == null) {
                InAppOverlay.showCrashOverlay(
                    buildString {
                        append("Snapchat timed out while trying to connect to Purrfect\n\n")
                        append("Make sure you:\n")
                        append(" - Have installed the latest Purrfect version (https://github.com/particle-box/Purrfect)\n")
                        append(" - Disabled battery optimizations\n")
                        append(" - Excluded Purrfect and Snapchat in HideMyApplist")
                    },
                    throwable
                )
                appContext.logCritical("Cannot connect to the Purrfect app")
                return@runBlocking
            }
            if (!canLoad) exitProcess(1)
            runCatching {
                LSPatchUpdater.onBridgeConnected(appContext)
            }.onFailure {
                appContext.log.error("Failed to init LSPatchUpdater", it)
            }
            jetpackComposeResourceHook()
            runCatching {
                measureTimeMillis {
                    init(this)
                }.also {
                    appContext.log.verbose("init took ${it}ms")
                }

                hookMainActivity("onPostCreate") {
                    appContext.mainActivity = this
                    if (!appContext.mappings.isMappingsLoaded) return@hookMainActivity
                    appContext.isMainActivityPaused = false
                    onActivityCreate(this)
                    appContext.actionManager.onNewIntent(intent)
                }

                hookMainActivity("onPause") {
                    appContext.bridgeClient.closeOverlay()
                    appContext.isMainActivityPaused = true
                }

                hookMainActivity("onNewIntent") { param ->
                    appContext.actionManager.onNewIntent(param.argNullable(0))
                }

                hookMainActivity("onResume") {
                    appContext.mainActivity = this
                    if (appContext.isMainActivityPaused.also {
                        appContext.isMainActivityPaused = false
                    }) {
                        appContext.reloadConfig()
                        appContext.executeAsync {
                            syncRemote()
                        }
                    }
                }
            }.onSuccess {
                isBridgeInitialized = true
            }.onFailure {
                appContext.logCritical("Failed to initialize bridge", it)
                InAppOverlay.showCrashOverlay("Purrfect failed to initialize. Please check logs for more details.", it)
            }
        }
    }

    private fun init(scope: CoroutineScope) {
        with(appContext) {
            Thread::class.java.hook("dispatchUncaughtException", HookStage.BEFORE) { param ->
                runCatching {
                    val throwable = param.argNullable(0) ?: Throwable()
                    logCritical(null, throwable)
                }
            }

            reloadConfig()
            installAndroid9ValdiBindGuard()
            initNative()
            initWidgetListener()
            scope.launch(Dispatchers.IO) {
                translation.userLocale = getConfigLocale()
                translation.load()
            }

            database.init()
            eventDispatcher.init()
            userInterface.init()
            
            // Check mappings status with detailed logging
            log.verbose("Checking mappings status...")
            log.verbose("Mappings loaded: ${mappings.isMappingsLoaded}")
            log.verbose("Mappings outdated: ${mappings.isMappingsOutdated()}")
            
            //if mappings aren't loaded, we can't initialize features
            if (!mappings.isMappingsLoaded) {
                log.warn("Mappings not loaded, skipping features initialization")
                log.warn("Mappings file exists: ${mappings.exists()}")
                log.warn("Generated build number: ${mappings.getGeneratedBuildNumber()}")
                // Trigger auto-generation by launching manager app
                triggerMappingsGeneration()
                return
            }
            
            // Also check if mappings are outdated and trigger regeneration
            if (mappings.isMappingsOutdated()) {
                log.warn("Mappings are outdated, triggering auto-generation check")
                triggerMappingsGeneration()
            }
            
            log.verbose("Initializing features...")
            runCatching {
                features.init()
                
                // Wire up the premium friend mutation toast provider
                features.get(me.eternal.purrfect.core.features.impl.FriendMutationObserver::class)?.let { observer ->
                    observer.aphelionToastProvider = { icon, text, bitmojiUrl, onDismiss ->
                        lateinit var composable: CustomComposable
                        composable = @Composable {
                            AphelionFriendMutationToast(
                                icon = icon,
                                text = text,
                                bitmojiUrl = bitmojiUrl,
                                onDismiss = {
                                    inAppOverlay.removeCustomComposable(composable)
                                    onDismiss()
                                }
                            )
                        }
                        inAppOverlay.addCustomComposable(composable)
                    }
                }
                
                log.verbose("Features initialized successfully")
            }.onFailure { throwable ->
                log.error("Failed to initialize features", throwable)
                longToast(appContext.translation["toast_init_features_failed"])
                // Continue with other initializations even if features fail
            }
            
            log.verbose("Initializing script runtime...")
            runCatching {
                scriptRuntime.init()
                scriptRuntime.eachModule { callFunction("module.onSnapApplicationLoad", androidContext) }
                log.verbose("Script runtime initialized successfully")
            }.onFailure { throwable ->
                log.error("Failed to initialize script runtime", throwable)
                longToast(appContext.translation["toast_init_script_runtime_failed"])
            }
        }
    }


    private fun triggerMappingsGeneration() {
        runCatching {
            val autoGen = appContext.bridgeClient.getDebugProp("auto_generate_mappings", "true")
            if (autoGen != "true") {
                appContext.log.verbose("Auto-generation disabled via debug prop")
                return
            }
            
            val intent = Intent().apply {
                setClassName(Constants.MODULE_PACKAGE_NAME, "${Constants.MODULE_PACKAGE_NAME}.ui.setup.SetupActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("requirements", 4) // Requirements.MAPPINGS = 4
            }
            appContext.androidContext.startActivity(intent)
            appContext.log.verbose("Triggered mappings generation via SetupActivity")
        }.onFailure {
            appContext.log.verbose("Could not trigger mappings generation: ${it.message}")
        }
    }

    private fun onActivityCreate(activity: Activity) {
        measureTimeMillis {
            with(appContext) {
                features.onActivityCreate(activity)
                inAppOverlay.onActivityCreate(activity)
                scriptRuntime.eachModule { callFunction("module.onSnapMainActivityCreate", activity) }
                actionManager.onActivityCreate()
            }
        }.also { time ->
            appContext.log.verbose("onActivityCreate took $time")
        }
    }

    private fun installAndroid9ValdiBindGuard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return
        val valdiNativeBridge = runCatching {
            classLoader.loadClass("com.snapchat.client.valdi.NativeBridge")
        }.getOrNull()
        val targetClasses = listOf("AXj", "NZj", "MZj")
            .mapNotNull { className ->
                runCatching { classLoader.loadClass(className) }.getOrNull()
            }
            .distinct()
        if (targetClasses.isEmpty()) return
        fun defaultReturn(type: Class<*>): Any? = when (type) {
            Boolean::class.javaPrimitiveType -> false
            Byte::class.javaPrimitiveType -> 0.toByte()
            Short::class.javaPrimitiveType -> 0.toShort()
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Float::class.javaPrimitiveType -> 0f
            Double::class.javaPrimitiveType -> 0.0
            Char::class.javaPrimitiveType -> 0.toChar()
            Void.TYPE -> null
            else -> null
        }

        fun installGuard(targetClass: Class<*>, methodName: String) {
            targetClass.hook(methodName, HookStage.BEFORE) { param ->
                val method = param.method() as? Method ?: return@hook
                if (android9ValdiBindDisabled) {
                    if (!android9ValdiBindDisableLogged) {
                        android9ValdiBindDisableLogged = true
                        appContext.log.warn("Skipping Valdi bind on Android 9 due to missing native impl")
                    }
                    param.setResult(defaultReturn(method.returnType))
                    return@hook
                }
                runCatching {
                    param.invokeOriginal()
                }.onSuccess { result ->
                    param.setResult(result)
                }.onFailure { throwable ->
                    val rootCause = throwable.cause ?: throwable
                    val message = rootCause.message ?: throwable.message
                    val isMissingNativeImpl = rootCause is UnsatisfiedLinkError &&
                        message?.contains("NativeBridge.createContext") == true
                    val isValdiContextNpe = rootCause is NullPointerException &&
                        message?.contains("ValdiContext") == true
                    if (isMissingNativeImpl || isValdiContextNpe) {
                        android9ValdiBindDisabled = true
                        if (!android9ValdiBindDisableLogged) {
                            android9ValdiBindDisableLogged = true
                            appContext.log.warn("Skipping Valdi bind on Android 9 due to missing native impl")
                            if (isMissingNativeImpl) {
                                appContext.log.error("Android 9 Valdi NativeBridge.createContext missing native impl", rootCause)
                            }
                        }
                        param.setResult(defaultReturn(method.returnType))
                        return@hook
                    }
                    appContext.log.error("Android 9 Valdi bind hook failed", throwable)
                    param.setResult(defaultReturn(method.returnType))
                }
            }
        }

        targetClasses.forEach { targetClass ->
            installGuard(targetClass, "f")
            installGuard(targetClass, "g2")
            installGuard(targetClass, "n2")
            installGuard(targetClass, "d")
        }

        valdiNativeBridge?.hook("createContext", HookStage.AFTER) { param ->
            val throwable = param.throwable() as? UnsatisfiedLinkError ?: return@hook
            android9ValdiBindDisabled = true
            if (!android9ValdiBindDisableLogged) {
                android9ValdiBindDisableLogged = true
                appContext.log.warn("Skipping Valdi bind on Android 9 due to missing native impl")
                appContext.log.error("Android 9 Valdi NativeBridge.createContext missing native impl", throwable)
            }
            param.setResult(null)
        }
    }

    private fun initNative() {
        val nativeSigCacheFileHandle = appContext.fileHandlerManager.getFileHandle(FileHandleScope.INTERNAL.key, InternalFileHandleType.NATIVE_SIG_CACHE.key).toWrapper()

        val oldSignatureCache = nativeSigCacheFileHandle.readBytes()
            .takeIf {
                it.isNotEmpty()
            }?.toString(Charsets.UTF_8)?.also {
                appContext.native.signatureCache = it
            }

        val lateInit = appContext.native.initOnce {
            nativeUnaryCallCallback = { request ->
                appContext.event.post(NativeUnaryCallEvent(request.uri, request.buffer)) {
                    request.buffer = buffer
                    request.canceled = canceled
                }
            }
            appContext.reloadNativeConfig()
        }.let { init ->
            {
                init()
                appContext.native.signatureCache.takeIf { it != oldSignatureCache }?.let {
                    appContext.log.verbose("new signature cache $it")
                    nativeSigCacheFileHandle.writeBytes(it.toByteArray(Charsets.UTF_8))
                }
            }
        }

        SecurityFeatures(appContext).init()

        Runtime::class.java.findRestrictedMethod {
            it.name == "loadLibrary0" && it.parameterTypes.contentEquals(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) arrayOf(Class::class.java, String::class.java)
                else arrayOf(ClassLoader::class.java, String::class.java)
            )
        }!!.apply {
            if (appContext.disablePlugin) {
                hook(HookStage.BEFORE) { param ->
                    if (param.arg<String>(1) != "scplugin") return@hook
                    param.setResult(null)
                    appContext.log.verbose("skipped scplugin load")

                    appContext.mappings.useMapper(PlatformClientAttestationMapper::class) {
                        pluginNativeClass.getAsClass()?.methods?.filter {
                            it.declaringClass == pluginNativeClass.getAsClass()
                        }?.forEach { method ->
                            method.hook(HookStage.BEFORE) {
                                appContext.log.error("Calling $method", Throwable())
                                it.setResult(null)
                                runCatching { exitProcess(139) }
                                runCatching { Thread.sleep(Long.MAX_VALUE) }
                            }
                        } ?: error("Failed to get pluginNativeClass class")
                    }
                }
            }

            lateinit var unhook: () -> Unit
            hook(HookStage.AFTER) { param ->
                if (param.arg<String>(1) != "client") return@hook
                if (!nativeLateInitTriggered.compareAndSet(false, true)) return@hook
                unhook()
                lateInit()
            }.also { unhook = { it.unhook() } }
        }
    }

    private fun initConfigListener() {
        val tasks = linkedSetOf<() -> Unit>()
        hookMainActivity("onResume") {
            tasks.forEach { it() }
        }

        fun runLater(task: () -> Unit) {
            if (appContext.isMainActivityPaused) {
                tasks.add(task)
            } else {
                task()
            }
        }

        appContext.bridgeClient.addOnConnectedCallback {
            appContext.bridgeClient.registerConfigStateListener(object: ConfigStateListener.Stub() {
                override fun onConfigChanged() {
                    appContext.log.verbose("onConfigChanged")
                    appContext.reloadConfig()
                }

                override fun onRestartRequired() {
                    appContext.log.verbose("onRestartRequired")
                    runLater {
                        appContext.log.verbose("softRestart")
                        appContext.softRestartApp(saveSettings = false)
                    }
                }

                override fun onCleanCacheRequired() {
                    appContext.log.verbose("onCleanCacheRequired")
                    tasks.clear()
                    runLater {
                        appContext.log.verbose("cleanCache")
                        appContext.actionManager.execute(EnumAction.CLEAN_CACHE)
                    }
                }
            })
        }
    }

    private fun initWidgetListener() {
        appContext.event.subscribe(SnapWidgetBroadcastReceiveEvent::class) { event ->
            if (event.action != ReceiversConfig.BRIDGE_SYNC_ACTION) return@subscribe
            event.canceled = true
            val feedEntries = appContext.database.getFeedEntries(Int.MAX_VALUE)

            val groups = feedEntries
                .asSequence()
                .mapNotNull { it.toMessagingGroupInfo() }
                .distinctBy { it.conversationId }
                .toList()

            val friends = appContext.database.getAllFriends()
                .asSequence()
                .filter { friend -> friend.isCurrentSocialFriend() }
                .mapNotNull { friend ->
                    val userId = friend.userId ?: return@mapNotNull null
                    MessagingFriendInfo(
                        userId = userId,
                        dmConversationId = appContext.database.getDMConversationId(userId),
                        displayName = friend.displayName,
                        mutableUsername = friend.mutableUsername ?: friend.usernameForSorting ?: return@mapNotNull null,
                        bitmojiId = friend.bitmojiAvatarId,
                        selfieId = friend.bitmojiSelfieId,
                        streaks = null
                    )
                }
                .toList()

            appContext.bridgeClient.passGroupsAndFriends(groups, friends)
        }
    }

    private fun syncRemote() {
        if (!appContext.isLoggedIn()) {
            syncCallback = null
            return
        }
        
        val myUserId = appContext.database.myUserId
        val streakEntries = appContext.database.getFeedEntries(Int.MAX_VALUE, whereClause = "streak_count IS NOT NULL AND streak_count > 0")
            .associateBy { entry -> (entry.friendUserId ?: entry.participants?.firstOrNull { it != myUserId }) }
            .filter { it.key != null }

        syncCallback = object : SyncCallback.Stub() {
            override fun syncFriend(uuid: String): String? {
                return appContext.database.getFriendInfo(uuid)?.let {
                    if (!it.isCurrentSocialFriend()) return@let null
                    MessagingFriendInfo(
                        userId = it.userId!!,
                        dmConversationId = appContext.database.getDMConversationId(it.userId!!),
                        displayName = it.displayName,
                        mutableUsername = it.mutableUsername!!,
                        bitmojiId = it.bitmojiAvatarId,
                        selfieId = it.bitmojiSelfieId,
                        streaks = if (it.streakLength > 0) {
                            FriendStreaks(
                                expirationTimestamp = it.streakExpirationTimestamp,
                                length = it.streakLength
                            )
                        } else streakEntries[it.userId]?.let {
                            FriendStreaks(
                                expirationTimestamp = it.streakExpirationTimestampMs ?: return@let null,
                                length = it.streakCount ?: return@let null
                            )
                        }
                    ).toSerialized()
                }
            }

            override fun syncGroup(uuid: String): String? {
                return appContext.database.getFeedEntryByConversationId(uuid)?.let {
                    MessagingGroupInfo(
                        it.key!!,
                        it.feedDisplayName ?: "",
                        it.participantsSize
                    ).toSerialized()
                }
            }
        }

        appContext.bridgeClient.sync(syncCallback!!)
    }

    private fun jetpackComposeResourceHook() {
        val stringResources = mutableMapOf<Int, String>()
        
        fun loadStrings(className: String) {
            runCatching {
                val clazz = classLoader.loadClass(className)
                clazz.fields.filter {
                    java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType
                }.forEach { field ->
                    stringResources[field.getInt(null)] = field.name
                }
            }
        }
        
        loadStrings("androidx.compose.material3.R\$string")
        loadStrings("androidx.compose.ui.R\$string")

        fun resolveComposeString(key: Int): String? {
            val name = stringResources[key]?.replaceFirst("m3c_", "") ?: return null
            return appContext.translation.getOrNull("material3_strings.${name}") ?: ""
        }

        fun resolveInvalidString(resources: Resources, key: Int): String? {
            val type = runCatching { resources.getResourceTypeName(key) }.getOrNull() ?: return ""
            return if (type == "string") null else ""
        }

        Resources::class.java.getMethod("getString", Int::class.javaPrimitiveType).hook(HookStage.BEFORE) { param ->
            val key = param.arg<Int>(0)
            resolveComposeString(key)?.let { param.setResult(it); return@hook }
            resolveInvalidString(param.thisObject() as Resources, key)?.let { param.setResult(it) }
        }

        Resources::class.java.getMethod("getText", Int::class.javaPrimitiveType).hook(HookStage.BEFORE) { param ->
            val key = param.arg<Int>(0)
            resolveComposeString(key)?.let { param.setResult(it); return@hook }
            resolveInvalidString(param.thisObject() as Resources, key)?.let { param.setResult(it) }
        }
    }
}
