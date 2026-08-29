-- Flyway V4: Agent 控制面 API Resource；Root 已由通配 Casbin policy 覆盖。
-- 非 Root 管理员可在既有角色-接口授权界面显式绑定这些资源。
INSERT INTO sys_api (name, method, path, permission_code, api_group, remark, is_enabled, deleted_at, created_by, updated_by)
VALUES
    ('创建 Agent', 'POST', '/api/agent', 'agent:create', 'Agent 管理', '', 1, 0, 0, 0),
    ('获取 Agent', 'GET', '/api/agent/:id', 'agent:definition:read', 'Agent 管理', '', 1, 0, 0, 0),
    ('创建 Agent 草稿', 'POST', '/api/agent/:id/revisions', 'agent:revision:create', 'Agent 管理', '', 1, 0, 0, 0),
    ('获取 Agent Revision', 'GET', '/api/agent/revisions/:id', 'agent:revision:read', 'Agent 管理', '', 1, 0, 0, 0),
    ('更新 Agent 草稿', 'PUT', '/api/agent/revisions/:id', 'agent:revision:update', 'Agent 管理', '', 1, 0, 0, 0),
    ('发布 Agent Revision', 'POST', '/api/agent/revisions/:id/publish', 'agent:revision:publish', 'Agent 管理', '', 1, 0, 0, 0),
    ('回滚 Agent Revision', 'POST', '/api/agent/:id/rollback', 'agent:revision:rollback', 'Agent 管理', '', 1, 0, 0, 0),
    ('创建 Agent 会话', 'POST', '/api/agent/:id/sessions', 'agent:session:create', 'Agent 管理', '', 1, 0, 0, 0),
    ('固定 Agent 会话 Revision', 'POST', '/api/agent/sessions/:id/resolve-revision', 'agent:session:resolve', 'Agent 管理', '', 1, 0, 0, 0),
    ('紧急禁用 Agent', 'POST', '/api/agent/:id/emergency-disable', 'agent:emergency:disable', 'Agent 管理', '', 1, 0, 0, 0),
    ('运行 Agent 会话', 'POST', '/api/agent/sessions/:id/events', 'agent:session:run', 'Agent 管理', '', 1, 0, 0, 0),
    ('续接 Agent 会话事件', 'GET', '/api/agent/sessions/:id/events', 'agent:session:resume', 'Agent 管理', '', 1, 0, 0, 0),
    ('取消 Agent 会话运行', 'POST', '/api/agent/sessions/:id/cancel', 'agent:session:cancel', 'Agent 管理', '', 1, 0, 0, 0),
    ('获取 Agent 会话', 'GET', '/api/agent/sessions/:id', 'agent:session:read', 'Agent 管理', '', 1, 0, 0, 0);
