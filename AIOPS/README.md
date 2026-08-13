# 客悦电商售后智能客服 Agent

基于 Spring Boot、Spring AI Alibaba 和 DashScope 的电商售后智能客服示例。系统支持多轮 SSE 对话、订单/物流查询、退款退货、投诉与补偿券处理，并通过政策 RAG、RocketMQ 异步链路和多级缓存形成售后闭环。

## 技术栈

- Java 17、Spring Boot 3.2、Spring AI Alibaba 1.1.0.0-RC2、DashScope 2.17.0
- Milvus 2.6.10 + Elasticsearch 8.12.0：向量检索、BM25 与 RRF 混合检索
- MySQL 8.0 + MyBatis-Plus 3.5.5：订单和售后业务数据
- Redis 7 + Caffeine 3.1.8：L2/L1 多级缓存
- RocketMQ 5.3.1：通知、消息持久化和坐席升级
- Sentinel 1.8.8、SkyWalking 9.7.0、MCP 2024-11-05

## 快速开始

```bash
export DASHSCOPE_API_KEY=your-api-key
docker compose -f vector-database.yml up -d
mvn spring-boot:run
```

项目提供 `make init` 一键启动和上传 `after-sales-docs/` 政策文档；初始化 SQL 位于 `sql/schema.sql` 和 `sql/data.sql`。MCP 物流服务位于 `mcp-server/logistics_mcp_server.py`。

## API 示例

```bash
curl -X POST http://localhost:9900/api/chat_stream \
  -H "Content-Type: application/json" \
  -d '{"Id":"s1","UserId":"U10001","Question":"我买的耳机想退款，应该怎么处理？"}'

curl -X POST http://localhost:9900/api/ai_ops \
  -H "Content-Type: application/json" \
  -d '{"UserId":"U10001"}'
```

服务端口为 `9900`；RocketMQ Dashboard、Sentinel Dashboard、SkyWalking UI、Milvus Attu 和 Elasticsearch 分别使用 `8180`、`8858`、`8088`、`8000` 和 `9200`。
