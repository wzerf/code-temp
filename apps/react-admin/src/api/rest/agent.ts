import { del, get, post, put } from './request';
import type {
  AgentDefinition,
  AgentRevision,
  AgentRunEvent,
  AgentSession,
  AgentSessionHistory,
  BindableSkill,
  CancelAgentRunRequest,
  CreateAgentMessageRequest,
  CreateAgentRevisionRequest,
  CreateGitSkillSourceRequest,
  CreateSkillDraftRequest,
  CreateSkillDraftResourceRequest,
  GitSkillPreview,
  GitSkillSource,
  GitSkillSyncResult,
  SkillDraft,
  SkillInstall,
  SkillMarket,
  SkillRelease,
  SkillResource,
  UpdateAgentRevisionRequest,
  UpdateGitSkillSourceRequest,
  UpdateSkillDraftRequest,
  UpdateSkillDraftResourceRequest,
} from './types';

export function getAgentDefinitionApi(id: number) {
  return get<AgentDefinition>(`/agent/${id}`);
}

export function listAgentDefinitionsApi() {
  return get<AgentDefinition[]>('/agent');
}

export function createAgentSessionApi(agentDefinitionId: number) {
  return post<AgentSession>(`/agent/${agentDefinitionId}/sessions`);
}

export function listAgentSessionsApi(agentDefinitionId: number) {
  return get<AgentSession[]>(`/agent/${agentDefinitionId}/sessions`);
}

export function getAgentSessionApi(sessionId: number) {
  return get<AgentSession>(`/agent/sessions/${sessionId}`);
}

export function getAgentSessionHistoryApi(sessionId: number) {
  return get<AgentSessionHistory>(`/agent/sessions/${sessionId}/history`);
}

export function cancelAgentRunApi(sessionId: number, body: CancelAgentRunRequest) {
  return post<AgentRunEvent>(`/agent/sessions/${sessionId}/cancel`, body);
}

export interface AgentEventStreamOptions {
  signal?: AbortSignal;
  onEvent: (event: AgentRunEvent) => void;
}

export function parseAgentEventStream(chunk: string, onEvent: (event: AgentRunEvent) => void): string {
  const frames = chunk.split(/\r?\n\r?\n/);
  const pending = frames.pop() ?? '';
  for (const frame of frames) {
    const data = frame
      .split(/\r?\n/)
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trimStart())
      .join('\n');
    if (data) onEvent(JSON.parse(data) as AgentRunEvent);
  }
  return pending;
}

