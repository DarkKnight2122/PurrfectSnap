package me.eternal.purrfect.core.instagram

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Typeface
import android.net.Uri
import android.text.BoringLayout
import android.text.DynamicLayout
import android.text.PrecomputedText
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject

internal object InstagramCustomEmojiFontHooks {
    private const val FONT_DIR = "instaeclipse"
    private const val LEGACY_FONT_DIR = "purrfect_insta"
    private const val FONT_FILE = "custom_emoji_font.ttf"
    private const val PROVIDER_URI = "content://me.eternal.purrfect.instagram.emoji/font"

    @Volatile private var installed = false
    @Volatile private var loadedTypeface: Typeface? = null
    @Volatile private var loadedPath: String = ""
    @Volatile private var failedPath: String = ""
    @Volatile private var appContext: Context? = null
    @Volatile private var appClassLoader: ClassLoader? = null

    fun install(context: Context, classLoader: ClassLoader) {
        appContext = context.applicationContext ?: context
        appClassLoader = classLoader
        ensureFontImported(context)
        if (installed) return
        installed = true
        installInstagramEmojiSpanHooks(classLoader)
        installFrameworkTextHooks()
        log("Installed custom emoji text and layout hooks")
    }

    fun getProviderUri(): String = PROVIDER_URI

    fun syncAfterStateUpdate(context: Context?, previous: InstagramFeatureState, next: InstagramFeatureState) {
        context?.let { appContext = it.applicationContext ?: it }
        val changed = previous.customEmojiFontEnabled != next.customEmojiFontEnabled ||
            previous.customEmojiFontPath != next.customEmojiFontPath ||
            previous.customEmojiFontName != next.customEmojiFontName ||
            previous.customEmojiFontUri != next.customEmojiFontUri
        if (!changed) return

        if (next.customEmojiFontEnabled ||
            next.customEmojiFontPath.isNotBlank() ||
            next.customEmojiFontUri.isNotBlank() ||
            next.customEmojiFontName.isNotBlank()
        ) {
            ensureFontImported(context)
        } else {
            clearImportedFont(context)
        }
    }

    fun ensureFontImported(context: Context?): Boolean {
        val ctx = context ?: appContext ?: return false
        val current = InstagramFeatureStateStore.current
        val hasSetting = current.customEmojiFontEnabled ||
            current.customEmojiFontPath.isNotBlank() ||
            current.customEmojiFontUri.isNotBlank() ||
            current.customEmojiFontName.isNotBlank()
        if (!hasSetting && !providerHasFont(ctx)) return false

        current.customEmojiFontPath.takeIf { it.isNotBlank() }?.let { path ->
            runCatching {
                val file = File(path)
                if (file.exists() && file.length() > 0L) {
                    loadedTypeface = Typeface.createFromFile(file)
                    loadedPath = path
                    failedPath = ""
                    updateEmojiFeatureState(true, path, current.customEmojiFontName, current.customEmojiFontUri, "emoji-font-existing")
                    log("Using existing imported emoji font $path")
                    return true
                }
            }
        }

        val uri = current.customEmojiFontUri.takeIf { it.isNotBlank() } ?: PROVIDER_URI
        log("Importing emoji font from provider $uri")
        var imported = importFontFromUri(ctx, uri, current.customEmojiFontName)
        if (!imported && uri != PROVIDER_URI) {
            log("Retrying emoji font import through companion provider")
            imported = importFontFromUri(ctx, PROVIDER_URI, current.customEmojiFontName)
        }
        return imported
    }

    fun bootstrapFromCompanion(context: Context?): Boolean {
        var imported = ensureFontImported(context)
        val ctx = context ?: appContext
        if (!imported && ctx != null && providerHasFont(ctx)) {
            log("Found companion provider font during startup")
            imported = importFontFromUri(ctx, PROVIDER_URI, InstagramFeatureStateStore.current.customEmojiFontName)
        }
        return imported
    }

