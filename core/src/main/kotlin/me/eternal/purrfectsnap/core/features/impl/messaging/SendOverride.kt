package me.eternal.purrfectsnap.core.features.impl.messaging

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import me.eternal.purrfectsnap.common.ui.createComposeAlertDialog
import me.eternal.purrfectsnap.core.event.events.impl.NativeUnaryCallEvent
import me.eternal.purrfectsnap.core.features.Feature
import me.eternal.purrfectsnap.core.features.impl.experiments.MediaFilePicker

class SendOverride : Feature("Send Override") {
    companion object {
        private var queuedRepeatCount = 0
        private var queuedOverrideType: String? = null

        private fun queueRepeats(count: Int, type: String) {
            queuedRepeatCount = count
            queuedOverrideType = type
            MediaFilePicker.setQueuedOverrideType(type)
        }

        private fun clearRepeats() {
            queuedRepeatCount = 0
            queuedOverrideType = null
        }

        fun handleQueuedOriginalItemRepeatSuccess(): Boolean {
            if (queuedRepeatCount <= 0) {
                clearRepeats()
                return false
            }
            val result = MediaFilePicker.sendReusableOriginalItem()
            if (result) {
                queuedRepeatCount--
            } else {
                clearRepeats()
            }
            return result
        }
    }

    override fun init() {
        val config = context.config.messaging.galleryMediaSendOverride
        
        context.event.subscribe(NativeUnaryCallEvent::class) { event ->
            if (event.uri != "/messagingcoreservice.MessagingCoreService/SendMessage") return@subscribe

            val pe = me.eternal.purrfectsnap.common.util.protobuf.ProtoReader(event.buffer)
            val convId = pe.getString(1, 1) ?: return@subscribe
            
            // v1.5.9 Alignment: Use feedDisplayName
            val recipientEntry = context.database.getFeedEntryByConversationId(convId)
            val recipientName = recipientEntry?.feedDisplayName ?: convId

            context.coroutineScope.launch(Dispatchers.Main) {
                val dialog = createComposeAlertDialog(context.mainActivity!!) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Send Options", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                        var selectedType by remember { mutableStateOf(config.mode.get()) }
                        var continuousEnabled by remember { mutableStateOf(false) }
                        var repeatCountString by remember { mutableStateOf("1") }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("SNAP", "ORIGINAL", "SAVEABLE_SNAP").forEach { t ->
                                FilterChip(
                                    selected = selectedType == t,
                                    onClick = { selectedType = t },
                                    label = { Text(t, fontSize = 10.sp) }
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = continuousEnabled, onCheckedChange = { continuousEnabled = it })
                            Text("Continuous Send", modifier = Modifier.weight(1f))
                            if (continuousEnabled) {
                                OutlinedTextField(
                                    value = repeatCountString,
                                    onValueChange = { repeatCountString = it },
                                    modifier = Modifier.width(80.dp),
                                    label = { Text("Count") }
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { /* dismiss handled by createComposeAlertDialog logic */ }) { Text("Cancel") }
                            Button(onClick = {
                                val count = repeatCountString.toIntOrNull() ?: 1
                                if (count > 1) {
                                    queueRepeats(count - 1, selectedType)
                                }
                                event.adapter.invokeOriginal()
                            }) {
                                Text("Send")
                            }
                        }
                    }
                }
                dialog.show()
            }
        }
    }
}
