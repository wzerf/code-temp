export type ChatRole = 'ai' | 'user' | 'system';

export interface ChatMessage {
  content: string;
  key: string;
  /** 等待首个文本/工具结果时展示三点动效；为 true 时 Bubble 会隐藏 content */
  loading?: boolean;
  /** 正在接收 TEXT_DELTA；为 true 时 Bubble 按流式增量渲染 content */
  streaming?: boolean;
  role: ChatRole;
}

function isOpenAssistant(message: ChatMessage): boolean {
  return message.role === 'ai' && Boolean(message.loading || message.streaming);
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
 * 追加助手文本增量：关闭 loading、打开 streaming。
 * 查找最近一条未完成 AI 气泡，避免被中间 TOOL 系统消息打断。
 */
export function appendAssistantDelta(
  messages: ChatMessage[],
  text: string,
  createKey: () => string,
): ChatMessage[] {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const item = messages[index];
    if (item && isOpenAssistant(item)) {
      return [
        ...messages.slice(0, index),
        {
          ...item,
          content: item.content + text,
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

/** 运行终态：关闭 loading/streaming，保留已生成正文。 */
export function finalizeAssistant(messages: ChatMessage[]): ChatMessage[] {
  return messages.map((item) =>
    isOpenAssistant(item) ? { ...item, loading: false, streaming: false } : item,
  );
}
