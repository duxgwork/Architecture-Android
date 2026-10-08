# Android 常见面试题

本文档整理 Android 开发常见面试题，覆盖 **语言基础 → 四大组件 → 原理 → 架构 → 性能 → 工程化**，并补充 **金融交易类 App**（WebSocket、混合栈、高实时 UI）相关高频题。适合 3–8 年 Android 岗位复习与模拟面试。

---

## 1. Kotlin 基础

### 1.1 `val` 和 `var` 的区别？

- `val`：只读引用，引用不可变，对象内容可变（如 `val list = mutableListOf()` 仍可 `add`）。
- `var`：可变引用。
- 优先使用 `val`，利于不可变设计与线程安全推理。

### 1.2 `lateinit` 和 `by lazy` 的区别？

| | `lateinit var` | `by lazy` |
|---|----------------|-----------|
| 类型 | 可变，非 null | 通常 `val`，惰性初始化 |
| 适用 | `var`，不能用于 primitive / nullable | 首次访问时初始化 |
| 线程 | 不保证线程安全（默认） | `lazy(LazyThreadSafetyMode)` 可配置 |
| 检查 | 未初始化访问抛 `UninitializedPropertyAccessException` | 自动保证只初始化一次 |

### 1.3 数据类 `data class` 自动生成什么？

`equals()`、`hashCode()`、`toString()`、`copy()`、`componentN()`（解构）。需至少一个主构造参数；`var`/`val` 必须写在主构造。

### 1.4 `sealed class` 和 `enum` 的区别？

- `enum`：固定常量集合，每个实例单例。
- `sealed class`：受限继承层次，子类可在同文件定义；适合表达 UI 状态、网络结果等 **带数据的有限分支**，配合 `when` 做穷尽匹配。

### 1.5 扩展函数与成员函数的区别？

扩展函数是静态分发（编译期），不改变被扩展类的结构；不能访问 `private` 成员。成员函数支持多态（运行时虚分派）。

### 1.6 内联函数 `inline` 的作用？

减少高阶函数 lambda 带来的对象分配；`noinline`、`crossinline` 控制 lambda 参数行为。`reified` 配合 `inline` 在运行时保留泛型类型。

### 1.7 协程：`launch` 和 `async` 的区别？

- `launch`：返回 `Job`，不关心返回值，异常由 `CoroutineExceptionHandler` 或父协程处理。
- `async`：返回 `Deferred<T>`，需 `await()` 取结果；异常在 `await()` 时抛出。
- 结构化并发：子协程生命周期绑定父 `CoroutineScope`。

### 1.8 `Dispatchers.Main`、`IO`、`Default` 分别做什么？

- `Main`：主线程，更新 UI。
- `IO`：阻塞 IO（网络、磁盘），线程池较大。
- `Default`：CPU 密集计算，线程数 ≈ CPU 核数。

### 1.9 `Flow` 和 `LiveData` 的区别？

| | LiveData | Flow |
|---|----------|------|
| 生命周期 | 自动感知 Lifecycle | 需 `flowWithLifecycle` / `repeatOnLifecycle` |
| 背压 | 无 | 支持 |
| 操作符 | 少 | `map`、`filter`、`combine`、`sample` 等丰富 |
| 线程切换 | `postValue` | `flowOn` |

### 1.10 `StateFlow` 和 `SharedFlow` 的区别？

- `StateFlow`：必有初始值，去重（相同值不重复发射），适合 UI 状态（如 `UiState`）。
- `SharedFlow`：可无初始值，可配置 `replay`、`extraBuffer`，适合事件（Toast、导航、一次性 Snackbar）。

---

## 2. Java 基础（Android 仍常考）

### 2.1 `==` 和 `equals()` 的区别？

- `==`：基本类型比值；引用类型比地址。
- `equals()`：默认同 `==`；`String`、包装类等已重写为值比较。

### 2.2 `HashMap` 原理？JDK 1.8 链表转红黑树条件？

