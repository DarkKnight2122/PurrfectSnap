package me.eternal.purrfectsnap.ui.manager.pages.features.themes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.currentBackStackEntryAsState
import me.eternal.purrfectsnap.common.config.ConfigContainer
import me.eternal.purrfectsnap.common.config.ConfigFlag
import me.eternal.purrfectsnap.common.config.PropertyPair
import me.eternal.purrfectsnap.common.ui.rememberAsyncMutableStateList
import me.eternal.purrfectsnap.ui.manager.Routes
import me.eternal.purrfectsnap.ui.manager.pages.features.FeaturesRootSection
import me.eternal.purrfectsnap.ui.manager.rememberRouteLazyListState
import me.eternal.purrfectsnap.common.ui.theme.PurrfectPalette
import me.eternal.purrfectsnap.ui.util.*
import me.eternal.purrfectsnap.ui.manager.pages.themes.legacy.components.LegacyBackground

/**
 * Modularized Features screen for the "Shell Swap" architecture.
 * 
 * This module implements the legacy visual aesthetic as an extension function of the base logic class.
 * All state management and core logic are hosted within [FeaturesRootSection], while this file
 * handles only the specific layout and components required for this theme.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeaturesRootSection.LegacyFeaturesContent(nav: NavBackStackEntry) {
    val navBackStackEntry by routes.navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val sectionName = navBackStackEntry?.arguments?.getString("name")
    val searchKeyword = navBackStackEntry?.arguments?.getString("keyword")
    
    val container = remember(sectionName) {
        if (sectionName != null) allContainers[sectionName]?.value?.get() as? ConfigContainer
        else context.config.root
    } ?: context.config.root

    val sectionTitle = when (currentDestination?.route) {
        FeaturesRootSection.FEATURE_CONTAINER_ROUTE -> {
            sectionName?.let { name ->
                allContainers[name]?.let { context.translation[it.key.propertyName()] }
            } ?: routeInfo.translatedKey?.value
        }
        FeaturesRootSection.SEARCH_FEATURE_ROUTE -> translation["search_button"]
        else -> routeInfo.translatedKey?.value
    }

    val stateKey = remember(currentDestination?.route, sectionName, searchKeyword) {
        "${routeInfo.id}:${currentDestination?.route}:$sectionName:$searchKeyword"
    }

    // FloatingControls State
    val searchHistory = remember { mutableStateListOf<String>().apply { addAll(loadSearchHistory()) } }
    var liveSearchQuery by rememberSaveable { mutableStateOf(searchKeyword.orEmpty()) }
    val isActiveSearch = currentDestination?.route == FeaturesRootSection.SEARCH_FEATURE_ROUTE || liveSearchQuery.isNotBlank()
    
    val properties = remember(container) {
        container.properties.map { PropertyPair(it.key, it.value) }.filter {
            !it.key.params.flags.contains(ConfigFlag.HIDDEN)
        }
    }

    val globalSearchProperties = remember(container == context.config.root) {
        if (container == context.config.root) {
            allProperties.filter { !it.key.params.flags.contains(ConfigFlag.HIDDEN) }.map { PropertyPair(it.key, it.value) }
        } else emptyList()
    }

    val displayProperties = remember(properties, globalSearchProperties, liveSearchQuery) {
        val query = liveSearchQuery
        if (query.isBlank()) return@remember properties
        val candidates = if (container == context.config.root) (properties + globalSearchProperties) else properties
        candidates.filter {
            it.key.name.contains(query, ignoreCase = true) ||
                context.translation[it.key.propertyName()].contains(query, ignoreCase = true) ||
                context.translation[it.key.propertyDescription()].contains(query, ignoreCase = true)
        }.distinctBy { it.key.propertyName() }
    }

    val density = LocalDensity.current
    var controlsHeight by remember { mutableStateOf(96.dp) }
    val listState = rememberRouteLazyListState(stateKey)
    var configRefreshNonce by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(liveSearchQuery) {
        if (!listState.isScrollInProgress) {
            listState.scrollToItem(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LegacyBackground()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(
                start = 6.dp,
                end = 6.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + controlsHeight + 12.dp,
                bottom = routes.bottomPadding + 16.dp
            )
        ) {
            if (displayProperties.isEmpty()) {
                item { EmptyState(isActiveSearch) }
            } else {
                itemsIndexed(displayProperties, key = { _, item -> item.key.propertyName() }) { _, item ->
                    PropertyCard(
                        property = item,
                        configRefreshNonce = configRefreshNonce,
                        onConfigChanged = { configRefreshNonce++ },
                        onOpen = if (isActiveSearch && liveSearchQuery.isNotBlank()) {
                            { upsertHistory(liveSearchQuery, searchHistory) }
                        } else null
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 0.dp)
                .zIndex(1f)
                .onGloballyPositioned { coordinates ->
                    val newHeight = with(density) { coordinates.size.height.toDp() }
                    if (newHeight != controlsHeight) controlsHeight = newHeight
                }
        ) {
            var showSearchBar by rememberSaveable { mutableStateOf(isActiveSearch) }
            val focusRequester = remember { FocusRequester() }
            var searchValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
                mutableStateOf(TextFieldValue(text = liveSearchQuery, selection = TextRange(liveSearchQuery.length)))
            }
            var showExportDropdownMenu by remember { mutableStateOf(false) }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = PurrfectPalette.cardOverlayColor,
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            PurrfectPalette.glowPrimary.copy(alpha = 0.6f),
                            PurrfectPalette.glowSecondary.copy(alpha = 0.52f)
                        )
                    )
                ),
                tonalElevation = 0.dp,
                shadowElevation = 8.dp
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(26.dp))
                            .background(PurrfectPalette.cardOverlay)
                    )
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (container != context.config.root || isActiveSearch) {
                                IconButton(onClick = { routes.navController.popBackStack() }) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                                }
                            }

                            if (showSearchBar) {
                                TextField(
                                    value = searchValue,
                                    onValueChange = { 
                                        searchValue = it
                                        liveSearchQuery = it.text
                                    },
                                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                                    singleLine = true,
                                    placeholder = { Text(text = translation["search_button"] ?: "Search", color = Color(0xFFE0DCFF)) },
                                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color.White) },
                                    trailingIcon = {
                                        if (searchValue.text.isNotEmpty()) {
                                            IconButton(onClick = {
                                                searchValue = TextFieldValue("", TextRange(0))
                                                liveSearchQuery = ""
                                                if (currentDestination?.route == FeaturesRootSection.SEARCH_FEATURE_ROUTE) routes.navController.popBackStack()
                                                else showSearchBar = false
                                            }) {
                                                Icon(Icons.Filled.Close, null, tint = Color.White)
                                            }
                                        }
                                    },
                                    keyboardActions = KeyboardActions(onDone = {
                                        if (searchValue.text.isNotBlank()) upsertHistory(searchValue.text, searchHistory)
                                    }),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        cursorColor = Color.White,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedPlaceholderColor = Color(0xFFE0DCFF),
                                        unfocusedPlaceholderColor = Color(0xFFE0DCFF),
                                        focusedLeadingIconColor = Color.White,
                                        unfocusedLeadingIconColor = Color.White.copy(alpha = 0.9f)
                                    )
                                )
                                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                            } else {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = sectionTitle ?: "", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                    Text(text = translation["manager.sections.features.subtitle"] ?: "", color = Color(0xFFCEC8FF), fontSize = 12.sp)
                                }
                            }

                            if (!showSearchBar || searchValue.text.isEmpty()) {
                                IconButton(onClick = { showSearchBar = !showSearchBar }) {
                                    Icon(imageVector = if (showSearchBar) Icons.Filled.Close else Icons.Filled.Search, null, tint = Color.White)
                                }
                            }

                            Box {
                                IconButton(onClick = { showExportDropdownMenu = true }) {
                                    Icon(Icons.Filled.MoreVert, null, tint = PurrfectPalette.glowSecondary)
                                }
                                DropdownMenu(
                                    expanded = showExportDropdownMenu,
                                    onDismissRequest = { showExportDropdownMenu = false },
                                    offset = DpOffset(0.dp, 8.dp),
                                    containerColor = Color(0xFF161821),
                                    shape = RoundedCornerShape(14.dp),
                                    tonalElevation = 8.dp,
                                    shadowElevation = 12.dp
                                ) {
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Filled.SaveAlt, null, tint = PurrfectPalette.glowPrimary) },
                                        text = { Text(translation["export_option"] ?: "Export", color = Color.White) },
                                        onClick = { showExportDropdownMenu = false }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Filled.FileDownload, null, tint = PurrfectPalette.glowPrimary) },
                                        text = { Text(translation["import_option"] ?: "Import", color = Color.White) },
                                        onClick = { showExportDropdownMenu = false }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Filled.Refresh, null, tint = PurrfectPalette.glowPrimary) },
                                        text = { Text(translation["reset_option"] ?: "Reset", color = Color.White) },
                                        onClick = { showExportDropdownMenu = false; context.config.reset() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

