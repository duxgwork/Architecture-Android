## 一、suspend 函数的实现原理

### 1.1 什么是 suspend 函数

suspend 函数是 Kotlin 协程的核心，它标记一个函数可以被挂起和恢复，但**不会阻塞底层线程**。

```kotlin
suspend fun fetchData(): String {
    delay(1000) // 挂起点
    return "data"
}
```

### 1.2 编译期变换：状态机（CPS + StateMachine）


suspend 函数在编译后会被转换为 **状态机（State Machine）**，核心变换包括两部分：

#### （1）CPS 变换（Continuation Passing Style）

编译器会给 suspend 函数额外添加一个 `Continuation<T>` 参数：

```kotlin
// 源码
suspend fun fetchData(): String

// 编译后（伪代码）
fun fetchData(continuation: Continuation<String>): Any?
```

`Continuation<T>` 接口定义：

```kotlin
public interface Continuation<in T> {
    public val context: CoroutineContext  // 协程上下文
    public fun resumeWith(result: Result<T>) // 恢复执行
}
```

返回值变为 `Any?`，有三种情况：
- **`COROUTINE_SUSPENDED`**：协程被挂起，结果稍后通过 Continuation 回调
- **实际结果**：协程同步完成，直接返回结果
- **异常**：通过 `Result.failure()` 传递

#### （2）状态机变换

编译器将 suspend 函数体拆分为多个状态（label），每个挂起点就是一个状态边界：

```kotlin
// 源码
suspend fun fetchData(): String {
    val a = requestA()  // 挂起点1
    val b = requestB()  // 挂起点2
    return a + b
}

// 编译后状态机（伪代码）
fun fetchData(continuation: Continuation<String>): Any? {
    val sm = if (continuation is FetchDataSM) continuation else FetchDataSM(continuation)

    when (sm.label) {
        0 -> {
            // 第一次执行：发起 requestA
            sm.label = 1
            sm.result = requestA(sm) // 传入自身作为continuation
            if (sm.result == COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
            // 如果没挂起，直接往下走
        }
        1 -> {
            // requestA 完成后恢复：发起 requestB
            val a = sm.result as String
            sm.label = 2
            sm.result = requestB(sm)
            if (sm.result == COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
        }
        2 -> {
            // requestB 完成后恢复：返回结果
            val a = sm.a
            val b = sm.result as String
            return a + b
        }
    }
}
```

**关键点**：
- 每个 suspend 函数编译后都会生成一个 **状态机内部类**，保存局部变量和当前 label
- 挂起时保存当前状态（label + 局部变量到状态机对象中），线程可以去执行其他任务
- 恢复时通过 `resumeWith` 回调，重新进入状态机，跳转到对应 label 继续执行
- **suspend 函数只能在协程或另一个 suspend 函数中调用**，因为需要 Continuation 参数

### 1.3 COROUTINE_SUSPENDED 的信号本质

`COROUTINE_SUSPENDED` 本质上是一个信号标记：
- **返回它** → "我还没完，先让出线程，等回调再叫我"
- **不返回它** → "我完事了，结果给你，你继续跑"

这使得 Kotlin 协程在真正需要异步时才挂起，同步场景下保持零开销，实现了"协程是轻量级线程"的设计目标。

```kotlin
// 伪代码：调用方如何处理返回值
val result = loadData(continuation)
if (result === COROUTINE_SUSPENDED) {
    // 协程已挂起，当前方法返回，让出线程
    // 等 Continuation.resume() 被调用后，状态机恢复继续执行
    return COROUTINE_SUSPENDED
} else {
    // 结果已就绪（同步返回），直接使用，无需挂起
    val data = result as String
    // 继续后续逻辑...
}
```

### 1.4 挂起与恢复的完整流程

#### （1）挂起（Suspend）
- 每个挂起点（suspend 函数调用处）对应状态机的一个 `case` 分支，`label` 初始值为 0，首次进入 `case 0`
- 遇到挂起函数时，`label` 加 1（记录恢复点），然后调用挂起函数
- 若挂起函数返回 `COROUTINE_SUSPENDED`，方法直接 `return`，**挂起的本质就是 return**

#### （2）恢复（Resume）
- 异步操作完成后，调用 `continuation.resumeWith(result)` 回调
- `ContinuationImpl.resumeWith()` 内部再次调用 `invokeSuspend(result)`
- 根据保存的 `label` 跳转到对应的 `case` 分支，将 `result` 作为挂起函数的返回值
- 方法恢复执行，**恢复的本质就是 callback 回调**

#### （3）本质总结
- **协程挂起 = 方法 return**（让出线程）
- **协程恢复 = callback 回调**（从挂起点继续）

```kotlin
// 伪代码：编译器转换后的挂起函数
fun loadData(continuation: Continuation<String>): Any {
    // 场景 1：结果已缓存，无需挂起
    if (cache != null) {
        return cache  // 直接返回实际结果，调用方继续执行
    }

    // 场景 2：需要网络请求，必须挂起
    networkRequest { result ->
        continuation.resume(result)  // 异步回调，恢复协程
    }
    return COROUTINE_SUSPENDED  // 告诉调用方：我挂起了，等回调
}
```

### 1.5 Continuation 的辅助方法

```kotlin
public interface Continuation<in T> {
    // 对应这个 Continuation 的协程上下文
    public val context: CoroutineContext

    // 恢复相应协程的执行，传递一个成功或失败的结果作为最后一个挂起点的返回值
    //      相当于 CallBack 的 onSuccess   结果
    //                 ↓                 ↓
    public fun resumeWith(result: Result<T>)
}

// 将 value 作为最后一个挂起点的返回值，恢复协程执行
fun <T> Continuation<T>.resume(value: T): Unit =
    resumeWith(Result.success(value))

// 恢复协程执行，在最后一个挂起点之后重新抛出异常
fun <T> Continuation<T>.resumeWithException(exception: Throwable): Unit =
    resumeWith(Result.failure(exception))
```

### 1.6 状态机的底层本质

状态机变换本质上是一种 **代码重写**，将递归的 CPS 调用改写为 `switch-case` 形式的迭代状态机，避免深层递归导致的栈溢出。每个挂起点就是一个 `label`，局部变量被提升到状态机对象的字段中保存。

