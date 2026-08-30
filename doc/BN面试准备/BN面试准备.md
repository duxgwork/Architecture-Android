# BN面试准备（含答案）

## 一、Android 原生

### 1. Handler 消息机制

**Q: Handler / Looper / MessageQueue 三者的关系？**

- **Handler**：负责发送和处理消息。通过 `sendMessage()` / `post()` 将消息插入 MessageQueue，通过 `handleMessage()` 处理分发消息。
- **Looper**：负责循环从 MessageQueue 中取出消息并分发给对应的 Handler 处理。调用 `loop()` 方法进入死循环。
- **MessageQueue**：按消息执行时间（`when`）排序的单链表，负责存储和提供消息。
- 三者关系：一个 Thread 只有一个 Looper，一个 Looper 只有一个 MessageQueue，一个 Looper 可以对应多个 Handler。Handler 构造时会绑定当前线程的 Looper，通过 Looper 把消息放入 MessageQueue，Looper 循环取出消息后回调 Handler 的 `handleMessage()`。

**Q: 消息优先级机制是什么？如何发送一条最高优先级的消息？**

- MessageQueue 是按 `when` 时间排序的单链表，没有显式的优先级字段。但可以通过设置 `when = 0`（或极小值）让消息排在队列最前面，相当于最高优先级。
- `MessageQueue.enqueueMessage()` 中，如果 `when == 0`，则 `msg.when` 会被设为 `SystemClock.uptimeMillis()`，所以无法真正插入"绝对最前"。但可以通过 `sendMessageAtFrontOfQueue()` 发送一个 `when = 0` 的消息，它会被插入到链表头部（在当前最早消息之前）。

**Q: 什么是同步屏障与异步消息？其使用场景有哪些？**

- **同步屏障**：通过 `MessageQueue.postSyncBarrier()` 插入一个 `target == null` 的特殊消息，标记为屏障。屏障存在期间，`MessageQueue.next()` 只会分发异步消息，跳过普通同步消息。
- **异步消息**：通过 `Handler.postSyncBarrier()` 后发送的 `setAsynchronous(true)` 的消息。
- **使用场景**：主要用于 UI 渲染。`ViewRootImpl.scheduleTraversals()` 中先插入同步屏障，再发送异步消息触发 `performTraversals()`，确保 UI 渲染消息被优先处理，不被普通消息阻塞。

**Q: 请谈谈 Choreographer 的工作原理？VSYNC 信号是如何驱动渲染的？**

- `Choreographer` 通过 `DisplayEventReceiver`（JNI 层）监听 VSYNC 信号。
- 收到 VSYNC 信号后，`doFrame()` 被调用，依次执行：
  1. **输入回调**（`CALLBACK_INPUT`）
  2. **动画回调**（`CALLBACK_ANIMATION`）—— 如 `ValueAnimator`
  3. **Inset 动画回调**（`CALLBACK_INSETS_ANIMATION`）
  4. **Traversal 回调**（`CALLBACK_TRAVERSAL`）—— 即 `ViewRootImpl` 的 `scheduleTraversals`，执行 measure/layout/draw
  5. **Commit 回调**（`CALLBACK_COMMIT`）
- VSYNC 分为 `VSYNC_APP`（应用帧开始）和 `VSYNC_DISPLAY`（显示刷新），通过 `SurfaceFlinger` 协调，保证帧同步。

**Q: MessageQueue 的阻塞 / 唤醒原理是什么？是否只要往 MessageQueue 中插入消息就会触发 `nativeWake`？**

- 阻塞/唤醒基于 Linux `epoll` 机制。`MessageQueue` 初始化时通过 `nativeInit()` 创建 epoll fd 和事件管道（pipe）。
- 当没有消息或下一条消息尚未到期时，`next()` 调用 `nativePollOnce()` → `epoll_wait()` 阻塞线程。
- 当新消息入队且需要唤醒时（`needWake = true`），调用 `nativeWake()` 向 pipe 的写端写入数据，epoll 监听的读端被触发，`epoll_wait` 返回，线程被唤醒。
- **不是**每次插入消息都会 `nativeWake`。只有当满足以下条件之一才会唤醒：
  1. 队列原本为空
  2. 新消息的 `when` 比队列头部消息更早（即成为新的队头）
  3. 队列头部的消息是屏障消息且新消息是异步消息

**Q: `nativeWake` 为何能唤醒线程？`epoll` 还有哪些应用场景？**

- `nativeWake` 向 pipe 写端写入一个字节，pipe 读端被 epoll 监听。`epoll_wait` 检测到读端 fd 可读事件，返回，线程从阻塞态变为可运行态。
- epoll 应用场景：
  - NIO / Netty 等网络框架的 IO 多路复用
  - Nginx 事件驱动模型
  - Android MessageQueue 消息唤醒
  - Redis 事件循环
  - 数据库连接池

**Q: 除 `epoll` 外，还有哪些操作可以阻塞线程？**

- `Object.wait()` / `Object.notify()`
- `Thread.sleep()`
- `LockSupport.park()` / `LockSupport.unpark()`
- `BlockingQueue.take()` / `put()`
- `synchronized` 获取锁失败时
- `CountDownLatch.await()`、`Semaphore.acquire()`
- `ServerSocket.accept()`（阻塞 IO）
- `InputStream.read()`（阻塞 IO 读取）

**Q: MessageQueue 的阻塞唤醒与 `BlockingQueue` 的阻塞唤醒有何区别？**

| 维度 | MessageQueue | BlockingQueue |
|------|-------------|---------------|
| 底层机制 | epoll + pipe（Linux 系统调用） | `ReentrantLock` + `Condition`（`LockSupport.park/unpark`） |
| 阻塞层级 | 系统调用级别，线程挂起到内核态 | JVM 级别，线程在用户态 park |
| 唤醒方式 | 向 pipe 写数据触发 epoll | `signal/ signalAll` 或 `unpark` |
| 数据结构 | 按时间排序的单链表 | 多种实现（ArrayBlockingQueue、LinkedBlockingQueue 等） |
| 使用场景 | Handler 消息循环 | 生产者-消费者模型、线程池任务队列 |

**Q: `ReentrantLock` 的实现原理？如何让其他线程阻塞？是公平锁吗？如何保证公平性？**

- 基于 AQS（AbstractQueuedSynchronizer）实现。内部维护一个 `state` 变量表示锁状态，0 为未锁定，>0 为重入次数。
- 加锁时通过 CAS 尝试将 `state` 从 0 改为 1，成功则获取锁；失败则将线程封装为 Node 加入 CLH 队列并 `park` 阻塞。
- **公平锁与非公平锁**：构造时可传入 `true` 创建公平锁。
  - **公平锁**：`tryAcquire()` 中先检查 CLH 队列是否有等待线程（`hasQueuedPredecessors()`），如果有则排队，保证先到先得。
  - **非公平锁**：直接 CAS 抢锁，失败才排队。新线程可能比等待队列中的线程先获取锁（存在"插队"）。