数组 + 链表/红黑树；`hash` 定位桶；链表长度 > 8 且数组长度 ≥ 64 时转红黑树；退化为链表条件相反。负载因子默认 0.75，扩容 2 倍。

### 2.3 `ConcurrentHashMap` 如何保证线程安全？

JDK 1.8：`CAS` + `synchronized` 锁桶头节点；分段锁已移除。`get` 大多无锁。

### 2.4 强引用、软引用、弱引用、虚引用的区别？

| 类型 | 回收时机 | 典型用途 |
|------|----------|----------|
| 强引用 | 永不主动回收（OOM 前） | 普通对象 |
| 软引用 | 内存不足时 | 图片缓存 |
| 弱引用 | GC 即回收 | `WeakHashMap`、`WeakReference` 防泄漏 |
| 虚引用 | 无法通过引用取对象 | 跟踪对象回收 |

### 2.5 接口和抽象类的区别？

- 接口：多实现，Java 8+ 可有 `default`/`static` 方法。
- 抽象类：单继承，可有构造方法和成员状态。

---

## 3. Android 四大组件

### 3.1 Activity 生命周期？

```
onCreate → onStart → onResume → [运行]
onPause → onStop → onDestroy
onRestart（从 stop 回来）
```

- `onPause`：部分可见（如上弹 Dialog），应快速返回。
- `onStop`：完全不可见。
- 配置变更（旋转屏）：默认销毁重建；可用 `ViewModel` + `onSaveInstanceState` 保状态。

### 3.2 `onSaveInstanceState` 和 `ViewModel` 的区别？

- `onSaveInstanceState`：系统杀进程后恢复轻量状态（Bundle 有大小限制）。
- `ViewModel`：配置变更（如旋转）间保留数据，进程被杀则不保证保留（除非配合 `SavedStateHandle`）。

### 3.3 Activity 启动模式？

| 模式 | 行为 |
|------|------|
| `standard` | 每次新建实例 |
| `singleTop` | 栈顶复用，`onNewIntent` |
| `singleTask` | 栈内复用，其上 Activity 出栈 |
| `singleInstance` | 独立任务栈 |

### 3.4 `Intent` 显式与隐式的区别？

- 显式：明确指定组件 `ComponentName`，应用内跳转。
- 隐式：指定 `action`/`category`/`data`，系统匹配；Deep Link 常用 `VIEW` + `scheme`。

### 3.5 Service 两种启动方式？

- `startService`：后台任务，需 `stopSelf`/`stopService` 停止。
- `bindService`：与组件绑定，所有客户端 unbind 后销毁。
- 前台 Service：`startForeground` + 通知，Android 8+ 后台限制场景必需。

### 3.6 `BroadcastReceiver` 注册方式？

- 静态注册（Manifest）：应用未启动也可收；Android 8+ 多数隐式广播受限。
- 动态注册（代码）：随组件生命周期注册/注销，常用 `LocalBroadcastManager`（已废弃，推荐 `Flow`/事件总线）。

### 3.7 ContentProvider 的作用？

跨进程数据共享的统一入口；底层基于 Binder。`ContentResolver` + URI 访问。

### 3.8 Fragment 生命周期与 Activity 的关系？

Fragment 生命周期嵌套在 Activity 内；`onAttach` → `onCreate` → `onCreateView` → `onViewCreated` → `onStart` → `onResume` → … → `onDestroyView` → `onDestroy` → `onDetach`。

`onDestroyView` 与 `onDestroy` 分离：ViewPager 中 Fragment 视图可销毁而实例保留。

---

## 4. Handler / Looper / MessageQueue

### 4.1 原理？

- 每个线程最多一个 `Looper`，关联一个 `MessageQueue`。
- `Handler` 发 `Message`/`Runnable` 入队；`Looper.loop()` 不断取消息在主线程执行。
- 主线程 `ActivityThread.main()` 启动时创建主线程 `Looper`。

### 4.2 为什么子线程不能更新 UI？

