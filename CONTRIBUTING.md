# 贡献指南 (CONTRIBUTING)

感谢你考虑为 **客悦电商售后智能客服 Agent** 贡献代码！

## 🤝 行为准则

- 友善、尊重、专注于技术本身
- 接受建设性的批评，以优雅的姿态争论
- 把社区利益放在个人利益之上

## 🐛 报告 Bug

请用 GitHub Issues 提交，并在标题前加 `[BUG]`。

一份好的 Bug 报告应包含：
- 复现步骤（越详细越好）
- 期望行为 vs 实际行为
- 截图（如果适用）
- 环境信息（OS、JDK 版本、Docker 版本）
- 相关日志（注意脱敏）

## ✨ 提交功能请求

用 GitHub Issues 提交，标题加 `[Feature]`。

请先描述：
- **为什么需要**：业务场景 / 用户痛点
- **怎么用**：API 调用示例 / UI 截图
- **替代方案**：是否考虑过其他实现

## 🔧 Pull Request 流程

1. Fork 仓库
2. 从 `main` 创建特性分支
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. 编写代码 + 测试
4. 自测通过：编译 + 启动服务
5. 提交 commit（参考下文提交规范）
6. Push 到你的 Fork
7. 在 GitHub 上创建 Pull Request

### 提交信息规范（Conventional Commits）

```
<type>(<scope>): <subject>

<body>

<footer>
```

| Type | 说明 |
|---|---|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `docs` | 文档变更 |
| `style` | 代码格式（不影响逻辑） |
| `refactor` | 重构 |
| `perf` | 性能优化 |
| `test` | 测试相关 |
| `chore` | 构建/工具/依赖变更 |

示例：
```
feat(agent): 新增 OrderQueryTool 支持批量查询

- 新增 queryUserOrders(userId, limit, offset)
- 新增 queryBatchOrders(List<String> orderIds)
- 单元测试：OrderQueryToolTest

Closes #123
```

## 📐 代码风格

### Java

- 遵循 [Alibaba Java Coding Guidelines](https://github.com/alibaba/p3c)
- 使用 Lombok 简化 POJO
- 避免过深的方法嵌套（≤ 3 层）
- 关键业务逻辑必须打日志
- 类/方法 Javadoc 公共 API 必须有

### 前端

- 不用 jQuery，原生 JS / ES6+ 即可
- 组件化（按页面拆分 JS 模块）
- 不引入额外依赖（marked.js + highlight.js 已够用）

### SQL

- 表名 `t_xxx`，字段名 `snake_case`
- 主键统一 `id`（VARCHAR 或 BIGINT）
- 必备字段：`created_at`, `updated_at`
- 索引：外键和常用查询字段

## 🧪 测试

- 单元测试：`src/test/java/`
- 集成测试：`src/test/java/` 用 `@SpringBootTest`
- API 测试：用 curl / Postman 提供示例
- 文档同步：改 API 时同步更新 README

## 📦 提交流程

```bash
# 1. 确保 CI 通过
mvn clean compile
mvn test

# 2. 提交
git add -A
git commit -m "feat: 新增 XXX"
git push origin feature/your-feature-name

# 3. 在 GitHub 上创建 PR
```

## 🏷 标签说明

仓库 Issue / PR 使用以下标签：

| 标签 | 颜色 | 用途 |
|---|---|---|
| `bug` | 🔴 red | 确认的 bug |
| `enhancement` | 🟢 green | 新功能 |
| `documentation` | 🔵 blue | 文档 |
| `good first issue` | 🟣 purple | 适合新手 |
| `help wanted` | 🟡 yellow | 需要帮助 |
| `priority: high` | 🟠 orange | 高优先级 |
| `wontfix` | ⚫ gray | 不予处理 |
| `duplicate` | ⚪ white | 重复 |

## 📞 联系方式

- GitHub Issues: [DXthinking/KeyueAgent/issues](https://github.com/DXthinking/KeyueAgent/issues)
- 邮件: 15154716+colddx@user.noreply.gitee.com

## 📜 License

贡献代码即同意以 [MIT License](LICENSE) 发布。
