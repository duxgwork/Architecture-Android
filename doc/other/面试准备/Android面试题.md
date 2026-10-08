# Android 面试题（按内容分类）

---

## 一、内存管理

### 1.1 LeakCanary 的原理？

LeakCanary 的核心流程分为四步：

1. **监控对象创建与销毁**：通过 `Application.registerActivityLifecycleCallbacks()` 监听 Activity 生命周期，在 `onDestroyed` 时将对象包装为 `KeyedWeakReference`，关联 `ReferenceQueue`。
2. **弱引用检测**：延迟约 5 秒后检查 `ReferenceQueue`。如果对象仍未被回收入队列，说明存在强引用链阻止 GC，疑似泄漏。
3. **触发 GC 并二次确认**：手动调用 `Runtime.gc()` 触发 GC，再次检查。若仍存活则确认泄漏。
4. **堆转储分析**：使用 `Debug.dumpHprofData()` 导出 hprof 文件，通过内置的 `Shark` 库解析堆转储，找到 GC Root 到泄漏对象的最短强引用链，最终以通知形式展示泄漏路径。

### 1.2 SoftReference 和 WeakReference 的区别？ReferenceQueue 的作用？能覆盖所有对象吗？

| 维度 | SoftReference | WeakReference |
|------|--------------|---------------|
| 回收时机 | 内存不足时回收 | GC 时即回收，不论内存是否充足 |
| 典型用途 | 实现内存敏感的缓存（如图片缓存） | 关联生命周期管理（如 LeakCanary、Handler 弱引用） |

**ReferenceQueue 的作用**：当引用对象被 GC 回收时，Reference 实例会被自动加入关联的 ReferenceQueue。通过轮询该队列，可以得知哪些对象已经被回收。LeakCanary 正是利用它判断监控对象是否被正常 GC。

**不能覆盖所有对象**：以下对象无法被弱引用回收：
- `Thread`、`System` 等 JVM 系统类
- JNI 层持有的 native 对象
- 基本类型（int、long 等）不存在引用概念
- 被 `ClassLoader` 持有的类对象

### 1.3 如何设计一个内存泄漏监控系统？

一套完整的内存泄漏监控系统需要以下模块：

1. **对象监控层**：在 Activity / Fragment 的 `onDestroy` 时将其包装为弱引用，关联 ReferenceQueue。
2. **延迟检测层**：延迟 5~10 秒后检查 ReferenceQueue，未回收则标记为疑似泄漏。
3. **GC 确认层**：手动触发 `Runtime.gc()` 后再次检查，排除 GC 延迟导致的误判。
4. **堆转储分析层**：导出 hprof 文件，解析 GC Root 引用链，定位泄漏路径。
5. **上报与告警层**：将泄漏信息（类名、引用链路、发生频次）上报后台。
6. **黑名单过滤**：过滤已知的系统级泄漏（如 InputMethodManager 在某些 ROM 上的泄漏），避免误报。
7. **聚合分析**：按版本、机型、页面聚合统计，输出泄漏趋势报告。

### 1.4 内存泄漏如何定位？

| 工具 | 使用阶段 | 特点 |
|------|---------|------|
| **LeakCanary** | 开发 & 测试阶段 | 自动检测，实时通知，零侵入 |
| **MAT（Memory Analysis Tool）** | 离线分析 | 功能强大，可分析 GC Root、Dominator Tree、Histogram |
| **Android Studio Profiler** | 开发阶段 | 实时查看内存分配，对比多次 heap dump 差异 |
| **StrictMode** | 开发阶段 | 检测 Activity/Fragment 泄漏，输出违规日志 |
| **自定义方案** | 线上监控 | 弱引用 + ReferenceQueue + 堆转储上报 |

实际定位流程：
1. 用 LeakCanary 复现并获取泄漏链路
2. 分析引用链中哪个环节持有了不该持有的引用
3. 常见泄漏场景：匿名内部类持有 Activity、静态集合缓存未清理、未注销的监听器/回调、Handler 消息链

---

## 二、Kotlin/Java 语言基础与单例

### 2.1 Kotlin/Java 中的各种单例写法？

**Java 写法**：

```java
// 1. 饿汉式（类加载时初始化，线程安全）
public class Singleton {
    private static final Singleton INSTANCE = new Singleton();
    private Singleton() {}
    public static Singleton getInstance() { return INSTANCE; }
}

// 2. 双重检查锁 DCL（懒加载 + 线程安全）
public class Singleton {
    private static volatile Singleton instance;
    private Singleton() {}
    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}

// 3. 静态内部类（懒加载，JVM 保证线程安全）
public class Singleton {
    private Singleton() {}
    private static class Holder {
        static final Singleton INSTANCE = new Singleton();
    }
    public static Singleton getInstance() { return Holder.INSTANCE; }
}

// 4. 枚举（天然单例，防反射、防序列化）
public enum Singleton { INSTANCE }
```

**Kotlin 写法**：

```kotlin
// 1. object 关键字（编译为 static final INSTANCE）
object Singleton { fun doSomething() {} }

// 2. companion object + const
class MyClass {
    companion object { val instance = MyClass() }
}

// 3. by lazy 委托（默认 SYNCHRONIZED 模式，线程安全）
val singleton: Singleton by lazy { Singleton() }
```

### 2.2 类的加载时机？static 单例为何能保证线程安全？

**类加载时机**（满足以下任一条件即触发类初始化）：
1. 创建该类的对象实例（`new`、反射、克隆、反序列化）
2. 访问该类的静态成员（字段或方法）
3. 创建子类实例时（父类先于子类加载）
4. JVM 启动时加载主类
5. 通过反射调用（`Class.forName()`）

