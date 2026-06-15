package me.eternal.purrfect.core.instagram

import android.content.Context
import android.os.Handler
import android.os.Looper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal class InstagramDevOptionsMetaConfig(
    private val context: Context,
    private val classLoader: ClassLoader,
    private val dexBridge: InstagramDexKitBridge,
    private val isDevEnabled: () -> Boolean
) {
    companion object {
        private const val MAX_DIRECT_META_CONFIG_SEARCH_RESULTS = 12
        private val mainHandler = Handler(Looper.getMainLooper())
        private val searchExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "PurrfectInsta-MetaConfigSearch").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        }
        private val searchSequence = AtomicInteger()
        private val hookedMethods = Collections.synchronizedSet(HashSet<String>())
        private val helperClassNames = Collections.synchronizedSet(HashSet<String>())
        private val generatedClassNames = Collections.synchronizedSet(HashSet<String>())
        private val searchResultRunnableClassNames = Collections.synchronizedSet(HashSet<String>())
        @Volatile private var nameHints: Map<Long, NameHint>? = null
        @Volatile private var universeLabels: Map<String, String>? = null
        @Volatile private var searchIndex: MetaConfigSearchIndex? = null
        @Volatile private var pendingSearchKey: String? = null
        @Volatile private var pendingSearchAtMs: Long = 0L
        @Volatile private var lastRenderAtMs: Long = 0L
        @Volatile private var nameHintsLogged = false
    }

    @Volatile private var stableWorkaroundsActive = false

    fun install() {
        if (shouldInstallStableMetaConfigWorkarounds()) {
            stableWorkaroundsActive = true
            hookReadableStableMetaConfigNames()
        }
    }

    private fun isPatchActive(): Boolean = isDevEnabled() || stableWorkaroundsActive

    private fun shouldInstallStableMetaConfigWorkarounds(): Boolean {
        val releaseChannel = detectInstagramReleaseChannel()
        if (releaseChannel == "PROD" || releaseChannel == "PUBLIC") {
            log("Installing stable MetaConfig name/search hooks; channel=$releaseChannel")
            return true
        }
        if (releaseChannel == "BETA" || releaseChannel == "ALPHA") {
            log("Skipping stable MetaConfig hooks; channel=$releaseChannel")
            return false
        }

        val versionName = getInstagramVersionName()
        if (versionName != null && versionName.contains(".0.0.0.")) {
            log("Skipping stable MetaConfig hooks; alpha-like version=$versionName")
            return false
        }
        if (versionName != null && versionName.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
            log("Installing stable MetaConfig name/search hooks; channel=UNKNOWN_STABLE version=$versionName")
            return true
        }

        log("Skipping stable MetaConfig hooks; release channel unknown${versionName?.let { " (version=$it)" }.orEmpty()}")
        return false
    }

    private fun detectInstagramReleaseChannel(): String? {
        detectReleaseChannelFromKnownClasses()?.let { return it }
        detectReleaseChannelFromHeaderBuilder()?.let { return it }
        return null
    }

    private fun detectReleaseChannelFromKnownClasses(): String? {
        detectReleaseChannelFromBuildHeaderClass("X.Ok2")?.let { return it }
        detectReleaseChannelFromBuildHeaderClass("p000X.AbstractC63155Ok2")?.let { return it }
        detectReleaseChannelFromEnumClass("X.C6g")?.let { return it }
        detectReleaseChannelFromEnumClass("p000X.EnumC31212C6g")?.let { return it }
        return null
    }

    private fun detectReleaseChannelFromBuildHeaderClass(className: String): String? {
        return runCatching {
            val clazz = Class.forName(className, false, classLoader)
            clazz.declaredMethods.forEach { method ->
                if (!Modifier.isStatic(method.modifiers)) return@forEach
                if (method.returnType != String::class.java) return@forEach
                val params = method.parameterTypes
                if (params.size != 1 || !Context::class.java.isAssignableFrom(params[0])) return@forEach
                method.isAccessible = true
                val header = method.invoke(null, context) as? String
                parseReleaseChannelFromBuildHeader(header)?.let { return it }
            }
            null
        }.getOrNull()
    }

    private fun parseReleaseChannelFromBuildHeader(header: String?): String? {
        if (header == null) return null
        val prefix = header.substringBefore(':', header)
        return normalizeReleaseChannel(prefix)
    }

    private fun detectReleaseChannelFromEnumClass(className: String): String? {
        return runCatching {
            val channelClass = Class.forName(className, false, classLoader)
            val providerField = channelClass.getDeclaredField("A02").apply { isAccessible = true }
            val provider = providerField.get(null) ?: return@runCatching null
            val known = findNoArgMethod(provider.javaClass, "A01")
            invokeReleaseChannelProvider(provider, known, channelClass)?.let { return@runCatching it }
            provider.javaClass.declaredMethods.forEach { method ->
                if (method.parameterTypes.isNotEmpty() || Modifier.isStatic(method.modifiers)) return@forEach
                if (!channelClass.isAssignableFrom(method.returnType)) return@forEach
                invokeReleaseChannelProvider(provider, method, channelClass)?.let { return@runCatching it }
            }
            null
        }.getOrNull()
    }

    private fun findNoArgMethod(clazz: Class<*>?, name: String): Method? {
        if (clazz == null) return null
        return runCatching { clazz.getDeclaredMethod(name).takeIf { it.parameterTypes.isEmpty() } }.getOrNull()
    }

    private fun invokeReleaseChannelProvider(provider: Any?, method: Method?, channelClass: Class<*>): String? {
        if (provider == null || method == null) return null
        return runCatching {
            method.isAccessible = true
            val result = method.invoke(provider) ?: return@runCatching null
            normalizeReleaseChannel(result.toString())?.let { return@runCatching it }
            channelClass.declaredFields.forEach { field ->
                if (!Modifier.isStatic(field.modifiers) || !channelClass.isAssignableFrom(field.type)) return@forEach
                field.isAccessible = true
                if (field.get(null) === result) {
                    return@runCatching when (field.name) {
                        "A0C" -> "PROD"
                        "A0A" -> "BETA"
                        "A09" -> "ALPHA"
                        "A0B" -> "NONE"
                        else -> null
                    }
                }
            }
            null
        }.getOrNull()
    }

    private fun detectReleaseChannelFromHeaderBuilder(): String? {
        return dexBridge.findMethodsUsingStrings("Build time:", "%s: v%s (Build #%d)").firstNotNullOfOrNull { method ->
            runCatching {
                if (!Modifier.isStatic(method.modifiers)) return@runCatching null
                if (method.returnType != String::class.java) return@runCatching null
                val params = method.parameterTypes
                if (params.size != 1 || !Context::class.java.isAssignableFrom(params[0])) return@runCatching null
                method.isAccessible = true
                parseReleaseChannelFromBuildHeader(method.invoke(null, context) as? String)
            }.getOrNull()
        }
    }

    private fun normalizeReleaseChannel(value: String?): String? {
        val normalized = value?.trim()?.uppercase() ?: return null
        return when {
            normalized.startsWith("PROD") -> "PROD"
            normalized.startsWith("PUBLIC") -> "PUBLIC"
            normalized.startsWith("BETA") -> "BETA"
            normalized.startsWith("ALPHA") -> "ALPHA"
            else -> null
        }
    }

    private fun getInstagramVersionName(): String? {
        return runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }

    private fun hookReadableStableMetaConfigNames() {
        discoverGeneratedMetaConfigNameClasses()
        hookMetaConfigListBuilderClass("X.XFc")
        hookMetaConfigListBuilderClass("p000X.C81585XFc")
        discoverAndHookMetaConfigListBuilderClasses()
        hookMetaConfigSearchRunnableClass("X.QJV")
        hookMetaConfigSearchRunnableClass("p000X.QJV")
        discoverAndHookMetaConfigSearchRunnableClasses()
        discoverMetaConfigSearchResultRendererClasses()
        hookMetaConfigSearchTextCallback()
    }

    private fun discoverAndHookMetaConfigListBuilderClasses() {
        val cached = loadCachedMetaConfigClassNames("DevOptionsMetaConfigListBuilders")
        if (cached.isNotEmpty()) {
            val cachedHooks = cached.count { hookMetaConfigListBuilderClass(it) }
            if (cachedHooks > 0) {
                log("MetaConfig helper discovery used cached class(es): $cachedHooks")
                return
            }
        }
        var hooked = 0
        val discovered = LinkedHashSet<String>()
        dexBridge.findClassNamesUsingStrings("^(ig_|android_|launcher_)+", "(_launcher|_universe)$").forEach { className ->
            if (hookMetaConfigListBuilderClass(className)) {
                hooked++
                discovered += className
                log("MetaConfig helper candidate: $className")
            }
        }
        saveCachedMetaConfigClassNames("DevOptionsMetaConfigListBuilders", discovered)
        log("MetaConfig helper discovery hooked $hooked class(es)")
    }

    private fun hookMetaConfigListBuilderClass(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        return runCatching {
            val helperClass = Class.forName(className, false, classLoader)
            var hooked = false
            helperClass.declaredMethods.forEach { method ->
                when {
                    method.parameterTypes.isEmpty() && List::class.java.isAssignableFrom(method.returnType) ->
                        hooked = hookMetaConfigListBuilderMethod(method) || hooked
                    method.parameterTypes.size == 1 && method.parameterTypes[0] == String::class.java && method.returnType == String::class.java ->
                        hooked = hookMetaConfigDisplayFormatterMethod(method) || hooked
                }
            }
            if (hooked) helperClassNames.add(className)
            hooked
        }.getOrDefault(false)
    }

    private fun hookMetaConfigDisplayFormatterMethod(formatter: Method): Boolean {
        val key = "${formatter.declaringClass.name}#${formatter.name}#metaconfig_display_name_cleanup"
        if (!hookedMethods.add(key)) return false
        formatter.isAccessible = true
        XposedBridge.hookMethod(
            formatter,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam<*>) {
                    if (!isPatchActive()) return
                    val result = param.result as? String ?: return
                    val raw = param.args?.firstOrNull() as? String
                    val cleaned = getFriendlyMetaConfigDisplayName(raw, result)
                    if (cleaned != result) param.result = cleaned
                }
            }
        )
        log("Hooked MetaConfig display formatter: ${formatter.declaringClass.name}.${formatter.name}")
        return true
    }

    private fun hookMetaConfigListBuilderMethod(buildList: Method): Boolean {
        val key = "${buildList.declaringClass.name}#${buildList.name}#readable_stable_metaconfig_names"
        if (!hookedMethods.add(key)) return false
        buildList.isAccessible = true
        XposedBridge.hookMethod(
            buildList,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam<*>) {
                    if (!isPatchActive()) return
                    val patched = applyReadableNamesToBmdList(param.result)
                    if (patched > 0) log("Restored readable names for $patched MetaConfig entries")
                }
            }
        )
        log("Hooked stable MetaConfig name restore: ${buildList.declaringClass.name}.${buildList.name}")
        return true
    }

    private fun discoverGeneratedMetaConfigNameClasses() {
        val cached = loadCachedMetaConfigClassNames("DevOptionsMetaConfigGeneratedClasses")
        if (cached.isNotEmpty()) {
            val added = cached.count { className -> !className.isNullOrBlank() && generatedClassNames.add(className) }
            if (added > 0) {
                nameHints = null
                universeLabels = null
                searchIndex = null
                nameHintsLogged = false
            }
            log("Loaded $added cached generated MetaConfig name class(es)")
            return
        }
        var added = 0
        val discovered = LinkedHashSet<String>()
        dexBridge.findClassNamesWithFieldNames("CONFIG_ID", "__CONFIG__").forEach { className ->
            if (!className.isNullOrBlank() && generatedClassNames.add(className)) {
                added++
                discovered += className
            }
        }
        if (added > 0) {
            nameHints = null
            universeLabels = null
            searchIndex = null
            nameHintsLogged = false
        }
        saveCachedMetaConfigClassNames("DevOptionsMetaConfigGeneratedClasses", discovered)
        log("Discovered $added generated MetaConfig name class(es)")
    }

    private fun discoverAndHookMetaConfigSearchRunnableClasses() {
        val cached = loadCachedMetaConfigClassNames("DevOptionsMetaConfigSearchRunnables")
        if (cached.isNotEmpty()) {
            val cachedHooks = cached.count { hookMetaConfigSearchRunnableClass(it) }
            if (cachedHooks > 0) {
                log("MetaConfig search discovery used cached class(es): $cachedHooks")
                return
            }
        }
        var hooked = 0
        val discovered = LinkedHashSet<String>()
        hooked += discoverAndHookMetaConfigSearchRunnableClasses(
            "com.instagram.debug.quickexperiment.QuickExperimentCategoriesFragment",
            "java.lang.String",
            discovered
        )
        hooked += discoverAndHookMetaConfigSearchRunnableClasses(
            "Lcom/instagram/debug/quickexperiment/QuickExperimentCategoriesFragment;",
            "Ljava/lang/String;",
            discovered
        )
        saveCachedMetaConfigClassNames("DevOptionsMetaConfigSearchRunnables", discovered)
        log("MetaConfig search discovery hooked $hooked class(es)")
    }

    private fun discoverAndHookMetaConfigSearchRunnableClasses(
        fragmentType: String,
        stringType: String,
        discovered: MutableSet<String>
    ): Int {
        var hooked = 0
        dexBridge.findClassNamesWithFieldTypes(fragmentType, stringType).forEach { className ->
            if (hookMetaConfigSearchRunnableClass(className)) {
                hooked++
                discovered += className
            }
        }
        return hooked
    }

    private fun discoverMetaConfigSearchResultRendererClasses() {
        searchResultRunnableClassNames.add("X.aDo")
        searchResultRunnableClassNames.add("p000X.RunnableC87802aDo")
        val cached = loadCachedMetaConfigClassNames("DevOptionsMetaConfigSearchRenderers")
        if (cached.isNotEmpty()) {
            val added = cached.count { className ->
                isMetaConfigSearchResultRendererClass(className) && searchResultRunnableClassNames.add(className)
            }
            if (added > 0) {
                log("MetaConfig search renderer discovery used cached class(es): $added")
                return
            }
        }
        var discovered = 0
        val discoveredClasses = LinkedHashSet<String>()
        discovered += discoverMetaConfigSearchResultRendererClasses(
            "com.instagram.debug.quickexperiment.QuickExperimentCategoriesFragment",
            "java.util.List",
            discoveredClasses
        )
        discovered += discoverMetaConfigSearchResultRendererClasses(
            "Lcom/instagram/debug/quickexperiment/QuickExperimentCategoriesFragment;",
            "Ljava/util/List;",
            discoveredClasses
        )
        saveCachedMetaConfigClassNames("DevOptionsMetaConfigSearchRenderers", discoveredClasses)
        if (discovered > 0) log("MetaConfig search renderer discovery found $discovered class(es)")
    }

    private fun discoverMetaConfigSearchResultRendererClasses(
        fragmentType: String,
        listType: String,
        discoveredClasses: MutableSet<String>
    ): Int {
        var added = 0
        dexBridge.findClassNamesWithFieldTypes(fragmentType, listType).forEach { className ->
            if (isMetaConfigSearchResultRendererClass(className) && searchResultRunnableClassNames.add(className)) {
                added++
                discoveredClasses += className
            }
        }
        return added
    }

    private fun loadCachedMetaConfigClassNames(key: String): List<String> {
        if (!InstagramDexKitCache.isCacheValid()) return emptyList()
        return InstagramDexKitCache.loadString(key)
            ?.split('\u0000')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    }

    private fun saveCachedMetaConfigClassNames(key: String, classNames: Collection<String>) {
        if (classNames.isEmpty()) return
        InstagramDexKitCache.saveString(key, classNames.filter { it.isNotBlank() }.distinct().joinToString("\u0000"))
    }

    private fun isMetaConfigSearchResultRendererClass(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        return runCatching {
            val clazz = Class.forName(className, false, classLoader)
            if (!Runnable::class.java.isAssignableFrom(clazz)) return@runCatching false
            var fragmentFields = 0
            var listFields = 0
            var instanceFields = 0
            clazz.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers)) return@forEach
                instanceFields++
                when {
                    isQuickExperimentCategoriesFragmentClass(field.type) -> fragmentFields++
                    List::class.java.isAssignableFrom(field.type) -> listFields++
                }
            }
            if (fragmentFields == 0 || listFields == 0 || instanceFields > 4) return@runCatching false
            clazz.declaredConstructors.any { constructor ->
                val params = constructor.parameterTypes
                params.size == 2 &&
                    isQuickExperimentCategoriesFragmentClass(params[0]) &&
                    List::class.java.isAssignableFrom(params[1])
            }
        }.getOrDefault(false)
    }

    private fun hookMetaConfigSearchRunnableClass(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        return runCatching {
            val clazz = Class.forName(className, false, classLoader)
            if (!isLikelyMetaConfigSearchRunnableClass(clazz)) return@runCatching false
            clazz.declaredMethods.firstOrNull { method ->
                method.name == "run" && method.parameterTypes.isEmpty() && method.returnType == Void.TYPE
            }?.let { hookMetaConfigSearchRunMethod(it) } ?: false
        }.getOrDefault(false)
    }

    private fun isLikelyMetaConfigSearchRunnableClass(clazz: Class<*>): Boolean {
        var fragmentFields = 0
        var stringFields = 0
        var instanceFields = 0
        clazz.declaredFields.forEach { field ->
            if (Modifier.isStatic(field.modifiers)) return@forEach
            instanceFields++
            when {
                field.type == String::class.java -> stringFields++
                isQuickExperimentCategoriesFragmentClass(field.type) -> fragmentFields++
            }
        }
        return fragmentFields == 1 && stringFields == 1 && instanceFields <= 4
    }

    private fun isQuickExperimentCategoriesFragmentClass(clazz: Class<*>?): Boolean {
        var current = clazz
        while (current != null) {
            if (current.name == "com.instagram.debug.quickexperiment.QuickExperimentCategoriesFragment") return true
            current = current.superclass
        }
        return false
    }

    private fun hookMetaConfigSearchRunMethod(method: Method): Boolean {
        val key = "${method.declaringClass.name}#${method.name}#metaconfig_search_cache_reset_safe"
        if (!hookedMethods.add(key)) return false
        method.isAccessible = true
        XposedBridge.hookMethod(
            method,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    if (!isPatchActive()) return
                    val fragment = findMetaConfigFragmentInSearchRunnable(param.thisObject)
                    val query = findStringFieldInObject(param.thisObject)
                    if (fragment != null && !query.isNullOrBlank()) {
                        scheduleDirectMetaConfigSearchRender(fragment, query)
                        param.result = null
                        return
                    }
                    if (fragment != null && clearMetaConfigSearchMaps(fragment) > 0) {
                        log("Cleared stale MetaConfig search cache")
                    }
                }
            }
        )
        log("Hooked MetaConfig search cache reset: ${method.declaringClass.name}.${method.name}")
        return true
    }

    private fun findMetaConfigFragmentInSearchRunnable(runnable: Any?): Any? {
        if (runnable == null) return null
        runnable.javaClass.declaredFields.forEach { field ->
            if (Modifier.isStatic(field.modifiers)) return@forEach
            val value = runCatching {
                field.isAccessible = true
                field.get(runnable)
            }.getOrNull()
            if (value != null && isQuickExperimentCategoriesFragmentClass(value.javaClass)) return value
        }
        return null
    }

    private fun findStringFieldInObject(owner: Any?): String? {
        if (owner == null) return null
        var current: Class<*>? = owner.javaClass
        while (current != null) {
            current.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers) || field.type != String::class.java) return@forEach
                val value = runCatching {
                    field.isAccessible = true
                    field.get(owner) as? String
                }.getOrNull()
                if (!value.isNullOrBlank()) return value
            }
            current = current.superclass
        }
        return null
    }

    private fun clearMetaConfigSearchMaps(fragment: Any): Int {
        findFieldInHierarchy(fragment.javaClass, "A02")?.let { field ->
            if (Map::class.java.isAssignableFrom(field.type) && clearMapField(fragment, field)) return 1
        }
        var cleared = 0
        var current: Class<*>? = fragment.javaClass
        while (current != null) {
            current.declaredFields.forEach { field ->
                if (Map::class.java.isAssignableFrom(field.type) && clearMapField(fragment, field)) cleared++
            }
            current = current.superclass
        }
        return cleared
    }

    private fun clearMapField(owner: Any, field: Field): Boolean {
        return runCatching {
            field.isAccessible = true
            if (field.get(owner) == null) return@runCatching false
            field.set(owner, null)
            true
        }.getOrDefault(false)
    }

    private fun hookMetaConfigSearchTextCallback() {
        runCatching {
            val fragmentClass = Class.forName(
                "com.instagram.debug.quickexperiment.QuickExperimentCategoriesFragment",
                false,
                classLoader
            )
            var hooked = 0
            fragmentClass.declaredMethods.forEach { method ->
                val params = method.parameterTypes
                if (method.returnType == Void.TYPE && params.size == 1 && params[0] == String::class.java) {
                    if (hookMetaConfigSearchTextCallbackMethod(method)) hooked++
                }
            }
            log("Hooked direct MetaConfig search callback(s): $hooked")
        }.onFailure { log("Direct MetaConfig search hook failed: ${it.message}") }
    }

    private fun hookMetaConfigSearchTextCallbackMethod(searchCallback: Method): Boolean {
        val key = "${searchCallback.declaringClass.name}#${searchCallback.name}#purrfect_metaconfig_direct_search"
        if (!hookedMethods.add(key)) return false
        searchCallback.isAccessible = true
        XposedBridge.hookMethod(
            searchCallback,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    if (!isPatchActive()) return
                    val query = (param.args?.firstOrNull() as? String)?.trim().orEmpty()
                    if (query.isEmpty()) return
                    setStringField(param.thisObject, "A00", query)
                    scheduleDirectMetaConfigSearchRender(param.thisObject, query)
                    param.result = null
                }
            }
        )
        log("Hooked direct MetaConfig search callback: ${searchCallback.declaringClass.name}.${searchCallback.name}")
        return true
    }

    private fun scheduleDirectMetaConfigSearchRender(fragment: Any?, query: String?) {
        if (!isPatchActive() || fragment == null || query.isNullOrBlank()) return
        val trimmedQuery = query.trim()
        if (normalizeSearchText(trimmedQuery).length < 3) return

        val sequence: Int
        val searchKey = "${System.identityHashCode(fragment)}\u0000$trimmedQuery"
        val now = System.currentTimeMillis()
        synchronized(InstagramDevOptionsMetaConfig::class.java) {
            if (searchKey == pendingSearchKey && now - pendingSearchAtMs < 1_000L) return
            pendingSearchKey = searchKey
            pendingSearchAtMs = now
            sequence = searchSequence.incrementAndGet()
        }

        mainHandler.postDelayed(
            {
                if (!isPatchActive() || sequence != searchSequence.get()) return@postDelayed
                if (!isMetaConfigSearchStillActive(fragment, trimmedQuery)) return@postDelayed
                searchExecutor.execute { renderDirectMetaConfigSearch(fragment, trimmedQuery, sequence) }
            },
            420L
        )
    }

    private fun renderDirectMetaConfigSearch(fragment: Any, query: String, sequence: Int) {
        if (!isPatchActive()) return
        val start = System.currentTimeMillis()
        val index = getMetaConfigSearchIndex() ?: return
        if (sequence != searchSequence.get()) return
        val normalizedQuery = normalizeSearchText(query)
        if (normalizedQuery.isEmpty()) return
        val queryTokens = normalizedQuery.split(' ').toTypedArray()
        val matches = ArrayList<Any>()
        for (entry in index.entries) {
            if (sequence != searchSequence.get()) return
            if (matchesMetaConfigSearchEntry(entry, queryTokens)) {
                matches += entry.item
                if (matches.size >= MAX_DIRECT_META_CONFIG_SEARCH_RESULTS) break
            }
        }

        mainHandler.post {
            if (!isPatchActive() || sequence != searchSequence.get()) return@post
            if (!isMetaConfigSearchStillActive(fragment, query)) return@post
            val now = System.currentTimeMillis()
            if (now - lastRenderAtMs < 750L) return@post
            lastRenderAtMs = now
            val renderStart = System.currentTimeMillis()
            if (renderMetaConfigSearchResultRunnable(fragment, matches)) {
                log(
                    "Direct MetaConfig search rendered ${matches.size}/${index.entries.size} result(s) for query: $query " +
                        "in ${System.currentTimeMillis() - start}ms (ui=${System.currentTimeMillis() - renderStart}ms)"
                )
            }
        }
    }

    private fun isMetaConfigSearchStillActive(fragment: Any, query: String): Boolean {
        val activeQuery = readStringField(fragment, "A00")
        return activeQuery == null || activeQuery == query
    }

    private fun getMetaConfigSearchIndex(): MetaConfigSearchIndex? {
        searchIndex?.let { return it }
        synchronized(InstagramDevOptionsMetaConfig::class.java) {
            searchIndex?.let { return it }
            val allItems = getRestoredMetaConfigItems() ?: return null
            val entries = ArrayList<MetaConfigSearchEntry>(allItems.size)
            allItems.forEach { item ->
                if (item == null) return@forEach
                val haystack = buildMetaConfigSearchHaystack(item)
                if (haystack.isNotEmpty()) entries += MetaConfigSearchEntry(item, haystack)
            }
            return MetaConfigSearchIndex(entries).also {
                searchIndex = it
                log("Built MetaConfig search index with ${entries.size} item(s)")
            }
        }
    }

    private fun getRestoredMetaConfigItems(): List<*>? {
        val classNames = HashSet<String>()
        classNames += "X.XFc"
        classNames += "p000X.C81585XFc"
        synchronized(helperClassNames) { classNames += helperClassNames }
        classNames.forEach { className ->
            runCatching {
                val helperClass = Class.forName(className, false, classLoader)
                helperClass.declaredMethods.forEach { method ->
                    if (!Modifier.isStatic(method.modifiers)) return@forEach
                    if (method.parameterTypes.isNotEmpty()) return@forEach
                    if (!List::class.java.isAssignableFrom(method.returnType)) return@forEach
                    method.isAccessible = true
                    val list = method.invoke(null) as? List<*> ?: return@forEach
                    if (!looksLikeMetaConfigItemList(list)) return@forEach
                    applyReadableNamesToBmdList(list)
                    return list
                }
            }
        }
        return null
    }

    private fun looksLikeMetaConfigItemList(list: List<*>?): Boolean {
        if (list == null) return false
        if (list.isEmpty()) return true
        var checked = 0
        list.forEach { item ->
            if (item == null) return@forEach
            checked++
            if (readLongField(item, "A00") != null &&
                readStringField(item, "A01") != null &&
                readStringField(item, "A02") != null
            ) return true
            if (checked >= 5) return@forEach
        }
        return false
    }

    private fun matchesMetaConfigSearchEntry(entry: MetaConfigSearchEntry, queryTokens: Array<String>): Boolean {
        if (entry.haystack.isEmpty()) return false
        return queryTokens.all { token -> token.isEmpty() || entry.haystack.contains(token) }
    }

    private fun buildMetaConfigSearchHaystack(item: Any?): String {
        val param = readStringField(item, "A01")
        val universe = readStringField(item, "A02")
        val specifier = readLongField(item, "A00")
        val friendlyUniverse = getFriendlyMetaConfigDisplayName(universe, universe?.replace('_', ' ').orEmpty())
        val values = listOfNotNull(
            param,
            param?.replace('_', ' '),
            universe,
            universe?.replace('_', ' '),
            cleanMetaConfigDisplayName(universe?.replace('_', ' ').orEmpty()),
            friendlyUniverse,
            specifier?.toString()
        )
        return normalizeSearchText(values.joinToString(" "))
    }

    private fun normalizeSearchText(value: String?): String {
        if (value == null) return ""
        val sb = StringBuilder()
        var pendingSpace = false
        value.forEach { c ->
            if (c.isLetterOrDigit()) {
                if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
                sb.append(c.lowercaseChar())
                pendingSpace = false
            } else {
                pendingSpace = true
            }
        }
        return sb.toString()
    }

    private fun renderMetaConfigSearchResultRunnable(fragment: Any, matches: List<*>): Boolean {
        val classNames = HashSet<String>()
        classNames += "X.aDo"
        classNames += "p000X.RunnableC87802aDo"
        synchronized(searchResultRunnableClassNames) { classNames += searchResultRunnableClassNames }
        var lastError: Throwable? = null
        classNames.forEach { className ->
            try {
                val runnableClass = Class.forName(className, false, classLoader)
                runnableClass.declaredConstructors.forEach { constructor ->
                    val params = constructor.parameterTypes
                    if (params.size != 2) return@forEach
                    if (!params[0].isAssignableFrom(fragment.javaClass)) return@forEach
                    if (!List::class.java.isAssignableFrom(params[1])) return@forEach
                    constructor.isAccessible = true
                    val runnable = constructor.newInstance(fragment, matches)
                    if (runnable is Runnable) {
                        runnable.run()
                        return true
                    }
                }
            } catch (throwable: Throwable) {
                lastError = throwable
            }
        }
        lastError?.let { log("Direct MetaConfig search render failed: ${it.message}") }
        return false
    }

    private fun applyReadableNamesToBmdList(result: Any?): Int {
        val list = result as? List<*> ?: return 0
        val hints = getMetaConfigNameHints()
        if (hints.isEmpty()) return 0
        var patched = 0
        list.forEach { item ->
            if (item == null) return@forEach
            val specifier = readLongField(item, "A00") ?: return@forEach
            val hint = hints[specifier] ?: return@forEach
            var changed = false
            val param = readStringField(item, "A01")
            val universe = readStringField(item, "A02")
            if (shouldReplaceObfuscatedMetaConfigName(param) || isBetterMetaConfigName(hint.paramName, param)) {
                changed = setStringField(item, "A01", hint.paramName) || changed
            }
            if (hint.universeName != null &&
                (shouldReplaceObfuscatedMetaConfigName(universe) || isBetterRawUniverseName(hint.universeName, universe))
            ) {
                changed = setStringField(item, "A02", hint.universeName) || changed
            }
            if (changed) patched++
        }
        return patched
    }

    private fun getMetaConfigNameHints(): Map<Long, NameHint> {
        nameHints?.let { return it }
        synchronized(InstagramDevOptionsMetaConfig::class.java) {
            nameHints?.let { return it }
            val built = buildMetaConfigNameHints()
            nameHints = built
            if (!nameHintsLogged) {
                nameHintsLogged = true
                log("Built stable MetaConfig name map with ${built.size} entries")
            }
            return built
        }
    }

    private fun buildMetaConfigNameHints(): Map<Long, NameHint> {
        val hints = HashMap<Long, NameHint>()
        val labels = HashMap<String, String>()
        val classNames = HashSet<String>()
        classNames += "com.instagram.realtimeclient.MC"
        classNames += "com.instagram.realtimeclient.clientconfig.MC"
        classNames += "com.instagram.realtimeclient.regionhint.MC"
        classNames += "com.instagram.realtimeclient.C0339MC"
        classNames += "com.instagram.realtimeclient.clientconfig.C0354MC"
        classNames += "com.instagram.realtimeclient.regionhint.C0358MC"
        synchronized(generatedClassNames) { classNames += generatedClassNames }

        var readableClasses = 0
        classNames.forEach { className ->
            runCatching {
                val clazz = Class.forName(className, false, classLoader)
                val before = hints.size
                collectMetaConfigNameHints(hints, labels, clazz)
                if (hints.size > before) readableClasses++
            }
        }
        universeLabels = labels
        log("Readable MetaConfig classes contributing names: $readableClasses/${classNames.size}")
        return hints
    }

    private fun collectMetaConfigNameHints(hints: HashMap<Long, NameHint>, labels: HashMap<String, String>, clazz: Class<*>?) {
        if (clazz == null) return
        collectMetaConfigNameHintsFromUniverse(hints, labels, clazz)
        clazz.declaredClasses.forEach { universeClass ->
            collectMetaConfigNameHintsFromUniverse(hints, labels, universeClass)
        }
    }

    private fun collectMetaConfigNameHintsFromUniverse(
        hints: HashMap<Long, NameHint>,
        labels: HashMap<String, String>,
        universeClass: Class<*>
    ) {
        val universeName = extractRawUniverseName(universeClass)
        val paramNames = ArrayList<String>()
        universeClass.declaredFields.forEach { field ->
            val modifiers = field.modifiers
            if (!Modifier.isStatic(modifiers)) return@forEach
            val paramName = field.name
            if (paramName == "INSTANCE" ||
                paramName == "CONFIG_ID" ||
                paramName == "__CONFIG__" ||
                shouldReplaceObfuscatedMetaConfigName(paramName)
            ) return@forEach
            paramNames += paramName
            val value = runCatching {
                field.isAccessible = true
                field.get(null)
            }.getOrNull()
            val specifier = readLongField(value, "A00") ?: return@forEach
            putBetterNameHint(hints, specifier, NameHint(universeName, paramName))
        }
        val friendlyLabel = buildFriendlyUniverseLabel(universeName, paramNames)
        if (!friendlyLabel.isNullOrEmpty()) labels[normalizeUniverseLookupKey(universeName)] = friendlyLabel
    }

    private fun putBetterNameHint(hints: HashMap<Long, NameHint>, specifier: Long, candidate: NameHint) {
        val current = hints[specifier]
        if (current == null || nameHintQuality(candidate) > nameHintQuality(current)) hints[specifier] = candidate
    }

    private fun nameHintQuality(hint: NameHint?): Int {
        if (hint == null) return Int.MIN_VALUE
        return metaConfigNameQuality(hint.paramName) * 3 + metaConfigNameQuality(hint.universeName)
    }

    private fun isBetterMetaConfigName(candidate: String?, current: String?): Boolean {
        return metaConfigNameQuality(candidate) > metaConfigNameQuality(current)
    }

    private fun isBetterRawUniverseName(candidate: String?, current: String?): Boolean {
        if (candidate.isNullOrEmpty()) return false
        if (current.isNullOrEmpty()) return true
        return candidate.contains("MC$") && !current.contains("MC$")
    }

    private fun metaConfigNameQuality(value: String?): Int {
        if (value.isNullOrBlank()) return -100
        val normalized = value.trim()
        var score = 0
        if (shouldReplaceObfuscatedMetaConfigName(normalized)) score -= 50
        if ('_' in normalized) score += 20
        if (normalized.length >= 8) score += 8
        if (normalized.length >= 16) score += 4
        if ('$' in normalized) score -= 12
        if (normalized.matches(Regex("[A-Z]\\d+"))) score -= 40
        if (normalized.matches(Regex("[A-Za-z]{1,3}"))) score -= 8
        if (normalized.any { it.isLowerCase() }) score += 3
        return score
    }

    private fun extractRawUniverseName(universeClass: Class<*>): String? {
        var universeName = universeClass.simpleName
        if (universeName.isNullOrEmpty()) {
            universeName = universeClass.name.substringAfterLast('.')
        }
        return if (shouldReplaceObfuscatedMetaConfigName(universeName)) null else universeName
    }

    private fun cleanMetaConfigDisplayName(value: String?): String {
        if (value.isNullOrEmpty()) return value.orEmpty()
        var cleaned = value.replace("MC$", "")
        var changed: Boolean
        do {
            changed = false
            when {
                cleaned.startsWith("ig ") -> {
                    cleaned = cleaned.substring(3)
                    changed = true
                }
                cleaned.startsWith("android ") -> {
                    cleaned = cleaned.substring(8)
                    changed = true
                }
                cleaned.startsWith("launcher ") -> {
                    cleaned = cleaned.substring(9)
                    changed = true
                }
            }
        } while (changed)
        cleaned = when {
            cleaned.endsWith(" launcher") -> cleaned.substring(0, cleaned.length - " launcher".length)
            cleaned.endsWith(" universe") -> cleaned.substring(0, cleaned.length - " universe".length)
            else -> cleaned
        }
        return cleaned
    }

    private fun getFriendlyMetaConfigDisplayName(raw: String?, display: String): String {
        val cleaned = cleanMetaConfigDisplayName(display)
        var labels = universeLabels
        if (labels == null) {
            getMetaConfigNameHints()
            labels = universeLabels
        }
        if (labels.isNullOrEmpty()) return cleaned
        val label = labels[normalizeUniverseLookupKey(raw)]
            ?: labels[normalizeUniverseLookupKey(display)]
            ?: labels[normalizeUniverseLookupKey(cleaned)]
        return if (!label.isNullOrEmpty()) label else cleaned
    }

    private fun buildFriendlyUniverseLabel(rawUniverseName: String?, paramNames: List<String>): String? {
        if (paramNames.isEmpty()) return null
        val scores = HashMap<String, Int>()
        val firstSeen = HashMap<String, Int>()
        var position = 0
        paramNames.forEach { paramName ->
            paramName.split('_').forEach { token ->
                val normalized = normalizeMetaConfigToken(token)
                if (normalized.isEmpty() || isGenericMetaConfigToken(normalized)) return@forEach
                if (!firstSeen.containsKey(normalized)) firstSeen[normalized] = position++
                scores[normalized] = (scores[normalized] ?: 0) + metaConfigTokenWeight(normalized)
            }
        }
        if (scores.isEmpty()) return null
        val selected = scores.keys.sortedWith { left, right ->
            val scoreCompare = (scores[right] ?: 0) - (scores[left] ?: 0)
            if (scoreCompare != 0) scoreCompare else (firstSeen[left] ?: 0) - (firstSeen[right] ?: 0)
        }.take(3)
        val friendly = selected.joinToString(" ") { formatMetaConfigToken(it) }.trim()
        if (friendly.length >= 4) return friendly
        val fallback = cleanMetaConfigDisplayName(rawUniverseName?.replace('_', ' ').orEmpty())
        return fallback.ifEmpty { null }
    }

    private fun normalizeUniverseLookupKey(value: String?): String {
        return cleanMetaConfigDisplayName(value?.replace('_', ' ').orEmpty())
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }

    private fun normalizeMetaConfigToken(token: String?): String {
        if (token == null) return ""
        return buildString {
            token.forEach { c -> if (c.isLetterOrDigit()) append(c.lowercaseChar()) }
        }
    }

    private fun metaConfigTokenWeight(token: String): Int {
        var weight = 1
        if (isMetaConfigAcronym(token)) weight += 2
        if (token.length >= 6) weight += 1
        return weight
    }

    private fun isGenericMetaConfigToken(token: String): Boolean {
        return when (token) {
            "a", "an", "and", "app", "apps", "config", "configs", "default",
            "disable", "disabled", "enable", "enabled", "false", "fix", "for",
            "from", "in", "is", "main", "ms", "new", "of", "on", "only", "or",
            "pre", "setting", "settings", "should", "the", "to", "true", "use",
            "used", "using", "value", "values", "with" -> true
            else -> token.length < 3 && !isMetaConfigAcronym(token)
        }
    }

    private fun formatMetaConfigToken(token: String): String = if (isMetaConfigAcronym(token)) token.uppercase() else token

    private fun isMetaConfigAcronym(token: String): Boolean {
        return token in setOf(
            "ai", "api", "cdn", "dgw", "dm", "fb", "fbid", "fup", "gql",
            "gqls", "ig", "ig4a", "igd", "mqtt", "msys", "qpl", "ui",
            "www", "xplat", "zbd"
        )
    }

    private fun shouldReplaceObfuscatedMetaConfigName(value: String?): Boolean {
        var normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) return true
        if (normalized.first() == '_') normalized = normalized.drop(1)
        if (normalized.isEmpty()) return true
        return normalized.all { it.isDigit() }
    }

    private fun readLongField(owner: Any?, fieldName: String): Long? {
        if (owner == null) return null
        val field = findFieldInHierarchy(owner.javaClass, fieldName) ?: return null
        return runCatching {
            field.isAccessible = true
            (field.get(owner) as? Number)?.toLong()
        }.getOrNull()
    }

    private fun readStringField(owner: Any?, fieldName: String): String? {
        if (owner == null) return null
        val field = findFieldInHierarchy(owner.javaClass, fieldName) ?: return null
        return runCatching {
            field.isAccessible = true
            field.get(owner) as? String
        }.getOrNull()
    }

    private fun setStringField(owner: Any?, fieldName: String, value: String?): Boolean {
        if (owner == null || value.isNullOrEmpty()) return false
        val field = findFieldInHierarchy(owner.javaClass, fieldName) ?: return false
        return runCatching {
            field.isAccessible = true
            field.set(owner, value)
            true
        }.getOrDefault(false)
    }

    private fun findFieldInHierarchy(clazz: Class<*>?, fieldName: String): Field? {
        var current = clazz
        while (current != null) {
            runCatching { current.getDeclaredField(fieldName) }.getOrNull()?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun log(message: String) {
        XposedBridge.log("[${InstagramFeatureState.TAG}] DevOptions MetaConfig: $message")
    }

    private data class NameHint(val universeName: String?, val paramName: String)
    private data class MetaConfigSearchIndex(val entries: ArrayList<MetaConfigSearchEntry>)
    private data class MetaConfigSearchEntry(val item: Any, val haystack: String)
}
