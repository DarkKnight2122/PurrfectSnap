package me.eternal.purrfect.mapper.impl

import me.eternal.purrfect.mapper.AbstractClassMapper
import me.eternal.purrfect.mapper.ext.getClassName

class PlatformPresenceActionWrapperMapper : AbstractClassMapper("PlatformPresenceActionWrapper") {
    val classReference = classReference("class")

    init {
        mapper {
            classes.firstOrNull { classDef ->
                classDef.fields.any { field ->
                    field.type == "Lcom/snap/presence/PlatformChatVisibleAction;"
                }
            }?.let { classReference.set(it.getClassName()) }
        }
    }
}
