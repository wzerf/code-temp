-- ============================================================
-- Flyway V8: MCP OAuth 完整登录（Authorization Code + PKCE）
-- 承接 fca588d 的 RFC9728/8414 发现：补齐 client 配置冻结、
-- 用户级 token、一次性 state/PKCE，形成“发现→登录→换票→使用”闭环。
-- 约定：
--   agent_mcp_draft / agent_mcp_release 追加 OAuth 配置列（Release 为冻结副本）
--   MARKET OAuth Release 禁止冻结 client_secret（用 DCR 或公开 client）
--   token 按 (release, user) 隔离；access/refresh 密文落库，明文只在内存
--   state 一次性，10 分钟过期，回调即删
-- ============================================================

-- 草稿：OAuth 配置列
ALTER TABLE agent_mcp_draft
    ADD COLUMN auth_type VARCHAR(32) NOT NULL DEFAULT 'NONE'
        COMMENT 'NONE=静态密钥直连;OAUTH=OAuth 登录' AFTER encrypted_secret,
    ADD COLUMN oauth_client_id VARCHAR(256) NOT NULL DEFAULT ''
        COMMENT 'OAuth Client ID（公开值;不支持 DCR 的服务必填）' AFTER auth_type,
    ADD COLUMN oauth_client_secret_enc TEXT DEFAULT NULL
        COMMENT 'OAuth Client Secret 密文（MARKET 必须为空）' AFTER oauth_client_id,
    ADD COLUMN oauth_scope VARCHAR(1024) NOT NULL DEFAULT ''
        COMMENT '空格分隔 scope（发布者预填;登录时可追加）' AFTER oauth_client_secret_enc,
    ADD COLUMN oauth_authorization_endpoint VARCHAR(512) NOT NULL DEFAULT ''
        COMMENT '发现缓存：授权端点' AFTER oauth_scope,
    ADD COLUMN oauth_token_endpoint VARCHAR(512) NOT NULL DEFAULT ''
        COMMENT '发现缓存：换票端点' AFTER oauth_authorization_endpoint,
    ADD COLUMN oauth_require_login TINYINT(1) NOT NULL DEFAULT 1
        COMMENT 'MARKET 发布：approve 是否要求校验发布者登录态；0=可选跳过' AFTER oauth_token_endpoint;

-- Release：同构冻结列
ALTER TABLE agent_mcp_release
    ADD COLUMN auth_type VARCHAR(32) NOT NULL DEFAULT 'NONE'
        COMMENT 'NONE=静态密钥直连;OAUTH=OAuth 登录' AFTER encrypted_secret,
    ADD COLUMN oauth_client_id VARCHAR(256) NOT NULL DEFAULT ''
        COMMENT 'OAuth Client ID（冻结）' AFTER auth_type,
    ADD COLUMN oauth_client_secret_enc TEXT DEFAULT NULL
        COMMENT 'OAuth Client Secret 密文（MARKET 必须为空;冻结）' AFTER oauth_client_id,
    ADD COLUMN oauth_scope VARCHAR(1024) NOT NULL DEFAULT ''
        COMMENT '空格分隔 scope（冻结）' AFTER oauth_client_secret_enc,
    ADD COLUMN oauth_authorization_endpoint VARCHAR(512) NOT NULL DEFAULT ''
        COMMENT '授权端点（冻结）' AFTER oauth_scope,
    ADD COLUMN oauth_token_endpoint VARCHAR(512) NOT NULL DEFAULT ''
        COMMENT '换票端点（冻结）' AFTER oauth_authorization_endpoint,
    ADD COLUMN oauth_require_login TINYINT(1) NOT NULL DEFAULT 1
        COMMENT 'MARKET 发布时的校验要求（冻结;运行时无用）' AFTER oauth_token_endpoint;