async function consumeAgentEventStream(
  path: string,
  accessToken: string,
  options: AgentEventStreamOptions,
  init?: RequestInit,
): Promise<void> {
  const response = await fetch(path, {
    ...init,
    headers: {
      Accept: 'text/event-stream',
      Authorization: `Bearer ${accessToken}`,
      'X-Requested-With': 'XMLHttpRequest',
      ...(init?.headers ?? {}),
    },
    signal: options.signal,
  });
  if (!response.ok || !response.body) {
    throw new Error(`Agent SSE 连接失败：${response.status}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let pending = '';
  try {
    for (;;) {
      const { done, value } = await reader.read();
      pending += decoder.decode(value, { stream: !done });
      pending = parseAgentEventStream(pending, options.onEvent);
      if (done) break;
    }
    if (pending.trim()) parseAgentEventStream(`${pending}\n\n`, options.onEvent);
  } finally {
    reader.releaseLock();
  }
}

/** POST SSE 不可用 EventSource；调用方可用 requestId 调用 resumeAgentSessionApi 续接。 */
export function runAgentSessionApi(
  sessionId: number,
  body: CreateAgentMessageRequest,
  options: AgentEventStreamOptions,
): Promise<void> {
  return consumeAgentEventStream(`/api/agent/sessions/${sessionId}/events`, body.accessToken, options, {
    body: JSON.stringify({ requestId: body.requestId, message: body.message }),
    headers: { 'Content-Type': 'application/json', 'X-Request-ID': body.requestId },
    method: 'POST',
  });
}

export function resumeAgentSessionApi(
  sessionId: number,
  requestId: string,
  accessToken: string,
  options: AgentEventStreamOptions,
): Promise<void> {
  const params = new URLSearchParams({ requestId });
  return consumeAgentEventStream(`/api/agent/sessions/${sessionId}/events?${params}`, accessToken, options);
}

export function listSkillDraftsApi() {
 return get<SkillDraft[]>('/agent/skills/drafts'); 
}
export function getSkillDraftApi(id: number) {
 return get<SkillDraft>(`/agent/skills/drafts/${id}`); 
}
export function createSkillDraftApi(body: CreateSkillDraftRequest) {
 return post<SkillDraft>('/agent/skills/drafts', body); 
}
export function updateSkillDraftApi(id: number, body: UpdateSkillDraftRequest) {
 return put<SkillDraft>(`/agent/skills/drafts/${id}`, body); 
}
export function listSkillDraftResourcesApi(draftId: number) {
  return get<SkillResource[]>(`/agent/skills/drafts/${draftId}/resources`);
}
export function createSkillDraftResourceApi(draftId: number, body: CreateSkillDraftResourceRequest) {
  return post<SkillResource>(`/agent/skills/drafts/${draftId}/resources`, body);
}
export function updateSkillDraftResourceApi(
  draftId: number,
  resourceId: number,
  body: UpdateSkillDraftResourceRequest,
) {
  return put<SkillResource>(`/agent/skills/drafts/${draftId}/resources/${resourceId}`, body);
}
export function deleteSkillDraftResourceApi(draftId: number, resourceId: number) {
  return del<void>(`/agent/skills/drafts/${draftId}/resources/${resourceId}`);
}
export function submitSkillDraftApi(id: number) {
 return post<SkillDraft>(`/agent/skills/drafts/${id}/submit`); 
}
export function withdrawSkillDraftApi(id: number) {
 return post<SkillDraft>(`/agent/skills/drafts/${id}/withdraw`); 
}
export function approveSkillDraftApi(id: number) {
 return post<SkillRelease>(`/agent/skills/drafts/${id}/approve`); 
}
export function rejectSkillDraftApi(id: number, comment?: string) {
 return post<SkillDraft>(`/agent/skills/drafts/${id}/reject`, { comment }); 
}
export function listSkillMarketApi() {
 return get<SkillMarket[]>('/agent/skills/market'); 
}
export function unlistSkillMarketApi(name: string) {
 return del<void>(`/agent/skills/market/${encodeURIComponent(name)}`); 
}
export function installSkillApi(name: string) {
 return post<SkillInstall>('/agent/skills/install', { name }); 
}
export function uninstallSkillApi(id: number) {
 return del<void>(`/agent/skills/install/${id}`); 
}
export function listBindableSkillsApi() {
 return get<BindableSkill[]>('/agent/skills/bindable'); 
}
export function getSkillReleaseApi(id: number) {
 return get<SkillRelease>(`/agent/skills/releases/${id}`); 
}
export function deprecateSkillReleaseApi(id: number) {
 return post<SkillRelease>(`/agent/skills/releases/${id}/deprecate`); 
}
export function listGitSkillSourcesApi() {
 return get<GitSkillSource[]>('/agent/skills/git-sources'); 
}
export function createGitSkillSourceApi(body: CreateGitSkillSourceRequest) {
 return post<GitSkillSource>('/agent/skills/git-sources', body); 
}
export function updateGitSkillSourceApi(id: number, body: UpdateGitSkillSourceRequest) {
 return put<GitSkillSource>(`/agent/skills/git-sources/${id}`, body); 
}
export function deleteGitSkillSourceApi(id: number) {
 return del<void>(`/agent/skills/git-sources/${id}`); 
}
export function previewGitSkillSourceApi(id: number) {
 return post<GitSkillPreview>(`/agent/skills/git-sources/${id}/preview`); 
}
export function syncGitSkillSourceApi(id: number, expectedCommitSha: string, skillPaths: string[]) {
 return post<GitSkillSyncResult>(`/agent/skills/git-sources/${id}/sync`, { expectedCommitSha, skillPaths }); 
}
export function createAgentRevisionApi(definitionId: number, body: CreateAgentRevisionRequest) {
 return post<AgentRevision>(`/agent/${definitionId}/revisions`, body); 
}
export function getAgentRevisionApi(id: number) {
 return get<AgentRevision>(`/agent/revisions/${id}`); 
}
export function updateAgentRevisionApi(id: number, body: UpdateAgentRevisionRequest) {
 return put<AgentRevision>(`/agent/revisions/${id}`, body); 
}
export function publishAgentRevisionApi(id: number) {
 return post<AgentRevision>(`/agent/revisions/${id}/publish`); 
}
