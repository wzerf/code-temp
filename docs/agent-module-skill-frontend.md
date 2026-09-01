# Agent Skill 前端对接说明

> **状态：后端已落地（架构 9.4），管理端 UI 未做**
> **读者：** React Admin / Vue Vben 前端
> **范围：** 对接现有 Admin API 做 Skill 草稿、审核发布、市场、安装，以及把 Skill Release 绑到 Agent 草稿 Revision。
> **后端来源：** `AgentSkillController`、`AgentController` 的 Revision 创建/更新；领域规则见 [`agent-module-skill.md`](agent-module-skill.md)。
> **不做：** MCP / HITL / Git·Nacos 市场 / Skill 脚本沙箱运维页；不要用 mock 数据替代这些接口。

Skill 是指令和资料包，不是可执行 Tool。前端只管理「包内容 + 生命周期 + 绑定」；对话运行面由后端按会话固定 Revision 的 Binding 快照装配。

## 1. 给前端的不变量

1. **安装 ≠ 绑定。** 市场安装只表示有资格。要进某次 Agent 运行，必须把某个 **Skill Release** 写进该 Agent **草稿 Revision** 的 `skillBindings`，再发布该 Revision。
2. **绑定的是 Release，不是市场最新行。** 市场同名再发新版，旧 Revision 仍跑旧 `contentHash`。
3. **私有 Skill 不进市场列表。** `visibility=PRIVATE` 发布后，`GET /api/agent/skills/market` 看不到它；所有者走「可绑定」列表。
4. **下架 = 市场当前行消失。** `DELETE /api/agent/skills/market/{name}` 后市场列举不到；已绑定的旧 Revision 仍可用快照。
5. **同名覆盖必须明示。** 同一草稿 Revision 若提交两个同名 Release（市场 vs 私有），必须恰好一条 `overrideWinner: true`，否则保存绑定失败。
6. **对话页暂不改。** 现有 Agent 对话只消费已发布 Revision。Skill 绑好并发布 Revision 后，**新会话**才会带上这些 Skill。

## 2. HTTP 约定

与现有 Admin API 一致：

| 项       | 约定                                                                                                                        |
| -------- | --------------------------------------------------------------------------------------------------------------------------- |
| 前缀     | 浏览器请求 `/api/...`。React 现有 `apps/react-admin/src/api/rest/request` 已加 `/api`，客户端 path 写 `/agent/skills/...`。 |
| 鉴权     | `Authorization: Bearer <accessToken>`                                                                                       |
| 成功体   | `{ code: 0, msg, data }`                                                                                                    |
| 失败体   | `{ code: 非0, msg }`，`msg` 可直接展示。常见：`1001` 参数错误、`2001` 未登录、`2004` 无权限                                 |
| 列表     | 本模块接口返回 **数组**，不是分页 `{ items, total }`                                                                        |
| 时间     | 平台墙钟 `Asia/Shanghai` 的本地日期时间字符串，无 `Z`                                                                       |
| 部分更新 | `PUT` 草稿：JSON **省略字段 = 不改**；key 出现才更新                                                                        |

安全中间件（时间戳 / nonce / 签名）与现有 Agent 接口相同，复用现有 `request` 封装，不要另开裸 `fetch`。

## 3. 包形态（创建/编辑表单）

前端提交的是拆开的字段，不是 zip：

| 字段           | 含义                                                          |
| -------------- | ------------------------------------------------------------- |
| `name`         | 与 `SKILL.md` YAML frontmatter 的 `name` **必须一致**         |
| `description`  | 与 frontmatter `description` 一致；可省略，后端用 frontmatter |
| `skillContent` | 完整 `SKILL.md` 文本，必须以 `---\n` 开头的 frontmatter       |
| `resources`    | 对象：`相对路径 → 文件内容`。路径禁止 `..`、绝对路径、反斜杠  |
| `visibility`   | `MARKET` 或 `PRIVATE`                                         |

`SKILL.md` 最小合法例子：

```markdown
---
name: code-reviewer
description: Review pull requests
---

按检查单审阅变更。
```

附属文件示例：

