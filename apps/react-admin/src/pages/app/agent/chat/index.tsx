import { useEffect, useMemo, useRef, useState } from 'react';
import { Bubble, Sender, Welcome } from '@ant-design/x';
import { AppstoreOutlined, PlusOutlined, StopOutlined } from '@ant-design/icons';
import { Alert, Avatar, Button, Card, Flex, Select, Spin, Tag, Tooltip, Typography, message } from 'antd';
import { useSearchParams } from 'react-router-dom';
import {
  cancelAgentRunApi,
  createAgentSessionApi,
  getAgentDefinitionApi,
  getAgentSessionApi,
  listAgentDefinitionsApi,
  resumeAgentSessionApi,
  runAgentSessionApi,
} from '@/api/rest/agent';
import type { AgentDefinition, AgentRunEvent, AgentSession } from '@/api/rest/types';
import { defaultIdGenerator } from '@/core/transport/rest/utils';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import { useAuthStore } from '@/stores';
import AssistantContent from './assistant-content';
import {
  appendAssistantDelta,
  appendAssistantThinkingDelta,
  ensureAssistantPlaceholder,
  finalizeAssistant,
  type ChatMessage,
} from './message-state';
import './index.css';

type RunState = 'idle' | 'running' | 'cancelling' | 'completed' | 'failed' | 'cancelled';

const terminalEvents: Record<string, RunState> = {
  CANCELLED: 'cancelled',
  COMPLETED: 'completed',
  FAILED: 'failed',
};

function statusLabel(state: RunState): string {
  return {
    cancelled: '已取消',
    cancelling: '取消中',
    completed: '已完成',
    failed: '运行失败',
    idle: '就绪',
    running: '生成中',
  }[state];
}

const AgentChatPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const accessToken = useAuthStore((state) => state.accessToken);
  const agentDefinitionId = Number(searchParams.get('agentDefinitionId'));
  const [agent, setAgent] = useState<AgentDefinition | null>(null);
  const [agents, setAgents] = useState<AgentDefinition[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [agentsLoaded, setAgentsLoaded] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [runState, setRunState] = useState<RunState>('idle');
  const [session, setSession] = useState<AgentSession | null>(null);
  const [resumable, setResumable] = useState(false);
  const [sending, setSending] = useState(false);
  const [inputValue, setInputValue] = useState('');
  const requestIdRef = useRef<string | null>(null);
  const sendingRef = useRef(false);
  const streamAbortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    let active = true;
    void listAgentDefinitionsApi()
      .then((definitions) => {
        if (!active) return;
        setAgents(definitions);
        setAgentsLoaded(true);
        if (!agentDefinitionId && definitions.length === 1) {
          setSearchParams({ agentDefinitionId: String(definitions[0].id) }, { replace: true });
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setAgentsLoaded(true);
          setError(cause instanceof Error ? cause.message : '加载 Agent 列表失败。');
        }
      });
    if (!Number.isSafeInteger(agentDefinitionId) || agentDefinitionId <= 0) {
      return () => {
        active = false;
      };
    }
    void getAgentDefinitionApi(agentDefinitionId)
      .then((definition) => {
        if (active) setAgent(definition);
      })
      .catch((cause: unknown) => {
        if (active) setError(cause instanceof Error ? cause.message : '加载 Agent 失败。');
      });
    return () => {
      active = false;
      streamAbortRef.current?.abort();
    };
  }, [agentDefinitionId, setSearchParams]);

  const canSend = Boolean(agent?.isEnabled && accessToken && !sending && runState !== 'cancelling');
  const statusColor = runState === 'failed' ? 'error' : runState === 'cancelling' ? 'warning' : runState === 'running' ? 'processing' : runState === 'cancelled' ? 'default' : 'success';
  const bubbles = useMemo(
    () =>
      messages.map((item) => {
        if (item.role !== 'ai') {
          return item;
        }
        // 仅有思考、正文仍为空时，给 Bubble 一个占位字符，确保 contentRender 会挂载
        const content = item.content || (item.thinking ? '\u200b' : '');
        return {
          ...item,
          content,
          contentRender: () => (
            <AssistantContent
              content={item.content}
              messageKey={item.key}
              thinking={item.thinking}
              thinkingDurationSec={item.thinkingDurationSec}
              thinkingStreaming={item.thinkingStreaming}
            />
          ),
        };
      }),
    [messages],
  );

  function consumeEvent(event: AgentRunEvent): void {
    if (event.type === 'STARTED') {
      setMessages((current) => ensureAssistantPlaceholder(current, defaultIdGenerator));
      return;
    }
    if (event.type === 'THINKING_DELTA' && event.text) {
      setMessages((current) => appendAssistantThinkingDelta(current, event.text, defaultIdGenerator));
      return;
    }
    if (event.type === 'TEXT_DELTA' && event.text) {
      setMessages((current) => appendAssistantDelta(current, event.text, defaultIdGenerator));
      return;
    }
    if (event.type === 'TOOL_STARTED' || event.type === 'TOOL_COMPLETED') {
      const action = event.type === 'TOOL_STARTED' ? '正在调用工具' : '工具调用完成';
      setMessages((current) => [
        ...ensureAssistantPlaceholder(current, defaultIdGenerator),
        { content: `${action}：${event.toolName ?? '未知工具'}`, key: defaultIdGenerator(), role: 'system' },
      ]);
      return;
    }
    const terminal = terminalEvents[event.type];
    if (terminal) {
      sendingRef.current = false;
      setSending(false);
      requestIdRef.current = null;
      setRunState(terminal);
      setResumable(false);
      setMessages((current) => finalizeAssistant(current));
      if (event.message) message[terminal === 'failed' ? 'error' : 'success'](event.message);
    }
  }

  function markResumable(detail: string): void {
    setSending(false);
    setResumable(Boolean(requestIdRef.current));
    setError(`${detail} 可续接该请求。`);
  }

  async function send(content: string): Promise<void> {
    const text = content.trim();
    if (!text || !canSend || !accessToken || !agent || sendingRef.current) return;
    sendingRef.current = true;
    setSending(true);
    setInputValue('');
    try {
      setError(null);
      setResumable(false);
      const activeSession = session ?? await createAgentSessionApi(agent.id);
      setSession(activeSession);
      const requestId = defaultIdGenerator();
      requestIdRef.current = requestId;
      setMessages((current) =>
        ensureAssistantPlaceholder(
          [...current, { content: text, key: requestId, role: 'user' }],
          defaultIdGenerator,
        ),
      );
      setRunState('running');
      const controller = new AbortController();
      streamAbortRef.current = controller;
      await runAgentSessionApi(activeSession.id, { accessToken, message: text, requestId }, { onEvent: consumeEvent, signal: controller.signal });
      if (requestIdRef.current === requestId) markResumable('Agent 流连接已关闭，运行状态未知；');
    } catch (cause: unknown) {
      if (cause instanceof DOMException && cause.name === 'AbortError') return;
      markResumable(cause instanceof Error ? cause.message : 'Agent 流连接中断。');
    } finally {
      streamAbortRef.current = null;
    }
  }

  async function cancel(): Promise<void> {
    if (!session || !requestIdRef.current || runState !== 'running') return;
    try {
      setRunState('cancelling');
      consumeEvent(await cancelAgentRunApi(session.id, { requestId: requestIdRef.current }));
    } catch (cause: unknown) {
      sendingRef.current = false;
      setSending(false);
      setError(cause instanceof Error ? cause.message : '取消 Agent 运行失败。');
    }
  }

  async function resume(): Promise<void> {
    if (!session || !requestIdRef.current || !accessToken) return;
    sendingRef.current = true;
    setSending(true);
    try {
      setError(null);
      setResumable(false);
      setRunState('running');
      const controller = new AbortController();
      streamAbortRef.current = controller;
      await resumeAgentSessionApi(session.id, requestIdRef.current, accessToken, { onEvent: consumeEvent, signal: controller.signal });
      if (requestIdRef.current) markResumable('Agent 流连接已关闭，运行状态未知；');
    } catch (cause: unknown) {
      if (cause instanceof DOMException && cause.name === 'AbortError') return;
      markResumable(cause instanceof Error ? cause.message : '续接 Agent 流失败。');
    } finally {
      streamAbortRef.current = null;
    }
  }

  async function refreshSession(): Promise<void> {
    if (!session) return;
    try {
      setSession(await getAgentSessionApi(session.id));
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : '刷新会话失败。');
    }
  }

  if (!agent) {
    if (!agentsLoaded) {
      return <ContentContainer><Flex align="center" justify="center" style={{ minHeight: 360 }}><Spin size="large" /></Flex></ContentContainer>;
    }
    return (
      <ContentContainer>
        <Card style={{ maxWidth: 560, margin: '48px auto' }}>
          {error ? <Alert message="无法加载 Agent" description={error} showIcon type="error" /> : (
            <Flex gap={16} vertical>
              <Typography.Title level={3} style={{ margin: 0 }}>选择已发布 Agent</Typography.Title>
              {agents.length === 0 ? <Alert message="暂无已发布 Agent" description="请先发布一个 Agent Revision，再返回此页开始对话。" showIcon type="info" /> : <Select aria-label="选择 Agent" onChange={(id: number) => setSearchParams({ agentDefinitionId: String(id) })} options={agents.map((item) => ({ label: item.name, value: item.id }))} placeholder="选择 Agent" />}
            </Flex>
          )}
        </Card>
      </ContentContainer>
    );
  }

  return (
    <ContentContainer padding="16px" scrollable={false}>
      <div className="agent-chat-workspace">
        <aside className="agent-chat-sidebar" aria-label="Agent 列表">
          <div className="agent-chat-brand">
            <span className="agent-chat-brand-mark"><AppstoreOutlined /></span>
            <span>智能体工作台</span>
          </div>
          <Button className="agent-chat-new" icon={<PlusOutlined />} onClick={() => {
            setMessages([]);
            setSession(null);
            setRunState('idle');
            setResumable(false);
            setError(null);
            setInputValue('');
          }}>新建对话</Button>
          <div className="agent-chat-agent-list">
            <Typography.Text className="agent-chat-list-title">已发布 Agent</Typography.Text>
            {agents.map((item) => (
              <Button
                className={`agent-chat-agent${item.id === agent.id ? ' agent-chat-agent--active' : ''}`}
                icon={<Avatar size={24}>{item.name.slice(0, 1)}</Avatar>}
                key={item.id}
                onClick={() => setSearchParams({ agentDefinitionId: String(item.id) })}
                type="text"
              >
                <span className="agent-chat-agent-name">{item.name}</span>
              </Button>
            ))}
          </div>
          <div className="agent-chat-sidebar-footer">
            <Flex align="center" gap={8}><Avatar size="small">我</Avatar><Typography.Text>当前用户</Typography.Text></Flex>
            <Tooltip title="帮助"><Button aria-label="帮助" type="text">?</Button></Tooltip>
          </div>
        </aside>
        <main className="agent-chat-main">
          <header className="agent-chat-header">
            <div className="agent-chat-header-title">
              <Avatar size={36}>{agent.name.slice(0, 1)}</Avatar>
              <div className="agent-chat-header-copy">
                <Typography.Text strong>{agent.name}</Typography.Text>
                <Typography.Text type="secondary">{agent.description || '已发布 Agent 对话'}</Typography.Text>
              </div>
            </div>
            <Flex align="center" gap={8}>
              <Tag color={statusColor}>{statusLabel(runState)}</Tag>
              <Tag>Revision {session?.agentRevisionId ?? agent.currentPublishedRevisionId ?? '待首次运行固定'}</Tag>
              {resumable && <Button onClick={() => void resume()}>续接请求</Button>}
              {session && <Button onClick={() => void refreshSession()}>刷新会话</Button>}
            </Flex>
          </header>
          {error && <Alert className="agent-chat-alert" closable message={error} onClose={() => setError(null)} showIcon type="error" />}
          <section aria-live="polite" className="agent-chat-messages">
            {bubbles.length === 0 ? (
              <div className="agent-chat-welcome">
                <Welcome description="告诉我你的目标，我会调用已配置的工具完成任务。" title={`你好，我是 ${agent.name}`} />
                <div className="agent-chat-suggestions">
                  {['帮我梳理当前任务的执行方案', '分析一段内容并给出关键结论', '基于已有信息创建一个可执行清单', '解释这个问题并给出下一步建议'].map((prompt) => (
                    <Button className="agent-chat-suggestion" key={prompt} onClick={() => void send(prompt)} type="text">{prompt}</Button>
                  ))}
                </div>
              </div>
            ) : <Bubble.List autoScroll className="agent-chat-bubbles" items={bubbles} role={{ ai: { placement: 'start' }, system: { placement: 'start', variant: 'borderless' }, user: { placement: 'end' } }} />}
          </section>
          <div className="agent-chat-composer">
            <Sender autoSize={{ maxRows: 6, minRows: 2 }} disabled={!canSend} loading={sending || runState === 'cancelling'} onCancel={() => void cancel()} onChange={setInputValue} onSubmit={(content) => void send(content)} placeholder={canSend ? '输入消息，Enter 发送，Shift+Enter 换行' : '等待当前运行结束后再发送'} submitType="enter" suffix={sending || runState === 'cancelling' ? <Button aria-label="取消 Agent 运行" danger icon={<StopOutlined />} loading={runState === 'cancelling'} onClick={() => void cancel()} type="text" /> : undefined} value={inputValue} />
          </div>
        </main>
      </div>
    </ContentContainer>
  );
};

export default AgentChatPage;