**static 单例线程安全的原因**：JVM 在类初始化阶段会对 `Class` 对象加锁（`Class` 对象的 monitor），保证 `<clinit>`（静态初始化块）在多线程环境下只被执行一次。因此基于类加载机制的单例（饿汉式、静态内部类）天然线程安全。

### 2.3 object 原理、by lazy 原理，是否线程安全？

**object 原理**：
- 编译后生成一个 `final` 类，包含一个 `public static final` 的 `INSTANCE` 字段
- 实例在 `<clinit>` 静态初始化块中创建
- **线程安全**：JVM 保证 `<clinit>` 只执行一次，多线程环境下会同步执行

**by lazy 原理**：
- 编译后生成一个 `Lazy<T>` 对象，默认实现为 `SynchronizedLazyImpl`
- 内部使用 `lazyValue` 存储值，`lock` 对象作为同步锁
- 首次调用 `get()` 时通过 `synchronized(lock)` 保证线程安全，执行 lambda 并缓存结果

| 模式 | 线程安全 | 实现方式 |
|------|---------|---------|
| `LazyThreadSafetyMode.SYNCHRONIZED`（默认） | 是 | `synchronized` 双重检查 |
| `LazyThreadSafetyMode.PUBLICATION` | 是 | CAS 操作 |
| `LazyThreadSafetyMode.NONE` | 否 | 无同步保证 |

### 2.4 如何写一个线程级别的单例？原理？

```kotlin
class ThreadLocalSingleton {
    companion object {
        private val threadLocal = ThreadLocal<ThreadLocalSingleton>()

        fun getInstance(): ThreadLocalSingleton {
            var instance = threadLocal.get()
            if (instance == null) {
                instance = ThreadLocalSingleton()
                threadLocal.set(instance)
            }
            return instance
        }
    }
}
```

**原理**：每个 `Thread` 对象内部维护一个 `ThreadLocalMap`，`ThreadLocal` 实例作为 key，存储的值作为 value。不同线程通过同一个 `ThreadLocal.get()` 获取的是各自线程私有的实例，互不干扰。常用于 `Looper` 的线程绑定、数据库连接管理等场景。

---

## 三、并发编程

### 3.1 实现线程安全的方式有哪些？

1. **互斥同步（阻塞同步）**：`synchronized`、`ReentrantLock`，同一时刻只有一个线程访问临界区
2. **非阻塞同步（乐观锁）**：CAS 操作（`AtomicInteger`、`AtomicReference`），失败自旋重试
3. **无同步方案**：
   - **线程本地存储**（`ThreadLocal`）：每个线程独立副本，无共享即无竞争
   - **不可变对象**（`final`、`String`、`data class` 配合 `val`）：创建后状态不可变
   - **栈封闭**：局部变量存储在线程栈中，天然线程安全
4. **并发数据结构**：`ConcurrentHashMap`、`CopyOnWriteArrayList`、`BlockingQueue` 等

### 3.2 线程安全的 Map、List 及其实现方式？

**线程安全 Map**：

| 类 | 实现方式 | 性能 |
|----|---------|------|
| `Hashtable` | 全表锁（`synchronized`），所有操作串行 | 差 |
| `Collections.synchronizedMap` | 包装器模式，全表锁 | 差 |
| `ConcurrentHashMap` | JDK 7：分段锁（`Segment[]`）；JDK 8：CAS + `synchronized` 锁桶头节点 | 高并发优秀 |

**线程安全 List**：

| 类 | 实现方式 | 适用场景 |
|----|---------|---------|
| `Vector` | 全表锁 | 已过时 |
| `Collections.synchronizedList` | 包装器，全表锁 | 简单场景 |
| `CopyOnWriteArrayList` | 写时复制（写操作复制整个数组，读无锁） | 读多写少场景 |

### 3.3 volatile 的作用？

`volatile` 有三个核心语义：

1. **可见性**：一个线程修改 `volatile` 变量后，其他线程立即可见。实现原理是通过插入内存屏障（LoadLoad / StoreStore），禁止 CPU 缓存优化，强制从主存读写。
2. **有序性**：禁止指令重排序。例如 DCL 单例中 `instance = new Singleton()` 的三步操作（分配内存 → 初始化 → 赋值引用）可能被重排，导致其他线程拿到未初始化的对象。`volatile` 通过插入 StoreStore 屏障阻止这种重排。
3. **不保证原子性**：`volatile int count; count++` 实际是 read-modify-write 三步操作，多线程下仍不安全，需要用 `AtomicInteger`。

### 3.4 Java 中有哪些锁？区别？

| 锁类型 | 实现 | 特点 |
|--------|------|------|
| `synchronized` | JVM 内置（monitorenter/monitorexit） | 自动释放，不可中断，非公平，支持锁升级 |
| `ReentrantLock` | AQS | 可中断、可超时、公平/非公平、支持多 Condition |
| `ReentrantReadWriteLock` | AQS | 读锁共享、写锁独占，适合读多写少 |
| `StampedLock` | AQS（Java 8+） | 乐观读锁，性能更高，但不可重入 |
| `LockSupport.park/unpark` | 底层线程阻塞原语 | 线程级别精确阻塞/唤醒 |

### 3.5 ReentrantLock 原理？如何让其他线程阻塞？是公平锁吗？如何保证公平？

**原理**：基于 AQS（AbstractQueuedSynchronizer）实现。
- 内部维护 `state` 变量表示锁状态（0 = 未锁定，>0 = 重入次数）
- 加锁时通过 CAS 尝试将 `state` 从 0 改为 1，成功则获取锁
- 失败则将当前线程封装为 CLH 队列的 Node，调用 `LockSupport.park()` 阻塞