```json
{
  "resources": {
    "references/style-guide.md": "# Style",
    "scripts/lint.sh": "#!/bin/sh\necho ok"
  }
}
```

## 4. 状态机（按钮显隐）

### 4.1 草稿 `status`

```
DRAFT ──提交──► PENDING_REVIEW ──通过──► （草稿变为 CONSUMED，同时产生 Release）
  ▲                    │
  │                    ├─撤回──► DRAFT
  │                    └─驳回──► REJECTED ──改内容后可再提交──► PENDING_REVIEW
```

| status           | 可编辑内容     | 可提交审核 | 可撤回 | 可驳回/通过 |
| ---------------- | -------------- | ---------- | ------ | ----------- |
| `DRAFT`          | 是             | 是         | 否     | 否          |
| `PENDING_REVIEW` | 否             | 否         | 是     | 是          |
| `REJECTED`       | 是             | 是         | 否     | 否          |
| `CONSUMED`       | 否（只读历史） | 否         | 否     | 否          |

同一用户、同一 `name` 同时只能有一份未 `CONSUMED` 的草稿。

### 4.2 Release `status`

| status       | 含义                  | 前端                                     |
| ------------ | --------------------- | ---------------------------------------- |
| `PUBLISHED`  | 可被新 Binding 选用   | 出现在「可绑定」                         |
| `DEPRECATED` | 不可再绑到新 Revision | 旧会话仍可能在用，管理列表可标「已弃用」 |

MARKET 的 `ownerUserId` 为 `0`（平台）。PRIVATE 的 `ownerUserId` 为创建者。

## 5. Skill API

Base：`/api/agent/skills`（React client：`/agent/skills`）。

### 5.1 草稿

#### 创建

`POST /api/agent/skills/drafts`

```json
{
  "name": "code-reviewer",
  "description": "Review pull requests",
  "skillContent": "---\nname: code-reviewer\ndescription: Review pull requests\n---\n按检查单审阅。\n",
  "visibility": "MARKET",
  "resources": {
    "references/style-guide.md": "# Style"
  },
  "basedOnReleaseId": null,
  "remark": ""
}
```

`data`：`SkillDraft`。

从已有 Release 开新草稿时填 `basedOnReleaseId`。

#### 列表 / 详情

- `GET /api/agent/skills/drafts` → `SkillDraft[]`（当前用户）
- `GET /api/agent/skills/drafts/{id}` → `SkillDraft`

#### 更新（部分字段）

`PUT /api/agent/skills/drafts/{id}`

只传要改的 key。`skillContent` / `resources` 任一出现时，后端会按整包重算 `contentHash`。`REJECTED` 更新成功后会回到 `DRAFT`。

```json
{
  "skillContent": "---\nname: code-reviewer\ndescription: Review pull requests\n---\n更新后的指令\n",
  "resources": {}
}
```

空 body `{}` 合法，表示什么都不改。

#### 提交 / 撤回 / 驳回 / 通过

| 方法 | 路径                    | body                                | 成功 `data`                    |
| ---- | ----------------------- | ----------------------------------- | ------------------------------ |
| POST | `/drafts/{id}/submit`   | 无                                  | `SkillDraft`                   |
| POST | `/drafts/{id}/withdraw` | 无                                  | `SkillDraft`                   |
| POST | `/drafts/{id}/reject`   | `{ "comment": "说明不足" }`，可省略 | `SkillDraft`                   |
| POST | `/drafts/{id}/approve`  | 无                                  | **`SkillRelease`**（不是草稿） |

通过后：

- 草稿 `status=CONSUMED`
- 插入不可变 Release，`version` 从 1 递增
- `visibility=MARKET` 时 upsert 市场当前行
- `visibility=PRIVATE` 时市场列表不变

### 5.2 市场

- `GET /api/agent/skills/market` → `SkillMarket[]`（仅当前已发布 MARKET 行）
- `DELETE /api/agent/skills/market/{name}` → `data` 为 `null`。`name` 为 Skill 名（如 `code-reviewer`），不是数字 id。

市场 VO 没有 `skillContent`；要看正文请再 `GET /api/agent/skills/releases/{currentReleaseId}`。

