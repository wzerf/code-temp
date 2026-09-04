-- ============================================================
-- 后台管理系统 MySQL Schema  (v5 基线 + v11 blacklist + v12 sys_user.account_expires_at + v13 sys_material + v14 target + v15 SYS_USER + v16 content + v17 sys_pay_method + v18 bills + v19 packages + v20 agent 平台)
-- 文件:       backend/db/schema.sql
-- 数据库:     <admin_db> （由各 admin 后端自行创建与配置）
-- 字符集:     utf8mb4 / utf8mb4_unicode_ci
-- 引擎:       InnoDB
-- 版本要求:   MySQL 5.7.8+ 及兼容发行版（prod 不用 utf8mb4_0900_ai_ci；该 collation 仅官方 MySQL 8.0+）
-- 表数:       47 张
--             核心 15（含 sys_data_permission / sys_blacklist / sys_material / sys_pay_method / sys_recharge_package / sys_withdraw_package）
--             账单 2（sys_pay_bill / sys_withdraw_bill；无 is_enabled / deleted_at / created_by / updated_by）
--             关联 4（sys_user_role / sys_role_api / sys_role_menu / sys_menu_api）
--             记录 4（3 张日志 + temporal_task_execution）
--             归档 3（api_log_archive / sys_login_log_archive / operation_log_archive）
--             casbin 1（casbin_rule）
--             agent 18（agent_definition/revision/session + skill draft/release(含资源/绑定) + mcp draft/release(含绑定) + model draft/release(含会话选择) + git source/sync）
-- 执行顺序:   按依赖顺序；FK 引用先建；自引用外键用 ALTER 后置
-- 部署:       本文件可独立执行；如使用迁移工具，按本文件顺序切分版本脚本
-- 边界:
--   1. admin 业务 schema 与 Temporal server 的 temporal schema 隔离
--   2. casbin_rule 由 casbin/mysql-adapter 自动管理，业务代码不直接 CRUD
--   3. 归档表与对应热表同结构，由 admin 后端 TTL 作业搬运
--   4. 软删: deleted_at BIGINT UNSIGNED (毫秒时间戳; 0=未删;非0=删除时刻)
--   5. 软删感知唯一: UNIQUE(col, deleted_at) — 0 与非0 视作不同值,天然支持"软删后重建"
--   6. 关联表（4 张）保留复合 PK + 无软删（解绑 = DELETE 物理删除）
-- NULL 策略（v5+）:
--   - NOT NULL + DEFAULT '' : VARCHAR/CHAR/TEXT/MEDIUMTEXT 业务字段
--   - NULL                  : TIMESTAMP（最后登录/关闭时间等真实未发生）
--   - NULL                  : JSON（metadata / input_summary / 条件快照等按需填）
--   - NOT NULL + DEFAULT 0  : BIGINT UNSIGNED 主外键占位（created_by / updated_by;0=系统操作/无用户上下文；
--                             sys_material.target_id 在 GENERAL 时为 0；v14+）
--   - NULL                  : BIGINT UNSIGNED 真软外键（language_code / parent_id / config_id /
--                             operation_log.target_id / sys_user_id-in-logs / subject_id）
--   - NULL                  : 业务语义 NULL（cron_expr=仅手动;reason=未失败;closed_at=仍在运行;path=BUTTON/DIR 无路径）
-- v5 相对 v4 的改动:
--   1. sys_user: 移除 dept_id 及其索引(原为 DEPT 类数据权限锚点;现交由 sys_data_permission 承担)
--   2. 4 张表: 移除 description 字段(只保留 remark 统一语义):
--        - sys_role, sys_api, dict_type, temporal_task_config
--   3. api_log: 字段扩充对齐 PG 风格(参考 sys_api_log):
--        - 新增: referer / request_uri / request_header / status_code / reason / success / location
--                / browser_name / browser_version / os_name / os_version / client_id / client_name
--                / sys_user_id / cost_time / format_change / before_change / after_change
--        - 改名: request_method→method, request_path→path, response_body→response
--                , duration_ms→cost_time, user_id→sys_user_id, response_status→status_code
--        - 放宽: request_id 64→128, module 64→255, client_ip 45→64, user_agent VARCHAR(512)→TEXT
--   4. sys_login_log: 字段扩充对齐 PG 风格(参考 PG sys_login_log):
--        - 新增: login_mac / login_time / status_code / location
--                / browser_name / browser_version / os_name / os_version / client_id / client_name
--                / reason / sys_user_id
--        - 改名: client_ip→login_ip, failure_reason→reason, user_id→sys_user_id
--        - 移除: device / os / browser / country / province / city
--                (由 os_name/version + browser_name/version + location 替代)
--        - 放宽: user_agent VARCHAR(512)→TEXT
--   5. api_log_archive / sys_login_log_archive: 同步与热表同结构
-- v8 (仅 dict_data):
--   1. dict_data: 重新加 platform VARCHAR(32) NOT NULL DEFAULT 'general'(字典项归属平台;
--        与前端的 VITE_APP_PLATFORM 配合做"前端只看自己+通用"过滤;enum={general,react-admin,vue-admin})
--   2. dict_data: 加 idx_dict_data_platform 索引
--   注: dict_type 保持 v7(无 platform);v6→v7→v8 形成"加 → 删 → 加"的明确取舍记录
-- v9 (仅 dict_data):
--   1. dict_data: 加 tag_type VARCHAR(32) NOT NULL DEFAULT 'default'(预设样式标识;
--        default=无样式;前端按标识映射 ant Tag 颜色 / vben Tag color;
--        enum={default,primary,success,warning,error,processing,magenta,red,
--              volcano,orange,gold,lime,green,cyan,blue,geekblue,purple})
-- v10 (仅 dict_data):
--   1. dict_data: UNIQUE 由 (type_id, value, deleted_at) 改为
--        (type_id, value, platform, deleted_at)
--        — 支持同类型同 value 在不同 platform 各有一条活跃行(配合 v8 平台过滤)
-- v11 (仅 sys_blacklist):
--   1. 新增核心表 sys_blacklist：多态 target(IP/USER/DEVICE) + scope(LOGIN/API/ALL)
--        + 生效窗 starts_at/expires_at；弱唯一含 deleted_at；Flyway V1 同步本表
-- v12 (仅 sys_user):
--   1. sys_user: 增加 account_expires_at TIMESTAMP NULL（NULL=永不过期;不含边界;
--        与 is_enabled/Soft Delete/Blacklist 正交；到期拒登录并踢会话）
-- v13 (仅 sys_material):
--   1. 新增核心表 sys_material：多类型素材(IMAGE/VIDEO/AUDIO/DOCUMENT/OTHER)
--        + storage_type；文件细节进 metadata JSON；不存二进制；无业务 UNIQUE
-- v14 (仅 sys_material):
--   1. sys_material: 加 target_type / target_id（多态归属;GENERAL 时 target_id=0;
--        SYS_USER/DEPT 为预留;不建 FK）
-- v15 (仅 sys_blacklist):
--   1. sys_blacklist.target_type：USER → SYS_USER（对齐表名 sys_user；与素材归属枚举一致）
-- v16 (仅 sys_material):
--   1. sys_material.storage_type：LOCAL / OSS / COS / S3 → LOCAL / S3 / DB
--   2. sys_material: 加 content TEXT（DB=正文/文件体文本；LOCAL/S3=对象地址）
-- v17 (仅 sys_pay_method):
--   1. 新增核心表 sys_pay_method：支付/提现方式配置
--        scene=PAY/WITHDRAW/BOTH；channel=ALIPAY/WECHAT/BANK/CRYPTO/OTHER
--        通道专属进 metadata JSON（与 sys_material.metadata 同名约定）；UNIQUE(code, deleted_at)
-- v18 (sys_pay_bill / sys_withdraw_bill):
--   1. 新增账单表 sys_pay_bill：支付账单；UNIQUE(bill_no)（非软删）
--   2. 新增账单表 sys_withdraw_bill：提现账单；UNIQUE(bill_no)（非软删）
--        金额 DECIMAL(18,2)；pay_method_id / user_id 软引用不建 FK；通道快照落列；其余进 metadata
--        无 is_enabled / deleted_at / created_by / updated_by；生命周期走 status
-- v19 (套餐 + 账单来源):
--   1. 新增核心表 sys_recharge_package / sys_withdraw_package：充值/提现套餐；UNIQUE(code, deleted_at)
--   2. 账单加 source + package_id
--        source=ADMIN 后台调账(package_id 必须为 0)
--        source=RECHARGE/WITHDRAW 用户套餐单(package_id 软引用对应套餐;0=未选套餐)
-- v20 (日志与失败原因不截断):
--   1. temporal_task_execution.failure_reason: VARCHAR(1024)→TEXT(失败原因全文)
--   2. api_log.reason / api_log_archive.reason: VARCHAR(255)→TEXT(异常消息全文)
--   3. api_log.request_body / response: 取消「应用层截断 64KB」约定(列本就是 MEDIUMTEXT)
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET SQL_MODE = 'STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION';


