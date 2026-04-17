package me.eternal.purrfectsnap.ui.manager.pages.themes.aphelion

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import me.eternal.purrfectsnap.ui.manager.ThemeContract
import me.eternal.purrfectsnap.ui.manager.pages.home.HomeRootSection
import me.eternal.purrfectsnap.ui.manager.pages.home.HomeSettings
import me.eternal.purrfectsnap.ui.manager.pages.home.HomeAbout
import me.eternal.purrfectsnap.ui.manager.pages.home.HomeLogs
import me.eternal.purrfectsnap.ui.manager.pages.home.HomeState
import me.eternal.purrfectsnap.ui.manager.pages.TasksRootSection
import me.eternal.purrfectsnap.ui.manager.pages.features.FeaturesRootSection
import me.eternal.purrfectsnap.ui.manager.pages.social.SocialRootSection
import me.eternal.purrfectsnap.ui.manager.pages.scripting.ScriptingRootSection
import me.eternal.purrfectsnap.ui.manager.pages.tracker.FriendTrackerManagerRoot
import me.eternal.purrfectsnap.ui.manager.pages.social.themes.AphelionSocialContent
import me.eternal.purrfectsnap.ui.manager.pages.tasks.themes.AphelionTasksContent
import me.eternal.purrfectsnap.ui.manager.pages.features.themes.AphelionFeaturesContent
import me.eternal.purrfectsnap.ui.manager.pages.home.themes.AphelionSettingsContent
import me.eternal.purrfectsnap.ui.manager.pages.home.themes.AphelionAboutContent
import me.eternal.purrfectsnap.ui.manager.pages.home.themes.AphelionLogsContent
import me.eternal.purrfectsnap.ui.manager.pages.scripting.themes.AphelionScriptingContent
import me.eternal.purrfectsnap.ui.manager.pages.tracker.themes.AphelionFriendTrackerContent
import me.eternal.purrfectsnap.common.ui.theme.*

object AphelionTheme : ThemeContract {
    @Composable
    override fun HomeRootSection.HomeScreen(nav: NavBackStackEntry, state: HomeState) {
        AphelionSkinProvider(context.androidContext) { AphelionHomeView(nav, state, routes) }
    }

    @Composable
    override fun HomeSettings.SettingsScreen(nav: NavBackStackEntry) {
        AphelionSkinProvider(context.androidContext) { AphelionSettingsContent(nav) }
    }

    @Composable
    override fun HomeAbout.AboutScreen(nav: NavBackStackEntry) {
        AphelionSkinProvider(context.androidContext) { AphelionAboutContent(nav) }
    }

    @Composable
    override fun HomeLogs.LogsScreen(nav: NavBackStackEntry) {
        AphelionSkinProvider(context.androidContext) { AphelionLogsContent(nav) }
    }

    @Composable
    override fun SocialRootSection.SocialScreen(nav: NavBackStackEntry) {
        AphelionSkinProvider(context.androidContext) { AphelionSocialContent(nav) }
    }

    @Composable
    override fun TasksRootSection.TasksScreen(nav: NavBackStackEntry) {
        AphelionSkinProvider(context.androidContext) { AphelionTasksContent(nav) }
    }

    @Composable
    override fun FeaturesRootSection.FeaturesScreen(nav: NavBackStackEntry) {
        AphelionSkinProvider(context.androidContext) { AphelionFeaturesContent(nav) }
    }

    @Composable
    override fun ScriptingRootSection.ScriptingScreen(nav: NavBackStackEntry) {
        AphelionSkinProvider(context.androidContext) { AphelionScriptingContent(nav) }
    }

    @Composable
    override fun FriendTrackerManagerRoot.FriendTrackerScreen(nav: NavBackStackEntry) {
        AphelionSkinProvider(context.androidContext) { AphelionFriendTrackerContent(nav) }
    }
}
