package me.eternal.purrfectsnap.core.features.impl.ui

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import android.widget.LinearLayout
import me.eternal.purrfectsnap.core.event.events.impl.AddViewEvent
import me.eternal.purrfectsnap.core.event.events.impl.BindViewEvent
import me.eternal.purrfectsnap.core.features.Feature
import me.eternal.purrfectsnap.core.ui.children
import me.eternal.purrfectsnap.core.ui.getValdiContext
import me.eternal.purrfectsnap.core.ui.hideViewCompletely
import me.eternal.purrfectsnap.core.ui.onLayoutChange
import me.eternal.purrfectsnap.core.util.dataBuilder
import me.eternal.purrfectsnap.core.util.hook.HookStage
import me.eternal.purrfectsnap.core.util.hook.Hooker
import me.eternal.purrfectsnap.core.util.hook.hook
import me.eternal.purrfectsnap.core.util.ktx.getIdentifier

fun getChatInputBar(event: AddViewEvent): Lazy<ViewGroup?>? {
    if (!event.parent.javaClass.name.endsWith("ChatInputLayout")) return null
    val isViewSwitcher = event.viewClassName.endsWith("ViewSwitcher")

    return lazy {
        // get the first linear layout in the view switcher
        val firstLinearLayout = if (isViewSwitcher) {
            (event.view as ViewGroup).children()
                .firstOrNull { it is LinearLayout } as? ViewGroup ?: return@lazy null
        } else {
            event.view as? ViewGroup ?: return@lazy null
        }
        // get the first linear layout with at least 3 children
        firstLinearLayout.children()
            .firstOrNull { v -> v is LinearLayout && v.childCount > 2 } as? LinearLayout
            ?: return@lazy null
    }
}

class UITweaks : Feature("UITweaks") {
    private val identifierCache = mutableMapOf<String, Int>()

    fun getId(name: String, defType: String): Int {
        return identifierCache.getOrPut("$name:$defType") {
            context.resources.getIdentifier(name, defType)
        }
    }

    private fun hideStorySection(event: AddViewEvent) {
        val parent = event.parent
        parent.visibility = View.GONE
        val marginLayoutParams = parent.layoutParams as MarginLayoutParams
        marginLayoutParams.setMargins(-99999, -99999, -99999, -99999)
        event.canceled = true
    }

