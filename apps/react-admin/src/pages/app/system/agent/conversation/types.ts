import type { AgentSession } from '@/api/rest/types';

/**
 * Agent 对话页前端消息模型与 AG-UI 事件类型。
 *
 * 与后端运行面契约对齐：docs/agent-conversation-architecture.md §6.3。
 * AG-UI 事件 JSON 经 SSE data 行到达（无 event 名），每条 data 是带 `type` 字段的完整事件。
 */

// ---------- AG-UI 事件（后端按 io.agentscope.core.agui.event.AguiEvent 序列化） ----------

/** RUN_FINISHED 的结果分类：success | interrupt */
export interface AguiRunFinishedOutcome {
  type?: 'success' | 'interrupt';
  interrupts?: AguiInterrupt[];
}

/** HITL 中断：等待用户对一次工具调用的决策 */
export interface AguiInterrupt {
  id: string;
  reason: string;
  message?: string | null;
  toolCallId?: string | null;
  responseSchema?: Record<string, unknown> | null;
  expiresAt?: string | null;
  metadata?: Record<string, unknown> | null;
}

/** 单条 AG-UI SSE 事件（type 判别联合；仅覆盖对话页需要渲染/关心的子集） */
export interface AguiEventBase {
  threadId: string;
  runId: string;
  timestamp?: number | null;
  rawEvent?: unknown;
}

export interface AguiRunStarted extends AguiEventBase {
  type: 'RUN_STARTED';
  parentRunId?: string | null;
  /** RunAgentInput：含本轮输入 messages（历史回放时提取 user 原文） */
  input?: AguiRunInputLike | null;
}

/** RunAgentInput 的宽松形态（仅回放需读取的字段） */
export interface AguiRunInputLike {
  threadId?: string;
  runId?: string;
  messages?: AguiInputMessage[];
  resume?: unknown[];
}

/** AG-UI 消息（宽松：回放提取 role/content 用） */
export interface AguiInputMessage {
  id?: string;
  role?: string;
  content?:
    | string
    | { value?: string; parts?: Array<{ type?: string; text?: string; content?: unknown }> }
    | null;
  toolCalls?: unknown[];
  toolCallId?: string | null;
}

/** 从 AG-UI 消息中提取纯文本（兼容 Text.value / Blocks.parts[].text） */
export function aguiMessageText(message: AguiInputMessage | undefined): string {
  if (!message) return '';
  const c = message.content;
  if (typeof c === 'string') return c;
  if (!c) return '';
  if (typeof c.value === 'string') return c.value;
  if (Array.isArray(c.parts)) {
    return c.parts
      .map((p) => {
        if (typeof p.text === 'string') return p.text;
        if (typeof p.content === 'string') return p.content;
        return '';
      })
      .join('');
  }
  return '';
}

export interface AguiRunFinished extends AguiEventBase {
  type: 'RUN_FINISHED';
  result?: unknown;
  outcome?: AguiRunFinishedOutcome;
}

export interface AguiRunError extends AguiEventBase {
  type: 'RUN_ERROR';
  message: string;
  code?: string | null;
}

export interface AguiTextMessageStart extends AguiEventBase {
  type: 'TEXT_MESSAGE_START';
  messageId: string;
  role: string;
}

export interface AguiTextMessageContent extends AguiEventBase {
  type: 'TEXT_MESSAGE_CONTENT';
  messageId: string;
  delta: string;
}

export interface AguiTextMessageEnd extends AguiEventBase {
  type: 'TEXT_MESSAGE_END';
  messageId: string;
}

export interface AguiTextMessageChunk extends AguiEventBase {
  type: 'TEXT_MESSAGE_CHUNK';
  messageId?: string;
  role?: string;
  delta?: string;
  name?: string;
}

export interface AguiReasoningMessageStart extends AguiEventBase {
  type: 'REASONING_MESSAGE_START' | 'REASONING_START';
  messageId: string;
  role?: string;
}

export interface AguiReasoningMessageContent extends AguiEventBase {
  type: 'REASONING_MESSAGE_CONTENT' | 'REASONING_MESSAGE_CHUNK';
  messageId?: string;
  delta: string;
}

export interface AguiReasoningMessageEnd extends AguiEventBase {
  type: 'REASONING_MESSAGE_END' | 'REASONING_END';
  messageId: string;
}

export interface AguiToolCallStart extends AguiEventBase {
  type: 'TOOL_CALL_START' | 'TOOL_CALL_CHUNK';
  toolCallId: string;
  toolCallName?: string;
  messageId?: string;
}

