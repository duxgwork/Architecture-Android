# BN面试大全

> 整合 BN面试准备大纲 + Boss直聘JD要求，去重合并，涵盖所有面试题及答案。

---

## 一、Android 原生

### 1. 语言基础

#### 请谈谈你对 Java/Kotlin 语言特性的理解？Kotlin 相比 Java 有哪些核心优势？

**Java 语言特性：**
- 面向对象：支持封装、继承、多态，一切皆对象（基本类型除外）
- 跨平台：JVM 字节码编译，一次编写到处运行
- 泛型：编译期类型检查，但存在类型擦除
- 异常处理：checked exception 和 unchecked exception
- 并发模型：基于线程的并发，synchronized、ReentrantLock 等

**Kotlin 语言特性：**
- 空安全：通过 `?`、`?.`、`?:`、`!!` 在编译期处理空指针问题
- 扩展函数/属性：无需继承即可为类添加功能
- 高阶函数与 Lambda：函数是一等公民，支持闭包
- 数据类（data class）：自动生成 equals/hashCode/toString/copy（浅拷贝，对于引用类型字段，如MutableList，拷贝的只是引用，而非深拷贝对象本身）
- 密封类（sealed class）：限制继承层次，配合 when 实现穷举
- 委托属性（by lazy, by viewModels 等）
- 协程：结构化并发，轻量级异步编程

**Kotlin 核心优势：**
1. **空安全**：编译期空检查，大幅减少 NPE
2. **简洁性**：类型推断、数据类、扩展函数减少样板代码
3. **协程支持**：原生协程支持，异步代码同步写法
4. **互操作性**：100% 兼容 Java，可渐进式迁移
5. **函数式编程**：支持高阶函数、Lambda、集合操作符
6. **Android 官方推荐**：Google 首推 Kotlin 作为 Android 开发语言

---

#### 你是如何理解 Kotlin 协程原理的？协程与线程的关系是什么？

**协程原理：**

协程本质是**编译器层面的状态机**，通过将挂起函数拆分为多个状态段来实现"暂停"和"恢复"。

1. **CPS 变换（Continuation Passing Style）**：编译器将 `suspend` 函数转换为带有 `Continuation` 参数的普通函数，每次 `suspend` 点就是一个状态分界
2. **状态机**：编译器为每个挂起函数生成状态机，通过 `label` 标记当前执行到哪个挂起点，恢复时跳转到对应位置继续执行
3. **调度器（Dispatcher）**：负责决定协程在哪个线程执行，如 `Dispatchers.Main`、`Dispatchers.IO`、`Dispatchers.Default`
4. **作用域（CoroutineScope）**：管理协程的生命周期，支持结构化并发，父协程取消时子协程自动取消

**协程与线程的关系：**
- 协程**不等于**线程，协程是运行在线程之上的轻量级任务
- 协程挂起时**不阻塞线程**，只是将后续逻辑挂到回调中
- 一个线程可以同时运行多个协程（协作式调度），协程在不同线程间切换执行
- `Dispatchers.Default` 使用线程池（默认 CPU 核心数），`Dispatchers.IO` 使用最多 64 个线程的线程池
- 协程的挂起/恢复是零开销抽象（编译期完成，运行时无额外对象分配）

---

#### 请说说 Kotlin 协程的启动模式（CoroutineStart）有哪些？

| 启动模式 | 行为 |
|---------|------|
| `DEFAULT` | 立即按照调度器调度执行 |
| `LAZY` | 惰性启动，直到首次 `await()` 或 `start()` 时才调度 |
| `ATOMIC` | 原子启动，协程体不可被取消（但内部挂起点仍可取消） |
| `UNDISPATCHED` | 在当前线程立即执行，直到遇到第一个挂起点后才按调度器切换 |

---

#### 线程池的配置策略和使用场景？ThreadPoolExecutor 的核心参数如何设置？

**核心参数：**
1. `corePoolSize`：核心线程数
2. `maximumPoolSize`：最大线程数
3. `keepAliveTime` + `unit`：非核心线程空闲存活时间
4. `workQueue`：任务等待队列
5. `threadFactory`：线程工厂
6. `handler`：拒绝策略

**配置策略：**
- **CPU 密集型**：corePoolSize = CPU 核心数 + 1
- **IO 密集型**：corePoolSize = CPU 核心数 * 2
- **混合型**：`线程数 = CPU 核心数 * (1 + 等待时间/计算时间)`

---

### 2. 内存模型与并发

#### Android Framework 的内存模型是怎样的？volatile、synchronized、CAS 在并发场景中的应用？

**Android 内存模型：**
- Android 基于 Linux 内核，每个进程有独立的虚拟地址空间
- ART 使用分代 GC（Concurrent Copying Collector）
- JMM：主内存（堆，所有线程共享）+ 工作内存（CPU 缓存/寄存器，线程私有）

**volatile：** 保证可见性（读写直接操作主内存）+ 有序性（内存屏障禁止重排序），不保证原子性

**synchronized：** 保证原子性和可见性，通过 Monitor 实现，ART 优化：偏向锁 → 轻量级锁 → 重量级锁

**CAS：** 无锁原子操作，CPU 指令 `lock cmpxchg`，存在 ABA 问题（用 `AtomicStampedReference` 解决）

---

#### Android 中的多线程模型是怎样的？主线程为什么不能执行耗时操作？

- **主线程（UI 线程）**：负责 UI 渲染、事件处理、生命周期回调
- **工作线程**：通过 Thread、HandlerThread、IntentService、ExecutorService、协程等创建
- **线程间通信**：通过 Handler 消息机制将结果切回主线程更新 UI

**不能耗时的原因：** ANR（输入超时 5s、BroadcastReceiver 10s）、UI 卡顿（每帧 16.6ms）、生命周期阻塞

---

#### Java 中有哪些锁？各自的区别？

| 锁类型 | 实现 | 特点 |
|--------|------|------|
| `synchronized` | JVM 内置 | 自动释放，不可中断，非公平 |
| `ReentrantLock` | AQS | 可中断、可超时、公平/非公平、支持多 Condition |
| `ReentrantReadWriteLock` | AQS | 读锁共享、写锁独占 |
| `StampedLock` | AQS（Java 8） | 乐观读锁，性能更高 |

---

#### `synchronized` 的锁升级过程？

JDK 6 引入锁优化，对象头 Mark Word 状态变化：
1. **无锁** → **偏向锁**：第一个线程获取锁，Mark Word 记录线程 ID
2. **偏向锁** → **轻量级锁**：第二个线程竞争时，撤销偏向，CAS 操作 Lock Record
3. **轻量级锁** → **重量级锁**：CAS 自旋超过阈值，指向 Monitor 对象，未获取锁的线程阻塞

---

#### CAS 的原理？

CAS（Compare And Swap）：三个操作数 V（内存地址）、A（预期值）、B（新值），当 V == A 时将 V 更新为 B。底层通过 CPU 指令 `lock cmpxchg` 实现，`lock` 前缀锁住总线/缓存行保证原子性。问题：ABA 问题（`AtomicStampedReference` 解决）、自旋开销。

---

#### `ReentrantLock` 的实现原理？是公平锁吗？

基于 AQS，维护 `state` 变量（0=未锁定）。加锁时 CAS 将 state 从 0 改为 1，失败则封装为 Node 加入 CLH 队列并 `park` 阻塞。
- **公平锁**：`tryAcquire()` 先检查 CLH 队列是否有等待线程（`hasQueuedPredecessors()`）
- **非公平锁**：直接 CAS 抢锁，失败才排队

