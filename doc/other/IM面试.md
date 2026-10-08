# IM 面试题精讲（Android）

本文档面向 **Android 开发** 面试，覆盖 IM 业务语义与客户端实现：消息生命周期、状态机、撤回/离线/多端、未读、群聊、推送、长连接、本地存储、列表性能与工程架构。

> 答题原则：先讲清业务语义，再落到 Android 实现（Room、长连接、Push、RecyclerView）；能画出客户端链路、能说清失败与多端同步即可。

---

## 目录

1. [消息业务](#一消息业务)
2. [消息状态](#二消息状态)
3. [撤回消息](#三撤回消息)
4. [离线消息](#四离线消息)
5. [多端登录](#五多端登录)
6. [聊天列表](#六聊天列表)
7. [未读数](#七未读数)
8. [删除聊天](#八删除聊天)
9. [群聊](#九群聊)
10. [消息类型](#十消息类型)
11. [通知](#十一通知)
12. [异常场景](#十二异常场景)
13. [产品设计开放题](#十三产品设计开放题)
14. [撤回：Android 客户端实现](#十四撤回android-客户端实现)
15. [图片 / 视频消息](#十五图片--视频消息)
16. [推送](#十六推送)
17. [安全（客户端侧）](#十七安全客户端侧)
18. [消息列表性能](#十八消息列表性能)
19. [长连接、心跳与重连](#十九长连接心跳与重连)
20. [可靠投递与幂等（客户端）](#二十可靠投递与幂等客户端)
21. [消息顺序与乱序](#二十一消息顺序与乱序)
22. [增量同步](#二十二增量同步)
23. [本地存储与数据模型](#二十三本地存储与数据模型)（含群组/单聊/会话/消息关系）
24. [Android 客户端工程架构](#二十四android-客户端工程架构)
25. [弱网、后台限制与耗电](#二十五弱网后台限制与耗电)
26. [进阶业务能力](#二十六进阶业务能力)
27. [面试答题框架与踩坑](#二十七面试答题框架与踩坑)

---

## 一、消息业务 ★★★★★

### 1. 一条消息的完整生命周期是什么？

```text
编辑消息
    ↓
本地生成 clientMsgId
    ↓
插入本地数据库（Sending）
    ↓
经长连接发送服务器
    ↓
收到 Server ACK（带回 serverMsgId / seq）
    ↓
更新状态为 Sent
    ↓
对方客户端收到并落库 → Delivered
    ↓
对方打开会话阅读 → Read ACK
    ↓
发送方看到已读
```

客户端侧至少有两套 ID：**clientMsgId**（本地生成，重发去重）与 **serverMsgId / seq**（服务端返回，用于排序与同步）。

---

### 2. 为什么消息发送前要先写本地数据库？

**用户体验（乐观更新）。** 点击发送后消息应立即出现，不能等网络返回。

正确做法：

1. 本地先插入（`Sending`）
2. ACK 后更新为 `Sent`，失败则为 `Fail`
3. UI 只订阅本地 DB / Flow，不直接绑网络回调

本地库是 UI 的 **Single Source of Truth**，网络只是写库的输入。

---

### 3. 消息发送失败怎么办？

- 状态置为 `Fail`
- 用户点击重发（沿用同一 `clientMsgId`）

**为什么不用自动无限重试？** 避免重复发送、耗流量、打爆连接。通常：自动重试有限次（如 3 次，指数退避）→ 仍失败则人工重发。重发必须幂等，发送队列宜串行/限流。

---

## 二、消息状态 ★★★★★

| 类别 | 状态 | 含义 |
|------|------|------|
| 发送 | `Sending` | 本地已写入，正在发往服务器 |
| 发送 | `Sent` | 服务器已 ACK |
| 发送 | `Fail` | 发送失败，可重发 |
| 收到 | `Delivered` | 对方客户端已收到 |
| 已读 | `Read` | 对方已打开会话阅读 |
| 撤回 | `Recalled` | 消息已撤回 |
| 删除 | `Deleted` | 客户端侧删除 |

### 4. Sent / Delivered / Read 区别？

| 状态 | 含义 |
|------|------|
| **Sent** | 服务器收到（本端收到 ACK） |
| **Delivered** | 对方客户端收到 |
| **Read** | 对方真正打开会话阅读 |

群聊里 Delivered/Read 成本高，产品常简化为「Sent + 未读数」。

---

### 5. 已读回执如何实现？

```text
打开聊天页 / 消息滑入可见区域
    ↓
根据可见消息推进已读水位（conversationId + lastReadSeq）
    ↓
本地先更新 unread / 对方已读态
    ↓
节流后上报 Server → 通知对端
    ↓
发送方更新 UI（单聊可到消息级）
```

**为什么很多群没有已读？** 产品选择：大群每人回 ACK 成本太高。

#### 只滑到部分未读怎么处理？（高频追问）

先分清两个「列表」：

| 列表 | 作用 |
|------|------|
| **会话列表** | 展示每个会话的未读角标；一般不负责算「看到哪一条」 |
| **聊天页消息列表** | RecyclerView 根据**可见消息**推进已读水位 |

假设有 10 条未读（按时间从旧到新为 U1…U10，U10 最新在底部）。用户只滑到 **倒数第 3 条未读 U8** 进入可视区，U9、U10 仍在下方未看见。

**正确做法：按可见消息推进水位，而不是一进会话就清零。**

```text
可见区域里，对方消息的最大 seq = U8.seq
    ↓
若 U8.seq > 本地 readSeq → readSeq = U8.seq（只增不减）
    ↓
未读数 = seq > readSeq 的对方消息数 → 还剩 2（U9、U10）
    ↓
会话列表角标改为 2；对发送方：U1…U8 可标已读，U9/U10 仍未读
```

Android 实现要点：

1. **监听可见范围**：`RecyclerView.OnScrollListener` + `LinearLayoutManager.findFirst/LastVisibleItemPosition()`（也可用 `OnChildAttachStateChangeListener`）
2. **算候选水位**：可见 item 中，筛选「对方发来的消息」，取最大 `seq`（或 `serverMsgId` 序）
3. **单调推进**：`newReadSeq = max(oldReadSeq, visibleMaxSeq)`，禁止回退
4. **节流上报**：滚动过程 debounce（如 300～500ms）或停滑后再报，避免每帧打 Server
5. **本地先改**：先写 Room 的 `read_seq` / `unread_count`，会话列表 Flow 自动刷新角标
6. **自己的消息不算**：可见的自己发送气泡不推进「我方已读水位」

```kotlin
// 示意：停滑或 debounce 后调用
fun onVisibleRangeChanged(first: Int, last: Int) {
    val visibleMaxSeq = messages
        .subList(first, last + 1)
        .filter { it.isFromPeer && it.seq != null }
        .maxOfOrNull { it.seq } ?: return
    if (visibleMaxSeq <= conversation.readSeq) return
    conversationRepository.advanceReadSeq(conversationId, visibleMaxSeq)
    // 内部：unread = count(peerMsg.seq > readSeq)；再节流上报 Read ACK
}
```

**和「一进聊天未读清零」的区别（产品策略）：**

| 策略 | 行为 | 常见于 |
|------|------|--------|
| 进会话清角标 | 打开聊天页，会话列表角标直接变 0 | 很多 App 的会话未读（简化） |
| 可见才已读 | 角标/对端已读都跟滑动走，未看完仍保留未读 | 严格已读回执、部分阅读器 |
| 混合 | 进会话清**本端会话角标**，但对端已读仍按可见水位报 | 体验与回执折中 |

面试建议答：**未读角标可以产品上进会话就清；真正的已读回执应按可见消息的 max seq 做水位。** 只滑到倒数第 3 条时，水位停在该条，下面 2 条仍未读。

补充边界：

- 打开会话若定位到「第一条未读」，初始可见范围可能已覆盖 U1～若干条，水位一次可跳到当前可见最大 seq  
- 快速惯性滑动：以停稳后的可见范围为准，或对经过的 item 也累计 max seq（看产品是否要求「滑过即已读」）  
- 多端：本端推进的 `readSeq` 同步出去，其他端未读一并收敛  

---

## 三、撤回消息 ★★★★★

### 6. 撤回如何实现？

**不是**删数据库。真正做法：发撤回请求 → Server 下发 `Recall Command` → 客户端更新消息类型/状态 → UI 显示「你撤回了一条消息」。详见 [第十四节](#十四撤回android-客户端实现)。

### 7. 为什么微信只能两分钟撤回？

产品策略，不是技术限制。

---

## 四、离线消息 ★★★★★

### 8. 用户离线时消息怎么办？

Server 保存；客户端上线后按 `lastSeq` **增量同步**（消息 + 命令）。

### 9. 为什么不能每次全量同步？

浪费流量、CPU、本地 DB。应增量同步；进入会话再按需分页拉更早历史。

上线同步 ≠ 拉历史：前者补离线期间事件，后者是用户上滑加载。

---

## 五、多端登录 ★★★★★

### 10. 手机、Pad、PC 同时在线怎么办？

消息、状态、删除、撤回、已读、置顶/免打扰都要同步。本端发出的消息也要出现在自己其他端（多端抄送）。客户端：收 Command → 写本地库 → UI 刷新。

### 11. 为什么微信手机退出 PC 也退出？

产品安全策略。

---

## 六、聊天列表 ★★★★★

### 12. 最近聊天为什么排最前？

更新 `lastMessage` / `lastTime` / 未读后重排。

### 13. 为什么置顶还能保持？

```text
isTop DESC, lastTime DESC
```

会话表应冗余摘要字段，**不要**每次打开列表都扫全量消息表。

---

## 七、未读数 ★★★★★

### 14. 未读数如何计算？

- 收到消息：本地 `Unread++`（免打扰可能只红点）
- 进入聊天：产品可选择「角标直接清零」，或按可见消息推进水位（见 §5 部分已读）
- 与 Server 对账，保证多端一致

| 方案 | 说明 |
|------|------|
| 服务器维护 | 权威，重启/换机不丢 |
| 客户端维护 | 本地快显，需对账 |
| 增量同步 | 用 seq / 已读水位差量 |

更稳妥的模型：`unread ≈ maxSeq - readSeq`（再扣免打扰、自己发的消息等规则），而不是单纯计数器。

### 15. 为什么退出重进未读还在？

Server 维护，本地只是缓存。

### 为什么未读更新快？

本地先改 UI → Server 对账 → 只同步变更会话 → 总角标聚合并定期校准。

---

## 八、删除聊天 ★★★☆☆

### 16. 删除聊天意味着什么？

面试先澄清语义，常见有两层（可组合）：

| 操作 | 含义 | 本地常见做法 |
|------|------|----------------|
| **删除会话入口** | 首页列表不再显示该行 | `conversation.is_hidden = true`（软删） |
| **清空聊天记录** | 本地消息删掉或不可见 | delete / 另标 `cleared_before_seq` |

多数产品「删除聊天」= **隐藏会话入口**，不一定清空服务端漫游；群关系（是否在群里）通常 **不变**——删会话 ≠ 退群。

### 17. 删除后服务器删了吗？

一般没有。Server 仍保留消息与会话能力；客户端只是本地隐藏。多端可同步「该端隐藏」，策略因产品而异。

---

### 删除会话后，又来了新消息 / Push，怎么设计？（高频）

结论先说：

> **删除是本地（或本端）隐藏，不是拒收。** 单聊对方再发、群里再有人说话，Push 仍会来；客户端应 **复活会话入口**，并按产品决定是否带上旧历史。

#### 1. 推荐数据模型（软删 + 复活）

不要物理删掉 `conversation` 行（或删了也要能按 `type + target_id` 再建）。

```text
conversation:
  is_hidden = true          // 列表 WHERE is_hidden=0
  hidden_at / clear_seq     // 可选：隐藏时刻对应的 seq
  unread_count = 0          // 删除时通常清角标
```

列表查询：`WHERE is_hidden = 0 ORDER BY is_top, last_msg_time`。

#### 2. 新消息到达时的交互链路

```text
Push / 长连接下行：单聊或群消息
    ↓
按 type + target_id 找到已有 conversation（即便 is_hidden=true）
    ↓
insert message（去重）
    ↓
【复活】is_hidden = false
    ↓
更新 last_msg_* 、max_seq
    ↓
若非当前聊天页：unread_count++（群 @我 仍可 has_at_me）
    ↓
会话列表重新出现该行；通知栏正常展示（受免打扰规则约束）
```

对用户的体感：**删掉的聊天，对方一说话又出现在列表里**——和微信等产品一致。

#### 3. 单聊 vs 群聊

| | 单聊 | 群聊 |
|--|------|------|
| 删会话后关系 | 仍是可聊对象 | **仍在群内**（未退群） |
| 新消息来源 | 对方再发 | 群内任意成员发 |
| Push | 照常（未拉黑/未免打扰） | 照常；免打扰群可能只红点或静默 |
| 客户端 | 复活 C2C 会话 | 复活 GROUP 会话；群资料表仍在 |
| 若已退群 | — | 不应再收群消息；若误推则忽略或提示已不在群 |

注意：用户若期望「再也不要这个群」，应走 **退群**，而不是删会话。面试要主动区分这两条产品路径。

#### 4. 历史消息怎么处理？（产品分支）

删除时若曾清空本地消息，复活后有两种常见策略：

| 策略 | 行为 | 体验 |
|------|------|------|
| **A. 仅展示删除后的新消息** | 记 `clear_before_seq`；聊天页只加载 `seq > clear_before_seq` | 像「新开的对话」，旧记录要手动搜/漫游才有 |
| **B. 复活后仍可上滑拉旧历史** | 本地空则按页向 Server 漫游 | 更像「只是从列表拿掉」 |
| **C. 删除时不清消息，只 hidden** | 复活后本地历史还在 | 实现简单，占空间 |

Android 实现建议：会话上留 `clear_before_seq`（或 `last_clear_time`），分页查询带上该下界，避免和「真删库」绑死。

#### 5. Push 点击怎么走？

```text
用户点击通知
    ↓
解析 conversationId / targetId
    ↓
若会话 is_hidden → 先复活（或进页时复活）
    ↓
打开 ChatActivity(conversationId)
    ↓
定位到最新消息；按已读策略清未读
```

不要因为 `is_hidden` 就丢弃 Push 或无法进页。

#### 6. 多端

- 手机删会话，Pad 未必删（各端 `is_hidden` 可独立，或 Server 同步隐藏指令）  
- 任一端收到新消息并复活后，其他在线端也应看到会话回来（同步 conversation 变更或靠消息增量间接 upsert）  

#### 7. 和「免打扰 / 拉黑」的区别

| 能力 | 列表 | 收消息 | 新消息是否现身 |
|------|------|--------|----------------|
| 删除会话 | 隐藏 | **仍收** | **复活并显示** |
| 免打扰 | 仍在 | 仍收 | 在，但不响/可不计总角标 |
| 拉黑 / 退群 | 视产品 | **基本不收** | 不应再出现新业务消息 |

#### 8. 客户端伪代码示意

```kotlin
fun onInboundMessage(msg: Message) {
    val conv = conversationDao.findByTarget(msg.type, msg.targetId)
        ?: conversationDao.insertNew(...)   // 从未聊过则新建

    messageDao.insertIgnore(msg)

    conv.isHidden = false                   // 复活
    conv.lastMsgPreview = msg.preview
    conv.lastMsgTime = msg.serverTime
    conv.maxSeq = max(conv.maxSeq, msg.seq)
    if (!isCurrentChat(conv.id) && msg.senderId != myId) {
        conv.unreadCount += 1
        if (msg.mentionsMe) conv.hasAtMe = true
    }
    conversationDao.update(conv)
}
```

删除时：

```kotlin
fun deleteConversation(id: String, clearHistory: Boolean) {
    val c = conversationDao.get(id)
    c.isHidden = true
    c.unreadCount = 0
    c.hasAtMe = false
    if (clearHistory) {
        c.clearBeforeSeq = c.maxSeq
        messageDao.deleteByConversation(id) // 或软删
    }
    conversationDao.update(c)
    notificationManager.cancel(id)
}
```

面试一句话：

> 删会话 = 列表软隐藏；链路不停。新消息/Push 到来时 upsert 消息并将 `is_hidden=false` 复活会话；是否露出删除前的历史由 `clear_before_seq` 决定。删会话 ≠ 退群/拉黑。

---
## 九、群聊 ★★★★★

### 18. 退群怎么办？

不再收新消息；历史一般仍可看。

### 19. 群消息为什么没有已读？

大群 ACK 太多，产品常放弃。

### 20. 群成员变化如何通知？

收 `GroupEvent` → 更新本地群资料与 UI。

### 群消息客户端怎么发？

**不要**对每个成员 `for` 发一次。客户端只发 **一条** 给 Server，由服务端扩散给成员。Android 侧关心：发一条、收一条、按会话落库。

### @群成员如何实现？（高频）

`@` **不是**再给被 @ 的人单独发一条消息，而是普通群消息上带 **mention 元数据**；被 @ 的人用这份元数据做强提醒、会话角标、Push。

#### 1. 输入侧（EditText）

```text
用户输入 @
    ↓
弹出群成员选择（本地群成员表 / 搜索）
    ↓
选中成员后，插入一段「可点的 @昵称」占位
    ↓
本地维护 mentions[] = [{ userId, start, end, name }]
    ↓
发送时：content 文本 + mentions 结构一起发出
```

Android 常见做法：

- 用 `Spannable` / 自定义 `MentionSpan` 标记 `@张三` 区间（颜色、不可拆半删除）
- 按退格：若光标在 mention 内，整段删掉，并同步更新 `mentions[]` 下标
- `@所有人`：特殊 id，如 `userId = "all"` / `mentionType = ALL`（通常仅群主/管理员）

发送体示意：

```json
{
  "type": "TEXT",
  "content": "你好 @张三 看一下",
  "mentions": [
    { "userId": "u_1001", "name": "张三", "start": 3, "end": 6 }
  ]
}
```

注意：`start/end` 是给渲染用的；**业务判断靠 `userId` 列表**，不要只靠解析文本里的 `@昵称`（昵称会改、会重名）。

#### 2. 接收侧

```text
收到群消息
    ↓
落库：content + mentions(JSON)
    ↓
判断 mentions 是否包含 me 或 ALL
    ↓
是 → 会话标记 hasAtMe / atMsgSeq；未读强提醒；可破免打扰
    ↓
气泡用 Span 高亮 @xxx；会话列表摘要前加「[有人@我]」
```

#### 3. 会话列表与未读

| 能力 | 处理 |
|------|------|
| 摘要 | `preview = "[有人@我] " + 文本摘要`（或独立红点文案） |
| 角标 | 普通未读仍 `Unread++`；另存 `at_me_count` 或 `has_at_me` |
| 免打扰 | 普通消息可能不进总角标 / 不响；**@我 / @所有人通常仍提醒** |
| 进入会话 | 可见已读后清未读；同时清 `has_at_me`（或进会话即清） |
| 点击「有人@我」 | 可跳到第一条未读 @ 消息（按 `atMsgSeq` 定位） |

#### 4. Push

Push payload 带 `mentionMe=true`（或服务端按 mention 列表定向加强推送）。客户端展示：「张三在群里@了你」。后台即便开了免打扰，@ 我仍可弹（产品规则）。

#### 5. 数据模型补充

**message** 增加：`mentions`（JSON 数组）

**conversation** 增加：`has_at_me`、`at_msg_seq`（可选 `at_me_count`）

#### 6. 边界

- 昵称变更：展示以当前群名片为准，或发送时快照 `name`；匹配永远用 `userId`
- 撤回：撤回后清气泡，并重算会话是否还有未读 @
- 退群后再看历史：仍可显示 @ 文本，但不再产生新提醒
- 只 @ 不在群里的人：发送前校验，或服务端过滤

面试一句话：**@ = 群消息的结构化附件（userId 列表）+ 客户端对「是否包含我」的强提醒策略，不是点对点私聊。**

---

## 十、消息类型 ★★★★☆

### 21. 为什么要有 Type？

`TEXT` / `IMAGE` / `VOICE` / `VIDEO` / `FILE` / `SYSTEM` / `RECALL` …  
Adapter 按 Type 渲染；未知 Type 降级为「不支持的消息」。

建议 **消息与信令分离**：聊天气泡走 Message，撤回/已读/群事件走 Command。

### 22. 为什么图片消息不是图片本身？

发的是 **URL + 元数据**。见 [第十五节](#十五图片--视频消息)。

---

## 十一、通知 ★★★★☆

### 23. 为什么前台不弹通知？

已在 App 内；后台才走 Push。

### 24. 为什么当前聊天页不提醒？

维护 `currentConversationId`，相等则只刷新 UI、不弹通知。

---

## 十二、异常场景 ★★★★★

### 25. 发送中 App 崩了？

本地仍是 `Sending` → 重启扫描 → 续传或标 `Fail`。

### 26. 同时撤回？

听 Server 的 Command，客户端只执行。

### 27. 发送成功但 ACK 丢了？

超时后同 `clientMsgId` 重发；服务端去重，客户端按返回更新为 Sent。

### 28. 用户改了手机时间？

列表排序用 **seq / serverTime**，不依赖手机时钟。

| 场景 | 客户端处理 |
|------|------------|
| 重复收到同一条 | `msgId` 幂等插入 |
| 先收到撤回后收到原消息 | 先记悬挂命令，原消息到达后合并为撤回态 |
| 多端已读时本端会话正打开 | 本地未读立即清零 |
| Token 失效 | 刷新后重连；未完成发送留在队列 |

---

## 十三、产品设计开放题 ★★★★★

### 设计消息状态？

`Sending` / `Sent` / `Delivered` / `Read` / `Failed` / `Recalled` / `Deleted`

### 设计撤回？

时限、多端同步、离线补同步、通知栏是否更新、命令而非硬删。

### 设计未读数？

单聊 / 群聊 / @我 / 总角标 / 多端 / 免打扰 / 对账。

### 设计聊天列表？

摘要、草稿、置顶、免打扰、未读、@我、类型文案（[图片]）、发送失败标记。

---

## 十四、撤回：Android 客户端实现 ★★★★★

**错误答案：** 收到撤回就 `dao.delete()`。  
**正确答案：** 更新类型/状态，保留列表坑位。

### 流程

1. 发 `RecallRequest(msgId)`  
2. 收到 Command：`{ type: RECALL, msgId, operator }`  
3. `messageDao.queryByMsgId` → 更新为 `RECALL`  
4. Room Flow → ViewModel → RecyclerView Diff 刷新  

### 为什么不删？

硬删会让聊天记录「少一条」；应显示「你撤回了一条消息」。

### 文案不要写死在 content

`type = RECALL`，UI 按「我/对方/多语言」决定文案。

### Adapter

```kotlin
when (message.type) {
    MessageType.TEXT -> { /* 文本气泡 */ }
    MessageType.IMAGE -> { /* 图片 */ }
    MessageType.RECALL -> { /* item_recall：你撤回了一条消息 */ }
}
```

### 离线 / 多端 / 本地无此消息

先 TEXT 后 RECALL 最终收敛；多端各自写库；本地无 `msgId` 则忽略，勿 Crash。

---

## 十五、图片 / 视频消息 ★★★★☆

```text
选图/视频 → 压缩 → 上传 OSS → 拿 URL
    → 发消息体（URL + 宽高/时长/封面）
```

客户端要点：

- 先插本地消息（本地 path + `Sending`），上传成功再补 URL 并 send  
- 上传失败可单独重试，与发消息解耦  
- 列表只解码缩略图；视频先封面，点击再播，注意播放器复用与生命周期  
- 鉴权 URL 过期要刷新，避免历史图裂  

---

## 十六、推送 ★★★★☆

后台 Socket 常被系统杀掉，靠 Push 触达：

| 场景 | 方案 |
|------|------|
| 海外 | FCM |
| 国内 | 厂商 Push（华为 / 小米 / OPPO / vivo 等） |

点击通知 → 拉起 App → 重连 → `lastSeq` 增量同步。

要点：

- Push 只做唤醒 + 摘要，**可靠数据靠同步**  
- 通知去重、点击进会话、撤回后更新/取消通知  
- 厂商 Token 上报；Android 13+ 通知权限  

---

## 十七、安全（客户端侧） ★★★★☆

Android 侧常考：

| 点 | 做法 |
|------|------|
| 传输 | HTTPS / WSS（TLS） |
| 连接鉴权 | Token；失效刷新后重连 |
| 证书 | 校验证书，防中间人抓包 |
| 本地 | 敏感日志脱敏；必要时 SQLCipher |
| 抓包 | TLS + 校验证书后，明文抓不到；业务层还可能再加密 |

E2EE 若被问到：知道「端上加解密、服务端不可读、多设备密钥更复杂」即可，不必展开服务端密钥体系。

---

## 十八、消息列表性能 ★★★★☆

### 消息很多怎么办？

分页、Room、上滑懒加载、DiffUtil；**禁止**动辄 `notifyDataSetChanged()`。

### 几十万条如何保证滚动流畅？

1. 窗口化加载（一次 20～50），双向分页  
2. 索引 `(conversation_id, seq)`  
3. RecyclerView 稳定 ID、异步 Diff、主线程禁大查询  
4. 图片缩略图，滑出可视区暂停解码  
5. 会话表与消息表分离  
6. 可用 Paging3（本地页 + 上滑再请求更早历史）  

性能杀手常是 **图片/表情/气泡测高** 和 **全量提交列表**，不只是 SQL。

---

## 十九、长连接、心跳与重连 ★★★★★

### 为什么用长连接？

短轮询延迟高、费电。前台用 WebSocket/TCP 长连接做实时收发。

### 心跳

检测半开连接、辅助保活。间隔常见几十秒；**前台可密，后台应疏或停**，改走 Push。

### 重连

```text
断开 → 短延迟重连 → 失败则指数退避（加 jitter）
     → 成功后：鉴权 → pull(lastSeq) → 恢复发送队列
```

- WiFi ↔ 蜂窝切换主动重连  
- Token 失效 / 被踢：不要死循环重连  

### Android 现实

**不要依赖保活黑科技撑后台长连接。**

- 前台：长连接  
- 后台：Push 唤醒；回前台立刻 `connect + sync`  
- 通话等场景才考虑正当的前台服务  

---

## 二十、可靠投递与幂等（客户端） ★★★★★

客户端目标：发出去能确认、收到不重复、失败可恢复。

### 发送

```text
写本地(Sending) → 发送 → 等 ACK
超时 → 同 clientMsgId 重发
收到 ACK → 更新 Sent + serverMsgId/seq
```

### 接收

```text
收到下行 → 按 msgId 去重 → 落库成功 → 回 ACK（若协议需要）
```

### 推 + 拉

在线靠推送求快；重连/定时/发现缺段则 **拉取补齐**。一句话：**推负责快，拉负责全。**

去重键：发送用 `clientMsgId`，接收用 `serverMsgId`；不要用本地时间戳当唯一键。

---

## 二十一、消息顺序与乱序 ★★★★★

乱序原因：推拉并发、重传、多端同时发。

客户端策略：

1. UI 按会话 **seq** 排序，不用手机时间  
2. 本地 `Sending` 消息用临时排序键，ACK 后换成 server seq 再 Diff  
3. 乱序先入库，缺 seq 则触发补拉  

---

## 二十二、增量同步 ★★★★★

同步的是 **事件流**：新消息、撤回、已读、群资料变更、置顶/免打扰等。

```text
持有 lastSyncSeq
重连/上线/定时 → pull(after = lastSyncSeq)
写本地 DB → 推进 lastSyncSeq
```

| 能力 | 目的 |
|------|------|
| 增量同步 | 追平离线期间事件 |
| 历史分页 | 用户上滑更早聊天记录 |

---

## 二十三、本地存储与数据模型 ★★★★★

### 群组、单聊、会话、消息的关系（高频）

先分清四个概念，很多人会把「群」和「会话」混为一谈。

| 概念 | 是什么 | 类比 |
|------|--------|------|
| **群组 Group** | 一群人的组织关系（成员、角色、群名、公告） | 通讯录里的「群」 |
| **单聊 Peer / C2C** | 两个 user 之间的私聊关系 | 通讯录里的「好友私聊」 |
| **会话 Conversation** | 首页聊天列表里的一行：最近消息、未读、置顶、草稿 | 微信首页列表项 |
| **消息 Message** | 会话里的一条内容（文本/图/撤回…） | 点进会话后的气泡 |

关系一句话：

> **群组 / 单聊是「和谁聊」的业务实体；会话是「聊到哪了」的入口摘要；消息挂在会话下。**  
> 首页只扫会话表；点进会话再按 `conversation_id` 分页查消息。群资料另表存，不塞进每条消息。

```text
                    ┌─────────────┐
                    │    User     │
                    └──────┬──────┘
           1:1 私聊         │          N:N 群成员
              ┌─────────────┼─────────────┐
              ▼             │             ▼
       ┌────────────┐       │      ┌────────────┐
       │ Peer/C2C   │       │      │   Group    │
       │ (对端uid)  │       │      │ 群资料/成员 │
       └──────┬─────┘       │      └──────┬─────┘
              │             │             │
              │    都映射为「会话」         │
              └─────────────┼─────────────┘
                            ▼
                   ┌─────────────────┐
                   │  Conversation   │  ← 会话列表 UI 只读这张表
                   │  type=C2C/GROUP │
                   │  未读/摘要/置顶  │
                   └────────┬────────┘
                            │ 1 : N
                            ▼
                   ┌─────────────────┐
                   │    Message      │  ← 聊天页按 conversation_id 分页
                   │  seq / status   │
                   └─────────────────┘
```

#### 单聊 vs 群聊在会话上的差异

| | 单聊会话 | 群聊会话 |
|--|----------|----------|
| `type` | `C2C` / `PRIVATE` | `GROUP` |
| 对端标识 | `peer_user_id` | `group_id` |
| 展示名/头像 | 对方资料 | 群名/群头像（来自 Group 表） |
| 成员 | 固定 2 人 | GroupMember 表，可变 |
| @ | 一般无 | 有 `mentions` / `has_at_me` |
| 会话 id 怎么定 | 常由两端 uid 规则生成，或 Server 下发 | 通常等于 `group_id`，或 Server 下发 |

客户端习惯：**会话表统一抽象**，用 `type + target_id`（对端 uid 或 group_id）区分，列表 UI 一套代码。

#### 谁先谁后？

1. 首次发消息 / 被加群 / 收到第一条消息 → 确保有 Group（若群聊）→ **upsert Conversation** → insert Message  
2. 之后每条消息：insert Message，并 **同一事务** 更新 Conversation 的摘要、时间、未读  
3. 删会话：多半只标 `is_hidden`（软删），Message 可清可留；**群关系不一定变**。之后若有新消息/Push，应复活会话（见 §8）

---

### 表结构示意（Android Room）

#### 1. conversation（会话 —— 列表页 SSOT）

| 字段 | 说明 |
|------|------|
| `conversation_id` | 主键 |
| `type` | `C2C` / `GROUP` |
| `target_id` | 单聊=对方 userId；群聊=groupId |
| `title` / `avatar` | 可冗余，便于列表免 join |
| `last_msg_id` / `last_msg_preview` / `last_msg_time` | 列表摘要 |
| `unread_count` | 未读数 |
| `read_seq` / `max_seq` | 已读水位 / 最大序号 |
| `is_top` / `is_mute` / `draft` | 置顶、免打扰、草稿 |
| `has_at_me` / `at_msg_seq` | 群 @ 强提醒 |
| `is_hidden` | 删除会话入口（软删） |
| `update_time` | 排序辅助 |

索引：`(is_top DESC, last_msg_time DESC)`；`target_id + type` 唯一（避免同一对象多条会话）。

#### 2. message（消息 —— 聊天页）

| 字段 | 说明 |
|------|------|
| `client_msg_id` | 客户端生成，重发去重，建议唯一 |
| `server_msg_id` | 服务端 id |
| `conversation_id` | **外键，归属哪个会话** |
| `sender_id` | 发送者 |
| `type` | TEXT / IMAGE / RECALL / … |
| `content` | 文本或 JSON |
| `mentions` | 群 @ 列表 JSON |
| `status` | Sending / Sent / Fail / … |
| `seq` | 会话内序，列表排序用 |
| `server_time` / `local_time` | 时间 |
| `recalled` | 是否撤回 |

索引：`(conversation_id, seq)`；`client_msg_id` 唯一。

#### 3. group（群组资料 —— 不是会话）

| 字段 | 说明 |
|------|------|
| `group_id` | 主键 |
| `name` / `avatar` / `owner_id` / `announcement` | 群资料 |
| `member_count` | 冗余人数 |
| `my_role` | 我在群里的角色（成员/管理/群主） |
| `update_time` | 资料版本 |

会话的 `target_id` 指向这里的 `group_id`。改群名时：更新 Group，并可选同步刷新 Conversation.title。

#### 4. group_member（群成员）

| 字段 | 说明 |
|------|------|
| `group_id` + `user_id` | 联合主键 |
| `nickname_in_group` | 群名片 |
| `role` | 角色 |
| `join_time` | 入群时间 |

用于 @ 选人、成员列表、权限判断。大群可再分页/按需拉取，不必一次全量常驻。

#### 5. user / contact（可选）

单聊展示名、头像；也可只缓存在内存 + 会话冗余字段。

#### 6. attachment（可选）

媒体本地 path、远端 url、上传状态、宽高时长；`message_id` 关联。

---

### 实体关系（ER）简图

```text
User 1──N Message(sender)
User 1──N GroupMember N──1 Group
Group 1──0..1 Conversation(type=GROUP, target_id=group_id)
User(peer) 1──0..1 Conversation(type=C2C, target_id=peer_id)
Conversation 1──N Message
Message 1──0..N Attachment
```

注意：**Group 与 Conversation 是 1:1（对该用户而言）**，不是把群成员当会话。成员变动改 `group_member`，发消息只增加 `message` 并更新那一个 `conversation`。

---

### 写入时怎么联动（客户端）

```text
收到/发出一条群消息
    ↓
确保 group 存在（没有则拉群资料）
    ↓
upsert conversation(type=GROUP, target_id=groupId)
    ↓
insert message(conversation_id=...)
    ↓
update conversation:
      last_msg_* , max_seq,
      unread++（若非自己且非当前会话）,
      has_at_me（若 mentions 含我）
```

单聊同理，只是没有 Group 表，`target_id = peerUserId`。

---

### 要点

1. UI 只读 DB；网络回调只写 DB  
2. **列表读 Conversation，详情读 Message**——职责分离  
3. `(conversation_id, seq)` 索引；`client_msg_id` 唯一  
4. 摘要、未读冗余在会话表，禁止列表扫消息表聚合  
5. 插消息 + 更新会话放同一事务  
6. Room Migration；切账号清库或分库  
7. 群资料（Group）与会话摘要（Conversation）分离，避免消息行里重复存群名  

面试一句话：

> 单聊/群组是关系，会话是入口，消息是内容；Android 用一张 Conversation 统一列表，用 `type + target_id` 挂到 Peer 或 Group，Message 只认 `conversation_id`。

---

## 二十四、Android 客户端工程架构 ★★★★★

```text
Chat / Conversation UI     RecyclerView + Diff / Paging
        ↑
    ViewModel              发送、已读、分页、当前会话
        ↑
    Repository             本地优先，合并 Connection / API / Push
        ↑
 Local(Room)  Connection  Push  OSS
```

| 组件 | 职责 |
|------|------|
| ConnectionManager | 连接状态机、心跳、重连、鉴权 |
| InboundDispatcher | 下行解包 → 去重 → 写库 |
| OutboundQueue | 发送队列、超时重试、失败打标 |
| SyncEngine | lastSeq 增量、补洞、未读对账 |
| NotificationGate | 前后台 / 当前会话抑制通知 |

- 写库与 Diff 在后台；主线程只提交列表  
- 同会话发送队列有序  
- 进程死后：启动时恢复连接、扫描 `Sending`、补同步  

---

## 二十五、弱网、后台限制与耗电 ★★★★★

### 弱网

超时退避、媒体与消息分离重试、大文件分片、界面展示网络状态。

### 后台

Doze / 厂商杀进程限制网络；**后台以 Push 为准**，回前台 `connect + sync`，可展示「未连接」。

### 耗电

后台停长连接或降频心跳；避免无意义重连与高频写库；图片解码按可见性开关。

---

## 二十六、进阶业务能力 ★★★★☆

| 能力 | 客户端要点 |
|------|------------|
| 正在输入 | 独立信令、节流，不落历史库 |
| 引用回复 | `refMsgId`；原消息撤回后的降级 UI |
| 消息编辑 | Command 更新 + `edited` 标记 |
| @我 | 消息带 `mentions[{userId}]`；含我则 `has_at_me` + 强提醒，可破免打扰；见 §9 |
| 免打扰 | 未读可不计入总角标 |
| 草稿 | 存在会话表，列表展示 |
| 语音 | 录制→上传→URL；已播状态本地记 |
| 搜索 | 本地 FTS；注意权限与性能 |
| 清通知栏 | 进入会话 / 多端已读后 cancel |

---

## 二十七、面试答题框架与踩坑 ★★★★★

### 开放题怎么答

1. 澄清：单聊/群聊、是否多端、是否漫游  
2. 画客户端主链路：本地落库 → 发送 → ACK → 同步 → UI  
3. 说清状态机：Sending → Sent → Delivered → Read / Fail / Recall  
4. 讲失败：重试、幂等、补洞、对账  
5. 落到 Android：Room SSOT、前后台与 Push、列表 Diff 性能  

### 常见踩坑

| 踩坑 | 正确认知 |
|------|----------|
| 撤回 = 删库 | 更新类型/状态 |
| 未读只靠本地 ++ | 水位 + 与 Server 对账 |
| 群发 for 每个人 | 客户端只发一条 |
| 图片走 IM 通道 | OSS + URL |
| 无限重试 | 有限次 + 幂等 + 人工 |
| 用手机时间排序 | seq / serverTime |
| 后台保活撑长连接 | Push + 回前台同步 |
| `notifyDataSetChanged` | DiffUtil / 局部刷新 |
| 只推不拉 | 半死连接会丢，需拉取补齐 |

### 一句话

> IM 客户端本质是 **以本地数据库为中心的状态机**：长连接和 Push 搬运事件，seq 对齐，幂等去重，UI 只读本地。

---

## 速记卡片

| 主题 | 一句话 |
|------|--------|
| 发送 | 先写本地再发网 |
| ID | clientMsgId 去重，seq 排序 |
| 状态 | Sent ≠ Delivered ≠ Read |
| 撤回 | 改类型，不硬删 |
| 离线 | 上线按 lastSeq 增量同步 |
| 删除聊天 | 软隐藏；新消息到来则复活会话 |
| 群发 | 客户端只发一条 |
| @成员 | 消息带 mentions(userId)，不是私聊 |
| 未读 | 本地快显 + 水位对账 |
| 部分已读 | 按可见 max seq 推进，未看见的仍算未读 |
| 图片 | OSS URL，不是二进制 |
| 后台 | 靠 Push，回前台重连同步 |
| 可靠 | 超时重发 + 幂等 |
| 列表 | 分页 + Diff |
| 架构 | Room 为 SSOT |
| 模型 | 群/单聊是关系，会话是入口，消息挂会话下 |
| 时间 | 用 seq，别信手机时钟 |

---

*可与 [Android面试.md](../Android/Android面试.md) 搭配复习。*
