# 🧠 客悦电商售后智能客服 Agent

> 一个能自己**查订单、查物流、查政策、发起退款、发补偿券、判断是否转人工**的智能客服 Agent。
>
> 不是"问答机器人"，是能 **自主决策工具调用链路** 的 AI Agent。

<p align="center">
  <img alt="Java" src="https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?logo=springboot&logoColor=white">
  <img alt="Spring AI Alibaba" src="https://img.shields.io/badge/Spring%20AI%20Alibaba-1.1.0--RC2-FF6B35">
  <img alt="DashScope" src="https://img.shields.io/badge/DashScope-Qwen-FF6900">
  <img alt="Milvus" src="https://img.shields.io/badge/Milvus-2.6.10-00A1B0?logo=milvus&logoColor=white">
  <img alt="Elasticsearch" src="https://img.shields.io/badge/Elasticsearch-8.12-005571?logo=elasticsearch&logoColor=white">
  <img alt="RocketMQ" src="https://img.shields.io/badge/RocketMQ-5.3.1-3578F3">
  <img alt="Redis" src="https://img.shields.io/badge/Redis-7.x-DC382D?logo=redis&logoColor=white">
  <img alt="MySQL" src="https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white">
  <img alt="Sentinel" src="https://img.shields.io/badge/Sentinel-1.8.8-FF8C00">
  <img alt="SkyWalking" src="https://img.shields.io/badge/SkyWalking-9.7.0-0094D8">
  <img alt="MCP" src="https://img.shields.io/badge/MCP-2024--11--05-purple">
</p>

<p align="center">
  <a href="docs/技术改造计划.md">📘 技术改造计划</a> ·
  <a href="docs/PRD-电商售后智能客服Agent.md">📋 产品需求</a> ·
  <a href="#-快速开始">🚀 快速开始</a> ·
  <a href="#-api-示例">🧪 API 示例</a> ·
  <a href="docs/ARCHITECTURE.md">🏗 架构</a>
</p>

---

## 🎯 这是什么

用户说 *"我昨天买的耳机想退款"*，Agent 自己干完这些事：

```
👤 用户:  我昨天买的耳机想退款
                          ↓
🤖 Agent 思考:  用户要退款 → 需要先查到订单
   ⚡ 调用 OrderQueryTool.queryUserOrders(userId="U10001")
     → L1 Caffeine 缓存未命中 → L2 Redis 缓存未命中 → MySQL 查到订单 OD20260809001 蓝牙耳机 ¥299
     → 查到结果写入 L1 + L2 双层缓存

🤖 Agent: 您好，查到您 8月9日购买了蓝牙耳机（OD20260809001），金额 ¥299。
          请问退款原因是什么？

👤 用户:  音质不好
                          ↓
🤖 Agent 思考:  需要确认退款政策 → 是否支持无理由退款
   ⚡ 调用 PolicySearchTool.queryInternalDocs("耳机 音质 退款")
     → 向量检索（Milvus）返回 top3 政策片段 + BM25 关键词检索（ES）返回 top3
     → RRF 倒数排名融合两路结果
     → 命中政策: 耳机属于 7 天无理由可退商品，需商品完好

🤖 Agent: 根据七天无理由退换货政策，耳机可退货，退款将在商家确认后 3-5 个工作日原路返回。
          是否现在发起退款？

👤 用户:  好的
                          ↓
🤖 Agent 思考:  用户确认 → 金额 ¥299 < ¥5000 无需风控 → 执行退款
   ⚡ 调用 RefundTool.createRefund(orderId, reason="音质不好", amount=299)
     → MySQL 写入退款单 RF20260810001 (状态: PENDING)
     → RocketMQ 发送消息 topic="after-sales-event" event="REFUND_CREATED"
        → Consumer notification-srv: 模拟发送短信通知用户
        → Consumer persistence-srv: 持久化处理记录
        → Consumer seat-srv: 金额 < ¥5000，记录备查

🤖 Agent: 退款申请已提交（退款单号 RF20260810001）。
          您将通过短信收到退款进度通知。
```

> **全程零人工干预，Agent 自主完成 4 次工具调用 + 1 次 MQ 消息发送。**

---

## 🧭 两种工作模式

### 模式 1️⃣：对话模式（用户驱动）

用户发消息 → ReactAgent 接收 → **LLM 自主决策调用哪些工具** → 流式返回结果