**Q: Handler 内存泄漏的原因是什么？如何处理？**

- **原因**：匿名内部类 Handler 持有外部类（Activity）的隐式引用 → Handler 被 Message 引用（`msg.target = handler`）→ Message 存在于 MessageQueue → MessageQueue 生命周期跟 Looper（主线程 Looper 永不停止）→ Activity 无法被回收。
- **引用链**：`Thread → Looper → MessageQueue → Message → Handler → Activity`
- **解决方案**：
  1. 使用 `static` 内部类 + 弱引用持有 Activity
  2. 在 `onDestroy()` 中调用 `removeCallbacksAndMessages(null)` 清除所有消息
  3. 两者结合使用

**Q: `Handler().post` 能否正确获取 View 的宽高？为什么？**

- **不能**（大多数情况下）。`post()` 将 Runnable 添加到 MessageQueue 末尾，但执行时机取决于 Looper 当前处理进度。如果 View 还没完成 measure/layout，获取的宽高为 0。
- 但如果 View 已经完成绘制后调用 `post()`，则可以正确获取。
- 正确获取方式：`View.post()` 在 View attach 到窗口后是可靠的（`ViewRootImpl` 会将 Runnable 加入 `RunQueue`，在 `performTraversals` 完成后执行）。

**Q: `onResume` 中能否直接获取 View 的宽高？为什么？**

- **不能**。`onResume` 时 View 树尚未完成 measure/layout/draw。`setContentView` 只是将 DecorView 添加到 Window，View 的测量和布局在 `onResume` 之后的 `performTraversals` 中才执行。
- 正确方式：`ViewTreeObserver.addOnGlobalLayoutListener`、`View.post()`、`onWindowFocusChanged()`。

---

### 2. View 与渲染

**Q: `invalidate()` 与 `requestLayout()` 的区别？连续调用多次分别会有什么后果？**

| 维度 | invalidate() | requestLayout() |
|------|-------------|-----------------|
| 触发流程 | 仅触发 draw 过程 | 触发 measure → layout → draw |
| 适用场景 | View 外观变化（颜色、文本等） | View 尺寸变化 |
| 线程要求 | 必须在主线程调用 | 必须在主线程调用 |
| 连续调用多次 | 会被合并，同一帧只绘制一次（通过 `mDirty` 区域合并） | 会触发多次 measure/layout，但 ViewRootImpl 会通过 `mLayoutRequested` 标记合并，最终在同一帧只执行一次 |

**Q: `invalidate()` 的实现原理？**

1. 调用 `View.invalidateInternal()`，计算需要重绘的区域（`mDirty` Rect）
2. 沿父链向上传递 `invalidateChild()`，将脏区域累加到父 View 的 `mDirty`
3. 最终传递到 `ViewRootImpl`，调用 `scheduleTraversals()`
4. 在下一帧 VSYNC 到来时执行 `performTraversals()` 中的 `performDraw()` → `draw()`

**Q: `requestLayout()` 的实现原理？**

1. 调用 `View.requestLayout()`，设置 `mPrivateFlags |= PFLAG_FORCE_LAYOUT`
2. 沿父链向上传递，直到 `ViewRootImpl`，设置 `mLayoutRequested = true`
3. 调用 `scheduleTraversals()`，在下一帧 VSYNC 执行 `performTraversals()` 完成 measure → layout → draw

**Q: `measuredWidth` 与 `width` 的区别？**

- `measuredWidth`：measure 阶段确定的测量宽度
- `width`（`getWidth()`）：layout 阶段确定的最终宽度，`width = right - left`
- 通常两者相等，但在某些情况下（如 layout 后又被修改）可能不同

**Q: `LayoutParams.width`、`measuredWidth`、`getWidth()` 三者分别代表什么？**

- `LayoutParams.width`：开发者设置的**期望宽度**，可以是具体值、`MATCH_PARENT`（-1）或 `WRAP_CONTENT`（-2）
- `measuredWidth`：measure 阶段根据 `LayoutParams` 和父容器约束计算出的**测量宽度**
- `getWidth()`：layout 阶段确定的**实际显示宽度**（`right - left`）

**Q: 打开一个 Activity，第一帧数据绘制的触发点在哪里？**

- `ActivityThread.handleLaunchActivity()` → `handleResumeActivity()` → `Activity.performResume()`
- 在 `onResume()` 之后，`ViewRootImpl` 被创建并调用 `setView()`（通过 `WindowManager.addView`）
- `setView()` 中调用 `scheduleTraversals()`，在下一个 VSYNC 信号到来时执行 `performTraversals()` 完成第一帧绘制
- 所以第一帧绘制的触发点是 `ViewRootImpl.scheduleTraversals()`

**Q: Android 屏幕刷新机制（VSYNC）是什么？**

- VSYNC（Vertical Synchronization）是硬件定时器产生的周期性信号，通常 16.6ms 一次（60fps）或 8.3ms（120fps）。
- `SurfaceFlinger` 负责合成所有图层并刷新到屏幕，`HWComposer` 提供硬件 VSYNC 信号。
- Android 使用**双重 VSYNC**：`VSYNC_APP`（应用帧开始）和 `VSYNC_DISPLAY`（显示刷新开始），配合 `Choreographer` 协调应用渲染与屏幕刷新。
- 从 Android 4.1 开始引入 **Project Butter**（三重缓冲），通过增加一个 buffer 减少掉帧。

**Q: `setContentView()` 的实现原理？**

1. `Activity.setContentView()` → `PhoneWindow.setContentView()`
2. 首次调用时创建 `DecorView`（`installDecor()`），设置 `contentParent`（`android.R.id.content` 的 FrameLayout）
3. 将传入的布局 inflate 到 `contentParent` 中
4. 回调 `Activity.onContentChanged()`，通知 `Activity` 内容已改变
5. 此时 `DecorView` 还没有被添加到 `WindowManager`，要等到 `handleResumeActivity()` 中 `WindowManager.addView(decorView)` 才真正显示

---

### 3. 内存泄漏

**Q: LeakCanary 的原理？**

1. **监控 Activity/Fragment 销毁**：通过 `Application.registerActivityLifecycleCallbacks` 监听 Activity 生命周期，在 `onDestroyed` 时将对象加入监控队列。
2. **弱引用检测**：将销毁的 Activity 包装为 `KeyedWeakReference`，关联 `ReferenceQueue`。5 秒后检查 `ReferenceQueue`，如果对象仍在其中（未被 GC），说明可能存在泄漏。
3. **触发 GC**：手动调用 `Runtime.gc()` 触发 GC。
4. **堆转储分析**：使用 `Debug.dumpHprofData()` 导出 hprof 文件，通过 `Shark` 库解析堆转储，找到 GC Root 到泄漏对象的最短引用链。
5. **展示结果**：将泄漏链路展示在通知中。

**Q: `SoftReference` 与 `WeakReference` 的区别？`ReferenceQueue` 的作用？能否覆盖所有对象？**

