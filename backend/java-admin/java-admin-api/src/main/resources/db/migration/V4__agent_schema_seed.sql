-- Flyway V4: Agent 控制面 API Resource；Root 已由通配 Casbin policy 覆盖。
-- 非 Root 管理员可在既有角色-接口授权界面显式绑定这些资源。
INSERT INTO sys_api (name, method, path, permission_code, api_group, remark, is_enabled, deleted_at, created_by, updated_by)
VALUES
    ('创建 Agent', 'POST', '/api/agent', 'agent:create', 'Agent 管理', '', 1, 0, 0, 0),
    ('列出 Agent', 'GET', '/api/agent', 'agent:definition:list', 'Agent 管理', '', 1, 0, 0, 0),
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

INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, redirect, permission_code, tree_path, metadata, sort, is_hidden, is_enabled, deleted_at, remark, created_by, updated_by)
VALUES (500, NULL, 'Agent 对话', 'MENU', '/agent/chat', '/agent/chat/index', 'lucide:bot-message-square', '', 'agent:session:run', '/500/', '{"routeName":"AgentChat","order":2002,"fullPathKey":false}', 2002, 0, 1, 0, '', 0, 0);

INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 500);

INSERT INTO sys_menu_api (menu_id, api_id, created_by)
SELECT 500, id, 0
FROM sys_api
WHERE permission_code IN ('agent:definition:list', 'agent:definition:read', 'agent:session:create', 'agent:session:resolve', 'agent:session:run', 'agent:session:resume', 'agent:session:cancel', 'agent:session:read');

-- 默认通用 Agent：为本地联调和后续 Agent UI 测试提供可直接选择的已发布 Revision。
INSERT INTO agent_definition (id, name, description, owner_user_id, current_published_revision_id, remark, is_enabled, deleted_at, created_by, updated_by)
VALUES (1, '通用助手', '用于本地联调的默认通用 Agent', 1, NULL, '系统测试种子', 1, 0, 0, 0);

INSERT INTO agent_revision (id, agent_definition_id, status, source_draft_revision_id, system_prompt, model_config, permission_policy, memory_policy, compression_policy, remark, is_enabled, deleted_at, created_by, updated_by)
VALUES (1, 1, 'PUBLISHED', NULL, '你是 Trellis Admin 的通用助手。请准确、简洁地回答用户问题。', NULL, NULL, NULL, NULL, '系统测试种子', 1, 0, 0, 0);

UPDATE agent_definition SET current_published_revision_id = 1 WHERE id = 1;

ALTER TABLE agent_definition AUTO_INCREMENT = 100;
ALTER TABLE agent_revision AUTO_INCREMENT = 100;
