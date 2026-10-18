package com.example.hermesmobile.ui

import android.content.Context
import android.util.Log
import com.termux.termuxapp.terminal.TerminalView
import com.termux.termuxapp.terminal.TerminalSession
import com.termux.termuxapp.terminal.TerminalSessionClient
import com.termux.termuxapp.terminal.TerminalViewClient
import com.termux.termuxapp.terminal.TerminalViewConstants
import java.io.File

// Proot bootstrap and TerminalSession setup
object TerminalManager {

    // Launch a TerminalSession running proot with a Debian ARM64 rootfs
    // The rootfs is expected at /data/data/<pkg>/files/linux/rootfs
    // proot binary is expected at native library dir (jniLibs/arm64-v8a/libproot.so)
    fun attachTerminal(view: TerminalView, context: Context, agentOutput: String.() -> Unit): TerminalSession {
        val rootfs = File(context.filesDir, "linux/rootfs")
        val proot = File(context.applicationInfo.nativeLibraryDir, "libproot.so")

        // Build proot arguments: bind /dev, /proc, /sys and launch /bin/sh
        val args = arrayOf(
            "--link2symlink",
            "-0",
            "-r", rootfs.absolutePath,
            "-w", "/",
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "/bin/sh", "-l"
        )

        val env = arrayOf(
            "HOME=/root",
            "TERM=xterm-256color",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "ANDROID_ROOT=/data/data/${context.packageName}",
            "PREFIX=/data/data/${context.packageName}/files"
        )

        val session = TerminalSession(
            proot.absolutePath,
            rootfs.absolutePath,
            args,
            env,
            10_000, // max session size in bytes
            object : TerminalSessionClient {
                override fun onText(text: String) {
                    // User typed text into the terminal — forward to agent
                    agentOutput(text)
                }

                override fun onExit(exitCode: Int) {
                    Log.d("TerminalManager", "Proot session exited with code $exitCode")
                }

                override fun onTitle(title: String?) {
                    // Optional: update UI title
                }

                override fun onClipboard(text: String?, action: Int) {
                    // Clipboard interactions
                }

                override fun onScroll(delta: Int) {
                    // Terminal scroll
                }

                override fun onScrollChanged(oldScroll: Int, newScroll: Int) {
                    // Scroll position changed
                }

                override fun onSessionRunningChanged(isRunning: Boolean) {
                    Log.d("TerminalManager", "Session running changed: $isRunning")
                }

                override fun onProcessDied() {
                    Log.w("TerminalManager", "Proot process died")
                }
            }
        )

        view.attachSession(session)
        return session
    }

    // Minimal TerminalViewClient for input forwarding
    fun bindTerminalViewClient(view: TerminalView, inputCallback: (String) -> Unit) {
        view.setTerminalViewClient(object : TerminalViewClient {
            override fun onKeyDown(keyCode: Int, keyEvent: android.view.KeyEvent?): Boolean {
                // Forward hardware key presses to terminal
                return false
            }

            override fun onKeyUp(keyCode: Int, keyEvent: android.view.KeyEvent?): Boolean {
                return false
            }

            override fun onTextInput(text: String) {
                // User typing in the text input area
                inputCallback(text)
            }

            override fun onSendPressed(text: String) {
                // Send button pressed
                inputCallback(text)
            }

            override fun onModifierChanged(hasShift: Boolean, hasCtrl: Boolean, hasAlt: Boolean) {
                // Modifier key state changes
            }

            override fun onScaleChanged(scale: Float) {
                // Terminal scale factor
            }

            override fun onCursorMoved(line: Int, column: Int) {
                // Cursor position
            }

            override fun onSelectionChanged(start: Int, end: Int, reversed: Boolean) {
                // Text selection state
            }

            override fun onLog(line: String) {
                // Log output from the terminal emulator
                Log.d("TerminalView", line)
            }

            override fun onScaleChanging(scale: Float, instant: Boolean) {
                // Scale change in progress
            }
        })
    }
}