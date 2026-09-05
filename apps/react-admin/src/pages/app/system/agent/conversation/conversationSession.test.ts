import { describe, expect, it } from 'vitest';
import type { AgentSession } from '@/api/rest/types';
import {
  FALLBACK_CONVERSATION_KEY,
  createDraft,
  isConversationHistoryReady,
  isDraftConversation,
  mergeSessionAfterModelBind,
  resolveChatConversationKey,
  shouldFlushPendingSend,
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

describe('mergeSessionAfterModelBind', () => {
  it('绑定返回同 id 会话时合并字段，保留会话 id', () => {
    const created = { ...session(42), remark: '新对话', modelReleaseId: null };
    const bound = { ...session(42), remark: '新对话', modelReleaseId: 7 };
    const merged = mergeSessionAfterModelBind(created, bound, 7);
    expect(merged.id).toBe(42);
    expect(merged.modelReleaseId).toBe(7);
  });

  it('绑定返回其它 id（例如旧绑定表主键）时不得替换会话 id', () => {
    const created = { ...session(42), remark: '新对话', modelReleaseId: null };
    const bound = { id: 999, modelReleaseId: 7 } as AgentSession;
    const merged = mergeSessionAfterModelBind(created, bound, 7);
    expect(merged.id).toBe(42);
    expect(merged.remark).toBe('新对话');
    expect(merged.modelReleaseId).toBe(7);
  });

  it('绑定返回空对象时仍用创建结果晋升，否则 conversationKey 对不上 queueRequest', () => {
    const created = session(42);
    const merged = mergeSessionAfterModelBind(created, {} as AgentSession, 3);
    expect(merged.id).toBe(42);
    expect(resolveChatConversationKey(merged)).toBe('42');
  });
});

describe('shouldFlushPendingSend', () => {
  it('仅当待发送会话已晋升且历史不再 loading 时才刷出', () => {
    const pending = { sessionId: 42, content: 'hi' };
    expect(shouldFlushPendingSend(pending, 42, true)).toBe(false);
    expect(shouldFlushPendingSend(pending, 42, false)).toBe(true);
    expect(shouldFlushPendingSend(pending, 7, false)).toBe(false);
    expect(shouldFlushPendingSend(null, 42, false)).toBe(false);
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
