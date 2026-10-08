# Realm 数据库面试题精讲（IM 业务向）

本文档面向 **Android + IM** 面试，聚焦 Realm 在会话列表、消息落库、未读、多线程读写、迁移与性能上的考点。可与 [IM面试.md](../other/IM面试.md) 搭配复习。

> 答题原则：先说清「为什么 IM 适合用 Realm」，再落到线程模型、LiveResults、事务与分页；能对比 Room，能讲清踩坑。

---

## 目录

1. [Realm 是什么、为何用在 IM](#一realm-是什么为何用在-im)
2. [核心概念](#二核心概念)
3. [IM 数据模型怎么建](#三im-数据模型怎么建)
4. [线程模型（必考）](#四线程模型必考)
5. [增删改查与事务](#五增删改查与事务)
6. [LiveResults / 自动刷新](#六liveresults--自动刷新)
7. [会话列表与消息列表查询](#七会话列表与消息列表查询)
8. [未读、已读水位、软删会话](#八未读已读水位软删会话)
9. [写入性能与批量落库](#九写入性能与批量落库)
10. [分页与大数据量](#十分页与大数据量)
11. [迁移 Schema](#十一迁移-schema)
12. [加密、多用户、文件管理](#十二加密多用户文件管理)
13. [Realm vs Room / SQLite](#十三realm-vs-room--sqlite)
14. [常见崩溃与踩坑](#十四常见崩溃与踩坑)
15. [结合 IM 链路的完整读写](#十五结合-im-链路的完整读写)
16. [开放题与速记](#十六开放题与速记)

---

## 一、Realm 是什么、为何用在 IM ★★★★★

### 1. Realm 是什么？

Realm 是移动端嵌入式对象数据库：直接以 **对象图** 持久化，不是「先写对象再 ORM 映射到行」。查询返回的是 **活对象（live objects）**，库变更后结果可自动更新。

IM 里典型用途：

- 会话表、消息表、群资料、草稿
- 作为 UI 的 **Single Source of Truth**（与 IM 面试里 Local-First 一致）
- 长连接/Push 回调写库 → 列表自动刷新

### 2. 为什么 IM 常用 Realm？（对比「只用内存」）

| 诉求 | Realm 怎么满足 |
|------|----------------|
| 发送先上屏 | 先 `insert` Sending，UI 订阅查询立刻看到 |
| 崩溃恢复 | Sending 消息仍在库里，重启可续传 |
| 十万级消息 | 磁盘持久化 + 按会话查询，不必全量进内存 |
| 列表实时变 | LiveResults / Flow 包装，少手写 `notify` |
| 多端同步后收敛 | 下行写库，UI 自动跟 |

### 3. 面试怎么定位？

「Realm 负责 IM 客户端本地状态；长连接和 Push 只负责把事件写进 Realm；界面只读 Realm 查询结果。」

---

## 二、核心概念 ★★★★☆

| 概念 | 含义 |
|------|------|
| **Realm** | 一次打开的数据库实例（绑定线程） |
| **RealmObject / RealmModel** | 可持久化模型（如 Message、Conversation） |
| **RealmConfiguration** | 路径、schemaVersion、迁移、加密 key |
| **Transaction** | 所有写操作必须在 `executeTransaction`（或写事务）里 |
| **RealmResults** | 查询结果集，默认是 **live** 的 |
| **Unmanaged / Managed** | 未托管对象可跨线程传；托管对象绑定创建它的 Realm/线程 |
| **copyFromRealm / copyToRealm** | 托管 ↔ 普通 Java/Kotlin 对象拷贝 |
| **freeze()** | 生成某一时刻的不可变快照，便于跨线程读 |

---

## 三、IM 数据模型怎么建 ★★★★★

与 [IM面试.md](../other/IM面试.md) 中「群组/单聊/会话/消息」一致，Realm 用对象表达：

### Conversation（会话）

```kotlin
open class Conversation : RealmObject() {
    @PrimaryKey var conversationId: String = ""
    var type: String = "C2C"          // C2C / GROUP
    var targetId: String = ""         // peerUid or groupId
    var title: String = ""
    var avatar: String = ""
    var lastMsgPreview: String = ""
    var lastMsgTime: Long = 0
    var unreadCount: Int = 0
    var readSeq: Long = 0
    var maxSeq: Long = 0
    var isTop: Boolean = false
    var isMute: Boolean = false
    var isHidden: Boolean = false     // 删除会话软隐藏
    var clearBeforeSeq: Long = 0      // 清空历史下界
    var hasAtMe: Boolean = false
    var atMsgSeq: Long = 0
    var draft: String = ""
}
```

### Message（消息）

```kotlin
open class Message : RealmObject() {
    @PrimaryKey var clientMsgId: String = ""
    var serverMsgId: String = ""
    var conversationId: String = ""
    @Index var conversationIdSeq: String = "" // 可选：conversationId + seq 拼索引键
    var senderId: String = ""
    var type: String = "TEXT"
    var content: String = ""
    var mentionsJson: String = ""     // @ 列表 JSON
    var status: String = "Sending"    // Sending/Sent/Fail/...
    var seq: Long = 0
    var serverTime: Long = 0
    var recalled: Boolean = false
}
```

### Group / GroupMember

群资料、成员与会话分离；`Conversation.targetId` 指向 `groupId`。

### 设计要点

1. **主键**：会话用 `conversationId`；消息用 `clientMsgId`（重发幂等）  
2. **索引**：`conversationId`、`seq`、`lastMsgTime`、`isHidden`；高频组合可冗余索引字段  
3. **不要**在 Message 里嵌整份 Conversation（易冗余、难更新）；用 id 关联  
4. `@`、扩展字段可用 JSON 字符串，避免频繁改 schema  

---

## 四、线程模型（必考） ★★★★★

这是 Realm 面试区分度最高的题。

### 规则

1. **Realm 实例不能跨线程使用**  
2. **Managed 对象 / RealmResults 不能跨线程传递**  
3. 每个线程各自 `Realm.getInstance(config)`（或用 `Realm.getDefaultInstance()` 按线程缓存）  
4. 用完在该线程 `close()`（尤其后台线程，防泄漏）

### IM 里典型线程划分

| 线程 | 做什么 |
|------|--------|
| Main | 打开 Realm，订阅会话/消息查询，驱动 UI |
| 网络/连接线程 | **不能**直接拿主线程的 Message 对象写；应传 `clientMsgId` 或 unmanaged 拷贝 |
| 写库线程（单写者更稳） | 下行消息批量 `executeTransaction` |

### 跨线程怎么传数据？

| 方式 | 场景 |
|------|------|
| 只传主键 id | 最安全：对端线程再 `where().equalTo("id", id).findFirst()` |
| `copyFromRealm(obj)` | 得到普通对象，可任意线程用（不再 live） |
| `freeze()` | 冻结快照，可跨线程读（只读） |
| Handler / Channel 抛回主线程写 | 网络线程只解析协议，主线程或专用写线程落库 |

### 错误示范（常考）

```kotlin
// 主线程查询
val msg = realm.where(Message::class.java).equalTo("clientMsgId", id).findFirst()
thread {
    // 崩溃：Realm access from incorrect thread
    msg?.status = "Sent"
    realm.insertOrUpdate(msg)
}
```

正确：

```kotlin
thread {
    val r = Realm.getInstance(config)
    r.executeTransaction {
        it.where(Message::class.java)
            .equalTo("clientMsgId", id)
            .findFirst()
            ?.status = "Sent"
    }
    r.close()
}
```

---

### 大数据量表：子线程插入/查询，主线程监听刷新（高频实战）

核心机制：

> **同一 Realm 文件**上，任意线程提交写事务后，**主线程上已注册的 LiveResults / ChangeListener 会自动回调**。  
> 子线程与主线程各持有自己的 `Realm` 实例，**不传托管对象**，只共享 `RealmConfiguration`（同一路径）。

```text
子线程                          主线程
──────                          ──────
Realm.getInstance(config)       Realm.getInstance(config)
executeTransaction { 批量写 }    where().findAll() → LiveResults
close()                         addChangeListener { 刷新 UI }
        \                         ↑
         \____ 文件变更通知 ______/
```

#### 1. 主线程：只订阅，不做大事务

```kotlin
class ChatRepository(private val config: RealmConfiguration) {
    private var mainRealm: Realm? = null
    private var liveMessages: RealmResults<Message>? = null

    fun observeMessages(conversationId: String, onChange: (List<Message>) -> Unit) {
        val realm = Realm.getInstance(config).also { mainRealm = it }
        val results = realm.where(Message::class.java)
            .equalTo("conversationId", conversationId)
            .sort("seq", Sort.ASCENDING)
            .findAll()
        liveMessages = results

        val listener = RealmChangeListener<RealmResults<Message>> { list ->
            // 回调已在主线程；不要把 list 传到子线程
            // 大数据：先 freeze / copyFromRealm 再 Diff，避免边遍历边变
            val snapshot = list.freeze() // 或 copyFromRealm(list)
            onChange(snapshot)
        }
        results.addChangeListener(listener)
    }

    fun dispose() {
        liveMessages?.removeAllChangeListeners()
        mainRealm?.close()
    }
}
```

UI（Activity / ViewModel）只收 snapshot，用 DiffUtil 提交，避免 `notifyDataSetChanged()`。

#### 2. 子线程：大批量插入（自己的 Realm + 单事务）

```kotlin
fun insertMessagesOnBg(config: RealmConfiguration, batch: List<MessageDto>) {
    executor.execute {
        val realm = Realm.getInstance(config)
        try {
            // 关键：一次事务写完，不要 for 里反复 executeTransaction
            realm.executeTransaction { r ->
                batch.forEach { dto ->
                    val exists = r.where(Message::class.java)
                        .equalTo("clientMsgId", dto.clientMsgId)
                        .findFirst()
                    if (exists != null) return@forEach
                    r.insert(Message().apply {
                        clientMsgId = dto.clientMsgId
                        conversationId = dto.conversationId
                        content = dto.content
                        seq = dto.seq
                        // ...
                    })
                }
            }
            // 事务一提交 → 主线程 LiveResults 自动 onChange
        } finally {
            realm.close() // 子线程必须 close，否则泄漏 / 文件锁
        }
    }
}
```

数据量特别大（如一次同步上万条）时再拆成多段事务（每段 500～2000），段与段之间可 `Thread.sleep` 极短或让出 CPU，避免长时间写锁堵读。

#### 3. 子线程：大查询（结果怎么回主线程？）

**不要**把子线程查到的 `RealmResults` / 托管对象直接抛给主线程。

| 做法 | 说明 |
|------|------|
| **A. 主线程 Live 查询（推荐展示）** | 主线程 `findAll` + Listener；子线程只负责写。列表展示走这条 |
| **B. 子线程查完 `copyFromRealm` / `freeze`** | 得到可跨线程快照，`Handler(Looper.getMainLooper()).post { ui(list) }` |
| **C. 只回传 id 列表** | 主线程再按 id 查（二次查询，适合轻量） |

```kotlin
fun queryPageOnBg(
    config: RealmConfiguration,
    conversationId: String,
    beforeSeq: Long,
    limit: Long,
    onMain: (List<Message>) -> Unit
) {
    executor.execute {
        val realm = Realm.getInstance(config)
        try {
            val results = realm.where(Message::class.java)
                .equalTo("conversationId", conversationId)
                .lessThan("seq", beforeSeq)
                .sort("seq", Sort.DESCENDING)
                .limit(limit)
                .findAll()

            // 跨线程：必须变成非托管 / 冻结快照
            val snapshot = realm.copyFromRealm(results) // List<Message> unmanaged

            Handler(Looper.getMainLooper()).post {
                onMain(snapshot.reversed()) // 按业务再排序
            }
        } finally {
            realm.close()
        }
    }
}
```

上滑加载历史常用 **B**：子线程按 `seq` 分页查 → `copyFromRealm` → 主线程拼进 Adapter。  
首页会话列表常用 **A**：主线程 LiveResults，子线程批量写完自动刷新。

#### 4. 为什么主线程能「感知」子线程的写？

Realm 在写事务提交后会通知其他线程上打开的同一 Realm 文件：主线程 Looper 上的 Results 标记为待更新，下一帧/`Handler` 触发 `RealmChangeListener`。  
因此 **不必** 子线程写完再手动 `runOnUiThread { adapter.notify... }` 去「通知有插入」（除非你走的是 copy 快照方案 B，没有 Live 订阅）。

#### 5. 大数据量注意点

| 点 | 建议 |
|----|------|
| 主线程 | 禁止大事务、禁止 `for` 全表拷贝到内存 |
| 子线程写 | 批量单事务；建好 `conversationId` / `seq` 索引 |
| 子线程读 | 带条件 + `limit`，不要 `findAll` 无过滤 |
| Listener | 回调里 `freeze()`/`copyFromRealm` 再 Diff，防 ConcurrentModification |
| 生命周期 | 主线程 Listener 成对移除；子线程 `finally close` |
| 写冲突 | IM 下行建议 **单写者线程**，避免多线程狂写同一文件 |

#### 6. 错误 vs 正确对照

```kotlin
// ❌ 子线程拿主线程的 realm / results
thread { mainRealm.executeTransaction { ... } }

// ❌ 子线程 results 直接给主线程
thread {
    val list = realm.where(...).findAll()
    runOnUiThread { adapter.submit(list) } // incorrect thread
}

// ✅ 子线程自建 Realm 写；主线程 LiveResults 听变化
// ✅ 或子线程 copyFromRealm 后把普通 List 帖回主线程
```

面试一句话：

> 子线程 `getInstance` + 事务写/分页查并 `close`；主线程对同一 config 开 Realm，用 LiveResults 听变化。跨线程只传 id 或 `copyFromRealm`/`freeze` 快照，绝不传托管对象。

---
## 五、增删改查与事务 ★★★★★

### 写必须在事务里

```kotlin
realm.executeTransaction { r ->
    val m = r.createObject(Message::class.java, clientMsgId)
    m.conversationId = cid
    m.content = text
    m.status = "Sending"
    // 同一事务更新会话摘要、未读
    val c = r.where(Conversation::class.java)
        .equalTo("conversationId", cid).findFirst()
        ?: r.createObject(Conversation::class.java, cid)
    c.lastMsgPreview = text
    c.lastMsgTime = System.currentTimeMillis()
}
```

IM 强调：**插消息 + 更新会话** 同一事务，避免列表摘要与详情不一致。

### 幂等插入（下行去重）

```kotlin
realm.executeTransaction { r ->
    val exists = r.where(Message::class.java)
        .equalTo("clientMsgId", msg.clientMsgId)
        .findFirst()
    if (exists != null) return@executeTransaction
    r.copyToRealm(msg) // msg 为 unmanaged
}
```

或用 `@PrimaryKey` + `insertOrUpdate`（注意覆盖字段语义）。

### 查询

```kotlin
val list = realm.where(Conversation::class.java)
    .equalTo("isHidden", false)
    .sort("isTop", Sort.DESCENDING, "lastMsgTime", Sort.DESCENDING)
    .findAll()
```

---

## 六、LiveResults / 自动刷新 ★★★★★

### 是什么？

`findAll()` 得到的 `RealmResults` 默认是 **live** 的：事务提交后，同一线程上的结果集内容会变，可注册监听刷新 UI。

```kotlin
val results = realm.where(Conversation::class.java)
    .equalTo("isHidden", false)
    .sort("lastMsgTime", Sort.DESCENDING)
    .findAll()

results.addChangeListener { list ->
    adapter.submit(list) // 注意 Diff；或转 snapshot
}
```

### 和 IM 架构的契合

```text
Push/Socket → 写线程 Transaction
                ↓
         Realm 文件变更
                ↓
         主线程 LiveResults 回调
                ↓
         会话列表 / 未读角标刷新
```

不必在网络回调里手动 `adapter.notifyDataSetChanged()` 满天飞。

### 注意

- Listener 必须在 **持有该 Results 的同一线程** 触发（通常主线程订阅）  
- Activity/Fragment 销毁要 `removeChangeListener`，并 `close` Realm  
- 回调里不要做重活；大列表先 `freeze()` 或拷贝再 Diff  
- 若用 Flow 封装：在 `awaitClose` 里移除监听并 close  

---

## 七、会话列表与消息列表查询 ★★★★☆

### 会话列表

```kotlin
realm.where(Conversation::class.java)
    .equalTo("isHidden", false)
    .sort("isTop", Sort.DESCENDING, "lastMsgTime", Sort.DESCENDING)
    .findAll()
```

摘要字段冗余在 Conversation，**禁止**每次用消息表聚合 `MAX(time)`。

### 聊天页消息（按会话 + seq）

```kotlin
realm.where(Message::class.java)
    .equalTo("conversationId", cid)
    .greaterThan("seq", clearBeforeSeq)   // 删除清空后的下界
    .sort("seq", Sort.ASCENDING)
    .findAll()
```

只滑到部分未读、已读水位等业务见 [IM面试.md](../other/IM面试.md) §5；Realm 侧就是更新 `Conversation.readSeq` / `unreadCount`。

### 按主键改状态（ACK）

```kotlin
realm.executeTransaction { r ->
    r.where(Message::class.java)
        .equalTo("clientMsgId", id)
        .findFirst()
        ?.apply {
            status = "Sent"
            serverMsgId = ack.serverMsgId
            seq = ack.seq
        }
}
```

---

## 八、未读、已读水位、软删会话 ★★★★★

### 未读

```kotlin
// 下行且非当前会话
c.unreadCount += 1
if (mentionMe) {
    c.hasAtMe = true
    c.atMsgSeq = msg.seq
}
```

总角标：对 `isHidden=false && !isMute`（按产品）的 `unreadCount` 求和；可用独立查询或内存聚合。

### 已读水位（可见即已读）

主线程根据 RecyclerView 可见范围算 `visibleMaxSeq`，写线程/主线程事务：

```kotlin
if (visibleMaxSeq > c.readSeq) {
    c.readSeq = visibleMaxSeq
    c.unreadCount = /* 统计 seq > readSeq 的对方消息，或按差量减少 */
    c.hasAtMe = false // 或按是否还有未读 @ 重算
}
```

### 删除会话后再来消息（复活）

```kotlin
realm.executeTransaction { r ->
    val c = findOrCreateConversation(...)
    c.isHidden = false          // 复活
    c.lastMsgPreview = preview
    c.lastMsgTime = time
    c.maxSeq = max(c.maxSeq, seq)
    if (!isCurrent) c.unreadCount += 1
    insertMessageIfAbsent(...)
}
```

与 [IM面试.md](../other/IM面试.md) §8 一致：删会话是 `isHidden`，不是拒收。

---

## 九、写入性能与批量落库 ★★★★☆

### 离线同步一次来几百条？

**务必批量单事务**，不要条条开事务：

```kotlin
realm.executeTransaction { r ->
    syncMessages.forEach { dto ->
        // insertOrUpdate message + 更新对应 conversation
    }
}
```

### 优化点

- 减少事务次数（批量）  
- 避免在事务里做网络/图片解码  
- 大字段（长 JSON）别在列表查询里无必要带出——列表用 Conversation 摘要  
- 写线程串行化，降低写冲突  
- 主线程少做大事务（会卡 UI）  

### insert vs copyToRealm vs insertOrUpdate

| API | 说明 |
|-----|------|
| `createObject` | 事务内创建托管对象 |
| `copyToRealm` | unmanaged → managed，主键冲突抛异常 |
| `copyToRealmOrUpdate` / `insertOrUpdate` | 按主键更新，适合 ACK 回写、同步覆盖 |

---

## 十、分页与大数据量 ★★★★☆

Realm 的 `RealmResults` 是懒加载视图，但 **UI 不能一次绑十几万行**。

IM 做法：

1. 聊天页窗口化：按 `seq` 区间查一页（如最近 50 条）  
2. 上滑再查更小 `seq` 段，合并到内存列表 / Adapter  
3. 或对 Results 做 `subList` 式窗口（注意 live 变化时的下标）  
4. DiffUtil 提交；禁止整表 `notifyDataSetChanged`  

```kotlin
// 示意：拉取更早一页
realm.where(Message::class.java)
    .equalTo("conversationId", cid)
    .lessThan("seq", firstVisibleSeq)
    .greaterThan("seq", clearBeforeSeq)
    .sort("seq", Sort.DESCENDING)
    .limit(50)
    .findAll()
```

（具体 API 因 Realm 版本而异；老版本可能用迭代取前 N 再反转。）

---

## 十一、迁移 Schema ★★★★☆

```kotlin
RealmConfiguration.Builder()
    .schemaVersion(3)
    .migration { realm, oldVersion, newVersion ->
        var v = oldVersion
        if (v == 1L) {
            // 例：Conversation 增加 isHidden
            realm.schema.get("Conversation")
                ?.addField("isHidden", Boolean::class.java)
                ?.transform { it.setBoolean("isHidden", false) }
            v++
        }
        if (v == 2L) {
            realm.schema.get("Message")
                ?.addField("mentionsJson", String::class.java)
            v++
        }
    }
    .build()
```

IM 注意：

- 版本只增不减；发版前写好 Migration  
- 字段重命名用 `renameField`，避免用户清数据  
- 开发期可用 `deleteRealmIfMigrationNeeded()`，**生产禁止**  
- 主键、索引变更要测升级路径  

---

## 十二、加密、多用户、文件管理 ★★★★☆

### 多账号

一用户一文件，避免串数据：

```text
realm_user_{uid}.realm
```

切换账号：关闭旧 Realm → 换 Configuration → 打开新文件。不要在同一文件用 `userId` 字段「逻辑隔离」却忘了删库（易漏数据）。

### 加密

`encryptionKey`（64 bytes）写入 Configuration；密钥存 Keystore，勿硬编码。

### 登出

`Realm.deleteRealm(config)` 或删文件；并取消通知、清空内存缓存。

---

## 十三、Realm vs Room / SQLite ★★★★★

| 维度 | Realm | Room |
|------|-------|------|
| 模型 | 对象数据库，活对象 | SQLite + ORM |
| 跨线程 | 实例/对象不可跨线程，约束严 | DAO 通常更直观，协程友好 |
| 观察 | LiveResults / 自带变更 | Flow / LiveData |
| 迁移 | 手写 Dynamic schema | Migration + SQL |
| SQL | 无（或有限） | 强，复杂查询灵活 |
| IM 契合 | 对象+自动刷新很香 | 官方主力，生态更大 |
| 现状 | 经典版维护策略需关注；有 Kotlin 新方案演进 | Android 主流默认选项 |

面试怎么答「为什么选 Realm」：

- 历史项目 / 团队栈  
- 需要对象图 + 自动刷新，少胶水代码  
- 已有封装成熟  

怎么答「为什么选 Room」：

- 官方维护、协程/Paging3 集成好  
- 复杂 SQL、FTS 搜索  
- 线程模型心智负担更小  

两边都能做 IM；**架构关键是 Local SSOT，不是品牌。**

---

## 十四、常见崩溃与踩坑 ★★★★★

| 现象 | 原因 | 处理 |
|------|------|------|
| `incorrect thread` | 跨线程用 Realm/托管对象 | 传 id；对端重查；或 copy/freeze |
| 改对象不生效 | 事务外修改 | 放入 `executeTransaction` |
| 泄漏 / 文件锁 | 后台线程未 `close` | `try/finally close` |
| UI 卡顿 | 主线程大事务 / 遍历超大 Results | 批量放写线程；分页 |
| Listener 内存泄漏 | 未移除监听 | `onDestroy` 成对移除 |
| 主键冲突崩溃 | `copyToRealm` 重复 | 先查再写或 `OrUpdate` |
| 列表闪烁 | live 回调直接全量刷新 | DiffUtil；或 freeze 快照 |
| 切账号串消息 | 共用一个 realm 文件 | 按 uid 分文件 |

---

## 十五、结合 IM 链路的完整读写 ★★★★★

### 发送

```text
UI 点发送
  → 主线程/写线程 Transaction：insert Message(Sending) + 更新 Conversation
  → LiveResults 刷新，气泡立刻出现
  → 出站队列发网络（只带 clientMsgId）
  → ACK 回：Transaction 更新 status=Sent、seq、serverMsgId
```

### 接收

```text
Socket/Push 解析
  → 写线程 Transaction：幂等 insert Message
  → upsert Conversation（含 isHidden=false 复活）
  → unread / hasAtMe
  → 主线程 Listener → 列表与角标
```

### 撤回

```text
收到 RECALL Command
  → 按 msgId 查 Message，改 type/recalled（不物理删）
  → 若影响 lastMsgPreview，同步改 Conversation 摘要
```

### 进程恢复

启动扫描 `status == Sending` 的消息，续传或标 Fail——依赖 Realm 持久化。

---

## 十六、开放题与速记 ★★★★☆

### 开放题怎么答

1. Realm 作为 IM 本地 SSOT  
2. Conversation / Message 模型与索引  
3. 线程：谁写谁读、如何跨线程  
4. 事务：消息+会话原子更新；同步批量写  
5. LiveResults 驱动 UI；分页防卡顿  
6. 软删 `isHidden`、多账号分文件、Migration  

### 速记卡片

| 主题 | 一句话 |
|------|--------|
| 定位 | IM 本地状态机的磁盘实现 |
| 线程 | 实例与托管对象不跨线程 |
| 写入 | 必须事务；消息+会话同事务 |
| UI | LiveResults 自动刷新 |
| 去重 | clientMsgId 主键幂等 |
| 删除会话 | isHidden；新消息复活 |
| 同步 | 批量单事务落库 |
| 大数据 | 按会话+seq 分页，不绑全表 |
| 多账号 | 一用户一 realm 文件 |
| 对比 Room | 都能做；选栈与生态，SSOT 思想相同 |

### 一句话总结

> 在 IM 里用 Realm，本质是把「会话 + 消息」做成可观察的本地对象库：网络只写库，UI 只订阅查询；把线程边界和事务边界划清，就胜过背 API。

---

*可与 [IM面试.md](../other/IM面试.md)、[Android面试.md](./Android面试.md) 搭配复习。*
