package com.universalrp.cleansweep.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle

object SecurityFixHelper {
    data class OpenResult(val opened: Boolean, val direct: Boolean, val guidance: String)

    fun openAppInfo(context: Context, packageName: String): OpenResult =
        openSpecificSettings(context, "app_info", FindingApp(packageName, packageName))

    fun openSpecificSettings(
        context: Context,
        findingId: String,
        app: FindingApp? = null,
    ): OpenResult {
        val guidance = SecuritySettingsRoutes.instructions(findingId, app)
        val destinations = SecuritySettingsRoutes.destinations(
            findingId, app, Build.VERSION.SDK_INT, Build.MANUFACTURER, Build.BRAND,
        )
        for (destination in destinations) {
            val intent = Intent(destination.action).apply {
                destination.dataPackage?.let { data = Uri.fromParts("package", it, null) }
                if (destination.activityPackage != null && destination.activityClass != null) {
                    setClassName(destination.activityPackage, destination.activityClass)
                }
                destination.extras.forEach { (key, value) -> putExtra(key, value) }
                destination.extras[SecuritySettingsRoutes.EXTRA_HIGHLIGHT]?.let { key ->
                    putExtra(":settings:show_fragment_args", Bundle().apply {
                        putString(SecuritySettingsRoutes.EXTRA_HIGHLIGHT, key)
                    })
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                // resolveActivity() can give a false negative under package visibility rules.
                // An absent, protected or non-exported OEM activity is a normal fallback case.
                context.startActivity(intent)
                return OpenResult(true, destination.direct, guidance)
            } catch (e: Exception) {
                // Try the next targeted destination, never the unrelated Settings homepage.
            }
        }
        return OpenResult(
            opened = false,
            direct = false,
            guidance = "This phone did not expose a settings shortcut. Open this path manually: $guidance",
        )
    }

    fun getDeviceGuidance(): String = SecuritySettingsRoutes.deviceGuidance(
        Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.VERSION.RELEASE,
    )
}