    fun importFontFromUri(context: Context?, uriString: String?, displayName: String? = null): Boolean {
        val ctx = context ?: appContext
        if (ctx == null || uriString.isNullOrBlank()) {
            return clearImportedFont(ctx)
        }

        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val output = fontFile(ctx)
        return runCatching {
            output.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) error("Cannot create font folder")
            }
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(output, false).use { outputStream ->
                    input.copyTo(outputStream, bufferSize = 64 * 1024)
                    outputStream.fd.sync()
                }
            } ?: error("Cannot open font URI")

            loadedTypeface = Typeface.createFromFile(output)
            loadedPath = output.absolutePath
            failedPath = ""
            updateEmojiFeatureState(
                enabled = true,
                path = output.absolutePath,
                name = displayName.orEmpty(),
                uri = uriString,
                source = "emoji-font-import"
            )
            log("Imported emoji font into Instagram storage: ${output.absolutePath}")
            true
        }.getOrElse {
            log("Font import failed from $uriString: ${it.message}")
            false
        }
    }

    fun clearImportedFont(context: Context?): Boolean {
        loadedTypeface = null
        loadedPath = ""
        failedPath = ""
        updateEmojiFeatureState(false, "", "", "", "emoji-font-clear")

        val ctx = context ?: appContext ?: return true
        return runCatching {
            fontFile(ctx).delete()
            File(File(ctx.filesDir, LEGACY_FONT_DIR), FONT_FILE).delete()
            true
        }.getOrElse {
            log("Emoji font reset failed: ${it.message}")
            false
        }
    }

    fun wrapEmojiText(source: CharSequence?): CharSequence? {
        val typeface = getTypeface()
        if (typeface == null || source == null || source.isEmpty() || !containsEmoji(source)) return source
        if (source is Spannable && source !is PrecomputedText) {
            applyEmojiSpans(source, typeface)
            return source
        }
        return SpannableStringBuilder(source).also { applyEmojiSpans(it, typeface) }
    }

    fun applyEmojiSpans(text: Spannable?, typeface: Typeface? = getTypeface()) {
        if (typeface == null || text == null || text.isEmpty()) return

        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            if (!isEmojiStart(text, index, codePoint)) {
                index += Character.charCount(codePoint)
                continue
            }

            val start = index
            val end = findEmojiSequenceEnd(text, index)
            if (end > start && !hasCustomSpan(text, start, end)) {
                try {
                    text.setSpan(CustomEmojiTypefaceSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } catch (_: IllegalArgumentException) {
                    return
                }
            }
            index = maxOf(end, start + Character.charCount(codePoint))
        }
    }

    private fun installInstagramEmojiSpanHooks(classLoader: ClassLoader) {
        arrayOf(
            "com.facebook.p042ui.emoji.FacebookTypefaceEmojiSpan",
            "com.facebook.ui.emoji.FacebookTypefaceEmojiSpan"
        ).forEach { className ->
            runCatching {
                val spanClass = runCatching { Class.forName(className, false, classLoader) }.getOrNull()
                    ?: return@forEach
                val paintHook = object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val textPaint = param.args.firstOrNull() as? TextPaint ?: return
                        getTypeface()?.let { textPaint.typeface = it }
                    }
                }
                XposedBridge.hookAllMethods(spanClass, "updateDrawState", paintHook)
                XposedBridge.hookAllMethods(spanClass, "updateMeasureState", paintHook)
                log("Hooked Instagram emoji span $className")
            }.onFailure {
                log("Instagram emoji span hook failed for $className: ${it.message}")
            }
        }
    }

    private fun installFrameworkTextHooks() {
        val wrapFirstTextArg = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam<*>) {
                if (param.args.isEmpty() || param.args[0] !is CharSequence) return
                param.args[0] = wrapEmojiText(param.args[0] as CharSequence)
            }
        }

        runCatching {
            val method = TextView::class.java.getDeclaredMethod(
                "setText",
                CharSequence::class.java,
                TextView.BufferType::class.java
            )
            XposedBridge.hookMethod(method, wrapFirstTextArg)
        }.onFailure { log("TextView.setText hook failed: ${it.message}") }

        runCatching {
            val method = TextView::class.java.getDeclaredMethod(
                "onDraw",
                Canvas::class.java
            )
            XposedBridge.hookMethod(
                method,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val textView = param.thisObject as? TextView ?: return
                        val text = textView.text
                        if (text is Spannable && text !is PrecomputedText) {
                            applyEmojiSpans(text, getTypeface())
                        }
                    }
                }
            )
        }.onFailure { log("TextView.onDraw hook failed: ${it.message}") }

        runCatching {
            val method = StaticLayout.Builder::class.java.getDeclaredMethod(
                "obtain",
                CharSequence::class.java,
                java.lang.Integer.TYPE,
                java.lang.Integer.TYPE,
                TextPaint::class.java,
                java.lang.Integer.TYPE
            )
            XposedBridge.hookMethod(method, wrapFirstTextArg)
        }.onFailure { log("StaticLayout.Builder hook failed: ${it.message}") }

        runCatching {
            XposedBridge.hookAllConstructors(StaticLayout::class.java, wrapFirstTextArg)
        }.onFailure { log("StaticLayout constructor hook failed: ${it.message}") }

        runCatching {
            XposedBridge.hookAllConstructors(DynamicLayout::class.java, wrapFirstTextArg)
        }.onFailure { log("DynamicLayout constructor hook failed: ${it.message}") }

        runCatching {
            XposedBridge.hookAllMethods(BoringLayout::class.java, "make", wrapFirstTextArg)
        }.onFailure { log("BoringLayout hook failed: ${it.message}") }
    }

    private fun hasCustomSpan(text: Spannable, start: Int, end: Int): Boolean {
        return text.getSpans(start, end, CustomEmojiTypefaceSpan::class.java)
            .any { text.getSpanStart(it) == start && text.getSpanEnd(it) == end }
    }

    private fun containsEmoji(text: CharSequence): Boolean {
        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            if (isEmojiStart(text, index, codePoint)) return true
            index += Character.charCount(codePoint)
        }
        return false
    }

    private fun isEmojiStart(text: CharSequence, index: Int, codePoint: Int): Boolean {
        if (isEmojiBase(codePoint) || isRegionalIndicator(codePoint)) return true
        if (codePoint == '#'.code || codePoint == '*'.code || codePoint in '0'.code..'9'.code) {
            val nextIndex = index + Character.charCount(codePoint)
            if (nextIndex >= text.length) return false
            val next = Character.codePointAt(text, nextIndex)
            if (next == 0x20E3) return true
            if (next == 0xFE0F) {
                val afterVariation = nextIndex + Character.charCount(next)
                return afterVariation < text.length && Character.codePointAt(text, afterVariation) == 0x20E3
            }
        }
        return false
    }

    private fun findEmojiSequenceEnd(text: CharSequence, index: Int): Int {
        val length = text.length
        val codePoint = Character.codePointAt(text, index)
        var cursor = index + Character.charCount(codePoint)

        if (isRegionalIndicator(codePoint) && cursor < length) {
            val next = Character.codePointAt(text, cursor)
            if (isRegionalIndicator(next)) cursor += Character.charCount(next)
            return cursor
        }

        while (cursor < length) {
            val next = Character.codePointAt(text, cursor)
            if (next == 0xFE0F || isEmojiModifier(next) || next == 0x20E3 || isEmojiTag(next)) {
                cursor += Character.charCount(next)
                continue
            }

            if (next == 0x200D) {
                val afterJoiner = cursor + Character.charCount(next)
                if (afterJoiner >= length) break
                val joined = Character.codePointAt(text, afterJoiner)
                if (!isEmojiBase(joined) && !isRegionalIndicator(joined)) break
                cursor = afterJoiner + Character.charCount(joined)
                continue
            }

            break
        }

        return cursor
    }

    private fun isEmojiBase(codePoint: Int): Boolean {
        return (codePoint in 0x1F000..0x1FAFF) ||
            (codePoint in 0x2600..0x27BF) ||
            codePoint == 0x00A9 ||
            codePoint == 0x00AE ||
            codePoint == 0x203C ||
            codePoint == 0x2049 ||
            codePoint == 0x2122 ||
            codePoint == 0x2139 ||
            codePoint == 0x3030 ||
            codePoint == 0x303D ||
            codePoint == 0x3297 ||
            codePoint == 0x3299
    }

    private fun isEmojiModifier(codePoint: Int): Boolean = codePoint in 0x1F3FB..0x1F3FF

    private fun isRegionalIndicator(codePoint: Int): Boolean = codePoint in 0x1F1E6..0x1F1FF

    private fun isEmojiTag(codePoint: Int): Boolean = codePoint in 0xE0020..0xE007F

    private fun providerHasFont(context: Context): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(PROVIDER_URI))?.use { input ->
                input.read() != -1
            } == true
        }.getOrDefault(false)
    }

    private fun getTypeface(): Typeface? {
        val current = InstagramFeatureStateStore.current
        if (!current.customEmojiFontEnabled) return null

        val path = current.customEmojiFontPath.trim()
        if (path.isEmpty()) return null
        if (path == loadedPath && loadedTypeface != null) return loadedTypeface
        if (path == failedPath) return null

        return try {
            val typeface = Typeface.createFromFile(path)
            loadedTypeface = typeface
            loadedPath = path
            failedPath = ""
            log("Loaded emoji font $path")
            typeface
        } catch (throwable: Throwable) {
            loadedTypeface = null
            loadedPath = ""
            failedPath = path
            log("Cannot load emoji font $path: ${throwable.message}")
            null
        }
    }

    private fun fontFile(context: Context): File = File(File(context.filesDir, FONT_DIR), FONT_FILE)

    private fun updateEmojiFeatureState(
        enabled: Boolean,
        path: String,
        name: String,
        uri: String,
        source: String
    ) {
        val current = InstagramFeatureStateStore.current
        if (current.customEmojiFontEnabled == enabled &&
            current.customEmojiFontPath == path &&
            current.customEmojiFontName == name &&
            current.customEmojiFontUri == uri
        ) {
            return
        }
        val snapshot = JSONObject()
        InstagramFeatureState.booleanFeatureKeys.forEach { key ->
            snapshot.put(key, readFeatureField(key) as? Boolean ?: false)
        }
        InstagramFeatureState.stringFeatureKeys.forEach { key ->
            snapshot.put(key, readFeatureField(key) as? String ?: "")
        }
        snapshot.put("customEmojiFontEnabled", enabled)
        snapshot.put("customEmojiFontPath", path)
        snapshot.put("customEmojiFontName", name)
        snapshot.put("customEmojiFontUri", uri)
        InstagramFeatureStateStore.update(InstagramFeatureState.fromJson(snapshot.toString(), source))
    }

    private fun readFeatureField(key: String): Any? {
        return runCatching {
            InstagramFeatureState::class.java.getDeclaredField(key)
                .apply { isAccessible = true }
                .get(InstagramFeatureStateStore.current)
        }.getOrNull()
    }

    private class CustomEmojiTypefaceSpan : MetricAffectingSpan() {
        override fun updateMeasureState(textPaint: TextPaint) {
            getTypeface()?.let { textPaint.typeface = it }
        }

        override fun updateDrawState(textPaint: TextPaint) {
            getTypeface()?.let { textPaint.typeface = it }
        }
    }

    private fun log(message: String) {
        XposedBridge.log("[${InstagramFeatureState.TAG}|EmojiFont] $message")
    }
}
