package com.example.hermesmobile.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.verticalscroll.*
import com.termux.termuxapp.terminal.TerminalSession
import com.termux.termuxapp.terminal.TerminalSessionClient
import kotlinx.coroutines.*
import java.io.OutputStream

/**
 * TerminalIOScheduler bridges a TerminalSession's PTY I/O to the Hermes gateway.
 *
 * Responsibilities:
 * 1. Read text from the TerminalSession (user input or command output) and send it
 *    to the Hermes gateway as a chat message (via ChatRepository).
 * 2. Receive gateway-turn output (tool results, model text) and write it into the
 *    TerminalSession so it appears in the terminal display.
 *
 * Design:
 * - Uses a coroutine with a Channel<String> as the inbound/outbound conduit.
 * - The "forwardToGateway" branch reads from the channel and calls
 *   ChatRepository.sendMessage().
 * - The "fromGateway" branch is driven by MainScreen passing in a callback;
 *   it writes strings into the channel, which the scheduler then forwards
 *   to the TerminalSession's write mechanism.
 *
 * Note: TerminalSession.write(byte[], int, int) is the API for feeding text into
 * the PTY. We wrap it in a convenient suspend function here.
 */
object TerminalIOScheduler {

    /** Max characters to send per batch to the gateway; prevents oversized messages. */
    private const val MAX_GATEWAY_MESSAGE_CHARS = 1000

    /** Channel carrying text from the gateway back into the terminal display. */
    private val gatewayToTerminalChannel = Channel<String>(64)

    /**
     * Must be called once on a UI thread context after the TerminalSession is attached.
     * Starts the I/O scheduler coroutine.
     *
     * @param session the TerminalSession whose PTY we are bridging
     * @param forwardToGateway a suspend fun that accepts a String message and
 *                         sends it to the Hermes gateway (e.g. via ChatRepository)
     * @param fromGateway a suspend fun that receives text from the gateway and
 *                    should write it into the TerminalSession PTY (via write())
 * @param context   used to obtain the Looper for coroutine dispatchers
     */
    fun start(
        session: TerminalSession,
        forwardToGateway: (String) -> Unit,
        fromGateway: (String) -> Unit,
        context: Context
    ) {
        // Ensure we start on the UI thread's looper so UI callbacks are safe.
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        coroutineScope.launch {
            _terminalIOLoop(session, forwardToGateway, fromGateway)
        }
    }

    /** Main loop: concurrently forward PTY↔gateway. */
    private suspend fun _terminalIOLoop(
        session: TerminalSession,
        forwardToGateway: (String) -> Unit,
        fromGateway: (String) -> Unit
    ) {
        // Branch 1: PTY → Gateway
        // Reads text from the session client's onText callback (already handled
        // at a higher level; here we just forward any chars that arrive via the
        * session's internal buffer). We use a simple scan: accumulate chars until
        * newline or max length, then forward.
        //
        // In practice, the TerminalSessionClient.onText already forwards user-typed
        * text to the app's logic (MainScreen). What we do here is re-emit any text
        * that the session captures so it can be sent to the gateway.
        val ptyToGatewayJob = lifecycleScope.launch {
            var buffer = StringBuilder()
            while (isActive) {
                // We peek at the session's latest text via a custom mechanism.
                // Since TerminalSession doesn't expose a read API from Java outside
                * the client, we rely on the fact that the app already forwards user
                * input to the gateway. What we add here is: if the gateway sends
                * text back (via fromGateway callback), we write it into the PTY
                * so it scrolls in the terminal.
                delay(100) // small poll
            }
        }

        // Branch 2: Gateway → PTY
        // Receives text from the gateway (e.g. tool output, model response) and
        // writes it into the TerminalSession PTY so it appears in the terminal display.
        val gatewayToTerminalJob = lifecycleScope.launch {
            while (isActive) {
                val msg = gatewayToTerminalChannel.receive()
                if (msg.isNotBlank()) {
                    // Write into the PTY using the session's writer.
                    // TerminalSession.write expects bytes + offset + length.
                    // We encode as UTF-8 and write a simulated "user output" line.
                    val encoded = msg.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                    try {
                        session.write(encoded, 0, encoded.size)
                    } catch (_: Exception) {
                        // Session may be closed; ignore
                    }
                    // Also deliver to the fromGateway callback so MainScreen can
                    // update its own UI if desired.
                    fromGateway(msg)
                }
            }
        }

        // Keep the job alive for the scope; cancel both when the object is no longer needed.
        session.closeRequested().onCancellation {
            ptyToGatewayJob.cancel()
            gatewayToTerminalJob.cancel()
        }
    }
}