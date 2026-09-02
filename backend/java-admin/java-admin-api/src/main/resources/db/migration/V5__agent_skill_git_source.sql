-- Flyway V5: Git Skill 来源配置、同步幂等记录与 Agent Skill 管理菜单。

CREATE TABLE agent_skill_git_source (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    scope           VARCHAR(32)     NOT NULL,
    owner_user_id   BIGINT UNSIGNED NOT NULL,
    url             VARCHAR(2048)   NOT NULL,
    ref             VARCHAR(255)    NOT NULL DEFAULT 'HEAD',
    subdirectory    VARCHAR(500)    NOT NULL DEFAULT '',
    secret_ref      VARCHAR(255)    NOT NULL DEFAULT '',
    last_commit_sha VARCHAR(64)     DEFAULT NULL,
    last_synced_at  TIMESTAMP       NULL DEFAULT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'READY',
    last_error      VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_agent_skill_git_source_scope_owner (scope, owner_user_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='受控 Git Skill 来源；secret_ref 仅保存外部密钥系统引用';

CREATE TABLE agent_skill_git_sync (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    source_id    BIGINT UNSIGNED NOT NULL,
    commit_sha   VARCHAR(64)     NOT NULL,
    skill_path   VARCHAR(500)    NOT NULL,
    content_hash VARCHAR(64)     NOT NULL,
    draft_id     BIGINT UNSIGNED NOT NULL,
    is_enabled   TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_git_sync_source_path (source_id, skill_path, deleted_at),
    CONSTRAINT fk_agent_skill_git_sync_source
        FOREIGN KEY (source_id) REFERENCES agent_skill_git_source (id),
    CONSTRAINT fk_agent_skill_git_sync_draft
        FOREIGN KEY (draft_id) REFERENCES agent_skill_draft (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Git 包最近一次导入草稿，保护人工修改并支持幂等同步';

INSERT INTO sys_api (name, method, path, permission_code, api_group, remark, is_enabled, deleted_at, created_by, updated_by)
VALUES
    ('创建 Git Skill 来源', 'POST', '/api/agent/skills/git-sources', 'skill:git-source:create', 'Agent Skill', '', 1, 0, 0, 0),
    ('更新 Git Skill 来源', 'PUT', '/api/agent/skills/git-sources/:id', 'skill:git-source:update', 'Agent Skill', '', 1, 0, 0, 0),
    ('删除 Git Skill 来源', 'DELETE', '/api/agent/skills/git-sources/:id', 'skill:git-source:delete', 'Agent Skill', '', 1, 0, 0, 0),
    ('列出 Git Skill 来源', 'GET', '/api/agent/skills/git-sources', 'skill:git-source:list', 'Agent Skill', '', 1, 0, 0, 0),
    ('读取 Git Skill 来源', 'GET', '/api/agent/skills/git-sources/:id', 'skill:git-source:read', 'Agent Skill', '', 1, 0, 0, 0),
    ('预览 Git Skill 来源', 'POST', '/api/agent/skills/git-sources/:id/preview', 'skill:git-source:preview', 'Agent Skill', '', 1, 0, 0, 0),
    ('同步 Git Skill 来源', 'POST', '/api/agent/skills/git-sources/:id/sync', 'skill:git-source:sync', 'Agent Skill', '', 1, 0, 0, 0);

INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, redirect, permission_code, tree_path, metadata, sort, is_hidden, is_enabled, deleted_at, remark, created_by, updated_by)
VALUES
    (502, 500, 'Agent 管理', 'MENU', '/agent/manage', '/agent/manage/index', 'lucide:bot', '', 'agent:revision:create', '/500/502/', '{"routeName":"AgentManage","order":2,"fullPathKey":false}', 2, 0, 1, 0, '', 0, 0),
    (503, 500, '我的 Skill', 'MENU', '/agent/skill/drafts', '/agent/skill/drafts/index', 'lucide:package', '', 'skill:draft:list', '/500/503/', '{"routeName":"AgentSkillDrafts","order":3,"fullPathKey":false}', 3, 0, 1, 0, '', 0, 0),
    (504, 500, 'Skill 市场', 'MENU', '/agent/skill/market', '/agent/skill/market/index', 'lucide:store', '', 'skill:market:list', '/500/504/', '{"routeName":"AgentSkillMarket","order":4,"fullPathKey":false}', 4, 0, 1, 0, '', 0, 0),
    (505, 500, 'Git Skill 来源', 'MENU', '/agent/skill/git', '/agent/skill/git/index', 'lucide:git-branch', '', 'skill:git-source:list', '/500/505/', '{"routeName":"AgentSkillGit","order":5,"fullPathKey":false}', 5, 0, 1, 0, '', 0, 0);

INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 502), (1, 503), (1, 504), (1, 505);

INSERT INTO sys_menu_api (menu_id, api_id, created_by)
SELECT 502, id, 0 FROM sys_api WHERE permission_code IN ('agent:definition:list', 'agent:definition:read', 'agent:revision:create', 'agent:revision:read', 'agent:revision:update', 'agent:revision:publish', 'skill:bindable:list');
INSERT INTO sys_menu_api (menu_id, api_id, created_by)
SELECT 503, id, 0 FROM sys_api WHERE permission_code LIKE 'skill:draft:%' OR permission_code LIKE 'skill:release:%' OR permission_code LIKE 'skill:git-source:%';
INSERT INTO sys_menu_api (menu_id, api_id, created_by)
SELECT 504, id, 0 FROM sys_api WHERE permission_code LIKE 'skill:market:%' OR permission_code LIKE 'skill:install:%' OR permission_code LIKE 'skill:draft:%';
INSERT INTO sys_menu_api (menu_id, api_id, created_by)
SELECT 505, id, 0 FROM sys_api WHERE permission_code LIKE 'skill:git-source:%';
