# Boss直聘JD要求

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

#### 请说说 Kotlin 协程的启动模式（CoroutineStart）有哪些？各自的区别是什么？

| 启动模式 | 行为 |
|---------|------|
| `DEFAULT` | 立即按照调度器调度执行，如果是 `Dispatchers.Unconfined` 则当前线程直接执行 |
| `LAZY` | 惰性启动，协程不会立即执行，直到首次 `await()` 或 `start()` 时才调度 |
| `ATOMIC` | 原子启动，协程体不可被取消，保证协程体开始执行前不会被取消（但协程体内部的挂起点仍可取消） |
| `UNDISPATCHED` | 非调度启动，协程体在当前线程立即执行，直到遇到第一个挂起点后才按调度器切换 |

**使用场景：**
- `DEFAULT`：最常用的模式，正常启动协程
- `LAZY`：需要延迟执行或条件执行时
- `ATOMIC`：需要保证协程体一定开始执行的场景（如资源初始化）
- `UNDISPATCHED`：需要在当前线程立即执行部分逻辑的场景

---

#### 线程池的配置策略和使用场景？ThreadPoolExecutor 的核心参数如何设置？

**ThreadPoolExecutor 核心参数：**
1. `corePoolSize`：核心线程数，即使空闲也不会被回收（除非设置 `allowCoreThreadTimeOut`）
2. `maximumPoolSize`：最大线程数，包括核心线程和非核心线程
3. `keepAliveTime` + `unit`：非核心线程空闲存活时间
4. `workQueue`：任务等待队列
5. `threadFactory`：线程工厂，用于创建线程
6. `handler`：拒绝策略

**常用队列：**
- `LinkedBlockingQueue`：无界队列，任务会一直排队（maximumPoolSize 失效）
- `ArrayBlockingQueue`：有界队列，配合 maximumPoolSize 使用
- `SynchronousQueue`：不存储任务，直接交给线程，适合大量短时任务

**拒绝策略：**
- `AbortPolicy`（默认）：抛出 RejectedExecutionException
- `CallerRunsPolicy`：调用者线程直接执行
- `DiscardPolicy`：静默丢弃
- `DiscardOldestPolicy`：丢弃最老任务

**配置策略：**
- **CPU 密集型**：corePoolSize = CPU 核心数 + 1
- **IO 密集型**：corePoolSize = CPU 核心数 * 2 或更多（如 2N），因为线程经常阻塞等待 IO
- **混合型**：根据任务的 CPU 时间与等待时间比例调整，公式：`线程数 = CPU 核心数 * (1 + 等待时间/计算时间)`

---

### 2. 内存模型与并发

#### Android Framework 的内存模型是怎样的？Java/Kotlin 内存模型在 Android 中的体现？volatile、synchronized、CAS 在并发场景中的应用？

**Android 内存模型：**
- Android 基于 Linux 内核，每个进程有独立的虚拟地址空间
- 底层使用 ART（Android Runtime），ART 使用分代 GC（Concurrent Copying Collector）
- Java/Kotlin 内存模型遵循 JMM（Java Memory Model）：
  - **主内存**：所有线程共享的变量存储区（堆）
  - **工作内存**：每个线程私有的变量副本（CPU 缓存/寄存器）
  - 线程对变量的操作在工作内存中进行，之后同步回主内存

**volatile：**
- 保证**可见性**：读写直接操作主内存，禁止使用工作内存缓存
- 保证**有序性**：通过内存屏障禁止指令重排序
- **不保证原子性**：如 `volatile int count; count++` 仍不安全
- 典型应用：双重检查锁定（DCL）单例、状态标志位

**synchronized：**
- 保证**原子性**和**可见性**
- 通过 Monitor（管程）实现，JVM 层面通过 monitorenter/monitorexit 字节码
- Android ART 优化：偏向锁 → 轻量级锁 → 重量级锁升级
- 典型应用：同步代码块、同步方法

**CAS（Compare And Swap）：**
- 无锁原子操作，CPU 指令级别保证原子性
- 三个操作数：内存值 V、预期值 A、新值 B，仅当 V == A 时才将 V 更新为 B
- 存在 ABA 问题，解决思路：在变量前面追加上版本号或者时间戳。AtomicStampedReference  类就是用来解决 ABA 问题的，其中的 compareAndSet() 方法就是首先检查当前引用是否等于预期引用，并且当前标志是否等于预期标志，如果全部相等，则以原子方式将该引用和该标志的值设置为给定的更新值。
- 典型应用：`AtomicInteger`、`AtomicReference`、协程底层实现

---

#### Android 中的多线程模型是怎样的？主线程为什么不能执行耗时操作？

**Android 多线程模型：**
- **主线程（UI 线程）**：负责 UI 渲染、事件处理、生命周期回调
- **工作线程**：通过 Thread、HandlerThread、IntentService、ExecutorService、协程等方式创建
- **线程间通信**：通过 Handler 消息机制将结果切回主线程更新 UI

**主线程不能执行耗时操作的原因：**
1. **ANR（Application Not Responding）**：主线程被阻塞超过 5 秒（输入事件）或 10 秒（BroadcastReceiver），系统会弹出 ANR 对话框
2. **UI 卡顿**：主线程负责 UI 绘制，每帧需要在 16.6ms 内完成（60fps），耗时操作导致掉帧
3. **生命周期阻塞**：Activity/Service 的生命周期回调在主线程执行，耗时操作阻塞后续组件启动

---

#### 请详细讲解 Handler 消息机制的原理？Looper、MessageQueue、Handler 三者如何协作？

**核心组件：**
1. **Handler**：发送和处理消息的接口，通过 `sendMessage()` 发送消息，通过 `handleMessage()` 处理消息
2. **Looper**：消息循环器，不断从 MessageQueue 中取出消息并分发给对应 Handler 处理
3. **MessageQueue**：消息队列，按 `when` 时间排序的单链表，通过 `next()` 取出下一条消息

**协作流程：**
```
Handler.sendMessage() → MessageQueue.enqueueMessage() → Looper.loop() 循环取出 → Message.target.dispatchMessage() → Handler.handleMessage()
```

**关键细节：**
- `Looper.loop()` 是一个死循环，通过 `MessageQueue.next()` 阻塞获取消息（底层使用 Linux `epoll` 机制实现阻塞/唤醒）
- 每个 Message 持有其目标 Handler 的引用（`target`字段）
- `MessageQueue.next()` 在没有消息时会阻塞（`nativePollOnce`），有新消息时通过 `nativeWake` 唤醒
- 主线程的 Looper 通过 `Looper.getMainLooper()` 获取，在 `ActivityThread.main()` 中调用 `Looper.prepareMainLooper()` 初始化
- `ThreadLocal` 保证每个线程最多只有一个 Looper 和 MessageQueue
- `Looper.quit()` / `Looper.quitSafely()` 退出循环，后者会处理完已有的屏障消息

---

### 3. 组件生命周期

#### 请详细描述 Activity 的生命周期？各个回调方法在什么场景下触发？

| 回调 | 触发场景 |
|------|---------|
| `onCreate()` | Activity 首次创建，初始化 UI 和数据 |
| `onStart()` | Activity 变为可见（但未在前台交互） |
| `onResume()` | Activity 进入前台并获得焦点，可以交互 |
| `onPause()` | Activity 失去焦点但仍可见（如弹出对话框、切换到其他 Activity） |
| `onStop()` | Activity 完全不可见 |
| `onDestroy()` | Activity 被销毁（用户主动关闭或系统回收） |
| `onRestart()` | Activity 从 Stop 状态重新回到前台 |

**特殊场景：**
- **A 启动 B**：A.onPause → B.onCreate → B.onStart → B.onResume → A.onStop
- **B 返回 A**：B.onPause → A.onRestart → A.onStart → A.onResume → B.onStop → B.onDestroy
- **屏幕旋转**：onPause → onStop → onDestroy → onCreate → onStart → onResume（默认会重建）
- **弹窗式 Activity（透明/对话框主题）**：onPause 但不会 onStop
- **onSaveInstanceState**：在 onStop 之前调用（API 28+ 在 onStop 之后），用于保存临时状态
- **onRestoreInstanceState**：在 onStart 之后、onResume 之前调用

---

#### Fragment 的生命周期与 Activity 的生命周期如何关联？

**Fragment 生命周期：**
`onAttach → onCreate → onCreateView → onViewCreated → onStart → onResume → onPause → onStop → onDestroyView → onDestroy → onDetach`

**与 Activity 的关联：**
- Fragment 的生命周期受宿主 Activity 驱动：Activity 调用 onStart 时，其所有 Fragment 依次调用 onStart
- Fragment 的最大生命周期不能超过 Activity：Activity onPause 时，Fragment 最多到 onPause
- `FragmentLifecycleCallbacks`：FragmentManager 提供回调监听 Fragment 生命周期变化

---

### 4. UI 绘制与渲染机制

#### 请描述 Android UI 的绘制流程？

Android UI 的绘制流程主要分为三个阶段：**Measure（测量）**、**Layout（布局）**、**Draw（绘制）**，由 `ViewRootImpl` 的 `performTraversals()` 方法驱动，这是整个绘制流程的入口。

当 UI 需要刷新时（如 `invalidate()`、`requestLayout()`），`ViewRootImpl` 会调用 `performTraversals()`，它按顺序执行：

```
performTraversals()
  ├── performMeasure()    → 测量阶段
  ├── performLayout()     → 布局阶段
  └── performDraw()       → 绘制阶段
```

**1. Measure（测量）—— 确定每个 View 的宽高：**

- 从根 View（DecorView）开始，**自顶向下**递归调用 `measure()`
- 父 View 通过 `MeasureSpec`（模式 + 大小）向子 View 传递约束：
  - `EXACTLY`：精确值（父 View 确定了具体大小，如 match_parent 或具体 dp 值）
  - `AT_MOST`：最大值（子 View 不能超过父 View 给定的上限，如 wrap_content）
  - `UNSPECIFIED`：无约束（子 View 可以任意大小）
- `measure()` 内部调用 `onMeasure()`，子类可重写该方法自定义测量逻辑
- ViewGroup 需要遍历所有子 View 调用 `measureChild()` / `measureChildWithMargin()` 进行测量，并汇总自身尺寸

**2. Layout（布局）—— 确定每个 View 在父容器中的位置：**

- 从 `ViewRootImpl.performLayout()` 开始，同样**自顶向下**递归调用 `layout()`
- 父 View 根据子 View 的 `measuredWidth/Height` 确定其位置（`left, top, right, bottom`）
- `layout()` 方法中调用 `onLayout()`，ViewGroup 需要在此方法中确定每个子 View 的位置
- 经过此阶段后，每个 View 的四个坐标（`mLeft`, `mTop`, `mRight`, `mBottom`）被确定

**3. Draw（绘制）—— 将 View 的内容渲染到屏幕上：**

- 从 `ViewRootImpl.performDraw()` → `draw()` 触发，**自顶向下**递归执行
- `View.draw()` 内部按以下顺序执行：
  1. 绘制背景 → `drawBackground(canvas)`
  2. 保存 canvas 层（可选，用于 fading 效果）
  3. 绘制自身内容 → `onDraw(canvas)`
  4. 绘制子 View → `dispatchDraw(canvas)`
  5. 绘制装饰效果 → 前景、滚动条、分隔线等
  6. 绘制默认焦点（可选）
- 使用硬件加速时，通过 `DisplayList`（RenderNode）记录绘制指令，由 RenderThread 异步执行

**触发重绘的两种方式：**
- `invalidate()`：仅触发 draw 阶段（异步）
- `requestLayout()`：触发 measure + layout + draw 完整流程

**关键补充：**

| 概念 | 说明 |
|---|---|
| **RequestLayout** | 触发 measure + layout + draw |
| **Invalidate** | 仅触发 draw 阶段 |
| **硬件加速** | 开启后，绘制操作通过 OpenGL 生成 DisplayList，再由 RenderThread 执行，性能更优 |
| **VSYNC 同步** | 绘制操作与屏幕刷新率同步（通常 16.6ms/帧），避免撕裂 |
| **Surface** | 每个 Window 对应一个 Surface，最终绘制结果提交到 SurfaceFlinger 进行合成显示 |

**整体流程图：**

```
App 启动 / 数据变化 / invalidate() / requestLayout()
                    │
                    ▼
           ViewRootImpl.performTraversals()
                    │
        ┌───────────┼───────────┐
        ▼           ▼           ▼
   performMeasure  performLayout  performDraw
        │           │           │
        ▼           ▼           ▼
   measure()     layout()     draw()
        │           │           │
        ▼           ▼           ▼
   onMeasure()   onLayout()   onDraw()
   (递归子View)  (递归子View)  (递归子View)
                                      │
                                      ▼
                              Surface → SurfaceFlinger → 屏幕显示
```

简单来说：**Measure 定大小，Layout 定位置，Draw 定内容**，三者协同完成一帧 UI 的渲染。

---

#### View 的事件传递机制是怎样的？dispatchTouchEvent → onInterceptTouchEvent → onTouchEvent 的流转逻辑是什么？

**事件传递流程（从外到内）：**

```
Activity.dispatchTouchEvent() → Window.superDispatchTouchEvent() → DecorView.dispatchTouchEvent() → ViewGroup.dispatchTouchEvent() → ...
```

**ViewGroup.dispatchTouchEvent() 逻辑：**
1. 检查是否拦截（`onInterceptTouchEvent()`）
2. 若不拦截，遍历子 View，找到点击区域内的子 View
3. 对目标子 View 调用 `child.dispatchTouchEvent()`
4. 若子 View 不消费事件，回退到父 View 的 `onTouchEvent()`

**onInterceptTouchEvent()：**
- 仅 ViewGroup 有此方法，View 没有
- 返回 true 表示拦截，后续事件交给当前 ViewGroup 的 `onTouchEvent()`
- 一旦拦截，同一事件序列中不再调用 `onInterceptTouchEvent()`

