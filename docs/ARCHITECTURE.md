# 架构详解（ARCHITECTURE）

> 电商售后智能客服 Agent 的完整架构说明。

## 一、整体分层

```
┌───────────────────────────────────────────────────────────┐
│                  Presentation Layer                       │
│  static/index.html + app.js + styles.css (SSE + Markdown) │
└───────────────────────────┬───────────────────────────────┘
                            │ HTTP / SSE
┌───────────────────────────▼───────────────────────────────┐
│                   API Gateway Layer                       │
│   ChatController · FileUploadController · MilvusCheck    │
│   (Sentinel 限流/熔断 + Global CORS)                      │
└─────┬─────────────────────────────────────────┬───────────┘
      │                                         │
┌─────▼─────────────────┐          ┌────────────▼────────────┐
│  ChatService           │          │  AiOpsService            │
│  ReactAgent            │          │  SupervisorAgent         │
│  (单 Agent 对话)       │          │  (多 Agent 协作)         │
└─────┬─────────────────┘          └────────────┬────────────┘
      │                                         │
      └────────────────┬────────────────────────┘
                       │ 11 个 ToolCallback（统一接口）
       ┌───────────────▼──────────────────┐
       │       Agent 工具层                │
       │  9 个 @Tool 本地 + 2 个 MCP 远程   │
       └──────┬─────────┬─────────┬────────┘
              │         │         │
       ┌──────▼───┐ ┌───▼────┐ ┌──▼─────┐
       │ 多级缓存 │ │ 向量库 │ │  MQ    │
       │ Caff+RD  │ │ MLV+ES │ │ Rocket │
       └──────────┘ └────────┘ └────────┘
```

## 二、核心模块

### 2.1 ReactAgent 单 Agent 模式（`ChatService`）

**职责**：用户驱动的对话场景。用户发问，Agent 自主决定调用哪个工具、按什么顺序调用、调几次。

**关键设计**：
- `ReactAgent` 由 Spring AI Alibaba 提供
- 工具列表通过 `buildMethodToolsArray()` 注册
- ToolCallback 数组：`9 个 @Tool + (optional) MCP 提供的`
- `ToolCallbackProvider` 为 `required = false`，兼容 MCP 未配置的情况

**流式输出**：
- `agent.stream(question)` 返回 `Flux<NodeOutput>`
- `onNext` 过滤 `OutputType.AGENT_MODEL_STREAMING` 类型向前端 SSE 推送
- `onComplete` 累积完整答案后通过 MQ 异步持久化

### 2.2 SupervisorAgent 多 Agent 模式（`AiOpsService`）

**职责**：Agent 主动巡检用户最近 7 天订单，发现异常并生成《售后诊断报告》。

**子 Agent 协作链**：

```
SupervisorAgent (调度层)
  │
  ├─ TriageAgent (分诊 Agent)
  │   → queryUserOrders / queryLogistics / queryExpressDeliveryTime
  │   → 筛选异常订单，输出 decision = {PLAN | EXECUTE | FINISH}
  │
  ├─ ResolverAgent (执行 Agent)
  │   → createRefund / createReturn / issueVoucher / createComplaint
  │   → 执行具体售后动作
  │
  └─ EscalationAgent (升级 Agent)
      → sendNotification → 发 agent-escalation topic → 坐席接管
```

**循环机制**：SupervisorAgent 通过 `OverAllState` 在 agent 间共享上下文；每轮决策后根据结果选择下一步；强制最大 5 轮循环防 Replan 死循环。

### 2.3 多级缓存（`CaffeineConfig` + `OrderQueryTool`）

**L1 Caffeine（本地）**：
- `orderCache`：500 条订单详情，TTL 1min
- `userOrdersCache`：200 个用户的订单列表，TTL 1min
- 命中后零网络 IO（~0.01ms）

**L2 Redis（分布式）**：
- TTL 5min，JSON 序列化
- 命中后回填 L1（~1ms）
- Redis 不可用时自动降级到 MySQL

**MySQL 回源**：
- 查到后同时写 L1 + L2（~10ms）

### 2.4 RAG 混合检索（`HybridSearchService`）

**两类数据源**：
- **向量检索**：Milvus（IVF_FLAT + L2 距离）—— 语义匹配
- **BM25 关键词检索**：Elasticsearch（IK 分词器）—— 精确匹配

**RRF 倒数排名融合算法**：
```
score(d) = Σ 1 / (k + rank_i(d))
其中 k = 60（标准常数），rank 从 1 开始
```

**新增政策文档只需**：
1. 把 `.md` 文件丢到 `after-sales-docs/`
2. `make upload`

**不改一行代码，Agent 自动可检索**。

### 2.5 MQ 异步闭环（`MqProducer` + 4 个 Consumer）

**3 个 Topic**：

| Topic | Producer | Consumer | 用途 |
|---|---|---|---|
| `after-sales-event` | Refund/Return/Complaint/Voucher Tool | notification-srv / persistence-srv / seat-srv | 售后事件通知 |
| `chat-message-persist` | ChatController | persistence-srv | 对话消息落库（MySQL + ES 双写） |
| `agent-escalation` | EscalationAgent | seat-srv | 转人工通知 |

**消息幂等性保证**：
- 所有 Consumer 失败时自动重试 RocketMQ 内置机制
- 业务表主键唯一约束防重复插入

### 2.6 Sentinel 熔断限流

**3 条限流规则** + **2 条熔断规则**：

