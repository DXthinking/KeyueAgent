# 更新日志 (Changelog)

所有重要变更都会记录在此文件。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added / 待发布
- 完整的 RAG 混合检索：向量 (Milvus) + BM25 (Elasticsearch) + RRF 融合
- 多级缓存：L1 Caffeine + L2 Redis + MySQL
- 9 个本地 Agent 工具 + 2 个 MCP 远程工具
- 多 Agent 协作：SupervisorAgent → TriageAgent / ResolverAgent / EscalationAgent
- RocketMQ 消息链路：3 Topic / 4 Consumer
- Sentinel 限流熔断：3 条限流 + 2 条熔断规则
- SkyWalking 全链路追踪集成
- SSE + Reactor Flux 流式输出
- 6 篇售后政策文档（Mock 数据集）
- 7 张业务表 + Mock 数据 (20+ 条订单)

## [0.1.0] - 2026-08-23

### Changed
- 将原 AIOps 通用运维 Agent 改造为电商售后智能客服 Agent
- 移除：Prometheus 告警查询、CLS 日志查询、运维知识库
- 新增：订单查询、物流查询、退款/退货发起、投诉记录、补偿券发放、政策混合检索
- Frontend 页面文案从 "AIOps" 改为 "客悦售后智能客服"
- Maven 依赖更新：增加 MyBatis-Plus / RocketMQ / Sentinel / Caffeine / Elasticsearch / MCP

### Added
- 项目根 README（替换原简短 README）
- docs/ 目录：PRD + 技术改造计划 + 架构详解
- .github/ 工作流：CI、Issue / PR 模板
- CHANGELOG.md / CONTRIBUTING.md

[Unreleased]: https://github.com/DXthinking/KeyueAgent/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/DXthinking/KeyueAgent/releases/tag/v0.1.0