**事件消费：**
- `onTouchEvent()` 返回 true 表示消费
- 若返回 false，事件向上传递给父 View 的 `onTouchEvent()`
- 若最终无人消费，Activity 的 `onTouchEvent()` 处理

**关键规则：**
- `ACTION_DOWN` 决定事件序列的消费者，一旦确定后续事件直接分发
- `requestDisallowInterceptTouchEvent(true)` 可阻止父 View 拦截（但 `ACTION_DOWN` 时无效）

---

#### 请谈谈 Choreographer 的工作原理？VSYNC 信号是如何驱动渲染的？

**Choreographer 工作原理：**

Choreographer（编舞者）是 Android 渲染管线的调度核心，负责协调输入、动画、布局、绘制的时序。

1. **VSYNC 信号**：硬件定时器产生的同步信号，通常每 16.6ms 一次（60fps），屏幕刷新率的节拍器
2. **VSYNC 分发**：`DisplayEventReceiver`（JNI 层）监听 VSYNC 信号，收到后回调 `Choreographer.doFrame()`
3. **doFrame() 执行流程**：
   - 计算掉帧数量（当前时间 - VSYNC 时间）
   - 按优先级依次执行：输入回调 → 动画回调 → 布局/绘制回调（`TraversalRunnable`）
4. **FrameCallback**：通过 `postFrameCallback()` 注册的回调在下一个 VSYNC 时执行

**双 VSYNC 机制（Android 4.1+）：**
- APP VSYNC：触发 UI 线程进行 measure/layout/draw
- SF VSYNC：触发 SurfaceFlinger 合成并送显
- 两者错开，给 UI 线程更多时间处理

**Project Butter 优化：**
- 双缓冲/三缓冲
- VSYNC 同步
- Choreographer 统一调度

---

#### SurfaceView 和 TextureView 的区别是什么？各自的适用场景和底层机制是什么？

| 特性 | SurfaceView | TextureView |
|------|------------|-------------|
| 底层实现 | 独立 Surface，独立绘制线程 | 基于 SurfaceTexture，通过 GL 纹理渲染 |
| 窗口层级 | 独立窗口（Window），位于主窗口之上/之下 | 在主窗口内，与普通 View 同一层级 |
| 硬件加速 | 不支持（独立 Surface） | 支持（作为纹理合成到主窗口） |
| View 特性 | 不支持 transform、alpha 等 | 支持所有 View 变换（旋转、缩放、透明度） |
| 内存占用 | 较低（双缓冲 Surface） | 较高（需要 GL 纹理缓冲区） |
| 同步性 | 可在非 UI 线程直接绘制 | 必须在 GL 线程操作 |

**适用场景：**
- **SurfaceView**：游戏、视频播放、相机预览等高性能场景，不需要 View 变换
- **TextureView**：需要动画变换的视频/相机预览（如视频通话中的小窗缩放），或需要与其他 View 混合显示

---

#### 你有自定义 View 的实践经验吗？请描述一下实现过程和关键要点。

**实现过程：**

1. **继承 View 或 ViewGroup**：根据需求选择继承 `View`（自绘控件）或 `ViewGroup`（容器控件）
2. **声明自定义属性**：在 `res/values/attrs.xml` 中定义 `<declare-styleable>`
3. **构造函数中获取属性**：通过 `TypedArray` 读取自定义属性
4. **重写核心方法**：
   - `onMeasure()`：计算 View 尺寸，注意处理 `wrap_content` 场景
   - `onLayout()`（ViewGroup）：确定子 View 位置
   - `onDraw()`：使用 Canvas/Paint 绘制内容
   - `onTouchEvent()`：处理触摸事件
5. **性能优化**：
   - 避免在 `onDraw()` 中创建对象
   - 使用 `invalidate()` 局部刷新
   - 使用硬件加速


**关键要点：**
- `onMeasure()` 中 `setMeasuredDimension()` 必须被调用
- `wrap_content` 需要给默认尺寸，否则等同于 `match_parent`
- 自定义 ViewGroup 需正确处理事件分发
- 使用 `save()`/`restore()` 管理 Canvas 状态


**硬件加速为什么性能更优？**

- **GPU 并行计算**：绘制本质是对大量像素做相同数学运算，GPU 有数百到数千个核心，天然擅长并行像素运算，而 CPU 仅 4~8 个核心，只能串行计算
- **DisplayList 缓存**：CPU 将绘制指令录制为 DisplayList，GPU 执行后缓存；后续重绘时 GPU 直接复用缓存，无需 CPU 重新录制，实现增量更新
- **RenderThread 流水线并行**：CPU 录制下一帧时，GPU 通过独立 RenderThread 同时执行上一帧，两者互不阻塞
- **动画/变换零开销**：平移、旋转、缩放等操作，GPU 仅需修改变换矩阵对已缓存纹理做变换，无需重新绘制像素

---

### 5. 系统架构与 IPC

#### 请谈谈你对 Android 系统架构的理解？Linux 内核层、硬件抽象层、运行时框架层、应用框架层各自的作用？

**Android 系统架构（自上而下）：**

1. **应用层（Applications）**：系统应用（Launcher、Phone）和第三方应用

2. **应用框架层（Application Framework）**：
   - 提供 Java/Kotlin API：ActivityManager、WindowManager、PackageManager 等
   - 系统服务（System Services）：AMS、WMS、PMS 等核心服务
   - Binder IPC 机制的 Java 层封装

3. **运行时框架层（Runtime / Android Runtime）**：
   - **ART**：将 DEX 字节码编译为机器码（AOT + JIT），支持 GC
   - **核心库**：java.lang、java.io、android.* 等
   - **NDK**：Native 开发支持

4. **硬件抽象层（HAL）**：
   - 标准化硬件接口（C/C++ 头文件定义）
   - 将硬件实现细节封装为统一接口供上层调用
   - 如 Camera HAL、Audio HAL、Sensor HAL

5. **Linux 内核层**：
   - 进程管理、内存管理、文件系统、网络协议栈
   - 驱动程序（显示、相机、蓝牙等）
   - 安全机制（SELinux、权限模型）
   - 电源管理

---

#### Android 中常用的 IPC 机制有哪些？请比较 Binder、AIDL、Messenger、ContentProvider 的优缺点。

| IPC 方式 | 特点 | 优点 | 缺点 |
|---------|------|------|------|
| **Binder** | Android 核心 IPC 机制，基于内存映射 | 高性能（一次拷贝）、安全性好（UID/PID 校验）、支持同步/异步 | 使用复杂、传输数据有限（1MB 缓冲区） |
| **AIDL** | Binder 的 IDL 封装工具 | 自动生成 Binder 桩代码、支持复杂接口、跨语言 | 接口变更需同步更新、学习成本较高 |
| **Messenger** | 基于 Handler + Binder 的消息传递 | 简单易用、线程安全（串行处理） | 性能较低（串行）、只支持消息传递、不支持 RPC 调用 |
| **ContentProvider** | 基于文件/URI 的数据共享 | 适合数据共享、支持 CRUD 操作、权限控制完善 | 性能一般、仅适合数据共享不适合 RPC |

**补充：**
- **Socket**：适合网络通信场景，跨网络也可用
- **Intent**：适合组件间松耦合通信，不适合频繁数据传输
- **MMKV / 文件共享**：适合简单数据共享

---

### 6. Jetpack 与主流框架

#### 请谈谈 ViewModel 的设计原理？它是如何在配置变更时保持数据的？

**ViewModel 设计原理：**

1. **存储机制**：ViewModel 存储在 `ViewModelStore` 中，以 `HashMap<String, ViewModel>` 形式保存
2. **配置变更存活**：当 Activity 因屏幕旋转等配置变更重建时：
   - `ActivityThread` 调用 `Activity.retainNonConfigurationInstances()`
   - 保存 `NonConfigurationInstances` 对象，其中持有 `ViewModelStore`
   - 新 Activity 重建时从 `LastNonConfigurationInstances` 恢复 `ViewModelStore`
3. **真正销毁**：只有 `onFinish()` 或系统杀死进程时才调用 `ViewModel.onCleared()` 清理资源

**ViewModelProvider 流程：**
1. `ViewModelProvider(this).get(MyVM::class.java)`
2. 通过 `ViewModelStoreOwner`（Activity/Fragment）获取 `ViewModelStore`
3. 先从 Store 中查找，存在则直接返回
4. 不存在则通过 `ViewModelProvider.Factory` 创建并存入 Store

**关键设计：**
- ViewModel 不持有 Activity/Context 引用，避免内存泄漏
- 需要 Application Context 时使用 `AndroidViewModel`
- `SavedStateHandle` 可在进程被杀后恢复数据

---

#### Room 数据库的架构设计是怎样的？请说说 @Entity、@Dao、@Database 的使用方式。

**Room 架构设计：**

Room 是 SQLite 的抽象层，提供编译期 SQL 校验和流畅的 API。

三层架构：
- `@Entity`：数据表映射，定义表结构
- `@Dao`：数据访问对象，定义 CRUD 操作
- `@Database`：数据库持有者，管理数据库和 DAO

**使用方式：**

```kotlin
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "email") val email: String,
    val age: Int
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE age > :minAge")
    fun getUsersOlderThan(minAge: Int): Flow<List<User>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)
    
    @Delete
    suspend fun delete(user: User)
}

@Database(entities = [User::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}
```

**高级特性：**
- `@Relation`：表关联查询
- `@Transaction`：事务操作
- `TypeConverter`：自定义类型转换
- `Migration`：数据库版本迁移
- `Flow` / `LiveData`：响应式数据观察

---

#### Retrofit 和 OkHttp 的协作原理是什么？拦截器链是如何工作的？

**协作原理：**

Retrofit 是**网络请求的封装层**（负责接口定义、参数组装、结果解析），OkHttp 是**网络请求的执行层**（负责连接管理、数据传输）。

1. Retrofit 通过动态代理（`Proxy.newProxyInstance`）将接口方法转换为 `ServiceMethod`
2. `ServiceMethod` 解析注解（`@GET`、`@POST`、`@Body` 等）构建 HTTP 请求
3. 通过 `OkHttpCall` 封装为 OkHttp 的 `Request` 对象
4. 调用 `OkHttpClient.newCall(request).execute()` 执行请求
5. OkHttp 返回 `Response` 后，Retrofit 通过 `Converter`（如 GsonConverterFactory）反序列化结果

**OkHttp 拦截器链（责任链模式）：**

```
RetryAndFollowUpInterceptor → BridgeInterceptor → CacheInterceptor → ConnectInterceptor → NetworkInterceptor → 网络请求
```

| 拦截器 | 职责 |
|--------|------|
| `RetryAndFollowUpInterceptor` | 失败重试和重定向跟随 |
| `BridgeInterceptor` | 添加请求头（Host、Content-Type 等）、Cookie、解压 |
| `CacheInterceptor` | 缓存策略，读取/写入缓存 |
| `ConnectInterceptor` | 建立连接（获取/复用 Connection） |
| 自定义 `Interceptor` | 应用拦截器（addInterceptor）和网络拦截器（addNetworkInterceptor） |

**调用链**：`RealInterceptorChain.proceed()` 递归调用下一个拦截器，形成链式处理

---

#### Glide 的缓存机制是怎样的？请描述其加载流程和设计模式。

**四级缓存：**
1. **活动资源缓存（Active Resources）**：当前正在显示的图像
2. **内存缓存（Memory Cache）**：最近加载过的图像（LruCache）
3. **磁盘缓存（Disk Cache）**：原始数据（`DataCache`）和转换后的资源（`ResourceCache`）
4. **远程数据源**：网络/本地文件

**加载流程：**
1. `Glide.with(context).load(url).into(imageView)`
2. 获取 `RequestManager`（绑定生命周期）
3. 创建 `RequestBuilder`，调用 `into()` 时：
   - 检查 Active Resources → 命中则直接显示
   - 检查 Memory Cache → 命中则显示并移入 Active
   - 检查 Disk Cache → 命中则解码、转换、存入 Memory Cache 并显示
   - 从数据源加载 → 解码、转换、存入 Disk Cache + Memory Cache + Active 并显示
4. `into()` 时会将 Target 绑定到 View 的 `ViewTreeObserver`，确保 View 尺寸确定后加载合适尺寸的图片

**设计模式：**
- **Builder 模式**：`RequestBuilder` 链式构建请求
- **观察者模式**：`Target` 监听加载结果
- **策略模式**：不同的 `DataFetcher`（网络、文件、资源等）
- **工厂模式**：`ModelLoaderFactory` 创建对应的 `ModelLoader`
- **责任链模式**：`Engine` 中的多级缓存查找

---

#### AsyncListDiffer 的原理是什么？它是如何实现列表高效 diff 更新的？

**原理：**

AsyncListDiffer 基于 **Eugene W. Myers 差分算法**，在后台线程计算新旧列表的差异，生成最小更新操作集。

**工作流程：**
1. 提交新列表：`submitList(newList)`
2. 在后台线程执行 `DiffUtil.calculateDiff()`（Myers 算法）
3. 计算出一组 `ListUpdateCallback` 操作：insert、remove、move、change
4. 切回主线程，将操作应用到 `AsyncListAdapter`
5. RecyclerView 根据操作执行局部动画更新

**关键要点：**
- 必须正确实现 `areItemsTheSame()`（判断是否同一项）和 `areContentsTheSame()`（判断内容是否变化）
- `areItemsTheSame` 返回 true 但 `areContentsTheSame` 返回 false 时，会触发 change 动画
- 使用 `getCurrentList()` 获取当前列表（返回不可变快照）
- 避免频繁提交大数据集，可合并多次变更

---

### 7. 本地数据存储

#### SQLite 在 Android 中的使用方式？与 Room 的关系是什么？

**SQLite 在 Android 中的使用：**
- 通过 `SQLiteOpenHelper` 管理数据库创建和版本升级
- 使用 `SQLiteDatabase` 执行 CRUD：`insert()`、`query()`、`update()`、`delete()`、`rawQuery()`
- 使用 `ContentValues` 封装数据，`Cursor` 遍历结果

