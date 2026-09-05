import { useCallback, useEffect, useState } from 'react';
import { Empty, Spin, message } from 'antd';
import { useTranslation } from 'react-i18next';
import { bindSessionModelApi, getSessionModelBindingApi, unbindSessionModelApi } from '@/api/rest/agent';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import ChatSender from './components/ChatSender';
import ChatSide from './components/ChatSide';
import ChatList from './components/ChatList';
import { useAgentConversation } from './useAgentConversation';
import './agent-conversation.css';

/**
 * Agent 对话页（运行面）：工作台式布局，文案保持原对话页。
 *
 * 左侧：Agent 选择 + 会话栏（新建/切换/删除）
 * 右侧：空会话居中标题+输入条；有消息后列表 + 底栏输入
 * HITL：RUN_FINISHED.outcome.interrupts → 审批条 → resume 续接
 */
export default function AgentConversationPage() {
  const { t } = useTranslation('agent-conversation');

  const conv = useAgentConversation();
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
    messages,
    historyReady,
    sendMessage,
    resumeRun,
    cancel,
    isRequesting,
    resetMessages,
  } = conv;

  const [modelReleaseId, setModelReleaseId] = useState<number | null>(null);
  const [modelLoading, setModelLoading] = useState(false);

  const sessionId = activeSession?.id ?? null;

  useEffect(() => {
    void conv.loadAgents();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const [prevModelSession, setPrevModelSession] = useState<number | null>(sessionId);
  if (prevModelSession !== sessionId) {
    setPrevModelSession(sessionId);
    setModelReleaseId(null);
  }

  useEffect(() => {
    if (sessionId === null) return;
    let alive = true;
    const timer = window.setTimeout(() => {
      setModelLoading(true);
      getSessionModelBindingApi(sessionId)
        .then((b) => {
          if (alive && b) setModelReleaseId(b.modelReleaseId);
        })
        .catch(() => {
          // 无绑定：回落 Revision 默认模型，无需提示
        })
        .finally(() => {
          if (alive) setModelLoading(false);
        });
    }, 0);
    return () => {
      alive = false;
      window.clearTimeout(timer);
    };
  }, [sessionId]);

  const handleChangeModel = useCallback(
    async (releaseId: number | null) => {
      if (sessionId === null) return;
      try {
        if (releaseId === null) {
          await unbindSessionModelApi(sessionId);
        } else {
          await bindSessionModelApi(sessionId, { modelReleaseId: releaseId });
        }
        setModelReleaseId(releaseId);
        message.success(t('modelBound'));
      } catch {
        message.error(t('modelBindFailed'));
      }
    },
    [sessionId, t],
  );

  const handleNewChat = useCallback(async () => {
    const session = await createConversation();
    if (session) {
      selectConversation(session);
    }
  }, [createConversation, selectConversation]);

  const handleDeleteSession = useCallback(
    async (id: number) => {
      await removeConversation(id);
      resetMessages();
    },
    [removeConversation, resetMessages],
  );

  const handleResume = useCallback(
    (resume: { interruptId: string; status: 'resolved' | 'cancelled'; payload?: Record<string, unknown> }[]) => {
      resumeRun(resume);
    },
    [resumeRun],
  );

  const emptyState = !activeAgent ? 'noAgent' : !activeSession ? 'noSession' : null;
  const showEmptyComposer = !emptyState && historyReady && messages.length === 0 && !isRequesting;

  const sender = (
    <ChatSender
      sessionId={sessionId}
      requesting={isRequesting}
      modelValue={modelReleaseId}
      modelLoading={modelLoading}
      onModelChange={handleChangeModel}
      onSend={sendMessage}
      onCancel={cancel}
      docked={!showEmptyComposer}
    />
  );

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
          ) : showEmptyComposer ? (
            <div className="agent-chat-empty">
              <h1 className="agent-chat-empty-title">{t('emptyHeadline')}</h1>
              {sender}
            </div>
          ) : (
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
                  onResume={handleResume}
                />
              )}
              {historyReady ? sender : null}
            </>
          )}
        </main>
      </div>
    </ContentContainer>
  );
}