> **核心总结**：挂起函数是 Kotlin 协程的核心优势，"结构化并发"是 Kotlin 协程的第二大优势。协程最核心的就是**挂起与恢复**。

---

## 二、协程构建器

### 2.1 协程构建器的作用

协程构建器本质上是把一个 suspend 函数"启动起来"的入口函数。suspend 函数自己不会执行，必须依附于一个协程，构建器就是负责创建协程、绑定作用域、启动执行的"启动器"。

| 构建器 | 返回值 | 特点 | 典型场景 |
|--------|--------|------|----------|
| launch | Job | 发射即忘，不返回结果 | 后台任务、UI 更新 |
| async | Deferred<T> | 有返回值，通过 await() 获取结果 | 并行请求、需要结果的异步任务 |
| runBlocking | T | 阻塞当前线程直到协程完成 | main 函数入口、测试代码 |
| withContext | T | 不创建新协程，只切换上下文 | 切到 IO 线程做计算再切回来 |
| coroutineScope | T | 创建子作用域，等待所有子协程完成后才返回 | 并行执行多个子任务并汇总 |

### 2.2 launch

```kotlin
public fun CoroutineScope.launch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job
```

- 返回 `Job`，代表协程的生命周期
- **fire-and-forget**：不返回结果，异常通过 `CoroutineExceptionHandler` 处理
- 默认 `CoroutineStart.DEFAULT`：立即调度执行

### 2.3 async

```kotlin
public fun <T> CoroutineScope.async(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T
): Deferred<T>
```

- 返回 `Deferred<T>`（继承自 `Job`），可以通过 `await()` 获取结果
- **异常是 lazy 的**：如果不调用 `await()`，异常会被延迟到 `await()` 时才抛出（`start = LAZY` 时）
- 默认模式下（`DEFAULT`），async 中的异常会立即传播给父协程

### 2.4 runBlocking

```kotlin
public fun <T> runBlocking(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): T
```

- **阻塞当前线程**直到协程完成
- 主要用于 main 函数和测试，**不应在业务代码中使用**
- 内部使用 `BlockingEventLoop` 作为默认调度器

### 2.5 withContext（不创建新协程）

```kotlin
public suspend fun <T> withContext(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T
): T
```

- **挂起当前协程**，在指定上下文（线程）中执行 block
- 不创建新的子协程，复用当前协程的 Job
- 是 suspend 函数，只能在协程或 suspend 函数中调用

### 2.6 coroutineScope（创建子作用域）

```kotlin
public suspend fun <T> coroutineScope(
    block: suspend CoroutineScope.() -> T
): T
```

- 创建一个新的 `CoroutineScope`，继承当前上下文
- **会等待所有子协程完成**后才返回
- 子协程异常会取消整个 scope（结构化并发）

### 2.7 supervisorScope

- 与 `coroutineScope` 类似，但子协程异常**不会**取消兄弟协程和父协程
- 适用于子任务相互独立的场景

### 2.8 底层做了什么

以 launch 为例，调用 `launch { ... }` 时底层做了这几件事：

#### （1）构建 CoroutineContext

把 CoroutineScope 自带的 context 和传入的 context 做合并：

```kotlin
// 简化逻辑
val context = this.context + givenContext  // 比如 Dispatchers.IO + Job + CoroutineName
```

合并后的 context 包含：调度器（决定在哪个线程执行）、Job（协程的生命周期节点）、异常处理器等。

#### （2）创建协程对象（状态机）

通过 `createCoroutine` 把 suspend 函数体包装成一个协程对象。编译器在编译期把 suspend 函数体改写成了状态机：

```kotlin
// 你写的代码
launch {
    val a = fetchData()    // 挂起点 1
    val b = processData(a) // 挂起点 2
    saveData(b)
}

// 编译器生成的状态机（简化）
when(label) {
    0 -> {
        // 执行到 fetchData()，保存状态，挂起
        label = 1
        fetchData(continuation)
    }
    1 -> {
        // 从 fetchData 恢复，a = result
        // 执行到 processData()，保存状态，挂起
        label = 2
        processData(a, continuation)
    }
    2 -> {
        // 从 processData 恢复，b = result
        saveData(b)
    }
}
```

每个挂起点都会把当前执行状态保存到 Continuation 对象里，挂起后线程可以去干别的事，恢复时从上次的位置继续执行。

#### （3）启动协程

调用 `startCoroutine`，根据启动模式决定是立即执行还是调度到指定线程后执行：

- `Dispatchers.Main` → 通过 Handler post 到主线程
- `Dispatchers.IO` → 提交到线程池
- 遇到第一个挂起点时，协程挂起，控制权交还给调度器

#### （4）Job 层级关系

每个构建器启动时都会创建一个 Job，这个 Job 是父 Scope 的 Job 的子节点，形成一棵 Job 树。这就是结构化并发的基础——父协程取消时，所有子协程自动取消。

### 2.9 为什么不建议使用 GlobalScope

`GlobalScope` 是一个全局的、顶级的 CoroutineScope，它的 Job 跟应用生命周期一样长，永远不会被主动取消。

#### ① 无法取消 → 资源泄漏

```kotlin
// 危险写法
fun loadData() {
    GlobalScope.launch {
        val data = api.fetchData()  // 网络请求可能要 10 秒
        updateUI(data)
    }
}
// 如果用户在 10 秒内离开了页面（Activity 销毁），
// 协程还在跑，请求还在发，回来更新 UI 时可能崩溃
```

用 `lifecycleScope` 或 `viewModelScope` 的话，页面销毁时协程自动取消。

#### ② 破坏结构化并发

结构化并发的核心是：子协程的生命周期不能超出父作用域。`GlobalScope` 没有父作用域，它启动的协程是"孤儿"——没人管它什么时候该结束，也没人等它完成。

```kotlin
GlobalScope.launch { taskA() }
GlobalScope.launch { taskB() }
// 没有任何机制能等待这两个都完成，也没有机制能统一取消它们
```

#### ③ 异常处理失控

`GlobalScope` 启动的协程如果抛异常，会沿着 Job 树向上传播，但 `GlobalScope` 没有父 Job，最终会走到默认的 `CoroutineExceptionHandler`，如果没设置就直接崩溃。

#### 推荐做法