**Room 与 SQLite 的关系：**
- Room 是 SQLite 的**高级抽象层**，底层仍使用 SQLite
- Room 在编译期校验 SQL 语句，避免运行时错误
- Room 提供注解驱动的 API，减少样板代码
- Room 支持响应式数据（Flow/LiveData），SQLite 原生 API 不支持
- Room 提供 `Migration` 机制管理数据库版本升级
- Room 是 Google 推荐的 Android 持久化方案

---

#### MMKV 的实现原理是什么？相比 SharedPreferences 有哪些优势？

**MMKV 实现原理：**

MMKV 是腾讯开源的轻量级 KV 存储库，基于**内存映射（mmap）**实现。

1. **mmap 内存映射**：将文件映射到内存空间，对内存的修改直接反映到文件，无需手动 `write()`
2. **序列化**：使用 protobuf 协议进行高效的序列化/反序列化
3. **增量更新**：修改数据时只写入变更部分，不需要全量写入
4. **双 Buffer 机制**：一个 Buffer 用于读写，另一个用于扩容时的数据迁移
5. **CRC 校验**：防止数据损坏，保证数据完整性

**相比 SharedPreferences 的优势：**

| 特性 | SharedPreferences | MMKV |
|------|------------------|------|
| 性能 | 全量写入，每次 commit/apply 写整个 XML | mmap + 增量写入，微秒级 |
| 线程安全 | 有锁竞争 | 无锁设计（Futex） |
| 数据格式 | XML（冗余大） | protobuf（紧凑高效） |
| 启动耗时 | 首次加载全量数据到内存 | mmap 按需加载 |
| ANR 风险 | commit 同步写磁盘可能 ANR | 无 ANR 风险 |

**MMKV 是线程安全的吗？如何实现？**

MMKV 在多线程和多进程场景下都是安全的：

- **单进程模式（默认）**：使用 `pthread_mutex_t` 互斥锁，保证同一进程内多线程安全
- **多进程模式（`MMKV_MULTI_PROCESS`）**：使用 `fcntl` 文件锁（`F_RDLCK`/`F_WRLCK`），保证跨进程安全
- **数据完整性**：每次写入后计算 CRC32 校验值，读取时校验，数据损坏时自动恢复到上次正确状态
- **读写流程**：写入时加锁 → 序列化到 mmap 缓冲区 → `msync()` 刷盘 → 释放锁；读取时同样加锁保证一致性

注意：MMKV 即使在单进程模式下读操作也加互斥锁（而非读写锁），因为扩容时 `munmap` + `mmap` 会导致旧内存地址失效，读写锁可能引发数据不一致。

---

#### 你了解 Realm 吗？它与 SQLite/Room 相比有什么特点？

**Realm 特点：**
- **对象数据库**：直接存储对象，无需 ORM 映射
- **零拷贝**：通过内存映射直接访问数据，无需反序列化
- **跨平台**：支持 Android、iOS、.NET
- **响应式**：内置 `RealmResults` 自动通知变更
- **加密**：支持 AES-256 加密

**与 SQLite/Room 对比：**

| 特性 | Realm | SQLite/Room |
|------|-------|-------------|
| 数据模型 | 对象存储，无需表定义 | 关系型，需要表结构 |
| 性能 | 直接内存访问，读取快 | 需要 SQL 解析和 Cursor 转换 |
| 包体积 | 较大（~几 MB） | 较小（系统内置） |
| 学习成本 | 低（直接操作对象） | 中等（需理解 SQL） |
| 社区/维护 | 已停止维护（2022年 MongoDB 收购后） | Google 官方推荐，活跃维护 |
| 迁移 | 复杂 | Room Migration 成熟 |

**注意**：Realm 已被 MongoDB 收购并整合为 MongoDB Realm / Atlas Device SDK，新项目不建议使用。

---

### 9. 架构设计

#### 请对比 MVVM 和 MVI 架构的优缺点？你在项目中是如何选型的？

| 特性 | MVVM | MVI |
|------|------|-----|
| 数据流 | 双向/多向 | 单向（Unidirectional） |
| 状态管理 | 多个 LiveData/StateFlow 分散管理 | 单一 State 对象集中管理 |
| View 层 | 观察多个 LiveData | 订阅单一 State |
| 可预测性 | 较低（多数据源可能冲突） | 高（状态变更路径明确） |
| 复杂度 | 中等 | 状态定义复杂，逻辑清晰 |
| 适用场景 | 中小项目、简单页面 | 大型项目、复杂交互页面 |

**MVVM 优点**：简单直观、学习成本低、与 Jetpack 组件天然配合
**MVVM 缺点**：多 LiveData 可能导致状态不一致、事件处理不够优雅

**MVI 优点**：单一数据源、状态可预测、易于调试和测试、天然支持 UI 状态还原
**MVI 缺点**：状态类可能膨胀、频繁重建 State 对象（可用 `copy()` 优化）、学习曲线较陡

**选型建议：**
- 简单 CRUD 页面 → MVVM
- 复杂多状态交互页面（如交易、IM）→ MVI
- 团队熟悉度也是重要考量因素

---

#### 什么是 Clean Architecture？你是如何在 Android 项目中落地的？

**Clean Architecture 核心思想：**

将应用分为多个层次，每层职责明确，依赖方向从外到内（外层依赖内层，内层不知道外层）。

**分层结构：**
1. **Presentation 层**：UI + ViewModel，负责展示和用户交互
2. **Domain 层**：Use Case（业务用例）+ Entity（领域模型），纯 Kotlin 模块，不依赖 Android 框架
3. **Data 层**：Repository 实现 + DataSource（Remote/Local），负责数据来源

**依赖规则：**
- Domain 层不依赖任何外层
- Data 层实现 Domain 层定义的 Repository 接口
- Presentation 层通过 ViewModel 调用 Domain 层的 Use Case

**Android 项目落地：**
```
app/                    → Presentation 层
domain/                 → Domain 层（纯 Kotlin 模块）
data/                   → Data 层
```

- 使用 Gradle multi-module 实现模块隔离
- Domain 层定义接口（Repository 接口），Data 层提供实现
- 通过 DI（Hilt/Dagger）注入依赖
- Use Case 封装单一业务逻辑，便于复用和测试

**优点：**
- **独立于框架**：UI、数据库、网络等外部细节不渗透到业务逻辑层，更换框架影响范围极小
- **高度可测试**：业务逻辑不依赖 Android SDK，可在纯 JVM 环境下单元测试
- **独立于 UI**：业务逻辑不关心界面是 Activity、Fragment 还是 Compose
- **职责清晰**：每层有明确职责，降低"上帝类"出现的概率
- **长期可维护性强**：分层隔离使修改某一模块不易引发连锁反应

**缺点：**
- **复杂度高，样板代码多**：一个简单功能可能跨越多层，文件数量和代码量大幅增加
- **学习曲线陡峭**：理解依赖方向、各层职责和数据流转需要较长时间
- **小项目过度设计**：功能简单的项目收益远不及引入的复杂度
- **数据映射成本高**：各层间传递需做 DTO 转换，增加重复代码
- **DI 几乎必不可少**：需引入 Hilt/Dagger 等 DI 框架，进一步增加复杂度

**适用场景：** 大型团队、长期维护、业务逻辑复杂、需要高可测试性的项目推荐；小型项目、快速原型不推荐。

---

#### 请描述你做过的模块化/组件化重构经验？遇到了哪些问题？如何解决的？

**重构经验：**

1. **模块拆分**：按业务域拆分为独立 Module（如 user、chat、trade、base）
2. **接口下沉**：公共接口定义在 `base` 或 `api` 模块，实现在 `impl` 模块
3. **依赖管理**：使用 `api` / `implementation` 控制依赖传递

**常见问题与解决方案：**

| 问题 | 解决方案 |
|------|---------|
| 模块间循环依赖 | 接口下沉到公共模块，依赖倒置 |
| 资源冲突 | 资源前缀（`resourcePrefix`） |
| 路由跳转 | ARouter / 自建路由框架，通过 URL 或 Class 跳转 |
| 数据共享 | 通过接口暴露 + DI 注入，避免直接依赖 |
| 编译速度 | 模块独立编译、按需编译、Gradle 缓存优化 |
| 初始化依赖 | Application Startup 库或自建初始化管理器，拓扑排序 |

---

### 10. 性能优化

#### 你是如何排查和解决 ANR 问题的？请描述你的排查流程。

**排查流程：**

1. **收集 ANR 日志**：
   - `/data/anr/traces.txt`（系统自动生成）
   - `adb bugreport` 获取完整 bugreport
   - 线上通过 APM 采集主线程堆栈

2. **分析 traces.txt**：
   - 查看主线程堆栈，定位阻塞位置
   - 关注 `main` 线程的 `waiting`/`blocked` 状态
   - 检查是否有锁竞争（`held by` 信息）

3. **常见 ANR 类型**：
   - **输入超时**（5秒）：主线程被耗时操作阻塞
   - **BroadcastReceiver 超时**（10秒）：`onReceive()` 执行过久
   - **Service 超时**（20秒，前台 3 分钟）：`onCreate()`/`onStartCommand()` 过久
   - **ContentProvider 超时**：`onCreate()` 过久

4. **解决策略**：
   - 耗时操作移到工作线程
   - 避免主线程 IO 操作（SP 写入、数据库操作）
   - 避免主线程大量对象创建导致 GC 停顿
   - 使用 `StrictMode` 检测主线程违规操作

5. **预防手段**：
   - 线上 ANR 监控（堆栈采集 + 聚合分析）
   - 代码审查关注主线程操作
   - 使用协程替代 AsyncTask/Thread

---

#### 内存泄漏的常见场景有哪些？你是如何使用 LeakCanary 等工具定位和解决的？

**常见内存泄漏场景：**
1. **静态引用**：静态变量持有 Activity/Context
2. **单例模式**：单例持有 Activity 引用
3. **内部类/匿名类**：非静态内部类持有外部类引用（如 Handler、Runnable）
4. **未取消的回调**：注册了监听器但未反注册
5. **资源未关闭**：Cursor、InputStream、BroadcastReceiver
6. **WebView**：WebView 持有 Activity 引用
7. **Thread/Timer**：后台线程持有 Activity 引用

**LeakCanary 工作原理：**
1. 监控 Activity/Fragment 的 `onDestroy()`
2. 使用 `WeakReference` + `ReferenceQueue` 检测对象是否被回收
3. 等待 5 秒后检查，若未被回收则判定泄漏
4. 通过 `HeapDump`（HPROF）获取堆快照
5. 使用 `Shark` 库分析引用链，找到 GC Root 到泄漏对象的路径
6. 输出泄漏链路，定位到具体代码位置

**解决方式：**
- 使用 `WeakReference` 持有 Context
- 在 `onDestroy()` 中取消回调、移除 Handler 消息
- 使用 `Lifecycle` 自动管理资源释放
- WebView 使用 Application Context 创建

---

#### 冷启动优化你是怎么做的？延迟初始化、按需注册的策略如何落地？

**冷启动优化策略：**

1. **启动耗时分析**：
   - `adb shell am start -W` 测量启动时间
   - Systrace / Perfetto 分析启动流程
   - 代码插桩统计各阶段耗时

2. **Application.onCreate() 优化**：
   - **延迟初始化**：非首屏必需的 SDK 延迟到首帧渲染后初始化
   - **按需注册**：根据启动页面判断是否需要初始化某些模块
   - **异步初始化**：无依赖关系的 SDK 放到工作线程并行初始化
   - 使用 `App Startup` 库管理初始化依赖关系

3. **首屏 Activity 优化**：
   - 减少布局层级（ConstraintLayout 扁平化）
   - 避免首屏主线程 IO
   - 预加载数据（在上一个页面提前请求）

4. **主题优化**：
   - 使用 `windowDisablePreview` 或自定义 Theme 避免白屏
   - 使用 Window Background 作为占位图

---

#### 针对 App 渲染帧率，你是如何分析和优化的？如何避免过度绘制？

**分析方法：**
- **开发者选项 → GPU 呈现模式分析**：可视化每帧耗时
- **Systrace / Perfetto**：分析渲染管线各阶段耗时
- **`adb shell dumpsys gfxinfo`**：统计帧渲染时间
- **Profile GPU Rendering**：柱状图分析

**优化策略：**
1. **减少过度绘制**：
   - 开发者选项 → 显示布局边界 / 过度绘制
   - 移除不必要的背景色（如 Theme 默认背景）
   - 使用 `clipRect()` 避免重叠区域重复绘制
   - 使用 `merge` 标签减少布局层级

2. **减少布局层级**：
   - 使用 ConstraintLayout 替代嵌套 LinearLayout
   - 使用 merge 标签减少一层 ViewGroup

3. **异步/延迟加载**：
   - 图片异步加载和缓存
   - 列表项延迟初始化

4. **避免主线程耗时操作**：
   - 减少 `onMeasure`/`onLayout` 中的计算
   - 避免在 `onDraw` 中创建对象

---

#### 包体积优化你做过哪些工作？从哪些维度入手？

**优化维度：**

1. **代码优化**：
   - 开启 ProGuard/R8 混淆（`minifyEnabled true`）
   - 移除未使用代码（`shrinkResources true`）
   - 使用 `lint` 检测无用代码
   - 避免引入不必要的依赖库

2. **资源优化**：
   - 图片压缩（TinyPNG、WebP 格式）
   - 使用 VectorDrawable 替代位图
   - 移除未使用的资源
   - 使用 `resConfigs` 限制语言资源
   - 动态下发资源（App Bundle）

3. **SO 库优化**：
   - 移除不需要的 ABI（如只保留 arm64-v8a）
   - SO 压缩存储（`extractNativeLibs=false`，Android 6.0+）
   - 按需加载 SO

