# Flutter 混合项目 WebSocket 数据共享方案

> 适用场景：Android 原生已建立 WebSocket 连接，Flutter 页面也需要接收同一份数据，且推送频率很高，要求性能不受影响。

---

## 核心原则

**不要让 Flutter 再连一条 WebSocket**，也不要用 `MethodChannel` 逐条推送。正确做法是：

> **Android 独占连接，向 Flutter 做受控的流式分发**

---

## 推荐架构

```
Android WebSocket
       │
       ▼
Native 缓冲 / 批处理 / 降采样
       │
       ▼
EventChannel / BasicMessageChannel (binary)
       │
       ▼
Flutter Stream → 页面订阅
```

设计原则：

1. **连接只在 Native 维护一份**（重连、心跳、鉴权都在 Android）
2. **Flutter 只订阅"已处理过"的数据流**
3. **高频原始帧不要原样逐条过 Bridge**

---

## 通道选择

| 方式 | 适用 | 高频场景 |
|------|------|----------|
| `MethodChannel` | 请求/响应、低频控制 | ❌ 不适合推送 |
| `EventChannel` | 持续事件流 | ✅ 默认选择 |
| `BasicMessageChannel` + Binary | 超高频、大包 | ✅ 性能最好 |
| 共享内存 / FFI | 极致（行情级） | 可选，成本高 |

多数业务用 **EventChannel + 批处理** 就够；如果每秒几百～几千条，再上 **Binary MessageChannel**。

---

## 核心：在 Native 侧做"节流与合并"

Bridge 调用本身有编解码 + 线程切换成本。高频推送必须在 Android 合并后再发：

```kotlin
// 伪代码：按时间窗批量发给 Flutter
class WsBridge(private val messenger: BinaryMessenger) {
    private val channel = EventChannel(messenger, "app/ws_stream")
    private var sink: EventChannel.EventSink? = null
    private val buffer = ConcurrentLinkedQueue<ByteArray>()
    private val handler = Handler(Looper.getMainLooper())

    private val flushRunnable = object : Runnable {
        override fun run() {
            if (buffer.isNotEmpty() && sink != null) {
                val batch = ArrayList<ByteArray>(buffer.size)
                while (true) {
                    val item = buffer.poll() ?: break
                    batch.add(item)
                }
                // 一次 Event 推一批，而不是 1 msg = 1 event
                sink?.success(batch)
            }
            handler.postDelayed(this, 16) // ~60fps，或按业务改成 50~100ms
        }
    }

    fun start() {
        channel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(args: Any?, events: EventChannel.EventSink?) {
                sink = events
                handler.post(flushRunnable)
            }
            override fun onCancel(args: Any?) {
                sink = null
                handler.removeCallbacks(flushRunnable)
            }
        })
    }

    fun onWsMessage(bytes: ByteArray) {
        // 在 IO 线程解析/过滤后入队，不要直接 sink.success
        buffer.offer(bytes)
        // 队列过长时丢弃旧数据（行情类常见）
        while (buffer.size > 500) buffer.poll()
    }
}
```

关键点：

- **批量发送**：N 条合并成 1 次 Channel 调用
- **固定刷新间隔**：16ms / 50ms / 100ms，按 UI 需求定
- **背压**：队列有上限，溢出丢旧留新（或按业务丢中间）
- **主线程只做 `sink.success`**，解析放在 WS/IO 线程

---

## Flutter 侧：轻订阅，重计算放 Isolate

```dart
class WsRepository {
  static const _channel = EventChannel('app/ws_stream');

  Stream<List<Uint8List>> get rawBatches =>
      _channel.receiveBroadcastStream().map((e) {
        final list = e as List;
        return list.cast<Uint8List>();
      });
}

// UI 层
late final stream = wsRepository.rawBatches
    .asyncMap((batch) => compute(parseBatch, batch)) // 重解析丢 Isolate
    .map((models) => models.last) // 多数 UI 只要最新态
    .distinct(); // 相同就不重建
```

建议：

- 用 `StreamController.broadcast` 做应用内二次分发，避免多个页面重复听 Channel
- UI 只订阅**派生后的状态**（最新价、聚合列表），不要订阅原始每一帧
- 重 JSON / Protobuf 解析用 `compute` / Isolate
- 能 Binary / Protobuf 就别用 JSON 字符串过 Bridge

---

## 性能清单（高频必备）

1. **数据格式**：优先 `Uint8List` / Protobuf，避免大 JSON 字符串反复编解码
2. **降采样**：UI 60fps 足够，没必要 1000Hz 全推到 Dart
3. **只推增量 / 最新快照**：很多业务只要"当前态"，中间帧可丢
4. **按页面订阅**：`onListen` 才开始向 Flutter 刷；无监听则只在 Native 缓存
5. **避免 setState 风暴**：用 `ValueNotifier` / Riverpod / Bloc，局部刷新
6. **不要在 build 里直接 listen Channel**

---

## 三种常见方案怎么选

### A. 中低频（<50 msg/s）

`EventChannel` + 单条或小批量即可。

### B. 高频 UI 展示（行情、轨迹、进度）—— 推荐

Native 批处理/降采样 → `EventChannel` / `BasicMessageChannel(binary)` → Flutter 只拿最新态渲染。

### C. 超高频且要完整处理（分析、录制）

Native 完整接收与落盘；Flutter 只拉摘要或按需拉取；必要时再考虑共享内存 / FFI（复杂度明显更高）。

---

## 不建议的做法

- ❌ Flutter 再开一条 WebSocket 双连接（状态不一致、流量翻倍）
- ❌ 每条 WS 消息都 `MethodChannel.invokeMethod`
- ❌ 把未过滤的原始高频流直接 `setState` 整页刷新
- ❌ 在主线程做重 JSON 解析后再过 Bridge

---

## 结论

Android 独占 WebSocket；对 Flutter 用 `EventChannel`（或 Binary MessageChannel）做**批量 + 降采样 + 背压**的流式推送；Flutter 侧用广播 Stream + Isolate 解析 + 只驱动最新 UI 状态。这样推送再密，UI 和 Bridge 压力也能控住。

---

## 附录：方案选型参考

| 指标 | 建议 |
|------|------|
| QPS < 50 | EventChannel，单条或小批量 |
| QPS 50~1000 | EventChannel + Native 批处理（16~100ms 窗口） |
| QPS > 1000 | BasicMessageChannel + Binary + 降采样 |
| 消息体 > 1KB | Protobuf / Binary，避免 JSON |
| UI 只需最新值 | Native 侧合并后只推 last snapshot |
| 需要完整历史 | Native 落盘，Flutter 按需拉取 |