| 场景 | 推荐 Scope |
|------|------------|
| ViewModel 中 | viewModelScope |
| Activity/Fragment 中 | lifecycleScope |
| main 函数入口 | runBlocking |
| 需要自定义生命周期 | CoroutineScope(SupervisorJob() + Dispatchers.Main) |

> Kotlin 1.8 之后，`GlobalScope` 已被标记为 `@DelicateCoroutinesApi`，IDE 会直接给出警告。

### 2.10 构建器对比总结

| 构建器 | 阻塞线程 | 返回值 | 异常处理 | 是否创建新协程 |
|--------|---------|--------|---------|-------------|
| launch | 否 | Job | 立即抛出 | 是（子协程） |
| async | 否 | Deferred<T> | await时抛出 | 是（子协程） |
| runBlocking | **是** | T | 抛出到调用线程 | 是 |
| withContext | 否 | T | 抛出到调用方 | **否** |
| coroutineScope | 否 | T | 子协程异常取消scope | 是（子作用域） |

> **一句话总结**：协程构建器是 suspend 函数的启动器，底层通过状态机 + Continuation 实现挂起和恢复，通过 Job 树实现结构化并发。GlobalScope 因为没有生命周期边界、无法取消、破坏结构化并发，所以不推荐使用。

---

## 三、核心概念详解

### 3.1 CoroutineContext

`CoroutineContext` 是一个**不可变的元素集合**，每个元素是一个 `IndexedSet` 中的键值对。

```kotlin
public interface CoroutineContext {
    public operator fun <E : Element> get(key: Key<E>): E?
    public fun <R> fold(initial: R, operation: (R, Element) -> R): R
    public operator fun plus(context: CoroutineContext): CoroutineContext
    public fun minusKey(key: Key<*>): CoroutineContext
}
```

**核心元素**：

| 元素 | 作用 |
|------|------|
| `Job` | 协程生命周期管理 |
| `CoroutineDispatcher` | 线程调度器（Dispatchers.IO / Main / Default） |
| `CoroutineName` | 协程名称（调试用） |
| `CoroutineExceptionHandler` | 未捕获异常处理器 |
| `ContinuationInterceptor` | 拦截 Continuation（Dispatcher 的父接口） |

**合并规则（`+` 运算符）**：
- 相同 key 的元素会被**右侧覆盖**
- `Job` 比较特殊：如果两侧都有 Job，会创建 `SupervisorJob` 将它们关联

```kotlin
// 示例
val context = Dispatchers.IO + CoroutineName("MyCoroutine") + Job()
```

### 3.2 CoroutineStart

协程的启动模式：

```kotlin
public enum class CoroutineStart {
    DEFAULT,    // 默认：立即调度到调度器执行
    LAZY,       // 惰性：只在需要时（await/start/join）才调度执行
    ATOMIC,     // 原子：不可取消，一旦启动就必须执行到第一个非挂起点
    UNDISPATCHED // 非调度：在当前线程立即执行，直到第一个挂起点后才切换调度器
}
```

**深入对比**：

- **DEFAULT**：调用 `dispatch` 将协程调度到对应线程，可能存在调度延迟
- **LAZY**：不立即调度，调用 `start()` 或 `join()`/`await()` 时才启动；适合按需执行
- **ATOMIC`**：与 DEFAULT 类似，但在第一个挂起点之前不可取消（`cancel` 不会中断）
- **UNDISPATCHED**：直接在**当前调用线程**执行到第一个挂起点，之后才按 Dispatcher 调度

### 3.3 Job

Job 是协程生命周期的核心抽象：

```kotlin
public interface Job : CoroutineContext.Element {
    // 状态查询
    public val isActive: Boolean
    public val isCompleted: Boolean
    public val isCancelled: Boolean

    // 生命周期操作
    public fun cancel(cause: CancellationException? = null)
    public fun cancelChildren(cause: CancellationException? = null)
    public suspend fun join() // 挂起等待完成

    // 子任务管理
    public val children: Sequence<Job>
    public fun invokeOnCompletion(handler: CompletionHandler): DisposableHandle
}
```

**Job 的状态流转**：

```
       new → active → completing → completed
                  ↘ cancelling → cancelled
```

| 状态 | isActive | isCompleted | isCancelled |
|------|----------|-------------|-------------|
| New | false | false | false |
| Active | true | false | false |
| Completing | true | false | false |
| Cancelling | false | false | true |
| Cancelled | false | true | true |
| Completed | false | true | false |

**Job 的层级关系**：
- `Job()` / `launch`：创建子 Job，父取消则子取消（**单向传播**）
- `SupervisorJob()`：子协程失败不影响父和兄弟
- 子协程全部完成后，父 Job 才进入 completed 状态

### 3.4 CoroutineScope

```kotlin
public interface CoroutineScope {
    public val coroutineContext: CoroutineContext
}
```

**作用**：
- 管理协程的**作用域**，实现结构化并发
- 所有通过 scope 启动的协程都继承其 context（特别是 Job）
- scope 被 cancel 时，所有子协程自动取消

**常见 scope**：
- `GlobalScope`：生命周期等于应用，**不推荐使用**
- `lifecycleScope`（Lifecycle）：绑定 Activity/Fragment 生命周期
- `viewModelScope`（ViewModel）：ViewModel 销毁时自动取消
- `rememberCoroutineScope()`（Compose）：绑定 Composable 生命周期

**自定义 scope**：
```kotlin
class MyRepository : CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.IO + job

    fun destroy() { job.cancel() }
}
```

---

## 四、切线程的本质与 withContext 完整流程

### 4.1 切线程的本质

协程本身运行在某一条线程上，协程的线程切换本质上是通过 **Dispatcher（调度器）** 将协程的执行权从一条线程“投递”到另一条线程。

#### ① CoroutineContext 与 ContinuationInterceptor

每个协程都持有一个 `CoroutineContext`，其中包含一个 `ContinuationInterceptor`（即 Dispatcher）。协程每次挂起后恢复时，都会经过这个拦截器，由它决定恢复执行时使用哪条线程。

```kotlin
// CoroutineContext 的核心结构
public interface CoroutineContext {
    public fun <R> fold(initial: R, operation: (R, Element) -> R): R
    public operator fun <E : Element> get(key: Key<E>): E?
}

