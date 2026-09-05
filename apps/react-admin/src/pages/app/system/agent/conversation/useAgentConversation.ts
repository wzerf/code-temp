import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useXChat } from '@ant-design/x-sdk';
import type { DefaultMessageInfo } from '@ant-design/x-sdk/es/x-chat';

import {
  bindSessionModelApi,
  createAgentSessionApi,
  deleteAgentSessionApi,
  listAgentSessionEventsApi,
  listAgentSessionsApi,
  listAllAgentApi,
} from '@/api/rest/agent';
import type { Agent, AgentSession } from '@/api/rest/types';
import { createAguiProvider } from './AguiChatProvider';
import { emptyAssistant, replayEvents } from './AguiEventMapper';
import { listModelAvailableApi } from '@/api/rest/model';
import type { AgentChatMessage, AguiEvent, AguiRunRequestBody, DraftConversation } from './types';
import {
  createDraft,
  isConversationHistoryReady,
  isDraftConversation,
  mergeSessionAfterModelBind,
  resolveChatConversationKey,
  shouldFlushPendingSend,
  type PendingDraftSend,
} from './conversationSession';

/**
 * Agent 对话状态装配：
 * - AgentPicker：可用 Agent 列表 + 当前选中
 * - 会话栏：某 Agent 的会话列表（加载/新建/删除/切换）
 * - useXChat：消息状态机（流式增量 / loading / error / abort）
 * - 历史回放：切换会话或刷新时从后端拉已持久化 AG-UI 事件重建消息
 *
 * 聊天引擎按 conversationKey 重挂：x-sdk 内部 key 只在 mount 时取初始值，
 * 草稿晋升/切会话若只改 props，消息会写进旧 store，新会话看起来永远是空的。
 */

/** 把持久化事件 JSON 列表解析为 AG-UI 事件 */
export function parseHistoryEvents(rawList: string[]): AguiEvent[] {
  const events: AguiEvent[] = [];
  for (const raw of rawList) {
    try {
      const parsed = JSON.parse(raw) as AguiEvent;
      if (parsed && typeof parsed.type === 'string') {
        events.push(parsed);
      }
    } catch {
      // 跳过无法解析的残留
    }
  }
  return events;
}

