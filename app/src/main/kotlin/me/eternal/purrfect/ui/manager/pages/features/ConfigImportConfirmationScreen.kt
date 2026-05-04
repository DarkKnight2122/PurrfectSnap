package me.eternal.purrfect.ui.manager.pages.features

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.eternal.purrfect.bridge.location.LocationCoordinates
import me.eternal.purrfect.storage.addOrUpdateLocationCoordinate
import me.eternal.purrfect.storage.getLocationCoordinates
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.manager.theme.PurrfectPalette
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class ConfigImportConfirmationScreen : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.features.config_import") }

    private data class ImportedFeature(
        val category: String,
        val name: String,
        val key: String,
        val value: Any,
        val indentation: Int
    )

    companion object {
        private const val COORDINATE_TOLERANCE = 0.0001
    }

    private fun importSavedLocations(locationsArray: com.google.gson.JsonArray) {
        val existingLocations = context.database.getLocationCoordinates()
        for (i in 0 until locationsArray.size()) {
            val locationObj = locationsArray.get(i).asJsonObject
            val name = locationObj.get("name")?.asString ?: continue
            val latitude = locationObj.get("latitude")?.asDouble ?: continue
            val longitude = locationObj.get("longitude")?.asDouble ?: continue
            val radius = locationObj.get("radius")?.asDouble ?: 100.0

            val existingMatch = existingLocations.find { existing ->
                abs(existing.latitude - latitude) < COORDINATE_TOLERANCE &&
                abs(existing.longitude - longitude) < COORDINATE_TOLERANCE
            }

            if (existingMatch == null) {
                val newLocation = LocationCoordinates().apply {
                    this.name = name
                    this.latitude = latitude
                    this.longitude = longitude
                    this.radius = radius
                }
                context.database.addOrUpdateLocationCoordinate(null, newLocation)
            }
        }
    }

    private inner class ConfigParser {
        fun parse(configJson: String): Map<String, List<ImportedFeature>> {
            val featureList = mutableListOf<ImportedFeature>()
            val json = JSONObject(configJson)
            fun parseProperties(categoryKey: String, niceCategoryName: String, properties: JSONObject, prefix: String, indent: Int) {
                for (key in properties.keys()) {
                    val value = properties.get(key)
                    val currentPrefix = if (prefix.isEmpty()) key else "$prefix.$key"
                    if (value is JSONObject && value.has("state") && value.has("properties")) {
                        val featureNameKey = "features.properties.$categoryKey.properties.${currentPrefix.split('.').joinToString(".properties.")}.name"
                        val featureName = context.translation[featureNameKey] ?: key
                        featureList.add(ImportedFeature(niceCategoryName, featureName, key, value.getBoolean("state"), indent))
                        parseProperties(categoryKey, niceCategoryName, value.getJSONObject("properties"), currentPrefix, indent + 1)
                    } else if (value is JSONObject && value.has("properties")) {
                        parseProperties(categoryKey, niceCategoryName, value.getJSONObject("properties"), currentPrefix, indent)
                    } else {
                        val featureNameKey = "features.properties.$categoryKey.properties.${currentPrefix.split('.').joinToString(".properties.")}.name"
                        val featureName = context.translation[featureNameKey] ?: key
                        featureList.add(ImportedFeature(niceCategoryName, featureName, key, value, indent))
                    }
                }
            }
            for (categoryKey in json.keys()) {
                val value = json.get(categoryKey)
                if (value is JSONObject) {
                    val niceCategoryName = context.translation["features.properties.$categoryKey.name"] ?: categoryKey.replaceFirstChar { it.uppercase() }
                    if (value.has("state") && !value.has("properties")) {
                        featureList.add(ImportedFeature(niceCategoryName, translation["enable_feature"], categoryKey, value.getBoolean("state"), 0))
                    } else if (value.has("properties")) {
                        parseProperties(categoryKey, niceCategoryName, value.getJSONObject("properties"), "", 0)
                    }
                }
            }
            return featureList.groupBy { it.category }
        }

        fun parseValue(featureKey: String, value: Any): Any {
            fun innerParse(v: Any): String {
                if (v is String) {
                    if (v.isBlank()) {
                        val emptyKey = "features.options.$featureKey.empty"
                        val translatedEmpty = context.translation[emptyKey]
                        val fallback = context.translation["features.options.empty"]?.takeUnless { it == "features.options.empty" }
                        return if (!translatedEmpty.isNullOrBlank() && translatedEmpty != emptyKey && !translatedEmpty.startsWith("features.")) translatedEmpty else (fallback ?: "Empty")
                    }
                    val translationKey = "features.options.$featureKey.$v"
                    val translated = context.translation[translationKey]
                    return if (!translated.isNullOrBlank() && translated != translationKey && !translated.startsWith("features.")) translated else v
                }
                return v.toString()
            }
            return when (value) {
                is Boolean -> if (value) translation["enabled"] else translation["disabled"]
                is JSONArray -> {
                    val list = mutableListOf<String>()
                    for (i in 0 until value.length()) {
                        list.add(innerParse(value.get(i)))
                    }
                    list
                }
                else -> innerParse(value)
            }
        }
    }

    override val content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit = {
        val parser = remember { ConfigParser() }
        val activeTargetJson = remember {
            routes.configJsonForImport?.let { json ->
                ScopedConfigJson.importForActiveTarget(context, json)
            }
        }
        val featuresByCategory = remember {
            activeTargetJson?.let { parser.parse(it) } ?: emptyMap()
        }
        val expandedState = remember { mutableStateMapOf<String, Boolean>() }
        val importLabel = translation["confirm_button"] ?: "Confirm Import"

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PurrfectPalette.backgroundGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // 1. Sleek Top Header
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 12.dp,
                    border = BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                                PurrfectPalette.glowSecondary.copy(alpha = 0.45f)
                            )
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .background(PurrfectPalette.cardOverlay, RoundedCornerShape(24.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { routes.navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = context.translation["common.back"],
                                tint = Color.White
                            )
                        }

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = translation["title"] ?: "Import Confirmation",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }

                        Spacer(Modifier.width(48.dp))
                    }
                }

                // 2. Scrollable Content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 140.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        activeTargetJson?.let { json ->
                            ConfigPreviewer(configJson = json)
                        }
                    }

                    items(featuresByCategory.toList()) { pair ->
                        val category = pair.first
                        val features = pair.second
                        val isExpanded = expandedState[category] ?: false
                        val rotationState by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedState[category] = !isExpanded },
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White.copy(alpha = 0.05f),
                            tonalElevation = 0.dp,
                            border = BorderStroke(
                                1.dp,
                                if (isExpanded) Brush.linearGradient(
                                    listOf(
                                        PurrfectPalette.glowPrimary.copy(alpha = 0.6f),
                                        PurrfectPalette.glowSecondary.copy(alpha = 0.5f)
                                    )
                                ) else SolidColor(Color.White.copy(alpha = 0.12f))
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = category,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = if (isExpanded) PurrfectPalette.glowPrimary else Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { expandedState[category] = !isExpanded }) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.graphicsLayer(rotationZ = rotationState),
                                            tint = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        features.forEach { feature ->
                                            val parsedValue = parser.parseValue(feature.key, feature.value)
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = (feature.indentation * 12).dp)
                                            ) {
                                                Text(
                                                    text = feature.name,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                if (parsedValue is List<*>) {
                                                    parsedValue.forEach { item ->
                                                        Text(
                                                            text = "• ${item.toString()}",
                                                            fontSize = 13.sp,
                                                            color = PurrfectPalette.textSecondary,
                                                            fontWeight = FontWeight.Normal
                                                        )
                                                    }
                                                } else {
                                                    Text(
                                                        text = parsedValue.toString(),
                                                        fontSize = 13.sp,
                                                        color = PurrfectPalette.textSecondary,
                                                        fontWeight = FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            // 3. Fixed Bottom Action Bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .navigationBarsPadding()
            ) {
                Button(
                    onClick = {
                        activeTargetJson?.let { json ->
                            runCatching {
                                val savedLocationsJson = context.config.loadFromString(json)
                                savedLocationsJson?.let { locationsArray ->
                                    importSavedLocations(locationsArray)
                                }
                            }.onFailure { err ->
                                context.longToast(
                                    context.translation.format(
                                        "config_import_failure_toast",
                                        "error" to (err.message ?: context.translation["common.unknown_error"])
                                    )
                                )
                            }
                            context.shortToast(translation["config_imported_toast"] ?: "Settings imported successfully")
                            context.coroutineScope.launch(Dispatchers.Main) {
                                routes.features.navigateReload()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        PurrfectPalette.glowPrimary,
                                        PurrfectPalette.glowSecondary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = importLabel,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}