---

#### `volatile` 的作用？

1. **可见性**：读写直接操作主内存
2. **有序性**：内存屏障禁止指令重排序
3. **不保证原子性**：`volatile int count; count++` 仍不安全

---

#### 实现线程安全的方式有哪些？

1. **互斥同步**：`synchronized`、`ReentrantLock`
2. **非阻塞同步**：CAS（`AtomicInteger`、`AtomicReference`）
3. **无同步方案**：`ThreadLocal`、不可变对象（`final`、`String`）、栈封闭
4. **并发工具**：`ConcurrentHashMap`、`CopyOnWriteArrayList`、`BlockingQueue`

---

#### 线程安全的 Map、List 有哪些？

**Map**：`Hashtable`（全表锁）、`Collections.synchronizedMap`（包装器全表锁）、`ConcurrentHashMap`（分段锁/CAS+synchronized）

**List**：`Vector`（全表锁）、`Collections.synchronizedList`（包装器）、`CopyOnWriteArrayList`（写时复制，读无锁）

---

#### `ConcurrentHashMap` 的原理？`get` 方法如何实现多线程安全访问？

**JDK 8**：`Node[]` + 链表 + 红黑树（链表 > 8 且数组 >= 64 时转换）。`put` 对桶头节点 `synchronized`，空桶 CAS 插入。`get` **无锁**：Node 的 `val` 和 `next` 是 `volatile`，直接读保证可见性。

---

#### 多线程资源竞争如何解决？

1. 避免竞争：`ThreadLocal`、不可变对象
2. 互斥锁：`synchronized`、`ReentrantLock`
3. CAS 乐观锁：`Atomic` 类
4. 并发数据结构：`ConcurrentHashMap`、`BlockingQueue`
5. 信号量：`Semaphore`

---

#### 内存模型：堆、栈、程序计数器、方法区、常量池分别是什么？

| 区域 | 说明 |
|------|------|
| **堆（Heap）** | 存放对象实例，GC 管理，所有线程共享 |
| **栈（VM Stack）** | 局部变量表、操作数栈、方法出口，线程私有 |
| **程序计数器** | 当前线程执行的字节码行号，线程私有 |
| **方法区** | 类信息、常量、静态变量、JIT 编译代码，线程共享 |
| **常量池** | 方法区一部分，编译期字面量和符号引用 |

全局变量（静态变量）在方法区（JDK 7 后移到堆），函数局部变量在虚拟机栈。

---

### 3. 组件生命周期

#### Activity 的生命周期？

| 回调 | 触发场景 |
|------|---------|
| `onCreate()` | 首次创建 |
| `onStart()` | 变为可见 |
| `onResume()` | 获得焦点，可交互 |
| `onPause()` | 失去焦点但仍可见 |
| `onStop()` | 完全不可见 |
| `onDestroy()` | 被销毁 |
| `onRestart()` | 从 Stop 回到前台 |

**特殊场景：** A 启动 B → A.onPause → B.onCreate → B.onStart → B.onResume → A.onStop；屏幕旋转默认重建；弹窗式 Activity 只 onPause 不 onStop。

---

#### Fragment 的生命周期与 Activity 如何关联？

`onAttach → onCreate → onCreateView → onViewCreated → onStart → onResume → onPause → onStop → onDestroyView → onDestroy → onDetach`

Fragment 最大生命周期不能超过 Activity，Activity onPause 时 Fragment 最多到 onPause。

---

### 4. Handler 消息机制

#### Handler / Looper / MessageQueue 三者的关系？

- **Handler**：发送和处理消息（`sendMessage()` / `handleMessage()`）
- **Looper**：循环从 MessageQueue 取消息分发给 Handler（`loop()` 死循环）
- **MessageQueue**：按 `when` 排序的单链表，`next()` 阻塞获取消息

一个 Thread 一个 Looper 一个 MessageQueue，可对应多个 Handler。`ThreadLocal` 保证每线程最多一个 Looper。

---

#### 消息优先级机制是什么？如何发送一条最高优先级的消息？

MessageQueue 按 `when` 排序，无显式优先级。通过 `sendMessageAtFrontOfQueue()` 发送 `when=0` 的消息，插入到链表头部（当前最早消息之前）。

---

#### 什么是同步屏障与异步消息？

- **同步屏障**：`postSyncBarrier()` 插入 `target==null` 的特殊消息，屏障期间 `next()` 只分发异步消息
- **使用场景**：`ViewRootImpl.scheduleTraversals()` 中插入屏障 + 异步消息，确保 UI 渲染优先处理

---

#### Choreographer 的工作原理？VSYNC 信号如何驱动渲染？

`DisplayEventReceiver`（JNI）监听 VSYNC → `doFrame()` 依次执行：输入回调 → 动画回调 → Inset 动画 → Traversal（measure/layout/draw）→ Commit。双 VSYNC：`VSYNC_APP`（应用帧）和 `VSYNC_DISPLAY`（显示刷新），配合三缓冲减少掉帧。

---

#### MessageQueue 的阻塞/唤醒原理？是否只要插入消息就触发 `nativeWake`？

基于 `epoll` + pipe。无消息时 `nativePollOnce()` → `epoll_wait()` 阻塞。不是每次插入都唤醒，仅当：队列为空、新消息成为队头、或屏障后插入异步消息时才 `nativeWake`。

---

#### `nativeWake` 为何能唤醒线程？`epoll` 还有哪些应用场景？

向 pipe 写端写数据，epoll 监听读端检测到可读事件，`epoll_wait` 返回。应用：NIO/Netty、Nginx、Redis、数据库连接池。

---

#### 除 `epoll` 外，还有哪些操作可以阻塞线程？

`Object.wait()`、`Thread.sleep()`、`LockSupport.park()`、`BlockingQueue.take()`、`synchronized` 获取锁失败、`CountDownLatch.await()`、`ServerSocket.accept()`、`InputStream.read()`

---

#### MessageQueue 的阻塞唤醒与 `BlockingQueue` 的区别？

| 维度 | MessageQueue | BlockingQueue |
|------|-------------|---------------|
| 底层 | epoll + pipe（系统调用） | `ReentrantLock` + `Condition`（`LockSupport.park`） |
| 阻塞层级 | 内核态 | 用户态 |
| 场景 | Handler 消息循环 | 生产者-消费者、线程池 |

---

#### Handler 内存泄漏的原因与处理？

**引用链**：`Thread → Looper → MessageQueue → Message → Handler(target) → Activity`

**处理**：使用 `static` 内部类 + 弱引用持有 Activity；`onDestroy()` 中 `removeCallbacksAndMessages(null)`

---

#### `Handler().post` 能否正确获取 View 的宽高？

大多数情况下不能。`post()` 将 Runnable 添加到 MessageQueue 末尾，如果 View 还没完成 measure/layout 则宽高为 0。但 View attach 到窗口后 `post()` 是可靠的（加入 `RunQueue`，在 `performTraversals` 完成后执行）。

---

#### `onResume` 中能否直接获取 View 的宽高？

不能。`onResume` 时 View 树尚未完成 measure/layout/draw。正确方式：`ViewTreeObserver.addOnGlobalLayoutListener`、`View.post()`、`onWindowFocusChanged()`。