| 资源 | 类型 | 阈值 | 含义 |
|---|---|---|---|
| `chatStream` | QPS 限流 | 10 | 对话接口 |
| `aiOpsAnalyze` | QPS 限流 | 5 | 分诊接口（更耗资源） |
| `toolCall` | QPS 限流 | 20 | 工具调用 |
| `llmCall` | RT 熔断 | 30s | LLM 响应超时 |
| `toolCall` | 异常比例 | 50% | 工具失败率过高 |

**生效方式**：`@SentinelResource` 注解 + `blockHandler` 回退方法，限流后返回友好提示（不是 500）。

### 2.7 MCP 外部工具（Python stdio）

**为什么用 MCP**：物流时效是外部能力（不在 Java 进程内），通过 MCP 标准化协议接入。

**通信**：JSON-RPC 2.0 / stdin-stdout。

**Spring AI 集成**：`spring-ai-starter-mcp-client-webflux` 自动管理 Python 子进程、发现工具、路由调用。

**LLM 视角**：MCP 工具和 Java @Tool 工具是统一的 `ToolCallback`，LLM 无感。

## 三、数据模型（7 张表）

```
t_order (订单)
  ├─ t_refund (退款)
  ├─ t_return (退货)
  ├─ t_complaint (投诉)
  └─ t_voucher (补偿券)

t_chat_session (会话)
  └─ t_chat_message (消息)

ES 索引（双写）:
  ├─ chat-message (对话全文搜索)
  └─ policy-doc (售后政策文档)
```

详见 [`../sql/schema.sql`](../sql/schema.sql)。

## 四、容器清单（12 个）

启动顺序：

```
基础设施层（无依赖）
  ├─ milvus-etcd (etcd)
  └─ milvus-minio (MinIO)

依赖基础设施层
  └─ milvus-standalone (Milvus)
       └─ milvus-attu (Web UI)

基础设施层（无依赖）
  ├─ cs-mysql
  ├─ cs-redis
  ├─ rocketmq-nameserver
  ├─ cs-elasticsearch
  ├─ cs-sentinel-dashboard
  └─ cs-skywalking-oap

依赖层
  ├─ rocketmq-broker (依赖 nameserver)
  ├─ rocketmq-dashboard (依赖 nameserver)
  └─ cs-skywalking-ui (依赖 skywalking-oap)
```

详见 [`../vector-database.yml`](../vector-database.yml)。

## 五、请求追踪链（SSE 一次完整调用）

```
1. 浏览器 POST /api/chat_stream
   └─ ↓ SkyWalking trace_id=t1

2. ChatController.chatStream()
   └─ ↓ Sentinel 限流校验 chatStream
       └─ 创建 SseEmitter (300s)
           └─ 提交到 executor 异步线程池

3. ChatService.buildSystemPrompt() — 组装 System Prompt
   └─ 读 session.history() (in-memory Map)

4. ChatService.createReactAgent() — 构建 ReactAgent
   └─ 注入 chatModel + systemPrompt + toolCallbacks

5. agent.stream(question) → Flux<NodeOutput>
   └─ LLM DashScope 推理（被监控为 llmCall）
       └─ LLM 决定调用哪个工具
           ├─ OrderQueryTool.queryUserOrders() (Caffeine + Redis + MySQL)
           ├─ PolicySearchTool.queryInternalDocs() (Milvus + ES + RRF)
           └─ ... (其他工具)

6. agent 输出 chunks → SseEmitter.send()
   └─ 浏览器实时显示

7. onComplete 累积完整答案 → MqProducer.sendChatMessagePersist()
   └─ RocketMQ topic=chat-message-persist
       └─ PersistenceConsumer
           ├─ MySQL INSERT INTO t_chat_message
           └─ ES INDEX chat-message

8. emitter.send(done) + emitter.complete()
   └─ 浏览器关闭 SSE 连接

9. SkyWalking 上 trace_id=t1 的完整链路可查
   └─ 看到每个工具调用的真实耗时
```

## 六、性能指标（设计目标）

| 指标 | 目标 | 备注 |
|---|---|---|
| 单次对话响应 (P95) | < 3s | L1 缓存命中 + LLM QPS=10 |
| 智能分诊报告 (P95) | < 30s | 多 Agent 协作 + Redis 命中 |
| 工具调用成功率 | > 99.5% | 异常 >50% 自动熔断 |
| 缓存命中率 | > 80% | Caffeine + Redis 双层 |
| MQ 消费延迟 (P95) | < 100ms | 同 Topic + 并发消费 |

## 七、扩展点

| 想做什么 | 怎么做 |
|---|---|
| 加一个新工具 | 在 `agent/tool/` 加 `@Component` + `@Tool` 方法 |
| 改一条政策 | 修改 `after-sales-docs/*.md` 后 `make upload` |
| 加一个新的 MCP 服务 | 在 `mcp-server/` 加新 Python 文件 + application.yml 加配置 |
| 加一张表 | `sql/schema.sql` 加 DDL + `model/` 加实体 + `dao/` 加 Mapper |
| 改限流阈值 | 编辑 `SentinelConfig.java`（或接入 Sentinel Dashboard 热更新） |

## 八、安全注意

| 模块 | 风险 | 缓解 |
|---|---|---|
| LLM Prompt | 注入攻击 | System Prompt 限定角色；敏感操作转人工审核 |
| 退款金额 | 资损风控 | >¥5000 必须 PENDING_REVIEW，需坐席审核 |
| 补偿券 | 滥用风险 | >¥50 必须 PENDING_REVIEW |
| 用户身份 | 伪造 UserId | 真实生产应在 API 网关层校验 JWT |
| MQ 消费 | 重复消费 | 主键唯一约束 + Consumer 端幂等校验 |
| API Key | 泄露 | 通过环境变量注入，代码不硬编码 |