-- ============================================================
-- Section 1: casbin_rule
-- 与 casbin/mysql-adapter v2 完全兼容;admin 业务代码不直接读写
-- ============================================================
CREATE TABLE casbin_rule (
    id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ptype VARCHAR(255)    NOT NULL,
    v0    VARCHAR(255)    DEFAULT NULL,
    v1    VARCHAR(255)    DEFAULT NULL,
    v2    VARCHAR(255)    DEFAULT NULL,
    v3    VARCHAR(255)    DEFAULT NULL,
    v4    VARCHAR(255)    DEFAULT NULL,
    v5    VARCHAR(255)    DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_casbin_rule_ptype_v0_v1 (ptype, v0, v1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Casbin policy 存储（casbin/mysql-adapter v2 标准表）';


-- ============================================================
-- Section 2: RBAC 核心 — sys_user
-- v2: 去 status(改 is_enabled);加 language_code;加 remark;UNIQUE 软删感知
-- v5: 移除 dept_id(原为 DEPT 类数据权限锚点;现由 sys_data_permission 承担,详见 v4 NULL 策略注释)
-- v12: 增加 account_expires_at（可选账号过期；NULL=永不过期）
-- ============================================================
CREATE TABLE sys_user (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username        VARCHAR(64)     NOT NULL  COMMENT '登录名',
    password_hash   VARCHAR(128)    NOT NULL  COMMENT '密码哈希(bcrypt/argon2 输出)',
    nickname        VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '展示名',
    email           VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '邮箱',
    phone           VARCHAR(32)     NOT NULL DEFAULT ''  COMMENT '手机号',
    avatar          VARCHAR(255)    NOT NULL DEFAULT ''  COMMENT '头像 URL',
    language_code   VARCHAR(16)     DEFAULT NULL  COMMENT '用户默认语言(软外键 → i18n_locale.code)',
    last_login_at   TIMESTAMP       NULL DEFAULT NULL  COMMENT '最近登录时间',
    last_login_ip   VARCHAR(45)     NOT NULL DEFAULT ''  COMMENT '最近登录 IP(IPv6 兼容)',
    account_expires_at TIMESTAMP    NULL DEFAULT NULL  COMMENT '账号过期时间(NULL=永不过期;到期后不可登录且踢会话;不含边界)',
    remark          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '管理员备注',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1  COMMENT '启用/禁用(独立于 deleted_at;三态:已删/禁用/正常)',
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻(应用层写 UNIX_TIMESTAMP()*1000)',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_sys_user_username (username, deleted_at),
    INDEX idx_sys_user_is_enabled (is_enabled),
    INDEX idx_sys_user_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户表';


-- ============================================================
-- Section 3: RBAC 核心 — sys_role
-- v2: 加 parent_id(自引用,角色层级);加 is_enabled/remark;UNIQUE 软删感知
-- v5: 移除 description(只保留 remark 统一语义)
-- ============================================================
CREATE TABLE sys_role (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code            VARCHAR(32)     NOT NULL  COMMENT '角色编码(如 admin/user/viewer)',
    name            VARCHAR(64)     NOT NULL  COMMENT '角色名(展示用)',
    parent_id       BIGINT UNSIGNED DEFAULT NULL  COMMENT '父角色 ID(自引用;支持角色层级继承)',
    sort            INT             NOT NULL DEFAULT 0  COMMENT '排序',
    remark          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '管理员备注',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_sys_role_code (code, deleted_at),
    INDEX idx_sys_role_parent_id (parent_id),
    INDEX idx_sys_role_is_enabled (is_enabled),
    INDEX idx_sys_role_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='角色表(支持父子层级继承)';


-- ============================================================
-- Section 4: API 管理 — sys_api
-- v2: 加 remark;UNIQUE 软删感知(method+path 与 permission_code)
-- v5: 移除 description(只保留 remark 统一语义)
-- ============================================================
CREATE TABLE sys_api (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name            VARCHAR(64)     NOT NULL  COMMENT '接口名(展示用)',
    method          VARCHAR(8)      NOT NULL  COMMENT 'HTTP method: GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD',
    path            VARCHAR(255)    NOT NULL  COMMENT '接口路径(支持 :id 占位,不含 host)',
    permission_code VARCHAR(128)    NOT NULL  COMMENT '权限码(与按钮权限码同构,后端 Casbin 鉴权 + 前端按钮控制)',
    api_group       VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '分组(便于管理后台分组展示)',
    remark          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '管理员备注',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_sys_api_method_path (method, path, deleted_at),
    UNIQUE KEY uniq_sys_api_permission_code (permission_code, deleted_at),
    INDEX idx_sys_api_group (api_group),
    INDEX idx_sys_api_is_enabled (is_enabled),
    INDEX idx_sys_api_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='API/接口管理(HTTP 路由 + 权限码)';


-- ============================================================
-- Section 5: 菜单管理 — sys_menu
-- v2: 加 tree_path(物化路径);加 metadata(前端扩展字段);加 remark
-- ============================================================
CREATE TABLE sys_menu (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_id       BIGINT UNSIGNED DEFAULT NULL  COMMENT '父菜单 ID(自引用,NULL=根)',
    name            VARCHAR(64)     NOT NULL  COMMENT '菜单名(展示用)',
    type            VARCHAR(16)     NOT NULL  COMMENT '类型: DIR=目录 / MENU=菜单/路由 / BUTTON=按钮',
    path            VARCHAR(255)    DEFAULT NULL  COMMENT '路由路径(仅 MENU 类型;BUTTON/DIR 不需要)',
    component       VARCHAR(255)    DEFAULT NULL  COMMENT '前端组件路径(仅 MENU 类型)',
    icon            VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '图标(前端展示)',
    redirect        VARCHAR(255)    NOT NULL DEFAULT ''  COMMENT '路由重定向(仅 MENU 类型;vue-vben-admin 习惯)',
    permission_code VARCHAR(128)    DEFAULT NULL  COMMENT '权限码;BUTTON 类型必填,MENU/DIR 可空(NULL=无需权限码)',
    tree_path       VARCHAR(1024)   DEFAULT NULL  COMMENT '物化路径(如 /1/3/7/),便于查祖先/子树(应用层在 INSERT 时维护)',
    metadata        JSON            DEFAULT NULL  COMMENT '前端扩展字段(badge / hideInBreadcrumb / keepAlive 等)',
    sort            INT             NOT NULL DEFAULT 0  COMMENT '同级排序',
    is_hidden       TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '是否隐藏(仅前端控制)',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    remark          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '管理员备注',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    INDEX idx_sys_menu_parent_id (parent_id),
    INDEX idx_sys_menu_tree_path (tree_path),
    INDEX idx_sys_menu_permission_code (permission_code),
    INDEX idx_sys_menu_type (type),
    INDEX idx_sys_menu_is_enabled (is_enabled),
    INDEX idx_sys_menu_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='菜单表(树形 + 物化路径 + 按钮级权限码)';


-- ============================================================
-- Section 6: I18n — i18n_locale
-- v2: 加 remark;UNIQUE 软删感知
-- ============================================================
CREATE TABLE i18n_locale (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code            VARCHAR(16)     NOT NULL  COMMENT '语言/区域代码(如 zh-CN / en-US)',
    name            VARCHAR(64)     NOT NULL  COMMENT '展示名(如 简体中文 / English)',
    is_default      TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '是否默认语言(应用层保证最多一条)',
    sort            INT             NOT NULL DEFAULT 0,
    remark          VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_i18n_locale_code (code, deleted_at),
    INDEX idx_i18n_locale_is_enabled (is_enabled),
    INDEX idx_i18n_locale_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='I18n 语言/区域';


-- ============================================================
-- Section 7: 字典 — dict_type
-- v2: 加 remark;UNIQUE 软删感知
-- v5: 移除 description(只保留 remark 统一语义)
-- v6: 加 platform(字典类型归属平台)
-- v7: 移除 platform(字典域回归无平台归属)
-- ============================================================
CREATE TABLE dict_type (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code            VARCHAR(64)     NOT NULL  COMMENT '字典类型编码(如 user_status)',
    name            VARCHAR(64)     NOT NULL  COMMENT '字典类型名(展示用)',
    remark          VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_dict_type_code (code, deleted_at),
    INDEX idx_dict_type_is_enabled (is_enabled),
    INDEX idx_dict_type_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='字典类型';


-- ============================================================
-- Section 8: 任务调度 — temporal_task_config
-- v2: 加 remark;UNIQUE 软删感知
-- v5: 移除 description(只保留 remark 统一语义)
-- ============================================================
CREATE TABLE temporal_task_config (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code            VARCHAR(64)     NOT NULL  COMMENT '任务编码(如 report_daily)',
    name            VARCHAR(128)    NOT NULL  COMMENT '任务名(展示用)',
    workflow_type   VARCHAR(128)    NOT NULL  COMMENT 'Temporal workflow 类名',
    task_queue      VARCHAR(128)    NOT NULL  COMMENT 'Temporal task queue',
    cron_expr       VARCHAR(64)     DEFAULT NULL  COMMENT 'cron 表达式(NULL=仅手动触发)',
    retry_policy    JSON            DEFAULT NULL  COMMENT '重试策略 JSON(最大尝试/初始间隔/退避系数等)',
    timeout_seconds INT UNSIGNED    DEFAULT NULL  COMMENT '超时(秒)',
    remark          VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_temporal_task_config_code (code, deleted_at),
    INDEX idx_temporal_task_config_is_enabled (is_enabled),
    INDEX idx_temporal_task_config_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Temporal 任务调度配置(workflow/activity 注册)';


-- ============================================================
-- Section 9: I18n 翻译 — i18n_translation (FK → i18n_locale)
-- v2: 加 remark;UNIQUE 软删感知
-- ============================================================
CREATE TABLE i18n_translation (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    locale_id       BIGINT UNSIGNED NOT NULL  COMMENT '所属语言',
    translation_key VARCHAR(255)    NOT NULL  COMMENT '翻译键(如 menu.user.create)',
    value           TEXT            NOT NULL  COMMENT '翻译值',
    remark          VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_i18n_translation_locale_key (locale_id, translation_key, deleted_at),
    INDEX idx_i18n_translation_key (translation_key),
    INDEX idx_i18n_translation_deleted_at (deleted_at),
    CONSTRAINT fk_i18n_translation_locale_id FOREIGN KEY (locale_id) REFERENCES i18n_locale (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='I18n 翻译(UI 字符串键值对)';


-- ============================================================
-- Section 10: 字典数据 — dict_data (FK → dict_type)
-- v2: 加 remark;UNIQUE 软删感知
-- v7: 跟随 dict_type 移除 platform 注释
-- v8: 重新加 platform(字典项归属平台;general = 跨平台通用)
-- v9: 加 tag_type(预设样式标识;default=无样式;前端按标识映射 ant Tag 颜色 / vben Tag color)
-- v10: UNIQUE 纳入 platform — 同 (type_id, value) 可在不同 platform 各有一条活跃行
-- ============================================================
CREATE TABLE dict_data (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    type_id         BIGINT UNSIGNED NOT NULL  COMMENT '所属字典类型',
    value           VARCHAR(64)     NOT NULL  COMMENT '字典值',
    label           VARCHAR(128)    NOT NULL  COMMENT '字典标签(展示用)',
    sort            INT             NOT NULL DEFAULT 0  COMMENT '同类型内排序',
    is_default      TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '是否该类型的默认值',
    platform        VARCHAR(32)     NOT NULL DEFAULT 'general'  COMMENT '归属平台(general=通用 / react-admin / vue-admin)',
    tag_type        VARCHAR(32)     NOT NULL DEFAULT 'default'  COMMENT '预设样式标识(default=无样式;前端按标识映射 ant Tag 颜色 / vben Tag color;enum={default,primary,success,warning,error,processing,magenta,red,volcano,orange,gold,lime,green,cyan,blue,geekblue,purple})',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    remark          VARCHAR(512)    NOT NULL DEFAULT '',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_dict_data_type_value_platform (type_id, value, platform, deleted_at),
    INDEX idx_dict_data_type_sort (type_id, sort),
    INDEX idx_dict_data_platform (platform),
    INDEX idx_dict_data_is_enabled (is_enabled),
    INDEX idx_dict_data_deleted_at (deleted_at),
    CONSTRAINT fk_dict_data_type_id FOREIGN KEY (type_id) REFERENCES dict_type (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='字典数据项';


-- ============================================================
-- Section 11: RBAC 关联 — sys_user_role (v2 不变)
-- ============================================================
CREATE TABLE sys_user_role (
    user_id         BIGINT UNSIGNED NOT NULL,
    role_id         BIGINT UNSIGNED NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    INDEX idx_sys_user_role_role_id (role_id),
    CONSTRAINT fk_sys_user_role_user_id FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_user_role_role_id FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户-角色关联(无软删,无 updated_at/updated_by)';


-- ============================================================
-- Section 12: RBAC 关联 — sys_role_api (v2 不变)
-- ============================================================
CREATE TABLE sys_role_api (
    role_id         BIGINT UNSIGNED NOT NULL,
    api_id          BIGINT UNSIGNED NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, api_id),
    INDEX idx_sys_role_api_api_id (api_id),
    CONSTRAINT fk_sys_role_api_role_id FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_sys_role_api_api_id  FOREIGN KEY (api_id)  REFERENCES sys_api (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='角色-API 授权关联(无软删)';


-- ============================================================
-- Section 13: RBAC 关联 — sys_role_menu (v2 不变)
-- ============================================================
CREATE TABLE sys_role_menu (
    role_id         BIGINT UNSIGNED NOT NULL,
    menu_id         BIGINT UNSIGNED NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, menu_id),
    INDEX idx_sys_role_menu_menu_id (menu_id),
    CONSTRAINT fk_sys_role_menu_role_id FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_sys_role_menu_menu_id FOREIGN KEY (menu_id) REFERENCES sys_menu (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='角色-菜单授权关联(无软删)';


-- ============================================================
-- Section 14: 菜单-API 快捷绑定 — sys_menu_api (v2 不变)
-- ============================================================
CREATE TABLE sys_menu_api (
    menu_id         BIGINT UNSIGNED NOT NULL,
    api_id          BIGINT UNSIGNED NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (menu_id, api_id),
    INDEX idx_sys_menu_api_api_id (api_id),
    CONSTRAINT fk_sys_menu_api_menu_id FOREIGN KEY (menu_id) REFERENCES sys_menu (id),
    CONSTRAINT fk_sys_menu_api_api_id  FOREIGN KEY (api_id)  REFERENCES sys_api (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='菜单-API 快捷绑定(非授权,便于按菜单批量赋权)';


-- ============================================================
-- Section 15: ABAC 数据权限 — sys_data_permission (v2 不变)
-- ============================================================
CREATE TABLE sys_data_permission (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    -- 主体多态
    subject_type    VARCHAR(16)     NOT NULL  COMMENT '主体类型(USER/ROLE/ANY_USER/ANY_ROLE)',
    subject_id      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '主体 ID;ANY_* 时为 0',

    -- 资源
    resource_table  VARCHAR(32)     NOT NULL  COMMENT '资源表名(如 orders/users)',

    -- 动作(JSON 数组 + 规范化字符串)
    action          JSON            NOT NULL  COMMENT '操作列表(如 ["read","write"])',
    action_key      VARCHAR(64)     NOT NULL DEFAULT 'read'
                                    COMMENT 'action 排序后拼接(如 "read,write"),用于唯一约束',

    -- 作用域
    scope_type      VARCHAR(32)     NOT NULL DEFAULT 'none'
                                    COMMENT '作用域类型(all/none/include/exclude/custom)',
    scope_field     VARCHAR(64)     NOT NULL DEFAULT 'id'
                                    COMMENT '用于匹配 scope_values 的字段',
    scope_values    JSON            NOT NULL  COMMENT '作用域值列表',

    -- 行过滤条件(自由 JSON)
    conditions      JSON            NOT NULL  COMMENT '行过滤条件(K=V map,应用层解释)',

    -- 冲突优先级
    priority        INT             NOT NULL DEFAULT 0  COMMENT '多主体冲突时的优先级(降序)',

    -- 备注/启停
    remark          VARCHAR(512)    NOT NULL DEFAULT '',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',

    PRIMARY KEY (id),

    -- 软删感知唯一:同 (主体,资源,动作) 至多一条"未删"行(deleted_at=0)
    UNIQUE KEY uniq_sys_data_permission_subject_resource_action_active
        (subject_type, subject_id, resource_table, action_key, deleted_at),

    -- 高频查询索引
    INDEX idx_sys_data_permission_subject (subject_type, subject_id),
    INDEX idx_sys_data_permission_subject_resource (subject_type, subject_id, resource_table),
    INDEX idx_sys_data_permission_resource (resource_table),
    INDEX idx_sys_data_permission_is_enabled (is_enabled),
    INDEX idx_sys_data_permission_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='ABAC 数据权限(主体多态 + 多 action + 多 scope,行级授权)';


-- ============================================================
-- Section 16: 访问黑名单 — sys_blacklist (v11)
-- 多态 target(IP/SYS_USER/DEVICE) + scope(LOGIN/API/ALL) + 生效窗
-- 命中语义: deleted_at=0 AND is_enabled=1 AND starts_at<=NOW()
--           AND (expires_at IS NULL OR expires_at>NOW())
--           AND scope IN (请求场景, 'ALL')；多行 OR
-- 弱唯一: 防「同 target+scope+完全相同时间窗」重复行；区间重叠不由 DB 约束
-- MySQL 注意: UNIQUE 中 expires_at=NULL 时多行可并存(NULL 互不等)，应用层需防永久窗重复
-- ============================================================
CREATE TABLE sys_blacklist (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    target_type     VARCHAR(16)     NOT NULL  COMMENT '目标类型: IP / SYS_USER / DEVICE',
    target_value    VARCHAR(128)    NOT NULL  COMMENT '目标值(IP 文本; SYS_USER=sys_user.id 十进制字符串软引用; DEVICE=客户端 deviceId 原样)',
    scope           VARCHAR(16)     NOT NULL DEFAULT 'ALL'
                                    COMMENT '限制范围: LOGIN=仅登录 / API=仅已认证 API / ALL=全部(命中时 scope IN (场景, ALL))',
    reason          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '封禁原因(对用户/审计可见;可空串)',
    starts_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    COMMENT '生效开始时间(含)',
    expires_at      TIMESTAMP       NULL DEFAULT NULL
                                    COMMENT '生效结束时间(不含;NULL=永不过期)',
    remark          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '管理员内部备注',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1  COMMENT '启用/禁用(独立于 deleted_at 与时间窗)',
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    -- 弱唯一: 同 (target, scope, 完全相同时间窗) 活跃行至多一条; 区间重叠不检测
    UNIQUE KEY uniq_sys_blacklist_target_scope_window
        (target_type, target_value, scope, starts_at, expires_at, deleted_at),
    INDEX idx_sys_blacklist_target (target_type, target_value),
    INDEX idx_sys_blacklist_expires_at (expires_at),
    INDEX idx_sys_blacklist_is_enabled (is_enabled),
    INDEX idx_sys_blacklist_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='访问黑名单(多态 target + scope + 时间窗;运行时拦截本波未实现)';


-- ============================================================
-- Section 16b: 素材库 — sys_material (v13; v14 加归属; v16 storage_type + content)
-- 多类型共用一张表; type 区分形态; 文件细节与类型扩展一律进 metadata
-- LOCAL/S3: content 存对象地址;细节仍进 metadata
-- DB: content 存正文/文件体文本
-- 归属: target_type + target_id(多态软引用;GENERAL 时 target_id=0;SYS_USER/DEPT 预留)
-- 无业务 UNIQUE: 无稳定自然键(name / metadata.url / metadata.checksum 均可空或重复)
-- ============================================================
CREATE TABLE sys_material (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name            VARCHAR(128)    NOT NULL  COMMENT '素材展示名',
    type            VARCHAR(32)     NOT NULL  COMMENT '素材类型: IMAGE / VIDEO / AUDIO / DOCUMENT / OTHER',
    target_type     VARCHAR(32)     NOT NULL DEFAULT 'GENERAL'
                                    COMMENT '归属类型: GENERAL / SYS_USER / DEPT;GENERAL 时 target_id 必须为 0',
    target_id       BIGINT UNSIGNED NOT NULL DEFAULT 0
                                    COMMENT '归属 ID;GENERAL=0;SYS_USER=sys_user.id;DEPT=预留部门 id',
    storage_type    VARCHAR(32)     NOT NULL DEFAULT 'LOCAL'
                                    COMMENT '存储: LOCAL / S3 / DB',
    content         TEXT            NOT NULL DEFAULT ''
                                    COMMENT 'DB=正文/文件体文本;LOCAL/S3=对象地址(路径或 URL)',
    metadata        JSON            DEFAULT NULL
                                    COMMENT '文件与类型扩展: mime_type/file_ext/original_name/storage_key/url/size_bytes/width/height/duration_ms/checksum 等;无则为 NULL',
    sort            INT             NOT NULL DEFAULT 0  COMMENT '排序(升序)',
    remark          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '管理员备注',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1  COMMENT '启用/禁用',
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    INDEX idx_sys_material_type (type),
    INDEX idx_sys_material_target (target_type, target_id),
    INDEX idx_sys_material_is_enabled (is_enabled),
    INDEX idx_sys_material_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='素材库(多类型元数据+存储定位+多态归属;DB 时 content 存文本体;v16)';


-- ============================================================
-- Section 16c: 支付方式配置 — sys_pay_method (v17)
-- 一张表覆盖支付与提现; scene 区分方向; channel 区分通道类型
-- 实例以 code 为自然键(软删感知 UNIQUE); 同通道可多实例(alipay_app / alipay_backup)
-- 通道专属(商户号/密钥引用/回调/费率/限额/币种)一律进 metadata; 不按通道拆列
-- 密钥只存 secret_ref 或应用层密文,不在约定里鼓励明文
-- ============================================================
CREATE TABLE sys_pay_method (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code            VARCHAR(32)     NOT NULL  COMMENT '实例编码(如 alipay_app / wechat_native / bank_card);软删感知唯一',
    name            VARCHAR(64)     NOT NULL  COMMENT '展示名',
    scene           VARCHAR(16)     NOT NULL DEFAULT 'BOTH'
                                    COMMENT '场景: PAY=仅支付 / WITHDRAW=仅提现 / BOTH=两者',
    channel         VARCHAR(32)     NOT NULL
                                    COMMENT '通道类型: ALIPAY / WECHAT / BANK / CRYPTO / OTHER',
    icon            VARCHAR(255)    NOT NULL DEFAULT ''
                                    COMMENT '展示图标(URL 或对象地址;可空串)',
    metadata        JSON            DEFAULT NULL
                                    COMMENT '通道扩展: merchant_id/app_id/secret_ref/notify_url/return_url/fee_rate/min_amount/max_amount/daily_limit/currency 等;无则为 NULL',
    sort            INT             NOT NULL DEFAULT 0  COMMENT '排序(升序)',
    remark          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '管理员备注',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1  COMMENT '启用/禁用',
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_sys_pay_method_code (code, deleted_at),
    INDEX idx_sys_pay_method_channel_scene (channel, scene),
    INDEX idx_sys_pay_method_is_enabled (is_enabled),
    INDEX idx_sys_pay_method_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='支付/提现方式配置(通道差异进 metadata;运行时接入本波未实现)';


-- ============================================================
-- Section 16c2: 充值套餐 — sys_recharge_package (v19)
-- 用户充值档位; 后台调账不走本表
-- pay_amount=实付; grant_amount=到账; bonus_amount=赠送(展示/对账;等式由应用层维护)
-- ============================================================
CREATE TABLE sys_recharge_package (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code            VARCHAR(32)     NOT NULL  COMMENT '套餐编码(如 recharge_100);软删感知唯一',
    name            VARCHAR(64)     NOT NULL  COMMENT '展示名',
    pay_amount      DECIMAL(18,2)   NOT NULL  COMMENT '用户实付',
    grant_amount    DECIMAL(18,2)   NOT NULL  COMMENT '到账金额',
    bonus_amount    DECIMAL(18,2)   NOT NULL DEFAULT 0.00  COMMENT '赠送金额(一般为 grant_amount-pay_amount)',
    currency        VARCHAR(16)     NOT NULL DEFAULT 'CNY'  COMMENT '币种',
    icon            VARCHAR(255)    NOT NULL DEFAULT ''  COMMENT '展示图标(可空串)',
    sort            INT             NOT NULL DEFAULT 0  COMMENT '排序(升序)',
    metadata        JSON            DEFAULT NULL
                                    COMMENT '扩展: tag/first_only/limit_per_user 等;无则为 NULL',
    remark          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '管理员备注',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1  COMMENT '启用/禁用',
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_sys_recharge_package_code (code, deleted_at),
    INDEX idx_sys_recharge_package_is_enabled (is_enabled),
    INDEX idx_sys_recharge_package_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='充值套餐(仅用户充值档位;后台调账不走本表)';


-- ============================================================
-- Section 16c3: 提现套餐 — sys_withdraw_package (v19)
-- 用户提现档位; 后台调账/人工出金不走本表
-- amount=申请额; fee_amount=手续费; actual_amount=到账(等式由应用层维护)
-- ============================================================
CREATE TABLE sys_withdraw_package (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code            VARCHAR(32)     NOT NULL  COMMENT '套餐编码(如 withdraw_100);软删感知唯一',
    name            VARCHAR(64)     NOT NULL  COMMENT '展示名',
    amount          DECIMAL(18,2)   NOT NULL  COMMENT '提现申请额',
    fee_amount      DECIMAL(18,2)   NOT NULL DEFAULT 0.00  COMMENT '手续费',
    actual_amount   DECIMAL(18,2)   NOT NULL  COMMENT '实际到账(一般为 amount-fee_amount)',
    currency        VARCHAR(16)     NOT NULL DEFAULT 'CNY'  COMMENT '币种',
    icon            VARCHAR(255)    NOT NULL DEFAULT ''  COMMENT '展示图标(可空串)',
    sort            INT             NOT NULL DEFAULT 0  COMMENT '排序(升序)',
    metadata        JSON            DEFAULT NULL
                                    COMMENT '扩展: min_vip/daily_limit 等;无则为 NULL',
    remark          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '管理员备注',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1  COMMENT '启用/禁用',
    deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
    updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_sys_withdraw_package_code (code, deleted_at),
    INDEX idx_sys_withdraw_package_is_enabled (is_enabled),
    INDEX idx_sys_withdraw_package_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='提现套餐(仅用户提现档位;后台调账不走本表)';


-- ============================================================
-- Section 16d: 支付账单 — sys_pay_bill (v18; v19 加 source/package_id)
-- 一笔支付/充值一单; bill_no 为自然键(硬 UNIQUE,无软删)
-- 非核心表: 无 is_enabled / deleted_at / created_by / updated_by
-- 生命周期走 status; 关单/失败不物理删; 通道回包/扩展进 metadata
-- source=ADMIN 后台调账(package_id 必须 0); source=RECHARGE 用户充值(package_id→sys_recharge_package)
-- ============================================================
CREATE TABLE sys_pay_bill (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    bill_no         VARCHAR(64)     NOT NULL  COMMENT '账单号(应用层生成;全局唯一)',
    source          VARCHAR(16)     NOT NULL
                                    COMMENT '来源: ADMIN=后台调账 / RECHARGE=用户充值套餐',
    user_id         BIGINT UNSIGNED NOT NULL DEFAULT 0
                                    COMMENT '业务用户 ID(软引用;本波不绑定具体用户表;0=未知)',
    pay_method_id   BIGINT UNSIGNED NOT NULL DEFAULT 0
                                    COMMENT '支付方式(软引用 sys_pay_method.id;0=未关联;ADMIN 常为 0)',
    package_id      BIGINT UNSIGNED NOT NULL DEFAULT 0
                                    COMMENT '充值套餐(软引用 sys_recharge_package.id;ADMIN 必须为 0;RECHARGE 选套餐时>0)',
    channel         VARCHAR(32)     NOT NULL
                                    COMMENT '下单时通道快照: ALIPAY / WECHAT / BANK / CRYPTO / OTHER;ADMIN 可用 OTHER',
    title           VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '账单标题/商品摘要',
    amount          DECIMAL(18,2)   NOT NULL  COMMENT '应付/调账金额(精确小数;禁止 FLOAT)',
    currency        VARCHAR(16)     NOT NULL DEFAULT 'CNY'  COMMENT '币种(ISO 4217 或约定码)',
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING'
                                    COMMENT 'PENDING / PAYING / SUCCESS / FAILED / CLOSED / REFUNDED',
    third_trade_no  VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '第三方交易号(回调对账;可空串;ADMIN 常为空串)',
    paid_at         TIMESTAMP       NULL DEFAULT NULL  COMMENT '支付成功时刻;NULL=未成功',
    expired_at      TIMESTAMP       NULL DEFAULT NULL  COMMENT '支付过期时刻;NULL=未设过期',
    metadata        JSON            DEFAULT NULL
                                    COMMENT '回包与扩展: method_code/package_code/grant_amount/notify_payload 等;无则为 NULL',
    remark          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '管理员备注',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_sys_pay_bill_bill_no (bill_no),
    INDEX idx_sys_pay_bill_user_id_created_at (user_id, created_at),
    INDEX idx_sys_pay_bill_source_status_created_at (source, status, created_at),
    INDEX idx_sys_pay_bill_package_id (package_id),
    INDEX idx_sys_pay_bill_third_trade_no (third_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='支付账单(ADMIN 调账 vs RECHARGE 套餐;无软删;运行时接入本波未实现)';


-- ============================================================
-- Section 16e: 提现账单 — sys_withdraw_bill (v18; v19 加 source/package_id)
-- 一笔提现/出款一单; bill_no 为自然键(硬 UNIQUE,无软删)
-- 非核心表: 无 is_enabled / deleted_at / created_by / updated_by
-- source=ADMIN 后台出金(package_id 必须 0); source=WITHDRAW 用户提现(package_id→sys_withdraw_package)
-- 收款户名/账号落列便于列表; 审核人软引用 sys_user; 其余进 metadata
-- ============================================================
CREATE TABLE sys_withdraw_bill (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    bill_no         VARCHAR(64)     NOT NULL  COMMENT '账单号(应用层生成;全局唯一)',
    source          VARCHAR(16)     NOT NULL
                                    COMMENT '来源: ADMIN=后台出金 / WITHDRAW=用户提现套餐',
    user_id         BIGINT UNSIGNED NOT NULL DEFAULT 0
                                    COMMENT '业务用户 ID(软引用;本波不绑定具体用户表;0=未知)',
    pay_method_id   BIGINT UNSIGNED NOT NULL DEFAULT 0
                                    COMMENT '提现方式(软引用 sys_pay_method.id;0=未关联;ADMIN 常为 0)',
    package_id      BIGINT UNSIGNED NOT NULL DEFAULT 0
                                    COMMENT '提现套餐(软引用 sys_withdraw_package.id;ADMIN 必须为 0;WITHDRAW 选套餐时>0)',
    channel         VARCHAR(32)     NOT NULL
                                    COMMENT '申请时通道快照: ALIPAY / WECHAT / BANK / CRYPTO / OTHER;ADMIN 可用 OTHER',
    amount          DECIMAL(18,2)   NOT NULL  COMMENT '申请金额(精确小数;禁止 FLOAT)',
    fee_amount      DECIMAL(18,2)   NOT NULL DEFAULT 0.00  COMMENT '手续费',
    actual_amount   DECIMAL(18,2)   NOT NULL  COMMENT '实际到账(一般为 amount-fee_amount)',
    currency        VARCHAR(16)     NOT NULL DEFAULT 'CNY'  COMMENT '币种(ISO 4217 或约定码)',
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING'
                                    COMMENT 'PENDING / APPROVED / REJECTED / PROCESSING / SUCCESS / FAILED / CANCELLED',
    account_name    VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '收款户名',
    account_no      VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '收款账号(卡号/钱包地址等)',
    third_trade_no  VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '第三方出款单号(可空串)',
    reject_reason   VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '拒绝原因(对用户可见;可空串)',
    reviewed_by     BIGINT UNSIGNED NOT NULL DEFAULT 0
                                    COMMENT '审核人(0=未审/系统;非0=软引用 sys_user.id)',
    reviewed_at     TIMESTAMP       NULL DEFAULT NULL  COMMENT '审核时刻;NULL=未审',
    finished_at     TIMESTAMP       NULL DEFAULT NULL  COMMENT '出款终态时刻(成功/失败);NULL=未结束',
    metadata        JSON            DEFAULT NULL
                                    COMMENT '扩展: method_code/bank_name/branch/notify_payload 等;无则为 NULL',
    remark          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '管理员备注',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_sys_withdraw_bill_bill_no (bill_no),
    INDEX idx_sys_withdraw_bill_user_id_created_at (user_id, created_at),
    INDEX idx_sys_withdraw_bill_source_status_created_at (source, status, created_at),
    INDEX idx_sys_withdraw_bill_package_id (package_id),
    INDEX idx_sys_withdraw_bill_third_trade_no (third_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='提现账单(ADMIN 出金 vs WITHDRAW 套餐;无软删;审核与出款本波未实现)';


-- ============================================================
-- Section 17: API 调用日志 — api_log (v5: 字段扩充对齐 PG sys_api_log)
-- request_id 全链路串联 api_log ↔ operation_log ↔ 链路追踪
-- 新增: 客户端指纹 / UA 解析 / IP 解析 / 变更前后 / 头信息
-- ============================================================
CREATE TABLE api_log (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    -- 调用结果
    method          VARCHAR(16)     NOT NULL  COMMENT 'HTTP method(GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD)',
    module          VARCHAR(255)    NOT NULL DEFAULT ''  COMMENT '业务模块(如 user/role/menu/order)',
    path            VARCHAR(255)    NOT NULL  COMMENT '请求路径(不含 query)',
    status_code     INT UNSIGNED    DEFAULT NULL  COMMENT 'HTTP 状态码(连接早期失败时可能未设置)',
    success         TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '业务级成功(由中间件按 status_code 判定,2xx=1)',
    reason          TEXT            NOT NULL DEFAULT ''  COMMENT '失败原因全文(不截断;无错时为空串)',
    cost_time       BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '耗时(毫秒)',

    -- 关联
    request_id      VARCHAR(128)    NOT NULL  COMMENT '请求唯一 ID(中间件生成;串联 api_log ↔ operation_log ↔ 链路追踪)',
    sys_user_id     BIGINT UNSIGNED DEFAULT NULL  COMMENT '操作用户(未登录请求为 NULL)',
    username        VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '冗余:请求时刻的用户名(避免 JOIN;未登录请求为空串)',

    -- 请求侧
    request_uri     TEXT            NOT NULL DEFAULT ''  COMMENT '完整 URI(含 query;便于回放)',
    request_query   TEXT            NOT NULL DEFAULT ('')  COMMENT 'query string',
    request_body    MEDIUMTEXT      NOT NULL DEFAULT ('')  COMMENT '请求 body(全文;敏感字段脱敏;不截断)',
    request_header  MEDIUMTEXT      NOT NULL DEFAULT ('')  COMMENT '请求头(应用层序列化,敏感字段脱敏后存储)',
    referer         VARCHAR(2048)   NOT NULL DEFAULT ''  COMMENT '来源页',

    -- 响应侧 / 变更
    response        MEDIUMTEXT      NOT NULL DEFAULT ('')  COMMENT '响应 body(全文;敏感字段脱敏;不截断)',
    before_change   MEDIUMTEXT      NOT NULL DEFAULT ('')  COMMENT '操作前数据快照(写操作场景;与应用层 before/after 钩子配合)',
    after_change    MEDIUMTEXT      NOT NULL DEFAULT ('')  COMMENT '操作后数据快照',
    format_change   TEXT            NOT NULL DEFAULT ''  COMMENT '格式化变更摘要(人读;如 "name: A→B;status: 0→1")',

    -- 客户端
    client_id       VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '客户端 ID(如 web-admin-vue3 / mobile-app-ios)',
    client_name     VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '客户端名(展示用)',
    client_ip       VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '客户端 IP(IPv6 兼容;PG 用 64 字符)',
    user_agent      TEXT            NOT NULL DEFAULT ''  COMMENT 'User Agent(完整;可能很长)',
    browser_name    VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '浏览器名(由 UA 解析)',
    browser_version VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '浏览器版本',
    os_name         VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '操作系统名(由 UA 解析)',
    os_version      VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '操作系统版本',
    location        VARCHAR(255)    NOT NULL DEFAULT ''  COMMENT 'IP 解析地理位置(应用层负责;格式可自由)',

    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uniq_api_log_request_id (request_id),
    INDEX idx_api_log_sys_user_id_created_at (sys_user_id, created_at),
    INDEX idx_api_log_module_created_at (module, created_at),
    INDEX idx_api_log_path_created_at (path, created_at),
    INDEX idx_api_log_status_code_created_at (status_code, created_at),
    INDEX idx_api_log_success_created_at (success, created_at),
    INDEX idx_api_log_client_ip_created_at (client_ip, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='API 调用日志(记录型,只增不改;含 UA/IP 解析与数据变更快照)';


-- ============================================================
-- Section 18: API 日志归档 — api_log_archive (v5: 与 api_log 同结构)
-- ============================================================
CREATE TABLE api_log_archive (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    method          VARCHAR(16)     NOT NULL,
    module          VARCHAR(255)    NOT NULL DEFAULT '',
    path            VARCHAR(255)    NOT NULL,
    status_code     INT UNSIGNED    DEFAULT NULL,
    success         TINYINT(1)      NOT NULL DEFAULT 0,
    reason          TEXT            NOT NULL DEFAULT '',
    cost_time       BIGINT UNSIGNED NOT NULL DEFAULT 0,

    request_id      VARCHAR(128)    NOT NULL,
    sys_user_id     BIGINT UNSIGNED DEFAULT NULL,
    username        VARCHAR(64)     NOT NULL DEFAULT '',

    request_uri     TEXT            NOT NULL DEFAULT '',
    request_query   TEXT            NOT NULL DEFAULT (''),
    request_body    MEDIUMTEXT      NOT NULL DEFAULT (''),
    request_header  MEDIUMTEXT      NOT NULL DEFAULT (''),
    referer         VARCHAR(2048)   NOT NULL DEFAULT '',

    response        MEDIUMTEXT      NOT NULL DEFAULT (''),
    before_change   MEDIUMTEXT      NOT NULL DEFAULT (''),
    after_change    MEDIUMTEXT      NOT NULL DEFAULT (''),
    format_change   TEXT            NOT NULL DEFAULT '',

    client_id       VARCHAR(128)    NOT NULL DEFAULT '',
    client_name     VARCHAR(128)    NOT NULL DEFAULT '',
    client_ip       VARCHAR(64)     NOT NULL DEFAULT '',
    user_agent      TEXT            NOT NULL DEFAULT '',
    browser_name    VARCHAR(128)    NOT NULL DEFAULT '',
    browser_version VARCHAR(128)    NOT NULL DEFAULT '',
    os_name         VARCHAR(128)    NOT NULL DEFAULT '',
    os_version      VARCHAR(128)    NOT NULL DEFAULT '',
    location        VARCHAR(255)    NOT NULL DEFAULT '',

    created_at      TIMESTAMP       NOT NULL  COMMENT '原始 created_at(便于跨表查询)',
    archived_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '归档时间',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_api_log_archive_request_id (request_id),
    INDEX idx_api_log_archive_sys_user_id_created_at (sys_user_id, created_at),
    INDEX idx_api_log_archive_module_created_at (module, created_at),
    INDEX idx_api_log_archive_path_created_at (path, created_at),
    INDEX idx_api_log_archive_status_code_created_at (status_code, created_at),
    INDEX idx_api_log_archive_success_created_at (success, created_at),
    INDEX idx_api_log_archive_client_ip_created_at (client_ip, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='API 日志归档(由 TTL 作业从 api_log 搬运;与热表同结构)';


-- ============================================================
-- Section 19: 登录日志 — sys_login_log (v5: 字段扩充对齐 PG sys_login_log)
-- 新增: MAC / UA 解析 / IP 解析 / 状态码 / 客户端指纹
-- 移除: device / os / browser / country / province / city
--       (由 os_name/version + browser_name/version + location + client_* 替代)
-- ============================================================
CREATE TABLE sys_login_log (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username        VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '登录用户名',
    success         TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '1=成功 0=失败',
    reason          VARCHAR(255)    NOT NULL DEFAULT ''  COMMENT '失败原因(若失败;成功时为空串;旧字段 failure_reason 改名为 reason 与 PG 对齐)',
    status_code     INT UNSIGNED    DEFAULT NULL  COMMENT 'HTTP 状态码(200=成功;其他=失败)',

    -- 关联
    sys_user_id     BIGINT UNSIGNED DEFAULT NULL  COMMENT '关联用户(登录成功后)',
    login_method    VARCHAR(32)     NOT NULL DEFAULT 'PASSWORD'
                                    COMMENT 'PASSWORD/SSO/OAUTH/SMS',

    -- 时间
    login_time      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    COMMENT '登录尝试时间(应用层可与 created_at 区分;异步上报时可能略晚)',

    -- 客户端
    login_ip        VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '登录 IP(IPv6 兼容)',
    login_mac       VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '登录 MAC(若有;CS 场景下多为空)',
    client_id       VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '客户端 ID',
    client_name     VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '客户端名',
    user_agent      TEXT            NOT NULL DEFAULT ''  COMMENT 'User Agent(完整)',
    browser_name    VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '浏览器名(由 UA 解析)',
    browser_version VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '浏览器版本',
    os_name         VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '操作系统名',
    os_version      VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '操作系统版本',
    location        VARCHAR(255)    NOT NULL DEFAULT ''  COMMENT 'IP 解析地理位置(应用层负责;格式可自由)',

    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_sys_login_log_username_created_at (username, created_at),
    INDEX idx_sys_login_log_success_created_at (success, created_at),
    INDEX idx_sys_login_log_sys_user_id (sys_user_id),
    INDEX idx_sys_login_log_login_ip_created_at (login_ip, created_at),
    INDEX idx_sys_login_log_login_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='登录日志(记录型,只增不改;含 UA/IP 解析与客户端指纹)';


-- ============================================================
-- Section 20: 登录日志归档 — sys_login_log_archive (v5: 与 sys_login_log 同结构)
-- ============================================================
CREATE TABLE sys_login_log_archive (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username        VARCHAR(64)     NOT NULL DEFAULT '',
    success         TINYINT(1)      NOT NULL DEFAULT 0,
    reason          VARCHAR(255)    NOT NULL DEFAULT '',
    status_code     INT UNSIGNED    DEFAULT NULL,

    sys_user_id     BIGINT UNSIGNED DEFAULT NULL,
    login_method    VARCHAR(32)     NOT NULL DEFAULT 'PASSWORD',

    login_time      TIMESTAMP       NOT NULL,

    login_ip        VARCHAR(64)     NOT NULL DEFAULT '',
    login_mac       VARCHAR(128)    NOT NULL DEFAULT '',
    client_id       VARCHAR(128)    NOT NULL DEFAULT '',
    client_name     VARCHAR(128)    NOT NULL DEFAULT '',
    user_agent      TEXT            NOT NULL DEFAULT '',
    browser_name    VARCHAR(128)    NOT NULL DEFAULT '',
    browser_version VARCHAR(128)    NOT NULL DEFAULT '',
    os_name         VARCHAR(128)    NOT NULL DEFAULT '',
    os_version      VARCHAR(128)    NOT NULL DEFAULT '',
    location        VARCHAR(255)    NOT NULL DEFAULT '',

    created_at      TIMESTAMP       NOT NULL,
    archived_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_sys_login_log_archive_username_created_at (username, created_at),
    INDEX idx_sys_login_log_archive_success_created_at (success, created_at),
    INDEX idx_sys_login_log_archive_sys_user_id (sys_user_id),
    INDEX idx_sys_login_log_archive_login_ip_created_at (login_ip, created_at),
    INDEX idx_sys_login_log_archive_login_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='登录日志归档(与热表同结构)';


-- ============================================================
-- Section 21: 操作日志 — operation_log (v2 不变)
-- ============================================================
CREATE TABLE operation_log (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED DEFAULT NULL  COMMENT '操作人(系统级操作为 NULL)',
    username        VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '冗余:操作时刻的用户名(系统级写为 system)',
    module          VARCHAR(64)     NOT NULL  COMMENT '业务模块(如 user/role/menu/dict)',
    action          VARCHAR(64)     NOT NULL  COMMENT '动作(create/update/delete/import/export/...)',
    target_id       BIGINT UNSIGNED DEFAULT NULL  COMMENT '被操作对象 ID',
    before_value    JSON            DEFAULT NULL  COMMENT '操作前数据快照(仅 UPDATE 有值)',
    after_value     JSON            DEFAULT NULL  COMMENT '操作后数据快照(仅 UPDATE 有值)',
    request_id      VARCHAR(64)     DEFAULT NULL  COMMENT '关联 api_log 的 request_id',
    source          VARCHAR(16)     NOT NULL DEFAULT 'AUTO'  COMMENT 'AUTO=AOP 拦截 / EXPLICIT=显式打标',
    remark          VARCHAR(512)    NOT NULL DEFAULT '',
    client_ip       VARCHAR(45)     NOT NULL DEFAULT '',
    user_agent      VARCHAR(512)    NOT NULL DEFAULT '',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_operation_log_user_id_created_at (user_id, created_at),
    INDEX idx_operation_log_module_action_created_at (module, action, created_at),
    INDEX idx_operation_log_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='操作日志(AOP 拦截 + 显式打标;记录型)';


-- ============================================================
-- Section 22: 操作日志归档 — operation_log_archive (v2 不变)
-- ============================================================
CREATE TABLE operation_log_archive (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED DEFAULT NULL,
    username        VARCHAR(64)     NOT NULL DEFAULT '',
    module          VARCHAR(64)     NOT NULL,
    action          VARCHAR(64)     NOT NULL,
    target_id       BIGINT UNSIGNED DEFAULT NULL,
    before_value    JSON            DEFAULT NULL,
    after_value     JSON            DEFAULT NULL,
    request_id      VARCHAR(64)     DEFAULT NULL,
    source          VARCHAR(16)     NOT NULL DEFAULT 'AUTO',
    remark          VARCHAR(512)    NOT NULL DEFAULT '',
    client_ip       VARCHAR(45)     NOT NULL DEFAULT '',
    user_agent      VARCHAR(512)    NOT NULL DEFAULT '',
    created_at      TIMESTAMP       NOT NULL,
    archived_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_operation_log_archive_user_id_created_at (user_id, created_at),
    INDEX idx_operation_log_archive_module_action_created_at (module, action, created_at),
    INDEX idx_operation_log_archive_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='操作日志归档';


-- ============================================================
-- Section 23: Temporal 执行记录 — temporal_task_execution (v2 不变)
-- ============================================================
CREATE TABLE temporal_task_execution (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    config_id       BIGINT UNSIGNED DEFAULT NULL  COMMENT '软外键 → temporal_task_config.id',
    workflow_id     VARCHAR(128)    NOT NULL  COMMENT 'Temporal 原生 workflow_id',
    run_id          VARCHAR(128)    NOT NULL  COMMENT 'Temporal 原生 run_id',
    workflow_type   VARCHAR(128)    NOT NULL,
    task_queue      VARCHAR(128)    NOT NULL,
    status          VARCHAR(32)     NOT NULL
                                    COMMENT 'PENDING/RUNNING/RETRYING/COMPLETED/FAILED/CANCELLED/TERMINATED/TIMED_OUT/CONTINUED_AS_NEW',
    pending_at      TIMESTAMP       NULL DEFAULT NULL  COMMENT '进入等待中(PENDING)的时间',
    started_at      TIMESTAMP       NULL DEFAULT NULL  COMMENT '真正运行开始时间(NULL=尚未真正运行)',
    closed_at       TIMESTAMP       NULL DEFAULT NULL  COMMENT '关闭时间(NULL=仍在运行/未启动)',
    input_summary   JSON            DEFAULT NULL  COMMENT '输入摘要(避免存大对象)',
    result_summary  JSON            DEFAULT NULL  COMMENT '结果摘要',
    failure_reason  TEXT            DEFAULT NULL  COMMENT '失败原因全文(不截断)',
    retry_count     INT             NOT NULL DEFAULT 0  COMMENT '已发生重试次数(首次执行为0)',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_temporal_task_execution_workflow_run (workflow_id, run_id),
    INDEX idx_temporal_task_execution_config_started_at (config_id, started_at),
    INDEX idx_temporal_task_execution_status_started_at (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Temporal 执行记录(应用层镜像,只存摘要)';


-- ============================================================
-- Section 24: Agent 平台（v20;对齐 Flyway V3__agent_schema.sql）
-- 模块: Agent 管理 / Skill 管理 / MCP 管理 / 会话控制面
-- 约定: 权威基线 = Flyway V3__agent_schema.sql;此处为可独立执行的合集镜像
-- ============================================================


-- ============================================================
-- Section 24a: Agent 定义与 Revision
-- ============================================================

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
-- Section A4b: 模型草稿与 Release
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


SET FOREIGN_KEY_CHECKS = 1;


-- ============================================================
-- Section 26: 自引用外键后置
-- CREATE TABLE 内无法引用自身,用 ALTER TABLE 补充
-- ============================================================
ALTER TABLE sys_menu
    ADD CONSTRAINT fk_sys_menu_parent_id
    FOREIGN KEY (parent_id) REFERENCES sys_menu (id);

ALTER TABLE sys_role
    ADD CONSTRAINT fk_sys_role_parent_id
    FOREIGN KEY (parent_id) REFERENCES sys_role (id);


-- ============================================================
-- End of schema.sql (v5 基线 + dict_data v8/v9/v10 + sys_blacklist v11 + sys_user.account_expires_at v12 + sys_material v13 + v14 target + v15 SYS_USER + v16 content + v17 sys_pay_method + v18 bills + v19 packages + v20 agent 平台)
-- ============================================================
