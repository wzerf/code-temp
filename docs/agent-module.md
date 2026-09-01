AgentScope Agent 后台系统架构
状态：目标架构（未落地）
范围：数据与模块设计。不修改现有 Java 代码、数据库脚本或 SDK 原字段。
读者：实现该平台的工程师与 agent。
校验基准：AgentScope Java 2.0.1 官方文档、当前仓库 CONTEXT.md / backend/db/docs/db-conventions.md / java-admin POM。

在现有 agentscope-harness:2.0.1 之上，采用控制面 + 运行面：

控制面：市场、个人资产、发布审核、Agent 定义与不可变版本、权限和装配策略。
运行面：按 Agent Revision 将 Skill、MCP、Java Tool、记忆策略、上下文压缩策略解析为一次可执行的 HarnessAgent 调用；会话状态、工作区与分布式锁由 RedisDistributedStore 承载。

业务目标：建设一个可运营的 Agent 平台后台，让管理员、发布者和终端用户能够管理 Agent、Skill、MCP 服务及受信任 Java 工具，并以可控、可复现、安全的方式执行对话式 Agent。

### 核心产品能力

1.  Agent 管理

- 创建 Agent 定义及其草稿版本（Revision）。
- 每次发布生成不可变版本快照，包含系统提示词、模型配置、权限策略、记忆/上下文压缩策略，以及已绑定的 Skill、MCP、Java Tool。
- 支持发布、回滚、紧急禁用。
- 新会话使用当前发布版本；已开始的会话固定在首次解析到的 Revision，升级不会静默影响历史会话。
- 用户如需切换版本，必须新建会话，不能把旧会话直接迁移到新版本。

2.  Agent 对话运行

- 提供流式 SSE 对话接口：发送消息、接收流式回复、取消执行、人工确认后恢复执行（HITL）。
- 同一会话可跨请求续聊；会话状态、工具状态、确认状态由 Redis 持久化。
- 同一个 requestId 的重试必须短时幂等，避免重复调用带副作用的工具。
- 用户取消时必须中断 Agent 执行，不能只断开 HTTP 而让后台工具继续运行。

3.  Skill 市场与个人 Skill

- 细节：[`docs/agent-module-skill.md`](agent-module-skill.md)。
- Skill 是指令和资料包，不是可直接执行的工具；可包含 SKILL.md、参考资料、受控脚本。
- 支持私有 Skill、市场 Skill、草稿、提交审核、发布、下架、弃用、安装。
- 发布 Skill 后生成不可变 Release 与内容哈希；后续修改必须发新版本。
- 用户安装 Skill 不代表自动给所有 Agent 使用，必须显式绑定到某个 Agent Revision。
- 同名 Skill 覆盖必须在 Revision 中明确配置，禁止私有 Skill 静默覆盖市场 Skill。
- 已发布 Revision 固定引用具体 Skill Release，市场发布新版本不能改变旧 Agent 行为。

4.  MCP 市场与个人 MCP

- 支持 HTTP/SSE/后续受控 STDIO MCP 服务的草稿、验证、审核、发布、安装和绑定。
- 发布前必须握手并获取工具目录快照；运行时只允许调用发布快照及 Binding 白名单中的工具。
- MCP 服务端工具新增、删除或 schema 改变时，识别为目录漂移并拒绝使用，不能自动放行未知工具。
- 用户配置与凭据分离：数据库只保存配置和 secret_ref，API Key、OAuth Token 等明文不得进入数据库、日志或模型上下文。
- MCP 连接按用户、凭据版本、MCP 版本隔离；配置或凭据变化后必须重建连接。

5.  Java Tool 管理

- Java Tool 仅来自已部署、受信任的 Spring Bean；禁止上传 JAR、填写类名、扫描任意 classpath 或执行用户命令。
- 应用启动时扫描并生成只读工具目录，包含名称、描述、参数 schema、风险级别、只读属性和版本 hash。
- 后台只能选择已发现且启用的 Tool 绑定到 Agent Revision。
- Tool 名称冲突、schema 非法、发布版本引用缺失/漂移 Tool 时必须阻止启动或发布。

6.  权限与人工确认

- 后台 RBAC 决定用户能否配置、发布、安装和调用资源。
- Agent 运行时权限决定模型本次是否可执行具体 Tool；两层必须同时通过。
- 默认：只读工具可放行；可回滚写操作需人工确认；高风险、不可逆、跨用户或生产操作默认拒绝。
- 无人值守模式中，未被明确允许的操作不得因为“无需询问”而自动执行。
- Tool 自身的输入校验和权限检查不可被全局策略绕过。

7.  记忆、工作区与运行状态

- Redis 是会话状态、共享工作区 KV、分布式执行锁的唯一运行时存储。
- MySQL 保存控制面配置：资产、版本、安装关系、Agent Revision、会话与版本绑定；不复制 Redis 的 Agent 状态。
- 长期记忆按“用户 + Agent”隔离，禁止不同 Agent 自动共享用户记忆。
- 提供“忘记”能力：删除长期记忆并使关联会话状态失效。

## 17. 参考依据

- [AgentScope Java：上生产](https://java.agentscope.io/v2/zh/docs/others/going-to-production.html)：`RedisDistributedStore`、多副本状态、Remote workspace、sandbox 快照与执行锁。
- [AgentScope Java：Harness 架构](https://java.agentscope.io/v2/zh/docs/harness/architecture.html)：Harness 的状态、记忆、压缩、Skill、MCP 和 Channel 装配边界。
- [AgentScope Java：Skill](https://java.agentscope.io/v2/zh/docs/harness/skill.html)：市场仓库、工作区层级、同名覆盖、审核与制品物化机制。
- [`docs/agent-module-skill.md`](agent-module-skill.md)：Skill 表结构与控制面/运行面流程。
- [AgentScope Java：Redis State Store](https://java.agentscope.io/v2/en/integration/session/redis.html)：Redis 状态存储与 client adapter。
- [AgentScope Java：Tool](https://java.agentscope.io/v2/zh/docs/building-blocks/tool.html)：`Toolkit`、Java Tool、MCP 命名 `mcp__{server}__{tool}`、Skill Tool Group。
- [AgentScope Java：智能体](https://java.agentscope.io/v2/zh/docs/building-blocks/agent.html)：`RuntimeContext` 一等字段、`AgentStateStore`、流式事件、中断、HITL 恢复。
- [AgentScope Java：Middleware](https://java.agentscope.io/v2/zh/docs/building-blocks/middleware.html)：可选 `OtelTracingMiddleware`（运行面运维，非证据面）。
- [AgentScope Java：Permission System](https://java.agentscope.io/v2/zh/docs/building-blocks/permission-system.html)：`PermissionBehavior` 与 `PermissionMode`（含 `DONT_ASK`）。
- 本仓库：`CONTEXT.md`（Admin API、当前不做部门）、`backend/db/docs/db-conventions.md`、ADR-0005（共享 OkHttpClient）。