```bash
POST /api/chat_stream
{"id":"session-001", "UserId":"U10001", "question":"我的快递到哪了？"}
```

ReactAgent 自主编排工具调用链：

| 用户问题 | Agent 自动编排的工具链路 |
|---|---|
| 查物流 | `OrderQueryTool` 查订单拿到 trackingNo → `LogisticsTool` 查物流轨迹 → 回答 |
| 退款 | `OrderQueryTool` 查订单 → `PolicySearchTool` 查退款政策 → `RefundTool` 发起退款 → `NotificationTool` 发 MQ 通知 |
| 投诉 | `ComplaintTool` 记录投诉 → 判断 CRITICAL → `NotificationTool` 发转人工 MQ |

> **11 个工具（9 个本地 @Tool + 2 个远程 MCP），LLM 自己决定用哪个、什么顺序、调几次。**

### 模式 2️⃣：智能分诊模式（Agent 主动巡检）

用户不输入任何内容，Agent **主动**扫描用户最近 7 天所有订单，发现异常并给出处理方案：

```bash
POST /api/ai_ops
{"UserId":"U10001"}
```

```
  SupervisorAgent（调度中心）
    │
    ├─ Step 1: 调用 TriageAgent（分诊 Agent）
    │   ⚡ queryUserOrders → 查到用户有 5 个订单
    │   ⚡ queryLogistics → 逐个查物流状态
    │   ⚡ queryExpressDeliveryTime (MCP) → 查顺丰标准时效 24h
    │   → 发现 OD20260808002 实际已 72h，超过标准时效 + 延误阈值
    │   → 输出: {intent:"物流延误", urgency:"urgent", route:"auto_resolve"}
    │
    ├─ Step 2: 调用 ResolverAgent（解决 Agent）
    │   ⚡ queryInternalDocs("物流延误 补偿") → 查到补偿政策
    │   → 政策说延误超 72h 可补偿 ¥20 券
    │   ⚡ issueVoucher(userId, amount=20) → ¥20 < ¥50 阈值，直接发放
    │   → 输出: 处理方案已执行
    │
    └─ Step 3: 再次调用 TriageAgent
        → 判断: 已解决，无需转人工
        → 输出: FINISH → 生成《售后诊断报告》
```

> **这不是"用户问、机器人答"，是 Agent 主动发现问题、查政策、执行补偿、判断是否升级。**

---

## 🛠 Agent 工具集

Agent 的大脑是 LLM（DashScope 通义千问），手和眼是 **11 个工具**——9 个 @Tool 本地工具 + 2 个 MCP 工具。

### 本地工具（Java 方法，纳秒级）

| 工具 | 能力 | LLM 什么时候会调它 | 数据源 |
|---|---|---|---|
| `OrderQueryTool` | 查订单列表 / 详情 | 任何涉及订单的场景，先查再答 | L1 Caffeine → L2 Redis → MySQL |
| `LogisticsTool` | 查物流轨迹 | 用户问"快递到哪了" | Mock 物流数据 |
| `RefundTool` | 发起 / 查询退款 | 用户确认要退款后 | MySQL + RocketMQ |
| `ReturnTool` | 发起 / 查询退货 | 用户要退货 | MySQL + RocketMQ |
| `PolicySearchTool` | RAG 混合检索售后政策 | 需要判断"能不能退 / 退多少" | Milvus 向量 + ES BM25 + RRF 融合 |
| `ComplaintTool` | 记录 / 查询投诉 | 用户要投诉 | MySQL + RocketMQ |
| `VoucherTool` | 发补偿券 | 判定需要补偿时 | MySQL + RocketMQ |
| `NotificationTool` | 发 MQ 通知 | 售后操作完成后通知下游 | RocketMQ |
| `DateTimeTools` | 获取当前时间 | 计算时效、判断是否超期 | 本地系统时间 |

### 远程工具（MCP 协议，毫秒级）

| 工具 | 能力 | LLM 什么时候会调它 | 数据源 |
|---|---|---|---|
| `queryExpressDeliveryTime` | 查询快递标准时效 | 需要判断"是否延误"——对比标准时效和实际到达时间 | Python MCP Server (Mock) |
| `getCarrierInfo` | 查询快递公司信息 | 需要了解承运商服务范围、成功率 | Python MCP Server (Mock) |

### 两层工具体系

