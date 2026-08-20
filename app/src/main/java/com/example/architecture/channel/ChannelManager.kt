package com.example.architecture.channel

import android.util.Log
import com.example.architecture.model.CollabEvent
import com.example.architecture.model.ChatMessage
import com.example.architecture.model.Envelope
import com.example.architecture.model.NotificationMessage
import com.example.architecture.model.PresenceUpdate
import com.example.architecture.model.Topic
import com.example.architecture.websocket.WebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * ChannelManager：Topic 路由分发器
 *
 * 核心职责：
 * 1. 订阅 WebSocketClient 的原始消息流
 * 2. 解析信封，提取 topic
 * 3. 按 topic 路由到对应的 Channel
 * 4. 业务侧各自监听自己的 Channel，互不干扰
 *
 * 这样做的好处：
 * - 底层只有一条 WebSocket 连接
 * - 业务侧拿到的是各自独立的 Stream，不再有 switch-case
 * - 新增 topic 只需加一个 Channel，不影响现有代码
 */
class ChannelManager(
    private val webSocketClient: WebSocketClient,
    private val scope: CoroutineScope
) {

    /** 聊天频道 */
    private val _chatChannel = MutableSharedFlow<ChatMessage>(replay = 0)
    val chatChannel: SharedFlow<ChatMessage> = _chatChannel.asSharedFlow()

    /** 通知频道 */
    private val _notificationChannel = MutableSharedFlow<NotificationMessage>(replay = 0)
    val notificationChannel: SharedFlow<NotificationMessage> = _notificationChannel.asSharedFlow()

    /** 协同编辑频道 */
    private val _collabChannel = MutableSharedFlow<CollabEvent>(replay = 0)
    val collabChannel: SharedFlow<CollabEvent> = _collabChannel.asSharedFlow()

    /** 在线状态频道 */
    private val _presenceChannel = MutableSharedFlow<PresenceUpdate>(replay = 0)
    val presenceChannel: SharedFlow<PresenceUpdate> = _presenceChannel.asSharedFlow()

    /** 启动路由：订阅 WebSocket 消息流，按 topic 分发 */
    fun start() {
        scope.launch {
            webSocketClient.messageFlow.collect { rawMessage ->
                val envelope = Envelope.fromJson(rawMessage)
                if (envelope != null) {
                    route(envelope)
                } else {
                    Log.w(TAG, "无法解析的消息: $rawMessage")
                }
            }
        }
    }

    /** 路由逻辑：根据 topic 分发到对应的 Channel */
    private fun route(envelope: Envelope) {
        when (envelope.topic) {
            Topic.CHAT -> {
                val message = ChatMessage.fromPayload(envelope.payload)
                _chatChannel.tryEmit(message)
                Log.d(TAG, "路由到聊天频道: ${message.content}")
            }
            Topic.NOTIFICATION -> {
                val notification = NotificationMessage.fromPayload(envelope.payload)
                _notificationChannel.tryEmit(notification)
                Log.d(TAG, "路由到通知频道: ${notification.title}")
            }
            Topic.COLLABORATION -> {
                val event = CollabEvent.fromPayload(envelope.payload)
                _collabChannel.tryEmit(event)
                Log.d(TAG, "路由到协同编辑频道: ${event.action}")
            }
            Topic.PRESENCE -> {
                val update = PresenceUpdate.fromPayload(envelope.payload)
                _presenceChannel.tryEmit(update)
                Log.d(TAG, "路由到在线状态频道: ${update.userId} - ${update.status}")
            }
        }
    }

    /** 发送消息：将领域模型包装成信封后发出 */
    fun sendChatMessage(from: String, content: String) {
        val payload = JSONObject().apply {
            put("from", from)
            put("content", content)
            put("timestamp", System.currentTimeMillis())
        }
        webSocketClient.send(Envelope(Topic.CHAT, payload))
    }

    fun sendNotification(title: String, body: String, type: String = "info") {
        val payload = JSONObject().apply {
            put("title", title)
            put("body", body)
            put("type", type)
        }
        webSocketClient.send(Envelope(Topic.NOTIFICATION, payload))
    }

    fun sendCollabEvent(userId: String, action: String, docId: String) {
        val payload = JSONObject().apply {
            put("userId", userId)
            put("action", action)
            put("docId", docId)
        }
        webSocketClient.send(Envelope(Topic.COLLABORATION, payload))
    }

    fun sendPresenceUpdate(userId: String, status: String) {
        val payload = JSONObject().apply {
            put("userId", userId)
            put("status", status)
        }
        webSocketClient.send(Envelope(Topic.PRESENCE, payload))
    }

    companion object {
        private const val TAG = "ChannelManager"
    }
}
