-- ============================================================
-- Flyway V4: Agent 平台 seed（sys_api / sys_menu / role / 授权）
-- 对齐 docs/agent-module-architecture.md: 权限码形如 agent:*, skill:*, mcp:*
-- 模块: Agent 管理 / Skill 管理 / MCP 管理（含市场;页面由 Release 派生,不另建市场表）
-- 约定:
--   sys_api 固定 id 续 90+;sys_menu 用独立段 500 系;root(1) 全量 role_menu/role_api
--   sys_menu_api 只绑「MENU ↔ 分页接口」快捷绑定
--   casbin_rule 由 adapter 管理,root 通配已在 V2;不在此重复插入
-- ============================================================

-- ============================================================
-- Section 1: sys_api（Agent/Skill/MCP 控制面接口;续 V2 id 90 之后）
-- api_group 分段: agent:list..=Agent 管理 / skill:..=Skill 管理 / mcp:..=MCP 管理
-- 命名: <域>:<资源>:<动作>;同码复用路径用 __<slug> 消歧（对齐 V2 惯例）
-- ============================================================

INSERT INTO sys_api (id, name, method, path, permission_code, api_group, remark, is_enabled, deleted_at, created_by, updated_by)
VALUES
    -- Agent 管理（定义 + Revision + 发布/回滚/禁用）
    (90,  'Agent 定义分页',       'GET',    '/api/system/agent/list',               'agent:definition:list',     'Agent 管理', '', 1, 0, 0, 0),
    (91,  'Agent 定义全量',       'GET',    '/api/system/agent/all',                'agent:definition:list__system_agent_all', 'Agent 管理', '', 1, 0, 0, 0),
    (92,  'Agent 定义详情',       'GET',    '/api/system/agent/:id',                'agent:definition:list__system_agent__id', 'Agent 管理', '', 1, 0, 0, 0),
    (93,  '创建 Agent 定义',      'POST',   '/api/system/agent',                    'agent:definition:create',   'Agent 管理', '', 1, 0, 0, 0),
    (94,  '更新 Agent 定义',      'PUT',    '/api/system/agent/:id',                'agent:definition:update',   'Agent 管理', '', 1, 0, 0, 0),
    (95,  '删除 Agent 定义',      'DELETE', '/api/system/agent/:id',                'agent:definition:delete',   'Agent 管理', '', 1, 0, 0, 0),
    (96,  'Agent Revision 分页',  'GET',    '/api/system/agent/:id/revisions',      'agent:revision:list',       'Agent 管理', '', 1, 0, 0, 0),
    (97,  '创建 Agent Revision',  'POST',   '/api/system/agent/:id/revisions',      'agent:revision:create',     'Agent 管理', '', 1, 0, 0, 0),
    (98,  '更新 Agent Revision',  'PUT',    '/api/system/agent/revisions/:revisionId', 'agent:revision:update',   'Agent 管理', '', 1, 0, 0, 0),
    (99,  '删除 Agent Revision',  'DELETE', '/api/system/agent/revisions/:revisionId', 'agent:revision:delete',   'Agent 管理', '', 1, 0, 0, 0),
    (100, '发布 Agent Revision',  'POST',   '/api/system/agent/revisions/:revisionId/publish', 'agent:revision:publish', 'Agent 管理', '', 1, 0, 0, 0),
    (101, '回滚 Agent Revision',  'POST',   '/api/system/agent/:id/rollback',       'agent:revision:rollback',   'Agent 管理', '', 1, 0, 0, 0),
    (102, '紧急禁用 Agent',       'POST',   '/api/system/agent/:id/disable',        'agent:definition:disable',  'Agent 管理', '', 1, 0, 0, 0),
    (103, '启用 Agent',           'POST',   '/api/system/agent/:id/enable',         'agent:definition:enable',   'Agent 管理', '', 1, 0, 0, 0),
    -- Revision Skill/MCP 绑定
    (104, 'Revision Skill 绑定列表', 'GET', '/api/system/agent/revisions/:revisionId/skill-bindings', 'agent:revision:bind', 'Agent 管理', '', 1, 0, 0, 0),
    (105, '绑定 Skill 到 Revision',  'POST', '/api/system/agent/revisions/:revisionId/skill-bindings', 'agent:revision:bind__skill', 'Agent 管理', '', 1, 0, 0, 0),
    (106, '解除 Revision Skill 绑定', 'DELETE', '/api/system/agent/revisions/:revisionId/skill-bindings/:bindingId', 'agent:revision:bind__unbind-skill', 'Agent 管理', '', 1, 0, 0, 0),
    (107, 'Revision MCP 绑定列表', 'GET', '/api/system/agent/revisions/:revisionId/mcp-bindings', 'agent:revision:bind__mcp-list', 'Agent 管理', '', 1, 0, 0, 0),
    (108, '绑定 MCP 到 Revision',  'POST', '/api/system/agent/revisions/:revisionId/mcp-bindings', 'agent:revision:bind__mcp', 'Agent 管理', '', 1, 0, 0, 0),
    (109, '解除 Revision MCP 绑定', 'DELETE', '/api/system/agent/revisions/:revisionId/mcp-bindings/:bindingId', 'agent:revision:bind__unbind-mcp', 'Agent 管理', '', 1, 0, 0, 0),

    -- Agent 会话（控制面;本期不含运行面 SSE）
    (110, 'Agent 会话分页', 'GET', '/api/system/agent/:id/sessions', 'agent:session:list', 'Agent 管理', '', 1, 0, 0, 0),
    (111, '创建 Agent 会话', 'POST', '/api/system/agent/:id/sessions', 'agent:session:create', 'Agent 管理', '', 1, 0, 0, 0),
    (112, 'Agent 会话详情', 'GET', '/api/system/agent/sessions/:sessionId', 'agent:session:list__session', 'Agent 管理', '', 1, 0, 0, 0),
    (113, '删除 Agent 会话', 'DELETE', '/api/system/agent/sessions/:sessionId', 'agent:session:delete', 'Agent 管理', '', 1, 0, 0, 0),
    -- Session Skill/MCP 绑定（用户侧追加/覆盖）
    (114, 'Session Skill 绑定列表', 'GET', '/api/system/agent/sessions/:sessionId/skill-bindings', 'agent:session:bind__skill-list', 'Agent 管理', '', 1, 0, 0, 0),
    (115, '绑定 Skill 到 Session',  'POST', '/api/system/agent/sessions/:sessionId/skill-bindings', 'agent:session:bind__skill', 'Agent 管理', '', 1, 0, 0, 0),
    (116, '解除 Session Skill 绑定', 'DELETE', '/api/system/agent/sessions/:sessionId/skill-bindings/:bindingId', 'agent:session:bind__unbind-skill', 'Agent 管理', '', 1, 0, 0, 0),
    (117, 'Session MCP 绑定列表', 'GET', '/api/system/agent/sessions/:sessionId/mcp-bindings', 'agent:session:bind__mcp-list', 'Agent 管理', '', 1, 0, 0, 0),
    (118, '绑定 MCP 到 Session',  'POST', '/api/system/agent/sessions/:sessionId/mcp-bindings', 'agent:session:bind__mcp', 'Agent 管理', '', 1, 0, 0, 0),
    (119, '解除 Session MCP 绑定', 'DELETE', '/api/system/agent/sessions/:sessionId/mcp-bindings/:bindingId', 'agent:session:bind__unbind-mcp', 'Agent 管理', '', 1, 0, 0, 0),

    -- Skill 草稿（草稿→提交→审核）
    (120, 'Skill 草稿分页', 'GET', '/api/system/skill/draft/list', 'skill:draft:list', 'Skill 管理', '', 1, 0, 0, 0),
    (121, 'Skill 草稿全量', 'GET', '/api/system/skill/draft/all', 'skill:draft:list__skill_draft_all', 'Skill 管理', '', 1, 0, 0, 0),
    (122, 'Skill 草稿详情', 'GET', '/api/system/skill/draft/:id', 'skill:draft:list__skill_draft__id', 'Skill 管理', '', 1, 0, 0, 0),
    (123, '创建 Skill 草稿', 'POST', '/api/system/skill/draft', 'skill:draft:create', 'Skill 管理', '', 1, 0, 0, 0),
    (124, '更新 Skill 草稿', 'PUT', '/api/system/skill/draft/:id', 'skill:draft:update', 'Skill 管理', '', 1, 0, 0, 0),
    (125, '删除 Skill 草稿', 'DELETE', '/api/system/skill/draft/:id', 'skill:draft:delete', 'Skill 管理', '', 1, 0, 0, 0),
    (126, '提交 Skill 草稿审核', 'POST', '/api/system/skill/draft/:id/submit', 'skill:draft:submit', 'Skill 管理', '', 1, 0, 0, 0),
    (127, '撤回 Skill 草稿', 'POST', '/api/system/skill/draft/:id/withdraw', 'skill:draft:withdraw', 'Skill 管理', '', 1, 0, 0, 0),
    (128, '通过 Skill 审核', 'POST', '/api/system/skill/draft/:id/approve', 'skill:draft:approve', 'Skill 管理', '', 1, 0, 0, 0),
    (129, '驳回 Skill 草稿', 'POST', '/api/system/skill/draft/:id/reject', 'skill:draft:reject', 'Skill 管理', '', 1, 0, 0, 0),
    -- Skill 草稿资源
    (130, 'Skill 草稿资源列表', 'GET', '/api/system/skill/draft/:id/resources', 'skill:draft:list__resources', 'Skill 管理', '', 1, 0, 0, 0),
    (131, '保存 Skill 草稿资源', 'PUT', '/api/system/skill/draft/:id/resources', 'skill:draft:update__resources', 'Skill 管理', '', 1, 0, 0, 0),
    -- Skill Release / 市场
    (132, 'Skill Release 分页', 'GET', '/api/system/skill/release/list', 'skill:release:list', 'Skill 管理', '', 1, 0, 0, 0),
    (133, 'Skill Release 详情', 'GET', '/api/system/skill/release/:id', 'skill:release:list__skill_release__id', 'Skill 管理', '', 1, 0, 0, 0),
    (134, 'Skill 市场列表', 'GET', '/api/system/skill/market', 'skill:market:list', 'Skill 管理', '', 1, 0, 0, 0),
    (135, 'Skill 市场下架', 'POST', '/api/system/skill/market/:id/take-down', 'skill:market:take-down', 'Skill 管理', '', 1, 0, 0, 0),
    (136, '弃用 Skill Release', 'POST', '/api/system/skill/release/:id/deprecate', 'skill:release:deprecate', 'Skill 管理', '', 1, 0, 0, 0),

    -- MCP 草稿
    (137, 'MCP 草稿分页', 'GET', '/api/system/mcp/draft/list', 'mcp:draft:list', 'MCP 管理', '', 1, 0, 0, 0),
    (138, 'MCP 草稿全量', 'GET', '/api/system/mcp/draft/all', 'mcp:draft:list__mcp_draft_all', 'MCP 管理', '', 1, 0, 0, 0),
    (139, 'MCP 草稿详情', 'GET', '/api/system/mcp/draft/:id', 'mcp:draft:list__mcp_draft__id', 'MCP 管理', '', 1, 0, 0, 0),
    (140, '创建 MCP 草稿', 'POST', '/api/system/mcp/draft', 'mcp:draft:create', 'MCP 管理', '', 1, 0, 0, 0),
    (141, '更新 MCP 草稿', 'PUT', '/api/system/mcp/draft/:id', 'mcp:draft:update', 'MCP 管理', '', 1, 0, 0, 0),
    (142, '删除 MCP 草稿', 'DELETE', '/api/system/mcp/draft/:id', 'mcp:draft:delete', 'MCP 管理', '', 1, 0, 0, 0),
    (143, 'MCP 握手验证', 'POST', '/api/system/mcp/draft/:id/verify', 'mcp:draft:verify', 'MCP 管理', '', 1, 0, 0, 0),
    (144, '提交 MCP 审核', 'POST', '/api/system/mcp/draft/:id/submit', 'mcp:draft:submit', 'MCP 管理', '', 1, 0, 0, 0),
    (145, '撤回 MCP 草稿', 'POST', '/api/system/mcp/draft/:id/withdraw', 'mcp:draft:withdraw', 'MCP 管理', '', 1, 0, 0, 0),
    (146, '通过 MCP 审核', 'POST', '/api/system/mcp/draft/:id/approve', 'mcp:draft:approve', 'MCP 管理', '', 1, 0, 0, 0),
    (147, '驳回 MCP 草稿', 'POST', '/api/system/mcp/draft/:id/reject', 'mcp:draft:reject', 'MCP 管理', '', 1, 0, 0, 0),
    -- MCP Release / 市场
    (148, 'MCP Release 分页', 'GET', '/api/system/mcp/release/list', 'mcp:release:list', 'MCP 管理', '', 1, 0, 0, 0),
    (149, 'MCP Release 详情', 'GET', '/api/system/mcp/release/:id', 'mcp:release:list__mcp_release__id', 'MCP 管理', '', 1, 0, 0, 0),
    (150, 'MCP 市场列表', 'GET', '/api/system/mcp/market', 'mcp:market:list', 'MCP 管理', '', 1, 0, 0, 0),
    (151, 'MCP 市场下架', 'POST', '/api/system/mcp/market/:id/take-down', 'mcp:market:take-down', 'MCP 管理', '', 1, 0, 0, 0),
    (152, '弃用 MCP Release', 'POST', '/api/system/mcp/release/:id/deprecate', 'mcp:release:deprecate', 'MCP 管理', '', 1, 0, 0, 0),
    (153, 'MCP 可绑定候选', 'GET', '/api/system/mcp/bindable', 'mcp:release:list__bindable', 'MCP 管理', '', 1, 0, 0, 0),
    (154, 'Skill 可绑定候选', 'GET', '/api/system/skill/release/bindable', 'skill:release:list__bindable', 'Skill 管理', '', 1, 0, 0, 0),
    (155, 'Agent 活跃草稿', 'GET', '/api/system/agent/:id/revisions/active-draft', 'agent:revision:list__active-draft', 'Agent 管理', '', 1, 0, 0, 0),
    -- Skill Git 受控导入
    (156, 'Git 来源列表', 'GET', '/api/system/skill/git-source/list', 'skill:git:list', 'Skill 管理', '', 1, 0, 0, 0),
    (157, '创建 Git 来源', 'POST', '/api/system/skill/git-source', 'skill:git:create', 'Skill 管理', '', 1, 0, 0, 0),
    (158, '更新 Git 来源', 'PUT', '/api/system/skill/git-source/:id', 'skill:git:update', 'Skill 管理', '', 1, 0, 0, 0),
    (159, '删除 Git 来源', 'DELETE', '/api/system/skill/git-source/:id', 'skill:git:delete', 'Skill 管理', '', 1, 0, 0, 0),
    (160, 'Git 来源预览', 'POST', '/api/system/skill/git-source/:id/preview', 'skill:git:preview', 'Skill 管理', '', 1, 0, 0, 0),
    (161, 'Git 来源同步', 'POST', '/api/system/skill/git-source/:id/sync', 'skill:git:sync', 'Skill 管理', '', 1, 0, 0, 0);

