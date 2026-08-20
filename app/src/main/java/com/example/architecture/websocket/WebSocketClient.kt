package com.example.architecture.websocket

import android.util.Log
import com.example.architecture.model.Envelope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * WebSocket 客户端：管理单条 WebSocket 连接
 * 底层只有一条连接，但通过 ChannelManager 路由后，业务侧拿到的是各自独立的 Stream
 */
class WebSocketClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    /** 原始消息流：所有收到的消息都先进这里，由 ChannelManager 订阅并路由 */
    private val _messageFlow = MutableSharedFlow<String>(replay = 0)
    val messageFlow: SharedFlow<String> = _messageFlow.asSharedFlow()

    /** 连接状态 */
    private val _connectionState = MutableSharedFlow<ConnectionState>(replay = 1)
    val connectionState: SharedFlow<ConnectionState> = _connectionState.asSharedFlow()

    /** 连接到 WebSocket 服务器 */
    fun connect(url: String) {
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket 连接已建立")
                _connectionState.tryEmit(ConnectionState.CONNECTED)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "收到原始消息: $text")
                // 所有消息统一发到 messageFlow，由 ChannelManager 按 topic 路由
                _messageFlow.tryEmit(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket 正在关闭: $code $reason")
                webSocket.close(1000, null)
                _connectionState.tryEmit(ConnectionState.DISCONNECTED)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket 已关闭")
                _connectionState.tryEmit(ConnectionState.DISCONNECTED)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket 连接失败", t)
                _connectionState.tryEmit(ConnectionState.ERROR)
            }
        })
    }

    /** 发送信封消息：将 Envelope 序列化为 JSON 后发出 */
    fun send(envelope: Envelope) {
        webSocket?.send(envelope.toJson())
    }

    /** 发送原始 JSON 字符串 */
    fun sendRaw(json: String) {
        webSocket?.send(json)
    }

    /** 断开连接 */
    fun disconnect() {
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
    }

    enum class ConnectionState {
        CONNECTED,
        DISCONNECTED,
        ERROR
    }

    companion object {
        private const val TAG = "WebSocketClient"
    }
}