- **SoftReference**：内存不足时被回收，适合做缓存（如图片缓存）
- **WeakReference**：GC 时就会被回收，无论内存是否充足
- **ReferenceQueue**：当引用对象被 GC 回收时，Reference 对象会被加入关联的 ReferenceQueue。通过轮询该队列可以知道哪些对象已被回收。LeakCanary 利用它判断对象是否被 GC。
- **不能覆盖所有对象**：`Thread`、`System` 等系统类以及一些 JNI 层对象不会被弱引用回收。另外基本类型（int 等）不存在引用概念。

**Q: 如何设计一个内存泄漏监控系统？**

1. **对象监控**：在 Activity/Fragment 的 `onDestroy` 时将其包装为弱引用，关联 ReferenceQueue
2. **延迟检测**：延迟 5-10 秒后检查 ReferenceQueue，未回收则疑似泄漏
3. **触发 GC**：手动触发 GC 后再次检查
4. **堆转储分析**：导出 hprof，解析 GC Root 引用链
5. **上报与告警**：将泄漏信息上报到后台，附带引用链路
6. **黑名单机制**：过滤已知的系统泄漏（如 InputMethodManager）

**Q: 内存泄漏如何定位？**

1. **LeakCanary**：开发阶段自动检测
2. **MAT（Memory Analysis Tool）**：分析 hprof 文件，查看 GC Root 引用链
3. **Android Studio Profiler**：实时查看内存分配，对比 heap dump
4. **StrictMode**：检测 Activity/Fragment 泄漏（开发模式）

**Q: Handler 内存泄漏的完整引用链是什么？**

`Thread(main) → Looper → MessageQueue → Message → Handler(target) → Activity(外部类)`

---

### 4. 性能优化

**Q: 冷启动优化的方案有哪些？如何做冷启动监控？**

**优化方案**：
1. **Application 优化**：延迟初始化非必须组件，使用 IdleHandler 延迟执行
2. **首屏优化**：减少首屏布局层级，使用 ConstraintLayout，异步加载数据
3. **启动页优化**：使用 XML theme 作为启动窗口（`windowBackground`），避免白屏
4. **预加载**：使用 `AppStartup` 库或自定义 ContentProvider 并行初始化
5. **I/O 优化**：减少主线程 IO 操作，SharedPreferences 迁移到 MMKV
6. **类加载优化**：使用 Baseline Profile 预编译关键路径

**冷启动监控**：
- 使用 `ActivityTaskManager` 或 `Activity.start` 到 `onWindowFocusChanged` 的时间差
- 通过 `DisplayManager` 获取首帧时间
- ADB 命令：`adb shell am start -W` 获取 `TotalTime`
- 自定义方案：在 Application 构造时记录时间戳，在首帧 `onWindowFocusChanged` 时计算差值

**Q: 复杂页面（如交易页）的构建方案？**

1. **模块化设计**：将页面拆分为独立模块/组件，每个模块有自己的数据源和 UI
2. **异步加载**：数据请求并行化，UI 骨架屏先行渲染
3. **ViewStub 延迟加载**：非首屏模块使用 ViewStub 按需加载
4. **RecyclerView 多类型**：使用 `ConcatAdapter` 或 `Epoxy` 管理复杂列表
5. **数据预取**：进入页面前预加载数据
6. **渲染优化**：减少过度绘制，扁平化布局

**Q: 卡顿检测与优化的方案有哪些？**

1. **Systrace / Perfetto**：系统级性能分析，查看帧渲染耗时
2. **BlockCanary**：利用 Looper Printer 监控主线程消息执行时间，超过阈值（如 16ms）则记录堆栈
3. **Android GPU Inspector (AGI)**：GPU 渲染分析
4. **Choreographer.FrameCallback**：监控每帧耗时
5. **StrictMode**：检测主线程磁盘 IO 和网络操作
6. **自定义**：通过 `Looper.setMessageLogging()` 监控 `dispatchMessage` 耗时

**Q: 如何统计每个页面的渲染时间？请描述实现思路。**

1. **方案一**：在 `Activity.onCreate` 记录开始时间，在 `onWindowFocusChanged(true)` 记录结束时间，差值即为渲染时间
2. **方案二**：使用 `Choreographer.postFrameCallback`，在首帧回调中计算时间
3. **方案三**：使用 `ViewTreeObserver.addOnPreDrawListener`，在首帧 `onPreDraw` 时计算
4. **方案四**：字节码插桩，自动在 Activity 生命周期方法中插入计时代码

**Q: 启动页优化：XML 异步加载的实现方式？**

1. 使用 `windowBackground` 设置启动主题，避免白屏/黑屏
2. 使用 `ViewStub` 延迟加载非首屏 View
3. 使用 `AsyncLayoutInflater` 在子线程 inflate 布局
4. 使用 `LayoutInflater.Factory2` 拦截 View 创建，实现自定义逻辑（如换肤）
5. 使用 `MergeResource` 合并资源减少 IO

---

### 5. 其他

**Q: App 启动流程？冷启动与热启动的区别？**

**启动流程**：
1. Launcher 启动 Activity → `startActivity` → `Instrumentation.execStartActivity`
2. AMS 处理启动请求，检查进程是否存在
3. 若进程不存在：通过 Socket 通知 Zygote fork 新进程 → `ActivityThread.main()` → 创建主线程 Looper → 创建 Application → 调用 `Application.onCreate()` → 启动目标 Activity
4. 若进程存在：直接在已有进程中启动 Activity

**冷启动 vs 热启动**：
- **冷启动**：进程不存在，需要创建进程、初始化 Application，耗时最长
- **热启动**：进程已存在，只需创建 Activity，走 `onStart → onResume`
- **温启动**：进程存在但 Activity 被回收，需要重新创建 Activity

**Q: Application 是什么时候创建的？**

在 `ActivityThread.handleBindApplication()` 中：
1. 通过 `Instrumentation.newApplication()` 反射创建 Application 实例
2. 调用 `Application.attach()` → `ContentProvider` 初始化
3. 调用 `Application.onCreate()`
这发生在 Activity 启动之前，在进程创建之后。

**Q: Context 是什么时候产生的？**

- `Context` 在 `ActivityThread.performLaunchActivity()` 中创建：通过 `Instrumentation.newContext()` 创建 `ContextImpl` 实例
- `ContextImpl` 是 Context 的具体实现，封装了 `Application`、`Resources`、`PackageManager` 等
- `Application`、`Activity`、`Service` 都有自己的 `ContextImpl`，它们都是 `ContextWrapper` 的子类，内部持有 `ContextImpl`

**Q: Gradle 有几种依赖方式？各自的区别？**

| 方式 | 说明 |
|------|------|
| `implementation` | 编译和运行时可用，但不会传递给依赖方（推荐） |
| `api` | 编译和运行时可用，会传递给依赖方（等价于旧的 `compile`） |
| `compileOnly` | 仅编译时可用，运行时不包含（类似 `provided`） |
| `runtimeOnly` | 仅运行时可用，编译时不可见 |
| `annotationProcessor` | 注解处理器 |
| `kapt` | Kotlin 注解处理器 |

