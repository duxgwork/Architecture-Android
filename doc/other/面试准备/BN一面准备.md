# 币安 Android 一面准备

---

## 一、Handler / Looper / MessageQueue

1. Handler、Looper、MessageQueue 三者的关系是什么？
2. MessageQueue 的消息优先级机制是怎样的？如何发送一条优先级最高的消息？
3. 什么是同步屏障（Sync Barrier）？什么是异步消息？它们的使用场景有哪些？
4. MessageQueue 阻塞与唤醒的底层原理是什么？
   - 是不是只要往 MessageQueue 里发消息就会触发唤醒（调用 `nativeWake`）？
   - `nativeWake` 为什么能够唤醒阻塞的线程？`epoll` 还有哪些应用场景？
   - 除了 `MessageQueue`，还有哪些操作可以阻塞线程？
5. MessageQueue 的阻塞唤醒机制跟 `BlockingQueue` 的阻塞唤醒有什么区别？

---

## 二、网络（HttpDNS / OkHttp / HTTP 协议）

1. 为什么要做 HttpDNS？
2. HttpDNS 如何实现？如果在 Interceptor 里把 baseURL 从域名直接替换成 IP 直连，会有什么问题？
3. IP 择优策略是怎样的？如果 HttpDNS 服务挂了怎么办？
4. `Dns.lookup()` 方法的调用时机是什么？`ConnectionPool` 复用连接可以节省哪部分时间？
5. OkHttp 会自动重试哪些请求？如何避免重复下单？
6. HTTP/1.1 下，采用 OkHttp 默认配置，同时有 100 个相同域名的请求，最终会建立几条连接？HTTP/2 下又如何？

---

## 三、WebSocket

1. WebSocket 高频消息场景下如何优化？如何防止 buffer 内存膨胀？
2. 如何提升 Account WebSocket 订阅逻辑的健壮性？需要考虑哪些方面？
3. 保证 WebSocket 连接稳定性，需要从哪些方面入手？

---

## 四、WebView 与跨域

1. WebView 为什么无法直接复用原生网络层？
2. 如何实现 Native 代发？代发请求是否会绕过 CORS？
3. 什么是跨域？

---

## 五、View / UI

1. `invalidate()` 和 `requestLayout()` 的区别是什么？连续调用多次会有什么后果？
2. `Handler().post` 能否正确获取 View 的宽高？为什么？
3. MPAndroidChart 的使用场景？遇到过哪些坑？如何优化的？
4. 复杂页面（如交易页）的构建方案是怎样的？

---

## 六、性能优化

### 6.1 冷启动 / App 启动流程

1. 复杂页面的冷启动优化方案是什么？请结合监控手段一起说明。
2. App 冷启动和热启动的区别是什么？
3. Application 是什么时候创建的？Context 是什么时候有的？
4. 打开一个 Activity，第一帧数据的绘制触发点在哪里？

### 6.2 ANR

1. ANR 如何分析、定位？有哪些解决方案？

### 6.3 卡顿优化

1. 卡顿检测有哪些方案？如何进行优化？
2. 仓位列表卡顿如何排查和优化？强平公式计算对性能有什么影响？

### 6.4 内存泄漏

1. LeakCanary 的原理是什么？
2. `SoftReference` 和 `WeakReference` 的区别是什么？`ReferenceQueue` 的作用是什么？LeakCanary 能覆盖所有对象的泄漏检测吗？
3. 如何设计一个内存泄漏监控系统？
4. 日常开发中如何定位内存泄漏？

---

## 七、多线程与线程安全

1. Java 中有哪些锁？它们之间有什么区别？
2. `ReentrantLock` 是怎么实现让其他线程阻塞的？它是公平锁吗？如何保证公平性？
3. 协程中有哪些锁？跟 Java 的锁有什么区别？
4. 多线程场景下如何解决资源竞争问题？
5. 线程安全集合有哪些？它们的实现方式分别是什么？（包括线程安全的 Map、List）
6. `volatile` 关键字的作用是什么？

---

## 八、单例模式与类加载

1. 请手写 Kotlin / Java 中的各种单例写法。
2. 类的加载时机是什么？
3. `static` 单例为什么能保证线程安全？
4. `object` 关键字的底层原理是什么？
5. `by lazy` 的原理是什么？是否线程安全？
6. 如何写一个线程级别的单例？原理是什么？

---

## 九、Kotlin 协程与 Flow

1. `StateFlow`、`SharedFlow`、`Channel` 的区别是什么？各自的适用场景？
2. `suspend` 函数的实现原理是什么？切线程的底层机制是怎样的？
3. 协程的异常处理机制是怎样的？
4. 协程的取消机制是怎样的？
5. Flow 的 `zip`、`combine` 操作符如何使用？
6. `inline`、`noinline`、`crossinline` 的含义和使用场景分别是什么？

---

## 十、业务相关

1. 你之前负责了什么业务？开发了哪些模块？
2. KYC 模块的识别率是怎么优化的？Camera 模块遇到过哪些坑？
3. 交易下单流程是怎样的？
4. 链充值流程是怎样的？
5. Alpha 市值下单场景下，如何保证用户的买入价格跟链上一致？
6. OrderBook 的合并逻辑是怎样的？

---

## 十一、AI 工程化落地

1. 你使用过哪些 AI 开发工具？在开发流程中如何使用 AI 提升效率？（如 Skill 读取项目规范、提升 Figma 还原度、Code Review、Session 压缩等）
2. AI 开发中的 Token 消耗和上下文管理你是怎么处理的？
3. Skill 和 MCP 的区别是什么？你有哪些 AI 工程化落地的经验？
4. 如何设计 AI Code Review 流程？
5. AI 抓包工具是如何实现的？
6. 你了解跨 Session 开发吗？如何解决上下文丢失的问题？
7. 你了解 Harness、Agent 吗？

---

## 高频考点汇总

| 考点 | 出现次数 |
|------|---------|
| Handler/Looper/MessageQueue + 同步屏障 + 阻塞唤醒 | 4 次 |
| 冷启动优化 / App 启动流程 | 4 次 |
| LeakCanary 原理 / 内存泄漏监控 | 4 次 |
| 协程异常处理 / 取消机制 | 4 次 |
| 线程安全集合 / 多线程资源竞争 | 4 次 |
| Java 锁 / ReentrantLock | 3 次 |
| 单例模式 / 类加载 / by lazy | 3 次 |
| suspend 原理 | 3 次 |
| WebSocket 稳定性 | 3 次 |
| AI 工程化（Skill/MCP/Code Review） | 3 次 |

> 建议优先准备以上高频考点。