**公平锁保证**：构造时传入 `fair = true`。
- **公平锁**：`tryAcquire()` 中先调用 `hasQueuedPredecessors()` 检查 CLH 队列是否有等待更久的线程，有则排队，保证 FIFO
- **非公平锁**：新线程直接 CAS 抢锁（"插队"），失败才加入队列

### 3.6 协程中的锁？与 Java 锁的区别？

协程中的锁主要有：
- **`Mutex`**：互斥锁，`lock()` / `unlock()`，支持 `withLock {}` 语法糖
- **`Semaphore`**：信号量，控制并发访问数量

**与 Java 锁的核心区别**：

| 维度 | Java 锁（synchronized/ReentrantLock） | 协程锁（Mutex） |
|------|--------------------------------------|----------------|
| 阻塞方式 | 阻塞操作系统线程（park） | 挂起协程（suspend），不阻塞线程 |
| 性能开销 | 线程上下文切换，开销大 | 协程切换在用户态完成，开销极小 |
| 可中断性 | 需要显式处理 | 天然支持协程取消 |
| 使用场景 | 传统多线程 | 协程并发控制 |

**注意**：`Mutex` 不能跨协程边界使用（不能在一个协程中 lock，另一个中 unlock），需要配合 `withLock` 使用。

### 3.7 多线程资源竞争如何解决？

1. **避免共享**：使用 `ThreadLocal` 线程私有存储、不可变对象、栈封闭
2. **互斥锁**：`synchronized`、`ReentrantLock`，保证同一时刻只有一个线程访问
3. **CAS 乐观锁**：`Atomic` 系列类，自旋重试，适合竞争不激烈的场景
4. **并发数据结构**：`ConcurrentHashMap`、`BlockingQueue` 等内部已处理并发
5. **信号量**：`Semaphore` 控制同时访问资源的线程数量
6. **协程方案**：`Mutex`、`Channel`（Actor 模型），避免线程阻塞

---

## 四、协程

### 4.1 协程的异常处理机制？

协程的异常处理取决于 `CoroutineScope` 和 `Job` 的结构：

1. **`launch`**：异常会向上传播给父协程，导致整个 `Job` 树取消。可通过 `CoroutineExceptionHandler` 捕获（仅顶层协程有效）。
2. **`async`**：异常被封装在 `Deferred` 中，只有在调用 `await()` 时才抛出。
3. **`supervisorScope`**：子协程异常不会传播给父协程和其他兄弟协程，适合独立任务的隔离。

```kotlin
// CoroutineExceptionHandler 示例
val handler = CoroutineExceptionHandler { _, exception ->
    Log.e("Coroutine", "Caught: ${exception.message}")
}
scope.launch(handler) {
    throw RuntimeException("error")
}
```

**关键规则**：`CoroutineExceptionHandler` 只在顶层协程（没有父 Job）中生效。子协程的异常应向上传播给父协程处理。

### 4.2 协程的取消机制？

1. **协作式取消**：协程通过检查 `isActive` 或调用 `ensureActive()` 来响应取消，不是强制中断。
2. **取消传播**：父协程取消会级联取消所有子协程；子协程取消默认也会取消父协程（除非使用 `supervisorScope`）。
3. **取消异常**：取消通过抛出 `CancellationException` 实现，它被视为正常终止，不会被 `CoroutineExceptionHandler` 捕获。
4. **不可取消代码段**：使用 `withContext(NonCancellable) { ... }` 包裹需要执行但不可取消的操作（如资源释放）。

```kotlin
// 在耗时操作中检查取消状态
while (isActive) {
    // 执行计算
    delay(100) // delay 内部会自动检查取消
}
```

### 4.3 suspend 关键字的实现原理？如何优化？

**原理**：`suspend` 函数编译后会被转换为**状态机**（CPS，Continuation-Passing Style）：
- 函数参数中增加一个 `Continuation` 参数
- 函数体被 `suspend` 标记点拆分为多个状态（`label = 0, 1, 2, ...`）
- 每次挂起时保存当前状态（局部变量、label），返回 `COROUTINE_SUSPENDED`
- 恢复时根据 `label` 跳转到对应位置继续执行

**优化方向**：
1. 减少挂起点数量：合并连续的 suspend 调用
2. 使用 `withContext(Dispatchers.IO)` 避免阻塞主线程
3. 避免在循环中频繁挂起，批量处理
4. 使用 `Flow` 替代多次 suspend 调用

### 4.4 协程切线程的原理？

`withContext(Dispatchers.IO) { ... }` 的切线程原理：
1. 当前协程在调用 `withContext` 时挂起（返回 `COROUTINE_SUSPENDED`）
2. `withContext` 内部将后续代码块作为新的协程任务提交到目标 `CoroutineDispatcher`（如 IO 线程池）
3. 目标线程池执行代码块，完成后通过 `Continuation.resumeWith()` 恢复原协程
4. 恢复时回到原来的 `Dispatcher` 继续执行

本质是**挂起当前协程 → 在目标线程恢复执行 → 完成后恢复原协程**，不涉及线程阻塞。

### 4.5 Flow 的使用？StateFlow、SharedFlow、Channel 的区别？

**Flow 基础使用**：
```kotlin
// 创建
fun fetchData(): Flow<String> = flow {
    emit("data1")
    emit("data2")
}

// 收集
viewModelScope.launch {
    fetchData().collect { value -> println(value) }
}
```