**Q: 字节码插桩的原理与应用？**

- **原理**：在编译后的 `.class` 文件或 `.dex` 文件中修改字节码。常用工具：
  - **ASM**：直接操作字节码指令，功能强大
  - **Javassist**：基于源码级 API 修改字节码，更易用
  - **Transform API**（AGP 7.0 前）/ **Instrumentation API**（AGP 7.0+）：Android Gradle 插件提供的插桩入口
- **应用**：
  - 方法耗时统计（自动在方法前后插入计时代码）
  - 崩溃监控（try-catch 注入）
  - 埋点（自动采集点击事件）
  - 权限监控
  - 内存泄漏检测

**Q: ANR 的分析、定位及解决方案？**

**分析定位**：
1. 查看 `/data/anr/traces.txt` 或 `bugreport` 中的 ANR 日志
2. 关注主线程堆栈，找到阻塞点（`blocked`、`waiting`、`sleeping`）
3. 常见原因：主线程 IO、锁竞争、Binder 调用超时、Service 超时（前台 20s，后台 200s）、BroadcastReceiver 超时（10s）

**解决方案**：
1. 避免主线程 IO（SP、数据库、文件）
2. 避免主线程大量计算
3. 避免主线程 Binder 调用耗时操作
4. 使用 `StrictMode` 检测主线程违规操作
5. 使用 `Looper Printer` 监控主线程消息耗时

**Q: 如何判断 so 库已经加载过？**

1. `System.loadLibrary()` 内部会检查是否已加载，重复加载不会报错（但 `System.load` 绝对路径重复加载会抛 `UnsatisfiedLinkError`）
2. 可以通过自定义标志位记录：在 `onLoad` 成功后设置标志
3. 通过检查类是否可用：`try { Class.forName("xxx") } catch`
4. 通过 `native` 方法是否存在来判断

**Q: SharedPreferences 有哪些缺陷？**

1. **全量写入**：每次 commit/apply 都将整个 XML 文件写入磁盘，数据量大时性能差
2. **ANR 风险**：`apply()` 会在主线程写入内存，写入磁盘在子线程，但 `commit()` 在主线程同步写磁盘，可能导致 ANR；`apply()` 的磁盘写入也会阻塞主线程的 `Activity.onPause/onStop`
3. **跨进程不安全**：多进程读写可能导致数据丢失
4. **内存占用**：加载时全量读入内存（Map 结构）
5. **无类型安全**：key-value 都是 Object，需要手动转型

---

## 二、Flutter

### 1. 渲染机制

**Q: Flutter Widget → Element → RenderObject 的渲染流程？**

1. **Widget**：不可变的 UI 配置描述（类似 XML 节点）
2. **Element**：Widget 的实例化对象，是 Widget 和 RenderObject 之间的桥梁，维护 Widget 树的结构
3. **RenderObject**：负责实际的布局（size）和绘制（paint），拥有 `RenderObject.parentData`、`paintBounds` 等

**流程**：
1. `Widget.createElement()` 创建 Element
2. `Element.mount()` 中调用 `widget.createRenderObject()` 创建 RenderObject
3. Element 树通过 `updateChild()` 方法进行 diff 更新：
   - Widget 类型相同且 key 相同 → 更新 Element（`update`）
   - key 不同 → 移除旧 Element，创建新 Element
4. RenderObject 通过 `PipelineOwner` 管理，依次执行：
   - **Layout**（`performLayout`）：计算尺寸和位置
   - **Paint**（`paint`）：绘制到 `Layer` 树
   - **Composite**：将 Layer 树提交给 Engine 渲染到屏幕

### 2. Dart 语言

**Q: Dart Future 的实现原理？（Dart 事件队列）**

- Dart 是单线程模型（主 Isolate），通过事件循环（Event Loop）驱动。
- 事件循环维护两个队列：
  - **Microtask Queue**（微任务队列）：优先级高，通过 `scheduleMicrotask()` 或 `Future.microtask()` 添加
  - **Event Queue**（事件队列）：优先级低，包含 I/O、定时器、UI 事件、`Future.then` 回调等
- Event Loop 每次先清空 Microtask Queue，再处理 Event Queue 中的一个事件。
- `Future` 代表一个异步操作的最终结果。`async/await` 是 Future 的语法糖，编译器会将其转换为状态机。

**Q: Dart 单线程模型的特点？**

- Dart 主线程（main Isolate）是单线程的，同一时刻只执行一个任务
- 通过 Event Loop 实现并发，异步操作不会阻塞主线程
- 没有锁竞争问题（同一 Isolate 内），代码更简单
- 计算密集型任务会阻塞 UI，需要使用其他 Isolate

**Q: Dart Isolate 与线程的区别？多个 Isolate 之间如何通信？**

- **Isolate** 拥有独立的内存堆（heap），不共享内存；**线程** 共享内存空间
- Isolate 之间不能直接访问对方变量，通过消息传递通信
- 通信方式：
  1. **SendPort / ReceivePort**：基础消息传递
  2. **compute()**：简单的一次性后台任务
  3. **Isolate.spawn()**：创建新 Isolate
  4. **Platform Channel**：Flutter 与原生通信（MethodChannel、EventChannel、BasicMessageChannel）

**Q: StatefulWidget 的生命周期？**

```
createState() → initState() → didChangeDependencies() → build()
→ didUpdateWidget() → build()（Widget 更新时）
→ deactivate() → dispose()（Widget 移除时）
```

- `initState()`：只调用一次，初始化操作
- `didChangeDependencies()`：依赖的 InheritedWidget 变化时调用
- `build()`：构建 UI
- `didUpdateWidget()`：父 Widget 重建导致更新时调用
- `deactivate()`：从树中移除时调用
- `dispose()`：永久移除时调用，释放资源

**Q: Flutter 热更新的实现原理？**

- **Hot Reload**（热重载）：
  1. 修改 Dart 代码后，将更新后的源码文件推送到设备
  2. Dart VM 替换旧的 Widget 类定义
  3. 触发 Widget 树重建（从根开始 rebuild）
  4. State 对象被保留，不会丢失状态
  5. 通过 `kernel` 快照增量更新，速度快（亚秒级）

- **Hot Restart**（热重启）：
  1. 重置所有状态，重新初始化
  2. 重新运行 `main()` 函数

---

## 三、综合 & 其他

### 1. AI 辅助开发

**Q: AI 开发工具的完整流程？（SuperPower 的流程）**

1. 需求分析：理解用户需求，拆解为具体任务
2. 代码生成：AI 根据上下文生成代码
3. 代码审查：AI Code Review 检查代码质量
4. 测试生成：自动生成单元测试
5. 持续集成：集成到 CI/CD 流程
6. 反馈迭代：根据开发者反馈优化

**Q: Skill 读取项目规范的方式？如何保证 UI 还原度？**

