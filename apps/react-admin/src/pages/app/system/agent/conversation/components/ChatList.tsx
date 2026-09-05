import { useEffect, useMemo, useRef, useState } from 'react';
import { Bubble } from '@ant-design/x';
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

/**
 * ChatList：消息列表。
 * assistant 渲染 Markdown（MdPreview 自带 XSS 过滤）；流式时 Bubble loading/typing。
 */
export default function ChatList({ messages, empty, requesting, onResume }: Props) {
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
        padding: '16px 24px 8px',
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
      }}
    >
      {messages.length === 0 || empty ? (
        <Empty style={{ margin: 'auto' }} description={t('emptySession')} />
      ) : (
        <>
          <div
            style={{
              flex: 1,
              minHeight: 0,
              width: '100%',
              maxWidth: 720,
              marginInline: 'auto',
              display: 'flex',
              flexDirection: 'column',
            }}
          >
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
          </div>
        </>
      )}
    </div>
  );
}