| 维度 | Flow | StateFlow | SharedFlow | Channel |
|------|------|-----------|------------|---------|
| 热/冷 | 冷流，每次 collect 重新执行 | 热流，始终活跃 | 热流，始终活跃 | 热流 |
| 初始值 | 无 | 必须有 `value` 初始值 | 可选 `replay` | 无 |
| 状态保持 | 不保持 | 始终保持最新值 | 可配置 replay 缓存 | 不保持 |
| 粘性 | 否 | 是（新订阅者立即获取当前值） | 可配置 | 否 |
| 多消费者 | 每个 collect 独立 | 支持多消费者 | 支持多消费者 | 一对一（或 Buffer） |
| 生命周期 | 跟随 collector | 跟随 scope | 跟随 scope | 需手动关闭 |
| 典型场景 | 一次性数据流 | UI 状态（如 loading、data） | 事件流（如 toast、导航） | 协程间通信 |

---

## 五、Handler 消息机制

### 5.1 Handler / Looper / MessageQueue 三者关系？

- **Handler**：负责发送和处理消息。通过 `sendMessage()` / `post()` 将消息插入 MessageQueue，通过 `handleMessage()` 处理消息。
- **Looper**：负责循环从 MessageQueue 中取出消息并分发给对应 Handler。调用 `loop()` 进入死循环。
- **MessageQueue**：按消息执行时间（`when`）排序的单链表，负责存储消息。

**三者关系**：一个 Thread 只有一个 Looper，一个 Looper 只有一个 MessageQueue，一个 Looper 可以对应多个 Handler。Handler 构造时绑定当前线程的 Looper，通过 Looper 把消息放入 MessageQueue，Looper 循环取出消息后回调 Handler 的 `handleMessage()`。

### 5.2 消息优先级机制？如何发送一个优先级最高的消息？

MessageQueue 按 `when` 时间排序，没有显式的优先级字段。发送最高优先级消息的方式：

```kotlin
handler.sendMessageAtFrontOfQueue(msg) // when = 0，插入链表头部
```

`sendMessageAtFrontOfQueue()` 将 `when` 设为 0，在 `enqueueMessage()` 中会被插入到链表最前面（在当前最早消息之前）。但注意 `when = 0` 会被替换为 `SystemClock.uptimeMillis()`，所以它只是"当前最早"，不是绝对最前。

### 5.3 什么是同步屏障 / 异步消息？使用场景？

- **同步屏障**：通过 `MessageQueue.postSyncBarrier()` 插入一条 `target == null` 的特殊消息。屏障存在期间，`MessageQueue.next()` 只分发异步消息，跳过普通同步消息。
- **异步消息**：通过 `Handler.post(target, msg, uptimeMillis).setAsynchronous(true)` 设置。

**使用场景**：UI 渲染。`ViewRootImpl.scheduleTraversals()` 中先插入同步屏障，再发送异步消息触发 `performTraversals()`，确保 UI 渲染消息优先处理，不被普通消息阻塞。这就是为什么 UI 刷新能及时进行的原因。

### 5.4 阻塞唤醒原理？只要往 MessageQueue 里发消息就会唤醒吗？

**阻塞/唤醒基于 Linux `epoll` 机制**：
- `MessageQueue` 初始化时通过 `nativeInit()` 创建 epoll fd 和事件管道（pipe）
- 没有消息或下一条消息未到期时，`next()` 调用 `nativePollOnce()` → `epoll_wait()` 阻塞线程
- 需要唤醒时调用 `nativeWake()` 向 pipe 写端写入数据，epoll 检测到可读事件，`epoll_wait` 返回，线程唤醒

**不是每次插入消息都会唤醒**，只有满足以下条件之一才触发 `nativeWake`：
1. 队列原本为空
2. 新消息的 `when` 比队列头部消息更早（成为新的队头）
3. 队头是屏障消息且新消息是异步消息

### 5.5 nativeWake 为何可以唤醒？epoll 的其他应用场景？

`nativeWake` 向 pipe 写端写入一个字节。pipe 读端被 epoll 监听，`epoll_wait` 检测到可读事件后返回，线程从阻塞态变为可运行态。

**epoll 应用场景**：
- NIO / Netty 的 IO 多路复用
- Nginx 事件驱动模型
- Redis 事件循环
- Android MessageQueue 消息唤醒
- 数据库连接池

### 5.6 还有哪些操作可以阻塞线程？跟 BlockingQueue 阻塞唤醒的区别？

**其他阻塞操作**：
- `Object.wait()` / `Thread.sleep()`
- `LockSupport.park()` / `unpark()`
- `BlockingQueue.take()` / `put()`
- `synchronized` 获取锁失败
- `CountDownLatch.await()` / `Semaphore.acquire()`
- `ServerSocket.accept()` / `InputStream.read()`（阻塞 IO）

**MessageQueue vs BlockingQueue 的区别**：

| 维度 | MessageQueue | BlockingQueue |
|------|-------------|---------------|
| 底层机制 | epoll + pipe（系统调用级） | `ReentrantLock` + `Condition`（JVM 级 `LockSupport.park`） |
| 阻塞层级 | 内核态挂起 | 用户态 park |
| 唤醒方式 | pipe 写数据触发 epoll | `signal` / `unpark` |
| 数据结构 | 按时间排序的单链表 | 多种实现（Array/Linked/Delay 等） |
| 使用场景 | Handler 消息循环 | 生产者-消费者、线程池任务队列 |

---

## 六、性能优化

### 6.1 冷启动优化方案？如何做冷启动监控？

**优化方案**：

