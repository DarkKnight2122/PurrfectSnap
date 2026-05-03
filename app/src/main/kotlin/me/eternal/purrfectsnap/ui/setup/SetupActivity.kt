@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)

package me.eternal.purrfectsnap.ui.setup

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.eternal.purrfectsnap.RemoteSideContext
import me.eternal.purrfectsnap.SharedContextHolder
import me.eternal.purrfectsnap.common.TargetApp
import me.eternal.purrfectsnap.common.ui.AppMaterialTheme
import me.eternal.purrfectsnap.ui.manager.ManagerAssistantDialog
import me.eternal.purrfectsnap.ui.manager.Routes
import me.eternal.purrfectsnap.ui.manager.components.AestheticDialog
import me.eternal.purrfectsnap.ui.manager.theme.PurrfectPalette
import me.eternal.purrfectsnap.ui.setup.screens.LocalSetupScrollState
import me.eternal.purrfectsnap.ui.setup.screens.LocalSetupViewportHeight
import me.eternal.purrfectsnap.ui.setup.screens.SetupScreen
import me.eternal.purrfectsnap.ui.setup.screens.impl.InstallModeScreen
import me.eternal.purrfectsnap.ui.setup.screens.impl.InstallMode
import me.eternal.purrfectsnap.ui.setup.screens.impl.MappingsScreen
import me.eternal.purrfectsnap.ui.setup.screens.impl.PermissionsScreen
import me.eternal.purrfectsnap.ui.setup.screens.impl.PickLanguageScreen
import me.eternal.purrfectsnap.ui.setup.screens.impl.PatchSnapchatScreen
import me.eternal.purrfectsnap.ui.setup.screens.impl.RootInstallSnapchatScreen
import me.eternal.purrfectsnap.ui.setup.screens.impl.SaveFolderScreen
import me.eternal.purrfectsnap.ui.setup.screens.impl.IntroShowcaseScreen
import me.eternal.purrfectsnap.ui.setup.screens.impl.parseSetupTargetApps
import me.eternal.purrfectsnap.ui.setup.screens.impl.toSetupTargetPrefsValue
import me.eternal.purrfectsnap.ui.util.ActivityLauncherHelper
import me.eternal.purrfectsnap.ui.util.scaleOnPress
import kotlinx.coroutines.delay

private const val SETUP_SELECTED_APPS_PREF = "setup_selected_apps"

