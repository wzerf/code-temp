/** vben 风格的 mock 接口类型 */

export interface LoginRequest {
  username: string;
  password: string;
  /** ALTCHA PoW payload（Base64 编码），登录前需通过人机校验 */
  altcha?: string;
}

export interface LoginResponse {
  id: number | string;
  username: string;
  realName: string;
  roles: string[];
  homePath?: string;
  accessToken: string;
  /** 会话专属 RSA 公钥（SPKI base64）；登录后客户端加密应使用此钥 */
  publicKey?: string;
}

export type AccessCode = string;

export interface UserInfo {
  id: number | string;
  username: string;
  realName: string;
  roles: string[];
  homePath?: string;
  avatar?: string;
  email?: string;
  tenantId?: string | number;
  [k: string]: unknown;
}

// ============================================================
// 用户管理（sys_user / sys_user_role）— 字段对齐 schema.sql v5
// 软删 deletedAt: 0=未删；passwordHash 仅后端持有，前端不暴露
// ============================================================

export interface UserListItem {
  id: number;
  username: string;
  nickname: string;
  email: string;
  phone: string;
  avatar: string;
  /** 用户默认语言（软外键 → i18n_locale.code） */
  languageCode: string | null;
  lastLoginAt: string | null;
  lastLoginIp: string;
  /** 账号过期时刻；null=永不过期 */
  accountExpiresAt: string | null;
  remark: string;
  isEnabled: 0 | 1;
  deletedAt: number;
  createdAt: string;
  updatedAt: string;
  /** 用户角色 ID 列表（来自 sys_user_role） */
  roleIds: number[];
  /** 角色名冗余，便于列表展示 */
  roleNames: string[];
}

export interface PageResult<T> {
  items: T[];
  /** 分页总数：接口管理为「分组数」 */
  total: number;
  /**
   * 接口条数（筛选后）。
   * 仅接口列表等「按组分页」接口会返回；普通分页可不传。
   */
  itemTotal?: number;
}

export interface UserListQuery {
  page?: number;
  pageSize?: number;
  username?: string;
  nickname?: string;
  status?: 0 | 1;
  /** 按角色 ID 过滤 */
  roleId?: number;
  [k: string]: unknown;
}

export interface CreateUserRequest {
  username: string;
  /** 创建时必填 */
  password: string;
  nickname: string;
  email?: string;
  phone?: string;
  avatar?: string;
  languageCode?: string | null;
  isEnabled?: 0 | 1;
  roleIds?: number[];
  remark?: string;
  /** 账号过期时刻；null/省略=永不过期 */
  accountExpiresAt?: string | null;
}

export interface UpdateUserRequest {
  id: number;
  data: Partial<Omit<CreateUserRequest, 'username' | 'password'>>;
}

/** 切换用户启停状态：{ status: 0|1 } */
export interface ToggleUserStatusRequest {
  id: number;
  status: 0 | 1;
}

/** 重置密码：{ password } */
export interface ResetPasswordRequest {
  id: number;
  password: string;
}

export interface MenuMeta {
  title?: string;
  icon?: string;
  order?: number;
  hideInMenu?: boolean;
  affixTab?: boolean;
  keepAlive?: boolean;
  authority?: string[];
  iframeSrc?: string;
  link?: string;
  badge?: string;
  badgeType?: string;
  badgeVariants?: string;
  [k: string]: unknown;
}

export interface MenuItem {
  id?: number | string;
  name?: string;
  path?: string;
  component?: string;
  redirect?: string;
  meta?: MenuMeta;
  children?: MenuItem[];
  [k: string]: unknown;
}

// ============================================================
// 字典管理（dict_type / dict_data）
// 字段对齐 backend-mock-template 的 schema；软删 deleted_at: 0=未删
// ============================================================

/**
 * 与 antd `_util/type` 内 `LiteralUnion` 等价的最小实现。
 *
 * antd 入口未 re-export LiteralUnion，因此在本文件内联；语义与 antd 官方
 * `T | (U & Record<never, never>)` 一致：
 *  - T 部分给 IDE auto-complete（命中预设字面量时收窄）
 *  - 任意 string 仍可传入，避免丢失向后兼容
 */