### 5.3 安装

- `POST /api/agent/skills/install` body `{ "name": "code-reviewer" }` → `SkillInstall`
- `DELETE /api/agent/skills/install/{id}` → `data` 为 `null`。这里的 `id` 是 **安装行 id**，不是 Skill id。

卸载不影响已经发布的 Agent Revision。新草稿 Revision 不能再选该市场 Skill，除非重装。

PRIVATE Skill **不必安装**；创建者天然可绑定。

### 5.4 可绑定列表

`GET /api/agent/skills/bindable` → `BindableSkill[]`

这是绑到 Agent 草稿时的候选源：已安装的 MARKET 最新 `PUBLISHED` Release + 自己的 PRIVATE `PUBLISHED` Release。

UI：多选 `skillReleaseId`，写入 Revision 的 `skillBindings`。

### 5.5 Release

- `GET /api/agent/skills/releases/{id}` → `SkillRelease`（含 `skillContent` 与 `resources`）
- `POST /api/agent/skills/releases/{id}/deprecate` → `SkillRelease`（`status=DEPRECATED`）

弃用 ≠ 下架。只要市场当前行还在，`GET /market` 仍能看到该 name。下架用市场 DELETE。

### 5.6 Git 来源与同步

Git 同步是创建或更新草稿的受控入口，不是浏览器直连 Git，也不会直接发布 Release、写入市场或改变已发布 Agent Revision。所有请求复用 `request` 封装与现有鉴权/签名中间件。

#### 来源

| 方法   | 路径                | body / 成功 `data`                               | 权限与效果                                                                          |
| ------ | ------------------- | ------------------------------------------------ | ----------------------------------------------------------------------------------- |
| POST   | `/git-sources`      | `CreateGitSkillSourceRequest` → `GitSkillSource` | 管理员可建 `MARKET` 来源；普通用户只能建自己的 `PRIVATE` 来源，后端拒绝越权 scope。 |
| GET    | `/git-sources`      | → `GitSkillSource[]`                             | 管理员看平台来源；用户只看自己的来源。                                              |
| GET    | `/git-sources/{id}` | → `GitSkillSource`                               | 仅平台来源管理员或来源所有者。                                                      |
| PUT    | `/git-sources/{id}` | `UpdateGitSkillSourceRequest` → `GitSkillSource` | 改 URL、ref、子目录或 `secretRef` 后清空最近同步结果。                              |
| DELETE | `/git-sources/{id}` | → `null`                                         | 删除来源配置，不删除已经创建的草稿、Release 或 Binding。                            |

`url` 仅接受 HTTPS，禁止 SSH、本地路径、URL 内 user-info 和明文 token。私有仓库只提交已在密钥系统登记的 `secretRef`；前端不接收、缓存或展示凭据明文。

#### 预览与同步

1. `POST /api/agent/skills/git-sources/{id}/preview` → `GitSkillPreview`：解析 `ref` 为不可变 `commitSha`，扫描该来源的可导入包；**不写任何草稿**。
2. 用户勾选 `skillPath` 后提交 `POST /api/agent/skills/git-sources/{id}/sync`：

```json
{
  "expectedCommitSha": "4a38…",
  "skillPaths": ["skills/code-reviewer", "skills/release-notes"]
}
```

返回 `GitSkillSyncResult`，逐包给出 `CREATED`、`UNCHANGED`、`UPDATED`、`CONFLICT` 或 `FAILED` 及 `draftId` / 可展示的错误。`expectedCommitSha` 必须等于服务器重新解析的 HEAD；不相等时整个请求返回冲突，前端必须重新预览，不能盲目导入新内容。

同步仅生成 `DRAFT`：

- `MARKET` 来源：草稿仍需提交审核并通过，才会更新市场当前行。
- `PRIVATE` 来源：草稿归当前来源所有者，走既有私有发布与 Binding 链路，不进入市场。
- 同一来源、commit、包路径和 `contentHash` 重复同步返回 `UNCHANGED`。若来源草稿仍是 `DRAFT` / `REJECTED` 且内容仍等于上次导入 hash，可安全更新为该 commit 的内容；用户手工编辑过、审核中或发生同名活跃草稿冲突时返回 `CONFLICT`，不覆盖内容。
- Git 拉取、安全检查或源级资源限制失败时不创建任何草稿；一个已拉取包解析失败只标记该包 `FAILED`，其他被选中的合法包可独立创建草稿。

