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
  resolveChatConversationKey,
} from './conversationSession';

/**
 * Agent 对话状态装配：
 * - AgentPicker：可用 Agent 列表 + 当前选中
 * - 会话栏：某 Agent 的会话列表（加载/新建/删除/切换）
 * - useXChat：消息状态机（流式增量 / loading / error / abort）
 * - 历史回放：切换会话或刷新时从后端拉已持久化 AG-UI 事件重建消息
 *
 * 会话切换语义：不同 Agent 的会话集合互相隔离；同一会话的消息在 useXChat 的
 * 会话 store 中按 conversationKey 缓存，切走再切回不重复请求历史。
 * 切换会话前会中止上一会话仍在跑的流（对齐「取消必须停止执行」）。
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

export function useAgentConversation() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [agentsLoading, setAgentsLoading] = useState(false);
  const [activeAgent, setActiveAgentState] = useState<Agent | null>(null);
  const [conversations, setConversations] = useState<AgentSession[]>([]);
  const [sessionsLoading, setSessionsLoading] = useState(false);
  const [activeSession, setActiveSessionState] = useState<AgentSession | DraftConversation | null>(null);
  // 每会话「历史已初始化」标记：避免 store 重建时重复拉历史
  const historyLoadedRef = useRef<Set<number>>(new Set());

  const markHistoryLoaded = useCallback((sid: number) => {
    historyLoadedRef.current.add(sid);
  }, []);

  // ---------- Agent / 会话数据 ----------

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
      // 有存量会话则选中最新一个，否则进入草稿会话（可直接开聊）
      setActiveSessionState(items[0] ?? createDraft(first.id));
    }
  }, [loadSessions]);

  // ---------- useXChat ----------
  // 草稿会话（id=null）或无会话时都没有可用的 provider/后端会话
  const sessionId = activeSession?.id ?? null;
  const conversationKey = resolveChatConversationKey(activeSession);
  // provider 绑定当前会话；无会话/草稿态不发消息
  const provider = useMemo(() => {
    if (sessionId === null || sessionId === undefined) return undefined;
    return createAguiProvider(sessionId);
  }, [sessionId]);

  // 历史回放 defaultMessages：仅当该会话 store 尚未加载过历史时触发
  const defaultMessages = useCallback(
    async (info: { conversationKey?: string }): Promise<DefaultMessageInfo<AgentChatMessage>[]> => {
      const key = info.conversationKey;
      const sid = key ? Number(key) : sessionId;
      // 草稿态（sid 为 null/0/NaN）没有后端会话，不应拉取历史
      if (!sid || Number.isNaN(sid) || sid <= 0) return [];
      const loaded = historyLoadedRef.current.has(sid);
      if (loaded) {
        return [];
      }
      historyLoadedRef.current.add(sid);
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
      } finally {
        markHistoryLoaded(sid);
      }
    },
    [sessionId, markHistoryLoaded],
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
      // 终态（RUN_ERROR/RUN_FINISHED）已写入消息后主动 abort 关连接：
      // 保留已渲染的错误/内容，而不是覆盖成「已取消」
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
    queueRequest,
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

  // 始终拿到「当前 provider」的中止函数（切换会话前先中止旧流）；
  // ref 写入放在 effect 里（事件处理器触发时 effect 已运行，读到的即最新）
  const abortRef = useRef(abort);
  useEffect(() => {
    abortRef.current = abort;
  }, [abort]);
  const stopCurrentStream = useCallback(() => {
    try {
      abortRef.current();
    } catch {
      // 无进行中请求（未初始化）时忽略
    }
  }, []);

  // 发送同样要拿到最新的 onRequest（草稿懒建会话后 provider 变化，
  // 闭包里的旧 onRequest 仍绑定旧 provider，必须走 ref 读取最新值）
  const onRequestRef = useRef(onRequest);
  useEffect(() => {
    onRequestRef.current = onRequest;
  }, [onRequest]);

  // 草稿态标识：进入页面或点「新建会话」时处于草稿（无后端会话）
  const isDraft = isDraftConversation(activeSession);
  // 草稿→真实会话 的落点引用：防重复创建
  const draftSessionRef = useRef<AgentSession | null>(null);
  const creatingSessionRef = useRef(false);

  /** 确保存在真实后端会话：处于草稿态时懒创建（含模型绑定），并发调用防重入 */
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
        const session = await createAgentSessionApi(activeAgent.id, { remark: label });
        draftSessionRef.current = session;
        markHistoryLoaded(session.id);
        setConversations((prev) => [session, ...prev]);
        // 优先绑定草稿预选模型；未选则回落可用列表第一项
        try {
          let releaseId = preferredModelReleaseId ?? null;
          if (releaseId === null) {
            const rows = await listModelAvailableApi();
            releaseId = rows?.[0]?.id ?? null;
          }
          if (releaseId !== null) {
            await bindSessionModelApi(session.id, { modelReleaseId: releaseId });
          }
        } catch {
          // 默认模型绑定失败不阻断发送；仍可手动选择
        }
        // 创建成功后把草稿替换为真实会话，触发 provider 重建
        setActiveSessionState(session);
        return session;
      } finally {
        creatingSessionRef.current = false;
      }
    },
    [activeAgent, activeSession, markHistoryLoaded],
  );

  /** 新建草稿会话：仅进入空白草稿态（不创建后端会话），供「新建会话」按钮使用 */
  const createDraftConversation = useCallback(() => {
    if (!activeAgent) return null;
    stopCurrentStream();
    draftSessionRef.current = null;
    const draft = createDraft(activeAgent.id);
    setActiveSessionState(draft);
    return draft;
  }, [activeAgent, stopCurrentStream]);

  // ---------- 会话操作（切换前中止旧会话的流） ----------

  const switchAgent = useCallback(
    async (agent: Agent | null) => {
      stopCurrentStream();
      setActiveAgentState(agent);
      setActiveSessionState(null);
      draftSessionRef.current = null;
      setConversations([]);
      if (agent) {
        const items = await loadSessions(agent.id);
        // 有存量会话则选中最新一个，否则进入草稿会话（可直接开聊）
        setActiveSessionState(items[0] ?? createDraft(agent.id));
      } else {
        setActiveSessionState(null);
      }
    },
    [loadSessions, stopCurrentStream],
  );

  const selectConversation = useCallback(
    (session: AgentSession | null) => {
      if (session && activeSession && session.id === activeSession.id) return;
      stopCurrentStream();
      draftSessionRef.current = session;
      setActiveSessionState(session);
    },
    [activeSession, stopCurrentStream],
  );

  const removeConversation = useCallback(
    async (sessionIdToDelete: number) => {
      if (activeSession?.id === sessionIdToDelete) {
        stopCurrentStream();
      }
      await deleteAgentSessionApi(sessionIdToDelete);
      setConversations((prev) => {
        const next = prev.filter((s) => s.id !== sessionIdToDelete);
        // 删除的是当前会话：若该 Agent 还有其它会话则选中第一个，否则进入草稿
        setActiveSessionState((cur) => {
          if (cur?.id !== sessionIdToDelete) return cur;
          draftSessionRef.current = null;
          return next[0] ?? createDraft(activeAgent?.id ?? cur.agentDefinitionId);
        });
        return next;
      });
      historyLoadedRef.current.delete(sessionIdToDelete);
    },
    [activeAgent?.id, activeSession?.id, stopCurrentStream],
  );

  // ---------- 发送 / 续接 / 取消 ----------

  const sendMessage = useCallback(
    async (content: string, modelReleaseId?: number | null) => {
      const text = content.trim();
      if (!text || isRequesting) return;
      // 草稿态：先懒创建真实会话（含所选/默认模型绑定）。
      // 不能立刻 onRequest：此时 provider/conversationKey 仍是草稿，
      // SDK 要等 conversationKey 切到新会话且 defaultMessages 结束后才发送。
      if (isDraft) {
        const session = await ensureSession(modelReleaseId);
        if (!session) return;
        queueRequest(String(session.id), { content: text });
        return;
      }
      onRequestRef.current({ content: text });
    },
    [isDraft, ensureSession, isRequesting, queueRequest],
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

  const cancel = useCallback(() => stopCurrentStream(), [stopCurrentStream]);

  return {
    // Agent
    agents,
    agentsLoading,
    activeAgent,
    setActiveAgent: switchAgent,
    loadAgents,
    // 会话
    conversations,
    sessionsLoading,
    activeSession,
    isDraft,
    createConversation: createDraftConversation,
    removeConversation,
    selectConversation,
    // 消息
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

export type AgentConversationApi = ReturnType<typeof useAgentConversation>;