---

### 5. UI 绘制与渲染机制

#### Android UI 的绘制流程？

由 `ViewRootImpl.performTraversals()` 驱动：
1. **Measure**：自顶向下递归 `measure()` → `onMeasure()`，通过 `MeasureSpec`（EXACTLY/AT_MOST/UNSPECIFIED）传递约束
2. **Layout**：自顶向下递归 `layout()` → `onLayout()`，确定 `left/top/right/bottom`
3. **Draw**：自顶向下递归，依次绘制背景 → 自身内容（`onDraw`）→ 子 View（`dispatchDraw`）→ 装饰

**简单说：Measure 定大小，Layout 定位置，Draw 定内容。**

---

#### `invalidate()` 与 `requestLayout()` 的区别？

| 维度 | invalidate() | requestLayout() |
|------|-------------|-----------------|
| 触发流程 | 仅 draw | measure + layout + draw |
| 场景 | 外观变化（颜色、文本） | 尺寸变化 |
| 连续调用多次 | 同一帧合并（`mDirty` 区域合并） | `mLayoutRequested` 标记合并，同一帧只执行一次 |

---

#### `measuredWidth` 与 `width` 的区别？`LayoutParams.width`、`measuredWidth`、`getWidth()` 三者？

- `LayoutParams.width`：开发者设置的**期望宽度**（具体值/MATCH_PARENT/WRAP_CONTENT）
- `measuredWidth`：measure 阶段计算出的**测量宽度**
- `getWidth()`：layout 阶段确定的**实际显示宽度**（`right - left`）

---

#### 打开一个 Activity，第一帧数据绘制的触发点？

`handleResumeActivity()` → `WindowManager.addView(decorView)` → `ViewRootImpl.setView()` → `scheduleTraversals()` → 下一帧 VSYNC 执行 `performTraversals()` 完成首帧绘制。

---

#### Android 屏幕刷新机制（VSYNC）？

硬件定时器周期性信号（16.6ms/60fps）。`SurfaceFlinger` 合成图层送显，`Choreographer` 协调应用渲染。双 VSYNC + 三缓冲（Project Butter）。

---

#### `setContentView()` 的实现原理？

`Activity.setContentView()` → `PhoneWindow.setContentView()` → 首次创建 `DecorView`（`installDecor()`）→ 将布局 inflate 到 `contentParent`（`android.R.id.content`）→ 回调 `onContentChanged()`。此时 DecorView 还未添加到 WindowManager，等到 `handleResumeActivity()` 中 `addView` 才显示。

---

#### View 的事件传递机制？

`Activity.dispatchTouchEvent()` → `Window` → `DecorView` → `ViewGroup.dispatchTouchEvent()`：
1. `onInterceptTouchEvent()` 检查是否拦截
2. 不拦截则遍历子 View，调用 `child.dispatchTouchEvent()`
3. 子 View 不消费则回退到父 View 的 `onTouchEvent()`

`ACTION_DOWN` 决定消费者，`requestDisallowInterceptTouchEvent(true)` 阻止父拦截（DOWN 时无效）。

---

#### SurfaceView 和 TextureView 的区别？

| 特性 | SurfaceView | TextureView |
|------|------------|-------------|
| 底层 | 独立 Surface | SurfaceTexture + GL 纹理 |
| 硬件加速 | 不支持 | 支持 |
| View 特性 | 不支持 transform/alpha | 支持所有 View 变换 |
| 场景 | 游戏、视频、相机 | 需动画变换的视频/相机 |

---

#### 自定义 View 的实践经验和关键要点？

1. 继承 View/ViewGroup
2. `attrs.xml` 定义自定义属性，构造函数 `TypedArray` 读取
3. 重写 `onMeasure()`（处理 wrap_content）、`onLayout()`、`onDraw()`、`onTouchEvent()`
4. 性能优化：避免 `onDraw` 创建对象、使用 `invalidate()` 局部刷新、硬件加速

---

### 6. 内存泄漏

#### LeakCanary 的原理？

1. `registerActivityLifecycleCallbacks` 监控 `onDestroy`
2. `WeakReference` + `ReferenceQueue` 检测是否被 GC
3. 5 秒后未回收 → 判定泄漏
4. `HeapDump` 导出 hprof → `Shark` 库分析 GC Root 引用链
5. 输出泄漏链路

---

#### `SoftReference` 与 `WeakReference` 的区别？`ReferenceQueue` 的作用？

- **SoftReference**：内存不足时回收，适合缓存
- **WeakReference**：GC 时就回收
- **ReferenceQueue**：引用对象被 GC 回收后，Reference 加入队列。LeakCanary 利用它判断对象是否被回收
- 不能覆盖所有对象：Thread、System、JNI 对象等

---

#### 如何设计一个内存泄漏监控系统？

1. 在 `onDestroy` 时包装为弱引用关联 ReferenceQueue
2. 延迟 5-10 秒检查，未回收则疑似泄漏
3. 手动触发 GC 后再次检查
4. 导出 hprof 解析引用链
5. 上报泄漏信息 + 过滤已知系统泄漏

---

#### 内存泄漏如何定位？

LeakCanary（自动检测）、MAT（分析 hprof）、Android Studio Profiler（heap dump 对比）、StrictMode（开发模式检测）

---

#### 常见内存泄漏场景？

静态引用持有 Activity、单例持有 Activity、非静态内部类/匿名类（Handler/Runnable）、未取消的回调/监听器、资源未关闭（Cursor/Stream）、WebView、Thread/Timer

---

### 7. 性能优化

#### 冷启动优化的方案？如何做冷启动监控？

**优化方案：**
1. Application 优化：延迟初始化非必须组件，IdleHandler 延迟执行
2. 首屏优化：ConstraintLayout 扁平化，异步加载数据
3. 启动页优化：XML theme 启动窗口（`windowBackground`）
4. 预加载：AppStartup 库或 ContentProvider 并行初始化
5. I/O 优化：SP → MMKV
6. Baseline Profile 预编译

**监控：** `adb shell am start -W`、Systrace/Perfetto、`onWindowFocusChanged` 时间差、字节码插桩

---

#### 复杂页面（如交易页）的构建方案？

1. 模块化设计：拆分为独立组件
2. 异步加载：数据请求并行化，骨架屏先行
3. ViewStub 延迟加载非首屏模块
4. ConcatAdapter / Epoxy 管理复杂列表
5. 数据预取

---

#### 卡顿检测与优化的方案？

1. Systrace / Perfetto
2. BlockCanary：Looper Printer 监控主线程消息耗时
3. Choreographer.FrameCallback 监控每帧
4. StrictMode 检测主线程 IO
5. `Looper.setMessageLogging()` 监控 `dispatchMessage` 耗时

---

#### 如何统计每个页面的渲染时间？

1. `onCreate` 记录开始，`onWindowFocusChanged` 记录结束
2. `Choreographer.postFrameCallback` 首帧回调计算
3. `ViewTreeObserver.addOnPreDrawListener` 首帧计算
4. 字节码插桩自动插入计时

---

#### ANR 的分析、定位及解决方案？

**排查**：`/data/anr/traces.txt`、`adb bugreport`、APM 采集主线程堆栈

**常见类型**：输入超时（5s）、BroadcastReceiver（10s）、Service（前台 20s/后台 200s）

**解决**：耗时操作移到工作线程、避免主线程 IO、`StrictMode` 检测、协程替代 AsyncTask

