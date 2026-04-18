package me.eternal.purrfectsnap.ui.manager.pages.social

import me.eternal.purrfectsnap.RemoteSideContext
import me.eternal.purrfectsnap.common.data.MessagingFriendInfo

internal fun RemoteSideContext.sortSocialFriends(
    friends: List<MessagingFriendInfo>,
    pinnedIds: List<String>? = null
): List<MessagingFriendInfo> {
    if (config.root.userInterface.sortSocialTabByStreakLength.get()) {
        return friends.sortedWith(
            compareByDescending<MessagingFriendInfo> { (it.streaks?.length ?: 0) > 0 }
                .thenByDescending { it.streaks?.length ?: 0 }
        )
    }

    return if (pinnedIds != null) {
        friends.sortedBy { -pinnedIds.indexOf(it.userId) }
    } else {
        friends
    }
}
