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
  } = conv;

  const [modelReleaseId, setModelReleaseId] = useState<number | null>(null);
  const [modelLoading, setModelLoading] = useState(false);

  const sessionId = activeSession?.id ?? null;

  useEffect(() => {
    void conv.loadAgents();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 切换真实会话时清空已选模型，由下方 effect 拉取绑定；草稿晋升真实会话则保留预选
  const [prevModelSession, setPrevModelSession] = useState<number | null>(sessionId);
  if (prevModelSession !== sessionId) {
    const promotedFromDraft = prevModelSession === null && sessionId !== null;
    setPrevModelSession(sessionId);
    if (!promotedFromDraft) {
      setModelReleaseId(null);
    }
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
      if (sessionId === null) {
        setModelReleaseId(releaseId);
        return;
      }
      try {
        if (releaseId === null) {
          await unbindSessionModelApi(sessionId);
        } else {
          await bindSessionModelApi(sessionId, { modelReleaseId: releaseId });
        }
        setModelReleaseId(releaseId);
      } catch {
        message.error(t('modelBindFailed'));
      }
    },
    [sessionId, t],
  );

  const handleNewChat = useCallback(() => {
    // 仅切换到空白草稿会话，不创建后端会话；发送首条消息时才真正创建。
    // 不要在这里 resetMessages：当前 conversationKey 还是上一真实会话，会把历史消息清掉。
    createConversation();
  }, [createConversation]);

  const handleDeleteSession = useCallback(
    async (id: number) => {
      await removeConversation(id);
    },
    [removeConversation],
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
      onSend={(content) => sendMessage(content, modelReleaseId)}
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