前端始终展示来源 URL 的脱敏形式、ref、解析 commit、最后成功时间与最后错误摘要；不得从错误体或返回对象推断或拼接凭据。

## 6. 把 Skill 绑到 Agent Revision

沿用现有 Agent 草稿接口，**不要**另开 Binding CRUD。

| 方法 | 路径                                  | 用途                                                                                                   |
| ---- | ------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| POST | `/api/agent/{definitionId}/revisions` | 创建草稿时可带 `skillBindings`                                                                         |
| PUT  | `/api/agent/revisions/{id}`           | 更新草稿；`skillBindings` 出现则 **整表替换**                                                          |
| GET  | `/api/agent/revisions/{id}`           | 返回 `skillBindings`                                                                                   |
| POST | `/api/agent/revisions/{id}/publish`   | 发布时校验 Binding：Release 必须 `PUBLISHED`、MARKET 须已安装、PRIVATE 须是所有者、同名须有唯一 winner |

绑定 item：

```json
{
  "skillReleaseId": 21,
  "overrideWinner": false
}
```

创建草稿示例：

```json
{
  "systemPrompt": "你是助手。",
  "skillBindings": [{ "skillReleaseId": 21, "overrideWinner": false }]
}
```

同名冲突（市场 + 私有都叫 `code-reviewer`）必须显式胜者：

```json
{
  "skillBindings": [
    { "skillReleaseId": 21, "overrideWinner": false },
    { "skillReleaseId": 22, "overrideWinner": true }
  ]
}
```

后端只持久化胜者那一行。GET Revision 时 `skillBindings` 每条还带只读 `skillName`、`contentHash`。

`PUT` 草稿时：

- **省略** `skillBindings`：绑定不变
- **传 `[]`**：清空该草稿全部 Skill
- **传非空数组**：按新列表替换

已发布 Revision 不能改绑定；要改组合只能开新草稿再发布。

## 7. TypeScript 类型（建议）

字段名与 JSON 一致。可放进 `apps/react-admin/src/api/rest/types.ts`（及 Vue 对应 types）。