-- 用户级 token（多用户各存各的；撤销=物理删）
CREATE TABLE agent_mcp_oauth_token (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    mcp_release_id      BIGINT UNSIGNED NOT NULL  COMMENT '绑定的 Release(FK)',
    user_id             BIGINT UNSIGNED NOT NULL  COMMENT '登录用户(软引用 sys_user.id)',
    access_token_enc    TEXT            NOT NULL  COMMENT 'access_token 密文(不存明文)',
    refresh_token_enc   TEXT            DEFAULT NULL  COMMENT 'refresh_token 密文(可空)',
    token_type          VARCHAR(32)     NOT NULL DEFAULT 'Bearer'  COMMENT 'token 类型',
    expires_at          TIMESTAMP       NULL DEFAULT NULL  COMMENT '过期时间(NULL=服务端未给)',
    scope               VARCHAR(1024)   NOT NULL DEFAULT ''  COMMENT '实际授予 scope',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_oauth_token_release_user (mcp_release_id, user_id),
    INDEX idx_oauth_token_user (user_id),
    CONSTRAINT fk_oauth_token_release FOREIGN KEY (mcp_release_id) REFERENCES agent_mcp_release (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='MCP OAuth 用户 token（按 release+user 隔离；明文只在内存）';

-- 一次性登录 state（CSRF + PKCE verifier 暂存；回调即删）
CREATE TABLE agent_mcp_oauth_state (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    state               VARCHAR(64)     NOT NULL  COMMENT '一次性 state（CSRF）',
    mcp_release_id      BIGINT UNSIGNED NOT NULL  COMMENT '目标 Release(FK)',
    user_id             BIGINT UNSIGNED NOT NULL  COMMENT '发起登录的用户(软引用 sys_user.id)',
    code_verifier       VARCHAR(128)    NOT NULL  COMMENT 'PKCE verifier（只存内存级随机串）',
    redirect_uri        VARCHAR(512)    NOT NULL  COMMENT '本次登录的回调地址',
    scope               VARCHAR(1024)   NOT NULL DEFAULT ''  COMMENT '本次请求的 scope',
    resource            VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT 'RFC9728 resource 指示',
    client_id           VARCHAR(256)    NOT NULL DEFAULT ''  COMMENT '本次使用的 client_id（DCR 或配置）',
    expires_at          TIMESTAMP       NOT NULL  COMMENT '过期时间（10 分钟）',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_oauth_state (state),
    INDEX idx_oauth_state_expires (expires_at),
    CONSTRAINT fk_oauth_state_release FOREIGN KEY (mcp_release_id) REFERENCES agent_mcp_release (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='MCP OAuth 一次性登录态（state+PKCE；回调即删）';

-- ============================================================
-- Section: sys_api（续 183 之后）+ root 授权
-- ============================================================

INSERT INTO sys_api (id, name, method, path, permission_code, api_group, remark, is_enabled, deleted_at, created_by, updated_by)
VALUES
    (184, '发起 MCP OAuth 登录', 'POST', '/api/system/mcp/release/:id/oauth/start', 'mcp:oauth:start', 'MCP 管理', 'DCR(可选)+拼授权地址+存一次性 state', 1, 0, 0, 0),
    (185, 'MCP OAuth 回调换票', 'POST', '/api/system/mcp/oauth/callback', 'mcp:oauth:callback', 'MCP 管理', '校验 state+PKCE 换票落库', 1, 0, 0, 0),
    (186, 'MCP OAuth 登录态', 'GET', '/api/system/mcp/release/:id/oauth/status', 'mcp:oauth:status', 'MCP 管理', '当前用户 token 有效性', 1, 0, 0, 0),
    (187, '刷新 MCP OAuth token', 'POST', '/api/system/mcp/release/:id/oauth/refresh', 'mcp:oauth:refresh', 'MCP 管理', 'refresh_token 换票', 1, 0, 0, 0),
    (188, '解绑 MCP OAuth 授权', 'DELETE', '/api/system/mcp/release/:id/oauth/token', 'mcp:oauth:revoke', 'MCP 管理', '删除当前用户 token', 1, 0, 0, 0);

INSERT INTO sys_role_api (role_id, api_id) VALUES
    (1, 184), (1, 185), (1, 186), (1, 187), (1, 188);