ALTER TABLE sys_api AUTO_INCREMENT = 200;

-- ============================================================
-- Section 2: sys_menu（Agent 平台 500 段;500 系目录 + 子菜单 + 按钮）
-- 布局: 500 Agent平台(DIR) → 501 Agent管理 / 502 Skill管理 / 503 MCP管理
-- ============================================================

INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, redirect, permission_code, tree_path, metadata, sort, is_hidden, is_enabled, deleted_at, remark, created_by, updated_by)
VALUES
    (500, NULL, 'agent.title', 'DIR', '/agent-platform', NULL, 'lucide:bot', '/agent-platform/agent', NULL, '/500/', '{"routeName":"AgentPlatform","order":2006}', 2006, 0, 1, 0, '', 0, 0),
    -- Agent 管理
    (501, 500, 'agent.agent.title', 'MENU', '/agent-platform/agent', '/system/agent/index', 'lucide:bot', '', 'agent:definition:list', '/500/501/', '{"routeName":"SystemAgent","order":1}', 1, 0, 1, 0, '', 0, 0),
    (5011, 501, '新建 Agent', 'BUTTON', NULL, NULL, '', '', 'agent:definition:create', '/500/501/5011/', NULL, 1, 0, 1, 0, '', 0, 0),
    (5012, 501, '发布 Agent', 'BUTTON', NULL, NULL, '', '', 'agent:revision:publish', '/500/501/5012/', NULL, 2, 0, 1, 0, '', 0, 0),
    (5013, 501, '绑定 Skill/MCP', 'BUTTON', NULL, NULL, '', '', 'agent:revision:bind', '/500/501/5013/', NULL, 3, 0, 1, 0, '', 0, 0),
    (5014, 501, '紧急禁用', 'BUTTON', NULL, NULL, '', '', 'agent:definition:disable', '/500/501/5014/', NULL, 4, 0, 1, 0, '', 0, 0),
    -- Skill 管理/市场
    (502, 500, 'agent.skill.title', 'MENU', '/agent-platform/skill', '/system/skill/index', 'lucide:package', '', 'skill:draft:list', '/500/502/', '{"routeName":"SystemSkill","order":2}', 2, 0, 1, 0, '', 0, 0),
    (5021, 502, '新建 Skill 草稿', 'BUTTON', NULL, NULL, '', '', 'skill:draft:create', '/500/502/5021/', NULL, 1, 0, 1, 0, '', 0, 0),
    (5022, 502, '审核 Skill', 'BUTTON', NULL, NULL, '', '', 'skill:draft:approve', '/500/502/5022/', NULL, 2, 0, 1, 0, '', 0, 0),
    (5023, 502, '市场下架', 'BUTTON', NULL, NULL, '', '', 'skill:market:take-down', '/500/502/5023/', NULL, 3, 0, 1, 0, '', 0, 0),
    (5024, 502, 'Git 来源', 'BUTTON', NULL, NULL, '', '', 'skill:git:list', '/500/502/5024/', NULL, 4, 0, 1, 0, '', 0, 0),
    -- MCP 管理/市场
    (503, 500, 'agent.mcp.title', 'MENU', '/agent-platform/mcp', '/system/mcp/index', 'lucide:plug', '', 'mcp:draft:list', '/500/503/', '{"routeName":"SystemMcp","order":3}', 3, 0, 1, 0, '', 0, 0),
    (5031, 503, '新建 MCP 草稿', 'BUTTON', NULL, NULL, '', '', 'mcp:draft:create', '/500/503/5031/', NULL, 1, 0, 1, 0, '', 0, 0),
    (5032, 503, '审核 MCP', 'BUTTON', NULL, NULL, '', '', 'mcp:draft:approve', '/500/503/5032/', NULL, 2, 0, 1, 0, '', 0, 0),
    (5033, 503, 'MCP 握手验证', 'BUTTON', NULL, NULL, '', '', 'mcp:draft:verify', '/500/503/5033/', NULL, 3, 0, 1, 0, '', 0, 0),
    (5034, 503, '市场下架', 'BUTTON', NULL, NULL, '', '', 'mcp:market:take-down', '/500/503/5034/', NULL, 4, 0, 1, 0, '', 0, 0);