```ts
export type SkillVisibility = "MARKET" | "PRIVATE";

export type SkillDraftStatus = "DRAFT" | "PENDING_REVIEW" | "REJECTED" | "CONSUMED";

export type SkillReleaseStatus = "PUBLISHED" | "DEPRECATED";

export interface SkillResource {
  path: string;
  content: string;
  contentHash: string;
}

export interface SkillDraft {
  id: number;
  name: string;
  description: string;
  skillContent: string;
  visibility: SkillVisibility;
  status: SkillDraftStatus;
  ownerUserId: number;
  basedOnReleaseId: number | null;
  contentHash: string;
  reviewComment: string;
  reviewedBy: number;
  reviewedAt: string | null;
  remark: string;
  resources: SkillResource[];
  createdAt: string;
  updatedAt: string;
}

export interface SkillRelease {
  id: number;
  name: string;
  version: number;
  description: string;
  skillContent: string;
  visibility: SkillVisibility;
  status: SkillReleaseStatus;
  ownerUserId: number;
  sourceDraftId: number | null;
  contentHash: string;
  source: string;
  remark: string;
  resources: SkillResource[];
  createdAt: string;
}

export interface SkillMarket {
  id: number;
  name: string;
  description: string;
  contentHash: string;
  currentReleaseId: number;
  source: string;
}

export interface SkillInstall {
  id: number;
  userId: number;
  skillName: string;
  visibility: SkillVisibility;
  ownerUserId: number;
  currentReleaseId: number;
}

export interface BindableSkill {
  skillReleaseId: number;
  name: string;
  visibility: SkillVisibility;
  ownerUserId: number;
  contentHash: string;
  version: number;
}

export interface SkillBinding {
  skillReleaseId: number;
  skillName: string;
  contentHash: string;
  overrideWinner: boolean;
}

export interface CreateSkillDraftRequest {
  name: string;
  description?: string;
  skillContent: string;
  visibility: SkillVisibility;
  resources?: Record<string, string>;
  basedOnReleaseId?: number | null;
  remark?: string;
}

export interface UpdateSkillDraftRequest {
  description?: string;
  skillContent?: string;
  resources?: Record<string, string>;
  remark?: string;
}

export type GitSkillSourceScope = "MARKET" | "PRIVATE";

export type GitSkillSourceStatus = "READY" | "SYNCING" | "FAILED";

export type GitSkillSyncItemStatus = "CREATED" | "UNCHANGED" | "UPDATED" | "CONFLICT" | "FAILED";

export interface GitSkillSource {
  id: number;
  scope: GitSkillSourceScope;
  ownerUserId: number;
  url: string; // 脱敏 URL；绝不含 user-info 或 token
  ref: string;
  subdirectory: string;
  hasSecretRef: boolean;
  lastCommitSha: string | null;
  lastSyncedAt: string | null;
  status: GitSkillSourceStatus;
  lastError: string;
  createdAt: string;
  updatedAt: string;
}

export interface GitSkillPreviewItem {
  skillPath: string;
  name: string;
  description: string;
  contentHash: string;
  resourceCount: number;
  totalBytes: number;
}

export interface GitSkillPreview {
  sourceId: number;
  commitSha: string;
  skills: GitSkillPreviewItem[];
}

export interface GitSkillSyncItem {
  skillPath: string;
  name: string | null;
  status: GitSkillSyncItemStatus;
  draftId: number | null;
  message: string;
}

export interface GitSkillSyncResult {
  sourceId: number;
  commitSha: string;
  results: GitSkillSyncItem[];
}

export interface CreateGitSkillSourceRequest {
  scope: GitSkillSourceScope;
  url: string;
  ref?: string;
  subdirectory?: string;
  secretRef?: string | null;
}

export interface UpdateGitSkillSourceRequest {
  url?: string;
  ref?: string;
  subdirectory?: string;
  secretRef?: string | null;
}

export interface SyncGitSkillSourceRequest {
  expectedCommitSha: string;
  skillPaths: string[];
}

export interface SkillBindingRequest {
  skillReleaseId: number;
  overrideWinner?: boolean;
}
```

现有 `AgentRevision` 类型需追加：

```ts
skillBindings?: SkillBinding[];
```

创建/更新 Agent 草稿的 request 追加可选 `skillBindings?: SkillBindingRequest[]`。

## 8. 建议的前端模块（尚未实现）

架构 9.3 已有对话页。Skill 管理是后续页，建议四块，都走真 API：

1. **我的草稿**：列表 + 编辑器（`SKILL.md` + 资源文件表）+ 提交审核。
2. **审核 / 市场**：待审列表（管理员/发布者调通过/驳回）；市场目录 + 安装/下架。
3. **Agent 草稿配置**：在现有创建/编辑 Revision 表单增加「绑定 Skill」多选，数据源 `GET /agent/skills/bindable`。
4. **Git 来源**：管理员在市场管理下维护 `MARKET` 来源；用户在「我的 Skill」下维护 `PRIVATE` 来源。新建/编辑使用来源表单，`secretRef` 使用凭据引用选择控件；预览以 commit 和包清单供用户勾选，同步结果逐项链接到草稿。禁止用浏览器 clone、裸 `fetch` 或 token 输入框替代这些接口。
   对话页（`/agent/chat`）**首期不必**加 Skill 选择器：会话使用已固定 Revision 的 Binding。

权限码已写入 `V4__agent_schema_seed.sql`（Root 通配仍可用）。非 Root 角色需在「角色-接口」里绑定，例如：

