package me.eternal.purrfectsnap.ui.setup.screens.impl

import me.eternal.purrfectsnap.common.TargetApp

class RootInstallSnapchatScreen(
    selectedAppsProvider: () -> Set<TargetApp> = { setOf(TargetApp.SNAPCHAT) }
) : TargetAppInstallScreen(
    selectedAppsProvider = selectedAppsProvider,
    flow = SetupInstallFlow.ROOT
)
