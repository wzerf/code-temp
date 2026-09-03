-- Flyway V4: Agent 平台权限码 seed（sys_api）
-- 对齐：docs/agent-module-architecture.md §10 权限模型（skill:draft:* / mcp:draft:*）
-- 说明：仅注册接口资源（供 Casbin 授权与角色分配）；菜单与前端管理页后续实现。
--       Root 已持 Casbin 通配 p, 1, /*, *，无需额外 sys_role_api。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO sys_api (id, name, method, path, permission_code, api_group, remark, is_enabled, deleted_at, created_by, updated_by)
VALUES
    -- Agent 管理
    (1000, 'Agent 分页列表', 'GET', '/api/agent/list', 'agent:list', 'Agent 管理', '', 1, 0, 0, 0),
    (1001, 'Agent 全量列表', 'GET', '/api/agent/all', 'agent:list', 'Agent 管理', '', 1, 0, 0, 0),
    (1002, 'Agent 详情', 'GET', '/api/agent/:id', 'agent:list', 'Agent 管理', '', 1, 0, 0, 0),
    (1003, '创建 Agent', 'POST', '/api/agent', 'agent:create', 'Agent 管理', '', 1, 0, 0, 0),
    (1004, '更新 Agent', 'PUT', '/api/agent/:id', 'agent:update', 'Agent 管理', '', 1, 0, 0, 0),
    (1005, '删除 Agent', 'DELETE', '/api/agent/:id', 'agent:delete', 'Agent 管理', '', 1, 0, 0, 0),
    (1006, '紧急禁用 Agent', 'POST', '/api/agent/:id/disable', 'agent:disable', 'Agent 管理', '', 1, 0, 0, 0),
    (1007, '创建草稿 Revision', 'POST', '/api/agent/:id/revisions', 'agent:revision:create', 'Agent 管理', '', 1, 0, 0, 0),
    (1008, '更新草稿 Revision', 'PUT', '/api/agent/revisions/:id', 'agent:revision:update', 'Agent 管理', '', 1, 0, 0, 0),
    (1009, '发布 Revision', 'POST', '/api/agent/revisions/:id/publish', 'agent:publish', 'Agent 管理', '', 1, 0, 0, 0),
    (1010, '回滚 Agent', 'POST', '/api/agent/:id/rollback', 'agent:rollback', 'Agent 管理', '', 1, 0, 0, 0),
    (1011, 'Revision 列表', 'GET', '/api/agent/:id/revisions', 'agent:revision:list', 'Agent 管理', '', 1, 0, 0, 0),
    (1012, '查看 Revision 绑定', 'GET', '/api/agent/revisions/:id/bindings', 'agent:revision:list', 'Agent 管理', '', 1, 0, 0, 0),
    (1013, '设置 Revision 绑定', 'PUT', '/api/agent/revisions/:id/bindings', 'agent:revision:binding', 'Agent 管理', '', 1, 0, 0, 0),

    -- Skill 管理 / 市场
    (1100, 'Skill 草稿分页', 'GET', '/api/agent/skill/list', 'skill:draft:list', 'Skill 管理', '', 1, 0, 0, 0),
    (1101, 'Skill 草稿详情', 'GET', '/api/agent/skill/:id', 'skill:draft:list', 'Skill 管理', '', 1, 0, 0, 0),
    (1102, '创建 Skill 草稿', 'POST', '/api/agent/skill', 'skill:draft:create', 'Skill 管理', '', 1, 0, 0, 0),
    (1103, '更新 Skill 草稿', 'PUT', '/api/agent/skill/:id', 'skill:draft:update', 'Skill 管理', '', 1, 0, 0, 0),
    (1104, '删除 Skill 草稿', 'DELETE', '/api/agent/skill/:id', 'skill:draft:delete', 'Skill 管理', '', 1, 0, 0, 0),
    (1105, '提交 Skill 审核', 'POST', '/api/agent/skill/:id/submit', 'skill:draft:submit', 'Skill 管理', '', 1, 0, 0, 0),
    (1106, '审核 Skill', 'POST', '/api/agent/skill/:id/review', 'skill:draft:review', 'Skill 管理', '', 1, 0, 0, 0),
    (1107, 'Skill Release 列表', 'GET', '/api/agent/skill/release/list', 'skill:release:list', 'Skill 管理', '', 1, 0, 0, 0),
    (1108, 'Skill Release 详情', 'GET', '/api/agent/skill/release/:id', 'skill:release:list', 'Skill 管理', '', 1, 0, 0, 0),
    (1109, '弃用 Skill Release', 'POST', '/api/agent/skill/release/:id/deprecate', 'skill:release:deprecate', 'Skill 管理', '', 1, 0, 0, 0),
    (1110, 'Skill 市场列表', 'GET', '/api/agent/skill/market', 'skill:release:list', 'Skill 管理', '', 1, 0, 0, 0),
    (1111, '查看 Skill 草稿资源', 'GET', '/api/agent/skill/:id/resources', 'skill:draft:list', 'Skill 管理', '', 1, 0, 0, 0),
    (1112, '设置 Skill 草稿资源', 'PUT', '/api/agent/skill/:id/resources', 'skill:draft:update', 'Skill 管理', '', 1, 0, 0, 0),
    (1113, 'Git 来源列表', 'GET', '/api/agent/skill/git/source/list', 'skill:git:list', 'Skill 管理', '', 1, 0, 0, 0),
    (1114, '创建 Git 来源', 'POST', '/api/agent/skill/git/source', 'skill:git:create', 'Skill 管理', '', 1, 0, 0, 0),
    (1115, '删除 Git 来源', 'DELETE', '/api/agent/skill/git/source/:id', 'skill:git:delete', 'Skill 管理', '', 1, 0, 0, 0),
    (1116, 'Git 导入预览', 'POST', '/api/agent/skill/git/preview', 'skill:git:preview', 'Skill 管理', '', 1, 0, 0, 0),
    (1117, 'Git 导入同步', 'POST', '/api/agent/skill/git/sync', 'skill:git:sync', 'Skill 管理', '', 1, 0, 0, 0),

    -- MCP 管理 / 市场
    (1200, 'MCP 草稿分页', 'GET', '/api/agent/mcp/list', 'mcp:draft:list', 'MCP 管理', '', 1, 0, 0, 0),
    (1201, 'MCP 草稿详情', 'GET', '/api/agent/mcp/:id', 'mcp:draft:list', 'MCP 管理', '', 1, 0, 0, 0),
    (1202, '创建 MCP 草稿', 'POST', '/api/agent/mcp', 'mcp:draft:create', 'MCP 管理', '', 1, 0, 0, 0),
    (1203, '更新 MCP 草稿', 'PUT', '/api/agent/mcp/:id', 'mcp:draft:update', 'MCP 管理', '', 1, 0, 0, 0),
    (1204, '删除 MCP 草稿', 'DELETE', '/api/agent/mcp/:id', 'mcp:draft:delete', 'MCP 管理', '', 1, 0, 0, 0),
    (1205, '提交 MCP 审核', 'POST', '/api/agent/mcp/:id/submit', 'mcp:draft:submit', 'MCP 管理', '', 1, 0, 0, 0),
    (1206, '验证 MCP 连接', 'POST', '/api/agent/mcp/:id/verify', 'mcp:draft:verify', 'MCP 管理', '', 1, 0, 0, 0),
    (1207, '审核 MCP', 'POST', '/api/agent/mcp/:id/review', 'mcp:draft:review', 'MCP 管理', '', 1, 0, 0, 0),
    (1208, 'MCP Release 列表', 'GET', '/api/agent/mcp/release/list', 'mcp:release:list', 'MCP 管理', '', 1, 0, 0, 0),
    (1209, 'MCP Release 详情', 'GET', '/api/agent/mcp/release/:id', 'mcp:release:list', 'MCP 管理', '', 1, 0, 0, 0),
    (1210, '弃用 MCP Release', 'POST', '/api/agent/mcp/release/:id/deprecate', 'mcp:release:deprecate', 'MCP 管理', '', 1, 0, 0, 0),
    (1211, 'MCP 市场列表', 'GET', '/api/agent/mcp/market', 'mcp:release:list', 'MCP 管理', '', 1, 0, 0, 0);

ALTER TABLE sys_api AUTO_INCREMENT = 1300;

SET FOREIGN_KEY_CHECKS = 1;