- Skill 通过读取项目中的规范文件（如 `.cursorrules`、`CONVENTIONS.md`）获取编码规范和架构约定
- UI 还原度保证：
  1. 提供设计稿截图作为上下文
  2. 使用 Design Token 统一设计规范
  3. 像素级对比工具检测差异
  4. 组件库标准化

**Q: AI Code Review 的设计方案？**

1. **触发机制**：PR 创建或更新时自动触发
2. **上下文收集**：收集 diff、相关文件、项目规范
3. **AI 分析**：检查代码风格、潜在 bug、性能问题、安全漏洞
4. **结果输出**：以评论形式反馈到 PR
5. **学习迭代**：根据开发者反馈调整规则

**Q: Session 压缩与跨 Session 开发的实现？**

- **Session 压缩**：将长对话历史压缩为摘要，保留关键上下文，减少 Token 消耗
- **跨 Session**：将项目状态、决策记录持久化到 Memory 系统，新 Session 启动时加载历史上下文

**Q: Token 消耗与上下文管理？**

- 上下文窗口有限（如 128K），需要管理输入输出 Token
- 策略：对话历史截断、摘要压缩、RAG（检索增强生成）、分层上下文（系统提示 + 知识 + 对话）

**Q: Harness 与 Agent 的概念与应用？**

- **Harness**：运行环境和工具集，提供文件操作、终端、搜索等能力
- **Agent**：具备自主决策能力的 AI 实体，根据目标自动规划、执行、验证任务
- 应用：Agent 接收任务 → 分析代码库 → 制定计划 → 使用 Harness 提供的工具执行修改 → 验证结果

**Q: AI Chat 如何实现类似打字机的响应效果？与 WebSocket 的区别？**

- **打字机效果**：服务端使用 SSE（Server-Sent Events）流式返回 Token，客户端逐字渲染
- **与 WebSocket 区别**：
  - SSE：单向（服务端→客户端），基于 HTTP，自动重连，适合 AI 响应
  - WebSocket：双向通信，独立协议，适合实时交互场景

**Q: Skill 与 MCP 的区别？**

- **Skill**：预定义的指令模板，教 AI 如何完成特定任务（如代码审查、提交规范）
- **MCP（Model Context Protocol）**：标准化协议，让 AI 模型连接外部工具和数据源（如数据库、API）

### 2. 业务相关

**Q: 交易下单流程？**

1. 用户选择交易对、方向（买/卖）、价格、数量
2. 客户端参数校验 → 提交订单
3. 风控系统检查（余额、限额、频率限制）
4. 订单进入撮合引擎
5. 撮合引擎按价格优先、时间优先原则匹配
6. 成交后更新持仓、余额
7. 通过 WebSocket 推送订单状态和成交信息

**Q: OrderBook 合并逻辑？**

- OrderBook 按价格档位维护买卖盘口
- 合并逻辑：相同价格的订单合并为一档，显示总量
- 价格精度处理：按最小价格步进取整
- 实时更新：通过 WebSocket 增量更新（`diff` 模式），本地维护完整 OrderBook

**Q: 链上充值？**

1. 用户获取充值地址（交易所分配或用户钱包）
2. 用户从外部钱包转账到充值地址
3. 链上节点确认交易（等待区块确认数）
4. 后端监听链上事件，检测到充值交易
5. 达到确认数后，更新用户账户余额
6. 通知用户充值成功

**Q: 仓位卡顿优化？**

1. 数据层面：WebSocket 增量更新代替全量推送，减少数据量
2. 渲染层面：使用 `shouldRebuild` / `const` Widget 减少不必要的重建
3. 计算层面：持仓计算（盈亏、杠杆）放到后台 Isolate
4. 列表优化：使用 `ListView.builder` 懒加载，避免一次性渲染所有仓位

### 3. Git

**Q: Git 中如何将多个 commit 合并为一个？**

1. **Interactive Rebase**：`git rebase -i HEAD~n`，将 `pick` 改为 `squash` 或 `fixup`
2. **Soft Reset**：`git reset --soft HEAD~n`，然后重新 `git commit`
3. **Merge --squash**：`git merge --squash feature-branch`，将分支的所有提交合并为一个

**Q: `git merge` 与 `git rebase` 的区别？**

| 维度 | git merge | git rebase |
|------|-----------|------------|
| 历史记录 | 保留完整历史，产生合并提交 | 线性历史，无合并提交 |
| 安全性 | 不修改已有提交 | 重写提交历史（改变 commit hash） |
| 适用场景 | 公共分支合并 | 个人分支整理提交 |
| 冲突处理 | 一次性解决 | 可能需要逐个 commit 解决 |

### 4. Java / Kotlin 基础

**Q: Java 中有哪些锁？各自的区别？**

| 锁类型 | 实现 | 特点 |
|--------|------|------|
| `synchronized` | JVM 内置（monitorenter/monitorexit） | 自动释放，不可中断，非公平 |
| `ReentrantLock` | AQS | 可中断、可超时、公平/非公平、支持多 Condition |
| `ReentrantReadWriteLock` | AQS | 读锁共享、写锁独占 |
| `StampedLock` | AQS（Java 8） | 乐观读锁，性能更高 |
| `LockSupport.park/unpark` | 底层原语 | 线程级别阻塞/唤醒 |

**Q: `synchronized` 的锁升级过程？**

JDK 6 引入锁优化，对象头 Mark Word 状态变化：
1. **无锁**：初始状态
2. **偏向锁**：第一个线程获取锁，Mark Word 记录线程 ID。后续该线程进入无需 CAS，直接比较线程 ID
3. **轻量级锁**：第二个线程竞争时，撤销偏向锁，升级为轻量级锁。Mark Word 指向栈上的锁记录（Lock Record），通过 CAS 操作
4. **重量级锁**：轻量级锁竞争失败（CAS 自旋超过阈值），升级为重量级锁。Mark Word 指向 Monitor 对象，未获取锁的线程阻塞（OS 级别）

**Q: CAS 的原理？（底层汇编 `lock cmpxchg`）**

- CAS（Compare And Swap）：比较并交换，包含三个操作数：内存地址 V、预期值 A、新值 B
- 当 V == A 时，将 V 更新为 B，否则不做操作
- 底层通过 CPU 指令 `lock cmpxchg` 实现，`lock` 前缀锁住总线/缓存行，保证原子性
- 问题：ABA 问题（通过 `AtomicStampedReference` 解决）、自旋开销

**Q: Kotlin / Java 中有哪些单例写法？**

**Java**：
1. 饿汉式：`static final` 直接初始化
2. 懒汉式：`synchronized` 方法
3. 双重检查锁（DCL）：`volatile` + `synchronized`
4. 静态内部类：利用类加载机制
5. 枚举：`enum` 天然单例

**Kotlin**：
1. `object` 关键字
2. `companion object`
3. `by lazy` 委托

**Q: 类的加载时机有哪些？`static` 单例为何能保证线程安全？**