4. **其他**：
   - 使用 APK Analyzer 分析包组成
   - 使用 App Bundle（AAB）按需分发
   - 资源去重（相同图片不同目录）

---

#### 耗电优化你做过吗？从哪些维度入手？

**优化维度：**

1. **网络请求**：
   - 合并请求，减少唤醒次数
   - 使用 GCM/FCM 推送替代轮询
   - Wi-Fi 批量传输，移动网络减少请求

2. **后台任务**：
   - 使用 `WorkManager` 管理后台任务（系统调度，批量执行）
   - 避免使用 `AlarmManager.setExact()` 频繁唤醒
   - 使用 `JobScheduler` 的约束条件（充电时、有网络时）

3. **定位**：
   - 降低定位精度（`PASSIVE_PROVIDER`）
   - 减少定位频率
   - 不需要时关闭定位

4. **WakeLock**：
   - 避免长时间持有 WakeLock
   - 使用 `PowerManager.PARTIAL_WAKE_LOCK` 替代全屏 WakeLock
   - 确保在 `finally` 中释放

5. **Alarm 优化**：
   - 使用 `setInexactRepeating()` 替代 `setRepeating()`
   - 合并 Alarm 批量执行

---

#### Profiler、MAT、Instruments、Systrace、TraceView、Perfetto 这些工具你分别怎么使用？各自适用于什么场景？

| 工具 | 用途 | 适用场景 |
|------|------|---------|
| **Android Profiler** | CPU、内存、网络、电量实时监控 | 日常开发调试，快速定位性能瓶颈 |
| **MAT (Memory Analyzer Tool)** | 分析 HPROF 堆转储文件 | 内存泄漏分析、大对象查找、GC Root 引用链 |
| **Instruments** | macOS/iOS 性能分析工具 | iOS 端性能分析（Time Profiler、Allocations） |
| **Systrace** | 系统级 Trace 分析 | 分析渲染、调度、系统服务性能（命令行工具） |
| **TraceView** | Java 层方法耗时分析 | 方法级别耗时分析（已废弃，推荐 Perfetto） |
| **Perfetto** | 新一代系统 Trace 工具 | 替代 Systrace，支持更大文件、Web UI、SQL 查询 |

**使用建议：**
- 日常开发用 **Profiler** 实时监控
- 内存问题用 **MAT** 或 Android Studio Memory Profiler
- 帧率/卡顿问题用 **Perfetto**（推荐）或 Systrace
- 方法耗时用 **Profiler CPU** 或 Perfetto

---

#### 启动与首帧优化中，延迟初始化和资源瘦身的策略你是如何实施的？

**延迟初始化策略：**
1. **分级初始化**：
   - P0（必须同步）：Crash 监控、基础网络库
   - P1（首帧后）：推送、统计、非核心 SDK
   - P2（按需）：进入对应页面时才初始化
2. **实现方式**：
   - 首帧回调（`onWindowFocusChanged` 或 `View.post`）后初始化 P1
   - 页面路由拦截器中初始化 P2
   - 使用 `ContentProvider` + `App Startup` 管理依赖

**资源瘦身策略：**
1. 使用 `lint --remove` 清除无用资源
2. 图片统一使用 WebP 格式
3. 动画使用 Lottie 替代帧动画
4. 使用 `resConfigs "zh"` 移除多语言资源
5. 第三方库资源裁剪（`exclude` 不需要的资源）

---

#### 列表与图片优化中，懒加载、缓存与尺寸约束的具体做法是什么？如何避免过度重绘？

**懒加载：**
- RecyclerView `setHasFixedSize(true)` + `setItemViewCacheSize()`
  - `setHasFixedSize(true)`：告诉 RecyclerView 尺寸不随数据变化而改变，跳过 content 变化时的重新 measure/layout，减少布局开销。前提是 RecyclerView 使用固定尺寸或 match_parent，不能用 wrap_content。
  - `setItemViewCacheSize(n)`：将 View Cache 大小从默认 2 调整为 n。View Cache 保存刚滑出屏幕的 ViewHolder，按 position 直接复用且不调用 onBindViewHolder，适合绑定操作较重的场景，代价是增加内存占用。
- 图片按需加载，滑出屏幕取消加载（Glide 自动处理）
- 分页加载（Paging 3 / 手动分页）

**缓存策略：**
- 内存缓存：Glide LruCache（默认 1/8 可用内存）
- 磁盘缓存：Glide DiskLruCache
- RecyclerView 缓存：`RecycledViewPool` 复用 ViewHolder

**尺寸约束：**
- 指定图片加载尺寸（`override(width, height)`）避免加载原图
- 使用 `centerCrop` / `fitCenter` 避免尺寸不匹配导致的重绘
- 列表项固定高度，避免图片加载后重新布局

**避免过度重绘：**
- 列表项背景与容器背景一致，避免叠加绘制
- 使用 `setTag` 避免重复设置
- 减少 `notifyDataSetChanged()`，使用 DiffUtil 精准更新

---

### 11. 工程化建设与架构规范

#### Gradle 编译流程你了解多少？你有过 Gradle 插件开发或构建优化的经验吗？

**Gradle 编译流程：**

1. **Initialization（初始化）**：解析 `settings.gradle`，确定参与构建的模块
2. **Configuration（配置）**：执行各模块的 `build.gradle`，构建 Task 依赖图
3. **Execution（执行）**：按依赖图执行 Task，生成构建产物

**Gradle 插件开发：**
- 实现 `Plugin<Project>` 接口
- 在 `apply()` 方法中注册 Task、Extension
- 通过 `project.afterEvaluate {}` 在配置完成后修改属性
- 发布到本地 Maven 或 Gradle Plugin Portal

**构建优化经验：**
- 开启 Gradle Daemon（常驻进程）
- 开启并行编译（`org.gradle.parallel=true`）
- 开启构建缓存（`org.gradle.caching=true`）
- 使用 `configuration cache`（Gradle 6.6+）
- 按需编译（`-x` 排除不需要的 Task）
- 依赖替换/镜像源加速
- 避免在 Configuration 阶段做耗时操作

---

#### Android 多渠道打包你是如何实现的？ProductFlavors 的配置和注意事项？

**ProductFlavors 配置：**

```kotlin
android {
    flavorDimensions += "channel"
    productFlavors {
        create("google") {
            dimension = "channel"
            applicationIdSuffix = ".google"
            buildConfigField("String", "CHANNEL", "\"google\"")
        }
        create("huawei") {
            dimension = "channel"
            applicationIdSuffix = ".huawei"
        }
    }
}
```

**注意事项：**
- `dimension` 必须相同才能组合（多维度时可交叉组合）
- 每个 Flavor 可有自己的 `src/main/java/` 源码和 `res/` 资源
- 使用 `resValue` 和 `buildConfigField` 区分渠道配置
- 配合 `signingConfigs` 为不同渠道使用不同签名
- 使用 `missingDimensionStrategy` 处理维度缺失

---

#### 你是如何推动代码规范落地的？CI/CD 流程建设中做了哪些工作？

**代码规范落地：**
1. **静态检查**：
   - Ktlint / Detekt 检查 Kotlin 代码风格
   - Android Lint 检查常见问题
   - 自定义 Lint 规则检查业务规范
2. **代码格式化**：
   - `.editorconfig` 统一编辑器配置
   - Pre-commit Hook + Ktlint 自动格式化
3. **Code Review**：
   - MR/PR 必须至少 1 人 Approve
   - Review 清单（性能、安全、规范）

**CI/CD 建设：**
1. **持续集成**：
   - 每次提交触发 Lint + 单元测试
   - MR 合并触发编译 + UI 测试
2. **持续交付**：
   - 自动打包（Firebase App Distribution / 蒲公英）
   - 版本号自动管理
3. **工具链**：Jenkins / GitHub Actions / GitLab CI

---

#### 单元测试和 UI 测试你是怎么做的？JUnit、MockK、Espresso/Compose Testing 的使用经验？

**单元测试：**
- **JUnit 4/5**：`@Test`、`@Before`、`@After` 注解
- **MockK**：Kotlin 友好的 Mock 框架
  - `mockk<Class>()` 创建 Mock 对象
  - `every { } returns` 定义行为
  - `verify { }` 验证调用
  - 支持协程 Mock（`coEvery`、`coVerify`）
- **测试范围**：ViewModel、UseCase、Repository（数据层）

**UI 测试：**
- **Espresso**：
  - `onView(withId(R.id.xxx)).perform(click())`
  - `check(matches(isDisplayed()))`
  - 支持 IdlingResource 处理异步
- **Compose Testing**：
  - `createComposeRule()` 创建测试规则
  - `onNodeWithText("xxx").performClick()`
  - `onNodeWithTag("tag").assertIsDisplayed()`

**实践建议：**
- ViewModel 层必须有单元测试
- UseCase 层必须有单元测试
- 关键 UI 路径需要 UI 测试覆盖
- 使用 `turbine` 测试 Flow

---

### 12. 高级技术

#### JNI 的原理是什么？Java 层和 Native 层是如何通信的？

**JNI（Java Native Interface）原理：**

JNI 是 Java/Kotlin 与 C/C++ 之间的桥梁，允许 Java 代码调用 Native 函数，反之亦然。

**Java → Native：**
1. Java 层声明 `native` 方法：`external fun stringFromJNI(): String`
2. C/C++ 实现对应函数（函数名遵循 JNI 命名规则）
3. 通过 `System.loadLibrary()` 加载 SO 库
4. JVM 通过 JNI 查找并调用 Native 函数

**Native → Java：**
1. 通过 `JNIEnv*` 指针调用 Java 方法
2. `FindClass()` 查找类，`GetMethodID()` 获取方法 ID
3. `CallVoidMethod()` / `CallObjectMethod()` 等调用 Java 方法

**关键概念：**
- `JNIEnv`：线程局部环境指针，提供 JNI 函数表
- 全局引用（`NewGlobalRef`）/ 局部引用 / 弱全局引用
- JNI 注册：静态注册（函数名匹配）vs 动态注册（`RegisterNatives`）

---

#### APM 监控体系你是如何建设的？崩溃分析的方案是什么？

**APM 监控体系：**

1. **崩溃监控**：
   - Java 异常：`Thread.UncaughtExceptionHandler`
   - Native 崩溃：Signal 处理（SIGSEGV 等）
   - ANR 监控：监控主线程 Looper 消息执行时间
   - 采集堆栈、设备信息、用户操作路径

2. **性能监控**：
   - 启动耗时：`ActivityTaskManager` + 首帧回调
   - 帧率监控：`Choreographer.FrameCallback` 计算掉帧
   - 内存监控：`Debug.getMemoryInfo()` 定期采集
   - 网络监控：OkHttp Interceptor 采集请求耗时

3. **卡顿监控**：
   - 使用 `Looper.setMessageLogging()` 监控消息执行时长
   - 超过阈值（如 300ms）采集主线程堆栈

**崩溃分析方案：**
- 采集完整堆栈 + 设备信息 + App 版本 + 用户操作
- 服务端聚合（相同堆栈归为一类）
- 优先级排序（影响用户数 × 崩溃频率）
- 修复验证 + 灰度发布

---

#### 动态化技术你了解哪些？热修复和插件化的原理分别是什么？

**热修复（Hot Fix）：**

原理：在运行时替换有 Bug 的类或资源。

- **类替换方案**：利用 `DexClassLoader` 加载补丁 DEX，通过修改 `PathClassLoader` 的 `dexElements` 数组，将补丁类插入到类加载顺序的前面（Tinker 的 bsdiff 差分方案）
- **资源替换方案**：修改 `AssetManager` 的资源路径，优先加载补丁资源
- **Native 层修复**：替换 SO 库中的函数指针

**插件化（Plugin）：**

原理：将部分功能以 APK 形式动态加载运行。

- **Activity 插件化**：通过 Hook `Instrumentation` 或 `ActivityThread`，将插件 APK 中的 Activity 骗过系统检查
- **资源隔离**：每个插件使用独立的 `Resources` 对象，避免资源 ID 冲突
- **代表框架**：RePlugin、Shadow（腾讯）、VirtualAPK（滴滴）

---


#### 网络、存储、埋点、日志、路由、配置等通用能力如何抽离为独立模块？设计思路和抽象方式？

**设计思路：**

1. **接口抽象**：定义通用接口层（如 `ILogger`、`IStorage`、`IRouter`），上层依赖接口而非实现
2. **模块隔离**：每个通用能力独立为一个 Gradle Module
3. **依赖注入**：通过 DI 框架（Hilt）注入具体实现，支持替换

**模块划分：**
```
lib-network/     → 网络模块（Retrofit + OkHttp 封装）
lib-storage/     → 存储模块（MMKV + Room 封装）
lib-tracker/     → 埋点模块（定义埋点协议，多渠道上报）
lib-log/         → 日志模块（分级日志 + 文件日志 + 上传）
lib-router/      → 路由模块（URL 路由 + 服务发现）
lib-config/      → 配置模块（远程配置 + 本地缓存 + 灰度）
```

**关键原则：**
- 依赖倒置：上层模块定义接口，底层模块实现
- 单一职责：每个模块只负责一个领域
- 可测试性：接口可 Mock，便于单元测试

---

#### 路由框架的选型和实现？路由表管理、拦截器、动态路由如何处理？

**路由框架选型：**
- **ARouter**（阿里）：功能全面，社区活跃，但维护减缓
- **自建路由**：轻量可控，适合深度定制

**核心实现：**
1. **路由表管理**：
   - 编译期注解处理器（APT/KSP）收集路由信息
   - 生成路由表（`Map<String, RouteMeta>`）
   - 按模块分组，按需加载

2. **拦截器**：
   - 全局拦截器：登录检查、权限校验
   - 路由级拦截器：特定页面的前置条件
   - 拦截器链：责任链模式，按优先级执行

3. **动态路由**：
   - 服务端下发路由映射表
   - 支持 URL Scheme 跳转（`myapp://page/detail?id=1`）
   - 支持降级策略（路由失败时的兜底处理）

