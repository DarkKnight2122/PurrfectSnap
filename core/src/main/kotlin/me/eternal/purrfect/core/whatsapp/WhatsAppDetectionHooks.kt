package me.eternal.purrfect.core.whatsapp

import android.content.pm.PackageManager
import android.os.Build
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import me.eternal.purrfect.common.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.io.Reader
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.security.SecureRandom
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object WhatsAppDetectionHooks {
    private const val PLAY_STORE_PACKAGE = "com.android.vending"
    private const val PACKAGE_SOURCE_STORE = 2
    private const val WHATSAPP_PACKAGE = "com.whatsapp"

    private val earlyInstalled = AtomicBoolean(false)
    private val runtimeInstalled = AtomicBoolean(false)
    private val buildSpoofInstalled = AtomicBoolean(false)
    private val systemPropertyHooksInstalled = AtomicBoolean(false)
    private val installerHooksInstalled = AtomicBoolean(false)
    private val firebaseMessagingGuardInstalled = AtomicBoolean(false)
    private val hookInjectionShieldInstalled = AtomicBoolean(false)
    private val hookInjectionBootstrapGuardInstalled = AtomicBoolean(false)
    private val registrationSignalHooksInstalled = AtomicBoolean(false)
    private val switchboardLogged = AtomicBoolean(false)
    private val callerCheck = ThreadLocal.withInitial { false }
    private val procPathStreams = Collections.synchronizedMap(IdentityHashMap<Any, String>())
    private val procPathReaders = Collections.synchronizedMap(IdentityHashMap<Any, String>())
    private val whatsappInstallSourceObjects = Collections.synchronizedMap(IdentityHashMap<Any, Boolean>())
    private val bootstrapSafeObjects = Collections.synchronizedMap(IdentityHashMap<Class<*>, Any>())
    private val bootstrapRandom = SecureRandom()

    private val customRomSystemProperties = mapOf(
        "ro.build.tags" to "release-keys",
        "ro.build.type" to "user",
        "ro.build.display.id" to safeBuildDisplay(),
        "ro.build.fingerprint" to safeBuildFingerprint(),
        "ro.build.version.incremental" to safeBuildIncremental(),
        "ro.modversion" to "",
        "ro.cm.version" to "",
        "ro.lineage.version" to "",
        "ro.crdroid.version" to "",
        "ro.evolution.version" to "",
        "ro.pixelos.version" to "",
        "ro.custom.version" to "",
        "ro.rom.version" to ""
    )

    private val hookClassPrefixes = listOf(
        "de.robv.android.xposed",
        "org.lsposed",
        "io.github.lsposed",
        "com.saurik.substrate"
    )

    private val hookClassFragments = listOf(
        "xposed",
        "lsposed",
        "lspatch",
        "edxposed",
        "substrate",
        "frida",
        "hookbridge",
        "lsp_hook",
        "lsphooker",
        "purrfect"
    )

    private val hookPackageNames = setOf(
        BuildConfig.APPLICATION_ID,
        "de.robv.android.xposed.installer",
        "org.meowcat.edxposed.manager",
        "org.lsposed.manager",
        "io.github.lsposed.manager",
        "org.lsposed.lspatch",
        "org.lsposed.lspatch.manager",
        "com.saurik.substrate"
    )

    private val procInspectionPaths = listOf(
        "/proc/self/maps",
        "/proc/self/smaps",
        "/proc/self/mountinfo",
        "/proc/self/status"
    )

    private val hookBootstrapDetectorClassNames = listOf(
        "X.0gC",
        "X.C11430gC",
        "X.1SC"
    )

    private val installerStringKeys = setOf(
        "installer",
        "installer_package",
        "installer_package_name",
        "installing_package",
        "installing_package_name",
        "originating_package",
        "originating_package_name",
        "initiating_package",
        "initiating_package_name",
        "update_owner_package",
        "update_owner_package_name",
        "install_source",
        "install_source_package",
        "app_campaign_download_source",
        "download_source"
    )

    private val installerBooleanKeys = setOf(
        "install_source_official",
        "is_installed_from_play_store",
        "is_from_play_store",
        "is_official_install_source"
    )

    private val installerIntegerKeys = setOf(
        "package_source",
        "install_package_source"
    )

    private val detectorLogKeyFragments = listOf(
        "rom",
        "installer",
        "install_source",
        "package_source",
        "official",
        "integrity",
        "attestation",
        "play_integrity",
        "hook",
        "injection",
        "xposed",
        "lsposed",
        "lspatch",
        "frida"
    )

    fun install(appClassLoader: ClassLoader) {
        installEarly(appClassLoader)
        installRuntime(appClassLoader)
    }

    @Suppress("UNUSED_PARAMETER")
    fun installEarly(appClassLoader: ClassLoader) {
        if (!earlyInstalled.compareAndSet(false, true)) return
        logSwitchboard()
        installCoreHooks(appClassLoader)
        WhatsAppDetectionEventLogger.lifecycle("Installed early WhatsApp custom ROM, installer, and hook-injection shields")
    }

    fun installRuntime(appClassLoader: ClassLoader) {
        installEarly(appClassLoader)
        if (!runtimeInstalled.compareAndSet(false, true)) return
        installCoreHooks(appClassLoader)
        installOnce(registrationSignalHooksInstalled) { installRegistrationSignalHooks() }
        WhatsAppDetectionEventLogger.lifecycle("Installed runtime WhatsApp detection logger")
    }

    private fun logSwitchboard() {
        if (!switchboardLogged.compareAndSet(false, true)) return
        WhatsAppDetectionEventLogger.lifecycle(
            "WhatsApp shields active: custom_rom_build_identity,custom_rom_system_properties," +
                "play_store_installer_spoof,hook_injection_class_stack_proc_hiding,detection_logger"
        )
        WhatsAppDetectionEventLogger.lifecycle(
            "WhatsApp broad root/integrity shields are inactive; only the requested narrow shields are installed"
        )
    }

    private fun installCoreHooks(appClassLoader: ClassLoader) {
        installOnce(buildSpoofInstalled) { spoofCustomRomBuildIdentity() }
        installOnce(systemPropertyHooksInstalled) { installCustomRomSystemPropertyHooks() }
        installOnce(installerHooksInstalled) { installPlayStoreInstallerHooks() }
        installOnce(firebaseMessagingGuardInstalled) { installFirebaseMessagingGuard(appClassLoader) }
        installOnce(hookInjectionShieldInstalled) { installHookInjectionShield(appClassLoader) }
    }

    private inline fun installOnce(gate: AtomicBoolean, block: () -> Unit) {
        if (gate.compareAndSet(false, true)) block()
    }

    private fun spoofCustomRomBuildIdentity() {
        setStaticStringField(Build::class.java, "TAGS", "release-keys")
        setStaticStringField(Build::class.java, "TYPE", "user")
        setStaticStringField(Build::class.java, "FINGERPRINT", safeBuildFingerprint())
        setStaticStringField(Build::class.java, "DISPLAY", safeBuildDisplay())
        runCatching {
            setStaticStringField(Build.VERSION::class.java, "INCREMENTAL", safeBuildIncremental())
        }
        WhatsAppDetectionEventLogger.lifecycle("Spoofed WhatsApp custom ROM Build identity")
    }

    private fun installCustomRomSystemPropertyHooks() {
        runCatching {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            XposedBridge.hookAllMethods(
                systemPropertiesClass,
                "get",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val key = param.args.firstOrNull() as? String ?: return
                        val clean = customRomSystemProperties[key] ?: return
                        param.result = clean
                        logProbe("custom_rom_system_property", key, "return_${clean.ifBlank { "empty" }}")
                    }
                }
            )
            XposedBridge.hookAllMethods(
                systemPropertiesClass,
                "getBoolean",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val key = param.args.firstOrNull() as? String ?: return
                        val value = customRomSystemProperties[key]?.toBooleanStrictOrNull() ?: return
                        param.result = value
                        logProbe("custom_rom_system_property_boolean", key, "return_$value")
                    }
                }
            )
            XposedBridge.hookAllMethods(
                systemPropertiesClass,
                "getInt",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val key = param.args.firstOrNull() as? String ?: return
                        val value = customRomSystemProperties[key]?.toIntOrNull() ?: return
                        param.result = value
                        logProbe("custom_rom_system_property_int", key, "return_$value")
                    }
                }
            )
            XposedBridge.hookAllMethods(
                systemPropertiesClass,
                "getLong",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val key = param.args.firstOrNull() as? String ?: return
                        val value = customRomSystemProperties[key]?.toLongOrNull() ?: return
                        param.result = value
                        logProbe("custom_rom_system_property_long", key, "return_$value")
                    }
                }
            )
            WhatsAppDetectionEventLogger.lifecycle("Hooked WhatsApp custom ROM system property shield")
        }.onFailure {
            WhatsAppDetectionEventLogger.lifecycle("Custom ROM system property shield unavailable: ${it.message}")
        }
    }

    private fun installPlayStoreInstallerHooks() {
        runCatching {
            val appPackageManagerClass = Class.forName("android.app.ApplicationPackageManager")
            hookInstallerPackageName(appPackageManagerClass)
            hookInstallSourceInfo(appPackageManagerClass)
            hookHookPackageLookups(appPackageManagerClass)
            WhatsAppDetectionEventLogger.lifecycle("Hooked WhatsApp Play Store installer spoof and hook package hiding")
        }.onFailure {
            WhatsAppDetectionEventLogger.lifecycle("PackageManager installer shield unavailable: ${it.message}")
        }
        hookInstallSourceInfoGetters()
    }

    private fun hookInstallerPackageName(packageManagerClass: Class<*>) {
        runCatching {
            XposedBridge.hookAllMethods(
                packageManagerClass,
                "getInstallerPackageName",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val packageName = param.args.firstOrNull() as? String ?: return
                        if (packageName == WHATSAPP_PACKAGE) {
                            param.result = PLAY_STORE_PACKAGE
                            logProbe("installer_source", packageName, "getInstallerPackageName_vending")
                        }
                    }
                }
            )
        }
    }

    private fun hookInstallSourceInfo(packageManagerClass: Class<*>) {
        runCatching {
            XposedBridge.hookAllMethods(
                packageManagerClass,
                "getInstallSourceInfo",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val packageName = param.args.firstOrNull() as? String ?: return
                        val result = param.result ?: return
                        if (packageName == WHATSAPP_PACKAGE) {
                            whatsappInstallSourceObjects[result] = true
                            logProbe("installer_source", packageName, "track_install_source_info")
                        }
                    }
                }
            )
        }
    }

    private fun hookInstallSourceInfoGetters() {
        runCatching {
            val installSourceInfoClass = Class.forName("android.content.pm.InstallSourceInfo")
            listOf(
                "getInstallingPackageName",
                "getInitiatingPackageName",
                "getOriginatingPackageName",
                "getUpdateOwnerPackageName"
            ).forEach { methodName ->
                runCatching {
                    XposedBridge.hookAllMethods(
                        installSourceInfoClass,
                        methodName,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam<*>) {
                                if (whatsappInstallSourceObjects[param.thisObject] == true) {
                                    param.result = PLAY_STORE_PACKAGE
                                    logProbe("installer_source", methodName, "return_vending")
                                }
                            }
                        }
                    )
                }
            }
            runCatching {
                XposedBridge.hookAllMethods(
                    installSourceInfoClass,
                    "getPackageSource",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam<*>) {
                            if (whatsappInstallSourceObjects[param.thisObject] == true) {
                                param.result = PACKAGE_SOURCE_STORE
                                logProbe("installer_source", "getPackageSource", "return_store")
                            }
                        }
                    }
                )
            }
        }
    }

    private fun hookHookPackageLookups(packageManagerClass: Class<*>) {
        listOf("getPackageInfo", "getApplicationInfo", "getLaunchIntentForPackage").forEach { methodName ->
            runCatching {
                XposedBridge.hookAllMethods(
                    packageManagerClass,
                    methodName,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam<*>) {
                            val packageName = param.args.firstOrNull() as? String ?: return
                            if (!isHookPackage(packageName)) return
                            if (methodName == "getLaunchIntentForPackage") {
                                param.result = null
                            } else {
                                param.throwable = PackageManager.NameNotFoundException(packageName)
                            }
                            logProbe("hook_package_lookup", packageName, "hidden")
                        }
                    }
                )
            }
        }
    }

    private fun installHookInjectionShield(appClassLoader: ClassLoader) {
        installClassProbeHooks()
        installStackTraceHooks()
        installProcReaderHooks()
        installOnce(hookInjectionBootstrapGuardInstalled) { installHookInjectionBootstrapGuard(appClassLoader) }
        WhatsAppDetectionEventLogger.lifecycle("Hooked WhatsApp hook/injection class, stack, and proc-map shield")
    }

    private fun installFirebaseMessagingGuard(appClassLoader: ClassLoader) {
        runCatching {
            val firebaseMessagingClass = Class.forName("com.google.firebase.messaging.FirebaseMessaging", false, appClassLoader)
            XposedBridge.hookAllMethods(
                firebaseMessagingClass,
                "getInstance",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val throwable = param.throwable ?: return
                        if (!isFirebaseMessagingMissing(throwable)) return
                        if (!isWhatsAppPushRegistrationStack(Thread.currentThread().stackTrace)) return
                        param.throwable = null
                        param.result = null
                        logProbe("firebase_messaging_guard", "FirebaseMessaging.getInstance", "return_null_for_missing_component")
                    }
                }
            )
            WhatsAppDetectionEventLogger.lifecycle("Hooked WhatsApp Firebase Messaging missing-component guard")
        }.onFailure {
            WhatsAppDetectionEventLogger.lifecycle("Firebase Messaging missing-component guard unavailable: ${it.message}")
        }
    }

    private fun installHookInjectionBootstrapGuard(appClassLoader: ClassLoader) {
        hookBootstrapDetectorClassNames.forEach { className ->
            val detectorClass = runCatching {
                Class.forName(className, false, appClassLoader)
            }.getOrNull() ?: return@forEach

            detectorClass.declaredMethods
                .map { it.name }
                .distinct()
                .forEach { methodName ->
                    runCatching {
                        XposedBridge.hookAllMethods(
                            detectorClass,
                            methodName,
                            object : XC_MethodHook() {
                                override fun afterHookedMethod(param: MethodHookParam<*>) {
                                    val throwable = param.throwable ?: return
                                    if (!isHookBootstrapThrowable(throwable)) return
                                    if (!isHookBootstrapStack(Thread.currentThread().stackTrace)) return
                                    param.result = hookBootstrapSafeResult((param.method as java.lang.reflect.Method).returnType)
                                    logProbe("hook_bootstrap_detector", "$className.$methodName", "suppressed_gx9")
                                }
                            }
                        )
                    }
                }
        }
        WhatsAppDetectionEventLogger.lifecycle("Hooked WhatsApp hook/injection bootstrap GX9 guard")
    }

    private fun installClassProbeHooks() {
        runCatching {
            XposedBridge.hookAllMethods(
                Class::class.java,
                "forName",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val className = param.args.firstOrNull() as? String ?: return
                        if (shouldHideClassProbe(className)) {
                            param.throwable = ClassNotFoundException(className)
                            logProbe("hook_class_probe", className, "class_not_found")
                        }
                    }
                }
            )
        }
        runCatching {
            XposedBridge.hookAllMethods(
                ClassLoader::class.java,
                "loadClass",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val className = param.args.firstOrNull() as? String ?: return
                        if (shouldHideClassProbe(className)) {
                            param.throwable = ClassNotFoundException(className)
                            logProbe("hook_classloader_probe", className, "class_not_found")
                        }
                    }
                }
            )
        }
    }

    private fun installStackTraceHooks() {
        runCatching {
            XposedBridge.hookAllMethods(
                Throwable::class.java,
                "getStackTrace",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (callerCheck.get() == true) return
                        val stack = param.result as? Array<StackTraceElement> ?: return
                        param.result = filterHookStackTrace(stack)
                    }
                }
            )
        }
        runCatching {
            XposedBridge.hookAllMethods(
                Thread::class.java,
                "getStackTrace",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        if (callerCheck.get() == true) return
                        val stack = param.result as? Array<StackTraceElement> ?: return
                        param.result = filterHookStackTrace(stack)
                    }
                }
            )
        }
    }

    private fun installProcReaderHooks() {
        hookPathConstructors(FileInputStream::class.java)
        hookPathConstructors(FileReader::class.java)
        hookPathConstructors(RandomAccessFile::class.java)
        hookInputStreamReaderPathPropagation()
        hookBufferedReaderPathPropagation()
        hookBufferedReaderLineSanitizer()
        hookRandomAccessFileLineSanitizer()
    }

    private fun hookPathConstructors(clazz: Class<*>) {
        runCatching {
            XposedBridge.hookAllConstructors(
                clazz,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val path = pathFromArg(param.args.firstOrNull()) ?: return
                        if (!isProcInspectionPath(path)) return
                        when (val instance = param.thisObject) {
                            is Reader -> procPathReaders[instance] = path
                            else -> procPathStreams[instance] = path
                        }
                        logProbe("hook_proc_probe", path, "track_for_line_filter")
                    }
                }
            )
        }
    }

    private fun hookInputStreamReaderPathPropagation() {
        runCatching {
            XposedBridge.hookAllConstructors(
                InputStreamReader::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val path = procPathStreams[param.args.firstOrNull()] ?: return
                        procPathReaders[param.thisObject] = path
                    }
                }
            )
        }
    }

    private fun hookBufferedReaderPathPropagation() {
        runCatching {
            XposedBridge.hookAllConstructors(
                BufferedReader::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val reader = param.args.firstOrNull() ?: return
                        val path = procPathReaders[reader] ?: return
                        procPathReaders[param.thisObject] = path
                    }
                }
            )
        }
    }

    private fun hookBufferedReaderLineSanitizer() {
        runCatching {
            XposedBridge.hookAllMethods(
                BufferedReader::class.java,
                "readLine",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val path = procPathReaders[param.thisObject] ?: return
                        val line = param.result as? String ?: return
                        if (isHookProcLine(line)) {
                            param.result = ""
                            logProbe("hook_proc_line", path, "blank_hook_line")
                        }
                    }
                }
            )
        }
    }

    private fun hookRandomAccessFileLineSanitizer() {
        runCatching {
            XposedBridge.hookAllMethods(
                RandomAccessFile::class.java,
                "readLine",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val path = procPathStreams[param.thisObject] ?: return
                        val line = param.result as? String ?: return
                        if (isHookProcLine(line)) {
                            param.result = ""
                            logProbe("hook_proc_line", path, "blank_hook_line")
                        }
                    }
                }
            )
        }
    }

    private fun installRegistrationSignalHooks() {
        runCatching {
            XposedBridge.hookAllMethods(
                JSONObject::class.java,
                "put",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        val key = param.args.firstOrNull() as? String ?: return
                        val normalized = key.lowercase(Locale.US)
                        if (normalized in installerIntegerKeys || installerIntegerKeys.any { normalized.endsWith("_$it") }) {
                            param.args[1] = PACKAGE_SOURCE_STORE
                            logProbe("installer_json_signal", key, "force_store_source")
                            return
                        }
                        if (normalized in installerStringKeys || installerStringKeys.any { normalized.endsWith("_$it") }) {
                            param.args[1] = PLAY_STORE_PACKAGE
                            logProbe("installer_json_signal", key, "force_vending")
                            return
                        }
                        if (normalized in installerBooleanKeys || installerBooleanKeys.any { normalized.endsWith("_$it") }) {
                            param.args[1] = true
                            logProbe("installer_json_signal", key, "force_true")
                            return
                        }
                        if (detectorLogKeyFragments.any { it in normalized }) {
                            val value = param.args.getOrNull(1)
                            logProbe("detection_json_signal", key, value?.toString()?.take(220).orEmpty())
                        }
                    }
                }
            )
            WhatsAppDetectionEventLogger.lifecycle("Hooked WhatsApp detection and installer JSON signal logger")
        }.onFailure {
            WhatsAppDetectionEventLogger.lifecycle("WhatsApp detection JSON logger unavailable: ${it.message}")
        }
    }

    private fun shouldHideClassProbe(className: String): Boolean {
        if (callerCheck.get() == true || isInternalPurrfectCall()) return false
        return runWithCallerGuard {
            isHookClassName(className)
        }
    }

    private fun filterHookStackTrace(stack: Array<StackTraceElement>): Array<StackTraceElement> {
        val filtered = stack.filterNot { frame ->
            val className = frame.className
            isHookClassName(className) || isHookMethodFrame(className, frame.methodName)
        }
        if (filtered.size != stack.size) {
            logProbe("hook_stack_trace", "StackTraceElement[]", "filtered_${stack.size - filtered.size}")
        }
        return filtered.toTypedArray()
    }

    private fun isHookPackage(packageName: String): Boolean {
        val normalized = packageName.lowercase(Locale.US)
        return normalized in hookPackageNames || hookClassFragments.any { it in normalized }
    }

    private fun isHookClassName(className: String): Boolean {
        val normalized = className.lowercase(Locale.US)
        return hookClassPrefixes.any { className.startsWith(it) } ||
            hookClassFragments.any { it in normalized } ||
            className == "LSPHooker_" ||
            className.startsWith("LSPHooker_")
    }

    private fun isHookMethodFrame(className: String, methodName: String): Boolean {
        val normalizedClass = className.lowercase(Locale.US)
        val normalizedMethod = methodName.lowercase(Locale.US)
        return "nativehooker" in normalizedClass ||
            "hookbridge" in normalizedClass ||
            "lsp_hook" in normalizedClass ||
            "lsp" in normalizedClass && "hook" in normalizedMethod
    }

    private fun isFirebaseMessagingMissing(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        repeat(8) {
            if (current?.message?.contains("Firebase Messaging component is not present") == true) return true
            current = current?.cause
        }
        return false
    }

    private fun isWhatsAppPushRegistrationStack(stack: Array<StackTraceElement>): Boolean {
        return stack.any { frame ->
            frame.className == "com.whatsapp.infra.push.RegistrationIntentService" ||
                frame.className == "com.google.firebase.messaging.FirebaseMessaging" ||
                frame.methodName == "doInBackground" && frame.className.startsWith("X.")
        }
    }

    private fun isProcInspectionPath(path: String): Boolean {
        val normalized = normalizePath(path)
        return procInspectionPaths.any { normalized == it || normalized.endsWith(it) } ||
            (normalized.startsWith("/proc/self/task/") && normalized.endsWith("/maps"))
    }

    private fun isHookProcLine(line: String): Boolean {
        val normalized = line.lowercase(Locale.US)
        return hookClassFragments.any { it in normalized } ||
            "de.robv.android.xposed" in normalized ||
            "org.lsposed" in normalized ||
            "io.github.lsposed" in normalized ||
            "liblspd" in normalized ||
            "libxposed" in normalized ||
            "libsubstrate" in normalized ||
            "rezygisk" in normalized ||
            "zygisk" in normalized && "lsposed" in normalized
    }

    private fun isHookBootstrapThrowable(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        repeat(8) {
            val className = current?.javaClass?.name.orEmpty()
            if (className == "X.GX9" || className.endsWith(".GX9")) return true
            if (className == "java.lang.ExceptionInInitializerError") return true
            if (current is NullPointerException && isBootstrapDetectorNullPointer(current)) return true
            current = current?.cause
        }
        return false
    }

    private fun isBootstrapDetectorNullPointer(throwable: NullPointerException): Boolean {
        val message = throwable.message.orEmpty()
        return "X.0gC.A00" in message ||
            "X.C11430gC.A00" in message ||
            "X.0gD.generatePrivateKey" in message ||
            "X.C11440gD.generatePrivateKey" in message ||
            ("generatePrivateKey" in message && "null object reference" in message) ||
            ("X.0gC" in message && "null object reference" in message)
    }

    private fun isHookBootstrapStack(stack: Array<StackTraceElement>): Boolean {
        return stack.any { frame ->
            val className = frame.className
            (className == "X.0gB" || className == "X.C11420gB") && frame.methodName == "<clinit>"
        } || stack.any { frame ->
            val className = frame.className
            className == "X.0dh" ||
                className == "X.0gT" ||
                className == "X.0eJ" ||
                className == "X.1SC" ||
                className == "X.1SK" ||
                className == "X.0aL" ||
                className == "X.0z9" ||
                className == "X.1Rw" ||
                className == "X.00C" ||
                className == "android.os.HandlerThread" ||
                className == "com.whatsapp.Main" ||
                className == "com.whatsapp.invite.ui.ReferralInviteManager"
        }
    }

    private fun hookBootstrapSafeResult(returnType: Class<*>): Any? {
        return hookBootstrapSafeResult(returnType, mutableSetOf())
    }

    private fun hookBootstrapSafeResult(returnType: Class<*>, seen: MutableSet<Class<*>>): Any? {
        return when {
            returnType == Void.TYPE -> null
            returnType == Boolean::class.javaPrimitiveType || returnType == java.lang.Boolean::class.java -> false
            returnType == Byte::class.javaPrimitiveType || returnType == java.lang.Byte::class.java -> 0.toByte()
            returnType == Short::class.javaPrimitiveType || returnType == java.lang.Short::class.java -> 0.toShort()
            returnType == Int::class.javaPrimitiveType || returnType == java.lang.Integer::class.java -> 0
            returnType == Long::class.javaPrimitiveType || returnType == java.lang.Long::class.java -> 0L
            returnType == Float::class.javaPrimitiveType || returnType == java.lang.Float::class.java -> 0f
            returnType == Double::class.javaPrimitiveType || returnType == java.lang.Double::class.java -> 0.0
            returnType == Char::class.javaPrimitiveType || returnType == Character::class.java -> 0.toChar()
            returnType == String::class.java -> ""
            returnType.isArray -> java.lang.reflect.Array.newInstance(returnType.componentType ?: Any::class.java, 0)
            returnType.isInterface -> bootstrapSafeInterface(returnType)
            else -> bootstrapSafeObject(returnType, seen)
        }
    }

    private fun bootstrapSafeInterface(type: Class<*>): Any? {
        if (!isBootstrapInterfaceType(type)) return null
        bootstrapSafeObjects[type]?.let { return it }
        val instance = instantiateCurve25519Provider(type) ?: runCatching {
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
                bootstrapInterfaceResult(proxy, method, args)
            }
        }.getOrNull()
        if (instance != null) {
            bootstrapSafeObjects[type] = instance
            logProbe("hook_bootstrap_safe_interface", type.name, "created")
        }
        return instance
    }

    private fun bootstrapSafeObject(type: Class<*>, seen: MutableSet<Class<*>>): Any? {
        if (!isBootstrapObjectType(type) || !seen.add(type)) return null
        bootstrapSafeObjects[type]?.let { return it }

        val instance = runCatching {
            type.declaredConstructors.firstOrNull { it.parameterTypes.isEmpty() }?.let { constructor ->
                constructor.isAccessible = true
                constructor.newInstance()
            }
        }.getOrNull() ?: unsafeAllocate(type)

        if (instance != null) {
            bootstrapSafeObjects[type] = instance
            hydrateBootstrapObject(instance, seen)
        }
        seen.remove(type)
        return instance
    }

    private fun hydrateBootstrapObject(instance: Any, seen: MutableSet<Class<*>>) {
        var current: Class<*>? = instance.javaClass
        while (current != null && current != Any::class.java) {
            current.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers)) return@forEach
                val fieldType = field.type
                val value = hookBootstrapSafeResult(fieldType, seen).takeIf {
                    it != null || !fieldType.isPrimitive
                } ?: return@forEach
                runCatching {
                    field.isAccessible = true
                    if (field.get(instance) == null || fieldType.isPrimitive) {
                        field.set(instance, value)
                    }
                }
            }
            current = current.superclass
        }
    }

    private fun isBootstrapObjectType(type: Class<*>): Boolean {
        if (type.isPrimitive || type.isArray || type.isInterface || type.isAnnotation || type.isEnum) return false
        if (Modifier.isAbstract(type.modifiers)) return false
        val name = type.name
        return name.startsWith("X.") || name.startsWith("com.whatsapp.")
    }

    private fun isBootstrapInterfaceType(type: Class<*>): Boolean {
        val name = type.name
        return name.startsWith("X.") || name.startsWith("com.whatsapp.") ||
            type.declaredMethods.any { method ->
                method.name == "generatePrivateKey" ||
                    method.name == "generatePublicKey" ||
                    method.name == "calculateAgreement"
            }
    }

    private fun instantiateCurve25519Provider(type: Class<*>): Any? {
        return listOf(
            "org.whispersystems.curve25519.OpportunisticCurve25519Provider",
            "org.whispersystems.curve25519.JavaCurve25519Provider",
            "org.whispersystems.curve25519.NativeCurve25519Provider"
        ).firstNotNullOfOrNull { className ->
            runCatching {
                val providerClass = Class.forName(className, false, type.classLoader)
                val provider = providerClass.getDeclaredConstructor().newInstance()
                provider.takeIf { type.isInstance(it) }
            }.getOrNull()
        }
    }

    private fun bootstrapInterfaceResult(proxy: Any, method: java.lang.reflect.Method, args: Array<Any?>?): Any? {
        if (method.declaringClass == Any::class.java) {
            return when (method.name) {
                "toString" -> "WhatsAppBootstrapSafeProxy(${proxy.javaClass.interfaces.firstOrNull()?.name.orEmpty()})"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }
        return when (method.name) {
            "generatePrivateKey" -> randomCurve25519PrivateKey()
            "generatePublicKey",
            "calculateAgreement" -> randomBytes(32)
            "calculateSignature" -> randomBytes(64)
            "getRandom" -> randomBytes((args?.firstOrNull() as? Int)?.coerceAtLeast(0) ?: 32)
            "isTorsionFree",
            "verifySignature" -> true
            else -> hookBootstrapSafeResult(method.returnType)
        }
    }

    private fun randomCurve25519PrivateKey(): ByteArray {
        val bytes = randomBytes(32)
        bytes[0] = (bytes[0].toInt() and 248).toByte()
        bytes[31] = ((bytes[31].toInt() and 127) or 64).toByte()
        return bytes
    }

    private fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        bootstrapRandom.nextBytes(bytes)
        return bytes
    }

    private fun unsafeAllocate(type: Class<*>): Any? {
        return runCatching {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafeField = unsafeClass.getDeclaredField("theUnsafe")
            unsafeField.isAccessible = true
            val unsafe = unsafeField.get(null)
            unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type)
        }.getOrNull()
    }

    private fun pathFromArg(arg: Any?): String? {
        return when (arg) {
            is File -> arg.path
            is String -> arg
            else -> null
        }
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').lowercase(Locale.US)
    }

    private fun setStaticStringField(clazz: Class<*>, fieldName: String, value: String) {
        runCatching {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        }.onFailure {
            WhatsAppDetectionEventLogger.lifecycle("Unable to set ${clazz.name}.$fieldName: ${it.message}")
        }
    }

    private fun safeBuildFingerprint(): String {
        val current = Build.FINGERPRINT.orEmpty()
        if (looksStockBuildValue(current)) return current
        val brand = safeBuildPart(Build.BRAND, "google")
        val product = safeBuildPart(Build.PRODUCT, "oriole")
        val device = safeBuildPart(Build.DEVICE, product)
        val release = safeBuildPart(Build.VERSION.RELEASE, "16")
        val id = safeBuildPart(Build.ID, "BP1A.260505.005")
        return "$brand/$product/$device:$release/$id/${safeBuildIncremental()}:user/release-keys"
    }

    private fun safeBuildDisplay(): String {
        val display = Build.DISPLAY.orEmpty()
        return if (looksStockBuildValue(display)) display else safeBuildIncremental()
    }

    private fun safeBuildIncremental(): String {
        val incremental = Build.VERSION.INCREMENTAL.orEmpty()
        return if (looksStockBuildValue(incremental)) incremental else "14256728"
    }

    private fun safeBuildPart(value: String?, fallback: String): String {
        val clean = value?.takeIf { looksStockBuildValue(it) } ?: fallback
        return clean.replace(' ', '_')
    }

    private fun looksStockBuildValue(value: String): Boolean {
        val normalized = value.lowercase(Locale.US)
        if (normalized.isBlank()) return false
        return listOf(
            "lineage",
            "cyanogenmod",
            "crdroid",
            "evolution",
            "pixelos",
            "grapheneos",
            "calyxos",
            "test-keys",
            "dev-keys",
            "userdebug",
            "eng"
        ).none { it in normalized }
    }

    private fun isInternalPurrfectCall(): Boolean {
        return runWithCallerGuard {
            Thread.currentThread().stackTrace.any { it.className.startsWith("me.eternal.purrfect") }
        }
    }

    private inline fun <T> runWithCallerGuard(block: () -> T): T {
        val previous = callerCheck.get()
        callerCheck.set(true)
        return try {
            block()
        } finally {
            callerCheck.set(previous)
        }
    }

    private fun logProbe(type: String, detail: String, action: String) {
        WhatsAppDetectionEventLogger.probe(type, detail, action)
    }
}