private data class SetupStepMeta(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

class SetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val setupContext = SharedContextHolder.remote(this).apply {
            activity = this@SetupActivity
        }
        fun endActivity() {
            setupContext.reload()
            sendBroadcast(Intent("me.eternal.purrfectsnap.RESTART"))
            finish()
        }
        val requirements = intent.getIntExtra("requirements", Requirements.FIRST_RUN)
        val setupPrefs = setupContext.sharedPreferences
        val setupRoutes = Routes(setupContext).apply {
            activityLauncher = ActivityLauncherHelper(this@SetupActivity)
        }
        fun hasRequirement(requirement: Int) = requirements and requirement == requirement
        val wasInProgress = setupPrefs.getBoolean("setup_in_progress", false)
        val isFirstRunFlow = hasRequirement(Requirements.FIRST_RUN) || wasInProgress
        val persistedRoute = setupPrefs.getString("setup_current_route", null)
        val persistedSkipPatch = setupPrefs.getBoolean("setup_skip_patch", false)
        val persistedInstallMode = setupPrefs.getString("setup_install_mode", null)
        val persistedSelectedApps = setupPrefs.getString(SETUP_SELECTED_APPS_PREF, null)
        val skipPatchChoice = mutableStateOf(persistedSkipPatch)
        val installModeChoice = mutableStateOf(
            runCatching { persistedInstallMode?.let { InstallMode.valueOf(it) } }.getOrNull()
        )
        val selectedAppsChoice = mutableStateOf(
            parseSetupTargetApps(
                persistedSelectedApps,
                fallback = if (wasInProgress && persistedRoute != null) setOf(TargetApp.SNAPCHAT) else emptySet()
            )
        )

        fun persistProgress(
            route: String,
            skipPatch: Boolean,
            installMode: InstallMode?,
            selectedApps: Set<TargetApp>,
            inProgress: Boolean = true
        ) {
            if (!isFirstRunFlow) return
            setupPrefs.edit()
                .putBoolean("setup_in_progress", inProgress)
                .putString("setup_current_route", route)
                .putBoolean("setup_skip_patch", skipPatch)
                .putString("setup_install_mode", installMode?.name)
                .putString(SETUP_SELECTED_APPS_PREF, selectedApps.toSetupTargetPrefsValue())
                .apply()
        }

        fun clearProgress() {
            setupPrefs.edit()
                .remove("setup_in_progress")
                .remove("setup_current_route")
                .remove("setup_skip_patch")
                .remove("setup_install_mode")
                .remove(SETUP_SELECTED_APPS_PREF)
                .apply()
        }

        val requiredScreens = mutableListOf<SetupScreen>().apply {
            if (isFirstRunFlow || hasRequirement(Requirements.LANGUAGE)) {
                add(PickLanguageScreen().apply { route = "language" })
                if (isFirstRunFlow) {
                    add(IntroShowcaseScreen(
                        selectedAppsProvider = { selectedAppsChoice.value },
                        onSelectionChanged = { selectedAppsChoice.value = it }
                    ).apply { route = "introShowcase" })
                }
                if (isFirstRunFlow) {
                    add(InstallModeScreen(
                        onModeChosen = { mode ->
                            installModeChoice.value = mode
                            skipPatchChoice.value = false
                        },
                        onSkipAutoSetup = {
                            skipPatchChoice.value = true
                            installModeChoice.value = null
                        }
                    ).apply { route = "installMode" })
                }
                if (isFirstRunFlow) {
                    add(RootInstallSnapchatScreen(
                        selectedAppsProvider = { selectedAppsChoice.value }
                    ).apply { route = "rootInstallSnapchat" })
                    add(PatchSnapchatScreen(
                        selectedAppsProvider = { selectedAppsChoice.value }
                    ).apply { route = "patchSnapchat" })
                }
            }
            if (isFirstRunFlow || hasRequirement(Requirements.GRANT_PERMISSIONS)) {
                add(PermissionsScreen(
                    selectedAppsProvider = { selectedAppsChoice.value }
                ).apply { route = "permissions" })
            }
            if (isFirstRunFlow || hasRequirement(Requirements.SAVE_FOLDER)) {
                add(SaveFolderScreen().apply { route = "saveFolder" })
            }
            if (isFirstRunFlow || hasRequirement(Requirements.MAPPINGS)) {
                add(MappingsScreen().apply { route = "mappings" })
            }
        }

        if (requiredScreens.isEmpty()) {
            endActivity()
            return
        }
        requiredScreens.forEach { screen ->
            screen.context = setupContext
            screen.isFirstRunFlow = isFirstRunFlow
            screen.init()
        }

            if (!isFirstRunFlow) {
                clearProgress()
                skipPatchChoice.value = false
                installModeChoice.value = null
                selectedAppsChoice.value = emptySet()
            }

        setContent {
            val context = LocalContext.current
            val translation = setupContext.translation
            val navController = rememberNavController()
            var canGoNext by remember { mutableStateOf(false) }
            var lastRoute by rememberSaveable { mutableStateOf("") }
            var currentRoute by rememberSaveable {
                mutableStateOf(
                    persistedRoute?.takeIf { route -> requiredScreens.any { it.route == route } }
                        ?: requiredScreens.first().route
                )
            }
            val skipPatch by rememberSaveable { skipPatchChoice }
            val installMode by installModeChoice
            val selectedApps by selectedAppsChoice
            val shouldShowAbiWarning = remember {
                val deviceIsArm64 = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it.startsWith("arm64") }
                val libDir = context.applicationInfo.nativeLibraryDir.orEmpty()
                val appIsArm64 = libDir.contains("arm64")
                deviceIsArm64 && !appIsArm64
            }
            if (shouldShowAbiWarning) {
                AestheticDialog(
                    onDismissRequest = {},
                    title = translation["setup.activity.wrong_apk_title"],
                    text = "",
                    icon = Icons.Filled.Warning,
                    confirmButtonText = translation["setup.activity.close_button"],
                    onConfirm = { (context as? Activity)?.finishAffinity() },
                    showCloseButton = false,
                    opaque = true,
                    customContent = {
                        Text(
                            text = translation["setup.activity.wrong_apk_message"],
                            color = PurrfectPalette.textSecondary,
                            lineHeight = 18.sp
                        )
                    }
                )
            }
            val visibleScreens = remember(skipPatch, installMode, selectedApps) {
                requiredScreens.filterNot { screen ->
                    if (skipPatch && (screen is PatchSnapchatScreen || screen is RootInstallSnapchatScreen)) {
                        return@filterNot true
                    }
                    if (installMode == null && (screen is PatchSnapchatScreen || screen is RootInstallSnapchatScreen)) {
                        return@filterNot true
                    }
                    if (installMode == InstallMode.ROOT && screen is PatchSnapchatScreen) {
                        return@filterNot true
                    }
                    if (installMode == InstallMode.NON_ROOT && screen is RootInstallSnapchatScreen) {
                        return@filterNot true
                    }
                    if (selectedApps.isNotEmpty() && TargetApp.SNAPCHAT !in selectedApps && screen is MappingsScreen) {
                        return@filterNot true
                    }
                    if (selectedApps == setOf(TargetApp.REDDIT) && screen is SaveFolderScreen) {
                        return@filterNot true
                    }
                    false
                }
            }
            val stepMeta = remember(skipPatch, installMode, selectedApps) { visibleScreens.map { it.meta(setupContext) } }
            val currentStepIndex = visibleScreens.indexOfFirst { it.route == currentRoute }.let {
                if (it == -1) 0 else it
            }
            val animatedProgress by animateFloatAsState(
                targetValue = (currentStepIndex + 1f) / stepMeta.size.toFloat(),
                label = "SetupProgress"
            )
            LaunchedEffect(skipPatch, installMode, selectedApps) {
                val adjustedRoute = visibleScreens.firstOrNull { it.route == currentRoute }?.route
                    ?: run {
                        val currentIndex = requiredScreens.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
                        val nextVisible = requiredScreens.drop(currentIndex + 1)
                            .firstOrNull { screen -> visibleScreens.any { it.route == screen.route } }
                        nextVisible?.route ?: visibleScreens.firstOrNull()?.route
                    }
                adjustedRoute?.let {
                    if (it != currentRoute) currentRoute = it
                }
            }

            LaunchedEffect(currentRoute, skipPatch, installMode, selectedApps) {
                persistProgress(currentRoute, skipPatch, installMode, selectedApps, true)
                if (navController.currentDestination?.route != currentRoute) {
                    navController.navigate(currentRoute) {
                        popUpTo(requiredScreens.first().route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
                if (lastRoute != currentRoute) {
                    canGoNext = false
                    lastRoute = currentRoute
                }
            }

            fun nextScreen() {
                if (!canGoNext) return
                canGoNext = false
                val currentScreen = visibleScreens.getOrNull(currentStepIndex)
                val proceed = {
                    currentScreen?.onLeave()
                    if (currentStepIndex < visibleScreens.lastIndex) {
                        val nextRoute = visibleScreens[currentStepIndex + 1].route
                        currentRoute = nextRoute
                    } else {
                        val preferredTarget = listOf(TargetApp.SNAPCHAT, TargetApp.REDDIT)
                            .firstOrNull { it in selectedApps }
                        preferredTarget?.let { setupContext.setActiveTargetApp(it) }
                        clearProgress()
                        endActivity()
                    }
                }
                currentScreen?.onNext { proceed() } ?: proceed()
            }

            AppMaterialTheme {
                val view = LocalView.current
                val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                var setupAiPrompt by rememberSaveable { mutableStateOf<String?>(null) }
                SideEffect {
                    val window = (view.context as Activity).window
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    @Suppress("DEPRECATION")
                    window.statusBarColor = Color.Transparent.toArgb()
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = Color.Transparent.toArgb()
                    val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = false
                    insetsController.isAppearanceLightNavigationBars = false
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                ) {
                    SetupAuroraBackground()
                    SetupTopBar(onAskAi = { setupAiPrompt = "hi" })
                    val bottomPadding = 80.dp + navBarPadding
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 110.dp,
                                bottom = bottomPadding
                            )
                            .statusBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SetupHeader(
                            currentStep = stepMeta[currentStepIndex],
                            currentIndex = currentStepIndex,
                            total = stepMeta.size
                        )
                        SetupProgressBar(animatedProgress)
                        val scrollState = rememberScrollState()
                        LaunchedEffect(currentRoute) {
                            scrollState.scrollTo(0)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = requiredScreens.first().route,
                                enterTransition = { fadeIn() },
                                exitTransition = { fadeOut() },
                                popEnterTransition = { fadeIn() },
                                popExitTransition = { fadeOut() }
                            ) {
                                requiredScreens.forEach { screen ->
                                    val screenRoute = screen.route
                                    screen.allowNext = allowNext@{ canGoNextFlag ->
                                        if (screenRoute != currentRoute) return@allowNext
                                        canGoNext = canGoNextFlag
                                    }
                                    screen.goNext = goNext@{
                                        if (screenRoute != currentRoute) return@goNext
                                        canGoNext = true
                                        nextScreen()
                                    }
                                    composable(
                                        screen.route,
                                        enterTransition = { slideInHorizontally { it } },
                                        exitTransition = { slideOutHorizontally { -it } },
                                        popEnterTransition = { slideInHorizontally { -it } },
                                        popExitTransition = { slideOutHorizontally { it } }
                                    ) {
                                        BackHandler(true) {}
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            BoxWithConstraints(
                                                modifier = Modifier
                                                    .widthIn(max = 560.dp)
                                                    .fillMaxHeight()
                                            ) {
                                                CompositionLocalProvider(
                                                    LocalSetupScrollState provides scrollState,
                                                    LocalSetupViewportHeight provides maxHeight
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        screen.Content()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    NextButton(
                        enabled = canGoNext,
                        isFinalStep = currentStepIndex >= stepMeta.lastIndex,
                        onClick = { nextScreen() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp)
                    )
                    setupAiPrompt?.let { prompt ->
                        ManagerAssistantDialog(
                            context = setupContext,
                            routes = setupRoutes,
                            initialUserMessage = prompt,
                            onDismiss = { setupAiPrompt = null }
                        )
                    }
                }
            }
        }
    }
}

private fun SetupScreen.meta(context: RemoteSideContext): SetupStepMeta {
    val translation = context.translation
    return when (this) {
        is PickLanguageScreen -> SetupStepMeta(
            route = route,
            title = translation["setup.dialogs.select_language"],
            subtitle = translation["setup.activity.language_subtitle"],
            icon = Icons.Filled.Language
        )
        is IntroShowcaseScreen -> SetupStepMeta(
            route = route,
            title = "Supported apps",
            subtitle = "Choose Snapchat, Reddit, or both",
            icon = Icons.Filled.AutoAwesome
        )

        is InstallModeScreen -> SetupStepMeta(
            route = route,
            title = translation["setup.activity.install_mode_title"],
            subtitle = translation["setup.activity.install_mode_subtitle"],
            icon = Icons.Filled.VerifiedUser
        )

        is PermissionsScreen -> SetupStepMeta(
            route = route,
            title = translation["setup.permissions.dialog"],
            subtitle = translation["setup.activity.permissions_subtitle"],
            icon = Icons.Filled.VerifiedUser
        )

        is PatchSnapchatScreen -> SetupStepMeta(
            route = route,
            title = translation["setup.activity.patch_title"],
            subtitle = translation["setup.activity.patch_subtitle"],
            icon = Icons.Filled.Download
        )

        is RootInstallSnapchatScreen -> SetupStepMeta(
            route = route,
            title = translation["setup.activity.root_install_title"],
            subtitle = translation["setup.activity.root_install_subtitle"],
            icon = Icons.Filled.Download
        )

        is SaveFolderScreen -> SetupStepMeta(
            route = route,
            title = translation["setup.dialogs.save_folder"],
            subtitle = translation["setup.activity.save_folder_subtitle"],
            icon = Icons.Filled.Folder
        )

        is MappingsScreen -> SetupStepMeta(
            route = route,
            title = translation["setup.mappings.dialog"],
            subtitle = translation["setup.activity.mappings_subtitle"],
            icon = Icons.Filled.AutoAwesome
        )

        else -> SetupStepMeta(route, route, route, Icons.Filled.Check)
    }
}

@Composable
private fun SetupAuroraBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "setupAurora")
    val driftX by infiniteTransition.animateFloat(
        initialValue = -90f,
        targetValue = 140f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftX"
    )
    val driftY by infiniteTransition.animateFloat(
        initialValue = 60f,
        targetValue = -120f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftY"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PurrfectPalette.backgroundGradient)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val primaryGlow = PurrfectPalette.glowPrimary.copy(alpha = 0.36f)
            val secondaryGlow = PurrfectPalette.glowSecondary.copy(alpha = 0.28f)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    start = Offset(x = size.width * 0.15f, y = 0f),
                    end = Offset(x = size.width * 0.75f, y = size.height * 0.6f)
                ),
                size = this.size
            )
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        PurrfectPalette.glowPrimary.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    start = Offset(x = 0f, y = size.height * 0.72f),
                    end = Offset(x = size.width, y = size.height)
                ),
                size = this.size
            )
        }
    }
}

