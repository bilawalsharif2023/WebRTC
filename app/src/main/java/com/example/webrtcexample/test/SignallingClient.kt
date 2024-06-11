package com.example.webrtcexample.test

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import okhttp3.*
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class SignallingClient(
    private val listener: SignallingClientListener
) : CoroutineScope {

    companion object {
        private const val HOST_ADDRESS = "ws://192.168.1.20:8080/connect"
    }

    private val job = Job()
    private val gson = Gson()

    override val coroutineContext = Dispatchers.IO + job

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private lateinit var webSocket: WebSocket

    private val sendChannel = ConflatedBroadcastChannel<String>()

    init {
        connect()
    }

    private fun connect() {
        val request = Request.Builder().url(HOST_ADDRESS).build()
        webSocket = client.newWebSocket(request, WebSocketListenerImpl())

        // Optional: You can add code here to handle reconnection logic if needed
    }

    private inner class WebSocketListenerImpl : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            listener.onConnectionEstablished()
            launch {
                val sendData = sendChannel.openSubscription()
                try {
                    while (true) {
                        sendData.tryReceive().getOrNull()?.let {
                            Log.v(this@SignallingClient.javaClass.simpleName, "Sending: $it")
                            webSocket.send(it)
                        }
                    }
                } catch (exception: Throwable) {
                    Log.e("SignallingClient", "Exception in sending data", exception)
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.v(this@SignallingClient.javaClass.simpleName, "Received: $text")
            val jsonObject = gson.fromJson(text, JsonObject::class.java)
            launch {
                when {
                    jsonObject.has("serverUrl") -> {
                        withContext(Dispatchers.Main) {
                            listener.onIceCandidateReceived(
                                gson.fromJson(
                                    jsonObject,
                                    IceCandidate::class.java
                                )
                            )
                        }
                    }
                    jsonObject.has("type") && jsonObject.get("type").asString == "OFFER" -> {
                        withContext(Dispatchers.Main) {
                            listener.onOfferReceived(
                                gson.fromJson(
                                    jsonObject,
                                    SessionDescription::class.java
                                )
                            )
                        }
                    }
                    jsonObject.has("type") && jsonObject.get("type").asString == "ANSWER" -> {
                        withContext(Dispatchers.Main) {
                            listener.onAnswerReceived(
                                gson.fromJson(
                                    jsonObject,
                                    SessionDescription::class.java
                                )
                            )
                        }
                    }
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e("SignallingClient", "WebSocket Failure", t)
        }
    }

    fun send(dataObject: Any?) = runBlocking {
        sendChannel.send(gson.toJson(dataObject))
    }

    fun destroy() {
        webSocket.close(1000, "Client closed")
        job.complete()
    }
}
