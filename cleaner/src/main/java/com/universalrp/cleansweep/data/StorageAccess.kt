package com.universalrp.cleansweep.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Helpers to open the right Settings pages on any ROM (MIUI / HyperOS included). */
object StorageAccess {

    fun requestAllFilesAccess(context: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        // 1) Direct page for this app (preferred).
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + context.packageName)
                ).addFlags(flags)
            )
            return
        } catch (e: Exception) {
            // Some ROMs don't have the per-app page; fall through.
        }
        // 2) The generic "All files access" list.
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(flags)
            )
            return
        } catch (e: Exception) {
            // Fall through.
        }
        // 3) Last resort: this app's detail page.
        openAppInfo(context, context.packageName)
    }

    fun openAppInfo(context: Context, pkg: String) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$pkg")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // Ignored: nothing sensible to do if Settings refuses.
        }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // Ignored.
        }
    }
}
