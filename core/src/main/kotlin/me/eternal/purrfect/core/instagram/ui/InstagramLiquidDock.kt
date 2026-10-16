package me.eternal.purrfect.core.instagram.ui

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import me.eternal.purrfect.core.instagram.InstagramFeatureState
import me.eternal.purrfect.core.logger.CoreLogger
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Controller and lifecycle injector for the Liquid Glass Navigation Dock in Instagram.
 *
 * Implements:
 * 1. Edge-to-Edge Z-Stack Surgery (defeats Android 16 letterboxing & scrims).
 * 2. Dual-Zone Progressive Ambient Gradient Blurs (Top status bar & Bottom bezel cushioning).
 * 3. 90% Width Floating Oval Stadium Pill with detached circular Search Puck.
 * 4. Compact White Puck Active Indicator with icon inversion & 0.32s spring physics.
 * 5. 120Hz Interactive Horizontal Gesture Sliding with tactile haptic detent ticks.
 * 6. Dynamic Shrink-on-Scroll morphing into dual circular islands.
 * 7. Reels Scrubber Protection & 3-second auto-hide freeze.
 */
object InstagramLiquidDock {

    private const val TAG = "InstagramLiquidDock"

    // Instagram View Resource IDs
    const val ID_TAB_BAR = "tab_bar"
    const val ID_TAB_BAR_SHADOW = "tab_bar_shadow"
    const val ID_CONTENT = "layout_container_main"
    const val ID_SWIPE_PAGER = "swipeable_tab_view_pager"
    const val ID_REELS_ROOT = "root_clips_layout"
    const val ID_SCRUBBER = "scrubber"
    const val ID_TOUCH_STRIP = "clips_expanded_touch_view"
    const val ID_SEARCH_TAB = "search_tab"

    private val idCache = HashMap<String, Int>()
    private val activeControllers = WeakHashMap<Activity, LiquidDockController>()

    fun init(featureState: InstagramFeatureState) {
        if (!featureState.enableLiquidGlassUi) return

        runCatching {
            XposedBridge.hookAllMethods(
                Activity::class.java,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val activity = param.thisObject as? Activity ?: return
                        attachToActivity(activity, featureState)
                    }
                }
            )