UI 非线程安全；只有持有主线程 `Looper` 的线程可更新 View。子线程需 `Handler(Looper.getMainLooper())` 或 `runOnUiThread` / `view.post {}`。

### 4.3 `Handler` 内存泄漏原因与解决？

- 原因：非静态内部类 `Handler` 持有外部 Activity；延迟消息未清除，Activity 无法回收。
- 解决：静态内部类 + `WeakReference<Activity>`；`onDestroy` 时 `removeCallbacksAndMessages(null)`；优先用协程（生命周期绑定）。

### 4.4 `post` 和 `sendMessage` 的区别？

本质都往 `MessageQueue` 放任务；`post(Runnable)` 内部封装为 `Message.callback`。

---

## 5. View 绘制与事件分发

### 5.1 View 绘制流程？

`measure`（测量）→ `layout`（布局）→ `draw`（绘制）。

- `measure`：`onMeasure`，`MeasureSpec`（mode + size）。
- `layout`：确定自身与子 View 位置。
- `draw`：`onDraw` 绘制内容 → `dispatchDraw` 绘制子 View。

### 5.2 `invalidate` 和 `requestLayout` 的区别？

- `invalidate`：仅重绘（`onDraw`），布局不变。
- `requestLayout`：重新 measure + layout + draw。

### 5.3 事件分发机制？

`dispatchTouchEvent` → `onInterceptTouchEvent`（ViewGroup）→ `onTouchEvent`。

- 自上而下分发，可拦截。
- 无人消费则事件消失。
- `ACTION_DOWN` 决定后续事件序列交给谁。

### 5.4 滑动冲突怎么处理？

- 外部拦截：父 `onInterceptTouchEvent` 根据位移/速度决定拦截。
- 内部接管：子 View `requestDisallowInterceptTouchEvent(true)`。
- 嵌套滚动：`NestedScrollingParent` / `NestedScrollingChild`（`RecyclerView`、`CoordinatorLayout`）。

### 5.5 自定义 View 注意事项？

- 支持 `wrap_content`、padding。
- 在 `onDetachedFromWindow` 释放资源、取消动画。
- 硬件加速、过度绘制优化。
- 金融 K 线等高频刷新：局部 `invalidate`、对象池、子线程计算 + 主线程绘制。

---

## 6. 内存与性能

### 6.1 常见内存泄漏场景？

| 场景 | 原因 |
|------|------|
| 静态变量持 Activity | 生命周期不匹配 |
| Handler 延迟消息 | 见 §4.3 |
| 匿名内部类 / 非静态内部类 | 隐式持有外部类 |
| 单例持 Context | 应使用 `ApplicationContext` |
| 监听器未注销 | 广播、传感器、EventBus |
| WebView | 独立进程或正确 destroy |

### 6.2 如何检测内存泄漏？

- LeakCanary（弱引用 + 延迟 GC 检测引用链）。
- Android Studio Profiler（Memory Dump + Heap Analysis）。
- `adb shell dumpsys meminfo`。

### 6.3 ANR 原因与避免？

**原因：** 主线程阻塞超过 5s（输入）、BroadcastReceiver 10s、Service 20s 等。

**避免：**

- 耗时操作放子线程 / 协程 `Dispatchers.IO`。
- 减少主线程 IO、大 JSON 解析、数据库查询。
- 分批渲染、懒加载。

### 6.4 冷启动、温启动、热启动？

| 类型 | 说明 |
|------|------|
| 冷启动 | 进程不存在，完整启动 |
| 温启动 | 进程在，Activity 需重建 |
| 热启动 | 进程和 Activity 都在，从后台恢复 |

优化：启动主题、`SplashScreen API`、延迟初始化、异步加载非关键 SDK。

### 6.5 列表卡顿优化？

- `RecyclerView`：DiffUtil、`ListAdapter`、ViewHolder 复用。
- 减少层级、`ConstraintLayout` 扁平化。
- 图片：合适尺寸、Glide 缓存、占位图。
- 避免 `onBind` 里创建对象、解析 JSON。
- 预取、`setHasFixedSize(true)`。