1. **Application 优化**：延迟初始化非必要组件，使用 `IdleHandler` 在主线程空闲时执行；使用 `AppStartup` 库并行初始化
2. **首屏优化**：减少布局层级（ConstraintLayout 扁平化），异步加载数据，骨架屏先行渲染
3. **启动页优化**：使用 XML theme 的 `windowBackground` 作为启动窗口，避免白屏
4. **I/O 优化**：减少主线程 IO，SharedPreferences 迁移到 MMKV
5. **类加载优化**：使用 Baseline Profile / ART Profile 预编译关键路径，减少 JIT 编译开销
6. **资源优化**：减少 Resources 加载，避免在 Application 中做大量资源解码

**冷启动监控**：
- **系统方案**：`adb shell am start -W` 获取 `TotalTime`
- **自定义方案**：在 Application 构造时记录时间戳，在首屏 Activity 的 `onWindowFocusChanged` 时计算差值
- **高级方案**：通过 `DisplayManager` 获取首帧渲染时间；使用 `ActivityTaskManager` 监听启动耗时

### 6.2 复杂页面（如交易页）的构建方案？

1. **模块化设计**：将页面拆分为独立模块/组件，每个模块有自己的数据源和 UI，通过 `ConcatAdapter` 或 `Epoxy` 管理复杂列表
2. **异步并行加载**：数据请求并行化（多个协程同时请求），UI 骨架屏先行渲染
3. **ViewStub 延迟加载**：非首屏模块使用 ViewStub 按需加载，减少首帧渲染压力
4. **数据预取**：进入页面前预加载数据（在上一个页面发起请求）
5. **渲染优化**：减少过度绘制（`adb shell dumpsys gfxinfo`），扁平化布局，避免嵌套过深
6. **局部刷新**：使用 `DiffUtil` 精确更新变化的部分，避免全量刷新

### 6.3 卡顿检测与优化方案？

**检测方案**：

| 方案 | 原理 | 适用阶段 |
|------|------|---------|
| **Systrace / Perfetto** | 系统级 trace，查看帧渲染各阶段耗时 | 开发 & 分析 |
| **BlockCanary** | `Looper.setMessageLogging()` 监控 `dispatchMessage` 耗时，超过阈值记录堆栈 | 开发 & 线上 |
| **Choreographer.FrameCallback** | 监控每帧 `doFrame` 耗时 | 开发 |
| **AGI（Android GPU Inspector）** | GPU 渲染分析 | 开发 |
| **StrictMode** | 检测主线程磁盘 IO 和网络操作 | 开发 |

**优化方案**：
1. 避免主线程耗时操作（IO、大量计算、数据库）
2. 减少布局层级，使用 ConstraintLayout
3. 避免过度绘制，减少透明层叠
4. 列表优化：使用 `DiffUtil`、`payload` 局部刷新、`setHasFixedSize`
5. 图片优化：异步加载、缓存、降采样
6. 使用 `RecyclerView` 的 `RecycledViewPool` 预创建 ViewHolder

### 6.4 仓位卡顿优化、强平公式优化？

1. **数据层面**：WebSocket 增量更新代替全量推送，减少数据解析量
2. **渲染层面**：使用 `const` Widget / `shouldRebuild` 减少不必要的重建；列表使用 `ListView.builder` 懒加载
3. **计算层面**：持仓盈亏计算、杠杆计算等放到后台 Isolate / 协程 IO 线程，避免阻塞 UI 线程
4. **频率控制**：对高频推送数据进行节流（throttle），合并短时间内多次更新为一次 UI 刷新
5. **强平公式优化**：预计算不变量，缓存中间结果，避免每次价格变动都重新计算全部字段

---

## 七、网络

### 7.1 HttpDNS 为何要做？如何实现？

**为什么要做**：传统 LocalDNS 基于 UDP，存在以下问题：
- DNS 劫持（运营商层面）
- 解析延迟（首次查询需递归查询）
- TTL 不准确（本地缓存过期策略不可控）
- 调度不精准（无法利用 HTTP 层的地理位置信息）

**实现方案**：
1. 通过 HTTP/HTTPS 向 HttpDNS 服务端请求域名对应的 IP 列表
2. 客户端缓存 IP 及 TTL
3. 在 OkHttp 中通过自定义 `Dns` 接口替换系统 DNS 解析
4. 将域名替换为 IP 直连

### 7.2 在 Interceptor 里把 baseURL 从域名替换成 IP 直连会有什么问题？

1. **HTTPS 证书校验失败**：SSL 证书是颁发给域名的，IP 直连时 SNI 不匹配，证书校验失败
2. **Cookie 问题**：Cookie 基于域名设置，IP 无法匹配
3. **CDN 失效**：无法利用 CDN 就近调度
4. **解决方案**：自定义 `SSLSocketFactory`，在 `checkServerTrusted` 中将 hostname 设为原始域名；自定义 `CookieJar` 处理域名匹配

### 7.3 IP 择优策略？HttpDNS 服务挂了怎么办？

- **IP 择优**：HttpDNS 返回多个 IP，客户端通过 TCP connect 延迟测试选择最优 IP；或使用 IP 轮询 + 失败自动切换
- **降级策略**：HttpDNS 失败时回退到 LocalDNS；缓存上次解析结果，过期后仍可使用（延长 TTL）

### 7.4 Dns.lookup() 的调用时机？ConnectionPool 复用连接可以节省哪部分时间？

- `Dns.lookup()` 在 OkHttp 建立连接时调用（`RealConnection.connect()` 的 DNS 解析阶段）
- `ConnectionPool` 复用连接可以节省：**TCP 三次握手** + **TLS 握手**（HTTPS 场景）的时间，通常可节省 1~3 个 RTT

### 7.5 OkHttp 会自动重试哪些请求？如何避免重复下单？

