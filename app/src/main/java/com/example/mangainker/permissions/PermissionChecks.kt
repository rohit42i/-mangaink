package com.example.mangainker.permissions

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.os.PowerManager
import com.example.mangainker.gesture.GestureAccessibilityService

object PermissionChecks {
    fun overlayGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun accessibilityGranted(context: Context): Boolean {
        val expected = ComponentName(context, GestureAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(':').any { entry ->
            ComponentName.unflattenFromString(entry)?.let { it == expected } == true
        }
    }

    fun batteryOptimizationExempt(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

}
