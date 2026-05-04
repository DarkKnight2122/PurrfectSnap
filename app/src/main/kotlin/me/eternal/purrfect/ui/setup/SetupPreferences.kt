package me.eternal.purrfect.ui.setup

import android.content.SharedPreferences
import me.eternal.purrfect.common.TargetApp

object SetupPreferences {
    const val COMPLETED_TARGET_APPS_PREF = "setup_completed_target_apps"
    const val SELECTED_TARGET_APPS_PREF = "setup_selected_target_apps"
    const val LAST_INSTALL_MODE_PREF = "setup_last_install_mode"
    const val AUTO_SETUP_SKIPPED_PREF = "setup_auto_setup_skipped"

    fun parseTargetApps(value: String?): Set<TargetApp> {
        return value.orEmpty()
            .split(',')
            .mapNotNull { key -> TargetApp.entries.firstOrNull { it.key == key.trim() } }
            .toSet()
    }

    fun formatTargetApps(targetApps: Set<TargetApp>): String {
        return TargetApp.entries
            .filter { it in targetApps }
            .joinToString(",") { it.key }
    }

    fun selectedTargetApps(sharedPreferences: SharedPreferences): Set<TargetApp> {
        return parseTargetApps(sharedPreferences.getString(SELECTED_TARGET_APPS_PREF, null))
    }

    fun completedTargetApps(sharedPreferences: SharedPreferences): Set<TargetApp> {
        return parseTargetApps(sharedPreferences.getString(COMPLETED_TARGET_APPS_PREF, null))
    }

    fun hasCompletedTarget(sharedPreferences: SharedPreferences, targetApp: TargetApp): Boolean {
        return targetApp in completedTargetApps(sharedPreferences)
    }

    fun addCompletedTarget(sharedPreferences: SharedPreferences, targetApp: TargetApp) {
        addCompletedTargets(sharedPreferences, setOf(targetApp))
    }

    fun addCompletedTargets(sharedPreferences: SharedPreferences, targetApps: Set<TargetApp>) {
        if (targetApps.isEmpty()) return
        val updated = completedTargetApps(sharedPreferences) + targetApps
        sharedPreferences.edit()
            .putString(COMPLETED_TARGET_APPS_PREF, formatTargetApps(updated))
            .apply()
    }

    fun saveSetupChoices(
        sharedPreferences: SharedPreferences,
        selectedApps: Set<TargetApp>,
        installModeName: String?,
        skippedAutoSetup: Boolean
    ) {
        val updatedSelectedApps = selectedTargetApps(sharedPreferences) + selectedApps
        sharedPreferences.edit()
            .putString(SELECTED_TARGET_APPS_PREF, formatTargetApps(updatedSelectedApps))
            .putBoolean(AUTO_SETUP_SKIPPED_PREF, skippedAutoSetup)
            .apply {
                if (installModeName.isNullOrBlank()) {
                    remove(LAST_INSTALL_MODE_PREF)
                } else {
                    putString(LAST_INSTALL_MODE_PREF, installModeName)
                }
            }
            .apply()
    }

    fun clearSetupChoices(sharedPreferences: SharedPreferences) {
        sharedPreferences.edit()
            .remove(COMPLETED_TARGET_APPS_PREF)
            .remove(SELECTED_TARGET_APPS_PREF)
            .remove(LAST_INSTALL_MODE_PREF)
            .remove(AUTO_SETUP_SKIPPED_PREF)
            .apply()
    }

    fun lastInstallModeName(sharedPreferences: SharedPreferences): String? {
        return sharedPreferences.getString(LAST_INSTALL_MODE_PREF, null)
    }

    fun wasAutoSetupSkipped(sharedPreferences: SharedPreferences): Boolean {
        return sharedPreferences.getBoolean(AUTO_SETUP_SKIPPED_PREF, false)
    }
}