- `retryOnConnectionFailure(true)` 时，对连接失败（IOException）、路由失败自动重试
- **POST 请求默认也会重试**，存在重复下单风险

**避免重复下单**：
1. 服务端做幂等性校验（唯一订单号 / 幂等 token）
2. 客户端对下单请求设置 `retryOnConnectionFailure(false)`
3. 使用 `Cache-Control: no-cache` 避免缓存干扰

### 7.6 HTTP/1.1 下 OkHttp 默认配置，100 个相同域名请求最终有几条连接？HTTP/2 又如何？

- **HTTP/1.1**：`ConnectionPool` 默认 `maxIdleConnections = 5`，但**活跃连接不受此限制**。100 个并发请求会建立 **100 条连接**（每个请求一条），完成后只保留 5 条空闲连接
- **HTTP/2**：支持多路复用，同一域名只需 **1 条连接**，100 个请求通过这条连接的多个 Stream 并发传输

### 7.7 WebSocket 稳定性如何保障？

1. **心跳机制**：定时发送 Ping/Pong 帧检测连接存活，超时未收到 Pong 则判定断连
2. **断线重连**：指数退避重连策略（1s → 2s → 4s → 8s...），避免频繁重连给服务端造成压力
3. **消息确认**：应用层 ACK 机制保证消息送达，未确认的消息重发
4. **多链路备份**：WebSocket + 长轮询降级方案

### 7.8 WebSocket 高频消息如何优化？如何防止 buffer 内存膨胀？

1. **消息合并**：短时间内多次更新合并为一次推送（如 100ms 内的 OrderBook 变更合并为一次）
2. **节流/限流**：控制消息发送频率，UI 层按帧率刷新
3. **背压机制**：消费端处理不过来时通知发送端降速
4. **buffer 管理**：设置最大 buffer 大小，超出时丢弃旧消息或断开重连
5. **二进制协议**：使用 Protobuf / FlatBuffers 等紧凑格式减少数据量

### 7.9 提升 WebSocket 订阅逻辑健壮性需要考虑哪些方面？

1. **连接状态检查**：订阅前确认连接已建立（`OPEN` 状态）
2. **重连后重新订阅**：断线重连后自动恢复订阅列表
3. **消息去重**：通过消息 ID 或序号防止重复推送
4. **异常处理**：单条消息解析失败不影响后续消息处理（try-catch 隔离）
5. **超时机制**：订阅请求超时未响应则重试或告警
6. **资源释放**：页面销毁时取消订阅、关闭连接，避免内存泄漏
7. **心跳保活**：定时 Ping 防止连接被中间代理断开

### 7.10 WebView 为什么无法直接复用原生网络层？Native 代发如何实现？是否会绕过 CORS？

- **无法复用原因**：WebView 使用 Chromium 内核，有自己独立的网络栈，与原生 OkHttp 网络栈隔离
- **Native 代发实现**：通过 `WebViewClient.shouldInterceptRequest()` 拦截请求，用原生 OkHttp 发起请求，将响应 InputStream 返回给 WebView
- **会绕过 CORS**：请求由原生网络栈发出，不受浏览器同源策略限制
- **跨域定义**：协议、域名、端口任一不同即为跨域，浏览器通过 CORS 机制限制跨域请求

### 7.11 流量优化、最优链路选择？

1. **协议优化**：HTTP/2 多路复用、WebSocket 长连接减少连接开销
2. **数据压缩**：Gzip / Brotli 压缩、Protobuf 序列化减少传输体积
3. **CDN 加速**：静态资源走 CDN 就近分发
4. **IP 择优**：HttpDNS + 延迟测试选择最优服务器 IP
5. **连接复用**：OkHttp `ConnectionPool` 复用 TCP 连接
6. **请求合并**：批量接口减少请求次数
7. **缓存策略**：合理使用 HTTP 缓存、本地缓存减少网络请求

---

## 八、UI 与渲染

### 8.1 invalidate() 和 requestLayout() 的区别？连续调用多次会有什么后果？

| 维度 | invalidate() | requestLayout() |
|------|-------------|-----------------|
| 触发流程 | 仅触发 draw 过程 | 触发 measure → layout → draw |
| 适用场景 | View 外观变化（颜色、文本等） | View 尺寸变化 |
| 线程要求 | 必须在主线程调用 | 必须在主线程调用 |
| 连续调用多次 | 同一帧只绘制一次（`mDirty` 区域合并） | `ViewRootImpl` 通过 `mLayoutRequested` 标记合并，同一帧只执行一次 measure/layout/draw |

### 8.2 Handler().post 能否正确获取 View 的宽高？

**大多数情况下不能**。`post()` 将 Runnable 添加到 MessageQueue 末尾，但执行时机取决于 Looper 当前处理进度。如果 View 还没完成 measure/layout，获取的宽高为 0。

**正确获取 View 宽高的方式**：
1. `View.post()`：View attach 到窗口后是可靠的（`ViewRootImpl` 将 Runnable 加入 `RunQueue`，在 `performTraversals` 完成后执行）
2. `ViewTreeObserver.addOnGlobalLayoutListener`
3. `onWindowFocusChanged()` 中获取

### 8.3 打开一个 Activity，第一帧数据绘制的触发点？

1. `ActivityThread.handleLaunchActivity()` → `handleResumeActivity()` → `Activity.performResume()`
2. `onResume()` 之后，`ViewRootImpl` 被创建并调用 `setView()`（通过 `WindowManager.addView(decorView)`）
3. `setView()` 中调用 `requestLayout()` → `scheduleTraversals()`
4. 下一个 VSYNC 信号到来时执行 `performTraversals()` 完成第一帧绘制

