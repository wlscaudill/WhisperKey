package com.whisperkey.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Simple logger that captures logs for display in the app.
 */
object Logger {
    private const val TAG = "WhisperKey"
    private const val MAX_LOGS = 500

    private val logs = CopyOnWriteArrayList<LogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String
    ) {
        fun format(): String {
            val time = dateFormat.format(Date(timestamp))
            return "[$time] $level/$tag: $message"
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        addLog("D", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        addLog("I", tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        addLog("W", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
            addLog("E", tag, "$message: ${throwable.message}")
        } else {
            Log.e(tag, message)
            addLog("E", tag, message)
        }
    }

    private fun addLog(level: String, tag: String, message: String) {
        logs.add(LogEntry(System.currentTimeMillis(), level, tag, message))
        while (logs.size > MAX_LOGS) {
            logs.removeAt(0)
        }
    }

    fun getLogs(): List<LogEntry> = logs.toList()

    fun getLogsAsString(): String {
        return logs.joinToString("\n") { it.format() }
    }

    fun clear() {
        logs.clear()
    }
}
