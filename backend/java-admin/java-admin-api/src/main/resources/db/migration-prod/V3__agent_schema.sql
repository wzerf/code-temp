-- Flyway V3: Agent 控制面基础（Agent Definition / Revision / Session）
-- 已发布 Revision 是不可变快照；会话仅固定 Revision，不保存运行状态。

CREATE TABLE agent_definition (
    id                            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name                          VARCHAR(128)    NOT NULL,
    description                   VARCHAR(512)    NOT NULL DEFAULT '',
    owner_user_id                 BIGINT UNSIGNED NOT NULL,
    current_published_revision_id BIGINT UNSIGNED DEFAULT NULL,
    remark                        VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled                    TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at                    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at                    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by                    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by                    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_definition_name_deleted_at (name, deleted_at),
    INDEX idx_agent_definition_current_published_revision_id (current_published_revision_id),
    INDEX idx_agent_definition_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent 稳定定义与当前发布 Revision 指针';

CREATE TABLE agent_revision (
    id                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    agent_definition_id       BIGINT UNSIGNED NOT NULL,
    status                    VARCHAR(32)     NOT NULL,
    source_draft_revision_id  BIGINT UNSIGNED DEFAULT NULL,
    system_prompt             TEXT            NOT NULL,
    model_config              JSON            DEFAULT NULL,
    permission_policy         JSON            DEFAULT NULL,
    memory_policy             JSON            DEFAULT NULL,
    compression_policy        JSON            DEFAULT NULL,
    remark                    VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled                TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at                BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at                TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by                BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by                BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_agent_revision_definition_status (agent_definition_id, status),
    INDEX idx_agent_revision_source_draft_revision_id (source_draft_revision_id),
    INDEX idx_agent_revision_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent 草稿与不可变发布 Revision 快照';

CREATE TABLE agent_session (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    agent_definition_id BIGINT UNSIGNED NOT NULL,
    agent_revision_id   BIGINT UNSIGNED DEFAULT NULL,
    owner_user_id       BIGINT UNSIGNED NOT NULL,
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_agent_session_owner_user_id_created_at (owner_user_id, created_at),
    INDEX idx_agent_session_definition_id_created_at (agent_definition_id, created_at),
    INDEX idx_agent_session_revision_id (agent_revision_id),
    INDEX idx_agent_session_owner_definition_active_at (owner_user_id, agent_definition_id, last_active_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent 会话控制面元数据与固定 Revision';

-- Skill 市场（SDK 契约）与控制面草稿 / Release / 安装 / Binding
CREATE TABLE agent_skill (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    name                 VARCHAR(255)  NOT NULL,
    description          TEXT          NOT NULL,
    skill_content        LONGTEXT      NOT NULL,
    source               VARCHAR(255)  NOT NULL,
    metadata_json        LONGTEXT      NULL,
    current_release_id   BIGINT UNSIGNED NOT NULL,
    owner_user_id        BIGINT UNSIGNED NOT NULL DEFAULT 0,
    visibility           VARCHAR(32)   NOT NULL DEFAULT 'MARKET',
    content_hash         VARCHAR(64)   NOT NULL,
    remark               VARCHAR(512)  NOT NULL DEFAULT '',
    is_enabled           TINYINT(1)    NOT NULL DEFAULT 1,
    created_by           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_name (name),
    INDEX idx_agent_skill_current_release_id (current_release_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='技能市场当前已发布行；MysqlSkillRepository 读取';

CREATE TABLE agent_skill_resource (
    id                BIGINT        NOT NULL,
    resource_path     VARCHAR(500)  NOT NULL,
    resource_content  LONGTEXT      NOT NULL,
    content_hash      VARCHAR(64)   NOT NULL,
    created_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id, resource_path),
    CONSTRAINT fk_agent_skill_resource_id
        FOREIGN KEY (id) REFERENCES agent_skill (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='技能市场当前行的附属文件';

CREATE TABLE agent_skill_draft (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name                 VARCHAR(255)    NOT NULL,
    description          TEXT            NOT NULL,
    skill_content        LONGTEXT        NOT NULL,
    visibility           VARCHAR(32)     NOT NULL,
    status               VARCHAR(32)     NOT NULL,
    owner_user_id        BIGINT UNSIGNED NOT NULL,
    based_on_release_id  BIGINT UNSIGNED DEFAULT NULL,
    content_hash         VARCHAR(64)     NOT NULL,
    review_comment       VARCHAR(512)    NOT NULL DEFAULT '',
    reviewed_by          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    reviewed_at          TIMESTAMP       NULL DEFAULT NULL,
    remark               VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled           TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_draft_owner_name (owner_user_id, name, deleted_at),
    INDEX idx_agent_skill_draft_status (status, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Skill 草稿与审核中内容';

CREATE TABLE agent_skill_draft_resource (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    draft_id          BIGINT UNSIGNED NOT NULL,
    resource_path     VARCHAR(500)    NOT NULL,
    resource_content  LONGTEXT        NOT NULL,
    content_hash      VARCHAR(64)     NOT NULL,
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_draft_resource_path (draft_id, resource_path),
    CONSTRAINT fk_agent_skill_draft_resource_draft_id
        FOREIGN KEY (draft_id) REFERENCES agent_skill_draft (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Skill 草稿附属文件';

CREATE TABLE agent_skill_release (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255)    NOT NULL,
    version         INT UNSIGNED    NOT NULL,
    description     TEXT            NOT NULL,
    skill_content   LONGTEXT        NOT NULL,
    visibility      VARCHAR(32)     NOT NULL,
    status          VARCHAR(32)     NOT NULL,
    owner_user_id   BIGINT UNSIGNED NOT NULL,
    source_draft_id BIGINT UNSIGNED DEFAULT NULL,
    content_hash    VARCHAR(64)     NOT NULL,
    source          VARCHAR(255)    NOT NULL DEFAULT 'mysql',
    remark          VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_release_scope_version (owner_user_id, visibility, name, version, deleted_at),
    INDEX idx_agent_skill_release_name_status (name, visibility, status, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='不可变 Skill Release 快照';

CREATE TABLE agent_skill_release_resource (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    release_id        BIGINT UNSIGNED NOT NULL,
    resource_path     VARCHAR(500)    NOT NULL,
    resource_content  LONGTEXT        NOT NULL,
    content_hash      VARCHAR(64)     NOT NULL,
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_release_resource_path (release_id, resource_path),
    CONSTRAINT fk_agent_skill_release_resource_release_id
        FOREIGN KEY (release_id) REFERENCES agent_skill_release (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Skill Release 冻结的附属文件';

CREATE TABLE agent_skill_install (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    skill_name      VARCHAR(255)    NOT NULL,
    visibility      VARCHAR(32)     NOT NULL,
    owner_user_id   BIGINT UNSIGNED NOT NULL,
    remark          VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_skill_install (user_id, skill_name, visibility, owner_user_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户对 Skill 的安装资格；不自动形成 Binding';

CREATE TABLE agent_revision_skill_binding (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    agent_revision_id   BIGINT UNSIGNED NOT NULL,
    skill_release_id    BIGINT UNSIGNED NOT NULL,
    skill_name          VARCHAR(255)    NOT NULL,
    content_hash        VARCHAR(64)     NOT NULL,
    override_winner     TINYINT(1)      NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_agent_revision_skill_binding_revision_name (agent_revision_id, skill_name),
    INDEX idx_agent_revision_skill_binding_release_id (skill_release_id),
    CONSTRAINT fk_agent_revision_skill_binding_revision
        FOREIGN KEY (agent_revision_id) REFERENCES agent_revision (id),
    CONSTRAINT fk_agent_revision_skill_binding_release
        FOREIGN KEY (skill_release_id) REFERENCES agent_skill_release (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent Revision 绑定的 Skill Release 快照指针';
