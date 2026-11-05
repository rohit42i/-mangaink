package com.example.mangainker.overlay

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AssistantEnableResult {
    START_REQUESTED,
    ALREADY_RUNNING,
    NEEDS_OVERLAY_PERMISSION,
    START_FAILED
}

/** Separates the user's requested setting from the live foreground-service state. */
data class FloatingAssistantState(
    val requested: Boolean = false,
    val running: Boolean = false,
    val error: String? = null
) {
    fun requestEnable(): FloatingAssistantState = copy(requested = true, error = null)

    fun requestDisable(): FloatingAssistantState = copy(requested = false, error = null)

    fun serviceStarted(): FloatingAssistantState = copy(running = true, error = null)

    fun awaitingPermission(): FloatingAssistantState =
        copy(running = false, error = "Display over other apps permission is required.")

    fun serviceFailed(reason: String): FloatingAssistantState =
        copy(running = false, error = reason)

    fun serviceStopped(): FloatingAssistantState = copy(
        running = false,
        error = when {
            !requested -> null
            error != null -> error
            else -> "Floating controls stopped unexpectedly. Turn them on again."
        }
    )
}

object OverlayActivation {
    private const val PREFERENCES_NAME = "floating_overlay_preferences"
    private const val KEY_ASSISTANT_ENABLED = "assistant_enabled"

    private val mutableState = MutableStateFlow(FloatingAssistantState())
    val state: StateFlow<FloatingAssistantState> = mutableState.asStateFlow()

    fun isRequested(context: Context): Boolean =
        preferences(context).getBoolean(KEY_ASSISTANT_ENABLED, false)

    fun enable(context: Context): AssistantEnableResult {
        val appContext = context.applicationContext
        preferences(appContext).edit().putBoolean(KEY_ASSISTANT_ENABLED, true).apply()
        mutableState.update { it.requestEnable() }

        if (!Settings.canDrawOverlays(appContext)) {
            mutableState.update { it.awaitingPermission() }
            return AssistantEnableResult.NEEDS_OVERLAY_PERMISSION
        }
        if (mutableState.value.running) return AssistantEnableResult.ALREADY_RUNNING
        return startService(appContext)
    }

    fun disable(context: Context) {
        val appContext = context.applicationContext
        markDisabled(appContext)
        try {
            val stopped = appContext.stopService(Intent(appContext, FloatingPanelService::class.java))
            if (!stopped) mutableState.update { it.serviceStopped() }
        } catch (error: Exception) {
            mutableState.update {
                it.serviceFailed("Could not stop floating controls: " + (error.message ?: error.javaClass.simpleName))
            }
        }
    }

    /** Mark OFF without issuing stopService; used by the service's own notification action. */
    fun markDisabled(context: Context) {
        preferences(context.applicationContext).edit().putBoolean(KEY_ASSISTANT_ENABLED, false).apply()
        mutableState.update { it.requestDisable() }
    }

    /**
     * Reconcile persisted intent with actual service state from a visible activity.
     * This avoids attempting a background foreground-service start.
     */
    fun restoreIfRequested(context: Context) {
        val appContext = context.applicationContext
        if (!isRequested(appContext)) {
            mutableState.update { it.requestDisable() }
            return
        }

        mutableState.update { it.requestEnable() }
        if (!Settings.canDrawOverlays(appContext)) {
            mutableState.update { it.awaitingPermission() }
            appContext.stopService(Intent(appContext, FloatingPanelService::class.java))
            return
        }
        if (!mutableState.value.running) startService(appContext)
    }

    fun serviceStarted() {
        mutableState.update { it.serviceStarted() }
    }

    fun awaitingOverlayPermission() {
        mutableState.update { it.awaitingPermission() }
    }

    fun serviceStartFailed(reason: String) {
        mutableState.update { it.serviceFailed(reason) }
    }

    fun serviceStopped() {
        mutableState.update { it.serviceStopped() }
    }

    private fun startService(context: Context): AssistantEnableResult {
        return try {
            val intent = Intent(context, FloatingPanelService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            AssistantEnableResult.START_REQUESTED
        } catch (error: Exception) {
            mutableState.update {
                it.serviceFailed("Could not start floating controls: " + (error.message ?: error.javaClass.simpleName))
            }
            AssistantEnableResult.START_FAILED
        }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
