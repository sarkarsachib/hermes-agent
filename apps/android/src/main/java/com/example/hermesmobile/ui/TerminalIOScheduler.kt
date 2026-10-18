package com.example.hermesmobile.ui

/**
 * Placeholder for the PTY-to-gateway bridge. The previous implementation relied
 * on non-existent lifecycle/session callbacks and was not connected to the UI.
 * Keep the feature unavailable until it is rebuilt against the selected Termux API.
 */
object TerminalIOScheduler {
    fun isSupported(): Boolean = false
}
