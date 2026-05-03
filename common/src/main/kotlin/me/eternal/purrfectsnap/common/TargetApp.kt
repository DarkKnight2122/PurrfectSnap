package me.eternal.purrfectsnap.common

enum class TargetApp(val key: String) {
    SNAPCHAT("snapchat"),
    REDDIT("reddit");

    companion object {
        const val PREF_KEY = "active_target_app"

        fun fromKey(key: String?): TargetApp {
            return entries.firstOrNull { it.key == key } ?: SNAPCHAT
        }
    }
}