---

#### 渲染帧率分析与过度绘制优化？

**分析**：GPU 呈现模式分析、Systrace/Perfetto、`adb shell dumpsys gfxinfo`

**优化**：移除不必要背景色、`clipRect()` 避免重叠、`merge` 标签减少层级、ConstraintLayout 扁平化、避免 `onDraw` 创建对象

---

#### 包体积优化？

代码：ProGuard/R8 混淆、移除未使用代码、避免不必要依赖
资源：图片压缩（WebP）、VectorDrawable、`resConfigs` 限制语言、App Bundle
SO：只保留 arm64-v8a、`extractNativeLibs=false`

---

#### 耗电优化？

合并请求 + FCM 推送替代轮询、WorkManager 管理后台任务、降低定位精度/频率、避免长时间 WakeLock、`setInexactRepeating()` 合并 Alarm

---

#### 性能分析工具？

| 工具 | 用途 |
|------|------|
| Android Profiler | CPU/内存/网络/电量实时监控 |
| MAT | HPROF 堆转储分析，内存泄漏 |
| Systrace / Perfetto | 系统级 Trace，帧率/卡顿 |
| TraceView | Java 层方法耗时（已废弃） |

---

#### 启动页优化：XML 异步加载？

`windowBackground` 启动主题、`ViewStub` 延迟加载、`AsyncLayoutInflater` 子线程 inflate、`LayoutInflater.Factory2` 拦截 View 创建

---

#### 列表与图片优化？

**懒加载**：`setHasFixedSize(true)` + `setItemViewCacheSize(n)`、图片按需加载、分页加载
**缓存**：Glide LruCache + DiskLruCache、`RecycledViewPool`
**避免过度重绘**：背景一致避免叠加、DiffUtil 精准更新

---

### 8. 系统架构与 IPC

#### Android 系统架构？

1. **应用层**：系统应用和第三方应用
2. **应用框架层**：AMS、WMS、PMS 等系统服务，Java/Kotlin API
3. **运行时框架层**：ART（AOT+JIT）、核心库、NDK
4. **硬件抽象层（HAL）**：标准化硬件接口（Camera HAL、Audio HAL）
5. **Linux 内核层**：进程/内存/文件/网络/驱动/安全

---

#### Android 中常用的 IPC 机制？

| IPC 方式 | 优点 | 缺点 |
|---------|------|------|
| **Binder** | 高性能（一次拷贝）、安全 | 使用复杂、1MB 缓冲限制 |
| **AIDL** | 自动生成桩代码、支持复杂接口 | 接口变更需同步 |
| **Messenger** | 简单、线程安全 | 串行、性能低 |
| **ContentProvider** | 适合数据共享、CRUD | 不适合 RPC |

---

### 9. Jetpack 与主流框架

#### ViewModel 的设计原理？如何配置变更时保持数据？

存储在 `ViewModelStore`（HashMap）。配置变更时通过 `retainNonConfigurationInstances()` 保存 `ViewModelStore`，新 Activity 重建时恢复。只有 `onFinish()` 或系统杀进程时才调用 `onCleared()`。

---

#### Room 数据库的架构？

`@Entity`（表结构）+ `@Dao`（CRUD）+ `@Database`（数据库持有者）。编译期 SQL 校验，支持 Flow/LiveData 响应式、Migration 版本迁移、TypeConverter 自定义类型转换。

---

#### Retrofit 和 OkHttp 的协作原理？

Retrofit 通过动态代理将接口转换为 `ServiceMethod`，解析注解构建 `Request`，交给 `OkHttpClient.newCall()` 执行。OkHttp 拦截器链：`RetryAndFollowUp → Bridge → Cache → Connect → Network → 网络请求`。

---

#### Glide 的缓存机制？

**四级缓存**：Active Resources（正在显示）→ Memory Cache（LruCache）→ Disk Cache（原始+转换后）→ 远程数据源。设计模式：Builder、观察者、策略、工厂、责任链。

---

#### AsyncListDiffer 的原理？

基于 Myers 差分算法，后台线程计算新旧列表差异（insert/remove/move/change），主线程应用更新到 RecyclerView。必须正确实现 `areItemsTheSame()` 和 `areContentsTheSame()`。

---

#### LiveData 的实现原理？

基于观察者模式，`LifecycleOwner` 感知生命周期。内部维护 `mVersion` 版本号，`observe()` 时注册 `Observer`，数据变更时通知活跃状态的 Observer。`setValue()` 主线程调用，`postValue()` 切主线程。支持 `map`、`switchMap` 等变换。

---

### 10. 本地数据存储

#### SQLite 与 Room 的关系？

Room 是 SQLite 的高级抽象层，底层使用 SQLite。编译期校验 SQL、注解驱动 API、支持 Flow/LiveData、Migration 机制。Google 推荐。

---

#### MMKV 的实现原理？相比 SharedPreferences 的优势？

基于 **mmap** 内存映射 + **protobuf** 序列化 + 增量写入 + 双 Buffer + CRC 校验。

| 特性 | SharedPreferences | MMKV |
|------|------------------|------|
| 性能 | 全量写入 XML | mmap 增量写入，微秒级 |
| 线程安全 | 有锁竞争 | 无锁（Futex） |
| ANR 风险 | commit 同步写可能 ANR | 无 |

**线程安全**：单进程用 `pthread_mutex_t`，多进程用 `fcntl` 文件锁。读操作也加互斥锁（扩容时 munmap+mmap 导致旧地址失效）。

---

#### Realm 的特点？

对象数据库（无需 ORM）、零拷贝（内存映射直接访问）、跨平台、响应式、AES-256 加密。已被 MongoDB 收购，新项目不建议使用。

---

#### SharedPreferences 有哪些缺陷？

全量写入（每次写整个 XML）、`commit()` 同步写磁盘可能 ANR、`apply()` 磁盘写入阻塞 `onPause/onStop`、跨进程不安全、全量加载到内存、无类型安全。

---

### 11. 架构设计

#### MVP 与 MVVM 的区别？

| 维度 | MVP | MVVM |
|------|-----|------|
| View 与 Model | Presenter 持有 View 接口，手动更新 | ViewModel 暴露数据，View 观察者自动更新 |
| 数据绑定 | 手动 | 数据驱动（LiveData/DataBinding） |
| 生命周期 | 不感知 | 感知 |

---

#### MVVM 与 MVI 的区别？

| 特性 | MVVM | MVI |
|------|------|-----|
| 数据流 | 双向/多向 | 单向 |
| 状态管理 | 多个 LiveData 分散 | 单一 State 集中 |
| 可预测性 | 较低 | 高 |
| 适用 | 中小项目 | 大型项目、复杂交互 |

---

#### Clean Architecture 的优缺点？适用场景？

**分层**：Presentation（UI+ViewModel）→ Domain（UseCase+Entity，纯 Kotlin）→ Data（Repository+DataSource）

**优点**：独立于框架、高度可测试、职责清晰、长期可维护
**缺点**：样板代码多、学习曲线陡、小项目过度设计、需 DI 框架
**适用**：大型团队、长期维护、业务复杂的项目

---

#### 模块化/组件化重构经验？

**问题与解决**：循环依赖 → 接口下沉；资源冲突 → 资源前缀；路由跳转 → ARouter/自建路由；数据共享 → 接口+DI；编译速度 → 模块独立/按需编译

---

