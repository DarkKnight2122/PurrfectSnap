package me.eternal.purrfectsnap.core.features.impl.global

import android.content.res.Resources
import me.eternal.purrfectsnap.core.features.Feature
import me.eternal.purrfectsnap.core.util.hook.HookStage
import me.eternal.purrfectsnap.core.util.hook.hook

/**
 * Custom Personal Branding Hook
 * Injects a personalized signature into the Snapchat "About" section.
 */
class Branding : Feature("Branding") {
    override fun init() {
        val packageInfo = context.mappings.getSnapchatPackageInfo() ?: return
        val versionName = packageInfo.versionName ?: return

        // Hook all 'getString' methods in android.content.res.Resources
        // This is the best way to intercept the version string without targeting obfuscated Snapchat classes
        Resources::class.java.methods.filter { 
            it.name == "getString" && it.returnType == String::class.java 
        }.forEach { method ->
            method.hook(HookStage.AFTER) { param ->
                val result = param.getResult() as? String ?: return@hook
                
                // Target the specific string that contains both the location and the version
                // This ensures "Made by Kal" appears at the very top of the stack.
                if (result.contains("Made in Los Angeles", ignoreCase = false) && result.contains(versionName)) {
                    val customSignature = "Made by Kal with \u2764\ufe0f\n"
                    param.setResult(customSignature + result)
                }
            }
        }
    }
}
