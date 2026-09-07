import type {
  AguiInputMessage,
  AssistantContent,
  AguiEvent,
  ToolCallView,
} from './types';
import { aguiMessageText } from './types';

/**
 * AG-UI 事件 → assistant 消息增量应用（纯函数，基于前一个消息内容）。
 *
 * 对齐 docs/agent-conversation-architecture.md §6.3 的映射表：
 * - REASONING_* → thinking 累积
 * - TOOL_CALL_* → toolCalls 节点
 * - TEXT_MESSAGE_* → content 累积
 * - RUN_FINISHED(outcome.interrupts) → interrupts（HITL）
 * - RUN_ERROR → error
 */

/** 空助手消息（requestPlaceholder 初始值） */
export function emptyAssistant(): AssistantContent {
  return { role: 'assistant', content: '', toolCalls: [] };
}

/** 把一个 AG-UI 事件应用到当前 assistant 内容，返回新内容。 */
function normalizeTs(ts?: number | null): number {
  if (ts == null) return Date.now();
  return ts < 1e12 ? ts * 1000 : ts;
}

export function applyAguiEvent(prev: AssistantContent | undefined, event: AguiEvent): AssistantContent {
  const base: AssistantContent = prev && prev.role === 'assistant' ? { ...prev } : emptyAssistant();
  base.toolCalls = base.toolCalls ? [...base.toolCalls] : [];
  base.generatedImages = base.generatedImages ? [...base.generatedImages] : undefined;

  switch (event.type) {
    case 'RUN_STARTED':
      return base;

    case 'REASONING_START':
    case 'REASONING_MESSAGE_START':
      base.thinkingStartedAt ??= normalizeTs(event.timestamp);
      return base;

    case 'REASONING_END':
    case 'REASONING_MESSAGE_END':
      base.thinkingEndedAt = normalizeTs(event.timestamp);
      return base;

    case 'REASONING_MESSAGE_CONTENT':
    case 'REASONING_MESSAGE_CHUNK': {
      const delta = (event as { delta?: string }).delta ?? '';
      base.thinking = `${base.thinking ?? ''}${delta}`;
      return base;
    }

    case 'TOOL_CALL_START':
    case 'TOOL_CALL_CHUNK': {
      const ev = event as {
        toolCallId: string;
        toolCallName?: string;
        type: string;
      };
      const tools = base.toolCalls ?? [];
      if (!tools.some((t) => t.id === ev.toolCallId)) {
        tools.push({
          id: ev.toolCallId,
          name: ev.toolCallName || (ev.type === 'TOOL_CALL_CHUNK' ? '(tool)' : ''),
          argsText: '',
          status: 'running',
          startedAt: normalizeTs(event.timestamp),
        });
        base.toolCalls = tools;
      }
      return base;
    }

    case 'TOOL_CALL_ARGS': {
      const ev = event as { toolCallId: string; delta: string };
      const tool = findTool(base, ev.toolCallId);
      if (tool) {
        tool.argsText = `${tool.argsText ?? ''}${ev.delta ?? ''}`;
      }
      return base;
    }

    case 'TOOL_CALL_END': {
      const ev = event as { toolCallId: string };
      const tool = findTool(base, ev.toolCallId);
      if (tool) {
        tool.status = 'done';
        tool.endedAt = normalizeTs(event.timestamp);
      }
      return base;
    }

    case 'TOOL_CALL_RESULT': {
      const ev = event as { toolCallId: string; content?: string | null; result?: unknown; output?: unknown };
      const tool = findTool(base, ev.toolCallId);
      if (tool) {
        const dedupKey = new Set<string>();
        const pushImage = (mime: string | undefined, b64: string | undefined) => {
          if (!b64 || b64.length < 80) return;
          const key = `${mime ?? 'image/png'}:${b64.slice(0, 32)}:${b64.length}`;
          if (dedupKey.has(key)) return;
          if (base.generatedImages?.some((x) => x.b64 === b64)) return;
          dedupKey.add(key);
          const m = mime ?? 'image/png';
          base.generatedImages ??= [];
          base.generatedImages.push({ mimeType: m, b64 });
        };
        const tryExtractImages = (value: unknown) => {
          if (value == null) return;
          if (typeof value === 'object') {
            const walk = (node: unknown) => {
              if (node == null) return;
              if (typeof node === 'string') {
                if (node.startsWith('data:image')) {
                  const m = node.match(/^data:([^;]+);base64,(.+)$/);
                  if (m) pushImage(m[1], m[2]);
                } else if (node.length > 80 && /^[A-Za-z0-9+/=]+$/.test(node.slice(0, 80))) {
                  // 可能是纯 base64 大段
                }
                return;
              }
              if (Array.isArray(node)) {
 node.forEach(walk); return; 
}
              if (typeof node !== 'object') return;
              const rec = node as Record<string, unknown>;
              if (rec.type === 'image' && rec.source && typeof rec.source === 'object') {
                const s = rec.source as Record<string, unknown>;
                const d = (s.data ?? s.base64) as string | undefined;
                const mt = (s.media_type ?? s.mimeType ?? rec.media_type ?? rec.mimeType) as string | undefined;
                if (d) pushImage(mt, d);
              }
              const mt2 = (rec.media_type ?? rec.mimeType) as string | undefined;
              const b64_2 = (rec.data ?? rec.base64 ?? rec.b64_json) as string | undefined;
              if (b64_2 && b64_2.length >= 80 && (rec.type === 'base64' || mt2?.startsWith('image/') || rec.type === 'image')) {
                pushImage(mt2, b64_2);
              }
              Object.values(rec).forEach(walk);
            };
            walk(value);
            return;
          }
          const str = value as string;
          if (str.length < 10) return;
          // 字符串：先尝试按 JSON 解析再走对象逻辑，避免正则全扫超大字符串
          const trimmed = str.trim();
          if ((trimmed.startsWith('{') || trimmed.startsWith('[')) && trimmed.length < 500000) {
            try {
              const parsed = JSON.parse(trimmed);
              tryExtractImages(parsed);
              return;
            } catch { /* fallback to regex */ }
          }
          const reDataUrl = /data:(image\/[^;]+);base64,([A-Za-z0-9+/=]{80,})/g;
          let m: RegExpExecArray | null;
          while ((m = reDataUrl.exec(str)) !== null) pushImage(m[1], m[2]);
          const reB64 = /"b64_json"\s*:\s*"([A-Za-z0-9+/=]{80,})"/g;
          while ((m = reB64.exec(str)) !== null) pushImage('image/png', m[1]);
          const reData = /"data"\s*:\s*"([A-Za-z0-9+/=]{80,})"/g;
          while ((m = reData.exec(str)) !== null) {
            const b64 = m[1];
            const start = Math.max(0, (m.index ?? 0) - 500);
            const head = str.slice(start, m.index ?? 0);
            const mtMatch = head.match(/"(media_type|mimeType|mime_type)"\s*:\s*"(image\/[^"]+)"/);
            pushImage(mtMatch?.[2], b64);
          }
        };
        tryExtractImages((ev as { result?: unknown }).result);
        tryExtractImages((ev as { output?: unknown }).output);
        tryExtractImages(ev.content);
        if (!base.generatedImages?.length) {
          tryExtractImages(event as unknown);
        }
        const rawContent = ev.content;
        const isImagePayload = rawContent != null && String(rawContent).length > 800 && (String(rawContent).includes('"type":"image"') || String(rawContent).includes('b64_json'));
        tool.resultText = isImagePayload
          ? '图片已生成（见下方预览）'
          : (rawContent != null && String(rawContent).length > 600
              ? `${String(rawContent).slice(0, 600)}...`
              : (rawContent ?? null));
        const endTs = normalizeTs(event.timestamp);
        if (tool.status === 'running') {
          tool.status = 'done';
          tool.endedAt = endTs;
        } else if (tool.endedAt == null) {
          tool.endedAt = endTs;
        }
        if (tool.startedAt != null && tool.endedAt != null && tool.endedAt <= tool.startedAt) {
          tool.endedAt = tool.startedAt + Math.max(1, endTs - tool.startedAt);
          if (tool.endedAt <= tool.startedAt) tool.endedAt = Date.now();
        }
      }
      return base;
    }

    case 'TEXT_MESSAGE_START':
      return base;

    case 'TEXT_MESSAGE_CONTENT': {
      const ev = event as { delta: string };
      base.content = `${base.content ?? ''}${ev.delta ?? ''}`;
      return base;
    }

    case 'TEXT_MESSAGE_CHUNK': {
      const ev = event as { delta?: string };
      if (ev.delta) {
        base.content = `${base.content ?? ''}${ev.delta}`;
      }
      return base;
    }

    case 'TEXT_MESSAGE_END':
      return base;

    case 'RUN_FINISHED': {
      const outcome = (event as { outcome?: { type?: string; interrupts?: unknown[] } }).outcome;
      const interrupts = Array.isArray(outcome?.interrupts)
        ? (outcome.interrupts as AssistantContent['interrupts'])
        : undefined;
      if (interrupts && interrupts.length > 0) {
        base.interrupts = interrupts;
        base.waitingForApproval = true;
      } else {
        delete base.error;
      }
      return base;
    }

    case 'RUN_ERROR': {
      const ev = event as { message: string };
      base.error = ev.message ?? '运行失败';
      return base;
    }

    case 'CUSTOM':
      return base;

    default:
      return base;
  }
}

