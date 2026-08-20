package com.example.architecture.demo

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.architecture.channel.ChannelManager
import com.example.architecture.model.CollabEvent
import com.example.architecture.model.ChatMessage
import com.example.architecture.model.NotificationMessage
import com.example.architecture.model.PresenceUpdate
import com.example.architecture.websocket.WebSocketClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * WebSocket Demo ViewModel：演示业务侧如何从独立 Stream 获取数据
 *
 * 关键变化：
 * - 不再有 messageStream + switch-case
 * - 每个业务模块各自 collect 自己的 channel
 * - 代码清晰，职责分离，互不干扰
 */
class WebSocketDemoViewModel(application: Application) : AndroidViewModel(application) {

    private val webSocketClient = WebSocketClient()
    private val channelManager = ChannelManager(webSocketClient, viewModelScope)

    /** 连接状态 */
    private val _connectionStatus = MutableStateFlow("未连接")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    /** 聊天消息列表：业务侧只关心自己的数据 */
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    /** 通知列表 */
    private val _notifications = MutableStateFlow<List<NotificationMessage>>(emptyList())
    val notifications: StateFlow<List<NotificationMessage>> = _notifications.asStateFlow()

    /** 协同编辑事件列表 */
    private val _collabEvents = MutableStateFlow<List<CollabEvent>>(emptyList())
    val collabEvents: StateFlow<List<CollabEvent>> = _collabEvents.asStateFlow()

    /** 在线用户列表 */
    private val _onlineUsers = MutableStateFlow<List<PresenceUpdate>>(emptyList())
    val onlineUsers: StateFlow<List<PresenceUpdate>> = _onlineUsers.asStateFlow()

    init {
        // 启动 ChannelManager 路由
        channelManager.start()

        // 监听连接状态
        viewModelScope.launch {
            webSocketClient.connectionState.collect { state ->
                _connectionStatus.value = when (state) {
                    WebSocketClient.ConnectionState.CONNECTED -> "已连接"
                    WebSocketClient.ConnectionState.DISCONNECTED -> "未连接"
                    WebSocketClient.ConnectionState.ERROR -> "连接错误"
                }
            }
        }

        // 各自独立收集各自的频道，不再有 switch-case
        viewModelScope.launch {
            channelManager.chatChannel.collect { message ->
                _chatMessages.value = _chatMessages.value + message
                Log.d(TAG, "聊天频道收到: ${message.content}")
            }
        }

        viewModelScope.launch {
            channelManager.notificationChannel.collect { notification ->
                _notifications.value = _notifications.value + notification
                Log.d(TAG, "通知频道收到: ${notification.title}")
            }
        }

        viewModelScope.launch {
            channelManager.collabChannel.collect { event ->
                _collabEvents.value = _collabEvents.value + event
                Log.d(TAG, "协同编辑频道收到: ${event.action}")
            }
        }

        viewModelScope.launch {
            channelManager.presenceChannel.collect { update ->
                _onlineUsers.value = _onlineUsers.value + update
                Log.d(TAG, "在线状态频道收到: ${update.userId} - ${update.status}")
            }
        }
    }

    /** 连接到 WebSocket 服务器 */
    fun connect(url: String) {
        webSocketClient.connect(url)
    }

    /** 断开连接 */
    fun disconnect() {
        webSocketClient.disconnect()
    }

    /** 发送聊天消息 */
    fun sendChatMessage(from: String, content: String) {
        channelManager.sendChatMessage(from, content)
    }

    /** 发送通知 */
    fun sendNotification(title: String, body: String) {
        channelManager.sendNotification(title, body)
    }

    /** 发送协同编辑事件 */
    fun sendCollabEvent(userId: String, action: String, docId: String) {
        channelManager.sendCollabEvent(userId, action, docId)
    }

    /** 发送在线状态更新 */
    fun sendPresenceUpdate(userId: String, status: String) {
        channelManager.sendPresenceUpdate(userId, status)
    }

    /** 模拟收到聊天消息（用于演示） */
    fun simulateIncomingChat(from: String, content: String) {
        viewModelScope.launch {
            val message = ChatMessage(from, content, System.currentTimeMillis())
            _chatMessages.value = _chatMessages.value + message
        }
    }

    /** 模拟收到通知（用于演示） */
    fun simulateIncomingNotification(title: String, body: String) {
        viewModelScope.launch {
            val notification = NotificationMessage(title, body, "info")
            _notifications.value = _notifications.value + notification
        }
    }

    /** 模拟收到协同编辑事件（用于演示） */
    fun simulateIncomingCollabEvent(userId: String, action: String, docId: String) {
        viewModelScope.launch {
            val event = CollabEvent(userId, action, docId)
            _collabEvents.value = _collabEvents.value + event
        }
    }

    /** 模拟收到在线状态更新（用于演示） */
    fun simulateIncomingPresenceUpdate(userId: String, status: String) {
        viewModelScope.launch {
            val update = PresenceUpdate(userId, status)
            _onlineUsers.value = _onlineUsers.value + update
        }
    }

    override fun onCleared() {
        super.onCleared()
        webSocketClient.disconnect()
    }

    companion object {
        private const val TAG = "WebSocketDemoVM"
    }
}
