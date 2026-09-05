import { useCallback, useEffect, useMemo, useState } from 'react';
import { Empty, Spin, Typography, message } from 'antd';
import { useTranslation } from 'react-i18next';
import { bindSessionModelApi, getSessionModelBindingApi, unbindSessionModelApi } from '@/api/rest/agent';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import AgentPicker from './components/AgentPicker';
import ChatSender from './components/ChatSender';
import ChatSide from './components/ChatSide';
import ChatList from './components/ChatList';
import { useAgentConversation } from './useAgentConversation';
import './components/ChatSender.css';

/**
 * Agent 对话页（运行面）：独立式布局。
 *
 * 顶部：AgentPicker（跨 Agent）
 * 左侧：会话栏（新建/切换/删除）
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

  // 初始加载 Agent 列表
  useEffect(() => {
    void conv.loadAgents();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 会话切换时重置「记住的模型选择」显示（渲染期调整，避免 effect 内同步 setState）
  const [prevModelSession, setPrevModelSession] = useState<number | null>(sessionId);
  if (prevModelSession !== sessionId) {
    setPrevModelSession(sessionId);
    setModelReleaseId(null);
  }

  // 会话切换时加载其记住的模型选择
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

  // 会话切换时清理旧会话的「进行中」消息状态副作用由 hook 处理；
  // 这里在切换后清空消息展示（useXChat store 按 key 保留，切回仍可见历史）

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
      // 先清除待审批标记，避免残留审批条
      resumeRun(resume);
    },
    [resumeRun],
  );

  const emptyState = useMemo(() => {
    if (!activeAgent) return 'noAgent';
    if (!activeSession) return 'noSession';
    return null;
  }, [activeAgent, activeSession]);

  const showEmptyComposer =
    !emptyState && historyReady && messages.length === 0 && !isRequesting;

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
      style={{ padding: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}
    >
      {/* 顶部栏：AgentPicker + 会话操作 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '10px 16px',
          borderBottom: '1px solid rgba(128,128,128,0.15)',
          flexWrap: 'wrap',
        }}
      >
        <AgentPicker
          agents={agents}
          loading={agentsLoading}
          value={activeAgent}
          onChange={(a) => void setActiveAgent(a)}
        />
        <div style={{ flex: 1 }} />
        {activeAgent && (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {t('conversationHint')}
          </Typography.Text>
        )}
      </div>

      {/* 主体：左侧会话栏 + 右侧聊天区 */}
      <div style={{ flex: 1, display: 'flex', minHeight: 0 }}>
        <aside
          style={{
            width: 260,
            borderRight: '1px solid rgba(128,128,128,0.15)',
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          <ChatSide
            agentName={activeAgent?.name ?? ''}
            conversations={conversations}
            loading={sessionsLoading}
            activeId={activeSession?.id ?? null}
            onSelect={selectConversation}
            onCreate={handleNewChat}
            onDelete={handleDeleteSession}
          />
        </aside>

        <main
          className="agent-chat-pane"
          style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}
        >
          {emptyState ? (
            <div style={{ margin: 'auto', textAlign: 'center' }}>
              <Empty
                description={
                  emptyState === 'noAgent' ? t('noAgent') : t('selectOrNewSession')
                }
              />
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
