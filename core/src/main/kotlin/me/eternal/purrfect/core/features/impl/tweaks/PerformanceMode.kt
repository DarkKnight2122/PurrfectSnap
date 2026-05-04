package me.eternal.purrfect.core.features.impl.tweaks

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.database.sqlite.SQLiteDatabase
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.os.Build
import android.os.HandlerThread
import android.os.Process
import android.transition.Transition
import android.util.Range
import android.view.View
import android.view.TextureView
import android.view.ViewPropertyAnimator
import android.view.WindowManager
import android.view.animation.Animation
import android.widget.OverScroller
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import me.eternal.purrfect.core.features.Feature
import me.eternal.purrfect.core.event.events.impl.NetworkApiRequestEvent
import me.eternal.purrfect.core.util.hook.HookStage
import me.eternal.purrfect.core.util.hook.findRestrictedMethod
import me.eternal.purrfect.core.util.hook.hook
import me.eternal.purrfect.core.util.hook.hookConstructor
import okhttp3.Dispatcher
import java.lang.Thread
import java.lang.reflect.Method
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean

class PerformanceMode : Feature("Performance Mode") {
    override fun init() {
        val profile = context.config.global.performanceMode.profile.getNullable() ?: return
        val isMaxProfile = profile == "max"
        val threadPriority = if (isMaxProfile) {
            Process.THREAD_PRIORITY_DISPLAY
        } else {
            Process.THREAD_PRIORITY_MORE_FAVORABLE
        }
        val minimumFrameRate = if (isMaxProfile) 60 else 45
        val durationScale = if (isMaxProfile) 0.35f else 0.55f
        val recyclerViewCacheSize = if (isMaxProfile) 64 else 32
        val maxRequests = if (isMaxProfile) 192 else 96
        val maxRequestsPerHost = if (isMaxProfile) 32 else 16
        val minimumCoreThreads = if (isMaxProfile) 16 else 8
        val prefetchItemCount = if (isMaxProfile) 24 else 12
        val maxAnimationDurationMs = if (isMaxProfile) 90L else 140L
        val maxScrollDurationMs = if (isMaxProfile) 72 else 180
        val preferredRefreshRate = if (isMaxProfile) 120f else 90f

        context.log.info(
            "Performance mode enabled: profile=$profile, threadPriority=$threadPriority, minFps=$minimumFrameRate, durationScale=$durationScale, rvCache=$recyclerViewCacheSize, maxRequests=$maxRequests/$maxRequestsPerHost, minCoreThreads=$minimumCoreThreads, prefetch=$prefetchItemCount, maxAnimMs=$maxAnimationDurationMs, maxScrollMs=$maxScrollDurationMs, preferredRefreshRate=$preferredRefreshRate",
            "PerformanceMode"
        )

        runCatching {
            ValueAnimator.setFrameDelay(0L)
        }

        fun isPerformanceSensitiveThread(name: String?): Boolean {
            val normalizedName = name?.lowercase() ?: return false
            return listOf("camera", "preview", "codec", "render", "gl", "transcod", "lens", "feed", "story", "opera", "messag", "network", "db", "disk", "map", "mapbox", "snapmap", "viewport").any {
                normalizedName.contains(it)
            }
        }

        fun clampPositiveDuration(durationMs: Long, maxDurationMs: Long): Long {
            if (durationMs <= 0L) return durationMs
            return durationMs.coerceAtMost(maxDurationMs)
        }

        context.event.subscribe(NetworkApiRequestEvent::class) { event ->
            if (!isMaxProfile) return@subscribe
            val url = event.url
            if (url.contains("mapbox") && (url.contains("events.") || url.contains("telemetry"))) {
                event.canceled = true
            }
        }

        HandlerThread::class.java.hookConstructor(HookStage.BEFORE) { param ->
            if (param.args().size < 2) return@hookConstructor
            val threadName = param.argNullable<String>(0)
            if (!isPerformanceSensitiveThread(threadName)) return@hookConstructor
            param.setArg(1, threadPriority)
        }

        HandlerThread::class.java.hook("start", HookStage.AFTER) { param ->
            val thread = param.nullableThisObject<Any>() as? HandlerThread ?: return@hook
            if (!isPerformanceSensitiveThread(thread.name)) return@hook
            runCatching {
                val tid = thread.threadId
                if (tid > 0) {
                    Process.setThreadPriority(tid, threadPriority)
                }
            }
        }

        Thread::class.java.hook("start", HookStage.AFTER) { param ->
            val thread = param.thisObject<Thread>()
            if (!isPerformanceSensitiveThread(thread.name)) return@hook
            runCatching {
                thread.priority = if (isMaxProfile) Thread.MAX_PRIORITY else Thread.NORM_PRIORITY + 1
            }
        }

        ThreadPoolExecutor::class.java.hookConstructor(HookStage.AFTER) { param ->
            val executor = param.thisObject<ThreadPoolExecutor>()
            runCatching {
                val targetCorePoolSize = executor.maximumPoolSize.coerceAtLeast(1).coerceAtMost(minimumCoreThreads.coerceAtLeast(executor.corePoolSize))
                if (executor.corePoolSize < targetCorePoolSize) {
                    executor.corePoolSize = targetCorePoolSize
                }
                executor.allowCoreThreadTimeOut(false)
                executor.prestartAllCoreThreads()
            }
        }

        Dispatcher::class.java.hookConstructor(HookStage.AFTER) { param ->
            val dispatcher = param.thisObject<Dispatcher>()
            runCatching {
                dispatcher.maxRequests = maxRequests
                dispatcher.maxRequestsPerHost = maxRequestsPerHost
            }
        }

        ValueAnimator::class.java.hook("getDurationScale", HookStage.AFTER) { param ->
            param.setResult(durationScale)
        }

        ValueAnimator::class.java.hook("setDuration", HookStage.BEFORE) { param ->
            val original = param.arg<Long>(0)
            val updated = original.coerceAtMost(maxAnimationDurationMs)
            if (updated != original) {
                param.setArg(0, updated)
            }
        }

        ViewPropertyAnimator::class.java.hook("setDuration", HookStage.BEFORE) { param ->
            val original = param.arg<Long>(0)
            val updated = original.coerceAtMost(maxAnimationDurationMs)
            if (updated != original) {
                param.setArg(0, updated)
            }
        }

        Transition::class.java.hook("setDuration", HookStage.BEFORE) { param ->
            val original = param.arg<Long>(0)
            val updated = original.coerceAtMost(maxAnimationDurationMs)
            if (updated != original) {
                param.setArg(0, updated)
            }
        }

        Animation::class.java.hook("setDuration", HookStage.BEFORE) { param ->
            val original = param.arg<Long>(0)
            val updated = original.coerceAtMost(maxAnimationDurationMs)
            if (updated != original) {
                param.setArg(0, updated)
            }
        }

        RecyclerView::class.java.hookConstructor(HookStage.AFTER) { param ->
            val recyclerView = param.thisObject<RecyclerView>()
            recyclerView.setItemViewCacheSize(recyclerViewCacheSize)
            recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
            recyclerView.recycledViewPool.setMaxRecycledViews(0, 20)
            if (isMaxProfile) {
                recyclerView.itemAnimator = null
            }
        }

        RecyclerView::class.java.hook("setAdapter", HookStage.AFTER) { param ->
            val recyclerView = param.thisObject<RecyclerView>()
            recyclerView.setItemViewCacheSize(recyclerViewCacheSize)
            recyclerView.recycledViewPool.setMaxRecycledViews(0, 20)
            if (isMaxProfile) {
                recyclerView.itemAnimator = null
            }
        }

        RecyclerView::class.java.hook("setLayoutManager", HookStage.AFTER) { param ->
            val recyclerView = param.thisObject<RecyclerView>()
            val layoutManager = param.argNullable<Any>(0)
            when (layoutManager) {
                is LinearLayoutManager -> {
                    layoutManager.isItemPrefetchEnabled = true
                    layoutManager.initialPrefetchItemCount = prefetchItemCount.coerceAtLeast(12)
                }
                is StaggeredGridLayoutManager -> {
                    layoutManager.isItemPrefetchEnabled = true
                    layoutManager.gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
                }
            }
        }

        fun SQLiteDatabase.applyPerformancePragmas() {
            runCatching { execSQL("PRAGMA synchronous = NORMAL") }
            runCatching { execSQL("PRAGMA temp_store = MEMORY") }
            runCatching { execSQL("PRAGMA cache_size = -32768") }
            runCatching { execSQL("PRAGMA mmap_size = 268435456") }
            runCatching { execSQL("PRAGMA journal_size_limit = 1048576") }
            runCatching { execSQL("PRAGMA optimize") }
        }

        SQLiteDatabase::class.java.hook("openDatabase", HookStage.AFTER) { param ->
            (param.getResult() as? SQLiteDatabase)?.applyPerformancePragmas()
        }

        SQLiteDatabase::class.java.hook("openOrCreateDatabase", HookStage.AFTER) { param ->
            (param.getResult() as? SQLiteDatabase)?.applyPerformancePragmas()
        }

        MediaRecorder::class.java.hook("setVideoFrameRate", HookStage.BEFORE) { param ->
            val currentRate = param.arg<Int>(0)
            val applied = currentRate
                .coerceAtLeast(if (isMaxProfile) 30 else 24)
                .coerceAtMost(if (isMaxProfile) 60 else 45)
            if (applied != currentRate) {
                param.setArg(0, applied)
            }
        }

        OverScroller::class.java.hook("startScroll", HookStage.BEFORE) { param ->
            if (param.args().size >= 5) {
                val original = param.arg<Int>(4)
                val updated = original.coerceAtMost(maxScrollDurationMs)
                if (updated != original) {
                    param.setArg(4, updated)
                }
            }
        }

        OverScroller::class.java.hook("fling", HookStage.BEFORE) { param ->
            if (param.args().size >= 10) {
                val overX = param.arg<Int>(8)
                val overY = param.arg<Int>(9)
                if (overX != 0) param.setArg(8, 0)
                if (overY != 0) param.setArg(9, 0)
            }
        }

        runCatching {
            val nativeMapViewClass = findClass("com.mapbox.mapboxsdk.maps.NativeMapView")
            val transitionOptionsClass = findClass("com.mapbox.mapboxsdk.style.layers.TransitionOptions")
            val transitionOptionsCtor = transitionOptionsClass.getDeclaredConstructor(Long::class.javaPrimitiveType, Long::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply {
                isAccessible = true
            }

            fun findNativeMapMethod(name: String, predicate: (Method) -> Boolean): Method? {
                return nativeMapViewClass.findRestrictedMethod { method ->
                    method.name == name && predicate(method)
                }?.apply {
                    isAccessible = true
                }
            }

            val nativeCancelTransitions = findNativeMapMethod("nativeCancelTransitions") { it.parameterCount == 0 }
            val nativeSetPrefetchTiles = findNativeMapMethod("nativeSetPrefetchTiles") { it.parameterCount == 1 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType }  
            val nativeSetPrefetchZoomDelta = findNativeMapMethod("nativeSetPrefetchZoomDelta") { it.parameterCount == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType }
            val nativeSetTransitionDelay = findNativeMapMethod("nativeSetTransitionDelay") { it.parameterCount == 1 && it.parameterTypes[0] == Long::class.javaPrimitiveType } 
            val nativeSetTransitionDuration = findNativeMapMethod("nativeSetTransitionDuration") { it.parameterCount == 1 && it.parameterTypes[0] == Long::class.javaPrimitiveType }
            val nativeSetTransitionOptions = findNativeMapMethod("nativeSetTransitionOptions") { it.parameterCount == 1 && it.parameterTypes[0].name == transitionOptionsClass.name }

            nativeMapViewClass.hookConstructor(HookStage.AFTER) { param ->
                val nativeMapView = param.thisObject<Any>()
                runCatching {
                    nativeSetPrefetchTiles?.invoke(nativeMapView, true)
                    nativeSetPrefetchZoomDelta?.invoke(nativeMapView, 6)
                    nativeSetTransitionDelay?.invoke(nativeMapView, 0L)
                    nativeSetTransitionDuration?.invoke(nativeMapView, 0L)
                    nativeSetTransitionOptions?.invoke(
                        nativeMapView,
                        transitionOptionsCtor.newInstance(0L, 0L, false)
                    )
                    nativeCancelTransitions?.invoke(nativeMapView)
                }
            }

            nativeMapViewClass.findRestrictedMethod { method ->
                method.name == "g" &&
                    method.parameterCount == 6 &&
                    method.parameterTypes.last() == Long::class.javaPrimitiveType
            }?.hook(HookStage.BEFORE) { param ->
                val original = param.arg<Long>(5)
                val applied = original.coerceAtMost(16L)
                if (applied != original) {
                    param.setArg(5, applied)
                }
                runCatching { nativeCancelTransitions?.invoke(param.thisObject<Any>()) }
            }

            nativeMapViewClass.findRestrictedMethod { method ->
                method.name == "v" &&
                    method.parameterCount == 3 &&
                    method.parameterTypes[0] == Double::class.javaPrimitiveType &&
                    method.parameterTypes[1] == Double::class.javaPrimitiveType &&
                    method.parameterTypes[2] == Long::class.javaPrimitiveType
            }?.hook(HookStage.BEFORE) { param ->
                val original = param.arg<Long>(2)
                val applied = original.coerceAtMost(8L)
                if (applied != original) {
                    param.setArg(2, applied)
                }
                runCatching { nativeCancelTransitions?.invoke(param.thisObject<Any>()) }
            }
        }.onFailure {
            context.log.error("Failed to install Snap Map transition hooks", it, "PerformanceMode")
        }

        fun applyActivityPerformanceTuning(activity: Activity) {
            runCatching {
                activity.window.decorView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                val display = activity.display
                val targetRefreshRate = display?.supportedModes?.maxByOrNull { it.refreshRate }?.refreshRate
                    ?.coerceAtLeast(preferredRefreshRate) ?: preferredRefreshRate
                activity.window.attributes = activity.window.attributes.apply {
                    this.preferredRefreshRate = targetRefreshRate
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isMaxProfile) {
                runCatching {
                    activity.window.setSustainedPerformanceMode(true)
                }
            }
        }

        onNextActivityCreate {
            applyActivityPerformanceTuning(it)
        }

        Dialog::class.java.hook("show", HookStage.AFTER) { param ->
            val dialog = param.nullableThisObject<Any>() as? Dialog ?: return@hook
            val window = dialog.window ?: return@hook
            runCatching {
                window.setWindowAnimations(0)
                window.decorView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                window.attributes = window.attributes.apply {
                    flags = flags or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                }
            }
        }

        TextureView::class.java.hookConstructor(HookStage.AFTER) { param ->
            val textureView = param.thisObject<TextureView>()
            runCatching {
                // Universal Guard: Only accelerate views owned by Snapchat.
                // This prevents crashes in native hardware providers across all devices.
                if (textureView.context.packageName != context.androidContext.packageName) return@runCatching
                textureView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
        }
    }
}