export type LiteralUnion<T, U extends string = string> =
  | T
  | (U & Record<never, never>);

/**
 * 预设样式联合类型：与 antd `<Tag color>` prop 的官方签名一致。
 * 从 antd `_util/colors` 子路径取类型，避免依赖入口是否 re-export。
 */
export type DictTagType = LiteralUnion<
  import('antd/_util/colors').PresetColorType | import('antd/_util/colors').PresetStatusColorType
>;

export interface DictType {
  id: number;
  code: string;
  name: string;
  remark: string;
  isEnabled: 0 | 1;
  deletedAt: number;
  createdAt: string;
  updatedAt: string;
  createdBy: number;
  updatedBy: number;
}

export interface DictData {
  id: number;
  typeId: number;
  value: string;
  label: string;
  sort: number;
  isDefault: 0 | 1;
  /** 归属平台：general / react-admin / vue-admin；与 schema v8 对齐 */
  platform: string;
  /**
   * 预设样式标识：与 antd `<Tag color>` 签名一致
   * （LiteralUnion<PresetColorType | PresetStatusColorType>）。
   * 可选值集合收敛到 13 项 preset 色 + 13 项 inverse + 5 项状态色
   * （default / primary / success / warning / error / processing
   *  / magenta / red / volcano / orange / gold / lime / green
   *  / cyan / blue / geekblue / purple / 各自 -inverse）。
   * 与 backend-mock 的 ALLOWED_TAG_TYPES（17 项无 inverse）完全相容。
   */
  tagType: DictTagType;
  isEnabled: 0 | 1;
  deletedAt: number;
  remark: string;
  createdAt: string;
  updatedAt: string;
  createdBy: number;
  updatedBy: number;
  /** 关联的字典类型编码（仅 list 接口返回） */
  typeCode?: string;
}

export interface DictTypeQuery {
  page?: number;
  pageSize?: number;
  /** 字典类型编码；前端多选下拉时传数组（精确匹配任一） */
  code?: string | string[];
  name?: string;
  status?: 0 | 1;
}

export interface DictDataQuery {
  page?: number;
  pageSize?: number;
  typeId?: number;
  /** 字典类型编码；多选下拉时传数组（精确匹配任一） */
  typeCode?: string | string[];
  label?: string;
  value?: string;
  status?: 0 | 1;
  /** 归属平台过滤（精确匹配；缺省由 hooks 层注入 VITE_APP_PLATFORM） */
  platform?: string;
  /** 是否把通用（general）并入过滤结果（仅当 platform !== 'general' 时生效） */
  includeGeneral?: boolean;
}

export interface CreateDictTypeRequest {
  code: string;
  name: string;
  remark?: string;
  isEnabled?: 0 | 1;
}

export interface UpdateDictTypeRequest {
  id: number;
  code?: string;
  name?: string;
  remark?: string;
  isEnabled?: 0 | 1;
}

export interface CreateDictDataRequest {
  typeId: number;
  value: string;
  label: string;
  sort?: number;
  isDefault?: boolean;
  /** 归属平台；缺省 mock 层回退到 'general' */
  platform?: string;
  /** 预设样式标识；缺省 mock 层回退到 'default' */
  tagType?: DictTagType;
  isEnabled?: 0 | 1;
  remark?: string;
}

export interface UpdateDictDataRequest {
  id: number;
  value?: string;
  label?: string;
  sort?: number;
  isDefault?: 0 | 1;
  platform?: string;
  tagType?: DictTagType;
  isEnabled?: 0 | 1;
  remark?: string;
}

// ============================================================
// I18n（i18n_locale / i18n_translation）
// 字段对齐 backend-mock-template 的 schema；软删 deleted_at: 0=未删
// ============================================================

export interface I18nLocale {
  id: number;
  code: string;
  name: string;
  isDefault: 0 | 1;
  sort: number;
  remark: string;
  isEnabled: 0 | 1;
  deletedAt: number;
  createdAt: string;
  updatedAt: string;
  createdBy: number;
  updatedBy: number;
}

