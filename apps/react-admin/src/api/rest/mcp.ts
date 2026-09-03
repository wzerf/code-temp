import { del, get, post, put } from './request';
import type {
  CreateMcpDraftRequest,
  McpDraft,
  McpDraftQuery,
  McpRelease,
  McpReleaseQuery,
  McpVerifyResult,
  PageResult,
  UpdateMcpDraftRequest,
} from './types';

/** MCP 草稿分页 */
export function listMcpDraftApi(query: McpDraftQuery = {}) {
  return get<PageResult<McpDraft>>('/system/mcp/draft/list', query as Record<string, unknown>);
}

/** MCP 草稿全量 */
export function listAllMcpDraftApi(params?: Omit<McpDraftQuery, 'page' | 'pageSize'>) {
  return get<McpDraft[]>('/system/mcp/draft/all', (params ?? {}) as Record<string, unknown>);
}

/** MCP 草稿详情 */
export function getMcpDraftApi(id: number) {
  return get<McpDraft>(`/system/mcp/draft/${id}`);
}

/** 创建 MCP 草稿 */
export function createMcpDraftApi(body: CreateMcpDraftRequest) {
  return post<McpDraft>('/system/mcp/draft', body);
}

/** 更新 MCP 草稿 */
export function updateMcpDraftApi({ id, ...patch }: UpdateMcpDraftRequest) {
  return put<McpDraft>(`/system/mcp/draft/${id}`, patch);
}

/** 删除 MCP 草稿 */
export function deleteMcpDraftApi(id: number) {
  return del<never>(`/system/mcp/draft/${id}`);
}

/** 握手验证（返回工具目录） */
export function verifyMcpDraftApi(id: number) {
  return post<McpVerifyResult>(`/system/mcp/draft/${id}/verify`);
}

/** 提交审核 */
export function submitMcpDraftApi(id: number) {
  return post<never>(`/system/mcp/draft/${id}/submit`);
}

/** 撤回审核 */
export function withdrawMcpDraftApi(id: number) {
  return post<never>(`/system/mcp/draft/${id}/withdraw`);
}

/** 通过审核并发布 Release */
export function approveMcpDraftApi(id: number) {
  return post<McpRelease>(`/system/mcp/draft/${id}/approve`);
}

/** 驳回草稿 */
export function rejectMcpDraftApi(id: number, reason?: string) {
  return post<never>(`/system/mcp/draft/${id}/reject`, { reason });
}

// ---------- Release / 市场 ----------

export function listMcpReleaseApi(query: McpReleaseQuery = {}) {
  return get<PageResult<McpRelease>>('/system/mcp/release/list', query as Record<string, unknown>);
}

export function getMcpReleaseApi(id: number) {
  return get<McpRelease>(`/system/mcp/release/${id}`);
}

/** MCP 市场列表 */
export function listMcpMarketApi() {
  return get<McpRelease[]>('/system/mcp/market');
}

/** 可绑定候选 */
export function listMcpBindableApi(ownerUserId?: number) {
  return get<McpRelease[]>('/system/mcp/bindable', ownerUserId ? { ownerUserId } : {});
}

/** 市场下架 */
export function takeDownMcpMarketApi(id: number) {
  return post<never>(`/system/mcp/market/${id}/take-down`);
}

/** 弃用单个 Release */
export function deprecateMcpReleaseApi(id: number) {
  return post<never>(`/system/mcp/release/${id}/deprecate`);
}
