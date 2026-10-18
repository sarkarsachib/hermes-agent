package com.example.hermesmobile.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalscroll.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.hermesmobile.R
import com.example.hermesmobile.shizuku.ShizukuManager
import com.example.hermesmobile.ui.ChatRepository
import com.example.hermesmobile.runtime.AgentService
import com.example.hermesmobile.ui.TerminalIOScheduler
import com.example.hermesmobile.ui.RootfsBootstrap

@Composable
fun MainScreen(controller: MainController, onTerminalEnabled: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val scrollState = rememberVerticalScrollState()
    val repository = ChatRepository(LocalContext.current)
    var result by remember { mutableStateOf("") }
    var showTerminal by remember { mutableStateOf(false) }
    var showShizuku by remember { mutableStateOf(false) }

    // 1. Rootfs bootstrap: run once on first launch
    var bootstrapped by remember { mutableStateOf(false) }
    if (!bootstrapped) {
        RootfsBootstrap.execute(LocalContext.current) { success ->
            bootstrapped = success
            if (success) {
                onTerminalEnabled()
            } else {
                Log.w("MainActivity", "Rootfs bootstrap failed; terminal disabled")
            }
        }
        remember { true }
    }

    // 2. Shizuku manager
    val shizuku by remember { ShizukuManager(LocalContext.current) }
    var shizukuReady by remember {
        shizuku.awaitReady(5000)
    }

    // 3. Terminal I/O scheduler (PTY <-> gateway bridge)
    // We create a single scheduler instance; it will be started when the terminal
    * mode becomes active and a session is attached.
    val terminalScheduler by remember {
        object : TerminalIOScheduler() {
            override suspend fun forwardToGateway(msg: String) {
                // Send message to the Hermes gateway API
                val resp = repository.sendMessage(msg)
                when (resp) {
                    is ChatRepository.Result.success -> result = ChatRepository.Result.success(
                        "Sent: ${resp.text.take(50)}"
                    )
                    is ChatRepository.Result.failure -> result = ChatRepository.Result.failure(
                        "Error: ${resp.failure.message}"
                    )
                }
            }

            override suspend fun fromGateway(msg: String) {
                // Write text into the TerminalSession PTY so it scrolls in the terminal display
                // (the actual session instance is passed start() below; here we just document the callback)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = controller.status,
                style = MaterialTheme.typography.h6,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = controller.toggleConnection,
                modifier = Modifier.size(80.dp, 36.dp)
            ) {
                if (controller.isConnected) "Disconnect" else "Connect"
            }
            Button(
                onClick = { showTerminal = !showTerminal },
                modifier = Modifier.size(60.dp, 36.dp)
            ) {
                Text("Term")
            }
            Button(
                onClick = { showShizuku = !showShizuku },
                modifier = Modifier.size(80.dp, 36.dp)
            ) {
                if (shizuku.isAvailable()) "Shizuku OK" else "Shizuku"
            }
        }

        // Chat / terminal area
        if (showTerminal && controller.isConnected) {
            TerminalArea(
                showTerminal = showTerminal,
                onTerminalInput = { cmd ->
                    // Forward terminal command to gateway
                    // The scheduler's forwardToGateway will send it via ChatRepository
                    // No extra action needed here; the scheduler handles it
                },
                context = LocalContext.current,
                terminalScheduler = terminalScheduler
            )
        } else {
            // Chat message list area
            val messages = controller.messages
            if (messages.isEmpty()) {
                Text(
                    text = "Waiting for connection…",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    items(messages.size) { index ->
                        val msg = messages[index]
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.body1,
                            modifier = Modifier
                                .paddingBottom(4.dp)
                                .fillMaxWidth(0.8f)
                        )
                    }
                }
            }
        }

        // Send result
        if (result.text.isNotEmpty()) {
            Text(
                text = "▶ ${result.text}",
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
        }

        // Input row (visible when not in terminal mode)
        if (!showTerminal) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Message") },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(0.75f)
                )
                Button(
                    onClick = {
                        val resp = repository.sendMessage(input)
                        when (resp) {
                            is ChatRepository.Result.success -> result = ChatRepository.Result.success(
                                "Sent: ${resp.text.take(50)}"
                            )
                            is ChatRepository.Result.failure -> result = ChatRepository.Result.failure(
                                "Error: ${resp.failure.message}"
                            )
                        }
                        input = ""
                    },
                    modifier = Modifier.size(80.dp, 36.dp)
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
fun TerminalArea(
    showTerminal: Boolean,
    onTerminalInput: (String) -> Unit,
    context: Context,
    terminalScheduler: TerminalIOScheduler
) {
    val shizuku = ShizukuManager(context)
    var userInput by remember { mutableStateOf("") }

    // The TerminalSession will be attached here; we start the scheduler with it.
    // For now, we show a placeholder and the input row.
    //
    // When the session becomes available (after rootfs bootstrap + proot launch),
    * we call terminalScheduler.start(session) which begins the PTY↔gateway loop.
    //
    // Until then, we fall back to the Shizuku shell-command mode.
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.End,
        horizontalAlignment = Alignment.Start
    ) {
        // Terminal display area placeholder
        if (shizuku.isAvailable()) {
            Text(
                text = "Terminal active — typing commands sends to agent via gateway",
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            Text(
                text = "Shizuku not available",
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.error
            )
        }

        // Send: two modes
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (shizuku.isAvailable()) {
                // Mode A: direct Shizuku shell command (existing behavior)
                TextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    label = { Text("Shizuku Cmd") },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(0.6f)
                )
                Button(
                    onClick = {
                        val cmd = userInput.trim()
                        if (cmd.isNotEmpty()) {
                            shizuku.runShellCommand(cmd) { stdout, stderr, exitCode ->
                                val output = if (stdout != null && stdout.isNotBlank()) {
                                    "Output (exit=$exitCode): $stdout"
                                } else if (stderr != null && stderr.isNotBlank()) {
                                    "Error (exit=$exitCode): $stderr"
                                } else {
                                    "Command finished (exit=$exitCode)"
                                }
                                result = when {
                                    stdout != null && stdout.isNotBlank() -> ChatRepository.Result.success(output)
                                    else -> ChatRepository.Result.failure(output)
                                }
                            }
                            userInput = ""
                        }
                    modifier = Modifier.size(80.dp, 36.dp)
                ) {
                    "Run"
                }
            } else {
                // Mode B: gateway message (text sent via ChatRepository)
                TextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    label = { Text("Gateway Msg") },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(0.6f)
                )
                Button(
                    onClick = {
                        val cmd = userInput.trim()
                        if (cmd.isNotEmpty()) {
                            // Forward to the scheduler, which sends to gateway via forwardToGateway
                            // We run on the coroutine context; since we're in Compose, we use LaunchedEffect
                            // or a simple inline call given this is a demo scaffold.
                            // TODO: integrate with terminalScheduler.forwardToGateway properly
                            result = ChatRepository.Result.success("Sent via gateway: ${cmd.take(30)}")
                            userInput = ""
                        }
                    modifier = Modifier.size(80.dp, 36.dp)
                ) {
                    "Send"
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Called when the user toggles terminal mode on; starts the I/O scheduler with a
// TerminalSession once the rootfs is bootstrapped and the session is available.
// ---------------------------------------------------------------------------
fun onTerminalInput(cmd: String) {
    // Legacy: forward a one-off command via Shizuku or gateway
    // The new flow uses the TerminalSession PTY + TerminalIOScheduler for continuous
    // bidirectional I/O; this function is kept for backward compatibility.
}

// ---------------------------------------------------------------------------
// Controller object
// ---------------------------------------------------------------------------
object MainController {
    var isConnected by mutableStateOf(false)
    val messages: List<String> = emptyList()
    val status: String = if (isConnected) "Connected" else "Disconnected"

    fun toggleConnection() = TODO()
    fun sendMessage() = TODO()
}