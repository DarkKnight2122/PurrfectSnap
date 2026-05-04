package me.eternal.purrfectsnap.ui.manager.pages.features

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.eternal.purrfectsnap.ui.manager.theme.PurrfectPalette
import org.json.JSONObject

@Composable
fun ConfigPreviewer(
    configJson: String
) {
    val sections = remember(configJson) {
        runCatching {
            val root = JSONObject(configJson)
            val experimental = root.optJSONObject("experimental")
            val messaging = root.optJSONObject("messaging")
            val privacy = root.optJSONObject("privacy")

            val list = mutableListOf<ConfigSection>()

            // Spoofs & Experimental
            experimental?.let { exp ->
                val native = exp.optJSONObject("native_hooks")
                val spoof = exp.optJSONObject("spoof")
                
                val items = mutableListOf<ConfigItem>()
                spoof?.optJSONObject("randomize_device_profile")?.let {
                    items.add(ConfigItem("Device Spoofer", if (it.optBoolean("global_state", false)) "Enabled" else "Disabled"))
                }
                native?.optJSONObject("valdi_hooks")?.let {
                    if (it.optBoolean("global_state", false)) items.add(ConfigItem("System Armor", "Active"))
                }
                
                if (items.isNotEmpty()) {
                    list.add(ConfigSection("Spoofs & Experimental", Icons.Default.Science, items))
                }
            }

            // Privacy & Stealth
            privacy?.let { priv ->
                val stealth = priv.optJSONObject("stealth_mode")
                val items = mutableListOf<ConfigItem>()
                
                if (priv.optBoolean("global_state", false)) {
                    items.add(ConfigItem("Privacy Shield", "Active"))
                }
                stealth?.let {
                    if (it.optBoolean("global_state", false)) items.add(ConfigItem("Stealth Mode", "Enabled"))
                }

                if (items.isNotEmpty()) {
                    list.add(ConfigSection("Privacy & Stealth", Icons.Default.Shield, items))
                }
            }

            // Messaging & Loops
            messaging?.let { msg ->
                val override = msg.optJSONObject("gallery_media_send_override")
                val items = mutableListOf<ConfigItem>()
                
                override?.optString("mode")?.let {
                    if (it != "ORIGINAL") items.add(ConfigItem("Send Override", it))
                }
                if (msg.optBoolean("auto_download", false)) items.add(ConfigItem("Auto Download", "Enabled"))

                if (items.isNotEmpty()) {
                    list.add(ConfigSection("Messaging & Loops", Icons.Default.Message, items))
                }
            }

            list
        }.getOrDefault(emptyList())
    }

    if (sections.isEmpty()) {
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sections) { section ->
            ConfigSectionCard(section)
        }
    }
}

@Composable
private fun ConfigSectionCard(section: ConfigSection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                tint = PurrfectPalette.glowPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = section.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PurrfectPalette.glowPrimary
            )
        }

        section.items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(item.label, fontSize = 11.sp, color = PurrfectPalette.textSecondary.copy(alpha = 0.85f))
                Text(item.value, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private data class ConfigSection(
    val title: String,
    val icon: ImageVector,
    val items: List<ConfigItem>
)

private data class ConfigItem(
    val label: String,
    val value: String
)
