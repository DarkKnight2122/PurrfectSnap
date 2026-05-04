package me.eternal.purrfect.common

object Constants {
    val SNAPCHAT_PACKAGE_NAME get() = "com.snapchat.android"
    val REDDIT_PACKAGE_NAME get() = "com.reddit.frontpage"
    val HOOK_TARGET_PACKAGES get() = setOf(SNAPCHAT_PACKAGE_NAME, REDDIT_PACKAGE_NAME)
    val MODULE_PACKAGE_NAME get() = BuildConfig.APPLICATION_ID
    val REDDIT_CONFIG_REQUEST_ACTION get() = "$MODULE_PACKAGE_NAME.action.REDDIT_CONFIG_REQUEST"
    val REDDIT_CONFIG_UPDATE_ACTION get() = "$MODULE_PACKAGE_NAME.action.REDDIT_CONFIG_UPDATE"
    val REDDIT_FORCE_STOP_ACTION get() = "$MODULE_PACKAGE_NAME.action.REDDIT_FORCE_STOP"
    const val REDDIT_CONFIG_JSON_EXTRA = "reddit_config_json"
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.3"
    const val OSM_USER_AGENT = "Purrfect/1.0"
}
