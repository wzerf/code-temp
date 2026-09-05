import { useCallback, useMemo, useRef, useState } from 'react';
import { useXChat } from '@ant-design/x-sdk';
import type { DefaultMessageInfo } from '@ant-design/x-sdk/es/x-chat';

import {
  createAgentSessionApi,
  deleteAgentSessionApi,
  listAgentSessionEventsApi,
  listAgentSessionsApi,
  listAllAgentApi,
} from '@/api/rest/agent';
import type { Agent, AgentSession } from '@/api/rest/types';
import { createAguiProvider } from './AguiChatProvider';
import { emptyAssistant, replayEvents } from './AguiEventMapper';
import type { AgentChatMessage, AguiEvent, AguiRunRequestBody } from './types';

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
  const [activeSession, setActiveSessionState] = useState<AgentSession | null>(null);
  // 每会话「历史已初始化」标记：避免从空 store 切回时重复拉历史
  const historyLoadedRef = useRef<Set<number>>(new Set());

  // ---------- Agent / 会话数据 ----------

  const loadAgents = useCallback(async () => {
    setAgentsLoading(true);
    try {
      const rows = await listAllAgentApi({ isEnabled: 1 });
      setAgents(Array.isArray(rows) ? rows : []);
    } finally {
      setAgentsLoading(false);
    }
  }, []);

  const loadSessions = useCallback(async (definitionId: number) => {
    setSessionsLoading(true);
    try {
      const res = await listAgentSessionsApi(definitionId, { page: 1, pageSize: 100 });
      const items = res && Array.isArray(res.items) ? res.items : [];
      setConversations(items);
    } finally {
      setSessionsLoading(false);
    }
  }, []);

  // ---------- useXChat ----------
  const sessionId = activeSession?.id;
  // provider 绑定当前会话；无会话时不发消息
  const provider = useMemo(() => {
    if (sessionId === undefined) return undefined;
    return createAguiProvider(sessionId);
  }, [sessionId]);

  // 历史回放 defaultMessages：仅当该会话 store 尚未加载过历史时触发
  const defaultMessages = useCallback(
    async (info: { conversationKey?: string }): Promise<DefaultMessageInfo<AgentChatMessage>[]> => {
      const key = info.conversationKey;
      const sid = key ? Number(key) : sessionId;
      if (sid === undefined || Number.isNaN(sid)) return [];
      const loaded = historyLoadedRef.current.has(sid);
      if (loaded) return [];
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

  const { onRequest, messages, isRequesting, abort, setMessages, parsedMessages } = useXChat<
    AgentChatMessage,
    AgentChatMessage,
    AguiRunRequestBody,
    AguiEvent
  >({
    provider,
    conversationKey: sessionId !== undefined ? String(sessionId) : undefined,
    defaultMessages,
    requestPlaceholder,
    requestFallback,
  });

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

  // ---------- 会话操作（切换前中止旧会话的流） ----------

  const switchAgent = useCallback(
    async (agent: Agent | null) => {
      stopCurrentStream();
      setActiveAgentState(agent);
      setActiveSessionState(null);
      setConversations([]);
      if (agent) {
        await loadSessions(agent.id);
      }
    },
    [loadSessions, stopCurrentStream],
  );

  const selectConversation = useCallback(
    (session: AgentSession | null) => {
      if (session && activeSession && session.id === activeSession.id) return;
      stopCurrentStream();
      setActiveSessionState(session);
    },
    [activeSession, stopCurrentStream],
  );

  const createConversation = useCallback(async (): Promise<AgentSession | null> => {
    if (!activeAgent) return null;
    const now = new Date();
    const label = `新对话 ${now.toLocaleDateString()} ${now.toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
    })}`;
    const session = await createAgentSessionApi(activeAgent.id, { remark: label });
    setConversations((prev) => [session, ...prev]);
    return session;
  }, [activeAgent]);

  const removeConversation = useCallback(
    async (sessionIdToDelete: number) => {
      if (activeSession?.id === sessionIdToDelete) {
        stopCurrentStream();
      }
      await deleteAgentSessionApi(sessionIdToDelete);
      setConversations((prev) => prev.filter((s) => s.id !== sessionIdToDelete));
      historyLoadedRef.current.delete(sessionIdToDelete);
      setActiveSessionState((cur) => (cur?.id === sessionIdToDelete ? null : cur));
    },
    [activeSession?.id, stopCurrentStream],
  );

  // ---------- 发送 / 续接 / 取消 ----------

  const sendMessage = useCallback(
    (content: string) => {
      if (!content.trim() || isRequesting || sessionId === undefined) return;
      onRequest({ content: content.trim() });
    },
    [onRequest, isRequesting, sessionId],
  );

  const resumeRun = useCallback(
    (
      resume: {
        interruptId: string;
        status: 'resolved' | 'cancelled';
        payload?: Record<string, unknown>;
      }[],
    ) => {
      if (sessionId === undefined || resume.length === 0) return;
      onRequest({ content: '', resume });
    },
    [onRequest, sessionId],
  );

  const cancel = useCallback(() => stopCurrentStream(), [stopCurrentStream]);

  const resetMessages = useCallback(() => {
    historyLoadedRef.current.clear();
    setMessages([]);
  }, [setMessages]);

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
    createConversation,
    removeConversation,
    selectConversation,
    // 消息
    messages,
    parsedMessages,
    sendMessage,
    resumeRun,
    cancel,
    isRequesting,
    setMessages,
    resetMessages,
  };
}

export type AgentConversationApi = ReturnType<typeof useAgentConversation>;