**加载时机**：
1. 创建对象实例时（`new`）
2. 访问静态成员时
3. 创建子类时（父类先加载）
4. 反射调用时
5. JVM 启动时（主类）

**static 单例线程安全原因**：JVM 在类加载阶段会加锁（`Class` 对象的锁），保证类只被初始化一次。静态内部类在首次被引用时才加载，由 JVM 保证线程安全。

**Q: `object` 关键字的原理？是否线程安全？**

- `object` 编译后生成一个 `final` 类，包含一个 `static final` 的 `INSTANCE` 字段
- 在 `<clinit>`（静态初始化块）中创建实例
- **线程安全**：JVM 保证 `<clinit>` 只被执行一次，且在多线程环境下会同步执行

**Q: `by lazy` 的原理？是否线程安全？**

- `by lazy` 编译后生成一个 `Lazy<T>` 对象，默认实现为 `SynchronizedLazyImpl`
- 内部使用 `lazyValue` 和 `lock` 对象，通过 `synchronized(lock)` 保证线程安全
- 默认模式 `LazyThreadSafetyMode.SYNCHRONIZED` 是线程安全的
- `LazyThreadSafetyMode.PUBLICATION` 使用 CAS
- `LazyThreadSafetyMode.NONE` 无线程安全保证

**Q: Kotlin 扩展函数的实现原理？**

- 扩展函数编译后是**静态方法**，第一个参数是接收者对象
- 例如 `fun String.hello()` 编译为 `static final void hello(String $this$hello)`
- 没有修改原始类的字节码，只是语法糖
- 因此扩展函数不能访问私有成员，也不能被重写（除非是 `open` 类的 `open` 方法）

**Q: Kotlin 默认参数的实现原理？对应的 Java 字节码是什么样的？**

- 编译器为带默认参数的方法生成一个**重载方法**，参数末尾增加一个 `int` 掩码（mask）参数
- 掩码的每一位表示对应参数是否使用默认值
- 同时生成一个桥接方法（`$default` 后缀），Java 调用时通过桥接方法传入掩码 0（全部使用传入值）
- 字节码示例：`fun foo(a: Int, b: Int = 10)` 生成 `foo(int, int)` 和 `foo$default(Foo, int, int, int, Object)`

**Q: Kotlin 接口默认方法的实现原理？对应的 Java 字节码是什么样的？**

- Kotlin 接口中的默认方法编译为接口中的 `static` 方法 + 一个 `DefaultImpls` 内部类
- Java 8+ 也可以直接在接口中使用 `default` 关键字
- 字节码：接口中有一个 `static` 方法实现默认逻辑，实现类中调用 `InterfaceName.DefaultImpls.methodName(this)`

**Q: Kotlin `by` 委托机制的原理？**

- `by` 关键字将属性的 getter/setter 委托给另一个对象
- 委托对象需实现 `ReadWriteProperty<This, Type>` 接口（或 `ReadOnlyProperty`）
- 编译后：属性字段被移除，getter 调用 `delegate.getValue(this, property)`，setter 调用 `delegate.setValue(this, property, value)`
- 常用委托：`lazy`、`observable`、`vetoable`、`Map` 委托

**Q: 静态代理与动态代理的具体实现方式？**

**静态代理**：
```kotlin
interface Service { fun doSomething() }
class RealService : Service { override fun doSomething() { ... } }
class ProxyService(private val real: Service) : Service {
    override fun doSomething() {
        // 前置处理
        real.doSomething()
        // 后置处理
    }
}
```

**动态代理（Java）**：
```java
Proxy.newProxyInstance(classLoader, interfaces, (proxy, method, args) -> {
    // 前置处理
    Object result = method.invoke(target, args);
    // 后置处理
    return result;
});
```

**Q: 如何手写一个 Kotlin 单例？对应的 Java 字节码是什么样的？**

```kotlin
object Singleton {
    fun doSomething() {}
}
```

字节码等价于：
```java
public final class Singleton {
    public static final Singleton INSTANCE = new Singleton();
    static { } // <clinit>
    private Singleton() {}
    public final void doSomething() {}
}
```

**Q: 如何写一个线程级别的单例？原理是什么？**

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

原理：每个线程有独立的 `ThreadLocalMap`，`ThreadLocal` 作为 key 存储线程私有的对象实例，不同线程获取的是各自独立的实例。

**Q: 内存模型：堆、栈、程序计数器、方法区、常量池分别是什么？**

| 区域 | 说明 |
|------|------|
| **堆（Heap）** | 存放对象实例，GC 管理的主要区域，所有线程共享 |
| **栈（VM Stack）** | 存放局部变量表、操作数栈、方法出口等，线程私有，方法结束自动释放 |
| **程序计数器（PC Register）** | 记录当前线程执行的字节码行号，线程私有 |
| **方法区（Method Area）** | 存储类信息、常量、静态变量、JIT 编译后的代码，线程共享 |
| **常量池（Runtime Constant Pool）** | 方法区的一部分，存储编译期生成的字面量和符号引用 |

**Q: 全局变量与函数变量分别分配在哪个内存区域？**

- **全局变量（静态变量）**：方法区（JDK 7 后移到堆中）
- **函数内局部变量**：虚拟机栈（基本类型直接在栈上，对象引用在栈上、实例在堆上）
- **函数内的匿名内部类/Lambda 捕获的变量**：如果在堆中创建对象，则对象在堆上

### 5. 线程安全

**Q: 实现线程安全的方式有哪些？**

1. **互斥同步**：`synchronized`、`ReentrantLock`
2. **非阻塞同步**：CAS（`AtomicInteger`、`AtomicReference`）
3. **无同步方案**：
   - 线程本地存储（`ThreadLocal`）
   - 不可变对象（`final`、`String`）
   - 栈封闭（局部变量天然线程安全）
4. **并发工具**：`ConcurrentHashMap`、`CopyOnWriteArrayList`、`BlockingQueue`

**Q: 线程安全的 Map、List 有哪些？各自的实现方式？**

**Map**：
| 类 | 实现方式 |
|----|---------|
| `Hashtable` | 全表锁（`synchronized`），性能差 |
| `Collections.synchronizedMap` | 包装器，全表锁 |
| `ConcurrentHashMap` | 分段锁（JDK 7）/ CAS + `synchronized`（JDK 8），高并发 |

**List**：
| 类 | 实现方式 |
|----|---------|
| `Vector` | 全表锁 |
| `Collections.synchronizedList` | 包装器，全表锁 |
| `CopyOnWriteArrayList` | 写时复制，读无锁，适合读多写少 |

**Q: `volatile` 的作用？**

1. **可见性**：一个线程修改 `volatile` 变量后，其他线程立即可见（通过内存屏障禁止 CPU 缓存优化）
2. **有序性**：禁止指令重排序（通过插入内存屏障）
3. **不保证原子性**：`volatile int count; count++` 不是原子操作

**Q: `ConcurrentHashMap` 的原理？`get` 方法如何实现多线程安全访问？**