export function useAgentSessionShell() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [agentsLoading, setAgentsLoading] = useState(false);
  const [activeAgent, setActiveAgentState] = useState<Agent | null>(null);
  const [conversations, setConversations] = useState<AgentSession[]>([]);
  const [sessionsLoading, setSessionsLoading] = useState(false);
  const [activeSession, setActiveSessionState] = useState<AgentSession | DraftConversation | null>(null);
  const [pendingSend, setPendingSend] = useState<PendingDraftSend | null>(null);
  const pendingSendRef = useRef<PendingDraftSend | null>(null);

  const draftSessionRef = useRef<AgentSession | null>(null);
  const creatingSessionRef = useRef(false);

  const loadSessions = useCallback(async (definitionId: number): Promise<AgentSession[]> => {
    setSessionsLoading(true);
    try {
      const res = await listAgentSessionsApi(definitionId, { page: 1, pageSize: 100 });
      const items = res && Array.isArray(res.items) ? res.items : [];
      setConversations(items);
      return items;
    } finally {
      setSessionsLoading(false);
    }
  }, []);

  const loadAgents = useCallback(async () => {
    setAgentsLoading(true);
    let first: Agent | undefined;
    try {
      const rows = await listAllAgentApi({ isEnabled: 1 });
      const list = Array.isArray(rows) ? rows : [];
      setAgents(list);
      first = list[0];
      if (first) {
        setActiveAgentState(first);
      }
    } finally {
      setAgentsLoading(false);
    }
    if (first) {
      const items = await loadSessions(first.id);
      setActiveSessionState(items[0] ?? createDraft(first.id));
    }
  }, [loadSessions]);

  const isDraft = isDraftConversation(activeSession);
  const conversationKey = resolveChatConversationKey(activeSession);

  const ensureSession = useCallback(
    async (preferredModelReleaseId?: number | null): Promise<AgentSession | null> => {
      if (activeSession && !activeSession.draft) return activeSession;
      if (!activeAgent) return null;
      if (creatingSessionRef.current) return draftSessionRef.current;
      creatingSessionRef.current = true;
      try {
        const now = new Date();
        const label = `新对话 ${now.toLocaleDateString()} ${now.toLocaleTimeString([], {
          hour: '2-digit',
          minute: '2-digit',
        })}`;
        const created = await createAgentSessionApi(activeAgent.id, { remark: label });
        if (created?.id == null) return null;
        let session = created;
        draftSessionRef.current = session;
        try {
          let releaseId = preferredModelReleaseId ?? null;
          if (releaseId === null) {
            const rows = await listModelAvailableApi();
            releaseId = rows?.[0]?.id ?? null;
          }
          if (releaseId !== null) {
            const bound = await bindSessionModelApi(session.id, { modelReleaseId: releaseId });
            session = mergeSessionAfterModelBind(created, bound, releaseId);
            draftSessionRef.current = session;
          }
        } catch {
          // 默认模型绑定失败不阻断发送；仍可手动选择
        }
        setConversations((prev) => [session, ...prev]);
        setActiveSessionState(session);
        return session;
      } finally {
        creatingSessionRef.current = false;
      }
    },
    [activeAgent, activeSession],
  );

  const createDraftConversation = useCallback(() => {
    if (!activeAgent) return null;
    draftSessionRef.current = null;
    pendingSendRef.current = null;
    setPendingSend(null);
    const draft = createDraft(activeAgent.id);
    setActiveSessionState(draft);
    return draft;
  }, [activeAgent]);

  const switchAgent = useCallback(
    async (agent: Agent | null) => {
      pendingSendRef.current = null;
      setPendingSend(null);
      setActiveAgentState(agent);
      setActiveSessionState(null);
      draftSessionRef.current = null;
      setConversations([]);
      if (agent) {
        const items = await loadSessions(agent.id);
        setActiveSessionState(items[0] ?? createDraft(agent.id));
      } else {
        setActiveSessionState(null);
      }
    },
    [loadSessions],
  );

  const selectConversation = useCallback(
    (session: AgentSession | null) => {
      if (session && activeSession && session.id === activeSession.id) return;
      pendingSendRef.current = null;
      setPendingSend(null);
      draftSessionRef.current = session;
      setActiveSessionState(session);
    },
    [activeSession],
  );

  const removeConversation = useCallback(
    async (sessionIdToDelete: number) => {
      await deleteAgentSessionApi(sessionIdToDelete);
      setConversations((prev) => {
        const next = prev.filter((s) => s.id !== sessionIdToDelete);
        setActiveSessionState((cur) => {
          if (cur?.id !== sessionIdToDelete) return cur;
          draftSessionRef.current = null;
          pendingSendRef.current = null;
          setPendingSend(null);
          return next[0] ?? createDraft(activeAgent?.id ?? cur.agentDefinitionId);
        });
        return next;
      });
    },
    [activeAgent?.id],
  );

  const applySessionUpdate = useCallback((session: AgentSession) => {
    setConversations((prev) => prev.map((s) => (s.id === session.id ? session : s)));
    setActiveSessionState((cur) => (cur && !isDraftConversation(cur) && cur.id === session.id ? session : cur));
    if (draftSessionRef.current?.id === session.id) {
      draftSessionRef.current = session;
    }
  }, []);

  const enqueuePendingSend = useCallback((payload: PendingDraftSend) => {
    pendingSendRef.current = payload;
    setPendingSend(payload);
  }, []);

  const consumePendingSend = useCallback((): PendingDraftSend | null => {
    const payload = pendingSendRef.current;
    pendingSendRef.current = null;
    setPendingSend(null);
    return payload;
  }, []);

  return {
    agents,
    agentsLoading,
    activeAgent,
    setActiveAgent: switchAgent,
    loadAgents,
    conversations,
    sessionsLoading,
    activeSession,
    isDraft,
    conversationKey,
    pendingSend,
    enqueuePendingSend,
    consumePendingSend,
    ensureSession,
    createConversation: createDraftConversation,
    removeConversation,
    selectConversation,
    applySessionUpdate,
  };
}