---

#### 支付安全方面考虑了哪些问题？支付流程的安全加固措施？

**安全措施：**
1. **通信安全**：HTTPS + 证书校验（SSL Pinning）
2. **数据加密**：敏感数据（卡号、密码）AES/RSA 加密传输
3. **签名验证**：请求参数签名，防止篡改
4. **Token 化**：不直接传输卡号，使用 Token 替代
5. **防重放**：请求加时间戳 + Nonce
6. **环境检测**：Root 检测、模拟器检测、调试器检测
7. **代码混淆**：ProGuard/R8 混淆支付相关代码

---

#### Google Play 支付的流程是怎样的？你遇到过什么问题？

**Google Play Billing 流程：**
1. 建立 `BillingClient` 连接
2. 查询商品详情（`queryProductDetailsAsync`）
3. 发起购买（`launchBillingFlow`）
4. Google Play 处理支付（系统 UI）
5. 接收购买结果（`PurchasesUpdatedListener`）
6. **确认消费**（`consumeAsync` / `acknowledgePurchase`），3 天内未确认会自动退款
7. 服务端验证（将 PurchaseToken 发送到后端验证）

**常见问题：**
- 消耗型商品未确认导致退款
- 网络中断导致购买结果丢失（需 `queryPurchasesAsync` 恢复）
- 订阅升级/降级的 proration 处理
- 多设备同步购买状态
- 测试账号配置和沙箱环境

---

### 13. Jetpack Compose

#### Jetpack Compose 的状态提升是如何实现的？请举例说明。

**状态提升（State Hoisting）**：将状态从 Composable 内部移到外部，由调用方管理，实现**单一数据源**。

```kotlin
// 低耦合、可复用的组件
@Composable
fun NameInput(
    name: String,
    onNameChange: (String) -> Unit
) {
    TextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text("Name") }
    )
}

// 调用方持有状态
@Composable
fun ProfileScreen() {
    var name by remember { mutableStateOf("") }
    
    NameInput(
        name = name,
        onNameChange = { name = it }
    )
}
```

**优点：**
- 单一数据源，避免状态不一致
- 组件可测试、可预览
- 状态变更可预测

---

#### Jetpack Compose 自定义组件是如何实现的？请举例说明。

**使用 Canvas 绘制自定义组件：**

```kotlin
@Composable
fun CircularProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(100.dp)) {
        // 背景圆
        drawArc(
            color = Color.Gray,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = 10.dp.toPx())
        )
        // 进度弧
        drawArc(
            color = Color.Blue,
            startAngle = -90f,
            sweepAngle = 360 * progress,
            useCenter = false,
            style = Stroke(width = 10.dp.toPx())
        )
    }
}
```

**使用 Layout 自定义布局：**

```kotlin
@Composable
fun FlowRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            var x = 0
            var y = 0
            var rowHeight = 0
            placeables.forEach { placeable ->
                if (x + placeable.width > constraints.maxWidth) {
                    x = 0
                    y += rowHeight
                    rowHeight = 0
                }
                placeable.place(x, y)
                x += placeable.width
                rowHeight = maxOf(rowHeight, placeable.height)
            }
        }
    }
}
```

---

#### Compose 中的副作用（Side Effect）有哪些处理方式？LaunchedEffect、SideEffect、DisposableEffect 的区别？

| API | 触发时机 | 用途 |
|-----|---------|------|
| `LaunchedEffect` | Composable 进入组合时启动协程，离开时取消 | 一次性异步操作（如网络请求、动画） |
| `SideEffect` | 每次成功重组后执行 | 将 Compose 状态同步到非 Compose 代码 |
| `DisposableEffect` | 进入组合时执行，离开时执行 `onDispose` | 需要清理资源的操作（如注册/注销监听） |
| `rememberCoroutineScope` | 获取当前组合的协程作用域 | 在事件回调中启动协程 |
| `produceState` | 启动协程生产状态 | 将非 Compose 状态转为 Compose 状态 |

```kotlin
// LaunchedEffect：进入组合时执行
LaunchedEffect(key) {
    viewModel.loadData()
}

// DisposableEffect：离开时清理
DisposableEffect(userId) {
    observer.register(userId)
    onDispose { observer.unregister(userId) }
}

// SideEffect：每次重组后同步
SideEffect {
    analytics.setScreenName(screenName)
}
```

---

#### Compose 路由栈与 Fragment 路由栈冲突的问题你是如何解决的？

**问题**：Compose 使用 `NavController` 管理导航栈，而 Fragment 有自己的 BackStack，两者混合时返回键行为混乱。

**解决方案：**

1. **统一使用 Compose Navigation**：将 Fragment 作为 Compose 的一个目的地，不在 Fragment 中管理导航
2. **使用 `AndroidViewBinding` 嵌入 Fragment**：在 Compose 中嵌入 Fragment，但导航由 Compose 控制
3. **`OnBackPressedDispatcher`**：统一处理返回键，Compose 和 Fragment 注册各自的回调
4. **避免混合导航**：新页面优先使用 Compose，旧页面保持 Fragment，通过 Activity 级别路由切换

---

#### 请谈谈 Compose Multiplatform / Kotlin Multiplatform 的理解？你参与过相关的技术建设吗？

**Kotlin Multiplatform（KMP）**：
- 共享业务逻辑代码（网络、数据、业务逻辑），各平台保留原生 UI
- 通过 `expect/actual` 机制实现平台特定实现
- 支持 Android、iOS、Web、Desktop

**Compose Multiplatform**：
- 基于 KMP，共享 UI 层代码
- 使用 Compose 编写跨平台 UI（Android、iOS、Desktop、Web）
- iOS 端通过 Skia 渲染引擎直接绘制，不依赖 UIKit

**技术建设经验：**
- 使用 KMP 共享数据层（Repository、UseCase）
- 网络层使用 Ktor（跨平台 HTTP 客户端）
- 存储层使用 SQLDelight（跨平台 SQLite）
- UI 层根据平台特点决定是否共享

---

#### 制定业务接入标准与迁移路径的经验可以分享一下吗？

**接入标准：**
1. **接口规范**：定义统一的模块接入接口（初始化、生命周期、路由注册）
2. **依赖规范**：模块间通过接口通信，禁止直接依赖实现
3. **资源规范**：资源前缀、主题规范、图片规范
4. **数据规范**：统一数据模型、序列化方式

**迁移路径：**
1. **基础设施先行**：搭建模块化框架、路由系统、DI 容器
2. **公共层抽取**：先抽取 base 库、网络库、存储库
3. **业务模块渐进迁移**：按优先级逐个迁移业务模块
4. **兼容期**：新旧架构并存期间通过适配器模式兼容
5. **验证与回归**：每个迁移节点进行完整回归测试

---

## 二、Flutter

### 1. Dart 语言基础

#### 请谈谈 Dart 语言的特点？Dart 的单线程模型和事件循环机制是怎样的？

**Dart 语言特点：**
- **AOT + JIT 编译**：开发阶段 JIT 编译支持热重载，发布时 AOT 编译为原生机器码
- **单线程模型**（默认）：基于事件循环，避免锁和竞态问题
- **垃圾回收**：分代 GC（新生代 Scavenge + 老生代 Mark-Sweep）
- **Isolate 模型**：多线程通过 Isolate 实现，每个 Isolate 有独立堆内存

**事件循环机制：**
- Dart 使用**事件循环（Event Loop）**驱动单线程执行
- 两个队列：
  - **Microtask Queue**：优先级高，`scheduleMicrotask()` 添加，在当前事件循环结束前全部执行
  - **Event Queue**：优先级低，I/O、定时器、手势、绘制等异步事件
- 执行顺序：执行同步代码 → 清空 Microtask Queue → 从 Event Queue 取一个事件执行 → 再清空 Microtask Queue → 循环

---

#### Dart Isolate 模型是什么？Isolate 之间的通信机制有哪些？如何理解线程内存隔离？

**Isolate 模型：**
- 每个 Isolate 拥有**独立的堆内存**，不共享任何内存
- 每个 Isolate 有自己的事件循环和 GC
- Isolate 之间无法直接访问对方的对象，天然避免数据竞争

**通信机制：**
1. **SendPort / ReceivePort**：通过消息传递通信（类似 Actor 模型）
2. **compute()**：高级 API，在线程池中执行函数并返回结果
3. **Isolate.spawn()**：创建新 Isolate 并传递消息
4. **TransferableTypedData**：零拷贝传输大量数据（转移所有权而非复制）

**线程内存隔离：**
- 每个 Isolate 的堆内存完全独立
- 传递消息时会**序列化/反序列化**（深拷贝），除非使用 TransferableTypedData
- Flutter 的 `compute()` 函数在独立 Isolate 中执行耗时计算，避免阻塞 UI 线程

---

#### Dart 和 Java 的垃圾回收有什么区别？

| 维度 | Dart | Java |
|------|------|------|
| **核心算法** | 分代 GC：新生代 Scavenge（复制）+ 老生代 Mark-Sweep | 分代收集：年轻代复制 + 老年代标记-整理 |
| **收集器** | 内置单一 GC，对开发者透明 | 多种收集器可选（G1、ZGC、Shenandoah 等） |
| **暂停目标** | 低延迟优先，为 UI 流畅性优化 | 取决于收集器，ZGC 可达亚毫秒级 |
| **堆大小** | 通常较小（移动端/嵌入式场景） | 支持 MB 到 TB 级别 |
| **内存模型** | Isolate 独立堆，无跨线程 GC 竞争 | 多线程共享堆，需处理并发 GC |
| **可调性** | GC 参数少，对开发者透明 | 丰富的调优参数和收集器选择 |
| **Finalizer** | 不支持传统 finalizer，推荐 `using` 模式 | 支持 `Cleaner`（`finalize()` 已废弃） |
| **JIT 配合** | 无逃逸分析优化 | JIT 做逃逸分析、标量替换，减少堆分配 |

**本质区别：**
- **Dart**：GC 为低延迟和简单性设计，Isolate 模型天然避免跨线程内存竞争
- **Java**：GC 为通用性和吞吐量设计，多种收集器适配服务端/移动端/嵌入式等不同场景

---

### 2. Flutter 渲染机制

#### 请详细描述 Flutter 的渲染原理？Widget 树、Element 树、Render 树（三棵树）各自的作用和关系？

**三棵树：**

| 树 | 类型 | 作用 |
|---|------|------|
| **Widget 树** | 不可变描述 | 声明式 UI 描述，每次 build 重新创建 |
| **Element 树** | 可变的中间层 | Widget 的实例化，管理生命周期，是 Widget 和 RenderObject 的桥梁 |
| **Render 树** | 渲染对象 | 负责布局（尺寸/位置计算）和绘制（Canvas 操作） |

**关系：**
- Widget 配置 Element 应该是什么（描述）
- Element 持有 RenderObject（控制渲染）
- Widget 变化时，Flutter 对比新旧 Widget，更新对应 Element
- Element 更新 RenderObject，触发重新布局和绘制

**为什么需要三棵树：**
- Widget 不可变，创建成本低，可以频繁重建
- Element 是可变的，保存状态，避免不必要的重建
- RenderObject 负责实际渲染，与平台无关

---

#### Widget 的渲染流程是怎样的？从 build 到上屏经历了哪些步骤？

**渲染流程：**

1. **Build 阶段**：
   - `Widget.build()` 返回新的 Widget 树
   - Framework 对比新旧 Widget，更新 Element 树

2. **Layout 阶段**：
   - `RenderObject.layout()` 计算每个节点的位置和尺寸
   - 约束从父到子传递（Constraints），尺寸从子到父汇报（Size）

3. **Paint 阶段**：
   - `RenderObject.paint()` 将绘制指令记录到 `Layer` 树
   - 使用 `PaintingContext` 封装 Canvas 操作

4. **Compositing 阶段**：
   - `Layer` 树提交给 **Skia**（或 Impeller）渲染引擎
   - Skia 生成 GPU 指令，通过 GPU 渲染到屏幕

5. **VSync 驱动**：每帧由 VSync 信号触发上述流程

---

#### Flutter Key 的分类有哪些？GlobalKey、LocalKey、ValueKey、ObjectKey 的原理和使用场景？

**Key 分类：**

```
Key
├── LocalKey（同级别内唯一）
│   ├── ValueKey（基于值相等判断）
│   ├── ObjectKey（基于对象实例判断，ObjectKey(obj)）
│   └── UniqueKey（全局唯一，每次创建新实例）
└── GlobalKey（全局唯一，跨父节点）
    ├── GlobalKeyWithParent
    └── GlobalObjectKey（基于对象实例的全局 Key）
```

**使用场景：**
- **ValueKey**：列表项基于数据 ID 标识（如 `ValueKey(user.id)`）
- **ObjectKey**：基于对象引用标识
- **UniqueKey**：强制每次重建（如随机卡片）
- **GlobalKey**：跨 Widget 树访问 State（如 `globalKey.currentState`）
- **GlobalObjectKey**：全局唯一对象标识

**原理：**
- Flutter 在更新 Element 时，通过 Key 判断新旧 Widget 是否匹配
- 相同 Key 的 Widget 会复用 Element（保留 State）
- 不同 Key 则销毁旧 Element 创建新 Element

---

#### 全局查找组件的实现方式是什么？你是如何理解的？

**实现方式：**

1. **GlobalKey**：
```dart
final key = GlobalKey();
// 使用
MyWidget(key: key);
// 获取 State
final state = key.currentState as MyWidgetState;
// 获取 RenderObject
final renderBox = key.currentContext?.findRenderObject() as RenderBox?;
```

2. **BuildContext.findAncestorStateOfType()**：
```dart
final scaffoldState = context.findAncestorStateOfType<ScaffoldState>();
```

3. **InheritedWidget**：通过 `of(context)` 向上查找最近的祖先

**理解：**
- GlobalKey 是最直接的方式，但应谨慎使用（破坏封装性）
- 推荐使用 InheritedWidget / Provider 进行依赖注入
- GlobalKey 的 `currentContext` 在 Widget 未挂载时为 null