export interface I18nTranslation {
  id: number;
  localeId: number;
  translationKey: string;
  value: string;
  remark: string;
  isEnabled: 0 | 1;
  deletedAt: number;
  createdAt: string;
  updatedAt: string;
  createdBy: number;
  updatedBy: number;
  /** 关联语言编码（仅 list 接口 join 后返回） */
  localeCode?: string;
}

export interface I18nLocaleQuery {
  page?: number;
  pageSize?: number;
  code?: string | string[];
  name?: string;
  status?: 0 | 1;
}

export interface I18nTranslationQuery {
  page?: number;
  pageSize?: number;
  /** 精确匹配语言 ID（与 localeCode 二选一；都传以 localeId 优先） */
  localeId?: number;
  /** 按语言编码过滤（前端选中左表行时使用） */
  localeCode?: string;
  /** 模糊匹配 key 或 value */
  value?: string;
  status?: 0 | 1;
}

/**
 * 按 translationKey 聚合的主行（list 接口加 ?byKey=true 返回）。
 * sampleRowId 取同 key 下某一行 id，传给抽屉打开 byKeyQuery。
 */
export interface I18nTranslationKey {
  translationKey: string;
  localeCount: number;
  sampleRowId: number;
  sampleLocaleId: number;
  sampleLocaleCode?: string;
  sampleUpdatedAt: string;
}

export interface I18nTranslationKeyQuery {
  page?: number;
  pageSize?: number;
  /** 模糊匹配 translationKey */
  value?: string;
}

export interface CreateI18nLocaleRequest {
  code: string;
  name: string;
  sort?: number;
  remark?: string;
  isDefault?: 0 | 1;
  isEnabled?: 0 | 1;
}

export interface UpdateI18nLocaleRequest {
  id: number;
  code?: string;
  name?: string;
  sort?: number;
  remark?: string;
  isDefault?: 0 | 1;
  isEnabled?: 0 | 1;
}

export interface CreateI18nTranslationRequest {
  localeId: number;
  translationKey: string;
  value: string;
  remark?: string;
  isEnabled?: 0 | 1;
}

export interface UpdateI18nTranslationRequest {
  id: number;
  translationKey?: string;
  value?: string;
  remark?: string;
  isEnabled?: 0 | 1;
}

/**
 * 按 translation_key 聚合返回的多语言版本（GET /system/i18n-translation/by-key/:key）。
 * 缺失 key 时 values 为空数组。
 */
export interface I18nTranslationByKeyValue {
  id: number;
  localeId: number;
  localeCode?: string;
  value: string;
  remark: string;
  isEnabled: 0 | 1;
}

export interface I18nTranslationByKeyResponse {
  translationKey: string;
  values: I18nTranslationByKeyValue[];
}

/**
 * 单 key 多语言事务化 upsert（POST /system/i18n-translation/batch-upsert-by-key）。
 * 处理顺序：rename → delete → upsert，任一阶段失败即返回 errors，不继续后续阶段。
 */
export interface I18nTranslationBatchUpsertByKeyItem {
  localeId: number;
  value: string;
  remark?: string;
  isEnabled?: 0 | 1;
}

export interface I18nTranslationBatchUpsertByKeyRequest {
  translationKey: string;
  /** 可选：仅「剩 1 row」时才提供 */
  newTranslationKey?: string;
  items: I18nTranslationBatchUpsertByKeyItem[];
  /** 可选：随本次保存一起删除的 row id */
  deletedIds?: number[];
}

export interface I18nTranslationBatchUpsertError {
  code: string;
  message: string;
  localeId?: number;
  id?: number;
}

export interface I18nTranslationBatchUpsertByKeyResponse {
  ok: boolean;
  affected?: { renamed: number; created: number; updated: number; deleted: number };
  values?: I18nTranslationByKeyValue[];
  errors?: I18nTranslationBatchUpsertError[];
}

// ============================================================
// I18n 导出 / 导入 / 同步
// ============================================================

/** 导出 JSON 请求参数 */
export interface I18nExportParams {
  ids: number[];
  type: 'raw' | 'simple';
}