**触发点**：`ViewRootImpl.scheduleTraversals()`，在 `ViewRootImpl.setView()` 中被首次调用。

### 8.4 MPAndroidChart 使用场景？踩过哪些坑？如何优化？

**使用场景**：K 线图、折线图、柱状图、饼图等金融数据可视化。

**常见坑**：
1. 数据量大时滑动卡顿（默认全量渲染）
2. 动态更新数据时闪烁
3. 手势冲突（与外层 ViewPager / RecyclerView 冲突）
4. 高亮线 / MarkerView 显示位置偏移

**优化方案**：
1. 使用 `setVisibleXRange()` 限制可见范围，配合 `setDataAndVisibility()` 按需渲染
2. 使用 `moveViewToX()` 实现实时滚动效果
3. 重写 `onInterceptTouchEvent` 处理手势冲突
4. 使用 `setDrawValues(false)` 关闭数值绘制减少开销
5. 大量数据时使用 `CandleStickChart` 的 `setReducedOffsets()` 降采样

---

## 九、抓包

### 9.1 Charles 抓包原理？

Charles 作为**中间人代理**（MITM, Man-In-The-Middle）：
1. 手机配置 Charles 的代理地址和端口
2. Charles 生成自签名 CA 证书，手机安装并信任
3. 请求流程：手机 → Charles（解密）→ 服务器；服务器响应 → Charles（加密）→ 手机
4. Charles 可以查看和修改请求/响应内容，支持断点调试、限速模拟等

### 9.2 AI 抓包工具的实现？

1. 基于 MITM 代理（类似 Charles/mitmproxy）捕获网络流量
2. 解析 HTTP/HTTPS 请求和响应，提取关键信息（URL、Header、Body）
3. 将抓包数据格式化后发送给 AI 模型分析
4. AI 根据请求/响应内容提供调试建议、异常检测、性能分析

---

## 十、AI 辅助开发

### 10.1 AI 开发工具的完整流程？（SuperPower 的流程）

1. **需求分析**：理解用户需求，拆解为具体的开发任务
2. **上下文构建**：收集项目代码、架构信息、编码规范作为 AI 上下文
3. **代码生成**：AI 根据上下文生成代码
4. **代码审查**：AI Code Review 检查代码质量、风格、潜在 bug
5. **测试生成**：自动生成单元测试
6. **持续集成**：集成到 CI/CD 流程
7. **反馈迭代**：根据开发者反馈优化生成策略

### 10.2 AI Code Review 如何设计？

1. **触发机制**：PR 创建或更新时自动触发（Webhook）
2. **上下文收集**：收集 diff、相关文件、项目规范（`.cursorrules`、`CONVENTIONS.md`）
3. **AI 分析**：
   - 代码风格与规范检查
   - 潜在 bug 和空指针检测
   - 性能问题识别（主线程 IO、内存泄漏风险）
   - 安全漏洞扫描
4. **结果输出**：以 PR 评论形式反馈，标注文件和行号
5. **学习迭代**：根据开发者反馈（采纳/忽略）调整规则权重

### 10.3 如何提升 Figma / UI 还原度？Skill 读取的项目规范如何保证还原度？

1. 提供设计稿截图作为 AI 上下文
2. 使用 Design Token 统一颜色、字号、间距等设计规范
3. Skill 读取项目中的 UI 规范文件（如 `CONVENTIONS.md`），确保生成的代码符合组件库标准
4. 像素级对比工具检测实现与设计稿的差异
5. 组件库标准化，减少自定义 View 的偏差

### 10.4 Session 压缩、跨 Session 开发？

- **Session 压缩**：将长对话历史压缩为摘要，保留关键决策和上下文，减少 Token 消耗。例如将 100 轮对话压缩为"已完成 A 模块开发，正在处理 B 模块的 X 问题"。
- **跨 Session 开发**：将项目状态、架构决策、开发进度持久化到 Memory 系统。新 Session 启动时加载历史上下文，实现"记忆延续"。

### 10.5 Token 消耗、上下文管理？

- 上下文窗口有限（如 128K），需要精细管理输入输出 Token
- 管理策略：
  1. 对话历史截断（保留最近 N 轮）
  2. 摘要压缩（长对话 → 关键信息）
  3. RAG（检索增强生成）：按需检索相关代码片段
  4. 分层上下文：系统提示（固定） + 知识（按需） + 对话（动态）

### 10.6 Harness、Agent？

- **Harness**：运行环境和工具集，提供文件读写、终端执行、代码搜索等能力
- **Agent**：具备自主决策能力的 AI 实体，根据目标自动规划、执行、验证任务
- **应用流程**：Agent 接收任务 → 分析代码库 → 制定计划 → 使用 Harness 提供的工具执行修改 → 验证结果 → 反馈调整

---

## 十一、业务与综合

### 11.1 KYC 模块：识别率如何优化？Camera 遇到哪些坑？

**识别率优化**：
1. 图像预处理：灰度化、二值化、透视矫正
2. 多帧合成：连续拍摄多帧，选择最清晰的一帧
3. 光线检测：引导用户调整光线环境
4. 边缘检测 + 裁剪：自动识别证件边缘并裁剪
5. 接入第三方 OCR SDK（如百度 OCR、Google ML Kit）提升识别精度

**Camera 常见坑**：
1. 不同厂商 Camera2 API 行为不一致
2. 相机权限被拒绝后的降级处理
3. 预览画面旋转/镜像问题（前置摄像头）
4. 相机被其他应用占用（`CameraDevice.StateCallback.onError`）
5. 低光环境下对焦慢、噪点多

### 11.2 AI Chat 如何实现类似打字机的响应效果？与 WebSocket 的区别？

