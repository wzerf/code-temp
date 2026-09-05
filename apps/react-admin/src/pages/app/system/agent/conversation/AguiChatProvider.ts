import { AbstractChatProvider } from '@ant-design/x-sdk';
import XRequest from '@ant-design/x-sdk/es/x-request';
import type { MessageStatus } from '@ant-design/x-sdk/es/x-chat';

import { useAuthStore } from '@/stores/auth';
import { applyAguiEvent, emptyAssistant } from './AguiEventMapper';
import type {
  AgentChatMessage,
  AguiRunRequestBody,
  AssistantContent,
  AguiEvent,
} from './types';

/**
 * AG-UI Chat Provider：消费后端 `POST /sessions/{sessionId}/events` 的标准 AG-UI SSE 流。
 *
 * x-sdk 未内置 AG-UI 协议解析（架构蓝图 §6.4 的 `protocol:'agui'` 未在真实 SDK 落地），
 * 故继承 AbstractChatProvider 自研：把 AG-UI 事件流逐条喂给 useXChat，经 AguiEventMapper
 * 增量更新到 assistant 占位消息。
 */

/** SSE 文本块 → AG-UI 事件对象的 TransformStream（兼容 \r\n 与任意分块） */
function createAguiSseTransform(): TransformStream<string, AguiEvent> {
  let buffer = '';
  return new TransformStream<string, AguiEvent>({
    transform(chunk, controller) {
      buffer += chunk;
      // 事件分隔：空行 \n\n 或 \r\n\r\n
      const parts = buffer.split(/\r?\n\r?\n/);
      buffer = parts.pop() ?? '';
      for (const part of parts) {
        const event = parseSsePart(part);
        if (event) controller.enqueue(event);
      }
    },
    flush(controller) {
      if (buffer.trim()) {
        const event = parseSsePart(buffer);
        if (event) controller.enqueue(event);
      }
    },
  });
}

function parseSsePart(part: string): AguiEvent | null {
  const dataLines: string[] = [];
  for (const line of part.split(/\r?\n/)) {
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''));
    }
  }
  if (dataLines.length === 0) return null;
  const raw = dataLines.join('\n');
  try {
    const parsed = JSON.parse(raw) as AguiEvent;
    return parsed && typeof parsed.type === 'string' ? parsed : null;
  } catch {
    return null;
  }
}

/** 从全局 auth store 取 token（每次创建 request 时读取，拿到最新值） */
function getAuthToken(): string | null {
  try {
    return useAuthStore.getState().accessToken;
  } catch {
    return null;
  }
}

export class AguiChatProvider extends AbstractChatProvider<AgentChatMessage, AguiRunRequestBody, AguiEvent> {
  readonly sessionId: number | string;

  /** 终态是否已到（RUN_FINISHED/RUN_ERROR）。后端 complete 后连接可能不主动关闭，
   *  前端收到终态即主动 abort，保证 isRequesting 收敛。 */
  finalized = false;

  constructor(sessionId: number | string) {
    super({
      request: () => createAguiRequest(sessionId),
    });
    this.sessionId = sessionId;
  }

  /**
   * 组装完整 RunAgentInput 请求体（XRequest 会把返回值作为 body 发送）。
   * 历史消息经 getMessages() 取自当前会话 store；本轮 user 消息已在调用前入 store。
   */
  transformParams(requestParams: Partial<AguiRunRequestBody>): AguiRunRequestBody {
    const history: AgentChatMessage[] = this.getMessages() ?? [];
    const messages = history
      .filter((m) => {
        if (m.role === 'user') return m.content.trim().length > 0;
        return true;
      })
      .map((m, index) => {
        const id = `msg-${index}`;
        if (m.role === 'user') {
          return { id, role: 'user', content: m.content };
        }
        // assistant：还原最终文本；工具调用一并还原（ReAct 多轮上下文需要）
        const toolCalls = (m.toolCalls ?? [])
          .filter((t) => t.name)
          .map((t) => ({
            id: t.id,
            type: 'function' as const,
            function: {
              name: t.name,
              arguments: t.argsText || '{}',
            },
          }));
        return {
          id,
          role: 'assistant',
          content: m.content ?? '',
          toolCalls: toolCalls.length > 0 ? toolCalls : undefined,
        };
      });
    return {
      threadId: String(this.sessionId),
      runId: requestParams.resume?.length ? `resume-${Date.now()}` : `run-${Date.now()}`,
      messages,
      ...(requestParams.resume?.length ? { resume: requestParams.resume } : {}),
    };
  }

  transformLocalMessage(requestParams: Partial<AguiRunRequestBody>): AgentChatMessage[] {
    const content = requestParams.content?.trim() ?? '';
    if (!content) return [];
    return [{ role: 'user', content }];
  }

  transformMessage(info: {
    originMessage?: AgentChatMessage;
    chunk: AguiEvent;
    chunks: AguiEvent[];
    status: MessageStatus;
  }): AgentChatMessage {
    const prev: AssistantContent | undefined =
      info.originMessage && info.originMessage.role === 'assistant'
        ? (info.originMessage as AssistantContent)
        : undefined;
    // useXChat 在流结束（onSuccess）时会带 chunk=undefined 再调一次：
    // 终态刷新不改变内容，直接返回当前消息避免误触发
    if (!info.chunk || typeof info.chunk !== 'object') {
      return prev ?? emptyAssistant();
    }
    const next = applyAguiEvent(prev, info.chunk);
    // 收到终态事件即视为本 run 结束：主动关闭连接（后端 complete 后可能不主动断）
    if (info.chunk.type === 'RUN_FINISHED' || info.chunk.type === 'RUN_ERROR') {
      if (!this.finalized) {
        this.finalized = true;
        // 等本次 onUpdate 渲染完再 abort，避免竞态
        setTimeout(() => {
          try {
            this.request.abort();
          } catch {
            // 忽略未初始化
          }
        }, 0);
      }
    }
    return next;
  }
}

/** 构造绑定额外请求头（Authorization）的 manual XRequest */
function createAguiRequest(sessionId: number | string) {
  const token = getAuthToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return XRequest<AguiRunRequestBody, AguiEvent>(`/api/system/agent/sessions/${sessionId}/events`, {
    manual: true,
    method: 'POST',
    headers,
    transformStream: () => createAguiSseTransform(),
  });
}

/** 创建绑定会话的 provider（跨 render 稳定：调用方 useMemo） */
export function createAguiProvider(sessionId: number | string): AguiChatProvider {
  return new AguiChatProvider(sessionId);
}