/** raw 导出格式 */
export interface I18nRawExport {
  '@type': 'raw';
  locale: I18nLocale;
  translations: Array<{
    id?: number;
    translationKey: string;
    value: string;
    remark?: string;
    isEnabled?: 0 | 1;
  }>;
}

/** simple 导出格式：顶层为嵌套字典（unflatten 后即得到 key/value） */
export interface I18nSimpleExport {
  '@type': 'simple';
  [key: string]: unknown;
}

export type I18nExportData = I18nRawExport | I18nSimpleExport;

/* ============================================================
 * 批量导入（多文件）— import-batch / import-preview / export-batch
 * ============================================================ */

export type I18nImportFormat = 'raw' | 'simple';

export interface I18nImportBatchItem {
  /** 文件名（用于 perFile 回显与 UI 标记） */
  name: string;
  /** key 前缀拼接；空或省略表示原样 */
  prefix?: string;
  /** 该文件的目标语言 code（simple 必填，raw 优先取文件内 locale.code） */
  localeCode: string;
  format: I18nImportFormat;
  /** 已 JSON.parse 后的 payload */
  payload: unknown;
}

export interface I18nImportBatchRequest {
  items: I18nImportBatchItem[];
}

export interface I18nImportBatchPerFile {
  name: string;
  ok: boolean;
  error?: string;
  createdLocales: number;
  softDeleted: number;
  createdTranslations: number;
}

export interface I18nImportBatchResponse {
  ok: boolean;
  affected: {
    createdLocales: number;
    softDeleted: number;
    createdTranslations: number;
    perFile: I18nImportBatchPerFile[];
  };
}

export interface I18nImportPreviewItem {
  localeCode: string;
  keys: string[];
}

export interface I18nImportPreviewRequest {
  items: I18nImportPreviewItem[];
}

export interface I18nImportPreviewResponse {
  currentRows: I18nTranslation[];
}

export interface I18nExportBatchRequest {
  ids: number[];
  format: I18nImportFormat;
}

export interface I18nExportBatchFile {
  code: string;
  format: I18nImportFormat;
  content: I18nRawExport | I18nSimpleExport;
}

export interface I18nExportBatchResponse {
  files: I18nExportBatchFile[];
}

// ============================================================
// 菜单管理（sys_menu）— 字段对齐 backend-mock-template 的 sys_menu；软删 deletedAt: 0=未删
// ============================================================

export type MenuType = 'DIR' | 'MENU' | 'BUTTON';

export interface SysMenu {
  id: number;
  parentId: number | null;
  name: string;
  type: MenuType;
  path: string | null;
  component: string | null;
  icon: string;
  redirect: string;
  permissionCode: string | null;
  /** 物化路径，如 /1/11/ */
  treePath: string;
  /** 前端扩展 JSON 字符串（badge/hideInBreadcrumb/keepAlive/affix/activeMenu） */
  metadata: string | null;
  sort: number;
  isHidden: 0 | 1;
  isEnabled: 0 | 1;
  deletedAt: number;
  remark: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: number;
  updatedBy?: number;
}

export interface MenuListQuery {
  page?: number;
  pageSize?: number;
  name?: string;
  type?: MenuType;
  permissionCode?: string;
  status?: 0 | 1;
  [k: string]: unknown;
}

export interface CreateMenuRequest {
  parentId?: number | null;
  name: string;
  type: MenuType;
  path?: string | null;
  component?: string | null;
  icon?: string;
  redirect?: string;
  permissionCode?: string | null;
  metadata?: string | null;
  sort?: number;
  isHidden?: 0 | 1;
  isEnabled?: 0 | 1;
  remark?: string;
}

export interface UpdateMenuRequest {
  id: number;
  data: Partial<CreateMenuRequest>;
}

// ============================================================
// 接口管理（sys_api）— 字段对齐 backend-mock-template 的 sys_api
// ============================================================

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'OPTIONS' | 'HEAD';

export interface SysApi {
  id: number;
  name: string;
  method: HttpMethod;
  path: string;
  permissionCode: string;
  apiGroup: string;
  remark: string;
  isEnabled: 0 | 1;
  deletedAt: number;
  createdAt: string;
  updatedAt: string;
  createdBy?: number;
  updatedBy?: number;
}