---

### 3. 组件生命周期与响应式编程

#### Flutter 组件的生命周期是怎样的？StatefulWidget 的各个生命周期回调在什么时候触发？

**StatefulWidget 生命周期：**

| 回调 | 触发时机 |
|------|----------|
| `createState()` | Framework 调用，创建 State 对象 |
| `initState()` | State 首次插入树中，仅调用一次，用于初始化 |
| `didChangeDependencies()` | 依赖的 InheritedWidget 变化时，或 initState 之后首次调用 |
| `build()` | 每次需要重建 UI 时调用 |
| `didUpdateWidget()` | 父 Widget 重建，新 Widget 配置与旧不同时调用 |
| `deactivate()` | State 从树中临时移除（如移动到其他位置） |
| `dispose()` | State 永久移除，用于释放资源 |
| `reassemble()` | 热重载时调用（仅开发模式） |

**生命周期顺序：**
`createState → initState → didChangeDependencies → build → didUpdateWidget → deactivate → dispose`

---

#### 请谈谈你对 Flutter 响应式编程的理解？数据驱动 UI 的机制是如何工作的？

**响应式编程核心思想：**
- UI 是状态的函数：`UI = f(state)`
- 状态变化自动触发 UI 重建

**数据驱动 UI 的机制：**

1. **setState()**：通知 Framework 状态变化，触发 `build()` 重建
2. **Stream / StreamBuilder**：监听数据流变化，自动重建
3. **ChangeNotifier + ChangeNotifierProvider**：观察者模式，状态变化通知所有监听者
4. **ValueListenableBuilder**：监听 `ValueNotifier` 变化

**工作流程：**
```
状态变化 → setState() / notifyListeners() → Element 标记为 dirty → 下一帧 VSync → build() 重建 Widget → 更新 Element/RenderObject → 重新布局/绘制
```

**关键理解：**
- Flutter 的响应式是**推送模型**（Push-based），状态变化主动通知
- `build()` 应该是幂等的，相同状态产生相同 UI

---

### 4. 状态管理

#### 请对比 Provider、Riverpod、BLoC、GetX 这几种状态管理方案的设计思想和优缺点？

| 方案 | 设计思想 | 优点 | 缺点 |
|------|---------|------|------|
| **Provider** | 基于 InheritedWidget，依赖注入 | 官方推荐、简单、与 Flutter 深度集成 | 依赖 BuildContext、跨层级访问复杂 |
| **Riverpod** | Provider 的进化版，编译期安全 | 编译期安全、不依赖 BuildContext、支持自动取消 | 学习曲线较陡、样板代码较多 |
| **BLoC** | 基于 Stream 的事件驱动 | 清晰的事件→状态流、易测试、分层清晰 | 样板代码多、简单场景过度设计 |
| **GetX** | 全能型框架，集成路由/状态/国际化 | 开发效率高、代码量少、功能全面 | 侵入性强、不遵循 Flutter 惯例、社区争议 |

**选型建议：**
- 小项目 / 快速开发：GetX 或 Provider
- 中大型项目 / 团队协作：BLoC 或 Riverpod
- 需要编译期安全：Riverpod
- 已有项目迁移：根据团队熟悉度选择

---

#### 你在项目中是如何选型状态管理方案的？为什么？

**选型考量因素：**
1. **项目规模**：小项目用简单方案，大项目需要分层清晰
2. **团队熟悉度**：优先选择团队熟悉的方案
3. **可测试性**：BLoC / Riverpod 天然易测试
4. **性能需求**：GetX 的响应式更新更精准，BLoC 的 Stream 有额外开销
5. **维护成本**：Provider / Riverpod 社区活跃，长期维护有保障

---

### 5. 性能分析与优化

#### Flutter 性能分析你是怎么做的？DevTools、Observatory、Timeline 各自的使用场景？

| 工具 | 用途 | 场景 |
|------|------|------|
| **Flutter DevTools** | 综合调试工具（Widget Inspector、Performance、Memory、Network） | 日常开发调试 |
| **Observatory**（已合并到 DevTools） | Dart VM 级别的调试和性能分析 | 分析 Isolate、GC、内存分配 |
| **Timeline**（DevTools Performance） | 帧级别性能分析 | 分析掉帧、卡顿原因 |
| **flutter run --profile** | Profile 模式运行 | 接近生产环境的性能测试 |

**DevTools 核心功能：**
- **Widget Inspector**：查看 Widget 树、布局问题
- **Performance Overlay**：实时显示 UI/Raster 线程耗时
- **Memory**：内存快照、泄漏检测
- **Network**：网络请求监控

---

#### 理解哪些常见性能问题与优化策略？

**启动与首帧：**
- **延迟初始化**：非首屏必需的 Plugin、SDK 延迟到首帧后初始化
- **按需注册**：路由、事件监听按需注册
- **资源瘦身**：减少启动时加载的资源，使用懒加载

**列表组件选型：**

| 组件 | 特点 | 适用场景 |
|------|------|----------|
| `ListView` | 一次性构建所有子 Widget | 少量固定数据 |
| `ListView.builder` | 懒加载，按需构建 | 长列表（推荐默认使用） |
| `ListView.separated` | builder + 分隔线 | 需要分隔线的列表 |
| `ListView.custom` | 自定义 `SliverChildDelegate`，精细控制构建和回收 | 需要自定义复用策略 |
| `SliverList` | 配合 `CustomScrollView` 使用的列表 Sliver | 需要混合滚动效果的列表 |
| `SliverFixedExtentList` | 所有 item 主轴尺寸固定，性能最优 | 等高列表项 |
| `ReorderableListView` | 支持拖拽排序 | 可排序列表 |
| `PageView` | 分页滑动 | 轮播图、引导页 |

**列表性能优化：**
- **懒加载**：`ListView.builder` 替代 `ListView`，按需构建
- **缓存**：`CachedNetworkImage` 缓存网络图片
- **尺寸约束**：指定图片尺寸避免加载原图
- **避免过度重绘**：使用 `RepaintBoundary` 隔离重绘区域

**重新构建优化：**
- **keys**：使用正确的 Key 避免不必要的重建
- **const**：尽可能使用 `const` Widget，Framework 跳过重建
- **Selector / Provider.value**：精准刷新，避免无关 Widget 重建
- **去除不必要 setState**：只在真正需要时调用

---

### 6. Platform Channel 与原生集成

#### Platform Channel 的三种类型分别是什么？各自的适用场景？

| 类型 | 方向 | 适用场景 |
|------|------|----------|
| **MethodChannel** | Flutter → Native（调用方法并获取结果） | 调用原生 API（相机、定位、支付等） |
| **EventChannel** | Native → Flutter（持续数据流） | 传感器数据、电量变化、网络状态等流式数据 |
| **BasicMessageChannel** | 双向消息传递 | 自定义消息格式、复杂数据传递 |

**编码/解码器：**
- `StandardMessageCodec`：默认，支持基本类型
- `JSONMessageCodec`：JSON 格式
- `BinaryCodec`：原始二进制数据

---

#### Engine、Isolate、Platform Channel、Flutter Plugin 的机制和性能优化你是如何理解的？

**Flutter Engine：**
- C++ 实现，负责渲染（Skia/Impeller）、事件处理、Platform Channel 通信
- 每个 Flutter 实例对应一个 Engine
- 优化：共享 Engine（多 View 场景）

**Isolate：**
- 独立内存空间的执行单元
- UI Isolate（Root Isolate）：运行 Flutter Framework，处理 UI
- 后台 Isolate：处理耗时计算，避免阻塞 UI

**Platform Channel：**
- Flutter → Platform：通过 JNI（Android）/ Objective-C bridge（iOS）调用
- Platform → Flutter：通过消息回调
- 消息传递是**异步**的，序列化/反序列化有性能开销

**Flutter Plugin：**
- 封装 Platform Channel 通信的模块化组件
- 注册在 `GeneratedPluginRegistrant` 中
- 优化：减少跨 Channel 调用次数、批量传输数据

---

#### 你编写过哪些 Flutter Plugin？与 iOS（Swift/OC）、Android（Kotlin/Java）原生模块深度集成的经验？

**Plugin 开发经验：**

1. **创建 Plugin**：`flutter create --template=plugin my_plugin`
2. **定义 Dart 接口**：使用 `MethodChannel` 调用原生方法
3. **实现 Android 端**：Kotlin 实现 `FlutterPlugin` 接口
4. **实现 iOS 端**：Swift 实现 `FlutterPlugin` 协议
5. **处理异步**：使用 `Result` 回调返回结果

**深度集成经验：**
- 相机 Plugin：与原生 Camera2 API / AVFoundation 集成
- 支付 Plugin：与 Google Pay / Apple Pay 集成
- 推送 Plugin：与 FCM / APNs 集成，处理后台消息

---

#### 熟悉原生开发（Kotlin），能独立处理 Platform Channels 集成的经验？

**经验要点：**
1. **MethodChannel 双向通信**：
   - Flutter 调用原生：`channel.invokeMethod()`
   - 原生回调 Flutter：`channel.invokeMethod()` 或 `Result.success()`
2. **线程处理**：Platform Channel 回调在 UI 线程，耗时操作需切到后台线程
3. **数据类型映射**：Flutter 的 `Map` ↔ Kotlin 的 `HashMap`，需处理类型转换
4. **错误处理**：使用 `PlatformException` 传递错误信息

---

### 7. 工程化建设与混合架构

#### Flutter 项目的 CI/CD 流程你是如何搭建的？

**CI/CD 流程：**
1. **代码提交**：触发 Lint + 单元测试
2. **MR 合并**：触发集成测试 + Widget 测试
3. **打 Tag**：触发打包流程
   - `flutter build apk --release`
   - `flutter build ios --release`
4. **分发**：上传到 Firebase App Distribution / TestFlight
5. **工具**：GitHub Actions / Jenkins / Fastlane

---

#### Flutter 与 Native 混合架构的演进和重构经验？架构设计和落地过程中遇到了什么问题？

**架构演进：**
1. **阶段一**：Flutter 作为独立页面嵌入（FlutterActivity/FlutterViewController）
2. **阶段二**：Flutter 为主，Native 作为 Plugin 提供能力
3. **阶段三**：统一路由管理，Flutter 和 Native 页面无缝切换

**遇到的问题：**
- **内存问题**：多个 FlutterEngine 占用大量内存 → 使用 FlutterEngineGroup 共享
- **生命周期**：Flutter 和 Native 生命周期不同步 → 统一生命周期管理
- **主题一致**：Flutter 和 Native 的 UI 风格不一致 → 统一设计系统
- **调试困难**：混合栈调试复杂 → 建立统一日志系统

---

#### Flutter 在大型 App 中的架构设计决策？如何平衡 Flutter 与 Native 的比例？

**设计决策：**
1. **新页面优先 Flutter**：快速迭代、跨端复用
2. **系统相关功能保留 Native**：推送、后台服务、硬件交互
3. **性能敏感页面评估**：复杂动画、大量原生交互的页面评估是否适合 Flutter

**平衡策略：**
- 按业务域划分：独立业务模块全部 Flutter
- 按团队能力：Flutter 熟练的团队加大 Flutter 比例
- 渐进式迁移：旧 Native 页面逐步用 Flutter 重写

---

#### Flutter Boost 框架你了解吗？请谈谈它的源码实现和设计思想。

**Flutter Boost（闲鱼开源）：**

**设计思想：**
- 解决 Flutter 与 Native 混合栈管理问题
- Native 管理页面栈，Flutter 只负责页面内容渲染
- 每个 Flutter 页面对应一个 Native Container（Activity/ViewController）

**核心实现：**
1. **BoostFlutterActivity**（Android）/ **BoostFlutterViewController**（iOS）：Native 容器
2. **BoostChannel**：通信通道，管理页面路由和参数传递
3. **页面生命周期**：Native 容器管理生命周期，Flutter 侧响应
4. **路由管理**：统一的路由 API，支持打开 Native 或 Flutter 页面

**优势：**
- 混合栈管理清晰，Native 控制页面生命周期
- 内存可控，Flutter 页面关闭时释放资源
- 与原生无缝集成，用户体验一致

---

#### 原生 + Flutter 混合项目开发你有哪些经验？遇到过什么问题？

**经验：**
1. **路由统一**：建立统一路由表，Flutter 和 Native 页面统一跳转
2. **数据传递**：通过路由参数传递，避免直接引用
3. **主题统一**：建立跨端设计规范

**遇到的问题：**
- Flutter 页面打开慢（首次创建 Engine）→ 预创建 FlutterEngine
- 页面切换动画不一致 → 统一使用 Native 转场动画
- 状态同步困难 → 使用统一的状态管理方案

---

#### 你有自研 Flutter 插件的经验吗？请描述一下过程和关键要点。

**开发过程：**
1. **需求分析**：确定需要封装的原生能力
2. **创建 Plugin**：`flutter create --template=plugin`
3. **定义接口**：Dart 层定义 MethodChannel 和 API
4. **实现原生端**：Android（Kotlin）和 iOS（Swift）分别实现
5. **错误处理**：统一异常处理和数据校验
6. **文档和示例**：编写 README 和 Example App

**关键要点：**
- 线程安全：原生端耗时操作切到后台线程
- 数据序列化：统一数据格式，处理平台差异
- 生命周期：监听 Plugin 的生命周期，及时释放资源
- 版本兼容：处理不同平台版本的 API 差异

---

### 8. 热更新与动态化

#### Flutter 各类热更新实现方案你了解哪些？各自的系统平台限制是什么？

