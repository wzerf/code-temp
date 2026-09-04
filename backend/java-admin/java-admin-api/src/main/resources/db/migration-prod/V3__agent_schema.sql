-- ============================================================
-- Flyway V3: Agent 平台 schema（对齐 docs/agent-module-architecture.md / agent-module-table-flows.md）
-- 版本: v20（权威基线 backend/db/schema.sql 同步）
-- 模块: Agent 管理 / Skill 管理 / MCP 管理 / 会话控制面（agent 对话运行面状态不落库,存 Redis,本期不建运行面代码）
-- 字符集: utf8mb4 / utf8mb4_unicode_ci（双版本安全;prod 不用 utf8mb4_0900_ai_ci）
-- 引擎: InnoDB
-- 约定: 遵循 backend/db/docs/db-conventions.md
--   核心实体表含 7 审计尾: remark/is_enabled/deleted_at/created_at/updated_at/created_by/updated_by
--   软删感知唯一: UNIQUE(col..., deleted_at)
--   关联/绑定表: 无软删/无 7 件套,物理删除语义,仅 created_at(+created_by)
--   枚举 VARCHAR(32);时间 TIMESTAMP;布尔 TINYINT(1);JSON DEFAULT NULL;长文本 TEXT NOT NULL DEFAULT ''
--   外键: 仅强一致父子关系建 FK;owner_user_id/审计字段软引用不建 FK
-- ============================================================

-- ============================================================
-- Section A1: Agent 定义与 Revision
-- ============================================================

