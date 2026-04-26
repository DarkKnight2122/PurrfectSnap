package me.eternal.purrfectsnap.ui.manager.pages.social

import me.eternal.purrfectsnap.RemoteSideContext
import me.eternal.purrfectsnap.common.data.MessagingFriendInfo
import me.eternal.purrfectsnap.storage.getFriends

internal fun RemoteSideContext.sortSocialFriends(
    friends: List<MessagingFriendInfo>,
    pinnedIds: List<String>? = null
): List<MessagingFriendInfo> {
    val whitelistedIds = pinnedIds?.toSet() ?: database.getFriends().map { it.userId }.toSet()
    val sortByStreakLength = config.root.userInterface.sortSocialTabByStreakLength.get()

    return friends.sortedWith { a, b ->
        val aSelected = whitelistedIds.contains(a.userId)
        val bSelected = whitelistedIds.contains(b.userId)

        if (aSelected != bSelected) {
            return@sortedWith if (aSelected) -1 else 1
        }

        if (sortByStreakLength) {
            val aStreak = a.streaks?.length ?: 0
            val bStreak = b.streaks?.length ?: 0
            if (aStreak != bStreak) return@sortedWith bStreak.compareTo(aStreak)
        }

        (a.displayName ?: a.mutableUsername).compareTo(b.displayName ?: b.mutableUsername, ignoreCase = true)
    }
}