| 方案 | 原理 | 平台限制 |
|------|------|----------|
| **官方热重载（Hot Reload）** | 开发阶段，保留 State 重新构建 Widget 树 | 仅开发模式 |
| **Flutter Hot Restart** | 开发阶段，完全重启 App | 仅开发模式 |
| **MXFlutter** | 使用 JS 编写 Flutter UI，动态下发 JS | 性能有损，复杂 UI 受限 |
| **Kraken** | 基于 Flutter 的 Web 引擎，动态加载 Web 内容 | 兼容性问题 |
| **原生动态化（如 Tinker）** | 替换 Flutter Engine 的 Dart 代码 | 需要修改 Engine，违反 App Store 政策风险 |

**iOS 限制：**
- App Store 禁止下载可执行代码（除 JS 通过 JavaScriptCore）
- Flutter AOT 编译的 Dart 代码无法动态更新
- 只有基于解释执行的方案（如 JS）才可能通过审核

---

#### Flutter 动态化的原理和可行性你是如何评估的？

**动态化原理：**
- 将 UI 描述为 JSON/DSL，运行时解析渲染
- 或使用 JS 引擎执行动态下发的 JS 代码，映射到 Flutter Widget

**可行性评估：**

| 维度 | 评估 |
|------|------|
| 技术可行性 | 可行，但性能和功能受限 |
| 平台合规性 | iOS 端需使用 JS 解释执行，存在审核风险 |
| 性能 | 动态方案有额外解析开销，复杂场景掉帧 |
| 开发体验 | DSL 表达能力有限，调试困难 |
| 维护成本 | 需要维护 DSL 解析器和运行时 |

**结论：**
- 简单页面/运营位：可以使用动态化方案
- 复杂交互页面：不建议动态化，使用发版更新
- 推荐方案：JSON DSL 描述 UI + 有限组件集

---

## 三、IM 系统

### 1. IM 核心架构

#### 请描述 IM 系统的整体架构设计？消息从发送到接收经历了哪些环节？

**整体架构：**

```
客户端 A → 接入层（网关）→ 消息路由层 → 业务逻辑层 → 消息存储层 → 推送层 → 客户端 B
```

**消息发送接收流程：**
1. **客户端 A 发送**：组装消息 → 本地存储（状态：发送中）→ 通过长连接发送到服务端
2. **接入层（网关）**：协议解析、鉴权、限流、加解密
3. **消息路由层**：根据接收者 ID 查找目标连接节点
4. **业务逻辑层**：消息校验、敏感词过滤、消息 ID 生成、持久化
5. **消息存储**：写入数据库（消息表 + 会话表 + 用户消息关系表）
6. **推送层**：通过长连接下发给在线用户，离线用户通过推送通道（FCM/APNs）通知
7. **客户端 B 接收**：收到消息 → 本地存储 → 更新 UI → 发送 ACK

---

#### IM 中长连接协议如何选型？WebSocket、自定义 TCP 协议、UDP 各自的优缺点和适用场景？

| 协议 | 优点 | 缺点                                                | 适用场景 |
|------|------|---------------------------------------------------|----------|
| **WebSocket** | 基于 HTTP，穿透性好；浏览器原生支持；实现简单 | 协议开销较大（HTTP 头）；不支持多路复用（在客户端可以通过定义不同通道ID的方式实现多路复用） | 中小型 IM、Web 端 IM |
| **自定义 TCP** | 协议紧凑，流量小；完全可控；支持多路复用 | 需要自行实现应用层协议；穿透性差（需处理粘包/拆包）                        | 大型 IM（微信、QQ） |
| **UDP** | 低延迟、无连接开销 | 不可靠、需自行保证可靠性                                      | 实时音视频、对延迟极度敏感的场景 |

**选型建议：**
- 一般 IM：WebSocket（开发成本低，兼容性好）
- 大规模 IM：自定义 TCP（性能更优，流量更省）
- 音视频：UDP + 自研可靠传输（如 QUIC）

---

#### 消息同步策略是怎样的？增量同步与全量同步如何设计？离线消息如何处理？

**消息同步策略：**

1. **增量同步**：
   - 客户端维护 `lastSeqId`（最后一条消息的序列号）
   - 拉取时发送 `lastSeqId`，服务端返回大于该 ID 的消息
   - 适用于正常在线状态下的消息同步

2. **全量同步**：
   - 首次登录或数据异常时，拉取全部消息
   - 通常只拉取最近 N 天的消息，更早的消息按需加载

3. **离线消息**：
   - 用户上线后，拉取离线期间的消息（通过 `lastSeqId` 判断）
   - 大量离线消息分页拉取，避免一次性加载过多
   - 离线消息可能过期（如 7 天），过期消息不再同步

---

#### 消息 ID 如何设计以保证全局唯一性和有序性？

**设计方案：**

1. **雪花算法（Snowflake）**：
   - 64 位 ID = 时间戳（42位）+ 机器 ID（10位）+ 序列号（12位）
   - 保证全局唯一且趋势递增
   - 客户端可本地生成，无需服务端分配

2. **服务端自增序列号（SeqId）**：
   - 每个会话维护一个自增序列号
   - 服务端生成，保证会话内有序
   - 客户端使用 SeqId 做增量同步

3. **组合方案**：
   - 消息展示使用客户端生成的 UUID（保证唯一）
   - 同步排序使用服务端分配的 SeqId（保证有序）

---

#### 消息的状态管理（未发送/发送中/已发送/已读/失败）是如何实现的？状态机如何设计？

**状态机设计：**

```
未发送 → 发送中 → 已发送 → 已送达 → 已读
          ↓
        发送失败 → 重试 → 发送中
```

**实现方式：**
- 每条消息维护 `status` 字段（枚举值）
- 发送时：status = SENDING → 收到服务端 ACK → status = SENT
- 收到已送达回执 → status = DELIVERED
- 收到已读回执 → status = READ
- 超时或错误 → status = FAILED → 用户点击重试 → 重新进入 SENDING

**UI 展示：**
- SENDING：显示加载动画
- SENT：无特殊标记
- DELIVERED：显示单勾
- READ：显示双勾
- FAILED：显示红色感叹号

---

#### 如何保证消息的至少一次投递（at-least-once delivery）？

**实现机制：**
1. **发送端**：消息发送后不立即删除，等待服务端 ACK
2. **服务端**：收到消息后持久化，返回 ACK 给发送方
3. **接收端**：收到消息后发送 ACK 给服务端
4. **超时重试**：发送方未收到 ACK，定时重发
5. **接收端去重**：基于消息 ID 去重，避免重复展示

**关键设计：**
- 发送方维护「待确认消息队列」，超时未 ACK 则重发
- 服务端幂等处理（相同消息 ID 不重复存储）
- 接收端基于消息 ID 去重

---

#### IM 消息的端到端加密你是如何实现的？安全方面考虑了哪些问题？

**端到端加密（E2EE）实现：**

1. **密钥交换**：
   - 使用 Signal Protocol 的 X3DH 协议
   - 双方通过服务端交换公钥（PreKey）
   - 协商出共享会话密钥，服务端无法解密

2. **消息加密**：
   - 使用 AES-256-GCM 加密消息内容
   - 每条消息使用不同的密钥（Double Ratchet 算法）

3. **安全考虑**：
   - 前向安全（Forward Secrecy）：即使密钥泄露，历史消息仍安全
   - 身份验证：验证通信双方身份，防止中间人攻击
   - 消息完整性：MAC 校验防止篡改
   - 元数据保护：尽量隐藏发送者/接收者信息

---

### 2. 长连接与协议

#### WebSocket 连接管理的完整流程是怎样的？包括建连、鉴权、心跳、重连、断连？

**完整流程：**

1. **建连**：
   - HTTP Upgrade 请求 → 服务端返回 101 → 建立 WebSocket 连接
   - 携带 Token 或 Session ID 用于鉴权

2. **鉴权**：
   - 建连后发送鉴权消息（包含 Token）
   - 服务端校验 Token，返回鉴权结果
   - 鉴权失败则断开连接

3. **心跳**：
   - 客户端定时发送 Ping，服务端回复 Pong
   - 连续 N 次未收到 Pong 则判定断连

4. **重连**：
   - 检测到断连后触发重连
   - 使用指数退避策略（1s → 2s → 4s → 8s → ...）
   - 设置最大重连次数和最大间隔

5. **断连**：
   - 主动断连：发送 Close 帧
   - 被动断连：网络异常、服务端踢下线

---

#### 断线重连的策略是什么？指数退避重连如何实现？如何避免重连风暴？

**指数退避重连：**

```kotlin
var retryCount = 0
val baseDelay = 1000L // 1秒
val maxDelay = 60000L // 60秒

fun reconnect() {
    val delay = min(baseDelay * (1 shl retryCount), maxDelay) + random(0, 1000)
    retryCount++
    handler.postDelayed({
        tryConnect()
    }, delay)
}
```

**避免重连风暴：**
1. **随机抖动（Jitter）**：在延迟时间上增加随机偏移，避免所有客户端同时重连
2. **服务端限流**：限制同一 IP/用户的连接频率
3. **服务端广播**：服务端故障恢复后主动通知客户端重连，而非客户端盲目重试
4. **全局状态感知**：网络恢复时统一触发重连，而非每个连接独立重试

---

#### 网络切换（WiFi ↔ 移动数据）时连接如何处理？

**处理策略：**
1. **监听网络变化**：使用 `ConnectivityManager.NetworkCallback` 监听网络切换
2. **快速重连**：网络切换时主动断开旧连接，立即发起新连接（不等心跳超时）
3. **消息补偿**：重连成功后拉取网络切换期间可能遗漏的消息
4. **IP 变化处理**：网络切换后 IP 变化，旧连接已失效，必须重建

---

#### 前后台切换对长连接的影响是什么？如何优化？

**影响：**
- 进入后台：系统可能杀死网络连接（尤其 iOS）
- 回到前台：连接可能已断开，需要重连

**优化策略：**
1. **进入后台**：
   - 停止心跳，减少电量消耗
   - 保持连接一段时间（等待可能的推送）
   - 注册推送通道作为备用

2. **回到前台**：
   - 立即检查连接状态
   - 断连则快速重连
   - 拉取后台期间遗漏的消息

---

#### 长连接的安全加密（TLS/SSL）是如何处理的？证书校验策略是什么？

**TLS/SSL 处理：**
1. **TLS 握手**：WebSocket 使用 `wss://` 协议，底层走 TLS
2. **证书校验**：
   - 默认校验：系统 CA 证书链校验
   - SSL Pinning：内置服务端证书指纹，防止中间人攻击
   - 证书更新：支持证书轮换（备份证书）
3. **证书锁定策略**：
   - 锁定公钥（推荐）：证书更换但公钥不变时无需更新客户端
   - 锁定证书：更安全但需要客户端同步更新

---

### 3. 消息可靠性与弱网处理

#### 消息乱序是如何产生的？如何通过序列号/时间戳/服务端排序来保证消息顺序？

**乱序产生原因：**
- 网络延迟不一致（后发的消息先到达）
- 多设备同时发送
- 消息重试导致重复和乱序
- 服务端多节点处理顺序不一致

**排序方案：**
1. **服务端序列号（推荐）**：服务端为每个会话维护递增 SeqId，客户端按 SeqId 排序展示
2. **时间戳排序**：使用服务端时间戳（避免客户端时钟不一致），但同一毫秒内的消息仍可能乱序
3. **客户端排序**：收到消息后按 SeqId 排序插入本地数据库

---

#### 消息重复的原因有哪些？去重策略是什么？基于消息 ID 去重与基于内容去重的优劣？

**重复原因：**
- 发送方超时重发（未收到 ACK）
- 网络抖动导致消息重复投递
- 客户端重复拉取

**去重策略：**
1. **基于消息 ID 去重（推荐）**：
   - 维护已接收消息 ID 集合（布隆过滤器或 HashSet）
   - 收到消息时检查 ID 是否已存在
   - 优点：精确、高效
   - 缺点：依赖消息 ID 的唯一性

2. **基于内容去重**：
   - 对消息内容做 Hash，比较 Hash 值
   - 优点：不依赖消息 ID
   - 缺点：相同内容的不同消息会被误判为重复

---

#### 消息丢失可能发生在哪些环节？如何通过 ACK + 重试机制来保障可靠性？

**丢失环节：**
1. 客户端发送失败（网络异常）
2. 服务端处理失败（数据库写入失败）
3. 服务端下发失败（接收方网络异常）
4. 客户端接收后处理失败（本地存储失败）

**ACK + 重试机制：**
1. **发送 ACK**：服务端收到消息后返回 ACK，发送方收到 ACK 才标记发送成功
2. **接收 ACK**：客户端收到消息后返回 ACK，服务端收到 ACK 才标记投递成功
3. **超时重试**：未收到 ACK 的消息定时重发，最多重试 N 次
4. **离线补偿**：重连后拉取未确认的消息

---

#### 弱网环境（高延迟、高丢包、带宽受限）下如何检测和适配？网络质量评估方案是什么？

**弱网检测：**
1. **RTT（往返延迟）**：Ping 服务端，RTT > 500ms 判定弱网
2. **丢包率**：统计心跳/Ping 的丢失比例
3. **带宽估算**：下载测试文件估算可用带宽
4. **综合评分**：结合 RTT + 丢包率 + 带宽给出网络质量评分

**弱网适配：**
- 降低图片质量/分辨率
- 减少心跳频率
- 合并请求，减少网络交互次数
- 消息发送排队，控制并发数
- 提示用户当前网络状况

---

#### 弱网下消息发送的排队、优先级、超时与重试策略如何设计？

**设计策略：**
1. **消息排队**：
   - 本地维护发送队列（FIFO）
   - 控制并发发送数（弱网下同时只发 1 条）

2. **优先级**：
   - 文本消息 > 图片 > 视频 > 文件
   - 系统消息可延迟发送

3. **超时策略**：
   - 弱网下增大超时时间（如 30s → 60s）
   - 超时后标记为发送失败，等待用户重试或自动重试

4. **重试策略**：
   - 指数退避重试
   - 网络恢复后优先重试失败消息
   - 最多重试 3 次，超过后标记为失败

---

#### 弱网下消息接收如何处理？如何保证消息的完整性和顺序性？

