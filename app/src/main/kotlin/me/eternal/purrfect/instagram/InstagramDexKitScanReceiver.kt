package me.eternal.purrfect.instagram

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import me.eternal.purrfect.common.Constants
import java.util.concurrent.Executors

class InstagramDexKitScanReceiver : BroadcastReceiver() {

    companion object {
        private val executor = Executors.newFixedThreadPool(1) { r ->
            Thread(r, "PurrfectDexKitScan").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
        }
        @Volatile private var running = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Constants.INSTAGRAM_DEXKIT_SCAN_ACTION) return
        val apkPath = intent.getStringExtra("apk_path") ?: return
        val appVersion = intent.getStringExtra("app_version") ?: return
        if (running) return
        running = true
        executor.execute {
            try { scanAll(context, apkPath, appVersion) }
            finally { running = false }
        }
    }

    private fun scanAll(ctx: Context, apk: String, ver: String) {
        val prefs = ctx.getSharedPreferences("purrfect_insta_dexkit_cache", Context.MODE_PRIVATE)
        val ed = prefs.edit().putString("_v", ver).putString("_schema", "3")

        val bridge = try {
            org.luckypray.dexkit.DexKitBridge.create(apk)
        } catch (_: Exception) { return }

        bridge.use { dex ->
            try { findClassByField(dex, "is_employee")?.let { ed.putString("s_DevOptionsClass", it) } } catch (_: Exception) {}
            try { findClassSetByString(dex, arrayOf("biography", "biography_with_entities", "profile_header_bio"))?.let { ed.putString("s_CopyBio_Classes", it) } } catch (_: Exception) {}
            try { findMethod(dex, "BottomSheetConstants", "com.instagram.mainactivity.InstagramMainActivity")?.let { ed.putString("m_BottomSheet", it) } } catch (_: Exception) {}
            try { findMethod(dex, "clips_ufi_like_button_component")?.let { ed.putString("m_ActivityHistory_clips_ufi", it) } } catch (_: Exception) {}
            try { findMethod(dex, "clips/write_seen_state/")?.let { ed.putString("m_ActivityHistory_clips_seen", it) } } catch (_: Exception) {}
            try { findAllMethods(dex, "CommentCopy_LongPress", ed, "fb_comment_long_press") } catch (_: Exception) {}
            try { findAllMethods(dex, "SponsoredController", ed, "SponsoredContentController") } catch (_: Exception) {}
            try { findMethodClass(dex, "FeedItemParserClass", ed, arrayOf("clips_netego", "suggested_users")) } catch (_: Exception) {}
            try { findMethod(dex, "mark_thread_seen-")?.let { ed.putString("m_GhostSeen", it) } } catch (_: Exception) {}
            try { findMethod(dex, "media/seen/")?.let { ed.putString("m_GhostStorySeen", it) } } catch (_: Exception) {}
            try { findMethod(dex, "is_typing_indicator_enabled")?.let { ed.putString("m_GhostTyping", it) } } catch (_: Exception) {}
            try { findMethod(dex, "visual_item_seen")?.let { ed.putString("m_GhostViewOnce", it) } } catch (_: Exception) {}
            try { findAllMethods(dex, "Replays_update", ed, "seen_count", "tap_models") } catch (_: Exception) {}
            try { findMethod(dex, "archived_media_timestamp", "view_mode")?.let { ed.putString("m_ViewOnceMedia", it) } } catch (_: Exception) {}
            try { findAllMethods(dex, "KeepUnsent_render_results", ed, "message_row_component") } catch (_: Exception) {}
            try { findAllMethods(dex, "KeepUnsent_process_ops", ed, "process_ops") } catch (_: Exception) {}
            try { findAllMethods(dex, "KeepUnsent_delta_remove", ed, "deleteThreads") } catch (_: Exception) {}
            try { findAllMethods(dex, "KeepUnsent_store_delete", ed, "DirectMutationStore") } catch (_: Exception) {}
            try { findMethod(dex, "Reporting screenshot")?.let { ed.putString("m_GhostScreenshot", it) } } catch (_: Exception) {}
            try { findMethod(dex, "double_tap_on_liked")?.let { ed.putString("m_DoubleTapLike", it) } } catch (_: Exception) {}
            try { findMethod(dex, "ig_disable_video_autoplay")?.let { ed.putString("m_VideoAutoPlay", it) } } catch (_: Exception) {}
            try { findAnyNamedMethod(dex, "VideoUrlCapture", ed, "getUrl") } catch (_: Exception) {}
            try { findMethodClass(dex, "s_PostDownloadClass", ed, arrayOf("row_feed_button_like", "like_button")) } catch (_: Exception) {}
            try { findMethod(dex, "reel_download")?.let { ed.putString("m_ReelDownload", it) } } catch (_: Exception) {}
        }
        ed.apply()
    }

    // === DexKit helpers ===
    private fun findClassByField(dex: Any, fieldName: String): String? {
        val fc = Class.forName("org.luckypray.dexkit.query.FindClass")
        val cm = Class.forName("org.luckypray.dexkit.query.matchers.ClassMatcher")
        val f = fc.getMethod("create").invoke(null)
        val m = cm.getMethod("create").invoke(null)
        cm.getMethod("addFieldForName", String::class.java).invoke(m, fieldName)
        fc.getMethod("matcher", cm).invoke(f, m)
        val results = (dex.javaClass as Class<*>).getMethod("findClass", fc).invoke(dex, f) as? Iterable<*>
        return results?.firstOrNull()?.let { (it.javaClass as Class<*>).getMethod("getName").invoke(it) as? String }
    }

    private fun findClassSetByString(dex: Any, strings: Array<String>): String? {
        val fc = Class.forName("org.luckypray.dexkit.query.FindClass")
        val cm = Class.forName("org.luckypray.dexkit.query.matchers.ClassMatcher")
        val names = java.util.LinkedHashSet<String>()
        for (s in strings) {
            val f = fc.getMethod("create").invoke(null)
            val m = cm.getMethod("create").invoke(null)
            findUsingStringsMethod(cm)?.invoke(m, arrayOf(s))
            fc.getMethod("matcher", cm).invoke(f, m)
            val r = (dex.javaClass as Class<*>).getMethod("findClass", fc).invoke(dex, f) as? Iterable<*>
            r?.forEach { d -> (d!!.javaClass as Class<*>).getMethod("getName").invoke(d)?.let { names.add(it as String) } }
        }
        return if (names.isEmpty()) null else names.joinToString("\u0000")
    }

    private fun findMethod(dex: Any, vararg strings: String, declaredClass: String? = null): String? {
        val fm = Class.forName("org.luckypray.dexkit.query.FindMethod")
        val mm = Class.forName("org.luckypray.dexkit.query.matchers.MethodMatcher")
        val f = fm.getMethod("create").invoke(null)
        val m = mm.getMethod("create").invoke(null)
        findUsingStringsMethod(mm)?.invoke(m, arrayOf(*strings))
        if (declaredClass != null) mm.getMethod("declaredClass", String::class.java).invoke(m, declaredClass)
        fm.getMethod("matcher", mm).invoke(f, m)
        val r = (dex.javaClass as Class<*>).getMethod("findMethod", fm).invoke(dex, f) as? Iterable<*>
        return r?.firstOrNull()?.let { encodeMethod(it) }
    }

    private fun findAllMethods(dex: Any, key: String, ed: SharedPreferences.Editor, vararg strings: String) {
        val fm = Class.forName("org.luckypray.dexkit.query.FindMethod")
        val mm = Class.forName("org.luckypray.dexkit.query.matchers.MethodMatcher")
        val f = fm.getMethod("create").invoke(null)
        val m = mm.getMethod("create").invoke(null)
        findUsingStringsMethod(mm)?.invoke(m, arrayOf(*strings))
        fm.getMethod("matcher", mm).invoke(f, m)
        val r = (dex.javaClass as Class<*>).getMethod("findMethod", fm).invoke(dex, f) as? Iterable<*>
        val methods = r?.mapNotNull { encodeMethod(it) }.orEmpty()
        if (methods.isNotEmpty()) {
            ed.putInt("mc_$key", methods.size)
            methods.forEachIndexed { i, enc -> ed.putString("m_${key}_$i", enc) }
        }
    }

    private fun findMethodClass(dex: Any, key: String, ed: SharedPreferences.Editor, strings: Array<String>) {
        val fm = Class.forName("org.luckypray.dexkit.query.FindMethod")
        val mm = Class.forName("org.luckypray.dexkit.query.matchers.MethodMatcher")
        val f = fm.getMethod("create").invoke(null)
        val m = mm.getMethod("create").invoke(null)
        findUsingStringsMethod(mm)?.invoke(m, strings)
        fm.getMethod("matcher", mm).invoke(f, m)
        val r = (dex.javaClass as Class<*>).getMethod("findMethod", fm).invoke(dex, f) as? Iterable<*>
        r?.firstOrNull()?.let { d -> ed.putString(key, (d!!.javaClass as Class<*>).getMethod("getClassName").invoke(d) as? String) }
    }

    private fun findAnyNamedMethod(dex: Any, key: String, ed: SharedPreferences.Editor, methodName: String) {
        val fm = Class.forName("org.luckypray.dexkit.query.FindMethod")
        val mm = Class.forName("org.luckypray.dexkit.query.matchers.MethodMatcher")
        val f = fm.getMethod("create").invoke(null)
        val m = mm.getMethod("create").invoke(null)
        mm.getMethod("name", String::class.java).invoke(m, methodName)
        fm.getMethod("matcher", mm).invoke(f, m)
        val r = (dex.javaClass as Class<*>).getMethod("findMethod", fm).invoke(dex, f) as? Iterable<*>
        val methods = r?.mapNotNull { encodeMethod(it) }.orEmpty()
        if (methods.isNotEmpty()) {
            ed.putInt("mc_$key", methods.size)
            methods.forEachIndexed { i, enc -> ed.putString("m_${key}_$i", enc) }
        }
    }

    private fun findUsingStringsMethod(clazz: Class<*>): java.lang.reflect.Method? {
        return clazz.methods.firstOrNull { it.name == "usingStrings" && it.parameterTypes.size == 1 }
    }

    private fun encodeMethod(data: Any?): String? {
        if (data == null) return null
        return try {
            val cls = data.javaClass as Class<*>
            val cn = cls.getMethod("getClassName").invoke(data) as? String ?: return null
            val mn = cls.getMethod("getName").invoke(data) as? String ?: return null
            val rt = cls.getMethod("getReturnType").invoke(data)?.toString() ?: "void"
            val pt = (cls.getMethod("getParamTypes").invoke(data) as? Iterable<*>)?.joinToString(",") { it.toString() } ?: ""
            "$cn\u0000$mn\u0000($pt)$rt"
        } catch (_: Exception) { null }
    }
}
