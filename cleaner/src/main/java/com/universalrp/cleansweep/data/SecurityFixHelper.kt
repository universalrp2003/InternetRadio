package com.universalrp.cleansweep.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object SecurityFixHelper {

    fun openAppInfo(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (ignored: Exception) {}
        }
    }

    fun openSpecificSettings(context: Context, findingId: String) {
        val intent = when (findingId) {
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "notification_listeners" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            "install_other_apps" -> Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            "usage_access" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            "no_lock" -> Intent(Settings.ACTION_SECURITY_SETTINGS)
            "developer_options" -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            } catch (ignored: Exception) {}
        }
    }

    fun getDeviceGuidance(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val model = Build.MODEL
        val isXiaomi = manufacturer.contains("xiaomi") || brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")
        val isSamsung = manufacturer.contains("samsung")
        val isOppoRealme = manufacturer.contains("oppo") || brand.contains("realme") || brand.contains("oneplus")
        val isVivo = manufacturer.contains("vivo") || brand.contains("iqoo")

        return when {
            isXiaomi -> "Phone: $model (MIUI / HyperOS)\n" +
                "• For 'Restricted setting': Go to Settings → Apps → Manage apps → Select Live Guard → Tap (⋮) 3 dots top right → 'Allow restricted settings'.\n" +
                "• To reach 100/100: Turn off Unknown app installation for browsers/files and verify banking SMS permissions."
            isSamsung -> "Phone: $model (Samsung One UI)\n" +
                "• Settings → Security and privacy → Permission manager.\n" +
                "• Turn off 'Install unknown apps' for non-store apps to score 100/100."
            isOppoRealme -> "Phone: $model (ColorOS / Realme UI / OxygenOS)\n" +
                "• Settings → Apps → Special app access → Unknown apps / SMS.\n" +
                "• Revoke high-risk permissions from non-essential apps for 100/100."
            isVivo -> "Phone: $model (Funtouch OS / OriginOS)\n" +
                "• Settings → Apps & permissions → Permission management.\n" +
                "• Review special access to achieve 100/100 safe score."
            else -> "Phone: $model (Android ${Build.VERSION.RELEASE})\n" +
                "• Settings → Apps → Special app access.\n" +
                "• Check SMS and Unknown app installation to achieve 100/100."
        }
    }
}