#### EventBus 的原理？`postSticky` 粘性事件？

基于观察者模式+反射。`register()` 扫描 `@Subscribe` 注解方法建立映射。`post()` 查找订阅方法调用。`postSticky` 将事件存储在 `stickyEvents` Map 中，新订阅者注册时检查并立即分发。

---

### 12. 工程化建设

#### Gradle 编译流程与优化？

**流程**：Initialization（解析 settings.gradle）→ Configuration（执行 build.gradle，构建 Task 图）→ Execution（执行 Task）

**优化**：Gradle Daemon、并行编译、构建缓存、Configuration Cache、依赖镜像源

**依赖方式**：`implementation`（不传递）、`api`（传递）、`compileOnly`（仅编译）、`runtimeOnly`（仅运行）、`annotationProcessor`/`kapt`

---

#### 字节码插桩的原理与应用？

在 `.class`/`.dex` 中修改字节码。工具：ASM（直接操作字节码）、Javassist（源码级 API）、Transform API / Instrumentation API。应用：方法耗时统计、崩溃监控、自动埋点、权限监控。

---

#### 多渠道打包？

`ProductFlavors` 配置不同渠道，每个 Flavor 有独立源码和资源。`buildConfigField` 区分配置，`signingConfigs` 不同签名，`missingDimensionStrategy` 处理维度缺失。

---

#### 代码规范与 CI/CD？

**规范**：Ktlint/Detekt、Android Lint、自定义 Lint、`.editorconfig`、Pre-commit Hook、Code Review
**CI/CD**：提交触发 Lint+单测、MR 触发编译+UI 测试、自动打包分发（Firebase/蒲公英）

---

#### 单元测试与 UI 测试？

**单测**：JUnit + MockK（`mockk`、`every`、`verify`、`coEvery`），覆盖 ViewModel/UseCase/Repository
**UI 测试**：Espresso（`onView().perform().check()`）、Compose Testing（`createComposeRule()`）

---

### 13. 高级技术

#### JNI 的原理？Java 层和 Native 层如何通信？

JNI 是 Java 与 C/C++ 的桥梁。Java → Native：声明 `native` 方法 → C/C++ 实现 → `System.loadLibrary()` 加载 SO。Native → Java：`JNIEnv*` 的 `FindClass()`/`GetMethodID()`/`CallObjectMethod()`。关键：`JNIEnv` 线程局部、全局引用/局部引用、静态注册 vs 动态注册（`RegisterNatives`）。

---

#### APM 监控体系？崩溃分析方案？

**崩溃**：`UncaughtExceptionHandler`（Java）、Signal 处理（Native）、ANR 监控
**性能**：启动耗时、帧率（FrameCallback）、内存（`getMemoryInfo()`）、网络（Interceptor）
**卡顿**：`Looper.setMessageLogging()` 监控消息执行时长

---

#### 动态化技术？热修复和插件化？

**热修复**：`DexClassLoader` 加载补丁 DEX，修改 `dexElements` 插入补丁类（Tinker bsdiff）；资源替换修改 `AssetManager` 路径
**插件化**：Hook `Instrumentation`/`ActivityThread` 骗过系统检查，独立 Resources 资源隔离。代表：RePlugin、Shadow、VirtualAPK

---

#### 如何判断 so 库已经加载过？

`System.loadLibrary()` 内部检查是否已加载（重复不报错）；自定义标志位记录；检查类是否可用（`Class.forName`）；检查 native 方法是否存在。

---

#### Remote ADB 的使用？

`adb tcpip 5555` 切换到 TCP/IP 模式 → `adb connect <device_ip>:5555` 无线连接 → `adb usb` 切回 USB。需同一局域网。

---

#### 通用能力模块抽离？

接口抽象（`ILogger`/`IStorage`/`IRouter`）→ 模块隔离（独立 Gradle Module）→ 依赖注入（Hilt）。模块：lib-network、lib-storage、lib-tracker、lib-log、lib-router、lib-config。

---

#### 路由框架？

编译期 APT/KSP 收集路由信息生成路由表 → 拦截器链（登录检查/权限校验）→ 动态路由（服务端下发映射表 + URL Scheme）→ 降级策略。

---

#### 支付安全？

HTTPS + SSL Pinning、AES/RSA 加密、请求签名、Token 化、防重放（时间戳+Nonce）、Root/模拟器/调试器检测、ProGuard 混淆。

---

#### Google Play 支付流程？

`BillingClient` 连接 → 查询商品 → 发起购买 → 接收结果 → 确认消费（`consumeAsync`/`acknowledgePurchase`，3 天内未确认自动退款）→ 服务端验证。

---

### 14. Jetpack Compose

#### 状态提升？

将状态从 Composable 内部移到外部调用方管理，实现单一数据源。通过参数传递值 + Lambda 回调传递变化。

---

#### 自定义组件？

Canvas 绘制（`drawArc`/`drawCircle` 等）或 Layout 自定义布局（`measurables.map { it.measure(constraints) }` + `layout() { placeable.place(x, y) }`）。

---

#### 副作用处理方式？

| API | 触发时机 | 用途 |
|-----|---------|------|
| `LaunchedEffect` | 进入组合启动协程 | 一次性异步操作 |
| `SideEffect` | 每次重组后 | 同步到非 Compose 代码 |
| `DisposableEffect` | 进入组合执行，离开时 `onDispose` | 需要清理资源 |
| `rememberCoroutineScope` | 获取当前组合协程作用域 | 事件回调中启动协程 |

---

#### Compose 路由栈与 Fragment 路由栈冲突？

统一使用 Compose Navigation、`OnBackPressedDispatcher` 统一返回键、避免混合导航（新页面优先 Compose）。

---

#### Compose Multiplatform / KMP？

KMP 共享业务逻辑（`expect/actual`），Compose Multiplatform 共享 UI 层（Skia/Impeller 渲染）。网络用 Ktor、存储用 SQLDelight。

---

### 15. 其他

#### App 启动流程？冷启动与热启动的区别？

**流程**：Launcher → `startActivity` → AMS → 检查进程 → 不存在则 Zygote fork → `ActivityThread.main()` → 创建 Looper → 创建 Application → 启动 Activity

- **冷启动**：进程不存在，需创建进程+初始化 Application
- **热启动**：进程存在，只创建 Activity
- **温启动**：进程存在但 Activity 被回收

---

#### Application 是什么时候创建的？

`ActivityThread.handleBindApplication()` 中：`Instrumentation.newApplication()` 反射创建 → `attach()` → ContentProvider 初始化 → `onCreate()`。在 Activity 启动之前。

---

#### Context 是什么时候产生的？

`ActivityThread.performLaunchActivity()` 中通过 `Instrumentation.newContext()` 创建 `ContextImpl`。Application/Activity/Service 各有自己的 `ContextImpl`，都是 `ContextWrapper` 子类。

---

#### Gradle 有几种依赖方式？

| 方式 | 说明 |
|------|------|
| `implementation` | 编译运行可用，不传递 |
| `api` | 编译运行可用，传递 |
| `compileOnly` | 仅编译时 |
| `runtimeOnly` | 仅运行时 |
| `annotationProcessor`/`kapt` | 注解处理器 |

---

## 二、Flutter

### 1. Dart 语言

#### Dart 语言特点？事件循环机制？

**特点**：AOT+JIT 编译、单线程模型（默认）、分代 GC、Isolate 多线程