            XposedBridge.hookAllMethods(
                Activity::class.java,
                "onDestroy",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val activity = param.thisObject as? Activity ?: return
                        activeControllers.remove(activity)?.dispose()
                    }
                }
            )
            log("Liquid Glass Dock lifecycle hooks registered successfully")
        }.onFailure { throwable ->
            log("Failed to hook Activity lifecycle for Liquid Glass: ${throwable.message}")
        }
    }

    private fun attachToActivity(activity: Activity, featureState: InstagramFeatureState) {
        val name = activity.javaClass.simpleName
        if (name.contains("Splash", ignoreCase = true) || name.contains("Launcher", ignoreCase = true)) return

        val window = activity.window ?: return
        val decor = window.decorView as? ViewGroup ?: return

        decor.post {
            runCatching {
                // Check if this Activity contains the Instagram main navigation bar
                val tabBarId = resolveId(activity, ID_TAB_BAR)
                if (tabBarId == 0) return@runCatching

                val tabBar = decor.findViewById<ViewGroup>(tabBarId) ?: return@runCatching

                if (!activeControllers.containsKey(activity)) {
                    val controller = LiquidDockController(activity, tabBar, featureState)
                    activeControllers[activity] = controller
                    controller.install()
                } else {
                    activeControllers[activity]?.refresh()
                }
            }
        }
    }

    fun resolveId(context: Context, name: String): Int {
        synchronized(idCache) {
            val cached = idCache[name]
            if (cached != null) return cached
            val resolved = context.resources.getIdentifier(name, "id", context.packageName)
            idCache[name] = resolved
            return resolved
        }
    }

    private fun log(message: String) {
        XposedBridge.log("[$TAG] $message")
        CoreLogger.xposedLog(message, TAG)
    }

    /**
     * Active controller instance bound to an Instagram Activity.
     */
    class LiquidDockController(
        private val activity: Activity,
        private val tabBar: ViewGroup,
        private val featureState: InstagramFeatureState
    ) : ViewTreeObserver.OnPreDrawListener {

        private val handler = Handler(Looper.getMainLooper())
        private val glassDrawable = LiquidGlassDrawable()

        private var contentContainer: ViewGroup? = null
        private var tabBarShadow: View? = null
        private var topAmbientBlurView: View? = null
        private var bottomAmbientBlurView: View? = null

        private var lastSelectedTab: View? = null
        private var isInstalled = false
        private var isShrunk = false

        // Spring animation state for the active tab puck
        private var currentPuckCenterX = Float.NaN
        private var targetPuckCenterX = Float.NaN
        private var puckVelocityX = 0f

        // Reels auto-hide timer
        private var isReelsVisible = false
        private val reelsHideRunnable = Runnable {
            if (isReelsVisible && featureState.liquidGlassReelsHide > 0) {
                tabBar.animate().alpha(0f).setDuration(280L).start()
            }
        }

        private var originalBarHeight = -1

        private fun getContentContainer(): ViewGroup? {
            if (contentContainer != null && contentContainer?.isAttachedToWindow == true) return contentContainer
            val contentId = resolveId(activity, ID_CONTENT)
            if (contentId != 0) {
                contentContainer = activity.findViewById(contentId)
            }
            if (contentContainer == null) {
                val parent = tabBar.parent as? ViewGroup
                if (parent != null) {
                    for (i in 0 until parent.childCount) {
                        val child = parent.getChildAt(i)
                        if (child is ViewGroup && child !== tabBar && (contentId == 0 || child.id == contentId)) {
                            contentContainer = child
                            break
                        }
                    }
                }
            }
            return contentContainer
        }

        /**
         * Adapts Hush's screensAboveTheBar:
         * Walks every child inside layout_container_main and the swipeable_tab_view_pager sibling.
         * If the child screen has bottomMargin == barHeight, zeroes it out so feed content flows
         * all the way behind the bar to the bottom edge of the screen.
         */
        private fun keepScreensAboveTheBar() {
            val container = getContentContainer() ?: return
            val barH = if (tabBar.height > 0) tabBar.height else originalBarHeight
            if (barH <= 0) return
            if (originalBarHeight < 0) originalBarHeight = barH

            val isFloating = featureState.liquidGlassEdgeToEdge || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            val wanted = if (isFloating) 0 else originalBarHeight

            screensAboveTheBar(container, barH, wanted)
        }

        private fun screensAboveTheBar(content: ViewGroup, barHeight: Int, wanted: Int) {
            for (i in 0 until content.childCount) {
                val child = content.getChildAt(i) ?: continue
                aboveTheBar(child, barHeight, wanted)
            }
            val pagerId = resolveId(activity, ID_SWIPE_PAGER)
            val parent = content.parent as? ViewGroup ?: return
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChildAt(i) ?: continue
                if (pagerId != 0 && sibling.id == pagerId) {
                    aboveTheBar(sibling, barHeight, wanted)
                }
            }
        }

        private fun aboveTheBar(screen: View, barHeight: Int, wanted: Int) {
            val margins = screen.layoutParams as? ViewGroup.MarginLayoutParams ?: return
            if (margins.bottomMargin == barHeight || (wanted == 0 && margins.bottomMargin in (barHeight - 20)..(barHeight + 60))) {
                margins.bottomMargin = wanted
                screen.layoutParams = margins
            }
        }

        // ColorOS / Android 9 layout listener to eliminate empty bottom space
        private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            runCatching {
                keepScreensAboveTheBar()

                // 1. Strip phantom bottom padding injected into the TabBar or its parent (ColorOS / Android 9 fix)
                if (tabBar.paddingBottom > 0) {
                    tabBar.setPadding(tabBar.paddingLeft, tabBar.paddingTop, tabBar.paddingRight, 0)
                }
                (tabBar.parent as? ViewGroup)?.let { parent ->
                    if (parent.paddingBottom > 0) {
                        parent.setPadding(parent.paddingLeft, parent.paddingTop, parent.paddingRight, 0)
                    }
                }

                // 2. Strip bottom margins on tab bar
                (tabBar.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                    if (lp.bottomMargin > 0) {
                        lp.bottomMargin = 0
                        tabBar.layoutParams = lp
                    }
                }

                // 3. Scan root layout children for phantom white spacer views (Realme 1 fix from doc)
                val decor = activity.window?.decorView as? ViewGroup ?: return@runCatching
                for (i in 0 until decor.childCount) {
                    val child = decor.getChildAt(i) ?: continue
                    if (child === tabBar || child === contentContainer || child === topAmbientBlurView || child === bottomAmbientBlurView) continue
                    val h = child.height
                    if (h in 30..250 && child.width >= decor.width * 0.7f) {
                        val loc = IntArray(2)
                        child.getLocationInWindow(loc)
                        if (loc[1] >= decor.height - 300) {
                            val bg = child.background
                            if (bg is ColorDrawable) {
                                val color = bg.color
                                if (color == Color.WHITE || color == Color.BLACK || Color.alpha(color) > 200) {
                                    child.visibility = View.GONE
                                }
                            }
                        }
                    }
                }
            }
        }

        fun install() {
            if (isInstalled) return
            isInstalled = true

            resolveSiblingViews()
            applyEdgeToEdgeSurgery()
            applyAmbientGradientBlurs()
            configureGlassDrawable()
            installGestureSlide()

            tabBar.viewTreeObserver.addOnPreDrawListener(this)
            (activity.window?.decorView as? ViewGroup)?.viewTreeObserver?.addOnGlobalLayoutListener(globalLayoutListener)
            tabBar.background = glassDrawable
            tabBar.clipToPadding = false

            log("LiquidDockController installed on ${activity.javaClass.simpleName}")
        }

        fun refresh() {
            applyEdgeToEdgeSurgery()
            configureGlassDrawable()
        }

        private fun resolveSiblingViews() {
            val shadowId = resolveId(activity, ID_TAB_BAR_SHADOW)
            if (shadowId != 0) tabBarShadow = activity.findViewById(shadowId)

            val contentId = resolveId(activity, ID_CONTENT)
            if (contentId != 0) contentContainer = activity.findViewById(contentId)
        }

        /**
         * 1. Edge-to-Edge Z-Stack Surgery:
         * Clears window navigation scrims, disables Android 16 contrast enforcement,
         * zeroes out content container margins, and eliminates legacy ColorOS 48dp bottom insets.
         */
        private fun applyEdgeToEdgeSurgery() {
            if (!featureState.liquidGlassEdgeToEdge) return

            val window = activity.window ?: return
            val decor = window.decorView

            activity.runOnUiThread {
                runCatching {
                    // Enable edge-to-edge drawing
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        window.setDecorFitsSystemWindows(false)
                    } else {
                        @Suppress("DEPRECATION")
                        decor.systemUiVisibility = decor.systemUiVisibility or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    }

                    // WindowManager background drawing flags (Android 9/10/11)
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                    @Suppress("DEPRECATION")
                    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

                    // Clear system bar colors to pure transparent
                    window.statusBarColor = Color.TRANSPARENT
                    window.navigationBarColor = Color.TRANSPARENT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        window.navigationBarDividerColor = Color.TRANSPARENT
                    }

                    // Zero out bottom insets so WindowManager does not inject 48dp on Android 9/ColorOS
                    decor.setOnApplyWindowInsetsListener { v, insets ->
                        @Suppress("DEPRECATION")
                        val consumed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            insets
                        } else {
                            insets.replaceSystemWindowInsets(
                                insets.systemWindowInsetLeft,
                                insets.systemWindowInsetTop,
                                insets.systemWindowInsetRight,
                                0 // Force bottom inset to 0
                            )
                        }
                        v.onApplyWindowInsets(consumed)
                    }

                    // Disable Android 10+ (Q / Android 14 / 16) navigation contrast scrim enforcement
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                        window.isStatusBarContrastEnforced = false
                    }

                    // Hide the flat divider line above the tab bar
                    tabBarShadow?.visibility = View.GONE

                    // Zero out container bottom margin so media flows behind the floating dock
                    contentContainer?.let { container ->
                        (container.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                            if (params.bottomMargin != 0) {
                                params.bottomMargin = 0
                                container.layoutParams = params
                            }
                        }
                    }
                    keepScreensAboveTheBar()
                }
            }
        }

        /**
         * 2. Progressive Ambient Gradient Blurs:
         * Adds atmospheric gradient depth at the top bezel (cushioning status bar icons & header pucks)
         * and bottom bezel (cushioning the floating stadium dock).
         */
        private fun applyAmbientGradientBlurs() {
            val decor = activity.window?.decorView as? ViewGroup ?: return
            val density = activity.resources.displayMetrics.density

            // Top Ambient Field (~96dp height from status bar downward)
            if (topAmbientBlurView == null) {
                val topHeight = (96 * density).toInt()
                val topGradient = ShapeDrawable(RectShape()).apply {
                    paint.shader = LinearGradient(
                        0f, 0f, 0f, topHeight.toFloat(),
                        Color.argb(0x80, 0x00, 0x00, 0x00), Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                }

                topAmbientBlurView = View(activity).apply {
                    background = topGradient
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        topHeight,
                        Gravity.TOP
                    )
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                decor.addView(topAmbientBlurView, 0)
            }

            // Bottom Ambient Field (~120dp height from bottom bezel upward)
            if (bottomAmbientBlurView == null) {
                val bottomHeight = (120 * density).toInt()
                val bottomGradient = ShapeDrawable(RectShape()).apply {
                    paint.shader = LinearGradient(
                        0f, bottomHeight.toFloat(), 0f, 0f,
                        Color.argb(0x8C, 0x00, 0x00, 0x00), Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                }

                bottomAmbientBlurView = View(activity).apply {
                    background = bottomGradient
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        bottomHeight,
                        Gravity.BOTTOM
                    )
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                val tabBarIndex = decor.indexOfChild(tabBar)
                if (tabBarIndex >= 0) {
                    decor.addView(bottomAmbientBlurView, tabBarIndex)
                } else {
                    decor.addView(bottomAmbientBlurView)
                }
            }
        }

        /**
         * 3. Configures the LiquidGlassDrawable with user settings and dimensions.
         */
        private fun configureGlassDrawable() {
            glassDrawable.apply {
                blurIntensity = featureState.liquidGlassBlurIntensity
                edgeToEdge = featureState.liquidGlassEdgeToEdge
                searchPuckEnabled = featureState.liquidGlassSearchPuck
                isDarkMode = isSystemInDarkTheme()
            }
        }

        private fun isSystemInDarkTheme(): Boolean {
            val nightMode = activity.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
            return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        override fun onPreDraw(): Boolean {
            runCatching {
                val barWidth = tabBar.width.toFloat()
                val barHeight = tabBar.height.toFloat()
                if (barWidth <= 0 || barHeight <= 0) return true

                val density = activity.resources.displayMetrics.density

                // Ensure child screens flow behind bar
                keepScreensAboveTheBar()

                val isAndroid13Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

                if (!isAndroid13Plus) {
                    // Android 12 and below: Full-width glass row (Hush classic style)
                    glassDrawable.setSearchPuckBounds(0f, 0f, 0f, 0f, false)
                    glassDrawable.setPillBounds(0f, 0f, barWidth, barHeight, 0f)

                    val selectedTab = findSelectedTab()
                    if (selectedTab != null) {
                        val tabCenterX = (selectedTab.left + selectedTab.right) * 0.5f

                        if (targetPuckCenterX.isNaN()) {
                            currentPuckCenterX = tabCenterX
                            targetPuckCenterX = tabCenterX
                        } else if (abs(targetPuckCenterX - tabCenterX) > 1.0f) {
                            targetPuckCenterX = tabCenterX
                        }

                        val delta = targetPuckCenterX - currentPuckCenterX
                        val springForce = delta * 0.28f
                        puckVelocityX = (puckVelocityX + springForce) * 0.78f
                        currentPuckCenterX += puckVelocityX

                        val puckWidth = (selectedTab.width.toFloat() - 10 * density).coerceAtLeast(36 * density)
                        val puckHeight = (barHeight - 12 * density).coerceAtLeast(32 * density)
                        val puckCenterY = barHeight * 0.5f
                        val puckRadius = 8 * density

                        glassDrawable.setCapsuleBounds(
                            currentPuckCenterX - puckWidth * 0.5f,
                            puckCenterY - puckHeight * 0.5f,
                            currentPuckCenterX + puckWidth * 0.5f,
                            puckCenterY + puckHeight * 0.5f,
                            puckRadius,
                            1.0f
                        )

                        if (abs(delta) > 0.5f || abs(puckVelocityX) > 0.5f) {
                            tabBar.invalidate()
                        }
                    }
                } else {
                    // Android 13+: Floating AGSL Liquid Dock
                    val dockHeightPx = (featureState.liquidGlassDockHeight * density).coerceAtLeast(36 * density)
                    val dockLiftPx = (featureState.liquidGlassDockLift * density).coerceAtLeast(0f)
                    val horizontalMargin = (barWidth * 0.05f).coerceAtLeast(16 * density) // 90% dock width

                    val dockTop = barHeight - dockHeightPx - dockLiftPx
                    val dockBottom = barHeight - dockLiftPx
                    val dockLeft = horizontalMargin
                    val dockRight = barWidth - horizontalMargin
                    val stadiumRadius = dockHeightPx * 0.5f

                    // Detached Search Puck Geometry
                    val searchTab = findSearchTab()
                    val isApart = featureState.liquidGlassSearchPuck && searchTab != null

                    if (isApart && searchTab != null) {
                        val searchWidth = (44 * density)
                        val searchGap = (8 * density)
                        val searchLeft = dockLeft
                        val searchRight = searchLeft + searchWidth

                        glassDrawable.setSearchPuckBounds(
                            searchLeft, dockTop, searchRight, dockBottom, true
                        )
                        glassDrawable.setPillBounds(
                            searchRight + searchGap, dockTop, dockRight, dockBottom, stadiumRadius
                        )
                    } else {
                        glassDrawable.setSearchPuckBounds(0f, 0f, 0f, 0f, false)
                        glassDrawable.setPillBounds(
                            dockLeft, dockTop, dockRight, dockBottom, stadiumRadius
                        )
                    }

                    // Update Active Tab Compact Puck Position
                    val selectedTab = findSelectedTab()
                    if (selectedTab != null) {
                        val tabCenterX = (selectedTab.left + selectedTab.right) * 0.5f

                        if (targetPuckCenterX.isNaN()) {
                            currentPuckCenterX = tabCenterX
                            targetPuckCenterX = tabCenterX
                        } else if (abs(targetPuckCenterX - tabCenterX) > 1.0f) {
                            targetPuckCenterX = tabCenterX
                        }

                        // Apply spring math: response = 0.32s, damping = 0.78f
                        val delta = targetPuckCenterX - currentPuckCenterX
                        val springForce = delta * 0.28f
                        puckVelocityX = (puckVelocityX + springForce) * 0.78f
                        currentPuckCenterX += puckVelocityX

                        // Dynamic velocity stretch (up to 1.18x during motion)
                        val stretchFactor = 1.0f + (min(abs(puckVelocityX) / (40 * density), 0.18f))
                        val puckSize = (38 * density)
                        val puckHalfWidth = (puckSize * 0.5f) * stretchFactor
                        val puckHalfHeight = (puckSize * 0.5f)
                        val puckCenterY = (dockTop + dockBottom) * 0.5f

                        glassDrawable.setCapsuleBounds(
                            currentPuckCenterX - puckHalfWidth,
                            puckCenterY - puckHalfHeight,
                            currentPuckCenterX + puckHalfWidth,
                            puckCenterY + puckHalfHeight,
                            puckHalfHeight,
                            1.0f
                        )

                        if (abs(delta) > 0.5f || abs(puckVelocityX) > 0.5f) {
                            tabBar.invalidate()
                        }
                    }
                }

                // Record backdrop node at 30Hz throttled rate
                val parent = tabBar.parent as? ViewGroup
                if (parent != null) {
                    glassDrawable.recordBackdropIfDue(parent, tabBar)
                }

                checkReelsState()
            }
            return true
        }

        private fun findSelectedTab(): View? {
            for (i in 0 until tabBar.childCount) {
                val child = tabBar.getChildAt(i)
                if (child.visibility == View.VISIBLE && child.isSelected) {
                    return child
                }
            }
            return null
        }

        private fun findSearchTab(): View? {
            val searchId = resolveId(activity, ID_SEARCH_TAB)
            if (searchId != 0) {
                val found = tabBar.findViewById<View>(searchId)
                if (found != null) return found
            }
            // Fallback: second child is traditionally Search on Instagram
            if (tabBar.childCount >= 2) return tabBar.getChildAt(1)
            return null
        }

        /**
         * 4. Interactive Gesture Sliding:
         * Intercepts horizontal drags across tabs with real-time 120Hz tracking and tactile haptics.
         */
        private fun installGestureSlide() {
            if (!featureState.liquidGlassSlideTabs) return

            var startX = 0f
            var startY = 0f
            var isDragging = false
            var lastTickedTabIndex = -1
            val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop

            tabBar.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.x
                        startY = event.y
                        isDragging = false
                        lastTickedTabIndex = -1
                        resetReelsTimer()
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - startX
                        val dy = event.y - startY

                        if (!isDragging && abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                            isDragging = true
                        }

                        if (isDragging) {
                            currentPuckCenterX = event.x
                            tabBar.invalidate()

                            // Check which tab center is under finger
                            val tabIndex = findTabIndexAt(event.x)
                            if (tabIndex != -1 && tabIndex != lastTickedTabIndex) {
                                lastTickedTabIndex = tabIndex
                                if (featureState.liquidGlassHaptics) {
                                    tabBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            isDragging = false
                            val targetIndex = findTabIndexAt(event.x)
                            if (targetIndex != -1 && targetIndex < tabBar.childCount) {
                                val targetTab = tabBar.getChildAt(targetIndex)
                                if (targetTab != null && !targetTab.isSelected) {
                                    targetTab.performClick()
                                }
                            }
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
        }

        private fun findTabIndexAt(x: Float): Int {
            for (i in 0 until tabBar.childCount) {
                val child = tabBar.getChildAt(i)
                if (child.visibility == View.VISIBLE && x >= child.left && x <= child.right) {
                    return i
                }
            }
            return -1
        }

        /**
         * 5. Reels Protection & Auto-Hide:
         * Elevates the video scrubber above the dock and auto-fades the dock after 3s.
         */
        private fun checkReelsState() {
            val reelsRootId = resolveId(activity, ID_REELS_ROOT)
            val decor = activity.window?.decorView as? ViewGroup ?: return
            val reelsView = if (reelsRootId != 0) decor.findViewById<View>(reelsRootId) else null
            val isNowReels = reelsView != null && reelsView.visibility == View.VISIBLE

            if (isNowReels != isReelsVisible) {
                isReelsVisible = isNowReels
                if (isReelsVisible) {
                    resetReelsTimer()
                    protectReelsScrubber(decor)
                } else {
                    handler.removeCallbacks(reelsHideRunnable)
                    tabBar.animate().alpha(1f).setDuration(200L).start()
                }
            }
        }

        private fun resetReelsTimer() {
            handler.removeCallbacks(reelsHideRunnable)
            if (tabBar.alpha < 1f) {
                tabBar.animate().alpha(1f).setDuration(180L).start()
            }
            if (isReelsVisible && featureState.liquidGlassReelsHide > 0) {
                handler.postDelayed(reelsHideRunnable, featureState.liquidGlassReelsHide * 1000L)
            }
        }

        private fun protectReelsScrubber(decor: ViewGroup) {
            val scrubberId = resolveId(activity, ID_SCRUBBER)
            val touchStripId = resolveId(activity, ID_TOUCH_STRIP)
            val density = activity.resources.displayMetrics.density
            val liftWanted = ((featureState.liquidGlassDockHeight + featureState.liquidGlassDockLift + 8) * density).toInt()

            listOf(scrubberId, touchStripId).filter { it != 0 }.forEach { id ->
                decor.findViewById<View>(id)?.let { view ->
                    (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                        if (params.bottomMargin < liftWanted) {
                            params.bottomMargin = liftWanted
                            view.layoutParams = params
                        }
                    }
                }
            }
        }

        fun dispose() {
            handler.removeCallbacks(reelsHideRunnable)
            tabBar.viewTreeObserver.removeOnPreDrawListener(this)
            runCatching {
                (activity.window?.decorView as? ViewGroup)?.viewTreeObserver?.removeOnGlobalLayoutListener(globalLayoutListener)
            }
            glassDrawable.release()
            log("LiquidDockController disposed on ${activity.javaClass.simpleName}")
        }
    }
}
