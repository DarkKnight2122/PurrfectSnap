package me.eternal.purrfectsnap.ui.setup.screens.impl

import me.eternal.purrfectsnap.common.Constants
import me.eternal.purrfectsnap.common.TargetApp

internal data class SetupInstallTarget(
    val targetApp: TargetApp,
    val displayName: String,
    val packageName: String
)

internal val setupTargetOrder = listOf(TargetApp.SNAPCHAT, TargetApp.REDDIT)

internal fun TargetApp.toSetupInstallTarget(): SetupInstallTarget {
    return when (this) {
        TargetApp.SNAPCHAT -> SetupInstallTarget(
            targetApp = this,
            displayName = "Snapchat",
            packageName = Constants.SNAPCHAT_PACKAGE_NAME
        )

        TargetApp.REDDIT -> SetupInstallTarget(
            targetApp = this,
            displayName = "Reddit",
            packageName = Constants.REDDIT_PACKAGE_NAME
        )
    }
}

internal fun setupInstallTargets(selectedApps: Set<TargetApp>): List<SetupInstallTarget> {
    val normalized = setupTargetOrder.filter { it in selectedApps }
    return normalized.ifEmpty { listOf(TargetApp.SNAPCHAT) }
        .map { it.toSetupInstallTarget() }
}

internal fun parseSetupTargetApps(value: String?, fallback: Set<TargetApp> = emptySet()): Set<TargetApp> {
    val parsed = value.orEmpty()
        .split(',')
        .mapNotNull { key ->
            TargetApp.entries.firstOrNull { it.key == key.trim() }
        }
        .toSet()
    return parsed.ifEmpty { fallback }
}

internal fun Set<TargetApp>.toSetupTargetPrefsValue(): String {
    return setupTargetOrder
        .filter { it in this }
        .joinToString(",") { it.key }
}
