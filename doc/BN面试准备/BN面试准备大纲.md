# BN面试准备

## 一、Android 原生

### 1. Handler 消息机制

- Handler / Looper / MessageQueue 三者的关系？
- 消息优先级机制是什么？如何发送一条最高优先级的消息？
- 什么是同步屏障与异步消息？其使用场景有哪些？
- 请谈谈 Choreographer 的工作原理？VSYNC 信号是如何驱动渲染的？
- MessageQueue 的阻塞 / 唤醒原理是什么？是否只要往 MessageQueue 中插入消息就会触发 `nativeWake`？
- `nativeWake` 为何能唤醒线程？`epoll` 还有哪些应用场景？
- 除 `epoll` 外，还有哪些操作可以阻塞线程？
- MessageQueue 的阻塞唤醒与 `BlockingQueue` 的阻塞唤醒有何区别？
- `ReentrantLock` 的实现原理？如何让其他线程阻塞？是公平锁吗？如何保证公平性？
- Handler 内存泄漏的原因是什么？如何处理？（匿名内部类引用链、弱引用、`static` 修饰）
- `Handler().post` 能否正确获取 View 的宽高？为什么？
- `onResume` 中能否直接获取 View 的宽高？为什么？

### 2. View 与渲染

- `invalidate()` 与 `requestLayout()` 的区别？连续调用多次分别会有什么后果？
- `invalidate()` 的实现原理？
- `requestLayout()` 的实现原理？
- `measuredWidth` 与 `width` 的区别？
- `LayoutParams.width`、`measuredWidth`、`getWidth()` 三者分别代表什么？
- 打开一个 Activity，第一帧数据绘制的触发点在哪里？
- Android 屏幕刷新机制（VSYNC）是什么？
- `setContentView()` 的实现原理？

### 3. 内存泄漏

- LeakCanary 的原理？
- `SoftReference` 与 `WeakReference` 的区别？`ReferenceQueue` 的作用？能否覆盖所有对象？
- 如何设计一个内存泄漏监控系统？
- 内存泄漏如何定位？
- Handler 内存泄漏的完整引用链是什么？

### 4. 性能优化

- 冷启动优化的方案有哪些？如何做冷启动监控？
- 复杂页面（如交易页）的构建方案？
- 卡顿检测与优化的方案有哪些？
- 如何统计每个页面的渲染时间？请描述实现思路。
- 启动页优化：XML 异步加载的实现方式？

### 5. 其他

- App 启动流程？冷启动与热启动的区别？
- Application 是什么时候创建的？
- Context 是什么时候产生的？
- Gradle 有几种依赖方式？各自的区别？
- 字节码插桩的原理与应用？
- ANR 的分析、定位及解决方案？
- 如何判断 so 库已经加载过？
- SharedPreferences 有哪些缺陷？
- LiveData 的实现原理？
- Remote ADB 的使用？

---

## 二、Flutter

### 1. 渲染机制

- Flutter Widget → Element → RenderObject 的渲染流程？

### 2. Dart 语言

- Dart Future 的实现原理？（Dart 事件队列）
- Dart 单线程模型的特点？
- Dart Isolate 与线程的区别？多个 Isolate 之间如何通信？
- StatefulWidget 的生命周期？
- Flutter 热更新的实现原理？

---

## 三、综合 & 其他

### 1. AI 辅助开发

- AI 开发工具的完整流程？（SuperPower 的流程）
- Skill 读取项目规范的方式？如何保证 UI 还原度？
- AI Code Review 的设计方案？
- Session 压缩与跨 Session 开发的实现？
- Token 消耗与上下文管理？
- Harness 与 Agent 的概念与应用？
- AI Chat 如何实现类似打字机的响应效果？与 WebSocket 的区别？
- Skill 与 MCP 的区别？

### 2. 业务相关

- 交易下单流程？
- OrderBook 合并逻辑？
- 链上充值？
- 仓位卡顿优化？

### 3. Git

