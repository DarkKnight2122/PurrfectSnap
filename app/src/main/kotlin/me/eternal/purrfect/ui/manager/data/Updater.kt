package me.eternal.purrfect.ui.manager.data

import com.google.gson.JsonParser
import me.eternal.purrfect.common.BuildConfig
import me.eternal.purrfect.common.TargetApp
import me.eternal.purrfect.common.logger.AbstractLogger
import me.eternal.purrfect.setup.patch.AutoPatchServer
import okhttp3.OkHttpClient
import okhttp3.Request


object Updater {
    const val REDDIT_INSTALLED_RELEASE_TAG_PREF = "reddit_installed_release_tag"

    enum class Channel { STABLE, PRERELEASE }
    enum class UpdateTarget { PURRFECT, REDDIT }

    data class LatestRelease(
        val versionName: String,
        val releaseUrl: String,
        val workflowId: Long?,
        val assetDownloads: Map<String, String> = emptyMap(),
        val repositoryFullName: String = DEFAULT_PURRFECT_REPOSITORY,
        val target: UpdateTarget = UpdateTarget.PURRFECT,
    )

    private data class GithubRepository(
        val owner: String,
        val name: String,
    ) {
        val fullName: String = "$owner/$name"
    }

    private const val DEFAULT_PURRFECT_REPOSITORY = "particle-box/Purrfect"
    private val purrfectRepositories = listOf(
        GithubRepository("particle-box", "Purrfect"),
        GithubRepository("curious-freak", "Purrfect"),
    )
    private val okHttpClient by lazy { OkHttpClient() }
    private val autoPatchServer by lazy { AutoPatchServer() }

    private fun normalizeVersionTag(version: String): String {
        return version.trim().removePrefix("v").removePrefix("V")
    }

    internal fun isVersionGreater(version: String, current: String): Boolean {
        fun segments(raw: String) = raw
            .split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }
            .map { it.toIntOrNull() ?: 0 }