@Composable
private fun SetupTopBar(onAskAi: () -> Unit) {
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .padding(top = topPadding),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.07f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    PurrfectPalette.glowPrimary.copy(alpha = 0.6f),
                    PurrfectPalette.glowSecondary.copy(alpha = 0.45f)
                )
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "PurrfectSnap",
                color = PurrfectPalette.textPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(40),
                color = Color.White.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(40))
                        .clickable(onClick = onAskAi)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.SmartToy, contentDescription = null, tint = Color.White)
                    Text(
                        text = "Ask AI",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupHeader(
    currentStep: SetupStepMeta,
    currentIndex: Int,
    total: Int
) {
    val translation = SharedContextHolder.remote(LocalContext.current).translation
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(30),
                color = Color.White.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Flag,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Text(
                        text = translation.format(
                            "setup.activity.step_counter",
                            "current" to (currentIndex + 1).toString(),
                            "total" to total.toString()
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(30),
                color = PurrfectPalette.glowPrimary.copy(alpha = 0.16f),
                border = androidx.compose.foundation.BorderStroke(1.dp, PurrfectPalette.glowPrimary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = currentStep.icon,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Text(
                        text = currentStep.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private enum class StepState { COMPLETE, ACTIVE, UPCOMING }

@Composable
private fun StepBadgesRow(steps: List<SetupStepMeta>, currentStep: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        steps.forEachIndexed { index, step ->
            val state = when {
                index < currentStep -> StepState.COMPLETE
                index == currentStep -> StepState.ACTIVE
                else -> StepState.UPCOMING
            }
            StepBadge(step, state)
            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .height(3.dp)
                        .width(28.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.05f),
                                    PurrfectPalette.glowPrimary.copy(alpha = 0.4f),
                                    Color.White.copy(alpha = 0.05f)
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun StepBadge(step: SetupStepMeta, state: StepState) {
    val translation = SharedContextHolder.remote(LocalContext.current).translation
    val baseColor = when (state) {
        StepState.COMPLETE -> PurrfectPalette.glowSecondary
        StepState.ACTIVE -> PurrfectPalette.glowPrimary
        StepState.UPCOMING -> Color.White.copy(alpha = 0.35f)
    }
    val background = when (state) {
        StepState.UPCOMING -> Color.White.copy(alpha = 0.05f)
        StepState.COMPLETE -> Color.White.copy(alpha = 0.08f)
        StepState.ACTIVE -> Color.White.copy(alpha = 0.12f)
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = background,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            baseColor.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                color = baseColor.copy(alpha = 0.22f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = step.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.widthIn(min = 0.dp, max = 160.dp)
            ) {
                Text(
                    text = step.title,
                    color = Color.White,
                    fontWeight = if (state == StepState.ACTIVE) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val hint = when (state) {
                    StepState.COMPLETE -> translation["setup.activity.step_complete"]
                    StepState.ACTIVE -> translation["setup.activity.step_active"]
                    StepState.UPCOMING -> translation["setup.activity.step_upcoming"]
                }
                Text(
                    text = hint,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun SetupProgressBar(progress: Float) {
    val gradient = Brush.horizontalGradient(
        listOf(
            PurrfectPalette.glowSecondary,
            PurrfectPalette.glowPrimary
        )
    )
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.07f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.14f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(gradient)
            )
        }
    }
}

@Composable
private fun SetupContentCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(34.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    PurrfectPalette.glowPrimary.copy(alpha = 0.45f),
                    PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                )
            )
        ),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(PurrfectPalette.cardOverlay)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.06f)
                        )
                    ),
                    shape = RoundedCornerShape(34.dp)
                )
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun NextButton(
    enabled: Boolean,
    isFinalStep: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val translation = SharedContextHolder.remote(LocalContext.current).translation
    val alpha by animateFloatAsState(targetValue = if (enabled) 1f else 0.6f, label = "NextButtonAlpha")
    val gradient = Brush.horizontalGradient(
        listOf(
            PurrfectPalette.glowSecondary,
            PurrfectPalette.glowPrimary
        )
    )
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .alpha(alpha)
            .scaleOnPress(interactionSource)
            .clip(RoundedCornerShape(36.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.24f),
                shape = RoundedCornerShape(36.dp)
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        color = Color.White.copy(alpha = if (enabled) 0.07f else 0.03f)
    ) {
        Box(
            modifier = Modifier
                .background(if (enabled) gradient else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))))
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isFinalStep) {
                        translation["setup.activity.finish_button"]
                    } else {
                        translation["setup.activity.continue_button"]
                    },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Icon(
                    imageVector = if (isFinalStep) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
