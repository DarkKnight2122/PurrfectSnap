package me.eternal.purrfectsnap.core.bridge


import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import me.eternal.purrfectsnap.bridge.*
import me.eternal.purrfectsnap.bridge.call.CallDownloadSession
import me.eternal.purrfectsnap.bridge.e2ee.E2eeInterface
import me.eternal.purrfectsnap.bridge.location.LocationManager
import me.eternal.purrfectsnap.bridge.logger.LoggerInterface
import me.eternal.purrfectsnap.bridge.logger.TrackerInterface
import me.eternal.purrfectsnap.bridge.task.TaskInterface
import me.eternal.purrfectsnap.bridge.scripting.IScripting
import me.eternal.purrfectsnap.bridge.snapclient.MessagingBridge
import me.eternal.purrfectsnap.bridge.storage.FileHandleManager
import me.eternal.purrfectsnap.common.Constants
import me.eternal.purrfectsnap.common.data.MessagingFriendInfo
import me.eternal.purrfectsnap.common.data.MessagingGroupInfo
import me.eternal.purrfectsnap.common.data.MessagingRuleType
import me.eternal.purrfectsnap.common.data.SocialScope
import me.eternal.purrfectsnap.common.ui.OverlayType
import me.eternal.purrfectsnap.common.util.toSerialized
import me.eternal.purrfectsnap.core.ModContext
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class BridgeClient(
    private val context: ModContext
):  ServiceConnection {
    private var continuation: Continuation<Boolean>? = null
    private val connectSemaphore = Semaphore(permits = 1)
    private val reconnectSemaphore = Semaphore(permits = 1)
    private val serviceStateLock = Any()
    @Volatile
    private var service: BridgeInterface? = null
    @Volatile
    private var serviceBinder: IBinder? = null
    @Volatile
    private var isBound = false
    @Volatile
    private var isHandlingServiceConnection = false
    private val connectionExecutor = Executors.newSingleThreadExecutor()
    private val legacyBindThread = HandlerThread("BridgeClient").apply { start() }
    private val legacyBindHandler by lazy { Handler(legacyBindThread.looper) }

    private val onConnectedCallbacks = mutableListOf<suspend () -> Unit>()
    private var cachePurrfectSnapApkPath: String? = null

    private val serviceDeathRecipient = IBinder.DeathRecipient {
        clearConnectedService()
    }

    private fun clearConnectedServiceLocked() {
        serviceBinder?.let { binder ->
            runCatching { binder.unlinkToDeath(serviceDeathRecipient, 0) }
        }
        serviceBinder = null
        service = null
    }

    private fun clearConnectedService() {
        synchronized(serviceStateLock) {
            clearConnectedServiceLocked()
        }
    }

    private fun attachConnectedService(binder: IBinder): Boolean {
        synchronized(serviceStateLock) {
            clearConnectedServiceLocked()
            serviceBinder = binder
            service = BridgeInterface.Stub.asInterface(binder)
            return runCatching {
                binder.linkToDeath(serviceDeathRecipient, 0)
                true
            }.getOrElse { throwable ->
                Log.w("BridgeClient", "Failed to link bridge death recipient", throwable)
                clearConnectedServiceLocked()
                false
            }
        }
    }

    private fun isServiceAlive(): Boolean {
        val binder = serviceBinder ?: service?.asBinder() ?: return false
        return binder.isBinderAlive && binder.pingBinder()
    }

    private val connectedService: BridgeInterface
        get() {
            val currentService = service ?: throw DeadObjectException()
            val binder = currentService.asBinder()
            if (!binder.isBinderAlive || !binder.pingBinder()) throw DeadObjectException()
            return currentService
        }

    private fun Context.unbindBridgeIfNeeded() {
        if (!isBound) return
        runCatching { unbindService(this@BridgeClient) }.onFailure { throwable ->
            if (throwable !is IllegalArgumentException) throw throwable
        }
        isBound = false
    }

    private fun Context.bindBridge(intent: Intent): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bindService(
                intent,
                Context.BIND_AUTO_CREATE,
                connectionExecutor,
                this@BridgeClient
            )
        } else {
            this::class.java.methods.firstOrNull {
                it.name == "bindServiceAsUser" && it.parameterTypes.size == 5
            }?.invoke(
                this,
                intent,
                this@BridgeClient,
                Context.BIND_AUTO_CREATE,
                legacyBindHandler,
                Process.myUserHandle()
            ) as? Boolean ?: false
        }
    }

    private fun isRecoverableBinderFailure(throwable: Throwable): Boolean {
        return throwable is DeadObjectException ||
            throwable is RemoteException ||
            throwable.cause is DeadObjectException ||
            throwable.cause is RemoteException
    }

    fun addOnConnectedCallback(initNow: Boolean = false, callback: suspend () -> Unit) {
        synchronized(onConnectedCallbacks) {
            onConnectedCallbacks.add(callback)
        }
        initNow.takeIf { it && isServiceAlive() }?.let {
            runBlocking {
                callback()
            }
        }
    }

    private fun resumeContinuation(state: Boolean) {
        runBlocking {
            connectSemaphore.withPermit {
                runCatching { continuation?.resume(state) }
                continuation = null
            }
        }
    }

    suspend fun connect(onFailure: (Throwable) -> Unit): Boolean? {
        if (isServiceAlive()) {
            return true
        }

        val connectionTimeout = 15000L
        val retryDelay = 3000L

        return withTimeoutOrNull(connectionTimeout) {
            val attempts = (connectionTimeout / retryDelay).toInt() + 1
            repeat(attempts) { attempt ->
                val result = withTimeoutOrNull(retryDelay) {
                    suspendCancellableCoroutine { cancellableContinuation ->
                        continuation = cancellableContinuation
                        with(context.androidContext) {
                            //ensure the remote process is running
                            runCatching {
                                startActivity(Intent()
                                    .setClassName(Constants.MODULE_PACKAGE_NAME, "me.eternal.purrfectsnap.bridge.ForceStartActivity")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                                )
                            }

                            runCatching {
                                val intent = Intent()
                                    .setClassName(Constants.MODULE_PACKAGE_NAME, "me.eternal.purrfectsnap.bridge.BridgeService")
                                unbindBridgeIfNeeded()
                                clearConnectedService()
                                if (!bindBridge(intent)) {
                                    throw IllegalStateException("bindService returned false")
                                }
                                isBound = true
                            }.onFailure {
                                onFailure(it)
                                resumeContinuation(false)
                            }
                        }
                    }
                }
                if (result == true) {
                    return@withTimeoutOrNull true
                }
                if (attempt + 1 < attempts) {
                    delay(250L)
                }
            }

            false
        }
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        isHandlingServiceConnection = true
        try {
            if (!attachConnectedService(service)) {
                resumeContinuation(false)
                return
            }

            runBlocking {
                onConnectedCallbacks.forEach {
                    runCatching {
                        it()
                    }.onFailure {
                        context.log.error("Failed to run onConnectedCallback", it)
                    }
                }
            }
            val remoteApkPath = runCatching {
                connectedService.applicationApkPath
            }.getOrElse { throwable ->
                if (isRecoverableBinderFailure(throwable)) {
                    Log.w("BridgeClient", "Bridge died during onServiceConnected initialization", throwable)
                } else {
                    Log.e("BridgeClient", "Bridge initialization failed", throwable)
                }
                clearConnectedService()
                resumeContinuation(false)
                return
            }
            cachePurrfectSnapApkPath = remoteApkPath.also {
                if (cachePurrfectSnapApkPath != null && cachePurrfectSnapApkPath != it) {
                    context.log.verbose("Restarting Snapchat due to PurrfectSnap update")
                    context.softRestartApp()
                    return
                }
            }
            resumeContinuation(true)
        } finally {
            isHandlingServiceConnection = false
        }
    }

    override fun onNullBinding(name: ComponentName) {
        clearConnectedService()
        isBound = false
        resumeContinuation(false)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        clearConnectedService()
        isBound = false
        continuation = null
    }

    override fun onBindingDied(name: ComponentName) {
        clearConnectedService()
        isBound = false
        resumeContinuation(false)
    }

    private fun tryReconnect() {
        runBlocking {
            reconnectSemaphore.withPermit {
                if (isServiceAlive()) return@withPermit
                Log.d("BridgeClient", "service is dead, restarting")
                val canLoad = connect {
                    Log.e("BridgeClient", "connection failed", it)
                }
                if (canLoad != true) {
                    Log.e("BridgeClient", "failed to reconnect to service, result=$canLoad")
                    return@runBlocking
                }
            }
        }
    }

    private fun <T> safeServiceCall(block: () -> T): T {
        return runCatching {
            block()
        }.getOrElse { throwable ->
            if (isRecoverableBinderFailure(throwable)) {
                clearConnectedService()
                if (!isHandlingServiceConnection) {
                    tryReconnect()
                    return@getOrElse runCatching {
                        block()
                    }.getOrElse {
                        Log.e("BridgeClient", "service call failed", it)
                        throw it
                    }
                }
            }
            throw throwable
        }
    }

    fun broadcastLog(tag: String, level: String, message: String) {
        message.chunked(1024 * 256).forEach {
            runCatching {
                connectedService.broadcastLog(tag, level, it)
            }
        }
    }

    fun getApplicationApkPath(): String = safeServiceCall { connectedService.applicationApkPath }

    fun enqueueDownload(intent: Intent, callback: DownloadCallback) = safeServiceCall {
        connectedService.enqueueDownload(intent, callback)
    }

    fun convertMedia(
        input: ParcelFileDescriptor,
        inputExtension: String,
        outputExtension: String,
        audioCodec: String?,
        videoCodec: String?
    ): ParcelFileDescriptor? = safeServiceCall {
        connectedService.convertMedia(input, inputExtension, outputExtension, audioCodec, videoCodec)
    }

    fun sync(callback: SyncCallback) {
        if (!context.database.hasMain()) return
        safeServiceCall {
            connectedService.sync(callback)
        }
    }

    fun triggerSync(scope: SocialScope, id: String) = safeServiceCall {
        connectedService.triggerSync(scope.key, id)
    }

    fun passGroupsAndFriends(groups: List<MessagingGroupInfo>, friends: List<MessagingFriendInfo>) =
        safeServiceCall {
            val serializedGroups = groups.mapNotNull { it.toSerialized() }
            val serializedFriends = friends.mapNotNull { it.toSerialized() }
            
            // Binder transaction limit is 1MB. Use 128KB chunks to avoid TransactionTooLargeException.
            val maxChunkBytes = 128 * 1024

            fun calculateParts(values: List<String>): List<List<String>> {
                if (values.isEmpty()) return listOf(emptyList())
                val result = mutableListOf<List<String>>()
                var currentChunk = mutableListOf<String>()
                var currentSize = 0

                values.forEach { value ->
                    val valueSize = value.toByteArray(Charsets.UTF_8).size + 32
                    if (currentChunk.isNotEmpty() && currentSize + valueSize > maxChunkBytes) {
                        result += currentChunk.toList()
                        currentChunk = mutableListOf()
                        currentSize = 0
                    }
                    currentChunk.add(value)
                    currentSize += valueSize
                }

                if (currentChunk.isNotEmpty()) {
                    result += currentChunk.toList()
                }
                return result
            }

            val groupParts = calculateParts(serializedGroups)
            val friendParts = calculateParts(serializedFriends)
            val totalParts = maxOf(groupParts.size, friendParts.size)

            context.log.info("Synchronizing social data in $totalParts part(s): ${serializedGroups.size} groups, ${serializedFriends.size} friends")

            repeat(totalParts) { index ->
                connectedService.passGroupsAndFriends(
                    groupParts.getOrElse(index) { emptyList() },
                    friendParts.getOrElse(index) { emptyList() },
                    index,
                    totalParts
                )
            }
        }

    fun getRules(targetUuid: String): List<MessagingRuleType> = safeServiceCall {
        connectedService.getRules(targetUuid).mapNotNull { MessagingRuleType.getByName(it) }
    }

    fun getRuleIds(ruleType: MessagingRuleType): List<String> = safeServiceCall {
        connectedService.getRuleIds(ruleType.key)
    }

    fun setRule(targetUuid: String, type: MessagingRuleType, state: Boolean) = safeServiceCall {
        connectedService.setRule(targetUuid, type.key, state)
    }

    fun getScopeNotes(id: String): String? = safeServiceCall { connectedService.getScopeNotes(id) }

    fun setScopeNotes(id: String, content: String?) = safeServiceCall { connectedService.setScopeNotes(id, content) }

    fun getAllScopeNotes(): Map<String, String> = safeServiceCall { connectedService.getAllScopeNotes() }

    fun setAllScopeNotes(notes: Map<String, String>) = safeServiceCall { connectedService.setAllScopeNotes(notes) }

    fun getScriptingInterface(): IScripting? = safeServiceCall<IScripting?> { connectedService.scriptingInterface }

    fun getE2eeInterface(): E2eeInterface = safeServiceCall { connectedService.e2eeInterface }

    fun getMessageLogger(): LoggerInterface = safeServiceCall { connectedService.logger }

    fun getTracker(): TrackerInterface = safeServiceCall { connectedService.tracker }

    fun getAccountStorage(): AccountStorage = safeServiceCall { connectedService.accountStorage }

    fun getFileHandlerManager(): FileHandleManager = safeServiceCall { connectedService.fileHandleManager }

    fun getLocationManager(): LocationManager = safeServiceCall { connectedService.locationManager }

    fun getTaskInterface(): TaskInterface = safeServiceCall { connectedService.taskInterface }

    fun registerMessagingBridge(bridge: MessagingBridge) = safeServiceCall { connectedService.registerMessagingBridge(bridge) }

    fun openOverlay(type: OverlayType) = safeServiceCall { connectedService.openOverlay(type.key) }
    fun closeOverlay() = safeServiceCall { connectedService.closeOverlay() }

    fun registerConfigStateListener(listener: ConfigStateListener) = safeServiceCall { connectedService.registerConfigStateListener(listener) }

    fun getDebugProp(name: String, defaultValue: String? = null): String? = safeServiceCall { connectedService.getDebugProp(name, defaultValue) }

    fun startCallDownload(
        startTimestamp: Long,
        author: String,
    ): CallDownloadSession {
        return safeServiceCall { connectedService.startCallDownload(startTimestamp, author) }
    }
}