export function useAgentChatEngine(opts: {
  sessionId: number | null;
  conversationKey: string;
  isDraft: boolean;
  pendingSend: PendingDraftSend | null;
  ensureSession: (modelReleaseId?: number | null) => Promise<AgentSession | null>;
  enqueuePendingSend: (payload: PendingDraftSend) => void;
  consumePendingSend: () => PendingDraftSend | null;
}) {
  const {
    sessionId,
    conversationKey,
    isDraft,
    pendingSend,
    ensureSession,
    enqueuePendingSend,
    consumePendingSend,
  } = opts;

  const provider = useMemo(() => {
    if (sessionId === null || sessionId === undefined) return undefined;
    return createAguiProvider(sessionId);
  }, [sessionId]);

  const defaultMessages = useCallback(
    async (info: { conversationKey?: string }): Promise<DefaultMessageInfo<AgentChatMessage>[]> => {
      const key = info.conversationKey;
      const sid = key ? Number(key) : sessionId;
      if (!sid || Number.isNaN(sid) || sid <= 0) return [];
      try {
        const rawList = await listAgentSessionEventsApi(sid);
        const events = parseHistoryEvents(rawList ?? []);
        const turns = replayEvents(events);
        const msgs: DefaultMessageInfo<AgentChatMessage>[] = [];
        for (const turn of turns) {
          if (turn.userContent) {
            msgs.push({ message: { role: 'user', content: turn.userContent } });
          }
          msgs.push({ message: turn.assistant });
        }
        return msgs;
      } catch {
        return [];
      }
    },
    [sessionId],
  );

  const requestPlaceholder = useCallback((params: Partial<AguiRunRequestBody>) => {
    if (params.resume && params.resume.length > 0) {
      return {
        role: 'assistant' as const,
        content: '',
        toolCalls: [],
        thinking: '继续执行工具…',
      };
    }
    return emptyAssistant();
  }, []);

  const requestFallback = useCallback(
    (
      _params: Partial<AguiRunRequestBody>,
      info: {
        error: Error;
        messageInfo?: { message?: AgentChatMessage };
      },
    ) => {
      const cur = info.messageInfo?.message;
      if (cur && cur.role === 'assistant' && (cur.error || cur.content || cur.interrupts)) {
        return cur;
      }
      return {
        role: 'assistant' as const,
        content: info.error.name === 'AbortError' ? '已取消' : '请求失败',
        error: info.error.name === 'AbortError' ? undefined : info.error.message,
        toolCalls: [],
      };
    },
    [],
  );

  const {
    onRequest,
    messages,
    isRequesting,
    abort,
    setMessages,
    parsedMessages,
    isDefaultMessagesRequesting,
  } = useXChat<AgentChatMessage, AgentChatMessage, AguiRunRequestBody, AguiEvent>({
    provider,
    conversationKey,
    defaultMessages,
    requestPlaceholder,
    requestFallback,
  });

  const historyReady = isConversationHistoryReady(sessionId, isDefaultMessagesRequesting);

  const onRequestRef = useRef(onRequest);
  useEffect(() => {
    onRequestRef.current = onRequest;
  }, [onRequest]);

  const abortRef = useRef(abort);
  useEffect(() => {
    abortRef.current = abort;
  }, [abort]);

  // 只在引擎随 conversationKey 真正卸载时中止旧流。
  // 不能把 abort 放进 effect 依赖：useXChat 每次请求都会换 abort 引用，
  // cleanup 会把刚发出的 SSE 立刻 abort，界面只剩「已取消」。
  useEffect(() => {
    return () => {
      try {
        abortRef.current();
      } catch {
        // 草稿/未初始化 provider 时 abort 会抛，忽略
      }
    };
  }, []);

  useEffect(() => {
    if (!shouldFlushPendingSend(pendingSend, sessionId, isDefaultMessagesRequesting)) return;
    const payload = pendingSend;
    if (!payload) return;
    // setTimeout(0)：避开 StrictMode 先 mount→unmount 把第一次 onRequest abort 掉
    const timer = window.setTimeout(() => {
      const taken = consumePendingSend();
      if (!taken) return;
      onRequestRef.current({ content: taken.content });
    }, 0);
    return () => window.clearTimeout(timer);
  }, [pendingSend, sessionId, isDefaultMessagesRequesting, consumePendingSend]);

  const sendMessage = useCallback(
    async (content: string, modelReleaseId?: number | null) => {
      const text = content.trim();
      if (!text || isRequesting) return;
      if (isDraft) {
        const session = await ensureSession(modelReleaseId);
        if (session?.id == null) return;
        enqueuePendingSend({ sessionId: session.id, content: text });
        return;
      }
      onRequestRef.current({ content: text });
    },
    [isDraft, ensureSession, enqueuePendingSend, isRequesting],
  );

  const resumeRun = useCallback(
    (
      resume: {
        interruptId: string;
        status: 'resolved' | 'cancelled';
        payload?: Record<string, unknown>;
      }[],
    ) => {
      if (sessionId === null || sessionId === undefined || resume.length === 0) return;
      onRequestRef.current({ content: '', resume });
    },
    [sessionId],
  );

  const cancel = useCallback(() => {
    try {
      abort();
    } catch {
      // ignore
    }
  }, [abort]);

  return {
    messages,
    historyReady,
    parsedMessages,
    sendMessage,
    resumeRun,
    cancel,
    isRequesting,
    setMessages,
  };
}

/** @deprecated 使用 useAgentSessionShell + 按 conversationKey 重挂的 useAgentChatEngine */
export function useAgentConversation() {
  const shell = useAgentSessionShell();
  const chat = useAgentChatEngine({
    sessionId: shell.activeSession?.id ?? null,
    conversationKey: shell.conversationKey,
    isDraft: shell.isDraft,
    pendingSend: shell.pendingSend,
    ensureSession: shell.ensureSession,
    enqueuePendingSend: shell.enqueuePendingSend,
    consumePendingSend: shell.consumePendingSend,
  });
  return { ...shell, ...chat };
}

export type AgentConversationApi = ReturnType<typeof useAgentConversation>;
