package me.eternal.purrfect.core.whatsapp

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import me.eternal.purrfect.core.logger.CoreLogger
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class WhatsAppChannelHooks(
    private val androidContext: Context,
    private val appClassLoader: ClassLoader = androidContext.classLoader
) {
    companion object {
        const val TAG = "PurrfectWA"
    }

    private val installed = AtomicBoolean(false)
    private val hiddenRecommendations = AtomicInteger(0)
    private val scanTimes = Collections.synchronizedMap(WeakHashMap<View, Long>())
    private val preDrawRoots = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val hiddenViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val hookedUpdatesAdapterClasses = Collections.synchronizedSet(mutableSetOf<String>())
    private val featureState get() = WhatsAppFeatureStateStore.current

    fun init() {
        installPlatformHooks()
    }

    private fun installPlatformHooks() {
        if (!installed.compareAndSet(false, true)) return

        runCatching {
            XposedBridge.hookAllMethods(
                View::class.java,
                "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        runSafe("View.onAttachedToWindow") {
                            val view = param.thisObject as? View ?: return@runSafe
                            if (shouldHideRecommendationsNow()) scheduleRecommendationScan(view)
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                View::class.java,
                "onLayout",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        runSafe("View.onLayout") {
                            val view = param.thisObject as? View ?: return@runSafe
                            if (shouldHideRecommendationsNow()) scheduleRecommendationScan(view)
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                TextView::class.java,
                "setText",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        runSafe("TextView.setText") {
                            if (!shouldHideRecommendationsNow()) return@runSafe
                            val textView = param.thisObject as? TextView ?: return@runSafe
                            handleRecommendationTextBind(textView)
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                RecyclerView.Adapter::class.java,
                "bindViewHolder",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        runSafe("RecyclerView.Adapter.bindViewHolder") {
                            if (!shouldHideRecommendationsNow()) return@runSafe
                            val adapter = param.thisObject as? RecyclerView.Adapter<*> ?: return@runSafe
                            val holder = param.args.getOrNull(0) as? RecyclerView.ViewHolder ?: return@runSafe
                            val position = (param.args.getOrNull(1) as? Int)
                                ?.takeIf { it != RecyclerView.NO_POSITION }
                                ?: holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                                ?: return@runSafe

                            if (!isLikelyUpdatesAdapter(adapter, holder.itemView)) return@runSafe
                            installRuntimeUpdatesAdapterHooks(adapter.javaClass)
                            hideBoundRecommendation(adapter, holder, position)
                        }
                    }
                }
            )
            logInfo("Installed WhatsApp channel recommendation UI hooks")
        }.onFailure { logError("Failed to install WhatsApp channel recommendation hooks", it) }
    }

    private fun scheduleRecommendationScan(view: View) {
        if (!shouldHideRecommendationsNow()) return
        val root = view.rootView ?: view
        installPreDrawScan(root)
        val now = SystemClock.uptimeMillis()
        val last = scanTimes[root] ?: 0L
        if (now - last < 80L) return
        scanTimes[root] = now
        runSafe("recommendation scan immediate") { scanRecommendationContainers(root) }
        root.post { runSafe("recommendation scan posted") { scanRecommendationContainers(root) } }
    }

    private fun installPreDrawScan(root: View) {
        if (!preDrawRoots.add(root)) return
        root.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!root.viewTreeObserver.isAlive) return true
                    runSafe("recommendation pre-draw scan") {
                        scanRecommendationContainers(root)
                    }
                    return true
                }
            }
        )
    }

    private fun scanRecommendationContainers(root: View) {
        if (!shouldHideRecommendationsNow()) return
        val rootHeight = root.height.takeIf { it > 0 } ?: root.resources.displayMetrics.heightPixels
        val rootTexts = mutableListOf<String>()
        collectText(root, rootTexts, 0, 120)
        val updatesScreen = looksLikeUpdatesScreen(rootTexts)
        val hasRecommendationSurface = updatesScreen && hasVisibleRecommendationSurface(rootTexts)
        scanNode(root, rootHeight, 0, updatesScreen, hasRecommendationSurface)
    }

    private fun scanNode(
        view: View,
        rootHeight: Int,
        depth: Int,
        updatesScreen: Boolean,
        hasRecommendationSurface: Boolean
    ) {
        if (depth > 16 || view.visibility != View.VISIBLE || view in hiddenViews) return

        if (view is TextView && updatesScreen && hasRecommendationSurface) {
            val text = view.text?.toString()?.trim().orEmpty()
            val contentDescription = view.contentDescription?.toString()?.trim().orEmpty()
            val texts = listOf(text, contentDescription).filter { it.isNotBlank() }
            if (texts.isNotEmpty() && looksLikeRecommendationCopy(texts) && isSafeToHide(view, rootHeight)) {
                hideRecommendationView(view, "copy", texts)
                return
            }
        }

        if (view is ViewGroup) {
            val texts = mutableListOf<String>()
            collectText(view, texts, 0, 40)
            if (
                texts.isNotEmpty() &&
                (
                    looksLikeRecommendationBlock(texts) ||
                        (updatesScreen && hasRecommendationSurface && looksLikeRecommendationRow(texts))
                    ) &&
                isSafeToHide(view, rootHeight)
            ) {
                hideRecommendationView(view, "container", texts)
                return
            }

            for (index in 0 until view.childCount) {
                scanNode(view.getChildAt(index), rootHeight, depth + 1, updatesScreen, hasRecommendationSurface)
            }
        }
    }

    private fun handleRecommendationTextBind(textView: TextView) {
        val text = textView.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        val normalized = text.normalizeUiText()

        when {
            isRecommendationCopyText(normalized) -> hideRecommendationCopyAround(textView, text)
            isRecommendationRowTriggerText(normalized) -> {
                hideRecommendationRowAround(textView)
                textView.post { runSafe("TextView recommendation row posted") { hideRecommendationRowAround(textView) } }
                textView.postDelayed({ runSafe("TextView recommendation row delayed") { hideRecommendationRowAround(textView) } }, 50L)
            }
        }
    }

    private fun hideRecommendationCopyAround(textView: TextView, text: String) {
        findAncestor(textView, 5) { candidate ->
            val group = candidate as? ViewGroup ?: return@findAncestor false
            val texts = mutableListOf<String>()
            collectText(group, texts, 0, 24)
            looksLikeRecommendationCopy(texts) &&
                texts.none { it.normalizeUiText() == "channels" || it.normalizeUiText() == "explore more" || it.normalizeUiText() == "create channel" }
        }?.let { block ->
            hideRecommendationView(block, "copy block", listOf(text))
            return
        }

        hideRecommendationView(textView, "copy text", listOf(text))
    }

    private fun hideRecommendationRowAround(textView: TextView) {
        findAncestor(textView, 8) { candidate ->
            val group = candidate as? ViewGroup ?: return@findAncestor false
            val texts = mutableListOf<String>()
            collectText(group, texts, 0, 48)
            looksLikeRecommendationRow(texts) && isRecommendationRowContainer(texts)
        }?.let { row ->
            val texts = mutableListOf<String>()
            collectText(row, texts, 0, 48)
            hideRecommendationView(row, "text-bound row", texts)
        }
    }

    private fun findAncestor(view: View, maxDepth: Int, predicate: (View) -> Boolean): View? {
        var current = view.parent as? View
        repeat(maxDepth) {
            val candidate = current ?: return null
            if (candidate in hiddenViews || candidate.visibility == View.GONE) return null
            if (predicate(candidate)) return candidate
            current = candidate.parent as? View
        }
        return null
    }

    private fun isRecommendationRowContainer(texts: List<String>): Boolean {
        val normalized = texts.map { it.normalizeUiText() }
        if (normalized.any { it == "updates" || it == "status" || it == "recent updates" || it == "add status" }) return false
        if (normalized.any { it == "channels" || it == "explore more" || it == "create channel" || it == "find channels to follow" }) return false
        return true
    }

    private fun isRecommendationRowTriggerText(normalized: String): Boolean {
        return normalized == "follow" || normalized == "follow channel" || normalized.hasFollowerCountText()
    }

    private fun isRecommendationCopyText(normalized: String): Boolean {
        return normalized == "find channels to follow" ||
            "stay updated on topics" in normalized ||
            "channels to follow below" in normalized
    }

    private fun shouldHideRecommendationsNow(): Boolean {
        val state = featureState
        return state.hideChannelRecommendations || state.source == "unavailable"
    }

    private fun hideBoundRecommendation(
        adapter: RecyclerView.Adapter<*>,
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        val view = holder.itemView
        if (view in hiddenViews || view.visibility == View.GONE) return

        val texts = mutableListOf<String>()
        collectText(view, texts, 0, 60)

        val itemViewType = runCatching { adapter.getItemViewType(position) }.getOrNull()
        val model = findAdapterItem(adapter, position)
        val modelText = model?.toString()?.takeIf { it.isNotBlank() }
        val allSignals = buildList {
            addAll(texts)
            modelText?.let { add(it) }
            itemViewType?.let { add("viewType=$it") }
        }

        val shouldHide =
            looksLikeRecommendationBlock(allSignals) ||
                looksLikeRecommendationRow(allSignals) ||
                looksLikeRecommendationModel(model)

        if (!shouldHide) return

        hideRecommendationView(
            view,
            "bound row${itemViewType?.let { " type=$it" }.orEmpty()}",
            allSignals.ifEmpty { listOf(view.javaClass.name) }
        )
    }

    private fun installRuntimeUpdatesAdapterHooks(adapterClass: Class<*>) {
        val className = adapterClass.name
        if (!hookedUpdatesAdapterClasses.add(className)) return

        var hookedMethods = 0
        adapterClass.declaredMethods.forEach { method ->
            if (!Collection::class.java.isAssignableFrom(method.returnType)) return@forEach

            runCatching {
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam<*>) {
                            runSafe("${adapterClass.name}.${method.name}") {
                                val collection = param.result as? MutableCollection<*> ?: return@runSafe
                                filterRecommendationModels(collection, "${adapterClass.name}.${method.name}")
                            }
                        }
                    }
                )
                hookedMethods++
            }.onFailure { throwable ->
                logError("Failed to hook WhatsApp updates adapter data method ${adapterClass.name}.${method.name}", throwable)
            }
        }

        logInfo("Installed direct WhatsApp updates adapter hooks on $className methods=$hookedMethods")
    }

    private fun filterRecommendationModels(collection: MutableCollection<*>, source: String) {
        if (collection.isEmpty()) return
        val hasRecommendationRows = collection.any { looksLikeRecommendationModel(it) }
        if (!hasRecommendationRows) return

        @Suppress("UNCHECKED_CAST")
        val mutableCollection = collection as? MutableCollection<Any?> ?: return
        val before = mutableCollection.size
        mutableCollection.removeAll { item ->
            looksLikeRecommendationModel(item)
        }
        val removed = before - mutableCollection.size
        if (removed > 0) {
            logBlock("Filtered WhatsApp channel recommendation models from $source removed=$removed")
        }
    }

    private fun isLikelyUpdatesAdapter(adapter: RecyclerView.Adapter<*>, itemView: View?): Boolean {
        if (adapter.javaClass.name.contains("status.updates", ignoreCase = true)) return true
        if (hasUpdatesFragmentField(adapter)) return true

        if (itemView != null) {
            val rootTexts = mutableListOf<String>()
            collectText(itemView.rootView ?: itemView, rootTexts, 0, 120)
            if (looksLikeUpdatesScreen(rootTexts)) return true
        }

        return false
    }

    private fun hasUpdatesFragmentField(adapter: RecyclerView.Adapter<*>): Boolean {
        var current: Class<*>? = adapter.javaClass
        var inspected = 0
        while (current != null && current != RecyclerView.Adapter::class.java && inspected < 4) {
            current.declaredFields.forEach { field ->
                val typeName = field.type.name
                if (typeName == "com.whatsapp.status.updates.ui.UpdatesFragment" ||
                    typeName.contains(".status.updates.", ignoreCase = true)
                ) {
                    return true
                }

                val value = runCatching {
                    field.isAccessible = true
                    field.get(adapter)
                }.getOrNull()
                val valueName = value?.javaClass?.name.orEmpty()
                if (valueName == "com.whatsapp.status.updates.ui.UpdatesFragment" ||
                    valueName.contains(".status.updates.", ignoreCase = true)
                ) {
                    return true
                }
            }
            current = current.superclass
            inspected++
        }
        return false
    }

    private fun findAdapterItem(adapter: RecyclerView.Adapter<*>, position: Int): Any? {
        if (position < 0) return null

        fun itemFrom(value: Any?): Any? {
            if (value is List<*> && position < value.size) return value[position]
            val resolved = runCatching {
                val method = value?.javaClass?.methods?.firstOrNull {
                    it.name == "getValue" && it.parameterTypes.isEmpty()
                } ?: return@runCatching null
                method.invoke(value)
            }.getOrNull()
            if (resolved is List<*> && position < resolved.size) return resolved[position]
            return null
        }

        var current: Class<*>? = adapter.javaClass
        var inspected = 0
        while (current != null && current != RecyclerView.Adapter::class.java && inspected < 4) {
            current.declaredFields.forEach { field ->
                val candidate = runCatching {
                    field.isAccessible = true
                    itemFrom(field.get(adapter))
                }.getOrNull()
                if (candidate != null) return candidate
            }
            current = current.superclass
            inspected++
        }

        return null
    }

    private fun collectText(view: View, out: MutableList<String>, depth: Int, maxItems: Int) {
        if (depth > 5 || out.size >= maxItems || view.visibility != View.VISIBLE) return
        val contentDescription = view.contentDescription?.toString()?.trim()
        if (!contentDescription.isNullOrBlank()) out += contentDescription
        if (view is TextView) {
            val text = view.text?.toString()?.trim()
            if (!text.isNullOrBlank()) out += text
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectText(view.getChildAt(index), out, depth + 1, maxItems)
                if (out.size >= maxItems) return
            }
        }
    }

    private fun looksLikeUpdatesScreen(texts: List<String>): Boolean {
        val normalized = texts.map { it.normalizeUiText() }
        return "updates" in normalized &&
            ("status" in normalized || "recent updates" in normalized || "channels" in normalized)
    }

    private fun hasVisibleRecommendationSurface(texts: List<String>): Boolean {
        val normalized = texts.map { it.normalizeUiText() }
        val hasFollowButton = normalized.any { it == "follow" || it == "follow channel" }
        val hasFollowerCount = normalized.any { it.hasFollowerCountText() }
        val hasOwnedSignal = normalized.any { it == "following" || it == "unfollow" || "your channel" in it || "my channel" in it }
        return hasFollowButton && hasFollowerCount && !hasOwnedSignal
    }

    private fun looksLikeRecommendationBlock(texts: List<String>): Boolean {
        val joined = texts.joinToString(" ").normalizeUiText()
        val hasChannelTerm = "channel" in joined || "channels" in joined || "newsletter" in joined
        if (!hasChannelTerm) return false

        val hasRecommendationTerm = listOf(
            "recommend",
            "recommended",
            "recommendations",
            "suggested",
            "suggestion",
            "discover",
            "find channels",
            "channels to follow",
            "popular channels",
            "follow channels",
            "explore channels"
        ).any { it in joined }

        val looksOwnedOrFollowed = listOf(
            "your channel",
            "my channel",
            "unfollow",
            "following",
            "mute channel"
        ).any { it in joined }

        return hasRecommendationTerm && !looksOwnedOrFollowed
    }

    private fun looksLikeNewRecommendationSurface(texts: List<String>): Boolean {
        val normalized = texts.map { it.normalizeUiText() }
        if (normalized.any { it == "updates" || it == "status" || it == "recent updates" || it == "add status" }) {
            return false
        }
        val hasChannelsHeader = normalized.any { it == "channels" }
        val hasFollowButton = normalized.any { it == "follow" || it == "follow channel" }
        val hasFollowerCount = normalized.any { it.hasFollowerCountText() }
        val hasRecommendationAction = normalized.any { it == "explore more" || it == "create channel" || "find channels" in it }
        val hasOwnedSignal = normalized.any { it == "following" || it == "unfollow" || "your channel" in it || "my channel" in it }
        return (hasChannelsHeader && hasFollowButton && hasFollowerCount && !hasOwnedSignal) ||
            (hasRecommendationAction && hasFollowButton && hasFollowerCount && !hasOwnedSignal)
    }

    private fun looksLikeRecommendationRow(texts: List<String>): Boolean {
        val normalized = texts.map { it.normalizeUiText() }
        val hasFollowButton = normalized.any { it == "follow" || it == "follow channel" }
        val hasFollowerCount = normalized.any { it.hasFollowerCountText() }
        val hasOwnedSignal = normalized.any { it == "following" || it == "unfollow" || "your channel" in it || "my channel" in it }
        val hasRealTitle = normalized.any { text ->
            text.isNotBlank() &&
                text != "follow" &&
                !text.hasFollowerCountText() &&
                text !in setOf("channels", "explore more", "create channel")
        }
        return hasFollowButton && hasFollowerCount && hasRealTitle && !hasOwnedSignal
    }

    private fun looksLikeRecommendationAction(texts: List<String>): Boolean {
        val normalized = texts.map { it.normalizeUiText() }
        return normalized.any {
            it == "explore more" ||
                it == "create channel" ||
                it == "find channels" ||
                it == "find channels to follow"
        }
    }

    private fun looksLikeRecommendationCopy(texts: List<String>): Boolean {
        val normalized = texts.map { it.normalizeUiText() }
        return normalized.any(::isRecommendationCopyText)
    }

    private fun looksLikeRecommendationStandaloneText(texts: List<String>): Boolean {
        val normalized = texts.map { it.normalizeUiText() }
        return normalized.any { it == "channels" || it == "explore more" || it == "create channel" }
    }

    private fun looksLikeRecommendationModel(model: Any?): Boolean {
        val text = model?.toString()?.normalizeUiText().orEmpty()
        if (text.isBlank()) return false
        return ("recounit" in text && ("newsletter" in text || "channel" in text)) ||
            ("recommended" in text && ("newsletter" in text || "channel" in text)) ||
            text.startsWith("reconewsletter") ||
            text.startsWith("recocommunity")
    }

    private fun isSafeToHide(view: View, rootHeight: Int): Boolean {
        if (view.parent !is ViewGroup) return false
        if (view.width <= 0 || view.height <= 0) return false
        if (view.height > rootHeight * 0.55f) return false
        return true
    }

    private fun hideRecommendationView(view: View, reason: String, texts: List<String>) {
        hiddenViews += view
        view.visibility = View.GONE
        view.alpha = 0f
        view.minimumHeight = 0
        view.layoutParams?.apply {
            height = 0
            view.layoutParams = this
        }
        val sample = texts.joinToString(" | ").take(180)
        logBlock("Hidden WhatsApp channel recommendation $reason: $sample")
    }

    private fun String.normalizeUiText(): String {
        return trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }

    private fun String.hasFollowerCountText(): Boolean {
        return contains("followers") ||
            contains("follower") ||
            contains("subscribers") ||
            contains("subscriber")
    }

    private fun logBlock(message: String) {
        val count = hiddenRecommendations.incrementAndGet()
        if (count <= 20 || count % 25 == 0) {
            logInfo("$message (count=$count)")
        }
    }

    private fun logInfo(message: String) {
        XposedBridge.log("[$TAG] $message")
        CoreLogger.xposedLog(message, TAG)
        WhatsAppAppLogWriter.info(androidContext, TAG, message)
    }

    private fun logError(message: String, throwable: Throwable) {
        val fullMessage = "$message: ${throwable.stackTraceToString()}"
        XposedBridge.log("[$TAG] $fullMessage")
        CoreLogger.xposedLog(fullMessage, TAG)
        WhatsAppAppLogWriter.error(androidContext, TAG, fullMessage)
    }

    private fun runSafe(source: String, block: () -> Unit) {
        runCatching(block).onFailure { throwable ->
            logError("WhatsApp hook callback failed in $source", throwable)
        }
    }
}
