import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Empty, Spin, message } from 'antd';
import { useTranslation } from 'react-i18next';
import { bindSessionModelApi } from '@/api/rest/agent';
import { isDraftConversation } from './conversationSession';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import ChatSender from './components/ChatSender';
import ChatSide from './components/ChatSide';
import ChatList from './components/ChatList';
import { useAgentChatEngine, useAgentSessionShell } from './useAgentConversation';
import './agent-conversation.css';

/**
 * Agent 对话页（运行面）：工作台式布局，文案保持原对话页。
 *
 * 左侧：Agent 选择 + 会话栏（新建/切换/删除）
 * 右侧：空会话居中标题+输入条；有消息后列表 + 底栏输入
 * HITL：RUN_FINISHED.outcome.interrupts → 审批条 → resume 续接
 *
 * 聊天引擎必须按 conversationKey 重挂：x-sdk 不会在 props 变化时同步内部 key。
 */
export default function AgentConversationPage() {
  const { t } = useTranslation('agent-conversation');
  const shell = useAgentSessionShell();
  const {
    agents,
    agentsLoading,
    activeAgent,
    setActiveAgent,
    conversations,
    sessionsLoading,
    activeSession,
    createConversation,
    removeConversation,
    selectConversation,
    applySessionUpdate,
    conversationKey,
  } = shell;

  const [draftModelReleaseId, setDraftModelReleaseId] = useState<number | null>(null);
  const [draftModelKey, setDraftModelKey] = useState(conversationKey);
  const [modelLoading, setModelLoading] = useState(false);

  const sessionId = activeSession?.id ?? null;
  const isDraft = isDraftConversation(activeSession);
  if (isDraft && draftModelKey !== conversationKey) {
    setDraftModelKey(conversationKey);
    setDraftModelReleaseId(null);
  } else if (!isDraft && draftModelKey !== conversationKey) {
    setDraftModelKey(conversationKey);
  }
  const modelReleaseId = isDraft
    ? draftModelReleaseId
    : (activeSession?.modelReleaseId ?? null);

  useEffect(() => {
    void shell.loadAgents();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleChangeModel = useCallback(
    async (releaseId: number | null) => {
      if (sessionId === null) {
        setDraftModelReleaseId(releaseId);
        return;
      }
      try {
        setModelLoading(true);
        const updated = await bindSessionModelApi(sessionId, { modelReleaseId: releaseId });
        if (updated?.id === sessionId) {
          applySessionUpdate(updated);
        } else if (activeSession && !isDraftConversation(activeSession) && activeSession.id === sessionId) {
          applySessionUpdate({ ...activeSession, modelReleaseId: releaseId });
        }
      } catch {
        message.error(t('modelBindFailed'));
      } finally {
        setModelLoading(false);
      }
    },
    [sessionId, applySessionUpdate, activeSession, t],
  );

  const handleNewChat = useCallback(() => {
    createConversation();
  }, [createConversation]);

  const handleDeleteSession = useCallback(
    async (id: number) => {
      await removeConversation(id);
    },
    [removeConversation],
  );

  const emptyState = !activeAgent ? 'noAgent' : !activeSession ? 'noSession' : null;

  return (
    <ContentContainer
      heightMode="fixed"
      scrollable={false}
      padding="16px"
      style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}
    >
      <div className="agent-chat-workspace">
        <aside className="agent-chat-sidebar" aria-label={t('chatSideTitle')}>
          <ChatSide
            agents={agents}
            agentsLoading={agentsLoading}
            activeAgent={activeAgent}
            onAgentChange={(a) => void setActiveAgent(a)}
            conversations={conversations}
            loading={sessionsLoading}
            activeId={activeSession?.id ?? null}
            creatingDisabled={!activeAgent}
            onSelect={selectConversation}
            onCreate={handleNewChat}
            onDelete={handleDeleteSession}
          />
        </aside>

        <main className="agent-chat-main">
          {emptyState ? (
            <div className="agent-chat-blank">
              {agentsLoading ? (
                <Spin />
              ) : (
                <Empty
                  description={emptyState === 'noAgent' ? t('noAgent') : t('selectOrNewSession')}
                />
              )}
            </div>
          ) : (
            <AgentChatMain
              key={conversationKey}
              conversationKey={conversationKey}
              sessionId={sessionId}
              isDraft={isDraftConversation(activeSession)}
              shell={shell}
              modelReleaseId={modelReleaseId}
              modelLoading={modelLoading}
              onModelChange={handleChangeModel}
            />
          )}
        </main>
      </div>
    </ContentContainer>
  );
}

function AgentChatMain({
  conversationKey,
  sessionId,
  isDraft,
  shell,
  modelReleaseId,
  modelLoading,
  onModelChange,
}: {
  conversationKey: string;
  sessionId: number | null;
  isDraft: boolean;
  shell: ReturnType<typeof useAgentSessionShell>;
  modelReleaseId: number | null;
  modelLoading: boolean;
  onModelChange: (releaseId: number | null) => Promise<void>;
}) {
  const chat = useAgentChatEngine({
    sessionId,
    conversationKey,
    isDraft,
    pendingSend: shell.pendingSend,
    ensureSession: shell.ensureSession,
    enqueuePendingSend: shell.enqueuePendingSend,
    consumePendingSend: shell.consumePendingSend,
  });

  const { messages, historyReady, sendMessage, resumeRun, cancel, isRequesting } = chat;
  const showEmptyComposer = historyReady && messages.length === 0 && !isRequesting;

  const sender = (
    <ChatSender
      sessionId={sessionId}
      requesting={isRequesting}
      modelValue={modelReleaseId}
      modelLoading={modelLoading}
      onModelChange={onModelChange}
      onSend={(content) => sendMessage(content, modelReleaseId)}
      onCancel={cancel}
      docked={!showEmptyComposer}
    />
  );

  return (
    <ChatMainBody
      showEmptyComposer={showEmptyComposer}
      historyReady={historyReady}
      messages={messages}
      isRequesting={isRequesting}
      sender={sender}
      onResume={resumeRun}
    />
  );
}

function ChatMainBody({
  showEmptyComposer,
  historyReady,
  messages,
  isRequesting,
  sender,
  onResume,
}: {
  showEmptyComposer: boolean;
  historyReady: boolean;
  messages: ReturnType<typeof useAgentChatEngine>['messages'];
  isRequesting: boolean;
  sender: ReactNode;
  onResume: ReturnType<typeof useAgentChatEngine>['resumeRun'];
}) {
  const { t } = useTranslation('agent-conversation');
  if (showEmptyComposer) {
    return (
      <div className="agent-chat-empty">
        <h1 className="agent-chat-empty-title">{t('emptyHeadline')}</h1>
        {sender}
      </div>
    );
  }
  return (
    <>
      {!historyReady ? (
        <div className="agent-chat-history-loading">
          <Spin />
        </div>
      ) : (
        <ChatList
          messages={messages}
          empty={messages.length === 0}
          requesting={isRequesting}
          onResume={onResume}
        />
      )}
      {historyReady ? sender : null}
    </>
  );
}
