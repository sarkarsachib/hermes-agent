package com.example.hermesmobile.ui

/**
 * Placeholder for the Termux-backed PTY integration. The branch's prior client
 * implementation targeted a different Termux callback API and was not wired into
 * the Android screen. Keep this capability explicitly unavailable rather than
 * advertising a terminal session that cannot be started.
 */
object TerminalManager {
    fun isSupported(): Boolean = false
}