**事件循环**：Microtask Queue（高优先级，`scheduleMicrotask`）+ Event Queue（低优先级，IO/定时器/手势）。执行顺序：同步代码 → 清空 Microtask → 取 Event Queue 一个事件 → 再清空 Microtask → 循环。

---

#### Dart Future 的实现原理？

`Future` 代表异步操作的最终结果。`async/await` 是语法糖，编译器转为状态机。`then` 回调加入 Event Queue，`Future.microtask` / `scheduleMicrotask` 加入 Microtask Queue。

---

#### Dart 单线程模型的特点？

主线程单线程，Event Loop 驱动，异步不阻塞主线程，无锁竞争，计算密集任务需其他 Isolate。

---

#### Dart Isolate 与线程的区别？通信方式？

Isolate 拥有独立堆内存，不共享；线程共享内存。通信：SendPort/ReceivePort、`compute()`、`Isolate.spawn()`、TransferableTypedData（零拷贝）。

---

#### Dart 和 Java 的 GC 区别？

| 维度 | Dart | Java |
|------|------|------|
| 算法 | 新生代 Scavenge + 老生代 Mark-Sweep | 多种收集器（G1/ZGC/Shenandoah） |
| 内存模型 | Isolate 独立堆，无跨线程 GC 竞争 | 多线程共享堆 |
| 堆大小 | 较小（移动端） | MB 到 TB |
| 可调性 | GC 参数少，对开发者透明 | 丰富调优参数 |

---

### 2. 渲染机制

#### Flutter Widget → Element → RenderObject 的渲染流程？

| 树 | 作用 |
|---|------|
| Widget 树 | 不可变 UI 描述 |
| Element 树 | 可变中间层，管理生命周期 |
| Render 树 | 负责布局和绘制 |

**流程**：Build（Widget 对比更新 Element）→ Layout（RenderObject 计算尺寸位置）→ Paint（记录到 Layer 树）→ Composite（Skia/Impeller 渲染到屏幕）→ VSync 驱动每帧。

---

#### Flutter Key 的分类？

```
Key
├── LocalKey（同级唯一）
│   ├── ValueKey（值相等）
│   ├── ObjectKey（对象实例）
│   └── UniqueKey（全局唯一）
└── GlobalKey（跨父节点）
```

通过 Key 判断新旧 Widget 匹配，相同 Key 复用 Element 保留 State。

---

### 3. 组件生命周期与响应式

#### StatefulWidget 的生命周期？

`createState → initState → didChangeDependencies → build → didUpdateWidget → deactivate → dispose`

---

#### 响应式编程？数据驱动 UI？

`UI = f(state)`。`setState()` / `notifyListeners()` → Element 标记 dirty → 下一帧 VSync → `build()` 重建 → 更新 Element/RenderObject → 重新布局绘制。推送模型，`build()` 应幂等。

---

### 4. 状态管理

#### Provider、Riverpod、BLoC、GetX 对比？

| 方案 | 设计思想 | 优点 | 缺点 |
|------|---------|------|------|
| Provider | InheritedWidget | 官方推荐、简单 | 依赖 BuildContext |
| Riverpod | Provider 进化 | 编译期安全、不依赖 Context | 学习曲线陡 |
| BLoC | Stream 事件驱动 | 清晰、易测试 | 样板代码多 |
| GetX | 全能型 | 高效、代码少 | 侵入性强 |

---

### 5. 性能优化

#### Flutter 性能分析工具？

| 工具 | 场景 |
|------|------|
| Flutter DevTools | 日常调试（Widget Inspector/Performance/Memory/Network） |
| Performance Overlay | 实时 UI/Raster 线程耗时 |
| `flutter run --profile` | 接近生产环境性能测试 |

**优化**：`ListView.builder` 懒加载、`const` Widget、`RepaintBoundary` 隔离重绘、正确 Key 避免不必要重建、精准刷新。

---

### 6. Platform Channel

#### 三种类型？

| 类型 | 方向 | 场景 |
|------|------|------|
| MethodChannel | Flutter → Native | 调用原生 API |
| EventChannel | Native → Flutter | 传感器等流式数据 |
| BasicMessageChannel | 双向 | 自定义消息格式 |

---

### 7. 工程化与混合架构

#### Flutter Boost 框架？

闲鱼开源，解决混合栈管理。Native 管理页面栈，每个 Flutter 页面对应一个 Native Container。BoostChannel 通信路由和参数。

---

#### 热更新方案？

| 方案 | 原理 | 限制 |
|------|------|------|
| 官方 Hot Reload | 保留 State 重建 Widget 树 | 仅开发模式 |
| MXFlutter | JS 编写 Flutter UI，动态下发 | 性能有损 |
| Kraken | 基于 Flutter Web 引擎 | 兼容性 |

iOS 限制：App Store 禁止下载可执行代码（JS 通过 JavaScriptCore 除外）。

---

## 三、IM 系统

### 1. IM 核心架构

#### IM 系统的整体架构？

`客户端 A → 接入层（网关）→ 消息路由层 → 业务逻辑层 → 消息存储层 → 推送层 → 客户端 B`

消息流程：组装 → 本地存储（发送中）→ 长连接发送 → 网关鉴权 → 路由查找 → 校验/过滤/持久化 → 推送给在线用户/离线推送 → 接收方 ACK。

---

#### 长连接协议选型？

| 协议 | 优点 | 缺点 | 场景 |
|------|------|------|------|
| WebSocket | HTTP 穿透好、浏览器支持 | 协议开销大 | 中小型/Web IM |
| 自定义 TCP | 紧凑、可控、多路复用 | 需自行实现应用层协议 | 大型 IM |
| UDP | 低延迟 | 不可靠 | 音视频 |

---

#### 消息同步策略？

**增量同步**：客户端维护 `lastSeqId`，服务端返回大于该 ID 的消息。**全量同步**：首次登录或异常时拉取。离线消息分页拉取，可能过期。

---

#### 消息 ID 设计？

**雪花算法**：64 位 = 时间戳(42) + 机器 ID(10) + 序列号(12)，全局唯一趋势递增。**服务端 SeqId**：会话内自增有序。**组合**：展示用 UUID，同步用 SeqId。

---

#### 消息状态管理？

`未发送 → 发送中 → 已发送 → 已送达 → 已读`，失败则重试。每条消息维护 status 字段，UI 展示加载动画/单勾/双勾/红色感叹号。

---

#### 至少一次投递？

发送后等 ACK → 服务端持久化返回 ACK → 接收方发 ACK → 超时重发 → 接收方基于消息 ID 去重 → 服务端幂等处理。

---

#### 端到端加密？

Signal Protocol（X3DH 密钥交换 + Double Ratchet），AES-256-GCM 加密。前向安全、身份验证、MAC 校验、元数据保护。

---

### 2. 长连接与协议

#### WebSocket 连接管理？

建连（HTTP Upgrade 101）→ 鉴权（Token）→ 心跳（Ping/Pong）→ 重连（指数退避）→ 断连（Close 帧/网络异常）

---

#### 断线重连策略？

指数退避 + 随机抖动（Jitter）+ 服务端限流 + 网络恢复统一触发重连。

---

#### 网络切换/前后台切换？

**网络切换**：监听 `ConnectivityManager.NetworkCallback`，主动断开旧连接立即重连，拉取遗漏消息。
**前后台**：进入后台停止心跳/注册推送备用，回前台检查连接/快速重连/拉取遗漏。

