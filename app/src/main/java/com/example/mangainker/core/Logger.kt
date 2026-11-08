package com.example.mangainker.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    const val TAG = "MI_GESTURE"
    private const val MAX_LINES = 250
    private val mutableLines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = mutableLines.asStateFlow()

    fun info(message: String) = append(Log.INFO, message)
    fun warning(message: String) = append(Log.WARN, message)
    fun error(message: String) = append(Log.ERROR, message)

    private fun append(priority: Int, message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "$time $message"
        when (priority) {
            Log.WARN -> Log.w(TAG, line)
            Log.ERROR -> Log.e(TAG, line)
            else -> Log.i(TAG, line)
        }
        mutableLines.update { (it + line).takeLast(MAX_LINES) }
    }
}