export interface AguiToolCallArgs extends AguiEventBase {
  type: 'TOOL_CALL_ARGS';
  toolCallId: string;
  delta: string;
}

export interface AguiToolCallEnd extends AguiEventBase {
  type: 'TOOL_CALL_END';
  toolCallId: string;
}

export interface AguiToolCallResult extends AguiEventBase {
  type: 'TOOL_CALL_RESULT';
  toolCallId: string;
  content?: string | null;
  role?: string;
  messageId?: string;
}

export interface AguiCustom extends AguiEventBase {
  type: 'CUSTOM';
  name: string;
  value?: unknown;
}

export type AguiEvent =
  | AguiRunStarted
  | AguiRunFinished
  | AguiRunError
  | AguiTextMessageStart
  | AguiTextMessageContent
  | AguiTextMessageEnd
  | AguiTextMessageChunk
  | AguiReasoningMessageStart
  | AguiReasoningMessageContent
  | AguiReasoningMessageEnd
  | AguiToolCallStart
  | AguiToolCallArgs
  | AguiToolCallEnd
  | AguiToolCallResult
  | AguiCustom
  | (AguiEventBase & { type: string });

/** XRequest SSE 解析产物：`{ data: '<json>' }`，data 为 AG-UI 事件 JSON 字符串 */
export interface SseDataChunk {
  data?: string;
  event?: string;
  id?: string;
}

/** 判断未知对象是否为 AG-UI 事件 JSON（具备 type 字段） */
export function isAguiEventJson(value: unknown): value is AguiEvent {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as { type?: unknown }).type === 'string'
  );
}

/** 解析 XRequest 的 SSE chunk（{data: string} 或纯对象）为 AG-UI 事件 */
export function parseAguiChunk(chunk: unknown): AguiEvent | null {
  if (!chunk || typeof chunk !== 'object') return null;
  const data = (chunk as SseDataChunk).data;
  if (typeof data === 'string' && data.trim()) {
    try {
      const parsed: unknown = JSON.parse(data.trim());
      return isAguiEventJson(parsed) ? parsed : null;
    } catch {
      return null;
    }
  }
  return isAguiEventJson(chunk) ? (chunk as AguiEvent) : null;
}

// ---------- 会话模型 ----------

/** 会话列表项：对话侧会话栏数据（标题取 remark；无标题自动生成） */
export interface AgentConversationItem {
  key: string;
  label: string;
  session: AgentSession;
  /** 是否仍在会话缓存中可恢复消息 */
  cached?: boolean;
}

/**
 * 草稿会话：进入页面或点击「新建会话」时只进入草稿态而不创建后端会话，
 * 发送第一条消息时才真实创建。此时尚无后端 id，provider 尚不可用。
 */
export interface DraftConversation {
  id: null;
  draft: true;
  agentDefinitionId: number;
  /** 给 useXChat 用的唯一 conversationKey，避免草稿共用 undefined/Symbol */
  draftKey: string;
}

// ---------- 前端消息模型（useXChat 的 ChatMessage） ----------

export interface ToolCallView {
  id: string;
  name: string;
  argsText: string;
  /** TOOL_CALL_RESULT 的内容摘要 */
  resultText?: string | null;
  status?: 'running' | 'done' | 'error';
}

/** 后端 RunAgentInput 请求体（XRequest 发送的 body） */
export interface AguiRunRequestBody {
  threadId: string;
  runId: string;
  messages: AguiInputMessage[];
  resume?: AguiResumeItem[];
  /** UI 侧字段：本轮发送文本（发送前被吸收进 messages，不直达后端） */
  content?: string;
}

/** HITL resume 项 */
export interface AguiResumeItem {
  interruptId: string;
  status: 'resolved' | 'cancelled';
  payload?: Record<string, unknown>;
}

/** 助手消息内容（含流式过程中的 thinking / toolCalls 折叠） */
export interface AssistantContent {
  role: 'assistant';
  /** 最终正文（Markdown） */
  content: string;
  /** 思考链文本（REASONING_* delta 累积） */
  thinking?: string;
  /** 本 turn 内工具调用节点（按顺序） */
  toolCalls?: ToolCallView[];
  /** RUN_ERROR 的错误信息 */
  error?: string;
  /** 触发 HITL 中断（RUN_FINISHED outcome.interrupts） */
  interrupts?: AguiInterrupt[];
  /** 是否仍在等待 HITL 决策 */
  waitingForApproval?: boolean;
}

/** 用户消息内容 */
export interface UserContent {
  role: 'user';
  content: string;
}

/** useXChat 的消息类型 */
export type AgentChatMessage = UserContent | AssistantContent;