// Dispatcher 本质上就是一个 ContinuationInterceptor
public interface ContinuationInterceptor : CoroutineContext.Element {
    public fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T>
    public fun releaseInterceptedContinuation(continuation: Continuation<T>) {}
}
```

#### ② 底层 dispatch 源码

```kotlin
// Dispatchers.IO 的核心调度逻辑（简化）
// ExecutorCoroutineDispatcherImpl 继承自 CoroutineDispatcher
override fun dispatch(context: CoroutineContext, block: Runnable) {
    // 将 block 投递到 IO 线程池
    DefaultExecutor.execute(block)  // 实际是 CoroutineScheduler
}

// Dispatchers.Main 的核心调度逻辑（Android）
// HandlerContext 内部持有 Handler
override fun dispatch(context: CoroutineContext, block: Runnable) {
    handler.post(block)  // 就是 Handler.post()，投递到主线程 MessageQueue
}

// ContinuationInterceptor.interceptContinuation() 的核心逻辑
// 它将原始的 Continuation 包装一层，使得 resumeWith() 时经过 dispatch
public override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> =
    DispatchedContinuation(continuation, this).also {
        // 每次 resumeWith 时，内部会调用 dispatcher.dispatch() 进行线程切换
    }
```

#### ③ 核心总结

- **切线程 = 换 Dispatcher**：`withContext(Dispatchers.IO)` 本质是将后续 Continuation 的恢复逻辑交给 `Dispatchers.IO` 的线程池执行
- **Dispatch = Executor.execute()**：所有 Dispatcher 底层都是将任务投递到某个 Executor（线程池或 Handler）
- **挂起是前提**：只有在挂起函数返回 `COROUTINE_SUSPENDED` 后，当前线程才被释放，后续恢复才能在新的线程上执行。如果挂起函数同步返回结果（未挂起），则不会发生线程切换
- **恢复时切回**：`withContext` 结束后自动恢复到原来的 Dispatcher，因为它保存了原始 Continuation 的 context
- **协程不会阻塞线程**：挂起时释放线程，恢复时由 Dispatcher 调度到合适的线程，整个过程是非阻塞的

### 4.2 withContext 源码级流程分析

```kotlin
suspend fun <T> withContext(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T
): T
```

**完整执行流程**：

```
1. 调用 withContext(Dispatchers.IO) { ... }
       ↓
2. 创建新的上下文：updatedContext = currentContext + Dispatchers.IO
       ↓
3. 创建 DispatchedTask，将 block 包装为 suspendCoroutineUninterceptedOrReturn
       ↓
4. 检查是否需要切换线程：
   - 如果 dispatcher 相同 → 直接执行（优化路径）
   - 如果 dispatcher 不同 → 进入调度流程
       ↓
5. 挂起当前协程（返回 COROUTINE_SUSPENDED）
   - 保存 Continuation，包装为 DispatchedTask
       ↓
6. 通过 Dispatchers.IO 的 dispatch() 方法
   - 将任务放入 IO 线程池的队列
       ↓
7. IO 线程池中的工作线程取到任务并执行
   - 恢复 Continuation → 执行 block
       ↓
8. block 执行完成
   - 通过 originalContinuation.resumeWith(result) 恢复原协程
       ↓
9. 原协程的调度器（如 Main）接管
   - 将结果投递回原线程执行
       ↓
10. 原协程恢复执行，拿到 withContext 的返回值
```

### 4.3 关键源码解读

```kotlin
// WithContext.kt 核心逻辑
public suspend fun <T> withContext(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T
): T = suspendCoroutineUninterceptedOrReturn { uCont ->
    // 1. 计算新上下文
    val newContext = uCont.context.newCoroutineContext(context)
    // 2. 创建新的 Continuation
    val cont = uCont as? DispatchedContinuation<*>
    // 3. 检查是否需要切换
    if (newContext.sameContext(uCont.context)) {
        // 优化：不需要切换，直接执行
        block.startCoroutineUninterceptedOrReturn(uCont)
    } else {
        // 需要切换：创建 DispatchedTask 并调度
        val task = DispatchedTask(newContext, uCont, block)
        task.schedule()
        COROUTINE_SUSPENDED
    }
}
```

### 4.4 性能优化点

- **同 Dispatcher 优化**：如果新旧 Dispatcher 相同，不会发生线程切换，直接执行
- **线程检查**：`Dispatchers.Main.immediate` 可以避免不必要的 post 回主线程
- **无额外 Job 创建**：withContext 不创建新的子 Job，复用当前协程的 Job

---

## 五、Dispatchers 的工作原理

### 5.1 常见 Dispatcher

| Dispatcher | 底层实现 | 适用场景 |
|-----------|--------|--------|
| `Dispatchers.Main` | 将 Runnable post 到 Android 主线程的 Handler（`Handler(Looper.getMainLooper())`） | UI 操作 |
| `Dispatchers.IO` | 共享线程池（最大 64 线程或 CPU 核心数，取较大值），适合网络/磁盘 IO | IO 操作 |
| `Dispatchers.Default` | CPU 密集型线程池（CPU 核心数 - 1），适合计算密集型任务 | CPU 计算 |
| `Dispatchers.Unconfined` | 不切换线程，在调用者线程直接执行 | 特殊场景 |

### 5.2 Dispatchers.IO 工作原理

```
Dispatchers.IO 内部结构：