export interface ApiListQuery {
  page?: number;
  pageSize?: number;
  name?: string;
  path?: string;
  method?: HttpMethod;
  group?: string;
  status?: 0 | 1;
  [k: string]: unknown;
}

export interface CreateApiRequest {
  name: string;
  method: HttpMethod;
  path: string;
  permissionCode: string;
  apiGroup?: string;
  remark?: string;
  isEnabled?: 0 | 1;
}

export interface UpdateApiRequest {
  id: number;
  data: Partial<CreateApiRequest>;
}

export interface ApiBatchRequest {
  action: 'enable' | 'disable' | 'delete';
  ids: number[];
}

export interface MenuBatchRequest {
  action: 'enable' | 'disable' | 'delete';
  ids: number[];
}

export interface ApiSyncResult {
  added: number;
  skipped: number;
  total: number;
}

export interface MenuBindApiItem {
  id: number;
  name: string;
  method: HttpMethod;
  path: string;
  permissionCode: string;
  apiGroup: string;
  isEnabled: 0 | 1;
  /** 是否已绑定到当前菜单 */
  bound: boolean;
}

// ============================================================
// 角色管理（sys_role / sys_role_menu / sys_role_api）— 字段对齐 schema.sql v5
// ============================================================

export interface SysRole {
  id: number;
  /** 角色编码（创建后不可改） */
  code: string;
  name: string;
  parentId: number | null;
  sort: number;
  remark: string;
  isEnabled: 0 | 1;
  deletedAt: number;
  createdAt: string;
  updatedAt: string;
  createdBy?: number;
  updatedBy?: number;
  /** 角色下用户数（实时统计，列表用） */
  userCount?: number;
  /** 父角色名（冗余，列表用） */
  parentName?: string | null;
}

export interface RoleListQuery {
  page?: number;
  pageSize?: number;
  code?: string;
  name?: string;
  status?: 0 | 1;
  [k: string]: unknown;
}

export interface CreateRoleRequest {
  code: string;
  name: string;
  parentId?: number | null;
  sort?: number;
  isEnabled?: 0 | 1;
  remark?: string;
}

export interface UpdateRoleRequest {
  id: number;
  data: Partial<Omit<CreateRoleRequest, 'code'>>;
}

/** 角色可授权的菜单项（带 bound 标记，复用菜单全量数据） */
export interface RoleMenuBindItem extends SysMenu {
  bound: boolean;
}

/** 角色可授权的接口项（带 bound 标记，复用接口全量数据） */
export interface RoleApiBindItem {
  id: number;
  name: string;
  method: HttpMethod;
  path: string;
  permissionCode: string;
  apiGroup: string;
  isEnabled: 0 | 1;
  bound: boolean;
}

/** 简化角色选项（用户表单的角色下拉用） */
export interface RoleOption {
  id: number;
  code: string;
  name: string;
}

// ============================================================
// 登录日志（sys_login_log / sys_login_log_archive）— 对齐 schema.sql v5
// ============================================================

export type LoginMethod = 'PASSWORD' | 'SSO' | 'OAUTH' | 'SMS';

export type LoginLogSource = 'hot' | 'archive';

export interface LoginLogListItem {
  id: number;
  username: string;
  success: 0 | 1;
  reason: string;
  statusCode: number | null;
  sysUserId: number | null;
  loginMethod: LoginMethod | string;
  loginTime: string;
  loginIp: string;
  loginMac: string;
  clientId: string;
  clientName: string;
  userAgent: string;
  browserName: string;
  browserVersion: string;
  osName: string;
  osVersion: string;
  location: string;
  createdAt: string;
  /** 仅 source=archive 时有值 */
  archivedAt?: string;
}

export interface LoginLogListQuery {
  page?: number;
  pageSize?: number;
  /** hot=热表 archive=归档，默认 hot */
  source?: LoginLogSource;
  username?: string;
  success?: 0 | 1;
  loginMethod?: string;
  loginIp?: string;
  loginTimeFrom?: string;
  loginTimeTo?: string;
  [k: string]: unknown;
}