    private fun hideView(view: View) {
        view.apply {
            visibility = View.GONE
            post {
                isEnabled = false
                visibility = View.GONE
                setWillNotDraw(true)
            }
            addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                view.post { view.visibility = View.GONE }
            }
        }
    }

    private fun onActivityCreate() {
        val blockAds by context.config.global.blockAds
        val hiddenElements by context.config.userInterface.hideUiComponents
        val hideStorySuggestions by context.config.userInterface.hideStorySuggestions
        val packageInfo = context.mappings.getSnapchatPackageInfo()
        val versionName = packageInfo?.versionName

        // About page version string normalization
        Resources::class.java.methods.filter { 
            it.name == "getString" && it.returnType == String::class.java
        }.forEach { method ->
            method.hook(HookStage.AFTER) { param ->
                val result = param.getResult() as? String ?: return@hook
                val salt = context.native.getIntegritySalt(1) ?: return@hook

                if (versionName != null && result.contains("Made in Los Angeles", ignoreCase = false) && result.contains(versionName)) {
                    val devOptions = context.config.experimental.developerOptions
                    if (!devOptions.enableBranding.get()) return@hook

                    val customName = devOptions.customBrandingName.get()
                    val devName = if (customName.isNullOrBlank()) salt else customName
                    param.setResult("$result\nMade with ❤️ by ᴋᴀʟᴀᴅɪɴ")
                }
            }
        }

        val displayMetrics = context.resources.displayMetrics
        val deviceAspectRatio = displayMetrics.widthPixels.toFloat() / displayMetrics.heightPixels.toFloat()

        val chatNoteRecordButton = getId("chat_note_record_button", "id")
        val unreadHintButton = getId("unread_hint_button", "id")

        Resources::class.java.methods.first { it.name == "getDimensionPixelSize"}.hook(
            HookStage.AFTER,
            { false }
        ) { param ->
            val id = param.arg<Int>(0)
            if (id == getId("capri_viewfinder_default_corner_radius", "dimen") ||
                id == getId("ngs_hova_nav_larger_camera_button_size", "dimen")) {
                param.setResult(0)
            }
        }

        context.event.subscribe(BindViewEvent::class, { hideStorySuggestions.isNotEmpty() }) { event ->
            if (event.view is FrameLayout) {
                fun removeView() {
                    event.view.layoutParams = event.view.layoutParams?.apply {
                        width = 0; height = 0
                    } ?: return
                }

                val viewModelString = event.prevModel.toString()
                val isMyStory by lazy { viewModelString.let { it.startsWith("StoryCarouselItemViewModel") && it.contains("storyId=") } }

                if (hideStorySuggestions.contains("hide_my_stories") && isMyStory) {
                    removeView()
                    return@subscribe
                }
            }
        }

        context.event.subscribe(AddViewEvent::class) { event ->
            val viewId = event.view.id
            val view = event.view

            if (blockAds && viewId == getId("df_promoted_story", "id")) {
                hideStorySection(event)
            }

            if (hiddenElements.contains("hide_billboard_prompt") && event.parent.javaClass.name.endsWith("BillboardFeedHeaderPromptComponent")) {
                hideView(event.parent)
                view.getValdiContext()?.componentContext?.get()?.dataBuilder {
                    val dismissFunction = get<Any>("_onDismiss") ?: return@subscribe
                    dismissFunction.javaClass.getMethod("invoke").invoke(dismissFunction)
                }
            }

            if (event.parent.javaClass.name.endsWith("ConstraintLayout") && event.view is LinearLayout && hiddenElements.contains("hide_map_reactions")) {
                val viewGroup = event.view as ViewGroup
                val children = viewGroup.children()

                // hide image views in the reaction bar
                if (children.takeIf { it.count() == 5 }?.all { it.javaClass.name.endsWith("SnapImageView") } == true) {
                    children.forEach { imageView ->
                        imageView.hideViewCompletely()
                    }
                }
            }

            if (event.parent.javaClass.name.endsWith("PreviewBottomToolbarView") && hiddenElements.contains("hide_post_to_story_buttons")) {
                if (event.parent.childCount == 1) {
                    event.view.hideViewCompletely()
                }
            }

            if (viewId == getId("send_btn", "id") && hiddenElements.contains("hide_post_to_story_buttons")) {
                // hide previous view
                if (event.parent.childCount > 0) {
                    val lastChild = event.parent.getChildAt(event.parent.childCount - 1)?.takeIf { it is LinearLayout } ?: return@subscribe
                    context.log.verbose("Hiding post to story button")
                    lastChild.hideViewCompletely()
                }
            }

            getChatInputBar(event)?.let { lazyChatInputBar ->
                val chatInputBar by lazyChatInputBar

                if (hiddenElements.contains("hide_live_location_share_button")) {
                    chatInputBar?.onLayoutChange {
                        chatInputBar!!.children().lastOrNull { it.javaClass.name.endsWith("AppCompatImageButton") && runCatching { it.resources.getResourceName(it.id) }.getOrNull() == null }
                            ?.hideViewCompletely()
                    }
                }

                if (hiddenElements.contains("hide_stickers_button")) {
                    chatInputBar
                        ?.children()
                        ?.lastOrNull { layout ->
                            layout is FrameLayout && layout.children().all {
                                it.javaClass.name.endsWith("SnapImageView")
                            }
                        }
                        ?.hideViewCompletely()
                }
            }

            if (viewId == chatNoteRecordButton && hiddenElements.contains("hide_voice_record_button")) {
                view.hideViewCompletely()
            }

            if (viewId == unreadHintButton && hiddenElements.contains("hide_unread_chat_hint")) {
                event.canceled = true
            }
        }
    }

    override fun init() {
        onNextActivityCreate {
            onActivityCreate()
        }
    }
}
