package com.example.hermesmobile.runtime

import android.app.ForegroundService
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.hermesmobile.R
import com.example.hermesmobile.ui.MainScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

// The port the gateway API server will bind to (must match gateway config)
private val GATEWAY_API_PORT = 8642

class AgentService : Service() {

    private var pythonProcess: Process? = null
    private var bridgeSocket: Socket? = null
    private var bridgeWriter: PrintWriter? = null

    override fun onCreate() {
        super.onCreate()
        startAgentGateway()
        startBridgeServer()
    }

    private fun startAgentGateway() {
        // Launch Python agent/gateway as a foreground service subprocess
        try {
            val builder = ProcessBuilder(
                "python3", "-m", "gateway.run"
            )
            builder.redirectErrorStream(true) // merge stderr into stdout
            pythonProcess = builder.start()

            // Log output to Android log
            thread {
                val reader = BufferedReader(
                    InputStreamReader(pythonProcess!!.inputStream)
                )
                var line: String?
                while ((line = reader.readLine()) != null) {
                    Log.d("HermesAgent", "gateway: $line")
                }
            }
        } catch (e: Exception) {
            Log.e("HermesAgent", "Failed to start gateway", e)
        }
    }

    private fun startBridgeServer() {
        // Start a tiny TCP bridge server that forwards messages between
        // the Android UI and the gateway's HTTP API
        thread {
            try {
                val serverSocket = ServerSocket(GATEWAY_API_PORT + 1) // use port+1 for our bridge
                Log.d("HermesAgent", "Bridge server listening on port ${serverSocket.localPort}")
                while (true) {
                    val client = serverSocket.accept()
                    Log.d("HermesAgent", "Bridge client connected")
                    thread {
                        forwardClient(client)
                    }
                }
            } catch (e: Exception) {
                Log.e("HermesAgent", "Bridge server error", e)
            }
        }
    }

    private fun forwardClient(client: Socket) {
        // Simple bidirectional forwarder between client and gateway
        try {
            val clientOut = PrintWriter(client.outputStream, true)
            val clientIn = BufferedReader(InputStreamReader(client.inputStream))

            // Read from client and forward to gateway stdin
            thread {
                val line: String?
                while ((line = clientIn.readLine()) != null) {
                    bridgeWriter?.println(line)
                }
            }

            // Read from gateway stdout (via pythonProcess) and forward to client
            thread {
                val reader = BufferedReader(
                    InputStreamReader(pythonProcess!!.errorStream)
                )
                var line: String?
                while ((line = reader.readLine()) != null) {
                    clientOut.println("GATEWAY: $line")
                }
            }
        } catch (e: Exception) {
            Log.e("HermesAgent", "Client forward error", e)
        } finally {
            client.close()
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        pythonProcess?.destroy()
        super.onDestroy()
    }
}