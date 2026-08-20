# WebSocket Topic 路由架构说明

## 架构概述

实现了基于 Topic 的 WebSocket 消息路由系统，解决了"同一条连接里同时跑聊天、通知、协同编辑、在线状态，导致 messageStream 里全是 switch-case"的问题。

### 核心设计

```
┌─────────────────────────────────────────────────────────┐
│                    业务层 (ViewModel)                    │
│  chatChannel  notificationChannel  collabChannel  ...  │
└────────────────┬────────────────────────────────────────┘
                 │ 各自独立的 SharedFlow
┌────────────────▼────────────────────────────────────────┐
│              ChannelManager (路由层)                     │
│  - 订阅 WebSocketClient.messageFlow                     │
│  - 解析 Envelope，提取 topic                            │
│  - 按 topic 路由到对应的 Channel                        │
└────────────────┬────────────────────────────────────────┘
                 │ 统一的信封格式
┌────────────────▼────────────────────────────────────────┐
│              WebSocketClient (传输层)                    │
│  - 单条 WebSocket 连接                                  │
│  - 所有消息都进入 messageFlow                            │
└─────────────────────────────────────────────────────────┘
```

## 关键组件

### 1. 信封模型 (Envelope)

所有 WebSocket 消息统一包装成带 topic 的信封：

```kotlin
data class Envelope(
    val topic: Topic,
    val payload: JSONObject
)
```

JSON 格式：
```json
{
  "topic": "chat",
  "payload": {
    "from": "张三",
    "content": "你好",
    "timestamp": 1234567890
  }
}
```

### 2. Topic 枚举

定义所有消息主题：

```kotlin
enum class Topic(val value: String) {
    CHAT("chat"),
    NOTIFICATION("notification"),
    COLLABORATION("collaboration"),
    PRESENCE("presence")
}
```

### 3. WebSocketClient

管理单条 WebSocket 连接，暴露原始消息流：

```kotlin
class WebSocketClient {
    val messageFlow: SharedFlow<String>  // 所有原始消息
    val connectionState: SharedFlow<ConnectionState>
    
    fun connect(url: String)
    fun send(envelope: Envelope)
    fun disconnect()
}
```

### 4. ChannelManager

Topic 路由分发器，核心职责：

```kotlin
class ChannelManager(
    private val webSocketClient: WebSocketClient,
    private val scope: CoroutineScope
) {
    // 各自独立的频道
    val chatChannel: SharedFlow<ChatMessage>
    val notificationChannel: SharedFlow<NotificationMessage>
    val collabChannel: SharedFlow<CollabEvent>
    val presenceChannel: SharedFlow<PresenceUpdate>
    
    fun start()  // 启动路由
    fun sendChatMessage(...)
    fun sendNotification(...)
    // ...
}
```

路由逻辑：
```kotlin
private fun route(envelope: Envelope) {
    when (envelope.topic) {
        Topic.CHAT -> _chatChannel.tryEmit(ChatMessage.fromPayload(...))
        Topic.NOTIFICATION -> _notificationChannel.tryEmit(...)
        Topic.COLLABORATION -> _collabChannel.tryEmit(...)
        Topic.PRESENCE -> _presenceChannel.tryEmit(...)
    }
}
```

### 5. 业务侧使用

ViewModel 各自 collect 自己的频道，不再有 switch-case：

```kotlin
// 聊天业务只关心聊天消息
viewModelScope.launch {
    channelManager.chatChannel.collect { message ->
        // 处理聊天消息
    }
}

// 通知业务只关心通知
viewModelScope.launch {
    channelManager.notificationChannel.collect { notification ->
        // 处理通知
    }
}
```

## 文件结构

```
app/src/main/java/com/example/architecture/
├── model/
│   └── Envelope.kt              # 信封模型 + Topic 枚举 + 领域消息模型
├── websocket/
│   └── WebSocketClient.kt       # WebSocket 客户端（单连接）
├── channel/
│   └── ChannelManager.kt        # Topic 路由分发器
└── demo/
    ├── WebSocketDemoActivity.kt # 演示界面
    └── WebSocketDemoViewModel.kt
```

## 演示功能

1. **连接管理**：输入 WebSocket 服务器地址，连接/断开
2. **四个频道展示**：聊天、通知、协同编辑、在线状态各自独立显示
3. **模拟消息**：无需真实服务器，点击按钮模拟接收各类消息

## 架构优势

### 改造前（switch-case 地狱）

```kotlin
messageStream.collect { message ->
    when (message.type) {
        "chat" -> handleChat(message)
        "notification" -> handleNotification(message)
        "collaboration" -> handleCollab(message)
        "presence" -> handlePresence(message)
        // 新增类型就要改这里，违反开闭原则
    }
}
```

### 改造后（独立 Stream）

```kotlin
// 聊天业务
chatChannel.collect { handleChat(it) }

// 通知业务
notificationChannel.collect { handleNotification(it) }

// 协同编辑业务
collabChannel.collect { handleCollab(it) }

// 各业务互不干扰，新增 topic 只需加一个 Channel
```

## 扩展性

新增 Topic 的步骤：

1. 在 `Topic` 枚举中添加新值
2. 创建对应的领域模型（如 `NewFeatureMessage`）
3. 在 `ChannelManager` 中添加新的 `MutableSharedFlow`
4. 在 `route()` 方法中添加新的路由分支

**现有代码完全不需要修改**，符合开闭原则。

## 技术栈

- **OkHttp 4.12.0**：WebSocket 客户端
- **Kotlin Coroutines & Flow**：异步流处理
- **AndroidX Lifecycle**：ViewModel + lifecycleScope
- **org.json**：JSON 解析（Android 内置，无需额外依赖）

## 运行演示

1. 在 Android Studio 中打开项目
2. 构建并运行到模拟器或真机
3. 在主界面点击"WebSocket Topic 路由演示"按钮
4. 输入 WebSocket 服务器地址（如 `ws://10.0.2.2:8080/ws`）
5. 点击"连接"建立连接
6. 点击"模拟消息"按钮测试各类消息的路由

## 总结

通过引入 Topic 路由层，实现了：

✅ **单条连接**：底层只有一条 WebSocket 连接  
✅ **独立 Stream**：业务侧各自监听独立的频道  
✅ **消除 switch-case**：不再有集中的消息分发逻辑  
✅ **易于扩展**：新增 topic 不影响现有代码  
✅ **职责分离**：每个业务模块只关心自己的数据  

客户端代码变得干净很多，架构清晰，维护性大幅提升。