```
ReactAgent 注册工具（统一为 ToolCallback[]）
  │
  ├─ 9 个 @Tool 本地工具               9 个 Java 方法
  │   (methodTools)                    延迟：纳秒级
  │   核心: 订单 / 退款 / 退货 / 投诉
  │
  └─ 2 个 MCP 工具                      Python 子进程
      (ToolCallbackProvider)           延迟：毫秒级
      外部: 物流时效查询
      通信: JSON-RPC 2.0 / stdio
```

> LLM 看到的是统一的 11 个工具列表，不区分本地还是 MCP。框架自动路由：@Tool 走 Java 方法反射，MCP 走 JSON-RPC 消息到 Python 子进程。

---

## 🏗 技术架构

```
                ┌──────────────────────────┐
                │       前端 Web 界面       │
                │   SSE 流式 + Markdown     │
                └────────────┬─────────────┘
                             │ HTTP / SSE
                ┌────────────▼─────────────┐
                │     ChatController        │
                │  /chat_stream  /ai_ops   │
                │  (Sentinel 限流/熔断)    │
                └───┬────────────────┬─────┘
                    │                │
          ┌─────────▼──────┐  ┌─────▼──────────┐
          │  ChatService    │  │  AiOpsService   │
          │  ReactAgent     │  │  SupervisorAgent│
          │  (单 Agent)     │  │  (多 Agent 协作)│
          └────────┬────────┘  └────────┬────────┘
                   │                     │
                   └──────────┬──────────┘
                              │ 11 个工具调用 (9 @Tool + 2 MCP)
                ┌─────────────▼──────────────┐
                │     Agent 工具层            │
                │  Order │ Logistics │ Refund │
                │  Return│ Policy    │ Complaint
                │  Voucher│ Notification│ Time
                │  + MCP: queryExpressDeliveryTime / getCarrierInfo
                └──┬──────┬──────┬──────┬────┘
                   │      │      │      │
          ┌────────▼──┐ ┌─▼──┐ ┌▼─────┐
          │ L1 Caffeine│ │Milv│ │Rocket│
          │ L2 Redis   │ │us  │ │  MQ  │
          │ MySQL      │ │+ES │ │      │
          │ (多级缓存)  │ │RRF │ │      │
          └───────────┘ └────┘ └──┬───┘
                                ┌──▼────▼──┐
                                │ Consumer  │
                                │  短信通知  │
                                │ MySQL+ES  │
                                │  双写落库  │
                                │ 坐席流转   │
                                └──────────┘

           全链路追踪: SkyWalking Agent → OAP → UI
           限流熔断:   Sentinel Rules → Dashboard
```

更多架构细节参见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

---

## 🔎 RAG 售后政策混合检索

售后政策不是硬编码在 Prompt 里，而是经过 **分片 → 向量化 → 存入 Milvus + ES**，Agent 通过混合检索获取：

```
售后政策文档 (6篇)
  ├─ 七天无理由退换货政策.md
  ├─ 各品类退换货规则.md
  ├─ 退款时效说明.md
  ├─ 物流异常处理流程.md
  ├─ 补偿标准.md
  └─ 投诉处理流程.md

文档处理流程:
  原文 → DocumentChunkService 分片(800字/片, 100字重叠)
       → VectorEmbeddingService 向量化(DashScope text-embedding-v4)
       → 存入 Milvus (IVF_FLAT 索引, L2 距离)    ← 向量检索数据源
       → 存入 Elasticsearch (IK 中文分词)          ← BM25 检索数据源

混合检索流程 (HybridSearchService):
  用户问题 "耳机能不能退款"
    │
    ├─ 向量检索 (Milvus)        → top3 语义相似片段
    │   query → embedding → Milvus.search(L2, topK=3)
    │
    ├─ BM25 关键词检索 (ES)     → top3 关键词匹配片段
    │   query → ES.match(content, ik_smart) → topK=3
    │
    └─ RRF 倒数排名融合
        公式: score(d) = Σ 1/(k + rank_i(d)), k=60
        向量检索第1名: 1/(60+1) = 0.0164
        BM25 检索第1名:   1/(60+1) = 0.0164
        两路都命中:      0.0164 + 0.0164 = 0.0328  ← 融合后排名更高
        → 取融合后 top3 返回给 LLM
```

> **为什么用混合检索而非纯向量检索？** 向量检索擅长语义匹配（"退款"匹配"退货"），BM25 擅长精确关键词匹配（"耳机"精确匹配"耳机"）。RRF 融合两路结果，兼顾语义理解和关键词精度。

