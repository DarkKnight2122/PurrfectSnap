package me.eternal.purrfect.ui.manager.pages.themes.legacy

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import me.eternal.purrfect.ui.manager.ThemeContract
import me.eternal.purrfect.ui.manager.pages.TasksRootSection
import me.eternal.purrfect.ui.manager.pages.features.FeaturesRootSection
import me.eternal.purrfect.ui.manager.pages.home.HomeAbout
import me.eternal.purrfect.ui.manager.pages.home.HomeRootSection
import me.eternal.purrfect.ui.manager.pages.home.HomeSettings
import me.eternal.purrfect.ui.manager.pages.home.HomeLogs
import me.eternal.purrfect.ui.manager.pages.scripting.ScriptingRootSection
import me.eternal.purrfect.ui.manager.pages.social.SocialRootSection
import me.eternal.purrfect.ui.manager.pages.tracker.FriendTrackerManagerRoot
import me.eternal.purrfect.ui.manager.pages.home.themes.LegacySettingsContent
import me.eternal.purrfect.ui.manager.pages.home.themes.LegacyAboutContent
import me.eternal.purrfect.ui.manager.pages.home.themes.LegacyLogsContent
import me.eternal.purrfect.ui.manager.pages.social.themes.LegacySocialContent
import me.eternal.purrfect.ui.manager.pages.tasks.themes.LegacyTasksContent
import me.eternal.purrfect.ui.manager.pages.features.themes.LegacyFeaturesContent
import me.eternal.purrfect.ui.manager.pages.scripting.themes.LegacyScriptingContent
import me.eternal.purrfect.ui.manager.pages.tracker.themes.LegacyFriendTrackerContent

import me.eternal.purrfect.common.ui.theme.AphelionSkinProvider

object LegacyTheme : ThemeContract {
    @Composable override fun HomeRootSection.HomeScreen(nav: NavBackStackEntry) = LegacyHomeView(nav, routes)
    @Composable override fun HomeSettings.SettingsScreen(nav: NavBackStackEntry) = LegacySettingsContent(nav)
    @Composable override fun HomeAbout.AboutScreen(nav: NavBackStackEntry) = LegacyAboutContent(nav)
    @Composable override fun HomeLogs.LogsScreen(nav: NavBackStackEntry) = LegacyLogsContent(nav)
    @Composable override fun SocialRootSection.SocialScreen(nav: NavBackStackEntry) = LegacySocialContent(nav)
    @Composable override fun TasksRootSection.TasksScreen(nav: NavBackStackEntry) = LegacyTasksContent(nav)
    @Composable override fun FeaturesRootSection.FeaturesScreen(nav: NavBackStackEntry) = LegacyFeaturesContent(nav)
    @Composable override fun ScriptingRootSection.ScriptingScreen(nav: NavBackStackEntry) = LegacyScriptingContent(nav)
    @Composable override fun FriendTrackerManagerRoot.FriendTrackerScreen(nav: NavBackStackEntry) = LegacyFriendTrackerContent(nav)
}