**JDK 7**：分段锁，`Segment[]` 数组，每个 Segment 是一个 `ReentrantLock`，锁住一个段内的 `HashEntry[]`

**JDK 8**：
- 数据结构：`Node[]` 数组 + 链表 + 红黑树（链表长度 > 8 且数组长度 >= 64 时转红黑树）
- `put`：对桶头节点使用 `synchronized`，空桶使用 CAS 插入
- `get`：**无锁**。Node 的 `val` 和 `next` 字段都是 `volatile` 的，保证可见性。`get` 直接读 `volatile` 变量，不需要加锁。因为 `put` 时先赋值 `val`（`volatile` 写），再设置 `next`（`volatile` 写），保证了 happens-before 关系

**Q: 多线程资源竞争如何解决？**

1. **避免竞争**：使用线程本地存储（`ThreadLocal`）、不可变对象
2. **互斥锁**：`synchronized`、`ReentrantLock`
3. **CAS 乐观锁**：`Atomic` 类，自旋重试
4. **并发数据结构**：`ConcurrentHashMap`、`BlockingQueue`
5. **信号量**：`Semaphore` 控制并发访问数量

### 6. 网络

**Q: 为何要做 HttpDNS？如何实现？**

- **原因**：传统 DNS 基于 UDP，存在 LocalDNS 劫持、解析慢、TTL 不准确等问题
- **实现**：
  1. 通过 HTTP/HTTPS 向 HttpDNS 服务器请求域名对应的 IP
  2. 客户端缓存 IP 及 TTL
  3. 在 OkHttp 中通过自定义 `Dns` 接口替换系统 DNS 解析
  4. 将域名替换为 IP 直连

**Q: 在 Interceptor 中把 baseURL 从域名直接替换成 IP 直连会有什么问题？**

1. **HTTPS 证书校验失败**：证书是颁发给域名的，IP 直连时 SNI 不匹配，证书校验会失败
2. **Cookie 问题**：Cookie 基于域名设置，IP 直连无法匹配
3. **CDN 失效**：无法利用 CDN 就近调度
4. **解决方案**：自定义 `SSLSocketFactory` 设置 hostname 为原始域名；自定义 CookieJar 处理

**Q: IP 择优策略是什么？HttpDNS 服务挂了怎么办？**

- **IP 择优**：HttpDNS 返回多个 IP，客户端通过延迟测试（ping/TCP connect）选择最优 IP；或使用 IP 轮询 + 失败切换
- **降级策略**：HttpDNS 失败时回退到 LocalDNS；缓存上次解析结果，过期后仍可使用

**Q: `Dns.lookup()` 的调用时机？`ConnectionPool` 复用连接可以节省哪部分时间？**

- `Dns.lookup()` 在 OkHttp 建立连接时调用（`RealConnection.connect()`），在 DNS 解析阶段
- `ConnectionPool` 复用连接可以节省：TCP 三次握手 + TLS 握手（HTTPS）的时间

**Q: OkHttp 会自动重试哪些请求？如何避免重复下单？**

- 自动重试：`retryOnConnectionFailure(true)` 时，对连接失败（IOException）、路由失败自动重试
- 仅对**幂等请求**安全。POST 请求默认也会重试
- **避免重复下单**：
  1. 服务端做幂等性校验（唯一订单号）
  2. 客户端对下单请求设置 `retryOnConnectionFailure(false)`
  3. 使用 `Cache-Control: no-cache` 避免缓存干扰

**Q: HTTP/1.1 下 OkHttp 默认配置，同时有 100 个相同域名的请求，最终会有几条连接？HTTP/2 下又如何？**

- **HTTP/1.1**：`ConnectionPool` 默认 `maxIdleConnections = 5`，`keepAliveDuration = 5min`。但活跃连接不受此限制。100 个并发请求会建立 **100 条连接**（每个请求一条），完成后只保留 5 条空闲连接
- **HTTP/2**：支持多路复用，同一域名只需 **1 条连接**，100 个请求通过这条连接的多个 Stream 并发传输

**Q: WebView 为什么无法直接复用原生网络层？Native 代发如何实现？是否会绕过 CORS？什么是跨域？**

- **无法复用原因**：WebView 有自己的网络栈（Chromium），与原生 OkHttp 网络栈隔离
- **Native 代发**：通过 `shouldInterceptRequest()` 拦截请求，用原生 OkHttp 发起请求，将响应流返回给 WebView
- **CORS**：跨域资源共享，浏览器安全策略。Native 代发**会绕过 CORS**，因为请求由原生发出，不受浏览器同源策略限制
- **跨域**：协议、域名、端口任一不同即为跨域，浏览器限制跨域请求

**Q: HTTPS 的交互过程？**

1. **TCP 三次握手**
2. **TLS 握手**：
   - Client Hello：支持的加密套件、随机数 Client Random
   - Server Hello：选定加密套件、随机数 Server Random、发送证书
   - 客户端验证证书（CA 链校验）
   - 客户端生成 Pre-Master Secret，用服务器公钥加密发送
   - 双方根据三个随机数生成会话密钥（对称加密）
3. **加密通信**：使用对称加密（如 AES-GCM）传输数据
4. **断开连接**：发送 close_notify

**Q: HTTP 协议如何实现缓存？有哪些缓存相关的 Header？**

| Header | 说明 |
|--------|------|
| `Cache-Control` | 缓存策略：`max-age`（秒）、`no-cache`（需验证）、`no-store`（不缓存）、`public`/`private` |
| `Expires` | 过期时间（HTTP 日期格式），优先级低于 `Cache-Control` |
| `Last-Modified` | 资源最后修改时间 |
| `ETag` | 资源内容哈希值 |
| `If-Modified-Since` | 请求头，配合 `Last-Modified` 条件请求 |
| `If-None-Match` | 请求头，配合 `ETag` 条件请求 |

**缓存流程**：
1. 强缓存：检查 `Cache-Control` / `Expires`，未过期直接用缓存（200 from cache）
2. 协商缓存：过期后发送条件请求，带 `If-None-Match`（ETag）或 `If-Modified-Since`
3. 服务器返回 304（未修改）或 200（新内容）

**Q: WebSocket 的稳定性如何保障？**

1. **心跳机制**：定时发送 Ping/Pong 帧检测连接存活
2. **断线重连**：指数退避重连策略
3. **消息确认**：应用层 ACK 机制保证消息送达
4. **多链路备份**：WebSocket + 长轮询降级

**Q: WebSocket 高频消息如何优化？如何防止 buffer 内存膨胀？**

1. **消息合并**：短时间内多次更新合并为一次推送
2. **节流/限流**：控制消息发送频率
3. **背压机制**：消费端处理不过来时通知发送端降速
4. **buffer 管理**：设置最大 buffer 大小，超出时丢弃旧消息或断开重连
5. **二进制协议**：使用 Protobuf 等紧凑格式减少数据量

**Q: 提升 WebSocket 订阅逻辑健壮性需要考虑哪些方面？**