---

## 📨 RocketMQ 消息链路

Agent 每执行一次售后操作，不是同步等通知发完，而是扔到 MQ 让下游异步处理：

| Topic | 谁发的 | 谁收的 | 为什么用 MQ |
|---|---|---|---|
| `after-sales-event` | Refund / Return / Complaint / Voucher Tool | 3 个 Consumer | 退款已创建，但发短信、持久化、坐席流转不需要用户等着 |
| `chat-message-persist` | ChatController | 1 个 Consumer | 对话消息异步落库 + ES 索引，不阻塞 SSE 响应 |
| `agent-escalation` | EscalationAgent | 1 个 Consumer | Agent 判断要转人工，通知坐席系统立即创建工单 |

```
after-sales-event
  ├─ notification-srv  → 收到 REFUND_CREATED          → 模拟发短信
  ├─ persistence-srv   → 收到消息                       → 写 MySQL 处理记录
  └─ seat-srv          → 收到 COMPLAINT_CREATED        → 通知坐席跟进
                        → 收到 REFUND_CREATED 且金额>¥5000 → 标记风控待审

chat-message-persist
  └─ persistence-srv   → 写 MySQL t_chat_message + 写 ES chat-message 索引（双写）

agent-escalation
  └─ seat-srv          → 创建转人工工单
```

---

## ⚡ 多级缓存 (Caffeine + Redis)

OrderQueryTool 不是每次都查 MySQL，而是走三级查询：

```
请求查订单
  │
  ├─ L1 Caffeine 本地缓存 (TTL 1min, 容量 500)
  │   命中? → 直接返回（零网络 IO，~0.01ms）
  │   未命中 ↓
  │
  ├─ L2 Redis 分布式缓存 (TTL 5min)
  │   命中? → 返回 + 回填 L1（~1ms，一次网络往返）
  │   未命中 ↓
  │
  └─ MySQL 数据库
      查到 → 写入 L1 + L2（~10ms）
```

> L2 命中后回填 L1 的原因：Caffeine 是本地内存，读取比 Redis 快 100 倍。用户一次对话中可能多次查同一订单（先查列表，再查详情，又回来查列表），回填 L1 后后续查询连 Redis 都不用走。

---

## 🔌 MCP 工具协议

9 个 @Tool 是 Java 方法，和主应用在同一个进程里。但物流时效查询是外部能力，通过 MCP 协议接入：

```
Java 应用进程                    Python MCP Server (子进程)
  │                              │
  │  MCP Client                  │  MCP Server
  │  ←── stdin ──── JSON-RPC ──→ │
  │  ─── stdout ── JSON-RPC ───→ │
  │                              │
  │  Spring AI 启动时拉起         │  python3 logistics_mcp_server.py
  │  Python 子进程                │  主循环: readline → 处理 → write
```

### MCP 通信流程

```
1. Spring Boot 启动 → MCP Client 执行 python3 logistics_mcp_server.py
2. 握手: Java 发 initialize → Python 返回 protocolVersion + serverInfo
3. 发现工具: Java 发 tools/list → Python 返回 2 个工具定义
4. 调用工具: Java 发 tools/call(name, arguments) → Python 执行 → 返回结果
5. LLM 拿到结果继续思考
```

MCP Server 提供 2 个工具：

- `queryExpressDeliveryTime(carrier, originCity, destinationCity)` → 标准时效 + 延误判定阈值
- `getCarrierInfo(carrier)` → 快递公司名称、覆盖率、成功率、服务类型

---

## 🌊 SSE + Reactor Flux 流式输出

对话接口采用 SSE（Server-Sent Events）+ Reactor Flux 实现流式输出，用户可以逐字看到 Agent 的回复，而不是等待完整响应。