        val v = segments(version)
        val c = segments(current)
        val size = maxOf(v.size, c.size)
        for (i in 0 until size) {
            val vi = v.getOrElse(i) { 0 }
            val ci = c.getOrElse(i) { 0 }
            if (vi > ci) return true
            if (vi < ci) return false
        }
        return false
    }

    private fun fetchLatestRelease(channel: Channel): LatestRelease? {
        purrfectRepositories.forEach { repository ->
            fetchLatestReleaseFromRepository(channel, repository)?.let { return it }
        }
        return null
    }

    private fun fetchLatestReleaseFromRepository(channel: Channel, repository: GithubRepository) = runCatching {
        val endpoint = Request.Builder()
            .url("https://api.github.com/repos/${repository.fullName}/releases")
            .build()
        val response = okHttpClient.newCall(endpoint).execute()

        if (!response.isSuccessful) throw Throwable("Failed to fetch releases from ${repository.fullName}: ${response.code}")

        val releases = JsonParser.parseString(response.body?.string()).asJsonArray.also {
            if (it.size() == 0) throw Throwable("No releases found")
        }

        val currentVersion = BuildConfig.VERSION_NAME
        val latestRelease = releases.mapNotNull { it.asJsonObject }.firstOrNull { release ->
            if (release.get("draft")?.asBoolean != false) return@firstOrNull false
            val matchesChannel = when (channel) {
                Channel.STABLE -> release.get("prerelease")?.asBoolean == false
                Channel.PRERELEASE -> release.get("prerelease")?.asBoolean == true
            }
            if (!matchesChannel) return@firstOrNull false
            val latestVersion = release.getAsJsonPrimitive("tag_name")?.asString?.let(::normalizeVersionTag) ?: return@firstOrNull false
            isVersionGreater(latestVersion, currentVersion)
        } ?: throw Throwable("No matching releases found for $channel channel in ${repository.fullName}")

        val latestVersion = normalizeVersionTag(latestRelease.getAsJsonPrimitive("tag_name").asString)
        if (latestVersion == BuildConfig.VERSION_NAME) return@runCatching null
        val assets = latestRelease.getAsJsonArray("assets")?.mapNotNull { element ->
            val obj = element.asJsonObject
            val name = obj.getAsJsonPrimitive("name")?.asString?.lowercase() ?: return@mapNotNull null
            val url = obj.getAsJsonPrimitive("browser_download_url")?.asString ?: return@mapNotNull null
            name to url
        } ?: emptyList()

        val assetDownloads = buildMap<String, String> {
            assets.forEach { (name, url) ->
                when {
                    name.contains("arm64") || name.contains("armv8") -> put("arm64", url)
                    name.contains("armeabi") || name.contains("armv7") || name.contains("arm32") -> put("armv7", url)
                }
            }
        }

        LatestRelease(
            versionName = latestVersion,
            releaseUrl = latestRelease.getAsJsonPrimitive("html_url")?.asString
                ?: endpoint.url.toString().replace("api.", "").replace("repos/", ""),
            workflowId = null,
            assetDownloads = assetDownloads,
            repositoryFullName = repository.fullName,
        )
    }.onFailure {
        AbstractLogger.directError("Failed to fetch latest release ($channel) from ${repository.fullName}", it)
    }.getOrNull()

    private fun fetchLatestDebugCI(): LatestRelease? {
        purrfectRepositories.forEach { repository ->
            fetchLatestDebugCIFromRepository(repository)?.let { return it }
        }
        return null
    }

    private fun fetchLatestDebugCIFromRepository(repository: GithubRepository) = runCatching {
        val actionRuns = okHttpClient.newCall(
            Request.Builder()
                .url("https://api.github.com/repos/${repository.fullName}/actions/runs?event=workflow_dispatch&branch=dev")
                .build()
        ).execute().use {
            if (!it.isSuccessful) throw Throwable("Failed to fetch CI runs: ${it.code}")
            JsonParser.parseString(it.body?.string()).asJsonObject
        }
        val debugRuns = actionRuns.getAsJsonArray("workflow_runs")?.mapNotNull { it.asJsonObject }?.filter { run ->
            run.get("conclusion")?.takeIf { it.isJsonPrimitive }?.asString == "success" && run.getAsJsonPrimitive("path")?.asString == ".github/workflows/debug.yml"
        } ?: throw Throwable("No debug CI runs found")

        val latestRun = debugRuns.firstOrNull() ?: throw Throwable("No debug CI runs found")
        val headSha = latestRun.getAsJsonPrimitive("head_sha")?.asString ?: throw Throwable("No head sha found")

        if (headSha == BuildConfig.GIT_HASH) return@runCatching null

        LatestRelease(
            versionName = headSha.substring(0, headSha.length.coerceAtMost(7)) + "-debug",
            releaseUrl = latestRun.getAsJsonPrimitive("html_url")?.asString ?: return@runCatching null,
            workflowId = latestRun.getAsJsonPrimitive("id")?.asLong,
            repositoryFullName = repository.fullName,
        )
    }.onFailure {
        AbstractLogger.directError("Failed to fetch latest debug CI from ${repository.fullName}", it)
    }.getOrNull()

    private val cache = mutableMapOf<Channel, LatestRelease?>()
    private val redditUpdateCache = mutableMapOf<String, LatestRelease?>()

    fun getLatestRelease(channel: Channel): LatestRelease? {
        return cache.getOrPut(channel) {
            if (BuildConfig.DEBUG && channel == Channel.STABLE) {
                fetchLatestDebugCI() ?: fetchLatestRelease(Channel.STABLE)
            } else {
                fetchLatestRelease(channel)
            }
        }
    }

    fun getLatestRedditUpdate(installedTag: String?): LatestRelease? {
        val currentTag = installedTag
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeVersionTag)
            ?: return null

        return redditUpdateCache.getOrPut(currentTag) {
            runCatching {
                val latest = autoPatchServer.fetchLatestApk(TargetApp.REDDIT) ?: return@runCatching null
                val latestVersion = normalizeVersionTag(latest.tagName)
                if (!isVersionGreater(latestVersion, currentTag)) return@runCatching null
                LatestRelease(
                    versionName = latestVersion,
                    releaseUrl = latest.downloadUrl,
                    workflowId = null,
                    assetDownloads = mapOf("apk" to latest.downloadUrl),
                    target = UpdateTarget.REDDIT,
                )
            }.onFailure {
                AbstractLogger.directError("Failed to fetch latest Reddit update", it)
            }.getOrNull()
        }
    }

    fun clearRedditUpdateCache() {
        redditUpdateCache.clear()
    }
}
