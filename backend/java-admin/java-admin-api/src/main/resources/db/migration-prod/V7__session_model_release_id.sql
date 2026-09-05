-- Flyway V7: 会话模型选择内联到 agent_session.model_release_id
-- 去掉一对一表 agent_session_model_binding；GET/DELETE 独立接口下线，PUT 传 null 即清除。

ALTER TABLE agent_session
    ADD COLUMN model_release_id BIGINT UNSIGNED DEFAULT NULL
        COMMENT '会话记住的模型 Release 指针(未选则 NULL;回落 Revision 默认)'
        AFTER agent_revision_id,
    ADD INDEX idx_agent_session_model_release (model_release_id),
    ADD CONSTRAINT fk_agent_session_model_release
        FOREIGN KEY (model_release_id) REFERENCES agent_model_release (id);

UPDATE agent_session s
INNER JOIN agent_session_model_binding b ON b.session_id = s.id
SET s.model_release_id = b.model_release_id;

DROP TABLE agent_session_model_binding;

UPDATE sys_api
SET name = '绑定会话模型',
    remark = '写入 agent_session.model_release_id;传 null 则清除并回落 Revision 默认'
WHERE id = 178 AND deleted_at = 0;

UPDATE sys_api
SET deleted_at = UNIX_TIMESTAMP() * 1000,
    is_enabled = 0
WHERE id IN (177, 179) AND deleted_at = 0;

DELETE FROM sys_role_api WHERE api_id IN (177, 179);
