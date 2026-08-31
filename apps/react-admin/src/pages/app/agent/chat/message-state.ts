export type ChatRole = 'ai' | 'user' | 'system';

export interface ChatMessage {
  content: string;
  key: string;
  /** 等待首个文本/工具结果时展示三点动效；为 true 时 Bubble 会隐藏 content */
  loading?: boolean;
  /** 正在接收 TEXT_DELTA；为 true 时 Bubble 按流式增量渲染 content */
  streaming?: boolean;
  /** 模型思考过程（来自 THINKING_DELTA） */
  thinking?: string;
  /** 思考过程是否仍在流式更新 */
  thinkingStreaming?: boolean;
  /** 思考耗时（秒），思考结束后写入 */
  thinkingDurationSec?: number;
  /** 首次收到思考增量时的客户端时间戳，用于估算耗时 */
  thinkingStartedAt?: number;
  role: ChatRole;
}

export interface ChatMessageMutateOptions {
  createKey: () => string;
  /** 可注入时间戳，便于单测稳定断言思考耗时 */
  now?: number;
}

function isOpenAssistant(message: ChatMessage): boolean {
  return message.role === 'ai' && Boolean(message.loading || message.streaming || message.thinkingStreaming);
}

function withClosedThinking(message: ChatMessage, now: number): ChatMessage {
  if (!message.thinkingStreaming) {
    return message;
  }
  const startedAt = message.thinkingStartedAt;
  const durationSec =
    typeof startedAt === 'number' ? Math.max(1, Math.round((now - startedAt) / 1000)) : message.thinkingDurationSec;
  return {
    ...message,
    thinkingStreaming: false,
    thinkingDurationSec: durationSec,
  };
}

/** 确保存在一条未完成的 AI 气泡（等待动效）；已有则原样返回。 */
export function ensureAssistantPlaceholder(
  messages: ChatMessage[],
  createKey: () => string,
): ChatMessage[] {
  const lastOpen = [...messages].reverse().find(isOpenAssistant);
  if (lastOpen) {
    return messages;
  }
  return [...messages, { content: '', key: createKey(), loading: true, role: 'ai' }];
}

/**
 * 追加助手思考增量：关闭 loading，打开 thinkingStreaming。
 * 查找最近一条未完成 AI 气泡，避免被中间 TOOL 系统消息打断。
 */
export function appendAssistantThinkingDelta(
  messages: ChatMessage[],
  text: string,
  { createKey, now = Date.now() }: ChatMessageMutateOptions,
): ChatMessage[] {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const item = messages[index];
    if (item && isOpenAssistant(item)) {
      return [
        ...messages.slice(0, index),
        {
          ...item,
          loading: false,
          thinking: (item.thinking ?? '') + text,
          thinkingStreaming: true,
          thinkingStartedAt: item.thinkingStartedAt ?? now,
        },
        ...messages.slice(index + 1),
      ];
    }
  }
  return [
    ...messages,
    {
      content: '',
      key: createKey(),
      loading: false,
      role: 'ai',
      thinking: text,
      thinkingStreaming: true,
      thinkingStartedAt: now,
    },
  ];
}

/**
 * 追加助手文本增量：关闭 loading、打开 streaming；若仍在思考则收尾思考耗时。
 * 查找最近一条未完成 AI 气泡，避免被中间 TOOL 系统消息打断。
 */
export function appendAssistantDelta(
  messages: ChatMessage[],
  text: string,
  { createKey, now = Date.now() }: ChatMessageMutateOptions,
): ChatMessage[] {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const item = messages[index];
    if (item && isOpenAssistant(item)) {
      const closed = withClosedThinking(item, now);
      return [
        ...messages.slice(0, index),
        {
          ...closed,
          content: closed.content + text,
          loading: false,
          streaming: true,
        },
        ...messages.slice(index + 1),
      ];
    }
  }
  return [
    ...messages,
    { content: text, key: createKey(), loading: false, streaming: true, role: 'ai' },
  ];
}

/** 运行终态：关闭 loading/streaming/thinkingStreaming，保留已生成正文与思考。 */
export function finalizeAssistant(messages: ChatMessage[], now: number = Date.now()): ChatMessage[] {
  return messages.map((item) =>
    isOpenAssistant(item)
      ? { ...withClosedThinking(item, now), loading: false, streaming: false }
      : item,
  );
}