---

#### TLS/SSL 证书校验？

`wss://` 底层 TLS，系统 CA 证书链校验 + SSL Pinning（内置证书指纹）。推荐锁定公钥（证书轮换无需更新客户端）。

---

### 3. 消息可靠性与弱网

#### 消息乱序/重复/丢失？

**乱序**：服务端 SeqId 排序。**重复**：基于消息 ID 去重（布隆过滤器/HashSet）。**丢失**：ACK + 超时重试 + 离线补偿。

---

#### 弱网检测与适配？

**检测**：RTT > 500ms、丢包率、带宽估算。**适配**：降低图片质量、减少心跳、合并请求、控制并发、提示用户。

---

#### 弱网消息发送策略？

FIFO 队列 + 并发控制 + 优先级（文本 > 图片 > 视频）+ 超时增大 + 指数退避重试（最多 3 次）。

---

#### 幂等性设计？

唯一请求 ID + 服务端状态机校验 + 乐观锁 + 客户端去重。

---

#### 缓存与降级策略？

**缓存**：请求缓存、响应 Cache-Control、预加载。**降级**：功能降级（关闭非核心）、数据降级（低质量图片）、接口降级（备用接口）、UI 降级（缓存+重试）。

---

### 4. IM 本地存储

#### 消息表结构与分表？

按会话分表 / 按时间分表 / 混合策略。索引：`(conversation_id, seq_id)` 用于消息加载，`(conversation_id, created_at)` 用于时间查询。

---

#### 消息索引与搜索？

会话表维护 `last_msg_id`/`unread_count`，全文索引（FTS）支持关键词搜索，按 `seq_id` 分页加载。

---

#### 大量消息查询优化？

分页加载（20~50 条）、懒加载（只加载可见会话）、WAL 模式、定期清理过期消息。

---

#### 消息撤回与删除？

**撤回**：服务端下发通知，本地标记「已撤回」，不物理删除。**删除**：本地标记/双方删除/定期物理清理/媒体文件释放。

---

## 四、金融交易业务

### 1. 数值精度与交易逻辑

#### BigDecimal 精度处理？

```kotlin
val price = BigDecimal("19.99") // 使用 String 构造
val result = total.divide(BigDecimal("3"), 2, RoundingMode.HALF_UP)
```

**常见问题**：`BigDecimal(0.1)` 精度丢失（IEEE 754）、double 运算误差、除法无限循环。**最佳实践**：String 构造、明确 MathContext、服务端下发字符串、本地用 Long 存储最小单位。

---

#### 交易数值计算准确性保证？

统一精度规范（价格 8 位/数量 4 位/金额 2 位展示+8 位内部）、服务端为准（客户端仅展示预估）、防篡改签名、边界值测试。

---

### 2. 高频刷新与行情

#### 高频列表优化？DiffUtil 注意事项？

`areItemsTheSame` 比较 ID（高效）、`areContentsTheSame` 用 hashCode/版本号、大数据集异步 diff、合并多次更新、固定时间窗口合并。

---

#### 节流/防抖策略？

- **Throttle**：固定窗口只执行第一次（`throttleLatest(100)`）
- **Debounce**：停止触发后等待执行（`debounce(200)`）
- **Sample**：固定间隔取最新值（`sample(16)`）
- **局部刷新**：`notifyItemChanged(position, payload)` 只更新变化部分

---

#### 高频行情 UI 优化？

数据层：100ms 窗口合并 + 增量更新 + 后台线程处理
UI 层：局部刷新 + AsyncListDiffer + 价格动画过渡 + 不可见区域不刷新
渲染层：Canvas 自绘 K 线图 + SurfaceView + ConstraintLayout 扁平化

---

#### WebSocket 行情接入？

订阅管理（进入页面订阅/离开取消/批量操作）+ 心跳（10~30s）+ 断线重连重新订阅 + 消息序号检测补发。

---

#### WebSocket 前后台切换与数据连续性？

进入后台保持连接降低心跳、回前台检查重连。记录最后数据序号，重连后补发缺失，超过阈值全量刷新。服务端保证序号连续。

---

#### 交易下单流程？

选择交易对/方向/价格/数量 → 客户端校验 → 风控检查（余额/限额/频率）→ 撮合引擎（价格优先时间优先）→ 更新持仓余额 → WebSocket 推送状态。

---

#### OrderBook 合并逻辑？

相同价格合并为一档显示总量，按最小价格步进取整，WebSocket 增量更新（diff 模式），本地维护完整 OrderBook。

---

#### 链上充值？

获取充值地址 → 外部钱包转账 → 链上确认（等待区块确认数）→ 后端监听链上事件 → 达到确认数更新余额 → 通知用户。

---

#### 仓位卡顿优化？

WebSocket 增量更新代替全量推送、`shouldRebuild`/`const` Widget 减少重建、持仓计算放后台 Isolate、`ListView.builder` 懒加载。

---

## 五、综合 & 其他

### 1. AI 辅助开发

#### AI 开发工具完整流程？

需求分析 → AI 生成代码 → AI Code Review → 测试生成 → 持续集成 → 反馈迭代

---

#### AI Code Review 设计方案？

PR 触发 → 收集 diff+相关文件+规范 → AI 分析（风格/bug/性能/安全）→ 评论反馈 → 学习迭代

---

#### Session 压缩与跨 Session？

**压缩**：长对话历史压缩为摘要保留关键上下文。**跨 Session**：项目状态/决策持久化到 Memory，新 Session 加载历史。

---

#### Token 消耗与上下文管理？

对话历史截断、摘要压缩、RAG（检索增强生成）、分层上下文（系统提示+知识+对话）。

---

#### Harness 与 Agent？

**Harness**：运行环境和工具集（文件操作/终端/搜索）。**Agent**：自主决策 AI 实体，规划→执行→验证。

---

#### AI Chat 打字机效果？与 WebSocket 区别？

SSE（Server-Sent Events）流式返回 Token 逐字渲染。SSE 单向+HTTP+自动重连，WebSocket 双向+独立协议。

---

#### Skill 与 MCP 的区别？

**Skill**：预定义指令模板教 AI 完成特定任务。**MCP**：标准化协议连接 AI 与外部工具/数据源。

---

#### AI 辅助编程工作流？

`需求分析 → 方案设计 → AI 生成代码 → 人工 Review → 测试验证 → 迭代优化`

---

### 2. Git

#### 如何将多个 commit 合并为一个？

1. `git rebase -i HEAD~n`，`pick` 改 `squash`/`fixup`
2. `git reset --soft HEAD~n` + 重新 commit
3. `git merge --squash feature-branch`

---

#### `git merge` 与 `git rebase` 的区别？

| 维度 | merge | rebase |
|------|-------|--------|
| 历史 | 保留完整+合并提交 | 线性历史 |
| 安全 | 不修改已有提交 | 重写提交（改变 hash） |
| 场景 | 公共分支 | 个人分支整理 |

---

### 3. Java / Kotlin 深入

#### Kotlin / Java 中有哪些单例写法？

**Java**：饿汉式、懒汉式（synchronized）、DCL（volatile+synchronized）、静态内部类、枚举
**Kotlin**：`object`、`companion object`、`by lazy`

---

#### `object` 关键字的原理？线程安全？

编译为 `final` 类 + `static final INSTANCE`，在 `<clinit>` 中创建。JVM 保证 `<clinit>` 只执行一次且同步，线程安全。