/** 历史回放：把一串已持久化事件重放成一轮轮对话。
 *  一轮 = 一条 user 消息（从 RUN_STARTED.input.messages 提取）+ 对应 assistant 内容。 */
export interface ReplayedTurn {
  userContent: string;
  assistant: AssistantContent;
  events: AguiEvent[];
}

/** 从 RUN_STARTED 的 input 提取本轮最新 user 文本 */
function userTextFromRunStarted(event: AguiEvent): string {
  const input = (event as { input?: { messages?: AguiInputMessage[] } }).input;
  const msgs = input?.messages;
  if (!Array.isArray(msgs)) return '';
  for (let i = msgs.length - 1; i >= 0; i -= 1) {
    const m = msgs[i];
    if (m && m.role === 'user') {
      const text = aguiMessageText(m);
      if (text.trim()) return text.trim();
    }
  }
  return '';
}

export function replayEvents(events: AguiEvent[]): ReplayedTurn[] {
  const turns: ReplayedTurn[] = [];
  let current: ReplayedTurn | null = null;
  for (const event of events) {
    if (event.type === 'RUN_STARTED') {
      current = {
        userContent: userTextFromRunStarted(event),
        assistant: emptyAssistant(),
        events: [],
      };
      turns.push(current);
      continue;
    }
    if (!current) {
      // 事件流未以 RUN_STARTED 开头（异常 run 的错误流），忽略其过程事件
      if (event.type === 'RUN_FINISHED' || event.type === 'RUN_ERROR') {
        continue;
      }
      continue;
    }
    current.assistant = applyAguiEvent(current.assistant, event);
    current.events.push(event);
    if (event.type === 'RUN_FINISHED' || event.type === 'RUN_ERROR') {
      // 该 turn 结束；后续事件属于下一 run
      current = null;
    }
  }
  return turns;
}

function findTool(content: AssistantContent, toolCallId: string): ToolCallView | undefined {
  return content.toolCalls?.find((t) => t.id === toolCallId);
}
