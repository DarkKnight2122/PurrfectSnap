package me.eternal.purrfect.core.instagram

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.Notification
import android.app.NotificationManager
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.database.sqlite.SQLiteDatabase
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.ViewStub
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.Space
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import me.eternal.purrfect.common.Constants
import me.eternal.purrfect.core.logger.CoreLogger
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.HashSet
import java.util.IdentityHashMap
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlin.math.roundToInt

class InstagramHooks(
    private val androidContext: Context,
    private val appClassLoader: ClassLoader = androidContext.classLoader,
    private val sourceApkPath: String? = null
) {
    companion object {
        private val activeHooks = Collections.synchronizedSet(
            Collections.newSetFromMap(WeakHashMap<InstagramHooks, Boolean>())
        )
        private val urlPattern = Pattern.compile("https?://[^\\s<>\"']+")
        private val instagramShareLinkPattern = Pattern.compile(
            "https?://(?:www\\.|m\\.)?(instagram\\.com|instagr\\.am)(/[^\\s<>\"']*)?",
            Pattern.CASE_INSENSITIVE
        )
        private val trackingParams = setOf(
            "igsh", "igshid", "ig_rid", "ig_mid", "ig_noroute", "ig_cache_key",
            "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid",
            "mc_cid", "mc_eid", "mibextid", "si", "spm", "ref", "ref_src",
            "share_id", "sharing_source", "tracking_token", "xmt", "utm_source",
            "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
            "story_media_id", "saved_by", "saved-by"
        )
        private const val IS_EMPLOYEE_CONFIG_ID = 36310864701161762L
        private const val REQUEST_PICK_ANY_FILE = 0x1EAF120
        private const val UPLOAD_BUTTON_TAG = "purrfect_insta_upload_file_button"
        private const val FEED_DOWNLOAD_BUTTON_TAG = "ie_media_download_btn"
        private const val TARGET_UPLOAD_QUALITY = 95
        private val TAG_REEL_MEDIA = "ie_reel_media".hashCode()
        private const val DM_ACTION_DOWNLOAD = 0
        private const val DM_ACTION_OPEN_APPS = 1
        private const val DM_ACTION_PREVIEW = 2
        private const val DM_ACTION_REPOST = 3
        private const val DM_ACTION_WHATSAPP_STORY = 4
        private const val DM_MESSAGE_ACTION_MARKER_PREFIX = "IE_DM_CONTEXT:"
        private const val STORY_OPTION_DOWNLOAD = "Download"
        private const val STORY_OPTION_MARK_SEEN = "Mark as Seen"
        private const val STORY_OPTION_REPOST = "Repost Story"
        private const val KEEP_UNSENT_MARKER_TAG = "ie_keep_unsent_message_deleted_marker"
        private const val KEEP_UNSENT_MARKER_TEXT = "Message Deleted"
        private const val KEEP_UNSENT_PREFS = "instaeclipse_keep_unsent_deleted"
        private const val KEEP_UNSENT_PREF_IDS = "deleted_message_ids"
        private const val MAX_KEEP_UNSENT_IDS = 1024
        private const val DEFAULT_VIEW_SCAN_LIMIT = 650
        private const val REFRESH_VIEW_SCAN_LIMIT = 2_500
        private const val BIOGRAPHY_HASH = 60358643
        private val broadMimeTypes = arrayOf(
            "*/*",
            "audio/*",
            "image/*",
            "video/*",
            "application/octet-stream",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/zip",
            "application/x-7z-compressed",
            "application/vnd.android.package-archive",
            "text/plain"
        )
        private val pickerActions = setOf(Intent.ACTION_GET_CONTENT, Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_PICK)
        private val mediaStoreDownloadRoots = setOf(
            "Download",
            "Downloads",
            "Pictures",
            "DCIM",
            "Movies",
            "Music",
            "Ringtones",
            "Alarms",
            "Notifications",
            "Podcasts",
            "Audiobooks"
        )
        private val dmActionLabels = arrayOf(
            "Download",
            "Open by Phone Apps",
            "Preview",
            "Repost",
            "Share to Whatsapp Story"
        )

        fun refreshActiveHooks(reason: String) {
            synchronized(activeHooks) { activeHooks.toList() }.forEach { it.refreshVisibleRoots(reason) }
        }
    }

    private val installed = AtomicBoolean(false)
    private val hookedDexMethods = Collections.synchronizedSet(mutableSetOf<String>())
    private val scannedRoots = Collections.synchronizedMap(WeakHashMap<View, Long>())
    private val trackedRoots = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    private val quickToggleButtons = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Activity, Boolean>()))
    private val recentMediaUrls = Collections.synchronizedList(mutableListOf<String>())
    private val searchHookedViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    private val inboxHookedViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    private val entryPointLayoutRetries = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Activity, Boolean>()))
    private val entryPointSearchWiringDone = Collections.synchronizedMap(WeakHashMap<Activity, Boolean>())
    private val directSeenControls = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    private val uploadButtonAnchors = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    private val captureOverlayStates = Collections.synchronizedMap(WeakHashMap<Activity, CaptureOverlayState>())
    private val storyTrayImageUrls = Collections.synchronizedMap(WeakHashMap<View, String>())
    private val profileDownloadHookedPics = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    private val profilePendingRootInjections = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    private val profilePendingCardInjections = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ViewGroup, Boolean>()))
    private val feedDownloadButtonUrls = Collections.synchronizedMap(WeakHashMap<View, List<String>>())
    private val dmMenuAnchors = Collections.synchronizedMap(WeakHashMap<Any, View>())
    private val dmImageViewUrls = Collections.synchronizedMap(WeakHashMap<View, String>())
    private val dmCustomLongPressActions = Collections.synchronizedMap(WeakHashMap<Any, Int>())
    private val dmCustomLongPressEnumActions = Collections.synchronizedMap(WeakHashMap<Any, Int>())
    private val dmMessageActionControllers = Collections.synchronizedMap(WeakHashMap<Any, Any>())
    private val dmMessageActionLifecycles = Collections.synchronizedMap(WeakHashMap<Any, DmMessageActionLifecycle>())
    private val dmKnownAudioUrls = ArrayDeque<String>()
    private val dmRecentUrls = ArrayDeque<RecentMediaUrl>()
    private val navigationHiddenViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    private val navigationAppliedBars = Collections.synchronizedMap(WeakHashMap<ViewGroup, String>())
    private val nativeNavigationTabKeys = Collections.synchronizedMap(WeakHashMap<View, String>())
    private val nativeNavigationTabBars = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ViewGroup, Boolean>()))
    private val navigationDefaultClickedTargets = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    private val sponsoredHiddenViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    private val hiddenUiOriginalStates = Collections.synchronizedMap(WeakHashMap<View, HiddenUiState>())
    private val hiddenUiCollapsedWrappers = Collections.synchronizedMap(WeakHashMap<View, View>())
    private val hiddenUiComposerAdjustments = Collections.synchronizedMap(WeakHashMap<View, ComposerUiAdjustment>())
    private val hiddenUiRowAdjustments = Collections.synchronizedMap(WeakHashMap<View, HiddenUiRowAdjustment>())
    private val hiddenUiInternalChange = ThreadLocal<Boolean>()
    private val monetAppliedBackgrounds = Collections.synchronizedMap(WeakHashMap<View, Int>())
    private val storyRingIdNameCache = Collections.synchronizedMap(LinkedHashMap<Int, String>())
    private val storyRingOriginalLayoutSizes = Collections.synchronizedMap(WeakHashMap<View, OriginalLayoutSize>())
    private val storyRingLoggedTargets = Collections.synchronizedSet(mutableSetOf<String>())
    private val storyRingScaledDimenPixels = Collections.synchronizedSet(mutableSetOf<Int>())
    private val storyRingTrayResolveDepth = ThreadLocal.withInitial { 0 }
    private val teenIconPreparedViews = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val manualPlayDrawables = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Drawable, Boolean>()))
    private val manualPlayDrawableStates = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Drawable.ConstantState, Boolean>()))
    private val manualPlayDrawableViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    private val suppressProfileActionBarChildHook = ThreadLocal<Boolean>()
    private val sponsoredRecyclerHooked = AtomicBoolean(false)
    private val sponsoredModelHooksInstalled = AtomicBoolean(false)
    private val monetHooksInstalled = AtomicBoolean(false)
    private val storyRingViewHooksInstalled = AtomicBoolean(false)
    private val storyRingTrayConstructorHooked = AtomicBoolean(false)
    private val manualPlayOverlayHooksInstalled = AtomicBoolean(false)
    private val pendingFollowCallbacks = ConcurrentHashMap<Int, String>()
    private val hookedFollowCallbackClasses = Collections.synchronizedSet(mutableSetOf<String>())
    private val storyMentionGetterCandidates = Collections.synchronizedList(mutableListOf<Method>())
    private val storySeenMethods = CopyOnWriteArrayList<Method>()
    private val storySeenBuilderMethods = CopyOnWriteArrayList<Method>()
    private val keepUnsentDeletedIds = Collections.synchronizedMap(
        object : LinkedHashMap<String, Boolean>(MAX_KEEP_UNSENT_IDS + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > MAX_KEEP_UNSENT_IDS
        }
    )
    private val keepUnsentBoundRows = Collections.synchronizedMap(
        object : LinkedHashMap<String, WeakReference<View>>(MAX_KEEP_UNSENT_IDS + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WeakReference<View>>?): Boolean = size > MAX_KEEP_UNSENT_IDS
        }
    )
    private val keepUnsentMarkedTextOriginals = Collections.synchronizedMap(WeakHashMap<TextView, CharSequence>())
    private val keepUnsentPersistLock = Any()
    private val remoteDeleteIds = ThreadLocal<Set<String>?>()
    private val copyBioHookedTextViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<TextView, Boolean>()))
    private val copyBioProfileMenuRows = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<TextView, Boolean>()))
    private val recentBiosByUsername = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(80, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 80
        }
    )
    private val knownChatNames = Collections.synchronizedSet(LinkedHashSet<String>())
    private val voiceSeenRequests = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Any, Boolean>()))
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dexBridge by lazy { InstagramDexKitBridge(sourceApkPath, appClassLoader) }
    private val devOptionsMetaConfig by lazy {
        InstagramDevOptionsMetaConfig(androidContext, appClassLoader, dexBridge) { state.isDevEnabled }
    }
    private val state get() = InstagramFeatureStateStore.current
    private var currentActivity: Activity? = null
    private var currentSettingsDialog: AlertDialog? = null
    @Volatile private var allowDmSeenUntilMs: Long = 0L
    @Volatile private var allowStorySeenUntilMs: Long = 0L
    @Volatile private var lastBlockedDmSeenMethodCall: BlockedSeenMethodCall? = null
    @Volatile private var lastBlockedDmSeenNetworkRequest: BlockedSeenNetworkRequest? = null
    @Volatile private var lastBlockedStorySeenOwner: WeakReference<Any>? = null
    @Volatile private var lastSeenStoryBuilderOwner: WeakReference<Any>? = null
    @Volatile private var lastSeenStoryBuilderArgs: Array<Any?>? = null
    @Volatile private var lastBlockedStorySeenNetworkRequest: BlockedSeenNetworkRequest? = null
    @Volatile private var entrySearchTabId = 0
    @Volatile private var entryActionBarEndId = 0
    @Volatile private var entryInboxButtonId = 0
    @Volatile private var entryDirectTabId = 0
    @Volatile private var composerOverflowButtonId = 0
    @Volatile private var composerOverflowLeftButtonId = 0
    @Volatile private var composerButtonsContainerId = 0
    @Volatile private var composerGalleryButtonId = 0
    @Volatile private var composerVoiceButtonId = 0
    @Volatile private var composerStickerButtonId = 0
    @Volatile private var loggedComposerIds = false
    @Volatile private var uploadQualityLogCount = 0
    @Volatile private var uploadQualitySkippedLogCount = 0
    @Volatile private var mediaClass: Class<*>? = null
    @Volatile private var mediaImageUrlMethod: Method? = null
    @Volatile private var mutableMediaDictIntfClass: Class<*>? = null
    @Volatile private var userClass: Class<*>? = null
    @Volatile private var userUsernameGetter: Method? = null
    private val mediaCarouselCandidates = CopyOnWriteArrayList<Method>()
    @Volatile private var storyVideoVersionIntfClass: Class<*>? = null
    @Volatile private var storyVideoVersionGetUrl: Method? = null
    @Volatile private var dmMenuClickInterfaceClass: Class<*>? = null
    @Volatile private var dmPrismItemConstructor: Constructor<*>? = null
    @Volatile private var dmLegacyItemConstructor: Constructor<*>? = null
    @Volatile private var dmMessageActionsViewModelClass: Class<*>? = null
    @Volatile private var dmMessageActionsControllerClass: Class<*>? = null
    @Volatile private var dmLongPressActionDataClass: Class<*>? = null
    @Volatile private var dmLongPressActionEnumClass: Class<*>? = null
    @Volatile private var dmLongPressActionDataConstructor: Constructor<*>? = null
    @Volatile private var dmNormalLongPressType: Any? = null
    @Volatile private var dmCustomLongPressEnum: Any? = null
    @Volatile private var dmPassiveLongPressEnum: Any? = null
    @Volatile private var dmImageUrlClass: Class<*>? = null
    @Volatile private var dmMessageActionClickHooked = false
    @Volatile private var dmNativeModalSuppressorHooked = false
    @Volatile private var dmDeferredActionResumeHooked = false
    @Volatile private var dmSuppressModalLaunchUntilMs = 0L
    @Volatile private var dmPendingDeferredAction: DeferredDmAction? = null
    @Volatile private var dmLatestStableActionActivity: WeakReference<Activity>? = null
    @Volatile private var dmLatestMessageActionLifecycle: DmMessageActionLifecycle? = null
    @Volatile private var dmLatestMessageActionController: Any? = null
    @Volatile private var dmLastVoiceContextAtMs = 0L
    @Volatile private var lastProfilePicUrl: String? = null
    @Volatile private var profileResourceIdsResolved = false
    @Volatile private var expandedProfilePicId = 0x7f0b16d8
    @Volatile private var profileShareCardId = 0x7f0b3070
    @Volatile private var profileShareCardDownloadButtonId = 0x7f0b3074
    @Volatile private var profileShareCardDownloadSpacerId = 0x7f0b3075
    @Volatile private var profileInteractionIconId = 0x7f0b3048
    @Volatile private var profileInteractionLabelId = 0x7f0b3049
    @Volatile private var profileInteractionLayoutId = 0x7f0e0b02
    @Volatile private var feedRefreshDialogPending = false
    @Volatile private var reelsRefreshDialogPending = false
    @Volatile private var monetPalette: MonetPalette? = null
    @Volatile private var allowFeedRefreshUntilMs = 0L
    @Volatile private var allowReelsRefreshUntilMs = 0L
    @Volatile private var confirmRefreshUiGuardHookedClasses = 0
    @Volatile private var currentFollowStatusUserId: String? = null
    @Volatile private var storyMentionGetterMethod: Method? = null
    @Volatile private var notesSpoofLogCount = 0
    @Volatile private var keepUnsentPersistedLoaded = false
    @Volatile private var keepUnsentPersistDirty = false
    @Volatile private var keepUnsentRowDecoratorSeen = false
    @Volatile private var keepUnsentMarkerAppliedSeen = false
    @Volatile private var keepUnsentFuzzyMatchSeen = false
    @Volatile private var keepUnsentRowBindingSeen = false
    @Volatile private var keepUnsentMissedDecorationSeen = false
    @Volatile private var knownChatNamesPersistScheduled = false
    @Volatile private var lastSeenBioText: String? = null
    @Volatile private var lastSeenBioUsername: String? = null
    @Volatile private var currentProfileBioText: String? = null
    @Volatile private var currentProfileUsername: String? = null
    @Volatile private var currentProfileBioAtMs = 0L
    @Volatile private var copyBioMenuActiveUntilMs = 0L
    @Volatile private var copyBioLogCount = 0
    @Volatile private var commentCopyLogCount = 0
    private val allowManualDmSeen = ThreadLocal.withInitial { false }
    private val allowManualStorySeen = ThreadLocal.withInitial { false }
    private val launchingExternalBrowser = ThreadLocal.withInitial { false }
    private val suppressDmMessageActionOpenLifecycle = ThreadLocal.withInitial { false }
    private val handlingCopyBioMenuClick = ThreadLocal.withInitial { false }

    fun init() {
        synchronized(activeHooks) { activeHooks += this }
        if (!installed.compareAndSet(false, true)) return
        logInfo(
            "Installing Instagram hooks from ${state.source}: " +
                "privacy=${state.isGhostSeen || state.isGhostTyping || state.isGhostStory}, " +
                "ui=${state.hasAnyFeedSuppression()}, downloads=${hasAnyDownloadFeature()}, " +
                "network=${state.isAdBlockEnabled || state.isAnalyticsBlocked || state.disableTrackingLinks}"
        )
        installScreenshotAndWindowHooks()
        installActivityHooks()
        installViewHooks()
        installSponsoredRecyclerHook()
        installTextHooks()
        installIntentAndClipboardHooks()
        installConfirmRefreshHooks()
        installNotificationHooks()
        installPreferenceHooks()
        installRecentSearchHooks()
        installResourceHooks()
        installManualVideoPlayOverlayHooks()
        installMonetThemeHooks()
        installGestureHooks()
        installInstagramImageHooks()
        installPostMediaUrlCaptureHooks()
        installHighQualityUploadHooks()
        installStartVideosWithSoundHooks()
        initStoryVideoVersionReflection()
        initMediaDownloadReflection()
        InstagramCustomEmojiFontHooks.install(androidContext, appClassLoader)
        InstagramShareSheetEmojiShortcutHooks.install(appClassLoader)
        InstagramActivityHistoryHooks.install(androidContext, appClassLoader)
        installOkHttpHooks()
        installTigonHooks()
        installDexKitHooks()
    }

    private fun installScreenshotAndWindowHooks() {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam<*>) {
                if (!state.allowScreenshots) return
                val flags = param.args.getOrNull(0) as? Int ?: return
                if ((flags and WindowManager.LayoutParams.FLAG_SECURE) != 0) {
                    if (param.method.name == "addFlags") {
                        param.args[0] = flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                    } else if (param.args.size >= 2) {
                        val mask = param.args[1] as? Int ?: return
                        param.args[0] = flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                        param.args[1] = mask and WindowManager.LayoutParams.FLAG_SECURE.inv()
                    }
                    logInfo("Stripped Instagram secure screenshot flag")
                }
            }
        }
        runSafe("Window screenshot hooks") {
            XposedBridge.hookAllMethods(Window::class.java, "addFlags", hook)
            XposedBridge.hookAllMethods(Window::class.java, "setFlags", hook)
        }
    }

    private fun installActivityHooks() {
        runSafe("Activity lifecycle hooks") {
            fun applyMonetActivity(activity: Activity) {
                if (!state.enableMonetTheme) return
                currentActivity = activity
                refreshMonetPalette(activity)
                applyMonetToActivity(activity)
            }
            val monetActivityHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam<*>) {
                    applyMonetActivity(param.thisObject as? Activity ?: return)
                }
            }
            XposedBridge.hookAllMethods(
                Activity::class.java,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val activity = param.thisObject as? Activity ?: return
                        currentActivity = activity
                        if (state.allowScreenshots) activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        rememberRoot(activity.window?.decorView)
                        if (state.enableMonetTheme) {
                            refreshMonetPalette(activity)
                            applyMonetToActivity(activity)
                        }
                        refreshActivity(activity, "onResume")
                        wireInstagramEntryPoints(activity)
                        applyGhostIndicator(activity)
                        applyCaptureOverlay(activity)
                        clickDefaultNavigationTab(activity)
                        activity.window?.decorView?.postDelayed({
                            InstagramFeatureStatus.maybeShowLoadedToast(activity, state)
                        }, 1_500L)
                    }
                }
            )
            listOf("onStart", "onPostResume", "onAttachedToWindow", "setContentView").forEach { methodName ->
                runCatching { XposedBridge.hookAllMethods(Activity::class.java, methodName, monetActivityHook) }
            }
            XposedBridge.hookAllMethods(
                Activity::class.java,
                "onWindowFocusChanged",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (param.args.firstOrNull() != true) return
                        applyMonetActivity(param.thisObject as? Activity ?: return)
                    }
                }
            )
            XposedBridge.hookAllMethods(
                Activity::class.java,
                "onDestroy",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val activity = param.thisObject as? Activity
                        quickToggleButtons.remove(activity)
                        activity?.let {
                            captureOverlayStates.remove(it)?.remove()
                            if (currentActivity == it) currentActivity = null
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                Activity::class.java,
                "onActivityResult",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (!state.enableDmAnyFileUpload) return
                        val requestCode = param.args.getOrNull(0) as? Int ?: return
                        if (requestCode != REQUEST_PICK_ANY_FILE) return
                        val activity = param.thisObject as? Activity ?: return
                        val resultCode = param.args.getOrNull(1) as? Int ?: Activity.RESULT_CANCELED
                        val data = param.args.getOrNull(2) as? Intent
                        handlePickedAnyFile(activity, resultCode, data)
                        param.result = null
                    }
                }
            )
        }
    }

    private fun installViewHooks() {
        val attachHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam<*>) {
                val view = param.thisObject as? View ?: return
                rememberRoot(view)
                processView(view, "attach")
            }
        }
        runSafe("View hooks") {
            XposedBridge.hookAllMethods(View::class.java, "onAttachedToWindow", attachHook)
            XposedBridge.hookAllMethods(
                View::class.java,
                "onLayout",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val view = param.thisObject as? View ?: return
                        rememberRoot(view)
                        if (shouldEnforceHiddenUiView(view)) {
                            forceHiddenUiView(view)
                        } else {
                            restoreHiddenUiView(view)
                            enforceComposerUiAdjustment(view)
                            enforceHiddenUiRowAdjustment(view)
                        }
                        processView(view, "layout")
                    }
                }
            )
            XposedBridge.hookAllMethods(
                View::class.java,
                "layout",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (hiddenUiInternalChange.get() == true) return
                        val view = param.thisObject as? View ?: return
                        if (shouldEnforceHiddenUiView(view)) {
                            rememberHiddenUiState(view)
                            forceHiddenUiView(view)
                            if (matchesExplicitHiddenUiRule(view)) compactRowsAroundHiddenUiView(view, null)
                        } else {
                            enforceComposerUiAdjustment(view)
                            enforceHiddenUiRowAdjustment(view)
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                ViewGroup::class.java,
                "addView",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val child = param.args.firstOrNull { it is View } as? View ?: return
                        processView(child, "addView")
                        (param.thisObject as? View)?.let { parent ->
                            if (state.isAdBlockEnabled) hideSponsoredSurfaceIfNeeded(parent)
                            if (state.enableProfileDownload && suppressProfileActionBarChildHook.get() != true && parent is ViewGroup && isProfileShareCardFast(parent)) {
                                scheduleProfileCardInjection(parent, 64L)
                            }
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                ViewStub::class.java,
                "inflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        (param.result as? View)?.let { processView(it, "inflate") }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                ViewGroup::class.java,
                "onViewAdded",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (!state.enableProfileDownload || suppressProfileActionBarChildHook.get() == true) return
                        val parent = param.thisObject as? ViewGroup ?: return
                        ensureProfileResourceIds(parent.context)
                        if (isProfileShareCardFast(parent)) scheduleProfileCardInjection(parent, 64L)
                    }
                }
            )
            XposedBridge.hookAllMethods(
                ViewGroup::class.java,
                "removeAllViews",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (!state.enableProfileDownload || suppressProfileActionBarChildHook.get() == true) return
                        val parent = param.thisObject as? ViewGroup ?: return
                        ensureProfileResourceIds(parent.context)
                        if (isProfileShareCardFast(parent)) scheduleProfileCardInjection(parent, 96L)
                    }
                }
            )
            XposedBridge.hookAllMethods(
                View::class.java,
                "setVisibility",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (hiddenUiInternalChange.get() == true) return
                        val view = param.thisObject as? View ?: return
                        if (shouldEnforceHiddenUiView(view)) {
                            rememberHiddenUiState(view)
                            param.args[0] = View.GONE
                        } else if (hasAnyViewHideRule() && shouldHideView(view)) {
                            rememberHiddenUiState(view)
                            param.args[0] = View.GONE
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                View::class.java,
                "setAlpha",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (hiddenUiInternalChange.get() == true) return
                        val view = param.thisObject as? View ?: return
                        if (shouldEnforceHiddenUiView(view)) {
                            rememberHiddenUiState(view)
                            param.args[0] = 0f
                        } else if (hasAnyViewHideRule() && shouldHideView(view)) {
                            rememberHiddenUiState(view)
                            param.args[0] = 0f
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                View::class.java,
                "setEnabled",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (hiddenUiInternalChange.get() == true) return
                        val view = param.thisObject as? View ?: return
                        if (shouldEnforceHiddenUiView(view)) {
                            rememberHiddenUiState(view)
                            param.args[0] = false
                        } else if (hasAnyViewHideRule() && shouldHideView(view)) {
                            rememberHiddenUiState(view)
                            param.args[0] = false
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                View::class.java,
                "setClickable",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (hiddenUiInternalChange.get() == true) return
                        val view = param.thisObject as? View ?: return
                        if (shouldEnforceHiddenUiView(view)) {
                            rememberHiddenUiState(view)
                            param.args[0] = false
                        } else if (hasAnyViewHideRule() && shouldHideView(view)) {
                            rememberHiddenUiState(view)
                            param.args[0] = false
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                View::class.java,
                "setLayoutParams",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (hiddenUiInternalChange.get() == true) return
                        val view = param.thisObject as? View ?: return
                        if (shouldEnforceHiddenUiView(view)) {
                            rememberHiddenUiState(view)
                            forceHiddenUiView(view)
                        } else if (hasAnyViewHideRule() && shouldHideView(view)) {
                            rememberHiddenUiState(view)
                            forceHiddenLayout(view)
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                View::class.java,
                "measure",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (hiddenUiInternalChange.get() == true) return
                        val view = param.thisObject as? View ?: return
                        if (shouldEnforceHiddenUiView(view)) {
                            rememberHiddenUiState(view)
                            forceHiddenUiMeasuredSize(view)
                        }
                    }
                }
            )
            listOf("setPadding", "setPaddingRelative").forEach { methodName ->
                XposedBridge.hookAllMethods(
                    View::class.java,
                    methodName,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam<*>) {
                            if (hiddenUiInternalChange.get() == true) return
                            enforceComposerUiAdjustment(param.thisObject as? View)
                        }
                    }
                )
            }
            XposedBridge.hookAllMethods(
                View::class.java,
                "performLongClick",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val view = param.thisObject as? View ?: return
                        if (handleUploadLongPress(view) || handleDirectSeenLongPress(view) || handleEntryPointLongPress(view) || handleStoryTrayLongPress(view) || handleGifCommentLongPress(view) || handleCopyLongPress(view) || handleCaptureLongPress(view)) {
                            param.result = true
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                View::class.java,
                "performClick",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val view = param.thisObject as? View ?: return
                        if (handleCopyBioMenuClick(view)) param.result = true
                    }
                }
            )
        }
    }

    private fun installSponsoredRecyclerHook() {
        if (!sponsoredRecyclerHooked.compareAndSet(false, true)) return
        runSafe("Sponsored RecyclerView rows") {
            val adapterClass = listOf(
                "androidx.recyclerview.widget.RecyclerView\$Adapter",
                "android.support.v7.widget.RecyclerView\$Adapter"
            ).firstNotNullOfOrNull { className -> runCatching { Class.forName(className, false, appClassLoader) }.getOrNull() }
                ?: return@runSafe
            XposedBridge.hookAllMethods(
                adapterClass,
                "bindViewHolder",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (!state.isAdBlockEnabled || param.args.isEmpty()) return
                        val itemView = getViewHolderItemView(param.args[0]) ?: return
                        if (itemView.rootView == itemView || sponsoredHiddenViews.contains(itemView)) return
                        if (isSponsoredViewTree(itemView)) hideSponsoredContainer(itemView)
                    }
                }
            )
        }
    }

    private fun installTextHooks() {
        runSafe("TextView hooks") {
            XposedBridge.hookAllMethods(
                TextView::class.java,
                "setText",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val textView = param.thisObject as? TextView ?: return
                        processTextView(textView)
                    }
                }
            )
        }
    }

    private fun installIntentAndClipboardHooks() {
        runSafe("Clipboard and Intent hooks") {
            XposedBridge.hookAllMethods(
                ClipboardManager::class.java,
                "setPrimaryClip",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val clip = param.args.firstOrNull() as? ClipData ?: return
                        rewriteCopyBioClipData(clip)?.let {
                            param.args[0] = it
                            return
                        }
                        val sanitized = sanitizeClipData(clip)
                        if (sanitized != null) param.args[0] = sanitized
                    }
                }
            )
            XposedBridge.hookAllMethods(
                ClipboardManager::class.java,
                "setText",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (!state.enableCopyBio || System.currentTimeMillis() > copyBioMenuActiveUntilMs) return
                        val copied = param.args.firstOrNull()?.toString().orEmpty()
                        if (!looksLikeInstagramProfileUrl(copied)) return
                        val bio = lastSeenBioText?.trim().orEmpty()
                        if (isReliableBioText(bio)) param.args[0] = bio
                    }
                }
            )
            XposedBridge.hookAllMethods(
                Intent::class.java,
                "putExtra",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (param.args.size != 2 || param.args[0] !is String) return
                        val intent = param.thisObject as? Intent
                        val key = param.args[0] as String
                        val value = param.args[1]
                        if (state.enableDmAnyFileUpload && key == Intent.EXTRA_MIME_TYPES && value is Array<*> && looksLikePicker(intent)) {
                            @Suppress("UNCHECKED_CAST")
                            param.args[1] = mergeMimeTypes(value.filterIsInstance<String>().toTypedArray())
                            return
                        }
                        when (value) {
                            is String -> param.args[1] = sanitizeTextForSharing(value)
                            is CharSequence -> param.args[1] = sanitizeTextForSharing(value.toString())
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                Intent::class.java,
                "setData",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val uri = param.args.firstOrNull() as? Uri ?: return
                        sanitizeUri(uri, allowShareDomainRewrite = false)?.let { param.args[0] = it }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                Intent::class.java,
                "setDataAndType",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val uri = param.args.firstOrNull() as? Uri ?: return
                        sanitizeUri(uri, allowShareDomainRewrite = false)?.let { param.args[0] = it }
                    }
                }
            )
            val launchHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    val intents = param.args.filterIsInstance<Intent>()
                    intents.forEach { intent ->
                        sanitizeIntent(intent)
                        if (state.enableDmAnyFileUpload) widenFilePicker(intent)
                    }
                    if (state.openLinksExternally && param.method.name == "startActivity") {
                        val intent = intents.firstOrNull()
                        val context = param.thisObject as? Context
                        if (intent != null && context != null && launchExternalBrowserIfNeeded(context, intent)) {
                            param.result = null
                            return
                        }
                    }
                }
            }
            XposedBridge.hookAllMethods(Activity::class.java, "startActivity", launchHook)
            XposedBridge.hookAllMethods(Activity::class.java, "startActivityForResult", launchHook)
            XposedBridge.hookAllMethods(ContextWrapper::class.java, "startActivity", launchHook)
            XposedBridge.hookAllMethods(
                Intent::class.java,
                "setType",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (!state.enableDmAnyFileUpload) return
                        val intent = param.thisObject as? Intent ?: return
                        if (looksLikePicker(intent) && shouldWidenType(param.args.firstOrNull() as? String)) {
                            param.args[0] = "*/*"
                            intent.putExtra(Intent.EXTRA_MIME_TYPES, broadMimeTypes)
                        }
                    }
                }
            )
        }
    }

    private fun installConfirmRefreshHooks() {
        val refreshClasses = listOf(
            "X.G56",
            "p000X.G56",
            "com.instagram.ui.widget.refresh.IgSwipeRefreshLayout",
            "com.instagram.p131ui.widget.refresh.IgSwipeRefreshLayout",
            "androidx.swiperefreshlayout.widget.SwipeRefreshLayout",
            "android.support.v4.widget.SwipeRefreshLayout",
            "com.instagram.features.clips.viewer.ui.ClipsSwipeRefreshLayout",
            "instagram.features.clips.viewer.p154ui.HomecomingSwipeRefreshLayout",
            "instagram.features.clips.viewer.ui.HomecomingSwipeRefreshLayout",
            "com.instagram.ui.widget.refresh.RefreshableNestedScrollingParent"
        )
        refreshClasses.forEach { className ->
            runSafe("Confirm refresh listener $className") {
                val cls = Class.forName(className, false, appClassLoader)
                var classHooked = false
                cls.declaredMethods
                    .filter { method ->
                        method.name == "setOnRefreshListener" &&
                            method.parameterTypes.size == 1 &&
                            method.parameterTypes[0].isInterface
                    }
                    .forEach { method ->
                        method.isAccessible = true
                        val listenerClass = method.parameterTypes[0]
                        XposedBridge.hookMethod(
                            method,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                                    val original = param.args.firstOrNull() ?: return
                                    if (Proxy.isProxyClass(original.javaClass)) return
                                    param.args[0] = wrapRefreshListener(param.thisObject, original, listenerClass)
                                }
                            }
                        )
                        classHooked = true
                    }
                if (classHooked) confirmRefreshUiGuardHookedClasses++
            }
        }
        listOf("X.MRQ", "p000X.MRQ").forEach { className ->
            runSafe("Confirm refresh callback $className") {
                val cls = Class.forName(className, false, appClassLoader)
                var classHooked = false
                cls.declaredMethods
                    .filter { method -> method.name == "FFM" && method.parameterTypes.isEmpty() && method.returnType == java.lang.Void.TYPE }
                    .forEach { method ->
                        method.isAccessible = true
                        XposedBridge.hookMethod(
                            method,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                                    if (!state.enableConfirmRefresh) return
                                    val surface = currentForegroundOrLikelyRefreshSurface() ?: return
                                    if (isRefreshAllowed(surface)) return
                                    clearVisibleRefreshSpinners(currentActivity)
                                    if (isRefreshDialogPending(surface)) {
                                        param.result = null
                                        return
                                    }
                                    setRefreshDialogPending(surface, true)
                                    showConfirmRefreshDialog(
                                        surface,
                                        onConfirm = {
                                            setRefreshDialogPending(surface, false)
                                            allowRefreshSurface(surface, 3_000L)
                                            runCatching { XposedBridge.invokeOriginalMethod(method, param.thisObject, emptyArray<Any>()) }
                                                .onFailure { logError("Confirmed refresh callback failed", it) }
                                        },
                                        onCancel = {
                                            setRefreshDialogPending(surface, false)
                                            clearVisibleRefreshSpinners(currentActivity)
                                        }
                                    )
                                    param.result = null
                                }
                            }
                        )
                        classHooked = true
                    }
                if (classHooked) confirmRefreshUiGuardHookedClasses++
            }
        }
    }

    private fun installConfirmRefreshDexListenerHooks() {
        var hooked = 0
        val hookedOwners = HashSet<String>()
        dexBridge.findMethodsByName("setOnRefreshListener", returnType = "void", paramCount = 1).forEach { method ->
            val params = method.parameterTypes
            if (params.size != 1 || !params[0].isInterface) return@forEach
            val signature = "${methodKey(method)}:confirm_refresh_listener"
            if (!hookedDexMethods.add(signature)) return@forEach
            runCatching {
                method.isAccessible = true
                val listenerClass = params[0]
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam<*>) {
                            val original = param.args.firstOrNull() ?: return
                            if (Proxy.isProxyClass(original.javaClass)) return
                            param.args[0] = wrapRefreshListener(param.thisObject, original, listenerClass)
                        }
                    }
                )
                if (hookedOwners.add(method.declaringClass.name)) hooked++
                logInfo("Hooked Dex refresh listener ${method.declaringClass.name}.${method.name}")
            }.onFailure { logError("Failed Dex refresh listener hook $signature", it) }
        }
        if (hooked > 0) logInfo("Dex refresh guard hooked classes=$hooked")
        if (hooked > 0) confirmRefreshUiGuardHookedClasses += hooked
    }

    private fun wrapRefreshListener(refreshView: Any?, original: Any, listenerClass: Class<*>): Any {
        return Proxy.newProxyInstance(listenerClass.classLoader, arrayOf(listenerClass)) { proxy, method, args ->
            val isRefreshCallback = method.name == "onRefresh" || (method.name == "FFM" && method.parameterTypes.isEmpty())
            if (!state.enableConfirmRefresh || !isRefreshCallback) {
                return@newProxyInstance method.invoke(original, *(args ?: emptyArray()))
            }
            val surface = surfaceForRefreshView(refreshView as? View)
            if (surface == null || isRefreshAllowed(surface)) {
                return@newProxyInstance method.invoke(original, *(args ?: emptyArray()))
            }
            setRefreshing(refreshView, false)
            if (!isRefreshSurfaceForeground(surface) && isClearlyBackgroundRefresh(surface)) {
                logInfo("Blocked background $surface refresh")
                return@newProxyInstance defaultResult(method.returnType)
            }
            if (isRefreshDialogPending(surface)) return@newProxyInstance defaultResult(method.returnType)
            setRefreshDialogPending(surface, true)
            showConfirmRefreshDialog(
                surface,
                onConfirm = {
                    setRefreshDialogPending(surface, false)
                    allowRefreshSurface(surface, 3_000L)
                    runCatching { method.invoke(original, *(args ?: emptyArray())) }
                        .onFailure { logError("Confirmed refresh listener failed", it) }
                },
                onCancel = {
                    setRefreshDialogPending(surface, false)
                    setRefreshing(refreshView, false)
                }
            )
            defaultResult(method.returnType)
        }
    }

    private fun installNotificationHooks() {
        runSafe("Notification hooks") {
            val hook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    val notification = param.args.filterIsInstance<Notification>().firstOrNull() ?: return
                    if (shouldBlockNotification(notification)) {
                        param.result = null
                        logInfo("Blocked Instagram DM media notification")
                    }
                }
            }
            listOf("notify", "notifyAsUser", "notifyAsPackage").forEach { method ->
                XposedBridge.hookAllMethods(NotificationManager::class.java, method, hook)
            }
        }
    }

    private fun installPreferenceHooks() {
        runSafe("SharedPreferences hooks") {
            val prefsClass = Class.forName("android.app.SharedPreferencesImpl")
            XposedBridge.hookAllMethods(
                prefsClass,
                "getBoolean",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val key = param.args.firstOrNull()?.toString()?.lowercase() ?: return
                        when {
                            state.disableVideoAutoPlay && key.contains("autoplay") -> param.result = false
                            (state.enableHighQualityStoryUpload || state.enableHighQualityDmUpload) && key.contains("high_quality") -> param.result = true
                        }
                    }
                }
            )
            val editorClass = Class.forName("android.app.SharedPreferencesImpl\$EditorImpl")
            val editorHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    val key = param.args.firstOrNull()?.toString()?.lowercase() ?: return
                    if (state.doNotSaveRecentSearches && (key.contains("recent_search") || key.contains("search_history"))) {
                        param.result = param.thisObject
                    }
                }
            }
            XposedBridge.hookAllMethods(editorClass, "putBoolean", editorHook)
            XposedBridge.hookAllMethods(editorClass, "putString", editorHook)
            XposedBridge.hookAllMethods(editorClass, "putStringSet", editorHook)
        }
    }

    private fun installRecentSearchHooks() {
        val insertHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam<*>) {
                if (!state.doNotSaveRecentSearches) return
                val table = param.args.firstOrNull() as? String ?: return
                if (table.equals("recent_searches", ignoreCase = true)) {
                    param.result = 1L
                    logInfo("Blocked Instagram recent-search database write")
                }
            }
        }
        val execHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam<*>) {
                if (!state.doNotSaveRecentSearches) return
                val sql = param.args.firstOrNull()?.toString()?.trim()?.lowercase(Locale.US) ?: return
                if (sql.contains("recent_searches") && (sql.startsWith("insert") || sql.startsWith("replace") || sql.startsWith("with "))) {
                    param.result = null
                    logInfo("Blocked Instagram recent-search SQL")
                }
            }
        }
        runSafe("Recent search privacy SQLite hooks") {
            XposedBridge.hookAllMethods(SQLiteDatabase::class.java, "insert", insertHook)
            XposedBridge.hookAllMethods(SQLiteDatabase::class.java, "insertOrThrow", insertHook)
            XposedBridge.hookAllMethods(SQLiteDatabase::class.java, "replace", insertHook)
            XposedBridge.hookAllMethods(SQLiteDatabase::class.java, "replaceOrThrow", insertHook)
            XposedBridge.hookAllMethods(SQLiteDatabase::class.java, "insertWithOnConflict", insertHook)
            XposedBridge.hookAllMethods(SQLiteDatabase::class.java, "execSQL", execHook)
        }
    }

    private fun installResourceHooks() {
        val targetDimens = setOf(
            "prism_avatar_story_ring_size_medium_device",
            "featured_user_story_ring_size",
            "avatar_reel_ring_size_extra_large",
            "prism_avatar_size_small_device"
        )
        val trayOnlyDimens = setOf(
            "avatar_size_ridiculously_large_plus",
            "avatar_size_ridiculously_xlarge",
            "avatar_size_ridiculously_xlarge_plus",
            "prism_avatar_story_ring_width_large_device",
            "prism_avatar_story_ring_width_small_device",
            "tray_pulsing_avatar_size_small",
            "tray_double_avatar_pulsing_avatar_size_small"
        )
        fun factor(): Float {
            return when (state.storyRingSize.lowercase(Locale.US)) {
                "small" -> 0.85f
                "large" -> 1.15f
                "huge" -> 1.30f
                else -> 1f
            }
        }
        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam<*>) {
                val scale = factor()
                if (scale == 1f) return
                val resId = param.args.firstOrNull() as? Int ?: return
                val resources = param.thisObject as? Resources ?: return
                val name = storyRingDimenName(resources, resId)
                val directTarget = name in targetDimens
                val trayOnlyTarget = name in trayOnlyDimens && (inStoryTrayResolve() || stackLooksLikeStoryRingSurface())
                if (!directTarget && !trayOnlyTarget) return
                when (val result = param.result) {
                    is Float -> {
                        val scaled = result * scale
                        param.result = scaled
                        storyRingScaledDimenPixels += scaled.roundToInt().coerceAtLeast(1)
                        logStoryRingTarget(method = "resource", name = name, factor = scale, from = result, to = scaled)
                    }
                    is Int -> {
                        val scaled = (result * scale).roundToInt().coerceAtLeast(1)
                        param.result = scaled
                        storyRingScaledDimenPixels += scaled
                        logStoryRingTarget(method = "resource", name = name, factor = scale, from = result, to = scaled)
                    }
                }
            }
        }
        runSafe("Story ring resource dimension hooks") {
            listOf("getDimension", "getDimensionPixelSize", "getDimensionPixelOffset").forEach { method ->
                XposedBridge.hookAllMethods(Resources::class.java, method, hook)
            }
            installStoryRingViewSizingHooks()
        }
    }

    private fun installManualVideoPlayOverlayHooks() {
        if (!manualPlayOverlayHooksInstalled.compareAndSet(false, true)) return
        runSafe("Manual video play overlay hooks") {
            val drawableLoadHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam<*>) {
                    val resources = param.thisObject as? Resources ?: return
                    val resId = param.args.firstOrNull() as? Int ?: return
                    val drawable = param.result as? Drawable ?: return
                    if (isManualVideoPlayDrawableResource(resources, resId)) rememberPlayDrawable(drawable)
                }
            }
            XposedBridge.hookAllMethods(Resources::class.java, "getDrawable", drawableLoadHook)
            XposedBridge.hookAllMethods(Resources::class.java, "getDrawableForDensity", drawableLoadHook)

            XposedBridge.hookAllMethods(
                ImageView::class.java,
                "setImageResource",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val imageView = param.thisObject as? ImageView ?: return
                        val resId = param.args.firstOrNull() as? Int ?: return
                        if (isManualVideoPlayDrawableResource(imageView.resources, resId)) markPlayDrawableView(imageView)
                    }
                }
            )
            XposedBridge.hookAllMethods(
                ImageView::class.java,
                "setImageDrawable",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val imageView = param.thisObject as? ImageView ?: return
                        val drawable = param.args.firstOrNull() as? Drawable ?: return
                        if (isRememberedPlayDrawable(drawable)) markPlayDrawableView(imageView)
                    }
                }
            )

            val clickHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam<*>) {
                    if (!state.disableVideoAutoPlay) return
                    val view = param.thisObject as? View ?: return
                    if (isManualVideoPlayTarget(view)) schedulePlayOverlayHide(view)
                }
            }
            XposedBridge.hookAllMethods(View::class.java, "performClick", clickHook)
            XposedBridge.hookAllMethods(View::class.java, "callOnClick", clickHook)
        }
    }

    private fun schedulePlayOverlayHide(clickedView: View) {
        val hide = Runnable { hideNearbyPlayOverlays(clickedView) }
        clickedView.post(hide)
        clickedView.postDelayed(hide, 80L)
        clickedView.postDelayed(hide, 250L)
        clickedView.postDelayed(hide, 700L)
    }

    private fun hideNearbyPlayOverlays(clickedView: View) {
        val anchor = closestPlayOverlayView(clickedView) ?: clickedView
        hidePlayOverlayIfSafe(anchor)
        hidePlayOverlayDescendants(anchor, 0)

        var current: View? = anchor
        var depth = 0
        while (current != null && depth++ < 5) {
            val parentView = current.parent as? View ?: break
            if (isPlayOverlayContainer(parentView)) hidePlayOverlayIfSafe(parentView)
            hidePlayOverlayDescendants(parentView, 0)
            current = parentView
        }
    }

    private fun closestPlayOverlayView(view: View): View? {
        var current: View? = view
        var depth = 0
        while (current != null && depth++ < 7) {
            if (isHideablePlayOverlay(current)) return current
            current = current.parent as? View
        }
        return null
    }

    private fun hidePlayOverlayDescendants(view: View, depth: Int) {
        if (view !is ViewGroup || depth > 4) return
        val childCount = minOf(view.childCount, 80)
        for (i in 0 until childCount) {
            val child = view.getChildAt(i)
            hidePlayOverlayIfSafe(child)
            hidePlayOverlayDescendants(child, depth + 1)
        }
    }

    private fun hidePlayOverlayIfSafe(view: View) {
        if (!isHideablePlayOverlay(view)) return
        view.isPressed = false
        view.visibility = View.GONE
    }

    private fun isManualVideoPlayTarget(view: View): Boolean {
        if (isMarkedPlayDrawableView(view) && hasVideoContext(view)) return true

        var current: View? = view
        var depth = 0
        while (current != null && depth++ < 7) {
            val name = resourceEntryName(current)
            if (isStrongPlayOverlayName(name)) return true
            if (isGenericPlayOverlayName(name) && hasVideoContext(current)) return true
            current = current.parent as? View
        }
        return false
    }

    private fun isHideablePlayOverlay(view: View): Boolean {
        if (isMainVideoSurface(view)) return false
        if (isMarkedPlayDrawableView(view) && hasVideoContext(view)) return true

        val name = resourceEntryName(view)
        return isStrongPlayOverlayName(name) || (isGenericPlayOverlayName(name) && hasVideoContext(view))
    }

    private fun isPlayOverlayContainer(view: View): Boolean {
        return when (resourceEntryName(view)) {
            "view_play_button_container",
            "play_button_stub",
            "play_icon_view_stub",
            "zero_rating_video_play_button_stub" -> true
            else -> false
        }
    }

    private fun hasVideoContext(view: View): Boolean {
        var current: View? = view
        var depth = 0
        while (current != null && depth++ < 8) {
            val name = resourceEntryName(current)
            if (name != null && (
                    name.contains("video") ||
                        name.contains("clips") ||
                        name.contains("reel") ||
                        name.contains("media")
                    )
            ) {
                return true
            }
            current = current.parent as? View
        }
        return false
    }

    private fun isMainVideoSurface(view: View): Boolean {
        return when (resourceEntryName(view)) {
            "video_player_container",
            "video_player_view",
            "clips_video_player",
            "clips_video_container",
            "map_video_player_container",
            "content_note_quick_reply_video_player_container" -> true
            else -> false
        }
    }

    private fun isStrongPlayOverlayName(name: String?): Boolean {
        return when (name) {
            "video_play_icon",
            "video_play_pause_button",
            "video_play_toggle_button",
            "fullscreen_video_play_icon",
            "clips_play_button",
            "view_play_button",
            "view_play_button_container",
            "zero_rating_video_play_button_stub" -> true
            else -> false
        }
    }

    private fun isGenericPlayOverlayName(name: String?): Boolean {
        return when (name) {
            "play_button",
            "play_button_stub",
            "play_icon_view_stub",
            "suggested_media_play_button",
            "question_media_play_button",
            "mention_thumbnail_video_play_button" -> true
            else -> false
        }
    }

    private fun isManualVideoPlayDrawableResource(resources: Resources?, resId: Int): Boolean {
        if (resources == null || resId == 0) return false
        return runCatching {
            resources.getResourceTypeName(resId) == "drawable" &&
                resourceEntryName(resources, resId).let { it == "play_button" || it == "play_button_large" }
        }.getOrDefault(false)
    }

    private fun rememberPlayDrawable(drawable: Drawable) {
        manualPlayDrawables += drawable
        drawable.constantState?.let { manualPlayDrawableStates += it }
    }

    private fun isRememberedPlayDrawable(drawable: Drawable?): Boolean {
        drawable ?: return false
        if (manualPlayDrawables.contains(drawable)) return true
        val constantState = drawable.constantState ?: return false
        return manualPlayDrawableStates.contains(constantState)
    }

    private fun markPlayDrawableView(imageView: ImageView) {
        manualPlayDrawableViews += imageView
    }

    private fun isMarkedPlayDrawableView(view: View): Boolean {
        return view is ImageView && manualPlayDrawableViews.contains(view)
    }

    private fun installMonetThemeHooks() {
        if (!monetHooksInstalled.compareAndSet(false, true)) return
        runSafe("Monet theme hooks") {
            val colorHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam<*>) {
                    if (!monetActive()) return
                    val resources = param.thisObject as? Resources ?: return
                    val resId = param.args.firstOrNull() as? Int ?: return
                    val original = param.result as? Int ?: return
                    monetColorForResource(resources, resId, original)?.let { param.result = it }
                }
            }
            XposedBridge.hookAllMethods(Resources::class.java, "getColor", colorHook)

            XposedBridge.hookAllMethods(
                Resources::class.java,
                "getColorStateList",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (!monetActive()) return
                        val resources = param.thisObject as? Resources ?: return
                        val resId = param.args.firstOrNull() as? Int ?: return
                        val name = resourceEntryName(resources, resId) ?: return
                        if (!isInstagramResource(resources, resId)) return
                        monetColorStateListForName(name, param.result as? ColorStateList)?.let { param.result = it }
                    }
                }
            )

            XposedBridge.hookAllMethods(
                Resources.Theme::class.java,
                "resolveAttribute",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (!monetActive() || param.result != true) return
                        val theme = param.thisObject as? Resources.Theme ?: return
                        val attrId = param.args.getOrNull(0) as? Int ?: return
                        val value = param.args.getOrNull(1) as? TypedValue ?: return
                        val attrName = resourceEntryName(theme.resources, attrId)
                        val original = if (isColorValue(value)) value.data else null
                        val replacement = if (value.resourceId != 0) {
                            monetColorForResource(theme.resources, value.resourceId, original)
                        } else null
                        val resolved = replacement ?: monetColorForThemeAttr(attrName, original) ?: return
                        value.data = resolved
                        if (value.resourceId == 0) value.type = TypedValue.TYPE_INT_COLOR_ARGB8
                        param.result = true
                    }
                }
            )

            XposedBridge.hookAllMethods(
                TypedArray::class.java,
                "getColor",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (!monetActive()) return
                        val array = param.thisObject as? TypedArray ?: return
                        val index = param.args.firstOrNull() as? Int ?: return
                        val original = param.result as? Int ?: return
                        monetColorForTypedValue(array.resources, array.peekValue(index), original)?.let { param.result = it }
                    }
                }
            )

            XposedBridge.hookAllMethods(
                TypedArray::class.java,
                "getColorStateList",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (!monetActive()) return
                        val array = param.thisObject as? TypedArray ?: return
                        val index = param.args.firstOrNull() as? Int ?: return
                        val value = array.peekValue(index) ?: return
                        if (value.resourceId == 0) return
                        val resources = array.resources
                        val name = resourceEntryName(resources, value.resourceId) ?: return
                        if (!isInstagramResource(resources, value.resourceId)) return
                        monetColorStateListForName(name, param.result as? ColorStateList)?.let { param.result = it }
                    }
                }
            )

            XposedBridge.hookAllMethods(
                GradientDrawable::class.java,
                "setColor",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val palette = monetPalette() ?: return
                        if (!state.enableMonetTheme || param.args.isEmpty()) return
                        when (val arg = param.args[0]) {
                            is Int -> monetShapeColorReplacement(arg, palette)?.let { param.args[0] = it }
                            is ColorStateList -> monetShapeColorReplacement(arg.defaultColor, palette)
                                ?.let { param.args[0] = ColorStateList.valueOf(it) }
                        }
                    }
                }
            )

            XposedBridge.hookAllMethods(
                View::class.java,
                "setBackgroundColor",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (!monetActive()) return
                        val view = param.thisObject as? View ?: return
                        val original = param.args.firstOrNull() as? Int ?: return
                        clearMonetAppliedBackground(view)
                        monetBackgroundColorForView(view, original)?.let { param.args[0] = it }
                    }
                }
            )

            XposedBridge.hookAllMethods(
                View::class.java,
                "setBackgroundTintList",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (!monetActive()) return
                        val view = param.thisObject as? View ?: return
                        val original = param.args.firstOrNull() as? ColorStateList ?: return
                        clearMonetAppliedBackground(view)
                        monetBackgroundTintForView(view, original)?.let { param.args[0] = it }
                    }
                }
            )

            val backgroundHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    (param.thisObject as? View)?.let(::clearMonetAppliedBackground)
                }

                override fun afterHookedMethod(param: MethodHookParam<*>) {
                    if (!monetActive()) return
                    val view = param.thisObject as? View ?: return
                    recolorMonetAssignedBackground(view, view.background)
                }
            }
            XposedBridge.hookAllMethods(View::class.java, "setBackground", backgroundHook)
            XposedBridge.hookAllMethods(View::class.java, "setBackgroundResource", backgroundHook)

            XposedBridge.hookAllMethods(
                TextView::class.java,
                "setHint",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (monetActive()) (param.thisObject as? TextView)?.let(::handleMonetTextSignal)
                    }
                }
            )
        }
    }

    private fun installGestureHooks() {
        // Double-tap like is handled through the source-shaped DexKit hooks below.
    }

    private fun installInstagramImageHooks() {
        runSafe("Instagram image URL hooks") {
            val imageViewClass = listOf(
                "com.instagram.common.ui.widget.imageview.IgImageView",
                "com.instagram.common.p066ui.widget.imageview.IgImageView"
            ).firstNotNullOfOrNull { className ->
                runCatching { Class.forName(className, false, appClassLoader) }.getOrNull()
            } ?: return@runSafe

            imageViewClass.declaredMethods
                .filter { method -> method.name == "setUrl" && method.parameterTypes.isNotEmpty() }
                .forEach { method ->
                    method.isAccessible = true
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam<*>) {
                                val view = param.thisObject as? View ?: return
                                val url = param.args.firstNotNullOfOrNull { imageUrlFromObject(it) } ?: return
                                if (isHttpImageUrl(url)) {
                                    storyTrayImageUrls[view] = url
                                    if (looksLikeProfileImageUrl(url)) lastProfilePicUrl = url
                                    if (!looksLikeProfileImageUrl(url)) rememberMediaUrl(url)
                                }
                            }
                        }
                    )
                }
            logInfo("Installed Instagram image URL capture on ${imageViewClass.name}")
        }
    }

    private fun installPostMediaUrlCaptureHooks() {
        runSafe("Post media Uri capture") {
            XposedBridge.hookAllMethods(
                Uri::class.java,
                "parse",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (!state.enablePostDownload) return
                        val url = param.args.firstOrNull() as? String ?: return
                        if (looksLikeMediaUrl(url) && !looksLikeProfileImageUrl(url)) rememberMediaUrl(url)
                    }
                }
            )
        }
    }

    private fun installHighQualityUploadHooks() {
        runSafe("High quality upload hooks") {
            XposedBridge.hookAllMethods(
                Bitmap::class.java,
                "compress",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        maybeUpgradeUploadQuality(param, 1, "Bitmap.compress")
                    }
                }
            )
        }
        runSafe("Native JPEG upload quality hooks") {
            val transcoder = Class.forName("com.facebook.imagepipeline.nativecode.NativeJpegTranscoder", false, appClassLoader)
            listOf("nativeTranscodeJpeg", "nativeTranscodeJpegWithExifOrientation").forEach { methodName ->
                transcoder.declaredMethods
                    .filter { method -> method.name == methodName && method.parameterTypes.lastOrNull() == java.lang.Integer.TYPE }
                    .forEach { method ->
                        method.isAccessible = true
                        val qualityIndex = method.parameterTypes.lastIndex
                        XposedBridge.hookMethod(
                            method,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                                    maybeUpgradeUploadQuality(param, qualityIndex, methodName)
                                }
                            }
                        )
                    }
            }
        }
    }

    private fun installStartVideosWithSoundHooks() {
        val shouldForceSound = { state.feedVideosStartWithSound || state.storiesStartWithSound }
        fun hookBooleanFalse(className: String, methodName: String) {
            runSafe("Start-with-sound $className.$methodName") {
                val cls = Class.forName(className, false, appClassLoader)
                XposedBridge.hookAllMethods(
                    cls,
                    methodName,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam<*>) {
                            if (shouldForceSound()) param.result = false
                        }
                    }
                )
            }
        }

        listOf(
            "com.instagram.api.schemas.AudioMutingInfo" to "CLG",
            "com.instagram.api.schemas.AudioMutingInfo" to "Cvw",
            "com.instagram.api.schemas.ImmutablePandoAudioMutingInfo" to "CLG",
            "com.instagram.api.schemas.ImmutablePandoAudioMutingInfo" to "Cvw",
            "com.instagram.api.schemas.OriginalSoundData" to "Ctf",
            "com.instagram.api.schemas.ImmutablePandoOriginalSoundData" to "Ctf",
            "com.instagram.api.schemas.OriginalSoundConsumptionInfo" to "Dg4",
            "com.instagram.api.schemas.ImmutablePandoOriginalSoundConsumptionInfo" to "Dg4"
        ).forEach { (className, methodName) -> hookBooleanFalse(className, methodName) }

        runSafe("MediaPlayer start-with-sound") {
            XposedBridge.hookAllMethods(
                MediaPlayer::class.java,
                "setVolume",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (!shouldForceSound() || param.args.size < 2) return
                        param.args[0] = 1.0f
                        param.args[1] = 1.0f
                    }
                }
            )
        }
        runSafe("AudioTrack start-with-sound") {
            XposedBridge.hookAllMethods(
                AudioTrack::class.java,
                "setVolume",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (!shouldForceSound() || param.args.isEmpty()) return
                        param.args[0] = 1.0f
                    }
                }
            )
        }
        listOf(
            "androidx.media3.exoplayer.ExoPlayerImpl",
            "com.google.android.exoplayer2.ExoPlayerImpl",
            "com.google.android.exoplayer2.SimpleExoPlayer"
        ).forEach { className ->
            runSafe("ExoPlayer start-with-sound $className") {
                val cls = Class.forName(className, false, appClassLoader)
                XposedBridge.hookAllMethods(
                    cls,
                    "setVolume",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam<*>) {
                            if (!shouldForceSound() || param.args.isEmpty()) return
                            param.args[0] = 1.0f
                        }
                    }
                )
            }
        }
    }

    private fun installOkHttpHooks() {
        runSafe("OkHttp hooks") {
            val clientClass = Class.forName("okhttp3.OkHttpClient", false, appClassLoader)
            XposedBridge.hookAllMethods(
                clientClass,
                "newCall",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val request = param.args.firstOrNull() ?: return
                        val url = requestUrl(request) ?: return
                        rememberMediaUrl(url)
                        runCatching { InstagramActivityHistoryHooks.recordNetworkRequest(androidContext, URI(url)) }
                        val rewritten = rewriteOrBlockNetworkUrl(url)
                        if (rewritten != null && rewritten != url) {
                            buildRequestWithUrl(request, rewritten)?.let { param.args[0] = it }
                            logInfo("Rewrote Instagram request: $url -> $rewritten")
                        }
                    }
                }
            )
        }
    }

    private fun installTigonHooks() {
        runSafe("Tigon network interceptor") {
            val tigonClass = Class.forName("com.instagram.api.tigon.TigonServiceLayer", false, appClassLoader)
            val startRequest = tigonClass.declaredMethods.firstOrNull {
                it.name == "startRequest" && it.parameterTypes.size == 3
            } ?: return@runSafe
            val requestClass = startRequest.parameterTypes.firstOrNull() ?: return@runSafe
            val uriField = requestClass.declaredFields.firstOrNull { it.type == URI::class.java } ?: return@runSafe
            uriField.isAccessible = true

            XposedBridge.hookMethod(
                startRequest,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val requestObj = param.args.firstOrNull() ?: return
                        val uri = runCatching { uriField.get(requestObj) as? URI }.getOrNull() ?: return
                        rememberMediaUrl(uri.toString())
                        InstagramActivityHistoryHooks.recordNetworkRequest(androidContext, uri)
                        if (state.showFollowerToast) handleFollowStatusRequest(uri, param.args)

                        if (maybeConfirmOrBlockRefresh(param.thisObject, startRequest, param.args, requestObj, uriField.name, uri)) {
                            param.result = defaultResult(startRequest.returnType)
                            return
                        }

                        val replacement = rewriteTigonUri(uri, param.thisObject, startRequest, param.args, requestObj, uriField)
                        if (replacement != null && replacement != uri) {
                            runCatching { uriField.set(requestObj, replacement) }
                            logInfo("Rewrote Instagram Tigon request: ${uri.path} -> ${replacement.path}")
                        }
                    }
                }
            )
            logInfo("Installed Instagram Tigon interceptor on ${startRequest.declaringClass.name}.${startRequest.name}")
        }
    }

    private fun installDexKitHooks() {
        Thread {
            runSafe("DexKit Instagram hooks") {
                installDevOptionsStableHooks()
                installBottomSheetNavigatorHook()
                InstagramActivityHistoryHooks.installDexKitHooks(dexBridge, appClassLoader)
                installCopyBioModelHooks()
                installCommentCopyLongPressHooks()
                installSponsoredModelHooks()
                installHideSuggestedFeedItemsHook()
                installGhostDmSeenHook()
                installGhostStorySeenHook()
                installGhostReplayLimitHooks()
                installGhostPermanentViewHook()
                installGhostEphemeralKeepHooks()
                installKeepUnsentMessagesHooks()
                installStoryOverflowHooks()
                installGhostTypingHook()
                installGhostViewOnceHook()
                installGhostVoiceSeenHooks()
                installBuildExpiredPopupHook()
                installVideoAutoPlayDexHook()
                installGhostScreenshotDetectionHook()
                installDoubleTapLikeDexHooks()
                installStoryFlippingDexHook()
                hookDexVoidOrFalse("NotesLocation", listOf("location_note_create_info", "longitude")) { state.enableNotesLocationSpoof }
                installNotesLocationFallbackHook()
                installPostVideoUrlCaptureHook()
                installPostDownloadMenuHook()
                installReelDownloadMenuHook()
                installDirectMessageContextMenuHook()
                installStoryMentionHook()
                installNavigationNativeTabFactoryHook()
                installStoryRingTrayConstructorHook()
                installConfirmRefreshDexListenerHooks()
            }
        }.apply {
            name = "PurrfectInstaDexKit"
            isDaemon = true
            start()
        }
    }

    private fun installBottomSheetNavigatorHook() {
        if (InstagramDexKitCache.isCacheValid()) {
            InstagramDexKitCache.loadMethod("BottomSheet", appClassLoader)?.let { cached ->
                val signature = "${methodKey(cached)}:BottomSheet_cached"
                if (hookedDexMethods.add(signature)) {
                    XposedBridge.hookMethod(cached, object : XC_MethodHook() {})
                    logInfo("DexKit cached hook installed: BottomSheet -> $signature")
                    return
                }
            }
        }
        val method = dexBridge.findMethodsUsingStrings("BottomSheetConstants")
            .firstOrNull { candidate ->
                candidate.declaringClass.name == "com.instagram.mainactivity.InstagramMainActivity" &&
                    !Modifier.isStatic(candidate.modifiers) &&
                    Modifier.isFinal(candidate.modifiers) &&
                    candidate.returnType != Void.TYPE &&
                    candidate.parameterTypes.isEmpty()
            } ?: return
        val signature = "${methodKey(method)}:BottomSheet"
        if (!hookedDexMethods.add(signature)) return
        runCatching {
            method.isAccessible = true
            XposedBridge.hookMethod(method, object : XC_MethodHook() {})
            InstagramDexKitCache.saveMethod("BottomSheet", method)
            logInfo("DexKit hook installed: BottomSheet -> $signature")
        }.onFailure { logError("Failed DexKit hook BottomSheet $signature", it) }
    }

    private fun installPostVideoUrlCaptureHook() {
        initStoryVideoVersionReflection()
        val videoVersionClass = storyVideoVersionIntfClass ?: return
        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam<*>) {
                if (!state.enablePostDownload) return
                val url = param.result as? String ?: return
                if (looksLikeMediaUrl(url) && !looksLikeProfileImageUrl(url)) rememberMediaUrl(url)
            }
        }
        if (InstagramDexKitCache.isCacheValid()) {
            InstagramDexKitCache.loadMethods("VideoUrlCapture", appClassLoader)
                ?.takeIf { it.isNotEmpty() }
                ?.let { cached ->
                    cached.forEach { method ->
                        val signature = "${methodKey(method)}:VideoUrlCapture_cached"
                        if (hookedDexMethods.add(signature)) XposedBridge.hookMethod(method, hook)
                    }
                    logInfo("DexKit cached hook installed: VideoUrlCapture methods=${cached.size}")
                    resolveUsernameGetter()
                    return
                }
        }
        val hooked = ArrayList<Method>()
        dexBridge.findMethodsByName("getUrl", paramCount = 0)
            .filter { method ->
                method.returnType == String::class.java &&
                    videoVersionClass.isAssignableFrom(method.declaringClass)
            }
            .forEach { method ->
                val signature = "${methodKey(method)}:VideoUrlCapture"
                if (!hookedDexMethods.add(signature)) return@forEach
            runCatching {
                method.isAccessible = true
                    XposedBridge.hookMethod(method, hook)
                    hooked += method
                    logInfo("DexKit hook installed: VideoUrlCapture -> $signature")
                }.onFailure { logError("Failed DexKit hook VideoUrlCapture $signature", it) }
            }
        if (hooked.isNotEmpty()) InstagramDexKitCache.saveMethods("VideoUrlCapture", hooked)
        resolveUsernameGetter()
    }

    private fun installVideoAutoPlayDexHook() {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam<*>) {
                if (state.disableVideoAutoPlay) param.result = true
            }
        }
        if (InstagramDexKitCache.isCacheValid()) {
            InstagramDexKitCache.loadMethod("AutoPlayDisable", appClassLoader)?.let { cached ->
                val signature = "${methodKey(cached)}:VideoAutoPlay_cached"
                if (hookedDexMethods.add(signature)) {
                    XposedBridge.hookMethod(cached, hook)
                    logInfo("DexKit cached hook installed: AutoPlayDisable -> $signature")
                    return
                }
            }
        }
        val methods = dexBridge.findMethodsUsingStrings("ig_disable_video_autoplay")
            .filter { method ->
                (method.returnType == java.lang.Boolean.TYPE || method.returnType == java.lang.Boolean::class.java) &&
                    method.parameterTypes.size == 1
            }
        methods.forEach { method ->
            val signature = "${methodKey(method)}:VideoAutoPlay"
            if (!hookedDexMethods.add(signature)) return@forEach
            runCatching {
                method.isAccessible = true
                XposedBridge.hookMethod(method, hook)
                InstagramDexKitCache.saveMethod("AutoPlayDisable", method)
                logInfo("DexKit hook installed: VideoAutoPlay -> $signature")
            }.onFailure { logError("Failed DexKit hook VideoAutoPlay $signature", it) }
        }
    }

    private fun installDoubleTapLikeDexHooks() {
        var cachedFeedHooked = false
        var cachedReelsHooked = false
        if (InstagramDexKitCache.isCacheValid()) {
            cachedFeedHooked = InstagramDexKitCache.loadMethod("DoubleTapLike", appClassLoader)?.let { method ->
                hookDoubleTapMethods("DoubleTapLike", listOf(method)) { stackLooksLikeDoubleTapCallback() } > 0
            } == true
            val cachedLegacyReelsHooked = InstagramDexKitCache.loadMethod("DoubleTapLikeReels", appClassLoader)?.let { method ->
                hookDoubleTapMethods("DoubleTapLikeReels", listOf(method)) { stackLooksLikeDoubleTapCallback() } > 0
            } == true
            val cachedGestureReelsHooked = InstagramDexKitCache.loadMethods("DoubleTapLikeReelsGestures", appClassLoader)?.let { methods ->
                hookDoubleTapMethods("DoubleTapLikeReelsGestures", methods) { param ->
                    looksLikeClipsDoubleTapGesture(param.thisObject)
                } > 0
            } == true
            cachedReelsHooked = cachedLegacyReelsHooked || cachedGestureReelsHooked
            if (cachedFeedHooked && cachedGestureReelsHooked) {
                logInfo("DexKit cached hook installed: DoubleTapLike")
                return
            }
        }

        if (!cachedFeedHooked) {
            val feedMethods = dexBridge.findMethodsUsingStrings("double_tap_on_liked", "used_double_tap")
            if (hookDoubleTapMethods("DoubleTapLike", feedMethods) { stackLooksLikeDoubleTapCallback() } > 0) {
                feedMethods.firstOrNull()?.let { InstagramDexKitCache.saveMethod("DoubleTapLike", it) }
                cachedFeedHooked = true
            }
        }

        val reelsGestureMethods = dexBridge.findMethodsByName("onDoubleTap", returnType = "boolean", paramCount = 1)
            .filter { method ->
                method.parameterTypes.singleOrNull()?.name == MotionEvent::class.java.name &&
                    isLikelyClipsGestureClass(method.declaringClass)
            }
        if (hookDoubleTapMethods("DoubleTapLikeReelsGestures", reelsGestureMethods) { param ->
                looksLikeClipsDoubleTapGesture(param.thisObject)
            } > 0
        ) {
            InstagramDexKitCache.saveMethods("DoubleTapLikeReelsGestures", reelsGestureMethods)
            cachedReelsHooked = true
        }

        val reelsLegacyMethods = dexBridge.findMethodsUsingStrings("clips_doubletap", "LIKE_FIRED")
        if (hookDoubleTapMethods("DoubleTapLikeReels", reelsLegacyMethods) {
            stackLooksLikeDoubleTapCallback()
            } > 0
        ) {
            reelsLegacyMethods.firstOrNull()?.let { InstagramDexKitCache.saveMethod("DoubleTapLikeReels", it) }
            cachedReelsHooked = true
        }

        if (!cachedFeedHooked) logInfo("DoubleTapLike feed method not found")
        if (!cachedReelsHooked) logInfo("DoubleTapLike reels entry not found")
    }

    private fun hookDoubleTapMethods(
        name: String,
        methods: List<Method>,
        shouldBlock: (XC_MethodHook.MethodHookParam<*>) -> Boolean
    ): Int {
        var hooked = 0
        methods.forEach { method ->
            val signature = "${methodKey(method)}:$name"
            if (!hookedDexMethods.add(signature)) return@forEach
            runCatching {
                method.isAccessible = true
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam<*>) {
                            if (!state.disableDoubleTapLike || !shouldBlock(param)) return
                            blockDoubleTapLike(param, method)
                        }
                    }
                )
                hooked++
                logInfo("DexKit hook installed: $name -> $signature")
            }.onFailure { logError("Failed DexKit hook $name $signature", it) }
        }
        return hooked
    }

    private fun blockDoubleTapLike(param: XC_MethodHook.MethodHookParam<*>, method: Method) {
        param.result = if (method.returnType == java.lang.Boolean.TYPE || method.returnType == java.lang.Boolean::class.java) {
            true
        } else {
            null
        }
    }

    private fun stackLooksLikeDoubleTapCallback(): Boolean {
        return Thread.currentThread().stackTrace.any { frame ->
            frame.methodName == "onDoubleTap" || frame.methodName == "onDoubleTapEvent"
        }
    }

    private fun isLikelyClipsGestureClass(clazz: Class<*>?): Boolean {
        if (clazz == null || !GestureDetector.SimpleOnGestureListener::class.java.isAssignableFrom(clazz)) return false
        var score = 0
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            current.declaredFields.forEach { field ->
                val typeName = field.type.name
                if (typeName == "com.instagram.clips.intf.ClipsViewerConfig") score += 3
                if (typeName == "com.instagram.common.session.UserSession") score += 1
                if (typeName == "android.view.GestureDetector") score += 1
                if (typeName == "android.view.ScaleGestureDetector") score += 1
                if (typeName.contains("ClipsViewer")) score += 2
            }
            current = current.superclass
        }
        return score >= 4
    }

    private fun looksLikeClipsDoubleTapGesture(target: Any?): Boolean {
        target ?: return false
        if (isLikelyClipsGestureClass(target.javaClass)) return true
        var current: Class<*>? = target.javaClass
        while (current != null && current != Any::class.java) {
            current.declaredFields.forEach { field ->
                runCatching {
                    if (!View::class.java.isAssignableFrom(field.type)) return@forEach
                    field.isAccessible = true
                    val value = field.get(target) as? View ?: return@forEach
                    if (looksLikeClipsView(value)) return true
                }
            }
            current = current.superclass
        }
        return false
    }

    private fun looksLikeClipsView(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            val entryName = resourceEntryName(current)
            if (entryName != null && (
                    entryName.contains("clips_video_container") ||
                        entryName.contains("clips_viewer_video_layout") ||
                        entryName.contains("sponsored_clips_showreel_view")
                    )
            ) {
                return true
            }
            current = current.parent as? View
        }
        return false
    }

    private fun installStoryFlippingDexHook() {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam<*>) {
                if (state.disableStoryFlipping) param.result = null
            }
        }
        if (InstagramDexKitCache.isCacheValid()) {
            InstagramDexKitCache.loadMethod("StoryFlipping", appClassLoader)?.let { cached ->
                val signature = "${methodKey(cached)}:StoryFlipping_cached"
                if (hookedDexMethods.add(signature)) {
                    XposedBridge.hookMethod(cached, hook)
                    logInfo("DexKit cached hook installed: StoryFlipping -> $signature")
                    return
                }
            }
        }
        val methods = dexBridge.findMethodsInClassUsingStrings(
            "instagram.features.stories.fragment.ReelViewerFragment",
            "void",
            listOf("java.lang.Object"),
            "userSession"
        )
        methods.firstOrNull()?.let { method ->
            val signature = "${methodKey(method)}:StoryFlipping"
            if (!hookedDexMethods.add(signature)) return
            runCatching {
                method.isAccessible = true
                XposedBridge.hookMethod(method, hook)
                InstagramDexKitCache.saveMethod("StoryFlipping", method)
                logInfo("DexKit hook installed: StoryFlipping -> $signature")
            }.onFailure { logError("Failed DexKit hook StoryFlipping $signature", it) }
        }
    }

    private fun installBuildExpiredPopupHook() {
        val noOpHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam<*>) {
                if (state.removeBuildExpiredPopup) param.result = null
            }
        }
        val falseHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam<*>) {
                if (state.removeBuildExpiredPopup) param.result = false
            }
        }

        var hookedMain = false
        if (InstagramDexKitCache.isCacheValid()) {
            InstagramDexKitCache.loadMethod("BuildExpiredShow", appClassLoader)?.let { method ->
                val signature = "${methodKey(method)}:build_expired_show_cached"
                if (hookedDexMethods.add(signature)) {
                    XposedBridge.hookMethod(method, noOpHook)
                    hookedMain = true
                    logInfo("DexKit cached hook installed: BuildExpiredShow -> $signature")
                }
            }
            InstagramDexKitCache.loadMethod("BuildExpiredCheck", appClassLoader)?.let { method ->
                val signature = "${methodKey(method)}:build_expired_snooze_cached"
                if (hookedDexMethods.add(signature)) {
                    XposedBridge.hookMethod(method, falseHook)
                    logInfo("DexKit cached hook installed: BuildExpiredSnooze -> $signature")
                }
            }
            if (hookedMain) return
        }

        dexBridge.findMethodsUsingStrings("lockout_active")
            .firstOrNull { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.firstOrNull()?.name.orEmpty().contains("FragmentActivity")
            }
            ?.let { method ->
                val signature = "${methodKey(method)}:build_expired_show"
                if (hookedDexMethods.add(signature)) {
                    method.isAccessible = true
                    XposedBridge.hookMethod(method, noOpHook)
                    InstagramDexKitCache.saveMethod("BuildExpiredShow", method)
                    logInfo("DexKit hook installed: BuildExpiredShow -> $signature")
                }
            }

        dexBridge.findMethodsUsingStrings("snooze_expiration_lockout_manager")
            .firstOrNull { it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java }
            ?.let { method ->
                val signature = "${methodKey(method)}:build_expired_snooze"
                if (hookedDexMethods.add(signature)) {
                    method.isAccessible = true
                    XposedBridge.hookMethod(method, falseHook)
                    InstagramDexKitCache.saveMethod("BuildExpiredCheck", method)
                    logInfo("DexKit hook installed: BuildExpiredSnooze -> $signature")
                }
            }
    }

    private fun hookDexVoidOrFalse(name: String, strings: List<String>, enabled: () -> Boolean) {
        val methods = dexBridge.findMethodsUsingStrings(*strings.toTypedArray())
        methods.forEach { method ->
            val signature = "${method.declaringClass.name}.${method.name}:${method.parameterTypes.size}"
            if (!hookedDexMethods.add(signature)) return@forEach
            runCatching {
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam<*>) {
                            if (!enabled()) return
                            if (name == "NotesLocation") {
                                applyNotesLocationSpoof(param)
                                return
                            }
                            param.result = defaultResult(method.returnType)
                        }
                    }
                )
                logInfo("DexKit hook installed: $name -> $signature")
            }.onFailure { logError("Failed DexKit hook $name $signature", it) }
        }
    }

    private fun hookDexBooleanResult(name: String, strings: List<String>, result: Boolean = true, enabled: () -> Boolean) {
        val methods = dexBridge.findMethodsUsingStrings(*strings.toTypedArray())
        methods.forEach { method ->
            if (method.returnType != java.lang.Boolean.TYPE && method.returnType != java.lang.Boolean::class.java) return@forEach
            val signature = "${method.declaringClass.name}.${method.name}:${method.parameterTypes.size}"
            if (!hookedDexMethods.add(signature)) return@forEach
            runCatching {
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam<*>) {
                            if (enabled()) param.result = result
                        }
                    }
                )
                logInfo("DexKit boolean hook installed: $name -> $signature")
            }.onFailure { logError("Failed DexKit boolean hook $name $signature", it) }
        }
    }

    private fun installHideSuggestedFeedItemsHook() {
        runSafe("Hide suggested feed parser") {
            val cachedClass = if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadString("FeedItemParserClass")
            } else {
                null
            }
            val parserClass = cachedClass?.let { className ->
                runCatching { Class.forName(className, false, appClassLoader) }.getOrNull()
            } ?: run {
                val method = listOf(
                arrayOf("clips_netego", "suggested_users", "Unknown FeedItem Type"),
                arrayOf("clips_netego", "media_or_ad"),
                arrayOf("clips_netego", "stories_netego", "bloks_netego")
                ).firstNotNullOfOrNull { markers ->
                    dexBridge.findMethodsUsingStrings(*markers).firstOrNull()
                } ?: return@runSafe
                InstagramDexKitCache.saveString("FeedItemParserClass", method.declaringClass.name)
                method.declaringClass
            }
            var hooked = 0
            parserClass.declaredMethods
                .filter { it.isBridge }
                .forEach { bridgeMethod ->
                    val signature = "${bridgeMethod.declaringClass.name}.${bridgeMethod.name}:hide_suggested"
                    if (!hookedDexMethods.add(signature)) return@forEach
                    bridgeMethod.isAccessible = true
                    XposedBridge.hookMethod(
                        bridgeMethod,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam<*>) {
                                if (!state.hideSuggestionsInFeed) return
                                val result = param.result ?: return
                                if (resultHasInstagramMedia(result)) return
                                param.result = null
                            }
                        }
                    )
                    hooked++
                }
            logInfo("Installed hide-suggested feed parser hooks=$hooked on ${parserClass.name}")
        }
    }

    private fun resultHasInstagramMedia(result: Any): Boolean {
        var cls: Class<*>? = result.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                if (field.type.name == "com.instagram.feed.media.Media") {
                    val value = runCatching {
                        field.isAccessible = true
                        field.get(result)
                    }.getOrNull()
                    if (value != null) return true
                }
            }
            cls = cls.superclass
        }
        return false
    }

    private fun installGhostTypingHook() {
        runSafe("Ghost typing hook") {
            val hook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    if (state.isGhostTyping) param.result = null
                }
            }
            if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadMethod("GhostTyping", appClassLoader)?.let { cached ->
                    val signature = "${methodKey(cached)}:ghost_typing_cached"
                    if (hookedDexMethods.add(signature)) {
                        XposedBridge.hookMethod(cached, hook)
                        logInfo("DexKit cached hook installed: GhostTyping -> $signature")
                    }
                    return@runSafe
                }
            }
            val method = dexBridge.findMethodsUsingStrings("is_typing_indicator_enabled")
                .firstOrNull { candidate ->
                    Modifier.isStatic(candidate.modifiers) &&
                        Modifier.isFinal(candidate.modifiers) &&
                        candidate.returnType == Void.TYPE &&
                        candidate.parameterTypes.size == 2 &&
                        candidate.parameterTypes[1] == java.lang.Boolean.TYPE
                }
                ?: return@runSafe
            val signature = "${methodKey(method)}:ghost_typing"
            if (!hookedDexMethods.add(signature)) return@runSafe
            method.isAccessible = true
            XposedBridge.hookMethod(method, hook)
            InstagramDexKitCache.saveMethod("GhostTyping", method)
            logInfo("Installed Ghost typing hook on ${method.declaringClass.name}.${method.name}")
        }
    }

    private fun installGhostViewOnceHook() {
        runSafe("Ghost view-once hook") {
            val hook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    if (!state.isGhostViewOnce) return
                    val marker = param.args.getOrNull(2) ?: return
                    if (objectExposesViewOnceSeenMarker(marker)) param.result = null
                }
            }
            if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadMethod("GhostViewOnce", appClassLoader)?.let { cached ->
                    val signature = "${methodKey(cached)}:ghost_view_once_cached"
                    if (hookedDexMethods.add(signature)) {
                        XposedBridge.hookMethod(cached, hook)
                        logInfo("DexKit cached hook installed: GhostViewOnce -> $signature")
                    }
                    return@runSafe
                }
            }
            val method = dexBridge.findMethodsUsingStrings("visual_item_seen")
                .firstOrNull { it.returnType == Void.TYPE && it.parameterTypes.size == 3 }
                ?: return@runSafe
            val signature = "${methodKey(method)}:ghost_view_once"
            if (!hookedDexMethods.add(signature)) return@runSafe
            method.isAccessible = true
            XposedBridge.hookMethod(method, hook)
            InstagramDexKitCache.saveMethod("GhostViewOnce", method)
            logInfo("Installed Ghost view-once hook on ${method.declaringClass.name}.${method.name}")
        }
    }

    private fun objectExposesViewOnceSeenMarker(target: Any): Boolean {
        target.javaClass.declaredMethods.forEach { method ->
            if (method.parameterTypes.isNotEmpty() || method.returnType != String::class.java) return@forEach
            val value = runCatching {
                method.isAccessible = true
                method.invoke(target) as? String
            }.getOrNull()
            if (value != null && (
                    value.contains("visual_item_seen") ||
                        value.contains("send_visual_item_seen_marker")
                    )
            ) {
                return true
            }
        }
        return false
    }

    private fun installGhostScreenshotDetectionHook() {
        runSafe("Ghost screenshot detection hook") {
            val hook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    if (state.isGhostScreenshot) param.result = null
                }
            }
            if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadMethod("GhostScreenshot", appClassLoader)?.let { cached ->
                    val signature = "${methodKey(cached)}:ghost_screenshot_cached"
                    if (hookedDexMethods.add(signature)) {
                        XposedBridge.hookMethod(cached, hook)
                        logInfo("DexKit cached hook installed: GhostScreenshot -> $signature")
                    }
                    return@runSafe
                }
            }
            dexBridge.findClassNamesUsingStrings("ScreenshotNotificationManager").forEach { className ->
                val cls = runCatching { Class.forName(className, false, appClassLoader) }.getOrNull() ?: return@forEach
                val method = cls.declaredMethods.firstOrNull {
                    it.returnType == Void.TYPE &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == java.lang.Long.TYPE
                } ?: return@forEach
                val signature = "${methodKey(method)}:ghost_screenshot"
                if (!hookedDexMethods.add(signature)) return@forEach
                method.isAccessible = true
                XposedBridge.hookMethod(method, hook)
                InstagramDexKitCache.saveMethod("GhostScreenshot", method)
                logInfo("Installed Ghost screenshot hook on ${method.declaringClass.name}.${method.name}")
                return@runSafe
            }
        }
    }

    private fun installGhostVoiceSeenHooks() {
        runSafe("Ghost voice-message seen hooks") {
            hookKnownVoiceSeenHandler()
            hookKnownVoiceRequestFactory()
            hookKnownVoiceRequestSchedulers()

            dexBridge.findMethodsUsingStrings("voice_item_seen")
                .filter { it.returnType == Void.TYPE && it.parameterTypes.size == 3 }
                .forEach { method -> hookVoiceBlockingMethod(method, "voice_seen_shape") }

            dexBridge.findMethodsUsingStrings("direct_v2/visual_threads/%s/item_seen/", "voice_item_seen")
                .forEach { method -> hookVoiceTrackingMethod(method, "voice_seen_factory_strings") }

            dexBridge.findMethodsUsingStrings("voice_item_seen")
                .filter { it.returnType == Void.TYPE }
                .forEach { method -> hookVoiceBlockingMethod(method, "voice_seen_string") }

            dexBridge.findMethodsUsingStrings("send_voice_item_seen_marker")
                .filter { it.returnType == Void.TYPE }
                .forEach { method -> hookVoiceBlockingMethod(method, "voice_seen_marker") }
        }
    }

    private fun hookKnownVoiceSeenHandler() {
        listOf("X.04yz", "X.4yz", "p000X.C131534yz", "X.C131534yz", "C131534yz").forEach { className ->
            val cls = runCatching { Class.forName(className, false, appClassLoader) }.getOrNull() ?: return@forEach
            val hooked = cls.declaredMethods.count { method ->
                method.name == "G5C" && hookVoiceBlockingMethod(method, "known_voice_handler")
            }
            if (hooked > 0) return
        }
    }

    private fun hookKnownVoiceRequestFactory() {
        listOf("p000X.JD5", "X.JD5", "JD5").forEach { className ->
            val cls = runCatching { Class.forName(className, false, appClassLoader) }.getOrNull() ?: return@forEach
            val hooked = cls.declaredMethods.count { method ->
                val params = method.parameterTypes
                params.size >= 2 &&
                    params[1] == String::class.java &&
                    method.returnType != Void.TYPE &&
                    hookVoiceTrackingMethod(method, "known_voice_factory")
            }
            if (hooked > 0) return
        }
    }

    private fun hookKnownVoiceRequestSchedulers() {
        listOf("X.01qG", "X.1qG", "p000X.C49681qG", "X.C49681qG", "C49681qG").forEach { className ->
            val cls = runCatching { Class.forName(className, false, appClassLoader) }.getOrNull() ?: return@forEach
            val hooked = cls.declaredMethods.count { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.isNotEmpty() &&
                    hookVoiceSchedulerMethod(method, "known_voice_scheduler")
            }
            if (hooked > 0) return
        }
    }

    private fun hookVoiceBlockingMethod(method: Method, source: String): Boolean {
        val signature = "${methodKey(method)}:voice_seen_block"
        if (!hookedDexMethods.add(signature)) return false
        return runCatching {
            method.isAccessible = true
            XposedBridge.hookMethod(
                method,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (state.hideVoiceMessageSeen) param.result = null
                    }
                }
            )
            true
        }.onFailure { logError("Failed voice seen blocking hook $signature", it) }.getOrDefault(false)
    }

    private fun hookVoiceTrackingMethod(method: Method, source: String): Boolean {
        val signature = "${methodKey(method)}:voice_seen_track"
        if (!hookedDexMethods.add(signature)) return false
        return runCatching {
            method.isAccessible = true
            XposedBridge.hookMethod(
                method,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (!state.hideVoiceMessageSeen || param.args.size < 2) return
                        if (param.args[1] != "voice_item_seen") return
                        param.result?.let { voiceSeenRequests += it }
                    }
                }
            )
            true
        }.onFailure { logError("Failed voice seen tracking hook $signature", it) }.getOrDefault(false)
    }

    private fun hookVoiceSchedulerMethod(method: Method, source: String): Boolean {
        val signature = "${methodKey(method)}:voice_seen_schedule"
        if (!hookedDexMethods.add(signature)) return false
        return runCatching {
            method.isAccessible = true
            XposedBridge.hookMethod(
                method,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (!state.hideVoiceMessageSeen || param.args.isEmpty()) return
                        val request = param.args[0] ?: return
                        if (voiceSeenRequests.remove(request)) param.result = null
                    }
                }
            )
            true
        }.onFailure { logError("Failed voice seen scheduler hook $signature", it) }.getOrDefault(false)
    }

    private fun installGhostDmSeenHook() {
        runSafe("Ghost DM seen hook") {
            val hook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    if (!state.isGhostSeen || allowManualDmSeen.get() == true) return
                    lastBlockedDmSeenMethodCall = BlockedSeenMethodCall(
                        param.method as Method,
                        param.thisObject,
                        param.args?.copyOf() ?: emptyArray(),
                        System.currentTimeMillis()
                    )
                    param.result = null
                }
            }
            if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadMethod("GhostSeen", appClassLoader)?.let { cached ->
                    val signature = "${methodKey(cached)}:ghost_dm_seen_cached"
                    if (hookedDexMethods.add(signature)) {
                        XposedBridge.hookMethod(cached, hook)
                        logInfo("DexKit cached hook installed: GhostSeen -> $signature")
                    }
                    return@runSafe
                }
            }
            var hooked = 0
            dexBridge.findMethodsUsingStrings("mark_thread_seen-")
                .filter { method ->
                    Modifier.isStatic(method.modifiers) &&
                        Modifier.isFinal(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.size >= 3
                }
                .forEach { method ->
                    val signature = "${method.declaringClass.name}.${method.name}:ghost_dm_seen"
                    if (!hookedDexMethods.add(signature)) return@forEach
                    method.isAccessible = true
                    XposedBridge.hookMethod(method, hook)
                    if (hooked == 0) InstagramDexKitCache.saveMethod("GhostSeen", method)
                    hooked++
                }
            logInfo("Installed Ghost DM seen method hooks=$hooked")
        }
    }

    private fun installGhostStorySeenHook() {
        runSafe("Ghost story seen hook") {
            val seenHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    if (!state.isGhostStory || allowManualStorySeen.get() == true) return
                    param.thisObject?.let { lastBlockedStorySeenOwner = WeakReference(it) }
                    param.result = null
                }
            }
            if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadMethod("GhostStorySeen", appClassLoader)?.let { cached ->
                    val signature = "${methodKey(cached)}:ghost_story_seen_cached"
                    if (hookedDexMethods.add(signature)) {
                        XposedBridge.hookMethod(cached, seenHook)
                        if (!storySeenMethods.contains(cached)) storySeenMethods += cached
                        logInfo("DexKit cached hook installed: GhostStorySeen -> $signature")
                    }
                }
            }
            var seenHooks = 0
            var builderHooks = 0
            dexBridge.findMethodsUsingStrings("media/seen/").forEach { method ->
                val signature = "${method.declaringClass.name}.${method.name}:${method.parameterTypes.size}:ghost_story_seen"
                if (!hookedDexMethods.add(signature)) return@forEach
                method.isAccessible = true
                if (method.returnType == Void.TYPE && method.parameterTypes.isEmpty()) {
                    XposedBridge.hookMethod(method, seenHook)
                    if (!storySeenMethods.contains(method)) storySeenMethods += method
                    if (seenHooks == 0) InstagramDexKitCache.saveMethod("GhostStorySeen", method)
                    seenHooks++
                } else {
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam<*>) {
                                if (!state.isGhostStory) return
                                param.thisObject?.let { lastSeenStoryBuilderOwner = WeakReference(it) }
                                lastSeenStoryBuilderArgs = param.args?.copyOf() ?: emptyArray()
                            }
                        }
                    )
                    if (!storySeenBuilderMethods.contains(method)) storySeenBuilderMethods += method
                    builderHooks++
                }
            }
            logInfo("Installed Ghost story seen hooks=$seenHooks builders=$builderHooks")
        }
    }

    private fun installGhostReplayLimitHooks() {
        runSafe("Unlimited replay hooks") {
            val updateHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    if (state.enableUnlimitedReplays) param.result = null
                }
            }
            val parseHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam<*>) {
                    if (!state.enableUnlimitedReplays) return
                    zeroReplayCountFields(param.thisObject)
                    param.result?.takeIf { it !== param.thisObject }?.let { zeroReplayCountFields(it) }
                }
            }
            val syncHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    if (state.enableUnlimitedReplays) param.result = null
                }
            }

            var updateHooks = 0
            if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadMethod("Replays_update", appClassLoader)?.let { method ->
                    val signature = "${methodKey(method)}:replay_update_cached"
                    if (hookedDexMethods.add(signature)) {
                        XposedBridge.hookMethod(method, updateHook)
                        updateHooks++
                    }
                }
            }
            dexBridge.findMethodsUsingStrings(
                "Entry should exist before function call",
                "Visual message is missing from thread entry"
            ).forEach { method ->
                if (method.returnType != Void.TYPE) return@forEach
                val signature = "${method.declaringClass.name}.${method.name}:replay_update"
                if (!hookedDexMethods.add(signature)) return@forEach
                method.isAccessible = true
                XposedBridge.hookMethod(method, updateHook)
                if (updateHooks == 0) InstagramDexKitCache.saveMethod("Replays_update", method)
                updateHooks++
            }

            var parseHooks = 0
            if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadMethods("Replays_parse", appClassLoader)?.forEach { method ->
                    val signature = "${methodKey(method)}:replay_parse_cached"
                    if (hookedDexMethods.add(signature)) {
                        XposedBridge.hookMethod(method, parseHook)
                        parseHooks++
                    }
                }
            }
            val parsedMethods = ArrayList<Method>()
            dexBridge.findMethodsUsingStrings("seen_count", "tap_models").forEach { method ->
                val signature = "${method.declaringClass.name}.${method.name}:replay_parse"
                if (!hookedDexMethods.add(signature)) return@forEach
                method.isAccessible = true
                XposedBridge.hookMethod(method, parseHook)
                parsedMethods += method
                parseHooks++
            }
            if (parsedMethods.isNotEmpty()) InstagramDexKitCache.saveMethods("Replays_parse", parsedMethods)

            var syncHooks = 0
            if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadMethod("Replays_sync", appClassLoader)?.let { method ->
                    val signature = "${methodKey(method)}:replay_sync_cached"
                    if (hookedDexMethods.add(signature)) {
                        XposedBridge.hookMethod(method, syncHook)
                        syncHooks++
                    }
                }
            }
            dexBridge.findMethodsByParamTypesWithWildcards(
                returnType = "void",
                paramTypeNames = listOf("com.instagram.common.session.UserSession", null, null)
            ).firstOrNull { Modifier.isSynchronized(it.modifiers) }?.let { method ->
                val signature = "${methodKey(method)}:replay_sync"
                if (hookedDexMethods.add(signature)) {
                    method.isAccessible = true
                    XposedBridge.hookMethod(method, syncHook)
                    InstagramDexKitCache.saveMethod("Replays_sync", method)
                    syncHooks++
                }
            }
            logInfo("Installed unlimited replay hooks update=$updateHooks parse=$parseHooks sync=$syncHooks")
        }
    }

    private fun zeroReplayCountFields(target: Any?) {
        if (target == null) return
        var cls: Class<*>? = target.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                if (field.type != java.lang.Integer.TYPE) return@forEach
                runCatching {
                    field.isAccessible = true
                    val value = field.getInt(target)
                    if (value in 1..10) field.setInt(target, 0)
                }
            }
            cls = cls.superclass
        }
    }

    private fun installGhostPermanentViewHook() {
        runSafe("Permanent view parser hook") {
            val hook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam<*>) {
                    if (!state.permanentViewMode) return
                    applyPermanentViewMode(param.result)
                }
            }
            if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadMethod("ViewOnceMedia", appClassLoader)?.let { cached ->
                    val signature = "${methodKey(cached)}:permanent_view_cached"
                    if (hookedDexMethods.add(signature)) {
                        XposedBridge.hookMethod(cached, hook)
                        logInfo("DexKit cached hook installed: ViewOnceMedia -> $signature")
                    }
                    return@runSafe
                }
            }
            val target = dexBridge.findMethodsUsingStrings("archived_media_timestamp", "view_mode")
                .filter { it.parameterTypes.size == 1 }
                .firstOrNull { it.returnType != Void.TYPE }
                ?: dexBridge.findMethodsUsingStrings("archived_media_timestamp", "view_mode")
                    .firstOrNull { it.parameterTypes.size == 1 }
                ?: return@runSafe
            val signature = "${target.declaringClass.name}.${target.name}:permanent_view"
            if (!hookedDexMethods.add(signature)) return@runSafe
            target.isAccessible = true
            XposedBridge.hookMethod(target, hook)
            InstagramDexKitCache.saveMethod("ViewOnceMedia", target)
            logInfo("Installed permanent-view parser hook on ${target.declaringClass.name}.${target.name}")
        }
    }

    private fun applyPermanentViewMode(result: Any?) {
        if (result == null) return
        var seenCount = 0
        var cls: Class<*>? = result.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                if (field.type == java.lang.Integer.TYPE) {
                    runCatching {
                        field.isAccessible = true
                        seenCount = field.getInt(result)
                    }
                }
            }
            cls = cls.superclass
        }

        cls = result.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                if (field.type != String::class.java) return@forEach
                runCatching {
                    field.isAccessible = true
                    when (field.get(result) as? String) {
                        "once" -> if (seenCount < 1) field.set(result, "permanent")
                        "replayable", "allow_replay" -> if (seenCount < 2) field.set(result, "permanent")
                    }
                }
            }
            cls = cls.superclass
        }
    }

    private fun installGhostEphemeralKeepHooks() {
        runSafe("Keep ephemeral hooks") {
            val blockHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    if (state.keepEphemeralMessages) param.result = null
                }
            }
            val expiryHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam<*>) {
                    if (!state.keepEphemeralMessages) return
                    clearExpiryTimestamp(param.thisObject)
                    param.result?.takeIf { it !== param.thisObject }?.let { clearExpiryTimestamp(it) }
                }
            }

            var vanishHooks = 0
            if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadMethod("Ephemeral_vanish", appClassLoader)?.let { method ->
                    val signature = "${methodKey(method)}:ephemeral_vanish_cached"
                    if (hookedDexMethods.add(signature)) {
                        XposedBridge.hookMethod(method, blockHook)
                        vanishHooks++
                    }
                }
            }
            dexBridge.findMethodsUsingStrings("igThreadIgid")
                .filter { method ->
                    method.returnType == Void.TYPE &&
                        method.parameterTypes.size == 2 &&
                        method.parameterTypes[0].name == "com.instagram.model.direct.DirectThreadKey" &&
                        method.parameterTypes[1] == java.lang.Boolean.TYPE
                }
                .forEach { method ->
                    val signature = "${method.declaringClass.name}.${method.name}:ephemeral_vanish"
                    if (!hookedDexMethods.add(signature)) return@forEach
                    method.isAccessible = true
                    XposedBridge.hookMethod(method, blockHook)
                    if (vanishHooks == 0) InstagramDexKitCache.saveMethod("Ephemeral_vanish", method)
                    vanishHooks++
            }

            var serverHooks = 0
            if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadMethod("Ephemeral_ping", appClassLoader)?.let { method ->
                    val signature = "${methodKey(method)}:ephemeral_ping_cached"
                    if (hookedDexMethods.add(signature)) {
                        XposedBridge.hookMethod(method, blockHook)
                        serverHooks++
                    }
                }
            }
            dexBridge.findMethodsUsingStrings("mark_ephemeral_item_ranges_viewed")
                .firstOrNull { it.returnType == Void.TYPE }
                ?.let { method ->
                    val signature = "${methodKey(method)}:ephemeral_ping"
                    if (hookedDexMethods.add(signature)) {
                        method.isAccessible = true
                        XposedBridge.hookMethod(method, blockHook)
                        InstagramDexKitCache.saveMethod("Ephemeral_ping", method)
                        serverHooks++
                    }
                }

            var expiryHooks = 0
            if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadMethods("Ephemeral_expiry", appClassLoader)?.forEach { method ->
                    val signature = "${methodKey(method)}:ephemeral_expiry_cached"
                    if (hookedDexMethods.add(signature)) {
                        XposedBridge.hookMethod(method, expiryHook)
                        expiryHooks++
                    }
                }
            }
            val expiryMethods = ArrayList<Method>()
            dexBridge.findMethodsUsingStrings("message_expiration_timestamp_ms").forEach { method ->
                val signature = "${method.declaringClass.name}.${method.name}:ephemeral_expiry"
                if (!hookedDexMethods.add(signature)) return@forEach
                method.isAccessible = true
                XposedBridge.hookMethod(method, expiryHook)
                expiryMethods += method
                expiryHooks++
            }
            if (expiryMethods.isNotEmpty()) InstagramDexKitCache.saveMethods("Ephemeral_expiry", expiryMethods)
            logInfo("Installed keep-ephemeral hooks vanish=$vanishHooks server=$serverHooks expiry=$expiryHooks")
        }
    }

    private fun clearExpiryTimestamp(target: Any?) {
        if (target == null) return
        val now = System.currentTimeMillis()
        val year2100 = 4_102_444_800_000L
        var cls: Class<*>? = target.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                if (field.type != java.lang.Long.TYPE) return@forEach
                runCatching {
                    field.isAccessible = true
                    val value = field.getLong(target)
                    if (value > now && value < year2100) field.setLong(target, 0L)
                }
            }
            cls = cls.superclass
        }
    }

    private fun installKeepUnsentMessagesHooks() {
        runSafe("Keep unsent messages hooks") {
            loadKeepUnsentDeletedIds(androidContext)
            fun hookKeepUnsentMethods(
                cacheKey: String,
                signatureSuffix: String,
                methods: List<Method>,
                hook: XC_MethodHook,
                afterHook: ((Method) -> Int)? = null,
                save: Boolean = true
            ): Int {
                var hooked = 0
                val saved = ArrayList<Method>()
                methods.forEach { method ->
                    val signature = "${methodKey(method)}:$signatureSuffix"
                    if (!hookedDexMethods.add(signature)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        XposedBridge.hookMethod(method, hook)
                        saved += method
                        hooked++
                        afterHook?.let { hooked += it(method) }
                    }.onFailure { logError("Failed keep-unsent hook $signature", it) }
                }
                if (save && saved.isNotEmpty()) InstagramDexKitCache.saveMethods(cacheKey, saved)
                return hooked
            }

            fun hookCachedKeepUnsentMethods(
                cacheKey: String,
                signatureSuffix: String,
                hook: XC_MethodHook,
                afterHook: ((Method) -> Int)? = null,
                validator: (Method) -> Boolean = { true }
            ): Int {
                if (!InstagramDexKitCache.isCacheValid()) return 0
                val cached = InstagramDexKitCache.loadMethods(cacheKey, appClassLoader).orEmpty()
                    .filter(validator)
                if (cached.isEmpty()) return 0
                val hooked = hookKeepUnsentMethods(cacheKey, signatureSuffix, cached, hook, afterHook, save = false)
                if (hooked > 0) logInfo("Installed cached keep-unsent hooks $cacheKey=$hooked")
                return hooked
            }

            fun isRenderMethod(method: Method): Boolean = method.parameterTypes.size == 1

            fun isProcessMethod(method: Method): Boolean =
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 4 &&
                    method.parameterTypes[1] == String::class.java &&
                    List::class.java.isAssignableFrom(method.parameterTypes[2]) &&
                    method.parameterTypes[3] == java.lang.Boolean.TYPE

            fun isRealtimeRemoveMethod(method: Method): Boolean = method.parameterTypes.size == 8

            fun isStoreDeleteMethod(method: Method): Boolean =
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 5 &&
                    method.parameterTypes[1] == java.lang.Integer::class.java &&
                    method.parameterTypes[2] == String::class.java &&
                    method.parameterTypes[3] == String::class.java &&
                    method.parameterTypes[4] == java.lang.Boolean.TYPE

            fun isLegacyPushMethod(method: Method): Boolean =
                method.returnType == Void.TYPE &&
                    method.parameterTypes.isNotEmpty() &&
                    !Modifier.isStatic(method.modifiers)

            val renderHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam<*>) {
                    if (!state.keepUnsentMessages) return
                    val result = param.result as? List<*> ?: return
                    val filtered = filterRemoveOperations(result)
                    if (filtered !== result) param.result = filtered
                }
            }
            var renderHooks = hookCachedKeepUnsentMethods(
                "KeepUnsent_render_results",
                "keep_unsent_render_cached",
                renderHook,
                validator = ::isRenderMethod
            )
            if (renderHooks == 0) {
                renderHooks = hookKeepUnsentMethods(
                    "KeepUnsent_render_results",
                    "keep_unsent_render",
                    dexBridge.findMethodsUsingStringsConstrained(
                        listOf("replace_message", "remove_message", "noop"),
                        paramCount = 1
                    ).filter(::isRenderMethod),
                    renderHook
                )
            }

            val processHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    if (!state.keepUnsentMessages) return
                    val operations = param.args?.getOrNull(2) as? List<*> ?: return
                    val deleteIds = collectRemoveOperationIds(operations)
                    if (deleteIds.isNotEmpty()) {
                        rememberKeepUnsentDeletedIds(deleteIds)
                        rememberScopedDeleteIds(param, deleteIds)
                    }
                    val filtered = filterRemoveOperations(operations)
                    if (filtered !== operations) param.args[2] = filtered
                }

                override fun afterHookedMethod(param: MethodHookParam<*>) {
                    restoreScopedDeleteIds(param)
                }
            }
            var processHooks = hookCachedKeepUnsentMethods(
                "KeepUnsent_process_ops",
                "keep_unsent_process_cached",
                processHook,
                validator = ::isProcessMethod
            )
            if (processHooks == 0) {
                processHooks = hookKeepUnsentMethods(
                    "KeepUnsent_process_ops",
                    "keep_unsent_process",
                    dexBridge.findMethodsUsingStringsConstrained(
                        listOf("process_start", "process_end"),
                        paramCount = 4,
                        returnType = "void"
                    ).filter(::isProcessMethod),
                    processHook
                )
            }

            val realtimeHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    if (!state.keepUnsentMessages) return
                    val deleteIds = collectRealtimeRemoveIds(param.args)
                    if (deleteIds.isNotEmpty()) {
                        rememberKeepUnsentDeletedIds(deleteIds)
                        rememberScopedDeleteIds(param, deleteIds)
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam<*>) {
                    restoreScopedDeleteIds(param)
                }
            }
            var realtimeHooks = hookCachedKeepUnsentMethods(
                "KeepUnsent_delta_remove",
                "keep_unsent_delta_remove_cached",
                realtimeHook,
                validator = ::isRealtimeRemoveMethod
            )
            if (realtimeHooks == 0) {
                realtimeHooks = hookKeepUnsentMethods(
                    "KeepUnsent_delta_remove",
                    "keep_unsent_delta_remove",
                    dexBridge.findMethodsUsingStringsConstrained(
                        listOf(
                            "NewMessageDeltaProcessor",
                            "Invalid DirectMessage format",
                            "persist_message_success",
                            "remove"
                        ),
                        paramCount = 8
                    ).filter(::isRealtimeRemoveMethod),
                    realtimeHook
                )
            }

            val legacyHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    if (state.keepUnsentMessages && containsLegacyDeleteItemPayload(param.args)) {
                        rememberKeepUnsentDeletedIds(collectUsefulIdsDeep(param.args))
                        param.result = null
                    }
                }
            }
            var legacyHooks = hookCachedKeepUnsentMethods(
                "KeepUnsent_legacy_push",
                "keep_unsent_legacy_cached",
                legacyHook,
                validator = ::isLegacyPushMethod
            )
            if (legacyHooks == 0) {
                val legacyMethods = dexBridge.findMethodsUsingStrings(
                    "direct_v2_delete_item",
                    "direct_v2_edit_message",
                    "direct_v2_reply_reminder"
                ).map { it.declaringClass }
                    .toSet()
                    .flatMap { clazz ->
                        clazz.declaredMethods
                            .filter(::isLegacyPushMethod)
                    }
                legacyHooks = hookKeepUnsentMethods("KeepUnsent_legacy_push", "keep_unsent_legacy", legacyMethods, legacyHook)
            }

            val storeHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    if (!state.keepUnsentMessages) return
                    if (isScopedRemoteDelete(param.args) || isOneIdServerDelete(param.args)) {
                        rememberKeepUnsentDeletedIds(collectUsefulIdsDeep(param.args))
                        param.result = null
                    }
                }
            }
            var storeHooks = hookCachedKeepUnsentMethods(
                "KeepUnsent_store_delete",
                "keep_unsent_store_delete_cached",
                storeHook,
                afterHook = { method -> hookKeepUnsentStoreDeleteWrappers(method.declaringClass) },
                validator = ::isStoreDeleteMethod
            )
            if (storeHooks == 0) {
                storeHooks = hookKeepUnsentMethods(
                    "KeepUnsent_store_delete",
                    "keep_unsent_store_delete",
                    dexBridge.findMethodsUsingStringsConstrained(
                        listOf("Client context should not be null if messageId is null."),
                        paramCount = 5,
                        returnType = "void"
                    ).filter(::isStoreDeleteMethod),
                    storeHook,
                    afterHook = { method -> hookKeepUnsentStoreDeleteWrappers(method.declaringClass) }
                )
            }

            val rowHooks = installKeepUnsentDeletedIndicatorHooks()

            logInfo("Installed keep-unsent hooks render=$renderHooks process=$processHooks realtime=$realtimeHooks legacy=$legacyHooks store=$storeHooks rows=$rowHooks")
        }
    }

    private fun filterRemoveOperations(operations: List<*>): List<*> {
        var changed = false
        val filtered = ArrayList<Any?>()
        operations.forEach { operation ->
            if (isLikelyRemoveMessageOperation(operation)) {
                rememberKeepUnsentDeletedIds(collectStringFields(operation))
                changed = true
            } else {
                filtered += operation
            }
        }
        return if (changed) filtered else operations
    }

    private fun isLikelyRemoveMessageOperation(operation: Any?): Boolean {
        if (operation == null) return false
        val className = operation.javaClass.name
        if (className.endsWith(".WH1") || operation.javaClass.simpleName == "WH1") return true

        var stringFieldCount = 0
        var hasMessageModelObject = false
        var nonStringObjects = 0
        var cls: Class<*>? = operation.javaClass
        var depth = 0
        while (cls != null && cls != Any::class.java && depth++ < 4) {
            cls.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers) || field.isSynthetic) return@forEach
                runCatching {
                    field.isAccessible = true
                    val value = field.get(operation) ?: return@forEach
                    if (value is String) {
                        stringFieldCount++
                    } else if (!isPrimitiveLike(value)) {
                        nonStringObjects++
                        if (looksLikeDirectMessageModel(value)) hasMessageModelObject = true
                    }
                }
            }
            cls = cls.superclass
        }
        return stringFieldCount >= 2 && nonStringObjects > 0 && !hasMessageModelObject
    }

    private fun containsLegacyDeleteItemPayload(args: Array<Any?>?): Boolean {
        return args?.any { containsStringFieldValue(it, "direct_v2_delete_item") } == true
    }

    private fun containsStringFieldValue(value: Any?, target: String): Boolean {
        if (value == null) return false
        if (value == target) return true
        var cls: Class<*>? = value.javaClass
        var depth = 0
        while (cls != null && cls != Any::class.java && depth++ < 4) {
            cls.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers) || field.isSynthetic || field.type != String::class.java) return@forEach
                val found = runCatching {
                    field.isAccessible = true
                    field.get(value) == target
                }.getOrDefault(false)
                if (found) return true
            }
            cls = cls.superclass
        }
        return false
    }

    private fun isOneIdServerDelete(args: Array<Any?>?): Boolean {
        if (args == null) return false
        val clientContext: String?
        val messageId: String?
        if (args.size == 3) {
            clientContext = args.getOrNull(1) as? String
            messageId = args.getOrNull(2) as? String
        } else if (args.size == 5) {
            clientContext = args.getOrNull(2) as? String
            messageId = args.getOrNull(3) as? String
        } else {
            return false
        }
        return isUsefulMessageId(clientContext) xor isUsefulMessageId(messageId)
    }

    private fun isUsefulMessageId(value: String?): Boolean {
        val trimmed = value?.trim().orEmpty()
        val lower = trimmed.lowercase(Locale.US)
        return trimmed.length >= 4 &&
            lower !in setOf("null", "none", "remove", "remove_message", "direct_v2_delete_item")
    }

    private fun looksLikeDirectMessageModel(value: Any?): Boolean {
        if (value == null) return false
        var hasMessageIdGetter = false
        var hasClientContextGetter = false
        var cls: Class<*>? = value.javaClass
        var depth = 0
        while (cls != null && cls != Any::class.java && depth++ < 4) {
            cls.declaredMethods.forEach { method ->
                if (method.parameterTypes.isNotEmpty() || method.returnType != String::class.java) return@forEach
                if (method.name == "A0o") hasMessageIdGetter = true
                if (method.name == "A0p") hasClientContextGetter = true
            }
            cls = cls.superclass
        }
        return hasMessageIdGetter && hasClientContextGetter
    }

    private fun isPrimitiveLike(value: Any): Boolean {
        val cls = value.javaClass
        return value is Number ||
            value is Boolean ||
            value is Char ||
            cls.isEnum
    }

    private fun installCopyBioModelHooks() {
        runSafe("Copy bio model hooks") {
            var hooked = 0
            arrayOf("com.instagram.user.model.LiveTreeUserDict", "p000X.C2A5", "X.C2A5").forEach { className ->
                hooked += hookBiographyStringMethods(className, exactOnly = true)
            }
            hooked += hookBiographyHashMethods()
            hooked += hookDexDiscoveredBiographyClasses()
            hooked += hookProfileActionSheetControllers()
            logInfo("Installed copy-bio source-style hooks=$hooked")
        }
    }

    private fun hookDexDiscoveredBiographyClasses(): Int {
        var hooked = 0
        val seen = HashSet<String>()
        arrayOf("biography", "biography_with_entities", "profile_header_bio").forEach { marker ->
            dexBridge.findClassNamesUsingStrings(marker).forEach { className ->
                if (!seen.add(className)) return@forEach
                hooked += hookBiographyStringMethods(className, exactOnly = false)
            }
        }
        return hooked
    }

    private fun hookBiographyStringMethods(className: String, exactOnly: Boolean): Int {
        val cls = runCatching { Class.forName(className, false, appClassLoader) }.getOrNull() ?: return 0
        var hooked = 0
        cls.declaredMethods.forEach { method ->
            if (method.parameterTypes.isNotEmpty() || method.returnType != String::class.java) return@forEach
            if (exactOnly && method.name != "BCv") return@forEach
            if (!exactOnly && !looksLikeBiographyGetterMethod(cls, method)) return@forEach
            val signature = "${cls.name}.${method.name}:copy_bio_getter"
            if (!hookedDexMethods.add(signature)) return@forEach
            method.isAccessible = true
            XposedBridge.hookMethod(
                method,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (!state.enableCopyBio) return
                        val bio = (param.result as? String)?.trim().orEmpty()
                        if (!isReliableBioText(bio)) return
                        rememberBioText(findUsernameOnModel(param.thisObject), bio)
                    }
                }
            )
            hooked++
        }
        return hooked
    }

    private fun hookBiographyHashMethods(): Int {
        var hooked = 0
        dexBridge.findMethodsUsingNumbers(BIOGRAPHY_HASH).forEach { method ->
            if (method.returnType != String::class.java && method.returnType != Any::class.java) return@forEach
            val signature = "${method.declaringClass.name}.${method.name}:copy_bio_hash"
            if (!hookedDexMethods.add(signature)) return@forEach
            method.isAccessible = true
            XposedBridge.hookMethod(
                method,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (!state.enableCopyBio) return
                        val bio = (param.result as? String)?.trim().orEmpty()
                        if (bio.isBlank() || isNeverBioText(bio.lowercase(Locale.US))) return
                        val model = param.thisObject ?: param.args?.firstOrNull()
                        rememberExactBioText(findUsernameOnModel(model), bio, "hash:${method.name}")
                    }
                }
            )
            hooked++
        }
        return hooked
    }

    private fun hookProfileActionSheetControllers(): Int {
        var hooked = 0
        dexBridge.findClassNamesUsingStrings("copy_profile_url").forEach { className ->
            val cls = runCatching { Class.forName(className, false, appClassLoader) }.getOrNull() ?: return@forEach
            runCatching {
                XposedBridge.hookAllConstructors(
                    cls,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam<*>) {
                            if (state.enableCopyBio) rememberProfileControllerUser(param.thisObject)
                        }
                    }
                )
            }
            cls.declaredMethods.filter { it.parameterTypes.size <= 3 }.forEach { method ->
                val signature = "${cls.name}.${method.name}:copy_bio_controller"
                if (!hookedDexMethods.add(signature)) return@forEach
                method.isAccessible = true
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam<*>) {
                            if (state.enableCopyBio) rememberProfileControllerUser(param.thisObject)
                        }
                    }
                )
            }
            hooked++
        }
        return hooked
    }

    private fun rememberProfileControllerUser(controller: Any?) {
        val user = findInstagramUserObject(controller) ?: return
        val username = extractUsernameFromUser(user)
        val bio = extractBiographyFromUser(user)?.trim().orEmpty()
        if (bio.isBlank() || isNeverBioText(bio.lowercase(Locale.US))) return
        rememberBioText(username, bio)
        currentProfileBioText = bio
        currentProfileUsername = normalizeUsername(username).ifBlank { null }
        currentProfileBioAtMs = System.currentTimeMillis()
    }

    private fun findInstagramUserObject(owner: Any?): Any? {
        if (owner == null) return null
        if (owner.javaClass.name == "com.instagram.user.model.User") return owner
        var cls: Class<*>? = owner.javaClass
        var checked = 0
        while (cls != null && cls != Any::class.java && checked < 80) {
            cls.declaredFields.forEach { field ->
                if (checked++ >= 80) return@forEach
                runCatching {
                    field.isAccessible = true
                    val value = field.get(owner)
                    if (value?.javaClass?.name == "com.instagram.user.model.User") return value
                }
            }
            cls = cls.superclass
        }
        return null
    }

    private fun extractUsernameFromUser(user: Any?): String? {
        if (user != null && (userClass?.isInstance(user) == true || user.javaClass.name == "com.instagram.user.model.User")) {
            userUsernameGetter?.let { getter ->
                runCatching { getter.invoke(user) as? String }
                    .getOrNull()
                    ?.takeIf { normalizeUsername(it).isNotBlank() }
                    ?.let { return it }
            }
        }
        invokeStringNoArg(user, "getUsername")?.takeIf { normalizeUsername(it).isNotBlank() }?.let { return it }
        return findUsernameOnModel(userDict(user))
    }

    private fun extractBiographyFromUser(user: Any?): String? {
        val dict = userDict(user)
        invokeStringNoArg(dict, "BCv")?.let { return it }
        return invokeBiographyByHash(dict)
    }

    private fun userDict(user: Any?): Any? {
        if (user == null) return null
        return runCatching {
            val field = user.javaClass.getDeclaredField("A00")
            field.isAccessible = true
            field.get(user)
        }.getOrNull()
    }

    private fun invokeBiographyByHash(dict: Any?): String? {
        if (dict == null) return null
        var cls: Class<*>? = dict.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredMethods.forEach { method ->
                runCatching {
                    val params = method.parameterTypes
                    method.isAccessible = true
                    val value = when {
                        Modifier.isStatic(method.modifiers) &&
                            params.size == 2 &&
                            params[0].isAssignableFrom(dict.javaClass) &&
                            params[1] == java.lang.Integer.TYPE &&
                            (method.returnType == Any::class.java || method.returnType == String::class.java) ->
                            method.invoke(null, dict, BIOGRAPHY_HASH)
                        !Modifier.isStatic(method.modifiers) &&
                            params.size == 1 &&
                            params[0] == java.lang.Integer.TYPE &&
                            (method.returnType == Any::class.java || method.returnType == String::class.java) ->
                            method.invoke(dict, BIOGRAPHY_HASH)
                        else -> null
                    }
                    if (value is String) return value
                }
            }
            cls = cls.superclass
        }
        cls = dict.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                runCatching {
                    field.isAccessible = true
                    val value = field.get(dict) ?: return@forEach
                    val getter = value.javaClass.getMethod("getOptionalStringValueByHashCode", java.lang.Integer.TYPE)
                    (getter.invoke(value, BIOGRAPHY_HASH) as? String)?.let { return it }
                }
            }
            cls = cls.superclass
        }
        return null
    }

    private fun maybeInstallCopyBioLongPress(textView: TextView) {
        if (!isProfileBioText(textView)) return
        rememberBioText(null, textView.text)
        synchronized(copyBioHookedTextViews) {
            if (!copyBioHookedTextViews.add(textView)) return
        }
        textView.isLongClickable = true
        textView.setOnLongClickListener { view ->
            val text = (view as? TextView)?.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) return@setOnLongClickListener false
            copyTextToClipboard(view.context, "Instagram bio", text, "Bio copied")
            true
        }
    }

    private fun maybeRememberProfileBioCandidate(textView: TextView) {
        val text = textView.text?.toString()?.trim().orEmpty()
        if (text.isBlank() || isNeverBioText(text.lowercase(Locale.US))) return
        val name = resourceEntryName(textView).orEmpty()
        val exactBioId = name == "profile_header_bio_text" ||
            name == "pbia_profile_header_bio" ||
            name == "biography" ||
            (name.contains("profile", ignoreCase = true) && name.contains("bio", ignoreCase = true))
        val score = scoreBioCandidate(textView.rootView, textView, text)
        val likelyBio = isReliableBioText(text) && (exactBioId || score >= 45 || (hasProfileBioAncestor(textView) && score >= 25))
        if (!likelyBio) return
        rememberBioText(findVisibleProfileUsername(textView.rootView), text)
    }

    private fun maybeInjectProfileMenuCopyBio(anchor: TextView) {
        val text = anchor.text?.toString()?.trim().orEmpty()
        if (!text.equals("Copy profile URL", ignoreCase = true) && !text.equals("Copy profile url", ignoreCase = true)) return
        synchronized(copyBioProfileMenuRows) { copyBioProfileMenuRows.add(anchor) }
        rememberVisibleBio(anchor.rootView)
        copyBioMenuActiveUntilMs = System.currentTimeMillis() + 120_000L
        anchor.text = "Copy bio"
        anchor.isLongClickable = false
        val row = findMenuActionRow(anchor)
        val action = View.OnClickListener { view -> copyVisibleBio(view.context, anchor.rootView) }
        anchor.setOnClickListener(action)
        row?.takeIf { it !== anchor }?.let {
            it.isClickable = true
            it.setOnClickListener(action)
        }
    }

    private fun handleCopyBioMenuClick(view: View): Boolean {
        if (!state.enableCopyBio || handlingCopyBioMenuClick.get()) return false
        if (!isCopyBioProfileMenuClickTarget(view)) return false
        handlingCopyBioMenuClick.set(true)
        return try {
            copyBioMenuActiveUntilMs = System.currentTimeMillis() + 120_000L
            rememberVisibleBio(view.rootView)
            copyVisibleBio(view.context, view.rootView)
            true
        } finally {
            handlingCopyBioMenuClick.set(false)
        }
    }

    private fun copyVisibleBio(context: Context, root: View?) {
        val bio = bestKnownBioForRoot(root)
        if (!isReliableBioText(bio)) {
            Toast.makeText(context, "No bio found", Toast.LENGTH_SHORT).show()
            return
        }
        copyTextToClipboard(context, "Instagram bio", bio, "Bio copied")
    }

    private fun bestKnownBioForRoot(root: View?): String {
        val searchRoot = bestProfileSearchRoot(root)
        rememberVisibleBio(searchRoot)
        val visibleUsername = findVisibleProfileUsername(searchRoot)
        val current = currentProfileBioText?.takeIf { System.currentTimeMillis() - currentProfileBioAtMs < 5 * 60_000L }
        val currentUser = currentProfileUsername
        if (isReliableBioText(current) &&
            (visibleUsername == null || currentUser == null || currentUser == normalizeUsername(visibleUsername))
        ) {
            return current!!.trim()
        }
        val visible = scanVisibleBioText(searchRoot)
        if (isReliableBioText(visible)) return visible.trim()
        val cached = normalizeUsername(visibleUsername).takeIf { it.isNotBlank() }?.let { recentBiosByUsername[it] }
        if (isReliableBioText(cached)) return cached!!.trim()
        if (lastSeenBioUsername != null &&
            visibleUsername != null &&
            lastSeenBioUsername == normalizeUsername(visibleUsername) &&
            isReliableBioText(lastSeenBioText)
        ) {
            return lastSeenBioText!!.trim()
        }
        return ""
    }

    private fun rememberVisibleBio(root: View?) {
        val searchRoot = bestProfileSearchRoot(root)
        val bio = scanVisibleBioText(searchRoot)
        if (isReliableBioText(bio)) rememberBioText(findVisibleProfileUsername(searchRoot), bio)
    }

    private fun bestProfileSearchRoot(root: View?): View? {
        val decor = currentActivity?.window?.decorView
        if (decor != null && (root == null || decor === root || decor.height >= root.height)) return decor
        return root
    }

    private fun scanVisibleBioText(root: View?): String {
        if (root == null) return ""
        val best = BioCandidate("", Int.MIN_VALUE)
        var visited = 0
        fun visit(view: View) {
            if (visited++ > 600) return
            if (view is TextView) {
                val text = view.text?.toString()?.trim().orEmpty()
                val score = scoreBioCandidate(root, view, text)
                if (score > best.score) {
                    best.text = text
                    best.score = score
                }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
        return if (best.score >= 45 && isReliableBioText(best.text)) best.text else ""
    }

    private fun isProfileBioText(textView: TextView): Boolean {
        val text = textView.text?.toString()?.trim().orEmpty()
        val name = resourceEntryName(textView).orEmpty().lowercase(Locale.US)
        if (name.contains("translation") || name.contains("translate")) return false
        if (isNeverBioText(text.lowercase(Locale.US))) return false
        if (name == "profile_header_bio_text" ||
            name == "pbia_profile_header_bio" ||
            name == "biography" ||
            (name.contains("profile") && name.contains("bio"))
        ) {
            return text.isNotBlank()
        }
        if (text.length < 2 || text.length > 300) return false
        if (text.startsWith("@")) return false
        return name.contains("bio") && hasProfileBioAncestor(textView)
    }

    private fun hasProfileBioAncestor(view: View): Boolean {
        var current: View? = view
        repeat(8) {
            val hay = "${current?.let { resourceEntryName(it) }.orEmpty()} ${current?.javaClass?.name.orEmpty()}".lowercase(Locale.US)
            if ((hay.contains("profile") && hay.contains("bio")) ||
                hay.contains("profile_header") ||
                hay.contains("pbia_profile") ||
                hay.contains("user_profile_header")
            ) {
                return true
            }
            current = current?.parent as? View
        }
        return false
    }

    private fun findVisibleProfileUsername(root: View?): String? {
        if (root == null) return null
        val best = UsernameCandidate(null, Int.MIN_VALUE)
        var visited = 0
        fun visit(view: View) {
            if (visited++ > 700) return
            if (view is TextView) {
                val name = resourceEntryName(view).orEmpty().lowercase(Locale.US)
                val raw = view.text?.toString()?.trim().orEmpty()
                val username = normalizeUsername(raw)
                if (username.isNotBlank() && raw.length <= 64) {
                    var score = 0
                    if (name.contains("username")) score += 80
                    if (name.contains("profile_header") || name.contains("action_bar")) score += 35
                    if (raw.startsWith("@")) score += 20
                    if (hasProfileBioAncestor(view)) score += 10
                    if (raw.contains(" ")) score -= 60
                    runCatching {
                        val loc = IntArray(2)
                        view.getLocationOnScreen(loc)
                        if (loc[1] < maxOf(240, (view.rootView.height * 0.35f).roundToInt())) score += 20
                    }
                    if (score > best.score) {
                        best.username = username
                        best.score = score
                    }
                }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
        return best.username?.takeIf { best.score >= 15 }
    }

    private fun rememberBioText(username: String?, bioValue: CharSequence?) {
        val bio = bioValue?.toString()?.trim().orEmpty()
        if (!isReliableBioText(bio)) return
        lastSeenBioText = bio
        val cleanUser = normalizeUsername(username)
        if (cleanUser.isNotBlank()) {
            lastSeenBioUsername = cleanUser
            recentBiosByUsername[cleanUser] = bio
        }
        if (copyBioLogCount++ < 12) logInfo("Cached Instagram bio${cleanUser.takeIf { it.isNotBlank() }?.let { " for @$it" }.orEmpty()}")
    }

    private fun rememberExactBioText(username: String?, bioValue: CharSequence?, source: String) {
        val bio = bioValue?.toString()?.trim().orEmpty()
        if (bio.isBlank() || isNeverBioText(bio.lowercase(Locale.US))) return
        lastSeenBioText = bio
        val cleanUser = normalizeUsername(username)
        if (cleanUser.isNotBlank()) {
            lastSeenBioUsername = cleanUser
            recentBiosByUsername[cleanUser] = bio
        }
        if (copyBioLogCount++ < 20) logInfo("Cached exact Instagram bio source=$source${cleanUser.takeIf { it.isNotBlank() }?.let { " for @$it" }.orEmpty()}")
    }

    private fun findUsernameOnModel(model: Any?): String? {
        if (model == null) return null
        arrayOf("getUsername", "DJy", "BKR", "A0r").forEach { methodName ->
            val value = invokeStringNoArg(model, methodName)
            if (normalizeUsername(value).isNotBlank()) return value
        }
        var cls: Class<*>? = model.javaClass
        var depth = 0
        while (cls != null && cls != Any::class.java && depth++ < 3) {
            cls.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers) || field.type != String::class.java) return@forEach
                runCatching {
                    field.isAccessible = true
                    val value = field.get(model) as? String
                    if (normalizeUsername(value).isNotBlank() && field.name.lowercase(Locale.US).contains("user")) return value
                }
            }
            cls = cls.superclass
        }
        return null
    }

    private fun invokeStringNoArg(target: Any?, methodName: String): String? {
        val value = invokeNoArgDeep(target, methodName)
        return value as? String
    }

    private fun looksLikeBiographyGetterMethod(cls: Class<*>, method: Method): Boolean {
        val hay = "${cls.name} ${method.name}".lowercase(Locale.US)
        return hay.contains("biograph") || hay.contains("profile_header_bio") || hay.contains("bio_text")
    }

    private fun isReliableBioText(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        if (text.length < 2 || text.length > 500) return false
        val lower = text.lowercase(Locale.US)
        if (isNeverBioText(lower)) return false
        if (looksLikeLinkOnly(text) || looksLikeDisplayNameOnly(text)) return false
        return looksLikeBioContent(text)
    }

    private fun scoreBioCandidate(root: View?, textView: TextView, value: String?): Int {
        val text = value?.trim().orEmpty()
        if (text.length < 2 || text.length > 500) return Int.MIN_VALUE
        val lower = text.lowercase(Locale.US)
        if (isNeverBioText(lower)) return Int.MIN_VALUE
        val name = resourceEntryName(textView).orEmpty().lowercase(Locale.US)
        val exactBioId = name.contains("bio") || name.contains("biography")
        if (!exactBioId) return Int.MIN_VALUE
        if (looksLikeLinkOnly(text)) return Int.MIN_VALUE

        var score = 120
        if (hasProfileBioAncestor(textView)) score += 35
        if (text.contains('\n')) score += 45
        if (lower.contains("@") || lower.contains("http") || lower.contains("linktr") ||
            lower.contains(".com") || lower.contains("mail")
        ) {
            score += 12
        }
        if (text.contains(" ") || text.contains(":")) score += 15
        score += minOf(30, text.length / 4)
        runCatching {
            val loc = IntArray(2)
            textView.getLocationOnScreen(loc)
            val rootHeight = root?.height ?: textView.rootView?.height ?: 0
            if (rootHeight > 0 && loc[1] > rootHeight * 0.58f) score -= 120
            if (rootHeight > 0 && loc[1] < rootHeight * 0.08f) score -= 40
        }
        return score
    }

    private fun looksLikeLinkOnly(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        if (text.isBlank() || text.contains('\n')) return false
        val compact = text.lowercase(Locale.US).replace(" ", "")
        val urlish = compact.startsWith("http://") ||
            compact.startsWith("https://") ||
            compact.startsWith("www.") ||
            compact.startsWith("linktr.ee/") ||
            compact.contains("instagram.com/") ||
            compact.contains("youtube.com/") ||
            compact.contains("youtu.be/") ||
            compact.contains(".com/") ||
            compact.endsWith(".com") ||
            compact.contains(".in/") ||
            compact.endsWith(".in")
        val emailish = compact.matches(Regex("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}"))
        return (urlish || emailish) && text.split(Regex("\\s+")).size <= 2
    }

    private fun looksLikeDisplayNameOnly(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        if (text.length < 2 || text.length > 48) return false
        val lower = text.lowercase(Locale.US)
        if (text.contains('\n') || lower.contains("@") || lower.contains("http")) return false
        if (text.contains("|") || text.contains(":") || text.contains("/") || text.contains("&")) return false
        if (text.split(Regex("\\s+")).size > 2) return false
        if (text.any { it.isDigit() }) return false
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            val type = Character.getType(codePoint)
            if (type == Character.OTHER_SYMBOL.toInt() || type == Character.MATH_SYMBOL.toInt()) return false
            offset += Character.charCount(codePoint)
        }
        return text.matches(Regex("[\\p{L} ._'-]+"))
    }

    private fun looksLikeBioContent(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        if (text.length < 2 || text.length > 500) return false
        val lower = text.lowercase(Locale.US)
        if (isNeverBioText(lower) || looksLikeLinkOnly(text) || looksLikeDisplayNameOnly(text)) return false
        if (text.contains('\n')) return true
        if (text.split(Regex("\\s+")).size >= 3) return true
        if (text.contains("|") || text.contains(":") || text.contains("/") || text.contains("&")) return true
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            val type = Character.getType(codePoint)
            if (type == Character.OTHER_SYMBOL.toInt() || type == Character.MATH_SYMBOL.toInt()) return true
            offset += Character.charCount(codePoint)
        }
        return false
    }

    private fun isNeverBioText(lower: String): Boolean {
        if (lower.isBlank()) return true
        if (lower.matches(Regex("@?\\d{6,}"))) return true
        if (lower.contains("translation")) return true
        return lower == "copy bio" ||
            lower == "copy profile url" ||
            lower == "copy profile link" ||
            lower == "edit profile" ||
            lower == "message" ||
            lower == "follow" ||
            lower == "following" ||
            lower == "id" ||
            lower == "posts" ||
            lower == "followers" ||
            lower == "restrict" ||
            lower == "block" ||
            lower == "report" ||
            lower == "about this account" ||
            lower == "hide your story" ||
            lower == "share this profile" ||
            lower == "qr code" ||
            lower == "add your bio" ||
            lower == "see translation" ||
            lower == "view translation" ||
            lower == "translate" ||
            lower == "original" ||
            lower.matches(Regex("\\d+(\\.\\d+)?[km]?")) ||
            lower.endsWith(" posts") ||
            lower.endsWith(" followers") ||
            lower.endsWith(" following") ||
            lower.contains("followers") ||
            lower.contains("following") ||
            lower.contains("posts")
    }

    private fun normalizeUsername(value: String?): String {
        val clean = value?.trim()?.removePrefix("@").orEmpty()
        if (clean.length !in 2..30) return ""
        if (!clean.matches(Regex("[A-Za-z0-9._]+"))) return ""
        val lower = clean.lowercase(Locale.US)
        if (lower == "id" || isNeverBioText(lower)) return ""
        return if (lower == "instagram" || lower == "meta") lower else lower
    }

    private fun findMenuActionRow(child: View): View? {
        var current: View? = child
        var best: View? = child
        repeat(8) {
            val parent = current?.parent as? View ?: return best
            if (parent.javaClass.name.contains("RecyclerView")) return current
            best = parent
            current = parent
        }
        return best
    }

    private fun isCopyBioProfileMenuClickTarget(view: View): Boolean {
        if (!containsExactText(view, "copy bio", 0, intArrayOf(0))) return false
        var current: View? = view
        repeat(7) {
            val hay = "${current?.javaClass?.name.orEmpty()} ${current?.let { resourceEntryName(it) }.orEmpty()}".lowercase(Locale.US)
            if (hay.contains("recyclerview") || hay.contains("action_sheet") || hay.contains("menu") || hay.contains("sheet")) return true
            current = current?.parent as? View
        }
        return false
    }

    private fun containsExactText(view: View?, lowerNeedle: String, depth: Int, visited: IntArray): Boolean {
        if (view == null || depth > 5 || visited[0]++ > 120) return false
        if (view is TextView && view.text?.toString()?.trim()?.lowercase(Locale.US) == lowerNeedle) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) if (containsExactText(view.getChildAt(i), lowerNeedle, depth + 1, visited)) return true
        }
        return false
    }

    private fun rewriteCopyBioClipData(clip: ClipData): ClipData? {
        if (!state.enableCopyBio || System.currentTimeMillis() > copyBioMenuActiveUntilMs || !looksLikeInstagramProfileUrlClip(clip)) return null
        val bio = lastSeenBioText?.trim().orEmpty()
        if (!isReliableBioText(bio)) return null
        return ClipData.newPlainText("Instagram bio", bio)
    }

    private fun looksLikeInstagramProfileUrlClip(clip: ClipData): Boolean {
        for (i in 0 until clip.itemCount) {
            val item = clip.getItemAt(i)
            if (looksLikeInstagramProfileUrl(item.text?.toString()) || looksLikeInstagramProfileUrl(item.uri?.toString())) return true
        }
        return false
    }

    private fun looksLikeInstagramProfileUrl(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        if (!text.contains("instagram.com/")) return false
        val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        if (!host.endsWith("instagram.com")) return false
        val segments = uri.pathSegments.orEmpty().filter { it.isNotBlank() }
        return segments.size == 1 && normalizeUsername(segments[0]).isNotBlank()
    }

    private fun installCommentCopyLongPressHooks() {
        runSafe("Copy comment long-press hook") {
            val hook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    if (!state.enableCopyComment) return
                    val text = extractCommentText(param.thisObject)?.trim().orEmpty()
                    if (text.isBlank()) return
                    val context = currentActivity ?: return
                    showCommentCopyPopup(context, text)
                }
            }
            if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadMethods("CommentCopy_LongPress", appClassLoader)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { cached ->
                        cached.forEach { method ->
                            val signature = "${methodKey(method)}:copy_comment_cached"
                            if (hookedDexMethods.add(signature)) XposedBridge.hookMethod(method, hook)
                        }
                        logInfo("Installed cached copy-comment long-press hooks=${cached.size}")
                        return@runSafe
                    }
            }
            val classFallbacks = dexBridge.findClassNamesUsingStrings("fb_comment_long_press")
                .flatMap { className ->
                    runCatching { Class.forName(className, false, appClassLoader).declaredMethods.toList() }
                        .getOrDefault(emptyList())
                }
            val candidates = (dexBridge.findMethodsUsingStrings("fb_comment_long_press") +
                dexBridge.findMethodsUsingStrings("comment_row_component") +
                classFallbacks)
                .filter { it.name == "onLongPress" }
                .distinctBy(::methodKey)
            var hooked = 0
            candidates.forEach { method ->
                val signature = "${method.declaringClass.name}.${method.name}:copy_comment"
                if (!hookedDexMethods.add(signature)) return@forEach
                method.isAccessible = true
                XposedBridge.hookMethod(method, hook)
                hooked++
            }
            if (candidates.isNotEmpty()) InstagramDexKitCache.saveMethods("CommentCopy_LongPress", candidates)
            logInfo("Installed copy-comment long-press hooks=$hooked")
        }
    }

    private fun extractCommentText(gestureListener: Any?): String? {
        if (gestureListener == null) return null
        if (InstagramDexKitCache.isCacheValid()) {
            val itemClass = InstagramDexKitCache.loadString("CommentCopy_ItemClass")
            val textField = InstagramDexKitCache.loadString("CommentCopy_TextField")
            if (!itemClass.isNullOrBlank() && !textField.isNullOrBlank()) {
                runCatching {
                    val item = findCommentItemByClass(gestureListener, itemClass) ?: return@runCatching null
                    val field = item.javaClass.getDeclaredField(textField)
                    field.isAccessible = true
                    (field.get(item) as? String)?.takeIf { it.isNotBlank() }
                }.getOrNull()?.let { return it }
            }
        }
        return discoverCommentText(gestureListener)
    }

    private fun findCommentItemByClass(gestureListener: Any, targetClassName: String): Any? {
        gestureListener.javaClass.declaredFields.forEach { first ->
            if (!isObfuscatedObjectField(first)) return@forEach
            runCatching {
                first.isAccessible = true
                val holder = first.get(gestureListener) ?: return@forEach
                holder.javaClass.declaredFields.forEach { second ->
                    if (!isObfuscatedObjectField(second)) return@forEach
                    runCatching {
                        second.isAccessible = true
                        val item = second.get(holder)
                        if (item != null && item.javaClass.name == targetClassName) return item
                    }
                }
            }
        }
        return null
    }

    private fun discoverCommentText(gestureListener: Any): String? {
        val userClass = runCatching { Class.forName("com.instagram.user.model.User", false, gestureListener.javaClass.classLoader) }.getOrNull()
        var best: String? = null
        gestureListener.javaClass.declaredFields.forEach { first ->
            if (!isObfuscatedObjectField(first)) return@forEach
            runCatching {
                first.isAccessible = true
                val holder = first.get(gestureListener) ?: return@forEach
                holder.javaClass.declaredFields.forEach { second ->
                    if (!isObfuscatedObjectField(second)) return@forEach
                    runCatching {
                        second.isAccessible = true
                        val item = second.get(holder) ?: return@forEach
                        if (userClass != null && !hasFieldOfType(item.javaClass, userClass)) return@forEach
                        val (fieldName, text) = findBestCommentTextField(item) ?: return@forEach
                        InstagramDexKitCache.saveString("CommentCopy_ItemClass", item.javaClass.name)
                        InstagramDexKitCache.saveString("CommentCopy_TextField", fieldName)
                        if (text.isNotBlank() && (best == null || text.length > best!!.length)) best = text
                    }
                }
            }
        }
        return best
    }

    private fun findBestCommentTextField(item: Any): Pair<String, String>? {
        var bestName: String? = null
        var bestValue: String? = null
        item.javaClass.declaredFields.forEach { field ->
            if (field.type != String::class.java) return@forEach
            runCatching {
                field.isAccessible = true
                val value = (field.get(item) as? String)?.trim().orEmpty()
                if (value.isBlank() || value.matches(Regex("\\d+")) || value.matches(Regex("\\d+_\\d+")) || value.startsWith("http")) return@forEach
                if (bestValue == null || value.length > bestValue!!.length) {
                    bestName = field.name
                    bestValue = value
                }
            }
        }
        return bestName?.let { name -> bestValue?.let { value -> name to value } }
    }

    private fun isObfuscatedObjectField(field: Field): Boolean {
        val type = field.type
        if (type.isPrimitive || type == String::class.java) return false
        val name = type.name
        return !name.startsWith("android.") && !name.startsWith("java.") && !name.startsWith("kotlin.") && !name.startsWith("androidx.")
    }

    private fun hasFieldOfType(cls: Class<*>, target: Class<*>): Boolean {
        return cls.declaredFields.any { target.isAssignableFrom(it.type) }
    }

    private fun showCommentCopyPopup(context: Context, text: String) {
        mainHandler.post {
            runCatching {
                AlertDialog.Builder(context)
                    .setTitle("Copy Comment")
                    .setMessage(text)
                    .setPositiveButton("Copy") { _, _ -> copyTextToClipboard(context, "comment", text, "Comment copied") }
                    .setNeutralButton("Select") { _, _ -> showCommentSelectDialog(context, text) }
                    .show()
            }.onFailure { logError("Copy comment popup failed", it) }
        }
    }

    private fun showCommentSelectDialog(context: Context, text: String) {
        val input = EditText(context).apply {
            setText(text)
            setTextIsSelectable(true)
            isFocusableInTouchMode = true
            setSelection(0, text.length)
            minLines = 3
            maxLines = 8
        }
        AlertDialog.Builder(context)
            .setTitle("Select Comment")
            .setView(input)
            .setPositiveButton("Copy Selected") { _, _ ->
                val start = input.selectionStart.coerceAtLeast(0)
                val end = input.selectionEnd.coerceAtLeast(0)
                val selected = if (end > start) text.substring(start, end) else text
                copyTextToClipboard(context, "comment", selected, "Comment copied")
            }
            .setNeutralButton("Copy All") { _, _ -> copyTextToClipboard(context, "comment", text, "Comment copied") }
            .show()
    }

    private fun copyTextToClipboard(context: Context, label: String, text: String, toast: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }

    private fun collectRemoveOperationIds(operations: List<*>): Set<String> {
        val ids = HashSet<String>()
        operations.forEach { operation ->
            if (isLikelyRemoveMessageOperation(operation)) ids += collectStringFields(operation)
        }
        return ids
    }

    private fun collectRealtimeRemoveIds(args: Array<Any?>?): Set<String> {
        if (args == null || !containsStringFieldValue(args.getOrNull(1), "remove")) return emptySet()
        val ids = HashSet<String>()
        args.forEach { arg ->
            if (arg is Map<*, *>) {
                arg.values.filterIsInstance<String>().forEach { addUsefulId(it, ids) }
            }
        }
        return ids
    }

    private fun rememberScopedDeleteIds(param: XC_MethodHook.MethodHookParam<*>, deleteIds: Set<String>) {
        val previous = remoteDeleteIds.get()
        param.setObjectExtra("ie_keep_unsent_has_scoped_delete_ids", true)
        param.setObjectExtra("ie_keep_unsent_previous_delete_ids", previous)
        remoteDeleteIds.set((previous.orEmpty() + deleteIds).toSet())
    }

    @Suppress("UNCHECKED_CAST")
    private fun restoreScopedDeleteIds(param: XC_MethodHook.MethodHookParam<*>) {
        if (param.getObjectExtra("ie_keep_unsent_has_scoped_delete_ids") != true) return
        val previous = param.getObjectExtra("ie_keep_unsent_previous_delete_ids") as? Set<String>
        if (previous != null) remoteDeleteIds.set(previous) else remoteDeleteIds.remove()
    }

    private fun isScopedRemoteDelete(args: Array<Any?>?): Boolean {
        val ids = remoteDeleteIds.get().orEmpty()
        if (ids.isEmpty() || args == null) return false
        return args.any { it is String && ids.contains(it.trim()) }
    }

    private fun collectStringFields(target: Any?): Set<String> {
        val ids = HashSet<String>()
        if (target == null) return ids
        var cls: Class<*>? = target.javaClass
        var depth = 0
        while (cls != null && cls != Any::class.java && depth++ < 4) {
            cls.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers) || field.isSynthetic || field.type != String::class.java) return@forEach
                runCatching {
                    field.isAccessible = true
                    addUsefulId(field.get(target) as? String, ids)
                }
            }
            cls = cls.superclass
        }
        return ids
    }

    private fun collectUsefulIdsDeep(target: Any?): Set<String> {
        val ids = HashSet<String>()
        collectUsefulIdsDeep(target, ids, Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()), 0)
        return ids
    }

    private fun collectUsefulIdsDeep(target: Any?, out: MutableSet<String>, seen: MutableSet<Any>, depth: Int) {
        if (target == null || depth > 3) return
        when (target) {
            is String -> {
                addUsefulId(target, out)
                return
            }
            is Array<*> -> {
                target.take(32).forEach { collectUsefulIdsDeep(it, out, seen, depth + 1) }
                return
            }
            is Iterable<*> -> {
                target.take(32).forEach { collectUsefulIdsDeep(it, out, seen, depth + 1) }
                return
            }
            is Map<*, *> -> {
                target.values.take(32).forEach { collectUsefulIdsDeep(it, out, seen, depth + 1) }
                return
            }
        }
        if (isPrimitiveLike(target) || !seen.add(target)) return
        var cls: Class<*>? = target.javaClass
        var classDepth = 0
        while (cls != null && cls != Any::class.java && classDepth++ < 4) {
            cls.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers) || field.isSynthetic || field.type.isPrimitive) return@forEach
                runCatching {
                    field.isAccessible = true
                    val value = field.get(target) ?: return@forEach
                    if (value is String) addUsefulId(value, out) else if (!isPrimitiveLike(value)) {
                        collectUsefulIdsDeep(value, out, seen, depth + 1)
                    }
                }
            }
            cls = cls.superclass
        }
    }

    private fun rememberKeepUnsentDeletedIds(ids: Collection<String>) {
        val messageIds = extractDeletedMessageIds(ids)
        if (messageIds.isEmpty()) return
        var added = 0
        synchronized(keepUnsentDeletedIds) {
            messageIds.forEach { id ->
                val key = id.trim()
                if (!keepUnsentDeletedIds.containsKey(key)) added++
                keepUnsentDeletedIds[key] = true
            }
        }
        if (added > 0) {
            keepUnsentPersistDirty = true
            persistKeepUnsentDeletedIds(androidContext)
            logInfo("Marked unsent Direct message ids: $added")
        }
        decorateKeepUnsentBoundRows(messageIds)
    }

    private fun loadKeepUnsentDeletedIds(context: Context?) {
        if (keepUnsentPersistedLoaded || context == null) return
        synchronized(keepUnsentPersistLock) {
            if (keepUnsentPersistedLoaded) return
            runCatching {
                val app = context.applicationContext ?: context
                val saved = app.getSharedPreferences(KEEP_UNSENT_PREFS, Context.MODE_PRIVATE)
                    .getStringSet(KEEP_UNSENT_PREF_IDS, null)
                saved.orEmpty().forEach { id ->
                    if (isMessageScopedId(id, allowNumeric = true)) keepUnsentDeletedIds[id.trim()] = true
                }
                keepUnsentPersistedLoaded = true
                if (!saved.isNullOrEmpty()) logInfo("Loaded persisted unsent Direct ids: ${saved.size}")
            }.onFailure { logError("Failed loading unsent Direct ids", it) }
        }
    }

    private fun persistKeepUnsentDeletedIds(context: Context?) {
        if (context == null) return
        synchronized(keepUnsentPersistLock) {
            runCatching {
                val ids = synchronized(keepUnsentDeletedIds) { keepUnsentDeletedIds.keys.toList() }
                    .takeLast(MAX_KEEP_UNSENT_IDS)
                    .filterTo(HashSet()) { isMessageScopedId(it, allowNumeric = true) }
                val app = context.applicationContext ?: context
                val ok = app.getSharedPreferences(KEEP_UNSENT_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putStringSet(KEEP_UNSENT_PREF_IDS, ids)
                    .commit()
                if (ok) keepUnsentPersistDirty = false
            }.onFailure { logError("Failed persisting unsent Direct ids", it) }
        }
    }

    private fun containsKeepUnsentDeletedId(ids: Set<String>): Boolean {
        val rowIds = extractRowBindableIds(ids)
        if (rowIds.isEmpty()) return false
        val deleted = synchronized(keepUnsentDeletedIds) {
            rowIds.firstOrNull { keepUnsentDeletedIds.containsKey(it.trim()) }?.let { return true }
            keepUnsentDeletedIds.keys.toList()
        }
        rowIds.forEach { rowId ->
            deleted.forEach { deletedId ->
                if (idsLikelyMatch(rowId, deletedId)) {
                    logKeepUnsentFuzzyMatchOnce()
                    return true
                }
            }
        }
        return false
    }

    private fun extractDeletedMessageIds(ids: Collection<String>?): Set<String> {
        val parsed = HashSet<String>()
        val fallback = HashSet<String>()
        ids.orEmpty().forEach { raw ->
            val trimmed = raw.trim()
            collectUriMessageIds(trimmed, parsed, strictMessageOnly = true)
            if (isMessageScopedId(trimmed, allowNumeric = true)) fallback += trimmed
        }
        return if (parsed.isNotEmpty()) parsed else fallback
    }

    private fun extractRowBindableIds(ids: Collection<String>?): Set<String> {
        val out = HashSet<String>()
        ids.orEmpty().forEach { id ->
            if (isMessageScopedId(id, allowNumeric = true)) out += id.trim()
        }
        return out
    }

    private fun addUsefulId(value: String?, out: MutableSet<String>) {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return
        if (collectUriMessageIds(trimmed, out, strictMessageOnly = false)) return
        if (isUsefulMessageId(trimmed)) out += trimmed
    }

    private fun collectUriMessageIds(value: String?, out: MutableSet<String>, strictMessageOnly: Boolean): Boolean {
        if (value.isNullOrBlank() || !value.contains("=")) return false
        var added = false
        runCatching {
            val uri = Uri.parse(value)
            arrayOf("did", "id", "message_id", "item_id", "client_context", "client_context_id").forEach { name ->
                val queryValue = uri.getQueryParameter(name)?.trim().orEmpty()
                if (queryValue.isNotBlank()) {
                    if (strictMessageOnly) {
                        if (isMessageScopedId(queryValue, allowNumeric = true)) added = out.add(queryValue) || added
                    } else {
                        val before = out.size
                        if (isUsefulMessageId(queryValue)) out += queryValue
                        added = added || out.size != before
                    }
                }
            }
        }
        return added
    }

    private fun isMessageScopedId(value: String?, allowNumeric: Boolean): Boolean {
        if (!isUsefulMessageId(value)) return false
        val trimmed = value!!.trim()
        val lower = trimmed.lowercase(Locale.US)
        if (lower.contains("?") || lower.contains("=") || lower.contains("://")) return false
        if (lower.contains("thread") || lower.contains("inbox") || lower.contains("cursor")) return false
        if (lower.contains("viewer") || lower.contains("sender") || lower.contains("recipient")) return false
        if (isUuidLike(lower) || lower.startsWith("mid") || lower.contains("messageid")) return true
        val canonical = canonicalMessageId(lower)
        if (canonical.length < 8) return false
        val hasDigit = canonical.any { it in '0'..'9' }
        val hasLetter = canonical.any { it in 'a'..'z' }
        if (hasDigit && hasLetter && canonical.length >= 12) return true
        return allowNumeric && !hasLetter && hasDigit && canonical.length >= 10
    }

    private fun isUuidLike(value: String?): Boolean {
        if (value == null || value.length != 36) return false
        return value.withIndex().all { (index, c) ->
            if (index in setOf(8, 13, 18, 23)) c == '-' else c in '0'..'9' || c in 'a'..'f'
        }
    }

    private fun idsLikelyMatch(left: String?, right: String?): Boolean {
        if (left == null || right == null) return false
        val trimmedLeft = left.trim()
        val trimmedRight = right.trim()
        if (trimmedLeft == trimmedRight) return true
        val canonicalLeft = canonicalMessageId(trimmedLeft)
        val canonicalRight = canonicalMessageId(trimmedRight)
        if (canonicalLeft.length < 8 || canonicalRight.length < 8) return false
        if (canonicalLeft == canonicalRight) return true
        val sharedLength = minOf(canonicalLeft.length, canonicalRight.length)
        if (sharedLength >= 12 && (canonicalLeft.contains(canonicalRight) || canonicalRight.contains(canonicalLeft))) return true
        val tailLength = minOf(18, sharedLength)
        return tailLength >= 12 &&
            canonicalLeft.takeLast(tailLength) == canonicalRight.takeLast(tailLength)
    }

    private fun canonicalMessageId(value: String?): String {
        if (value == null) return ""
        return buildString(value.length) {
            value.trim().lowercase(Locale.US).forEach { c ->
                if (c in 'a'..'z' || c in '0'..'9') append(c)
            }
        }
    }

    private fun logKeepUnsentFuzzyMatchOnce() {
        if (!keepUnsentFuzzyMatchSeen) {
            keepUnsentFuzzyMatchSeen = true
            logInfo("Matched deleted Direct row with compatible message id")
        }
    }

    private fun hookKeepUnsentStoreDeleteWrappers(storeClass: Class<*>): Int {
        var hooked = 0
        storeClass.declaredMethods.forEach { method ->
            val params = method.parameterTypes
            if (method.returnType != Void.TYPE || params.size != 3 || params[1] != String::class.java || params[2] != String::class.java) {
                return@forEach
            }
            val signature = "${storeClass.name}.${method.name}:keep_unsent_store_delete_wrapper"
            if (!hookedDexMethods.add(signature)) return@forEach
            method.isAccessible = true
            XposedBridge.hookMethod(
                method,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (!state.keepUnsentMessages) return
                        if (isScopedRemoteDelete(param.args) || isOneIdServerDelete(param.args)) {
                            rememberKeepUnsentDeletedIds(collectUsefulIdsDeep(param.args))
                            param.result = null
                        }
                    }
                }
            )
            hooked++
        }
        return hooked
    }

    private fun installKeepUnsentDeletedIndicatorHooks(): Int {
        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam<*>) {
                if (!state.keepUnsentMessages || param.args == null || param.args.size < 2) return
                var rowIds = collectRowIds(param.args[0])
                var rowRoot = findViewHolderRootForKeepUnsent(param.args[1])
                if (rowRoot == null || rowIds.isEmpty()) {
                    val swappedIds = collectRowIds(param.args[1])
                    val swappedRoot = findViewHolderRootForKeepUnsent(param.args[0])
                    if (swappedRoot != null && swappedIds.isNotEmpty()) {
                        rowIds = swappedIds
                        rowRoot = swappedRoot
                    }
                }
                if (rowRoot == null || rowIds.isEmpty()) return
                loadKeepUnsentDeletedIds(rowRoot.context)
                if (keepUnsentPersistDirty) persistKeepUnsentDeletedIds(rowRoot.context)
                if (!keepUnsentRowDecoratorSeen) {
                    keepUnsentRowDecoratorSeen = true
                    logInfo("Keep-unsent deleted marker row binder active")
                }
                rememberKeepUnsentBoundRows(rowIds, rowRoot)
                applyKeepUnsentDeletedMarker(rowRoot, containsKeepUnsentDeletedId(rowIds))
            }
        }

        var hooked = 0
        val hookedMethods = ArrayList<Method>()
        fun hookRowMethod(method: Method, suffix: String): Boolean {
            val signature = "${methodKey(method)}:$suffix"
            if (!hookedDexMethods.add(signature)) return false
            return runCatching {
                method.isAccessible = true
                XposedBridge.hookMethod(method, hook)
                hookedMethods += method
                hooked++
                true
            }.onFailure { logError("Failed keep-unsent row hook $signature", it) }.getOrDefault(false)
        }

        if (InstagramDexKitCache.isCacheValid()) {
            InstagramDexKitCache.loadMethods("KeepUnsent_row_decorator", appClassLoader)
                .orEmpty()
                .filter { it.returnType == Void.TYPE && it.parameterTypes.size == 2 }
                .forEach { method -> hookRowMethod(method, "keep_unsent_row_cached") }
        }

        arrayOf("X.8Ox", "p000X.AbstractC215078Ox").forEach { className ->
            runCatching {
                val cls = Class.forName(className, false, appClassLoader)
                cls.declaredMethods.forEach { method ->
                    if (method.name != "A0J" || method.returnType != Void.TYPE || method.parameterTypes.size != 2) return@forEach
                    hookRowMethod(method, "keep_unsent_row_known")
                }
            }
        }

        val tagId = resolveInstagramRId("message_content_view_tag").takeIf { it != 0 } ?: 2131437280
        dexBridge.findMethodsUsingNumbersConstrained(
            listOf(tagId),
            paramCount = 2,
            returnType = "void"
        ).filter { it.returnType == Void.TYPE && it.parameterTypes.size == 2 }
            .forEach { method ->
                hookRowMethod(method, "keep_unsent_row_dex")
            }
        if (hookedMethods.isNotEmpty()) InstagramDexKitCache.saveMethods("KeepUnsent_row_decorator", hookedMethods)
        return hooked
    }

    private fun resolveInstagramRId(name: String): Int {
        arrayOf(
            "androidx.compose.ui.tooling.data.R\$id",
            "androidx.compose.ui.tooling.data.C0010R\$id",
            "androidx.compose.p003ui.tooling.data.C0010R\$id"
        ).forEach { className ->
            runCatching {
                val cls = Class.forName(className, false, appClassLoader)
                val field = cls.getDeclaredField(name)
                field.isAccessible = true
                return field.getInt(null)
            }
        }
        return 0
    }

    private fun collectRowIds(rowModel: Any?): Set<String> {
        val ids = HashSet<String>()
        addRowMessageId(invokeStringLikeNoArg(rowModel, "getKey"), ids, allowLooseKey = true)
        invokeNoArgDeep(rowModel, "getKey")?.takeIf { it !is String && !isPrimitiveLike(it) }?.let {
            collectRowIdentityIds(it, ids, Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()), 0)
        }
        collectRowIdentityIds(rowModel, ids, Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()), 0)
        arrayOf("BS5", "BRw", "Cj0").forEach { methodName ->
            collectRowIdentityIds(invokeNoArgDeep(rowModel, methodName), ids, Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()), 0)
        }
        collectDirectMessageGetterIds(rowModel, ids, Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()), 0)
        return ids
    }

    private fun collectRowIdentityIds(target: Any?, ids: MutableSet<String>, seen: MutableSet<Any>, depth: Int) {
        if (target == null || depth > 4) return
        when (target) {
            is String -> {
                addRowMessageId(target, ids, allowLooseKey = false)
                return
            }
            is Array<*> -> {
                target.take(24).forEach { collectRowIdentityIds(it, ids, seen, depth + 1) }
                return
            }
            is Iterable<*> -> {
                target.take(24).forEach { collectRowIdentityIds(it, ids, seen, depth + 1) }
                return
            }
            is Map<*, *> -> {
                target.values.take(24).forEach { collectRowIdentityIds(it, ids, seen, depth + 1) }
                return
            }
        }
        if (isPrimitiveLike(target) || !seen.add(target)) return
        if (isMessageIdentifierObject(target)) collectMessageIdentifierFields(target, ids)

        arrayOf("getKey", "A0o", "A0p", "A0r", "A0s").forEach { methodName ->
            val value = invokeNoArgDeep(target, methodName)
            if (value is String) {
                if (methodName == "getKey") addRowMessageId(value, ids, allowLooseKey = true) else addMessageIdentifierId(value, ids)
            } else if (value != null && !isPrimitiveLike(value)) {
                collectRowIdentityIds(value, ids, seen, depth + 1)
            }
        }
        arrayOf("BS4", "CHD", "Baq", "BX3", "A0e").forEach { methodName ->
            invokeNoArgDeep(target, methodName)?.takeIf { !isPrimitiveLike(it) }?.let {
                collectRowIdentityIds(it, ids, seen, depth + 1)
            }
        }

        var cls: Class<*>? = target.javaClass
        var classDepth = 0
        while (cls != null && cls != Any::class.java && classDepth++ < 4) {
            cls.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers) || field.isSynthetic || field.type.isPrimitive) return@forEach
                runCatching {
                    field.isAccessible = true
                    val value = field.get(target) ?: return@forEach
                    if (value is String) addRowMessageId(value, ids, allowLooseKey = false)
                    else if (shouldTraverseRowIdentityObject(value, depth)) collectRowIdentityIds(value, ids, seen, depth + 1)
                }
            }
            cls = cls.superclass
        }
    }

    private fun collectDirectMessageGetterIds(target: Any?, ids: MutableSet<String>, seen: MutableSet<Any>, depth: Int) {
        if (target == null || depth > 3 || isPrimitiveLike(target) || !seen.add(target)) return
        arrayOf("A0o", "A0p", "A0r", "A0s").forEach { addMessageIdentifierId(invokeStringLikeNoArg(target, it), ids) }
        var cls: Class<*>? = target.javaClass
        var classDepth = 0
        while (cls != null && cls != Any::class.java && classDepth++ < 4) {
            cls.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers) || field.isSynthetic || field.type.isPrimitive || field.type == String::class.java) return@forEach
                runCatching {
                    field.isAccessible = true
                    val value = field.get(target) ?: return@forEach
                    if (!isPrimitiveLike(value)) collectDirectMessageGetterIds(value, ids, seen, depth + 1)
                }
            }
            cls = cls.superclass
        }
    }

    private fun addRowMessageId(value: String?, out: MutableSet<String>, allowLooseKey: Boolean): Boolean {
        val trimmed = value?.trim().orEmpty()
        if (!isMessageScopedId(trimmed, allowNumeric = allowLooseKey)) return false
        return out.add(trimmed)
    }

    private fun addMessageIdentifierId(value: String?, out: MutableSet<String>): Boolean {
        val trimmed = value?.trim().orEmpty()
        if (!isMessageScopedId(trimmed, allowNumeric = true)) return false
        return out.add(trimmed)
    }

    private fun isMessageIdentifierObject(target: Any?): Boolean {
        val className = target?.javaClass?.name?.lowercase(Locale.US).orEmpty()
        return className.contains("messageidentifier") ||
            className.contains("directmessageidentifier") ||
            className.contains("messageid")
    }

    private fun collectMessageIdentifierFields(target: Any?, ids: MutableSet<String>) {
        var cls: Class<*>? = target?.javaClass
        var depth = 0
        while (cls != null && cls != Any::class.java && depth++ < 4) {
            cls.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers) || field.isSynthetic || field.type != String::class.java) return@forEach
                runCatching {
                    field.isAccessible = true
                    addMessageIdentifierId(field.get(target) as? String, ids)
                }
            }
            cls = cls.superclass
        }
    }

    private fun shouldTraverseRowIdentityObject(value: Any?, depth: Int): Boolean {
        if (value == null || depth >= 4 || value is View || isPrimitiveLike(value)) return false
        val className = value.javaClass.name
        if (className.startsWith("android.") ||
            className.startsWith("androidx.") ||
            className.startsWith("java.") ||
            className.startsWith("kotlin.") ||
            className.contains("UserSession")
        ) return false
        val lower = className.lowercase(Locale.US)
        return depth < 2 ||
            lower.contains("message") ||
            lower.contains("direct") ||
            lower.contains("identifier") ||
            className.startsWith("p000X.")
    }

    private fun rememberKeepUnsentBoundRows(ids: Set<String>, rowRoot: View) {
        val rowIds = extractRowBindableIds(ids)
        if (rowIds.isEmpty()) return
        if (!keepUnsentRowBindingSeen) {
            keepUnsentRowBindingSeen = true
            logInfo("Keep-unsent bound Direct row ids: ${rowIds.size}")
        }
        synchronized(keepUnsentBoundRows) {
            rowIds.forEach { keepUnsentBoundRows[it.trim()] = WeakReference(rowRoot) }
        }
    }

    private fun decorateKeepUnsentBoundRows(ids: Collection<String>) {
        val lookupIds = extractDeletedMessageIds(ids).toList()
        if (lookupIds.isEmpty()) return
        val views = ArrayList<View>()
        val seenViews = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
        val stale = ArrayList<String>()
        var usedCompatibleId = false
        synchronized(keepUnsentBoundRows) {
            lookupIds.forEach { id ->
                val view = keepUnsentBoundRows[id]?.get()
                if (view == null) stale += id else if (seenViews.add(view)) views += view
            }
            keepUnsentBoundRows.forEach { (boundId, ref) ->
                val view = ref.get()
                if (view == null) {
                    stale += boundId
                } else if (!lookupIds.contains(boundId) && lookupIds.any { idsLikelyMatch(boundId, it) } && seenViews.add(view)) {
                    views += view
                    usedCompatibleId = true
                }
            }
            stale.forEach { keepUnsentBoundRows.remove(it) }
        }
        if (usedCompatibleId) logKeepUnsentFuzzyMatchOnce()
        if (views.isEmpty() && !keepUnsentMissedDecorationSeen) {
            keepUnsentMissedDecorationSeen = true
            logInfo("Keep-unsent marker had deleted ids but no bound Direct row match yet")
        }
        views.forEach { applyKeepUnsentDeletedMarker(it, deleted = true) }
    }

    private fun findViewHolderRootForKeepUnsent(holder: Any?): View? {
        if (holder is View) return holder
        if (holder == null) return null
        findFieldDeep(holder.javaClass, "A0I")?.let { field ->
            runCatching {
                field.isAccessible = true
                (field.get(holder) as? View)?.let { return it }
            }
        }
        var cls: Class<*>? = holder.javaClass
        var depth = 0
        while (cls != null && cls != Any::class.java && depth++ < 5) {
            cls.declaredFields.forEach { field ->
                if (!View::class.java.isAssignableFrom(field.type)) return@forEach
                runCatching {
                    field.isAccessible = true
                    (field.get(holder) as? View)?.let { return it }
                }
            }
            cls = cls.superclass
        }
        return null
    }

    private fun findFieldDeep(start: Class<*>?, name: String): Field? {
        var cls = start
        var depth = 0
        while (cls != null && cls != Any::class.java && depth++ < 8) {
            runCatching { return cls.getDeclaredField(name) }
            cls = cls.superclass
        }
        return null
    }

    private fun invokeNoArgDeep(target: Any?, methodName: String): Any? {
        if (target == null) return null
        var cls: Class<*>? = target.javaClass
        var depth = 0
        while (cls != null && cls != Any::class.java && depth++ < 6) {
            cls.declaredMethods.forEach { method ->
                if (method.name == methodName && method.parameterTypes.isEmpty()) {
                    return runCatching {
                        method.isAccessible = true
                        method.invoke(target)
                    }.getOrNull()
                }
            }
            cls = cls.superclass
        }
        return null
    }

    private fun invokeStringLikeNoArg(target: Any?, methodName: String): String? = invokeNoArgDeep(target, methodName)?.toString()

    private fun applyKeepUnsentDeletedMarker(rowRoot: View, deleted: Boolean) {
        rowRoot.post {
            runCatching {
                removeKeepUnsentDeletedMarkers(rowRoot)
                restoreKeepUnsentMarkedText(rowRoot)
                if (!deleted) return@runCatching
                if (decorateKeepUnsentTextMessage(rowRoot)) return@runCatching
                val content = findKeepUnsentMessageContentView(rowRoot)
                val parent = findKeepUnsentMarkerParent(content ?: rowRoot, rowRoot) ?: return@runCatching
                removeKeepUnsentDeletedMarkers(parent)
                val marker = TextView(parent.context).apply {
                    tag = KEEP_UNSENT_MARKER_TAG
                    text = KEEP_UNSENT_MARKER_TEXT
                    setTextColor(Color.rgb(160, 166, 173))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setIncludeFontPadding(false)
                    setSingleLine(true)
                    gravity = Gravity.START
                    alpha = 0.95f
                }
                parent.addView(marker, createKeepUnsentMarkerLayoutParams(parent))
                if (!keepUnsentMarkerAppliedSeen) {
                    keepUnsentMarkerAppliedSeen = true
                    logInfo("Applied keep-unsent Message Deleted indicator")
                }
            }.onFailure { logError("Keep-unsent marker decoration failed", it) }
        }
    }

    private fun decorateKeepUnsentTextMessage(rowRoot: View): Boolean {
        val textView = findViewByResourceName(rowRoot, "direct_text_message_text_view") as? TextView ?: return false
        val current = cleanMessageDeletedLines(textView.text)
        if (current.isBlank()) return false
        val original = synchronized(keepUnsentMarkedTextOriginals) {
            val stored = keepUnsentMarkedTextOriginals[textView]?.let(::cleanMessageDeletedLines)
            val base = stored ?: current
            keepUnsentMarkedTextOriginals[textView] = base
            base
        }
        val markerPrefix = "\n"
        val text = SpannableString(original + markerPrefix + KEEP_UNSENT_MARKER_TEXT)
        val start = original.length + markerPrefix.length
        text.setSpan(ForegroundColorSpan(Color.rgb(160, 166, 173)), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(RelativeSizeSpan(0.78f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        textView.setSingleLine(false)
        textView.maxLines = Int.MAX_VALUE
        textView.text = text
        if (!keepUnsentMarkerAppliedSeen) {
            keepUnsentMarkerAppliedSeen = true
            logInfo("Applied keep-unsent Message Deleted indicator")
        }
        return true
    }

    private fun cleanMessageDeletedLines(value: CharSequence?): String {
        val text = value?.toString()?.replace("\r\n", "\n")?.replace('\r', '\n').orEmpty()
        val lines = text.split('\n')
        if (lines.size == 1) return text
        return lines.filterNot { it.trim() == KEEP_UNSENT_MARKER_TEXT }.joinToString("\n").trimEnd('\n')
    }

    private fun findKeepUnsentMessageContentView(root: View): View? {
        findViewByResourceName(root, "direct_text_message_text_view")?.let { return it }
        val tagId = root.resources.getIdentifier("message_content_view_tag", "id", root.context.packageName)
        if (tagId != 0) findTaggedView(root, tagId)?.let { return it }
        return findViewByResourceName(root, "message_content_horizontal_linear_layout") ?: root
    }

    private fun findTaggedView(view: View?, tagId: Int): View? {
        if (view == null) return null
        runCatching { if (view.getTag(tagId) != null) return view }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findTaggedView(view.getChildAt(i), tagId)?.let { return it }
            }
        }
        return null
    }

    private fun findViewByResourceName(view: View?, resourceName: String): View? {
        if (view == null) return null
        runCatching {
            val id = view.id
            if (id != View.NO_ID && view.resources.getResourceEntryName(id) == resourceName) return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findViewByResourceName(view.getChildAt(i), resourceName)?.let { return it }
            }
        }
        return null
    }

    private fun findKeepUnsentMarkerParent(content: View, rowRoot: View): ViewGroup? {
        if (content is ViewGroup && content !== rowRoot) return content
        (content.parent as? ViewGroup)?.takeIf { it !== rowRoot }?.let { return it }
        var current: View? = content
        while (current != null) {
            if (current is LinearLayout && current.orientation == LinearLayout.VERTICAL) return current
            if (current === rowRoot) break
            current = current.parent as? View
        }
        if (content is ViewGroup && content !is LinearLayout) return content
        var parent = content.parent as? View
        while (parent != null) {
            if (parent is ViewGroup && !(parent is LinearLayout && parent.orientation == LinearLayout.HORIZONTAL)) return parent
            if (parent === rowRoot) break
            parent = parent.parent as? View
        }
        return rowRoot as? ViewGroup
    }

    private fun createKeepUnsentMarkerLayoutParams(parent: ViewGroup): ViewGroup.LayoutParams {
        val top = dp(parent, 2)
        val horizontal = dp(parent, 4)
        val bottom = dp(parent, 1)
        return when (parent) {
            is LinearLayout -> LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = top
                marginStart = horizontal
                gravity = Gravity.START
            }
            is FrameLayout -> FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
                marginStart = horizontal
                bottomMargin = bottom
            }
            else -> ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = top
                marginStart = horizontal
            }
        }
    }

    private fun dp(view: View, value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), view.resources.displayMetrics).roundToInt()

    private fun removeKeepUnsentDeletedMarkers(view: View) {
        if (view !is ViewGroup) return
        for (i in view.childCount - 1 downTo 0) {
            val child = view.getChildAt(i)
            if (child.tag == KEEP_UNSENT_MARKER_TAG) view.removeViewAt(i) else removeKeepUnsentDeletedMarkers(child)
        }
    }

    private fun restoreKeepUnsentMarkedText(view: View) {
        if (view is TextView) {
            val original = synchronized(keepUnsentMarkedTextOriginals) { keepUnsentMarkedTextOriginals.remove(view) }
            if (original != null) {
                view.text = cleanMessageDeletedLines(original)
            } else {
                val cleaned = cleanMessageDeletedLines(view.text)
                if (cleaned != view.text?.toString().orEmpty()) view.text = cleaned
            }
            return
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) restoreKeepUnsentMarkedText(view.getChildAt(i))
        }
    }

    private fun installDevOptionsStableHooks() {
        hookDevOptionsUserSessionBooleans("X.2jq")
        hookDevOptionsUserSessionBooleans("p000X.C71182jq")
        if (InstagramDexKitCache.isCacheValid()) {
            InstagramDexKitCache.loadString("DevOptionsClass")?.let { className ->
                hookDevOptionsUserSessionBooleans(className)
            }
        }
        runSafe("DevOptions E2E blocker") {
            val cls = Class.forName("com.facebook.endtoend.EndToEnd", false, appClassLoader)
            cls.declaredMethods
                .filter { method ->
                    (method.returnType == java.lang.Boolean.TYPE || method.returnType == java.lang.Boolean::class.java) &&
                        method.parameterTypes.isEmpty() &&
                        (method.name == "A07" ||
                            method.name.contains("enabled", ignoreCase = true) ||
                            method.name.contains("e2e", ignoreCase = true))
                }
                .forEach { method ->
                    hookBooleanMethodOnce("DevOptionsE2E", method, false) { state.isDevEnabled }
                }
        }
        devOptionsMetaConfig.install()
        installDevOptionsDynamicHooks()
    }

    private fun installDevOptionsDynamicHooks() {
        runSafe("DevOptions dynamic discovery") {
            logInfo("DevOptions discovery tier 1 (is_employee string)")
            var found = false
            dexBridge.findClassNamesUsingStrings("is_employee")
                .filter { className -> className.startsWith("X.") || className.startsWith("p000X.") }
                .forEach { className ->
                    if (found) return@forEach
                    dexBridge.findMethodRefsInClassUsingStrings(className, "is_employee").forEach { methodRef ->
                        if (found) return@forEach
                        if (inspectDevOptionsInvokedMethods(methodRef)) found = true
                    }
                }

            if (!found) {
                logInfo("DevOptions discovery tier 2 (mobile config ID)")
                dexBridge.findMethodRefsUsingNumbers(IS_EMPLOYEE_CONFIG_ID)
                    .firstOrNull { methodRef ->
                        methodRef.returnType.contains("boolean", ignoreCase = true) && methodRef.paramTypes.size == 1
                    }
                    ?.let { methodRef ->
                        InstagramDexKitCache.saveString("DevOptionsClass", methodRef.className)
                        hookDevOptionsUserSessionBooleans(methodRef.className)
                        logInfo("DevOptions found via config ID in ${methodRef.className}")
                        found = true
                    }
            }

            if (!found) {
                logInfo("DevOptions tier 2 failed; logging global is_employee references")
                dexBridge.findMethodRefsUsingStrings("is_employee").forEach { methodRef ->
                    logInfo("DevOptionsDebug: is_employee found in ${methodRef.className}.${methodRef.name}")
                }
            }
        }
    }

    private fun inspectDevOptionsInvokedMethods(methodRef: InstagramDexKitBridge.MethodRef): Boolean {
        methodRef.invokes.forEach { invoked ->
            if (!invoked.returnType.contains("boolean", ignoreCase = true)) return@forEach
            if (invoked.paramTypes.size != 1 ||
                !invoked.paramTypes.first().contains("com.instagram.common.session.UserSession")
            ) return@forEach
            InstagramDexKitCache.saveString("DevOptionsClass", invoked.className)
            hookDevOptionsUserSessionBooleans(invoked.className)
            logInfo("DevOptions hooking via string detection: ${invoked.className}")
            return true
        }
        return false
    }

    private fun hookDevOptionsUserSessionBooleans(className: String) {
        runSafe("DevOptions class $className") {
            val cls = Class.forName(className, false, appClassLoader)
            cls.declaredMethods
                .filter { method ->
                    (method.returnType == java.lang.Boolean.TYPE || method.returnType == java.lang.Boolean::class.java) &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0].name == "com.instagram.common.session.UserSession"
                }
                .forEach { method ->
                    hookBooleanMethodOnce("DevOptionsStable", method, true) { state.isDevEnabled }
                }
        }
    }

    private fun hookBooleanMethodOnce(name: String, method: Method, result: Boolean, enabled: () -> Boolean) {
        val signature = "${method.declaringClass.name}.${method.name}:${method.parameterTypes.size}:$result"
        if (!hookedDexMethods.add(signature)) return
        runCatching {
            method.isAccessible = true
            XposedBridge.hookMethod(
                method,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (enabled()) param.result = result
                    }
                }
            )
            logInfo("Boolean hook installed: $name -> $signature")
        }.onFailure { logError("Failed boolean hook $name $signature", it) }
    }

    private fun installSponsoredModelHooks() {
        if (!sponsoredModelHooksInstalled.compareAndSet(false, true)) return
        runSafe("Sponsored model blockers") {
            hookSponsoredControllerMethods()
            val sponsoredContentClass = findFirstClass("X.D4s", "p000X.C33719D4s")
            var hooked = 0
            hooked += hookClipsSponsoredMerge(sponsoredContentClass)
            hooked += hookSponsoredResponseSink(sponsoredContentClass)
            hooked += hookFeedDataSource(sponsoredContentClass)
            hooked += hookMainFeedCache(sponsoredContentClass)
            if (hookSponsoredContentInsertItemCheck()) hooked++
            logInfo("Installed sponsored model blockers=$hooked")
        }
    }

    private fun hookSponsoredControllerMethods() {
        if (InstagramDexKitCache.isCacheValid()) {
            InstagramDexKitCache.loadMethods("AdBlockerControllerMethods", appClassLoader)
                ?.takeIf { it.isNotEmpty() }
                ?.let { cached ->
                    val hooked = hookSponsoredControllerTargets(cached)
                    logInfo("Hooked cached sponsored controller methods=$hooked")
                    return
                }
        }

        val targets = linkedSetOf<Method>()
        listOf(
            "SponsoredContentController.insertItem",
            "SponsoredContentController.processValidatedContent",
            "onSponsoredContentDelivered",
            "onInjectionRuleStatusUpdated"
        ).forEach { marker ->
            dexBridge.findMethodsUsingStrings(marker).forEach { method ->
                if (isSponsoredControllerCandidate(method)) targets += method
            }
        }
        if (targets.isNotEmpty()) {
            InstagramDexKitCache.saveMethods("AdBlockerControllerMethods", targets.toList())
        }
        val hooked = hookSponsoredControllerTargets(targets)
        if (hooked > 0) {
            logInfo("Hooked sponsored controller methods=$hooked")
        } else {
            logInfo("No sponsored controller methods found")
        }
    }

    private fun hookSponsoredControllerTargets(targets: Collection<Method>): Int {
        var hooked = 0
        targets.forEach { method ->
            val signature = "${methodKey(method)}:sponsored_controller"
            if (!hookedDexMethods.add(signature)) return@forEach
            runCatching {
                method.isAccessible = true
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam<*>) {
                            if (!state.isAdBlockEnabled) return
                            if (method.returnType == java.lang.Boolean.TYPE || method.returnType == java.lang.Boolean::class.java) {
                                param.result = false
                            } else {
                                val cleared = clearListArgs(param)
                                if (cleared > 0) {
                                    logInfo("Cleared sponsored controller list args=$cleared from ${method.declaringClass.name}.${method.name}")
                                }
                            }
                        }
                    }
                )
                hooked++
            }.onFailure { logError("Failed sponsored controller hook $signature", it) }
        }
        return hooked
    }

    private fun hookSponsoredContentInsertItemCheck(): Boolean {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam<*>) {
                if (state.isAdBlockEnabled) param.result = false
            }
        }

        if (InstagramDexKitCache.isCacheValid()) {
            InstagramDexKitCache.loadMethod("AdBlocker", appClassLoader)?.let { method ->
                val signature = "${methodKey(method)}:adblocker_cached"
                if (!hookedDexMethods.add(signature)) return false
                return runCatching {
                    XposedBridge.hookMethod(method, hook)
                    logInfo("Hooked cached SponsoredContentController.insertItem: ${method.declaringClass.name}.${method.name}")
                    true
                }.getOrElse {
                    logError("Failed cached SponsoredContentController hook $signature", it)
                    false
                }
            }
        }

        dexBridge.findMethodsUsingStrings("SponsoredContentController.insertItem")
            .firstOrNull { method -> method.returnType == java.lang.Boolean.TYPE || method.returnType == java.lang.Boolean::class.java }
            ?.let { method ->
                val signature = "${methodKey(method)}:adblocker_insert_item"
                if (!hookedDexMethods.add(signature)) return false
                return runCatching {
                    method.isAccessible = true
                    InstagramDexKitCache.saveMethod("AdBlocker", method)
                    XposedBridge.hookMethod(method, hook)
                    logInfo("Hooked SponsoredContentController.insertItem: ${method.declaringClass.name}.${method.name}")
                    true
                }.getOrElse {
                    logError("Failed SponsoredContentController.insertItem hook $signature", it)
                    false
                }
            }
        logInfo("No valid SponsoredContentController.insertItem method hooked")
        return false
    }

    private fun isSponsoredControllerCandidate(method: Method): Boolean {
        if (method.returnType == java.lang.Boolean.TYPE || method.returnType == java.lang.Boolean::class.java) return true
        return method.parameterTypes.any { List::class.java.isAssignableFrom(it) }
    }

    private fun clearListArgs(param: XC_MethodHook.MethodHookParam<*>): Int {
        var cleared = 0
        param.args?.indices?.forEach { index ->
            val arg = param.args[index]
            if (arg is List<*> && arg.isNotEmpty()) {
                param.args[index] = ArrayList<Any?>()
                cleared++
            }
        }
        return cleared
    }

    private fun hookClipsSponsoredMerge(sponsoredContentClass: Class<*>?): Int {
        val mergeClass = findFirstClass("X.4Xr", "p000X.AbstractC115434Xr") ?: return 0
        var hooked = 0
        mergeClass.declaredMethods.forEach { method ->
            val params = method.parameterTypes
            if (!Modifier.isStatic(method.modifiers) || params.size != 5 ||
                !List::class.java.isAssignableFrom(params[1]) ||
                !List::class.java.isAssignableFrom(params[2])
            ) return@forEach
            if (hookAdMethodOnce("clips merge", method) { param ->
                    sanitizeAdListArg(param, 1, sponsoredContentClass) + sanitizeAdListArg(param, 2, sponsoredContentClass)
                }
            ) hooked++
        }
        return hooked
    }

    private fun hookSponsoredResponseSink(sponsoredContentClass: Class<*>?): Int {
        val responseClass = findFirstClass("X.4Xv", "p000X.C115474Xv") ?: return 0
        var hooked = 0
        responseClass.declaredMethods.forEach { method ->
            val params = method.parameterTypes
            if (Modifier.isStatic(method.modifiers) || method.returnType != Void.TYPE || params.size != 5 ||
                params[2] != String::class.java || !List::class.java.isAssignableFrom(params[3]) ||
                params[4] != java.lang.Boolean.TYPE
            ) return@forEach
            if (hookAdMethodOnce("sponsored response", method) { param -> sanitizeAdListArg(param, 3, sponsoredContentClass) }) hooked++
        }
        return hooked
    }

    private fun hookFeedDataSource(sponsoredContentClass: Class<*>?): Int {
        val dataSourceClass = findFirstClass("X.BXf", "p000X.AbstractC29367BXf") ?: return 0
        var hooked = 0
        dataSourceClass.declaredMethods.forEach { method ->
            val params = method.parameterTypes
            if (method.returnType != Void.TYPE) return@forEach
            when {
                Modifier.isStatic(method.modifiers) && method.name == "A03" && params.size == 5 && params[0] == dataSourceClass -> {
                    if (hookAdMethodOnce("feed datasource single", method) { param ->
                            if (isAdPayload(param.args.getOrNull(2), sponsoredContentClass)) {
                                param.result = null
                                1
                            } else 0
                        }
                    ) hooked++
                }
                Modifier.isStatic(method.modifiers) && method.name == "A04" && params.size == 2 &&
                    params[0] == dataSourceClass && List::class.java.isAssignableFrom(params[1]) -> {
                    if (hookAdMethodOnce("feed datasource snapshot", method) { param -> sanitizeAdListArg(param, 1, sponsoredContentClass) }) hooked++
                }
                !Modifier.isStatic(method.modifiers) && method.name == "A0D" && params.size == 1 -> {
                    if (hookAdMethodOnce("feed datasource append single", method) { param ->
                            if (isAdPayload(param.args.firstOrNull(), sponsoredContentClass)) {
                                param.result = null
                                1
                            } else 0
                        }
                    ) hooked++
                }
                !Modifier.isStatic(method.modifiers) && method.name == "A0E" && params.size == 1 &&
                    List::class.java.isAssignableFrom(params[0]) -> {
                    if (hookAdMethodOnce("feed datasource append", method) { param -> sanitizeAdListArg(param, 0, sponsoredContentClass) }) hooked++
                }
            }
        }
        return hooked
    }

    private fun hookMainFeedCache(sponsoredContentClass: Class<*>?): Int {
        val cacheClass = findFirstClass("com.instagram.mainfeed.network.MainFeedCacheDataSource") ?: return 0
        var hooked = 0
        cacheClass.declaredMethods.forEach { method ->
            val params = method.parameterTypes
            if (method.returnType != Void.TYPE || Modifier.isStatic(method.modifiers)) return@forEach
            when {
                method.name == "A0G" && params.size == 5 && params[0] == String::class.java &&
                    List::class.java.isAssignableFrom(params[1]) -> {
                    if (hookAdMethodOnce("main feed cache list", method) { param -> sanitizeAdListArg(param, 1, sponsoredContentClass) }) hooked++
                }
                method.name == "A0E" && params.size == 1 && isInstagramFeedItemClass(params[0].name) -> {
                    if (hookAdMethodOnce("main feed cache single", method) { param ->
                            if (isAdPayload(param.args.firstOrNull(), sponsoredContentClass)) {
                                param.result = null
                                1
                            } else 0
                        }
                    ) hooked++
                }
            }
        }
        return hooked
    }

    private fun hookAdMethodOnce(name: String, method: Method, action: (XC_MethodHook.MethodHookParam<*>) -> Int): Boolean {
        val signature = "${methodKey(method)}:$name"
        if (!hookedDexMethods.add(signature)) return false
        return runCatching {
            method.isAccessible = true
            XposedBridge.hookMethod(
                method,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (!state.isAdBlockEnabled) return
                        val removed = action(param)
                        if (removed > 0) logInfo("Stripped $removed sponsored item(s) from $name")
                    }
                }
            )
            true
        }.getOrElse {
            logError("Failed ad method hook $signature", it)
            false
        }
    }

    private fun sanitizeAdListArg(param: XC_MethodHook.MethodHookParam<*>, argIndex: Int, sponsoredContentClass: Class<*>?): Int {
        val args = param.args ?: return 0
        if (argIndex !in args.indices) return 0
        val collection = args[argIndex] as? Collection<*> ?: return 0
        if (collection.isEmpty()) return 0
        val kept = ArrayList<Any?>()
        var removed = 0
        collection.forEach { item ->
            if (isAdPayload(item, sponsoredContentClass)) removed++ else kept += item
        }
        if (removed > 0) args[argIndex] = kept
        return removed
    }

    private fun isAdPayload(item: Any?, sponsoredContentClass: Class<*>?): Boolean {
        if (item == null) return false
        if (isSponsoredContentObject(item, sponsoredContentClass)) return true
        if (isAdMediaObject(item)) return true
        if (isInstagramFeedItemClass(item.javaClass.name)) return isAdMediaObject(invokeNoArg(item, "A05"))
        if (isInstagramClipsItemClass(item.javaClass.name)) {
            if (invokeBooleanNoArg(item, "Dwa")) return true
            val media = invokeNoArg(item, "A06")
            if (isAdMediaObject(media)) return true
            return isAdMediaObject(getFieldValue(item, "A0T"))
        }
        return false
    }

    private fun isSponsoredContentObject(item: Any, sponsoredContentClass: Class<*>?): Boolean {
        if (sponsoredContentClass?.isInstance(item) == true) return true
        val className = item.javaClass.name
        return className == "X.D4s" || className == "p000X.C33719D4s"
    }

    private fun isAdMediaObject(item: Any?): Boolean = item != null && isInstagramObject(item) && invokeBooleanNoArg(item, "Dwa")

    private fun isInstagramFeedItemClass(className: String): Boolean = className == "X.5hx" || className == "p000X.C146455hx"

    private fun isInstagramClipsItemClass(className: String): Boolean = className == "X.5gB" || className == "p000X.C145355gB"

    private fun isInstagramObject(item: Any): Boolean {
        val className = item.javaClass.name
        return className.startsWith("X.") || className.startsWith("p000X.") || className.startsWith("com.instagram.")
    }

    private fun invokeNoArg(target: Any?, methodName: String): Any? {
        if (target == null) return null
        return runCatching {
            val method = target.javaClass.getDeclaredMethod(methodName)
            method.isAccessible = true
            method.invoke(target)
        }.getOrNull()
    }

    private fun invokeBooleanNoArg(target: Any?, methodName: String): Boolean = invokeNoArg(target, methodName) as? Boolean ?: false

    private fun getFieldValue(target: Any?, fieldName: String): Any? {
        if (target == null) return null
        return runCatching {
            val field = target.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.get(target)
        }.getOrNull()
    }

    private fun findFirstClass(vararg names: String): Class<*>? {
        names.forEach { name ->
            runCatching { Class.forName(name, false, appClassLoader) }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun methodKey(method: Method): String {
        return buildString {
            append(method.declaringClass.name)
            append('#')
            append(method.name)
            append('(')
            method.parameterTypes.forEach { append(it.name).append(',') }
            append("):")
            append(method.returnType.name)
        }
    }

    private fun installNotesLocationFallbackHook() {
        runSafe("Notes location known-class fallback") {
            val cls = Class.forName(
                "com.instagram.direct.inbox.notes.data.repository.GraphqlOptimisticPostOperation",
                false,
                appClassLoader
            )
            cls.declaredMethods
                .filter { it.parameterTypes.isNotEmpty() }
                .forEach { method ->
                    val signature = "${method.declaringClass.name}.${method.name}:notes-fallback"
                    if (!hookedDexMethods.add(signature)) return@forEach
                    method.isAccessible = true
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam<*>) {
                                if (state.enableNotesLocationSpoof) applyNotesLocationSpoof(param)
                            }
                        }
                    )
                }
        }
    }

    private fun installPostDownloadMenuHook() {
        runSafe("Post download menu hook") {
            val optionClassName = "com.instagram.feed.media.mediaoption.MediaOption\$Option"
            val optionClass = Class.forName(optionClassName, false, appClassLoader)
            val downloadOption = optionClass.enumConstants?.firstOrNull { it.toString() == "DOWNLOAD" }
                ?: return@runSafe
            var addButtonMethod: Method? = null
            var creatorClass: Class<*>? = null
            var enumIndex = 0
            var optionIndex = 1
            var selfIndex = 2
            var textIndex = 3
            var listIndex = 4
            fun acceptAddButtonCandidate(method: Method, ownerClass: Class<*>? = null): Boolean {
                if (!Modifier.isStatic(method.modifiers) || method.returnType != java.lang.Void.TYPE) return false
                val methodParams = method.parameterTypes
                if (methodParams.size < 4) return false
                val foundOption = methodParams.indexOfFirst { it == optionClass }
                val foundList = methodParams.indexOfFirst { java.util.ArrayList::class.java.isAssignableFrom(it) }
                if (foundOption < 0 || foundList < 0) return false
                addButtonMethod = method.apply { isAccessible = true }
                creatorClass = ownerClass ?: method.declaringClass
                optionIndex = foundOption
                listIndex = foundList
                selfIndex = methodParams.indexOfFirst { it == creatorClass }.takeIf { it >= 0 }
                    ?: 2.coerceAtMost(methodParams.lastIndex)
                textIndex = methodParams.indexOfFirst { CharSequence::class.java.isAssignableFrom(it) }.takeIf { it >= 0 }
                    ?: 3.coerceAtMost(methodParams.lastIndex)
                enumIndex = methodParams.indices.firstOrNull { methodParams[it].isEnum && it != optionIndex }
                    ?: 0
                InstagramDexKitCache.saveMethod("PostDownload_addButton", method)
                InstagramDexKitCache.saveString(
                    "PostDownload_addButtonIdx",
                    "$enumIndex,$optionIndex,$selfIndex,$textIndex,$listIndex"
                )
                return true
            }

            if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadMethod("PostDownload_addButton", appClassLoader)?.let { cached ->
                    addButtonMethod = cached.apply { isAccessible = true }
                    creatorClass = cached.declaringClass
                    InstagramDexKitCache.loadString("PostDownload_addButtonIdx")
                        ?.split(',')
                        ?.takeIf { it.size == 5 }
                        ?.mapNotNull { it.toIntOrNull() }
                        ?.takeIf { it.size == 5 }
                        ?.let { indices ->
                            enumIndex = indices[0]
                            optionIndex = indices[1]
                            selfIndex = indices[2]
                            textIndex = indices[3]
                            listIndex = indices[4]
                        }
                    logInfo("Loaded cached post download add-button hook ${cached.declaringClass.name}.${cached.name}")
                }
            }

            if (addButtonMethod == null) {
                val creatorClassNames = dexBridge.findClassNamesUsingStrings("MediaOptionsOverflowMenuCreator").distinct()
                creatorClassNames.forEach { creatorClassName ->
                    if (addButtonMethod != null) return@forEach
                    val cls = runCatching { Class.forName(creatorClassName, false, appClassLoader) }
                        .onFailure { logError("Post download creator class $creatorClassName", it) }
                        .getOrNull()
                        ?: return@forEach
                    cls.declaredMethods.firstOrNull { method -> acceptAddButtonCandidate(method, cls) }
                }
                if (addButtonMethod == null) {
                    dexBridge.findMethodsByParamTypes("void", optionClassName)
                        .firstOrNull { method -> acceptAddButtonCandidate(method, method.declaringClass) }
                }
            }

            val resolvedAddButtonMethod = addButtonMethod ?: run {
                logInfo("Post download add-button method not found")
                return@runSafe
            }
            val resolvedCreatorClass = creatorClass ?: resolvedAddButtonMethod.declaringClass
            val params = resolvedAddButtonMethod.parameterTypes
            if (optionIndex !in params.indices || params[optionIndex] != optionClass) {
                logInfo("Post download add-button option parameter mismatch")
                return@runSafe
            }
            if (listIndex !in params.indices || !java.util.ArrayList::class.java.isAssignableFrom(params[listIndex])) {
                logInfo("Post download add-button list parameter mismatch")
                return@runSafe
            }
            if (selfIndex !in params.indices) selfIndex = 2.coerceAtMost(params.lastIndex)
            if (textIndex !in params.indices) textIndex = 3.coerceAtMost(params.lastIndex)
            if (enumIndex !in params.indices || !params[enumIndex].isEnum) {
                enumIndex = params.indices.firstOrNull { params[it].isEnum && it != optionIndex } ?: run {
                    logInfo("Post download add-button enum parameter not found")
                    return@runSafe
                }
            }
            val buttonEnumValue = params[enumIndex].enumConstants?.firstOrNull { it.toString().equals("normal", true) }
                ?: params[enumIndex].enumConstants?.firstOrNull { it.toString().equals("action", true) }
                ?: params[enumIndex].enumConstants?.firstOrNull()
                ?: run {
                    logInfo("Post download add-button enum value not found")
                    return@runSafe
                }
            val addingDownload = ThreadLocal<Boolean>()
            val processedCreators = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Any, Boolean>()))

            XposedBridge.hookMethod(
                resolvedAddButtonMethod,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (addingDownload.get() == true || !state.enablePostDownload) return
                        if (param.args.getOrNull(optionIndex) == downloadOption) param.result = null
                    }

                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (addingDownload.get() == true || !state.enablePostDownload) return
                        if (param.args.getOrNull(optionIndex) == downloadOption) return
                        val creator = param.args.getOrNull(selfIndex)
                            ?: param.args.getOrNull(listIndex)
                            ?: param.args
                        if (param.args.getOrNull(selfIndex) != null &&
                            !resolvedCreatorClass.isInstance(param.args.getOrNull(selfIndex)) &&
                            selfIndex != 2.coerceAtMost(params.lastIndex)
                        ) return
                        synchronized(processedCreators) {
                            if (!processedCreators.add(creator)) return
                        }
                        val callArgs = param.args.copyOf(resolvedAddButtonMethod.parameterCount)
                        callArgs[enumIndex] = buttonEnumValue
                        callArgs[optionIndex] = downloadOption
                        callArgs[textIndex] = "Download"
                        addingDownload.set(true)
                        try {
                            resolvedAddButtonMethod.invoke(null, *callArgs)
                        } finally {
                            addingDownload.remove()
                        }
                    }
                }
            )

            val clickHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    if (!state.enablePostDownload || addingDownload.get() == true) return
                    val clicked = param.args.firstOrNull { optionClass.isInstance(it) }
                        ?: param.args.firstOrNull { it?.javaClass?.isEnum == true && it.toString().contains("DOWNLOAD") }
                        ?: return
                    if (clicked != downloadOption && clicked.toString() != "DOWNLOAD") return
                    param.result = null
                    val context = findContextInObjectGraph(param.thisObject) ?: currentActivity ?: androidContext
                    val media = findMediaObject(param.thisObject)
                    val urls = media?.let { extractAllMediaDownloadUrls(context, it) }
                        .orEmpty()
                        .ifEmpty { collectCdnUrls(media ?: param.thisObject).take(1) }
                    if (urls.isEmpty()) {
                        Toast.makeText(context, "No media found for post", Toast.LENGTH_SHORT).show()
                        return
                    }
                    val downloadContext = downloadContextFrom(media ?: param.thisObject, "post")
                    val carouselIndex = findObjectCarouselIndex(param.thisObject, urls.size)
                    mainHandler.post { showPostDownloadChoices(context, urls.distinct(), downloadContext, carouselIndex) }
                }
            }

            val clickMethods = InstagramDexKitCache.loadMethods("PostDownload_click", appClassLoader)
                ?.takeIf { InstagramDexKitCache.isCacheValid() && it.isNotEmpty() }
                ?: dexBridge.findMethodsByParamTypes("void", optionClassName)
                    .filterNot { Modifier.isStatic(it.modifiers) }
                    .also { if (it.isNotEmpty()) InstagramDexKitCache.saveMethods("PostDownload_click", it) }

            clickMethods.forEach { method ->
                    method.isAccessible = true
                    val signature = "${method.declaringClass.name}.${method.name}:${method.parameterTypes.size}"
                    if (!hookedDexMethods.add("PostDownloadClick:$signature")) return@forEach
                    XposedBridge.hookMethod(method, clickHook)
                }
            logInfo("Installed post download menu hook on ${resolvedAddButtonMethod.declaringClass.name}.${resolvedAddButtonMethod.name}; clickHandlers=${clickMethods.size}")
        }
    }

    private fun installReelDownloadMenuHook() {
        runSafe("Reel download menu hook") {
            val mediaClassName = "com.instagram.feed.media.Media"
            val optionsMethod = InstagramDexKitCache.loadMethod("ReelDownload", appClassLoader)
                ?.takeIf { InstagramDexKitCache.isCacheValid() }
                ?: run {
                    val controllerClass = dexBridge.findMethodsUsingStrings("ClipsOrganicMediaItemViewMoreOptionsController")
                        .firstOrNull()
                        ?.declaringClass
                        ?: return@runSafe
                    controllerClass.declaredMethods.firstOrNull { method ->
                        method.returnType == java.lang.Void.TYPE &&
                            method.parameterTypes.size >= 2 &&
                            method.parameterTypes[0].name == mediaClassName &&
                            !method.parameterTypes[1].isPrimitive &&
                            method.parameterTypes[1] != String::class.java
                    }?.also { InstagramDexKitCache.saveMethod("ReelDownload", it) }
                }
                ?: return@runSafe
            optionsMethod.isAccessible = true
            var buttonAdderMethod: Method? = null
            var activityField: Field? = null

            XposedBridge.hookMethod(
                optionsMethod,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (!state.enableReelDownload && !state.enableReelThumbnailDownload) return
                        val controller = param.thisObject ?: return
                        val media = param.args.getOrNull(0) ?: return
                        val buttonAdder = param.args.getOrNull(1) ?: return

                        if (activityField == null) {
                            activityField = controller.javaClass.declaredFields.firstOrNull { Activity::class.java.isAssignableFrom(it.type) }?.apply {
                                isAccessible = true
                            }
                        }
                        val activity = runCatching { activityField?.get(controller) as? Activity }.getOrNull()
                            ?: currentActivity
                            ?: return
                        if (buttonAdderMethod == null) {
                            buttonAdderMethod = buttonAdder.javaClass.declaredMethods.firstOrNull { method ->
                                val p = method.parameterTypes
                                p.size == 4 &&
                                    Context::class.java.isAssignableFrom(p[0]) &&
                                    View.OnClickListener::class.java.isAssignableFrom(p[1]) &&
                                    p[2] == String::class.java &&
                                    p[3] == java.lang.Integer.TYPE
                            }?.apply { isAccessible = true }
                        }
                        val addButton = buttonAdderMethod ?: return
                        val click = View.OnClickListener {
                            handleReelDownloadClick(activity, media, controller)
                        }
                        runCatching { addButton.invoke(buttonAdder, activity, click, "Download", resolveMediaOptionDownloadIcon(activity)) }
                    }
                }
            )
            logInfo("Installed reel download menu hook on ${optionsMethod.declaringClass.name}.${optionsMethod.name}")
        }
    }

    private fun installStoryOverflowHooks() {
        runSafe("Story overflow options hook") {
            initStoryVideoVersionReflection()
            val helpers = linkedSetOf<Class<*>>()
            listOf("X.C8BD", "X.8BD", "p000X.C8BD").forEach { className ->
                runCatching { Class.forName(className, false, appClassLoader) }.getOrNull()?.let { helpers += it }
            }
            InstagramDexKitCache.loadMethod("StoryDownload_button", appClassLoader)
                ?.takeIf { InstagramDexKitCache.isCacheValid() }
                ?.declaringClass
                ?.let { helpers += it }
            dexBridge.findMethodsUsingStrings("[INTERNAL] Pause Playback").forEach { helpers += it.declaringClass }

            var arrayHooks = 0
            var arrayParamHooks = 0
            var clickHooks = 0
            helpers.forEach { helper ->
                helper.declaredMethods.forEach { method ->
                    method.isAccessible = true
                    if (method.returnType.isArray &&
                        CharSequence::class.java.isAssignableFrom(method.returnType.componentType)
                    ) {
                        val signature = "${method.declaringClass.name}.${method.name}:story_options_array"
                        if (hookedDexMethods.add(signature)) {
                            XposedBridge.hookMethod(
                                method,
                                object : XC_MethodHook() {
                                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                                        if (!shouldOfferAnyStoryOption()) return
                                        val original = param.result as? Array<*> ?: return
                                        val patched = appendStoryOptions(original.filterIsInstance<CharSequence>().toTypedArray())
                                        if (patched !== original) param.result = patched
                                    }
                                }
                            )
                            arrayHooks++
                            InstagramDexKitCache.saveMethod("StoryDownload_button", method)
                        }
                    }
                    if (method.parameterTypes.any { it.isArray && CharSequence::class.java.isAssignableFrom(it.componentType) }) {
                        val signature = "${method.declaringClass.name}.${method.name}:story_options_array_param"
                        if (hookedDexMethods.add(signature)) {
                            XposedBridge.hookMethod(
                                method,
                                object : XC_MethodHook() {
                                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                                        if (!shouldOfferAnyStoryOption()) return
                                        param.args?.indices?.forEach { index ->
                                            val original = param.args[index] as? Array<*> ?: return@forEach
                                            if (!original.all { it is CharSequence }) return@forEach
                                            val patched = appendStoryOptions(original.filterIsInstance<CharSequence>().toTypedArray())
                                            if (patched !== original) param.args[index] = patched
                                        }
                                    }
                                }
                            )
                            arrayParamHooks++
                        }
                    }
                    if (method.returnType == Void.TYPE && method.parameterTypes.any { CharSequence::class.java.isAssignableFrom(it) }) {
                        val signature = "${method.declaringClass.name}.${method.name}:story_option_click"
                        if (hookedDexMethods.add(signature)) {
                            XposedBridge.hookMethod(
                                method,
                                object : XC_MethodHook() {
                                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                                        handleStoryOptionClick(param)
                                    }
                                }
                            )
                            clickHooks++
                        }
                    }
                }
            }

            val directClickMethods = InstagramDexKitCache.loadMethod("StoryDownload_click", appClassLoader)
                ?.takeIf { InstagramDexKitCache.isCacheValid() }
                ?.let(::listOf)
                ?: dexBridge.findMethodsUsingStrings("explore_viewer", "friendships/mute_friend_reel/%s/", "[INTERNAL] Pause Playback")
                    .filter { it.returnType == Void.TYPE }
                    .also { methods -> methods.firstOrNull()?.let { InstagramDexKitCache.saveMethod("StoryDownload_click", it) } }

            directClickMethods
                .forEach { method ->
                    val signature = "${method.declaringClass.name}.${method.name}:story_download_click"
                    if (!hookedDexMethods.add(signature)) return@forEach
                    method.isAccessible = true
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam<*>) {
                                handleStoryOptionClick(param)
                            }
                        }
                    )
                    clickHooks++
                }

            logInfo("Installed story overflow hooks arrays=$arrayHooks arrayParams=$arrayParamHooks clicks=$clickHooks helpers=${helpers.size}")
        }
    }

    private fun shouldOfferMarkSeenStoryOption(): Boolean {
        return state.enableStoryMarkSeenButton || state.enableStoryDownload
    }

    private fun shouldOfferRepostStoryOption(): Boolean {
        return state.enableStoryRepostButton || state.enableStoryDownload
    }

    private fun shouldOfferAnyStoryOption(): Boolean {
        return state.enableStoryDownload || shouldOfferMarkSeenStoryOption() || shouldOfferRepostStoryOption()
    }

    private fun initStoryVideoVersionReflection() {
        if (storyVideoVersionIntfClass != null && storyVideoVersionGetUrl != null) return
        runCatching {
            val cls = Class.forName("com.instagram.model.mediasize.VideoVersionIntf", false, appClassLoader)
            storyVideoVersionIntfClass = cls
            storyVideoVersionGetUrl = cls.getMethod("getUrl")
        }
    }

    private fun initMediaDownloadReflection() {
        if (mediaClass != null && mutableMediaDictIntfClass != null && mediaCarouselCandidates.isNotEmpty()) return
        runCatching {
            val resolvedMedia = Class.forName("com.instagram.feed.media.Media", false, appClassLoader)
            mediaClass = resolvedMedia
            val mediaExt = Class.forName("com.instagram.feed.media.MediaExtKt", false, appClassLoader)
            mediaImageUrlMethod = mediaExt.declaredMethods.firstOrNull { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.size == 2 &&
                    Context::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                    method.parameterTypes[1] == resolvedMedia
            }?.apply { isAccessible = true }
        }.onFailure { logError("Could not resolve Instagram MediaExt download helpers", it) }

        runCatching {
            val dictClass = Class.forName("com.instagram.feed.media.MutableMediaDictIntf", false, appClassLoader)
            mutableMediaDictIntfClass = dictClass
            val seen = linkedSetOf<String>()
            fun collect(owner: Class<*>) {
                owner.declaredMethods
                    .filter { method ->
                        method.parameterTypes.isEmpty() &&
                            List::class.java.isAssignableFrom(method.returnType) &&
                            seen.add(method.name)
                    }
                    .forEach { method ->
                        method.isAccessible = true
                        mediaCarouselCandidates += method
                    }
            }
            collect(dictClass)
            dictClass.interfaces
                .filter { it.name.startsWith("X.") || it.name.startsWith("p000X.") || it.name.startsWith("com.instagram.") || it.name.startsWith("com.facebook.") }
                .forEach(::collect)
        }.onFailure { logError("Could not resolve Instagram mutable media download helpers", it) }
    }

    private fun resolveUsernameGetter() {
        if (userClass != null && userUsernameGetter != null) return
        if (InstagramDexKitCache.isCacheValid()) {
            val cachedClass = InstagramDexKitCache.loadString("UserClass")
            val cachedGetter = InstagramDexKitCache.loadMethod("UsernameGetter", appClassLoader)
            if (!cachedClass.isNullOrBlank()) {
                runCatching { Class.forName(cachedClass, false, appClassLoader) }.getOrNull()?.let { cached ->
                    userClass = cached
                    userUsernameGetter = cachedGetter?.apply { isAccessible = true }
                    if (userUsernameGetter != null) return
                }
            }
        }

        val resolvedUserClass = dexBridge.findMethodsUsingStrings("username_missing_during_update")
            .firstOrNull()
            ?.declaringClass
            ?: runCatching { Class.forName("com.instagram.user.model.User", false, appClassLoader) }.getOrNull()
            ?: return
        userClass = resolvedUserClass
        InstagramDexKitCache.saveString("UserClass", resolvedUserClass.name)

        val getter = dexBridge.findMethodsUsingNumbers(-265713450)
            .firstOrNull { method ->
                method.declaringClass.name == "com.instagram.user.model.User" &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.isEmpty()
            }
            ?: resolvedUserClass.declaredMethods.firstOrNull { method ->
                method.name == "getUsername" &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.isEmpty()
            }
        getter?.let { method ->
            method.isAccessible = true
            userUsernameGetter = method
            InstagramDexKitCache.saveMethod("UsernameGetter", method)
        }
    }

    private fun appendStoryOptions(original: Array<CharSequence>): Array<CharSequence> {
        if (!shouldOfferAnyStoryOption()) return original
        val labels = original.toMutableList()
        fun addOnce(enabled: Boolean, label: String) {
            if (enabled && labels.none { it.contentEquals(label) }) labels += label
        }
        addOnce(state.enableStoryDownload, STORY_OPTION_DOWNLOAD)
        addOnce(shouldOfferMarkSeenStoryOption(), STORY_OPTION_MARK_SEEN)
        addOnce(shouldOfferRepostStoryOption(), STORY_OPTION_REPOST)
        return if (labels.size == original.size) original else labels.toTypedArray()
    }

    private fun handleStoryOptionClick(param: XC_MethodHook.MethodHookParam<*>) {
        if (!shouldOfferAnyStoryOption()) return
        val tapped = param.args?.firstOrNull { it is CharSequence } as? CharSequence ?: return
        val isDownload = state.enableStoryDownload && tapped.contentEquals(STORY_OPTION_DOWNLOAD)
        val isMarkSeen = shouldOfferMarkSeenStoryOption() && tapped.contentEquals(STORY_OPTION_MARK_SEEN)
        val isRepost = shouldOfferRepostStoryOption() && tapped.contentEquals(STORY_OPTION_REPOST)
        if (!isDownload && !isMarkSeen && !isRepost) return

        param.result = null
        val holder = findReelItemHolder(param) ?: param.thisObject
        val context = findContextInObjectGraph(holder) ?: findContextInObjectGraph(param.thisObject) ?: currentActivity ?: androidContext

        if (isMarkSeen) {
            val ok = markStorySeen(holder, param.thisObject, param.args)
            Toast.makeText(context, if (ok) "Story marked as seen" else "Could not mark story as seen", Toast.LENGTH_SHORT).show()
            return
        }

        val url = extractStoryUrl(holder) ?: run {
            Toast.makeText(context, "Story media URL not found", Toast.LENGTH_SHORT).show()
            return
        }
        val isVideo = mimeTypeForUrl(url).startsWith("video")
        val metadata = downloadContextFrom(holder, "story")
        if (isRepost) {
            Toast.makeText(context, "Preparing story repost", Toast.LENGTH_SHORT).show()
            repostStoryMedia(context, url, isVideo)
        } else {
            Toast.makeText(context, if (isVideo) "Downloading story video" else "Downloading story photo", Toast.LENGTH_SHORT).show()
            enqueueDownload(url, metadata)
        }
    }

    private fun findReelItemHolder(param: XC_MethodHook.MethodHookParam<*>): Any? {
        if (hasFieldByTypeName(param.thisObject, "com.instagram.model.reels.ReelItem")) return param.thisObject
        param.args?.forEach { arg ->
            if (hasFieldByTypeName(arg, "com.instagram.model.reels.ReelItem")) return arg
        }
        return null
    }

    private fun hasFieldByTypeName(value: Any?, typeName: String): Boolean = readFieldByTypeName(value, typeName) != null

    private fun readFieldByTypeName(value: Any?, typeName: String): Any? {
        if (value == null) return null
        if (value.javaClass.name == typeName) return value
        var cls: Class<*>? = value.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                if (field.type.name == typeName) {
                    return runCatching {
                        field.isAccessible = true
                        field.get(value)
                    }.getOrNull()
                }
            }
            cls = cls.superclass
        }
        return null
    }

    private fun extractStoryUrl(holder: Any?): String? {
        if (holder == null) return null
        val reelItem = readFieldByTypeName(holder, "com.instagram.model.reels.ReelItem")
        val target = reelItem ?: holder
        findStoryVideoUrl(target, Collections.newSetFromMap(IdentityHashMap()), 0)?.let { return it }

        val candidates = mutableListOf<StoryImageCandidate>()
        collectStoryImageCandidates(target, candidates, Collections.newSetFromMap(IdentityHashMap()), 0)
        if (candidates.isNotEmpty()) return candidates.maxByOrNull { it.area }?.url

        val urls = collectCdnUrls(target)
        return pickBestStoryUrl(urls)
    }

    private fun pickBestStoryUrl(urls: List<String>): String? {
        val filtered = urls.filter { looksLikeMediaUrl(it) && !looksLikeProfileImageUrl(it) }.distinct()
        filtered.firstOrNull { mimeTypeForUrl(it).startsWith("video") }?.let { return it }
        return filtered.maxByOrNull { parseCdnArea(it) } ?: filtered.firstOrNull()
    }

    private fun parseCdnArea(url: String): Int {
        var max = 0
        var index = 0
        while (index < url.length) {
            if (!url[index].isDigit()) {
                index++
                continue
            }
            val start = index
            while (index < url.length && url[index].isDigit()) index++
            if (index >= url.length || url[index] != 'x') continue
            val mid = index
            index++
            val heightStart = index
            while (index < url.length && url[index].isDigit()) index++
            val width = url.substring(start, mid).toIntOrNull() ?: continue
            val height = url.substring(heightStart, index).toIntOrNull() ?: continue
            max = max.coerceAtLeast(width * height)
        }
        return max
    }

    private data class StoryImageCandidate(val url: String, val area: Int)

    private fun findStoryVideoUrl(value: Any?, visited: MutableSet<Any>, depth: Int): String? {
        if (value == null || depth > 5 || !visited.add(value)) return null
        val videoClass = storyVideoVersionIntfClass
        val getUrl = storyVideoVersionGetUrl
        if (videoClass == null || getUrl == null) return null

        if (videoClass.isInstance(value)) {
            val url = runCatching { getUrl.invoke(value) as? String }.getOrNull()
            if (url != null && isStoryCdnUrl(url) && isStoryVideoUrl(url)) return url
        }

        if (!shouldInspectObject(value.javaClass)) return null

        var cls: Class<*>? = value.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                runCatching {
                    field.isAccessible = true
                    val nested = field.get(value) ?: return@forEach
                    if (nested is Iterable<*>) {
                        nested.forEach { item ->
                            if (item != null && videoClass.isInstance(item)) {
                                val url = runCatching { getUrl.invoke(item) as? String }.getOrNull()
                                if (url != null && isStoryCdnUrl(url) && isStoryVideoUrl(url)) return url
                            }
                        }
                    } else if (shouldInspectObject(nested.javaClass)) {
                        findStoryVideoUrl(nested, visited, depth + 1)?.let { return it }
                    }
                }
            }
            cls = cls.superclass
        }
        return null
    }

    private fun collectStoryImageCandidates(value: Any?, out: MutableList<StoryImageCandidate>, visited: MutableSet<Any>, depth: Int) {
        if (value == null || depth > 7 || out.size >= 40 || !visited.add(value)) return
        if (!shouldInspectObject(value.javaClass)) return

        var candidateUrl: String? = null
        val dims = mutableListOf<Int>()

        fun acceptDimension(raw: Long) {
            if (raw in 50L..20_000L) dims += raw.toInt()
        }

        var cls: Class<*>? = value.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                runCatching {
                    field.isAccessible = true
                    when (field.type) {
                        String::class.java -> {
                            val url = field.get(value) as? String
                            if (url != null && isStoryCdnUrl(url) && !isStoryVideoUrl(url)) candidateUrl = url
                        }
                        java.lang.Integer.TYPE -> acceptDimension(field.getInt(value).toLong())
                        java.lang.Long.TYPE -> acceptDimension(field.getLong(value))
                    }
                }
            }
            cls = cls.superclass
        }

        cls = value.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredMethods
                .filter { it.parameterTypes.isEmpty() }
                .forEach { method ->
                    runCatching {
                        method.isAccessible = true
                        when (method.returnType) {
                            String::class.java -> {
                                val url = method.invoke(value) as? String
                                if (url != null && isStoryCdnUrl(url) && !isStoryVideoUrl(url) && candidateUrl == null) {
                                    candidateUrl = url
                                }
                            }
                            java.lang.Integer.TYPE -> acceptDimension((method.invoke(value) as? Int)?.toLong() ?: return@runCatching)
                            java.lang.Long.TYPE -> acceptDimension(method.invoke(value) as? Long ?: return@runCatching)
                        }
                    }
                }
            cls = cls.superclass
        }

        if (candidateUrl != null && dims.size >= 2) {
            val area = dims.sortedDescending().take(2).fold(1) { acc, dim -> acc * dim }
            out += StoryImageCandidate(candidateUrl.orEmpty(), area)
            return
        }

        cls = value.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                runCatching {
                    field.isAccessible = true
                    val nested = field.get(value) ?: return@forEach
                    when (nested) {
                        is String -> return@forEach
                        is Iterable<*> -> nested.forEach { collectStoryImageCandidates(it, out, visited, depth + 1) }
                        else -> if (shouldInspectObject(nested.javaClass)) collectStoryImageCandidates(nested, out, visited, depth + 1)
                    }
                }
            }
            cls = cls.superclass
        }
    }

    private fun isStoryCdnUrl(url: String): Boolean {
        return (url.startsWith("http://") || url.startsWith("https://")) &&
            (url.contains("cdninstagram.com") || url.contains("fbcdn.net")) &&
            !looksLikeProfileImageUrl(url)
    }

    private fun isStoryVideoUrl(url: String): Boolean {
        return url.contains("t50.") || url.contains("/o1/") || mimeTypeForUrl(url).startsWith("video")
    }

    private fun installDirectMessageContextMenuHook() {
        runSafe("DM context menu hook") {
            val menuClass = loadFirstClass("X.2CW", "p000X.C2CW")
                ?: dexBridge.findClassNamesUsingStrings("IgdsContextMenu_BadTokenException")
                    .firstNotNullOfOrNull { name ->
                        runCatching { Class.forName(name, false, appClassLoader) }
                            .getOrNull()
                            ?.takeIf { PopupWindow::class.java.isAssignableFrom(it) }
                    }
                ?: return@runSafe

            dmPrismItemConstructor = loadFirstClass("X.B7o", "p000X.C28700B7o")?.let { findConstructor(it, 24) }
            dmLegacyItemConstructor = loadFirstClass("X.B7N", "p000X.B7N")?.let { findConstructor(it, 10) }
            dmMenuClickInterfaceClass = loadFirstClass("X.Lhz", "p000X.InterfaceC55387Lhz")

            val itemHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    maybeInjectDmMenuItems(param)
                }
            }
            listOf("A08", "A07", "A09").forEach { methodName ->
                runCatching { XposedBridge.hookAllMethods(menuClass, methodName, itemHook) }
            }

            val anchorHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    captureDmMenuAnchor(param)
                }
            }
            listOf("A06", "showAsDropDown", "showAtLocation").forEach { methodName ->
                runCatching { XposedBridge.hookAllMethods(menuClass, methodName, anchorHook) }
            }

            installDmMessageActionsFlow()
            installDirectMessageImageUrlCaptureHooks()
            installDirectMessageUrlCaptureHooks()
            installDirectMessageAudioPlaybackCaptureHooks()
            installDmNativeModalLaunchSuppression()
            installDmDeferredActionResume()
            logInfo("Installed DM context menu hook on ${menuClass.name} with MessageActions flow")
        }
    }

    private fun installDmMessageActionsFlow() {
        runSafe("DM MessageActions hook") {
            dmMessageActionsViewModelClass = loadFirstClass(
                "com.instagram.direct.messagethread.messageactions.model.MessageActionsViewModel"
            )
            dmLongPressActionDataClass = loadFirstClass(
                "com.instagram.direct.messagethread.interaction.longpressaction.LongPressActionData"
            )
            val viewModelClass = dmMessageActionsViewModelClass
            val actionDataClass = dmLongPressActionDataClass
            if (viewModelClass == null || actionDataClass == null) {
                logInfo("DM MessageActions classes not found")
                return@runSafe
            }

            dmLongPressActionDataConstructor = findConstructor(actionDataClass, 11)
            if (dmLongPressActionDataConstructor == null) {
                logInfo("DM LongPressActionData constructor not found")
                return@runSafe
            }

            inferDmLongPressActionTypes()
            hookDmMessageActionsBuilders()
            hookDmMessageActionsRenderers()
            hookDmMessageActionsDispatchers()
            hookDmMessageActionsControllers()
            hookDmMessageActionDelegates()
            hookDmMessageActionListenerMethods()
            hookDmMessageActionMenuBuilderRows()
            hookDmMessageActionRowModels()
            hookDmMessageActionClickWrappers()
        }
    }

    private fun inferDmLongPressActionTypes() {
        val constructor = dmLongPressActionDataConstructor ?: return
        val params = constructor.parameterTypes
        if (params.size < 5) return
        dmNormalLongPressType = findDmEnumLikeValue(params[1], "NORMAL", null, "A09")
        dmLongPressActionEnumClass = params[2]
        dmCustomLongPressEnum = findDmEnumLikeValueExact(params[2], "GOOD_RESULT", "good_result", "A0S")
            ?: findDmEnumLikeValueExact(params[2], "SAVE_MEDIA", "save_media", "A0x")
        dmPassiveLongPressEnum = findDmEnumLikeValueExact(params[2], null, "none", null)
    }

    private fun hookDmMessageActionsBuilders() {
        val viewModelClass = dmMessageActionsViewModelClass ?: return
        val builders = mutableListOf<Any>()
        viewModelClass.declaredFields.forEach { field ->
            if (!Modifier.isStatic(field.modifiers)) return@forEach
            val type = field.type
            if (type.isPrimitive || type == String::class.java || type.name.contains("Parcelable")) return@forEach
            runCatching {
                field.isAccessible = true
                field.get(null)
            }.getOrNull()?.let { value ->
                if (builders.none { it === value }) builders += value
            }
        }

        var hooked = 0
        builders.forEach { builder ->
            builder.javaClass.declaredMethods.forEach { method ->
                if (method.returnType != viewModelClass) return@forEach
                val signature = "${method.declaringClass.name}.${method.name}:dm_message_actions_builder"
                if (!hookedDexMethods.add(signature)) return@forEach
                method.isAccessible = true
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam<*>) {
                            if (!state.enableDmContextMenuOptions) return
                            maybeInjectDmMessageActionsViewModel(contextFromArgs(param.args), param.result, false)
                        }
                    }
                )
                hooked++
            }
        }
        logInfo("Hooked DM MessageActions builders=$hooked")
    }

    private fun hookDmMessageActionsRenderers() {
        val viewModelClass = dmMessageActionsViewModelClass ?: return
        val classes = linkedSetOf<Class<*>>()
        loadFirstClass("X.eby", "p000X.C96758eby")?.let { classes += it }
        addDmClassesUsingString(classes, "MessageActionsHelper")

        var hooked = 0
        classes.forEach { cls ->
            cls.declaredMethods.forEach { method ->
                if (!methodHasParameter(method, viewModelClass)) return@forEach
                val signature = "${method.declaringClass.name}.${method.name}:dm_message_actions_renderer"
                if (!hookedDexMethods.add(signature)) return@forEach
                method.isAccessible = true
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam<*>) {
                            if (!state.enableDmContextMenuOptions) return
                            val viewModel = firstArgOfType(param.args, viewModelClass)
                            maybeInjectDmMessageActionsViewModel(contextFromArgs(param.args), viewModel, true)
                            rememberDmMessageActionLifecycle(viewModel, param.args)
                            if (shouldSuppressDmMessageActionOpenLifecycle(viewModel)) {
                                suppressDmMessageActionOpenLifecycle.set(true)
                                suppressDmMessageActionOpenLifecycleCallbacks(param.args, method.parameterTypes)
                            }
                        }

                        override fun afterHookedMethod(param: MethodHookParam<*>) {
                            suppressDmMessageActionOpenLifecycle.remove()
                        }
                    }
                )
                hooked++
            }
        }
        logInfo("Hooked DM MessageActions renderers=$hooked")
    }

    private fun hookDmMessageActionsDispatchers() {
        val enumClass = dmLongPressActionEnumClass ?: return
        val classes = linkedSetOf<Class<*>>()
        loadFirstClass("X.eby", "p000X.C96758eby")?.let { classes += it }
        addDmClassesUsingString(classes, "MessageActionsHelper")

        var hooked = 0
        classes.forEach { cls ->
            cls.declaredMethods.forEach { method ->
                if (method.returnType != Void.TYPE || !methodHasParameter(method, enumClass)) return@forEach
                val signature = "${method.declaringClass.name}.${method.name}:dm_message_actions_dispatcher"
                if (!hookedDexMethods.add(signature)) return@forEach
                method.isAccessible = true
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam<*>) {
                            if (!state.enableDmContextMenuOptions) return
                            val action = dmActionFromCustomLongPressEnumArgs(param.args)
                            if (action < 0) return
                            val view = firstArgOfType(param.args, View::class.java) as? View
                            val context = contextFromArgs(param.args) ?: view?.context ?: currentActivity ?: androidContext
                            val sources = param.args?.clone()
                            val url = resolveDmContextUrl(null, view, sources)
                            logInfo("Claimed DM MessageActions dispatcher action=$action enum=${enumWire(firstDmActionEnumArg(param.args))}")
                            runResolvedDmMessageAction(action, context, view, sources, url)
                            param.result = null
                        }
                    }
                )
                hooked++
            }
        }
        logInfo("Hooked DM MessageActions dispatchers=$hooked")
    }

    private fun hookDmMessageActionsControllers() {
        val viewModelClass = dmMessageActionsViewModelClass ?: return
        val classes = linkedSetOf<Class<*>>()
        loadFirstClass("X.gkP", "p000X.C100034gkP")?.let { classes += it }
        addDmClassesUsingString(classes, "MessageActionsController")
        addDmClassesUsingString(classes, "direct_message_actions_fragment")

        var hooked = 0
        classes.forEach { cls ->
            if (dmMessageActionsControllerClass == null) dmMessageActionsControllerClass = cls
            cls.declaredConstructors.forEach { constructor ->
                if (!constructorHasParameter(constructor, viewModelClass)) return@forEach
                val signature = "${constructor.declaringClass.name}.<init>:dm_message_actions_controller"
                if (!hookedDexMethods.add(signature)) return@forEach
                constructor.isAccessible = true
                XposedBridge.hookMethod(
                    constructor,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam<*>) {
                            if (!state.enableDmContextMenuOptions) return
                            maybeInjectDmMessageActionsViewModel(
                                contextFromArgs(param.args),
                                firstArgOfType(param.args, viewModelClass),
                                false
                            )
                        }

                        override fun afterHookedMethod(param: MethodHookParam<*>) {
                            val viewModel = firstArgOfType(param.args, viewModelClass)
                            rememberDmMessageActionController(viewModel, param.thisObject)
                            rememberDmMessageActionDelegate(firstDmMessageActionDelegate(param.args))
                        }
                    }
                )
                hooked++
            }
        }
        logInfo("Hooked DM MessageActions controller constructors=$hooked")
    }

    private fun hookDmMessageActionDelegates() {
        val viewModelClass = dmMessageActionsViewModelClass ?: return
        val classes = linkedSetOf<Class<*>>()
        loadFirstClass("X.TIR", "p000X.TIR")?.let { classes += it }
        addDmClassesUsingString(classes, "MESSAGE_ACTIONS_VIEW_MODEL_KEY")
        addDmClassesUsingString(classes, "DirectIntermediatePermanentMediaViewer.ITEM_ACTIONS_FRAGMENT_TAG")

        var hooked = 0
        classes.forEach { cls ->
            cls.declaredMethods.forEach { method ->
                if (method.returnType != Void.TYPE || method.parameterTypes.size != 1) return@forEach
                val paramType = method.parameterTypes[0]
                if (paramType.isPrimitive || paramType == String::class.java || View::class.java.isAssignableFrom(paramType)) return@forEach
                val signature = "${method.declaringClass.name}.${method.name}:dm_message_action_delegate_setter"
                if (!hookedDexMethods.add(signature)) return@forEach
                method.isAccessible = true
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam<*>) {
                            if (!state.enableDmContextMenuOptions) return
                            rememberDmMessageActionDelegate(param.args?.firstOrNull())
                        }
                    }
                )
                hooked++
            }

            cls.declaredConstructors.forEach { constructor ->
                if (!constructorHasParameter(constructor, viewModelClass)) return@forEach
                val signature = "${constructor.declaringClass.name}.<init>:dm_message_action_delegate_ctor"
                if (!hookedDexMethods.add(signature)) return@forEach
                constructor.isAccessible = true
                XposedBridge.hookMethod(
                    constructor,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam<*>) {
                            if (!state.enableDmContextMenuOptions) return
                            rememberDmMessageActionDelegate(param.thisObject)
                            val viewModel = firstArgOfType(param.args, viewModelClass)
                            if (viewModel != null) {
                                rememberDmMessageActionController(viewModel, param.thisObject)
                                rememberDmMessageActionLifecycle(viewModel, param.args)
                            }
                        }
                    }
                )
                hooked++
            }
        }
        logInfo("Hooked DM MessageActions delegate surfaces=$hooked")
    }

    private fun hookDmMessageActionListenerMethods() {
        val classes = linkedSetOf<Class<*>>()
        loadFirstClass("X.emy", "p000X.ViewOnClickListenerC97315emy")?.let { classes += it }
        addDmClassesUsingString(classes, "more_action_sheet")

        var hooked = 0
        classes.forEach { cls ->
            if (!View.OnClickListener::class.java.isAssignableFrom(cls)) return@forEach
            if (!hasDmLongPressActionDataField(cls)) return@forEach
            cls.declaredMethods.forEach { method ->
                if (method.name != "onClick" || method.parameterTypes.size != 1 || !View::class.java.isAssignableFrom(method.parameterTypes[0])) return@forEach
                val signature = "${method.declaringClass.name}.${method.name}:dm_message_action_listener"
                if (!hookedDexMethods.add(signature)) return@forEach
                method.isAccessible = true
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam<*>) {
                            if (!state.enableDmContextMenuOptions) return
                            val action = findDmActionInListener(param.thisObject)
                            if (action < 0) return
                            val view = param.args?.firstOrNull() as? View
                            val context = view?.context ?: currentActivity ?: androidContext
                            val sources = arrayOf(param.thisObject)
                            logInfo("Claimed DM MessageActions listener action=$action")
                            runDmMessageActionAfterDismiss(action, context, view, param.thisObject, sources)
                            param.result = null
                        }
                    }
                )
                hooked++
            }
        }
        logInfo("Hooked DM MessageActions click listeners=$hooked")
    }

    private fun hookDmMessageActionMenuBuilderRows() {
        val menuBuilderClass = loadFirstClass("X.80M", "p000X.C80M") ?: return
        var hooked = 0
        menuBuilderClass.declaredMethods.forEach { method ->
            if (method.returnType != Void.TYPE) return@forEach
            val params = method.parameterTypes
            val labelIndex = params.indexOfFirst { it == String::class.java }
            val listenerIndex = params.indexOfFirst { View.OnClickListener::class.java.isAssignableFrom(it) }
            if (labelIndex < 0 || listenerIndex < 0) return@forEach
            val signature = "${method.declaringClass.name}.${method.name}:dm_message_action_builder_row"
            if (!hookedDexMethods.add(signature)) return@forEach
            method.isAccessible = true
            XposedBridge.hookMethod(
                method,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (state.enableDmContextMenuOptions) {
                            wrapCustomDmMessageActionListenerArg(param.args, labelIndex, listenerIndex)
                        }
                    }
                }
            )
            hooked++
        }
        logInfo("Hooked DM MessageActions menu builder rows=$hooked")
    }

    private fun hookDmMessageActionRowModels() {
        val rowClass = loadFirstClass("X.80c", "p000X.C2086280c") ?: return
        var hooked = 0
        rowClass.declaredConstructors.forEach { constructor ->
            val params = constructor.parameterTypes
            val labelIndex = params.indexOfFirst { it == String::class.java }
            val listenerIndex = params.indexOfFirst { View.OnClickListener::class.java.isAssignableFrom(it) }
            if (listenerIndex < 0) return@forEach
            val signature = "${constructor.declaringClass.name}.<init>:dm_message_action_row"
            if (!hookedDexMethods.add(signature)) return@forEach
            constructor.isAccessible = true
            XposedBridge.hookMethod(
                constructor,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (state.enableDmContextMenuOptions) {
                            wrapCustomDmMessageActionListenerArg(param.args, labelIndex, listenerIndex)
                        }
                    }
                }
            )
            hooked++
        }
        logInfo("Hooked DM MessageActions row models=$hooked")
    }

    private fun hookDmMessageActionClickWrappers() {
        if (dmMessageActionClickHooked) return
        dmMessageActionClickHooked = true
        XposedBridge.hookAllMethods(
            View::class.java,
            "setOnClickListener",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    if (!state.enableDmContextMenuOptions) return
                    val listener = param.args?.firstOrNull() as? View.OnClickListener ?: return
                    if (listener is DmMessageActionClickListener) return
                    val action = findDmActionInListener(listener)
                    if (action < 0) return
                    param.args[0] = DmMessageActionClickListener(action, listener)
                }
            }
        )
    }

    private fun maybeInjectDmMessageActionsViewModel(context: Context?, viewModel: Any?, fromRenderer: Boolean) {
        val viewModelClass = dmMessageActionsViewModelClass ?: return
        if (viewModel == null || !viewModelClass.isInstance(viewModel)) return
        if (dmLongPressActionDataClass == null || dmLongPressActionDataConstructor == null) return
        val ctx = context ?: currentActivity ?: androidContext
        val likelyVisual = isLikelyDmVisualMessageViewModel(viewModel)
        var injectedLists = 0
        var cls: Class<*>? = viewModel.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                if (!List::class.java.isAssignableFrom(field.type)) return@forEach
                runCatching {
                    field.isAccessible = true
                    val list = field.get(viewModel) as? List<*> ?: return@runCatching
                    if (!isDmLongPressActionList(list)) return@runCatching
                    if (!fromRenderer && !likelyVisual && !looksLikeDirectVisualMessageMenu(list)) return@runCatching
                    val extended = prependDmMessageActions(ctx, list) ?: return@runCatching
                    field.set(viewModel, extended)
                    injectedLists++
                }.onFailure { logError("DM MessageActions list injection failed", it) }
            }
            cls = cls.superclass
        }
        if (injectedLists > 0) {
            logInfo("Injected DM MessageActions custom rows=$injectedLists renderer=$fromRenderer visual=$likelyVisual")
        }
    }

    private fun isLikelyDmVisualMessageViewModel(viewModel: Any?): Boolean {
        val type = enumWireField(viewModel, "A07")?.lowercase(Locale.US) ?: return false
        return type.contains("media") ||
            type.contains("raven") ||
            type.contains("visual") ||
            type.contains("photo") ||
            type.contains("video") ||
            type.contains("clip") ||
            type.contains("reel") ||
            type.contains("story") ||
            type.contains("xma") ||
            type.contains("audio") ||
            type.contains("voice")
    }

    private fun isDmLongPressActionList(list: List<*>): Boolean {
        val cls = dmLongPressActionDataClass ?: return false
        return list.firstOrNull { it != null }?.let { cls.isInstance(it) } == true
    }

    private fun prependDmMessageActions(context: Context, original: List<*>): List<Any?>? {
        if (containsCustomDmMessageAction(original) || hasInjectedDmItems(context, original)) {
            rememberExistingDmCustomActions(context, original)
            return null
        }
        val extended = ArrayList<Any?>()
        for (action in DM_ACTION_DOWNLOAD..DM_ACTION_WHATSAPP_STORY) {
            createDmLongPressActionData(context, action)?.let { extended += it }
        }
        if (extended.isEmpty()) return null
        extended.addAll(original)
        return extended
    }

    private fun createDmLongPressActionData(context: Context?, action: Int): Any? {
        val constructor = dmLongPressActionDataConstructor ?: return null
        val normalType = dmNormalLongPressType ?: return null
        val customEnum = dmEnumForCustomAction(action) ?: return null
        return runCatching {
            val args = arrayOfNulls<Any>(11)
            args[0] = null
            args[1] = normalType
            args[2] = customEnum
            args[3] = null
            args[4] = null
            args[5] = dmMenuIconResourceId(context, action)
            args[6] = null
            args[7] = dmActionLabel(action)
            args[8] = null
            args[9] = null
            args[10] = null
            constructor.newInstance(*args).also { data ->
                dmCustomLongPressActions[data] = action
                rememberDmCustomLongPressEnumAction(customEnum, action)
            }
        }.onFailure { logError("Create DM LongPressActionData failed", it) }.getOrNull()
    }

    private fun dmEnumForCustomAction(action: Int): Any? {
        val value = findFirstDmEnumWire(*dmCustomEnumWiresForAction(action))
            ?: findFirstUnusedDmEnumWire(
                "good_result",
                "bad_result",
                "good_response",
                "bad_response",
                "ai_subscription_manage_update",
                "creator_ai_inspect_message",
                "provide_agent_refinement_feedback",
                "regenerate_ai_response",
                "see_info",
                "ask_meta_ai",
                "summarize_with_ai",
                "save_media",
                "share",
                "forward",
                "share_to_story"
            )
            ?: dmCustomLongPressEnum
            ?: dmPassiveLongPressEnum
        if (value != null) rememberDmCustomLongPressEnumAction(value, action)
        return value
    }

    private fun dmCustomEnumWiresForAction(action: Int): Array<String> {
        return when (action) {
            DM_ACTION_DOWNLOAD -> arrayOf("good_result", "save_media", "create_cutout")
            DM_ACTION_OPEN_APPS -> arrayOf("bad_result", "good_response", "share")
            DM_ACTION_PREVIEW -> arrayOf("creator_ai_inspect_message", "see_info", "details")
            DM_ACTION_REPOST -> arrayOf("bad_response", "provide_agent_refinement_feedback", "forward")
            else -> arrayOf("ai_subscription_manage_update", "summarize_with_ai", "ask_meta_ai", "share_to_story")
        }
    }

    private fun findFirstDmEnumWire(vararg wires: String): Any? {
        val enumClass = dmLongPressActionEnumClass ?: return null
        return wires.firstNotNullOfOrNull { wire -> findDmEnumLikeValueExact(enumClass, null, wire, null) }
    }

    private fun findFirstUnusedDmEnumWire(vararg wires: String): Any? {
        return wires.firstNotNullOfOrNull { wire ->
            val value = findFirstDmEnumWire(wire) ?: return@firstNotNullOfOrNull null
            synchronized(dmCustomLongPressEnumActions) {
                if (!dmCustomLongPressEnumActions.containsKey(value)) value else null
            }
        }
    }

    private fun findDmEnumLikeValue(cls: Class<*>?, enumName: String?, wireName: String?, fieldName: String?): Any? {
        findDmEnumLikeValueExact(cls, enumName, wireName, fieldName)?.let { return it }
        return runCatching {
            val values = cls?.getDeclaredMethod("values") ?: return@runCatching null
            values.isAccessible = true
            (values.invoke(null) as? Array<*>)?.firstOrNull()
        }.getOrNull()
    }

    private fun findDmEnumLikeValueExact(cls: Class<*>?, enumName: String?, wireName: String?, fieldName: String?): Any? {
        staticFieldValue(cls, fieldName)?.let { return it }
        return runCatching {
            val values = cls?.getDeclaredMethod("values") ?: return@runCatching null
            values.isAccessible = true
            val candidates = values.invoke(null) as? Array<*> ?: return@runCatching null
            candidates.firstOrNull { candidate ->
                (enumName != null && enumName == enumName(candidate)) ||
                    (wireName != null && wireName == stringField(candidate, "A00"))
            }
        }.getOrNull()
    }

    private fun staticFieldValue(cls: Class<*>?, fieldName: String?): Any? {
        if (cls == null || fieldName == null) return null
        return runCatching {
            val field = cls.getDeclaredField(fieldName)
            field.isAccessible = true
            field.get(null)
        }.getOrNull()
    }

    private fun enumName(value: Any?): String? {
        if (value == null) return null
        return runCatching { value.javaClass.getMethod("name").invoke(value) as? String }.getOrNull()
    }

    private fun enumWire(value: Any?): String? {
        return stringField(value, "A00")?.takeIf { it.isNotBlank() } ?: enumName(value)
    }

    private fun enumWireField(item: Any?, fieldName: String): String? {
        val value = fieldValueDeep(item, fieldName)
        return enumWire(value)
    }

    private fun findDmActionInListener(listener: Any?): Int {
        val actionClass = dmLongPressActionDataClass ?: return -1
        if (listener == null) return -1
        var cls: Class<*>? = listener.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                if (!actionClass.isAssignableFrom(field.type)) return@forEach
                runCatching {
                    field.isAccessible = true
                    actionFromDmLongPressActionData(field.get(listener))
                }.getOrNull()?.takeIf { it >= 0 }?.let { return it }
            }
            cls = cls.superclass
        }
        return -1
    }

    private fun dmActionFromCustomLongPressEnumArgs(args: Array<Any?>?): Int {
        args?.forEach { arg ->
            dmActionFromCustomLongPressEnum(arg).takeIf { it >= 0 }?.let { return it }
        }
        return -1
    }

    private fun firstDmActionEnumArg(args: Array<Any?>?): Any? {
        return args?.firstOrNull { dmActionFromCustomLongPressEnum(it) >= 0 }
    }

    private fun dmActionFromCustomLongPressEnum(enumValue: Any?): Int {
        if (enumValue == null || enumValue === dmPassiveLongPressEnum) return -1
        return synchronized(dmCustomLongPressEnumActions) { dmCustomLongPressEnumActions[enumValue] ?: -1 }
    }

    private fun rememberDmCustomLongPressEnumAction(enumValue: Any?, action: Int) {
        if (enumValue == null || enumValue === dmPassiveLongPressEnum) return
        dmCustomLongPressEnumActions[enumValue] = action
    }

    private fun actionFromDmLongPressActionData(data: Any?): Int {
        val actionClass = dmLongPressActionDataClass ?: return -1
        if (data == null || !actionClass.isInstance(data)) return -1
        synchronized(dmCustomLongPressActions) {
            dmCustomLongPressActions[data]?.let { return it }
        }

        val markerAction = actionFromDmMessageActionMarker(markerFromDmLongPressActionData(data))
        if (markerAction >= 0) {
            rememberDmCustomLongPressAction(data, markerAction)
            rememberDmCustomEnumFromData(data, markerAction)
            clearDmLongPressSecondaryText(data)
            return markerAction
        }

        val labelAction = actionFromDmCustomLabel(extractDmMenuLabel(data))
        if (labelAction >= 0) {
            rememberDmCustomLongPressAction(data, labelAction)
            rememberDmCustomEnumFromData(data, labelAction)
        }
        return labelAction
    }

    private fun actionFromDmCustomLabel(label: String?): Int {
        return when (label?.trim()?.lowercase(Locale.US)) {
            "download" -> DM_ACTION_DOWNLOAD
            "open by phone apps" -> DM_ACTION_OPEN_APPS
            "preview" -> DM_ACTION_PREVIEW
            "repost" -> DM_ACTION_REPOST
            "share to whatsapp story" -> DM_ACTION_WHATSAPP_STORY
            else -> -1
        }
    }

    private fun containsCustomDmMessageAction(items: List<*>): Boolean = items.any { actionFromDmLongPressActionData(it) >= 0 }

    private fun rememberExistingDmCustomActions(context: Context?, items: List<*>) {
        items.forEach { item ->
            val known = actionFromDmLongPressActionData(item)
            if (known >= 0) {
                clearDmLongPressSecondaryText(item)
                return@forEach
            }
            val label = extractDmMenuLabel(item) ?: return@forEach
            for (action in DM_ACTION_DOWNLOAD..DM_ACTION_WHATSAPP_STORY) {
                if (label == dmActionLabel(action)) {
                    rememberDmCustomLongPressAction(item, action)
                    rememberDmCustomEnumFromData(item, action)
                    clearDmLongPressSecondaryText(item)
                    return@forEach
                }
            }
        }
    }

    private fun rememberDmCustomLongPressAction(data: Any?, action: Int) {
        if (data == null) return
        dmCustomLongPressActions[data] = action
    }

    private fun rememberDmCustomEnumFromData(data: Any?, action: Int) {
        fieldValueDeep(data, "A04")?.let { rememberDmCustomLongPressEnumAction(it, action) }
    }

    private fun clearDmLongPressSecondaryText(data: Any?) {
        val actionClass = dmLongPressActionDataClass ?: return
        if (data == null || !actionClass.isInstance(data)) return
        listOf("A0A", "A09", "A08").forEach { fieldName ->
            runCatching {
                val field = data.javaClass.getDeclaredField(fieldName)
                field.isAccessible = true
                val value = field.get(data) as? String
                if (isDmMessageActionMarker(value)) field.set(data, null)
            }
        }
    }

    private fun markerFromDmLongPressActionData(data: Any?): String? {
        val actionClass = dmLongPressActionDataClass ?: return null
        if (data == null || !actionClass.isInstance(data)) return null
        listOf("A0A", "A09", "A08").forEach { fieldName ->
            stringField(data, fieldName)?.takeIf { isDmMessageActionMarker(it) }?.let { return it }
        }
        data.javaClass.declaredFields.forEach { field ->
            if (field.type != String::class.java) return@forEach
            runCatching {
                field.isAccessible = true
                field.get(data) as? String
            }.getOrNull()?.takeIf { isDmMessageActionMarker(it) }?.let { return it }
        }
        return null
    }

    private fun isDmMessageActionMarker(value: String?): Boolean = value?.startsWith(DM_MESSAGE_ACTION_MARKER_PREFIX) == true

    private fun actionFromDmMessageActionMarker(marker: String?): Int {
        if (!isDmMessageActionMarker(marker)) return -1
        return marker?.substring(DM_MESSAGE_ACTION_MARKER_PREFIX.length)?.toIntOrNull() ?: -1
    }

    private fun wrapCustomDmMessageActionListenerArg(args: Array<Any?>?, labelIndex: Int, listenerIndex: Int) {
        if (args == null || listenerIndex !in args.indices) return
        val listener = args[listenerIndex] as? View.OnClickListener ?: return
        if (listener is DmMessageActionClickListener) return
        var action = -1
        if (labelIndex >= 0 && labelIndex in args.indices) action = actionFromDmCustomLabel(args[labelIndex] as? String)
        if (action < 0) action = findDmActionInListener(listener)
        if (action < 0) return
        args[listenerIndex] = DmMessageActionClickListener(action, listener)
    }

    private fun hasDmLongPressActionDataField(cls: Class<*>?): Boolean {
        val actionClass = dmLongPressActionDataClass ?: return false
        var current = cls
        while (current != null && current != Any::class.java) {
            if (current.declaredFields.any { actionClass.isAssignableFrom(it.type) }) return true
            current = current.superclass
        }
        return false
    }

    private fun addDmClassesUsingString(out: MutableSet<Class<*>>, value: String) {
        dexBridge.findClassNamesUsingStrings(value).forEach { name ->
            runCatching { Class.forName(name, false, appClassLoader) }.getOrNull()?.let { out += it }
        }
    }

    private fun methodHasParameter(method: Method, type: Class<*>): Boolean {
        return method.parameterTypes.any { it == type || type.isAssignableFrom(it) || it.isAssignableFrom(type) }
    }

    private fun constructorHasParameter(constructor: Constructor<*>, type: Class<*>): Boolean {
        return constructor.parameterTypes.any { it == type || type.isAssignableFrom(it) || it.isAssignableFrom(type) }
    }

    private fun firstArgOfType(args: Array<Any?>?, type: Class<*>): Any? {
        return args?.firstOrNull { it != null && type.isInstance(it) }
    }

    private fun contextFromArgs(args: Array<Any?>?): Context? {
        args?.forEach { arg ->
            when (arg) {
                is Context -> return arg
                is View -> return arg.context
            }
        }
        return null
    }

    private fun shouldSuppressDmMessageActionOpenLifecycle(viewModel: Any?): Boolean {
        return isLikelyDmVisualMessageViewModel(viewModel) || dmViewModelContainsCustomMessageAction(viewModel)
    }

    private fun dmViewModelContainsCustomMessageAction(viewModel: Any?): Boolean {
        if (viewModel == null) return false
        var cls: Class<*>? = viewModel.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                if (!List::class.java.isAssignableFrom(field.type)) return@forEach
                runCatching {
                    field.isAccessible = true
                    val list = field.get(viewModel) as? List<*> ?: return@runCatching false
                    containsCustomDmMessageAction(list)
                }.getOrDefault(false).takeIf { it }?.let { return true }
            }
            cls = cls.superclass
        }
        return false
    }

    private fun suppressDmMessageActionOpenLifecycleCallbacks(args: Array<Any?>?, paramTypes: Array<Class<*>>) {
        if (args == null) return
        args.indices.forEach { index ->
            val original = args[index] ?: return@forEach
            val paramType = paramTypes.getOrNull(index)
            val kind = dmLifecycleKind(original, paramType) ?: return@forEach
            val iface = dmLifecycleProxyInterface(original, paramType, kind) ?: return@forEach
            createDmLifecycleNoHideProxy(original, iface, kind)?.let { args[index] = it }
        }
    }

    private fun dmLifecycleKind(value: Any?, paramType: Class<*>?): String? {
        return when {
            isDmChromeLifecycleType(paramType) || isDmChromeLifecycleCallback(value) -> "chrome"
            isDmVisibilityLifecycleType(paramType) || isDmVisibilityLifecycleCallback(value) -> "visibility"
            else -> null
        }
    }

    private fun isDmChromeLifecycleCallback(value: Any?): Boolean = value != null && hasNoArgMethod(value, "EQL") && hasNoArgMethod(value, "EQf")

    private fun isDmVisibilityLifecycleCallback(value: Any?): Boolean = value != null && hasNoArgMethod(value, "Dab") && hasNoArgMethod(value, "Gj5")

    private fun isDmChromeLifecycleType(type: Class<*>?): Boolean = type != null && findNoArgMethod(type, "EQL") != null && findNoArgMethod(type, "EQf") != null

    private fun isDmVisibilityLifecycleType(type: Class<*>?): Boolean = type != null && findNoArgMethod(type, "Dab") != null && findNoArgMethod(type, "Gj5") != null

    private fun dmLifecycleProxyInterface(value: Any, paramType: Class<*>?, kind: String): Class<*>? {
        if (paramType?.isInterface == true) {
            if (kind == "chrome" && isDmChromeLifecycleType(paramType)) return paramType
            if (kind == "visibility" && isDmVisibilityLifecycleType(paramType)) return paramType
        }
        val interfaces = mutableListOf<Class<*>>()
        collectInterfaces(value.javaClass, interfaces)
        return interfaces.firstOrNull { iface ->
            (kind == "chrome" && isDmChromeLifecycleType(iface)) ||
                (kind == "visibility" && isDmVisibilityLifecycleType(iface))
        }
    }

    private fun collectInterfaces(cls: Class<*>?, out: MutableList<Class<*>>) {
        if (cls == null || cls == Any::class.java) return
        cls.interfaces.forEach { iface ->
            if (iface !in out) {
                out += iface
                collectInterfaces(iface, out)
            }
        }
        collectInterfaces(cls.superclass, out)
    }

    private fun createDmLifecycleNoHideProxy(target: Any, iface: Class<*>, kind: String): Any? {
        return runCatching {
            Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { proxy, method, args ->
                when {
                    method.name == "toString" && method.parameterTypes.isEmpty() -> "PurrfectInstaDmLifecycleProxy($kind)"
                    method.name == "hashCode" && method.parameterTypes.isEmpty() -> System.identityHashCode(proxy)
                    method.name == "equals" && method.parameterTypes.size == 1 -> args != null && args.isNotEmpty() && proxy === args[0]
                    shouldSuppressDmLifecycleOpenMethod(kind, method) -> defaultResult(method.returnType)
                    else -> runCatching {
                        method.isAccessible = true
                        method.invoke(target, *(args ?: emptyArray()))
                    }.getOrElse { throwable ->
                        val cause = (throwable as? java.lang.reflect.InvocationTargetException)?.cause
                        throw (cause ?: throwable)
                    }
                }
            }
        }.getOrNull()
    }

    private fun shouldSuppressDmLifecycleOpenMethod(kind: String, method: Method): Boolean {
        if (suppressDmMessageActionOpenLifecycle.get() != true) return false
        if (method.parameterTypes.isNotEmpty() || method.returnType != Void.TYPE) return false
        return when (kind) {
            "visibility" -> method.name == "Dab" || method.name != "Gj5"
            "chrome" -> method.name == "EQf" || method.name != "EQL"
            else -> false
        }
    }

    private fun hasNoArgMethod(target: Any?, methodName: String): Boolean {
        return target != null && findNoArgMethod(target.javaClass, methodName) != null
    }

    private fun findNoArgMethod(cls: Class<*>?, methodName: String): Method? {
        var current = cls
        while (current != null && current != Any::class.java) {
            runCatching {
                val method = current.getDeclaredMethod(methodName)
                if (method.parameterTypes.isEmpty()) {
                    method.isAccessible = true
                    return method
                }
            }
            current = current.superclass
        }
        return runCatching {
            val method = cls?.getMethod(methodName)
            if (method?.parameterTypes?.isEmpty() == true) method.apply { isAccessible = true } else null
        }.getOrNull()
    }

    private fun firstDmMessageActionDelegate(args: Array<Any?>?): Any? {
        args?.forEach { arg ->
            if (arg != null && findDmMessageActionsViewModel(arg) != null && (hasNoArgMethod(arg, "Edu") || hasNoArgMethod(arg, "EjE"))) {
                return arg
            }
        }
        return null
    }

    private fun rememberDmMessageActionController(viewModel: Any?, controller: Any?) {
        if (viewModel == null || controller == null) return
        dmMessageActionControllers[viewModel] = controller
        dmLatestMessageActionController = controller
    }

    private fun rememberDmMessageActionDelegate(delegate: Any?) {
        if (delegate == null) return
        val viewModel = findDmMessageActionsViewModel(delegate) ?: return
        rememberDmMessageActionController(viewModel, delegate)
        val chromeCallback = fieldValueDeep(delegate, "A0b")
        val visibilityCallback = fieldValueDeep(delegate, "A0h")
        val lifecycle = DmMessageActionLifecycle(chromeCallback, visibilityCallback, delegate)
        dmMessageActionLifecycles[viewModel] = lifecycle
        dmLatestMessageActionLifecycle = lifecycle
    }

    private fun rememberDmMessageActionLifecycle(viewModel: Any?, args: Array<Any?>?) {
        if (viewModel == null || args == null) return
        var chromeCallback: Any? = null
        var visibilityCallback: Any? = null
        val viewModelIndex = indexOfDmArg(args, viewModel)
        if (viewModelIndex >= 22) {
            args.getOrNull(viewModelIndex - 22)?.takeIf { isDmChromeLifecycleCallback(it) }?.let { chromeCallback = it }
        }
        if (chromeCallback == null && viewModelIndex >= 23) {
            args.getOrNull(viewModelIndex - 23)?.takeIf { isDmChromeLifecycleCallback(it) }?.let { chromeCallback = it }
        }
        if (visibilityCallback == null && viewModelIndex >= 17) {
            args.getOrNull(viewModelIndex - 17)?.takeIf { isDmVisibilityLifecycleCallback(it) }?.let { visibilityCallback = it }
        }
        args.forEach { arg ->
            if (arg == null) return@forEach
            if (chromeCallback == null && hasNoArgMethod(arg, "EQL") && hasNoArgMethod(arg, "EQf")) chromeCallback = arg
            if (visibilityCallback == null && hasNoArgMethod(arg, "Dab") && hasNoArgMethod(arg, "Gj5")) visibilityCallback = arg
        }
        if (chromeCallback == null && visibilityCallback == null) return
        val lifecycle = DmMessageActionLifecycle(chromeCallback, visibilityCallback, null)
        dmMessageActionLifecycles[viewModel] = lifecycle
        dmLatestMessageActionLifecycle = lifecycle
    }

    private fun indexOfDmArg(args: Array<Any?>?, target: Any?): Int {
        if (args == null || target == null) return -1
        return args.indexOfFirst { it === target }
    }

    private fun restoreDmMessageActionsUiSoon(source: Any?, sources: Array<Any?>?) {
        mainHandler.post { restoreDmMessageActionsUiOnly(source, sources) }
        mainHandler.postDelayed({ restoreDmMessageActionsUiOnly(source, sources) }, 250L)
        mainHandler.postDelayed({ restoreDmMessageActionsUiOnly(source, sources) }, 800L)
        mainHandler.postDelayed({ restoreDmMessageActionsUiOnly(source, sources) }, 1600L)
    }

    private fun restoreDmMessageActionsUiOnly(source: Any?, sources: Array<Any?>?) {
        var lifecycle: DmMessageActionLifecycle? = null
        sources?.forEach { candidate ->
            lifecycle = lifecycle ?: dmMessageActionLifecycleForSource(candidate)
        }
        lifecycle = lifecycle ?: dmMessageActionLifecycleForSource(source) ?: dmLatestMessageActionLifecycle
        restoreDmMessageActionsUiOnly(lifecycle)
        val controller = findDmMessageActionController(source, sources)
        if (controller != null) {
            mainHandler.post { invokeDmControllerRestoreCallbacks(controller) }
        }
    }

    private fun restoreDmMessageActionsUiOnly(lifecycle: DmMessageActionLifecycle?) {
        if (lifecycle == null) return
        var restored = invokeNamedNoArg(lifecycle.chromeCallback, "EQL")
        restored = invokeNamedNoArg(lifecycle.visibilityCallback, "Gj5") || restored
        if (!restored) {
            restored = invokeNamedNoArg(lifecycle.delegateCallback, "EjE") || restored
            restored = invokeNamedNoArg(lifecycle.delegateCallback, "Edu") || restored
            restored = invokeNamedNoArg(lifecycle.delegateCallback, "Edd") || restored
        }
    }

    private fun dmMessageActionLifecycleForSource(source: Any?): DmMessageActionLifecycle? {
        val viewModel = findDmMessageActionsViewModel(source) ?: return null
        return dmMessageActionLifecycles[viewModel]
    }

    private fun findDmMessageActionController(source: Any?, sources: Array<Any?>?): Any? {
        findDmControllerObject(source)?.let { return it }
        dmControllerForViewModel(findDmMessageActionsViewModel(source))?.let { return it }
        sources?.forEach { candidate ->
            findDmControllerObject(candidate)?.let { return it }
            dmControllerForViewModel(findDmMessageActionsViewModel(candidate))?.let { return it }
        }
        return dmLatestMessageActionController
    }

    private fun findDmControllerObject(source: Any?): Any? {
        if (source == null) return null
        val controllerClass = dmMessageActionsControllerClass ?: return null
        if (controllerClass.isInstance(source)) return source
        var cls: Class<*>? = source.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers) || !controllerClass.isAssignableFrom(field.type)) return@forEach
                runCatching {
                    field.isAccessible = true
                    field.get(source)
                }.getOrNull()?.let { return it }
            }
            cls = cls.superclass
        }
        return null
    }

    private fun dmControllerForViewModel(viewModel: Any?): Any? {
        if (viewModel == null) return null
        return dmMessageActionControllers[viewModel]
    }

    private fun invokeDmControllerRestoreCallbacks(controller: Any?): Boolean {
        var invoked = invokeNamedNoArg(controller, "EjE")
        invoked = invokeNamedNoArg(controller, "Edu") || invoked
        invoked = invokeNamedNoArg(controller, "Edd") || invoked
        invoked = invokeNamedNoArg(fieldValueDeep(controller, "A0h"), "Gj5") || invoked
        invoked = invokeNamedNoArg(fieldValueDeep(controller, "A0b"), "EQL") || invoked
        val callback = fieldValueDeep(controller, "A0T")
        invoked = invokeNamedNoArg(callback, "EjE") || invoked
        invoked = invokeNamedNoArg(callback, "Edu") || invoked
        invoked = invokeNamedNoArg(callback, "Edd") || invoked
        return invoked
    }

    private fun invokeNamedNoArg(target: Any?, methodName: String): Boolean {
        val method = findNoArgMethod(target?.javaClass, methodName) ?: return false
        if (method.returnType != Void.TYPE) return false
        return runCatching {
            method.invoke(target)
            true
        }.onFailure { logError("DM MessageActions callback $methodName failed", it) }.getOrDefault(false)
    }

    private inner class DmMessageActionClickListener(
        private val action: Int,
        private val original: View.OnClickListener
    ) : View.OnClickListener {
        override fun onClick(view: View) {
            if (!state.enableDmContextMenuOptions) {
                original.onClick(view)
                return
            }
            runDmMessageActionAfterDismiss(action, view.context ?: currentActivity ?: androidContext, view, original, arrayOf(original))
        }
    }

    private fun installDirectMessageUrlCaptureHooks() {
        runSafe("DM menu Uri capture") {
            XposedBridge.hookAllMethods(
                Uri::class.java,
                "parse",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (!state.enableDmContextMenuOptions) return
                        val url = param.args.firstOrNull() as? String ?: return
                        val voiceStack = currentStackLooksLikeVoice()
                        if (voiceStack) dmLastVoiceContextAtMs = System.currentTimeMillis()
                        if (!looksLikeProfileImageUrl(url) && (isLikelyAudioHttpUrl(url) || voiceStack)) rememberKnownDmAudioUrl(url)
                        if (looksLikeMediaUrl(url) && !looksLikeProfileImageUrl(url)) rememberMediaUrl(url)
                    }
                }
            )
        }
        runSafe("DM menu URL capture") {
            XposedBridge.hookAllConstructors(
                URL::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (!state.enableDmContextMenuOptions) return
                        val url = param.args.firstOrNull() as? String ?: return
                        val voiceStack = currentStackLooksLikeVoice()
                        if (voiceStack) dmLastVoiceContextAtMs = System.currentTimeMillis()
                        if (!looksLikeProfileImageUrl(url) && (isLikelyAudioHttpUrl(url) || voiceStack)) rememberKnownDmAudioUrl(url)
                        if (looksLikeMediaUrl(url) && !looksLikeProfileImageUrl(url)) rememberMediaUrl(url)
                    }
                }
            )
        }
    }

    private fun installDirectMessageImageUrlCaptureHooks() {
        runSafe("DM ImageUrl capture") {
            dmImageUrlClass = loadFirstClass(
                "com.instagram.common.typedurl.ImageUrl",
                "com.instagram.common.p027typedurl.ImageUrl"
            )
            val imageUrlClass = dmImageUrlClass ?: return@runSafe
            val igImageViewClass = loadFirstClass(
                "com.instagram.common.ui.widget.imageview.IgImageView",
                "com.instagram.common.p066ui.widget.imageview.IgImageView"
            ) ?: return@runSafe

            var hooked = 0
            igImageViewClass.declaredMethods.forEach { method ->
                if (method.name != "setUrl" || !method.parameterTypes.any { imageUrlClass.isAssignableFrom(it) }) return@forEach
                val signature = "${method.declaringClass.name}.${method.name}:dm_image_url_capture"
                if (!hookedDexMethods.add(signature)) return@forEach
                method.isAccessible = true
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam<*>) {
                            if (!state.enableDmContextMenuOptions) return
                            val view = param.thisObject as? View ?: return
                            param.args?.firstOrNull { it != null && imageUrlClass.isInstance(it) }
                                ?.let { urlFromTypedObject(it) }
                                ?.let { rememberDmImageUrl(view, it) }
                        }
                    }
                )
                hooked++
            }
            logInfo("Hooked DM IgImageView.setUrl overloads=$hooked")
        }
    }

    private fun installDirectMessageAudioPlaybackCaptureHooks() {
        runSafe("DM audio playback capture") {
            val candidates = linkedSetOf<Class<*>>()
            loadFirstClass("com.instagram.direct.videoplayer.service.AudioMessagePlaybackServiceConnection")?.let { candidates += it }
            addDmClassesUsingString(candidates, "com.instagram.direct.messagethread.voice.service.IAudioMessagePlaybackListener")
            addDmClassesUsingString(candidates, "com.instagram.direct.messagethread.voice.service.IAudioMessagePlaybackService")
            addDmClassesUsingString(candidates, "AudioMessagePlaybackServiceConnection")

            var hooked = 0
            candidates.forEach { cls ->
                cls.declaredMethods.forEach { method ->
                    if (!isLikelyDmVoicePlaybackMethod(method)) return@forEach
                    val signature = "${method.declaringClass.name}.${method.name}:dm_voice_playback_capture"
                    if (!hookedDexMethods.add(signature)) return@forEach
                    method.isAccessible = true
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam<*>) {
                                if (!state.enableDmContextMenuOptions) return
                                dmLastVoiceContextAtMs = System.currentTimeMillis()
                                val url = findUrlInDmActionSources(param.args, preferAudio = true)
                                if (url != null) {
                                    rememberKnownDmAudioUrl(url)
                                    rememberMediaUrl(url)
                                }
                            }
                        }
                    )
                    hooked++
                }
            }
            logInfo("Hooked DM audio playback capture methods=$hooked")
        }
    }

    private fun isLikelyDmVoicePlaybackMethod(method: Method): Boolean {
        if (method.returnType != Void.TYPE) return false
        val name = method.name
        val hay = "${method.declaringClass.name} $name".lowercase(Locale.US)
        if (name == "FnS") return true
        if (hay.contains("audio") || hay.contains("voice")) return method.parameterTypes.isNotEmpty()
        if (method.parameterTypes.size < 8) return false
        return method.parameterTypes.any {
            val p = it.name.lowercase(Locale.US)
            p.contains("media") || p.contains("direct") || p.contains("message")
        }
    }

    private fun installDmNativeModalLaunchSuppression() {
        if (dmNativeModalSuppressorHooked) return
        dmNativeModalSuppressorHooked = true
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam<*>) {
                if (maybeSuppressNativeDmMessageActionModalLaunch(param.args)) param.result = null
            }
        }
        runSafe("DM native modal launch suppression") {
            XposedBridge.hookAllMethods(Activity::class.java, "startActivity", hook)
            XposedBridge.hookAllMethods(Activity::class.java, "startActivityForResult", hook)
            XposedBridge.hookAllMethods(ContextWrapper::class.java, "startActivity", hook)
        }
    }

    private fun installDmDeferredActionResume() {
        if (dmDeferredActionResumeHooked) return
        dmDeferredActionResumeHooked = true
        runSafe("DM deferred action resume") {
            XposedBridge.hookAllMethods(
                Activity::class.java,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val activity = param.thisObject as? Activity ?: return
                        if (isInstagramModalActivity(activity) && !isActivityHistoryModal(activity)) return
                        dmLatestStableActionActivity = WeakReference(activity)
                        drainDeferredDmAction(activity)
                    }
                }
            )
        }
    }

    private fun suppressNativeDmModalLaunch(durationMs: Long) {
        val until = System.currentTimeMillis() + durationMs.coerceAtLeast(0L)
        if (until > dmSuppressModalLaunchUntilMs) dmSuppressModalLaunchUntilMs = until
    }

    private fun maybeSuppressNativeDmMessageActionModalLaunch(args: Array<Any?>?): Boolean {
        if (!state.enableDmContextMenuOptions || System.currentTimeMillis() > dmSuppressModalLaunchUntilMs) return false
        val intent = args?.firstOrNull { it is Intent } as? Intent ?: return false
        if (!intentTargetsInstagramModal(intent) || intentMentionsActivityHistory(intent)) return false
        logInfo("Suppressed native DM MessageActions ModalActivity launch")
        return true
    }

    private fun maybeInjectDmMenuItems(param: XC_MethodHook.MethodHookParam<*>) {
        if (!state.enableDmContextMenuOptions) return
        val original = param.args.firstOrNull() as? List<*> ?: return
        if (original.isEmpty()) return
        val ctx = dmContextFromPopup(param.thisObject)
        if (!looksLikeDirectVisualMessageMenu(original) || hasInjectedDmItems(ctx, original)) return
        inferDmMenuItemShape(original.firstOrNull())
        val legacy = dmLegacyItemConstructor?.declaringClass?.isInstance(original.firstOrNull()) == true
        val itemConstructor = if (legacy) dmLegacyItemConstructor else dmPrismItemConstructor
        if (itemConstructor == null || dmMenuClickInterfaceClass == null) return

        val extended = mutableListOf<Any>()
        for (action in DM_ACTION_DOWNLOAD..DM_ACTION_WHATSAPP_STORY) {
            createDmMenuItem(ctx, param.thisObject, action, legacy)?.let { extended += it }
        }
        if (extended.isEmpty()) return
        extended.addAll(original.filterNotNull())
        param.args[0] = extended
    }

    private fun inferDmMenuItemShape(sample: Any?) {
        if (sample == null) return
        val cls = sample.javaClass
        if (dmMenuClickInterfaceClass == null) dmMenuClickInterfaceClass = findDmClickInterface(cls)
        if (dmLegacyItemConstructor == null && hasDeclaredStringField(cls, "A03")) {
            dmLegacyItemConstructor = findConstructor(cls, 10)
        }
        if (dmPrismItemConstructor == null && hasDeclaredStringField(cls, "A0G")) {
            dmPrismItemConstructor = findConstructor(cls, 24)
        }
    }

    private fun findDmClickInterface(itemClass: Class<*>): Class<*>? {
        itemClass.declaredFields.forEach { field ->
            if (isDmClickInterface(field.type)) return field.type
        }
        itemClass.declaredConstructors.forEach { constructor ->
            constructor.parameterTypes.forEach { type ->
                if (isDmClickInterface(type)) return type
            }
        }
        return null
    }

    private fun isDmClickInterface(type: Class<*>?): Boolean {
        if (type == null || !type.isInterface) return false
        val hasCanClick = type.declaredMethods.any {
            it.name == "Bbt" && it.parameterTypes.isEmpty() && it.returnType == java.lang.Boolean.TYPE
        }
        val hasClick = type.declaredMethods.any {
            it.name == "EUS" && it.parameterTypes.isEmpty() && it.returnType == java.lang.Void.TYPE
        }
        return hasCanClick && hasClick
    }

    private fun captureDmMenuAnchor(param: XC_MethodHook.MethodHookParam<*>) {
        param.args.filterIsInstance<View>().firstOrNull()?.let { view ->
            dmMenuAnchors[param.thisObject] = view
        }
    }

    private fun createDmMenuItem(context: Context?, popup: Any?, action: Int, legacy: Boolean): Any? {
        return runCatching {
            val constructor = (if (legacy) dmLegacyItemConstructor else dmPrismItemConstructor) ?: return@runCatching null
            if (legacy) {
                val args = arrayOfNulls<Any>(10)
                args[1] = loadDmMenuIcon(context, action)
                args[2] = createDmMenuClickProxy(popup, action)
                args[4] = dmActionLabel(action)
                args[6] = false
                args[7] = true
                args[8] = false
                args[9] = false
                constructor.newInstance(*args)
            } else {
                val args = arrayOfNulls<Any>(24)
                args[0] = loadDmMenuIcon(context, action)
                args[4] = createDmMenuClickProxy(popup, action)
                args[9] = -1
                args[13] = 0
                args[15] = 0
                args[16] = dmActionLabel(action)
                args[18] = false
                args[19] = true
                args[20] = false
                args[21] = false
                args[22] = true
                args[23] = false
                constructor.newInstance(*args)
            }
        }.getOrNull()
    }

    private fun createDmMenuClickProxy(popup: Any?, action: Int): Any? {
        val clickInterface = dmMenuClickInterfaceClass ?: return null
        return Proxy.newProxyInstance(clickInterface.classLoader, arrayOf(clickInterface)) { proxy, method, args ->
            when (method.name) {
                "Bbt" -> true
                "EUS" -> {
                    dismissPopup(popup)
                    handleDmContextAction(popup, action)
                    null
                }
                "toString" -> "PurrfectInstaDmMenuAction"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> args != null && args.isNotEmpty() && proxy === args[0]
                else -> defaultResult(method.returnType)
            }
        }
    }

    private fun dismissPopup(popup: Any?) {
        runCatching {
            when (popup) {
                is PopupWindow -> popup.dismiss()
                else -> popup?.javaClass?.methods?.firstOrNull { it.name == "dismiss" && it.parameterTypes.isEmpty() }?.invoke(popup)
            }
        }
    }

    private fun handleDmContextAction(popup: Any?, action: Int) {
        val context = dmContextFromPopup(popup) ?: currentActivity ?: androidContext
        val anchor = dmMenuAnchors[popup]
        val url = resolveDmContextUrl(popup, anchor, null)
        handleResolvedDmAction(context, popup, anchor, action, null, url)
    }

    private fun runDmMessageActionAfterDismiss(action: Int, context: Context, view: View?, source: Any?, sources: Array<Any?>?) {
        val url = resolveDmContextUrl(null, view, sources)
        restoreDmMessageActionsUiSoon(source, sources)
        mainHandler.postDelayed({
            handleResolvedDmAction(stableDmActionContext(context), null, view, action, sources, url)
        }, 80L)
    }

    private fun runResolvedDmMessageAction(
        action: Int,
        context: Context,
        view: View?,
        sources: Array<Any?>?,
        resolvedUrl: String?
    ) {
        restoreDmMessageActionsUiSoon(null, sources)
        mainHandler.postDelayed({
            handleResolvedDmAction(stableDmActionContext(context), null, view, action, sources, resolvedUrl)
        }, 80L)
    }

    private fun handleResolvedDmAction(
        context: Context,
        popup: Any?,
        anchor: View?,
        action: Int,
        sources: Array<Any?>?,
        resolvedUrl: String?
    ) {
        val url = resolvedUrl ?: resolveDmContextUrl(popup, anchor, sources)
        if (url == null) {
            if (action == DM_ACTION_DOWNLOAD && tryDownloadVoiceFallbackForCurrentMessage(context)) return
            Toast.makeText(context, "No media found for this message", Toast.LENGTH_SHORT).show()
            return
        }
        if (action == DM_ACTION_DOWNLOAD && looksLikeProfileImageUrl(url)) {
            if (tryDownloadVoiceFallbackForCurrentMessage(context)) return
            Toast.makeText(context, "No media found for this message", Toast.LENGTH_SHORT).show()
            return
        }
        val isAudio = isAudioUrl(url)
        val isVideo = !isAudio && (mimeTypeForUrl(url).startsWith("video") || url.substringBefore('?').lowercase(Locale.US).contains(".mp4"))
        when (action) {
            DM_ACTION_DOWNLOAD -> enqueueDownload(url)
            else -> if (isAudio) {
                Toast.makeText(context, "Voice messages can only be downloaded", Toast.LENGTH_SHORT).show()
            } else {
                when (action) {
                    DM_ACTION_OPEN_APPS -> openDmMediaByPhoneApps(context, url, isVideo)
                    DM_ACTION_PREVIEW -> {
                        if (isVideo) openDmMediaByPhoneApps(context, url, true) else previewDmMedia(context, url)
                    }
                    DM_ACTION_REPOST -> {
                        if (maybeDeferDmModalAction(context, action, url)) return
                        shareDmMediaToPackage(context, url, isVideo, context.packageName, "Share failed")
                    }
                    DM_ACTION_WHATSAPP_STORY -> {
                        if (maybeDeferDmModalAction(context, action, url)) return
                        shareDmMediaToWhatsAppStory(context, url, isVideo)
                    }
                }
            }
        }
    }

    private fun resolveDmContextUrl(popup: Any?, explicitAnchor: View?, actionSources: Array<Any?>? = null): String? {
        val anchor = explicitAnchor ?: popup?.let { dmMenuAnchors[it] }
        val preferAudio = looksLikeVoiceMessageContext(actionSources, anchor)
        if (preferAudio) dmLastVoiceContextAtMs = System.currentTimeMillis()
        findUrlInDmActionSources(actionSources, preferAudio)?.let { return it }
        if (preferAudio) {
            findRecentDmAudioUrl()?.let { return it }
            logInfo("DM voice context detected but no audio URL was resolved")
            return null
        }
        findDmUrlNear(anchor)?.let { return it }
        if (anchor != null || !actionSources.isNullOrEmpty()) return null
        synchronized(recentMediaUrls) {
            return recentMediaUrls.asReversed().firstOrNull { !looksLikeProfileImageUrl(it) }
        }
    }

    private fun findDmUrlNear(anchor: View?): String? {
        if (anchor == null) return null
        val candidates = mutableListOf<ViewUrlCandidate>()
        var current: View? = anchor
        var level = 0
        while (current != null && level++ < 8) {
            collectDmViewUrlCandidates(current, anchor, candidates, 0, intArrayOf(0))
            current = current.parent as? View
        }
        return candidates.minWithOrNull(
            compareBy<ViewUrlCandidate> { it.distance }
                .thenByDescending { it.area }
        )?.url
    }

    private fun collectDmViewUrlCandidates(
        view: View,
        target: View,
        out: MutableList<ViewUrlCandidate>,
        depth: Int,
        visited: IntArray
    ) {
        if (depth > 5 || visited[0]++ > 140) return
        val url = extractMediaUrlFromView(view)
        if (url != null && !looksLikeProfileImageUrl(url)) {
            out += ViewUrlCandidate(url, (view.width * view.height).coerceAtLeast(1), distanceBetween(view, target))
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectDmViewUrlCandidates(view.getChildAt(i), target, out, depth + 1, visited)
            }
        }
    }

    private fun extractMediaUrlFromView(view: View): String? {
        dmImageViewUrls[view]?.takeIf { looksLikeMediaUrl(it) }?.let { return it }
        storyTrayImageUrls[view]?.takeIf { looksLikeMediaUrl(it) }?.let { return it }
        invokeUrlGetter(view, "getTypedUrl")?.let { return it }
        invokeUrlGetter(view, "getTrackingUrl")?.let { return it }
        extractUrlFromView(view)?.takeIf { looksLikeMediaUrl(it) }?.let { return it }
        runCatching {
            view.javaClass.methods
                .filter { it.parameterTypes.isEmpty() && (it.returnType == String::class.java || it.returnType == Uri::class.java) }
                .forEach { method ->
                    method.isAccessible = true
                    val value = method.invoke(view)?.toString()
                    if (value != null && looksLikeMediaUrl(value)) return value
                }
        }
        return null
    }

    private fun distanceBetween(a: View, b: View): Int {
        val aLoc = IntArray(2)
        val bLoc = IntArray(2)
        a.getLocationOnScreen(aLoc)
        b.getLocationOnScreen(bLoc)
        val ax = aLoc[0] + a.width / 2
        val ay = aLoc[1] + a.height / 2
        val bx = bLoc[0] + b.width / 2
        val by = bLoc[1] + b.height / 2
        return kotlin.math.abs(ax - bx) + kotlin.math.abs(ay - by)
    }

    private fun previewDmMedia(context: Context, url: String) {
        val activity = findActivity(context)
        if (activity == null || (isInstagramModalActivity(activity) && !isActivityHistoryModal(activity))) {
            openDmMediaByPhoneApps(stableDmActionContext(context), url, false)
            return
        }
        showImagePreview(activity, dmActionLabel(DM_ACTION_PREVIEW), url)
    }

    private fun findUrlInDmActionSources(actionSources: Array<Any?>?, preferAudio: Boolean): String? {
        if (actionSources.isNullOrEmpty()) return null
        val candidates = mutableListOf<MediaUrlCandidate>()
        val seen = IdentityHashMap<Any, Boolean>()
        actionSources.forEach { source ->
            collectDmObjectUrlCandidates(source, candidates, seen, 0, "arg", 0)
        }
        val best = bestDmMediaUrlCandidate(candidates, preferAudio)
            ?: if (preferAudio) bestDmAudioFallbackCandidate(candidates) else null
        if (best != null) {
            if (best.audio) rememberKnownDmAudioUrl(best.url)
            rememberMediaUrl(best.url)
            return best.url
        }
        return null
    }

    private fun collectDmObjectUrlCandidates(
        value: Any?,
        out: MutableList<MediaUrlCandidate>,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
        path: String,
        score: Int
    ) {
        if (value == null || depth > 5 || out.size > 80) return
        when (value) {
            is String -> {
                addDmUrlCandidate(value, out, path, score + 10)
                return
            }
            is Uri -> {
                addDmUrlCandidate(value.toString(), out, path, score + 10)
                return
            }
        }

        val cls = value.javaClass
        if (isSimpleDmUrlValue(cls) || shouldSkipDmUrlObject(value, cls) || seen.containsKey(value)) return
        seen[value] = true

        val classPath = "$path@${cls.name}"
        val localScore = score + scoreDmUrlName(cls.name) + scoreDmUrlName(path)
        urlFromTypedObject(value)?.let { addDmUrlCandidate(it, out, classPath, localScore + 70) }

        if (cls.isArray) {
            val length = java.lang.reflect.Array.getLength(value).coerceAtMost(30)
            for (i in 0 until length) {
                collectDmObjectUrlCandidates(java.lang.reflect.Array.get(value, i), out, seen, depth + 1, "$path[$i]", localScore)
            }
            return
        }

        if (value is Iterable<*>) {
            var index = 0
            value.forEach { item ->
                if (index >= 30) return@forEach
                collectDmObjectUrlCandidates(item, out, seen, depth + 1, "$path[$index]", localScore)
                index++
            }
            return
        }

        if (value is Map<*, *>) {
            var index = 0
            value.values.forEach { item ->
                if (index >= 30) return@forEach
                collectDmObjectUrlCandidates(item, out, seen, depth + 1, "$path.value$index", localScore)
                index++
            }
            return
        }

        collectDmGetterUrlCandidates(value, out, seen, depth, path, localScore)
        if (!shouldInspectDmUrlFields(cls)) return

        var current: Class<*>? = cls
        var visitedFields = 0
        while (current != null && current != Any::class.java && visitedFields < 120) {
            current.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers) || visitedFields++ >= 120) return@forEach
                runCatching {
                    field.isAccessible = true
                    collectDmObjectUrlCandidates(
                        field.get(value),
                        out,
                        seen,
                        depth + 1,
                        "$path.${field.name}:${field.type.name}",
                        localScore + scoreDmUrlName(field.name) + scoreDmUrlName(field.type.name)
                    )
                }
            }
            current = current.superclass
        }
    }

    private fun collectDmGetterUrlCandidates(
        value: Any,
        out: MutableList<MediaUrlCandidate>,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
        path: String,
        score: Int
    ) {
        if (!shouldInspectDmUrlMethods(value.javaClass)) return
        arrayOf("A0Z", "A0a", "A0Y", "A0d", "A0c", "A0V", "A0L", "A0M", "BW1", "CCF", "getUrl", "getTypedUrl", "getTrackingUrl")
            .forEach { name ->
                runCatching {
                    val method = value.javaClass.getMethod(name)
                    if (method.parameterTypes.isNotEmpty()) return@forEach
                    collectDmObjectUrlCandidates(method.invoke(value), out, seen, depth + 1, "$path#$name", score + scoreDmUrlName(name))
                }
            }
    }

    private fun addDmUrlCandidate(url: String?, out: MutableList<MediaUrlCandidate>, path: String, score: Int) {
        if (url.isNullOrBlank() || looksLikeProfileImageUrl(url)) return
        val audio = isLikelyAudioHttpUrl(url) || (looksLikeAudioPath(path) && looksLikeMediaUrl(url) && !mimeTypeForUrl(url).startsWith("video"))
        val hosted = isLikelyInstagramHostedMediaUrl(url)
        if (!looksLikeMediaUrl(url) && !audio && !hosted) return
        val video = !audio && (mimeTypeForUrl(url).startsWith("video") || url.lowercase(Locale.US).contains(".mp4"))
        val adjusted = score + scoreDmUrlName(url) + if (audio) 90 else if (video) 80 else 20
        out += MediaUrlCandidate(url, adjusted, video, audio, path)
    }

    private fun bestDmMediaUrlCandidate(candidates: List<MediaUrlCandidate>, preferAudio: Boolean): MediaUrlCandidate? {
        return candidates
            .filter { if (preferAudio) it.audio else !it.audio }
            .maxWithOrNull(compareBy<MediaUrlCandidate> { it.score }.thenBy { it.url.length })
    }

    private fun bestDmAudioFallbackCandidate(candidates: List<MediaUrlCandidate>): MediaUrlCandidate? {
        return candidates
            .filter { !it.video && !looksLikeProfileImageUrl(it.url) && (it.audio || isLikelyAudioHttpUrl(it.url) || looksLikeAudioPath(it.path)) }
            .maxWithOrNull(compareBy<MediaUrlCandidate> { it.score }.thenBy { it.url.length })
    }

    private fun shouldInspectDmUrlFields(cls: Class<*>): Boolean {
        val name = cls.name
        if (name.startsWith("com.instagram.common.session")) return false
        if (name.startsWith("com.instagram.direct.thread.analytics")) return false
        if (name.startsWith("android.") || name.startsWith("androidx.")) return false
        return name.startsWith("p000X.") || name.startsWith("X.") || name.startsWith("com.instagram.")
    }

    private fun shouldInspectDmUrlMethods(cls: Class<*>): Boolean {
        val name = cls.name
        return name.startsWith("p000X.") || name.startsWith("X.") || name.startsWith("com.instagram.")
    }

    private fun shouldSkipDmUrlObject(value: Any, cls: Class<*>): Boolean {
        if (value is Context || value is View || value is Drawable || value is Bitmap) return true
        val name = cls.name
        return name.contains("UserSession") ||
            name.contains("DirectThreadAnalytics") ||
            name.contains("Logger") ||
            name.contains("Logging") ||
            name.contains("Coroutine") ||
            name.contains("Fragment") ||
            name.contains("Activity") ||
            name.startsWith("java.lang.reflect.")
    }

    private fun isSimpleDmUrlValue(cls: Class<*>): Boolean {
        return cls.isPrimitive ||
            Number::class.java.isAssignableFrom(cls) ||
            cls == java.lang.Boolean::class.java ||
            cls == java.lang.Character::class.java ||
            Enum::class.java.isAssignableFrom(cls) ||
            cls == Class::class.java
    }

    private fun scoreDmUrlName(value: String?): Int {
        val lower = value?.lowercase(Locale.US) ?: return 0
        var score = 0
        if (lower.contains("video") || lower.contains("playback") || lower.contains("progressive") || lower.contains("download")) score += 60
        if (lower.contains("audio") || lower.contains("voice") || lower.contains("sound") || lower.contains("audioclip")) score += 70
        if (lower.contains("image") || lower.contains("photo") || lower.contains("media") || lower.contains("url")) score += 25
        if (lower.contains("imageurl") || lower.contains("extendedimageurl")) score += 35
        if (lower.contains("thumbnail") || lower.contains("cover") || lower.contains("avatar") || lower.contains("profile") || lower.contains("sticker") || lower.contains("emoji")) score -= 45
        if (lower.contains("direct") || lower.contains("messagethread")) score += 25
        if (lower.contains("3tp") || lower.contains("8uj") || lower.contains("kfy")) score += 30
        return score
    }

    private fun invokeUrlGetter(value: Any, methodName: String): String? {
        return runCatching {
            val method = value.javaClass.getMethod(methodName)
            if (method.parameterTypes.isNotEmpty()) return@runCatching null
            urlFromTypedObject(method.invoke(value))
        }.getOrNull()
    }

    private fun urlFromTypedObject(value: Any?): String? {
        return when (value) {
            is String -> value.takeIf { looksLikeMediaUrl(it) || isLikelyAudioHttpUrl(it) }
            null -> null
            else -> runCatching {
                val method = value.javaClass.getMethod("getUrl")
                val url = method.invoke(value) as? String
                url?.takeIf { looksLikeMediaUrl(it) || isLikelyAudioHttpUrl(it) }
            }.getOrNull()
        }
    }

    private fun rememberDmImageUrl(view: View, url: String) {
        dmImageViewUrls[view] = url
        if (!looksLikeProfileImageUrl(url)) rememberMediaUrl(url)
    }

    private fun rememberKnownDmAudioUrl(url: String?) {
        if (url.isNullOrBlank() || !url.startsWith("http") || looksLikeProfileImageUrl(url)) return
        synchronized(dmKnownAudioUrls) {
            dmKnownAudioUrls.remove(url)
            dmKnownAudioUrls.addFirst(url)
            while (dmKnownAudioUrls.size > 40) dmKnownAudioUrls.removeLast()
        }
    }

    private fun findRecentDmAudioUrl(): String? {
        synchronized(dmKnownAudioUrls) {
            if (dmKnownAudioUrls.isNotEmpty()) return dmKnownAudioUrls.peekFirst()
        }
        synchronized(dmRecentUrls) {
            pruneDmRecentUrls()
            return dmRecentUrls.firstOrNull { isAudioUrl(it.url) }?.url
        }
    }

    private fun looksLikeVoiceMessageContext(sources: Array<Any?>?, anchor: View?): Boolean {
        if (anchor != null && viewContainsVoiceHint(anchor, 0, intArrayOf(0))) return true
        if (sources == null) return false
        val seen = IdentityHashMap<Any, Boolean>()
        sources.forEach { source ->
            val viewModel = findDmMessageActionsViewModel(source)
            if (looksLikeVoiceToken(enumWireField(viewModel, "A07"))) return true
            if (objectContainsVoiceHint(source, seen, 0)) return true
        }
        return false
    }

    private fun viewContainsVoiceHint(view: View?, depth: Int, visited: IntArray): Boolean {
        if (view == null || depth > 4 || visited[0]++ > 80) return false
        if (looksLikeVoiceToken(view.contentDescription?.toString())) return true
        runCatching {
            if (view.id != View.NO_ID && looksLikeVoiceToken(view.resources.getResourceEntryName(view.id))) return true
        }
        if (view is TextView && looksLikeVoiceToken(view.text?.toString())) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (viewContainsVoiceHint(view.getChildAt(i), depth + 1, visited)) return true
            }
        }
        return false
    }

    private fun objectContainsVoiceHint(value: Any?, seen: IdentityHashMap<Any, Boolean>, depth: Int): Boolean {
        if (value == null || depth > 4) return false
        if (value is CharSequence || value is Uri) return looksLikeVoiceToken(value.toString())
        val cls = value.javaClass
        if (Enum::class.java.isAssignableFrom(cls)) return looksLikeVoiceToken(enumName(value)) || looksLikeVoiceToken(enumWire(value))
        if (isSimpleDmUrlValue(cls) || value is Context || value is View || value is Drawable || value is Bitmap) return false
        if (seen.containsKey(value)) return false
        seen[value] = true
        if (looksLikeVoiceToken(cls.name)) return true
        if (cls.isArray) {
            val length = java.lang.reflect.Array.getLength(value).coerceAtMost(20)
            for (i in 0 until length) if (objectContainsVoiceHint(java.lang.reflect.Array.get(value, i), seen, depth + 1)) return true
            return false
        }
        if (value is Iterable<*>) {
            var index = 0
            value.forEach { item ->
                if (index++ >= 20) return@forEach
                if (objectContainsVoiceHint(item, seen, depth + 1)) return true
            }
            return false
        }
        if (!shouldInspectDmUrlFields(cls)) return false
        var current: Class<*>? = cls
        var visitedFields = 0
        while (current != null && current != Any::class.java && visitedFields < 80) {
            current.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers) || visitedFields++ >= 80) return@forEach
                if (looksLikeVoiceToken(field.name) || looksLikeVoiceToken(field.type.name)) return true
                runCatching {
                    field.isAccessible = true
                    if (objectContainsVoiceHint(field.get(value), seen, depth + 1)) return true
                }
            }
            current = current.superclass
        }
        return false
    }

    private fun looksLikeVoiceToken(value: String?): Boolean {
        val lower = value?.lowercase(Locale.US) ?: return false
        return lower.contains("voice_message") ||
            lower.contains("voice message") ||
            lower.contains("voice_note") ||
            lower.contains("voice note") ||
            lower.contains("audio_message") ||
            lower.contains("audio message") ||
            lower.contains("audio_clip") ||
            lower.contains("audioclip") ||
            lower.contains("audio clip") ||
            lower == "voice" ||
            lower == "audio"
    }

    private fun looksLikeAudioPath(path: String?): Boolean {
        val lower = path?.lowercase(Locale.US) ?: return false
        return lower.contains("voice") || lower.contains("audio") || lower.contains("audioclip")
    }

    private fun currentStackLooksLikeVoice(): Boolean {
        return Thread.currentThread().stackTrace.any {
            looksLikeVoiceToken(it.className) || looksLikeVoiceToken(it.methodName)
        }
    }

    private fun isAudioUrl(url: String?): Boolean {
        val lower = url?.lowercase(Locale.US) ?: return false
        val known = synchronized(dmKnownAudioUrls) { dmKnownAudioUrls.contains(url) }
        return known ||
            lower.contains(".m4a") ||
            lower.contains(".aac") ||
            lower.contains(".mp3") ||
            lower.contains(".opus") ||
            lower.contains(".ogg") ||
            lower.contains("audio") ||
            lower.contains("voice") ||
            lower.contains("audioclip") ||
            lower.contains("/audio/")
    }

    private fun isLikelyAudioHttpUrl(url: String?): Boolean {
        val lower = url?.lowercase(Locale.US) ?: return false
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        if (!isAudioUrl(lower)) return false
        return lower.contains("cdninstagram.com") ||
            lower.contains("fbcdn.net") ||
            lower.contains("fbsbx.com") ||
            lower.contains("facebook.com")
    }

    private fun isLikelyInstagramHostedMediaUrl(url: String?): Boolean {
        val lower = url?.lowercase(Locale.US) ?: return false
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        if (looksLikeProfileImageUrl(lower)) return false
        return lower.contains("cdninstagram.com") || lower.contains("fbcdn.net") || lower.contains("fbsbx.com")
    }

    private fun tryDownloadVoiceFallbackForCurrentMessage(context: Context): Boolean {
        if (!hasFreshDmVoiceContext()) return false
        findRecentDmAudioUrl()?.let { url ->
            rememberKnownDmAudioUrl(url)
            enqueueDownload(url)
            return true
        }
        return tryDownloadRecentVoiceCache(context, requireVoiceContext = true)
    }

    private fun hasFreshDmVoiceContext(): Boolean {
        val age = System.currentTimeMillis() - dmLastVoiceContextAtMs
        return age in 0..3_500L
    }

    private fun tryDownloadRecentVoiceCache(context: Context, requireVoiceContext: Boolean): Boolean {
        val age = System.currentTimeMillis() - dmLastVoiceContextAtMs
        if (requireVoiceContext && (age < 0 || age > 15_000L)) return false
        Thread({
            val source = runCatching { findRecentVoiceCacheFile(context, if (requireVoiceContext) 20 * 60_000L else 4 * 60_000L) }.getOrNull()
            if (source == null) {
                mainHandler.post { Toast.makeText(context, "No media found for this message", Toast.LENGTH_SHORT).show() }
                return@Thread
            }
            runCatching { saveLocalVoiceFile(context, source) }
                .onSuccess { mainHandler.post { Toast.makeText(context, "Voice message saved", Toast.LENGTH_SHORT).show() } }
                .onFailure {
                    logError("DM voice cache fallback failed", it)
                    mainHandler.post { Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show() }
                }
        }, "PurrfectInstaVoiceExport").start()
        return true
    }

    private fun findRecentVoiceCacheFile(context: Context, windowMs: Long): File? {
        val roots = mutableListOf<File>()
        fun add(root: File?) {
            if (root != null && root.exists() && roots.none { it.absolutePath == root.absolutePath }) roots += root
        }
        add(context.cacheDir)
        add(context.filesDir)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) add(context.codeCacheDir)
        add(context.externalCacheDir)
        val cutoff = System.currentTimeMillis() - windowMs.coerceAtLeast(60_000L)
        val best = VoiceCacheCandidate()
        val visited = intArrayOf(0)
        roots.forEach { scanVoiceCache(it, cutoff, 0, visited, best) }
        return best.file
    }

    private fun scanVoiceCache(file: File?, cutoff: Long, depth: Int, visited: IntArray, best: VoiceCacheCandidate) {
        if (file == null || !file.exists() || depth > 7 || visited[0]++ > 2600) return
        if (file.isDirectory) {
            val lower = file.absolutePath.lowercase(Locale.US)
            val childLimit = if (
                lower.contains("cache") || lower.contains("exo") || lower.contains("audio") ||
                lower.contains("voice") || lower.contains("direct") || lower.contains("media")
            ) 140 else 60
            file.listFiles()?.take(childLimit)?.forEach { child -> scanVoiceCache(child, cutoff, depth + 1, visited, best) }
            return
        }

        val length = file.length()
        val modified = file.lastModified()
        if (length < 2_048L || modified < cutoff) return
        val lower = file.absolutePath.lowercase(Locale.US)
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.endsWith(".gif") || lower.contains("/image") || lower.contains("\\image")
        ) return

        var score = 0
        if (lower.contains("voice")) score += 120
        if (lower.contains("audio")) score += 100
        if (lower.contains("audioclip")) score += 120
        if (lower.contains("direct")) score += 35
        if (lower.contains("exo")) score += 25
        if (lower.contains("media")) score += 10
        if (lower.endsWith(".m4a") || lower.endsWith(".aac") || lower.endsWith(".opus") || lower.endsWith(".ogg") || lower.endsWith(".mp3")) score += 140
        if (lower.endsWith(".mp4") || lower.endsWith(".exo")) score += 45
        score += (length / 65_536L).toInt().coerceAtMost(50)
        score += ((modified - cutoff) / 60_000L).toInt().coerceIn(0, 50)
        if (score > best.score || (score == best.score && modified > best.modified)) {
            best.file = file
            best.score = score
            best.modified = modified
        }
    }

    private fun saveLocalVoiceFile(context: Context, source: File) {
        val filename = "purrfect_insta_voice_${System.currentTimeMillis()}${voiceExtension(source)}"
        val mime = voiceMimeType(source)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/PurrfectInsta/Voice Messages")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Audio MediaStore insert failed")
            try {
                java.io.FileInputStream(source).use { input ->
                    context.contentResolver.openOutputStream(uri)?.use { output -> copyStream(input, output) }
                        ?: error("Audio output stream unavailable")
                }
            } catch (throwable: Throwable) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw throwable
            }
            ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, this, null, null)
            }
            return
        }

        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val dir = File(root, "PurrfectInsta/Voice Messages")
        if (!dir.exists() && !dir.mkdirs()) error("Cannot create voice folder")
        val outFile = File(dir, filename)
        java.io.FileInputStream(source).use { input ->
            java.io.FileOutputStream(outFile).use { output -> copyStream(input, output) }
        }
        outFile.setReadable(true, false)
    }

    private fun voiceExtension(file: File): String {
        val lower = file.name.lowercase(Locale.US)
        return when {
            lower.endsWith(".opus") -> ".opus"
            lower.endsWith(".ogg") -> ".ogg"
            lower.endsWith(".mp3") -> ".mp3"
            lower.endsWith(".aac") -> ".aac"
            else -> ".m4a"
        }
    }

    private fun voiceMimeType(file: File): String {
        return when (voiceExtension(file)) {
            ".opus", ".ogg" -> "audio/ogg"
            ".mp3" -> "audio/mpeg"
            ".aac" -> "audio/aac"
            else -> "audio/mp4"
        }
    }

    private fun maybeDeferDmModalAction(context: Context, action: Int, url: String): Boolean {
        if (action !in listOf(DM_ACTION_OPEN_APPS, DM_ACTION_PREVIEW, DM_ACTION_REPOST, DM_ACTION_WHATSAPP_STORY)) return false
        val activity = findActivity(context)
        if (!isInstagramModalActivity(activity) || isActivityHistoryModal(activity)) return false
        dmPendingDeferredAction = DeferredDmAction(action, url, System.currentTimeMillis())
        dismissDmModalWithBack(activity)
        mainHandler.postDelayed({ drainDeferredDmAction(dmLatestStableActionActivity?.get()) }, 900L)
        return true
    }

    private fun dismissDmModalWithBack(activity: Activity?) {
        if (activity == null) return
        mainHandler.post {
            runCatching {
                if (activity.isFinishing || activity.isDestroyed) return@post
                runCatching { activity.onBackPressed() }.getOrElse {
                    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
                    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
                }
            }.onFailure { logError("Failed to dismiss DM modal", it) }
        }
    }

    private fun drainDeferredDmAction(hostActivity: Activity?) {
        val pending = dmPendingDeferredAction ?: return
        if (hostActivity == null) {
            if (System.currentTimeMillis() - pending.createdAtMs > 10_000L) dmPendingDeferredAction = null
            return
        }
        if (isInstagramModalActivity(hostActivity) && !isActivityHistoryModal(hostActivity)) {
            if (System.currentTimeMillis() - pending.createdAtMs < 5_000L) return
            dmPendingDeferredAction = null
            return
        }
        dmPendingDeferredAction = null
        mainHandler.postDelayed({
            handleResolvedDmAction(hostActivity, null, null, pending.action, null, pending.url)
        }, 220L)
    }

    private fun stableDmActionContext(context: Context): Context {
        val activity = findActivity(context)
        if (isInstagramModalActivity(activity) && !isActivityHistoryModal(activity)) {
            context.applicationContext?.let { return it }
        }
        return context
    }

    private fun isInstagramModalActivity(activity: Activity?): Boolean {
        return activity?.javaClass?.name == "com.instagram.modal.ModalActivity"
    }

    private fun isActivityHistoryModal(activity: Activity?): Boolean {
        if (!isInstagramModalActivity(activity)) return false
        return runCatching {
            val text = "${activity?.intent?.data} ${activity?.intent?.extras}".lowercase(Locale.US)
            text.contains("instaeclipse_activity_history") || text.contains("purrfect_activity_history")
        }.getOrDefault(false)
    }

    private fun intentTargetsInstagramModal(intent: Intent?): Boolean {
        if (intent == null) return false
        return runCatching { intent.component?.className == "com.instagram.modal.ModalActivity" }.getOrDefault(false) ||
            intent.toString().contains("com.instagram.modal.ModalActivity")
    }

    private fun intentMentionsActivityHistory(intent: Intent?): Boolean {
        if (intent == null) return false
        return runCatching {
            val text = "$intent ${intent.data} ${intent.extras}".lowercase(Locale.US)
            text.contains("instaeclipse_activity_history") || text.contains("purrfect_activity_history")
        }.getOrDefault(false)
    }

    private fun findDmMessageActionsViewModel(source: Any?): Any? {
        val viewModelClass = dmMessageActionsViewModelClass ?: return null
        if (source == null) return null
        if (viewModelClass.isInstance(source)) return source
        var cls: Class<*>? = source.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers) || !viewModelClass.isAssignableFrom(field.type)) return@forEach
                runCatching {
                    field.isAccessible = true
                    field.get(source)
                }.getOrNull()?.let { return it }
            }
            cls = cls.superclass
        }
        return null
    }

    private fun fieldValueDeep(item: Any?, name: String?): Any? {
        if (item == null || name == null) return null
        var cls: Class<*>? = item.javaClass
        while (cls != null && cls != Any::class.java) {
            runCatching {
                val field = cls.getDeclaredField(name)
                field.isAccessible = true
                return field.get(item)
            }
            cls = cls.superclass
        }
        return null
    }

    private fun dmMenuIconResourceId(context: Context?, action: Int): Int {
        val ctx = context ?: currentActivity ?: androidContext
        val names = when (action) {
            DM_ACTION_DOWNLOAD -> arrayOf("fb_ic_download_outline_24", "fb_ic_photo_download_outline_24")
            DM_ACTION_OPEN_APPS -> arrayOf("fb_ic_share_external_outline_24", "fb_ic_share_android_outline_24")
            DM_ACTION_PREVIEW -> arrayOf("fb_ic_eye_outline_24", "instagram_eye_pano_outline_24")
            DM_ACTION_REPOST -> arrayOf(
                "instagram_reshare_outline_24",
                "instagram_reshare_pano_outline_24",
                "instagram_reshare_pano_filled_24",
                "instagram_reshare_filled_24",
                "fb_ic_reshare_24",
                "fb_ic_reshare_outline_20",
                "fb_ic_reshare_20",
                "groups_reshare_send_icon",
                "fb_ic_share_android_outline_24"
            )
            else -> arrayOf(
                "fb_ic_app_whatsapp_status_plus_outline_24",
                "fb_ic_app_whatsapp_status_outline_20",
                "fb_ic_app_whatsapp_outline_24"
            )
        }
        names.forEach { name ->
            runCatching { ctx.resources.getIdentifier(name, "drawable", ctx.packageName) }
                .getOrDefault(0)
                .takeIf { it != 0 }
                ?.let { return it }
        }
        return when (action) {
            DM_ACTION_DOWNLOAD -> android.R.drawable.stat_sys_download_done
            DM_ACTION_OPEN_APPS -> android.R.drawable.ic_menu_share
            DM_ACTION_PREVIEW -> android.R.drawable.ic_menu_view
            DM_ACTION_REPOST -> android.R.drawable.ic_menu_upload
            else -> android.R.drawable.ic_menu_send
        }
    }

    private fun openDmMediaByPhoneApps(context: Context, url: String, isVideo: Boolean) {
        withExportedDmMedia(context, url, isVideo, { uri ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, dmMimeType(url, isVideo))
                applyReadGrant(context, this, uri)
            }
            val chooser = Intent.createChooser(intent, dmActionLabel(DM_ACTION_OPEN_APPS))
            applyReadGrant(context, chooser, uri)
            launchExternal(context, chooser) { openUrlFallback(context, url) }
        }, {
            openUrlFallback(context, url)
        })
    }

    private fun shareDmMediaToPackage(context: Context, url: String, isVideo: Boolean, packageName: String?, errorText: String) {
        withExportedDmMedia(context, url, isVideo, { uri ->
            val intent = buildDmSendIntent(context, uri, dmMimeType(url, isVideo)).apply {
                if (!packageName.isNullOrBlank()) setPackage(packageName)
            }
            launchExternal(context, intent) {
                val chooser = Intent.createChooser(buildDmSendIntent(context, uri, dmMimeType(url, isVideo)), dmActionLabel(DM_ACTION_REPOST))
                applyReadGrant(context, chooser, uri)
                launchExternal(context, chooser) { Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show() }
            }
        }, {
            Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show()
        })
    }

    private fun repostStoryMedia(context: Context, url: String, isVideo: Boolean) {
        withExportedStoryRepostMedia(context, url, isVideo, { uri ->
            val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
            val storyIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
                setPackage(context.packageName)
                setDataAndType(uri, mimeType)
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra("interactive_asset_uri", uri)
                putExtra("top_background_color", "#000000")
                putExtra("bottom_background_color", "#000000")
                applyReadGrant(context, this, uri)
            }
            launchExternal(context, storyIntent) {
                val fallback = buildDmSendIntent(context, uri, mimeType).apply {
                    setPackage(context.packageName)
                }
                launchExternal(context, fallback) {
                    Toast.makeText(context, "Story repost failed", Toast.LENGTH_SHORT).show()
                }
            }
        }, {
            Toast.makeText(context, "Story repost failed", Toast.LENGTH_SHORT).show()
        })
    }

    private fun shareDmMediaToWhatsAppStory(context: Context, url: String, isVideo: Boolean) {
        withExportedDmMedia(context, url, isVideo, { uri ->
            val whatsapp = buildDmSendIntent(context, uri, dmMimeType(url, isVideo)).apply {
                setPackage("com.whatsapp")
                putExtra("jid", "status@broadcast")
            }
            launchExternal(context, whatsapp) {
                val business = buildDmSendIntent(context, uri, dmMimeType(url, isVideo)).apply {
                    setPackage("com.whatsapp.w4b")
                    putExtra("jid", "status@broadcast")
                }
                launchExternal(context, business) {
                    Toast.makeText(context, "WhatsApp share failed", Toast.LENGTH_SHORT).show()
                }
            }
        }, {
            Toast.makeText(context, "WhatsApp share failed", Toast.LENGTH_SHORT).show()
        })
    }

    private fun withExportedDmMedia(
        context: Context,
        url: String,
        isVideo: Boolean,
        onReady: (Uri) -> Unit,
        onError: () -> Unit
    ) {
        Thread({
            runCatching { createExportedDmMediaUri(context, url, isVideo) }
                .onSuccess { uri -> mainHandler.post { onReady(uri) } }
                .onFailure {
                    logError("DM context media export failed", it)
                    mainHandler.post(onError)
                }
        }, "PurrfectInstaDmExport").start()
    }

    private fun withExportedStoryRepostMedia(
        context: Context,
        url: String,
        isVideo: Boolean,
        onReady: (Uri) -> Unit,
        onError: () -> Unit
    ) {
        Thread({
            runCatching { createExportedStoryRepostUri(context, url, isVideo) }
                .onSuccess { uri -> mainHandler.post { onReady(uri) } }
                .onFailure {
                    logError("Story repost media export failed", it)
                    mainHandler.post(onError)
                }
        }, "PurrfectInstaStoryRepostExport").start()
    }

    private fun createExportedStoryRepostUri(context: Context, url: String, isVideo: Boolean): Uri {
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
        val extension = if (isVideo) "mp4" else "jpg"
        val filename = buildDownloadFilename(url, DownloadMetadata(username = "story", type = "repost"), extension)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/PurrfectInsta/Story Repost"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val itemUri = context.contentResolver.insert(collection, values)
                ?: error("MediaStore insert failed")
            try {
                context.contentResolver.openOutputStream(itemUri)?.use { out -> downloadToStream(url, out) }
                    ?: error("MediaStore output stream unavailable")
            } catch (throwable: Throwable) {
                runCatching { context.contentResolver.delete(itemUri, null, null) }
                throw throwable
            }
            ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(itemUri, this, null, null)
            }
            return itemUri
        }

        val root = Environment.getExternalStoragePublicDirectory(if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES)
        val dir = File(root, "PurrfectInsta/Story Repost")
        if (!dir.exists() && !dir.mkdirs()) error("Cannot create export folder")
        val file = File(dir, filename)
        java.io.FileOutputStream(file).use { out -> downloadToStream(url, out) }
        file.setReadable(true, false)
        disableFileUriExposure()
        return Uri.fromFile(file)
    }

    private fun createExportedDmMediaUri(context: Context, url: String, isVideo: Boolean): Uri {
        val mimeType = dmMimeType(url, isVideo)
        val filename = "purrfect_insta_dm_${System.currentTimeMillis()}.${extensionForUrl(url)}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/PurrfectInsta/DM Context"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val itemUri = context.contentResolver.insert(collection, values)
                ?: error("MediaStore insert failed")
            try {
                context.contentResolver.openOutputStream(itemUri)?.use { out -> downloadToStream(url, out) }
                    ?: error("MediaStore output stream unavailable")
            } catch (throwable: Throwable) {
                runCatching { context.contentResolver.delete(itemUri, null, null) }
                throw throwable
            }
            ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(itemUri, this, null, null)
            }
            return itemUri
        }

        val root = Environment.getExternalStoragePublicDirectory(if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES)
        val dir = File(root, "PurrfectInsta/DM Context")
        if (!dir.exists() && !dir.mkdirs()) error("Cannot create export folder")
        val file = File(dir, filename)
        java.io.FileOutputStream(file).use { out -> downloadToStream(url, out) }
        file.setReadable(true, false)
        disableFileUriExposure()
        return Uri.fromFile(file)
    }

    private fun downloadToStream(url: String, output: OutputStream) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Instagram 300.0.0.0 Android")
        try {
            connection.inputStream.use { input -> copyStream(input, output) }
        } finally {
            connection.disconnect()
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
        }
    }

    private fun buildDmSendIntent(context: Context, uri: Uri, mimeType: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            applyReadGrant(context, this, uri)
        }
    }

    private fun applyReadGrant(context: Context, intent: Intent, uri: Uri) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            intent.clipData = ClipData.newUri(context.contentResolver, "PurrfectInsta media", uri)
        }
        runCatching {
            context.grantUriPermission(context.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun launchExternal(context: Context, intent: Intent, onError: () -> Unit) {
        val launchContext = context.applicationContext ?: context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        mainHandler.postDelayed({
            runCatching { launchContext.startActivity(intent) }
                .onFailure {
                    logError("DM context launch failed", it)
                    onError()
                }
        }, 350L)
    }

    private fun openUrlFallback(context: Context, url: String) {
        runCatching {
            val chooser = Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(url)), dmActionLabel(DM_ACTION_OPEN_APPS))
            launchExternal(context, chooser) {
                Toast.makeText(context, "Cannot open media", Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(context, "Cannot open media", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dmMimeType(url: String, isVideo: Boolean): String {
        val detected = mimeTypeForUrl(url)
        return when {
            detected != "application/octet-stream" -> detected
            isVideo -> "video/mp4"
            else -> "image/jpeg"
        }
    }

    private fun disableFileUriExposure() {
        runCatching {
            val vmRuntime = Class.forName("android.os.StrictMode").getMethod("disableDeathOnFileUriExposure")
            vmRuntime.invoke(null)
        }
    }

    private fun dmContextFromPopup(popup: Any?): Context? {
        if (popup is PopupWindow) popup.contentView?.context?.let { return it }
        return findContextInObjectGraph(popup) ?: currentActivity ?: androidContext
    }

    private fun dmActionLabel(action: Int): String {
        return dmActionLabels.getOrNull(action) ?: "Action"
    }

    private fun loadDmMenuIcon(context: Context?, action: Int): Drawable? {
        val ctx = context ?: currentActivity ?: androidContext
        val names = when (action) {
            DM_ACTION_DOWNLOAD -> arrayOf("fb_ic_download_outline_24", "fb_ic_photo_download_outline_24")
            DM_ACTION_OPEN_APPS -> arrayOf("fb_ic_share_external_outline_24", "fb_ic_share_android_outline_24")
            DM_ACTION_PREVIEW -> arrayOf("fb_ic_eye_outline_24", "instagram_eye_pano_outline_24")
            DM_ACTION_REPOST -> arrayOf(
                "instagram_reshare_outline_24",
                "instagram_reshare_pano_outline_24",
                "instagram_reshare_pano_filled_24",
                "instagram_reshare_filled_24",
                "fb_ic_reshare_24",
                "fb_ic_reshare_outline_20",
                "fb_ic_reshare_20",
                "groups_reshare_send_icon",
                "fb_ic_share_android_outline_24"
            )
            else -> arrayOf(
                "fb_ic_app_whatsapp_status_plus_outline_24",
                "fb_ic_app_whatsapp_status_outline_20",
                "fb_ic_app_whatsapp_outline_24"
            )
        }
        names.forEach { name ->
            val id = runCatching { ctx.resources.getIdentifier(name, "drawable", ctx.packageName) }.getOrDefault(0)
            if (id != 0) {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ctx.resources.getDrawable(id, ctx.theme)
                } else {
                    @Suppress("DEPRECATION")
                    ctx.resources.getDrawable(id)
                }
            }
        }
        val fallback = when (action) {
            DM_ACTION_DOWNLOAD -> android.R.drawable.stat_sys_download_done
            DM_ACTION_OPEN_APPS -> android.R.drawable.ic_menu_share
            DM_ACTION_PREVIEW -> android.R.drawable.ic_menu_view
            DM_ACTION_REPOST -> android.R.drawable.ic_menu_upload
            else -> android.R.drawable.ic_menu_send
        }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ctx.resources.getDrawable(fallback, ctx.theme)
            } else {
                @Suppress("DEPRECATION")
                ctx.resources.getDrawable(fallback)
            }
        }.getOrNull()
    }

    private fun looksLikeDirectVisualMessageMenu(items: List<*>): Boolean {
        var hasAddSticker = false
        var hasVisualMessage = false
        var hasViewPhoto = false
        var hasVisualAction = false
        items.forEach { item ->
            when (extractLongPressActionWire(item)) {
                "camera_reply_for_expiring_media", "report_bug_for_vm", "details" -> hasVisualAction = true
            }
            val label = extractDmMenuLabel(item)?.lowercase(Locale.US) ?: return@forEach
            if (label.contains("add sticker")) hasAddSticker = true
            if (label.contains("visual") && label.contains("message")) hasVisualMessage = true
            if (label.contains("view photo") || label.contains("view video")) hasViewPhoto = true
            if (label.contains("voice") || label.contains("audio")) hasVisualAction = true
        }
        return hasAddSticker || hasVisualMessage || hasViewPhoto || hasVisualAction
    }

    private fun extractLongPressActionWire(item: Any?): String? {
        val actionClass = dmLongPressActionDataClass ?: return null
        if (item == null || !actionClass.isInstance(item)) return null
        return enumWireField(item, "A04")
    }

    private fun hasInjectedDmItems(context: Context?, items: List<*>): Boolean {
        val labels = (DM_ACTION_DOWNLOAD..DM_ACTION_WHATSAPP_STORY).map { dmActionLabel(it) }.toSet()
        return items.any { item -> extractDmMenuLabel(item)?.let { it in labels } == true }
    }

    private fun extractDmMenuLabel(item: Any?): String? {
        if (item == null) return null
        listOf("A07", "A0G", "A03").forEach { name ->
            stringField(item, name)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        item.javaClass.declaredFields.forEach { field ->
            if (field.type != String::class.java) return@forEach
            runCatching {
                field.isAccessible = true
                field.get(item) as? String
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun stringField(item: Any?, name: String): String? {
        if (item == null) return null
        return runCatching {
            val field = item.javaClass.getDeclaredField(name)
            field.isAccessible = true
            field.get(item) as? String
        }.getOrNull()
    }

    private fun hasDeclaredStringField(cls: Class<*>, name: String): Boolean {
        return runCatching { cls.getDeclaredField(name).type == String::class.java }.getOrDefault(false)
    }

    private fun findConstructor(cls: Class<*>, parameterCount: Int): Constructor<*>? {
        return cls.declaredConstructors.firstOrNull { it.parameterTypes.size == parameterCount }?.apply { isAccessible = true }
    }

    private fun loadFirstClass(vararg classNames: String): Class<*>? {
        return classNames.firstNotNullOfOrNull { name ->
            runCatching { Class.forName(name, false, appClassLoader) }.getOrNull()
        }
    }

    private fun installStoryMentionHook() {
        runSafe("Story mention hook") {
            resolveStoryMentionGetters()
            val buttonMethod = (if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadMethod("MentionButton", appClassLoader)
            } else {
                null
            }) ?: dexBridge.findMethodsUsingStrings("[INTERNAL] Pause Playback")
                .firstOrNull { method ->
                    method.returnType.isArray &&
                        CharSequence::class.java.isAssignableFrom(method.returnType.componentType)
                }?.also { InstagramDexKitCache.saveMethod("MentionButton", it) }
            buttonMethod?.let { method ->
                method.isAccessible = true
                val signature = "${method.declaringClass.name}.${method.name}:mentions-button"
                if (hookedDexMethods.add(signature)) {
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam<*>) {
                                if (!state.enableStoryMentions) return
                                val original = param.result as? Array<*> ?: return
                                if (original.any { it?.toString() == "View Mentions" }) return
                                param.result = original.map { it as? CharSequence ?: it.toString() }
                                    .plus("View Mentions")
                                    .toTypedArray<CharSequence>()
                            }
                        }
                    )
                }
            }

            val clickMethod = (if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadMethod("MentionClick", appClassLoader)
            } else {
                null
            }) ?: dexBridge.findMethodsUsingStrings(
                "explore_viewer",
                "friendships/mute_friend_reel/%s/",
                "[INTERNAL] Pause Playback"
            ).firstOrNull { it.returnType == java.lang.Void.TYPE }?.also { InstagramDexKitCache.saveMethod("MentionClick", it) }
                ?: return@runSafe
            clickMethod.isAccessible = true
            val clickSignature = "${clickMethod.declaringClass.name}.${clickMethod.name}:mentions-click"
            if (!hookedDexMethods.add(clickSignature)) return@runSafe
            XposedBridge.hookMethod(
                clickMethod,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        if (!state.enableStoryMentions) return
                        val tapped = param.args.firstOrNull { it is CharSequence }?.toString() ?: return
                        if (tapped != "View Mentions") return
                        param.result = null
                        val media = findMediaObject(param.thisObject) ?: param.args.firstNotNullOfOrNull { findMediaObject(it) }
                        val context = findContextInObjectGraph(param.thisObject)
                            ?: param.args.firstNotNullOfOrNull { findContextInObjectGraph(it) }
                            ?: currentActivity
                            ?: androidContext
                        showMentionsDialog(context, resolveStoryMentions(media))
                    }
                }
            )
        }
    }

    private fun resolveStoryMentionGetters() {
        if (storyMentionGetterCandidates.isNotEmpty()) return
        if (InstagramDexKitCache.isCacheValid()) {
            InstagramDexKitCache.loadMethods("MentionGetter", appClassLoader)
                ?.takeIf { it.isNotEmpty() }
                ?.let { cached ->
                    storyMentionGetterCandidates.addAll(cached)
                    storyMentionGetterMethod = cached.firstOrNull()
                    logInfo("Loaded story mention getters from cache=${cached.size}")
                    return
                }
        }
        dexBridge.findMethodsUsingStrings("Required value was null.")
            .filter { method ->
                method.declaringClass.name == "com.instagram.feed.media.MediaExtKt" &&
                    method.returnType == java.util.List::class.java &&
                    method.parameterTypes.size == 1
            }
            .forEach { method ->
                method.isAccessible = true
                storyMentionGetterCandidates += method
            }
        storyMentionGetterMethod = storyMentionGetterCandidates.firstOrNull()
        if (storyMentionGetterCandidates.isNotEmpty()) {
            InstagramDexKitCache.saveMethods("MentionGetter", storyMentionGetterCandidates.toList())
        }
    }

    private fun resolveStoryMentions(media: Any?): List<String> {
        if (media == null) return emptyList()
        val candidates = synchronized(storyMentionGetterCandidates) { storyMentionGetterCandidates.toList() }
        if (candidates.isEmpty()) return emptyList()
        if (storyMentionGetterMethod == null || storyMentionGetterMethod == candidates.firstOrNull()) {
            candidates.forEach { candidate ->
                val probe = runCatching { candidate.invoke(null, media) as? List<*> }.getOrNull()
                val first = probe?.firstOrNull()
                if (first != null && first !is String) {
                    storyMentionGetterMethod = candidate
                    return@forEach
                }
            }
        }
        val result = runCatching { storyMentionGetterMethod?.invoke(null, media) as? List<*> }.getOrNull().orEmpty()
        return result.mapNotNull { item ->
            when (item) {
                null, is String -> null
                else -> usernameFromUserObject(item)
            }
        }.distinct()
    }

    private fun usernameFromUserObject(user: Any): String? {
        listOf("getUsername", "getUserName", "Aso", "BKR").forEach { methodName ->
            runCatching {
                val method = user.javaClass.methods.firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() && it.returnType == String::class.java }
                method?.invoke(user) as? String
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        user.javaClass.declaredFields.forEach { field ->
            if (field.type != String::class.java) return@forEach
            val name = field.name.lowercase(Locale.US)
            if (!name.contains("username") && name !in setOf("bkr", "aso", "a0b", "a0c")) return@forEach
            runCatching {
                field.isAccessible = true
                field.get(user) as? String
            }.getOrNull()?.takeIf { it.isNotBlank() && it.length <= 30 }?.let { return it }
        }
        return null
    }

    private fun showMentionsDialog(context: Context, usernames: List<String>) {
        mainHandler.post {
            val dialogContext = findActivity(context) ?: currentActivity
            if (dialogContext == null || dialogContext.isFinishing) {
                Toast.makeText(context, if (usernames.isEmpty()) "No mentions found" else usernames.joinToString(", ") { "@$it" }, Toast.LENGTH_SHORT).show()
                return@post
            }
            val message = if (usernames.isEmpty()) {
                "No mentions found"
            } else {
                usernames.joinToString("\n") { "@$it" }
            }
            AlertDialog.Builder(dialogContext)
                .setTitle("Story Mentions")
                .setMessage(message)
                .setPositiveButton("Copy All") { _, _ ->
                    if (usernames.isNotEmpty()) {
                        val clipboard = dialogContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Story mentions", usernames.joinToString("\n") { "@$it" }))
                        Toast.makeText(dialogContext, "Mentions copied", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun showConfirmRefreshDialog(surface: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
        mainHandler.post {
            val activity = currentActivity
            if (activity == null || activity.isFinishing) {
                onCancel()
                return@post
            }
            val surfaceLabel = if (surface == "feed") "Refresh feed" else "Refresh reels"
            AlertDialog.Builder(activity)
                .setTitle("Confirm refresh")
                .setMessage("Refresh ${if (surface == "feed") "feed" else "reels"}?")
                .setPositiveButton(surfaceLabel) { _, _ -> onConfirm() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
                .setOnCancelListener { onCancel() }
                .show()
        }
    }

    private fun allowRefreshSurface(surface: String, windowMs: Long) {
        val until = System.currentTimeMillis() + windowMs.coerceAtLeast(1_000L)
        if (surface == "feed") allowFeedRefreshUntilMs = until else allowReelsRefreshUntilMs = until
    }

    private fun isRefreshAllowed(surface: String): Boolean {
        val now = System.currentTimeMillis()
        return if (surface == "feed") now <= allowFeedRefreshUntilMs else now <= allowReelsRefreshUntilMs
    }

    private fun isRefreshDialogPending(surface: String): Boolean {
        return if (surface == "feed") feedRefreshDialogPending else reelsRefreshDialogPending
    }

    private fun setRefreshDialogPending(surface: String, pending: Boolean) {
        if (surface == "feed") feedRefreshDialogPending = pending else reelsRefreshDialogPending = pending
    }

    private fun setRefreshing(refreshView: Any?, refreshing: Boolean) {
        runCatching {
            refreshView?.javaClass?.methods
                ?.firstOrNull { it.name == "setRefreshing" && it.parameterTypes.contentEquals(arrayOf(java.lang.Boolean.TYPE)) }
                ?.invoke(refreshView, refreshing)
        }
    }

    private fun clearVisibleRefreshSpinners(activity: Activity?) {
        val root = activity?.window?.decorView ?: return
        fun visit(view: View, depth: Int) {
            if (depth > 10) return
            setRefreshing(view, false)
            if (view is ViewGroup) {
                val count = view.childCount.coerceAtMost(120)
                for (i in 0 until count) visit(view.getChildAt(i), depth + 1)
            }
        }
        visit(root, 0)
    }

    private fun surfaceForRefreshView(view: View?): String? {
        val haystack = refreshHierarchySignal(view)
        if (haystack.contains("direct") || haystack.contains("inbox") || haystack.contains("message_list") ||
            haystack.contains("search") || haystack.contains("profile")
        ) {
            return null
        }
        if (haystack.contains("clips") || haystack.contains("reels") || haystack.contains("mixed_media")) return "reels"
        if (haystack.contains("feed") || haystack.contains("timeline") || haystack.contains("refresh")) return "feed"
        if (isRefreshSurfaceForeground("feed")) return "feed"
        if (isRefreshSurfaceForeground("reels")) return "reels"
        return null
    }

    private fun currentForegroundOrLikelyRefreshSurface(): String? {
        if (isRefreshSurfaceForeground("feed")) return "feed"
        if (isRefreshSurfaceForeground("reels")) return "reels"
        if (!isClearlyBackgroundRefresh("feed")) return "feed"
        if (!isClearlyBackgroundRefresh("reels")) return "reels"
        return null
    }

    private fun isRefreshSurfaceForeground(surface: String): Boolean {
        val activity = currentActivity ?: return false
        if (activity.isFinishing) return false
        if (surface == "feed") {
            val root = activity.window?.decorView
            if (hasVisibleIdContaining(root, "message_list", "direct_inbox", "direct_search", "clips_tab_view_pager")) {
                return false
            }
            return isAnySelected(activity, "feed_tab", "current_feed_tab") ||
                (isAnyVisible(activity, "feed_tab", "current_feed_tab") &&
                    !isAnySelected(activity, "direct_tab", "search_tab", "clips_tab"))
        }
        return isAnySelected(activity, "clips_tab") ||
            (isAnyVisible(activity, "clips_tab") &&
                !isAnySelected(activity, "direct_tab", "search_tab", "feed_tab", "current_feed_tab"))
    }

    private fun isClearlyBackgroundRefresh(surface: String): Boolean {
        val root = currentActivity?.window?.decorView ?: return false
        return if (surface == "feed") {
            hasVisibleIdContaining(
                root,
                "message_list",
                "direct_inbox",
                "direct_search",
                "clips_tab_view_pager",
                "profile_header",
                "search_tab_bar"
            )
        } else {
            hasVisibleIdContaining(
                root,
                "message_list",
                "direct_inbox",
                "direct_search",
                "feed_view",
                "feed_tab",
                "profile_header",
                "search_tab_bar"
            )
        }
    }

    private fun refreshHierarchySignal(view: View?): String {
        val out = StringBuilder()
        var current = view
        repeat(10) {
            val node = current ?: return@repeat
            out.append(' ').append(resourceEntryName(node).orEmpty())
            out.append(' ').append(node.javaClass.name)
            current = node.parent as? View
        }
        return out.toString().lowercase(Locale.US)
    }

    private fun hasVisibleIdContaining(view: View?, vararg needles: String): Boolean {
        if (view == null || view.visibility != View.VISIBLE) return false
        val id = resourceEntryName(view).orEmpty().lowercase(Locale.US)
        if (needles.any { id.contains(it) }) return true
        if (view is ViewGroup) {
            val count = view.childCount.coerceAtMost(120)
            for (i in 0 until count) {
                if (hasVisibleIdContaining(view.getChildAt(i), *needles)) return true
            }
        }
        return false
    }

    private fun isAnySelected(activity: Activity, vararg ids: String): Boolean {
        return ids.any { idName ->
            val id = activity.resources.getIdentifier(idName, "id", activity.packageName)
            id != 0 && isSelectedLike(activity.findViewById(id), 0)
        }
    }

    private fun isAnyVisible(activity: Activity, vararg ids: String): Boolean {
        return ids.any { idName ->
            val id = activity.resources.getIdentifier(idName, "id", activity.packageName)
            id != 0 && (activity.findViewById<View>(id)?.visibility == View.VISIBLE)
        }
    }

    private fun isSelectedLike(view: View?, depth: Int): Boolean {
        if (view == null || view.visibility != View.VISIBLE || depth > 4) return false
        if (view.isSelected || view.isActivated || view.isPressed) return true
        val desc = view.contentDescription?.toString()?.lowercase(Locale.US).orEmpty()
        if (desc.contains("selected") || desc.contains("current")) return true
        if (view is ViewGroup) {
            val count = view.childCount.coerceAtMost(16)
            for (i in 0 until count) {
                if (isSelectedLike(view.getChildAt(i), depth + 1)) return true
            }
        }
        return false
    }

    private fun refreshVisibleRoots(reason: String) {
        mainHandler.post {
            restoreKnownHiddenViewsIfNoHideRules()
            val roots = LinkedHashSet<View>()
            currentActivity?.window?.decorView?.rootView?.let { roots += it }
            synchronized(trackedRoots) { roots += trackedRoots.filter { it.isAttachedToWindow } }
            roots.forEach { root ->
                if (!root.isAttachedToWindow) return@forEach
                scanTree(root, "refresh:$reason", REFRESH_VIEW_SCAN_LIMIT)
                root.post { scanTree(root, "refresh:$reason posted", REFRESH_VIEW_SCAN_LIMIT) }
                root.postDelayed({ scanTree(root, "refresh:$reason delayed") }, 250L)
                root.postDelayed({ scanTree(root, "refresh:$reason settle", REFRESH_VIEW_SCAN_LIMIT) }, 750L)
            }
            currentActivity?.let { activity ->
                wireInstagramEntryPoints(activity)
                applyGhostIndicator(activity)
                applyCaptureOverlay(activity)
                clickDefaultNavigationTab(activity)
            }
        }
    }

    private fun refreshActivity(activity: Activity, reason: String) {
        val root = activity.window?.decorView ?: return
        root.post { scanTree(root, reason, REFRESH_VIEW_SCAN_LIMIT) }
        root.postDelayed({ scanTree(root, "$reason delayed", REFRESH_VIEW_SCAN_LIMIT) }, 250L)
        root.post { wireInstagramEntryPoints(activity) }
        root.postDelayed({ wireInstagramEntryPoints(activity) }, 650L)
        root.postDelayed({ wireInstagramEntryPoints(activity) }, 1_500L)
        root.post { applyCaptureOverlay(activity) }
    }

    private fun hasExplicitHiddenUiRules(current: InstagramFeatureState = state): Boolean {
        return current.hiddenUiElementIdSet.isNotEmpty() || current.hiddenUiElementSelectorSet.isNotEmpty()
    }

    private fun hasAnyViewHideRule(current: InstagramFeatureState = state): Boolean {
        return hasExplicitHiddenUiRules(current) ||
            current.isExtremeMode ||
            current.disableStories ||
            current.disableFeed ||
            current.disableReels ||
            current.disableExplore ||
            current.disableComments ||
            current.enableTeenAppIcons
    }

    private fun hasAnyTextHideRule(current: InstagramFeatureState = state): Boolean {
        return current.disableDiscoverPeople ||
            current.disableComments ||
            current.disableReels ||
            current.disableStories
    }

    private fun hasAnyTextViewWork(current: InstagramFeatureState = state): Boolean {
        return current.isAdBlockEnabled ||
            current.enableCustomDateFormat ||
            current.enableHideChats ||
            current.enableCopyBio ||
            current.enableCopyComment ||
            hasAnyTextHideRule(current)
    }

    private fun hasKnownHiddenUiState(view: View): Boolean {
        return hiddenUiOriginalStates.containsKey(view) ||
            hiddenUiCollapsedWrappers.containsKey(view) ||
            hiddenUiComposerAdjustments.containsKey(view) ||
            hiddenUiRowAdjustments.containsKey(view)
    }

    private fun processView(view: View, reason: String) {
        val current = state
        if (view is TextView && hasAnyTextViewWork(current)) processTextView(view)
        processEntryPointCandidate(view)
        if (current.enableActivityHistory) InstagramActivityHistoryHooks.recordVisibleView(view)
        if (current.isAdBlockEnabled) hideSponsoredSurfaceIfNeeded(view)
        if (current.isGhostLive) hideLivePresenceIfNeeded(view)
        applyDirectGhostSeenControls(view)
        applyDmAnyFileUploadButton(view)
        applyProfilePictureDownload(view)
        applyFeedPostDownloadControls(view)
        if (!enforceHiddenUiState(view) && hasAnyViewHideRule(current) && shouldHideView(view)) hideView(view, reason)
        if (current.enableMonetTheme) applyMonetThemeToView(view)
        if (current.storyRingSize != "default") applyStoryRingScale(view)
        if (current.enableTeenAppIcons) applyTeenIconTweak(view)
        scheduleScan(view, reason)
    }

    private fun processTextView(textView: TextView) {
        val current = state
        if (!hasAnyTextViewWork(current)) return
        val needsRawText = current.isAdBlockEnabled ||
            current.enableCustomDateFormat ||
            current.enableHideChats ||
            current.enableCopyBio ||
            current.enableCopyComment ||
            hasAnyTextHideRule(current)
        if (!needsRawText) return
        val raw = textView.text?.toString().orEmpty()
        val isAdDisclosure = isAdDisclosureText(raw)
        if (current.isAdBlockEnabled && (isSponsoredText(raw) ||
                (isAdDisclosure && (hasFeedAdDisclosureContext(textView) || hasAdDisclosureNeighborContext(textView)))
            )
        ) {
            hideSponsoredContainer(textView)
        } else if (current.isAdBlockEnabled) {
            hideSponsoredSurfaceIfNeeded(textView)
        }
        if (current.enableCustomDateFormat) rewriteRelativeDate(textView, raw)
        if (current.enableHideChats && maybeCollectOrHideChatRow(textView, raw)) return
        if (shouldHideText(raw)) hideView(textView, "text")
        if (current.enableCopyBio) {
            maybeRememberProfileBioCandidate(textView)
            maybeInjectProfileMenuCopyBio(textView)
            maybeInstallCopyBioLongPress(textView)
        }
        if ((current.enableCopyComment || current.enableCopyBio) && shouldMakeCopyable(textView, raw)) {
            textView.setTextIsSelectable(true)
        }
    }

    private fun scheduleScan(view: View, reason: String) {
        if (!state.hasAnyFeedSuppression() &&
            !state.isAdBlockEnabled &&
            !state.enableHideChats &&
            !state.enableNavigationTabCustomization
        ) return
        val root = view.rootView ?: view
        val now = System.currentTimeMillis()
        val last = scannedRoots[root] ?: 0L
        if (now - last < 500L) return
        scannedRoots[root] = now
        mainHandler.post { scanTree(root, reason) }
    }

    private fun scanTree(root: View, reason: String, visitLimit: Int = DEFAULT_VIEW_SCAN_LIMIT) {
        if (!root.isAttachedToWindow) return
        var visited = 0
        fun visit(view: View) {
            if (visited++ > visitLimit) return
            processViewWithoutScheduling(view, reason)
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
        }
        visit(root)
        if (hasExplicitHiddenUiRules() || hiddenUiRowAdjustments.isNotEmpty()) {
            val activeRowAdjustments = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
            collectHiddenUiRowCompactions(root, activeRowAdjustments)
            restoreInactiveHiddenUiRowAdjustments(root, activeRowAdjustments)
        }
    }

    private fun processViewWithoutScheduling(view: View, reason: String) {
        val current = state
        if (view is TextView && hasAnyTextViewWork(current)) processTextView(view)
        processEntryPointCandidate(view)
        if (current.enableActivityHistory) InstagramActivityHistoryHooks.recordVisibleView(view)
        if (current.isAdBlockEnabled) hideSponsoredSurfaceIfNeeded(view)
        if (current.isGhostLive) hideLivePresenceIfNeeded(view)
        applyDirectGhostSeenControls(view)
        applyDmAnyFileUploadButton(view)
        applyProfilePictureDownload(view)
        applyFeedPostDownloadControls(view)
        if (!enforceHiddenUiState(view) && hasAnyViewHideRule(current) && shouldHideView(view)) hideView(view, reason)
        if (current.enableMonetTheme) applyMonetThemeToView(view)
        if (current.enableNavigationTabCustomization) applyNavigationTabRules(view)
    }

    private fun rememberRoot(view: View?) {
        val root = view?.rootView ?: return
        trackedRoots += root
    }

    private fun enforceHiddenUiState(view: View): Boolean {
        if (shouldEnforceHiddenUiView(view)) {
            forceHiddenUiView(view)
            return true
        }
        if (hasExplicitHiddenUiRules() || hasKnownHiddenUiState(view)) {
            restoreHiddenUiView(view)
            enforceComposerUiAdjustment(view)
            enforceHiddenUiRowAdjustment(view)
        }
        return false
    }

    private fun shouldEnforceHiddenUiView(view: View?): Boolean {
        if (view == null || shouldSkipHiddenUiInContext(view.context)) return false
        if (matchesExplicitHiddenUiRule(view)) return true
        val source = hiddenUiCollapsedWrappers[view] ?: return false
        val keepHidden = source !== view && matchesExplicitHiddenUiRule(source)
        if (!keepHidden) hiddenUiCollapsedWrappers.remove(view)
        return keepHidden
    }

    private fun shouldSkipHiddenUiInContext(context: Context?): Boolean {
        return state.enableDmContextMenuOptions &&
            findActivity(context)?.javaClass?.name == "com.instagram.modal.ModalActivity"
    }

    private fun matchesExplicitHiddenUiRule(view: View?): Boolean {
        if (view == null || shouldSkipHiddenUiInContext(view.context)) return false
        if (state.hiddenUiElementIdSet.isEmpty() && state.hiddenUiElementSelectorSet.isEmpty()) return false
        val resourceId = resourceEntryName(view)
        if (resourceId != null && state.hiddenUiElementIdSet.contains(resourceId)) return true
        return matchesHiddenSelector(view)
    }

    private fun rememberHiddenUiState(view: View) {
        if (hiddenUiOriginalStates.containsKey(view)) return
        hiddenUiOriginalStates[view] = HiddenUiState(view)
    }

    private fun forceHiddenUiView(view: View) {
        rememberHiddenUiState(view)
        runCatching {
            hiddenUiInternalChange.set(true)
            view.alpha = 0f
            view.isEnabled = false
            view.isClickable = false
            if (view.visibility != View.GONE) view.visibility = View.GONE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            collapseHiddenUiFootprint(view)
        }.onFailure { logError("Failed to hide explicit Instagram UI view", it) }
        hiddenUiInternalChange.remove()
        if (matchesExplicitHiddenUiRule(view)) collapseHiddenUiNeighbors(view)
    }

    private fun restoreHiddenUiView(view: View) {
        if (hiddenUiCollapsedWrappers.containsKey(view)) return
        val original = hiddenUiOriginalStates.remove(view) ?: return
        runCatching {
            hiddenUiInternalChange.set(true)
            restoreHiddenUiFootprint(view, original)
            view.visibility = original.visibility
            view.alpha = original.alpha
            view.isEnabled = original.enabled
            view.isClickable = original.clickable
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                view.importantForAccessibility = original.importantForAccessibility
            }
        }.onFailure { logError("Failed to restore explicit Instagram UI view", it) }
        hiddenUiInternalChange.remove()
    }

    private fun restoreKnownHiddenViewsIfNoHideRules() {
        if (hasAnyViewHideRule()) return
        val views = LinkedHashSet<View>()
        synchronized(hiddenUiCollapsedWrappers) {
            views += hiddenUiCollapsedWrappers.keys
            views += hiddenUiCollapsedWrappers.values
            hiddenUiCollapsedWrappers.clear()
        }
        synchronized(hiddenUiOriginalStates) { views += hiddenUiOriginalStates.keys }
        synchronized(hiddenUiComposerAdjustments) { views += hiddenUiComposerAdjustments.keys }
        synchronized(hiddenUiRowAdjustments) { views += hiddenUiRowAdjustments.keys }
        views.forEach { view ->
            restoreHiddenUiView(view)
            restoreComposerUiAdjustment(view)
            hiddenUiRowAdjustments.remove(view)?.let { restoreHiddenUiRowAdjustment(view, it) }
            requestHiddenUiLayout(view)
        }
    }

    private fun collapseHiddenUiNeighbors(source: View) {
        collapseHiddenUiWrapper(source)
        collapseGenericActionSlot(source)
        collapseCompoundActionItem(source)
        collapseComposerActionParent(source)
        collapseComposerActionSlot(source)
        adjustComposerUiForHiddenAction(source)
        compactRowsAroundHiddenUiView(source, null)
    }

    private fun collapseHiddenUiWrapper(source: View) {
        val target = findHiddenUiCollapseTarget(source)
        if (target === source) return
        hiddenUiCollapsedWrappers[target] = source
        forceHiddenUiView(target)
    }

    private fun findHiddenUiCollapseTarget(source: View): View {
        var best = source
        var current = source
        var depth = 0
        while (depth < 3) {
            val parent = current.parent as? ViewGroup ?: break
            if (resourceEntryName(parent) != null || !isHiddenUiLayoutShell(parent, current)) break
            best = parent
            current = parent
            depth++
        }
        return best
    }

    private fun isHiddenUiLayoutShell(parent: ViewGroup, child: View): Boolean {
        var usefulChildren = 0
        for (index in 0 until parent.childCount) {
            val candidate = parent.getChildAt(index) ?: continue
            if (candidate === child) {
                usefulChildren++
                continue
            }
            if (candidate.visibility == View.GONE || candidate.alpha <= 0.01f) continue
            if (candidate.width <= 0 || candidate.height <= 0) continue
            usefulChildren++
        }
        return usefulChildren <= 1 || hasSameHiddenUiBounds(parent, child)
    }

    private fun hasSameHiddenUiBounds(parent: View, child: View): Boolean {
        if (parent.width <= 0 || parent.height <= 0 || child.width <= 0 || child.height <= 0) return false
        if (kotlin.math.abs(parent.width - child.width) > 2 || kotlin.math.abs(parent.height - child.height) > 2) return false
        val parentLocation = IntArray(2)
        val childLocation = IntArray(2)
        parent.getLocationOnScreen(parentLocation)
        child.getLocationOnScreen(childLocation)
        return kotlin.math.abs(parentLocation[0] - childLocation[0]) <= 2 &&
            kotlin.math.abs(parentLocation[1] - childLocation[1]) <= 2
    }

    private fun collapseHiddenUiFootprint(view: View) {
        view.layoutParams?.let { params ->
            var changed = false
            if (params.width != 0) {
                params.width = 0
                changed = true
            }
            if (params.height != 0) {
                params.height = 0
                changed = true
            }
            if (params is ViewGroup.MarginLayoutParams &&
                (params.leftMargin != 0 || params.topMargin != 0 || params.rightMargin != 0 || params.bottomMargin != 0)
            ) {
                params.setMargins(0, 0, 0, 0)
                changed = true
            }
            if (changed) view.layoutParams = params
        }
        view.minimumWidth = 0
        view.minimumHeight = 0
        forceHiddenUiMeasuredSize(view)
        requestHiddenUiLayout(view)
    }

    private fun restoreHiddenUiFootprint(view: View, original: HiddenUiState) {
        view.layoutParams?.let { params ->
            var changed = false
            if (original.hasLayoutParams && params.width != original.layoutWidth) {
                params.width = original.layoutWidth
                changed = true
            }
            if (original.hasLayoutParams && params.height != original.layoutHeight) {
                params.height = original.layoutHeight
                changed = true
            }
            if (params is ViewGroup.MarginLayoutParams && original.hasMargins &&
                (params.leftMargin != original.leftMargin || params.topMargin != original.topMargin ||
                    params.rightMargin != original.rightMargin || params.bottomMargin != original.bottomMargin)
            ) {
                params.setMargins(original.leftMargin, original.topMargin, original.rightMargin, original.bottomMargin)
                changed = true
            }
            if (changed) view.layoutParams = params
        }
        view.minimumWidth = original.minWidth
        view.minimumHeight = original.minHeight
        requestHiddenUiLayout(view)
    }

    private fun forceHiddenUiMeasuredSize(view: View) {
        runCatching {
            hiddenUiInternalChange.set(true)
            View::class.java.getDeclaredMethod(
                "setMeasuredDimension",
                java.lang.Integer.TYPE,
                java.lang.Integer.TYPE
            ).apply { isAccessible = true }.invoke(view, 0, 0)
        }
        hiddenUiInternalChange.remove()
    }

    private fun requestHiddenUiLayout(view: View) {
        view.requestLayout()
        view.invalidate()
        (view.parent as? View)?.let {
            it.requestLayout()
            it.invalidate()
        }
    }

    private fun collapseGenericActionSlot(source: View) {
        if (isComposerOrTextInputUiContext(source)) return
        var current = source
        var depth = 0
        while (depth < 3) {
            val parent = current.parent as? ViewGroup ?: break
            if (!isGenericActionUiSlot(parent, current)) break
            hiddenUiCollapsedWrappers[parent] = source
            forceHiddenUiView(parent)
            current = parent
            depth++
        }
    }

    private fun collapseCompoundActionItem(source: View) {
        if (isComposerOrTextInputUiContext(source)) return
        var current = source
        var depth = 0
        while (depth < 3) {
            val parent = current.parent as? ViewGroup ?: break
            if (isCompoundActionUiItem(parent, current)) {
                hiddenUiCollapsedWrappers[parent] = source
                forceHiddenUiView(parent)
                current = parent
                depth++
                continue
            }
            if (!isHiddenUiLayoutShell(parent, current) && !hasSameHiddenUiBounds(parent, current)) break
            current = parent
            depth++
        }
    }

    private fun isGenericActionUiSlot(parent: ViewGroup, child: View): Boolean {
        if (isComposerOrTextInputUiContext(parent) || !isHiddenUiLayoutShell(parent, child)) return false
        val width = maxOf(parent.width, parent.measuredWidth)
        val height = maxOf(parent.height, parent.measuredHeight)
        if (width <= 0 || height <= 0 || width > dp(parent, 240) || height > dp(parent, 180)) return false
        return hasActionUiName(parent) || hasActionUiName(child) || isIconLikeUiView(child)
    }

    private fun isCompoundActionUiItem(parent: ViewGroup, child: View): Boolean {
        if (isComposerOrTextInputUiContext(parent)) return false
        val width = maxOf(parent.width, parent.measuredWidth)
        val height = maxOf(parent.height, parent.measuredHeight)
        if (width <= 0 || height <= 0 || width > dp(parent, 220) || height > dp(parent, 240)) return false
        val parentName = resourceEntryName(parent).orEmpty().lowercase(Locale.US)
        if (!parentName.contains("item") && !parentName.contains("cell") &&
            (parentName.contains("buttons") || parentName.contains("toolbar") || parentName.contains("navigation") || parentName.contains("tab_bar"))
        ) return false
        var usefulChildren = 0
        var labels = 0
        var iconControls = 0
        for (index in 0 until parent.childCount) {
            val candidate = parent.getChildAt(index) ?: continue
            if (candidate !== child && candidate.visibility == View.GONE && !shouldEnforceHiddenUiView(candidate)) continue
            if (candidate !== child && candidate.alpha <= 0.01f) continue
            usefulChildren++
            if (isIconLikeUiView(candidate) || hasActionUiName(candidate)) iconControls++
            if (isActionTextUiLabel(candidate)) labels++
        }
        return usefulChildren in 2..5 && labels > 0 && (iconControls > 0 || hasActionUiName(parent))
    }

    private fun isIconLikeUiView(view: View): Boolean {
        var width = maxOf(view.width, view.measuredWidth)
        var height = maxOf(view.height, view.measuredHeight)
        hiddenUiOriginalStates[view]?.let {
            if (width <= 0) width = maxOf(width, it.width)
            if (height <= 0) height = maxOf(height, it.height)
        }
        if (width <= 0 || height <= 0 || width > dp(view, 132) || height > dp(view, 132)) return false
        val className = view.javaClass.name.lowercase(Locale.US)
        return className.contains("image") || className.contains("button") ||
            className.contains("textview") || view.isClickable || view.isLongClickable
    }

    private fun isActionTextUiLabel(view: View): Boolean {
        val name = resourceEntryName(view).orEmpty().lowercase(Locale.US)
        return name.contains("count") || name.contains("label") || name.contains("text") ||
            name.contains("title") || name.contains("subtitle") || name.contains("number") ||
            view.javaClass.name.lowercase(Locale.US).contains("textview")
    }

    private fun hasActionUiName(view: View): Boolean {
        val name = "${resourceEntryName(view).orEmpty()} ${view.javaClass.name}".lowercase(Locale.US)
        return name.contains("button") || name.contains("action") || name.contains("icon") ||
            name.contains("ufi") || name.contains("menu") || name.contains("share") ||
            name.contains("save") || name.contains("like") || name.contains("comment")
    }

    private fun isComposerOrTextInputUiContext(view: View): Boolean {
        var current: View? = view
        repeat(8) {
            val node = current ?: return false
            val name = "${resourceEntryName(node).orEmpty()} ${node.javaClass.name}".lowercase(Locale.US)
            if (name.contains("edittext") || name.contains("textarea") || name.contains("text_area") || name.contains("composer")) return true
            current = node.parent as? View
        }
        return false
    }

    private fun collapseComposerActionParent(source: View) {
        val action = findComposerUiActionView(source) ?: return
        if (action === source) return
        hiddenUiCollapsedWrappers[action] = source
        forceHiddenUiView(action)
    }

    private fun collapseComposerActionSlot(source: View) {
        var current = findComposerUiActionView(source) ?: return
        var depth = 0
        while (depth < 3) {
            val parent = current.parent as? ViewGroup ?: break
            if (!isComposerUiActionSlot(parent, current)) break
            hiddenUiCollapsedWrappers[parent] = source
            forceHiddenUiView(parent)
            current = parent
            depth++
        }
    }

    private fun isComposerUiActionSlot(parent: ViewGroup, child: View): Boolean {
        val name = resourceEntryName(parent).orEmpty().lowercase(Locale.US)
        if (!name.contains("composer")) return false
        if (name.contains("textarea") || name.contains("text_area") || name.contains("edittext") ||
            name.contains("edit_text") || name.contains("buttons_container")
        ) return false
        return name.contains("shortcut_viewgroup") ||
            (name.contains("button") && (name.contains("container") || name.contains("stub")) && isHiddenUiLayoutShell(parent, child)) ||
            (name.contains("viewgroup") && isHiddenUiLayoutShell(parent, child))
    }

    private fun findComposerUiActionView(source: View): View? {
        var current: View? = source
        repeat(8) {
            val node = current ?: return null
            val name = resourceEntryName(node).orEmpty().lowercase(Locale.US)
            if (isComposerUiActionName(name)) return node
            if (isComposerUiContainerName(name)) return null
            current = node.parent as? View
        }
        return null
    }

    private fun isComposerUiActionName(name: String): Boolean {
        if (!name.contains("composer")) return false
        if (name.contains("container") || name.contains("edittext") || name.contains("edit_text") ||
            name.contains("textarea") || name.contains("text_area")
        ) return false
        return name.contains("button") || name.contains("icon") || name.contains("camera") ||
            name.contains("gallery") || name.contains("voice") || name.contains("sticker") ||
            name.contains("emoji") || name.contains("overflow")
    }

    private fun isComposerUiContainerName(name: String): Boolean {
        if (!name.contains("composer")) return false
        if (name.contains("button") || name.contains("icon") || name.contains("shortcut_viewgroup")) return false
        if ((name.contains("edittext") || name.contains("edit_text")) && !name.contains("container")) return false
        return name.contains("container") || name.contains("bar") || name.contains("controls") ||
            name.contains("textarea") || name.contains("text_area")
    }

    private fun adjustComposerUiForHiddenAction(source: View) {
        val action = findComposerUiActionView(source) ?: return
        val actionState = hiddenUiOriginalStates[action] ?: hiddenUiOriginalStates[source] ?: return
        if (actionState.width <= 0 || actionState.height <= 0) return
        val root = findComposerUiRoot(action) ?: return
        val targets = mutableListOf<View>()
        collectComposerUiTextTargets(root, action, targets)
        targets.filterNot { target -> targets.any { other -> other !== target && isHiddenUiDescendant(target, other) } }
            .forEach { target ->
                val shift = calculateComposerUiShift(actionState, target)
                if (shift > 0) adjustComposerUiTarget(target, shift)
            }
    }

    private fun enforceComposerUiAdjustment(target: View?) {
        if (target == null || !isComposerUiTextTarget(target)) return
        val root = findComposerUiRoot(target)
        if (root == null) {
            restoreComposerUiAdjustment(target)
            return
        }
        val hiddenActions = mutableListOf<View>()
        collectHiddenComposerUiActions(root, hiddenActions)
        var bestShift = 0
        hiddenActions.forEach { source ->
            val action = findComposerUiActionView(source) ?: return@forEach
            val sourceState = hiddenUiOriginalStates[action] ?: hiddenUiOriginalStates[source] ?: return@forEach
            bestShift = maxOf(bestShift, calculateComposerUiShift(sourceState, target))
        }
        if (bestShift > 0) adjustComposerUiTarget(target, bestShift) else restoreComposerUiAdjustment(target)
    }

    private fun collectHiddenComposerUiActions(view: View, out: MutableList<View>) {
        if (matchesExplicitHiddenUiRule(view) && findComposerUiActionView(view) != null) out += view
        if (view !is ViewGroup) return
        for (index in 0 until view.childCount) collectHiddenComposerUiActions(view.getChildAt(index), out)
    }

    private fun findComposerUiRoot(source: View): ViewGroup? {
        var current: View? = source
        var fallback = source.parent as? ViewGroup
        repeat(12) {
            val node = current ?: return fallback
            val group = node as? ViewGroup
            if (group != null && isComposerUiContainerName(resourceEntryName(group).orEmpty().lowercase(Locale.US))) {
                if (containsComposerUiTextTarget(group, source)) return group
                fallback = fallback ?: group
            }
            current = node.parent as? View
        }
        return fallback
    }

    private fun containsComposerUiTextTarget(root: ViewGroup, source: View): Boolean {
        if (root !== source && !isHiddenUiDescendant(root, source) && isComposerUiTextTarget(root)) return true
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (child === source || isHiddenUiDescendant(child, source)) continue
            if (isComposerUiTextTarget(child)) return true
            if (child is ViewGroup && containsComposerUiTextTarget(child, source)) return true
        }
        return false
    }

    private fun collectComposerUiTextTargets(view: View, source: View, out: MutableList<View>) {
        if (view === source || isHiddenUiDescendant(view, source)) return
        if (isComposerUiTextTarget(view)) out += view
        if (view !is ViewGroup) return
        for (index in 0 until view.childCount) collectComposerUiTextTargets(view.getChildAt(index), source, out)
    }

    private fun isComposerUiTextTarget(view: View): Boolean {
        if (view.visibility == View.GONE) return false
        val name = resourceEntryName(view).orEmpty().lowercase(Locale.US)
        if (name.contains("composer") && !name.contains("button") && !name.contains("icon") && !name.contains("send") &&
            (name.contains("edittext") || name.contains("edit_text") || name.contains("textarea") ||
                name.contains("text_area") || name.endsWith("_text"))
        ) return true
        return view.javaClass.name.lowercase(Locale.US).contains("edittext")
    }

    private fun calculateComposerUiShift(source: HiddenUiState, target: View): Int {
        val location = IntArray(2)
        target.getLocationOnScreen(location)
        val top = location[1]
        val bottom = top + maxOf(target.height, target.measuredHeight)
        if (bottom <= source.screenTop || top >= source.screenBottom) return 0
        if (location[0] <= source.screenLeft + 2) return 0
        val rawShift = location[0] - source.screenLeft
        val sourceFootprint = source.width + source.leftMargin + source.rightMargin
        return minOf(rawShift, maxOf(sourceFootprint, (72f * source.density.coerceAtLeast(1f)).roundToInt()))
    }

    private fun adjustComposerUiTarget(target: View, shift: Int) {
        if (shift <= 0) return
        val original = hiddenUiComposerAdjustments.getOrPut(target) { ComposerUiAdjustment(target) }
        runCatching {
            hiddenUiInternalChange.set(true)
            var remaining = shift
            val params = target.layoutParams
            if (params is ViewGroup.MarginLayoutParams && original.hasMargins) {
                val leftMargin = maxOf(0, original.leftMargin - remaining)
                remaining -= maxOf(0, original.leftMargin - leftMargin)
                params.setMargins(leftMargin, original.topMargin, original.rightMargin, original.bottomMargin)
                target.layoutParams = params
            }
            val leftPadding = maxOf(0, original.paddingLeft - remaining)
            remaining -= maxOf(0, original.paddingLeft - leftPadding)
            target.setPadding(leftPadding, original.paddingTop, original.paddingRight, original.paddingBottom)
            target.translationX = original.translationX - maxOf(0, remaining)
            requestHiddenUiLayout(target)
        }.onFailure { logError("Failed to adjust Instagram composer after hidden action", it) }
        hiddenUiInternalChange.remove()
    }

    private fun restoreComposerUiAdjustment(target: View) {
        val original = hiddenUiComposerAdjustments.remove(target) ?: return
        runCatching {
            hiddenUiInternalChange.set(true)
            val params = target.layoutParams
            if (params is ViewGroup.MarginLayoutParams && original.hasMargins) {
                params.setMargins(original.leftMargin, original.topMargin, original.rightMargin, original.bottomMargin)
                target.layoutParams = params
            }
            target.setPadding(original.paddingLeft, original.paddingTop, original.paddingRight, original.paddingBottom)
            target.translationX = original.translationX
            requestHiddenUiLayout(target)
        }.onFailure { logError("Failed to restore Instagram composer hidden-action adjustment", it) }
        hiddenUiInternalChange.remove()
    }

    private fun collectHiddenUiRowCompactions(view: View, activeTargets: MutableSet<View>) {
        if (view is ViewGroup) {
            compactHiddenUiRowGroup(view, activeTargets)
            for (index in 0 until view.childCount) collectHiddenUiRowCompactions(view.getChildAt(index), activeTargets)
        }
    }

    private fun compactRowsAroundHiddenUiView(source: View?, activeTargets: MutableSet<View>?) {
        var current = source ?: return
        var depth = 0
        while (depth < 8) {
            val parent = current.parent as? ViewGroup ?: break
            compactHiddenUiRowGroup(parent, activeTargets)
            current = parent
            depth++
        }
    }

    private fun compactHiddenUiRowGroup(group: ViewGroup, activeTargets: MutableSet<View>?) {
        if (!isCompactableHiddenUiRowGroup(group)) return
        val elements = mutableListOf<HiddenUiRowElement>()
        var hasHidden = false
        for (index in 0 until group.childCount) {
            val element = hiddenUiRowElementFrom(group.getChildAt(index), group) ?: continue
            elements += element
            hasHidden = hasHidden || element.hidden
        }
        if (!hasHidden || elements.size < 2) return
        val broadGroup = isBroadHiddenUiCompactionGroup(group)
        buildHiddenUiRows(elements).forEach { applyHiddenUiRowCompaction(group, it, activeTargets, broadGroup) }
        buildHiddenUiColumns(elements).forEach { applyHiddenUiColumnCompaction(group, it, activeTargets, broadGroup) }
    }

    private fun hiddenUiRowElementFrom(view: View?, group: ViewGroup): HiddenUiRowElement? {
        if (view == null) return null
        val hidden = shouldEnforceHiddenUiView(view)
        if (hidden) {
            val original = hiddenUiOriginalStates[view] ?: return null
            if (original.width <= 0 || original.height <= 0) return null
            if (!isHiddenUiRowCompactionCandidate(view, original.width, original.height, group, true)) return null
            return HiddenUiRowElement(view, true, original.screenLeft, original.screenTop, original.width, original.height)
        }

        if (view.visibility != View.VISIBLE || view.alpha <= 0.01f) return null
        hiddenUiRowAdjustments[view]?.let { state ->
            if (state.width > 0 && state.height > 0) {
                return HiddenUiRowElement(view, false, state.screenLeft, state.screenTop, state.width, state.height)
            }
        }
        val width = maxOf(view.width, view.measuredWidth)
        val height = maxOf(view.height, view.measuredHeight)
        if (width <= 0 || height <= 0) return null
        if (!isHiddenUiRowCompactionCandidate(view, width, height, group, false)) return null
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return HiddenUiRowElement(view, false, location[0], location[1], width, height)
    }

    private fun isCompactableHiddenUiRowGroup(group: ViewGroup): Boolean {
        val childCount = group.childCount
        if (childCount < 2 || childCount > 36) return false
        if (isComposerOrTextInputUiContext(group)) return false
        val width = group.width
        val height = group.height
        if (width <= 0 || height <= 0) return false
        val className = group.javaClass.name
        return !className.contains("RecyclerView") &&
            !className.contains("ScrollView") &&
            !className.contains("ViewPager") &&
            !className.contains("NestedScroll") &&
            !className.contains("SwipeRefresh")
    }

    private fun isBroadHiddenUiCompactionGroup(group: ViewGroup): Boolean {
        val width = maxOf(group.width, group.measuredWidth)
        val height = maxOf(group.height, group.measuredHeight)
        return height > dp(group, 160) && width > dp(group, 220)
    }

    private fun buildHiddenUiRows(elements: List<HiddenUiRowElement>): List<List<HiddenUiRowElement>> {
        val sorted = elements.sortedWith { left, right ->
            var topCompare = left.centerY().compareTo(right.centerY())
            if (kotlin.math.abs(left.centerY() - right.centerY()) <= maxOf(8, minOf(left.height, right.height))) topCompare = 0
            if (topCompare != 0) topCompare else left.left.compareTo(right.left)
        }
        val rows = mutableListOf<MutableList<HiddenUiRowElement>>()
        sorted.forEach { element ->
            val row = rows.firstOrNull { it.isNotEmpty() && isSameHiddenUiRow(it[0], element) }
                ?: mutableListOf<HiddenUiRowElement>().also { rows += it }
            row += element
        }
        return rows
    }

    private fun isSameHiddenUiRow(a: HiddenUiRowElement, b: HiddenUiRowElement): Boolean {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (overlap > minOf(a.height, b.height) / 3) return true
        return kotlin.math.abs(a.centerY() - b.centerY()) <= maxOf(24, minOf(a.height, b.height))
    }

    private fun buildHiddenUiColumns(elements: List<HiddenUiRowElement>): List<List<HiddenUiRowElement>> {
        val sorted = elements.sortedWith { left, right ->
            var leftCompare = left.centerX().compareTo(right.centerX())
            if (kotlin.math.abs(left.centerX() - right.centerX()) <= maxOf(8, minOf(left.width, right.width))) leftCompare = 0
            if (leftCompare != 0) leftCompare else left.top.compareTo(right.top)
        }
        val columns = mutableListOf<MutableList<HiddenUiRowElement>>()
        sorted.forEach { element ->
            val column = columns.firstOrNull { it.isNotEmpty() && isSameHiddenUiColumn(it[0], element) }
                ?: mutableListOf<HiddenUiRowElement>().also { columns += it }
            column += element
        }
        return columns
    }

    private fun isSameHiddenUiColumn(a: HiddenUiRowElement, b: HiddenUiRowElement): Boolean {
        val overlap = minOf(a.right, b.right) - maxOf(a.left, b.left)
        if (overlap > minOf(a.width, b.width) / 3) return true
        return kotlin.math.abs(a.centerX() - b.centerX()) <= maxOf(24, minOf(a.width, b.width))
    }

    private fun applyHiddenUiRowCompaction(group: ViewGroup, row: List<HiddenUiRowElement>, activeTargets: MutableSet<View>?, broadGroup: Boolean) {
        if (row.size < 2) return
        buildHiddenUiRowClusters(group, row.sortedBy { it.left }).forEach { cluster ->
            applyHiddenUiRowClusterCompaction(group, cluster, activeTargets, broadGroup)
        }
    }

    private fun buildHiddenUiRowClusters(group: ViewGroup, row: List<HiddenUiRowElement>): List<List<HiddenUiRowElement>> {
        val clusters = mutableListOf<MutableList<HiddenUiRowElement>>()
        val gapThreshold = maxOf(dp(group, 72), hiddenUiMedianPositiveGap(row) * 3)
        var current: MutableList<HiddenUiRowElement>? = null
        var previous: HiddenUiRowElement? = null
        row.forEach { element ->
            val gap = previous?.let { element.left - it.right } ?: 0
            if (current == null || gap > gapThreshold) {
                current = mutableListOf()
                clusters += current!!
            }
            current!! += element
            previous = element
        }
        return clusters
    }

    private fun hiddenUiMedianPositiveGap(row: List<HiddenUiRowElement>): Int {
        val gaps = row.zipWithNext().map { (left, right) -> right.left - left.right }.filter { it > 0 }.sorted()
        return gaps.getOrNull(gaps.size / 2) ?: 0
    }

    private fun applyHiddenUiRowClusterCompaction(group: ViewGroup, row: List<HiddenUiRowElement>, activeTargets: MutableSet<View>?, broadGroup: Boolean) {
        if (row.size < 2 || row.none { it.hidden } || row.none { !it.hidden }) return
        if (isRedistributableHiddenUiSlotCluster(group, row, horizontal = true, broadGroup = broadGroup)) {
            applyDistributedHiddenUiSlotCompaction(group, row, horizontal = true, activeTargets = activeTargets)
            return
        }
        if (broadGroup && !isHiddenUiActionCluster(group, row, horizontal = true, broadGroup = true)) return
        var cumulativeShift = 0
        var hiddenRunStart: Int? = null
        row.forEach { element ->
            if (element.hidden) {
                if (hiddenRunStart == null) hiddenRunStart = element.left
                return@forEach
            }
            hiddenRunStart?.let { start ->
                val removedWidth = element.left - start
                if (removedWidth > 0) cumulativeShift += minOf(removedWidth, dp(group, 180))
                hiddenRunStart = null
            }
            if (cumulativeShift > 0) adjustHiddenUiRowTarget(group, element.view, cumulativeShift, activeTargets)
        }
    }

    private fun applyHiddenUiColumnCompaction(group: ViewGroup, column: List<HiddenUiRowElement>, activeTargets: MutableSet<View>?, broadGroup: Boolean) {
        if (column.size < 2) return
        buildHiddenUiColumnClusters(group, column.sortedBy { it.top }).forEach { cluster ->
            applyHiddenUiColumnClusterCompaction(group, cluster, activeTargets, broadGroup)
        }
    }

    private fun buildHiddenUiColumnClusters(group: ViewGroup, column: List<HiddenUiRowElement>): List<List<HiddenUiRowElement>> {
        val clusters = mutableListOf<MutableList<HiddenUiRowElement>>()
        val gapThreshold = maxOf(dp(group, 64), hiddenUiMedianPositiveVerticalGap(column) * 3)
        var current: MutableList<HiddenUiRowElement>? = null
        var previous: HiddenUiRowElement? = null
        column.forEach { element ->
            val gap = previous?.let { element.top - it.bottom } ?: 0
            if (current == null || gap > gapThreshold) {
                current = mutableListOf()
                clusters += current!!
            }
            current!! += element
            previous = element
        }
        return clusters
    }

    private fun hiddenUiMedianPositiveVerticalGap(column: List<HiddenUiRowElement>): Int {
        val gaps = column.zipWithNext().map { (top, bottom) -> bottom.top - top.bottom }.filter { it > 0 }.sorted()
        return gaps.getOrNull(gaps.size / 2) ?: 0
    }

    private fun applyHiddenUiColumnClusterCompaction(group: ViewGroup, column: List<HiddenUiRowElement>, activeTargets: MutableSet<View>?, broadGroup: Boolean) {
        if (column.size < 2 || column.none { it.hidden } || column.none { !it.hidden }) return
        if (isRedistributableHiddenUiSlotCluster(group, column, horizontal = false, broadGroup = broadGroup)) {
            applyDistributedHiddenUiSlotCompaction(group, column, horizontal = false, activeTargets = activeTargets)
            return
        }
        if (broadGroup && !isHiddenUiActionCluster(group, column, horizontal = false, broadGroup = true)) return
        var cumulativeShift = 0
        var hiddenRunStart: Int? = null
        column.forEach { element ->
            if (element.hidden) {
                if (hiddenRunStart == null) hiddenRunStart = element.top
                return@forEach
            }
            hiddenRunStart?.let { start ->
                val removedHeight = element.top - start
                if (removedHeight > 0) cumulativeShift += minOf(removedHeight, dp(group, 180))
                hiddenRunStart = null
            }
            if (cumulativeShift > 0) adjustHiddenUiColumnTarget(group, element.view, cumulativeShift, activeTargets)
        }
    }

    private fun isRedistributableHiddenUiSlotCluster(group: ViewGroup, cluster: List<HiddenUiRowElement>, horizontal: Boolean, broadGroup: Boolean): Boolean {
        if (cluster.size !in 3..12) return false
        val hiddenCount = cluster.count { it.hidden }
        val visibleCount = cluster.size - hiddenCount
        if (hiddenCount == 0 || visibleCount < 2) return false
        if (!isHiddenUiActionCluster(group, cluster, horizontal, broadGroup)) return false
        val primarySpan = if (horizontal) clusterRight(cluster) - clusterLeft(cluster) else clusterBottom(cluster) - clusterTop(cluster)
        val crossSpan = if (horizontal) clusterBottom(cluster) - clusterTop(cluster) else clusterRight(cluster) - clusterLeft(cluster)
        return if (horizontal) primarySpan >= dp(group, 96) && crossSpan <= dp(group, 150)
        else primarySpan >= dp(group, 120) && crossSpan <= dp(group, 170)
    }

    private fun isHiddenUiActionCluster(group: ViewGroup, cluster: List<HiddenUiRowElement>, horizontal: Boolean, broadGroup: Boolean): Boolean {
        var actionLike = if (hasHiddenUiSlotContainerName(group)) 1 else 0
        var iconLike = 0
        var textLike = 0
        cluster.forEach { element ->
            if (hasHiddenUiActionLikeName(element.view)) actionLike++
            if (isIconLikeUiView(element.view)) iconLike++
            if (isActionTextUiLabel(element.view)) textLike++
        }
        if (actionLike >= 2 || iconLike >= 2) return true
        if (!broadGroup && actionLike + iconLike + textLike >= 2) return true
        return if (horizontal) isHiddenUiHorizontalEdgeSlotCluster(group, cluster) && actionLike + iconLike >= 1
        else isHiddenUiVerticalEdgeSlotCluster(group, cluster) && actionLike + iconLike >= 1
    }

    private fun isHiddenUiHorizontalEdgeSlotCluster(group: ViewGroup, cluster: List<HiddenUiRowElement>): Boolean {
        val location = IntArray(2)
        group.getLocationOnScreen(location)
        val groupTop = location[1]
        val groupBottom = groupTop + maxOf(group.height, group.measuredHeight)
        val clusterHeight = clusterBottom(cluster) - clusterTop(cluster)
        if (clusterHeight > dp(group, 150)) return false
        return kotlin.math.abs(clusterTop(cluster) - groupTop) <= dp(group, 96) ||
            kotlin.math.abs(groupBottom - clusterBottom(cluster)) <= dp(group, 160)
    }

    private fun isHiddenUiVerticalEdgeSlotCluster(group: ViewGroup, cluster: List<HiddenUiRowElement>): Boolean {
        val location = IntArray(2)
        group.getLocationOnScreen(location)
        val groupLeft = location[0]
        val groupRight = groupLeft + maxOf(group.width, group.measuredWidth)
        val clusterWidth = clusterRight(cluster) - clusterLeft(cluster)
        if (clusterWidth > dp(group, 170)) return false
        return kotlin.math.abs(clusterLeft(cluster) - groupLeft) <= dp(group, 96) ||
            kotlin.math.abs(groupRight - clusterRight(cluster)) <= dp(group, 120)
    }

    private fun applyDistributedHiddenUiSlotCompaction(group: ViewGroup, cluster: List<HiddenUiRowElement>, horizontal: Boolean, activeTargets: MutableSet<View>?) {
        val visible = cluster.filterNot { it.hidden }.sortedBy { if (horizontal) it.centerX() else it.centerY() }
        if (visible.size < 2) return
        val startCenter = if (horizontal) cluster.minOf { it.centerX() } else cluster.minOf { it.centerY() }
        val endCenter = if (horizontal) cluster.maxOf { it.centerX() } else cluster.maxOf { it.centerY() }
        val span = endCenter - startCenter
        if (span <= 0) return
        val step = span / (visible.size - 1).toFloat()
        val maxOffset = maxOf(dp(group, 260), span / 2)
        visible.forEachIndexed { index, element ->
            val targetCenter = (startCenter + step * index).roundToInt()
            val offset = targetCenter - if (horizontal) element.centerX() else element.centerY()
            if (kotlin.math.abs(offset) <= 2 || kotlin.math.abs(offset) > maxOffset) return@forEachIndexed
            if (horizontal) {
                moveHiddenUiRowTargetBy(element.view, offset, 0, updateX = true, updateY = false, activeTargets = activeTargets)
            } else {
                moveHiddenUiRowTargetBy(element.view, 0, offset, updateX = false, updateY = true, activeTargets = activeTargets)
            }
        }
    }

    private fun clusterLeft(elements: List<HiddenUiRowElement>) = elements.minOfOrNull { it.left } ?: 0
    private fun clusterTop(elements: List<HiddenUiRowElement>) = elements.minOfOrNull { it.top } ?: 0
    private fun clusterRight(elements: List<HiddenUiRowElement>) = elements.maxOfOrNull { it.right } ?: 0
    private fun clusterBottom(elements: List<HiddenUiRowElement>) = elements.maxOfOrNull { it.bottom } ?: 0

    private fun adjustHiddenUiRowTarget(group: ViewGroup, target: View, shift: Int, activeTargets: MutableSet<View>?) {
        if (shift > 0) moveHiddenUiRowTargetBy(target, -shift, 0, updateX = true, updateY = false, activeTargets = activeTargets)
    }

    private fun adjustHiddenUiColumnTarget(group: ViewGroup, target: View, shift: Int, activeTargets: MutableSet<View>?) {
        if (shift > 0) moveHiddenUiRowTargetBy(target, 0, -shift, updateX = false, updateY = true, activeTargets = activeTargets)
    }

    private fun moveHiddenUiRowTargetBy(target: View, offsetX: Int, offsetY: Int, updateX: Boolean, updateY: Boolean, activeTargets: MutableSet<View>?) {
        activeTargets?.add(target)
        val original = hiddenUiRowAdjustments.getOrPut(target) { HiddenUiRowAdjustment(target) }
        if (updateX) original.appliedShiftX = -offsetX
        if (updateY) original.appliedShiftY = -offsetY
        runCatching {
            hiddenUiInternalChange.set(true)
            target.translationX = original.translationX - original.appliedShiftX
            target.translationY = original.translationY - original.appliedShiftY
            requestHiddenUiLayout(target)
        }.onFailure { logError("Failed to compact Instagram hidden UI row", it) }
        hiddenUiInternalChange.remove()
    }

    private fun enforceHiddenUiRowAdjustment(target: View?) {
        val original = target?.let { hiddenUiRowAdjustments[it] } ?: return
        if (original.appliedShiftX == 0 && original.appliedShiftY == 0) return
        runCatching {
            hiddenUiInternalChange.set(true)
            target.translationX = original.translationX - original.appliedShiftX
            target.translationY = original.translationY - original.appliedShiftY
        }
        hiddenUiInternalChange.remove()
    }

    private fun restoreInactiveHiddenUiRowAdjustments(root: View, activeTargets: Set<View>) {
        val entries = synchronized(hiddenUiRowAdjustments) { hiddenUiRowAdjustments.entries.toList() }
        entries.forEach { (target, original) ->
            if (target == null || (target !== root && !isHiddenUiDescendant(target, root))) return@forEach
            if (target in activeTargets) return@forEach
            restoreHiddenUiRowAdjustment(target, original)
            hiddenUiRowAdjustments.remove(target)
        }
    }

    private fun restoreHiddenUiRowAdjustment(target: View, original: HiddenUiRowAdjustment) {
        runCatching {
            hiddenUiInternalChange.set(true)
            target.translationX = original.translationX
            target.translationY = original.translationY
            requestHiddenUiLayout(target)
        }.onFailure { logError("Failed to restore Instagram hidden UI row compaction", it) }
        hiddenUiInternalChange.remove()
    }

    private fun isHiddenUiRowCompactionCandidate(view: View, width: Int, height: Int, group: ViewGroup, hidden: Boolean): Boolean {
        if (width <= 0 || height <= 0 || isComposerOrTextInputUiContext(view)) return false
        val groupWidth = maxOf(group.width, group.measuredWidth)
        val maxWidth = maxOf(dp(group, 220), (groupWidth * 0.42f).roundToInt())
        if (width > maxWidth) return false
        if (height > maxOf(dp(group, 96), (maxOf(group.height, group.measuredHeight) * 0.9f).roundToInt())) return false
        val className = view.javaClass.name.lowercase(Locale.US)
        if (className.contains("recyclerview") || className.contains("scrollview") ||
            className.contains("viewpager") || className.contains("nestedscroll") || className.contains("edittext")
        ) return false
        if (className.contains("textview") && width > dp(group, 180)) return false
        if (view is ViewGroup) {
            if (view.childCount > 6) return false
            if (!hidden && width > dp(group, 180) && !hasHiddenUiActionLikeName(view)) return false
        }
        return true
    }

    private fun hasHiddenUiActionLikeName(view: View): Boolean {
        val lower = "${resourceEntryName(view).orEmpty()} ${view.javaClass.name}".lowercase(Locale.US)
        return lower.contains("action") || lower.contains("button") || lower.contains("icon") ||
            lower.contains("like") || lower.contains("comment") || lower.contains("share") ||
            lower.contains("send") || lower.contains("bookmark") || lower.contains("save") ||
            lower.contains("search") || lower.contains("home") || lower.contains("profile") ||
            lower.contains("avatar") || lower.contains("direct") || lower.contains("message") ||
            lower.contains("camera") || lower.contains("gallery") || lower.contains("voice") ||
            lower.contains("reply") || lower.contains("reaction") || lower.contains("follow") ||
            lower.contains("more") || lower.contains("overflow") || lower.contains("ufi")
    }

    private fun hasHiddenUiSlotContainerName(view: View): Boolean {
        val lower = resourceEntryName(view).orEmpty().lowercase(Locale.US)
        if (lower.contains("composer")) return false
        return lower.contains("actions") || lower.contains("action_bar") || lower.contains("button_bar") ||
            lower.contains("buttons") || lower.contains("navigation") || lower.contains("nav_bar") ||
            lower.contains("tab_bar") || lower.contains("tabs") || lower.contains("toolbar") ||
            lower.contains("header") || lower.contains("footer") || lower.contains("rail") ||
            lower.contains("tray") || lower.contains("controls") || lower.contains("ufi")
    }

    private fun isHiddenUiDescendant(view: View, ancestor: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return view !== ancestor
            current = current.parent as? View
        }
        return false
    }

    private fun shouldHideView(view: View): Boolean {
        if (!hasAnyViewHideRule()) return false
        val resourceId = resourceEntryName(view)
        if (matchesExplicitHiddenUiRule(view)) return true
        val content = view.contentDescription?.toString().orEmpty()
        val haystack = listOfNotNull(resourceId, content, view.javaClass.name)
            .joinToString(" ")
            .lowercase()
        return when {
            state.isExtremeMode && (haystack.contains("feed") || haystack.contains("reel") || haystack.contains("explore") || haystack.contains("story")) -> true
            state.disableStories && (haystack.contains("story") || haystack.contains("reel_tray")) -> true
            state.disableFeed && haystack.contains("feed") -> true
            state.disableReels && haystack.contains("reel") && !(state.disableReelsExceptDM && haystack.contains("direct")) -> true
            state.disableExplore && (haystack.contains("explore") || haystack.contains("search_tab")) -> true
            state.disableComments && haystack.contains("comment") -> true
            state.enableTeenAppIcons && haystack.contains("teen") -> true
            else -> false
        }
    }

    private fun shouldHideText(text: String): Boolean {
        val lower = text.lowercase()
        if (lower.isBlank()) return false
        return when {
            state.disableDiscoverPeople && lower.contains("discover people") -> true
            state.disableComments && (lower == "comments" || lower.contains("add a comment")) -> true
            state.disableReels && lower == "reels" -> true
            state.disableStories && lower == "stories" -> true
            else -> false
        }
    }

    private fun hideLivePresenceIfNeeded(view: View) {
        val name = resourceEntryName(view)
        if (!isLivePresenceName(name)) return

        var target: View = view
        var current: View? = view
        var depth = 0
        while (current != null && depth++ < 3) {
            val parent = current.parent as? View ?: break
            val parentName = resourceEntryName(parent)
            if (parentName != null && (
                    parentName.contains("recycler") ||
                        parentName.contains("viewer") ||
                        parentName.contains("broadcast")
                    )
            ) {
                break
            }
            target = parent
            current = parent
        }

        target.alpha = 0f
        target.visibility = View.GONE
    }

    private fun isLivePresenceName(name: String?): Boolean {
        return name != null && (
            name.startsWith("iglive_presence") ||
                name.contains("layout_iglive_presence") ||
                name.contains("live_presence_overlay")
            )
    }

    private fun hideView(view: View, reason: String) {
        if (view.visibility == View.GONE) return
        rememberHiddenUiState(view)
        view.visibility = View.GONE
        view.alpha = 0f
        view.isEnabled = false
        view.isClickable = false
        view.isLongClickable = false
        forceHiddenLayout(view)
        logInfo("Hidden Instagram UI element from $reason: ${resourceEntryName(view) ?: view.javaClass.simpleName}")
    }

    private fun forceHiddenLayout(view: View) {
        view.layoutParams?.let { params ->
            if (params.width == 0 && params.height == 0) return
            params.width = 0
            params.height = 0
            view.layoutParams = params
        }
    }

    private fun hideSponsoredSurfaceIfNeeded(view: View?) {
        if (view == null || sponsoredHiddenViews.contains(view)) return
        val name = resourceEntryName(view)
        if (isSponsoredResourceName(name) || isAdDisclosureResourceName(name)) hideSponsoredContainer(view)
    }

    private fun hideSponsoredContainer(anchor: View) {
        var target = findSponsoredHideTarget(anchor)
        var allowLargeSponsoredTarget = target != null &&
            target !== anchor &&
            runCatching { isSponsoredViewTree(target) }.getOrDefault(false)
        if (target == null || isUnsafeHideTarget(target, allowLargeSponsoredTarget)) {
            target = anchor
            allowLargeSponsoredTarget = false
        }
        if (isUnsafeHideTarget(target, allowLargeSponsoredTarget)) return
        sponsoredHiddenViews += target
        target.alpha = 0f
        target.visibility = View.GONE
    }

    private fun findSponsoredHideTarget(anchor: View): View? {
        val anchorText = (anchor as? TextView)?.text?.toString()
        val anchorIsDisclosure = isSponsoredText(anchorText) || isAdDisclosureText(anchorText)
        var target: View? = if (isUnsafeHideTarget(anchor)) null else anchor
        var current: View? = anchor
        var depth = 0
        while (depth++ < 12 && current != null) {
            val parentView = current.parent as? View ?: break
            val parentName = resourceEntryName(parentView)
            if (isHardListBoundary(parentView)) break
            if (isStrongSponsoredCardContainer(parentName)) {
                target = parentView
                break
            }
            if (anchorIsDisclosure &&
                !isUnsafeGlobalResourceName(parentName) &&
                isSponsoredViewTree(parentView)
            ) {
                target = parentView
                current = parentView
                continue
            }
            if (isUnsafeHideTarget(parentView)) break
            if (isLikelySponsoredCardContainer(parentName) && !isTooLargeToHide(parentView)) target = parentView
            if (depth <= 1 && isSponsoredResourceName(parentName) && !isTooLargeToHide(parentView)) target = parentView
            current = parentView
        }
        return target
    }

    private fun isSponsoredViewTree(root: View): Boolean {
        val scan = SponsoredViewScan()
        scanSponsoredViewTree(root, scan, 0)
        return scan.hasSponsoredText ||
            scan.hasSponsoredResource ||
            (scan.hasAdDisclosureText && (scan.hasAdDisclosureResource || scan.hasAdCtaText || scan.hasFeedContainer))
    }

    private fun scanSponsoredViewTree(view: View?, scan: SponsoredViewScan, depth: Int) {
        if (view == null || scan.visited > 220 || depth > 16) return
        scan.visited++
        val name = resourceEntryName(view)
        if (isSponsoredResourceName(name)) scan.hasSponsoredResource = true
        if (isAdDisclosureResourceName(name)) scan.hasAdDisclosureResource = true
        if (isLikelySponsoredCardContainer(name) || isStrongSponsoredCardContainer(name)) scan.hasFeedContainer = true
        if (view is TextView) {
            val text = view.text?.toString().orEmpty()
            if (isSponsoredText(text)) scan.hasSponsoredText = true
            if (isAdDisclosureText(text)) scan.hasAdDisclosureText = true
            if (isAdCtaText(text)) scan.hasAdCtaText = true
        }
        if (view !is ViewGroup) return
        for (i in 0 until view.childCount) {
            scanSponsoredViewTree(view.getChildAt(i), scan, depth + 1)
            if (scan.hasSponsoredText || scan.hasSponsoredResource ||
                (scan.hasAdDisclosureText && (scan.hasAdDisclosureResource || scan.hasAdCtaText || scan.hasFeedContainer))
            ) return
        }
    }

    private data class SponsoredViewScan(
        var visited: Int = 0,
        var hasSponsoredText: Boolean = false,
        var hasSponsoredResource: Boolean = false,
        var hasAdDisclosureText: Boolean = false,
        var hasAdDisclosureResource: Boolean = false,
        var hasAdCtaText: Boolean = false,
        var hasFeedContainer: Boolean = false
    )

    private fun isAdCtaText(text: String?): Boolean {
        return when (text?.trim()?.lowercase(Locale.US)) {
            "learn more", "shop now", "sign up", "install", "download", "book now",
            "apply now", "contact us", "send message", "chat on whatsapp", "watch more",
            "get offer", "order now", "subscribe", "follow", "message", "view profile",
            "visit profile", "visit instagram profile", "see more", "try now", "use app",
            "get quote", "view shop" -> true
            else -> false
        }
    }

    private fun isHardListBoundary(view: View): Boolean {
        val className = view.javaClass.name
        return className.contains("RecyclerView") ||
            className.contains("ViewPager") ||
            className.contains("ViewPager2") ||
            className.contains("NestedScrollView")
    }

    private fun isLikelySponsoredCardContainer(name: String?): Boolean {
        val value = name?.lowercase(Locale.US) ?: return false
        return value.startsWith("row_feed") ||
            value.contains("feed_item") ||
            value.contains("feed_timeline") ||
            value.startsWith("clips_item") ||
            value.startsWith("clips_viewer_item") ||
            value.startsWith("reel_item") ||
            value.contains("sponsored_card") ||
            value.contains("sponsored_item") ||
            value.contains("sponsored_unit")
    }

    private fun isStrongSponsoredCardContainer(name: String?): Boolean {
        val value = name?.lowercase(Locale.US) ?: return false
        return value == "in_feed_item_container" ||
            value == "product_feed_item" ||
            value == "shopping_ads_grid_item_sponsored" ||
            value == "custom_ad_row" ||
            value == "on_feed_advertiser_disclosure_view" ||
            value.startsWith("ad_card_") ||
            value.startsWith("collection_ad_") ||
            value.startsWith("intent_aware_ad_") ||
            value.startsWith("lead_gen_card") ||
            value == "lead_ad_multi_submit_row" ||
            value == "promote_ad_messaging_post_selector_card" ||
            value == "profile_extension_ad_two_by_two_grid_card" ||
            value == "sponsored_clips_showreel_view" ||
            value == "sponsored_reel_showreel_composition_view" ||
            value == "story_interstitial_reel_item_container"
    }

    private fun isSponsoredResourceName(name: String?): Boolean {
        val value = name?.lowercase(Locale.US) ?: return false
        return value.contains("sponsored") ||
            value.contains("advertiser_disclosure") ||
            value.contains("ad_label") ||
            value == "row_feed_cta" ||
            value == "row_feed_cta_stub" ||
            value == "row_feed_cta_wrapper" ||
            value == "row_feed_cta_redesign" ||
            value == "row_feed_cta_overlay" ||
            value.contains("feed_delayed_skip_ad") ||
            value.contains("story_delayed_skip_ad") ||
            value.contains("reel_bottom_ad_banner") ||
            value.contains("reel_item_ad_label") ||
            value.contains("messaging_ad_suggested_post") ||
            value.contains("inbox_messaging_ad_response") ||
            value.contains("ad_overlay") ||
            value.contains("feed_ad_end_card") ||
            value.contains("lead_ad_multi_submit") ||
            value.contains("intent_aware_ad") ||
            value.contains("collection_ad") ||
            value.contains("shopping_ads_grid_item_sponsored") ||
            value.contains("sponsored_label") ||
            value.contains("sponsored_content") ||
            value.contains("clips_sponsored") ||
            value.contains("feed_sponsored") ||
            value.contains("reel_sponsored") ||
            value.contains("on_media_sponsored") ||
            value.contains("bottom_right_sponsored")
    }

    private fun isSponsoredText(text: String?): Boolean {
        val clean = text?.trim().orEmpty()
        return clean.equals("Sponsored", ignoreCase = true) ||
            clean.equals("Sponsored ·", ignoreCase = true) ||
            clean.lowercase(Locale.US).matches(Regex("^sponsored\\s*[·.]*\\s*$"))
    }

    private fun isAdDisclosureText(text: String?): Boolean {
        val clean = text?.trim().orEmpty()
        return clean.equals("Ad", ignoreCase = true) ||
            clean.lowercase(Locale.US).matches(Regex("^ad\\s*\\W{0,2}\\s*$"))
    }

    private fun isAdDisclosureResourceName(name: String?): Boolean {
        val value = name?.lowercase(Locale.US) ?: return false
        return value == "on_feed_advertiser_disclosure_view" ||
            value == "label_top_sponsored" ||
            value == "reel_item_ad_label_media_bottom_right" ||
            value.contains("advertiser_disclosure") ||
            value.contains("sponsored_label") ||
            value.contains("_ad_label")
    }

    private fun hasFeedAdDisclosureContext(view: View): Boolean {
        val ownName = resourceEntryName(view)
        if (isAdDisclosureResourceName(ownName)) return true
        var current: View? = view
        var depth = 0
        while (depth++ < 6 && current != null) {
            val parentView = current.parent as? View ?: break
            val parentName = resourceEntryName(parentView)
            if (parentName != null) {
                val lowerName = parentName.lowercase(Locale.US)
                if (lowerName == "row_feed_profile_header" ||
                    lowerName == "on_feed_profile_header_view" ||
                    lowerName == "row_feed_media_profile_header" ||
                    lowerName.contains("sponsored_label")
                ) return true
                if (isHardListBoundary(parentView) || isUnsafeGlobalResourceName(parentName)) return false
            }
            current = parentView
        }
        return false
    }

    private fun hasAdDisclosureNeighborContext(view: View): Boolean {
        var current: View? = view
        var depth = 0
        while (depth++ < 8 && current != null) {
            val parentView = current.parent as? View ?: break
            val parentName = resourceEntryName(parentView)
            if (isHardListBoundary(parentView) || isUnsafeGlobalResourceName(parentName)) return false
            val lowerName = parentName?.lowercase(Locale.US).orEmpty()
            if (lowerName.contains("clips") ||
                lowerName.contains("reel") ||
                lowerName.contains("feed") ||
                lowerName.contains("media") ||
                lowerName.contains("profile_header")
            ) return true
            if (isSponsoredViewTree(parentView)) return true
            current = parentView
        }
        return false
    }

    private fun isUnsafeHideTarget(view: View?, allowLargeSponsored: Boolean = false): Boolean {
        if (view == null) return true
        val name = resourceEntryName(view)
        if (isStrongSponsoredCardContainer(name)) return false
        if (view.rootView == view || isHardListBoundary(view)) return true
        if (!allowLargeSponsored && isTooLargeToHide(view)) return true
        if (isUnsafeGlobalResourceName(name)) return true
        val className = view.javaClass.name
        return className.contains("CoordinatorLayout") ||
            className.contains("DrawerLayout") ||
            className.contains("SwipeRefreshLayout") ||
            className.contains("FragmentContainerView")
    }

    private fun isUnsafeGlobalResourceName(name: String?): Boolean {
        val lowerName = name?.lowercase(Locale.US) ?: return false
        return lowerName == "content" ||
            lowerName == "android:id/content" ||
            lowerName.contains("root") ||
            lowerName.contains("fragment") ||
            lowerName.contains("main_container") ||
            lowerName.contains("navigation") ||
            lowerName.contains("nav_bar") ||
            lowerName.contains("tab_bar") ||
            lowerName.contains("feed_view") ||
            lowerName.contains("view_pager")
    }

    private fun isTooLargeToHide(view: View): Boolean {
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return false
        val metrics = view.resources.displayMetrics
        return width >= metrics.widthPixels * 0.92f && height >= metrics.heightPixels * 0.82f
    }

    private fun getViewHolderItemView(holder: Any?): View? {
        if (holder == null) return null
        return runCatching {
            val field = holder.javaClass.getField("itemView")
            field.get(holder) as? View
        }.getOrNull()
    }

    private fun hideConversationRow(textView: TextView) {
        val row = findConversationRowContainer(textView) ?: textView
        row.visibility = View.GONE
        row.alpha = 0f
        row.layoutParams?.let { params ->
            params.height = 0
            row.layoutParams = params
        }
    }

    private fun findConversationRowContainer(start: View): View? {
        var current: View? = start
        repeat(10) {
            val active = current ?: return@repeat
            val parent = active.parent as? View ?: return active
            val parentId = resourceEntryName(parent).orEmpty()
            val parentClass = parent.javaClass.name
            if (parentId == "inbox_refreshable_thread_list_recyclerview" ||
                parentId == "target_thread_list" ||
                parentId == "existing_thread_recycler_view" ||
                parentClass.contains("RecyclerView") ||
                parentClass.contains("ListView")
            ) {
                return active
            }
            current = parent
        }
        return current
    }

    private fun maybeCollectOrHideChatRow(textView: TextView, raw: String): Boolean {
        val displayName = cleanChatName(raw)
        if (displayName.isBlank()) return false
        if (!isInboxConversationListView(textView) || isSearchSurface(textView)) return false
        if (!isDirectThreadNameView(textView) && !isPlausibleChatName(displayName)) return false

        recordKnownChatName(displayName)

        if (!state.enableHideChats) return false
        val hiddenNames = parseChatNameLines(state.hiddenChatNames).map { it.lowercase(Locale.US) }.toSet()
        if (hiddenNames.isEmpty() || displayName.lowercase(Locale.US) !in hiddenNames) return false
        hideConversationRow(textView)
        return true
    }

    private fun recordKnownChatName(displayName: String) {
        val normalized = displayName.lowercase(Locale.US)
        if (normalized.isBlank()) return
        synchronized(knownChatNames) {
            if (knownChatNames.isEmpty()) knownChatNames.addAll(parseChatNameLines(state.knownChatNames))
            if (knownChatNames.any { it.lowercase(Locale.US) == normalized }) return
            knownChatNames += displayName
            scheduleKnownChatNamesPersist()
            logInfo("Collected Instagram Direct chat for picker: $displayName")
        }
    }

    private fun scheduleKnownChatNamesPersist() {
        if (knownChatNamesPersistScheduled) return
        knownChatNamesPersistScheduled = true
        mainHandler.postDelayed({
            val snapshot = synchronized(knownChatNames) { knownChatNames.joinToString("\n") }
            knownChatNamesPersistScheduled = false
            persistStringFeatureUpdate("knownChatNames", snapshot)
        }, 1_000L)
    }

    private fun isDirectThreadNameView(textView: TextView): Boolean {
        return when (resourceEntryName(textView).orEmpty()) {
            "thread_name",
            "thread_title",
            "direct_action_row_name",
            "direct_inbox_campaign_thread_name",
            "direct_unsupported_thread_name",
            "meta_ai_voice_thread_name" -> true
            else -> false
        }
    }

    private fun isInboxConversationListView(view: View): Boolean {
        var current: View? = view
        repeat(12) {
            val id = current?.let { resourceEntryName(it) }.orEmpty()
            if (id == "inbox_refreshable_thread_list_recyclerview" ||
                id == "thread_list_container" ||
                id == "target_thread_list" ||
                id == "existing_thread_recycler_view" ||
                id == "direct_inbox_action_bar"
            ) return true
            current = current?.parent as? View
        }
        return false
    }

    private fun isSearchSurface(view: View): Boolean {
        if (hasActiveSearchInput(view.rootView ?: view)) return true
        var current: View? = view
        repeat(12) {
            val id = current?.let { resourceEntryName(it) }.orEmpty().lowercase(Locale.US)
            val cls = current?.javaClass?.name.orEmpty().lowercase(Locale.US)
            if (id.contains("search") || cls.contains("search")) return true
            current = current?.parent as? View
        }
        return false
    }

    private fun hasActiveSearchInput(view: View?): Boolean {
        if (view == null || view.visibility != View.VISIBLE) return false
        val id = resourceEntryName(view).orEmpty().lowercase(Locale.US)
        if ((id.contains("search") || id.contains("target_thread_search")) &&
            view is EditText &&
            (view.hasFocus() || (view.text?.isNotEmpty() == true))
        ) return true
        if (view is ViewGroup) {
            val count = view.childCount.coerceAtMost(120)
            for (i in 0 until count) {
                if (hasActiveSearchInput(view.getChildAt(i))) return true
            }
        }
        return false
    }

    private fun isPlausibleChatName(value: String): Boolean {
        val clean = cleanChatName(value)
        if (clean.length !in 2..80) return false
        val lower = clean.lowercase(Locale.US)
        if (lower.contains("sent ") ||
            lower.contains("you:") ||
            lower.contains("seen") ||
            lower.contains("active") ||
            lower.contains("typing") ||
            lower.contains("replied") ||
            lower.contains("liked") ||
            lower.contains("shared") ||
            Regex(".*\\b\\d+[smhdw]\\b.*").matches(lower) ||
            Regex("\\d{1,2}:\\d{2}.*").matches(lower) ||
            lower == "notes" ||
            lower == "requests" ||
            lower == "messages"
        ) return false
        return true
    }

    private fun parseChatNameLines(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split('\n', ',')
            .map { cleanChatName(it) }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.US) }
    }

    private fun cleanChatName(value: String): String = value.replace('\n', ' ').trim()

    private fun matchesHiddenSelector(view: View): Boolean {
        val selectors = state.hiddenUiElementSelectorSet
        if (selectors.isEmpty()) return false
        return selectors.any { selector -> selectorMatches(view, selector) }
    }

    private fun installNavigationNativeTabFactoryHook() {
        runSafe("Navigation native tab factory hook") {
            val candidates = linkedSetOf<Class<*>>()
            runCatching { Class.forName("com.instagram.mainactivity.InstagramMainActivity", false, appClassLoader) }
                .getOrNull()
                ?.let(candidates::add)
            if (candidates.isEmpty()) {
                listOf("InstagramMainActivity.createTabButton(", "feed_tab", "clips_tab").forEach { marker ->
                    dexBridge.findClassNamesUsingStrings(marker).forEach { className ->
                        runCatching { Class.forName(className, false, appClassLoader) }.getOrNull()?.let(candidates::add)
                    }
                }
            }
            var hooked = 0
            candidates.forEach { cls ->
                cls.declaredMethods.filter(::looksLikeNavigationTabFactory).forEach { method ->
                    val signature = "${method.declaringClass.name}.${method.name}:navigation_native_tab_factory"
                    if (!hookedDexMethods.add(signature)) return@forEach
                    method.isAccessible = true
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam<*>) {
                                val tabView = param.result as? View ?: return
                                val enumArg = findNavigationTabEnumArg(param.args)
                                val key = inferNavigationTabKey(tabView, -1, -1) ?: mapNavigationTabEnum(enumArg) ?: return
                                nativeNavigationTabKeys[tabView] = key
                                applySingleNavigationTabVisibility(tabView, key)
                                val parent = findViewGroupArg(param.args) ?: tabView.parent as? ViewGroup
                                if (parent != null) {
                                    nativeNavigationTabBars += parent
                                    listOf(0L, 96L, 420L).forEach { delay ->
                                        mainHandler.postDelayed({
                                            applyNavigationTabBar(parent)
                                            (findActivity(parent.context) ?: currentActivity)?.let { clickDefaultNavigationTab(it) }
                                        }, delay)
                                    }
                                }
                                maybeClickDefaultNavigationTab(tabView, key)
                                mainHandler.post { applyNearNativeNavigationTab(tabView) }
                                mainHandler.postDelayed({ applyNearNativeNavigationTab(tabView) }, 160L)
                            }
                        }
                    )
                    hooked++
                }
            }
            logInfo("Installed native navigation tab factories=$hooked classes=${candidates.size}")
        }
    }

    private fun looksLikeNavigationTabFactory(method: Method): Boolean {
        if (!View::class.java.isAssignableFrom(method.returnType) || method.parameterTypes.size < 3) return false
        val hasViewGroup = method.parameterTypes.any { ViewGroup::class.java.isAssignableFrom(it) }
        val hasEnumish = method.parameterTypes.any { type ->
            val name = type.name.lowercase(Locale.US)
            type.isEnum || name.contains("enumc") || name.contains("tab") || name.contains("76162") || name.contains("2rs")
        }
        val owner = "${method.declaringClass.name} ${method.name}".lowercase(Locale.US)
        return hasViewGroup && (hasEnumish || owner.contains("instagrammainactivity"))
    }

    private fun findNavigationTabEnumArg(args: Array<Any?>?): Any? {
        args?.forEach { arg ->
            if (arg == null || arg is Context || arg is View || arg is ViewGroup) return@forEach
            val desc = describeNavigationTabEnum(arg)
            if (desc.length <= 320 &&
                (mapNavigationTabEnum(arg) != null || desc.contains("fragment_") ||
                    desc.contains("_tab") || desc.contains("direct_inbox") || desc.contains("clips"))
            ) return arg
        }
        return null
    }

    private fun findViewGroupArg(args: Array<Any?>?): ViewGroup? = args?.firstOrNull { it is ViewGroup } as? ViewGroup

    private fun applyNearNativeNavigationTab(tabView: View) {
        val bar = findNativeNavigationSiblingBar(tabView) ?: return
        nativeNavigationTabBars += bar
        applyNavigationTabBar(bar)
        (findActivity(bar.context) ?: currentActivity)?.let { clickDefaultNavigationTab(it) }
    }

    private fun findNativeNavigationSiblingBar(tabView: View): ViewGroup? {
        var current: View? = tabView
        var best: ViewGroup? = null
        repeat(10) {
            val group = current as? ViewGroup
            if (group != null) {
                val nativeCount = countNativeNavigationTabs(group, 0)
                val tabs = directNavigationTabChildren(group)
                if (nativeCount >= 3 || tabs.count { it.key != null } >= 3) {
                    best = group
                    if (tabs.size in 3..8) return best
                }
            }
            current = current?.parent as? View
        }
        return best
    }

    private fun countNativeNavigationTabs(view: View, depth: Int): Int {
        if (depth > 9) return 0
        var count = if (nativeNavigationTabKeys.containsKey(view)) 1 else 0
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) count += countNativeNavigationTabs(view.getChildAt(index), depth + 1)
        }
        return count
    }

    private fun mapNavigationTabEnum(value: Any?): String? {
        val desc = describeNavigationTabEnum(value)
        if (desc.isBlank() || desc.length > 500 || (desc.contains("activity_task") && desc.contains("window"))) return null
        return when {
            desc.contains("direct") || desc.contains("inbox") || desc.contains("messenger") -> "direct"
            desc.contains("clips") || desc.contains("reels") || desc.contains("reel") -> "reels"
            desc.contains("search") || desc.contains("explore") -> "search"
            desc.contains("profile") || desc.contains("account") -> "profile"
            desc.contains("creation") || desc.contains("share") || desc.contains("gallery_camera") ||
                desc.contains("new_post") || desc.contains("camera") -> "create"
            desc.contains("shopping") || desc.contains("shop") -> "shop"
            desc.contains("feed_switcher") || desc.contains("fragment_feed") || desc.contains("feed_tab") || desc.contains("feed") -> "home"
            else -> null
        }
    }

    private fun describeNavigationTabEnum(value: Any?): String {
        if (value == null) return ""
        val output = StringBuilder(value.toString())
        runCatching { value.javaClass.getMethod("name").invoke(value)?.let { output.append(' ').append(it) } }
        var cls: Class<*>? = value.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                if (field.type != String::class.java || Modifier.isStatic(field.modifiers)) return@forEach
                runCatching {
                    field.isAccessible = true
                    field.get(value)?.let { output.append(' ').append(it) }
                }
            }
            cls = cls.superclass
        }
        return output.toString().lowercase(Locale.US)
    }

    private fun applyMonetThemeToView(view: View) {
        if (!monetActive() || !isInstagramView(view)) return
        recolorMonetView(view)
        if (view is TextView) handleMonetTextSignal(view)
    }

    private fun applyMonetToActivity(activity: Activity) {
        if (!monetActiveActivity(activity)) return
        val palette = monetPalette() ?: return
        activity.window?.let { window ->
            applyMonetSystemBars(window, palette)
            val decor = window.decorView
            decor.post { applyMonetSystemBars(window, palette) }
            decor.postDelayed({ applyMonetSystemBars(window, palette) }, 80L)
            decor.postDelayed({ applyMonetSystemBars(window, palette) }, 250L)
            decor.post { scanTree(decor, "monet") }
            decor.postDelayed({ scanTree(decor, "monet delayed") }, 250L)
        }
    }

    private fun applyMonetSystemBars(window: Window, palette: MonetPalette) {
        window.statusBarColor = palette.surface
        window.navigationBarColor = palette.surface
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.navigationBarDividerColor = palette.outline
        }
        val decor = window.decorView
        var flags = decor.systemUiVisibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = if (palette.light) flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            else flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = if (palette.light) flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            else flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
        decor.systemUiVisibility = flags
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            val appearance = if (palette.light) mask else 0
            window.insetsController?.setSystemBarsAppearance(appearance, mask)
        }
    }

    private fun handleMonetTextSignal(textView: TextView) {
        val palette = monetPalette() ?: return
        if (!isInstagramView(textView)) return
        if (isPrimaryActionText(textView)) {
            textView.setTextColor(monetReadableOn(palette.primary))
            findPrimaryActionTarget(textView)?.let { tintOrPaintMonetBackground(it, palette.primary, roundedFallback = false, allowMissing = false) }
            return
        }
        val descriptor = monetViewDescriptor(textView)
        if (isSearchPromptText(textView) || isSearchSurfaceRole(textView, descriptor) || isCommentTextSignal(textView) ||
            isNotificationTextSignal(textView) || isDiscoveryTextSignal(textView)
        ) {
            var current: View? = textView
            var depth = 0
            while (current != null && depth++ < 5) {
                val name = monetViewDescriptor(current)
                if (isSearchContainer(current, name) || isSurfaceViewName(name) || isSurfaceContainerName(name) ||
                    isElevatedViewName(name) || isUtilitySurface(current, name, drawableDefaultColor(current.background) ?: palette.surface)
                ) {
                    tintOrPaintMonetBackground(
                        current,
                        if (isElevatedViewName(name) || isSearchContainer(current, name)) palette.surfaceElevated else palette.surfaceContainer,
                        roundedFallback = isRoundedSurfaceName(name),
                        allowMissing = true
                    )
                }
                current = current.parent as? View
            }
        }
    }

    private fun recolorMonetView(view: View) {
        val palette = monetPalette() ?: return
        val name = monetViewDescriptor(view)
        if (isMediaScoped(name) || isMediaViewName(name) || isDirectThreadBodySurface(name) || isTransientDirectOverlay(name)) return
        val backgroundColor = drawableDefaultColor(view.background)
        when {
            view is TextView && isPrimaryActionText(view) -> handleMonetTextSignal(view)
            backgroundColor != null && isInsetSpacerCandidate(view, backgroundColor) ->
                tintOrPaintMonetBackground(view, palette.surfaceContainer, roundedFallback = false, allowMissing = true)
            isButtonLikeView(view, name) || isAccentViewName(name) ->
                tintOrPaintMonetBackground(view, palette.primary, roundedFallback = false, allowMissing = false)
            isElevatedViewName(name) ->
                tintOrPaintMonetBackground(view, palette.surfaceElevated, roundedFallback = isRoundedSurfaceName(name), allowMissing = true)
            isSearchSurfaceRole(view, name) || isSearchContainer(view, name) ->
                tintOrPaintMonetBackground(view, palette.surfaceElevated, roundedFallback = true, allowMissing = true)
            isCommentSurfaceName(name) || isNotificationScreenSurface(view, name) ->
                tintOrPaintMonetBackground(view, palette.surfaceContainer, roundedFallback = isRoundedSurfaceName(name), allowMissing = true)
            backgroundColor != null && isUtilitySurface(view, name, backgroundColor) ->
                tintOrPaintMonetBackground(view, palette.surfaceContainer, roundedFallback = shouldRoundUtilitySurface(view, name), allowMissing = true)
            isSurfaceViewName(name) || isAnonymousNeutralPanel(view, name) ->
                tintOrPaintMonetBackground(view, palette.surfaceContainer, roundedFallback = false, allowMissing = shouldPaintMissingBackground(view, name))
            backgroundColor != null && isSafeNeutralContainer(view, name, backgroundColor) ->
                tintOrPaintMonetBackground(view, palette.surfaceContainer, roundedFallback = false, allowMissing = true)
        }
    }

    private fun recolorMonetAssignedBackground(view: View, drawable: Drawable?) {
        if (drawable == null || !isInstagramView(view)) return
        val color = drawableDefaultColor(drawable)
        val name = monetViewDescriptor(view)
        val actionRole = isPrimaryActionRole(view, name)
        val searchRole = isSearchSurfaceRole(view, name)
        if (color != null && !isNeutralColor(color) && colorForKnownInstagramBlue(color) == null && !actionRole && !searchRole) return
        if (isMediaScoped(name) || isMediaViewName(name) || isDirectThreadBodySurface(name) || isTransientDirectOverlay(name)) return
        if (drawable is ColorDrawable || drawable is GradientDrawable || actionRole || searchRole ||
            isAccentViewName(name) || isElevatedViewName(name) || isSurfaceViewName(name)
        ) {
            recolorMonetView(view)
            if (view.width <= 0 || view.height <= 0) view.post { recolorMonetView(view) }
        }
    }

    private fun tintOrPaintMonetBackground(view: View?, color: Int, roundedFallback: Boolean, allowMissing: Boolean) {
        if (view == null || !isInstagramView(view)) return
        val marker = monetBackgroundMarker(color, roundedFallback)
        if (monetAppliedBackgrounds[view] == marker && view.background != null) return
        val background = view.background
        var changed = false
        runCatching {
            when (background) {
                is ColorDrawable -> {
                    background.mutate()
                    background.color = color
                    view.invalidate()
                    changed = true
                }
                is GradientDrawable -> {
                    background.mutate()
                    background.setColor(color)
                    view.invalidate()
                    changed = true
                }
                null -> {
                    if (allowMissing && view is ViewGroup) {
                        view.background = if (roundedFallback) roundedMonetBackground(view, color) else ColorDrawable(color)
                        changed = true
                    }
                }
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        view.backgroundTintList = ColorStateList.valueOf(color)
                        changed = true
                    }
                }
            }
        }
        if (changed) monetAppliedBackgrounds[view] = marker
    }

    private fun clearMonetAppliedBackground(view: View) {
        monetAppliedBackgrounds.remove(view)
    }

    private fun monetBackgroundMarker(color: Int, roundedFallback: Boolean): Int {
        return color xor if (roundedFallback) 0x40000000 else 0
    }

    private fun monetBackgroundColorForView(view: View, original: Int): Int? {
        if (!isInstagramView(view)) return null
        val palette = monetPalette() ?: return null
        val knownBlue = colorForKnownInstagramBlue(original) != null
        val name = monetViewDescriptor(view)
        val actionRole = isPrimaryActionRole(view, name)
        val searchRole = isSearchSurfaceRole(view, name)
        if (!knownBlue && !isNeutralColor(original) && !actionRole && !searchRole) return null
        if (isMediaScoped(name) || isMediaViewName(name) || isDirectThreadBodySurface(name) || isTransientDirectOverlay(name)) return null
        if (isReelsScopedAction(view, name) && actionRole) return null
        if (isInsetSpacerCandidate(view, original)) return withOriginalAlpha(palette.surfaceContainer, original)
        if (actionRole || isAccentViewName(name)) return colorForKnownInstagramBlue(original) ?: withOriginalAlpha(palette.primary, original)
        if (searchRole || (isSearchContainer(view, name) && isNeutralColor(original))) return withOriginalAlpha(palette.surfaceElevated, original)
        if (isElevatedViewName(name) && isNeutralColor(original)) return withOriginalAlpha(palette.surfaceElevated, original)
        if (isUtilitySurface(view, name, original)) return withOriginalAlpha(palette.surfaceContainer, original)
        if ((isSurfaceViewName(name) || isAnonymousNeutralPanel(view, name)) && isNeutralColor(original)) return withOriginalAlpha(palette.surfaceContainer, original)
        if (isSafeNeutralContainer(view, name, original)) return withOriginalAlpha(palette.surfaceContainer, original)
        return null
    }

    private fun monetBackgroundTintForView(view: View, original: ColorStateList): ColorStateList? {
        return monetBackgroundColorForView(view, original.defaultColor)?.let { ColorStateList.valueOf(it) }
    }

    private fun monetColorForTypedValue(resources: Resources, value: TypedValue?, original: Int): Int? {
        return value?.resourceId?.takeIf { it != 0 }?.let { monetColorForResource(resources, it, original) }
    }

    private fun monetColorForResource(resources: Resources, resId: Int, original: Int?): Int? {
        if (!isInstagramResource(resources, resId)) return null
        return monetColorForName(resourceEntryName(resources, resId), original)
    }

    private fun monetColorForThemeAttr(attrName: String?, original: Int?): Int? {
        val palette = monetPalette() ?: return null
        val name = normalized(attrName)
        if (isMediaScoped(name)) return null
        return when {
            isAccentAttr(name) -> withOriginalAlpha(palette.primary, original)
            isAccentContainerAttr(name) -> withOriginalAlpha(palette.primaryContainer, original)
            isElevatedAttr(name) -> withOriginalAlpha(palette.surfaceElevated, original)
            isSurfaceContainerAttr(name) -> withOriginalAlpha(palette.surfaceContainer, original)
            isSurfaceAttr(name) -> withOriginalAlpha(palette.surface, original)
            isOutlineAttr(name) -> withOriginalAlpha(palette.outline, original)
            else -> null
        }
    }

    private fun monetColorForName(resourceName: String?, original: Int?): Int? {
        val palette = monetPalette() ?: return null
        val name = normalized(resourceName)
        if (name.isBlank() || isMediaScoped(name)) return null
        return when {
            isAccentName(name) -> withOriginalAlpha(palette.primary, original)
            isAccentContainerName(name) -> withOriginalAlpha(palette.primaryContainer, original)
            isElevatedName(name) -> withOriginalAlpha(palette.surfaceElevated, original)
            isSurfaceContainerName(name) -> withOriginalAlpha(palette.surfaceContainer, original)
            isSurfaceName(name) -> withOriginalAlpha(palette.surface, original)
            isOutlineName(name) -> withOriginalAlpha(palette.outline, original)
            else -> null
        }
    }

    private fun monetColorStateListForName(resourceName: String?, original: ColorStateList?): ColorStateList? {
        val color = monetColorForName(resourceName, original?.defaultColor) ?: return null
        val name = normalized(resourceName)
        if (!isAccentName(name) && !isAccentContainerName(name)) return ColorStateList.valueOf(color)
        val palette = monetPalette() ?: return ColorStateList.valueOf(color)
        val disabled = withAlpha(color, 0x61)
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed),
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf()
            ),
            intArrayOf(palette.primary, palette.primaryStrong, disabled, color)
        )
    }

    private fun monetShapeColorReplacement(color: Int, palette: MonetPalette): Int? {
        if (Color.alpha(color) < 0xc0 || !isNeutralColor(color)) return null
        return withOriginalAlpha(palette.surfaceContainer, color)
    }

    private fun refreshMonetPalette(context: Context?) {
        context ?: return
        monetPalette = MonetPalette.from(context)
        monetAppliedBackgrounds.clear()
    }

    private fun monetPalette(): MonetPalette? {
        monetPalette?.let { return it }
        refreshMonetPalette(currentActivity ?: androidContext)
        return monetPalette
    }

    private fun monetActive(): Boolean = state.enableMonetTheme && monetPalette() != null

    private fun monetActiveActivity(activity: Activity?): Boolean {
        return state.enableMonetTheme && activity != null && isSupportedInstagramPackage(activity.packageName) && monetPalette() != null
    }

    private fun monetReadableOn(color: Int): Int {
        return if (luminance(color) > 0.62) Color.BLACK else Color.WHITE
    }

    private fun roundedMonetBackground(view: View, color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = if (view.height in 1..dp(view, 56)) maxOf(dp(view, 8).toFloat(), view.height / 2f) else dp(view, 8).toFloat()
        }
    }

    private fun drawableDefaultColor(drawable: Drawable?): Int? {
        return when (drawable) {
            is ColorDrawable -> drawable.color
            else -> null
        }
    }

    private fun isColorValue(value: TypedValue?): Boolean {
        return value != null && value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT
    }

    private fun isInstagramResource(resources: Resources, resId: Int): Boolean {
        if (resId == 0) return false
        return runCatching { isSupportedInstagramPackage(resources.getResourcePackageName(resId)) }.getOrDefault(false)
    }

    private fun isInstagramView(view: View?): Boolean {
        view ?: return false
        return isSupportedInstagramPackage(view.context?.packageName)
    }

    private fun isSupportedInstagramPackage(packageName: String?): Boolean {
        return packageName == Constants.INSTAGRAM_PACKAGE_NAME
    }

    private fun resourceEntryName(resources: Resources, resId: Int): String? {
        if (resId == 0) return null
        return runCatching { resources.getResourceEntryName(resId) }.getOrNull()
    }

    private fun monetViewDescriptor(view: View?): String {
        view ?: return ""
        val builder = StringBuilder()
        var current: View? = view
        var depth = 0
        while (current != null && depth++ < 4) {
            builder.append(' ').append(resourceEntryName(current).orEmpty())
            builder.append(' ').append(current.javaClass.name)
            builder.append(' ').append(current.contentDescription?.toString().orEmpty())
            if (current is TextView) {
                builder.append(' ').append(current.text?.toString().orEmpty())
                builder.append(' ').append(current.hint?.toString().orEmpty())
            }
            current = current.parent as? View
        }
        return builder.toString().lowercase(Locale.US)
    }

    private fun primaryTextOrHint(textView: TextView): String {
        val text = textView.text?.toString().orEmpty()
        if (text.isNotBlank()) return text
        return textView.hint?.toString().orEmpty()
    }

    private fun findPrimaryActionTarget(textView: TextView): View? {
        var current: View? = textView
        var depth = 0
        while (current != null && depth++ < 4) {
            val name = monetViewDescriptor(current)
            if (current.background != null && (isPrimaryActionRole(current, name) || isPrimaryActionContainer(current, name))) return current
            current = current.parent as? View
        }
        return null
    }

    private fun isPrimaryActionContainer(view: View, name: String): Boolean {
        if (view !is ViewGroup) return false
        return isPrimaryActionName(name) || name.contains("button_container") || name.contains("action_container")
    }

    private fun isPrimaryActionText(view: View?): Boolean {
        val textView = view as? TextView ?: return false
        val text = normalized(primaryTextOrHint(textView)).trim()
        return text == "follow" || text == "follow back" || text == "get app" || text == "install" ||
            text == "create" || text == "connect" || (text == "search" && isSearchActionText(textView)) ||
            text == "confirm" || text == "done" || text == "send" || text == "message"
    }

    private fun isSearchActionText(view: View): Boolean {
        return monetViewDescriptor(view).contains("search")
    }

    private fun normalized(value: String?): String = value?.lowercase(Locale.US).orEmpty()

    private fun isMediaScoped(name: String): Boolean {
        return name.contains("on_media") || name.contains("media_background") ||
            name.contains("photo_overlay") || name.contains("photo_placeholder") ||
            name.contains("camera_") || name.contains("clips_progress") || name.contains("shutter")
    }

    private fun isMediaViewName(name: String): Boolean {
        return name.contains("avatar") ||
            name.contains("profile_pic") ||
            name.contains("profile_photo") ||
            name.contains("profile_picture") ||
            name.contains("user_picture") ||
            name.contains("media_item") ||
            name.contains("media_container") ||
            name.contains("photo_grid") ||
            name.contains("cover_photo") ||
            name.contains("thumbnail") ||
            name.contains("igimageview") ||
            name.contains("drawee") ||
            name.contains("video_container") ||
            name.contains("video_preview") ||
            name.contains("video_player") ||
            name.contains("video_surface") ||
            name.contains("story_tray_item") ||
            name.contains("camera_preview") ||
            name.contains("gallery_grid") ||
            name.contains("gallery_item") ||
            name.contains("feed_media_preview") ||
            name.contains("feed_preview") ||
            name.contains("media_group") ||
            name.contains("row_feed_carousel_media") ||
            name.contains("row_feed_media_image") ||
            name.contains("row_feed_media_video") ||
            name.contains("row_feed_collection_thumbnail_media") ||
            name.contains("row_feed_collection_main_media") ||
            name.contains("textureview") ||
            name.contains("surfaceview") ||
            name.contains("photo_view") ||
            name.contains("video_view") ||
            name.contains("media_view") ||
            name.contains("reel_viewer") ||
            name.contains("clips_viewer") ||
            name.contains("story_viewer")
    }

    private fun isDirectThreadBodySurface(name: String): Boolean {
        return name.contains("direct_thread_content_below_action_bar") ||
            name.contains("thread_toggle_child_fragment_container") ||
            name.contains("thread_view_root") ||
            name.contains("message_thread_container") ||
            name.contains("message_list_refresh_container") ||
            name.contains("message_list") || name.contains("thread_message_list") ||
            name.contains("direct_thread_recycler") || name.contains("direct_thread_view") ||
            name.contains("messagethread") && !name.contains("actionbar") && !name.contains("composer")
    }

    private fun isTransientDirectOverlay(name: String): Boolean {
        return name.contains("message_actions_fragment") ||
            name.contains("composer_overlay_top") ||
            name.contains("drag_and_drop_reaction_overlay") ||
            name.contains("direct_suggested_media_overlay") ||
            name.contains("thread_background_view_overlay") ||
            name.contains("direct_visual_message") || name.contains("view_once") ||
            name.contains("ephemeral") || name.contains("replay") || name.contains("media_overlay")
    }

    private fun isForegroundColorName(name: String): Boolean {
        return name.contains("text") || name.contains("label") || name.contains("icon") ||
            name.contains("glyph") || name.contains("foreground") || name.contains("control") ||
            name.contains("tint") || name.contains("link") || name.contains("action_color") ||
            name.contains("indicator") || name.contains("ripple") || name.contains("graph")
    }

    private fun isGenericBackgroundName(name: String): Boolean {
        if (isMediaScoped(name) || isForegroundColorName(name)) return false
        if (name.contains("action_bar_default_button") || name.contains("igds_action_bar_default_button") ||
            name.contains("default_button_background") || name.contains("dialog_button") ||
            name.contains("secondary_button") || name.contains("transparent_button") ||
            name.contains("borderless_button") || name.contains("icon_button") ||
            name.contains("camera_button") || name.contains("close_button") ||
            name.contains("more_button") || name.contains("like_button") ||
            name.contains("share_button") || name.contains("save_button") ||
            name.contains("comment_button")
        ) return false
        return name.contains("background") || name.contains("_bg") ||
            name.contains("bg_color") || name.contains("bgcolor") || name.contains("surface")
    }

    private fun isAccentAttr(name: String): Boolean {
        if (isForegroundColorName(name)) return false
        return name == "igds_color_primary_button" || name == "igds_color_primary_button_indigo" ||
            name == "igds_color_prism_primary_button_background_indigo" ||
            name == "igds_search_meta_ai_send_button_background_color" ||
            name == "ig_search_meta_ai_send_button_background_color" ||
            name == "igds_color_list_badge" ||
            name.contains("primarybuttonbackground") || name.contains("primary_button_background") ||
            name.contains("primary_pill_button_background") || name.contains("ctabuttonbackground") ||
            name.contains("cta_button_background") || name.contains("confirmbuttonbackground") ||
            name.contains("confirm_button_background") || name.contains("sendbuttonbackground") ||
            name.contains("send_button_background") || name.contains("badgebackground") ||
            name.contains("badge_background")
    }

    private fun isAccentContainerAttr(name: String): Boolean {
        return name == "igds_color_pill_active_background" ||
            name == "igds_color_secondary_button_selected_panavision" ||
            name == "igds_color_secondary_button_selected_pressed_panavision" ||
            name == "igds_color_temporary_highlight" ||
            name.contains("selectedpillbg") || name.contains("activebadge")
    }

    private fun isSurfaceAttr(name: String): Boolean {
        return name == "backgroundcolorprimary" || name == "igdsprimarybackground" ||
            name == "igds_color_primary_background" || name == "android_colorbackground" ||
            name == "windowbackground" || name == "actionbarbackgroundcolor" ||
            name == "statusbarbackgroundcolor" || name == "tabbarbackgroundcolor" ||
            name == "fds_usage_tab_bar_background" || name == "igds_color_clips_tab_bar_background" ||
            name == "directthreadactionbarbackgroundcolor" || name == "modalactionbarbackground" ||
            name == "permissionbannerbackground" || name == "messagecomposerbackgroundcolor" ||
            isGenericBackgroundName(name)
    }

    private fun isSurfaceContainerAttr(name: String): Boolean {
        return name == "backgroundcolorsecondary" || name == "igdssecondarybackground" ||
            name == "igds_color_secondary_background" || name == "igds_color_highlight_background" ||
            name == "igds_color_tertiary_background" || name == "fds_usage_new_notification_background" ||
            name == "igds_color_notification_background" || name == "igds_color_pill_background" ||
            name == "igds_color_form_field_background_default_color" ||
            name == "igds_color_inbox_filter_chip_pressed_background" ||
            name == "igds_color_inbox_filter_chip_selected_background" ||
            name == "igds_color_prism_card_background" || name == "igds_color_prism_chip_background" ||
            name == "igds_color_media_thumbnail_tray_background" || name == "directrowbackgroundresource" ||
            name == "directrowdynamichoverbackgroundresource" || name == "directinboxfooterbackground" ||
            name == "inlinesearchbarbackground" || name == "messagecomposerredesignbackgroundcolor" ||
            name == "messagefromothersgraybackground" || name == "commentsmessagepillbackgroundcolor" ||
            name == "reactionsmessagepillbackgroundcolor" || name == "forwardingandreplyshortcuticonbackgroundcolor"
    }

    private fun isElevatedAttr(name: String): Boolean {
        return name == "elevatedbackgroundcolor" || name == "bottomsheetbackgroundcolor" ||
            name == "bottomsheetbackground" || name == "cardbackgroundcolor" ||
            name == "igds_color_elevated_background" || name == "igds_color_elevated_background_dark" ||
            name == "igds_color_elevated_highlight_background" || name == "directsharesheetbackground" ||
            name == "modalbottomsheetbackground" || name == "reactionspickerbackground" ||
            name == "reactionslistbackground"
    }

    private fun isOutlineAttr(name: String): Boolean {
        return name == "dividercolor" || name == "elevateddividercolor" ||
            name == "messagecomposerbordercolor" || name == "igds_color_separator" ||
            name == "igds_color_stroke" || name == "igds_color_border_secondary" ||
            name.contains("bordercolor")
    }

    private fun isAccentName(name: String): Boolean {
        if (isForegroundColorName(name)) return false
        return name == "ig_search_meta_ai_send_button_background_color" ||
            name == "igds_search_meta_ai_send_button_background_color" ||
            name == "igds_primary_button" || name == "igds_prism_primary_button_background_indigo" ||
            name.contains("primary_button_background") || name.contains("primary_pill_button_background") ||
            name.contains("follow_button_background") || name.contains("feed_cta_button") ||
            name.contains("cta_button_background") || name.contains("confirm_button_background") ||
            name.contains("send_button_background") || name.contains("reg_blue_button_background") ||
            name.contains("smashable_post_button_background") || name.contains("smashable_send_button_background") ||
            name.contains("button_background_indigo") || name.contains("badge_background") ||
            name.contains("list_badge")
    }

    private fun isAccentContainerName(name: String): Boolean {
        return name == "blue_5_10_transparent" || name == "blue_5_20_transparent" ||
            name == "blue_5_50_transparent" || name == "blue_5_60_transparent" ||
            name == "bds_blue_5_30_transparent" || name == "bds_blue_5_70_transparent" ||
            name.contains("pill_active_background") || name.contains("secondary_button_selected") ||
            name.contains("temporary_highlight") || name.contains("selected_text_background")
    }

    private fun isSurfaceName(name: String): Boolean {
        return name == "igds_primary_background" || name == "igds_color_primary_background" ||
            name == "default_bg_color_baseline" || name == "default_bg_color_light" ||
            name == "default_bg_color_dark" || name == "dialog_bg_color_baseline" ||
            name == "menu_item_bg_color" || name == "ig_splash_screen_background" ||
            isGenericBackgroundName(name)
    }

    private fun isSurfaceContainerName(name: String): Boolean {
        return name == "igds_secondary_background" || name == "igds_highlight_background" ||
            name == "igds_elevated_highlight_background" || name == "igds_color_secondary_background" ||
            name == "igds_color_highlight_background" || name == "igds_color_tertiary_background" ||
            name == "igds_color_pill_background" || name == "chat_sticker_chat_bubble_color" ||
            name == "comments_message_pill_background_color" || name == "notes_bubble_background" ||
            name == "igds_color_inbox_filter_chip_pressed_background" ||
            name == "igds_color_inbox_filter_chip_selected_background" ||
            name == "igds_color_media_thumbnail_tray_background" ||
            name == "igds_color_prism_card_background" || name == "igds_color_prism_chip_background" ||
            name == "meta_ai_composer_background" || name == "notification_background" ||
            name == "direct_search_bar_background" || name == "direct_inbox_search_bar_background" ||
            name == "inbox_search_bar_background" || name == "inlinesearchbarbackground" ||
            name == "prompt_creation_card_background_color" || name == "xav_post_card_surface_background" ||
            name.contains("comment_thread") || name.contains("comment_row") ||
            name.contains("comment_cell") || name.contains("comment_list") ||
            name.contains("comments_v2") || name.contains("comments_background") ||
            name.contains("composer_background") || name.contains("direct_search") ||
            name.contains("inbox_search") || name.contains("discover_people") ||
            name.contains("find_friends") || name.contains("connect_contacts") ||
            name.contains("search_for_friends") || name.contains("accounts_to_follow") ||
            name.contains("suggested_users") || name.contains("notification") ||
            name.contains("activity_feed") || name.contains("newsfeed_you") ||
            name.contains("reaction_tray") || name.contains("reactions_tray") ||
            name.contains("emoji_tray") || name.contains("reply_pill_background")
    }

    private fun isElevatedName(name: String): Boolean {
        return name == "igds_elevated_background" || name == "igds_context_menu_background_color" ||
            name == "bottom_sheet_background" || name == "toast_bg_color_baseline" ||
            name == "tooltip_banner_background" || name == "igds_creation_menu_background" ||
            name.contains("elevated_background") || name.contains("bottom_sheet_background")
    }

    private fun isOutlineName(name: String): Boolean {
        return name == "igds_separator" || name == "igds_stroke" ||
            name == "context_line_color" || name == "border_primary" ||
            name == "divider_color_baseline" || name == "hairline_stroke_color_baseline" ||
            name.contains("border_color") || name.contains("divider") || name.contains("separator")
    }

    private fun isSurfaceViewName(name: String): Boolean {
        return isSurfaceName(name) || isSurfaceContainerName(name) ||
            name.contains("action_bar") || name.contains("actionbar") || name.contains("toolbar") ||
            name.contains("tab_bar") || name.contains("tabbar") || name.contains("fds_usage_tab_bar") ||
            name.contains("bottom_navigation") || name.contains("bottom_nav") ||
            name.contains("navigation_bar") || name.contains("nav_bar") ||
            name.contains("layout_container_main") ||
            name.contains("activity_and_camera_shared_views_main_container") ||
            name.contains("swipeable_tab_view_pager") ||
            name.contains("viewpager") || name.contains("view_pager") ||
            name.contains("message_composer") || name.contains("composer") ||
            name.contains("message_composer_bar") ||
            name.contains("comment_composer") || name.contains("composer_container") ||
            name.contains("thread_action_bar") || name.contains("thread_header") ||
            name.contains("direct_thread_header") ||
            name.contains("thread_fragment_container") ||
            name.contains("thread_list") || name.contains("inbox_header") ||
            name.contains("direct_inbox") || name.contains("direct_row") ||
            name.contains("direct_thread") || name.contains("direct_background_view") ||
            name.contains("existing_thread_row") || name.contains("thread_row") ||
            name.contains("create_new_thread_row") ||
            name.contains("inbox") || name.contains("search_bar") || name.contains("searchbar") ||
            name.contains("search_container") || name.contains("search_background_view") ||
            name.contains("search_header_container") || name.contains("search_box") ||
            name.contains("nav_buttons_and_title") || name.contains("title_text_view") ||
            name.contains("drawer_container") || name.contains("main_container") ||
            name.contains("root_view") || name.contains("fragment_container") ||
            name.contains("composeview") || name.contains("androidcomposeview") ||
            name.contains("content_container") || name.contains("screen_container") ||
            name.contains("recycler_view") || name.contains("recyclerview") ||
            name.contains("recycler") || name.contains("list_view") || name.contains("listview") ||
            name.contains("listview_progressbar") ||
            name.contains("scrollview") || name.contains("nestedscrollview") ||
            name.contains("feed_list") || name.contains("newsfeed") ||
            name.contains("fragment_newsfeed") ||
            name.contains("stories_in_feed_tray") || name.contains("stories_tray") ||
            name.contains("reel_tray") || name.contains("notes_tray") || name.contains("note_tray") ||
            name.contains("profile_header") || name.contains("profile_grid") ||
            name.contains("profile_content") || name.contains("clips_profile") ||
            name.contains("accounts_to_follow") ||
            name.contains("suggested_users") || name.contains("discover_people") ||
            name.contains("find_friends") || name.contains("connect_contacts") ||
            name.contains("search_for_friends") || name.contains("notification") ||
            name.contains("activity_feed") || name.contains("newsfeed_you") ||
            name.contains("reaction_tray") || name.contains("reactions_tray") ||
            name.contains("emoji_tray") || name.contains("comment")
    }

    private fun isElevatedViewName(name: String): Boolean {
        return isElevatedName(name) || name.contains("bottom_sheet") ||
            name.contains("modal") || name.contains("dialog") || name.contains("popup") ||
            name.contains("popover") || name.contains("tooltip") ||
            name.contains("context_menu") || name.contains("menu_container") ||
            name.contains("card_container") || name.contains("direct_search") ||
            name.contains("direct_user_search") || name.contains("inbox_search") ||
            name.contains("meta_ai_search") || name.contains("search_bar") ||
            name.contains("searchbar") || name.contains("search_box") ||
            name.contains("search_field") || name.contains("composer_bar") ||
            name.contains("composer_background") || name.contains("pill_background") ||
            name.contains("reply_pill") || name.contains("share_sheet")
    }

    private fun isAccentViewName(name: String): Boolean {
        return isAccentName(name) ||
            name.contains("blue_button") ||
            name.contains("selected_pill") ||
            name.contains("active_badge") ||
            name.contains("list_badge") ||
            name.contains("meta_ai_send")
    }

    private fun isPrimaryActionName(name: String): Boolean {
        if (name.contains("action_bar") || name.contains("actionbar") || name.contains("toolbar")) return false
        return name.contains("primary_button") || name.contains("primary_pill_button") ||
            name.contains("follow_button") || name.contains("feed_cta_button") ||
            name.contains("cta_button") || name.contains("confirm_button") ||
            name.contains("done_button") || name.contains("send_button") ||
            name.contains("create_button") || name.contains("connect_button") ||
            name.contains("search_button") || name.contains("install_button") ||
            name.contains("get_app_button") || name.contains("reg_blue_button_background") ||
            name.contains("smashable_post_button_background") || name.contains("smashable_send_button_background") ||
            name.contains("ig_search_meta_ai_send_button") || name.contains("igds_search_meta_ai_send_button") ||
            name.contains("meta_ai_send")
    }

    private fun isPrimaryActionRole(view: View, name: String): Boolean {
        return isButtonLikeView(view, name) || isPrimaryActionName(name)
    }

    private fun isButtonLikeView(view: View, name: String): Boolean {
        if (isMediaScoped(name) || isMediaViewName(name)) return false
        if (isReelsScopedAction(view, name) && isPrimaryActionText(view)) return false
        if (name.contains("icon_button") || name.contains("back_button") || name.contains("close_button") ||
            name.contains("more_button") || name.contains("menu_button") || name.contains("like_button") ||
            name.contains("share_button") || name.contains("save_button") || name.contains("comment_button") ||
            name.contains("camera_button") || name.contains("tab_button") ||
            name.contains("feed_header_chevron")
        ) return false
        val namedButton = isPrimaryActionName(name) || isPrimaryActionText(view)
        if (!namedButton) return false
        val width = view.width
        val height = view.height
        return width >= dp(view, 64) && height in dp(view, 28)..dp(view, 88) && width >= height
    }

    private fun isSearchPromptText(textView: TextView): Boolean {
        val text = normalized(primaryTextOrHint(textView)).trim()
        return text == "search" ||
            text.startsWith("search or ask") ||
            text.startsWith("search messages") ||
            text.startsWith("search in chat") ||
            text.startsWith("search chats") ||
            text.contains("search instagram")
    }

    private fun isCommentTextSignal(textView: TextView): Boolean {
        val hay = monetViewDescriptor(textView)
        val text = normalized(primaryTextOrHint(textView)).trim()
        return hay.contains("comment") ||
            text == "comments" ||
            text == "reply" ||
            text == "see translation" ||
            (text.startsWith("view ") && text.contains(" repl")) ||
            text.startsWith("add a comment") ||
            text.startsWith("join the conversation") ||
            text.startsWith("comment as ") ||
            text.contains("add a comment for ")
    }

    private fun isDiscoveryTextSignal(textView: TextView): Boolean {
        val hay = monetViewDescriptor(textView)
        val text = normalized(primaryTextOrHint(textView)).trim()
        return hay.contains("discover_people") || hay.contains("suggested") ||
            hay.contains("find_friends") || hay.contains("connect_contacts") ||
            text == "find friends to follow and message" ||
            text == "connect contacts" ||
            text == "find people you know" ||
            text == "search for friends" ||
            text == "find your friends' accounts." ||
            text == "find your friends' accounts" ||
            text == "accounts to follow" ||
            text == "suggested for you" ||
            text == "follow suggestions" ||
            text == "suggested users" ||
            text == "see all"
    }

    private fun isNotificationTextSignal(textView: TextView): Boolean {
        val hay = monetViewDescriptor(textView)
        val text = normalized(primaryTextOrHint(textView)).trim()
        return hay.contains("notification") || hay.contains("activity_feed") || hay.contains("newsfeed") ||
            text == "notifications" ||
            text == "yesterday" ||
            text == "today" ||
            text == "this week" ||
            text == "last 7 days" ||
            text == "earlier" ||
            text.startsWith("follow suggestions")
    }

    private fun isSearchContainer(view: View, name: String): Boolean {
        return name.contains("search_bar") || name.contains("searchbar") ||
            name.contains("inline_search") || name.contains("inlinesearch") ||
            name.contains("direct_user_search") || name.contains("meta_ai") ||
            name.contains("search_box") || name.contains("search_container") ||
            name.contains("search_input") || (view is ViewGroup && name.contains("search"))
    }

    private fun isSearchSurfaceRole(view: View, name: String): Boolean {
        return isSearchContainer(view, name) || name.contains("search_surface") ||
            name.contains("direct_search") || name.contains("direct_user_search_bar") ||
            name.contains("inbox_search") || name.contains("meta_ai_search") ||
            name.contains("search_bar_rounded_background") ||
            name.contains("search_bar_field_container")
    }

    private fun isCommentSurfaceName(name: String): Boolean {
        return name.contains("comment_thread") || name.contains("comment_row") ||
            name.contains("comment_cell") || name.contains("comment_list") ||
            name.contains("comments_v2") || name.contains("comments_background") ||
            name.contains("comment_composer") || name.contains("comments_sheet") ||
            name.contains("comments_bottom_sheet") || name.contains("reply_bar") ||
            name.contains("reactions_tray") || name.contains("emoji_reaction") ||
            name.contains("reply_pill") || (name.contains("bottom_sheet") && name.contains("comment"))
    }

    private fun isNotificationScreenSurface(view: View, name: String): Boolean {
        return view is ViewGroup && (name.contains("notification") || name.contains("activity_feed") ||
            name.contains("newsfeed") || name.contains("fragment_newsfeed") ||
            name.contains("composeview") || name.contains("androidcomposeview"))
    }

    private fun isUtilitySurface(view: View, name: String, original: Int): Boolean {
        if (!isNeutralColor(original)) return false
        return view is ViewGroup && (
            name.contains("discover_people") || name.contains("suggested_users") ||
                name.contains("accounts_to_follow") || name.contains("find_friends") ||
                name.contains("connect_contacts") || name.contains("prompt_creation_card") ||
                name.contains("xav_post_card") || name.contains("reaction_tray") ||
                name.contains("reactions_tray") || name.contains("emoji_tray") ||
                name.contains("emoji_reaction") || name.contains("comment_quick_reaction")
            )
    }

    private fun isAnonymousNeutralPanel(view: View, name: String): Boolean {
        if (view !is ViewGroup || name.isNotBlank()) return false
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return false
        return width < view.resources.displayMetrics.widthPixels * 0.95f &&
            height < view.resources.displayMetrics.heightPixels * 0.70f
    }

    private fun isSafeNeutralContainer(view: View, name: String, original: Int): Boolean {
        if (view !is ViewGroup || view is TextView || !isNeutralColor(original)) return false
        if (isMediaScoped(name) || isMediaViewName(name) || isDirectThreadBodySurface(name) ||
            isTransientDirectOverlay(name) || isButtonLikeView(view, name) || isAccentViewName(name)
        ) return false
        return Color.alpha(original) >= 0xc0
    }

    private fun isInsetSpacerCandidate(view: View, original: Int): Boolean {
        if (!isNeutralColor(original)) return false
        val name = monetViewDescriptor(view)
        return name.contains("status_bar") || name.contains("navigation_bar") ||
            name.contains("inset") || name.contains("system_bar") ||
            (view.height in 1..dp(view, 48) && view.width >= view.resources.displayMetrics.widthPixels * 0.75f)
    }

    private fun isRoundedSurfaceName(name: String): Boolean {
        return name.contains("pill") || name.contains("chip") || name.contains("card") ||
            name.contains("button") || name.contains("search") || name.contains("composer") ||
            name.contains("bottom_sheet") || name.contains("dialog")
    }

    private fun shouldRoundUtilitySurface(view: View, name: String): Boolean {
        return isRoundedSurfaceName(name) || view.height in 1..dp(view, 96)
    }

    private fun shouldPaintMissingBackground(view: View, name: String): Boolean {
        if (view !is ViewGroup) return false
        if (name.contains("row_feed") || name.contains("feed_media") ||
            name.contains("feed_preview") || name.contains("media_group") ||
            name.contains("carousel") || name.contains("stories_tray") ||
            name.contains("story_tray") || name.contains("reel_tray") ||
            name.contains("newsfeed")
        ) return false
        if (isMediaScoped(name) || isMediaViewName(name) || isDirectThreadBodySurface(name)) return false
        return isSurfaceViewName(name) || isSearchContainer(view, name) || isCommentSurfaceName(name) ||
            isNotificationScreenSurface(view, name) || isUtilitySurface(view, name, monetPalette()?.surface ?: Color.TRANSPARENT)
    }

    private fun isReelsScopedAction(view: View, name: String): Boolean {
        if (name.contains("clips_viewer") || name.contains("clipsviewer") ||
            name.contains("reel_viewer") || name.contains("reelviewer") ||
            name.contains("reels_viewer") || name.contains("clips_tab") ||
            name.contains("layout_clips_viewer_media_info") || name.contains("reel_item_follow_button") ||
            name.contains("reel_header_follow_button") || (name.contains("row_reel_viewer") && name.contains("follow_button"))
        ) return true
        var current: View? = view
        var depth = 0
        while (current != null && depth++ < 4) {
            val currentName = monetViewDescriptor(current)
            if (currentName.contains("clips_viewer") || currentName.contains("reel_viewer") ||
                currentName.contains("reelviewer") || currentName.contains("reel_item_follow_button") ||
                currentName.contains("reel_header_follow_button")
            ) return true
            current = current.parent as? View
        }
        return false
    }

    private fun isNeutralColor(color: Int): Boolean {
        if (Color.alpha(color) < 0x20) return false
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        return max - min <= 18 || max <= 36 || min >= 224
    }

    private fun colorForKnownInstagramBlue(original: Int): Int? {
        val palette = monetPalette() ?: return null
        return when (original and 0x00ffffff) {
            0x0095f6, 0x0064d1, 0x1d85fc, 0x47afff, 0x0074cc, 0x0057a3,
            0x00376b, 0x4a5df9, 0x576aff, 0x3b52e7, 0x4150f7, 0x3143e3, 0x8592ff ->
                withOriginalAlpha(pickByLuminance(original, palette), original)
            0xe0f1ff, 0xb3dbff, 0xa1bbff, 0xc7cdff, 0xdbdfff ->
                withOriginalAlpha(palette.primaryContainer, original)
            else -> null
        }
    }

    private fun pickByLuminance(original: Int, palette: MonetPalette): Int {
        val lum = luminance(original)
        return when {
            lum > 0.78 -> palette.primaryContainer
            lum < 0.24 -> palette.primaryStrong
            else -> palette.primary
        }
    }

    private fun withOriginalAlpha(color: Int, original: Int?): Int = if (original == null) color else withAlpha(color, Color.alpha(original))

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00ffffff) or ((alpha and 0xff) shl 24)

    private fun luminance(color: Int): Double {
        return (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) / 255.0
    }

    private fun blend(from: Int, to: Int, amount: Float): Int {
        val clamped = amount.coerceIn(0f, 1f)
        val a = (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * clamped).roundToInt()
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * clamped).roundToInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * clamped).roundToInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * clamped).roundToInt()
        return Color.argb(a, r, g, b)
    }

    private data class MonetPalette(
        val light: Boolean,
        val primary: Int,
        val primaryStrong: Int,
        val primaryContainer: Int,
        val surface: Int,
        val surfaceContainer: Int,
        val surfaceElevated: Int,
        val outline: Int
    ) {
        companion object {
            fun from(context: Context): MonetPalette {
                val light = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) !=
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                val seed = wallpaperSeed(context)
                val fallbackPrimary = if (light) blendStatic(seed, Color.BLACK, 0.18f) else blendStatic(seed, Color.WHITE, 0.35f)
                val fallbackStrong = if (light) blendStatic(seed, Color.BLACK, 0.34f) else blendStatic(seed, Color.WHITE, 0.52f)
                val fallbackContainer = if (light) blendStatic(seed, Color.WHITE, 0.78f) else blendStatic(seed, Color.BLACK, 0.52f)
                val fallbackSurface = if (light) blendStatic(seed, Color.WHITE, 0.96f) else blendStatic(seed, Color.BLACK, 0.88f)
                val fallbackSurfaceContainer = if (light) blendStatic(seed, Color.WHITE, 0.90f) else blendStatic(seed, Color.BLACK, 0.78f)
                val fallbackSurfaceElevated = if (light) blendStatic(seed, Color.WHITE, 0.84f) else blendStatic(seed, Color.BLACK, 0.68f)
                val fallbackOutline = if (light) blendStatic(seed, Color.WHITE, 0.55f) else blendStatic(seed, Color.BLACK, 0.36f)
                return MonetPalette(
                    light,
                    systemColor(context, if (light) "system_accent1_600" else "system_accent1_200", fallbackPrimary),
                    systemColor(context, if (light) "system_accent1_700" else "system_accent1_100", fallbackStrong),
                    systemColor(context, if (light) "system_accent1_100" else "system_accent1_800", fallbackContainer),
                    systemColor(context, if (light) "system_neutral1_99" else "system_neutral1_10", fallbackSurface),
                    systemColor(context, if (light) "system_neutral1_95" else "system_neutral1_20", fallbackSurfaceContainer),
                    systemColor(context, if (light) "system_neutral2_90" else "system_neutral2_30", fallbackSurfaceElevated),
                    systemColor(context, if (light) "system_neutral2_80" else "system_neutral2_60", fallbackOutline)
                )
            }

            private fun wallpaperSeed(context: Context): Int {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    runCatching {
                        val colors: WallpaperColors? = WallpaperManager.getInstance(context)
                            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                        colors?.primaryColor?.toArgb()
                    }.getOrNull()?.let { return it }
                }
                val value = TypedValue()
                runCatching {
                    if (context.theme.resolveAttribute(android.R.attr.colorAccent, value, true)) {
                        if (value.resourceId != 0) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) context.getColor(value.resourceId)
                            else context.resources.getColor(value.resourceId)
                        } else if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                            value.data
                        } else null
                    } else null
                }.getOrNull()?.let { return it }
                return 0xff0095f6.toInt()
            }

            private fun systemColor(context: Context, name: String, fallback: Int): Int {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return fallback
                return runCatching {
                    val id = context.resources.getIdentifier(name, "color", "android")
                    if (id != 0) context.getColor(id) else fallback
                }.getOrDefault(fallback)
            }

            private fun blendStatic(from: Int, to: Int, amount: Float): Int {
                val clamped = amount.coerceIn(0f, 1f)
                val a = (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * clamped).roundToInt()
                val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * clamped).roundToInt()
                val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * clamped).roundToInt()
                val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * clamped).roundToInt()
                return Color.argb(a, r, g, b)
            }
        }
    }

    private fun inStoryTrayResolve(): Boolean = (storyRingTrayResolveDepth.get() ?: 0) > 0

    private fun storyRingScaleFactor(): Float {
        return when (state.storyRingSize.lowercase(Locale.US)) {
            "small" -> 0.85f
            "large" -> 1.15f
            "huge" -> 1.30f
            else -> 1f
        }
    }

    private fun storyRingDimenName(resources: Resources, id: Int): String {
        storyRingIdNameCache[id]?.let { return it }
        val name = runCatching {
            if (resources.getResourceTypeName(id) != "dimen") ""
            else resources.getResourceEntryName(id).lowercase(Locale.US)
        }.getOrDefault("")
        storyRingIdNameCache[id] = name
        return name
    }

    private fun installStoryRingViewSizingHooks() {
        if (!storyRingViewHooksInstalled.compareAndSet(false, true)) return
        runSafe("Story ring view sizing hooks") {
            XposedBridge.hookAllMethods(
                View::class.java,
                "setLayoutParams",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val view = param.thisObject as? View ?: return
                        val params = param.args.firstOrNull() as? ViewGroup.LayoutParams ?: return
                        maybeScaleStoryRingLayout(view, params, "setLayoutParams")
                    }
                }
            )
        }
    }

    private fun installStoryRingTrayConstructorHook() {
        if (!storyRingTrayConstructorHooked.compareAndSet(false, true)) return
        runSafe("Story ring tray constructor hook") {
            if (InstagramDexKitCache.isCacheValid()) {
                InstagramDexKitCache.loadString("StoryRingTrayClass")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { cachedClass ->
                        val hooked = hookStoryRingTrayConstructors(cachedClass)
                        if (hooked > 0) {
                            logInfo("Installed cached story ring tray constructor hook: $cachedClass")
                            return@runSafe
                        }
                    }
            }
            val resources = androidContext.resources
            val pkg = androidContext.packageName
            val ids = listOf(
                "avatar_size_ridiculously_large_plus",
                "prism_avatar_story_ring_size_medium_device",
                "prism_avatar_story_ring_width_large_device",
                "prism_avatar_story_ring_width_small_device",
                "tray_pulsing_avatar_size_small"
            ).map { resources.getIdentifier(it, "dimen", pkg) }
            if (ids.any { it == 0 }) return@runSafe
            val classNames = dexBridge.findClassNamesUsingNumbers(*ids.toIntArray())
            var hooked = 0
            classNames.forEach { className ->
                val classHooked = hookStoryRingTrayConstructors(className)
                if (classHooked > 0) {
                    InstagramDexKitCache.saveString("StoryRingTrayClass", className)
                    hooked += classHooked
                    return@forEach
                }
            }
            logInfo("Installed story ring tray constructor hooks=$hooked")
        }
    }

    private fun hookStoryRingTrayConstructors(className: String): Int {
        val cls = runCatching { Class.forName(className, false, appClassLoader) }.getOrNull() ?: return 0
        var hooked = 0
        cls.declaredConstructors.forEach { constructor ->
            val signature = "${cls.name}#<init>(${constructor.parameterTypes.joinToString { it.name }})"
            if (!hookedDexMethods.add("StoryRingTray:$signature")) return@forEach
            runCatching {
                constructor.isAccessible = true
                XposedBridge.hookMethod(
                    constructor,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam<*>) {
                            storyRingTrayResolveDepth.set((storyRingTrayResolveDepth.get() ?: 0) + 1)
                        }

                        override fun afterHookedMethod(param: MethodHookParam<*>) {
                            storyRingTrayResolveDepth.set(((storyRingTrayResolveDepth.get() ?: 0) - 1).coerceAtLeast(0))
                        }
                    }
                )
                hooked++
            }.onFailure { logError("Failed story ring tray constructor hook $signature", it) }
        }
        return hooked
    }

    private fun applyStoryRingTreeSoon(root: View, delayMs: Long) {
        root.postDelayed({ applyStoryRingTree(root) }, delayMs.coerceAtLeast(0L))
    }

    private fun applyStoryRingTree(root: View?) {
        root ?: return
        val params = root.layoutParams
        if (params != null && maybeScaleStoryRingLayout(root, params, "tree")) root.layoutParams = params
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) applyStoryRingTree(root.getChildAt(i))
        }
    }

    private fun maybeScaleStoryRingLayout(view: View, params: ViewGroup.LayoutParams, source: String): Boolean {
        val factor = storyRingScaleFactor()
        if (factor == 1f || !isStoryRingView(view)) return false
        if (wasAlreadyScaledByStoryRingResourceHook(params)) {
            storyRingOriginalLayoutSizes[view] = OriginalLayoutSize(
                width = if (params.width > 0) (params.width / factor).roundToInt().coerceAtLeast(1) else params.width,
                height = if (params.height > 0) (params.height / factor).roundToInt().coerceAtLeast(1) else params.height,
                scaledWidth = params.width,
                scaledHeight = params.height
            )
            return false
        }
        val original = storyRingOriginalLayoutSizes[view]?.takeIf { saved ->
            (params.width <= 0 || kotlin.math.abs(params.width - saved.scaledWidth) <= 2) &&
                (params.height <= 0 || kotlin.math.abs(params.height - saved.scaledHeight) <= 2)
        } ?: OriginalLayoutSize(params.width, params.height).also { storyRingOriginalLayoutSizes[view] = it }
        var changed = false
        if (original.width > 0) {
            val scaled = (original.width * factor).roundToInt().coerceAtLeast(1)
            if (params.width != scaled) {
                params.width = scaled
                changed = true
            }
            original.scaledWidth = scaled
        }
        if (original.height > 0) {
            val scaled = (original.height * factor).roundToInt().coerceAtLeast(1)
            if (params.height != scaled) {
                params.height = scaled
                changed = true
            }
            original.scaledHeight = scaled
        }
        if (changed) logStoryRingTarget(source, storyRingViewIdentity(view), factor, "${original.width}x${original.height}", "${params.width}x${params.height}")
        return changed
    }

    private fun wasAlreadyScaledByStoryRingResourceHook(params: ViewGroup.LayoutParams): Boolean {
        return (params.width > 0 && storyRingScaledDimenPixels.contains(params.width)) ||
            (params.height > 0 && storyRingScaledDimenPixels.contains(params.height))
    }

    private fun isStoryRingView(view: View): Boolean {
        if (looksLikeStoryRingName(resourceEntryName(view))) return true
        if (looksLikeStoryRingName(view.javaClass.name)) return true
        var parent = view.parent as? View
        var depth = 0
        while (parent != null && depth++ < 4) {
            if (looksLikeStoryRingName(resourceEntryName(parent)) || looksLikeStoryRingName(parent.javaClass.name)) return true
            parent = parent.parent as? View
        }
        return false
    }

    private fun looksLikeStoryRingName(value: String?): Boolean {
        val lower = value?.lowercase(Locale.US) ?: return false
        return lower.contains("reel_ring") || lower.contains("story_ring") ||
            lower.contains("avatarreel") || lower.contains("reelring") || lower.contains("storyring")
    }

    private fun storyRingViewIdentity(view: View): String {
        return "view:${resourceEntryName(view).orEmpty().ifBlank { view.javaClass.simpleName }}"
    }

    private fun logStoryRingTarget(method: String, name: String, factor: Float, from: Any?, to: Any?) {
        val key = "$method:$name:$factor"
        if (!storyRingLoggedTargets.add(key)) return
        logInfo("Story ring scaled $name via $method factor=$factor from=$from to=$to")
    }

    private fun applyStoryRingScale(view: View) {
        val name = (resourceEntryName(view).orEmpty() + " " + view.contentDescription?.toString().orEmpty()).lowercase()
        if (!name.contains("story") && !isStoryRingView(view)) return
        val scale = storyRingScaleFactor()
        if (scale == 1f) return
        view.scaleX = scale
        view.scaleY = scale
        view.rootView?.let { root ->
            applyStoryRingTreeSoon(root, 0L)
            applyStoryRingTreeSoon(root, 250L)
            applyStoryRingTreeSoon(root, 900L)
        }
    }

    private data class OriginalLayoutSize(
        val width: Int,
        val height: Int,
        var scaledWidth: Int = width,
        var scaledHeight: Int = height
    )

    private fun stackLooksLikeStoryRingSurface(): Boolean {
        return Thread.currentThread().stackTrace.take(40).any { frame ->
            val text = "${frame.className}.${frame.methodName}".lowercase(Locale.US)
            !text.contains("direct") && !text.contains("message") &&
                (text.contains("story") || text.contains("stories") || text.contains("reel") ||
                    text.contains("tray") || text.contains("avatarreel"))
        }
    }

    private fun applyTeenIconTweak(view: View) {
        if (teenIconPreparedViews.containsKey(view)) return
        val name = resourceEntryName(view).orEmpty().lowercase(Locale.US)
        val desc = view.contentDescription?.toString()?.lowercase(Locale.US).orEmpty()
        val cls = view.javaClass.name.lowercase(Locale.US)
        val looksLikeLogo = name.contains("instagram_logo") ||
            name.contains("instagram_wordmark") ||
            name.contains("ig_logo") ||
            name.contains("logo_icon") ||
            name.contains("action_bar_logo") ||
            name.contains("nav_logo") ||
            name.contains("brand_header") ||
            desc == "instagram" ||
            desc.contains("instagram logo") ||
            (cls.contains("image") && desc.contains("instagram"))
        if (!looksLikeLogo) return
        teenIconPreparedViews[view] = true
        view.isLongClickable = true
        view.setOnLongClickListener {
            openTeenAppIconPicker(it.context)
        }
    }

    private fun openTeenAppIconPicker(context: Context): Boolean {
        val launchContext = findActivity(context) ?: context
        val routes = listOf(
            "com.instagram.aura.appicon.ui.AuraAppIconPickerFragment",
            "com.instagram.settings.common.AuraAppIconPickerFragment",
            "AuraAppIconPickerFragment",
            "aura_app_icon_picker"
        )
        routes.forEach { route ->
            val launched = runCatching {
                val intent = Intent()
                    .setClassName(launchContext.packageName, "com.instagram.modal.ModalActivity")
                    .putExtra("fragment_name", route)
                    .putExtra("fragment_arguments", Bundle())
                if (launchContext !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchContext.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return true
        }
        Toast.makeText(launchContext, "Could not open Instagram app icon picker", Toast.LENGTH_SHORT).show()
        return false
    }

    private fun applyNavigationTabRules(view: View) {
        if (!isNavigationCustomizationActive()) return
        if (view is ViewGroup) applyNavigationTabBar(view)
        val hidden = state.navigationTabHidden.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        if (hidden.isEmpty()) return
        val name = (resourceEntryName(view).orEmpty() + " " + view.contentDescription?.toString().orEmpty() + " " + (view as? TextView)?.text?.toString().orEmpty()).lowercase()
        val tabKey = when {
            name.contains("home") -> "home"
            name.contains("search") || name.contains("explore") -> "search"
            name.contains("reel") || name.contains("clip") -> "reels"
            name.contains("create") || name.contains("new_post") -> "create"
            name.contains("direct") || name.contains("inbox") || name.contains("message") -> "direct"
            name.contains("shop") -> "shop"
            name.contains("profile") || name.contains("avatar") || name.contains("account") || name.contains("user_tab") -> "profile"
            else -> ""
        }
        if (tabKey in hidden) hideView(view, "navigation tab")
    }

    private fun applyNavigationTabBar(group: ViewGroup) {
        if (!isNavigationCustomizationActive()) return
        val count = group.childCount
        if (count !in 3..10 && group !in nativeNavigationTabBars) return
        val groupHint = collectShortViewText(group, 0).lowercase(Locale.US)
        val looksLikeTabBar = groupHint.contains("tab") || groupHint.contains("nav") ||
            groupHint.contains("bottom") || groupHint.contains("navigation")
        val tabs = directNavigationTabChildren(group).toMutableList()
        val resolvedTabs = if (tabs.count { it.key != null } >= 3) {
            tabs
        } else if (looksLikeTabBar && count in 5..7 && tabs.size >= 4) {
            val defaults = listOf("home", "search", "reels", "create", "direct", "shop", "profile")
            tabs.map { if (it.key == null) it.copy(key = defaults.getOrNull(it.originalIndex)) else it }
        } else {
            return
        }
        if (resolvedTabs.size < 4 || resolvedTabs.count { it.key != null } < 3) return

        val hidden = parseCsv(state.navigationTabHidden)
        val order = parseNavigationOrder(state.navigationTabOrder)
        val signature = resolvedTabs.joinToString("|") { "${it.key}:${it.originalIndex}:${it.view.visibility}" } +
            ";hidden=${hidden.joinToString(",")};order=${order.joinToString(",")}"
        if (navigationAppliedBars[group] == signature) return

        resolvedTabs.forEach { tab ->
            val key = tab.key ?: return@forEach
            if (key in hidden) {
                if (tab.view.visibility != View.GONE) tab.view.visibility = View.GONE
                navigationHiddenViews += tab.view
            } else if (navigationHiddenViews.remove(tab.view) && tab.view.visibility == View.GONE) {
                tab.view.visibility = View.VISIBLE
            }
        }

        val orderIndex = order.withIndex().associate { it.value to it.index }
        val sorted = resolvedTabs.sortedWith(
            compareBy<NavigationTabView> { tab -> tab.key?.let { orderIndex[it] } ?: 1000 + tab.originalIndex }
                .thenBy { it.originalIndex }
        )
        if (sorted.map { it.view } != (0 until count).map { group.getChildAt(it) }) {
            runCatching {
                sorted.forEach { group.removeView(it.view) }
                sorted.forEach { group.addView(it.view) }
            }
        }
        navigationAppliedBars[group] = signature
    }

    private fun directNavigationTabChildren(group: ViewGroup): List<NavigationTabView> {
        val tabs = mutableListOf<NavigationTabView>()
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            val key = inferNavigationTabKey(child, index, group.childCount)
            if (key != null || child.isClickable || child.hasOnClickListeners()) tabs += NavigationTabView(child, key, index)
        }
        if (tabs.count { it.key != null } >= 3 || tabs.size >= 4) return fillNavigationPositionKeys(tabs)

        val leaves = mutableListOf<View>()
        collectNavigationTabLeaves(group, leaves, 0, IntArray(1))
        val slots = LinkedHashMap<View, String?>()
        leaves.forEach { leaf ->
            val slot = topChildUnder(group, leaf) ?: return@forEach
            val key = inferNavigationTabKey(leaf, -1, -1)
            if (!slots.containsKey(slot) || slots[slot] == null) slots[slot] = key
        }
        val slotViews = slots.keys.sortedBy(::centerXOnScreen)
        if (slotViews.size in 3..8) {
            val nested = slotViews.mapIndexed { index, slot ->
                NavigationTabView(slot, slots[slot] ?: positionNavigationFallbackKey(index, slotViews.size), group.indexOfChild(slot))
            }
            if (nested.count { it.key != null } >= 3) return nested
        }
        return fillNavigationPositionKeys(tabs)
    }

    private fun applySingleNavigationTabVisibility(view: View, key: String) {
        val hidden = parseCsv(state.navigationTabHidden)
        if (key in hidden) {
            if (view.visibility != View.GONE) view.visibility = View.GONE
            navigationHiddenViews += view
        } else if (navigationHiddenViews.remove(view) && view.visibility == View.GONE) {
            view.visibility = View.VISIBLE
        }
    }

    private fun maybeClickDefaultNavigationTab(tabView: View, key: String) {
        val target = normalizeNavigationTabKey(state.navigationDefaultTab) ?: return
        if (target == "home" || target != key || target in parseCsv(state.navigationTabHidden)) return
        val root = tabView.rootView ?: tabView
        val clickKey: Any = findActivity(tabView.context) ?: root
        if (navigationDefaultClickedTargets[clickKey] == true) return
        fun click() {
            if (navigationDefaultClickedTargets[clickKey] == true) return
            if (tabView.visibility != View.VISIBLE) return
            if (tabView.performClick() && tabView.isAttachedToWindow) navigationDefaultClickedTargets[clickKey] = true
        }
        mainHandler.postAtFrontOfQueue { click() }
        mainHandler.post { click() }
        mainHandler.postDelayed({ click() }, 24L)
        mainHandler.postDelayed({ click() }, 80L)
        mainHandler.postDelayed({ click() }, 180L)
    }

    private fun inferNavigationTabKey(view: View, index: Int, count: Int): String? {
        nativeNavigationTabKeyInTree(view, 0)?.let { return it }
        val text = collectShortViewText(view, 0).lowercase(Locale.US)
        val key = when {
            text.contains("home") || text.contains("feed") -> "home"
            text.contains("search") || text.contains("explore") -> "search"
            text.contains("reel") || text.contains("clip") -> "reels"
            text.contains("create") || text.contains("new_post") || text.contains("plus") -> "create"
            text.contains("direct") || text.contains("inbox") || text.contains("message") || text.contains("messenger") -> "direct"
            text.contains("shop") -> "shop"
            text.contains("profile") || text.contains("avatar") || text.contains("account") || text.contains("user_tab") -> "profile"
            else -> null
        }
        if (key != null) return key
        return positionNavigationFallbackKey(index, count)
    }

    private fun nativeNavigationTabKeyInTree(view: View?, depth: Int): String? {
        if (view == null || depth > 5) return null
        nativeNavigationTabKeys[view]?.let { return it }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) nativeNavigationTabKeyInTree(view.getChildAt(index), depth + 1)?.let { return it }
        }
        return null
    }

    private fun collectNavigationTabLeaves(view: View, out: MutableList<View>, depth: Int, visited: IntArray) {
        if (depth > 8 || visited[0]++ > 220 || out.size > 24) return
        if (view.visibility == View.VISIBLE && view.width > 0 && view.height > 0) {
            val key = inferNavigationTabKey(view, -1, -1)
            if (key != null || view.isClickable || view.hasOnClickListeners()) out += view
        }
        if (view !is ViewGroup) return
        for (index in 0 until view.childCount) collectNavigationTabLeaves(view.getChildAt(index), out, depth + 1, visited)
    }

    private fun fillNavigationPositionKeys(tabs: List<NavigationTabView>): List<NavigationTabView> {
        if (tabs.isEmpty()) return tabs
        val sorted = tabs.sortedBy { centerXOnScreen(it.view) }
        val fallback = sorted.mapIndexedNotNull { index, tab ->
            if (tab.key == null) tab.view to positionNavigationFallbackKey(index, sorted.size) else null
        }.toMap()
        if (fallback.isEmpty()) return tabs
        return tabs.map { if (it.key == null) it.copy(key = fallback[it.view]) else it }
    }

    private fun positionNavigationFallbackKey(index: Int, count: Int): String? {
        if (index < 0) return null
        val common = when (count) {
            4 -> listOf("home", "search", "reels", "profile")
            5 -> listOf("home", "search", "reels", "direct", "profile")
            6 -> listOf("home", "search", "reels", "create", "direct", "profile")
            7 -> listOf("home", "search", "reels", "create", "direct", "shop", "profile")
            else -> return null
        }
        return common.getOrNull(index)
    }

    private fun topChildUnder(ancestor: ViewGroup, descendant: View): View? {
        var current: View? = descendant
        while (current != null && current !== ancestor) {
            val parent = current.parent as? View ?: return null
            if (parent === ancestor) return current
            current = parent
        }
        return null
    }

    private fun centerXOnScreen(view: View): Int {
        val location = IntArray(2)
        runCatching { view.getLocationOnScreen(location) }
        return location[0] + view.width / 2
    }

    private fun collectShortViewText(view: View, depth: Int): String {
        if (depth > 2) return ""
        val own = buildString {
            append(' ').append(resourceEntryName(view).orEmpty())
            append(' ').append(view.contentDescription?.toString().orEmpty())
            append(' ').append(view.javaClass.name)
            if (view is TextView) append(' ').append(view.text?.toString().orEmpty())
        }
        if (view !is ViewGroup) return own
        return buildString {
            append(own)
            for (i in 0 until view.childCount.coerceAtMost(6)) {
                append(' ').append(collectShortViewText(view.getChildAt(i), depth + 1))
            }
        }
    }

    private fun parseCsv(value: String): Set<String> {
        return value.split(",").mapNotNull(::normalizeNavigationTabKey).toSet()
    }

    private fun parseNavigationOrder(value: String): List<String> {
        val defaults = listOf("home", "search", "reels", "create", "direct", "shop", "profile")
        val parsed = value.split(",").mapNotNull(::normalizeNavigationTabKey).filter { it in defaults }.distinct()
        return (parsed + defaults).distinct()
    }

    private fun normalizeNavigationTabKey(value: String?): String? {
        val key = value?.trim()?.lowercase(Locale.US)?.replace(" ", "_")
        return when (key) {
            "explore" -> "search"
            "clips", "reel" -> "reels"
            "inbox", "dm", "messages" -> "direct"
            "home", "search", "reels", "create", "direct", "shop", "profile" -> key
            else -> null
        }
    }

    private fun isNavigationCustomizationActive(): Boolean {
        if (state.enableNavigationTabCustomization) return true
        val hidden = state.navigationTabHidden.trim()
        val order = state.navigationTabOrder.trim().replace(" ", "").lowercase(Locale.US)
        val default = normalizeNavigationTabKey(state.navigationDefaultTab)
        return hidden.isNotEmpty() ||
            (order.isNotEmpty() && order != "home,search,reels,create,direct,shop,profile") ||
            (default != null && default != "home")
    }

    private fun clickDefaultNavigationTab(activity: Activity) {
        if (!isNavigationCustomizationActive()) return
        val target = normalizeNavigationTabKey(state.navigationDefaultTab)?.takeIf { it != "home" } ?: return
        val hidden = parseCsv(state.navigationTabHidden)
        if (target in hidden) return
        val root = activity.window?.decorView ?: return
        fun clickMatching() {
            findView(root) { view ->
                val name = (resourceEntryName(view).orEmpty() + " " + view.contentDescription?.toString().orEmpty() + " " + (view as? TextView)?.text?.toString().orEmpty()).lowercase()
                name.contains(target) && view.isClickable
            }?.performClick()
        }
        root.post { clickMatching() }
        root.postDelayed({ clickMatching() }, 80L)
        root.postDelayed({ clickMatching() }, 180L)
        root.postDelayed({ clickMatching() }, 450L)
    }

    private fun wireInstagramEntryPoints(activity: Activity) {
        currentActivity = activity
        val root = activity.window?.decorView ?: return
        ensureEntryPointIdsCached(activity)

        var anySearchFound = false
        entrySearchTabId.takeIf { it != 0 }?.let { root.findViewById<View>(it) }?.let {
            processSearchView(activity, it, "search_tab")
            anySearchFound = true
        }
        if (!anySearchFound) {
            entryActionBarEndId.takeIf { it != 0 }?.let { root.findViewById<View>(it) }?.let {
                processSearchView(activity, it, "action_bar_end_action_buttons")
                anySearchFound = true
            }
        }

        entryInboxButtonId.takeIf { it != 0 }?.let { root.findViewById<View>(it) }?.let {
            applyInboxLongPress(activity, it)
        }
        entryDirectTabId.takeIf { it != 0 }?.let { root.findViewById<View>(it) }?.let {
            applyInboxLongPress(activity, it)
        }
        if (!anySearchFound) retrySearchEntryPointWiringAfterLayout(activity, root)
        else entryPointSearchWiringDone[activity] = true
        applyGhostIndicator(activity)
    }

    private fun ensureEntryPointIdsCached(activity: Activity) {
        if (entrySearchTabId != 0 && entryActionBarEndId != 0 &&
            entryInboxButtonId != 0 && entryDirectTabId != 0
        ) return
        runCatching {
            val pkg = activity.packageName
            val resources = activity.resources
            if (entrySearchTabId == 0) entrySearchTabId = resources.getIdentifier("search_tab", "id", pkg)
            if (entryActionBarEndId == 0) entryActionBarEndId = resources.getIdentifier("action_bar_end_action_buttons", "id", pkg)
            if (entryInboxButtonId == 0) entryInboxButtonId = resources.getIdentifier("action_bar_inbox_button", "id", pkg)
            if (entryDirectTabId == 0) entryDirectTabId = resources.getIdentifier("direct_tab", "id", pkg)
        }.onFailure { logError("Failed to cache Instagram entry-point ids", it) }
    }

    private fun retrySearchEntryPointWiringAfterLayout(activity: Activity, root: View) {
        if (entryPointSearchWiringDone[activity] == true) return
        if (!entryPointLayoutRetries.add(activity)) return
        root.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (!root.isAttachedToWindow) {
                    runCatching { root.viewTreeObserver.removeOnGlobalLayoutListener(this) }
                    entryPointLayoutRetries.remove(activity)
                    return
                }
                var found = false
                entrySearchTabId.takeIf { it != 0 }?.let { root.findViewById<View>(it) }?.let {
                    processSearchView(activity, it, "search_tab")
                    found = true
                }
                if (!found) {
                    entryActionBarEndId.takeIf { it != 0 }?.let { root.findViewById<View>(it) }?.let {
                        processSearchView(activity, it, "action_bar_end_action_buttons")
                        found = true
                    }
                }
                if (found) {
                    runCatching { root.viewTreeObserver.removeOnGlobalLayoutListener(this) }
                    entryPointLayoutRetries.remove(activity)
                    entryPointSearchWiringDone[activity] = true
                }
            }
        })
    }

    private fun processEntryPointCandidate(view: View) {
        val activity = currentActivity ?: findActivity(view.context) ?: return
        ensureEntryPointIdsCached(activity)
        when (view.id) {
            entrySearchTabId -> if (entrySearchTabId != 0) {
                processSearchView(activity, view, "search_tab")
                return
            }
            entryActionBarEndId -> if (entryActionBarEndId != 0) {
                processSearchView(activity, view, "action_bar_end_action_buttons")
                return
            }
            entryInboxButtonId -> if (entryInboxButtonId != 0) {
                applyInboxLongPress(activity, view)
                return
            }
            entryDirectTabId -> if (entryDirectTabId != 0) {
                applyInboxLongPress(activity, view)
                return
            }
        }
        if (entrySearchTabId != 0 &&
            entryActionBarEndId != 0 &&
            entryInboxButtonId != 0 &&
            entryDirectTabId != 0
        ) return
        val name = resourceEntryName(view).orEmpty()
        when {
            (entrySearchTabId != 0 && view.id == entrySearchTabId) || name == "search_tab" ->
                processSearchView(activity, view, "search_tab")
            (entryActionBarEndId != 0 && view.id == entryActionBarEndId) || name == "action_bar_end_action_buttons" ->
                processSearchView(activity, view, "action_bar_end_action_buttons")
            (entryInboxButtonId != 0 && view.id == entryInboxButtonId) || name == "action_bar_inbox_button" ->
                applyInboxLongPress(activity, view)
            (entryDirectTabId != 0 && view.id == entryDirectTabId) || name == "direct_tab" ->
                applyInboxLongPress(activity, view)
        }
    }

    private fun processSearchView(activity: Activity, view: View, idName: String) {
        if (idName == "action_bar_end_action_buttons" && view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val description = child.contentDescription?.toString()?.lowercase(Locale.US).orEmpty()
                if (description.contains("search")) applySearchLongPress(activity, child)
            }
        } else {
            applySearchLongPress(activity, view)
        }
    }

    private fun applySearchLongPress(activity: Activity, view: View) {
        if (searchHookedViews.add(view)) {
            logInfo("Hooked Instagram search long-press settings entry")
        }
        view.isLongClickable = true
        view.setOnLongClickListener {
            showPurrfectInstaOptionsDialog(activity)
            vibrate(activity)
            true
        }
    }

    private fun applyInboxLongPress(activity: Activity, view: View) {
        if (inboxHookedViews.add(view)) {
            logInfo("Hooked Instagram inbox/direct long-press ghost toggle")
        }
        view.isLongClickable = true
        view.setOnLongClickListener {
            toggleSelectedGhostOptions(activity)
            vibrate(activity)
            true
        }
    }

    private fun handleEntryPointLongPress(view: View): Boolean {
        val activity = currentActivity ?: findActivity(view.context) ?: return false
        ensureEntryPointIdsCached(activity)
        val name = resourceEntryName(view).orEmpty()
        if (isSearchEntryPointView(view, activity, name)) {
            showPurrfectInstaOptionsDialog(activity)
            vibrate(activity)
            return true
        }
        val isInbox = (entryInboxButtonId != 0 && view.id == entryInboxButtonId) ||
            (entryDirectTabId != 0 && view.id == entryDirectTabId) ||
            name == "action_bar_inbox_button" ||
            name == "direct_tab"
        if (isInbox) {
            toggleSelectedGhostOptions(activity)
            vibrate(activity)
            return true
        }
        return false
    }

    private fun isSearchEntryPointView(view: View, activity: Activity, viewName: String = resourceEntryName(view).orEmpty()): Boolean {
        ensureEntryPointIdsCached(activity)
        if ((entrySearchTabId != 0 && view.id == entrySearchTabId) || viewName == "search_tab") return true

        val description = view.contentDescription?.toString()?.lowercase(Locale.US).orEmpty()
        val isSearchish = description.contains("search") || viewName.contains("search")
        if ((entryActionBarEndId != 0 && view.id == entryActionBarEndId) || viewName == "action_bar_end_action_buttons") {
            return isSearchish || view is ViewGroup
        }

        var parent = view.parent as? View
        repeat(8) {
            val node = parent ?: return@repeat
            val name = resourceEntryName(node).orEmpty()
            if ((entrySearchTabId != 0 && node.id == entrySearchTabId) || name == "search_tab") return true
            val isActionBarEnd = (entryActionBarEndId != 0 && node.id == entryActionBarEndId) ||
                name == "action_bar_end_action_buttons"
            if (isActionBarEnd && isSearchish) return true
            parent = node.parent as? View
        }
        return false
    }

    private fun showPurrfectInstaOptionsDialog(activity: Activity) {
        currentSettingsDialog?.takeIf { it.isShowing }?.dismiss()
        currentSettingsDialog = null

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(28, 28, 30))
                cornerRadii = floatArrayOf(40f, 40f, 40f, 40f, 0f, 0f, 0f, 0f)
            }
        }
        layout.addView(createDragHandle(activity))
        layout.addView(createDialogTitle(activity, "PurrfectInsta"))
        layout.addView(createDivider(activity))
        hostFeatureSections().forEach { section ->
            layout.addView(createActionRow(activity, section.title) {
                showFeatureSectionDialog(activity, section)
            })
        }
        layout.addView(createDivider(activity))
        layout.addView(createActionRow(activity, "Activity History") {
            currentSettingsDialog?.dismiss()
            InstagramActivityHistoryDialog.show(activity)
        })
        layout.addView(createActionRow(activity, "Backup & Restore") {
            showBackupRestoreSection(activity)
        })
        layout.addView(createActionRow(activity, "About") {
            showAboutSection(activity)
        })
        layout.addView(createActionRow(activity, "Restart App") {
            showRestartSection(activity)
        })
        layout.addView(createActionRow(activity, "Clear Hooks Cache") {
            showClearHooksCacheSection(activity)
        })
        layout.addView(createActionRow(activity, "Close", Color.rgb(255, 69, 58)) {
            currentSettingsDialog?.dismiss()
        })

        val scroll = ScrollView(activity).apply { addView(layout) }
        currentSettingsDialog = createBottomDialog(activity, scroll).also { it.show() }
    }

    private fun showHostActionSection(activity: Activity, title: String, buildContent: LinearLayout.() -> Unit) {
        currentSettingsDialog?.dismiss()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(28, 28, 30))
                cornerRadii = floatArrayOf(40f, 40f, 40f, 40f, 0f, 0f, 0f, 0f)
            }
        }
        content.addView(createDragHandle(activity))
        content.addView(createActionRow(activity, "<  $title", Color.rgb(10, 132, 255)) {
            showPurrfectInstaOptionsDialog(activity)
        })
        content.addView(createDivider(activity))
        content.buildContent()
        content.addView(createDivider(activity))
        content.addView(createActionRow(activity, "Back", Color.rgb(10, 132, 255)) {
            showPurrfectInstaOptionsDialog(activity)
        })
        val scroll = ScrollView(activity).apply { addView(content) }
        currentSettingsDialog = createBottomDialog(activity, scroll).also { it.show() }
    }

    private fun showBackupRestoreSection(activity: Activity) {
        showHostActionSection(activity, "Backup & Restore") {
            addView(createActionRow(activity, "Backup Settings", Color.rgb(48, 209, 88)) {
                exportInstagramSettingsBackup(activity)
            })
            addView(createActionRow(activity, "Restore Settings", Color.rgb(10, 132, 255)) {
                importInstagramSettingsBackup(activity)
            })
        }
    }

    private fun showAboutSection(activity: Activity) {
        showHostActionSection(activity, "About") {
            addView(createDialogTitle(activity, "InstaEclipse \uD83C\uDF18"))
            addView(TextView(activity).apply {
                text = "Created by @reso7200"
                setTextColor(Color.rgb(142, 142, 147))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(24), 0, dp(24), dp(14))
            })
            addView(createActionRow(activity, "GitHub", Color.rgb(10, 132, 255)) {
                openUrl(activity, "https://github.com/ReSo7200/InstaEclipse")
            })
            addView(createActionRow(activity, "Telegram", Color.rgb(41, 182, 246)) {
                openUrl(activity, "https://t.me/InstaEclipse")
            })
        }
    }

    private fun showRestartSection(activity: Activity) {
        showHostActionSection(activity, "Restart App") {
            addView(TextView(activity).apply {
                text = "Clear app cache and restart?"
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(18), dp(24), dp(18))
            })
            addView(createActionRow(activity, "Restart Now", Color.rgb(255, 69, 58)) {
                restartInstagram(activity)
            })
        }
    }

    private fun showClearHooksCacheSection(activity: Activity) {
        showHostActionSection(activity, "Clear Hooks Cache") {
            addView(TextView(activity).apply {
                text = "This will force InstaEclipse to re-scan Instagram on next launch. The app will restart."
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(18), dp(24), dp(18))
            })
            addView(createActionRow(activity, "Clear Cache & Restart", Color.rgb(255, 159, 10)) {
                InstagramDexKitCache.clearCache()
                restartInstagram(activity)
            })
        }
    }

    private fun showFeatureSectionDialog(activity: Activity, section: HostFeatureSection) {
        currentSettingsDialog?.dismiss()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(28, 28, 30))
                cornerRadii = floatArrayOf(40f, 40f, 40f, 40f, 0f, 0f, 0f, 0f)
            }
        }
        content.addView(createDragHandle(activity))
        content.addView(createActionRow(activity, "<  ${section.title}", Color.rgb(10, 132, 255)) {
            showPurrfectInstaOptionsDialog(activity)
        })
        content.addView(createDivider(activity))
        section.features.forEach { feature ->
            when (feature) {
                is HostFeature.BooleanFeature -> content.addView(createSwitchRow(activity, feature))
                is HostFeature.StringFeature -> content.addView(createStringRow(activity, feature))
                is HostFeature.ActionFeature -> content.addView(
                    createActionRow(activity, feature.label, feature.color) {
                        feature.action(activity)
                    }
                )
            }
        }
        content.addView(createDivider(activity))
        content.addView(createActionRow(activity, "Back", Color.rgb(10, 132, 255)) {
            showPurrfectInstaOptionsDialog(activity)
        })
        val scroll = ScrollView(activity).apply { addView(content) }
        currentSettingsDialog = createBottomDialog(activity, scroll).also { it.show() }
    }

    private fun createSwitchRow(activity: Activity, feature: HostFeature.BooleanFeature): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(8), dp(20), dp(8))
            isClickable = true
        }
        val label = TextView(activity).apply {
            text = feature.label
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        val toggle = Switch(activity).apply {
            isChecked = feature.value(state)
            thumbTintList = switchThumbTint()
            trackTintList = switchTrackTint()
        }
        row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(toggle)
        row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
        toggle.setOnCheckedChangeListener { _, checked ->
            feature.update?.invoke(activity, checked) ?: persistBooleanFeatureUpdate(feature.key, checked)
        }
        return row
    }

    private fun createStringRow(activity: Activity, feature: HostFeature.StringFeature): View {
        if (feature.key == "dmMarkSeenControlMode") {
            return createActionRow(activity, "${feature.label}: ${dmMarkSeenModeLabel(feature.value(state))}") {
                val values = arrayOf("eye", "hold_gallery")
                val labels = arrayOf<CharSequence>("Eye icon", "Tap and hold gallery icon")
                val currentIndex = values.indexOf(feature.value(state)).takeIf { it >= 0 } ?: 0
                AlertDialog.Builder(activity)
                    .setTitle(feature.label)
                    .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                        persistStringFeatureUpdate(feature.key, values[which])
                        dialog.dismiss()
                        findHostFeatureSectionContaining(feature.key)
                            ?.let { showFeatureSectionDialog(activity, it) }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        return createActionRow(activity, "${feature.label}: ${feature.value(state).ifBlank { "not set" }}") {
            val input = EditText(activity).apply {
                setText(feature.value(state))
                setSingleLine(false)
                minLines = 1
                setTextColor(Color.WHITE)
            }
            AlertDialog.Builder(activity)
                .setTitle(feature.label)
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    persistStringFeatureUpdate(feature.key, input.text?.toString().orEmpty())
                    findHostFeatureSectionContaining(feature.key)
                        ?.let { showFeatureSectionDialog(activity, it) }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun dmMarkSeenModeLabel(mode: String): String {
        return if (mode == "hold_gallery") "Tap and hold gallery icon" else "Eye icon"
    }

    private fun createDialogTitle(context: Context, titleText: String): View {
        return TextView(context).apply {
            text = titleText
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(6), dp(24), dp(14))
        }
    }

    private fun createDragHandle(context: Context): View {
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(10), 0, dp(6))
            addView(View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.rgb(72, 72, 74))
                    cornerRadius = 4f
                }
            }, LinearLayout.LayoutParams(dp(72), dp(4)))
        }
    }

    private fun createDivider(context: Context): View {
        return View(context).apply {
            setBackgroundColor(Color.rgb(58, 58, 60))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                setMargins(0, dp(8), 0, dp(8))
            }
        }
    }

    private fun createActionRow(
        context: Context,
        labelText: String,
        color: Int = Color.WHITE,
        onClick: () -> Unit
    ): View {
        return TextView(context).apply {
            text = labelText
            setTextColor(color)
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(15), dp(24), dp(15))
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    private fun createBottomDialog(context: Context, view: View): AlertDialog {
        return AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(true)
            .create()
            .apply {
                setOnShowListener {
                    window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                    window?.setGravity(Gravity.BOTTOM)
                    window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
            }
    }

    private fun switchThumbTint() = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(Color.rgb(10, 132, 255), Color.WHITE)
    )

    private fun switchTrackTint() = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(Color.rgb(28, 76, 120), Color.rgb(108, 108, 112))
    )

    private fun applyGhostIndicator(activity: Activity) {
        val root = activity.window?.decorView ?: return
        val inbox = activity.resources.getIdentifier("action_bar_inbox_button", "id", activity.packageName)
            .takeIf { it != 0 }?.let { root.findViewById<View>(it) }
        val direct = activity.resources.getIdentifier("direct_tab", "id", activity.packageName)
            .takeIf { it != 0 }?.let { root.findViewById<View>(it) }
        val image = findFirstImageView(inbox ?: direct ?: return) ?: return
        if (isGhostQuickToggleActive(state)) {
            image.setColorFilter(Color.rgb(255, 215, 0), PorterDuff.Mode.SRC_ATOP)
        } else {
            image.clearColorFilter()
        }
    }

    private fun applyDirectGhostSeenControls(view: View) {
        if (!state.isGhostSeen) return
        val name = resourceEntryName(view).orEmpty()
        when (name) {
            "row_thread_composer_button_gallery" -> {
                if (state.dmMarkSeenControlMode == "hold_gallery") {
                    view.isLongClickable = true
                    view.setOnLongClickListener {
                        triggerDirectSeen(view, 200, 300L, "Seen sent", markAfterReply = true)
                        true
                    }
                }
            }
            "row_thread_composer_buttons_container" -> {
                if (state.dmMarkSeenControlMode != "hold_gallery") {
                    val parent = view.parent as? ViewGroup ?: return
                    injectDirectSeenButton(parent, view)
                }
            }
            "seen_state_text" -> {
                val textView = view as? TextView ?: return
                if (isDirectHeaderWithCallsOrBlend(view.rootView)) return
                if (!directSeenControls.add(textView)) return
                textView.setTextColor(Color.CYAN)
                val currentText = textView.text?.toString().orEmpty()
                if (!currentText.contains("\uD83D\uDC7B")) textView.text = "$currentText \uD83D\uDC7B"
                textView.setOnClickListener { triggerDirectSeen(textView, 300, 400L, "Channel seen sent", markAfterReply = false) }
            }
        }
    }

    private fun applyDmAnyFileUploadButton(view: View) {
        if (!state.enableDmAnyFileUpload) return
        cacheComposerIds(view)
        if (isComposerUploadAnchor(view)) ensureUploadButton(view)
    }

    private fun handleDirectSeenLongPress(view: View): Boolean {
        if (!state.isGhostSeen || state.dmMarkSeenControlMode != "hold_gallery") return false
        if (resourceEntryName(view) != "row_thread_composer_button_gallery") return false
        triggerDirectSeen(view, 200, 300L, "Seen sent", markAfterReply = true)
        return true
    }

    private fun handleUploadLongPress(view: View): Boolean {
        if (!state.enableDmAnyFileUpload || view.tag != UPLOAD_BUTTON_TAG) return false
        launchAnyFilePicker(view.context)
        return true
    }

    private fun cacheComposerIds(view: View) {
        if (composerOverflowButtonId != 0 &&
            composerOverflowLeftButtonId != 0 &&
            composerButtonsContainerId != 0 &&
            composerGalleryButtonId != 0 &&
            composerVoiceButtonId != 0 &&
            composerStickerButtonId != 0
        ) return
        runCatching {
            val resources = view.resources
            val pkg = view.context.packageName
            composerOverflowButtonId = resources.getIdentifier("row_thread_composer_button_overflow", "id", pkg)
            composerOverflowLeftButtonId = resources.getIdentifier("row_thread_composer_button_overflow_left_aligned", "id", pkg)
            composerButtonsContainerId = resources.getIdentifier("row_thread_composer_buttons_container", "id", pkg)
            composerGalleryButtonId = resources.getIdentifier("row_thread_composer_button_gallery", "id", pkg)
            composerVoiceButtonId = resources.getIdentifier("row_thread_composer_voice", "id", pkg)
            composerStickerButtonId = resources.getIdentifier("row_thread_composer_button_sticker", "id", pkg)
            if (!loggedComposerIds) {
                loggedComposerIds = true
                logInfo(
                    "DM any-file composer ids container=$composerButtonsContainerId gallery=$composerGalleryButtonId " +
                        "voice=$composerVoiceButtonId sticker=$composerStickerButtonId " +
                        "overflow=$composerOverflowButtonId,$composerOverflowLeftButtonId"
                )
            }
        }
    }

    private fun isComposerUploadAnchor(view: View): Boolean {
        val id = view.id
        return (composerOverflowButtonId != 0 && id == composerOverflowButtonId) ||
            (composerOverflowLeftButtonId != 0 && id == composerOverflowLeftButtonId) ||
            (composerButtonsContainerId != 0 && id == composerButtonsContainerId) ||
            (composerGalleryButtonId != 0 && id == composerGalleryButtonId) ||
            (composerVoiceButtonId != 0 && id == composerVoiceButtonId) ||
            (composerStickerButtonId != 0 && id == composerStickerButtonId)
    }

    private fun ensureUploadButton(anchor: View) {
        val activity = findActivity(anchor.context) ?: return
        val root = activity.window?.decorView ?: return
        val overlayParent = activity.findViewById<View>(android.R.id.content) as? FrameLayout ?: return
        removeNestedUploadButtons(root, overlayParent)
        val reference = firstExisting(
            root,
            composerVoiceButtonId,
            composerGalleryButtonId,
            composerOverflowButtonId,
            composerOverflowLeftButtonId,
            composerStickerButtonId
        )?.takeIf { it.isShown } ?: return

        val upload = findUploadButton(overlayParent) ?: ImageView(overlayParent.context).apply {
            tag = UPLOAD_BUTTON_TAG
            setImageDrawable(UploadGlyphDrawable())
            scaleType = ImageView.ScaleType.CENTER
            adjustViewBounds = false
            contentDescription = "Pick file"
            isClickable = true
            isFocusable = true
            setOnClickListener { launchAnyFilePicker(it.context) }
            setOnLongClickListener {
                launchAnyFilePicker(it.context)
                true
            }
            overlayParent.addView(this)
        }
        applyReferenceTint(upload, reference)
        positionUploadOverlay(overlayParent, upload, reference)
        upload.visibility = View.VISIBLE
        upload.bringToFront()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) upload.elevation = dp(12).toFloat()
    }

    private fun findUploadButton(parent: ViewGroup): ImageView? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.tag == UPLOAD_BUTTON_TAG && child is ImageView) return child
        }
        return null
    }

    private fun removeNestedUploadButtons(view: View, keepParent: ViewGroup) {
        val group = view as? ViewGroup ?: return
        for (i in group.childCount - 1 downTo 0) {
            val child = group.getChildAt(i)
            if (child.tag == UPLOAD_BUTTON_TAG && group != keepParent) {
                group.removeViewAt(i)
                continue
            }
            removeNestedUploadButtons(child, keepParent)
        }
    }

    private fun firstExisting(root: View, vararg ids: Int): View? {
        ids.forEach { id ->
            if (id != 0) root.findViewById<View>(id)?.let { return it }
        }
        return null
    }

    private fun applyReferenceTint(upload: ImageView, reference: View) {
        if (reference is ImageView) {
            runCatching { upload.setColorFilter(reference.colorFilter) }
            upload.alpha = reference.alpha
        }
    }

    private fun positionUploadOverlay(parent: FrameLayout, upload: ImageView, reference: View, attempt: Int = 0) {
        val refWidth = reference.width
        val refHeight = reference.height
        if (refWidth <= 0 || refHeight <= 0 || parent.width <= 0) {
            if (attempt < 3) reference.post { positionUploadOverlay(parent, upload, reference, attempt + 1) }
            return
        }
        val size = dp(38).coerceAtLeast(maxOf(refWidth, refHeight).coerceAtMost(dp(54)))
        val padding = dp(6).coerceAtLeast(size / 7)
        upload.setPadding(padding, padding, padding, padding)

        val parentLoc = IntArray(2)
        val refLoc = IntArray(2)
        parent.getLocationOnScreen(parentLoc)
        reference.getLocationOnScreen(refLoc)

        val gap = dp(8)
        var left = refLoc[0] - parentLoc[0] - size - gap
        val top = refLoc[1] - parentLoc[1] + maxOf(0, (refHeight - size) / 2)
        if (left < gap) left = refLoc[0] - parentLoc[0] + refWidth + gap
        left = left.coerceIn(gap, (parent.width - size - gap).coerceAtLeast(gap))

        upload.layoutParams = FrameLayout.LayoutParams(size, size).apply {
            leftMargin = left
            topMargin = top.coerceAtLeast(0)
        }
    }

    private fun launchAnyFilePicker(context: Context?) {
        val activity = findActivity(context) ?: return
        runCatching {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, broadMimeTypes)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            activity.startActivityForResult(intent, REQUEST_PICK_ANY_FILE)
        }.onFailure { logError("DM any-file picker launch failed", it) }
    }

    private fun handlePickedAnyFile(activity: Activity, resultCode: Int, data: Intent?) {
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) return
        runCatching {
            activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val mime = activity.contentResolver.getType(uri)?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
        runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage(activity.packageName)
            }
            if (send.resolveActivity(activity.packageManager) == null) send.setPackage(null)
            activity.startActivity(send)
            Toast.makeText(activity, "Opening Instagram file share...", Toast.LENGTH_SHORT).show()
        }.onFailure { logError("DM any-file share handoff failed", it) }
    }

    private fun injectDirectSeenButton(parent: ViewGroup, anchor: View) {
        if (parent.findViewWithTag<View>("ie_ghost_seen_btn") != null) return
        if (!directSeenControls.add(parent)) return
        val button = ImageButton(parent.context).apply {
            tag = "ie_ghost_seen_btn"
            val eye = loadModuleDrawable("ic_eye")
            if (eye != null) {
                setImageDrawable(eye)
            } else {
                setImageResource(android.R.drawable.ic_menu_view)
                setColorFilter(Color.WHITE)
            }
            background = null
            setOnClickListener { triggerDirectSeen(anchor, 200, 300L, "Seen sent", markAfterReply = true) }
            contentDescription = "Mark seen"
        }
        val params: ViewGroup.LayoutParams = when (parent) {
            is FrameLayout -> FrameLayout.LayoutParams(dp(35), dp(35), Gravity.CENTER_VERTICAL or Gravity.START).apply {
                marginStart = dp(5)
                topMargin = 25
            }
            is LinearLayout -> LinearLayout.LayoutParams(dp(35), dp(35)).apply {
                marginStart = dp(5)
                topMargin = 25
            }
            else -> ViewGroup.LayoutParams(dp(35), dp(35))
        }
        parent.post {
            runCatching {
                parent.addView(button, parent.childCount.coerceAtMost(3), params)
            }.onFailure {
                runCatching { parent.addView(button, params) }
            }
        }
    }

    private fun loadModuleDrawable(name: String): Drawable? {
        return runCatching {
            val moduleContext = androidContext.createPackageContext(
                Constants.MODULE_PACKAGE_NAME,
                Context.CONTEXT_IGNORE_SECURITY
            )
            val id = moduleContext.resources.getIdentifier(name, "drawable", Constants.MODULE_PACKAGE_NAME)
            if (id == 0) return@runCatching null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                moduleContext.resources.getDrawable(id, moduleContext.theme)
            } else {
                @Suppress("DEPRECATION")
                moduleContext.resources.getDrawable(id)
            }
        }.getOrNull()
    }

    private fun isDirectHeaderWithCallsOrBlend(root: View?): Boolean {
        root ?: return false
        val context = root.context ?: return false
        val headerId = context.resources.getIdentifier("header_right_buttons", "id", context.packageName)
        val header = headerId.takeIf { it != 0 }?.let { root.findViewById<View>(it) } as? ViewGroup ?: return false
        for (i in 0 until header.childCount) {
            val desc = header.getChildAt(i).contentDescription?.toString()?.lowercase(Locale.US).orEmpty()
            if (desc.contains("audio call") || desc.contains("video call") || desc.contains("blend")) return true
        }
        return false
    }

    private fun triggerDirectSeen(view: View, scrollDelta: Int, delayMs: Long, toast: String, markAfterReply: Boolean) {
        val root = view.rootView ?: return
        val context = view.context ?: androidContext
        val messageListId = context.resources.getIdentifier("message_list", "id", context.packageName)
        val messageList = messageListId.takeIf { it != 0 }?.let { root.findViewById<View>(it) } as? ViewGroup
        val previous = state
        if (markAfterReply) markDmSeenAfterReply(root, view)
        messageList?.scrollBy(0, 100_000)
        InstagramFeatureStateStore.update(previous.copy(isGhostSeen = false, source = "manual-dm-seen"))
        messageList?.scrollBy(0, -scrollDelta)
        view.postDelayed({
            messageList?.scrollBy(0, scrollDelta)
            val current = state
            if (!current.isGhostSeen) {
                InstagramFeatureStateStore.update(current.copy(isGhostSeen = previous.isGhostSeen, source = previous.source))
            }
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
            refreshVisibleRoots("manual-dm-seen")
        }, delayMs)
    }

    private fun applyCaptureOverlay(activity: Activity) {
        if (!state.captureUiElementIdsEnabled) {
            captureOverlayStates.remove(activity)?.remove()
            return
        }
        val decor = activity.window?.decorView as? ViewGroup ?: return
        val overlayState = captureOverlayStates.getOrPut(activity) { CaptureOverlayState() }
        val button = overlayState.button ?: createCaptureButton(activity, decor, overlayState).also {
            overlayState.button = it
        }
        if (button.parent != decor) {
            removeFromParent(button)
            decor.addView(button, createInitialCaptureButtonLayout(activity))
        }
    }

    private fun createCaptureButton(activity: Activity, decor: ViewGroup, overlayState: CaptureOverlayState): TextView {
        return TextView(activity).apply {
            text = "ID"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) elevation = dp(12).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE6000000.toInt())
                setStroke(dp(2), Color.WHITE)
            }
            setOnTouchListener(object : View.OnTouchListener {
                private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
                private var downRawX = 0f
                private var downRawY = 0f
                private var downLeft = 0
                private var downTop = 0
                private var dragging = false

                override fun onTouch(view: View, event: MotionEvent): Boolean {
                    val currentDecor = view.parent as? ViewGroup ?: decor
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downRawX = event.rawX
                            downRawY = event.rawY
                            val params = frameParams(view)
                            downLeft = params.leftMargin
                            downTop = params.topMargin
                            dragging = false
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - downRawX
                            val dy = event.rawY - downRawY
                            if (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) dragging = true
                            if (dragging) moveCaptureButton(activity, currentDecor, view, downLeft + dx.roundToInt(), downTop + dy.roundToInt())
                            return true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (!dragging && event.actionMasked == MotionEvent.ACTION_UP) {
                                startCapturePicker(activity, currentDecor, overlayState)
                            }
                            return true
                        }
                    }
                    return true
                }
            })
        }
    }

    private fun createInitialCaptureButtonLayout(activity: Activity): FrameLayout.LayoutParams {
        val size = dp(52)
        val margin = dp(16)
        val width = activity.resources.displayMetrics.widthPixels
        return FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.START).apply {
            leftMargin = (width - size - margin).coerceAtLeast(margin)
            topMargin = dp(160)
        }
    }

    private fun frameParams(view: View): FrameLayout.LayoutParams {
        val raw = view.layoutParams
        if (raw is FrameLayout.LayoutParams) {
            raw.gravity = Gravity.TOP or Gravity.START
            return raw
        }
        return FrameLayout.LayoutParams(raw?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT, raw?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            view.layoutParams = this
        }
    }

    private fun moveCaptureButton(activity: Activity, decor: ViewGroup, button: View, left: Int, top: Int) {
        val params = frameParams(button)
        val width = decor.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        val height = decor.height.takeIf { it > 0 } ?: activity.resources.displayMetrics.heightPixels
        val buttonWidth = button.width.takeIf { it > 0 } ?: params.width
        val buttonHeight = button.height.takeIf { it > 0 } ?: params.height
        params.leftMargin = left.coerceIn(0, (width - buttonWidth).coerceAtLeast(0))
        params.topMargin = top.coerceIn(0, (height - buttonHeight).coerceAtLeast(0))
        button.layoutParams = params
    }

    private fun startCapturePicker(activity: Activity, decor: ViewGroup, overlayState: CaptureOverlayState) {
        removeFromParent(overlayState.pickerOverlay)
        val overlay = FrameLayout(activity).apply {
            isClickable = true
            isFocusable = true
            setBackgroundColor(0x330A84FF)
        }
        val label = TextView(activity).apply {
            text = "Tap an element"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply {
                setColor(0xE6000000.toInt())
                cornerRadius = dp(20).toFloat()
            }
        }
        overlay.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(28)
        })
        overlay.setOnTouchListener { _, event ->
            if (event.actionMasked != MotionEvent.ACTION_UP) return@setOnTouchListener true
            removeFromParent(overlay)
            overlayState.pickerOverlay = null
            val target = findBestCaptureViewAt(decor, event.rawX, event.rawY, overlayState)
            showCapturedView(activity, decor, target)
            true
        }
        overlayState.pickerOverlay = overlay
        decor.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun findBestCaptureViewAt(root: View, rawX: Float, rawY: Float, overlayState: CaptureOverlayState): View? {
        val candidates = mutableListOf<CaptureCandidate>()
        fun collect(view: View, depth: Int) {
            if (view == root) {
                if (view is ViewGroup) for (i in view.childCount - 1 downTo 0) collect(view.getChildAt(i), depth + 1)
                return
            }
            if (view == overlayState.button || view == overlayState.pickerOverlay) return
            if (view.visibility != View.VISIBLE || view.alpha <= 0.01f) return
            if (!isPointInside(view, rawX, rawY)) return
            if (view is ViewGroup) for (i in view.childCount - 1 downTo 0) collect(view.getChildAt(i), depth + 1)
            if (view.width > 0 && view.height > 0 && !view.javaClass.name.contains("Guideline") && !view.javaClass.name.contains("ViewStub")) {
                candidates += CaptureCandidate(view, depth)
            }
        }
        collect(root, 0)
        return candidates.minWithOrNull(
            compareBy<CaptureCandidate> { it.area }
                .thenBy { it.isViewGroup }
                .thenByDescending { it.depth }
                .thenByDescending { it.hasId }
                .thenByDescending { it.interactive }
        )?.view
    }

    private fun isPointInside(view: View, rawX: Float, rawY: Float): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return view.width > 0 && view.height > 0 &&
            rawX >= location[0] && rawX <= location[0] + view.width &&
            rawY >= location[1] && rawY <= location[1] + view.height
    }

    private fun showCapturedView(activity: Activity, root: View, target: View?) {
        if (target == null) {
            Toast.makeText(activity, "No UI element found here.", Toast.LENGTH_SHORT).show()
            return
        }
        val targetId = resourceEntryName(target)
        val namedTarget = targetId?.let { target } ?: findNearestNamedAncestor(target, root)
        val nearestId = namedTarget?.let { resourceEntryName(it) }
        val selector = if (targetId == null) buildSelector(target, root).orEmpty() else ""
        val copyValue = targetId ?: selector.ifBlank { nearestId ?: describeViewPath(target, root) }
        val hideValue = targetId ?: selector
        val message = buildString {
            append("Selected: ").append(target.javaClass.name).append('\n')
            append("ID: ").append(targetId?.let { "#$it" } ?: "NO_ID").append('\n')
            if (targetId == null && nearestId != null) {
                append("Nearest ID: #").append(nearestId).append('\n')
                append("Nearest view: ").append(namedTarget?.javaClass?.name).append('\n')
            }
            if (targetId == null && selector.isNotBlank()) {
                append("Exact selector: ").append(selectorDisplayName(selector)).append('\n')
            }
            append('\n').append("Path: ").append(describeViewPath(target, root))
        }
        val builder = AlertDialog.Builder(activity)
            .setTitle("UI Element ID")
            .setMessage(message)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Instagram UI element ID", copyValue))
                Toast.makeText(activity, "Copied: $copyValue", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
        if (hideValue.isNotBlank()) {
            builder.setNeutralButton("Hide") { _, _ -> hideCapturedElement(activity, hideValue) }
        }
        builder.show()
    }

    private fun hideCapturedElement(activity: Activity, idOrSelector: String) {
        if (idOrSelector.startsWith("selector:v1|")) {
            val clean = idOrSelector.trim()
            if (clean.isBlank()) return
            val old = state
            val next = (old.hiddenUiElementSelectors.lineSequence().map { it.trim() }.filter { it.isNotEmpty() } + clean)
                .distinct()
                .joinToString("\n")
            InstagramFeatureStateStore.update(old.copy(hiddenUiElementSelectors = next, source = "capture-ui"))
            persistStringFeatureUpdate("hiddenUiElementSelectors", next)
            refreshActivity(activity, "capture-ui")
            Toast.makeText(activity, "Hidden exact element.", Toast.LENGTH_SHORT).show()
            return
        }
        val clean = InstagramFeatureState.normalizeUiElementId(idOrSelector)
        if (clean.isBlank()) {
            Toast.makeText(activity, "This element has no resource ID.", Toast.LENGTH_SHORT).show()
            return
        }
        val old = state
        val next = (old.hiddenUiElementIds.lineSequence().map { it.trim() }.filter { it.isNotEmpty() } + clean)
            .distinct()
            .joinToString("\n")
        InstagramFeatureStateStore.update(old.copy(hiddenUiElementIds = next, source = "capture-ui"))
        persistStringFeatureUpdate("hiddenUiElementIds", next)
        refreshActivity(activity, "capture-ui")
        Toast.makeText(activity, "Hidden: $clean", Toast.LENGTH_SHORT).show()
    }

    private fun removeFromParent(view: View?) {
        val parent = view?.parent as? ViewGroup ?: return
        parent.removeView(view)
    }

    private fun findFirstImageView(view: View): ImageView? {
        if (view is ImageView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findFirstImageView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun toggleSelectedGhostOptions(activity: Activity) {
        val old = state
        val selectedStates = listOf(
            old.quickToggleSeen to old.isGhostSeen,
            old.quickToggleTyping to old.isGhostTyping,
            old.quickToggleScreenshot to old.isGhostScreenshot,
            old.quickToggleViewOnce to old.isGhostViewOnce,
            old.quickToggleStory to old.isGhostStory,
            old.quickToggleLive to old.isGhostLive,
            old.quickToggleEphemeral to old.keepEphemeralMessages,
            old.quickToggleUnsend to old.keepUnsentMessages,
            old.quickToggleReplays to old.enableUnlimitedReplays,
            old.quickTogglePermanentView to old.permanentViewMode,
            old.quickToggleAllowScreenshots to old.allowScreenshots
        )
        if (selectedStates.none { it.first }) {
            Toast.makeText(activity, "No Ghost Mode options selected", Toast.LENGTH_SHORT).show()
            applyGhostIndicator(activity)
            return
        }
        val newValue = !selectedStates.any { (selected, active) -> selected && active }
        val changes = linkedMapOf<String, Boolean>()
        fun change(enabled: Boolean, key: String) {
            if (enabled) changes[key] = newValue
        }
        change(old.quickToggleSeen, "isGhostSeen")
        change(old.quickToggleTyping, "isGhostTyping")
        change(old.quickToggleScreenshot, "isGhostScreenshot")
        change(old.quickToggleViewOnce, "isGhostViewOnce")
        change(old.quickToggleStory, "isGhostStory")
        change(old.quickToggleLive, "isGhostLive")
        change(old.quickToggleEphemeral, "keepEphemeralMessages")
        change(old.quickToggleUnsend, "keepUnsentMessages")
        change(old.quickToggleReplays, "enableUnlimitedReplays")
        change(old.quickTogglePermanentView, "permanentViewMode")
        change(old.quickToggleAllowScreenshots, "allowScreenshots")

        val next = old.copy(
            isGhostSeen = changes["isGhostSeen"] ?: old.isGhostSeen,
            isGhostTyping = changes["isGhostTyping"] ?: old.isGhostTyping,
            isGhostScreenshot = changes["isGhostScreenshot"] ?: old.isGhostScreenshot,
            isGhostViewOnce = changes["isGhostViewOnce"] ?: old.isGhostViewOnce,
            isGhostStory = changes["isGhostStory"] ?: old.isGhostStory,
            isGhostLive = changes["isGhostLive"] ?: old.isGhostLive,
            keepEphemeralMessages = changes["keepEphemeralMessages"] ?: old.keepEphemeralMessages,
            keepUnsentMessages = changes["keepUnsentMessages"] ?: old.keepUnsentMessages,
            enableUnlimitedReplays = changes["enableUnlimitedReplays"] ?: old.enableUnlimitedReplays,
            permanentViewMode = changes["permanentViewMode"] ?: old.permanentViewMode,
            allowScreenshots = changes["allowScreenshots"] ?: old.allowScreenshots,
            source = "inbox-long-press"
        )
        InstagramFeatureStateStore.update(next)
        changes.forEach { (key, value) -> persistBooleanFeatureUpdate(key, value) }
        refreshActiveHooks("inbox-long-press")
        applyGhostIndicator(activity)
        Toast.makeText(activity, if (newValue) "Ghost Mode Enabled" else "Ghost Mode Disabled", Toast.LENGTH_SHORT).show()
    }

    private val hostQuickToggleMasterKeys = listOf(
        "quickToggleSeen", "quickToggleTyping", "quickToggleScreenshot", "quickToggleViewOnce",
        "quickToggleStory", "quickToggleLive", "quickToggleEphemeral", "quickToggleUnsend",
        "quickToggleReplays", "quickTogglePermanentView", "quickToggleAllowScreenshots"
    )

    private val hostGhostMasterKeys = listOf(
        "isGhostSeen", "isGhostTyping", "isGhostStory", "isGhostLive", "hideVoiceMessageSeen",
        "allowScreenshots", "isGhostScreenshot", "isGhostViewOnce", "enableUnlimitedReplays",
        "permanentViewMode", "keepEphemeralMessages", "keepUnsentMessages",
        "markTextsSeenAfterReply", "storyInteractionSendsSeen"
    )
    private val hostDistractionMasterKeys = listOf(
        "disableStories", "disableFeed", "disableReels", "disableReelsExceptDM",
        "disableExplore", "disableComments"
    )
    private val hostMiscMasterKeys = listOf(
        "disableStoryFlipping", "disableVideoAutoPlay", "feedVideosStartWithSound",
        "storiesStartWithSound", "disableRepost", "showFollowerToast", "showFeatureToasts",
        "enableStoryMentions", "disableDiscoverPeople", "enableCopyComment", "enableCopyBio",
        "disableDoubleTapLike", "enableMonetTheme", "customEmojiFontEnabled",
        "enableShareSheetEmojiShortcuts", "enableActivityHistory", "enableNavigationTabCustomization",
        "enableConfirmRefresh", "enableNotesLocationSpoof", "enableHideChats",
        "stripShareTrackingParameters", "doNotSaveRecentSearches", "enableCustomDateFormat",
        "openLinksExternally", "replaceShareLinkDomain", "enableTeenAppIcons",
        "enableStoryTrayLongPressActions", "blockDmReelNotifications", "blockDmPostNotifications"
    )
    private val hostDownloaderMasterKeys = listOf(
        "enablePostDownload", "enableStoryDownload", "enableReelDownload", "enableProfileDownload",
        "enableDmContextMenuOptions", "enableReelThumbnailDownload",
        "enableStoryMarkSeenButton", "enableStoryRepostButton", "enableHighQualityStoryUpload",
        "enableHighQualityDmUpload", "enableGifCommentDownload", "enableDmAnyFileUpload"
    )

    private fun updateHostFeatureGroup(activity: Activity, keys: List<String>, checked: Boolean, sectionTitle: String) {
        keys.forEach { persistBooleanFeatureUpdate(it, checked) }
        findHostFeatureSection(sectionTitle)
            ?.let { showFeatureSectionDialog(activity, it) }
    }

    private sealed class HostFeature(open val key: String, open val label: String) {
        data class BooleanFeature(
            override val key: String,
            override val label: String,
            val update: ((Activity, Boolean) -> Unit)? = null,
            val value: InstagramFeatureState.() -> Boolean
        ) : HostFeature(key, label)

        data class StringFeature(
            override val key: String,
            override val label: String,
            val value: InstagramFeatureState.() -> String
        ) : HostFeature(key, label)

        data class ActionFeature(
            override val key: String,
            override val label: String,
            val color: Int,
            val action: (Activity) -> Unit
        ) : HostFeature(key, label)
    }

    private data class HostFeatureSection(
        val title: String,
        val features: List<HostFeature>
    )

    private fun hostQuickToggleSection(): HostFeatureSection = HostFeatureSection(
        "Customize Quick Toggle",
        listOf(
            HostFeature.BooleanFeature(
                key = "quickToggleCoreMaster",
                label = "Enable/Disable All",
                value = {
                    quickToggleSeen && quickToggleTyping && quickToggleScreenshot && quickToggleViewOnce &&
                        quickToggleStory && quickToggleLive && quickToggleEphemeral && quickToggleUnsend &&
                        quickToggleReplays && quickTogglePermanentView && quickToggleAllowScreenshots
                },
                update = { activity, checked ->
                    updateHostFeatureGroup(activity, hostQuickToggleMasterKeys, checked, "Customize Quick Toggle")
                }
            ),
            HostFeature.BooleanFeature("quickToggleSeen", "Include Hide DM Seen") { quickToggleSeen },
            HostFeature.BooleanFeature("quickToggleTyping", "Include Hide Typing Indicator") { quickToggleTyping },
            HostFeature.BooleanFeature("quickToggleScreenshot", "Include Bypass Screenshot Detection") { quickToggleScreenshot },
            HostFeature.BooleanFeature("quickToggleViewOnce", "Include Hide View Once Opened") { quickToggleViewOnce },
            HostFeature.BooleanFeature("quickToggleStory", "Include Hide Story Views") { quickToggleStory },
            HostFeature.BooleanFeature("quickToggleLive", "Include Hide Live Presence") { quickToggleLive },
            HostFeature.BooleanFeature("quickToggleEphemeral", "Include Keep Disappearing Messages") { quickToggleEphemeral },
            HostFeature.BooleanFeature("quickToggleUnsend", "Include Keep Unsent Messages") { quickToggleUnsend },
            HostFeature.BooleanFeature("quickToggleReplays", "Include Unlimited View-Once Replays") { quickToggleReplays },
            HostFeature.BooleanFeature("quickTogglePermanentView", "Include Permanent View Once Media") { quickTogglePermanentView },
            HostFeature.BooleanFeature("quickToggleAllowScreenshots", "Include Allow Screenshots in DMs") { quickToggleAllowScreenshots }
        )
    )

    private fun hostFeatureSections(): List<HostFeatureSection> = listOf(
        HostFeatureSection(
            "Developer Options",
            listOf(
                HostFeature.BooleanFeature("isDevEnabled", "Enable Developer Mode") { isDevEnabled },
                HostFeature.ActionFeature("openInstagramDevOptions", "Open Instagram Dev Options", Color.rgb(10, 132, 255)) {
                    openInstagramDevOptions(it)
                },
                HostFeature.ActionFeature("importInstagramDevConfig", "Import Dev Config", Color.rgb(48, 209, 88)) {
                    importInstagramDevConfig(it)
                },
                HostFeature.ActionFeature("exportInstagramDevConfig", "Export Dev Config", Color.rgb(10, 132, 255)) {
                    exportInstagramDevConfig(it)
                },
                HostFeature.BooleanFeature("removeBuildExpiredPopup", "Remove Build Expired Popup") { removeBuildExpiredPopup }
            )
        ),
        HostFeatureSection(
            "Ghost Mode Settings",
            listOf(
                HostFeature.ActionFeature("customizeQuickToggle", "Customize Quick Toggle", Color.WHITE) {
                    showFeatureSectionDialog(it, hostQuickToggleSection())
                },
                HostFeature.BooleanFeature(
                    key = "ghostSettingsCoreMaster",
                    label = "Enable/Disable All",
                    value = {
                        isGhostSeen && isGhostTyping && isGhostStory && isGhostLive && hideVoiceMessageSeen &&
                            allowScreenshots && isGhostScreenshot && isGhostViewOnce && enableUnlimitedReplays &&
                            permanentViewMode && keepEphemeralMessages && keepUnsentMessages &&
                            markTextsSeenAfterReply && storyInteractionSendsSeen
                    },
                    update = { activity, checked ->
                        updateHostFeatureGroup(activity, hostGhostMasterKeys, checked, "Ghost Mode Settings")
                    }
                ),
                HostFeature.BooleanFeature("isGhostSeen", "Hide DM Seen") { isGhostSeen },
                HostFeature.BooleanFeature("markTextsSeenAfterReply", "Mark texts as seen after reply") { markTextsSeenAfterReply },
                HostFeature.BooleanFeature("isGhostTyping", "Hide Typing Indicator") { isGhostTyping },
                HostFeature.BooleanFeature("isGhostStory", "Hide Story Views") { isGhostStory },
                HostFeature.BooleanFeature("storyInteractionSendsSeen", "Send story seen when liking/replying/commenting") { storyInteractionSendsSeen },
                HostFeature.BooleanFeature("isGhostLive", "Hide Live Presence") { isGhostLive },
                HostFeature.BooleanFeature("hideVoiceMessageSeen", "Hide Voice Message Listens") { hideVoiceMessageSeen },
                HostFeature.BooleanFeature("allowScreenshots", "Allow Screenshots in DMs") { allowScreenshots },
                HostFeature.BooleanFeature("isGhostScreenshot", "Bypass Screenshot Detection") { isGhostScreenshot },
                HostFeature.BooleanFeature("isGhostViewOnce", "Hide View Once Opened") { isGhostViewOnce },
                HostFeature.BooleanFeature("enableUnlimitedReplays", "Unlimited View-Once Replays") { enableUnlimitedReplays },
                HostFeature.BooleanFeature("permanentViewMode", "Permanent View Once Media") { permanentViewMode },
                HostFeature.BooleanFeature("keepEphemeralMessages", "Keep Disappearing Messages") { keepEphemeralMessages },
                HostFeature.BooleanFeature("keepUnsentMessages", "Keep Unsent Messages") { keepUnsentMessages },
                HostFeature.StringFeature("dmMarkSeenControlMode", "DM mark-as-seen control") { dmMarkSeenControlMode }
            )
        ),
        HostFeatureSection(
            "Ad/Analytics Block",
            listOf(
                HostFeature.BooleanFeature(
                    key = "adsAndLinksCoreMaster",
                    label = "Enable/Disable All",
                    value = { isAdBlockEnabled && isAnalyticsBlocked && disableTrackingLinks },
                    update = { activity, checked ->
                        persistBooleanFeatureUpdate("isAdBlockEnabled", checked)
                        persistBooleanFeatureUpdate("isAnalyticsBlocked", checked)
                        persistBooleanFeatureUpdate("disableTrackingLinks", checked)
                        findHostFeatureSection("Ad/Analytics Block")
                            ?.let { showFeatureSectionDialog(activity, it) }
                    }
                ),
                HostFeature.BooleanFeature("isAdBlockEnabled", "Block Ads") { isAdBlockEnabled },
                HostFeature.BooleanFeature("isAnalyticsBlocked", "Block Analytics") { isAnalyticsBlocked },
                HostFeature.BooleanFeature("disableTrackingLinks", "Disable Tracking Links") { disableTrackingLinks }
            )
        ),
        HostFeatureSection(
            "Clean Feed",
            listOf(
                HostFeature.BooleanFeature("hideSuggestionsInFeed", "Hide Suggestions in Feed") { hideSuggestionsInFeed }
            )
        ),
        HostFeatureSection(
            "Distraction-Free Instagram",
            listOf(
                HostFeature.BooleanFeature(
                    key = "distractionCoreMaster",
                    label = "Enable/Disable All",
                    value = {
                        disableStories && disableFeed && disableReels &&
                            disableReelsExceptDM && disableExplore && disableComments
                    },
                    update = { activity, checked ->
                        updateHostFeatureGroup(activity, hostDistractionMasterKeys, checked, "Distraction-Free Instagram")
                    }
                ),
                HostFeature.BooleanFeature(
                    key = "isExtremeMode",
                    label = "Extreme Mode (Irreversible until reinstall)",
                    value = { isExtremeMode },
                    update = { activity, checked ->
                        persistBooleanFeatureUpdate("isExtremeMode", checked)
                        if (checked) persistBooleanFeatureUpdate("isDistractionFree", true)
                        findHostFeatureSection("Distraction-Free Instagram")
                            ?.let { showFeatureSectionDialog(activity, it) }
                    }
                ),
                HostFeature.BooleanFeature("disableStories", "Disable Stories") { disableStories },
                HostFeature.BooleanFeature("disableFeed", "Disable Feed") { disableFeed },
                HostFeature.BooleanFeature(
                    key = "disableReels",
                    label = "Disable Reels",
                    value = { disableReels },
                    update = { activity, checked ->
                        persistBooleanFeatureUpdate("disableReels", checked)
                        if (!checked) persistBooleanFeatureUpdate("disableReelsExceptDM", false)
                        findHostFeatureSection("Distraction-Free Instagram")
                            ?.let { showFeatureSectionDialog(activity, it) }
                    }
                ),
                HostFeature.BooleanFeature(
                    key = "disableReelsExceptDM",
                    label = "Disable Reels Except in DMs",
                    value = { disableReelsExceptDM },
                    update = { activity, checked ->
                        persistBooleanFeatureUpdate("disableReelsExceptDM", checked)
                        if (checked) persistBooleanFeatureUpdate("disableReels", true)
                        findHostFeatureSection("Distraction-Free Instagram")
                            ?.let { showFeatureSectionDialog(activity, it) }
                    }
                ),
                HostFeature.BooleanFeature("disableExplore", "Disable Explore") { disableExplore },
                HostFeature.BooleanFeature("disableComments", "Disable Comments") { disableComments }
            )
        ),
        HostFeatureSection(
            "Hide UI Elements",
            listOf(
                HostFeature.BooleanFeature("captureUiElementIdsEnabled", "Capture UI Element ID Overlay") { captureUiElementIdsEnabled },
                HostFeature.ActionFeature("unhideAllHiddenUiElements", "Unhide All UI Elements", Color.rgb(255, 69, 58)) { activity ->
                    persistStringFeatureUpdate("hiddenUiElementIds", "")
                    persistStringFeatureUpdate("hiddenUiElementSelectors", "")
                    findHostFeatureSection("Hide UI Elements")
                        ?.let { showFeatureSectionDialog(activity, it) }
                },
                HostFeature.StringFeature("hiddenUiElementIds", "Hidden UI Element IDs") { hiddenUiElementIds },
                HostFeature.StringFeature("hiddenUiElementSelectors", "Hidden UI Element Selectors") { hiddenUiElementSelectors }
            )
        ),
        HostFeatureSection(
            "Misc Features",
            listOf(
                HostFeature.BooleanFeature(
                    key = "miscCoreMaster",
                    label = "Enable/Disable All",
                    value = {
                        disableStoryFlipping && disableVideoAutoPlay && feedVideosStartWithSound &&
                            storiesStartWithSound && disableRepost && showFollowerToast &&
                            showFeatureToasts && enableStoryMentions && disableDiscoverPeople &&
                            enableCopyComment && enableCopyBio && disableDoubleTapLike &&
                            enableMonetTheme && customEmojiFontEnabled &&
                            enableShareSheetEmojiShortcuts && enableActivityHistory &&
                            enableNavigationTabCustomization && enableConfirmRefresh &&
                            enableNotesLocationSpoof && enableHideChats &&
                            stripShareTrackingParameters && doNotSaveRecentSearches &&
                            enableCustomDateFormat && openLinksExternally && replaceShareLinkDomain &&
                            enableTeenAppIcons && enableStoryTrayLongPressActions &&
                            blockDmReelNotifications && blockDmPostNotifications
                    },
                    update = { activity, checked ->
                        updateHostFeatureGroup(activity, hostMiscMasterKeys, checked, "Misc Features")
                    }
                ),
                HostFeature.BooleanFeature("disableStoryFlipping", "Disable Story Auto-Skip / Auto-Swipe") { disableStoryFlipping },
                HostFeature.BooleanFeature("disableVideoAutoPlay", "Disable Video Autoplay") { disableVideoAutoPlay },
                HostFeature.BooleanFeature("feedVideosStartWithSound", "Start Feed Videos With Sound") { feedVideosStartWithSound },
                HostFeature.BooleanFeature("storiesStartWithSound", "Play Stories With Sound") { storiesStartWithSound },
                HostFeature.BooleanFeature("disableRepost", "Disable Repost") { disableRepost },
                HostFeature.BooleanFeature("showFollowerToast", "Show Follower Toast") { showFollowerToast },
                HostFeature.BooleanFeature("showFeatureToasts", "Show Feature Toasts") { showFeatureToasts },
                HostFeature.BooleanFeature("enableStoryMentions", "View Story Mentions") { enableStoryMentions },
                HostFeature.BooleanFeature("disableDiscoverPeople", "Disable Discover People") { disableDiscoverPeople },
                HostFeature.BooleanFeature("enableCopyComment", "Copy Comment") { enableCopyComment },
                HostFeature.BooleanFeature("enableCopyBio", "Copy Profile Bio") { enableCopyBio },
                HostFeature.BooleanFeature("disableDoubleTapLike", "Disable Double Tap to Like") { disableDoubleTapLike },
                HostFeature.BooleanFeature("enableMonetTheme", "Monet Theme") { enableMonetTheme },
                HostFeature.BooleanFeature("customEmojiFontEnabled", "Custom Emoji Font") { customEmojiFontEnabled },
                HostFeature.BooleanFeature("enableShareSheetEmojiShortcuts", "Share Sheet Emoji Shortcuts") { enableShareSheetEmojiShortcuts },
                HostFeature.BooleanFeature("enableActivityHistory", "Activity History Logging") { enableActivityHistory },
                HostFeature.BooleanFeature("enableNavigationTabCustomization", "Navigation Tab Customizer") { enableNavigationTabCustomization },
                HostFeature.BooleanFeature("enableConfirmRefresh", "Confirm before refreshing Feed/Reels") { enableConfirmRefresh },
                HostFeature.BooleanFeature("enableNotesLocationSpoof", "Location spoof for Notes tray") { enableNotesLocationSpoof },
                HostFeature.BooleanFeature("enableHideChats", "Hide conversations") { enableHideChats },
                HostFeature.BooleanFeature("stripShareTrackingParameters", "Strip tracing parameters when sharing links") { stripShareTrackingParameters },
                HostFeature.BooleanFeature("doNotSaveRecentSearches", "Do not save recent searches") { doNotSaveRecentSearches },
                HostFeature.BooleanFeature("enableCustomDateFormat", "Custom date format") { enableCustomDateFormat },
                HostFeature.BooleanFeature("openLinksExternally", "Open links in external browser") { openLinksExternally },
                HostFeature.BooleanFeature("replaceShareLinkDomain", "Replace domain in shared links") { replaceShareLinkDomain },
                HostFeature.BooleanFeature("enableTeenAppIcons", "Teen app icons") { enableTeenAppIcons },
                HostFeature.BooleanFeature("enableStoryTrayLongPressActions", "Story tray long-press actions") { enableStoryTrayLongPressActions },
                HostFeature.StringFeature("customDateFormat", "Date format") { customDateFormat },
                HostFeature.BooleanFeature("customDateFormatFeed", "Custom Dates in Feed") { customDateFormatFeed },
                HostFeature.BooleanFeature("customDateFormatComments", "Custom Dates in Comments") { customDateFormatComments },
                HostFeature.BooleanFeature("customDateFormatReels", "Custom Dates in Reels") { customDateFormatReels },
                HostFeature.BooleanFeature("customDateFormatStories", "Custom Dates in Stories") { customDateFormatStories },
                HostFeature.BooleanFeature("customDateFormatDirect", "Custom Dates in Direct") { customDateFormatDirect },
                HostFeature.StringFeature("shareLinkReplacementDomain", "Embed-friendly domain") { shareLinkReplacementDomain },
                HostFeature.BooleanFeature("blockDmReelNotifications", "Block DM reel notifications") { blockDmReelNotifications },
                HostFeature.BooleanFeature("blockDmPostNotifications", "Block DM post notifications") { blockDmPostNotifications },
                HostFeature.StringFeature("notesSpoofLatitude", "Latitude") { notesSpoofLatitude },
                HostFeature.StringFeature("notesSpoofLongitude", "Longitude") { notesSpoofLongitude },
                HostFeature.StringFeature("hiddenChatNames", "Hidden conversations") { hiddenChatNames },
                HostFeature.StringFeature("knownChatNames", "Known conversations") { knownChatNames },
                HostFeature.StringFeature("navigationTabHidden", "Hide navigation tabs") { navigationTabHidden },
                HostFeature.StringFeature("navigationTabOrder", "Reorder navigation tabs") { navigationTabOrder },
                HostFeature.StringFeature("navigationDefaultTab", "Default tab on open") { navigationDefaultTab },
                HostFeature.StringFeature("storyRingSize", "Story ring size") { storyRingSize },
                HostFeature.StringFeature("customEmojiFontPath", "Custom Emoji Font Path") { customEmojiFontPath },
                HostFeature.StringFeature("customEmojiFontName", "Custom Emoji Font Name") { customEmojiFontName },
                HostFeature.StringFeature("customEmojiFontUri", "Custom Emoji Font URI") { customEmojiFontUri }
            )
        ),
        HostFeatureSection(
            "Downloader",
            listOf(
                HostFeature.BooleanFeature(
                    key = "downloaderCoreMaster",
                    label = "Enable/Disable All",
                    value = {
                        enablePostDownload && enableStoryDownload && enableReelDownload &&
                            enableProfileDownload && enableDmContextMenuOptions &&
                            enableReelThumbnailDownload && enableStoryMarkSeenButton &&
                            enableStoryRepostButton && enableHighQualityStoryUpload &&
                            enableHighQualityDmUpload && enableGifCommentDownload &&
                            enableDmAnyFileUpload
                    },
                    update = { activity, checked ->
                        updateHostFeatureGroup(activity, hostDownloaderMasterKeys, checked, "Downloader")
                    }
                ),
                HostFeature.BooleanFeature("enablePostDownload", "Download Posts") { enablePostDownload },
                HostFeature.BooleanFeature("enableStoryDownload", "Download Stories") { enableStoryDownload },
                HostFeature.BooleanFeature("enableReelDownload", "Download Reels") { enableReelDownload },
                HostFeature.BooleanFeature("enableProfileDownload", "Download Profile Pictures") { enableProfileDownload },
                HostFeature.BooleanFeature("enableDmContextMenuOptions", "DM Context Menu Options") { enableDmContextMenuOptions },
                HostFeature.BooleanFeature("enableReelThumbnailDownload", "Download Reel Thumbnails") { enableReelThumbnailDownload },
                HostFeature.BooleanFeature("enableStoryMarkSeenButton", "Story Mark as Seen Button") { enableStoryMarkSeenButton },
                HostFeature.BooleanFeature("enableStoryRepostButton", "Story Repost Button") { enableStoryRepostButton },
                HostFeature.BooleanFeature("enableGifCommentDownload", "Download GIF comments") { enableGifCommentDownload },
                HostFeature.BooleanFeature("enableHighQualityStoryUpload", "High Quality Story Upload") { enableHighQualityStoryUpload },
                HostFeature.BooleanFeature("enableHighQualityDmUpload", "High Quality DM Photos") { enableHighQualityDmUpload },
                HostFeature.BooleanFeature("enableDmAnyFileUpload", "DM any-file upload picker") { enableDmAnyFileUpload },
                HostFeature.BooleanFeature("downloaderUsernameFolder", "Save in Username Subfolder") { downloaderUsernameFolder },
                HostFeature.BooleanFeature("downloaderAddTimestamp", "Add Timestamp to Filename") { downloaderAddTimestamp },
                HostFeature.StringFeature("downloaderCustomPath", "Download Folder") { downloaderCustomPath },
                HostFeature.StringFeature("downloaderCustomUri", "Download Folder URI") { downloaderCustomUri }
            )
        )
    )

    private fun findHostFeatureSection(title: String): HostFeatureSection? {
        return if (title == "Customize Quick Toggle") {
            hostQuickToggleSection()
        } else {
            hostFeatureSections().firstOrNull { it.title == title }
        }
    }

    private fun findHostFeatureSectionContaining(key: String): HostFeatureSection? {
        return (hostFeatureSections() + hostQuickToggleSection())
            .firstOrNull { section -> section.features.any { it.key == key } }
    }

    private fun persistBooleanFeatureUpdate(key: String, value: Boolean) {
        updateLocalFeatureState(key, value)
        androidContext.sendBroadcast(
            Intent(Constants.INSTAGRAM_FEATURE_PREF_UPDATE_ACTION)
                .setPackage(Constants.MODULE_PACKAGE_NAME)
                .putExtra(Constants.INSTAGRAM_FEATURE_PREF_KEY_EXTRA, key)
                .putExtra(Constants.INSTAGRAM_FEATURE_PREF_BOOLEAN_EXTRA, value)
                .putExtra(Constants.INSTAGRAM_FEATURE_PREF_IS_STRING_EXTRA, false)
        )
    }

    private fun persistStringFeatureUpdate(key: String, value: String) {
        val persistedValue = if (key == "shareLinkReplacementDomain") cleanShareReplacementDomain(value) else value
        updateLocalFeatureState(key, persistedValue)
        androidContext.sendBroadcast(
            Intent(Constants.INSTAGRAM_FEATURE_PREF_UPDATE_ACTION)
                .setPackage(Constants.MODULE_PACKAGE_NAME)
                .putExtra(Constants.INSTAGRAM_FEATURE_PREF_KEY_EXTRA, key)
                .putExtra(Constants.INSTAGRAM_FEATURE_PREF_STRING_EXTRA, persistedValue)
                .putExtra(Constants.INSTAGRAM_FEATURE_PREF_IS_STRING_EXTRA, true)
        )
    }

    private fun cleanShareReplacementDomain(raw: String): String {
        var value = raw.trim().lowercase(Locale.US)
        if (value.isEmpty()) return "ddinstagram.com"
        value = value.removePrefix("https://").removePrefix("http://")
        val slash = value.indexOf('/')
        if (slash >= 0) value = value.substring(0, slash)
        value = value.replace(Regex("[^a-z0-9.-]"), "")
        return if (value.isNotEmpty() && value.contains(".")) value else "ddinstagram.com"
    }

    private fun updateLocalFeatureState(key: String, value: Any) {
        runCatching {
            val snapshot = JSONObject()
            val stateClass = InstagramFeatureState::class.java
            InstagramFeatureState.booleanFeatureKeys.forEach { featureKey ->
                snapshot.put(featureKey, readFeatureField(stateClass, state, featureKey) as? Boolean ?: false)
            }
            InstagramFeatureState.stringFeatureKeys.forEach { featureKey ->
                snapshot.put(featureKey, readFeatureField(stateClass, state, featureKey) as? String ?: "")
            }
            snapshot.put(key, value)
            val previous = state
            val next = InstagramFeatureState.fromJson(snapshot.toString(), "local-settings:$key")
            InstagramFeatureStateStore.update(next)
            InstagramCustomEmojiFontHooks.syncAfterStateUpdate(androidContext, previous, next)
            refreshActiveHooks("local-settings:$key")
        }.onFailure { throwable ->
            logError("Failed to update local feature state for $key", throwable)
        }
    }

    private fun readFeatureField(stateClass: Class<InstagramFeatureState>, value: InstagramFeatureState, key: String): Any? {
        return runCatching {
            stateClass.getDeclaredField(key).apply { isAccessible = true }.get(value)
        }.getOrNull()
    }

    private fun isGhostQuickToggleActive(value: InstagramFeatureState): Boolean {
        return (value.quickToggleSeen && value.isGhostSeen) ||
            (value.quickToggleTyping && value.isGhostTyping) ||
            (value.quickToggleScreenshot && value.isGhostScreenshot) ||
            (value.quickToggleViewOnce && value.isGhostViewOnce) ||
            (value.quickToggleStory && value.isGhostStory) ||
            (value.quickToggleLive && value.isGhostLive) ||
            (value.quickToggleEphemeral && value.keepEphemeralMessages) ||
            (value.quickToggleUnsend && value.keepUnsentMessages) ||
            (value.quickToggleReplays && value.enableUnlimitedReplays) ||
            (value.quickTogglePermanentView && value.permanentViewMode) ||
            (value.quickToggleAllowScreenshots && value.allowScreenshots)
    }

    private fun openPurrfectManager(context: Context) {
        runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(Constants.MODULE_PACKAGE_NAME)
                ?: Intent().setPackage(Constants.MODULE_PACKAGE_NAME)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "Unable to open Purrfect", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInstagramDevOptions(context: Context) {
        if (!state.isDevEnabled) {
            persistBooleanFeatureUpdate("isDevEnabled", true)
        }
        val starter = (context as? Activity)?.takeUnless { it.isFinishing }
            ?: currentActivity?.takeUnless { it.isFinishing }
            ?: context
        val packageName = androidContext.packageName
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("instagram://settings_devoptions"))
            .setPackage(packageName)
        if (starter !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            starter.startActivity(intent)
        }.onFailure { throwable ->
            logError("Failed to open Instagram Dev Options", throwable)
            Toast.makeText(context, "Unable to open Instagram developer options", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importInstagramDevConfig(activity: Activity) {
        runCatching {
            activity.startActivity(
                Intent().apply {
                    component = ComponentName(
                        Constants.MODULE_PACKAGE_NAME,
                        "me.eternal.purrfect.instagram.InstagramJsonImportActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("target_package", androidContext.packageName)
                    putExtra("broadcast_action", Constants.INSTAGRAM_DEV_CONFIG_IMPORT_ACTION)
                }
            )
        }.onFailure { throwable ->
            logError("Failed to start developer config import", throwable)
            Toast.makeText(activity, "Unable to open config importer", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportInstagramDevConfig(activity: Activity) {
        runCatching {
            val source = File(androidContext.filesDir, "mobileconfig/mc_overrides.json")
            if (!source.exists()) {
                Toast.makeText(activity, "mc_overrides.json not found.", Toast.LENGTH_SHORT).show()
                return
            }
            val json = source.readText(Charsets.UTF_8).trim()
            activity.startActivity(
                Intent().apply {
                    component = ComponentName(
                        Constants.MODULE_PACKAGE_NAME,
                        "me.eternal.purrfect.instagram.InstagramJsonExportActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(Constants.INSTAGRAM_DEV_CONFIG_JSON_EXTRA, json)
                    putExtra("json_content", json)
                }
            )
        }.onFailure { throwable ->
            logError("Failed to export developer config", throwable)
            Toast.makeText(activity, "Failed to read config: ${throwable.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportInstagramSettingsBackup(activity: Activity) {
        runCatching {
            val json = InstagramSettingsBackup.toJson(state)
            activity.startActivity(
                Intent().apply {
                    component = ComponentName(
                        Constants.MODULE_PACKAGE_NAME,
                        "me.eternal.purrfect.instagram.InstagramJsonExportActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(Constants.INSTAGRAM_DEV_CONFIG_JSON_EXTRA, json)
                    putExtra("json_content", json)
                    putExtra("file_name", "instaeclipse_settings.json")
                }
            )
        }.onFailure { throwable ->
            logError("Failed to export settings backup", throwable)
            Toast.makeText(activity, "Failed to create backup: ${throwable.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importInstagramSettingsBackup(activity: Activity) {
        runCatching {
            activity.startActivity(
                Intent().apply {
                    component = ComponentName(
                        Constants.MODULE_PACKAGE_NAME,
                        "me.eternal.purrfect.instagram.InstagramJsonImportActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("target_package", androidContext.packageName)
                    putExtra("broadcast_action", Constants.INSTAGRAM_SETTINGS_RESTORE_ACTION)
                }
            )
        }.onFailure { throwable ->
            logError("Failed to start settings restore", throwable)
            Toast.makeText(activity, "Instagram is not open or ready.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openUrl(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure { throwable ->
            logError("Failed to open URL $url", throwable)
        }
    }

    private fun restartInstagram(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            clearAppCache(context)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            Toast.makeText(context, "Could not find the app to restart.", Toast.LENGTH_SHORT).show()
        }
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun clearAppCache(context: Context) {
        runCatching {
            deleteRecursive(context.cacheDir)
            logInfo("Cache cleared for ${context.packageName}")
        }.onFailure { throwable ->
            logError("Failed to clear app cache for ${context.packageName}", throwable)
        }
    }

    private fun deleteRecursive(file: File?) {
        if (file == null || !file.exists()) return
        if (file.isDirectory) file.listFiles()?.forEach(::deleteRecursive)
        if (file != androidContext.cacheDir) file.delete()
    }

    private fun vibrate(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(35L)
            }
        }
    }

    private fun findActivity(context: Context?): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    private fun installQuickToggle(activity: Activity) {
        if (!hasAnyQuickToggle() || quickToggleButtons.contains(activity)) return
        val root = activity.window?.decorView as? ViewGroup ?: return
        quickToggleButtons += activity
        val button = TextView(activity).apply {
            text = "PI"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(225, 48, 108))
                setStroke(2, Color.WHITE)
            }
            setOnClickListener {
                toggleQuickFeatures()
                Toast.makeText(activity, "PurrfectInsta quick toggled", Toast.LENGTH_SHORT).show()
            }
        }
        val params = FrameLayout.LayoutParams(dp(42), dp(42), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            marginEnd = dp(8)
        }
        runCatching { root.addView(button, params) }
    }

    private fun toggleQuickFeatures() {
        val old = state
        val next = old.copy(
            isGhostSeen = if (old.quickToggleSeen) !old.isGhostSeen else old.isGhostSeen,
            isGhostTyping = if (old.quickToggleTyping) !old.isGhostTyping else old.isGhostTyping,
            isGhostScreenshot = if (old.quickToggleScreenshot) !old.isGhostScreenshot else old.isGhostScreenshot,
            isGhostViewOnce = if (old.quickToggleViewOnce) !old.isGhostViewOnce else old.isGhostViewOnce,
            isGhostStory = if (old.quickToggleStory) !old.isGhostStory else old.isGhostStory,
            isGhostLive = if (old.quickToggleLive) !old.isGhostLive else old.isGhostLive,
            keepEphemeralMessages = if (old.quickToggleEphemeral) !old.keepEphemeralMessages else old.keepEphemeralMessages,
            keepUnsentMessages = if (old.quickToggleUnsend) !old.keepUnsentMessages else old.keepUnsentMessages,
            enableUnlimitedReplays = if (old.quickToggleReplays) !old.enableUnlimitedReplays else old.enableUnlimitedReplays,
            permanentViewMode = if (old.quickTogglePermanentView) !old.permanentViewMode else old.permanentViewMode,
            allowScreenshots = if (old.quickToggleAllowScreenshots) !old.allowScreenshots else old.allowScreenshots,
            source = "quick-toggle"
        )
        InstagramFeatureStateStore.update(next)
        refreshActiveHooks("quick-toggle")
    }

    private fun handleStoryTrayLongPress(view: View): Boolean {
        if (!state.enableStoryTrayLongPressActions) return false
        if (!isStoryTraySurface(view)) return false
        val activity = findActivity(view.context) ?: currentActivity ?: return false
        val media = findStoryTrayMedia(view)
        if (media.profileUrl == null && media.coverUrl == null) return false
        val labels = mutableListOf<String>()
        val urls = mutableListOf<String>()
        media.profileUrl?.let {
            labels += "View profile picture"
            urls += it
        }
        media.coverUrl?.let {
            labels += "View story cover"
            urls += it
        }
        AlertDialog.Builder(activity)
            .setTitle("Story Tray Actions")
            .setItems(labels.toTypedArray()) { _, which ->
                val index = which.coerceIn(0, urls.lastIndex)
                showImagePreview(activity, labels[index], urls[index])
            }
            .show()
        return true
    }

    private fun findStoryTrayMedia(pressed: View): StoryTrayMedia {
        val container = nearestStoryTrayContainer(pressed) ?: pressed
        val candidates = mutableListOf<Pair<View, String>>()
        fun collect(view: View, depth: Int) {
            if (depth > 8 || candidates.size > 120) return
            storyTrayImageUrls[view]?.takeIf { isHttpImageUrl(it) }?.let { candidates += view to it }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) collect(view.getChildAt(i), depth + 1)
            }
        }
        collect(container, 0)
        if (candidates.isEmpty() && container != pressed) collect(pressed, 0)

        var bestProfile: Pair<View, String>? = null
        var bestCover: Pair<View, String>? = null
        fun score(candidate: Pair<View, String>): Int {
            val (candidateView, url) = candidate
            var value = candidateView.width.coerceAtLeast(1) * candidateView.height.coerceAtLeast(1)
            val name = resourceEntryName(candidateView).orEmpty().lowercase(Locale.US)
            if (looksLikeProfileImageUrl(url)) value += 2_000_000
            if (name.contains("profile") || name.contains("avatar")) value += 1_000_000
            if (name.contains("cover") || name.contains("thumbnail") || name.contains("preview")) value += 500_000
            return value
        }
        candidates.forEach { candidate ->
            if (looksLikeProfileImageUrl(candidate.second)) {
                if (bestProfile == null || score(candidate) > score(bestProfile!!)) bestProfile = candidate
            } else if (bestCover == null || score(candidate) > score(bestCover!!)) {
                bestCover = candidate
            }
        }
        return StoryTrayMedia(bestProfile?.second, bestCover?.second)
    }

    private fun nearestStoryTrayContainer(view: View): View? {
        var current: View? = view
        repeat(10) {
            val name = resourceEntryName(current ?: return@repeat).orEmpty().lowercase(Locale.US)
            if (isStoryTrayName(name)) return current
            current = current?.parent as? View
        }
        return null
    }

    private fun isStoryTraySurface(view: View): Boolean {
        var current: View? = view
        repeat(10) {
            val name = resourceEntryName(current ?: return@repeat).orEmpty().lowercase(Locale.US)
            if (isStoryTrayName(name)) return true
            current = current?.parent as? View
        }
        return false
    }

    private fun isStoryTrayName(name: String): Boolean {
        return name.contains("story_tray") ||
            name.contains("reel_tray") ||
            name.contains("reels_tray") ||
            name.contains("tray_item") ||
            name.contains("story_ring") ||
            name.contains("reel_ring") ||
            name.contains("highlight") ||
            name.contains("avatar_reel") ||
            name.contains("top_serp_story")
    }

    private fun showImagePreview(activity: Activity, title: String, url: String) {
        val imageView = ImageView(activity).apply {
            val padding = dp(12)
            setPadding(padding, padding, padding, padding)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            minimumHeight = dp(260)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(imageView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        Thread({
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Instagram 300.0.0.0 Android")
                try {
                    connection.inputStream.use { BitmapFactory.decodeStream(it) ?: error("bitmap decode failed") }
                } finally {
                    connection.disconnect()
                }
            }.onSuccess { bitmap ->
                mainHandler.post { if (dialog.isShowing) imageView.setImageBitmap(bitmap) }
            }.onFailure {
                mainHandler.post { Toast.makeText(activity, "No media found", Toast.LENGTH_SHORT).show() }
            }
        }, "PurrfectInstaStoryPreview").start()
    }

    private fun handleGifCommentLongPress(view: View): Boolean {
        if (!state.enableGifCommentDownload || !isCommentSurface(view)) return false
        val url = findGifCommentUrlNear(view) ?: return false
        val activity = findActivity(view.context) ?: currentActivity ?: return false
        AlertDialog.Builder(activity)
            .setTitle("GIF Comment")
            .setItems(arrayOf("Download GIF")) { _, _ ->
                enqueueDownload(url, DownloadMetadata(type = "comment_gif", folderName = "comments"))
            }
            .show()
        return true
    }

    private fun findGifCommentUrlNear(view: View): String? {
        var current: View? = view
        repeat(8) {
            val node = current ?: return@repeat
            storyTrayImageUrls[node]?.takeIf { looksLikeGifCommentUrl(it) }?.let { return it }
            if (node is ViewGroup) findGifCommentUrlInTree(node, 0, intArrayOf(80))?.let { return it }
            current = node.parent as? View
        }
        return null
    }

    private fun findGifCommentUrlInTree(view: View, depth: Int, budget: IntArray): String? {
        if (depth > 8 || budget[0]-- <= 0) return null
        storyTrayImageUrls[view]?.takeIf { looksLikeGifCommentUrl(it) }?.let { return it }
        extractUrlFromView(view)?.takeIf { looksLikeGifCommentUrl(it) }?.let { return it }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findGifCommentUrlInTree(view.getChildAt(i), depth + 1, budget)?.let { return it }
            }
        }
        return null
    }

    private fun isCommentSurface(view: View): Boolean {
        var current: View? = view
        repeat(10) {
            val node = current ?: return@repeat
            val name = resourceEntryName(node).orEmpty().lowercase(Locale.US)
            if (name.contains("comment") || name.contains("comments") || name.contains("ufi") ||
                name.contains("reply_row") || name.contains("media_comment")
            ) {
                return true
            }
            current = node.parent as? View
        }
        return false
    }

    private fun looksLikeGifCommentUrl(url: String?): Boolean {
        val lower = url?.lowercase(Locale.US) ?: return false
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        if (looksLikeProfileImageUrl(lower)) return false
        return lower.contains("giphy") ||
            lower.contains("tenor") ||
            lower.contains("gif") ||
            lower.contains("sticker") ||
            lower.contains(".webp") ||
            lower.contains(".mp4") ||
            lower.contains("image_url") ||
            lower.contains("animated")
    }

    private fun applyProfilePictureDownload(view: View) {
        if (!state.enableProfileDownload) return
        ensureProfileResourceIds(view.context)
        when {
            isExpandedProfilePicView(view) -> {
                injectProfilePictureLongPress(view)
                scheduleProfileDownloadButtonInjection(view.rootView)
                rememberExpandedProfilePictureUrl(view)
            }
            view is ViewGroup && isProfileShareCard(view) -> {
                scheduleProfileCardInjection(view, 0L)
            }
            isExistingNativeProfileDownloadButton(view) -> {
                hideNativeProfileDownloadButton(view)
            }
        }
    }

    private fun injectProfilePictureLongPress(view: View) {
        if (!profileDownloadHookedPics.add(view)) return
        view.isLongClickable = true
        view.setOnLongClickListener {
            downloadCurrentProfilePicture(it.context, it)
            true
        }
    }

    private fun scheduleProfileDownloadButtonInjection(rootOrCard: View?) {
        if (rootOrCard == null) return
        ensureProfileResourceIds(rootOrCard.context)
        if (rootOrCard is ViewGroup && isProfileShareCardFast(rootOrCard)) {
            scheduleProfileCardInjection(rootOrCard, 0L)
            return
        }
        if (!profilePendingRootInjections.add(rootOrCard)) return
        rootOrCard.postDelayed({
            profilePendingRootInjections.remove(rootOrCard)
            maybeInjectProfileDownloadButton(rootOrCard)
        }, 96L)
    }

    private fun scheduleProfileCardInjection(card: ViewGroup?, delayMs: Long) {
        card ?: return
        if (!profilePendingCardInjections.add(card)) return
        card.postDelayed({
            profilePendingCardInjections.remove(card)
            injectProfileDownloadButton(card)
        }, delayMs.coerceAtLeast(0L))
    }

    private fun maybeInjectProfileDownloadButton(rootOrCard: View?) {
        if (!state.enableProfileDownload || rootOrCard == null) return
        ensureProfileResourceIds(rootOrCard.context)
        val card = when {
            rootOrCard is ViewGroup && isProfileShareCard(rootOrCard) -> rootOrCard
            else -> findViewByIdOrName(rootOrCard, profileShareCardId, "profile_share_card") as? ViewGroup
        } ?: return
        scheduleProfileCardInjection(card, 0L)
    }

    private fun injectProfileDownloadButton(rootOrCard: View?) {
        val card = rootOrCard as? ViewGroup ?: return
        if (!state.enableProfileDownload || card.childCount == 0) return
        hideNativeProfileDownloadButton(card)
        card.findViewWithTag<View>("purrfect_insta_profile_download_button")?.let {
            bindProfileDownloadClick(it)
            normalizeProfileShareCard(card)
            return
        }
        val context = card.context
        val item = inflateProfileDownloadItem(context, card).apply {
            tag = "purrfect_insta_profile_download_button"
            layoutParams = profileActionItemLayoutParams(card)
        }
        bindProfileDownloadClick(item)
        val spacer = Space(context).apply { layoutParams = profileSpacerLayoutParams(card) }
        val insertIndex = profileTrailingSpaceIndex(card)
        runCatching {
            suppressProfileActionBarChildHook.set(true)
            card.addView(spacer, insertIndex)
            card.addView(item, insertIndex + 1)
        }.onFailure { logError("Failed injecting profile download button", it) }
        suppressProfileActionBarChildHook.remove()
        normalizeProfileShareCard(card)
    }

    private fun inflateProfileDownloadItem(context: Context, parent: ViewGroup): View {
        val inflated = profileInteractionLayoutId.takeIf { it != 0 }?.let { layoutId ->
            runCatching { LayoutInflater.from(context).inflate(layoutId, parent, false) }.getOrNull()
        }
        val item = inflated ?: createFallbackProfileDownloadItem(context)
        item.visibility = View.VISIBLE
        item.isEnabled = true
        item.isClickable = true
        item.isFocusable = true
        item.contentDescription = "Download"
        findImageViewByIdOrName(item, profileInteractionIconId, "profile_interaction_item_icon")?.apply {
            setImageResource(android.R.drawable.stat_sys_download_done)
            setColorFilter(Color.WHITE)
            contentDescription = "Download"
        }
        normalizeProfileDownloadItemText(item)
        return item
    }

    private fun createFallbackProfileDownloadItem(context: Context): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(ImageView(context).apply {
                setImageResource(android.R.drawable.stat_sys_download_done)
                setColorFilter(Color.WHITE)
            }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(TextView(context).apply {
                text = "Download"
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                textSize = 14f
                maxLines = 2
                setSingleLine(false)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            })
        }
    }

    private fun profileSpacerLayoutParams(parent: ViewGroup): ViewGroup.LayoutParams {
        return if (parent is LinearLayout) LinearLayout.LayoutParams(0, 0, 0f) else ViewGroup.LayoutParams(0, 0)
    }

    private fun profileActionItemLayoutParams(parent: ViewGroup): ViewGroup.LayoutParams {
        return if (parent is LinearLayout) {
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
        } else {
            ViewGroup.LayoutParams(dp(74), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun bindProfileDownloadClick(button: View) {
        button.setOnClickListener { downloadCurrentProfilePicture(it.context, it.rootView) }
        button.setOnLongClickListener {
            downloadCurrentProfilePicture(it.context, it.rootView)
            true
        }
    }

    private fun normalizeProfileShareCard(card: ViewGroup) {
        if (card !is LinearLayout) return
        for (index in 0 until card.childCount) {
            val child = card.getChildAt(index)
            if (child is Space) {
                child.visibility = View.GONE
                child.layoutParams = LinearLayout.LayoutParams(0, 0, 0f)
                continue
            }
            if (child.visibility == View.GONE) continue
            child.minimumWidth = 0
            child.layoutParams = profileActionItemLayoutParams(card)
            normalizeProfileDownloadItemText(child)
        }
        card.clipToPadding = false
        card.clipChildren = false
        (card.parent as? ViewGroup)?.clipChildren = false
    }

    private fun normalizeProfileDownloadItemText(item: View) {
        findTextViewByIdOrName(item, profileInteractionLabelId, "profile_interaction_item_label")?.apply {
            text = if (item.tag == "purrfect_insta_profile_download_button") "Download" else text
            gravity = Gravity.CENTER
            setSingleLine(false)
            maxLines = 2
        }
    }

    private fun profileTrailingSpaceIndex(card: ViewGroup): Int {
        val count = card.childCount
        if (count <= 0) return 0
        return if (card.getChildAt(count - 1) is Space) count - 1 else count
    }

    private fun hideNativeProfileDownloadButton(rootOrButton: View?) {
        rootOrButton ?: return
        val button = if (isExistingNativeProfileDownloadButton(rootOrButton)) {
            rootOrButton
        } else {
            findViewByIdOrName(rootOrButton, profileShareCardDownloadButtonId, "profile_share_card_download_button")
        }
        if (button?.tag == "purrfect_insta_profile_download_button") return
        button?.apply {
            visibility = View.GONE
            isEnabled = false
            setOnClickListener(null)
            setOnLongClickListener(null)
        }
        findViewByIdOrName(rootOrButton.rootView, profileShareCardDownloadSpacerId, "profile_share_card_download_button_spacer")
            ?.let { it.visibility = View.GONE }
    }

    private fun downloadCurrentProfilePicture(context: Context, anchor: View?) {
        val url = resolveProfilePictureUrl(anchor) ?: run {
            Toast.makeText(context, "Profile picture URL not found", Toast.LENGTH_SHORT).show()
            return
        }
        val activity = findActivity(context)
        val username = findVisibleProfileUsername(activity?.window?.decorView ?: anchor?.rootView)
        enqueueDownload(url, DownloadMetadata(username = username, type = "profile"))
    }

    private fun resolveProfilePictureUrl(anchor: View?): String? {
        if (anchor != null) {
            storyTrayImageUrls[anchor]?.takeIf { isHttpImageUrl(it) }?.let { return it }
            extractMediaUrlFromView(anchor)?.takeIf { isHttpImageUrl(it) }?.let { return it }
            if (anchor is ViewGroup) findProfileImageUrlInTree(anchor, 0, intArrayOf(120))?.let { return it }
        }
        return lastProfilePicUrl?.takeIf { isHttpImageUrl(it) }
    }

    private fun rememberExpandedProfilePictureUrl(view: View) {
        resolveProfilePictureUrl(view)?.let { lastProfilePicUrl = it }
    }

    private fun ensureProfileResourceIds(context: Context?) {
        if (profileResourceIdsResolved || context == null) return
        synchronized(this) {
            if (profileResourceIdsResolved) return
            fun id(name: String, type: String, fallback: Int): Int {
                return runCatching { context.resources.getIdentifier(name, type, context.packageName) }
                    .getOrDefault(0)
                    .takeIf { it != 0 } ?: fallback
            }
            expandedProfilePicId = id("expanded_profile_pic", "id", expandedProfilePicId)
            profileShareCardId = id("profile_share_card", "id", profileShareCardId)
            profileShareCardDownloadButtonId = id("profile_share_card_download_button", "id", profileShareCardDownloadButtonId)
            profileShareCardDownloadSpacerId = id("profile_share_card_download_button_spacer", "id", profileShareCardDownloadSpacerId)
            profileInteractionIconId = id("profile_interaction_item_icon", "id", profileInteractionIconId)
            profileInteractionLabelId = id("profile_interaction_item_label", "id", profileInteractionLabelId)
            profileInteractionLayoutId = id("layout_expanded_profile_picture_interaction_bar_item_view", "layout", profileInteractionLayoutId)
            profileResourceIdsResolved = true
        }
    }

    private fun isExpandedProfilePicView(view: View?): Boolean {
        view ?: return false
        ensureProfileResourceIds(view.context)
        return view.id != View.NO_ID && view.id == expandedProfilePicId
    }

    private fun isProfileShareCardFast(view: View?): Boolean {
        view ?: return false
        return view.id != View.NO_ID && view.id == profileShareCardId
    }

    private fun isProfileShareCard(view: View?): Boolean {
        view ?: return false
        ensureProfileResourceIds(view.context)
        return isProfileShareCardFast(view) || resourceEntryName(view).orEmpty().contains("profile_share_card")
    }

    private fun isExistingNativeProfileDownloadButton(view: View?): Boolean {
        view ?: return false
        ensureProfileResourceIds(view.context)
        return (view.id != View.NO_ID && view.id == profileShareCardDownloadButtonId) ||
            resourceEntryName(view).orEmpty().contains("profile_share_card_download_button")
    }

    private fun findViewByIdOrName(root: View?, id: Int, name: String): View? {
        root ?: return null
        runCatching {
            if (id != 0) root.findViewById<View>(id)?.let { return it }
        }
        if (resourceEntryName(root) == name) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findViewByIdOrName(root.getChildAt(i), id, name)?.let { return it }
            }
        }
        return null
    }

    private fun findImageViewByIdOrName(root: View?, id: Int, name: String): ImageView? =
        findViewByIdOrName(root, id, name) as? ImageView

    private fun findTextViewByIdOrName(root: View?, id: Int, name: String): TextView? =
        findViewByIdOrName(root, id, name) as? TextView

    private fun findProfileImageUrlInTree(view: View, depth: Int, budget: IntArray): String? {
        if (depth > 8 || budget[0]-- <= 0) return null
        storyTrayImageUrls[view]?.takeIf { isHttpImageUrl(it) }?.let { return it }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findProfileImageUrlInTree(view.getChildAt(i), depth + 1, budget)?.let { return it }
            }
        }
        return null
    }

    private fun handleCopyLongPress(view: View): Boolean {
        val text = findTextForCopy(view) ?: return false
        if (!state.enableCopyComment && !state.enableCopyBio) return false
        val clipboard = androidContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("Instagram", text))
        Toast.makeText(androidContext, "Copied", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun handleCaptureLongPress(view: View): Boolean {
        if (!state.captureUiElementIdsEnabled) return false
        val id = resourceEntryName(view)
        val selector = buildSelector(view)
        val value = id ?: selector ?: return false
        androidContext.sendBroadcast(
            Intent(Constants.INSTAGRAM_UI_ELEMENT_CAPTURED_ACTION)
                .setPackage(Constants.MODULE_PACKAGE_NAME)
                .putExtra(Constants.INSTAGRAM_UI_ELEMENT_CAPTURED_VALUE_EXTRA, value)
                .putExtra(Constants.INSTAGRAM_UI_ELEMENT_CAPTURED_IS_SELECTOR_EXTRA, id == null)
        )
        Toast.makeText(androidContext, "Captured Instagram UI element", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun enqueueDownload(url: String, metadata: DownloadMetadata = DownloadMetadata()) {
        runCatching {
            val uri = Uri.parse(url)
            val extension = uri.lastPathSegment?.substringAfterLast('.', "bin")?.substringBefore('?')?.takeIf { it.length in 2..5 } ?: extensionForUrl(url)
            val fileName = buildDownloadFilename(url, metadata, extension)
            val safeUsername = sanitizePathSegment(metadata.folderName ?: metadata.username)
            val mimeType = mimeTypeForUrl(url)
            val customTreeUri = state.downloaderCustomUri.ifBlank {
                state.downloaderCustomPath.takeIf { it.startsWith("content://") }.orEmpty()
            }
            if (customTreeUri.isNotBlank()) {
                val serviceIntent = Intent(Constants.INSTAGRAM_DOWNLOAD_SAVE_ACTION)
                    .setClassName(Constants.MODULE_PACKAGE_NAME, "me.eternal.purrfect.instagram.InstagramDownloadSaveService")
                    .putExtra(Constants.INSTAGRAM_DOWNLOAD_URL_EXTRA, url)
                    .putExtra(Constants.INSTAGRAM_DOWNLOAD_AUDIO_URL_EXTRA, metadata.audioUrl)
                    .putExtra(Constants.INSTAGRAM_DOWNLOAD_FILENAME_EXTRA, fileName)
                    .putExtra(Constants.INSTAGRAM_DOWNLOAD_MIME_TYPE_EXTRA, mimeType)
                    .putExtra(Constants.INSTAGRAM_DOWNLOAD_USERNAME_EXTRA, safeUsername)
                    .putExtra(Constants.INSTAGRAM_DOWNLOAD_TREE_URI_EXTRA, customTreeUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    androidContext.startForegroundService(serviceIntent)
                } else {
                    androidContext.startService(serviceIntent)
                }
                Toast.makeText(androidContext, "Instagram download started", Toast.LENGTH_SHORT).show()
                return
            }

            Thread({
                runCatching {
                    val output = openDownloadOutputStream(androidContext, fileName, mimeType, safeUsername)
                    try {
                        downloadToStream(url, output)
                    } catch (throwable: Throwable) {
                        (output as? PendingMediaStoreOutputStream)?.discard()
                        throw throwable
                    } finally {
                        output.close()
                    }
                }.onSuccess {
                    mainHandler.post { Toast.makeText(androidContext, "Saved: $fileName", Toast.LENGTH_SHORT).show() }
                }.onFailure { throwable ->
                    logError("Instagram direct media save failed", throwable)
                    mainHandler.post { Toast.makeText(androidContext, "Download failed: ${throwable.message}", Toast.LENGTH_SHORT).show() }
                }
            }, "PurrfectInstaDownload").start()
            Toast.makeText(androidContext, "Instagram download started", Toast.LENGTH_SHORT).show()
        }.onFailure { logError("Failed to enqueue Instagram media download", it) }
    }

    private fun openDownloadOutputStream(context: Context, filename: String, mimeType: String, username: String?): OutputStream {
        val rawPath = state.downloaderCustomPath.takeIf { it.isNotBlank() && !it.startsWith("content://") }
        if (rawPath != null) {
            runCatching {
                var dir = File(rawPath)
                if (state.downloaderUsernameFolder && !username.isNullOrBlank()) dir = File(dir, username)
                if (!dir.exists() && !dir.mkdirs()) error("Cannot create dir: ${dir.absolutePath}")
                return java.io.FileOutputStream(File(dir, filename))
            }.onFailure { logError("Instagram raw download path failed; falling back to MediaStore", it) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
                put(MediaStore.MediaColumns.RELATIVE_PATH, buildDownloadRelativePath(username))
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed")
            return PendingMediaStoreOutputStream(
                context,
                uri,
                context.contentResolver.openOutputStream(uri) ?: error("MediaStore openOutputStream failed")
            )
        }

        var dir = File(Environment.getExternalStorageDirectory(), "PurrfectInsta")
        if (state.downloaderUsernameFolder && !username.isNullOrBlank()) dir = File(dir, username)
        if (!dir.exists() && !dir.mkdirs()) error("Cannot create dir: ${dir.absolutePath}")
        return java.io.FileOutputStream(File(dir, filename))
    }

    private fun buildDownloadRelativePath(username: String?): String {
        var base = "Download/PurrfectInsta"
        val customPath = state.downloaderCustomPath
        if (customPath.isNotBlank() && !customPath.startsWith("content://")) {
            val externalRoot = Environment.getExternalStorageDirectory().absolutePath
            if (customPath.startsWith("$externalRoot/")) {
                val relative = customPath.substring(externalRoot.length + 1).trim('/')
                if (relative.isNotBlank()) {
                    val topLevel = relative.substringBefore('/')
                    base = if (topLevel in mediaStoreDownloadRoots) relative else "Download/$relative"
                }
            }
        }
        if (state.downloaderUsernameFolder && !username.isNullOrBlank()) base += "/$username"
        return base
    }

    private fun buildDownloadFilename(url: String, metadata: DownloadMetadata, extension: String): String {
        val username = sanitizePathSegment(metadata.username).ifBlank { "unknown" }
        val type = metadata.type.ifBlank { if (mimeTypeForUrl(url).startsWith("video")) "video" else "image" }
        val mediaId = sanitizePathSegment(metadata.mediaId).ifBlank {
            val segment = Uri.parse(url).lastPathSegment?.substringBefore('?').orEmpty()
            segment.substringBeforeLast('.').takeIf { it.isNotBlank() } ?: System.currentTimeMillis().toString()
        }
        val ext = extension.trimStart('.').ifBlank { extensionForUrl(url) }
        return buildString {
            append(username).append('_').append(type).append('_').append(mediaId)
            if (state.downloaderAddTimestamp) {
                append('_').append(java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date()))
            }
            append('.').append(ext)
        }
    }

    private fun sanitizePathSegment(value: String?): String {
        return value?.trim()
            ?.removePrefix("@")
            ?.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
            ?.trim('_')
            ?.take(80)
            .orEmpty()
    }

    private fun mimeTypeForUrl(url: String): String {
        val lower = url.substringBefore('?').lowercase(Locale.US)
        return when {
            lower.endsWith(".mp4") || lower.endsWith(".mov") -> "video/mp4"
            lower.endsWith(".m4a") || lower.endsWith(".aac") -> "audio/mp4"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            else -> if (looksLikeMediaUrl(url) && lower.contains(".mp4")) "video/mp4" else "application/octet-stream"
        }
    }

    private fun extensionForUrl(url: String): String {
        return when (mimeTypeForUrl(url)) {
            "video/mp4" -> "mp4"
            "audio/mp4" -> "m4a"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/jpeg" -> "jpg"
            else -> "bin"
        }
    }

    private fun shouldBlockNotification(notification: Notification): Boolean {
        if (!state.blockDmReelNotifications && !state.blockDmPostNotifications) return false
        val text = notificationText(notification).lowercase(Locale.US)
        if (text.isBlank()) return false
        val directShare = text.contains("sent you") ||
            text.contains("sent a") ||
            text.contains("shared") ||
            text.contains("forwarded") ||
            text.contains("replied with")
        return (state.blockDmReelNotifications && directShare && (text.contains(" reel") || text.contains("reels") || text.contains("clip"))) ||
            (state.blockDmPostNotifications && directShare &&
                (text.contains(" post") || text.contains("photo") || text.contains("video") ||
                    text.contains("media") || text.contains("publication")))
    }

    private fun notificationText(notification: Notification): String {
        val out = StringBuilder()
        notification.tickerText?.let { out.append(it).append(' ') }
        val extras = notification.extras ?: return out.toString()
        fun append(value: CharSequence?) {
            if (value != null) out.append(value).append(' ')
        }
        append(extras.getCharSequence(Notification.EXTRA_TITLE))
        append(extras.getCharSequence(Notification.EXTRA_TEXT))
        append(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        append(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { append(it) }
        extras.keySet().forEach { key ->
            when (val value = runCatching { extras.get(key) }.getOrNull()) {
                is CharSequence -> append(value)
                is Array<*> -> value.filterIsInstance<CharSequence>().forEach { append(it) }
            }
        }
        return out.toString()
    }

    private fun sanitizeIntent(intent: Intent): Boolean {
        var changed = false
        if (state.enableDmAnyFileUpload && widenFilePicker(intent)) changed = true
        sanitizeUri(intent.data, allowShareDomainRewrite = false)?.let {
            intent.data = it
            changed = true
        }
        sanitizeClipData(intent.clipData)?.let {
            intent.clipData = it
            changed = true
        }
        intent.extras?.keySet()?.forEach { key ->
            val value = runCatching { intent.extras?.get(key) }.getOrNull()
            when (value) {
                is String -> {
                    val clean = sanitizeTextForSharing(value)
                    if (clean != value) {
                        intent.putExtra(key, clean)
                        changed = true
                    }
                }
                is CharSequence -> {
                    val clean = sanitizeTextForSharing(value.toString())
                    if (clean != value.toString()) {
                        intent.putExtra(key, clean as CharSequence)
                        changed = true
                    }
                }
            }
        }
        return changed
    }

    private fun launchExternalBrowserIfNeeded(context: Context, intent: Intent): Boolean {
        if (launchingExternalBrowser.get()) return false
        val url = extractHttpUrl(intent) ?: return false
        if (!looksInternalBrowserIntent(context, intent)) return false
        return runCatching {
            val external = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (context !is Activity) external.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launchingExternalBrowser.set(true)
            context.startActivity(external)
            true
        }.onFailure {
            Toast.makeText(context, "Could not open external browser", Toast.LENGTH_SHORT).show()
        }.also {
            launchingExternalBrowser.set(false)
        }.getOrDefault(false)
    }

    private fun looksInternalBrowserIntent(context: Context, intent: Intent): Boolean {
        val action = intent.action.orEmpty().lowercase(Locale.US)
        val hostPackage = context.packageName.lowercase(Locale.US)
        val targetPackage = intent.`package`.orEmpty().lowercase(Locale.US)
        val component = intent.component?.flattenToString().orEmpty().lowercase(Locale.US)
        val extras = extrasSummary(intent).lowercase(Locale.US)
        return component.contains("browserlite") ||
            component.contains("webview") ||
            component.contains("igbrowser") ||
            component.contains("inappbrowser") ||
            component.contains("web_view") ||
            (component.contains("modalactivity") && (extras.contains("browser") || extras.contains("webview") || extras.contains("web_view") || extras.contains("url"))) ||
            (intent.action == Intent.ACTION_VIEW && isWebUri(intent.data) && targetPackage == hostPackage) ||
            (action != Intent.ACTION_VIEW.lowercase(Locale.US) && isHttp(intent.dataString) &&
                ((targetPackage.isNotBlank() && targetPackage == hostPackage) || component.contains(hostPackage)))
    }

    private fun extractHttpUrl(intent: Intent): String? {
        intent.dataString?.takeIf { isHttp(it) }?.let { return it }
        return valueToHttpUrl(intent.extras)
    }

    private fun valueToHttpUrl(value: Any?): String? {
        return when (value) {
            null -> null
            is Uri -> value.toString().takeIf { isHttp(it) }
            is String -> value.takeIf { isHttp(it) }
            is CharSequence -> value.toString().takeIf { isHttp(it) }
            is Bundle -> {
                value.keySet().firstNotNullOfOrNull { key ->
                    runCatching { valueToHttpUrl(value.get(key)) }.getOrNull()
                }
            }
            else -> null
        }
    }

    private fun extrasSummary(intent: Intent): String {
        val extras = intent.extras ?: return ""
        return buildString {
            extras.keySet().forEach { key ->
                append(' ').append(key)
                val value = runCatching { extras.get(key) }.getOrNull()
                if (value is CharSequence) append(' ').append(value)
            }
        }
    }

    private fun isHttp(value: String?): Boolean {
        val lower = value?.trim()?.lowercase(Locale.US).orEmpty()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun widenFilePicker(intent: Intent?): Boolean {
        if (intent == null) return false
        var changed = false
        @Suppress("DEPRECATION")
        val nested = runCatching { intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) }.getOrNull()
        if (nested != null && widenFilePicker(nested)) changed = true

        if (!looksLikePicker(intent)) return changed
        val oldType = intent.type
        if (shouldWidenType(oldType)) {
            intent.type = "*/*"
            changed = true
        }
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mergeMimeTypes(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)))
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        return true
    }

    private fun looksLikePicker(intent: Intent?): Boolean {
        if (intent == null) return false
        val action = intent.action
        if (action in pickerActions) return true
        val combined = "${lower(action)} ${lower(intent.type)} ${lower(intent.data?.toString())}"
        return combined.contains("content") &&
            (combined.contains("image") || combined.contains("video") || combined.contains("audio"))
    }

    private fun shouldWidenType(type: String?): Boolean {
        val clean = lower(type)
        return clean.isBlank() ||
            clean == "*/*" ||
            clean.startsWith("image/") ||
            clean.startsWith("video/") ||
            clean.startsWith("audio/") ||
            clean.contains("octet-stream")
    }

    private fun mergeMimeTypes(existing: Array<String>?): Array<String> {
        val merged = linkedSetOf<String>()
        merged += broadMimeTypes
        existing.orEmpty().forEach { type ->
            if (type.isNotBlank()) merged += type
        }
        return merged.toTypedArray()
    }

    private fun sanitizeClipData(original: ClipData?): ClipData? {
        if (original == null || original.itemCount == 0) return null
        var sanitized: ClipData? = null
        var changed = false
        val description: ClipDescription = original.description
        for (i in 0 until original.itemCount) {
            val item = original.getItemAt(i)
            val text = item.text
            val uri = item.uri
            val cleanText = text?.toString()?.let { sanitizeTextForSharing(it) }
            val cleanUri = sanitizeUri(uri)
            val cleanItem = ClipData.Item(
                cleanText ?: text,
                item.htmlText,
                item.intent,
                cleanUri ?: uri
            )
            if (sanitized == null) sanitized = ClipData(description, cleanItem) else sanitized.addItem(cleanItem)
            if ((cleanText != null && cleanText != text?.toString()) || cleanUri != null) changed = true
        }
        return if (changed) sanitized else null
    }

    private fun sanitizeTextForSharing(text: String): String {
        var input = text
        if (state.shouldRewriteShareDomain()) input = rewriteShareDomainText(input)
        if (input.indexOf("http", ignoreCase = true) < 0) return input
        val matcher = urlPattern.matcher(input)
        val out = StringBuffer()
        var changed = false
        while (matcher.find()) {
            val raw = matcher.group()
            val clean = sanitizeUri(Uri.parse(raw))
            if (clean != null) {
                matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(clean.toString()))
                changed = true
            }
        }
        matcher.appendTail(out)
        return if (changed) out.toString() else input
    }

    private fun sanitizeUri(uri: Uri?, allowShareDomainRewrite: Boolean = true): Uri? {
        if (uri == null || !isWebUri(uri)) return null
        val replacedDomain = if (allowShareDomainRewrite && state.shouldRewriteShareDomain() && isInstagramShareHost(uri.host)) {
            uri.buildUpon().authority(sanitizedShareReplacementDomain()).build()
        } else {
            uri
        }
        if (!state.disableTrackingLinks && !state.stripShareTrackingParameters && replacedDomain == uri) return null
        val names = runCatching { replacedDomain.queryParameterNames }.getOrNull().orEmpty()
        if (names.isEmpty()) return if (replacedDomain != uri) replacedDomain else null
        val builder = replacedDomain.buildUpon().clearQuery()
        var removed = false
        names.forEach { name ->
            if (isTrackingParam(name)) {
                removed = true
            } else {
                replacedDomain.getQueryParameters(name).forEach { value -> builder.appendQueryParameter(name, value) }
            }
        }
        val result = builder.build()
        return if (removed || result != uri) result else null
    }

    private fun rewriteShareDomainText(input: String): String {
        if (input.isEmpty()) return input
        val domain = sanitizedShareReplacementDomain()
        val matcher = instagramShareLinkPattern.matcher(input)
        val out = StringBuffer()
        var changed = false
        while (matcher.find()) {
            var match = matcher.group()
            var suffix = ""
            while (match.isNotEmpty() && isShareLinkTrailingPunctuation(match.last())) {
                suffix = match.last() + suffix
                match = match.dropLast(1)
            }
            val replacement = rewriteSingleShareUrl(match, domain) + suffix
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(replacement))
            changed = true
        }
        matcher.appendTail(out)
        return if (changed) out.toString() else input
    }

    private fun rewriteSingleShareUrl(original: String, domain: String): String {
        return runCatching {
            Uri.parse(original).buildUpon().authority(domain).build().toString()
        }.getOrElse {
            original.replaceFirst(
                Regex("(?i)://(?:www\\.|m\\.)?(instagram\\.com|instagr\\.am)"),
                "://$domain"
            )
        }
    }

    private fun isInstagramShareHost(host: String?): Boolean {
        val lowerHost = host?.lowercase(Locale.US) ?: return false
        return lowerHost == "instagram.com" ||
            lowerHost == "www.instagram.com" ||
            lowerHost == "m.instagram.com" ||
            lowerHost == "instagr.am" ||
            lowerHost == "www.instagr.am" ||
            lowerHost == "m.instagr.am"
    }

    private fun sanitizedShareReplacementDomain(): String {
        return cleanShareReplacementDomain(state.shareLinkReplacementDomain)
    }

    private fun isShareLinkTrailingPunctuation(char: Char): Boolean {
        return char == '.' || char == ',' || char == '!' || char == '?' || char == ')' || char == ']'
    }

    private fun maybeUpgradeUploadQuality(param: XC_MethodHook.MethodHookParam<*>, qualityIndex: Int, source: String) {
        if (qualityIndex !in param.args.indices) return
        val current = param.args[qualityIndex] as? Int ?: return
        if (current >= TARGET_UPLOAD_QUALITY) return
        val surface = currentUploadSurface()
        val allowed = (surface == UploadSurface.STORY && state.enableHighQualityStoryUpload) ||
            (surface == UploadSurface.DIRECT && state.enableHighQualityDmUpload)
        if (!allowed) {
            if ((state.enableHighQualityStoryUpload || state.enableHighQualityDmUpload) && uploadQualitySkippedLogCount++ < 10) {
                logInfo("Saw $source upload quality=$current surface=$surface; left unchanged")
            }
            return
        }
        param.args[qualityIndex] = TARGET_UPLOAD_QUALITY
        if (uploadQualityLogCount++ < 12) {
            logInfo("$source upload quality $current -> $TARGET_UPLOAD_QUALITY surface=$surface")
        }
    }

    private fun currentUploadSurface(): UploadSurface {
        var direct = false
        var story = false
        Thread.currentThread().stackTrace.forEach { frame ->
            val s = "${frame.className}.${frame.methodName}".lowercase(Locale.US)
            if (s.contains("direct") || s.contains("armadillo") || s.contains("messagethread") ||
                s.contains("thread") || s.contains("inbox") || s.contains("dm") ||
                s.contains("directsend") || s.contains("directshare")
            ) {
                direct = true
            }
            if (s.contains("story") || s.contains("stories") || s.contains("reel") ||
                s.contains("reels") || s.contains("camera") || s.contains("creation") ||
                s.contains("pendingmedia") || s.contains("composer") || s.contains("capture") ||
                s.contains("share") || s.contains("upload")
            ) {
                story = true
            }
        }
        return when {
            direct -> UploadSurface.DIRECT
            story -> UploadSurface.STORY
            else -> UploadSurface.UNKNOWN
        }
    }

    private fun maybeConfirmOrBlockRefresh(
        service: Any?,
        method: Method,
        args: Array<Any?>?,
        requestObj: Any,
        uriFieldName: String,
        uri: URI
    ): Boolean {
        if (!state.enableConfirmRefresh) return false
        val surface = refreshSurfaceForUri(uri) ?: return false
        val foreground = isRefreshSurfaceForeground(surface)
        if (!foreground && isClearlyBackgroundRefresh(surface)) {
            logInfo("Silently blocked background $surface refresh ${uri.path}")
            return true
        }
        if (!foreground) return false
        if (confirmRefreshUiGuardHookedClasses > 0) return false
        if (isRefreshAllowed(surface)) return false
        if (isRefreshDialogPending(surface)) return true
        setRefreshDialogPending(surface, true)
        val blocked = BlockedRefreshRequest(
            service = service,
            method = method,
            args = args?.copyOf() ?: emptyArray(),
            requestObj = requestObj,
            uriFieldName = uriFieldName,
            originalUri = uri,
            surface = surface
        )
        showConfirmRefreshDialog(
            surface,
            onConfirm = {
                setRefreshDialogPending(surface, false)
                allowRefreshSurface(surface, 3_000L)
                replayRefreshRequest(blocked)
            },
            onCancel = {
                setRefreshDialogPending(surface, false)
                clearVisibleRefreshSpinners(currentActivity)
            }
        )
        logInfo("Blocked pending $surface refresh ${uri.path}")
        return true
    }

    private fun refreshSurfaceForUri(uri: URI): String? {
        val path = lower(uri.path)
        return when {
            path.contains("/feed/timeline") ||
                path.contains("/feed/text_post_app_timeline") -> "feed"
            path.contains("/clips") ||
                path.contains("/mixed_media") ||
                path.contains("/feed/injected_reels_media") -> "reels"
            else -> null
        }
    }

    private fun replayRefreshRequest(request: BlockedRefreshRequest) {
        runCatching {
            setUriField(request.requestObj, request.uriFieldName, request.originalUri)
            XposedBridge.invokeOriginalMethod(request.method, request.service, request.args)
            logInfo("Replayed ${request.surface} refresh ${request.originalUri.path}")
        }.onFailure { logError("Refresh replay failed", it) }
    }

    private fun rewriteTigonUri(
        uri: URI,
        service: Any?,
        method: Method,
        args: Array<Any?>?,
        requestObj: Any,
        uriField: Field
    ): URI? {
        val reason = dropReasonForNetworkUri(uri)
        if (reason != null) {
            when (reason) {
                "dm_seen" -> recordBlockedDmSeenNetworkRequest(service, method, args, requestObj, uriField.name, uri)
                "story_seen" -> recordBlockedStorySeenNetworkRequest(service, method, args, requestObj, uriField.name, uri)
            }
            return URI("https", "127.0.0.1", "/404", "reason=$reason", null)
        }
        return null
    }

    private fun rewriteOrBlockNetworkUrl(url: String): String? {
        val clean = url
        val exactReason = runCatching { dropReasonForNetworkUri(URI(clean)) }.getOrNull()
        if (exactReason != null) {
            return "https://127.0.0.1/404?reason=$exactReason"
        }
        val lower = clean.lowercase()
        val blockReason = when {
            state.isGhostSeen && (lower.contains("mark_thread_seen") || lower.contains("/item_seen/")) -> "dm_seen"
            state.isGhostTyping && lower.contains("typing") -> "typing"
            state.isGhostStory && (lower.contains("media/seen") || lower.contains("reel_seen") || lower.contains("write_seen_state")) -> "story_seen"
            state.isGhostScreenshot && lower.contains("screenshot") -> "screenshot"
            state.isGhostViewOnce && lower.contains("visual_item_seen") -> "view_once"
            state.hideVoiceMessageSeen && lower.contains("voice_item_seen") -> "voice_seen"
            state.isGhostLive && (lower.contains("live_presence") || lower.contains("/presence/")) -> "live_presence"
            state.keepEphemeralMessages && lower.contains("mark_ephemeral_item_ranges_viewed") -> "ephemeral_seen"
            state.permanentViewMode && (lower.contains("view_mode") || lower.contains("seen_count")) -> "permanent_view"
            state.doNotSaveRecentSearches && lower.contains("recent_search") -> "recent_search"
            state.disableRepost && lower.contains("/media/create_note/") -> "repost"
            state.isAdBlockEnabled && runCatching { isAdOrSponsoredRequest(URI(clean)) }.getOrDefault(false) -> "ad"
            state.isAnalyticsBlocked && runCatching {
                val uri = URI(clean)
                val host = lower(uri.host)
                host.contains("graph.instagram.com") ||
                    host.contains("graph.facebook.com") ||
                    lower(uri.path).contains("/logging_client_events")
            }.getOrDefault(false) -> "analytics"
            else -> null
        }
        if (blockReason != null) {
            return "https://i.instagram.com/api/v1/purrfect/blocked/?reason=$blockReason"
        }
        if ((state.enableHighQualityStoryUpload || state.enableHighQualityDmUpload) && lower.contains("upload") && !lower.contains("purrfect_hq=1")) {
            return Uri.parse(clean).buildUpon()
                .appendQueryParameter("purrfect_hq", "1")
                .appendQueryParameter("quality", "100")
                .build()
                .toString()
        }
        return if (clean != url) clean else null
    }

    private fun dropReasonForNetworkUri(uri: URI): String? {
        val path = lower(uri.path)
        val query = lower(uri.query)
        val host = lower(uri.host)

        if (state.markTextsSeenAfterReply && isDirectTextSendRequest(uri)) {
            logInfo("Detected outgoing Direct text/reply while seen-after-reply is enabled: $path")
            markDmSeenAfterReply(currentActivity, currentActivity?.window?.decorView)
        }
        if (state.storyInteractionSendsSeen && isStoryInteractionRequest(uri)) {
            logInfo("Detected story interaction while story-interaction seen is enabled: $path")
            allowStorySeenUntilMs = System.currentTimeMillis() + 30_000L
            markStorySeen(currentActivity, currentActivity?.window?.decorView)
        }

        if (state.isGhostSeen && isDmSeenRequest(uri)) {
            if (consumeSeenAllowance(isStory = false)) return null
            return "dm_seen"
        }
        if (state.keepEphemeralMessages && path.contains("/mark_ephemeral_item_ranges_viewed")) return "ephemeral_seen"
        if (state.isGhostScreenshot && (path.endsWith("/screenshot/") || path.endsWith("/ephemeral_screenshot/"))) return "screenshot"
        if (state.isGhostViewOnce && (path.endsWith("/item_replayed/") || (path.contains("/direct") && path.endsWith("/item_seen/")))) return "view_once"
        if (state.isGhostStory && path.contains("/media/seen/")) {
            if (consumeSeenAllowance(isStory = true)) return null
            return "story_seen"
        }
        if (state.isGhostLive && isLivePresenceRequest(uri)) return "live_presence"
        if (state.hideVoiceMessageSeen && isVoiceSeenRequest(uri)) return "voice_seen"

        if (state.disableStories && (
                path.contains("/feed/reels_tray/") ||
                    path.contains("feed/get_latest_reel_media/") ||
                    path.contains("direct_v2/pending_inbox/?visual_message") ||
                    path.contains("stories/hallpass/") ||
                    path.contains("/api/v1/feed/reels_media_stream/")
            )
        ) return "stories"
        if (state.disableFeed && path.endsWith("/feed/timeline/")) return "feed"
        if (state.disableReels && !state.disableReelsExceptDM && (
                path.endsWith("/qp/batch_fetch/") ||
                    path.contains("api/v1/clips") ||
                    path.contains("clips") ||
                    path.contains("mixed_media") ||
                    path.contains("mixed_media/discover/stream/")
            )
        ) return "reels"
        if (state.disableReelsExceptDM && !path.startsWith("/api/v1/direct_v2/") && (
                (path.startsWith("/api/v1/clips/") && (query.contains("next_media_ids=") || query.contains("max_id="))) ||
                    path.contains("/clips/discover/") ||
                    path.contains("/mixed_media/discover/stream/")
            )
        ) return "reels"
        if (state.disableExplore && (
                path.contains("/discover/topical_explore") ||
                    path.contains("/discover/topical_explore_stream") ||
                    (host.contains("i.instagram.com") && path.contains("/api/v1/fbsearch/top_serp/"))
            )
        ) return "explore"
        if (state.disableComments && path.contains("/api/v1/media/") && path.contains("comments/")) return "comments"
        if (state.isAdBlockEnabled && isAdOrSponsoredRequest(uri)) return "ad"
        if (state.isAnalyticsBlocked && (
                host.contains("graph.instagram.com") ||
                    host.contains("graph.facebook.com") ||
                    path.contains("/logging_client_events")
            )
        ) return "analytics"
        if (state.disableRepost && path.contains("/media/create_note/")) return "repost"
        if (state.disableDiscoverPeople && (path.contains("/discover/ayml/") || path.contains("discover/chaining/"))) return "discover_people"
        if (state.doNotSaveRecentSearches && isRecentSearchPersistenceRequest(uri)) return "recent_search"
        return null
    }

    private fun isDmSeenRequest(uri: URI): Boolean {
        val path = lower(uri.path)
        val combined = "$path?${lower(uri.query)}"
        if (!combined.contains("direct")) return false
        return (path.contains("/threads/") && path.contains("/opened")) ||
            combined.contains("mark_thread_seen") ||
            combined.contains("send_thread_seen_marker") ||
            combined.contains("thread_seen_marker") ||
            (combined.contains("thread") && (combined.contains("/seen") || combined.contains("opened")))
    }

    private fun isDirectTextSendRequest(uri: URI): Boolean {
        val path = lower(uri.path)
        val combined = "$path?${lower(uri.query)}"
        if (!combined.contains("direct") && !combined.contains("notes_send_text_response")) return false
        return combined.contains("send_text_message") ||
            combined.contains("broadcast/text") ||
            combined.contains("/text/") ||
            combined.contains("text_message") ||
            combined.contains("reply_text") ||
            combined.contains("send_repost_reply_message") ||
            combined.contains("notes_send_text_response") ||
            combined.contains("send_link_message") ||
            combined.contains("broadcast/link")
    }

    private fun isStoryInteractionRequest(uri: URI): Boolean {
        val path = lower(uri.path)
        val combined = "$path?${lower(uri.query)}"
        val storySurface = combined.contains("story") || combined.contains("reel") || combined.contains("reels_media")
        if (!storySurface) return false
        return combined.contains("story_interaction") ||
            combined.contains("send_story_interaction_reply") ||
            combined.contains("story_like") ||
            combined.contains("reel/react") ||
            combined.contains("reel/reply") ||
            combined.contains("reel_comment") ||
            (path.contains("/media/") && (path.contains("/like") || path.contains("/comment")))
    }

    private fun isLivePresenceRequest(uri: URI): Boolean {
        val path = lower(uri.path)
        val query = lower(uri.query)
        if (!path.contains("/live/")) return false
        return path.contains("/heartbeat_and_get_viewer_count/") ||
            path.contains("/get_viewer_count/") ||
            path.contains("/get_viewer_list/") ||
            path.contains("/viewer_list/") ||
            path.contains("/viewer_presence/") ||
            path.contains("/live_presence/") ||
            path.contains("/broadcast_viewer/") ||
            path.contains("/presence/") ||
            query.contains("live_presence") ||
            query.contains("viewer_presence")
    }

    private fun isVoiceSeenRequest(uri: URI): Boolean {
        val combined = "${lower(uri.path)}?${lower(uri.query)}"
        if (!combined.contains("direct")) return false
        val voice = combined.contains("voice") ||
            combined.contains("audio_clip") ||
            combined.contains("audioclip") ||
            combined.contains("audio_message")
        val seen = combined.contains("seen") ||
            combined.contains("opened") ||
            combined.contains("played") ||
            combined.contains("listened") ||
            combined.contains("playback")
        return voice && seen
    }

    private fun isAdOrSponsoredRequest(uri: URI): Boolean {
        val path = lower(uri.path)
        val query = lower(uri.query)
        if (path.contains("/ads/promote/")) return false
        return path.contains("profile_ads/get_profile_ads/") ||
            path.contains("/async_ads/") ||
            path.contains("async_ads") ||
            path.contains("/feed/injected_reels_media/") ||
            path.contains("injected_story") ||
            path.contains("prefetched_ads") ||
            path.contains("/api/v1/ads/") ||
            path == "/api/v1/ads/graphql/" ||
            path.contains("/sponsored/") ||
            query.contains("is_sponsored=true") ||
            query.contains("is_ad=true") ||
            query.contains("sponsored_label")
    }

    private fun isRecentSearchPersistenceRequest(uri: URI): Boolean {
        val combined = "${lower(uri.path)}?${lower(uri.query)}"
        if (combined.contains("clear_recent_searches") ||
            combined.contains("remove_recent_search") ||
            combined.contains("delete_recent_search")
        ) return false
        return combined.contains("register_recent_search") ||
            combined.contains("recent_search_click") ||
            combined.contains("save_recent_search") ||
            combined.contains("recent_searches/register") ||
            combined.contains("map/register_recent_search") ||
            combined.contains("fbsearch/register_recent_search") ||
            (combined.contains("recent_search") && combined.contains("mutation"))
    }

    private fun consumeSeenAllowance(isStory: Boolean): Boolean {
        val now = System.currentTimeMillis()
        val until = if (isStory) allowStorySeenUntilMs else allowDmSeenUntilMs
        if (until <= now) {
            if (isStory) allowStorySeenUntilMs = 0L else allowDmSeenUntilMs = 0L
            return false
        }
        logInfo("Allowing manual ${if (isStory) "story" else "DM"} seen request")
        return true
    }

    private fun markDmSeenAfterReply(vararg roots: Any?): Boolean {
        allowManualDmSeen.set(true)
        return try {
            replayBlockedDmSeenMethod() ||
                replayBlockedNetworkRequest(lastBlockedDmSeenNetworkRequest, isStory = false) ||
                run {
                    allowDmSeenUntilMs = System.currentTimeMillis() + 30_000L
                    true
                }
        } finally {
            allowManualDmSeen.remove()
        }
    }

    private fun recordBlockedDmSeenNetworkRequest(
        service: Any?,
        method: Method,
        args: Array<Any?>?,
        requestObj: Any?,
        uriFieldName: String?,
        originalUri: URI?
    ) {
        if (service == null || requestObj == null || uriFieldName.isNullOrBlank() || originalUri == null) return
        lastBlockedDmSeenNetworkRequest = BlockedSeenNetworkRequest(
            service,
            method,
            args?.copyOf() ?: emptyArray(),
            requestObj,
            uriFieldName,
            originalUri,
            System.currentTimeMillis()
        )
    }

    private fun recordBlockedStorySeenNetworkRequest(
        service: Any?,
        method: Method,
        args: Array<Any?>?,
        requestObj: Any?,
        uriFieldName: String?,
        originalUri: URI?
    ) {
        if (service == null || requestObj == null || uriFieldName.isNullOrBlank() || originalUri == null) return
        lastBlockedStorySeenNetworkRequest = BlockedSeenNetworkRequest(
            service,
            method,
            args?.copyOf() ?: emptyArray(),
            requestObj,
            uriFieldName,
            originalUri,
            System.currentTimeMillis()
        )
    }

    private fun replayBlockedDmSeenMethod(): Boolean {
        val call = lastBlockedDmSeenMethodCall ?: return false
        if (System.currentTimeMillis() - call.atMs > 180_000L) return false
        return runCatching {
            allowDmSeenUntilMs = System.currentTimeMillis() + 30_000L
            XposedBridge.invokeOriginalMethod(call.method, call.owner, call.args)
            true
        }.getOrElse {
            logError("Failed replaying blocked DM seen method", it)
            false
        }
    }

    private fun replayBlockedNetworkRequest(request: BlockedSeenNetworkRequest?, isStory: Boolean): Boolean {
        val req = request ?: return false
        if (System.currentTimeMillis() - req.atMs > 180_000L) return false
        return runCatching {
            setUriField(req.requestObj, req.uriFieldName, req.originalUri)
            if (isStory) allowStorySeenUntilMs = System.currentTimeMillis() + 30_000L else allowDmSeenUntilMs = System.currentTimeMillis() + 30_000L
            XposedBridge.invokeOriginalMethod(req.method, req.service, req.args)
            true
        }.getOrElse {
            logError("Failed replaying blocked ${if (isStory) "story" else "DM"} seen request", it)
            false
        }
    }

    private fun setUriField(requestObj: Any, uriFieldName: String, uri: URI) {
        var cls: Class<*>? = requestObj.javaClass
        while (cls != null && cls != Any::class.java) {
            val field = runCatching { cls.getDeclaredField(uriFieldName) }.getOrNull()
            if (field != null) {
                field.isAccessible = true
                field.set(requestObj, uri)
                return
            }
            cls = cls.superclass
        }
        throw NoSuchFieldException(uriFieldName)
    }

    private fun markStorySeen(vararg roots: Any?): Boolean {
        if (storySeenMethods.isEmpty() && storySeenBuilderMethods.isEmpty() && lastBlockedStorySeenNetworkRequest == null) {
            return false
        }
        allowManualStorySeen.set(true)
        return try {
            val visited = Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
            val cachedOwner = lastBlockedStorySeenOwner?.get()
            invokeStorySeenInGraph(cachedOwner, visited, 0) ||
                roots.any { invokeStorySeenInGraph(it, visited, 0) } ||
                replayBlockedNetworkRequest(lastBlockedStorySeenNetworkRequest, isStory = true) ||
                invokeStorySeenBuilder() ||
                run {
                    allowStorySeenUntilMs = System.currentTimeMillis() + 30_000L
                    true
                }
        } finally {
            allowManualStorySeen.remove()
        }
    }

    private fun invokeStorySeenBuilder(): Boolean {
        val owner = lastSeenStoryBuilderOwner?.get() ?: return false
        val args = lastSeenStoryBuilderArgs ?: return false
        storySeenBuilderMethods.forEach { method ->
            if (!method.declaringClass.isAssignableFrom(owner.javaClass)) return@forEach
            if (method.parameterTypes.size != args.size) return@forEach
            val ok = runCatching {
                allowStorySeenUntilMs = System.currentTimeMillis() + 30_000L
                method.invoke(owner, *args)
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    private fun invokeStorySeenInGraph(value: Any?, visited: MutableSet<Any>, depth: Int): Boolean {
        if (value == null || depth > 8) return false
        if (value is Array<*>) return value.any { invokeStorySeenInGraph(it, visited, depth + 1) }
        val cls = value.javaClass
        if (cls.isArray) {
            val count = java.lang.reflect.Array.getLength(value).coerceAtMost(20)
            for (index in 0 until count) {
                if (invokeStorySeenInGraph(java.lang.reflect.Array.get(value, index), visited, depth + 1)) return true
            }
            return false
        }
        if (isSimpleOrFramework(cls) || !visited.add(value)) return false

        storySeenMethods.forEach { method ->
            if (method.declaringClass.isAssignableFrom(cls)) {
                val ok = runCatching {
                    allowStorySeenUntilMs = System.currentTimeMillis() + 30_000L
                    method.invoke(value)
                    true
                }.getOrDefault(false)
                if (ok) return true
            }
        }

        if (value is Collection<*>) {
            value.take(25).forEach { if (invokeStorySeenInGraph(it, visited, depth + 1)) return true }
            return false
        }
        if (!shouldInspectObject(cls)) return false
        var current: Class<*>? = cls
        var fields = 0
        while (current != null && current != Any::class.java && fields < 80) {
            current.declaredFields.forEach { field ->
                if (fields++ >= 80) return@forEach
                runCatching {
                    field.isAccessible = true
                    if (invokeStorySeenInGraph(field.get(value), visited, depth + 1)) return true
                }
            }
            current = current.superclass
        }
        return false
    }

    private fun isSimpleOrFramework(cls: Class<*>): Boolean {
        return cls.isPrimitive ||
            cls == String::class.java ||
            Number::class.java.isAssignableFrom(cls) ||
            cls == java.lang.Boolean::class.java ||
            cls == java.lang.Character::class.java ||
            Enum::class.java.isAssignableFrom(cls) ||
            cls.name.startsWith("android.view.") ||
            cls.name.startsWith("android.widget.") ||
            cls.name.startsWith("android.content.") ||
            cls.name.startsWith("java.lang.reflect.")
    }

    private fun handleFollowStatusRequest(uri: URI, args: Array<Any?>?) {
        val path = uri.path ?: return
        if (!path.startsWith("/api/v1/friendships/show/")) return
        val userId = path.split('/').firstOrNull { it.all(Char::isDigit) } ?: return
        currentFollowStatusUserId = userId
        args?.getOrNull(1)?.let { registerFollowStatusCallback(it, userId) }
        args?.getOrNull(2)?.let { registerFollowStatusCallback(it, userId) }
    }

    private fun registerFollowStatusCallback(callback: Any, userId: String) {
        pendingFollowCallbacks[System.identityHashCode(callback)] = userId
        val className = callback.javaClass.name
        if (!hookedFollowCallbackClasses.add(className)) return
        callback.javaClass.declaredMethods
            .filter { !Modifier.isStatic(it.modifiers) && it.parameterTypes.isNotEmpty() }
            .forEach { method ->
                runCatching {
                    method.isAccessible = true
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam<*>) {
                                if (!state.showFollowerToast) return
                                val hash = System.identityHashCode(param.thisObject)
                                val pendingUserId = pendingFollowCallbacks[hash] ?: return
                                param.args.forEach { arg ->
                                    parseFollowedBy(arg)?.let { followedBy ->
                                        pendingFollowCallbacks.remove(hash)
                                        showFollowStatusToast(pendingUserId, followedBy)
                                        return
                                    }
                                }
                                parseFollowedBy(param.thisObject)?.let { followedBy ->
                                    pendingFollowCallbacks.remove(hash)
                                    showFollowStatusToast(pendingUserId, followedBy)
                                }
                            }
                        }
                    )
                }
            }
    }

    private fun parseFollowedBy(value: Any?): Boolean? {
        val body = toJsonString(value, 3)?.takeIf { it.contains("\"followed_by\"") } ?: return null
        return runCatching { org.json.JSONObject(body).optBoolean("followed_by", false) }
            .getOrElse { body.contains("\"followed_by\":true") || body.contains("\"followed_by\": true") }
    }

    private fun toJsonString(value: Any?, depth: Int): String? {
        if (value == null || depth < 0) return null
        when (value) {
            is String -> return value.takeIf { it.contains("followed_by") }
            is ByteArray -> return runCatching { String(value, Charsets.UTF_8) }.getOrNull()?.takeIf { it.contains("followed_by") }
            is java.nio.ByteBuffer -> {
                return runCatching {
                    val duplicate = value.duplicate()
                    val bytes = ByteArray(duplicate.remaining())
                    duplicate.get(bytes)
                    String(bytes, Charsets.UTF_8)
                }.getOrNull()?.takeIf { it.contains("followed_by") }
            }
        }
        if (depth == 0) return null
        var cls: Class<*>? = value.javaClass
        var visited = 0
        while (cls != null && cls != Any::class.java && visited < 80) {
            cls.declaredFields.forEach { field ->
                if (visited++ >= 80 || Modifier.isStatic(field.modifiers) || field.type.isPrimitive) return@forEach
                runCatching {
                    field.isAccessible = true
                    val nested = field.get(value)
                    if (nested !== value && nested !is Number) toJsonString(nested, depth - 1) else null
                }.getOrNull()?.takeIf { it.contains("followed_by") }?.let { return it }
            }
            cls = cls.superclass
        }
        return null
    }

    private fun showFollowStatusToast(userId: String, followedBy: Boolean) {
        mainHandler.post {
            if (currentFollowStatusUserId != null && currentFollowStatusUserId != userId) return@post
            val status = if (followedBy) "follows you" else "does not follow you"
            Toast.makeText(androidContext, "($userId) $status", Toast.LENGTH_SHORT).show()
            currentFollowStatusUserId = null
        }
    }

    private fun requestUrl(request: Any): String? {
        val method = request.javaClass.methods.firstOrNull { it.name == "url" && it.parameterTypes.isEmpty() }
            ?: request.javaClass.methods.firstOrNull { it.name == "getUrl" && it.parameterTypes.isEmpty() }
            ?: return null
        return method.invoke(request)?.toString()
    }

    private fun buildRequestWithUrl(request: Any, url: String): Any? {
        return runCatching {
            val builder = request.javaClass.getMethod("newBuilder").invoke(request)
            val urlMethod = builder.javaClass.methods.firstOrNull {
                it.name == "url" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
            } ?: return@runCatching null
            urlMethod.invoke(builder, url)
            builder.javaClass.getMethod("build").invoke(builder)
        }.getOrNull()
    }

    private fun rememberMediaUrl(url: String) {
        if (!looksLikeMediaUrl(url)) return
        synchronized(recentMediaUrls) {
            recentMediaUrls.remove(url)
            recentMediaUrls.add(url)
            while (recentMediaUrls.size > 60) recentMediaUrls.removeAt(0)
        }
        synchronized(dmRecentUrls) {
            dmRecentUrls.removeAll { it.url == url }
            dmRecentUrls.addFirst(RecentMediaUrl(url, System.currentTimeMillis()))
            pruneDmRecentUrls()
        }
    }

    private fun pruneDmRecentUrls() {
        val cutoff = System.currentTimeMillis() - 90_000L
        while (dmRecentUrls.size > 60) dmRecentUrls.removeLast()
        while (dmRecentUrls.isNotEmpty() && dmRecentUrls.peekLast().timeMs < cutoff) dmRecentUrls.removeLast()
    }

    private fun looksLikeMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".cdninstagram.com") ||
            lower.contains(".fbcdn.net") ||
            lower.contains(".fbsbx.com") ||
            lower.contains("scontent") ||
            lower.contains(".mp4") ||
            lower.contains(".m4a") ||
            lower.contains(".aac") ||
            lower.contains(".opus") ||
            lower.contains(".ogg") ||
            lower.contains(".mp3") ||
            lower.contains(".jpg") ||
            lower.contains(".jpeg") ||
            lower.contains(".webp") ||
            lower.contains(".gif")
    }

    private fun isHttpImageUrl(url: String?): Boolean {
        val lower = url?.lowercase(Locale.US) ?: return false
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        return lower.contains("cdninstagram.com") ||
            lower.contains("fbcdn.net") ||
            lower.contains("scontent") ||
            lower.contains(".jpg") ||
            lower.contains(".jpeg") ||
            lower.contains(".png") ||
            lower.contains(".webp")
    }

    private fun looksLikeProfileImageUrl(url: String?): Boolean {
        val lower = url?.lowercase(Locale.US) ?: return false
        return (lower.contains("/t51.") && lower.contains("-19/")) ||
            (lower.contains("t51.") && lower.contains("-19")) ||
            lower.matches(Regex(".*t51\\.[0-9]+-19.*")) ||
            (lower.contains("/v/t51.") && lower.contains("-19")) ||
            lower.contains("profile_pic") ||
            lower.contains("profilepic") ||
            lower.contains("profilepicture") ||
            lower.contains("profile_picture") ||
            lower.contains("profile_photo") ||
            lower.contains("profilephoto") ||
            lower.contains("avatar") ||
            lower.contains("profile%5fpic") ||
            lower.contains("profile%2f") ||
            lower.contains("/profile/") ||
            (lower.contains("ig_cache_key") && lower.contains("profile")) ||
            lower.contains("s150x150") ||
            lower.contains("s320x320")
    }

    private fun imageUrlFromObject(value: Any?): String? {
        if (value == null) return null
        if (value is String && value.startsWith("http")) return value
        if (value is Uri && value.toString().startsWith("http")) return value.toString()
        value.toString().takeIf { it.startsWith("http") }?.let { return it }
        val visited = Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        fun scan(obj: Any?, depth: Int): String? {
            if (obj == null || depth > 2 || !visited.add(obj)) return null
            when (obj) {
                is String -> return obj.takeIf { it.startsWith("http") }
                is Uri -> return obj.toString().takeIf { it.startsWith("http") }
            }
            val cls = obj.javaClass
            cls.declaredMethods
                .filter { it.parameterTypes.isEmpty() && (it.returnType == String::class.java || it.returnType == Uri::class.java) }
                .forEach { method ->
                    runCatching {
                        method.isAccessible = true
                        scan(method.invoke(obj), depth + 1)
                    }.getOrNull()?.let { return it }
                }
            cls.declaredFields
                .filter { it.type == String::class.java || it.type == Uri::class.java || depth == 0 }
                .forEach { field ->
                    runCatching {
                        field.isAccessible = true
                        scan(field.get(obj), depth + 1)
                    }.getOrNull()?.let { return it }
                }
            return null
        }
        return scan(value, 0)
    }

    private fun resolveMediaOptionDownloadIcon(context: Context): Int {
        return runCatching {
            val optionClass = Class.forName("com.instagram.feed.media.mediaoption.MediaOption\$Option", false, context.classLoader)
            optionClass.enumConstants
                ?.firstOrNull { it.toString().contains("DOWNLOAD") }
                ?.let { option ->
                    option.javaClass.getField("iconDrawable").get(option) as? Int
                }
        }.getOrNull() ?: 0
    }

    private fun handleReelDownloadClick(activity: Activity, media: Any, controller: Any) {
        if (state.enableReelDownload) {
            AlertDialog.Builder(activity)
                .setTitle("Download")
                .setItems(arrayOf("Reel video", "Thumbnail")) { _, which ->
                    if (which == 0) startReelVideoDownload(activity, media, controller)
                    else startReelThumbnailDownload(activity, media)
                }
                .show()
            return
        }
        if (state.enableReelThumbnailDownload) startReelThumbnailDownload(activity, media)
    }

    private fun startReelVideoDownload(context: Context, media: Any, controller: Any) {
        val metadata = downloadContextFrom(media, "reel")
        bestMediaVideoUrl(media)?.let {
            enqueueDownload(it, metadata)
            return
        }

        val urls = extractAllMediaDownloadUrls(context, media)
        if (urls.isEmpty()) {
            Toast.makeText(context, "No reel media found", Toast.LENGTH_SHORT).show()
            return
        }
        val viewIndex = findCarouselIndexFromView(context, urls.size)
        val controllerIndex = findReelControllerCarouselIndex(controller, urls.size)
        showPostDownloadChoices(context, urls, metadata, if (viewIndex >= 0) viewIndex else controllerIndex)
    }

    private fun startReelThumbnailDownload(context: Context, media: Any) {
        val url = bestReelThumbnailUrl(media)
        if (url == null) {
            Toast.makeText(context, "Reel thumbnail URL not found", Toast.LENGTH_SHORT).show()
            return
        }
        enqueueDownload(url, downloadContextFrom(media, "reel_thumbnail"))
    }

    private fun bestReelThumbnailUrl(media: Any): String? {
        val urls = collectCdnUrls(media).distinct()
        return urls
            .filterNot(::isVideoMediaUrl)
            .filterNot(::looksLikeProfileImageUrl)
            .maxWithOrNull(compareBy<String> { parseCdnArea(it) }.thenBy { it.length })
            ?: urls.firstOrNull { !isVideoMediaUrl(it) && !looksLikeProfileImageUrl(it) }
    }

    private fun extractAllMediaDownloadUrls(context: Context, media: Any): List<String> {
        initMediaDownloadReflection()
        bestMediaVideoUrl(media)?.let { return listOf(it) }

        val dictClass = mutableMediaDictIntfClass
        val dict = findAssignableInObjectGraph(media, dictClass)
        if (dict != null && mediaCarouselCandidates.isNotEmpty()) {
            mediaCarouselCandidates.forEach { candidate ->
                val items = runCatching { candidate.invoke(dict) as? List<*> }.getOrNull().orEmpty()
                if (items.size < 2) return@forEach
                val videoClass = storyVideoVersionIntfClass
                if (videoClass != null && videoClass.isInstance(items.firstOrNull())) return@forEach
                val carouselUrls = items.mapNotNull { item ->
                    item ?: return@mapNotNull null
                    bestMediaVideoUrl(item)
                        ?: imageUrlFromMedia(context, item)
                        ?: probeCdnUrlViaStringMethods(item)
                        ?: collectCdnUrls(item)
                            .filterNot(::isVideoMediaUrl)
                            .let(::pickBestImageMediaUrl)
                }
                if (carouselUrls.size >= 2) return carouselUrls.distinct()
            }
        }

        imageUrlFromMedia(context, media)?.let { return listOf(it) }
        val fallback = collectCdnUrls(media).distinct()
        return when {
            fallback.isEmpty() -> emptyList()
            fallback.any(::isVideoMediaUrl) -> listOf(fallback.first(::isVideoMediaUrl))
            else -> listOf(pickBestImageMediaUrl(fallback) ?: fallback.first())
        }
    }

    private fun imageUrlFromMedia(context: Context, media: Any): String? {
        val mediaType = mediaClass
        val method = mediaImageUrlMethod
        if (mediaType == null || method == null || !mediaType.isInstance(media)) return null
        return runCatching { method.invoke(null, context, media) as? String }
            .getOrNull()
            ?.takeIf { looksLikeMediaUrl(it) && !isVideoMediaUrl(it) }
    }

    private fun applyFeedPostDownloadControls(view: View) {
        if (!state.enablePostDownload) return
        val name = resourceEntryName(view).orEmpty()
        val isFeedLike = name == "row_feed_button_like"
        val isReelLike = name == "like_button" && hasAncestorNamed(view, "clips_ufi_component")
        if (!isFeedLike && !isReelLike) return

        val parent = view.parent as? ViewGroup ?: return
        val snapshot = recentMediaUrlsSince(System.currentTimeMillis() - 10_000L)
        if (isFeedLike) {
            val existing = parent.findViewWithTag<View>(FEED_DOWNLOAD_BUTTON_TAG)
            if (existing != null) {
                feedDownloadButtonUrls[existing] = snapshot
                installFeedLikeLongPress(view, existing)
                return
            }
            injectFeedDownloadButton(view, parent, snapshot)
        } else {
            installReelLikePostDownloadLongPress(view)
        }
    }

    private fun injectFeedDownloadButton(likeButton: View, parent: ViewGroup, snapshot: List<String>) {
        val context = likeButton.context ?: return
        val button = ImageButton(context).apply {
            tag = FEED_DOWNLOAD_BUTTON_TAG
            setImageResource(android.R.drawable.stat_sys_download)
            setColorFilter(Color.WHITE)
            background = null
            contentDescription = "Download media"
            val size = dp(34)
            layoutParams = if (parent is LinearLayout) {
                LinearLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setMargins(dp(4), 0, dp(4), 0)
                }
            } else {
                FrameLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER_VERTICAL or Gravity.END
                    setMargins(0, 0, dp(8), 0)
                }
            }
            setOnClickListener { handleFeedPostDownload(likeButton, this) }
        }
        feedDownloadButtonUrls[button] = snapshot
        installFeedLikeLongPress(likeButton, button)
        parent.post {
            if (button.parent == null && parent.findViewWithTag<View>(FEED_DOWNLOAD_BUTTON_TAG) == null) {
                runCatching {
                    parent.addView(button)
                    button.bringToFront()
                }.onFailure { logError("Feed download button injection failed", it) }
            }
        }
    }

    private fun installFeedLikeLongPress(likeButton: View, downloadButton: View) {
        likeButton.isLongClickable = true
        likeButton.setOnLongClickListener {
            handleFeedPostDownload(likeButton, downloadButton)
            true
        }
    }

    private fun installReelLikePostDownloadLongPress(likeButton: View) {
        likeButton.isLongClickable = true
        likeButton.setOnLongClickListener { view ->
            if (!state.enablePostDownload) return@setOnLongClickListener false
            val taggedMedia = runCatching { view.getTag(TAG_REEL_MEDIA) }.getOrNull()
            if (taggedMedia != null) {
                bestMediaVideoUrl(taggedMedia)?.let { url ->
                    enqueueDownload(url, downloadContextFrom(taggedMedia, "reel"))
                    return@setOnLongClickListener true
                }
            }
            val all = recentMediaUrlsSince(System.currentTimeMillis() - 60_000L)
            val m86 = all.filter { it.contains("/m86/") || it.contains("%2Fm86%2F", ignoreCase = true) }
            val pick = (m86.ifEmpty { all }).firstOrNull()
            if (pick == null) {
                Toast.makeText(view.context, "No reel URL found. Scroll a bit and try again.", Toast.LENGTH_SHORT).show()
            } else {
                enqueueDownload(pick, DownloadMetadata(type = "reel"))
            }
            true
        }
    }

    private fun handleFeedPostDownload(anchor: View, downloadButton: View?) {
        val context = anchor.context ?: currentActivity ?: androidContext
        val media = resolveFeedControlMedia(anchor)
        val urls = when {
            media != null -> extractAllMediaDownloadUrls(context, media)
            downloadButton != null -> feedDownloadButtonUrls[downloadButton].orEmpty()
            else -> emptyList()
        }.ifEmpty {
            recentMediaUrlsSince(System.currentTimeMillis() - 30_000L)
        }.filterNot(::looksLikeProfileImageUrl).distinct()

        if (urls.isEmpty()) {
            Toast.makeText(context, "No media found for post", Toast.LENGTH_SHORT).show()
            return
        }
        showPostDownloadChoices(context, urls, downloadContextFrom(media ?: anchor, "post"), 0)
    }

    private fun resolveFeedControlMedia(anchor: View): Any? {
        findMediaObject(getViewOnClickListener(anchor))?.let { return it }
        val saveButton = findFeedSaveButtonNear(anchor)
        findMediaObject(getViewOnClickListener(saveButton))?.let { return it }
        return null
    }

    private fun findFeedSaveButtonNear(anchor: View): View? {
        var parent = anchor.parent
        repeat(4) {
            val group = parent as? ViewGroup ?: return@repeat
            findDescendantByResourceName(group, "row_feed_button_save", 5)?.let { return it }
            parent = group.parent
        }
        return null
    }

    private fun findDescendantByResourceName(root: View, name: String, maxDepth: Int): View? {
        if (resourceEntryName(root) == name) return root
        if (root !is ViewGroup || maxDepth <= 0) return null
        val count = root.childCount.coerceAtMost(80)
        for (index in 0 until count) {
            findDescendantByResourceName(root.getChildAt(index), name, maxDepth - 1)?.let { return it }
        }
        return null
    }

    private fun getViewOnClickListener(view: View?): Any? {
        view ?: return null
        return runCatching {
            val getListenerInfo = View::class.java.getDeclaredMethod("getListenerInfo").apply { isAccessible = true }
            val listenerInfo = getListenerInfo.invoke(view) ?: return@runCatching null
            val field = listenerInfo.javaClass.getDeclaredField("mOnClickListener").apply { isAccessible = true }
            field.get(listenerInfo)
        }.getOrNull()
    }

    private fun recentMediaUrlsSince(sinceMs: Long): List<String> {
        synchronized(dmRecentUrls) {
            pruneDmRecentUrls()
            return dmRecentUrls
                .filter { it.timeMs >= sinceMs && looksLikeMediaUrl(it.url) && !looksLikeProfileImageUrl(it.url) }
                .map { it.url }
                .distinct()
        }
    }

    private fun hasAncestorNamed(view: View, name: String): Boolean {
        var current: View? = view
        repeat(8) {
            if (resourceEntryName(current ?: return@repeat) == name) return true
            current = current?.parent as? View
        }
        return false
    }

    private fun bestMediaVideoUrl(media: Any): String? {
        val urls = linkedSetOf<String>()
        collectVideoVersionUrls(media, urls, Collections.newSetFromMap(IdentityHashMap()), 0)
        return urls.firstOrNull { it.contains("/m86/") || it.contains("%2Fm86%2F") }
            ?: urls.firstOrNull()
    }

    private fun collectVideoVersionUrls(value: Any?, out: MutableSet<String>, visited: MutableSet<Any>, depth: Int) {
        if (value == null || depth > 5 || !visited.add(value)) return
        val videoClass = storyVideoVersionIntfClass ?: return
        val getUrl = storyVideoVersionGetUrl ?: return
        if (videoClass.isInstance(value)) {
            runCatching { getUrl.invoke(value) as? String }
                .getOrNull()
                ?.takeIf { looksLikeMediaUrl(it) }
                ?.let(out::add)
            return
        }
        if (!shouldInspectObject(value.javaClass)) return
        when (value) {
            is Iterable<*> -> {
                value.forEach { collectVideoVersionUrls(it, out, visited, depth + 1) }
                return
            }
        }
        if (value.javaClass.isArray) {
            val count = java.lang.reflect.Array.getLength(value).coerceAtMost(40)
            for (index in 0 until count) collectVideoVersionUrls(java.lang.reflect.Array.get(value, index), out, visited, depth + 1)
            return
        }
        var cls: Class<*>? = value.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                if (field.type.isPrimitive || Modifier.isStatic(field.modifiers)) return@forEach
                runCatching {
                    field.isAccessible = true
                    collectVideoVersionUrls(field.get(value), out, visited, depth + 1)
                }
            }
            cls = cls.superclass
        }
    }

    private fun findAssignableInObjectGraph(root: Any?, type: Class<*>?, depth: Int = 0, visited: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())): Any? {
        if (root == null || type == null || depth > 4 || !visited.add(root)) return null
        if (type.isInstance(root)) return root
        if (!shouldInspectObject(root.javaClass)) return null
        if (root is Iterable<*>) {
            root.forEach { findAssignableInObjectGraph(it, type, depth + 1, visited)?.let { found -> return found } }
            return null
        }
        if (root.javaClass.isArray) {
            val count = java.lang.reflect.Array.getLength(root).coerceAtMost(30)
            for (index in 0 until count) {
                findAssignableInObjectGraph(java.lang.reflect.Array.get(root, index), type, depth + 1, visited)?.let { return it }
            }
            return null
        }
        var cls: Class<*>? = root.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { field ->
                if (field.type.isPrimitive || field.type == String::class.java || Modifier.isStatic(field.modifiers)) return@forEach
                runCatching {
                    field.isAccessible = true
                    findAssignableInObjectGraph(field.get(root), type, depth + 1, visited)
                }.getOrNull()?.let { return it }
            }
            cls = cls.superclass
        }
        return null
    }

    private fun probeCdnUrlViaStringMethods(value: Any): String? {
        var cls: Class<*>? = value.javaClass
        while (cls != null && cls != Any::class.java) {
            if (!shouldInspectObject(cls)) break
            cls.declaredMethods
                .filter { it.parameterTypes.isEmpty() && it.returnType == String::class.java }
                .forEach { method ->
                    runCatching {
                        method.isAccessible = true
                        method.invoke(value) as? String
                    }.getOrNull()?.takeIf { looksLikeMediaUrl(it) }?.let { return it }
                }
            cls = cls.superclass
        }
        return null
    }

    private fun pickBestImageMediaUrl(urls: List<String>): String? {
        return urls.firstOrNull { url ->
            !url.contains("/s150x") &&
                !url.contains("/s240x") &&
                !url.contains("/s320x") &&
                !url.contains("/s480x") &&
                !url.contains("/s640x") &&
                !url.contains("_s.jpg")
        } ?: urls.maxByOrNull(::parseCdnArea)
    }

    private fun isVideoMediaUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return mimeTypeForUrl(url).startsWith("video") ||
            lower.contains("t50.") ||
            lower.contains("/o1/") ||
            lower.contains("/m86/") ||
            lower.contains("%2fm86%2f")
    }

    private fun showPostDownloadChoices(context: Context, urls: List<String>, metadata: DownloadMetadata, currentIndex: Int) {
        val choices = urls.distinct()
        if (choices.isEmpty()) {
            Toast.makeText(context, "No media found for post", Toast.LENGTH_SHORT).show()
            return
        }
        if (choices.size == 1) {
            enqueueDownload(choices.first(), metadata)
            return
        }
        val safeIndex = currentIndex.takeIf { it in choices.indices } ?: 0
        val activity = findActivity(context) ?: currentActivity
        if (activity == null) {
            enqueueDownload(choices[safeIndex], metadata)
            return
        }
        showCarouselDownloadBottomSheet(activity, choices, metadata, safeIndex)
    }

    private fun showCarouselDownloadBottomSheet(context: Context, urls: List<String>, metadata: DownloadMetadata, safeIndex: Int) {
        runCatching {
            val density = context.resources.displayMetrics.density
            val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val sheetBg = if (dark) Color.rgb(28, 28, 30) else Color.rgb(242, 242, 247)
            val textPrimary = if (dark) Color.WHITE else Color.rgb(28, 28, 30)
            val textSecondary = if (dark) Color.rgb(174, 174, 178) else Color.rgb(108, 108, 112)
            val accentBg = Color.rgb(10, 132, 255)
            val secondaryBg = if (dark) Color.rgb(58, 58, 60) else Color.rgb(229, 229, 234)
            val secondaryText = if (dark) Color.WHITE else Color.rgb(28, 28, 30)
            val handleColor = if (dark) Color.rgb(72, 72, 74) else Color.rgb(199, 199, 204)

            val dialog = Dialog(context)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            val sheet = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedDrawable(sheetBg, 20f)
                setPadding((20 * density).roundToInt(), (12 * density).roundToInt(), (20 * density).roundToInt(), (28 * density).roundToInt())
            }
            sheet.addView(View(context).apply {
                background = roundedDrawable(handleColor, 2f)
            }, LinearLayout.LayoutParams((40 * density).roundToInt(), (4 * density).roundToInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (16 * density).roundToInt()
            })
            sheet.addView(TextView(context).apply {
                text = "Download"
                setTextColor(textPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (4 * density).roundToInt()
            })
            sheet.addView(TextView(context).apply {
                text = "${urls.size} items in this carousel"
                setTextColor(textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (14 * density).roundToInt()
            })
            sheet.addView(makeDownloadPillButton(context, "Download current (${safeIndex + 1}/${urls.size})", accentBg, Color.WHITE, density).apply {
                setOnClickListener {
                    dialog.dismiss()
                    enqueueDownload(urls[safeIndex], metadata)
                }
            })
            sheet.addView(makeDownloadPillButton(context, "Download all (${urls.size})", secondaryBg, secondaryText, density).apply {
                setOnClickListener {
                    dialog.dismiss()
                    urls.forEach { enqueueDownload(it, metadata) }
                }
            })
            dialog.setContentView(sheet)
            dialog.show()
            dialog.window?.let { window ->
                window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                window.setGravity(Gravity.BOTTOM)
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
                val attrs = window.attributes
                val margin = (12 * density).roundToInt()
                attrs.x = margin
                attrs.y = margin
                window.attributes = attrs
            }
        }.onFailure {
            AlertDialog.Builder(context)
                .setTitle("Download")
                .setItems(arrayOf("Download current (${safeIndex + 1}/${urls.size})", "Download all (${urls.size})")) { _, which ->
                    if (which == 0) enqueueDownload(urls[safeIndex], metadata) else urls.forEach { url -> enqueueDownload(url, metadata) }
                }
                .show()
        }
    }

    private fun makeDownloadPillButton(context: Context, label: String, bgColor: Int, textColor: Int, density: Float): Button {
        return Button(context).apply {
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedDrawable(bgColor, 14f)
            setAllCaps(false)
            setPadding((20 * density).roundToInt(), (14 * density).roundToInt(), (20 * density).roundToInt(), (14 * density).roundToInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * density).roundToInt()
            }
        }
    }

    private fun roundedDrawable(color: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * androidContext.resources.displayMetrics.density
        }
    }

    private fun findObjectCarouselIndex(value: Any?, count: Int): Int {
        if (value == null || count <= 1) return 0
        fun readIndex(field: Field): Int? {
            if (field.type != java.lang.Integer.TYPE) return null
            return runCatching {
                field.isAccessible = true
                field.getInt(value).takeIf { it in 0 until count }
            }.getOrNull()
        }
        value.javaClass.declaredFields.firstOrNull { it.name == "A00" }?.let { readIndex(it)?.let { index -> return index } }
        value.javaClass.declaredFields.forEach { field ->
            readIndex(field)?.takeIf { it > 0 }?.let { return it }
        }
        return 0
    }

    private fun findReelControllerCarouselIndex(controller: Any?, count: Int): Int {
        if (controller == null || count <= 1) return 0
        var best: Int? = null
        var cls: Class<*>? = controller.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.forEach { outer ->
                if (outer.type.isPrimitive || !shouldInspectObject(outer.type)) return@forEach
                val nested = runCatching {
                    outer.isAccessible = true
                    outer.get(controller)
                }.getOrNull() ?: return@forEach
                val ints = nested.javaClass.declaredFields.filter { it.type == java.lang.Integer.TYPE }
                if (ints.size != 1) return@forEach
                val index = runCatching {
                    ints.first().isAccessible = true
                    ints.first().getInt(nested)
                }.getOrNull() ?: return@forEach
                if (index in 0 until count && (best == null || index < best!!)) best = index
            }
            cls = cls.superclass
        }
        return best ?: 0
    }

    private fun findCarouselIndexFromView(context: Context, count: Int): Int {
        if (count <= 1) return 0
        val root = (findActivity(context) ?: currentActivity)?.window?.decorView ?: return -1
        fun adapterCount(view: View): Int {
            val adapter = runCatching { view.javaClass.getMethod("getAdapter").invoke(view) }.getOrNull() ?: return -1
            return listOf("getItemCount", "getCount").firstNotNullOfOrNull { name ->
                runCatching { adapter.javaClass.getMethod(name).invoke(adapter) as? Int }.getOrNull()
            } ?: -1
        }
        fun scan(view: View): Int {
            val className = view.javaClass.name
            if ((className.contains("ViewPager") || className.contains("RecyclerView")) && adapterCount(view) == count) {
                listOf("getCurrentItem", "getCurrentDataIndex", "getCurrentWrappedDataIndex", "getCurrentRawDataIndex").forEach { name ->
                    runCatching { view.javaClass.getMethod(name).invoke(view) as? Int }.getOrNull()?.let { index ->
                        if (index >= 0) return index
                    }
                }
                if (className.contains("RecyclerView")) {
                    val layout = runCatching { view.javaClass.getMethod("getLayoutManager").invoke(view) }.getOrNull()
                    if (layout != null) {
                        val horizontal = runCatching { layout.javaClass.getMethod("getOrientation").invoke(layout) as? Int }
                            .getOrNull()
                            ?.let { it == 0 }
                            ?: true
                        if (horizontal) {
                            listOf("findFirstCompletelyVisibleItemPosition", "findFirstVisibleItemPosition").forEach { name ->
                                runCatching { layout.javaClass.getMethod(name).invoke(layout) as? Int }.getOrNull()?.let { index ->
                                    if (index >= 0) return index
                                }
                            }
                        }
                    }
                }
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    val found = scan(view.getChildAt(index))
                    if (found >= 0) return found
                }
            }
            return -1
        }
        return scan(root)
    }

    private fun showDownloadChoices(context: Context, urls: List<String>, metadata: DownloadMetadata = DownloadMetadata()) {
        if (urls.size == 1) {
            enqueueDownload(urls.first(), metadata)
            return
        }
        val activity = findActivity(context) ?: currentActivity
        if (activity == null) {
            enqueueDownload(urls.first(), metadata)
            return
        }
        val labels = urls.mapIndexed { index, url ->
            val type = if (mimeTypeForUrl(url).startsWith("video")) "Video" else "Image"
            "$type ${index + 1}"
        }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("Download")
            .setItems(labels) { _, which -> enqueueDownload(urls[which.coerceIn(urls.indices)], metadata) }
            .show()
    }

    private fun downloadContextFrom(root: Any?, type: String): DownloadMetadata {
        return DownloadMetadata(
            username = findUsernameInObjectGraph(root),
            mediaId = findMediaIdInObjectGraph(root),
            type = type
        )
    }

    private fun findUsernameInObjectGraph(root: Any?): String? {
        val visited = Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        fun scan(value: Any?, depth: Int): String? {
            if (value == null || depth > 5 || !visited.add(value)) return null
            if (value.javaClass.name == "com.instagram.user.model.User" || userClass?.isInstance(value) == true) {
                extractUsernameFromUser(value)?.let { return it }
            }
            findUsernameOnModel(value)?.let { return it }
            listOf("getUser", "A0C", "A0r", "BKR").forEach { methodName ->
                val user = invokeNoArgDeep(value, methodName)
                if (user != null && user !== value) scan(user, depth + 1)?.let { return it }
            }
            if (!shouldInspectObject(value.javaClass)) return null
            if (value is Iterable<*>) {
                value.take(20).forEach { scan(it, depth + 1)?.let { username -> return username } }
            }
            if (value.javaClass.isArray) {
                val count = java.lang.reflect.Array.getLength(value).coerceAtMost(20)
                for (i in 0 until count) scan(java.lang.reflect.Array.get(value, i), depth + 1)?.let { return it }
            }
            var cls: Class<*>? = value.javaClass
            var fields = 0
            while (cls != null && cls != Any::class.java && fields < 80) {
                cls.declaredFields.forEach { field ->
                    if (fields++ >= 80) return@forEach
                    if (field.type.isPrimitive || Modifier.isStatic(field.modifiers)) return@forEach
                    runCatching {
                        field.isAccessible = true
                        scan(field.get(value), depth + 1)
                    }.getOrNull()?.let { return it }
                }
                cls = cls.superclass
            }
            return null
        }
        return normalizeUsername(scan(root, 0)).takeIf { it.isNotBlank() }
    }

    private fun findMediaIdInObjectGraph(root: Any?): String? {
        val visited = Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        fun normalizeId(value: Any?): String? {
            val raw = value?.toString()?.trim().orEmpty()
            if (raw.isBlank()) return null
            val clean = raw.substringBefore('_')
            return clean.takeIf { it.length in 3..80 && it.matches(Regex("[A-Za-z0-9_-]+")) }
        }
        fun scan(value: Any?, depth: Int): String? {
            if (value == null || depth > 4 || !visited.add(value)) return null
            listOf("getId", "getPk", "A0Y", "A0W").forEach { methodName ->
                normalizeId(invokeNoArgDeep(value, methodName))?.let { return it }
            }
            if (!shouldInspectObject(value.javaClass)) return null
            var cls: Class<*>? = value.javaClass
            var fields = 0
            while (cls != null && cls != Any::class.java && fields < 70) {
                cls.declaredFields.forEach { field ->
                    if (fields++ >= 70) return@forEach
                    if (Modifier.isStatic(field.modifiers)) return@forEach
                    val name = field.name.lowercase(Locale.US)
                    runCatching {
                        field.isAccessible = true
                        val fieldValue = field.get(value)
                        if ((name.contains("id") || name.contains("pk")) && (fieldValue is String || fieldValue is Number)) {
                            normalizeId(fieldValue)?.let { return it }
                        }
                        if (!field.type.isPrimitive) scan(fieldValue, depth + 1)?.let { return it }
                    }
                }
                cls = cls.superclass
            }
            return null
        }
        return scan(root, 0)
    }

    private fun findContextInObjectGraph(root: Any?): Context? {
        val visited = Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        fun scan(value: Any?, depth: Int): Context? {
            if (value == null || depth > 4 || !visited.add(value)) return null
            if (value is Context) return value
            if (!shouldInspectObject(value.javaClass)) return null
            var cls: Class<*>? = value.javaClass
            var fields = 0
            while (cls != null && cls != Any::class.java && fields < 80) {
                cls.declaredFields.forEach { field ->
                    if (fields++ >= 80) return@forEach
                    if (field.type.isPrimitive) return@forEach
                    runCatching {
                        field.isAccessible = true
                        scan(field.get(value), depth + 1)
                    }.getOrNull()?.let { return it }
                }
                cls = cls.superclass
            }
            return null
        }
        return scan(root, 0)
    }

    private fun findMediaObject(root: Any?): Any? {
        val visited = Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        fun scan(value: Any?, depth: Int): Any? {
            if (value == null || depth > 4 || !visited.add(value)) return null
            if (value.javaClass.name == "com.instagram.feed.media.Media") return value
            if (!shouldInspectObject(value.javaClass)) return null
            if (value is Iterable<*>) {
                value.take(24).forEach { item -> scan(item, depth + 1)?.let { return it } }
            }
            if (value.javaClass.isArray) {
                val count = java.lang.reflect.Array.getLength(value).coerceAtMost(24)
                for (i in 0 until count) scan(java.lang.reflect.Array.get(value, i), depth + 1)?.let { return it }
            }
            var cls: Class<*>? = value.javaClass
            var fields = 0
            while (cls != null && cls != Any::class.java && fields < 90) {
                cls.declaredFields.forEach { field ->
                    if (fields++ >= 90) return@forEach
                    if (field.type.isPrimitive) return@forEach
                    runCatching {
                        field.isAccessible = true
                        scan(field.get(value), depth + 1)
                    }.getOrNull()?.let { return it }
                }
                cls = cls.superclass
            }
            return null
        }
        return scan(root, 0)
    }

    private fun collectCdnUrls(root: Any?): List<String> {
        val output = linkedSetOf<String>()
        val visited = Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        fun scan(value: Any?, depth: Int) {
            if (value == null || depth > 5 || output.size >= 40) return
            when (value) {
                is String -> {
                    urlPattern.matcher(value).let { matcher ->
                        while (matcher.find()) {
                            val url = matcher.group()
                            if (looksLikeMediaUrl(url)) output += url
                        }
                    }
                    if (looksLikeMediaUrl(value)) output += value
                    return
                }
                is Uri -> {
                    value.toString().takeIf { looksLikeMediaUrl(it) }?.let { output += it }
                    return
                }
                is Iterable<*> -> {
                    value.take(40).forEach { scan(it, depth + 1) }
                    return
                }
            }
            val cls = value.javaClass
            if (cls.isArray) {
                val count = java.lang.reflect.Array.getLength(value).coerceAtMost(40)
                for (i in 0 until count) scan(java.lang.reflect.Array.get(value, i), depth + 1)
                return
            }
            if (!visited.add(value) || !shouldInspectObject(cls)) return
            cls.declaredMethods
                .filter { it.parameterTypes.isEmpty() && !it.returnType.isPrimitive && it.returnType != java.lang.Void.TYPE }
                .take(40)
                .forEach { method ->
                    runCatching {
                        method.isAccessible = true
                        scan(method.invoke(value), depth + 1)
                    }
                }
            var current: Class<*>? = cls
            var fields = 0
            while (current != null && current != Any::class.java && fields < 100) {
                current.declaredFields.forEach { field ->
                    if (fields++ >= 100) return@forEach
                    if (field.type.isPrimitive) return@forEach
                    runCatching {
                        field.isAccessible = true
                        scan(field.get(value), depth + 1)
                    }
                }
                current = current.superclass
            }
        }
        scan(root, 0)
        return output.toList()
    }

    private fun shouldInspectObject(cls: Class<*>): Boolean {
        val name = cls.name
        return name.startsWith("X.") ||
            name.startsWith("p000X.") ||
            name.startsWith("com.instagram.") ||
            name.startsWith("com.facebook.") ||
            name.startsWith("java.util.")
    }

    private fun extractUrlFromView(view: View): String? {
        val candidates = mutableListOf<String>()
        candidates += view.contentDescription?.toString().orEmpty()
        candidates += (view as? TextView)?.text?.toString().orEmpty()
        candidates += view.tag?.toString().orEmpty()
        candidates += runCatching { view.getTag(android.R.id.text1)?.toString().orEmpty() }.getOrDefault("")
        candidates.forEach { text ->
            val matcher = urlPattern.matcher(text)
            if (matcher.find()) return matcher.group()
        }
        return null
    }

    private fun findTextForCopy(view: View): String? {
        if (view is TextView) return view.text?.toString()?.takeIf { it.isNotBlank() }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findTextForCopy(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun shouldMakeCopyable(textView: TextView, raw: String): Boolean {
        if (raw.length < 12) return false
        val id = resourceEntryName(textView).orEmpty().lowercase()
        return (state.enableCopyComment && (id.contains("comment") || raw.split(" ").size >= 3)) ||
            (state.enableCopyBio && (id.contains("bio") || id.contains("profile")))
    }

    private fun applyCustomEmojiFont(textView: TextView) {
        if (!state.customEmojiFontEnabled) return
        val text = textView.text ?: return
        val wrapped = InstagramCustomEmojiFontHooks.wrapEmojiText(text) ?: return
        if (wrapped !== text) textView.text = wrapped
    }

    private fun rewriteRelativeDate(textView: TextView, raw: String) {
        val clean = raw.trim()
        if (clean.isEmpty() || clean.length > 32 || clean.contains('\n')) return
        if (!dateSurfaceAllowed(textView)) return
        val date = parseInstagramRelativeDate(clean) ?: return
        val formatted = runCatching {
            SimpleDateFormat(state.customDateFormat.ifBlank { "yyyy-MM-dd HH:mm" }, Locale.getDefault()).format(date)
        }.getOrElse {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)
        }
        if (formatted != raw) textView.text = formatted
    }

    private fun parseInstagramRelativeDate(raw: String): Date? {
        if (Regex("^(now|just now|moments ago)$", RegexOption.IGNORE_CASE).matches(raw)) return Date()
        val match = Regex(
            "^(?:about\\s+)?(\\d+)\\s*(s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days|w|wk|wks|week|weeks|mo|mon|mons|month|months|y|yr|yrs|year|years)(?:\\s+ago)?$",
            RegexOption.IGNORE_CASE
        ).matchEntire(raw) ?: return null
        val amount = match.groupValues[1].toIntOrNull() ?: return null
        val unit = match.groupValues[2].lowercase(Locale.US)
        return Calendar.getInstance().apply {
            when {
                unit == "s" || unit.startsWith("sec") -> add(Calendar.SECOND, -amount)
                unit == "m" || unit.startsWith("min") -> add(Calendar.MINUTE, -amount)
                unit.startsWith("h") -> add(Calendar.HOUR_OF_DAY, -amount)
                unit.startsWith("d") -> add(Calendar.DAY_OF_YEAR, -amount)
                unit.startsWith("w") -> add(Calendar.WEEK_OF_YEAR, -amount)
                unit.startsWith("mo") || unit.startsWith("mon") -> add(Calendar.MONTH, -amount)
                unit.startsWith("y") -> add(Calendar.YEAR, -amount)
                else -> return null
            }
        }.time
    }

    private fun dateSurfaceAllowed(textView: TextView): Boolean {
        val surface = buildString {
            var current: View? = textView
            repeat(10) {
                val node = current ?: return@repeat
                append(' ').append(resourceEntryName(node).orEmpty())
                append(' ').append(node.javaClass.name)
                current = node.parent as? View
            }
            var context: Context? = textView.context
            repeat(8) {
                val ctx = context ?: return@repeat
                append(' ').append(ctx.javaClass.name)
                context = (ctx as? ContextWrapper)?.baseContext
            }
        }.lowercase(Locale.US)
        return when {
            surface.contains("comment") || surface.contains("ufi") -> state.customDateFormatComments
            surface.contains("direct") || surface.contains("inbox") || surface.contains("thread") ||
                surface.contains("message") || surface.contains("mailbox") -> state.customDateFormatDirect
            surface.contains("story") || surface.contains("reel_tray") || surface.contains("tray_item") -> state.customDateFormatStories
            surface.contains("clips") || surface.contains("reels") || surface.contains("reel_viewer") -> state.customDateFormatReels
            else -> state.customDateFormatFeed
        }
    }

    private fun buildSelector(view: View, root: View = view.rootView ?: view): String? {
        if (view == root) return null
        val anchor = findNearestNamedAncestor(view, root) as? ViewGroup ?: return null
        val anchorId = resourceEntryName(anchor)?.takeIf { it.isNotBlank() } ?: return null
        val indexes = mutableListOf<Int>()
        val classes = mutableListOf<String>()
        var current: View? = view
        while (current != null && current != anchor) {
            val parent = current.parent as? ViewGroup ?: return null
            val index = parent.indexOfChild(current)
            if (index < 0) return null
            indexes += index
            classes += current.javaClass.name
            current = parent
        }
        if (indexes.isEmpty()) return null
        indexes.reverse()
        classes.reverse()
        return "selector:v1|anchor=${escapeSelectorPart(anchorId)}|indexes=${indexes.joinToString("/")}|classes=${escapeSelectorPart(classes.joinToString("/"))}"
    }

    private fun selectorMatches(view: View, selector: String): Boolean {
        val parsed = parseAnchoredSelector(selector) ?: return false
        if (parsed.classes.lastOrNull() != view.javaClass.name) return false
        var current: View = view
        for (i in parsed.indexes.indices.reversed()) {
            val parent = current.parent as? ViewGroup ?: return false
            if (parent.indexOfChild(current) != parsed.indexes[i]) return false
            if (parsed.classes.getOrNull(i) != current.javaClass.name) return false
            current = parent
        }
        return resourceEntryName(current) == parsed.anchorId
    }

    private fun parseAnchoredSelector(selector: String): ParsedSelector? {
        val clean = selector.trim()
        if (!clean.startsWith("selector:v1|")) return null
        var anchor = ""
        var indexes = ""
        var classes = ""
        clean.removePrefix("selector:v1|").split("|").forEach { part ->
            val equals = part.indexOf('=')
            if (equals <= 0) return@forEach
            val key = part.substring(0, equals)
            val value = unescapeSelectorPart(part.substring(equals + 1))
            when (key) {
                "anchor" -> anchor = value
                "indexes" -> indexes = value
                "classes" -> classes = value
            }
        }
        if (anchor.isBlank() || indexes.isBlank() || classes.isBlank()) return null
        val parsedIndexes = indexes.split("/").mapNotNull { it.toIntOrNull() }
        val parsedClasses = classes.split("/")
        if (parsedIndexes.isEmpty() || parsedIndexes.size != parsedClasses.size || parsedClasses.any { it.isBlank() }) return null
        return ParsedSelector(anchor, parsedIndexes, parsedClasses)
    }

    private fun selectorDisplayName(selector: String): String {
        val parsed = parseAnchoredSelector(selector) ?: return selector
        val leaf = parsed.classes.lastOrNull()?.substringAfterLast('.') ?: "View"
        return "Captured $leaf under #${parsed.anchorId}\n${parsed.indexes.joinToString("/")}"
    }

    private fun findNearestNamedAncestor(target: View, root: View): View? {
        var current: View? = target
        while (current != null) {
            if (resourceEntryName(current) != null) return current
            if (current == root) break
            current = current.parent as? View
        }
        return null
    }

    private fun describeViewPath(target: View, root: View): String {
        val parts = mutableListOf<String>()
        var current: View? = target
        var depth = 0
        while (current != null && depth++ < 8) {
            parts += buildString {
                append(current.javaClass.simpleName)
                resourceEntryName(current)?.let { append('#').append(it) }
            }
            if (current == root) break
            current = current.parent as? View
        }
        return parts.joinToString(" <- ")
    }

    private fun escapeSelectorPart(value: String): String {
        return value.replace("%", "%25")
            .replace("|", "%7C")
            .replace("=", "%3D")
            .replace("\n", "%0A")
    }

    private fun unescapeSelectorPart(value: String): String {
        return value.replace("%0A", "\n")
            .replace("%3D", "=")
            .replace("%7C", "|")
            .replace("%25", "%")
    }

    private fun applyNotesLocationSpoof(param: XC_MethodHook.MethodHookParam<*>) {
        val latitude = state.notesSpoofLatitude.toFloatOrNull()?.takeIf { it in -90f..90f } ?: return
        val longitude = state.notesSpoofLongitude.toFloatOrNull()?.takeIf { it in -180f..180f } ?: return
        var changed = applyNotesLocationSpoofToObject(param.thisObject, latitude, longitude)
        param.args.indices.forEach { index ->
            when (param.args[index]) {
                is Double -> {
                    param.args[index] = (if (index % 2 == 0) latitude else longitude).toDouble()
                    changed = true
                }
                is Float -> {
                    param.args[index] = if (index % 2 == 0) latitude else longitude
                    changed = true
                }
                else -> changed = applyNotesLocationSpoofToObject(param.args[index], latitude, longitude) || changed
            }
        }
        if (changed && notesSpoofLogCount++ < 30) logInfo("Applied Notes location spoof before ${param.method}")
    }

    private fun applyNotesLocationSpoofToObject(target: Any?, latitude: Float, longitude: Float): Boolean {
        if (target == null) return false
        val name = target.javaClass.name.lowercase(Locale.US)
        val noteLike = name.contains("note") || name.contains("location") || name.contains("graphqloptimisticpostoperation")
        var changed = setFloatFieldIfPresent(target, "A0I", latitude)
        changed = setFloatFieldIfPresent(target, "A0J", longitude) || changed
        changed = setFloatFieldIfPresent(target, "latitude", latitude) || changed
        changed = setFloatFieldIfPresent(target, "longitude", longitude) || changed
        changed = setFloatFieldIfPresent(target, "lat", latitude) || changed
        changed = setFloatFieldIfPresent(target, "lng", longitude) || changed
        return if (!changed && noteLike) setFirstTwoFloatFields(target, latitude, longitude) else changed
    }

    private fun setFloatFieldIfPresent(target: Any, fieldName: String, value: Float): Boolean {
        var cls: Class<*>? = target.javaClass
        while (cls != null && cls != Any::class.java) {
            try {
                val field = cls.getDeclaredField(fieldName)
                if (!isFloatField(field)) return false
                field.isAccessible = true
                field.set(target, value)
                return true
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            } catch (_: Throwable) {
                return false
            }
        }
        return false
    }

    private fun setFirstTwoFloatFields(target: Any, latitude: Float, longitude: Float): Boolean {
        var cls: Class<*>? = target.javaClass
        var set = 0
        while (cls != null && cls != Any::class.java && set < 2) {
            cls.declaredFields.forEach { field ->
                if (set >= 2 || !isFloatField(field)) return@forEach
                runCatching {
                    field.isAccessible = true
                    field.set(target, if (set == 0) latitude else longitude)
                    set++
                }
            }
            cls = cls.superclass
        }
        return set == 2
    }

    private fun isFloatField(field: Field): Boolean {
        return field.type == java.lang.Float.TYPE || field.type == java.lang.Float::class.java
    }

    private fun defaultResult(type: Class<*>): Any? {
        return when {
            type == java.lang.Boolean.TYPE -> false
            type == java.lang.Byte.TYPE -> 0.toByte()
            type == java.lang.Short.TYPE -> 0.toShort()
            type == java.lang.Integer.TYPE -> 0
            type == java.lang.Long.TYPE -> 0L
            type == java.lang.Float.TYPE -> 0f
            type == java.lang.Double.TYPE -> 0.0
            type == java.lang.Character.TYPE -> 0.toChar()
            type == java.lang.Void.TYPE -> null
            type.isInterface -> Proxy.newProxyInstance(type.classLoader ?: appClassLoader, arrayOf(type)) { proxy, method, args ->
                when (method.name) {
                    "toString" -> "PurrfectInstaNoOpRefreshRequest"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> args != null && args.isNotEmpty() && proxy === args[0]
                    else -> defaultResult(method.returnType)
                }
            }
            else -> null
        }
    }

    private fun findView(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findView(root.getChildAt(i), predicate)?.let { return it }
            }
        }
        return null
    }

    private fun resourceEntryName(view: View): String? {
        val id = view.id
        if (id == View.NO_ID || id == 0) return null
        return runCatching { view.resources.getResourceEntryName(id) }.getOrNull()
    }

    private fun isTrackingParam(name: String): Boolean {
        val clean = name.lowercase(Locale.US)
        return clean.startsWith("utm_") || clean in trackingParams
    }

    private fun isWebUri(uri: Uri?): Boolean {
        val scheme = uri?.scheme?.lowercase(Locale.US)
        return scheme == "http" || scheme == "https"
    }

    private fun lower(value: String?): String = value?.lowercase(Locale.US).orEmpty()

    private fun hasAnyDownloadFeature(): Boolean {
        return state.enablePostDownload || state.enableStoryDownload || state.enableReelDownload ||
            state.enableProfileDownload || state.enableDmContextMenuOptions || state.enableReelThumbnailDownload ||
            state.enableStoryRepostButton || state.enableGifCommentDownload
    }

    private fun hasAnyQuickToggle(): Boolean {
        return state.quickToggleSeen || state.quickToggleTyping || state.quickToggleScreenshot ||
            state.quickToggleViewOnce || state.quickToggleStory || state.quickToggleLive ||
            state.quickToggleEphemeral || state.quickToggleUnsend || state.quickToggleReplays ||
            state.quickTogglePermanentView || state.quickToggleAllowScreenshots
    }

    private fun dp(value: Int): Int {
        return (value * androidContext.resources.displayMetrics.density).roundToInt()
    }

    private class HiddenUiState(view: View) {
        val visibility = view.visibility
        val alpha = view.alpha
        val enabled = view.isEnabled
        val clickable = view.isClickable
        val importantForAccessibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.importantForAccessibility
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        val params = view.layoutParams
        val hasLayoutParams = params != null
        val layoutWidth = params?.width ?: 0
        val layoutHeight = params?.height ?: 0
        val marginParams = params as? ViewGroup.MarginLayoutParams
        val hasMargins = marginParams != null
        val leftMargin = marginParams?.leftMargin ?: 0
        val topMargin = marginParams?.topMargin ?: 0
        val rightMargin = marginParams?.rightMargin ?: 0
        val bottomMargin = marginParams?.bottomMargin ?: 0
        val minWidth = view.minimumWidth
        val minHeight = view.minimumHeight
        val width = maxOf(view.width, view.measuredWidth)
        val height = maxOf(view.height, view.measuredHeight)
        val density = runCatching { view.resources.displayMetrics.density }.getOrDefault(1f)
        private val location = IntArray(2).also { runCatching { view.getLocationOnScreen(it) } }
        val screenLeft = location[0]
        val screenTop = location[1]
        val screenBottom = screenTop + height
    }

    private class ComposerUiAdjustment(target: View) {
        val params = target.layoutParams as? ViewGroup.MarginLayoutParams
        val hasMargins = params != null
        val leftMargin = params?.leftMargin ?: 0
        val topMargin = params?.topMargin ?: 0
        val rightMargin = params?.rightMargin ?: 0
        val bottomMargin = params?.bottomMargin ?: 0
        val paddingLeft = target.paddingLeft
        val paddingTop = target.paddingTop
        val paddingRight = target.paddingRight
        val paddingBottom = target.paddingBottom
        val translationX = target.translationX
    }

    private data class HiddenUiRowElement(
        val view: View,
        val hidden: Boolean,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int
    ) {
        val right: Int = left + width
        val bottom: Int = top + height
        fun centerX(): Int = left + width / 2
        fun centerY(): Int = top + height / 2
    }

    private class HiddenUiRowAdjustment(target: View) {
        private val location = IntArray(2).also { runCatching { target.getLocationOnScreen(it) } }
        val screenLeft = location[0]
        val screenTop = location[1]
        val width = maxOf(target.width, target.measuredWidth)
        val height = maxOf(target.height, target.measuredHeight)
        val translationX = target.translationX
        val translationY = target.translationY
        var appliedShiftX = 0
        var appliedShiftY = 0
    }

    private data class NavigationTabView(
        val view: View,
        val key: String?,
        val originalIndex: Int
    )

    private data class CaptureOverlayState(
        var button: TextView? = null,
        var pickerOverlay: View? = null
    ) {
        fun remove() {
            (pickerOverlay?.parent as? ViewGroup)?.removeView(pickerOverlay)
            (button?.parent as? ViewGroup)?.removeView(button)
            pickerOverlay = null
            button = null
        }
    }

    private data class CaptureCandidate(
        val view: View,
        val depth: Int
    ) {
        val area: Int = view.width.coerceAtLeast(1) * view.height.coerceAtLeast(1)
        val isViewGroup: Boolean = view is ViewGroup
        val hasId: Boolean = view.id != View.NO_ID && view.id != 0
        val interactive: Boolean = view.isClickable || view.isLongClickable || view.isFocusable
    }

    private data class ParsedSelector(
        val anchorId: String,
        val indexes: List<Int>,
        val classes: List<String>
    )

    private data class StoryTrayMedia(
        val profileUrl: String?,
        val coverUrl: String?
    )

    private data class ViewUrlCandidate(
        val url: String,
        val area: Int,
        val distance: Int
    )

    private data class DownloadMetadata(
        val username: String? = null,
        val mediaId: String? = null,
        val type: String = "",
        val audioUrl: String? = null,
        val folderName: String? = null
    )

    private data class BioCandidate(
        var text: String,
        var score: Int
    )

    private data class UsernameCandidate(
        var username: String?,
        var score: Int
    )

    private class PendingMediaStoreOutputStream(
        private val context: Context,
        private val uri: Uri,
        private val delegate: OutputStream
    ) : OutputStream() {
        private var discarded = false
        private var closed = false

        override fun write(b: Int) = delegate.write(b)

        override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)

        override fun flush() = delegate.flush()

        fun discard() {
            discarded = true
        }

        override fun close() {
            if (closed) return
            closed = true
            try {
                delegate.close()
            } finally {
                if (discarded) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                        context.contentResolver.update(uri, this, null, null)
                    }
                }
            }
        }
    }

    private data class MediaUrlCandidate(
        val url: String,
        val score: Int,
        val video: Boolean,
        val audio: Boolean,
        val path: String
    )

    private data class RecentMediaUrl(
        val url: String,
        val timeMs: Long
    )

    private data class DeferredDmAction(
        val action: Int,
        val url: String,
        val createdAtMs: Long
    )

    private data class DmMessageActionLifecycle(
        val chromeCallback: Any?,
        val visibilityCallback: Any?,
        val delegateCallback: Any?
    )

    private data class VoiceCacheCandidate(
        var file: File? = null,
        var score: Int = Int.MIN_VALUE,
        var modified: Long = 0L
    )

    private data class BlockedSeenMethodCall(
        val method: Method,
        val owner: Any?,
        val args: Array<Any?>,
        val atMs: Long
    )

    private data class BlockedRefreshRequest(
        val service: Any?,
        val method: Method,
        val args: Array<Any?>,
        val requestObj: Any,
        val uriFieldName: String,
        val originalUri: URI,
        val surface: String
    )

    private data class BlockedSeenNetworkRequest(
        val service: Any,
        val method: Method,
        val args: Array<Any?>,
        val requestObj: Any,
        val uriFieldName: String,
        val originalUri: URI,
        val atMs: Long
    )

    private enum class UploadSurface {
        UNKNOWN,
        STORY,
        DIRECT
    }

    private class UploadGlyphDrawable : android.graphics.drawable.Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val cx = b.left + w / 2f
            val top = b.top + h * 0.20f
            val mid = b.top + h * 0.34f
            val shaftBottom = b.top + h * 0.62f
            val trayTop = b.top + h * 0.66f
            val trayBottom = b.top + h * 0.80f
            val left = b.left + w * 0.26f
            val right = b.left + w * 0.74f
            paint.strokeWidth = maxOf(2.5f, w * 0.075f)

            canvas.drawLine(cx, top, cx, shaftBottom, paint)
            canvas.drawLine(cx, top, b.left + w * 0.37f, mid, paint)
            canvas.drawLine(cx, top, b.left + w * 0.63f, mid, paint)
            canvas.drawLine(left, trayTop, left, trayBottom, paint)
            canvas.drawLine(left, trayBottom, right, trayBottom, paint)
            canvas.drawLine(right, trayBottom, right, trayTop, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun runSafe(label: String, block: () -> Unit) {
        runCatching(block).onFailure { logError(label, it) }
    }

    private fun logInfo(message: String) {
        XposedBridge.log("[${InstagramFeatureState.TAG}] $message")
        CoreLogger.xposedLog(message, InstagramFeatureState.TAG)
        InstagramAppLogWriter.info(androidContext, InstagramFeatureState.TAG, message)
    }

    private fun logError(message: String, throwable: Throwable) {
        val full = "$message: ${throwable.javaClass.simpleName}: ${throwable.message}"
        XposedBridge.log("[${InstagramFeatureState.TAG}] $full")
        CoreLogger.xposedLog(full, InstagramFeatureState.TAG)
        InstagramAppLogWriter.error(androidContext, InstagramFeatureState.TAG, full)
    }
}