┌─────────────────────────────────────────┐
│            IO Scheduler                  │
│                                          │
│  ┌──────────────────────┐               │
│  │   共享线程池            │               │
│  │   核心线程数 = CPU核心数  │               │
│  │   最大线程数 = 64       │               │
│  │   空闲超时 = 60s        │               │
│  └──────────────────────┘               │
│         ↑ 与 Default 共享线程池            │
│                                          │
│  ┌──────────────────────┐               │
│  │   任务队列（FIFO）      │               │
│  └──────────────────────┘               │
└─────────────────────────────────────────┘
```

**核心机制**：

1. **线程池共享**：`Dispatchers.IO` 和 `Dispatchers.Default` **共享同一个底层线程池**，但 IO 有自己的任务队列
2. **弹性线程**：当线程都在忙时，可以创建新线程（最多 64 个）；空闲线程 60 秒后回收
3. **任务调度**：调用 `dispatch(group, block)` 时，将任务放入队列，如果有空闲线程则立即执行，否则创建新线程（不超过上限）
4. **公平调度**：使用 FIFO 队列，保证任务按提交顺序执行

### 5.3 Dispatchers.Main 工作原理

```kotlin
// Android 平台实现
internal class AndroidDispatcherFactory : MainDispatcherFactory {
    override fun createDispatcher(priority: Int): MainCoroutineDispatcher {
        return HandlerDispatcher(Handler(Looper.getMainLooper()))
    }
}
```

- 底层通过 `Handler(Looper.getMainLooper())` 实现
- `dispatch()` 调用 `Handler.post(runnable)` 将任务投递到主线程消息队列
- `Dispatchers.Main.immediate`：如果当前已在主线程，直接执行不 post

### 5.4 ContinuationInterceptor 与线程切换

> 关于 ContinuationInterceptor 如何包装 Continuation 实现线程切换，详见第四章「切线程的本质」。核心原理是：每次协程挂起后恢复时，`interceptContinuation` 将原始 Continuation 包装为 `DispatchedContinuation`，其 `resumeWith()` 内部调用 `dispatcher.dispatch()` 将恢复操作调度到目标线程。这就是**每次挂起恢复后能回到正确线程**的原因。

---

## 六、协程的并发

### 6.1 基本并发模式

```kotlin
// 串行（慢）
val a = fetchA() // 1s
val b = fetchB() // 1s
// 总耗时 2s

// 并发（快）
coroutineScope {
    val deferredA = async { fetchA() } // 1s
    val deferredB = async { fetchB() } // 1s
    val result = deferredA.await() + deferredB.await()
}
// 总耗时 1s
```

### 6.2 并发原语

#### （1）launch + join

```kotlin
coroutineScope {
    val job1 = launch { task1() }
    val job2 = launch { task2() }
    job1.join()
    job2.join()
}
```

#### （2）async + await

```kotlin
coroutineScope {
    val d1 = async { task1() }
    val d2 = async { task2() }
    val result = d1.await() + d2.await()
}
```

#### （3）CoroutineScope 批量并发

```kotlin
coroutineScope {
    val results = (1..100).map { i ->
        async { fetchData(i) }
    }.awaitAll()
}
```

### 6.3 并发安全问题分析

协程的并发和线程并发一样存在共享资源竞争问题：

```kotlin
var counter = 0

// 1000个协程并发递增，结果可能 < 1000
coroutineScope {
    repeat(1000) {
        launch {
            counter++ // 非原子操作，存在竞争
        }
    }
}
```

**解决方案**：

| 方案 | 机制 | 性能 |
|------|------|------|
| `Mutex` | 协程锁 | 高（挂起不阻塞线程） |
| `AtomicInteger` | CAS 原子操作 | 最高 |
| `volatile` | 可见性保证 | 仅保证可见性，不保证原子性 |
| `Dispatchers.Main` | 单线程 | 天然线程安全 |
| `Actor` | 消息传递 | 高（无共享状态） |

### 6.4 结构化并发

```kotlin
// 结构化并发的核心原则：
// 1. 子协程异常自动传播到父协程
// 2. 父协程取消时自动取消所有子协程
// 3. 父协程等待所有子协程完成后才完成

coroutineScope {
    launch { /* 子协程1 */ }
    launch { /* 子协程2 */ }
    // 两个子协程都完成后，coroutineScope 才返回
}
```

---

## 七、协程锁 Mutex

### 7.1 为什么需要 Mutex

协程中不能使用 `synchronized` 关键字（因为 synchronized 阻塞的是线程，而协程挂起不占用线程）。`kotlinx.coroutines.sync.Mutex` 是协程专用的互斥锁。

### 7.2 Mutex 基本用法

```kotlin
val mutex = Mutex()
var counter = 0

suspend fun increment() {
    mutex.lock()    // 挂起等待获取锁（不阻塞线程）
    try {
        counter++
    } finally {
        mutex.unlock()
    }
}

// 更推荐的写法
suspend fun incrementSafe() {
    mutex.withLock {
        counter++
    }
}
```

### 7.3 Mutex 实现原理

```kotlin
public class Mutex(locked: Boolean = false) {
    // 内部使用原子引用 + 等待队列
    private val _lock = atomic(EmptyLock)

    public suspend fun lock() {
        // 1. 尝试通过 CAS 获取锁
        if (tryLock()) return
        // 2. 获取失败：将当前协程包装为 LockCont 放入等待队列
        // 3. 挂起当前协程（suspendCancellableCoroutine）
        // 4. 当锁释放时，从队列取出下一个等待者并 resume
    }

    public fun unlock() {
        // 1. 通过 CAS 释放锁
        // 2. 从等待队列取出下一个等待者
        // 3. 恢复该等待者的 Continuation
    }
}
```

**关键特点**：
- **公平锁**：按 FIFO 顺序分发锁，避免饥饿
- **挂起而非阻塞**：等待锁时协程被挂起，线程可以执行其他任务
- **可取消**：等待锁的协程可以被取消
- **不支持重入**：同一个协程不能重复 lock（会死锁）

### 7.4 Mutex vs synchronized vs Atomic

```
场景：1000个协程并发递增计数器

synchronized { counter++ }
  → 阻塞线程，协程场景下会浪费线程资源

Mutex.withLock { counter++ }
  → 挂起协程，线程可复用，性能更好

AtomicInteger.incrementAndGet()
  → CAS 无锁操作，性能最高，但只适用于简单原子操作
```

---

## 八、协程的异常处理机制

### 8.1 异常传播规则

```
异常传播方向：子协程 → 父协程 → 祖父协程 → ... → 顶层协程 Handler

launch 中的异常：
  → 立即向上传播给父协程
  → 父协程取消所有子协程 → 取消自身 → 传播到根

async 中的异常：
  → 异常存储在 Deferred 中
  → 调用 await() 时抛出
  → 如果不调用 await()，在协程完成时向父协程传播
```

**传播规则总结**：
- 异常沿协程层级向上传播，若中途被 `SupervisorJob` 或 `try-catch` 阻断，则不会到达顶层 Handler
- 未捕获异常最终到达顶层协程时，由 `CoroutineExceptionHandler` 处理；若无 Handler，则崩溃

### 8.2 异常处理器

#### （1）CoroutineExceptionHandler

```kotlin
val handler = CoroutineExceptionHandler { _, exception ->
    println("Caught: $exception")
}