**接收处理：**
1. **消息缓存**：服务端缓存未 ACK 的消息，等待客户端重连后补发
2. **分片传输**：大消息分片传输，避免弱网下传输失败
3. **完整性校验**：消息携带 CRC/MD5，接收方校验完整性
4. **顺序保证**：按 SeqId 排序，发现缺失则请求服务端补发

---

#### 消息的优先级分级（如文本/图片/视频/系统通知）是如何处理的？不同优先级的传输策略有何差异？

**优先级分级：**

| 优先级 | 消息类型 | 传输策略 |
|--------|---------|----------|
| 高 | 文本消息、信令 | 立即发送，走长连接 |
| 中 | 图片、语音 | 压缩后发送，支持缩略图优先 |
| 低 | 视频、文件 | 后台队列发送，WiFi 下优先 |
| 最低 | 系统通知 | 延迟批量发送，可合并 |

---

#### 弱网环境下网络请求的幂等性设计如何保证请求的正确性？

**幂等性设计：**
1. **唯一请求 ID**：每次发送消息生成唯一 ID，服务端根据 ID 判断是否重复处理
2. **状态机校验**：服务端检查消息当前状态，避免重复操作
3. **乐观锁**：使用版本号防止并发冲突
4. **客户端去重**：发送前检查是否已发送（相同 ID 的消息）

---

#### 网络请求的缓存策略、降级策略

**缓存策略：**
1. **请求缓存**：相同请求短时间内返回缓存结果
2. **响应缓存**：HTTP Cache-Control / 本地缓存
3. **预加载**：进入页面时预加载可能需要的数据

**降级策略：**
1. **功能降级**：弱网下关闭非核心功能（如动态、直播）
2. **数据降级**：图片降低质量、视频降低分辨率
3. **接口降级**：主接口超时后调用备用接口
4. **UI 降级**：加载失败显示缓存数据 + 重试按钮

---

### 4. IM 本地存储

#### IM 消息的本地存储方案是如何设计的？消息表结构、分表策略（按会话/时间）？

**消息表结构：**

```sql
CREATE TABLE messages (
    msg_id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    sender_id TEXT NOT NULL,
    content TEXT,
    msg_type INTEGER,
    seq_id INTEGER,
    status INTEGER,
    created_at INTEGER,
    updated_at INTEGER
);

-- 索引
CREATE INDEX idx_conv_seq ON messages(conversation_id, seq_id);
CREATE INDEX idx_conv_created ON messages(conversation_id, created_at);
```

**分表策略：**
1. **按会话分表**：每个会话一张表（`messages_{conversationId}`），适合会话数量少的场景
2. **按时间分表**：每月一张表（`messages_202401`），适合单会话消息量大的场景
3. **混合策略**：按会话 + 时间，如 `messages_{convId}_{month}`

---

#### 消息索引如何设计以支持高效的会话列表加载和消息搜索？

**索引设计（基于B+树）：**
1. **会话列表**：
   - 会话表维护 `last_msg_id`、`last_msg_time`、`unread_count`
   - 按 `last_msg_time` 降序排列

2. **消息搜索**：
   - 全文索引（FTS）：支持关键词搜索消息内容
   - 发送者索引：按发送者筛选
   - 时间范围索引：按时间段筛选

3. **消息加载**：
   - 按 `seq_id` 分页加载
   - 本地缓存最新消息（最近 100 条）

---

#### 大量消息的查询性能如何优化？分页加载、懒加载策略？

**优化策略：**
1. **分页加载**：
   - 每次加载 20~50 条消息
   - 使用 `LIMIT + OFFSET` 或基于 `seq_id` 的范围查询
   - 滑动到底部时触发加载更早的消息

2. **懒加载**：
   - 只加载当前可见会话的消息
   - 图片/视频消息延迟加载（滑到可见区域才加载）

3. **数据库优化**：
   - 合理索引
   - 使用 `WAL` 模式提高并发读写性能
   - 定期清理过期消息

---

#### 消息的撤回和删除在本地存储层面如何处理？

**消息撤回：**
1. 服务端下发撤回通知（包含消息 ID）
2. 本地将消息标记为「已撤回」
3. UI 显示「该消息已撤回」
4. 不物理删除，保留记录（审计需要）

**消息删除：**
1. **单删**：本地标记为已删除，对端不可见
2. **双方删除**：服务端下发删除通知，双方本地都标记删除
3. **物理删除**：定期清理已标记删除的消息（节省存储空间）
4. **媒体文件**：删除消息关联的图片/视频文件，释放磁盘空间

---

## 四、金融交易业务

### 1. 数值精度与交易逻辑

#### BigDecimal 精度处理在交易场景中如何应用？常见的精度丢失问题有哪些？

**BigDecimal 应用：**

```kotlin
// 正确方式：使用 String 构造
val price = BigDecimal("19.99")
val quantity = BigDecimal("3")
val total = price.multiply(quantity) // 59.97

// 设置精度和舍入模式
val result = total.divide(BigDecimal("3"), 2, RoundingMode.HALF_UP) // 19.99
```

**常见精度丢失问题：**
1. **浮点数构造**：`BigDecimal(0.1)` 实际值为 `0.1000000000000000055511151231257827021181583404541015625`
2. **double 运算**：`0.1 + 0.2 = 0.30000000000000004`
3. **除法无限循环**：`1 / 3 = 0.333...`，必须指定精度和舍入模式
4. **舍入模式选择错误**：金融场景通常使用 `HALF_UP`（四舍五入），而非 `HALF_EVEN`（银行家舍入）

double 遵循 IEEE 754 双精度浮点标准，用二进制小数存储，0.1 在二进制中是一个无限循环小数，因此在进行金融计算时，容易导致精度丢失。

**最佳实践：**
- 始终使用 `String` 或 `valueOf()` 构造 BigDecimal
- 明确指定 `MathContext` 或精度 + 舍入模式
- 服务端和客户端使用相同的精度规则

---

#### 交易场景下的数值计算逻辑（如盈亏计算、保证金计算）你是如何保证准确性的？

**保证准确性的措施：**

1. **统一精度规范**：
   - 价格：8 位小数
   - 数量：4 位小数
   - 金额：2 位小数（展示）/ 8 位小数（内部计算）

2. **服务端为准**：
   - 客户端仅做展示计算
   - 实际交易以服务端计算结果为准
   - 客户端计算仅用于预估展示

3. **防篡改**：
   - 交易参数签名校验
   - 服务端重新计算所有关键数值

4. **测试覆盖**：
   - 边界值测试（极大/极小值）
   - 精度边界测试（0.00000001）

---

#### 浮点精度控制的最佳实践是什么？在 Android/Kotlin 中如何落地？

**最佳实践：**

1. **禁止使用 float/double 做金额计算**
2. **统一使用 BigDecimal**：
   ```kotlin
   // 扩展函数简化使用
   fun Double.toBigDecimal(): BigDecimal = BigDecimal.valueOf(this)
   fun String.toMoney(): BigDecimal = BigDecimal(this).setScale(2, RoundingMode.HALF_UP)
   ```
3. **展示层格式化**：
   ```kotlin
   fun BigDecimal.formatMoney(): String {
       return setScale(2, RoundingMode.HALF_UP)
           .toPlainString()// 去掉科学计数法表示
           .let { "$${it}" }
   }
   ```
4. **服务端下发字符串**：避免 JSON 解析时精度丢失
5. **本地存储用 Long**：以最小单位存储（如分），避免浮点问题

---

### 2. 高频刷新与行情

#### 高频刷新列表的优化经验有哪些？DiffUtil 的使用注意事项和性能陷阱？

**优化经验：**

1. **DiffUtil 注意事项**：
   - `areItemsTheSame` 必须高效（比较 ID，不比较内容）
   - `areContentsTheSame` 避免深度比较（使用 hashCode 或版本号）
   - 大数据集（>1000 条）考虑异步计算 diff
   - 避免频繁调用 `submitList`，合并多次更新

2. **性能陷阱**：
   - `notifyDataSetChanged()` 全量刷新（避免使用）
   - 每次数据变化都触发 diff（应该合并多次变化）
   - 复杂对象比较耗时（使用版本号代替内容比较）

3. **高频场景优化**：
   - 使用 `AsyncListDiffer` 在后台线程计算 diff
   - 固定时间窗口合并更新（如 100ms 内的多次更新合并为一次）
   - 只更新变化的行，不刷新整个列表

---

#### 局部刷新与节流（throttle/debounce）策略在高频数据更新中如何实施？

**节流策略：**

1. **Throttle（节流）**：固定时间窗口内只执行第一次
   ```kotlin
   // 每 100ms 最多更新一次 UI
   val flow = priceFlow.throttleLatest(100)
   ```

2. **Debounce（防抖）**：停止触发后等待一段时间再执行
   ```kotlin
   // 停止更新 200ms 后再刷新 UI
   val flow = priceFlow.debounce(200)
   ```

3. **采样（Sample）**：固定时间间隔取最新值
   ```kotlin
   // 每 16ms（一帧）取最新价格
   val flow = priceFlow.sample(16)
   ```

**局部刷新：**
- 使用 `notifyItemChanged(position, payload)` 携带变化内容
- 在 `onBindViewHolder(holder, position, payloads)` 中只更新变化的部分
- 避免全量 `notifyDataSetChanged()`

---

#### 高频行情数据下的 UI 性能优化方案是什么？如何避免频繁刷新导致的卡顿？

**优化方案：**

1. **数据层**：
   - 合并高频更新（100ms 窗口合并）
   - 只传递变化的字段（增量更新）
   - 后台线程处理数据，主线程只负责渲染

2. **UI 层**：
   - 局部刷新（只更新变化的行/字段）
   - 使用 `RecyclerView` + `AsyncListDiffer`
   - 价格变化使用动画过渡（避免跳变）
   - 不可见区域不刷新

3. **渲染层**：
   - 使用 `Canvas` 自绘 K 线图（避免 View 层级过深）
   - 使用 `SurfaceView` 渲染高频图表
   - 减少布局层级（ConstraintLayout 扁平化）

---

#### WebSocket 行情/通道接入经验？订阅管理、心跳、重连、消息可靠性如何处理？

**接入经验：**

1. **订阅管理**：
   - 进入页面自动订阅，离开页面取消订阅
   - 支持批量订阅/取消订阅
   - 订阅状态维护（已订阅/未订阅/订阅失败）

2. **心跳**：
   - 定时发送 Ping，检测连接活性
   - 行情通道心跳间隔通常 10~30s

3. **重连**：
   - 断线后自动重连
   - 重连后重新订阅之前的频道
   - 拉取断线期间的数据（增量同步）

4. **消息可靠性**：
   - 消息序号检测，发现缺失则请求补发
   - 数据完整性校验

---

#### WebSocket 前后台切换、断线恢复、数据连续性如何保障？

**保障策略：**

1. **前后台切换**：
   - 进入后台：保持连接，降低心跳频率
   - 回到前台：检查连接状态，断连则重连
   - 拉取后台期间的数据更新

2. **断线恢复**：
   - 记录最后接收的数据序号
   - 重连后发送序号，服务端补发缺失数据
   - 若缺失过多，全量拉取最新数据

3. **数据连续性**：
   - 服务端保证数据序号连续
   - 客户端检测序号间隙，发现不连续则请求补发
   - 超过阈值直接全量刷新

---

## 五、综合 & 其他

### 1. AI 工具提效

#### 你使用过哪些 AI 编程工具（GitHub Copilot、Cursor、ChatGPT）？在实际开发中如何提效的？

**使用经验：**

1. **GitHub Copilot**：
   - 代码自动补全：减少重复代码编写
   - 单元测试生成：快速生成测试用例
   - 代码注释生成：提高文档质量

2. **Cursor**：
   - 代码重构：批量修改代码结构
   - 问题排查：描述问题，AI 分析可能原因
   - 代码审查：检查潜在问题

3. **ChatGPT**：
   - 技术方案设计：讨论架构设计、技术选型
   - 知识查询：快速了解新技术/框架
   - 代码 Review：分析代码质量、性能问题

**提效场景：**
- 重复性代码生成（DTO、Mapper、Adapter）
- 复杂正则表达式编写
- 文档/注释编写
- 问题排查思路提供

---

#### 你了解 spec 驱动的 AI 工具吗？比如 spec-kit（Plan 模式编程）的使用经验？

**Spec 驱动开发：**

1. **核心理念**：先写规格说明（Spec），再由 AI 根据 Spec 生成代码
2. **工作流程**：
   - 编写需求 Spec（接口定义、输入输出、边界条件）
   - AI 根据 Spec 生成实现代码
   - 人工 Review 和调整
   - 生成测试用例验证实现

3. **Plan 模式编程**：
   - 先制定实现计划（步骤、文件、接口）
   - AI 按计划逐步实现
   - 每个步骤完成后验证，确保方向正确

**优势：**
- 代码质量更高（有明确的规格约束）
- 减少返工（先规划后实现）
- 更适合复杂任务

---

#### 你能总结一下 AI 辅助编程的工作流吗？

**AI 辅助编程工作流：**

```
需求分析 → 方案设计 → AI 生成代码 → 人工 Review → 测试验证 → 迭代优化
```

**详细流程：**

1. **需求理解**：用 AI 分析需求，明确输入输出和边界条件
2. **方案设计**：与 AI 讨论技术方案，确定架构和接口
3. **代码生成**：
   - 使用 Copilot 补全重复代码
   - 使用 Cursor/ChatGPT 生成复杂逻辑
   - 生成单元测试
4. **代码 Review**：
   - 人工审查 AI 生成的代码
   - 检查安全性、性能、边界情况
5. **测试验证**：运行测试，确认功能正确
6. **迭代优化**：根据反馈调整，持续改进

**注意事项：**
- AI 生成的代码必须人工 Review
- 不要完全依赖 AI，保持独立思考
- 复杂业务逻辑仍需人工设计
- 注意代码安全（不泄露敏感信息）

---