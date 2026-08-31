import { describe, expect, it } from 'vitest';
import {
  appendAssistantDelta,
  appendAssistantThinkingDelta,
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

  it('THINKING_DELTA 关闭 loading、打开 thinkingStreaming，并增量拼接', () => {
    resetKeys();
    const waiting: ChatMessage[] = [
      { content: '问', key: 'u1', role: 'user' },
      { content: '', key: 'a1', loading: true, role: 'ai' },
    ];
    const first = appendAssistantThinkingDelta(waiting, '先', { createKey: nextKey, now: 1_000 });
    expect(first.at(-1)).toEqual({
      content: '',
      key: 'a1',
      loading: false,
      role: 'ai',
      thinking: '先',
      thinkingStreaming: true,
      thinkingStartedAt: 1_000,
    });
    const second = appendAssistantThinkingDelta(first, '想', { createKey: nextKey, now: 1_500 });
    expect(second.at(-1)?.thinking).toBe('先想');
    expect(second.at(-1)?.thinkingStartedAt).toBe(1_000);
  });

  it('TEXT_DELTA 关闭 loading、打开 streaming，并增量拼接', () => {
    resetKeys();
    const waiting: ChatMessage[] = [
      { content: '问', key: 'u1', role: 'user' },
      { content: '', key: 'a1', loading: true, role: 'ai' },
    ];
    const first = appendAssistantDelta(waiting, '你', { createKey: nextKey });
    expect(first.at(-1)).toEqual({
      content: '你',
      key: 'a1',
      loading: false,
      streaming: true,
      role: 'ai',
    });
    const second = appendAssistantDelta(first, '好', { createKey: nextKey });
    expect(second.at(-1)?.content).toBe('你好');
    expect(second.at(-1)?.streaming).toBe(true);
  });

  it('正文增量到达时收尾思考耗时', () => {
    resetKeys();
    const thinking: ChatMessage[] = [
      {
        content: '',
        key: 'a1',
        loading: false,
        role: 'ai',
        thinking: '推理中',
        thinkingStreaming: true,
        thinkingStartedAt: 1_000,
      },
    ];
    const next = appendAssistantDelta(thinking, '答案', { createKey: nextKey, now: 4_200 });
    expect(next.at(-1)).toMatchObject({
      content: '答案',
      streaming: true,
      thinking: '推理中',
      thinkingStreaming: false,
      thinkingDurationSec: 3,
    });
  });

  it('工具系统消息插入后仍追加到最近未完成 AI 气泡', () => {
    resetKeys();
    const messages: ChatMessage[] = [
      { content: '问', key: 'u1', role: 'user' },
      { content: '', key: 'a1', loading: true, role: 'ai' },
      { content: '正在调用工具：get_platform_time', key: 's1', role: 'system' },
    ];
    const next = appendAssistantDelta(messages, '现在是', { createKey: nextKey });
    expect(next).toEqual([
      { content: '问', key: 'u1', role: 'user' },
      { content: '现在是', key: 'a1', loading: false, streaming: true, role: 'ai' },
      { content: '正在调用工具：get_platform_time', key: 's1', role: 'system' },
    ]);
  });

  it('终态关闭 loading/streaming/thinkingStreaming 并保留思考', () => {
    const messages: ChatMessage[] = [
      {
        content: '答',
        key: 'a1',
        loading: false,
        streaming: true,
        role: 'ai',
        thinking: '想过',
        thinkingStreaming: true,
        thinkingStartedAt: 1_000,
      },
    ];
    expect(finalizeAssistant(messages, 3_500)).toEqual([
      {
        content: '答',
        key: 'a1',
        loading: false,
        streaming: false,
        role: 'ai',
        thinking: '想过',
        thinkingStreaming: false,
        thinkingDurationSec: 3,
        thinkingStartedAt: 1_000,
      },
    ]);
  });
});