// 只在根协程中生效（launch 的异常）
val scope = CoroutineScope(SupervisorJob() + handler)
scope.launch {
    throw RuntimeException("error") // 被 handler 捕获
}
```

**注意**：
- 只对 `launch` 有效，`async` 的异常由 `await()` 处理
- 只在**根协程**中生效（子协程的异常会先传播到父协程）

#### （2）try-catch

```kotlin
// 在协程内部直接捕获
launch {
    try {
        throw RuntimeException("error")
    } catch (e: Exception) {
        println("Caught: $e")
    }
}

// 捕获 withContext 的异常
try {
    withContext(Dispatchers.IO) {
        throw IOException("network error")
    }
} catch (e: IOException) {
    println("Caught: $e")
}
```

### 8.3 CancellationException 的特殊处理

```kotlin
// CancellationException 不会被 CoroutineExceptionHandler 捕获
// 它是协程取消的正常机制，不是错误

launch {
    try {
        delay(1000)
    } catch (e: CancellationException) {
        println("Cancelled!")
        throw e // 必须重新抛出！否则协程取消机制会被破坏
    }
}
```

**重要规则**：
- `CancellationException` 永远不应该被吞掉
- 如果需要做清理工作，在 catch 中执行后必须重新 throw
- 使用 `NonCancellable` + `withContext` 执行不可取消的清理代码

```kotlin
try {
    // 可取消的工作
} finally {
    withContext(NonCancellable) {
        // 这里的代码不会被取消
        saveData()
    }
}
```

### 8.4 SupervisorJob 与异常隔离

`SupervisorJob()` 只是一个普通的顶层函数，返回的对象是 `Job` 的子类。它与普通 `Job` 最大的区别在于：**当它的子 Job 发生异常时，其他子 Job 不会受到牵连**。

```kotlin
// 普通 Job：一个子协程异常 → 所有子协程取消
val job = Job()
val scope = CoroutineScope(job)
scope.launch { throw Error() } // 取消所有子协程和自身

// SupervisorJob：子协程异常 → 不影响其他子协程
val supervisor = SupervisorJob()
val scope = CoroutineScope(supervisor)
scope.launch { throw Error() } // 只取消自身，其他子协程不受影响
```

**普通 Job vs SupervisorJob 异常传播对比**：

```
普通 Job：
  parentJob
    ├── job1  ← 异常！
    ├── job2
    └── job3
  → job1 异常 → parentJob 取消 → job2、job3 全部被牵连取消

SupervisorJob：
  supervisorJob
    ├── job1  ← 异常！
    ├── job2
    └── job3
  → job1 异常 → 只取消 job1，job2、job3 不受影响
```

**注意**：在 `SupervisorJob` 场景下，顶层 Handler **无法捕获**子协程异常，需在子 Scope 中单独设置 Handler。

**supervisorScope**：
```kotlin
supervisorScope {
    launch { /* 异常不影响兄弟 */ }
    launch { /* 异常不影响兄弟 */ }
}
```

### 8.5 Flow 异常处理

Flow 的异常使用 `catch` 操作符捕获上游异常：

```kotlin
flowOf(1, 2, 3)
    .map { value ->
        if (value == 2) throw IOException("error")
        value
    }
    .catch { e -> 
        // 捕获上游异常，可以发射替代值
        println("Caught: $e")
        emit(-1) // 发射替代值
    }
    .collect { println(it) }
```

**注意**：`catch` 只能捕获**上游**（即它之前）的异常，不能捕获下游（`collect` 内部）的异常。

### 8.6 异常处理最佳实践

```kotlin
// 1. ViewModel 中使用 viewModelScope + SupervisorJob
class MyViewModel : ViewModel() {
    fun loadData() {
        viewModelScope.launch(SupervisorJob() + CoroutineExceptionHandler { _, e ->
            // 统一处理异常：更新UI状态
            _uiState.value = UiState.Error(e.message)
        }) {
            val data = repository.fetchData() // 可能抛异常
            _uiState.value = UiState.Success(data)
        }
    }
}

// 2. Repository 层让异常自然传播
class Repository {
    suspend fun fetchData(): Data {
        return withContext(Dispatchers.IO) {
            // 不捕获异常，让调用方决定如何处理
            api.getData()
        }
    }
}
```

---

## 九、Flow、StateFlow、SharedFlow、Channel

### 9.1 四种数据流全面对比

| 类型 | 热/冷 | 初始值 | 值去重 | 粘性（新订阅收最新值） | 多收集者 | 适用场景 |
|------|------|--------|--------|-------------------|---------|--------|
| **Flow** | 冷 | 无 | 否 | 否 | 每次独立执行 | 数据源（网络/DB），每次订阅重新获取 |
| **StateFlow** | 热 | 必须有 | 是 | 是 | 支持 | UI 状态管理（去重语义） |
| **SharedFlow** | 热 | 无 | 否 | 取决于 replay | 支持 | 事件广播（Toast、导航，replay=0） |
| **Channel** | 热（通信原语） | 无 | 否 | 否 | 一对一消费 | 协程间通信（有背压语义） |

**选择建议**：UI 状态 → StateFlow；一次性事件广播 → SharedFlow(replay=0)；每次订阅重新获取数据 → Flow（冷流）；协程间传递数据 → Channel。

### 9.2 Flow（冷流）

Flow 是**冷流**，没有收集者时不执行，每次 collect 时都会重新执行上游逻辑。天然支持背压，下游处理不过来时上游会挂起等待。

```kotlin
fun main() = runBlocking {
    flow {                  // 上游，发源地
        emit(1)
        emit(2)
        emit(3)
        emit(4)
        emit(5)
    }.filter { it > 2 }     // 中转站1
      .map { it * 2 }       // 中转站2
      .take(2)              // 中转站3
      .collect {            // 下游
          println(it)
      }
}
// 输出结果：6, 8
```

#### Flow 的操作符

```kotlin
// 转换操作符
flow.map { it * 2 }
flow.filter { it > 0 }
flow.take(5)
flow.distinctUntilChanged()

// 背压处理
flow.buffer()          // 在独立协程中并发执行生产和消费
flow.conflate()        // 跳过中间值，只处理最新值
flow.collectLatest { } // 取消上一次 collect，处理最新的

