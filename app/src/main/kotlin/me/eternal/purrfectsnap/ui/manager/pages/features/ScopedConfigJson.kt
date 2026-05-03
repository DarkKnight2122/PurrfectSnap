package me.eternal.purrfectsnap.ui.manager.pages.features

import me.eternal.purrfectsnap.RemoteSideContext
import org.json.JSONObject

internal object ScopedConfigJson {
    fun exportForActiveTarget(
        context: RemoteSideContext,
        exportSensitiveData: Boolean,
        includeSavedLocations: Boolean,
        savedLocations: List<me.eternal.purrfectsnap.bridge.location.LocationCoordinates>? = null
    ): String {
        val full = JSONObject(context.config.exportToString(exportSensitiveData, includeSavedLocations, savedLocations))
        return if (context.isRedditMode) {
            JSONObject()
                .put("reddit", full.optJSONObject("reddit") ?: JSONObject(context.getRedditFeaturesJson()))
                .put("_target_app", "reddit")
                .toString(2)
        } else {
            full.remove("reddit")
            full.put("_target_app", "snapchat")
            full.toString(2)
        }
    }

    fun importForActiveTarget(context: RemoteSideContext, json: String): String {
        val input = JSONObject(json)
        return if (context.isRedditMode) {
            val reddit = input.optJSONObject("reddit") ?: input
            val redditContainer = if (reddit.has("properties")) {
                reddit
            } else {
                JSONObject()
                    .put("state", JSONObject.NULL)
                    .put("properties", reddit)
            }
            JSONObject()
                .put("reddit", redditContainer)
                .put("_target_app", "reddit")
                .toString()
        } else {
            input.remove("reddit")
            input.put("_target_app", "snapchat")
            input.toString()
        }
    }
}
