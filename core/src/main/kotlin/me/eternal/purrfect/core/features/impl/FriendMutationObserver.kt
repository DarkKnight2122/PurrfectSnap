package me.eternal.purrfect.core.features.impl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.gson.JsonObject
import me.eternal.purrfect.common.data.FriendLinkType
import me.eternal.purrfect.common.database.impl.FriendInfo
import me.eternal.purrfect.core.event.events.impl.NetworkApiRequestEvent
import me.eternal.purrfect.core.event.events.impl.UnaryCallEvent
import me.eternal.purrfect.core.features.Feature
import me.eternal.purrfect.core.util.EvictingMap
import me.eternal.purrfect.common.util.protobuf.ProtoReader
import java.io.InputStreamReader
import java.util.Calendar

class FriendMutationObserver: Feature("FriendMutationObserver") {
    private val translation by lazy { context.translation.getCategory("friend_mutation_observer") }
    private val addSourceCache = EvictingMap<String, String>(500)

    private val notificationManager by lazy { context.androidContext.getSystemService(NotificationManager::class.java) }
    private val channelId by lazy {
        "friend_mutation_observer".also {
            notificationManager.createNotificationChannel(
                NotificationChannel(it, translation["notification_channel_name"], NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    // Injected from app layer — keeps core free of ui.* imports
    var aphelionToastProvider: ((
        icon: ImageVector,
        text: String,
        bitmojiUrl: String?,
        onDismiss: () -> Unit
    ) -> Unit)? = null

    fun getFriendAddSource(userId: String): String? = addSourceCache[userId]

    private fun sendMutationNotification(icon: ImageVector, contentText: String, friendInfo: FriendInfo? = null) {
        val currentTheme = context.config.global.uiSettings.managerTheme.get()
        val isAphelion = currentTheme == "APHELION"

        notificationManager.notify(System.nanoTime().toInt(),
            Notification.Builder(context.androidContext, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(translation["notification_channel_name"])
                .setContentText(contentText)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .build()
        )

        val provider = aphelionToastProvider
        if (isAphelion && provider != null) {
            val bitmojiUrl = friendInfo?.let {
                me.eternal.purrfect.common.util.snap.BitmojiSelfie.getBitmojiSelfie(
                    it.bitmojiSelfieId,
                    it.bitmojiAvatarId,
                    me.eternal.purrfect.common.util.snap.BitmojiSelfie.BitmojiSelfieType.NEW_THREE_D
                )
            }
            provider(icon, contentText, bitmojiUrl) {
                // onDismiss handled inside the provider lambda in app layer
            }
        } else {
            context.inAppOverlay.showStatusToast(icon, contentText, durationMs = 7000)
        }
    }

    private fun formatUsername(friendInfo: FriendInfo): String {
        return friendInfo.displayName?.takeIf { it.isNotBlank() }?.let {
            "$it (${friendInfo.mutableUsername})"
        } ?: friendInfo.mutableUsername ?: ""
    }

    private fun prettyPrintBirthday(month: Int, day: Int): String {
        val calendar = Calendar.getInstance()
        calendar[Calendar.MONTH] = month
        return calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, context.translation.loadedLocale)?.toString() + " " + day
    }

    private fun com.google.gson.JsonElement?.asSafeString(): String? {
        return if (this != null && this.isJsonPrimitive) this.asString else null
    }

    override fun init() {
        val config by context.config.messaging.friendMutationNotifier

        // Diagnostic log for gRPC calls related to friends
        context.event.subscribe(UnaryCallEvent::class) { event ->
            if (event.uri.contains("friend", ignoreCase = true) || event.uri.contains("atlas", ignoreCase = true)) {
                context.log.verbose("[FRIEND_MUTATION] Intercepted gRPC unary request: ${event.uri} payloadSize=${event.buffer.size}")
                
                if (event.uri == "/com.snapchat.atlas.gw.AtlasGw/SyncFriendData") {
                    event.addResponseCallback {
                        runCatching {
                            val rootReader = ProtoReader(buffer)
                            val container = rootReader.followPath(1) ?: return@runCatching
                            
                            container.eachBuffer(2) {
                                val username = getString(2) ?: return@eachBuffer
                                val databaseFriend = context.database.getFriendInfoByUsername(username) ?: return@eachBuffer
                                
                                // Ensure they are/were a mutual friend in database before tracking mutations
                                if (FriendLinkType.fromValue(databaseFriend.friendLinkType) != FriendLinkType.MUTUAL) return@eachBuffer
                                
                                val linkType = getVarInt(8)?.toInt()
                                context.log.verbose("[FRIEND_MUTATION] gRPC sync update: username=$username, linkType=$linkType")

                                // 0. Friend removal check (prioritized to prevent false downstream birthday-removed alerts)
                                if (linkType == null || linkType != 1) { // 1 is MUTUAL in gRPC
                                    if (config.contains("remove_friend")) {
                                        sendMutationNotification(Icons.Default.PersonRemove, translation.format("friend_removed", "username" to formatUsername(databaseFriend)), databaseFriend)
                                    }
                                    return@eachBuffer // Exit immediately since they are no longer mutual
                                }
                                
                                // 1. Birthday Changes
                                if (config.contains("birthday_changes")) {
                                    val birthMonth = followPath(5)?.getVarInt(2)?.toInt()
                                    val birthDay = followPath(5)?.getVarInt(3)?.toInt()
                                    val currentBirthdayStr = databaseFriend.birthday.takeIf { it != 0L }?.let {
                                        ((it shr 32).toInt()).toString().padStart(2, '0') + "-" + (it.toInt()).toString().padStart(2, '0')
                                    }
                                    val newBirthdayStr = if (birthMonth != null && birthDay != null && birthMonth > 0 && birthDay > 0) {
                                        "${birthMonth.toString().padStart(2, '0')}-${birthDay.toString().padStart(2, '0')}"
                                    } else null
                                    
                                    if (currentBirthdayStr != newBirthdayStr) {
                                        val oldBirthday = databaseFriend.birthday.takeIf { it != 0L }?.let { prettyPrintBirthday((it shr 32).toInt() - 1, it.toInt()) }
                                        if (newBirthdayStr == null) {
                                            sendMutationNotification(Icons.Default.Cake, translation.format("birthday_removed", "username" to formatUsername(databaseFriend), "birthday" to oldBirthday.orEmpty()), databaseFriend)
                                        } else {
                                            val newBirthday = prettyPrintBirthday(birthMonth!! - 1, birthDay!!)
                                            if (oldBirthday == null) {
                                                sendMutationNotification(Icons.Default.Cake, translation.format("birthday_added", "username" to formatUsername(databaseFriend), "birthday" to newBirthday), databaseFriend)
                                            } else {
                                                sendMutationNotification(Icons.Default.Cake, translation.format("birthday_changed", "username" to formatUsername(databaseFriend), "oldBirthday" to oldBirthday, "newBirthday" to newBirthday), databaseFriend)
                                            }
                                        }
                                    }
                                }
                                
                                // 2. Bitmoji Avatar changes (Tag 10)
                                if (config.contains("bitmoji_avatar_changes")) {
                                    val newAvatarId = getString(10)
                                    if (databaseFriend.bitmojiAvatarId != newAvatarId) {
                                        sendMutationNotification(Icons.Default.Face, translation.format("bitmoji_avatar_changed", "username" to formatUsername(databaseFriend)), databaseFriend)
                                    }
                                }
                                
                                // 3. Bitmoji Selfie changes (Tag 11)
                                if (config.contains("bitmoji_selfie_changes")) {
                                    val newSelfieId = getString(11)
                                    if (databaseFriend.bitmojiSelfieId != newSelfieId) {
                                        sendMutationNotification(Icons.Default.Face, translation.format("bitmoji_selfie_changed", "username" to formatUsername(databaseFriend)), databaseFriend)
                                    }
                                }
                                
                                // 4. Bitmoji Background changes (Tag 13)
                                if (config.contains("bitmoji_background_changes")) {
                                    val newBackgroundId = getString(13)
                                    if (databaseFriend.bitmojiBackgroundId != newBackgroundId) {
                                        sendMutationNotification(Icons.Default.Image, translation.format("bitmoji_background_changed", "username" to formatUsername(databaseFriend)), databaseFriend)
                                    }
                                }
                                
                                // 5. Bitmoji Scene changes (Tag 12)
                                if (config.contains("bitmoji_scene_changes")) {
                                    val newSceneId = getString(12)
                                    if (databaseFriend.bitmojiSceneId != newSceneId) {
                                        sendMutationNotification(Icons.Default.Landscape, translation.format("bitmoji_scene_changed", "username" to formatUsername(databaseFriend)), databaseFriend)
                                    }
                                }
                            }
                        }.onFailure { t ->
                            context.log.error("Failed to parse SyncFriendData response", t)
                        }
                    }
                }
            }
        }

        context.event.subscribe(NetworkApiRequestEvent::class) { event ->
            if (event.url.contains("ami/friends")) {
                context.log.verbose("[FRIEND_MUTATION] Intercepted REST endpoint: ${event.url}")
            }
            if (!event.url.contains("ami/friends")) return@subscribe
            event.onSuccess { buffer ->
                runCatching {
                    val jsonObject = context.gson.fromJson(InputStreamReader(buffer?.inputStream() ?: return@onSuccess, Charsets.UTF_8), JsonObject::class.java)
                    jsonObject.getAsJsonArray("added_friends")?.map { it.asJsonObject }?.forEach { friend ->
                        val userId = friend.get("user_id").asSafeString() ?: return@forEach
                        (friend.get("add_source").asSafeString()
                            ?: friend.get("add_source_type").asSafeString())?.let {
                            addSourceCache[userId] = it
                        }
                    }

                    if (config.isEmpty()) return@runCatching
                    jsonObject.getAsJsonArray("friends")?.map { it.asJsonObject }?.forEach { friend ->
                        runCatching {
                            val userId = friend.get("user_id").asSafeString() ?: return@forEach
                            if (userId == context.database.myUserId) return@forEach
                            val databaseFriend = context.database.getFriendInfo(userId) ?: return@forEach
                            if (FriendLinkType.fromValue(databaseFriend.friendLinkType) != FriendLinkType.MUTUAL) return@forEach

                            if (friend.get("direction").asSafeString() == "OUTGOING" && !friend.has("fidelius_info")) {
                                val isDeactivated = friend.get("deactivated")?.takeIf { it.isJsonPrimitive }?.asBoolean == true || friend.has("deactivated_timestamp")
                                if (isDeactivated) {
                                    if (config.contains("deactivated_friend")) {
                                        sendMutationNotification(Icons.Default.PersonRemove, translation.format("friend_deactivated", "username" to formatUsername(databaseFriend)), databaseFriend)
                                    }
                                } else {
                                    if (config.contains("remove_friend")) {
                                        sendMutationNotification(Icons.Default.PersonRemove, translation.format("friend_removed", "username" to formatUsername(databaseFriend)), databaseFriend)
                                    }
                                }
                                return@forEach
                            }

                            if (config.contains("birthday_changes") &&
                                databaseFriend.birthday.takeIf { it != 0L }?.let {
                                    ((it shr 32).toInt()).toString().padStart(2, '0') + "-" + (it.toInt()).toString().padStart(2, '0')
                                } != friend.get("birthday").asSafeString()
                            ) {
                                val oldBirthday = databaseFriend.birthday.takeIf { it != 0L }?.let { prettyPrintBirthday((it shr 32).toInt() - 1, it.toInt()) }
                                if (!friend.has("birthday")) {
                                    sendMutationNotification(Icons.Default.Cake, translation.format("birthday_removed", "username" to formatUsername(databaseFriend), "birthday" to oldBirthday.orEmpty()), databaseFriend)
                                } else {
                                    val newBirthday = friend.get("birthday").asSafeString()?.split("-")?.let { prettyPrintBirthday(it[0].toInt() - 1, it[1].toInt()) }
                                    if (oldBirthday == null) {
                                        sendMutationNotification(Icons.Default.Cake, translation.format("birthday_added", "username" to formatUsername(databaseFriend), "birthday" to newBirthday.orEmpty()), databaseFriend)
                                    } else {
                                        sendMutationNotification(Icons.Default.Cake, translation.format("birthday_changed", "username" to formatUsername(databaseFriend), "oldBirthday" to oldBirthday, "newBirthday" to newBirthday.orEmpty()), databaseFriend)
                                    }
                                }
                            }

                            if (config.contains("bitmoji_avatar_changes") && databaseFriend.bitmojiAvatarId != friend.get("bitmoji_avatar_id").asSafeString()) {
                                sendMutationNotification(Icons.Default.Face, translation.format("bitmoji_avatar_changed", "username" to formatUsername(databaseFriend)), databaseFriend)
                            }

                            if (config.contains("bitmoji_selfie_changes") && databaseFriend.bitmojiSelfieId != friend.get("bitmoji_selfie_id").asSafeString()) {
                                sendMutationNotification(Icons.Default.Face, translation.format("bitmoji_selfie_changed", "username" to formatUsername(databaseFriend)), databaseFriend)
                            }

                            if (config.contains("bitmoji_background_changes") && databaseFriend.bitmojiBackgroundId != friend.get("bitmoji_background_id").asSafeString()) {
                                sendMutationNotification(Icons.Default.Image, translation.format("bitmoji_background_changed", "username" to formatUsername(databaseFriend)), databaseFriend)
                            }

                            if (config.contains("bitmoji_scene_changes") && databaseFriend.bitmojiSceneId != friend.get("bitmoji_scene_id").asSafeString()) {
                                sendMutationNotification(Icons.Default.Landscape, translation.format("bitmoji_scene_changed", "username" to formatUsername(databaseFriend)), databaseFriend)
                            }
                        }.onFailure { context.log.error("Failed to process friend", it) }
                    }
                }.onFailure { context.log.error("Failed to process friends", it) }
            }
        }
    }
}