---

#### `by lazy` 的原理？线程安全？

默认 `SynchronizedLazyImpl`，内部 `synchronized(lock)` 保证线程安全。模式：`SYNCHRONIZED`（默认）、`PUBLICATION`（CAS）、`NONE`（无线程安全）。

---

#### Kotlin 扩展函数的实现原理？

编译为**静态方法**，第一个参数是接收者对象。如 `fun String.hello()` → `static void hello(String $this$hello)`。语法糖，不修改原始类。

---

#### Kotlin 默认参数的实现原理？

编译器生成重载方法 + `int` 掩码参数（每一位表示对应参数是否使用默认值）+ 桥接方法（`$default` 后缀）。

---

#### Kotlin 接口默认方法的实现原理？

编译为接口 `static` 方法 + `DefaultImpls` 内部类。实现类调用 `InterfaceName.DefaultImpls.methodName(this)`。

---

#### Kotlin `by` 委托机制？

`by` 将 getter/setter 委托给实现 `ReadWriteProperty` 的对象。编译后 getter 调用 `delegate.getValue()`，setter 调用 `delegate.setValue()`。常用：`lazy`、`observable`、`vetoable`。

---

#### 静态代理与动态代理？

**静态代理**：手动编写代理类持有真实对象
**动态代理**：`Proxy.newProxyInstance(classLoader, interfaces, InvocationHandler)`

---

#### 手写 Kotlin 单例？字节码？

```kotlin
object Singleton { fun doSomething() {} }
```
等价 Java：`public final class Singleton { public static final Singleton INSTANCE = new Singleton(); private Singleton() {} }`

---

#### 线程级别的单例？

```kotlin
private val threadLocal = ThreadLocal<MyClass>()
fun getInstance(): MyClass {
    var instance = threadLocal.get()
    if (instance == null) { instance = MyClass(); threadLocal.set(instance) }
    return instance
}
```
原理：每个线程有独立 `ThreadLocalMap`，以 ThreadLocal 为 key 存储线程私有实例。

---

#### 类的加载时机？`static` 单例为何线程安全？

**加载时机**：new 对象、访问静态成员、创建子类、反射调用、JVM 启动主类
**线程安全**：JVM 类加载阶段加锁（Class 对象锁），保证 `<clinit>` 只执行一次。

---

### 4. 网络

#### 为何做 HttpDNS？如何实现？

**原因**：LocalDNS 劫持、解析慢、TTL 不准确。**实现**：HTTP/HTTPS 请求 IP → 客户端缓存 → 自定义 `Dns` 接口替换 → IP 直连。

---

#### IP 直连替换 baseURL 的问题？

HTTPS 证书校验失败（SNI 不匹配）、Cookie 域名不匹配、CDN 失效。**解决**：自定义 SSLSocketFactory 设置 hostname、自定义 CookieJar。

---

#### IP 择优与 HttpDNS 降级？

返回多 IP + 延迟测试选最优。降级：HttpDNS 失败回退 LocalDNS + 缓存上次结果。

---

#### `Dns.lookup()` 调用时机？ConnectionPool 复用节省？

建立连接时调用（`RealConnection.connect()`）。复用节省 TCP 三次握手 + TLS 握手时间。

---

#### OkHttp 自动重试？避免重复下单？

`retryOnConnectionFailure(true)` 重试连接失败/路由失败。POST 也会重试。**避免**：服务端幂等校验、客户端设置 `retryOnConnectionFailure(false)`。

---

#### 100 个相同域名请求的连接数？

**HTTP/1.1**：100 条活跃连接（完成后保留 5 条空闲）。**HTTP/2**：1 条连接（多路复用，100 个 Stream）。

---

#### WebView 无法复用原生网络层？Native 代发？CORS？

WebView 有自己的 Chromium 网络栈，与 OkHttp 隔离。`shouldInterceptRequest()` 拦截请求用原生 OkHttp 代发。**会绕过 CORS**（原生不受浏览器同源策略限制）。**跨域**：协议/域名/端口任一不同。

---

#### HTTPS 交互过程？

TCP 三次握手 → TLS 握手（Client Hello → Server Hello+证书 → 验证证书 → Pre-Master Secret 加密发送 → 生成会话密钥）→ 对称加密通信 → close_notify 断开。

---

#### HTTP 缓存机制？

| Header | 说明 |
|--------|------|
| `Cache-Control` | `max-age`/`no-cache`/`no-store`/`public`/`private` |
| `Expires` | 过期时间（优先级低于 Cache-Control） |
| `ETag` / `If-None-Match` | 内容哈希条件请求 |
| `Last-Modified` / `If-Modified-Since` | 修改时间条件请求 |

强缓存未过期直接用 → 过期后协商缓存（304 或 200）。

---

#### WebSocket 稳定性保障？

心跳（Ping/Pong）、指数退避重连、应用层 ACK、WebSocket + 长轮询降级。

---

#### WebSocket 高频优化？防止 buffer 膨胀？

消息合并、节流限流、背压机制、最大 buffer 限制（超出丢弃/重连）、Protobuf 紧凑格式。

---

#### WebSocket 订阅健壮性？

连接状态检查、重连后重新订阅、消息去重、异常处理不影响后续、超时机制、页面销毁取消订阅关闭连接。

---

#### 流量优化与最优链路？

HTTP/2 多路复用、WebSocket 长连接、Gzip/Protobuf 压缩、CDN、HttpDNS IP 择优、ConnectionPool 复用、请求合并。

---

### 5. 抓包

#### Charles 抓包原理？

中间人代理（MITM）：手机配置代理 → Charles 生成自签名 CA 证书 → 手机安装信任 → 请求经 Charles 解密转发 → 可查看/修改请求响应。

---

#### AI 抓包工具？

MITM 代理捕获流量 → 解析 HTTP/HTTPS → 格式化发送给 AI → AI 提供调试建议。

---

### 6. 序列化

#### `Serializable` 与 `Parcelable` 的区别？

| 维度 | Serializable | Parcelable |
|------|-------------|------------|
| 来源 | Java 标准库 | Android 特有 |
| 实现 | 标记接口+反射 | `writeToParcel`/`CREATOR` |
| 性能 | 慢（反射、临时对象多） | 快（直接内存操作） |
| 场景 | 磁盘/网络 | Intent/Bundle 内存传递 |

Parcelable 不适合本地持久化（Parcel 是内存数据结构，格式不稳定）。需持久化可转 JSON/ProtoBuf。

---

## 六、算法（低频 ⭐）

### 1. 两数之和（LeetCode #1）

```kotlin
fun twoSum(nums: IntArray, target: Int): IntArray {
    val map = HashMap<Int, Int>()
    for (i in nums.indices) {
        val complement = target - nums[i]
        map[complement]?.let { return intArrayOf(it, i) }
        map[nums[i]] = i
    }
    return intArrayOf()
}
```
时间 O(n)，空间 O(n)。

---

### 2. 最长公共前缀（LeetCode #14）

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

---

### 3. 字符串倒序打印

```kotlin
fun printReverse(str: String) {
    for (i in str.length - 1 downTo 0) {
        println(str[i])
    }
}
```

---

### 4. 路径总和 II（LeetCode #113）

```kotlin
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

---

### 5. 字母异位词分组（LeetCode #49）

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
时间 O(N * K log K)。
