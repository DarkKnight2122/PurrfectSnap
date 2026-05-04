package me.eternal.purrfect.setup.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

object ApkInstaller {
    private const val ACTION_INSTALL_STATUS = "me.eternal.purrfect.action.APK_INSTALL_STATUS"

    fun install(
        context: Context,
        apk: File,
        packageName: String,
        printLog: (String) -> Unit = {}
    ) {
        require(apk.exists() && apk.length() > 0L) {
            "APK is missing or empty: ${apk.absolutePath}"
        }

        printLog("Starting PackageInstaller session for $packageName (${apk.length()} bytes).")
        installWithPackageInstaller(context, apk, packageName)
        printLog("PackageInstaller session committed. Confirm the system install prompt.")
    }

    private fun installWithPackageInstaller(context: Context, apk: File, packageName: String) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callbackIntent = Intent(context, ApkInstallStatusReceiver::class.java).apply {
                action = ACTION_INSTALL_STATUS
                putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
                putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pendingIntent.intentSender)
        } catch (throwable: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw throwable
        } finally {
            session.close()
        }
    }
}
