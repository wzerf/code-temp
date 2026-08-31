import { get, post } from './request';
import type {
  AgentDefinition,
  AgentRunEvent,
  AgentSession,
  CancelAgentRunRequest,
  CreateAgentMessageRequest,
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

export function getAgentSessionApi(sessionId: number) {
  return get<AgentSession>(`/agent/sessions/${sessionId}`);
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
