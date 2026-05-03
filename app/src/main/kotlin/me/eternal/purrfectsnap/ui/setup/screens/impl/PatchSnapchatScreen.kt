package me.eternal.purrfectsnap.ui.setup.screens.impl

import me.eternal.purrfectsnap.common.TargetApp

class PatchSnapchatScreen(
    selectedAppsProvider: () -> Set<TargetApp> = { setOf(TargetApp.SNAPCHAT) }
) : TargetAppInstallScreen(
    selectedAppsProvider = selectedAppsProvider,
    flow = SetupInstallFlow.PATCH
)