/** API 调用日志来源：热表 / 归档 */
export type ApiLogSource = 'hot' | 'archive';

/** API 调用日志列表项（对齐 api_log / api_log_archive camel 输出） */
export interface ApiLogListItem {
  id: number;
  method: string;
  module: string;
  path: string;
  statusCode: number | null;
  success: 0 | 1;
  reason: string;
  costTime: number;
  requestId: string;
  sysUserId: number | null;
  username: string;
  requestUri: string;
  requestQuery: string;
  requestBody: string;
  requestHeader: string;
  referer: string;
  response: string;
  beforeChange: string;
  afterChange: string;
  formatChange: string;
  clientId: string;
  clientName: string;
  clientIp: string;
  userAgent: string;
  browserName: string;
  browserVersion: string;
  osName: string;
  osVersion: string;
  location: string;
  createdAt: string;
  /** 仅 source=archive 时有值 */
  archivedAt?: string;
}

export interface ApiLogListQuery {
  page?: number;
  pageSize?: number;
  /** hot=热表 archive=归档，默认 hot */
  source?: ApiLogSource;
  method?: string;
  module?: string;
  path?: string;
  success?: 0 | 1;
  statusCode?: number;
  username?: string;
  clientIp?: string;
  requestId?: string;
  createdAtFrom?: string;
  createdAtTo?: string;
  [k: string]: unknown;
}

// ============================================================
// Temporal 任务调度 — task-config / task-execution
// 字段对齐 mock camelCase 输出（backend schema §8 / §22）
// ============================================================

/** Temporal 工作流执行状态（与 mock 枚举一致） */
export type TaskExecutionStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'RETRYING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'
  | 'TERMINATED'
  | 'TIMED_OUT'
  | 'CONTINUED_AS_NEW';

/** 任务配置下拉选项（workflowType / taskQueue） */
export interface TaskSelectOption {
  label: string;
  value: string;
}

/** 任务配置 */
export interface TaskConfig {
  id: number;
  code: string;
  name: string;
  workflowType: string;
  taskQueue: string;
  /** null = 仅手动触发 */
  cronExpr: string | null;
  /** 重试策略 JSON 对象 */
  retryPolicy: Record<string, unknown> | null;
  timeoutSeconds: number | null;
  remark: string;
  isEnabled: 0 | 1;
  deletedAt: number;
  createdAt: string;
  updatedAt: string;
  createdBy: number;
  updatedBy: number;
}

export interface TaskConfigQuery {
  page?: number;
  pageSize?: number;
  code?: string | string[];
  name?: string;
  /** isEnabled 过滤：0 | 1 */
  status?: 0 | 1;
  /** 工作流类型精确匹配 */
  workflowType?: string;
  /** 任务队列精确匹配 */
  taskQueue?: string;
  [k: string]: unknown;
}

export interface CreateTaskConfigRequest {
  code: string;
  name: string;
  workflowType: string;
  taskQueue: string;
  cronExpr?: string | null;
  retryPolicy?: Record<string, unknown> | null;
  timeoutSeconds?: number | null;
  remark?: string;
  isEnabled?: 0 | 1;
}

export interface UpdateTaskConfigRequest {
  id: number;
  code?: string;
  name?: string;
  workflowType?: string;
  taskQueue?: string;
  cronExpr?: string | null;
  retryPolicy?: Record<string, unknown> | null;
  timeoutSeconds?: number | null;
  remark?: string;
  isEnabled?: 0 | 1;
}

export type TaskConfigBatchAction = 'enable' | 'disable' | 'delete' | 'trigger';

export interface TaskConfigBatchRequest {
  action: TaskConfigBatchAction;
  ids: number[];
}

export interface TaskConfigBatchResult {
  action: string;
  affected: number;
  ids: number[];
  executionIds?: number[];
  skippedDisabled?: number[];
}

export interface TaskConfigTriggerResult {
  config: TaskConfig;
  execution: TaskExecution;
}

