import { describe, expect, it } from 'vitest';
import type { AgentSession } from '@/api/rest/types';
import {
  FALLBACK_CONVERSATION_KEY,
  createDraft,
  isConversationHistoryReady,
  isDraftConversation,
  resolveChatConversationKey,
} from './conversationSession';

function session(id: number): AgentSession {
  return { id } as AgentSession;
}

describe('resolveChatConversationKey', () => {
  it('真实会话使用后端 id，且始终为 truthy 字符串', () => {
    expect(resolveChatConversationKey(session(42))).toBe('42');
  });

  it('每次草稿使用唯一 key，避免 SDK 在 undefined key 下卡住内部 conversationKey', () => {
    const a = createDraft(1);
    const b = createDraft(1);
    expect(a.draftKey).toMatch(/^draft-\d+$/);
    expect(b.draftKey).not.toBe(a.draftKey);
    expect(resolveChatConversationKey(a)).toBe(a.draftKey);
    expect(resolveChatConversationKey(b)).toBe(b.draftKey);
    expect(resolveChatConversationKey(a)).toBeTruthy();
  });

  it('无会话回落到稳定占位 key，而不是 undefined', () => {
    expect(resolveChatConversationKey(null)).toBe(FALLBACK_CONVERSATION_KEY);
  });
});

describe('isDraftConversation', () => {
  it('仅识别 draft: true 的草稿', () => {
    expect(isDraftConversation(createDraft(7))).toBe(true);
    expect(isDraftConversation(session(7))).toBe(false);
    expect(isDraftConversation(null)).toBe(false);
  });
});

describe('isConversationHistoryReady', () => {
  it('草稿/无会话不转圈', () => {
    expect(isConversationHistoryReady(null, true)).toBe(true);
    expect(isConversationHistoryReady(undefined, true)).toBe(true);
  });

  it('真实会话在 defaultMessages 请求中时未就绪（避免空列表闪一下）', () => {
    expect(isConversationHistoryReady(12, true)).toBe(false);
  });

  it('真实会话 defaultMessages 结束后就绪（含 store 已缓存、不再回调的情况）', () => {
    expect(isConversationHistoryReady(12, false)).toBe(true);
  });
});
