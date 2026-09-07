import { memo, useEffect, useMemo, useState } from 'react';
import { Bubble } from '@ant-design/x';
import type { MessageInfo } from '@ant-design/x-sdk/es/x-chat';
import { MdPreview } from 'md-editor-rt';
import 'md-editor-rt/lib/preview.css';
import { RobotOutlined, UserOutlined } from '@ant-design/icons';
import { Alert, Image as AntImage, Space, Spin, Typography } from 'antd';
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
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class', 'data-theme'],
    });
    return () => observer.disconnect();
  }, []);
  return dark;
}

function assistantHasVisibleBody(content: AssistantContent): boolean {
  return Boolean(
    content.error ||
      content.content?.trim() ||
      content.thinking?.trim() ||
      (content.toolCalls?.length ?? 0) > 0 ||
      (content.generatedImages?.length ?? 0) > 0 ||
      (content.waitingForApproval && content.interrupts?.length),
  );
}

const MdContent = memo(function MdContent({
  text,
  messageKey,
  streaming,
}: {
  text: string;
  messageKey: string;
  streaming: boolean;
}) {
  const dark = useDark();
  if (!text.trim()) return null;
  return (
    <MdPreview
      editorId={`agent-md-${messageKey}`}
      modelValue={text}
      theme={dark ? 'dark' : 'light'}
      previewTheme="vuepress"
      className={streaming ? 'agent-chat-markdown agent-chat-markdown--streaming' : 'agent-chat-markdown'}
    />
  );
});

function UserText({ text }: { text: string }) {
  return <span className="agent-chat-user-text">{text}</span>;
}

const GeneratedImages = memo(function GeneratedImages({ images }: { images: Array<{ mimeType: string; b64: string }> }) {
  if (!images?.length) return null;
  const cols = images.length <= 2 ? 2 : 3;
  return (
    <AntImage.PreviewGroup>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))`,
          gap: 8,
          marginTop: 8,
          maxWidth: cols === 2 ? 520 : 640,
        }}
      >
        {images.map((img, i) => (
          <div key={i} style={{ aspectRatio: '1 / 1', overflow: 'hidden', borderRadius: 8, border: '1px solid #eee', background: '#fafafa' }}>
            <AntImage
              alt={`generated-${i}`}
              src={`data:${img.mimeType};base64,${img.b64}`}
              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
              preview={{ mask: '预览' }}
              wrapperStyle={{ width: '100%', height: '100%' }}
            />
          </div>
        ))}
      </div>
    </AntImage.PreviewGroup>
  );
});

function AssistantBubbleContent({
  content,
  streaming,
  messageKey,
}: {
  content: AssistantContent;
  streaming: boolean;
  messageKey: string;
}) {
  return (
    <div className="agent-chat-assistant-content">
      {(content.thinking || (content.toolCalls?.length ?? 0) > 0) && (
        <ThoughtChainBubble
          thinking={content.thinking}
          thinkingStartedAt={content.thinkingStartedAt}
          thinkingEndedAt={content.thinkingEndedAt}
          toolCalls={content.toolCalls}
          streaming={streaming}
        />
      )}
      {content.error ? (
        <Alert type="error" showIcon title={content.error} style={{ marginBottom: 4 }} />
      ) : (
        <>
          <MdContent text={content.content} messageKey={messageKey} streaming={streaming} />
          <GeneratedImages images={content.generatedImages ?? []} />
        </>
      )}
    </div>
  );
}

/**
 * ChatList：消息列表。
 * assistant 渲染 Markdown（MdPreview 自带 XSS 过滤）。
 * Bubble.loading 会整块替换正文，因此仅在首个 token 到达前使用；流式中靠增量 content + streaming。
 */
export default function ChatList({ messages, empty, requesting, onResume }: Props) {
  const { t } = useTranslation('agent-conversation');

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
        const isUpdating = info.status === 'loading' || info.status === 'updating';
        const hasVisibleBody = assistantHasVisibleBody(content);
        // loading=true 时 Bubble 只渲染转圈、隐藏正文；对齐 ai-1：仅等首 token
        const waitingFirstToken = isUpdating && !hasVisibleBody;
        const waitingHitl = Boolean(content.waitingForApproval && content.interrupts?.length);
        const hitlChildren = waitingHitl ? (
          <HitlApproveBar interrupts={content.interrupts ?? []} onResume={onResume} />
        ) : null;
        return {
          key: info.id,
          role: 'ai',
          placement: 'start',
          avatar: <RobotOutlined />,
          loading: waitingFirstToken,
          streaming: isUpdating,
          content: content.content || (hasVisibleBody ? '\u200b' : ''),
          contentRender: () => (
            <>
              <AssistantBubbleContent
                content={content}
                streaming={isUpdating}
                messageKey={String(info.id)}
              />
              {hitlChildren}
            </>
          ),
        };
      }),
    [messages, onResume],
  );

  const waitingFirstToken = requesting && !messages.some((info) => {
    if (info.message.role !== 'assistant') return false;
    return assistantHasVisibleBody(info.message as AssistantContent);
  });

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

  if (empty || messages.length === 0) {
    return null;
  }

  return (
    <section className="agent-chat-messages" aria-live="polite">
      <Bubble.List
        className="agent-chat-bubbles"
        items={items as never}
        role={roleConfig as never}
        autoScroll
      />
      {waitingFirstToken && (
        <Space className="agent-chat-typing">
          <Spin size="small" />
          <Text type="secondary" style={{ fontSize: 12 }}>
            {t('agentThinking')}
          </Text>
        </Space>
      )}
    </section>
  );
}
