package com.example.hermesmobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.hermesmobile.shizuku.ShizukuManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HermesMobileScreen()
                }
            }
        }
    }
}

@Composable
private fun HermesMobileScreen() {
    val context = LocalContext.current
    val repository = remember(context) { ChatRepository(context) }
    val shizuku = remember(context) { ShizukuManager(context) }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Not connected") }
    var terminalMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Hermes Mobile", style = MaterialTheme.typography.headlineSmall)
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    status = "Checking gateway…"
                    status = when (val result = repository.getStatus()) {
                        is ChatRepository.Result.Success -> "Gateway connected"
                        is ChatRepository.Result.Failure -> "Gateway unavailable: ${result.error.message ?: "error"}"
                    }
                }
            }) {
                Text("Check gateway")
            }
            OutlinedButton(onClick = { terminalMode = !terminalMode }) {
                Text(if (terminalMode) "Chat" else "Terminal")
            }
        }

        if (terminalMode) {
            Text(
                text = if (shizuku.isAvailable()) {
                    "Shizuku is available. The terminal session integration is not yet wired into this screen."
                } else {
                    "Terminal setup requires a configured rootfs and Shizuku service."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(onClick = { shizuku.requestPermission() }) {
                Text("Request Shizuku permission")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Message") }
                )
                Button(onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        messages.add("You: $text")
                        input = ""
                        scope.launch {
                            when (val result = repository.sendMessage(text)) {
                                is ChatRepository.Result.Success -> messages.add("Hermes: ${result.text}")
                                is ChatRepository.Result.Failure -> messages.add(
                                    "Error: ${result.error.message ?: "request failed"}"
                                )
                            }
                        }
                    }
                }) {
                    Text("Send")
                }
            }
        }
    }
}