/** 任务执行记录 */
export interface TaskExecution {
  id: number;
  /** 软外键；配置软删后可悬空 */
  configId: number | null;
  /** list/detail 解析出的配置名；缺失时为 null（前端展示 —） */
  configName?: string | null;
  workflowId: string;
  runId: string;
  workflowType: string;
  taskQueue: string;
  status: TaskExecutionStatus;
  /** 进入等待中的时间 */
  pendingAt: string | null;
  /** 真正运行开始时间；尚未真正运行时为 null */
  startedAt: string | null;
  closedAt: string | null;
  inputSummary: Record<string, unknown> | null;
  resultSummary: Record<string, unknown> | null;
  failureReason: string | null;
  /** 已发生重试次数；首次执行为 0 */
  retryCount: number;
  createdAt: string;
}

export interface TaskExecutionQuery {
  page?: number;
  pageSize?: number;
  configId?: number;
  status?: TaskExecutionStatus | string;
  startedAtFrom?: string;
  startedAtTo?: string;
  /** 工作流类型精确匹配 */
  workflowType?: string;
  [k: string]: unknown;
}

// ============================================================
// 访问黑名单（sys_blacklist）— 对齐 mock/Java camelCase VO
// target: IP|SYS_USER|DEVICE；scope: LOGIN|API|ALL
// ============================================================

export type BlacklistTargetType = 'IP' | 'SYS_USER' | 'DEVICE';

export type BlacklistScope = 'LOGIN' | 'API' | 'ALL';

export interface Blacklist {
  id: number;
  targetType: BlacklistTargetType | string;
  targetValue: string;
  scope: BlacklistScope | string;
  reason: string;
  /** 生效开始（含）；ISO 或 LocalDateTime 字符串 */
  startsAt: string;
  /** 生效结束（不含）；null = 永不过期 */
  expiresAt: string | null;
  remark: string;
  isEnabled: 0 | 1;
  deletedAt: number;
  createdAt: string;
  updatedAt: string;
  createdBy: number;
  updatedBy: number;
}

export interface BlacklistQuery {
  page?: number;
  pageSize?: number;
  targetType?: BlacklistTargetType | string;
  targetValue?: string;
  scope?: BlacklistScope | string;
  /** isEnabled 过滤：0 | 1 */
  status?: 0 | 1;
  [k: string]: unknown;
}

export interface CreateBlacklistRequest {
  targetType: BlacklistTargetType | string;
  targetValue: string;
  scope?: BlacklistScope | string;
  reason?: string;
  startsAt?: string | null;
  expiresAt?: string | null;
  remark?: string;
  isEnabled?: 0 | 1;
}

export interface UpdateBlacklistRequest {
  id: number;
  targetType?: BlacklistTargetType | string;
  targetValue?: string;
  scope?: BlacklistScope | string;
  reason?: string;
  startsAt?: string | null;
  expiresAt?: string | null;
  /** true 时清空 expiresAt（永久） */
  clearExpiresAt?: boolean;
  remark?: string;
  isEnabled?: 0 | 1;
}

export type BlacklistBatchAction = 'enable' | 'disable' | 'delete';

export interface BlacklistBatchRequest {
  action: BlacklistBatchAction;
  ids: number[];
}

export interface BlacklistBatchResult {
  action: string;
  affected: number;
  ids: number[];
}

// ============================================================
// Agent 对话（与 java-admin AgentController 的稳定 HTTP/SSE 契约对齐）
// ============================================================

export interface AgentDefinition {
  id: number;
  name: string;
  description: string;
  ownerUserId: number;
  currentPublishedRevisionId: number | null;
  remark: string;
  isEnabled: number;
  createdAt: string;
  updatedAt: string;
}

export interface AgentSession {
  id: number;
  agentDefinitionId: number;
  agentRevisionId: number | null;
  ownerUserId: number;
  status: string;
  createdAt: string;
}

export interface AgentRunEvent {
  type: string;
  requestId: string;
  sessionId: number;
  agentRevisionId: number | null;
  text: string | null;
  toolName: string | null;
  message: string | null;
}

export interface CreateAgentMessageRequest {
  requestId: string;
  message: string;
  /** 仅用于 POST SSE 请求头，绝不发送到后端业务载荷。 */
  accessToken: string;
}

export interface CancelAgentRunRequest {
  requestId: string;
}