- **打字机效果**：服务端使用 SSE（Server-Sent Events）流式返回 Token，客户端逐字渲染。每收到一个 chunk 就追加到 UI 上。
- **与 WebSocket 区别**：

| 维度 | SSE | WebSocket |
|------|-----|-----------|
| 方向 | 单向（服务端 → 客户端） | 双向 |
| 协议 | 基于 HTTP | 独立协议（ws://） |
| 重连 | 浏览器自动重连 | 需手动实现 |
| 数据格式 | 纯文本 | 文本 / 二进制 |
| 适用场景 | AI 响应流式输出 | 实时交互（交易、聊天室） |

### 11.3 ANR 的分析、定位及解决方案？

**分析定位**：
1. 查看 `/data/anr/traces.txt` 或 `bugreport` 中的 ANR 日志
2. 关注主线程堆栈，找到阻塞点（`blocked`、`waiting`、`sleeping`）
3. 常见原因：主线程 IO、锁竞争、Binder 调用超时

**ANR 触发条件**：
- Input 事件 5 秒内未响应
- Service 前台 20 秒 / 后台 200 秒未完成
- BroadcastReceiver 10 秒内未完成

**解决方案**：
1. 避免主线程 IO（SP、数据库、文件读写）
2. 避免主线程大量计算
3. 避免主线程 Binder 调用耗时操作
4. 使用 `StrictMode` 检测主线程违规操作
5. 使用 `Looper.setMessageLogging()` 监控主线程消息耗时

### 11.4 交易下单流程？

1. 用户选择交易对、方向（买/卖）、价格、数量
2. 客户端参数校验（价格精度、数量范围、余额检查）
3. 提交订单 → 服务端风控系统检查（余额、限额、频率限制）
4. 订单进入撮合引擎，按价格优先、时间优先原则匹配
5. 成交后更新持仓、余额
6. 通过 WebSocket 推送订单状态和成交信息

### 11.5 OrderBook 合并逻辑？

- OrderBook 按价格档位维护买卖盘口
- **合并逻辑**：相同价格的订单合并为一档，显示总量
- 价格精度处理：按最小价格步进取整
- **实时更新**：通过 WebSocket 增量更新（`diff` 模式），本地维护完整 OrderBook
- 前端按精度分组显示，支持点击价格档位快速下单

### 11.6 链上充值？

1. 用户获取充值地址（交易所分配或用户钱包）
2. 用户从外部钱包转账到充值地址
3. 链上节点确认交易（等待区块确认数，如 BTC 需 3 个确认）
4. 后端监听链上事件，检测到充值交易
5. 达到确认数后，更新用户账户余额
6. 通知用户充值成功

### 11.7 Remote ADB？

Remote ADB 允许通过网络连接 Android 设备进行调试：
1. 设备端开启 `adb tcpip 5555`（切换到 TCP 模式）
2. 电脑端 `adb connect <设备IP>:5555`
3. 之后可像 USB 连接一样使用 adb 命令
4. Android 11+ 支持无线调试（基于 mDNS 自动发现），无需手动开启 TCP 模式

### 11.8 MVVM vs MVI？

| 维度 | MVVM | MVI |
|------|------|-----|
| 数据流 | 双向/多向 | 单向（Intent → Model → View） |
| 状态管理 | 多个 LiveData / StateFlow 分别管理 | 单一 State 对象（data class）管理所有状态 |
| 状态一致性 | 多个状态源可能出现不一致 | 状态唯一，不会不一致 |
| 可测试性 | 好 | 好（纯函数 reducer） |
| 复杂度 | 适中 | 状态多时 Intent 爆炸，reducer 逻辑复杂 |
| 适用场景 | 大多数项目 | 状态复杂且需要严格一致性的场景 |

---

## 十二、架构设计

### 12.1 MVP 与 MVVM 的区别？

| 维度 | MVP | MVVM |
|------|-----|------|
| View 与 Model | Presenter 持有 View 接口，手动更新 UI | ViewModel 暴露 Observable 数据，View 自动更新 |
| 数据绑定 | 手动绑定 | 数据驱动（LiveData / DataBinding / StateFlow） |
| 生命周期 | Presenter 不感知生命周期 | ViewModel 感知生命周期，自动管理 |
| 测试性 | 好（View 接口隔离） | 好（ViewModel 独立于 View） |

### 12.2 EventBus 的原理？如何实现 postSticky 粘性事件？

- **原理**：基于观察者模式 + 反射。`register()` 时通过反射扫描订阅者的 `@Subscribe` 注解方法，建立事件类型到订阅方法的映射。`post()` 时查找对应事件类型的订阅方法并调用。
- **postSticky**：将事件存储在内存 `Map<Class<?>, Object>` 中（`stickyEvents`）。新订阅者 `register()` 时检查 `stickyEvents`，如果有匹配的粘性事件，立即分发给该订阅者。

---

## 十三、序列化

### 13.1 Serializable 与 Parcelable 的区别？

| 维度 | Serializable | Parcelable |
|------|-------------|------------|
| 来源 | Java 标准库 | Android 特有 |
| 实现方式 | 标记接口，反射序列化 | 实现 `writeToParcel` / `CREATOR` |
| 性能 | 慢（反射、临时对象多、GC 压力大） | 快（直接内存操作） |
| 使用场景 | 磁盘存储、网络传输 | 内存传递（Intent、Bundle） |

**Parcelable 本地序列化**：Parcelable 本身不适合持久化（`Parcel` 是内存数据结构，格式不稳定）。如需本地持久化，应先将 Parcelable 对象转为 JSON / ProtoBuf 再存储。
