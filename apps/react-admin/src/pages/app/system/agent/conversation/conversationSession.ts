import type { AgentSession } from '@/api/rest/types';
import type { DraftConversation } from './types';

export type ConversationSelection = AgentSession | DraftConversation | null;

/** 无会话时的占位 key；显式草稿使用 createDraft() 生成的唯一 draftKey */
export const FALLBACK_CONVERSATION_KEY = 'draft';

let draftSeq = 0;

/** 构造草稿会话：每次新建使用唯一 conversationKey，避免复用旧草稿 store，也不误清真实会话消息 */
export function createDraft(agentDefinitionId: number): DraftConversation {
  draftSeq += 1;
  return {
    id: null,
    draft: true,
    agentDefinitionId,
    draftKey: `draft-${draftSeq}`,
  };
}

export function isDraftConversation(
  session: ConversationSelection,
): session is DraftConversation {
  return session != null && 'draft' in session && session.draft === true;
}

/**
 * useXChat 的 conversationKey 必须始终是 truthy 字符串。
 * 传入 undefined 时 SDK 会生成 Symbol，且切回草稿时不会覆盖内部 key，
 * 导致后续历史会话无法重新触发 defaultMessages、页面一直转圈。
 */
export function resolveChatConversationKey(session: ConversationSelection): string {
  if (isDraftConversation(session)) return session.draftKey;
  if (session?.id != null) return String(session.id);
  return FALLBACK_CONVERSATION_KEY;
}

/**
 * 真实会话：等 useXChat 拉完 defaultMessages。
 * 草稿/无会话：没有后端历史，视为已就绪（展示空态输入框）。
 */
export function isConversationHistoryReady(
  sessionId: number | null | undefined,
  isDefaultMessagesRequesting: boolean,
): boolean {
  return sessionId == null || !isDefaultMessagesRequesting;
}

export type PendingDraftSend = { sessionId: number; content: string };

/** 聊天引擎按 conversationKey 重挂后：历史拉完且 sessionId 已对上，才能把草稿待发送刷进新 store。 */
export function shouldFlushPendingSend(
  pending: PendingDraftSend | null,
  sessionId: number | null,
  isDefaultMessagesRequesting: boolean,
): boolean {
  return (
    pending != null && sessionId != null && pending.sessionId === sessionId && !isDefaultMessagesRequesting
  );
}

/**
 * 草稿晋升后绑定模型：会话身份必须以 create 返回值为准。
 * 绑定接口若返回完整会话（id 一致）则合并字段；否则只吸收 modelReleaseId，避免把会话 id 换成绑定行 id / 空对象。
 */
export function mergeSessionAfterModelBind(
  created: AgentSession,
  bound: AgentSession | null | undefined,
  fallbackReleaseId?: number | null,
): AgentSession {
  if (bound != null && bound.id === created.id) {
    return { ...created, ...bound };
  }
  const fromBound = bound != null && bound.modelReleaseId != null ? bound.modelReleaseId : null;
  const modelReleaseId = fromBound ?? fallbackReleaseId ?? created.modelReleaseId ?? null;
  return { ...created, modelReleaseId };
}
