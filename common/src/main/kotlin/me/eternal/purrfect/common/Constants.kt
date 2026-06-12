package me.eternal.purrfect.common

object Constants {
    val SNAPCHAT_PACKAGE_NAME get() = "com.snapchat.android"
    val REDDIT_PACKAGE_NAME get() = "com.reddit.frontpage"
    val WHATSAPP_PACKAGE_NAME get() = "com.whatsapp"
    val INSTAGRAM_PACKAGE_NAME get() = "com.instagram.android"
    val INSTAGRAM_PACKAGE_NAMES get() = linkedSetOf(
        INSTAGRAM_PACKAGE_NAME,
        "com.instagold.android",
        "com.instaflux.app",
        "com.myinsta.android",
        "cc.honista.app",
        "com.instaprime.android",
        "com.instafel.android",
        "com.instadm.android",
        "com.dfistagram.android",
        "com.Instander.android",
        "com.aero.instagram",
        "com.instapro.android",
        "com.instaflow.android",
        "com.instagram1.android",
        "com.instagram2.android",
        "com.instagramclone.android",
        "com.instaclone.android",
    )
    val HOOK_TARGET_PACKAGES get() = setOf(SNAPCHAT_PACKAGE_NAME, REDDIT_PACKAGE_NAME, WHATSAPP_PACKAGE_NAME) + INSTAGRAM_PACKAGE_NAMES
    val MODULE_PACKAGE_NAME get() = BuildConfig.APPLICATION_ID
    val REDDIT_CONFIG_REQUEST_ACTION get() = "$MODULE_PACKAGE_NAME.action.REDDIT_CONFIG_REQUEST"
    val REDDIT_CONFIG_UPDATE_ACTION get() = "$MODULE_PACKAGE_NAME.action.REDDIT_CONFIG_UPDATE"
    val REDDIT_FORCE_STOP_ACTION get() = "$MODULE_PACKAGE_NAME.action.REDDIT_FORCE_STOP"
    const val REDDIT_CONFIG_JSON_EXTRA = "reddit_config_json"
    val WHATSAPP_CONFIG_REQUEST_ACTION get() = "$MODULE_PACKAGE_NAME.action.WHATSAPP_CONFIG_REQUEST"
    val WHATSAPP_CONFIG_UPDATE_ACTION get() = "$MODULE_PACKAGE_NAME.action.WHATSAPP_CONFIG_UPDATE"
    val WHATSAPP_UI_ELEMENT_CAPTURED_ACTION get() = "$MODULE_PACKAGE_NAME.action.WHATSAPP_UI_ELEMENT_CAPTURED"
    val WHATSAPP_FORCE_STOP_ACTION get() = "$MODULE_PACKAGE_NAME.action.WHATSAPP_FORCE_STOP"
    const val WHATSAPP_CONFIG_JSON_EXTRA = "whatsapp_config_json"
    const val WHATSAPP_UI_ELEMENT_CAPTURED_VALUE_EXTRA = "whatsapp_ui_element_value"
    const val WHATSAPP_UI_ELEMENT_CAPTURED_IS_SELECTOR_EXTRA = "whatsapp_ui_element_is_selector"
    val INSTAGRAM_CONFIG_REQUEST_ACTION get() = "$MODULE_PACKAGE_NAME.action.INSTAGRAM_CONFIG_REQUEST"
    val INSTAGRAM_CONFIG_UPDATE_ACTION get() = "$MODULE_PACKAGE_NAME.action.INSTAGRAM_CONFIG_UPDATE"
    val INSTAGRAM_FEATURE_PREF_UPDATE_ACTION get() = "$MODULE_PACKAGE_NAME.action.INSTAGRAM_FEATURE_PREF_UPDATE"
    val INSTAGRAM_DEV_CONFIG_IMPORT_ACTION get() = "$MODULE_PACKAGE_NAME.action.INSTAGRAM_DEV_CONFIG_IMPORT"
    val INSTAGRAM_DEV_CONFIG_EXPORT_REQUEST_ACTION get() = "$MODULE_PACKAGE_NAME.action.INSTAGRAM_DEV_CONFIG_EXPORT_REQUEST"
    val INSTAGRAM_DEV_CONFIG_SEND_ACTION get() = "$MODULE_PACKAGE_NAME.action.INSTAGRAM_DEV_CONFIG_SEND"
    val INSTAGRAM_SETTINGS_RESTORE_ACTION get() = "$MODULE_PACKAGE_NAME.action.INSTAGRAM_SETTINGS_RESTORE"
    val INSTAGRAM_DOWNLOAD_SAVE_ACTION get() = "$MODULE_PACKAGE_NAME.action.INSTAGRAM_DOWNLOAD_SAVE"
    val INSTAGRAM_UI_ELEMENT_CAPTURED_ACTION get() = "$MODULE_PACKAGE_NAME.action.INSTAGRAM_UI_ELEMENT_CAPTURED"
    val INSTAGRAM_FORCE_STOP_ACTION get() = "$MODULE_PACKAGE_NAME.action.INSTAGRAM_FORCE_STOP"
    val INSTAGRAM_DEXKIT_SCAN_ACTION get() = "$MODULE_PACKAGE_NAME.action.INSTAGRAM_DEXKIT_SCAN"
    const val INSTAGRAM_CONFIG_JSON_EXTRA = "instagram_config_json"
    const val INSTAGRAM_DEV_CONFIG_JSON_EXTRA = "instagram_dev_config_json"
    const val INSTAGRAM_DEV_CONFIG_ERROR_EXTRA = "instagram_dev_config_error"
    const val INSTAGRAM_FEATURE_PREF_KEY_EXTRA = "instagram_feature_pref_key"
    const val INSTAGRAM_FEATURE_PREF_BOOLEAN_EXTRA = "instagram_feature_pref_boolean"
    const val INSTAGRAM_FEATURE_PREF_STRING_EXTRA = "instagram_feature_pref_string"
    const val INSTAGRAM_FEATURE_PREF_IS_STRING_EXTRA = "instagram_feature_pref_is_string"
    const val INSTAGRAM_FEATURE_PREF_BATCH_JSON_EXTRA = "instagram_feature_pref_batch_json"
    const val INSTAGRAM_DOWNLOAD_URL_EXTRA = "instagram_download_url"
    const val INSTAGRAM_DOWNLOAD_AUDIO_URL_EXTRA = "instagram_download_audio_url"
    const val INSTAGRAM_DOWNLOAD_FILENAME_EXTRA = "instagram_download_filename"
    const val INSTAGRAM_DOWNLOAD_MIME_TYPE_EXTRA = "instagram_download_mime_type"
    const val INSTAGRAM_DOWNLOAD_USERNAME_EXTRA = "instagram_download_username"
    const val INSTAGRAM_DOWNLOAD_TREE_URI_EXTRA = "instagram_download_tree_uri"
    const val INSTAGRAM_UI_ELEMENT_CAPTURED_VALUE_EXTRA = "instagram_ui_element_value"
    const val INSTAGRAM_UI_ELEMENT_CAPTURED_IS_SELECTOR_EXTRA = "instagram_ui_element_is_selector"
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.3"
    const val OSM_USER_AGENT = "Purrfect/1.0"
}