### 6.6 包体积优化？

- 资源压缩、WebP、矢量图。
- 按需 ABI（`abiFilters`）。
- R8/ProGuard 混淆压缩。
- 动态特性模块、移除无用依赖。

---

## 7. 架构与设计

### 7.1 MVC / MVP / MVVM 对比？

| 模式 | 特点 | 缺点 |
|------|------|------|
| MVC | Controller 臃肿，易与 View 耦合 | Activity 过重 |
| MVP | Presenter 与 View 接口隔离 | 接口爆炸、生命周期需手动绑定 |
| MVVM | View 观察 ViewModel 数据驱动 | 调试链略长，需规范单向数据流 |

OSL 项目以 **MVVM + Repository** 为主，KChart 部分保留 MVP。

### 7.2 单向数据流（UDF）是什么？

```
Event → ViewModel → State → UI 渲染
```

UI 发意图（Intent/Event），ViewModel 处理并产出不可变 State；UI 只根据 State 渲染，减少状态不一致。

### 7.3 Repository 模式的作用？

统一数据来源（网络、缓存、DB）；ViewModel 不直接依赖 Retrofit/Room；便于测试与切换数据源。

### 7.4 依赖注入常见方案？

- Hilt / Dagger：编译期生成，适合大型项目。
- Koin：纯 Kotlin DSL，轻量。
- 手动：`@ServiceProvider`（TheRouter，OSL 项目跨模块服务注入）。

### 7.5 模块化架构如何通信？

- **禁止** Feature 模块直接互依赖。
- 接口下沉 Core：`IAssetsService`、`IFlutterService` 等。
- 路由：TheRouter `@Route`、ARouter。
- 事件：SharedFlow / 轻量 EventBus（避免滥用）。

---

## 8. Jetpack 组件

### 8.1 `ViewModel` 为什么能横屏不丢数据？

通过 `ViewModelStore` 与 Activity/Fragment 作用域绑定；配置变更时 Store 保留，新 Activity 取同一实例。由 `ActivityThread` + `FragmentManager` 协调。

### 8.2 `LiveData` 粘性问题？

新观察者会立即收到最新值。一次性事件（导航、Toast）用 `SharedFlow`、`Channel` 或 `SingleLiveEvent` 模式。

### 8.3 `Room` 核心组件？

`Entity`、`DAO`、`Database`。编译期生成实现；支持 `Flow` 返回、事务、`Migration`。

### 8.4 `WorkManager` 适用场景？

deferrable、保证执行的后台任务（同步、上传）；系统根据约束（网络、充电）调度；不适合即时任务。

### 8.5 `Navigation` 组件优势？

统一导航图、类型安全参数（Safe Args）、深层链接、返回栈管理。

---

## 9. 网络与数据

### 9.1 OkHttp 拦截器链？

应用拦截器 → 重试拦截器 → Bridge → 缓存 → 连接 → 网络拦截器。可做日志、Header 注入、Token 刷新、重试。

### 9.2 Retrofit 原理？

动态代理 + 注解解析生成 `Service` 实现；`CallAdapter` 支持 RxJava/协程；`Converter` 做 JSON 转换。

### 9.3 HTTPS 握手过程（简述）？

TCP 连接 → TLS ClientHello → ServerHello + 证书 → 验证证书 → 密钥交换 → 对称加密通信。

### 9.4 证书锁定（Certificate Pinning）？

客户端预埋服务器证书/公钥哈希，防中间人攻击。金融类 App 常见要求。

### 9.5 WebSocket 与 HTTP 长轮询的区别？

| | WebSocket | 长轮询 |
|---|-----------|--------|
| 连接 | 全双工持久连接 | 反复 HTTP 请求 |
| 开销 | 低，适合高频推送 | 高 |
| 场景 | 行情、深度、订单推送 | 简单实时通知 |

**面试常问：**

- 心跳保活（ping/pong）。
- 断线重连（指数退避）。
- 前后台切换重订阅。
- snapshot + update 增量合并。
- 多页面共享连接 + 引用计数退订。

