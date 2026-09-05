-- ============================================================
-- Flyway V6: Agent 对话（运行面）菜单 + 历史回放端点 seed
-- 对齐 docs/agent-conversation-architecture.md
-- 1) sys_menu: 500 目录下新增「Agent 对话」菜单（component → 前端对话页）
-- 2) sys_api:  历史回放 GET 端点（183）
-- 3) sys_role_menu / sys_role_api: root 全量
-- 4) sys_menu_api: 对话菜单 ↔ 运行 SSE(182) + 历史回放(183)
-- ============================================================

-- ============================================================
-- Section 1: sys_menu（500 目录下追加 505「Agent 对话」）
-- 对齐已有菜单约定：type=MENU、component=/system/<page>/index 由前端 pageMap 匹配
-- ============================================================

INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, redirect, permission_code, tree_path, metadata, sort, is_hidden, is_enabled, deleted_at, remark, created_by, updated_by)
VALUES
    (505, 500, 'agent.conversation.title', 'MENU', '/agent-platform/conversation', '/system/agent/conversation/index', 'lucide:message-square-text', '', 'agent:session:run__events', '/500/505/', '{"routeName":"AgentConversation","order":5}', 5, 0, 1, 0, 'Agent 对话运行面:AG-UI 流式对话/会话装配/HITL', 0, 0);

-- ============================================================
-- Section 2: sys_api（历史回放;续 V5 id 182 → 183）
-- 说明: 该 GET 不以 /events 结尾,走常规 Encrypt/Sign + Sa-Token + Casbin
-- ============================================================

INSERT INTO sys_api (id, name, method, path, permission_code, api_group, remark, is_enabled, deleted_at, created_by, updated_by)
VALUES
    (183, 'Agent 会话事件历史回放', 'GET', '/api/system/agent/sessions/:sessionId/events/history', 'agent:session:history', 'Agent 管理', '按序返回会话持久化 AG-UI 事件 JSON 列表', 1, 0, 0, 0);

-- ============================================================
-- Section 3: sys_role_menu（root 追加 505）
-- ============================================================

INSERT INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 505);

-- ============================================================
-- Section 4: sys_role_api（root 追加 183）
-- ============================================================

INSERT INTO sys_role_api (role_id, api_id) VALUES
    (1, 183);

-- ============================================================
-- Section 5: sys_menu_api（对话菜单 ↔ 运行 SSE(182) + 历史回放(183)）
-- ============================================================

INSERT INTO sys_menu_api (menu_id, api_id, created_by) VALUES
    (505, 182, 0),
    (505, 183, 0);
