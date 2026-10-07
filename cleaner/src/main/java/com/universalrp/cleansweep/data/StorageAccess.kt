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

    /**
     * Opens the Storage page of another app straight away.
     * From there the user taps "Clear cache" — this is the two-tap guided flow
     * that replaces the old Accessibility automation in v1.3.
     */
    fun openAppStorage(context: Context, pkg: String) {
        // Most ROMs (including MIUI/HyperOS) accept this undocumented-but-stable action.
        try {
            context.startActivity(
                Intent("android.intent.action.APPLICATION_DETAILS_STORAGE_SETTINGS")
                    .setPackage("com.android.settings")
                    .putExtra("package", pkg)
                    .putExtra(":settings:show_fragment_args", android.os.Bundle().apply {
                        putString("package", pkg)
                    })
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        } catch (e: Exception) {
            // Fall through to the app-info page.
        }
        openAppInfo(context, pkg)
    }
}
