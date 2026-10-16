package me.eternal.purrfect.core.instagram.ui

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import me.eternal.purrfect.core.logger.CoreLogger
import java.util.WeakHashMap

/**
 * Dedicated fix for eliminating empty bottom space, phantom navigation bar padding,
 * and ColorOS gesture insets on Android 9–11 devices.
 *
 * Can run standalone via the "Remove Empty Bottom Space" setting, or automatically
 * in tandem with Liquid Glass UI.
 */
object InstagramBottomSpaceFix {

    private const val TAG = "InstagramBottomSpaceFix"
    private const val ID_TAB_BAR = "tab_bar"

    private var isHooked = false
    private val idCache = HashMap<String, Int>()
    private val activeListeners = WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>()

    fun init() {
        if (isHooked) return
        isHooked = true

        // 1. Piko Proactive Hook: Force Instagram's navigation bar detector to evaluate false
        runCatching {
            val systemNavId = runCatching {
                Resources.getSystem().getIdentifier("config_showNavigationBar", "bool", "android")
            }.getOrDefault(0)

            XposedBridge.hookAllMethods(
                Resources::class.java,
                "getIdentifier",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val name = param.args.getOrNull(0) as? String ?: return
                        if (name == "config_showNavigationBar") {
                            param.result = 0
                        }
                    }
                }
            )

            XposedBridge.hookAllMethods(
                Resources::class.java,
                "getBoolean",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val id = param.args.getOrNull(0) as? Int ?: return
                        if (systemNavId != 0 && id == systemNavId) {
                            param.result = false
                        }
                    }
                }
            )
            log("config_showNavigationBar spoofing active (empty bottom space prevention, id=$systemNavId)")
        }.onFailure { throwable ->
            log("Failed to register config_showNavigationBar spoof: ${throwable.message}")
        }

        // 2. Activity Lifecycle Hook: Zero window insets and strip layout padding
        runCatching {
            XposedBridge.hookAllMethods(
                Activity::class.java,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val activity = param.thisObject as? Activity ?: return
                        attachToActivity(activity)
                    }
                }
            )

            XposedBridge.hookAllMethods(
                Activity::class.java,
                "onDestroy",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val activity = param.thisObject as? Activity ?: return
                        val listener = activeListeners.remove(activity) ?: return
                        runCatching {
                            (activity.window?.decorView as? ViewGroup)?.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
                        }
                    }
                }
            )
            log("Lifecycle hooks registered successfully")
        }.onFailure { throwable ->
            log("Failed to hook Activity lifecycle: ${throwable.message}")
        }
    }

    private fun attachToActivity(activity: Activity) {
        val name = activity.javaClass.simpleName
        if (name.contains("Splash", ignoreCase = true) || name.contains("Launcher", ignoreCase = true)) return

        val window = activity.window ?: return
        val decor = window.decorView as? ViewGroup ?: return

        decor.post {
            runCatching {
                applyWindowFlags(activity)
                stripBottomPadding(activity, decor)
            }
        }
    }

    private fun applyWindowFlags(activity: Activity) {
        val window = activity.window ?: return
        val decor = window.decorView ?: return

        activity.runOnUiThread {
            runCatching {
                // WindowManager background drawing flags (Android 9/10/11)
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                @Suppress("DEPRECATION")
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

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
            }
        }
    }

    private fun stripBottomPadding(activity: Activity, decor: ViewGroup) {
        val tabBarId = resolveId(activity, ID_TAB_BAR)
        if (tabBarId == 0) return
        val tabBar = decor.findViewById<ViewGroup>(tabBarId) ?: return

        if (activeListeners.containsKey(activity)) return

        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            runCatching {
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
                for (i in 0 until decor.childCount) {
                    val child = decor.getChildAt(i) ?: continue
                    if (child === tabBar) continue
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

        activeListeners[activity] = listener
        decor.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun resolveId(context: Context, name: String): Int {
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
}