ALTER TABLE sys_menu AUTO_INCREMENT = 2000;

-- ============================================================
-- Section 3: sys_role_menu（root 全量;追加 500 段全部菜单/按钮）
-- ============================================================

INSERT INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 500),
    (1, 501),
    (1, 5011),
    (1, 5012),
    (1, 5013),
    (1, 5014),
    (1, 502),
    (1, 5021),
    (1, 5022),
    (1, 5023),
    (1, 5024),
    (1, 503),
    (1, 5031),
    (1, 5032),
    (1, 5033),
    (1, 5034);

-- ============================================================
-- Section 4: sys_role_api（root 全量;追加 90..161）
-- ============================================================

INSERT INTO sys_role_api (role_id, api_id) VALUES
    (1, 90), (1, 91), (1, 92), (1, 93), (1, 94), (1, 95),
    (1, 96), (1, 97), (1, 98), (1, 99), (1, 100), (1, 101), (1, 102), (1, 103),
    (1, 104), (1, 105), (1, 106), (1, 107), (1, 108), (1, 109),
    (1, 110), (1, 111), (1, 112), (1, 113),
    (1, 114), (1, 115), (1, 116), (1, 117), (1, 118), (1, 119),
    (1, 120), (1, 121), (1, 122), (1, 123), (1, 124), (1, 125),
    (1, 126), (1, 127), (1, 128), (1, 129), (1, 130), (1, 131),
    (1, 132), (1, 133), (1, 134), (1, 135), (1, 136),
    (1, 137), (1, 138), (1, 139), (1, 140), (1, 141), (1, 142),
    (1, 143), (1, 144), (1, 145), (1, 146), (1, 147),
    (1, 148), (1, 149), (1, 150), (1, 151), (1, 152),
    (1, 153), (1, 154), (1, 155),
    (1, 156), (1, 157), (1, 158), (1, 159), (1, 160), (1, 161);

-- ============================================================
-- Section 5: sys_menu_api（菜单 ↔ 分页接口快捷绑定）
-- ============================================================

INSERT INTO sys_menu_api (menu_id, api_id, created_by) VALUES
    (501, 90, 0),
    (502, 120, 0),
    (503, 137, 0);