// 扁平化
flow.flatMapConcat { innerFlow }  // 串行（一个完成再下一个）
flow.flatMapMerge { innerFlow }   // 并发
```

#### Flow 的线程安全

```kotlin
// flow 的 emit 必须在同一个协程上下文中调用
// 如果需要从多个协程发射，使用 channelFlow

val flow = channelFlow {
    launch { send(1) }
    launch { send(2) }
    launch { send(3) }
}
// channelFlow 内部使用 Channel，线程安全
```

### 9.3 StateFlow（热流，表示状态）

StateFlow 无论是否有订阅者，都会执行发射数据流的操作，并且发布者与订阅者是**一对多**的关系。

**核心特性**：
- 内置 `distinctUntilChanged` 语义（连续相同值不重复通知）
- 新收集者立即收到最新 `value`（粘性）
- **不支持**传统意义上的"挂起式背压"，采用 **conflate（合并/跳过）** 策略：如果收集者处理不过来，中间值会被跳过，收集者只会收到最新值，不会阻塞生产者

```kotlin
class MainViewModel : ViewModel() {
    // 与 LiveData 不同的是，开发者必须为 MutableStateFlow 指定默认值
    private val _uiState = MutableStateFlow("")
    val uiState: StateFlow<String> = _uiState
}

lifecycleScope.launch {
    mainViewModel.uiState.collect {
        Log.d(TAG, "打印结果：${it}")
    }
}
```

### 9.4 SharedFlow（热流，表示事件）

SharedFlow 是 StateFlow 的一种**可配置性极高**的泛化数据流，可配置 `replay`（回放数量）、`extraBufferCapacity`（额外缓冲区大小）、`onBufferOverflow`（缓冲区满时的溢出策略）。

#### 溢出策略

```kotlin
// 1. SUSPEND（默认 replay = 0）：上游挂起等待，最安全的背压
MutableSharedFlow<Int>(
    extraBufferCapacity = 10,
    onBufferOverflow = BufferOverflow.SUSPEND
)

// 2. DROP_OLDEST：丢弃最旧的值，保留最新的，上游不会挂起
MutableSharedFlow<Int>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)

// 3. DROP_LATEST：丢弃最新的值，保留旧的，上游不会挂起
MutableSharedFlow<Int>(
    extraBufferCapacity = 10,
    onBufferOverflow = BufferOverflow.DROP_LATEST
)
```

#### replay 配置

```kotlin
// replay = 0：新收集者只能收到订阅之后发射的值（默认）
val events = MutableSharedFlow<Event>(replay = 0)
// replay = 1：新收集者立即收到最近 1 个历史值
val state = MutableSharedFlow<Int>(replay = 1)
// replay = 3：新收集者立即收到最近 3 个历史值
val history = MutableSharedFlow<String>(replay = 3)
```

### 9.5 Channel（热流，协程间通信）

Channel 是一个管道，管道的一端发送数据，另一端接收数据。可通过 `receiveAsFlow()` 转为 Flow。

```kotlin
public fun <E> Channel(
    capacity: Int = RENDEZVOUS,
    // 管道容量：
    //   RENDEZVOUS（默认，容量为0）
    //   CONFLATED（容量为1，新数据替代旧数据）
    //   UNLIMITED（无限容量）
    //   BUFFERED（默认容量是64，具体容量由VM参数决定）
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    // 管道满时的溢出策略：
    //   SUSPEND（默认，上游挂起等待）
    //   DROP_OLDEST（丢弃最旧的值）
    //   DROP_LATEST（丢弃最新的值）
    onUndeliveredElement: ((E) -> Unit)? = null
    // 当发送的数据无法被接收方处理时的回调
    // 适用于“接收方对数据是否被消费特别关心”的场景
): Channel<E>
```

**Channel 的关闭**：
- 用完 channel 后要主动调用 `channel.close()`，否则会造成资源浪费
- 使用 `produce{}` 高阶函数可以自动调用 `close()`：

```kotlin
fun main() = runBlocking {
    val channel: ReceiveChannel<Int> = produce {
        (1..3).forEach {
            send(it)
            logX("Send: $it")
        }
    }

    launch {
        for (i in channel) {
            logX("Receive: $i")
        }
    }
    logX("end")
}
```

### 9.6 Flow 的 zip 与 combine

- **zip**：两个 Flow 各发射一个元素配对，必须两边都有新值才触发。结果数量取决于**较慢**的 Flow
- **combine**：任一 Flow 发射新值时，用两个 Flow 的最新值组合触发。结果数量取决于**较快**的 Flow

```kotlin
flowA.zip(flowB) { a, b -> "$a $b" }      // 一一对应
flowA.combine(flowB) { a, b -> "$a $b" }  // 任一侧更新即触发
```

**组合操作符对比**：

| 操作符 | 触发条件 | 结果数量 | 适用场景 |
|--------|---------|---------|--------|
| `zip` | 两边都有新值 | 取决于较慢的 Flow | 配对操作（如请求参数+请求体） |
| `combine` | 任一侧发射新值 | 取决于较快的 Flow | 状态组合（如多个 UI 状态合并） |
| `merge` | 任一发射 | 所有元素 | 合并多个同类型数据源 |

### 9.7 Select

`select` 表达式允许**等待多个挂起操作中的第一个完成**：

```kotlin
select<T> {
    channel1.onReceive { value ->
        // channel1 收到数据
        value
    }
    channel2.onReceive { value ->
        // channel2 收到数据
        value
    }
    deferred.onAwait { result ->
        // deferred 完成
        result
    }
    onTimeout(1000) {
        // 超时
        "timeout"
    }
}
```

**Select 的子句**：

| 子句 | 说明 |
|------|------|
| `onReceive` | Channel 接收到数据时 |
| `onSend` | Channel 可以发送数据时 |
| `onAwait` | Deferred 完成时 |
| `onJoin` | Job 完成时 |
| `onTimeout` | 超时后 |

**Select 的特点**：
- **竞争模式**：哪个先完成就执行哪个的分支
- **只执行一个分支**：类似 switch-case，但基于挂起操作的完成顺序
- **可组合**：可以混合不同类型的挂起操作
- **支持 default**：如果没有就绪的子句，执行 default 分支

```kotlin
// 实际应用：实现超时控制
suspend fun fetchDataWithTimeout(): String = select {
    apiCall.onAwait { it }
    onTimeout(5000) { "request timeout" }
}

