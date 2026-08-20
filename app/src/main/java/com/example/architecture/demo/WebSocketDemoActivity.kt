package com.example.architecture.demo

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.architecture.databinding.ActivityWebsocketDemoBinding
import kotlinx.coroutines.launch

/**
 * WebSocket Topic 路由演示 Activity
 *
 * 展示改造后的架构：
 * - 底层只有一条 WebSocket 连接
 * - 消息统一包成带 topic 的信封
 * - ChannelManager 按 topic 路由分发
 * - 业务侧各自监听独立的 Stream，不再有 switch-case
 */
class WebSocketDemoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebsocketDemoBinding
    private val viewModel: WebSocketDemoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebsocketDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeState()
    }

    private fun setupListeners() {
        // 连接按钮
        binding.btnConnect.setOnClickListener {
            val url = binding.etServerUrl.text.toString()
            if (url.isNotBlank()) {
                viewModel.connect(url)
            }
        }

        // 断开按钮
        binding.btnDisconnect.setOnClickListener {
            viewModel.disconnect()
        }

        // 模拟消息按钮（用于演示，无需真实服务器）
        binding.btnSimChat.setOnClickListener {
            viewModel.simulateIncomingChat("张三", "你好，这是测试消息！")
        }

        binding.btnSimNotification.setOnClickListener {
            viewModel.simulateIncomingNotification("系统通知", "你有一条新消息")
        }

        binding.btnSimCollab.setOnClickListener {
            viewModel.simulateIncomingCollabEvent("user123", "edit", "doc_456")
        }

        binding.btnSimPresence.setOnClickListener {
            viewModel.simulateIncomingPresenceUpdate("user789", "online")
        }
    }

    private fun observeState() {
        // 观察连接状态
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.connectionStatus.collect { status ->
                    binding.tvConnectionStatus.text = "状态: $status"
                }
            }
        }

        // 观察聊天消息：业务侧只关心自己的数据
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.chatMessages.collect { messages ->
                    binding.tvChatMessages.text = if (messages.isEmpty()) {
                        "暂无消息"
                    } else {
                        messages.joinToString("\n") { "[${it.from}] ${it.content}" }
                    }
                }
            }
        }

        // 观察通知：独立的 Stream，互不干扰
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.notifications.collect { notifications ->
                    binding.tvNotifications.text = if (notifications.isEmpty()) {
                        "暂无通知"
                    } else {
                        notifications.joinToString("\n") { "[${it.type}] ${it.title}: ${it.body}" }
                    }
                }
            }
        }

        // 观察协同编辑事件
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.collabEvents.collect { events ->
                    binding.tvCollabEvents.text = if (events.isEmpty()) {
                        "暂无事件"
                    } else {
                        events.joinToString("\n") { "[${it.userId}] ${it.action} on ${it.docId}" }
                    }
                }
            }
        }

        // 观察在线状态
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.onlineUsers.collect { users ->
                    binding.tvOnlineUsers.text = if (users.isEmpty()) {
                        "暂无在线用户"
                    } else {
                        users.joinToString("\n") { "${it.userId}: ${it.status}" }
                    }
                }
            }
        }
    }
}
