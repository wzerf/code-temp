-- ============================================================
-- Flyway V5: Agent 运行面 seed（SSE 事件流端点 sys_api + root 授权）
-- 对齐 docs/agent-module-architecture.md §5.3/§5.4 与
--      docs/agent-conversation-architecture.md §4.3
-- 运行面端点复用 Agent 会话域权限（agent:session:*）;不新增菜单
-- casbin_rule 由 adapter 管理,root 通配已在 V2;此处仅补 sys_api + sys_role_api
-- ============================================================

-- ============================================================
-- Section 1: sys_api（运行面 SSE;续 V4 id 182）
-- 说明: /events 路径命中 Encrypt/Sign 中间件 shouldBypass(后缀 /events),
--       仅受 Sa-Token + Casbin 保护;Casbin keyMatch2 对 :sessionId 通配
-- ============================================================

INSERT INTO sys_api (id, name, method, path, permission_code, api_group, remark, is_enabled, deleted_at, created_by, updated_by)
VALUES
    (182, '运行 Agent 会话(SSE)', 'POST', '/api/system/agent/sessions/:sessionId/events', 'agent:session:run__events', 'Agent 管理', 'SSE 事件流;首启固定 Revision', 1, 0, 0, 0);

-- ============================================================
-- Section 2: sys_role_api（root 全量;追加 182）
-- ============================================================

INSERT INTO sys_role_api (role_id, api_id) VALUES
    (1, 182);

-- ============================================================
-- Section 3: sys_menu_api 不新增（运行面非菜单页;入口在会话详情 Drawer 后续接对话页）
-- ============================================================