-- Agent Definition: 面向运营的稳定标识,承载名称/归属/启停/当前发布 Revision 指针
CREATE TABLE agent_definition (
    id                              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name                            VARCHAR(128)    NOT NULL  COMMENT '名称(软删感知唯一)',
    description                     VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '描述',
    owner_user_id                   BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '所有者(软引用 sys_user.id;0=平台)',
    current_published_revision_id   BIGINT UNSIGNED DEFAULT NULL  COMMENT '当前发布 Revision 指针(首次发布前为 NULL;软引用 agent_revision.id 不建 FK)',
    remark                          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '备注',
    is_enabled                      TINYINT(1)      NOT NULL DEFAULT 1  COMMENT '启停(紧急禁用=0,只阻止新会话/首启)',
    deleted_at                      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at                      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by                      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by                      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_definition_name (name, deleted_at),
    INDEX idx_agent_definition_owner (owner_user_id),
    INDEX idx_agent_definition_is_enabled (is_enabled),
    INDEX idx_agent_definition_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent 定义(稳定标识 + 当前发布 Revision 指针)';

-- Agent Revision: 可编辑 DRAFT 或不可变 PUBLISHED 快照
CREATE TABLE agent_revision (
    id                          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    agent_definition_id         BIGINT UNSIGNED NOT NULL  COMMENT '归属 Definition(FK)',
    status                      VARCHAR(32)     NOT NULL DEFAULT 'DRAFT'  COMMENT 'DRAFT=草稿/PUBLISHED=已发布不可变',
    source_draft_revision_id    BIGINT UNSIGNED DEFAULT NULL  COMMENT '发布快照来源草稿 Revision id(仅 PUBLISHED 行有;软引用不建 FK)',
    system_prompt               TEXT            NOT NULL  COMMENT '系统提示词快照(发布后冻结)',
    model_config                JSON            DEFAULT NULL  COMMENT '模型配置 JSON 快照(发布后冻结)',
    permission_policy           JSON            DEFAULT NULL  COMMENT '运行时权限策略 JSON(permission_policy.allowedTools 白名单)',
    memory_policy               JSON            DEFAULT NULL  COMMENT '记忆策略(首期非空即拒绝运行)',
    compression_policy          JSON            DEFAULT NULL  COMMENT '压缩策略(首期非空即拒绝运行)',
    remark                      VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '备注',
    is_enabled                  TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at                  BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at                  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by                  BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by                  BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    INDEX idx_agent_revision_definition (agent_definition_id),
    INDEX idx_agent_revision_definition_status (agent_definition_id, status),
    INDEX idx_agent_revision_status (status),
    INDEX idx_agent_revision_deleted_at (deleted_at),
    CONSTRAINT fk_agent_revision_definition FOREIGN KEY (agent_definition_id) REFERENCES agent_definition (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent Revision(草稿/不可变发布快照)';

-- ============================================================
-- Section A2: Agent 会话（控制面元数据;运行状态在 Redis）
-- ============================================================

CREATE TABLE agent_session (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    agent_definition_id BIGINT UNSIGNED NOT NULL  COMMENT '归属 Definition(FK)',
    agent_revision_id   BIGINT UNSIGNED DEFAULT NULL  COMMENT '固定 Revision(首启前 NULL;bindSessionRevision 写入;软引用不建 FK)',
    owner_user_id       BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '会话所有者(软引用 sys_user.id)',
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE'  COMMENT 'ACTIVE=活跃',
    last_active_at      TIMESTAMP       NULL DEFAULT NULL  COMMENT '最近活跃时间(运行面更新;本期控制面记录创建时间)',
    remark              VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '备注',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    INDEX idx_agent_session_definition (agent_definition_id),
    INDEX idx_agent_session_revision (agent_revision_id),
    INDEX idx_agent_session_owner (owner_user_id),
    INDEX idx_agent_session_status (status),
    INDEX idx_agent_session_deleted_at (deleted_at),
    CONSTRAINT fk_agent_session_definition FOREIGN KEY (agent_definition_id) REFERENCES agent_definition (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent 会话(控制面元数据;运行状态/事件/锁在 Redis,不落库)';

-- ============================================================
-- Section A3: Skill 草稿与 Release
-- ============================================================

-- Skill 草稿: SKILL.md 全文 + 资源文件,审核后变为不可变 Release
CREATE TABLE agent_skill_draft (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_user_id       BIGINT UNSIGNED NOT NULL  COMMENT '所有者(软引用 sys_user.id)',
    name                VARCHAR(128)    NOT NULL  COMMENT 'Skill 名(来自 SKILL.md frontmatter name)',
    visibility          VARCHAR(32)     NOT NULL DEFAULT 'PRIVATE'  COMMENT 'MARKET=进市场/PRIVATE=仅所有者',
    status              VARCHAR(32)     NOT NULL DEFAULT 'DRAFT'  COMMENT 'DRAFT/PENDING_REVIEW/REJECTED/CONSUMED',
    description         VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '来自 SKILL.md frontmatter description',
    skill_content       MEDIUMTEXT      NOT NULL  COMMENT '完整 SKILL.md 全文',
    content_hash        VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT 'SKILL.md+资源按 resource_path 字典序拼接的 SHA-256 hex',
    based_on_release_id BIGINT UNSIGNED DEFAULT NULL  COMMENT '从既有 Release 开草稿时的来源(软引用)',
    review_comment      VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '审核意见(对用户可见)',
    reviewed_by         BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '审核人(0=未审;软引用 sys_user.id)',
    reviewed_at         TIMESTAMP       NULL DEFAULT NULL  COMMENT '审核时间',
    remark              VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '内部备注',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_draft_owner_name_vis (owner_user_id, name, visibility, deleted_at),
    INDEX idx_agent_skill_draft_status (status),
    INDEX idx_agent_skill_draft_visibility (visibility),
    INDEX idx_agent_skill_draft_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Skill 草稿(所有者+name+visibility 唯一;SKILL.md 全文 + 资源)';

-- Skill 草稿资源文件: 相对路径一行一个,禁止绝对路径/../反斜杠
CREATE TABLE agent_skill_draft_resource (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    draft_id    BIGINT UNSIGNED NOT NULL  COMMENT '归属草稿(FK)',
    resource_path VARCHAR(255)  NOT NULL  COMMENT '相对路径(如 references/foo.md)',
    content     MEDIUMTEXT      NOT NULL  COMMENT '文件文本内容(二进制资源本期不入)',
    content_hash VARCHAR(64)    NOT NULL DEFAULT ''  COMMENT '文件内容 SHA-256 hex',
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_draft_resource_path (draft_id, resource_path),
    INDEX idx_agent_skill_draft_resource_draft (draft_id),
    CONSTRAINT fk_agent_skill_draft_resource_draft FOREIGN KEY (draft_id) REFERENCES agent_skill_draft (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Skill 草稿资源文件(相对路径;随草稿维护,发布时冻结拷贝)';

-- Skill Release: 不可变快照;市场列表由此派生
CREATE TABLE agent_skill_release (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_user_id       BIGINT UNSIGNED NOT NULL  COMMENT '所有者(软引用 sys_user.id)',
    name                VARCHAR(128)    NOT NULL  COMMENT 'Skill 名(冻结)',
    visibility          VARCHAR(32)     NOT NULL  COMMENT 'MARKET/PRIVATE(冻结;只决定是否进市场列表)',
    status              VARCHAR(32)     NOT NULL DEFAULT 'PUBLISHED'  COMMENT 'PUBLISHED=在售/DEPRECATED=弃用(下架/单个弃用)',
    version             INT UNSIGNED    NOT NULL  COMMENT '在(owner,visibility,name)内从 1 递增',
    description         VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '来自 SKILL.md description(冻结)',
    skill_content       MEDIUMTEXT      NOT NULL  COMMENT '完整 SKILL.md 全文(冻结,不可 UPDATE)',
    content_hash        VARCHAR(64)     NOT NULL  COMMENT '冻结内容 hash(运行漂移校验用)',
    source_draft_id     BIGINT UNSIGNED DEFAULT NULL  COMMENT '来源草稿 id(软引用)',
    remark              VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '备注',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_release_owner_name_ver (owner_user_id, visibility, name, version),
    INDEX idx_agent_skill_release_market (visibility, status, name),
    INDEX idx_agent_skill_release_status (status),
    INDEX idx_agent_skill_release_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Skill Release(不可变快照;行插入后内容/版本不得 UPDATE,弃用只改 status)';

-- Skill Release 资源文件: 冻结拷贝
CREATE TABLE agent_skill_release_resource (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    release_id    BIGINT UNSIGNED NOT NULL  COMMENT '归属 Release(FK)',
    resource_path VARCHAR(255)    NOT NULL  COMMENT '相对路径',
    content       MEDIUMTEXT      NOT NULL  COMMENT '文件文本内容',
    content_hash  VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '文件内容 SHA-256 hex',
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_release_resource_path (release_id, resource_path),
    INDEX idx_agent_skill_release_resource_release (release_id),
    CONSTRAINT fk_agent_skill_release_resource_release FOREIGN KEY (release_id) REFERENCES agent_skill_release (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Skill Release 冻结资源文件(不可变)';

-- ============================================================
-- Section A4: MCP 草稿与 Release
-- ============================================================

-- MCP Draft: 连接配置草稿(私有可带加密密钥,市场草稿应无密钥)
CREATE TABLE agent_mcp_draft (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_user_id       BIGINT UNSIGNED NOT NULL  COMMENT '所有者(软引用 sys_user.id)',
    name                VARCHAR(128)    NOT NULL  COMMENT 'server 名(唯一键内)',
    visibility          VARCHAR(32)     NOT NULL DEFAULT 'PRIVATE'  COMMENT 'MARKET/PRIVATE',
    status              VARCHAR(32)     NOT NULL DEFAULT 'DRAFT'  COMMENT 'DRAFT/PENDING_REVIEW/REJECTED/CONSUMED',
    transport           VARCHAR(32)     NOT NULL DEFAULT 'sse'  COMMENT 'sse/http(小写)',
    url                 VARCHAR(512)    NOT NULL  COMMENT '连接地址(HTTP/SSE endpoint)',
    headers_json        JSON            DEFAULT NULL  COMMENT '静态头(无密)',
    encrypted_secret    TEXT            DEFAULT NULL  COMMENT '加密密钥密文(不存明文;MARKET 发布时剥离)',
    connect_timeout_ms  INT UNSIGNED    NOT NULL DEFAULT 5000  COMMENT '连接超时(毫秒)',
    review_comment      VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '审核意见(对用户可见)',
    reviewed_by         BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '审核人(0=未审;软引用 sys_user.id)',
    reviewed_at         TIMESTAMP       NULL DEFAULT NULL  COMMENT '审核时间',
    remark              VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '内部备注',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_mcp_draft_owner_name_vis (owner_user_id, name, visibility, deleted_at),
    INDEX idx_agent_mcp_draft_status (status),
    INDEX idx_agent_mcp_draft_visibility (visibility),
    INDEX idx_agent_mcp_draft_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='MCP 连接配置草稿(私有可带密钥,市场草稿应无密钥)';

-- MCP Release: 审核通过时握手冻结的连接配置副本;工具目录不落库
CREATE TABLE agent_mcp_release (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_user_id       BIGINT UNSIGNED NOT NULL  COMMENT '所有者(软引用 sys_user.id)',
    name                VARCHAR(128)    NOT NULL  COMMENT 'server 名(冻结)',
    visibility          VARCHAR(32)     NOT NULL  COMMENT 'MARKET(无密钥)/PRIVATE(带密钥)',
    status              VARCHAR(32)     NOT NULL DEFAULT 'PUBLISHED'  COMMENT 'PUBLISHED=在售/DEPRECATED=弃用',
    version             INT UNSIGNED    NOT NULL  COMMENT '在(owner,visibility,name)内递增',
    transport           VARCHAR(32)     NOT NULL DEFAULT 'sse'  COMMENT 'sse/http(冻结)',
    url                 VARCHAR(512)    NOT NULL  COMMENT '连接地址(冻结)',
    headers_json        JSON            DEFAULT NULL  COMMENT '静态头(冻结)',
    encrypted_secret    TEXT            DEFAULT NULL  COMMENT '加密密钥密文(MARKET Release 必须为空)',
    connect_timeout_ms  INT UNSIGNED    NOT NULL DEFAULT 5000  COMMENT '连接超时(冻结)',
    source_draft_id     BIGINT UNSIGNED DEFAULT NULL  COMMENT '来源草稿 id(软引用)',
    remark              VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '备注',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_mcp_release_owner_name_ver (owner_user_id, visibility, name, version),
    INDEX idx_agent_mcp_release_market (visibility, status, name),
    INDEX idx_agent_mcp_release_status (status),
    INDEX idx_agent_mcp_release_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='MCP Release(连接配置冻结副本;目录不入库;MARKET 无密钥 PRIVATE 带密钥)';

-- ============================================================
-- Section A5: Revision 级 Binding（发布者预置默认装配）
-- ============================================================

-- Revision Skill Binding: Revision 内 skill_name 唯一;override_winner 处理同名
CREATE TABLE agent_revision_skill_binding (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    agent_revision_id   BIGINT UNSIGNED NOT NULL  COMMENT '归属 Revision(FK)',
    skill_release_id    BIGINT UNSIGNED NOT NULL  COMMENT '绑定的 Release 快照(FK)',
    skill_name          VARCHAR(128)    NOT NULL  COMMENT '从 Release 拷贝的 skill_name',
    content_hash        VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '从 Release 拷贝(运行漂移校验用)',
    override_winner     TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '同名冲突(市场vs私有)胜者标记;Revision 内同 skill_name 恰好一条=1',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_rev_skill_binding_name (agent_revision_id, skill_name),
    INDEX idx_agent_rev_skill_binding_release (skill_release_id),
    INDEX idx_agent_rev_skill_binding_revision (agent_revision_id),
    CONSTRAINT fk_agent_rev_skill_binding_revision FOREIGN KEY (agent_revision_id) REFERENCES agent_revision (id),
    CONSTRAINT fk_agent_rev_skill_binding_release FOREIGN KEY (skill_release_id) REFERENCES agent_skill_release (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent Revision Skill Binding(发布者预置;skill_name 唯一,override_winner 处理同名;解绑=物理删)';

-- Revision MCP Binding: Revision 内 mcp_name 唯一;Agent 发布时补配密钥
CREATE TABLE agent_revision_mcp_binding (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    agent_revision_id   BIGINT UNSIGNED NOT NULL  COMMENT '归属 Revision(FK)',
    mcp_release_id      BIGINT UNSIGNED NOT NULL  COMMENT '绑定的 Release(FK)',
    mcp_name            VARCHAR(128)    NOT NULL  COMMENT 'server 名(从 Release 拷贝)',
    encrypted_secret    TEXT            DEFAULT NULL  COMMENT 'Agent 层补配/覆盖的加密密钥(市场 MCP 在此配密钥)',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_rev_mcp_binding_name (agent_revision_id, mcp_name),
    INDEX idx_agent_rev_mcp_binding_release (mcp_release_id),
    INDEX idx_agent_rev_mcp_binding_revision (agent_revision_id),
    CONSTRAINT fk_agent_rev_mcp_binding_revision FOREIGN KEY (agent_revision_id) REFERENCES agent_revision (id),
    CONSTRAINT fk_agent_rev_mcp_binding_release FOREIGN KEY (mcp_release_id) REFERENCES agent_mcp_release (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent Revision MCP Binding(发布者预置;mcp_name 唯一;密钥在此补配并冻结)';

-- ============================================================
-- Section A6: Session 级 Binding（用户侧临时追加/覆盖,不改 Agent 定义）
-- ============================================================

CREATE TABLE agent_session_skill_binding (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id          BIGINT UNSIGNED NOT NULL  COMMENT '归属会话(FK)',
    skill_release_id    BIGINT UNSIGNED NOT NULL  COMMENT '绑定的 Release 快照(FK)',
    skill_name          VARCHAR(128)    NOT NULL  COMMENT 'skill_name(Session 内唯一;同名覆盖 Revision)',
    content_hash        VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '从 Release 拷贝(运行漂移校验用)',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_session_skill_binding_name (session_id, skill_name),
    INDEX idx_agent_session_skill_binding_release (skill_release_id),
    INDEX idx_agent_session_skill_binding_session (session_id),
    CONSTRAINT fk_agent_session_skill_binding_session FOREIGN KEY (session_id) REFERENCES agent_session (id),
    CONSTRAINT fk_agent_session_skill_binding_release FOREIGN KEY (skill_release_id) REFERENCES agent_skill_release (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent Session Skill Binding(用户侧追加/覆盖;Session 内 skill_name 唯一;解绑=物理删)';

CREATE TABLE agent_session_mcp_binding (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id          BIGINT UNSIGNED NOT NULL  COMMENT '归属会话(FK)',
    mcp_release_id      BIGINT UNSIGNED NOT NULL  COMMENT '绑定的 Release(FK)',
    mcp_name            VARCHAR(128)    NOT NULL  COMMENT 'server 名(Session 内唯一;同名覆盖 Revision)',
    encrypted_secret    TEXT            DEFAULT NULL  COMMENT 'Session 绑定时补配/覆盖的加密密钥密文',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_session_mcp_binding_name (session_id, mcp_name),
    INDEX idx_agent_session_mcp_binding_release (mcp_release_id),
    INDEX idx_agent_session_mcp_binding_session (session_id),
    CONSTRAINT fk_agent_session_mcp_binding_session FOREIGN KEY (session_id) REFERENCES agent_session (id),
    CONSTRAINT fk_agent_session_mcp_binding_release FOREIGN KEY (mcp_release_id) REFERENCES agent_mcp_release (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent Session MCP Binding(用户侧追加/覆盖;Session 内 mcp_name 唯一;密钥补配冻结)';

-- ============================================================
-- Section A7: Git Skill 来源（受控导入;本期建表,preview/sync 接口后续实现）
-- ============================================================

CREATE TABLE agent_skill_git_source (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    scope               VARCHAR(32)     NOT NULL DEFAULT 'PRIVATE'  COMMENT 'MARKET(仅管理员)/PRIVATE(归当前用户)',
    owner_user_id       BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '来源所有者(软引用 sys_user.id)',
    url                 VARCHAR(255)    NOT NULL  COMMENT 'HTTPS 地址(禁止 SSH/本地路径/user-info;展示脱敏)',
    ref                 VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '分支/标签/commit',
    subdirectory        VARCHAR(255)    NOT NULL DEFAULT ''  COMMENT '仓库子目录',
    encrypted_secret    TEXT            DEFAULT NULL  COMMENT '加密密钥密文(私有仓库用)',
    last_commit_sha     VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '最近成功同步 commit_sha',
    last_synced_at      TIMESTAMP       NULL DEFAULT NULL  COMMENT '最近成功同步时间',
    status              VARCHAR(32)     NOT NULL DEFAULT 'READY'  COMMENT 'READY=正常/FAILED=同步失败',
    last_error          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '错误摘要',
    remark              VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '备注',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_git_source_scope_owner_url (scope, owner_user_id, url, deleted_at),
    INDEX idx_agent_skill_git_source_owner (owner_user_id),
    INDEX idx_agent_skill_git_source_status (status),
    INDEX idx_agent_skill_git_source_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Git Skill 来源(受控导入配置;唯一(scope,owner,url,deleted_at))';

CREATE TABLE agent_skill_git_sync (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    source_id       BIGINT UNSIGNED NOT NULL  COMMENT '来源(FK)',
    commit_sha      VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '同步时 commit',
    skill_path      VARCHAR(255)    NOT NULL  COMMENT '包路径',
    content_hash    VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '导入内容 hash',
    draft_id        BIGINT UNSIGNED DEFAULT NULL  COMMENT '对应草稿(软引用;NULL=无草稿)',
    result          VARCHAR(32)     NOT NULL DEFAULT 'CREATED'  COMMENT 'CREATED/UPDATED/UNCHANGED/CONFLICT/FAILED',
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_git_sync_source_path (source_id, skill_path, deleted_at),
    INDEX idx_agent_skill_git_sync_source (source_id),
    INDEX idx_agent_skill_git_sync_draft (draft_id),
    CONSTRAINT fk_agent_skill_git_sync_source FOREIGN KEY (source_id) REFERENCES agent_skill_git_source (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Git Skill 同步记录(幂等;保护人工修改;唯一(source,skill_path,deleted_at))';

-- ============================================================
-- Section A3: 模型草稿、Release 与 Session 模型选择
-- ============================================================

CREATE TABLE agent_model_draft (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_user_id           BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '所有者(软引用 sys_user.id;OFFICIAL 可为 0=平台)',
    name                    VARCHAR(128)    NOT NULL  COMMENT '模型显示名(唯一键内)',
    scope                   VARCHAR(32)     NOT NULL DEFAULT 'PRIVATE'  COMMENT 'OFFICIAL=官方全站/PRIVATE=仅所有者',
    code                    VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '功能码(video/image 等;普通文本为空)',
    status                  VARCHAR(32)     NOT NULL DEFAULT 'DRAFT'  COMMENT 'DRAFT/PENDING_REVIEW/REJECTED/CONSUMED',
    provider                VARCHAR(32)     NOT NULL  COMMENT 'openai-compatible/anthropic(小写)',
    base_url                VARCHAR(512)    NOT NULL  COMMENT '连接地址(HTTPS)',
    model_name              VARCHAR(128)    NOT NULL  COMMENT '远端模型标识',
    capabilities            JSON            DEFAULT NULL  COMMENT '能力 JSON:text/thinking/tool_use/vision/json_mode',
    parameter_guardrails    JSON            DEFAULT NULL  COMMENT '参数护栏 JSON:temperature/top_p/max_tokens 范围与默认',
    context_length          BIGINT UNSIGNED NOT NULL DEFAULT 500000 COMMENT '上下文长度(token)',
    encrypted_secret        TEXT            DEFAULT NULL  COMMENT '加密 API Key 密文(不存明文)',
    review_comment          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '审核意见(对用户可见)',
    reviewed_by             BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '审核人(0=未审;软引用 sys_user.id)',
    reviewed_at             TIMESTAMP       NULL DEFAULT NULL  COMMENT '审核时间',
    remark                  VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '内部备注',
    is_enabled              TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at              BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by              BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by              BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_model_draft_owner_name_scope (owner_user_id, name, scope, deleted_at),
    INDEX idx_agent_model_draft_status (status),
    INDEX idx_agent_model_draft_scope (scope),
    INDEX idx_agent_model_draft_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='模型连接配置草稿(OFFICIAL 需审核;PRIVATE 免审但仍须探测后发布)';

-- 模型 Release: 探测冻结的连接配置副本;发布即进入可用模型池
CREATE TABLE agent_model_release (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_user_id           BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '所有者(软引用 sys_user.id)',
    name                    VARCHAR(128)    NOT NULL  COMMENT '模型显示名(冻结)',
    scope                   VARCHAR(32)     NOT NULL  COMMENT 'OFFICIAL/PRIVATE(冻结)',
    code                    VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '功能码(冻结)',
    status                  VARCHAR(32)     NOT NULL DEFAULT 'PUBLISHED'  COMMENT 'PUBLISHED=可用/DEPRECATED=弃用',
    version                 INT UNSIGNED    NOT NULL  COMMENT '在(owner,scope,name)内递增',
    provider                VARCHAR(32)     NOT NULL  COMMENT 'openai-compatible/anthropic(冻结)',
    base_url                VARCHAR(512)    NOT NULL  COMMENT '连接地址(冻结)',
    model_name              VARCHAR(128)    NOT NULL  COMMENT '远端模型标识(冻结)',
    capabilities            JSON            DEFAULT NULL  COMMENT '能力 JSON(冻结)',
    parameter_guardrails    JSON            DEFAULT NULL  COMMENT '参数护栏 JSON(冻结)',
    context_length          BIGINT UNSIGNED NOT NULL DEFAULT 500000 COMMENT '上下文长度(token，冻结)',
    encrypted_secret        TEXT            DEFAULT NULL  COMMENT '加密 API Key 密文(冻结;官方平台托管/私有用户密钥)',
    source_draft_id         BIGINT UNSIGNED DEFAULT NULL  COMMENT '来源草稿 id(软引用)',
    remark                  VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '备注',
    is_enabled              TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at              BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by              BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by              BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_model_release_owner_name_ver (owner_user_id, scope, name, version),
    INDEX idx_agent_model_release_pool (scope, status, name),
    INDEX idx_agent_model_release_status (status),
    INDEX idx_agent_model_release_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='模型 Release(连接配置冻结副本;发布即进可用池;弃用只改 status)';

-- Session 记住的模型选择: 每会话一条;无密钥(密钥已在 Release 冻结)
CREATE TABLE agent_session_model_binding (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id          BIGINT UNSIGNED NOT NULL  COMMENT '归属会话(FK)',
    model_release_id    BIGINT UNSIGNED NOT NULL  COMMENT '用户选择的模型 Release 指针(FK)',
    model_name          VARCHAR(128)    NOT NULL  COMMENT '从 Release 拷贝的远端模型标识',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_session_model_binding_session (session_id),
    INDEX idx_agent_session_model_binding_release (model_release_id),
    CONSTRAINT fk_agent_session_model_binding_session FOREIGN KEY (session_id) REFERENCES agent_session (id),
    CONSTRAINT fk_agent_session_model_binding_release FOREIGN KEY (model_release_id) REFERENCES agent_model_release (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent Session 模型选择(每会话记住一个 model_release_id;解绑=物理删)';
