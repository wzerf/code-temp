import { describe, expect, it } from 'vitest';
import {
  appendAssistantDelta,
  ensureAssistantPlaceholder,
  finalizeAssistant,
  type ChatMessage,
} from './message-state';

let seq = 0;
const nextKey = () => `k-${++seq}`;

function resetKeys(): void {
  seq = 0;
}

describe('agent chat message-state', () => {
  it('发送后 ensure 出 loading 占位，重复调用保持幂等', () => {
    resetKeys();
    const user: ChatMessage = { content: '你好', key: 'u1', role: 'user' };
    const once = ensureAssistantPlaceholder([user], nextKey);
    expect(once).toEqual([
      user,
      { content: '', key: 'k-1', loading: true, role: 'ai' },
    ]);
    expect(ensureAssistantPlaceholder(once, nextKey)).toBe(once);
  });

  it('TEXT_DELTA 关闭 loading、打开 streaming，并增量拼接', () => {
    resetKeys();
    const waiting: ChatMessage[] = [
      { content: '问', key: 'u1', role: 'user' },
      { content: '', key: 'a1', loading: true, role: 'ai' },
    ];
    const first = appendAssistantDelta(waiting, '你', nextKey);
    expect(first.at(-1)).toEqual({
      content: '你',
      key: 'a1',
      loading: false,
      streaming: true,
      role: 'ai',
    });
    const second = appendAssistantDelta(first, '好', nextKey);
    expect(second.at(-1)?.content).toBe('你好');
    expect(second.at(-1)?.streaming).toBe(true);
  });

  it('工具系统消息插入后仍追加到最近未完成 AI 气泡', () => {
    resetKeys();
    const messages: ChatMessage[] = [
      { content: '问', key: 'u1', role: 'user' },
      { content: '', key: 'a1', loading: true, role: 'ai' },
      { content: '正在调用工具：get_platform_time', key: 's1', role: 'system' },
    ];
    const next = appendAssistantDelta(messages, '现在是', nextKey);
    expect(next).toEqual([
      { content: '问', key: 'u1', role: 'user' },
      { content: '现在是', key: 'a1', loading: false, streaming: true, role: 'ai' },
      { content: '正在调用工具：get_platform_time', key: 's1', role: 'system' },
    ]);
  });

  it('终态关闭 loading/streaming', () => {
    const messages: ChatMessage[] = [
      { content: '答', key: 'a1', loading: false, streaming: true, role: 'ai' },
    ];
    expect(finalizeAssistant(messages)).toEqual([
      { content: '答', key: 'a1', loading: false, streaming: false, role: 'ai' },
    ]);
  });
});