---

## 10. 进程与 IPC

### 10.1 Android 有哪些 IPC 方式？

| 方式 | 特点 |
|------|------|
| Bundle | 简单数据，Intent 传递 |
| 文件 | 不适合并发 |
| AIDL | 跨进程接口调用，Binder 实现 |
| Messenger | 串行消息，基于 Handler |
| ContentProvider | 数据共享 |
| Socket | 灵活，开销大 |

### 10.2 Binder 原理（简述）？

Linux 进程隔离；Binder 是内核驱动，一次拷贝（对比 Socket 两次）；`Proxy`/`Stub` 透明跨进程；线程池处理回调。Service、Broadcast、ContentProvider 底层都依赖 Binder。

### 10.3 AIDL 使用步骤？

定义 `.aidl` 接口 → 实现 `Stub` → Service `onBind` 返回 → 客户端 `bindService` 获取代理。

---

## 11. 多线程与并发

### 11.1 `synchronized` 和 `ReentrantLock` 的区别？

- `synchronized`：JVM 关键字，自动释放锁。
- `ReentrantLock`：可中断、公平锁、`tryLock`、多条件 `Condition`。

### 11.2 线程池参数含义？

`corePoolSize`、`maximumPoolSize`、`keepAliveTime`、`workQueue`、`RejectedExecutionHandler`。避免无界线程创建。

### 11.3 主线程规则总结？

UI 操作、生命周期敏感逻辑在主线程；网络/磁盘/重计算在子线程；回主线程用 Handler / `withContext(Main)`。

---

## 12. 存储与安全

### 12.1 `SharedPreferences` 和 DataStore 的区别？

- SP：同步 API 易 ANR；`apply` 异步写；无类型安全。
- DataStore（Preferences / Proto）：协程 + Flow，事务性更好。

### 12.2 敏感数据如何存储？

- `EncryptedSharedPreferences`、SQLCipher。
- Keystore 存密钥；生物识别加密。
- 禁止明文存 Token 于 log。

### 12.3 组件导出风险？

`exported=true` 的 Activity/Service 可被外部调用；需权限校验、签名验证。Android 12+ 需显式声明 `android:exported`。

---

## 13. 构建与工程化

### 13.1 Gradle 构建流程？

初始化 → 配置阶段（解析 `build.gradle`）→ 执行阶段（Task 有向无环图）。

### 13.2 `implementation` 和 `api` 的区别？

- `implementation`：依赖对外不可见，编译隔离，加快构建。
- `api`：传递依赖给上游模块。

### 13.3 混淆 R8 做什么？

代码压缩、优化、混淆；`proguard-rules.pro` 保留反射、JNI、序列化类。

### 13.4 多渠道打包？

`productFlavors` + `manifestPlaceholders` / 资源替换；或使用 Walle 等写入渠道号。

---

## 14. Flutter 混合开发（OSL 相关高频）

### 14.1 混合架构常见方案？

| 方案 | 说明 |
|------|------|
| Flutter Module + AAR | 原生壳嵌入 Flutter 引擎 |
| FlutterBoost | 混合栈路由，Flutter 页与 Native 页互跳 |
| MethodChannel | 双向通信（登录态、Header、刷新资产） |

OSL：`FlutterEmbeddedActivity` + FlutterBoost + `IFlutterService`。

### 14.2 MethodChannel 原理？

BinaryMessenger 跨平台消息通道；Flutter `MethodChannel.invokeMethod` ↔ Native `MethodCallHandler`。异步回调需主线程注意。

### 14.3 混合栈页面生命周期问题？

Flutter 页在 Boost 容器中可能 pause/resume 与 Native 不同步；需注意 WS 订阅、定时器在 `onResume`/`onPause` 配对。

---

## 15. 场景与设计题

### 15.1 设计一个图片加载库要考虑什么？

三级缓存（内存 LRU、磁盘、网络）、线程池、Bitmap 复用（inBitmap）、降采样、生命周期绑定、GIF/WebP、占位与错误图。