```java
// ChatController.chatStream()
SseEmitter emitter = new SseEmitter(300000L);  // 5 分钟超时

Flux<NodeOutput> stream = agent.stream(question);  // ReactAgent 流式调用

stream.subscribe(
    output -> {                              // onNext：每收到一个 chunk
        if (output instanceof StreamingOutput streamingOutput) {
            OutputType type = streamingOutput.getOutputType();
            if (type == OutputType.AGENT_MODEL_STREAMING) {
                String chunk = streamingOutput.message().getText();
                fullAnswerBuilder.append(chunk);     // 累积完整答案
                emitter.send(SseEmitter.event()
                    .name("message")
                    .data(SseMessage.content(chunk), MediaType.APPLICATION_JSON));
            }
        }
    },
    error -> {                               // onError：异常处理
        emitter.send(SseMessage.error(...));
        emitter.completeWithError(error);
    },
    () -> {                                  // onComplete：流结束
        session.addMessage(question, fullAnswerBuilder.toString());
        persistChatMessage(...);              // 异步持久化
        emitter.send(SseMessage.done());
        emitter.complete();
    }
);
```

### 流式输出的关键设计点

| 设计点 | 说明 |
|---|---|
| `SseEmitter` 超时 | 5 分钟（300000ms），覆盖长对话 + 多轮工具调用 |
| `fullAnswerBuilder` | 线程安全累积完整答案，流结束后一次性持久化 |
| `onNext` 过滤 | 只转发 `AGENT_MODEL_STREAMING` 类型，工具调用结果不直接推给前端 |
| `onError` | 异常时先推 error 事件再 `completeWithError`，前端能收到友好提示 |
| `onComplete` | 流结束后持久化对话 + 发 done 事件 + `complete()` 关闭连接 |
| 异步持久化 | 对话消息通过 MQ 异步落库，不阻塞 SSE 响应 |

---

## 🧱 技术栈

| 层级 | 技术 | 版本 | 干什么 |
|---|---|---|---|
| **Agent 框架** | Spring AI Alibaba | 1.1.0.0-RC2 | ReactAgent 单 Agent + SupervisorAgent 多 Agent |
| **LLM** | DashScope 通义千问 | 2.17.0 | 对话生成 + 工具调用决策 |
| **向量化** | DashScope text-embedding-v4 | - | 售后政策文档向量化 |
| **向量库** | Milvus | 2.6.10 | RAG 向量语义检索 |
| **搜索** | Elasticsearch | 8.12.0 | BM25 关键词检索 + RRF 融合 + 对话全文搜索 |
| **消息队列** | RocketMQ | 5.3.1 | 3 Topic / 4 Consumer 异步通知 |
| **缓存** | Redis | 7.x | L2 分布式缓存（TTL 5min） |
| **本地缓存** | Caffeine | 3.1.8 | L1 本地缓存（TTL 1min，多级缓存） |
| **数据库** | MySQL | 8.0 | 7 张业务表 |
| **ORM** | MyBatis-Plus | 3.5.5 | 数据访问层 |
| **熔断限流** | Sentinel | 1.8.8 | 对话/分诊接口 QPS 限流 + LLM 调用 RT 熔断 |
| **链路追踪** | SkyWalking | 9.7.0 | Controller → Agent → Tool → MySQL/MQ 全链路追踪 |
| **外部工具协议** | MCP | 2024-11-05 | JSON-RPC 2.0 / stdio 连接 Python Server |
| **框架** | Spring Boot | 3.2.0 | Web 服务 + SSE 流式输出 |

---

## ⚖️ 内置业务规则

Agent 不是随便答应用户的，它遵守这些规则：

| 规则 | 触发条件 | Agent 行为 |
|---|---|---|
| 退款风控 | 退款金额 > ¥5000 | 提示需风控审核，不承诺到账时间，MQ 通知坐席 |
| 补偿券审批 | 补偿券金额 > ¥50 | 不自动发放，状态标为 PENDING_REVIEW，MQ 通知人工 |
| 投诉升级 | 投诉级别 = CRITICAL | 立即转人工，MQ 通知坐席系统 |
| 政策约束 | 用户要退生鲜 | 查政策发现不支持无理由退货 → 如实告知 |
| 主动转人工 | Agent 连续无法理解用户 | 发 MQ 通知坐席接管 |

---

## 🛡 Sentinel 熔断限流规则

| 资源 | 规则类型 | 阈值 | 说明 |
|---|---|---|---|
| `chatStream` | QPS 限流 | 10 | 对话接口每秒最多 10 个请求 |
| `aiOpsAnalyze` | QPS 限流 | 5 | 分诊接口更耗资源，限制更严 |
| `toolCall` | QPS 限流 | 20 | 工具调用每秒最多 20 次 |
| `llmCall` | RT 熔断 | 30s | LLM 响应超 30s 触发熔断，10s 后恢复 |
| `toolCall` | 异常比例熔断 | 50% | 工具调用异常率超 50% 熔断 |

