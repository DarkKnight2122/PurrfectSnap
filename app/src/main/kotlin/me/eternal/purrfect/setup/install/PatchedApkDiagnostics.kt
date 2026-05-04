package me.eternal.purrfect.setup.install

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

object PatchedApkDiagnostics {
    fun inspect(context: Context, apk: File, expectedPackageName: String): List<String> {
        val lines = mutableListOf<String>()
        lines += "APK diagnostics:"
        lines += "- path: ${apk.absolutePath}"
        lines += "- size: ${apk.length()} bytes"
        lines += "- sha256: ${apk.sha256()}"
        lines += "- expected package: $expectedPackageName"
        lines += inspectZip(apk)
        lines += inspectPackageParser(context, apk, expectedPackageName)
        lines += inspectSignatures(apk)
        return lines
    }

    private fun inspectZip(apk: File): String {
        return runCatching {
            ZipFile(apk).use { zip ->
                val entries = zip.entries().asSequence().toList()
                val hasManifest = zip.getEntry("AndroidManifest.xml") != null
                val dexCount = entries.count { it.name.startsWith("classes") && it.name.endsWith(".dex") }
                val nativeCount = entries.count { it.name.endsWith(".so") }
                val hasLspatchConfig = zip.getEntry("assets/lspatch/config.json") != null
                val hasOriginApk = zip.getEntry("assets/lspatch/origin.apk") != null
                "- zip: ok entries=${entries.size}, manifest=$hasManifest, dex=$dexCount, native=$nativeCount, config=$hasLspatchConfig, origin=$hasOriginApk"
            }
        }.getOrElse { "- zip: failed ${it.javaClass.simpleName}: ${it.message}" }
    }

    @Suppress("DEPRECATION")
    private fun inspectPackageParser(context: Context, apk: File, expectedPackageName: String): String {
        return runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    apk.absolutePath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            }
            val packageName = packageInfo?.packageName
            val versionName = packageInfo?.versionName
            val versionCode = packageInfo?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong()
            }
            "- package parser: ${if (packageInfo == null) "failed/null" else "ok package=$packageName version=$versionName code=$versionCode matches=${packageName == expectedPackageName}"}"
        }.getOrElse { "- package parser: threw ${it.javaClass.simpleName}: ${it.message}" }
    }

    private fun inspectSignatures(apk: File): String {
        return runCatching {
            val result = ApkVerifier.Builder(apk)
                .setMinCheckedPlatformVersion(Build.VERSION.SDK_INT)
                .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT)
                .build()
                .verify()
            val errors = result.errors.take(3).joinToString("; ") { it.toString() }
            val warnings = result.warnings.take(3).joinToString("; ") { it.toString() }
            "- signatures: verified=${result.isVerified}, v1=${result.isVerifiedUsingV1Scheme}, v2=${result.isVerifiedUsingV2Scheme}, v3=${result.isVerifiedUsingV3Scheme}, errors=${errors.ifBlank { "none" }}, warnings=${warnings.ifBlank { "none" }}"
        }.getOrElse { "- signatures: threw ${it.javaClass.simpleName}: ${it.message}" }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