| permission_code                                                                                | 接口                                                 |
| ---------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| `skill:draft:create`                                                                           | POST `/api/agent/skills/drafts`                      |
| `skill:draft:list` / `skill:draft:read` / `skill:draft:update`                                 | 草稿读改                                             |
| `skill:draft:submit` / `withdraw` / `approve` / `reject`                                       | 审核流                                               |
| `skill:market:list` / `skill:market:unlist`                                                    | 市场                                                 |
| `skill:install:create` / `skill:install:delete`                                                | 安装                                                 |
| `skill:bindable:list`                                                                          | 可绑定                                               |
| `skill:release:read` / `skill:release:deprecate`                                               | Release                                              |
| 现有 `agent:revision:create` / `update` / `publish`                                            | 绑定随 Revision 走                                   |
| `skill:git-source:market:create` / `update` / `delete` / `list` / `read` / `preview` / `sync`  | 平台 MARKET Git 来源                                 |
| `skill:git-source:private:create` / `update` / `delete` / `list` / `read` / `preview` / `sync` | 自己的 PRIVATE Git 来源；服务端仍校验 owner 和 scope |

菜单尚未 seed。新页面要同时加 `sys_menu` + `sys_menu_api`（或走现有菜单管理 UI），不要只加前端路由。

## 9. 错误文案（可直接展示 `msg`）

| 场景                         | 典型 `msg`                                                                            |
| ---------------------------- | ------------------------------------------------------------------------------------- |
| 无 frontmatter / name 不一致 | `SKILL.md frontmatter is required` / `name must match SKILL.md frontmatter`           |
| 资源路径非法                 | `resource path must be a relative printable path`                                     |
| 同名活跃草稿已存在           | `an active draft already exists for this skill name`                                  |
| 审核中改内容                 | `only draft or rejected skills can be updated`                                        |
| 未提交就通过                 | `only pending review skills can be published`                                         |
| 未安装就绑定 MARKET          | `skill is not installed or owned by current user`                                     |
| 同名未声明覆盖               | `skill name conflict must declare exactly one overrideWinner: {name}`                 |
| 市场不存在                   | `market skill is not published` / `market skill not found`                            |
| Git URL / ref / 路径非法     | `git source URL must use HTTPS` / `git ref not found` / `git subdirectory is invalid` |
| 目标地址或资源限制被拒绝     | `git source target is not allowed` / `git source exceeds configured limit`            |
| 预览已过期                   | `git source HEAD changed; preview again before syncing`                               |
| 草稿不可安全更新             | `git sync conflicts with an active draft: {name}`                                     |

## 10. 推荐联调顺序

1. 用户创建 PRIVATE Git 来源，`preview` 返回固定 commit 和合法包；选包 `sync` 后仅创建 PRIVATE 草稿。
2. 用户提交并发布该草稿；`GET /agent/skills/bindable` 应出现 Release，`GET /agent/skills/market` **不应**出现。
3. 管理员创建 MARKET Git 来源；同步草稿经审核通过后，`GET /market` 能看到，用户可 `POST /install`。
4. 创建 Agent 草稿，`skillBindings` 只带 MARKET Release，发布 Revision，开新会话对话。
5. Git 来源更新到新 commit 后重新同步、审核并发布：市场 `currentReleaseId` 变了，步骤 4 的旧会话/旧 Revision 的 `contentHash` 不变。
6. 同名 MARKET + PRIVATE 一起写入 `skillBindings` 且两个 `overrideWinner=false` → 应失败；把私有设 `true` → 成功。
7. 预览后改变目标 ref 的 HEAD，再以旧 `expectedCommitSha` 同步 → 必须要求重新预览，且不建草稿。
8. `DELETE /agent/skills/market/{name}` 后市场列表为空，旧 Revision 仍可运行。

| 层                  | 路径                                                                         |
| ------------------- | ---------------------------------------------------------------------------- |
| Skill HTTP          | `backend/java-admin/java-admin-api/.../controller/AgentSkillController.java` |
| Revision 绑定       | 同模块 `AgentController` 的 `/revisions`                                     |
| 现有对话 API 客户端 | `apps/react-admin/src/api/rest/agent.ts`                                     |
| 领域规则            | [`agent-module-skill.md`](agent-module-skill.md)                             |
| 架构步骤            | [`agent-module-architecture.md`](agent-module-architecture.md) §9.4          |