- Git 中如何将多个 commit 合并为一个？
- `git merge` 与 `git rebase` 的区别？

### 4. Java / Kotlin 基础

- Java 中有哪些锁？各自的区别？
- `synchronized` 的锁升级过程？
- CAS 的原理？（底层汇编 `lock cmpxchg`）
- Kotlin / Java 中有哪些单例写法？
- 类的加载时机有哪些？`static` 单例为何能保证线程安全？
- `object` 关键字的原理？是否线程安全？
- `by lazy` 的原理？是否线程安全？
- Kotlin 扩展函数的实现原理？
- Kotlin 默认参数的实现原理？对应的 Java 字节码是什么样的？
- Kotlin 接口默认方法的实现原理？对应的 Java 字节码是什么样的？
- Kotlin `by` 委托机制的原理？
- 静态代理与动态代理的具体实现方式？
- 如何手写一个 Kotlin 单例？对应的 Java 字节码是什么样的？
- 如何写一个线程级别的单例？原理是什么？
- 内存模型：堆、栈、程序计数器、方法区、常量池分别是什么？
- 全局变量与函数变量分别分配在哪个内存区域？

### 5. 线程安全

- 实现线程安全的方式有哪些？
- 线程安全的 Map、List 有哪些？各自的实现方式？
- `volatile` 的作用？
- `ConcurrentHashMap` 的原理？`get` 方法如何实现多线程安全访问？
- 多线程资源竞争如何解决？

### 6. 网络

- 为何要做 HttpDNS？如何实现？
- 在 Interceptor 中把 baseURL 从域名直接替换成 IP 直连会有什么问题？
- IP 择优策略是什么？HttpDNS 服务挂了怎么办？
- `Dns.lookup()` 的调用时机？`ConnectionPool` 复用连接可以节省哪部分时间？
- OkHttp 会自动重试哪些请求？如何避免重复下单？
- HTTP/1.1 下 OkHttp 默认配置，同时有 100 个相同域名的请求，最终会有几条连接？HTTP/2 下又如何？
- WebView 为什么无法直接复用原生网络层？Native 代发如何实现？是否会绕过 CORS？什么是跨域？
- HTTPS 的交互过程？
- HTTP 协议如何实现缓存？有哪些缓存相关的 Header？（`Cache-Control`、`Expires`、`Last-Modified`）
- WebSocket 的稳定性如何保障？
- WebSocket 高频消息如何优化？如何防止 buffer 内存膨胀？
- 提升 WebSocket 订阅逻辑健壮性需要考虑哪些方面？
- 流量优化与最优链路选择？

### 7. 抓包

- Charles 抓包原理？
- AI 抓包工具的实现？

### 8. 架构设计

- MVP 与 MVVM 的区别？
- MVVM 与 MVI 的区别？
- Clean Architecture 的优缺点？适用场景？
- EventBus 的原理？如何实现 `postSticky` 粘性事件？

### 9. 序列化

- `Serializable` 与 `Parcelable` 的区别？`Parcelable` 如何实现本地序列化？

---

## 四、算法（低频 ⭐）

### 1. 两数之和

- LeetCode #1：https://leetcode-cn.com/problems/two-sum

### 2. 最长公共前缀

- LeetCode #14：https://leetcode-cn.com/problems/longest-common-prefix
- 编写一个函数来查找字符串数组中的最长公共前缀。如果不存在公共前缀，返回空字符串 ""。

### 3. 字符串倒序打印

- 用一个指针 i 循环遍历字符串，从末尾到开头逆序打印每个字符。

### 4. 路径总和 II

- LeetCode #113：https://leetcode-cn.com/problems/path-sum-ii
- 给你二叉树的根节点 root 和一个整数目标和 targetSum，找出所有从根节点到叶子节点路径总和等于给定目标和的路径。

### 5. 字母异位词分组

- LeetCode #49：https://leetcode-cn.com/problems/group-anagrams
- 给你一个字符串数组，请你将字母异位词组合在一起。可以按任意顺序返回结果列表。