### 15.2 如何实现应用内换肤（主题）？

`Resources` 替换（不推荐）、`LayoutInflater.Factory2`、主题属性（`?attr`）、夜间模式 `uiMode`、动态下载资源包。

### 15.3 下拉刷新 + 上拉加载更多如何实现？

`SwipeRefreshLayout` + `RecyclerView.OnScrollListener`；或 `Paging 3` 统一分页；注意重复触发与空态。

### 15.4 如何保证交易订单不被重复提交？

按钮防抖、请求中禁用、幂等 ID（clientOrderId）、服务端去重、乐观锁。

### 15.5 WebSocket 行情页如何设计？

- 单连接多频道复用。
- 引用计数订阅/退订。
- snapshot + incremental merge。
- UI 节流（`sample`/`throttle`）。
- 断连检测 + 指数退避重连 + 重订阅。
- 前后台 `ProcessLifecycleOwner` 管理。

### 15.6 自绘 K 线图要注意什么？

子线程算指标、主线程绘制；局部刷新；对象池；双指缩放平移矩阵；大量历史数据分页加载；last/mark/index 多数据源切换。

---

## 16. 算法与手写代码（Android 岗偶考）

### 16.1 常见算法题

- 两数之和、反转链表、有效括号。
- LRU 缓存（对应 `LruCache` 原理）。
- 二叉树层序遍历。
- 快排 / 归并思路。

### 16.2 常见手写代码

```kotlin
// 单例（双重检查）
class Singleton private constructor() {
    companion object {
        @Volatile
        private var instance: Singleton? = null
        fun getInstance() = instance ?: synchronized(this) {
            instance ?: Singleton().also { instance = it }
        }
    }
}
```

```kotlin
// 主线程安全执行任务
fun runOnUiThread(action: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        action()
    } else {
        Handler(Looper.getMainLooper()).post(action)
    }
}
```

```kotlin
// 快速幂
fun fastPow(x: Double, n: Int): Double {
    var base = x
    var exp = n.toLong()
    var result = 1.0
    while (exp > 0) {
        if (exp and 1 == 1L) result *= base
        base *= base
        exp = exp shr 1
    }
    return result
}
```

---

## 17. 行为与项目深挖（结合 OSL 可准备）

面试官常根据简历追问，建议准备以下话术要点：

| 方向 | 可讲内容 |
|------|----------|
| 模块化 | Feature 隔离、Core Service 接口、TheRouter 路由 |
| 合约交易 | WS MultiSub、snapshot/update 合并、公式 QuickJS |
| K 线 / 深度 | REST+WS 双通道、增量 K 线合并、订单簿 checksum |
| 混合栈 | FlutterBoost 路由、MethodChannel 刷新资产 |
| 性能 | 列表 DiffUtil、协程 IO、启动优化、LeakCanary |
| 质量 | 混淆规则、多环境 Flavor、CI 构建 |

**STAR 法则：** Situation → Task → Action → Result，用数据说明（如启动耗时降低 X%、崩溃率、包体积）。

---

## 18. 复习 checklist

- [ ] Activity/Fragment 生命周期与场景题
- [ ] Handler 机制与内存泄漏
- [ ] View 绘制与事件分发
- [ ] MVVM + Flow/StateFlow
- [ ] 协程作用域与异常处理
- [ ] Binder / IPC 概念
- [ ] OkHttp/Retrofit 链路与拦截器
- [ ] WebSocket 重连与增量合并
- [ ] 内存泄漏检测与 ANR 排查
- [ ] RecyclerView 优化
- [ ] 模块化与依赖倒置
- [ ] 1–2 个项目深挖故事（OSL 合约/K 线/混合栈）

---

## 19. 相关文档

- [README](./README.md) — OSL 项目架构与技术栈
- [合约业务梳理](1.CoreBiz/合约业务梳理.md) — WS、公式、交易终端
- [合约K线与交易深度技术要点](1.CoreBiz/合约K线与交易深度技术要点.md) — K 线/深度专项
