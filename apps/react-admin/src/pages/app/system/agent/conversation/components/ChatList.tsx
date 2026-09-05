import { useEffect, useMemo, useRef, useState } from 'react';
import { Bubble, Prompts, Welcome } from '@ant-design/x';
import type { MessageInfo } from '@ant-design/x-sdk/es/x-chat';
import { MdPreview } from 'md-editor-rt';
import 'md-editor-rt/lib/style.css';
import { RobotOutlined, UserOutlined } from '@ant-design/icons';
import { Alert, Empty, Space, Spin, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { isDarkMode } from '@/components/common/Editor/src/utils';
import ThoughtChainBubble from './ThoughtChainBubble';
import type { AgentChatMessage, AssistantContent } from '../types';
import HitlApproveBar from './HitlApproveBar';

interface Props {
  messages: MessageInfo<AgentChatMessage>[];
  /** 空态：是否已选会话但尚无任何消息 */
  empty: boolean;
  /** 正在请求（Sender loading / HITL 等待共用） */
  requesting: boolean;
  onSendPrompt: (text: string) => void;
  onRetry?: (info: MessageInfo<AgentChatMessage>) => void;
  onResume: (resume: {
    interruptId: string;
    status: 'resolved' | 'cancelled';
    payload?: Record<string, unknown>;
  }[]) => void;
}

const { Text } = Typography;

/** 主题是否暗色（auto 跟随系统） */
function useDark(): boolean {
  const [dark, setDark] = useState(isDarkMode());
  useEffect(() => {
    const update = () => setDark(isDarkMode());
    update();
    const observer = new MutationObserver(update);
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });
    return () => observer.disconnect();
  }, []);
  return dark;
}

function MdContent({ text }: { text: string }) {
  const dark = useDark();
  if (!text.trim()) return <span style={{ opacity: 0.4 }}>…</span>;
  return (
    <MdPreview
      modelValue={text}
      theme={dark ? 'dark' : 'light'}
      previewTheme="default"
      style={{ background: 'transparent' }}
      className="agent-chat-md"
    />
  );
}

/** 用户气泡文本（纯文本，保留换行） */
function UserText({ text }: { text: string }) {
  return (
    <span style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{text}</span>
  );
}

function AssistantBubbleContent({ content, requesting }: { content: AssistantContent; requesting: boolean }) {
  return (
    <div>
      {(content.thinking || (content.toolCalls?.length ?? 0) > 0) && (
        <ThoughtChainBubble
          thinking={content.thinking}
          toolCalls={content.toolCalls}
          streaming={requesting}
        />
      )}
      {content.error ? (
        <Alert type="error" showIcon message={content.error} style={{ marginBottom: 4 }} />
      ) : (
        <MdContent text={content.content} />
      )}
    </div>
  );
}

const WELCOME_PROMPTS = [
  { key: 'greet', label: '你好，介绍一下你自己' },
  { key: 'help', label: '你能帮我做什么？' },
];

/**
 * ChatList：消息列表 + 空态。
 * assistant 渲染 Markdown（MdPreview 自带 XSS 过滤）；流式时 Bubble loading/typing。
 */
export default function ChatList({ messages, empty, requesting, onSendPrompt, onResume }: Props) {
  const { t } = useTranslation('agent-conversation');
  const scrollRef = useRef<HTMLDivElement>(null);

  // 自动滚动到底部
  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages]);

  const items = useMemo(
    () =>
      messages.map((info) => {
        const msg = info.message;
        if (msg.role === 'user') {
          return {
            key: info.id,
            role: 'user',
            placement: 'end',
            content: <UserText text={msg.content} />,
            avatar: <UserOutlined />,
          };
        }
        const content = msg as AssistantContent;
        const loading = info.status === 'loading' || info.status === 'updating';
        const waitingHitl = Boolean(content.waitingForApproval && content.interrupts?.length);
        const hitlChildren = waitingHitl ? (
          <HitlApproveBar interrupts={content.interrupts ?? []} onResume={onResume} />
        ) : null;
        return {
          key: info.id,
          role: 'ai',
          placement: 'start',
          avatar: <RobotOutlined />,
          loading,
          typing: loading ? { step: 4, interval: 20 } : false,
          content: (
            <>
              <AssistantBubbleContent content={content} requesting={loading} />
              {hitlChildren}
            </>
          ),
        };
      }),
    [messages, onResume],
  );

  const showWelcome = empty && !requesting && messages.length === 0;

  const roleConfig = {
    user: {
      placement: 'end',
      variant: 'filled',
      shape: 'corner',
    },
    ai: {
      placement: 'start',
      variant: 'outlined',
      shape: 'corner',
    },
  };

  return (
    <div
      ref={scrollRef}
      style={{
        flex: 1,
        overflowY: 'auto',
        padding: '16px 24px',
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
      }}
    >
      {showWelcome ? (
        <div style={{ margin: 'auto', textAlign: 'center' }}>
          <Welcome
            variant="borderless"
            icon={<RobotOutlined />}
            title={t('welcomeTitle')}
            description={t('welcomeDesc')}
          />
          <Prompts
            title={t('promptsTitle')}
            items={WELCOME_PROMPTS.map((p) => ({ ...p, label: t(`prompts.${p.key}`) }))}
            onItemClick={(info) => onSendPrompt(String(info.data.label))}
            wrap
            style={{ justifyContent: 'center' }}
          />
        </div>
      ) : messages.length === 0 ? (
        <Empty style={{ margin: 'auto' }} description={t('emptySession')} />
      ) : (
        <>
          <Bubble.List
            items={items as never}
            role={roleConfig as never}
            autoScroll
            style={{ flex: 1, minHeight: 0 }}
          />
          {requesting && (
            <Space style={{ alignSelf: 'flex-start', paddingInline: 12 }}>
              <Spin size="small" />
              <Text type="secondary" style={{ fontSize: 12 }}>
                {t('agentThinking')}
              </Text>
            </Space>
          )}
        </>
      )}
    </div>
  );
}