// 实际应用：实现竞速模式（多个数据源取最快的）
suspend fun fetchFastest(): String = select {
    servers.map { server ->
        server.fetch().onAwait { it }
    }
}
```

---

## 十、协程的取消机制

### 10.1 取消的基本原理

- 调用 `Job.cancel()` 设置取消标志，协程在下一个**挂起点**检查 `isActive`，抛出 `CancellationException`
- 取消是**协作式**的：协程可以捕获 `CancellationException` 但必须重新抛出

### 10.2 cancel() vs cancelAndJoin()

| 维度 | cancel() | cancelAndJoin() |
|------|----------|----------------|
| 是否挂起 | 否（普通函数，立即返回） | 是（suspend 函数） |
| 是否等待协程完全停止 | 否，仅发送取消信号 | 是，等协程真正结束 |
| 内部实现 | 设置取消标志 | cancel() + join() |
| 适用场景 | 不关心协程何时结束 | 需确保协程完全取消后再继续（如资源清理、测试） |

```kotlin
// 如果你只是“发个信号让它停”，后续不依赖它的完成状态
job.cancel()

// 如果你在 finally 块或需要确保资源清理完成的场景中
job.cancelAndJoin()
```

### 10.3 取消与 Job 层级关系

**重要陷阱**：在父协程中使用 `launch(Job()) {}` 这种方式创建子协程，会**打破原有的协程结构**。在这种情况下，当调用 `parentJob.cancel()` 时，就无法取消“子协程”了。

```kotlin
val parentJob = Job()
val scope = CoroutineScope(parentJob)

// ❌ 错误：手动指定 Job() 会断开父子关系
scope.launch(Job()) {
    // 这个协程不会被 parentJob.cancel() 取消
}

// ✅ 正确：继承父 scope 的 Job
scope.launch {
    // 这个协程会被 parentJob.cancel() 取消
}
```

---

## 十一、高频面试题速答

### Q1：协程和线程的区别？

- 协程是**编译器层面的状态机变换**，线程是 OS 调度单元
- 协程挂起不阻塞线程，线程阻塞占用 OS 资源
- 协程是**协作式**的（suspend 点让出），线程是**抢占式**的
- 协程更轻量，可以轻松创建百万个

### Q2：协程是如何实现"挂起"的？

通过 CPS 变换 + 状态机：
1. suspend 函数编译后增加 Continuation 参数
2. 挂起时保存当前状态（label + 局部变量）到状态机对象
3. 返回 COROUTINE_SUSPENDED 标记
4. 异步操作完成后通过 Continuation.resumeWith() 恢复执行

### Q3：withContext 和 launch 的区别？

- `withContext`：不创建新协程，复用当前 Job，是 suspend 函数
- `launch`：创建新的子协程，有独立 Job，不是 suspend 函数

### Q4：协程的取消是如何工作的？

- `cancel()` 设置 Job 状态为 Cancelling
- 下一个挂起点检查 `ensureActive()`，抛出 `CancellationException`
- 非挂起代码需要通过 `isActive` 手动检查
- 取消是协作式的，协程可以捕获 CancellationException 但必须重新抛出

### Q5：Dispatchers.IO 和 Dispatchers.Default 的区别？

- IO：弹性线程池，最大 64 线程，适合 IO 密集型
- Default：固定线程池，线程数 = CPU 核心数，适合计算密集型
- 两者共享底层线程池，但各自有独立的任务队列

### Q6：Flow 和 LiveData 的区别？

- Flow 是 Kotlin 原生，LiveData 是 Android 特有
- Flow 支持背压，LiveData 不支持
- Flow 有丰富的操作符，LiveData 操作有限
- Flow 可以脱离 Android 使用，LiveData 依赖 Android 生命周期
- Flow 是冷流（默认），LiveData 是热流

### Q7：什么是结构化并发？

- 协程通过父子关系组织成树形结构
- 子协程异常自动传播到父协程
- 父协程取消时自动取消所有子协程
- 父协程等待所有子协程完成后才完成
- 保证协程不会"泄漏"（不会被遗忘在后台）

### Q8：Flow 的 zip 和 combine 的区别？

- **zip**：两个 Flow 各发射一个元素配对，必须两边都有新值才触发，结果数量取决于较慢的 Flow
- **combine**：任一 Flow 发射新值时，用两个 Flow 的最新值组合触发，结果数量取决于较快的 Flow

### Q9：StateFlow 和 SharedFlow 的区别？

- StateFlow 必须有初始值，SharedFlow 不需要
- StateFlow 内置 `distinctUntilChanged`（连续相同值不重复通知），SharedFlow 没有
- StateFlow 新收集者立即收到最新值（粘性），SharedFlow 取决于 `replay` 配置
- StateFlow 采用 conflate 策略（跳过中间值），SharedFlow 可配置溢出策略
- 适用场景：UI 状态 → StateFlow；一次性事件（Toast、导航） → SharedFlow(replay=0)

### Q10：协程取消时 cancel() 和 cancelAndJoin() 的区别？

- `cancel()`：普通函数，立即返回，仅发送取消信号，不等待协程真正结束
- `cancelAndJoin()`：suspend 函数，等待协程真正结束后才返回，内部实现是 `cancel() + join()`
- 需要确保资源清理完成时用 `cancelAndJoin()`，仅发信号用 `cancel()`

### Q11：协程切线程的本质是什么？

- 切线程 = 换 Dispatcher：`withContext(Dispatchers.IO)` 本质是将后续 Continuation 的恢复逻辑交给 `Dispatchers.IO` 的线程池执行
- 所有 Dispatcher 底层都是将任务投递到某个 Executor（线程池或 Handler）
- 挂起是前提：只有挂起函数返回 `COROUTINE_SUSPENDED` 后，当前线程才被释放
- 恢复时自动切回原 Dispatcher，因为保存了原始 Continuation 的 context

### Q12：COROUTINE_SUSPENDED 的作用？

- 它是一个信号标记，表示协程已挂起
- 返回它 → 让出线程，等回调再恢复
- 不返回它 → 同步完成，直接返回结果
- 同步场景下保持零开销，只有真正需要异步时才挂起
