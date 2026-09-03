-- Flyway V3: Agent 平台 schema（四大模块控制面表）
-- 范围：Agent 管理 / Agent 对话（会话控制面）/ Skill 管理·市场 / MCP 管理·市场
-- 对齐：docs/agent-module-architecture.md、docs/agent-module-table-flows.md
-- 约定：InnoDB + utf8mb4_unicode_ci + BIGINT UNSIGNED 主键 + snake_case + 枚举 VARCHAR(32)
--       核心表含 7 审计字段 + deleted_at 软删；绑定表物理删除；子资源表物理删除。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 模块一：Agent 管理
-- ============================================================

-- Agent Definition：面向运营的稳定标识。
CREATE TABLE agent_definition (
    id                          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name                        VARCHAR(64)     NOT NULL                COMMENT '名称，软删感知唯一',
    description                 VARCHAR(512)    NOT NULL DEFAULT ''     COMMENT '描述',
    owner_user_id               BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '所有者（0=系统）',
    current_published_revision_id BIGINT UNSIGNED DEFAULT NULL          COMMENT '当前发布 Revision 指针（软引用 agent_revision.id）',
    remark                      VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled                  TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at                  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at                  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by                  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by                  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_definition_name (name, deleted_at),
    INDEX idx_agent_definition_owner (owner_user_id),
    INDEX idx_agent_definition_enabled (is_enabled),
    INDEX idx_agent_definition_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 定义';

-- Agent Revision：可编辑 DRAFT 或不可变 PUBLISHED 快照。
CREATE TABLE agent_revision (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    agent_definition_id     BIGINT UNSIGNED NOT NULL COMMENT '归属 Definition',
    status                  VARCHAR(32)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED',
    source_draft_revision_id BIGINT UNSIGNED DEFAULT NULL COMMENT '发布快照来源草稿（软引用 agent_revision.id）',
    system_prompt           MEDIUMTEXT      NULL COMMENT '系统提示词',
    model_config            JSON            NOT NULL COMMENT '模型配置 JSON 快照',
    permission_policy       JSON            NOT NULL COMMENT '权限策略 JSON 快照',
    memory_policy           JSON            NULL COMMENT '记忆策略 JSON 快照（可空）',
    compression_policy      JSON            NULL COMMENT '压缩策略 JSON 快照（可空）',
    remark                  VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled              TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at              BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by              BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by              BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_agent_revision_definition (agent_definition_id),
    INDEX idx_agent_revision_status (status),
    INDEX idx_agent_revision_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent Revision 草稿/发布快照';

-- ============================================================
-- 模块二：Agent 对话（会话控制面）
-- ============================================================

-- Agent Session：会话控制面元数据 + 固定 Revision（运行状态在 Redis）。
CREATE TABLE agent_session (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    agent_definition_id BIGINT UNSIGNED NOT NULL COMMENT '归属 Definition',
    agent_revision_id   BIGINT UNSIGNED DEFAULT NULL COMMENT '固定 Revision（首启前 NULL）',
    owner_user_id       BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '会话所有者',
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE',
    last_active_at      TIMESTAMP       NULL DEFAULT NULL COMMENT '最近活跃时间',
    remark              VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_agent_session_definition (agent_definition_id),
    INDEX idx_agent_session_revision (agent_revision_id),
    INDEX idx_agent_session_owner (owner_user_id),
    INDEX idx_agent_session_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 会话（控制面元数据）';

-- 会话级 Skill 绑定（用户侧追加/覆盖；同名覆盖 Revision）。
CREATE TABLE agent_session_skill_binding (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id       BIGINT UNSIGNED NOT NULL COMMENT '会话',
    skill_release_id BIGINT UNSIGNED NOT NULL COMMENT '绑定的不可变 Release',
    skill_name       VARCHAR(64)     NOT NULL COMMENT '从 Release 拷贝',
    content_hash     VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '运行漂移校验用',
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_session_skill_binding (session_id, skill_name),
    INDEX idx_agent_session_skill_release (skill_release_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话级 Skill 绑定';

-- 会话级 MCP 绑定（用户侧追加/覆盖；补配密钥）。
CREATE TABLE agent_session_mcp_binding (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id       BIGINT UNSIGNED NOT NULL COMMENT '会话',
    mcp_release_id   BIGINT UNSIGNED NOT NULL COMMENT '绑定的不可变 Release',
    mcp_name         VARCHAR(64)     NOT NULL COMMENT 'server 名',
    encrypted_secret TEXT            NULL COMMENT '会话层补配/覆盖的加密密钥密文',
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_session_mcp_binding (session_id, mcp_name),
    INDEX idx_agent_session_mcp_release (mcp_release_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话级 MCP 绑定';

-- ============================================================
-- 模块三：Skill 管理 / 市场
-- ============================================================

-- Skill 草稿。
CREATE TABLE agent_skill_draft (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name                VARCHAR(64)     NOT NULL COMMENT 'Skill 名',
    skill_content       MEDIUMTEXT      NOT NULL COMMENT '完整 SKILL.md',
    visibility          VARCHAR(32)     NOT NULL DEFAULT 'PRIVATE' COMMENT 'MARKET / PRIVATE',
    status              VARCHAR(32)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PENDING_REVIEW / REJECTED / CONSUMED',
    owner_user_id       BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '所有者',
    based_on_release_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '从既有 Release 开草稿时的来源',
    content_hash        VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '内容 hash',
    review_comment      VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '审核意见',
    reviewed_by         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '审核人',
    reviewed_at         TIMESTAMP       NULL DEFAULT NULL COMMENT '审核时间',
    remark              VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_draft (owner_user_id, name, visibility, deleted_at),
    INDEX idx_agent_skill_draft_status (status),
    INDEX idx_agent_skill_draft_visibility (visibility),
    INDEX idx_agent_skill_draft_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill 草稿';

-- Skill 草稿附属资源。
CREATE TABLE agent_skill_draft_resource (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    draft_id      BIGINT UNSIGNED NOT NULL COMMENT '归属草稿',
    resource_path VARCHAR(255)    NOT NULL COMMENT '相对路径，禁止 .. / 绝对路径 / 反斜杠',
    content       MEDIUMTEXT      NULL COMMENT '资源内容',
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_draft_resource (draft_id, resource_path),
    INDEX idx_agent_skill_draft_resource_draft (draft_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill 草稿附属资源';

-- Skill Release（不可变快照；市场列表由此派生）。
CREATE TABLE agent_skill_release (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_user_id   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '所有者',
    name            VARCHAR(64)     NOT NULL COMMENT 'Skill 名',
    visibility      VARCHAR(32)     NOT NULL COMMENT 'MARKET / PRIVATE',
    version         INT             NOT NULL COMMENT '在 (owner, visibility, name) 内从 1 递增',
    status          VARCHAR(32)     NOT NULL DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED / DEPRECATED',
    source_draft_id BIGINT UNSIGNED DEFAULT NULL COMMENT '来源草稿',
    skill_content   MEDIUMTEXT      NOT NULL COMMENT '冻结 SKILL.md',
    content_hash    VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '冻结内容 hash',
    remark          VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_release (owner_user_id, visibility, name, version, deleted_at),
    INDEX idx_agent_skill_release_market (visibility, status, name),
    INDEX idx_agent_skill_release_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill Release 不可变快照';

-- Skill Release 附属资源（冻结）。
CREATE TABLE agent_skill_release_resource (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    release_id    BIGINT UNSIGNED NOT NULL COMMENT '归属 Release',
    resource_path VARCHAR(255)    NOT NULL COMMENT '相对路径',
    content       MEDIUMTEXT      NULL COMMENT '资源内容',
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_release_resource (release_id, resource_path),
    INDEX idx_agent_skill_release_resource_release (release_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill Release 附属资源';

-- Revision 绑定的 Skill Release 快照指针。
CREATE TABLE agent_revision_skill_binding (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    agent_revision_id BIGINT UNSIGNED NOT NULL COMMENT '归属 Revision',
    skill_release_id  BIGINT UNSIGNED NOT NULL COMMENT '绑定的 Release 快照',
    skill_name        VARCHAR(64)     NOT NULL COMMENT '从 Release 拷贝',
    content_hash      VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '运行漂移校验用',
    override_winner   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '同名冲突时的胜者标记',
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by        BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_revision_skill_binding (agent_revision_id, skill_name),
    INDEX idx_agent_revision_skill_release (skill_release_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Revision Skill 绑定';

-- ============================================================
-- 模块四：MCP 管理 / 市场
-- ============================================================

-- MCP 草稿（连接配置草稿）。
CREATE TABLE agent_mcp_draft (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name               VARCHAR(64)     NOT NULL COMMENT 'server 名',
    transport          VARCHAR(32)     NOT NULL DEFAULT 'sse' COMMENT 'sse / http',
    url                VARCHAR(512)    NOT NULL COMMENT '连接地址',
    headers_json       JSON            NULL COMMENT '静态头（无密）',
    encrypted_secret   TEXT            NULL COMMENT '加密密钥密文（私有草稿可带）',
    connect_timeout_ms INT UNSIGNED    NOT NULL DEFAULT 5000 COMMENT '连接超时（毫秒）',
    visibility         VARCHAR(32)     NOT NULL DEFAULT 'PRIVATE' COMMENT 'MARKET / PRIVATE',
    status             VARCHAR(32)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PENDING_REVIEW / REJECTED / CONSUMED',
    owner_user_id      BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '所有者',
    review_comment     VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '审核意见',
    reviewed_by        BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '审核人',
    reviewed_at        TIMESTAMP       NULL DEFAULT NULL COMMENT '审核时间',
    remark             VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled         TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_mcp_draft (owner_user_id, name, visibility, deleted_at),
    INDEX idx_agent_mcp_draft_status (status),
    INDEX idx_agent_mcp_draft_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 草稿';

-- MCP Release（连接配置副本，工具目录不落库）。
CREATE TABLE agent_mcp_release (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_user_id      BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '所有者',
    name               VARCHAR(64)     NOT NULL COMMENT 'server 名',
    visibility         VARCHAR(32)     NOT NULL COMMENT 'MARKET / PRIVATE',
    version            INT             NOT NULL COMMENT '在 (owner, visibility, name) 内递增',
    status             VARCHAR(32)     NOT NULL DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED / DEPRECATED',
    source_draft_id    BIGINT UNSIGNED DEFAULT NULL COMMENT '来源草稿',
    transport          VARCHAR(32)     NOT NULL COMMENT 'sse / http',
    url                VARCHAR(512)    NOT NULL COMMENT '连接地址',
    headers_json       JSON            NULL COMMENT '静态头（无密）',
    encrypted_secret   TEXT            NULL COMMENT 'MARKET 无密钥，PRIVATE 带密钥',
    connect_timeout_ms INT UNSIGNED    NOT NULL DEFAULT 5000 COMMENT '连接超时（毫秒）',
    remark             VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled         TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_mcp_release (owner_user_id, visibility, name, version, deleted_at),
    INDEX idx_agent_mcp_release_market (visibility, status, name),
    INDEX idx_agent_mcp_release_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP Release 连接配置副本';

-- Revision 绑定的 MCP Release 指针 + 补配密钥。
CREATE TABLE agent_revision_mcp_binding (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    agent_revision_id BIGINT UNSIGNED NOT NULL COMMENT '归属 Revision',
    mcp_release_id    BIGINT UNSIGNED NOT NULL COMMENT '绑定的 Release',
    mcp_name          VARCHAR(64)     NOT NULL COMMENT 'server 名',
    encrypted_secret  TEXT            NULL COMMENT 'Agent 层补配/覆盖的加密密钥',
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by        BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_revision_mcp_binding (agent_revision_id, mcp_name),
    INDEX idx_agent_revision_mcp_release (mcp_release_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Revision MCP 绑定';

-- ============================================================
-- Git 受控导入（Skill）
-- ============================================================

-- Git Skill 来源配置。
CREATE TABLE agent_skill_git_source (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    scope             VARCHAR(32)     NOT NULL DEFAULT 'PRIVATE' COMMENT 'MARKET / PRIVATE',
    owner_user_id     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '来源所有者',
    url               VARCHAR(255)    NOT NULL COMMENT 'HTTPS 地址（脱敏展示）',
    ref               VARCHAR(128)    NOT NULL DEFAULT 'main' COMMENT '分支/标签/commit',
    subdirectory      VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '仓库子目录',
    encrypted_secret  TEXT            NULL COMMENT '加密密钥密文',
    last_commit_sha   VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '最近成功同步 commit',
    last_synced_at    TIMESTAMP       NULL DEFAULT NULL COMMENT '最近成功同步时间',
    status            VARCHAR(32)     NOT NULL DEFAULT 'READY' COMMENT 'READY / FAILED',
    last_error        VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '错误摘要',
    remark            VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled        TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at        BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by        BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by        BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_git_source (scope, owner_user_id, url, deleted_at),
    INDEX idx_agent_skill_git_source_status (status),
    INDEX idx_agent_skill_git_source_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Git Skill 来源配置';

-- Git 幂等同步记录。
CREATE TABLE agent_skill_git_sync (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    source_id    BIGINT UNSIGNED NOT NULL COMMENT '来源',
    commit_sha   VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '同步时 commit',
    skill_path   VARCHAR(255)    NOT NULL COMMENT '包路径',
    content_hash VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '导入内容 hash',
    draft_id     BIGINT UNSIGNED DEFAULT NULL COMMENT '对应草稿',
    created_at   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_git_sync (source_id, skill_path, deleted_at),
    INDEX idx_agent_skill_git_sync_draft (draft_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Git Skill 同步记录';

SET FOREIGN_KEY_CHECKS = 1;
