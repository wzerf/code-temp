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
export function applyAguiEvent(prev: AssistantContent | undefined, event: AguiEvent): AssistantContent {
  const base: AssistantContent = prev && prev.role === 'assistant' ? { ...prev } : emptyAssistant();
  // 结构拷贝嵌套数组，避免改到不可变消息树
  base.toolCalls = base.toolCalls ? [...base.toolCalls] : [];

  switch (event.type) {
    case 'RUN_STARTED':
      return base;

    case 'REASONING_START':
    case 'REASONING_MESSAGE_START':
    case 'REASONING_END':
    case 'REASONING_MESSAGE_END':
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
      // 同一 toolCallId 重复 START（chunk 模式）不重复插入
      const tools = base.toolCalls ?? [];
      if (!tools.some((t) => t.id === ev.toolCallId)) {
        tools.push({
          id: ev.toolCallId,
          name: ev.toolCallName || (ev.type === 'TOOL_CALL_CHUNK' ? '(tool)' : ''),
          argsText: '',
          status: 'running',
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
      }
      return base;
    }

    case 'TOOL_CALL_RESULT': {
      const ev = event as { toolCallId: string; content?: string | null };
      const tool = findTool(base, ev.toolCallId);
      if (tool) {
        tool.resultText = ev.content ?? null;
        tool.status = tool.status === 'running' ? 'done' : tool.status;
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