> 限流后返回友好提示 *"当前请求过多，请稍后重试"*，不是 HTTP 500 报错。

---

## 📁 项目结构

```
KeyueAgent/
├── pom.xml                                # Maven 配置（Spring Boot 3.2）
├── Makefile                               # 一键启动（init/up/start/upload/clean）
├── vector-database.yml                    # Docker Compose（12 个容器）
├── README.md                              # ← 本文件
├── LICENSE                                # MIT
│
├── src/main/java/org/example/
│   ├── Main.java                          # 启动入口（@MapperScan）
│   ├── controller/
│   │   ├── ChatController.java            # 对话 + 分诊接口 (SSE + Sentinel)
│   │   ├── FileUploadController.java      # 政策文档上传
│   │   └── MilvusCheckController.java     # 健康检查
│   ├── service/
│   │   ├── ChatService.java               # 单 Agent 对话编排
│   │   ├── AiOpsService.java              # 多 Agent 协作编排
│   │   ├── HybridSearchService.java       # 混合检索 (向量 + BM25 + RRF)
│   │   ├── ChatSearchService.java         # ES 对话全文搜索
│   │   ├── VectorEmbeddingService.java    # 文本向量化
│   │   ├── VectorSearchService.java       # 向量检索
│   │   ├── VectorIndexService.java        # 向量索引管理
│   │   └── DocumentChunkService.java      # 文档分片
│   ├── agent/tool/                        # ← 9 个 @Tool 本地工具
│   │   ├── OrderQueryTool.java            #    订单查询 (L1 + L2 + MySQL)
│   │   ├── LogisticsTool.java             #    物流查询 (Mock)
│   │   ├── RefundTool.java                #    退款发起 (MySQL + MQ)
│   │   ├── ReturnTool.java                #    退货发起 (MySQL + MQ)
│   │   ├── PolicySearchTool.java          #    政策检索 (Milvus + ES + RRF)
│   │   ├── ComplaintTool.java             #    投诉记录 (MySQL + MQ)
│   │   ├── VoucherTool.java               #    补偿券发放 (MySQL + MQ)
│   │   ├── NotificationTool.java          #    MQ 通知发送
│   │   └── DateTimeTools.java             #    时间工具
│   ├── model/                             # 6 个实体类 + 2 个 ES 文档
│   ├── dao/                               # 6 个 DAO + 2 个 ES Repository
│   ├── mq/                                # MQ 层
│   │   ├── MqProducer.java                #    Producer (3 Topic)
│   │   ├── AfterSalesMessage.java         #    售后事件 DTO
│   │   ├── ChatMessagePersistDTO.java     #    对话持久化 DTO
│   │   └── consumer/
│   │       ├── NotificationConsumer.java  #    短信通知
│   │       ├── PersistenceConsumer.java   #    对话落库 MySQL + ES
│   │       ├── SeatConsumer.java          #    坐席流转
│   │       └── EscalationConsumer.java    #    转人工通知
│   ├── config/                            # 14 个配置类
│   └── constant/                          # 常量
│
├── src/main/resources/
│   ├── application.yml                    # 数据源 + 缓存 + MQ + 限流 + MCP
│   └── static/                            # 前端 (HTML + JS + CSS)
│
├── mcp-server/                            # MCP Server (Python, stdio 模式)
│   └── logistics_mcp_server.py            #    物流时效查询 (JSON-RPC 2.0)
│
├── after-sales-docs/                      # 6 篇售后政策文档（上传到 Milvus + ES）
├── sql/                                   # DDL + Mock 数据（7 表 20+ 条订单）
├── docs/                                  # PRD + 技术改造计划 + 架构
│   ├── PRD-电商售后智能客服Agent.md
│   ├── 技术改造计划.md
│   └── ARCHITECTURE.md
└── .github/                               # CI + Issue 模板
```

---

## 🚀 快速开始

### 0. 环境要求

