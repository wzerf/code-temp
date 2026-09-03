import { del, get, post, put } from './request';
import type {
  Agent,
  AgentQuery,
  AgentRevision,
  AgentSession,
  BindMcpRequest,
  BindSessionMcpRequest,
  BindSessionSkillRequest,
  BindSkillRequest,
  CreateAgentRequest,
  PageResult,
  RevisionMcpBinding,
  RevisionSkillBinding,
  SaveAgentRevisionRequest,
  SessionMcpBinding,
  SessionSkillBinding,
  UpdateAgentRequest,
} from './types';

/** Agent 定义分页 */
export function listAgentApi(query: AgentQuery = {}) {
  return get<PageResult<Agent>>('/system/agent/list', query as Record<string, unknown>);
}

/** Agent 定义全量 */
export function listAllAgentApi(params?: Omit<AgentQuery, 'page' | 'pageSize'>) {
  return get<Agent[]>('/system/agent/all', (params ?? {}) as Record<string, unknown>);
}

/** Agent 定义详情 */
export function getAgentApi(id: number) {
  return get<Agent>(`/system/agent/${id}`);
}

/** 创建 Agent 定义 */
export function createAgentApi(body: CreateAgentRequest) {
  return post<Agent>('/system/agent', body);
}

/** 更新 Agent 定义 */
export function updateAgentApi({ id, ...patch }: UpdateAgentRequest) {
  return put<Agent>(`/system/agent/${id}`, patch);
}

/** 软删 Agent 定义 */
export function deleteAgentApi(id: number) {
  return del<never>(`/system/agent/${id}`);
}

/** 紧急禁用 */
export function disableAgentApi(id: number) {
  return post<Agent>(`/system/agent/${id}/disable`);
}

/** 启用 */
export function enableAgentApi(id: number) {
  return post<Agent>(`/system/agent/${id}/enable`);
}

/** 回滚到指定已发布 Revision */
export function rollbackAgentApi(id: number, revisionId: number) {
  return post<Agent>(`/system/agent/${id}/rollback`, { revisionId });
}

// ---------- Revision ----------

/** Revision 列表 */
export function listAgentRevisionsApi(definitionId: number) {
  return get<AgentRevision[]>(`/system/agent/${definitionId}/revisions`);
}

/** 活跃草稿（无则返回 null） */
export function getActiveAgentDraftApi(definitionId: number) {
  return get<AgentRevision | null>(`/system/agent/${definitionId}/revisions/active-draft`);
}

/** 创建草稿 Revision */
export function createAgentRevisionApi(definitionId: number, body: SaveAgentRevisionRequest) {
  return post<AgentRevision>(`/system/agent/${definitionId}/revisions`, body);
}

/** 更新草稿 Revision */
export function updateAgentRevisionApi(revisionId: number, body: SaveAgentRevisionRequest) {
  return put<AgentRevision>(`/system/agent/revisions/${revisionId}`, body);
}

/** 删除草稿 Revision */
export function deleteAgentRevisionApi(revisionId: number) {
  return del<never>(`/system/agent/revisions/${revisionId}`);
}

/** 发布草稿（copyAsPublished） */
export function publishAgentRevisionApi(revisionId: number) {
  return post<AgentRevision>(`/system/agent/revisions/${revisionId}/publish`);
}

// ---------- Revision Skill/MCP 绑定 ----------

/** 路径参数守卫:revisionId 必须是有穷数字,否则直接抛错而不是把 undefined 打到后端。 */
function requireRevisionId(revisionId: number): number {
  if (typeof revisionId !== 'number' || !Number.isFinite(revisionId)) {
    throw new Error(`revisionId 非法: ${String(revisionId)}`);
  }
  return revisionId;
}

export function listRevisionSkillBindingsApi(revisionId: number) {
  return get<RevisionSkillBinding[]>(
    `/system/agent/revisions/${requireRevisionId(revisionId)}/skill-bindings`,
  );
}

export function bindSkillToRevisionApi(revisionId: number, body: BindSkillRequest) {
  return post<RevisionSkillBinding>(
    `/system/agent/revisions/${requireRevisionId(revisionId)}/skill-bindings`,
    body,
  );
}

export function unbindSkillFromRevisionApi(revisionId: number, bindingId: number) {
  return del<never>(
    `/system/agent/revisions/${requireRevisionId(revisionId)}/skill-bindings/${bindingId}`,
  );
}

export function listRevisionMcpBindingsApi(revisionId: number) {
  return get<RevisionMcpBinding[]>(
    `/system/agent/revisions/${requireRevisionId(revisionId)}/mcp-bindings`,
  );
}

export function bindMcpToRevisionApi(revisionId: number, body: BindMcpRequest) {
  return post<RevisionMcpBinding>(
    `/system/agent/revisions/${requireRevisionId(revisionId)}/mcp-bindings`,
    body,
  );
}

export function unbindMcpFromRevisionApi(revisionId: number, bindingId: number) {
  return del<never>(
    `/system/agent/revisions/${requireRevisionId(revisionId)}/mcp-bindings/${bindingId}`,
  );
}

// ---------- Session（控制面） ----------

export function listAgentSessionsApi(definitionId: number, query: { page?: number; pageSize?: number } = {}) {
  return get<PageResult<AgentSession>>(`/system/agent/${definitionId}/sessions`, query as Record<string, unknown>);
}

export function createAgentSessionApi(definitionId: number, body: { remark?: string } = {}) {
  return post<AgentSession>(`/system/agent/${definitionId}/sessions`, body);
}

export function deleteAgentSessionApi(sessionId: number) {
  return del<never>(`/system/agent/sessions/${sessionId}`);
}

export function bindRevisionToSessionApi(sessionId: number) {
  return post<AgentSession>(`/system/agent/sessions/${sessionId}/bind-revision`);
}

export function listSessionSkillBindingsApi(sessionId: number) {
  return get<SessionSkillBinding[]>(`/system/agent/sessions/${sessionId}/skill-bindings`);
}

export function bindSkillToSessionApi(sessionId: number, body: BindSessionSkillRequest) {
  return post<SessionSkillBinding>(`/system/agent/sessions/${sessionId}/skill-bindings`, body);
}

export function unbindSkillFromSessionApi(sessionId: number, bindingId: number) {
  return del<never>(`/system/agent/sessions/${sessionId}/skill-bindings/${bindingId}`);
}

export function listSessionMcpBindingsApi(sessionId: number) {
  return get<SessionMcpBinding[]>(`/system/agent/sessions/${sessionId}/mcp-bindings`);
}

export function bindMcpToSessionApi(sessionId: number, body: BindSessionMcpRequest) {
  return post<SessionMcpBinding>(`/system/agent/sessions/${sessionId}/mcp-bindings`, body);
}

export function unbindMcpFromSessionApi(sessionId: number, bindingId: number) {
  return del<never>(`/system/agent/sessions/${sessionId}/mcp-bindings/${bindingId}`);
}