1. 连接状态检查：订阅前确认连接已建立
2. 重连后重新订阅：断线重连后自动恢复订阅列表
3. 消息去重：防止重复推送
4. 异常处理：解析失败不影响后续消息处理
5. 超时机制：订阅响应超时处理
6. 资源释放：页面销毁时取消订阅、关闭连接

**Q: 流量优化与最优链路选择？**

1. **协议优化**：HTTP/2 多路复用、WebSocket 长连接
2. **数据压缩**：Gzip、Protobuf
3. **CDN 加速**：静态资源 CDN
4. **IP 择优**：HttpDNS + 延迟测试选择最优服务器
5. **连接复用**：OkHttp ConnectionPool
6. **请求合并**：批量请求减少网络次数

### 7. 抓包

**Q: Charles 抓包原理？**

- Charles 作为**中间人代理**（MITM），在手机和服务器之间充当代理
- 手机配置 Charles 的代理地址和端口
- Charles 生成自签名 CA 证书，手机安装并信任
- 请求流程：手机 → Charles（解密）→ 服务器；服务器 → Charles（加密）→ 手机
- Charles 可以同时查看和修改请求/响应内容

**Q: AI 抓包工具的实现？**

1. 基于 MITM 代理捕获网络流量
2. 解析 HTTP/HTTPS 请求和响应
3. 将抓包数据格式化后发送给 AI 分析
4. AI 根据请求/响应内容提供调试建议

### 8. 架构设计

**Q: MVP 与 MVVM 的区别？**

| 维度 | MVP | MVVM |
|------|-----|------|
| View 与 Model | Presenter 持有 View 接口，手动更新 UI | ViewModel 暴露数据，View 通过观察者自动更新 |
| 数据绑定 | 手动绑定 | 数据驱动（LiveData/DataBinding） |
| 生命周期 | Presenter 不感知生命周期 | ViewModel 感知生命周期 |
| 测试性 | 好（View 接口隔离） | 好（ViewModel 独立） |
| 适用场景 | 简单项目 | 复杂项目，推荐 |

**Q: MVVM 与 MVI 的区别？**

| 维度 | MVVM | MVI |
|------|------|-----|
| 数据流 | 双向/多向 | 单向（Intent → Model → View） |
| 状态管理 | 多个 LiveData 分别管理 | 单一 State 对象管理所有状态 |
| 状态一致性 | 可能出现多状态不一致 | 状态唯一，不会不一致 |
| 复杂度 | 适中 | 状态多时 Intent 爆炸 |

**Q: Clean Architecture 的优缺点？适用场景？**

**优点**：
1. 职责清晰，分层明确（Presentation → Domain → Data）
2. 依赖倒置，核心业务不依赖框架
3. 易于测试（每层可独立 Mock）
4. 可替换性强（换数据库、换网络库不影响业务）

**缺点**：
1. 类/文件数量多，代码量大
2. 简单功能也需要多层传递，开发效率低
3. 学习成本高

**适用场景**：大型项目、团队协作、需要长期维护的项目

**Q: EventBus 的原理？如何实现 `postSticky` 粘性事件？**

- **原理**：基于观察者模式 + 反射。`register()` 时通过反射扫描订阅者的方法注解（`@Subscribe`），建立事件类型到订阅方法的映射。`post()` 时查找对应事件类型的订阅方法并调用。
- **postSticky**：将事件存储在内存 Map 中（`stickyEvents`）。新订阅者 `register()` 时检查 `stickyEvents`，如果有匹配的粘性事件，立即分发给该订阅者。

### 9. 序列化

**Q: `Serializable` 与 `Parcelable` 的区别？`Parcelable` 如何实现本地序列化？**

| 维度 | Serializable | Parcelable |
|------|-------------|------------|
| 来源 | Java 标准库 | Android 特有 |
| 实现方式 | 标记接口，反射序列化 | 实现 `writeToParcel` / `CREATOR` |
| 性能 | 慢（反射、临时对象多、GC 压力大） | 快（直接内存操作） |
| 使用场景 | 磁盘存储、网络传输 | 内存传递（Intent、Bundle） |

**Parcelable 本地序列化**：
- Parcelable 本身不支持持久化（`Parcel` 是内存数据结构，不同进程/重启后格式可能变化）
- 如需本地持久化，可将 Parcelable 对象先转为 JSON/ProtoBuf 再存储
- 或者使用 `Parcel.obtain()` 写入 `byte[]` 后存储（不推荐，格式不稳定）

---

## 四、算法（低频 ⭐）

### 1. 两数之和

**LeetCode #1**

```kotlin
fun twoSum(nums: IntArray, target: Int): IntArray {
    val map = HashMap<Int, Int>() // value -> index
    for (i in nums.indices) {
        val complement = target - nums[i]
        map[complement]?.let { return intArrayOf(it, i) }
        map[nums[i]] = i
    }
    return intArrayOf()
}
```

时间复杂度 O(n)，空间复杂度 O(n)。

### 2. 最长公共前缀

**LeetCode #14**

```kotlin
fun longestCommonPrefix(strs: Array<String>): String {
    if (strs.isEmpty()) return ""
    var prefix = strs[0]
    for (i in 1 until strs.size) {
        while (!strs[i].startsWith(prefix)) {
            prefix = prefix.substring(0, prefix.length - 1)
            if (prefix.isEmpty()) return ""
        }
    }
    return prefix
}
```

时间复杂度 O(S)，S 为所有字符总数。

### 3. 字符串倒序打印

```kotlin
fun printReverse(str: String) {
    for (i in str.length - 1 downTo 0) {
        println(str[i])
    }
}
```

### 4. 路径总和 II

**LeetCode #113**

```kotlin
class TreeNode(val `val`: Int, var left: TreeNode? = null, var right: TreeNode? = null)

fun pathSum(root: TreeNode?, targetSum: Int): List<List<Int>> {
    val result = mutableListOf<List<Int>>()
    dfs(root, targetSum, mutableListOf(), result)
    return result
}

private fun dfs(node: TreeNode?, remaining: Int, path: MutableList<Int>, result: MutableList<List<Int>>) {
    if (node == null) return
    path.add(node.`val`)
    if (node.left == null && node.right == null && remaining == node.`val`) {
        result.add(ArrayList(path))
    } else {
        dfs(node.left, remaining - node.`val`, path, result)
        dfs(node.right, remaining - node.`val`, path, result)
    }
    path.removeAt(path.size - 1)
}
```

### 5. 字母异位词分组

**LeetCode #49**

```kotlin
fun groupAnagrams(strs: Array<String>): List<List<String>> {
    val map = HashMap<String, MutableList<String>>()
    for (str in strs) {
        val key = String(str.toCharArray().also { it.sort() })
        map.getOrPut(key) { mutableListOf() }.add(str)
    }
    return map.values.toList()
}
```

时间复杂度 O(N * K log K)，N 为字符串数量，K 为最长字符串长度。