- **JDK 17+** · **Maven 3.6+** · **Docker 20+** · **Python 3.10+**（用于 MCP Server）
- 一个 **DashScope API Key**（[申请地址](https://dashscope.aliyun.com/)）

### 1. 配置 API Key

```bash
export DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxxxxxx
```

### 2. 一键启动

```bash
make init
```

`make init` 自动完成：

1. 🐳 启动 Docker Compose（MySQL + Redis + RocketMQ + Milvus + ES + Sentinel + SkyWalking，12 个容器）
2. 🚀 启动 Spring Boot 服务（后台运行）
3. ⏳ 等待服务就绪
4. 📤 上传 `after-sales-docs/` 政策文档到 Milvus + ES

### 3. 开始使用

打开浏览器访问：

| 名称 | 地址 | 用途 |
|---|---|---|
| **Web 聊天界面** | http://localhost:9900 | 用户对话 / 智能分诊入口 |
| **RocketMQ Dashboard** | http://localhost:8180 | 查看消息消费进度 |
| **Sentinel Dashboard** | http://localhost:8858 | 配置限流 / 熔断规则 |
| **SkyWalking UI** | http://localhost:8088 | 全链路追踪可视化 |
| **Milvus Attu** | http://localhost:8000 | 向量数据浏览器 |
| **Elasticsearch** | http://localhost:9200 | 全文搜索 & BM25 索引 |

### 4. 验证服务

```bash
# 健康检查（Milvus）
curl http://localhost:9900/milvus/health

# 健康检查（Sentinel 限流统计）
curl http://localhost:9900/milvus/health
```

### 5. 单独操作

```bash
make up          # 仅启动 Docker 容器
make start       # 仅启动 Spring Boot
make upload      # 仅上传售后政策文档
make status      # 查看容器状态
make stop        # 停止 Spring Boot
make down        # 停止 Docker
make restart     # 重启 Spring Boot
make clean       # 清理临时文件
make help        # 显示所有命令
```

---

## 🧪 API 示例

### 1. 对话模式（SSE 流式）— 用户发消息

```bash
curl -N -X POST http://localhost:9900/api/chat_stream \
  -H "Content-Type: application/json" \
  -d '{"id":"s1","UserId":"U10001","question":"我昨天买的耳机想退款，应该怎么处理？"}'
```

SSE 响应流（每条一行）：

```
event:message
data:{"type":"content","data":"您好"}

event:message
data:{"type":"content","data":"，根据"}

event:message
data:{"type":"content","data":"您的订单..."}

event:message
data:{"type":"done","data":""}
```

### 2. 对话模式（同步一次性）— 不带流式

```bash
curl -X POST http://localhost:9900/api/chat \
  -H "Content-Type: application/json" \
  -d '{"id":"s2","UserId":"U10001","question":"我的订单都买过什么？"}'
```

### 3. 智能分诊模式 — Agent 主动巡检

```bash
curl -N -X POST http://localhost:9900/api/ai_ops \
  -H "Content-Type: application/json" \
  -d '{"UserId":"U10001"}'
```

Agent 会主动扫描该用户所有订单 → 查物流 → 查政策 → 生成《售后诊断报告》。

### 4. 上传售后政策文档

```bash
curl -X POST http://localhost:9900/api/upload \
  -F "file=@after-sales-docs/七天无理由退换货政策.md"
```

文档上传后会自动分片 → 向量化 → 存入 Milvus + ES（双写）。

### 5. 会话管理

```bash
# 查看会话状态
curl http://localhost:9900/api/chat/session/s1

# 清空会话历史
curl -X POST http://localhost:9900/api/chat/clear \
  -H "Content-Type: application/json" \
  -d '{"id":"s1"}'
```

---

## 💎 技术亮点

### 1️⃣ LLM 自主工具调用

不是 `if-else` 写死的流程，是 LLM 根据 user prompt 自主决策。同样一句 *"我想退款"*，如果订单状态是 PAID，LLM 会直接走退款；如果订单状态是 SHIPPED，LLM 会先查物流再判断。工具调用链路是动态的。

11 个工具（9 个 @Tool 本地 + 2 个 MCP 远程），LLM 不区分本地还是 MCP，框架自动路由——@Tool 走 Java 方法反射，MCP 走 JSON-RPC 到 Python 子进程。

### 2️⃣ RAG 混合检索（向量 + BM25 + RRF 融合）

售后政策不写在 Prompt 里，而是分片向量化存入 Milvus + ES。检索时并行执行向量语义检索（Milvus L2）和 BM25 关键词检索（ES IK 分词），通过 RRF 倒数排名融合算法（k=60）合并两路结果。向量检索擅长语义匹配，BM25 擅长精确关键词匹配，RRF 兼顾两者优势。**新增政策只需上传文档，不改代码。**

### 3️⃣ 多 Agent 协作

SupervisorAgent 编排 Planner-Executor-Replanner 循环。TriageAgent 主动查所有订单 → 筛异常 → ResolverAgent 执行补偿 → EscalationAgent 判断是否转人工。**支持多轮 Replan，不是一次性的。**

### 4️⃣ MQ 驱动的异步闭环

退款发起后不是同步等通知发完，而是扔到 MQ 让 3 个 Consumer 各干各的。对话消息也通过 MQ 异步双写 MySQL + ES。**转人工是独立 Topic 独立消费组。**

### 5️⃣ Caffeine + Redis 多级缓存

OrderQueryTool 采用 **L1 Caffeine（本地，1min TTL）+ L2 Redis（分布式，5min TTL）两级缓存**。L1 命中零网络 IO，L2 未命中才查 MySQL，L2 命中后回填 L1。缓存命中率通过 Caffeine `recordStats()` 监控。

### 6️⃣ Sentinel 熔断限流

对话接口 QPS 限流 10、分诊接口限流 5（多 Agent 更耗资源）。LLM 调用 RT 超 30s 触发熔断 10s 恢复。工具调用异常比例超 50% 熔断。**限流后返回友好提示而非报错。**

### 7️⃣ SkyWalking 全链路追踪

一次对话请求经过 `Controller → ChatService → ReactAgent → Tool → MySQL/Redis/MQ`，SkyWalking 自动采集全链路 Trace，**可视化排查工具调用耗时瓶颈**。

### 8️⃣ MCP 标准化工具协议

通过 MCP（Model Context Protocol）协议接入 Python 实现的物流时效查询服务，JSON-RPC 2.0 / stdio 通信。@Tool 处理高频核心业务（纳秒级），MCP 处理外部服务集成（毫秒级），**LLM 不区分两者**。

### 9️⃣ 内置风控规则

不是什么请求都自动执行。**退款 > ¥5000 不自动通过**，补偿券 > ¥50 转人工审批，CRITICAL 投诉立即转人工。规则在 Prompt + 代码双重保障。

---

## 🔧 自定义扩展

### 新增 Agent 工具

1. 在 `agent/tool/` 下新建 `@Component` 类
2. 方法上加 `@Tool(description = "...")` 和 `@ToolParam(description = "...")`
3. 在 `ChatService.buildMethodToolsArray()` 中注入新工具
4. 重启服务 → LLM 自动发现并使用

### 新增售后政策文档

```bash
echo "# 新政策\n..." > after-sales-docs/新政策.md
make upload
```

文档自动分片、向量化、存入 Milvus + ES，**不改一行代码** Agent 即可检索到。

### 新增 MCP 工具

1. 在 `mcp-server/` 下新建 Python MCP Server
2. 在 `application.yml` 的 `mcp.client.stdio.connections` 下追加配置
3. 重启服务 → Java 端自动通过 JSON-RPC 调用

---

## 📊 数据规模（Mock 数据）

| 表 | 条数 | 说明 |
|---|---|---|
| `t_order` | 10 条 | 覆盖 PAID / SHIPPED / DELIVERED / REFUNDED / CLOSED |
| `t_refund` | 3 条 | APPROVED / PENDING / REJECTED |
| `t_return` | 2 条 | COMPLETED / PENDING |
| `t_complaint` | 2 条 | OPEN / ESCALATED（覆盖 HIGH + CRITICAL） |
| `t_voucher` | 2 条 | ISSUED 状态 |
| `t_chat_session` + `t_chat_message` | 4 个会话 + 8 条消息 | 模拟历史对话 |

详见 [sql/data.sql](sql/data.sql)。

---

## 📚 文档

- 📋 [PRD - 电商售后智能客服 Agent](docs/PRD-电商售后智能客服Agent.md)
- 📘 [技术改造计划](docs/技术改造计划.md)
- 🏗 [架构详解](docs/ARCHITECTURE.md)
- 📝 [更新日志](CHANGELOG.md)
- 🤝 [贡献指南](CONTRIBUTING.md)

---

## 🤝 贡献

欢迎 PR！请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)，了解：

- 代码风格要求
- 提交信息规范（Conventional Commits）
- Issue / PR 模板

详见 `.github/` 目录下的 Issue & PR 模板。

---

## 📜 License

[MIT](LICENSE) © KeyueAgent Contributors
