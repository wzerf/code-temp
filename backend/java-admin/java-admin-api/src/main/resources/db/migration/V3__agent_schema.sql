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
    PRIMARY KEY (id),
    INDEX idx_agent_session_owner_user_id_created_at (owner_user_id, created_at),
    INDEX idx_agent_session_definition_id_created_at (agent_definition_id, created_at),
    INDEX idx_agent_session_revision_id (agent_revision_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent 会话控制面元数据与固定 Revision';
